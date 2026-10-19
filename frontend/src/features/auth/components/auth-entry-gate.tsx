"use client";

import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";

import { useCurrentSessionQuery } from "../queries";
import { SessionErrorState, SessionLoadingState } from "./session-state";

export function AuthEntryGate({ children }: { children: ReactNode }) {
  const router = useRouter();
  const sessionQuery = useCurrentSessionQuery();
  const isRegistered = sessionQuery.data?.userType === "REGISTERED";

  useEffect(() => {
    if (isRegistered) router.replace("/app");
  }, [isRegistered, router]);

  if (sessionQuery.isError) {
    return <SessionErrorState onRetry={() => void sessionQuery.refetch()} />;
  }
  if (sessionQuery.isPending || isRegistered) return <SessionLoadingState />;

  return <>{children}</>;
}