export type BrokerageAccountSummary = {
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
  amount: string;
  currency: string;
};

export type DashboardMetric = {
  label: string;
  value: string;
};
