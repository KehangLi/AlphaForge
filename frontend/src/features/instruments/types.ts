import type { AccountActivityResponse } from "@/features/brokerage-accounts/types";
import type { HoldingResponse } from "@/features/holdings/types";

export type InstrumentDetailResponse = {
  brokerageAccountId: string;
  instrumentKey: string;
  isin: string | null;
  ticker: string | null;
  instrumentName: string | null;
  holding: HoldingResponse | null;
  activities: AccountActivityResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};
