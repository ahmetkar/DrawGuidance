package com.karapp.drawguidance

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.ColorAdjust
import com.karapp.drawguidance.data.model.ColorAdjustOps
import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.model.LayerBlend
import com.karapp.drawguidance.data.model.LayerOps
import com.karapp.drawguidance.data.model.ShapeSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.SubPath
import com.karapp.drawguidance.data.model.V56Flags
import com.karapp.drawguidance.data.model.V56Presets
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.GlobalContext
import kotlin.math.abs

/** v56 aşama 7 saf kurallar: renk ayarları (HSL, renk dengesi, eğriler), katman çoğalt/birleştir, hazır ayarlar. */
class V56ColorRulesTest {
    private fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
    private fun near(a: Int, b: Int, tol: Int = 1) = (0..2).all { abs(ch(a, it * 8) - ch(b, it * 8)) <= tol } && (a ushr 24) == (b ushr 24)

    @Test fun identityAndHsl() {
        val red = 0xFFFF0000.toInt()
        assertEquals(red, ColorAdjustOps.apply(red, ColorAdjust()))
        assertTrue(near(0xFF00FFFF.toInt(), ColorAdjustOps.apply(red, ColorAdjust(hue = 180f))))
        val gray = ColorAdjustOps.apply(red, ColorAdjust(saturation = -100f))
        assertEquals(ch(gray, 16), ch(gray, 8)); assertEquals(ch(gray, 8), ch(gray, 0))
        assertEquals(0xFFFFFFFF.toInt(), ColorAdjustOps.apply(red, ColorAdjust(lightness = 100f)))
        assertEquals(0xFF000000.toInt(), ColorAdjustOps.apply(red, ColorAdjust(lightness = -100f)))
        // Renklendir: gri bir renk mavi tona çekilir
        val blue = ColorAdjustOps.apply(0xFF808080.toInt(), ColorAdjust(hue = 240f, colorize = true))
        assertTrue(ch(blue, 0) > ch(blue, 16) && ch(blue, 0) > ch(blue, 8))
        // Saydam piksel değişmez, alfa korunur
        assertEquals(0, ColorAdjustOps.apply(0, ColorAdjust(hue = 90f)))
        assertEquals(0x80, ColorAdjustOps.apply(0x80FF0000.toInt(), ColorAdjust(hue = 90f)) ushr 24)
    }

    @Test fun balanceAndCurves() {
        val mid = 0xFF808080.toInt()
        val warm = ColorAdjustOps.apply(mid, ColorAdjust().withBalance(1, 0, 100f).copy(preserveLuminosity = false))
        assertTrue("b1", ch(warm, 16) > 0x80)
        val kept = ColorAdjustOps.apply(mid, ColorAdjust().withBalance(1, 0, 100f))
        val lum = { c: Int -> 0.299 * ch(c, 16) + 0.587 * ch(c, 8) + 0.114 * ch(c, 0) }
        assertEquals(lum(mid), lum(kept), 1.5)
        // Gölge ayarı parlak renge etki etmez
        assertEquals("white", 0xFFFFFFFF.toInt(), ColorAdjustOps.apply(0xFFFFFFFF.toInt(), ColorAdjust().withBalance(0, 2, 100f).copy(preserveLuminosity = false)))
        // Ters eğri
        val invert = ColorAdjust(curves = listOf(ColorAdjust.CURVE_X.reversed()) + ColorAdjust.IDENTITY_CURVES.drop(1))
        assertTrue("b2", near(0xFF000000.toInt(), ColorAdjustOps.apply(0xFFFFFFFF.toInt(), invert)))
        assertTrue("b3", near(0xFF00FFFF.toInt(), ColorAdjustOps.apply(0xFFFF0000.toInt(), invert)))
        val lut = ColorAdjustOps.curveLut(ColorAdjust.CURVE_X)
        assertTrue("b4", (0..255).all { abs(lut[it] - it) <= 1 })
        // Yalnız kırmızı kanal eğrisi
        val redUp = ColorAdjust().withCurve(1, 2, 0.9f)
        val r = ColorAdjustOps.apply(mid, redUp)
        assertTrue("b5", ch(r, 16) > 0xD0 && ch(r, 8) == 0x80)
    }

    @Test fun hexAndStrokeMapping() {
        val m = ColorAdjustOps.Mapper(ColorAdjust(hue = 180f))
        assertEquals("#00FFFF", ColorAdjustOps.mapHex("#FF0000", m))
        assertEquals("#8000FFFF", ColorAdjustOps.mapHex("#80FF0000", m))
        assertEquals("bad", ColorAdjustOps.mapHex("bad", m))
        val shape = Stroke(
            "s", listOf(StrokePoint(0f, 0f), StrokePoint(10f, 0f), StrokePoint(10f, 10f)), "#FF0000", BrushType.SHAPE, 2f, 1f, 0,
            shape = ShapeSpec("tri", listOf(SubPath(3, true)), ShapeStyle(strokeColorHex = "#FF0000", fillColorHex = "#FF0000")),
        )
        val img = Stroke("i", listOf(StrokePoint(0f, 0f)), "#FF0000", BrushType.IMAGE, 1f, 1f, 1, assetId = "a")
        val out = ColorAdjustOps.mapStrokes(listOf(shape, img), setOf("s", "i"), ColorAdjust(hue = 180f))
        assertEquals("#00FFFF", out[0].colorHex)
        assertEquals("#00FFFF", out[0].shape!!.style.fillColorHex)
        assertEquals("#00FFFF", out[0].shape!!.style.strokeColorHex)
        assertEquals(img, out[1])
        assertEquals(listOf(shape), ColorAdjustOps.mapStrokes(listOf(shape), setOf("x"), ColorAdjust(hue = 90f)))
    }

    private fun s(id: String, layer: String?, group: String? = null) =
        Stroke(id, listOf(StrokePoint(0f, 0f), StrokePoint(5f, 5f)), "#000000", BrushType.INK_PEN, 2f, 1f, 0, layerId = layer, groupId = group)

    @Test fun duplicateAndMerge() {
        val layers = listOf(Layer("a", "A"), Layer("b", "B", opacity = 0.5f), Layer("c", "C"))
        val pages = listOf(listOf(s("1", null), s("2", "b", "g"), s("3", "b", "g"), s("4", "c")), listOf(s("5", "b")))
        var n = 0
        val (l2, p2) = LayerOps.duplicate(layers, pages, "b", "b2", "B (kopya)") { "n${n++}" }
        assertEquals(listOf("a", "b", "b2", "c"), l2.map { it.id })
        assertEquals(0.5f, l2[2].opacity)
        val copies = p2[0].filter { it.layerId == "b2" }
        assertEquals(2, copies.size)
        assertTrue(copies.none { it.id in setOf("2", "3") })
        assertEquals(1, copies.map { it.groupId }.toSet().size); assertNotEquals("g", copies[0].groupId)
        assertEquals(listOf("1", "2", "3"), p2[0].take(3).map { it.id })
        assertEquals("c", p2[0].last().layerId)
        assertEquals(2, p2[1].size)

        assertTrue(LayerOps.needsRaster(layers, setOf("a", "b")))
        assertFalse(LayerOps.needsRaster(layers, setOf("a", "c")))
        val (l3, p3) = LayerOps.mergeVector(layers, pages, setOf("a", "c"))
        assertEquals(listOf("b", "c"), l3.map { it.id })
        assertTrue(p3[0].filter { it.id == "1" || it.id == "4" }.all { it.layerId == "c" })
        // Sıra korunur: a'nın elemanı c'ninkinden önce
        assertTrue(p3[0].indexOfFirst { it.id == "1" } < p3[0].indexOfFirst { it.id == "4" })
        assertEquals(layers to pages, LayerOps.mergeVector(layers, pages, setOf("a")))
        assertEquals("a", LayerOps.below(layers, "b")!!.id)
    }

    @Test fun presetsAndFlags() {
        assertEquals(LayerBlend.SCREEN, V56Presets.layer(V56Presets.Kind.LIGHT).blend)
        assertEquals(LayerBlend.MULTIPLY, V56Presets.layer(V56Presets.Kind.SHADOW).blend)
        assertTrue(V56Flags.enabled(V56Flags.Feature.LAYER_HSL_BALANCE, CanvasKind.COMIC))
        assertFalse(V56Flags.enabled(V56Flags.Feature.LAYER_HSL_BALANCE, CanvasKind.NOTES))
        assertFalse(V56Flags.enabled(V56Flags.Feature.LAYER_DUPLICATE_MERGE, CanvasKind.KIDS))
    }
}

/** v56 aşama 7 düzenleyicide: katman çoğalt/birleştir, katman renkleri (tek geri alma adımı), hazır katman. */
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V56ColorUiTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun openWith(name: String, strokes: List<Stroke>) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking {
            val c = repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = CanvasKind.ART)
            repo().saveStrokes(c.localId, listOf(strokes))
        }
        waitForText(name)
        compose.onAllNodes(hasText(name))[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name && vm().canvas.value?.strokes?.size == strokes.size }
    }

    private fun line(id: String, color: String) =
        Stroke(id, listOf(StrokePoint(100f, 100f), StrokePoint(140f, 120f)), color, BrushType.INK_PEN, 6f, 1f, 0)

    @Test
    fun layerDuplicateMergeAndColorAdjust() {
        openWith("Renkler", listOf(line("a", "#FF0000")))
        val base = vm().layers.value.first().id
        compose.runOnIdle { vm().duplicateLayer(base) }
        waitFor { vm().layers.value.size == 2 && vm().canvas.value!!.strokes.size == 2 }
        val copy = vm().layers.value[1].id
        compose.runOnIdle { vm().previewColorAdjust(ColorAdjust(hue = 180f), copy) }
        compose.runOnIdle { vm().applyColorAdjust(ColorAdjust(hue = 180f), copy) }
        waitFor { vm().canvas.value!!.strokes.map { it.colorHex } == listOf("#FF0000", "#00FFFF") }
        compose.runOnIdle { vm().undo() }
        waitFor { vm().canvas.value!!.strokes.all { it.colorHex == "#FF0000" } }
        compose.runOnIdle { vm().mergeLayers(setOf(base, copy)) }
        waitFor { vm().layers.value.size == 1 && vm().canvas.value!!.strokes.size == 2 }
        compose.runOnIdle { vm().undo() }
        waitFor { vm().layers.value.size == 2 }
        compose.runOnIdle { vm().addPresetLayer(V56Presets.Kind.SHADOW) }
        waitFor { vm().layers.value.size == 3 && vm().layers.value.last().blend == LayerBlend.MULTIPLY && vm().activeLayer.value == vm().layers.value.last().id }
    }

    @Test
    fun propertiesShowLayerColorsWhenNothingSelected() {
        openWith("Katman renkleri", listOf(line("a", "#FF0000")))
        compose.runOnIdle { vm().setMode(com.karapp.drawguidance.ui.screens.canvasedit.EditMode.SELECT); vm().clearSelection() }
        compose.runOnIdle { vm().togglePanel(com.karapp.drawguidance.ui.screens.canvasedit.PanelTab.BRUSH) }
        waitFor { exists("v56_layer_colors") }
        assertTrue(exists("v56_ca_tab_hsl") && exists("v56_ca_colorize") && exists("v56_ca_apply"))
        runCatching { compose.onNodeWithTag("v56_ca_colorize").performScrollTo() }
        compose.onNodeWithTag("v56_ca_colorize").performClick()
        runCatching { compose.onNodeWithTag("v56_ca_apply").performScrollTo() }
        compose.onNodeWithTag("v56_ca_apply").assertIsEnabled()
        compose.onNodeWithTag("v56_ca_reset").performClick()
        // Denetimler yan panelde (dar ekranda kaydırılarak); uygulama yolu VM'den
        val layer = vm().activeLayer.value
        compose.runOnIdle { vm().previewColorAdjust(ColorAdjust(colorize = true), layer) }
        waitFor { vm().canvas.value!!.strokes[0].colorHex != "#FF0000" }
        compose.runOnIdle { vm().applyColorAdjust(ColorAdjust(colorize = true), layer) }
        waitFor { vm().canvas.value!!.strokes[0].colorHex != "#FF0000" }
    }
}
