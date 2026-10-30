# Import integrity

## Import policy

All checks are scoped to a brokerage account. The same export or broker ID can
exist in another account. The import transaction acquires the owner's database
write lock **before** loading the account, checks, parses, validates, and saves.
`UserWriteLockService.lockUser(...)` owns this locking operation. Account creation,
account deletion, and batch deletion acquire the same lock. `GuestQuotaService`
checks resource limits after the caller has acquired the lock.
This applies to registered users as well as guests, across application replicas.

- Identical file bytes: the existing file-hash check rejects the upload (400).
- Same nonblank broker ID and same economic content: reject as a duplicate (409).
- Same broker ID and different content: reject as a conflict (409), never overwrite.
- If either record has no broker ID, identical economic content is ambiguous:
  reject (409), asking the user to resolve overlapping exports.
- Different nonblank IDs can share a timestamp and economic content. They may be
  separate fills and are allowed.

These checks compare both saved activities and earlier rows in the current file.
A duplicate/conflict rejects the **whole transaction**, including the new batch
and otherwise valid new activities. Errors name the CSV row and the matching
batch/row, or the matching row in the same file. The existing behavior for normal
CSV mapping errors is unchanged: valid rows may be saved as COMPLETED_WITH_ERRORS.

Economic content uses the UTC instant, action, instrument (ISIN first, then
ticker, then name), quantities, price, exchange rate, amounts/currencies, taxes,
fees, and merchant data. Decimal formatting and timezone offsets do not change
identity. Values are compared at PostgreSQL's timestamp/numeric precision.
Display names, notes, and raw CSV hashes are not authoritative identities.
Missing optional values are not assumed to equal zero.

The MVP deliberately accepts only one row per nonblank broker ID per account.
The example export supports this policy but does not prove that every broker's
ID identifies an execution. An export that uses one order ID for multiple fills
must be rejected until a broker-specific fill identity is available. Do not
silently discard its extra rows or assume that timestamps uniquely identify fills.

## Undoing imports

Batch deletion is LIFO **per account**, using a database-generated import sequence,
not the transaction date or created_at. Failed and partially successful batches
are also part of this stack. Uploading older trading history still creates the
newest import. Sequences may have gaps after a rolled-back transaction.

The backend returns `deletable` for the newest batch; older buttons are disabled.
DELETE rechecks the newest batch under the write lock and returns 409 if another
import has since committed. The frontend refreshes the history after a deletion
attempt, including conflicts. The flag is an affordance; server checks are authoritative.

Before deletion, remaining history is also checked for negative holdings. This
protects legacy accounts that predate the LIFO rule. Failure rolls back deletion.
Deleting an entire account still removes all its batches and activities together.

For trades at exactly the same instant, holdings replay uses import sequence then
CSV row number. This preserves earlier validated history when another file is
added. It is deterministic, but cannot reconstruct a broker's finer execution
order when the export omits it.

## V5 and existing databases

V1-V4 remain unchanged. V5 adds account/file-hash and account/nonblank-ID unique
indexes, plus a generated import sequence. Existing batches are ordered by
created_at then UUID; the true historical order of tied timestamps is unavailable.
No existing rows are automatically removed or merged.

Before applying V5 to an existing database, run these read-only checks:

```sql
SELECT brokerage_account_id, file_hash, count(*), array_agg(id) AS batches
FROM import_batch
WHERE file_hash IS NOT NULL
GROUP BY brokerage_account_id, file_hash
HAVING count(*) > 1;

SELECT brokerage_account_id, btrim(external_transaction_id) AS broker_id,
       count(*), array_agg(id) AS activities, array_agg(import_batch_id) AS batches
FROM account_activity
WHERE nullif(btrim(external_transaction_id), '') IS NOT NULL
GROUP BY brokerage_account_id, btrim(external_transaction_id)
HAVING count(*) > 1;
```

Any result requires reviewing the original exports and deciding how to repair
the history. V5 refuses the migration if these duplicates remain; PostgreSQL
rolls back the migration. Back up real data and resolve the cause before retrying.
Do not edit applied migration files or automatically delete one side of a conflict.
Existing records without IDs are not retroactively deduplicated by these indexes;
review overlapping historical exports before using those accounts for calculations.

## Verification

`./mvnw verify` uses disposable PostgreSQL via the test profile. Tests cover
normalized duplicates/conflicts, no-ID dividends, account isolation, rollback,
concurrent imports/deletes, backfilled imports, pagination flags, direct-write
constraints, upgrading V4 data, and refusing historical duplicates without loss.
