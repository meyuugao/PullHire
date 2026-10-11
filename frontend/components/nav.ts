/**
 * Pure route manifest — no side effects, no imports with side effects.
 *
 * Single source of truth for all SPA routes. The cabinet shell
 * (`components/shell.tsx`, wired in `app/layout.tsx`) renders navigation
 * purely from this manifest, and the client-side guard evaluates
 * `guard` metadata in `GUARD_ORDER` sequence.
 *
 * All 13 entries below map to real pages under `app/(candidate)/`
 * (route group — URLs stay flat, e.g. `/register`): register, verify,
 * login, consents, profile, privacy, fsp, survey, grade, test, card,
 * invitations, notifications.
 */

export type RouteId =
  | "register"
  | "verify"
  | "login"
  | "consents"
  | "profile"
  | "privacy"
  | "fsp"
  | "survey"
  | "grade"
  | "test"
  | "card"
  | "invitations"
  | "notifications";

/**
 * Data-driven guard level. Evaluated in `GUARD_ORDER` sequence so a later
 * integration task (ST-16) can reorder/extend guarding by editing data
 * here instead of rewriting guard components.
 * - `public`: reachable without session (landing/onboarding steps).
 * - `auth`: requires an authenticated session.
 * - `cabinet`: requires session AND accepted consents (152-ФЗ gate passed).
 */
export type GuardLevel = "public" | "auth" | "cabinet";

export interface RouteMeta {
  id: RouteId;
  /** Absolute app path, e.g. "/register". */
  path: string;
  /** Human-readable title (ru). */
  title: string;
  guard: GuardLevel;
  /** Show in cabinet navigation. */
  showInNav: boolean;
  /** Short nav label; defaults to title when omitted. */
  navLabel?: string;
}

export const ROUTES: readonly RouteMeta[] = [
  { id: "register", path: "/register", title: "Регистрация", guard: "public", showInNav: false },
  { id: "verify", path: "/verify", title: "Подтверждение email", guard: "public", showInNav: false },
  { id: "login", path: "/login", title: "Вход", guard: "public", showInNav: false },
  { id: "consents", path: "/consents", title: "Согласия", guard: "auth", showInNav: false },
  { id: "profile", path: "/profile", title: "Профиль", guard: "cabinet", showInNav: true, navLabel: "Профиль" },
  { id: "privacy", path: "/privacy", title: "Приватность", guard: "cabinet", showInNav: true, navLabel: "Приватность" },
  { id: "fsp", path: "/fsp", title: "ФСП", guard: "cabinet", showInNav: true, navLabel: "ФСП" },
  { id: "survey", path: "/survey", title: "Опрос", guard: "cabinet", showInNav: true, navLabel: "Опрос" },
  { id: "grade", path: "/grade", title: "Грейд", guard: "cabinet", showInNav: true, navLabel: "Грейд" },
  { id: "test", path: "/test", title: "Тест", guard: "cabinet", showInNav: true, navLabel: "Тест" },
  { id: "card", path: "/card", title: "Карточка", guard: "cabinet", showInNav: true, navLabel: "Карточка" },
  { id: "invitations", path: "/invitations", title: "Приглашения", guard: "cabinet", showInNav: true, navLabel: "Приглашения" },
  { id: "notifications", path: "/notifications", title: "Уведомления", guard: "cabinet", showInNav: true, navLabel: "Уведомления" },
];

function objectFromRoutes(routes: readonly RouteMeta[]): Record<string, RouteMeta> {
  const map: Record<string, RouteMeta> = {};
  for (const r of routes) map[r.path] = r;
  return map;
}

// Local helper kept free of side effects (avoids Object.fromEntries lib-target issues).
function fromRoutes(): Readonly<Record<string, RouteMeta>> {
  return objectFromRoutes(ROUTES);
}

// Rebuilt explicitly to keep module evaluation pure and obvious.
export const ROUTE_BY_PATH: Readonly<Record<string, RouteMeta>> = fromRoutes();

export const NAV_ROUTES: readonly RouteMeta[] = ROUTES.filter((r) => r.showInNav);

/**
 * Guard evaluation order (data-driven). The shell guard checks these
 * levels in sequence:
 * 1. `auth` — unauthenticated → redirect to REGISTER_PATH.
 * 2. `cabinet` — authenticated but consents not accepted → CONSENTS_PATH.
 * Reorder/extend here without touching guard components.
 */
export const GUARD_ORDER: readonly GuardLevel[] = ["auth", "cabinet"] as const;

export const REGISTER_PATH = "/register";
export const CONSENTS_PATH = "/consents";
/** Cabinet home for authenticated users with consents accepted. */
export const CABINET_HOME_PATH = "/profile";

/** Resolve metadata by pathname; unknown paths yield `undefined` (treated as cabinet). */
export function routeForPath(pathname: string): RouteMeta | undefined {
  const clean = pathname.split("?")[0].split("#")[0] || "/";
  return ROUTE_BY_PATH[clean];
}
