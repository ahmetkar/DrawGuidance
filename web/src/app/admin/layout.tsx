import type { Metadata, Viewport } from "next";
import "../globals.css";

// v42 yönetim paneli (ayrı kök düzen): arama motorlarına kapalı, önbelleğe alınmaz, yalnız passkey ile girilir.
export const metadata: Metadata = {
  title: { default: "Yönetim paneli", template: "%s · Yönetim" },
  robots: { index: false, follow: false, nocache: true, googleBot: { index: false, follow: false } },
};

export const viewport: Viewport = { width: "device-width", initialScale: 1 };

export default function AdminRootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="tr">
      <body className="admin-body">{children}</body>
    </html>
  );
}
