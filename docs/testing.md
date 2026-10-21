# Backend tests

## Run the tests

Use Java 21 and a running Docker-compatible container runtime (for example, Docker Desktop):

```sh
./mvnw test
```

There is no need to start the development PostgreSQL service or load `.env`.
The first integration test run downloads the `postgres:16` image if it is not cached.
If Docker is unavailable, integration tests fail rather than falling back to the development database.

To run one integration test class:

```sh
./mvnw -Dtest=RegistrationLoginIntegrationTest test
```

Tests that mock their dependencies still run without Docker, for example:

```sh
./mvnw -Dtest=BrokerageAccountServiceTest test
```

## Database isolation

All Spring integration tests extend `AbstractPostgresIntegrationTest`. It activates the
`test` profile, imports the PostgreSQL container configuration, and clears test data before
each test method. Test-only defaults also select `test` for a plain `@SpringBootTest`, so a
missing container configuration fails at startup instead of defaulting to `local`.

`PostgresTestConfiguration` creates a PostgreSQL 16 container with a dynamic host port and
the `alphaforge_test` database. Spring manages the container for the lifetime of its cached
application context. Classes with the same Spring configuration share that context and its
container; different configurations may create separate containers. No development volume
is mounted and containers are not reused across test runs.

`@ServiceConnection` supplies both JDBC and Flyway connections, taking precedence over
external connection properties. The test profile does not reference `DB_URL`, `DB_USERNAME`,
or `DB_PASSWORD`, and disables development seed data. The production Flyway migrations
create the schema; Hibernate validates it instead of generating a separate test schema.

The shared cleanup script truncates all four business tables and both JDBC session tables,
including anonymous sessions. It runs in a separate, committed transaction before each test,
leaving the schema and Flyway history intact. This also clears data committed by real HTTP
requests, which the test method's transaction cannot roll back. Repository tests retain
their existing transactional rollback. HTTP tests close their clients after each test.

JUnit parallel execution is disabled in `junit-platform.properties` because tests sharing a
database must finish before the next test clears its data.

`DatabaseIsolationIntegrationTest` verifies container connections despite conflicting
external settings and checks that committed business data and sessions are cleared between
test methods while Flyway history remains available.

## Adding tests and running in CI

New tests that load the application and access PostgreSQL should extend
`AbstractPostgresIntegrationTest` and use `@SpringBootTest`. Use
`webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT` for real HTTP tests. Each test
creates its own fixtures after the shared cleanup. Mock-based tests do not need the base class.

The `.github/workflows/ci.yml` workflow runs independent Frontend and Backend jobs
for pull requests and pushes to `main`, and can also be started manually from GitHub Actions.
The Backend job uses Java 21 and Docker on an Ubuntu runner and runs
`./mvnw --batch-mode --no-transfer-progress clean verify` to compile, run all tests,
and package the application. It does not need a separately provisioned PostgreSQL
service or development/production database credentials.

After the first GitHub Actions run, configure the repository's merge rules to
require both the `Frontend` and `Backend` checks to pass before merging.
