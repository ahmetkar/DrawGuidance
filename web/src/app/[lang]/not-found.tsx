"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { getDictionary, isLocale, type Locale } from "@/lib/i18n";

/** Dil altındaki bilinmeyen sayfalar için 404 (site düzeni içinde). */
export default function NotFound() {
  const params = useParams<{ lang?: string }>();
  const locale: Locale = params?.lang && isLocale(params.lang) ? params.lang : "tr";
  const t = getDictionary(locale);
  return (
    <article className="container narrow doc">
      <h1>{t.notFound.title}</h1>
      <p>{t.notFound.text}</p>
      <div className="button-row">
        <Link className="button" href={`/${locale}`}>
          {t.notFound.back}
        </Link>
      </div>
    </article>
  );
}
