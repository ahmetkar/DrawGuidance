// Sunucu uçları (tarayıcıdan çağrılır; sunucu CORS ile bu sitenin kökenine izin verir).
import { API_BASE_URL } from "./config";

/** Sunucunun ortak hata gövdesi: {"error": "<kod>", "message": "<açıklama>"}. */
export type ApiResult = { ok: true; status: number } | { ok: false; status: number; error: string };

const TIMEOUT_MS = 20_000;

async function call(path: string, init: RequestInit): Promise<ApiResult> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const res = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      signal: controller.signal,
      credentials: "omit",
      cache: "no-store",
      referrerPolicy: "strict-origin-when-cross-origin",
    });
    if (res.ok) return { ok: true, status: res.status };
    let error = "generic";
    try {
      const body = (await res.json()) as { error?: unknown };
      if (typeof body.error === "string") error = body.error;
    } catch {
      // gövde JSON değil
    }
    return { ok: false, status: res.status, error };
  } catch {
    return { ok: false, status: 0, error: "network" };
  } finally {
    clearTimeout(timer);
  }
}

function postJson(path: string, body: unknown): Promise<ApiResult> {
  return call(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

/** POST /api/v1/account/deletion-requests → 202 {"status":"received"} (yinelenen talepte de aynı). */
export function sendDeletionRequest(input: { email: string; reason?: string; locale: string }): Promise<ApiResult> {
  return postJson("/api/v1/account/deletion-requests", {
    email: input.email,
    reason: input.reason || undefined,
    locale: input.locale,
  });
}

/** POST /api/v1/support/messages → 202 {"status":"received"}. */
export function sendSupportMessage(input: { email: string; subject: string; message: string; locale: string }): Promise<ApiResult> {
  return postJson("/api/v1/support/messages", input);
}

/** DELETE /api/v1/users/me (Firebase ID jetonu) → 204: hesap ve sunucudaki veriler silinir. */
export function deleteMyAccount(idToken: string): Promise<ApiResult> {
  return call("/api/v1/users/me", { method: "DELETE", headers: { Authorization: `Bearer ${idToken}` } });
}
