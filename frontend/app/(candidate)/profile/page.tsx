"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { components } from "@/lib/api/generated/candidate/schema";

type ProfileRequest = components["schemas"]["ProfileRequest"];
type ProfileResponse = components["schemas"]["ProfileResponse"];

const inputClass =
  "w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500";
const labelClass = "mb-1 block text-sm font-medium text-slate-700";

function toList(value: string): string[] {
  return value
    .split(",")
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

function fromList(value?: string[]): string {
  return (value ?? []).join(", ");
}

function toForm(p: ProfileResponse | null): Record<string, string> {
  return {
    full_name: p?.fullName ?? "",
    contact_email: p?.contactEmail ?? "",
    contact_phone: p?.contactPhone ?? "",
    position_title: p?.positionTitle ?? "",
    grade_declared: p?.gradeDeclared ?? "",
    experience_years: p?.experienceYears !== undefined && p.experienceYears !== null ? String(p.experienceYears) : "",
    stack: fromList(p?.stack),
    skills: fromList(p?.skills),
    soft_skills: fromList(p?.softSkills),
    summary: p?.summary ?? "",
    specialization_code: p?.specializationCode ?? "",
    industry_code: p?.industryCode ?? "",
  };
}

export default function CandidateProfilePage() {
  const [form, setForm] = useState<Record<string, string>>(toForm(null));
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [savedAt, setSavedAt] = useState<string | null>(null);
  const [dirty, setDirty] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const profile = await api.get<ProfileResponse>("/candidate/profile");
        if (!cancelled) setForm(toForm(profile));
      } catch (err) {
        if (!cancelled) {
          const status = (err as { status?: number }).status;
          // Профиля ещё нет — начинаем с пустой формы, это не ошибка.
          if (status !== 404) setLoadError("Не удалось загрузить профиль. Попробуйте обновить страницу.");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const set = useCallback((key: string, value: string) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setDirty(true);
    setSavedAt(null);
  }, []);

  const onSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      setSaving(true);
      setSaveError(null);
      try {
        const payload: ProfileRequest = {
          full_name: form.full_name.trim(),
          contact_email: form.contact_email.trim() || undefined,
          contact_phone: form.contact_phone.trim() || undefined,
          position_title: form.position_title.trim() || undefined,
          grade_declared: form.grade_declared.trim() || undefined,
          experience_years: form.experience_years.trim() === "" ? undefined : Number(form.experience_years),
          stack: form.stack.trim() === "" ? undefined : toList(form.stack),
          skills: form.skills.trim() === "" ? undefined : toList(form.skills),
          soft_skills: form.soft_skills.trim() === "" ? undefined : toList(form.soft_skills),
          summary: form.summary.trim() || undefined,
          specialization_code: form.specialization_code.trim() || undefined,
          industry_code: form.industry_code.trim() || undefined,
        };
        if (payload.experience_years !== undefined && Number.isNaN(payload.experience_years)) {
          setSaveError("Стаж должен быть числом (лет).");
          return;
        }
        if (!payload.full_name) {
          setSaveError("Укажите ФИО — это обязательное поле.");
          return;
        }
        await api.put<ProfileResponse>("/candidate/profile", payload);
        setDirty(false);
        setSavedAt(new Date().toLocaleString("ru-RU"));
      } catch {
        setSaveError("Не удалось сохранить профиль. Проверьте данные и попробуйте снова.");
      } finally {
        setSaving(false);
      }
    },
    [form],
  );

  if (loading) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-10">
        <h1 className="text-2xl font-semibold">Профиль кандидата</h1>
        <p className="mt-4 text-sm text-slate-500">Загрузка профиля…</p>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      <h1 className="text-2xl font-semibold">Профиль кандидата</h1>
      <p className="mt-1 text-sm text-slate-500">Заполните данные — работодатели видят их при откликах.</p>

      <section className="mt-4 rounded-md border border-slate-200 bg-slate-50 px-4 py-3">
        <h2 className="text-sm font-medium text-slate-700">Резюме / PDF-загрузка</h2>
        <p className="mt-1 text-sm text-slate-500">Загрузите резюме в формате PDF для автозаполнения профиля.</p>
        <a
          href="/profile/upload"
          className="mt-2 inline-block rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
        >
          Перейти к загрузке резюме
        </a>
      </section>

      {loadError && (
        <div role="alert" className="mt-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {loadError}
        </div>
      )}

      <form onSubmit={onSubmit} className="mt-6 space-y-4">
        <div>
          <label htmlFor="full_name" className={labelClass}>ФИО *</label>
          <input
            id="full_name"
            className={inputClass}
            value={form.full_name}
            onChange={(e) => set("full_name", e.target.value)}
            required
            autoComplete="name"
            placeholder="Иванов Иван Иванович"
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="contact_email" className={labelClass}>Контактный e-mail</label>
            <input
              id="contact_email"
              type="email"
              className={inputClass}
              value={form.contact_email}
              onChange={(e) => set("contact_email", e.target.value)}
              autoComplete="email"
              placeholder="ivan@example.com"
            />
          </div>
          <div>
            <label htmlFor="contact_phone" className={labelClass}>Контактный телефон</label>
            <input
              id="contact_phone"
              type="tel"
              className={inputClass}
              value={form.contact_phone}
              onChange={(e) => set("contact_phone", e.target.value)}
              autoComplete="tel"
              placeholder="+7 900 000-00-00"
            />
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="position_title" className={labelClass}>Желаемая роль</label>
            <input
              id="position_title"
              className={inputClass}
              value={form.position_title}
              onChange={(e) => set("position_title", e.target.value)}
              placeholder="Backend-разработчик"
            />
          </div>
          <div>
            <label htmlFor="grade_declared" className={labelClass}>Грейд</label>
            <input
              id="grade_declared"
              className={inputClass}
              value={form.grade_declared}
              onChange={(e) => set("grade_declared", e.target.value)}
              placeholder="Middle"
            />
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="experience_years" className={labelClass}>Стаж (лет)</label>
            <input
              id="experience_years"
              type="number"
              min={0}
              step={1}
              className={inputClass}
              value={form.experience_years}
              onChange={(e) => set("experience_years", e.target.value)}
              placeholder="3"
            />
          </div>
          <div>
            <label htmlFor="specialization_code" className={labelClass}>Код специализации</label>
            <input
              id="specialization_code"
              className={inputClass}
              value={form.specialization_code}
              onChange={(e) => set("specialization_code", e.target.value)}
              placeholder="backend"
            />
          </div>
        </div>

        <div>
          <label htmlFor="industry_code" className={labelClass}>Код индустрии</label>
          <input
            id="industry_code"
            className={inputClass}
            value={form.industry_code}
            onChange={(e) => set("industry_code", e.target.value)}
            placeholder="fintech"
          />
        </div>

        <div>
          <label htmlFor="stack" className={labelClass}>Стек (через запятую)</label>
          <input
            id="stack"
            className={inputClass}
            value={form.stack}
            onChange={(e) => set("stack", e.target.value)}
            placeholder="Java, Spring, PostgreSQL"
          />
        </div>

        <div>
          <label htmlFor="skills" className={labelClass}>Навыки (через запятую)</label>
          <input
            id="skills"
            className={inputClass}
            value={form.skills}
            onChange={(e) => set("skills", e.target.value)}
            placeholder="REST, микросервисы, SQL"
          />
        </div>

        <div>
          <label htmlFor="soft_skills" className={labelClass}>Софт-скиллы (через запятую)</label>
          <input
            id="soft_skills"
            className={inputClass}
            value={form.soft_skills}
            onChange={(e) => set("soft_skills", e.target.value)}
            placeholder="коммуникация, менторство"
          />
        </div>

        <div>
          <label htmlFor="summary" className={labelClass}>О себе</label>
          <textarea
            id="summary"
            rows={4}
            className={inputClass}
            value={form.summary}
            onChange={(e) => set("summary", e.target.value)}
            placeholder="Кратко о вашем опыте и целях"
          />
        </div>

        {saveError && (
          <div role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {saveError}
          </div>
        )}
        {savedAt && !dirty && (
          <div role="status" className="rounded-md border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700">
            Профиль сохранён ({savedAt})
          </div>
        )}
        {dirty && !savedAt && (
          <p role="status" className="text-sm text-slate-500">Есть несохранённые изменения.</p>
        )}

        <button
          type="submit"
          disabled={saving}
          className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
        >
          {saving ? "Сохранение…" : "Сохранить профиль"}
        </button>
      </form>
    </main>
  );
}
