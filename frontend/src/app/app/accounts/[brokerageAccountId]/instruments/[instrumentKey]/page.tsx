import { SessionGate } from "@/features/auth/components/session-gate";
import { InstrumentDetailPage } from "@/features/instruments/components/instrument-detail-page";

type InstrumentRouteProps = {
  params: Promise<{
    brokerageAccountId: string;
    instrumentKey: string;
  }>;
};

export default async function InstrumentRoute({ params }: InstrumentRouteProps) {
  const { brokerageAccountId, instrumentKey } = await params;

  return (
    <SessionGate>
      <InstrumentDetailPage
        brokerageAccountId={brokerageAccountId}
        instrumentKey={decodeURIComponent(instrumentKey)}
      />
    </SessionGate>
  );
}
