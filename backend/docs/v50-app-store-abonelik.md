# v50 App Store abonelikleri (StoreKit 2)

iOS'taki Premium, Android'deki Google Play aboneliğinin karşılığıdır. İki mağazanın aboneliği aynı Firebase hesabında
aynı `subscriptions` belgesine yazılır: iOS'ta alınan Premium Android'de, Android'de alınan Premium iOS'ta açılır.

## Akış

1. iOS satın alır (`Product.purchase`, girişliyse `appAccountToken` = uid'den UUID; bkz. `AppleStates.appAccountToken`).
   Hak cihazda hemen açılır (Apple imzalı işlem). Girişsiz satın alma serbesttir (App Review kuralı).
2. Girişliyse iOS imzalı işlemi (`jwsRepresentation`) `POST /api/v1/subscriptions/verify-apple` ile gönderir.
   Sunucu imzayı Apple Root CA - G3'e kadar doğrular, durumu App Store Server API'den okur ve hesaba bağlar.
   Girişsiz alınmış abonelik, giriş yapılınca ya da "Geri yükle" ile bağlanır.
3. Apple, abonelik değişince `POST /api/v1/billing/apple-notifications` ucuna Server Notifications V2 gönderir
   (yenileme, iptal, iade, ödeme sorunu). Sunucu imzayı doğrular, durumu API'den yeniden okur.
4. `/subscriptions/me` 12 saatten eski App Store kaydını App Store'a yeniden sorar (kaçan bildirime karşı).
   Yanıtta `store` (`GOOGLE_PLAY` / `APP_STORE`): uygulama "aboneliği yönet"i buna göre açar.

Durum eşlemesi (Apple → sunucu): 1 etkin → `ACTIVE` (otomatik yenileme kapalıysa `CANCELED`, dönem sonuna kadar hak
var), 4 ek süre → `IN_GRACE_PERIOD`, 3 ödeme yeniden deneniyor → `ON_HOLD`, 2 süresi doldu / 5 geri alındı / iade →
`EXPIRED`. Abonelik belgesinde `purchaseToken = apple:<originalTransactionId>`.

## App Store Connect'te yapılacaklar

1. **Sözleşmeler:** İşletme → Sözleşmeler → "Ücretli Uygulamalar" (Paid Apps) sözleşmesini kabul et; banka ve vergi
   bilgilerini doldur. Bu bitmeden ürünler Sandbox'ta bile yüklenmez.
2. **Abonelik grubu ve ürün:** Uygulama → Para Kazanma → Abonelikler → grup oluştur: `Premium`. İçine otomatik
   yenilenen abonelik: Referans adı `Premium Monthly`, **Ürün kimliği `com.karapp.drawguidance.premium.monthly`**,
   süre 1 ay, fiyat Türkiye için 100 TL'ye en yakın kademe. Türkçe ve İngilizce görünen ad/açıklama, inceleme
   ekran görüntüsü. (Kimlik değişirse iOS Info.plist `DGPremiumProductId` ve sunucu `APPLE_PREMIUM_PRODUCT_IDS`.)
3. **Uygulama Apple ID:** Uygulama Bilgileri → Genel Bilgiler → Apple ID (sayı) → sunucuda `APPLE_APP_APPLE_ID`.
4. **Uygulama İçi Satın Alma anahtarı:** Kullanıcılar ve Erişim → Entegrasyonlar → Uygulama İçi Satın Alma →
   anahtar oluştur. `SubscriptionKey_XXXXXXXXXX.p8` bir kez indirilebilir: PC'de `yayin-anahtari\` klasörüne koy.
   Aynı sayfadaki **Issuer ID** → `APPLE_IAP_ISSUER_ID`, **Key ID** → `APPLE_IAP_KEY_ID`.
5. **Sunucu bildirim adresi:** Uygulama Bilgileri → App Store Server Notifications → Üretim ve Sandbox için
   `https://drawapi.ahmetkar.com/api/v1/billing/apple-notifications`, sürüm **Version 2**.
6. **Sandbox test hesabı:** Kullanıcılar ve Erişim → Sandbox → Test Hesabı (TestFlight için gerekmez).

## Sunucu ayarları

| Ortam değişkeni | Değer |
|---|---|
| `APPLE_APP_APPLE_ID` | 3. adımdaki sayı (üretim satın almalarının doğrulaması için zorunlu) |
| `APPLE_IAP_ISSUER_ID`, `APPLE_IAP_KEY_ID` | 4. adım |
| `APPLE_IAP_PRIVATE_KEY_FILE` | `.p8` dosyasının yolu (Swarm'da `/run/secrets/apple_iap.p8`, sır `apple_iap_key`) |
| `APPLE_PREMIUM_PRODUCT_IDS` | öntanımlı `com.karapp.drawguidance.premium.monthly` |
| `APPLE_ALLOW_SANDBOX` | `true` kalmalı (App Review ve TestFlight Sandbox kullanır) |

Swarm: `docker secret create apple_iap_key SubscriptionKey_XXXX.p8` (bkz. `docker/swarm/README.md`). Bu sır yoksa yığın
dağıtılamaz. Anahtar yokken doğrulama 503 `billing_not_configured` döner; cihazdaki hak yine çalışır.
