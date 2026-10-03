"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";

import { useBrokerageAccountsQuery } from "../queries";
import type { BrokerageAccountListResponse } from "../types";
import { AccountActivitiesTable } from "./account-activities-table";

export function BrokerageDashboard() {
  const [selectedAccountId, setSelectedAccountId] = useState("");
  const {
    data: accounts = [],
    error,
    isError,
    isFetching,
    isLoading,
    refetch,
  } = useBrokerageAccountsQuery();     //const accounts = await getBrokerageAccounts()

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

          <Button>Import CSV</Button>
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
                disabled={isLoading || accounts.length === 0}
                id="account-select"
                onChange={(event) => setSelectedAccountId(event.target.value)}
                value={resolvedSelectedAccountId}
              >
                {accounts.length === 0 ? (
                  <option value="">
                    {isLoading ? "Loading accounts..." : "No accounts found"}
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
                disabled={isFetching}
                onClick={() => void refetch()}
                variant="secondary"
              >
                {isFetching ? "Refreshing" : "Refresh"}
              </Button>
            </div>

            {isError ? (
              <p className="text-sm font-medium text-[#9a3412]">
                {error instanceof Error
                  ? error.message
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
          <AccountActivitiesTable activities={[]} />
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
