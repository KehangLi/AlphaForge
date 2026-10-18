"use client";

import { ChevronDown, LogOut, UserRound } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";

type RegisteredSessionMenuProps = {
  displayName: string;
  onSignOut: () => void;
  isPending?: boolean;
  error?: string;
};

export function RegisteredSessionMenu({ displayName, onSignOut, isPending = false, error }: RegisteredSessionMenuProps) {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelId = useId();

  useEffect(() => {
    if (!isOpen) return;

    function handlePointerDown(event: PointerEvent) {
      if (event.target instanceof Node && !containerRef.current?.contains(event.target)) setIsOpen(false);
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setIsOpen(false);
        triggerRef.current?.focus();
      }
    }

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen]);

  return (
    <div className="relative ml-auto max-w-full" ref={containerRef}>
      <button
        aria-controls={panelId}
        aria-expanded={isOpen}
        className="flex min-h-11 max-w-full items-center gap-2 rounded-md border border-[#c3cfc7] bg-white px-3 text-sm font-medium text-[#18221d] transition hover:bg-[#f7f9f8] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#3e6f57]"
        onClick={() => setIsOpen((open) => !open)}
        ref={triggerRef}
        type="button"
      >
        <UserRound aria-hidden="true" className="shrink-0 text-[#65746a]" size={17} />
        <span className="max-w-40 truncate" title={displayName}>{displayName}</span>
        <ChevronDown aria-hidden="true" className={`shrink-0 transition ${isOpen ? "rotate-180" : ""}`} size={15} />
      </button>

      {isOpen ? (
        <div className="absolute right-0 top-[calc(100%+0.5rem)] z-30 w-64 max-w-[calc(100vw-2.5rem)] rounded-lg border border-[#cfd9d2] bg-white p-2 shadow-lg" id={panelId}>
          <div className="border-b border-[#e3e9e5] px-3 pb-3 pt-2">
            <p className="break-words text-sm font-semibold [overflow-wrap:anywhere]">{displayName}</p>
            <p className="mt-1 text-xs text-[#65746a]">Personal account</p>
          </div>
          <button className="mt-1 flex min-h-11 w-full items-center gap-2 rounded-md px-3 text-left text-sm font-medium text-[#a3352e] hover:bg-[#fcf1ef] focus-visible:outline-2 focus-visible:outline-[#3e6f57] disabled:cursor-wait disabled:opacity-60" disabled={isPending} onClick={onSignOut} type="button">
            <LogOut aria-hidden="true" size={16} />
            {isPending ? "Signing out..." : "Sign out"}
          </button>
          {error ? <p className="px-3 py-2 text-xs leading-5 text-[#a3352e]" role="alert">{error}</p> : null}
        </div>
      ) : null}
    </div>
  );
}
