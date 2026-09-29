import { apiRequest } from "@/lib/api/client";

import type {
  AccountActivityCsvImportResult,
  AccountActivityPageResponse,
  BrokerageAccountListResponse,
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
  brokerageAccountId?: string,
): Promise<AccountActivityCsvImportResult> {
  const formData = new FormData();
  formData.append("file", file);

  if (brokerageAccountId) {
    formData.append("brokerageAccountId", brokerageAccountId);
  }

  return apiRequest<AccountActivityCsvImportResult>(
    "/api/account-activities/imports",
    {
      method: "POST",
      body: formData,
    },
  );
}
