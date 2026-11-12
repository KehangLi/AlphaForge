import { Button } from "@/components/ui/button";
import Link from "next/link";
import { ChevronLeft, ChevronRight, RefreshCw } from "lucide-react";

import type { HoldingFilter, HoldingResponse } from "../types";

const STATUS_FILTERS: { value: HoldingFilter; label: string }[] = [
  { value: "OPEN", label: "Open" },
  { value: "CLOSED", label: "Closed" },
  { value: "ALL", label: "All" },
];

type HoldingsTableProps = {
  holdings: HoldingResponse[];
  brokerageAccountId?: string;
  error?: unknown;
  isError?: boolean;
  isFetching?: boolean;
  isLoading?: boolean;
  isPageFirst: boolean;
  isPageLast: boolean;
  onNextPage: () => void;
  onPreviousPage: () => void;
  onRefresh?: () => void;
  onStatusChange: (status: HoldingFilter) => void;
  page: number;
  size: number;
  status: HoldingFilter;
  totalElements?: number;
  totalPages?: number;
};

export function HoldingsTable({
  holdings,
  brokerageAccountId,
  error,
  isError = false,
  isFetching = false,
  isLoading = false,
  isPageFirst,
  isPageLast,
  onNextPage,
  onPreviousPage,
  onRefresh,
  onStatusChange,
  page,
  size,
  status,
  totalElements,
  totalPages,
}: HoldingsTableProps) {
  const hasHoldings = holdings.length > 0;
  const rangeStart = hasHoldings ? page * size + 1 : 0;
  const rangeEnd = hasHoldings ? page * size + holdings.length : 0;

  return (
    <section className="overflow-hidden rounded-lg border border-[#cfd9d2] bg-white shadow-sm">
      <div className="space-y-4 border-b border-[#d8e1db] p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className="text-lg font-semibold">Holdings</h2>
            <p aria-live="polite" className="mt-1 text-sm text-[#65746a]">
              {totalElements === undefined
                ? "Positions for the selected brokerage account."
                : `Showing ${rangeStart}-${rangeEnd} of ${totalElements} positions.`}
            </p>
          </div>
          <Button
            className="flex shrink-0 items-center gap-2 px-3"
            disabled={isFetching || !onRefresh}
            onClick={onRefresh}
            variant="secondary"
          >
            <RefreshCw aria-hidden="true" className={isFetching ? "animate-spin" : undefined} size={16} />
            {isFetching ? "Refreshing" : "Refresh"}
          </Button>
        </div>
        <div aria-label="Holding status" className="flex w-fit max-w-full rounded-md border border-[#bac8bf] p-1" role="group">
          {STATUS_FILTERS.map((filter) => (
            <button
              aria-pressed={status === filter.value}
              className={[
                "min-h-10 rounded px-4 text-sm font-semibold transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#3e6f57]",
                status === filter.value
                  ? "bg-[#173f35] text-white"
                  : "text-[#65746a] hover:bg-[#eef3ef] hover:text-[#173f35]",
              ].join(" ")}
              key={filter.value}
              onClick={() => onStatusChange(filter.value)}
              type="button"
            >
              {filter.label}
            </button>
          ))}
        </div>
        {isError && hasHoldings ? (
          <p className="text-sm text-[#9a3412]" role="alert">
            {formatErrorMessage(error, "Refresh failed. Showing the last loaded holdings.")}
          </p>
        ) : null}
      </div>

      <div aria-busy={isFetching} className="h-[560px] max-h-[65vh] min-h-[280px] overflow-auto">
        <table className="w-full min-w-[820px] border-collapse text-sm">
          <thead className="sticky top-0 z-10 bg-[#f4f7f5] text-left text-xs uppercase tracking-[0.12em] text-[#65746a]">
            <tr>
              <th className="px-5 py-4 font-semibold">Instrument</th>
              <th className="px-5 py-4 font-semibold">Ticker</th>
              <th className="px-5 py-4 text-right font-semibold">Shares</th>
              <th className="px-5 py-4 text-right font-semibold">Bought</th>
              <th className="px-5 py-4 text-right font-semibold">Sold</th>
              <th className="px-5 py-4 font-semibold">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#e3e9e5]">
            {isLoading && !hasHoldings ? (
              <tr>
                <td className="px-5 py-10 text-center text-[#65746a]" colSpan={6}>
                  Loading holdings...
                </td>
              </tr>
            ) : null}
            {isError && !hasHoldings ? (
              <tr>
                <td className="px-5 py-10 text-center text-[#9a3412]" colSpan={6}>
                  {formatErrorMessage(error, "Failed to load holdings.")}
                </td>
              </tr>
            ) : null}
            {!isLoading && !isError && !hasHoldings ? (
              <tr>
                <td className="px-5 py-10 text-center text-[#65746a]" colSpan={6}>
                  {page > 0
                    ? "No holdings on this page."
                    : status === "CLOSED"
                      ? "No closed positions for this account."
                      : status === "OPEN"
                        ? "No open positions for this account."
                        : "No positions for this account."}
                </td>
              </tr>
            ) : null}
            {holdings.map((holding) => (
              <tr className="hover:bg-[#f8faf8]" key={holdingKey(holding)}>
                <td className="px-5 py-4">
                  {brokerageAccountId ? (
                    <Link
                      className="font-medium text-[#173f35] underline decoration-[#b9c9be] underline-offset-4 hover:decoration-[#173f35]"
                      href={`/app/accounts/${encodeURIComponent(brokerageAccountId)}/instruments/${encodeURIComponent(holding.instrumentKey)}`}
                    >
                      {holding.instrumentName ?? "Unknown instrument"}
                    </Link>
                  ) : (
                    <p className="font-medium">
                      {holding.instrumentName ?? "Unknown instrument"}
                    </p>
                  )}
                  <p className="mt-1 text-xs text-[#65746a]">
                    {holding.isin ?? "ISIN unavailable"}
                  </p>
                </td>
                <td className="px-5 py-4 font-medium text-[#4e5d53]">
                  {brokerageAccountId ? (
                    <Link
                      className="text-[#173f35] hover:underline"
                      href={`/app/accounts/${encodeURIComponent(brokerageAccountId)}/instruments/${encodeURIComponent(holding.instrumentKey)}`}
                    >
                      {holding.ticker ?? "-"}
                    </Link>
                  ) : holding.ticker ?? "-"}
                </td>
                <td className="px-5 py-4 text-right text-[#4e5d53]">
                  {formatNumber(holding.quantity)}
                </td>
                <td className="px-5 py-4 text-right text-[#4e5d53]">
                  {formatNumber(holding.totalBoughtQuantity)}
                </td>
                <td className="px-5 py-4 text-right text-[#4e5d53]">
                  {formatNumber(holding.totalSoldQuantity)}
                </td>
                <td className="px-5 py-4">
                  <span className={statusClassName(holding.status)}>
                    {holding.status.toLowerCase()}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="flex flex-col gap-3 border-t border-[#d8e1db] p-4 sm:flex-row sm:items-center sm:justify-between">
        <p aria-live="polite" className="text-sm font-medium text-[#65746a]">
          {totalPages === undefined ? "Page loading..." : `Page ${page + 1} of ${Math.max(totalPages, 1)}`}
        </p>
        <div className="flex gap-2">
          <Button
            className="flex min-h-10 items-center gap-1.5 px-3"
            disabled={isLoading || isFetching || isPageFirst}
            onClick={onPreviousPage}
            variant="secondary"
          >
            <ChevronLeft aria-hidden="true" size={16} />
            Previous
          </Button>
          <Button
            className="flex min-h-10 items-center gap-1.5 px-3"
            disabled={isLoading || isFetching || isPageLast}
            onClick={onNextPage}
            variant="secondary"
          >
            Next
            <ChevronRight aria-hidden="true" size={16} />
          </Button>
        </div>
      </div>
    </section>
  );
}

function holdingKey(holding: HoldingResponse) {
  return holding.instrumentKey;
}

function formatErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

function formatNumber(value: number | null) {
  if (value === null) return "-";

  return new Intl.NumberFormat("en-US", {
    maximumFractionDigits: 8,
  }).format(value);
}

function statusClassName(status: HoldingResponse["status"]) {
  return status === "OPEN"
    ? "rounded-full bg-[#edf5ef] px-2.5 py-1 text-xs font-semibold uppercase text-[#2d654b]"
    : "rounded-full bg-[#eef3ef] px-2.5 py-1 text-xs font-semibold uppercase text-[#65746a]";
}
