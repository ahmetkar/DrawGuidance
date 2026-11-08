import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { BrandMark } from "@/components/BrandMark";
import { strongSecret } from "@/lib/security";
import { dbConfigured } from "@/lib/server/db";
import { adminExists } from "@/lib/server/session";
import { PasskeyRegister } from "../Passkey";

export const metadata: Metadata = { title: "İlk kurulum" };
export const dynamic = "force-dynamic";

/** İlk kurulum: yalnız yönetici yokken ve ADMIN_SETUP_TOKEN (en az 32 karakter) tanımlıyken açılır. */
export default async function AdminSetupPage() {
  if (!dbConfigured()) redirect("/admin/login");
  if (await adminExists()) redirect("/admin/login");
  if (!strongSecret(process.env.ADMIN_SETUP_TOKEN)) notFound();
  return (
    <main className="admin-auth">
      <div className="card auth-card">
        <div className="brand">
          <BrandMark />
          <span>Çizim Rehberi · Yönetim</span>
        </div>
        <h1>İlk kurulum</h1>
        <p className="meta">
          Vercel&apos;deki kurulum anahtarını girin ve bu cihazda bir passkey oluşturun. Kurulumdan sonra bu sayfa kapanır;
          güvenlik için ADMIN_SETUP_TOKEN değişkenini Vercel&apos;den silebilirsiniz.
        </p>
        <PasskeyRegister setup />
      </div>
    </main>
  );
}
