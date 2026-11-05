"use client";

import { useQuery } from "@tanstack/react-query";

import { getInstrumentDetail } from "./api";

export const instrumentQueryKeys = {
  all: ["instruments"] as const,
  detail: (brokerageAccountId: string, instrumentKey: string, page: number, size: number) =>
    [...instrumentQueryKeys.all, brokerageAccountId, instrumentKey, page, size] as const,
};

export function useInstrumentDetailQuery({
  brokerageAccountId,
  instrumentKey,
  page = 0,
  size = 10,
}: {
  brokerageAccountId: string;
  instrumentKey: string;
  page?: number;
  size?: number;
}) {
  return useQuery({
    enabled: brokerageAccountId.length > 0 && instrumentKey.length > 0,
    queryFn: ({ signal }) =>
      getInstrumentDetail(
        brokerageAccountId,
        instrumentKey,
        { page, size },
        signal,
      ),
    queryKey: instrumentQueryKeys.detail(
      brokerageAccountId,
      instrumentKey,
      page,
      size,
    ),
  });
}
