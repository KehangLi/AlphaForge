"use client";

import { useQuery } from "@tanstack/react-query";

import { getHoldings } from "./api";
import type { HoldingFilter, HoldingPageResponse } from "./types";

export const holdingsQueryKeys = {
  all: ["holdings"] as const,
  byAccount: (brokerageAccountId: string) =>
    [...holdingsQueryKeys.all, "list", brokerageAccountId] as const,
  list: (brokerageAccountId: string, status: HoldingFilter, page: number, size: number) =>
    [...holdingsQueryKeys.byAccount(brokerageAccountId), status, page, size] as const,
};

export function useHoldingsQuery({
  brokerageAccountId,
  page = 0,
  size = 20,
  status = "OPEN",
  enabled = true,
}: {
  brokerageAccountId: string;
  page?: number;
  size?: number;
  status?: HoldingFilter;
  enabled?: boolean;
}) {
  return useQuery<HoldingPageResponse>({
    enabled: enabled && brokerageAccountId.length > 0,
    placeholderData: (previousData, previousQuery) =>
      previousData?.brokerageAccountId === brokerageAccountId
        && previousQuery?.queryKey[3] === status
        ? previousData
        : undefined,
    queryFn: ({ signal }) =>
      getHoldings(brokerageAccountId, { page, size, status }, signal),
    queryKey: holdingsQueryKeys.list(brokerageAccountId, status, page, size),
  });
}
