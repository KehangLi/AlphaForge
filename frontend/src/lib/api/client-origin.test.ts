import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { test } from "node:test";

const clientModule = new URL("./client.ts", import.meta.url).href;

for (const { name, nodeEnvironment, publicOrigin, expectedOrigin } of [
  {
    name: "production reads, CSRF, and writes stay on the frontend origin despite a legacy override",
    nodeEnvironment: "production", publicOrigin: "https://cross-site.example", expectedOrigin: "",
  },
  {
    name: "local development still connects directly to the default backend",
    nodeEnvironment: "development", publicOrigin: undefined, expectedOrigin: "http://localhost:8080",
  },
  {
    name: "local development can still override the backend address",
    nodeEnvironment: "development", publicOrigin: "http://localhost:9090", expectedOrigin: "http://localhost:9090",
  },
] as const) {
  test(name, () => {
    const environment: NodeJS.ProcessEnv = { ...process.env, NODE_ENV: nodeEnvironment };
    if (publicOrigin === undefined) delete environment.NEXT_PUBLIC_API_BASE_URL;
    else environment.NEXT_PUBLIC_API_BASE_URL = publicOrigin;

    const result = spawnSync(process.execPath, ["--import", "tsx", "--input-type=module", "--eval", `
      import assert from "node:assert/strict";
      const calls = [];
      globalThis.fetch = async (url, init) => {
        calls.push({ url, init });
        return Response.json(String(url).endsWith("/csrf")
          ? { headerName: "X-CSRF-TOKEN", token: "test-token" }
          : { ok: true });
      };
      const { apiRequest } = await import(${JSON.stringify(clientModule)});
      await apiRequest("/api/brokerage-accounts");
      await apiRequest("/api/brokerage-accounts", { method: "POST", body: "{}" });
      const origin = ${JSON.stringify(expectedOrigin)};
      assert.deepEqual(calls.map(({ url }) => url), [
        origin + "/api/brokerage-accounts",
        origin + "/api/auth/csrf",
        origin + "/api/brokerage-accounts",
      ]);
      assert.ok(calls.every(({ init }) => init.credentials === "include"));
      assert.equal(new Headers(calls[2].init.headers).get("X-CSRF-TOKEN"), "test-token");
    `], { env: environment, encoding: "utf8", timeout: 15_000 });

    assert.equal(result.error, undefined);
    assert.equal(result.status, 0, result.stderr || result.stdout);
  });
}
