import type { Dictionary } from "@/lib/i18n";

/** Sunucunun hata kodunu ({"error": "<kod>"}) kullanıcıya gösterilecek yazıya çevirir. */
export function errorMessage(t: Dictionary, code: string): string {
  const errors = t.form.errors as Record<string, string>;
  if (code === "bad_request" || code === "too_large") return errors.too_long;
  return errors[code] ?? errors.generic;
}
