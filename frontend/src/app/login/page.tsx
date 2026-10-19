import type { Metadata } from "next";

import { AuthForm } from "@/features/auth/components/auth-form";
import { AuthEntryGate } from "@/features/auth/components/auth-entry-gate";
import { AuthPageShell } from "@/features/auth/components/auth-page-shell";

export const metadata: Metadata = { title: "Sign in | AlphaForge" };

export default function LoginPage() {
  return <AuthEntryGate><AuthPageShell><AuthForm key="login" mode="login" /></AuthPageShell></AuthEntryGate>;
}
