package com.karapp.drawguidance.content.model3d

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * v24 saf Kotlin PNG yazıcı (glTF dışa aktarmada dokuları gömmek için; Android'e bağımlı değil): RGBA 8 bit, her
 * satırda "Sub" süzgeci, DEFLATE ile sıkıştırma.
 */
object PngWriter {

    fun encode(t: Texture): ByteArray = encode(t.px, t.w, t.h)

    /** [argb] pikseller (önçarpımsız ARGB) → PNG baytları. */
    fun encode(argb: IntArray, w: Int, h: Int): ByteArray {
        require(argb.size == w * h)
        val raw = ByteArray(h * (1 + w * 4))
        var o = 0
        for (y in 0 until h) {
            raw[o++] = 1 // Sub süzgeci
            var pr = 0; var pg = 0; var pb = 0; var pa = 0
            for (x in 0 until w) {
                val c = argb[y * w + x]
                val a = c ushr 24 and 0xFF; val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF
                raw[o++] = (r - pr).toByte(); raw[o++] = (g - pg).toByte(); raw[o++] = (b - pb).toByte(); raw[o++] = (a - pa).toByte()
                pr = r; pg = g; pb = b; pa = a
            }
        }
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        val idat = try {
            deflater.setInput(raw); deflater.finish()
            val bos = ByteArrayOutputStream(raw.size / 3 + 64)
            val buf = ByteArray(16384)
            while (!deflater.finished()) bos.write(buf, 0, deflater.deflate(buf))
            bos.toByteArray()
        } finally { deflater.end() }
        val out = ByteArrayOutputStream(idat.size + 64)
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A))
        val ihdr = ByteArray(13)
        putInt(ihdr, 0, w); putInt(ihdr, 4, h)
        ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0
        chunk(out, "IHDR", ihdr)
        chunk(out, "IDAT", idat)
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val len = ByteArray(4); putInt(len, 0, data.size)
        out.write(len)
        val t = type.toByteArray(Charsets.US_ASCII)
        out.write(t); out.write(data)
        val crc = CRC32(); crc.update(t); crc.update(data)
        val c = ByteArray(4); putInt(c, 0, crc.value.toInt())
        out.write(c)
    }

    private fun putInt(b: ByteArray, o: Int, v: Int) {
        b[o] = (v ushr 24).toByte(); b[o + 1] = (v ushr 16).toByte(); b[o + 2] = (v ushr 8).toByte(); b[o + 3] = v.toByte()
    }
}
