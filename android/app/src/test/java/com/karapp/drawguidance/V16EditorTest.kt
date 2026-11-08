package com.karapp.drawguidance

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.drawing.FormulaRenderer
import com.karapp.drawguidance.ui.drawing.VectorEdit
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * v16 düzenleyici: iş bitince İncele modu, doku/desen eklemenin kalkması, formülün kendi üstünde düzenlenmesi,
 * alçak/geniş yazı çubuğu ve şekil düzenlemede vektör noktaları/çizgiler.
 */
abstract class V16EditorBase : EditorTestBase() {

    protected fun addSquare(from: Offset, delta: Offset) {
        addMenu("Şekil ekle")
        waitForText("Kare")
        compose.onNodeWithText("Kare").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Kare")
        drag(from, delta)
    }

    protected fun modeBarTitle(text: String) = waitFor {
        compose.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag("mode_bar"))).fetchSemanticsNodes().isNotEmpty()
    }

    /** v17: iş bitince seçimsiz Seç modu (eski İncele): mod çubuğu yok, altta yönerge çipi. */
    protected fun inView() = waitFor {
        compose.onAllNodesWithTag("mode_bar").fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodesWithText("Elemana dokunarak seç", substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    protected fun done() = compose.onNode(hasContentDescription("Bitti") and hasAnyAncestor(hasTestTag("mode_bar"))).performClick()

    protected fun shapeStroke(): Stroke = canvas().strokes.first { it.brushType == BrushType.SHAPE }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V16ModePolicyTest : V16EditorBase() {
    @Test
    fun everyFinishedJobGoesToViewModeAndPatternsAreGone() {
        createCanvasAndOpen("İncele")
        val size = canvasSize()
        // Ekle modunda doku/desen yok (v18: eklenecekler Ekle modunun üst menüsünde)
        addMenu("Şekil ekle")
        assertTrue(compose.onAllNodesWithContentDescription("Doku / desen ekle").fetchSemanticsNodes().isEmpty())
        waitForText("Kare")
        compose.onNodeWithText("Kare").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Kare")
        drag(Offset(size.x * 0.25f, size.y * 0.2f), Offset(size.x * 0.4f, size.x * 0.4f))
        waitFor { canvas().strokes.size == 1 }
        // Ekleme → Taşı → Bitti → İncele
        modeBarTitle("Taşı")
        compose.onNodeWithText("Bitti").performClick()
        inView()
        assertTrue(canvas().strokes.isNotEmpty())

        // Seç → Şekli düzenle → Bitti → İncele
        mode("Seç")
        tap(Offset(size.x * 0.45f, size.y * 0.2f))
        editButton("Şekli düzenle")
        modeBarTitle("Şekli Düzenle: Noktalar")
        done()
        inView()

        // Yazı → Yazıyı bitir → İncele
        addMenu("Yazı ekle")
        waitForText("Yazı eklemek için tuvale dokun", substring = true)
        tap(Offset(size.x * 0.2f, size.y * 0.6f))
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor"))).performTextInput("Not")
        compose.onNodeWithContentDescription("Yazıyı bitir").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.any { it.text != null } }
        inView()

        // v17: Seç'te seçiliyken Bitti seçimi kaldırır (Seç'te kalınır)
        tap(Offset(size.x * 0.45f, size.y * 0.2f))
        modeBarTitle("Seç")
        compose.onNodeWithText("Bitti").performClick()
        inView()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V16VectorUiTest : V16EditorBase() {
    @Test
    fun nodesLinesSlopeAndCut() {
        createCanvasAndOpen("Vektör")
        val size = canvasSize()
        // Ekran ↔ tuval dönüşümü için kalibrasyon çizgisi
        val start = Offset(size.x * 0.1f, size.y * 0.9f)
        drag(start, Offset(size.x * 0.5f, 0f))
        waitFor { canvas().strokes.size == 1 }
        val map = mappingFrom(start, size.x * 0.5f)
        addSquare(Offset(size.x * 0.25f, size.y * 0.2f), Offset(size.x * 0.4f, size.x * 0.4f))
        waitFor { canvas().strokes.size == 2 }
        compose.onNodeWithText("Bitti").performClick()
        inView()
        val box = ElementOps.bounds(shapeStroke())!!
        fun screen(x: Float, y: Float) = map.toScreen(x, y)
        // Karenin köşeleri (vektör noktaları)
        val g0 = VectorEdit.graph(shapeStroke())
        val tl = g0.nodes.minBy { it.point.x + it.point.y }.point
        val br = g0.nodes.maxBy { it.point.x + it.point.y }.point
        val bl = g0.nodes.minBy { it.point.x - it.point.y }.point

        mode("Seç")
        tap(screen(box.center.x, box.top))
        editButton("Şekli düzenle")
        modeBarTitle("Şekli Düzenle: Noktalar")
        // Yeni araçlar; eski ayarlar yok
        // v17: araçlar üst menüde
        listOf("Noktalar", "Çizgiler", "Alan boya").forEach {
            assertTrue(it, compose.onAllNodes(hasContentDescription(it) and hasAnyAncestor(hasTestTag("tool_row_context"))).fetchSemanticsNodes().isNotEmpty())
        }
        // v33 Nokta ekle ve Çizgi çiz alttaki nokta panelinde
        listOf("Nokta ekle", "Çizgi çiz").forEach {
            assertTrue(it, compose.onAllNodes(hasContentDescription(it) and hasAnyAncestor(hasTestTag("node_tool_controls"))).fetchSemanticsNodes().isNotEmpty())
        }
        listOf("Parça", "Boya", "Dönüştür", "Ayır", "Parçala").forEach {
            assertTrue(it, compose.onAllNodes(hasText(it) and hasAnyAncestor(hasTestTag("mode_bar"))).fetchSemanticsNodes().isEmpty())
        }
        assertEquals(4, VectorEdit.graph(shapeStroke()).nodes.size)

        // Noktalar: sağ-alt köşe sürüklenir, bağlı iki çizgi gelir
        drag(screen(br.x, br.y), Offset(60f, 40f))
        waitFor { ElementOps.bounds(shapeStroke())!!.right > box.right + 5f }
        val moved = VectorEdit.graph(shapeStroke())
        assertEquals(4, moved.nodes.size)
        val corner = moved.nodes.maxBy { it.point.x + it.point.y }.point

        // Nokta ekle: üst kenarın ortasına dokununca nokta eklenir
        compose.onNode(hasContentDescription("Nokta ekle") and hasAnyAncestor(hasTestTag("node_tool_controls"))).performClick()
        modeBarTitle("Şekli Düzenle: Nokta ekle")
        tap(screen((tl.x + br.x) / 2f, tl.y))
        waitFor { VectorEdit.graph(shapeStroke()).nodes.size == 5 }

        // Çizgi çiz: sol-üst köşeden sağ-alt köşeye çizgi
        compose.onNode(hasContentDescription("Çizgi çiz") and hasAnyAncestor(hasTestTag("node_tool_controls"))).performClick()
        modeBarTitle("Şekli Düzenle: Çizgi çiz")
        val edges0 = VectorEdit.graph(shapeStroke()).edges.size
        tap(screen(tl.x, tl.y))
        waitForText("Sonraki noktaya dokun", substring = true)
        tap(screen(corner.x, corner.y))
        waitFor { VectorEdit.graph(shapeStroke()).edges.size == edges0 + 1 }
        val diagonal = VectorEdit.graph(shapeStroke()).edgeBetween(tl, corner)!!

        // Çizgiler: köşegen seçilir; eğim ayarı; Yatay ile eğimi 0 olur
        compose.onNode(hasContentDescription("Çizgiler") and hasAnyAncestor(hasTestTag("tool_row_context"))).performClick()
        val mid = diagonal.middle()
        tap(screen(mid.x, mid.y))
        waitFor { compose.onAllNodesWithTag("edge_controls").fetchSemanticsNodes().isNotEmpty() }
        waitForText("Eğim", substring = true)
        compose.onNode(hasText("Yatay") and hasAnyAncestor(hasTestTag("edge_controls"))).performClick()
        waitFor {
            VectorEdit.graph(shapeStroke()).edges.any { e ->
                abs(e.start.y - e.end.y) < 0.6f && abs(e.length() - diagonal.length()) < 2f && abs(e.start.y - mid.y) < 2f
            }
        }

        // Noktalar: bir köşe seçilip Kes → kesilen parça ayrı eleman, seçili olarak Taşı
        compose.onNode(hasContentDescription("Noktalar") and hasAnyAncestor(hasTestTag("tool_row_context"))).performClick()
        val g = VectorEdit.graph(shapeStroke())
        val pick = g.nodeNear(bl)!!.point
        tap(screen(pick.x, pick.y))
        waitFor { compose.onAllNodes(hasContentDescription("Kes") and hasAnyAncestor(hasTestTag("mode_bar"))).fetchSemanticsNodes().isNotEmpty() }
        val before = canvas().strokes.size
        compose.onNode(hasContentDescription("Kes") and hasAnyAncestor(hasTestTag("mode_bar"))).performClick()
        waitFor { canvas().strokes.size == before + 1 }
        modeBarTitle("Taşı")
        assertEquals(2, canvas().strokes.count { it.brushType == BrushType.SHAPE })
        compose.onNodeWithText("Bitti").performClick()
        inView()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V16FormulaInlineTest : V16EditorBase() {
    @Test
    fun coefficientsAreEditedOnTheFormulaItself() {
        createNotesCanvasAndOpen("Formül")
        val size = canvasSize()
        val start = Offset(size.x * 0.1f, size.y * 0.9f)
        drag(start, Offset(size.x * 0.5f, 0f))
        waitFor { canvas().strokes.size == 1 }
        val map = mappingFrom(start, size.x * 0.5f)
        addMenu("Formül ekle")
        waitFor { compose.onAllNodesWithTag("formula_picker").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Üslü ifade").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Formül: Üslü ifade")
        tap(Offset(size.x * 0.5f, size.y * 0.4f))
        waitFor { canvas().strokes.size == 2 }
        // Eklemeden sonra Taşı'da: katsayıya dokununca formülün üstünde yazı alanı açılır (pencere yok)
        modeBarTitle("Taşı")
        val f = canvas().strokes.first { it.brushType == BrushType.FORMULA }
        // v17: formül elemanlarına ayrıldı; ilk "a" yuvası
        val slotA = com.karapp.drawguidance.content.formula.Formula.slots(f.formula!!.source).first { it.value == "a" }.name
        val quad = FormulaRenderer.slotQuad(f, slotA)!!
        val c = Offset(quad.map { it.x }.average().toFloat(), quad.map { it.y }.average().toFloat())
        tap(map.toScreen(c.x, c.y))
        waitFor { compose.onAllNodesWithTag("formula_inline_editor").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithTag("formula_edit_sheet").fetchSemanticsNodes().isEmpty())
        // Yazı alanı yuvanın üstünde durur
        val field = compose.onNode(hasSetTextAction() and hasContentDescription("Yuva $slotA")).fetchSemanticsNode().boundsInRoot
        val canvasTop = compose.onNodeWithTag("drawing_canvas").fetchSemanticsNode().boundsInRoot.top
        val slotScreen = map.toScreen(c.x, c.y) + Offset(0f, canvasTop)
        assertTrue("alan yuvanın üstünde: $field / $slotScreen", slotScreen.y in (field.top - 4f)..(field.bottom + 4f))
        compose.onNode(hasSetTextAction() and hasContentDescription("Yuva $slotA")).performTextReplacement("x+1")
        waitFor { canvas().strokes.first { it.brushType == BrushType.FORMULA }.formula!!.source.contains("\\slot{$slotA}{x+1}") }
        val w0 = f.points[1].x - f.points[0].x
        val w1 = canvas().strokes.first { it.brushType == BrushType.FORMULA }.let { it.points[1].x - it.points[0].x }
        assertTrue("formül genişledi: $w0 → $w1", w1 > w0)
        // Formülün tamamı da üstünde yazılır
        compose.onNodeWithContentDescription("Formülün tamamını düzenle").performClick()
        waitFor { compose.onAllNodes(hasSetTextAction() and hasContentDescription("Formülün tamamı")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Formülü bitir").performClick()
        waitFor { compose.onAllNodesWithTag("formula_inline_editor").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Bitti").performClick()
        inView()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V16TextBarTest : V16EditorBase() {
    @Test
    fun textBarIsLowWideAndNeverCoversTheText() {
        createNotesCanvasAndOpen("Yazı Çubuğu")
        val size = canvasSize()
        addMenu("Yazı ekle")
        waitForText("Yazı eklemek için tuvale dokun", substring = true)
        // Alta yakın bir yere (mod çubuğunun hemen üstü) yazı: alan yazı çubuğunun altında kalmaz (görünüm kayar)
        val canvasTop = compose.onNodeWithTag("drawing_canvas").fetchSemanticsNode().boundsInRoot.top
        val barTop = compose.onNodeWithTag("mode_bar").fetchSemanticsNode().boundsInRoot.top - canvasTop
        tap(Offset(size.x * 0.2f, minOf(size.y * 0.9f, barTop - 6f)))
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor"))).performTextInput("Alt satır")
        val density = compose.density.density
        var last = ""
        try {
            waitFor {
                val editor = compose.onNodeWithTag("inline_text_editor").fetchSemanticsNode().boundsInRoot
                val bar = compose.onNodeWithTag("inline_font_bar").fetchSemanticsNode().boundsInRoot
                last = "editor=$editor bar=$bar"
                editor.bottom <= bar.top
            }
        } catch (e: AssertionError) {
            throw AssertionError(last, e)
        }
        val bar = compose.onNodeWithTag("inline_font_bar").fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("drawing_canvas").fetchSemanticsNode().boundsInRoot
        assertTrue("çubuk alçak: ${bar.height / density} dp", bar.height / density < 150f)
        assertTrue("çubuk geniş: ${bar.width} / ${root.width}", bar.width > root.width * 0.9f)
        // Kalın ve paragraf düğmeleri çubukta
        compose.onNode(hasContentDescription("Kalın") and hasAnyAncestor(hasTestTag("inline_font_bar"))).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasContentDescription("Madde işaretli liste") and hasAnyAncestor(hasTestTag("inline_font_bar"))).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Yazıyı bitir").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.any { it.text != null } }
        val t = canvas().strokes.single { it.text != null }.text!!
        assertTrue(t.bold)
        assertTrue(t.content.startsWith("• "))
        // v23: not tuvalinde eklenen yazı seçili olarak Taşı moduna geçer
        waitFor { compose.onAllNodes(androidx.compose.ui.test.hasText("Taşı") and hasAnyAncestor(hasTestTag("mode_bar"))).fetchSemanticsNodes().isNotEmpty() }
    }
}
