"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useId, useState } from "react";
import { getDictionary, href, type Locale } from "@/lib/i18n";
import { BrandMark } from "./BrandMark";

/**
 * v42 üst bölüm: marka, menü (Ana sayfa, Hakkımızda, İndir, İletişim, Destek), dil değiştirici ve "İndir" düğmesi.
 * Dar ekranda menü düğmeyle açılır (aria-expanded; Esc ve sayfa değişince kapanır).
 */
export function SiteHeader({ locale }: { locale: Locale }) {
  const t = getDictionary(locale);
  const pathname = usePathname() || `/${locale}`;
  // Menü yalnız açıldığı sayfada açık kalır (sayfa değişince kendiliğinden kapanır)
  const [openAt, setOpenAt] = useState<string | null>(null);
  const open = openAt === pathname;
  const setOpen = (v: boolean | ((prev: boolean) => boolean)) =>
    setOpenAt((prev) => ((typeof v === "function" ? v(prev === pathname) : v) ? pathname : null));
  const menuId = useId();
  const other: Locale = locale === "tr" ? "en" : "tr";
  const otherPath = pathname.replace(/^\/(tr|en)(?=\/|$)/, `/${other}`);
  const items = [
    { page: "", label: t.nav.home },
    { page: "about", label: t.nav.about },
    { page: "download", label: t.nav.download },
    { page: "contact", label: t.nav.contact },
    { page: "support", label: t.nav.support },
  ] as const;

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpenAt(null);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  return (
    <header className="site-header">
      <div className="container header-row">
        <Link href={href(locale, "")} className="brand" aria-current={pathname === `/${locale}` ? "page" : undefined}>
          <BrandMark />
          <span>{t.appName}</span>
        </Link>
        <button
          type="button"
          className="menu-toggle"
          aria-expanded={open}
          aria-controls={menuId}
          aria-label={open ? t.nav.closeMenu : t.nav.openMenu}
          onClick={() => setOpen((v) => !v)}
        >
          <span aria-hidden="true" className={open ? "burger open" : "burger"} />
        </button>
        <nav id={menuId} className={open ? "site-nav open" : "site-nav"} aria-label={t.nav.menu}>
          <ul>
            {items.map((item) => {
              const target = href(locale, item.page);
              return (
                <li key={item.page || "home"}>
                  <Link href={target} aria-current={pathname === target ? "page" : undefined}>
                    {item.label}
                  </Link>
                </li>
              );
            })}
            <li>
              <Link href={otherPath} hrefLang={other} lang={other} className="lang-switch">
                {t.otherLang}
              </Link>
            </li>
            <li className="nav-cta">
              <Link href={href(locale, "download")} className="button small">
                {t.nav.download}
              </Link>
            </li>
          </ul>
        </nav>
      </div>
    </header>
  );
}
