# v43 içerik dağıtımı (paket, bildirim, duyuru, güncelleme)

Sunucu cihazlara dört tür içerik gönderir: **paket**, **bildirim**, **duyuru** ve **uygulama güncellemesi**. Firebase
ve FCM kullanılmaz: cihaz sunucuyu yoklar. Bu belge kaydı, protokolü, aşamaları ve yük altındaki davranışı anlatır.

## Veri

| Koleksiyon | İçerik |
|---|---|
| `content_packages` | `{_id, name, mode: COMIC\|NOTES\|ART, version, status: DRAFT\|PUBLISHED, base, items: [{id, window, title, category?, url}]}`. `url`: `https://…` ya da `gs://<kova>/<nesne>`. |
| `app_notifications` | `{_id, title, body}` |
| `announcements` | `{_id, title, body, imageUrl}` |
| `app_releases` | `{_id: versionCode, versionName, message, minSupportedVersionCode}` |
| `devices` | `_id` = HMAC-SHA256(cihaz kimliği). `uid`, `info` (AES-256-GCM, cihaz anahtarı AAD), kurulu paketler ve sürümleri, `appVersionCode`, `inboxVersion`, `inboxChangedAt`, `lastSeenAt`. |
| `dispatches` | Gönderim: tür, içerik, hedef (`--herkes` / kullanıcılar), durum, kira (lease). |
| `deliveries` | Cihaz başına teslimat: `(deviceKey, kind, refId, version)` tekil. Aşama, deneme zamanı, hata. |

Modların pencereleri: çizgi romanda `objects`, `effects`, `backgrounds`, `balloons`, `shapes`, `characters`; not
almada `shapes`, `diagrams`; resimde `shapes`.

## Aşamalar

`QUEUED → INFO_SENT → INFO_RECEIVED → DATA_SENT → DELIVERED`. Bunlara ek olarak `FAILED` ve `CANCELLED` vardır.

1. **İşçi.** Her örnekte 10 sn'de bir çalışır. Gönderimi kirayla alır (birden çok örnek aynı işi yapmaz). Hedef
   cihazlar için teslimatları 1000'lik toplu, sırasız upsert ile açar.
   - Gereksizler atlanır: paket kuruluysa ya da cihazın uygulama sürümü yeniyse teslimat açılmaz.
   - Değişen cihazların `inboxVersion`'ı artar; bekleyen uzun yoklamalar uyandırılır.
2. **Yoklama.** Cihaz `GET /api/v1/inbox` ile yeni teslimatları alır. Alınanlar `INFO_SENT` olur.
3. **Bilgi alındı.** Cihaz `POST /api/v1/inbox/ack` ile toplu "bilgi alındı" bildirir. Veri hazırsa teslimat aynı
   yanıtta `DATA_SENT` olur.
4. **Veri.** Cihaz `GET /api/v1/inbox/{id}/data` ile içeriği alır.
   - Bildirim: metin. Duyuru: metin ve küçük resim. Paket: öğeler ve resim adresleri.
   - Resimler imzalı Cloud Storage adresleridir (V4, 24 saat): dosyalar sunucudan geçmez.
5. **Teslim.** Cihaz bütün resimleri indirip paketi kurunca toplu "teslim edildi" bildirir: `DELIVERED`.

Hesap değişince eski hesabın açık teslimatları iptal edilir. Çıkışta cihaz kaydı kaldırılır.

## Yoklama protokolü (Firebase yok)

- **ETag.** Yanıtta `ETag: "v<inboxVersion>"` döner. Cihaz bunu `If-None-Match` ile geri gönderir. Değişiklik yoksa
  yanıt **304** olur: tek indeksli okuma, gövde yok.
- **Uzun yoklama.** Uygulama açıkken `?wait=25` kullanılır. Sunucu yeni bir şey olana kadar ya da 25 sn dolana kadar
  bekletir; bildirim böylece anında gelir.
  - Bekleyenler sanal iş parçacıklarında, bellekte tutulur (`InboxSignals`).
  - Örnek başına tek bir izleyici 2 sn'de bir `devices.inboxChangedAt` alanına bakar. Başka örnekte açılan teslimat
    da birkaç saniyede uyandırır.
- **Yük koruma.**
  - Bekleyen sayısı `maxWaiters`'ı (5000) aşınca yeni istekler bekletilmeden döner.
  - `X-Poll-After` başlığı yoğunlukta 15 dk'dan 45 dk'ya uzar.
  - Cihaz önerilen aralığa %0–20 rastgele sapma ekler (binlerce cihaz aynı anda gelmez).
  - Hata olursa cihaz üstel bekler (5 sn → 5 dk).
- **Arka plan.** Uygulama kapalıyken WorkManager yaklaşık 15 dk'da bir yoklar. Sunucu daha uzun bir aralık önerdiyse
  o zamana kadar istek yapmaz.
- **Veri önbelleği.** Aynı (tür, içerik, sürüm) için hazırlanan veri ve imzalı adresler 30 dk bellekte tutulur. Aynı
  paketi isteyen binlerce cihaz için imza ve veritabanı okuması bir kez yapılır.

## Cihaz tarafı (Android)

Olay tablosu (`sync_events`, Room v22) iki yönlüdür:

- `direction = OUT`: giden işler.
- `direction = IN`: gelen teslimatlar. Her teslimatın bayrakları: yeni, bilgi onaylandı, veri alındı, işlendi
  (gösterildi / kuruldu), teslim onaylandı, kapatıldı.

İşleme:

- **Günlük indirme sınırı.** Her 16 KB'lık parçadan önce sorulur. Sınır aşılacaksa indirme bırakılır ve yarım paket
  silinir; ertesi gün paket baştan iner.
- **Kurulum.** Paket önce geçici klasöre iner. Tamamlanınca tek adımda `files/packs/<mod>/<paket>/` klasörüne taşınır.
  Çizgi romanın nesne, efekt, mekân ve karakterleri kendi pencerelerinde görünür. Ötekiler **Ekle → Paket
  içerikleri**'nde.
- **Gösterim.** Bildirimler Android bildirimi olur. Duyurular ana sayfada çıkar. Güncellemeler Play içi güncelleme
  penceresine yönlendirir.

## Taban paketler

Uygulamanın kendi pencere resimleri 3 paket hâlinde yüklüdür:

| Paket | Öğe |
|---|---|
| `base-comic` | 1723 |
| `base-notes` | 230 |
| `base-art` | 146 |

- Resimler `gs://dgpackets/base/<paket>/…` adresindedir. Kayıtları sunucu açılışta `base-packages/manifest.json`
  dosyasından oluşturur (`BasePackagesStartup`). Kova farklıysa bu adım atlanır.
- Cihaz taban paketleri sürüm 1 olarak kurulu sayar ve yeniden indirmez. Paket güncellenirse yalnız uygulamada
  olmayan öğeler pencerelere eklenir.

Taban paketleri yeniden üretmek ve yüklemek için iki adım çalıştırılır:

1. Resimleri dışa aktar:

   ```
   cd android && ./gradlew :app:testDebugUnitTest --tests '*V43BasePackExport' -PbasePackOut=/tmp/basepack
   ```

2. Kovaya yükle (MD5'i aynı olan dosyalar yeniden yüklenmez):

   ```
   java -jar drawguidance-server.jar cli taban-paket --klasor /tmp/basepack --yukle
   ```

İkinci komut `bundled-manifest.json` dosyasını da üretir. Bu dosya
`backend/src/main/resources/base-packages/manifest.json` olarak kopyalanır.

## Gizlilik

- Cihaz kimliği veritabanına yalnız HMAC-SHA256 olarak yazılır.
- Model, üretici, Android sürümü ve dil AES-256-GCM ile şifrelenir. Cihaz anahtarı AAD olarak kullanıldığı için bir
  kaydın şifreli bilgisi başka bir kayda taşınamaz.
- Anahtar `DEVICE_DATA_KEY` değişkenindedir ve üretimde zorunludur.
