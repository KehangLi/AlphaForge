import { useState } from "react";

import { Button } from "@/components/ui/button";

import { formatDateTime } from "../formatters";
import type { ImportBatchResponse, ImportBatchStatus } from "../types";

type ImportBatchCardProps = {
  batch: ImportBatchResponse;
  deleting?: boolean;
  disabled?: boolean;
  onDelete: (importBatchId: string) => void;
};

export function ImportBatchCard({
  batch,
  deleting = false,
  disabled = false,
  onDelete,
}: ImportBatchCardProps) {
  const [isConfirming, setIsConfirming] = useState(false);

  return (
    <article className="p-5">
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
              disabled={deleting}
              onClick={() => setIsConfirming(false)}
              variant="secondary"
            >
              Cancel
            </Button>
            <Button
              className="bg-[#9a3412] px-3 py-1.5 text-xs hover:bg-[#7c2d12]"
              disabled={deleting}
              onClick={() => {
                onDelete(batch.id);
                setIsConfirming(false);
              }}
            >
              {deleting ? "Deleting" : "Delete"}
            </Button>
          </div>
        ) : (
          <Button
            className="shrink-0 px-3 py-1.5 text-xs"
            disabled={disabled || deleting}
            onClick={() => setIsConfirming(true)}
            variant="secondary"
          >
            {deleting ? "Deleting" : "Delete"}
          </Button>
        )}
      </div>
    </article>
  );
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
  return (
    <span
      className={`shrink-0 rounded-full px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.08em] ${getStatusClassName(status)}`}
    >
      {status.replaceAll("_", " ").toLowerCase()}
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
