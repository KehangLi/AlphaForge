import { isIP } from "node:net";

export function backendProxyHeaders(input: Headers, environment: Partial<NodeJS.ProcessEnv>): Headers {
  const headers = new Headers(input);
  headers.delete("x-alphaforge-proxy-token");
  headers.delete("x-alphaforge-client-ip");

  if (environment.VERCEL === "1") {
    const secret = environment.TRUSTED_PROXY_SECRET;
    const clientIp = input.get("x-vercel-forwarded-for")?.trim();
    if (!secret || secret.length < 32 || secret.length > 256 || !clientIp || !isIP(clientIp)) {
      throw new Error("Trusted API forwarding is not configured.");
    }
    // Vercel supplies this address. Incoming application-specific headers are always replaced.
    headers.set("x-alphaforge-client-ip", clientIp);
    headers.set("x-alphaforge-proxy-token", secret);
  }

  return headers;
}
