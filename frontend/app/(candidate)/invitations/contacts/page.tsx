"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { api } from "@/lib/api";
import type { components } from "@/lib/api/generated/outreach/schema";

type InvitationResponse = components["schemas"]["InvitationResponse"];
type EmployerContact = components["schemas"]["EmployerContact"];
type PageResponse = components["schemas"]["PageResponseInvitationResponse"];

const HIDDEN_TEXT = "контакты будут раскрыты после принятия приглашения";

// Локальный type guard: в сгенерированной схеме employer_contact /
// released_contact типизированы как unknown — сужаем тип только здесь,
// сгенерированные файлы не трогаем.
function asEmployerContact(value: unknown): EmployerContact | null {
  if (typeof value !== "object" || value === null) return null;
  const v = value as Record<string, unknown>;
  const contact: EmployerContact = {};
  if (typeof v.contact_email === "string" && v.contact_email.trim() !== "") {
    contact.contact_email = v.contact_email;
  }
  if (typeof v.contact_phone === "string" && v.contact_phone.trim() !== "") {
    contact.contact_phone = v.contact_phone;
  }
  if (typeof v.website === "string" && v.website.trim() !== "") {
    contact.website = v.website;
  }
  if (
    contact.contact_email === undefined &&
    contact.contact_phone === undefined &&
    contact.website === undefined
  ) {
    return null;
  }
  return contact;
}

function isAccepted(inv: InvitationResponse): boolean {
  const status = (inv.status ?? "").toLowerCase();
  // CRITICAL INVARIANT: контакты видны только после принятия.
  // Раскрытием считаем принятый статус либо отметку contact_released_at.
  return status === "accepted" || (inv.contact_released_at ?? "") !== "";
}

function ContactsInner() {
  const searchParams = useSearchParams();
  const requestedId = searchParams.get("invitation");

  const [items, setItems] = useState<InvitationResponse[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const page = await api.get<PageResponse>("/invitations");
        if (cancelled) return;
        const list = page.items ?? [];
        setItems(list);
        if (requestedId && list.some((i) => i.id === requestedId)) {
          setSelectedId(requestedId);
        } else if (list.length > 0 && list[0]?.id !== undefined) {
          setSelectedId(list[0]?.id ?? null);
        } else {
          setSelectedId(null);
        }
      } catch {
        if (!cancelled) setError("Не удалось загрузить приглашения. Попробуйте обновить страницу.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [requestedId]);

  const selected: InvitationResponse | null = useMemo(
    () => items.find((i) => i.id === selectedId) ?? null,
    [items, selectedId],
  );

  const accepted = selected !== null && isAccepted(selected);

  // Контакты вычисляем, но рендерим только при accepted (инвариант).
  const contact: EmployerContact | null = useMemo(() => {
    if (selected === null) return null;
    return (
      asEmployerContact(selected.released_contact) ??
      asEmployerContact(selected.employer_contact)
    );
  }, [selected]);

  if (loading) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-10">
        <h1 className="text-2xl font-semibold">Контакты работодателя</h1>
        <p className="mt-4 text-sm text-slate-500">Загрузка…</p>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      <h1 className="text-2xl font-semibold">Контакты работодателя</h1>
      <p className="mt-1 text-sm text-slate-500">
        Контакты компании видны только после принятия приглашения.
      </p>

      {error && (
        <div role="alert" className="mt-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}

      {items.length === 0 && !error ? (
        <p className="mt-6 text-sm text-slate-500">Приглашений пока нет.</p>
      ) : (
        <>
          <label htmlFor="invitation" className="mb-1 mt-6 block text-sm font-medium text-slate-700">
            Приглашение
          </label>
          <select
            id="invitation"
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
            value={selectedId ?? ""}
            onChange={(e) => setSelectedId(e.target.value || null)}
          >
            {items.map((inv) => (
              <option key={inv.id ?? ""} value={inv.id ?? ""}>
                {inv.company_name ?? "Компания"} — {inv.status ?? "статус неизвестен"}
              </option>
            ))}
          </select>

          {selected && (
            <section className="mt-6 rounded-md border border-slate-200 bg-white p-4">
              <h2 className="text-lg font-medium">{selected.company_name ?? "Компания"}</h2>
              {selected.message && (
                <p className="mt-1 text-sm text-slate-600">{selected.message}</p>
              )}
              <p className="mt-1 text-xs text-slate-500">Статус: {selected.status ?? "—"}</p>

              {!accepted ? (
                <p role="status" className="mt-4 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                  {HIDDEN_TEXT}
                </p>
              ) : contact === null ? (
                <p role="status" className="mt-4 text-sm text-slate-500">
                  Контакты пока не указаны работодателем.
                </p>
              ) : (
                <dl className="mt-4 space-y-2 text-sm">
                  {contact.contact_email !== undefined && (
                    <div className="flex gap-2">
                      <dt className="w-28 shrink-0 font-medium text-slate-700">E-mail</dt>
                      <dd className="text-slate-900">{contact.contact_email}</dd>
                    </div>
                  )}
                  {contact.contact_phone !== undefined && (
                    <div className="flex gap-2">
                      <dt className="w-28 shrink-0 font-medium text-slate-700">Телефон</dt>
                      <dd className="text-slate-900">{contact.contact_phone}</dd>
                    </div>
                  )}
                  {contact.website !== undefined && (
                    <div className="flex gap-2">
                      <dt className="w-28 shrink-0 font-medium text-slate-700">Сайт</dt>
                      <dd className="text-slate-900">{contact.website}</dd>
                    </div>
                  )}
                </dl>
              )}
            </section>
          )}
        </>
      )}
    </main>
  );
}

export default function InvitationContactsPage() {
  return (
    <Suspense fallback={
      <main className="mx-auto max-w-2xl px-4 py-10">
        <h1 className="text-2xl font-semibold">Контакты работодателя</h1>
        <p className="mt-4 text-sm text-slate-500">Загрузка…</p>
      </main>
    }>
      <ContactsInner />
    </Suspense>
  );
}
