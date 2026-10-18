"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";
import { useCreateGuestSessionMutation } from "@/features/auth/queries";

export function GuestEntryPage() {
  const router = useRouter();
  const guestSessionMutation = useCreateGuestSessionMutation();

  function handleTryAsGuest() {
    guestSessionMutation.mutate(undefined, {
      onSuccess: () => router.push("/app"),
    });
  }

  return (
    <main className="min-h-screen bg-[#eef3ef] text-[#18221d]">
      <div className="mx-auto flex min-h-screen w-full max-w-6xl flex-col px-6 py-6 lg:px-10">
        <header className="flex flex-wrap items-center justify-between gap-4 border-b border-[#cfd9d2] pb-5">
          <Link
            className="text-sm font-semibold uppercase tracking-[0.18em] text-[#3e6f57]"
            href="/"
          >
            AlphaForge
          </Link>
          <nav aria-label="Account" className="flex items-center gap-4 text-sm font-semibold">
            <Link className="rounded-sm text-[#526258] hover:text-[#173f35] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[#3e6f57]" href="/login">Sign in</Link>
            <Link className="inline-flex min-h-10 items-center justify-center rounded-md border border-[#bac8bf] px-3 text-[#173f35] transition hover:bg-white focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[#3e6f57]" href="/register">Create account</Link>
          </nav>
        </header>

        <section className="grid flex-1 items-center gap-12 py-12 lg:grid-cols-[minmax(0,1fr)_420px] lg:gap-24">
          <div className="max-w-2xl">
            <p className="text-sm font-semibold uppercase tracking-[0.18em] text-[#3e6f57]">
              Start with your activity data
            </p>
            <h1 className="mt-5 max-w-xl text-5xl font-semibold leading-[1.05] tracking-normal text-[#101713] md:text-7xl">
              Make every brokerage activity easier to understand.
            </h1>
            <p className="mt-6 max-w-xl text-lg leading-8 text-[#526258]">
              Import your account activity, review the raw transactions, and
              build a clearer picture of your investing history.
            </p>
          </div>

          <div className="rounded-xl border border-[#cfd9d2] bg-white p-6 shadow-sm">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-sm font-semibold uppercase tracking-[0.12em] text-[#3e6f57]">
                  Quick start
                </p>
                <h2 className="mt-3 text-2xl font-semibold text-[#18221d]">
                  Explore as a guest
                </h2>
              </div>
              <span className="rounded-full bg-[#edf5ef] px-3 py-1 text-xs font-semibold text-[#2d654b]">
                No account needed
              </span>
            </div>

            <p className="mt-4 text-sm leading-6 text-[#65746a]">
              Enter the workspace with a temporary guest session. You can
              create an account and import a CSV when you are ready.
            </p>

            <Button
              className="mt-6 w-full justify-center"
              disabled={guestSessionMutation.isPending}
              onClick={handleTryAsGuest}
            >
              {guestSessionMutation.isPending
                ? "Starting session..."
                : "Try as guest"}
            </Button>
            {guestSessionMutation.isError ? (
              <p className="mt-3 text-sm font-medium text-[#9a3412]">
                {guestSessionMutation.error instanceof Error
                  ? guestSessionMutation.error.message
                  : "Unable to start a guest session."}
              </p>
            ) : null}

            <div className="mt-4 flex gap-3 rounded-md border border-[#d8e1db] bg-[#f8faf8] p-4">
              <span
                aria-hidden="true"
                className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-[#dce9df] text-xs font-bold text-[#2d654b]"
              >
                i
              </span>
              <p className="text-sm leading-5 text-[#65746a]">
                Guest data is temporary and tied to this browser session.
              </p>
            </div>
          </div>
        </section>

        <footer className="border-t border-[#cfd9d2] pt-5 text-sm text-[#65746a]">
          Your data stays in your workspace while you explore.
        </footer>
      </div>
    </main>
  );
}
