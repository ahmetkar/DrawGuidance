package com.karapp.drawguidance.content.catalog

import com.karapp.drawguidance.content.vector.LineStyle
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/**
 * v36 kütüphane şekillerinin cihaz veritabanındaki ikili biçimi: JSON'la aynı yollar (4 basamak), bütün alanlar,
 * daha küçük ve daha hızlı. Bozuk veri boş liste verir.
 */
class V36CatalogPackTest {
    private fun deflate(b: ByteArray): Int {
        val d = Deflater(Deflater.BEST_COMPRESSION)
        d.setInput(b); d.finish()
        val out = ByteArrayOutputStream(); val buf = ByteArray(8192)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.size()
    }

    @Test
    fun allFieldsRoundTrip() {
        val paths = listOf(
            VPath(listOf(P(0f, 0f), P(511.99994f, -3.25f), P(-1234.5678f, 0.0001f)), closed = true, fill = "#FFAA00", stroke = "#000000",
                width = 2.75f, line = LineStyle.DASHED, alpha = 0.35f, label = "Çizim ğüşöç 漢字", labelSize = 14.5f, tag = "tick:x:3"),
            VPath(emptyList()),
            VPath(listOf(P(10f, 10f)), line = LineStyle.DOTTED),
        )
        val back = CatalogCodec.unpackPaths(CatalogCodec.packPaths(paths))
        assertEquals(CatalogCodec.encodePaths(paths), CatalogCodec.encodePaths(back))
        assertEquals(paths[0].label, back[0].label)
        assertEquals(paths[0].tag, back[0].tag)
        assertEquals(LineStyle.DOTTED, back[2].line)
        // Bozuk ya da yabancı veri
        assertEquals(emptyList<VPath>(), CatalogCodec.unpackPaths(ByteArray(0)))
        assertEquals(emptyList<VPath>(), CatalogCodec.unpackPaths("[{\"p\":[1,2]}]".toByteArray()))
        assertEquals(emptyList<VPath>(), CatalogCodec.unpackPaths(CatalogCodec.packPaths(paths).copyOf(20)))
    }

    @Test
    fun bundledShapesAreSmallerAndFasterThanJson() {
        val vectors = CatalogCodec.bundledVectors()
        var json = 0L; var bin = 0L; var jsonZ = 0L; var binZ = 0L
        val packed = vectors.map { v ->
            val j = CatalogCodec.encodePaths(v.paths).toByteArray(Charsets.UTF_8)
            val b = CatalogCodec.packPaths(v.paths)
            json += j.size; bin += b.size; jsonZ += deflate(j); binZ += deflate(b)
            assertEquals(v.id, CatalogCodec.encodePaths(v.paths), CatalogCodec.encodePaths(CatalogCodec.unpackPaths(b)))
            j to b
        }
        // Isınma ve ölçüm
        repeat(2) { packed.forEach { (j, b) -> CatalogCodec.decodePaths(String(j, Charsets.UTF_8)); CatalogCodec.unpackPaths(b) } }
        val t0 = System.nanoTime()
        packed.forEach { (j, _) -> CatalogCodec.decodePaths(String(j, Charsets.UTF_8)) }
        val t1 = System.nanoTime()
        packed.forEach { (_, b) -> CatalogCodec.unpackPaths(b) }
        val t2 = System.nanoTime()
        println("V36 şekil yolları (${vectors.size}): JSON ${json / 1024} KB (deflate ${jsonZ / 1024} KB, çözme ${(t1 - t0) / 1_000_000} ms), " +
            "ikili ${bin / 1024} KB (deflate ${binZ / 1024} KB, çözme ${(t2 - t1) / 1_000_000} ms)")
        assertTrue("ikili daha küçük: $bin / $json", bin * 2 < json)
        assertTrue("sıkıştırılmış ikili daha küçük: $binZ / $jsonZ", binZ < jsonZ)
    }
}
