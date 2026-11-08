package com.karapp.drawguidance.data.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Seçilen resmi okuyup [ImageImportValidator] kontrollerinden geçirir. */
class ImageImporter(private val context: Context) {
    suspend fun check(uri: Uri): ImageCheckResult = withContext(Dispatchers.IO) {
        ImageImportValidator.check(context.contentResolver, uri)
    }

    /**
     * v29 Resimlerim'deki (uygulamanın kaydettiği) resim: boyut ve ayrıntı sınırı uygulanmaz, yalnız uzun kenarı
     * [ImageImportValidator.STORE_MAX_SIDE]'a küçültülür. Okunamazsa null.
     */
    suspend fun prepareSaved(uri: Uri): PreparedImage? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val w = bounds.outWidth; val h = bounds.outHeight
            if (w <= 0 || h <= 0) return@runCatching null
            val side = ImageImportValidator.STORE_MAX_SIDE
            val factor = if (max(w, h) > side) side.toFloat() / max(w, h) else 1f
            val tw = max(1, (w * factor).roundToInt()); val th = max(1, (h * factor).roundToInt())
            var sample = 1
            while (w / (sample * 2) >= tw && h / (sample * 2) >= th) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@runCatching null
            val bitmap = if (decoded.width != tw || decoded.height != th) {
                Bitmap.createScaledBitmap(decoded, tw, th, true).also { if (it !== decoded) decoded.recycle() }
            } else decoded
            val png = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
            // Saydamlığı olmayan büyük resim JPEG olarak daha az yer kaplar
            val (stored, mime) = if (!bitmap.hasAlpha() && png.size > 600 * 1024) {
                ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it); it.toByteArray() } to "image/jpeg"
            } else png to "image/png"
            PreparedImage(stored, mime, bitmap.width, bitmap.height, bitmap, bytes.size, 0f)
        }.getOrNull()
    }
}
