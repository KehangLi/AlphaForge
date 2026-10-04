"use client";

import { useRef, useState, type ChangeEvent } from "react";

import { Button } from "@/components/ui/button";

import {
  useAccountActivitiesQuery,
  useBrokerageAccountsQuery,
  useImportAccountActivitiesMutation,
} from "../queries";
import type {
  AccountActivityCsvImportResult,
  BrokerageAccountListResponse,
} from "../types";
import { AccountActivitiesTable } from "./account-activities-table";

const ACTIVITY_PAGE_SIZE = 10;

export function BrokerageDashboard() {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [selectedAccountId, setSelectedAccountId] = useState("");
  const [activityPage, setActivityPage] = useState(0);
  const {
    data: accounts = [],
    error: brokerageAccountsError,
    isError: isBrokerageAccountsError,
    isFetching: isBrokerageAccountsFetching,
    isLoading: isBrokerageAccountsLoading,
    refetch: refetchBrokerageAccounts,
  } = useBrokerageAccountsQuery();     //const accounts = await getBrokerageAccounts()
  const importMutation = useImportAccountActivitiesMutation();

  // check if the selectedAccount exist?
  const resolvedSelectedAccountId = accounts.some(
    (account) => account.id === selectedAccountId,
  )
    ? selectedAccountId
    : accounts[0]?.id ?? "";

  // according to the account ID, to find the account
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

  function handleImportButtonClick() {
    if (!resolvedSelectedAccountId || importMutation.isPending) {
      return;
    }

    fileInputRef.current?.click();
  }

  function handleCsvFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";

    if (!file || !resolvedSelectedAccountId) {
      return;
    }

    setActivityPage(0);
    importMutation.mutate({
      brokerageAccountId: resolvedSelectedAccountId,
      file,
    });
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

          <div className="flex flex-col items-start gap-2 md:items-end">
            <Button
              disabled={!selectedAccount || importMutation.isPending}
              onClick={handleImportButtonClick}
            >
              {importMutation.isPending ? "Uploading CSV" : "Import CSV"}
            </Button>
            <input
              accept=".csv,text/csv"
              className="hidden"
              onChange={handleCsvFileChange}
              ref={fileInputRef}
              type="file"
            />
            <UploadStatusMessage
              error={importMutation.error}
              isError={importMutation.isError}
              result={importMutation.data}
            />
          </div>
        </header>

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
                disabled={isBrokerageAccountsLoading || accounts.length === 0}
                id="account-select"
                onChange={(event) => {
                  setSelectedAccountId(event.target.value);
                  setActivityPage(0);
                  importMutation.reset();
                }}
                value={resolvedSelectedAccountId}
              >
                {accounts.length === 0 ? (
                  <option value="">
                    {isBrokerageAccountsLoading ? "Loading accounts..." : "No accounts found"}
                  </option>
                ) : null}
                {accounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {formatAccountLabel(account)}
                  </option>
                ))}
              </select>
              <Button
                className="w-full justify-center sm:w-fit"
                disabled={isBrokerageAccountsFetching}
                onClick={() => void refetchBrokerageAccounts()}
                variant="secondary"
              >
                {isBrokerageAccountsFetching ? "Refreshing" : "Refresh"}
              </Button>
            </div>

            {isBrokerageAccountsError ? (
              <p className="text-sm font-medium text-[#9a3412]">
                {brokerageAccountsError instanceof Error
                  ? brokerageAccountsError.message
                  : "Failed to load brokerage accounts."}
              </p>
            ) : null}

            {selectedAccount ? (
              <p className="text-sm text-[#65746a]">
                Selected {selectedAccount.brokerName} /{" "}
                {selectedAccount.accountName}
              </p>
            ) : null}
          </div>
        </section>

        <section className="flex-1 py-6">
          <AccountActivitiesTable
            activities={accountActivityPage?.activities ?? []}
            emptyMessage={
              selectedAccount
                ? "No account activity found for this account."
                : "Select an account to load activity."
            }
            error={accountActivitiesError}
            isError={isAccountActivitiesError}
            isFetching={isAccountActivitiesFetching}
            isLoading={isAccountActivitiesLoading}
            isPageFirst={accountActivityPage?.first ?? true}
            isPageLast={accountActivityPage?.last ?? true}
            onNextPage={() => setActivityPage((currentPage) => currentPage + 1)}
            onPreviousPage={() => setActivityPage((currentPage) => Math.max(currentPage - 1, 0))}
            onRefresh={() => void refetchAccountActivities()}
            page={accountActivityPage?.page}
            size={accountActivityPage?.size}
            totalElements={accountActivityPage?.totalElements}
            totalPages={accountActivityPage?.totalPages}
          />
        </section>
      </div>
    </main>
  );
}

function formatAccountLabel(account: BrokerageAccountListResponse) {
  const accountNumber = account.accountNumberMasked
    ? ` ${account.accountNumberMasked}`
    : "";

  return `${account.brokerName} - ${account.accountName}${accountNumber} (${account.baseCurrency})`;
}

type UploadStatusMessageProps = {
  error: unknown;
  isError: boolean;
  result?: AccountActivityCsvImportResult;
};

function UploadStatusMessage({
  error,
  isError,
  result,
}: UploadStatusMessageProps) {
  if (isError) {
    return (
      <p className="max-w-sm text-sm font-medium text-[#9a3412]">
        {error instanceof Error ? error.message : "CSV upload failed."}
      </p>
    );
  }

  if (!result) {
    return null;
  }

  const firstError = result.errors[0];

  return (
    <div className="max-w-sm text-sm font-medium text-[#2d654b] md:text-right">
      <p>
        Imported {result.successRows} of {result.totalRows} rows
        {result.failedRows > 0 ? `, ${result.failedRows} failed` : ""}.
      </p>
      {firstError ? (
        <p className="mt-1 text-[#9a3412]">
          First error: row {firstError.rowNumber}
          {firstError.columnName ? ` / ${firstError.columnName}` : ""} -{" "}
          {firstError.message}
        </p>
      ) : null}
    </div>
  );
}
