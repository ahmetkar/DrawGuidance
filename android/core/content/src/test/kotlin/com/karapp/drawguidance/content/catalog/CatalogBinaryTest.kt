package com.karapp.drawguidance.content.catalog

import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.Fit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

/**
 * v37 katalogun ikili aktarım biçimi ([CatalogBinary]): sunucuyla ortak örnek dosya (`catalog-binary-fixture.json`)
 * bayt bayt aynı çıktıyı verir (sunucudaki `CatalogBinaryTest.java` aynı dosyayı ve aynı özetleri sınar), çözülen
 * öğeler JSON yoluyla çözülenlerle aynıdır, eksik alanlara JSON çözücüsünün öntanımlıları konur, bozuk veri reddedilir.
 */
class CatalogBinaryTest {
    companion object {
        /** Beklenen baytların SHA-256 özeti: sunucu testindeki sabitlerle aynı olmalı (iki kopya ayrışmasın). */
        const val VECTORS_SHA256 = "7c3c993e9f7fe2f96eeb426eda9b1971fb8ffa5f59fb343aa71030614cf6ce85"
        const val MODE_NETS_SHA256 = "4d89bdc3d9156f1673703b39b6bda8d7338f37d99891a95594471f0cf16398c3"
        const val FIXTURE_VERSION = 37000L
    }

    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/catalog/$name")?.use { it.readBytes() } ?: error("örnek dosya yok: $name")

    private fun hex(name: String): ByteArray {
        val text = String(resource(name), Charsets.US_ASCII).filterNot { it.isWhitespace() }
        return ByteArray(text.length / 2) { i -> text.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
    }

    private fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private val fixture: JsonObject by lazy {
        CatalogCodec.json.parseToJsonElement(String(resource("catalog-binary-fixture.json"), Charsets.UTF_8)) as JsonObject
    }

    /** Uygulamanın JSON yolu: öğe → [CatalogCodec.vector] (çözülemeyen öğe atlanır). */
    private val jsonVectors: List<CatalogVector> by lazy {
        (fixture["vectors"] as JsonArray).mapNotNull { CatalogCodec.vector(it as JsonObject) }
    }
    private val jsonNets: List<CatalogModeNet> by lazy {
        (fixture["modeNets"] as JsonArray).mapNotNull { CatalogCodec.modeNet(it as JsonObject) }
    }

    @Test
    fun fixtureVectorsMatchServerBytes() {
        assertEquals(5, jsonVectors.size)
        val expected = hex("catalog-binary-fixture.vectors.hex")
        assertEquals(VECTORS_SHA256, sha256(expected))
        assertArrayEquals(expected, CatalogBinary.encodeVectors(FIXTURE_VERSION, jsonVectors))
    }

    @Test
    fun fixtureModeNetsMatchServerBytes() {
        assertEquals(3, jsonNets.size)
        val expected = hex("catalog-binary-fixture.modenets.hex")
        assertEquals(MODE_NETS_SHA256, sha256(expected))
        assertArrayEquals(expected, CatalogBinary.encodeModeNets(FIXTURE_VERSION, jsonNets))
    }

    @Test
    fun vectorsDecodeToTheSameItemsAsJson() {
        val bytes = hex("catalog-binary-fixture.vectors.hex")
        assertTrue(CatalogBinary.isBinary(bytes))
        val section = CatalogBinary.decodeVectorEntries(bytes)
        assertEquals(CatalogBinary.SECTION_VECTORS, section.section)
        assertEquals(FIXTURE_VERSION, section.version)
        assertEquals(jsonVectors.size, section.items.size)
        section.items.zip(jsonVectors).forEach { (entry, json) ->
            val v = entry.vector
            assertEquals(json.copy(paths = emptyList()), v.copy(paths = emptyList()))
            assertEquals(json.id, CatalogCodec.encodePaths(json.paths), CatalogCodec.encodePaths(v.paths))
            assertEquals(json.paths.size, v.paths.size)
            // Paketli yollar cihaz veritabanındaki biçimin aynısı; türler JSON'daki gibi
            assertArrayEquals(CatalogCodec.packPaths(json.paths), entry.packedPaths)
            assertEquals(CatalogKinds.forVector(json.audience), entry.kinds)
        }
        val first = section.items.first().vector
        assertEquals("Çizim örneği ğüşöçİı 漢字 😀", first.title)
        assertEquals(4f / 3f, first.aspect)
        assertEquals(listOf("örnek", "test", ""), first.tags)
        assertEquals(Audience.NOTES, first.audience)
        assertEquals(Fit.SQUARE, first.fit)
        assertEquals("Etiket ğ", first.paths[0].label)
        assertEquals(0.0625f, first.paths[0].labelSize)
        assertEquals(0f, first.paths[2].width)
        assertEquals(0f, first.paths[3].alpha)
        assertEquals(CatalogBinary.decodeVectors(bytes).items, section.items.map { it.vector })
    }

    @Test
    fun modeNetsDecodeToTheSameItemsAsJson() {
        val section = CatalogBinary.decodeModeNets(hex("catalog-binary-fixture.modenets.hex"))
        assertEquals(CatalogBinary.SECTION_MODE_NETS, section.section)
        assertEquals(FIXTURE_VERSION, section.version)
        assertEquals(jsonNets.size, section.items.size)
        section.items.zip(jsonNets).forEach { (n, json) ->
            assertEquals(json.mode, n.mode)
            assertEquals(json.version, n.version)
            assertEquals(json.format, n.format)
            assertArrayEquals(json.weights, n.weights)
        }
        assertEquals(300, section.items[0].weights.size)
        assertEquals(0, section.items[1].weights.size)
    }

    @Test
    fun bundledCatalogRoundTripsAndIsSmallerThanJson() {
        val vectors = CatalogCodec.bundledVectors()
        val bin = CatalogBinary.encodeVectors(CatalogInfo.VERSION, vectors)
        val json = buildJsonObject {
            put("version", CatalogInfo.VERSION)
            put("items", JsonArray(vectors.map { CatalogCodec.vector(it) as JsonElement }))
        }.toString().toByteArray(Charsets.UTF_8)
        val back = CatalogBinary.decodeVectors(bin)
        assertEquals(CatalogInfo.VERSION, back.version)
        assertEquals(vectors.map { it.id }, back.items.map { it.id })
        // JSON yoluyla gelen şekillerle aynı (yollar 4 basamağa yuvarlanmış)
        val viaJson = CatalogCodec.items(String(json, Charsets.UTF_8)).second.map { CatalogCodec.vector(it)!! }
        back.items.zip(viaJson).forEach { (b, j) ->
            assertEquals(j.copy(paths = emptyList()), b.copy(paths = emptyList()))
            assertEquals(j.id, CatalogCodec.encodePaths(j.paths), CatalogCodec.encodePaths(b.paths))
        }
        // Sunucunun yaptığı gibi: JSON'dan gelen şekiller yeniden kodlanınca aynı baytlar
        assertArrayEquals(bin, CatalogBinary.encodeVectors(CatalogInfo.VERSION, viaJson))
        val binZ = gzip(bin).size; val jsonZ = gzip(json).size
        println("V37 ikili katalog (${vectors.size} şekil): JSON ${json.size / 1024} KB (gzip ${jsonZ / 1024} KB), ikili ${bin.size / 1024} KB (gzip ${binZ / 1024} KB)")
        assertTrue("ikili daha küçük: ${bin.size} / ${json.size}", bin.size * 2 < json.size)
        assertTrue("sıkıştırılmış ikili daha küçük: $binZ / $jsonZ", binZ < jsonZ)
        assertFalse(CatalogBinary.isBinary(json))
    }

    /** Bayrağı olmayan alanlar JSON çözücüsünün öntanımlılarını alır; bilinmeyen değerler JSON'daki gibi düşer. */
    @Test
    fun missingFieldsGetJsonDefaultsAndUnknownTrailingFieldsAreSkipped() {
        val emptyPaths = CatalogCodec.packPaths(emptyList())
        val item = Raw().apply {
            varint(1024) // yalnız gelecekteki (bilinmeyen) bir alan bayrağı
            string("x.min")
            bytes(emptyPaths)
            raw(byteArrayOf(9, 9, 9)) // yeni sürümün öğe sonuna eklediği alan
        }.toByteArray()
        val withUnknownEnums = Raw().apply {
            varint(CatalogBinary.V_AUDIENCE or CatalogBinary.V_FIT)
            string("x.enum"); string("TEENS"); string("WIDTH")
            bytes(emptyPaths)
        }.toByteArray()
        val bytes = Raw().apply {
            raw("DGCB".toByteArray()); byte(1); byte(CatalogBinary.SECTION_VECTORS); varint(5); varint(2)
            bytes(item); bytes(withUnknownEnums)
        }.toByteArray()
        val items = CatalogBinary.decodeVectors(bytes).items
        assertEquals(CatalogVector("x.min", "", "remote", "Yeni şekiller", "", Audience.ART, Fit.BOX, 1f, emptyList(), emptyList()), items[0])
        // JSON çözücüsüyle aynı sonuç
        val json = CatalogCodec.vector(buildJsonObject { put("id", "x.min"); put("paths", "[]") })
        assertEquals(json, items[0])
        assertEquals(Audience.ART, items[1].audience)
        assertEquals(Fit.BOX, items[1].fit)

        val net = Raw().apply { varint(0); string("ART"); bytes(byteArrayOf(1, 2)) }.toByteArray()
        val nets = Raw().apply {
            raw("DGCB".toByteArray()); byte(1); byte(CatalogBinary.SECTION_MODE_NETS); varint(5); varint(1); bytes(net)
        }.toByteArray()
        val n = CatalogBinary.decodeModeNets(nets).items.single()
        assertEquals(0, n.version)
        assertEquals(CatalogInfo.MODE_NET_FORMAT, n.format)
        assertArrayEquals(byteArrayOf(1, 2), n.weights)
    }

    @Test
    fun largeVersionsAndNegativeNetVersionsRoundTrip() {
        val big = 1L shl 40
        assertEquals(big, CatalogBinary.decodeVectors(CatalogBinary.encodeVectors(big, emptyList())).version)
        val nets = listOf(CatalogModeNet("ART", -5, "x", ByteArray(200) { it.toByte() }))
        val back = CatalogBinary.decodeModeNets(CatalogBinary.encodeModeNets(big, nets))
        assertEquals(big, back.version)
        assertEquals(-5, back.items.single().version)
        assertArrayEquals(nets.single().weights, back.items.single().weights)
    }

    @Test
    fun corruptOrForeignDataIsRejected() {
        val good = hex("catalog-binary-fixture.vectors.hex")
        val cases = mapOf(
            "boş" to ByteArray(0),
            "JSON" to "{\"version\":1,\"items\":[]}".toByteArray(),
            "kesik" to good.copyOf(good.size - 7),
            "kesik başlık" to good.copyOf(7),
            "yeni biçim sürümü" to good.copyOf().also { it[4] = 2 },
        )
        cases.forEach { (why, bytes) ->
            try {
                CatalogBinary.decodeVectors(bytes)
                fail("reddedilmeliydi: $why")
            } catch (e: IllegalArgumentException) {
                // beklenen
            }
        }
        // Yanlış bölüm
        try {
            CatalogBinary.decodeModeNets(good)
            fail("bölüm denetlenmeli")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("bölüm"))
        }
    }

    private fun gzip(b: ByteArray): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()

    /** Elle bayt dizisi kurmak için (öğe ve kap). */
    private class Raw {
        private val out = ByteArrayOutputStream()
        fun byte(v: Int) = out.write(v)
        fun raw(b: ByteArray) = out.write(b)
        fun varint(value: Int) {
            var v = value
            while (v and 0x7F.inv() != 0) { out.write((v and 0x7F) or 0x80); v = v ushr 7 }
            out.write(v)
        }
        fun bytes(b: ByteArray) { varint(b.size); out.write(b) }
        fun string(s: String) = bytes(s.toByteArray(Charsets.UTF_8))
        fun toByteArray(): ByteArray = out.toByteArray()
    }
}
