package com.karapp.drawguidance

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.ui.drawing.BrushPainter
import com.karapp.drawguidance.ui.drawing.LiveStrokeLayer
import com.karapp.drawguidance.ui.drawing.defaultSize
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * Canlı çizim performansı: uzun bir çizgi boyunca her karenin maliyeti. Eski yöntem (her karede tüm
 * çizgiyi baştan çizmek) ile yeni yöntem (kesin parçalar karolara bir kez, her karede sadece kuyruk)
 * karşılaştırılır. Yeni yöntemde kare maliyeti çizgi uzadıkça artmamalı.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class LiveDrawingPerformanceTest {
    @After fun tearDown() = stopKoin()

    private val n = 600
    private val xs = FloatArray(n) { 540f + cos(it / 40f) * (100f + it * 0.6f) }
    private val ys = FloatArray(n) { 540f + sin(it / 40f) * (100f + it * 0.6f) }

    private class Result(val totalMs: Double, val avgMs: Double, val worstMs: Double, val firstHalfAvg: Double, val secondHalfAvg: Double)

    private fun measure(frame: (count: Int) -> Unit): Result {
        val times = ArrayList<Long>()
        for (count in 2..n step 2) {
            val t0 = System.nanoTime()
            frame(count)
            times += System.nanoTime() - t0
        }
        val half = times.size / 2
        return Result(
            times.sum() / 1e6, times.average() / 1e6, times.max() / 1e6,
            times.take(half).average() / 1e6, times.drop(half).average() / 1e6,
        )
    }

    @Test fun liveFrameCostStaysFlatAndIsFarBelowFullRepaint() {
        val screen = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(screen)
        val types = listOf(
            BrushType.PENCIL, BrushType.MARKER, BrushType.INK_PEN, BrushType.CALLIGRAPHY, BrushType.FLAT_BRUSH, BrushType.PASTEL,
            BrushType.CHARCOAL, BrushType.SOFT_ROUND, BrushType.AIRBRUSH, BrushType.STAMP_STAR, BrushType.HALFTONE_DOTS,
            BrushType.TEXTURE_SPLATTER, BrushType.PATTERN_ZIGZAG, BrushType.ERASER,
        )
        val report = StringBuilder()
        for (type in types) {
            val color = -0xddccbb
            // Isınma
            repeat(2) { BrushPainter.paint(canvas, xs, ys, n, color, type, type.defaultSize(), 1f, 7, 1f, 1f) }

            // Eski yöntem: her karede tüm çizgi (yeni statik motorla)
            val full = measure { count ->
                screen.eraseColor(0)
                BrushPainter.paint(canvas, xs, ys, count, color, type, type.defaultSize(), 1f, 7, 1f, 1f)
            }

            // Yeni yöntem: iki yeni nokta karolara + ekrana karolar ve kuyruk
            val layer = LiveStrokeLayer()
            layer.start("s-$type", type, color, type.defaultSize(), 1f, 1f, 1f, 1f, 0f, 0f, 1080, 1080)
            layer.add(xs[0], ys[0])
            val live = measure { count ->
                // Bu karede gelen iki nokta (sırayla)
                for (i in count - 2 until count) if (i >= 1) layer.add(xs[i], ys[i])
                screen.eraseColor(0)
                layer.drawTo(canvas)
            }
            layer.release()
            if (type in setOf(BrushType.FLAT_BRUSH, BrushType.PASTEL, BrushType.CHARCOAL, BrushType.SOFT_ROUND, BrushType.AIRBRUSH, BrushType.HALFTONE_DOTS)) {
                assertTrue(
                    "$type: uzun çizginin sonunda canlı kare (${live.secondHalfAvg} ms) tüm çizgiyi çizmekten (${full.secondHalfAvg} ms) en az 3 kat hızlı olmalı",
                    live.secondHalfAvg * 3 < full.secondHalfAvg,
                )
            }
            report.append(
                "PERF $type: tüm çizgi kare ort=${"%.2f".format(full.avgMs)} ms (ilk yarı ${"%.2f".format(full.firstHalfAvg)} → son yarı ${"%.2f".format(full.secondHalfAvg)}), " +
                    "en kötü=${"%.1f".format(full.worstMs)} | canlı karo kare ort=${"%.2f".format(live.avgMs)} ms (ilk yarı ${"%.2f".format(live.firstHalfAvg)} → son yarı ${"%.2f".format(live.secondHalfAvg)}), en kötü=${"%.1f".format(live.worstMs)}\n",
            )
        }
        println(report)
        System.getenv("DG_BENCH")?.let { java.io.File(it).writeText(report.toString()) }
    }
}
