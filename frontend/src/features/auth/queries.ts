"use client";

import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { createGuestSession, getCurrentSession, login, logout, register } from "./api";
import { authQueryKeys, removeCachedUserData, replaceCachedSession } from "./session-cache";
import type { AuthSession } from "./types";

export { authQueryKeys } from "./session-cache";

export function useCurrentSessionQuery() {
  const queryClient = useQueryClient();
  return useQuery({
    queryFn: async ({ signal }) => {
      const session = await getCurrentSession(signal);
      const previous = queryClient.getQueryData<AuthSession | null>(authQueryKeys.session());
      // The cookie may have changed in another browser tab.
      if (previous?.userId !== session?.userId) removeCachedUserData(queryClient);
      return session;
    },
    queryKey: authQueryKeys.session(),
    retry: false,
    staleTime: 30_000,
    refetchOnWindowFocus: "always",
  });
}

export function useCreateGuestSessionMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationKey: [...authQueryKeys.all, "guest"],
    mutationFn: createGuestSession,
    onSuccess: (session) => replaceCachedSession(queryClient, session),
  });
}

export function useLoginMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: [...authQueryKeys.all, "login"],
    mutationFn: login,
    retry: false,
    gcTime: 0,
    onSuccess: (session) => replaceCachedSession(queryClient, session),
  });
}

export function useRegisterMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: [...authQueryKeys.all, "register"],
    mutationFn: register,
    retry: false,
    gcTime: 0,
    onSuccess: (session) => replaceCachedSession(queryClient, session),
  });
}

export function useLogoutMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationKey: [...authQueryKeys.all, "logout"],
    mutationFn: logout,
    onSuccess: async () => {
      await queryClient.cancelQueries();
      // A new document also discards credential mutations and cached workspace state.
      window.location.replace("/");
    },
  });
}
