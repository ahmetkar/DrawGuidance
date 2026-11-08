import "server-only";
import { unstable_cache } from "next/cache";
import {
  DEFAULTS,
  IMAGE_SLOTS,
  parseContent,
  type ContentKey,
  type ContentLocale,
  type ContentMap,
  type ImageSlot,
  type SiteImage,
} from "@/lib/contentSchema";
import { db } from "./db";

// v42 sayfa içerikleri ve görseller: yönetim panelinde kaydedilen (veritabanı), yoksa varsayılan. Okumalar
// önbelleklenir ("site-content" etiketi); panelde kaydedince etiket yenilenir.

export const CONTENT_TAG = "site-content";

const readContent = unstable_cache(
  async (locale: ContentLocale, key: ContentKey): Promise<unknown> => {
    const sql = db();
    if (!sql) return null;
    try {
      const rows = (await sql`SELECT value FROM site_content WHERE locale = ${locale} AND key = ${key}`) as { value: unknown }[];
      return rows[0]?.value ?? null;
    } catch {
      // Tablo henüz yoksa (db:migrate çalışmadı) varsayılan
      return null;
    }
  },
  ["site-content-v1"],
  { tags: [CONTENT_TAG], revalidate: 3600 },
);

const readImages = unstable_cache(
  async (): Promise<Record<string, SiteImage>> => {
    const sql = db();
    if (!sql) return {};
    try {
      const rows = (await sql`SELECT slot, url, alt_tr, alt_en FROM site_images`) as { slot: string; url: string; alt_tr: string; alt_en: string }[];
      return Object.fromEntries(rows.map((r) => [r.slot, { url: r.url, altTr: r.alt_tr, altEn: r.alt_en }]));
    } catch {
      return {};
    }
  },
  ["site-images-v1"],
  { tags: [CONTENT_TAG], revalidate: 3600 },
);

export async function getContent<K extends ContentKey>(locale: ContentLocale, key: K): Promise<ContentMap[K]> {
  const raw = await readContent(locale, key);
  return raw == null ? DEFAULTS[locale][key] : parseContent(key, locale, raw);
}

export async function getImages(): Promise<Record<ImageSlot, SiteImage>> {
  const stored = await readImages();
  return Object.fromEntries(IMAGE_SLOTS.map((s) => [s, stored[s] ?? { url: "", altTr: "", altEn: "" }])) as Record<ImageSlot, SiteImage>;
}
