import assert from "node:assert/strict";
import { test } from "node:test";

import { createNextConfig } from "./next.config";

test("ordinary CI builds need no backend connection or deployment configuration", async () => {
  const config = createNextConfig({ NODE_ENV: "production" });
  assert.deepEqual(await config.rewrites!(), []);
});

test("Vercel deployments require an explicitly configured backend", () => {
  for (const value of [undefined, "", "   "]) {
    assert.throws(() => createNextConfig({ VERCEL: "1", BACKEND_ORIGIN: value }), /Set BACKEND_ORIGIN/);
  }
});

test("API forwarding preserves the backend API prefix and normalizes the origin", async () => {
  const config = createNextConfig({ VERCEL: "1", BACKEND_ORIGIN: " https://backend.example/ " });
  assert.deepEqual(await config.rewrites!(), [{
    source: "/api/:path*",
    destination: "https://backend.example/api/:path*",
  }]);
});

test("invalid backend configuration fails without echoing its contents", () => {
  for (const value of [
    "not-a-url", "ftp://backend.example", "http://backend.example",
    "https://backend.example/api", "https://backend.example?key=private-value",
    "https://backend.example#private-value", "https://user:private-value@backend.example",
  ]) {
    assert.throws(() => createNextConfig({ BACKEND_ORIGIN: value }), (error) => {
      assert.ok(error instanceof Error);
      assert.match(error.message, /BACKEND_ORIGIN/);
      assert.equal(error.message.includes(value), false);
      assert.equal(error.message.includes("private-value"), false);
      return true;
    });
  }
});

test("local production verification can use a loopback backend", async () => {
  const config = createNextConfig({ BACKEND_ORIGIN: "http://127.0.0.1:8080" });
  assert.deepEqual(await config.rewrites!(), [{
    source: "/api/:path*",
    destination: "http://127.0.0.1:8080/api/:path*",
  }]);
});

test("Vercel deployments cannot accidentally forward to loopback", () => {
  for (const origin of ["http://localhost:8080", "https://localhost", "http://127.0.0.1", "https://[::1]"]) {
    assert.throws(() => createNextConfig({ VERCEL: "1", BACKEND_ORIGIN: origin }), /must use HTTPS/);
  }
});

test("personal API responses opt out of browser and Vercel rewrite caching", async () => {
  const config = createNextConfig({});
  const headers = await config.headers!();
  const api = headers.find((rule) => rule.source === "/api/:path*");
  assert.ok(api);
  assert.ok(api.headers.some(({ key, value }) => key === "Cache-Control" && value.includes("no-store")));
  assert.ok(api.headers.some(({ key, value }) => key === "x-vercel-enable-rewrite-caching" && value === "0"));
});
