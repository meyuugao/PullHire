"use client";

import { Suspense, useCallback, useState } from "react";
import { useSearchParams } from "next/navigation";
import { api } from "@/lib/api";
import type { components } from "@/lib/api/generated/assessment/schema";

type SessionStartResponse = components["schemas"]["SessionStartResponse"];
type AnswerResponse = components["schemas"]["AnswerResponse"];
type ServedItem = components["schemas"]["ServedItem"];
type JsonNode = components["schemas"]["JsonNode"];

function toAnswerNode(text: string): JsonNode {
  return text as unknown as JsonNode;
}

function describeItem(item: ServedItem): string {
  const kind = item.item_kind ?? "вопрос";
  const id = item.item_id ?? "без id";
  return `${kind} · ${id}`;
}

function ContentView({ item }: { item: ServedItem }) {
  const content = item.content as unknown;
  let text: string | null = null;
  if (typeof content === "string") {
    text = content;
  } else if (content !== null && typeof content === "object") {
    const rec = content as Record<string, unknown>;
    for (const key of ["text", "prompt", "question", "stem", "body"]) {
      if (typeof rec[key] === "string" && (rec[key] as string).trim() !== "") {
        text = rec[key] as string;
        break;
      }
    }
  }
  return (
    <div className="space-y-3">
      <p className="text-xs text-slate-500">{describeItem(item)}</p>
      {text !== null ? (
        <p className="whitespace-pre-wrap text-base leading-relaxed text-slate-900">{text}</p>
      ) : (
        <pre className="overflow-auto rounded-md bg-slate-950 p-3 text-xs text-slate-100">
          {JSON.stringify(content ?? null, null, 2)}
        </pre>
      )}
      {item.option_order !== undefined && item.option_order !== null && item.option_order.length > 0 && (
        <p className="text-xs text-slate-500">Варианты: {item.option_order.join(", ")}</p>
      )}
    </div>
  );
}

function TestRunner() {
  const searchParams = useSearchParams();
  const paramId = searchParams.get("sessionId") ?? searchParams.get("id") ?? "";
  const storedId =
    typeof window !== "undefined" ? (window.localStorage.getItem("assessment_session_id") ?? "") : "";
  const initialId = paramId || storedId;
  const [sessionId, setSessionId] = useState(initialId);
  const [started, setStarted] = useState(false);
  const [starting, setStarting] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [item, setItem] = useState<ServedItem | null>(null);
  const [answerText, setAnswerText] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [theta, setTheta] = useState<number | null>(null);
  const [thetaSe, setThetaSe] = useState<number | null>(null);
  const [itemCount, setItemCount] = useState<number | null>(null);
  const [result, setResult] = useState<AnswerResponse | null>(null);
  const [answered, setAnswered] = useState(0);

  const errMsg = useCallback((fallback: string, err: unknown) => {
    const statusCode = (err as { status?: number }).status;
    if (statusCode === 404) return "Сессия не найдена. Проверьте идентификатор.";
    if (statusCode === 409) return "Сессия уже завершена или недоступна для старта.";
    if (statusCode === 422) return "Ответ отклонён: проверьте заполнение поля.";
    return fallback;
  }, []);

  const onStart = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      const id = sessionId.trim();
      if (!id) {
        setError("Укажите идентификатор сессии.");
        return;
      }
      setStarting(true);
      setError(null);
      setResult(null);
      try {
        const res = await api.post<SessionStartResponse>(
          `/assessment/sessions/${encodeURIComponent(id)}/start`,
        );
        setStarted(true);
        setStatus(res.status ?? null);
        setItem(res.item ?? null);
        setAnswerText("");
        setAnswered(0);
        setTheta(null);
        setThetaSe(null);
        setItemCount(null);
        if (!res.item) {
          setError("Сессия стартовала, но вопрос не получен. Попробуйте отправить ответ заново.");
        }
      } catch (err) {
        setError(errMsg("Не удалось начать сессию. Попробуйте позже.", err));
      } finally {
        setStarting(false);
      }
    },
    [sessionId, errMsg],
  );

  const onSubmitAnswer = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      const id = sessionId.trim();
      if (!item?.item_id) {
        setError("Нет активного вопроса для ответа.");
        return;
      }
      if (answerText.trim() === "") {
        setError("Введите ответ перед отправкой.");
        return;
      }
      setSubmitting(true);
      setError(null);
      try {
        const res = await api.post<AnswerResponse>(
          `/assessment/sessions/${encodeURIComponent(id)}/answers`,
          {
            item_id: item.item_id,
            answer: toAnswerNode(answerText),
          },
        );
        setStatus(res.status ?? null);
        setTheta(res.theta ?? null);
        setThetaSe(res.theta_se ?? null);
        setItemCount(res.item_count ?? null);
        setAnswered((n) => n + 1);
        setAnswerText("");
        if (res.completed) {
          setResult(res);
          setItem(res.item ?? null);
        } else {
          setItem(res.item ?? null);
          if (!res.item) {
            setError("Ответ принят, но следующий вопрос не получен. Попробуйте начать сессию заново.");
          }
        }
      } catch (err) {
        setError(errMsg("Не удалось отправить ответ. Попробуйте снова.", err));
      } finally {
        setSubmitting(false);
      }
    },
    [sessionId, item, answerText, errMsg],
  );

  const onReset = useCallback(() => {
    setStarted(false);
    setItem(null);
    setResult(null);
    setStatus(null);
    setTheta(null);
    setThetaSe(null);
    setItemCount(null);
    setAnswered(0);
    setAnswerText("");
    setError(null);
  }, []);

  const finished = result !== null && result.completed;

  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      <h1 className="text-2xl font-semibold">Прохождение теста</h1>
      <p className="mt-1 text-sm text-slate-500">
        Начните сессию, отвечайте на вопросы по одному и дождитесь итоговой оценки.
      </p>

      {!started || finished ? null : (
        <div className="mt-4 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500">
          {status && <span>Статус: {status}</span>}
          {answered > 0 && <span>Отвечено: {answered}</span>}
          {itemCount !== null && itemCount !== undefined && <span>Вопросов: {itemCount}</span>}
        </div>
      )}

      {error && (
        <div role="alert" className="mt-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}

      {!started && (
        <form onSubmit={onStart} className="mt-6 space-y-4">
          <div>
            <label htmlFor="session-id" className="mb-1 block text-sm font-medium text-slate-700">
              Идентификатор сессии
            </label>
            <input
              id="session-id"
              className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
              value={sessionId}
              onChange={(e) => setSessionId(e.target.value)}
              placeholder="Например, 123"
              inputMode="numeric"
            />
          </div>
          <button
            type="submit"
            disabled={starting}
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
          >
            {starting ? "Начинаем…" : "Начать сессию"}
          </button>
        </form>
      )}

      {started && !finished && item && (
        <section className="mt-6 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <ContentView item={item} />
          <form onSubmit={onSubmitAnswer} className="mt-4 space-y-3">
            <div>
              <label htmlFor="answer" className="mb-1 block text-sm font-medium text-slate-700">
                Ваш ответ
              </label>
              <textarea
                id="answer"
                rows={4}
                className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                value={answerText}
                onChange={(e) => setAnswerText(e.target.value)}
                placeholder="Введите ответ…"
              />
            </div>
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={submitting}
                className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
              >
                {submitting ? "Отправка…" : "Отправить ответ"}
              </button>
              <button
                type="button"
                onClick={onReset}
                className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                Заново
              </button>
            </div>
          </form>
        </section>
      )}

      {started && !finished && !item && !submitting && (
        <div className="mt-6">
          <button
            type="button"
            onClick={onReset}
            className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Начать заново
          </button>
        </div>
      )}

      {finished && result && (
        <section className="mt-6 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <h2 className="text-lg font-semibold">Результат</h2>
          {result.grade !== undefined && result.grade !== null && result.grade !== "" ? (
            <div className="mt-3 space-y-1">
              <p className="text-sm text-slate-700">
                Грейд: <span className="font-semibold text-slate-900">{result.grade}</span>
              </p>
              {result.category_code !== undefined && result.category_code !== null && result.category_code !== "" && (
                <p className="text-sm text-slate-700">
                  Категория: <span className="font-semibold text-slate-900">{result.category_code}</span>
                </p>
              )}
            </div>
          ) : (
            <p className="mt-3 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
              Результат требует перепроверки — итоговая оценка пока недоступна.
            </p>
          )}
          <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500">
            {result.status && <span>Статус: {result.status}</span>}
            {result.item_count !== undefined && result.item_count !== null && (
              <span>Вопросов: {result.item_count}</span>
            )}
          </div>
          <button
            type="button"
            onClick={onReset}
            className="mt-4 rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Пройти ещё раз
          </button>
        </section>
      )}
    </main>
  );
}

export default function CandidateTestPage() {
  return (
    <Suspense
      fallback={
        <main className="mx-auto max-w-2xl px-4 py-10">
          <h1 className="text-2xl font-semibold">Прохождение теста</h1>
          <p className="mt-4 text-sm text-slate-500">Загрузка…</p>
        </main>
      }
    >
      <TestRunner />
    </Suspense>
  );
}
