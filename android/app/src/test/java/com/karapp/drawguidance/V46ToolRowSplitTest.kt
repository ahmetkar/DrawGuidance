package com.karapp.drawguidance

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.components.TOOL_PART_DIVIDER
import com.karapp.drawguidance.ui.screens.canvasedit.components.ToolRowSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v46 (5) üst menünün iki parçaya bölünme noktası (saf kural). */
class V46ToolRowSplitRulesTest {
    @Test fun smallMenusAreNotSplit() {
        for (n in 0..4) assertEquals(n, ToolRowSplit.index(n))
    }

    @Test fun splitIsBalancedAndPrefersGroupBoundaries() {
        // Yarıya bir düğme yakın dörtlü grup sınırı: grup bölünmez
        assertEquals(4, ToolRowSplit.index(9))
        assertEquals(4, ToolRowSplit.index(10))
        assertEquals(8, ToolRowSplit.index(15))
        assertEquals(8, ToolRowSplit.index(16))
        assertEquals(8, ToolRowSplit.index(17))
        // Sınır uzaksa ya da bir parçada tek düğme kalacaksa tam yarı (ilk parça en çok bir fazla)
        assertEquals(3, ToolRowSplit.index(5))
        assertEquals(4, ToolRowSplit.index(6))
        assertEquals(4, ToolRowSplit.index(7))
        assertEquals(6, ToolRowSplit.index(11))
        assertEquals(6, ToolRowSplit.index(12))
        for (n in 5..40) {
            val at = ToolRowSplit.index(n)
            assertTrue("$n → $at", at in 2..n - 2)
            // İki parça dengeli: fark en çok üç düğme (grup sınırına kayma payı)
            assertTrue("$n → $at", kotlin.math.abs(at - (n - at)) <= 3)
        }
    }
}

abstract class V46SplitBase : V45EditorBase() {
    protected fun part(tag: String): SemanticsNode? =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()

    protected fun partDividers(row: String) =
        compose.onAllNodes(hasTestTag(TOOL_PART_DIVIDER) and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true).fetchSemanticsNodes()

    /** İki parça alt alta: ikincinin üstü birincinin altında, ikisi de ekranın içinde; yan yana ayracı yok. */
    protected fun assertStacked(row: String) {
        val a = part("${row}_1") ?: error("${row}_1 yok")
        val b = part("${row}_2") ?: error("${row}_2 yok: menü bölünmedi")
        assertTrue("$row: ${rectOf(a)} / ${rectOf(b)}", rectOf(b).top >= rectOf(a).bottom - 0.5f)
        assertTrue(rectOf(a).width > root().width * 0.6f && rectOf(b).width > root().width * 0.6f)
        assertTrue(partDividers(row).isEmpty())
        // Her parçada düğme var; toplam düğme sayısı bölünmeden önceki kadar
        assertTrue(rowButtons("${row}_1").isNotEmpty() && rowButtons("${row}_2").isNotEmpty())
        assertEquals(rowButtons(row).size, rowButtons("${row}_1").size + rowButtons("${row}_2").size)
    }

    /** İki parça yan yana: aynı satırda, ikinci birincinin ardında, aralarında ayraç. */
    protected fun assertSideBySide(row: String) {
        val a = part("${row}_1") ?: error("${row}_1 yok")
        val b = part("${row}_2") ?: error("${row}_2 yok: menü bölünmedi")
        val ra = rectOf(a); val rb = rectOf(b)
        assertTrue("$row: $ra / $rb", rb.left >= ra.right - 0.5f)
        assertTrue("$row aynı satırda: $ra / $rb", kotlin.math.abs(ra.center.y - rb.center.y) < ra.height * 0.5f)
        val d = partDividers(row)
        assertEquals(1, d.size)
        val rd = rectOf(d[0])
        assertTrue(rd.left >= ra.right - 1f && rd.right <= rb.left + 1f)
        assertEquals(rowButtons(row).size, rowButtons("${row}_1").size + rowButtons("${row}_2").size)
    }
}

/** Dikey telefon: üst menü iki satır alt alta, mod menüsü (Çiz/Ekle/Seç…) onların altında; mod değişince menü değişir. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V46PortraitSplitTest : V46SplitBase() {
    @Test
    fun advancedMenusStackAboveModeMenu() {
        openKind("Bölünmüş", CanvasKind.ART)
        waitFor { exists("tool_row_draw") }
        assertStacked("tool_row_draw")
        val modes = node("mode_selector")
        assertTrue("mod menüsü iki parçanın altında", rectOf(modes).top >= rectOf(part("tool_row_draw_2")!!).bottom - 0.5f)
        // Ekle modunda menü değişir (yine iki satır), mod menüsü yerinde
        compose.runOnIdle { vm().chooseMode(EditMode.ADD) }
        waitFor { exists("tool_row_context") }
        assertFalse(exists("tool_row_draw"))
        assertStacked("tool_row_context")
        assertTrue(rectOf(node("mode_selector")).top >= rectOf(part("tool_row_context_2")!!).bottom - 0.5f)
        // Düğmeler çalışır: ikinci parçadaki düğme seçilir
        val second = rowButtons("tool_row_context_2").first()
        compose.runOnIdle { second.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action?.invoke() }
        compose.waitForIdle()
        assertGrouped("tool_row_context")
        shot("v46-split-portrait")
    }

    @Test
    fun notesAndComicAlsoSplit() {
        for (kind in listOf(CanvasKind.NOTES, CanvasKind.COMIC)) {
            if (kind != CanvasKind.NOTES) {
                // Seç modundan çıkıp (geri tuşu önce modu kapatır) tuvalden çık
                compose.runOnIdle { vm().chooseMode(EditMode.DRAW) }
                compose.waitForIdle()
                SystemBack.press()
                waitFor { !exists("drawing_canvas") }
                kotlinx.coroutines.runBlocking { repo().createCanvas("B $kind", 1000, 1414, "#FFFFFF", aiEnabled = false, kind = kind) }
                waitForText("B $kind")
                compose.onAllNodes(androidx.compose.ui.test.hasText("B $kind"))[0].performClick()
                waitFor { exists("drawing_canvas") && vm().canvas.value?.kind == kind }
            } else openKind("B $kind", kind)
            compose.runOnIdle { vm().chooseMode(EditMode.DRAW) }
            waitFor { exists("tool_row_draw") }
            assertStacked("tool_row_draw")
            compose.runOnIdle { vm().chooseMode(EditMode.SELECT) }
            waitFor { exists("tool_row_context") }
            assertStacked("tool_row_context")
        }
    }

    @Test
    fun simpleMenuStacksKidsMenuUnchanged() {
        openKind("Basit", CanvasKind.SIMPLE_ART)
        waitFor { exists("simple_tool_row") }
        // v48 basit resimde dikeyde üç menü (alt alta)
        val menus = listOf("simple_menu_add", "simple_menu_draw", "simple_menu_edit").map { rectOf(node(it)) }
        assertTrue(menus.zipWithNext().all { (a, b) -> a.bottom <= b.top + 0.5f })
        SystemBack.press()
        waitFor { !exists("drawing_canvas") }
        kotlinx.coroutines.runBlocking { repo().createCanvas("Çocuk", 1000, 1000, "#FFFFFF", aiEnabled = false, kind = CanvasKind.KIDS) }
        waitForText("Çocuk")
        compose.onAllNodes(androidx.compose.ui.test.hasText("Çocuk"))[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.kind == CanvasKind.KIDS }
        // v47 çocuk menüsü de dikeyde iki satır
        waitFor { part("simple_tool_row_2") != null }
    }

}

/** Yatay telefon: iki parça yan yana (aralarında ayraç). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w891dp-h411dp-land-xhdpi")
class V46LandscapeSplitTest : V46SplitBase() {
    @Test
    fun partsSitSideBySide() {
        // v48 gelişmiş modlarda yatayda üstteki iki menü tek menü (bölünmez, tek satır)
        openKind("Yatay bölünmüş", CanvasKind.ART)
        waitFor { exists("tool_row_draw") }
        assertTrue(part("tool_row_draw_2") == null)
        compose.runOnIdle { vm().chooseMode(EditMode.ADD) }
        waitFor { exists("tool_row_context") }
        assertTrue(part("tool_row_context_2") == null)
        assertGrouped("tool_row_context")
        shot("v46-split-landscape")
    }

    @Test
    fun simpleMenuSingleRowInLandscape() {
        // v47 basit modlarda yatayda tek satır (v48 gruplama yok)
        openKind("Yatay basit", CanvasKind.SIMPLE_NOTES)
        waitFor { exists("simple_tool_row") }
        assertTrue(part("simple_tool_row_2") == null)
        assertGrouped("simple_tool_row")
    }
}

/**
 * v46 (3) eğitimlerin yenilikler grupları: her gelişmiş ve basit türde v46 grubu var, yenilikler olarak önerilir ve
 * baştan sona oynar (iki parçalı menü, seçim menüsü, Panel ekle, formülle yeniden çizilen şekiller, hazır mekân ve
 * karakterlerle çizgi roman sayfası); bitince tuval eski haline döner.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V46TutorialsTest : V32EditorBase() {
    private fun repo() = org.koin.core.context.GlobalContext.get().get<com.karapp.drawguidance.data.repository.CanvasRepository>()

    private fun play(kind: CanvasKind, id: String, check: (List<com.karapp.drawguidance.data.model.Stroke>) -> Unit) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        kotlinx.coroutines.runBlocking { repo().createCanvas("Eğitim $id", 1000, 1414, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText("Eğitim $id")
        compose.onAllNodes(androidx.compose.ui.test.hasText("Eğitim $id"))[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.kind == kind }
        val group = com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialScripts.groups(kind).first { it.id == id }
        val before = live().strokes.size
        compose.runOnIdle { vm().tutorial.fast = true; vm().tutorial.start(group) }
        waitFor { calloutShown() }
        var last: List<com.karapp.drawguidance.data.model.Stroke> = emptyList()
        for (i in 1..group.steps.size) {
            runUntil(90_000) { vm().tutorial.ui.value?.index == i - 1 }
            awaitDemo(90_000)
            advanceMain(250)
            assertEquals("$i / ${group.steps.size}", stepText())
            if (i == group.steps.size) { last = live().strokes; shot("v46-tut-$id") }
            next()
        }
        waitFor { !calloutShown() }
        check(last)
        assertEquals("eğitim bitince tuval eski haline döner", before, live().strokes.size)
        assertTrue(id in vm().tutorial.completed.value)
    }

    @Test fun whatsNewIsV48Everywhere() {
        val s = com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialScripts
        // v48 eğitim sürümü: yenilikler artık yeni menü sistemini tanıtır
        // v50 eğitim sürümü: çizim modlarında yenilikler "Resimden çizim", not türlerinde v48 menüleri
        assertEquals(50, s.VERSION)
        for ((kind, id) in listOf(CanvasKind.ART to "v50_trace_art", CanvasKind.NOTES to "v48_advanced_notes", CanvasKind.COMIC to "v50_trace_comic",
            CanvasKind.SIMPLE_ART to "v50_trace_simple_art", CanvasKind.SIMPLE_NOTES to "v48_simple_simple_notes", CanvasKind.KIDS to "v50_trace_kids")) {
            assertEquals(id, s.whatsNew(kind)!!.id)
            assertTrue(s.groups(kind).any { it.id == id })
        }
        // v48 menü yenilikleri listede tarihçe olarak kalır
        for ((kind, id) in listOf(CanvasKind.ART to "v48_advanced_art", CanvasKind.COMIC to "v48_advanced_comic",
            CanvasKind.SIMPLE_ART to "v48_simple_simple_art", CanvasKind.KIDS to "v48_kids")) assertTrue(id, s.groups(kind).any { it.id == id })
        // v46 yenilikler grubu listede tarihçe olarak kalır
        assertTrue(s.groups(CanvasKind.ART).any { it.id == "art_v46" })
        // Çocuk tanıtımında v45 boya fırçası
        assertTrue(com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialKids.intro.steps.any { it.anchor == com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor.simple("paint_brush") })
    }

    @Test fun art() = play(CanvasKind.ART, "art_v46") { s ->
        assertTrue(s.any { it.shape?.kind == "CIRCLE" } && s.count { it.shape?.kind == "TRIANGLE" } >= 2)
        assertTrue("kuşlar kopyalandı", s.count { it.brushType == com.karapp.drawguidance.data.model.BrushType.INK_PEN } >= 3)
    }

    @Test fun notes() = play(CanvasKind.NOTES, "notes_v46") { s ->
        assertEquals(3, s.count { com.karapp.drawguidance.ui.screens.canvasedit.state.NotesPanels.isNotesPanel(it) })
        val fb = s.first { it.shape?.kind == "lib.sci.phys.free_body" }
        assertEquals("40", fb.shape!!.params!!["F"])
        assertEquals("30", fb.shape!!.params!!["alpha"])
        assertEquals("17", s.first { it.shape?.kind == "lib.sci.chem.bohr_atom" }.shape!!.params!!["z"])
        assertTrue(s.any { it.brushType == com.karapp.drawguidance.data.model.BrushType.FORMULA })
    }

    @Test fun comic() = play(CanvasKind.COMIC, "comic_v46") { s ->
        assertEquals(3, s.count { com.karapp.drawguidance.ui.screens.canvasedit.state.PanelSets.isPanel(it) })
        assertEquals("3 mekân + 3 karakter", 6, s.count { it.brushType == com.karapp.drawguidance.data.model.BrushType.IMAGE })
        // Mekânlar panellerin arkasında
        val firstPanel = s.indexOfFirst { com.karapp.drawguidance.ui.screens.canvasedit.state.PanelSets.isPanel(it) }
        assertTrue(s.take(firstPanel).count { it.brushType == com.karapp.drawguidance.data.model.BrushType.IMAGE } == 3)
        assertTrue(s.count { it.text?.box?.balloon != null } >= 3)
    }

    @Test fun simpleArt() = play(CanvasKind.SIMPLE_ART, "simple_art_v46") { s -> assertTrue(s.any { it.shape?.kind == "HOUSE" }) }

    @Test fun simpleNotes() = play(CanvasKind.SIMPLE_NOTES, "simple_notes_v46") { s ->
        val lever = s.first { it.shape?.kind == "lib.sci.phys.lever" }
        assertEquals("90", lever.shape!!.params!!["P"])
        assertEquals("3", lever.shape!!.params!!["b"])
    }
}
