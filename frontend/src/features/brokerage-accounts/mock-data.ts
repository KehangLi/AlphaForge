import type {
  AccountActivity,
  BrokerageAccountSummary,
} from "./types";

export const brokerageAccounts: BrokerageAccountSummary[] = [
  {
    id: "trading-212-main",
    broker: "Trading 212",
    name: "Main brokerage",
    currency: "EUR",
    status: "Ready",
  },
  {
    id: "interactive-brokers-long-term",
    broker: "Interactive Brokers",
    name: "Long-term",
    currency: "USD",
    status: "Sync pending",
  },
];

export const accountActivitiesByAccountId: Record<string, AccountActivity[]> = {
  "trading-212-main": [
    {
      date: "2026-09-24",
      action: "BUY",
      instrument: "Vanguard FTSE All-World",
      ticker: "VWCE",
      shares: "4",
      price: "115.82",
      total: "-463.28",
      currency: "EUR",
    },
    {
      date: "2026-09-22",
      action: "DIVIDEND",
      instrument: "Microsoft Corp.",
      ticker: "MSFT",
      shares: "-",
      price: "-",
      total: "18.42",
      currency: "USD",
    },
    {
      date: "2026-09-18",
      action: "SELL",
      instrument: "Apple Inc.",
      ticker: "AAPL",
      shares: "2",
      price: "210.55",
      total: "421.10",
      currency: "USD",
    },
  ],
  "interactive-brokers-long-term": [
    {
      date: "2026-09-25",
      action: "BUY",
      instrument: "Microsoft Corp.",
      ticker: "MSFT",
      shares: "3",
      price: "401.50",
      total: "-1,204.50",
      currency: "USD",
    },
    {
      date: "2026-09-20",
      action: "DIVIDEND",
      instrument: "iShares Core MSCI World",
      ticker: "IWDA",
      shares: "-",
      price: "-",
      total: "32.16",
      currency: "USD",
    },
    {
      date: "2026-09-12",
      action: "FEE",
      instrument: "Exchange fee",
      ticker: "-",
      shares: "-",
      price: "-",
      total: "-2.40",
      currency: "USD",
    },
  ],
};
