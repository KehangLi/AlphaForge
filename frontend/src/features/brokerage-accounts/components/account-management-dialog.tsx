import { useState } from "react";

import { Dialog } from "@/components/ui/dialog";

import {
  useCreateBrokerageAccountMutation,
  useDeleteBrokerageAccountMutation,
} from "../queries";
import type {
  BrokerageAccountCreateRequest,
  BrokerageAccountListResponse,
} from "../types";
import { AccountListView } from "./account-list-view";
import { AddAccountForm } from "./add-account-form";
import { DeleteAccountConfirmation } from "./delete-account-confirmation";

type AccountManagementDialogProps = {
  accounts: BrokerageAccountListResponse[];
  open: boolean;
  onClose: () => void;
  onAccountCreated: (account: BrokerageAccountListResponse) => void;
  onAccountDeleted: (accountId: string) => void;
};

type DialogView = "list" | "add" | "delete";

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
  const createAccountMutation = useCreateBrokerageAccountMutation();
  const deleteAccountMutation = useDeleteBrokerageAccountMutation();

  const dialogCopy = getDialogCopy(view);

  function handleDialogClose() {
    setView("list");
    setAccountToDelete(null);
    createAccountMutation.reset();
    deleteAccountMutation.reset();
    onClose();
  }

  function handleOpenAdd() {
    createAccountMutation.reset();
    setView("add");
  }

  function handleDeleteClick(account: BrokerageAccountListResponse) {
    deleteAccountMutation.reset();
    setAccountToDelete(account);
    setView("delete");
  }

  function handleAddSubmit(request: BrokerageAccountCreateRequest) {
    createAccountMutation.mutate(request, {
      onSuccess: (createdAccount) => {
        onAccountCreated(createdAccount);
        handleDialogClose();
      },
    });
  }

  function handleDeleteAccount() {
    if (!accountToDelete || deleteAccountMutation.isPending) {
      return;
    }

    deleteAccountMutation.mutate(accountToDelete.id, {
      onSuccess: () => {
        onAccountDeleted(accountToDelete.id);
        handleDialogClose();
      },
    });
  }

  return (
    <Dialog
      description={dialogCopy.description}
      onClose={handleDialogClose}
      open={open}
      title={dialogCopy.title}
    >
      {view === "list" ? (
        <AccountListView
          accounts={accounts}
          onAdd={handleOpenAdd}
          onDelete={handleDeleteClick}
        />
      ) : null}

      {view === "add" ? (
        <AddAccountForm
          error={createAccountMutation.error}
          isPending={createAccountMutation.isPending}
          onCancel={() => setView("list")}
          onSubmit={handleAddSubmit}
        />
      ) : null}

      {view === "delete" && accountToDelete ? (
        <DeleteAccountConfirmation
          account={accountToDelete}
          error={deleteAccountMutation.error}
          isPending={deleteAccountMutation.isPending}
          onCancel={() => setView("list")}
          onConfirm={handleDeleteAccount}
        />
      ) : null}
    </Dialog>
  );
}

function getDialogCopy(view: DialogView) {
  if (view === "add") {
    return {
      title: "Add account",
      description: "Add the account details used for CSV imports.",
    };
  }

  if (view === "delete") {
    return {
      title: "Delete account",
      description: "This action will remove the account and its imported data.",
    };
  }

  return {
    title: "Manage accounts",
    description: "Manage the brokerage accounts available in the account selector.",
  };
}
