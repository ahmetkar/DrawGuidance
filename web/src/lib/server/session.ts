import "server-only";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { newToken, sha256 } from "@/lib/security";
import { db } from "./db";

// v42 yönetici oturumu: 32 baytlık rastgele jeton yalnız HttpOnly, Secure, SameSite=Strict çerezde; veritabanında
// jetonun SHA-256 özeti. Oturum en çok 8 saat sürer, 30 dakika işlem yapılmazsa düşer; çıkışta silinir.

const PROD = process.env.NODE_ENV === "production";
/** __Host- öneki: yalnız HTTPS, Path=/ ve alan adı yok (alt alan adları çerezi göremez/değiştiremez). */
export const SESSION_COOKIE = PROD ? "__Host-dg_admin" : "dg_admin";
const ABSOLUTE_HOURS = 8;
const IDLE_MINUTES = 30;

export type Admin = { userId: string; name: string; tokenHash: string };

export async function createSession(userId: string, ipHash: string, userAgent: string): Promise<void> {
  const sql = db();
  if (!sql) throw new Error("db");
  const token = newToken();
  const hash = sha256(token);
  await sql`
    INSERT INTO admin_sessions (token_hash, user_id, expires_at, ip_hash, user_agent)
    VALUES (${hash}, ${userId}, now() + make_interval(hours => ${ABSOLUTE_HOURS}), ${ipHash}, ${userAgent})
  `;
  // Eski oturumlar temizlenir
  await sql`DELETE FROM admin_sessions WHERE expires_at < now() OR last_seen_at < now() - make_interval(mins => ${IDLE_MINUTES})`;
  const jar = await cookies();
  jar.set(SESSION_COOKIE, token, {
    httpOnly: true,
    secure: PROD,
    sameSite: "strict",
    path: "/",
    maxAge: ABSOLUTE_HOURS * 3600,
    priority: "high",
  });
}

/** Geçerli yönetici (oturum yoksa ya da süresi dolduysa null). Her istekte son görülme güncellenir. */
export async function currentAdmin(): Promise<Admin | null> {
  const sql = db();
  if (!sql) return null;
  const jar = await cookies();
  const token = jar.get(SESSION_COOKIE)?.value;
  if (!token || token.length < 40 || token.length > 64) return null;
  const hash = sha256(token);
  const rows = (await sql`
    UPDATE admin_sessions s SET last_seen_at = now()
    FROM admin_users u
    WHERE s.token_hash = ${hash} AND u.id = s.user_id
      AND s.expires_at > now() AND s.last_seen_at > now() - make_interval(mins => ${IDLE_MINUTES})
    RETURNING s.user_id, u.name
  `) as { user_id: string; name: string }[];
  const row = rows[0];
  return row ? { userId: row.user_id, name: row.name, tokenHash: hash } : null;
}

/** Yönetim sayfaları ve işlemleri için: oturum yoksa giriş sayfasına. */
export async function requireAdmin(): Promise<Admin> {
  const admin = await currentAdmin();
  if (!admin) redirect("/admin/login");
  return admin;
}

export async function destroySession(): Promise<void> {
  const sql = db();
  const jar = await cookies();
  const token = jar.get(SESSION_COOKIE)?.value;
  if (sql && token) await sql`DELETE FROM admin_sessions WHERE token_hash = ${sha256(token)}`;
  jar.delete(SESSION_COOKIE);
}

/** Yönetici var mı (yoksa ilk kurulum sayfası açılır). */
export async function adminExists(): Promise<boolean> {
  const sql = db();
  if (!sql) return false;
  const rows = (await sql`SELECT 1 FROM admin_users LIMIT 1`) as unknown[];
  return rows.length > 0;
}
