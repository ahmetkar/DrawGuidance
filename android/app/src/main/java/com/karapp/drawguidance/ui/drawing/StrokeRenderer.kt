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
        val (ps, ts, os) = BrushPainter.dynamicsOf(stroke.points)
        drawIntoCanvas {
            BrushPainter.paint(
                it.nativeCanvas, xs, ys, n, hexToColor(stroke.colorHex).toArgb(), stroke.brushType,
                stroke.sizePx, stroke.opacity, stroke.id.hashCode(), stroke.flow, stroke.strength, ps, ts, stroke.params, os,
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
    }

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
        val (ps, ts, os) = BrushPainter.dynamicsOf(stroke.points)
        BrushPainter.paint(
            canvas, xs, ys, n, hexToColor(stroke.colorHex).toArgb(), stroke.brushType,
            stroke.sizePx, stroke.opacity, stroke.id.hashCode(), stroke.flow, stroke.strength, ps, ts, stroke.params, os,
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
    fun DrawScope.drawStrokeList(strokes: List<Stroke>, checkpoint: () -> Unit = {}) {
        if (strokes.none { it.brushType == BrushType.ERASER }) {
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
                for (m in i until j) { checkpoint(); drawStroke(strokes[m]) }
                laterErasers.forEach { checkpoint(); drawStroke(it) }
                drawIntoCanvas { it.restore() }
            }
            i = j
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
        underlay: (DrawScope.() -> Unit)? = null,
    ) {
        val bounds = Rect(0f, 0f, canvasWidth, canvasHeight)
        drawRect(background, topLeft = Offset.Zero, size = bounds.size)
        if (decor != null && !decor.isEmpty) drawIntoCanvas {
            BackgroundRenderer.drawCanvasBackground(it.nativeCanvas, decor, 0f, 0f, canvasWidth, canvasHeight, 1f, background.toArgb(), vector = true)
        }
        underlay?.invoke(this)
        withStrokeLayer(bounds) { drawStrokeList(strokes) }
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
    )

    private val paintBounds = object : android.util.LruCache<String, PaintBounds>(8192) {}

    /**
     * Çizimin ekrana boya bırakabileceği en geniş alan (tuval birimi): noktaların kutusu + fırça kalınlığı ve
     * saçılma payı. Görünür alanın dışındaki çizimler hiç işlenmez (fırça motoru çalışmaz).
     */
    fun paintBounds(stroke: Stroke): Rect? {
        val pts = stroke.points
        if (pts.isEmpty()) return null
        paintBounds.get(stroke.id)?.let { if (it.points === pts && it.size == stroke.sizePx && it.params == stroke.params) return it.rect }
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
        val rect = Rect(l - pad, t - pad, r + pad, b + pad)
        paintBounds.put(stroke.id, PaintBounds(pts, stroke.sizePx, rect, stroke.params))
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
    ): ImageBitmap {
        val bitmap = ImageBitmap(max(1, widthPx), max(1, heightPx))
        // Yalnızca görünen bölgeye değen çizimler çizilir (yakınlaştırınca ekran dışındakiler atlanır)
        val shown = visibleIn(strokes, visibleCanvasRect(widthPx, heightPx, scale, offset, clip))
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(widthPx.toFloat(), heightPx.toFloat())) {
            clipRect(clip.left, clip.top, clip.right, clip.bottom) {
                translate(offset.x, offset.y) {
                    scale(scale, scale, pivot = Offset.Zero) {
                        drawStrokeList(shown, checkpoint)
                    }
                }
            }
        }
        return bitmap
    }

    /** Mevcut görünüm önbelleğine yalnızca yeni eklenen çizimleri çizer (artımlı güncelleme). */
    fun drawIntoViewportCache(bitmap: ImageBitmap, strokes: List<Stroke>, scale: Float, offset: Offset, clip: Rect) {
        if (strokes.isEmpty()) return
        val shown = visibleIn(strokes, visibleCanvasRect(bitmap.width, bitmap.height, scale, offset, clip))
        if (shown.isEmpty()) return
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
            clipRect(clip.left, clip.top, clip.right, clip.bottom) {
                translate(offset.x, offset.y) {
                    scale(scale, scale, pivot = Offset.Zero) {
                        drawStrokeList(shown)
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
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.alpha = alpha
            }
            native.drawBitmap(bitmap, matrix, paint)
        }
    }

    private val placeholderPaint = android.graphics.Paint().apply { color = 0x33808A99 }
}
