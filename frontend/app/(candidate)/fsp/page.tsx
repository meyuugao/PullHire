"use client";

import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { components } from "@/lib/api/generated/fsp/schema";

type LinkResponse = components["schemas"]["LinkResponse"];
type AchievementsResponse = components["schemas"]["AchievementsResponse"];

function errorMessage(err: unknown, fallback: string): string {
  if (err instanceof Error) return err.message || fallback;
  return fallback;
}

export default function FspPage() {
  const [fspId, setFspId] = useState("");
  const [link, setLink] = useState<LinkResponse | null>(null);
  const [achievements, setAchievements] = useState<AchievementsResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadingAchievements, setLoadingAchievements] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const loadAchievements = useCallback(async () => {
    setLoadingAchievements(true);
    setError(null);
    try {
      const data = await api.get<AchievementsResponse>("/candidate/fsp/achievements");
      setAchievements(data);
      if (data.fspId) {
        setLink((prev) =>
          prev
            ? { ...prev, fspId: data.fspId, linkStatus: data.linkStatus, historyStatus: data.historyStatus }
            : { fspId: data.fspId, linkStatus: data.linkStatus, historyStatus: data.historyStatus },
        );
      }
    } catch (err) {
      const apiErr = err as ApiError;
      if (apiErr && typeof apiErr.status === "number" && apiErr.status === 404) {
        setAchievements(null);
      } else {
        setError(errorMessage(err, "Не удалось загрузить данные ФСП."));
      }
    } finally {
      setLoadingAchievements(false);
    }
  }, []);

  useEffect(() => {
    void loadAchievements();
  }, [loadAchievements]);

  async function handleLink(e: React.FormEvent) {
    e.preventDefault();
    const value = fspId.trim();
    if (!value) {
      setFormError("Введите ID ФСП.");
      return;
    }
    setFormError(null);
    setLoading(true);
    try {
      const data = await api.post<LinkResponse>("/candidate/fsp/link", { fsp_id: value });
      setLink(data);
      setFspId("");
      await loadAchievements();
    } catch (err) {
      setFormError(errorMessage(err, "Не удалось привязать ID ФСП."));
    } finally {
      setLoading(false);
    }
  }

  async function handleUnlink() {
    setLoading(true);
    setFormError(null);
    try {
      const data = await api.delete<LinkResponse>("/candidate/fsp/link");
      setLink(data);
      await loadAchievements();
    } catch (err) {
      setFormError(errorMessage(err, "Не удалось отвязать ID ФСП."));
    } finally {
      setLoading(false);
    }
  }

  const items = achievements?.items ?? [];
  const linkedFspId = link?.fspId ?? achievements?.fspId ?? null;

  return (
    <main className="mx-auto max-w-2xl p-6">
      <h1 className="text-2xl font-semibold">Спортивные достижения (ФСП)</h1>
      <p className="mt-2 text-sm text-gray-600">
        Привяжите ID Федерации спортивного программирования, чтобы показать ваши достижения в профиле.
      </p>

      <section className="mt-6 rounded-lg border border-gray-200 p-4">
        <h2 className="text-lg font-medium">Привязка ФСП ID</h2>
        {linkedFspId ? (
          <div className="mt-3">
            <p className="text-sm">
              Привязан ID: <span className="font-mono font-semibold">{linkedFspId}</span>
            </p>
            {link?.linkStatus ? <p className="mt-1 text-xs text-gray-500">Статус: {link.linkStatus}</p> : null}
            <button
              type="button"
              onClick={handleUnlink}
              disabled={loading}
              className="mt-3 rounded-md border border-gray-300 px-4 py-2 text-sm hover:bg-gray-50 disabled:opacity-50"
            >
              {loading ? "Отвязываем…" : "Отвязать"}
            </button>
          </div>
        ) : (
          <form onSubmit={handleLink} className="mt-3 flex flex-col gap-3">
            <label htmlFor="fsp-id" className="text-sm font-medium">
              ID ФСП
            </label>
            <input
              id="fsp-id"
              type="text"
              value={fspId}
              onChange={(e) => setFspId(e.target.value)}
              placeholder="Например, 12345"
              className="rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
            <button
              type="submit"
              disabled={loading}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {loading ? "Привязываем…" : "Привязать"}
            </button>
          </form>
        )}
        {formError ? (
          <p role="alert" className="mt-3 text-sm text-red-600">
            {formError}
          </p>
        ) : null}
      </section>

      <section className="mt-6 rounded-lg border border-gray-200 p-4">
        <h2 className="text-lg font-medium">Достижения</h2>
        {loadingAchievements ? (
          <p className="mt-3 text-sm text-gray-500">Загружаем достижения…</p>
        ) : error ? (
          <p role="alert" className="mt-3 text-sm text-red-600">
            {error}
          </p>
        ) : items.length === 0 ? (
          <div className="mt-3 rounded-md bg-gray-50 p-4 text-center">
            <p className="text-sm font-medium text-gray-700">Нет истории</p>
            <p className="mt-1 text-xs text-gray-500">
              У вас пока нет загруженных достижений ФСП. Это ни на что не влияет — можно участвовать без них.
            </p>
          </div>
        ) : (
          <ul className="mt-3 divide-y divide-gray-100">
            {items.map((item, idx) => (
              <li key={item.externalAchievementId ?? idx} className="py-3">
                <p className="text-sm font-medium">{item.title ?? "Достижение"}</p>
                <p className="mt-1 text-xs text-gray-500">
                  {[item.eventType, item.place != null ? `место: ${item.place}` : null, item.achievedAt]
                    .filter(Boolean)
                    .join(" · ")}
                </p>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}
