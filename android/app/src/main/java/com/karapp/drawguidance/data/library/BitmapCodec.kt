package com.karapp.drawguidance.data.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.io.ByteArrayOutputStream

/**
 * v23 üretilen resimlerin (arka plan resimleri, 3B dokuları, 3B küçük resimleri) sıkıştırılması: saydam pikseli
 * olmayanlar kayıplı WebP (en küçük), saydamlar PNG (kayıpsız, eski Android sürümlerinde de doğru açılır).
 */
object BitmapCodec {

    fun bitmap(px: IntArray, w: Int, h: Int): Bitmap = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)

    fun encode(px: IntArray, w: Int, h: Int, opaque: Boolean, quality: Int = 88): ByteArray {
        val bmp = bitmap(px, w, h)
        try {
            return encode(bmp, opaque, quality)
        } finally {
            bmp.recycle()
        }
    }

    @Suppress("DEPRECATION")
    fun encode(bmp: Bitmap, opaque: Boolean, quality: Int = 88): ByteArray {
        val out = ByteArrayOutputStream(bmp.width * bmp.height / 4)
        val format = when {
            !opaque -> Bitmap.CompressFormat.PNG
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> Bitmap.CompressFormat.WEBP
        }
        bmp.compress(format, quality, out)
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): Bitmap? = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

    /** Çözülmüş resmin pikselleri (ARGB). */
    fun pixels(bytes: ByteArray): Triple<Int, Int, IntArray>? {
        val bmp = decode(bytes) ?: return null
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val r = Triple(bmp.width, bmp.height, px)
        bmp.recycle()
        return r
    }
}
