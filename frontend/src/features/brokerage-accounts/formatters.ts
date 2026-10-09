import type {
  AccountActivityResponse,
  BrokerageAccountListResponse,
} from "./types";

export function formatAccountLabel(account: BrokerageAccountListResponse) {
  const accountNumber = account.accountNumberMasked
    ? ` ${account.accountNumberMasked}`
    : "";

  return `${account.brokerName} - ${account.accountName}${accountNumber} (${account.baseCurrency})`;
}

export function formatShortAccountLabel(account: BrokerageAccountListResponse) {
  return `${account.brokerName} / ${account.accountName}`;
}

export function formatActivitySummary({
  currentCount,
  page,
  size,
  totalElements,
}: {
  currentCount: number;
  page?: number;
  size?: number;
  totalElements?: number;
}) {
  if (totalElements === undefined || page === undefined || size === undefined) {
    return "Recent rows from the selected brokerage account.";
  }

  const start = totalElements === 0 ? 0 : page * size + 1;
  const end = page * size + currentCount;

  return `Showing ${start}-${end} of ${totalElements} rows.`;
}

export function formatDateTime(value: string) {
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

export function formatPageLabel(page?: number, totalPages?: number) {
  if (page === undefined || totalPages === undefined) {
    return "Page loading...";
  }

  return `Page ${page + 1} of ${Math.max(totalPages, 1)}`;
}

export function formatErrorMessage(error: unknown, fallbackMessage: string) {
  return error instanceof Error ? error.message : fallbackMessage;
}

export function formatInstrumentName(activity: AccountActivityResponse) {
  return (
    activity.instrumentName ??
    activity.merchantName ??
    activity.notes ??
    "Unknown instrument"
  );
}

export function formatInstrumentCode(activity: AccountActivityResponse) {
  return (
    activity.ticker ??
    activity.isin ??
    activity.merchantCategory ??
    "-"
  );
}

export function formatNumber(value: number | null) {
  if (value === null) {
    return "-";
  }

  return new Intl.NumberFormat("en-US", {
    maximumFractionDigits: 8,
  }).format(value);
}

export function formatMoney(amount: number | null, currency: string | null) {
  if (amount === null) {
    return "-";
  }

  const formattedAmount = new Intl.NumberFormat("en-US", {
    maximumFractionDigits: 4,
  }).format(amount);

  return currency ? `${formattedAmount} ${currency}` : formattedAmount;
}
