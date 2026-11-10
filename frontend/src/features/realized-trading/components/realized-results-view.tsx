"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";

import { useAccountRealizedTradingSummaryQuery } from "../queries";
import { RealizedContributionsTable } from "./realized-contributions-table";
import { RealizedSummary } from "./realized-summary";

const PAGE_SIZE = 10;

type RealizedResultsViewProps = {
  brokerageAccountId: string;
  baseCurrency?: string;
};

export function RealizedResultsView({
  brokerageAccountId,
  baseCurrency,
}: RealizedResultsViewProps) {
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [currency, setCurrency] = useState("");
  const [page, setPage] = useState(0);
  const [appliedFilters, setAppliedFilters] = useState({
    from: "",
    to: "",
    currency: "",
  });
  const query = useAccountRealizedTradingSummaryQuery({
    brokerageAccountId,
    from: appliedFilters.from,
    to: appliedFilters.to,
    currency: appliedFilters.currency,
    page,
    size: PAGE_SIZE,
  });
  const data = query.data;
  const currencyOptions = getCurrencyOptions(
    baseCurrency,
    data?.realizedSummary.currencies.map((item) => item.currency) ?? [],
  );

  function handleApplyFilters() {
    if (from && to && from > to) return;

    setPage(0);
    setAppliedFilters({ from, to, currency });
  }

  function handleClearFilters() {
    setFrom("");
    setTo("");
    setCurrency("");
    setPage(0);
    setAppliedFilters({ from: "", to: "", currency: "" });
  }

  const hasInvalidDateRange = Boolean(from && to && from > to);

  return (
    <div className="space-y-6">
      <section className="rounded-lg border border-[#cfd9d2] bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
          <div>
            <h2 className="text-lg font-semibold">Realized results</h2>
            <p className="mt-1 max-w-2xl text-sm leading-6 text-[#65746a]">
              Broker-reported results from SELL rows. Fees and taxes are not recalculated here.
            </p>
          </div>
          {data?.realizedSummary ? (
            <p className="text-sm text-[#65746a]">
              {data.realizedSummary.includedSellRecordCount} of {data.realizedSummary.sellRecordCount} sells included
            </p>
          ) : null}
        </div>

        <div className="mt-5 grid gap-3 md:grid-cols-[1fr_1fr_180px_auto_auto] md:items-end">
          <FilterField label="From" value={from} onChange={setFrom} />
          <FilterField label="To" value={to} onChange={setTo} />
          <label className="flex flex-col gap-1.5 text-sm font-semibold text-[#4e5d53]" htmlFor="realized-currency">
            Currency
            <select
              className="min-h-10 rounded-md border border-[#b9c9be] bg-white px-3 text-sm font-medium text-[#18221d] outline-none focus:border-[#3e6f57] focus:ring-2 focus:ring-[#b9c9be]"
              id="realized-currency"
              onChange={(event) => setCurrency(event.target.value)}
              value={currency}
            >
              <option value="">All currencies</option>
              {currencyOptions.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>
          <Button
            className="w-full justify-center md:w-fit"
            disabled={hasInvalidDateRange || query.isFetching}
            onClick={handleApplyFilters}
            variant="primary"
          >
            Apply
          </Button>
          <Button
            className="w-full justify-center md:w-fit"
            disabled={query.isFetching && !query.data}
            onClick={handleClearFilters}
            variant="secondary"
          >
            Clear
          </Button>
        </div>
        {hasInvalidDateRange ? (
          <p className="mt-3 text-sm font-medium text-[#9a3412]">
            From date must be before or equal to the To date.
          </p>
        ) : null}
        {appliedFilters.currency ? (
          <p className="mt-3 text-xs text-[#65746a]">
            Currency filtering applies to the instrument contributions. The account summary remains grouped across currencies.
          </p>
        ) : null}
      </section>

      {data ? (
        <section className="space-y-4">
          <RealizedSummary summary={data.realizedSummary} />
          {data.unidentifiedSellRecordCount > 0 ? (
            <p className="rounded-md border border-[#ead6ae] bg-[#fff9eb] px-4 py-3 text-sm text-[#8a4b05]">
              {data.unidentifiedSellRecordCount} sell record(s) could not be assigned to an instrument.
            </p>
          ) : null}
          {data.realizedSummary.excludedSellRecordCount > 0 ? (
            <p className="rounded-md border border-[#ead6ae] bg-[#fff9eb] px-4 py-3 text-sm text-[#8a4b05]">
              {data.realizedSummary.missingResultCount} sell record(s) are missing Result and {data.realizedSummary.missingResultCurrencyCount} are missing Result currency.
            </p>
          ) : null}
        </section>
      ) : null}

      <RealizedContributionsTable
        brokerageAccountId={brokerageAccountId}
        contributions={data?.contributions ?? []}
        error={query.error}
        isError={query.isError}
        isFetching={query.isFetching}
        isLoading={query.isLoading}
        isPageFirst={data?.first ?? true}
        isPageLast={data?.last ?? true}
        onNextPage={() => setPage((currentPage) => currentPage + 1)}
        onPreviousPage={() => setPage((currentPage) => Math.max(currentPage - 1, 0))}
        onRefresh={() => void query.refetch()}
        page={data?.page}
        totalElements={data?.totalElements}
        totalPages={data?.totalPages}
      />
    </div>
  );
}

function FilterField({
  label,
  onChange,
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  value: string;
}) {
  return (
    <label className="flex flex-col gap-1.5 text-sm font-semibold text-[#4e5d53]">
      {label}
      <input
        className="min-h-10 rounded-md border border-[#b9c9be] bg-white px-3 text-sm font-medium text-[#18221d] outline-none focus:border-[#3e6f57] focus:ring-2 focus:ring-[#b9c9be]"
        onChange={(event) => onChange(event.target.value)}
        type="date"
        value={value}
      />
    </label>
  );
}

function getCurrencyOptions(baseCurrency: string | undefined, responseCurrencies: string[]) {
  return Array.from(
    new Set(
      [baseCurrency, ...responseCurrencies]
        .filter((currency): currency is string => Boolean(currency))
        .map((currency) => currency.toUpperCase()),
    ),
  ).sort();
}

