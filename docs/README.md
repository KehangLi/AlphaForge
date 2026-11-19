# Documentation

The [project README](../README.md) introduces AlphaForge, the live demo, and its
core features. These guides describe how to run, understand, test, and deploy
the current implementation.

## Start here

| Goal | Guide |
| --- | --- |
| Run the complete app locally | [Local development](local-development.md): prerequisites, startup, seed users, configuration, database inspection, and troubleshooting |
| Understand the system | [Architecture](architecture.md): request flow, authentication, package responsibilities, import transactions, calculations, and frontend caching |
| Understand persistence | [Database schema](../src/main/java/com/likehang/alphaforge/model/entity/schema.md): relationships, constraints, sessions, and migrations |
| Work on the UI | [Frontend guide](../frontend/README.md): routes, source entry points, request handling, and checks |

Suggested first read: local development, architecture, then the API guide for
the feature you are changing.

## Behavior and APIs

| Topic | Guide |
| --- | --- |
| Guest access, registration, login, logout, cookies, and CSRF | [Authentication](authentication.md) |
| Duplicate/conflicting rows, validation, concurrent writes, and undoing imports | [Import integrity](import-integrity.md) |
| Open/closed holdings, instrument identity, and calculation rules | [Holdings API](holdings-api.md) |
| Single-instrument history/results and editable transaction notes | [Instrument detail and journal API](instrument-api.md) |
| UTC sale-date filters, currency summaries, and instrument contributions | [Account realized trading API](account-realized-trading-api.md) |
| Request rate limits, guest quotas, upload bounds, and trusted proxy headers | [Abuse protection](abuse-protection.md) |

## Verification and delivery

| Topic | Guide |
| --- | --- |
| Testcontainers, isolated databases, unit/HTTP tests, and CI | [Backend testing](testing.md) |
| Dependency, code, secret, and container-image scans | [Security checks](security-checks.md) |
| Profiles, production database TLS, environment variables, and secrets | [Production configuration](production-configuration.md) |
| Image build, CPU architecture, and a disposable local smoke environment | [Backend containerization](containerization.md) |
| Vercel project settings, API forwarding, previews, and cookie verification | [Frontend deployment](frontend-deployment.md) |
| GHCR/Azure releases, health verification, and image rollback | [Backend release and rollback](backend-release.md) |

API examples use the current session cookie and CSRF scheme. Establish a guest
or registered session before calling protected endpoints; a resource ID alone
does not grant access. The [Postman walkthrough](local-development.md#call-the-api-with-postman)
covers this sequence.
