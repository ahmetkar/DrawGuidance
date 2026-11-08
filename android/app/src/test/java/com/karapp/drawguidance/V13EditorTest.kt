package com.karapp.drawguidance

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.DiagramSide
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.model.TextParagraph
import com.karapp.drawguidance.diagnostics.AppLog
import com.karapp.drawguidance.ui.drawing.DiagramRenderer
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Diyagramda tutamaktan çekilen ok boşluğa bırakılınca orada yeni adım açılır; diyagramın dışına
 * bırakılırsa diyagram büyür (adımların yeri değişmez). Adımlara ve oklara ayrı renk verilir.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V13DiagramDropTest : EditorTestBase() {

    private fun closeSheet(tag: String) {
        SystemBack.press()
        waitFor { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun droppedArrowCreatesANodeThereAndNodesGetOwnColors() {
        createNotesCanvasAndOpen("Akış")
        addMenu("Diyagram ekle")
        waitForText("Diyagram Türü")
        compose.onNodeWithText("Akış Diyagramı").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Diyagram: Akış Diyagramı")
        val size = canvasSize()
        val screen = Rect(size.x * 0.1f, size.y * 0.06f, size.x * 0.6f, size.y * 0.5f)
        drag(screen.topLeft, Offset(screen.width, screen.height))
        waitFor { canvas().strokes.isNotEmpty() }
        val placed = canvas().strokes.single()
        val box = Rect(placed.points.minOf { it.x }, placed.points.minOf { it.y }, placed.points.maxOf { it.x }, placed.points.maxOf { it.y })
        val scale = screen.width / box.width
        // v47 menü satırı değişince kâğıt yeniden sığdırılır: konum her seferinde görünümden hesaplanır
        fun toScreen(c: Offset) = fittedScreen(c)
        fun toCanvas(p: Offset) = fittedCanvas(p)
        fun spec() = canvas().strokes.single().diagram!!
        editButton("Diyagramı düzenle")
        waitForText("Adım ekle")

        // 1) Karar adımına dokun, sağ tutamağından diyagramın sağındaki boşluğa sürükle
        val stroke = canvas().strokes.single()
        val karar = spec().nodes[2]
        val c = DiagramRenderer.toCanvas(stroke, karar.x + karar.w / 2f, karar.y + karar.h / 2f)!!
        tap(toScreen(c))
        waitFor { compose.onAllNodesWithTag("diagram_focus_panel").fetchSemanticsNodes().isNotEmpty() }
        val port = toScreen(DiagramRenderer.portPoint(stroke, karar, DiagramSide.RIGHT)!!)
        val drop = Offset(size.x * 0.85f, port.y)
        drag(port, drop - port, steps = 30)
        waitFor { spec().nodes.size == 5 }
        val added = spec().nodes.last()
        val edge = spec().edges.last()
        assertEquals(karar.id, edge.from)
        assertEquals(added.id, edge.to)
        assertEquals(DiagramSide.RIGHT, edge.fromSide)
        assertTrue("elle yerleşim", spec().manual)
        // Yeni adım bırakılan yerde; diyagram sağa büyüdü, eski adımlar yerinde
        val grown = canvas().strokes.single()
        val center = DiagramRenderer.toCanvas(grown, added.x + added.w / 2f, added.y + added.h / 2f)!!
        val dropCanvas = toCanvas(drop)
        // v34: üst satırların düğmeleri 34 dp olunca görünüm birkaç piksel kaydı; bırakılan yere 5 px yakın
        assertEquals(dropCanvas.x, center.x, 5f)
        assertEquals(dropCanvas.y, center.y, 5f)
        assertTrue("diyagram büyüdü", grown.points.maxOf { it.x } > box.right + 1f)
        val kararAfter = DiagramRenderer.toCanvas(grown, karar.x + karar.w / 2f, karar.y + karar.h / 2f)!!
        assertEquals(c.x, kararAfter.x, 1f)
        assertEquals(c.y, kararAfter.y, 1f)
        // Tek geri alma adımı
        compose.onNodeWithContentDescription("Geri al").performClick()
        waitFor { spec().nodes.size == 4 }
        assertEquals(box.right, canvas().strokes.single().points.maxOf { it.x }, 1f)
        compose.onNodeWithContentDescription("Yinele").performClick()
        waitFor { spec().nodes.size == 5 }

        // 2) Yeni adım odakta: Renk → dolgu kırmızı, yazı beyaz
        tap(toScreen(center))
        waitFor { compose.onAllNodesWithTag("diagram_focus_panel").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Renk").performClick()
        waitFor { compose.onAllNodesWithTag("part_color_sheet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Renk #E53935").performClick()
        waitFor { spec().nodes.last().fillColorHex == "#E53935" }
        compose.onNode(hasText("Yazı") and hasAnyAncestor(hasTestTag("part_color_sheet"))).performClick()
        compose.onNodeWithContentDescription("Renk #FFFFFF").performClick()
        waitFor { spec().nodes.last().textColorHex == "#FFFFFF" }
        closeSheet("part_color_sheet")
        assertEquals("diğer adımlar varsayılan renkte", 1, spec().nodes.count { it.fillColorHex != null })
    }
}

/** Not tuvalinde yazı: yazı tipi listeden seçilir; madde işaretli/numaralı liste ve başlık seviyeleri. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V13TextListsTest : EditorTestBase() {

    private fun field() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor")))

    @Test
    fun listsHeadingsAndFontsFromTheList() {
        createNotesCanvasAndOpen("Notlar")
        val size = canvasSize()
        addMenu("Yazı ekle")
        waitForText("Yazı eklemek için tuvale dokun", substring = true)
        tap(Offset(size.x * 0.2f, size.y * 0.3f))
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        // Madde işaretli liste: Enter bir sonraki maddeyi açar
        compose.onNode(hasContentDescription("Madde işaretli liste") and hasAnyAncestor(hasTestTag("inline_font_bar"))).performSemanticsAction(SemanticsActions.OnClick)
        field().performTextInput("Elma")
        field().performTextInput("\n")
        field().performTextInput("Armut")
        // Yazı tipi: listeden Caveat
        compose.onNode(hasTestTag("font_picker_button") and hasAnyAncestor(hasTestTag("inline_font_bar"))).performClick()
        waitFor { compose.onAllNodesWithTag("font_list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("font_list").performScrollToNode(hasContentDescription("Caveat"))
        compose.onNode(hasContentDescription("Caveat") and hasClickAction()).performClick()
        waitFor { compose.onAllNodesWithTag("font_list").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Yazıyı bitir").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.any { it.text != null } }
        val list = canvas().strokes.single { it.text != null }.text!!
        assertEquals("• Elma\n• Armut", list.content)
        assertEquals(TextParagraph.BULLET, list.paragraph)
        assertEquals(TextFont.CAVEAT, list.font)

        // Numaralıya çevir (seçili yazıya panelden); v16: yazı bitince İncele'ye geçilir, yazı Seç ile seçilir
        mode("Seç")
        tap(Offset(size.x * 0.24f, size.y * 0.3f))
        waitForText("1 eleman seçili")
        clickElements()
        waitFor { compose.onAllNodesWithTag("text_style_controls").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Numaralı liste") and hasAnyAncestor(hasTestTag("text_style_controls"))).performScrollTo().performClick()
        try {
            waitFor { canvas().strokes.single { it.text != null }.text!!.content == "1. Elma\n2. Armut" }
        } catch (e: AssertionError) {
            throw AssertionError("içerik: " + canvas().strokes.single { it.text != null }.text, e)
        }
        // Başlık 1: işaretler kalkar, yazı büyür
        val beforeHeight = ElementOps.bounds(canvas().strokes.single { it.text != null })!!.height
        compose.onNode(hasContentDescription("Başlık 1") and hasAnyAncestor(hasTestTag("text_style_controls"))).performScrollTo().performClick()
        waitFor { canvas().strokes.single { it.text != null }.text!!.paragraph == TextParagraph.H1 }
        val heading = canvas().strokes.single { it.text != null }
        assertEquals("Elma\nArmut", heading.text!!.content)
        assertTrue("başlık daha büyük", ElementOps.bounds(heading)!!.height > beforeHeight * 1.5f)
    }
}

/** Mod menüsünde İnceleme (fare imleci), üst menüde Kırp. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V13MenuTest : EditorTestBase() {

    @Test
    fun viewIsInTheModeRowAndCropIsInTheTopRow() {
        createCanvasAndOpen("Menü")
        // Kırp üst menüde (mod menüsünde değil); v18: Düzenle modunun üst menüsünde
        compose.onNode(hasContentDescription("Kırp") and hasAnyAncestor(hasTestTag("mode_selector"))).assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Kırp").assertCountEquals(0)
        mode("Kırp")
        waitForText("Çerçevenin kenarlarını sürükle", substring = true)
        compose.onNodeWithContentDescription("Kırp").performClick()
        waitFor { compose.onAllNodesWithText("Çerçevenin kenarlarını sürükle", substring = true).fetchSemanticsNodes().isEmpty() }
        // v17: İncele Seç ile birleşti; mod menüsünde Seç, Ekle, Düzenle; seçili modun adı yazar
        compose.onNode(hasContentDescription("İncele") and hasAnyAncestor(hasTestTag("mode_selector"))).assertDoesNotExist()
        compose.onNode(hasContentDescription("Seç") and hasAnyAncestor(hasTestTag("mode_selector"))).performClick()
        waitForText("Elemana dokunarak seç", substring = true)
        compose.onNode(hasContentDescription("Çiz") and hasAnyAncestor(hasTestTag("mode_selector"))).performClick()
        waitFor { compose.onAllNodesWithText("Elemana dokunarak seç", substring = true).fetchSemanticsNodes().isEmpty() }
    }
}

/** v39 geliştirici ayarları kaldırıldı: Gelişmiş sekmesinde yalnız hata raporları var. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V13DeveloperLogsTest : EditorTestBase() {

    @Test
    fun developerSettingsAreRemoved() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNode(hasText("Ayarlar") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { compose.onAllNodesWithTag("settings_tab_advanced").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings_tab_advanced").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { compose.onAllNodesWithTag("reports_card").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithTag("developer_card").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Geliştirici modu").fetchSemanticsNodes().isEmpty())
    }
}
