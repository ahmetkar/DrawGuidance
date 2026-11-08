package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
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
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.data.settings.ColorPaletteStore
import com.karapp.drawguidance.ui.drawing.DiagramRenderer
import com.karapp.drawguidance.ui.drawing.ShapeEditor
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapeKind
import com.karapp.drawguidance.ui.drawing.ShapeRenderer
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renk Ver ve boya kutusu aynı "dolgu rengi"ni verir: şekilde iç dolgu, yazıda harf rengi, çizimde mürekkep. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V11FillRulesTest {

    @org.junit.After fun tearDown() = org.koin.core.context.stopKoin()

    private val style = ShapeStyle(strokeColorHex = "#1E1E1E", strokeWidth = 4f)

    private fun shape(kind: ShapeKind, id: String) =
        ShapeFactory.build(kind, Offset(100f, 100f), Offset(400f, 400f), style, null, id = id, order = 0)!!

    @Test fun fillGoesToTheRightPropertyOfEachElementKind() {
        val square = shape(ShapeKind.SQUARE, "sq")
        val line = shape(ShapeKind.LINE, "ln")
        val text = Stroke(
            "tx", listOf(StrokePoint(0f, 0f), StrokePoint(100f, 0f), StrokePoint(100f, 40f), StrokePoint(0f, 40f)),
            "#1E1E1E", BrushType.TEXT, 30f, 1f, 2, text = TextSpec("Başlık"),
        )
        val ink = Stroke("pn", listOf(StrokePoint(0f, 0f), StrokePoint(50f, 50f)), "#1E1E1E", BrushType.PENCIL, 4f, 1f, 3)
        val eraser = Stroke("er", listOf(StrokePoint(0f, 0f), StrokePoint(50f, 50f)), "#FFFFFF", BrushType.ERASER, 10f, 1f, 4)
        val all = listOf(square, line, text, ink, eraser)
        val filled = ElementOps.fill(all, all.mapTo(HashSet()) { it.id }, "#E53935")

        assertEquals("kare: iç dolgu", "#E53935", filled[0].shape!!.style.fillColorHex)
        assertEquals("kare: kenar değişmez", "#1E1E1E", filled[0].shape!!.style.strokeColorHex)
        assertNull("doğru dolgu alamaz", filled[1].shape!!.style.fillColorHex)
        assertEquals("doğru: rengi değişir", "#E53935", filled[1].shape!!.style.strokeColorHex)
        assertEquals("yazı: harf rengi", "#E53935", filled[2].colorHex)
        assertEquals("çizim: mürekkep", "#E53935", filled[3].colorHex)
        assertEquals("silgi değişmez", eraser, filled[4])
        assertEquals("#E53935", ElementOps.fillColorOf(filled[0]))
        assertEquals("#E53935", ElementOps.fillColorOf(filled[2]))
        assertNull(ElementOps.fillColorOf(eraser))
    }

    @Test fun splitShapeKeepsItsFillButOpenCurvesDoNot() {
        val square = shape(ShapeKind.SQUARE, "sq")
        val split = ShapeEditor.split(square, Offset(250f, 100f), ShapeEditor.splitGap(square))!!
        assertTrue("parçalanan kare açık", split.shape!!.subpaths.none { it.closed })
        assertTrue("kopukluk küçük: dolgu korunur", ShapeRenderer.isFillable(split))
        val filled = ElementOps.fill(listOf(split), setOf("sq"), "#43A047").single()
        assertEquals("#43A047", filled.shape!!.style.fillColorHex)
        // Dolu parçalanmış karenin içine dokunmak onu seçer
        assertEquals(0f, ShapeRenderer.distanceTo(filled, Offset(250f, 250f)), 0f)
        assertFalse("doğru dolgu alamaz", ShapeRenderer.isFillable(shape(ShapeKind.LINE, "ln")))
    }
}

/**
 * Diyagrama ok da eklenir: alttaki "Ok ekle" ile önce başlangıç, sonra hedef adıma dokunulur; yeni ok odaklanır
 * ve yazısı verilir. Adım/ok ekleme ayrı geri alma adımlarıdır. Düzenleme modunda kalem ve silgi pasiftir.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V11DiagramEdgeTest : EditorTestBase() {

    @Test
    fun arrowsAreAddedFromTheBottomBarAndEachChangeUndoesSeparately() {
        createNotesCanvasAndOpen("Akış")
        addMenu("Diyagram ekle")
        waitForText("Diyagram Türü")
        compose.onNodeWithText("Akış Diyagramı").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Diyagram: Akış Diyagramı")
        // Diyagram ekleme modunda kalem ve silgi yok (v17: üst menüde eklenecekler)
        assertTrue(compose.onAllNodesWithContentDescription("Fırça Seç").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithContentDescription("Silgi").fetchSemanticsNodes().isEmpty())

        val size = canvasSize()
        val screen = Rect(size.x * 0.15f, size.y * 0.12f, size.x * 0.75f, size.y * 0.62f)
        drag(screen.topLeft, Offset(screen.width, screen.height))
        waitFor { canvas().strokes.isNotEmpty() }
        val placed = canvas().strokes.single()
        val box = Rect(placed.points.minOf { it.x }, placed.points.minOf { it.y }, placed.points.maxOf { it.x }, placed.points.maxOf { it.y })
        fun nodeCenter(index: Int): Offset {
            val stroke = canvas().strokes.single()
            val node = stroke.diagram!!.nodes[index]
            val c = DiagramRenderer.toCanvas(stroke, node.x + node.w / 2f, node.y + node.h / 2f)!!
            return Offset(screen.left + (c.x - box.left) / box.width * screen.width, screen.top + (c.y - box.top) / box.height * screen.height)
        }

        editButton("Diyagramı düzenle")
        waitForText("Adım ekle")
        assertTrue(compose.onAllNodesWithContentDescription("Fırça Seç").fetchSemanticsNodes().isEmpty())

        // Ok ekle: önce başlangıç (Karar), sonra hedef (Başla)
        compose.onNode(hasText("Ok ekle") and hasClickAction()).performClick()
        waitForText("Okun başlayacağı adımın", substring = true)
        tap(nodeCenter(2))
        waitForText("Okun gideceği adımın", substring = true)
        tap(nodeCenter(0))
        waitFor { canvas().strokes.single().diagram!!.edges.size == 4 }
        val edge = canvas().strokes.single().diagram!!.edges.last()
        val nodes = canvas().strokes.single().diagram!!.nodes
        assertEquals(nodes[2].id, edge.from)
        assertEquals(nodes[0].id, edge.to)
        // Yeni ok odakta: yazısı panelden verilir
        waitFor { compose.onAllNodesWithTag("diagram_focus_panel").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("diagram_focus_panel"))).performTextReplacement("hayır")
        waitFor { canvas().strokes.single().diagram!!.edges.last().label == "hayır" }

        // Oka odaklıyken "Adım ekle": yeni adım okun arasına girer (Karar → yeni → Başla)
        compose.onNode(hasText("Adım ekle") and hasClickAction()).performClick()
        waitFor { canvas().strokes.single().diagram!!.nodes.size == 5 }
        assertEquals(5, canvas().strokes.single().diagram!!.edges.size)

        // Her değişiklik ayrı geri alınır
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { canvas().strokes.single().diagram!!.nodes.size == 4 }
        assertEquals("ok ve yazısı duruyor", "hayır", canvas().strokes.single().diagram!!.edges.last().label)
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { canvas().strokes.single().diagram!!.edges.last().label == "" }
        assertEquals(4, canvas().strokes.single().diagram!!.edges.size)
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { canvas().strokes.single().diagram!!.edges.size == 3 }

        // Bitti: Seç'e geçilir; Çiz'de kalem ve silgi yeniden etkin
        compose.onNodeWithContentDescription("Bitti").performClick()
        waitFor { compose.onAllNodesWithText("Adım ekle").fetchSemanticsNodes().isEmpty() }
        mode("Çiz")
        compose.onNodeWithContentDescription("Fırça Seç").assertIsEnabled()
        compose.onNodeWithContentDescription("Silgi").assertIsEnabled()
    }
}

/** Renk Ver elemanın dolgu rengini verir; boya kutusu seçili yazının harf rengini (dolgusunu) boyar. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V11RecolorFillTest : EditorTestBase() {

    @Test
    fun recolorSetsTheFillAndThePaintBucketPaintsTheSelectedText() {
        createNotesCanvasAndOpen("Renkler")
        addMenu("Şekil ekle")
        waitForText("Geometrik Cisimler")
        compose.onNodeWithText("Kare").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Kare")
        val size = canvasSize()
        drag(Offset(size.x * 0.15f, size.y * 0.15f), Offset(size.x * 0.3f, size.x * 0.3f))
        waitFor { canvas().strokes.size == 1 }
        // v16: Bitti İncele'ye geçer (seçim kalkar); eklenen kare seçiliyken Seç moduna geçilir
        mode("Seç")

        // Renk Ver: kayıtlı renk dolgu olur, kenar rengi değişmez
        GlobalContext.get().get<ColorPaletteStore>().add("#2E7D32")
        val strokeColor = canvas().strokes.single().shape!!.style.strokeColorHex
        clickElements()
        waitForText("Renk Ver")
        compose.onNodeWithText("Renk Ver").performClick()
        waitForText("Verilen renk seçili elemanların dolgu rengi olur", substring = true)
        compose.onNodeWithContentDescription("Kayıtlı renk #2E7D32").clickInPopup()
        compose.onNodeWithText("Uygula").clickInPopup()
        waitFor { canvas().strokes.single().shape!!.style.fillColorHex == "#2E7D32" }
        assertEquals(strokeColor, canvas().strokes.single().shape!!.style.strokeColorHex)
        // Elemanlar panelindeki dolgu rengi de aynı
        waitForText("#2E7D32")
        compose.onNodeWithContentDescription("Paneli kapat").performClick()

        // Yazı ekle; seçili yazıya boya kutusu harf rengini verir, tuvalin arka planı değişmez
        addMenu("Yazı ekle")
        waitForText("Yazı eklemek için tuvale dokun", substring = true)
        tap(Offset(size.x * 0.3f, size.y * 0.6f))
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor"))).performTextInput("Not")
        compose.onNodeWithContentDescription("Yazıyı bitir").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.any { it.text != null } }
        // v23: not tuvalinde "Yazıyı bitir" eklenen yazıyı seçili bırakıp Taşı moduna geçer; Seç'e geçince seçim korunur
        waitFor { compose.onAllNodesWithTag("mode_bar").fetchSemanticsNodes().isNotEmpty() }
        val background = canvas().backgroundColorHex
        val textBefore = canvas().strokes.first { it.text != null }.colorHex
        mode("Seç")
        if (compose.onAllNodesWithText("1 eleman seçili").fetchSemanticsNodes().isEmpty()) tap(Offset(size.x * 0.32f, size.y * 0.6f))
        waitForText("1 eleman seçili")
        clickFill()
        waitForText("Renk seç")
        compose.onNodeWithTag("color_sv_area").performTouchInput { click(Offset(width * 0.9f, height * 0.2f)) }
        waitFor { !canvas().strokes.first { it.text != null }.colorHex.equals(textBefore, ignoreCase = true) }
        assertEquals("tuvalin arka planı değişmedi", background, canvas().backgroundColorHex)
        assertEquals("kare değişmedi", "#2E7D32", canvas().strokes.first { it.shape != null }.shape!!.style.fillColorHex)
    }
}

/** Yazarken tuvalde başka yere dokunmak ya da modu değiştirmek yazılanı kaybetmez. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V11TextDraftTest : EditorTestBase() {

    @Test
    fun typedTextIsKeptWhenTappingElsewhereOrChangingMode() {
        createCanvasAndOpen("Yazılar")
        val size = canvasSize()
        addMenu("Yazı ekle")
        waitForText("Yazı eklemek için tuvale dokun", substring = true)
        tap(Offset(size.x * 0.2f, size.y * 0.3f))
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor"))).performTextInput("Merhaba")
        // Başka yere dokun: yazı kaydedilir
        tap(Offset(size.x * 0.6f, size.y * 0.42f))
        waitFor { canvas().strokes.any { it.text?.content == "Merhaba" } }
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isEmpty() }

        // Yeni yazı; modu değiştirince de kaydedilir
        tap(Offset(size.x * 0.2f, size.y * 0.55f))
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor"))).performTextInput("Dünya")
        compose.onNodeWithContentDescription("Seç").performClick()
        waitFor { canvas().strokes.count { it.text != null } == 2 }
        assertTrue(canvas().strokes.any { it.text?.content == "Dünya" })
    }
}

/** Geri alma sayfayı da geri getirir: değişikliğin yapıldığı sayfaya dönülür. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V11PageUndoTest : EditorTestBase() {

    @Test
    fun undoReturnsToThePageWhereTheChangeWasMade() {
        createNotesCanvasAndOpen("Sayfalar")
        val size = canvasSize()
        drag(Offset(size.x * 0.2f, size.y * 0.3f), Offset(size.x * 0.4f, 0f))
        waitFor { canvas().strokes.size == 1 }
        compose.onNodeWithContentDescription("Sayfa ekle").performClick()
        waitFor { canvas().pageCount == 2 && canvas().currentPage == 1 }
        drag(Offset(size.x * 0.2f, size.y * 0.5f), Offset(size.x * 0.4f, 0f))
        waitFor { canvas().strokes.size == 1 && canvas().currentPage == 1 }
        compose.onNodeWithContentDescription("Sayfa 1").performClick()
        waitFor { canvas().currentPage == 0 }

        // Geri al: 2. sayfadaki çizim geri alınır ve o sayfa açılır
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { canvas().currentPage == 1 && canvas().pages[1].isEmpty() }
        assertEquals("1. sayfa korunur", 1, canvas().pages[0].size)
        // Sayfa eklemeyi geri al: 1. sayfaya dönülür
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { canvas().pageCount == 1 && canvas().currentPage == 0 }
        assertEquals(1, canvas().strokes.size)
    }
}
