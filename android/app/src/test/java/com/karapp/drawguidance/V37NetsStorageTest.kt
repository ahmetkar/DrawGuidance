package com.karapp.drawguidance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.catalog.CatalogCodec
import com.karapp.drawguidance.content.catalog.CatalogInfo
import com.karapp.drawguidance.content.catalog.CatalogModeNet
import com.karapp.drawguidance.content.ink.HandwritingNet
import com.karapp.drawguidance.content.shapes.ShapeMode
import com.karapp.drawguidance.content.shapes.ShapeNet
import com.karapp.drawguidance.data.catalog.CatalogNetRepository
import com.karapp.drawguidance.data.catalog.CatalogStore
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.local.CatalogNetEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * v37 (2.1) sinir ağları vektör şekiller gibi cihaz veritabanında: uygulamayla gelenler (şekil ağları ve not alma
 * modunun yazı ağı) bir kez yazılır; eski not alma ağının kayıtları silinir; v36'dan kalan ağ dosyaları veritabanına
 * taşınır.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V37NetsStorageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun close() { db.close(); org.koin.core.context.stopKoin() }

    @Test
    fun bundledNetsAreSeededOnceWithTextNet() = runBlocking {
        val repo = CatalogNetRepository(db.catalogNetDao())
        assertTrue(repo.seedBundled())
        val bundled = repo.bundledNets().associateBy { it.mode }
        assertEquals(ShapeMode.entries.map { it.name }.toSet() + CatalogInfo.TEXT_NET_MODE, bundled.keys)
        ShapeMode.entries.forEach { assertEquals(ShapeNet.modeNetVersion(it), bundled.getValue(it.name).version) }
        assertEquals(HandwritingNet.VERSION, bundled.getValue(CatalogInfo.TEXT_NET_MODE).version)
        assertEquals(CatalogInfo.TEXT_NET_FORMAT, bundled.getValue(CatalogInfo.TEXT_NET_MODE).format)
        assertTrue(bundled.getValue(CatalogInfo.TEXT_NET_MODE).weights.size > 100_000)
        // Aynı katalog sürümünde yeniden yazılmaz
        assertFalse(repo.seedBundled())
        assertEquals(CatalogCodec.bundledModeNets().size, db.catalogNetDao().count(CatalogNetEntity.SOURCE_BUNDLED))
    }

    @Test
    fun oldNotesNetIsDeletedOthersKept() = runBlocking {
        val repo = CatalogNetRepository(db.catalogNetDao())
        // v36'dan kalan sunucu ağları: eski not alma ağı (yazı + şekil tek ağda) ve resim ağı
        repo.saveRemote(CatalogModeNet("NOTES", 2, CatalogInfo.MODE_NET_FORMAT, ByteArray(10)), 36000)
        repo.saveRemote(CatalogModeNet("ART", 40, CatalogInfo.MODE_NET_FORMAT, ByteArray(10)), 37001)
        val remote = repo.remoteNets().map { it.mode }
        assertEquals(listOf("ART"), remote)
        // Yeni not alma ağı silinmez
        repo.saveRemote(CatalogModeNet("NOTES", 38, CatalogInfo.MODE_NET_FORMAT, ByteArray(10)), 37001)
        assertEquals(setOf("ART", "NOTES"), repo.remoteNets().map { it.mode }.toSet())
    }

    @Test
    fun legacyNetFilesMoveToDatabase() = runBlocking {
        val store = CatalogStore(context)
        store.saveNet(CatalogModeNet("ART", 40, CatalogInfo.MODE_NET_FORMAT, byteArrayOf(1, 2, 3)))
        store.saveNet(CatalogModeNet("NOTES", 2, CatalogInfo.MODE_NET_FORMAT, byteArrayOf(4)))
        assertEquals(setOf("ART", "NOTES"), store.nets().map { it.mode }.toSet())
        val repo = CatalogNetRepository(db.catalogNetDao())
        // Eşitleyicinin açılıştaki taşıması
        store.nets().forEach { repo.saveRemote(it, store.appliedVersion) }
        store.clearNets()
        assertTrue(store.nets().isEmpty())
        val moved = repo.remoteNets()
        assertEquals(listOf("ART"), moved.map { it.mode })
        assertTrue(moved.single().weights.contentEquals(byteArrayOf(1, 2, 3)))
    }
}
