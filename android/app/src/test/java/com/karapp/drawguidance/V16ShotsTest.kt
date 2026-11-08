package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.drawing.FormulaRenderer
import com.karapp.drawguidance.ui.drawing.VectorEdit
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v16 görsel kontrolü (DG_SHOTS verilince ekran görüntüsü): vektör düzenleme, formül üstünde düzenleme, yazı çubuğu. */
abstract class V16ShotsBase : V15ShotsBase() {

    private fun seed(name: String, vararg strokes: com.karapp.drawguidance.data.model.Stroke) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val repository = GlobalContext.get().get<CanvasRepository>()
        runBlocking {
            val c = repository.createCanvas(name, 1240, 1754, "#FFFFFF", false, kind = CanvasKind.NOTES)
            repository.saveStrokes(c.localId, listOf(strokes.toList()))
        }
        waitForText(name)
        openCanvas(name)
    }

    protected fun vector(prefix: String) {
        seed("Vektör", shape("h", "HEXAGON", 300f, 260f, 940f, 820f, 0), shape("c", "CIRCLE", 340f, 980f, 900f, 1540f, 1))
        mode("Seç")
        val size = canvasSize()
        fun at(p: Offset) = fit(size, 1240f, 1754f, p.x, p.y)
        val hex = canvas().strokes.first { it.id == "h" }
        val nodes = VectorEdit.graph(hex).nodes.map { it.point }.sortedBy { kotlin.math.atan2(it.y - 540f, it.x - 620f) }
        tap(at(nodes[0]))
        editButton("Şekli düzenle")
        waitForText("Şekli Düzenle: Noktalar")
        settle()
        shot("$prefix-1-nodes")
        tap(at(nodes[1]))
        tap(at(nodes[2]))
        settle()
        shot("$prefix-2-selected")
        compose.onNode(hasContentDescription("Çizgi çiz") and hasAnyAncestor(hasTestTag("node_tool_controls"))).performClick()
        tap(at(nodes[0]))
        tap(at(nodes[3]))
        settle()
        shot("$prefix-3-connect")
        compose.onNode(hasContentDescription("Çizgiler") and hasAnyAncestor(hasTestTag("tool_row_context"))).performClick()
        val mid = (nodes[0] + nodes[3]) / 2f
        tap(at(mid))
        settle()
        shot("$prefix-4-line")
        // Ortadaki tutamaktan sürükle: kavis
        drag(at(mid), Offset(0f, -90f))
        settle()
        shot("$prefix-5-bent")
    }

    protected fun formulaInline(prefix: String) {
        val source = "x_{1,2} = \\frac{-\\slot{b}{b} \\pm \\sqrt{\\slot{b}{b}^2 - 4\\slot{a}{a}\\slot{c}{c}}}{2\\slot{a}{a}}"
        seed("Formül", formula("f", source, 80f, 120f, 400f, 0))
        mode("Seç")
        val size = canvasSize()
        val f = canvas().strokes.single()
        val c = f.points.let { p -> Offset(p.map { it.x }.average().toFloat(), p.map { it.y }.average().toFloat()) }
        tap(fit(size, 1240f, 1754f, c.x, c.y))
        waitForText("Formülü düzenle")
        val q = FormulaRenderer.slotQuad(canvas().strokes.single(), "c")!!
        val sc = Offset(q.map { it.x }.average().toFloat(), q.map { it.y }.average().toFloat())
        tap(fit(size, 1240f, 1754f, sc.x, sc.y))
        waitFor { compose.onAllNodesWithTag("formula_inline_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("formula_inline_editor"))).performTextReplacement("12")
        settle()
        shot("$prefix-1-slot")
    }

    protected fun textBar(prefix: String) {
        seed("Yazı")
        val size = canvasSize()
        addMenu("Yazı ekle")
        waitForText("Yazı eklemek için tuvale dokun", substring = true)
        tap(Offset(size.x * 0.2f, size.y * 0.55f))
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor"))).performTextInput("Newton'un 2. yasası")
        settle()
        shot("$prefix-1-text-bar")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V16VectorShotsTest : V16ShotsBase() {
    @Test fun vector() = vector("v16-vector")
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V16FormulaShotsTest : V16ShotsBase() {
    @Test fun formula() = formulaInline("v16-formula")
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V16TextShotsTest : V16ShotsBase() {
    @Test fun text() = textBar("v16-text")
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w891dp-h411dp-land-xhdpi")
class V16LandTextShotsTest : V16ShotsBase() {
    @Test fun text() = textBar("v16-land-text")
}
