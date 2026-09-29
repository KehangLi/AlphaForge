import type {
  AccountActivity,
  BrokerageAccountSummary,
  DashboardMetric,
} from "./types";

export const dashboardMetrics: DashboardMetric[] = [
  { label: "Accounts", value: "2" },
  { label: "Imported rows", value: "1,248" },
  { label: "Base currency", value: "EUR" },
];

export const brokerageAccounts: BrokerageAccountSummary[] = [
  {
    broker: "Trading 212",
    name: "Main brokerage",
    currency: "EUR",
    status: "Ready",
  },
  {
    broker: "Interactive Brokers",
    name: "Long-term",
    currency: "USD",
    status: "Sync pending",
  },
];

export const accountActivities: AccountActivity[] = [
  {
    date: "2026-09-24",
    action: "BUY",
    instrument: "Vanguard FTSE All-World",
    ticker: "VWCE",
    shares: "4",
    amount: "-463.28",
    currency: "EUR",
  },
  {
    date: "2026-09-22",
    action: "DIVIDEND",
    instrument: "Microsoft Corp.",
    ticker: "MSFT",
    shares: "-",
    amount: "18.42",
    currency: "USD",
  },
  {
    date: "2026-09-18",
    action: "SELL",
    instrument: "Apple Inc.",
    ticker: "AAPL",
    shares: "2",
    amount: "421.10",
    currency: "USD",
  },
];
