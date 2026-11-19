# Local development

[Documentation](README.md) | [Project overview](../README.md) | [Architecture](architecture.md)

Run PostgreSQL in Docker, the Java backend with Maven Wrapper, and the Next.js
frontend with npm. The checked-in local defaults work together; environment
files and cloud credentials are optional for this workflow.

## Requirements

| Tool | Requirement | Check |
| --- | --- | --- |
| Java | JDK 21 | `java -version` |
| Node.js | 24.x, as recorded in `.nvmrc` | `node --version` |
| npm | Included with Node.js | `npm --version` |
| Docker | Running Docker Desktop or a compatible Docker Engine | `docker info` |
| Docker Compose | Compose v2 | `docker compose version` |

With nvm installed, run `nvm install` and `nvm use` from the repository root.
For an IDE backend run configuration, select JDK 21 and the `local` Spring
profile. A separate Maven installation is unnecessary because `./mvnw` is included.
The first run downloads dependencies and the PostgreSQL image.

Clone the repository, then use its root as the working directory unless a step
explicitly changes it:

```sh
git clone https://github.com/KehangLi/AlphaForge.git
cd AlphaForge
```

## 1. Start PostgreSQL

```sh
docker compose up -d postgres
docker compose ps postgres
docker compose exec postgres pg_isready -U alphaforgeUser -d alphaforge
```

Wait for `accepting connections` before starting the backend. The default
development database is:

| Setting | Local value |
| --- | --- |
| Host and port | `localhost:5432` |
| Database | `alphaforge` |
| Username | `alphaforgeUser` |
| Password | `alphaforgePass` |
| JDBC URL | `jdbc:postgresql://localhost:5432/alphaforge` |
| Storage | Docker Compose's `pgdata` named volume |

These are public development defaults. An existing root `.env` can override the
Compose settings; use the corresponding credentials in commands if customized.

## 2. Start the backend

From the repository root:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Flyway applies pending migrations from `src/main/resources/db/migration` in
version order. On a fresh database, this creates the four business tables and
two Spring Session tables. Hibernate then validates the entity mappings against
that schema. Later starts apply only migrations not already recorded in
`flyway_schema_history`.

After the application has started, check it from another terminal:

```sh
curl -i http://localhost:8080/health
```

Expect HTTP 200 with an empty body and release headers. This is a basic process
check; it does not continuously verify database availability.

## 3. Start the frontend

In a separate terminal, from the repository root:

```sh
cd frontend
npm ci
npm run dev
```

Open [http://localhost:3000](http://localhost:3000). In development, the browser
calls `http://localhost:8080/api/...` directly. Local CORS allows
`http://localhost:3000`, and the local session cookie works over HTTP. Use
`localhost` consistently rather than mixing it with `127.0.0.1`.

No Azure, Vercel, or `TRUSTED_PROXY_SECRET` configuration is needed for these
local development commands. For an optional local container build instead of
running Java on the host, see [containerization](containerization.md).

## 4. Try the complete workflow

1. Choose **Try as guest**, or create a registered account with a password of
   15-128 characters. Both start their own workspace.
2. Add a brokerage account and select it.
3. Download the sample CSV from the page, or use the repository's
   [synthetic sample](../frontend/public/templates/account-activity-sample.csv).
4. Import it and check the upload result and activity pages.
5. Open **Holdings** and **Realized results**, then select a stock to see its
   position, results, and activity history.
6. Save a journal note, refresh, and confirm it is still present.
7. Open **Import history** to undo the latest batch, or sign out and sign back
   in to a registered workspace.

Guest data belongs to its guest session. Registering creates a separate user;
it does not move the guest's accounts or imports.

### Optional seed user

`LocalDevDataSeeder` runs only under `local` when
`DEV_SEED_DATA_ENABLED=true` (the default). It creates or reuses
`dev@alphaforge.local` and two Trading 212 accounts, `Main brokerage` and
`Long term`. It does not create an authenticated browser session, import CSV
data, or attach these accounts to a newly created guest/registered user.

The seed user has no password by default. To initialize one, stop the backend
and restart it from a terminal with a local-only password:

```sh
export DEV_USER_PASSWORD='local-only-example-password'
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Then sign in with `dev@alphaforge.local` and that password. The value must have
15-128 characters. It only initializes a missing hash; changing the environment
variable does not replace an existing password. Set `DEV_SEED_DATA_ENABLED=false`
to skip seeding on later starts; existing records remain.

## Configuration overrides

| Variable | Used by | Purpose |
| --- | --- | --- |
| `DB_NAME` | Compose | Database created when PostgreSQL first initializes |
| `DB_URL` | Backend | JDBC connection URL, including the matching database name |
| `DB_USERNAME`, `DB_PASSWORD` | Compose and backend | Database credentials |
| `SERVER_PORT` | Backend | HTTP port; default `8080` |
| `CORS_ALLOWED_ORIGINS` | Backend | Browser origins; local default `http://localhost:3000` |
| `DEV_SEED_DATA_ENABLED`, `DEV_USER_PASSWORD` | Backend, `local` only | Optional seed data and initial seed-user password |
| `NEXT_PUBLIC_API_BASE_URL` | Frontend, `next dev` only | Browser-visible backend origin; default `http://localhost:8080` |

Docker Compose automatically reads the root `.env` for substitution. Spring Boot
does not read it automatically. If overriding the defaults, create an optional
file without replacing an existing one:

```sh
cp -n .env.example .env
```

After editing that trusted, shell-compatible file, load it in the terminal that
runs the backend:

```sh
set -a
source .env
set +a
docker compose up -d postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

An IDE run configuration must receive the same variables. PostgreSQL's image
initialization settings do not rename databases or change passwords in an
already initialized volume. If credentials change, update PostgreSQL itself
and the backend settings together.

Next.js reads `frontend/.env.local` automatically. From the repository root:

```sh
cp -n frontend/.env.example frontend/.env.local
```

Edit `NEXT_PUBLIC_API_BASE_URL` if needed, then restart `npm run dev`. If changing
the frontend port, update `CORS_ALLOWED_ORIGINS` to match and restart the backend.
If changing the backend port, update the frontend URL too. Production uses
different forwarding and cookie settings; see [production configuration](production-configuration.md)
and [Vercel deployment](frontend-deployment.md).

### Optional local production frontend

To check `next build` and `next start` against the local backend, stop the
frontend dev server and run from the repository root:

```sh
BACKEND_ORIGIN=http://localhost:8080 npm --prefix frontend run build
npm --prefix frontend run start
```

Keep the backend running with its `local` profile. This frontend uses same-origin
`/api` requests and the build's rewrite to `8080`; production ignores
`NEXT_PUBLIC_API_BASE_URL`. Rebuild if `BACKEND_ORIGIN` changes. This checks the
production frontend bundle locally, without enabling the backend's cloud `prod`
profile or its HTTPS-only cookie.

## Inspect the database

Use the connection settings above in an IDE database browser and select the
`alphaforge` database's `public` schema. Or open psql:

```sh
docker compose exec postgres psql -U alphaforgeUser -d alphaforge
```

Inside psql:

```sql
\dt
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
\q
```

Business tables are `app_user`, `brokerage_account`, `import_batch`, and
`account_activity`. Sessions use `spring_session` and `spring_session_attributes`.
The [database diagram](../src/main/java/com/likehang/alphaforge/model/entity/schema.md)
explains their relationships. Keep applied migrations intact; evolve the schema
with a new migration and matching entity changes.

## Call the API with Postman

Keep Postman's cookie jar enabled for `localhost`. Authentication uses cookies,
so a Bearer token is not needed.

1. Send `GET http://localhost:8080/api/auth/csrf`. Save the returned `headerName`
   and `token`; keep the session cookie saved by Postman.
2. Send `POST /api/auth/guest`, or `POST /api/auth/login` with a JSON body
   containing `email` and `password`. Include the CSRF header from step 1.
3. Successful authentication replaces the anonymous session. Fetch
   `GET /api/auth/csrf` again to obtain a token for the new session.
4. Send `GET /api/brokerage-accounts` with the cookie to discover account IDs.
   Read requests need the cookie but no CSRF header. A holdings URL is
   `/api/brokerage-accounts/{brokerageAccountId}/holdings`.
5. For uploads, account changes, journal edits, or logout, send the current
   cookie and CSRF header. Uploads use multipart form-data with a `file` field
   at `/api/brokerage-accounts/{brokerageAccountId}/account-activities/imports`.

Reuse the token while that session remains valid; it is not necessary to fetch
one before every write. The frontend handles these steps through its API client.
See [authentication](authentication.md) for all auth endpoints and session rules.

## Verification and shutdown

From the repository root:

```sh
./mvnw verify
npm --prefix frontend test
npm --prefix frontend run lint
npm --prefix frontend run build
```

Backend integration tests use disposable PostgreSQL containers. They do not
require the development database or a running application. Docker must still be
available. See [backend testing](testing.md) for focused test commands.

Stop the backend and frontend with Ctrl+C in their terminals. Stop the
development database with:

```sh
docker compose stop postgres
```

Its named volume preserves the local data. Running `docker compose up -d postgres`
starts it again.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Database connection refused | Start Docker and PostgreSQL; wait for `pg_isready`; check `DB_URL` and port `5432`. |
| PostgreSQL password authentication fails | Match backend credentials to the existing database; editing `.env` does not change a password in an initialized volume. |
| Java compilation or Node engine error | Check `java -version`, `./mvnw -version`, and `node --version`; select JDK 21 and Node 24. |
| Port already in use | Stop the process using the intended port, or change the corresponding server port, frontend URL, and CORS origin together. |
| Seed accounts are missing after guest entry | The seed user and guest are different owners. Sign in as the seed user or create accounts in the guest workspace. |
| `/api/auth/me` returns 401 before signing in | Expected for an anonymous visitor. Establish a guest or registered session. |
| Writes return 403 | Preserve the cookie and use the current session's CSRF token; fetch a new token after authentication or logout. |
| Browser requests fail but `/health` works | Check the frontend's API origin and backend CORS allowlist; use `localhost` consistently. |
| Import returns 400, 409, 413, or 429 | Read its message: check duplicate/history validation, guest quotas, upload bounds, or request limits. |
