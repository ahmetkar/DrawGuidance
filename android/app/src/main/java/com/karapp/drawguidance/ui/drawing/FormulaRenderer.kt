package com.karapp.drawguidance.ui.drawing

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.content.formula.DrawOp
import com.karapp.drawguidance.content.formula.Formula
import com.karapp.drawguidance.content.formula.FormulaLayout
import com.karapp.drawguidance.content.formula.GlyphMetrics
import com.karapp.drawguidance.content.formula.GlyphStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import kotlin.math.max

/**
 * Formül elemanının çizimi: formül motoru ([Formula]) yazı tipinin ölçüleriyle dizilir, çizim komutları (glifler,
 * çizgiler, kök/ayraç/integral yolları) elemanın 4 köşesine eşlenir. Formül resim değil yazıdır: her boyutta keskin,
 * taşınır, döndürülür ve ölçeklenir. Düzenlenebilir yuvalar (katsayılar) istenirse vurgulanır.
 */
object FormulaRenderer {

    /** Dizgi boyutu: elemanın yazı boyutundan bağımsız sabit boyutta dizilir, dörtgene ölçeklenir. */
    private const val LAYOUT_SIZE = 100f

    /** Kutunun her yanındaki pay (em oranı): italik taşmaları kesilmesin. */
    private const val PAD = 0.18f

    private val typefaces = mapOf(
        GlyphStyle.ROMAN to Typeface.create(Typeface.SERIF, Typeface.NORMAL),
        GlyphStyle.ITALIC to Typeface.create(Typeface.SERIF, Typeface.ITALIC),
        GlyphStyle.BOLD to Typeface.create(Typeface.SERIF, Typeface.BOLD),
        GlyphStyle.BOLD_ITALIC to Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC),
    )

    /** Her iş parçacığının kendi boyası (çizim hem ana iş parçacığında hem arka planda yapılır). */
    private val textPaints = object : ThreadLocal<Paint>() { override fun initialValue() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG) }
    private val linePaints = object : ThreadLocal<Paint>() { override fun initialValue() = Paint(Paint.ANTI_ALIAS_FLAG) }

    /** Android yazı ölçüleri (Paint): dizgi motoruna verilir. */
    private object Metrics : GlyphMetrics {
        // ThreadLocal.withInitial Android 8.0+; eski sürümler için alt sınıf
        private val paint = object : ThreadLocal<Paint>() { override fun initialValue() = Paint(Paint.ANTI_ALIAS_FLAG) }
        private val bounds = object : ThreadLocal<Rect>() { override fun initialValue() = Rect() }

        private fun p(size: Float, style: GlyphStyle): Paint = paint.get()!!.apply {
            textSize = size
            typeface = typefaces.getValue(style)
        }

        override fun advance(text: String, size: Float, style: GlyphStyle): Float = p(size, style).measureText(text)
        override fun ascent(size: Float): Float = -p(size, GlyphStyle.ROMAN).fontMetrics.ascent
        override fun descent(size: Float): Float = p(size, GlyphStyle.ROMAN).fontMetrics.descent
        override fun inkBounds(text: String, size: Float, style: GlyphStyle): FloatArray? {
            val r = bounds.get()!!
            p(size, style).getTextBounds(text, 0, text.length, r)
            if (r.isEmpty) return null
            return floatArrayOf(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
        }
    }

    private val layouts = object : LruCache<String, FormulaLayout>(256) {}

    /** Kaynağın dizgisi (önbellekli). */
    fun layout(source: String): FormulaLayout {
        layouts.get(source)?.let { return it }
        val l = Formula.layout(source, LAYOUT_SIZE, Metrics)
        layouts.put(source, l)
        return l
    }

    /** Dizginin paylı kutusu (dizgi birimi): sol, üst, genişlik, yükseklik. */
    private fun box(l: FormulaLayout): RectF {
        val pad = LAYOUT_SIZE * PAD
        return RectF(-pad, -l.ascent - pad, l.width + pad, l.descent + pad)
    }

    /**
     * [size] yazı boyutunda formülün tuvaldeki dörtgeni: sol-üst köşesi [left],[top]. Noktalar sırasıyla sol-üst,
     * sağ-üst, sağ-alt, sol-alt.
     */
    fun quadAt(source: String, size: Float, left: Float, top: Float): List<Offset> {
        val b = box(layout(source))
        val k = size / LAYOUT_SIZE
        val w = b.width() * k
        val h = b.height() * k
        return listOf(Offset(left, top), Offset(left + w, top), Offset(left + w, top + h), Offset(left, top + h))
    }

    /**
     * Kaynak değişince dörtgen yeni dizgiye göre yeniden ölçülür: sol-üst köşe, dönüş ve ölçek korunur.
     */
    fun refit(stroke: Stroke, source: String): List<StrokePoint> {
        val old = stroke.formula?.source ?: source
        val oldBox = box(layout(old))
        val newBox = box(layout(source))
        if (stroke.points.size < 4) return stroke.points
        val p0 = stroke.points[0]; val p1 = stroke.points[1]; val p3 = stroke.points[3]
        val ux = (p1.x - p0.x) / oldBox.width(); val uy = (p1.y - p0.y) / oldBox.width()
        val vx = (p3.x - p0.x) / oldBox.height(); val vy = (p3.y - p0.y) / oldBox.height()
        val w = newBox.width(); val h = newBox.height()
        fun at(a: Float, b: Float) = StrokePoint(p0.x + ux * a + vx * b, p0.y + uy * a + vy * b)
        return listOf(at(0f, 0f), at(w, 0f), at(w, h), at(0f, h))
    }

    private fun matrix(stroke: Stroke, l: FormulaLayout): Matrix? {
        if (stroke.points.size < 4) return null
        val b = box(l)
        val src = floatArrayOf(b.left, b.top, b.right, b.top, b.right, b.bottom, b.left, b.bottom)
        val dst = FloatArray(8)
        for (i in 0 until 4) { dst[i * 2] = stroke.points[i].x; dst[i * 2 + 1] = stroke.points[i].y }
        val m = Matrix()
        return if (m.setPolyToPoly(src, 0, dst, 0, 4)) m else null
    }

    /** Tuvaldeki bir noktanın dizgi koordinatı (yuvaya dokunmayı bulmak için). */
    fun toLayout(stroke: Stroke, p: Offset): Offset? {
        val source = stroke.formula?.source ?: return null
        val m = matrix(stroke, layout(source)) ?: return null
        val inv = Matrix()
        if (!m.invert(inv)) return null
        val pts = floatArrayOf(p.x, p.y)
        inv.mapPoints(pts)
        return Offset(pts[0], pts[1])
    }

    /** v16: yuvanın tuvaldeki dörtgeni (sol-üst, sağ-üst, sağ-alt, sol-alt); formül üstünde yazı alanı için. */
    fun slotQuad(stroke: Stroke, name: String): List<Offset>? {
        val source = stroke.formula?.source ?: return null
        val l = layout(source)
        val r = l.slots.firstOrNull { it.name == name } ?: return null
        val m = matrix(stroke, l) ?: return null
        val pad = LAYOUT_SIZE * 0.04f
        val pts = floatArrayOf(r.left - pad, r.top - pad, r.right + pad, r.top - pad, r.right + pad, r.bottom + pad, r.left - pad, r.bottom + pad)
        m.mapPoints(pts)
        return List(4) { Offset(pts[it * 2], pts[it * 2 + 1]) }
    }

    /** Dokunulan yuvanın adı (yoksa null). */
    fun slotAt(stroke: Stroke, p: Offset): String? {
        val source = stroke.formula?.source ?: return null
        val q = toLayout(stroke, p) ?: return null
        val pad = LAYOUT_SIZE * 0.08f
        return layout(source).slots.firstOrNull { q.x in (it.left - pad)..(it.right + pad) && q.y in (it.top - pad)..(it.bottom + pad) }?.name
    }

    /** Yalnızca düzenlenebilir yuvaların vurgusu (seçili formülün üstüne; formül ayrıca çizilmiş olur). */
    fun drawSlots(canvas: Canvas, stroke: Stroke, activeSlot: String?, color: Int) {
        val source = stroke.formula?.source ?: return
        val l = layout(source)
        if (l.slots.isEmpty()) return
        val m = matrix(stroke, l) ?: return
        val save = canvas.save()
        canvas.concat(m)
        val hp = linePaints.get()!!
        val pad = LAYOUT_SIZE * 0.04f
        l.slots.forEach { r ->
            val rect = RectF(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
            hp.reset(); hp.isAntiAlias = true; hp.style = Paint.Style.FILL
            // v17: her sayı/harf ayrı eleman — hepsi hafif, düzenlenen belirgin
            hp.color = color; hp.alpha = if (r.name == activeSlot) 70 else 22
            canvas.drawRoundRect(rect, LAYOUT_SIZE * 0.06f, LAYOUT_SIZE * 0.06f, hp)
            hp.style = Paint.Style.STROKE; hp.strokeWidth = LAYOUT_SIZE * 0.012f; hp.alpha = if (r.name == activeSlot) 220 else 60
            canvas.drawRoundRect(rect, LAYOUT_SIZE * 0.06f, LAYOUT_SIZE * 0.06f, hp)
        }
        canvas.restoreToCount(save)
    }

    /**
     * Formülü çizer. [highlightSlots]: düzenlenebilir yuvalar hafif renkli zeminle gösterilir (seçiliyken),
     * [activeSlot] daha belirgin.
     */
    fun draw(
        canvas: Canvas, stroke: Stroke, color: Int, offsetX: Float = 0f, offsetY: Float = 0f,
        highlightSlots: Boolean = false, activeSlot: String? = null, highlightColor: Int = 0xFF4F6BED.toInt(),
    ) {
        val source = stroke.formula?.source ?: return
        val l = layout(source)
        val m = matrix(stroke, l) ?: return
        val save = canvas.save()
        if (offsetX != 0f || offsetY != 0f) canvas.translate(offsetX, offsetY)
        canvas.concat(m)
        val alpha = (stroke.opacity.coerceIn(0f, 1f) * 255).toInt()
        if (highlightSlots && l.slots.isNotEmpty()) {
            val hp = linePaints.get()!!
            hp.reset(); hp.isAntiAlias = true; hp.style = Paint.Style.FILL
            l.slots.forEach { r ->
                hp.color = highlightColor
                hp.alpha = if (r.name == activeSlot) 90 else 38
                val pad = LAYOUT_SIZE * 0.04f
                canvas.drawRoundRect(RectF(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad), LAYOUT_SIZE * 0.06f, LAYOUT_SIZE * 0.06f, hp)
            }
        }
        val tp = textPaints.get()!!
        val lp = linePaints.get()!!
        for (op in l.ops) {
            when (op) {
                is DrawOp.Glyph -> {
                    tp.reset(); tp.isAntiAlias = true; tp.isSubpixelText = true
                    tp.textSize = op.size
                    tp.typeface = typefaces.getValue(op.style)
                    tp.color = color
                    tp.alpha = alpha
                    canvas.drawText(op.text, op.x, op.y, tp)
                }
                is DrawOp.Rule -> {
                    lp.reset(); lp.isAntiAlias = true; lp.style = Paint.Style.STROKE
                    lp.strokeWidth = max(0.5f, op.thickness); lp.strokeCap = Paint.Cap.BUTT
                    lp.color = color; lp.alpha = alpha
                    canvas.drawLine(op.x1, op.y1, op.x2, op.y2, lp)
                }
                is DrawOp.Stroke -> {
                    val pts = op.points
                    if (pts.size < 4) continue
                    val path = Path()
                    path.moveTo(pts[0], pts[1])
                    var i = 2
                    while (i + 1 < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
                    if (op.closed || op.filled) path.close()
                    lp.reset(); lp.isAntiAlias = true
                    lp.color = color; lp.alpha = alpha
                    if (op.filled) {
                        lp.style = Paint.Style.FILL
                    } else {
                        lp.style = Paint.Style.STROKE
                        lp.strokeWidth = max(0.5f, op.thickness)
                        lp.strokeCap = Paint.Cap.ROUND; lp.strokeJoin = Paint.Join.ROUND
                    }
                    canvas.drawPath(path, lp)
                }
            }
        }
        canvas.restoreToCount(save)
    }
}
