package com.karapp.drawguidance.di

import android.util.Log
import androidx.room.Room
import com.karapp.drawguidance.BuildConfig
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.images.ImageImporter
import com.karapp.drawguidance.data.images.ImagesRepository
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.remote.ApiService
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.data.repository.CanvasSaveQueue
import com.karapp.drawguidance.data.settings.ColorPaletteStore
import com.karapp.drawguidance.data.settings.ExportSettingsStore
import com.karapp.drawguidance.data.settings.ServerConfig
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvaslist.CanvasListViewModel
import com.karapp.drawguidance.ui.screens.images.ImagesViewModel
import com.karapp.drawguidance.ui.screens.settings.SettingsViewModel
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.module.dsl.viewModel
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun appModule(apiBaseUrl: String) = module {

    single {
        Room.databaseBuilder(get(), AppDatabase::class.java, "drawguidance.db")
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
    }

    single { get<AppDatabase>().canvasDao() }
    single { get<AppDatabase>().savedImageDao() }
    single { get<AppDatabase>().canvasAssetDao() }
    single { get<AppDatabase>().folderDao() }
    single { get<AppDatabase>().shapeTemplateDao() }
    // v33 sunucunun (daha yeni) kataloğu uygulandıysa açılışta yerleşik katalog yeniden yazılmaz
    single { com.karapp.drawguidance.data.repository.ShapeTemplateRepository(get(), remoteCatalogActive = { get<com.karapp.drawguidance.data.catalog.CatalogStore>().remoteActive }) }
    // v23 arka plan kütüphanesi, hazır 3B dokuları ve 3B Şekillerim
    single { get<AppDatabase>().paperAssetDao() }
    single { get<AppDatabase>().model3DDao() }
    single { get<AppDatabase>().texture3DDao() }
    single { com.karapp.drawguidance.data.library.PaperRepository(get()) }
    single { com.karapp.drawguidance.data.library.TextureRepository(get()) }
    single { com.karapp.drawguidance.data.library.Model3DRepository(androidContext(), get(), get()) }
    single { com.karapp.drawguidance.data.library.Model3DResults() }
    single { com.karapp.drawguidance.data.local.FolderRepository(get(), get(), get()) }
    single { com.karapp.drawguidance.data.settings.EditorPrefsStore(androidContext()) }
    // v24 3B düzenleyicinin çizim tercihleri (ekran kartı, kalite)
    // Sınama anahtarı: APK'nın kurulma zamanı (her yeni kurulumda ekran kartı yeniden sınanır)
    single {
        val ctx = androidContext()
        val installed = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime }.getOrDefault(BuildConfig.VERSION_CODE.toLong())
        com.karapp.drawguidance.data.settings.Model3DPrefs(ctx, installed)
    }
    single { CanvasAssetStore(get()) }
    single { ImageImporter(androidContext()) }
    single { ExportSettingsStore(androidContext()) }
    single { ImageExporter(androidContext(), settings = get()) }
    single { ImagesRepository(get(), get(), get(), get()) }
    single { ColorPaletteStore(androidContext()) }

    // v40 yayın derlemesinde sunucu adresi sabit (kayıtlı adres yok sayılır)
    single { ServerConfig(androidContext(), apiBaseUrl, allowOverride = BuildConfig.DEBUG) }

    single {
        val logging = HttpLoggingInterceptor { message -> Log.d("HTTP", message) }
            // v30 yalnız başlıklar (gövde günlüğe yazılırsa dosya yükleme/indirme belleğe alınır)
            .apply { level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE }
            // v40 hata ayıklama günlüğünde de Firebase jetonu ve çerezler yazılmaz
            .apply { redactHeader("Authorization"); redactHeader("Cookie"); redactHeader("Set-Cookie") }
        val serverConfig = get<ServerConfig>()
        OkHttpClient.Builder()
            // Her istek, o anki sunucu adresine yönlendirilir (Ayarlar'dan değiştirilebilir).
            .addInterceptor { chain ->
                val target = serverConfig.currentHttpUrl
                // v44 yayın derlemesinde yalnız HTTPS (adres değiştirilemez; ağ güvenliği ayarı da düz HTTP'yi kapatır)
                if (!BuildConfig.DEBUG && target.scheme != "https") throw java.io.IOException("Yalnız güvenli bağlantı")
                val request = chain.request()
                val url = request.url.newBuilder()
                    .scheme(target.scheme)
                    .host(target.host)
                    .port(target.port)
                    .build()
                chain.proceed(request.newBuilder().url(url).build())
            }
            // v33 istemci sürümü (kimlik Firebase jetonuyla, yalnız kullanıcı uçlarında) ve yeniden deneme
            .addInterceptor(com.karapp.drawguidance.data.remote.v1.ClientInterceptor())
            .addInterceptor(com.karapp.drawguidance.data.remote.v1.RetryInterceptor())
            .addInterceptor(logging)
            // v37 günlük indirme/yükleme sınırları için ağ trafiği sayılır (önbellekten gelenler sayılmaz)
            .addNetworkInterceptor(com.karapp.drawguidance.data.sync.TrafficInterceptor(get<com.karapp.drawguidance.data.sync.SyncPolicy>()::count))
            // v30 bağlantı havuzu ve ETag'li yanıtlar için disk önbelleği
            .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
            .cache(okhttp3.Cache(java.io.File(androidContext().cacheDir, "http"), 20L * 1024 * 1024))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    single {
        Retrofit.Builder()
            .baseUrl(apiBaseUrl)
            .client(get())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
    // v30 sunucu CRUD kütüphanesi (Spring Boot + MongoDB)
    single {
        Retrofit.Builder()
            .baseUrl(apiBaseUrl)
            .client(get())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(com.karapp.drawguidance.data.remote.v1.DrawGuidanceApi::class.java)
    }
    single { com.karapp.drawguidance.data.remote.v1.RemoteStore(get(), json) }
    // v33 çevrimdışı çalışma: bağlantı izleme, olay tablosu ve işleyicisi, günlük katalog eşitleme, hesap
    single<com.karapp.drawguidance.data.sync.Connectivity> {
        (androidContext() as? com.karapp.drawguidance.DrawGuidanceApp)?.createConnectivity()
            ?: com.karapp.drawguidance.data.sync.AndroidConnectivity(androidContext())
    }
    single { get<AppDatabase>().syncEventDao() }
    single { get<AppDatabase>().localUserDao() }
    // v34 dil ayarı (veritabanı + açılış için ayar dosyası aynası)
    single { get<AppDatabase>().languageSettingsDao() }
    single { com.karapp.drawguidance.data.settings.LanguageRepository(androidContext(), get()) }
    // v37 eşitleme denetimi: yalnız Wi-Fi, durdur/başlat, günlük indirme/yükleme sınırları
    single { com.karapp.drawguidance.data.sync.SyncPolicy(androidContext(), get()) }
    single {
        val policy = get<com.karapp.drawguidance.data.sync.SyncPolicy>()
        com.karapp.drawguidance.data.sync.SyncEngine(get(), get()).apply {
            scheduler = { ctx -> com.karapp.drawguidance.data.sync.SyncWork.schedule(ctx) }
            whenOnline = { com.karapp.drawguidance.data.sync.SyncWork.kickWhenOnline(androidContext()) }
            gate = { policy.allowed }
            val ctx = androidContext()
            policy.onChange = { kick(); com.karapp.drawguidance.data.sync.SyncWork.reschedule(ctx) }
        }
    }
    // v37 depolama: bölüm bölüm kullanılan yer, önbellek sınırı, temizleme ve sıkıştırma
    single {
        val koin = getKoin()
        com.karapp.drawguidance.data.settings.StorageManager(
            androidContext(),
            compactDatabase = {
                val db = koin.get<AppDatabase>().openHelper.writableDatabase
                db.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
                db.execSQL("VACUUM")
            },
            evictHttpCache = { koin.get<OkHttpClient>().cache?.evictAll() },
        )
    }
    single { com.karapp.drawguidance.data.catalog.CatalogStore(androidContext()) }
    // v37 hata/çökme raporları (olay tablosuyla, bağlantı varken gönderilir)
    single { com.karapp.drawguidance.data.reports.ErrorReporter(androidContext(), get(), get(), getOrNull()) }
    // v36 kütüphane şekillerinin veritabanı kaydı (yerleşik + sunucudan gelen)
    single { get<AppDatabase>().catalogVectorDao() }
    single { com.karapp.drawguidance.data.catalog.CatalogVectorRepository(get()) }
    // v37 sinir ağlarının veritabanı kaydı (yerleşik + sunucudan gelen)
    single { get<AppDatabase>().catalogNetDao() }
    single { com.karapp.drawguidance.data.catalog.CatalogNetRepository(get()) }
    // v40 içerik eşitleme izni (sunucunun 10 dakikalık dilimleri; izin yoksa WorkManager ile dilime ertelenir)
    single {
        val ctx = androidContext()
        com.karapp.drawguidance.data.sync.SyncPermits(ctx, get(), getOrNull(), schedule = { d -> com.karapp.drawguidance.data.sync.SyncWork.scheduleCatalogAt(ctx, d) })
    }
    single {
        val permits = getKoin()
        com.karapp.drawguidance.data.catalog.CatalogSyncer(
            get(), get(), get(), get(), get(), get(),
            permit = { permits.get<com.karapp.drawguidance.data.sync.SyncPermits>().check() is com.karapp.drawguidance.data.sync.PermitDecision.Allowed },
        ).also { syncer ->
            get<com.karapp.drawguidance.data.sync.SyncEngine>().register(com.karapp.drawguidance.data.sync.SyncTypes.CATALOG_SYNC) { syncer.sync() }
        }
    }
    single<com.karapp.drawguidance.data.account.AuthProvider> {
        (androidContext() as? com.karapp.drawguidance.DrawGuidanceApp)?.createAuthProvider()
            ?: com.karapp.drawguidance.data.account.FirebaseGoogleAuth(androidContext())
    }
    single {
        val koin = getKoin()
        com.karapp.drawguidance.data.account.AccountRepository(get(), get(), get(), get(), get()).apply {
            // v43 çıkışta cihazın gelen kutusu hesaptan ayrılır
            beforeSignOut = { token -> koin.getOrNull<com.karapp.drawguidance.data.inbox.InboxRepository>()?.onSignedOut(token) }
        }
    }
    // v40 mod başına geri bildirim (olay tablosuyla; giriş ister) ve yerel bildirimler (güncelleme, hatırlatma)
    single { com.karapp.drawguidance.data.feedback.FeedbackRepository(get(), get(), getOrNull()) }
    single { com.karapp.drawguidance.notify.NotificationPrefs(androidContext()) }
    viewModel { com.karapp.drawguidance.ui.screens.account.AccountViewModel(get(), get(), get(), get()) }
    // v43 Android izinleri (ilk açılışta istenir) ve gelen kutusu: paketler, bildirimler, duyurular, güncellemeler
    single { get<AppDatabase>().appPermissionDao() }
    single { com.karapp.drawguidance.data.permissions.PermissionRepository(androidContext(), get()) }
    // v44 çocuk kilidi
    single { get<AppDatabase>().kidsLockDao() }
    single { com.karapp.drawguidance.data.kids.KidsLockRepository(get(), get()) }
    single { com.karapp.drawguidance.data.inbox.PackStore(java.io.File(androidContext().filesDir, "packs")) }
    single {
        val ctx = androidContext()
        com.karapp.drawguidance.data.inbox.InboxRepository(
            dao = get(), remote = getOrNull(), auth = getOrNull(), policy = get(), connectivity = get(), packs = get(),
            downloader = com.karapp.drawguidance.data.inbox.OkHttpDownloader(
                com.karapp.drawguidance.data.inbox.OkHttpDownloader.client(
                    com.karapp.drawguidance.data.sync.TrafficInterceptor(get<com.karapp.drawguidance.data.sync.SyncPolicy>()::count),
                ),
            ),
            notifier = com.karapp.drawguidance.data.inbox.AndroidInboxNotifier(ctx),
            device = com.karapp.drawguidance.data.inbox.DeviceIdentity.info(ctx),
            prefs = ctx.getSharedPreferences("inbox", android.content.Context.MODE_PRIVATE),
            announcementsDir = java.io.File(ctx.filesDir, "announcements"),
        )
    }
    single { com.karapp.drawguidance.data.inbox.InboxForeground(get()) }

    single { CanvasRepository(get(), get(), get<AppDatabase>()) }
    single { CanvasSaveQueue(get()) }
    // v29 moda özel şekil tahmini (her modun kendi sinir ağı; cihazda eğitilir, uygulama klasöründe saklanır)
    single { com.karapp.drawguidance.content.shapes.ShapeModeModels(java.io.File(androidContext().filesDir, "shape_models")) }
    // v29 modlar arası aktarma (tuval → not/çizgi roman, 3B → tuval/çizgi roman)
    single { com.karapp.drawguidance.data.transfer.CanvasTransfer(get(), get()) }
    single { com.karapp.drawguidance.data.settings.DeveloperSettingsStore(androidContext()) }
    // v30 Ana sayfa: son açılan / bırakılan tuvaller
    single { com.karapp.drawguidance.data.settings.RecentCanvasStore(androidContext()) }
    // v32 eğitim modu (birim testlerinde ilk girişte öneri açılmaz)
    single { com.karapp.drawguidance.data.settings.TutorialPrefs(androidContext(), (androidContext() as? com.karapp.drawguidance.DrawGuidanceApp)?.offerTutorials ?: true) }
    // v37 çocuk modu: maskot ve Ana sayfa önerisi
    single { com.karapp.drawguidance.data.settings.KidsPrefs(androidContext()) }
    // v39 abonelik (Free/Premium), haklar, reklamlar ve çizim görevi ilerlemesi
    single { get<AppDatabase>().billingDao() }
    // v42 çizgi roman tema paketleri
    single { get<AppDatabase>().themePackDao() }
    single { com.karapp.drawguidance.data.comicassets.ThemePackRepository(get(), prefs = androidContext().getSharedPreferences("comic_theme_packs", android.content.Context.MODE_PRIVATE)) }
    single { get<AppDatabase>().challengeProgressDao() }
    // v40 çocuk tuvaline girişlerin sayacı (maskotun görev önerisi)
    single { get<AppDatabase>().kidsEntryDao() }
    // v40 tuvalde geçirilen süre (Ana sayfa ve Tuvallerim özeti)
    single { get<AppDatabase>().canvasTimeDao() }
    single { com.karapp.drawguidance.data.repository.CanvasTimeRepository(get()) }
    single {
        com.karapp.drawguidance.data.challenge.KidsEntryRepository(
            get(), get(), suggestionsEnabled = (androidContext() as? com.karapp.drawguidance.DrawGuidanceApp)?.offerChallengeSuggestion ?: true,
        )
    }
    single<com.karapp.drawguidance.data.billing.BillingGateway> {
        (androidContext() as? com.karapp.drawguidance.DrawGuidanceApp)?.createBillingGateway()
            ?: com.karapp.drawguidance.data.billing.PlayBillingGateway(androidContext())
    }
    single<com.karapp.drawguidance.data.ads.AdsGateway> {
        (androidContext() as? com.karapp.drawguidance.DrawGuidanceApp)?.createAdsGateway() ?: com.karapp.drawguidance.data.ads.AdMobGateway()
    }
    single {
        com.karapp.drawguidance.data.billing.SubscriptionRepository(
            get(), get(), get(), get(), get(), get(), androidContext().packageName,
            override = (androidContext() as? com.karapp.drawguidance.DrawGuidanceApp)?.createEntitlementsOverride(),
        )
    }
    single {
        val users = get<com.karapp.drawguidance.data.local.LocalUserDao>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        com.karapp.drawguidance.data.ads.AdsCoordinator(
            androidContext(), get(), get<com.karapp.drawguidance.data.billing.SubscriptionRepository>().entitlements,
            users.observe().stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null),
            BuildConfig.ADMOB_INTERSTITIAL_ID,
        )
    }
    single {
        com.karapp.drawguidance.data.challenge.ChallengeProgressRepository(
            get(), get(), get(), get(), get(), get(),
            levelIds = { com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeLevels.all.map { it.id } },
            stars = { com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeScore.stars(it) },
        )
    }
    viewModel { com.karapp.drawguidance.ui.screens.subscription.SubscriptionViewModel(get()) }
    viewModel { com.karapp.drawguidance.ui.screens.home.HomeViewModel(get(), get(), get(), canvasTime = get()) }

    viewModel { CanvasListViewModel(get(), get(), get(), get(), get(), editorPrefs = get(), canvasTime = get()) }
    viewModel { SettingsViewModel(get(), get(), get(), tutorials = get()) }
    viewModel { com.karapp.drawguidance.ui.screens.logs.LogsViewModel(androidContext()) }
    viewModel { (canvasLocalId: String) ->
        CanvasEditViewModel(get(), get(), get(), get(), get(), get(), get(), get(), canvasLocalId, get(), get(), transfer = get(), shapeModels = get(), recent = get(), tutorialPrefs = get())
    }
    // Boş kimlik: yok (yeni model / tuvalsiz açılış)
    viewModel { (modelId: String, canvasId: String, strokeId: String, name: String) ->
        com.karapp.drawguidance.ui.screens.model3d.Model3DEditorViewModel(
            get(), get(), get(), modelId.ifEmpty { null }, canvasId.ifEmpty { null }, strokeId.ifEmpty { null }, get(),
            // v25 resim olarak dışa aktarma Resimlerim'e yazar
            images = get(),
            // v29 tuvale / çizgi romana aktarma
            transfer = get(),
            // v30 tuvalin yapay zekâ ayarı
            canvasAi = { id -> get<com.karapp.drawguidance.data.repository.CanvasRepository>().isAiEnabled(id) ?: true },
            // v33 ana sayfadaki 3B tuvalde verilen ad
            initialName = name.ifEmpty { null },
        )
    }
    viewModel { com.karapp.drawguidance.ui.screens.model3d.Model3DListViewModel(get()) }
    viewModel { ImagesViewModel(get(), get()) }
}
