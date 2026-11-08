import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { JsonLd } from "@/components/JsonLd";
import { CONTACT_EMAIL, DEVELOPER_NAME, SITE_URL } from "@/lib/config";
import { getDictionary, href, isLocale } from "@/lib/i18n";
import { breadcrumb, pageMetadata } from "@/lib/seo";
import { ContactForm } from "./ContactForm";

export async function generateMetadata({ params }: PageProps<"/[lang]/contact">): Promise<Metadata> {
  const { lang } = await params;
  if (!isLocale(lang)) return {};
  const t = getDictionary(lang);
  return pageMetadata(lang, "contact", t.meta.contactTitle, t.meta.contactDescription);
}

export default async function ContactPage({ params }: PageProps<"/[lang]/contact">) {
  const { lang } = await params;
  if (!isLocale(lang)) notFound();
  const t = getDictionary(lang);
  return (
    <>
      <JsonLd data={breadcrumb(lang, "contact", t.meta.contactTitle)} />
      <JsonLd
        data={{
          "@context": "https://schema.org",
          "@type": "ContactPage",
          name: t.meta.contactTitle,
          url: `${SITE_URL}${href(lang, "contact")}`,
          about: { "@type": "Organization", name: DEVELOPER_NAME, email: CONTACT_EMAIL },
        }}
      />
      <section className="page-hero compact">
        <div className="container">
          <p className="eyebrow">{t.nav.contact}</p>
          <h1>{t.meta.contactTitle}</h1>
          <p className="lead">{t.pages.contactLead}</p>
        </div>
      </section>
      <section className="container section contact-grid">
        <div className="card">
          <h2>{t.pages.contactFormTitle}</h2>
          <ContactForm locale={lang} />
        </div>
        <aside className="contact-aside" aria-label={t.meta.contactTitle}>
          <div className="card">
            <h2>{t.pages.emailUs}</h2>
            <p>
              <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>
            </p>
            <p className="meta">{t.pages.responseTime}</p>
          </div>
          <div className="card">
            <h2>{t.nav.support}</h2>
            <p>{t.pages.contactSupportHint}</p>
            <Link href={href(lang, "support")}>{t.pages.contactSupportLink}</Link>
          </div>
        </aside>
      </section>
    </>
  );
}
