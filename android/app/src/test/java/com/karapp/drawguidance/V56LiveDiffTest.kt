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
import com.karapp.drawguidance.data.model.LiveDiff

/** v56 aşama 3B (2C-1): canlı yayının fark paketi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V56LiveDiffTest {
    @After fun tearDown() = stopKoin()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val q = ContentCodec::quantize

    private fun line(id: String, x: Float, color: String = "#1E1E1E") = Stroke(
        id = id, points = listOf(StrokePoint(q(x), q(10f)), StrokePoint(q(x + 50f), q(60f))),
        colorHex = color, brushType = BrushType.MARKER, sizePx = 6f, opacity = 1f, order = 0,
    )

    @Test
    fun diffApplyEqualsNext() {
        val a = line("a", 0f); val b = line("b", 10f); val c = line("c", 20f); val d = line("d", 30f)
        val cases = listOf(
            listOf(a, b) to listOf(a, b, c),                 // ekleme
            listOf(a, b, c) to listOf(a, c),                 // silme
            listOf(a, b) to listOf(a, b.copy(colorHex = "#FF0000")), // değişiklik
            listOf(a, b, c) to listOf(c, a, b),              // sıra
            listOf(a, b) to listOf(a, d, b),                 // araya ekleme
            listOf(a, b) to listOf(a, b),                    // değişiklik yok
        )
        for ((prev, next) in cases) {
            val diff = LiveDiff.diff(prev, next)
            assertEquals(next, LiveDiff.apply(prev, diff))
        }
        assertTrue(LiveDiff.diff(listOf(a, b), listOf(a, b)).isEmpty)
        assertNull("yalnız sona ekleme: sıra listesi gitmez", LiveDiff.diff(listOf(a, b), listOf(a, b, c)).order)
        assertNotNull(LiveDiff.diff(listOf(a, b), listOf(a, d, b)).order)
    }

    @Test
    fun diffPackageMatchesFullPackageOnReceiver() = runBlocking {
        val sender = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val receiver = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = ShareSettings(context)
        fun packages(db: AppDatabase): SharePackages {
            val assets = CanvasAssetStore(db.canvasAssetDao())
            val canvases = CanvasRepository(db.canvasDao(), assets, db)
            val images = ImagesRepository(db.savedImageDao(), ImageExporter(context), assets, db.folderDao())
            return SharePackages(db.canvasDao(), db.canvasAssetDao(), FolderRepository(db.folderDao(), canvases, images), settings) { "Paylaşılanlar" }
        }
        val repo = CanvasRepository(sender.canvasDao(), CanvasAssetStore(sender.canvasAssetDao()), sender)
        val canvas = repo.createCanvas("Yayın", 400, 300, "#FFFFFF", aiEnabled = true, kind = CanvasKind.ART, pageCount = 2)
        repo.saveStrokes(canvas.localId, listOf(listOf(line("a", 0f), line("b", 40f)), listOf(line("p2", 5f))))
        val first = repo.getCanvas(canvas.localId)!!
        val sent = HashSet<String>()
        val start = packages(sender).livePackage(first, "o", 1, sent, "Tablet")
        val ids = HashMap<String, String>()
        val localId = packages(receiver).install(SharePackage.decode(ShareWire.decode(start.encode(ShareWire.LIVE_UPDATE))), ids)
        var live = receiver.canvasDao().getByLocalId(localId)!!.toUi()

        // Çizgi eklenir, biri değişir: tek sayfa değişti, fark paketi yeter
        val next = first.copy(pages = listOf(listOf(line("a", 0f, "#FF0000"), line("b", 40f), line("c", 80f)), first.pages[1]))
        val page = LiveDiff.changedPage(first, next)
        assertEquals(0, page)
        val d = LiveDiff.diff(first.pages[0], next.pages[0])
        assertEquals(listOf("a", "c"), d.upserts.map { it.id })
        val pkg = packages(sender).liveDiffPackage(next, 0, d, "o", 2, 1, sent, "Tablet")
        val decoded = SharePackage.decode(ShareWire.decode(pkg.encode(ShareWire.LIVE_DIFF)))
        assertEquals(1L, decoded.header.diffBase)
        live = packages(receiver).applyLiveDiff(decoded, ids, live)
        assertEquals(next.pages, live.pages)
        // Toplu yazım veritabanında da aynı tuval
        packages(receiver).persistLive(live)
        assertEquals(next.pages, receiver.canvasDao().getByLocalId(localId)!!.toUi().pages)

        // Sayfa değişince ya da iki sayfa birden değişince tam paket
        assertNull(LiveDiff.changedPage(next, next.copy(currentPage = 1)))
        assertNull(LiveDiff.changedPage(first, next.copy(pages = listOf(next.pages[0], emptyList()))))
        assertEquals(-1, LiveDiff.changedPage(next, next))
    }
}
