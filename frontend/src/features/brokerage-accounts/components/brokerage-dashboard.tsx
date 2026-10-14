"use client";

import { formatShortAccountLabel } from "../formatters";
import { useBrokerageWorkspace } from "../hooks/use-brokerage-workspace";
import { AccountManagementDialog } from "./account-management-dialog";
import { AccountActivitiesTable } from "./account-activities-table";
import { AccountToolbar } from "./account-toolbar";
import { CsvImportAction } from "./csv-import-action";
import { ImportBatchHistory } from "./import-batch-history";

export function BrokerageDashboard() {
  const workspace = useBrokerageWorkspace();

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
            accountId={workspace.selectedAccountId}
            disabled={!workspace.selectedAccount}
            key={workspace.selectedAccountId}
            onImportStarted={() => workspace.setActivityPage(0)}
          />
        </header>

        <AccountToolbar
          accountSelectPlaceholder={workspace.accountSelectPlaceholder}
          accounts={workspace.accounts}
          accountsError={workspace.accountsError}
          isAccountsError={workspace.isAccountsError}
          isAccountsFetching={workspace.isAccountsFetching}
          isAccountsLoading={workspace.isAccountsLoading}
          isImportSidebarOpen={workspace.isImportSidebarOpen}
          onAccountChange={workspace.onAccountChange}
          onManageAccounts={() => workspace.setIsAccountManagementOpen(true)}
          onRefresh={() => void workspace.refetchAccounts()}
          onToggleImportSidebar={() =>
            workspace.setIsImportSidebarOpen((isCurrentlyOpen) => !isCurrentlyOpen)
          }
          selectedAccount={workspace.selectedAccount}
          selectedAccountId={workspace.selectedAccountId}
        />

        <section
          className={
            workspace.isImportSidebarOpen
              ? "grid flex-1 gap-6 py-6 xl:grid-cols-[minmax(0,1fr)_360px]"
              : "grid flex-1 gap-6 py-6"
          }
        >
          <div className="min-w-0">
            <AccountActivitiesTable
              activities={workspace.accountActivityPage?.activities ?? []}
              emptyMessage={
                workspace.selectedAccount
                  ? "No activity yet. Import a CSV to populate this account."
                  : "Select an account to load activity."
              }
              error={workspace.accountActivitiesError}
              isError={workspace.isAccountActivitiesError}
              isFetching={workspace.isAccountActivitiesFetching}
              isLoading={workspace.isAccountActivitiesLoading}
              isPageFirst={workspace.accountActivityPage?.first ?? true}
              isPageLast={workspace.accountActivityPage?.last ?? true}
              onNextPage={() =>
                workspace.setActivityPage((currentPage) => currentPage + 1)
              }
              onPreviousPage={() =>
                workspace.setActivityPage((currentPage) => Math.max(currentPage - 1, 0))
              }
              onRefresh={() => void workspace.refetchAccountActivities()}
              page={workspace.accountActivityPage?.page}
              size={workspace.accountActivityPage?.size}
              totalElements={workspace.accountActivityPage?.totalElements}
              totalPages={workspace.accountActivityPage?.totalPages}
            />
          </div>

          {workspace.isImportSidebarOpen ? (
            <aside className="min-w-0 xl:sticky xl:top-6 xl:self-start">
              <ImportBatchHistory
                accountLabel={
                  workspace.selectedAccount
                    ? formatShortAccountLabel(workspace.selectedAccount)
                    : undefined
                }
                batches={workspace.importBatchPage?.importBatches ?? []}
                deletingImportBatchId={workspace.deletingImportBatchId}
                deleteError={workspace.deleteImportBatchError}
                error={workspace.importBatchesError}
                isDisabled={!workspace.selectedAccount}
                isError={workspace.isImportBatchesError}
                isFetching={workspace.isImportBatchesFetching}
                isLoading={workspace.isImportBatchesLoading}
                onClose={() => workspace.setIsImportSidebarOpen(false)}
                onDeleteBatch={workspace.onDeleteImportBatch}
                onRefresh={() => void workspace.refetchImportBatches()}
              />
            </aside>
          ) : null}
        </section>

        <AccountManagementDialog
          accounts={workspace.accounts}
          onAccountCreated={workspace.onAccountCreated}
          onAccountDeleted={workspace.onAccountDeleted}
          onClose={() => workspace.setIsAccountManagementOpen(false)}
          open={workspace.isAccountManagementOpen}
        />
      </div>
    </main>
  );
}
