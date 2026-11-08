package com.karapp.drawguidance.ui.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.isObject
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.isQuad
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Silgiyle artık hiç görünmeyen çizimleri (ve boşa kalan silgileri) veriden çıkarır; böylece elemanlar
 * listesinde görünmezler.
 *
 * Sonradan çizilen bir silginin değdiği her çizim gerçek piksellerle denetlenir: çizim tam kapsamayla (opaklık/akışkanlık dikkate
 * alınmadan, soluk çizimler de sayılsın diye) küçük bir maskeye çizilir, kendisinden sonraki silgiler
 * gerçek ayarlarıyla uygulanır ve görünür mürekkep kalmadıysa çizim kaldırılır. Bu yöntem birden çok silgi
 * geçişinin birlikte örttüğü, dokulu fırçalı ve resim elemanlarını da doğru bulur.
 */
object ErasedStrokePruner {

    /** Görünür sayılan en düşük alfa (≈%10) ve kalan piksel toleransı (kenar yumuşatma artıkları). */
    private const val VISIBLE_ALPHA = 26
    private const val LEFTOVER_PIXELS = 3
    private const val TARGET_SIDE = 256f
    private const val MAX_SIDE = 1024f

    fun prune(strokes: List<Stroke>, focus: Rect? = null): List<Stroke> {
        val erasers = strokes.withIndex().filter { it.value.brushType == BrushType.ERASER && it.value.points.isNotEmpty() }
        if (erasers.isEmpty()) return strokes
        val boundsCache = HashMap<String, Rect?>()
        fun boxOf(s: Stroke) = boundsCache.getOrPut(s.id) { inkBounds(s) }

        // 1) Kendisinden sonraki silgilerle artık görünmeyen çizimler (gerçek piksellerle)
        val removed = HashSet<String>()
        var removedArea: Rect? = null
        strokes.forEachIndexed { position, stroke ->
            // Nesne elemanlar (şekil, yazı, resim, diyagram) silgi değdiğinde zaten tamamen siliniyor
            if (stroke.brushType == BrushType.ERASER || stroke.brushType.isObject || stroke.points.isEmpty()) return@forEachIndexed
            val box = boxOf(stroke) ?: return@forEachIndexed
            if (focus != null && !focus.overlaps(box)) return@forEachIndexed
            val later = erasers.filter {
                it.index > position && (it.value.layerId ?: "") == (stroke.layerId ?: "") && boxOf(it.value)?.overlaps(box) == true
            }.map { it.value }
            if (later.isEmpty()) return@forEachIndexed
            if (!isVisible(stroke, later, box)) {
                removed += stroke.id
                removedArea = removedArea?.let { union(it, box) } ?: box
            }
        }

        // 2) Boşa kalan silgiler: kendisinden önce çizilmiş, hâlâ duran hiçbir çizimin mürekkebine erişemeyenler.
        // Temkinli: fırçanın dokusu/saçılması dahil en geniş erişim kullanılır (gereksiz silgiyi tutmak zararsız,
        // gerekli silgiyi atmak silinmiş izi geri getirirdi).
        val gcArea = when {
            focus == null -> null
            removedArea == null -> focus
            else -> union(focus, removedArea!!)
        }
        val kept = ArrayList<Stroke>(strokes.size)
        val visibleBefore = ArrayList<Stroke>()
        for (stroke in strokes) {
            if (stroke.id in removed) continue
            if (stroke.brushType != BrushType.ERASER) {
                // Silgi yalnızca fırça izlerini siler: nesneler silginin gerekliliğine sayılmaz
                if (!stroke.brushType.isObject) visibleBefore += stroke
                kept += stroke
                continue
            }
            val box = boxOf(stroke)
            val inArea = gcArea == null || (box != null && gcArea.overlaps(box))
            if (!inArea || stroke.points.isEmpty() ||
                visibleBefore.any { (it.layerId ?: "") == (stroke.layerId ?: "") && reaches(stroke, it, box, boxOf(it)) }
            ) kept += stroke
        }
        if (kept.size == strokes.size) return strokes
        return kept.mapIndexed { order, s -> if (s.order == order) s else s.copy(order = order) }
    }

    /** Silgi, çizimin (doku ve saçılma dahil) mürekkebine erişebilir mi? */
    private fun reaches(eraser: Stroke, target: Stroke, eraserBox: Rect?, targetBox: Rect?): Boolean {
        if (eraserBox == null || targetBox == null || !eraserBox.overlaps(targetBox)) return false
        val eraserHalf = ElementOps.halfWidth(eraser)
        val targetReach = if (target.brushType.isQuad) 0f else reachRadius(target)
        val limit = eraserHalf + targetReach + 1f
        val step = max(1f, eraserHalf * 0.5f)
        val pts = eraser.points
        fun near(x: Float, y: Float): Boolean {
            val p = androidx.compose.ui.geometry.Offset(x, y)
            return if (target.brushType.isQuad) ElementOps.distanceToShape(p, target) <= eraserHalf + 1f
            else ElementOps.distanceToPolyline(p, target.points) <= limit
        }
        if (pts.size == 1) return near(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            val length = kotlin.math.hypot(b.x - a.x, b.y - a.y)
            var t = 0f
            while (true) {
                val f = if (length == 0f) 0f else t / length
                if (near(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)) return true
                if (t >= length) break
                t = min(length, t + step)
            }
        }
        return false
    }

    /** Çizimin merkez çizgisinden mürekkebin en uzak ulaşabildiği mesafe. */
    private fun reachRadius(stroke: Stroke): Float {
        val half = ElementOps.halfWidth(stroke)
        return half + half * (stroke.brushType.style().reach - 1f) * 1.6f + 2f
    }

    /** Fırça dokusu, sapma ve uçlar dahil çizimin en geniş sınırı. */
    private fun inkBounds(stroke: Stroke): Rect? {
        val b = ElementOps.bounds(stroke) ?: return null
        if (stroke.brushType.isQuad) return b.inflate(1f)
        val half = ElementOps.halfWidth(stroke)
        // bounds zaten yarı genişliği içerir; doku/saçılma payı eklenir
        return b.inflate(half * (stroke.brushType.style().reach - 1f) * 1.6f + 2f)
    }

    private fun union(a: Rect, b: Rect) = Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom))

    /** Çizimden sonraki silgiler uygulandıktan sonra görünür mürekkep kalıyor mu? */
    internal fun isVisible(stroke: Stroke, laterErasers: List<Stroke>, box: Rect): Boolean {
        val side = max(box.width, box.height).coerceAtLeast(1f)
        val ink = if (stroke.brushType.isQuad) side else stroke.brushType.inkWidth(stroke.sizePx, stroke.strength)
        var scale = max(TARGET_SIDE / side, 2f / ink).coerceAtMost(2f)
        if (side * scale > MAX_SIDE) scale = MAX_SIDE / side
        val w = ceil(box.width * scale).toInt() + 2
        val h = ceil(box.height * scale).toInt() + 2
        val bitmap = Bitmap.createBitmap(max(1, w), max(1, h), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.translate(1f, 1f)
            canvas.scale(scale, scale)
            canvas.translate(-box.left, -box.top)
            drawCoverage(canvas, stroke)
            for (eraser in laterErasers) drawEraser(canvas, eraser)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            var visible = 0
            for (p in pixels) {
                if ((p ushr 24) > VISIBLE_ALPHA && ++visible > LEFTOVER_PIXELS) return true
            }
            return false
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawCoverage(canvas: Canvas, stroke: Stroke) {
        val pts = stroke.points
        if (stroke.brushType == BrushType.TEXT) {
            TextRenderer.draw(canvas, stroke, 0xFF000000.toInt(), coverage = true)
            return
        }
        if (stroke.brushType == BrushType.IMAGE) {
            if (pts.size < 4) return
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y); lineTo(pts[1].x, pts[1].y); lineTo(pts[2].x, pts[2].y); lineTo(pts[3].x, pts[3].y); close()
            }
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() })
            return
        }
        val xs = FloatArray(pts.size) { pts[it].x }
        val ys = FloatArray(pts.size) { pts[it].y }
        val stream = StrokeStream.create(stroke.brushType, 0xFF000000.toInt(), stroke.sizePx, 1f, stroke.id.hashCode(), 1f, stroke.strength, params = stroke.params)
        stream.coverageOnly = true
        // v18: kalem basınçlı çizginin kapladığı alan da basınca göre (v20: eğim ayarları, aralık, 3B gölge)
        val (ps, ts, os) = BrushPainter.dynamicsOf(pts)
        stream.setPoints(xs, ys, pts.size, ps, ts, os)
        stream.drawStatic(canvas)
    }

    private fun drawEraser(canvas: Canvas, eraser: Stroke) {
        val pts = eraser.points
        val xs = FloatArray(pts.size) { pts[it].x }
        val ys = FloatArray(pts.size) { pts[it].y }
        val (ps, ts, _) = BrushPainter.dynamicsOf(pts)
        BrushPainter.paint(canvas, xs, ys, pts.size, 0xFF000000.toInt(), BrushType.ERASER, eraser.sizePx, eraser.opacity, eraser.id.hashCode(), eraser.flow, eraser.strength, ps, ts)
    }
}
