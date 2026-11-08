package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapePath
import com.karapp.drawguidance.ui.drawing.VectorEdit
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.components.SIMPLE_NAME_MS
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.sin

/** v37 (9) basit düzenlemede şekiller çok az noktayla açılır; çok parçalı şekilde toplam da sınırlı. */
class V37SimpleNodesTest {
    private val style = ShapeStyle(strokeColorHex = "#1E1E1E", strokeWidth = 4f)

    private fun polygon(corners: Int, cx: Float, cy: Float, r: Float): ShapePath {
        val pts = (0..corners).map { i ->
            val a = i % corners / corners.toDouble() * Math.PI * 2
            Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
        }
        return ShapePath(pts, closed = true)
    }

    @Test fun twelveCornersOpenWithFewPoints() {
        val s = ShapeFactory.fromPaths("PATH", listOf(polygon(12, 200f, 200f, 150f)), style, null, "s", 0)!!
        val coarse = VectorEdit.graph(s, coarse = true)
        val normal = VectorEdit.graph(s)
        assertEquals("olağan düzenlemede bütün köşeler", 12, normal.nodes.size)
        assertTrue("basit: ${coarse.nodes.size}", coarse.nodes.size in 3..VectorEdit.SIMPLE_NODES)
    }

    @Test fun multiPartShapeStaysAroundTotalLimit() {
        val parts = (0 until 5).map { polygon(12, 150f + it * 320f, 200f, 120f) }
        val s = ShapeFactory.fromPaths("PATH", parts, style, null, "s", 0)!!
        val g = VectorEdit.graph(s, coarse = true)
        // Parça başına en az 3 (kapalı), toplam ~16
        assertTrue("toplam: ${g.nodes.size}", g.nodes.size <= maxOf(VectorEdit.SIMPLE_TOTAL, parts.size * 3))
        assertTrue("toplam: ${g.nodes.size}", g.nodes.size >= parts.size * 2)
    }
}

/**
 * v37 (9) basit modlarda dokunulan düğmenin adı yanında görünür, bir süre sonra kaybolur; (9.1) basit resim ve
 * çocuk modunda kağıdın kenarına basılı tutunca ya da Genişlet düğmesiyle çizim alanı genişletilir. Basit notta yok.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V37SimpleModesTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun openKind(name: String, kind: CanvasKind) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    /** Ad yazan hap simge düğmesinden (36 dp) belirgin geniştir. */
    private fun named(id: String): Boolean {
        val nodes = compose.onAllNodes(hasTestTag("simple_tool_$id")).fetchSemanticsNodes()
        return nodes.isNotEmpty() && nodes.first().size.width > with(compose.density) { 56.dp.toPx() }
    }

    private fun selected(id: String) = compose.onAllNodes(hasTestTag("simple_tool_$id") and isSelected()).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun tappedButtonShowsItsNameForAWhile() {
        // v48 dikey ekranda bütün düğmelerin adı simgenin yanında sürekli yazar (dokununca açılıp kapanmaz)
        openKind("Adlar", CanvasKind.SIMPLE_ART)
        val size = canvasSize()
        drag(Offset(size.x * 0.4f, size.y * 0.5f), Offset(size.x * 0.2f, 0f))
        waitFor { canvas().strokes.size == 1 }
        assertTrue(named("undo") && named("redo") && named("brush"))
        // v49 geri al/yinele artık sabit hap (yatay kaydırma dışında, hep görünür) — kaydırmaya gerek yok
        compose.onNodeWithTag("simple_tool_undo").performClick()
        waitFor { canvas().strokes.isEmpty() }
        assertFalse("geri al seçili olmaz", selected("undo"))
        shot("v37-simple-name")
        compose.mainClock.advanceTimeBy(SIMPLE_NAME_MS + 200)
        compose.waitForIdle()
        assertTrue(named("undo"))
        assertTrue(selected("brush"))
    }

    @Test
    fun holdingPaperEdgeOpensExpandAndDragGrowsCanvas() {
        openKind("Kenar", CanvasKind.SIMPLE_ART)
        assertTrue(exists("simple_tool_expand"))
        assertEquals(EditMode.DRAW, vm().mode.value)
        // Sağ kenara basılı tut: genişletme modu, Genişlet düğmesi seçili; çizim yapılmaz
        longPress(screenOf(1000f, 500f))
        waitFor { vm().mode.value == EditMode.EXPAND }
        waitFor { selected("expand") }
        assertTrue(canvas().strokes.isEmpty())
        // Kenarı dışa sürükle: kağıt genişler
        val w0 = canvas().width
        drag(screenOf(1000f, 500f), Offset(120f, 0f))
        waitFor { canvas().width > w0 }
        assertEquals(1000, canvas().height)
        shot("v37-simple-expand")
        // Düğmeye yeniden dokununca çizime dönülür; düğmeyle doğrudan da açılır
        compose.onNodeWithTag("simple_tool_expand").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.DRAW }
        compose.onNodeWithTag("simple_tool_expand").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.EXPAND }
    }

    @Test
    fun kidsHaveExpand() {
        openKind("Çocuk genişlet", CanvasKind.KIDS)
        assertTrue(exists("simple_tool_expand"))
        compose.onNodeWithTag("simple_tool_expand").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.EXPAND }
        longPress(screenOf(500f, 500f))
        assertEquals(EditMode.EXPAND, vm().mode.value)
    }

    @Test
    fun simpleNotesHaveNoExpand() {
        openKind("Basit not kenar", CanvasKind.SIMPLE_NOTES)
        assertFalse(exists("simple_tool_expand"))
        // Kenara basılı tutmak genişletme açmaz
        longPress(screenOf(canvas().width.toFloat(), canvas().height / 2f))
        assertTrue(vm().mode.value != EditMode.EXPAND)
    }
}
