import { ApiError, apiRequest, confirmSession, resetCsrfToken } from "@/lib/api/client";

import type { AuthSession, LoginRequest, RegisterRequest } from "./types";

export async function createGuestSession(): Promise<AuthSession> {
  const session = await apiRequest<AuthSession>("/api/auth/guest", {
    method: "POST",
    cache: "no-store",
  });
  resetCsrfToken();
  return session;
}

export async function getCurrentSession(signal?: AbortSignal): Promise<AuthSession | null> {
  try {
    const session = await apiRequest<AuthSession>("/api/auth/me", {
      signal,
      notifyUnauthorized: false,
    });
    signal?.throwIfAborted();
    confirmSession();
    return session;
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      confirmSession();
      return null;
    }
    throw error;
  }
}

export async function login(request: LoginRequest): Promise<AuthSession> {
  const session = await apiRequest<AuthSession>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(request),
    notifyUnauthorized: false,
  });
  resetCsrfToken();
  return session;
}

export async function register(request: RegisterRequest): Promise<AuthSession> {
  const session = await apiRequest<AuthSession>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(request),
    notifyUnauthorized: false,
  });
  resetCsrfToken();
  return session;
}

export async function logout(): Promise<void> {
  await apiRequest<void>("/api/auth/logout", {
    method: "POST",
    cache: "no-store",
  });
  resetCsrfToken();
}
