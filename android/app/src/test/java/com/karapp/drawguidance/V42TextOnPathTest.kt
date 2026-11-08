package com.karapp.drawguidance

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapePath
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import com.karapp.drawguidance.ui.drawing.TextOnPath
import com.karapp.drawguidance.ui.drawing.TextRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v42 yol üzerinde yazı: yola yerleştirme, kayıt (içerik biçimi v9), çizim ve yoldan çıkarma. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class V42TextOnPathModelTest {
    @org.junit.After fun stopKoin() = org.koin.core.context.stopKoin()

    private fun penPath(vararg xy: Float, closed: Boolean = false): Stroke {
        val pts = (xy.indices step 2).map { Offset(xy[it], xy[it + 1]) }
        return ShapeFactory.fromPaths(ShapeRegistry.FREE_PATH, listOf(ShapePath(pts, closed = closed, nodes = pts.indices.toList())), ShapeStyle(), null, "path", 0)!!
    }

    private fun text(content: String = "Yol boyunca yazı") = Stroke(
        "t1", TextRenderer.quadAt(TextSpec(content), 40f, 100f, 100f), "#202020", BrushType.TEXT, 40f, 1f, 1,
        text = TextSpec(content, align = TextAlignKind.CENTER),
    )

    @Test
    fun attachCopiesPathIntoTextBox() {
        val path = penPath(100f, 600f, 300f, 400f, 500f, 400f, 700f, 600f)
        assertTrue(TextOnPath.canHost(path))
        assertTrue(TextOnPath.isPenPath(path))
        assertFalse(TextOnPath.canHost(text()))
        val on = TextOnPath.attach(text(), path)!!
        val spec = on.text!!.path!!
        val m = 40f * 1.4f
        assertEquals(100f - m, on.points[0].x, 0.01f); assertEquals(400f - m, on.points[0].y, 0.01f)
        assertEquals(600f + 2 * m, spec.w, 0.01f); assertEquals(200f + 2 * m, spec.h, 0.01f)
        // Yerel yol: kutunun sol-üstüne göre
        assertEquals(m, spec.points.first().x, 0.01f); assertEquals(200f + m, spec.points.first().y, 0.01f)
        assertEquals(spec.w to spec.h, TextRenderer.measure(on.text!!, on.sizePx))
        // Yol bulunur (üstteki önce; tolerans içinde)
        assertEquals(path.id, TextOnPath.hostAt(listOf(path, on), Offset(400f, 405f), 20f)?.id)
        assertNull(TextOnPath.hostAt(listOf(path, on), Offset(400f, 520f), 20f))
        // Ayarlar ve yoldan çıkarma
        val adj = TextOnPath.adjust(on, start = 0.3f, below = true, reversed = true)!!.text!!.path!!
        assertEquals(0.3f, adj.start, 0f); assertTrue(adj.below && adj.reversed)
        val plain = TextOnPath.detach(on)!!
        assertNull(plain.text!!.path)
        assertEquals(TextRenderer.measure(plain.text!!, 40f).first, plain.points[1].x - plain.points[0].x, 0.5f)
    }

    @Test
    fun savedWithNewFormatAndReadBack() {
        val on = TextOnPath.attach(text(), penPath(0f, 300f, 200f, 100f, 400f, 300f))!!
        val adjusted = TextOnPath.adjust(on, below = true)!!
        val bytes = ContentCodec.encode(listOf(penPath(0f, 0f, 10f, 10f), adjusted))
        assertEquals(9, bytes[2].toInt())
        val back = ContentCodec.decode(bytes).single { it.brushType == BrushType.TEXT }
        assertEquals(adjusted.text!!.path, back.text!!.path)
        assertEquals("Yol boyunca yazı", back.text!!.content)
        // Yol yazısı olmayan belge eski biçimde kalır (eski sürümler açabilsin)
        assertTrue(ContentCodec.encode(listOf(text()))[2].toInt() < 9)
    }

    @Test
    fun drawsAlongTheCurve() {
        // Yay biçimli yol: yazı yayın üstünde, kutunun alt köşelerinde boş
        val pts = FloatArray(42) { i -> val k = i / 2; val t = k / 20f; if (i % 2 == 0) 100f + 600f * t else 500f - 300f * kotlin.math.sin(Math.PI * t).toFloat() }
        val on = TextOnPath.attach(text("ÇİZGİ ROMAN YAZISI BURADA"), penPath(*pts))!!
        val bmp = Bitmap.createBitmap(800, 700, Bitmap.Config.ARGB_8888)
        TextRenderer.draw(android.graphics.Canvas(bmp), on, android.graphics.Color.BLACK)
        fun inked(x0: Int, y0: Int, x1: Int, y1: Int): Int {
            var n = 0
            for (y in y0 until y1) for (x in x0 until x1) if (android.graphics.Color.alpha(bmp.getPixel(x, y)) > 100) n++
            return n
        }
        assertTrue("yayın tepesinde yazı", inked(300, 140, 500, 220) > 150)
        assertTrue("yayın altında boş", inked(330, 300, 470, 480) < 20)
        assertTrue("sol yanda yazı", inked(100, 330, 260, 500) > 60)
    }
}

/** v42 yazı düzenleyicisindeki "Yol üzerinde yazı" düğmesi: kalem yolu çizilir, yazı yola yerleşir. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V42TextOnPathUiTest : V33EditorBase() {

    private fun penPathAt(y: Float) = compose.runOnIdle {
        vm().startPen()
        val c = vm().canvas.value!!
        vm().penTap(Offset(c.width * 0.15f, y), 4f)
        vm().penTap(Offset(c.width * 0.5f, y - 120f), 4f)
        vm().penTap(Offset(c.width * 0.85f, y), 4f)
        vm().penFinish(closed = false)
    }

    @Test
    fun buttonPlacesTextOnPenPath() {
        createCanvasAndOpen("Yol yazısı")
        val size = canvasSize()
        // Yol yokken düğme kapalı
        addMenu("Yazı ekle")
        tap(Offset(size.x * 0.3f, size.y * 0.2f))
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        field().performTextInput("Merhaba")
        assertFalse("yol yokken kapalı", isEnabled())
        compose.onNodeWithContentDescription("Yazıyı bitir").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { vm().canvas.value!!.strokes.any { it.text != null } }

        // Tek yol: yazı doğrudan yerleşir
        val h = vm().canvas.value!!.height.toFloat()
        penPathAt(h * 0.6f)
        waitFor { vm().canvas.value!!.strokes.any(TextOnPath::canHost) }
        val textId = vm().canvas.value!!.strokes.single { it.text != null }.id
        compose.runOnIdle { vm().startTextOnPathFor(textId) }
        waitFor { vm().canvas.value!!.strokes.single { it.id == textId }.text?.path != null }

        // İki yol: dokunulan yol seçilir
        penPathAt(h * 0.3f)
        waitFor { vm().canvas.value!!.strokes.count(TextOnPath::canHost) == 2 }
        compose.runOnIdle { vm().startTextOnPathFor(textId) }
        waitFor { exists("text_path_pick") }
        shot("v42-03-text-path-pick")
        val c = vm().canvas.value!!
        val second = c.strokes.last(TextOnPath::canHost)
        val target = TextOnPath.points(second)[TextOnPath.points(second).size / 2]
        compose.runOnIdle { assertTrue(vm().pickTextPath(target, 10f)) }
        waitFor { !exists("text_path_pick") }
        val placed = vm().canvas.value!!.strokes.single { it.id == textId }
        assertNotNull(placed.text!!.path)
        // Yazı kutusu ikinci yolun çevresinde
        assertTrue(placed.points.minOf { it.y } < target.y && placed.points.maxOf { it.y } > target.y)
        shot("v42-04-text-on-path")
        // Yoldan çıkar
        compose.runOnIdle { vm().detachTextFromPath(textId) }
        waitFor { vm().canvas.value!!.strokes.single { it.id == textId }.text?.path == null }
    }

    private fun isEnabled(): Boolean =
        !compose.onNodeWithTag("text_on_path").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
}
