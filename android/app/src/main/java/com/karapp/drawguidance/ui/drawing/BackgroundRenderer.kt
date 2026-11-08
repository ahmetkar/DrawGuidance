package com.karapp.drawguidance.ui.drawing

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.ui.graphics.asAndroidBitmap
import com.karapp.drawguidance.content.paper.ImageLayout
import com.karapp.drawguidance.content.paper.PaperPattern
import com.karapp.drawguidance.content.paper.PatternColors
import com.karapp.drawguidance.content.paper.PatternPainter
import com.karapp.drawguidance.content.paper.PatternSurface
import com.karapp.drawguidance.data.images.CanvasImageCache
import com.karapp.drawguidance.data.library.PaperLibrary
import com.karapp.drawguidance.data.model.BackgroundSpec
import com.karapp.drawguidance.data.model.BoxFit
import com.karapp.drawguidance.data.model.TextBox
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** v23 desenlerin Android Canvas'a çizimi ([PatternPainter] yüzeyi). */
class AndroidPatternSurface(var canvas: Canvas) : PatternSurface {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val dashes = HashMap<Float, DashPathEffect>()

    override fun save() { canvas.save() }
    override fun restore() { canvas.restore() }
    override fun translate(dx: Float, dy: Float) = canvas.translate(dx, dy)
    override fun scale(s: Float) = canvas.scale(s, s)
    override fun clipRect(l: Float, t: Float, r: Float, b: Float) { canvas.clipRect(l, t, r, b) }

    private fun strokePaint(width: Float, color: Int, dash: Float): Paint {
        stroke.color = color
        stroke.strokeWidth = width
        if (dash > 0f) {
            stroke.strokeCap = Paint.Cap.BUTT
            stroke.pathEffect = dashes.getOrPut(dash) { DashPathEffect(floatArrayOf(dash, dash * 0.7f), 0f) }
        } else {
            stroke.strokeCap = Paint.Cap.ROUND
            stroke.pathEffect = null
        }
        return stroke
    }

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int, dash: Float) {
        canvas.drawLine(x1, y1, x2, y2, strokePaint(width, color, dash))
    }

    override fun rect(x: Float, y: Float, w: Float, h: Float, fill: Boolean, width: Float, color: Int, dash: Float) {
        if (fill) { this.fill.color = color; canvas.drawRect(x, y, x + w, y + h, this.fill) }
        else canvas.drawRect(x, y, x + w, y + h, strokePaint(width, color, dash))
    }

    override fun circle(cx: Float, cy: Float, r: Float, fill: Boolean, width: Float, color: Int, dash: Float) {
        if (fill) { this.fill.color = color; canvas.drawCircle(cx, cy, r, this.fill) }
        else canvas.drawCircle(cx, cy, r, strokePaint(width, color, dash))
    }

    override fun poly(xy: FloatArray, closed: Boolean, fill: Boolean, width: Float, color: Int, dash: Float) {
        if (xy.size < 4) return
        path.rewind()
        path.moveTo(xy[0], xy[1])
        for (i in 1 until xy.size / 2) path.lineTo(xy[2 * i], xy[2 * i + 1])
        if (closed) path.close()
        if (fill) { this.fill.color = color; canvas.drawPath(path, this.fill) }
        else canvas.drawPath(path, strokePaint(width, color, dash))
    }
}

/**
 * v23 arka plan çizimi: tuvalin arka plan resmi ve deseni (kağıt renginin üstünde, çizimlerin altında) ve yazı
 * kutularının arka planı (renk, desen, resim, kenar). Ekranda desen karosu bir kez resme çizilip döşenir (her karede
 * yüzlerce çizgi çizilmez); dışa aktarmada ve PDF'te vektör olarak çizilir.
 */
object BackgroundRenderer {

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** Ekran karoları: desen + renkler + ölçek → karo resmi. */
    private val tiles = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    init {
        com.karapp.drawguidance.platform.DeviceCaps.onTrim { tiles.evictAll() }
    }

    fun parseColor(hex: String?, fallback: Int): Int = hex?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() } ?: fallback

    /** Arka plan resminin bitmap'i (yüklenmediyse null; yükleme başlar). */
    fun backgroundBitmap(spec: BackgroundSpec): Bitmap? {
        spec.paperImageId?.let { return PaperLibrary.image(it)?.asAndroidBitmap() }
        spec.assetId?.let { return CanvasImageCache.get(it)?.asAndroidBitmap() }
        return null
    }

    /**
     * Tuvalin arka planı: [l], [t], [r], [b] kağıdın bu tuvaldeki dikdörtgeni, [scale] tuval biriminin bu tuvaldeki
     * boyu (ekranda yakınlaştırma; dışa aktarmada çıktı ölçeği). [vector] true ise desen karoları tek tek çizilir.
     */
    fun drawCanvasBackground(
        c: Canvas, spec: BackgroundSpec, l: Float, t: Float, r: Float, b: Float, scale: Float, paperColor: Int, vector: Boolean,
    ) {
        if (spec.isEmpty || r <= l || b <= t) return
        c.save()
        c.clipRect(l, t, r, b)
        // v27 gradyan kağıt (kağıt renginin üstünde; resim ve desen onun üstüne)
        Gradients.shader(spec.gradient, l, t, r, b)?.let { sh ->
            fillPaint.shader = sh
            fillPaint.color = 0xFFFFFFFF.toInt()
            c.drawRect(l, t, r, b, fillPaint)
            fillPaint.shader = null
        }
        backgroundBitmap(spec)?.let { bmp ->
            val layout = spec.paperImageId?.let { PaperLibrary.layout(it) }
            // v40 resim kaydırılmış/boyutlandırılmışsa çerçevesi kağıda göre taşınır ve ölçeklenir (kağıtla kırpılır)
            val saved = c.save()
            if (spec.hasImageTransform) {
                c.translate(spec.imageOffsetX * (r - l), spec.imageOffsetY * (b - t))
                c.scale(spec.imageScale, spec.imageScale, (l + r) / 2f, (t + b) / 2f)
            }
            drawImage(c, bmp, spec.imageFit, layout, l, t, r, b, scale, spec.imageOpacity)
            c.restoreToCount(saved)
        }
        val p = PaperLibrary.pattern(spec.patternId)
        if (p != null) {
            val colors = PatternColors(parseColor(spec.patternColorHex, p.color), paperColor, p.accent, spec.patternOpacity.coerceIn(0f, 1f))
            drawPattern(c, p, colors, l, t, r, b, spec.patternScale * scale, vector)
        }
        c.restore()
    }

    /** Deseni dikdörtgene çizer ([scale]: deseninin bu tuvaldeki ölçeği). */
    fun drawPattern(c: Canvas, p: PaperPattern, colors: PatternColors, l: Float, t: Float, r: Float, b: Float, scale: Float, vector: Boolean) {
        val surface = AndroidPatternSurface(c)
        val w = r - l; val h = b - t
        val clip = Rect()
        val visible = if (c.getClipBounds(clip)) floatArrayOf(clip.left.toFloat(), clip.top.toFloat(), clip.right.toFloat(), clip.bottom.toFloat()) else null
        val tileBmp = if (vector || !p.hasTile) null else tileBitmap(p, colors, scale)
        if (tileBmp == null) {
            PatternPainter.paint(p, surface, l, t, w, h, colors, scale, visible)
            return
        }
        if (p.tint != 0 && p.tintAlpha > 0f) {
            fillPaint.color = PatternColors.withAlpha(p.tint, p.tintAlpha * colors.opacity)
            fillPaint.shader = null
            c.drawRect(l, t, r, b, fillPaint)
        }
        val area = PatternPainter.tileArea(p, l, t, w, h)
        if (area[2] > area[0] && area[3] > area[1]) {
            val shader = BitmapShader(tileBmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            val m = Matrix()
            m.setScale(p.tileW * scale / tileBmp.width, p.tileH * scale / tileBmp.height)
            m.postTranslate(area[0], area[1])
            shader.setLocalMatrix(m)
            fillPaint.shader = shader
            fillPaint.color = 0xFFFFFFFF.toInt()
            fillPaint.isFilterBitmap = true
            c.drawRect(area[0], area[1], area[2], area[3], fillPaint)
            fillPaint.shader = null
        }
        if (p.page.isNotEmpty()) PatternPainter.paint(p, surface, l, t, w, h, colors, scale, visible, tiles = false, tint = false)
    }

    /**
     * Karo resmi: çözünürlük ölçeğin bir üst ikinin kuvvetine yuvarlanır (yakınlaştırırken her karede yeniden
     * çizilmez, keskin kalır). Çok büyük karolarda null (vektör çizilir).
     */
    private fun tileBitmap(p: PaperPattern, colors: PatternColors, scale: Float): Bitmap? {
        if (scale <= 0f) return null
        val rs = 2f.pow(ceil(ln(scale.toDouble()) / ln(2.0)).toFloat()).coerceIn(0.125f, 8f)
        val tw = p.tileW * rs; val th = p.tileH * rs
        val bw = tw.roundToInt(); val bh = th.roundToInt()
        if (bw < 2 || bh < 2 || bw > 4096 || bh > 4096 || bw.toLong() * bh > 1_500_000L) return null
        val key = "${p.id}|${colors.main}|${colors.paper}|${colors.accent}|${colors.opacity}|$rs"
        tiles.get(key)?.let { return it }
        val bmp = runCatching { Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val canvas = Canvas(bmp)
        canvas.scale(bw / tw, bh / th)
        PatternPainter.paintTile(p, AndroidPatternSurface(canvas), colors, rs)
        tiles.put(key, bmp)
        return bmp
    }

    /** Resmi dikdörtgene yerleşimine göre çizer. [scale] resim pikselinin bu tuvaldeki boyu (döşeme, dokuz parça). */
    fun drawImage(c: Canvas, bmp: Bitmap, fit: BoxFit, layout: ImageLayout?, l: Float, t: Float, r: Float, b: Float, scale: Float, opacity: Float) {
        if (bmp.isRecycled) return
        imagePaint.alpha = (opacity.coerceIn(0f, 1f) * 255).roundToInt()
        val dst = RectF(l, t, r, b)
        when (fit) {
            BoxFit.STRETCH -> c.drawBitmap(bmp, null, dst, imagePaint)
            BoxFit.TILE -> {
                val shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                val m = Matrix()
                m.setScale(scale, scale)
                m.postTranslate(l, t)
                shader.setLocalMatrix(m)
                imagePaint.shader = shader
                c.drawRect(dst, imagePaint)
                imagePaint.shader = null
            }
            BoxFit.NINE_SLICE -> {
                val lay = layout?.takeIf { it.kind == ImageLayout.NINE }
                if (lay == null) c.drawBitmap(bmp, null, dst, imagePaint) else drawNine(c, bmp, lay, l, t, r, b, scale)
            }
            BoxFit.COVER -> {
                val k = max((r - l) / bmp.width, (b - t) / bmp.height)
                val sw = (r - l) / k; val sh = (b - t) / k
                val sx = (bmp.width - sw) / 2f; val sy = (bmp.height - sh) / 2f
                c.drawBitmap(bmp, Rect(sx.roundToInt(), sy.roundToInt(), (sx + sw).roundToInt(), (sy + sh).roundToInt()), dst, imagePaint)
            }
        }
        imagePaint.alpha = 255
    }

    /**
     * Dokuz parçalı çizim: köşeler [scale] ölçeğinde aynı kalır; kenar ve orta parçalar esnetilir ya da ([ImageLayout.repeat])
     * tam sayıda tekrarlanır (kopyalar hafifçe esnetilerek alanı tam doldurur; desen dikişsiz kalır).
     */
    private fun drawNine(c: Canvas, bmp: Bitmap, lay: ImageLayout, l: Float, t: Float, r: Float, b: Float, scale: Float) {
        val ins = lay.insets
        val iw = bmp.width; val ih = bmp.height
        // Kaynak sınırları (resim boyundan büyük kayıtlara karşı)
        val sl = ins[0].coerceIn(0, iw / 2); val sr = ins[2].coerceIn(0, iw - sl - 1)
        val st = ins[1].coerceIn(0, ih / 2); val sb = ins[3].coerceIn(0, ih - st - 1)
        var k = scale
        val needW = (sl + sr) * k; val needH = (st + sb) * k
        if (needW > r - l) k *= (r - l) / needW
        if (needH > b - t) k = min(k, scale * (b - t) / needH)
        val xs = floatArrayOf(l, l + sl * k, r - sr * k, r)
        val ys = floatArrayOf(t, t + st * k, b - sb * k, b)
        val sxs = intArrayOf(0, sl, iw - sr, iw)
        val sys = intArrayOf(0, st, ih - sb, ih)
        val src = Rect(); val dst = RectF()
        for (j in 0..2) for (i in 0..2) {
            val dl = xs[i]; val dr = xs[i + 1]; val dt = ys[j]; val db = ys[j + 1]
            if (dr - dl < 0.01f || db - dt < 0.01f) continue
            src.set(sxs[i], sys[j], sxs[i + 1], sys[j + 1])
            if (src.width() <= 0 || src.height() <= 0) continue
            val nx = if (lay.repeat && i == 1) max(1, ((dr - dl) / (src.width() * k)).roundToInt()) else 1
            val ny = if (lay.repeat && j == 1) max(1, ((db - dt) / (src.height() * k)).roundToInt()) else 1
            val cw = (dr - dl) / nx; val ch = (db - dt) / ny
            for (yy in 0 until ny) for (xx in 0 until nx) {
                // Kopyalar arasında yarım piksel örtüşme: kenar yumuşatması dikiş bırakmasın
                dst.set(dl + xx * cw, dt + yy * ch, dl + (xx + 1) * cw + 0.5f, dt + (yy + 1) * ch + 0.5f)
                c.drawBitmap(bmp, src, dst, imagePaint)
            }
        }
    }

    /** Yazı kutusunun resmi (hazır resim ya da galeriden). */
    fun boxBitmap(box: TextBox): Bitmap? {
        val id = box.imageId ?: return null
        return if (id.startsWith(BackgroundSpec.ASSET)) CanvasImageCache.get(id.removePrefix(BackgroundSpec.ASSET))?.asAndroidBitmap()
        else PaperLibrary.image(id)?.asAndroidBitmap()
    }

    /** Hazır kutu resminin yazı için iç boşluğu (resim pikseli; resim yoksa null). */
    fun boxContent(box: TextBox): IntArray? {
        val id = box.imageId ?: return null
        if (id.startsWith(BackgroundSpec.ASSET) || box.imageFit != BoxFit.NINE_SLICE) return null
        val lay = PaperLibrary.layout(id)
        return if (lay.kind == ImageLayout.NINE) lay.content else null
    }

    /**
     * Yazı kutusunun arka planı (kutu biriminde 0..[w], 0..[h]). [paper] kutunun altındaki kağıt rengi (desen tonları),
     * [coverage] silinme denetimi için kutu tam opak çizilir.
     */
    fun drawBox(c: Canvas, box: TextBox, w: Float, h: Float, paper: Int, alpha: Float, coverage: Boolean) {
        if (!box.hasBackground) return
        val rad = box.corner.coerceIn(0f, min(w, h) / 2f)
        val rect = RectF(0f, 0f, w, h)
        if (coverage) {
            fillPaint.shader = null
            fillPaint.color = 0xFF000000.toInt()
            c.drawRoundRect(rect, rad, rad, fillPaint)
            return
        }
        val save = c.save()
        if (rad > 0f) {
            val path = Path().apply { addRoundRect(rect, rad, rad, Path.Direction.CW) }
            c.clipPath(path)
        } else c.clipRect(rect)
        val gradient = Gradients.shader(box.fillGradient, 0f, 0f, w, h, alpha)
        if (gradient != null) {
            // v27 gradyan dolgu (rengin yerine)
            fillPaint.shader = gradient
            fillPaint.color = 0xFFFFFFFF.toInt()
            c.drawRect(rect, fillPaint)
            fillPaint.shader = null
        } else box.fillHex?.let {
            fillPaint.shader = null
            fillPaint.color = PatternColors.withAlpha(parseColor(it, 0xFFFFFFFF.toInt()), alpha)
            c.drawRect(rect, fillPaint)
        }
        boxBitmap(box)?.let { bmp ->
            val layout = box.imageId?.takeUnless { it.startsWith(BackgroundSpec.ASSET) }?.let { PaperLibrary.layout(it) }
            drawImage(c, bmp, box.imageFit, layout, 0f, 0f, w, h, box.patternScale, alpha)
        }
        PaperLibrary.pattern(box.patternId)?.let { p ->
            val under = box.fillHex?.let { parseColor(it, paper) } ?: paper
            val colors = PatternColors(parseColor(box.patternColorHex, p.color), under, p.accent, alpha)
            drawPattern(c, p, colors, 0f, 0f, w, h, box.patternScale, vector = true)
        }
        c.restoreToCount(save)
        box.borderHex?.let {
            borderPaint.color = PatternColors.withAlpha(parseColor(it, 0xFF333333.toInt()), alpha)
            borderPaint.strokeWidth = max(1f, min(w, h) * 0.012f)
            val inset = borderPaint.strokeWidth / 2f
            c.drawRoundRect(RectF(inset, inset, w - inset, h - inset), max(0f, rad - inset), max(0f, rad - inset), borderPaint)
        }
    }
}
