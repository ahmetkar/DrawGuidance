package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.library.ShapeLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * v37 mod ağları: kütüphaneden üretilen nesne/karakter/efekt şablonları, ART ve COMIC ağlarının yeni sınıfları, yeniden
 * eğitilen NOTES şekil ağı (yeni sürüm; eski not alma ağının cihazda öğrendikleri silinir) ve değişmeyen KIDS ağı.
 */
class ModeNetsV37Test {
    private val index by lazy { ShapeIndex.fromCatalog(ShapeTemplates.catalog) }

    @Test fun libraryTemplatesAreBundled() {
        val e = LibraryTemplates.entries
        val objects = e.filter { it.template.key.startsWith(LibraryTemplates.OBJECT_PREFIX) }
        val chars = e.filter { it.template.key.startsWith(LibraryTemplates.CHARACTER_PREFIX) }
        val effects = e.filter { it.template.key.startsWith(LibraryTemplates.EFFECT_PREFIX) }
        println("kütüphane şablonları: nesne ${objects.size}, karakter ${chars.size}, efekt ${effects.size}")
        assertTrue(objects.size >= 150); assertTrue(chars.size >= 15); assertTrue(effects.size >= 20)
        assertEquals(e.size, objects.size + chars.size + effects.size)
        // her biri kütüphanedeki bir şekle bağlı, moda özel (genel aramada yok)
        for (x in e) {
            // v43 nesne kütüphanesi (Şekil ekle → Nesneler) kaldırıldı: nesne şablonları tanıma için kendi çizgileriyle
            // kalır, kütüphanede karşılıkları yok; karakter ve efektlerin karşılığı var
            if (x.template.key.startsWith(LibraryTemplates.OBJECT_PREFIX)) assertNull(ShapeLibrary.find(x.libraryId))
            else assertNotNull("${x.template.key} → ${x.libraryId}", ShapeLibrary.find(x.libraryId))
            assertEquals(x.libraryId, LibraryTemplates.libraryId(x.template.key))
            assertTrue(ModeCatalog.isModeOnly(x.template.key))
            assertTrue(x.template.strokes.isNotEmpty() && x.template.strokes.all { s -> s.size >= 2 })
        }
        val all = ShapeTemplates.all.map { it.key }.toSet()
        val modeOnly = ShapeTemplates.modeOnly.map { it.key }.toSet()
        assertTrue(e.none { it.template.key in all } && e.all { it.template.key in modeOnly })
        // kodlama gidiş-dönüş
        val back = LibraryTemplates.decode(LibraryTemplates.encode(e))!!
        assertEquals(e.map { it.template.key }, back.map { it.template.key })
    }

    @Test fun scopesOfLibraryTemplates() {
        val models = ShapeModeModels(null)
        val art = models.keys(ShapeMode.ART, index); val comic = models.keys(ShapeMode.COMIC, index)
        val notes = models.keys(ShapeMode.NOTES, index); val kids = models.keys(ShapeMode.KIDS, index)
        val obj = LibraryTemplates.entries.map { it.template }.filter { it.key.startsWith(LibraryTemplates.OBJECT_PREFIX) }
        assertTrue("nesneler resimde", obj.all { it.key in art })
        assertTrue("nesneler çizgi romanda", obj.all { it.key in comic })
        assertTrue("karakter ve efektler yalnız çizgi romanda", LibraryTemplates.entries.filter { !it.template.key.startsWith(LibraryTemplates.OBJECT_PREFIX) }
            .all { it.template.key in comic && it.template.key !in art && it.template.key !in notes })
        assertTrue("notta yalnız not kategorilerindeki nesneler", notes.count { it.startsWith(LibraryTemplates.OBJECT_PREFIX) } in 1 until obj.size)
        assertTrue("çocuk modunda kütüphane şablonu yok", kids.none { LibraryTemplates.isLibraryKey(it) })
        // eski "nesne." şablonları (genel katalog) moda özel değil
        assertTrue(ShapeTemplates.all.any { it.key.startsWith("nesne.") })
        assertTrue(ShapeTemplates.all.none { ModeCatalog.isModeOnly(it.key) })
    }

    @Test fun bundledNetsKnowNewClassesAndVersions() {
        val models = ShapeModeModels(null)
        for (mode in ShapeMode.entries) {
            val net = ShapeNet.bundledFor(mode)!!
            assertEquals("$mode ağı kapsamın bütün şablonlarını tanır", models.keys(mode, index).toList(), net.keys)
        }
        val art = ShapeNet.bundledFor(ShapeMode.ART)!!.keys.toSet()
        val comic = ShapeNet.bundledFor(ShapeMode.COMIC)!!.keys.toSet()
        assertTrue(LibraryTemplates.entries.filter { it.template.key.startsWith(LibraryTemplates.OBJECT_PREFIX) }.all { it.template.key in art })
        assertTrue(LibraryTemplates.entries.all { it.template.key in comic })
        // v38 bütün modların ağları 4+ elemanla yeniden eğitildi (çocuk dahil): 38
        assertEquals(ShapeNet.V38, ShapeNet.modeNetVersion(ShapeMode.ART))
        assertEquals(ShapeNet.V38, ShapeNet.modeNetVersion(ShapeMode.COMIC))
        assertEquals(ShapeNet.V38, ShapeNet.modeNetVersion(ShapeMode.NOTES))
        assertEquals(ShapeNet.V38, ShapeNet.modeNetVersion(ShapeMode.KIDS))
        assertEquals(37, ShapeNet.resetBelow(ShapeMode.NOTES))
        assertEquals(0, ShapeNet.resetBelow(ShapeMode.ART))
        assertEquals(0, ShapeNet.resetBelow(ShapeMode.KIDS))
        println("ağ sınıfları: " + ShapeMode.entries.joinToString { "$it ${ShapeNet.bundledFor(it)!!.keys.size}" })
    }

    /** Eski not alma ağının cihazda öğrendikleri silinir (yeniden çalışılmaz); resim modunun öğrendikleri korunur. */
    @Test fun oldNotesLearningIsDiscardedOthersKept() {
        val dir = Files.createTempDirectory("v37modes").toFile()
        val models0 = ShapeModeModels(null)
        for (mode in listOf(ShapeMode.NOTES, ShapeMode.ART)) {
            val keys = models0.keys(mode, index).toList()
            val net = ModeNet.init(keys, ShapeNet.bundled, null)
            val old = ModeModel(mode, net, hashMapOf(keys[0] to 3), HashMap(), arrayListOf(keys[0] to FloatArray(ShapeNet.IN_DIM)), trained = 3,
                baseVersion = ShapeNet.MODE_NET_VERSION)
            File(dir, "shape_mode_${mode.name.lowercase()}.bin").writeBytes(old.encode())
        }
        val models = ShapeModeModels(dir)
        assertEquals("eski not modeli sayılmaz", 0, models.info(ShapeMode.NOTES, index).accepted)
        assertEquals(3, models.info(ShapeMode.ART, index).accepted)
        val notes = models.model(ShapeMode.NOTES, index)
        assertEquals(0, notes.accepted); assertEquals(0, notes.trained); assertEquals(ShapeNet.modeNetVersion(ShapeMode.NOTES), notes.baseVersion)
        val art = models.model(ShapeMode.ART, index)
        assertEquals("resim modunun kabulleri yeni ağda yeniden çalışılır", 3, art.accepted)
        assertEquals(ShapeNet.modeNetVersion(ShapeMode.ART), art.baseVersion)
        // yeni dosyalar v37 biçiminde ve artık eski sayılmaz
        val reread = ModeModel.decode(File(dir, "shape_mode_notes.bin").readBytes())!!
        assertEquals(ModeModel.FORMAT, reread.format)
        assertTrue(!ShapeModeModels.isStale(reread))
    }
}
