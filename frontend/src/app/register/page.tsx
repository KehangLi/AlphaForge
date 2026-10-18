import type { Metadata } from "next";

import { AuthForm } from "@/features/auth/components/auth-form";
import { AuthPageShell } from "@/features/auth/components/auth-page-shell";

export const metadata: Metadata = { title: "Create account | AlphaForge" };

export default function RegisterPage() {
  return <AuthPageShell><AuthForm key="register" mode="register" /></AuthPageShell>;
}
