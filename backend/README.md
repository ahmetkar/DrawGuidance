# DrawGuidance sunucusu (v33)

Spring Boot 3 (Java 21) + MongoDB 7. İki iş yapar:

- **Katalog** (herkese açık, salt okunur): şekil şablonları (yapay zekâ eşleştirme tablosu), hazır vektör şekiller ve
  modların temel sinir ağı ağırlıkları. Jar içindeki tohum dosyasından açılışta eşitlenir.
- **Kullanıcılar**: Firebase ID jetonuyla doğrulanan profil kaydı.

Kullanıcı tuvalleri, klasörleri, kaydedilen resimleri, 3B şekilleri, varlıkları ve eşitleme uçları **sunucuda yoktur**
(v33'te kaldırıldı; bunlar yalnız telefonda tutulur). Kapı (v35'ten beri 4747; önce 3000) ve sağlık ucu (`/api/health`) değişmedi.

## Çalıştırma (Docker)

```
cd backend/docker
copy .env.example .env      (parolaları değiştirin; Windows betikleri bunu kendisi yapar)
docker compose up -d
```

- **API:** `http://<bilgisayarın IP'si>:4747` (tüm arayüzlerde dinler; uygulamada Ayarlar → **Ağda ara**).
- **MongoDB (bu bilgisayardan):** `mongodb://<yönetici>:<parola>@127.0.0.1:27017/?directConnection=true&authSource=admin`.
- **Firebase projesi:** `.env` içinde `FIREBASE_PROJECT_ID` (öntanımlı `drawguidance`).
- **Hazır jarla:** `./gradlew bootJar` sonra `API_BUILD_TARGET=prebuilt docker compose up -d --build`.
- **Üç üyeli kopya kümesi:** `.env` içinde `RS_MEMBERS=mongo1:27017,mongo2:27017,mongo3:27017` ve
  `docker compose --profile rs3 up -d`.
- **Tarayıcıdan yönetim:** `docker compose --profile tools up -d` → `http://localhost:8081`.
- **Parçalı küme:** `docker compose -f docker-compose.sharded.yml up -d`. `users` koleksiyonu `firebaseUid`
  (benzersiz) anahtarıyla parçalanır; katalog koleksiyonları küçüktür, parçalanmaz.

Windows'ta `Sunucuyu-Baslat.bat` / `Sunucuyu-Durdur.bat` (DrawGuidance klasöründe) Docker Desktop'ı başlatıp bunları
yapar; Docker yoksa uygulama sunucusuz çalışmaya devam eder.

## Uçlar

Hata gövdesi her yerde aynı: `{"error": "<kod>", "message": "<Türkçe açıklama>"}`.

| Uç | Açıklama |
|---|---|
| `GET /api/health` | durum, sürüm, veritabanı, küme türü (STANDALONE / REPLICA_SET / SHARDED) |
| `GET /api/v1/catalog/manifest` | `{version, updatedAt, counts: {templates, vectors, modeNets}, binary}` |
| `GET /api/v1/catalog/templates` | `{version, items: [Template]}` |
| `GET /api/v1/catalog/vectors` | `{version, items: [Vector]}` ya da ikili (v37) |
| `GET /api/v1/catalog/mode-nets` | `{version, items: [ModeNet]}` ya da ikili (v37) |
| `PUT /api/v1/users/me` | kullanıcı kaydı oluştur/güncelle (`firebaseUid` jetondaki `sub` olmalı) |
| `GET /api/v1/users/me` | kullanıcı kaydı (yoksa 404) |
| `DELETE /api/v1/users/me` | kaydı sil (204) |
| `POST /api/v1/reports` | hata/çökme raporu (v37; kimlik isteğe bağlı) → 202 `{id}` |

- **Katalog:** kimlik gerekmez (gönderilen jeton yok sayılır). Her yanıtın ETag'i `"cat-<sürüm>"`, `If-None-Match`
  tutarsa 304. Bölümler `Accept-Encoding: gzip` ile önceden sıkıştırılmış gelir. v37: vektörler ve temel ağlar
  `Accept: application/vnd.drawguidance.catalog+bin` (ya da `?format=bin`) ile ikili gelir, ETag'i `"cat-<sürüm>-bin"`;
  öntanımlı JSON. Ayrıntı ve ikili düzen: [docs/v37-api.md](docs/v37-api.md).
- **Kullanıcılar:** `Authorization: Bearer <Firebase ID token>`. İmza Google securetoken anahtarlarıyla (JWKS),
  `iss = https://securetoken.google.com/<proje>`, `aud = <proje>`, `exp`/`iat` (60 sn pay), `sub` boş değil.
  Jeton yok → 401 `unauthorized`, geçersiz/süresi dolmuş → 401 `invalid_token`, Google anahtarları alınamazsa → 503
  `auth_unavailable`. `firebaseUid` jetondakinden farklıysa 403 `uid_mismatch`. İlk kayıtta `id` UUID olmalı
  (400 `invalid_id`); kayıt varsa `_id` korunur. `birthDate` `yyyy-MM-dd` ya da null, gelecekte olamaz
  (400 `invalid_birth_date`).

## Katalog tohum dosyası

`src/main/resources/catalog/catalog.json.gz` (gzip'li JSON):
`{"version": 33000, "generatedAt": "...", "templates": [...], "vectors": [...], "modeNets": [...]}`.
Anahtarlar: `Template.key`, `Vector.id`, `ModeNet.mode` (MongoDB'de `_id`). Sunucu öğelerin içeriğini yorumlamaz.

Açılışta (sunucu istek kabul etmeden önce) eşitlenir: veritabanında hiç eşitleme yoksa, dosya sürümü
veritabanındakinden büyükse ya da dosyada öğe varken katalog koleksiyonları boşsa. Eksik öğe eklenir, değişen
güncellenir, dosyada olmayan silinir; sürüm en son yazılır. Eski bir jar (küçük sürüm) kataloğu geri almaz. Dosya
yoksa katalog boş sayılır (sürüm 0, uyarı yazılır), veritabanında daha yeni katalog varsa korunur. Birden çok sunucu
aynı anda açılırsa eşitlemeyi `catalog_meta.lock` kirasını alan tek sunucu yapar. Dosya yolu
`drawguidance.catalog.seed-resource` (ortam değişkeni `CATALOG_SEED_RESOURCE`) ile değiştirilebilir.

## Koleksiyonlar

`users` (`firebaseUid` benzersiz), `catalog_templates`, `catalog_vectors`, `catalog_modenets`, `catalog_meta`,
`error_reports` (v37, `receivedAt` dizinli), `error_report_groups` (aynı hatanın sayısı).
v30'dan kalan bir Docker biriminde eski koleksiyonlar (`canvases`, `folders`, `images`, `models3d`, `assets`,
`files.*`) varsa sunucu onlara dokunmaz; gerekirse elle silinebilir.

## Yüksek trafik için

- Java 21 sanal iş parçacıkları, Tomcat 10.000 bağlantı.
- Katalog: her istekte yalnız küçük `catalog_meta` okunur; bölüm gövdeleri sürüm başına bir kez hazırlanıp (JSON ve
  gzip) bellekte tutulur; istemciler ETag ile yalnız sürüm değişince indirir.
- MongoDB sürücüsü: 200 bağlantılık havuz, kısa zaman aşımları, yeniden denenen okuma/yazmalar, zstd/snappy/zlib
  kablo sıkıştırması, `majority` yazma onayı (`.env` ile `w1`).
- Kullanıcı kaydı tek komutta (`findAndModify`, upsert); eşzamanlı ilk kayıtlarda tek belge kalır.

## Geliştirme

```
./gradlew test        # gerçek MongoDB'yle (Testcontainers; Docker gerekir)
./gradlew bootJar     # build/libs/drawguidance-server.jar
MONGODB_URI=mongodb://localhost:27017/drawguidance java -jar build/libs/drawguidance-server.jar
```
