package com.karapp.drawguidance.ui.drawing

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.model.ShapeSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.SubPath
import com.karapp.drawguidance.ui.common.hexToColor
import androidx.compose.ui.graphics.toArgb
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Vektör şekillerin çizimi: alt yollar (kapalı/kesik), dolgu rengi, kenar rengi/kalınlığı ve köşe
 * yuvarlaklığı. Yollar çizim başına önbelleklenir (aynı noktalarla tekrar çizimde yeniden kurulmaz).
 */
object ShapeRenderer {

    /** Parça renginde "kalem rengi" anlamındaki özel değer. */
    const val INK = "@ink"

    /**
     * v21 gölgeli (3B) cisimlerin yüz dolgusu: "@shade:0.72" şeklin dolgu renginin (yoksa kenar renginin) tonudur
     * (0.5 = kendisi, altı koyu, üstü açık). Renk değişince yüzler yeni renge göre gölgelenir.
     */
    const val SHADE = "@shade:"

    /** v21 dolgu almayan kapalı parça (cisimlerin dış hattı). */
    const val NO_FILL = "@none"

    fun shade(level: Float): String = SHADE + String.format(java.util.Locale.US, "%.2f", level.coerceIn(0f, 1f))

    fun isShade(hex: String?): Boolean = hex != null && hex.startsWith(SHADE)

    /** Parçanın gerçek dolgu rengi (özel değerler çözülür); dolgu yoksa null. */
    fun resolveFill(own: String?, style: com.karapp.drawguidance.data.model.ShapeStyle, ink: String = style.strokeColorHex): String? = when {
        own == null -> style.fillColorHex
        own == INK -> ink
        own == NO_FILL -> null
        own.startsWith(SHADE) -> {
            val level = own.removePrefix(SHADE).toFloatOrNull() ?: 0.5f
            val fill = style.fillColorHex?.takeIf { it.isNotEmpty() }
            var base = hexToColor(fill ?: ink).toArgb()
            // Dolgusuz cisimde çok koyu kenar rengi açılır (gölge tonları görünsün)
            if (fill == null) {
                val lum = (0.3f * android.graphics.Color.red(base) + 0.59f * android.graphics.Color.green(base) + 0.11f * android.graphics.Color.blue(base)) / 255f
                if (lum < 0.35f) base = SurfaceTextures.mix(base, android.graphics.Color.WHITE, 0.5f)
            }
            String.format("#%06X", SurfaceTextures.tone(base, level) and 0xFFFFFF)
        }
        else -> own
    }

    /** Köşe sayılan en küçük yön değişimi (daire gibi eğrilerde yuvarlama uygulanmaz). */
    private const val CORNER_COS = 0.94f

    /**
     * Çizim sırasındaki bir parça grubu: art arda gelen ve aynı biçimdeki parçalar tek yolda birleşir (az çizim
     * çağrısı). Gruplar sırayla çizilir: önce dolgu, sonra kenar — üstteki parça alttakini örter.
     */
    private class Item(
        val path: Path,
        val fill: Boolean,
        val ownFill: String?,
        val stroke: String?,
        val widthScale: Float,
        val dashed: Boolean,
        val dotted: Boolean,
        val alpha: Float,
    ) {
        fun sameStyle(o: Item) = fill == o.fill && ownFill == o.ownFill && stroke == o.stroke && widthScale == o.widthScale &&
            dashed == o.dashed && dotted == o.dotted && alpha == o.alpha
    }

    private class Cached(
        val points: List<StrokePoint>,
        val radius: Float,
        val subpaths: List<SubPath>,
        val items: List<Item>,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )

    /**
     * Kurulmuş yolların önbelleği. v36: sınır nokta sayısıyla (çok parçalı karakterler az, basit şekiller çok yer
     * tutar; bellek öngörülebilir). Küçük resimler önbelleğe girmez (tuvaldeki şekillerin yollarını silmesin).
     */
    private val cache = object : LruCache<String, Cached>(MAX_CACHED_POINTS) {
        override fun sizeOf(key: String, value: Cached): Int = value.points.size + 32
    }
    private const val MAX_CACHED_POINTS = 400_000
    private val clipRect = object : ThreadLocal<android.graphics.Rect>() { override fun initialValue() = android.graphics.Rect() }

    /**
     * Alt yol dolgu alır mı: kapalı yollar ve parçalanınca kopan yeri küçük kalan (neredeyse kapalı) yollar.
     * Böylece şekil parçalanınca dolgusu kaybolmaz; eğri, doğru gibi açık şekiller dolgu almaz.
     */
    fun fills(sub: SubPath, pts: List<StrokePoint>, strokeWidth: Float): Boolean {
        if (sub.label != null || pts.size < 3) return false
        if (sub.fillColorHex == NO_FILL) return false
        // Kendi dolgusu verilmiş parça (ör. kütüphane çizimlerinde ağzı açık cam kap) kapalı gibi dolar
        if (sub.fillColorHex != null) return true
        if (sub.dashed) return false
        if (sub.closed) return true
        val gap = hypot(pts.first().x - pts.last().x, pts.first().y - pts.last().y)
        if (gap > max(14f, strokeWidth * 6f)) return false
        var length = 0f
        for (i in 1 until pts.size) length += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        return length > gap * 8f
    }

    /** Şeklin dolgu alabilen bir yolu var mı (yoksa "dolgu" rengi kenar rengine uygulanır). */
    fun isFillable(stroke: Stroke): Boolean {
        val spec = stroke.shape ?: return false
        val ranges = ranges(spec, stroke.points.size)
        return ranges.indices.any { i ->
            val sub = spec.subpaths.getOrNull(i) ?: return@any false
            fills(sub, subPoints(stroke, ranges[i]), spec.style.strokeWidth)
        }
    }

    /** Şeklin alt yollarının nokta aralıkları. */
    fun ranges(spec: ShapeSpec, total: Int): List<IntRange> {
        val out = ArrayList<IntRange>(spec.subpaths.size)
        var start = 0
        for (sub in spec.subpaths) {
            val end = min(total, start + sub.count)
            if (end > start) out += start until end
            start = end
        }
        if (out.isEmpty() && total > 0) out += 0 until total
        return out
    }

    /** Bir alt yolun noktaları. */
    fun subPoints(stroke: Stroke, range: IntRange): List<StrokePoint> = stroke.points.subList(range.first, range.last + 1)

    private fun build(stroke: Stroke, spec: ShapeSpec, useCache: Boolean = true): Cached {
        val radius = spec.style.cornerRadius
        val key = stroke.id
        if (useCache) cache.get(key)?.let { if (it.points === stroke.points && it.radius == radius && it.subpaths === spec.subpaths) return it }
        val items = ArrayList<Item>()
        ranges(spec, stroke.points.size).forEachIndexed { i, range ->
            val sub = spec.subpaths.getOrNull(i) ?: SubPath(range.last - range.first + 1)
            val pts = subPoints(stroke, range)
            if (pts.size < 2 || sub.label != null) return@forEachIndexed
            val fills = fills(sub, pts, spec.style.strokeWidth)
            val next = Item(
                Path(), fills, sub.fillColorHex, sub.strokeColorHex, sub.widthScale ?: 1f, sub.dashed, sub.dotted,
                (sub.alpha ?: 1f).coerceIn(0f, 1f),
            )
            val last = items.lastOrNull()
            val target = if (last != null && last.sameStyle(next)) last else next.also { items += it }
            addPath(target.path, pts, sub.closed || fills, radius)
        }
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var bt = -Float.MAX_VALUE
        stroke.points.forEach { l = min(l, it.x); t = min(t, it.y); r = max(r, it.x); bt = max(bt, it.y) }
        val cached = Cached(stroke.points, radius, spec.subpaths, items, l, t, r, bt)
        if (useCache) cache.put(key, cached)
        return cached
    }

    private fun addPath(path: Path, pts: List<StrokePoint>, closed: Boolean, radius: Float) {
        val n = pts.size
        if (radius <= 0.01f) {
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until n) path.lineTo(pts[i].x, pts[i].y)
            if (closed) path.close()
            return
        }
        // Köşeleri yuvarla: köşeden iki kenara doğru geri çekilip köşe noktası kontrol noktası olur
        val last = if (closed && pts.first().x == pts.last().x && pts.first().y == pts.last().y) n - 1 else n
        var started = false
        for (i in 0 until last) {
            val p = pts[i]
            val prev = pts[(i - 1 + last) % last]
            val next = pts[(i + 1) % last]
            val open = !closed && (i == 0 || i == last - 1)
            val v1x = prev.x - p.x; val v1y = prev.y - p.y
            val v2x = next.x - p.x; val v2y = next.y - p.y
            val l1 = hypot(v1x, v1y); val l2 = hypot(v2x, v2y)
            val cos = if (l1 < 1e-3f || l2 < 1e-3f) 1f else (v1x * v2x + v1y * v2y) / (l1 * l2)
            if (open || cos < -CORNER_COS || cos > CORNER_COS || l1 < 1e-3f || l2 < 1e-3f) {
                // Düz devam ya da açık uç: köşe yuvarlanmaz
                if (!started) { path.moveTo(p.x, p.y); started = true } else path.lineTo(p.x, p.y)
                continue
            }
            val d = min(radius, min(l1, l2) / 2f)
            val a = Offset(p.x + v1x / l1 * d, p.y + v1y / l1 * d)
            val b = Offset(p.x + v2x / l2 * d, p.y + v2y / l2 * d)
            if (!started) { path.moveTo(a.x, a.y); started = true } else path.lineTo(a.x, a.y)
            path.quadTo(p.x, p.y, b.x, b.y)
        }
        if (closed) path.close()
    }

    // Çizim hem ana iş parçacığında hem arka planda (önbellek, dışa aktarma) yapılır: her iş parçacığının kendi boyası
    private val paints = object : ThreadLocal<Paint>() { override fun initialValue() = Paint(Paint.ANTI_ALIAS_FLAG) }
    private val paint: Paint get() = paints.get()!!

    /** Şekli çizer: önce dolgu, sonra kenar (kesik alt yollar kesik çizgiyle). */
    fun draw(canvas: Canvas, stroke: Stroke, offsetX: Float = 0f, offsetY: Float = 0f, coverage: Boolean = false, cache: Boolean = true) {
        val spec = stroke.shape ?: return
        if (stroke.points.size < 2) return
        val cached = build(stroke, spec, cache)
        val save = canvas.save()
        if (offsetX != 0f || offsetY != 0f) canvas.translate(offsetX, offsetY)
        // Görünür alanın tamamen dışındaki şekiller çizilmez (yakınlaştırmada büyük kazanç)
        val clip = clipRect.get()!!
        if (canvas.getClipBounds(clip)) {
            val pad = spec.style.strokeWidth + 2f
            if (cached.right + pad < clip.left || cached.left - pad > clip.right ||
                cached.bottom + pad < clip.top || cached.top - pad > clip.bottom
            ) {
                canvas.restoreToCount(save)
                return
            }
        }
        val alpha = if (coverage) 1f else stroke.opacity.coerceIn(0f, 1f)
        val style = spec.style
        val baseWidth = max(0.2f, style.strokeWidth)
        cached.items.forEach { item ->
            val ink = item.stroke?.takeIf { it != INK } ?: style.strokeColorHex
            // Dolgu: parçanın kendi rengi ("@ink": kenar rengi), yoksa şeklin dolgusu
            if (item.fill) {
                val hex = resolveFill(item.ownFill, style, ink)
                if (hex != null || coverage) {
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.style = Paint.Style.FILL
                    paint.color = if (coverage) 0xFF000000.toInt() else hexToColor(hex!!).toArgb()
                    val opacity = if (item.ownFill != null) 1f else style.fillOpacity.coerceIn(0f, 1f)
                    paint.alpha = ((if (coverage) 1f else opacity * alpha * item.alpha) * 255).toInt()
                    // v27 şeklin gradyan dolgusu (parçanın kendi rengi yoksa; şeklin kutusuna göre)
                    if (!coverage && item.ownFill == null && style.fillGradient?.valid == true) {
                        paint.shader = Gradients.shader(style.fillGradient, cached.left, cached.top, cached.right, cached.bottom)
                        paint.color = 0xFFFFFFFF.toInt()
                        paint.alpha = (opacity * alpha * item.alpha * 255).toInt()
                    }
                    canvas.drawPath(item.path, paint)
                    paint.shader = null
                }
            }
            val width = baseWidth * item.widthScale
            if (item.widthScale <= 0f) return@forEach
            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(0.2f, width)
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = if (coverage) 0xFF000000.toInt() else hexToColor(ink).toArgb()
            paint.alpha = ((if (coverage) 1f else alpha * item.alpha) * 255).toInt()
            paint.pathEffect = when {
                item.dotted -> DashPathEffect(floatArrayOf(0.01f, max(width * 2.2f, 5f)), 0f)
                item.dashed -> max(width * 3f, 9f).let { DashPathEffect(floatArrayOf(it, it * 0.7f), 0f) }
                else -> null
            }
            canvas.drawPath(item.path, paint)
        }
        paint.pathEffect = null
        drawLabels(canvas, stroke, spec, alpha, coverage)
        canvas.restoreToCount(save)
    }

    private val textPaints = object : ThreadLocal<Paint>() { override fun initialValue() = Paint(Paint.ANTI_ALIAS_FLAG) }
    private val textPaint: Paint get() = textPaints.get()!!

    /** Eksen etiketleri ve formül yazısı (tek noktalık alt yollar). */
    private fun drawLabels(canvas: Canvas, stroke: Stroke, spec: ShapeSpec, alpha: Float, coverage: Boolean) {
        if (spec.subpaths.none { it.label != null }) return
        textPaint.reset()
        textPaint.isAntiAlias = true
        textPaint.style = Paint.Style.FILL
        textPaint.color = if (coverage) 0xFF000000.toInt() else hexToColor(spec.style.strokeColorHex).toArgb()
        textPaint.alpha = ((if (coverage) 1f else alpha) * 255).toInt()
        var start = 0
        for (sub in spec.subpaths) {
            val end = min(stroke.points.size, start + sub.count)
            val text = sub.label
            if (text != null && end > start && text.isNotBlank()) {
                val p = stroke.points[start]
                val own = sub.strokeColorHex
                if (!coverage) {
                    textPaint.color = hexToColor(own?.takeIf { it != INK } ?: spec.style.strokeColorHex).toArgb()
                    textPaint.alpha = (alpha * (sub.alpha ?: 1f).coerceIn(0f, 1f) * 255).toInt()
                }
                textPaint.textSize = max(4f, sub.labelSize)
                canvas.drawText(text, p.x, p.y + textPaint.textSize * 0.82f, textPaint)
            }
            start = end
        }
    }

    /** Şeklin kapladığı en geniş alan payı (kenar kalınlığının yarısı). */
    fun halfWidth(stroke: Stroke): Float = max(0.5f, (stroke.shape?.style?.strokeWidth ?: 1f) / 2f)

    /** Nokta şeklin üstünde mi (kenara uzaklık ya da dolgunun içi)? */
    fun distanceTo(stroke: Stroke, point: Offset): Float {
        val spec = stroke.shape ?: return Float.MAX_VALUE
        var best = Float.MAX_VALUE
        ranges(spec, stroke.points.size).forEachIndexed { i, range ->
            val pts = subPoints(stroke, range)
            val sub = spec.subpaths.getOrNull(i)
            val closed = sub?.closed == true
            val list = if (closed && pts.size > 2) pts + pts.first() else pts
            best = min(best, com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps.distanceToPolyline(point, list))
            if (best <= 0f) return 0f
            val filled = sub != null && fills(sub, pts, spec.style.strokeWidth)
            if (filled && resolveFill(sub?.fillColorHex, spec.style) != null && insidePolygon(point, pts)) return 0f
        }
        return best
    }

    private fun insidePolygon(p: Offset, pts: List<StrokePoint>): Boolean {
        var inside = false
        var j = pts.size - 1
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y + 1e-6f) + a.x) inside = !inside
            j = i
        }
        return inside
    }
}
