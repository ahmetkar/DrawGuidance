package com.karapp.drawguidance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.graphics.BlendMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.ui.drawing.BrushPainter
import com.karapp.drawguidance.ui.drawing.CurveSampler
import com.karapp.drawguidance.ui.drawing.LiveStrokeLayer
import com.karapp.drawguidance.ui.drawing.SampleBuffer
import com.karapp.drawguidance.ui.drawing.StrokeStream
import com.karapp.drawguidance.ui.drawing.defaultSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Akışlı fırça motoru: artımlı örnekleme eski (tüm çizgiyi bir kerede) örneklemeyle birebir aynı;
 * canlı çizimde parça parça üretilen iz, kaydedilen (statik) çizimle aynı görünür.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class StrokeStreamTest {

    @After fun tearDown() = stopKoin()

    /** v5'teki örnekleme algoritmasının birebir kopyası (referans). */
    private fun referenceResample(xs: FloatArray, ys: FloatArray, count: Int, step: Float): SampleBuffer {
        val px = ArrayList<Float>(); val py = ArrayList<Float>()
        px += xs[0]; py += ys[0]
        if (count >= 3) {
            var sx = xs[0]; var sy = ys[0]
            for (i in 1 until count) {
                val cx = xs[i - 1]; val cy = ys[i - 1]
                val ex = (xs[i - 1] + xs[i]) / 2f; val ey = (ys[i - 1] + ys[i]) / 2f
                val len = hypot(cx - sx, cy - sy) + hypot(ex - cx, ey - cy)
                val n = max(1, ceil(len / max(0.5f, step * 0.5f)).toInt()).coerceAtMost(64)
                for (k in 1..n) {
                    val u = k / n.toFloat()
                    val a = (1 - u) * (1 - u); val b = 2 * (1 - u) * u; val c = u * u
                    px += a * sx + b * cx + c * ex; py += a * sy + b * cy + c * ey
                }
                sx = ex; sy = ey
            }
            px += xs[count - 1]; py += ys[count - 1]
        } else {
            for (i in 1 until count) { px += xs[i]; py += ys[i] }
        }
        var total = 0f
        for (i in 1 until px.size) total += hypot(px[i] - px[i - 1], py[i] - py[i - 1])
        val out = SampleBuffer()
        val n = max(1, (total / step).toInt() + 1)
        if (total <= 0f || px.size < 2) { out.add(px[0], py[0], 1f, 0f, 0f, 0, 0); return out }
        var seg = 1; var segStart = 0f; var segLen = hypot(px[1] - px[0], py[1] - py[0])
        var ldx = 1f; var ldy = 0f
        for (k in 0 until n) {
            val target = min(total, k * step)
            while (seg < px.size - 1 && segStart + segLen < target) { segStart += segLen; seg++; segLen = hypot(px[seg] - px[seg - 1], py[seg] - py[seg - 1]) }
            val u = if (segLen == 0f) 0f else ((target - segStart) / segLen).coerceIn(0f, 1f)
            val ax = px[seg - 1]; val ay = py[seg - 1]; val bx = px[seg]; val by = py[seg]
            val l = hypot(bx - ax, by - ay)
            if (l > 0f) { ldx = (bx - ax) / l; ldy = (by - ay) / l } else if (k == 0) { ldx = 1f; ldy = 0f }
            out.add(ax + (bx - ax) * u, ay + (by - ay) * u, ldx, ldy, target, 0, k)
        }
        return out
    }

    @Test fun incrementalSamplingMatchesWholeStrokeSampling() {
        val random = Random(5)
        repeat(60) { trial ->
            val count = when (trial % 6) { 0 -> 1; 1 -> 2; 2 -> 3; else -> random.nextInt(4, 120) }
            val xs = FloatArray(count); val ys = FloatArray(count)
            var x = 200f; var y = 200f
            for (i in 0 until count) {
                if (random.nextInt(8) != 0) { x += random.nextFloat() * 14f - 7f; y += random.nextFloat() * 14f - 5f } // bazen aynı nokta
                xs[i] = x; ys[i] = y
            }
            val step = listOf(0.6f, 1.7f, 3.2f, 11f)[trial % 4]
            val reference = referenceResample(xs, ys, count, step)
            val sampler = CurveSampler(step)
            // Noktalar tek tek gelir; ara ara kuyruk da istenir (kesin örnekleri bozmamalı)
            val tail = SampleBuffer()
            for (i in 1..count) {
                sampler.advance(xs, ys, i)
                if (i % 3 == 0) { tail.clear(); sampler.tail(xs, ys, i, tail) }
            }
            sampler.finish(xs, ys, count)
            val s = sampler.samples
            assertEquals("deneme $trial: örnek sayısı", reference.size, s.size)
            for (k in 0 until s.size) {
                assertEquals("deneme $trial örnek $k x", reference.x[k], s.x[k], 1e-3f)
                assertEquals("deneme $trial örnek $k y", reference.y[k], s.y[k], 1e-3f)
                assertEquals("deneme $trial örnek $k dx", reference.dx[k], s.dx[k], 1e-3f)
                assertEquals("deneme $trial örnek $k dy", reference.dy[k], s.dy[k], 1e-3f)
                assertEquals("deneme $trial örnek $k konum", reference.along[k], s.along[k], 1e-2f)
            }
        }
    }

    private fun spiral(n: Int) = Pair(
        FloatArray(n) { 260f + cos(it / 18f) * (40f + it * 0.9f) },
        FloatArray(n) { 260f + sin(it / 18f) * (40f + it * 0.9f) },
    )

    private fun white() = Bitmap.createBitmap(520, 520, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

    private fun compositePaint(stream: StrokeStream): Paint? = when {
        stream.isEraser -> Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
        stream.compositeAlpha < 0.999f || stream.blend != BlendMode.SrcOver -> StrokeStream.layerPaint(stream.compositeAlpha, stream.blend)
        else -> null
    }

    /** Canlı çizim benzetimi: nokta geldikçe kesin parçalar ham katmana yazılır, kuyruk ayrı çizilir; sonunda birleştirilir. */
    private fun renderLive(type: BrushType, xs: FloatArray, ys: FloatArray, flow: Float, opacity: Float, base: Bitmap, frames: Boolean = true): Bitmap {
        val stream = StrokeStream.create(type, 0xFF2A4B8D.toInt(), type.defaultSize() * 1.5f, opacity, 99, flow, 0.8f, eraserMask = true)
        val raw = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val rawCanvas = Canvas(raw)
        // v20 3B fırçanın gölgesi ayrı ham katmanda (canlı katmandaki gibi); bitişte tamamı yeniden çizilir
        val shadow = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val shadowCanvas = Canvas(shadow)
        val scratch = Canvas(Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888))
        fun paint(chunk: com.karapp.drawguidance.ui.drawing.StrokeChunk?) {
            chunk ?: return
            if (chunk.replaceShadow) shadow.eraseColor(0)
            if (chunk.hasShadow) chunk.drawShadow(shadowCanvas)
            chunk.draw(rawCanvas)
        }
        for (i in xs.indices) {
            stream.add(xs[i], ys[i])
            paint(stream.takeFinal(if (type == BrushType.FLAT_BRUSH) 6 else 1))
            if (frames) stream.tail()?.draw(scratch)
        }
        stream.end()
        paint(stream.takeFinal(0))
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        if (stream.shadowAlpha > 0f) {
            val restore = canvas.saveLayer(null, compositePaint(stream))
            canvas.drawBitmap(shadow, 0f, 0f, Paint().apply { alpha = (stream.shadowAlpha * 255).roundToInt() })
            canvas.drawBitmap(raw, 0f, 0f, null)
            canvas.restoreToCount(restore)
        } else canvas.drawBitmap(raw, 0f, 0f, compositePaint(stream))
        return out
    }

    private fun renderStatic(type: BrushType, xs: FloatArray, ys: FloatArray, flow: Float, opacity: Float, base: Bitmap): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val stream = StrokeStream.create(type, 0xFF2A4B8D.toInt(), type.defaultSize() * 1.5f, opacity, 99, flow, 0.8f)
        stream.setPoints(xs.copyOf(), ys.copyOf(), xs.size)
        stream.drawStatic(canvas)
        return out
    }

    private data class Diff(val mean: Double, val big: Int, val inked: Int, val max: Int)

    private fun diff(a: Bitmap, b: Bitmap): Diff {
        var sum = 0L; var big = 0; var inked = 0; var mx = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val p = a.getPixel(x, y); val q = b.getPixel(x, y)
            // Önceden çarpılmış (premultiplied) değerler: saydam kenar piksellerinde renk kanalı anlamsızdır
            fun pm(c: Int, ch: Int) = ch * Color.alpha(c) / 255
            val d = maxOf(
                abs(pm(p, Color.red(p)) - pm(q, Color.red(q))), abs(pm(p, Color.green(p)) - pm(q, Color.green(q))),
                abs(pm(p, Color.blue(p)) - pm(q, Color.blue(q))), abs(Color.alpha(p) - Color.alpha(q)),
            )
            sum += d; if (d > 48) big++; if (d > mx) mx = d
            if (p != Color.WHITE || q != Color.WHITE) inked++
        }
        return Diff(sum.toDouble() / (a.width * a.height), big, inked, mx)
    }

    @Test fun liveDrawingLooksLikeTheSavedStrokeForEveryBrush() {
        val (xs, ys) = spiral(260)
        val report = StringBuilder()
        val types = BrushType.entries.filter { it != BrushType.IMAGE }
        for (type in types) for ((flow, opacity) in listOf(1f to 1f, 0.35f to 0.7f)) {
            val base = if (type == BrushType.ERASER) Bitmap.createBitmap(520, 520, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF224466.toInt()) } else white()
            val live = renderLive(type, xs, ys, flow, opacity, base)
            val still = renderStatic(type, xs, ys, flow, opacity, base)
            val d = diff(live, still)
            System.getenv("DG_SHOTS")?.let { dir ->
                java.io.File(dir).mkdirs()
                if (d.mean >= 1.0) {
                    java.io.File(dir, "stream-$type-$flow-live.png").outputStream().use { live.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    java.io.File(dir, "stream-$type-$flow-static.png").outputStream().use { still.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            val bigShare = if (d.inked == 0) 0.0 else d.big.toDouble() / d.inked
            report.append("STREAM $type akış=$flow opak=$opacity: ort fark=${"%.3f".format(d.mean)}, büyük farklı piksel=${d.big}/${d.inked} (%${"%.2f".format(bigShare * 100)}), en büyük=${d.max}\n")
            assertTrue("$type (akış $flow): canlı ve kayıtlı çizim farklı görünüyor: $d", d.max <= 6 || (d.mean < 1.0 && bigShare < 0.01))
        }
        println(report)
    }

    @Test fun finishedLiveStrokeHandsOffToTheViewCacheLikeARerender() {
        val (xs, ys) = spiral(220)
        val scale = 1.37f; val ox = 40f; val oy = -25f
        val clip = android.graphics.RectF(30f, 10f, 610f, 560f)
        for (type in listOf(BrushType.PASTEL, BrushType.MARKER, BrushType.HIGHLIGHTER, BrushType.AIRBRUSH, BrushType.FLAT_BRUSH, BrushType.ERASER)) {
            // Önbellek: kağıt olmadan, şeffaf katmanda önceki çizimler
            fun base() = Bitmap.createBitmap(640, 600, Bitmap.Config.ARGB_8888).also { b ->
                val c = Canvas(b)
                c.drawRect(60f, 60f, 580f, 300f, Paint().apply { color = 0xFFCC5533.toInt() })
            }
            val color = 0xFF2A4B8D.toInt()
            val size = type.defaultSize() * 1.2f
            val id = "stroke-$type"

            val handed = base()
            val layer = LiveStrokeLayer()
            layer.start(id, type, color, size, 0.8f, 0.6f, 0.9f, scale, ox, oy, 640, 600)
            for (i in xs.indices) layer.add(xs[i], ys[i])
            layer.finish()
            layer.blitInto(Canvas(handed), clip)
            layer.release()

            val rerendered = base()
            Canvas(rerendered).apply {
                clipRect(clip)
                translate(ox, oy)
                scale(scale, scale)
                BrushPainter.paint(this, xs.copyOf(), ys.copyOf(), xs.size, color, type, size, 0.8f, id.hashCode(), 0.6f, 0.9f)
            }
            val d = diff(handed, rerendered)
            println("HANDOFF $type: $d")
            assertTrue("$type: önbelleğe aktarılan canlı çizgi yeniden çizimden farklı: $d", d.max <= 6 || (d.mean < 1.0 && d.big.toDouble() / max(1, d.inked) < 0.01))
        }
    }
}
