"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import type { FormEvent } from "react";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { components } from "@/lib/api/generated/auth/schema";

type LoginRequest = components["schemas"]["LoginRequest"];
type TokenResponse = components["schemas"]["TokenResponse"];

export default function LoginPage() {
  const router = useRouter();
  const { setSession } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setPending(true);
    try {
      const body: LoginRequest = { email, password };
      const data = await api.post<TokenResponse & { accessToken?: string; refreshToken?: string }>(
        "/auth/login",
        body,
        { anonymous: true },
      );
      const accessToken = data.accessToken ?? data.access_token ?? "";
      const refreshToken = data.refreshToken ?? data.refresh_token ?? "";
      if (!accessToken || !refreshToken) throw new Error("Нет токенов в ответе");
      setSession({ accessToken, refreshToken });
      router.push("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Ошибка входа");
    } finally {
      setPending(false);
    }
  }

  return (
    <main className="mx-auto flex max-w-md flex-col gap-4 p-10">
      <h1 className="text-2xl font-bold">Вход</h1>
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <label className="flex flex-col gap-1 text-sm">
          Email
          <input
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="rounded border border-slate-300 px-3 py-2"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Пароль
          <input
            type="password"
            required
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
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
          {pending ? "Входим…" : "Войти"}
        </button>
      </form>
      <p className="text-sm text-slate-600">
        Нет аккаунта?{" "}
        <Link href="/register" className="underline">
          Регистрация
        </Link>
      </p>
    </main>
  );
}
