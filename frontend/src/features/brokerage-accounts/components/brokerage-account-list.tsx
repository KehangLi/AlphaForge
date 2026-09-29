import type { BrokerageAccountSummary } from "../types";

type BrokerageAccountListProps = {
  accounts: BrokerageAccountSummary[];
};

export function BrokerageAccountList({ accounts }: BrokerageAccountListProps) {
  return (
    <aside className="rounded-lg border border-[#cfd9d2] bg-white p-4 shadow-sm">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-lg font-semibold">Brokerage accounts</h2>
        <span className="rounded-full bg-[#dce9df] px-3 py-1 text-xs font-semibold text-[#255640]">
          Live soon
        </span>
      </div>

      <div className="space-y-3">
        {accounts.map((account) => (
          <button
            className="w-full rounded-md border border-[#d8e1db] bg-[#f8faf8] p-4 text-left transition hover:border-[#8db49d] hover:bg-white"
            key={`${account.broker}-${account.name}`}
            type="button"
          >
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="font-semibold">{account.broker}</p>
                <p className="mt-1 text-sm text-[#65746a]">{account.name}</p>
              </div>
              <span className="text-sm font-semibold text-[#3e6f57]">
                {account.currency}
              </span>
            </div>
            <p className="mt-3 text-xs font-medium uppercase tracking-[0.14em] text-[#7b6a3e]">
              {account.status}
            </p>
          </button>
        ))}
      </div>
    </aside>
  );
}
