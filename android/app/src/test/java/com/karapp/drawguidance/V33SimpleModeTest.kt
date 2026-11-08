package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.formula.FormulaCatalog
import com.karapp.drawguidance.content.model3d.Catalog3D
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.drawing.BrushCategory
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import com.karapp.drawguidance.ui.drawing.SimpleCatalog
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
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

/** v33 (3) basit çizim türlerinin sınırlı katalogları: 10'ar şekil/diyagram/formül/3B şekil, 3 fırça kategorisi, 50 (v34: 86) çocuk şekli. */
class V33SimpleCatalogTest {
    @Test
    fun kindsAndCatalogs() {
        assertEquals(CanvasKind.ART, CanvasKind.SIMPLE_ART.base)
        assertEquals(CanvasKind.ART, CanvasKind.KIDS.base)
        assertEquals(CanvasKind.NOTES, CanvasKind.SIMPLE_NOTES.base)
        assertTrue(CanvasKind.SIMPLE_NOTES.paged && !CanvasKind.SIMPLE_ART.paged && !CanvasKind.KIDS.paged)
        assertEquals(200, CanvasKind.SIMPLE_NOTES.maxPages)
        assertTrue(CanvasKind.Simple.all { it.simple } && CanvasKind.Advanced.none { it.simple })

        // v45 basit not, gelişmiş not almanın bütün kategorilerini birebir sunar (v34'teki 42 şekillik üç kategori kalktı;
        // ayrıntılı karşılaştırma V45SimpleNotesCatalogTest'te); v42 basit resimde genel şekiller (akış şeması yok)
        ShapeRegistry.categories(CanvasKind.SIMPLE_NOTES).let { cats ->
            val notes = ShapeRegistry.categories(CanvasKind.NOTES)
            assertEquals(notes.map { it.id }, cats.map { it.id })
            val items = cats.flatMap { it.items }
            assertEquals(notes.flatMap { it.items }, items)
            assertTrue("gelişmiş not kataloğu: ${items.size}", items.size > 42)
            assertTrue(items.contains("graph.FUNCTION") && items.contains("graph.AXES"))
        }
        ShapeRegistry.categories(CanvasKind.SIMPLE_ART).let { cats ->
            val items = cats.flatMap { it.items }
            assertEquals(7, cats.size)
            assertTrue(items.size >= 100)
            assertEquals(items.size, items.toSet().size)
        }
        val kids = ShapeRegistry.categories(CanvasKind.KIDS).flatMap { it.items }
        assertEquals(86, kids.size)
        assertEquals(10, ShapeRegistry.categories(CanvasKind.KIDS).size)
        assertTrue(kids.all { it.startsWith("lib.kid_") && ShapeRegistry.title(it) != null })
        // Gelişmiş türlerde çocuk şekilleri yok, basit türlerde gelişmiş katalog yok
        assertTrue(ShapeRegistry.categories(CanvasKind.ART).flatMap { it.items }.none { it.startsWith("lib.kid_") })
        assertTrue(ShapeRegistry.categories(CanvasKind.ART).sumOf { it.items.size } > 10)

        CanvasKind.Simple.forEach { assertEquals(3, BrushCategory.forKind(it).size) }
        assertTrue(BrushCategory.forKind(CanvasKind.ART).size > 3)

        val formulas = FormulaCatalog.categories.flatMap { it.templates }.map { it.id }.toSet()
        assertEquals(10, SimpleCatalog.formulas.size)
        assertTrue(SimpleCatalog.formulas.all { it in formulas })
        assertEquals(10, SimpleCatalog.diagrams.toSet().size)
        // v45 basit resim ve çocuklarda 10 hazır 3B şekil; basit notta (gelişmiş not gibi) bütün 3B katalog ve diyagramlar
        listOf(CanvasKind.SIMPLE_ART, CanvasKind.KIDS).forEach { k ->
            val models = SimpleCatalog.models3d(k)!!
            assertEquals(10, models.toSet().size)
            assertTrue(models.all { id -> Catalog3D.all.any { it.id == id } })
            assertEquals(SimpleCatalog.diagrams, SimpleCatalog.diagrams(k))
        }
        assertEquals(null, SimpleCatalog.models3d(CanvasKind.SIMPLE_NOTES))
        assertEquals(null, SimpleCatalog.diagrams(CanvasKind.SIMPLE_NOTES))
        assertEquals(10, SimpleCatalog.legacyNoteModels.toSet().size)
        assertTrue(SimpleCatalog.legacyNoteModels.all { id -> Catalog3D.all.any { it.id == id } })
        assertEquals(null, SimpleCatalog.models3d(CanvasKind.ART))
    }
}

/**
 * v33 (3) basit çizim düzenleyicisi: üstte tek menü (mod seçici yok), yalnız seçili aracın adı yazar; Şekil boyama
 * dokunulan şekil alanını boyar; çocuklarda büyük palet ve sade fırça paneli; basit notta Diyagram ve Formül.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V33SimpleModeTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun openKind(name: String, kind: CanvasKind) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    /** Seçili araç adıyla birlikte (hap) gösterilir: simge düğmesinden (36 dp) belirgin genişlikte. */
    private fun labelShown(id: String): Boolean {
        val nodes = compose.onAllNodes(hasTestTag("simple_tool_$id")).fetchSemanticsNodes()
        val minPx = with(compose.density) { 56.dp.toPx() }
        return nodes.isNotEmpty() && nodes.first().size.width > minPx
    }

    private fun pill(id: String) = compose.onAllNodes(hasTestTag("simple_tool_$id") and isSelected())

    @Test
    fun simpleArtHasSingleMenu() {
        // Formdan basit resim tuvali
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithContentDescription("Yeni Tuval").performClick()
        waitForText("Yeni Tuval Oluştur")
        compose.onNodeWithTag("form_kind_SIMPLE_ART").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Basit")
        compose.onNodeWithText("Oluştur").clickInPopup()
        waitFor { exists("drawing_canvas") }
        assertEquals(CanvasKind.SIMPLE_ART, vm().canvas.value?.kind)
        // Tek menü: mod seçici yok, fırça seçili ve adı yazıyor
        assertTrue(exists("simple_tool_row"))
        assertFalse(exists("mode_selector"))
        assertFalse(exists("tool_row_draw"))
        waitFor { compose.onAllNodes(hasTestTag("simple_tool_brush") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(labelShown("brush"))
        // v48 dikey ekranda bütün düğmelerin adı simgenin yanında yazar
        assertTrue(labelShown("text"))
        // Seç: adı yazar, mod Seç
        compose.onNodeWithTag("simple_tool_select").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.SELECT }
        waitFor { labelShown("select") && compose.onAllNodes(hasTestTag("simple_tool_select") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        // Silgi → çizim modunda silgi
        compose.onNodeWithTag("simple_tool_eraser").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.DRAW && pill("eraser").fetchSemanticsNodes().isNotEmpty() }
        // Şekil ekle → 10 şekillik seçici
        compose.onNodeWithTag("simple_tool_shape").performScrollTo().performClick()
        waitFor { vm().isShapePickerOpen.value }
        shot("v33-simple-art")
    }

    @Test
    fun kidsPaintFillsShapeRegion() {
        openKind("Çocuk", CanvasKind.KIDS)
        // Çocuk şekli (elma) kağıdın ortasına
        compose.runOnIdle {
            vm().selectShapeId("lib.kid_apple")
            vm().addShapeAt("g1", Offset(500f, 500f))
        }
        waitFor { vm().canvas.value?.strokes?.any { it.shape != null } == true }
        compose.runOnIdle { vm().chooseMode(EditMode.DRAW) }
        // Şekil boyama: büyük palet, kırmızı seçilir, şeklin ortasına dokunulur
        compose.onNodeWithTag("simple_tool_paint").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.PAINT && exists("kids_palette") }
        compose.onNodeWithContentDescription("Kırmızı").performClick()
        val size = canvasSize()
        tap(screenOf(500f, 500f))
        waitFor {
            val s = vm().canvas.value?.strokes?.firstOrNull { it.shape != null } ?: return@waitFor false
            ShapeFactory.subPaths(s).any { it.tag == com.karapp.drawguidance.ui.drawing.VectorEdit.REGION && it.fillColorHex.equals("#E53935", true) }
        }
        // Boşluğa dokunmak bir şey boyamaz (bildirim)
        tap(Offset(size.x * 0.05f, size.y * 0.05f))
        waitForText("Burada boyanacak bir şekil yok", substring = true)
        shot("v33-kids-paint")
        // Çocuk fırça paneli sade: büyük palet var, opaklık yok
        compose.onNodeWithTag("simple_tool_brush").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.DRAW }
        compose.runOnIdle { vm().togglePanel(com.karapp.drawguidance.ui.screens.canvasedit.PanelTab.BRUSH) }
        waitFor { exists("kids_brush_panel") }
        assertTrue(compose.onAllNodesWithText("Opaklık", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun simpleNotesHasDiagramAndFormula() {
        openKind("Basit not", CanvasKind.SIMPLE_NOTES)
        assertTrue(exists("simple_tool_diagram") && exists("simple_tool_formula"))
        compose.onNodeWithTag("simple_tool_formula").performScrollTo().performClick()
        waitFor { exists("formula_picker") }
        // v35 gelişmiş not almadaki bütün formüller kategorileriyle (10 temel formüllük kısıt kalktı)
        assertTrue(compose.onAllNodesWithText("Temel formüller").fetchSemanticsNodes().isEmpty())
        val calc = FormulaCatalog.categories.first { c -> c.templates.any { it.title == "Türev tanımı" } }
        assertTrue(FormulaCatalog.categories.size > 1)
        compose.onAllNodesWithText(calc.title)[0].performScrollTo().performClick()
        waitForText("Türev tanımı")
        assertNull(SimpleCatalog.formulas(CanvasKind.SIMPLE_NOTES))
    }

    /** v35 basit menüde aynı anda tek araç seçili; seçilenin simgesinin yanında adı yazar; öteki pencere kapanır. */
    @Test
    fun onlyOneSimpleToolSelected() {
        openKind("Tek seçim", CanvasKind.SIMPLE_NOTES)
        val ids = listOf("brush", "eraser", "shape", "formula", "diagram", "background", "image", "text", "model3d", "paint", "elements", "select")
        fun selectedIds() = ids.filter { id ->
            compose.onAllNodes(hasTestTag("simple_tool_$id")).fetchSemanticsNodes().firstOrNull()
                ?.config?.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Selected) { null } == true
        }
        waitFor { selectedIds() == listOf("brush") }
        assertTrue(labelShown("brush"))
        // Arka plan basit notta da var ve şeklin hemen ardında
        compose.onNodeWithTag("simple_tool_background").performScrollTo().performClick()
        waitFor { exists("background_sheet") && selectedIds() == listOf("background") }
        assertTrue(labelShown("background"))
        shot("v35-simple-one-selected")
        // Elemanlar seçilince arka plan penceresi kapanır; v47 Elemanlar açılır alanın düğmesi, etkin araç (fırça) seçili kalır
        compose.onNodeWithTag("simple_tool_elements").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitFor { !exists("background_sheet") && "elements" in selectedIds() && "background" !in selectedIds() }
        // v47 Elemanlar açılır alanın düğmesi: araç seçilince alan açık kalır (araçlardan yalnız biri seçili)
        compose.onNodeWithTag("simple_tool_brush").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitFor { selectedIds() == listOf("brush", "elements") }
    }
}

/**
 * v33 (3) Ana sayfada iki oluşturma düğmesi: basit ve gelişmiş çizim; v34 alt modlar düğmelerin altında açılan
 * listede (ayrı pencere yok); 3B şekil 3B düzenleyicide.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HomeTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V33HomeChooserTest : V30EditorBase() {
    @Test
    fun simpleAndAdvancedGroups() {
        waitFor { exists("welcome_screen") }
        repeat(30) { if (exists("welcome_screen")) { compose.mainClock.advanceTimeBy(150); advanceMain(150) } }
        waitFor { !exists("welcome_screen") && exists("home_new_simple") && exists("home_new_advanced") }
        shot("v33-home")
        // Basit çizim: üç alt mod
        compose.onNodeWithTag("home_new_simple").performScrollTo().performClick()
        waitFor { exists("new_kind_KIDS") }
        assertTrue(exists("new_kind_SIMPLE_ART") && exists("new_kind_SIMPLE_NOTES"))
        assertFalse(exists("new_kind_ART") || exists("new_kind_3D"))
        compose.onNodeWithTag("new_kind_KIDS").performScrollTo().performClick()
        waitForText("Yeni Tuval Oluştur")
        // Formda yalnız basit türler
        assertTrue(exists("form_kind_SIMPLE_ART"))
        assertFalse(exists("form_kind_ART"))
        compose.onNode(hasSetTextAction()).performTextInput("Boyama")
        compose.onNodeWithText("Oluştur").clickInPopup()
        waitFor { exists("drawing_canvas") && exists("simple_tool_row") }
        assertEquals(CanvasKind.KIDS, allCanvases().single().kind)
        compose.onNodeWithContentDescription("Geri").performClick()
        waitFor { exists("home_screen") }
        // Gelişmiş çizim: resim, not, çizgi roman ve 3B şekil oluşturma
        compose.onNodeWithTag("home_new_advanced").performScrollTo().performClick()
        waitFor { exists("new_kind_3D") }
        assertTrue(exists("new_kind_ART") && exists("new_kind_NOTES") && exists("new_kind_COMIC"))
        // v34 ayrı pencere yok: liste ana sayfanın içinde; aynı düğmeye yeniden dokununca kapanır
        assertFalse(exists("new_drawing_sheet"))
        assertTrue(exists("new_drawing_list") && exists("home_screen"))
        shot("v34-home-inline-list")
        compose.onNodeWithTag("new_kind_3D").performScrollTo().performClick()
        waitFor { exists("new_3d_sheet") }
        compose.onNode(hasSetTextAction()).performTextInput("Kalem kutusu")
        compose.onNodeWithTag("new_3d_create").performClick()
        waitFor { exists("model3d_editor") }
        waitForText("Kalem kutusu", substring = true)
        // 3B tuval Tuvallerim'e eklenmez
        assertEquals(1, allCanvases().size)
    }
}
