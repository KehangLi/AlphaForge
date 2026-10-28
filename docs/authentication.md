# Session Authentication

The backend uses Spring Security with Spring Session JDBC. Guest and registered
users share the same session cookie and business endpoints.

## Endpoints

| Method | Path | Successful response |
| --- | --- | --- |
| GET | `/api/auth/csrf` | 200: `headerName`, `token` |
| POST | `/api/auth/guest` | 200: guest user details |
| POST | `/api/auth/register` | 201: registered user details |
| POST | `/api/auth/login` | 200: registered user details |
| GET | `/api/auth/me` | 200: current user details |
| POST | `/api/auth/logout` | 204 |

User responses contain only `userId`, `userType`, and `displayName`.
Authentication responses use `Cache-Control: no-store`.

Registration accepts `email`, `password`, and `displayName`. Email is trimmed and
lowercased, the display name is trimmed, and the password is preserved exactly.
Passwords must contain 15-128 characters. Display names must be nonblank and at
most 120 characters; emails must be valid and at most 320 characters.
Login accepts `email` and `password`.

Passwords are stored as salted PBKDF2-HMAC-SHA256 hashes using 600,000 iterations,
with an algorithm prefix for the configured Spring `PasswordEncoder`. Neither
passwords nor their hashes are placed in session attributes or user responses.
The database enforces email uniqueness ignoring case and surrounding spaces.

## Browser Request Flow

1. Use `credentials: "include"` on API requests.
2. Fetch `GET /api/auth/csrf`. This can create an anonymous session cookie.
3. Include the returned token under the returned header name on every POST, PUT,
   PATCH, and DELETE request, including guest creation, registration, login,
   logout, and multipart CSV uploads.
4. Successful guest creation, registration, or login starts a fresh authenticated
   session. Fetch `/api/auth/csrf` again before the next write request because the
   previous token belongs to the old session. Fetch it again after logout too.
5. Read `/api/auth/me` when restoring the current frontend user. A missing or
   expired identity returns 401. Missing or invalid CSRF tokens return 403.

The client does not read the HttpOnly session cookie. The browser stores it and
sends it to the backend. Production cookies require HTTPS; local development
uses the existing `local` profile. Configure allowed frontend origins with
`CORS_ALLOWED_ORIGINS`. This setup assumes the frontend and API are deployed
on the same site, as required by the current `SameSite=Lax` cookie policy.
For deployment variables and secret storage, see
[production-configuration.md](production-configuration.md). The planned frontend
uses a single browser-facing domain with `/api` forwarding to the backend; browser
authentication is verified when that frontend is deployed.

## Account Behavior

Registration creates a separate registered user, including when called from a
guest session. Guest accounts and uploads remain owned by the original guest.
Guest upgrade and data migration are separate features.

Login rotates the current session ID. Failed registration or login preserves
the existing identity. Invalid credentials, unknown emails, and accounts without
a password all return the same 401 message: `Invalid email or password`.

Logout invalidates only the current session and clears its cookie. Other sessions
for the same user remain active. Repeating logout with a valid CSRF token returns
204. User accounts and imported activities are retained.

## Local Seed User

The optional `DEV_USER_PASSWORD` environment variable initializes a password hash
for the local seed user if that user has no password yet. It must meet the same
15-128 character length requirement. Existing password hashes are not replaced
on startup. Leaving it unset keeps the seed user unavailable for password login;
new users can register through the API instead.

## Scope

This change supplies email/password registration, login, session persistence,
request authentication, and CSRF protection. Guest upgrade, email verification,
password reset and OAuth login are separate work items. Authentication throttling
and guest resource limits are documented in [abuse-protection.md](abuse-protection.md).
