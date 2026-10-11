/**
 * Sole HTTP boundary of the SPA: every API call goes through the
 * api-gateway — no direct service URLs. See docs/architecture.md §3.2.
 *
 * - Base URL configurable via `NEXT_PUBLIC_GATEWAY_URL` (default `/`
 *   — i.e. same-origin relative requests).
 * - Attaches the short-TTL access JWT from the session store (`lib/auth`).
 * - On 401, attempts a single refresh-token rotation and retries once.
 *
 * Plain typed fetch wrappers only — ST-1 must NOT reference generated
 * OpenAPI clients (owned by a parallel task).
 */

import { getStoredAccessToken, refreshStoredSession } from "./auth";

function gatewayBase(): string {
  const raw = process.env.NEXT_PUBLIC_GATEWAY_URL;
  if (raw === undefined || raw === null || raw === "") return "";
  return raw.replace(/\/$/, "");
}

export function buildUrl(path: string): string {
  if (/^https?:\/\//i.test(path)) return path;
  const base = gatewayBase();
  if (!base) return path.startsWith("/") ? path : `/${path}`;
  return `${base}${path.startsWith("/") ? path : `/${path}`}`;
}

export interface ApiError extends Error {
  status: number;
  body: unknown;
}

export function createApiError(status: number, body: unknown): ApiError {
  const message = typeof body === "object" && body !== null && "message" in body
    ? String((body as { message: unknown }).message)
    : `Request failed with status ${status}`;
  const err = new Error(message) as ApiError;
  err.status = status;
  err.body = body;
  return err;
}

async function parseBody(res: Response): Promise<unknown> {
  const text = await res.text();
  if (!text) return null;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

export interface ApiFetchOptions extends RequestInit {
  /** JSON-serialisable payload; sets Content-Type automatically (overrides body). */
  json?: unknown;
  /** Skip attaching the Authorization header (e.g. login/register). */
  anonymous?: boolean;
}

/**
 * Typed fetch against the gateway. Retries once after a refresh on 401.
 */
export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const { json, anonymous, headers, ...rest } = options;

  const send = async (withAuth: boolean): Promise<Response> => {
    const reqHeaders: Record<string, string> = {};
    if (headers) {
      new Headers(headers).forEach((value, key) => {
        reqHeaders[key] = value;
      });
    }
    if (json !== undefined && reqHeaders["content-type"] === undefined) {
      reqHeaders["content-type"] = "application/json";
    }
    if (withAuth && !anonymous) {
      const token = getStoredAccessToken();
      if (token) reqHeaders["authorization"] = `Bearer ${token}`;
    }
    return fetch(buildUrl(path), {
      ...rest,
      headers: reqHeaders,
      body: json !== undefined ? JSON.stringify(json) : rest.body,
    });
  };

  let res = await send(true);
  if (res.status === 401 && !anonymous) {
    const refreshed = await refreshStoredSession();
    if (refreshed) {
      res = await send(true);
    }
  }
  if (!res.ok) {
    throw createApiError(res.status, await parseBody(res));
  }
  return (await parseBody(res)) as T;
}

export const api = {
  get: <T>(path: string, options?: ApiFetchOptions) =>
    apiFetch<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, json?: unknown, options?: ApiFetchOptions) =>
    apiFetch<T>(path, { ...options, method: "POST", json }),
  put: <T>(path: string, json?: unknown, options?: ApiFetchOptions) =>
    apiFetch<T>(path, { ...options, method: "PUT", json }),
  patch: <T>(path: string, json?: unknown, options?: ApiFetchOptions) =>
    apiFetch<T>(path, { ...options, method: "PATCH", json }),
  delete: <T>(path: string, options?: ApiFetchOptions) =>
    apiFetch<T>(path, { ...options, method: "DELETE" }),
};
