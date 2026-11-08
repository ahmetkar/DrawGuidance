import Link from "next/link";
import { CONTACT_EMAIL, DEVELOPER_NAME } from "@/lib/config";
import { getDictionary, href, type Locale } from "@/lib/i18n";
import { BrandMark } from "./BrandMark";

/** v42 alt bölüm: marka ve kısa tanıtım, ürün / kurumsal / yasal sütunları. */
export function SiteFooter({ locale }: { locale: Locale }) {
  const t = getDictionary(locale);
  const cols = [
    {
      title: t.footer.product,
      links: [
        { page: "", label: t.nav.home },
        { page: "download", label: t.nav.download },
        { page: "support", label: t.nav.support },
      ],
    },
    {
      title: t.footer.company,
      links: [
        { page: "about", label: t.nav.about },
        { page: "contact", label: t.nav.contact },
      ],
    },
    {
      title: t.footer.legal,
      links: [
        { page: "privacy", label: t.nav.privacy },
        { page: "terms", label: t.nav.terms },
        { page: "delete-account", label: t.nav.deleteAccount },
      ],
    },
  ] as const;
  return (
    <footer className="site-footer">
      <div className="container footer-grid">
        <div className="footer-brand">
          <Link href={href(locale, "")} className="brand">
            <BrandMark />
            <span>{t.appName}</span>
          </Link>
          <p>{t.footer.blurb}</p>
          <p>
            <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>
          </p>
        </div>
        {cols.map((c) => (
          <nav key={c.title} aria-label={c.title} className="footer-col">
            <h2>{c.title}</h2>
            <ul>
              {c.links.map((l) => (
                <li key={l.page || "home"}>
                  <Link href={href(locale, l.page)}>{l.label}</Link>
                </li>
              ))}
            </ul>
          </nav>
        ))}
      </div>
      <div className="container footer-bottom">
        <p>
          © 2026 {DEVELOPER_NAME}. {t.footer.rights} · {t.footer.developer}: {DEVELOPER_NAME}
        </p>
        <p>{t.footer.noTracking}</p>
      </div>
    </footer>
  );
}
