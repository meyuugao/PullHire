"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { components } from "@/lib/api/generated/notification/schema";

type PageResponse = components["schemas"]["PageResponseNotificationLogItem"];
type Item = components["schemas"]["NotificationLogItem"];

const POLL_INTERVAL_MS = 30000;

function formatDate(value?: string): string {
  if (!value) return "—";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString("ru-RU");
}

export default function NotificationsPage() {
  const [items, setItems] = useState<Item[]>([]);
  const [total, setTotal] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [updatedAt, setUpdatedAt] = useState<string | null>(null);

  const load = useCallback(async (signal: AbortSignal) => {
    try {
      const page = await api.get<PageResponse>("/notifications?page=0&size=20&sort=created_at,desc", {
        signal,
      });
      if (signal.aborted) return;
      setItems(page.items ?? []);
      setTotal(page.total ?? null);
      setError(null);
      setUpdatedAt(new Date().toLocaleString("ru-RU"));
    } catch (err) {
      if (signal.aborted) return;
      const aborted =
        (err as { name?: string }).name === "AbortError" ||
        (err as { status?: number }).status === undefined;
      if (!aborted || (err as { status?: number }).status !== undefined) {
        setError("Не удалось загрузить уведомления. Попробуйте обновить страницу.");
      }
    } finally {
      if (!signal.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    const timer = setInterval(() => {
      void load(controller.signal);
    }, POLL_INTERVAL_MS);
    return () => {
      clearInterval(timer);
      controller.abort();
    };
  }, [load]);

  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      <div className="flex items-baseline justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Уведомления</h1>
          <p className="mt-1 text-sm text-slate-500">
            {total !== null ? `Всего: ${total}. ` : ""}Обновление автоматически каждые 30 секунд.
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            const controller = new AbortController();
            void load(controller.signal);
          }}
          className="shrink-0 rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50"
        >
          Обновить
        </button>
      </div>

      {updatedAt && !loading && (
        <p className="mt-2 text-xs text-slate-400">Обновлено: {updatedAt}</p>
      )}

      {loading && <p className="mt-6 text-sm text-slate-500">Загрузка уведомлений…</p>}

      {error && (
        <div role="alert" className="mt-6 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}

      {!loading && !error && items.length === 0 && (
        <p className="mt-6 rounded-md border border-slate-200 bg-slate-50 px-3 py-4 text-sm text-slate-500">
          Уведомлений пока нет.
        </p>
      )}

      {!loading && !error && items.length > 0 && (
        <ul className="mt-6 space-y-3">
          {items.map((n, i) => (
            <li
              key={n.id ?? `${n.created_at ?? "n"}-${i}`}
              className="rounded-md border border-slate-200 bg-white px-4 py-3 shadow-sm"
            >
              <div className="flex items-start justify-between gap-3">
                <p className="text-sm font-medium text-slate-900">
                  {n.template_code ?? n.event_type ?? "Уведомление"}
                </p>
                {n.status && (
                  <span className="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                    {n.status}
                  </span>
                )}
              </div>
              {n.event_type && n.template_code && (
                <p className="mt-1 text-xs text-slate-500">Событие: {n.event_type}</p>
              )}
              {n.error && <p className="mt-1 text-xs text-red-600">Ошибка: {n.error}</p>}
              <p className="mt-2 text-xs text-slate-400">
                Создано: {formatDate(n.created_at)}
                {n.sent_at ? ` · Отправлено: ${formatDate(n.sent_at)}` : " · Ещё не отправлено"}
              </p>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
