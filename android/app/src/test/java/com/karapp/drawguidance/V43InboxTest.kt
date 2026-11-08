package com.karapp.drawguidance

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.karapp.drawguidance.data.comicassets.ComicCatalog
import com.karapp.drawguidance.data.comicassets.ComicPacks
import com.karapp.drawguidance.data.comicassets.ComicProp
import com.karapp.drawguidance.data.inbox.BasePacks
import com.karapp.drawguidance.data.inbox.InboxForeground
import com.karapp.drawguidance.data.inbox.InboxRepository
import com.karapp.drawguidance.data.inbox.PackStore
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.local.SyncEventEntity
import com.karapp.drawguidance.data.remote.v1.DrawGuidanceApi
import com.karapp.drawguidance.data.remote.v1.RemoteStore
import com.karapp.drawguidance.data.sync.FixedConnectivity
import com.karapp.drawguidance.data.sync.SyncNetwork
import com.karapp.drawguidance.data.sync.SyncPolicy
import com.karapp.drawguidance.ui.screens.canvasedit.components.PackWindows
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import retrofit2.Retrofit
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

/**
 * v43 gelen kutusu (Firebase yok): kayıt → yoklama (ETag/304) → bilgi alındı onayı → veri → indirme → paket modun
 * klasörüne → teslim onayı; bildirim gösterilir, duyuru ana sayfada, güncelleme bildirilir. Günlük indirme sınırı
 * dolunca yarım paket silinir. Taban paketlerin uygulamada olan öğeleri iki kez görünmez.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V43InboxTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var root: File
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Sunucuda bekleyen teslimatlar: kimlik → (tür, ref, aşama). */
    private val stages = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val acks = CopyOnWriteArrayList<String>()
    private val registrations = CopyOnWriteArrayList<String>()
    private var inboxVersion = 1
    private var conditionalHits = 0

    private val png: ByteArray by lazy {
        val b = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }
        ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private val items = listOf(
        """{"deliveryId":"d-not","kind":"NOTIFICATION","refId":"n1","version":1,"stage":"INFO_SENT"}""",
        """{"deliveryId":"d-pkg","kind":"PACKAGE","refId":"uzay","version":2,"stage":"INFO_SENT"}""",
        """{"deliveryId":"d-ann","kind":"ANNOUNCEMENT","refId":"a1","version":1,"stage":"INFO_SENT"}""",
        """{"deliveryId":"d-upd","kind":"APP_UPDATE","refId":"99","version":1,"stage":"INFO_SENT"}""",
    )

    private fun data(id: String): String = when (id) {
        "d-not" -> """{"kind":"NOTIFICATION","id":"n1","title":"Yeni paket","body":"Uzay paketi geldi"}"""
        "d-pkg" -> """{"kind":"PACKAGE","id":"uzay","name":"Uzay","mode":"COMIC","version":2,"items":[
            {"id":"roket","window":"objects","title":"Roket","category":"Uzay araçları","url":"https://cdn.test/uzay/roket.png?sig=1"},
            {"id":"yildiz","window":"effects","title":"Yıldız","url":"https://cdn.test/uzay/yildiz.png"},
            {"id":"balon","window":"balloons","title":"Uzay balonu","url":"https://cdn.test/uzay/balon.png"}]}"""
        "d-ann" -> """{"kind":"ANNOUNCEMENT","id":"a1","title":"Hoş geldin","body":"Yeni sürüm","imageUrl":"https://cdn.test/a1.png"}"""
        else -> """{"kind":"APP_UPDATE","versionCode":99,"versionName":"9.9","message":"Güncelleme var"}"""
    }

    @Before fun open() {
        context.getSharedPreferences("sync_policy", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        root = File(context.filesDir, "v43test-" + System.nanoTime()).apply { mkdirs() }
        TestAccount.reset()
        TestAccount.auth.signedIn = FakeAuth.DEFAULT
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.startsWith("/api/v1/devices/me") && request.method == "PUT" -> {
                        registrations += request.body.readUtf8()
                        MockResponse().setBody("{}").setHeader("Content-Type", "application/json")
                    }
                    path.startsWith("/api/v1/inbox/ack") -> {
                        val body = json.parseToJsonElement(request.body.readUtf8())
                        val results = StringBuilder()
                        (body as kotlinx.serialization.json.JsonObject)["items"]!!.let { it as kotlinx.serialization.json.JsonArray }.forEach { e ->
                            val o = e as kotlinx.serialization.json.JsonObject
                            val id = (o["deliveryId"] as kotlinx.serialization.json.JsonPrimitive).content
                            val stage = (o["stage"] as kotlinx.serialization.json.JsonPrimitive).content
                            acks += "$id:$stage"
                            // Bilgi alındı: veri hazır (DATA_SENT); teslim: DELIVERED
                            val next = if (stage == "INFO_RECEIVED") "DATA_SENT" else stage
                            stages[id] = next
                            if (results.isNotEmpty()) results.append(',')
                            results.append("\"$id\":\"$next\"")
                        }
                        MockResponse().setBody("{\"results\":{$results}}").setHeader("Content-Type", "application/json")
                    }
                    path.startsWith("/api/v1/inbox/") && path.endsWith("/data") -> {
                        val id = path.removePrefix("/api/v1/inbox/").removeSuffix("/data")
                        MockResponse().setBody(data(id)).setHeader("Content-Type", "application/json")
                    }
                    path.startsWith("/api/v1/inbox") -> {
                        val etag = "\"v$inboxVersion\""
                        if (request.getHeader("If-None-Match") == etag) {
                            conditionalHits++
                            MockResponse().setResponseCode(304).setHeader("ETag", etag).setHeader("X-Poll-After", "900")
                        } else {
                            val open = items.filter { val id = Regex("\"deliveryId\":\"([^\"]+)\"").find(it)!!.groupValues[1]; stages[id] != "DELIVERED" }
                                .map { s -> val id = Regex("\"deliveryId\":\"([^\"]+)\"").find(s)!!.groupValues[1]; stages[id]?.let { s.replace("INFO_SENT", it) } ?: s }
                            MockResponse().setBody("{\"items\":[${open.joinToString(",")}]}")
                                .setHeader("Content-Type", "application/json").setHeader("ETag", etag).setHeader("X-Poll-After", "900")
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After fun close() {
        server.shutdown()
        db.close()
        root.deleteRecursively()
        org.koin.core.context.stopKoin()
    }

    private class Shown(val messages: MutableList<String> = ArrayList(), val updates: MutableList<String> = ArrayList()) : InboxRepository.InboxNotifier {
        override fun message(event: SyncEventEntity): Boolean { messages += event.title.orEmpty(); return true }
        override fun appUpdate(event: SyncEventEntity): Boolean { updates += event.title.orEmpty(); return true }
    }

    /** Sahte indirici: her istekte PNG; [onRead] ile sayılır. */
    private inner class FakeDownloader(var bytes: () -> ByteArray = { png }, var length: Boolean = true) : InboxRepository.Downloader {
        val urls = CopyOnWriteArrayList<String>()
        override suspend fun <T> open(url: String, onOpen: suspend (stream: InputStream, length: Long) -> T): T {
            urls += url
            val b = bytes()
            return onOpen(ByteArrayInputStream(b), if (length) b.size.toLong() else -1L)
        }
    }

    private fun remote(): RemoteStore {
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(DrawGuidanceApi::class.java)
        return RemoteStore(api, json)
    }

    private fun repo(policy: SyncPolicy, packs: PackStore, downloader: InboxRepository.Downloader, shown: Shown, now: () -> Long = { 1_800_000_000_000L }) = InboxRepository(
        dao = db.syncEventDao(), remote = remote(), auth = TestAccount.auth, policy = policy, connectivity = TestAccount.connectivity,
        packs = packs, downloader = downloader, notifier = shown,
        device = InboxRepository.DeviceInfo("dev-1", 43, "1.1.0", "Pixel", "Google", 34) { "tr-TR" },
        prefs = context.getSharedPreferences("inbox-test-" + System.nanoTime(), Context.MODE_PRIVATE),
        announcementsDir = File(root, "announcements"), clock = now, random = Random(1),
    )

    @Test
    fun fullRoundTripStoresFlagsInstallsPackAndAcks() = runBlocking {
        val policy = SyncPolicy(context, TestAccount.connectivity, scope = TestScope())
        val packs = PackStore(File(root, "packs"))
        val shown = Shown()
        val dl = FakeDownloader()
        val repo = repo(policy, packs, dl, shown)

        val r = repo.sync()
        assertEquals(InboxRepository.Outcome.DONE, r.outcome)
        assertEquals(4, r.newItems)
        assertEquals(4, r.received)
        // Kayıtta taban paketler kurulu bildirilir
        assertTrue(registrations.single().contains("\"base-comic\""))
        // Önce bilgi alındı, sonra teslim edildi (toplu)
        for (id in listOf("d-not", "d-pkg", "d-ann", "d-upd")) {
            assertTrue(acks.indexOf("$id:INFO_RECEIVED") in 0 until acks.indexOf("$id:DELIVERED"))
            val e = db.syncEventDao().incoming(id)!!
            assertEquals(SyncEventEntity.DIR_IN, e.direction)
            assertTrue(e.infoAcked && e.dataReceived && e.deliveredAcked)
            assertEquals(SyncEventEntity.STATUS_DONE, e.status)
        }
        // Paket modun klasöründe, öğeler pencerelerinde
        val pack = packs.forMode("COMIC").single()
        assertEquals("uzay", pack.id)
        assertEquals(2, pack.manifest.version)
        assertEquals(File(File(root, "packs"), "comic/uzay").canonicalPath, pack.dir.canonicalPath)
        assertEquals(listOf("roket"), pack.items("objects").map { it.id })
        assertTrue(pack.file(pack.items("objects")[0]).isFile)
        assertEquals(8, pack.items("objects")[0].w)
        assertTrue(packs.installed().any { it.id == "uzay" && it.version == 2 })
        assertFalse(File(root, "packs/.staging").listFiles().orEmpty().any { it.name == "d-pkg" })
        // Bildirim gösterildi, güncelleme bildirildi, duyurunun resmi indi (ana sayfada gösterilecek)
        assertEquals(listOf("Yeni paket"), shown.messages)
        assertEquals(listOf("9.9"), shown.updates)
        val ann = db.syncEventDao().incoming("d-ann")!!
        assertTrue(File(ann.localPath!!).isFile)
        assertFalse(ann.handled)
        repo.announcementShown(ann.id)
        assertTrue(db.syncEventDao().incoming("d-ann")!!.handled)

        // İkinci tur: değişiklik yok → 304, kayıt yinelenmez (gün içinde)
        val r2 = repo.sync()
        assertEquals(InboxRepository.Outcome.NOT_MODIFIED, r2.outcome)
        assertEquals(1, conditionalHits)
        // Paket kuruldu: kurulu paketler değişti, bir kez yeniden kaydolunur
        assertEquals(2, registrations.size)
        assertTrue(registrations.last().contains("\"uzay\""))
        assertTrue(repo.nextPollAt > 1_800_000_000_000L + 900_000L - 1)
    }

    @Test
    fun dailyLimitDiscardsPartialPackage() = runBlocking {
        val policy = SyncPolicy(context, TestAccount.connectivity, scope = TestScope())
        policy.setDownloadLimit(1)
        // Sınırın hemen altı: ilk 16 KB'lık parça sığmaz
        policy.count(SyncPolicy.MB - 4 * 1024, 0)
        val packs = PackStore(File(root, "packs"))
        val dl = FakeDownloader(length = false)
        val repo = repo(policy, packs, dl, Shown())
        val r = repo.sync()
        assertEquals(InboxRepository.Outcome.DONE, r.outcome)
        val e = db.syncEventDao().incoming("d-pkg")!!
        assertTrue(e.infoAcked)
        assertFalse("yarım paket alınmış sayılmaz", e.dataReceived)
        assertTrue(e.lastError!!.contains("sınır"))
        assertTrue(packs.packs.value.isEmpty())
        assertTrue("geçici klasör silindi", File(root, "packs/.staging").listFiles().orEmpty().isEmpty())
        assertFalse(acks.contains("d-pkg:DELIVERED"))

        // Ertesi gün (ya da sınır kalkınca) baştan indirilir ve kurulur
        policy.setDownloadLimit(0)
        repo.sync()
        assertTrue(db.syncEventDao().incoming("d-pkg")!!.dataReceived)
        assertEquals("uzay", packs.forMode("comic").single().id)
    }

    @Test
    fun brokenImageFailsPackageAndReportsToServer() = runBlocking {
        val policy = SyncPolicy(context, TestAccount.connectivity, scope = TestScope())
        val packs = PackStore(File(root, "packs"))
        val dl = FakeDownloader(bytes = { "değil".toByteArray() })
        val repo = repo(policy, packs, dl, Shown())
        repo.sync()
        val e = db.syncEventDao().incoming("d-pkg")!!
        assertEquals(SyncEventEntity.STATUS_FAILED, e.status)
        assertTrue(acks.contains("d-pkg:FAILED"))
        assertTrue(packs.packs.value.isEmpty())
    }

    @Test
    fun waitsWhenSignedOutOfflineOrMetered() = runBlocking {
        val policy = SyncPolicy(context, TestAccount.connectivity, scope = TestScope())
        val repo = repo(policy, PackStore(File(root, "packs")), FakeDownloader(), Shown())
        TestAccount.auth.signedIn = null
        assertEquals(InboxRepository.Outcome.SIGNED_OUT, repo.sync().outcome)
        TestAccount.auth.signedIn = FakeAuth.DEFAULT
        TestAccount.connectivity.set(false)
        assertEquals(InboxRepository.Outcome.OFFLINE, repo.sync().outcome)
        TestAccount.connectivity.set(true)
        policy.setNetwork(SyncNetwork.WIFI_ONLY)
        TestAccount.connectivity.setMetered(true)
        assertEquals(InboxRepository.Outcome.HELD, repo.sync().outcome)
        assertEquals("istek yapılmadı", 0, server.requestCount)
        TestAccount.connectivity.setMetered(false)
    }

    @Test
    fun packStoreInstallReplacesAndDiscards() {
        val store = PackStore(File(root, "packs"))
        fun stage(key: String, name: String): File = store.staging(key).also { File(it, "objects").mkdirs(); File(it, "objects/a.png").writeBytes(png) }
        val item = PackStore.Item("a", "objects", "A", null, "objects/a.png", 8, 6)
        store.install(stage("k1", "v1"), PackStore.Manifest("p1", "Paket", "COMIC", 1, listOf(item)))
        store.install(stage("k2", "v2"), PackStore.Manifest("p1", "Paket 2", "COMIC", 2, listOf(item)))
        assertEquals(listOf(2), store.forMode("COMIC").map { it.manifest.version })
        assertEquals("Paket 2", PackStore(File(root, "packs")).packs.value.single().manifest.name)
        // Eksik dosyalı paket kurulmaz
        val bad = store.staging("k3")
        assertTrue(runCatching { store.install(bad, PackStore.Manifest("p2", "x", "ART", 1, listOf(item))) }.isFailure)
        store.staging("k4")
        store.discardAll()
        assertFalse(File(root, "packs/.staging").exists())
        assertEquals("a_b", PackStore.safe("a/b"))
        assertEquals("_", PackStore.safe(".."))
    }

    @Test
    fun basePackItemsAlreadyInAppAreNotShownTwice() {
        val dir = File(root, "base").apply { mkdirs() }
        val items = listOf(
            PackStore.Item("it_old_01", "objects", "Eski", "Kat", "objects/a.png"),
            PackStore.Item("it_new_01", "objects", "Yeni", "Kat", "objects/b.png"),
            PackStore.Item("balloon-speech", "balloons", "Konuşma", null, "balloons/s.png"),
            PackStore.Item("balloon-yeni", "balloons", "Yeni balon", null, "balloons/y.png"),
        )
        val base = PackStore.Pack(PackStore.Manifest("base-comic", "Taban", "COMIC", 2, items), dir)
        val other = PackStore.Pack(PackStore.Manifest("uzay", "Uzay", "COMIC", 1, items), dir)
        val bundled = ComicCatalog(objects = listOf(ComicProp("it_old_01", listOf("comic"), "c", emptyList(), "a", "a", 1, 1)))
        val known = BasePacks.comicKnown(bundled)
        assertEquals(setOf("it_old_01"), known)
        assertEquals(listOf("pk.base-comic.it_new_01"), ComicPacks.extras(listOf(base), known).objects.map { it.id })
        // Başka paketlerde aynı kimlik olsa da gösterilir
        assertEquals(2, ComicPacks.extras(listOf(other), known).objects.size)
        val merged = ComicPacks.merge(bundled, ComicPacks.extras(listOf(base), known))
        assertEquals(listOf("it_old_01", "pk.base-comic.it_new_01"), merged.objects.map { it.id })
        assertTrue(PackWindows.builtin("balloons", "balloon-speech"))
        assertEquals(listOf("balloon-yeni"), PackWindows.items(listOf(base), "COMIC", "balloons").map { it.second.id })
        assertEquals(2, PackWindows.items(listOf(other), "COMIC", "balloons").size)
        assertTrue(PackWindows.builtin("shapes", BasePacks.itemId("CIRCLE")))
        assertFalse(PackWindows.builtin("shapes", "yeni-sekil"))
        assertEquals(listOf("balloons", "shapes"), PackWindows.forMode("COMIC"))
        assertEquals(listOf("shapes", "diagrams"), PackWindows.forMode("NOTES"))
    }

    @Test
    fun foregroundLoopBacksOffWhenSignedOut() = runBlocking {
        val policy = SyncPolicy(context, TestAccount.connectivity, scope = TestScope())
        TestAccount.auth.signedIn = null
        val repo = repo(policy, PackStore(File(root, "packs")), FakeDownloader(), Shown())
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val fg = InboxForeground(repo, scope)
        fg.start()
        Thread.sleep(300)
        fg.stop()
        assertEquals(0, server.requestCount)
        assertNull(db.syncEventDao().incoming("d-not"))
        assertNotNull(fg)
    }
}
