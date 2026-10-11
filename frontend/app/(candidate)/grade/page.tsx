"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import type { components } from "@/lib/api/generated/assessment/schema";

type GradeChoiceResponse = components["schemas"]["GradeChoiceResponse"];
type GradeChoiceRequest = components["schemas"]["GradeChoiceRequest"];
type EligibilityResponse = components["schemas"]["EligibilityResponse"];
type HistoryResponse = components["schemas"]["HistoryResponse"];

interface GradeCard {
  code: string;
  title: string;
  description: string;
}

const BASE_GRADES: GradeCard[] = [
  { code: "Intern", title: "Intern", description: "Первые шаги в профессии, базовые знания и наставничество." },
  { code: "Junior", title: "Junior", description: "Уверенная база, простые задачи под руководством." },
  { code: "Middle", title: "Middle", description: "Самостоятельная работа, задачи средней сложности." },
  { code: "Senior", title: "Senior", description: "Сложные задачи, проектирование и менторство." },
  { code: "Lead", title: "Lead", description: "Техническое лидерство, архитектура и ответственность за результат." },
];

const SESSION_STORAGE_KEY = "assessment_session_id";

function readSessionIdFromUrl(): string {
  if (typeof window === "undefined") return "";
  try {
    const params = new URLSearchParams(window.location.search);
    return params.get("session_id") ?? params.get("sessionId") ?? "";
  } catch {
    return "";
  }
}

function formatAvailableAfter(value?: string): string | null {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("ru-RU", { dateStyle: "long", timeStyle: "short" });
}

export default function GradeChoicePage() {
  const router = useRouter();
  const [sessionId, setSessionId] = useState("");
  const [selected, setSelected] = useState("");
  const [eligibility, setEligibility] = useState<EligibilityResponse | null>(null);
  const [history, setHistory] = useState<HistoryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const fromUrl = readSessionIdFromUrl();
    const stored = typeof window !== "undefined" ? window.localStorage.getItem(SESSION_STORAGE_KEY) ?? "" : "";
    const surveyStored =
      typeof window !== "undefined" ? window.localStorage.getItem("assessment_survey_session_id") ?? "" : "";
    if (!cancelled) setSessionId(fromUrl || stored || surveyStored);
    (async () => {
      try {
        const [elig, hist] = await Promise.all([
          api.get<EligibilityResponse>("/assessment/grade-eligibility"),
          api.get<HistoryResponse>("/assessment/history"),
        ]);
        if (!cancelled) {
          setEligibility(elig);
          setHistory(hist);
        }
      } catch {
        if (!cancelled) setLoadError("Не удалось загрузить данные. Проверьте соединение и обновите страницу.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const grades: GradeCard[] = useMemo(() => {
    const seen = new Set<string>(BASE_GRADES.map((g) => g.code));
    const extra: GradeCard[] = [];
    const fromHistory: Array<string | undefined> = [
      ...(history?.grade_history ?? []).map((h) => h.grade),
      ...(history?.sessions ?? []).map((s) => s.target_grade),
    ];
    for (const code of fromHistory) {
      if (code && !seen.has(code)) {
        seen.add(code);
        extra.push({ code, title: code, description: "Грейд из вашей истории попыток." });
      }
    }
    return [...BASE_GRADES, ...extra];
  }, [history]);

  const locked = eligibility?.eligible === false;
  const availableAfter = formatAvailableAfter(eligibility?.available_after);

  const onSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      setSubmitError(null);
      const sid = sessionId.trim();
      if (!sid) {
        setSubmitError("Нет session_id опроса. Пройдите опрос — код сессии подставится автоматически.");
        return;
      }
      if (!selected) {
        setSubmitError("Выберите грейд из каталога.");
        return;
      }
      if (locked) return;
      setSubmitting(true);
      try {
        const payload: GradeChoiceRequest = { session_id: sid, declared_grade: selected };
        const res = await api.post<GradeChoiceResponse>("/assessment/grade-choice", payload);
        if (typeof window !== "undefined") {
          const effectiveId = res.session_id ?? sid;
          window.localStorage.setItem(SESSION_STORAGE_KEY, effectiveId);
        }
        router.push(`/test?sessionId=${encodeURIComponent(res.session_id ?? sid)}`);
      } catch (err) {
        const status = (err as { status?: number }).status;
        if (status === 409 || status === 423) {
          setSubmitError("Смена грейда временно недоступна (cooldown). Дождитесь окончания периода.");
        } else if (status === 400) {
          setSubmitError("Неизвестный грейд или некорректная сессия. Проверьте выбор и session_id.");
        } else {
          setSubmitError("Не удалось зафиксировать грейд. Попробуйте ещё раз.");
        }
      } finally {
        setSubmitting(false);
      }
    },
    [locked, router, selected, sessionId],
  );

  if (loading) {
    return (
      <main className="mx-auto max-w-3xl px-4 py-10">
        <h1 className="text-2xl font-semibold text-slate-900">Выбор грейда</h1>
        <p className="mt-4 text-sm text-slate-500">Загружаем каталог грейдов…</p>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="text-2xl font-semibold text-slate-900">Выбор грейда</h1>
      <p className="mt-1 text-sm text-slate-500">
        Выберите грейд, на который претендуете. После подтверждения вы перейдёте к тестированию.
      </p>

      {loadError && (
        <div role="alert" className="mt-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {loadError}
        </div>
      )}

      {locked && (
        <div
          role="alert"
          aria-label="Смена грейда заблокирована"
          className="mt-4 rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800"
        >
          <p className="font-medium">Смена грейда временно заблокирована (cooldown — 1 месяц).</p>
          <p className="mt-1">
            {availableAfter
              ? `Повторная смена будет доступна после ${availableAfter}. Каталог ниже заблокирован до окончания периода.`
              : "Дождитесь окончания периода cooldown — каталог ниже временно заблокирован."}
          </p>
        </div>
      )}

      <form onSubmit={onSubmit} className="mt-6 space-y-5">
        <div>
          <label htmlFor="session_id" className="mb-1 block text-sm font-medium text-slate-700">
            Код сессии опроса (session_id)
          </label>
          <input
            id="session_id"
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
            value={sessionId}
            onChange={(e) => setSessionId(e.target.value)}
            placeholder="Из ответа /assessment/survey (?session_id=…)"
            autoComplete="off"
          />
          <p className="mt-1 text-xs text-slate-500">Подставляется автоматически из опроса, можно вставить вручную.</p>
        </div>

        <fieldset disabled={locked} aria-disabled={locked}>
          <legend className="text-sm font-medium text-slate-700">
            Каталог грейдов {locked && <span className="text-amber-700">(заблокирован)</span>}
          </legend>
          <div className="mt-2 grid gap-3 sm:grid-cols-2">
            {grades.map((grade) => {
              const active = selected === grade.code;
              return (
                <button
                  key={grade.code}
                  type="button"
                  disabled={locked}
                  onClick={() => setSelected(grade.code)}
                  aria-pressed={active}
                  className={`rounded-lg border px-4 py-3 text-left transition ${
                    locked
                      ? "cursor-not-allowed border-slate-200 bg-slate-100 opacity-60"
                      : active
                        ? "border-slate-900 bg-slate-900 text-white shadow"
                        : "border-slate-300 bg-white text-slate-900 hover:border-slate-500"
                  }`}
                >
                  <span className="flex items-center justify-between">
                    <span className="text-base font-semibold">{grade.title}</span>
                    {locked ? (
                      <span className="text-xs font-medium uppercase tracking-wide">🔒 недоступно</span>
                    ) : (
                      active && <span className="text-xs font-medium uppercase tracking-wide">✓ выбран</span>
                    )}
                  </span>
                  <span className={`mt-1 block text-sm ${active && !locked ? "text-slate-200" : "text-slate-500"}`}>
                    {grade.description}
                  </span>
                </button>
              );
            })}
          </div>
        </fieldset>

        {submitError && (
          <div role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {submitError}
          </div>
        )}

        <button
          type="submit"
          disabled={submitting || locked || !selected || !sessionId.trim()}
          className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {submitting ? "Фиксируем грейд…" : "Подтвердить и перейти к тесту"}
        </button>
        {!locked && (
          <p className="text-xs text-slate-500">Нажимая кнопку, вы фиксируете заявленный грейд и переходите на /test.</p>
        )}
      </form>
    </main>
  );
}
