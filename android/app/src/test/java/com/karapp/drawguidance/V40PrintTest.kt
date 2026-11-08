package com.karapp.drawguidance

import com.karapp.drawguidance.data.print.ArgbCmykSource
import com.karapp.drawguidance.data.print.BlackGeneration
import com.karapp.drawguidance.data.print.ByteCmykSource
import com.karapp.drawguidance.data.print.CbzWriter
import com.karapp.drawguidance.data.print.CmykConverter
import com.karapp.drawguidance.data.print.CmykJpegEncoder
import com.karapp.drawguidance.data.print.CmykOptions
import com.karapp.drawguidance.data.print.CmykTiffEncoder
import com.karapp.drawguidance.data.print.MiniPdfWriter
import com.karapp.drawguidance.data.print.SoftProof
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.abs

/** v40 baskı: RGB → CMYK dönüşümü, CMYK JPEG/TIFF/PDF kodlayıcıları, CBZ ve ekran provası (saf JVM). */
class V40PrintTest {

    private fun cmyk(argb: Int, options: CmykOptions = CmykOptions()): IntArray {
        val out = ByteArray(4)
        CmykConverter(options).convert(argb, out, 0)
        return IntArray(4) { out[it].toInt() and 0xFF }
    }

    @Test
    fun conversionWithBlackGenerationAndInkLimit() {
        assertArrayEquals(intArrayOf(0, 0, 0, 0), cmyk(0xFFFFFFFF.toInt()))
        // Saf siyah yalnız K ile (zengin siyah değil)
        assertArrayEquals(intArrayOf(0, 0, 0, 255), cmyk(0xFF000000.toInt()))
        assertArrayEquals(intArrayOf(0, 255, 255, 0), cmyk(0xFFFF0000.toInt()))
        assertArrayEquals(intArrayOf(255, 0, 255, 0), cmyk(0xFF00FF00.toInt()))
        // Saydam piksel kağıt beyazı
        assertArrayEquals(intArrayOf(0, 0, 0, 0), cmyk(0x00123456))
        // Orta gri: hafif siyah üretiminde K yok (CMY), yoğunda tamamı K
        val gray = 0xFF808080.toInt()
        val light = cmyk(gray, CmykOptions(BlackGeneration.LIGHT, 0))
        val heavy = cmyk(gray, CmykOptions(BlackGeneration.HEAVY, 0))
        assertTrue(light[3] < 5 && light[0] > 120)
        assertTrue(heavy[3] > 120 && heavy[0] < 5)
        // Toplam mürekkep sınırı: koyu, doygun renkte %300'ü aşmaz
        for (c in listOf(0xFF102030.toInt(), 0xFF301000.toInt(), 0xFF050510.toInt(), 0xFF200020.toInt())) {
            val limited = cmyk(c, CmykOptions(BlackGeneration.LIGHT, 300))
            assertTrue("TAC ${limited.sum()}", limited.sum() <= 300 * 255 / 100 + 3)
            val free = cmyk(c, CmykOptions(BlackGeneration.LIGHT, 0))
            assertTrue(free.sum() >= limited.sum())
            assertEquals("siyah korunur", free[3], limited[3])
        }
    }

    private fun gradient(w: Int, h: Int): ByteArray = ByteArray(w * h * 4).also { d ->
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            d[i] = (x * 255 / (w - 1)).toByte()
            d[i + 1] = (y * 255 / (h - 1)).toByte()
            d[i + 2] = ((x + y) * 255 / (w + h - 2)).toByte()
            d[i + 3] = (if ((x / 8 + y / 8) % 2 == 0) 40 else 160).toByte()
        }
    }

    @Test
    fun cmykJpegHasAdobeMarkerAndDecodesToTheSameInks() {
        val w = 37; val h = 21
        val data = gradient(w, h)
        val jpeg = CmykJpegEncoder.encode(ByteCmykSource(w, h, data), quality = 98)
        assertEquals(0xFF, jpeg[0].toInt() and 0xFF); assertEquals(0xD8, jpeg[1].toInt() and 0xFF)
        assertEquals(0xD9, jpeg.last().toInt() and 0xFF)
        // APP14 "Adobe", dönüşüm 0 (CMYK)
        val app14 = (0 until jpeg.size - 1).first { (jpeg[it].toInt() and 0xFF) == 0xFF && (jpeg[it + 1].toInt() and 0xFF) == 0xEE }
        assertEquals("Adobe", String(jpeg, app14 + 4, 5, Charsets.US_ASCII))
        assertEquals(0, jpeg[app14 + 4 + 11].toInt())
        // Java'nın JPEG okuyucusu ham 4 kanalı verir (Adobe geleneği: ters değerler)
        val raster = JdkRaster.readJpeg(jpeg)
        assertEquals(w, raster.width); assertEquals(h, raster.height); assertEquals(4, raster.bands)
        var worst = 0
        for (y in 0 until h) for (x in 0 until w) for (b in 0 until 4) {
            val expected = data[(y * w + x) * 4 + b].toInt() and 0xFF
            val got = 255 - raster.sample(x, y, b)
            worst = maxOf(worst, abs(expected - got))
        }
        assertTrue("en büyük fark $worst", worst <= 24)
    }

    private fun le(b: ByteArray) = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)

    @Test
    fun cmykTiffIsSeparatedDeflateWithExactPixels() {
        val w = 50; val h = 70
        val data = gradient(w, h)
        val tiff = CmykTiffEncoder.encode(ByteCmykSource(w, h, data))
        val buf = le(tiff)
        assertEquals('I'.code.toByte(), tiff[0]); assertEquals('I'.code.toByte(), tiff[1]); assertEquals(42, buf.getShort(2).toInt())
        val ifd = buf.getInt(4)
        val n = buf.getShort(ifd).toInt()
        val tags = HashMap<Int, Triple<Int, Int, Int>>()
        for (i in 0 until n) {
            val o = ifd + 2 + i * 12
            val type = buf.getShort(o + 2).toInt()
            val value = if (type == 3) buf.getShort(o + 8).toInt() and 0xFFFF else buf.getInt(o + 8)
            tags[buf.getShort(o).toInt() and 0xFFFF] = Triple(type, buf.getInt(o + 4), value)
        }
        assertEquals(w, tags[256]!!.third); assertEquals(h, tags[257]!!.third)
        assertEquals(8, tags[259]!!.third)  // Adobe Deflate
        assertEquals(5, tags[262]!!.third)  // Separated (CMYK)
        assertEquals(4, tags[277]!!.third)
        assertEquals(1, tags[332]!!.third)  // InkSet CMYK
        val strips = tags[273]!!.second
        val offsets = IntArray(strips) { buf.getInt(tags[273]!!.third + it * 4) }
        val counts = IntArray(strips) { buf.getInt(tags[279]!!.third + it * 4) }
        val pixels = ByteArrayOutputStream()
        for (s in 0 until strips) {
            InflaterInputStream(ByteArrayInputStream(tiff, offsets[s], counts[s])).use { it.copyTo(pixels) }
        }
        assertArrayEquals(data, pixels.toByteArray())
        // Çözünürlük 300 dpi
        val xres = tags[282]!!.third
        assertEquals(300, buf.getInt(xres)); assertEquals(1, buf.getInt(xres + 4))
    }

    /** PDF'i çözümler: xref girdilerinin hepsi nesne başlarını gösterir; sayfa sayısı ve resim akışları. */
    private fun checkPdf(pdf: ByteArray, pages: Int): String {
        val text = String(pdf, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4\n"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        val startxref = Regex("startxref\n(\\d+)\n%%EOF").find(text)!!.groupValues[1].toInt()
        assertTrue(text.startsWith("xref\n", startxref))
        val header = Regex("xref\n0 (\\d+)\n").find(text, startxref)!!
        val size = header.groupValues[1].toInt()
        var pos = header.range.last + 1
        for (i in 0 until size) {
            val entry = text.substring(pos, pos + 20)
            if (i > 0) {
                val offset = entry.substring(0, 10).toInt()
                assertTrue("nesne $i", text.startsWith("$i 0 obj", offset))
            }
            pos += 20
        }
        assertEquals(pages, Regex("/Type /Page ").findAll(text).count())
        assertTrue(text.contains("/Count $pages"))
        return text
    }

    @Test
    fun cmykPdfIsValidAndCarriesDeviceCmykImages() {
        val out = ByteArrayOutputStream()
        val writer = MiniPdfWriter(out, "Çizgi roman")
        val sources = (1..3).map { i -> gradient(20 + i, 30) }
        sources.forEachIndexed { i, d -> writer.addPage(200f, 300f, ByteCmykSource(20 + i + 1, 30, d), 10f, 10f, 180f, 280f) }
        writer.finish()
        val pdf = out.toByteArray()
        val text = checkPdf(pdf, 3)
        assertEquals(3, Regex("/ColorSpace /DeviceCMYK").findAll(text).count())
        assertTrue(text.contains("/Title <FEFF"))
        // İlk resim akışı açılınca kaynak baytlar
        val m = Regex("/Width 21 /Height 30 /ColorSpace /DeviceCMYK /BitsPerComponent 8 /Filter /FlateDecode /Length (\\d+) >>\nstream\n").find(text)!!
        val len = m.groupValues[1].toInt()
        val start = m.range.last + 1
        val inflater = Inflater().apply { setInput(pdf, start, len) }
        val raw = ByteArray(21 * 30 * 4)
        assertEquals(raw.size, inflater.inflate(raw))
        assertArrayEquals(sources[0], raw)
    }

    @Test
    fun cbzHasNumberedPagesAndComicInfo() {
        val out = ByteArrayOutputStream()
        val cbz = CbzWriter(out, "Kahraman & <Dostlar>", "tr")
        repeat(3) { cbz.addPage(byteArrayOf(1, 2, 3, it.toByte()), 100, 150, "png") }
        cbz.finish()
        val names = ArrayList<String>()
        var info = ""
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                names += e.name
                val bytes = zip.readBytes()
                if (e.name == "ComicInfo.xml") info = String(bytes, Charsets.UTF_8)
                else { assertEquals(ZipEntry.STORED, e.method); assertEquals(4, bytes.size) }
            }
        }
        assertEquals(listOf("00001.png", "00002.png", "00003.png", "ComicInfo.xml"), names)
        assertTrue(info.contains("<PageCount>3</PageCount>"))
        assertTrue(info.contains("<Title>Kahraman &amp; &lt;Dostlar&gt;</Title>"))
        assertTrue(info.contains("<Page Image=\"0\" Type=\"FrontCover\""))
        assertEquals(3, Regex("<Page Image=").findAll(info).count())
        assertTrue(info.contains("<LanguageISO>tr</LanguageISO>"))
    }

    @Test
    fun softProofFlagsSaturatedRgbOnly() {
        val pixels = intArrayOf(0xFF0000FF.toInt(), 0xFF00FF00.toInt(), 0xFF808080.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFE0B090.toInt())
        val r = SoftProof.proof(pixels, CmykConverter())
        // Saf mavi ve yeşil basılamaz (soluklaşır), gri/beyaz/siyah ve ten rengi basılır
        assertEquals(2f / 6f, r.outOfGamut, 1e-4f)
        assertEquals(0xFF00E040.toInt(), r.warning[0])
        assertEquals(r.pixels[2], r.warning[2])
        // Prova açıklığı korur: gri gri kalır
        val g = r.pixels[2]
        assertTrue(abs(((g shr 16) and 0xFF) - 128) <= 3 && abs((g and 0xFF) - 128) <= 3)
    }

    @Test
    fun argbSourceConvertsRows() {
        val px = intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFF0000.toInt(), 0xFF00FFFF.toInt())
        val src = ArgbCmykSource.of(px, 2, 2, CmykConverter())
        val out = ByteArray(8)
        src.rows(1, 1, out)
        assertArrayEquals(byteArrayOf(0, -1, -1, 0, -1, 0, 0, 0), out)
    }
}

/**
 * JDK'nin JPEG okuyucusu (javax.imageio; Android derleme sınıf yolunda yok, testte JDK'de var) yansıma ile: CMYK
 * JPEG'in ham 4 kanalını okur.
 */
internal class JdkRaster private constructor(private val raster: Any) {
    private val rasterClass = Class.forName("java.awt.image.Raster")
    val width: Int get() = rasterClass.getMethod("getWidth").invoke(raster) as Int
    val height: Int get() = rasterClass.getMethod("getHeight").invoke(raster) as Int
    val bands: Int get() = rasterClass.getMethod("getNumBands").invoke(raster) as Int
    private val getSample = rasterClass.getMethod("getSample", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    fun sample(x: Int, y: Int, b: Int): Int = getSample.invoke(raster, x, y, b) as Int

    companion object {
        fun readJpeg(bytes: ByteArray): JdkRaster {
            val imageIO = Class.forName("javax.imageio.ImageIO")
            val readerClass = Class.forName("javax.imageio.ImageReader")
            val readers = imageIO.getMethod("getImageReadersByFormatName", String::class.java).invoke(null, "jpeg") as Iterator<*>
            val reader = readers.next()!!
            val stream = imageIO.getMethod("createImageInputStream", Any::class.java).invoke(null, ByteArrayInputStream(bytes))
            readerClass.getMethod("setInput", Any::class.java).invoke(reader, stream)
            val raster = readerClass.getMethod("readRaster", Int::class.javaPrimitiveType, Class.forName("javax.imageio.ImageReadParam")).invoke(reader, 0, null)
            return JdkRaster(raster!!)
        }
    }
}
