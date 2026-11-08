import "server-only";
import { headers } from "next/headers";
import { SITE_URL } from "@/lib/config";
import { clientIp, ipHash, sameOrigin } from "@/lib/security";
import { db } from "./db";

// v42 istek denetimleri: köken (CSRF), IP özeti, istek sınırı ve yönetim kayıtları.

/** Kabul edilen kökenler: sitenin üretim adresi, Vercel önizleme adresi ve geliştirmede localhost. */
export function allowedOrigins(): string[] {
  const list = [new URL(SITE_URL).origin];
  const vercel = process.env.VERCEL_URL?.trim();
  if (vercel) list.push(`https://${vercel}`);
  const extra = process.env.ADMIN_EXTRA_ORIGINS?.split(",").map((s) => s.trim()).filter(Boolean) ?? [];
  list.push(...extra);
  if (process.env.NODE_ENV !== "production") list.push("http://localhost:3000");
  return [...new Set(list)];
}

export async function requestInfo(): Promise<{ ipHash: string; userAgent: string; sameOrigin: boolean }> {
  const h = await headers();
  const salt = process.env.IP_HASH_SALT || process.env.ADMIN_SETUP_TOKEN || "drawguidance-web";
  return {
    ipHash: ipHash(clientIp(h), salt),
    userAgent: (h.get("user-agent") || "").slice(0, 300),
    sameOrigin: sameOrigin(h, allowedOrigins()),
  };
}

/**
 * Sabit pencereli istek sınırı (veritabanında; sunucusuz işlevler arasında ortak). [limit] aşıldıysa false.
 * Veritabanı yoksa (geliştirme) sınır uygulanmaz.
 */
export async function rateLimit(key: string, limit: number, windowSeconds: number): Promise<boolean> {
  const sql = db();
  if (!sql) return true;
  const rows = (await sql`
    INSERT INTO rate_limits (key, count, window_start) VALUES (${key}, 1, now())
    ON CONFLICT (key) DO UPDATE SET
      count = CASE WHEN rate_limits.window_start < now() - make_interval(secs => ${windowSeconds}) THEN 1 ELSE rate_limits.count + 1 END,
      window_start = CASE WHEN rate_limits.window_start < now() - make_interval(secs => ${windowSeconds}) THEN now() ELSE rate_limits.window_start END
    RETURNING count
  `) as { count: number }[];
  // Eski kayıtlar ara sıra temizlenir
  if (Math.random() < 0.02) await sql`DELETE FROM rate_limits WHERE window_start < now() - interval '2 days'`;
  return (rows[0]?.count ?? 0) <= limit;
}

/** Yönetim işlemi kaydı (kim, ne, hangi IP özeti). */
export async function audit(userId: string | null, action: string, detail = "", ip = ""): Promise<void> {
  const sql = db();
  if (!sql) return;
  await sql`INSERT INTO admin_audit (user_id, action, detail, ip_hash) VALUES (${userId}, ${action}, ${detail.slice(0, 500)}, ${ip})`;
}
