/**
 * Consents screen — 152-ФЗ consent gate (ST-4).
 * See docs/api.md §auth (GET/PUT /auth/consents).
 *
 * Loads current consents, renders required/optional checkboxes with
 * 152-ФЗ wording (согласие на обработку и публикацию данных профиля),
 * submits updates. Required consents not accepted → inline error, no
 * navigation. On success → redirect to cabinet home (/profile).
 */

"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { CABINET_HOME_PATH } from "@/components/nav";
import type { components } from "@/lib/api/generated/auth/schema";

type ConsentsResponse = components["schemas"]["ConsentsResponse"];
type ConsentUpdateRequest = components["schemas"]["ConsentUpdateRequest"];
type ConsentItem = components["schemas"]["ConsentItem"];

interface ConsentDef {
  type: string;
  required: boolean;
  title: string;
  text: string;
}

const CONSENT_DEFS: readonly ConsentDef[] = [
  {
    type: "data_processing",
    required: true,
    title: "Согласие на обработку персональных данных (152-ФЗ, обязательно)",
    text: "Я даю согласие на обработку моих персональных данных в соответствии с Федеральным законом № 152-ФЗ «О персональных данных» для создания и ведения профиля кандидата.",
  },
  {
    type: "profile_publication",
    required: true,
    title: "Согласие на публикацию данных профиля (152-ФЗ, обязательно)",
    text: "Я даю согласие на публикацию данных моего профиля (опыт, навыки, грейд) работодателям на платформе в соответствии со ст. 9 Федерального закона № 152-ФЗ.",
  },
  {
    type: "task_participation",
    required: false,
    title: "Участие в регулярных заданиях (необязательно)",
    text: "Я соглашаюсь участвовать в регулярных заданиях платформы и на показ результата. Можно отозвать в любой момент.",
  },
];

function initialState(items: ConsentItem[] | undefined): Record<string, boolean> {
  const byType = new Map((items ?? []).map((c) => [c.consent_type ?? "", c.granted === true]));
  const state: Record<string, boolean> = {};
  for (const def of CONSENT_DEFS) {
    state[def.type] = byType.get(def.type) ?? false;
  }
  return state;
}

export default function ConsentsPage() {
  const router = useRouter();
  const { setConsentsAccepted } = useAuth();
  const [checked, setChecked] = useState<Record<string, boolean> | null>(null);
  const [versions, setVersions] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await api.get<ConsentsResponse>("/auth/consents");
        if (cancelled) return;
        setChecked(initialState(data.consents));
        const v: Record<string, string> = {};
        for (const c of data.consents ?? []) {
          if (c.consent_type && c.policy_version) v[c.consent_type] = c.policy_version;
        }
        setVersions(v);
      } catch {
        if (cancelled) return;
        setChecked(initialState(undefined));
        setError("Не удалось загрузить текущие согласия. Проверьте соединение и попробуйте ещё раз.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const toggle = (type: string) => {
    setChecked((prev) => (prev ? { ...prev, [type]: !prev[type] } : prev));
    setError(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!checked || saving) return;
    const missing = CONSENT_DEFS.filter((d) => d.required && !checked[d.type]);
    if (missing.length > 0) {
      setError("Для продолжения необходимо принять обязательные согласия по 152-ФЗ: обработку персональных данных и публикацию данных профиля.");
      return;
    }
    setSaving(true);
    setError(null);
    const body: ConsentUpdateRequest = {
      consents: CONSENT_DEFS.map((d) => ({
        consent_type: d.type,
        granted: checked[d.type] === true,
        ...(versions[d.type] ? { policy_version: versions[d.type] } : {}),
      })),
    };
    try {
      await api.put<ConsentsResponse>("/auth/consents", body);
      setConsentsAccepted(true);
      router.replace(CABINET_HOME_PATH);
    } catch {
      setError("Не удалось сохранить согласия. Попробуйте ещё раз.");
    } finally {
      setSaving(false);
    }
  };

  if (loading || checked === null) {
    return (
      <main className="mx-auto max-w-2xl p-6">
        <h1 className="text-2xl font-bold">Согласия на обработку данных</h1>
        <p className="mt-4 text-slate-600" aria-live="polite">
          Загружаем текущие согласия…
        </p>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-2xl p-6">
      <h1 className="text-2xl font-bold">Согласия на обработку данных</h1>
      <p className="mt-2 text-sm text-slate-600">
        В соответствии с Федеральным законом № 152-ФЗ «О персональных данных» для использования кабинета
        необходимо дать согласие на обработку и публикацию данных профиля.
      </p>
      <form onSubmit={handleSubmit} className="mt-6 flex flex-col gap-4">
        {CONSENT_DEFS.map((def) => (
          <label
            key={def.type}
            className="flex cursor-pointer items-start gap-3 rounded-lg border border-slate-200 p-4 hover:border-slate-300"
          >
            <input
              type="checkbox"
              className="mt-1 h-4 w-4 accent-slate-900"
              checked={checked[def.type] === true}
              onChange={() => toggle(def.type)}
              aria-label={def.title}
            />
            <span>
              <span className="font-medium">
                {def.title}
                {def.required && <span className="ml-1 text-red-600">*</span>}
              </span>
              <span className="mt-1 block text-sm text-slate-600">{def.text}</span>
            </span>
          </label>
        ))}
        {error && (
          <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">
            {error}
          </p>
        )}
        <button
          type="submit"
          disabled={saving}
          className="rounded-lg bg-slate-900 px-4 py-2 font-medium text-white disabled:opacity-50"
        >
          {saving ? "Сохраняем…" : "Принять и продолжить"}
        </button>
      </form>
    </main>
  );
}
