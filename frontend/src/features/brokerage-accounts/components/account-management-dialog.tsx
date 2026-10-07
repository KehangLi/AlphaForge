"use client";

import { useState, type FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";

import type { BrokerageAccountListResponse } from "../types";

type AccountManagementDialogProps = {
  accounts: BrokerageAccountListResponse[];
  open: boolean;
  onClose: () => void;
};

type DialogView = "list" | "add" | "delete";

export function AccountManagementDialog({
  accounts,
  open,
  onClose,
}: AccountManagementDialogProps) {
  const [view, setView] = useState<DialogView>("list");
  const [accountToDelete, setAccountToDelete] =
    useState<BrokerageAccountListResponse | null>(null);

  const dialogTitle =
    view === "add"
      ? "Add account"
      : view === "delete"
        ? "Delete account"
        : "Manage accounts";
  const dialogDescription =
    view === "add"
      ? "Add the account details used for CSV imports."
      : view === "delete"
        ? "This action will remove the account and its imported data."
        : "Manage the brokerage accounts available in the account selector.";

  function handleDeleteClick(account: BrokerageAccountListResponse) {
    setAccountToDelete(account);
    setView("delete");
  }

  function handleAddSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
  }

  function handleDialogClose() {
    setView("list");
    setAccountToDelete(null);
    onClose();
  }

  return (
    <Dialog
      description={dialogDescription}
      onClose={handleDialogClose}
      open={open}
      title={dialogTitle}
    >
      {view === "list" ? (
        <AccountListView
          accounts={accounts}
          onAdd={() => setView("add")}
          onDelete={handleDeleteClick}
        />
      ) : null}

      {view === "add" ? (
        <form className="space-y-4" onSubmit={handleAddSubmit}>
          <div className="space-y-1.5">
            <label className="text-sm font-semibold" htmlFor="broker-name">
              Broker name
            </label>
            <input
              className="min-h-11 w-full rounded-md border border-[#b9c9be] bg-white px-3 text-sm outline-none focus:border-[#3e6f57] focus:ring-2 focus:ring-[#b9c9be]"
              id="broker-name"
              placeholder="Interactive Brokers"
              type="text"
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-sm font-semibold" htmlFor="account-name">
              Account name
            </label>
            <input
              className="min-h-11 w-full rounded-md border border-[#b9c9be] bg-white px-3 text-sm outline-none focus:border-[#3e6f57] focus:ring-2 focus:ring-[#b9c9be]"
              id="account-name"
              placeholder="Long-term portfolio"
              type="text"
            />
          </div>

          <div className="space-y-1.5">
            <label
              className="text-sm font-semibold"
              htmlFor="account-number-masked"
            >
              Masked account number
              <span className="ml-1 font-normal text-[#65746a]">(optional)</span>
            </label>
            <input
              className="min-h-11 w-full rounded-md border border-[#b9c9be] bg-white px-3 text-sm outline-none focus:border-[#3e6f57] focus:ring-2 focus:ring-[#b9c9be]"
              id="account-number-masked"
              placeholder="****1234"
              type="text"
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-sm font-semibold" htmlFor="base-currency">
              Base currency
            </label>
            <input
              className="min-h-11 w-full rounded-md border border-[#b9c9be] bg-white px-3 text-sm uppercase outline-none focus:border-[#3e6f57] focus:ring-2 focus:ring-[#b9c9be]"
              id="base-currency"
              maxLength={3}
              placeholder="EUR"
              type="text"
            />
          </div>

          <div className="flex justify-end gap-2 border-t border-[#d8e1db] pt-4">
            <Button onClick={() => setView("list")} variant="secondary">
              Cancel
            </Button>
            <Button type="submit">Save account</Button>
          </div>
        </form>
      ) : null}

      {view === "delete" && accountToDelete ? (
        <div className="space-y-5">
          <div className="rounded-md border border-[#e7c5b8] bg-[#fff7f3] p-4 text-sm text-[#7c2d12]">
            <p className="font-semibold">
              {accountToDelete.brokerName} / {accountToDelete.accountName}
            </p>
            <p className="mt-2">
              Deleting this account also removes its imported activities and
              import batches.
            </p>
          </div>

          <div className="flex justify-end gap-2">
            <Button onClick={() => setView("list")} variant="secondary">
              Cancel
            </Button>
            <Button
              className="bg-[#9a3412] hover:bg-[#7c2d12]"
              onClick={() => setView("list")}
            >
              Delete account
            </Button>
          </div>
        </div>
      ) : null}
    </Dialog>
  );
}

type AccountListViewProps = {
  accounts: BrokerageAccountListResponse[];
  onAdd: () => void;
  onDelete: (account: BrokerageAccountListResponse) => void;
};

function AccountListView({ accounts, onAdd, onDelete }: AccountListViewProps) {
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
