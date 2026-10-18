"use client";

import { createContext, useContext, useState, type ReactNode } from "react";

import type { AuthSession } from "../types";

type AuthPreviewContextValue = {
  session: (AuthSession & { entry: "login" | "register" }) | null;
  start: (displayName: string, entry: "login" | "register") => void;
  end: () => void;
};

const AuthPreviewContext = createContext<AuthPreviewContextValue | null>(null);

// Keep UI previews separate from the server session and TanStack Query cache.
export function AuthPreviewProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthPreviewContextValue["session"]>(null);

  return (
    <AuthPreviewContext.Provider
      value={{
        session,
        start: (displayName, entry) => setSession({
          userId: "ui-preview-user",
          userType: "REGISTERED",
          displayName,
          entry,
        }),
        end: () => setSession(null),
      }}
    >
      {children}
    </AuthPreviewContext.Provider>
  );
}

export function useAuthPreview() {
  const context = useContext(AuthPreviewContext);

  if (!context) {
    throw new Error("useAuthPreview must be used within AuthPreviewProvider");
  }

  return context;
}
