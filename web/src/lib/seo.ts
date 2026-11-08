import type { Metadata } from "next";
import { SITE_URL } from "./config";
import { getDictionary, href, isLocale, LOCALES, type Locale, type PagePath } from "./i18n";

/**
 * Sayfa başlığı, açıklaması, kanonik adres, öteki dildeki karşılığı (hreflang) ve Open Graph / Twitter kartı.
 * Paylaşım görseli [lang]/opengraph-image.tsx'ten gelir (her sayfaya kendiliğinden eklenir).
 */
export function pageMetadata(lang: string, page: PagePath, title: string | undefined, description: string): Metadata {
  const locale: Locale = isLocale(lang) ? lang : "tr";
  const t = getDictionary(locale);
  const languages: Record<string, string> = {};
  for (const l of LOCALES) languages[l] = href(l, page);
  languages["x-default"] = href("tr", page);
  const fullTitle = title ? `${title} · ${t.appName}` : t.meta.homeTitle;
  return {
    ...(title ? { title } : {}),
    description,
    alternates: { canonical: href(locale, page), languages },
    openGraph: {
      type: "website",
      siteName: t.appName,
      locale: locale === "tr" ? "tr_TR" : "en_US",
      alternateLocale: locale === "tr" ? ["en_US"] : ["tr_TR"],
      url: `${SITE_URL}${href(locale, page)}`,
      title: fullTitle,
      description,
    },
    twitter: { card: "summary_large_image", title: fullTitle, description },
  };
}

/** schema.org kırıntı yolu (Ana sayfa › sayfa). */
export function breadcrumb(locale: Locale, page: PagePath, name: string) {
  const t = getDictionary(locale);
  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: [
      { "@type": "ListItem", position: 1, name: t.pages.breadcrumbHome, item: `${SITE_URL}${href(locale, "")}` },
      { "@type": "ListItem", position: 2, name, item: `${SITE_URL}${href(locale, page)}` },
    ],
  };
}
