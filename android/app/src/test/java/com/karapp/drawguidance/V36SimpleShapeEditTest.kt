package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapePath
import com.karapp.drawguidance.ui.drawing.VectorEdit
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * v36 (7) basit düzenleme (saf mantık): basit çizim türlerinde şekil az noktayla açılır; Noktaları azalt görünümü
 * değiştirmeden nokta sayısını düşürür, birleşim yerlerini ve açık uçları korur.
 */
class V36SimpleVectorTest {
    private val style = ShapeStyle(strokeColorHex = "#1E1E1E", strokeWidth = 4f)

    private fun shape(vararg paths: ShapePath): Stroke = ShapeFactory.fromPaths("PATH", paths.toList(), style, null, "s", 0)!!

    private fun circle(n: Int = 96, r: Float = 150f): List<Offset> =
        (0 until n).map { i -> val a = i / n.toDouble() * Math.PI * 2; Offset(200f + r * cos(a).toFloat(), 200f + r * sin(a).toFloat()) }

    /** Dalgalı açık çizgi: çok sayıda belirgin nokta. */
    private fun wave(n: Int = 160): List<Offset> = (0 until n).map { i -> Offset(i * 4f, 60f * sin(i / 6.0).toFloat()) }

    @Test fun coarseGraphShowsAtMostEightNodesPerPart() {
        val c = shape(ShapePath(circle() + circle().first(), closed = true))
        val normal = VectorEdit.graph(c)
        val coarse = VectorEdit.graph(c, coarse = true)
        assertTrue("sade: ${coarse.nodes.size}, olağan: ${normal.nodes.size}", coarse.nodes.size in 3..VectorEdit.SIMPLE_NODES)
        assertTrue(coarse.nodes.size <= normal.nodes.size)
        assertEquals(coarse.nodes.size, coarse.edges.size)
        // Dalgalı çizgi de en çok 8 noktayla; uçlar korunur
        val w = shape(ShapePath(wave()))
        val gw = VectorEdit.graph(w, coarse = true)
        assertTrue("dalga: ${gw.nodes.size}", gw.nodes.size in 2..VectorEdit.SIMPLE_NODES)
        assertNotNull(gw.nodeNear(wave().first()))
        assertNotNull(gw.nodeNear(wave().last()))
    }

    @Test fun fewPointShapesAndWrittenNodesStayAsTheyAre() {
        // Az noktalı (kare, yıldız) şekillerde bütün köşeler nokta kalır
        val square = shape(ShapePath(listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 100f), Offset(0f, 100f), Offset(0f, 0f)), closed = true))
        assertEquals(4, VectorEdit.graph(square, coarse = true).nodes.size)
        // Yazılı (kullanıcının eklediği) noktalar sade görünümde de silinmez
        val written = shape(ShapePath(wave(), nodes = (0 until 160 step 10).toList() + 159))
        assertEquals(17, VectorEdit.graph(written, coarse = true).nodes.size)
    }

    @Test fun simplifyKeepsTheLookAndDropsPoints() {
        val written = shape(ShapePath(wave(), nodes = (0 until 160 step 5).toList() + 159))
        val g = VectorEdit.graph(written, coarse = true)
        assertTrue(VectorEdit.canSimplify(g))
        val out = VectorEdit.simplifyNodes(g, VectorEdit.diagonal(written))
        val rebuilt = ShapeFactory.fromEditable(written, out)!!
        val g2 = VectorEdit.graph(rebuilt, coarse = true)
        assertTrue("azaltılmış: ${g2.nodes.size}", g2.nodes.size <= VectorEdit.SIMPLE_NODES)
        assertFalse(VectorEdit.canSimplify(g2))
        // Görünüm aynı: bütün çizim noktaları yerinde
        assertEquals(g.paths.single().points, out.single().points)
        // Uçlar korunur
        assertNotNull(g2.nodeNear(wave().first()))
        assertNotNull(g2.nodeNear(wave().last()))
    }

    @Test fun simplifyKeepsJunctionsBetweenParts() {
        // İki parça ortadaki bir noktada birleşir: azaltınca birleşim noktası kalır (birlikte taşınır)
        val pts = wave()
        val joint = pts[80]
        val a = ShapePath(pts, nodes = (0 until 160 step 4).toList() + 159)
        val b = ShapePath(listOf(joint, joint + Offset(0f, 200f)))
        val s = shape(a, b)
        val g = VectorEdit.graph(s, coarse = true)
        val out = VectorEdit.simplifyNodes(g, VectorEdit.diagonal(s))
        val g2 = VectorEdit.graph(ShapeFactory.fromEditable(s, out)!!, coarse = true)
        val node = g2.nodeNear(joint)
        assertNotNull(node)
        assertEquals(3, g2.degree(node!!.id))
    }

    @Test fun nothingToSimplifyReturnsSameParts() {
        val square = shape(ShapePath(listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 100f), Offset(0f, 0f)), closed = true))
        val g = VectorEdit.graph(square, coarse = true)
        assertFalse(VectorEdit.canSimplify(g))
        assertTrue(VectorEdit.simplifyNodes(g, VectorEdit.diagonal(square)) === g.paths)
    }
}

/**
 * v36 (7) basit resimde Şekli düzenle düğmesi: seçili şekil az noktalı düzenlemede açılır; seçim yokken bekler ve
 * dokunulan şekli açar; yeniden basınca bekleme kalkar. Basit notta düğme yoktur.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V36SimpleShapeEditTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun openKind(name: String, kind: CanvasKind) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun shapeStroke() = vm().canvas.value?.strokes?.firstOrNull { it.shape != null }

    @Test
    fun simpleArtEditsShapeWithFewPoints() {
        openKind("Basit şekil", CanvasKind.SIMPLE_ART)
        assertTrue(exists("simple_tool_shape_edit"))
        compose.runOnIdle {
            vm().selectShapeId("CIRCLE")
            vm().addShapeAt("g1", Offset(500f, 500f))
        }
        waitFor { shapeStroke() != null }
        // Şekil seçiliyken Şekli düzenle → doğrudan nokta düzenleme, düğme seçili
        compose.onNodeWithTag("simple_tool_shape_edit").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.SHAPE_EDIT && vm().shapeTool.value == ShapeTool.NODES }
        waitFor { compose.onAllNodes(hasTestTag("simple_tool_shape_edit") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodes(hasTestTag("simple_tool_select") and isSelected()).fetchSemanticsNodes().isEmpty())
        val g = VectorEdit.graph(shapeStroke()!!, coarse = true)
        assertTrue("çember noktası: ${g.nodes.size}", g.nodes.size <= VectorEdit.SIMPLE_NODES)
        // Bir noktayı sürükle: şekil değişir, nokta sayısı az kalır
        val node = g.nodes.first()
        val before = shapeStroke()!!.points
        val from = screenOf(node.point.x, node.point.y)
        drag(from, screenOf(node.point.x + 80f, node.point.y + 40f) - from)
        waitFor { shapeStroke()!!.points != before }
        assertTrue(VectorEdit.graph(shapeStroke()!!, coarse = true).nodes.size <= VectorEdit.SIMPLE_NODES)
        // Noktaları azalt düğmesi basit düzenlemede görünür
        assertTrue(compose.onAllNodesWithContentDescription("Noktaları azalt").fetchSemanticsNodes().isNotEmpty())
        shot("v36-simple-shape-edit")
        // Bitti → Seç; seçim yokken Şekli düzenle bekler, dokunulan şekil açılır
        compose.runOnIdle { vm().finishAction() }
        waitFor { vm().mode.value == EditMode.SELECT }
        compose.runOnIdle { vm().chooseMode(EditMode.SELECT) }
        compose.onNodeWithTag("simple_tool_shape_edit").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.EDIT_PICK && vm().pendingShapeTool.value == ShapeTool.NODES }
        // Yeniden basınca bekleme kalkar
        compose.onNodeWithTag("simple_tool_shape_edit").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.SELECT && vm().pendingShapeTool.value == null }
    }

    @Test
    fun simpleNotesHaveNoShapeEditButton() {
        openKind("Basit not", CanvasKind.SIMPLE_NOTES)
        assertFalse(exists("simple_tool_shape_edit"))
    }
}
