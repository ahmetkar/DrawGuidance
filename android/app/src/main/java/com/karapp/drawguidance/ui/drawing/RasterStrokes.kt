package com.karapp.drawguidance.ui.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.isHelper
import com.karapp.drawguidance.ui.common.hexToColor
import androidx.compose.ui.graphics.toArgb
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * v51 piksel tabanlı fırça izlerinin (boya ve yardımcı fırçalar) Android çizimi: izin altındaki aynı katman içeriği
 * yazılımsal bir bitmap'e çizilir, [RasterBrush] ile işlenir, sonuç tuvale "maskeyle sil + sonucu ekle" olarak konur.
 * Sonuçlar çizim ve altındaki içeriğin kimliğiyle önbelleğe alınır: yakınlaştırma/kaydırmada yeniden hesaplanmaz.
 */
internal object RasterStrokes {
    /** Bir iz için hesaplanan en çok piksel sayısı (ölçek buna göre düşer). */
    private const val MAX_PIXELS = 2_500_000
    private const val MAX_SCALE = 2f
    private const val CACHE_BYTES = 64 * 1024 * 1024

    private class Entry(
        val stroke: Stroke, val contextKey: Long,
        val left: Float, val top: Float, val scale: Float,
        val color: Bitmap, val mask: Bitmap,
    ) {
        val bytes: Int get() = color.byteCount + mask.byteCount
    }

    private val cache = object : android.util.LruCache<String, Entry>(com.karapp.drawguidance.platform.DeviceCaps.secondaryCacheBytes(CACHE_BYTES)) {
        override fun sizeOf(key: String, value: Entry): Int = value.bytes
    }.also { c -> com.karapp.drawguidance.platform.DeviceCaps.onTrim { synchronized(c) { c.evictAll() } } }

    private val addPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val clearPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val plainPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /**
     * v52 düzenleyicideki sayfanın kağıt rengi (ARGB; 0 = yok). Yardımcı fırçalar (klon, onarma, karıştırıcı, bulanıklaştırma…)
     * alttaki içeriği kağıdın üstünde okur: boş kağıttan klonlanan yer kağıt rengini alır, karıştırıcı kağıtla karışır.
     * Eskiden kağıt saydam okunuyordu; klon/karıştırıcı boş alanlarda "etkisiz" görünüyor, klon çizimi siliyordu.
     */
    @Volatile var paperArgb: Int = 0
    /** Dışa aktarma/küçük resim çiziminde o çizimin kağıdı (iş parçacığına özel; düzenleyicinin değerini ezmez). */
    val paperOverride = ThreadLocal<Int?>()
    private fun paperNow(): Int = paperOverride.get() ?: paperArgb

    /**
     * İzi çizer. [context] izden önce çizilen aynı katman içeriği (null: bağlam yok, ör. fırça önizlemesi ya da bekleyen
     * çizim): boya fırçaları boş zemine çizilir, yardımcı fırçalar hiçbir şey çizmez.
     */
    fun draw(canvas: Canvas, stroke: Stroke, context: List<Stroke>?) {
        if (stroke.points.isEmpty()) return
        if (context == null && stroke.brushType.isHelper) return
        val entry = entryFor(stroke, context) ?: return
        val save = canvas.save()
        canvas.translate(entry.left, entry.top)
        val inv = 1f / entry.scale
        canvas.scale(inv, inv)
        if (context != null) {
            canvas.drawBitmap(entry.mask, 0f, 0f, clearPaint)
            canvas.drawBitmap(entry.color, 0f, 0f, addPaint)
        } else {
            canvas.drawBitmap(entry.color, 0f, 0f, plainPaint)
        }
        canvas.restoreToCount(save)
    }

    private fun sameLook(a: Stroke, b: Stroke): Boolean = a === b || (a.points === b.points && a.colorHex == b.colorHex &&
        a.brushType == b.brushType && a.sizePx == b.sizePx && a.opacity == b.opacity && a.flow == b.flow && a.strength == b.strength &&
        a.params == b.params)

    /** İzin okuduğu ve yazdığı bölge (tuval birimi). */
    fun region(stroke: Stroke): Rect? {
        val b = StrokeRenderer.paintBounds(stroke) ?: return null
        val width = stroke.brushType.inkWidth(stroke.sizePx, stroke.strength)
        val margin = RasterBrush.readMargin(stroke.brushType, width, stroke.strength)
        var r = Rect(b.left - margin, b.top - margin, b.right + margin, b.bottom + margin)
        RasterBrush.sourceOffset(stroke.brushType, stroke.params, width)?.let { (dx, dy) ->
            r = Rect(min(r.left, b.left + dx), min(r.top, b.top + dy), max(r.right, b.right + dx), max(r.bottom, b.bottom + dy))
        }
        return r
    }

    /** v52 ana iş parçacığında istenen ama önbellekte olmayan sonuçlar burada hesaplanır (çizim donmasın). */
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "dg-raster").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
    private val inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    /** Arka planda bir sonuç bitince artar; düzenleyici tuvali bunu okur ve yeniden çizer. */
    val version = androidx.compose.runtime.mutableIntStateOf(0)

    private fun entryFor(stroke: Stroke, context: List<Stroke>?): Entry? {
        val region = region(stroke) ?: return null
        val relevant = context?.filter { !it.hidden && StrokeRenderer.paintBounds(it)?.overlaps(region) == true }
        val paper = if (relevant == null || !stroke.brushType.isHelper) 0 else paperNow()
        var key = if (relevant == null) -1L else 17L + paper
        relevant?.forEach { key = key * 31 + System.identityHashCode(it) }
        val stale: Entry?
        synchronized(cache) {
            val e = cache.get(stroke.id)
            if (e != null && e.contextKey == key && sameLook(e.stroke, stroke)) return e
            stale = e?.takeIf { sameLook(it.stroke, stroke) }
        }
        // v52 ana iş parçacığında ağır piksel hesabı yapılmaz: eski sonuç (varsa) gösterilir, yenisi arka planda
        // hesaplanınca tuval yeniden çizilir. Dışa aktarma ve önbellek üretimi (arka plan) eskisi gibi beklenir.
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper() && android.os.Build.FINGERPRINT != "robolectric") {
            val job = stroke.id + "@" + key
            if (inFlight.add(job)) {
                worker.execute {
                    try { compute(stroke, region, relevant, paper, key) } catch (t: Throwable) {
                        com.karapp.drawguidance.diagnostics.AppLog.w("RasterStrokes", "piksel fırça hesaplanamadı", t)
                    } finally {
                        inFlight.remove(job)
                        android.os.Handler(android.os.Looper.getMainLooper()).post { version.intValue++ }
                    }
                }
            }
            return stale
        }
        return compute(stroke, region, relevant, paper, key)
    }

    private fun compute(stroke: Stroke, region: Rect, relevant: List<Stroke>?, paper: Int, key: Long): Entry? {
        val rw = region.width; val rh = region.height
        if (rw <= 0f || rh <= 0f) return null
        val scale = min(MAX_SCALE, sqrt(MAX_PIXELS / (rw * rh)))
        val w = max(1, ceil(rw * scale).toInt()); val h = max(1, ceil(rh * scale).toInt())
        val left = floor(region.left); val top = floor(region.top)
        val patch = RasterBrush.Patch(left, top, scale, w, h)
        if (relevant != null && (relevant.isNotEmpty() || (paper ushr 24) != 0)) renderBase(relevant, patch, paper)
        val n = stroke.points.size
        val xs = FloatArray(n) { stroke.points[it].x }
        val ys = FloatArray(n) { stroke.points[it].y }
        val d = BrushPainter.dynamicsOf(stroke.points)
        val result = RasterBrush.render(
            patch, stroke.brushType, hexToColor(stroke.colorHex).toArgb(), stroke.sizePx, stroke.opacity, stroke.flow, stroke.strength,
            stroke.id.hashCode(), xs, ys, n, d.pressures, d.tilts, d.orients, d.rolls, d.barrels, stroke.params,
        )
        val colors = IntArray(w * h)
        val masks = IntArray(w * h)
        val c = result.color; val m = result.mask
        for (i in 0 until w * h) {
            val mk = m[i]
            if (mk <= 0f) continue
            val a = c[i * 4 + 3]
            val outA = (a * mk).coerceIn(0f, 1f)
            masks[i] = ((mk.coerceIn(0f, 1f) * 255f).roundToInt() shl 24)
            if (outA <= 0f || a <= 0f) continue
            val r = (c[i * 4] / a).coerceIn(0f, 1f); val g = (c[i * 4 + 1] / a).coerceIn(0f, 1f); val b = (c[i * 4 + 2] / a).coerceIn(0f, 1f)
            colors[i] = ((outA * 255f).roundToInt() shl 24) or ((r * 255f).roundToInt() shl 16) or ((g * 255f).roundToInt() shl 8) or (b * 255f).roundToInt()
        }
        val colorBitmap = Bitmap.createBitmap(colors, w, h, Bitmap.Config.ARGB_8888)
        val maskBitmap = Bitmap.createBitmap(masks, w, h, Bitmap.Config.ARGB_8888)
        val entry = Entry(stroke, key, left, top, scale, colorBitmap, maskBitmap)
        synchronized(cache) { cache.put(stroke.id, entry) }
        return entry
    }

    /** Alttaki içeriği (aynı katmanın önceki çizimleri) bölgenin pikseline çizer ve ön çarpımlı RGBA'ya çevirir. */
    private fun renderBase(strokes: List<Stroke>, patch: RasterBrush.Patch, paper: Int = 0) {
        val bitmap = Bitmap.createBitmap(patch.w, patch.h, Bitmap.Config.ARGB_8888)
        // v52 kağıt rengi zemin (yardımcı fırçalar boş kağıdı da okur)
        if ((paper ushr 24) != 0) bitmap.eraseColor(paper)
        val native = Canvas(bitmap)
        native.scale(patch.scale, patch.scale)
        native.translate(-patch.left, -patch.top)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(native), Size(patch.w.toFloat(), patch.h.toFloat())) {
            with(StrokeRenderer) { drawStrokeList(strokes) }
        }
        val px = IntArray(patch.w * patch.h)
        bitmap.getPixels(px, 0, patch.w, 0, 0, patch.w, patch.h)
        bitmap.recycle()
        val out = patch.rgba
        for (i in px.indices) {
            val v = px[i]
            val a = (v ushr 24) / 255f
            if (a <= 0f) continue
            out[i * 4] = ((v shr 16) and 0xFF) / 255f * a
            out[i * 4 + 1] = ((v shr 8) and 0xFF) / 255f * a
            out[i * 4 + 2] = (v and 0xFF) / 255f * a
            out[i * 4 + 3] = a
        }
    }

    /** Test ve bellek baskısı için. */
    fun clear() = synchronized(cache) { cache.evictAll() }
}
