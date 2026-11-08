package com.karapp.drawguidance.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sunucu ayarları ({@code drawguidance.*}).
 *
 * @param version   sunucu sürümü (sağlık ucunda görünür)
 * @param mongo     MongoDB istemci ayarları (bağlantı havuzu, sıkıştırma, okuma/yazma tercihleri, parçalama)
 * @param firebase  Firebase ID jetonu doğrulaması
 * @param catalog   katalog tohum dosyası
 * @param reports   v37 hata/çökme raporu ucunun sınırları
 * @param billing   v39 Google Play abonelikleri (planlar, satın alma doğrulaması, gerçek zamanlı bildirimler)
 * @param web       v40 herkese açık web sitesi: CORS, vekil arkasında istemci adresi, form uçlarının istek sınırı
 * @param slack     v40 Slack bildirimleri (gelen web kancası)
 * @param limits    v40 güvenlik: genel istek gövdesi sınırı ve kimlikli/pahalı uçların istek sınırları
 */
@ConfigurationProperties(prefix = "drawguidance")
public record DrawGuidanceProperties(String version, Mongo mongo, Firebase firebase, Catalog catalog, Reports reports, Billing billing,
                                     Web web, Slack slack, Limits limits) {

    public DrawGuidanceProperties {
        if (version == null) version = "37.0.0";
        if (mongo == null) mongo = new Mongo(null, null, null, null, null, null, null, null, null, null, null);
        if (firebase == null) firebase = new Firebase(null, null);
        if (catalog == null) catalog = new Catalog(null);
        if (reports == null) reports = new Reports(null, null, null, null);
        if (billing == null) billing = new Billing(null, null, null, null, null, null);
        if (web == null) web = new Web(null, null, null, null, null, null, null);
        if (slack == null) slack = new Slack(null);
        if (limits == null) limits = new Limits(null, null, null, null, null, null, null);
    }

    /**
     * v40 güvenlik sınırları (bellekte jeton kovası, sunucu kopyası başına; Traefik'teki genel sınır bunun önündedir).
     *
     * @param maxBodyBytes   {@code /api/**} istek gövdesinin genel üst sınırı (bayt; aşılırsa 413 {@code too_large}).
     *                       Rapor ucu kendi sınırını ({@code reports.max-body-bytes}) kullanır
     * @param ipLimit        IP başına {@code /api/v1/**} istek sınırı (pencere başına; aşılırsa 429)
     * @param ipWindow       IP sınırının penceresi
     * @param writeLimit     kullanıcı (Firebase uid) başına yazma isteği sınırı ({@code PUT/POST/DELETE} kimlikli uçlar)
     * @param writeWindow    yazma sınırının penceresi
     * @param verifyLimit    kullanıcı başına abonelik doğrulaması sınırı (her biri Google Play API çağrısı)
     * @param verifyWindow   doğrulama sınırının penceresi
     */
    public record Limits(Integer maxBodyBytes, Integer ipLimit, java.time.Duration ipWindow, Integer writeLimit,
                         java.time.Duration writeWindow, Integer verifyLimit, java.time.Duration verifyWindow) {
        public Limits {
            if (maxBodyBytes == null || maxBodyBytes <= 0) maxBodyBytes = 64 * 1024;
            if (ipLimit == null || ipLimit <= 0) ipLimit = 600;
            if (ipWindow == null || ipWindow.isNegative() || ipWindow.isZero()) ipWindow = java.time.Duration.ofMinutes(1);
            if (writeLimit == null || writeLimit <= 0) writeLimit = 120;
            if (writeWindow == null || writeWindow.isNegative() || writeWindow.isZero()) writeWindow = java.time.Duration.ofHours(1);
            if (verifyLimit == null || verifyLimit <= 0) verifyLimit = 30;
            if (verifyWindow == null || verifyWindow.isNegative() || verifyWindow.isZero()) verifyWindow = java.time.Duration.ofHours(1);
        }
    }

    /**
     * v40 herkese açık web sitesi (Next.js, Vercel) ve onun çağırdığı uçlar.
     *
     * @param allowedOrigins  {@code /api/v1/**} için CORS'a izinli kökenler (tam adres, ör.
     *                        {@code https://drawguidance.ahmetkar.com}); ortam değişkeninde virgülle ayrılır
     * @param allowedOriginPatterns  isteğe bağlı köken kalıpları (ör. Vercel önizlemeleri için
     *                        {@code https://drawguidance-*.vercel.app}); öntanımlı boş
     * @param behindProxy     {@code true}: sunucu güvenilen bir ters vekilin (nginx, Caddy …) arkasında; istemci
     *                        adresi {@code X-Forwarded-For} başlığından okunur. {@code false} (öntanımlı): başlık yok
     *                        sayılır (sahte başlıkla istek sınırı atlatılamasın)
     * @param proxyHops       {@code X-Forwarded-For} listesinde sağdan kaçıncı adresin istemci olduğu (tek vekil: 1)
     * @param formLimit       IP başına, uç başına pencere içinde en çok form isteği (silme talebi, destek mesajı)
     * @param formWindow      form istek sınırının penceresi (kova bu sürede tamamen dolar)
     * @param ipHashSalt      IP özetinin gizli tuzu (HMAC anahtarı). Boşsa açılışta rastgele üretilir (özetler
     *                        yeniden başlatmadan sonra eşleşmez)
     */
    public record Web(java.util.List<String> allowedOrigins, java.util.List<String> allowedOriginPatterns, Boolean behindProxy,
                      Integer proxyHops, Integer formLimit, java.time.Duration formWindow, String ipHashSalt) {
        public static final java.util.List<String> DEFAULT_ORIGINS = java.util.List.of("https://drawguidance.ahmetkar.com", "http://localhost:3000");

        public Web {
            allowedOrigins = allowedOrigins == null ? DEFAULT_ORIGINS : clean(allowedOrigins);
            allowedOriginPatterns = allowedOriginPatterns == null ? java.util.List.of() : clean(allowedOriginPatterns);
            if (behindProxy == null) behindProxy = false;
            if (proxyHops == null || proxyHops < 1) proxyHops = 1;
            if (formLimit == null || formLimit <= 0) formLimit = 5;
            if (formWindow == null || formWindow.isNegative() || formWindow.isZero()) formWindow = java.time.Duration.ofHours(1);
            if (ipHashSalt != null && ipHashSalt.isBlank()) ipHashSalt = null;
        }

        private static java.util.List<String> clean(java.util.List<String> list) {
            return list.stream().filter(o -> o != null && !o.isBlank()).map(String::trim).toList();
        }

        /** Tuz günlüğe ya da hata iletisine düşmesin. */
        @Override
        public String toString() {
            return "Web[allowedOrigins=" + allowedOrigins + ", allowedOriginPatterns=" + allowedOriginPatterns + ", behindProxy=" + behindProxy + ", proxyHops=" + proxyHops
                    + ", formLimit=" + formLimit + ", formWindow=" + formWindow + ", ipHashSalt=" + (ipHashSalt == null ? "yok" : "***") + "]";
        }
    }

    /**
     * v40 Slack gelen web kancası. Adres bir sırdır: günlüğe yazılmaz, yanıtta dönmez ({@link #toString} gizler).
     *
     * @param webhookUrl  {@code https://hooks.slack.com/services/...}; boşsa bildirim gönderilmez
     */
    public record Slack(String webhookUrl) {
        public Slack {
            if (webhookUrl != null && webhookUrl.isBlank()) webhookUrl = null;
            if (webhookUrl != null) webhookUrl = webhookUrl.trim();
        }

        public boolean enabled() { return webhookUrl != null; }

        @Override
        public String toString() { return "Slack[webhookUrl=" + (webhookUrl == null ? "yok" : "***") + "]"; }
    }

    /**
     * v39 Google Play abonelikleri.
     *
     * @param packageName         uygulamanın paket adı (doğrulama isteğindeki ve bildirimdeki ad bununla aynı olmalı)
     * @param serviceAccountFile  Play Console yetkili hizmet hesabının JSON anahtar dosyası (yol). Boşsa faturalama
     *                            yapılandırılmamış sayılır: doğrulama 503 {@code billing_not_configured} döner
     * @param apiBaseUrl          Google Play Developer API adresi (testlerde yerel sunucu)
     * @param requireAccountId    {@code true}: Play kaydında {@code obfuscatedExternalAccountId} yoksa doğrulama
     *                            reddedilir. {@code false} (öntanımlı): yoksa jeton ilk doğrulayan hesaba bağlanır
     *                            (Play Store'da kod kullanımı gibi uygulama dışı satın almalar)
     * @param pubsub              Pub/Sub itme isteklerinin OIDC jetonu doğrulaması
     * @param premium             Premium planın Play ürünü ve gösterilen fiyatı
     */
    public record Billing(String packageName, String serviceAccountFile, String apiBaseUrl, Boolean requireAccountId,
                          PubSub pubsub, Premium premium) {
        public static final String DEFAULT_PACKAGE = "com.karapp.drawguidance";
        public static final String DEFAULT_API = "https://androidpublisher.googleapis.com";

        public Billing {
            if (packageName == null || packageName.isBlank()) packageName = DEFAULT_PACKAGE;
            if (serviceAccountFile != null && serviceAccountFile.isBlank()) serviceAccountFile = null;
            if (apiBaseUrl == null || apiBaseUrl.isBlank()) apiBaseUrl = DEFAULT_API;
            if (requireAccountId == null) requireAccountId = false;
            if (pubsub == null) pubsub = new PubSub(null, null, null, null);
            if (premium == null) premium = new Premium(null, null, null, null, null, null);
        }
    }

    /**
     * v39 Pub/Sub itme aboneliğinin kimlik doğrulaması. {@code audience} boşsa doğrulama yapılmaz (bildirim yalnız
     * Play'den durumu yeniden okumayı tetikler; sahte bildirim abonelik veremez).
     *
     * @param audience             itme aboneliğinde ayarlanan hedef kitle ({@code aud}); genellikle uç adresi
     * @param serviceAccountEmail  jetonun {@code email} değeri (itme aboneliğinin hizmet hesabı); boşsa denetlenmez
     * @param jwkSetUri            Google OIDC açık anahtarları
     * @param required             v40 {@code true} (üretim profili): doğrulama zorunlu. {@code audience} ya da
     *                             {@code serviceAccountEmail} boşsa uç her isteği 503 {@code rtdn_not_configured} ile
     *                             reddeder (yalnız hedef kitle yetmez: herkes kendi Google hizmet hesabıyla istediği
     *                             hedef kitleye jeton üretebilir; asıl güvence {@code email} denetimidir)
     */
    public record PubSub(String audience, String serviceAccountEmail, String jwkSetUri, Boolean required) {
        public static final String GOOGLE_CERTS = "https://www.googleapis.com/oauth2/v3/certs";

        public PubSub {
            if (required == null) required = false;
            if (audience != null && audience.isBlank()) audience = null;
            if (serviceAccountEmail != null && serviceAccountEmail.isBlank()) serviceAccountEmail = null;
            if (jwkSetUri == null || jwkSetUri.isBlank()) jwkSetUri = GOOGLE_CERTS;
        }
    }

    /**
     * v39 Premium plan. Gösterilen fiyat yalnız yedektir: Play fiyatı ülkeye göre çevirir, uygulama Play'in
     * {@code formattedPrice} değerini gösterir.
     *
     * @param productId     Play Console abonelik ürün kimliği
     * @param basePlanId    temel plan kimliği
     * @param priceMicros   fiyat (milyonda bir birim; 100 TL = 100000000)
     * @param currency      para birimi (ISO 4217)
     * @param priceDisplay  gösterilen fiyat yazısı
     * @param period        yenileme dönemi (ISO 8601, ör. P1M)
     */
    public record Premium(String productId, String basePlanId, Long priceMicros, String currency, String priceDisplay, String period) {
        public Premium {
            if (productId == null || productId.isBlank()) productId = "drawguidance_premium";
            if (basePlanId == null || basePlanId.isBlank()) basePlanId = "premium-monthly";
            if (priceMicros == null || priceMicros < 0) priceMicros = 100_000_000L;
            if (currency == null || currency.isBlank()) currency = "TRY";
            if (priceDisplay == null || priceDisplay.isBlank()) priceDisplay = "100 TL";
            if (period == null || period.isBlank()) period = "P1M";
        }
    }

    /**
     * v37 {@code POST /api/v1/reports} sınırları (bellekte sayaç; sunucu başına).
     *
     * @param maxBodyBytes     istek gövdesinin üst sınırı (bayt; aşılırsa 413)
     * @param perInstallLimit  bir kurulumdan ({@code installId}) pencere başına en çok rapor (aşılırsa 429)
     * @param perIpLimit       bir IP adresinden pencere başına en çok istek (aşılırsa 429)
     * @param window           sayaç penceresi
     */
    public record Reports(Integer maxBodyBytes, Integer perInstallLimit, Integer perIpLimit, java.time.Duration window) {
        public Reports {
            if (maxBodyBytes == null || maxBodyBytes <= 0) maxBodyBytes = 256 * 1024;
            if (perInstallLimit == null || perInstallLimit <= 0) perInstallLimit = 30;
            if (perIpLimit == null || perIpLimit <= 0) perIpLimit = 300;
            if (window == null || window.isNegative() || window.isZero()) window = java.time.Duration.ofHours(1);
        }
    }

    /**
     * @param maxPoolSize     sunucu başına en çok bağlantı (yüksek trafik: 200)
     * @param minPoolSize     sıcak tutulan bağlantılar
     * @param maxConnecting   aynı anda açılan bağlantı (bağlantı fırtınasını önler)
     * @param compressors     kablo sıkıştırması (zstd, snappy, zlib)
     * @param readPreference  okuma tercihi (primaryPreferred)
     * @param writeConcern    yazma onayı (majority: kalıcı; w1: hızlı)
     * @param sharding        parçalama (mongos'a bağlıyken koleksiyonları parçala)
     * @param timeoutsMs      bağlantı/sunucu seçimi zaman aşımı (ms)
     * @param username        v40 isteğe bağlı kullanıcı adı: parola bağlantı adresine (URI) yazılmadan ayrıca verilir
     *                        ({@code MONGO_APP_USER}); boşsa URI'deki kimlik kullanılır
     * @param password        v40 isteğe bağlı parola ({@code MONGO_APP_PASSWORD} ya da Docker sırrından
     *                        {@code MONGO_APP_PASSWORD_FILE}); günlüğe yazılmaz
     * @param authSource      v40 kimlik veritabanı; boşsa URI'deki {@code authSource}, o da yoksa uygulama veritabanı
     */
    public record Mongo(Integer maxPoolSize, Integer minPoolSize, Integer maxConnecting, String compressors, String readPreference,
                        String writeConcern, Sharding sharding, Integer timeoutsMs, String username, String password, String authSource) {
        public Mongo {
            if (username != null && username.isBlank()) username = null;
            if (password != null && password.isEmpty()) password = null;
            if (authSource != null && authSource.isBlank()) authSource = null;
            if (maxPoolSize == null) maxPoolSize = 200;
            if (minPoolSize == null) minPoolSize = 10;
            if (maxConnecting == null) maxConnecting = 8;
            if (compressors == null) compressors = "zstd,snappy,zlib";
            if (readPreference == null) readPreference = "primaryPreferred";
            if (writeConcern == null) writeConcern = "majority";
            if (sharding == null) sharding = new Sharding(false);
            if (timeoutsMs == null) timeoutsMs = 5000;
        }

        /** Parola günlüğe ya da hata iletisine düşmesin. */
        @Override
        public String toString() {
            return "Mongo[maxPoolSize=" + maxPoolSize + ", minPoolSize=" + minPoolSize + ", readPreference=" + readPreference
                    + ", writeConcern=" + writeConcern + ", sharding=" + sharding + ", username=" + username
                    + ", password=" + (password == null ? "yok" : "***") + ", authSource=" + authSource + "]";
        }
    }

    /** @param enabled mongos'a bağlıyken açılışta parçalama komutları çalışır. */
    public record Sharding(boolean enabled) {}

    /**
     * @param projectId  Firebase proje kimliği: jetonun {@code aud} değeri ve {@code iss} =
     *                   {@code https://securetoken.google.com/<projectId>}
     * @param jwkSetUri  Google securetoken açık anahtarları (JWKS)
     */
    public record Firebase(String projectId, String jwkSetUri) {
        public static final String GOOGLE_JWKS = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

        public Firebase {
            if (projectId == null || projectId.isBlank()) projectId = "drawguidance";
            if (jwkSetUri == null || jwkSetUri.isBlank()) jwkSetUri = GOOGLE_JWKS;
        }

        public String issuer() { return "https://securetoken.google.com/" + projectId; }
    }

    /** @param seedResource katalog tohum dosyası (gzip'li JSON; {@code classpath:} ya da {@code file:} adresi) */
    public record Catalog(String seedResource) {
        public static final String DEFAULT_SEED = "classpath:catalog/catalog.json.gz";

        public Catalog {
            if (seedResource == null || seedResource.isBlank()) seedResource = DEFAULT_SEED;
        }
    }
}
