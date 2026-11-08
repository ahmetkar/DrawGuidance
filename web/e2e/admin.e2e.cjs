// v42 yönetim paneli uçtan uca denemesi (yerel): sanal passkey (Chrome DevTools WebAuthn), kurulum, içerik düzenleme,
// XSS denemesi, iletişim mesajı, çıkış/giriş, CSRF ve yanlış kurulum anahtarı.
// Kullanım: sunucu çalışırken  BASE=http://localhost:3100 SETUP=<ADMIN_SETUP_TOKEN> node e2e/admin.e2e.cjs
const { chromium } = require("playwright");
const assert = require("node:assert/strict");

const BASE = process.env.BASE || "http://localhost:3100";
const SETUP = process.env.SETUP;

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  const dialogs = [];
  page.on("dialog", (d) => { dialogs.push(d.message()); d.dismiss(); });
  const cdp = await ctx.newCDPSession(page);
  await cdp.send("WebAuthn.enable");
  await cdp.send("WebAuthn.addVirtualAuthenticator", {
    options: { protocol: "ctap2", transport: "internal", hasResidentKey: true, hasUserVerification: true, isUserVerified: true },
  });

  // CSRF: başka kökenden istek reddedilir
  const evil = await page.request.post(`${BASE}/api/admin/webauthn/login-options`, { headers: { Origin: "https://evil.example", "Content-Type": "application/json" }, data: {} });
  assert.equal(evil.status(), 403, "yabancı köken");

  // Yanlış kurulum anahtarı
  const wrong = await page.request.post(`${BASE}/api/admin/webauthn/register-options`, { headers: { Origin: BASE, "Content-Type": "application/json" }, data: { setupToken: "x".repeat(40), name: "Saldırgan" } });
  assert.equal(wrong.status(), 403, "yanlış kurulum anahtarı");

  // İlk kurulum
  await page.goto(`${BASE}/admin/setup`);
  await page.fill('input[name="token"]', SETUP);
  await page.fill('input[name="name"]', "Ahmet");
  await page.fill('input[name="nickname"]', "Sanal anahtar");
  await page.click('button[type="submit"]');
  await page.waitForURL(`${BASE}/admin`, { timeout: 15000 });
  await page.waitForSelector("text=Hoş geldiniz, Ahmet");
  console.log("kurulum ve oturum: tamam");

  // Kurulum sayfası artık kapalı
  const setupAgain = await page.request.get(`${BASE}/admin/setup`, { maxRedirects: 0 });
  assert.ok([307, 308].includes(setupAgain.status()), "kurulum kapandı");

  // İçerik düzenleme + XSS denemesi
  await page.goto(`${BASE}/admin/content/home?lang=tr`);
  await page.fill('input[name="heroTitle"]', '<script>alert("xss")</script> Yeni başlık');
  await page.fill('input[name="heroBadge"]', "Deneme rozeti");
  await page.click('button:has-text("Kaydet")');
  await page.waitForSelector("text=Kaydedildi");
  await page.goto(`${BASE}/tr`);
  const h1 = await page.textContent("h1");
  assert.ok(h1.includes('<script>alert("xss")</script> Yeni başlık'), "başlık metin olarak");
  assert.equal(await page.locator("h1 script").count(), 0, "betik çalışmaz");
  console.log("içerik + XSS: tamam");

  // Zararlı adres reddedilir
  await page.goto(`${BASE}/admin/content/download?lang=tr`);
  await page.fill('input[name="android.url"]', "javascript:alert(1)");
  await page.click('button:has-text("Kaydet")');
  await page.waitForSelector("text=Bazı alanlar geçersiz");
  console.log("javascript: adresi reddi: tamam");

  // Görsel
  await page.goto(`${BASE}/admin/images`);
  await page.fill('input[name="hero.url"]', "https://example.com/hero.png");
  await page.fill('input[name="hero.altTr"]', "Uygulama ekranı");
  await page.click('button:has-text("Kaydet")');
  await page.waitForSelector("text=Görseller kaydedildi");
  await page.goto(`${BASE}/tr`);
  assert.equal(await page.getAttribute("img.site-image", "src"), "https://example.com/hero.png");
  console.log("görsel: tamam");

  // İletişim formu → panel
  const visitor = await browser.newPage();
  await visitor.goto(`${BASE}/tr/contact`);
  await visitor.fill('input[name="name"]', "Ziyaretçi");
  await visitor.fill('input[name="email"]', "ziyaretci@example.com");
  await visitor.fill('input[name="subject"]', "İş birliği");
  await visitor.fill('textarea[name="message"]', "Merhaba, uygulamanız hakkında görüşmek isterim. <b>kalın</b>");
  await visitor.click('button[type="submit"]');
  await visitor.waitForSelector("text=Mesajın alındı");
  await page.goto(`${BASE}/admin/messages`);
  await page.waitForSelector("text=İş birliği");
  assert.ok((await page.textContent(".message .pre")).includes("<b>kalın</b>"), "mesaj metin olarak");
  await page.click('button:has-text("Okundu yap")');
  await page.waitForSelector('button:has-text("Okunmadı yap")');
  console.log("iletişim mesajı: tamam");

  // Çıkış ve passkey ile giriş
  await page.click('button:has-text("Çıkış")');
  await page.waitForURL(`${BASE}/admin/login`);
  const blocked = await page.request.get(`${BASE}/admin/messages`, { maxRedirects: 0 });
  assert.equal(blocked.status(), 307, "çıkıştan sonra panel kapalı");
  await page.click('button:has-text("Passkey ile giriş yap")');
  await page.waitForURL(`${BASE}/admin`, { timeout: 15000 });
  console.log("çıkış/giriş: tamam");

  // Sahte çerezle panel açılmaz
  const ctx2 = await browser.newContext();
  await ctx2.addCookies([{ name: "__Host-dg_admin", value: "A".repeat(43), domain: "localhost", path: "/", secure: true, httpOnly: true, sameSite: "Strict" }]);
  const p2 = await ctx2.newPage();
  await p2.goto(`${BASE}/admin/messages`);
  assert.ok(p2.url().endsWith("/admin/login"), "sahte oturum reddedilir");
  console.log("sahte oturum reddi: tamam");

  assert.equal(dialogs.length, 0, "hiç uyarı penceresi açılmadı");
  await page.screenshot({ path: "/tmp/claude-0/webshots/admin-home.png", fullPage: true });
  await page.goto(`${BASE}/admin/content/home?lang=tr`);
  await page.screenshot({ path: "/tmp/claude-0/webshots/admin-content.png" });
  await browser.close();
  console.log("HEPSİ TAMAM");
})().catch((e) => {
  console.error("HATA", e);
  process.exit(1);
});
