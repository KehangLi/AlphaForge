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
- `realizedSummary`: broker-reported sell results over the selected instrument's
  complete history, grouped by Result currency. See the calculation rules below.
- `activities`: the requested page using the existing `AccountActivityResponse`
  contract, including amounts, currencies, fees, original broker `notes`, batch ID,
  row number, `importFilename`, `journalNote`, and `journalNoteUpdatedAt`.
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

With `size=1`, each page contains one activity, while `holding` and `realizedSummary` remain the same.
Activities are newest first, with import sequence, CSV row, and activity UUID
as descending tie breakers. A page beyond the last page returns an empty
`activities` array, preserving the summary and actual totals.

## Realized trading summary

Example `realizedSummary` for three sell records with Results +20 EUR, -5 EUR,
and one missing Result:

```json
{
  "sellRecordCount": 3,
  "includedSellRecordCount": 2,
  "excludedSellRecordCount": 1,
  "missingResultCount": 1,
  "missingResultCurrencyCount": 0,
  "currencies": [
    {
      "currency": "EUR",
      "totalResult": 15,
      "totalProfit": 20,
      "totalLoss": -5,
      "profitableSellCount": 1,
      "losingSellCount": 1,
      "breakevenSellCount": 0
    }
  ]
}
```

- `RealizedTradingCalculator` sums the original `resultAmount` on actions
  containing SELL, matching the existing trade classification. It never derives
  profit from `Total`, pairs buy/sell lots, converts currencies, or subtracts fees.
  These are broker-reported Results, not a claim of net profit after all fees/tax.
- EUR and USD have separate currency entries, sorted by currency. `totalLoss`
  is negative; `totalResult = totalProfit + totalLoss`.
- Buy, dividend, cash, and result-adjustment rows are excluded even if they carry
  a Result. Partial sales count before the entire position is closed.
- Each sell is one record, not a reconstructed round-trip trade. Repeated partial
  sales count individually. No trade-level win rate is inferred.
- A missing Result is excluded and counted by `missingResultCount`. A nonnull
  Result without a currency is excluded and counted by `missingResultCurrencyCount`.
  If both are missing, the row counts only as missing Result. The two missing
  categories are disjoint and sum to `excludedSellRecordCount`.
- An actual zero with a currency is included as breakeven. Missing values never
  become zero, and an all-missing set produces an empty currency list.
- The summary uses all imported history for this account/instrument. There is no
  date filter in this version. Activity pagination never changes summary values.

## Journal notes

Save, replace, or clear one activity's plain-text journal note:

```http
PUT /api/brokerage-accounts/{brokerageAccountId}/account-activities/{activityId}/journal-note
Content-Type: application/json
X-CSRF-TOKEN: <token for the current session>
Cookie: ALPHAFORGE_SESSION=<session cookie>

{"journalNote":"Entry reason\nExit plan"}
```

Response (200, `Cache-Control: no-store`):

```json
{
  "activityId": "40000000-0000-0000-0000-000000000001",
  "journalNote": "Entry reason\nExit plan",
  "journalNoteUpdatedAt": "2026-11-05T10:00:00Z"
}
```

- `journalNote` is required, at most 5,000 characters, and distinct from the CSV
  `notes` field. No imported trading values, hashes, or original notes are edited.
- Leading/trailing whitespace is stripped, internal line breaks are preserved.
  Send `{"journalNote":""}` to clear it. Blank input becomes `journalNote: null`
  in the response. Missing/null input or an oversized note returns 400.
- Read the saved note and last edit time through existing account-activity or
  instrument-detail GET responses. The fields are null on migrated rows with no
  journal edit. A clear operation retains its last edit timestamp.
- Repeating an identical normalized note leaves its timestamp unchanged. Writes
  use the existing user lock, then load the activity by owner, account, and ID.
  Saving an unavailable/foreign activity returns 404. Concurrent edits serialize;
  the last successful edit wins. There is no revision history or version conflict UI.
- Journal writes require a valid session cookie and CSRF token. The trusted-proxy
  secret is unrelated to this client request. Note content is not written to logs.
- V6 adds nullable note and timestamp columns plus a database length constraint;
  existing imported values remain intact. Notes live on their activity, so hard
  deletion of its batch/account removes them. Reimport creates new activities
  without restoring old journal text. An editor targeting a deleted ID gets 404.
- The frontend should render the note as text, not HTML, and refresh account and
  instrument activity caches after a successful save. UI wiring is a separate ticket.

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
- No summary table or materialized holding snapshot is needed. V6 only adds
  journal fields to the existing activity table. Computing summaries still scales
  with the selected instrument's imported history.

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
Additional coverage exercises mixed-currency sell summaries, missing versus zero
Results, original note preservation, journal idempotency and clearing, owner/account
authorization, CSRF, V5-to-V6 preservation, maximum note length, deletion/reimport,
and a concurrent note edit versus batch deletion.
