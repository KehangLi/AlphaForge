import type { NextConfig } from "next";

export function createNextConfig(environment: Partial<NodeJS.ProcessEnv>): NextConfig {
  const configuredOrigin = environment.BACKEND_ORIGIN?.trim();
  const onVercel = environment.VERCEL === "1";

  if (onVercel && !configuredOrigin) {
    throw new Error("Set BACKEND_ORIGIN to the HTTPS backend origin before deploying to Vercel.");
  }

  if (onVercel && (!environment.TRUSTED_PROXY_SECRET || environment.TRUSTED_PROXY_SECRET.length < 32
      || environment.TRUSTED_PROXY_SECRET.length > 256)) {
    throw new Error("Set TRUSTED_PROXY_SECRET (32-256 characters) in Vercel and the Azure backend.");
  }

  let backendOrigin: string | undefined;
  if (configuredOrigin) {
    let url: URL;
    try {
      url = new URL(configuredOrigin);
    } catch {
      throw new Error("BACKEND_ORIGIN must be an HTTP(S) origin without credentials, a path, query, or fragment.");
    }

    const loopback = ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);
    if (url.username || url.password || url.pathname !== "/" || url.search || url.hash
        || !["http:", "https:"].includes(url.protocol)) {
      throw new Error("BACKEND_ORIGIN must be an HTTP(S) origin without credentials, a path, query, or fragment.");
    }
    if ((url.protocol !== "https:" && !loopback) || (onVercel && (loopback || url.protocol !== "https:"))) {
      throw new Error("BACKEND_ORIGIN must use HTTPS. HTTP loopback origins are allowed only outside Vercel for local verification.");
    }

    backendOrigin = url.origin;
  }

  return {
    async rewrites() {
      return backendOrigin ? [{
        source: "/api/:path*",
        destination: `${backendOrigin}/api/:path*`,
      }] : [];
    },
    async headers() {
      return [{
        source: "/api/:path*",
        headers: [
          { key: "Cache-Control", value: "private, no-store" },
          { key: "x-vercel-enable-rewrite-caching", value: "0" },
        ],
      }];
    },
  };
}

export default createNextConfig(process.env);

/***
 example of process.env:

 {
   BACKEND_ORIGIN: "https://backend.example",
   VERCEL: "1"
 }

 ***/
