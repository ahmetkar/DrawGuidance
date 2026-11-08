# Sunucu v39 API sözleşmesi (39.0.0)

İki yenilik: **Google Play abonelikleri** (Ücretsiz / Premium planlar, satın alma doğrulaması, gerçek zamanlı
bildirimler) ve çocuk modu **Çizim Görevi ilerleme eşitlemesi**. Eski uçlar değişmedi. Hata gövdesi her yerde aynı:
`{"error": "<kod>", "message": "<Türkçe açıklama>"}`. Zamanlar ISO-8601 (UTC) yazıdır, aksi yazılmadıkça.

---

## 1. Koleksiyonlar

| Koleksiyon | Belge | Dizinler |
|---|---|---|
| `plans` | `_id` = `free` \| `premium`; `name`, `description`, `productId`, `basePlanId`, `price {amountMicros, currency, display}`, `period` (ISO-8601, `P1M`), `active`, `sort`, `updatedAt` | `productId_1` |
| `plan_features` | `_id` = `<plan>:<özellik>`; `planId`, `feature`, `enabled`, `limit` (null = sınırsız), `sort` | `planId_1_feature_1` **benzersiz** |
| `subscriptions` | kullanıcı başına **tek güncel** belge, `_id` = Firebase uid; `planId`, `status`, `playState`, `productId`, `basePlanId`, `offerId`, `purchaseToken`, `linkedPurchaseToken`, `orderId` (son başarılı sipariş), `startTime`, `expiryTime`, `autoRenewing`, `acknowledged`, `testPurchase`, `regionCode`, `source` (`VERIFY` \| `RTDN`), `fetchedAt`, `createdAt`, `updatedAt` | `purchaseToken_1` **benzersiz** (kısmi), `linkedPurchaseToken_1`, `expiryTime_1_status_1` |
| `subscription_transactions` | **yalnız eklenir**; `_id` UUID, `userId` (bilinmiyorsa null), `purchaseToken`, `orderId`, `type`, `status`, `planId`, `productId`, `basePlanId`, `linkedPurchaseToken`, `startTime`, `expiryTime`, `autoRenewing`, `source`, `messageId`, `notificationType`, `eventTime`, `at`, `snapshot` (Play'in ham `subscriptionsv2` yanıtı) | `orderId_1_type_1` **benzersiz** (kısmi: `orderId` olanlar), `userId_1_at_-1`, `purchaseToken_1` |
| `billing_webhook_events` | `_id` = Pub/Sub **messageId**; `kind` (`subscription` \| `voided` \| `test` \| `oneTimeProduct` \| `unknown`), `notificationType`, `notificationName`, `purchaseToken`, `subscriptionId`, `orderId`, `packageName`, `eventTime`, `publishTime`, `pushSubscription`, `payload`, `receivedAt`, `lastAttemptAt`, `processedAt`, `status` (`PROCESSING` \| `PROCESSED` \| `IGNORED` \| `FAILED`), `result`, `error`, `attempts`, `duplicates` | `receivedAt` **TTL 180 gün**, `status_1_receivedAt_-1`, `purchaseToken_1` |
| `challenge_progress` | `_id` = Firebase uid; `firebaseUid`, `levels {"0": {bestScore, stars, updatedAt}, …}`, `version`, `createdAt`, `updatedAt` | `firebaseUid_1` **benzersiz** |

### Neden bu koleksiyonlar (ve neden fazlası yok)

- İstenen beş tablonun hepsi gerekli ve eklendi. **Kaldırılan yok.**
- `subscriptions` **kullanıcı başına tek belgedir** (`_id` = uid). Satın alma jetonu → kullanıcı eşlemesi bu belgedeki
  benzersiz `purchaseToken` ile yapılır; ayrı bir `purchase_tokens` tablosu gerekmez. Eski jetonlar
  (`linkedPurchaseToken`: yükseltme/yeniden abonelik) ve önceki hareketler de kullanıcıyı bulmaya yeter.
- **Sipariş numarası, doğrulama ve yenileme geçmişi** `subscription_transactions`'tadır: her dönem yeni `orderId`
  (`GPA.…..0`, `..1` …) taşır; `orderId + type` benzersiz olduğundan aynı sipariş/olay hem uygulamadan hem bildirimden
  gelse de bir kez yazılır.
- **Hak (entitlement) tablosu yok**: Premium hakkı `subscriptions` (durum + bitiş) ile `plan_features`'tan her istekte
  hesaplanır; ayrı tablo eskiyip çelişebilirdi.
- **Hesap eşlemesi tablosu yok**: uygulama `obfuscatedAccountId` = **SHA-256(Firebase uid)** (UTF-8, küçük harfli 64
  onaltılık) verir; sunucu aynı değeri hesaplar. Ayrı sütun/tablo gerekmez (`/me` yanıtında da döner).
- Tek seferlik ürün (`oneTimeProductNotification`) uygulamada yok; bildirimi kaydedilir ve yok sayılır.
- `billing_webhook_events` 180 gün sonra kendiliğinden silinir (Pub/Sub iletiyi en çok 7 gün yeniden gönderir;
  tekrar işleme önlemesi için yeter). Kalıcı geçmiş `subscription_transactions`'tadır.
- Faturalama koleksiyonları parçalanmaz (benzersiz `purchaseToken` dizini parça anahtarıyla başlayamaz; veri küçük).
- Hesap silme (`DELETE /users/me`) aboneliği silmez: abonelik Play'de sürer, kullanıcı Play'den iptal eder.

### Tohumlama (açılışta, tekrar çalıştırılabilir)

`plans` ve `plan_features` her açılışta `_id` ile **yerine yazılır** (kaynak: `drawguidance.billing.premium.*`);
tohumdaki planların listede olmayan özellikleri silinir. **Ücretsiz** planda özellik yoktur; **Premium** planda
şu 19 anahtarın hepsi vardır:

```
art.image_bg_removal  art.image_effects_advanced  art.shape_suggest_advanced  art.shapes_advanced
notes.image_bg_removal  notes.image_effects_advanced  notes.diagrams_advanced  notes.shape_suggest_advanced
comic.image_bg_removal  comic.effects_advanced  comic.characters_advanced  comic.objects_suggest_advanced
comic.effects  comic.characters  comic.backgrounds  comic.templates  comic.shapes_advanced
model3d.suggest  kids.challenge_advanced  ads.none
```

(v40: `kids.challenge_advanced` çocuk modu Çizim Görevi'nin son 15 seviyesini, 36–50'yi açar.)

---

## 2. Uçlar

| Uç | Kimlik |
|---|---|
| `GET /api/v1/plans` | herkese açık |
| `GET /api/v1/subscriptions/me` | Firebase `Authorization: Bearer <ID token>` |
| `POST /api/v1/subscriptions/verify` | Firebase |
| `GET /api/v1/subscriptions/me/history[?limit=50]` | Firebase |
| `POST /api/v1/billing/rtdn` | Pub/Sub (Firebase jetonu **okunmaz**; ayarlıysa Pub/Sub OIDC jetonu) |
| `GET` / `PUT /api/v1/challenge/progress` | Firebase |

Firebase isteyen uçlarda jeton yok/geçersiz → **401** (`unauthorized` / `invalid_token`).

### `GET /api/v1/plans`

`ETag: "plans-<16 onaltılık>"`, `Cache-Control: no-cache`; `If-None-Match` tutarsa gövdesiz **304**.

```json
{"plans": [
  {"id": "free", "name": "Ücretsiz", "description": "…", "productId": null, "basePlanId": null,
   "price": {"amountMicros": 0, "currency": "TRY", "display": "0 TL"}, "period": null, "sort": 0,
   "features": [], "limits": {}},
  {"id": "premium", "name": "Premium", "description": "…", "productId": "drawguidance_premium",
   "basePlanId": "premium-monthly", "price": {"amountMicros": 100000000, "currency": "TRY", "display": "100 TL"},
   "period": "P1M", "sort": 1, "features": ["art.image_bg_removal", "…", "ads.none"], "limits": {}}],
 "featureKeys": ["art.image_bg_removal", "…", "ads.none"]}
```

`price` yalnız yedektir: Play fiyatı ülkeye göre çevirir; uygulama `ProductDetails` içindeki `formattedPrice`'ı gösterir.

### `GET /api/v1/subscriptions/me` (oturum açılınca)

```json
{"planId": "premium", "status": "ACTIVE", "active": true,
 "productId": "drawguidance_premium", "basePlanId": "premium-monthly",
 "startTime": "2026-10-20T10:00:00Z", "expiryTime": "2026-11-20T10:00:00Z",
 "autoRenewing": true, "acknowledged": true, "orderId": "GPA.1234-5678-9012-34567..0",
 "features": ["art.image_bg_removal", "…", "ads.none"], "limits": {},
 "obfuscatedAccountId": "<sha256(uid) hex>", "updatedAt": "2026-10-20T10:00:01.123Z"}
```

Abonelik hiç yoksa: `planId` `"free"`, `status` **null**, `active` false, `features` `[]`, ürün/zaman alanları null,
`obfuscatedAccountId` yine dolu.

- `active` = Premium hakkı şu an var mı. Hak veren durumlar: `ACTIVE`, `CANCELED` (bitişe kadar), `IN_GRACE_PERIOD`
  (en çok bitiş + 30 gün). `ON_HOLD`, `PAUSED`, `PENDING`, `EXPIRED` hak vermez.
- Bildirim kaçsa bile bitişi geçmiş `ACTIVE`/`CANCELED` kayıt `status: "EXPIRED"`, `planId: "free"` döner.
- `status` değerleri: `ACTIVE`, `IN_GRACE_PERIOD`, `ON_HOLD`, `PAUSED`, `CANCELED`, `EXPIRED`, `PENDING`.

### `POST /api/v1/subscriptions/verify` (satın almadan sonra ve değişmiş bir satın alma görülünce)

```json
{"packageName": "com.karapp.drawguidance", "productId": "drawguidance_premium", "purchaseToken": "<Purchase.purchaseToken>"}
```

Sunucu durumu **Google Play'den** okur (`purchases.subscriptionsv2.get`), uygulamanın söylediğine güvenmez:

1. Faturalama ayarlı değilse → **503 `billing_not_configured`** (sahte doğrulama yapılmaz, hiçbir şey yazılmaz).
2. `packageName` sunucununki değilse → 400 `package_mismatch`.
3. Play jetonu tanımıyorsa → 400 `invalid_purchase_token`; Play'e ulaşılamıyorsa → **502 `play_unavailable`** (sonra yeniden dene).
4. Play'deki ürün gövdedekiyle aynı değilse → 400 `product_mismatch`; ürün bir plana bağlı değilse → 400 `unknown_product`.
5. `obfuscatedExternalAccountId` = SHA-256(uid) değilse → **403 `account_mismatch`**. Alan yoksa (Play Store'da kod
   kullanımı gibi uygulama dışı satın alma) jeton ilk doğrulayan hesaba bağlanır (`require-account-id: true` ise 403).
6. Jeton başka bir kullanıcıya bağlıysa → **409 `purchase_token_in_use`**.
7. Onaylanmamışsa `purchases.subscriptions.acknowledge` çağrılır (onaylanamazsa `acknowledged: false` kalır, sonraki
   doğrulamada yeniden denenir; 3 gün içinde onaylanmayan satın almayı Play iade eder).
8. `subscriptions` güncellenir, hareket eklenir (ilk doğrulama `PURCHASE`; sonra farka göre `RENEWAL`, `CANCEL`,
   `EXPIRE`, `RESTART`, `RECOVER`, `ON_HOLD`, `GRACE_PERIOD`, `PAUSE`; değişiklik yoksa hareket yazılmaz).

Yanıt: `/me` ile aynı gövde. Eksik alan → 400 `validation`.

**Uygulama için:** `BillingFlowParams.newBuilder().setObfuscatedAccountId(sha256Hex(firebaseUid))` (ya da `/me`'deki
`obfuscatedAccountId`). Onaylamayı sunucu yapar; uygulama `acknowledgePurchase`'ı yalnız sunucuya ulaşamazsa yedek
olarak çağırabilir.

### `GET /api/v1/subscriptions/me/history?limit=50`

```json
{"items": [{"id": "…", "type": "RENEWAL", "status": "ACTIVE", "orderId": "GPA.…..0", "planId": "premium",
            "productId": "drawguidance_premium", "basePlanId": "premium-monthly", "expiryTime": "…",
            "source": "RTDN", "at": "…"}]}
```

Yeniden eskiye, en çok 200; ham Play yanıtı dönmez.

### `POST /api/v1/billing/rtdn` (Google Play gerçek zamanlı bildirimleri, Pub/Sub itme)

Gövde Pub/Sub itme biçimidir:

```json
{"message": {"data": "<base64 DeveloperNotification>", "messageId": "123", "publishTime": "…", "attributes": {}},
 "subscription": "projects/<proje>/subscriptions/<abonelik>"}
```

1. `drawguidance.billing.pubsub.audience` ayarlıysa `Authorization: Bearer <Google OIDC>` denetlenir (imza, `iss`
   `accounts.google.com`, `aud`, süre, ayarlıysa `email`); yok/geçersiz → 401.
2. `messageId` ile `billing_webhook_events`'e yazılır. **Aynı ileti tekrar gelirse:** işlenmişse **200
   `{"status": "duplicate"}`** (ikinci kez işlenmez); başka istek işliyorsa 409; önceki deneme `FAILED` ise yeniden işlenir.
3. Bildirimdeki durum kullanılmaz; abonelik **Play'den yeniden okunur** ve jetonun kullanıcısına uygulanır (aynı
   kurallar; onaylanmamışsa onaylanır). Hareket türü bildirim türünden: 1 `RECOVER`, 2 `RENEWAL`, 3 `CANCEL`,
   4 `PURCHASE`, 5 `ON_HOLD`, 6 `GRACE_PERIOD`, 7 `RESTART`, 8/19 `PRICE_CHANGE`, 9 `DEFERRED`, 10 `PAUSE`,
   11 `PAUSE_SCHEDULE_CHANGED`, 12 `REVOKE`, 13 `EXPIRE`, 20 `PENDING_CANCELED`; abonelik iadesi
   (`voidedPurchaseNotification`, `productType` 1) `REVOKE`.
4. Jetonun kullanıcısı henüz bilinmiyorsa (bildirim uygulamanın doğrulamasından önce geldiyse) hareket kullanıcısız
   yazılır (`result: "unknown_user"`); uygulama doğrulayınca kullanıcıya bağlanır.

| Yanıt | Ne zaman |
|---|---|
| `200 {"messageId", "status": "processed", "result": "applied" \| "unknown_user"}` | uygulandı |
| `200 {"status": "ignored", "result": "test" \| "package_mismatch" \| "invalid_purchase_token" \| "not_subscription" \| "account_mismatch" \| "unknown_product" \| "malformed"}` | kalıcı olarak yok sayıldı |
| `200 {"status": "duplicate", "result": "processed" \| "ignored"}` | tekrar |
| `409 in_progress` | aynı ileti şu an işleniyor (Pub/Sub yeniden gönderir) |
| `503 billing_not_configured` / `503 play_unavailable` | geçici; `FAILED` yazılır, Pub/Sub yeniden gönderir |
| `400 bad_request` | Pub/Sub iletisi değil (`message.messageId` yok, JSON değil) |
| `401` | OIDC jetonu yok/geçersiz (yalnız `audience` ayarlıyken) |

Sahte bir bildirim abonelik veremez: yalnız Play'den yeniden okumayı tetikler.

### Çizim Görevi: `GET /api/v1/challenge/progress`

```json
{"levels": [{"level": 0, "bestScore": 92, "stars": 3, "passed": true, "updatedAt": 1760954400000},
            {"level": 3, "bestScore": 80, "stars": 2, "passed": true, "updatedAt": 1760954300000}],
 "levelCount": 50, "passScore": 60, "passedCount": 2, "totalStars": 5, "updatedAt": "2026-10-20T10:00:00.123Z"}
```

Kaydı olmayan kullanıcı: `levels` `[]`, `passedCount` 0, `totalStars` 0, `updatedAt` null.

- `level`: seviye **sırası 0–49** (v40: 50 seviye; v39'daki 0–14 aynı seviyelerdir). `bestScore` 0–100. `passed` = `bestScore ≥ 60`.
- `stars` sunucuda hesaplanır: < 60 → 0, 60–74 → 1, 75–89 → 2, 90–100 → 3.
- Seviye `updatedAt`: o puanın istemci zamanı (Unix **ms**); üst düzey `updatedAt`: belgenin son yazılışı (ISO).
- Yalnız kaydı olan seviyeler, sıraya göre döner.

### Çizim Görevi: `PUT /api/v1/challenge/progress`

```json
{"levels": [{"level": 0, "bestScore": 92, "updatedAt": 1760954400000}, {"level": 3, "bestScore": 40}]}
```

(doğrudan dizi `[{…}, …]` de kabul edilir; en çok 100 öğe.) **En yüksek puan kalır, puan asla düşmez:** gelen puan
kayıtlıdan büyükse puan, yıldız ve `updatedAt` (yoksa sunucu zamanı) yazılır; eşit ya da düşükse yok sayılır. Yanıt:
birleşmiş ilerleme (`GET` ile aynı gövde). Eşzamanlı yazmalar iyimser kilitle birleşir (puan kaybolmaz).

Hatalar: `level` 0–49 dışı → 400 `invalid_level`; `bestScore` tamsayı 0–100 değil → 400 `invalid_score`;
`levels` dizisi yok → 400 `validation`; JSON bozuk → 400 `bad_request`. Geçersiz istek hiçbir şey yazmaz.

---

## 3. Ayarlar (`application.yml` → `drawguidance.billing`)

| Ayar | Ortam değişkeni | Öntanımlı |
|---|---|---|
| `package-name` | `GOOGLE_PLAY_PACKAGE_NAME` | `com.karapp.drawguidance` |
| `service-account-file` | `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` (JSON dosyasının **yolu**) | boş → doğrulama kapalı (503) |
| `require-account-id` | `BILLING_REQUIRE_ACCOUNT_ID` | `false` |
| `pubsub.audience` | `PUBSUB_PUSH_AUDIENCE` | boş → OIDC denetimi yok |
| `pubsub.service-account-email` | `PUBSUB_PUSH_SERVICE_ACCOUNT` | boş → `email` denetlenmez |
| `premium.product-id` | `PREMIUM_PRODUCT_ID` | `drawguidance_premium` |
| `premium.base-plan-id` | `PREMIUM_BASE_PLAN_ID` | `premium-monthly` |
| `premium.price-micros` / `currency` / `price-display` / `period` | — | `100000000` / `TRY` / `100 TL` / `P1M` |
| `api-base-url` | — | `https://androidpublisher.googleapis.com` |

Hizmet hesabı dosyası **depoya konmaz**; sunucuda gizli dosya olarak bağlanır (ör. Docker secret, `chmod 600`). Dosya
içeriği ve erişim jetonları günlüğe yazılmaz. Google istemci kitaplığı kullanılmaz: JWT (RS256) JDK ile imzalanır,
`https://oauth2.googleapis.com/token`'dan `androidpublisher` kapsamlı erişim jetonu alınır.

---

## 4. Kurulum: Google Cloud ve Play Console (adım adım)

1. **Play Console'da ürün:** Uygulama → *Para kazanma → Ürünler → Abonelikler* → *Abonelik oluştur*: ürün kimliği
   `drawguidance_premium` (değiştirirseniz `PREMIUM_PRODUCT_ID`). Temel plan ekleyin: kimlik `premium-monthly`, aylık
   otomatik yenilenen, fiyat **100 TRY** (Play diğer ülkelere çevirir; isterseniz ülke fiyatlarını düzenleyin). Ek süre
   (grace period) ve hesap askısı (account hold) önerilen ayarlarda kalsın. Temel planı **etkinleştirin**.
2. **Google Cloud projesi:** Play Console'un bağlı olduğu (ya da yeni) bir Cloud projesinde *API'ler ve Hizmetler →
   Kitaplık* → **Google Play Android Developer API**'yi etkinleştirin.
3. **Hizmet hesabı:** *IAM ve Yönetici → Hizmet hesapları → Oluştur* (ör. `play-billing`). Rol gerekmez. *Anahtarlar →
   Anahtar ekle → JSON* ile anahtarı indirin ve sunucuya gizli dosya olarak koyun; `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`
   bu dosyanın yolunu göstersin.
4. **Play Console izni:** Play Console → *Kullanıcılar ve izinler → Yeni kullanıcı davet et* → hizmet hesabının
   e-postası (`play-billing@<proje>.iam.gserviceaccount.com`). Uygulama izinlerinde bu uygulamayı seçip **"Finansal
   verileri, siparişleri ve iptal anketi yanıtlarını görüntüle"** ve **"Siparişleri ve abonelikleri yönet"** izinlerini
   verin. (İzinlerin etkinleşmesi birkaç saat sürebilir; o sürede API 401/403 döner.)
5. **Pub/Sub konusu:** Cloud Console → *Pub/Sub → Konu oluştur* (ör. `play-rtdn`). Konunun *İzinler* bölümünde
   `google-play-developer-notifications@system.gserviceaccount.com` hesabına **Pub/Sub Yayıncısı** rolünü verin.
6. **İtme aboneliği:** Konuya *Abonelik oluştur* → teslim türü **İtme (Push)**, uç noktası
   `https://<sunucunuz>/api/v1/billing/rtdn` (herkese açık HTTPS gerekir). Önerilir: *Kimlik doğrulamayı etkinleştir*
   → bir hizmet hesabı seçin, *Hedef kitle* olarak uç adresini yazın; sunucuda `PUBSUB_PUSH_AUDIENCE` = aynı adres,
   `PUBSUB_PUSH_SERVICE_ACCOUNT` = seçilen hizmet hesabının e-postası. Onay süresi 30 sn+, yeniden deneme politikası
   "üstel geri çekilme" uygundur.
7. **Play Console'da RTDN:** Uygulama → *Para kazanma kurulumu → Google Play Faturalandırma → Gerçek zamanlı geliştirici
   bildirimleri*: konu adı `projects/<proje>/topics/play-rtdn`, bildirim içeriği "Abonelikler ve tüm satın alma
   işlemleri". **"Test bildirimi gönder"**e basın: sunucu günlüğünde bildirim görünür ve
   `billing_webhook_events`'te `notificationName: "TEST"`, `status: "IGNORED"` kaydı oluşur.
8. **Lisans testçileri:** Play Console → *Ayarlar → Lisans testi*'ne test hesaplarını ekleyin; test aboneliklerinde
   yenileme dakikalar içinde olur (`testPurchase` alanı dolu gelir), RENEWED/EXPIRED akışı hızlıca denenebilir.
9. Sunucuyu yeniden başlatın: günlükte `Google Play hizmet hesabı yüklendi: …` görünmeli. Görünmüyorsa doğrulama
   `503 billing_not_configured` döner.

---

## 5. Diğer

- Sunucu sürümü **39.0.0** (`/api/health` → `"version": "39.0.0"`).
- Testler: 81/81 (58 eski + 23 yeni: plan tohumlama ve listeleme, `/me` öntanımlı Ücretsiz, doğrulama başarılı /
  hesap uyuşmazlığı / ayarsız / süresi dolmuş / geçersiz istekler, RTDN tekrar önleme, yenileme, iptal, süre dolması,
  ek süre, askı, geri dönüş, iade, yeniden deneme, bilinmeyen jeton, hareket geçmişi, dizinler, gerçek Play
  istemcisinin JWT/HTTP akışı yerel sunucuya karşı, Pub/Sub OIDC denetimi, Çizim Görevi birleştirme/eşzamanlılık/
  kullanıcı ayrımı/401/doğrulama).
