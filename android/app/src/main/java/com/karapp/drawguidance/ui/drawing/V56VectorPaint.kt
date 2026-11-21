package com.karapp.drawguidance.ui.drawing

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb
import com.karapp.drawguidance.data.model.ArrowKind
import com.karapp.drawguidance.data.model.CapStyle
import com.karapp.drawguidance.data.model.GradientPoint
import com.karapp.drawguidance.data.model.JoinStyle
import com.karapp.drawguidance.data.model.ShapeSpec
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokeAlign
import com.karapp.drawguidance.data.model.V56Textures
import com.karapp.drawguidance.data.model.V56Vector
import com.karapp.drawguidance.data.model.VectorStyle
import com.karapp.drawguidance.ui.common.hexToColor
import kotlin.math.hypot
import kotlin.math.max

/** v56 aşama 8A şekil görünümü: serbest gradyan, desen ve ek dolgular, kontur biçimi ve hizası, ok uçları. */
object V56VectorPaint {
    private const val GRADIENT_SIZE = 48
    private val gradients = LruCache<List<GradientPoint>, Bitmap>(24)

    private fun argb(hex: String): Int = hexToColor(hex).toArgb()

    /** Serbest gradyan: küçük ters-uzaklık bitmap'i şeklin kutusuna gerilir (yumuşak süzgeçle). */
    fun freeGradientShader(points: List<GradientPoint>, l: Float, t: Float, r: Float, b: Float): Shader {
        val bmp = synchronized(gradients) {
            gradients.get(points) ?: run {
                val n = GRADIENT_SIZE
                val px = IntArray(n * n) { i -> V56Vector.freeGradientColor(points, (i % n + 0.5f) / n, (i / n + 0.5f) / n, ::argb) }
                Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).also { gradients.put(points, it) }
            }
        }
        return BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(max(r - l, 1f) / GRADIENT_SIZE, max(b - t, 1f) / GRADIENT_SIZE); postTranslate(l, t) })
        }
    }

    /** Desen dolgu (doku karosu dolgu renginde) ve ek dolgular. */
    fun drawFillExtras(canvas: Canvas, path: Path, v: VectorStyle, colorHex: String, alpha: Float) {
        v.patternId?.takeIf { V56Textures.isBuiltin(it) }?.let { id ->
            val shader = BitmapShader(GrainRenderer.tile(id, 1f), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            val k = v.patternScale.coerceIn(V56Textures.MIN_SCALE, V56Textures.MAX_SCALE)
            shader.setLocalMatrix(Matrix().apply { setScale(k, k) })
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                this.shader = shader
                colorFilter = PorterDuffColorFilter(argb(colorHex), PorterDuff.Mode.SRC_IN)
                this.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
            }
            canvas.drawPath(path, p)
        }
        v.fills.forEach { f ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = argb(f.colorHex)
                this.alpha = (f.opacity.coerceIn(0f, 1f) * alpha * 255).toInt()
            }
            canvas.drawPath(path, p)
        }
    }

    fun styleStroke(paint: Paint, v: VectorStyle) {
        paint.strokeCap = when (v.cap) { CapStyle.ROUND -> Paint.Cap.ROUND; CapStyle.BUTT -> Paint.Cap.BUTT; CapStyle.SQUARE -> Paint.Cap.SQUARE }
        paint.strokeJoin = when (v.join) { JoinStyle.ROUND -> Paint.Join.ROUND; JoinStyle.MITER -> Paint.Join.MITER; JoinStyle.BEVEL -> Paint.Join.BEVEL }
        paint.strokeMiter = v.miterLimit.coerceIn(1f, 20f)
        val dash = v.dash?.filter { it > 0f }
        if (dash != null && dash.size >= 2) paint.pathEffect = DashPathEffect((if (dash.size % 2 == 0) dash else dash + dash).toFloatArray(), 0f)
    }

    /** Kontur hizası: kapalı yolda iç (yola kırpılır) ya da dış (yolun dışı); kalınlık iki katı ([block] çarpanı alır). */
    inline fun withAlign(canvas: Canvas, path: Path, closed: Boolean, align: StrokeAlign, paint: Paint, block: (Float) -> Unit) {
        if (!closed || align == StrokeAlign.CENTER) { block(1f); return }
        val save = canvas.save()
        if (align == StrokeAlign.INSIDE) canvas.clipPath(path)
        else if (android.os.Build.VERSION.SDK_INT >= 26) canvas.clipOutPath(path)
        else @Suppress("DEPRECATION") canvas.clipPath(path, android.graphics.Region.Op.DIFFERENCE)
        block(2f)
        canvas.restoreToCount(save)
    }

    /** Açık alt yolların uçlarına ok başı. */
    fun drawArrows(canvas: Canvas, stroke: Stroke, spec: ShapeSpec, v: VectorStyle, width: Float, color: Int, alpha: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; this.alpha = (alpha * 255).toInt(); strokeWidth = width; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val size = max(width * 4f, 10f)
        ShapeRenderer.ranges(spec, stroke.points.size).forEachIndexed { i, range ->
            val sub = spec.subpaths.getOrNull(i)
            if (sub != null && (sub.closed || sub.label != null)) return@forEachIndexed
            val pts = ShapeRenderer.subPoints(stroke, range)
            if (pts.size < 2) return@forEachIndexed
            fun head(kind: ArrowKind, tip: Int, from: Int) {
                if (kind == ArrowKind.NONE) return
                val t = pts[tip]; var f = pts[from]
                // Çok yakın komşu noktaları atla (yönü kararlı)
                var k = from
                while (hypot(t.x - f.x, t.y - f.y) < 0.5f && k in 1 until pts.size - 1) { k += if (from > tip) 1 else -1; f = pts[k] }
                val len = hypot(t.x - f.x, t.y - f.y).takeIf { it > 1e-3f } ?: return
                val ux = (t.x - f.x) / len; val uy = (t.y - f.y) / len
                val g = V56Vector.arrowHead(kind, t.x, t.y, ux, uy, size)
                when (kind) {
                    ArrowKind.TRIANGLE -> { paint.style = Paint.Style.FILL; canvas.drawPath(Path().apply { moveTo(g[0].first, g[0].second); lineTo(g[1].first, g[1].second); lineTo(g[2].first, g[2].second); close() }, paint) }
                    ArrowKind.OPEN -> { paint.style = Paint.Style.STROKE; canvas.drawPath(Path().apply { moveTo(g[0].first, g[0].second); lineTo(g[1].first, g[1].second); lineTo(g[2].first, g[2].second) }, paint) }
                    ArrowKind.CIRCLE -> { paint.style = Paint.Style.FILL; canvas.drawCircle(g[0].first, g[0].second, g[1].first, paint) }
                    ArrowKind.BAR -> { paint.style = Paint.Style.STROKE; canvas.drawLine(g[0].first, g[0].second, g[1].first, g[1].second, paint) }
                    ArrowKind.NONE -> Unit
                }
            }
            head(v.arrowStart, 0, 1)
            head(v.arrowEnd, pts.size - 1, pts.size - 2)
        }
    }
}
