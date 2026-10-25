This is the front-end for AlphaForge.

## Local setup

Use Node.js 24 LTS. The repository-root `.nvmrc` records the major version for
local version managers and the CI workflow; `package.json` declares the
same requirement. With nvm installed, run `nvm install` and `nvm use` from the
repository root. The version file alone does not install or switch Node.js.

On macOS with Homebrew, install `node@24` with `brew install node@24` and add
`export PATH="/opt/homebrew/opt/node@24/bin:$PATH"` to `~/.zprofile` on Apple
Silicon (`/usr/local/opt/node@24/bin` on Intel). Open a new terminal, then check
that `node --version` starts with `v24.`.

From this directory, install dependencies with `npm ci`, then run `npm run dev`
to start the development server.

## Authentication UI

`/login` and `/register` submit to the backend and open `/app` after successful
authentication. Registered users and guests share the real brokerage workspace.
Registration starts an empty workspace; guest accounts and imports are not
migrated. Refreshing `/app` restores the user through `GET /api/auth/me`.

The API client includes session cookies and automatically obtains a CSRF token
before writes, including multipart uploads. It discards the token when the
session changes. A CSRF rejection discards the token, rechecks the session, and
asks the user to retry; writes are never automatically replayed after an error.
The old workspace is hidden and further writes are blocked until that session
check finishes successfully.
Password failures stay on the login form; expired sessions on protected requests
redirect to `/login`.

Identity changes cancel outstanding queries and remove cached account data.
Late mutation results from the previous identity do not update the current cache.
Logout opens a fresh entry page, discarding the previous workspace's React state.
Session cookies are HttpOnly and managed by the browser. Credentials and CSRF
tokens are not persisted in local storage.

With `npm run dev`, the backend defaults to `http://localhost:8080`. Override it
with `NEXT_PUBLIC_API_BASE_URL` in `frontend/.env.local` when needed; see
[.env.example](.env.example). The backend's `CORS_ALLOWED_ORIGINS` must include
the frontend origin (locally `http://localhost:3000`).

Production builds always request `/api/...` on the frontend's own origin,
including CSRF requests and CSV uploads. `NEXT_PUBLIC_API_BASE_URL` is ignored
in production. Next.js forwards those requests to `BACKEND_ORIGIN`, preserving
the `/api` prefix. Set `BACKEND_ORIGIN` to an HTTPS origin in Vercel before
building. This keeps the current `Secure`, `HttpOnly`, `SameSite=Lax` session
cookie usable through one browser-facing domain. API responses opt out of
browser caching and Vercel's external rewrite caching.

See [the Vercel deployment guide](../docs/frontend-deployment.md) for project
settings, Azure origin configuration, preview behavior, and browser verification.

Run `npm test` for request and session-cache tests, `npm run lint` for linting,
and `npm run build` for the production build and TypeScript checks.

## Continuous integration

The repository's `.github/workflows/ci.yml` currently runs independent Frontend
and Backend jobs through manual `workflow_dispatch`. The Frontend job reads Node.js from
the root `.nvmrc`, installs dependencies with `npm ci`, then runs lint, tests, and
the production build. It does not need `BACKEND_ORIGIN`, a running backend, or a
database. Vercel deployment settings are separate from this CI workflow.
