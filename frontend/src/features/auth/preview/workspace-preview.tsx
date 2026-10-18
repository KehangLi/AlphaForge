"use client";

import { ChartNoAxesCombined, Download, FlaskConical, Plus } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { AccountActivitiesTable } from "@/features/brokerage-accounts/components/account-activities-table";
import { AddAccountForm } from "@/features/brokerage-accounts/components/add-account-form";
import type { AccountActivityResponse, BrokerageAccountCreateRequest, BrokerageAccountListResponse } from "@/features/brokerage-accounts/types";

import { AuthPageShell } from "../components/auth-page-shell";
import { RegisteredSessionMenu } from "../components/registered-session-menu";
import { useAuthPreview } from "./auth-preview-provider";

const sampleAccount: BrokerageAccountListResponse = {
  id: "sample-account",
  brokerName: "Trading 212",
  accountName: "Sample portfolio",
  accountNumberMasked: null,
  baseCurrency: "EUR",
};

const sampleActivities: AccountActivityResponse[] = [
  sampleActivity("sample-buy", "2026-10-01T09:22:00Z", "Limit buy", 2, 151.4, 265.27),
  sampleActivity("sample-sell", "2026-10-01T12:49:00Z", "Limit sell", 1, 154.1, 134.96),
].reverse();

export function WorkspacePreview() {
  const preview = useAuthPreview();

  if (!preview.session) {
    return (
      <AuthPageShell>
        <h1 className="text-[32px] font-semibold">Your workspace</h1>
        <p className="mt-3 text-sm leading-6 text-[#65746a]">Sign in or create an account to continue.</p>
        <div className="mt-7 flex flex-wrap gap-3">
          <Link className="inline-flex min-h-11 items-center rounded-md bg-[#173f35] px-5 text-sm font-semibold text-white hover:bg-[#0f2f27]" href="/login">Sign in</Link>
          <Link className="inline-flex min-h-11 items-center rounded-md border border-[#bac8bf] px-5 text-sm font-semibold text-[#173f35] hover:bg-[#eef3ef]" href="/register">Create account</Link>
        </div>
      </AuthPageShell>
    );
  }

  return <PreviewContent displayName={preview.session.displayName} entry={preview.session.entry} onEnd={preview.end} />;
}

function PreviewContent({ displayName, entry, onEnd }: {
  displayName: string;
  entry: "login" | "register";
  onEnd: () => void;
}) {
  const router = useRouter();
  const [accounts, setAccounts] = useState<BrokerageAccountListResponse[]>(entry === "login" ? [sampleAccount] : []);
  const [selectedId, setSelectedId] = useState(entry === "login" ? sampleAccount.id : "");
  const [isAddOpen, setIsAddOpen] = useState(false);
  const accountSequence = useRef(0);
  const selected = accounts.find((account) => account.id === selectedId);
  const activities = selectedId === sampleAccount.id ? sampleActivities : [];

  function handleAddAccount(request: BrokerageAccountCreateRequest) {
    const account = { ...request, id: `preview-account-${++accountSequence.current}`, accountNumberMasked: request.accountNumberMasked ?? null };
    setAccounts((current) => [...current, account]);
    setSelectedId(account.id);
    setIsAddOpen(false);
  }

  return (
    <main className="min-h-dvh bg-[#eef3ef] text-[#18221d]">
      <div className="mx-auto w-full max-w-7xl px-5 py-6 sm:px-8 lg:px-10">
        <header className="flex flex-wrap items-center justify-between gap-4 border-b border-[#cfd9d2] pb-5">
          <Link className="inline-flex items-center gap-2 text-xl font-semibold" href="/">
            <ChartNoAxesCombined aria-hidden="true" size={23} />
            AlphaForge
          </Link>
          <RegisteredSessionMenu displayName={displayName} onSignOut={() => { onEnd(); router.replace("/"); }} />
        </header>

        <div className="flex items-center gap-2 border-b border-[#cfd9d2] py-3 text-xs font-medium text-[#59648b]">
          <FlaskConical aria-hidden="true" size={15} />
          Sample workspace
        </div>

        <div className="flex flex-wrap items-center justify-between gap-4 py-7">
          <h1 className="text-2xl font-semibold sm:text-3xl">Brokerage activity</h1>
          {accounts.length > 0 ? (
            <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto">
              <label className="sr-only" htmlFor="preview-account">Brokerage account</label>
              <select className="min-h-11 min-w-0 flex-1 rounded-md border border-[#b9c9be] bg-white px-3 text-sm outline-none focus:border-[#3e6f57] focus:ring-2 focus:ring-[#d5e5dc] sm:max-w-64" id="preview-account" onChange={(event) => setSelectedId(event.target.value)} value={selectedId}>
                {accounts.map((account) => <option key={account.id} value={account.id}>{account.accountName}</option>)}
              </select>
              <Button className="inline-flex min-h-11 shrink-0 items-center gap-2" onClick={() => setIsAddOpen(true)} variant="secondary"><Plus aria-hidden="true" size={16} />Add account</Button>
            </div>
          ) : null}
        </div>

        {selected ? (
          <>
            <p className="mb-4 text-sm text-[#65746a]">{selected.brokerName} / {selected.accountName} / {selected.baseCurrency}</p>
            <AccountActivitiesTable activities={activities} emptyMessage="No activity yet. Your imports will appear here." page={0} size={10} totalElements={activities.length} totalPages={activities.length ? 1 : 0} />
          </>
        ) : (
          <section className="flex min-h-[360px] flex-col items-center justify-center border-y border-[#d6e0d9] py-14 text-center">
            <span className="mb-5 flex size-12 items-center justify-center rounded-lg bg-[#e1e9e4] text-[#3e6f57]"><ChartNoAxesCombined aria-hidden="true" size={23} /></span>
            <h2 className="text-xl font-semibold">Add your first brokerage account</h2>
            <p className="mt-3 max-w-sm text-sm leading-6 text-[#65746a]">Your account activity starts here.</p>
            <Button className="mt-6 inline-flex min-h-11 items-center gap-2" onClick={() => setIsAddOpen(true)}><Plus aria-hidden="true" size={17} />Add brokerage account</Button>
          </section>
        )}

        <a className="mt-6 inline-flex min-h-10 items-center gap-2 text-sm font-medium text-[#3e6f57] underline underline-offset-4" download href="/templates/account-activity-sample.csv"><Download aria-hidden="true" size={16} />Download sample CSV</a>

        <Dialog description="Account details" onClose={() => setIsAddOpen(false)} open={isAddOpen} title="Add brokerage account">
          <AddAccountForm onCancel={() => setIsAddOpen(false)} onSubmit={handleAddAccount} />
        </Dialog>
      </div>
    </main>
  );
}

function sampleActivity(id: string, occurredAt: string, action: string, numberOfShares: number, pricePerShare: number, totalAmount: number): AccountActivityResponse {
  return {
    id, occurredAt, action, numberOfShares, pricePerShare, totalAmount,
    brokerageAccountId: sampleAccount.id,
    importBatchId: "sample-import",
    rowNumber: 2,
    externalTransactionId: null,
    isin: "US67066G1040",
    ticker: "NVDA",
    instrumentName: "NVIDIA",
    notes: null,
    pricePerShareCurrency: "USD",
    exchangeRate: null,
    resultAmount: null,
    resultCurrency: null,
    totalCurrency: "EUR",
    withholdingTax: null,
    withholdingTaxCurrency: null,
    currencyConversionFee: null,
    currencyConversionFeeCurrency: null,
    merchantName: null,
    merchantCategory: null,
  };
}
