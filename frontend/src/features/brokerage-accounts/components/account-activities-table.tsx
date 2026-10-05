import { Button } from "@/components/ui/button";

import type { AccountActivityResponse } from "../types";

type AccountActivitiesTableProps = {
  activities: AccountActivityResponse[];
  emptyMessage?: string;
  error?: unknown;
  isError?: boolean;
  isFetching?: boolean;
  isLoading?: boolean;
  isPageFirst?: boolean;
  isPageLast?: boolean;
  onNextPage?: () => void;
  onPreviousPage?: () => void;
  onRefresh?: () => void;
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
};

export function AccountActivitiesTable({
  activities,
  emptyMessage = "No account activity found.",
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
  size,
  totalElements,
  totalPages,
}: AccountActivitiesTableProps) {
  const hasActivities = activities.length > 0;

  return (
    <section className="overflow-hidden rounded-lg border border-[#cfd9d2] bg-white shadow-sm">
      <div className="flex flex-col gap-3 border-b border-[#d8e1db] p-5 md:flex-row md:items-center md:justify-between">
        <div>
          <h2 className="text-lg font-semibold">Account activities</h2>
          <p className="mt-1 text-sm text-[#65746a]">
            {formatActivitySummary({
              currentCount: activities.length,
              page,
              size,
              totalElements,
            })}
          </p>
          {isError && hasActivities ? (
            <p className="mt-1 text-sm font-medium text-[#9a3412]">
              {formatErrorMessage(error, "Refresh failed. Showing the last loaded rows.")}
            </p>
          ) : null}
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
        <table className="w-full min-w-[860px] border-collapse text-sm">
          <thead className="bg-[#f4f7f5] text-left text-xs uppercase tracking-[0.12em] text-[#65746a]">
            <tr>
              <th className="px-5 py-4 font-semibold">Date</th>
              <th className="px-5 py-4 font-semibold">Action</th>
              <th className="px-5 py-4 font-semibold">Instrument</th>
              <th className="px-5 py-4 font-semibold">Shares</th>
              <th className="px-5 py-4 text-right font-semibold">Price</th>
              <th className="px-5 py-4 text-right font-semibold">Total</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#e3e9e5]">
            {isLoading && !hasActivities ? (
              <tr>
                <td
                  className="px-5 py-10 text-center text-sm font-medium text-[#65746a]"
                  colSpan={6}
                >
                  Loading account activity...
                </td>
              </tr>
            ) : null}
            {isError && !hasActivities ? (
              <tr>
                <td
                  className="px-5 py-10 text-center text-sm font-medium text-[#9a3412]"
                  colSpan={6}
                >
                  {formatErrorMessage(error, "Failed to load account activity.")}
                </td>
              </tr>
            ) : null}
            {!isLoading && !isError && !hasActivities ? (
              <tr>
                <td
                  className="px-5 py-10 text-center text-sm font-medium text-[#65746a]"
                  colSpan={6}
                >
                  {emptyMessage}
                </td>
              </tr>
            ) : null}
            {hasActivities ? activities.map((activity) => (
              <tr
                className="hover:bg-[#f8faf8]"
                key={activity.id}
              >
                <td className="px-5 py-4 text-[#4e5d53]">
                  {formatDateTime(activity.occurredAt)}
                </td>
                <td className="px-5 py-4">
                  <span className="rounded-full bg-[#edf5ef] px-2.5 py-1 text-xs font-semibold text-[#2d654b]">
                    {activity.action}
                  </span>
                </td>
                <td className="px-5 py-4">
                  <p className="font-medium">{formatInstrumentName(activity)}</p>
                  <p className="mt-1 text-xs text-[#65746a]">
                    {formatInstrumentCode(activity)}
                  </p>
                </td>
                <td className="px-5 py-4 text-[#4e5d53]">
                  {formatNumber(activity.numberOfShares)}
                </td>
                <td className="px-5 py-4 text-right text-[#4e5d53]">
                  {formatMoney(
                    activity.pricePerShare,
                    activity.pricePerShareCurrency,
                  )}
                </td>
                <td className="px-5 py-4 text-right font-semibold">
                  {formatMoney(
                    activity.totalAmount ?? activity.resultAmount,
                    activity.totalCurrency ?? activity.resultCurrency,
                  )}
                </td>
              </tr>
            )) : null}
          </tbody>
        </table>
      </div>
      <div className="flex flex-col gap-3 border-t border-[#d8e1db] p-4 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm font-medium text-[#65746a]">
          {formatPageLabel(page, totalPages)}
        </p>
        <div className="flex gap-2">
          <Button
            disabled={isLoading || isFetching || isPageFirst || !onPreviousPage}
            onClick={onPreviousPage}
            variant="secondary"
          >
            Previous
          </Button>
          <Button
            disabled={isLoading || isFetching || isPageLast || !onNextPage}
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

function formatActivitySummary({
  currentCount,
  page,
  size,
  totalElements,
}: {
  currentCount: number;
  page?: number;
  size?: number;
  totalElements?: number;
}) {
  if (totalElements === undefined || page === undefined || size === undefined) {
    return "Recent rows from the selected brokerage account.";
  }

  const start = totalElements === 0 ? 0 : page * size + 1;
  const end = page * size + currentCount;

  return `Showing ${start}-${end} of ${totalElements} rows.`;
}

function formatDateTime(value: string) {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeZone: "UTC",
    timeStyle: "short",
  }).format(date);
}

function formatPageLabel(page?: number, totalPages?: number) {
  if (page === undefined || totalPages === undefined) {
    return "Page loading...";
  }

  return `Page ${page + 1} of ${Math.max(totalPages, 1)}`;
}

function formatErrorMessage(error: unknown, fallbackMessage: string) {
  return error instanceof Error ? error.message : fallbackMessage;
}

function formatInstrumentName(activity: AccountActivityResponse) {
  return activity.instrumentName
    ?? activity.merchantName
    ?? activity.notes
    ?? "Unknown instrument";
}

function formatInstrumentCode(activity: AccountActivityResponse) {
  return activity.ticker
    ?? activity.isin
    ?? activity.merchantCategory
    ?? "-";
}

function formatNumber(value: number | null) {
  if (value === null) {
    return "-";
  }

  return new Intl.NumberFormat("en-US", {
    maximumFractionDigits: 8,
  }).format(value);
}

function formatMoney(amount: number | null, currency: string | null) {
  if (amount === null) {
    return "-";
  }

  const formattedAmount = new Intl.NumberFormat("en-US", {
    maximumFractionDigits: 4,
  }).format(amount);

  return currency ? `${formattedAmount} ${currency}` : formattedAmount;
}
