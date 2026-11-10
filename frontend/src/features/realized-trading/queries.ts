"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { getAccountRealizedTradingSummary } from "./api";

export const realizedTradingQueryKeys = {
  all: ["realized-trading"] as const,
  byAccount: (brokerageAccountId: string) =>
    [...realizedTradingQueryKeys.all, brokerageAccountId] as const,
  summary: (
    brokerageAccountId: string,
    from: string,
    to: string,
    currency: string,
    page: number,
    size: number,
  ) =>
    [
      ...realizedTradingQueryKeys.byAccount(brokerageAccountId),
      from,
      to,
      currency,
      page,
      size,
    ] as const,
};

export function useAccountRealizedTradingSummaryQuery({
  brokerageAccountId,
  from = "",
  to = "",
  currency = "",
  page = 0,
  size = 10,
  enabled = true,
}: {
  brokerageAccountId: string;
  from?: string;
  to?: string;
  currency?: string;
  page?: number;
  size?: number;
  enabled?: boolean;
}) {
  return useQuery({
    enabled: enabled && brokerageAccountId.length > 0,
    placeholderData: keepPreviousData,
    queryFn: ({ signal }) =>
      getAccountRealizedTradingSummary(
        brokerageAccountId,
        { from, to, currency, page, size },
        signal,
      ),
    queryKey: realizedTradingQueryKeys.summary(
      brokerageAccountId,
      from,
      to,
      currency,
      page,
      size,
    ),
  });
}

