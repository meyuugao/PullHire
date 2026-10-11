"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { components as AssessmentComponents } from "@/lib/api/generated/assessment/schema";
import type { components as FspComponents } from "@/lib/api/generated/fsp/schema";

type HistoryResponse = AssessmentComponents["schemas"]["HistoryResponse"];
type GradeHistoryItem = AssessmentComponents["schemas"]["GradeHistoryItem"];
type AchievementsResponse = FspComponents["schemas"]["AchievementsResponse"];

type Status = "loading" | "error" | "unassigned" | "assigned";

function latestAssigned(items?: GradeHistoryItem[] | null): GradeHistoryItem | null {
  if (!items || items.length === 0) return null;
  const assigned = items.filter(
    (i) => i.category_code !== undefined && i.category_code !== null && String(i.category_code).trim() !== "",
  );
  if (assigned.length === 0) return null;
  const sorted = [...assigned].sort((a, b) => String(b.created_at ?? "").localeCompare(String(a.created_at ?? "")));
  return sorted[0] ?? null;
}

export default function CandidateCardPage() {
  const [status, setStatus] = useState<Status>("loading");
  const [entry, setEntry] = useState<GradeHistoryItem | null>(null);
  const [achievements, setAchievements] = useState<AchievementsResponse | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const history = await api.get<HistoryResponse>("/assessment/history");
        const found = latestAssigned(history.grade_history);
        if (!found) {
          if (!cancelled) {
            setEntry(null);
            setStatus("unassigned");
          }
          return;
        }
        if (!cancelled) {
          setEntry(found);
          setStatus("assigned");
        }
        // ФСП-достижения — опционально: недоступность не блокирует карточку.
        try {
          const fsp = await api.get<AchievementsResponse>("/candidate/fsp/achievements");
          if (!cancelled) setAchievements(fsp);
        } catch {
          if (!cancelled) setAchievements(null);
        }
      } catch {
        if (!cancelled) setStatus("error");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  if (status === "loading") {
    return (
      <main className="mx-auto max-w-2xl px-4 py-10">
        <h1 className="text-2xl font-semibold">Моя карточка</h1>
        <p className="mt-4 text-sm text-slate-500">Загрузка карточки…</p>
      </main>
    );
  }

  if (status === "error") {
    return (
      <main className="mx-auto max-w-2xl px-4 py-10">
        <h1 className="text-2xl font-semibold">Моя карточка</h1>
        <div role="alert" className="mt-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          Не удалось загрузить карточку. Попробуйте обновить страницу.
        </div>
      </main>
    );
  }

  // CRITICAL INVARIANT: карточка появляется строго после category_assigned.
  // До присвоения категории содержимое карточки скрыто.
  if (status === "unassigned" || !entry) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-10">
        <h1 className="text-2xl font-semibold">Моя карточка</h1>
        <div className="mt-6 rounded-lg border border-slate-200 bg-slate-50 px-4 py-6 text-center">
          <p className="text-lg font-medium text-slate-700">Категория: не подтверждён</p>
          <p className="mt-2 text-sm text-slate-500">Категория ещё не присвоена. Пройдите оценку, чтобы получить карточку.</p>
        </div>
      </main>
    );
  }

  const items = achievements?.items ?? [];

  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      <h1 className="text-2xl font-semibold">Моя карточка</h1>
      <section aria-label="Карточка категории" className="mt-6 rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <dl className="space-y-3 text-sm">
          <div className="flex gap-2">
            <dt className="w-40 shrink-0 font-medium text-slate-500">Категория</dt>
            <dd className="text-slate-900">{entry.category_code}</dd>
          </div>
          {entry.specialization_code && (
            <div className="flex gap-2">
              <dt className="w-40 shrink-0 font-medium text-slate-500">Специализация</dt>
              <dd className="text-slate-900">{entry.specialization_code}</dd>
            </div>
          )}
          {entry.grade && (
            <div className="flex gap-2">
              <dt className="w-40 shrink-0 font-medium text-slate-500">Грейд</dt>
              <dd className="text-slate-900">{entry.grade}</dd>
            </div>
          )}
          {entry.created_at && (
            <div className="flex gap-2">
              <dt className="w-40 shrink-0 font-medium text-slate-500">Дата присвоения</dt>
              <dd className="text-slate-900">{new Date(entry.created_at).toLocaleDateString("ru-RU")}</dd>
            </div>
          )}
        </dl>
      </section>

      <section aria-label="Достижения ФСП" className="mt-6">
        <h2 className="text-lg font-semibold">Достижения ФСП</h2>
        {items.length === 0 ? (
          <p className="mt-2 text-sm text-slate-500">Достижения ФСП отсутствуют.</p>
        ) : (
          <ul className="mt-3 space-y-2">
            {items.map((a, idx) => (
              <li key={a.externalAchievementId ?? `${a.title ?? "achievement"}-${idx}`} className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm">
                <p className="font-medium text-slate-900">{a.title ?? "Достижение"}</p>
                <p className="mt-0.5 text-xs text-slate-500">
                  {[a.eventType, a.place !== undefined && a.place !== null ? `место: ${a.place}` : null]
                    .filter(Boolean)
                    .join(" · ")}
                  {a.achievedAt ? ` · ${new Date(a.achievedAt).toLocaleDateString("ru-RU")}` : ""}
                </p>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}
