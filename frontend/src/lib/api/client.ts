const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

type CsrfToken = { headerName: string; token: string };
type ApiRequestOptions = RequestInit & { notifyUnauthorized?: boolean };

let csrfTokenRequest: Promise<CsrfToken> | undefined;
let sessionVerificationRequired = false;

export function confirmSession() {
  sessionVerificationRequired = false;
}

export function resetCsrfToken() {
  csrfTokenRequest = undefined;
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const { notifyUnauthorized = true, ...init } = options;
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");

  if (init.body && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  const needsCsrf = !["GET", "HEAD", "OPTIONS"].includes((init.method ?? "GET").toUpperCase());

  init.signal?.throwIfAborted(); // handle if request is aborted?
  if (needsCsrf) {
    assertSessionVerified();
    const csrf = await getCsrfToken();
    assertSessionVerified();
    headers.set(csrf.headerName, csrf.token);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    cache: "no-store",
    ...init,
    headers,
    credentials: "include",
  });

  if (!response.ok) {
    const message = await getErrorMessage(response);
    init.signal?.throwIfAborted();

    if (needsCsrf && response.status === 403 && message === "A valid CSRF token is required") {
      resetCsrfToken();
      sessionVerificationRequired = true;
      // A changed cookie may now identify someone else. Never replay the old write.
      if (typeof window !== "undefined") window.dispatchEvent(new Event("auth:session-changed"));
      throw new ApiError("Your session changed. Please try again.", 403);
    }

    if (response.status === 401 && notifyUnauthorized && typeof window !== "undefined") {
      resetCsrfToken();
      window.dispatchEvent(new Event("auth:unauthorized"));
    }

    throw new ApiError(message || `Request failed with status ${response.status}`, response.status);
  }

  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}

function assertSessionVerified() {
  if (sessionVerificationRequired) {
    throw new ApiError("Checking your session. Please try again.", 403);
  }
}

// single-flight
function getCsrfToken(): Promise<CsrfToken> {
  if (!csrfTokenRequest) {
    const request = fetchCsrfToken();
    csrfTokenRequest = request;
    void request.catch(() => {
      if (csrfTokenRequest === request) resetCsrfToken();
    });
  }
  return csrfTokenRequest;
}

async function fetchCsrfToken(): Promise<CsrfToken> {
  const response = await fetch(`${API_BASE_URL}/api/auth/csrf`, {
    cache: "no-store",
    credentials: "include",
    headers: { Accept: "application/json" },
  });

  if (!response.ok) {
    throw new ApiError(await getErrorMessage(response) || "Unable to prepare request", response.status);
  }

  const token = await response.json() as CsrfToken;
  if (!token.headerName || !token.token) throw new Error("Invalid CSRF token response");
  return token;
}

async function getErrorMessage(response: Response) {
  const contentType = response.headers.get("Content-Type") ?? "";

  if (contentType.includes("application/json")) {
    try {
      const body = await response.json() as { message?: string; error?: string };
      return body.message ?? body.error ?? "";
    } catch {
      return "";
    }
  }

  return "";
}

/***
 GET: Send the request directly with cookies.
 POST/DELETE: Fetch a CSRF token before sending the request.
 401: Sign out of the current session.
 403 (CSRF): Verify the session first; do not retry automatically.

 Business APIs
 -> apiRequest()
 -> Include cookies
 -> Fetch a CSRF token when needed
 -> Send the request
 -> Parse errors
 -> Handle 401 responses / session state
 ***/
