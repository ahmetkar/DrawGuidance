import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { SupportForm } from "@/components/SupportForm";
import { CONTACT_EMAIL, DEVELOPER_NAME } from "@/lib/config";
import { getDictionary, href, isLocale } from "@/lib/i18n";
import { pageMetadata } from "@/lib/seo";

export async function generateMetadata({ params }: PageProps<"/[lang]/support">): Promise<Metadata> {
  const { lang } = await params;
  if (!isLocale(lang)) return {};
  const t = getDictionary(lang);
  return pageMetadata(lang, "support", t.meta.supportTitle, `${t.appName}: ${t.meta.supportTitle}`);
}

export default async function SupportPage({ params }: PageProps<"/[lang]/support">) {
  const { lang } = await params;
  if (!isLocale(lang)) notFound();
  const t = getDictionary(lang);
  const tr = lang === "tr";
  return (
    <article>
      <h1>{t.meta.supportTitle}</h1>
      <p className="lead">
        {tr
          ? `${t.appName} hakkında soru, öneri, hata bildirimi ya da gizlilik talepleriniz için bize yazın. ${DEVELOPER_NAME} ekibi e-posta ile yanıt verir.`
          : `Write to us with questions, suggestions, bug reports or privacy requests about ${t.appName}. The ${DEVELOPER_NAME} team replies by email.`}
      </p>
      <ul>
        <li>
          {tr ? "Hesabınızı silmek mi istiyorsunuz? " : "Want to delete your account? "}
          <Link href={href(lang, "delete-account")}>{t.meta.deleteTitle}</Link>
        </li>
        <li>
          {tr
            ? "Uygulamadaki bir hatayı bildirmek için uygulamada Ayarlar > Hata raporları > Sorun bildir düğmesini de kullanabilirsiniz; cihaz bilgileri rapora otomatik eklenir."
            : "To report a bug in the app you can also use Settings > Error reports > Report a problem in the app; device details are added to the report automatically."}
        </li>
      </ul>

      <section className="card" aria-labelledby="support-form-title">
        <h2 id="support-form-title">{t.support.formTitle}</h2>
        <SupportForm locale={lang} />
        {CONTACT_EMAIL && (
          <p className="meta">
            {t.support.emailAlt} <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>
          </p>
        )}
      </section>
      <p className="meta">
        {tr
          ? "Gönderdiğiniz e-posta adresi ve mesaj yalnızca talebinizi yanıtlamak için kullanılır ve 2 yıl saklanır. "
          : "Your email address and message are used only to answer your request and are kept for 2 years. "}
        <Link href={href(lang, "privacy")}>{t.nav.privacy}</Link>
      </p>
    </article>
  );
}
