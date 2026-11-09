# Account realized trading summary API

## Request

```http
GET /api/brokerage-accounts/{brokerageAccountId}/realized-trading-summary?from=2026-10-01&to=2026-10-31&currency=EUR&page=0&size=10
```

The API uses the current guest or registered user's session cookie. GET requires
no CSRF header. Account ownership is checked before reading financial history;
the repository query also includes both user and account IDs.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `from` | unbounded | First included sale date, `yyyy-MM-dd`, in UTC |
| `to` | unbounded | Last included sale date, `yyyy-MM-dd`, in UTC |
| `currency` | all | Optional three-letter Result currency; filters contribution rows only |
| `page` | `0` | Zero-based contribution page, clamped to 0-1000 |
| `size` | `10` | Contribution rows per page, maximum 200; values below 1 use 10 |

Dates may be supplied independently. When both are present, `from <= to` is
required. Years 0001-9999 are supported. The database interval starts at 00:00 UTC
on `from` and ends **before** 00:00 UTC on the day after `to`. This includes the
entire last day, including microseconds, and treats UTC offsets by actual instant.
Dates filter the sale, not its earlier purchase. No date filter means all imported
sale history.

## Response

```text
brokerageAccountId
from, to                      Applied UTC dates, null for unbounded
currency                      Normalized contribution currency filter, or null
realizedSummary               Complete overview for the selected account/date range
unidentifiedSellRecordCount   Sale rows without either ISIN or ticker
contributions[]               Current page of instrument/currency groups
page, size, totalElements, totalPages, first, last
```

`realizedSummary` reuses the existing `RealizedTradingSummaryResponse`:

```json
{
  "sellRecordCount": 3,
  "includedSellRecordCount": 3,
  "excludedSellRecordCount": 0,
  "missingResultCount": 0,
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
    },
    {
      "currency": "USD",
      "totalResult": 10,
      "totalProfit": 10,
      "totalLoss": 0,
      "profitableSellCount": 1,
      "losingSellCount": 0,
      "breakevenSellCount": 0
    }
  ]
}
```

Each contribution has this structure:

```text
instrumentKey                 ISIN:... or TICKER:..., null for unknown instruments
isin, ticker, instrumentName   Latest nonblank sale metadata within this group/period
currency                      Result currency, null when unavailable
realizedSummary               The same summary type, restricted to this group
```

A group is one instrument plus one Result currency. If NVDA has both EUR and USD
sale results it has two contribution rows. Each group's `realizedSummary.currencies`
contains zero or one entry. An empty list means there is no attributable known
result; the UI should show unavailable, rather than zero.

### Overview versus contribution filters

`from`/`to` apply to both the overview and contributions. `currency` and pagination
apply **only to the contribution table**. The overview always returns all Result
currencies and complete missing-data counts for the date range, even when the table
shows EUR or a later page. A currency selector can display the corresponding
overview currency entry without losing coverage information. For example,
`currency=GBP` can return an empty table while EUR/USD remain in the overview.

`totalElements` counts instrument/currency groups after the contribution currency
filter, not trades, not distinct stocks, and not the count in the current page.
An out-of-range page is empty and retains the overview and actual totals.

### Contribution order and navigation

Contributions are ordered by currency ascending, with missing currency last.
Within each currency, known `totalResult` is descending; groups without a known
result sort last. Ticker and instrument key resolve ties. EUR and USD are never
compared as if their amounts had the same unit.

Use `instrumentKey` to navigate to the existing instrument detail route. Ticker-only
rows are separate from ISIN-backed history, even if the ticker matches. ISIN-backed
sales with renamed tickers remain one instrument. Metadata comes from sale time,
with import sequence/CSV row/UUID as deterministic ties, not latest import time.

Unknown instruments are retained as one group per currency with
`instrumentKey: null` and `instrumentName: "Unknown instrument"`. Disable navigation
for these rows. Their known Result amounts still contribute to the account overview,
and `unidentifiedSellRecordCount` exposes how many records need better attribution.
Rows without a Result currency are similarly retained in null-currency groups
when querying all currencies. They are not silently assigned to the account's
base currency.

## Calculation and reconciliation

The endpoint reuses `RealizedTradingCalculator`. The rules remain consistent with
the [single-instrument summary](instrument-api.md#realized-trading-summary):

- Only actions containing SELL are included, consistent with existing trade
  classification. Purchases, dividends, cash interest, and result adjustments
  do not contribute even if their raw CSV Result happens to be populated.
- Use broker-reported Result and its currency. Do not calculate cost basis,
  pair lots, convert currencies, or subtract fees again. These results are not
  labeled as net profit after all fees/tax or as account return percentages.
- Partial sales and fully closed positions both contribute. Repeated partial
  sales count as separate sell records, not reconstructed round-trip trades.
- Missing Result is counted first. A nonnull Result with no currency counts as
  missing currency. These categories are disjoint. Actual zero with a currency
  is included as breakeven; missing values do not turn into zero.
- Across all contribution pages, with no currency filter, the per-currency totals
  and coverage counts reconcile to the account overview. Include unknown and
  null-currency groups when reconciling. `unidentifiedSellRecordCount` overlaps
  financial coverage counts; it is not an additional category to subtract.

An empty account, buy-only account, or date range without sales returns 200 with
zero sell counts, empty currencies, and empty contributions.

## Persistence and consistency

One compact projection query loads matching sales in chronological order. It
selects only fields needed for summaries and display metadata, using SQL date and
action predicates. No entity graph or per-instrument database query is needed.
`REPEATABLE_READ` keeps ownership checks and the sale read in one snapshot.
The overview, grouping, and pagination use that same fetched history.

No new table or migration is required. Read-only queries take no user write lock.
Import, batch deletion, and account deletion are reflected on the next query.
For the MVP, calculation and grouping happen in Java: cost grows with the number
of sales in the selected period, even when requesting a small contribution page.

## Errors and verification

Responses use `Cache-Control: no-store`.

| Status | Meaning |
| --- | --- |
| 400 | Malformed/reversed/out-of-range dates, invalid currency format, or invalid path/paging parameter |
| 401 | Missing, expired, or invalid session |
| 404 | Account missing or owned by another user |

`./mvnw verify` runs unit, isolated PostgreSQL, and authenticated HTTP tests.
Coverage includes mixed currencies, amount/count reconciliation, missing versus
zero Results, unknown identifiers, UTC/timezone/microsecond/month-end/leap-day
boundaries, unbounded dates, pagination and currency filtering, renamed/fallback
instruments, guest/registered ownership, deletion/reimport, and consistent reads
during concurrent deletion.
