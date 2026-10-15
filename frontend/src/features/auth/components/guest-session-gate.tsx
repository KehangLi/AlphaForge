"use client";

import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api/client";

import { useCurrentSessionQuery } from "../queries";

type GuestSessionGateProps = {
  children: React.ReactNode;
};

export function GuestSessionGate({ children }: GuestSessionGateProps) {
  const sessionQuery = useCurrentSessionQuery();

  if (sessionQuery.isLoading) {
    return <SessionLoadingState />;
  }

  if (sessionQuery.isError) {
    if (sessionQuery.error instanceof ApiError && sessionQuery.error.status === 401) {
      return <SessionLoadingState />;
    }

    return (
      <main className="flex min-h-screen items-center justify-center bg-[#eef3ef] px-6 text-[#18221d]">
        <div className="max-w-md rounded-xl border border-[#e7c5b8] bg-white p-6 text-center shadow-sm">
          <h1 className="text-xl font-semibold">Unable to restore session</h1>
          <p className="mt-3 text-sm leading-6 text-[#65746a]">
            {sessionQuery.error instanceof Error
              ? sessionQuery.error.message
              : "The workspace session could not be loaded."}
          </p>
          <Button className="mt-5" onClick={() => void sessionQuery.refetch()}>
            Try again
          </Button>
        </div>
      </main>
    );
  }

  return <>{children}</>;
}

function SessionLoadingState() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-[#eef3ef] px-6 text-[#65746a]">
      <p className="text-sm font-medium">Restoring workspace session...</p>
    </main>
  );
}

// when session valid, then show the dashboard
