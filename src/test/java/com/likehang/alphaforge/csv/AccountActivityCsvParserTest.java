package com.likehang.alphaforge.csv;

import com.likehang.alphaforge.exception.UploadTooLargeException;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.model.entity.ImportBatch;
import org.junit.jupiter.api.Test;

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

    private AccountActivityCsvParseResult parse(String csv, int rows) {
        return parser.parse(csv.getBytes(StandardCharsets.UTF_8), new ImportBatch(account, "test.csv"), account, rows);
    }
}
