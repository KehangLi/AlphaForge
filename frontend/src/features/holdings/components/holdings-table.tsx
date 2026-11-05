import { Button } from "@/components/ui/button";
import Link from "next/link";

import type { HoldingResponse } from "../types";

type HoldingsTableProps = {
  holdings: HoldingResponse[];
  brokerageAccountId?: string;
  error?: unknown;
  isError?: boolean;
  isFetching?: boolean;
  isLoading?: boolean;
  onRefresh?: () => void;
};

export function HoldingsTable({
  holdings,
  brokerageAccountId,
  error,
  isError = false,
  isFetching = false,
  isLoading = false,
  onRefresh,
}: HoldingsTableProps) {
  const hasHoldings = holdings.length > 0;

  return (
    <section className="overflow-hidden rounded-lg border border-[#cfd9d2] bg-white shadow-sm">
      <div className="flex flex-col gap-3 border-b border-[#d8e1db] p-5 md:flex-row md:items-center md:justify-between">
        <div>
          <h2 className="text-lg font-semibold">Holdings</h2>
          <p className="mt-1 text-sm text-[#65746a]">
            Current positions for the selected brokerage account.
          </p>
        </div>
        <Button
          className="px-3"
          disabled={isFetching || !onRefresh}
          onClick={onRefresh}
          variant="secondary"
        >
          {isFetching ? "Refreshing" : "Refresh"}
        </Button>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[820px] border-collapse text-sm">
          <thead className="bg-[#f4f7f5] text-left text-xs uppercase tracking-[0.12em] text-[#65746a]">
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
                  No current holdings for this account.
                </td>
              </tr>
            ) : null}
            {isError && hasHoldings ? (
              <tr>
                <td className="px-5 py-3 text-sm text-[#9a3412]" colSpan={6}>
                  {formatErrorMessage(error, "Refresh failed. Showing the last loaded holdings.")}
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
