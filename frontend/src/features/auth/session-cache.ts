import type { QueryClient } from "@tanstack/react-query";

import type { AuthSession } from "./types";

export const authQueryKeys = {
  all: ["auth"] as const,
  session: () => [...authQueryKeys.all, "session"] as const,
};

// Clear all query caches whose keys don’t start with auth
export function removeCachedUserData(queryClient: QueryClient) {
  queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== authQueryKeys.all[0] });
}

export function getCachedUserId(queryClient: QueryClient) {
  return queryClient.getQueryData<AuthSession | null>(authQueryKeys.session())?.userId;
}

export function isCurrentUser(queryClient: QueryClient, userId: string | undefined) {
  return userId !== undefined && getCachedUserId(queryClient) === userId;
}

//Cancel pending requests, clear stale data, and set the new identity
export async function replaceCachedSession(queryClient: QueryClient, session: AuthSession | null) {
  await queryClient.cancelQueries();
  removeCachedUserData(queryClient);
  queryClient.setQueryData(authQueryKeys.session(), session);
}
