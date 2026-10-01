export type BrokerageAccountSummary = {
  id: string;
  broker: string;
  name: string;
  currency: string;
  status: string;
};

export type AccountActivity = {
  date: string;
  action: string;
  instrument: string;
  ticker: string;
  shares: string;
  price: string;
  total: string;
  currency: string;
};

export type DashboardMetric = {
  label: string;
  value: string;
};

export type BrokerageAccountListResponse = {
  id: string;
  brokerName: string;
  accountName: string;
  accountNumberMasked: string;
  baseCurrency: string;
};

export type AccountActivityResponse = {
  id: string;
  brokerageAccountId: string;
  importBatchId: string;
  rowNumber: number;
  externalTransactionId: string | null;
  action: string;
  occurredAt: string;
  isin: string | null;
  ticker: string | null;
  instrumentName: string | null;
  notes: string | null;
  numberOfShares: number | null;
  pricePerShare: number | null;
  pricePerShareCurrency: string | null;
  exchangeRate: number | null;
  resultAmount: number | null;
  resultCurrency: string | null;
  totalAmount: number | null;
  totalCurrency: string | null;
  withholdingTax: number | null;
  withholdingTaxCurrency: string | null;
  currencyConversionFee: number | null;
  currencyConversionFeeCurrency: string | null;
  merchantName: string | null;
  merchantCategory: string | null;
};

export type AccountActivityPageResponse = {
  brokerageAccountId: string;
  activities: AccountActivityResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type ImportBatchStatus =
  | "PENDING"
  | "PROCESSING"
  | "COMPLETED"
  | "COMPLETED_WITH_ERRORS"
  | "FAILED";

export type CsvImportRowError = {
  rowNumber: number;
  columnName: string;
  message: string;
};

export type AccountActivityCsvImportResult = {
  importBatchId: string;
  brokerageAccountId: string;
  originalFilename: string;
  fileHash: string;
  status: ImportBatchStatus;
  totalRows: number;
  successRows: number;
  failedRows: number;
  errors: CsvImportRowError[];
};
