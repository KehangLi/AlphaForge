import { useRef, type ChangeEvent } from "react";

import { Button } from "@/components/ui/button";

import { useImportAccountActivitiesMutation } from "../queries";

type CsvImportActionProps = {
  accountId: string;
  disabled?: boolean;
  onImportStarted: () => void;
};

export function CsvImportAction({
  accountId,
  disabled = false,
  onImportStarted,
}: CsvImportActionProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const importMutation = useImportAccountActivitiesMutation();

  function handleButtonClick() {
    if (!accountId || disabled || importMutation.isPending) {
      return;
    }

    fileInputRef.current?.click();
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";

    if (!file || !accountId) {
      return;
    }

    onImportStarted();
    importMutation.mutate({
      brokerageAccountId: accountId,
      file,
    });
  }

  return (
    <div className="flex flex-col items-start gap-2 md:items-end">
      <Button
        disabled={disabled || importMutation.isPending}
        onClick={handleButtonClick}
      >
        {importMutation.isPending ? "Uploading CSV" : "Import CSV"}
      </Button>
      <input
        accept=".csv,text/csv"
        className="hidden"
        onChange={handleFileChange}
        ref={fileInputRef}
        type="file"
      />
      <UploadStatusMessage
        error={importMutation.error}
        isError={importMutation.isError}
        result={importMutation.data}
      />
    </div>
  );
}

function UploadStatusMessage({
  error,
  isError,
  result,
}: {
  error: unknown;
  isError: boolean;
  result?: {
    successRows: number;
    totalRows: number;
    failedRows: number;
    errors: {
      rowNumber: number;
      columnName: string | null;
      message: string;
    }[];
  };
}) {
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
