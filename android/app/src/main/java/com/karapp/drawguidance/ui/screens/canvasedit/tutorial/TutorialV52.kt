package com.karapp.drawguidance.ui.screens.canvasedit.tutorial

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.formula.Formula
import com.karapp.drawguidance.content.formula.FormulaCatalog
import com.karapp.drawguidance.content.formula.FormulaEval
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.FormulaSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.FormulaRenderer
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import java.util.UUID

/*
 * v52 not alma eğitimi: formülle oluşturulan kimya ve fizik şekilleri (molekül formülden, Bohr atomu atom numarasından,
 * eğik atış hız ve açıdan) ve formül hesaplama (yuvalara değer yazılır, sonuç kendiliğinden eklenir; değer değişince
 * sonuç yenilenir). iOS `TutorialV52` ile aynı.
 */
internal object TutorialV52 {
    private const val INK = "#263238"
    private const val ACCENT = "#1565C0"
    private const val MOLECULE = "lib.sci.chem.molecule"
    private const val BOHR = "lib.sci.chem.bohr_atom"
    private const val PROJECTILE = "lib.sci.phys.projectile"
    private const val MOLECULE_ID = "tut52-molecule"
    private const val BOHR_ID = "tut52-bohr"
    private const val PROJECTILE_ID = "tut52-projectile"
    private const val CALC_ID = "tut52-calc"
    private const val CALC2_ID = "tut52-calc2"

    private val q = com.karapp.drawguidance.data.local.ContentCodec::quantize

    private fun param(kind: String, r: Rect, params: Map<String, String>, id: String, width: Float = 2.5f): Stroke? =
        ShapeFactory.build(kind, r.topLeft, r.bottomRight, ShapeStyle(INK, width), null, id, 0, params = params)

    private suspend fun TutorialDemo.redraw(id: String, kind: String, r: Rect, params: Map<String, String>) {
        val next = param(kind, r, params, id) ?: return
        host.mutate { list -> list.map { if (it.id == id) next.copy(order = it.order, layerId = it.layerId) else it } }
        pause(700)
    }

    /**
     * Şablon formülü [values] yuvalarına yazılmış ve hesaplanmış olarak (uygulamadaki gibi: sonuç eklenir). [values]
     * boşsa şablon olduğu gibi.
     */
    fun calcSource(templateId: String, values: Map<String, String>): String? {
        val t = FormulaCatalog.find(templateId) ?: return null
        var src = Formula.atomize(t.source)
        if (values.isEmpty()) return src
        for (s in Formula.slots(src)) values[s.value]?.let { src = Formula.setSlot(src, s.name, it) }
        return FormulaEval.annotate(src).source
    }

    private fun formula(templateId: String, values: Map<String, String>, left: Float, top: Float, size: Float, id: String): Stroke? {
        val src = calcSource(templateId, values) ?: return null
        val quad = FormulaRenderer.quadAt(src, size, left, top).map { StrokePoint(q(it.x), q(it.y)) }
        return Stroke(
            id = id, points = quad, colorHex = ACCENT, brushType = BrushType.FORMULA, sizePx = size,
            opacity = 1f, order = 0, formula = FormulaSpec(src, templateId),
        )
    }

    private suspend fun TutorialDemo.reformula(id: String, templateId: String, values: Map<String, String>, left: Float, top: Float, size: Float) {
        val next = formula(templateId, values, left, top, size, id) ?: return
        host.mutate { list -> list.map { if (it.id == id) next.copy(order = it.order, layerId = it.layerId) else it } }
        pause(800)
    }

    private fun TutorialDemo.moleculeRect() = Rect(w * 0.06f, h * 0.07f, w * 0.06f + u * 0.46f, h * 0.07f + u * 0.33f)
    private fun TutorialDemo.bohrRect() = Rect(w * 0.94f - u * 0.32f, h * 0.07f, w * 0.94f, h * 0.07f + u * 0.32f)
    private fun TutorialDemo.projectileRect(): Rect {
        val bw = minOf(w * 0.88f, u * 0.8f); val top = h * 0.07f + u * 0.4f
        return Rect(w * 0.06f, top, w * 0.06f + bw, top + bw / 1.8f)
    }
    private fun TutorialDemo.calcTop() = projectileRect().bottom + u * 0.05f

    val notesScience: TutorialGroup by lazy {
        TutorialGroup(
            id = "notes_science_v52", kind = CanvasKind.NOTES, title = R.string.v52_tut_science, summary = R.string.v52_tut_science_text,
            icon = Icons.Filled.Science, ai = false,
            steps = listOf(
                // Molekül formülden: H₂O → CO₂ → CH₄ → etanol
                TutorialStep(TutorialAnchor.simple("shape"), R.string.v52_tut_molecule, R.string.v52_tut_molecule_text) {
                    tapAnchor(TutorialAnchor.simple("shape"))
                    val r = moleculeRect()
                    val s = param(MOLECULE, r, mapOf("formula" to "H2O", "view" to "structural"), MOLECULE_ID) ?: return@TutorialStep
                    drag(r.topLeft, r.bottomRight, 600) { _, _ -> listOf(s) }
                    pause(500)
                    for (f in listOf("CO2", "CH4", "C2H5OH")) redraw(MOLECULE_ID, MOLECULE, r, mapOf("formula" to f, "view" to "structural"))
                },
                // Bohr atomu atom numarasından: C (6) → O (8) → Na (11)
                TutorialStep(TutorialAnchor.simple("shape"), R.string.v52_tut_atom, R.string.v52_tut_atom_text) {
                    tapAnchor(TutorialAnchor.simple("shape"))
                    val r = bohrRect()
                    val s = param(BOHR, r, mapOf("z" to "6"), BOHR_ID, width = 2f) ?: return@TutorialStep
                    drag(r.topLeft, r.bottomRight, 500) { _, _ -> listOf(s) }
                    pause(400)
                    redraw(BOHR_ID, BOHR, r, mapOf("z" to "8"))
                    redraw(BOHR_ID, BOHR, r, mapOf("z" to "11"))
                },
                // Eğik atış hız ve açıdan; menzil ve yükseklik şeklin üstünde hesaplanır
                TutorialStep(TutorialAnchor.simple("shape"), R.string.v52_tut_projectile, R.string.v52_tut_projectile_text) {
                    tapAnchor(TutorialAnchor.simple("shape"))
                    val r = projectileRect()
                    val s = param(PROJECTILE, r, mapOf("v" to "20", "theta" to "45"), PROJECTILE_ID) ?: return@TutorialStep
                    drag(r.topLeft, r.bottomRight, 600) { _, _ -> listOf(s) }
                    pause(400)
                    redraw(PROJECTILE_ID, PROJECTILE, r, mapOf("v" to "20", "theta" to "60"))
                    redraw(PROJECTILE_ID, PROJECTILE, r, mapOf("v" to "30", "theta" to "60"))
                },
                // Formül hesaplama: yuvalara değer yazılır, sonuç eklenir
                TutorialStep(TutorialAnchor.simple("formula"), R.string.v52_tut_calc, R.string.v52_tut_calc_text) {
                    tapAnchor(TutorialAnchor.simple("formula"))
                    val left = w * 0.06f; val top = calcTop(); val size = u * 0.05f
                    val f = formula("geometry.circle_area", emptyMap(), left, top, size, CALC_ID) ?: return@TutorialStep
                    place(Offset(left + u * 0.1f, top + size), listOf(f))
                    pause(700)
                    reformula(CALC_ID, "geometry.circle_area", mapOf("r" to "3"), left, top, size)
                    val top2 = top + size * 2.2f
                    val f2 = formula("physics.newton2", mapOf("m" to "4", "a" to "3"), left, top2, size, CALC2_ID) ?: return@TutorialStep
                    place(Offset(left + u * 0.1f, top2 + size), listOf(f2))
                },
                // Değer değişince sonuç yenilenir
                TutorialStep(TutorialAnchor.CANVAS, R.string.v52_tut_calc_edit, R.string.v52_tut_calc_edit_text) {
                    val left = w * 0.06f; val top = calcTop(); val size = u * 0.05f
                    reformula(CALC_ID, "geometry.circle_area", mapOf("r" to "5"), left, top, size)
                    reformula(CALC2_ID, "physics.newton2", mapOf("m" to "6", "a" to "3"), left, top + size * 2.2f, size)
                },
            ),
        )
    }
}
