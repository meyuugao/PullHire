/**
 * Session store: short-TTL access JWT + refresh-token lifecycle.
 * See docs/architecture.md §6.1 — RS256 access token with short TTL,
 * opaque refresh token, JWKS validation server-side; the SPA only
 * carries tokens and proactively refreshes before expiry.
 *
 * No state-management library: plain React context + hooks.
 * Client-side only (static export — no middleware, no server session).
 */

"use client";

import { createContext, createElement, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { apiFetch } from "./api";

export interface SessionTokens {
  accessToken: string | null;
  refreshToken: string | null;
  /** Whether the 152-ФЗ consents gate has been accepted. */
  consentsAccepted: boolean;
}

export interface AuthContextValue extends SessionTokens {
  isAuthenticated: boolean;
  /** Persist a fresh session (e.g. after login/register/verify). */
  setSession: (tokens: { accessToken: string; refreshToken: string; consentsAccepted?: boolean }) => void;
  /** Update the consents flag (e.g. after /consents accepted). */
  setConsentsAccepted: (accepted: boolean) => void;
  /** Attempt a refresh-token rotation. Resolves true on success. */
  refresh: () => Promise<boolean>;
  /** Clear session (logout). */
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const LS_ACCESS = "pullhire.accessToken";
const LS_REFRESH = "pullhire.refreshToken";
const LS_CONSENTS = "pullhire.consentsAccepted";
/** Refresh slightly before access-token expiry to avoid racing the TTL. */
const REFRESH_SKEW_MS = 30_000;

function gatewayBase(): string {
  return process.env.NEXT_PUBLIC_GATEWAY_URL ?? "";
}

function joinUrl(base: string, path: string): string {
  if (/^https?:\/\//i.test(path)) return path;
  if (!base) return path;
  return `${base.replace(/\/$/, "")}${path.startsWith("/") ? path : `/${path}`}`;
}

/** Best-effort `exp` (seconds) extraction from a JWT payload; null when opaque/unparseable. */
export function getAccessExpiryMs(accessToken: string | null): number | null {
  if (!accessToken) return null;
  const parts = accessToken.split(".");
  if (parts.length < 2) return null;
  try {
    const payload = JSON.parse(atob(parts[1].replace(/-/g, "+").replace(/_/g, "/")));
    if (typeof payload?.exp === "number") return payload.exp * 1000;
    return null;
  } catch {
    return null;
  }
}

function readStoredSession(): SessionTokens {
  if (typeof window === "undefined") {
    return { accessToken: null, refreshToken: null, consentsAccepted: false };
  }
  try {
    return {
      accessToken: window.localStorage.getItem(LS_ACCESS),
      refreshToken: window.localStorage.getItem(LS_REFRESH),
      consentsAccepted: window.localStorage.getItem(LS_CONSENTS) === "1",
    };
  } catch {
    return { accessToken: null, refreshToken: null, consentsAccepted: false };
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSessionState] = useState<SessionTokens>(() => readStoredSession());
  const sessionRef = useRef(session);
  sessionRef.current = session;
  const refreshTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const refreshing = useRef<Promise<boolean> | null>(null);

  const persist = useCallback((next: SessionTokens) => {
    sessionRef.current = next;
    setSessionState(next);
    try {
      if (next.accessToken) window.localStorage.setItem(LS_ACCESS, next.accessToken);
      else window.localStorage.removeItem(LS_ACCESS);
      if (next.refreshToken) window.localStorage.setItem(LS_REFRESH, next.refreshToken);
      else window.localStorage.removeItem(LS_REFRESH);
      window.localStorage.setItem(LS_CONSENTS, next.consentsAccepted ? "1" : "0");
    } catch {
      // Storage unavailable (private mode) — session stays in memory only.
    }
  }, []);

  const logout = useCallback(() => {
    if (refreshTimer.current) clearTimeout(refreshTimer.current);
    persist({ accessToken: null, refreshToken: null, consentsAccepted: false });
  }, [persist]);

  const refresh = useCallback(async (): Promise<boolean> => {
    if (refreshing.current) return refreshing.current;
    const run = (async () => {
      const { refreshToken, consentsAccepted } = sessionRef.current;
      if (!refreshToken) return false;
      let data: {
        accessToken?: string;
        access_token?: string;
        refreshToken?: string;
        refresh_token?: string;
      };
      try {
        // Routed through the sole HTTP boundary (lib/api.ts); anonymous: true
        // skips auth-attach (api.ts:77) and the 401-retry branch (api.ts:89),
        // so no infinite recursion with refreshStoredSession.
        data = await apiFetch("/auth/refresh", {
          anonymous: true,
          method: "POST",
          json: { refreshToken },
        });
      } catch (err) {
        if (err !== null && typeof err === "object" && "status" in err) {
          // Refresh rejected (rotated/expired) — drop the session.
          logout();
          refreshing.current = null;
          return false;
        }
        refreshing.current = null;
        return false;
      }
      try {
        const nextAccess = data.accessToken ?? data.access_token ?? null;
        const nextRefresh = data.refreshToken ?? data.refresh_token ?? refreshToken;
        if (!nextAccess) {
          logout();
          return false;
        }
        persist({ accessToken: nextAccess, refreshToken: nextRefresh, consentsAccepted });
        return true;
      } catch {
        return false;
      } finally {
        refreshing.current = null;
      }
    })();
    refreshing.current = run;
    return run;
  }, [logout, persist]);

  const setSession = useCallback(
    (tokens: { accessToken: string; refreshToken: string; consentsAccepted?: boolean }) => {
      persist({
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
        consentsAccepted: tokens.consentsAccepted ?? sessionRef.current.consentsAccepted,
      });
    },
    [persist],
  );

  const setConsentsAccepted = useCallback(
    (accepted: boolean) => {
      persist({ ...sessionRef.current, consentsAccepted: accepted });
    },
    [persist],
  );

  // Proactive refresh loop: schedule a refresh ahead of access-token expiry.
  useEffect(() => {
    if (refreshTimer.current) {
      clearTimeout(refreshTimer.current);
      refreshTimer.current = null;
    }
    const { accessToken, refreshToken } = session;
    if (!accessToken || !refreshToken) return;
    const expiry = getAccessExpiryMs(accessToken);
    if (expiry === null) return; // Opaque token — refresh happens lazily on 401 in lib/api.
    const delay = Math.max(expiry - Date.now() - REFRESH_SKEW_MS, 5_000);
    refreshTimer.current = setTimeout(() => {
      void refresh();
    }, delay);
    return () => {
      if (refreshTimer.current) clearTimeout(refreshTimer.current);
    };
  }, [session, refresh]);

  const value = useMemo<AuthContextValue>(
    () => ({
      ...session,
      isAuthenticated: session.accessToken !== null,
      setSession,
      setConsentsAccepted,
      refresh,
      logout,
    }),
    [session, setSession, setConsentsAccepted, refresh, logout],
  );

  return createElement(AuthContext.Provider, { value }, children);
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within <AuthProvider>");
  return ctx;
}

/**
 * Non-hook escape hatch for the sole HTTP boundary (`lib/api.ts`):
 * the provider registers current-token/refresh accessors so apiFetch
 * can attach the access token and rotate on 401 without importing
 * React context (avoids a context ↔ fetch cycle).
 */
interface AuthHooks {
  getAccessToken: () => string | null;
  refresh: () => Promise<boolean>;
}

let authHooks: AuthHooks | null = null;

export function registerAuthHooks(hooks: AuthHooks | null): void {
  authHooks = hooks;
}

export function getStoredAccessToken(): string | null {
  if (authHooks) return authHooks.getAccessToken();
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(LS_ACCESS);
  } catch {
    return null;
  }
}

export function refreshStoredSession(): Promise<boolean> {
  if (authHooks) return authHooks.refresh();
  return Promise.resolve(false);
}

/** Client component bridging AuthProvider state into the api-layer hooks. */
export function AuthHooksBridge() {
  const { refresh } = useAuth();
  const accessRef = useRef<string | null>(null);
  const auth = useAuth();
  accessRef.current = auth.accessToken;
  useEffect(() => {
    registerAuthHooks({
      getAccessToken: () => accessRef.current,
      refresh,
    });
    return () => registerAuthHooks(null);
  }, [refresh]);
  return null;
}
