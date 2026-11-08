package com.karapp.drawguidance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.catalog.CatalogBinary
import com.karapp.drawguidance.content.catalog.CatalogCodec
import com.karapp.drawguidance.content.catalog.CatalogInfo
import com.karapp.drawguidance.content.catalog.CatalogModeNet
import com.karapp.drawguidance.content.ink.HandwritingNet
import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.shapes.ShapeMode
import com.karapp.drawguidance.content.shapes.ShapeNet
import com.karapp.drawguidance.data.catalog.CatalogStore
import com.karapp.drawguidance.data.catalog.CatalogNetRepository
import com.karapp.drawguidance.data.catalog.CatalogSyncer
import com.karapp.drawguidance.data.catalog.CatalogVectorRepository
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.local.CatalogVectorEntity
import com.karapp.drawguidance.data.settings.ServerConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * v37 (10) katalog ikili biçimde iner: manifest `binary.sections` bildirirse vektörler ve temel ağlar
 * `Accept: application/vnd.drawguidance.catalog+bin` ile istenir; yollar veritabanına yeniden kodlanmadan yazılır.
 * Eski sunucu (manifestte `binary` yok) JSON'la çalışmayı sürdürür.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V37CatalogBinarySyncTest {
    private val server = MockWebServer()
    private inline fun <reified T : Any> koin(): T = GlobalContext.get().get()

    @After fun stop() {
        org.koin.core.context.stopKoin()
        server.shutdown()
        ShapeLibrary.install(emptyList())
        ShapeNet.installRemote(ShapeMode.ART, ShapeNet.MODE_NET_VERSION, ByteArray(0))
        HandwritingNet.installRemote(HandwritingNet.VERSION, ByteArray(0))
    }

    private val version = CatalogInfo.VERSION + 1
    private val target = CatalogCodec.bundledVectors().first()
    private val vectors = listOf(target.copy(title = "İkili çizim"), target.copy(id = "bin_yeni", title = "İkili yeni", categoryId = "bin", categoryTitle = "İkili"))
    private val netBytes = ShapeNet::class.java.getResourceAsStream("/shapenet/mode_art.bin")!!.readBytes()
    private val artVersion = ShapeNet.modeNetVersion(ShapeMode.ART) + 1
    private val textNetBytes = HandwritingNet::class.java.getResourceAsStream(HandwritingNet.RESOURCE)!!.readBytes()
    private val nets = listOf(
        CatalogModeNet("ART", artVersion, CatalogInfo.MODE_NET_FORMAT, netBytes),
        // v37 not alma modunun yazı ağı da katalogla gelir
        CatalogModeNet(CatalogInfo.TEXT_NET_MODE, HandwritingNet.VERSION + 1, CatalogInfo.TEXT_NET_FORMAT, textNetBytes),
    )

    private fun list(items: List<kotlinx.serialization.json.JsonObject>) =
        buildJsonObject { put("version", version); put("items", JsonArray(items)) }.toString()

    /** Sunucu: [binary] ise manifest ikili bölümleri bildirir ve Accept'e göre ikili döner. */
    private fun serve(binary: Boolean, accepts: MutableList<String>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val accept = request.getHeader("Accept").orEmpty()
                val wantsBin = binary && CatalogBinary.MEDIA_TYPE in accept
                return when (path) {
                    "/api/v1/catalog/manifest" -> MockResponse().setHeader("ETag", "\"cat-$version\"").setBody(
                        if (binary) """{"version":$version,"binary":{"mediaType":"${CatalogBinary.MEDIA_TYPE}","formatVersion":1,"sections":["vectors","modeNets"]}}"""
                        else """{"version":$version}""",
                    )
                    "/api/v1/catalog/templates" -> MockResponse().setBody(list(CatalogCodec.bundledTemplates().map { CatalogCodec.template(it) }))
                    "/api/v1/catalog/vectors" -> {
                        accepts += accept
                        if (wantsBin) MockResponse().setHeader("Content-Type", CatalogBinary.MEDIA_TYPE).setBody(Buffer().write(CatalogBinary.encodeVectors(version, vectors)))
                        else MockResponse().setBody(list(vectors.map { CatalogCodec.vector(it) }))
                    }
                    "/api/v1/catalog/mode-nets" -> {
                        accepts += accept
                        if (wantsBin) MockResponse().setHeader("Content-Type", CatalogBinary.MEDIA_TYPE).setBody(Buffer().write(CatalogBinary.encodeModeNets(version, nets)))
                        else MockResponse().setBody(list(nets.map { CatalogCodec.modeNet(it) }))
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        koin<ServerConfig>().update(server.url("/").toString())
    }

    @Test
    fun binaryCatalogIsRequestedAndApplied() = runBlocking {
        val accepts = CopyOnWriteArrayList<String>()
        serve(binary = true, accepts)
        assertTrue(koin<CatalogSyncer>().sync())
        assertEquals(2, accepts.size)
        assertTrue("ikili istendi: $accepts", accepts.all { it.startsWith(CatalogBinary.MEDIA_TYPE) && "application/json;q=0.5" in it })
        assertEquals("İkili çizim", ShapeLibrary.find(target.id)!!.title)
        assertNotNull(ShapeLibrary.find("bin_yeni"))
        assertEquals(artVersion, ShapeNet.baseFor(ShapeMode.ART).second)
        assertEquals(HandwritingNet.VERSION + 1, HandwritingNet.current().second)
        assertEquals(version, koin<CatalogStore>().appliedVersion)
        // Ağlar cihaz veritabanında (dosyada değil)
        val stored = koin<CatalogNetRepository>().remoteNets().associateBy { it.mode }
        assertEquals(setOf("ART", CatalogInfo.TEXT_NET_MODE), stored.keys)
        assertArrayEquals(textNetBytes, stored.getValue(CatalogInfo.TEXT_NET_MODE).weights)
        assertTrue(koin<CatalogStore>().nets().isEmpty())
        // Veritabanında: yollar ikilide geldiği gibi (paketlenmiş, 4 basamağa yuvarlanmış), çözülünce aynı
        val saved = koin<CatalogVectorRepository>().remoteVectors().associateBy { it.id }
        assertEquals(CatalogCodec.unpackPaths(CatalogCodec.packPaths(target.paths)), saved.getValue(target.id).paths)
        assertEquals("İkili yeni", saved.getValue("bin_yeni").title)
        val rows = koin<AppDatabase>().catalogVectorDao().bySource(CatalogVectorEntity.SOURCE_REMOTE).associateBy { it.id }
        assertArrayEquals(
            CatalogCodec.packPaths(target.paths),
            CatalogVectorRepository.inflate(rows.getValue(target.id).paths),
        )
        // Açılışta yeniden yüklenir
        ShapeLibrary.install(emptyList())
        HandwritingNet.installRemote(HandwritingNet.VERSION, ByteArray(0))
        koin<CatalogSyncer>().installSaved()
        assertEquals("İkili çizim", ShapeLibrary.find(target.id)!!.title)
        assertEquals(HandwritingNet.VERSION + 1, HandwritingNet.current().second)
    }

    @Test
    fun oldServerKeepsJson() = runBlocking {
        val accepts = CopyOnWriteArrayList<String>()
        serve(binary = false, accepts)
        assertTrue(koin<CatalogSyncer>().sync())
        assertTrue("JSON istendi: $accepts", accepts.none { CatalogBinary.MEDIA_TYPE in it })
        assertEquals("İkili çizim", ShapeLibrary.find(target.id)!!.title)
        assertEquals(artVersion, ShapeNet.baseFor(ShapeMode.ART).second)
    }

    @Test
    fun decodesEitherBodyAndFileStoreReadsBoth() {
        val bin = CatalogBinary.encodeVectors(version, vectors)
        val json = list(vectors.map { CatalogCodec.vector(it) }).toByteArray()
        val (fromBin, packed) = CatalogSyncer.decodeVectors(bin)
        val (fromJson, none) = CatalogSyncer.decodeVectors(json)
        assertEquals(vectors.map { it.id }, fromBin.map { it.id })
        assertEquals(fromJson.map { it.title }, fromBin.map { it.title })
        assertEquals(vectors.size, packed!!.size)
        assertNull(none)
        assertEquals(nets.map { it.version }, CatalogSyncer.decodeModeNets(CatalogBinary.encodeModeNets(version, nets)).map { it.version })

        // Veritabanı olmayan kayıt (dosya): ikili de, v35'ten kalan JSON da okunur
        val context: Context = ApplicationProvider.getApplicationContext()
        val store = CatalogStore(context)
        store.saveVectors(bin)
        assertTrue(File(context.filesDir, "catalog/vectors.bin").exists())
        assertEquals("İkili yeni", store.vectors()!!.last().title)
        store.saveVectors(json)
        assertTrue(!File(context.filesDir, "catalog/vectors.bin").exists())
        assertEquals("İkili yeni", store.vectors()!!.last().title)
    }
}
