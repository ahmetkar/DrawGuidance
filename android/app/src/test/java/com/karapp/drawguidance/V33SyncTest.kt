package com.karapp.drawguidance

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.catalog.CatalogCodec
import com.karapp.drawguidance.content.catalog.CatalogInfo
import com.karapp.drawguidance.content.catalog.CatalogModeNet
import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.shapes.ShapeMode
import com.karapp.drawguidance.content.shapes.ShapeNet
import com.karapp.drawguidance.data.account.AccountRepository
import com.karapp.drawguidance.data.catalog.CatalogStore
import com.karapp.drawguidance.data.catalog.CatalogSyncer
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.local.LocalUserDao
import com.karapp.drawguidance.data.local.SyncEventEntity
import com.karapp.drawguidance.data.remote.v1.RemoteException
import com.karapp.drawguidance.data.repository.ShapeTemplateRepository
import com.karapp.drawguidance.data.settings.ServerConfig
import com.karapp.drawguidance.data.sync.FixedConnectivity
import com.karapp.drawguidance.data.sync.SyncEngine
import com.karapp.drawguidance.data.sync.SyncTypes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * v33 (5) olay tablosu: iş bağlantı yokken bekler, bağlantı gelince yapılır; aynı anahtarlı bekleyen iş tekleşir;
 * geçici hata üstel beklemeyle yeniden denenir, kalıcı hata FAILED olur; geçmiş saklanır. Ayrıca v11 → v12 göçü.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V33SyncEngineTest {
    private lateinit var db: AppDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun close() { db.close(); org.koin.core.context.stopKoin() }

    @Test
    fun pendingUntilOnlineRetriesAndFailures() = runBlocking {
        val net = FixedConnectivity(false)
        var now = 1_000_000L
        // Arka plan denemeleri çalışmaz (TestScope ilerletilmez): işler yalnız process() ile yapılır
        val engine = SyncEngine(db.syncEventDao(), net, TestScope(), clock = { now })
        val calls = CopyOnWriteArrayList<String>()
        var failNext: Throwable? = null
        engine.register("A") { e -> failNext?.let { failNext = null; throw it }; calls += "A:${e.payload}" }

        // Bağlantı yok: iş bekler, deneme sayısı artmaz
        engine.enqueue("A", "1", dedupeKey = null)
        assertEquals(0, engine.process())
        assertEquals(1, engine.pendingCount.first())
        assertEquals(0, db.syncEventDao().pending().single().attempts)

        // Bağlantı geldi: yapılır, geçmişte DONE
        net.set(true)
        assertEquals(1, engine.process())
        assertEquals(listOf("A:1"), calls)
        assertEquals(SyncEventEntity.STATUS_DONE, db.syncEventDao().all().single().status)

        // Aynı anahtarlı bekleyen iş tekleşir (son hali)
        net.set(false)
        repeat(3) { engine.enqueue("A", "u$it", dedupeKey = "user") }
        assertEquals(1, db.syncEventDao().pending().size)
        assertEquals("u2", db.syncEventDao().pending().single().payload)
        net.set(true)
        engine.process()
        assertEquals("A:u2", calls.last())

        // Geçici hata: 30 sn sonra yeniden
        failNext = IOException("bağlantı koptu")
        engine.enqueue("A", "r", dedupeKey = null)
        assertEquals(0, engine.process())
        val retry = db.syncEventDao().pending().single()
        assertEquals(1, retry.attempts)
        assertEquals(now + SyncEngine.backoff(1), retry.nextAttemptAt)
        assertEquals("bağlantı koptu", retry.lastError)
        assertEquals(0, engine.process())
        now += 31_000
        assertEquals(1, engine.process())
        assertEquals("A:r", calls.last())

        // Kalıcı hata (400) ve bilinmeyen iş türü: FAILED
        failNext = RemoteException(400, "validation", "geçersiz")
        engine.enqueue("A", "x", dedupeKey = null)
        engine.enqueue("YOK", dedupeKey = null)
        engine.process()
        val failed = db.syncEventDao().all().filter { it.status == SyncEventEntity.STATUS_FAILED }
        assertEquals(2, failed.size)
        assertEquals(0, engine.pendingCount.first())

        // 401 geçicidir (jeton yenilenip denenir)
        assertTrue(RemoteException(401, "invalid_token", "").isRetryable)
        assertFalse(RemoteException(403, "uid_mismatch", "").isRetryable)
        // Üstel bekleme 6 saatle sınırlı
        assertEquals(6 * 3_600_000L, SyncEngine.backoff(40))
    }

    @Test
    fun migration11to12CreatesTables() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "mig-test.db"
        ctx.deleteDatabase(name)
        // v12 veritabanı kurulur, yeni tablolar silinip sürüm 11'e çekilir; göçle yeniden açılır (Room şemayı denetler).
        // v36 göç zinciri v14'e kadar: kütüphane şekilleri tablosu ve kullanıcı onay sütunları da eklenir.
        Room.databaseBuilder(ctx, AppDatabase::class.java, name).allowMainThreadQueries().build().apply {
            openHelper.writableDatabase.apply {
                execSQL("DROP TABLE sync_events"); execSQL("DROP TABLE local_user"); execSQL("DROP TABLE catalog_vectors"); version = 11
            }
            close()
        }
        val migrated = Room.databaseBuilder(ctx, AppDatabase::class.java, name).allowMainThreadQueries()
            .addMigrations(*AppDatabase.ALL_MIGRATIONS).build()
        runBlocking {
            migrated.syncEventDao().enqueue(SyncEventEntity(type = "T", createdAt = 1, updatedAt = 1))
            assertEquals(1, migrated.syncEventDao().pending().size)
            assertNull(migrated.localUserDao().get())
            assertEquals(0, migrated.catalogVectorDao().count("bundled"))
        }
        assertEquals(23, migrated.openHelper.readableDatabase.version)
        migrated.close()
        ctx.deleteDatabase(name)
    }
}

/**
 * v33 (5.1) katalog eşitleme: sunucunun sürümü yeniyse şablonlar, vektörler ve temel ağlar inip uygulanır
 * (sunucudakiler öncelikli); ETag eşleşince gövde inmez; eski/eşit sürüm uygulanmaz.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V33CatalogSyncTest {
    private val server = MockWebServer()
    private inline fun <reified T : Any> koin(): T = GlobalContext.get().get()

    @After fun stop() {
        org.koin.core.context.stopKoin()
        server.shutdown()
        ShapeLibrary.install(emptyList())
        ShapeNet.installRemote(ShapeMode.ART, ShapeNet.MODE_NET_VERSION, ByteArray(0))
    }

    @Test
    fun newerServerCatalogWins() = runBlocking {
        val version = CatalogInfo.VERSION + 1
        val bundled = CatalogCodec.bundledTemplates()
        val newTemplate = bundled.first().copy(key = "srv.test.ucgen", templateKey = "srv.test.ucgen", name = "Sunucu üçgeni")
        val templates = (bundled.drop(1) + bundled.first().copy(name = "Sunucudaki ad") + newTemplate)
        val vectors = CatalogCodec.bundledVectors()
        val target = vectors.first()
        val changedVector = target.copy(title = "Sunucudaki çizim")
        val addedVector = target.copy(id = "srv_yeni", title = "Yeni çizim", categoryId = "srv_kat", categoryTitle = "Sunucu")
        val netBytes = ShapeNet::class.java.getResourceAsStream("/shapenet/mode_art.bin")!!.readBytes()
        fun list(items: List<kotlinx.serialization.json.JsonObject>) = buildJsonObject { put("version", version); put("items", JsonArray(items)) }.toString()
        val bodies = mapOf(
            "/api/v1/catalog/templates" to list(templates.map { CatalogCodec.template(it) }),
            "/api/v1/catalog/vectors" to list(listOf(changedVector, addedVector).map { CatalogCodec.vector(it) }),
            "/api/v1/catalog/mode-nets" to list(listOf(CatalogCodec.modeNet(CatalogModeNet("ART", ShapeNet.modeNetVersion(ShapeMode.ART) + 1, CatalogInfo.MODE_NET_FORMAT, netBytes)))),
        )
        val etag = "\"cat-$version\""
        val requests = CopyOnWriteArrayList<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requests += path
                if (path == "/api/v1/catalog/manifest") {
                    if (request.getHeader("If-None-Match") == etag) return MockResponse().setResponseCode(304).setHeader("ETag", etag)
                    return MockResponse().setHeader("ETag", etag).setBody("""{"version":$version,"updatedAt":"2026-10-18T10:00:00Z","counts":{"templates":${templates.size}}}""")
                }
                return bodies[path]?.let { MockResponse().setHeader("ETag", etag).setBody(it) } ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        koin<ServerConfig>().update(server.url("/").toString())

        val syncer = koin<CatalogSyncer>()
        val store = koin<CatalogStore>()
        val repo = koin<ShapeTemplateRepository>()
        repo.load()
        assertTrue(syncer.sync())
        assertEquals(version, store.appliedVersion)
        assertTrue(store.remoteActive)
        // Şablonlar: yeni anahtar eklendi, aynı anahtarın adı sunucudaki
        val index = repo.indexFlow.value!!
        assertNotNull(index.meta("srv.test.ucgen"))
        assertEquals("Sunucudaki ad", index.meta(bundled.first().key)!!.name)
        // Vektörler: aynı kimlik değişti, yeni kimlik eklendi
        assertEquals("Sunucudaki çizim", ShapeLibrary.find(target.id)!!.title)
        assertNotNull(ShapeLibrary.find("srv_yeni"))
        // Temel ağ: sunucudaki sürüm
        assertEquals(ShapeNet.modeNetVersion(ShapeMode.ART) + 1, ShapeNet.baseFor(ShapeMode.ART).second)

        // İkinci denetim: ETag eşleşir, gövde inmez
        val before = requests.size
        assertFalse(syncer.sync())
        assertEquals(listOf("/api/v1/catalog/manifest"), requests.drop(before))
        // Açılışta indirilmiş katalog yeniden yüklenir
        ShapeLibrary.install(emptyList())
        syncer.installSaved()
        assertEquals("Sunucudaki çizim", ShapeLibrary.find(target.id)!!.title)
    }

    @Test
    fun sameVersionIsNotDownloaded() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path == "/api/v1/catalog/manifest") MockResponse().setHeader("ETag", "\"cat-${CatalogInfo.VERSION}\"").setBody("""{"version":${CatalogInfo.VERSION}}""")
                else MockResponse().setResponseCode(500)
        }
        server.start()
        koin<ServerConfig>().update(server.url("/").toString())
        assertFalse(koin<CatalogSyncer>().sync())
        assertEquals(1, server.requestCount)
        assertFalse(koin<CatalogStore>().remoteActive)
        assertTrue(koin<CatalogStore>().lastCheck > 0)
    }
}

/**
 * v33 (6, 7) Google ile giriş (sahte sağlayıcı) ve hesap: Ayarlar'da yalnız internet varken hesap kartı; ilk girişte
 * doğum tarihi; kullanıcı önce cihaza, sonra sunucuya (Bearer jetonla PUT) yazılır ve sunucunun kimliği alınır; ana
 * sayfada ad-soyad ve fotoğraf; bağlantı yokken giriş yapılmamış gibi; en altta çıkış; bağlantısızken bekleyen iş
 * bağlantı gelince yapılır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V33AccountTest : V33EditorBase() {
    private val server = MockWebServer()
    private inline fun <reified T : Any> koin(): T = GlobalContext.get().get()
    private val puts = CopyOnWriteArrayList<RecordedRequest>()

    @Before fun startServer() {
        TestAccount.reset()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path != "/api/v1/users/me") return MockResponse().setResponseCode(404)
                return when (request.method) {
                    "GET" -> MockResponse().setResponseCode(404).setBody("""{"error":"user_not_found","message":"yok"}""")
                    "PUT" -> {
                        puts += request
                        // Gövde kopyadan okunur: test sonradan gönderilen alanları denetler
                        val body = Json.parseToJsonElement(request.body.clone().readUtf8()).jsonObject
                        val echo = buildJsonObject {
                            body.forEach { (k, v) -> if (k != "id" && k != "clientUpdatedAt") put(k, v) }
                            put("id", "sunucu-kimligi-1")
                            put("version", puts.size)
                        }
                        MockResponse().setBody(echo.toString())
                    }
                    else -> MockResponse().setResponseCode(405)
                }
            }
        }
        server.start()
    }

    @After fun stopServer() { server.shutdown(); TestAccount.reset() }

    private fun openSettings() {
        compose.onNode(hasContentDescription("Ayarlar").or(hasText("Ayarlar")) and hasAnyAncestor(hasTestTag("main_bottom_bar"))).performClick()
        // v36 ayarlar sekmeli (sunucu kartı Bağlantı sekmesinde); hesap başlığı sekmelerin üstünde
        waitFor { exists("settings_tabs") }
    }

    /** v36 hesap açılışında iki onay kutusu (Kullanım Şartları, KVKK Aydınlatma Metni). */
    private fun acceptConsent() {
        waitFor { exists("consent_box") }
        compose.onNodeWithTag("consent_terms").performScrollTo().performClick()
        compose.onNodeWithTag("consent_kvkk").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun user() = runBlocking { koin<LocalUserDao>().get() }

    @Test
    fun signInBirthDateProfileOfflineAndSignOut() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        koin<ServerConfig>().update(server.url("/").toString())
        openSettings()
        // İnternet var: hesap kartı ve giriş düğmesi
        waitFor { exists("account_sign_in") }
        compose.onNodeWithTag("account_sign_in").performClick()
        // İlk giriş: doğum tarihi sorulur
        waitFor { exists("birth_date_dialog") }
        compose.onNodeWithTag("birth_day").performTextInput("17")
        compose.onNodeWithTag("birth_month").performTextInput("5")
        compose.onNodeWithTag("birth_year").performTextInput("1990")
        // v36 hesap açılırken Kullanım Şartları ve KVKK onayı: ikisi işaretlenmeden düğme kapalı
        assertFalse(compose.onAllNodes(hasTestTag("birth_date_ok") and androidx.compose.ui.test.isEnabled()).fetchSemanticsNodes().isNotEmpty())
        acceptConsent()
        compose.onNodeWithTag("birth_date_ok").performClick()
        // Önce cihaza
        waitFor { user()?.birthDate == "1990-05-17" }
        val local = user()!!
        assertEquals(com.karapp.drawguidance.ui.screens.account.LegalTexts.VERSION, local.termsVersion)
        assertNotNull(local.termsAcceptedAt)
        assertEquals("uid-ahmet", local.firebaseUid)
        assertEquals("Ahmet", local.givenName)
        assertEquals("Kar", local.familyName)
        assertTrue(local.emailVerified)
        assertEquals(1_700_000_000_000L, local.accountCreatedAt)
        // Sonra sunucuya (olay tablosundaki iş): Bearer jeton, bütün alanlar; sunucunun kimliği alınır
        waitFor(15_000) { puts.isNotEmpty() && user()?.id == "sunucu-kimligi-1" }
        val put = puts.first()
        assertEquals("Bearer fake-token", put.getHeader("Authorization"))
        assertTrue(put.body.clone().readUtf8().contains("\"termsVersion\":\"${com.karapp.drawguidance.ui.screens.account.LegalTexts.VERSION}\""))
        assertNotNull(user()!!.syncedAt)
        // Ayarlar: profil ve en altta çıkış
        waitFor { exists("account_name") }
        assertTrue(compose.onAllNodes(hasTestTag("account_name") and hasText("Ahmet Kar")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(exists("account_sign_out"))
        shot("v33-settings-account")
        // Ana sayfa üst çubuğunda ad-soyad
        compose.onNodeWithTag("tab_home").performClick()
        waitFor { exists("home_profile") }
        assertTrue(compose.onAllNodes(hasTestTag("home_profile") and hasContentDescription("Ahmet Kar", substring = true)).fetchSemanticsNodes().isNotEmpty())
        shot("v33-home-profile")

        // İnternet yok: giriş yapılmamış gibi (profil ve hesap kartı görünmez)
        compose.runOnIdle { TestAccount.connectivity.set(false) }
        waitFor { !exists("home_profile") }
        openSettings()
        waitFor { !exists("account_card") && !exists("account_sign_out") }
        // Bağlantısızken yapılan değişiklik bekler, bağlantı gelince sunucuya gider
        val engine = koin<SyncEngine>()
        engine.start(null)
        runBlocking { koin<AccountRepository>().updateProfile("Ahmet", "Kar", java.time.LocalDate.of(1990, 5, 18)) }
        waitFor { runBlocking { engine.pendingCount.first() } == 1 }
        assertEquals(1, puts.size)
        compose.runOnIdle { TestAccount.connectivity.set(true) }
        waitFor(15_000) { puts.size == 2 && runBlocking { engine.pendingCount.first() } == 0 }
        assertTrue(puts[1].body.clone().readUtf8().contains("\"birthDate\":\"1990-05-18\"") || user()?.birthDate == "1990-05-18")

        // Çıkış (en altta)
        waitFor { exists("account_sign_out") }
        compose.onNodeWithTag("account_sign_out").performScrollTo().performClick()
        waitFor { exists("account_sign_out_confirm") }
        compose.onNodeWithTag("account_sign_out_confirm").performClick()
        waitFor { exists("account_sign_in") && user()?.signedIn == false }
        // Aynı hesapla yeniden giriş: doğum tarihi yeniden sorulmaz
        compose.onNodeWithTag("account_sign_in").performClick()
        waitFor { user()?.signedIn == true && exists("account_name") }
        assertFalse(exists("birth_date_dialog"))
        assertEquals("1990-05-18", user()!!.birthDate)
    }

    @Test
    fun accountSettingsEditsAndCancelledBirthDate() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        koin<ServerConfig>().update(server.url("/").toString())
        openSettings()
        waitFor { exists("account_sign_in") }
        // Doğum tarihi verilmezse giriş tamamlanmaz
        compose.onNodeWithTag("account_sign_in").performClick()
        waitFor { exists("birth_date_dialog") }
        compose.onNodeWithTag("birth_year").performTextInput("2999")
        assertFalse(compose.onAllNodes(hasTestTag("birth_date_ok") and androidx.compose.ui.test.isEnabled()).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("birth_date_cancel").performClick()
        waitFor { !exists("birth_date_dialog") }
        assertNull(user())
        assertNull(TestAccount.auth.signedIn)
        // Giriş ve hesap ayarları
        compose.onNodeWithTag("account_sign_in").performClick()
        waitFor { exists("birth_date_dialog") }
        compose.onNodeWithTag("birth_day").performTextInput("1")
        compose.onNodeWithTag("birth_month").performTextInput("2")
        compose.onNodeWithTag("birth_year").performTextInput("2001")
        acceptConsent()
        compose.onNodeWithTag("birth_date_ok").performClick()
        waitFor { exists("account_settings_open") }
        compose.onNodeWithTag("account_settings_open").performClick()
        waitFor { exists("account_screen") }
        compose.onNodeWithTag("account_given").performTextReplacement("Ahmet Can")
        compose.onNodeWithTag("account_save").performScrollTo().performClick()
        waitFor { user()?.givenName == "Ahmet Can" }
        assertEquals("2001-02-01", user()!!.birthDate)
        // v34 hesabı değiştir yok
        assertFalse(exists("account_change"))
        // Çıkıp başka hesapla giriş: cihazda yalnız yeni kullanıcı kalır
        runBlocking { koin<AccountRepository>().signOut() }
        TestAccount.auth.account = FakeAuth.DEFAULT.copy(firebaseUid = "uid-baska", displayName = "Ayşe Yılmaz", givenName = "Ayşe", familyName = "Yılmaz", email = "ayse@example.com")
        waitFor { exists("account_screen_sign_in") }
        // Kaydedildi bildirimi düğmenin üstünde olabilir: tıklama anlam eylemiyle
        compose.onNodeWithTag("account_screen_sign_in").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitFor { exists("birth_date_dialog") }
        compose.onNodeWithTag("birth_day").performTextInput("3")
        compose.onNodeWithTag("birth_month").performTextInput("4")
        compose.onNodeWithTag("birth_year").performTextInput("1995")
        acceptConsent()
        compose.onNodeWithTag("birth_date_ok").performClick()
        waitFor { user()?.firebaseUid == "uid-baska" }
        assertEquals(1, runBlocking { koin<AppDatabase>().openHelper.readableDatabase.query("SELECT COUNT(*) FROM local_user").use { it.moveToFirst(); it.getInt(0) } })
    }
}

/** v33 gerçek sunucu (127.0.0.1:4747, Docker'daki Spring Boot + MongoDB): sağlık ve katalog (sürüm uygulamayla aynı). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V33BackendTest {
    private inline fun <reified T : Any> koin(): T = GlobalContext.get().get()

    @After fun stop() = org.koin.core.context.stopKoin()

    @Test
    fun healthAndCatalogFromRealServer() = runBlocking {
        val health = koin<com.karapp.drawguidance.data.remote.ApiService>().health()
        assertEquals("ok", health.status)
        assertEquals("44.0.0", health.version)
        val manifest = koin<com.karapp.drawguidance.data.remote.v1.RemoteStore>().catalogManifest()!!
        assertEquals(CatalogInfo.VERSION, manifest.first.version)
        assertTrue((manifest.first.counts["templates"] ?: 0) > 400)
        assertEquals("\"cat-${CatalogInfo.VERSION}\"", manifest.second)
        // Sunucunun kataloğu uygulamayla gelenle aynı sürüm: indirilmez; ikinci denetimde 304
        val syncer = koin<CatalogSyncer>()
        assertFalse(syncer.sync())
        assertFalse(syncer.sync())
        assertNotNull(koin<CatalogStore>().etag)
        // Sunucunun şablonları uygulamanınkilerle aynı (tohum dosyası uygulamanın kataloğundan)
        val (v, items) = CatalogCodec.items(koin<com.karapp.drawguidance.data.remote.v1.RemoteStore>().catalogTemplates())
        assertEquals(CatalogInfo.VERSION, v)
        assertEquals(CatalogCodec.bundledTemplates().map { it.key }.toSet(), items.map { it["key"]!!.jsonPrimitive.content }.toSet())
        // Kullanıcı ucu jetonsuz kabul edilmez
        val e = runCatching { koin<com.karapp.drawguidance.data.remote.v1.RemoteStore>().me("gecersiz") }.exceptionOrNull()
        assertTrue("$e", e is RemoteException && (e.status == 401 || e.status == 503))
    }
}
