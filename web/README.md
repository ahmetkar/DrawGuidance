# Çizim Rehberi — web sitesi (v42)

Üretim adresi: **https://drawguidance.ahmetkar.com** (Vercel'de özel alan adı).

Google Play'in istediği herkese açık sayfalar: gizlilik politikası, kullanım şartları, hesap ve veri silme, destek ve
`app-ads.txt`. Next.js (App Router, TypeScript), Türkçe öntanımlı, İngilizce ikinci dil. Çerez ve izleme kodu yok.

## Sayfalar

| Sayfa | Türkçe | İngilizce | Kısa adres |
| --- | --- | --- | --- |
| Ana sayfa | `/tr` | `/en` | `/` → `/tr` |
| Gizlilik politikası | `/tr/privacy` | `/en/privacy` | `/gizlilik`, `/privacy` |
| Kullanım şartları | `/tr/terms` | `/en/terms` | `/kullanim-sartlari`, `/terms` |
| Hesap ve veri silme | `/tr/delete-account` | `/en/delete-account` | `/hesap-silme`, `/delete-account` |
| Destek ve iletişim | `/tr/support` | `/en/support` | `/destek`, `/support` |
| AdMob doğrulaması | `/app-ads.txt` | | |

Hesap silme sayfasında iki yol var:

1. **Google ile giriş yapıp silme:** Firebase JS SDK ile Google girişi, açık onay, sonra sunucuda
   `DELETE /api/v1/users/me` (Firebase ID jetonuyla) ve Firebase oturum hesabının silinmesi. Firebase değişkenleri
   eksikse bu bölüm hiç görünmez.
2. **Girişsiz talep formu:** e-posta, isteğe bağlı neden ve onay kutusu → `POST /api/v1/account/deletion-requests`.

Destek formu `POST /api/v1/support/messages` ucuna gider. Yeni talepler Slack'e bildirilir (sunucuda
`SLACK_WEBHOOK_URL` verildiyse).

## v42: kurumsal site ve yönetim paneli

| Sayfa | Türkçe | İngilizce | Kısa adres |
| --- | --- | --- | --- |
| Ana sayfa (tanıtım) | `/tr` | `/en` | `/` |
| Hakkımızda | `/tr/about` | `/en/about` | `/hakkimizda`, `/about` |
| İndir (Google Play, Windows, iOS) | `/tr/download` | `/en/download` | `/indir`, `/download` |
| İletişim (form → sitenin veritabanı) | `/tr/contact` | `/en/contact` | `/iletisim`, `/contact` |
| Yönetim paneli | `/admin` | | |

- **Veritabanı:** Vercel Neon Postgres (`DATABASE_URL`). Uygulamanın sunucusundan (MongoDB) ayrıdır. Tablolar
  `db/schema.sql`'de; `DATABASE_URL=... npm run db:migrate` ile kurulur (tekrar çalıştırmak güvenli).
  Veritabanı yoksa site varsayılan metinlerle çalışır, iletişim formu "şu anda kullanılamıyor" der, panel kapalıdır.
- **Hangi form nereye gider:** İletişim formu → Neon (panelde *Mesajlar*). Destek formu ve hesap silme talebi →
  uygulamanın sunucusu (değişmedi; uygulama hesabıyla ilgili oldukları için).
- **Panelde düzenlenenler:** Ana sayfa, Hakkımızda ve İndirmeler sayfalarının bütün metinleri (Türkçe ve İngilizce),
  indirme bağlantıları ve "yakında" durumu, görsel yuvaları (https adresi + alternatif metin; adres boşken yer
  tutucu görünür), iletişim mesajları (okundu/sil), passkey'ler, açık oturumlar ve işlem kaydı.

### Panel kurulumu (bir kez)

1. Vercel → **Storage → Create → Neon** → projeye bağlayın (`DATABASE_URL` eklenir).
2. Vercel → **Settings → Environment Variables**: `ADMIN_SETUP_TOKEN` (en az 32 karakter rastgele) ve
   `IP_HASH_SALT` (en az 32 karakter rastgele) ekleyin. **Redeploy**.
3. Bilgisayarda: `cd web && DATABASE_URL="<Neon adresi>" npm run db:migrate`.
4. `https://drawguidance.ahmetkar.com/admin/setup` → kurulum anahtarı, adınız → **Passkey oluştur**
   (Face ID / parmak izi / Windows Hello / güvenlik anahtarı). Panel açılır.
5. **Güvenlik** sayfasından ikinci bir cihazda yedek passkey ekleyin. Sonra `ADMIN_SETUP_TOKEN`'ı silebilirsiniz.

### Panel güvenliği

- Parola yok: **Passkey (WebAuthn/FIDO2)**, kullanıcı doğrulaması zorunlu, imza sayacı denetlenir, meydan okuma tek
  kullanımlık ve 5 dakikalık. Açık anahtar dışında hiçbir sır saklanmaz.
- Oturum: 32 bayt rastgele jeton yalnız `__Host-` önekli, HttpOnly, Secure, SameSite=Strict çerezde; veritabanında
  SHA-256 özeti. En çok 8 saat, 30 dakika işlemsizlikte düşer; panelden kapatılabilir.
- Her işlem veritabanındaki oturumla yeniden denetlenir (proxy yalnız hızlı yönlendirme yapar), köken (CSRF) denetimi,
  IP özetine göre istek sınırı (giriş 10/15 dk, kurulum 5/saat, iletişim 5/saat), işlem kaydı.
- SQL enjeksiyonu: bütün sorgular parametreli (Neon etiketli şablon). XSS: içerik düz metin olarak çizilir (HTML
  yorumlanmaz), JSON-LD kaçırılır, CSP, adres alanlarında yalnız https. Panel `noindex`, `no-store`.
- Testler: `npm test` (güvenlik ve içerik doğrulaması), `npm run lint`, `npm run build`.

### SEO

Her sayfada başlık/açıklama, kanonik adres ve `hreflang` (tr, en, x-default), Open Graph ve Twitter kartı (otomatik
üretilen paylaşım görseli), JSON-LD (Organization, WebSite, MobileApplication, FAQPage, BreadcrumbList, AboutPage,
ContactPage), site haritası (öncelik ve sıklıkla), `robots.txt` (`/admin` ve `/api` kapalı).

## Ortam değişkenleri

`.env.example` dosyasına bakın. Hepsi `NEXT_PUBLIC_` ile başlar, yani derleme anında koda gömülür ve tarayıcıda
görünür: **buraya sır koymayın**. Değiştirdikten sonra Vercel'de yeniden dağıtın (Redeploy).

| Değişken | Zorunlu mu | Açıklama |
| --- | --- | --- |
| `NEXT_PUBLIC_API_BASE_URL` | Hayır | Sunucu adresi. Öntanımlı `https://drawapi.ahmetkar.com` |
| `NEXT_PUBLIC_FIREBASE_API_KEY` | Oturumlu silme için | Firebase web uygulamasının `apiKey` değeri |
| `NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN` | Oturumlu silme için | `drawguidance.firebaseapp.com` |
| `NEXT_PUBLIC_FIREBASE_PROJECT_ID` | Oturumlu silme için | `drawguidance` |
| `NEXT_PUBLIC_FIREBASE_APP_ID` | Oturumlu silme için | Firebase web uygulamasının `appId` değeri (`1:...:web:...`) |
| `NEXT_PUBLIC_SITE_URL` | Hayır | Sitenin üretim adresi (site haritası, kanonik adresler). Öntanımlı `https://drawguidance.ahmetkar.com` |
| `NEXT_PUBLIC_CONTACT_EMAIL` | Hayır | Sayfalarda gösterilecek iletişim e-postası. Verilmezse `ahmetkar.1258@gmail.com` |

Firebase web API anahtarı gizli bir anahtar değildir; girişleri Firebase'in yetkili alan adları listesi ve sunucudaki
jeton denetimi korur.

## Yerelde çalıştırma

```bash
cd web
cp .env.example .env.local   # değerleri doldurun (isteğe bağlı)
npm ci
npm run dev                  # http://localhost:3000
npm run lint
npm run build
```

Sunucu `http://localhost:3000` kökenine öntanımlı olarak izin verir. Yerel sunucuyu kullanmak için
`NEXT_PUBLIC_API_BASE_URL=http://localhost:4747` yazın.

## 1. Vercel'e yükleme

1. https://vercel.com adresinde GitHub hesabınızla oturum açın.
2. **Add New… → Project** ve bu depoyu seçin (**Import**).
3. **Root Directory** alanında **Edit** deyip `web` klasörünü seçin. Framework otomatik olarak Next.js gelir; build
   komutlarını değiştirmeyin.
4. **Environment Variables** bölümüne yukarıdaki değişkenleri girin (en az Firebase'in dört değeri; adımlar aşağıda).
   Production, Preview ve Development için işaretli kalabilir.
5. **Deploy**. İlk adres `https://<proje-adı>.vercel.app` olur (yalnız deneme için).
6. **Alan adı:** Project → **Settings → Domains → Add** → `drawguidance.ahmetkar.com`. Vercel'in gösterdiği DNS kaydını
   `ahmetkar.com` alan adının DNS ayarlarına ekleyin (genellikle `drawguidance` adına `CNAME` →
   `cname.vercel-dns.com`; Cloudflare kullanıyorsanız kaydı **DNS only**, gri bulut yapın). HTTPS sertifikası otomatik
   gelir. Alan adı "Valid Configuration" olunca site `https://drawguidance.ahmetkar.com` adresinde açılır.
7. İsteğe bağlı: aynı sayfada `*.vercel.app` adresini üretim alan adına yönlendirin (Redirect to
   `drawguidance.ahmetkar.com`), böylece herkes tek adresi görür.

## 2. Sunucu ayarları (drawapi.ahmetkar.com)

Sunucu öntanımlı olarak `https://drawguidance.ahmetkar.com` ve `http://localhost:3000` kökenlerine izin verir; üretim
için ayrıca bir şey yazmanız gerekmez. `backend/docker/.env` dosyasına (sohbete yapıştırmadan) şunları ekleyip sunucuyu
yeniden başlatın:

```
# Öntanımlı değer zaten bu; başka adres eklerseniz hepsini virgülle yazın
WEB_ALLOWED_ORIGINS=https://drawguidance.ahmetkar.com,http://localhost:3000
# İsteğe bağlı: Vercel önizleme adresleri de formları kullanabilsin (kendi proje adınızla sınırlayın)
WEB_ALLOWED_ORIGIN_PATTERNS=https://drawguidance-*.vercel.app
# Sunucu nginx/Caddy/Cloudflare Tunnel gibi bir ters vekilin arkasındaysa true, değilse false
WEB_BEHIND_PROXY=true
# Uzun rastgele bir yazı: openssl rand -hex 32
IP_HASH_SALT=...
# Slack → Apps → Incoming Webhooks → Add New Webhook to Workspace ile alınan adres
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...
```

İzinli olmayan bir adresten açılan sitede formlar ve silme çalışmaz (tarayıcı CORS hatası). `*.vercel.app` gibi geniş
bir kalıp yazmayın: başkalarının Vercel siteleri de izin almış olur.

## 3. Firebase ayarları (proje: `drawguidance`)

1. https://console.firebase.google.com → **drawguidance** projesi → ⚙ **Proje ayarları → Genel**.
2. **Uygulamalarınız** bölümünde **Uygulama ekle → Web (`</>`)**. Takma ad: `DrawGuidance Web`. Firebase Hosting
   kutusunu işaretlemeyin. **Uygulamayı kaydet**.
3. Gösterilen `firebaseConfig` içinden şu değerleri Vercel'e girin:
   - `apiKey` → `NEXT_PUBLIC_FIREBASE_API_KEY`
   - `authDomain` → `NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN` (`drawguidance.firebaseapp.com`)
   - `projectId` → `NEXT_PUBLIC_FIREBASE_PROJECT_ID` (`drawguidance`)
   - `appId` → `NEXT_PUBLIC_FIREBASE_APP_ID`
4. **Authentication → Ayarlar (Settings) → Yetkili alan adları (Authorized domains) → Alan adı ekle:**
   `drawguidance.ahmetkar.com`. (Önizleme adreslerinde de denemek isterseniz o `*.vercel.app` adresini de ekleyin.)
   Eklenmezse Google girişi `auth/unauthorized-domain` hatası verir; sayfa bu durumda talep formunu önerir.
5. **Authentication → Oturum açma yöntemi:** Google zaten açık olmalı (uygulama kullanıyor).
6. Vercel'de **Redeploy**.

## 4. Play Console'a girilecek adresler

| Play Console alanı | Adres |
| --- | --- |
| **Uygulama içeriği → Gizlilik politikası** | `https://drawguidance.ahmetkar.com/tr/privacy` |
| **Uygulama içeriği → Veri güvenliği → Hesap silme URL'si** (Delete account URL) | `https://drawguidance.ahmetkar.com/tr/delete-account` |
| **Veri güvenliği → "Kullanıcılar hesabı silmeden verilerin bir kısmının silinmesini isteyebilir mi?"** | İsterseniz `https://drawguidance.ahmetkar.com/tr/support` |
| **Mağaza ayarları → Web sitesi** (app-ads.txt için) | `https://drawguidance.ahmetkar.com` |

- **app-ads.txt:** AdMob, Play'deki **web sitesi** adresinin kökünde `app-ads.txt` arar. Web sitesi olarak
  `https://drawguidance.ahmetkar.com` girildiğinde dosya `https://drawguidance.ahmetkar.com/app-ads.txt` adresindedir.
  (Not: AdMob, alt alan adı için bazen ana alan adına, `https://ahmetkar.com/app-ads.txt`, de bakar; doğrulama
  geçmezse aynı satırı oraya da koyun.) Kontrol: AdMob → Uygulamalar → app-ads.txt sekmesi (doğrulama 24 saate kadar
  sürebilir).
- Hesap silme sayfası uygulama ve geliştirici adını, silme adımlarını, silinen ve saklanan verileri ve süreleri
  içerir; Play'in istediği budur.
- Geliştirici adı `src/lib/config.ts` içindeki `DEVELOPER_NAME` (`KarApplication`, Play Console'daki geliştirici adı).
  Herkese açık iletişim e-postası `CONTACT_EMAIL` (`ahmetkar.1258@gmail.com`; `NEXT_PUBLIC_CONTACT_EMAIL` ile değişir).
- Uygulama yayınlanınca Play bağlantısı (`PLAY_STORE_URL`) zaten doğru adrese gider.

## Güvenlik

- `next.config.ts` güvenlik başlıklarını ekler: CSP (yalnız site, sunucu ve Firebase/Google girişi), HSTS,
  `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`, `Cross-Origin-Opener-Policy:
  same-origin-allow-popups` (Google giriş penceresi için).
- Firebase kodu yalnız hesap silme sayfasında yüklenir; oturum yalnız o sekmede tutulur.
- Sunucu form uçlarını IP başına saatte 5 istekle sınırlar, ham IP saklamaz.
