# Instrument detail API

## Request

```http
GET /api/brokerage-accounts/{brokerageAccountId}/instruments/{instrumentKey}?page=0&size=10
```

Example:

```http
GET /api/brokerage-accounts/20000000-0000-0000-0000-000000000001/instruments/ISIN:US67066G1040?page=0&size=10
```

Use the `instrumentKey` supplied by the holdings API. ISIN-backed history uses
`ISIN:US67066G1040`; ticker-only history uses `TICKER:NVDA`. Prefix and identifier
are trimmed and normalized to uppercase. Identifiers must be nonblank and at
most 12 characters for ISIN or 40 for ticker. Encode the key as a URL path segment
when constructing a link (for example, `encodeURIComponent(instrumentKey)`).

The endpoint uses the current guest or registered user's session cookie. GET
does not require a CSRF header. A client-supplied `userId` does not set identity.

| Parameter | Default | Behavior |
| --- | --- | --- |
| `page` | `0` | Zero-based activity page; clamped to 0-1000 |
| `size` | `10` | Maximum 200; values below 1 use 10 |

## Response

The response includes:

- `brokerageAccountId` and the canonical `instrumentKey`.
- `isin`, `ticker`, and `instrumentName`: latest nonblank metadata from the
  selected instrument's activity in chronological order.
- `holding`: the same `HoldingResponse` used by the holdings endpoint, computed
  from the selected instrument's complete history. Its display metadata and
  `lastTradedAt` come from trades, while the top-level metadata can include a
  later dividend or other instrument activity.
- `activities`: the requested page using the existing `AccountActivityResponse`
  contract, including amounts, currencies, fees, notes, batch ID, and row number.
- `page`, `size`, `totalElements`, `totalPages`, `first`, `last`: pagination of
  activities only. `totalElements` counts all matching activities, including dividends.

Buying five shares, selling two, and receiving one dividend yields:

```text
instrumentKey: ISIN:US67066G1040
holding.quantity: 3
holding.totalBoughtQuantity: 5
holding.totalSoldQuantity: 2
holding.status: OPEN
totalElements: 3
```

With `size=1`, each page contains one activity, while `holding` remains the same.
Activities are newest first, with import sequence, CSV row, and activity UUID
as descending tie breakers. A page beyond the last page returns an empty
`activities` array, preserving the summary and actual totals.

## Scope and consistency

The summary reuses `HoldingCalculator`, which is also used by the holdings list.
Its calculation rules, numeric precision, and negative-history checks are
described in [holdings-api.md](holdings-api.md).

- Account ownership is checked before any history is returned. Both repository
  queries also include the session user's ID and selected account ID.
- ISIN takes precedence over ticker. A `TICKER:` key matches only rows without a
  nonblank ISIN. It does not combine ISIN-backed instruments sharing a ticker.
- All matching activities are included, regardless of action. Dividends are
  visible in history but do not add shares. Account cash activity without a
  matching instrument is excluded.
- A dividend-only or other non-trade-only instrument returns metadata and
  activities with `holding: null`. This means no position can be inferred;
  it is different from a traded, fully closed position with quantity zero.
- Summary and page queries run in a read-only REPEATABLE_READ transaction so
  a concurrent import or deletion cannot produce mixed versions within a response.
  No user write lock is acquired. A subsequent request sees committed changes.
- The full-history query selects only calculation/metadata fields. The activity
  page loads its account and batch associations together. Full entities are only
  loaded for the requested page.
- No table, migration, or materialized holding snapshot is needed. Computing
  the summary still scales with the selected instrument's imported history.

Responses use `Cache-Control: no-store`.

## Errors

| Status | Meaning |
| --- | --- |
| 400 | Malformed/unsupported instrument key or invalid path/pagination parameter |
| 401 | Missing, expired, or invalid session |
| 404 | Missing/unowned account, or no matching instrument history in that account |
| 409 | Selected instrument's trade history has negative holdings or missing share quantities |

## Verification

`./mvnw verify` covers complete-history summaries with paged activity, dividends,
closed positions, deletion/reimport, metadata changes and backfills, equal-time
ordering, ISIN/ticker separation, guest/registered ownership, invalid keys,
unrelated invalid history, empty pages, HTTP cookie authentication, encoded path
segments, and consistent reads when a batch is deleted between summary and page.
