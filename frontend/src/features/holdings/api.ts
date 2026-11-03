import { apiRequest } from "@/lib/api/client";

import type { HoldingFilter, HoldingPageResponse } from "./types";

type HoldingQuery = {
  status?: HoldingFilter;
  search?: string;
  page?: number;
  size?: number;
};

export function getHoldings(
  brokerageAccountId: string,
  query: HoldingQuery = {},
  signal?: AbortSignal,
): Promise<HoldingPageResponse> {
  const searchParams = new URLSearchParams();

  if (query.status !== undefined) searchParams.set("status", query.status);
  if (query.search !== undefined) searchParams.set("search", query.search);
  if (query.page !== undefined) searchParams.set("page", String(query.page));
  if (query.size !== undefined) searchParams.set("size", String(query.size));

  const queryString = searchParams.toString();
  const path = `/api/brokerage-accounts/${encodeURIComponent(brokerageAccountId)}/holdings${queryString ? `?${queryString}` : ""}`;

  return apiRequest<HoldingPageResponse>(path, { signal });
}
