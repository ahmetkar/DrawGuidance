package com.karapp.drawguidance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BackgroundSpec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.components.INLINE_LABEL_PREFIX
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.PanelTab
import com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasMenuPlacement
import com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction
import com.karapp.drawguidance.ui.screens.canvasedit.components.SelectionMenu
import com.karapp.drawguidance.ui.screens.canvasedit.components.SelectionMenuIds
import com.karapp.drawguidance.ui.screens.canvasedit.components.TOOL_GROUP_DIVIDER
import com.karapp.drawguidance.ui.screens.canvasedit.components.TabletWindows
import com.karapp.drawguidance.ui.screens.canvasedit.components.ToolGroups
import com.karapp.drawguidance.ui.screens.canvasedit.components.ToolRowAlign
import com.karapp.drawguidance.ui.screens.canvasedit.components.WindowPositionStore
import com.karapp.drawguidance.ui.screens.canvasedit.components.modeToolbar
import com.karapp.drawguidance.ui.screens.canvasedit.components.selectionMenuActions
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import com.karapp.drawguidance.ui.theme.DrawGuidanceTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** v45 (12, 13, 13.1) saf kurallar: gruplar, hizalama, menü yerleşimi, pencere sınırları, mod sırası. */
class V45EditorRulesTest {
    @Test fun dividersEveryFourButtons() {
        // v48 menülerde gruplama yok: hiçbir menüde ayraç konmaz
        for (n in listOf(0, 4, 5, 8, 9, 13, 16)) assertEquals(emptyList<Int>(), ToolGroups.dividerBefore(n))
        assertEquals(0, ToolGroups.dividerCount(6, size = 2))
    }

    @Test fun alignOnlyWhenSelectedIsNotFullyVisible() {
        // Görünür: kıpırdamaz (döndürmede/yeniden çizimde sıçrama yok)
        assertNull(ToolRowAlign.target(10f, 90f, 400f, 120, 600, 20f, rtl = false))
        assertNull(ToolRowAlign.target(0f, 400f, 400f, 0, 600, 20f, rtl = false))
        // Sağda gizli: en az kaydırmayla düğme sağ kenara (paylı) gelir
        assertEquals(120 + (560 - 400 + 20), ToolRowAlign.target(500f, 560f, 400f, 120, 1000, 20f, rtl = false))
        // Satırdan geniş düğme: başlangıç kenarına
        assertEquals(120 + (-50 - 20), ToolRowAlign.target(-50f, 600f, 400f, 120, 1000, 20f, rtl = false))
        // Solda gizli: geri kayar
        assertEquals(300 + (-80) - 20, ToolRowAlign.target(-80f, -20f, 400f, 300, 1000, 20f, rtl = false))
        // Sınırlar: 0 ile en çok kaydırma arası; zaten oradaysa null
        assertEquals(0, ToolRowAlign.target(-80f, -20f, 400f, 50, 1000, 20f, rtl = false))
        assertEquals(1000, ToolRowAlign.target(900f, 960f, 400f, 700, 1000, 20f, rtl = false))
        assertNull(ToolRowAlign.target(900f, 960f, 400f, 1000, 1000, 20f, rtl = false))
        // Sağdan sola: sola (sona doğru) taşan düğme sol kenara, sağa (başa doğru) taşan sağ kenara (paylı) gelir
        assertEquals(100 + (20 - (-100)), ToolRowAlign.target(-100f, -40f, 400f, 100, 2000, 20f, rtl = true))
        assertEquals(300 + (400 - 20 - 450), ToolRowAlign.target(420f, 450f, 400f, 300, 2000, 20f, rtl = true))
        assertNull(ToolRowAlign.target(10f, 50f, 0f, 0, 0, 20f, rtl = false))
    }

    @Test fun selectionMenuSitsAboveTheEndCornerAndStaysOnScreen() {
        val area = IntSize(1000, 2000)
        val menu = IntSize(400, 60)
        val sel = Rect(300f, 800f, 700f, 1100f)
        // Soldan sağa: sağ üst köşe, seçimin üstünde (üstüne binmez)
        val ltr = CanvasMenuPlacement.aboveOrBelow(sel, menu, area, 10f, 8f, rtl = false)
        assertEquals(IntOffset(300, 730), ltr)
        assertTrue(ltr.y + menu.height <= sel.top)
        // Sağdan sola: sol üst köşe
        assertEquals(IntOffset(300, 730), CanvasMenuPlacement.aboveOrBelow(sel, menu, area, 10f, 8f, rtl = true))
        val small = Rect(500f, 800f, 600f, 900f)
        assertEquals(200, CanvasMenuPlacement.aboveOrBelow(small, menu, area, 10f, 8f, rtl = false).x)
        assertEquals(500, CanvasMenuPlacement.aboveOrBelow(small, menu, area, 10f, 8f, rtl = true).x)
        // Üstte yer yok: altında
        val top = Rect(300f, 20f, 700f, 300f)
        val below = CanvasMenuPlacement.aboveOrBelow(top, menu, area, 10f, 8f, rtl = false)
        assertEquals(310, below.y)
        assertTrue(below.y >= top.bottom)
        // Ne üstte ne altta yer var: ekranın içinde kalır (kaçınılmaz binme)
        val huge = Rect(0f, 30f, 1000f, 1990f)
        val clamped = CanvasMenuPlacement.aboveOrBelow(huge, menu, area, 10f, 8f, rtl = false)
        assertTrue(clamped.y >= 8 && clamped.y + menu.height <= area.height - 8)
        // Ekranın dışındaki seçim: menü yine ekranda
        val off = CanvasMenuPlacement.aboveOrBelow(Rect(1200f, 900f, 1500f, 1000f), menu, area, 10f, 8f, rtl = false)
        assertTrue(off.x >= 8 && off.x + menu.width <= area.width - 8)
        val offLeft = CanvasMenuPlacement.aboveOrBelow(Rect(-600f, 900f, -300f, 1000f), menu, area, 10f, 8f, rtl = true)
        assertEquals(8, offLeft.x)
    }

    @Test fun addMenuOpensAtTheTapAndFlipsAtEdges() {
        val area = IntSize(1000, 2000)
        val menu = IntSize(300, 800)
        assertEquals(IntOffset(100, 200), CanvasMenuPlacement.atPoint(Offset(100f, 200f), menu, area, 8f, rtl = false))
        // Sağ kenarda sola, alt kenarda yukarı açılır
        assertEquals(IntOffset(600, 1100), CanvasMenuPlacement.atPoint(Offset(900f, 1900f), menu, area, 8f, rtl = false))
        // Sağdan sola: noktanın soluna açılır; yer yoksa sağına
        assertEquals(IntOffset(600, 200), CanvasMenuPlacement.atPoint(Offset(900f, 200f), menu, area, 8f, rtl = true))
        assertEquals(IntOffset(100, 200), CanvasMenuPlacement.atPoint(Offset(100f, 200f), menu, area, 8f, rtl = true))
    }

    @Test fun secondTapOfDoubleTapIsRecognisedOnlyQuicklyAndNearby() {
        val at = Offset(200f, 300f)
        assertTrue(CanvasMenuPlacement.isSecondTap(1000L, 1100L, at, Offset(210f, 305f), 30f))
        assertTrue(CanvasMenuPlacement.isSecondTap(1000L, 1000L + CanvasMenuPlacement.DOUBLE_TAP_MS, at, at, 30f))
        // Geç ya da uzak: sıradan dokunuş (menü kapanır, kağıt sığmaz)
        assertFalse(CanvasMenuPlacement.isSecondTap(1000L, 1000L + CanvasMenuPlacement.DOUBLE_TAP_MS + 1, at, at, 30f))
        assertFalse(CanvasMenuPlacement.isSecondTap(1000L, 1100L, at, Offset(260f, 300f), 30f))
        // Menü henüz açılmadı ya da dokunuş açılıştan önce
        assertFalse(CanvasMenuPlacement.isSecondTap(-1L, 1100L, at, at, 30f))
        assertFalse(CanvasMenuPlacement.isSecondTap(1000L, 900L, at, at, 30f))
    }

    @Test fun windowsStayOnScreen() {
        val screen = Size(1000f, 2000f)
        val home = Rect(100f, 50f, 400f, 250f)
        assertEquals(Offset(200f, 300f), TabletWindows.clamp(Offset(200f, 300f), home, screen))
        assertEquals(Offset(-100f, -50f), TabletWindows.clamp(Offset(-5000f, -5000f), home, screen))
        assertEquals(Offset(600f, 1750f), TabletWindows.clamp(Offset(5000f, 5000f), home, screen))
        // Ekran boyu panel dikeyde kıpırdamaz
        assertEquals(Offset(-50f, 0f), TabletWindows.clamp(Offset(-50f, 400f), Rect(600f, 0f, 900f, 2000f), screen))
    }

    @Test fun artSwapsAddAndSelect() {
        // v47 her gelişmiş türde dört mod: Seç, Çiz, Ekle, Düzenle (Taşı ve Genişlet Düzenle'nin altında tek düğme)
        for (k in listOf(CanvasKind.ART, CanvasKind.NOTES, CanvasKind.COMIC)) assertEquals(EditMode.Toolbar, modeToolbar(k))
        assertEquals(listOf(EditMode.SELECT, EditMode.DRAW, EditMode.ADD, EditMode.EDIT_PICK), EditMode.Toolbar)
    }
}

/** v45 testlerinin ortak yardımcıları. */
abstract class V45EditorBase : V38LabelsBase() {
    protected fun root(): Rect = compose.onRoot().fetchSemanticsNode().boundsInRoot

    protected fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    /** Düğümün kırpılmamış kutusu (kaydırılan satırda ekran dışındaki düğmenin de gerçek yeri). */
    protected fun rectOf(n: SemanticsNode): Rect = Rect(n.positionInRoot, Size(n.size.width.toFloat(), n.size.height.toFloat()))

    /** Düğüm tamamen görünüyor: kırpılmamış ve ekranın içinde. */
    protected fun fullyVisible(n: SemanticsNode): Boolean {
        val r = rectOf(n)
        return n.boundsInRoot.width >= r.width - 1f && r.left >= root().left - 0.5f && r.right <= root().right + 0.5f
    }

    /** Kaydırılan satırdaki düğmeye önce kaydırıp dokunur. */
    protected fun clickIn(row: String, label: String) {
        val m = hasContentDescription(label) and hasAnyAncestor(hasTestTag(row))
        runCatching { compose.onNode(m).performScrollTo() }
        compose.onNode(m).performClick()
        compose.waitForIdle()
    }

    /** [row] içindeki tıklanabilir düğmeler (soldan sağa ya da [vertical] iken yukarıdan aşağı). */
    protected fun rowButtons(row: String, vertical: Boolean = false): List<SemanticsNode> =
        inReadingOrder(row, compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick) and hasAnyAncestor(hasTestTag(row))).fetchSemanticsNodes(), vertical)

    protected fun dividers(row: String, vertical: Boolean = false): List<SemanticsNode> =
        inReadingOrder(row, compose.onAllNodes(hasTestTag(TOOL_GROUP_DIVIDER) and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true).fetchSemanticsNodes(), vertical)

    /**
     * v46 üst menü iki parçaya bölünebilir (`<satır>_1`, `<satır>_2`; dikeyde alt alta, yatayda yan yana): düğmeler
     * okuma sırasıyla — önce birinci parça, sonra ikinci; parça içinde soldan sağa (ya da yukarıdan aşağı).
     */
    private fun inReadingOrder(row: String, nodes: List<SemanticsNode>, vertical: Boolean): List<SemanticsNode> {
        val second = compose.onAllNodes(hasAnyAncestor(hasTestTag("${row}_2")), useUnmergedTree = true).fetchSemanticsNodes().mapTo(HashSet()) { it.id }
        return nodes.sortedWith(compareBy({ if (it.id in second) 1 else 0 }, { if (vertical) rectOf(it).top else rectOf(it).left }))
    }

    /** v46 satırın parçaları (bölünmüşse iki, değilse satırın kendisi). */
    protected fun rowParts(row: String): List<String> {
        val parts = listOf("${row}_1", "${row}_2").filter { compose.onAllNodes(hasTestTag(it), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        return if (parts.size == 2) parts else listOf(row)
    }

    protected fun name(n: SemanticsNode): String = n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
        ?: n.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text ?: "?"

    /** Ayraçlar her 4 düğmede bir: 4. ile 5., 8. ile 9. düğmenin arasında. */
    protected fun assertGrouped(row: String, vertical: Boolean = false): Int =
        // v46 bölünmüş menüde gruplar her parçada ayrı sayılır (parça sınırı ayracın yerini tutar)
        rowParts(row).sumOf { assertGroupedPart(it, vertical) }

    private fun assertGroupedPart(row: String, vertical: Boolean): Int {
        val buttons = rowButtons(row, vertical)
        val divs = dividers(row, vertical)
        assertEquals("$row: ${buttons.map(::name)}", ToolGroups.dividerCount(buttons.size), divs.size)
        divs.forEachIndexed { k, d ->
            val before = rectOf(buttons[(k + 1) * 4 - 1])
            val after = rectOf(buttons[(k + 1) * 4])
            val b = rectOf(d)
            if (vertical) {
                assertTrue("$row ayraç $k: ${before.bottom} <= ${b.top}", before.bottom <= b.top + 0.5f)
                assertTrue("$row ayraç $k: ${b.bottom} <= ${after.top}", b.bottom <= after.top + 0.5f)
            } else {
                assertTrue("$row ayraç $k önünde ${name(buttons[(k + 1) * 4 - 1])}: ${before.right} <= ${b.left}", before.right <= b.left + 0.5f)
                assertTrue("$row ayraç $k ardında ${name(buttons[(k + 1) * 4])}: ${b.right} <= ${after.left}", b.right <= after.left + 0.5f)
                // Gruplar arası boşluk grup içindekinden büyük
                val inner = rectOf(buttons[1]).left - rectOf(buttons[0]).right
                assertTrue("$row grup arası boşluk: ${after.left - before.right} > $inner", after.left - before.right > inner)
            }
        }
        return buttons.size
    }

    /** [row] satırında adı [label] olan düğme. */
    protected fun button(row: String, label: String): SemanticsNode =
        compose.onNode(hasContentDescription(label) and hasAnyAncestor(hasTestTag(row))).fetchSemanticsNode()


    /** Çiz modunda tuvale bir çizgi çeker (tuval koordinatı) ve sayısı artınca döner. */
    protected fun drawLine(from: Offset, to: Offset) {
        val before = canvas().strokes.size
        compose.runOnIdle { vm().setMode(EditMode.DRAW) }
        drag(screenOf(from.x, from.y), screenOf(to.x, to.y) - screenOf(from.x, from.y))
        waitFor { canvas().strokes.size == before + 1 }
    }

    /** Seç'te elemana dokunup seçer. */
    protected fun selectAt(x: Float, y: Float) {
        compose.runOnIdle { vm().setMode(EditMode.SELECT) }
        tap(screenOf(x, y))
        waitFor { vm().selectedIds.value.isNotEmpty() }
    }

    protected fun selectionBox(): Rect = ElementOps.selectionBounds(canvas().strokes, vm().selectedIds.value)!!.let { Rect(it.left, it.top, it.right, it.bottom) }

    /** Tuval koordinatındaki kutunun kök (ekran) koordinatı. */
    protected fun rootRectOf(r: Rect): Rect {
        val origin = compose.onNodeWithTag("drawing_canvas").fetchSemanticsNode().boundsInRoot.topLeft
        val a = screenOf(r.left, r.top) + origin
        val b = screenOf(r.right, r.bottom) + origin
        return Rect(a, b)
    }

    protected fun menuAction(id: String) = compose.onNodeWithTag("selection_menu_$id")

    protected fun repo() = GlobalContext.get().get<CanvasRepository>()

    protected fun openWithBackground(name: String, kind: CanvasKind, background: BackgroundSpec) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind, background = background) }
        waitForText(name)
        compose.onAllNodes(hasText(name))[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    protected fun windowHandles(): List<String> =
        compose.onAllNodes(SemanticsMatcher("window drag") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("window_drag:") == true }, useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }

    /** Tutamaktan [delta] kadar sürükleme (dokunma eşiği adım adım aşılır). */
    protected fun dragHandle(tag: String, delta: Offset, steps: Int = 25) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(steps) { moveBy(Offset(delta.x / steps, delta.y / steps)) }
            up()
        }
        compose.waitForIdle()
    }
}

/**
 * v45 (3) gelişmiş modların (resim, not, çizgi roman) dikey ekranında düğme adları düğmenin içinde, simgenin yanında;
 * çizim alanının köşesinde ad yazmaz, yalnız basılı tutunca açıklama. (11) gelişmiş resimde Ekle Çiz'in yanında.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V45PortraitLabelsTest : V45EditorBase() {
    private fun inlineLabel(name: String): SemanticsNode? =
        compose.onAllNodes(hasTestTag(INLINE_LABEL_PREFIX + name), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()

    /** [row] satırının her düğmesinde ad düğmenin içinde, simgenin yanında (sağında) yazar. */
    private fun assertInlineLabels(row: String) {
        val buttons = rowButtons(row)
        assertTrue("$row düğmeleri", buttons.size >= 6)
        for (b in buttons) {
            val n = name(b)
            val label = inlineLabel(n)
            assertNotNull("$n adı düğmede yazmalı", label)
            label!!
            val bb = rectOf(b)
            val lb = rectOf(label)
            assertTrue("$n adı düğmenin içinde: $lb ⊂ $bb", lb.left >= bb.left - 0.5f && lb.right <= bb.right + 0.5f && lb.top >= bb.top - 0.5f && lb.bottom <= bb.bottom + 0.5f)
            // Simge adın solunda (düğmenin solunda adın genişliğinden fazla yer var: simge + boşluk)
            assertTrue("$n simgesi adın yanında", lb.left - bb.left >= 14f * compose.density.density)
            assertTrue("$n adı görünür genişlikte", lb.width > 4f)
        }
    }

    private fun assertNothingInCorner(row: String, label: String) {
        clickIn(row, label)
        assertNull("ad köşeye yazılmamalı", helpBox())
        assertTrue(compose.onAllNodesWithTag("tool_help_box").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun artPortraitButtonsShowNamesInsideAndNoCornerName() {
        openKind("Ad", CanvasKind.ART)
        waitFor { exists("tool_row_draw") }
        assertInlineLabels("tool_row_draw")
        // Mod seçicide her modun adı düğmenin içinde
        for (m in rowButtons("mode_selector")) {
            val label = inlineLabel(name(m))
            assertNotNull("${name(m)} modunun adı", label)
            assertTrue(rectOf(m).contains(rectOf(label!!).center))
        }
        // Moda özel satırlar da (Seç, Ekle, Düzenle)
        for (m in listOf(EditMode.SELECT, EditMode.ADD, EditMode.EDIT_PICK)) {
            compose.runOnIdle { vm().chooseMode(m) }
            waitFor { exists("tool_row_context") && vm().mode.value == m }
            assertInlineLabels("tool_row_context")
        }
        compose.runOnIdle { vm().chooseMode(EditMode.DRAW) }
        waitFor { exists("tool_row_draw") }
        shot("v45-portrait-labels-art")
        // Dokununca köşede ad yok; düğme çalışır
        assertNothingInCorner("tool_row_draw", "Silgi")
        waitFor { vm().brushSettings.value.brushType == BrushType.ERASER }
        assertNothingInCorner("mode_selector", "Seç")
        waitFor { vm().mode.value == EditMode.SELECT }
        // Basılı tutunca açıklama (uzun basma yardımı korunur), düğme çalışmaz
        hold("Katmanlar", "mode_selector")
        waitFor { helpBox()?.contains(description(R.string.tab_layers)) == true }
        assertNull(vm().panel.value)
        compose.onNodeWithTag("tool_help_box").performClick()
        waitFor { helpBox() == null }
    }

    @Test
    fun notesAndComicAlsoLabelled() {
        for (kind in listOf(CanvasKind.NOTES, CanvasKind.COMIC)) {
            if (kind == CanvasKind.COMIC) {
                compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
                waitFor { !exists("drawing_canvas") }
                runBlocking { repo().createCanvas("Ad $kind", 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind) }
                waitForText("Ad $kind")
                compose.onAllNodes(hasText("Ad $kind"))[0].performClick()
                waitFor { exists("drawing_canvas") && vm().canvas.value?.kind == kind }
            } else openKind("Ad $kind", kind)
            waitFor { exists("tool_row_draw") }
            assertInlineLabels("tool_row_draw")
            compose.runOnIdle { vm().chooseMode(EditMode.ADD) }
            waitFor { exists("tool_row_context") }
            assertInlineLabels("tool_row_context")
            for (m in rowButtons("mode_selector")) assertNotNull(name(m), inlineLabel(name(m)))
            assertNothingInCorner("tool_row_context", "Yazı ekle")
            compose.runOnIdle { vm().finishAction(); vm().setMode(EditMode.DRAW) }
            compose.waitForIdle()
        }
    }

    @Test
    fun simpleModesUnchangedNoInlineLabels() {
        // v48 basit modların dikey ekranında da her düğmenin adı simgenin yanında (üç menü)
        openKind("Basit", CanvasKind.SIMPLE_ART)
        waitFor { exists("simple_tool_row") }
        val inline = compose.onAllNodes(SemanticsMatcher("inline") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(INLINE_LABEL_PREFIX) == true }, useUnmergedTree = true)
            .fetchSemanticsNodes()
        val buttons = compose.onAllNodes(SemanticsMatcher("tool") { n ->
            n.config.getOrNull(SemanticsProperties.TestTag)?.let { it.startsWith("simple_tool_") && it != "simple_tool_row" } == true
        }).fetchSemanticsNodes()
        assertTrue("basit modda düğme içi ad: ${inline.size} / ${buttons.size}", inline.size >= buttons.size && buttons.size >= 15)
        assertFalse(exists("mode_selector"))
    }

    @Test
    fun artOrderAddNextToDrawWithSameSubMenus() {
        openKind("Sıra", CanvasKind.ART)
        waitFor { exists("mode_selector") }
        val order = rowButtons("mode_selector").map(::name)
        // v47 dört mod (Seç, Çiz, Ekle, Düzenle) ve yeni grup: Elemanlar, Katmanlar, Özellikler
        assertEquals(listOf("Seç", "Çiz", "Ekle", "Düzenle", "Elemanlar", "Katmanlar", "Özellikler"), order)
        // Ekle eklenecekleri, Seç seçim araçlarını göstermeye devam eder
        compose.onNode(hasContentDescription("Ekle") and hasAnyAncestor(hasTestTag("mode_selector"))).performClick()
        waitFor { vm().mode.value == EditMode.ADD }
        waitFor { compose.onAllNodes(hasContentDescription("Şekil ekle") and hasAnyAncestor(hasTestTag("tool_row_context"))).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodes(hasContentDescription("Yazı ekle") and hasAnyAncestor(hasTestTag("tool_row_context"))).fetchSemanticsNodes().isNotEmpty())
        compose.onNode(hasContentDescription("Seç") and hasAnyAncestor(hasTestTag("mode_selector"))).performClick()
        waitFor { vm().mode.value == EditMode.SELECT }
        listOf("Kopyala", "Sil", "Dikdörtgen seçim").forEach { label ->
            waitFor { compose.onAllNodes(hasContentDescription(label, substring = true) and hasAnyAncestor(hasTestTag("tool_row_context"))).fetchSemanticsNodes().isNotEmpty() }
        }
        assertGrouped("mode_selector")
    }

    @Test
    fun notesAndComicKeepSelectThenAdd() {
        openKind("Not sırası", CanvasKind.NOTES)
        waitFor { exists("mode_selector") }
        assertEquals(listOf("Seç", "Çiz", "Ekle", "Düzenle", "Elemanlar", "Katmanlar", "Özellikler"), rowButtons("mode_selector").map(::name))
    }
}

/**
 * v45 (12) bütün üst menülerde dörderli gruplar ve aralarında ayraç; kaydırılan satır seçili düğmeye hizalı kalır,
 * kullanıcı kaydırınca bir sonraki seçime kadar hizalanmaz.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V45ToolGroupsTest : V45EditorBase() {
    @Test
    fun advancedMenusGroupedByFour() {
        openKind("Gruplar", CanvasKind.COMIC)
        waitFor { exists("tool_row_draw") }
        assertTrue(assertGrouped("tool_row_draw") >= 9)
        assertGrouped("mode_selector")
        for (m in listOf(EditMode.SELECT, EditMode.ADD, EditMode.EDIT_PICK, EditMode.MOVE)) {
            compose.runOnIdle { vm().chooseMode(m) }
            waitFor { exists("tool_row_context") && vm().mode.value == m }
            val n = assertGrouped("tool_row_context")
            assertTrue("$m: $n düğme", n >= 5)
        }
        shot("v45-groups-comic-add")
    }

    @Test
    fun simpleMenusGroupedByFour() {
        for (kind in listOf(CanvasKind.SIMPLE_ART, CanvasKind.SIMPLE_NOTES, CanvasKind.KIDS)) {
            if (kind != CanvasKind.SIMPLE_ART) {
                compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
                waitFor { !exists("drawing_canvas") }
                runBlocking { repo().createCanvas("G $kind", 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind) }
                waitForText("G $kind")
                compose.onAllNodes(hasText("G $kind"))[0].performClick()
                waitFor { exists("drawing_canvas") && vm().canvas.value?.kind == kind }
            } else openKind("G $kind", kind)
            waitFor { exists("simple_tool_row") }
            // Çocuk menüsünde geri al/yinele kaydırılan satırın dışında ayrı bir grup; kaydırılan araçlar dörderli
            // v47 dikeyde iki satır: her satır kendi içinde dörderli
            val n = if (kind == CanvasKind.KIDS) assertGrouped("kids_tool_scroll") + assertGrouped("kids_tool_scroll_2") else assertGrouped("simple_tool_row")
            assertTrue("$kind: $n", n >= 10)
        }
    }

    @Test
    fun offscreenSelectionScrollsIntoViewUserScrollIsRespected() {
        openKind("Hizalama", CanvasKind.COMIC)
        compose.runOnIdle { vm().chooseMode(EditMode.ADD) }
        waitFor { exists("tool_row_context") }
        val row = "tool_row_context"
        // Ekle satırı ekrana sığmaz: son düğme (Resim ekle) başta görünmez
        assertFalse(fullyVisible(button(row, "Resim ekle")))
        // Program son öğeyi seçince satır kayar, düğme görünür
        compose.runOnIdle { vm().rememberAddItem("image") }
        waitFor { fullyVisible(button(row, "Resim ekle")) }
        assertTrue(button(row, "Resim ekle").config.getOrNull(SemanticsProperties.Selected) == true)
        // Yeniden çizim (seçim aynı) satırı oynatmaz
        val imageLeft = rectOf(button(row, "Resim ekle")).left
        compose.runOnIdle { vm().togglePanel(PanelTab.LAYERS) }
        waitFor { vm().panel.value == PanelTab.LAYERS }
        compose.runOnIdle { vm().closePanel() }
        settleFor(300)
        assertEquals(imageLeft, rectOf(button(row, "Resim ekle")).left, 1f)
        // Kullanıcı satırı başa kaydırır: seçim aynı kaldıkça hizalama geri gelmez
        compose.onNodeWithTag(row).performTouchInput { swipeRight(startX = left + 20f, endX = right - 4f, durationMillis = 300) }
        compose.onNodeWithTag(row).performTouchInput { swipeRight(startX = left + 20f, endX = right - 4f, durationMillis = 300) }
        settleFor(300)
        assertFalse("kullanıcının kaydırması geri alınmamalı", fullyVisible(button(row, "Resim ekle")))
        compose.runOnIdle { vm().togglePanel(PanelTab.LAYERS) }
        settleFor(300)
        compose.runOnIdle { vm().closePanel() }
        settleFor(300)
        assertFalse(fullyVisible(button(row, "Resim ekle")))
        // Yeni seçim: yeniden hizalanır
        compose.runOnIdle { vm().rememberAddItem("model3d") }
        waitFor { fullyVisible(button(row, "3B şekil ekle")) }
        // Mod seçicide de: programla seçilen mod görünür
        compose.runOnIdle { vm().chooseMode(EditMode.EDIT_PICK) }
        waitFor { fullyVisible(button("mode_selector", "Düzenle")) }
        compose.runOnIdle { vm().chooseMode(EditMode.DRAW) }
        waitFor { fullyVisible(button("mode_selector", "Çiz")) }
    }
}

/**
 * v45 (13) seçili elemanın köşesinde saydam yatay menü (Taşı, Ölçekle, Kopyala, Sil, Düzenle) ve her eylemin işi;
 * boş yere dokununca dikey ekleme menüsü; eski bağlam menüleri yok; korunan etkileşimler (5 sn taşıma, arka planın
 * seçilmesi, yazı düzenleme, çizgi roman paneli Düzenle'de).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V45CanvasMenusTest : V45EditorBase() {
    private fun assertNoOldMenus() {
        listOf("canvas_context_menu", "add_here_menu", "bg_transform_bar").forEach { assertFalse("$it kaldırıldı", exists(it)) }
    }

    @Test
    fun selectionMenuHasFiveActionsAtCornerAndEachWorks() {
        openKind("Seçim menüsü", CanvasKind.ART)
        drawLine(Offset(300f, 600f), Offset(700f, 600f))
        selectAt(500f, 600f)
        waitFor { exists("selection_menu") }
        assertNoOldMenus()
        // Beş eylem bu sırayla, küçük adlarıyla
        val actions = SelectionMenuIds.ORDER.map { node("selection_menu_$it") }
        assertEquals(SelectionMenuIds.ORDER, SelectionMenuIds.ORDER.sortedBy { node("selection_menu_$it").boundsInRoot.left })
        listOf("Taşı", "Ölçekle", "Kopyala", "Sil", "Düzenle").forEachIndexed { i, label ->
            assertTrue(label, compose.onAllNodes(hasText(label) and hasAnyAncestor(hasTestTag("selection_menu_${SelectionMenuIds.ORDER[i]}")), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        }
        // Menü ekranda, seçimin üstüne binmiyor ve seçimin sağ üst köşesinde
        val menu = node("selection_menu").boundsInRoot
        val sel = rootRectOf(selectionBox())
        assertTrue("ekranda: $menu", menu.left >= 0f && menu.right <= root().right && menu.top >= 0f)
        assertFalse("seçimin üstüne binmemeli: $menu / $sel", menu.overlaps(sel))
        assertTrue("seçimin üstünde", menu.bottom <= sel.top + 1f)
        // Sağ köşeye hizalı; menü seçimden genişse ekranın sol kenarında (8 dp pay) kalır
        val margin = 8f * compose.density.density
        val areaLeft = compose.onNodeWithTag("drawing_canvas").fetchSemanticsNode().boundsInRoot.left
        if (sel.right - menu.width >= areaLeft + margin) assertEquals("sağ köşede", sel.right, menu.right, 3f * compose.density.density)
        else assertEquals("ekranın içinde", areaLeft + margin, menu.left, 3f * compose.density.density)
        assertTrue(actions.all { it.boundsInRoot.height < 40f * compose.density.density })
        shot("v45-selection-menu")

        // Kopyala: kopya eklenir, seçim kopyada
        val count = canvas().strokes.size
        val original = vm().selectedIds.value
        menuAction(SelectionMenuIds.COPY).performClick()
        waitFor { canvas().strokes.size == count + 1 }
        assertTrue(vm().selectedIds.value.isNotEmpty() && vm().selectedIds.value != original)
        // Sil: seçili kopya silinir
        menuAction(SelectionMenuIds.DELETE).performClick()
        waitFor { canvas().strokes.size == count && vm().selectedIds.value.isEmpty() }
        assertFalse(exists("selection_menu"))

        // Ölçekle: Ölçekle moduna geçer; +%10 genişliği büyütür
        selectAt(500f, 600f)
        waitFor { exists("selection_menu") }
        val before = selectionBox()
        menuAction(SelectionMenuIds.SCALE).performClick()
        waitFor { vm().mode.value == EditMode.SCALE }
        assertFalse("Ölçekle'de menü gizli", exists("selection_menu"))
        compose.onNode(hasText("+10%") and hasAnyAncestor(hasTestTag("mode_bar"))).performClick()
        waitFor { selectionBox().width > before.width * 1.05f }
        assertEquals(before.width * 1.1f, selectionBox().width, before.width * 0.03f)
        // v17 iş bitince seçim kalkar, Seç'e dönülür (menü de kalkar)
        finish()
        waitFor { vm().mode.value == EditMode.SELECT && vm().selectedIds.value.isEmpty() && !exists("selection_menu") }

        // Taşı: Taşı moduna geçer; sürükleme seçimi taşır
        selectAt(500f, 600f)
        waitFor { exists("selection_menu") }
        val start = selectionBox()
        menuAction(SelectionMenuIds.MOVE).performClick()
        waitFor { vm().mode.value == EditMode.MOVE }
        drag(screenOf(500f, 600f), screenOf(600f, 700f) - screenOf(500f, 600f))
        waitFor { selectionBox().left > start.left + 50f }
        assertEquals(start.left + 100f, selectionBox().left, 15f)
        assertEquals(start.top + 100f, selectionBox().top, 15f)
        finish()
        waitFor { vm().mode.value == EditMode.SELECT && vm().selectedIds.value.isEmpty() }
        selectAt(600f, 700f)
        waitFor { exists("selection_menu") }

        // Düzenle: türüne özel işlemler dikey listede (çizim: renk, incelt…, sıra, gizle, seçimi kaldır)
        menuAction(SelectionMenuIds.EDIT).performClick()
        waitFor { exists("selection_menu_edit_list") }
        listOf("Renk", "Bir üste getir", "Gizle", "Seçimi temizle").forEach { label ->
            assertTrue(label, compose.onAllNodes(hasText(label, substring = true, ignoreCase = true) and hasAnyAncestor(hasTestTag("selection_menu_edit_list"))).fetchSemanticsNodes().isNotEmpty())
        }
        // Liste de ekranda
        val list = node("selection_menu_edit_list").boundsInRoot
        assertTrue(list.left >= 0f && list.right <= root().right && list.top >= 0f && list.bottom <= root().bottom)
        // Yeniden dokununca kapanır
        menuAction(SelectionMenuIds.EDIT).performClick()
        waitFor { !exists("selection_menu_edit_list") }
    }

    @Test
    fun editOpensShapeEditorAndTextEditing() {
        openKind("Düzenle", CanvasKind.NOTES)
        val size = canvasSize()
        addSquareViaMenu(Offset(size.x * 0.3f, size.y * 0.5f), Offset(size.x * 0.3f, size.x * 0.3f))
        waitFor { shapes().size == 1 }
        compose.runOnIdle { vm().setMode(EditMode.SELECT) }
        if (vm().selectedIds.value.isEmpty()) {
            tap(Offset(size.x * 0.3f, size.y * 0.5f + size.x * 0.15f))
            waitFor { vm().selectedIds.value.isNotEmpty() }
        }
        waitFor { exists("selection_menu") }
        menuAction(SelectionMenuIds.EDIT).performClick()
        waitFor { exists("selection_menu_edit_list") }
        // Not tuvalinde şekilden ok çıkarma da Düzenle'de (eski bağlam menüsünden)
        assertTrue(compose.onAllNodes(hasText("Bu şekilden ok çıkar") and hasAnyAncestor(hasTestTag("selection_menu_edit_list"))).fetchSemanticsNodes().isNotEmpty())
        compose.onNode(hasText("Şekli düzenle") and hasAnyAncestor(hasTestTag("selection_menu_edit_list"))).performClick()
        waitFor { vm().mode.value == EditMode.SHAPE_EDIT }
        assertFalse(exists("selection_menu"))
        finish()

        // Yazı düzenleme: Düzenle → Metni Düzenle yazıyı tuvalde açar
        compose.runOnIdle { vm().setMode(EditMode.DRAW) }
        addTextAt(screenOf(200f, 150f), "Merhaba")
        waitFor { texts().size == 1 }
        compose.runOnIdle { vm().setMode(EditMode.SELECT) }
        compose.waitForIdle()
        if (vm().selectedIds.value != setOf(texts().single().id)) {
            tap(screenOf(texts().single().points.first().x + 10f, texts().single().points.first().y + 10f))
            waitFor { vm().selectedIds.value.isNotEmpty() }
        }
        waitFor { exists("selection_menu") }
        menuAction(SelectionMenuIds.EDIT).performClick()
        waitFor { exists("selection_menu_edit_list") }
        compose.onNode(hasText("Metni Düzenle") and hasAnyAncestor(hasTestTag("selection_menu_edit_list"))).performClick()
        waitFor { exists("inline_text_editor") }
    }

    @Test
    fun comicPanelActionsThroughEdit() {
        openKind("Panel", CanvasKind.COMIC)
        compose.runOnIdle { vm().insertTemplate("four_grid") }
        waitFor { canvas().strokes.count { it.shape?.panel != null } == 4 }
        val panel = canvas().strokes.first { it.shape?.panel != null }
        val b = ElementOps.selectionBounds(canvas().strokes, setOf(panel.id))!!
        compose.runOnIdle { vm().chooseMode(EditMode.SELECT); vm().selectOnlyAt(b.left, (b.top + b.bottom) / 2f, 8f) }
        waitFor { vm().selectedIds.value.size == 1 && exists("selection_menu") }
        assertTrue(canvas().strokes.first { it.id in vm().selectedIds.value }.shape?.panel != null)
        menuAction(SelectionMenuIds.EDIT).performClick()
        waitFor { exists("selection_menu_edit_list") }
        // Paneli düzenle listenin başında
        val items = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick) and hasAnyAncestor(hasTestTag("selection_menu_edit_list"))).fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
        assertTrue(items.first().config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty().contains("Paneli düzenle"))
        compose.onNode(hasText("Paneli düzenle") and hasAnyAncestor(hasTestTag("selection_menu_edit_list"))).performClick()
        waitFor { exists("templates_sheet") }
    }

    @Test
    fun emptyTapOpensNoMenuAndOldMenusAreGone() {
        // v47 boş yere dokunmanın dikey ekleme menüsü kaldırıldı: tuvalde yalnız saydam seçim menüsü
        openKind("Ekleme menüsü", CanvasKind.ART)
        compose.runOnIdle { vm().setMode(EditMode.SELECT) }
        tap(screenOf(300f, 300f))
        compose.waitForIdle()
        assertFalse(exists("canvas_add_menu"))
        assertNoOldMenus()
        // Ekle modunda dokunma: menü yok; son seçilen eklenecek yoksa tuvale bir şey eklenmez
        compose.runOnIdle { vm().setMode(EditMode.ADD) }
        val strokes = canvas().strokes.size
        tap(screenOf(600f, 500f))
        compose.waitForIdle()
        assertFalse(exists("canvas_add_menu"))
        assertEquals(strokes, canvas().strokes.size)
        // İki parmakla boş yere basma: menü açılmaz
        compose.runOnIdle { vm().setMode(EditMode.DRAW) }
        twoFingerHold(screenOf(500f, 800f))
        compose.waitForIdle()
        assertFalse(exists("canvas_add_menu"))
        assertNoOldMenus()
        // İki parmakla elemana basma: seçilir, Seç'e geçilir, köşesinde seçim menüsü
        drawLine(Offset(300f, 600f), Offset(700f, 600f))
        compose.runOnIdle { vm().setMode(EditMode.DRAW) }
        twoFingerHold(screenOf(500f, 600f))
        waitFor { vm().mode.value == EditMode.SELECT && vm().selectedIds.value.isNotEmpty() && exists("selection_menu") }
        assertNoOldMenus()
        // Seçim varken boş yere dokunmak önce seçimi kaldırır (menü açılmaz)
        tap(screenOf(150f, 900f))
        waitFor { vm().selectedIds.value.isEmpty() && !exists("selection_menu") }
        assertFalse(exists("canvas_add_menu"))
    }
    @Test
    fun holdToMoveAndBackgroundSelectionStillWork() {
        openWithBackground("Arka plan menüsü", CanvasKind.ART, BackgroundSpec(patternId = "art.polka"))
        drawLine(Offset(300f, 600f), Offset(700f, 600f))
        // v40 5 sn basılı tutma: eleman seçilir, Taşı'ya geçilir
        compose.runOnIdle { vm().setMode(EditMode.SELECT); vm().clearSelection() }
        longPress(screenOf(500f, 600f), millis = 5300)
        waitFor { vm().mode.value == EditMode.MOVE && vm().selectedIds.value.isNotEmpty() }
        finish()
        compose.runOnIdle { vm().setMode(EditMode.SELECT); vm().clearSelection() }
        compose.waitForIdle()
        // v41 boş yere dokunma arka planı eleman gibi seçer: aynı yatay menü
        tap(screenOf(150f, 150f))
        waitFor { exists("bg_selection_menu") }
        assertFalse(exists("canvas_add_menu"))
        assertNoOldMenus()
        assertEquals(SelectionMenuIds.ORDER, SelectionMenuIds.ORDER.sortedBy { node("bg_selection_menu_$it").boundsInRoot.left })
        // Desen (resim yok): Taşı ve Ölçekle pasif; Kopyala (bütün sayfalara) tek sayfada pasif
        listOf(SelectionMenuIds.MOVE, SelectionMenuIds.SCALE, SelectionMenuIds.COPY).forEach {
            assertTrue(it, node("bg_selection_menu_$it").config.contains(SemanticsProperties.Disabled))
        }
        compose.onNodeWithTag("bg_selection_menu_edit").performClick()
        waitFor { exists("bg_change") && exists("bg_selected_opacity") && exists("bg_transform_done") }
        assertFalse(exists("bg_transform_reset"))
        compose.onNodeWithTag("bg_selection_menu_delete").performClick()
        waitFor { vm().canvas.value!!.background.isEmpty && !exists("bg_transform_overlay") }
    }
}

/** v45 sağdan sola dilde menüler aynalanır (seçimin sol üst köşesi, eylemler sağdan sola), ekranda kalır. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "ar-w411dp-h891dp-xxhdpi")
class V45RtlMenuTest {
    @get:Rule val compose = createComposeRule()

    @org.junit.After fun tearDown() = org.koin.core.context.stopKoin()

    @Test
    fun selectionAndAddMenusMirrorInRtl() {
        val anchor = Rect(300f, 700f, 700f, 900f)
        compose.setContent {
            DrawGuidanceTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.size(400.dp, 700.dp)) {
                        SelectionMenu(
                            anchor = { anchor },
                            actions = selectionMenuActions(onMove = {}, onScale = {}, onCopy = {}, onDelete = {}),
                            editItems = listOf(ModeAction("Bir", null, {})),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val menu = compose.onNodeWithTag("selection_menu").fetchSemanticsNode().boundsInRoot
        // Seçimin sol üst köşesi, seçimin üstünde
        assertEquals(anchor.left, menu.left, 1f)
        assertTrue(menu.bottom <= anchor.top)
        // Eylemler sağdan sola: Taşı en sağda, Düzenle en solda
        val move = compose.onNodeWithTag("selection_menu_move").fetchSemanticsNode().boundsInRoot
        val edit = compose.onNodeWithTag("selection_menu_edit").fetchSemanticsNode().boundsInRoot
        assertTrue(move.left > edit.left)
    }
}

/** v45 (13.1) tablette (dikey) pencereler ve üstteki iki menü sürüklenir, ekranda kalır, sıfırlanır, konum saklanır. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w800dp-h1280dp-xhdpi")
class V45TabletPortraitWindowsTest : V45EditorBase() {
    @Test
    fun topMenusSidePanelAndSheetsDragWithinBounds() {
        openKind("Tablet", CanvasKind.ART)
        waitFor { exists("tool_row_draw") && exists("mode_selector") }
        assertTrue(TabletWindows.isTablet(compose.activity.resources.configuration))
        assertTrue(windowHandles().containsAll(listOf("window_drag:top_menu", "window_drag:mode_menu")))
        // Dikey tablette de gelişmiş modda adlar düğmenin içinde
        assertTrue(compose.onAllNodes(hasTestTag(INLINE_LABEL_PREFIX + "Silgi"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())

        // Araç satırı: sürüklenir
        val home = rectOf(node("tool_row_draw"))
        val slop = 40f
        dragHandle("window_drag:top_menu", Offset(-150f, 400f))
        val moved = rectOf(node("tool_row_draw"))
        assertEquals(home.left - 150f, moved.left, slop)
        assertEquals(home.top + 400f, moved.top, slop)
        val store = WindowPositionStore(compose.activity)
        assertTrue("konum saklanır", store.load(TabletWindows.TOP_MENU) != Offset.Zero)
        // Ekranın dışına çıkamaz
        dragHandle("window_drag:top_menu", Offset(-6000f, -6000f))
        val clamped = rectOf(node("tool_row_draw"))
        assertTrue("sol üstte sınır: $clamped", clamped.left >= -12f * compose.density.density && clamped.top >= -12f * compose.density.density)
        dragHandle("window_drag:top_menu", Offset(9000f, 9000f))
        val far = rectOf(node("tool_row_draw"))
        assertTrue("sağ altta sınır: $far", far.right <= root().right + 12f * compose.density.density && far.bottom <= root().bottom + 12f * compose.density.density)
        // Sıfırla: asıl yerine döner, kayıt silinir
        compose.onNodeWithTag("window_reset:top_menu").performClick()
        waitFor { abs(rectOf(node("tool_row_draw")).left - home.left) < 1f && abs(rectOf(node("tool_row_draw")).top - home.top) < 1f }
        assertEquals(Offset.Zero, store.load(TabletWindows.TOP_MENU))
        assertFalse(exists("window_reset:top_menu"))

        // Mod rayı
        val rail = rectOf(node("mode_selector"))
        dragHandle("window_drag:mode_menu", Offset(300f, -200f))
        val railMoved = rectOf(node("mode_selector"))
        assertEquals(rail.left + 300f, railMoved.left, slop)
        assertEquals(rail.top - 200f, railMoved.top, slop)

        // Yan panel: serbestçe sürüklenir, ekranın içinde kalır
        compose.runOnIdle { vm().togglePanel(PanelTab.ELEMENTS) }
        waitFor { exists("side_panel_drag") }
        compose.waitForIdle()
        val panelTag = if (exists("side_panel_right")) "side_panel_right" else "side_panel_left"
        val panel = rectOf(node(panelTag))
        dragHandle("side_panel_drag", Offset(-400f, 0f))
        val panelMoved = rectOf(node(panelTag))
        assertEquals(panel.left - 400f, panelMoved.left, slop)
        dragHandle("side_panel_drag", Offset(9000f, 0f))
        assertTrue(rectOf(node(panelTag)).right <= root().right + 1f)
        // Sıfırla düğmesi yalnız panel yerinden oynamışken
        dragHandle("side_panel_drag", Offset(-300f, 0f))
        waitFor { exists("window_reset:side_panel") }
        compose.onNodeWithTag("window_reset:side_panel").performClick()
        waitFor { abs(rectOf(node(panelTag)).left - panel.left) < 1f }
        compose.runOnIdle { vm().closePanel() }

        // Açılan pencere (Arka plan): sürüklenebilir pencere
        clickIn("tool_row_draw", "Arka plan")
        waitFor { exists("floating_sheet") && exists("background_sheet") }
        val sheet = rectOf(node("floating_sheet"))
        dragHandle("window_drag:sheet", Offset(-100f, -300f))
        val sheetMoved = rectOf(node("floating_sheet"))
        assertEquals(sheet.top - 300f, sheetMoved.top, slop)
        assertEquals(sheet.left - 100f, sheetMoved.left, slop)
        dragHandle("window_drag:sheet", Offset(0f, -9000f))
        assertTrue(rectOf(node("floating_sheet")).top >= -1f)
        shot("v45-tablet-portrait")
    }
}

/** v45 (13.1) yatay tablette de üst menü ve mod rayı sürüklenir; ekranda kalır. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w1280dp-h800dp-land-xhdpi")
class V45TabletLandscapeWindowsTest : V45EditorBase() {
    @Test
    fun menusDragInLandscape() {
        openKind("Yatay tablet", CanvasKind.COMIC)
        waitFor { exists("tool_row_draw") && exists("mode_selector") }
        assertTrue(windowHandles().containsAll(listOf("window_drag:top_menu", "window_drag:mode_menu")))
        val rail = rectOf(node("mode_selector"))
        dragHandle("window_drag:mode_menu", Offset(500f, 100f))
        val moved = rectOf(node("mode_selector"))
        assertEquals(rail.left + 500f, moved.left, 40f)
        dragHandle("window_drag:mode_menu", Offset(-9000f, 9000f))
        val clamped = rectOf(node("mode_selector"))
        assertTrue(clamped.left >= -1f && clamped.bottom <= root().bottom + 30f * compose.density.density)
        val row = rectOf(node("tool_row_draw"))
        dragHandle("window_drag:top_menu", Offset(-300f, 250f))
        assertEquals(row.top + 250f, rectOf(node("tool_row_draw")).top, 40f)
        // Gruplar tablette de
        assertGrouped("tool_row_draw")
        assertGrouped("mode_selector", vertical = true)
    }
}

/** v45 (13.1) telefonlarda yerleşim değişmez: tutamak yok, pencereler alt sayfa, yan panel kenara yerleşir. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V45PhoneLayoutTest : V45EditorBase() {
    @Test
    fun phonePortraitKeepsDockedLayout() {
        openKind("Telefon", CanvasKind.ART)
        waitFor { exists("tool_row_draw") }
        assertFalse(TabletWindows.isTablet(compose.activity.resources.configuration))
        assertTrue(windowHandles().isEmpty())
        clickIn("tool_row_draw", "Arka plan")
        waitFor { exists("background_sheet") }
        assertFalse("telefonda alt sayfa", exists("floating_sheet"))
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w891dp-h411dp-land-xhdpi")
class V45PhoneLandscapeLayoutTest : V45EditorBase() {
    @Test
    fun phoneLandscapeKeepsV43PanelDocking() {
        openKind("Yatay telefon", CanvasKind.ART)
        waitFor { exists("tool_row_draw") }
        assertTrue(windowHandles().isEmpty())
        // v38 yatayda adlar düğmenin altında (düğmenin içinde değil)
        assertTrue(compose.onAllNodes(hasTestTag(INLINE_LABEL_PREFIX + "Silgi"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(labelled().contains("Silgi"))
        // v47 yatayda açılır alan hep sağda; Elemanlar, Katmanlar, Özellikler alanın soluna yapışık rayda (sürüklenmez)
        compose.runOnIdle { vm().togglePanel(PanelTab.ELEMENTS) }
        waitFor { exists("side_panel_right") }
        assertFalse(exists("side_panel_drag"))
        assertFalse(exists("window_reset:side_panel"))
        waitFor {
            val rail = node("panel_rail").boundsInRoot
            val panel = node("side_panel_right").boundsInRoot
            kotlin.math.abs(rail.right - panel.left) < 12f * compose.density.density
        }
    }
}

/**
 * v45 ortak işler: seçim menüsünün Düzenle'si düzenleyicinin bilinen Düzenle yolunu açar (bilim şekillerinde Değerler
 * ve "Vektör düzenle"); not alma modlarına eklenen "Panel ekle" düğmesi dörderli gruplara kendiliğinden girer.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V45SharedFeaturesTest : V45EditorBase() {
    private fun openWithStrokes(name: String, kind: CanvasKind, strokes: List<com.karapp.drawguidance.data.model.Stroke>) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking {
            val c = repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind)
            repo().saveStrokes(c.localId, listOf(strokes))
        }
        waitForText(name)
        compose.onAllNodes(hasText(name))[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name && canvas().strokes.size == strokes.size }
    }

    @Test
    fun selectionMenuEditReachesScienceParamsAndVectorEdit() {
        val style = com.karapp.drawguidance.data.model.ShapeStyle(strokeColorHex = "#263238", strokeWidth = 3f)
        val shape = com.karapp.drawguidance.ui.drawing.ShapeFactory.build(
            com.karapp.drawguidance.content.library.ShapeLibrary.PREFIX + "sci.phys.projectile",
            Offset(150f, 300f), Offset(850f, 800f), style, id = "sci", order = 0,
        )!!
        openWithStrokes("Bilim seçimi", CanvasKind.NOTES, listOf(shape))
        // Seç'te şekle dokununca köşesinde seçim menüsü
        val p = canvas().strokes.single().points.minByOrNull { abs(it.x - 500f) + abs(it.y - 550f) }!!
        selectAt(p.x, p.y)
        waitFor { vm().selectedIds.value == setOf("sci") && exists("selection_menu") }
        // Düzenle → listenin başındaki "Şekli düzenle" (eski Düzenle yolu: viewModel.editSelectedShape)
        menuAction(SelectionMenuIds.EDIT).performClick()
        waitFor { exists("selection_menu_edit_list") }
        val items = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick) and hasAnyAncestor(hasTestTag("selection_menu_edit_list")))
            .fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }
        assertTrue(items.first().config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty().contains("Şekli düzenle"))
        compose.onNode(hasText("Şekli düzenle") and hasAnyAncestor(hasTestTag("selection_menu_edit_list"))).performClick()
        // Bilim şekli değer/formül düzenleyicisiyle (Değerler aracı) açılır; seçim menüsü kalkar
        waitFor { vm().mode.value == EditMode.SHAPE_EDIT && compose.onAllNodesWithTag("param_controls", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.PARAMS, vm().shapeTool.value)
        assertFalse(exists("selection_menu"))
        // "Vektör düzenle" nokta düzenlemeye geçer
        compose.onNodeWithTag("param_vector_edit", useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { vm().shapeTool.value == com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.NODES }
    }

    @Test
    fun doubleTapOnEmptySpaceFitsPaper() {
        openKind("Çift dokunma", CanvasKind.ART)
        drawLine(Offset(300f, 600f), Offset(700f, 600f))
        compose.runOnIdle { vm().setMode(EditMode.SELECT); vm().clearSelection() }
        compose.waitForIdle()
        val line = screenOf(500f, 600f)
        // Seç'te boş yerde sürükleme kağıdı kaydırır: çizgi artık eski yerinde değil
        drag(screenOf(150f, 150f), Offset(260f, 340f))
        compose.waitForIdle()
        // Çift dokunma kağıdı sığdırır (v47 ekleme menüsü açılmaz)
        val p = screenOf(150f, 950f)
        compose.onNodeWithTag("drawing_canvas").performTouchInput { click(p) }
        compose.mainClock.advanceTimeBy(48)
        assertFalse(exists("canvas_add_menu"))
        compose.onNodeWithTag("drawing_canvas").performTouchInput { click(p) }
        compose.waitForIdle()
        assertEquals("eklenen bir şey yok", 1, canvas().strokes.size)
        // Kağıt sığdı: çizginin ilk yerine dokunmak onu seçer
        tap(line)
        waitFor { vm().selectedIds.value.isNotEmpty() && exists("selection_menu") }
    }
    @Test
    fun notesPanelButtonFallsIntoGroupsOfFour() {
        openKind("Panel grupları", CanvasKind.NOTES)
        compose.runOnIdle { vm().chooseMode(EditMode.ADD) }
        waitFor { exists("tool_row_context") }
        val names = rowButtons("tool_row_context").map(::name)
        // Panel ekle not alma eklemelerinin (yazı, diyagram, formül, şekil) ardında; satır yine dörderli gruplu (ayraçlar
        // yeni düğmeyi de sayar: düğme ikinci grupta, ayracın sağında)
        val panel = names.indexOf("Panel ekle")
        assertTrue("$names", panel > names.indexOf("Şekil ekle") && names.indexOf("Şekil ekle") > names.indexOf("Diyagram ekle"))
        // v46 menü bölünebilir: Panel ekle'nin bulunduğu parçada sırası 4 ve sonrasıysa ayracın ardında
        val part = rowParts("tool_row_context").first { p -> rowButtons(p).any { name(it) == "Panel ekle" } }
        val inPart = rowButtons(part).map(::name).indexOf("Panel ekle")
        if (inPart >= 4) {
            val firstDivider = rectOf(dividers(part).first())
            assertTrue("Panel ekle ayracın ardında: $part ${rowButtons(part).map { name(it) to rectOf(it).left }} ayraçlar ${dividers(part).map { rectOf(it).left }}",
                rectOf(button(part, "Panel ekle")).left >= firstDivider.right - 0.5f)
        }
        val n = assertGrouped("tool_row_context")
        assertEquals(names.size, n)
        // v46 bölünmüş menüde ayraçlar parça başına
        assertEquals(rowParts("tool_row_context").sumOf { ToolGroups.dividerCount(rowButtons(it).size) }, dividers("tool_row_context").size)
        // Basit not menüsünde de: gruplar dörderli (Ekle modunda geri tuşu önce modu bitirir: Çiz'den çıkılır)
        compose.runOnIdle { vm().finishAction(); vm().setMode(EditMode.DRAW) }
        compose.waitForIdle()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor { !exists("drawing_canvas") }
        runBlocking { repo().createCanvas("Basit panel", 1000, 1000, "#FFFFFF", aiEnabled = false, kind = CanvasKind.SIMPLE_NOTES) }
        waitForText("Basit panel")
        compose.onAllNodes(hasText("Basit panel"))[0].performClick()
        waitFor { exists("simple_tool_row") && vm().canvas.value?.kind == CanvasKind.SIMPLE_NOTES }
        val simple = rowButtons("simple_tool_row").map(::name)
        assertTrue("$simple", "Panel ekle" in simple)
        assertGrouped("simple_tool_row")
    }
}
