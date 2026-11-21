package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.karapp.drawguidance.data.images.CanvasImageCache
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.isObject
import com.karapp.drawguidance.data.model.isRaster
import com.karapp.drawguidance.ui.common.hexToColor
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tüm çizim yüzeylerinin (düzenleme ekranı, kart önizlemesi, fırça örnekleri,
 * PNG dışa aktarma) kullandığı tek çizim motoru. Koordinatlar tuval birimidir;
 * ekrana ölçekleme çağıran tarafça yapılır.
 */
object StrokeRenderer {

    /**
     * Tek bir çizimi fırça stiline göre çizer. [offset] taşıma sırasındaki geçici kaydırmadır.
     * Elemana kırpma uygulanmışsa yalnızca kırpma dörtgeninin içi çizilir.
     */
    fun DrawScope.drawStroke(stroke: Stroke, offset: Offset = Offset.Zero) {
        // v56 2E nesne karışım modu ve opaklık maskesi: eleman ara katmanda
        val st = stroke.style2
        if (st != null && (st.blend != com.karapp.drawguidance.data.model.LayerBlend.NORMAL || !st.opacityMask.isNullOrEmpty())) {
            drawStyled(stroke, offset, st); return
        }
        drawStrokeBody(stroke, offset)
    }

    private val luminanceToAlpha = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0.2126f, 0.7152f, 0.0722f, 0f, 0f,
    )))

    private fun DrawScope.drawStyled(stroke: Stroke, offset: Offset, st: com.karapp.drawguidance.data.model.VectorStyle) {
        val bounds = Rect(-1e6f, -1e6f, 1e6f, 1e6f)
        drawIntoCanvas { it.saveLayer(bounds, Paint().apply { blendMode = LayerCompositor.blendMode(st.blend) }) }
        drawStrokeBody(stroke, offset)
        val masks = st.opacityMask
        if (!masks.isNullOrEmpty()) {
            // Maske siyah zeminde çizilir; parlaklığı alfa olur ve DstIn ile elemanı keser
            drawIntoCanvas { c ->
                c.saveLayer(bounds, Paint().apply { blendMode = androidx.compose.ui.graphics.BlendMode.DstIn; colorFilter = luminanceToAlpha })
                c.nativeCanvas.drawColor(android.graphics.Color.BLACK)
            }
            masks.forEach { drawStroke(it.copy(style2 = it.style2?.copy(opacityMask = null)), offset) }
            drawIntoCanvas { it.restore() }
        }
        drawIntoCanvas { it.restore() }
    }

    private fun DrawScope.drawStrokeBody(stroke: Stroke, offset: Offset) {
        val mask = stroke.mask
        if (mask != null && mask.points.size >= 3) { drawMasked(stroke, offset, mask); return }
        drawClipped(stroke, offset)
    }

    /**
     * v56 seçim maskesi: sert kenarda `clipPath` (ters seçimde çift-tek kuralıyla dışı); yumuşatılmışta çizim ara
     * katmanda, bulanık maske `DstIn` ile uygulanır.
     */
    private fun DrawScope.drawMasked(stroke: Stroke, offset: Offset, mask: com.karapp.drawguidance.data.model.ClipMask) {
        val path = Path().apply {
            moveTo(mask.points[0].x + offset.x, mask.points[0].y + offset.y)
            for (i in 1 until mask.points.size) lineTo(mask.points[i].x + offset.x, mask.points[i].y + offset.y)
            close()
        }
        if (mask.feather <= 0.5f) {
            if (mask.inverted) {
                path.fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                path.addRect(Rect(-1e6f, -1e6f, 1e6f, 1e6f))
            }
            clipPath(path) { drawClipped(stroke, offset) }
            return
        }
        val bounds = Rect(-1e6f, -1e6f, 1e6f, 1e6f)
        drawIntoCanvas { it.saveLayer(bounds, Paint()) }
        drawClipped(stroke, offset)
        // Maske ayrı ara katmanda (dışı saydam) ve DstIn ile birleşir: maskenin dışı tamamen silinir
        drawIntoCanvas { c ->
            c.saveLayer(bounds, Paint().apply { blendMode = androidx.compose.ui.graphics.BlendMode.DstIn })
            val blur = android.graphics.BlurMaskFilter(mask.feather, android.graphics.BlurMaskFilter.Blur.NORMAL)
            if (mask.inverted) {
                c.nativeCanvas.drawColor(android.graphics.Color.BLACK)
                val cut = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT); maskFilter = blur
                }
                c.nativeCanvas.drawPath(path.asAndroidPath(), cut)
            } else {
                c.nativeCanvas.drawPath(path.asAndroidPath(), android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { maskFilter = blur })
            }
            c.restore()
            c.restore()
        }
    }

    private fun DrawScope.drawClipped(stroke: Stroke, offset: Offset) {
        // v56 yansıma: dalga (vektörde noktalar, resimde ağ) ve alfa gradyanı
        val wave = stroke.warp?.wave
        if (wave != null && WarpRenderer.hasWave(stroke)) {
            val body = if (stroke.brushType == BrushType.IMAGE) stroke else WarpRenderer.displaced(stroke)
            val b = paintBounds(stroke)
            drawIntoCanvas { c ->
                WarpRenderer.withFade(c.nativeCanvas, wave, (b?.top ?: 0f) + offset.y, (b?.bottom ?: 0f) + offset.y) { drawGrained(body, offset) }
            }
            return
        }
        drawGrained(stroke, offset)
    }

    private fun DrawScope.drawGrained(stroke: Stroke, offset: Offset) {
        // v56 gren: iz ara katmanda çizilip dokuyla çarpılır
        val grain = GrainRenderer.grainOf(stroke)
        if (grain != null && !stroke.brushType.isObject) {
            val first = stroke.points[0]
            drawIntoCanvas { c ->
                GrainRenderer.withGrain(c.nativeCanvas, grain, first.x + offset.x, first.y + offset.y) { drawClippedInner(stroke, offset) }
            }
            return
        }
        drawClippedInner(stroke, offset)
    }

    private fun DrawScope.drawClippedInner(stroke: Stroke, offset: Offset) {
        val clip = stroke.clip
        if (clip != null && clip.size == 4) {
            val path = Path().apply {
                moveTo(clip[0].x + offset.x, clip[0].y + offset.y)
                for (i in 1 until 4) lineTo(clip[i].x + offset.x, clip[i].y + offset.y)
                close()
            }
            clipPath(path) { drawUnclipped(stroke, offset) }
            return
        }
        drawUnclipped(stroke, offset)
    }

    private fun DrawScope.drawUnclipped(stroke: Stroke, offset: Offset) {
        if (stroke.brushType == BrushType.LASSO_FILL) {
            // v56 kement dolgu: kapalı yolun içi (renk ve opaklık)
            val pts = stroke.points
            if (pts.size >= 3) {
                val path = Path().apply {
                    moveTo(pts[0].x + offset.x, pts[0].y + offset.y)
                    for (i in 1 until pts.size) lineTo(pts[i].x + offset.x, pts[i].y + offset.y)
                    close()
                }
                drawPath(path, hexToColor(stroke.colorHex).let { it.copy(alpha = it.alpha * stroke.opacity.coerceIn(0f, 1f)) })
            }
            return
        }
        if (stroke.brushType == BrushType.IMAGE) {
            drawImageStroke(stroke, offset)
            return
        }
        if (stroke.brushType == BrushType.SHAPE) {
            drawIntoCanvas { ShapeRenderer.draw(it.nativeCanvas, stroke, offset.x, offset.y) }
            return
        }
        if (stroke.brushType == BrushType.DIAGRAM) {
            drawIntoCanvas { DiagramRenderer.draw(it.nativeCanvas, stroke, offset.x, offset.y) }
            return
        }
        if (stroke.brushType == BrushType.TEXT) {
            drawIntoCanvas { TextRenderer.draw(it.nativeCanvas, stroke, hexToColor(stroke.colorHex).toArgb(), offset.x, offset.y) }
            return
        }
        if (stroke.brushType == BrushType.FORMULA) {
            drawIntoCanvas { FormulaRenderer.draw(it.nativeCanvas, stroke, hexToColor(stroke.colorHex).toArgb(), offset.x, offset.y) }
            return
        }
        val n = stroke.points.size
        if (n == 0) return
        // v56 resimden fırça: uç resmi iz boyunca damgalanır (uç yüklenmediyse fırçanın kendi ucu)
        if (stroke.params?.stampAssetId != null && !stroke.brushType.isRaster) {
            var drawn = false
            drawIntoCanvas { drawn = GrainRenderer.drawStamps(it.nativeCanvas, stroke, offset.x, offset.y, hexToColor(stroke.colorHex).toArgb()) }
            if (drawn) return
        }
        // v51 piksel tabanlı fırça: bağlamsız çizim (önizleme, bekleyen çizim); listede bağlamıyla çizilir
        if (stroke.brushType.isRaster) {
            drawIntoCanvas { c ->
                val save = c.nativeCanvas.save()
                if (offset != Offset.Zero) c.nativeCanvas.translate(offset.x, offset.y)
                RasterStrokes.draw(c.nativeCanvas, stroke, null)
                c.nativeCanvas.restoreToCount(save)
            }
            return
        }
        // Bitmiş fırça izi bir kez kaydedilip (Picture) sonraki çizimlerde yalnızca oynatılır: yakınlaştırma,
        // kaydırma ya da geri alma sonrası yeniden çizimde fırça motoru (örnekleme, damgalar) tekrar çalışmaz
        if (offset == Offset.Zero && pictureCacheEnabled) {
            val picture = pictureOf(stroke)
            if (picture != null) {
                drawIntoCanvas { it.nativeCanvas.drawPicture(picture.picture) }
                return
            }
        }
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        for (i in 0 until n) {
            xs[i] = stroke.points[i].x + offset.x
            ys[i] = stroke.points[i].y + offset.y
        }
        val dyn = BrushPainter.dynamicsOf(stroke.points)
        val (ps, ts, os) = dyn
        drawIntoCanvas {
            BrushPainter.paint(
                it.nativeCanvas, xs, ys, n, hexToColor(stroke.colorHex).toArgb(), stroke.brushType,
                stroke.sizePx, stroke.opacity, stroke.id.hashCode(), stroke.flow, stroke.strength, ps, ts, stroke.params, os,
                dyn.rolls, dyn.barrels,
            )
        }
    }

    /** Kaydedilmiş fırça izi: tuvaldeki sol-üst köşesi ve çizim komutları. */
    private class StrokePicture(val source: Stroke, val left: Float, val top: Float, val picture: android.graphics.Picture)

    private const val PICTURE_BUDGET = 400_000

    /** Test ve karşılaştırma için kapatılabilir. */
    @Volatile var pictureCacheEnabled: Boolean = true

    /** Kayıtlı izlerin toplam ağırlığı (nokta sayısı × motor katsayısı) sınırlıdır: eskiler atılır. */
    private val pictures = object : android.util.LruCache<String, StrokePicture>(PICTURE_BUDGET) {
        override fun sizeOf(key: String, value: StrokePicture): Int = pictureWeight(value.source)
    }.also { c -> com.karapp.drawguidance.platform.DeviceCaps.onTrim { c.evictAll() } }
    init { com.karapp.drawguidance.platform.DeviceCaps.cache(editor = true) { synchronized(pictures) { pictures.evictAll() } } }

    private fun pictureWeight(stroke: Stroke): Int {
        val engine = stroke.brushType.style().engine
        val factor = if (engine == BrushEngine.PATH || engine == BrushEngine.TAPERED_PATH || engine == BrushEngine.RIBBON) 1 else 6
        return (stroke.points.size * factor).coerceAtLeast(1)
    }

    /** Aynı görünüm (nokta listesi, renk, fırça ayarları) için kayıtlı iz; yoksa kaydeder. */
    private fun pictureOf(stroke: Stroke): StrokePicture? {
        val cached = pictures.get(stroke.id)
        if (cached != null) {
            val s = cached.source
            if (s === stroke || (s.points === stroke.points && s.colorHex == stroke.colorHex && s.brushType == stroke.brushType &&
                    s.sizePx == stroke.sizePx && s.opacity == stroke.opacity && s.flow == stroke.flow && s.strength == stroke.strength &&
                    s.params == stroke.params)
            ) return cached
        }
        if (pictureWeight(stroke) > PICTURE_BUDGET / 8) return null
        val bounds = paintBounds(stroke) ?: return null
        // Tuval koordinatlarında kaydedilir (kâğıda sabit dokular aynı yerde kalsın); tuvalin solunda/üstünde
        // taşan izler kaydedilmez, doğrudan çizilir
        if (bounds.left < 0f || bounds.top < 0f) return null
        val w = kotlin.math.ceil(bounds.right).toInt().coerceAtLeast(1)
        val h = kotlin.math.ceil(bounds.bottom).toInt().coerceAtLeast(1)
        if (w > 32768 || h > 32768) return null
        val n = stroke.points.size
        val xs = FloatArray(n) { stroke.points[it].x }
        val ys = FloatArray(n) { stroke.points[it].y }
        val picture = android.graphics.Picture()
        val canvas = picture.beginRecording(w, h)
        val dyn = BrushPainter.dynamicsOf(stroke.points)
        val (ps, ts, os) = dyn
        BrushPainter.paint(
            canvas, xs, ys, n, hexToColor(stroke.colorHex).toArgb(), stroke.brushType,
            stroke.sizePx, stroke.opacity, stroke.id.hashCode(), stroke.flow, stroke.strength, ps, ts, stroke.params, os,
            dyn.rolls, dyn.barrels,
        )
        picture.endRecording()
        val entry = StrokePicture(stroke, bounds.left, bounds.top, picture)
        pictures.put(stroke.id, entry)
        return entry
    }

    fun DrawScope.drawStroke(
        points: List<Offset>,
        color: Color,
        brushType: BrushType,
        sizePx: Float,
        opacity: Float,
        seed: Int,
        flow: Float = 1f,
        strength: Float = 1f,
        params: com.karapp.drawguidance.data.model.BrushParams? = null,
    ) {
        val n = points.size
        if (n == 0) return
        val xs = FloatArray(n) { points[it].x }
        val ys = FloatArray(n) { points[it].y }
        drawIntoCanvas {
            BrushPainter.paint(it.nativeCanvas, xs, ys, n, color.toArgb(), brushType, sizePx, opacity, seed, flow, strength, params = params)
        }
    }

    /**
     * Çizim listesini sırayla çizer. Silgiler yalnızca kendi katmanlarındaki ve kendilerinden önce çizilmiş fırça
     * izlerini siler; nesneler (şekil, diyagram, yazı, resim, formül) silgiden etkilenmez. Bunun için her katmanın
     * ardışık fırça izleri ayrı bir ara katmanda çizilir ve o katmanın sonraki silgileri de o ara katmana uygulanır.
     * Silgi yoksa ara katman açılmaz (çoğu çizim).
     */
    fun DrawScope.drawStrokeList(
        strokes: List<Stroke>,
        checkpoint: () -> Unit = {},
        /** v56 katmanlar: opaklık, karışım modu ya da kırpma varsa katman katman birleştirilir ([LayerCompositor]). */
        layers: List<com.karapp.drawguidance.data.model.Layer> = emptyList(),
    ) {
        if (strokes.isNotEmpty() && LayerCompositor.needed(layers)) {
            with(LayerCompositor) { drawLayered(strokes, layers) { drawPlainList(it, checkpoint) } }
            return
        }
        drawPlainList(strokes, checkpoint)
    }

    private fun isLocked(s: Stroke): Boolean = s.params?.alphaLocked == true

    private fun DrawScope.drawPlainList(strokes: List<Stroke>, checkpoint: () -> Unit) {
        if (strokes.none { it.brushType == BrushType.ERASER || it.brushType.isRaster || isLocked(it) }) {
            strokes.forEach { checkpoint(); drawStroke(it) }
            return
        }
        val layerBounds = Rect(-1e6f, -1e6f, 1e6f, 1e6f)
        val n = strokes.size
        var i = 0
        while (i < n) {
            val first = strokes[i]
            if (first.brushType.isObject) { checkpoint(); drawStroke(first); i++; continue }
            val layer = first.layerId ?: ""
            // Aynı katmandaki ardışık fırça izleri (ve silgileri): bir nesne ya da katman değişince biter
            var j = i
            while (j < n && !strokes[j].brushType.isObject && (strokes[j].layerId ?: "") == layer) j++
            // Aynı katmanda daha sonra gelen silgiler bu izleri de siler
            var k = j
            val laterErasers = ArrayList<Stroke>()
            while (k < n && (strokes[k].layerId ?: "") == layer) {
                if (strokes[k].brushType == BrushType.ERASER) laterErasers += strokes[k]
                k++
            }
            val runHasInk = (i until j).any { strokes[it].brushType != BrushType.ERASER }
            if (runHasInk) {
                drawIntoCanvas { it.saveLayer(layerBounds, Paint()) }
                for (m in i until j) { checkpoint(); drawInRun(strokes, m, layer) }
                laterErasers.forEach { checkpoint(); drawStroke(it) }
                drawIntoCanvas { it.restore() }
            }
            i = j
        }
    }

    /**
     * v51 katman ara katmanındaki bir çizim: piksel tabanlı fırça izi, aynı katmanda kendisinden önce çizilenleri
     * (nesneler dahil) okuyarak çizilir; diğerleri her zamanki gibi.
     */
    private fun DrawScope.drawInRun(strokes: List<Stroke>, m: Int, layer: String) {
        val s = strokes[m]
        if (isLocked(s) && s.brushType != BrushType.ERASER) {
            // v56 alfa kilidi: yalnız katmanda önceden boyanmış yerlerde (SrcAtop)
            drawIntoCanvas { it.saveLayer(Rect(-1e6f, -1e6f, 1e6f, 1e6f), Paint().apply { blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop }) }
            if (s.brushType.isRaster) drawRaster(strokes, m, layer, s) else drawStroke(s)
            drawIntoCanvas { it.restore() }
            return
        }
        if (!s.brushType.isRaster) { drawStroke(s); return }
        drawRaster(strokes, m, layer, s)
    }

    private fun DrawScope.drawRaster(strokes: List<Stroke>, m: Int, layer: String, s: Stroke) {
        val context = ArrayList<Stroke>(m)
        for (q in 0 until m) { val o = strokes[q]; if ((o.layerId ?: "") == layer && !o.hidden) context += o }
        drawIntoCanvas { c ->
            val clip = s.clip
            val save = c.nativeCanvas.save()
            if (clip != null && clip.size == 4) {
                val path = android.graphics.Path().apply {
                    moveTo(clip[0].x, clip[0].y); for (i in 1 until 4) lineTo(clip[i].x, clip[i].y); close()
                }
                c.nativeCanvas.clipPath(path)
            }
            // v56 seçim maskesi (piksel fırçasında sert kenar)
            s.mask?.takeIf { it.points.size >= 3 }?.let { m ->
                val mp = android.graphics.Path().apply {
                    moveTo(m.points[0].x, m.points[0].y); for (i in 1 until m.points.size) lineTo(m.points[i].x, m.points[i].y); close()
                    if (m.inverted) { fillType = android.graphics.Path.FillType.EVEN_ODD; addRect(-1e6f, -1e6f, 1e6f, 1e6f, android.graphics.Path.Direction.CW) }
                }
                c.nativeCanvas.clipPath(mp)
            }
            val grain = GrainRenderer.grainOf(s)
            if (grain != null) {
                GrainRenderer.withGrain(c.nativeCanvas, grain, s.points[0].x, s.points[0].y) { RasterStrokes.draw(c.nativeCanvas, s, context) }
            } else {
                RasterStrokes.draw(c.nativeCanvas, s, context)
            }
            c.nativeCanvas.restoreToCount(save)
        }
    }

    /**
     * Kağıt + tüm çizimler. Çizimler ayrı bir katmanda toplanır ki silgi
     * (BlendMode.Clear) sadece çizimleri silsin, kağıdı değil.
     */
    fun DrawScope.drawCanvasContent(
        background: Color,
        strokes: List<Stroke>,
        canvasWidth: Float,
        canvasHeight: Float,
        /** v23 arka plan resmi ve deseni (rengin üstünde; vektör olarak). */
        decor: com.karapp.drawguidance.data.model.BackgroundSpec? = null,
        /** v56 katmanlar (opaklık, karışım modu, kırpma). */
        layers: List<com.karapp.drawguidance.data.model.Layer> = emptyList(),
        underlay: (DrawScope.() -> Unit)? = null,
    ) {
        val bounds = Rect(0f, 0f, canvasWidth, canvasHeight)
        drawRect(background, topLeft = Offset.Zero, size = bounds.size)
        if (decor != null && !decor.isEmpty) drawIntoCanvas {
            BackgroundRenderer.drawCanvasBackground(it.nativeCanvas, decor, 0f, 0f, canvasWidth, canvasHeight, 1f, background.toArgb(), vector = true)
        }
        underlay?.invoke(this)
        // v52 piksel fırçaları bu çizimin kağıdını okur
        val previousPaper = RasterStrokes.paperOverride.get()
        RasterStrokes.paperOverride.set(background.toArgb())
        try { withStrokeLayer(bounds) { drawStrokeList(strokes, layers = layers) } } finally { RasterStrokes.paperOverride.set(previousPaper) }
    }

    /** [bounds] o anki (dönüştürülmüş) koordinat sistemindeki katman sınırıdır. */
    inline fun DrawScope.withStrokeLayer(bounds: Rect, block: DrawScope.() -> Unit) {
        drawIntoCanvas { it.saveLayer(bounds, Paint()) }
        block()
        drawIntoCanvas { it.restore() }
    }

    /** Çizimin boyadığı alanın önbelleği (nokta listesi aynı kaldıkça yeniden hesaplanmaz). */
    private class PaintBounds(
        val points: List<com.karapp.drawguidance.data.model.StrokePoint>, val size: Float, val rect: Rect,
        val params: com.karapp.drawguidance.data.model.BrushParams? = null,
        val warp: com.karapp.drawguidance.data.model.ImageWarp? = null,
    )

    private val paintBounds = object : android.util.LruCache<String, PaintBounds>(8192) {}
    init { com.karapp.drawguidance.platform.DeviceCaps.cache(editor = true) { paintBounds.evictAll() } }

    /**
     * Çizimin ekrana boya bırakabileceği en geniş alan (tuval birimi): noktaların kutusu + fırça kalınlığı ve
     * saçılma payı. Görünür alanın dışındaki çizimler hiç işlenmez (fırça motoru çalışmaz).
     */
    fun paintBounds(stroke: Stroke): Rect? {
        val pts = stroke.points
        if (pts.isEmpty()) return null
        paintBounds.get(stroke.id)?.let { if (it.points === pts && it.size == stroke.sizePx && it.params == stroke.params && it.warp == stroke.warp) return it.rect }
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (p in pts) {
            if (p.x < l) l = p.x
            if (p.x > r) r = p.x
            if (p.y < t) t = p.y
            if (p.y > b) b = p.y
        }
        val pad = when (stroke.brushType) {
            BrushType.IMAGE, BrushType.TEXT, BrushType.DIAGRAM, BrushType.FORMULA -> 2f
            BrushType.SHAPE -> (stroke.shape?.style?.strokeWidth ?: stroke.sizePx) + 4f +
                // Eksen etiketleri ve formül yazısı noktanın sağına/altına taşar
                (stroke.shape?.subpaths?.maxOfOrNull { sub -> sub.label?.let { l -> sub.labelSize * (l.length + 1) } ?: 0f } ?: 0f)
            // Fırça izleri: kalınlık, doku ve saçılma (serpinti, sprey) payı; v20 eğimle genişleme ve 3B gölge
            else -> stroke.sizePx * stroke.brushType.reachFactor(stroke.params) + 4f +
                // v21 3B çizim modunda katı gövde kabarma yönünde uzanır
                Iso3D.extrudeReach(stroke.params?.depth3d, stroke.brushType.inkWidth(stroke.sizePx, stroke.strength))
        }
        // v56 Çarpıtma ağı noktaları ve yansıma dalgası kutunun dışına taşabilir
        stroke.warp?.mesh?.forEach { p -> if (p.x < l) l = p.x; if (p.x > r) r = p.x; if (p.y < t) t = p.y; if (p.y > b) b = p.y }
        val wavePad = stroke.warp?.wave?.amplitude?.let { kotlin.math.abs(it) * 1.5f } ?: 0f
        val rect = Rect(l - pad - wavePad, t - pad - wavePad, r + pad + wavePad, b + pad + wavePad)
        paintBounds.put(stroke.id, PaintBounds(pts, stroke.sizePx, rect, stroke.params, stroke.warp))
        return rect
    }

    /** Görünür bölgeye (tuval birimi) değen çizimler. */
    fun visibleIn(strokes: List<Stroke>, visible: Rect): List<Stroke> {
        var allVisible = true
        for (s in strokes) {
            val r = paintBounds(s)
            if (r == null || !r.overlaps(visible)) { allVisible = false; break }
        }
        if (allVisible) return strokes
        return strokes.filter { s -> paintBounds(s)?.overlaps(visible) == true }
    }

    /** Bitmap'in görünen kısmının ([clip] ∩ bitmap) tuval koordinatlarındaki karşılığı. */
    fun visibleCanvasRect(widthPx: Int, heightPx: Int, scale: Float, offset: Offset, clip: Rect): Rect {
        val screen = Rect(
            max(0f, clip.left), max(0f, clip.top), kotlin.math.min(widthPx.toFloat(), clip.right), kotlin.math.min(heightPx.toFloat(), clip.bottom),
        )
        val s = if (scale <= 0f) 1f else scale
        return Rect((screen.left - offset.x) / s, (screen.top - offset.y) / s, (screen.right - offset.x) / s, (screen.bottom - offset.y) / s)
    }

    /**
     * Tamamlanmış çizimleri görünüm boyutunda şeffaf bir bitmap'e bir kez çizer
     * (ekran = [offset] + tuval × [scale]); çizimler [clip] (ekrandaki kağıt) dışına taşmaz.
     * Ekran her karede sadece bu bitmap'i ve o an değişen çizimleri çizer.
     */
    fun renderViewportCache(
        strokes: List<Stroke>,
        widthPx: Int,
        heightPx: Int,
        scale: Float,
        offset: Offset,
        clip: Rect,
        /** Her çizimden önce çağrılır; iptal için istisna fırlatabilir (ör. yeni bir görünüm istendiğinde). */
        checkpoint: () -> Unit = {},
        layers: List<com.karapp.drawguidance.data.model.Layer> = emptyList(),
    ): ImageBitmap {
        val bitmap = ImageBitmap(max(1, widthPx), max(1, heightPx))
        // Yalnızca görünen bölgeye değen çizimler çizilir (yakınlaştırınca ekran dışındakiler atlanır)
        val shown = visibleIn(strokes, visibleCanvasRect(widthPx, heightPx, scale, offset, clip))
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(widthPx.toFloat(), heightPx.toFloat())) {
            clipRect(clip.left, clip.top, clip.right, clip.bottom) {
                translate(offset.x, offset.y) {
                    scale(scale, scale, pivot = Offset.Zero) {
                        drawStrokeList(shown, checkpoint, layers)
                    }
                }
            }
        }
        return bitmap
    }

    /** Mevcut görünüm önbelleğine yalnızca yeni eklenen çizimleri çizer (artımlı güncelleme). */
    fun drawIntoViewportCache(
        bitmap: ImageBitmap, strokes: List<Stroke>, scale: Float, offset: Offset, clip: Rect,
        layers: List<com.karapp.drawguidance.data.model.Layer> = emptyList(),
    ) {
        if (strokes.isEmpty()) return
        val shown = visibleIn(strokes, visibleCanvasRect(bitmap.width, bitmap.height, scale, offset, clip))
        if (shown.isEmpty()) return
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
            clipRect(clip.left, clip.top, clip.right, clip.bottom) {
                translate(offset.x, offset.y) {
                    scale(scale, scale, pivot = Offset.Zero) {
                        drawStrokeList(shown, layers = layers)
                    }
                }
            }
        }
    }

    /**
     * Resim elemanı: resim, noktalardaki 4 köşeye (taşıma/döndürme/ölçekleme sonrası
     * dörtgen) perspektif eşlemesiyle çizilir. Resim henüz yüklenmediyse yer tutucu çizilir.
     */
    private fun DrawScope.drawImageStroke(stroke: Stroke, offset: Offset) {
        if (stroke.points.size < 4) return
        val dst = FloatArray(8)
        for (i in 0 until 4) {
            dst[i * 2] = stroke.points[i].x + offset.x
            dst[i * 2 + 1] = stroke.points[i].y + offset.y
        }
        val image = CanvasImageCache.get(stroke.assetId)
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            if (image == null) {
                val path = android.graphics.Path().apply {
                    moveTo(dst[0], dst[1]); lineTo(dst[2], dst[3]); lineTo(dst[4], dst[5]); lineTo(dst[6], dst[7]); close()
                }
                native.drawPath(path, placeholderPaint)
                return@drawIntoCanvas
            }
            val bitmap = image.asAndroidBitmap()
            val alphaW = (stroke.opacity.coerceIn(0f, 1f) * 255).toInt()
            // v56 Çarpıtma ağı ve yansıma dalgası: ağ çizimi
            val raw = FloatArray(8) { if (it % 2 == 0) stroke.points[it / 2].x else stroke.points[it / 2].y }
            val verts = WarpRenderer.verts(raw, stroke.warp, offset.x, offset.y)
            if (verts != null) {
                WarpRenderer.drawMesh(native, bitmap, verts, alphaW, stroke.warp?.filter ?: 1)
                return@drawIntoCanvas
            }
            val src = floatArrayOf(0f, 0f, bitmap.width.toFloat(), 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), 0f, bitmap.height.toFloat())
            val matrix = android.graphics.Matrix()
            if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return@drawIntoCanvas
            val alpha = (stroke.opacity.coerceIn(0f, 1f) * 255).toInt()
            // v43 arka planla uyum (gölge, renk/ışık, kenar): özgün resim değişmeden çizimde uygulanır
            val harmony = stroke.harmony
            if (harmony != null) {
                com.karapp.drawguidance.ui.drawing.scene.HarmonyRenderer.draw(native, bitmap, stroke.assetId, harmony, matrix, alpha)
                return@drawIntoCanvas
            }
            // v56 ara değerleme: en yakın (0) süzgeçsiz
            val flags = if (stroke.warp?.filter == 0) android.graphics.Paint.ANTI_ALIAS_FLAG else android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG
            val paint = android.graphics.Paint(flags).apply {
                this.alpha = alpha
            }
            native.drawBitmap(bitmap, matrix, paint)
        }
    }

    private val placeholderPaint = android.graphics.Paint().apply { color = 0x33808A99 }
}
