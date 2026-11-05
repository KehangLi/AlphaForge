import { apiRequest } from "@/lib/api/client";

import type { InstrumentDetailResponse } from "./types";

export function getInstrumentDetail(
  brokerageAccountId: string,
  instrumentKey: string,
  query: { page?: number; size?: number } = {},
  signal?: AbortSignal,
): Promise<InstrumentDetailResponse> {
  const searchParams = new URLSearchParams();

  if (query.page !== undefined) searchParams.set("page", String(query.page));
  if (query.size !== undefined) searchParams.set("size", String(query.size));

  const queryString = searchParams.toString();
  const path = `/api/brokerage-accounts/${encodeURIComponent(brokerageAccountId)}/instruments/${encodeURIComponent(instrumentKey)}${queryString ? `?${queryString}` : ""}`;

  return apiRequest<InstrumentDetailResponse>(path, { signal });
}
