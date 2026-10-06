import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "PullHire",
  description: "Платформа-агрегатор ИТ-вакансий с верифицированным профилем ФСП",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ru">
      <body className="min-h-screen bg-slate-50 text-slate-900">{children}</body>
    </html>
  );
}
