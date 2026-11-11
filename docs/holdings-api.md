# Holdings query API

## Request

```http
GET /api/brokerage-accounts/{brokerageAccountId}/holdings?status=OPEN&search=nvda&page=0&size=10
```

The endpoint uses the current guest or registered user's session. It accepts no
user identity from query parameters. Responses use `Cache-Control: no-store`.

| Parameter | Default | Behavior |
| --- | --- | --- |
| `status` | `OPEN` | `OPEN`, `CLOSED`, or `ALL` (uppercase) |
| `search` | empty | Case-insensitive literal substring of ticker, ISIN, or latest name; trimmed; maximum 120 characters |
| `page` | `0` | Zero-based; clamped to 0-1000 |
| `size` | `10` | Maximum 200; values below 1 use 10 |

Results have a fixed ascending order by normalized ticker (instrument key when
ticker is missing), then instrument key to resolve ties. There is no date filter:
positions always use all imported history. Pagination applies to aggregated
instruments, not individual CSV rows. `totalElements` is the number of instruments
matching the status and search filters.

## Response

```json
{
  "brokerageAccountId": "20000000-0000-0000-0000-000000000001",
  "holdings": [
    {
      "instrumentKey": "ISIN:US67066G1040",
      "isin": "US67066G1040",
      "ticker": "NVDA",
      "instrumentName": "NVIDIA",
      "quantity": 3,
      "totalBoughtQuantity": 5,
      "totalSoldQuantity": 2,
      "lastTradedAt": "2026-10-02T10:00:00Z",
      "status": "OPEN"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

An empty account, an account with only cash activity/dividends, or an unmatched
filter returns 200 with an empty `holdings` array. A page beyond the last page
is also empty, retaining the actual filtered total.

## Calculation rules

- These are positions derived from imported buy/sell history, not a live broker
  balance. Average cost, market value, and realized/unrealized profit are outside
  this endpoint's scope.
- The query reads a compact projection of the entire account history in one SQL
  query, ordered by trade instant, import sequence, CSV row, then activity UUID.
  It does not load full entities or lazily fetch a batch for each row.
- `HoldingCalculator` performs the aggregation and is shared with the
  [instrument detail endpoint](instrument-api.md), so both views use the same
  position calculation.
- Buy/sell recognition, absolute share quantities at 10 decimal places, and
  instrument identity are shared with the import holding validator through
  `TradeActivitySupport`. `AccountActivityAction` explicitly recognizes Market,
  Limit, Stop, and Stop limit buys/sells (case-insensitive, ignoring surrounding
  whitespace). Unsupported actions fail instead of being guessed or skipped.
- Purchases increase `totalBoughtQuantity`, sales increase `totalSoldQuantity`,
  and `quantity = totalBoughtQuantity - totalSoldQuantity`. Totals span every
  imported trading cycle, including periods after closing and reopening a position.
- A positive quantity is OPEN; an exactly zero quantity is CLOSED. Zero-share
  rows and non-trade actions do not create a holding or change the last trade time.
  Dividends, interest, card payments, and adjustments do not add shares.
- ISIN takes precedence over ticker; identifiers are trimmed and uppercased.
  Ticker-only history uses a separate `TICKER:` key. It is not automatically
  merged with ISIN-backed history, since a shared ticker does not prove identity.
  Names alone are not identities. Nonzero trades without either ISIN or ticker
  cause a 409 rather than disappearing from the result. New imports reject such
  trades, including zero-share rows, with a 400 and roll back the whole batch.
  Dividend-only instruments have no inferred position.
- The latest nonblank trade ticker/name in chronological order supplies display
  metadata. A backfilled older import does not replace newer trade metadata.
- A missing trade quantity or negative balance at any point in replay returns
  409, including when a later purchase would make the final balance positive.
  This detects inconsistent legacy history instead of publishing a misleading
  result. It does not reconstruct missing transfers, opening balances, splits,
  or other corporate actions.

The endpoint checks account ownership and scopes its history query to both user
and account. It does not write rows, acquire the user's write lock, or create a
holding snapshot table. An import or batch deletion is reflected on the next query.
For the MVP, aggregation happens in Java over the projected history; query cost
grows with the account's history even for a small response page.

## Errors

| Status | Meaning |
| --- | --- |
| 400 | Invalid query/path parameter or search longer than 120 characters |
| 401 | Missing, expired, or invalid session |
| 404 | Missing account or an account owned by another user |
| 409 | Imported trade history cannot produce consistent holdings |

## Verification

`./mvnw verify` runs unit tests plus isolated PostgreSQL and HTTP/session tests.
Holdings coverage includes fractional and signed quantities, all-history
aggregation, closed/reopened positions, status/search before paging, normalized
identifiers, metadata changes/backfills, deterministic equal-time ordering,
imports/deletions, empty/out-of-range pages, invalid legacy history, and account
isolation for guests and registered users.
