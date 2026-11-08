import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DeletionRequestForm } from "@/components/DeletionRequestForm";
import { SignedInDeletion } from "@/components/SignedInDeletion";
import { DeletionDetails, DeletionIntro } from "@/content/deletion";
import { FIREBASE_CONFIG } from "@/lib/config";
import { getDictionary, isLocale } from "@/lib/i18n";
import { pageMetadata } from "@/lib/seo";

export async function generateMetadata({ params }: PageProps<"/[lang]/delete-account">): Promise<Metadata> {
  const { lang } = await params;
  if (!isLocale(lang)) return {};
  const t = getDictionary(lang);
  return pageMetadata(lang, "delete-account", t.meta.deleteTitle, `${t.appName}: ${t.meta.deleteTitle}`);
}

/**
 * Google Play'in istediği herkese açık hesap silme sayfası. Firebase web yapılandırması (NEXT_PUBLIC_FIREBASE_*)
 * eksikse oturumlu silme gizlenir; yalnız girişsiz talep formu kalır.
 */
export default async function DeleteAccountPage({ params }: PageProps<"/[lang]/delete-account">) {
  const { lang } = await params;
  if (!isLocale(lang)) notFound();
  const t = getDictionary(lang);
  return (
    <article>
      <DeletionIntro locale={lang} />

      {FIREBASE_CONFIG && (
        <section className="card" aria-labelledby="signed-in-title">
          <h2 id="signed-in-title">{t.signedIn.title}</h2>
          <p>{t.signedIn.lead}</p>
          <SignedInDeletion locale={lang} config={FIREBASE_CONFIG} />
        </section>
      )}

      <section className="card" aria-labelledby="request-title" id="request">
        <h2 id="request-title">{t.deletionForm.title}</h2>
        <p>{t.deletionForm.lead}</p>
        <DeletionRequestForm locale={lang} />
      </section>

      <DeletionDetails locale={lang} />
    </article>
  );
}
