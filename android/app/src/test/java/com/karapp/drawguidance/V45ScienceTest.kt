package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.formula.Formula
import com.karapp.drawguidance.content.formula.FormulaEval
import com.karapp.drawguidance.content.graph.GraphKind
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.FormulaSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.drawing.FormulaRenderer
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool
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

/** v45 bilim şekillerinin ortak yardımcıları. */
private object V45Sci {
    val STYLE = ShapeStyle(strokeColorHex = "#263238", strokeWidth = 3f)

    /** Şeklin köşedeki formül yazısının satırları (0: "formula", sonra "formula:1"…). */
    fun labels(s: Stroke): List<String> = s.shape!!.subpaths.filter { it.label != null && (it.tag == "formula" || it.tag?.startsWith("formula:") == true) }
        .sortedBy { it.tag!!.removePrefix("formula").removePrefix(":").ifEmpty { "0" }.toInt() }.map { it.label!! }

    /** Kütüphane kimliği ("sci.…" → "lib.sci.…"). */
    fun lib(id: String) = if (id.startsWith("sci.")) com.karapp.drawguidance.content.library.ShapeLibrary.PREFIX + id else id

    fun build(kind: String, params: Map<String, String>? = null, id: String = "p") =
        ShapeFactory.build(lib(kind), Offset(100f, 100f), Offset(1100f, 800f), STYLE, id = id, order = 0, params = params)!!
}

/**
 * v45 (5–9) parametreli şekiller, grafik ayarları ve kayıt: değerler elemanın kendi kaydında (ShapeSpec.params,
 * GraphSpec.angleDeg) saklanır, eski kayıtlar açılır, köşe yazısı değerlerle çizilir, yeniden kurulan şekil kutusunu
 * korur, tutamaklar şeklin üstünde durur.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V45ScienceLogicTest {
    @org.junit.After
    fun tearDown() = org.koin.core.context.stopKoin()

    @Test
    fun paramsAreStoredOnTheShapeAndDrawnInTheCornerLabel() {
        val plain = V45Sci.build("sci.phys.projectile")
        assertNull("varsayılanla eklenen şekilde değer kaydı yok", plain.shape!!.params)
        assertEquals("y = x·tanθ − g·x²/(2v²·cos²θ)", V45Sci.labels(plain)[0])
        assertEquals("v = 20 m/s; θ = 55°", V45Sci.labels(plain)[1])
        val fast = V45Sci.build("sci.phys.projectile", mapOf("v" to "30", "theta" to "45"))
        assertEquals(mapOf("v" to "30", "theta" to "45"), fast.shape!!.params)
        assertEquals("v = 30 m/s; θ = 45°", V45Sci.labels(fast)[1])
        // R = v²·sin2θ/g = 900/9,8 ≈ 91,8 m
        assertTrue(V45Sci.labels(fast)[2], V45Sci.labels(fast)[2].startsWith("R = 91,8 m"))
        // Köşe yazısı sol üstte
        val subs = fast.shape!!.subpaths
        val i = subs.indexOfFirst { it.tag == "formula" }
        val start = subs.take(i).sumOf { it.count }
        val box = ShapeFactory.box(fast)!!
        val xs = fast.points.subList(start, start + subs[i].count).map { it.x }
        assertTrue("yazı sol kenarda: ${xs.minOrNull()} / ${box.left}", xs.minOrNull()!! < box.left + box.width * 0.1f)
    }

    @Test
    fun rebuildKeepsTheBoxAndHandlesSitOnTheShape() {
        val s = V45Sci.build("sci.phys.pendulum")
        val box = ShapeFactory.box(s)!!
        val next = ShapeFactory.rebuild(s, s.shape!!.style, s.shape!!.graph, mapOf("L" to "2", "theta" to "-20"))!!
        assertEquals(mapOf("L" to "2", "theta" to "-20"), next.shape!!.params)
        val box2 = ShapeFactory.box(next)!!
        assertEquals(box.left, box2.left, 1f); assertEquals(box.top, box2.top, 1f)
        assertEquals(box.width, box2.width, 1f); assertEquals(box.height, box2.height, 1f)
        assertTrue(V45Sci.labels(next)[1], V45Sci.labels(next)[1].startsWith("L = 2 m"))
        // Kare (oranı korunan) şeklin kutusu karedir; kutu işareti görünmez (kalınlık 0, açık çizgi)
        assertEquals(box.width, box.height, 2f)
        val marks = next.shape!!.subpaths.filter { it.tag == com.karapp.drawguidance.content.library.science.ParamModel.FRAME_TAG }
        assertEquals(2, marks.size)
        assertTrue(marks.all { it.widthScale == 0f && !it.closed })
        // Arka arkaya değer değişikliklerinde kutu kaymaz
        var cur = next
        for (l in listOf("0.5", "3", "1.2")) cur = ShapeFactory.rebuild(cur, cur.shape!!.style, null, mapOf("L" to l))!!
        val box3 = ShapeFactory.box(cur)!!
        assertEquals(box.left, box3.left, 1f); assertEquals(box.width, box3.width, 1f)
        // Tutamak kutunun içinde
        val model = ShapeRegistry.paramModel(V45Sci.lib("sci.phys.pendulum"))!!
        for (h in model.unitHandles(next.shape!!.params)) {
            assertTrue("${h.key}: ${h.x},${h.y}", h.x in 0f..1f && h.y in 0f..1f)
        }
        // Parametresiz şekillerde model yok
        assertNull(ShapeRegistry.paramModel("CIRCLE"))
        assertNull(ShapeRegistry.paramModel(V45Sci.lib("sci.phys.prism")))
        for (id in listOf("sci.phys.projectile", "sci.phys.transverse_wave", "sci.phys.spring_mass", "sci.phys.pendulum",
            "sci.phys.convex_lens", "sci.phys.concave_lens", "sci.phys.inclined_plane", "sci.phys.ohm_graph",
            "sci.geo.triangle", "sci.geo.circle", "sci.geo.polygon", "sci.math.parabola", "sci.bio.population_growth",
            "sci.chem.water_molecule", "sci.chem.methane", "sci.chem.benzene", "sci.chem.molecule", "sci.chem.molecule_3d",
            "sci.chem.org.glucose", "sci.chem.org.alanine")) {
            assertNotNull(id, ShapeRegistry.paramModel(V45Sci.lib(id)))
            assertNotNull(id, ShapeFactory.build(V45Sci.lib(id), Offset(0f, 0f), Offset(400f, 300f), V45Sci.STYLE, id = id, order = 0))
        }
    }

    @Test
    fun saveLoadRoundTripKeepsParamsAndAngleMode() {
        val proj = V45Sci.build("sci.phys.projectile", mapOf("v" to "25", "theta" to "40"), id = "a")
        val mol = V45Sci.build("sci.chem.molecule", mapOf("formula" to "CH3COOH", "view" to "skeletal"), id = "b").copy(order = 1)
        val g0 = ShapeFactory.build("curve.sinus", Offset(0f, 0f), Offset(600f, 400f), V45Sci.STYLE, id = "c", order = 2)!!
        val graph = ShapeFactory.rebuild(g0, g0.shape!!.style, g0.shape!!.graph!!.copy(angleDeg = true))!!
        assertTrue(graph.shape!!.graph!!.angleDeg)
        val plain = V45Sci.build("sci.phys.prism", id = "d").copy(order = 3)
        val list = listOf(proj, mol, graph, plain)
        val back = ContentCodec.decode(ContentCodec.encode(list))
        // Noktalar kayıtta yuvarlanır; şekil kaydı (değerler dahil) aynen geri gelir
        assertEquals(list.map { it.id to it.shape }, back.map { it.id to it.shape })
        assertEquals(mapOf("v" to "25", "theta" to "40"), back[0].shape!!.params)
        assertEquals("CH3COOH", back[1].shape!!.params!!["formula"])
        assertTrue(back[2].shape!!.graph!!.angleDeg)
        // Geriye uyum: değer kaydı olmayan şekil ve derece ayarı olmayan grafik JSON'a yeni alan yazmaz
        val json = kotlinx.serialization.json.Json { encodeDefaults = false }
        val plainJson = json.encodeToString(com.karapp.drawguidance.data.model.ShapeSpec.serializer(), plain.shape!!)
        assertFalse(plainJson, plainJson.contains("params"))
        val gJson = json.encodeToString(com.karapp.drawguidance.data.model.ShapeSpec.serializer(), g0.shape!!)
        assertFalse(gJson, gJson.contains("angleDeg"))
        // Eski (v44) JSON'u okunur: params yok → varsayılan değerlerle çizilir
        val old = ShapeFactory.rebuild(proj.copy(shape = proj.shape!!.copy(params = null)), V45Sci.STYLE, null)!!
        assertEquals("v = 20 m/s; θ = 55°", V45Sci.labels(old)[1])
    }

    @Test
    fun moleculeShapesFollowTheirFormulaAndView() {
        val acid = V45Sci.build("sci.chem.molecule", mapOf("formula" to "CH3COOH"))
        val l = V45Sci.labels(acid)
        assertEquals("CH₃COOH", l[0])
        assertTrue(l[1], l[1].contains("C₂H₄O₂") && l[1].contains("60,05 g/mol"))
        val atoms = acid.shape!!.subpaths.count { it.tag?.startsWith("atom:") == true }
        assertEquals("açık formülde 8 atom yazısı", 8, atoms)
        val ball = V45Sci.build("sci.chem.molecule", mapOf("formula" to "CH3COOH", "view" to "3d"))
        // 3B modelde her atom bir top
        assertEquals(8, ball.shape!!.subpaths.filter { it.tag?.startsWith("atom:") == true }.map { it.tag }.distinct().size)
        // Bilinmeyen yapı: örnek izomer, "örnek yapı" yazısıyla
        val ex = V45Sci.build("sci.chem.molecule", mapOf("formula" to "C7H16"))
        assertTrue(V45Sci.labels(ex)[1], V45Sci.labels(ex)[1].contains("örnek yapı"))
    }
}

/**
 * v45 (6, 8, 9) Düzenle akışı: parametreli şekle Düzenle'de dokununca değer/formül düzenleyicisi açılır, köşe yazısı
 * formülle ve adım düğmeleriyle güncellenir, hatalı formül kırmızı yazılır ve şekil değişmez, tutamak sürüklenince
 * değerler değişir, "Vektör düzenle" nokta düzenlemeye geçer. Grafiklerde formül satırı; formül elemanında sonuç.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V45ScienceUiTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun open(name: String, strokes: List<Stroke>) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking {
            val c = repo().createCanvas(name, 1200, 900, "#FFFFFF", false, kind = CanvasKind.NOTES)
            repo().saveStrokes(c.localId, listOf(strokes))
        }
        waitForText(name)
        compose.onNodeWithText(name).performClick()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Tuval (1200×900) noktasının ekrandaki yeri (sığdırılmış görünüm). */
    private fun screen(p: Offset): Offset {
        val size = canvasSize()
        val m = 2 * 24f * compose.density.density
        val scale = minOf((size.x - m) / 1200f, (size.y - m) / 900f)
        return Offset((size.x - 1200f * scale) / 2f + p.x * scale, (size.y - 900f * scale) / 2f + p.y * scale)
    }

    private fun shape(): Stroke = canvas().strokes.single { it.brushType == BrushType.SHAPE }
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun shown(tag: String): String = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .config.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString("") { it.text } ?: ""

    /** Düzenle → şekle dokun (şeklin ortaya en yakın çizgi noktası; ortası boş olabilir). */
    private fun openEditor() {
        mode("Düzenle")
        val p = shape().points.minByOrNull { abs(it.x - 600f) + abs(it.y - 450f) }!!
        tap(screen(Offset(p.x, p.y)))
        waitFor { vm().mode.value == EditMode.SHAPE_EDIT }
    }

    /** Formül penceresine [value] yazıp Tamam'a basar. */
    private fun typeInSheet(chip: String, sheet: String, value: String) {
        compose.onNodeWithTag(chip).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { has(sheet) }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(sheet))).performTextReplacement(value)
        val ok = compose.activity.getString(R.string.ok_button)
        compose.onNode(hasText(ok) and hasAnyAncestor(hasTestTag(sheet))).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { !has(sheet) }
    }

    @Test
    fun editOpensParamEditorFormulaUpdatesCornerLabelAndVectorEditSwitches() {
        open("Eğik atış", listOf(V45Sci.build("sci.phys.projectile")))
        openEditor()
        // Düzenle → değer/formül düzenleyicisi (nokta düzenleme değil)
        waitFor { has("param_controls") }
        assertEquals(ShapeTool.PARAMS, vm().shapeTool.value)
        assertTrue("tutamaklar çizili", has("param_handles"))
        assertTrue(has("param_step_v") && has("param_step_theta"))
        assertFalse("ileri ayar (g) adımlayıcısı yok", has("param_step_g"))
        assertEquals("v = 20 m/s; θ = 55°", V45Sci.labels(shape())[1])
        shot("v45-param-editor")

        // Formül: atama ile iki değer birden
        typeInSheet("param_formula", "param_formula_sheet", "v = 30; θ = 45°")
        waitFor { shape().shape!!.params == mapOf("v" to "30", "theta" to "45") }
        assertEquals("köşe yazısı güncellendi", "v = 30 m/s; θ = 45°", V45Sci.labels(shape())[1])
        assertFalse(has("param_error"))

        // Türetilmiş değer: menzil R = 50 m → v çözülür (θ = 45°: v = √(50·9,8) = 22,14)
        typeInSheet("param_formula", "param_formula_sheet", "R = 50")
        waitFor { shape().shape!!.params?.get("v") != "30" }
        val v = shape().shape!!.params!!["v"]!!.toDouble()
        assertEquals(22.136, v, 0.01)
        assertTrue(V45Sci.labels(shape())[2], V45Sci.labels(shape())[2].startsWith("R = 50 m"))

        // Hatalı formül: kırmızı ileti, şekil (son geçerli çizim) değişmez
        val before = shape()
        typeInSheet("param_formula", "param_formula_sheet", "v = (3")
        waitFor { has("param_error") }
        assertTrue(shown("param_error"), shown("param_error").contains("Parantez"))
        assertEquals(before, shape())
        // Aralık dışı değer de uygulanmaz
        typeInSheet("param_formula", "param_formula_sheet", "θ = 120°")
        waitFor { shown("param_error").contains("aralığın dışında") }
        assertEquals(before, shape())
        shot("v45-param-error")

        // Adım düğmesi: v bir artar, hata kalkar
        compose.onNode(hasContentDescription("v = 22,14 m/s artır") and hasAnyAncestor(hasTestTag("param_step_v"))).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { (shape().shape!!.params!!["v"]!!.toDouble() - 23.136).let { abs(it) < 0.01 } }
        assertFalse(has("param_error"))
        assertTrue(V45Sci.labels(shape())[1], V45Sci.labels(shape())[1].startsWith("v = 23,14 m/s"))

        // Tutamak sürüklenince hız vektörü (v, θ) değişir; tek geri alma adımı
        val (key, h) = vm().paramShapes.handles().single()
        assertEquals("v0", key)
        val p0 = shape().shape!!.params
        drag(screen(h), Offset(-60f, -60f))
        waitFor { shape().shape!!.params != p0 }
        val th = shape().shape!!.params!!["theta"]!!.toDouble()
        assertTrue("açı büyüdü: $th", th > 45.0)
        assertEquals(EditMode.SHAPE_EDIT, vm().mode.value)

        // Vektör düzenle → nokta düzenleme; değer kaydı korunur
        val kept = shape().shape!!.params
        compose.onNodeWithTag("param_vector_edit").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { vm().shapeTool.value == ShapeTool.NODES }
        waitFor { !has("param_controls") }
        assertFalse(has("param_handles"))
        assertEquals(kept, shape().shape!!.params)
    }

    @Test
    fun moleculeEditorChangesFormulaViewAndShowsChemistryErrors() {
        open("Molekül", listOf(V45Sci.build("sci.chem.org.ethanol")))
        openEditor()
        waitFor { has("param_controls") }
        assertTrue(has("param_choice_structural") && has("param_choice_3d"))
        assertFalse("metin değerinin adımlayıcısı yok", has("param_step_formula"))
        assertEquals("CH₃CH₂OH", V45Sci.labels(shape())[0])
        // Görünüm: 3B top-çubuk
        compose.onNodeWithTag("param_choice_3d").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { shape().shape!!.params?.get("view") == "3d" }
        // Formül: glisin (yarı açık formül)
        typeInSheet("param_formula", "param_formula_sheet", "NH2CH2COOH")
        waitFor { shape().shape!!.params?.get("formula") == "NH2CH2COOH" }
        assertTrue(V45Sci.labels(shape())[1], V45Sci.labels(shape())[1].contains("C₂H₅NO₂"))
        assertEquals("görünüm korunur", "3d", shape().shape!!.params?.get("view"))
        // Değerlik hatası
        val before = shape()
        typeInSheet("param_formula", "param_formula_sheet", "CH5")
        waitFor { has("param_error") }
        assertEquals(before, shape())
        // Bilinmeyen element
        typeInSheet("param_formula", "param_formula_sheet", "CXy4")
        waitFor { shown("param_error").contains("element", ignoreCase = true) }
        assertEquals(before, shape())
        shot("v45-molecule-error")
    }

    @Test
    fun graphFormulaRowAppliesValidFormulaAndKeepsLastValidCurve() {
        val g = ShapeFactory.build("curve.sinus", Offset(100f, 100f), Offset(1100f, 800f), V45Sci.STYLE, id = "g", order = 0)!!
        open("Grafik formülü", listOf(g))
        openEditor()
        waitFor { vm().shapeTool.value == ShapeTool.GRAPH }
        waitFor { has("graph_formula") }
        assertEquals(GraphKind.FUNCTION.name, shape().shape!!.graph!!.kind)
        // İki eğri
        typeInSheet("graph_formula", "graph_formula_sheet", "y = x^2; y = 2x + 1")
        waitFor { shape().shape!!.graph!!.formula == "y = x^2; y = 2x + 1" }
        assertFalse(has("graph_formula_error"))
        val label = V45Sci.labels(shape()).firstOrNull() ?: ""
        assertTrue("formül yazısı: $label", label.contains("x²") || label.contains("x^2"))
        // Hatalı formül: kırmızı ileti, son geçerli eğri kalır
        val before = shape()
        typeInSheet("graph_formula", "graph_formula_sheet", "y = sin(")
        waitFor { has("graph_formula_error") }
        assertEquals(before, shape())
        // Derece seçimi kaydedilir
        compose.onNodeWithTag("graph_degrees").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { shape().shape!!.graph!!.angleDeg }
        shot("v45-graph-formula")
    }

    @Test
    fun formulaElementShowsResultAndDivisionByZeroMessage() {
        val src = Formula.atomize("\\frac{6}{x}")
        val quad = FormulaRenderer.quadAt(src, 64f, 400f, 400f).map { StrokePoint(ContentCodec.quantize(it.x), ContentCodec.quantize(it.y)) }
        val f = Stroke("f", quad, "#111111", BrushType.FORMULA, 64f, 1f, 0, formula = FormulaSpec(src))
        open("Formül hesabı", listOf(f))
        val center = Offset(quad.map { it.x }.average().toFloat(), quad.map { it.y }.average().toFloat())
        mode("Seç")
        tap(screen(center))
        waitFor { vm().selectedIds.value == setOf("f") }
        compose.runOnIdle { vm().editFormula() }
        fun source() = canvas().strokes.single().formula!!.source
        val x = Formula.slots(source()).first { it.value == "x" }.name
        compose.runOnIdle { vm().setFormulaSlot(x, "4") }
        waitFor { source().contains(FormulaEval.RESULT_MARK) }
        waitFor { source().endsWith("=\\frac{3}{2}") }
        assertFalse(has("formula_eval_error"))
        // Sıfıra bölme: sonuç "tanımsız", kırmızı ileti
        compose.runOnIdle { vm().setFormulaSlot(x, "0") }
        waitFor { has("formula_eval_error") }
        waitFor { source().endsWith("=\\text{tanımsız}") }
        assertTrue(compose.onAllNodes(hasText("Sıfıra bölme", substring = true) and hasAnyAncestor(hasTestTag("formula_eval_error")))
            .fetchSemanticsNodes().isNotEmpty())
        shot("v45-formula-div-zero")
        // Düzelince ileti kalkar
        compose.runOnIdle { vm().setFormulaSlot(x, "3") }
        waitFor { !has("formula_eval_error") }
        waitFor { source().endsWith("=2") }
    }
}
