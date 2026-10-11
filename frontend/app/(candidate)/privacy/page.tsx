"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "../../../lib/api";
import type { components } from "../../../lib/api/generated/candidate/schema";

type PrivacyRequest = components["schemas"]["PrivacyRequest"];
type PrivacyResponse = components["schemas"]["PrivacyResponse"];

const DEFAULTS: PrivacyRequest = {
  visibleToEmployers: true,
  showFsp: true,
  showContacts: false,
};

function Toggle({
  checked,
  onChange,
  label,
  description,
  disabled,
}: {
  checked: boolean;
  onChange: (next: boolean) => void;
  label: string;
  description: string;
  disabled?: boolean;
}) {
  return (
    <label
      className={`flex cursor-pointer items-start justify-between gap-4 rounded-lg border border-gray-200 bg-white p-4 transition-colors ${
        disabled ? "cursor-not-allowed opacity-60" : "hover:border-gray-300"
      }`}
    >
      <span>
        <span className="block text-sm font-medium text-gray-900">{label}</span>
        <span className="mt-1 block text-sm text-gray-500">{description}</span>
      </span>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        disabled={disabled}
        onClick={(e) => {
          e.preventDefault();
          onChange(!checked);
        }}
        className={`relative mt-1 inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors ${
          checked ? "bg-blue-600" : "bg-gray-300"
        }`}
      >
        <span
          className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${
            checked ? "translate-x-6" : "translate-x-1"
          }`}
        />
      </button>
      <input
        type="checkbox"
        className="sr-only"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
      />
    </label>
  );
}

export default function PrivacyPage() {
  const [form, setForm] = useState<PrivacyRequest>(DEFAULTS);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await api.get<PrivacyResponse>("/candidate/privacy");
        if (cancelled) return;
        setForm({
          visibleToEmployers: data.visibleToEmployers ?? DEFAULTS.visibleToEmployers,
          showFsp: data.showFsp ?? DEFAULTS.showFsp,
          showContacts: data.showContacts ?? DEFAULTS.showContacts,
        });
      } catch (e) {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : "Не удалось загрузить настройки");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const set = useCallback(
    (key: keyof PrivacyRequest) => (next: boolean) => {
      setSaved(false);
      setForm((prev) => ({ ...prev, [key]: next }));
    },
    []
  );

  const handleSave = useCallback(async () => {
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      const payload: PrivacyRequest = { ...form };
      const data = await api.put<PrivacyResponse>("/candidate/privacy", payload);
      setForm({
        visibleToEmployers: data.visibleToEmployers ?? payload.visibleToEmployers,
        showFsp: data.showFsp ?? payload.showFsp,
        showContacts: data.showContacts ?? payload.showContacts,
      });
      setSaved(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Не удалось сохранить настройки");
    } finally {
      setSaving(false);
    }
  }, [form]);

  if (loading) {
    return (
      <main className="mx-auto max-w-2xl p-6">
        <h1 className="text-xl font-semibold text-gray-900">Настройки приватности</h1>
        <p className="mt-4 text-sm text-gray-500">Загрузка…</p>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-2xl space-y-4 p-6">
      <div>
        <h1 className="text-xl font-semibold text-gray-900">Настройки приватности</h1>
        <p className="mt-1 text-sm text-gray-500">
          Управляйте тем, что видят работодатели в вашем профиле.
        </p>
      </div>

      {error && (
        <p role="alert" className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </p>
      )}
      {saved && (
        <p role="status" className="rounded-lg bg-green-50 px-4 py-3 text-sm text-green-700">
          Настройки сохранены
        </p>
      )}

      <Toggle
        checked={form.visibleToEmployers}
        onChange={set("visibleToEmployers")}
        label="Профиль виден работодателям"
        description="Если выключено, ваш профиль не будет показываться в поиске работодателей."
        disabled={saving}
      />
      <Toggle
        checked={form.showFsp}
        onChange={set("showFsp")}
        label="Показывать ФСП-верификацию"
        description="Отображать работодателям статус проверки ФСП."
        disabled={saving}
      />
      <Toggle
        checked={form.showContacts}
        onChange={set("showContacts")}
        label="Показывать контакты"
        description="Открыть email и телефон для работодателей."
        disabled={saving}
      />

      <button
        type="button"
        onClick={handleSave}
        disabled={saving}
        className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {saving ? "Сохранение…" : "Сохранить"}
      </button>
    </main>
  );
}
