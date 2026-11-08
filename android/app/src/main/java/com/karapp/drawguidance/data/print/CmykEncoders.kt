package com.karapp.drawguidance.data.print

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * v40 CMYK satır kaynağı: resim bant bant üretilir (büyük resimlerde tüm CMYK verisi bellekte tutulmaz).
 * [rows] [count] satırı piksel başına 4 baytla (C, M, Y, K; 0 = mürekkep yok) [out]'a yazar.
 */
interface CmykSource {
    val width: Int
    val height: Int
    fun rows(y: Int, count: Int, out: ByteArray)
}

/** Bellekteki CMYK baytlarından kaynak (testler ve küçük resimler). */
class ByteCmykSource(override val width: Int, override val height: Int, private val data: ByteArray) : CmykSource {
    override fun rows(y: Int, count: Int, out: ByteArray) {
        System.arraycopy(data, y * width * 4, out, 0, count * width * 4)
    }
}

/** ARGB piksel okuyucusundan (ör. Bitmap.getPixels) dönüştürerek kaynak. */
class ArgbCmykSource(
    override val width: Int,
    override val height: Int,
    private val converter: CmykConverter,
    private val read: (y: Int, count: Int, dst: IntArray) -> Unit,
) : CmykSource {
    private var buffer = IntArray(0)
    override fun rows(y: Int, count: Int, out: ByteArray) {
        if (buffer.size < width * count) buffer = IntArray(width * count)
        read(y, count, buffer)
        converter.convertRow(buffer, 0, width * count, out, 0)
    }

    companion object {
        fun of(pixels: IntArray, width: Int, height: Int, converter: CmykConverter) = ArgbCmykSource(width, height, converter) { y, count, dst ->
            System.arraycopy(pixels, y * width, dst, 0, width * count)
        }
    }
}

/**
 * v40 saf Kotlin temel (baseline) JPEG kodlayıcı, 4 bileşenli CMYK: alt örnekleme yok, IJG kalite ölçekli standart
 * niceleme ve Huffman tabloları. Adobe APP14 işaretiyle (dönüşüm 0) yazılır; Photoshop geleneğine uygun olarak değerler
 * ters (255 − mürekkep) saklanır, Photoshop, GIMP, Acrobat ve libjpeg tabanlı okuyucular doğru açar.
 */
object CmykJpegEncoder {

    fun encode(source: CmykSource, out: OutputStream, quality: Int = 92) {
        val w = source.width
        val h = source.height
        require(w in 1..65535 && h in 1..65535)
        val q = quality.coerceIn(1, 100)
        val table = quantTable(q)
        val fdtbl = FloatArray(64)
        var k = 0
        for (row in 0 until 8) for (col in 0 until 8) {
            fdtbl[k] = (1.0 / (table[ZIGZAG[k]] * AASF[row] * AASF[col] * 8.0)).toFloat()
            k++
        }
        val bw = BitWriter(out)
        writeHeaders(out, w, h, table)
        val dcCodes = codes(DC_COUNTS, DC_VALUES)
        val acCodes = codes(AC_COUNTS, AC_VALUES)
        val dc = IntArray(4)
        val band = ByteArray(w * 8 * 4)
        val block = FloatArray(64)
        val du = IntArray(64)
        val quant = IntArray(64)
        var y = 0
        while (y < h) {
            val rows = minOf(8, h - y)
            source.rows(y, rows, band)
            var x = 0
            while (x < w) {
                for (c in 0 until 4) {
                    // 8×8 blok; kenarda son satır/sütun tekrarlanır
                    for (by in 0 until 8) {
                        val sy = minOf(by, rows - 1)
                        for (bx in 0 until 8) {
                            val sx = minOf(x + bx, w - 1)
                            val ink = band[(sy * w + sx) * 4 + c].toInt() and 0xFF
                            block[by * 8 + bx] = (255 - ink - 128).toFloat()
                        }
                    }
                    fdct(block, fdtbl, quant)
                    for (j in 0 until 64) du[ZIGZAG[j]] = quant[j]
                    dc[c] = encodeBlock(bw, du, dc[c], dcCodes, acCodes)
                }
                x += 8
            }
            y += 8
        }
        bw.flush()
        out.write(0xFF); out.write(0xD9)
    }

    fun encode(source: CmykSource, quality: Int = 92): ByteArray = ByteArrayOutputStream().also { encode(source, it, quality) }.toByteArray()

    private fun quantTable(quality: Int): IntArray {
        val sf = if (quality < 50) 5000 / quality else 200 - quality * 2
        val zz = IntArray(64)
        for (i in 0 until 64) {
            val t = ((YQT[i] * sf + 50) / 100).coerceIn(1, 255)
            zz[ZIGZAG[i]] = t
        }
        return zz
    }

    private fun writeHeaders(out: OutputStream, w: Int, h: Int, table: IntArray) {
        fun word(v: Int) { out.write((v shr 8) and 0xFF); out.write(v and 0xFF) }
        out.write(0xFF); out.write(0xD8)
        // APP14 Adobe: sürüm 100, bayraklar 0, dönüşüm 0 (CMYK, renk dönüşümü yok)
        out.write(0xFF); out.write(0xEE); word(14)
        out.write("Adobe".toByteArray(Charsets.US_ASCII)); word(100); word(0); word(0); out.write(0)
        // DQT (tek tablo, zikzak sırada)
        out.write(0xFF); out.write(0xDB); word(67); out.write(0)
        table.forEach { out.write(it) }
        // SOF0: 8 bit, 4 bileşen (1..4), örnekleme 1×1, tablo 0
        out.write(0xFF); out.write(0xC0); word(8 + 3 * 4); out.write(8); word(h); word(w); out.write(4)
        for (c in 1..4) { out.write(c); out.write(0x11); out.write(0) }
        // DHT: DC 0 ve AC 0
        out.write(0xFF); out.write(0xC4); word(2 + 1 + 16 + DC_VALUES.size + 1 + 16 + AC_VALUES.size)
        out.write(0x00); for (i in 1..16) out.write(DC_COUNTS[i]); DC_VALUES.forEach { out.write(it) }
        out.write(0x10); for (i in 1..16) out.write(AC_COUNTS[i]); AC_VALUES.forEach { out.write(it) }
        // SOS
        out.write(0xFF); out.write(0xDA); word(6 + 2 * 4); out.write(4)
        for (c in 1..4) { out.write(c); out.write(0x00) }
        out.write(0); out.write(63); out.write(0)
    }

    /** Huffman kodları: [değer] → (kod shl 8) | uzunluk. */
    private fun codes(counts: IntArray, values: IntArray): IntArray {
        val result = IntArray(256)
        var code = 0
        var pos = 0
        for (len in 1..16) {
            repeat(counts[len]) {
                result[values[pos]] = (code shl 8) or len
                pos++; code++
            }
            code = code shl 1
        }
        return result
    }

    private fun category(v: Int): Int {
        var a = if (v < 0) -v else v
        var n = 0
        while (a != 0) { n++; a = a shr 1 }
        return n
    }

    private fun bits(v: Int, cat: Int): Int = if (v >= 0) v else (v + (1 shl cat) - 1)

    private fun encodeBlock(bw: BitWriter, du: IntArray, prevDc: Int, dcCodes: IntArray, acCodes: IntArray): Int {
        val diff = du[0] - prevDc
        val dcCat = category(diff)
        bw.code(dcCodes[dcCat])
        if (dcCat > 0) bw.write(bits(diff, dcCat), dcCat)
        var end = 63
        while (end > 0 && du[end] == 0) end--
        if (end == 0) { bw.code(acCodes[0x00]); return du[0] }
        var i = 1
        while (i <= end) {
            val start = i
            while (du[i] == 0 && i <= end) i++
            var zeros = i - start
            while (zeros >= 16) { bw.code(acCodes[0xF0]); zeros -= 16 }
            val v = du[i]
            val cat = category(v)
            bw.code(acCodes[(zeros shl 4) + cat])
            bw.write(bits(v, cat), cat)
            i++
        }
        if (end != 63) bw.code(acCodes[0x00])
        return du[0]
    }

    /** AAN hızlı ileri DCT + niceleme ([fdtbl] doğal sırada). */
    private fun fdct(data: FloatArray, fdtbl: FloatArray, out: IntArray) {
        for (pass in 0..1) {
            val stride = if (pass == 0) 1 else 8
            val step = if (pass == 0) 8 else 1
            for (i in 0 until 8) {
                val o = i * step
                val d0 = data[o]; val d1 = data[o + stride]; val d2 = data[o + 2 * stride]; val d3 = data[o + 3 * stride]
                val d4 = data[o + 4 * stride]; val d5 = data[o + 5 * stride]; val d6 = data[o + 6 * stride]; val d7 = data[o + 7 * stride]
                val tmp0 = d0 + d7; val tmp7 = d0 - d7; val tmp1 = d1 + d6; val tmp6 = d1 - d6
                val tmp2 = d2 + d5; val tmp5 = d2 - d5; val tmp3 = d3 + d4; val tmp4 = d3 - d4
                var tmp10 = tmp0 + tmp3; val tmp13 = tmp0 - tmp3; var tmp11 = tmp1 + tmp2; var tmp12 = tmp1 - tmp2
                data[o] = tmp10 + tmp11
                data[o + 4 * stride] = tmp10 - tmp11
                val z1 = (tmp12 + tmp13) * 0.70710677f
                data[o + 2 * stride] = tmp13 + z1
                data[o + 6 * stride] = tmp13 - z1
                tmp10 = tmp4 + tmp5; tmp11 = tmp5 + tmp6; tmp12 = tmp6 + tmp7
                val z5 = (tmp10 - tmp12) * 0.38268343f
                val z2 = 0.5411961f * tmp10 + z5
                val z4 = 1.306563f * tmp12 + z5
                val z3 = tmp11 * 0.70710677f
                val z11 = tmp7 + z3; val z13 = tmp7 - z3
                data[o + 5 * stride] = z13 + z2
                data[o + 3 * stride] = z13 - z2
                data[o + stride] = z11 + z4
                data[o + 7 * stride] = z11 - z4
            }
        }
        for (i in 0 until 64) {
            val v = data[i] * fdtbl[i]
            out[i] = if (v > 0f) (v + 0.5f).toInt() else (v - 0.5f).toInt()
        }
    }

    /** Bit yazıcı (0xFF baytından sonra 0x00 doldurma). */
    private class BitWriter(private val out: OutputStream) {
        private var acc = 0
        private var n = 0
        fun code(packed: Int) = write(packed ushr 8, packed and 0xFF)
        fun write(value: Int, length: Int) {
            for (i in length - 1 downTo 0) {
                acc = (acc shl 1) or ((value ushr i) and 1)
                n++
                if (n == 8) emit()
            }
        }
        private fun emit() {
            out.write(acc)
            if (acc == 0xFF) out.write(0)
            acc = 0; n = 0
        }
        fun flush() { while (n != 0) { acc = (acc shl 1) or 1; n++; if (n == 8) emit() } }
    }

    private val AASF = doubleArrayOf(1.0, 1.387039845, 1.306562965, 1.175875602, 1.0, 0.785694958, 0.541196100, 0.275899379)

    /** Doğal sıra → zikzak sırası. */
    val ZIGZAG = intArrayOf(
        0, 1, 5, 6, 14, 15, 27, 28,
        2, 4, 7, 13, 16, 26, 29, 42,
        3, 8, 12, 17, 25, 30, 41, 43,
        9, 11, 18, 24, 31, 40, 44, 53,
        10, 19, 23, 32, 39, 45, 52, 54,
        20, 22, 33, 38, 46, 51, 55, 60,
        21, 34, 37, 47, 50, 56, 59, 61,
        35, 36, 48, 49, 57, 58, 62, 63,
    )

    private val YQT = intArrayOf(
        16, 11, 10, 16, 24, 40, 51, 61,
        12, 12, 14, 19, 26, 58, 60, 55,
        14, 13, 16, 24, 40, 57, 69, 56,
        14, 17, 22, 29, 51, 87, 80, 62,
        18, 22, 37, 56, 68, 109, 103, 77,
        24, 35, 55, 64, 81, 104, 113, 92,
        49, 64, 78, 87, 103, 121, 120, 101,
        72, 92, 95, 98, 112, 100, 103, 99,
    )

    private val DC_COUNTS = intArrayOf(0, 0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0)
    private val DC_VALUES = IntArray(12) { it }
    private val AC_COUNTS = intArrayOf(0, 0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d)
    private val AC_VALUES = intArrayOf(
        0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
        0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08, 0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0,
        0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0a, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
        0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
        0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
        0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
        0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7,
        0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5,
        0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2,
        0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
        0xf9, 0xfa,
    )
}

/**
 * v40 CMYK TIFF (Photometric = Separated, InkSet = CMYK, 8 bit, Adobe Deflate sıkıştırma, 300 dpi): baskı evlerinin
 * en yaygın kabul ettiği kayıpsız biçim.
 */
object CmykTiffEncoder {
    const val ROWS_PER_STRIP = 32
    const val DPI = 300

    fun encode(source: CmykSource, out: OutputStream) {
        val w = source.width
        val h = source.height
        val strips = (h + ROWS_PER_STRIP - 1) / ROWS_PER_STRIP
        val counter = CountingStream(out)
        // Başlık: "II", 42, IFD adresi sonra yazılır → önce verileri tampona değil doğrudan akışa yazmak için IFD sona konur;
        // adresi bilinmediğinden şeritler önce bellekte sıkıştırılır (şerit başına) ve uzunlukları toplanır.
        val compressed = ArrayList<ByteArray>(strips)
        val band = ByteArray(w * ROWS_PER_STRIP * 4)
        for (s in 0 until strips) {
            val y = s * ROWS_PER_STRIP
            val rows = minOf(ROWS_PER_STRIP, h - y)
            source.rows(y, rows, band)
            val bos = ByteArrayOutputStream()
            DeflaterOutputStream(bos, Deflater(6)).use { it.write(band, 0, rows * w * 4) }
            compressed += bos.toByteArray()
        }
        val dataSize = compressed.sumOf { it.size.toLong() }
        var ifdOffset = 8L + dataSize
        if (ifdOffset % 2 == 1L) ifdOffset++
        val le = LittleEndian(counter)
        counter.write('I'.code); counter.write('I'.code); le.short(42); le.int(ifdOffset)
        val offsets = LongArray(strips)
        var pos = 8L
        compressed.forEachIndexed { i, bytes -> offsets[i] = pos; counter.write(bytes); pos += bytes.size }
        if (counter.count % 2 == 1L) counter.write(0)

        val software = "DrawGuidance\u0000".toByteArray(Charsets.US_ASCII)
        data class Tag(val id: Int, val type: Int, val count: Int, val inline: Long?, val data: ByteArray?)
        fun shorts(vararg v: Int) = ByteArrayOutputStream().also { b -> val l = LittleEndian(b); v.forEach { l.short(it) } }.toByteArray()
        fun longs(v: LongArray) = ByteArrayOutputStream().also { b -> val l = LittleEndian(b); v.forEach { l.int(it) } }.toByteArray()
        fun rational(n: Long, d: Long) = longs(longArrayOf(n, d))
        val tags = listOf(
            Tag(256, LONG, 1, w.toLong(), null),
            Tag(257, LONG, 1, h.toLong(), null),
            Tag(258, SHORT, 4, null, shorts(8, 8, 8, 8)),
            Tag(259, SHORT, 1, 8, null),
            Tag(262, SHORT, 1, 5, null),
            if (strips == 1) Tag(273, LONG, 1, offsets[0], null) else Tag(273, LONG, strips, null, longs(offsets)),
            Tag(277, SHORT, 1, 4, null),
            Tag(278, LONG, 1, ROWS_PER_STRIP.toLong(), null),
            if (strips == 1) Tag(279, LONG, 1, compressed[0].size.toLong(), null)
            else Tag(279, LONG, strips, null, longs(LongArray(strips) { compressed[it].size.toLong() })),
            Tag(282, RATIONAL, 1, null, rational(DPI.toLong(), 1)),
            Tag(283, RATIONAL, 1, null, rational(DPI.toLong(), 1)),
            Tag(284, SHORT, 1, 1, null),
            Tag(296, SHORT, 1, 2, null),
            Tag(305, ASCII, software.size, null, software),
            Tag(332, SHORT, 1, 1, null),
        )
        // IFD: giriş sayısı + 12 bayt × giriş + sonraki IFD (0); taşan değerler hemen arkasında
        var extra = ifdOffset + 2 + 12L * tags.size + 4
        val extras = ArrayList<ByteArray>()
        le.short(tags.size)
        tags.forEach { t ->
            le.short(t.id); le.short(t.type); le.int(t.count.toLong())
            if (t.data == null) {
                if (t.type == SHORT) { le.short(t.inline!!.toInt()); le.short(0) } else le.int(t.inline!!)
            } else if (t.data.size <= 4) {
                counter.write(t.data); repeat(4 - t.data.size) { counter.write(0) }
            } else {
                le.int(extra)
                extras += t.data
                extra += t.data.size + (t.data.size % 2)
            }
        }
        le.int(0)
        extras.forEach { counter.write(it); if (it.size % 2 == 1) counter.write(0) }
        counter.flush()
    }

    fun encode(source: CmykSource): ByteArray = ByteArrayOutputStream().also { encode(source, it) }.toByteArray()

    private const val SHORT = 3
    private const val LONG = 4
    private const val RATIONAL = 5
    private const val ASCII = 2

    private class LittleEndian(private val out: OutputStream) {
        fun short(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        fun int(v: Long) { for (i in 0 until 4) out.write(((v shr (8 * i)) and 0xFF).toInt()) }
    }
}

/** Yazılan bayt sayısını tutan akış. */
class CountingStream(private val out: OutputStream) : OutputStream() {
    var count = 0L
        private set
    override fun write(b: Int) { out.write(b); count++ }
    override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
    override fun flush() = out.flush()
    override fun close() = out.close()
}
