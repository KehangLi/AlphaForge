# Frontend deployment to Vercel

The Next.js frontend runs on Vercel and forwards `/api/...` to the Spring Boot
backend on Azure. Use the default Vercel HTTPS domain for the first deployment.

```text
Browser -> https://<frontend>.vercel.app/api/auth/login
        -> https://<backend>.azurecontainerapps.io/api/auth/login
```

The browser keeps the frontend URL. The backend's session cookie is host-only
(no `Domain` attribute), so a cookie returned through the proxy belongs to the
frontend host. Keep `Secure`, `HttpOnly`, `SameSite=Lax`, and `Path=/`. The proxy
must forward `Cookie`, `Set-Cookie`, the CSRF header, query strings, and request
bodies, including multipart CSV uploads. `/api` responses disable browser caching
and Vercel's external rewrite caching.

## Configuration

| Variable | Where | Purpose |
| --- | --- | --- |
| `BACKEND_ORIGIN` | Vercel project environment variables | HTTPS backend origin, without `/api`, credentials, query parameters, or fragment |
| `TRUSTED_PROXY_SECRET` | Vercel server environment variables and Azure secret | Same random 32-256 character value on both; protects forwarded client IP |
| `NEXT_PUBLIC_API_BASE_URL` | Optional `frontend/.env.local` for `next dev` | Override the local browser-to-backend address; ignored in production builds |
| `CORS_ALLOWED_ORIGINS` | Azure Container App environment variables | Exact allowed frontend origins, separated by commas |

For the existing backend, use this ordinary configuration value in Vercel:

```text
BACKEND_ORIGIN=https://alphaforge-backend.yellowforest-0f068776.canadacentral.azurecontainerapps.io
```

`BACKEND_ORIGIN` is read by Next.js configuration during the build. Set it before
deploying and rebuild after changing it. On Vercel, a missing value, HTTP URL,
loopback host, or URL containing a path/credentials/query/fragment fails the
configuration check. Ordinary local/CI builds can omit it and build without
contacting Azure; these builds do not configure an API forwarding destination.

Keep database credentials, registry tokens, and Azure credentials in their
existing backend/deployment secret stores. This frontend needs no database
secrets. Variables prefixed `NEXT_PUBLIC_` are included in browser JavaScript.

## Verify before deploying

From the repository root, use Node.js 24 and run:

```sh
cd frontend
npm ci
npm run lint
npm test
npm run build
```

For a local production check against the local backend, set this value in
`frontend/.env.local` before building:

```text
BACKEND_ORIGIN=http://localhost:8080
```

Then run `npm run build` and `npm run start` from `frontend`, with the backend
running under its `local` profile. The frontend uses `http://localhost:3000/api/...`
and Next.js forwards to the local backend. Allow `http://localhost:3000` in the
local backend's CORS configuration. Local HTTP verification uses the local
backend's non-Secure cookie; HTTPS cookie behavior is verified on Vercel.
Local environment files are ignored by Git; `.env.example` contains no secrets.

## Create the Vercel project

Import the GitHub repository and set:

| Setting | Value |
| --- | --- |
| Framework Preset | Next.js |
| Root Directory | `frontend` |
| Node.js Version | `24.x` |
| Install Command | `npm ci` |
| Build Command | `npm run build` |
| Output Directory | Keep the Next.js default |
| Production Branch | `main`, after the frontend preparation change is merged |

Add `BACKEND_ORIGIN` for the **Production** environment before deploying. Keep
Vercel's system environment variables enabled so the `VERCEL=1` configuration
check runs. The build installs and builds the frontend from Git.

Vercel's Git integration can deploy pushes independently of GitHub Actions.
The current CI workflow is manual; run its checks before the first release.
Connecting Vercel does not make that manual workflow a deployment gate.

## Configure the Azure backend origin allowlist

After Vercel assigns the project's stable production domain, add that exact
origin to `CORS_ALLOWED_ORIGINS` on `alphaforge-backend`, for example:

```text
CORS_ALLOWED_ORIGINS=https://<your-project>.vercel.app
```

Use the real assigned domain, including `https://`, without a path or trailing
slash. Preserve any other origins that are deliberately allowed. Deploy the
Azure configuration change so the backend reads the new value. Proxy requests
can retain the browser's `Origin` header; verify both the proxy and the backend
allowlist instead of assuming a successful Postman call proves browser access.
Keep CSRF protection and the production cookie settings enabled.

The `src/proxy.ts` layer forwards Vercel's client IP using private, authenticated
headers before the existing rewrite runs. Generate `TRUSTED_PROXY_SECRET` with
`openssl rand -hex 32`, store the same value in both deployments, and rebuild the
frontend. Never expose it through a `NEXT_PUBLIC_` variable. Incoming identity
headers are replaced; backend socket-peer fallback protects direct requests.
Verify real-client separation after deploying. See
[abuse-protection.md](abuse-protection.md) for rate-limit defaults and details.

## Preview deployments

Vercel Preview has its own environment settings and hostnames. It does not
automatically inherit Production's `BACKEND_ORIGIN`. A preview without an
explicit backend origin fails the configuration check instead of publishing an
unusable application.

Before enabling a functional preview, choose its backend explicitly and allow
the exact preview origin on that backend if needed. Prefer a separate test
backend/database for preview writes. If a preview uses the production backend,
registrations, guests, and CSV imports create real production data. Do not allow
all `*.vercel.app` origins. Verify on the stable production hostname after the
first release, rather than relying on a temporary deployment URL.

## Browser verification after deployment

Use the production URL and browser developer tools:

1. Open `/`, `/login`, `/register`, and `/app` directly. Confirm JavaScript and CSS
   load and the Network panel shows API requests to the **frontend** host.
2. Request `/api/auth/csrf`. Expect 200 and the session cookie on the frontend
   host with `Secure`, `HttpOnly`, `SameSite=Lax`, and `Path=/`.
3. Register a test user (201), read `/api/auth/me` (200), and refresh `/app`.
   Confirm the identity remains the same. Check that the CSRF header and session
   cookie reach writes; a missing/incorrect CSRF token must still return 403.
4. Log out (204), confirm `/api/auth/me` returns 401, then log in (200). Confirm
   the prior user's workspace is not shown after changing identities.
5. Try the guest flow, create a brokerage account, import a small sample CSV,
   and check pagination and deletion. Confirm multipart bodies survive the proxy.
6. Repeat reads with two different signed-in users. Confirm responses belong to
   the requesting user and are not shared from a CDN cache.
7. Check the first request after Azure scales to zero. A slow cold start must
   recover through the existing retry UI. Investigate repeat 502/503 responses in
   Azure console logs. Vercel external rewrites have a 120-second proxy timeout;
   test representative CSV sizes and import times through the deployed proxy.

The `/health` endpoint remains on Azure; frontend forwarding covers `/api/...`.
Application build checks and local proxy verification do not replace this live
HTTPS/browser verification. Record the deployed commit, frontend URL, backend
image version, and the results before considering the release verified.

## References

- [Next.js external rewrites](https://nextjs.org/docs/app/api-reference/config/next-config-js/rewrites)
- [Vercel rewrite caching](https://vercel.com/docs/routing/rewrites)
- [Vercel Node.js versions](https://vercel.com/docs/functions/runtimes/node-js/node-js-versions)
- [Vercel monorepo root directory](https://vercel.com/docs/monorepos)
- [Next.js environment variables](https://nextjs.org/docs/app/guides/environment-variables)
- [Vercel proxy timeout](https://vercel.com/docs/limits#proxied-request-timeout)
