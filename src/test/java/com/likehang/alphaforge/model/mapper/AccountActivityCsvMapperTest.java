package com.likehang.alphaforge.model.mapper;

import com.likehang.alphaforge.model.dto.csv.AccountActivityCsvHeaders;
import com.likehang.alphaforge.model.dto.csv.AccountActivityCsvRow;
import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.model.entity.ImportBatch;
import org.junit.jupiter.api.Test;

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
                .isInstanceOf(CsvRowMappingException.class)
                .hasMessageContaining("CSV row 4")
                .hasMessageContaining(AccountActivityCsvHeaders.ACTION)
                .hasMessageContaining("value is required");
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
