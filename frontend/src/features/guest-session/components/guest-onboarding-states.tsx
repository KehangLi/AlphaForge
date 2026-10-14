import type { ReactNode } from "react";

import { Button } from "@/components/ui/button";

type GuestAccountEmptyStateProps = {
  onAddAccount: () => void;
};

export function GuestAccountEmptyState({
  onAddAccount,
}: GuestAccountEmptyStateProps) {
  return (
    <section className="flex min-h-[420px] items-center justify-center rounded-lg border border-dashed border-[#b9c9be] bg-white px-6 py-12 text-center shadow-sm">
      <div className="max-w-md">
        <p className="text-sm font-semibold uppercase tracking-[0.14em] text-[#3e6f57]">
          First step
        </p>
        <h2 className="mt-3 text-2xl font-semibold text-[#18221d]">
          Add your first brokerage account
        </h2>
        <p className="mt-3 text-sm leading-6 text-[#65746a]">
          Create an account workspace first. Then you can download the sample
          CSV and import your activity data.
        </p>
        <Button className="mt-6" onClick={onAddAccount}>
          Add brokerage account
        </Button>
      </div>
    </section>
  );
}

type GuestActivityEmptyStateProps = {
  importAction: ReactNode;
};

export function GuestActivityEmptyState({
  importAction,
}: GuestActivityEmptyStateProps) {
  return (
    <section className="flex min-h-[420px] items-center justify-center rounded-lg border border-dashed border-[#b9c9be] bg-white px-6 py-12 text-center shadow-sm">
      <div className="max-w-lg">
        <p className="text-sm font-semibold uppercase tracking-[0.14em] text-[#3e6f57]">
          Ready for your first import?
        </p>
        <h2 className="mt-3 text-2xl font-semibold text-[#18221d]">
          Start with the sample activity CSV
        </h2>
        <p className="mt-3 text-sm leading-6 text-[#65746a]">
          Download the sample file to see the expected format, then upload it
          to populate this account with activity rows.
        </p>

        <div className="mt-6 flex flex-col justify-center gap-3 sm:flex-row">
          <a
            className="inline-flex min-h-10 items-center justify-center rounded-md border border-[#bac8bf] px-4 py-2 text-sm font-semibold text-[#173f35] transition hover:bg-[#eef3ef]"
            download
            href="/templates/account-activity-sample.csv"
          >
            Download sample CSV
          </a>
          {importAction}
        </div>

        <p className="mt-5 text-xs leading-5 text-[#65746a]">
          You can upload the sample as-is or edit its rows before importing.
        </p>
      </div>
    </section>
  );
}
