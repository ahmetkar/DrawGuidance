package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.StrokeRenderer
import com.karapp.drawguidance.ui.drawing.StrokeRenderer.drawStroke
import com.karapp.drawguidance.ui.drawing.defaultSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * v13 çizim motoru: görünüm önbelleği yalnızca görünen bölgeye değen çizimleri çizer. Yakınlaştırılmış bir
 * görünümde (ekranda tuvalin küçük bir kısmı) yeniden çizim, tüm çizimleri işlemekten çok daha hızlı olmalı ve
 * görüntü birebir aynı kalmalı.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V13RenderPerformanceTest {
    @After fun tearDown() = stopKoin()

    private val types = listOf(
        BrushType.PENCIL, BrushType.MARKER, BrushType.PASTEL, BrushType.CHARCOAL, BrushType.SOFT_ROUND,
        BrushType.AIRBRUSH, BrushType.TEXTURE_SPLATTER, BrushType.HALFTONE_DOTS, BrushType.INK_PEN, BrushType.FLAT_BRUSH,
        BrushType.ERASER, BrushType.CALLIGRAPHY, BrushType.STAMP_STAR, BrushType.PATTERN_ZIGZAG,
    )

    private fun scene(count: Int, size: Float): List<Stroke> {
        val r = Random(7)
        return List(count) { i ->
            val type = types[i % types.size]
            var x = r.nextFloat() * size
            var y = r.nextFloat() * size
            var a = r.nextFloat() * 6.28f
            val pts = List(60) {
                a += (r.nextFloat() - 0.5f) * 0.4f
                x += cos(a) * 6f; y += sin(a) * 6f
                StrokePoint(x, y)
            }
            Stroke("s$i", pts, "#335577", type, type.defaultSize(), 1f, i)
        }
    }

    /** Eski yöntem: tüm çizimler (görünmeyenler dahil) işlenir. */
    private fun renderAll(strokes: List<Stroke>, w: Int, h: Int, scale: Float, offset: Offset, clip: Rect): ImageBitmap {
        val bitmap = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat())) {
            clipRect(clip.left, clip.top, clip.right, clip.bottom) {
                translate(offset.x, offset.y) { scale(scale, scale, pivot = Offset.Zero) { strokes.forEach { drawStroke(it) } } }
            }
        }
        return bitmap
    }

    private fun timeMs(block: () -> Unit): Double {
        val t0 = System.nanoTime(); block(); return (System.nanoTime() - t0) / 1e6
    }

    private fun pixels(b: ImageBitmap): IntArray {
        val a = b.asAndroidBitmap()
        val out = IntArray(a.width * a.height)
        a.getPixels(out, 0, a.width, 0, 0, a.width, a.height)
        return out
    }

    /** Kayıtlı (Picture) fırça izleri: tüm tuval görünürken ikinci ve sonraki çizimler hızlanır, görüntü aynı. */
    @Test fun recordedStrokesRedrawFasterAndLookTheSame() {
        val size = 4000f
        val strokes = scene(600, size)
        val w = 720; val h = 720
        val scale = w / size
        val offset = Offset.Zero
        val clip = Rect(offset, Size(size * scale, size * scale))
        StrokeRenderer.pictureCacheEnabled = false
        StrokeRenderer.renderViewportCache(strokes, w, h, scale, offset, clip)
        val direct = (0 until 3).map { timeMs { StrokeRenderer.renderViewportCache(strokes, w, h, scale, offset, clip) } }.min()
        val reference = pixels(StrokeRenderer.renderViewportCache(strokes, w, h, scale, offset, clip))
        StrokeRenderer.pictureCacheEnabled = true
        val first = timeMs { StrokeRenderer.renderViewportCache(strokes, w, h, scale, offset, clip) }
        val cached = (0 until 3).map { timeMs { StrokeRenderer.renderViewportCache(strokes, w, h, scale, offset, clip) } }.min()
        println("V13 kayıtlı izler: doğrudan %.1f ms, ilk kayıt %.1f ms, kayıttan %.1f ms".format(direct, first, cached))
        val replay = pixels(StrokeRenderer.renderViewportCache(strokes, w, h, scale, offset, clip))
        var diff = 0
        for (i in reference.indices) if (reference[i] != replay[i]) diff++
        assertTrue("görüntü aynı (farklı piksel: $diff)", diff <= reference.size / 1000)
        assertTrue("kayıttan çizim daha hızlı ($direct → $cached ms)", cached < direct)
    }

    @Test fun zoomedRedrawSkipsOffscreenStrokesAndLooksTheSame() {
        val size = 4000f
        val strokes = scene(800, size)
        val w = 720; val h = 720
        // 4x yakınlaştırma: ekranda tuvalin yaklaşık 1/100'ü
        val scale = 4f
        val offset = Offset(-1500f * scale, -1500f * scale)
        val clip = Rect(offset, Size(size * scale, size * scale))
        // Görünmeyenleri atlamanın etkisi (kayıtlı izler kapalıyken ölçülür)
        StrokeRenderer.pictureCacheEnabled = false
        // Isınma
        renderAll(strokes, w, h, scale, offset, clip)
        StrokeRenderer.renderViewportCache(strokes, w, h, scale, offset, clip)
        val old = (0 until 3).map { timeMs { renderAll(strokes, w, h, scale, offset, clip) } }.min()
        val new = (0 until 3).map { timeMs { StrokeRenderer.renderViewportCache(strokes, w, h, scale, offset, clip) } }.min()
        val visible = StrokeRenderer.visibleIn(strokes, StrokeRenderer.visibleCanvasRect(w, h, scale, offset, clip)).size
        println("V13 yakın görünüm: tümü %.1f ms, görünenler (%d/%d) %.1f ms".format(old, visible, strokes.size, new))
        assertTrue("görünmeyenler atlandı", visible < strokes.size / 5)
        assertTrue("en az 2 kat hızlı ($old → $new ms)", new * 2 < old)

        // Görüntü birebir aynı
        val a = renderAll(strokes, w, h, scale, offset, clip).asAndroidBitmap()
        val b = StrokeRenderer.renderViewportCache(strokes, w, h, scale, offset, clip).asAndroidBitmap()
        val pa = IntArray(w * h); val pb = IntArray(w * h)
        a.getPixels(pa, 0, w, 0, 0, w, h); b.getPixels(pb, 0, w, 0, 0, w, h)
        var diff = 0
        for (i in pa.indices) if (pa[i] != pb[i]) diff++
        assertEquals("aynı görüntü", 0, diff)
        StrokeRenderer.pictureCacheEnabled = true
    }
}
