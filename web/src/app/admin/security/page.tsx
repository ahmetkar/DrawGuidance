import type { Metadata } from "next";
import { db } from "@/lib/server/db";
import { requireAdmin } from "@/lib/server/session";
import { deletePasskey, revokeOtherSessions, revokeSession } from "../actions";
import { AdminShell } from "../AdminShell";
import { PasskeyRegister } from "../Passkey";

export const metadata: Metadata = { title: "Güvenlik" };
export const dynamic = "force-dynamic";

export default async function SecurityPage() {
  const admin = await requireAdmin();
  const sql = db()!;
  const keys = (await sql`
    SELECT id, nickname, device_type, backed_up, created_at, last_used_at FROM admin_credentials WHERE user_id = ${admin.userId} ORDER BY created_at
  `) as { id: string; nickname: string; device_type: string; backed_up: boolean; created_at: string; last_used_at: string | null }[];
  const sessions = (await sql`
    SELECT token_hash, created_at, last_seen_at, user_agent FROM admin_sessions WHERE user_id = ${admin.userId} AND expires_at > now() ORDER BY last_seen_at DESC
  `) as { token_hash: string; created_at: string; last_seen_at: string; user_agent: string }[];
  const log = (await sql`SELECT action, detail, ip_hash, created_at FROM admin_audit ORDER BY created_at DESC LIMIT 30`) as {
    action: string;
    detail: string;
    ip_hash: string;
    created_at: string;
  }[];
  const date = (d: string | null) => (d ? new Date(d).toLocaleString("tr-TR") : "—");
  return (
    <AdminShell name={admin.name} active="security">
      <h1>Güvenlik</h1>
      <section className="card">
        <h2>Passkey&apos;ler</h2>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Ad</th>
                <th>Tür</th>
                <th>Eklendi</th>
                <th>Son kullanım</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {keys.map((k) => (
                <tr key={k.id}>
                  <td>{k.nickname || "Passkey"}</td>
                  <td>{k.device_type === "multiDevice" ? "Eşitlenen" : "Tek cihaz"}{k.backed_up ? " · yedekli" : ""}</td>
                  <td>{date(k.created_at)}</td>
                  <td>{date(k.last_used_at)}</td>
                  <td>
                    {keys.length > 1 && (
                      <form action={deletePasskey}>
                        <input type="hidden" name="id" value={k.id} />
                        <button className="button danger small" type="submit">
                          Sil
                        </button>
                      </form>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="meta">Yedek olarak ikinci bir cihazda da passkey oluşturun (anahtarı kaybederseniz panele giremezsiniz).</p>
        <PasskeyRegister setup={false} />
      </section>
      <section className="card">
        <h2>Açık oturumlar</h2>
        <ul className="plain">
          {sessions.map((s) => (
            <li key={s.token_hash}>
              {s.token_hash === admin.tokenHash ? <strong>Bu oturum</strong> : s.user_agent.slice(0, 80) || "Bilinmeyen tarayıcı"}{" "}
              <span className="meta">· son: {date(s.last_seen_at)}</span>
              {s.token_hash !== admin.tokenHash && (
                <form action={revokeSession} className="inline">
                  <input type="hidden" name="hash" value={s.token_hash} />
                  <button className="button secondary small" type="submit">
                    Kapat
                  </button>
                </form>
              )}
            </li>
          ))}
        </ul>
        {sessions.length > 1 && (
          <form action={revokeOtherSessions}>
            <button className="button secondary small" type="submit">
              Öteki oturumları kapat
            </button>
          </form>
        )}
      </section>
      <section className="card">
        <h2>İşlem kaydı</h2>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Zaman</th>
                <th>İşlem</th>
                <th>Ayrıntı</th>
                <th>IP özeti</th>
              </tr>
            </thead>
            <tbody>
              {log.map((l, i) => (
                <tr key={i}>
                  <td>{date(l.created_at)}</td>
                  <td>
                    <code>{l.action}</code>
                  </td>
                  <td>{l.detail}</td>
                  <td>
                    <code>{l.ip_hash.slice(0, 10)}</code>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </AdminShell>
  );
}
