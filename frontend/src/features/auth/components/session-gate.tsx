"use client";

import { useRouter } from "next/navigation";
import { Fragment, useEffect, type ReactNode } from "react";
import { useIsMutating } from "@tanstack/react-query";

import { authQueryKeys, useCurrentSessionQuery } from "../queries";
import { SessionErrorState, SessionLoadingState } from "./session-state";

export function SessionGate({ children }: { children: ReactNode }) {
  const router = useRouter();
  const sessionQuery = useCurrentSessionQuery();
  const isChangingSession = useIsMutating({ mutationKey: authQueryKeys.all }) > 0; // if currently doing the auth mutation

  useEffect(() => {
    if (sessionQuery.isSuccess && !sessionQuery.data && !isChangingSession) router.replace("/login");
  }, [isChangingSession, router, sessionQuery.data, sessionQuery.isSuccess]);

  if (sessionQuery.isError) {
    return <SessionErrorState onRetry={() => void sessionQuery.refetch()} />;
  }
  if (!sessionQuery.data) return <SessionLoadingState />;

  return <Fragment key={sessionQuery.data.userId}>{children}</Fragment>;
}
