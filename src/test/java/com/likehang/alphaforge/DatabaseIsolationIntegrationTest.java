package com.likehang.alphaforge;

import com.likehang.alphaforge.config.LocalDevDataSeeder;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.AppUserType;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.model.entity.ImportBatch;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.repository.ImportBatchRepository;
import com.likehang.alphaforge.support.AbstractPostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "DB_URL=jdbc:postgresql://127.0.0.1:1/not_the_test_database",
        "DB_USERNAME=external_user",
        "DB_PASSWORD=external_password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/not_the_test_database",
        "spring.datasource.username=external_user",
        "spring.datasource.password=external_password",
        "spring.flyway.url=jdbc:postgresql://127.0.0.1:1/not_the_test_database",
        "spring.flyway.user=external_user",
        "spring.flyway.password=external_password"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseIsolationIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final List<String> DATA_TABLES = List.of(
            "app_user", "brokerage_account", "import_batch", "account_activity",
            "spring_session", "spring_session_attributes"
    );

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PostgreSQLContainer postgres;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private BrokerageAccountRepository brokerageAccountRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private JdbcIndexedSessionRepository sessionRepository;

    @Test
    @Order(1)
    void usesContainerDespiteExternalConnectionSettingsAndCommitsTestData() throws Exception {
        assertThat(environment.getDefaultProfiles()).containsExactly("test");
        assertThat(environment.getActiveProfiles()).containsExactly("test");
        assertThat(context.getBeansOfType(LocalDevDataSeeder.class)).isEmpty();
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(postgres.getJdbcUrl());
            assertThat(connection.getCatalog()).isEqualTo("alphaforge_test");
            assertThat(connection.getMetaData().getUserName()).isEqualTo(postgres.getUsername());
        }
        assertDataTablesAreEmpty();

        AppUser user = appUserRepository.saveAndFlush(new AppUser(null, "Isolation test", AppUserType.GUEST));
        BrokerageAccount account = brokerageAccountRepository.saveAndFlush(
                new BrokerageAccount(user, "Test broker", "Test account", "EUR")
        );
        ImportBatch batch = importBatchRepository.saveAndFlush(new ImportBatch(account, "test.csv"));
        jdbcTemplate.update("""
                INSERT INTO account_activity (
                    id, import_batch_id, brokerage_account_id, row_number, action,
                    occurred_at, total_amount, total_currency, created_at
                ) VALUES (?, ?, ?, 1, 'Interest on cash', now(), 1, 'EUR', now())
                """, UUID.randomUUID(), batch.getId(), account.getId());

        var authenticatedSession = sessionRepository.createSession();
        ((Session) authenticatedSession).setAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, user.getId().toString()
        );
        sessionRepository.save(authenticatedSession);
        var anonymousSession = sessionRepository.createSession();
        ((Session) anonymousSession).setAttribute("csrfToken", "test-token");
        sessionRepository.save(anonymousSession);

        // Intentionally leave committed data behind to exercise the next test's cleanup.
        for (String table : DATA_TABLES) {
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class))
                    .as("persisted rows in %s", table).isPositive();
        }
    }

    @Test
    @Order(2)
    void startsWithEmptyDataAndPreservesFlywayHistory() {
        assertDataTablesAreEmpty();
        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.info().pending()).isEmpty();
    }

    private void assertDataTablesAreEmpty() {
        for (String table : DATA_TABLES) {
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class))
                    .as("rows in %s before the test", table).isZero();
        }
    }
}
