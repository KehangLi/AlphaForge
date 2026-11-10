export type RealizedCurrencySummaryResponse = {
  currency: string;
  totalResult: number;
  totalProfit: number;
  totalLoss: number;
  profitableSellCount: number;
  losingSellCount: number;
  breakevenSellCount: number;
};

export type RealizedTradingSummaryResponse = {
  sellRecordCount: number;
  includedSellRecordCount: number;
  excludedSellRecordCount: number;
  missingResultCount: number;
  missingResultCurrencyCount: number;
  currencies: RealizedCurrencySummaryResponse[];
};

export type InstrumentRealizedContributionResponse = {
  instrumentKey: string | null;
  isin: string | null;
  ticker: string | null;
  instrumentName: string | null;
  currency: string | null;
  realizedSummary: RealizedTradingSummaryResponse;
};

export type AccountRealizedTradingSummaryResponse = {
  brokerageAccountId: string;
  from: string | null;
  to: string | null;
  currency: string | null;
  realizedSummary: RealizedTradingSummaryResponse;
  unidentifiedSellRecordCount: number;
  contributions: InstrumentRealizedContributionResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

