import { Button } from "@/components/ui/button";

import type { BrokerageAccountListResponse } from "../types";

type DeleteAccountConfirmationProps = {
  account: BrokerageAccountListResponse;
  error?: unknown;
  isPending?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
};

export function DeleteAccountConfirmation({
  account,
  error,
  isPending = false,
  onCancel,
  onConfirm,
}: DeleteAccountConfirmationProps) {
  return (
    <div className="space-y-5">
      <div className="rounded-md border border-[#e7c5b8] bg-[#fff7f3] p-4 text-sm text-[#7c2d12]">
        <p className="font-semibold">
          {account.brokerName} / {account.accountName}
        </p>
        <p className="mt-2">
          Deleting this account also removes its imported activities and import
          batches.
        </p>
      </div>

      {error ? (
        <p className="text-sm font-medium text-[#9a3412]">
          {getErrorMessage(error, "Failed to delete brokerage account.")}
        </p>
      ) : null}

      <div className="flex justify-end gap-2">
        <Button disabled={isPending} onClick={onCancel} variant="secondary">
          Cancel
        </Button>
        <Button
          className="bg-[#9a3412] hover:bg-[#7c2d12]"
          disabled={isPending}
          onClick={onConfirm}
        >
          {isPending ? "Deleting..." : "Delete account"}
        </Button>
      </div>
    </div>
  );
}

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}
