import { ArrowLeft, ChartNoAxesCombined, Download } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";

type AuthPageShellProps = {
  children: ReactNode;
};

export function AuthPageShell({ children }: AuthPageShellProps) {
  return (
    <main className="flex min-h-dvh flex-col bg-[#f7f9f8] text-[#18221d]">
      <header className="mx-auto flex w-full max-w-6xl flex-wrap items-center justify-between gap-4 px-5 py-6 sm:px-8">
        <Link className="inline-flex items-center gap-2.5 rounded-sm focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[#3e6f57]" href="/">
          <span className="flex size-9 items-center justify-center rounded-md bg-[#173f35] text-white">
            <ChartNoAxesCombined aria-hidden="true" size={20} strokeWidth={1.8} />
          </span>
          <span className="text-xl font-semibold">AlphaForge</span>
        </Link>
        <Link className="inline-flex min-h-10 items-center gap-2 rounded-sm text-sm font-medium text-[#65746a] transition hover:text-[#173f35] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[#3e6f57]" href="/">
          <ArrowLeft aria-hidden="true" size={16} />
          Back to home
        </Link>
      </header>

      <div className="flex flex-1 items-center justify-center px-5 pb-12 pt-6 sm:px-8 sm:pb-16 sm:pt-10">
        <div className="w-full max-w-[420px]">{children}</div>
      </div>

      <footer className="mx-auto flex w-full max-w-6xl flex-wrap items-center justify-between gap-3 border-t border-[#dce4df] px-5 py-5 text-xs text-[#65746a] sm:px-8">
        <span>AlphaForge / Brokerage activity</span>
        <a className="inline-flex min-h-9 items-center gap-1.5 rounded-sm hover:text-[#173f35] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[#3e6f57]" download href="/templates/account-activity-sample.csv">
          <Download aria-hidden="true" size={14} />
          Sample activity CSV
        </a>
      </footer>
    </main>
  );
}
