package com.likehang.alphaforge;

import com.likehang.alphaforge.support.AbstractPostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ImportIntegrityMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void upgradesV4DataWithoutLosingRowsAndBackfillsStableOrder() {
        String schema = schema();
        try {
            UUID account = createV4Account(schema);
            UUID earlier = UUID.fromString("10000000-0000-0000-0000-000000000001");
            UUID later = UUID.fromString("20000000-0000-0000-0000-000000000001");
            batch(schema, account, later, "b");
            batch(schema, account, earlier, "a");
            migrate(schema, "latest");

            assertThat(jdbc.queryForList("select id from " + schema + ".import_batch order by import_sequence", UUID.class))
                    .containsExactly(earlier, later);
            assertThat(jdbc.queryForList("select import_sequence from " + schema + ".import_batch order by import_sequence", Long.class))
                    .containsExactly(1L, 2L);
            batch(schema, account, UUID.randomUUID(), "c");
            assertThat(jdbc.queryForObject("select max(import_sequence) from " + schema + ".import_batch", Long.class))
                    .isEqualTo(3L);
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"file", "transaction"})
    void refusesHistoricalDuplicatesAndLeavesV4RowsUntouched(String duplicate) {
        String schema = schema();
        try {
            UUID account = createV4Account(schema);
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            batch(schema, account, first, "a");
            batch(schema, account, second, duplicate.equals("file") ? "a" : "b");
            if (duplicate.equals("transaction")) {
                for (UUID batch : List.of(first, second)) {
                    jdbc.update("insert into " + schema + ".account_activity (id, import_batch_id, brokerage_account_id,"
                            + " row_number, external_transaction_id, action, occurred_at, total_amount, total_currency, created_at)"
                            + " values (?, ?, ?, 2, 'DUPLICATE', 'Interest on cash', now(), 1, 'EUR', now())",
                            UUID.randomUUID(), batch, account);
                }
            }
            assertThatThrownBy(() -> migrate(schema, "latest")).isInstanceOf(FlywayException.class);
            assertThat(jdbc.queryForObject("select count(*) from " + schema + ".import_batch", Long.class)).isEqualTo(2L);
            assertThat(jdbc.queryForObject("select count(*) from " + schema + ".flyway_schema_history where version = '5'", Long.class))
                    .isZero();
            assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = ?"
                    + " and table_name = 'import_batch' and column_name = 'import_sequence'", Long.class, schema)).isZero();
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    private UUID createV4Account(String schema) {
        migrate(schema, "4");
        UUID user = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".app_user (id, email, display_name, user_type, created_at, updated_at)"
                + " values (?, 'migration@example.com', 'Migration', 'REGISTERED', now(), now())", user);
        jdbc.update("insert into " + schema + ".brokerage_account (id, user_id, broker_name, account_name, base_currency, created_at, updated_at)"
                + " values (?, ?, 'Trading 212', 'Main', 'EUR', now(), now())", account, user);
        return account;
    }

    private void batch(String schema, UUID account, UUID id, String hash) {
        jdbc.update("insert into " + schema + ".import_batch (id, brokerage_account_id, original_filename, file_hash,"
                + " status, total_rows, success_rows, failed_rows, created_at, updated_at)"
                + " values (?, ?, 'migration.csv', ?, 'COMPLETED', 0, 0, 0, '2026-10-01T00:00:00Z', now())",
                id, account, hash.repeat(64));
    }

    private void migrate(String schema, String target) {
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load().migrate();
    }

    private String schema() {
        return "integrity_" + UUID.randomUUID().toString().replace("-", "");
    }
}
