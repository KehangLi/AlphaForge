"use client";

import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  getAccountActivities,
  getBrokerageAccounts,
  importAccountActivities,
} from "./api";

export const brokerageAccountQueryKeys = {
  all: ["brokerage-accounts"] as const,
  list: () => [...brokerageAccountQueryKeys.all, "list"] as const,
};

export const accountActivityQueryKeys = {
  all: ["account-activities"] as const,
  lists: () => [...accountActivityQueryKeys.all, "list"] as const,
  byAccount: (brokerageAccountId: string) =>
    [...accountActivityQueryKeys.lists(), brokerageAccountId] as const,
  list: (brokerageAccountId: string, page: number, size: number) =>
    [
      ...accountActivityQueryKeys.byAccount(brokerageAccountId),
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

type ImportAccountActivitiesParams = {
  brokerageAccountId: string;
  file: File;
};

export function useImportAccountActivitiesMutation() {
  const queryClient = useQueryClient();

  // change the data use Mutation
  return useMutation({
    mutationFn: ({ brokerageAccountId, file }: ImportAccountActivitiesParams) =>
      importAccountActivities(file, brokerageAccountId),
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({
        queryKey: accountActivityQueryKeys.byAccount(result.brokerageAccountId),
      });
    },
  });
}
