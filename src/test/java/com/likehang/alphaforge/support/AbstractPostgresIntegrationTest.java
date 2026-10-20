package com.likehang.alphaforge.support;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
// Cleanup must commit independently of each test's transaction and HTTP requests.
@Sql(
        scripts = "/db/cleanup.sql",
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
)
public abstract class AbstractPostgresIntegrationTest {
}
