package com.karapp.drawguidance

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.formula.Formula
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.PageAlign
import com.karapp.drawguidance.data.model.PageNumberFormat
import com.karapp.drawguidance.data.model.PageSettings
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool
import com.karapp.drawguidance.ui.screens.canvasedit.components.ToolDescriptions
import com.karapp.drawguidance.ui.screens.canvasedit.state.ComicCover
import com.karapp.drawguidance.ui.screens.canvasedit.state.NotesPanels
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** v45 arayüz testlerinin ortak yardımcıları (tuvali depoda oluşturup açar, düğmeler, resim karşılaştırma). */
abstract class V45NotesComicUiBase : V33EditorBase() {
    protected fun repo() = GlobalContext.get().get<CanvasRepository>()
    protected fun str(id: Int, vararg args: Any): String = compose.activity.getString(id, *args)
    protected fun has(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Listede yeni bir tuval oluşturup açar ([block] ile içerik/ayar yazılabilir). */
    protected fun open(name: String, kind: CanvasKind, w: Int = 1000, h: Int = 1400, block: suspend (CanvasUi) -> Unit = {}) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking {
            val c = repo().createCanvas(name, w, h, "#FFFFFF", aiEnabled = false, kind = kind)
            block(c)
        }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    /** Düzenleyiciden listeye dönüp tuvali yeniden açar (kayıttan yüklenir). */
    protected fun reopen(name: String) {
        val before = vm()
        compose.runOnIdle { before.setMode(EditMode.DRAW); before.saveNow() }
        compose.onAllNodes(hasContentDescription("Geri"))[0].performClick()
        waitFor { !exists("drawing_canvas") }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm() !== before && vm().canvas.value?.name == name }
    }

    /** [row] satırındaki düğmelerin erişilebilirlik adları (sırasıyla). */
    protected fun buttons(row: String): List<String> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription) and hasAnyAncestor(hasTestTag(row)))
            .fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }

    protected fun strokes(): List<Stroke> = vm().canvas.value?.strokes.orEmpty()

    /** Düğümün ekrandaki görüntüsü (pencere kökü çizilip düğümün sınırlarıyla kırpılır). */
    protected fun bitmap(tag: String): Bitmap {
        repeat(3) { compose.waitForIdle(); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }
        val root = compose.activity.window.decorView
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { root.draw(android.graphics.Canvas(full)) }
        val b = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        val l = b.left.toInt().coerceIn(0, full.width - 1)
        val t = b.top.toInt().coerceIn(0, full.height - 1)
        return Bitmap.createBitmap(full, l, t, (b.width.toInt()).coerceIn(1, full.width - l), (b.height.toInt()).coerceIn(1, full.height - t))
    }

    /** İki resmin farklı piksel sayısı (boyutları aynı olmalı). */
    protected fun diff(a: Bitmap, b: Bitmap): Int {
        assertEquals(a.width, b.width); assertEquals(a.height, b.height)
        var n = 0
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getPixel(x, y) != b.getPixel(x, y)) n++
        return n
    }

    protected fun contentDescription(tag: String): String? = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()

    protected fun shownText(tag: String): String = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: ""

    /** Seçili elemanın düzenleme düğmesi (Seç modunun alt çubuğunda; ör. "Şekli düzenle", "Formülü düzenle"). */
    protected fun selectionButton(label: String) {
        val node = hasText(label) and hasAnyAncestor(hasTestTag("mode_bar"))
        waitFor { compose.onAllNodes(node).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(node)[0].performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** Alt çubuktaki eylem düğmesi (Taşı, Sil…; erişilebilirlik adıyla). */
    protected fun barAction(label: String) {
        val node = hasContentDescription(label) and hasAnyAncestor(hasTestTag("mode_bar"))
        waitFor { compose.onAllNodes(node).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(node)[0].performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** Şekil seçicide arayıp [title] adlı şekle dokunur. */
    protected fun pickShape(title: String) {
        waitFor { exists("shape_picker") }
        compose.onNode(hasTestTag("shape_search")).performClick()
        compose.onNode(hasTestTag("shape_search")).performTextInput(title)
        // Arama kutusu da aynı yazıyı taşır: yalnız seçilebilir kutular
        val tile = hasText(title) and hasAnyAncestor(hasTestTag("shape_grid")) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        waitFor { compose.onAllNodes(tile).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(tile)[0].performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { !exists("shape_picker") }
    }

    /** Formül penceresine [value] yazıp Tamam'a basar (grafik formülü, parametreli şeklin formülü). */
    protected fun typeInSheet(chip: String, sheet: String, value: String) {
        compose.onNodeWithTag(chip).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { has(sheet) }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(sheet))).performTextReplacement(value)
        compose.onNode(hasText(str(R.string.ok_button)) and hasAnyAncestor(hasTestTag(sheet))).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { !has(sheet) }
    }

    /** Elemanın kutusu (tuval birimi). */
    protected fun box(s: Stroke) = floatArrayOf(s.points.minOf { it.x }, s.points.minOf { it.y }, s.points.maxOf { it.x }, s.points.maxOf { it.y })
}

/**
 * v45 madde 10 — not alma panelleri arayüzde: "Panel ekle" iki not modunda (gelişmiş Ekle satırı, basit üst menü)
 * kendi adı, açıklaması ve benzersiz simgesiyle; yerleşim penceresi; eklenen alanlar seçilir, taşınır, silinir ve
 * kayıtla birlikte saklanıp yeniden açılınca aynı yerde olur.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V45NotesPanelsUiTest : V45NotesComicUiBase() {

    private fun panels(): List<Stroke> = strokes().filter(NotesPanels::isNotesPanel)
    private fun savedPanels(): List<Stroke> = canvas().strokes.filter(NotesPanels::isNotesPanel)

    /** Yerleşim penceresi: bütün yerleşimler kendi (çevrilmiş) adıyla; sonra [layout] seçilir. */
    private fun pickLayout(layout: String) {
        waitFor { exists("notes_panel_sheet") }
        assertEquals(str(R.string.v45_panels_title), compose.onAllNodesWithText(str(R.string.v45_panels_title)).fetchSemanticsNodes().first()
            .config.getOrNull(SemanticsProperties.Text)!!.joinToString("") { it.text })
        NotesPanels.layouts.forEach { l ->
            assertEquals(l.id, str(l.title), contentDescription("notes_panel_layout_${l.id}"))
        }
        compose.onNodeWithTag("notes_panel_layout_$layout").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { !exists("notes_panel_sheet") }
    }

    /** Eklenen alanların kutuları yerleşimin geometrisiyle birebir aynı (okuma sırasıyla). */
    private fun assertGeometry(layout: String, list: List<Stroke>, w: Float, h: Float) {
        val expected = NotesPanels.byId(layout)!!.rects(w, h)
        assertEquals(expected.size, list.size)
        list.sortedBy { it.order }.zip(expected).forEach { (s, r) ->
            val b = box(s)
            for (k in 0..3) assertEquals("$layout ${s.id} kenar $k", r[k], b[k], 0.6f)
        }
    }

    @Test
    fun advancedNotesAddRowHasPanelButtonWithLabelHelpAndSheet() {
        open("Panel düğmesi", CanvasKind.NOTES)
        mode("Ekle")
        val label = str(R.string.v45_panels_add)
        assertEquals("Panel ekle", label)
        val node = hasContentDescription(label) and hasAnyAncestor(hasTestTag("tool_row_context"))
        waitFor { compose.onAllNodes(node).fetchSemanticsNodes().size == 1 }
        // Açıklaması (2 sn basılı tutunca) Araç açıklamaları tablosunda
        assertEquals(R.string.v45_help_panels_add, ToolDescriptions.pairs.toMap()[R.string.v45_panels_add])
        // Yazı, diyagram ve formül menünün başında; Panel ekle diyagramdan sonra, şablonlardan önce sırada
        val add = buttons("tool_row_context")
        assertTrue(add.toString(), add.indexOf(label) > add.indexOf(str(R.string.add_diagram)))
        runCatching { compose.onNode(node).performScrollTo() }
        compose.onNode(node).performClick()
        waitFor { exists("notes_panel_sheet") }
        // Her yerleşimin alan sayısı yazılı
        NotesPanels.layouts.forEach { l ->
            assertTrue(l.id, compose.onAllNodes(hasText(str(R.string.v45_panel_count, l.areaCount)) and hasAnyAncestor(hasTestTag("notes_panel_layout_${l.id}")), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty())
        }
        shotAll("v45-notes-panels-sheet")
        SystemBack.press()
        waitFor { !exists("notes_panel_sheet") }
        assertTrue("pencere kapanınca bir şey eklenmez", panels().isEmpty())
        assertFalse(vm().v45.panelsOpen.value)
    }

    @Test
    fun advancedNotesInsertSelectMoveDeleteAndReload() {
        open("Panel gelişmiş", CanvasKind.NOTES)
        addMenu("Panel ekle")
        pickLayout("cornell")
        waitFor { panels().size == 3 }
        val added = panels()
        // Alanlar yazıların/çizimlerin altında (en alt sıralar), eklenince seçili ve Taşı'da
        assertEquals(listOf(0, 1, 2), added.map { it.order })
        assertEquals(added.map { it.id }.toSet(), vm().selectedIds.value)
        assertEquals(EditMode.MOVE, vm().mode.value)
        assertGeometry("cornell", added, 1000f, 1400f)
        added.forEach { s ->
            assertEquals(NotesPanels.FRAME_COLOR, s.shape!!.style.strokeColorHex)
            assertEquals(NotesPanels.TINT_COLOR, s.shape!!.style.fillColorHex)
            assertEquals(1000f * NotesPanels.CORNER, s.shape!!.style.cornerRadius, 0.01f)
        }
        // Kaydedildi
        waitFor { savedPanels().size == 3 }
        shot("v45-notes-panels-cornell")

        // Seç: özet şeridinin (3. alan) içine dokununca yalnız o seçilir
        compose.runOnIdle { vm().chooseMode(EditMode.SELECT); vm().clearSelection() }
        waitFor { vm().selectedIds.value.isEmpty() }
        val summary = added.single { it.order == 2 }
        val sb = box(summary)
        tap(screenOf((sb[0] + sb[2]) / 2f, (sb[1] + sb[3]) / 2f))
        waitFor { vm().selectedIds.value == setOf(summary.id) }
        // Not alanına (2. alan) dokununca o seçilir
        val keys = added.single { it.order == 1 }
        val kb = box(keys)
        // Seç'te dokunuş seçime ekler (art arda iki dokunuş çift dokunma sayılmasın)
        compose.mainClock.advanceTimeBy(1000)
        tap(screenOf((kb[0] + kb[2]) / 2f, (kb[1] + kb[3]) / 2f))
        waitFor { vm().selectedIds.value == setOf(summary.id, keys.id) }
        // Kenar boşluğuna (alan dışı) dokununca seçim kalkar; sonra yalnız not alanı seçilir
        compose.mainClock.advanceTimeBy(1000)
        tap(screenOf(15f, 15f))
        waitFor { vm().selectedIds.value.isEmpty() }
        compose.mainClock.advanceTimeBy(1000)
        tap(screenOf((kb[0] + kb[2]) / 2f, (kb[1] + kb[3]) / 2f))
        waitFor { vm().selectedIds.value == setOf(keys.id) }

        // Taşı: sürükleyince yalnız seçili alan kayar (öteki iki alan yerinde)
        mode("Taşı")
        waitFor { vm().mode.value == EditMode.MOVE }
        val view = canvasSize()
        val scale = minOf((view.x - 48f * compose.density.density) / 1000f, (view.y - 48f * compose.density.density) / 1400f)
        drag(screenOf((kb[0] + kb[2]) / 2f, (kb[1] + kb[3]) / 2f), Offset(0f, 150f * scale))
        waitFor { abs(box(panels().single { it.id == keys.id })[1] - kb[1]) > 20f }
        val moved = box(panels().single { it.id == keys.id })
        assertEquals("aşağı ~150 birim", kb[1] + 150f, moved[1], 25f)
        assertEquals("yatayda yerinde", kb[0], moved[0], 3f)
        assertEquals("boyutu aynı", kb[3] - kb[1], moved[3] - moved[1], 0.6f)
        panels().filter { it.id != keys.id }.forEach { s ->
            val orig = box(added.single { it.id == s.id })
            box(s).forEachIndexed { k, v -> assertEquals(orig[k], v, 0.01f) }
        }
        waitFor { savedPanels().single { it.id == keys.id }.let { abs(box(it)[1] - moved[1]) < 0.6f } }

        // Sil: seçili alan silinir, ötekiler kalır; geri al geri getirir
        compose.runOnIdle { vm().chooseMode(EditMode.SELECT) }
        waitFor { vm().mode.value == EditMode.SELECT && vm().selectedIds.value == setOf(keys.id) }
        val deleteNode = hasContentDescription(str(R.string.delete_selection)) and hasAnyAncestor(hasTestTag("tool_row_context"))
        waitFor { compose.onAllNodes(deleteNode).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(deleteNode)[0].performScrollTo().performClick()
        waitFor { panels().size == 2 }
        assertTrue(panels().none { it.id == keys.id })
        waitFor { savedPanels().size == 2 }
        compose.runOnIdle { vm().undo() }
        waitFor { panels().size == 3 }
        compose.runOnIdle { vm().redo() }
        waitFor { panels().size == 2 }

        // Kayıt → yeniden aç: iki alan aynı yerde, aynı görünümle
        val before = panels().associateBy { it.id }
        reopen("Panel gelişmiş")
        waitFor { panels().size == 2 }
        panels().forEach { s ->
            val b = before.getValue(s.id)
            assertEquals(NotesPanels.KIND, s.shape!!.kind)
            assertEquals(b.shape!!.style, s.shape!!.style)
            box(b).zip(box(s)).forEach { (x, y) -> assertEquals(x, y, 0.6f) }
            assertEquals(b.order, s.order)
        }
        // Yeniden açılınca da seçilir
        compose.runOnIdle { vm().chooseMode(EditMode.SELECT); vm().clearSelection() }
        compose.mainClock.advanceTimeBy(1000)
        val target = panels().first()
        val tb = box(target)
        tap(screenOf((tb[0] + tb[2]) / 2f, (tb[1] + tb[3]) / 2f))
        waitFor { vm().selectedIds.value == setOf(target.id) }
    }

    @Test
    fun simpleNotesTopMenuHasPanelButtonAndUniqueIcon() {
        open("Panel basit", CanvasKind.SIMPLE_NOTES)
        waitFor { exists("simple_tool_panels") }
        assertEquals("Panel ekle", contentDescription("simple_tool_panels"))
        // Diyagramın ardında (not modunda yazı, diyagram, formül başta)
        val order = compose.onAllNodes(hasTestTag("simple_tool_panels") or hasTestTag("simple_tool_diagram") or hasTestTag("simple_tool_formula"))
            .fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
        assertEquals(listOf("simple_tool_diagram", "simple_tool_formula", "simple_tool_panels"), order)
        // Simge benzersiz: menüdeki öteki her düğmenin simgesinden farklı ve boş değil
        compose.onNodeWithTag("simple_tool_panels").performScrollTo()
        // v48 dikey ekranda düğmeler simge + ad (genişlikleri farklı): yalnız simge karşılaştırılır
        val d = compose.density.density
        fun icon(b: android.graphics.Bitmap) = android.graphics.Bitmap.createBitmap(b, (12f * d).toInt(), ((b.height - 18f * d) / 2f).toInt().coerceAtLeast(0), (18f * d).toInt(), (18f * d).toInt())
        val mine = icon(bitmap("simple_tool_panels"))
        val ids = listOf("shape", "formula", "diagram", "background", "image", "text", "model3d", "paint", "elements", "select", "eraser")
        var compared = 0
        ids.filter { exists("simple_tool_$it") }.forEach { id ->
            compose.onNodeWithTag("simple_tool_$id").performScrollTo()
            val other = icon(bitmap("simple_tool_$id"))
            if (other.width == mine.width && other.height == mine.height) {
                compared++
                assertTrue("simge $id ile aynı", diff(mine, other) > mine.width * mine.height / 50)
            }
        }
        assertTrue("karşılaştırılan düğme: $compared", compared >= 5)
        val center = mine.getPixel(mine.width / 2, mine.height / 2)
        var inked = 0
        for (y in 0 until mine.height) for (x in 0 until mine.width) if (mine.getPixel(x, y) != center) inked++
        assertTrue("simge çizili", inked > 30)
        // Gelişmiş Ekle menüsündeki "Panel ekle" ile aynı simge (iki modda aynı düğme) ayrıca gelişmiş testte

        // Panel ekle → pencere → 2 × 2 kare: dört tam kare alan
        compose.onNodeWithTag("simple_tool_panels").performScrollTo().performClick()
        waitFor { exists("notes_panel_sheet") }
        waitFor { compose.onAllNodes(hasTestTag("simple_tool_panels")).fetchSemanticsNodes().single().config.getOrNull(SemanticsProperties.Selected) == true }
        pickLayout("square_2x2")
        waitFor { panels().size == 4 }
        assertGeometry("square_2x2", panels(), 1000f, 1400f)
        panels().forEach { s -> box(s).let { b -> assertEquals(b[2] - b[0], b[3] - b[1], 0.6f) } }
        shot("v45-simple-notes-panels")
    }

    @Test
    fun simpleNotesSelectMoveDeleteAndReload() {
        open("Panel basit taşı", CanvasKind.SIMPLE_NOTES)
        compose.onNodeWithTag("simple_tool_panels").performScrollTo().performClick()
        pickLayout("two_rows")
        waitFor { panels().size == 2 }
        assertGeometry("two_rows", panels(), 1000f, 1400f)
        val (top, bottom) = panels().sortedBy { it.order }
        // Basit Seç ile alt alana dokun: yalnız o seçili
        compose.onNodeWithTag("simple_tool_select").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.SELECT }
        compose.runOnIdle { vm().clearSelection() }
        val bb = box(bottom)
        tap(screenOf((bb[0] + bb[2]) / 2f, (bb[1] + bb[3]) / 2f))
        waitFor { vm().selectedIds.value == setOf(bottom.id) }
        // Seç çubuğundaki Taşı → sürükle
        barAction(str(R.string.mode_move))
        waitFor { vm().mode.value == EditMode.MOVE }
        val view = canvasSize()
        val scale = minOf((view.x - 48f * compose.density.density) / 1000f, (view.y - 48f * compose.density.density) / 1400f)
        drag(screenOf((bb[0] + bb[2]) / 2f, (bb[1] + bb[3]) / 2f), Offset(-80f * scale, 0f))
        waitFor { abs(box(panels().single { it.id == bottom.id })[0] - bb[0]) > 20f }
        val moved = box(panels().single { it.id == bottom.id })
        assertEquals(bb[0] - 80f, moved[0], 20f)
        assertEquals(bb[1], moved[1], 3f)
        box(panels().single { it.id == top.id }).zip(box(top)).forEach { (a, b) -> assertEquals(b, a, 0.01f) }
        // Seç çubuğundaki Sil
        compose.onNodeWithTag("simple_tool_select").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.SELECT && vm().selectedIds.value == setOf(bottom.id) }
        barAction(str(R.string.delete_selection))
        waitFor { panels().map { it.id } == listOf(top.id) }
        // Kayıt → yeniden aç
        reopen("Panel basit taşı")
        waitFor { panels().size == 1 }
        assertEquals(top.id, panels().single().id)
        box(panels().single()).zip(box(top)).forEach { (a, b) -> assertEquals(b, a, 0.6f) }
        assertEquals(top.shape!!.style, panels().single().shape!!.style)
    }

    @Test
    fun noPanelButtonOutsideNotes() {
        open("Resim", CanvasKind.ART)
        mode("Ekle")
        waitFor { exists("tool_row_context") }
        assertFalse(buttons("tool_row_context").contains(str(R.string.v45_panels_add)))
        assertFalse(exists("simple_tool_panels"))
    }

    @Test
    fun noPanelButtonInSimpleArt() {
        open("Basit resim", CanvasKind.SIMPLE_ART)
        waitFor { exists("simple_tool_shape") }
        assertFalse(exists("simple_tool_panels"))
    }
}

/**
 * v45 madde 4 — basit not alma, gelişmiş not almanın şekil kataloğunun tamamını (grafikler, fen şekilleri) ve
 * formülleri arayüzden ekler ve gelişmiş not almadaki Düzenle girişleriyle düzenler: grafik → Grafik aracı ve formül
 * satırı; parametreli fen şekli → Değerler (formül, adımlayıcı) ve "Vektör düzenle"; formül → formül düzenleme.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V45SimpleNotesShapesUiTest : V45NotesComicUiBase() {

    private fun shapeEls(): List<Stroke> = strokes().filter { it.brushType == BrushType.SHAPE }

    /** Basit üst menüden şekil seçip tuvale sürükleyerek ekler; eklenen şekil döner. */
    private fun addShape(title: String): Stroke {
        compose.onNodeWithTag("simple_tool_shape").performScrollTo().performClick()
        pickShape(title)
        waitFor { vm().mode.value == EditMode.SHAPE }
        val n = shapeEls().size
        val view = canvasSize()
        val scale = minOf((view.x - 48f * compose.density.density) / 1000f, (view.y - 48f * compose.density.density) / 1400f)
        drag(screenOf(150f, 300f), Offset(700f * scale, 500f * scale))
        waitFor { shapeEls().size == n + 1 }
        return shapeEls().last()
    }

    /** Basit Seç → şekle dokun → alt çubukta "Şekli düzenle". */
    private fun openShapeEditor(s: Stroke) {
        compose.onNodeWithTag("simple_tool_select").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.SELECT }
        compose.runOnIdle { vm().clearSelection() }
        val b = box(s)
        val p = s.points.minByOrNull { abs(it.x - (b[0] + b[2]) / 2f) + abs(it.y - (b[1] + b[3]) / 2f) }!!
        tap(screenOf(p.x, p.y))
        waitFor { vm().selectedIds.value == setOf(s.id) }
        selectionButton(str(R.string.shape_edit_open))
        waitFor { vm().mode.value == EditMode.SHAPE_EDIT }
    }

    @Test
    fun graphIsAddedFromSimplePickerAndEditedWithGraphToolAndFormula() {
        open("Basit grafik", CanvasKind.SIMPLE_NOTES)
        val g = addShape("Fonksiyon grafiği")
        assertEquals(ShapeRegistryIds.FUNCTION, g.shape!!.kind)
        assertNotNull("grafik verisiyle", g.shape!!.graph)
        openShapeEditor(g)
        // Gelişmiş not almadaki gibi grafik aracı ve formül satırı
        waitFor { vm().shapeTool.value == ShapeTool.GRAPH }
        waitFor { has("graph_formula") }
        typeInSheet("graph_formula", "graph_formula_sheet", "y = x^2 - 1")
        waitFor { shapeEls().single().shape!!.graph!!.formula == "y = x^2 - 1" }
        assertFalse(has("graph_formula_error"))
        // Hatalı formül: ileti, son geçerli eğri kalır
        val before = shapeEls().single()
        typeInSheet("graph_formula", "graph_formula_sheet", "y = (")
        waitFor { has("graph_formula_error") }
        assertEquals(before, shapeEls().single())
        shot("v45-simple-notes-graph-edit")
        // Kaydedildi
        // v45 kayıt gecikmeli olabilir: eleman kayda geçene kadar beklenir (yoksa koşul hemen istisna atardı)
        waitFor { canvas().strokes.singleOrNull { it.brushType == BrushType.SHAPE }?.shape?.graph?.formula == "y = x^2 - 1" }
    }

    @Test
    fun scienceShapeIsAddedFromSimplePickerAndEditedWithParamsAndVectorEdit() {
        open("Basit fen", CanvasKind.SIMPLE_NOTES)
        val s = addShape("Eğik atış")
        assertEquals("lib.sci.phys.projectile", s.shape!!.kind)
        assertNotNull(com.karapp.drawguidance.ui.drawing.ShapeRegistry.paramModel(s.shape!!.kind))
        openShapeEditor(s)
        // Düzenle → değer/formül düzenleyicisi (gelişmiş not almadaki PARAMS aracı)
        waitFor { vm().shapeTool.value == ShapeTool.PARAMS }
        waitFor { has("param_controls") }
        assertTrue(has("param_step_v") && has("param_step_theta"))
        typeInSheet("param_formula", "param_formula_sheet", "v = 30; θ = 45°")
        waitFor { shapeEls().single().shape!!.params == mapOf("v" to "30", "theta" to "45") }
        assertFalse(has("param_error"))
        shot("v45-simple-notes-param-edit")
        // Vektör düzenle → nokta düzenleme; değerler korunur
        compose.onNodeWithTag("param_vector_edit").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { vm().shapeTool.value == ShapeTool.NODES }
        waitFor { !has("param_controls") }
        assertEquals(mapOf("v" to "30", "theta" to "45"), shapeEls().single().shape!!.params)
        // Kayıtta değerler saklı
        // v45 kayıt gecikmeli olabilir: eleman kayda geçene kadar beklenir (yoksa koşul hemen istisna atardı)
        waitFor { canvas().strokes.singleOrNull { it.brushType == BrushType.SHAPE }?.shape?.params == mapOf("v" to "30", "theta" to "45") }
    }

    @Test
    fun formulaIsAddedFromSimpleMenuAndEdited() {
        open("Basit formül", CanvasKind.SIMPLE_NOTES)
        compose.onNodeWithTag("simple_tool_formula").performScrollTo().performClick()
        waitFor { exists("formula_picker") }
        // Gelişmiş not almadaki bütün kategoriler; "Türev tanımı" seçilir
        val calc = com.karapp.drawguidance.content.formula.FormulaCatalog.categories.first { c -> c.templates.any { it.title == "Türev tanımı" } }
        compose.onAllNodesWithText(calc.title)[0].performScrollTo().performClick()
        waitForText("Türev tanımı")
        compose.onAllNodesWithText("Türev tanımı")[0].performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.FORMULA }
        tap(screenOf(500f, 700f))
        waitFor { strokes().count { it.brushType == BrushType.FORMULA } == 1 }
        val f = strokes().single { it.brushType == BrushType.FORMULA }
        // Seç → formüle dokun → "Formülü düzenle"
        compose.onNodeWithTag("simple_tool_select").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.SELECT }
        compose.runOnIdle { vm().clearSelection() }
        val b = box(f)
        tap(screenOf((b[0] + b[2]) / 2f, (b[1] + b[3]) / 2f))
        waitFor { vm().selectedIds.value == setOf(f.id) }
        selectionButton(str(R.string.formula_edit))
        waitFor { vm().formulaEditing.value != null }
        // Bir boşluğu doldur: formül kaynağı değişir ve kaydedilir
        val slot = Formula.slots(f.formula!!.source).first().name
        compose.runOnIdle { vm().setFormulaSlot(slot, "7") }
        waitFor { strokes().single { it.brushType == BrushType.FORMULA }.formula!!.source != f.formula!!.source }
        val after = strokes().single { it.brushType == BrushType.FORMULA }.formula!!.source
        assertTrue(after, after.contains("7"))
        // v45 kayıt gecikmeli olabilir: eleman kayda geçene kadar beklenir (yoksa koşul hemen istisna atardı)
        waitFor { canvas().strokes.singleOrNull { it.brushType == BrushType.FORMULA }?.formula?.source == after }
    }

    private object ShapeRegistryIds {
        val FUNCTION = com.karapp.drawguidance.ui.drawing.ShapeRegistry.GRAPH_PREFIX + com.karapp.drawguidance.content.graph.GraphKind.FUNCTION.name
    }
}

/**
 * v45 madde 2 — çizgi roman kapağı arayüzde: yeni çizgi roman kapak + bir boş sayfayla açılır, şeritte "Kapak"
 * rozeti ve kapaksız numaralar; kapakta tuval üstü yazı yok; eski çizgi romana "Kapak ekle" (numaraları değişmez,
 * geri alınır, kaydedilir).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V45ComicCoverUiTest : V45NotesComicUiBase() {

    private val settings = PageSettings(
        pageNumbers = true, numberAlign = PageAlign.RIGHT, numberFormat = PageNumberFormat.NUMBER_OF_TOTAL,
        header = "BÖLÜM BİR", footer = "Okul",
    )

    @Test
    fun newComicFromTheFormStartsWithCoverAndOneBlankPage() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNode(hasContentDescription("Yeni Tuval")).performClick()
        waitForText("Yeni Tuval Oluştur")
        compose.onAllNodesWithText("Çizgi roman")[0].performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasSetTextAction()).performTextInput("Uzay Korsanları")
        compose.onNodeWithText("Oluştur").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == "Uzay Korsanları" }
        val c = vm().canvas.value!!
        assertEquals(CanvasKind.COMIC, c.kind)
        assertEquals(2, c.pageCount)
        assertTrue(c.hasCover)
        assertEquals("kapaktan sonraki boş sayfada açılır", 1, c.currentPage)
        assertTrue(c.pages[1].isEmpty())
        // Kapak şablonu: tam sayfa resim alanı, başlık (tuvalin adı), yazar alanı
        val cover = c.pages[0]
        assertEquals(3, cover.size)
        assertEquals(com.karapp.drawguidance.ui.screens.canvasedit.state.PanelSets.KIND, cover[0].shape!!.kind)
        assertEquals("Uzay Korsanları", cover[1].text!!.content)
        assertEquals(ComicCover.subtitlePlaceholder(), cover[2].text!!.content)
        // Şerit: kapak "Kapak" rozetiyle, ilk sayfa 1; konum "1 / 1"
        waitFor { exists("page_chip_cover") && exists("page_chip_1") }
        assertEquals(str(R.string.v45_cover), contentDescription("page_chip_cover"))
        assertEquals(str(R.string.page_number, 1), contentDescription("page_chip_1"))
        assertEquals("1 / 1", shownText("page_position"))
        // Kapağa git: konum "Kapak"
        compose.onNodeWithTag("page_chip_cover").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { vm().canvas.value!!.currentPage == 0 }
        assertEquals("Kapak", shownText("page_position"))
        shot("v45-comic-cover-new")
        // Sayfa eklenince "2 / 2"; kapak bayrağı yeni sayfaya geçmez
        compose.onNodeWithTag("page_chip_1").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { vm().canvas.value!!.currentPage == 1 }
        compose.runOnIdle { vm().addPage() }
        waitFor { vm().canvas.value!!.pageCount == 3 }
        assertEquals("2 / 2", shownText("page_position"))
        assertTrue(vm().canvas.value!!.hasCover)
        assertFalse(vm().canvas.value!!.isCover(2))
        assertEquals(str(R.string.page_number, 2), contentDescription("page_chip_2"))
        // Kayıtta kapak + 2 sayfa
        waitFor { canvas().pageCount == 3 && canvas().hasCover }
        // Kapaklı çizgi romanda "Kapak ekle" yok
        compose.onNodeWithTag("page_chip_1").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.waitForIdle()
        assertFalse(exists("page_add_cover"))
    }

    @Test
    fun editorOverlayLeavesTheCoverUndecoratedAndNumbersFromOne() {
        open("Kapaklı", CanvasKind.COMIC, 800, 1200) { c ->
            repo().saveStrokes(c.localId, listOf(ComicCover.build(800, 1200, "Kapaklı", null), emptyList(), emptyList()))
            repo().saveBackgroundSpec(c.localId, c.background, listOf(com.karapp.drawguidance.data.model.PageBackground(cover = true)))
        }
        assertTrue(vm().canvas.value!!.hasCover)
        assertEquals(3, vm().canvas.value!!.pageCount)
        // Ayarsız ve ayarlı kapak birebir aynı (tuval üstünde numara/üst/alt bilgi yok)
        compose.runOnIdle { vm().goToPage(0) }
        waitFor { vm().canvas.value!!.currentPage == 0 }
        val bare = bitmap("drawing_canvas")
        compose.runOnIdle { vm().v40.setPageSettings(settings) }
        waitFor { vm().canvas.value!!.pageSettings == settings }
        assertEquals("kapak yazısız", 0, diff(bare, bitmap("drawing_canvas")))
        // Sayfa 1 (sıra 1): yazılar çizilir
        compose.runOnIdle { vm().v40.setPageSettings(PageSettings()) }
        compose.runOnIdle { vm().goToPage(1) }
        waitFor { vm().canvas.value!!.currentPage == 1 && vm().canvas.value!!.pageSettings.isEmpty }
        val page1Bare = bitmap("drawing_canvas")
        compose.runOnIdle { vm().v40.setPageSettings(settings) }
        waitFor { vm().canvas.value!!.pageSettings == settings }
        assertTrue("sayfa 1 yazılı", diff(page1Bare, bitmap("drawing_canvas")) > 100)
        // Çizilen satırlar: "1 / 2" (kapak sayılmaz)
        val c = vm().canvas.value!!
        assertEquals(listOf("BÖLÜM BİR", "Okul", "1 / 2"), c.pageSettings.lines(1, c.pageCount, c.hasCover).map { it.text })
        assertEquals("2 / 2", c.pageSettings.lines(2, c.pageCount, c.hasCover).last().text)
        assertEquals("1 / 2", shownText("page_position"))
        // Sayfa ayarları penceresindeki toplam da kapaksız
        waitFor { canvas().pageSettings == settings && canvas().hasCover }
    }

    @Test
    fun oldComicGetsAddCoverWithUnchangedNumbersUndoAndReload() {
        open("Eski roman", CanvasKind.COMIC, 800, 1200) { c ->
            repo().saveStrokes(c.localId, listOf(listOf(V45Fixtures.line("a", 300f)), listOf(V45Fixtures.line("b", 500f))))
            repo().savePageSettings(c.localId, settings)
        }
        // Seçim/gruplama önerisi balonları bu testin konusu değil (kapak üç yakın eleman ekler)
        prefs().setSelectionSuggest(false)
        val old = vm().canvas.value!!
        assertFalse("eski çizgi roman kapaksız", old.hasCover)
        assertEquals(2, old.pageCount)
        // Eski numaralar: 1, 2 (değişmedi)
        waitFor { exists("page_chip_0") && exists("page_chip_1") }
        assertFalse(exists("page_chip_cover"))
        assertEquals(str(R.string.page_number, 1), contentDescription("page_chip_0"))
        assertEquals("1 / 2", shownText("page_position"))
        assertEquals(listOf("BÖLÜM BİR", "Okul", "1 / 2"), old.pageSettings.lines(0, 2, old.hasCover).map { it.text })
        // Sayfa menüsünde "Kapak ekle" (açıklamasıyla)
        compose.onNodeWithTag("page_chip_0").performSemanticsAction(SemanticsActions.OnLongClick)
        waitFor { exists("page_add_cover") }
        assertEquals(str(R.string.v45_cover_add), contentDescription("page_add_cover"))
        assertEquals(R.string.v45_help_cover_add, ToolDescriptions.pairs.toMap()[R.string.v45_cover_add])
        compose.onNodeWithTag("page_add_cover").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { vm().canvas.value!!.pageCount == 3 }
        val c = vm().canvas.value!!
        assertTrue(c.hasCover)
        assertEquals("kapak açılır", 0, c.currentPage)
        assertEquals("Eski roman", c.pages[0][1].text!!.content)
        // Eski sayfalar sırasıyla kapaktan sonra; numaraları aynı (1, 2)
        assertEquals(listOf("a"), c.pages[1].map { it.id })
        assertEquals(listOf("b"), c.pages[2].map { it.id })
        assertEquals(listOf("BÖLÜM BİR", "Okul", "1 / 2"), c.pageSettings.lines(1, 3, true).map { it.text })
        assertEquals(listOf("BÖLÜM BİR", "Okul", "2 / 2"), c.pageSettings.lines(2, 3, true).map { it.text })
        waitFor { exists("page_chip_cover") }
        assertEquals(str(R.string.page_number, 1), contentDescription("page_chip_1"))
        assertEquals(str(R.string.page_number, 2), contentDescription("page_chip_2"))
        assertEquals("Kapak", shownText("page_position"))
        assertFalse("kapak varken seçenek yok", exists("page_add_cover"))
        // Kaydedildi
        waitFor { canvas().hasCover && canvas().pageCount == 3 }
        shot("v45-comic-cover-added")
        // Geri al: kapak sayfası ve bayrağı birlikte gider
        compose.runOnIdle { vm().undo() }
        waitFor { vm().canvas.value!!.pageCount == 2 }
        assertFalse(vm().canvas.value!!.hasCover)
        assertEquals(listOf("a"), vm().canvas.value!!.pages[0].map { it.id })
        compose.runOnIdle { vm().redo() }
        waitFor { vm().canvas.value!!.pageCount == 3 && vm().canvas.value!!.hasCover }
        // Yeniden aç: kapak ve numaralar korunur
        reopen("Eski roman")
        val back = vm().canvas.value!!
        assertTrue(back.hasCover)
        assertEquals(3, back.pageCount)
        assertEquals(settings, back.pageSettings)
        assertEquals(listOf("a"), back.pages[1].map { it.id })
        waitFor { exists("page_chip_cover") }
        // Kapak silinince bayrak da gider; eski numaralandırmaya dönülür
        compose.runOnIdle { vm().deletePage(0) }
        waitFor { vm().canvas.value!!.pageCount == 2 }
        assertFalse(vm().canvas.value!!.hasCover)
        waitFor { !exists("page_chip_cover") && exists("page_chip_0") }
        // Yeniden "Kapak ekle" sunulur
        compose.onNodeWithTag("page_chip_0").performSemanticsAction(SemanticsActions.OnLongClick)
        waitFor { exists("page_add_cover") }
    }

    @Test
    fun notesCanvasHasNoAddCover() {
        open("Not sayfaları", CanvasKind.NOTES) { c ->
            repo().saveStrokes(c.localId, listOf(emptyList(), emptyList()))
        }
        waitFor { exists("page_chip_0") }
        compose.onNodeWithTag("page_chip_0").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.waitForIdle()
        assertFalse(exists("page_add_cover"))
        assertFalse(vm().v45.canAddCover())
        assertFalse(vm().v45.addCover())
        assertNull(vm().canvas.value!!.pages.getOrNull(2))
    }
}
