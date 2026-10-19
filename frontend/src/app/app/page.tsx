import { SessionGate } from "@/features/auth/components/session-gate";
import { BrokerageDashboard } from "@/features/brokerage-accounts/components/brokerage-dashboard";

export default function AppPage() {
  return (
    <SessionGate>
      <BrokerageDashboard />
    </SessionGate>
  );
}
