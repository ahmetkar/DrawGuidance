// v42 güvenlik yardımcıları (sunucuya özgü bağımlılık yok; birim testleri bunları doğrudan dener).
import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import { z } from "zod";

/** Oturum jetonu: 32 bayt rastgele, base64url (çerezde); veritabanında yalnız SHA-256 özeti saklanır. */
export function newToken(): string {
  return randomBytes(32).toString("base64url");
}

export function sha256(value: string): string {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

/** Sabit sürede karşılaştırma (kurulum anahtarı gibi sırlar için). */
export function safeEqual(a: string, b: string): boolean {
  const ab = Buffer.from(a, "utf8");
  const bb = Buffer.from(b, "utf8");
  if (ab.length !== bb.length) {
    // Uzunluk farkı da sabit sürede: kendisiyle karşılaştırılıp false döner
    timingSafeEqual(ab, ab);
    return false;
  }
  return timingSafeEqual(ab, bb);
}

/** IP adresinin tuzlu özeti (adres saklanmaz). */
export function ipHash(ip: string, salt: string): string {
  return sha256(`${salt}:${ip}`).slice(0, 32);
}

/** İsteği yapanın IP'si (Vercel: x-forwarded-for'un ilk değeri; yoksa x-real-ip). */
export function clientIp(headers: { get(name: string): string | null }): string {
  const fwd = headers.get("x-forwarded-for");
  if (fwd) return fwd.split(",")[0]!.trim().slice(0, 64);
  return (headers.get("x-real-ip") || "unknown").trim().slice(0, 64);
}

/**
 * Değiştiren isteklerde köken denetimi (CSRF): Origin başlığı sitenin kökeniyle aynı olmalı. Origin yoksa
 * (eski tarayıcılar) Referer'ın kökenine bakılır; ikisi de yoksa reddedilir.
 */
export function sameOrigin(headers: { get(name: string): string | null }, expected: string[]): boolean {
  const origin = headers.get("origin") || refererOrigin(headers.get("referer"));
  if (!origin) return false;
  return expected.includes(origin);
}

function refererOrigin(referer: string | null): string | null {
  if (!referer) return null;
  try {
    return new URL(referer).origin;
  } catch {
    return null;
  }
}

/** Kontrol karakterlerini atar (satır sonu ve sekme kalır). */
export function clean(value: string): string {
  return value.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F\u202A-\u202E\u2066-\u2069]/g, "").trim();
}

/** İletişim formu (web sitesi): ad, e-posta, konu, mesaj; "website" gizli tuzak alanı boş olmalı. */
export const contactSchema = z.object({
  name: z.string().transform(clean).pipe(z.string().min(2).max(100)),
  email: z.string().transform(clean).pipe(z.email().max(254)),
  subject: z.string().transform(clean).pipe(z.string().min(2).max(150)),
  message: z.string().transform(clean).pipe(z.string().min(10).max(5000)),
  locale: z.enum(["tr", "en"]),
  website: z.string().max(0).optional().or(z.literal("")),
});

export type ContactInput = z.infer<typeof contactSchema>;

/** E-posta başlığı enjeksiyonuna karşı: satır sonu içeren tek satırlık alan reddedilir. */
export function singleLine(value: string): boolean {
  return !/[\r\n]/.test(value);
}

/** Kurulum anahtarı yeterince güçlü mü (en az 32 karakter). */
export function strongSecret(value: string | undefined): value is string {
  return typeof value === "string" && value.trim().length >= 32;
}
