import { useState, type FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";

import {
  useCreateBrokerageAccountMutation,
  useDeleteBrokerageAccountMutation,
} from "../queries";
import type {
  BrokerageAccountCreateRequest,
  BrokerageAccountListResponse,
} from "../types";

type AccountManagementDialogProps = {
  accounts: BrokerageAccountListResponse[];
  open: boolean;
  onClose: () => void;
  onAccountCreated: (account: BrokerageAccountListResponse) => void;
  onAccountDeleted: (accountId: string) => void;
};

type DialogView = "list" | "add" | "delete";

const emptyAccountForm: BrokerageAccountCreateRequest = {
  brokerName: "",
  accountName: "",
  accountNumberMasked: "",
  baseCurrency: "",
};

export function AccountManagementDialog({
  accounts,
  open,
  onClose,
  onAccountCreated,
  onAccountDeleted,
}: AccountManagementDialogProps) {
  const [view, setView] = useState<DialogView>("list");
  const [accountToDelete, setAccountToDelete] =
    useState<BrokerageAccountListResponse | null>(null);
  const [accountForm, setAccountForm] =
    useState<BrokerageAccountCreateRequest>(emptyAccountForm);
  const createAccountMutation = useCreateBrokerageAccountMutation();
  const deleteAccountMutation = useDeleteBrokerageAccountMutation();

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
    deleteAccountMutation.reset();
    setAccountToDelete(account);
    setView("delete");
  }

  function handleAddSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    createAccountMutation.mutate(
      {
        brokerName: accountForm.brokerName.trim(),
        accountName: accountForm.accountName.trim(),
        accountNumberMasked: accountForm.accountNumberMasked?.trim() || undefined,
        baseCurrency: accountForm.baseCurrency.trim().toUpperCase(),
      },
      {
        onSuccess: (createdAccount) => {
          onAccountCreated(createdAccount);
          handleDialogClose();
        },
      },
    );
  }

  function handleDialogClose() {
    setView("list");
    setAccountToDelete(null);
    setAccountForm(emptyAccountForm);
    createAccountMutation.reset();
    deleteAccountMutation.reset();
    onClose();
  }

  function handleOpenAdd() {
    createAccountMutation.reset();
    setAccountForm(emptyAccountForm);
    setView("add");
  }

  function handleDeleteAccount() {
    if (!accountToDelete || deleteAccountMutation.isPending) {
      return;
    }

    const deletedAccountId = accountToDelete.id;
    deleteAccountMutation.mutate(deletedAccountId, {
      onSuccess: () => {
        onAccountDeleted(deletedAccountId);
        handleDialogClose();
      },
    });
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
          onAdd={handleOpenAdd}
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
              onChange={(event) =>
                setAccountForm((current) => ({
                  ...current,
                  brokerName: event.target.value,
                }))
              }
              placeholder="Interactive Brokers"
              required
              type="text"
              value={accountForm.brokerName}
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-sm font-semibold" htmlFor="account-name">
              Account name
            </label>
            <input
              className="min-h-11 w-full rounded-md border border-[#b9c9be] bg-white px-3 text-sm outline-none focus:border-[#3e6f57] focus:ring-2 focus:ring-[#b9c9be]"
              id="account-name"
              onChange={(event) =>
                setAccountForm((current) => ({
                  ...current,
                  accountName: event.target.value,
                }))
              }
              placeholder="Long-term portfolio"
              required
              type="text"
              value={accountForm.accountName}
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
              onChange={(event) =>
                setAccountForm((current) => ({
                  ...current,
                  accountNumberMasked: event.target.value,
                }))
              }
              placeholder="****1234"
              type="text"
              value={accountForm.accountNumberMasked}
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
              onChange={(event) =>
                setAccountForm((current) => ({
                  ...current,
                  baseCurrency: event.target.value,
                }))
              }
              placeholder="EUR"
              required
              type="text"
              value={accountForm.baseCurrency}
            />
          </div>

          {createAccountMutation.isError ? (
            <p className="text-sm font-medium text-[#9a3412]">
              {getErrorMessage(
                createAccountMutation.error,
                "Failed to create brokerage account.",
              )}
            </p>
          ) : null}

          <div className="flex justify-end gap-2 border-t border-[#d8e1db] pt-4">
            <Button onClick={() => setView("list")} variant="secondary">
              Cancel
            </Button>
            <Button disabled={createAccountMutation.isPending} type="submit">
              {createAccountMutation.isPending ? "Saving..." : "Save account"}
            </Button>
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

          {deleteAccountMutation.isError ? (
            <p className="text-sm font-medium text-[#9a3412]">
              {getErrorMessage(
                deleteAccountMutation.error,
                "Failed to delete brokerage account.",
              )}
            </p>
          ) : null}

          <div className="flex justify-end gap-2">
            <Button onClick={() => setView("list")} variant="secondary">
              Cancel
            </Button>
            <Button
              className="bg-[#9a3412] hover:bg-[#7c2d12]"
              disabled={deleteAccountMutation.isPending}
              onClick={handleDeleteAccount}
            >
              {deleteAccountMutation.isPending ? "Deleting..." : "Delete account"}
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

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}
