import assert from "node:assert/strict";
import { afterEach, beforeEach, mock, test } from "node:test";

import { createGuestSession, getCurrentSession, login, logout, register } from "../../features/auth/api";
import { ApiError, apiRequest, confirmSession, resetCsrfToken } from "./client";

type FetchCall = { url: string; init: RequestInit };
const user = { userId: "registered-user", userType: "REGISTERED", displayName: "Alex" };

function json(body: unknown, status = 200) {
  return Response.json(body, { status });
}

function csrf(token = "csrf-one") {
  return json({ headerName: "X-CSRF-TOKEN", token });
}

function stubFetch(responses: Array<Response | (() => Promise<Response>)>) {
  const calls: FetchCall[] = [];
  mock.method(globalThis, "fetch", async (input: string | URL | Request, init: RequestInit = {}) => {
    calls.push({ url: String(input), init });
    const response = responses.shift();
    assert.ok(response, `Unexpected request: ${String(input)}`);
    return typeof response === "function" ? response() : response;
  });
  return calls;
}

beforeEach(() => {
  resetCsrfToken();
  confirmSession();
});
afterEach(() => {
  resetCsrfToken();
  confirmSession();
  mock.restoreAll();
});

test("reads include cookies without requesting a CSRF token", async () => {
  const calls = stubFetch([json([])]);
  assert.deepEqual(await apiRequest("/api/brokerage-accounts"), []);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].init.credentials, "include");
  assert.equal(calls[0].init.cache, "no-store");
});

test("writes share a CSRF token request and send its returned header", async () => {
  const calls = stubFetch([csrf(), json({ id: "one" }), new Response(null, { status: 204 })]);
  await Promise.all([
    apiRequest("/api/brokerage-accounts", { method: "POST", body: JSON.stringify({ accountName: "Main" }) }),
    apiRequest("/api/brokerage-accounts/two", { method: "DELETE" }),
  ]);
  assert.equal(calls.filter((call) => call.url.endsWith("/csrf")).length, 1);
  for (const call of calls.slice(1)) {
    assert.equal(new Headers(call.init.headers).get("X-CSRF-TOKEN"), "csrf-one");
    assert.equal(call.init.credentials, "include");
  }
  assert.equal(new Headers(calls[1].init.headers).get("Content-Type"), "application/json");
});

test("multipart uploads carry CSRF without overriding the browser's content type", async () => {
  const calls = stubFetch([csrf(), json({ successRows: 1 })]);
  const body = new FormData();
  body.append("file", new Blob(["Action,Time (UTC)\n"]), "sample.csv");
  await apiRequest("/api/import", { method: "POST", body });
  assert.equal(calls[1].init.body, body);
  assert.equal(new Headers(calls[1].init.headers).has("Content-Type"), false);
  assert.equal(new Headers(calls[1].init.headers).get("X-CSRF-TOKEN"), "csrf-one");
});

test("a CSRF rejection never replays a write and the next user attempt gets a fresh token", async () => {
  const calls = stubFetch([
    csrf(), json({ message: "A valid CSRF token is required" }, 403),
    json(user), csrf("csrf-two"), json({ id: "created" }),
  ]);
  await assert.rejects(apiRequest("/api/brokerage-accounts", { method: "POST" }), {
    message: "Your session changed. Please try again.", status: 403,
  });
  assert.equal(calls.length, 2);
  await getCurrentSession();
  assert.deepEqual(await apiRequest("/api/brokerage-accounts", { method: "POST" }), { id: "created" });
  assert.equal(calls.length, 5);
  assert.equal(new Headers(calls[4].init.headers).get("X-CSRF-TOKEN"), "csrf-two");
});

test("repeated CSRF rejections each stop after one write", async () => {
  const calls = stubFetch([
    csrf(), json({ message: "A valid CSRF token is required" }, 403),
    json(user), csrf("csrf-two"), json({ message: "A valid CSRF token is required" }, 403),
  ]);
  await assert.rejects(apiRequest("/api/account", { method: "DELETE" }), { status: 403 });
  assert.equal(calls.length, 2);
  await getCurrentSession();
  await assert.rejects(apiRequest("/api/account", { method: "DELETE" }), { status: 403 });
  assert.equal(calls.length, 5);
});

test("manual retries cannot write while a changed identity is still being verified", async () => {
  let resolveSession!: (response: Response) => void;
  const sessionResponse = new Promise<Response>((resolve) => { resolveSession = resolve; });
  const calls = stubFetch([
    csrf(), json({ message: "A valid CSRF token is required" }, 403),
    () => sessionResponse, csrf("new-user-csrf"), json({ id: "new-user-account" }),
  ]);

  await assert.rejects(apiRequest("/api/account", { method: "POST" }), { status: 403 });
  const verification = getCurrentSession();
  await assert.rejects(apiRequest("/api/account", { method: "POST" }), {
    message: "Checking your session. Please try again.", status: 403,
  });
  assert.equal(calls.length, 3);

  resolveSession(json({ ...user, userId: "different-user" }));
  await verification;
  assert.equal(calls.length, 3);
  await apiRequest("/api/account", { method: "POST" });
  assert.equal(calls.length, 5);
});

test("permission errors and server failures never replay a write", async () => {
  const calls = stubFetch([csrf(), json({ message: "Access denied" }, 403), json({ message: "Import failed" }, 500)]);
  await assert.rejects(apiRequest("/api/account", { method: "DELETE" }), { message: "Access denied", status: 403 });
  await assert.rejects(apiRequest("/api/import", { method: "POST" }), { message: "Import failed", status: 500 });
  assert.equal(calls.length, 3);
});

test("failed CSRF preparation can be retried without sending the write prematurely", async () => {
  const calls = stubFetch([json({ message: "Temporarily unavailable" }, 503), csrf(), json({ id: "created" })]);
  await assert.rejects(apiRequest("/api/account", { method: "POST" }), { status: 503 });
  assert.equal(calls.length, 1);
  await apiRequest("/api/account", { method: "POST" });
  assert.equal(calls.length, 3);
});

test("login preserves password whitespace and refreshes CSRF for the new session", async () => {
  const calls = stubFetch([csrf(), json(user), csrf("authenticated-csrf"), json([])]);
  assert.deepEqual(await login({ email: "alex@example.com", password: " passphrase with spaces " }), user);
  await apiRequest("/api/account", { method: "POST" });
  assert.equal(JSON.parse(calls[1].init.body as string).password, " passphrase with spaces ");
  assert.equal(new Headers(calls[3].init.headers).get("X-CSRF-TOKEN"), "authenticated-csrf");
});

test("registration and guest creation discard the previous session's token", async () => {
  const calls = stubFetch([
    csrf(), json(user, 201), csrf("registered-csrf"),
    json({ ...user, userType: "GUEST" }), csrf("guest-csrf"), json([]),
  ]);
  await register({ email: "alex@example.com", password: "sample-passphrase-123", displayName: "Alex" });
  await createGuestSession();
  await apiRequest("/api/account", { method: "POST" });
  assert.equal(calls.filter((call) => call.url.endsWith("/csrf")).length, 3);
});

test("logout handles 204 and discards CSRF before the next guest session", async () => {
  const calls = stubFetch([csrf(), new Response(null, { status: 204 }), csrf("next-csrf"), json(user)]);
  assert.equal(await logout(), undefined);
  await createGuestSession();
  assert.equal(new Headers(calls[3].init.headers).get("X-CSRF-TOKEN"), "next-csrf");
});

test("a missing session is normal on public pages but other failures remain errors", async () => {
  stubFetch([json({ message: "A valid user session is required" }, 401), json({ message: "Database unavailable" }, 500)]);
  assert.equal(await getCurrentSession(), null);
  await assert.rejects(getCurrentSession(), { status: 500 });
});

test("invalid login stays local while a protected request reports an expired session", async (context) => {
  const browser = new EventTarget();
  Object.defineProperty(globalThis, "window", { configurable: true, value: browser });
  context.after(() => Reflect.deleteProperty(globalThis, "window"));
  let unauthorizedEvents = 0;
  browser.addEventListener("auth:unauthorized", () => unauthorizedEvents++);
  stubFetch([
    csrf(), json({ message: "Invalid email or password" }, 401),
    json({ message: "A valid user session is required" }, 401),
  ]);
  await assert.rejects(login({ email: "alex@example.com", password: "wrong password" }), { status: 401 });
  assert.equal(unauthorizedEvents, 0);
  await assert.rejects(apiRequest("/api/brokerage-accounts"), { status: 401 });
  assert.equal(unauthorizedEvents, 1);
});

test("a CSRF failure requests a session check without executing under a new identity", async (context) => {
  const browser = new EventTarget();
  Object.defineProperty(globalThis, "window", { configurable: true, value: browser });
  context.after(() => Reflect.deleteProperty(globalThis, "window"));
  let sessionChecks = 0;
  browser.addEventListener("auth:session-changed", () => sessionChecks++);
  const calls = stubFetch([csrf(), json({ message: "A valid CSRF token is required" }, 403)]);
  await assert.rejects(apiRequest("/api/account", { method: "POST" }), { status: 403 });
  assert.equal(sessionChecks, 1);
  assert.equal(calls.length, 2);
});

test("duplicate registration exposes its server error without retrying", async () => {
  const calls = stubFetch([csrf(), json({ message: "An account with this email already exists" }, 409)]);
  await assert.rejects(register({ email: "alex@example.com", password: "sample-passphrase-123", displayName: "Alex" }), {
    message: "An account with this email already exists", status: 409,
  });
  assert.equal(calls.length, 2);
});

test("a cancelled query does not announce an expired session", async () => {
  const controller = new AbortController();
  controller.abort();
  const calls = stubFetch([]);
  await assert.rejects(apiRequest("/api/account", { signal: controller.signal }), { name: "AbortError" });
  assert.equal(calls.length, 0);
});

test("unreadable error responses still produce a consistent API error", async () => {
  stubFetch([new Response("not json", { status: 502, headers: { "Content-Type": "application/json" } })]);
  await assert.rejects(apiRequest("/api/account"), (error) => error instanceof ApiError && error.status === 502);
});
