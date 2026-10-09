"use client";

import { useState } from "react";

import {
  useAccountActivitiesQuery,
  useBrokerageAccountsQuery,
  useDeleteImportBatchMutation,
  useImportBatchesQuery,
} from "../queries";
import {
  formatShortAccountLabel,
} from "../formatters";
import type { BrokerageAccountListResponse } from "../types";
import { AccountManagementDialog } from "./account-management-dialog";
import { AccountActivitiesTable } from "./account-activities-table";
import { AccountToolbar } from "./account-toolbar";
import { CsvImportAction } from "./csv-import-action";
import { ImportBatchHistory } from "./import-batch-history";

const ACTIVITY_PAGE_SIZE = 10;
const IMPORT_BATCH_PAGE_SIZE = 20;

export function BrokerageDashboard() {
  const [selectedAccountId, setSelectedAccountId] = useState("");
  const [activityPage, setActivityPage] = useState(0);
  const [isImportSidebarOpen, setIsImportSidebarOpen] = useState(true);
  const [isAccountManagementOpen, setIsAccountManagementOpen] = useState(false);
  const {
    data: accounts = [],
    error: brokerageAccountsError,
    isError: isBrokerageAccountsError,
    isFetching: isBrokerageAccountsFetching,
    isLoading: isBrokerageAccountsLoading,
    refetch: refetchBrokerageAccounts,
  } = useBrokerageAccountsQuery();
  const deleteImportBatchMutation = useDeleteImportBatchMutation();

  const resolvedSelectedAccountId = accounts.some(
    (account) => account.id === selectedAccountId,
  )
    ? selectedAccountId
    : accounts[0]?.id ?? "";
  const selectedAccount = accounts.find(
    (account) => account.id === resolvedSelectedAccountId,
  );

  const {
    data: accountActivityPage,
    error: accountActivitiesError,
    isError: isAccountActivitiesError,
    isFetching: isAccountActivitiesFetching,
    isLoading: isAccountActivitiesLoading,
    refetch: refetchAccountActivities,
  } = useAccountActivitiesQuery({
    brokerageAccountId: resolvedSelectedAccountId,
    page: activityPage,
    size: ACTIVITY_PAGE_SIZE,
  });
  const {
    data: importBatchPage,
    error: importBatchesError,
    isError: isImportBatchesError,
    isFetching: isImportBatchesFetching,
    isLoading: isImportBatchesLoading,
    refetch: refetchImportBatches,
  } = useImportBatchesQuery({
    brokerageAccountId: resolvedSelectedAccountId,
    page: 0,
    size: IMPORT_BATCH_PAGE_SIZE,
  });
  const deletingImportBatchId = deleteImportBatchMutation.isPending
    ? deleteImportBatchMutation.variables?.importBatchId
    : undefined;

  function resetAccountActions() {
    setActivityPage(0);
    deleteImportBatchMutation.reset();
  }

  function handleAccountChange(accountId: string) {
    setSelectedAccountId(accountId);
    resetAccountActions();
  }

  function handleDeleteImportBatch(importBatchId: string) {
    if (!resolvedSelectedAccountId || deleteImportBatchMutation.isPending) {
      return;
    }

    setActivityPage(0);
    deleteImportBatchMutation.mutate({
      brokerageAccountId: resolvedSelectedAccountId,
      importBatchId,
    });
  }

  function handleAccountCreated(account: BrokerageAccountListResponse) {
    setSelectedAccountId(account.id);
    resetAccountActions();
  }

  function handleAccountDeleted(accountId: string) {
    if (accountId === resolvedSelectedAccountId) {
      setSelectedAccountId("");
    }

    resetAccountActions();
  }

  return (
    <main className="min-h-screen bg-[#eef3ef] text-[#18221d]">
      <div className="mx-auto flex min-h-screen w-full max-w-7xl flex-col px-6 py-6 lg:px-10">
        <header className="flex flex-col gap-4 border-b border-[#cfd9d2] pb-6 md:flex-row md:items-center md:justify-between">
          <div>
            <p className="text-sm font-semibold uppercase tracking-[0.18em] text-[#3e6f57]">
              AlphaForge
            </p>
            <h1 className="mt-3 text-3xl font-semibold tracking-normal text-[#101713] md:text-5xl">
              Brokerage activity console
            </h1>
          </div>

          <CsvImportAction
            accountId={resolvedSelectedAccountId}
            disabled={!selectedAccount}
            key={resolvedSelectedAccountId}
            onImportStarted={() => setActivityPage(0)}
          />
        </header>

        <AccountToolbar
          accountSelectPlaceholder={getAccountSelectPlaceholder({
            isError: isBrokerageAccountsError,
            isLoading: isBrokerageAccountsLoading,
          })}
          accounts={accounts}
          accountsError={brokerageAccountsError}
          isAccountsError={isBrokerageAccountsError}
          isAccountsFetching={isBrokerageAccountsFetching}
          isAccountsLoading={isBrokerageAccountsLoading}
          isImportSidebarOpen={isImportSidebarOpen}
          onAccountChange={handleAccountChange}
          onManageAccounts={() => setIsAccountManagementOpen(true)}
          onRefresh={() => void refetchBrokerageAccounts()}
          onToggleImportSidebar={() =>
            setIsImportSidebarOpen((isCurrentlyOpen) => !isCurrentlyOpen)
          }
          selectedAccount={selectedAccount}
          selectedAccountId={resolvedSelectedAccountId}
        />

        <section
          className={
            isImportSidebarOpen
              ? "grid flex-1 gap-6 py-6 xl:grid-cols-[minmax(0,1fr)_360px]"
              : "grid flex-1 gap-6 py-6"
          }
        >
          <div className="min-w-0">
            <AccountActivitiesTable
              activities={accountActivityPage?.activities ?? []}
              emptyMessage={
                selectedAccount
                  ? "No activity yet. Import a CSV to populate this account."
                  : "Select an account to load activity."
              }
              error={accountActivitiesError}
              isError={isAccountActivitiesError}
              isFetching={isAccountActivitiesFetching}
              isLoading={isAccountActivitiesLoading}
              isPageFirst={accountActivityPage?.first ?? true}
              isPageLast={accountActivityPage?.last ?? true}
              onNextPage={() =>
                setActivityPage((currentPage) => currentPage + 1)
              }
              onPreviousPage={() =>
                setActivityPage((currentPage) => Math.max(currentPage - 1, 0))
              }
              onRefresh={() => void refetchAccountActivities()}
              page={accountActivityPage?.page}
              size={accountActivityPage?.size}
              totalElements={accountActivityPage?.totalElements}
              totalPages={accountActivityPage?.totalPages}
            />
          </div>

          {isImportSidebarOpen ? (
            <aside className="min-w-0 xl:sticky xl:top-6 xl:self-start">
              <ImportBatchHistory
                accountLabel={
                  selectedAccount
                    ? formatShortAccountLabel(selectedAccount)
                    : undefined
                }
                batches={importBatchPage?.importBatches ?? []}
                deletingImportBatchId={deletingImportBatchId}
                deleteError={deleteImportBatchMutation.error}
                error={importBatchesError}
                isDisabled={!selectedAccount}
                isError={isImportBatchesError}
                isFetching={isImportBatchesFetching}
                isLoading={isImportBatchesLoading}
                onClose={() => setIsImportSidebarOpen(false)}
                onDeleteBatch={handleDeleteImportBatch}
                onRefresh={() => void refetchImportBatches()}
              />
            </aside>
          ) : null}
        </section>

        <AccountManagementDialog
          accounts={accounts}
          onAccountCreated={handleAccountCreated}
          onAccountDeleted={handleAccountDeleted}
          onClose={() => setIsAccountManagementOpen(false)}
          open={isAccountManagementOpen}
        />
      </div>
    </main>
  );
}

function getAccountSelectPlaceholder({
  isError,
  isLoading,
}: {
  isError: boolean;
  isLoading: boolean;
}) {
  if (isLoading) {
    return "Loading accounts...";
  }

  if (isError) {
    return "Failed to load accounts";
  }

  return "No accounts found";
}
