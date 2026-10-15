import { GuestSessionGate } from "@/features/auth/components/guest-session-gate";
import { BrokerageDashboard } from "@/features/brokerage-accounts/components/brokerage-dashboard";

export default function AppPage() {
  return (
    <GuestSessionGate>
      <BrokerageDashboard />
    </GuestSessionGate>
  );
}
