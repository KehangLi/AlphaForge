package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.ConflictException;
import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountActivityImportIntegrityService {

    private final AccountActivityRepository activities;

    public AccountActivityImportIntegrityService(AccountActivityRepository activities) {
        this.activities = activities;
    }

    // The caller holds the user's write lock until the import transaction commits.
    public void validate(UUID brokerageAccountId, List<AccountActivity> incoming) {
        Map<String, Row> byId = new HashMap<>();
        Map<Content, Row> byContent = new HashMap<>();
        Map<Content, Row> withoutId = new HashMap<>();
        for (AccountActivity activity : activities.findByBrokerageAccount_Id(brokerageAccountId)) {
            index(row(activity), byId, byContent, withoutId);
        }

        for (AccountActivity activity : incoming) {
            Row row = row(activity);
            Row sameId = row.externalId() == null ? null : byId.get(row.externalId());
            if (sameId != null) {
                if (!sameId.content().equals(row.content())) {
                    throw rejection("Conflicting transaction ID " + row.externalId(), row, sameId);
                }
                throw rejection("Duplicate transaction ID " + row.externalId(), row, sameId);
            }

            // Different nonblank IDs may represent legitimate fills in the same second.
            Row overlap = row.externalId() == null ? byContent.get(row.content()) : withoutId.get(row.content());
            if (overlap != null) {
                throw rejection("Ambiguous duplicate activity without a transaction ID", row, overlap);
            }
            index(row, byId, byContent, withoutId);
        }
    }

    private Row row(AccountActivity activity) {
        String isin = upper(activity.getIsin());
        String ticker = upper(activity.getTicker());
        String instrument = isin != null ? "ISIN:" + isin
                : ticker != null ? "TICKER:" + ticker : "NAME:" + upper(activity.getInstrumentName());
        // Match PostgreSQL's timestamp and numeric precision, not CSV text formatting.
        Instant time = activity.getOccurredAt().toInstant().plusNanos(500).truncatedTo(ChronoUnit.MICROS);
        Content content = new Content(
                upper(activity.getAction()), time, instrument,
                decimal(activity.getNumberOfShares(), 10), decimal(activity.getPricePerShare(), 10),
                upper(activity.getPricePerShareCurrency()), decimal(activity.getExchangeRate(), 10),
                decimal(activity.getResultAmount(), 4), upper(activity.getResultCurrency()),
                decimal(activity.getTotalAmount(), 4), upper(activity.getTotalCurrency()),
                decimal(activity.getWithholdingTax(), 4), upper(activity.getWithholdingTaxCurrency()),
                decimal(activity.getCurrencyConversionFee(), 4), upper(activity.getCurrencyConversionFeeCurrency()),
                upper(activity.getMerchantName()), upper(activity.getMerchantCategory())
        );
        return new Row(trim(activity.getExternalTransactionId()), content, activity);
    }

    private void index(Row row, Map<String, Row> byId, Map<Content, Row> byContent, Map<Content, Row> withoutId) {
        if (row.externalId() != null) {
            byId.putIfAbsent(row.externalId(), row); // if key doesn't exist, then put it
        } else {
            withoutId.putIfAbsent(row.content(), row);
        }
        byContent.putIfAbsent(row.content(), row);
    }

    private ConflictException rejection(String reason, Row incoming, Row existing) {
        AccountActivity previous = existing.activity();
        String source = previous.getImportBatch().getId().equals(incoming.activity().getImportBatch().getId())
                ? " in the same CSV file" : " in batch " + previous.getImportBatch().getId();
        return new ConflictException(reason + " at CSV row " + incoming.activity().getRowNumber()
                + "; matches row " + previous.getRowNumber() + source
                + ". The entire import was rejected; no activities were saved. Check overlapping exports or broker IDs.");
    }

    private BigDecimal decimal(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private String upper(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Row(String externalId, Content content, AccountActivity activity) {}

    private record Content(
            String action, Instant time, String instrument,
            BigDecimal shares, BigDecimal price, String priceCurrency, BigDecimal exchangeRate,
            BigDecimal result, String resultCurrency, BigDecimal total, String totalCurrency,
            BigDecimal withholdingTax, String withholdingTaxCurrency,
            BigDecimal conversionFee, String conversionFeeCurrency, String merchant, String merchantCategory
    ) {}
}

/*
| Condition | Action |
|---|---|
| Same ID, identical transaction details | Duplicate; reject |
| Same ID, different transaction details | Conflict; reject |
| At least one transaction has no ID, but the transaction details match | Suspected duplicate; reject |
| Both transactions have different IDs, even if the transaction details match | Allow; may represent two separate trades |
*/
