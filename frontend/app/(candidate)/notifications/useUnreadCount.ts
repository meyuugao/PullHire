"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { components } from "@/lib/api/generated/notification/schema";

type PageResponse = components["schemas"]["PageResponseNotificationLogItem"];

const POLL_INTERVAL_MS = 30000;

function isUnreadStatus(status?: string): boolean {
  if (!status) return true;
  const s = status.toUpperCase();
  return s !== "SENT" && s !== "DELIVERED" && s !== "SUCCESS";
}

/** Непрочитанные = записи без sent_at либо со статусом не из SENT/DELIVERED. */
export function countUnread(items: PageResponse["items"]): number {
  return (items ?? []).filter((n) => !n.sent_at && isUnreadStatus(n.status)).length;
}

/**
 * Хук числа непрочитанных уведомлений для шапки (ST-16 подключит позже).
 * Опрашивает GET /notifications каждые 30 секунд.
 */
export function useUnreadCount(): { unread: number; loading: boolean } {
  const [unread, setUnread] = useState(0);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async (signal: AbortSignal) => {
    try {
      const page = await api.get<PageResponse>("/notifications?size=100&sort=created_at,desc", {
        signal,
      });
      if (!signal.aborted) setUnread(countUnread(page.items));
    } catch {
      // Игнорируем ошибки фона: счётчик просто не обновится.
    } finally {
      if (!signal.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    const timer = setInterval(() => {
      void load(controller.signal);
    }, POLL_INTERVAL_MS);
    return () => {
      clearInterval(timer);
      controller.abort();
    };
  }, [load]);

  return { unread, loading };
}
