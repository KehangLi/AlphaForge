"use client";

import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { getCachedUserId, isCurrentUser } from "@/features/auth/session-cache";

import {
  createBrokerageAccount,
  deleteBrokerageAccount,
  deleteImportBatch,
  getAccountActivities,
  getBrokerageAccounts,
  getImportBatches,
  importAccountActivities,
} from "./api";
import type { BrokerageAccountListResponse } from "./types";

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
    queryFn: ({ signal }) => getBrokerageAccounts(signal),
    queryKey: brokerageAccountQueryKeys.list(),  // so, here is ["brokerage-accounts", "list"]
  });
}

export function useCreateBrokerageAccountMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createBrokerageAccount,
    onMutate: () => getCachedUserId(queryClient),
    onSuccess: async (createdAccount, _request, userId) => {
      if (!isCurrentUser(queryClient, userId)) return;
      queryClient.setQueryData<BrokerageAccountListResponse[]>(   //cache immediately change to new data
        brokerageAccountQueryKeys.list(),
        (currentAccounts = []) => [
          createdAccount,
          ...currentAccounts.filter((account) => account.id !== createdAccount.id),
        ],
      );

      await queryClient.invalidateQueries({
        queryKey: brokerageAccountQueryKeys.all,
      });
    },
  });
}

export function useDeleteBrokerageAccountMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: deleteBrokerageAccount,
    onMutate: () => getCachedUserId(queryClient),  // the return value will be passed as the third argument
    onSuccess: async (_result, brokerageAccountId, userId) => {
      if (!isCurrentUser(queryClient, userId)) return;
      queryClient.setQueryData<BrokerageAccountListResponse[]>(
        brokerageAccountQueryKeys.list(),
        (currentAccounts) =>
          currentAccounts?.filter((account) => account.id !== brokerageAccountId),
      );
      queryClient.removeQueries({
        queryKey: accountActivityQueryKeys.byAccount(brokerageAccountId),
      });
      queryClient.removeQueries({
        queryKey: importBatchQueryKeys.byAccount(brokerageAccountId),
      });

      await queryClient.invalidateQueries({
        queryKey: brokerageAccountQueryKeys.all,
      });
    },
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
    queryFn: ({ signal }) => getAccountActivities(brokerageAccountId, { page, size }, signal),
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
    queryFn: ({ signal }) => getImportBatches(brokerageAccountId, { page, size }, signal),
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
    onMutate: () => getCachedUserId(queryClient),
    onSuccess: async (result, _request, userId) => {
      if (!isCurrentUser(queryClient, userId)) return;
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
    onMutate: () => getCachedUserId(queryClient),
    onSettled: async (_result, _error, variables, userId) => {
      if (!isCurrentUser(queryClient, userId)) return;
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
