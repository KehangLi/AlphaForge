import { Button } from "@/components/ui/button";

import { formatErrorMessage } from "../formatters";
import type { ImportBatchResponse } from "../types";
import { ImportBatchCard } from "./import-batch-card";

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
            {formatErrorMessage(
              error,
              "Refresh failed. Showing the last loaded imports.",
            )}
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

        {batches.map((batch) => (
          <ImportBatchCard
            batch={batch}
            deleting={deletingImportBatchId === batch.id}
            disabled={isDisabled}
            key={batch.id}
            onDelete={onDeleteBatch}
          />
        ))}
      </div>
    </section>
  );
}
