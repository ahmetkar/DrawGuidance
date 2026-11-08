import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { TermsEn, TermsTr } from "@/content/terms";
import { getDictionary, isLocale } from "@/lib/i18n";
import { pageMetadata } from "@/lib/seo";

export async function generateMetadata({ params }: PageProps<"/[lang]/terms">): Promise<Metadata> {
  const { lang } = await params;
  if (!isLocale(lang)) return {};
  const t = getDictionary(lang);
  return pageMetadata(lang, "terms", t.meta.termsTitle, `${t.appName}: ${t.meta.termsTitle}`);
}

export default async function TermsPage({ params }: PageProps<"/[lang]/terms">) {
  const { lang } = await params;
  if (!isLocale(lang)) notFound();
  return lang === "tr" ? <TermsTr /> : <TermsEn />;
}
