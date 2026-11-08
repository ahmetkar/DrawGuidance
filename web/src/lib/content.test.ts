// v42 yönetim paneli içerik şemaları ve form dönüşümü.
import assert from "node:assert/strict";
import { test } from "node:test";
import { fieldsOf, readForm } from "./contentForm.ts";
import { CONTENT_KEYS, CONTENT_LOCALES, DEFAULTS, imageSchema, isSafeUrl, parseContent, schemas } from "./contentSchema.ts";

test("varsayılan içerik iki dilde de şemaya uyar", () => {
  for (const l of CONTENT_LOCALES) for (const k of CONTENT_KEYS) assert.ok(schemas[k].safeParse(DEFAULTS[l][k]).success, `${l}/${k}`);
});

test("güvenli adres: yalnız https ya da kök göreli", () => {
  assert.ok(isSafeUrl("https://play.google.com/store/apps/details?id=x"));
  assert.ok(isSafeUrl("/tr/download"));
  for (const bad of ["javascript:alert(1)", "data:text/html,<b>", "http://example.com", "//evil.example/x", "https://u:p@x.com", "vbscript:x", "https://x.com/\"onerror"]) {
    assert.ok(!isSafeUrl(bad), bad);
  }
  assert.ok(!imageSchema.safeParse({ url: "javascript:alert(1)", altTr: "", altEn: "" }).success);
  assert.ok(imageSchema.safeParse({ url: "", altTr: "", altEn: "" }).success);
});

test("form alanları şablondan üretilir ve aynı yapıya okunur", () => {
  const tpl = DEFAULTS.tr.download;
  const fields = fieldsOf(tpl, tpl);
  const map = new Map(fields.map((f) => [f.path, f]));
  assert.equal(map.get("android.url")?.kind, "url");
  assert.equal(map.get("android.available")?.kind, "checkbox");
  assert.equal(map.get("intro")?.kind, "textarea");
  const data: Record<string, string> = {};
  for (const f of fields) if (f.kind !== "checkbox") data[f.path] = String(f.value);
  data["android.available"] = "on";
  data["title"] = "Yeni başlık";
  data["__proto__.polluted"] = "x";
  data["constructor.prototype.x"] = "y";
  const out = readForm(tpl, (n) => data[n] ?? null) as typeof tpl;
  assert.equal(out.title, "Yeni başlık");
  assert.equal(out.android.available, true);
  assert.equal(out.windows.available, false);
  assert.ok(schemas.download.safeParse(out).success);
  assert.equal(({} as Record<string, unknown>).polluted, undefined);
  assert.equal(Object.keys(out).sort().join(","), Object.keys(tpl).sort().join(","));
});

test("bozuk kayıt varsayılana döner; zararlı adres reddedilir", () => {
  assert.deepEqual(parseContent("about", "en", { title: 1 }), DEFAULTS.en.about);
  const bad = structuredClone(DEFAULTS.tr.download);
  bad.android.url = "javascript:alert(1)";
  assert.ok(!schemas.download.safeParse(bad).success);
  assert.deepEqual(parseContent("download", "tr", bad), DEFAULTS.tr.download);
  const long = structuredClone(DEFAULTS.tr.home);
  long.heroTitle = "x".repeat(121);
  assert.ok(!schemas.home.safeParse(long).success);
});
