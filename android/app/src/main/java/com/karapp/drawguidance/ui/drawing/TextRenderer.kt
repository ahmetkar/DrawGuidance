package com.karapp.drawguidance.ui.drawing

import android.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.data.model.TextSpec
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/**
 * Yazı elemanlarının ölçümü ve çizimi. Yazı [Stroke.sizePx] boyutunda satırlara dizilir (satırlar Enter ile
 * ayrılır) ve bu düzen, elemanın 4 köşesine (taşıma/döndürme/ölçekleme sonrası dörtgen) eşlenerek çizilir.
 * Düzenleyici, küçük resimler, PNG çıktısı ve silinme denetimi aynı kodu kullanır.
 */
object TextRenderer {

    /** Kutu kenarı ile yazı arasındaki boşluk (yazı boyutunun katı). */
    private const val PADDING = 0.12f
    /** v23 arka planlı yazı kutusunda boşluk. */
    private const val BOX_PADDING = 0.4f

    private data class Key(val spec: TextSpec, val sizePx: Float, val color: Int)

    /**
     * v23 iç boşluklar (sol, üst, sağ, alt; yazı birimi). Kutusuz yazıda yazı boyutunun küçük bir katı; arka planlı
     * kutuda biraz daha geniş; hazır dokuz parçalı resimde resmin yazı alanı (spiral, bant gibi süsler yazının altında
     * kalmaz).
     */
    fun pads(spec: TextSpec, sizePx: Float): FloatArray {
        val size = effectiveSize(spec, sizePx)
        val base = size * PADDING
        val box = spec.box ?: return floatArrayOf(base, base, base, base)
        // v27 konuşma balonu: yazı balonun içinde kalsın (oval biçimlerde kenarlar daha geniş)
        box.balloon?.let { b -> return BalloonRenderer.pads(b, box.width ?: (size * 8f), size) }
        box.padding?.let { p -> return floatArrayOf(p, p, p, p) }
        BackgroundRenderer.boxContent(box)?.let { c ->
            val k = box.patternScale
            return FloatArray(4) { max(base, c[it] * k) }
        }
        val p = if (box.hasBackground) max(base, size * BOX_PADDING) else base
        return floatArrayOf(p, p, p, p)
    }

    private val layouts = LruCache<Key, StaticLayout>(64)

    private val TR = java.util.Locale("tr", "TR")

    private fun typeface(spec: TextSpec): Typeface {
        val bold = spec.bold || spec.paragraph.heading
        val style = when {
            bold && spec.italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            spec.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return FontRegistry.typeface(spec.font, style)
    }

    /** Başlıklar yazı boyutunun katı kadar büyük yazılır. */
    fun effectiveSize(spec: TextSpec, sizePx: Float): Float = sizePx * spec.paragraph.sizeFactor

    /** Yazının düzeni (aynı ayarlar için önbellekten; çizim iş parçacıkları arasında paylaşılabilir). */
    @Synchronized
    private fun layout(spec: TextSpec, sizePx: Float, color: Int): StaticLayout {
        val key = Key(spec, sizePx, color)
        layouts.get(key)?.let { return it }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = max(1f, effectiveSize(spec, sizePx))
            typeface = typeface(spec)
            isUnderlineText = spec.underline
            // Sistemde eğik yazı tipi yoksa yapay eğim
            if (spec.italic && !typeface.isItalic) textSkewX = -0.25f
            if ((spec.bold || spec.paragraph.heading) && !typeface.isBold) isFakeBoldText = true
            this.color = color
            // v27 harf aralığı (em)
            spec.box?.effects?.letterSpacing?.takeIf { it != 0f }?.let { letterSpacing = it.coerceIn(-0.2f, 1f) }
        }
        // v27 büyük harf (Türkçe kurallarıyla: i → İ)
        val text = spec.content.ifEmpty { " " }.let { if (spec.box?.effects?.caps == true) it.uppercase(TR) else it }
        // v23 genişliği verilmiş yazı kutusunda satırlar kutuya göre kaydırılır
        val boxWidth = spec.box?.width?.let { bw ->
            val pad = pads(spec, sizePx)
            (bw - pad[0] - pad[2]).coerceAtLeast(max(1f, paint.textSize))
        }
        val width = boxWidth?.let { ceil(it).toInt() - 2 } ?: text.split('\n').maxOf { ceil(paint.measureText(it)).toInt() }.coerceAtLeast(1)
        val alignment = when (spec.align) {
            TextAlignKind.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlignKind.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignKind.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        // Android 6.0 öncesinde Builder yok: eski kurucu (bkz. Compat)
        val result = com.karapp.drawguidance.platform.Compat.staticLayout(text, paint, width + 2, alignment, includePad = true)
        layouts.put(key, result)
        return result
    }

    /** Yazı kutusunun (boşluklar dahil) tuval birimindeki genişliği ve yüksekliği. */
    fun measure(spec: TextSpec, sizePx: Float): Pair<Float, Float> {
        // v42 yol üzerinde yazı: kutu yolun kutusudur
        spec.path?.let { return it.w to it.h }
        val l = layout(spec, sizePx, 0xFF000000.toInt())
        val pad = pads(spec, sizePx)
        val box = spec.box
        val w = box?.width ?: (l.width + pad[0] + pad[2])
        val h = max(l.height + pad[1] + pad[3], box?.height ?: 0f)
        return w to h
    }

    /** Sol-üst köşesi [x],[y] olan, döndürülmemiş yazı kutusunun 4 köşesi. */
    fun quadAt(spec: TextSpec, sizePx: Float, x: Float, y: Float): List<StrokePoint> {
        val (w, h) = measure(spec, sizePx)
        return listOf(StrokePoint(x, y), StrokePoint(x + w, y), StrokePoint(x + w, y + h), StrokePoint(x, y + h))
    }

    /**
     * Ayarları değişen yazının yeni kutusu: sol-üst köşe, yön (döndürme) ve önceki ölçeklemedeki
     * en/boy esnemesi korunur; kutu yeni yazı düzeninin boyutuna göre büyür/küçülür.
     */
    fun refit(old: Stroke, spec: TextSpec, sizePx: Float): List<StrokePoint> {
        val q = old.points
        val oldSpec = old.text ?: return quadAt(spec, sizePx, q.firstOrNull()?.x ?: 0f, q.firstOrNull()?.y ?: 0f)
        if (q.size < 4) return quadAt(spec, sizePx, q.firstOrNull()?.x ?: 0f, q.firstOrNull()?.y ?: 0f)
        val (ow, oh) = measure(oldSpec, old.sizePx)
        val (nw, nh) = measure(spec, sizePx)
        val ux = q[1].x - q[0].x; val uy = q[1].y - q[0].y
        val vx = q[3].x - q[0].x; val vy = q[3].y - q[0].y
        val ulen = hypot(ux, uy); val vlen = hypot(vx, vy)
        if (ulen < 1e-3f || vlen < 1e-3f) return quadAt(spec, sizePx, q[0].x, q[0].y)
        val su = ulen / ow; val sv = vlen / oh
        val ax = ux / ulen * nw * su; val ay = uy / ulen * nw * su
        val bx = vx / vlen * nh * sv; val by = vy / vlen * nh * sv
        val tl = q[0]
        return listOf(
            StrokePoint(tl.x, tl.y),
            StrokePoint(tl.x + ax, tl.y + ay),
            StrokePoint(tl.x + ax + bx, tl.y + ay + by),
            StrokePoint(tl.x + bx, tl.y + by),
        )
    }

    /** Yazıyı elemanın dörtgenine çizer; [coverage] silinme denetimi için tam opak siyah çizer. */
    fun draw(canvas: Canvas, stroke: Stroke, color: Int, offsetX: Float = 0f, offsetY: Float = 0f, coverage: Boolean = false) {
        val spec = stroke.text ?: return
        if (stroke.points.size < 4 || spec.content.isEmpty()) return
        // Görünür alanın dışındaki yazılar çizilmez (yazı düzeni de kurulmaz)
        val clip = android.graphics.Rect()
        if (canvas.getClipBounds(clip)) {
            var bl = Float.MAX_VALUE; var bt = Float.MAX_VALUE; var br = -Float.MAX_VALUE; var bb = -Float.MAX_VALUE
            stroke.points.forEach {
                bl = minOf(bl, it.x + offsetX); bt = minOf(bt, it.y + offsetY)
                br = maxOf(br, it.x + offsetX); bb = maxOf(bb, it.y + offsetY)
            }
            if (br < clip.left || bl > clip.right || bb < clip.top || bt > clip.bottom) return
        }
        val alpha = if (coverage) 255 else (stroke.opacity.coerceIn(0f, 1f) * 255).toInt()
        val ink = if (coverage) 0xFF000000.toInt() else (color and 0x00FFFFFF) or (alpha shl 24)
        spec.path?.let { drawOnPath(canvas, stroke, spec, it, ink, alpha, offsetX, offsetY, coverage); return }
        val l = layout(spec, stroke.sizePx, ink)
        val pad = pads(spec, stroke.sizePx)
        val (w, h) = measure(spec, stroke.sizePx)
        val src = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        val dst = FloatArray(8)
        for (i in 0 until 4) {
            dst[i * 2] = stroke.points[i].x + offsetX
            dst[i * 2 + 1] = stroke.points[i].y + offsetY
        }
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return
        val save = canvas.save()
        canvas.concat(matrix)
        // v23 kutunun arka planı (renk, resim, desen, kenar) yazının altında; v27 konuşma balonu ve gradyan
        spec.box?.let { box ->
            val balloon = box.balloon
            if (balloon != null) BalloonRenderer.draw(canvas, balloon, w, h, effectiveSize(spec, stroke.sizePx), alpha / 255f, coverage, box.fillGradient)
            else BackgroundRenderer.drawBox(canvas, box, w, h, 0xFFFFFFFF.toInt(), alpha / 255f, coverage)
        }
        canvas.translate(pad[0], pad[1])
        val fx = spec.box?.effects?.takeUnless { coverage }
        val size = effectiveSize(spec, stroke.sizePx)
        synchronized(l) {
            val paint = l.paint
            // v27 gölge: kenarlıkta (varsa) ya da dolguda; donanım hızlandırmalı yazı gölgesi (her sürümde)
            val shadow = fx?.shadowHex?.let { hex ->
                val c = com.karapp.drawguidance.ui.common.hexToColor(hex).toArgb()
                floatArrayOf(max(0.01f, fx.shadowBlur * size), fx.shadowDx * size, fx.shadowDy * size, Float.fromBits(
                    (((c ushr 24) * alpha / 255) shl 24) or (c and 0x00FFFFFF),
                ))
            }
            // Harf kenarlığı: önce kalın çizgi (yarısı dışarıda kalır), üstüne dolgu (yazı rengi)
            val outline = spec.outlineColorHex
            val hasOutline = outline != null && spec.outlineWidth > 0.05f
            if (hasOutline) {
                val style = paint.style
                val color = paint.color
                val width = paint.strokeWidth
                val join = paint.strokeJoin
                paint.style = Paint.Style.STROKE
                paint.strokeJoin = Paint.Join.ROUND
                paint.strokeWidth = spec.outlineWidth * 2f
                paint.color = if (coverage) 0xFF000000.toInt()
                else (com.karapp.drawguidance.ui.common.hexToColor(outline!!).toArgb() and 0x00FFFFFF) or (alpha shl 24)
                shadow?.let { paint.setShadowLayer(it[0], it[1], it[2], it[3].toRawBits()) }
                l.draw(canvas)
                paint.clearShadowLayer()
                paint.style = style
                paint.color = color
                paint.strokeWidth = width
                paint.strokeJoin = join
            }
            // v27 gradyan dolgu: yazı bloğunun kutusuna göre
            val gradient = fx?.gradient?.let { Gradients.shader(it, 0f, 0f, l.width.toFloat(), l.height.toFloat(), alpha / 255f) }
            if (gradient != null) paint.shader = gradient
            if (!hasOutline) shadow?.let { paint.setShadowLayer(it[0], it[1], it[2], it[3].toRawBits()) }
            l.draw(canvas)
            paint.clearShadowLayer()
            paint.shader = null
        }
        canvas.restoreToCount(save)
    }

    /**
     * v42 yol üzerinde yazı: [pathSpec] noktaları kutunun yerel düzlemindedir; kutu elemanın dörtgenine eşlenir
     * (taşıma, döndürme, ölçekleme yolu da dönüştürür). Yazı tek satırdır; harf kenarlığı ve gölge desteklenir.
     */
    private fun drawOnPath(
        canvas: Canvas, stroke: Stroke, spec: TextSpec, pathSpec: com.karapp.drawguidance.data.model.TextPathSpec,
        ink: Int, alpha: Int, offsetX: Float, offsetY: Float, coverage: Boolean,
    ) {
        if (pathSpec.points.size < 2 || pathSpec.w <= 0f || pathSpec.h <= 0f) return
        val src = floatArrayOf(0f, 0f, pathSpec.w, 0f, pathSpec.w, pathSpec.h, 0f, pathSpec.h)
        val dst = FloatArray(8)
        for (i in 0 until 4) {
            dst[i * 2] = stroke.points[i].x + offsetX
            dst[i * 2 + 1] = stroke.points[i].y + offsetY
        }
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return
        val path = pathOf(pathSpec)
        val paint = pathPaint(spec, stroke.sizePx, ink)
        val text = singleLine(spec)
        val (h, v) = offsets(spec, pathSpec, path, paint, text)
        val save = canvas.save()
        canvas.concat(matrix)
        val fx = spec.box?.effects?.takeUnless { coverage }
        val size = effectiveSize(spec, stroke.sizePx)
        fx?.shadowHex?.let { hex ->
            val c = com.karapp.drawguidance.ui.common.hexToColor(hex).toArgb()
            paint.setShadowLayer(max(0.01f, fx.shadowBlur * size), fx.shadowDx * size, fx.shadowDy * size, (((c ushr 24) * alpha / 255) shl 24) or (c and 0x00FFFFFF))
        }
        val outline = spec.outlineColorHex
        if (outline != null && spec.outlineWidth > 0.05f) {
            val o = Paint(paint)
            o.style = Paint.Style.STROKE
            o.strokeJoin = Paint.Join.ROUND
            o.strokeWidth = spec.outlineWidth * 2f
            o.color = if (coverage) 0xFF000000.toInt() else (com.karapp.drawguidance.ui.common.hexToColor(outline).toArgb() and 0x00FFFFFF) or (alpha shl 24)
            canvas.drawTextOnPath(text, path, h, v, o)
            paint.clearShadowLayer()
        }
        canvas.drawTextOnPath(text, path, h, v, paint)
        canvas.restoreToCount(save)
    }

    /** Yolun (yerel) çizgisi; [com.karapp.drawguidance.data.model.TextPathSpec.reversed] ise ters yönde. */
    fun pathOf(spec: com.karapp.drawguidance.data.model.TextPathSpec): android.graphics.Path {
        val pts = if (spec.reversed) spec.points.asReversed() else spec.points
        return android.graphics.Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            if (spec.closed) close()
        }
    }

    /** Yol yazısının boyası (yazı tipi, kalınlık, eğiklik, boyut, harf aralığı). */
    fun pathPaint(spec: TextSpec, sizePx: Float, ink: Int): android.text.TextPaint =
        android.text.TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = typeface(spec)
            textSize = effectiveSize(spec, sizePx)
            color = ink
            isUnderlineText = spec.underline
            spec.box?.effects?.letterSpacing?.takeIf { it != 0f }?.let { letterSpacing = it }
        }

    /** Yol yazısı tek satır (satır sonları boşluk olur); büyük harf efekti uygulanır. */
    fun singleLine(spec: TextSpec): String {
        val t = spec.content.replace('\n', ' ')
        return if (spec.box?.effects?.caps == true) t.uppercase(java.util.Locale.getDefault()) else t
    }

    /** Yazının yoldaki başlangıcı (yatay) ve yola uzaklığı (dikey): hizalama ve [com.karapp.drawguidance.data.model.TextPathSpec.below]. */
    private fun offsets(
        spec: TextSpec, pathSpec: com.karapp.drawguidance.data.model.TextPathSpec, path: android.graphics.Path, paint: Paint, text: String,
    ): Pair<Float, Float> {
        val length = android.graphics.PathMeasure(path, false).length
        val width = paint.measureText(text)
        val start = pathSpec.start.coerceIn(0f, 1f) * length
        val h = when (spec.align) {
            com.karapp.drawguidance.data.model.TextAlignKind.LEFT -> start
            com.karapp.drawguidance.data.model.TextAlignKind.CENTER -> (length - width) / 2f + start
            com.karapp.drawguidance.data.model.TextAlignKind.RIGHT -> length - width - start
        }
        val fm = paint.fontMetrics
        // Yolun üstünde: taban çizgisi yolun biraz üstünde; altında: harflerin tepesi yolun biraz altında
        val v = if (pathSpec.below) -fm.ascent + paint.textSize * 0.08f else -paint.textSize * 0.12f
        return h to v
    }
}
