"use client";

import { useQuery } from "@tanstack/react-query";

import { getBrokerageAccounts } from "./api";

export const brokerageAccountQueryKeys = {
  all: ["brokerage-accounts"] as const,
  list: () => [...brokerageAccountQueryKeys.all, "list"] as const,
};

export function useBrokerageAccountsQuery() {
  return useQuery({
    queryFn: getBrokerageAccounts,
    queryKey: brokerageAccountQueryKeys.list(),  // so, here is ["brokerage-accounts", "list"]
  });
}

// queryKey --> the name of this data