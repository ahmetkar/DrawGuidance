package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.sin

/** Döndür / Ölçekle modları (hareket ve hızlı düğmeler), geri alma, yakınlaştırma ve tuval genişletme. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorTransformTest : EditorTestBase() {

    private fun extent(strokes: List<Stroke>): Pair<Float, Float> {
        val xs = strokes.flatMap { s -> s.points.map { it.x } }
        val ys = strokes.flatMap { s -> s.points.map { it.y } }
        return (xs.max() - xs.min()) to (ys.max() - ys.min())
    }

    private fun element1() = canvas().strokes.take(2)

    @Test
    fun rotateScaleUndoZoomAndExpand() {
        createCanvasAndOpen("Dönüşüm")
        val size = canvasSize()
        val s1 = Offset(size.x * 0.25f, size.y * 0.35f)
        drag(s1, Offset(300f, 0f))
        waitFor { canvas().strokes.size == 1 }
        val map = mappingFrom(s1, 300f)
        drag(s1 + Offset(60f, -60f), Offset(0f, 120f))
        drag(Offset(size.x * 0.25f, size.y * 0.65f), Offset(300f, 0f))
        waitFor { canvas().strokes.size == 3 }
        val untouched = canvas().strokes[2]

        // Seç modunda ilk elemanı seç
        mode("Seç")
        compose.onNodeWithTag("drawing_canvas").performTouchInput { click(s1 + Offset(200f, 0f)) }
        waitForText("1 eleman seçili")

        // Döndür: hareketle ~90° (15°'lik katlara yapışır) → yatay şekil dikey olur
        mode("Döndür")
        val (w0, h0) = extent(element1())
        assertTrue(w0 > h0)
        val bounds = ElementOps.selectionBounds(canvas().strokes, canvas().strokes.take(2).map { it.id }.toSet())!!
        val pivot = map.toScreen(bounds.center.x, bounds.center.y)
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(pivot + Offset(220f, 0f))
            for (i in 1..12) {
                val a = Math.toRadians(90.0 * i / 12)
                moveTo(pivot + Offset((220 * cos(a)).toFloat(), (220 * sin(a)).toFloat()))
            }
            up()
        }
        waitFor { extent(element1()).second > extent(element1()).first }
        val (w1, h1) = extent(element1())
        assertEquals(w0, h1, 1f)
        assertEquals(h0, w1, 1f)
        assertEquals(untouched, canvas().strokes[2])

        // Hızlı düğme: +90° → tekrar yatay
        compose.onNodeWithText("+90°").performClick()
        waitFor { extent(element1()).first > extent(element1()).second }

        // Ölçekle: +10% düğmesi orantılı büyütür
        mode("Ölçekle")
        val (w2, h2) = extent(element1())
        compose.onNodeWithText("+10%").performClick()
        waitFor { extent(element1()).first > w2 + 1f }
        assertEquals(w2 * 1.1f, extent(element1()).first, 1f)
        assertEquals(h2 * 1.1f, extent(element1()).second, 1f)

        // Sağ kenar tutamacından sürükleme sadece yatayda uzatır
        val (w3, h3) = extent(element1())
        val box = ElementOps.selectionBounds(canvas().strokes, canvas().strokes.take(2).map { it.id }.toSet())!!
        val rightHandle = map.toScreen(box.right, box.center.y)
        drag(rightHandle, Offset(150f, 0f), steps = 10)
        waitFor { extent(element1()).first > w3 + 5f }
        val expectedSx = 1f + (150f / map.scale) / box.width
        assertEquals(w3 * expectedSx, extent(element1()).first, w3 * 0.02f)
        assertEquals(h3, extent(element1()).second, 0.5f)

        // Geri al: son ölçekleme geri alınır
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { kotlin.math.abs(extent(element1()).first - w3) < 0.5f }

        // İki parmakla yakınlaştırma: çizim oluşturmaz, oran artar
        mode("Çiz")
        assertEquals(100, zoomPercent())
        val center = Offset(size.x / 2f, size.y / 2f)
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            pinch(center - Offset(60f, 0f), center - Offset(180f, 0f), center + Offset(60f, 0f), center + Offset(180f, 0f))
        }
        waitFor { zoomPercent() > 150 }
        assertEquals(3, canvas().strokes.size)
        // Yakınlaştırma yalnızca elle: mod menüsünde Yakınlaştır yok
        assertTrue(compose.onAllNodesWithContentDescription("Yakınlaştır").fetchSemanticsNodes().isEmpty())

        // Yakınken çizilen çizgi tuvalde yakınlaştırma oranı kadar kısa olur (koordinatlar doğru dönüşüyor)
        val zoom = zoomPercent() / 100f
        drag(center + Offset(-150f, 100f), Offset(300f, 0f))
        waitFor { canvas().strokes.size == 4 }
        val zoomed = canvas().strokes[3]
        assertEquals(300f / map.scale / zoom, zoomed.points.last().x - zoomed.points.first().x, 2f)
        compose.onNodeWithContentDescription("Yakınlaştırma %", substring = true).performClick()
        waitFor { zoomPercent() == 100 }

        // Genişlet: sağ kenar sağa çekilince genişlik artar
        mode("Genişlet")
        val c0 = canvas()
        val rightEdge = map.toScreen(c0.width.toFloat(), c0.height / 2f)
        drag(rightEdge, Offset(150f, 0f), steps = 10)
        val expectedWidth = Math.round(c0.width + 150f / map.scale)
        waitFor { canvas().width != c0.width }
        assertEquals(expectedWidth.toFloat(), canvas().width.toFloat(), 1.5f)
        assertEquals(c0.height, canvas().height)
        waitForText("${canvas().width} × ${canvas().height}")

        // Sol kenar sola çekilince tuval sola büyür, çizimler yeni orijine göre kayar
        val c1 = canvas()
        val leftEdge = map.toScreen(0f, c1.height / 2f)
        drag(leftEdge, Offset(-120f, 0f), steps = 10)
        waitFor { canvas().width != c1.width }
        val shift = 120f / map.scale
        assertEquals(c1.width + shift, canvas().width.toFloat(), 1.5f)
        assertEquals(c1.strokes[2].points.first().x + Math.round(shift), canvas().strokes[2].points.first().x, 1.5f)
        assertTrue(compose.onAllNodesWithText("eleman seçili", substring = true).fetchSemanticsNodes().isEmpty())
    }
}
