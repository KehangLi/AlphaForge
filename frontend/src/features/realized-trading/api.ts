import { apiRequest } from "@/lib/api/client";

import type { AccountRealizedTradingSummaryResponse } from "./types";

export type RealizedTradingQuery = {
  from?: string;
  to?: string;
  currency?: string;
  page?: number;
  size?: number;
};

export function getAccountRealizedTradingSummary(
  brokerageAccountId: string,
  query: RealizedTradingQuery = {},
  signal?: AbortSignal,
): Promise<AccountRealizedTradingSummaryResponse> {
  const searchParams = new URLSearchParams();

  if (query.from) searchParams.set("from", query.from);
  if (query.to) searchParams.set("to", query.to);
  if (query.currency) searchParams.set("currency", query.currency);
  if (query.page !== undefined) searchParams.set("page", String(query.page));
  if (query.size !== undefined) searchParams.set("size", String(query.size));

  const queryString = searchParams.toString();
  const path = `/api/brokerage-accounts/${encodeURIComponent(brokerageAccountId)}/realized-trading-summary${queryString ? `?${queryString}` : ""}`;

  return apiRequest<AccountRealizedTradingSummaryResponse>(path, { signal });
}

