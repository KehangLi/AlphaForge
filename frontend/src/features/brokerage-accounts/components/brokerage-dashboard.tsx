"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";

import {
  accountActivitiesByAccountId,
  brokerageAccounts,
} from "../mock-data";
import { AccountActivitiesTable } from "./account-activities-table";

export function BrokerageDashboard() {
  const defaultAccount = brokerageAccounts[0];
  const [selectedAccountId, setSelectedAccountId] = useState(
    defaultAccount.id,
  );
  const selectedAccount =
    brokerageAccounts.find((account) => account.id === selectedAccountId) ??
    defaultAccount;
  const activities = accountActivitiesByAccountId[selectedAccount.id] ?? [];

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
          <select
            className="min-h-11 w-full rounded-md border border-[#b9c9be] bg-white px-3 text-sm font-semibold text-[#18221d] shadow-sm outline-none transition focus:border-[#3e6f57] focus:ring-2 focus:ring-[#b9c9be] md:w-auto md:min-w-80"
            id="account-select"
            onChange={(event) => setSelectedAccountId(event.target.value)}
            value={selectedAccount.id}
          >
            {brokerageAccounts.map((account) => (
              <option key={account.id} value={account.id}>
                {account.broker} - {account.name} ({account.currency})
              </option>
            ))}
          </select>
        </section>

        <section className="flex-1 py-6">
          <AccountActivitiesTable activities={activities} />
        </section>
      </div>
    </main>
  );
}
