"use client";

import { useQuery } from "@tanstack/react-query";

import { getAccountActivities, getBrokerageAccounts } from "./api";

export const brokerageAccountQueryKeys = {
  all: ["brokerage-accounts"] as const,
  list: () => [...brokerageAccountQueryKeys.all, "list"] as const,
};

export const accountActivityQueryKeys = {
  all: ["account-activities"] as const,
  list: (brokerageAccountId: string, page: number, size: number) =>
    [
      ...accountActivityQueryKeys.all,
      "list",
      brokerageAccountId,
      page,
      size,
    ] as const,
};

export function useBrokerageAccountsQuery() {
  return useQuery({
    queryFn: getBrokerageAccounts,
    queryKey: brokerageAccountQueryKeys.list(),  // so, here is ["brokerage-accounts", "list"]
  });
}

type UseAccountActivitiesQueryParams = {
  brokerageAccountId: string;
  page?: number;
  size?: number;
};

export function useAccountActivitiesQuery({
  brokerageAccountId,
  page = 0,
  size = 10,
}: UseAccountActivitiesQueryParams) {
  return useQuery({
    enabled: brokerageAccountId.length > 0,
    queryFn: () => getAccountActivities(brokerageAccountId, { page, size }),
    queryKey: accountActivityQueryKeys.list(brokerageAccountId, page, size),
  });
}

// queryKey --> the name of this data
