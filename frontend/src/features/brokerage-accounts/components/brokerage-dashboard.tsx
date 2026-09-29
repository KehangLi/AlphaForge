import { Button } from "@/components/ui/button";
import { MetricCard } from "@/components/ui/metric-card";

import {
  accountActivities,
  brokerageAccounts,
  dashboardMetrics,
} from "../mock-data";
import { AccountActivitiesTable } from "./account-activities-table";
import { BrokerageAccountList } from "./brokerage-account-list";

export function BrokerageDashboard() {
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

        <section className="grid gap-4 py-6 md:grid-cols-3">
          {dashboardMetrics.map((metric) => (
            <MetricCard
              key={metric.label}
              label={metric.label}
              value={metric.value}
            />
          ))}
        </section>

        <section className="grid flex-1 gap-5 lg:grid-cols-[320px_1fr]">
          <BrokerageAccountList accounts={brokerageAccounts} />
          <AccountActivitiesTable activities={accountActivities} />
        </section>
      </div>
    </main>
  );
}
