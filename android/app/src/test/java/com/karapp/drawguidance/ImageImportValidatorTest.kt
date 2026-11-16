package com.karapp.drawguidance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.ImageImportValidator
import com.karapp.drawguidance.data.images.ImageIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.Random

/** Resim ekleme kontrolleri: biçim, v54 10 MB ve 8192 px, boyut sınırları, detay yoğunluğu, gömülü kod ve kayıt biçimi. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ImageImportValidatorTest {

    @org.junit.After fun tearDown() = org.koin.core.context.stopKoin()

    companion object {
        fun png(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        fun jpeg(bitmap: Bitmap, q: Int = 90): ByteArray = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, q, it); it.toByteArray() }

        /** Beyaz zemin üzerinde birkaç kalın çizgi ve daire: sade bir logo/çizim. */
        fun simpleDrawing(w: Int, h: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            val c = Canvas(this)
            c.drawColor(Color.WHITE)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 60, 120); strokeWidth = w / 40f; style = Paint.Style.STROKE }
            c.drawCircle(w / 2f, h / 2f, minOf(w, h) / 3f, p)
            c.drawLine(w * 0.1f, h * 0.9f, w * 0.9f, h * 0.1f, p)
        }

        fun noise(w: Int, h: Int, seed: Long = 7): Bitmap {
            val r = Random(seed)
            val pixels = IntArray(w * h) { Color.rgb(r.nextInt(256), r.nextInt(256), r.nextInt(256)) }
            return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        }

        /** Çok sayıda küçük şekil: orta-yüksek detay. */
        fun busy(w: Int, h: Int, count: Int, seed: Long = 3): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            val c = Canvas(this)
            c.drawColor(Color.WHITE)
            val r = Random(seed)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            repeat(count) {
                p.color = Color.rgb(r.nextInt(200), r.nextInt(200), r.nextInt(200))
                c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, 2f + r.nextFloat() * w / 60f, p)
            }
        }
    }

    @Test fun detailScoresAreOrdered() {
        val simple = ImageImportValidator.detailScore(simpleDrawing(512, 512))
        val light = ImageImportValidator.detailScore(busy(512, 512, 150))
        val heavy = ImageImportValidator.detailScore(busy(512, 512, 3000))
        val random = ImageImportValidator.detailScore(noise(256, 256))
        println("DETAIL simple=$simple light=$light heavy=$heavy noise=$random")
        assertTrue(simple < ImageImportValidator.DETAIL_WARN)
        assertTrue(random >= ImageImportValidator.DETAIL_REJECT)
        assertTrue(simple < light && light < heavy && heavy <= random + 0.05f)
    }

    @Test fun simpleDrawingIsAcceptedWithoutWarnings() {
        val bytes = png(simpleDrawing(800, 600))
        val result = ImageImportValidator.check(bytes)
        assertTrue(result.errors.toString(), result.accepted)
        assertEquals(emptyList<ImageIssue>(), result.warnings)
        val image = assertNotNullAndGet(result.image)
        assertEquals(800, image.width)
        assertEquals(600, image.height)
        // v54 her zaman yeniden kodlanır (özgün dosya saklanmaz); boyut orijinale yakın kalır
        assertTrue("saklanan veri orijinalden çok büyük olmamalı", image.bytes.size <= bytes.size * 11 / 10)
    }

    @Test fun tooLargeFileIsRejected() {
        val bytes = png(noise(2048, 2048))
        assertTrue(bytes.size > ImageImportValidator.MAX_BYTES)
        val result = ImageImportValidator.check(bytes)
        assertTrue(result.errors.any { it is ImageIssue.TooLarge })
        assertTrue(!result.accepted)
    }

    @Test fun tooDetailedImageIsRejected() {
        val bytes = png(noise(300, 300))
        assertTrue(bytes.size < ImageImportValidator.MAX_BYTES)
        val result = ImageImportValidator.check(bytes)
        assertTrue(result.errors.toString(), result.errors.any { it is ImageIssue.TooDetailed })
    }

    @Test fun unsupportedTinyAndHugeImagesAreRejected() {
        assertEquals(listOf(ImageIssue.UnsupportedFormat), ImageImportValidator.check("GIF89a not really".toByteArray()).errors)
        assertTrue(ImageImportValidator.check(png(simpleDrawing(10, 10))).errors.any { it is ImageIssue.TooSmall })
        assertTrue(ImageImportValidator.check(png(simpleDrawing(9000, 40))).errors.any { it is ImageIssue.DimensionsTooLarge })
        // v54 8192 px'e kadar kabul
        assertTrue(ImageImportValidator.check(png(simpleDrawing(4500, 40))).errors.none { it is ImageIssue.DimensionsTooLarge })
    }

    @Test fun largeResolutionIsDownscaledWithWarning() {
        val bytes = jpeg(simpleDrawing(6000, 3000))
        val result = ImageImportValidator.check(bytes, storeSide = 3000)
        assertTrue(result.errors.toString(), result.accepted)
        val downscale = result.warnings.filterIsInstance<ImageIssue.WillDownscale>().single()
        assertEquals(3000, downscale.targetWidth)
        assertEquals(1500, downscale.targetHeight)
        assertEquals(3000, result.image!!.width)
        assertEquals("image/jpeg", result.image!!.mimeType)
    }

    @Test fun imageWithEmbeddedCodeIsRejected() {
        val bytes = png(simpleDrawing(200, 200)) + "<script>alert(1)</script>".toByteArray()
        assertEquals(listOf(ImageIssue.Suspicious), ImageImportValidator.check(bytes).errors)
        val zip = jpeg(simpleDrawing(200, 200)) + byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 10, 0)
        assertEquals(listOf(ImageIssue.Suspicious), ImageImportValidator.check(zip).errors)
    }

    private fun <T> assertNotNullAndGet(value: T?): T { assertNotNull(value); return value!! }
}
