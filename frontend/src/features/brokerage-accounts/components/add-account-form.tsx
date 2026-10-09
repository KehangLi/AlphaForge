import { useState, type FormEvent } from "react";

import { Button } from "@/components/ui/button";

import type { BrokerageAccountCreateRequest } from "../types";

type AddAccountFormProps = {
  error?: unknown;
  isPending?: boolean;
  onCancel: () => void;
  onSubmit: (request: BrokerageAccountCreateRequest) => void;
};

const emptyAccountForm: BrokerageAccountCreateRequest = {
  brokerName: "",
  accountName: "",
  accountNumberMasked: "",
  baseCurrency: "",
};

export function AddAccountForm({
  error,
  isPending = false,
  onCancel,
  onSubmit,
}: AddAccountFormProps) {
  const [form, setForm] = useState<BrokerageAccountCreateRequest>(
    emptyAccountForm,
  );

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit({
      brokerName: form.brokerName.trim(),
      accountName: form.accountName.trim(),
      accountNumberMasked: form.accountNumberMasked?.trim() || undefined,
      baseCurrency: form.baseCurrency.trim().toUpperCase(),
    });
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit}>
      <FormField
        id="broker-name"
        label="Broker name"
        onChange={(value) =>
          setForm((current) => ({ ...current, brokerName: value }))
        }
        placeholder="Interactive Brokers"
        required
        value={form.brokerName}
      />
      <FormField
        id="account-name"
        label="Account name"
        onChange={(value) =>
          setForm((current) => ({ ...current, accountName: value }))
        }
        placeholder="Long-term portfolio"
        required
        value={form.accountName}
      />
      <FormField
        id="account-number-masked"
        label="Masked account number"
        onChange={(value) =>
          setForm((current) => ({ ...current, accountNumberMasked: value }))
        }
        optional
        placeholder="****1234"
        value={form.accountNumberMasked ?? ""}
      />
      <FormField
        id="base-currency"
        label="Base currency"
        maxLength={3}
        onChange={(value) =>
          setForm((current) => ({ ...current, baseCurrency: value }))
        }
        placeholder="EUR"
        required
        uppercase
        value={form.baseCurrency}
      />

      {error ? (
        <p className="text-sm font-medium text-[#9a3412]">
          {getErrorMessage(error, "Failed to create brokerage account.")}
        </p>
      ) : null}

      <div className="flex justify-end gap-2 border-t border-[#d8e1db] pt-4">
        <Button onClick={onCancel} variant="secondary">
          Cancel
        </Button>
        <Button disabled={isPending} type="submit">
          {isPending ? "Saving..." : "Save account"}
        </Button>
      </div>
    </form>
  );
}

type FormFieldProps = {
  id: string;
  label: string;
  onChange: (value: string) => void;
  placeholder: string;
  value: string;
  maxLength?: number;
  optional?: boolean;
  required?: boolean;
  uppercase?: boolean;
};

function FormField({
  id,
  label,
  maxLength,
  onChange,
  optional = false,
  placeholder,
  required = false,
  uppercase = false,
  value,
}: FormFieldProps) {
  return (
    <div className="space-y-1.5">
      <label className="text-sm font-semibold" htmlFor={id}>
        {label}
        {optional ? (
          <span className="ml-1 font-normal text-[#65746a]">(optional)</span>
        ) : null}
      </label>
      <input
        className={`min-h-11 w-full rounded-md border border-[#b9c9be] bg-white px-3 text-sm outline-none focus:border-[#3e6f57] focus:ring-2 focus:ring-[#b9c9be]${uppercase ? " uppercase" : ""}`}
        id={id}
        maxLength={maxLength}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        required={required}
        type="text"
        value={value}
      />
    </div>
  );
}

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}
