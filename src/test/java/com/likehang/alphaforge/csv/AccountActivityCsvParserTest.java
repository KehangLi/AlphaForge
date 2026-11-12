package com.likehang.alphaforge.csv;

import com.likehang.alphaforge.exception.UploadTooLargeException;
import com.likehang.alphaforge.exception.CsvImportException;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.model.entity.ImportBatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountActivityCsvParserTest {

    private final AccountActivityCsvParser parser = new AccountActivityCsvParser(new AccountActivityCsvMapper());
    private final BrokerageAccount account = new BrokerageAccount(new AppUser("test@example.com", "Test"), "Broker", "Test", "EUR");

    @Test
    void invalidRowsStillCountTowardsTheRowLimit() {
        String csv = "Action,Time (UTC),Total,Currency (Total)\n"
                + "Interest on cash,invalid,1,EUR\n".repeat(3);
        assertThatThrownBy(() -> parse(csv, 2)).isInstanceOf(UploadTooLargeException.class);
    }

    @Test
    void errorDetailsAreBoundedWithoutLosingTheFailureCount() {
        String csv = "Action,Time (UTC),Total,Currency (Total)\n"
                + "Interest on cash,invalid,1,EUR\n".repeat(120);
        var result = parse(csv, 150);
        assertThat(result.totalRows()).isEqualTo(120);
        assertThat(result.failedRows()).isEqualTo(120);
        assertThat(result.errors()).hasSize(100);
    }

    @Test
    void quotedNewlinesRemainOneCsvRecord() {
        String csv = "Action,Time (UTC),Total,Currency (Total),Notes\n"
                + "Interest on cash,2026-10-01 00:00:00+00:00,1,EUR,\"first line\nsecond line\"\n";
        assertThat(parse(csv, 1).activities()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Market buy", "Market sell", "Limit buy", "Limit sell", "Stop buy", "Stop sell",
            "Stop limit buy", "Stop limit sell"})
    void tradesWithoutEitherIdentifierAbortTheFileInsteadOfBecomingPartialSuccess(String action) {
        String csv = "Action,Time (UTC),ISIN,Ticker,Name,No. of shares,Total,Currency (Total)\n"
                + "Interest on cash,2026-10-01 00:00:00+00:00,,,,,1,EUR\n"
                + action + ",2026-10-02 00:00:00+00:00, , ,NVIDIA,2,300,EUR\n";

        assertThatThrownBy(() -> parse(csv, 10))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("CSV row 3").hasMessageContaining("ISIN/Ticker");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Limit bye", "Limit buy typo", "Limit sell cancelled", "Buy Sell", "Stock split", " "})
    void unknownOrMissingActionAbortsEvenWhenTheRowAlsoHasAMappingError(String action) {
        String csv = "Action,Time (UTC),ISIN,Ticker,No. of shares,Total,Currency (Total)\n"
                + action + ",invalid,US67066G1040,NVDA,2,300,EUR\n";

        assertThatThrownBy(() -> parse(csv, 10))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("CSV row 2").hasMessageContaining("Action");
    }

    @Test
    void missingInstrumentAbortsEvenWhenTheTradeAlsoHasAnInvalidTimestamp() {
        String csv = "Action,Time (UTC),No. of shares,Total,Currency (Total)\n"
                + "Limit buy,invalid,2,300,EUR\n";

        assertThatThrownBy(() -> parse(csv, 10))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("CSV row 2").hasMessageContaining("ISIN/Ticker");
    }

    @ParameterizedTest
    @ValueSource(strings = {"US67066G1040,", ", nvda "})
    void eitherIdentifierIsEnoughAndActionMatchingIgnoresCaseAndOuterWhitespace(String identifiers) {
        String csv = "Action,Time (UTC),ISIN,Ticker,No. of shares,Total,Currency (Total)\n"
                + " limit BUY ,2026-10-01 00:00:00+00:00," + identifiers + ",2,300,EUR\n";

        var result = parse(csv, 10);
        assertThat(result.activities()).hasSize(1);
        assertThat(result.failedRows()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Interest on cash", "Card debit", "Dividend (Dividend)", "Result adjustment"})
    void supportedNonTradeActivitiesMayHaveNoInstrumentIdentifiers(String action) {
        String csv = "Action,Time (UTC),Total,Currency (Total)\n"
                + action + ",2026-10-01 00:00:00+00:00,1,EUR\n";

        var result = parse(csv, 10);
        assertThat(result.activities()).hasSize(1);
        assertThat(result.errors()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30 10:00:00+00:00", "2026-02-30T10:00:00Z"})
    void reportsInvalidDateAsARowErrorAndDoesNotEmitACorrectedActivity(String timestamp) {
        String csv = "Action,Time (UTC),Total,Currency (Total)\n"
                + "Interest on cash,2026-02-28 10:00:00+00:00,1,EUR\n"
                + "Interest on cash," + timestamp + ",2,EUR\n";

        var result = parse(csv, 10);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.activities()).singleElement()
                .satisfies(activity -> assertThat(activity.getRowNumber()).isEqualTo(2));
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.rowNumber()).isEqualTo(3);
            assertThat(error.columnName()).isEqualTo("Time (UTC)");
            assertThat(error.message()).isEqualTo("invalid timestamp: " + timestamp);
        });
    }

    private AccountActivityCsvParseResult parse(String csv, int rows) {
        return parser.parse(csv.getBytes(StandardCharsets.UTF_8), new ImportBatch(account, "test.csv"), account, rows);
    }
}
