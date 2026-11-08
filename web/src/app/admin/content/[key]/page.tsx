import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { fieldsOf } from "@/lib/contentForm";
import { CONTENT_KEYS, CONTENT_LOCALES, DEFAULTS, type ContentKey, type ContentLocale } from "@/lib/contentSchema";
import { getContent } from "@/lib/server/content";
import { requireAdmin } from "@/lib/server/session";
import { resetContent } from "../../actions";
import { AdminShell } from "../../AdminShell";
import { ContentEditor } from "./ContentEditor";

export const metadata: Metadata = { title: "İçerik" };
export const dynamic = "force-dynamic";

const TITLES: Record<ContentKey, string> = { home: "Ana sayfa", about: "Hakkımızda", download: "İndirmeler" };
const PATHS: Record<ContentKey, string> = { home: "", about: "/about", download: "/download" };

export default async function ContentPage({ params, searchParams }: PageProps<"/admin/content/[key]">) {
  const admin = await requireAdmin();
  const { key } = await params;
  const sp = await searchParams;
  if (!CONTENT_KEYS.includes(key as ContentKey)) notFound();
  const k = key as ContentKey;
  const lang: ContentLocale = sp.lang === "en" ? "en" : "tr";
  const current = await getContent(lang, k);
  const fields = fieldsOf(DEFAULTS[lang][k], current);
  if (!CONTENT_LOCALES.includes(lang)) notFound();
  return (
    <AdminShell name={admin.name} active={`content-${k}`}>
      <div className="admin-head">
        <h1>{TITLES[k]}</h1>
        <div className="seg" role="tablist" aria-label="Dil">
          {CONTENT_LOCALES.map((l) => (
            <Link key={l} href={`/admin/content/${k}?lang=${l}`} aria-current={l === lang ? "page" : undefined} role="tab" aria-selected={l === lang}>
              {l === "tr" ? "Türkçe" : "English"}
            </Link>
          ))}
        </div>
      </div>
      <p className="meta">
        Alanları düzenleyip kaydedin; site hemen güncellenir. Boş satır paragraf ayırır. HTML yazılamaz (metin olarak görünür).{" "}
        <a href={`/${lang}${PATHS[k]}`} target="_blank" rel="noopener">
          Sayfayı aç ↗
        </a>
      </p>
      {sp.reset === "1" && <p className="status success">Varsayılan içeriğe dönüldü.</p>}
      <ContentEditor contentKey={k} locale={lang} fields={fields} />
      <form action={resetContent} className="danger-zone">
        <input type="hidden" name="_key" value={k} />
        <input type="hidden" name="_locale" value={lang} />
        <button className="button danger small" type="submit">
          Bu dildeki sayfayı varsayılana döndür
        </button>
      </form>
    </AdminShell>
  );
}
