import type { RealizedTradingSummaryResponse } from "../types";

type RealizedSummaryProps = {
  summary: RealizedTradingSummaryResponse;
};

export function RealizedSummary({ summary }: RealizedSummaryProps) {
  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-3">
        <SummaryStat label="Sell records" value={summary.sellRecordCount} />
        <SummaryStat
          label="Included sells"
          value={summary.includedSellRecordCount}
        />
        <SummaryStat
          label="Excluded sells"
          tone={summary.excludedSellRecordCount > 0 ? "warning" : "muted"}
          value={summary.excludedSellRecordCount}
        />
      </div>

      {summary.currencies.length > 0 ? (
        <div className="overflow-x-auto rounded-md border border-[#d8e1db] bg-white">
          <table className="w-full min-w-[700px] border-collapse text-sm">
            <thead className="bg-[#f4f7f5] text-left text-xs uppercase tracking-[0.12em] text-[#65746a]">
              <tr>
                <th className="px-4 py-3 font-semibold">Currency</th>
                <th className="px-4 py-3 text-right font-semibold">Result</th>
                <th className="px-4 py-3 text-right font-semibold">Profit</th>
                <th className="px-4 py-3 text-right font-semibold">Loss</th>
                <th className="px-4 py-3 text-right font-semibold">Outcomes</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#e3e9e5]">
              {summary.currencies.map((currency) => (
                <tr key={currency.currency}>
                  <td className="px-4 py-3 font-semibold text-[#18221d]">
                    {currency.currency}
                  </td>
                  <td className={amountClassName(currency.totalResult)}>
                    {formatAmount(currency.totalResult, currency.currency)}
                  </td>
                  <td className="px-4 py-3 text-right text-[#2d654b]">
                    {formatAmount(currency.totalProfit, currency.currency)}
                  </td>
                  <td className="px-4 py-3 text-right text-[#9a3412]">
                    {formatAmount(currency.totalLoss, currency.currency)}
                  </td>
                  <td className="px-4 py-3 text-right text-[#65746a]">
                    {currency.profitableSellCount} profit / {currency.losingSellCount} loss / {currency.breakevenSellCount} even
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="rounded-md border border-dashed border-[#b9c9be] bg-white px-4 py-4 text-sm text-[#65746a]">
          No sell results with a usable currency are available for this account.
        </p>
      )}
    </div>
  );
}

function SummaryStat({
  label,
  tone = "default",
  value,
}: {
  label: string;
  tone?: "default" | "muted" | "warning";
  value: number;
}) {
  const valueClassName =
    tone === "warning"
      ? "text-[#9a3412]"
      : tone === "muted"
        ? "text-[#65746a]"
        : "text-[#18221d]";

  return (
    <div className="rounded-md border border-[#d8e1db] bg-white px-4 py-3">
      <p className="text-xs font-semibold uppercase tracking-[0.12em] text-[#65746a]">
        {label}
      </p>
      <p className={`mt-2 text-xl font-semibold ${valueClassName}`}>{value}</p>
    </div>
  );
}

function amountClassName(amount: number) {
  return `px-4 py-3 text-right font-semibold ${amount >= 0 ? "text-[#2d654b]" : "text-[#9a3412]"}`;
}

function formatAmount(amount: number, currency: string) {
  return `${new Intl.NumberFormat("en-US", {
    maximumFractionDigits: 4,
  }).format(amount)} ${currency}`;
}

