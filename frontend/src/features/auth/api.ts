import { apiRequest } from "@/lib/api/client";

import type { AuthSession } from "./types";

export function createGuestSession(): Promise<AuthSession> {
  return apiRequest<AuthSession>("/api/auth/guest", {
    method: "POST",
    cache: "no-store",
  });
}

export function getCurrentSession(): Promise<AuthSession> {
  return apiRequest<AuthSession>("/api/auth/me", {
    cache: "no-store",
  });
}

export function logout(): Promise<void> {
  return apiRequest<void>("/api/auth/logout", {
    method: "POST",
    cache: "no-store",
  });
}
