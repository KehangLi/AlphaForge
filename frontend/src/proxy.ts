import { NextResponse, type NextRequest } from "next/server";

import { backendProxyHeaders } from "@/lib/api/proxy-headers";

export function proxy(request: NextRequest) {
  try {
    return NextResponse.next({ request: { headers: backendProxyHeaders(request.headers, process.env) } });
  } catch {
    return NextResponse.json({ message: "Unable to reach AlphaForge. Please try again later." }, {
      status: 503,
      headers: { "Cache-Control": "no-store" },
    });
  }
}

export const config = { matcher: "/api/:path*" };
