import type { SiteImage as SiteImageData } from "@/lib/contentSchema";
import type { Locale } from "@/lib/i18n";

/**
 * v42 yönetim panelinden değiştirilebilen görsel. Adres verilmediyse yer tutucu (degrade zemin, simge ve "Görsel
 * yakında"). Dış görseller düz <img> ile (boyutlu, tembel yükleme); adres panelde yalnız https olarak kabul edilir.
 */
export function SiteImage({
  image,
  locale,
  label,
  placeholder,
  width,
  height,
  priority = false,
  className,
}: {
  image: SiteImageData;
  locale: Locale;
  label: string;
  placeholder: string;
  width: number;
  height: number;
  priority?: boolean;
  className?: string;
}) {
  const alt = (locale === "tr" ? image.altTr : image.altEn) || label;
  if (image.url) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img
        className={["site-image", className].filter(Boolean).join(" ")}
        src={image.url}
        alt={alt}
        width={width}
        height={height}
        loading={priority ? "eager" : "lazy"}
        decoding="async"
        fetchPriority={priority ? "high" : "auto"}
        referrerPolicy="no-referrer"
      />
    );
  }
  return (
    <div
      className={["image-placeholder", className].filter(Boolean).join(" ")}
      role="img"
      aria-label={`${label} — ${placeholder}`}
      style={{ aspectRatio: `${width} / ${height}` }}
    >
      <svg viewBox="0 0 48 48" aria-hidden="true" focusable="false">
        <rect x="6" y="9" width="36" height="30" rx="5" fill="none" stroke="currentColor" strokeWidth="2.5" />
        <circle cx="17" cy="19" r="4" fill="currentColor" />
        <path d="M9 35l10-10 7 7 5-5 8 8" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinejoin="round" />
      </svg>
      <span>{placeholder}</span>
    </div>
  );
}
