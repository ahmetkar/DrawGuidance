import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { BrandMark } from "@/components/BrandMark";
import { dbConfigured } from "@/lib/server/db";
import { adminExists, currentAdmin } from "@/lib/server/session";
import { PasskeyLogin } from "../Passkey";

export const metadata: Metadata = { title: "Giriş" };
export const dynamic = "force-dynamic";

export default async function AdminLoginPage() {
  if (await currentAdmin()) redirect("/admin");
  const configured = dbConfigured();
  const exists = configured && (await adminExists());
  return (
    <main className="admin-auth">
      <div className="card auth-card">
        <div className="brand">
          <BrandMark />
          <span>Çizim Rehberi · Yönetim</span>
        </div>
        <h1>Yönetim paneline giriş</h1>
        {!configured ? (
          <p className="status error">Veritabanı bağlı değil: Vercel&apos;de DATABASE_URL (Neon) tanımlayın ve npm run db:migrate çalıştırın.</p>
        ) : exists ? (
          <>
            <p className="meta">Bu cihazdaki ya da telefonunuzdaki passkey ile (Face ID, parmak izi, Windows Hello, güvenlik anahtarı) giriş yapın. Parola kullanılmaz.</p>
            <PasskeyLogin />
          </>
        ) : (
          <p>
            Henüz yönetici yok. <Link href="/admin/setup">İlk kurulumu yapın</Link>.
          </p>
        )}
      </div>
    </main>
  );
}
