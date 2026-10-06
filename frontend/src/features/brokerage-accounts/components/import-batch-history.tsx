import { useState } from "react";

import { Button } from "@/components/ui/button";

import type { ImportBatchResponse, ImportBatchStatus } from "../types";

type ImportBatchHistoryProps = {
  accountLabel?: string;
  batches: ImportBatchResponse[];
  deletingImportBatchId?: string;
  deleteError?: unknown;
  error?: unknown;
  isDisabled?: boolean;
  isError?: boolean;
  isFetching?: boolean;
  isLoading?: boolean;
  onClose?: () => void;
  onDeleteBatch: (importBatchId: string) => void;
  onRefresh?: () => void;
};

export function ImportBatchHistory({
  accountLabel,
  batches,
  deletingImportBatchId,
  deleteError,
  error,
  isDisabled = false,
  isError = false,
  isFetching = false,
  isLoading = false,
  onClose,
  onDeleteBatch,
  onRefresh,
}: ImportBatchHistoryProps) {
  const [confirmingBatchId, setConfirmingBatchId] = useState<string | null>(
    null,
  );
  const hasBatches = batches.length > 0;

  return (
    <section className="overflow-hidden rounded-lg border border-[#cfd9d2] bg-white shadow-sm">
      <div className="border-b border-[#d8e1db] p-5">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold">Import history</h2>
            <p className="mt-1 text-sm text-[#65746a]">
              {accountLabel
                ? `CSV uploads for ${accountLabel}.`
                : "Select an account to view CSV uploads."}
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <span className="rounded-full bg-[#edf5ef] px-2.5 py-1 text-xs font-semibold text-[#2d654b]">
              {batches.length}
            </span>
            {onRefresh ? (
              <Button
                className="px-3 py-1.5 text-xs"
                disabled={isDisabled || isFetching}
                onClick={onRefresh}
                variant="secondary"
              >
                {isFetching ? "Refreshing" : "Refresh"}
              </Button>
            ) : null}
            {onClose ? (
              <Button
                className="px-3 py-1.5 text-xs"
                onClick={onClose}
                variant="secondary"
              >
                Hide
              </Button>
            ) : null}
          </div>
        </div>
        {isError && hasBatches ? (
          <p className="mt-3 text-sm font-medium text-[#9a3412]">
            {formatErrorMessage(error, "Refresh failed. Showing the last loaded imports.")}
          </p>
        ) : null}
        {deleteError ? (
          <p className="mt-3 text-sm font-medium text-[#9a3412]">
            {formatErrorMessage(deleteError, "Failed to delete import batch.")}
          </p>
        ) : null}
      </div>

      <div className="divide-y divide-[#e3e9e5]">
        {isLoading && !hasBatches ? (
          <div className="p-5 text-sm font-medium text-[#65746a]">
            Loading import history...
          </div>
        ) : null}

        {isError && !hasBatches ? (
          <div className="p-5 text-sm font-medium text-[#9a3412]">
            {formatErrorMessage(error, "Failed to load import history.")}
          </div>
        ) : null}

        {!isLoading && !isError && !hasBatches ? (
          <div className="p-5 text-sm font-medium text-[#65746a]">
            {isDisabled
              ? "Select an account first."
              : "No CSV imports yet for this account."}
          </div>
        ) : null}

        {batches.map((batch) => {
          const isConfirming = confirmingBatchId === batch.id;
          const isDeleting = deletingImportBatchId === batch.id;

          return (
            <article className="p-5" key={batch.id}>
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p
                    className="truncate text-sm font-semibold text-[#18221d]"
                    title={batch.originalFilename}
                  >
                    {batch.originalFilename}
                  </p>
                  <p className="mt-1 text-xs font-medium text-[#65746a]">
                    {formatDateTime(batch.createdAt)}
                  </p>
                </div>
                <StatusBadge status={batch.status} />
              </div>

              <div className="mt-4 grid grid-cols-3 gap-2 text-sm">
                <ImportStat label="Total" value={batch.totalRows} />
                <ImportStat label="Saved" value={batch.successRows} />
                <ImportStat
                  label="Failed"
                  tone={batch.failedRows > 0 ? "warning" : "muted"}
                  value={batch.failedRows}
                />
              </div>

              <div className="mt-4 flex items-center justify-between gap-3">
                <p className="text-xs font-medium text-[#65746a]">
                  Deleting removes this upload and its rows.
                </p>

                {isConfirming ? (
                  <div className="flex shrink-0 gap-2">
                    <Button
                      className="px-3 py-1.5 text-xs"
                      disabled={isDeleting}
                      onClick={() => setConfirmingBatchId(null)}
                      variant="secondary"
                    >
                      Cancel
                    </Button>
                    <Button
                      className="bg-[#9a3412] px-3 py-1.5 text-xs hover:bg-[#7c2d12]"
                      disabled={isDeleting}
                      onClick={() => {
                        onDeleteBatch(batch.id);
                        setConfirmingBatchId(null);
                      }}
                    >
                      {isDeleting ? "Deleting" : "Delete"}
                    </Button>
                  </div>
                ) : (
                  <Button
                    className="shrink-0 px-3 py-1.5 text-xs"
                    disabled={isDisabled || isDeleting}
                    onClick={() => setConfirmingBatchId(batch.id)}
                    variant="secondary"
                  >
                    {isDeleting ? "Deleting" : "Delete"}
                  </Button>
                )}
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}

function formatErrorMessage(error: unknown, fallbackMessage: string) {
  return error instanceof Error ? error.message : fallbackMessage;
}

function ImportStat({
  label,
  tone = "default",
  value,
}: {
  label: string;
  tone?: "default" | "muted" | "warning";
  value: number;
}) {
  const valueColor =
    tone === "warning"
      ? "text-[#9a3412]"
      : tone === "muted"
        ? "text-[#65746a]"
        : "text-[#18221d]";

  return (
    <div className="rounded-md border border-[#e3e9e5] bg-[#f8faf8] px-3 py-2">
      <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-[#65746a]">
        {label}
      </p>
      <p className={`mt-1 text-sm font-semibold ${valueColor}`}>{value}</p>
    </div>
  );
}

function StatusBadge({ status }: { status: ImportBatchStatus }) {
  const statusClassName = getStatusClassName(status);

  return (
    <span
      className={`shrink-0 rounded-full px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.08em] ${statusClassName}`}
    >
      {formatStatus(status)}
    </span>
  );
}

function getStatusClassName(status: ImportBatchStatus) {
  if (status === "COMPLETED") {
    return "bg-[#edf5ef] text-[#2d654b]";
  }

  if (status === "COMPLETED_WITH_ERRORS") {
    return "bg-[#fff4df] text-[#8a4b05]";
  }

  if (status === "FAILED") {
    return "bg-[#fff1ed] text-[#9a3412]";
  }

  return "bg-[#eef3ef] text-[#4e5d53]";
}

function formatStatus(status: ImportBatchStatus) {
  return status.replaceAll("_", " ").toLowerCase();
}

function formatDateTime(value: string) {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "UTC",
  }).format(date);
}
