package com.karapp.drawguidance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.images.ImagesRepository
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.local.CanvasAssetEntity
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.local.FolderRepository
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.data.repository.toUi
import com.karapp.drawguidance.data.share.ShareChunker
import com.karapp.drawguidance.data.share.ShareHello
import com.karapp.drawguidance.data.share.SharePackage
import com.karapp.drawguidance.data.share.SharePackages
import com.karapp.drawguidance.data.share.ShareSettings
import com.karapp.drawguidance.data.share.ShareWire
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config

/** v51 cihazlar arası paylaşım: mesaj biçimi, parçalama ve tuval paketinin bir cihazdan diğerine kurulması. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class ShareProtocolTest {
    @After fun tearDown() = stopKoin()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun messageLayoutMatchesIosFixture() {
        // iOS ShareWireTests aynı baytları bekler: [tür][u32 BE JSON uzunluğu][JSON][kuyruk]
        val bytes = ShareWire.encode(ShareWire.HELLO, """{"v":1}""", byteArrayOf(9, 8))
        assertEquals("01000000077b2276223a317d0908", bytes.joinToString("") { "%02x".format(it) })
        val m = ShareWire.decode(bytes)
        assertEquals(ShareWire.HELLO, m.type)
        assertEquals("""{"v":1}""", m.json)
        assertArrayEquals(byteArrayOf(9, 8), m.tail)
    }

    @Test
    fun helloRoundTripIgnoresUnknownFields() {
        val text = ShareWire.text(ShareHello(name = "Tablet", platform = "android", app = "1.9.0"))
        val back = ShareWire.json.decodeFromString(ShareHello.serializer(), text.dropLast(1) + ""","extra":true}""")
        assertEquals("Tablet", back.name)
        assertEquals(ShareWire.PROTOCOL, back.v)
    }

    @Test
    fun chunkerSplitsAndJoinsOutOfOrder() {
        val sender = ShareChunker(1000)
        val receiver = ShareChunker(1000)
        val message = ByteArray(4321) { (it * 7).toByte() }
        val parts = sender.split(message)
        assertEquals(5, parts.size)
        // İlk parçanın başlığı: mesaj no 1, sıra 0, sayı 5
        assertEquals("000000010000000000000005", parts[0].copyOfRange(0, 12).joinToString("") { "%02x".format(it) })
        var whole: ByteArray? = null
        var last = 0
        for (p in parts.reversed()) whole = receiver.accept(p) { got, _ -> last = got } ?: whole
        assertEquals(5, last)
        assertArrayEquals(message, whole)
        // Tek parçalık mesaj doğrudan döner
        val small = sender.split(byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), receiver.accept(small.single()))
        // Bozuk parça yok sayılır
        assertNull(receiver.accept(byteArrayOf(0, 0, 0, 1)))
    }

    @Test
    fun canvasPackageInstallsOnOtherDeviceWithNewAssetIds() = runBlocking {
        val sender = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val receiver = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = ShareSettings(context).apply { setReceivedFolder(true) }
        fun packages(db: AppDatabase): SharePackages {
            val assets = CanvasAssetStore(db.canvasAssetDao())
            val canvases = CanvasRepository(db.canvasDao(), assets, db)
            val images = ImagesRepository(db.savedImageDao(), ImageExporter(context), assets, db.folderDao())
            return SharePackages(db.canvasDao(), db.canvasAssetDao(), FolderRepository(db.folderDao(), canvases, images), settings) { "Paylaşılanlar" }
        }
        // Gönderen: resimli iki sayfalık not tuvali
        val repo = CanvasRepository(sender.canvasDao(), CanvasAssetStore(sender.canvasAssetDao()), sender)
        val canvas = repo.createCanvas("Ders notu", 800, 1100, "#FFFDF5", aiEnabled = true, kind = CanvasKind.NOTES, pageCount = 2)
        val assetId = "11111111-2222-3333-4444-555555555555"
        // v54 alıcı yalnız gerçek resim kabul eder: 1×1 PNG
        val pngBytes = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 31, 21, -60, -119, 0, 0, 0, 13, 73, 68, 65, 84, 120, -100, 99, -8, -49, -64, -16, 31, 0, 5, 0, 1, -1, -119, -103, 61, 29, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126)
        sender.canvasAssetDao().insert(CanvasAssetEntity(assetId, canvas.localId, "image/png", 1, 1, pngBytes, 0))
        val q = ContentCodec::quantize
        val image = Stroke(
            id = "img", points = listOf(StrokePoint(q(0f), q(0f)), StrokePoint(q(40f), q(0f)), StrokePoint(q(40f), q(40f)), StrokePoint(q(0f), q(40f))),
            colorHex = "#000000", brushType = BrushType.IMAGE, sizePx = 1f, opacity = 1f, order = 0, assetId = assetId,
        )
        val line = Stroke(
            id = "line", points = listOf(StrokePoint(q(10f), q(10f)), StrokePoint(q(200f), q(220f))),
            colorHex = "#1E1E1E", brushType = BrushType.MARKER, sizePx = 6f, opacity = 1f, order = 1,
        )
        repo.saveStrokes(canvas.localId, listOf(listOf(image, line), emptyList()))

        val pkg = packages(sender).canvasPackage(canvas.localId, ShareWire.TYPE_CANVAS, "offer-1", "Telefonum")
        assertNotNull(pkg)
        // Kablodan geçip yeniden okunur
        val wire = pkg!!.encode(ShareWire.PACKAGE)
        val decoded = SharePackage.decode(ShareWire.decode(wire))
        assertEquals("Ders notu", decoded.header.name)
        assertEquals(1, decoded.assets.size)

        val ids = HashMap<String, String>()
        val localId = packages(receiver).install(decoded, ids)
        assertNotEquals(canvas.localId, localId)
        val got = receiver.canvasDao().getByLocalId(localId)!!.toUi()
        assertEquals(CanvasKind.NOTES, got.kind)
        assertEquals(800, got.width)
        assertEquals("#FFFDF5", got.backgroundColorHex)
        assertEquals(2, got.pageCount)
        val newAsset = ids.getValue(assetId)
        assertNotEquals(assetId, newAsset)
        assertEquals(newAsset, got.pages[0].first { it.brushType == BrushType.IMAGE }.assetId)
        assertArrayEquals(pngBytes, receiver.canvasAssetDao().getByIds(listOf(newAsset)).single().data)
        // Resim olmayan ya da içinde kod olan "resim" kurulmaz
        val evil = SharePackage(decoded.header, decoded.content, listOf(pngBytes + "<script>x</script>".toByteArray()))
        assertTrue(runCatching { packages(receiver).install(evil, HashMap()) }.isFailure)
        // "Paylaşılanlar" klasörüne kondu
        val folder = receiver.folderDao().get(got.folderId!!)
        assertEquals("Paylaşılanlar", folder?.name)

        // Canlı yayın güncellemesi aynı tuvali günceller; gönderilmiş resim yeniden gönderilmez
        val sent = HashSet(decoded.header.assets.map { it.id })
        val edited = repo.getCanvas(canvas.localId)!!.let { it.copy(pages = listOf(it.pages[0] + line.copy(id = "line2", order = 2), it.pages[1])) }
        val live = packages(sender).livePackage(edited, "offer-1", 1, sent, "Telefonum")
        assertTrue(live.header.assets.isEmpty())
        packages(receiver).install(SharePackage.decode(ShareWire.decode(live.encode(ShareWire.LIVE_UPDATE))), ids, existingId = localId)
        val updated = receiver.canvasDao().getByLocalId(localId)!!.toUi()
        assertEquals(3, updated.pages[0].size)
        assertEquals(newAsset, updated.pages[0].first { it.brushType == BrushType.IMAGE }.assetId)
        assertEquals(got.folderId, updated.folderId)
        sender.close(); receiver.close()
    }
}
