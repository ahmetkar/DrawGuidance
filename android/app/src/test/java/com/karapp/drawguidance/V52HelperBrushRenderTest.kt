package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushParams
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.FxParams
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.RasterStrokes
import com.karapp.drawguidance.ui.drawing.StrokeRenderer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v52 klonlama, onarma ve karıştırıcı fırçaları tuvalin gerçek çiziminde (görünüm önbelleği) etkili olmalı: altındaki
 * aynı katman içeriğini (fırça izleri ve resimler) okuyup değiştirir.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V52HelperBrushRenderTest {
    @After fun tearDown() { stopKoin(); RasterStrokes.clear() }

    private fun line(id: String, color: String, type: BrushType, y: Float, x0: Float, x1: Float, size: Float, order: Int, params: BrushParams? = null, layer: String? = null) =
        Stroke(id, (0..20).map { StrokePoint(x0 + (x1 - x0) * it / 20f, y) }, color, type, size, 1f, order, params = params, layerId = layer)

    /** Sol yarı kırmızı, sağ yarı mavi kalın çizgiler (yükseklik 100..300). */
    private fun scene(layer: String? = null): List<Stroke> {
        val out = ArrayList<Stroke>()
        var o = 0
        for (y in 100..300 step 20) {
            out += line("r$y", "#FF0000", BrushType.MARKER, y.toFloat(), 20f, 200f, 30f, o++, layer = layer)
            out += line("b$y", "#0000FF", BrushType.MARKER, y.toFloat(), 200f, 380f, 30f, o++, layer = layer)
        }
        return out
    }

    private fun render(strokes: List<Stroke>): IntArray {
        val w = 400; val h = 400
        val bmp = StrokeRenderer.renderViewportCache(strokes, w, h, 1f, Offset.Zero, Rect(Offset.Zero, Size(400f, 400f))).asAndroidBitmap()
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        return px
    }

    private fun red(p: Int) = (p shr 16) and 0xFF
    private fun blue(p: Int) = p and 0xFF

    @Test fun cloneCopiesSourceInRealRender() {
        val base = scene()
        val clone = line("c", "#000000", BrushType.FX_CLONE, 200f, 60f, 140f, 40f, base.size, BrushParams(fx = FxParams(srcDx = 220f, srcDy = 0f)))
        val before = render(base)
        val after = render(base + clone)
        val i = 200 * 400 + 100
        println("klon: önce r=${red(before[i])} b=${blue(before[i])} sonra r=${red(after[i])} b=${blue(after[i])}")
        assertTrue("klon mavi getirir", blue(after[i]) > 150 && red(after[i]) < 100)
    }

    @Test fun cloneWorksOnNamedLayer() {
        val base = scene(layer = "L2")
        val clone = line("c", "#000000", BrushType.FX_CLONE, 200f, 60f, 140f, 40f, base.size, BrushParams(fx = FxParams(srcDx = 220f, srcDy = 0f)), layer = "L2")
        val after = render(base + clone)
        val i = 200 * 400 + 100
        assertTrue("katmanda klon mavi getirir", blue(after[i]) > 150 && red(after[i]) < 100)
    }

    @Test fun healBringsTexture() {
        val base = scene()
        val heal = line("h", "#000000", BrushType.FX_HEAL, 200f, 150f, 250f, 40f, base.size, BrushParams(fx = FxParams(srcDx = 0f, srcDy = 60f)))
        val before = render(base)
        val after = render(base + heal)
        var diff = 0
        for (y in 180..220) for (x in 140..260) { val i = y * 400 + x; if (before[i] != after[i]) diff++ }
        println("onarma: değişen piksel $diff")
        assertTrue("onarma pikselleri değiştirir ($diff)", diff > 200)
    }

    @Test fun mixerSmearsColors() {
        val base = scene()
        val mix = line("m", "#00FF00", BrushType.FX_MIXER, 200f, 120f, 300f, 40f, base.size)
        val before = render(base)
        val after = render(base + mix)
        val i = 200 * 400 + 230
        println("karıştırıcı: önce r=${red(before[i])} b=${blue(before[i])} sonra r=${red(after[i])} b=${blue(after[i])}")
        assertTrue("kırmızı mavinin üstüne sürüklenir", red(after[i]) > red(before[i]) + 30)
    }
}
