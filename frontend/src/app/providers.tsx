"use client";

import {
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useState, type ReactNode } from "react";

import { AuthPreviewProvider } from "@/features/auth/preview/auth-preview-provider";

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
      queryClient.clear();
      router.replace("/");
    }

    window.addEventListener("auth:unauthorized", handleUnauthorized);
    return () => {
      window.removeEventListener("auth:unauthorized", handleUnauthorized);
    };
  }, [queryClient, router]);

  return (
    <QueryClientProvider client={queryClient}>
      <AuthPreviewProvider>{children}</AuthPreviewProvider>
    </QueryClientProvider>
  );
}
