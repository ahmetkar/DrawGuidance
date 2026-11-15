package com.karapp.drawguidance.ui.screens.canvasedit.tutorial

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.content.formula.Formula
import com.karapp.drawguidance.content.formula.FormulaEval
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.karapp.drawguidance.TestDrawGuidanceApp

/** v52 not alma "Bilim şekilleri ve hesaplama" eğitimi: şekiller kurulur, formüllerin sonucu eklenir. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V52TutorialTest {
    @Test fun scienceGroupInNotes() {
        val g = TutorialScripts.groups(CanvasKind.NOTES).first { it.id == "notes_science_v52" }
        assertEquals(5, g.steps.size)
    }

    @Test fun shapesBuild() {
        val style = ShapeStyle("#263238", 2.5f)
        for ((kind, params) in listOf(
            "lib.sci.chem.molecule" to mapOf("formula" to "C2H5OH", "view" to "structural"),
            "lib.sci.chem.bohr_atom" to mapOf("z" to "11"),
            "lib.sci.phys.projectile" to mapOf("v" to "30", "theta" to "60"),
        )) {
            val s = ShapeFactory.build(kind, Offset(0f, 0f), Offset(400f, 250f), style, null, "x", 0, params = params)
            assertNotNull(kind, s)
            assertTrue(kind, s!!.shape!!.subpaths.isNotEmpty())
        }
    }

    @Test fun formulasGetResults() {
        val circle = TutorialV52.calcSource("geometry.circle_area", mapOf("r" to "3"))!!
        assertTrue(circle, circle.contains(FormulaEval.RESULT_MARK))
        assertTrue(Formula.plainText(circle), Formula.plainText(circle).contains("28"))
        val newton = TutorialV52.calcSource("physics.newton2", mapOf("m" to "4", "a" to "3"))!!
        assertTrue(Formula.plainText(newton), Formula.plainText(newton).contains("12"))
    }
}
