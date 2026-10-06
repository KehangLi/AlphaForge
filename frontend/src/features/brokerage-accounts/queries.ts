"use client";

import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  deleteImportBatch,
  getAccountActivities,
  getBrokerageAccounts,
  getImportBatches,
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

export const importBatchQueryKeys = {
  all: ["import-batches"] as const,
  lists: () => [...importBatchQueryKeys.all, "list"] as const,
  byAccount: (brokerageAccountId: string) =>
    [...importBatchQueryKeys.lists(), brokerageAccountId] as const,
  list: (brokerageAccountId: string, page: number, size: number) =>
    [
      ...importBatchQueryKeys.byAccount(brokerageAccountId),
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
    placeholderData: keepPreviousData,
    queryFn: () => getAccountActivities(brokerageAccountId, { page, size }),
    queryKey: accountActivityQueryKeys.list(brokerageAccountId, page, size),
  });
}

type UseImportBatchesQueryParams = {
  brokerageAccountId: string;
  page?: number;
  size?: number;
};

export function useImportBatchesQuery({
  brokerageAccountId,
  page = 0,
  size = 20,
}: UseImportBatchesQueryParams) {
  return useQuery({
    enabled: brokerageAccountId.length > 0,
    placeholderData: keepPreviousData,
    queryFn: () => getImportBatches(brokerageAccountId, { page, size }),
    queryKey: importBatchQueryKeys.list(brokerageAccountId, page, size),
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
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: accountActivityQueryKeys.byAccount(result.brokerageAccountId),
        }),
        queryClient.invalidateQueries({
          queryKey: importBatchQueryKeys.byAccount(result.brokerageAccountId),
        }),
      ]);
    },
  });
}

type DeleteImportBatchParams = {
  brokerageAccountId: string;
  importBatchId: string;
};

export function useDeleteImportBatchMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ brokerageAccountId, importBatchId }: DeleteImportBatchParams) =>
      deleteImportBatch(brokerageAccountId, importBatchId),
    onSuccess: async (_result, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: importBatchQueryKeys.byAccount(variables.brokerageAccountId),
        }),
        queryClient.invalidateQueries({
          queryKey: accountActivityQueryKeys.byAccount(
            variables.brokerageAccountId,
          ),
        }),
      ]);
    },
  });
}
