import type { AccountActivityResponse } from "@/features/brokerage-accounts/types";
import type { HoldingResponse } from "@/features/holdings/types";

export type InstrumentDetailResponse = {
  brokerageAccountId: string;
  instrumentKey: string;
  isin: string | null;
  ticker: string | null;
  instrumentName: string | null;
  holding: HoldingResponse | null;
  realizedSummary?: RealizedTradingSummaryResponse | null;
  activities: AccountActivityResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type RealizedTradingSummaryResponse = {
  sellRecordCount: number;
  includedSellRecordCount: number;
  excludedSellRecordCount: number;
  missingResultCount: number;
  missingResultCurrencyCount: number;
  currencies: RealizedCurrencySummaryResponse[];
};

export type RealizedCurrencySummaryResponse = {
  currency: string;
  totalResult: number;
  totalProfit: number;
  totalLoss: number;
  profitableSellCount: number;
  losingSellCount: number;
  breakevenSellCount: number;
};
