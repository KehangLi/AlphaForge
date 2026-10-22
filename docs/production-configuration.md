# Backend production configuration and secrets

The backend uses the same application artifact in every environment. Runtime
configuration selects the database and profile; passwords are supplied separately
from the artifact. This step prepares configuration only. Creating Azure resources,
building a Docker image, and deploying the frontend are separate steps.

## Profiles

| Profile | Purpose | Database | Development seed data |
| --- | --- | --- | --- |
| `local` | Local development; the current default | Local PostgreSQL from `compose.yml` | Enabled by default |
| `test` | Automated tests | Isolated PostgreSQL managed by Testcontainers | Disabled |
| `prod` | Deployed backend | Explicitly configured production PostgreSQL | Disabled |

Production must explicitly set `SPRING_PROFILES_ACTIVE=prod`. The current default
remains `local` to preserve local development. Setting both `prod` and `local` or
`test` is rejected before the application opens database connections.

`application.yaml` contains shared settings. Spring also loads the active
profile's YAML file. The production profile keeps Flyway migrations enabled and
Hibernate schema validation enabled; Hibernate must not create or replace tables.
The initial deployment runs Flyway with the configured database account, so that
account needs migration permissions in the application's database/schema. Use an
account scoped to this application, rather than the database server administrator.
A separate migration identity can be introduced later.

## Runtime configuration

| Variable | Required in production | Where to configure in Container Apps | Meaning |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Yes: `prod` | Environment variable | Select production settings |
| `DB_URL` | Yes | Environment variable | `jdbc:postgresql://host[:port]/database`; address only |
| `DB_USERNAME` | Yes | Environment variable | Application database account |
| `DB_PASSWORD` | Yes | Environment variable referencing a Secret | Password for that database account |
| `SESSION_TIMEOUT` | No; defaults to `24h` | Environment variable | Session lifetime |
| `SERVER_PORT` | No; defaults to `8080` | Environment variable | Backend's internal listening port |
| `CORS_ALLOWED_ORIGINS` | No; defaults to empty | Environment variable | Comma-separated browser origins, without paths |

The production startup check rejects missing or blank database settings and
prints variable names rather than connection strings or passwords. It runs
before datasource initialization. The normal Spring datasource property names
are also supported, but deployments should consistently use the `DB_*` names
above. Do not put credentials in a connection URL: JDBC URLs can appear in logs.

`.env.prod.example` documents the deployment variables and deliberately leaves
the password empty. It is a reference, not a working production configuration.
Do not put actual credentials in either example file.

## Database encryption

The production profile supplies these PostgreSQL driver properties through
Hikari, the connection pool:

```yaml
sslmode: verify-full
sslfactory: org.postgresql.ssl.DefaultJavaSSLFactory
```

This requires encryption, a trusted server certificate, and a matching database
hostname. The factory uses Java's trust store. Use the Azure PostgreSQL DNS name,
not its IP address, and a current Java 21 runtime with the appropriate trusted
root CAs. Certificate trust and the real Azure connection must be checked when
the database and deployment runtime exist.

`DB_URL` deliberately accepts a single host and database with no query parameters,
userinfo, or fragment. PostgreSQL URL parameters override separately supplied
driver properties; keeping them out of the URL avoids accidentally replacing
the TLS defaults or embedding a password. Configure any additional driver
properties through `spring.datasource.hikari.data-source-properties` after
review, rather than adding a query string. Do not override production TLS
properties to disable encryption or certificate/hostname verification.

Local and test databases retain their existing non-TLS configuration. Switching
to `prod` against the ordinary local Compose database is not a production smoke
test: that database does not provide the required TLS setup.

## Secrets in Azure Container Apps

For the initial MVP, use a Container Apps Secret and reference it from an
environment variable. This requires no Azure SDK or Azure credentials in the
Java application. Key Vault references are a later option for centralized
management; Microsoft's production guidance recommends Key Vault-backed secrets.

When the container app is created:

1. Under **Security > Secrets**, add a Container Apps secret named `db-password`.
   Supply the real production database password there, using a password manager
   as the owner's secure record. Do not reuse the development database password.
2. In the container's environment variables, add `DB_PASSWORD`, choose
   **Reference a secret**, and select `db-password`. The secret name and the
   environment variable name are different; the reference connects them.
3. Configure `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, and `DB_USERNAME` as ordinary
   environment variables. Do not add local `DEV_*` variables.
4. Deploy the revision. Restrict permission to read secrets and modify the app to
   the identities that actually need it.

Passwords must not be committed, included in Docker build arguments or image
layers, written into workflow YAML, sent to the frontend, or printed in logs.
Variables prefixed `NEXT_PUBLIC_` are for browser-visible frontend configuration
and must never contain backend secrets. Ordinary build/test CI does not need
production database credentials.

To rotate a password, coordinate the change in PostgreSQL and the Container Apps
Secret, then deploy a new revision or restart the active revisions that reference
it. Updating a directly stored Container Apps Secret alone does not refresh
existing running revisions. Verify a new database connection afterwards, not
only an existing pooled connection. Changing an environment variable does not
change the actual PostgreSQL password. Immediate rotation may interrupt service;
use overlapping database accounts if a future deployment needs zero downtime.

## Local environment files

`.env`, `.env.local`, `.env.prod`, and other `.env.*` files are ignored by Git;
the `.example` templates remain trackable. Git ignore rules do not remove a
previously committed secret. If a real secret was exposed, rotate it.

Docker Compose reads `.env` for its variable substitution. Spring Boot itself
does not automatically read that file. Supply variables to the Java process
through your IDE run configuration or your shell environment. For example, with
an optional, trusted local `.env` copied from `.env.example`:

```sh
cp -n .env.example .env  # Preserve an existing .env.
```

After editing it for local development, in a dedicated terminal:

```sh
set -a
source .env
set +a
./mvnw spring-boot:run
```

Sourcing executes shell code; use only your own trusted, shell-compatible file.
Quote values containing spaces or shell-special characters. Keep real production
credentials in Azure rather than copying them into a local development file.
Tests still use `./mvnw test` without sourcing `.env`; see [testing.md](testing.md).

## Backend verification and later frontend integration

During backend deployment, verify startup, database TLS, Flyway migrations,
`GET /health`, and API requests with a non-browser client. An empty CORS allowlist
does not prevent those requests when they contain no browser `Origin` header.
`/health` is a basic process check, not a continuous database readiness probe.

The production session cookie stays `Secure`, `HttpOnly`, and `SameSite=Lax`.
Configure the hosting ingress for HTTPS; the Java container does not need a
separate public TLS certificate when the hosting platform terminates HTTPS.
Production suppresses SQL printing, detailed HTTP request logging, and Spring's
generic error-response exception/stack-trace details. Application API validation
errors still provide their intended messages. Do not enable request-body,
cookie, or credential logging while debugging production.

The agreed frontend design uses one browser-facing domain: `/` serves the
frontend and `/api/...` forwards to this backend. Configure and verify origins,
proxy forwarding, cookies, and CSRF after the frontend exists. Do not weaken
cookie settings or disable CSRF to make the backend deploy independently.

## References

- [Spring Boot externalized configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [PostgreSQL JDBC SSL configuration](https://jdbc.postgresql.org/documentation/ssl/)
- [PostgreSQL JDBC connection parameter precedence](https://jdbc.postgresql.org/documentation/use/)
- [Azure PostgreSQL TLS and trusted roots](https://learn.microsoft.com/en-us/azure/postgresql/security/security-tls)
- [Container Apps secrets, references, and updates](https://learn.microsoft.com/en-us/azure/container-apps/manage-secrets)
