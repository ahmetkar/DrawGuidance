package com.karapp.drawguidance

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.DiagramSpec
import com.karapp.drawguidance.data.model.GraphSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.ui.drawing.DiagramRenderer
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapeKind
import com.karapp.drawguidance.ui.drawing.ShapeRenderer
import com.karapp.drawguidance.ui.drawing.StrokeRenderer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID

/**
 * v9 performansı: çok sayıda vektör şekil, diyagram ve yazı içeren yoğun bir sayfanın kare maliyeti
 * ve çok sayfalı içeriğin kodlanma/çözülme süresi. Şekil yolları önbelleğe alındığı için ikinci
 * kareden sonra maliyet belirgin şekilde düşmelidir.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V9PerformanceTest {
    @After fun tearDown() = stopKoin()

    private val style = ShapeStyle(strokeColorHex = "#1E1E1E", strokeWidth = 4f, fillColorHex = "#EEF2FF", cornerRadius = 8f)
    private val shapeKinds = listOf(
        ShapeKind.HEXAGON, ShapeKind.CUBE, ShapeKind.STAR, ShapeKind.CYLINDER, ShapeKind.FUNCTION_PLOT,
        ShapeKind.PIE_CHART, ShapeKind.BUTTERFLY, ShapeKind.TREE, ShapeKind.HOUSE, ShapeKind.FLOWER,
    )

    /** 60 şekil + 6 diyagram + 20 yazı + 100 fırça izi içeren yoğun bir sayfa. */
    private fun heavyPage(): List<Stroke> {
        val out = ArrayList<Stroke>()
        var order = 0
        for (i in 0 until 60) {
            val col = i % 6
            val row = i / 6
            val x = 40f + col * 190f
            val y = 40f + row * 170f
            out += ShapeFactory.build(
                shapeKinds[i % shapeKinds.size], Offset(x, y), Offset(x + 160f, y + 140f), style,
                GraphSpec(formula = "sin(x)"), id = "s$i", order = order++,
            ) ?: continue
        }
        for (i in 0 until 6) {
            val x = 60f + (i % 3) * 360f
            val y = 1200f + (i / 3) * 260f
            val kind = DiagramKind.entries[i % DiagramKind.entries.size]
            val nodes = DiagramRenderer.layout(kind, DiagramRenderer.defaultNodes(kind))
            out += Stroke(
                id = "d$i",
                points = listOf(StrokePoint(x, y), StrokePoint(x + 320f, y), StrokePoint(x + 320f, y + 230f), StrokePoint(x, y + 230f)),
                colorHex = "#1E1E1E", brushType = BrushType.DIAGRAM, sizePx = 3f, opacity = 1f, order = order++,
                diagram = DiagramSpec(kind, nodes, DiagramRenderer.defaultEdges(kind, nodes)),
            )
        }
        for (i in 0 until 20) {
            val x = 40f + (i % 4) * 300f
            val y = 1700f + (i / 4) * 60f
            out += Stroke(
                id = "t$i",
                points = listOf(StrokePoint(x, y), StrokePoint(x + 260f, y), StrokePoint(x + 260f, y + 48f), StrokePoint(x, y + 48f)),
                colorHex = "#1E1E1E", brushType = BrushType.TEXT, sizePx = 40f, opacity = 1f, order = order++,
                text = TextSpec("Örnek yazı $i"),
            )
        }
        for (i in 0 until 100) {
            val y = 2050f + (i % 20) * 12f
            out += Stroke(
                id = "b$i",
                points = (0..24).map { StrokePoint(40f + it * 45f, y + (it % 5) * 4f) },
                colorHex = "#2B3A55", brushType = BrushType.PENCIL, sizePx = 6f, opacity = 1f, order = order++,
            )
        }
        return out
    }

    @Test fun heavyPageStaysFastAfterTheFirstFrameAndMultiPageContentEncodesQuickly() {
        val page = heavyPage()
        val bitmap = Bitmap.createBitmap(1240, 2200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val report = StringBuilder()

        fun frame(): Double {
            val t0 = System.nanoTime()
            canvas.drawColor(android.graphics.Color.WHITE)
            page.forEach { stroke ->
                when (stroke.brushType) {
                    BrushType.SHAPE -> ShapeRenderer.draw(canvas, stroke)
                    BrushType.DIAGRAM -> DiagramRenderer.draw(canvas, stroke)
                    else -> Unit
                }
            }
            return (System.nanoTime() - t0) / 1e6
        }

        val first = frame()
        val warm = (1..12).map { frame() }
        val warmAvg = warm.average()
        report.append("ilk kare %.2f ms, önbellekli ortalama %.2f ms\n".format(first, warmAvg))
        assertTrue("önbellekli kare maliyeti düşmeli: ilk=$first ort=$warmAvg", warmAvg <= first)
        assertTrue("yoğun sayfa karesi 60 ms altında olmalı: $warmAvg", warmAvg < 60.0)

        // 16 sayfalık içerik: kodlama/çözme ve değişmeyen çizimlerin yeniden kullanımı
        val pages = List(16) { page }
        val cache = ContentCodec.EncodeCache()
        val t0 = System.nanoTime()
        val bytes = ContentCodec.encodePages(pages, cache)
        val firstEncode = (System.nanoTime() - t0) / 1e6
        val t1 = System.nanoTime()
        val bytes2 = ContentCodec.encodePages(pages, cache)
        val cachedEncode = (System.nanoTime() - t1) / 1e6
        val t2 = System.nanoTime()
        val decoded = ContentCodec.decodePages(bytes)
        val decodeMs = (System.nanoTime() - t2) / 1e6
        report.append("16 sayfa: ilk kodlama %.1f ms, önbellekli %.1f ms, çözme %.1f ms, %d KB\n"
            .format(firstEncode, cachedEncode, decodeMs, bytes.size / 1024))
        println(report)

        // Yakınlaştırıldığında görünmeyen elemanlar hiç çizilmez
        val zoomed = Bitmap.createBitmap(1240, 300, Bitmap.Config.ARGB_8888)
        val zoomCanvas = Canvas(zoomed)
        fun zoomFrame(): Double {
            val t0 = System.nanoTime()
            zoomCanvas.drawColor(android.graphics.Color.WHITE)
            page.forEach { stroke ->
                when (stroke.brushType) {
                    BrushType.SHAPE -> ShapeRenderer.draw(zoomCanvas, stroke)
                    BrushType.DIAGRAM -> DiagramRenderer.draw(zoomCanvas, stroke)
                    else -> Unit
                }
            }
            return (System.nanoTime() - t0) / 1e6
        }
        repeat(3) { zoomFrame() }
        val zoomAvg = (1..10).map { zoomFrame() }.average()
        report.append("görünür alan dar iken kare %.2f ms\n".format(zoomAvg))
        println(report)
        assertTrue("alan dışı elemanlar atlanmalı: dar=$zoomAvg tam=$warmAvg", zoomAvg < warmAvg)

        assertTrue("önbellekli kodlama daha hızlı olmalı", cachedEncode <= firstEncode)
        assertTrue("kodlama sonucu kararlı", bytes.contentEquals(bytes2))
        assertTrue("16 sayfa 1500 ms altında kodlanmalı: $firstEncode", firstEncode < 1500.0)
        assertTrue("16 sayfa 1500 ms altında çözülmeli: $decodeMs", decodeMs < 1500.0)
        assertTrue(decoded.size == 16 && decoded.all { it.size == page.size })
    }

    @Test fun shapePathCacheIsReusedWhilePointsDoNotChange() {
        val shape = ShapeFactory.build(ShapeKind.TREE, Offset(0f, 0f), Offset(400f, 400f), style, null, UUID.randomUUID().toString(), 0)!!
        val bitmap = Bitmap.createBitmap(420, 420, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        fun draw(s: Stroke): Double {
            val t0 = System.nanoTime()
            repeat(40) { ShapeRenderer.draw(canvas, s) }
            return (System.nanoTime() - t0) / 1e6
        }
        // Soğuk: her çizimde yeni nokta listesi (yol her seferinde yeniden kurulur); sıcak: aynı noktalar (önbellek)
        fun drawRebuilt(s: Stroke): Double {
            val t0 = System.nanoTime()
            repeat(40) { ShapeRenderer.draw(canvas, s.copy(points = s.points.toList())) }
            return (System.nanoTime() - t0) / 1e6
        }
        draw(shape); drawRebuilt(shape)
        val cold = (0 until 3).minOf { drawRebuilt(shape) }
        val warm = (0 until 3).minOf { draw(shape) }
        // Noktalar değişmediği sürece yol yeniden kurulmaz
        assertTrue("önbellekli çizim en az bir kat hızlı olmalı: soğuk=$cold sıcak=$warm", warm <= cold)
        assertTrue("40 çizim 200 ms altında: $warm", warm < 200.0)
    }
}
