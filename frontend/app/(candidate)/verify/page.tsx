"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { Suspense, useState } from "react";
import type { FormEvent } from "react";
import { useSearchParams } from "next/navigation";
import { api } from "@/lib/api";
import type { components } from "@/lib/api/generated/auth/schema";

type VerifyEmailRequest = components["schemas"]["VerifyEmailRequest"];
type MessageResponse = components["schemas"]["MessageResponse"];

function VerifyForm() {
  const router = useRouter();
  const params = useSearchParams();
  const [token, setToken] = useState(params.get("token") ?? "");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const sent = params.get("sent") === "1";

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setPending(true);
    try {
      const body: VerifyEmailRequest = { token };
      await api.post<MessageResponse>("/auth/verify-email", body, { anonymous: true });
      router.push("/login");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Ошибка подтверждения");
    } finally {
      setPending(false);
    }
  }

  return (
    <main className="mx-auto flex max-w-md flex-col gap-4 p-10">
      <h1 className="text-2xl font-bold">Подтверждение email</h1>
      {sent && (
        <p className="text-sm text-slate-600" role="status">
          Проверьте почту: мы отправили письмо со ссылкой.
        </p>
      )}
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <label className="flex flex-col gap-1 text-sm">
          Токен
          <input
            type="text"
            required
            autoComplete="one-time-code"
            value={token}
            onChange={(e) => setToken(e.target.value)}
            className="rounded border border-slate-300 px-3 py-2"
          />
        </label>
        {error && (
          <p className="text-sm text-red-600" role="alert">
            {error}
          </p>
        )}
        <button
          type="submit"
          disabled={pending}
          className="rounded bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {pending ? "Проверяем…" : "Подтвердить"}
        </button>
      </form>
    </main>
  );
}

export default function VerifyPage() {
  return (
    <Suspense>
      <VerifyForm />
    </Suspense>
  );
}
