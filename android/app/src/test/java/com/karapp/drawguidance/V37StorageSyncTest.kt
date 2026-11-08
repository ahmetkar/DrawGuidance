package com.karapp.drawguidance

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.settings.StorageArea
import com.karapp.drawguidance.data.settings.StorageManager
import com.karapp.drawguidance.data.sync.FixedConnectivity
import com.karapp.drawguidance.data.sync.SyncEngine
import com.karapp.drawguidance.data.sync.SyncHold
import com.karapp.drawguidance.data.sync.SyncNetwork
import com.karapp.drawguidance.data.sync.SyncPolicy
import com.karapp.drawguidance.data.sync.TrafficInterceptor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * v37 (5.1) eşitleme denetimi: durdur/başlat, yalnız Wi-Fi (ücretli ağda bekler), günlük indirme/yükleme sınırları
 * (gün değişince sıfırlanır); işler beklerken kaybolmaz, koşul düzelince yapılır. Ağ trafiği sayılır.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V37SyncPolicyTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    @Before fun open() {
        context.getSharedPreferences("sync_policy", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun close() { db.close(); org.koin.core.context.stopKoin() }

    @Test
    fun autoMeteredAndLimitsThenResumes() = runBlocking {
        val net = FixedConnectivity(true)
        var now = 1_700_000_000_000L
        val policy = SyncPolicy(context, net, clock = { now }, scope = TestScope())
        val engine = SyncEngine(db.syncEventDao(), net, TestScope(), clock = { now }).apply { gate = { policy.allowed } }
        var changes = 0
        policy.onChange = { changes++ }
        val calls = ArrayList<String>()
        engine.register("A") { calls += it.payload }
        assertNull(policy.currentHold())

        // v43 otomatik/elle ayarı yok: eşitleme her zaman açık
        engine.enqueue("A", "1", dedupeKey = null)
        assertEquals(1, engine.process())
        assertEquals(listOf("1"), calls)

        // Yalnız Wi-Fi: mobil veride bekler, Wi-Fi'da yapılır
        policy.setNetwork(SyncNetwork.WIFI_ONLY)
        net.setMetered(true)
        assertEquals(SyncHold.METERED, policy.currentHold())
        engine.enqueue("A", "2", dedupeKey = null)
        assertEquals(0, engine.process())
        net.setMetered(false)
        assertEquals(1, engine.process())
        assertTrue("ayar değişince bekleyenler yeniden denenir", changes >= 1)

        // Günlük indirme sınırı (10 MB): dolunca bekler; ertesi gün sayaç sıfırlanır
        policy.setDownloadLimit(10)
        policy.count(11 * SyncPolicy.MB, 0)
        assertEquals(SyncHold.DOWNLOAD_LIMIT, policy.currentHold())
        engine.enqueue("A", "3", dedupeKey = null)
        assertEquals(0, engine.process())
        now += 24 * 3_600_000L
        assertNull(policy.currentHold())
        assertEquals(0L, policy.usage.value.downloaded)
        assertEquals(1, engine.process())

        // Yükleme sınırı
        policy.setUploadLimit(10)
        policy.count(0, 10 * SyncPolicy.MB)
        assertEquals(SyncHold.UPLOAD_LIMIT, policy.currentHold())

        // Çevrimdışı
        policy.setUploadLimit(0)
        net.set(false)
        assertEquals(SyncHold.OFFLINE, policy.currentHold())

        // Ayarlar saklanır
        val again = SyncPolicy(context, net, clock = { now }, scope = TestScope())
        assertEquals(SyncNetwork.WIFI_ONLY, again.settings.value.network)
        assertEquals(10, again.settings.value.downloadLimitMb)
    }

    /** v43 eski "durduruldu" / "otomatik kapalı" ayarları silinir: eşitleme bekletilmez. */
    @Test
    fun legacyPausedAndAutoOffAreIgnored() {
        context.getSharedPreferences("sync_policy", android.content.Context.MODE_PRIVATE).edit().putBoolean("paused", true).putBoolean("auto", false).commit()
        val policy = SyncPolicy(context, FixedConnectivity(true), scope = TestScope())
        assertNull(policy.currentHold())
        val prefs = context.getSharedPreferences("sync_policy", android.content.Context.MODE_PRIVATE)
        assertFalse(prefs.contains("paused") || prefs.contains("auto"))
    }

    @Test
    fun trafficIsCounted() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("x".repeat(5000)))
        server.start()
        val down = AtomicLong(); val up = AtomicLong()
        try {
            val client = OkHttpClient.Builder().addNetworkInterceptor(TrafficInterceptor { d, u -> down.addAndGet(d); up.addAndGet(u) }).build()
            val body = "y".repeat(1000).toRequestBody("text/plain".toMediaType())
            client.newCall(Request.Builder().url(server.url("/a")).post(body).build()).execute().use { it.body!!.string() }
            assertTrue("indirilen ${down.get()}", down.get() >= 5000)
            assertTrue("yüklenen ${up.get()}", up.get() >= 1000)
        } finally {
            server.shutdown()
        }
    }
}

/**
 * v37 (5) depolama: bölüm boyutları, önbellek sınırı (en eskiler silinir, sunucu yanıtları önbelleğine dokunulmaz),
 * önbelleği temizle ve yer aç.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V37StorageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun clean() {
        context.getSharedPreferences("storage", Context.MODE_PRIVATE).edit().clear().commit()
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    @After fun stop() = org.koin.core.context.stopKoin()

    private fun file(dir: File, name: String, kb: Int, age: Long): File = File(dir, name).apply {
        parentFile!!.mkdirs()
        writeBytes(ByteArray(kb * 1024))
        setLastModified(System.currentTimeMillis() - age)
    }

    @Test
    fun measuresTrimsAndClears() {
        var compacted = false
        val storage = StorageManager(context, compactDatabase = { compacted = true })
        val thumbs = File(context.cacheDir, "thumbs")
        val old = file(thumbs, "old.png", 600, 3_000_000)
        val mid = file(thumbs, "mid.png", 600, 2_000_000)
        val new = file(thumbs, "new.png", 600, 1_000)
        val http = file(File(context.cacheDir, "http"), "resp", 100, 9_000_000)
        file(File(context.filesDir, "catalog"), "catalog.bin", 50, 0)

        val sizes = storage.measure()
        assertTrue(sizes.getValue(StorageArea.CACHE) >= 1900 * 1024)
        assertTrue(sizes.getValue(StorageArea.CATALOG) >= 50 * 1024)
        assertEquals(StorageManager.DEFAULT_LIMIT_MB, storage.cacheLimitMb.value)

        // Sınır 1 MB: en eskiler silinir, en yeni kalır; sunucu yanıtları önbelleği kendi sınırında (dokunulmaz)
        storage.setCacheLimit(1)
        assertTrue(storage.trimCache() > 0)
        assertFalse(old.exists())
        assertFalse(mid.exists())
        assertTrue(new.exists())
        assertTrue(http.exists())
        // Ayar saklanır
        assertEquals(1, StorageManager(context).cacheLimitMb.value)

        // Önbelleği temizle
        assertTrue(storage.clearCache() >= 600 * 1024)
        assertFalse(new.exists())
        storage.compact()
        assertTrue(compacted)
    }
}

/** v37 (5, 5.2) ayarlar arayüzü: Kaydetme → Depolama, Bağlantı → Eşitleme ayarları (öteki kartlarla aynı görünüm). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V37StorageSyncUiTest : V33EditorBase() {
    private inline fun <reified T : Any> koin(): T = GlobalContext.get().get()

    @After fun reset() { TestAccount.connectivity.setMetered(false) }

    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun storageAndSyncCards() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNode(hasText("Ayarlar") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("settings_tab_saving") }
        click("settings_tab_saving")
        waitFor { exists("storage_card") }
        waitFor { compose.onAllNodes(hasTestTag("storage_total") and hasText("Toplam", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(exists("storage_drawings") && exists("storage_cache"))
        click("storage_limit_50")
        waitFor { koin<StorageManager>().cacheLimitMb.value == 50 }
        click("storage_clear_cache")
        waitFor { exists("storage_message") }
        shot("v37-settings-storage")

        click("settings_tab_connection")
        waitFor { exists("sync_control_card") }
        val policy = koin<SyncPolicy>()
        click("sync_network_wifi_only")
        waitFor { policy.settings.value.network == SyncNetwork.WIFI_ONLY }
        TestAccount.connectivity.setMetered(true)
        waitFor { compose.onAllNodes(hasTestTag("sync_hold") and hasText("Mobil veride bekliyor", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        // v43 otomatik eşitleme anahtarı yok: her zaman açık
        assertFalse(exists("sync_auto"))
        assertTrue(exists("sync_always_hint"))
        click("sync_down_50")
        click("sync_up_10")
        waitFor { policy.settings.value.downloadLimitMb == 50 && policy.settings.value.uploadLimitMb == 10 }
        assertTrue(exists("sync_usage"))
        shot("v37-settings-sync")
        assertTrue(exists("sync_last") && !exists("sync_now"))
        click("sync_network_any")
        waitFor { policy.settings.value.network == SyncNetwork.ANY }
    }
}
