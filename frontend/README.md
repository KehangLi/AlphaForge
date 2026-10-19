This is the front-end for AlphaForge.

Run `npm run dev` from this directory to start the development server.

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

The backend defaults to `http://localhost:8080`. Override it with
`NEXT_PUBLIC_API_BASE_URL` when needed. Its `CORS_ALLOWED_ORIGINS` must include
the frontend origin (locally `http://localhost:3000`). Production requires HTTPS
and frontend/API hosts on the same site for the current `SameSite=Lax` cookies.

Run `npm test` for request and session-cache tests, `npm run lint` for linting,
and `npm run build` for the production build and TypeScript checks.
