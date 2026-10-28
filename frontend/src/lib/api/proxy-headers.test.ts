import assert from "node:assert/strict";
import { test } from "node:test";
import { NextRequest } from "next/server";

import { backendProxyHeaders } from "./proxy-headers";
import { proxy } from "../../proxy";

const secret = "test-proxy-secret-with-at-least-32-characters";

test("proxy replaces spoofed identity headers using only Vercel's client address", () => {
  const headers = backendProxyHeaders(new Headers({
    "x-alphaforge-proxy-token": "attacker-token",
    "x-alphaforge-client-ip": "192.0.2.99",
    "x-forwarded-for": "192.0.2.98",
    "x-vercel-forwarded-for": "203.0.113.10",
    "cookie": "ALPHAFORGE_SESSION=example",
    "x-csrf-token": "example-token",
    "content-type": "multipart/form-data; boundary=example",
  }), { VERCEL: "1", TRUSTED_PROXY_SECRET: secret });
  assert.equal(headers.get("x-alphaforge-client-ip"), "203.0.113.10");
  assert.equal(headers.get("x-alphaforge-proxy-token"), secret);
  assert.equal(headers.get("cookie"), "ALPHAFORGE_SESSION=example");
  assert.equal(headers.get("x-csrf-token"), "example-token");
  assert.equal(headers.get("content-type"), "multipart/form-data; boundary=example");
});

test("outside Vercel no untrusted request can supply the private proxy headers", () => {
  const headers = backendProxyHeaders(new Headers({
    "x-alphaforge-proxy-token": secret, "x-alphaforge-client-ip": "203.0.113.10",
  }), {});
  assert.equal(headers.has("x-alphaforge-proxy-token"), false);
  assert.equal(headers.has("x-alphaforge-client-ip"), false);
});

test("invalid deployment secrets or missing and malformed client addresses fail closed", () => {
  for (const address of [undefined, "unknown", "127.0.0.1, 192.0.2.1", "example.com"]) {
    assert.throws(() => backendProxyHeaders(new Headers(address ? { "x-vercel-forwarded-for": address } : {}), {
      VERCEL: "1", TRUSTED_PROXY_SECRET: secret,
    }), /not configured/);
  }
  for (const value of [undefined, "short"]) {
    assert.throws(() => backendProxyHeaders(new Headers({ "x-vercel-forwarded-for": "203.0.113.10" }), {
      VERCEL: "1", TRUSTED_PROXY_SECRET: value,
    }), /not configured/);
  }
});

test("IPv6 addresses are accepted as one client address", () => {
  const headers = backendProxyHeaders(new Headers({ "x-vercel-forwarded-for": "2001:db8::1" }), {
    VERCEL: "1", TRUSTED_PROXY_SECRET: secret,
  });
  assert.equal(headers.get("x-alphaforge-client-ip"), "2001:db8::1");
});

test("the Next proxy forwards authenticated identity as upstream request headers", (context) => {
  const originalVercel = process.env.VERCEL;
  const originalSecret = process.env.TRUSTED_PROXY_SECRET;
  process.env.VERCEL = "1";
  process.env.TRUSTED_PROXY_SECRET = secret;
  context.after(() => {
    if (originalVercel === undefined) delete process.env.VERCEL;
    else process.env.VERCEL = originalVercel;
    if (originalSecret === undefined) delete process.env.TRUSTED_PROXY_SECRET;
    else process.env.TRUSTED_PROXY_SECRET = originalSecret;
  });
  const response = proxy(new NextRequest("https://frontend.example/api/auth/guest", {
    headers: { "x-vercel-forwarded-for": "203.0.113.10", cookie: "ALPHAFORGE_SESSION=test" },
  }));
  assert.equal(response.headers.get("x-middleware-request-x-alphaforge-client-ip"), "203.0.113.10");
  assert.equal(response.headers.get("x-middleware-request-x-alphaforge-proxy-token"), secret);
  assert.equal(response.headers.get("x-middleware-request-cookie"), "ALPHAFORGE_SESSION=test");
  assert.equal(response.headers.has("x-alphaforge-proxy-token"), false);
});

test("the Next proxy returns a non-cacheable 503 instead of forwarding an unverified address", async (context) => {
  const originalVercel = process.env.VERCEL;
  const originalSecret = process.env.TRUSTED_PROXY_SECRET;
  process.env.VERCEL = "1";
  process.env.TRUSTED_PROXY_SECRET = secret;
  context.after(() => {
    if (originalVercel === undefined) delete process.env.VERCEL;
    else process.env.VERCEL = originalVercel;
    if (originalSecret === undefined) delete process.env.TRUSTED_PROXY_SECRET;
    else process.env.TRUSTED_PROXY_SECRET = originalSecret;
  });
  const response = proxy(new NextRequest("https://frontend.example/api/auth/csrf"));
  assert.equal(response.status, 503);
  assert.equal(response.headers.get("Cache-Control"), "no-store");
  assert.equal((await response.text()).includes(secret), false);
});
