# Authentication rate limits and guest resource quotas

The backend enforces request frequency separately from retained guest data.
Configuration lives under `alphaforge.rate-limits`, `alphaforge.guest-quotas`, and
`alphaforge.uploads` in `application.yaml`. Invalid capacities, durations and upload
sizes fail validation at startup. These limits do not replace session ownership
checks, CSRF, HTTPS, or hosting-level traffic protection.

## Rate limits

| Budget | Default capacity | Refill interval |
| --- | --- | --- |
| Requests per source IP | 120 | 1 minute |
| Login attempts per source IP | 30 | 1 minute |
| Login attempts per normalized email | 10 | 10 minutes |
| Registration requests per source IP | 5 | 1 hour |
| New guest creations per source IP | 5 | 1 hour |
| CSRF reads per source IP | 60 | 1 minute |
| Import attempts per authenticated user, guest or registered | 5 | 1 minute |

Bucket4j refills the full capacity at intervals measured from bucket creation;
this is not an exact sliding-window policy. Independent source-IP and email
budgets apply to login. Successful and failed login attempts consume capacity;
successful authentication does not reset it. Known and unknown emails are treated
the same. Rejected and invalid registration requests consume the IP budget before
password hashing or account creation. Existing guest resumption does not consume
new-guest creation capacity, but still uses the general request budget.

The request filter runs inside Spring Security after CORS and before CSRF checks.
It protects CSRF reads before anonymous sessions are created. Its general budget
also covers malformed writes outside `/api`, which could otherwise trigger
anonymous CSRF session creation. Preflight OPTIONS requests and GET `/health` are
excluded. Service-layer quotas and email/user limits remain active for alternate
service entry points.

Rate exhaustion returns JSON matching `ApiErrorResponse`, HTTP 429,
`Cache-Control: no-store`, and a `Retry-After` value in seconds. The message does
not reveal the email, IP, bucket or remaining capacity. State contains hashed
identity keys and expires after inactivity longer than the longest refill
interval. At the configured key capacity, new identities are refused rather than
evicting blocked buckets and allowing them to bypass their limits.

## Deployment boundary

This release uses a bounded, thread-safe in-memory limiter. Deploy one active
backend replica/revision with all traffic directed to it. Counters reset on a
restart, cold start, new revision, or cache expiry; separate JVMs do not share
budgets. Before scaling out, replace limiter storage with a shared implementation
or enforce equivalent budgets at a trusted gateway. Quotas use PostgreSQL and
remain effective across replicas and restarts.

The test profile disables only rate limiting so existing fixture-heavy tests do
not consume production-sized budgets. Guest quotas and upload bounds stay active.
Dedicated rate-limit HTTP tests explicitly enable throttling and use a controllable
clock, so refill and concurrency tests do not sleep or depend on wall-clock time.

## Trusted client addresses through Vercel

The production path is browser -> Vercel `/api` proxy -> Azure. The backend does
not trust arbitrary `Forwarded`, `X-Forwarded-For`, or Vercel-looking request
headers. Automatic forwarded-header interpretation is disabled to keep the
socket peer address from being replaced before application checks.

The Next.js `src/proxy.ts` layer preserves the existing API rewrite and replaces
incoming `X-Alphaforge-Client-IP` and `X-Alphaforge-Proxy-Token` headers. On Vercel,
it uses the platform-provided single `x-vercel-forwarded-for` address and adds a
server-only shared secret. Missing/invalid forwarding configuration returns 503
instead of sending an unauthenticated client identity. Cookie, CSRF, content type
and multipart bodies keep their existing forwarding behavior.

Set **the same `TRUSTED_PROXY_SECRET` on both deployments**. Generate a random
value with `openssl rand -hex 32`; never commit the output or prefix the variable
with `NEXT_PUBLIC_`. Store the backend value as an Azure Container Apps Secret
and reference it from an environment variable. Add the same value as a Vercel
server environment variable, then rebuild/redeploy the frontend. Production
backend startup and Vercel configuration require this secret; local and tests
can omit it. Values must contain 32-256 characters. Secret property `toString`
output is redacted.

The backend only trusts the supplied literal address when the token matches in
constant time. Otherwise it uses the socket peer, ignoring the asserted client
address. Direct Azure access therefore cannot forge distinct client identities
using arbitrary forwarding headers. It can share an ingress-peer budget; if
direct access is unnecessary, restrict it at the hosting/network boundary too.
Never log the private forwarding headers. HTTPS protects the secret in transit.

Changing `TRUSTED_PROXY_SECRET` requires coordinating the backend and frontend
deployments. An unmatched value makes proxied clients fall back to the shared
socket-peer budget until the configuration matches again. Verify two real
clients receive separate budgets through Vercel after deployment. Local unit
and HTTP tests verify forwarding construction, spoof rejection, and backend
responses; they do not prove the live Vercel/Azure network configuration.

## Guest quotas and import bounds

| Resource | Guest | Registered |
| --- | --- | --- |
| Retained brokerage accounts | 2 per user | No guest account cap |
| Retained import batches, including failed batches | 10 across all accounts | No guest batch cap |
| Retained activities | 5,000 across all accounts | No guest activity cap |
| File size | 1 MiB | 5 MiB |
| CSV records excluding header | 1,000 | 10,000 |

There is also a 5 MiB servlet file limit and a 6 MiB total multipart request
limit, so oversized requests are rejected before service parsing. If upload size
is deliberately changed, configure both `UPLOAD_MAX_FILE_SIZE` and
`UPLOAD_MAX_REQUEST_SIZE` consistently. Service reads are bounded even if a file
reports an incorrect size. CSV records, including invalid rows, count toward the
row limit; quoted newlines are not extra records. At most 100 row-error details
are returned, while total/failed row counts remain accurate.

Before creating an account or importing, the transaction takes a pessimistic
write lock on the owner row, then checks counts and writes in the same transaction.
Concurrent imports into different accounts of one guest therefore share a single
budget. CSV parsing and validation failures that throw an exception roll back the
batch and activities. An ordinary parse result with invalid rows can still create
a FAILED or COMPLETED_WITH_ERRORS batch, and that retained batch consumes space.
Exceeding activity space rejects the entire new import rather than truncating it.

Quota exhaustion returns HTTP 409 with an actionable JSON message. File/record
size limits return HTTP 413. Deleting an account or import releases retained
resource capacity, but does not reset request/import frequency budgets. Registered
users still have basic upload and frequency bounds. Creating another session for
the same guest does not create a separate resource quota.

Guest TTL, automated guest-data deletion, overall tenant retention, CAPTCHA and
distributed rate-limit storage are separate work. Session expiry alone does not
delete retained guest rows.

## Validation

- Limiter unit tests cover exact refill, independent identities, capacity and
  simultaneous requests; no timing sleeps are needed.
- Real HTTP/PostgreSQL tests cover 429/CORS/Retry-After, normalized email throttling,
  registration/guest rejection without extra users or sessions, and IP spoofing.
- Guest quota tests run concurrent account creation and concurrent cross-account
  imports, check rollback, failed-batch counting, deletion recovery and registered
  user exemption.
- Upload tests cover servlet/file limits, invalid-row counts and bounded error
  details. Frontend tests cover trusted forwarding and preservation of existing
  cookie/CSRF/multipart headers.

References: [Bucket4j](https://github.com/bucket4j/bucket4j),
[OWASP authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html),
[Vercel request headers](https://vercel.com/docs/headers/request-headers).
