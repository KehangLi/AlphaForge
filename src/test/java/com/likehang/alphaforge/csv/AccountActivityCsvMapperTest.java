package com.likehang.alphaforge.csv;

import com.likehang.alphaforge.csv.dto.AccountActivityCsvHeaders;
import com.likehang.alphaforge.csv.dto.AccountActivityCsvRow;
import com.likehang.alphaforge.exception.CsvImportException;
import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.model.entity.ImportBatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountActivityCsvMapperTest {

    private final AccountActivityCsvMapper mapper = new AccountActivityCsvMapper();

    @Test
    void mapsCsvRowToAccountActivity() {
        AccountActivityCsvRow row = AccountActivityCsvRow.fromColumns(sampleLimitBuyColumns());
        BrokerageAccount brokerageAccount = sampleBrokerageAccount();
        ImportBatch importBatch = new ImportBatch(brokerageAccount, "sample.csv");

        AccountActivity accountActivity = mapper.toEntity(row, importBatch, brokerageAccount, 4);

        assertThat(accountActivity.getImportBatch()).isSameAs(importBatch);
        assertThat(accountActivity.getBrokerageAccount()).isSameAs(brokerageAccount);
        assertThat(accountActivity.getRowNumber()).isEqualTo(4);
        assertThat(accountActivity.getExternalTransactionId()).isEqualTo("EOF53510842405");
        assertThat(accountActivity.getAction()).isEqualTo("Limit buy");
        assertThat(accountActivity.getOccurredAt()).isEqualTo(OffsetDateTime.parse("2026-07-01T13:35:43Z"));
        assertThat(accountActivity.getIsin()).isEqualTo("US21873S1087");
        assertThat(accountActivity.getTicker()).isEqualTo("CRWV");
        assertThat(accountActivity.getInstrumentName()).isEqualTo("CoreWeave");
        assertThat(accountActivity.getNumberOfShares()).isEqualByComparingTo(new BigDecimal("1.0000000000"));
        assertThat(accountActivity.getPricePerShare()).isEqualByComparingTo(new BigDecimal("88.4900000000"));
        assertThat(accountActivity.getPricePerShareCurrency()).isEqualTo("USD");
        assertThat(accountActivity.getExchangeRate()).isEqualByComparingTo(new BigDecimal("1.13901403"));
        assertThat(accountActivity.getResultAmount()).isNull();
        assertThat(accountActivity.getResultCurrency()).isNull();
        assertThat(accountActivity.getTotalAmount()).isEqualByComparingTo(new BigDecimal("77.81"));
        assertThat(accountActivity.getTotalCurrency()).isEqualTo("EUR");
        assertThat(accountActivity.getCurrencyConversionFee()).isEqualByComparingTo(new BigDecimal("0.12"));
        assertThat(accountActivity.getCurrencyConversionFeeCurrency()).isEqualTo("EUR");
        assertThat(accountActivity.getRawRowHash()).hasSize(64);
    }

    @Test
    void mapsBlankOptionalFieldsToNull() {
        Map<String, String> columns = new HashMap<>();
        columns.put(AccountActivityCsvHeaders.ACTION, "Interest on cash");
        columns.put(AccountActivityCsvHeaders.TIME_UTC, "2026-07-01 01:05:30+00:00");
        columns.put(AccountActivityCsvHeaders.NOTES, "Interest on cash");
        columns.put(AccountActivityCsvHeaders.ID, "019f1b35-ac33-79d5-a7d9-994b1cfaa727");
        columns.put(AccountActivityCsvHeaders.TOTAL, "0.03");
        columns.put(AccountActivityCsvHeaders.TOTAL_CURRENCY, "EUR");

        AccountActivityCsvRow row = AccountActivityCsvRow.fromColumns(columns);
        BrokerageAccount brokerageAccount = sampleBrokerageAccount();
        ImportBatch importBatch = new ImportBatch(brokerageAccount, "sample.csv");

        AccountActivity accountActivity = mapper.toEntity(row, importBatch, brokerageAccount, 2);

        assertThat(accountActivity.getTicker()).isNull();
        assertThat(accountActivity.getNumberOfShares()).isNull();
        assertThat(accountActivity.getPricePerShare()).isNull();
        assertThat(accountActivity.getTotalAmount()).isEqualByComparingTo(new BigDecimal("0.03"));
        assertThat(accountActivity.getTotalCurrency()).isEqualTo("EUR");
    }

    @Test
    void throwsHelpfulExceptionForInvalidDecimal() {
        Map<String, String> columns = sampleLimitBuyColumns();
        columns.put(AccountActivityCsvHeaders.TOTAL, "not-a-number");
        AccountActivityCsvRow row = AccountActivityCsvRow.fromColumns(columns);
        BrokerageAccount brokerageAccount = sampleBrokerageAccount();
        ImportBatch importBatch = new ImportBatch(brokerageAccount, "sample.csv");

        assertThatThrownBy(() -> mapper.toEntity(row, importBatch, brokerageAccount, 4))
                .isInstanceOf(CsvRowMappingException.class)
                .hasMessageContaining("CSV row 4")
                .hasMessageContaining(AccountActivityCsvHeaders.TOTAL)
                .hasMessageContaining("invalid decimal");
    }

    @Test
    void throwsHelpfulExceptionForMissingRequiredValue() {
        Map<String, String> columns = sampleLimitBuyColumns();
        columns.put(AccountActivityCsvHeaders.ACTION, " ");
        AccountActivityCsvRow row = AccountActivityCsvRow.fromColumns(columns);
        BrokerageAccount brokerageAccount = sampleBrokerageAccount();
        ImportBatch importBatch = new ImportBatch(brokerageAccount, "sample.csv");

        assertThatThrownBy(() -> mapper.toEntity(row, importBatch, brokerageAccount, 4))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("CSV row 4")
                .hasMessageContaining(AccountActivityCsvHeaders.ACTION)
                .hasMessageContaining("value is required");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            2026-07-01 13:35:43+00:00 | 2026-07-01T13:35:43Z
            2026-02-28 23:59:59+00:00 | 2026-02-28T23:59:59Z
            2024-02-29 10:00:00+00:00 | 2024-02-29T10:00:00Z
            2000-02-29 10:00:00+00:00 | 2000-02-29T10:00:00Z
            2026-04-30 10:00:00+00:00 | 2026-04-30T10:00:00Z
            2026-07-01 15:35:43+02:00 | 2026-07-01T15:35:43+02:00
            2026-07-01T13:35:43Z | 2026-07-01T13:35:43Z
            2024-02-29T10:00:00Z | 2024-02-29T10:00:00Z
            2026-07-01T13:35:43.123456789Z | 2026-07-01T13:35:43.123456789Z
            2026-07-01T08:35:43-05:00 | 2026-07-01T08:35:43-05:00
            """)
    void parsesValidDatesWithoutChangingTheirDateTimeOrOffset(String timestamp, String expected) {
        Map<String, String> columns = sampleLimitBuyColumns();
        columns.put(AccountActivityCsvHeaders.TIME_UTC, timestamp);
        BrokerageAccount account = sampleBrokerageAccount();

        AccountActivity activity = mapper.toEntity(AccountActivityCsvRow.fromColumns(columns),
                new ImportBatch(account, "sample.csv"), account, 4);

        assertThat(activity.getOccurredAt()).isEqualTo(OffsetDateTime.parse(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-02-30 10:00:00+00:00",
            "2026-02-29 10:00:00+00:00",
            "1900-02-29 10:00:00+00:00",
            "2026-04-31 10:00:00+00:00",
            "2026-00-01 10:00:00+00:00",
            "2026-13-01 10:00:00+00:00",
            "2026-01-00 10:00:00+00:00",
            "2026-01-32 10:00:00+00:00",
            "2026-07-01 24:00:00+00:00",
            "2026-07-01 10:60:00+00:00",
            "2026-07-01 10:00:60+00:00",
            "2026-07-01 10:00:00",
            "2026-02-30T10:00:00Z",
            "2026-02-29T10:00:00+02:00",
            "2026-07-01T24:00:00Z"
    })
    void rejectsInvalidTimestampsInsteadOfCorrectingThem(String timestamp) {
        Map<String, String> columns = sampleLimitBuyColumns();
        columns.put(AccountActivityCsvHeaders.TIME_UTC, timestamp);
        BrokerageAccount account = sampleBrokerageAccount();

        assertThatThrownBy(() -> mapper.toEntity(AccountActivityCsvRow.fromColumns(columns),
                new ImportBatch(account, "sample.csv"), account, 4))
                .isInstanceOfSatisfying(CsvRowMappingException.class, exception -> {
                    assertThat(exception.getRowNumber()).isEqualTo(4);
                    assertThat(exception.getColumnName()).isEqualTo(AccountActivityCsvHeaders.TIME_UTC);
                    assertThat(exception.getReason()).isEqualTo("invalid timestamp: " + timestamp);
                });
    }

    private static BrokerageAccount sampleBrokerageAccount() {
        AppUser user = new AppUser("dev@alphaforge.local", "Local Dev User");
        return new BrokerageAccount(user, "Manual CSV Upload", "Default Local Account", "USD");
    }

    private static Map<String, String> sampleLimitBuyColumns() {
        Map<String, String> columns = new HashMap<>();
        columns.put(AccountActivityCsvHeaders.ACTION, "Limit buy");
        columns.put(AccountActivityCsvHeaders.TIME_UTC, "2026-07-01 13:35:43+00:00");
        columns.put(AccountActivityCsvHeaders.ISIN, "US21873S1087");
        columns.put(AccountActivityCsvHeaders.TICKER, "crwv");
        columns.put(AccountActivityCsvHeaders.NAME, "CoreWeave");
        columns.put(AccountActivityCsvHeaders.ID, "EOF53510842405");
        columns.put(AccountActivityCsvHeaders.NUMBER_OF_SHARES, "1.0000000000");
        columns.put(AccountActivityCsvHeaders.PRICE_PER_SHARE, "88.4900000000");
        columns.put(AccountActivityCsvHeaders.PRICE_PER_SHARE_CURRENCY, "usd");
        columns.put(AccountActivityCsvHeaders.EXCHANGE_RATE, "1.13901403");
        columns.put(AccountActivityCsvHeaders.RESULT, "");
        columns.put(AccountActivityCsvHeaders.RESULT_CURRENCY, "");
        columns.put(AccountActivityCsvHeaders.TOTAL, "77.81");
        columns.put(AccountActivityCsvHeaders.TOTAL_CURRENCY, "eur");
        columns.put(AccountActivityCsvHeaders.WITHHOLDING_TAX, "");
        columns.put(AccountActivityCsvHeaders.WITHHOLDING_TAX_CURRENCY, "");
        columns.put(AccountActivityCsvHeaders.CURRENCY_CONVERSION_FEE, "0.12");
        columns.put(AccountActivityCsvHeaders.CURRENCY_CONVERSION_FEE_CURRENCY, "eur");
        return columns;
    }
}
