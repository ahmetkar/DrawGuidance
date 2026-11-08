import type { Metadata, Viewport } from "next";
import { notFound } from "next/navigation";
import "../globals.css";
import { SiteFooter } from "@/components/SiteFooter";
import { SiteHeader } from "@/components/SiteHeader";
import { JsonLd } from "@/components/JsonLd";
import { CONTACT_EMAIL, DEVELOPER_NAME, SITE_URL } from "@/lib/config";
import { getDictionary, isLocale, LOCALES } from "@/lib/i18n";

// Yalnız tr ve en; başka dil kodu 404
export const dynamicParams = false;

export function generateStaticParams() {
  return LOCALES.map((lang) => ({ lang }));
}

export async function generateMetadata({ params }: LayoutProps<"/[lang]">): Promise<Metadata> {
  const { lang } = await params;
  if (!isLocale(lang)) return {};
  const t = getDictionary(lang);
  return {
    metadataBase: new URL(SITE_URL),
    title: { default: t.meta.homeTitle, template: `%s · ${t.appName}` },
    description: t.meta.homeDescription,
    applicationName: t.appName,
    robots: { index: true, follow: true, googleBot: { index: true, follow: true, "max-image-preview": "large", "max-snippet": -1 } },
    authors: [{ name: DEVELOPER_NAME }],
    creator: DEVELOPER_NAME,
    publisher: DEVELOPER_NAME,
    category: "technology",
    formatDetection: { email: false, telephone: false, address: false },
  };
}

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f7f6fb" },
    { media: "(prefers-color-scheme: dark)", color: "#14131a" },
  ],
};

export default async function RootLayout({ children, params }: LayoutProps<"/[lang]">) {
  const { lang } = await params;
  if (!isLocale(lang)) notFound();
  const t = getDictionary(lang);
  return (
    <html lang={lang}>
      <body>
        <a className="skip-link" href="#main">
          {t.skipToContent}
        </a>
        <JsonLd
          data={{
            "@context": "https://schema.org",
            "@graph": [
              {
                "@type": "Organization",
                "@id": `${SITE_URL}/#org`,
                name: DEVELOPER_NAME,
                url: SITE_URL,
                logo: `${SITE_URL}/icon.svg`,
                email: CONTACT_EMAIL,
              },
              {
                "@type": "WebSite",
                "@id": `${SITE_URL}/#website`,
                url: SITE_URL,
                name: t.appName,
                inLanguage: lang === "tr" ? "tr-TR" : "en-US",
                publisher: { "@id": `${SITE_URL}/#org` },
              },
            ],
          }}
        />
        <SiteHeader locale={lang} />
        <main id="main" tabIndex={-1}>
          {children}
        </main>
        <SiteFooter locale={lang} />
      </body>
    </html>
  );
}
