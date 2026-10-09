import { Button } from "@/components/ui/button";

import type { BrokerageAccountListResponse } from "../types";

type AccountListViewProps = {
  accounts: BrokerageAccountListResponse[];
  onAdd: () => void;
  onDelete: (account: BrokerageAccountListResponse) => void;
};

export function AccountListView({
  accounts,
  onAdd,
  onDelete,
}: AccountListViewProps) {
  return (
    <div className="space-y-4">
      {accounts.length > 0 ? (
        <div className="divide-y divide-[#e3e9e5] rounded-md border border-[#d8e1db] bg-white">
          {accounts.map((account) => (
            <div
              className="flex items-center justify-between gap-4 p-4"
              key={account.id}
            >
              <div className="min-w-0">
                <p className="truncate font-semibold text-[#18221d]">
                  {account.brokerName}
                </p>
                <p className="mt-1 truncate text-sm text-[#65746a]">
                  {account.accountName} · {account.baseCurrency}
                </p>
              </div>
              <Button
                className="shrink-0 text-[#9a3412] hover:bg-[#fff1eb]"
                onClick={() => onDelete(account)}
                variant="secondary"
              >
                Delete
              </Button>
            </div>
          ))}
        </div>
      ) : (
        <p className="rounded-md border border-dashed border-[#b9c9be] px-4 py-8 text-center text-sm text-[#65746a]">
          No brokerage accounts yet.
        </p>
      )}

      <div className="flex justify-end">
        <Button onClick={onAdd}>Add account</Button>
      </div>
    </div>
  );
}
