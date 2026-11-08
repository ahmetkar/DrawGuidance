import Link from "next/link";
import { BrandMark } from "@/components/BrandMark";
import { logout } from "./actions";

/** Oturumlu yönetim sayfalarının çerçevesi: yan menü (dar ekranda üstte) ve çıkış. */
export function AdminShell({ name, active, children }: { name: string; active: string; children: React.ReactNode }) {
  const items = [
    { href: "/admin", key: "home", label: "Özet" },
    { href: "/admin/content/home", key: "content-home", label: "Ana sayfa" },
    { href: "/admin/content/about", key: "content-about", label: "Hakkımızda" },
    { href: "/admin/content/download", key: "content-download", label: "İndirmeler" },
    { href: "/admin/images", key: "images", label: "Görseller" },
    { href: "/admin/messages", key: "messages", label: "Mesajlar" },
    { href: "/admin/security", key: "security", label: "Güvenlik" },
  ];
  return (
    <div className="admin">
      <aside className="admin-side">
        <div className="brand">
          <BrandMark />
          <span>Yönetim</span>
        </div>
        <nav aria-label="Yönetim menüsü">
          <ul>
            {items.map((i) => (
              <li key={i.key}>
                <Link href={i.href} aria-current={active === i.key ? "page" : undefined}>
                  {i.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
        <div className="admin-user">
          <span>{name}</span>
          <form action={logout}>
            <button className="button secondary small" type="submit">
              Çıkış
            </button>
          </form>
          <a href="/tr" target="_blank" rel="noopener">
            Siteyi aç ↗
          </a>
        </div>
      </aside>
      <main className="admin-main" id="main">
        {children}
      </main>
    </div>
  );
}
