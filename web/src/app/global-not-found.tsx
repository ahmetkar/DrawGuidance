import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

// Hiçbir yola uymayan adresler için 404 (kök düzen /[lang] altında olduğundan ayrı tam sayfa).
export const metadata: Metadata = {
  title: "Sayfa bulunamadı · Page not found",
  robots: { index: false },
};

export default function GlobalNotFound() {
  return (
    <html lang="tr">
      <body>
        <main id="main">
          <div className="container">
            <h1>Sayfa bulunamadı</h1>
            <p>Aradığınız sayfa yok ya da taşınmış olabilir.</p>
            <p lang="en">The page you are looking for does not exist or may have moved.</p>
            <div className="button-row">
              <Link className="button" href="/tr">
                Ana sayfa
              </Link>
              <Link className="button secondary" href="/en" lang="en">
                Home (English)
              </Link>
            </div>
          </div>
        </main>
      </body>
    </html>
  );
}
