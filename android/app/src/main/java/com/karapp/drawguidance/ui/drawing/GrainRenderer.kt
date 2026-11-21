package com.karapp.drawguidance.ui.drawing

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidBitmap
import com.karapp.drawguidance.data.images.CanvasImageCache
import com.karapp.drawguidance.data.model.GrainSpec
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.V56Textures
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * v56 aşama 5 gren ve resimden fırça çizimi. Gren: iz ara katmanda çizilir, sonra hazır doku karosu (alfa =
 * [V56Textures.alpha]) `DstIn` ile çarpılır; doku "Kayar"da tuvale, değilse izin ilk noktasına sabittir.
 * Resim ucu: varlıktaki uç resmi (beyaz + alfa) iz boyunca aralıklı damgalanır ve fırça rengiyle boyanır.
 */
object GrainRenderer {

    private val tiles = HashMap<String, Bitmap>()

    /** Doku karosu: beyaz + alfa; güç 10 düzeyde önbellekte. */
    @Synchronized
    fun tile(id: String, strength: Float): Bitmap {
        val level = (strength.coerceIn(0f, 1f) * 10).roundToInt()
        return tiles.getOrPut("$id-$level") {
            val n = V56Textures.SIZE
            val values = V56Textures.tile(id)
            val px = IntArray(n * n) { i ->
                val a = (V56Textures.alpha(values[i], level / 10f) * 255f).roundToInt().coerceIn(0, 255)
                (a shl 24) or 0xFFFFFF
            }
            Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        }
    }

    /** Doku önizlemesi (seçici düğmeleri): koyu = boya tutan yer. */
    fun preview(id: String): Bitmap = tile(id, 1f)

    fun shaderMatrix(spec: GrainSpec, firstX: Float, firstY: Float): Matrix {
        val (ax, ay) = V56Textures.anchor(spec, firstX, firstY)
        val s = V56Textures.scaleOf(spec)
        return Matrix().apply { setScale(s, s); postTranslate(ax, ay) }
    }

    /** [block] ile çizilen izi grenle çarpar. */
    inline fun withGrain(canvas: Canvas, spec: GrainSpec, firstX: Float, firstY: Float, block: () -> Unit) {
        val bounds = RectF(-1e6f, -1e6f, 1e6f, 1e6f)
        val save = canvas.saveLayer(bounds, null)
        block()
        val shader = BitmapShader(tile(spec.textureId, spec.strength), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        shader.setLocalMatrix(shaderMatrix(spec, firstX, firstY))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader; xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        canvas.drawPaint(paint)
        canvas.restoreToCount(save)
    }

    fun grainOf(stroke: Stroke): GrainSpec? = stroke.params?.grain?.takeIf { stroke.points.isNotEmpty() }

    /**
     * Resimden fırça: uç resmi her [step] px'de bir damgalanır; genişlik fırça boyutu (basınçla), yükseklik resmin
     * oranında; damga iz yönüne döner. Uç henüz yüklenmediyse false (çağıran fırçanın kendi ucuyla çizer).
     */
    fun drawStamps(canvas: Canvas, stroke: Stroke, offsetX: Float, offsetY: Float, color: Int): Boolean {
        val id = stroke.params?.stampAssetId ?: return false
        val tip = CanvasImageCache.get(id)?.asAndroidBitmap() ?: return false
        val pts = stroke.points
        if (pts.isEmpty()) return true
        val aspect = tip.height.toFloat() / max(1, tip.width)
        val spacing = (stroke.params?.spacing ?: 0.25f).coerceIn(0.02f, 3f)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(color or (0xFF shl 24), PorterDuff.Mode.SRC_IN)
            alpha = (stroke.opacity.coerceIn(0f, 1f) * stroke.flow.coerceIn(0.05f, 1f) * 255f).roundToInt().coerceIn(1, 255)
        }
        val m = Matrix()
        fun stamp(x: Float, y: Float, pressure: Float, angle: Float) {
            val w = max(1f, stroke.sizePx * pressure.coerceIn(0.2f, 1.5f))
            m.reset()
            m.setScale(w / tip.width, w * aspect / tip.height)
            m.postTranslate(-w / 2f, -w * aspect / 2f)
            m.postRotate(angle)
            m.postTranslate(x + offsetX, y + offsetY)
            canvas.drawBitmap(tip, m, paint)
        }
        stamp(pts[0].x, pts[0].y, pts[0].pressure, 0f)
        var carry = 0f
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val len = hypot(b.x - a.x, b.y - a.y)
            if (len <= 0f) continue
            val angle = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
            var d = carry
            while (d <= len) {
                val t = d / len
                val step = max(1f, stroke.sizePx * (a.pressure + (b.pressure - a.pressure) * t).coerceIn(0.2f, 1.5f) * spacing)
                if (d > 0f) stamp(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.pressure + (b.pressure - a.pressure) * t, angle)
                d += step
            }
            carry = d - len
        }
        return true
    }

    /**
     * Seçilen resimden fırça ucu: en çok [max] px, koyu yerler boya (alfa = (1 − parlaklık) × resim alfası), renk
     * beyaz (çizerken fırça rengine boyanır). Kenarlardaki tamamen boş satır/sütunlar kırpılır.
     */
    fun tipFrom(source: Bitmap, max: Int = 256): Bitmap {
        val scale = minOf(1f, max.toFloat() / maxOf(source.width, source.height))
        val w = maxOf(1, (source.width * scale).roundToInt())
        val h = maxOf(1, (source.height * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(source.copy(Bitmap.Config.ARGB_8888, false), w, h, true)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h) { i ->
            val c = px[i]
            val a = (c ushr 24) and 0xFF
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            val alpha = ((1f - lum) * a).roundToInt().coerceIn(0, 255)
            (alpha shl 24) or 0xFFFFFF
        }
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) if ((out[y * w + x] ushr 24) > 8) {
            if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        val full = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        if (maxX < minX || maxY < minY) return full
        return Bitmap.createBitmap(full, minX, minY, maxX - minX + 1, maxY - minY + 1)
    }
}
