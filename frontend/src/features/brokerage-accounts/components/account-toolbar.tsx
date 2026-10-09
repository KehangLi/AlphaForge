import { Button } from "@/components/ui/button";

import { formatAccountLabel } from "../formatters";
import type { BrokerageAccountListResponse } from "../types";

type AccountToolbarProps = {
  accounts: BrokerageAccountListResponse[];
  accountSelectPlaceholder: string;
  isAccountsError: boolean;
  isAccountsFetching: boolean;
  isAccountsLoading: boolean;
  accountsError: unknown;
  isImportSidebarOpen: boolean;
  selectedAccount?: BrokerageAccountListResponse;
  selectedAccountId: string;
  onAccountChange: (accountId: string) => void;
  onManageAccounts: () => void;
  onRefresh: () => void;
  onToggleImportSidebar: () => void;
};

export function AccountToolbar({
  accounts,
  accountSelectPlaceholder,
  isAccountsError,
  isAccountsFetching,
  isAccountsLoading,
  accountsError,
  isImportSidebarOpen,
  selectedAccount,
  selectedAccountId,
  onAccountChange,
  onManageAccounts,
  onRefresh,
  onToggleImportSidebar,
}: AccountToolbarProps) {
  return (
    <section className="flex flex-col gap-3 border-b border-[#cfd9d2] py-5 md:flex-row md:items-center md:justify-between">
      <label
        className="text-sm font-semibold text-[#4e5d53]"
        htmlFor="account-select"
      >
        Account
      </label>
      <div className="flex w-full flex-col gap-2 md:w-auto md:min-w-96">
        <div className="flex flex-col gap-2 sm:flex-row">
          <select
            className="min-h-11 w-full rounded-md border border-[#b9c9be] bg-white px-3 text-sm font-semibold text-[#18221d] shadow-sm outline-none transition disabled:cursor-not-allowed disabled:bg-[#f4f7f5] disabled:text-[#7c8b81] focus:border-[#3e6f57] focus:ring-2 focus:ring-[#b9c9be]"
            disabled={isAccountsLoading || accounts.length === 0}
            id="account-select"
            onChange={(event) => onAccountChange(event.target.value)}
            value={selectedAccountId}
          >
            {accounts.length === 0 ? (
              <option value="">{accountSelectPlaceholder}</option>
            ) : null}
            {accounts.map((account) => (
              <option key={account.id} value={account.id}>
                {formatAccountLabel(account)}
              </option>
            ))}
          </select>
          <Button
            className="w-full justify-center sm:w-fit"
            disabled={isAccountsFetching}
            onClick={onRefresh}
            variant="secondary"
          >
            {isAccountsFetching ? "Refreshing" : "Refresh"}
          </Button>
          <Button
            className="w-full justify-center sm:w-fit"
            onClick={onToggleImportSidebar}
            variant="secondary"
          >
            {isImportSidebarOpen ? "Hide imports" : "Show imports"}
          </Button>
          <Button
            className="w-full justify-center sm:w-fit"
            onClick={onManageAccounts}
            variant="secondary"
          >
            Manage accounts
          </Button>
        </div>

        {isAccountsError ? (
          <p className="text-sm font-medium text-[#9a3412]">
            {accountsError instanceof Error
              ? accountsError.message
              : "Failed to load brokerage accounts."}
          </p>
        ) : null}

        {selectedAccount ? (
          <p className="text-sm text-[#65746a]">
            Selected {selectedAccount.brokerName} / {selectedAccount.accountName}
          </p>
        ) : null}
      </div>
    </section>
  );
}
