package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Layers
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v15: mod politikası (ekle → Taşı, düzenle → Seç), Ekle menüsünde son seçilen, silginin yalnızca çizimleri silmesi,
 * katmanlar, formül ve desen ekleme, otomatik kaydın kapatılması, grafik düzenleme.
 */
abstract class V15EditorBase : EditorTestBase() {

    protected fun addSquare(from: Offset, delta: Offset) {
        addMenu("Şekil ekle")
        waitForText("Kare")
        compose.onNodeWithText("Kare").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Kare")
        drag(from, delta)
    }

    protected fun modeBarTitle(text: String) = waitFor {
        compose.onAllNodes(androidx.compose.ui.test.hasText(text) and hasAnyAncestor(hasTestTag("mode_bar"))).fetchSemanticsNodes().isNotEmpty()
    }

    protected fun modeBarGone() = waitFor { compose.onAllNodesWithTag("mode_bar").fetchSemanticsNodes().isEmpty() }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V15ModePolicyTest : V15EditorBase() {
    @Test
    fun insertGoesToMoveEditGoesToSelectAndAddMenuRemembersLastItem() {
        createCanvasAndOpen("Politika")
        val size = canvasSize()
        addSquare(Offset(size.x * 0.2f, size.y * 0.2f), Offset(size.x * 0.4f, size.x * 0.4f))
        waitFor { canvas().strokes.size == 1 }
        // Eklenen şekil seçili olarak Taşı modunda
        modeBarTitle("Taşı")
        waitForText("1 eleman seçili")
        // Taşı: sürükleyince şekil taşınır
        val before = ElementOps.bounds(canvas().strokes.single())!!
        drag(Offset(size.x * 0.4f, size.y * 0.2f + size.x * 0.2f), Offset(60f, 0f))
        waitFor { ElementOps.bounds(canvas().strokes.single())!!.left > before.left + 5f }
        // Bitti: v16'da İncele moduna geçilir
        compose.onNodeWithText("Bitti").performClick()
        modeBarGone()

        // v18: son seçilen eklenecek Ekle modunun üst menüsünde seçili görünür (+ menüsü kalktı)
        mode("Ekle")
        waitFor {
            compose.onAllNodes(
                androidx.compose.ui.test.hasContentDescription("Şekil ekle") and androidx.compose.ui.test.isSelected() and hasAnyAncestor(hasTestTag("tool_row_context")),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        SystemBack.press()
        compose.waitForIdle()

        // Şekil düzenleme bitince (v16) İncele moduna geçilir
        mode("Seç")
        tap(Offset(size.x * 0.2f + 60f + 4f, size.y * 0.2f + size.x * 0.2f))
        editButton("Şekli düzenle")
        waitForText("Şekli Düzenle: Noktalar")
        // v16 araçları: vektör noktaları ve parça özellikleri (v17: araçlar üst menüde)
        waitForDescription("Nokta ekle")
        compose.onNodeWithContentDescription("Parça özellikleri").performClick()
        waitFor { compose.onAllNodesWithTag("part_style_sheet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Kesik").performClick()
        waitFor { canvas().strokes.single().shape!!.subpaths.first().dashed }
        SystemBack.press()
        waitFor { compose.onAllNodesWithTag("part_style_sheet").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Bitti").performClick()
        waitForText("Elemana dokunarak seç", substring = true)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V15EraserTest : V15EditorBase() {
    @Test
    fun eraserErasesOnlyInkAndLeavesObjects() {
        createCanvasAndOpen("Silgi")
        val size = canvasSize()
        addSquare(Offset(size.x * 0.25f, size.y * 0.25f), Offset(size.x * 0.4f, size.x * 0.4f))
        waitFor { canvas().strokes.size == 1 }
        compose.onNodeWithText("Bitti").performClick()
        modeBarGone()
        // v16: İncele'den çizmeye Çiz ile geçilir
        mode("Çiz")
        // Şeklin üstünden geçen kalem çizgisi
        val y = size.y * 0.25f + size.x * 0.2f
        drag(Offset(size.x * 0.1f, y), Offset(size.x * 0.8f, 0f))
        waitFor { canvas().strokes.size == 2 }
        compose.onNodeWithContentDescription("Silgi").performClick()
        waitForDescription("Seçili fırça: Silgi")
        drag(Offset(size.x * 0.05f, y), Offset(size.x * 0.9f, 0f))
        // Kalem çizgisi tamamen silinir (kayıttan da kalkar); şekil yerinde kalır
        waitFor { canvas().strokes.none { it.brushType == BrushType.PENCIL } }
        assertTrue(canvas().strokes.any { it.brushType == BrushType.SHAPE })
        assertEquals(1, canvas().strokes.count { it.brushType == BrushType.SHAPE })
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V15LayersTest : V15EditorBase() {
    @Test
    fun layersAddHideLockAndPlaceNewElements() {
        createCanvasAndOpen("Katmanlar")
        val size = canvasSize()
        drag(Offset(size.x * 0.2f, size.y * 0.3f), Offset(200f, 0f))
        waitFor { canvas().strokes.size == 1 }
        // v45 dikey ekranda adlar düğmenin içinde, üst satır kayar: Katmanlar (satırın sonunda) önce görünür yapılır
        compose.onNodeWithContentDescription("Katmanlar").performScrollTo().performClick()
        waitFor { compose.onAllNodesWithTag("layers_panel").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Yeni katman").performClick()
        waitFor { canvas().layers.size == 2 }
        val top = canvas().layers.last()
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        compose.waitForIdle()
        // Yeni çizim etkin (yeni) katmana eklenir; eski katman etkilenmez
        drag(Offset(size.x * 0.2f, size.y * 0.5f), Offset(200f, 0f))
        waitFor { canvas().strokes.size == 2 }
        assertEquals(listOf(null, top.id), canvas().strokes.map { it.layerId })
        assertEquals(listOf(Layers.of(canvas().strokes[0], canvas().layers), top.id), canvas().strokes.map { Layers.of(it, canvas().layers) })

        // Gizle: katman gizlenir; gizli katmana çizilmez
        // v45 dikey ekranda adlar düğmenin içinde, üst satır kayar: Katmanlar (satırın sonunda) önce görünür yapılır
        compose.onNodeWithContentDescription("Katmanlar").performScrollTo().performClick()
        waitFor { compose.onAllNodesWithTag("layers_panel").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("Katmanı gizle")[0].performClick()
        waitFor { !canvas().layers.last().visible }
        compose.onNodeWithContentDescription("Katmanı göster").performClick()
        waitFor { canvas().layers.last().visible }
        // Kilitle: kilitli katmandaki eleman seçilemez
        compose.onAllNodesWithContentDescription("Katmanı kilitle")[0].performClick()
        waitFor { canvas().layers.last().locked }
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        compose.waitForIdle()
        drag(Offset(size.x * 0.2f, size.y * 0.7f), Offset(200f, 0f))
        waitForText("Etkin katman gizli ya da kilitli", substring = true)
        assertEquals(2, canvas().strokes.size)
        mode("Seç")
        tap(Offset(size.x * 0.2f + 100f, size.y * 0.5f))
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("1 eleman seçili").fetchSemanticsNodes().isEmpty())
        // v45 kilitli eleman seçilemez: oraya dokunmak boş yere dokunmak gibidir (v47 menü açılmaz)
        assertTrue(compose.onAllNodesWithTag("canvas_add_menu").fetchSemanticsNodes().isEmpty())
        tap(Offset(size.x * 0.2f + 100f, size.y * 0.3f))
        waitForText("1 eleman seçili")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V15FormulaTest : V15EditorBase() {
    @Test
    fun formulaIsInsertedAsTextAndSlotsAreEditable() {
        createNotesCanvasAndOpen("Formül Testi")
        val size = canvasSize()
        addMenu("Formül ekle")
        waitFor { compose.onAllNodesWithTag("formula_picker").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Üslü ifade").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Formül: Üslü ifade")
        tap(Offset(size.x * 0.5f, size.y * 0.4f))
        waitFor { canvas().strokes.size == 1 }
        val f = canvas().strokes.single()
        assertEquals(BrushType.FORMULA, f.brushType)
        // v17: formül elemanlarına ayrılır (her harf/sayı ayrı yuva)
        val slotA = com.karapp.drawguidance.content.formula.Formula.slots(f.formula!!.source).first { it.value == "a" }.name
        assertTrue(com.karapp.drawguidance.content.formula.Formula.isAtomized(f.formula!!.source))
        modeBarTitle("Taşı")
        compose.onNodeWithText("Bitti").performClick()
        modeBarGone()
        // Seçip düzenle: v16'da formülün üstünde yazı alanı açılır (pencere yok); yuvaya değer yazılır
        mode("Seç")
        tap(Offset(size.x * 0.5f, size.y * 0.4f))
        waitForText("Formülü düzenle")
        if (compose.onAllNodesWithTag("formula_inline_editor").fetchSemanticsNodes().isEmpty()) {
            compose.onAllNodesWithText("Formülü düzenle")[0].performClick()
        }
        waitFor { compose.onAllNodesWithTag("formula_inline_editor").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithTag("formula_edit_sheet").fetchSemanticsNodes().isEmpty())
        compose.onNode(hasSetTextAction() and hasContentDescription("Yuva $slotA")).performTextReplacement("x+1")
        waitFor { canvas().strokes.single().formula!!.source.contains("\\slot{$slotA}{x+1}") }
        val w0 = f.points[1].x - f.points[0].x
        val w1 = canvas().strokes.single().let { it.points[1].x - it.points[0].x }
        assertTrue("formül genişledi: $w0 → $w1", w1 > w0)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V15AutosaveTest : V15EditorBase() {
    @Test
    fun autosaveOffKeepsChangesUntilSaveIsPressed() {
        createCanvasAndOpen("Kayıt")
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        waitForText("Otomatik kaydet")
        compose.onNodeWithText("Otomatik kaydet").performClick()
        SystemBack.press()
        waitForDescription("Kaydet")
        val size = canvasSize()
        drag(Offset(size.x * 0.2f, size.y * 0.3f), Offset(200f, 0f))
        waitForDescription("Kaydet (kaydedilmemiş değişiklik var)")
        Thread.sleep(600)
        compose.waitForIdle()
        assertEquals("otomatik kayıt kapalıyken yazılmaz", 0, canvas().strokes.size)
        compose.onNodeWithContentDescription("Kaydet (kaydedilmemiş değişiklik var)").performClick()
        waitFor { canvas().strokes.size == 1 }
        waitForDescription("Kaydet")
        // Kaydedilmemiş değişiklikle çıkarken sorulur
        drag(Offset(size.x * 0.2f, size.y * 0.5f), Offset(200f, 0f))
        waitForDescription("Kaydet (kaydedilmemiş değişiklik var)")
        SystemBack.press()
        waitForText("Kaydedilmemiş değişiklikler")
        compose.onNodeWithText("Kaydet").performClick()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isEmpty() }
        waitFor { canvas().strokes.size == 2 }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V15GraphTest : V15EditorBase() {
    @Test
    fun graphToolIncreasesPatternAndPointsAndEditsNumbers() {
        createNotesCanvasAndOpen("Grafik Testi")
        val size = canvasSize()
        addMenu("Şekil ekle")
        waitForText("Grafik")
        compose.onAllNodesWithText("Grafik")[0].performClick()
        waitForText("Grafik eğrileri")
        compose.onNodeWithText("Sinüs").performSemanticsAction(SemanticsActions.OnClick)
        tap(Offset(size.x * 0.5f, size.y * 0.4f))
        waitFor { canvas().strokes.size == 1 }
        val repeat0 = canvas().strokes.single().shape!!.graph!!.repeat
        compose.onNodeWithText("Bitti").performClick()
        mode("Seç")
        tap(Offset(size.x * 0.5f, size.y * 0.4f))
        editButton("Şekli düzenle")
        // Grafikler grafik aracıyla açılır
        waitForText("Şekli Düzenle: Grafik")
        // v18: fonksiyon grafiğinde desen eğrinin sıklığı (x aralığı aynı kalır)
        compose.onNodeWithContentDescription("Deseni artır").performClick()
        waitFor { canvas().strokes.single().shape!!.graph!!.freq == 2 }
        assertEquals(repeat0, canvas().strokes.single().shape!!.graph!!.repeat)
        val ticks0 = canvas().strokes.single().shape!!.graph!!.ticks
        compose.onNodeWithContentDescription("Noktaları artır").performClick()
        waitFor { canvas().strokes.single().shape!!.graph!!.ticks == ticks0 + 1 }
        assertNotNull(canvas().strokes.single().shape!!.subpaths.firstOrNull { it.tag?.startsWith("tick:") == true })
    }
}
