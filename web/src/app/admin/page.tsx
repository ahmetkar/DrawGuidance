import type { Metadata } from "next";
import Link from "next/link";
import { db } from "@/lib/server/db";
import { requireAdmin } from "@/lib/server/session";
import { AdminShell } from "./AdminShell";

export const metadata: Metadata = { title: "Özet" };
export const dynamic = "force-dynamic";

export default async function AdminHome() {
  const admin = await requireAdmin();
  const sql = db()!;
  const [counts] = (await sql`
    SELECT
      (SELECT count(*)::int FROM contact_messages WHERE read_at IS NULL) AS unread,
      (SELECT count(*)::int FROM contact_messages) AS messages,
      (SELECT count(*)::int FROM site_content) AS edited,
      (SELECT count(*)::int FROM admin_credentials WHERE user_id = ${admin.userId}) AS passkeys
  `) as { unread: number; messages: number; edited: number; passkeys: number }[];
  const recent = (await sql`
    SELECT action, detail, created_at FROM admin_audit ORDER BY created_at DESC LIMIT 8
  `) as { action: string; detail: string; created_at: string }[];
  return (
    <AdminShell name={admin.name} active="home">
      <h1>Hoş geldiniz, {admin.name}</h1>
      <div className="card-grid four">
        <Link className="stat-card" href="/admin/messages">
          <strong>{counts!.unread}</strong>
          <span>Okunmamış mesaj</span>
        </Link>
        <Link className="stat-card" href="/admin/messages">
          <strong>{counts!.messages}</strong>
          <span>Toplam mesaj</span>
        </Link>
        <Link className="stat-card" href="/admin/content/home">
          <strong>{counts!.edited}</strong>
          <span>Düzenlenen sayfa</span>
        </Link>
        <Link className="stat-card" href="/admin/security">
          <strong>{counts!.passkeys}</strong>
          <span>Passkey</span>
        </Link>
      </div>
      <section className="card">
        <h2>Son işlemler</h2>
        {recent.length === 0 ? (
          <p className="meta">Henüz işlem yok.</p>
        ) : (
          <ul className="plain">
            {recent.map((r, i) => (
              <li key={i}>
                <code>{r.action}</code> {r.detail} <span className="meta">· {new Date(r.created_at).toLocaleString("tr-TR")}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </AdminShell>
  );
}
