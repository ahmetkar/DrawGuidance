package com.karapp.drawguidance.content.model3d

import java.io.ByteArrayOutputStream

/**
 * v56 aşama 13 dönen video için saf Kotlin GIF89a yazıcısı (Android'e bağımlı değil). Kareler ARGB piksellerdir;
 * renkler sabit 6×7×6 paletine (252 renk) sıralı titreme ile indirgenir, LZW ile sıkıştırılır. Sonsuz döngü
 * (NETSCAPE2.0) yazılır.
 */
class GifWriter(val width: Int, val height: Int, private val delayCs: Int = 4) {

    private val out = ByteArrayOutputStream()
    private var frames = 0

    init {
        require(width in 1..4096 && height in 1..4096)
        out.write("GIF89a".toByteArray())
        short(width); short(height)
        out.write(0xF7) // genel renk tablosu, 8 bit, 256 giriş
        out.write(0); out.write(0)
        for (i in 0 until 256) {
            val c = if (i < 252) PALETTE[i] else 0
            out.write((c shr 16) and 0xFF); out.write((c shr 8) and 0xFF); out.write(c and 0xFF)
        }
        // NETSCAPE2.0 sonsuz döngü
        out.write(0x21); out.write(0xFF); out.write(11); out.write("NETSCAPE2.0".toByteArray())
        out.write(3); out.write(1); short(0); out.write(0)
    }

    val frameCount: Int get() = frames

    /** Bir kare ekler ([argb] boyu width·height). */
    fun addFrame(argb: IntArray) {
        require(argb.size == width * height)
        // Grafik denetimi: gecikme
        out.write(0x21); out.write(0xF9); out.write(4); out.write(0); short(delayCs); out.write(0); out.write(0)
        // Görüntü tanımı
        out.write(0x2C); short(0); short(0); short(width); short(height); out.write(0)
        val idx = ByteArray(argb.size)
        for (y in 0 until height) for (x in 0 until width) {
            val i = y * width + x
            idx[i] = index(argb[i], BAYER[(y and 3) * 4 + (x and 3)]).toByte()
        }
        out.write(8)
        lzw(idx)
        frames++
    }

    /** Dosyayı bitirir ve baytları döndürür. */
    fun finish(): ByteArray {
        out.write(0x3B)
        return out.toByteArray()
    }

    private fun short(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }

    private fun lzw(data: ByteArray) {
        val minCode = 8
        val clear = 1 shl minCode
        val eoi = clear + 1
        val block = ByteArrayOutputStream()
        var bitBuf = 0; var bitCount = 0
        var codeSize = minCode + 1
        val sub = ByteArray(255); var subLen = 0
        fun emitByte(b: Int) {
            sub[subLen++] = b.toByte()
            if (subLen == 255) { block.write(255); block.write(sub, 0, 255); subLen = 0 }
        }
        fun emit(code: Int) {
            bitBuf = bitBuf or (code shl bitCount); bitCount += codeSize
            while (bitCount >= 8) { emitByte(bitBuf and 0xFF); bitBuf = bitBuf ushr 8; bitCount -= 8 }
        }
        val dict = HashMap<Int, Int>(8192)
        var next = eoi + 1
        emit(clear)
        var prefix = data[0].toInt() and 0xFF
        for (i in 1 until data.size) {
            val k = data[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val found = dict[key]
            if (found != null) { prefix = found; continue }
            emit(prefix)
            if (next < 4096) {
                dict[key] = next++
                if (next > (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                emit(clear); dict.clear(); next = eoi + 1; codeSize = minCode + 1
            }
            prefix = k
        }
        emit(prefix)
        emit(eoi)
        if (bitCount > 0) emitByte(bitBuf and 0xFF)
        if (subLen > 0) { block.write(subLen); block.write(sub, 0, subLen) }
        block.write(0)
        out.write(block.toByteArray())
    }

    companion object {
        private val LEVELS_R = 6; private val LEVELS_G = 7; private val LEVELS_B = 6

        /** 6×7×6 sabit palet (RGB). */
        val PALETTE: IntArray = IntArray(LEVELS_R * LEVELS_G * LEVELS_B).also { p ->
            var i = 0
            for (r in 0 until LEVELS_R) for (g in 0 until LEVELS_G) for (b in 0 until LEVELS_B) {
                p[i++] = ((r * 255 / (LEVELS_R - 1)) shl 16) or ((g * 255 / (LEVELS_G - 1)) shl 8) or (b * 255 / (LEVELS_B - 1))
            }
        }

        private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

        /** ARGB rengin palet sırası (yarı saydam pikseller beyaza karıştırılır); [t] titreme eşiği 0..15. */
        fun index(argb: Int, t: Int = 8): Int {
            val a = (argb ushr 24) and 0xFF
            var r = (argb shr 16) and 0xFF; var g = (argb shr 8) and 0xFF; var b = argb and 0xFF
            if (a < 255) { r = (r * a + 255 * (255 - a)) / 255; g = (g * a + 255 * (255 - a)) / 255; b = (b * a + 255 * (255 - a)) / 255 }
            val d = (t + 0.5f) / 16f
            fun q(v: Int, levels: Int): Int = (v * (levels - 1) / 255f + d - 0.5f).let { Math.round(it).coerceIn(0, levels - 1) }
            return (q(r, LEVELS_R) * LEVELS_G + q(g, LEVELS_G)) * LEVELS_B + q(b, LEVELS_B)
        }
    }
}

/**
 * v56 aşama 13 dönen video kamera yolu: modelin çevresinde tam tur ([degrees]); [speed] 0.5×/1×/2× tur süresini
 * değiştirir (kare sayısı = saniye × fps / hız).
 */
object OrbitTrack {

    /** Tam turun 1× hızdaki süresi (saniye). */
    const val SECONDS = 6f

    fun frameCount(fps: Int, speed: Float): Int = (SECONDS * fps / speed.coerceIn(0.25f, 4f)).toInt().coerceAtLeast(2)

    fun cameras(base: Camera3, frames: Int, degrees: Float = 360f): List<Camera3> =
        List(frames) { i -> base.copy(yaw = base.yaw + degrees * i / frames) }
}
