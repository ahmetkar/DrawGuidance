package com.karapp.drawguidance

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.export.DxfWriter
import com.karapp.drawguidance.data.export.ExportDoc
import com.karapp.drawguidance.data.export.ExportNum
import com.karapp.drawguidance.data.export.PsdWriter
import com.karapp.drawguidance.data.export.SvgWriter
import com.karapp.drawguidance.data.images.V56DocExport
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.model.LayerBlend
import com.karapp.drawguidance.data.model.ShapeSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.SubPath
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel.SaveTab
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v56 aşama 9: SVG, PSD ve DXF yazıcıları (saf) ve Kaydet penceresindeki sekmeler. */
class V56ExportRulesTest {
    private val layers = listOf(Layer("base", "Çizgi"), Layer("l2", "Gölge", opacity = 0.5f, blend = LayerBlend.MULTIPLY), Layer("l3", "Gizli", visible = false))
    private fun rect(id: String, layer: String) = Stroke(id, listOf(StrokePoint(10f, 10f), StrokePoint(90f, 10f), StrokePoint(90f, 60f), StrokePoint(10f, 60f)),
        "#000000", BrushType.SHAPE, 4f, 1f, 0, layerId = layer,
        shape = ShapeSpec("rect", listOf(SubPath(4, closed = true)), ShapeStyle("#FF112233", 3f, "#00FF00")))
    private fun pen(id: String, layer: String) = Stroke(id, listOf(StrokePoint(0f, 0f), StrokePoint(50.125f, 20f)), "#FF0000", BrushType.INK_PEN, 5f, 1f, 1, layerId = layer)
    private fun sponge(id: String) = Stroke(id, listOf(StrokePoint(0f, 0f), StrokePoint(5f, 5f)), "#0000FF", BrushType.TEXTURE_SPONGE, 20f, 1f, 2, layerId = "base")
    private fun text(id: String) = Stroke(id, listOf(StrokePoint(20f, 30f), StrokePoint(120f, 30f), StrokePoint(120f, 60f), StrokePoint(20f, 60f)),
        "#000000", BrushType.TEXT, 24f, 1f, 3, layerId = "l2", text = TextSpec("Merhaba <dünya>"))
    private val doc = ExportDoc(200, 100, "#FFFFFF", layers, listOf(rect("a", "base"), sponge("s"), pen("p", "l2"), text("t"), pen("h", "l3")), "Deneme")

    @Test fun numberFormat() {
        assertEquals("1.5", ExportNum.f(1.5f)); assertEquals("2", ExportNum.f(2f)); assertEquals("0.05", ExportNum.f(0.05)); assertEquals("-3.25", ExportNum.f(-3.25))
        assertEquals("50.13", ExportNum.f(50.125)); assertEquals("0", ExportNum.f(-0.001))
    }

    @Test fun svgLayersVectorsAndRasterRuns() {
        var runs = 0
        val svg = SvgWriter.write(doc) { run -> runs++; assertEquals(listOf("s"), run.map { it.id }); byteArrayOf(1, 2, 3) }
        assertEquals(1, runs)
        assertTrue(svg.startsWith("<?xml"))
        assertTrue(svg.contains("inkscape:label=\"Çizgi\""))
        assertTrue(svg.contains("inkscape:label=\"Gölge\" opacity=\"0.5\" style=\"mix-blend-mode:multiply\""))
        assertFalse("gizli katman yazılmaz", svg.contains("Gizli"))
        assertTrue(svg.contains("<path d=\"M10 10 L90 10 L90 60 L10 60 Z\" fill=\"#00FF00\" stroke=\"#112233\" stroke-width=\"3\""))
        assertTrue(svg.contains("M0 0 L50.13 20"))
        assertTrue(svg.contains("data:image/png;base64,AQID"))
        assertTrue(svg.contains("Merhaba &lt;dünya&gt;"))
        assertTrue(svg.trimEnd().endsWith("</svg>"))
    }

    @Test fun dxfPolylinesTextAndSkipped() {
        val r = DxfWriter.write(doc)
        assertEquals("sünger fırçası atlanır", 1, r.skipped)
        val t = r.text
        assertTrue(t.contains("AC1009"))
        assertTrue(t.contains("\n2\nCIZGI_1\n")); assertTrue(t.contains("\n2\nGOLGE_2\n"))
        assertEquals(3, Regex("\nPOLYLINE\n").findAll(t).count()) // dikdörtgen + iki kalem (gizli katman da yazılır, kapalı gösterilir)
        assertTrue(t.contains("\nTEXT\n")); assertTrue(t.contains("Merhaba <dünya>"))
        // 10 px = 2.65 mm; y ters (100 px yükseklik → 26.46 mm)
        assertTrue(t.contains("\n10\n2.65\n20\n23.81\n"))
        assertTrue(t.trimEnd().endsWith("EOF"))
        assertTrue(t.contains("\n62\n-")) // gizli katman eksi renk
    }

    @Test fun psdHeaderLayersAndRle() {
        val row = byteArrayOf(5, 5, 5, 5, 1, 2, 3)
        assertArrayEquals(byteArrayOf(-3, 5, 2, 1, 2, 3), PsdWriter.packBits(row))
        val w = 4; val h = 3
        val px = IntArray(w * h) { 0xFF336699.toInt() }
        val bytes = PsdWriter.write(w, h, listOf(
            PsdWriter.PsdLayer("Arka plan", px),
            PsdWriter.PsdLayer("Gölge", IntArray(w * h), visible = false, opacity = 0.5f, blend = LayerBlend.MULTIPLY, clipping = true, alphaLock = true),
        ), px)
        val info = PsdWriter.readInfo(bytes)
        assertEquals(w, info.width); assertEquals(h, info.height); assertEquals(3, info.channels)
        assertEquals(listOf("Arka plan", "G_lge"), info.layers.map { it.first })
        assertEquals("mul ", info.layers[1].second); assertEquals(128, info.layers[1].third)
        assertEquals(3, info.flags[1]); assertEquals(1, info.clipping[1])
        assertTrue(String(bytes, Charsets.ISO_8859_1).contains("luni"))
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V56ExportUiTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    @Test
    fun saveSheetHasDocTabsAndWritesFiles() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val strokes = listOf(
            Stroke("a", listOf(StrokePoint(10f, 10f), StrokePoint(200f, 10f), StrokePoint(200f, 200f)), "#000000", BrushType.SHAPE, 4f, 1f, 0,
                shape = ShapeSpec("tri", listOf(SubPath(3, closed = true)), ShapeStyle("#000000", 4f, "#FF0000"))),
            Stroke("b", listOf(StrokePoint(50f, 300f), StrokePoint(300f, 320f)), "#00AA00", BrushType.CHARCOAL, 12f, 1f, 1),
        )
        runBlocking {
            val c = repo().createCanvas("Dışa", 400, 400, "#FFFFFF", aiEnabled = false, kind = CanvasKind.ART)
            repo().saveStrokes(c.localId, listOf(strokes))
        }
        waitForText("Dışa")
        compose.onAllNodes(hasText("Dışa"))[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.strokes?.size == 2 }
        assertTrue(vm().saveTabs().containsAll(listOf(SaveTab.SVG, SaveTab.PSD, SaveTab.DXF)))
        compose.runOnIdle { vm().openUnifiedSave() }
        waitFor { exists("save_doc_psd") }
        compose.onNodeWithTag("save_doc_psd").performClick()
        waitFor { vm().saveTab.value == SaveTab.PSD && exists("v56_doc_save") }
        compose.runOnIdle { vm().closeUnifiedSave() }
        val canvas = vm().canvas.value!!
        val svg = V56DocExport.svg(canvas)
        assertTrue(svg.contains("<image ")) // kömür kalemi resim olarak
        assertTrue(svg.contains("fill=\"#FF0000\""))
        val psd = PsdWriter.readInfo(V56DocExport.psd(GlobalContext.get().get(), canvas))
        assertEquals(400, psd.width)
        assertEquals("Arka plan", psd.layers.first().first)
        assertEquals(2, psd.layers.size)
    }
}
