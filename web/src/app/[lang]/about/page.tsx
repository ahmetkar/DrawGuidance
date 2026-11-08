import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { JsonLd } from "@/components/JsonLd";
import { Paragraphs } from "@/components/Paragraphs";
import { SiteImage } from "@/components/SiteImage";
import { getDictionary, href, isLocale } from "@/lib/i18n";
import { breadcrumb, pageMetadata } from "@/lib/seo";
import { getContent, getImages } from "@/lib/server/content";

// v42 Hakkımızda (yönetim panelinden düzenlenir).
export const revalidate = 3600;

export async function generateMetadata({ params }: PageProps<"/[lang]/about">): Promise<Metadata> {
  const { lang } = await params;
  if (!isLocale(lang)) return {};
  const t = getDictionary(lang);
  return pageMetadata(lang, "about", t.meta.aboutTitle, t.meta.aboutDescription);
}

export default async function AboutPage({ params }: PageProps<"/[lang]/about">) {
  const { lang } = await params;
  if (!isLocale(lang)) notFound();
  const t = getDictionary(lang);
  const [c, images] = await Promise.all([getContent(lang, "about"), getImages()]);
  return (
    <>
      <JsonLd data={breadcrumb(lang, "about", t.meta.aboutTitle)} />
      <JsonLd data={{ "@context": "https://schema.org", "@type": "AboutPage", name: c.title, description: t.meta.aboutDescription }} />
      <section className="page-hero">
        <div className="container hero-grid">
          <div className="hero-copy">
            <p className="eyebrow">{t.nav.about}</p>
            <h1>{c.title}</h1>
            <Paragraphs text={c.intro} className="lead" />
          </div>
          <div className="hero-visual">
            <SiteImage image={images.about} locale={lang} label={c.title} placeholder={t.pages.imagePlaceholder} width={640} height={480} priority />
          </div>
        </div>
      </section>

      <section className="container section split" aria-labelledby="mission-title">
        <h2 id="mission-title">{c.missionTitle}</h2>
        <div>
          <Paragraphs text={c.mission} />
        </div>
      </section>

      <section className="section soft-band" aria-labelledby="values-title">
        <div className="container">
          <h2 id="values-title" className="section-title">
            {c.valuesTitle}
          </h2>
          <div className="card-grid three">
            {c.values.map((v) => (
              <article className="feature-card" key={v.title}>
                <h3>{v.title}</h3>
                <p>{v.text}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="container section split" aria-labelledby="story-title">
        <h2 id="story-title">{c.storyTitle}</h2>
        <div>
          <Paragraphs text={c.story} />
          <div className="button-row">
            <Link className="button" href={href(lang, "download")}>
              {t.nav.download}
            </Link>
            <Link className="button secondary" href={href(lang, "contact")}>
              {t.nav.contact}
            </Link>
          </div>
        </div>
      </section>
    </>
  );
}
