import { useState } from "react";

import {
  useAccountActivitiesQuery,
  useBrokerageAccountsQuery,
  useDeleteImportBatchMutation,
  useImportBatchesQuery,
} from "../queries";
import type { BrokerageAccountListResponse } from "../types";

const ACTIVITY_PAGE_SIZE = 10;
const IMPORT_BATCH_PAGE_SIZE = 20;

export function useBrokerageWorkspace() {
  const [selectedAccountId, setSelectedAccountId] = useState("");
  const [activityPage, setActivityPage] = useState(0);
  const [isImportSidebarOpen, setIsImportSidebarOpen] = useState(true);
  const [isAccountManagementOpen, setIsAccountManagementOpen] = useState(false);
  const {
    data: accounts = [],
    error: brokerageAccountsError,
    isError: isBrokerageAccountsError,
    isFetching: isBrokerageAccountsFetching,
    isLoading: isBrokerageAccountsLoading,
    refetch: refetchBrokerageAccounts,
  } = useBrokerageAccountsQuery();
  const deleteImportBatchMutation = useDeleteImportBatchMutation();

  const resolvedSelectedAccountId = accounts.some(
    (account) => account.id === selectedAccountId,
  )
    ? selectedAccountId
    : accounts[0]?.id ?? "";
  const selectedAccount = accounts.find(
    (account) => account.id === resolvedSelectedAccountId,
  );

  const {
    data: accountActivityPage,
    error: accountActivitiesError,
    isError: isAccountActivitiesError,
    isFetching: isAccountActivitiesFetching,
    isLoading: isAccountActivitiesLoading,
    refetch: refetchAccountActivities,
  } = useAccountActivitiesQuery({
    brokerageAccountId: resolvedSelectedAccountId,
    page: activityPage,
    size: ACTIVITY_PAGE_SIZE,
  });
  const {
    data: importBatchPage,
    error: importBatchesError,
    isError: isImportBatchesError,
    isFetching: isImportBatchesFetching,
    isLoading: isImportBatchesLoading,
    refetch: refetchImportBatches,
  } = useImportBatchesQuery({
    brokerageAccountId: resolvedSelectedAccountId,
    page: 0,
    size: IMPORT_BATCH_PAGE_SIZE,
  });
  const deletingImportBatchId = deleteImportBatchMutation.isPending
    ? deleteImportBatchMutation.variables?.importBatchId
    : undefined;

  function resetAccountActions() {
    setActivityPage(0);
    deleteImportBatchMutation.reset();
  }

  function handleAccountChange(accountId: string) {
    setSelectedAccountId(accountId);
    resetAccountActions();
  }

  function handleDeleteImportBatch(importBatchId: string) {
    if (!resolvedSelectedAccountId || deleteImportBatchMutation.isPending) {
      return;
    }

    setActivityPage(0);
    deleteImportBatchMutation.mutate({
      brokerageAccountId: resolvedSelectedAccountId,
      importBatchId,
    });
  }

  function handleAccountCreated(account: BrokerageAccountListResponse) {
    setSelectedAccountId(account.id);
    resetAccountActions();
  }

  function handleAccountDeleted(accountId: string) {
    if (accountId === resolvedSelectedAccountId) {
      setSelectedAccountId("");
    }

    resetAccountActions();
  }

  return {
    accounts,
    accountSelectPlaceholder: getAccountSelectPlaceholder({
      isError: isBrokerageAccountsError,
      isLoading: isBrokerageAccountsLoading,
    }),
    accountsError: brokerageAccountsError,
    isAccountsError: isBrokerageAccountsError,
    isAccountsFetching: isBrokerageAccountsFetching,
    isAccountsLoading: isBrokerageAccountsLoading,
    refetchAccounts: refetchBrokerageAccounts,

    selectedAccount,
    selectedAccountId: resolvedSelectedAccountId,
    onAccountChange: handleAccountChange,
    onAccountCreated: handleAccountCreated,
    onAccountDeleted: handleAccountDeleted,

    accountActivityPage,
    accountActivitiesError,
    isAccountActivitiesError,
    isAccountActivitiesFetching,
    isAccountActivitiesLoading,
    refetchAccountActivities,
    activityPage,
    setActivityPage,

    importBatchPage,
    importBatchesError,
    isImportBatchesError,
    isImportBatchesFetching,
    isImportBatchesLoading,
    refetchImportBatches,
    deletingImportBatchId,
    deleteImportBatchError: deleteImportBatchMutation.error,
    onDeleteImportBatch: handleDeleteImportBatch,

    isImportSidebarOpen,
    setIsImportSidebarOpen,
    isAccountManagementOpen,
    setIsAccountManagementOpen,
  };
}

function getAccountSelectPlaceholder({
  isError,
  isLoading,
}: {
  isError: boolean;
  isLoading: boolean;
}) {
  if (isLoading) {
    return "Loading accounts...";
  }

  if (isError) {
    return "Failed to load accounts";
  }

  return "No accounts found";
}
