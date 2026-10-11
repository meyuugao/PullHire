import type { Metadata } from "next";
import "./globals.css";
import Providers from "@/components/shell";

export const metadata: Metadata = {
  title: "PullHire",
  description: "Платформа-агрегатор ИТ-вакансий с верифицированным профилем ФСП",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ru">
      <body className="min-h-screen bg-slate-50 text-slate-900">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
