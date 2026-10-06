import { apiRequest } from "@/lib/api/client";

import type {
  AccountActivityCsvImportResult,
  AccountActivityPageResponse,
  BrokerageAccountListResponse,
  ImportBatchPageResponse,
} from "./types";

export function getBrokerageAccounts(): Promise<BrokerageAccountListResponse[]> {
  return apiRequest<BrokerageAccountListResponse[]>("/api/brokerage-accounts");
}

type AccountActivityQuery = {
  page?: number;
  size?: number;
};

export function getAccountActivities(
  brokerageAccountId: string,
  query: AccountActivityQuery = {},
): Promise<AccountActivityPageResponse> {
  const searchParams = new URLSearchParams();

  if (query.page !== undefined) {
    searchParams.set("page", String(query.page));
  }

  if (query.size !== undefined) {
    searchParams.set("size", String(query.size));
  }

  const queryString = searchParams.toString();
  const path = `/api/brokerage-accounts/${encodeURIComponent(brokerageAccountId)}/account-activities${queryString ? `?${queryString}` : ""}`;

  return apiRequest<AccountActivityPageResponse>(path);
}

export function importAccountActivities(
  file: File,
  brokerageAccountId: string,
): Promise<AccountActivityCsvImportResult> {
  const formData = new FormData();
  formData.append("file", file);

  const path = `/api/brokerage-accounts/${encodeURIComponent(brokerageAccountId)}/account-activities/imports`;

  return apiRequest<AccountActivityCsvImportResult>(
    path,
    {
      method: "POST",
      body: formData,
    },
  );
}

type ImportBatchQuery = {
  page?: number;
  size?: number;
};

export function getImportBatches(
  brokerageAccountId: string,
  query: ImportBatchQuery = {},
): Promise<ImportBatchPageResponse> {
  const searchParams = new URLSearchParams();

  if (query.page !== undefined) {
    searchParams.set("page", String(query.page));
  }

  if (query.size !== undefined) {
    searchParams.set("size", String(query.size));
  }

  const queryString = searchParams.toString();
  const path = `/api/brokerage-accounts/${encodeURIComponent(brokerageAccountId)}/import-batches${queryString ? `?${queryString}` : ""}`;

  return apiRequest<ImportBatchPageResponse>(path);
}

export function deleteImportBatch(
  brokerageAccountId: string,
  importBatchId: string,
): Promise<void> {
  const path = `/api/brokerage-accounts/${encodeURIComponent(brokerageAccountId)}/import-batches/${encodeURIComponent(importBatchId)}`;

  return apiRequest<void>(path, {
    method: "DELETE",
  });
}
