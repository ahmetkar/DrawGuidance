import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { PrivacyEn, PrivacyTr } from "@/content/privacy";
import { getDictionary, isLocale } from "@/lib/i18n";
import { pageMetadata } from "@/lib/seo";

export async function generateMetadata({ params }: PageProps<"/[lang]/privacy">): Promise<Metadata> {
  const { lang } = await params;
  if (!isLocale(lang)) return {};
  const t = getDictionary(lang);
  return pageMetadata(lang, "privacy", t.meta.privacyTitle, `${t.appName}: ${t.meta.privacyTitle}`);
}

export default async function PrivacyPage({ params }: PageProps<"/[lang]/privacy">) {
  const { lang } = await params;
  if (!isLocale(lang)) notFound();
  return lang === "tr" ? <PrivacyTr /> : <PrivacyEn />;
}
