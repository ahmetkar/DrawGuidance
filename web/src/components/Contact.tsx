import Link from "next/link";
import { CONTACT_EMAIL } from "@/lib/config";
import { href, type Locale } from "@/lib/i18n";

/** İletişim yolu: destek formu ve (ayarlıysa) e-posta. */
export function Contact({ locale }: { locale: Locale }) {
  const form = (
    <Link href={href(locale, "support")}>{locale === "tr" ? "destek ve iletişim formu" : "support and contact form"}</Link>
  );
  if (!CONTACT_EMAIL) return form;
  return (
    <>
      {form}
      {locale === "tr" ? " ya da " : " or "}
      <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>
    </>
  );
}
