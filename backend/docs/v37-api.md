# Sunucu v37 API sözleşmesi (37.0.0)

İki yenilik: katalogun **ikili aktarım biçimi** (vektörler ve temel ağlar) ve **hata/çökme raporu ucu**. Eski uçların
JSON yanıtları değişmedi; eski uygulamalar etkilenmez. Hata gövdesi her yerde aynı: `{"error": "<kod>", "message": "<Türkçe açıklama>"}`.

---

## 1. Katalog: ikili biçim

### İstek

| Uç | İkili | Not |
|---|---|---|
| `GET /api/v1/catalog/vectors[?kind=…]` | var | `?kind=` süzmesi ikili biçimde de çalışır |
| `GET /api/v1/catalog/mode-nets` | var | ağırlıklar base64'süz, ham bayt |
| `GET /api/v1/catalog/templates[?kind=…]` | yok | her zaman JSON |
| `GET /api/v1/catalog/manifest` | — | JSON; `binary` alanı eklendi |

Biçim seçimi (öncelik sırasıyla):

1. `?format=bin` (ya da `binary`) → ikili; `?format=json` → JSON. Başka değer → **400 `unknown_format`**. İkilisi olmayan
   bölümde (`templates`) `?format=bin` → **400 `unsupported_format`**.
2. `Accept` başlığı `application/vnd.drawguidance.catalog+bin` türünü `q > 0` ile içeriyor ve `application/json`'a
   ondan yüksek `q` vermiyorsa → ikili. Önerilen başlık:
   `Accept: application/vnd.drawguidance.catalog+bin, application/json;q=0.5`
   (`templates` bu başlıkla JSON döner.)
3. Diğer her durum (başlık yok, `*/*`, `application/json`) → JSON (eskisi gibi).

### Yanıt

- `200`, `Content-Type: application/vnd.drawguidance.catalog+bin`, `Cache-Control: no-cache`,
  `Vary: Accept, Accept-Encoding` (vektör ve ağ uçlarında JSON yanıtlarda da).
- **ETag** temsile özgüdür: JSON `"cat-<sürüm>"` / `"cat-<sürüm>-<TÜR>"`, ikili `"cat-<sürüm>-bin"` /
  `"cat-<sürüm>-<TÜR>-bin"` (ör. `"cat-37000-bin"`, `"cat-37000-KIDS-bin"`). `If-None-Match` tutarsa gövdesiz `304`
  (aynı ETag ve Vary ile). JSON ETag'i ikili isteği tutmaz, tersi de.
- `Accept-Encoding: gzip` varsa gövde önceden gzip'lenmiş gelir (`Content-Encoding: gzip`; OkHttp kendiliğinden açar).
- Gövdeler sürüm başına bir kez hazırlanıp bellekte tutulur (ikili gövde ilk ikili istekte).

Boyut (v36 tohumu, 829 vektör): JSON 10,7 MB (gzip 2,1 MB) → ikili 2,4 MB (gzip ~1 MB).

### Manifest

```json
{"version": 37000, "updatedAt": "...", "counts": {"templates": 750, "vectors": 829, "modeNets": 5},
 "binary": {"mediaType": "application/vnd.drawguidance.catalog+bin", "formatVersion": 1, "sections": ["vectors", "modeNets"]}}
```

Manifestin ETag'i değişmedi (`"cat-<sürüm>"`). Uygulama `binary.sections` içinde bölümü görürse ikili isteyebilir;
alan yoksa (eski sunucu) JSON kullanır.

### İkili düzen (biçim sürümü 1)

Sayı kodlaması `CatalogCodec.packPaths` ile aynı:

- **varint**: işaretsiz, 7 bitlik gruplar, küçük grup önce, devam biti `0x80` (`Int` için en çok 5 bayt; negatif
  `Int` 5 bayt tutar). **varint64**: aynısı `Long` için (en çok 10 bayt).
- **zigzag**: `(v shl 1) xor (v shr 31)` sonra varint.
- **yazı**: UTF-8 bayt uzunluğu (varint) + baytlar. **baytlar**: uzunluk (varint) + baytlar.
- **yazı listesi**: sayı (varint) + yazılar.
- **float32**: IEEE-754 bitleri (`floatToIntBits`), 4 bayt, büyük uçlu.

**Kap:**

```
'D' 'G' 'C' 'B'      44 47 43 42
biçim sürümü         1 bayt = 01
bölüm                1 bayt: 01 vektörler, 02 temel ağlar
katalog sürümü       varint64 (JSON'daki "version")
öğe sayısı           varint
öğeler               her biri: uzunluk (varint) + öğe baytları
```

Örnek başlangıç: `44 47 43 42 01 01 88 a1 02 05 ed 01 ff 03 0d 66 78 2e …` = sihirli baytlar, biçim 1, vektörler,
sürüm 37000, 5 öğe, ilk öğe 237 bayt, bayraklar 511, `id` 13 bayt `"fx.all_fields"` …

Öğe uzunluğu ileriye uyumluluk içindir: yeni sürümler öğenin **sonuna** alan ekleyebilir; çözücü tanımadığı bayrak
bitlerini ve öğenin kalanını atlar. Biçim sürümü 1 değilse çözücü reddeder.

**Vektör öğesi:**

```
bayraklar   varint     1 title, 2 categoryId, 4 categoryTitle, 8 section, 16 audience, 32 fit,
                       64 aspect, 128 tags, 256 kinds  (hepsi varsa 511)
id          yazı
title       yazı        (bayrak 1)
categoryId  yazı        (2)
categoryTitle yazı      (4)
section     yazı        (8)
audience    yazı        (16)   olduğu gibi (ART/NOTES/BOTH/KIDS; tanınmayanı uygulama ART sayar)
fit         yazı        (32)   olduğu gibi (BOX/SQUARE/LINE; tanınmayan → BOX)
aspect      float32     (64)
tags        yazı listesi (128)
kinds       yazı listesi (256)
paths       baytlar     CatalogCodec.packPaths çıktısı (cihaz veritabanındaki biçimin aynısı)
```

Bayrağı olmayan alan JSON'da yoktu (ya da yazı/dizi/sayı değildi): çözücü `CatalogCodec.vector(JsonObject)`
öntanımlılarını kullanır (`title` "", `categoryId` "remote", `categoryTitle` "Yeni şekiller", `section` "",
`audience` ART, `fit` BOX, `aspect` 1, `tags` boş).

**Yollar (`packPaths`, değişmedi):** `'V'`, `01`, yol sayısı (varint); her yol için bayraklar (varint: 1 kapalı,
2 fill, 4 stroke, 8 width, 16 line, 32 alpha, 64 tag, 128 label, 256 labelSize), nokta sayısı (varint), noktalar
(x ve y için zigzag fark; fark bir önceki noktaya göre, **yollar arasında da sürer**; değer
`Math.round(v * 10000f)`), sonra bayraktaki alanlar şu sırayla: fill (yazı), stroke (yazı), width (zigzag q4),
line (yazı), alpha (zigzag q4), tag (yazı), label (yazı), labelSize (zigzag q4).

**Temel ağ öğesi:**

```
bayraklar   varint     1 version, 2 format
mode        yazı
version     zigzag     (bayrak 1; yoksa 0)
format      yazı       (bayrak 2; yoksa "modenet-int8-v1")
weights     baytlar    ham ağırlıklar (JSON'daki base64'ün çözülmüşü)
```

### Sunucunun kodlama kuralları

- Öğeler JSON'daki sırayla (anahtara göre artan). Sürüm JSON yanıtındakiyle aynı.
- Yollar öğedeki JSON metninden uygulamanın `decodePaths`'i gibi okunur (sayılar `Float.parseFloat`; eksik/geçersiz
  `w` 1, `a` 1, `ls` 0, nokta 0; tek kalan koordinat atılır; `"c"` 1 ya da true ise kapalı).
- Çizgi biçimi (`"l"`) yorumlanmaz: `"SOLID"` dışındaki her ad yazılır (uygulama tanımadığını SOLID sayar).
- Uygulamanın JSON yolunda **atlayacağı** öğe (`id`/`paths` yazı değil, yollar bozuk ya da `"p"` yok, `aspect`
  nesne; ağda `mode`/`weights` yok ya da base64 değil) ikili yanıta **konmaz** (öğe sayısı buna göre).
- Aynı veriden Kotlin (`CatalogBinary.encodeVectors/encodeModeNets`) ve Java kodlayıcısı **bayt bayt aynı** çıktıyı
  verir: ortak örnek `catalog-binary-fixture.json` → `.vectors.hex` / `.modenets.hex` iki tarafın testlerinde
  (`CatalogBinaryTest.kt`, `CatalogBinaryTest.java`; SHA-256 özetleri de aynı). v36 tohumunun tamamında da (829
  vektör, 4 ağ) iki çıktı aynıdır.

### Uygulama tarafı (core:content)

`com.karapp.drawguidance.content.catalog.CatalogBinary`:

- `MEDIA_TYPE`, `FORMAT_VERSION`, `isBinary(bytes)` (sihirli baytlar; JSON'u ayırmak için).
- `decodeVectors(bytes): Section<CatalogVector>` — `Section(section, version, items)`.
- `decodeVectorEntries(bytes): Section<VectorEntry>` — `VectorEntry(vector, kinds, packedPaths)`; `packedPaths`
  cihaz veritabanına yeniden kodlamadan (Deflate ile) yazılabilir.
- `decodeModeNets(bytes): Section<CatalogModeNet>`.
- `encodeVectors(version, list)`, `encodeModeNets(version, list)`.
- Bozuk/yabancı veri, yanlış bölüm, bilinmeyen biçim sürümü → `IllegalArgumentException`.

---

## 2. Hata/çökme raporu: `POST /api/v1/reports`

### İstek

- `Content-Type: application/json` (değilse **415 `unsupported_media_type`**). Gövde en çok **256 KB** (aşarsa
  **413 `too_large`**, `Content-Length` büyükse gövde okunmaz).
- `Authorization: Bearer <Firebase ID token>` **isteğe bağlı**. Geçerliyse kullanıcı kimliği (`sub`) rapora eklenir.
  Jeton yok, bozuk ya da süresi dolmuşsa rapor yine kabul edilir (kimliksiz; 401 dönmez).
- Bilinmeyen alanlar yok sayılır.

| Alan | Tür | Kural |
|---|---|---|
| `kind` | yazı | **zorunlu**: `CRASH` \| `ERROR` \| `USER` (büyük/küçük harf duyarsız) → yoksa 400 `invalid_kind` |
| `installId` | yazı | **zorunlu**, UUID (kuruluma özgü rastgele) → değilse 400 `invalid_install_id` |
| `message` | yazı | en çok 4000 karakter (fazlası `…` ile kesilir) |
| `stackTrace` | yazı | en çok 64000: baştan 48000 + `…[N karakter kesildi]…` + sondan 15000 |
| `note` | yazı / null | kullanıcının notu, en çok 4000 |
| `feature` | yazı | özellik/ekran etiketi, en çok 100 (denetim karakterleri silinir) |
| `appVersion` | yazı | en çok 50 |
| `versionCode` | tam sayı | ≥ 0 → değilse 400 `validation` |
| `sdk` | tam sayı | 1–1000 → değilse 400 `validation` |
| `device` | yazı | en çok 120 |
| `language` | yazı | en çok 35 |
| `deviceTime` | tam sayı (Unix ms) | ≥ 0 → değilse 400 `validation` |
| `breadcrumbs` | yazı listesi | boşlar atılır, son 50 kalır, her biri en çok 300 |
| `exceptionType` | yazı | isteğe bağlı; yoksa `stackTrace`'in ilk satırından (`a.b.C: ileti` → `a.b.C`) |

`message`, `stackTrace` ve `note` üçü de boşsa **400 `empty_report`**. Alan türü yanlış ya da JSON bozuk/nesne değilse
**400 `bad_request`**.

### Yanıt

| Durum | Gövde |
|---|---|
| `202 Accepted` | `{"id": "<UUID>"}` |
| `400` | `invalid_kind`, `invalid_install_id`, `empty_report`, `validation`, `bad_request` |
| `413` | `too_large` |
| `415` | `unsupported_media_type` |
| `429` | `rate_limited`, `Retry-After: <saniye>` başlığıyla |
| `503` / `500` | `database_unavailable` / `internal` |

Başka yöntemler (`GET`/`PUT`/`DELETE /api/v1/reports`) **404 `not_found`**.

Uygulama için öneri: `202`, `400`, `413` sonrası yeniden gönderme; `429`'da `Retry-After` kadar bekle; ağ hatası ve
`5xx`'te sonra yeniden dene (kuyruk).

### İstek sınırı (bellekte, sunucu başına, sabit pencere)

- IP başına: pencere başına 300 istek (`REPORTS_PER_IP_LIMIT`), gövde okunmadan önce.
- Kurulum (`installId`) başına: pencere başına 30 rapor (`REPORTS_PER_INSTALL_LIMIT`), geçerli raporlarda.
- Pencere 1 saat (`REPORTS_WINDOW`, ör. `30m`), gövde sınırı `REPORTS_MAX_BODY_BYTES`.
- Ters vekil arkasında gerçek istemci adresi için `SERVER_FORWARD_HEADERS_STRATEGY=native`.

### Saklama

- `error_reports` (`_id` = sunucunun ürettiği UUID): yukarıdaki alanlar (`deviceTime` tarih olarak), `installId`
  (küçük harf), `receivedAt` (sunucu alış zamanı), `fingerprint`, `auth` (`none` | `valid` | `invalid` |
  `unverified`: Google anahtarları alınamadı), `uid` (yalnız geçerli jetonla), `truncated` (kesilen alan adları).
  Boş alanlar yazılmaz. **IP adresi saklanmaz.** Dizinler: `receivedAt_-1`, `fingerprint_1_receivedAt_-1`.
- `error_report_groups` (`_id` = parmak izi): `count`, `firstSeen`, `lastSeen`, `lastReportId`, `lastAppVersion`,
  `kind`, `exceptionType`, `feature`, `sampleMessage`. Dizin: `lastSeen_-1`.
- Parmak izi: SHA-256(`kind`, istisna türü, ilk istisnanın en üstteki 5 yığın satırı — dosya/satır numarası atılır,
  `$1` gibi derleyici adları `$#`) ilk 16 bayt, 32 onaltılık karakter. Yığın yoksa ileti (sayılar `#`), o da yoksa
  `feature`.

---

## 3. Diğer

- Sunucu sürümü **37.0.0** (`/api/health` → `"version": "37.0.0"`).
- CORS: `POST` izinli, `Retry-After` başlığı açık.
