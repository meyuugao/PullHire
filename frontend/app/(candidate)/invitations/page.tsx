"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { components } from "@/lib/api/generated/outreach/schema";

type InvitationResponse = components["schemas"]["InvitationResponse"];
type PageResponse = components["schemas"]["PageResponseInvitationResponse"];

const STATUS_LABELS: Record<string, string> = {
  sent: "Новое",
  viewed: "Просмотрено",
  accepted: "Принято",
  declined: "Отклонено",
  expired: "Истекло",
};

function statusLabel(status?: string): string {
  if (!status) return "—";
  return STATUS_LABELS[status] ?? status;
}

function formatSalary(inv: InvitationResponse): string {
  const from = inv.salary_from;
  const to = inv.salary_to;
  if (from === undefined && to === undefined) return "Зарплата не указана";
  const fmt = (n: number) => n.toLocaleString("ru-RU");
  const basis = inv.salary_basis ? ` (${inv.salary_basis})` : "";
  if (from !== undefined && to !== undefined) return `от ${fmt(from)} до ${fmt(to)}${basis}`;
  if (from !== undefined) return `от ${fmt(from)}${basis}`;
  return `до ${fmt(to as number)}${basis}`;
}

function formatDate(value?: string): string {
  if (!value) return "—";
  try {
    return new Date(value).toLocaleString("ru-RU");
  } catch {
    return value;
  }
}

export default function InvitationsPage() {
  const [items, setItems] = useState<InvitationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [openedId, setOpenedId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const page = await api.get<PageResponse>("/invitations");
        if (!cancelled) setItems(page.items ?? []);
      } catch {
        if (!cancelled) setError("Не удалось загрузить приглашения. Попробуйте обновить страницу.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const updateItem = useCallback((next: InvitationResponse) => {
    setItems((prev) => prev.map((it) => (it.id === next.id ? next : it)));
  }, []);

  const openInvitation = useCallback(
    async (inv: InvitationResponse) => {
      if (!inv.id) return;
      const id = inv.id;
      setOpenedId((prev) => (prev === id ? null : id));
      if (inv.status === "sent") {
        try {
          const updated = await api.post<InvitationResponse>(`/invitations/${id}/mark-viewed`);
          updateItem(updated);
        } catch {
          // Тихо игнорируем: просмотр не должен ломать раскрытие.
        }
      }
    },
    [updateItem],
  );

  const accept = useCallback(
    async (inv: InvitationResponse) => {
      if (!inv.id) return;
      setPendingId(inv.id);
      setActionError(null);
      try {
        const updated = await api.post<InvitationResponse>(`/invitations/${inv.id}/accept`);
        updateItem(updated);
      } catch {
        setActionError("Не удалось принять приглашение. Попробуйте снова.");
      } finally {
        setPendingId(null);
      }
    },
    [updateItem],
  );

  const decline = useCallback(
    async (inv: InvitationResponse) => {
      if (!inv.id) return;
      setPendingId(inv.id);
      setActionError(null);
      try {
        const updated = await api.post<InvitationResponse>(`/invitations/${inv.id}/decline`);
        updateItem(updated);
      } catch {
        setActionError("Не удалось отклонить приглашение. Попробуйте снова.");
      } finally {
        setPendingId(null);
      }
    },
    [updateItem],
  );

  if (loading) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-10">
        <h1 className="text-2xl font-semibold">Приглашения</h1>
        <p className="mt-4 text-sm text-slate-500">Загрузка приглашений…</p>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      <h1 className="text-2xl font-semibold">Приглашения</h1>
      <p className="mt-1 text-sm text-slate-500">Приглашения от работодателей: вакансия и уровень заработной платы.</p>

      {error && (
        <div role="alert" className="mt-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}
      {actionError && (
        <div role="alert" className="mt-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {actionError}
        </div>
      )}

      {items.length === 0 && !error ? (
        <p className="mt-6 text-sm text-slate-500">Пока нет приглашений.</p>
      ) : (
        <ul className="mt-6 space-y-4">
          {items.map((inv) => {
            const id = inv.id ?? "";
            const isOpen = openedId === id;
            const busy = pendingId === id;
            const actionable = inv.status === "sent" || inv.status === "viewed";
            return (
              <li key={id || inv.vacancy_id} className="rounded-md border border-slate-200 bg-white p-4 shadow-sm">
                <button
                  type="button"
                  onClick={() => void openInvitation(inv)}
                  className="w-full text-left"
                  aria-expanded={isOpen}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="text-sm font-medium text-slate-900">{inv.company_name || "Компания не указана"}</p>
                      <p className="mt-1 text-sm text-slate-600">Вакансия: {inv.vacancy_id || "—"}</p>
                      <p className="mt-1 text-sm text-slate-600">{formatSalary(inv)}</p>
                    </div>
                    <span className="shrink-0 rounded-full bg-slate-100 px-2 py-1 text-xs font-medium text-slate-700">
                      {statusLabel(inv.status)}
                    </span>
                  </div>
                </button>

                {isOpen && (
                  <div className="mt-3 border-t border-slate-100 pt-3">
                    {inv.message && <p className="text-sm text-slate-700">{inv.message}</p>}
                    <p className="mt-2 text-xs text-slate-500">Получено: {formatDate(inv.created_at)}</p>
                    {actionable && (
                      <div className="mt-3 flex flex-wrap gap-2">
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => void accept(inv)}
                          className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
                        >
                          {busy ? "Обработка…" : "Принять"}
                        </button>
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => void decline(inv)}
                          className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                        >
                          {busy ? "Обработка…" : "Отклонить"}
                        </button>
                      </div>
                    )}
                    {inv.status === "accepted" && id && (
                      <Link
                        href={`/invitations/contacts?invitation=${id}`}
                        className="mt-3 inline-block rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50"
                      >
                        Открыть контакты
                      </Link>
                    )}
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </main>
  );
}
