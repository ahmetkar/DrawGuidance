package com.karapp.drawguidance.data.images

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Eklenmeye hazır, kaydedilecek en küçük biçime getirilmiş resim. */
class PreparedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val bitmap: Bitmap,
    val originalBytes: Int,
    val detailScore: Float,
)

/** Resim kontrolünde bulunan sorun; hata (eklenmez) ya da uyarı (onayla eklenir). */
sealed interface ImageIssue {
    data class TooLarge(val bytes: Long) : ImageIssue
    data object UnsupportedFormat : ImageIssue
    data object Unreadable : ImageIssue
    data class TooSmall(val width: Int, val height: Int) : ImageIssue
    data class DimensionsTooLarge(val width: Int, val height: Int) : ImageIssue
    data class TooDetailed(val percent: Int) : ImageIssue
    // Uyarılar
    data class Detailed(val percent: Int) : ImageIssue
    data class WillDownscale(val width: Int, val height: Int, val targetWidth: Int, val targetHeight: Int) : ImageIssue
    data class NearSizeLimit(val bytes: Long) : ImageIssue
}

class ImageCheckResult(
    val errors: List<ImageIssue>,
    val warnings: List<ImageIssue>,
    val image: PreparedImage?,
) {
    val accepted: Boolean get() = errors.isEmpty() && image != null
}

/**
 * Tuvale eklenecek resmin sıkı kontrolleri:
 * - Biçim: yalnızca PNG, JPEG, WEBP (dosya imzasından anlaşılır; uzantıya güvenilmez)
 * - Dosya boyutu en fazla 1 MB (0,8 MB üstü uyarı)
 * - Boyut: kenarlar 16–4096 px, en fazla 16 MP; uzun kenarı 1600 px'ten büyükse küçültülür (uyarı)
 * - Detay: 256 px'e küçültülmüş gri tonlu görüntüde güçlü kenar içeren piksel oranı.
 *   %30 ve üstü reddedilir (fotoğraf/doku gibi çok detaylı), %15 ve üstü uyarı verilir.
 * Kaydedilecek veri: orijinal, PNG ve (Android 11+) kayıpsız WEBP içinden en küçüğü.
 */
object ImageImportValidator {
    const val MAX_BYTES = 1024L * 1024L
    const val WARN_BYTES = 800L * 1024L
    const val MIN_SIDE = 16
    const val MAX_SIDE = 4096
    const val MAX_PIXELS = 16_000_000L
    const val STORE_MAX_SIDE = 1600
    const val DETAIL_REJECT = 0.30f
    const val DETAIL_WARN = 0.15f
    private const val ANALYSIS_SIDE = 256
    private const val EDGE_THRESHOLD = 96

    fun check(resolver: ContentResolver, uri: Uri): ImageCheckResult {
        val declaredSize = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
            }
        }.getOrNull()
        if (declaredSize != null && declaredSize > MAX_BYTES) {
            return ImageCheckResult(listOf(ImageIssue.TooLarge(declaredSize)), emptyList(), null)
        }
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) return ImageCheckResult(listOf(ImageIssue.TooLarge(total + input.available())), emptyList(), null)
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
        }.getOrNull() ?: return ImageCheckResult(listOf(ImageIssue.Unreadable), emptyList(), null)
        return check(bytes)
    }

    fun check(bytes: ByteArray): ImageCheckResult {
        val errors = ArrayList<ImageIssue>()
        val warnings = ArrayList<ImageIssue>()
        if (bytes.size > MAX_BYTES) errors += ImageIssue.TooLarge(bytes.size.toLong())
        val mime = sniffMime(bytes)
        if (mime == null) {
            errors += ImageIssue.UnsupportedFormat
            return ImageCheckResult(errors, warnings, null)
        }
        if (errors.isNotEmpty()) return ImageCheckResult(errors, warnings, null)
        if (bytes.size > WARN_BYTES) warnings += ImageIssue.NearSizeLimit(bytes.size.toLong())

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return ImageCheckResult(listOf(ImageIssue.Unreadable), emptyList(), null)
        if (w < MIN_SIDE || h < MIN_SIDE) errors += ImageIssue.TooSmall(w, h)
        if (w > MAX_SIDE || h > MAX_SIDE || w.toLong() * h > MAX_PIXELS) errors += ImageIssue.DimensionsTooLarge(w, h)
        if (errors.isNotEmpty()) return ImageCheckResult(errors, warnings, null)

        // Kaydedilecek boyut (uzun kenar en fazla STORE_MAX_SIDE)
        val longest = max(w, h)
        val factor = if (longest > STORE_MAX_SIDE) STORE_MAX_SIDE.toFloat() / longest else 1f
        val targetW = max(1, (w * factor).roundToInt())
        val targetH = max(1, (h * factor).roundToInt())
        var sample = 1
        while (w / (sample * 2) >= targetW && h / (sample * 2) >= targetH) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return ImageCheckResult(listOf(ImageIssue.Unreadable), emptyList(), null)
        val bitmap = if (decoded.width != targetW || decoded.height != targetH) {
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also { if (it !== decoded) decoded.recycle() }
        } else decoded
        if (factor < 1f) warnings += ImageIssue.WillDownscale(w, h, targetW, targetH)

        val detail = detailScore(bitmap)
        val percent = (detail * 100).roundToInt()
        when {
            detail >= DETAIL_REJECT -> errors += ImageIssue.TooDetailed(percent)
            detail >= DETAIL_WARN -> warnings += ImageIssue.Detailed(percent)
        }
        if (errors.isNotEmpty()) {
            bitmap.recycle()
            return ImageCheckResult(errors, warnings, null)
        }

        val (storedBytes, storedMime) = smallestEncoding(bytes, mime, bitmap, keepOriginal = factor == 1f)
        return ImageCheckResult(
            errors, warnings,
            PreparedImage(storedBytes, storedMime, bitmap.width, bitmap.height, bitmap, bytes.size, detail),
        )
    }

    /** Dosya imzası: PNG, JPEG, WEBP; diğer her şey desteklenmez. */
    fun sniffMime(b: ByteArray): String? = when {
        b.size >= 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte() -> "image/png"
        b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte() -> "image/jpeg"
        b.size >= 12 && String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> null
    }

    /**
     * Detay puanı (0..1): resim 256 px'e küçültülür, gri tona çevrilir ve Sobel kenar
     * büyüklüğü eşiği aşan piksellerin oranı hesaplanır. Saydam pikseller beyaz sayılır.
     */
    fun detailScore(source: Bitmap): Float {
        val scale = ANALYSIS_SIDE.toFloat() / max(source.width, source.height)
        val w = max(3, if (scale < 1f) (source.width * scale).roundToInt() else source.width)
        val h = max(3, if (scale < 1f) (source.height * scale).roundToInt() else source.height)
        val small = if (w != source.width || h != source.height) Bitmap.createScaledBitmap(source, w, h, true) else source
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== source) small.recycle()
        val gray = IntArray(w * h) { i ->
            val p = pixels[i]
            val a = Color.alpha(p)
            val l = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
            (l * a + 255 * (255 - a)) / 255
        }
        var strong = 0
        var total = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val gx = -gray[i - w - 1] - 2 * gray[i - 1] - gray[i + w - 1] + gray[i - w + 1] + 2 * gray[i + 1] + gray[i + w + 1]
                val gy = -gray[i - w - 1] - 2 * gray[i - w] - gray[i - w + 1] + gray[i + w - 1] + 2 * gray[i + w] + gray[i + w + 1]
                val magnitude = sqrt((gx * gx + gy * gy).toDouble())
                if (magnitude > EDGE_THRESHOLD) strong++
                total++
            }
        }
        return if (total == 0) 0f else strong.toFloat() / total
    }

    private fun smallestEncoding(original: ByteArray, mime: String, bitmap: Bitmap, keepOriginal: Boolean): Pair<ByteArray, String> {
        val candidates = ArrayList<Pair<ByteArray, String>>()
        if (keepOriginal) candidates += original to mime
        encode(bitmap, Bitmap.CompressFormat.PNG, 100)?.let { candidates += it to "image/png" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            encode(bitmap, Bitmap.CompressFormat.WEBP_LOSSLESS, 100)?.let { candidates += it to "image/webp" }
        }
        // Küçültülen JPEG fotoğraflar için JPEG de aday (kaynak zaten kayıplı)
        if (!keepOriginal && mime == "image/jpeg") encode(bitmap, Bitmap.CompressFormat.JPEG, 92)?.let { candidates += it to "image/jpeg" }
        return candidates.filter { it.first.isNotEmpty() }.minByOrNull { it.first.size } ?: (original to mime)
    }

    private fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray? = runCatching {
        ByteArrayOutputStream().use { out ->
            if (!bitmap.compress(format, quality, out)) return@runCatching null
            out.toByteArray()
        }
    }.getOrNull()
}
