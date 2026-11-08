package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.PanelTab
import com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool
import com.karapp.drawguidance.ui.screens.canvasedit.components.ToolDescriptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v33 araç yardımı testleri için ortak yardımcılar. */
abstract class V33EditorBase : V31EditorBase() {
    protected fun vm(): CanvasEditViewModel = CanvasEditViewModel.lastCreated?.get() ?: error("düzenleyici yok")

    /** Köşedeki açıklama kutusunun okunan metni (kutu yoksa null). */
    protected fun helpBox(): String? = compose.onAllNodesWithTag("tool_help_box").fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")

    protected fun inRow(label: String, tag: String) = hasContentDescription(label) and hasAnyAncestor(hasTestTag(tag))

    /** [label] düğmesini [millis] ms basılı tutar (gerçek dokunuş). */
    protected fun hold(label: String, tag: String, millis: Long? = null) {
        // Kaydırılan satırda düğme önce görünür alana getirilir
        runCatching { compose.onNode(inRow(label, tag)).performScrollTo() }
        compose.onNode(inRow(label, tag)).performTouchInput {
            if (millis == null) longClick()
            else { down(center); advanceEventTime(millis); up() }
        }
        compose.waitForIdle()
    }

    protected fun description(labelRes: Int): String {
        val ctx = compose.activity
        return ToolDescriptions.table(ctx).getValue(ctx.getString(labelRes))
    }
}

/**
 * v33 (2) çizim alanının üstündeki menülerde dokununca düğmenin adı, 2 sn basılı tutunca ne işe yaradığı köşedeki
 * kutuda görünür; basılı tutmak düğmeyi çalıştırmaz, 1 sn basmak dokunuş sayılır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V33ToolHelpTest : V33EditorBase() {
    @Test
    fun tapShowsNameHoldShowsDescription() {
        createCanvasAndOpen("Yardım")
        assertNull(helpBox())
        // Dokunuş: düğme çalışır (silgiye geçilir). v45 gelişmiş modda ad köşeye yazılmaz; düğmenin içinde yazar
        compose.onNode(inRow("Silgi", "tool_row_draw")).performClick()
        waitFor { vm().brushSettings.value.brushType == BrushType.ERASER }
        assertNull(helpBox())
        assertTrue(compose.onAllNodes(hasTestTag("inline_label:Silgi") and hasAnyAncestor(hasTestTag("tool_row_draw")), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        shot("v33-help-name")
        compose.mainClock.advanceTimeBy(1700)
        assertNull(helpBox())

        // 2 sn basılı tutma: açıklama görünür, düğme çalışmaz (Katmanlar paneli açılmaz)
        hold("Katmanlar", "mode_selector")
        waitFor { helpBox()?.contains(description(R.string.tab_layers)) == true }
        assertNull(vm().panel.value)
        shot("v33-help-description")
        // Açıklamaya dokununca kapanır
        compose.onNodeWithTag("tool_help_box").performClick()
        waitFor { helpBox() == null }

        // 1 sn basmak dokunuş sayılır: açıklama yok, düğme çalışır
        hold("Özellikler", "mode_selector", millis = 1000)
        waitFor { vm().panel.value == PanelTab.BRUSH }
        val box = helpBox()
        assertTrue(box == null || !box.contains(description(R.string.brush_settings_toggle)))
    }

    @Test
    fun modeSelectorHoldShowsModeDescription() {
        createCanvasAndOpen("Modlar")
        hold("Düzenle", "mode_selector")
        waitFor { helpBox()?.contains(description(R.string.mode_edit_pick)) == true }
        // Mod değişmedi
        assertEquals(EditMode.DRAW, vm().mode.value)
        // Dokunuş modu değiştirir (seçili modun adı düğmede yazar)
        compose.onNode(inRow("Ekle", "mode_selector")).performClick()
        waitFor { vm().mode.value == EditMode.ADD }
    }
}

/**
 * v33 (1) şekil düzenleme menüsü: üst satırda Noktalar, Çizgiler, Alan boya, Döndür, Ölçekle ve Katmanlar kaydırmadan
 * görünür; Nokta ekle ve Çizgi çiz alttaki nokta panelinde. Noktalar ailesindeyken Noktalar'a dokunmak alt aracı korur.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V33ShapeEditMenuTest : V33EditorBase() {
    @Test
    fun allShapeEditButtonsVisibleAndNodePanel() {
        createCanvasAndOpen("Şekil menüsü")
        val size = canvasSize()
        addSquareViaMenu(Offset(size.x * 0.3f, size.y * 0.3f), Offset(size.x * 0.3f, size.x * 0.3f))
        waitFor { shapes().size == 1 }
        mode("Düzenle")
        // v34 Vektör düzenle yerine şekil araçları doğrudan Düzenle satırında
        waitFor { hasTopTool("Nokta düzenle") }
        if (vm().selectionKind() != com.karapp.drawguidance.ui.screens.canvasedit.SelectionKind.SHAPE) {
            topTool("Nokta düzenle").performClick()
            tap(Offset(size.x * 0.3f, size.y * 0.3f + size.x * 0.15f))
        } else topTool("Nokta düzenle").performClick()
        waitFor { vm().mode.value == EditMode.SHAPE_EDIT }
        val rootWidth = compose.onRoot().fetchSemanticsNode().size.width
        // v47 Katmanlar düzenleme satırında değil, mod menüsünde
        val labels = listOf("Geri al", "Yinele", "Noktalar", "Çizgiler", "Alan boya", "Döndür", "Ölçekle")
        labels.forEach { label ->
            val node = compose.onNode(inRow(label, "tool_row_context")).fetchSemanticsNode()
            val b = node.boundsInRoot
            assertTrue("$label ekranda değil: $b", b.left >= 0f && b.right <= rootWidth.toFloat())
        }
        // Eski üst menü düğmeleri satırda yok, nokta panelinde var
        listOf("Nokta ekle", "Çizgi çiz").forEach {
            assertFalse(it, hasTopTool(it))
            assertTrue(it, hasNodeTool(it))
        }
        assertTrue(compose.onAllNodes(hasContentDescription("Noktalar") and hasAnyAncestor(hasTestTag("tool_row_context")) and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).fetchSemanticsNodes().isNotEmpty())
        shot("v33-shape-edit-row")
        // Nokta ekle → Noktalar seçili kalır; Noktalar'a dokunmak Nokta ekle'yi korur
        compose.onNode(inRow("Nokta ekle", "node_tool_controls")).performClick()
        waitFor { vm().shapeTool.value == ShapeTool.ADD_NODE }
        modeBarTitle("Şekli Düzenle: Nokta ekle")
        topTool("Noktalar").performClick()
        compose.waitForIdle()
        assertEquals(ShapeTool.ADD_NODE, vm().shapeTool.value)
        // Alan boya: nokta paneli kalkar, renk satırı gelir; 2 sn basılı tutunca açıklama
        topTool("Alan boya").performClick()
        waitFor { vm().shapeTool.value == ShapeTool.FILL && exists("region_fill_controls") && !exists("node_tool_controls") }
        hold("Alan boya", "tool_row_context")
        waitFor { helpBox()?.contains(description(R.string.tool_region_fill)) == true }
        shot("v33-shape-edit-fill-help")
        // Noktalar'a dönüş: Taşı / seç
        topTool("Noktalar").performClick()
        waitFor { vm().shapeTool.value == ShapeTool.NODES && exists("node_tool_controls") }
    }
}

/**
 * v33 (2) açıklama tablosu: çizim alanının üstündeki menülerde (üst satırlar, mod seçici, mod çubuğu, sayfa şeridi)
 * görünen her düğmenin açıklaması var. Resim, not alma ve çizgi roman tuvallerinde birçok mod gezilir.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V33ToolHelpCoverageTest : V33EditorBase() {
    private val containers = listOf("tool_row_draw", "tool_row_context", "mode_selector", "mode_bar", "page_strip", "node_tool_controls", "simple_tool_row")

    /** Menülerdeki tıklanabilir düğmelerin adları. */
    private fun labels(): Set<String> {
        val out = LinkedHashSet<String>()
        containers.forEach { tag ->
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick) and hasAnyAncestor(hasTestTag(tag))).fetchSemanticsNodes().forEach { n ->
                val label = n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                    ?: n.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
                if (label != null) out += label
            }
        }
        // Renk kutuları ve sayfa numaraları araç değil, içerik
        val kidsColors = com.karapp.drawguidance.ui.screens.canvasedit.components.KidsColors.map { it.second }.toSet()
        return out.filterNot { it.startsWith("Renk #") || it in kidsColors || Regex("Sayfa \\d+").matches(it) || Regex("\\d+").matches(it) }.toSet()
    }

    private fun visit(modes: List<EditMode>, seen: MutableSet<String>) {
        modes.forEach { m ->
            compose.runOnUiThread { vm().chooseMode(m) }
            compose.waitForIdle()
            advanceMain(100)
            seen += labels()
        }
        compose.runOnUiThread { vm().chooseMode(EditMode.DRAW) }
        compose.waitForIdle()
    }

    @Test
    fun everyToolbarButtonHasDescription() {
        val seen = LinkedHashSet<String>()
        createCanvasAndOpen("Kapsam")
        val size = canvasSize()
        seen += labels()
        addSquareViaMenu(Offset(size.x * 0.3f, size.y * 0.3f), Offset(size.x * 0.25f, size.x * 0.25f))
        waitFor { shapes().size == 1 }
        // Seçili şekille bütün modlar
        visit(listOf(EditMode.SELECT, EditMode.MOVE, EditMode.ADD, EditMode.EDIT_PICK, EditMode.EXPAND, EditMode.ROTATE, EditMode.SCALE, EditMode.CROP), seen)
        compose.runOnUiThread { vm().editSelectedShape() }
        waitFor { vm().mode.value == EditMode.SHAPE_EDIT }
        listOf(ShapeTool.NODES, ShapeTool.ADD_NODE, ShapeTool.CONNECT, ShapeTool.LINES, ShapeTool.FILL).forEach { t ->
            compose.runOnUiThread { vm().selectShapeTool(t) }
            compose.waitForIdle()
            seen += labels()
        }
        val ctx = compose.activity
        val table = ToolDescriptions.table(ctx)
        val missing = seen.filter { it !in table }
        assertTrue("Açıklaması olmayan düğmeler: $missing", missing.isEmpty())
        assertTrue(seen.size > 30)
    }

    @Test
    fun notesAndComicButtonsHaveDescriptions() {
        val seen = LinkedHashSet<String>()
        createNotesCanvasAndOpen("Not kapsam")
        seen += labels()
        visit(listOf(EditMode.SELECT, EditMode.MOVE, EditMode.ADD, EditMode.EDIT_PICK, EditMode.EXPAND), seen)
        val table = ToolDescriptions.table(compose.activity)
        val missing = seen.filter { it !in table }
        assertTrue("Açıklaması olmayan düğmeler (not): $missing", missing.isEmpty())
        assertEquals(CanvasKind.NOTES, vm().canvas.value!!.kind)
    }

    /** v33 basit çizim türlerinin tek menüsü, Şekil boyama ve basit Seç çubuğu. */
    @Test
    fun simpleKindsButtonsHaveDescriptions() {
        val seen = LinkedHashSet<String>()
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        listOf(CanvasKind.SIMPLE_NOTES, CanvasKind.KIDS).forEachIndexed { i, kind ->
            val name = "Basit $i"
            kotlinx.coroutines.runBlocking {
                org.koin.core.context.GlobalContext.get().get<com.karapp.drawguidance.data.repository.CanvasRepository>()
                    .createCanvas(name, 800, 800, "#FFFFFF", aiEnabled = false, kind = kind)
            }
            waitForText(name)
            compose.onAllNodes(androidx.compose.ui.test.hasText(name))[0].performClick()
            waitFor { exists("simple_tool_row") && vm().canvas.value?.name == name }
            seen += labels()
            visit(listOf(EditMode.PAINT, EditMode.TEXT), seen)
            compose.onNodeWithContentDescription("Geri").performClick()
            waitFor { !exists("drawing_canvas") }
        }
        val missing = seen.filter { it !in ToolDescriptions.table(compose.activity) }
        assertTrue("Açıklaması olmayan düğmeler (basit): $missing", missing.isEmpty())
        // v45 çocuk menüsünde kısa adlar: Şekil boyama → Kova, Arka plan boyama → Zemin; yeni Boya (boya fırçası)
        assertTrue("$seen", seen.containsAll(listOf("Kova", "Zemin", "Boya")))
    }
}

/** v33 çizgi roman Ekle satırındaki düğmelerin açıklamaları. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V33ToolHelpComicTest : V33EditorBase() {
    @Test
    fun comicAddRowDescribed() {
        createComicCanvasAndOpen("Roman kapsam")
        compose.runOnUiThread { vm().chooseMode(EditMode.ADD) }
        compose.waitForIdle()
        val table = ToolDescriptions.table(compose.activity)
        val labels = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick) and hasAnyAncestor(hasTestTag("tool_row_context")))
            .fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
        assertTrue(labels.contains("Konuşma balonu"))
        val missing = labels.filter { it !in table }
        assertTrue("Açıklaması olmayan düğmeler (çizgi roman): $missing", missing.isEmpty())
        // Ekle satırının sonundaki düğmeye basılı tutma da açıklama verir
        hold("Resim ekle", "tool_row_context")
        waitFor { helpBox()?.contains(description(R.string.add_image)) == true }
    }
}
