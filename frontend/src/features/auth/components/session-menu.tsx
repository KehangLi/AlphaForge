"use client";

import { useRouter } from "next/navigation";

import { GuestSessionMenu } from "@/features/guest-session/components/guest-session-menu";

import { useCurrentSessionQuery, useLogoutMutation } from "../queries";
import { RegisteredSessionMenu } from "./registered-session-menu";

export function SessionMenu() {
  const router = useRouter();
  const { data: session } = useCurrentSessionQuery();
  const logout = useLogoutMutation();

  if (!session || session.userType === "GUEST") return <GuestSessionMenu />;

  return (
    <RegisteredSessionMenu
      displayName={session.displayName}
      error={logout.isError ? logout.error instanceof Error ? logout.error.message : "Unable to sign out. Please try again." : undefined}
      isPending={logout.isPending}
      onSignOut={() => logout.mutate(undefined, { onSuccess: () => router.replace("/") })}
    />
  );
}
