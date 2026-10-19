"use client";

import {
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useState, type ReactNode } from "react";

import { authQueryKeys, removeCachedUserData, replaceCachedSession } from "@/features/auth/session-cache";

type ProvidersProps = {
  children: ReactNode;
};

export function Providers({ children }: ProvidersProps) {
  const router = useRouter();
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            refetchOnWindowFocus: false,
            retry: 1,
            staleTime: 30_000,
          },
        },
      }),
  );

  useEffect(() => {
    function handleUnauthorized() {
      void replaceCachedSession(queryClient, null);
      router.replace("/login");
    }

    function handleSessionChanged() {
      removeCachedUserData(queryClient);
      void queryClient.resetQueries({ queryKey: authQueryKeys.session() });
    }

    window.addEventListener("auth:unauthorized", handleUnauthorized);
    window.addEventListener("auth:session-changed", handleSessionChanged);
    return () => {
      window.removeEventListener("auth:unauthorized", handleUnauthorized);
      window.removeEventListener("auth:session-changed", handleSessionChanged);
    };
  }, [queryClient, router]);

  return (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );
}
