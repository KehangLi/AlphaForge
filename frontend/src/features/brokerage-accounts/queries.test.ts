import assert from "node:assert/strict";
import { afterEach, beforeEach, mock, test } from "node:test";
import { createElement } from "react";
import { renderToString } from "react-dom/server";
import { QueryClient, QueryClientProvider, QueryObserver } from "@tanstack/react-query";

import { authQueryKeys } from "../auth/session-cache";
import { getInstrumentDetail } from "../instruments/api";
import { instrumentQueryKeys } from "../instruments/queries";
import type { InstrumentDetailResponse } from "../instruments/types";
import { confirmSession, resetCsrfToken } from "../../lib/api/client";
import {
  useDeleteBrokerageAccountMutation,
  useDeleteImportBatchMutation,
  useImportAccountActivitiesMutation,
  useSaveJournalNoteMutation,
} from "./queries";

const ACCOUNT = "account-a";
const OTHER_ACCOUNT = "account-b";
const NVDA = "ISIN:US67066G1040";
const AAPL = "ISIN:US0378331005";
const clients: QueryClient[] = [];

function createClient() {
  const client = new QueryClient({ defaultOptions: { queries: { staleTime: 30_000, retry: false } } });
  client.setQueryData(authQueryKeys.session(), { userId: "user-a", userType: "REGISTERED", displayName: "Alex" });
  clients.push(client);
  return client;
}

function renderHook<T>(client: QueryClient, useTestHook: () => T): T {
  let result: T | undefined;
  function Probe() {
    result = useTestHook();
    return null;
  }
  renderToString(createElement(QueryClientProvider, { client }, createElement(Probe)));
  assert.ok(result);
  return result;
}

function instrumentDetail(quantity = 5): InstrumentDetailResponse {
  return {
    brokerageAccountId: ACCOUNT, instrumentKey: NVDA, isin: "US67066G1040", ticker: "NVDA", instrumentName: "NVIDIA",
    holding: {
      instrumentKey: NVDA, isin: "US67066G1040", ticker: "NVDA", instrumentName: "NVIDIA", quantity,
      totalBoughtQuantity: quantity, totalSoldQuantity: 0, lastTradedAt: "2026-11-01T10:00:00Z", status: "OPEN",
    },
    realizedSummary: {
      sellRecordCount: 1, includedSellRecordCount: 1, excludedSellRecordCount: 0,
      missingResultCount: 0, missingResultCurrencyCount: 0,
      currencies: [{ currency: "EUR", totalResult: quantity * 2, totalProfit: quantity * 2, totalLoss: 0,
        profitableSellCount: 1, losingSellCount: 0, breakevenSellCount: 0 }],
    },
    activities: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true,
  };
}

function seedInstrumentCache(client: QueryClient) {
  const keys = [
    instrumentQueryKeys.detail(ACCOUNT, NVDA, 0, 10),
    instrumentQueryKeys.detail(ACCOUNT, NVDA, 1, 10),
    instrumentQueryKeys.detail(ACCOUNT, AAPL, 0, 20),
  ];
  for (const key of keys) client.setQueryData(key, instrumentDetail());
  const otherKey = instrumentQueryKeys.detail(OTHER_ACCOUNT, NVDA, 0, 10);
  client.setQueryData(otherKey, instrumentDetail());
  return { keys, otherKey };
}

function stubFetch(responses: Array<Response | (() => Promise<Response>)>) {
  const calls: string[] = [];
  mock.method(globalThis, "fetch", async (input: string | URL | Request) => {
    calls.push(String(input));
    const response = responses.shift();
    assert.ok(response, `Unexpected request: ${String(input)}`);
    return typeof response === "function" ? response() : response;
  });
  return calls;
}

function csrf() {
  return Response.json({ headerName: "X-CSRF-TOKEN", token: "test-csrf" });
}

beforeEach(() => {
  resetCsrfToken();
  confirmSession();
});
afterEach(() => {
  clients.splice(0).forEach(client => client.clear());
  resetCsrfToken();
  confirmSession();
  mock.restoreAll();
});

const importResult = { brokerageAccountId: ACCOUNT, importBatchId: "batch-a", status: "COMPLETED", successRows: 1 };
const upload = (client: QueryClient) => renderHook(client, useImportAccountActivitiesMutation).mutateAsync({
  brokerageAccountId: ACCOUNT, file: new File(["csv"], "sample.csv"),
});
const deleteBatch = (client: QueryClient) => renderHook(client, useDeleteImportBatchMutation).mutateAsync({
  brokerageAccountId: ACCOUNT, importBatchId: "batch-a",
});

for (const scenario of [
  { name: "import", run: upload, response: () => Response.json(importResult), fails: false },
  { name: "batch deletion", run: deleteBatch, response: () => new Response(null, { status: 204 }), fails: false },
  { name: "conflicting batch deletion", run: deleteBatch, response: () => Response.json({ message: "Delete newer imports first" }, { status: 409 }), fails: true },
]) {
  test(`${scenario.name} invalidates all instrument pages for only that account, then refetches on return`, { timeout: 5000 }, async () => {
    const client = createClient();
    const { keys, otherKey } = seedInstrumentCache(client);
    assert.equal(client.getQueryState(keys[0])?.isInvalidated, false);
    const calls = stubFetch([csrf(), scenario.response(), Response.json(instrumentDetail(8))]);

    if (scenario.fails) await assert.rejects(scenario.run(client), { status: 409 });
    else await scenario.run(client);

    for (const key of keys) assert.equal(client.getQueryState(key)?.isInvalidated, true);
    assert.equal(client.getQueryState(otherKey)?.isInvalidated, false);

    const observer = new QueryObserver(client, {
      queryKey: keys[0],
      queryFn: ({ signal }) => getInstrumentDetail(ACCOUNT, NVDA, { page: 0, size: 10 }, signal),
    });
    let unsubscribe = () => {};
    try {
      await new Promise<void>((resolve, reject) => {
        unsubscribe = observer.subscribe(result => {
          if (result.isError) reject(result.error);
          if (result.isSuccess && result.data.holding?.quantity === 8) resolve();
        });
      });
      assert.equal(client.getQueryData<InstrumentDetailResponse>(keys[0])?.holding?.quantity, 8);
      assert.equal(client.getQueryData<InstrumentDetailResponse>(keys[0])?.realizedSummary?.currencies[0].totalResult, 16);
      assert.ok(calls[2].endsWith(`/instruments/${encodeURIComponent(NVDA)}?page=0&size=10`));
    } finally {
      unsubscribe();
      observer.destroy();
    }
  });
}

test("deleting an account removes every instrument page without removing another account's details", async () => {
  const client = createClient();
  const { keys, otherKey } = seedInstrumentCache(client);
  stubFetch([csrf(), new Response(null, { status: 204 })]);

  await renderHook(client, useDeleteBrokerageAccountMutation).mutateAsync(ACCOUNT);

  for (const key of keys) assert.equal(client.getQueryState(key), undefined);
  assert.ok(client.getQueryData(otherKey));
});

test("saving a journal note still invalidates every instrument page in the account", async () => {
  const client = createClient();
  const { keys, otherKey } = seedInstrumentCache(client);
  stubFetch([csrf(), Response.json({ activityId: "activity-a", journalNote: "Review", journalNoteUpdatedAt: "2026-11-01T10:00:00Z" })]);

  await renderHook(client, useSaveJournalNoteMutation).mutateAsync({
    brokerageAccountId: ACCOUNT, activityId: "activity-a", journalNote: "Review",
  });

  for (const key of keys) assert.equal(client.getQueryState(key)?.isInvalidated, true);
  assert.equal(client.getQueryState(otherKey)?.isInvalidated, false);
});

test("an import finishing after an identity change leaves the new user's instrument cache alone", async () => {
  const client = createClient();
  const { keys } = seedInstrumentCache(client);
  let finishUpload!: (response: Response) => void;
  let markStarted!: () => void;
  const started = new Promise<void>(resolve => { markStarted = resolve; });
  stubFetch([csrf(), () => new Promise<Response>(resolve => {
    finishUpload = resolve;
    markStarted();
  })]);

  const pending = upload(client);
  await started;
  client.setQueryData(authQueryKeys.session(), { userId: "user-b", userType: "REGISTERED", displayName: "Sam" });
  for (const key of keys) client.setQueryData(key, instrumentDetail(42));
  finishUpload(Response.json(importResult));
  await pending;

  for (const key of keys) {
    assert.equal(client.getQueryState(key)?.isInvalidated, false);
    assert.equal(client.getQueryData<InstrumentDetailResponse>(key)?.holding?.quantity, 42);
  }
});
