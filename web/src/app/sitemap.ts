import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/config";
import { href, LOCALES, PAGES } from "@/lib/i18n";

// v42: kurumsal sayfalar (Ana sayfa, Hakkımızda, İndir, İletişim) yasal metinlerden önce ve daha yüksek öncelikle.
const PRIORITY: Record<string, number> = { "": 1, download: 0.9, about: 0.7, contact: 0.6, support: 0.5 };

export default function sitemap(): MetadataRoute.Sitemap {
  return PAGES.flatMap((page) =>
    LOCALES.map((locale) => ({
      url: `${SITE_URL}${href(locale, page)}`,
      changeFrequency: page === "" || page === "download" ? ("weekly" as const) : ("monthly" as const),
      priority: PRIORITY[page] ?? 0.3,
      alternates: { languages: Object.fromEntries(LOCALES.map((l) => [l, `${SITE_URL}${href(l, page)}`])) },
    })),
  );
}
