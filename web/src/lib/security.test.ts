// v42 web güvenlik yardımcılarının testleri: node --experimental-strip-types --test
import assert from "node:assert/strict";
import { test } from "node:test";
import { clean, clientIp, contactSchema, ipHash, newToken, safeEqual, sameOrigin, sha256, singleLine, strongSecret } from "./security.ts";

const headers = (h: Record<string, string>) => ({ get: (n: string) => h[n.toLowerCase()] ?? null });

test("oturum jetonu rastgele ve yeterince uzun; veritabanında yalnız özeti", () => {
  const a = newToken();
  const b = newToken();
  assert.notEqual(a, b);
  assert.ok(a.length >= 43 && /^[A-Za-z0-9_-]+$/.test(a));
  assert.equal(sha256("abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
});

test("sabit sürede karşılaştırma ve güçlü kurulum anahtarı", () => {
  assert.ok(safeEqual("gizli-anahtar", "gizli-anahtar"));
  assert.ok(!safeEqual("gizli-anahtar", "gizli-anahtaR"));
  assert.ok(!safeEqual("kisa", "daha-uzun-bir-deger"));
  assert.ok(!strongSecret("kisa"));
  assert.ok(!strongSecret(undefined));
  assert.ok(strongSecret("x".repeat(32)));
});

test("köken denetimi (CSRF): yalnız sitenin kökeni", () => {
  const ok = ["https://drawguidance.ahmetkar.com"];
  assert.ok(sameOrigin(headers({ origin: "https://drawguidance.ahmetkar.com" }), ok));
  assert.ok(!sameOrigin(headers({ origin: "https://evil.example" }), ok));
  assert.ok(sameOrigin(headers({ referer: "https://drawguidance.ahmetkar.com/admin/login" }), ok));
  assert.ok(!sameOrigin(headers({ referer: "https://drawguidance.ahmetkar.com.evil.example/x" }), ok));
  assert.ok(!sameOrigin(headers({}), ok));
});

test("IP: ilk iletilen adres, tuzlu özet (adres saklanmaz)", () => {
  assert.equal(clientIp(headers({ "x-forwarded-for": "203.0.113.5, 10.0.0.1" })), "203.0.113.5");
  assert.equal(clientIp(headers({ "x-real-ip": "198.51.100.7" })), "198.51.100.7");
  const h = ipHash("203.0.113.5", "tuz");
  assert.equal(h.length, 32);
  assert.ok(!h.includes("203"));
  assert.notEqual(h, ipHash("203.0.113.5", "baska-tuz"));
});

test("iletişim formu doğrulaması: kontrol karakterleri, uzunluk, e-posta, tuzak alanı", () => {
  const good = { name: "Ahmet", email: "a@example.com", subject: "Merhaba", message: "Uygulama hakkında bir sorum var.", locale: "tr", website: "" };
  assert.ok(contactSchema.safeParse(good).success);
  assert.ok(!contactSchema.safeParse({ ...good, email: "gecersiz" }).success);
  assert.ok(!contactSchema.safeParse({ ...good, message: "kısa" }).success);
  assert.ok(!contactSchema.safeParse({ ...good, message: "x".repeat(5001) }).success);
  assert.ok(!contactSchema.safeParse({ ...good, locale: "de" }).success);
  assert.ok(!contactSchema.safeParse({ ...good, website: "http://spam" }).success);
  const parsed = contactSchema.parse({ ...good, name: "  Ah\u0000met\u202E " });
  assert.equal(parsed.name, "Ahmet");
  assert.equal(clean("a\nb\tc\u0007"), "a\nb\tc");
  assert.ok(singleLine("Konu"));
  assert.ok(!singleLine("Konu\r\nBcc: x@y"));
});

test("XSS: iletişim alanları HTML'i reddetmez ama metin olarak saklanır (React kaçırır)", () => {
  const parsed = contactSchema.parse({ name: "<b>Ad</b>", email: "a@example.com", subject: "<script>x</script>", message: "<img src=x onerror=alert(1)> merhaba", locale: "en" });
  assert.equal(parsed.subject, "<script>x</script>");
});
