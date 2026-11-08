package com.karapp.drawguidance

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.images.ImagesRepository
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.canvasOf
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class DatabaseAndExportTest {

    @After fun tearDown() = stopKoin()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun migrationFromVersion1ToLatestKeepsCanvasesCompressesContentAndDropsCloudColumns() {
        val name = "migration-test.db"
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        val json = """[{"id":"8f14e45f-ceea-467a-9575-3f3f1d3c2a11","points":[{"x":10.5,"y":20.25},{"x":30.0,"y":40.0}],"colorHex":"#1E1E1E","brushType":"MARKER","sizePx":14.0,"opacity":1.0,"order":0}]"""
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            // Room v1'in oluşturduğu şema (derlenen AppDatabase_Impl'den)
            db.execSQL("CREATE TABLE IF NOT EXISTS `canvases` (`localId` TEXT NOT NULL, `remoteId` TEXT, `name` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `backgroundColorHex` TEXT NOT NULL, `cloudSyncEnabled` INTEGER NOT NULL, `aiEnabled` INTEGER NOT NULL, `contentJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, `lastSyncedAt` INTEGER, PRIMARY KEY(`localId`))")
            db.execSQL("INSERT INTO canvases VALUES ('c1', 'r1', 'Eski Tuval', 800, 600, '#FFFFFF', 1, 0, '$json', 1, 2, 'SYNCED', 3)")
            db.execSQL("INSERT INTO canvases VALUES ('c2', NULL, 'Boş Tuval', 100, 100, '#000000', 0, 0, '[]', 4, 5, 'PENDING', NULL)")
            db.version = 1
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            val migrated = room.canvasDao().getByLocalId("c1")!!
            assertEquals("Eski Tuval", migrated.name)
            assertEquals(800, migrated.width)
            assertEquals(2L, migrated.updatedAt)
            assertTrue("içerik sıkıştırılmış ikili biçimde olmalı", migrated.content[0] == 'D'.code.toByte())
            val strokes = com.karapp.drawguidance.data.local.ContentCodec.decode(migrated.content)
            assertEquals(1, strokes.size)
            assertEquals(BrushType.MARKER, strokes[0].brushType)
            assertEquals(StrokePoint(10.5f, 20.25f), strokes[0].points[0])
            assertEquals(emptyList<Stroke>(), com.karapp.drawguidance.data.local.ContentCodec.decode(room.canvasDao().getByLocalId("c2")!!.content))
            assertEquals(emptyList<Any>(), room.savedImageDao().observeAll().first())
            assertEquals(emptyList<String>(), room.canvasAssetDao().idsForCanvas("c1"))
        }
        // v4: cloud senkron kolonları tablodan kaldırıldı
        val columns = room.openHelper.readableDatabase.query("PRAGMA table_info(canvases)").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }
        // v6: tuval türü (eski tuvaller resim türünde) — v7: açık sayfa ve kılavuz — v10: arka plan (desen/resim)
        assertEquals(
            listOf("localId", "name", "width", "height", "backgroundColorHex", "aiEnabled", "content", "createdAt", "updatedAt", "kind", "currentPage", "guide", "folderId", "background"),
            columns,
        )
        runBlocking { assertEquals("ART", room.canvasDao().getByLocalId("c1")!!.kind) }
        runBlocking { assertEquals(0, room.canvasDao().getByLocalId("c1")!!.currentPage) }
        assertEquals(23, room.openHelper.readableDatabase.version)
        // v12 (v33): olay tablosu ve cihazın kullanıcısı
        room.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            val tables = buildSet { while (c.moveToNext()) add(c.getString(0)) }
            assertTrue("sync_events ve local_user olmalı: $tables", "sync_events" in tables && "local_user" in tables)
            // v13 (v34): dil ayarı
            assertTrue("language_settings olmalı: $tables", "language_settings" in tables)
            // v14 (v36): kütüphane şekilleri
            assertTrue("catalog_vectors olmalı: $tables", "catalog_vectors" in tables)
            // v15 (v37): sinir ağları
            assertTrue("catalog_nets olmalı: $tables", "catalog_nets" in tables)
        }
        // v11 (v30): eski tuvallerde yapay zekâ bir kez açılır
        runBlocking { assertTrue(room.canvasDao().getByLocalId("c1")!!.aiEnabled && room.canvasDao().getByLocalId("c2")!!.aiEnabled) }
        // v5: kaydedilen resimlerin dosya boyutu
        val imageColumns = room.openHelper.readableDatabase.query("PRAGMA table_info(saved_images)").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }
        assertTrue("saved_images.sizeBytes olmalı: $imageColumns", "sizeBytes" in imageColumns)
        // v8: klasörler (tuval ve resim klasörleri)
        assertTrue("saved_images.folderId olmalı: $imageColumns", "folderId" in imageColumns)
        val tables = room.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        assertTrue("folders tablosu olmalı: $tables", "folders" in tables)
        // v9: şekil veritabanı (v21)
        assertTrue("shape_templates tablosu olmalı: $tables", "shape_templates" in tables)
        runBlocking { assertEquals(0, room.shapeTemplateDao().count()) }
        // v10: arka plan kütüphanesi, 3B Şekillerim ve 3B dokuları (v23)
        for (t in listOf("paper_assets", "models3d", "textures3d")) assertTrue("$t tablosu olmalı: $tables", t in tables)
        room.close()
    }

    @Test
    fun savingCanvasAsImageWritesPngAndListsIt() {
        val room = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val assets = CanvasAssetStore(room.canvasAssetDao())
        val repository = ImagesRepository(room.savedImageDao(), ImageExporter(context, useGallery = false), assets)
        // Tuvale eklenmiş, veritabanında saklanan kırmızı bir resim
        val red = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        val prepared = com.karapp.drawguidance.data.images.ImageImportValidator.check(ImageImportValidatorTest.png(red)).image!!
        val assetId = runBlocking { assets.save("c1", prepared) }
        val canvas = canvasOf(
            localId = "c1", name = "Deniz Feneri", width = 640, height = 480, backgroundColorHex = "#F6F1E7",
            aiEnabled = false, createdAt = 0, updatedAt = 0,
            strokes = listOf(
                Stroke("s", listOf(StrokePoint(10f, 10f), StrokePoint(600f, 400f)), "#1E1E1E", BrushType.PASTEL, 20f, 1f, 0),
                Stroke("img", listOf(StrokePoint(400f, 20f), StrokePoint(600f, 20f), StrokePoint(600f, 120f), StrokePoint(400f, 120f)), "#000000", BrushType.IMAGE, 1f, 1f, 1, assetId = assetId),
            ),
        )

        val saved = runBlocking { repository.saveCanvasAsImage(canvas) }
        assertEquals(640, saved.width)
        assertEquals(480, saved.height)
        val listed = runBlocking { room.savedImageDao().observeAll().first() }
        assertEquals(listOf(saved.id), listed.map { it.id })

        val path = Uri.parse(saved.uri).path!!
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        assertEquals(640, options.outWidth)
        assertEquals(480, options.outHeight)
        // Resim elemanı PNG'ye çizilmiş olmalı (bellek önbelleği temizlense de veritabanından yüklenir)
        val pixel = BitmapFactory.decodeFile(path).getPixel(500, 70)
        assertEquals(android.graphics.Color.RED, pixel)
        assertTrue(saved.location.contains("DrawGuidance"))

        runBlocking { repository.delete(saved.id) }
        assertFalse(File(path).exists())
        assertTrue(runBlocking { room.savedImageDao().observeAll().first() }.isEmpty())
        room.close()
    }
}
