/**
 * Landing → redirect (client-side only; static export).
 * See docs/user_flow.md §2.1: unauthenticated visitors start at
 * registration; authenticated users land in the cabinet.
 * The consents gate itself is enforced by the shell guard.
 */

"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useAuth } from "@/lib/auth";
import { CABINET_HOME_PATH, REGISTER_PATH } from "@/components/nav";

export default function Home() {
  const router = useRouter();
  const { isAuthenticated } = useAuth();

  useEffect(() => {
    router.replace(isAuthenticated ? CABINET_HOME_PATH : REGISTER_PATH);
  }, [isAuthenticated, router]);

  return (
    <main className="mx-auto flex max-w-2xl flex-col items-center gap-4 p-10 text-center">
      <h1 className="text-3xl font-bold">PullHire</h1>
      <p className="text-slate-600" aria-live="polite">
        Перенаправляем…
      </p>
    </main>
  );
}
