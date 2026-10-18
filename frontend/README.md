This is the front-end for AlphaForge.

Run `npm run dev` from this directory to start the development server.

## Authentication UI

`/login` and `/register` currently demonstrate the email/password form UI. They
validate input locally, show a pending state, and open `/app/preview` on submission.
The preview holds only a mock user's display name and entry mode in React memory.
It never sends or stores credentials, writes the real auth query cache, or creates
a server session. Refreshing the preview clears its mock identity.

The preview displays an existing sample account after sign-in and an empty
workspace after registration. New preview accounts are local React state only.
The existing guest entry and `/app` workspace continue using their existing APIs.

Real registration/login requests, CSRF token handling, server error handling, and
redirects to `/app` belong to the authentication API integration ticket. Replace
the preview submit callback when that integration is implemented. The reusable
`RegisteredSessionMenu` already accepts sign-out pending and error states.
