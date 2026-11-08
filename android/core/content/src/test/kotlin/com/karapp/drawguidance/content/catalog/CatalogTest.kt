package com.karapp.drawguidance.content.catalog

import com.karapp.drawguidance.content.ink.HandwritingNet
import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.shapes.ShapeMode
import com.karapp.drawguidance.content.shapes.ShapeNet
import com.karapp.drawguidance.content.vector.LineStyle
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VPath
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream

/** v33 katalog biçimi: şablon/vektör/ağ kodlaması, sunucu kataloğunun önceliği ve sunucu tohum dosyası. */
class CatalogTest {
    @Test
    fun pathsRoundTrip() {
        val paths = listOf(
            VPath(listOf(P(0.1f, 0.2f), P(0.5f, 0.9f), P(0.123456f, 0.3f)), closed = true, fill = "#FFFFFF", stroke = "#2A2A33", width = 2.2f),
            VPath(listOf(P(0f, 0f), P(1f, 1f)), line = LineStyle.DASHED, alpha = 0.5f, tag = "guide"),
        )
        val back = CatalogCodec.decodePaths(CatalogCodec.encodePaths(paths))
        assertEquals(2, back.size)
        assertEquals(0.1235f, back[0].points[2].x, 1e-4f)
        assertTrue(back[0].closed && back[0].fill == "#FFFFFF" && back[0].width == 2.2f)
        assertEquals(LineStyle.DASHED, back[1].line)
        assertEquals("guide", back[1].tag)
    }

    @Test
    fun templatesAndNetsRoundTrip() {
        val t = CatalogCodec.bundledTemplates().first()
        val back = CatalogCodec.template(CatalogCodec.template(t))!!
        assertEquals(t.key, back.key)
        assertTrue(t.geometry.contentEquals(back.geometry) && t.descriptor.contentEquals(back.descriptor))
        val bytes = ShapeNet::class.java.getResourceAsStream("/shapenet/mode_art.bin")!!.readBytes()
        val n = CatalogCodec.modeNet(CatalogCodec.modeNet(CatalogModeNet("ART", 7, CatalogInfo.MODE_NET_FORMAT, bytes)))!!
        assertTrue(n.weights.contentEquals(bytes))
        // v37: sunucunun yalnız daha yeni sürümdeki ağı öncelikli; aynı ya da eski sürüm (ör. v36 tohumundaki 1) kurulmaz
        val bundled = ShapeNet.modeNetVersion(ShapeMode.ART)
        assertEquals(ShapeNet.V38, bundled)
        assertTrue(ShapeNet.installRemote(ShapeMode.ART, bundled + 1, bytes))
        assertEquals(bundled + 1, ShapeNet.baseFor(ShapeMode.ART).second)
        assertTrue(ShapeNet.installRemote(ShapeMode.ART, ShapeNet.MODE_NET_VERSION, bytes))
        assertEquals(bundled, ShapeNet.baseFor(ShapeMode.ART).second)
        assertTrue(ShapeNet.installRemote(ShapeMode.ART, bundled, bytes))
        assertEquals(bundled, ShapeNet.baseFor(ShapeMode.ART).second)
    }

    /** v37 tohumda her modun ağı kendi sürümüyle ve not alma modunun yazı ağı ayrı kayıt olarak bulunur. */
    @Test
    fun seedCarriesTextNet() {
        assertEquals(43000L, CatalogInfo.VERSION)
        val nets = CatalogCodec.bundledModeNets()
        assertEquals(ShapeMode.entries.map { it.name }.toSet() + CatalogInfo.TEXT_NET_MODE, nets.map { it.mode }.toSet())
        for (n in nets) {
            val back = CatalogCodec.modeNet(CatalogCodec.modeNet(n))!!
            assertEquals(n.mode, back.mode); assertEquals(n.version, back.version); assertEquals(n.format, back.format)
            assertTrue(back.weights.contentEquals(n.weights))
        }
        val art = nets.first { it.mode == "ART" }; val kids = nets.first { it.mode == "KIDS" }; val text = nets.first { it.mode == CatalogInfo.TEXT_NET_MODE }
        // v38 bütün mod ağları (çocuk dahil) 38
        assertEquals(ShapeNet.V38, art.version); assertEquals(ShapeNet.V38, kids.version)
        assertEquals(HandwritingNet.VERSION, text.version); assertEquals(CatalogInfo.TEXT_NET_FORMAT, text.format)
        assertNotNull(HandwritingNet.decode(text.weights))
        // v38 yazı ağında "yazı mı" kapısı var (sürüm 2)
        assertEquals(2, HandwritingNet.VERSION)
        assertTrue(HandwritingNet.decode(text.weights)!!.hasGate)
        // Tohum JSON'unda yazı ağı da var
        val seed = CatalogCodec.seed(nets, "2026-10-19T00:00:00Z").jsonObject
        val modes = (seed["modeNets"] as kotlinx.serialization.json.JsonArray).map { CatalogCodec.modeNet(it.jsonObject)!!.mode }
        assertTrue(CatalogInfo.TEXT_NET_MODE in modes)
    }

    /** v37 sunucudan inen yazı ağı: yalnız daha yeni sürüm kurulur; bozuk ağ reddedilir; bilinmeyen biçim yok sayılır. */
    @Test
    fun remoteTextNetInstall() {
        val text = CatalogCodec.bundledModeNets().first { it.mode == CatalogInfo.TEXT_NET_MODE }
        try {
            assertEquals(true, CatalogCodec.installNet(text.copy(version = HandwritingNet.VERSION + 1)))
            assertEquals(HandwritingNet.VERSION + 1, HandwritingNet.current().second)
            assertEquals(false, CatalogCodec.installNet(text.copy(version = HandwritingNet.VERSION + 2, weights = byteArrayOf(1, 2, 3))))
            assertEquals(null, CatalogCodec.installNet(text.copy(format = "inknet-int8-v9")))
            assertEquals(null, CatalogCodec.installNet(text.copy(mode = "UNKNOWN_MODE", format = CatalogInfo.MODE_NET_FORMAT)))
            // aynı sürüm: sunucu ağı kaldırılır, uygulamayla gelen kullanılır
            assertEquals(true, CatalogCodec.installNet(text))
            assertEquals(HandwritingNet.VERSION, HandwritingNet.current().second)
        } finally {
            HandwritingNet.installRemote(HandwritingNet.VERSION, ByteArray(0))
        }
    }

    @Test
    fun remoteVectorsOverrideAndAdd() {
        val bundled = CatalogCodec.bundledVectors()
        val target = bundled.first { it.audience == Audience.ART }
        val changed = target.copy(title = "Sunucudaki ${target.title}", paths = listOf(VPath(listOf(P(0f, 0f), P(1f, 1f)))))
        val added = changed.copy(id = "srv_new", categoryId = "srv_cat", categoryTitle = "Sunucu", title = "Yeni")
        try {
            ShapeLibrary.install(listOf(CatalogCodec.vector(CatalogCodec.vector(changed))!!, added))
            assertEquals("Sunucudaki ${target.title}", ShapeLibrary.find(target.id)!!.title)
            assertEquals(1, ShapeLibrary.find(target.id)!!.paths().size)
            assertNotNull(ShapeLibrary.find("srv_new"))
            assertTrue(ShapeLibrary.categories.any { c -> c.id == "srv_cat" && c.shapes.single().id == "srv_new" })
            // Kategorisi korunur
            val cat = ShapeLibrary.categories.first { c -> c.shapes.any { it.id == target.id } }
            assertEquals(target.categoryId, cat.id)
        } finally {
            ShapeLibrary.install(emptyList())
        }
        assertSame(ShapeLibrary.bundledCategories, ShapeLibrary.categories)
        assertEquals(target.title, ShapeLibrary.find(target.id)!!.title)
    }

    /** `-PcatalogOut=<dosya>` verilince sunucunun tohum dosyası yazılır (uygulamayla gelen katalog). */
    @Test
    fun exportSeed() {
        val out = System.getProperty("catalogOut")
        assumeTrue("catalogOut verilmedi", !out.isNullOrBlank())
        // v37: şekil modlarının ağları kendi sürümleriyle + not alma modunun yazı ağı (NOTES_TEXT)
        val nets = CatalogCodec.bundledModeNets()
        val seed = CatalogCodec.seed(nets, java.time.Instant.now().toString())
        val file = File(out!!)
        file.parentFile?.mkdirs()
        // v36 en yüksek gzip sıkıştırması (dosya bir kez üretilir, sunucu ve istemci yalnız açar)
        object : GZIPOutputStream(file.outputStream(), 1 shl 16) { init { def.setLevel(java.util.zip.Deflater.BEST_COMPRESSION) } }
            .bufferedWriter(Charsets.UTF_8).use { it.write(seed.toString()) }
        val o = seed.jsonObject
        println("katalog: ${file.length()} bayt, ağ ${nets.joinToString { "${it.mode}=${it.version}" }}, şablon ${(o["templates"] as kotlinx.serialization.json.JsonArray).size}, vektör ${(o["vectors"] as kotlinx.serialization.json.JsonArray).size}")
    }
}
