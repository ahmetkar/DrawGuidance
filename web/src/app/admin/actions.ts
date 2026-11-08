"use server";

import { revalidatePath, updateTag } from "next/cache";
import { redirect } from "next/navigation";
import { readForm } from "@/lib/contentForm";
import { CONTENT_KEYS, CONTENT_LOCALES, DEFAULTS, IMAGE_SLOTS, imageSchema, schemas, type ContentKey, type ContentLocale } from "@/lib/contentSchema";
import { CONTENT_TAG } from "@/lib/server/content";
import { db } from "@/lib/server/db";
import { audit, requestInfo } from "@/lib/server/guard";
import { destroySession, requireAdmin } from "@/lib/server/session";

// v42 yönetim işlemleri. Her işlem: köken denetimi, oturum denetimi (veritabanından), doğrulama, parametreli sorgu
// ve işlem kaydı. Hata ayrıntısı kullanıcıya gösterilmez.

export type ActionResult = { ok: boolean; message: string; issues?: string[] };

async function guard() {
  const info = await requestInfo();
  if (!info.sameOrigin) throw new Error("origin");
  const admin = await requireAdmin();
  const sql = db();
  if (!sql) throw new Error("db");
  return { admin, sql, info };
}

function refreshSite() {
  updateTag(CONTENT_TAG);
  revalidatePath("/", "layout");
}

export async function saveContent(_prev: ActionResult, form: FormData): Promise<ActionResult> {
  const { admin, sql, info } = await guard();
  const key = String(form.get("_key")) as ContentKey;
  const locale = String(form.get("_locale")) as ContentLocale;
  if (!CONTENT_KEYS.includes(key) || !CONTENT_LOCALES.includes(locale)) return { ok: false, message: "Geçersiz sayfa." };
  const raw = readForm(DEFAULTS[locale][key], (n) => {
    const v = form.get(n);
    return typeof v === "string" ? v : null;
  });
  const parsed = schemas[key].safeParse(raw);
  if (!parsed.success) {
    return {
      ok: false,
      message: "Bazı alanlar geçersiz: boş bırakılamayan ya da çok uzun alanları ve adresleri (yalnız https) denetleyin.",
      issues: parsed.error.issues.map((i) => i.path.join(".")).slice(0, 20),
    };
  }
  const json = JSON.stringify(parsed.data);
  await sql`
    INSERT INTO site_content (locale, key, value, updated_by) VALUES (${locale}, ${key}, ${json}::jsonb, ${admin.userId})
    ON CONFLICT (locale, key) DO UPDATE SET value = EXCLUDED.value, updated_at = now(), updated_by = EXCLUDED.updated_by
  `;
  await audit(admin.userId, "content.save", `${locale}/${key}`, info.ipHash);
  refreshSite();
  return { ok: true, message: "Kaydedildi. Sitede hemen görünür." };
}

export async function resetContent(form: FormData): Promise<void> {
  const { admin, sql, info } = await guard();
  const key = String(form.get("_key")) as ContentKey;
  const locale = String(form.get("_locale")) as ContentLocale;
  if (!CONTENT_KEYS.includes(key) || !CONTENT_LOCALES.includes(locale)) return;
  await sql`DELETE FROM site_content WHERE locale = ${locale} AND key = ${key}`;
  await audit(admin.userId, "content.reset", `${locale}/${key}`, info.ipHash);
  refreshSite();
  redirect(`/admin/content/${key}?lang=${locale}&reset=1`);
}

export async function saveImages(_prev: ActionResult, form: FormData): Promise<ActionResult> {
  const { admin, sql, info } = await guard();
  const bad: string[] = [];
  const rows = IMAGE_SLOTS.map((slot) => {
    const parsed = imageSchema.safeParse({
      url: String(form.get(`${slot}.url`) ?? ""),
      altTr: String(form.get(`${slot}.altTr`) ?? ""),
      altEn: String(form.get(`${slot}.altEn`) ?? ""),
    });
    if (!parsed.success) bad.push(slot);
    return { slot, data: parsed.success ? parsed.data : null };
  });
  if (bad.length) return { ok: false, message: "Görsel adresleri https:// ile başlamalı (ya da boş bırakılmalı).", issues: bad };
  for (const { slot, data } of rows) {
    await sql`
      INSERT INTO site_images (slot, url, alt_tr, alt_en) VALUES (${slot}, ${data!.url}, ${data!.altTr}, ${data!.altEn})
      ON CONFLICT (slot) DO UPDATE SET url = EXCLUDED.url, alt_tr = EXCLUDED.alt_tr, alt_en = EXCLUDED.alt_en, updated_at = now()
    `;
  }
  await audit(admin.userId, "images.save", "", info.ipHash);
  refreshSite();
  return { ok: true, message: "Görseller kaydedildi." };
}

function messageId(form: FormData): number | null {
  const id = Number(form.get("id"));
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

export async function markMessage(form: FormData): Promise<void> {
  const { sql } = await guard();
  const id = messageId(form);
  const read = form.get("read") === "1";
  if (id) await sql`UPDATE contact_messages SET read_at = ${read ? new Date().toISOString() : null} WHERE id = ${id}`;
  revalidatePath("/admin/messages");
}

export async function deleteMessage(form: FormData): Promise<void> {
  const { admin, sql, info } = await guard();
  const id = messageId(form);
  if (id) {
    await sql`DELETE FROM contact_messages WHERE id = ${id}`;
    await audit(admin.userId, "message.delete", String(id), info.ipHash);
  }
  revalidatePath("/admin/messages");
}

export async function deletePasskey(form: FormData): Promise<void> {
  const { admin, sql, info } = await guard();
  const id = String(form.get("id") ?? "").slice(0, 1024);
  // Son anahtar silinemez (panele girilemez olurdu)
  const rows = (await sql`SELECT count(*)::int AS n FROM admin_credentials WHERE user_id = ${admin.userId}`) as { n: number }[];
  if ((rows[0]?.n ?? 0) > 1) {
    await sql`DELETE FROM admin_credentials WHERE id = ${id} AND user_id = ${admin.userId}`;
    await audit(admin.userId, "passkey.delete", id.slice(0, 16), info.ipHash);
  }
  revalidatePath("/admin/security");
}

export async function revokeSession(form: FormData): Promise<void> {
  const { admin, sql, info } = await guard();
  const hash = String(form.get("hash") ?? "");
  if (/^[0-9a-f]{64}$/.test(hash) && hash !== admin.tokenHash) {
    await sql`DELETE FROM admin_sessions WHERE token_hash = ${hash} AND user_id = ${admin.userId}`;
    await audit(admin.userId, "session.revoke", hash.slice(0, 8), info.ipHash);
  }
  revalidatePath("/admin/security");
}

export async function revokeOtherSessions(): Promise<void> {
  const { admin, sql, info } = await guard();
  await sql`DELETE FROM admin_sessions WHERE user_id = ${admin.userId} AND token_hash <> ${admin.tokenHash}`;
  await audit(admin.userId, "session.revoke_others", "", info.ipHash);
  revalidatePath("/admin/security");
}

export async function logout(): Promise<void> {
  const info = await requestInfo();
  if (info.sameOrigin) await destroySession();
  redirect("/admin/login");
}
