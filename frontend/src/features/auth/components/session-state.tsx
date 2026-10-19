"use client";

import { Button } from "@/components/ui/button";

export function SessionLoadingState() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-[#eef3ef] px-6 text-[#65746a]">
      <p className="text-sm font-medium" role="status">Restoring workspace session...</p>
    </main>
  );
}

export function SessionErrorState({ onRetry }: { onRetry: () => void }) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-[#eef3ef] px-6 text-[#18221d]">
      <div className="max-w-md text-center">
        <h1 className="text-xl font-semibold">Unable to restore session</h1>
        <p className="mt-3 text-sm leading-6 text-[#65746a]" role="alert">
          We could not reach your workspace. Please try again.
        </p>
        <Button className="mt-5" onClick={onRetry}>Try again</Button>
      </div>
    </main>
  );
}
