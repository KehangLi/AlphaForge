import Link from "next/link";

import { Button } from "@/components/ui/button";

import type { InstrumentRealizedContributionResponse } from "../types";

type RealizedContributionsTableProps = {
  brokerageAccountId: string;
  contributions: InstrumentRealizedContributionResponse[];
  error?: unknown;
  isError?: boolean;
  isFetching?: boolean;
  isLoading?: boolean;
  isPageFirst?: boolean;
  isPageLast?: boolean;
  onNextPage: () => void;
  onPreviousPage: () => void;
  onRefresh: () => void;
  page?: number;
  totalElements?: number;
  totalPages?: number;
};

export function RealizedContributionsTable({
  brokerageAccountId,
  contributions,
  error,
  isError = false,
  isFetching = false,
  isLoading = false,
  isPageFirst = true,
  isPageLast = true,
  onNextPage,
  onPreviousPage,
  onRefresh,
  page,
  totalElements,
  totalPages,
}: RealizedContributionsTableProps) {
  const hasContributions = contributions.length > 0;

  return (
    <section className="overflow-hidden rounded-lg border border-[#cfd9d2] bg-white shadow-sm">
      <div className="flex flex-col gap-3 border-b border-[#d8e1db] p-5 md:flex-row md:items-center md:justify-between">
        <div>
          <h2 className="text-lg font-semibold">By instrument</h2>
          <p className="mt-1 text-sm text-[#65746a]">
            Broker-reported realized results grouped by instrument and currency.
          </p>
        </div>
        <Button
          className="px-3"
          disabled={isFetching}
          onClick={onRefresh}
          variant="secondary"
        >
          {isFetching ? "Refreshing" : "Refresh"}
        </Button>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[900px] border-collapse text-sm">
          <thead className="bg-[#f4f7f5] text-left text-xs uppercase tracking-[0.12em] text-[#65746a]">
            <tr>
              <th className="px-5 py-4 font-semibold">Instrument</th>
              <th className="px-5 py-4 font-semibold">Currency</th>
              <th className="px-5 py-4 text-right font-semibold">Result</th>
              <th className="px-5 py-4 text-right font-semibold">Profit</th>
              <th className="px-5 py-4 text-right font-semibold">Loss</th>
              <th className="px-5 py-4 text-right font-semibold">Sell outcomes</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#e3e9e5]">
            {isLoading && !hasContributions ? (
              <tr>
                <td className="px-5 py-10 text-center text-[#65746a]" colSpan={6}>
                  Loading realized results...
                </td>
              </tr>
            ) : null}
            {isError && !hasContributions ? (
              <tr>
                <td className="px-5 py-10 text-center text-[#9a3412]" colSpan={6}>
                  {formatErrorMessage(error, "Failed to load realized results.")}
                </td>
              </tr>
            ) : null}
            {!isLoading && !isError && !hasContributions ? (
              <tr>
                <td className="px-5 py-10 text-center text-[#65746a]" colSpan={6}>
                  No realized trading results for the selected filters.
                </td>
              </tr>
            ) : null}
            {isError && hasContributions ? (
              <tr>
                <td className="px-5 py-3 text-sm text-[#9a3412]" colSpan={6}>
                  {formatErrorMessage(error, "Refresh failed. Showing the last loaded results.")}
                </td>
              </tr>
            ) : null}
            {contributions.map((contribution) => (
              <ContributionRow
                brokerageAccountId={brokerageAccountId}
                contribution={contribution}
                key={contributionKey(contribution)}
              />
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex flex-col gap-3 border-t border-[#d8e1db] px-5 py-4 text-sm text-[#65746a] sm:flex-row sm:items-center sm:justify-between">
        <span>
          {totalElements === undefined
            ? ""
            : `Page ${(page ?? 0) + 1} of ${Math.max(totalPages ?? 1, 1)} (${totalElements} instruments)`}
        </span>
        <div className="flex gap-2">
          <Button
            className="px-3"
            disabled={isPageFirst || isFetching}
            onClick={onPreviousPage}
            variant="secondary"
          >
            Previous
          </Button>
          <Button
            className="px-3"
            disabled={isPageLast || isFetching}
            onClick={onNextPage}
            variant="secondary"
          >
            Next
          </Button>
        </div>
      </div>
    </section>
  );
}

function ContributionRow({
  brokerageAccountId,
  contribution,
}: {
  brokerageAccountId: string;
  contribution: InstrumentRealizedContributionResponse;
}) {
  const currencySummary = contribution.realizedSummary.currencies[0];
  const instrumentLabel =
    contribution.instrumentName ?? contribution.ticker ?? contribution.isin ?? "Unknown instrument";
  const instrumentKey = contribution.instrumentKey;
  const detailHref = instrumentKey
    ? `/app/accounts/${encodeURIComponent(brokerageAccountId)}/instruments/${encodeURIComponent(instrumentKey)}`
    : undefined;

  return (
    <tr className="hover:bg-[#f8faf8]">
      <td className="px-5 py-4">
        {detailHref ? (
          <Link
            className="font-medium text-[#173f35] underline decoration-[#b9c9be] underline-offset-4 hover:decoration-[#173f35]"
            href={detailHref}
          >
            {instrumentLabel}
          </Link>
        ) : (
          <p className="font-medium">{instrumentLabel}</p>
        )}
        <p className="mt-1 text-xs text-[#65746a]">
          {contribution.ticker ?? contribution.isin ?? "Instrument unavailable"}
        </p>
      </td>
      <td className="px-5 py-4 font-medium text-[#4e5d53]">
        {contribution.currency ?? "-"}
      </td>
      <td className={resultClassName(currencySummary?.totalResult)}>
        {currencySummary ? formatAmount(currencySummary.totalResult, currencySummary.currency) : "-"}
      </td>
      <td className="px-5 py-4 text-right text-[#2d654b]">
        {currencySummary
          ? formatAmount(currencySummary.totalProfit, currencySummary.currency)
          : "-"}
      </td>
      <td className="px-5 py-4 text-right text-[#9a3412]">
        {currencySummary
          ? formatAmount(currencySummary.totalLoss, currencySummary.currency)
          : "-"}
      </td>
      <td className="px-5 py-4 text-right text-[#65746a]">
        {currencySummary
          ? `${currencySummary.profitableSellCount} profit / ${currencySummary.losingSellCount} loss / ${currencySummary.breakevenSellCount} even`
          : "-"}
      </td>
    </tr>
  );
}

function contributionKey(contribution: InstrumentRealizedContributionResponse) {
  return `${contribution.instrumentKey ?? "unknown"}-${contribution.currency ?? "unknown"}`;
}

function formatErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

function formatAmount(amount: number, currency: string) {
  return `${new Intl.NumberFormat("en-US", {
    maximumFractionDigits: 4,
  }).format(amount)} ${currency}`;
}

function resultClassName(amount: number | undefined) {
  return `px-5 py-4 text-right font-semibold ${amount === undefined || amount >= 0 ? "text-[#2d654b]" : "text-[#9a3412]"}`;
}

