"use client";

import Link from "next/link";
import { useState } from "react";

import { SessionMenu } from "@/features/auth/components/session-menu";
import { AccountActivitiesTable } from "@/features/brokerage-accounts/components/account-activities-table";
import { formatShortAccountLabel } from "@/features/brokerage-accounts/formatters";
import { useBrokerageAccountsQuery } from "@/features/brokerage-accounts/queries";
import { useInstrumentDetailQuery } from "../queries";
import type { HoldingResponse } from "@/features/holdings/types";

type InstrumentDetailPageProps = {
  brokerageAccountId: string;
  instrumentKey: string;
};

export function InstrumentDetailPage({
  brokerageAccountId,
  instrumentKey,
}: InstrumentDetailPageProps) {
  const [page, setPage] = useState(0);
  const {
    data: accounts = [],
    isLoading: isAccountsLoading,
  } = useBrokerageAccountsQuery();
  const selectedAccount = accounts.find(
    (account) => account.id === brokerageAccountId,
  );
  const detailQuery = useInstrumentDetailQuery({
    brokerageAccountId,
    instrumentKey,
    page,
  });
  const detail = detailQuery.data;

  return (
    <main className="min-h-screen bg-[#eef3ef] text-[#18221d]">
      <div className="mx-auto flex min-h-screen w-full max-w-7xl flex-col px-6 py-6 lg:px-10">
        <header className="flex flex-wrap items-center justify-between gap-4 border-b border-[#cfd9d2] pb-6">
          <div>
            <Link
              className="text-sm font-semibold text-[#3e6f57] hover:text-[#173f35]"
              href="/app"
            >
              Back to workspace
            </Link>
            <p className="mt-5 text-sm font-semibold uppercase tracking-[0.18em] text-[#3e6f57]">
              Instrument detail
            </p>
            <h1 className="mt-2 text-3xl font-semibold text-[#101713] md:text-5xl">
              {detail?.instrumentName ?? instrumentKey}
            </h1>
          </div>
          <SessionMenu />
        </header>

        <div className="border-b border-[#cfd9d2] py-5 text-sm text-[#65746a]">
          <p>
            {detail?.ticker ?? "Ticker unavailable"} · {detail?.isin ?? instrumentKey}
          </p>
          <p className="mt-1">
            {isAccountsLoading
              ? "Loading account..."
              : selectedAccount
                ? formatShortAccountLabel(selectedAccount)
                : "Account unavailable"}
          </p>
        </div>

        {detailQuery.isLoading ? <LoadingState /> : null}
        {detailQuery.isError ? (
          <ErrorState error={detailQuery.error} onRetry={() => void detailQuery.refetch()} />
        ) : null}

        {detail && !detailQuery.isError ? (
          <>
            <HoldingSummary holding={detail.holding} />
            <section className="py-6">
              <AccountActivitiesTable
                activities={detail.activities}
                brokerageAccountId={brokerageAccountId}
                error={detailQuery.error}
                isError={detailQuery.isError}
                isFetching={detailQuery.isFetching}
                isLoading={detailQuery.isLoading}
                isPageFirst={detail.first}
                isPageLast={detail.last}
                onNextPage={() => setPage((currentPage) => currentPage + 1)}
                onPreviousPage={() =>
                  setPage((currentPage) => Math.max(currentPage - 1, 0))
                }
                onRefresh={() => void detailQuery.refetch()}
                page={detail.page}
                size={detail.size}
                totalElements={detail.totalElements}
                totalPages={detail.totalPages}
              />
            </section>
          </>
        ) : null}
      </div>
    </main>
  );
}

function HoldingSummary({ holding }: { holding: HoldingResponse | null }) {
  const items = [
    ["Quantity", holding ? formatNumber(holding.quantity) : "-"],
    ["Bought", holding ? formatNumber(holding.totalBoughtQuantity) : "-"],
    ["Sold", holding ? formatNumber(holding.totalSoldQuantity) : "-"],
    ["Status", holding?.status.toLowerCase() ?? "-"],
    ["Last traded", holding ? formatDate(holding.lastTradedAt) : "-"],
  ];

  return (
    <section className="grid gap-3 border-b border-[#cfd9d2] py-6 sm:grid-cols-2 lg:grid-cols-5">
      {items.map(([label, value]) => (
        <div className="rounded-md border border-[#d8e1db] bg-white px-4 py-3" key={label}>
          <p className="text-xs font-semibold uppercase tracking-[0.12em] text-[#65746a]">
            {label}
          </p>
          <p className="mt-2 font-semibold text-[#18221d]">{value}</p>
        </div>
      ))}
    </section>
  );
}

function LoadingState() {
  return <p className="py-12 text-center text-sm text-[#65746a]">Loading instrument detail...</p>;
}

function ErrorState({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <div className="py-12 text-center">
      <p className="text-sm text-[#9a3412]">
        {error instanceof Error ? error.message : "Failed to load instrument detail."}
      </p>
      <button
        className="mt-4 rounded-md border border-[#bac8bf] px-4 py-2 text-sm font-semibold text-[#173f35] hover:bg-white"
        onClick={onRetry}
        type="button"
      >
        Try again
      </button>
    </div>
  );
}

function formatNumber(value: number) {
  return new Intl.NumberFormat("en-US", { maximumFractionDigits: 8 }).format(value);
}

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "UTC",
  }).format(date);
}
