# v40 Güvenlik denetimi ve sertleştirme

Kapsam:

- **Sunucu (Spring Boot):** OWASP ASVS 4 (L2) ve OWASP API Security Top 10 (2023) maddelerine göre gözden geçirildi.
- **Docker Swarm üretim yığını:** `backend/docker/swarm/`.
- **Android istemcisi.**

Ağırlık dereceleri: **Yüksek**, **Orta**, **Düşük**, **Bilgi**.

## Bulgular

### Sunucu

| # | Ağırlık | Yer | Bulgu → Düzeltme | Durum |
|---|---|---|---|---|
| S1 | Yüksek | `billing/PubSubPushVerifier` | RTDN ucu, OIDC kitlesi ya da hizmet hesabı tanımlı değilse doğrulamasız çalışıyordu. Herkes kendi Google kimliğiyle her kitleye jeton üretebildiği için kitle tek başına yetmez. → `prod` profilinde `pubsub.required=true`: kitle ve e-posta tanımlı değilse uç 503 `rtdn_not_configured` döner. Jeton yalnız RS256 ile, 5 sn zaman aşımıyla doğrulanır. | Düzeltildi |
| S2 | Yüksek | `user/UserService` | Profil e-postası istek gövdesinden yazılıyordu. Başka birinin e-postasını yazan bir kullanıcı, o kişinin hesap silme isteğini kapatabilirdi (IDOR). → E-posta ve `emailVerified` Firebase jetonundan alınır. Gövdedeki e-posta "doğrulanmamış" olarak saklanır. Silme istekleri yalnız doğrulanmış e-postayla kapanır. | Düzeltildi |
| S3 | Yüksek | Hesap silme | Hesap silindikten sonra eldeki jeton (≤1 sa) yazmaya devam edebiliyordu. → `token_revocations` tablosu (uid özeti, TTL 2 sa) eklendi. `iat` silmeden önceyse POST/PUT/PATCH istekleri 401 `token_revoked` alır. | Düzeltildi |
| S4 | Orta | Bütün API | İstek gövdesi için boyut sınırı yoktu. → Gövde 64 KB ile sınırlı (hata raporu ucunun kendi sınırı ayrı). `Content-Length` fazlaysa ya da parçalı gövde sınırı aşarsa 413 `too_large` döner. Tomcat zaman aşımı ve form sınırları da eklendi. | Düzeltildi |
| S5 | Orta | Bütün API | İstek sınırı yalnız hata raporu ucundaydı. → `TokenBucketLimiter` ile üç sınır eklendi: IP başına (600/dk), kullanıcı başına yazma (120/sa) ve abonelik doğrulama (30/sa). Sınır aşılınca 429 ve `Retry-After` döner. Önde Traefik'te de IP sınırı var. | Düzeltildi |
| S6 | Orta | `GlobalExceptionHandler`, `application.yml` | Doğrulama hatalarında iç alan ve mesajlar istemciye gidiyordu, Whitelabel hata sayfası açıktı. → Genel mesajlar dönüyor, ayrıntı yalnız debug günlüğünde. `server.error.*=never`. | Düzeltildi |
| S7 | Orta | Actuator | → Yalnız `health` açık. Ayrıntı ve bileşen bilgisi gösterilmiyor, JMX kapalı. Traefik `/actuator`'ü dışarıya hiç yönlendirmiyor. | Düzeltildi |
| S8 | Orta | Vekil arkası IP | `X-Forwarded-For` her istemciden kabul edilebiliyordu. Bu, IP sınırını ve IP özetini atlatmaya izin veriyordu. → `prod` profilinde Tomcat RemoteIpValve kullanılıyor ve başlıklara yalnız `TRUSTED_PROXIES` (edge ağı 10.231.0.0/24) adreslerinden güveniliyor. Hata raporu ucu da aynı istemci IP mantığını kullanıyor. | Düzeltildi |
| S9 | Orta | Güvenlik başlıkları | → CSP `default-src 'none'; frame-ancestors 'none'`, `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY`, `nosniff` ve HSTS (1 yıl). | Düzeltildi |
| S10 | Orta | JWT | → Firebase jetonları yalnız RS256 ile kabul ediliyor. JWKS zaman aşımları eklendi. issuer, audience ve exp zaten doğrulanıyordu. | Düzeltildi |
| S11 | Orta | Sırlar | Parolalar URI ya da ortam değişkeni içindeydi. → `*_FILE` desteği (`SecretFilesEnvironment`): Docker sırları dosyadan okunuyor, değer hiçbir günlüğe yazılmıyor. Mongo parolası URI'den ayrıldı (`MONGO_APP_PASSWORD`). `Mongo.toString` parolayı gizliyor. | Düzeltildi |
| S12 | Orta | MongoDB yetkileri | Uygulama kullanıcısında `dbAdmin` yetkisi vardı. → Yalnız `readWrite` kaldı. Yedekleme için ayrı bir kullanıcı (`backup` rolü) var. Mevcut kullanıcıların parolası ve rolleri her açılışta eşitleniyor. | Düzeltildi |
| S13 | Orta | Bağımlılıklar | Spring Boot 3.3.5 (OSS desteği bitmiş; Tomcat, Spring ve Logback CVE'leri vardı). → Boot 3.5.16, Tomcat 10.1.61, Logback 1.5.38, zstd-jni 1.5.7-24, snappy-java 1.1.10.12. | Düzeltildi |
| S14 | Düşük | Mongo enjeksiyonu / toplu atama | Sorgular `Criteria`/`Query` ile tür güvenli kuruluyor, ham `$where` ya da JSON birleştirme yok. İstek gövdeleri ayrı DTO kayıtları; uid ve sahiplik jetondan geliyor. Ek bir sorun bulunmadı. | Denetlendi |
| S15 | Düşük | Sayfalama | Sayfalı tek uç (`GET /subscriptions/history?limit=`) servis içinde 1–200 aralığına kırpılıyor (`SubscriptionService`). Katalog uçları tam listeyi ETag ile veriyor. | Denetlendi |
| S16 | Bilgi | Satın alma doğrulaması | Yeniden oynatma: satın alma jetonu tek kullanıcıya bağlanıyor (benzersiz dizin). `prod` profilinde `obfuscatedAccountId` eşleşmesi zorunlu (`BILLING_REQUIRE_ACCOUNT_ID=true`); uygulama bu kimliği her zaman gönderiyor. | Düzeltildi |
| S17 | Bilgi | Günlükler | Jeton, e-posta ya da sır yazan günlük satırı bulunmadı. IP adresleri tuzlu özet olarak tutuluyor. `prod` profilinde günlük düzeyi WARN. | Denetlendi |
| S18 | Bilgi | CORS | İstendiği gibi değiştirilmedi. Swarm'da yalnız `https://drawguidance.ahmetkar.com` izinli. | Değişmedi |

### Altyapı (Docker Swarm)

| # | Ağırlık | Yer | Bulgu → Düzeltme | Durum |
|---|---|---|---|---|
| A1 | Yüksek | `Dockerfile` | İmaj root ile ve JDK ile çalışıyordu. → Çok aşamalı derleme: yalnız JRE ve uid 10001 kalıyor, setuid/setgid bitleri siliniyor. Jar salt okunur. Konteyner ayarlı JVM ve HEALTHCHECK eklendi. `prod` profili öntanımlı. | Düzeltildi |
| A2 | Yüksek | `swarm/stack.yml` | TLS Traefik v3'te sonlanıyor: Let's Encrypt, TLS 1.2+ ile AEAD şifreleri, `sniStrict`, HTTP→HTTPS yönlendirmesi ve HSTS. Pano kapalı. Docker soketi doğrudan değil, salt okunan `socket-proxy` üzerinden okunuyor. | Yeni |
| A3 | Orta | `swarm/stack.yml` | api için salt okunur kök dosya sistemi, tmpfs `/tmp` ve `cap_drop: ALL` var. Güncelleme sırası start-first, hata olursa otomatik geri alınıyor. Kaynak sınırları ve 2 kopya var. | Yeni |
| A4 | Orta | MongoDB | Kopya kümesinde kimlik doğrulama ve anahtar dosyası açık. mongod root değil `mongodb` kullanıcısıyla çalışıyor. Kapısı yayımlanmıyor ve veritabanı düğümüne sabitlenmiş. | Yeni |
| A5 | Orta | Ağlar | Bütün overlay ağlar şifreli. `backend` ve `socket` ağları `internal`. | Yeni |
| A6 | Orta | Sırlar | 7 Docker sırrının hepsi `external`. Hiçbir değer dosyada, imajda ya da ortam değişkeninde değil. | Yeni |
| A7 | Orta | Yedek | Günlük `mongodump --oplog` alınıyor. Parola komut satırında değil, tmpfs'teki bir config dosyasında. 14 gün saklanıyor. | Yeni |
| A8 | Orta | İmaj (Swarm'da bulundu) | Swarm tmpfs bağlamaları `noexec` olduğu için zstd/snappy yerel kitaplıkları `/tmp`'den yüklenemiyordu ve api açılmıyordu. → Kitaplıklar derlemede `/app/native`'e çıkarılıyor. | Düzeltildi |

### Android

| # | Ağırlık | Yer | Bulgu → Düzeltme | Durum |
|---|---|---|---|---|
| M1 | Yüksek | Manifest | `usesCleartextTraffic="true"` yayın derlemesinde de geçerliydi. → `network_security_config` eklendi. Yayında düz HTTP yalnız localhost'a izinli ve yalnız sistem CA'larına güveniliyor. Hata ayıklama derlemesinde (`src/debug`) yerel ağa düz HTTP serbest. | Düzeltildi |
| M2 | Yüksek | Yedekleme | `allowBackup="true"` kuralsızdı. Firebase oturumu (yenileme jetonu), sunucu adresi ve çökme kayıtları buluta ve yeni cihaza gidiyordu. → `backup_rules.xml` ve `data_extraction_rules.xml` bunları dışarıda bırakıyor. Çizimler ve veritabanı yedeklenmeye devam ediyor. | Düzeltildi |
| M3 | Orta | `ServerConfig` | Yayın derlemesi, kayıtlı (eski sürümden ya da yedekten gelen) sunucu adresini kullanabiliyordu. Bu, jetonların başka sunucuya gitmesine yol açabilirdi. → Yayında adres sabit (`allowOverride = BuildConfig.DEBUG`). | Düzeltildi |
| M4 | Orta | HTTP günlüğü | Hata ayıklama günlüğü `Authorization` başlığını açık yazıyordu. → `Authorization`, `Cookie` ve `Set-Cookie` gizleniyor. Yayında günlük zaten kapalı. | Düzeltildi |
| M5 | Düşük | R8 | Küçültme ve karartma kapalıydı. → Yayında `isMinifyEnabled` ve `isShrinkResources` açık, keep kuralları eklendi. `bundleRelease` derleniyor. Mapping'de Room `_Impl`, serileştiriciler, Worker'lar ve Credentials sağlayıcısı korunuyor. | Düzeltildi (cihazda denenmeli) |
| M6 | Bilgi | Dışa açık bileşenler | Yalnız başlatıcı `MainActivity` dışa açık. `server_url` parametresi yalnız hata ayıklamada işleniyor. FileProvider dar kapsamlı. Deep link, WebView ve ham SQL (`@RawQuery`) yok; göçler sabit SQL kullanıyor. | Denetlendi |
| M7 | Bilgi | Sertifika sabitleme | Uygulanmadı. Let's Encrypt sertifikaları 90 günde bir yenileniyor ve ara CA'lar habersiz değişebiliyor. Yanlış bir pin, uygulama güncellenene dek bütün kullanıcıları sunucudan koparır. Güvenli olması için en az iki yedek anahtarla (ör. kendi CSR'ımızla sabit anahtar) ve uzaktan kapatılabilir biçimde kurulmalı. | Bilinçli olarak yapılmadı |

## Kalan riskler

1. **Spring Boot 3.5'in OSS desteği Haziran 2026'da bitti.** Boot 4 (Spring 7) geçişi planlanmalı. O zamana dek Tomcat ve Logback yamaları elle izlenmeli.
2. **Sunucudaki istek sınırları her kopyada ayrı tutuluyor.** 2 kopyada toplam sınır iki katı olur. Traefik'teki IP sınırı bütün kopyaların önünde ama tek Traefik görevine bağlı.
3. **Jeton iptali yalnız yazmaları kesiyor ve 30 saniyelik önbelleği var.** Silinen hesabın jetonu, süresi bitene dek (≤1 sa) okuma yapabilir. Ancak hesap silindiği için okunacak veri yok. Firebase `checkRevoked` (ağ çağrısı) kullanılmıyor.
4. **MongoDB tek üyeli.** Düğüm kaybında hem veri hem yedek gider. Üç üyeye geçilmeli (README §9) ve yedekler sunucu dışına taşınmalı.
5. **R8 cihazda denenmedi.** Derleme ve mapping denetlendi, ama yayından önce iç test kanalında giriş, eşitleme, satın alma ve reklam akışları denenmeli. `mapping.txt` her sürümde saklanmalı.
6. **Sertifika sabitleme yok** (M7).
7. **Firebase Installations dosyaları** (`PersistedInstallation*.json`) yedekte kalıyor. Bunlar yalnız kurulum kimliği taşıyor, oturum değil.
8. **Traefik ve socket-proxy** yönetici düğümde koşuyor. Socket-proxy'nin ele geçirilmesi yalnız okuma sağlar, ama Docker'ın bütün servis ve görev listesini görür.

## Doğrulama

- **Sunucu testleri:** `backend/gradlew -p backend test` → 118 test, hepsi geçti. Yeni testler:
  - `HardeningApiTest` (9 test);
  - `ProdProfileTest` (3 test);
  - `SecretFilesEnvironmentTest` (4 test);
  - `PubSubPushVerifierTest`'e 1 test eklendi.
- **Android:** `V40SecurityTest` (4 test). İlgili `V35ServerPortTest`, `ServerConfigNormalizeTest` ve `SettingsFlowTest` de geçti. `:app:bundleRelease` R8 açıkken derlendi.
- **Swarm yerel denemesi:** Tek düğümlü bir kümede, sahte sırlarla yapıldı. Doğrulananlar:
  - 5 servisin hepsi sağlıklı açıldı;
  - HTTP→HTTPS 301 dönüyor, HTTPS isteği 200 dönüyor ve HSTS ile CSP başlıkları var;
  - `/actuator` 404, kimliksiz PUT 401, imzasız RTDN 401 dönüyor;
  - TLS 1.1 reddediliyor; 150 eşzamanlı istekte 56'sı 429 aldı;
  - yedek alındı ve README'deki geri yükleme komutuyla geri yüklendi;
  - zorla güncelleme ve geri alma çalıştı.

  Kum havuzunda IPVS olmadığı için socket-proxy `dnsrr` kipine alındı. ACME sertifikası yerelde alınamadığından bu deneme `sniStrict` kapalı bir kopyayla yapıldı.
