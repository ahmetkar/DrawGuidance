package com.karapp.drawguidance

import kotlinx.coroutines.launch

import android.app.Application
import com.karapp.drawguidance.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

open class DrawGuidanceApp : Application() {

    /** Backend adresi. Testlerde farklı bir adrese yönlendirmek için override edilebilir. */
    protected open val apiBaseUrl: String = BuildConfig.API_BASE_URL

    /** v30 uygulama Ana sayfayla açılır (eski testler Tuvallerim'den başlar). */
    open val startOnHome: Boolean = true

    /** v30 açılışta karşılama ekranı (uygulamanın adı ve logosu) gösterilir. */
    open val showWelcome: Boolean = true

    /** v32 her tuval türüne (çizim, not alma, çizgi roman) ilk girişte eğitim önerilir (eski testlerde hayır). */
    open val offerTutorials: Boolean = true

    /** v40 çocuk tuvaline ilk girişlerde maskotun çizim görevi önerisi (eski testlerde kapalı). */
    open val offerChallengeSuggestion: Boolean = true

    /** v33 internet bağlantısı izleyicisi (testlerde elle değiştirilen). */
    open fun createConnectivity(): com.karapp.drawguidance.data.sync.Connectivity = com.karapp.drawguidance.data.sync.AndroidConnectivity(this)

    /** v33 Google ile giriş (testlerde sahte sağlayıcı). */
    open fun createAuthProvider(): com.karapp.drawguidance.data.account.AuthProvider = com.karapp.drawguidance.data.account.FirebaseGoogleAuth(this)

    /** v39 Google Play Faturalandırma (testlerde mağazasız/sahte). */
    open fun createBillingGateway(): com.karapp.drawguidance.data.billing.BillingGateway = com.karapp.drawguidance.data.billing.PlayBillingGateway(this)

    /** v39 reklamlar (testlerde reklamsız). */
    open fun createAdsGateway(): com.karapp.drawguidance.data.ads.AdsGateway = com.karapp.drawguidance.data.ads.AdMobGateway()

    /** v39 testlerde elle verilen haklar (null: gerçek abonelik). */
    open fun createEntitlementsOverride(): kotlinx.coroutines.flow.StateFlow<com.karapp.drawguidance.data.billing.Entitlements?>? = null

    /** v40 ikinci açılıştan sonra Android 13+ bildirim izni için açıklama (eski testlerde kapalı). */
    open val askNotificationPermission: Boolean = true
    /** v43 yeni sürüm penceresi (testlerde öntanımlı olarak bildirim izniyle birlikte kapalı). */
    open val showAppUpdates: Boolean get() = askNotificationPermission

    /** v33 açılışta olay tablosu işlenir ve günlük katalog eşitlemesi planlanır (eski testlerde hayır). */
    open val autoSync: Boolean = true

    /** v23 kütüphaneler ilk açılışta doldurulsun mu (Robolectric birim testlerinde hayır). */
    protected open val seedLibraries: Boolean get() = android.os.Build.FINGERPRINT != "robolectric"

    /** v34 ilk açılışta sistemden algılanmak yerine bu dil (null: algıla). Robolectric testleri Türkçe başlar. */
    protected open val initialLanguage: String? get() = if (android.os.Build.FINGERPRINT == "robolectric") "tr" else null

    /** v34 uygulama bağlamına seçili dil uygulanır (ilk açılışta sistemin dili/ülkesi algılanır). */
    override fun attachBaseContext(base: android.content.Context) {
        initialLanguage?.let { com.karapp.drawguidance.i18n.LanguageManager.initialOverride = it }
        super.attachBaseContext(com.karapp.drawguidance.i18n.LanguageManager.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        com.karapp.drawguidance.diagnostics.Diagnostics.install(this)
        // v49 freeRASP çalışma zamanı koruması: açılışta başlat (hook/kurcalamada uygulamayı kapatır)
        com.karapp.drawguidance.security.TalsecGuard.start(this)
        // v42 ön plandayken beklenmedik kapanma (yerel çökme, donma, bellek) izlenir
        runCatching { com.karapp.drawguidance.diagnostics.SessionWatch.install(this) }
        // v18: sürüm ve bellek yetenekleri (sınırlar ve önbellekler buna göre)
        com.karapp.drawguidance.platform.DeviceCaps.init(this)
        // v18: tablet kalemi algılama ve kalem ayarları
        runCatching { com.karapp.drawguidance.platform.Stylus.init(this) }
        com.karapp.drawguidance.ui.drawing.FontRegistry.init(this)
        // v27 hazır 3B şekil küçük resimleri diskte saklanır (pencere her oturumda yeniden çizmesin)
        com.karapp.drawguidance.ui.screens.model3d.CatalogThumbs.dir = java.io.File(cacheDir, "catalog3d_thumbs")
        val koin = startKoin {
            androidContext(this@DrawGuidanceApp)
            modules(appModule(apiBaseUrl))
        }.koin
        // Ayar dosyaları ve yazı tipleri arka planda hazırlanır: ilk ekranlar ana iş parçacığında diske gitmez
        Thread({
            // v37 merkezi hata yönetimi: raporlar olay tablosuyla gönderilir; önceki çalışmada çökme olduysa sorulur
            // v42 önceki çalışma ön plandayken kapandıysa (istisnasız) günlüklerle rapor hazırlanır
            runCatching { com.karapp.drawguidance.diagnostics.SessionWatch.checkPrevious(this) }
            runCatching {
                com.karapp.drawguidance.diagnostics.AppErrors.sink = koin.get<com.karapp.drawguidance.data.reports.ErrorReporter>()
                com.karapp.drawguidance.diagnostics.AppErrors.checkPendingCrash(this)
            }
            // v40 geri bildirim işleyicisi; bildirim kanalları, günlük bildirim işi ve açılışta sürüm denetimi
            runCatching { koin.get<com.karapp.drawguidance.data.feedback.FeedbackRepository>() }
            runCatching { com.karapp.drawguidance.notify.AppNotifications.ensureChannels(this) }
            if (autoSync) runCatching {
                com.karapp.drawguidance.notify.EngagementScheduler.schedule(this)
                kotlinx.coroutines.runBlocking {
                    com.karapp.drawguidance.notify.EngagementChecks(
                        this@DrawGuidanceApp, koin.get(), koin.getOrNull(), postReminder = { false },
                    ).checkUpdate()
                }
            }
            // v37 önbellek sınırı aşıldıysa en eski önbellek dosyaları silinir
            runCatching { koin.get<com.karapp.drawguidance.data.settings.StorageManager>().trimCache() }
            // v33 sunucudan daha önce inen (yerleşikten yeni) katalog; olay tablosu ve günlük katalog eşitlemesi
            runCatching {
                val syncer = koin.get<com.karapp.drawguidance.data.catalog.CatalogSyncer>()
                syncer.installSaved()
                val account = koin.get<com.karapp.drawguidance.data.account.AccountRepository>()
                account.startup()
                // v39 abonelik (ilk girişte Free satırı, sunucuyla eşitleme) ve çizim görevi ilerlemesi
                runCatching { koin.get<com.karapp.drawguidance.data.billing.SubscriptionRepository>().start(network = autoSync) }
                runCatching { koin.get<com.karapp.drawguidance.data.challenge.ChallengeProgressRepository>().start(network = autoSync) }
                if (autoSync) {
                    val engine = koin.get<com.karapp.drawguidance.data.sync.SyncEngine>()
                    engine.start(this)
                    if (syncer.due()) kotlinx.coroutines.runBlocking { engine.enqueue(com.karapp.drawguidance.data.sync.SyncTypes.CATALOG_SYNC) }
                }
                // v43 gelen kutusu (paket, bildirim, duyuru, güncelleme; Firebase yok): önceki oturumdan kalan yarım
                // indirmeler silinir; uygulama ön plandayken uzun yoklama, arka planda WorkManager
                runCatching {
                    val packStore = koin.get<com.karapp.drawguidance.data.inbox.PackStore>()
                    packStore.discardAll()
                    // İnmiş çizgi roman paketleri hazır pencerelere (nesneler, efektler, mekânlar, karakterler) eklenir
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default).launch {
                        packStore.packs.collect { list ->
                            // Taban paketin yeni sürümü inmişse yalnız uygulamada olmayan öğeleri eklenir
                            val comicKnown = if (list.any { com.karapp.drawguidance.data.inbox.BasePacks.isBase(it.id) })
                                com.karapp.drawguidance.data.inbox.BasePacks.comicKnown(com.karapp.drawguidance.data.comicassets.ComicAssets.bundled) else emptySet()
                            com.karapp.drawguidance.data.comicassets.ComicAssets.extras = com.karapp.drawguidance.data.comicassets.ComicPacks.extras(list, comicKnown)
                        }
                    }
                }
                // v43 izin tablosu sistemin güncel durumuyla eşitlenir (kullanıcı ayarlardan değiştirmiş olabilir)
                runCatching { kotlinx.coroutines.runBlocking { koin.get<com.karapp.drawguidance.data.permissions.PermissionRepository>().refresh() } }
                // v44 çocuk kilidi: 12 yaşından küçük girişli kullanıcıda kendiliğinden açılır
                runCatching { koin.get<com.karapp.drawguidance.data.kids.KidsLockRepository>().start() }
                if (autoSync) runCatching {
                    val foreground = koin.get<com.karapp.drawguidance.data.inbox.InboxForeground>()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(foreground)
                    }
                }
            }.onFailure { com.karapp.drawguidance.diagnostics.AppLog.e("DrawGuidanceApp", "eşitleme başlatılamadı", it) }
            // v34 dil ayarı veritabanına yazılır (ilk açılışta algılanan dil)
            runCatching { kotlinx.coroutines.runBlocking { koin.get<com.karapp.drawguidance.data.settings.LanguageRepository>().ensureStored() } }
            runCatching {
                koin.get<com.karapp.drawguidance.data.settings.ServerConfig>()
                koin.get<com.karapp.drawguidance.data.settings.ColorPaletteStore>()
                koin.get<com.karapp.drawguidance.data.settings.ExportSettingsStore>()
                koin.get<com.karapp.drawguidance.data.settings.DeveloperSettingsStore>()
                koin.get<com.karapp.drawguidance.data.settings.EditorPrefsStore>().warmUp()
            }
            runCatching { com.karapp.drawguidance.ui.drawing.FontRegistry.preload() }
            // El yazısı şablonları bir kez hazırlanır (ilk öneride gecikme olmasın)
            runCatching { com.karapp.drawguidance.content.ink.Handwriting.warmUp() }
            // v23 arka plan desenleri/resimleri, 3B dokuları ve örnek 3B modeller veritabanına bir kez yazılır
            // (birim testlerinde arka planda ağır üretim yapılmaz; gerekince istenen öge anında üretilir)
            if (seedLibraries) {
                runCatching { koin.get<com.karapp.drawguidance.data.library.PaperRepository>().warmUp() }
                runCatching { koin.get<com.karapp.drawguidance.data.library.TextureRepository>().warmUp() }
                runCatching { koin.get<com.karapp.drawguidance.data.library.Model3DRepository>().warmUp() }
                // v36 yerleşik kütüphane şekilleri (hazır çizimler, nesneler, efektler…) veritabanına; sürüm değişince bir kez
                runCatching { kotlinx.coroutines.runBlocking { koin.get<com.karapp.drawguidance.data.catalog.CatalogVectorRepository>().seedBundled() } }
                // v37 yerleşik sinir ağları (şekil ve yazı düzeltme) veritabanına; eski not alma ağı silinir
                runCatching { kotlinx.coroutines.runBlocking { koin.get<com.karapp.drawguidance.data.catalog.CatalogNetRepository>().seedBundled() } }
                // v42 çizgi roman tema paketleri (paket ↔ karakter/nesne/efekt/mekân) katalogdan veritabanına
                runCatching {
                    val packs = koin.get<com.karapp.drawguidance.data.comicassets.ThemePackRepository>()
                    kotlinx.coroutines.runBlocking { packs.seedIfChanged() }
                    packs.start()
                }
            } else {
                // Kayıtlı desenler ve 3B Şekillerim yine de okunur (yazma yok)
                runCatching { koin.get<com.karapp.drawguidance.data.library.PaperRepository>() }
                runCatching { koin.get<com.karapp.drawguidance.data.library.TextureRepository>() }
            }
        }, "dg-warmup").apply { priority = Thread.MIN_PRIORITY; isDaemon = true }.start()
    }

    /** v18: sistem bellek istediğinde (arka planda ya da bellek azken) resim önbellekleri bırakılır. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // v42 bellek baskısı rapor izine yazılır (beklenmedik kapanmanın nedeni anlaşılsın)
        if (level >= 10) runCatching { com.karapp.drawguidance.diagnostics.AppErrors.breadcrumb("bellek baskısı: $level") }
        @Suppress("DEPRECATION")
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND || level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            com.karapp.drawguidance.platform.DeviceCaps.trimMemory()
        }
    }
}
