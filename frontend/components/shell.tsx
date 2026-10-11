/**
 * Cabinet shell: client-side session provider, guard wrapper and
 * navigation rendered from the pure route manifest (`./nav`).
 *
 * All guards are client-side (next.config.mjs uses `output: "export"` —
 * no middleware, no dynamic server components). Guard order is
 * data-driven via `GUARD_ORDER`:
 * 1. unauthenticated → REGISTER_PATH,
 * 2. authenticated without consents → CONSENTS_PATH.
 */

"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type ReactNode } from "react";
import { AuthHooksBridge, AuthProvider, useAuth } from "@/lib/auth";
import { api } from "@/lib/api";
import {
  CABINET_HOME_PATH,
  CONSENTS_PATH,
  GUARD_ORDER,
  NAV_ROUTES,
  REGISTER_PATH,
  routeForPath,
  type GuardLevel,
} from "./nav";

function evaluateGuard(
  level: GuardLevel,
  routeGuard: GuardLevel,
  isAuthenticated: boolean,
  consentsAccepted: boolean,
): string | null {
  if (level === "auth") {
    // Any non-public route requires a session.
    if (routeGuard !== "public" && !isAuthenticated) return REGISTER_PATH;
    return null;
  }
  if (level === "cabinet") {
    // Cabinet routes additionally require the consents gate.
    if (routeGuard === "cabinet" && isAuthenticated && !consentsAccepted) {
      return CONSENTS_PATH;
    }
    return null;
  }
  return null;
}

function Guard({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { isAuthenticated, consentsAccepted } = useAuth();

  const route = pathname ? routeForPath(pathname) : undefined;
  // Unknown paths (e.g. "/" handled by page.tsx, or future screens)
  // are treated as cabinet routes: session + consents required.
  const routeGuard: GuardLevel = route?.guard ?? "cabinet";

  useEffect(() => {
    for (const level of GUARD_ORDER) {
      const target = evaluateGuard(level, routeGuard, isAuthenticated, consentsAccepted);
      if (target && pathname !== target) {
        router.replace(target);
        return;
      }
    }
  }, [pathname, routeGuard, isAuthenticated, consentsAccepted, router]);

  return <>{children}</>;
}

/**
 * Счётчик непрочитанных уведомлений для шапки.
 * Дублирует логику `app/(candidate)/notifications/useUnreadCount.ts`
 * локально: прямой импорт из группы маршрутов `(candidate)` хрупок
 * для статического экспорта, поэтому опрос GET /notifications
 * живёт здесь (фоновая ошибка игнорируется).
 */
function useUnreadBadge(enabled: boolean): { unread: number } {
  const [unread, setUnread] = useState(0);

  const load = useCallback(async (signal: AbortSignal) => {
    try {
      const page = await api.get<{ items?: { sent_at?: string | null; status?: string }[] }>(
        "/notifications?size=100&sort=created_at,desc",
        { signal },
      );
      if (signal.aborted) return;
      const items = page.items ?? [];
      setUnread(
        items.filter((n) => {
          if (n.sent_at) return false;
          const s = (n.status ?? "").toUpperCase();
          return s !== "SENT" && s !== "DELIVERED" && s !== "SUCCESS";
        }).length,
      );
    } catch {
      // Игнорируем ошибки фона: счётчик просто не обновится.
    }
  }, []);

  useEffect(() => {
    if (!enabled) {
      setUnread(0);
      return;
    }
    const controller = new AbortController();
    void load(controller.signal);
    const timer = setInterval(() => {
      void load(controller.signal);
    }, 30000);
    return () => {
      clearInterval(timer);
      controller.abort();
    };
  }, [enabled, load]);

  return { unread };
}

function CabinetNav() {
  const pathname = usePathname();
  const { isAuthenticated, logout } = useAuth();
  const { unread } = useUnreadBadge(isAuthenticated);
  if (!isAuthenticated) return null;
  return (
    <nav aria-label="Кабинет" className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-1 gap-y-1 px-4 py-2">
        <Link href={CABINET_HOME_PATH} className="mr-3 text-lg font-bold text-slate-900">
          PullHire
        </Link>
        {NAV_ROUTES.map((r) => {
          const active = pathname === r.path || pathname?.startsWith(`${r.path}/`);
          return (
            <Link
              key={r.id}
              href={r.path}
              aria-current={active ? "page" : undefined}
              className={`rounded px-2 py-1 text-sm ${
                active ? "bg-slate-900 text-white" : "text-slate-600 hover:bg-slate-100"
              }`}
            >
              {r.navLabel ?? r.title}
              {r.id === "notifications" && unread > 0 && (
                <span
                  aria-label={`Непрочитанных: ${unread}`}
                  className="ml-1 inline-block min-w-5 rounded-full bg-red-600 px-1 text-center text-xs leading-5 text-white"
                >
                  {unread > 99 ? "99+" : unread}
                </span>
              )}
            </Link>
          );
        })}
        <button
          type="button"
          onClick={logout}
          className="ml-auto rounded px-2 py-1 text-sm text-slate-500 hover:bg-slate-100"
        >
          Выйти
        </button>
      </div>
    </nav>
  );
}

function Providers({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <AuthHooksBridge />
      <Guard>
        <CabinetNav />
        {children}
      </Guard>
    </AuthProvider>
  );
}

export default Providers;
