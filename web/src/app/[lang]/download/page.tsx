import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { JsonLd } from "@/components/JsonLd";
import { SiteImage } from "@/components/SiteImage";
import type { DownloadContent } from "@/lib/contentSchema";
import { getDictionary, isLocale } from "@/lib/i18n";
import { breadcrumb, pageMetadata } from "@/lib/seo";
import { getContent, getImages } from "@/lib/server/content";

// v42 İndir: Google Play, Windows ve iOS (başlık, açıklama, bağlantı; yönetim panelinden düzenlenir).
export const revalidate = 3600;

export async function generateMetadata({ params }: PageProps<"/[lang]/download">): Promise<Metadata> {
  const { lang } = await params;
  if (!isLocale(lang)) return {};
  const t = getDictionary(lang);
  return pageMetadata(lang, "download", t.meta.downloadTitle, t.meta.downloadDescription);
}

const ICONS: Record<"android" | "windows" | "ios", React.ReactNode> = {
  android: (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path fill="currentColor" d="M4 9h16v8a2 2 0 0 1-2 2h-1v3h-2v-3H9v3H7v-3H6a2 2 0 0 1-2-2zM6.6 3.2l1.3 2.3A8 8 0 0 1 12 4.5a8 8 0 0 1 4.1 1l1.3-2.3.9.5-1.3 2.3A6.9 6.9 0 0 1 20 8H4a6.9 6.9 0 0 1 3-2.4L5.7 3.7zM9 6.5a.8.8 0 1 0 0 1.6.8.8 0 0 0 0-1.6zm6 0a.8.8 0 1 0 0 1.6.8.8 0 0 0 0-1.6z" />
    </svg>
  ),
  windows: (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path fill="currentColor" d="M3 5.1 10.4 4v7.2H3zm8.3-1.2L21 2.5v8.7h-9.7zM3 12.8h7.4V20L3 18.9zm8.3 0H21v8.7l-9.7-1.4z" />
    </svg>
  ),
  ios: (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path fill="currentColor" d="M16.4 12.6c0-2.5 2-3.7 2.1-3.8a4.6 4.6 0 0 0-3.6-1.9c-1.5-.2-3 .9-3.7.9-.8 0-2-.9-3.2-.9a4.8 4.8 0 0 0-4.1 2.5c-1.8 3.1-.5 7.6 1.3 10.1.8 1.2 1.8 2.6 3.1 2.5 1.2 0 1.7-.8 3.2-.8s1.9.8 3.2.8 2.2-1.2 3-2.4a10 10 0 0 0 1.4-2.8 4.3 4.3 0 0 1-2.7-4.2zM14 5.3A4.2 4.2 0 0 0 15 2a4.3 4.3 0 0 0-2.8 1.5 4 4 0 0 0-1 3.1A3.6 3.6 0 0 0 14 5.3z" />
    </svg>
  ),
};

export default async function DownloadPage({ params }: PageProps<"/[lang]/download">) {
  const { lang } = await params;
  if (!isLocale(lang)) notFound();
  const t = getDictionary(lang);
  const [c, images] = await Promise.all([getContent(lang, "download"), getImages()]);
  const platforms = (["android", "windows", "ios"] as const).map((k) => [k, c[k]] as const);
  return (
    <>
      <JsonLd data={breadcrumb(lang, "download", t.meta.downloadTitle)} />
      <section className="page-hero">
        <div className="container hero-grid">
          <div className="hero-copy">
            <p className="eyebrow">{t.nav.download}</p>
            <h1>{c.title}</h1>
            <p className="lead">{c.intro}</p>
          </div>
          <div className="hero-visual">
            <SiteImage image={images.download} locale={lang} label={c.title} placeholder={t.pages.imagePlaceholder} width={640} height={480} priority />
          </div>
        </div>
      </section>
      <section className="container section" aria-label={t.pages.platform}>
        <div className="card-grid three">
          {platforms.map(([key, p]) => (
            <PlatformCard key={key} id={key} p={p} icon={ICONS[key]} soon={t.pages.soon} available={t.pages.available} />
          ))}
        </div>
        {c.note && <p className="notice">{c.note}</p>}
      </section>
    </>
  );
}

function PlatformCard({
  id,
  p,
  icon,
  soon,
  available,
}: {
  id: string;
  p: DownloadContent["android"];
  icon: React.ReactNode;
  soon: string;
  available: string;
}) {
  const live = p.available && p.url;
  return (
    <article className={live ? "platform-card" : "platform-card muted-card"} id={id} aria-labelledby={`${id}-title`}>
      <span className="platform-icon">{icon}</span>
      <h2 id={`${id}-title`}>{p.title}</h2>
      <p>{p.description}</p>
      <span className={live ? "pill ok" : "pill"}>{live ? available : soon}</span>
      {live ? (
        <a className="button" href={p.url} rel="noopener noreferrer">
          {p.button}
        </a>
      ) : (
        <span className="button secondary" aria-disabled="true">
          {p.button}
        </span>
      )}
    </article>
  );
}
