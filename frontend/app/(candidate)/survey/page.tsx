"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { FormEvent } from "react";
import { api } from "@/lib/api";
import type { components } from "@/lib/api/generated/assessment/schema";

type SurveyRequest = components["schemas"]["SurveyRequest"];
type SurveyResponse = components["schemas"]["SurveyResponse"];

const INDUSTRIES: readonly { code: string; label: string }[] = [
  { code: "it", label: "ИТ и разработка" },
  { code: "fintech", label: "Финтех" },
  { code: "ecommerce", label: "Электронная коммерция" },
  { code: "telecom", label: "Телеком" },
  { code: "other", label: "Другая отрасль" },
];

const SPECIALIZATIONS: readonly { code: string; label: string }[] = [
  { code: "backend", label: "Backend-разработка" },
  { code: "frontend", label: "Frontend-разработка" },
  { code: "qa", label: "Тестирование (QA)" },
  { code: "devops", label: "DevOps" },
  { code: "analytics", label: "Аналитика" },
];

const GRADES: readonly string[] = ["junior", "middle", "senior"];

const DIMENSIONS: readonly string[] = ["algorithms", "system_design", "tooling"];

export default function SurveyPage() {
  const router = useRouter();
  const [industryCode, setIndustryCode] = useState(INDUSTRIES[0].code);
  const [specializationCode, setSpecializationCode] = useState("");
  const [targetGrade, setTargetGrade] = useState(GRADES[1]);
  const [dimensions, setDimensions] = useState<string[]>([DIMENSIONS[0]]);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function toggleDimension(code: string) {
    setDimensions((prev) =>
      prev.includes(code) ? prev.filter((d) => d !== code) : [...prev, code],
    );
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!specializationCode) {
      setError("Выберите специализацию.");
      return;
    }
    if (dimensions.length === 0) {
      setError("Выберите хотя бы одно направление оценки.");
      return;
    }
    setPending(true);
    try {
      const body: SurveyRequest = {
        industry_code: industryCode || undefined,
        specialization_code: specializationCode,
        target_grade: targetGrade,
        declared_dimensions: dimensions,
        survey_answers: {},
      };
      const res = await api.post<SurveyResponse>("/assessment/survey", body);
      if (res.session_id && typeof window !== "undefined") {
        window.localStorage.setItem("assessment_survey_session_id", res.session_id);
      }
      router.push("/grade");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Не удалось отправить анкету");
    } finally {
      setPending(false);
    }
  }

  return (
    <main className="mx-auto flex max-w-md flex-col gap-4 p-10">
      <h1 className="text-2xl font-bold">Анкета</h1>
      <p className="text-sm text-slate-600">
        Укажите отрасль и специализацию, чтобы подобрать оценку.
      </p>
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <label className="flex flex-col gap-1 text-sm">
          Отрасль
          <select
            value={industryCode}
            onChange={(e) => setIndustryCode(e.target.value)}
            className="rounded border border-slate-300 px-3 py-2"
          >
            {INDUSTRIES.map((i) => (
              <option key={i.code} value={i.code}>
                {i.label}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Специализация
          <select
            required
            value={specializationCode}
            onChange={(e) => setSpecializationCode(e.target.value)}
            className="rounded border border-slate-300 px-3 py-2"
          >
            <option value="">Выберите специализацию</option>
            {SPECIALIZATIONS.map((s) => (
              <option key={s.code} value={s.code}>
                {s.label}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Целевой грейд
          <select
            value={targetGrade}
            onChange={(e) => setTargetGrade(e.target.value)}
            className="rounded border border-slate-300 px-3 py-2"
          >
            {GRADES.map((g) => (
              <option key={g} value={g}>
                {g}
              </option>
            ))}
          </select>
        </label>
        <fieldset className="flex flex-col gap-1 text-sm">
          <legend>Направления оценки</legend>
          {DIMENSIONS.map((d) => (
            <label key={d} className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={dimensions.includes(d)}
                onChange={() => toggleDimension(d)}
              />
              {d}
            </label>
          ))}
        </fieldset>
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
          {pending ? "Отправляем…" : "Продолжить"}
        </button>
      </form>
    </main>
  );
}
