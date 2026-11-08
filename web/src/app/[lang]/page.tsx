import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { JsonLd } from "@/components/JsonLd";
import { SiteImage } from "@/components/SiteImage";
import { DEVELOPER_NAME, PACKAGE_NAME, PLAY_STORE_URL, SITE_URL } from "@/lib/config";
import { getDictionary, href, isLocale } from "@/lib/i18n";
import { pageMetadata } from "@/lib/seo";
import { getContent, getImages } from "@/lib/server/content";

// v42 kurumsal ana sayfa (mobil uygulama SaaS tanıtımı): karşılama, rakamlar, özellikler, vitrin, SSS ve çağrı.
// Metinler ve görseller yönetim panelinden düzenlenir; panel kaydı önbelleği yeniler.
export const revalidate = 3600;

export async function generateMetadata({ params }: PageProps<"/[lang]">): Promise<Metadata> {
  const { lang } = await params;
  if (!isLocale(lang)) return {};
  return pageMetadata(lang, "", undefined, getDictionary(lang).meta.homeDescription);
}

export default async function HomePage({ params }: PageProps<"/[lang]">) {
  const { lang } = await params;
  if (!isLocale(lang)) notFound();
  const t = getDictionary(lang);
  const [c, images] = await Promise.all([getContent(lang, "home"), getImages()]);
  const showcaseImages = [images.showcase1, images.showcase2, images.showcase3];
  return (
    <>
      <JsonLd
        data={{
          "@context": "https://schema.org",
          "@graph": [
            {
              "@type": "MobileApplication",
              name: t.appName,
              operatingSystem: "Android 6.0+",
              applicationCategory: "DesignApplication",
              url: `${SITE_URL}${href(lang, "")}`,
              downloadUrl: PLAY_STORE_URL,
              identifier: PACKAGE_NAME,
              description: t.meta.homeDescription,
              inLanguage: ["tr", "en", "ar", "ja", "ko", "ru", "zh"],
              offers: { "@type": "Offer", price: "0", priceCurrency: lang === "tr" ? "TRY" : "USD" },
              author: { "@type": "Organization", name: DEVELOPER_NAME },
            },
            {
              "@type": "FAQPage",
              mainEntity: c.faq.map((f) => ({ "@type": "Question", name: f.q, acceptedAnswer: { "@type": "Answer", text: f.a } })),
            },
          ],
        }}
      />

      <section className="hero-band" aria-labelledby="hero-title">
        <div className="container hero-grid">
          <div className="hero-copy">
            {c.heroBadge && <p className="eyebrow">{c.heroBadge}</p>}
            <h1 id="hero-title">{c.heroTitle}</h1>
            <p className="lead">{c.heroSubtitle}</p>
            <div className="button-row">
              <Link className="button large" href={href(lang, "download")}>
                {c.heroPrimary}
              </Link>
              <a className="button large secondary" href="#features">
                {c.heroSecondary}
              </a>
            </div>
          </div>
          <div className="hero-visual">
            <SiteImage image={images.hero} locale={lang} label={t.appName} placeholder={t.pages.imagePlaceholder} width={720} height={820} priority />
          </div>
        </div>
      </section>

      <section className="container stats" aria-label={t.pages.statsLabel}>
        {c.stats.map((s) => (
          <div className="stat" key={s.label}>
            <strong>{s.value}</strong>
            <span>{s.label}</span>
          </div>
        ))}
      </section>

      <section id="features" className="container section" aria-labelledby="features-title">
        <p className="eyebrow center">{t.pages.featuresLabel}</p>
        <h2 id="features-title" className="section-title">
          {c.featuresTitle}
        </h2>
        {c.featuresIntro && <p className="section-intro">{c.featuresIntro}</p>}
        <div className="card-grid">
          {c.features.map((f, i) => (
            <article className="feature-card" key={f.title}>
              <span className="feature-icon" aria-hidden="true">
                {String(i + 1).padStart(2, "0")}
              </span>
              <h3>{f.title}</h3>
              <p>{f.text}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="section soft-band" aria-labelledby="showcase-title">
        <div className="container">
          <p className="eyebrow center">{t.pages.showcaseLabel}</p>
          <h2 id="showcase-title" className="section-title">
            {c.showcaseTitle}
          </h2>
          {c.showcase.map((s, i) => (
            <div className={i % 2 ? "showcase reverse" : "showcase"} key={s.title}>
              <div className="showcase-copy">
                <h3>{s.title}</h3>
                <p>{s.text}</p>
              </div>
              <SiteImage image={showcaseImages[i]!} locale={lang} label={s.title} placeholder={t.pages.imagePlaceholder} width={640} height={420} />
            </div>
          ))}
        </div>
      </section>

      <section className="container section narrow" aria-labelledby="faq-title">
        <p className="eyebrow center">{t.pages.faqLabel}</p>
        <h2 id="faq-title" className="section-title">
          {c.faqTitle}
        </h2>
        <div className="faq">
          {c.faq.map((f) => (
            <details key={f.q}>
              <summary>{f.q}</summary>
              <p>{f.a}</p>
            </details>
          ))}
        </div>
      </section>

      <section className="container" aria-labelledby="cta-title">
        <div className="cta-band">
          <div>
            <h2 id="cta-title">{c.ctaTitle}</h2>
            <p>{c.ctaText}</p>
          </div>
          <Link className="button large light" href={href(lang, "download")}>
            {c.ctaButton}
          </Link>
        </div>
      </section>
    </>
  );
}
