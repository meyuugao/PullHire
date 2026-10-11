/**
 * Кандидат: загрузка резюме (PDF) + скачивание стандартизированного PDF.
 * См. docs/api.md §candidate: POST /candidate/resume (multipart/form-data
 * с полем file), GET /candidate/profile.pdf (binary).
 */

"use client";

import { useRef, useState } from "react";
import { apiFetch, buildUrl, createApiError } from "@/lib/api";
import { getStoredAccessToken } from "@/lib/auth";
import type { components } from "@/lib/api/generated/candidate/schema";

type ResumeResponse = components["schemas"]["ResumeResponse"];

const MAX_SIZE_BYTES = 5 * 1024 * 1024;

function getErrorMessage(status: number, body: unknown, fallback: string): string {
  if (status === 413) {
    return "Файл слишком большой. Максимальный размер — 5 МБ. Выберите файл поменьше.";
  }
  if (typeof body === "object" && body !== null && "message" in body) {
    const m = (body as { message: unknown }).message;
    if (typeof m === "string" && m.length > 0) return m;
  }
  if (typeof body === "string" && body.length > 0 && body.length < 500) return body;
  return fallback;
}

function isApiError(err: unknown): err is { status: number; body: unknown } {
  return (
    typeof err === "object" &&
    err !== null &&
    "status" in err &&
    typeof (err as { status: unknown }).status === "number"
  );
}

export default function ResumeUploadPage() {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [fileName, setFileName] = useState<string>("");
  const [uploading, setUploading] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ResumeResponse | null>(null);

  async function handleFile(file: File | undefined) {
    setError(null);
    setResult(null);
    if (!file) return;
    if (file.type !== "application/pdf" && !file.name.toLowerCase().endsWith(".pdf")) {
      setError("Выберите PDF-файл.");
      return;
    }
    if (file.size > MAX_SIZE_BYTES) {
      setError("Файл слишком большой. Максимальный размер — 5 МБ. Выберите файл поменьше.");
      return;
    }
    setFileName(file.name);
    setUploading(true);
    try {
      // multipart/form-data с полем file (см. ResumeController @RequestParam("file")):
      // FormData через rest.body без явного content-type — браузер сам
      // выставит multipart-границу.
      const form = new FormData();
      form.append("file", file, file.name);
      const data = await apiFetch<ResumeResponse>("/candidate/resume", {
        method: "POST",
        body: form,
      });
      setResult(data);
    } catch (err) {
      if (isApiError(err)) {
        setError(getErrorMessage(err.status, err.body, "Не удалось загрузить резюме. Попробуйте ещё раз."));
      } else {
        setError("Не удалось загрузить резюме. Попробуйте ещё раз.");
      }
    } finally {
      setUploading(false);
    }
  }

  async function handleDownload() {
    setError(null);
    setDownloading(true);
    try {
      // apiFetch разбирает тело как JSON-текст, поэтому для бинарного PDF
      // используем обычный fetch с Bearer-токеном и createObjectURL для blob.
      const token = getStoredAccessToken();
      const res = await fetch(buildUrl("/candidate/profile.pdf"), {
        method: "GET",
        headers: token ? { authorization: `Bearer ${token}` } : {},
      });
      if (!res.ok) {
        let body: unknown = null;
        try {
          body = await res.json();
        } catch {
          body = null;
        }
        throw createApiError(res.status, body);
      }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "profile.pdf";
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      if (isApiError(err)) {
        setError(getErrorMessage(err.status, err.body, "Не удалось скачать PDF. Попробуйте ещё раз."));
      } else {
        setError("Не удалось скачать PDF. Попробуйте ещё раз.");
      }
    } finally {
      setDownloading(false);
    }
  }

  return (
    <main className="mx-auto flex max-w-2xl flex-col gap-6 p-6">
      <h1 className="text-2xl font-bold">Загрузка резюме</h1>
      <p className="text-sm text-slate-600">
        Загрузите резюме в формате PDF. Мы автоматически распознаем данные и подставим их в
        профиль (автораспознавание).
      </p>

      <section className="flex flex-col gap-3 rounded-lg border border-slate-200 p-4">
        <label htmlFor="resume-file" className="text-sm font-medium">
          PDF-файл резюме (до 5 МБ)
        </label>
        <input
          ref={inputRef}
          id="resume-file"
          type="file"
          accept="application/pdf,.pdf"
          className="text-sm"
          onChange={(e) => void handleFile(e.target.files?.[0])}
        />
        {fileName ? <p className="text-sm text-slate-600">Выбран файл: {fileName}</p> : null}
        <button
          type="button"
          disabled={uploading}
          onClick={() => inputRef.current?.click()}
          className="w-fit rounded bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {uploading ? "Загружаем…" : "Выбрать файл"}
        </button>
        {result ? (
          <p aria-live="polite" className="rounded bg-green-50 p-3 text-sm text-green-800">
            Резюме загружено{result.fileName ? ` (${result.fileName})` : ""}. Распознанные поля уже
            подставлены в профиль — проверьте их на странице профиля.
            {result.parseStatus ? ` Статус разбора: ${result.parseStatus}.` : ""}
          </p>
        ) : null}
      </section>

      <section className="flex flex-col gap-3 rounded-lg border border-slate-200 p-4">
        <h2 className="text-sm font-medium">Стандартизированный профиль (PDF)</h2>
        <p className="text-sm text-slate-600">
          Скачайте ваш профиль в едином стандартном оформлении.
        </p>
        <button
          type="button"
          disabled={downloading}
          onClick={() => void handleDownload()}
          className="w-fit rounded bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {downloading ? "Готовим PDF…" : "Скачать PDF"}
        </button>
      </section>

      {error ? (
        <p role="alert" className="rounded bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      ) : null}
    </main>
  );
}
