export type HoldingStatus = "OPEN" | "CLOSED";
export type HoldingFilter = "OPEN" | "CLOSED" | "ALL";

export type HoldingResponse = {
  instrumentKey: string;
  isin: string | null;
  ticker: string | null;
  instrumentName: string | null;
  quantity: number;
  totalBoughtQuantity: number;
  totalSoldQuantity: number;
  lastTradedAt: string;
  status: HoldingStatus;
};

export type HoldingPageResponse = {
  brokerageAccountId: string;
  holdings: HoldingResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};
