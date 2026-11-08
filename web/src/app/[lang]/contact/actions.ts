"use server";

import { contactSchema, singleLine } from "@/lib/security";
import { db } from "@/lib/server/db";
import { rateLimit, requestInfo } from "@/lib/server/guard";

// v42 iletişim formu (web sitesinin kendi veritabanında; yönetim panelinde okunur). Uygulamanın sunucusuna gitmez.

export type ContactState = { status: "idle" | "ok" | "error"; error?: "validation" | "rate_limited" | "unavailable" | "generic"; fields?: string[] };

export async function sendContact(_prev: ContactState, form: FormData): Promise<ContactState> {
  const info = await requestInfo();
  // Server Action'lar Next.js'te köken denetimlidir; yine de aynı köken değilse reddedilir
  if (!info.sameOrigin) return { status: "error", error: "generic" };
  const raw = {
    name: String(form.get("name") ?? ""),
    email: String(form.get("email") ?? ""),
    subject: String(form.get("subject") ?? ""),
    message: String(form.get("message") ?? ""),
    locale: String(form.get("locale") ?? "tr"),
    website: String(form.get("website") ?? ""),
  };
  // Gizli tuzak alanı dolduysa (bot) sessizce başarılı görünür, kaydedilmez
  if (raw.website) return { status: "ok" };
  const parsed = contactSchema.safeParse(raw);
  if (!parsed.success || !singleLine(parsed.data.name) || !singleLine(parsed.data.subject)) {
    const fields = parsed.success ? ["name"] : [...new Set(parsed.error.issues.map((i) => String(i.path[0])))];
    return { status: "error", error: "validation", fields };
  }
  const sql = db();
  if (!sql) return { status: "error", error: "unavailable" };
  try {
    if (!(await rateLimit(`contact:${info.ipHash}`, 5, 3600))) return { status: "error", error: "rate_limited" };
    const d = parsed.data;
    await sql`
      INSERT INTO contact_messages (name, email, subject, message, locale, ip_hash, user_agent)
      VALUES (${d.name}, ${d.email}, ${d.subject}, ${d.message}, ${d.locale}, ${info.ipHash}, ${info.userAgent})
    `;
    return { status: "ok" };
  } catch {
    return { status: "error", error: "generic" };
  }
}
