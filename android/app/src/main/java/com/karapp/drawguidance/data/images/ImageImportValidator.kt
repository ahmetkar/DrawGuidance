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
    /** v54 resmin içinde/sonunda kod ya da başka bir dosya (polyglot) var; eklenmez. */
    data object Suspicious : ImageIssue
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
 * - v54 yapı denetimi ([ImageSecurity]): parça/segment yapısı tutarlı olmalı; içinde ya da bitiş işaretinden sonra
 *   betik, HTML, çalıştırılabilir dosya ya da arşiv imzası bulunan resim reddedilir
 * - v54 dosya boyutu en fazla 10 MB (8 MB üstü uyarı)
 * - v54 boyut: kenarlar 16–8192 px, en fazla 64 MP; uzun kenarı 3000 px'ten (giriş düzeyi cihazda 2000 px) büyükse
 *   küçültülür (uyarı). Büyük resim önce tam boyda açılmaz (Android 9+ ImageDecoder hedef boyda, öncesinde inSampleSize)
 * - Detay: 256 px'e küçültülmüş gri tonlu görüntüde güçlü kenar içeren piksel oranı.
 *   %30 ve üstü reddedilir (fotoğraf/doku gibi çok detaylı), %15 ve üstü uyarı verilir.
 * Kaydedilecek veri: v54 her zaman yeniden kodlanır (özgün dosya saklanmaz: üst veri, konum bilgisi ve eklenmiş
 * veriler atılır); PNG, (Android 11+) kayıpsız WEBP ve JPEG kaynakta JPEG içinden en küçüğü.
 */
object ImageImportValidator {
    const val MAX_BYTES = 10L * 1024L * 1024L
    const val WARN_BYTES = 8L * 1024L * 1024L
    const val MIN_SIDE = 16
    const val MAX_SIDE = 8192
    const val MAX_PIXELS = 64_000_000L
    const val STORE_MAX_SIDE = 3000
    const val STORE_MAX_SIDE_LOW = 2000
    const val DETAIL_REJECT = 0.30f
    const val DETAIL_WARN = 0.15f
    private const val ANALYSIS_SIDE = 256
    private const val EDGE_THRESHOLD = 96

    /** Cihaz sınıfına göre saklanan resmin uzun kenarı (giriş düzeyinde bellek için daha küçük). */
    val storeMaxSide: Int
        get() = if (com.karapp.drawguidance.platform.DeviceCaps.tier == com.karapp.drawguidance.platform.DeviceCaps.Tier.LOW) STORE_MAX_SIDE_LOW else STORE_MAX_SIDE

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

    fun check(bytes: ByteArray, storeSide: Int = storeMaxSide): ImageCheckResult {
        val errors = ArrayList<ImageIssue>()
        val warnings = ArrayList<ImageIssue>()
        if (bytes.size > MAX_BYTES) errors += ImageIssue.TooLarge(bytes.size.toLong())
        val mime = sniffMime(bytes)
        if (mime == null) {
            errors += ImageIssue.UnsupportedFormat
            return ImageCheckResult(errors, warnings, null)
        }
        if (errors.isNotEmpty()) return ImageCheckResult(errors, warnings, null)
        // v54 yapı ve gömülü kod denetimi (çözmeden önce)
        when (ImageSecurity.inspect(bytes, mime)) {
            ImageSecurity.Verdict.MALFORMED -> return ImageCheckResult(listOf(ImageIssue.Unreadable), emptyList(), null)
            ImageSecurity.Verdict.SUSPICIOUS -> return ImageCheckResult(listOf(ImageIssue.Suspicious), emptyList(), null)
            ImageSecurity.Verdict.CLEAN -> Unit
        }
        if (bytes.size > WARN_BYTES) warnings += ImageIssue.NearSizeLimit(bytes.size.toLong())

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return ImageCheckResult(listOf(ImageIssue.Unreadable), emptyList(), null)
        if (w < MIN_SIDE || h < MIN_SIDE) errors += ImageIssue.TooSmall(w, h)
        if (w > MAX_SIDE || h > MAX_SIDE || w.toLong() * h > MAX_PIXELS) errors += ImageIssue.DimensionsTooLarge(w, h)
        if (errors.isNotEmpty()) return ImageCheckResult(errors, warnings, null)

        // Kaydedilecek boyut (uzun kenar en fazla storeSide)
        val longest = max(w, h)
        val factor = if (longest > storeSide) storeSide.toFloat() / longest else 1f
        val targetW = max(1, (w * factor).roundToInt())
        val targetH = max(1, (h * factor).roundToInt())
        val bitmap = decodeTo(bytes, w, h, targetW, targetH) ?: return ImageCheckResult(listOf(ImageIssue.Unreadable), emptyList(), null)
        if (factor < 1f) warnings += ImageIssue.WillDownscale(w, h, bitmap.width, bitmap.height)

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

        val (storedBytes, storedMime) = smallestEncoding(mime, bitmap)
        if (storedBytes.isEmpty()) {
            bitmap.recycle()
            return ImageCheckResult(listOf(ImageIssue.Unreadable), emptyList(), null)
        }
        return ImageCheckResult(
            errors, warnings,
            PreparedImage(storedBytes, storedMime, bitmap.width, bitmap.height, bitmap, bytes.size, detail),
        )
    }

    /**
     * v54 resmi hedef boyda çözer; büyük resim bellekte tam boyda açılmaz. Android 9+ ImageDecoder doğrudan hedef boyda
     * (yazılım bitmap, çizilebilir/okunabilir); öncesinde inSampleSize ile en fazla hedefin iki katı ve 24 MP, ardından
     * tam hedefe ölçeklenir.
     */
    fun decodeTo(bytes: ByteArray, w: Int, h: Int, targetW: Int, targetH: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val src = android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
                return android.graphics.ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                    if (targetW != w || targetH != h) decoder.setTargetSize(targetW, targetH)
                }
            }
        }
        var sample = 1
        while (w / (sample * 2) >= targetW && h / (sample * 2) >= targetH) sample *= 2
        // Eski cihazlarda ara bitmap sınırı: 24 MP üstüyse bir kat daha örneklenir
        while ((w.toLong() / sample) * (h.toLong() / sample) > 24_000_000L) sample *= 2
        val decoded = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull() ?: return null
        val tw = minOf(targetW, decoded.width).coerceAtLeast(1)
        val th = minOf(targetH, decoded.height).coerceAtLeast(1)
        return if (decoded.width != tw || decoded.height != th) {
            Bitmap.createScaledBitmap(decoded, tw, th, true).also { if (it !== decoded) decoded.recycle() }
        } else decoded
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

    /**
     * v54 her zaman yeniden kodlanır (özgün dosyanın üst verisi ve eklenmiş verileri saklanmaz): JPEG kaynakta JPEG %92;
     * ötekilerde PNG ve (Android 11+, 4 MP'ye kadar) kayıpsız WEBP içinden en küçüğü.
     */
    private fun smallestEncoding(mime: String, bitmap: Bitmap): Pair<ByteArray, String> {
        val candidates = ArrayList<Pair<ByteArray, String>>()
        // Fotoğraf (JPEG kaynak): kayıpsız biçimler hep daha büyük ve yavaş; yalnız JPEG
        if (mime == "image/jpeg" && !bitmap.hasAlpha()) {
            encode(bitmap, Bitmap.CompressFormat.JPEG, 92)?.let { return it to "image/jpeg" }
        }
        encode(bitmap, Bitmap.CompressFormat.PNG, 100)?.let { candidates += it to "image/png" }
        // Kayıpsız WEBP büyük resimde çok yavaş: 4 MP'ye kadar denenir
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && bitmap.width.toLong() * bitmap.height <= 4_000_000L) {
            encode(bitmap, Bitmap.CompressFormat.WEBP_LOSSLESS, 100)?.let { candidates += it to "image/webp" }
        }
        return candidates.filter { it.first.isNotEmpty() }.minByOrNull { it.first.size } ?: (ByteArray(0) to "image/png")
    }

    private fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray? = runCatching {
        ByteArrayOutputStream().use { out ->
            if (!bitmap.compress(format, quality, out)) return@runCatching null
            out.toByteArray()
        }
    }.getOrNull()
}
