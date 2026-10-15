"use client";

import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { createGuestSession, getCurrentSession, logout } from "./api";

export const authQueryKeys = {
  all: ["auth"] as const,
  session: () => [...authQueryKeys.all, "session"] as const,
};

export function useCurrentSessionQuery() {
  return useQuery({
    queryFn: getCurrentSession,
    queryKey: authQueryKeys.session(),
    retry: false,
    staleTime: 0,
  });
}

export function useCreateGuestSessionMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createGuestSession,
    onSuccess: (session) => {
      queryClient.setQueryData(authQueryKeys.session(), session);
    },
  });
}

export function useLogoutMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.clear();
    },
  });
}
