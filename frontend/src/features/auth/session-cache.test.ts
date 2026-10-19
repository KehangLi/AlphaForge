import assert from "node:assert/strict";
import { test } from "node:test";
import { MutationObserver, QueryClient } from "@tanstack/react-query";

import { authQueryKeys, getCachedUserId, isCurrentUser, replaceCachedSession } from "./session-cache";

test("switching identity removes account, activity and import caches", async () => {
  const client = new QueryClient();
  client.setQueryData(authQueryKeys.session(), { userId: "guest-user", userType: "GUEST" });
  client.setQueryData(["brokerage-accounts", "list"], [{ id: "guest-account" }]);
  client.setQueryData(["account-activities", "list", "guest-account", 0, 10], ["guest-activity"]);
  client.setQueryData(["import-batches", "list", "guest-account"], ["guest-import"]);
  const registered = { userId: "registered-user", userType: "REGISTERED" as const, displayName: "Alex" };

  await replaceCachedSession(client, registered);

  assert.deepEqual(client.getQueryData(authQueryKeys.session()), registered);
  assert.equal(client.getQueryCache().getAll().length, 1);
  client.clear();
});

test("clearing a session stores an anonymous identity and cancels in-flight user data", async () => {
  const client = new QueryClient();
  let requestSignal: AbortSignal | undefined;
  const pending = client.fetchQuery({
    queryKey: ["brokerage-accounts", "list"],
    queryFn: ({ signal }) => {
      requestSignal = signal;
      return new Promise<never>(() => {});
    },
  });
  const cancelled = assert.rejects(pending);

  await replaceCachedSession(client, null);
  await cancelled;

  assert.equal(requestSignal?.aborted, true);
  assert.equal(client.getQueryData(authQueryKeys.session()), null);
  assert.equal(client.getQueryData(["brokerage-accounts", "list"]), undefined);
  client.clear();
});

test("a late mutation from the previous identity cannot update the next user's account cache", async () => {
  const client = new QueryClient();
  const accountKey = ["brokerage-accounts", "list"];
  client.setQueryData(authQueryKeys.session(), { userId: "guest-user", userType: "GUEST", displayName: "Guest" });
  let finishMutation!: (value: string[]) => void;
  let mutationStarted!: () => void;
  const started = new Promise<void>((resolve) => { mutationStarted = resolve; });
  const mutation = new MutationObserver(client, {
    onMutate: () => getCachedUserId(client),
    mutationFn: () => new Promise<string[]>((resolve) => {
      finishMutation = resolve;
      mutationStarted();
    }),
    onSuccess: (accounts, _variables, userId) => {
      if (isCurrentUser(client, userId)) client.setQueryData(accountKey, accounts);
    },
  });
  const pending = mutation.mutate();
  await started;

  await replaceCachedSession(client, { userId: "registered-user", userType: "REGISTERED", displayName: "Alex" });
  client.setQueryData(accountKey, ["registered-account"]);
  finishMutation(["guest-account"]);
  await pending;

  assert.deepEqual(client.getQueryData(accountKey), ["registered-account"]);
  client.clear();
});
