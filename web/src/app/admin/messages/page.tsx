import type { Metadata } from "next";
import Link from "next/link";
import { db } from "@/lib/server/db";
import { requireAdmin } from "@/lib/server/session";
import { deleteMessage, markMessage } from "../actions";
import { AdminShell } from "../AdminShell";

export const metadata: Metadata = { title: "Mesajlar" };
export const dynamic = "force-dynamic";

type Row = { id: string; name: string; email: string; subject: string; message: string; locale: string; created_at: string; read_at: string | null };

export default async function MessagesPage({ searchParams }: PageProps<"/admin/messages">) {
  const admin = await requireAdmin();
  const sp = await searchParams;
  const page = Math.max(1, Math.min(1000, Number(sp.page) || 1));
  const unreadOnly = sp.filter === "unread";
  const sql = db()!;
  const rows = (unreadOnly
    ? await sql`SELECT id, name, email, subject, message, locale, created_at, read_at FROM contact_messages WHERE read_at IS NULL ORDER BY created_at DESC LIMIT 25 OFFSET ${(page - 1) * 25}`
    : await sql`SELECT id, name, email, subject, message, locale, created_at, read_at FROM contact_messages ORDER BY created_at DESC LIMIT 25 OFFSET ${(page - 1) * 25}`) as Row[];
  return (
    <AdminShell name={admin.name} active="messages">
      <div className="admin-head">
        <h1>İletişim mesajları</h1>
        <div className="seg">
          <Link href="/admin/messages" aria-current={!unreadOnly ? "page" : undefined}>
            Tümü
          </Link>
          <Link href="/admin/messages?filter=unread" aria-current={unreadOnly ? "page" : undefined}>
            Okunmamış
          </Link>
        </div>
      </div>
      {rows.length === 0 && <p className="meta">Mesaj yok.</p>}
      <div className="messages">
        {rows.map((m) => (
          <article key={m.id} className={m.read_at ? "card message" : "card message unread"}>
            <header>
              <h2>{m.subject}</h2>
              <p className="meta">
                {m.name} · <a href={`mailto:${encodeURIComponent(m.email)}?subject=${encodeURIComponent("Re: " + m.subject)}`}>{m.email}</a> ·{" "}
                {m.locale.toUpperCase()} · {new Date(m.created_at).toLocaleString("tr-TR")}
              </p>
            </header>
            <p className="pre">{m.message}</p>
            <div className="button-row">
              <form action={markMessage}>
                <input type="hidden" name="id" value={m.id} />
                <input type="hidden" name="read" value={m.read_at ? "0" : "1"} />
                <button className="button secondary small" type="submit">
                  {m.read_at ? "Okunmadı yap" : "Okundu yap"}
                </button>
              </form>
              <form action={deleteMessage}>
                <input type="hidden" name="id" value={m.id} />
                <button className="button danger small" type="submit">
                  Sil
                </button>
              </form>
            </div>
          </article>
        ))}
      </div>
      <nav className="pager" aria-label="Sayfalar">
        {page > 1 && <Link href={`/admin/messages?page=${page - 1}${unreadOnly ? "&filter=unread" : ""}`}>← Önceki</Link>}
        {rows.length === 25 && <Link href={`/admin/messages?page=${page + 1}${unreadOnly ? "&filter=unread" : ""}`}>Sonraki →</Link>}
      </nav>
    </AdminShell>
  );
}
