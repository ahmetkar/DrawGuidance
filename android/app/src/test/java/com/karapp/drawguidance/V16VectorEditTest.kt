package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapeFactory.EditablePath
import com.karapp.drawguidance.ui.drawing.ShapePath
import com.karapp.drawguidance.ui.drawing.VectorEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * v16 vektör düzenleme (saf mantık): vektör noktaları ve çizgiler, nokta taşıma (bağlı çizgiler gelir), çizgiye nokta
 * ekleme, serbest nokta, iki nokta arasına çizgi (yol uzar / birleşir / kapanır), kavis, eğim, kesme ve silme.
 */
class V16VectorEditTest {

    private val style = ShapeStyle(strokeColorHex = "#1E1E1E", strokeWidth = 4f, fillColorHex = "#BBDEFB")

    private fun shape(vararg paths: ShapePath): Stroke = ShapeFactory.fromPaths("PATH", paths.toList(), style, null, "s", 0)!!

    private fun square() = shape(
        ShapePath(listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 100f), Offset(0f, 100f), Offset(0f, 0f)), closed = true),
    )

    private fun rebuilt(stroke: Stroke, paths: List<EditablePath>): Stroke = ShapeFactory.fromEditable(stroke, paths)!!

    private fun near(a: Offset, b: Offset, tol: Float = 0.05f) = abs(a.x - b.x) <= tol && abs(a.y - b.y) <= tol

    @Test fun squareHasFourNodesAndFourLines() {
        val g = VectorEdit.graph(square())
        assertEquals(4, g.nodes.size)
        assertEquals(4, g.edges.size)
        assertTrue(g.edges.all { it.points.size == 2 })
        // Her köşeye iki çizgi bağlı
        assertTrue(g.nodes.all { g.degree(it.id) == 2 })
    }

    @Test fun circleShowsAFewNodesNotEverySample() {
        val pts = (0 until 96).map { i -> val a = i / 96.0 * Math.PI * 2; Offset(200f + 150f * cos(a).toFloat(), 200f + 150f * sin(a).toFloat()) }
        val g = VectorEdit.graph(shape(ShapePath(pts + pts.first(), closed = true)))
        assertTrue("çember noktası: ${g.nodes.size}", g.nodes.size in 6..24)
        assertEquals(g.nodes.size, g.edges.size)
        assertTrue(g.edges.all { it.curved })
    }

    @Test fun movingACornerStretchesItsLines() {
        val s = square()
        val g = VectorEdit.graph(s)
        val corner = g.nodeNear(Offset(100f, 100f))!!
        val moved = VectorEdit.graph(rebuilt(s, VectorEdit.moveNodes(g, mapOf(corner.id to Offset(20f, 30f)))))
        assertNotNull(moved.nodeNear(Offset(120f, 130f)))
        assertNull(moved.nodeNear(Offset(100f, 100f)))
        // Diğer köşeler yerinde, iki çizgi yeni köşeye bağlı
        listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(0f, 100f)).forEach { assertNotNull(moved.nodeNear(it)) }
        assertEquals(2, moved.degree(moved.nodeNear(Offset(120f, 130f))!!.id))
    }

    @Test fun movingANodeBendsACurvedLineSmoothly() {
        // Kavisli çizgi: uç taşınınca ara noktalar uzaklığa göre gelir (kırılmaz)
        val s = shape(ShapePath((0..10).map { Offset(it * 10f, if (it in 1..9) 20f else 0f) }, nodes = listOf(0, 10)))
        val g = VectorEdit.graph(s)
        assertEquals(1, g.edges.size)
        val end = g.nodeNear(Offset(100f, 0f))!!
        val out = VectorEdit.moveNodes(g, mapOf(end.id to Offset(0f, 50f))).single().points
        assertTrue(near(out.first(), Offset(0f, 0f)))
        assertTrue(near(out.last(), Offset(100f, 50f)))
        val mid = out[5]
        assertTrue("orta nokta yarı yarıya gelir: $mid", mid.y in 40f..50f)
    }

    @Test fun insertingANodeOnALineSplitsIt() {
        val s = square()
        val g = VectorEdit.graph(s)
        val hit = g.edgeAt(Offset(50f, 2f), 5f)!!
        val (paths, at) = VectorEdit.insertNode(g, hit)
        assertTrue(near(at, Offset(50f, 0f)))
        val g2 = VectorEdit.graph(rebuilt(s, paths))
        assertEquals(5, g2.nodes.size)
        assertEquals(5, g2.edges.size)
        // Uca çok yakın dokunuş yeni nokta eklemez
        val corner = g.edgeAt(Offset(0.3f, 0.2f), 5f)!!
        assertEquals(4, VectorEdit.graph(rebuilt(s, VectorEdit.insertNode(g, corner).first)).nodes.size)
    }

    @Test fun freeNodesConnectIntoLinesAndDisappearAsFreeNodes() {
        val s = shape(ShapePath(listOf(Offset(0f, 0f), Offset(100f, 0f)), nodes = listOf(0, 1)))
        var g = VectorEdit.graph(s)
        // Serbest nokta: çizilmez ama vektör noktasıdır
        var paths = VectorEdit.addFreeNode(g, Offset(50f, 80f))
        var st = rebuilt(s, paths)
        g = VectorEdit.graph(st)
        assertEquals(3, g.nodes.size)
        assertTrue(g.isFree(g.nodeNear(Offset(50f, 80f))!!.id))
        assertEquals(1, g.edges.size)
        // Açık yolun ucundan serbest noktaya çizgi: yol uzar, serbest nokta kalkar
        paths = VectorEdit.connect(g, Offset(100f, 0f), Offset(50f, 80f))!!
        st = rebuilt(st, paths)
        g = VectorEdit.graph(st)
        assertEquals(1, g.paths.count { it.label == null && it.tag != VectorEdit.VNODE })
        assertTrue(g.paths.none { it.tag == VectorEdit.VNODE })
        assertEquals(2, g.edges.size)
        // Aynı yolun iki ucu bağlanınca yol kapanır (üçgen)
        paths = VectorEdit.connect(g, Offset(50f, 80f), Offset(0f, 0f))!!
        st = rebuilt(st, paths)
        g = VectorEdit.graph(st)
        assertTrue(g.paths.single().closed)
        assertEquals(3, g.edges.size)
        assertEquals(3, g.nodes.size)
        // Var olan düz çizgi yeniden çizilmez
        assertNull(VectorEdit.connect(g, Offset(0f, 0f), Offset(100f, 0f)))
    }

    @Test fun connectingTwoOpenPathsMergesThem() {
        val s = shape(
            ShapePath(listOf(Offset(0f, 0f), Offset(50f, 0f)), nodes = listOf(0, 1)),
            ShapePath(listOf(Offset(100f, 0f), Offset(150f, 0f)), nodes = listOf(0, 1)),
        )
        val g = VectorEdit.graph(s)
        val merged = VectorEdit.graph(rebuilt(s, VectorEdit.connect(g, Offset(50f, 0f), Offset(100f, 0f))!!))
        assertEquals(1, merged.paths.size)
        assertEquals(3, merged.edges.size)
        assertEquals(4, merged.nodes.size)
        // İki ayrı noktayı bağlamak yeni bir çizgi parçası ekler
        val cross = VectorEdit.connect(g, Offset(0f, 0f), Offset(75f, 60f))
        assertNotNull(cross)
    }

    @Test fun bendingALineKeepsItsEndsAndPassesThroughTheHandle() {
        val s = square()
        val g = VectorEdit.graph(s)
        val top = g.edgeBetween(Offset(0f, 0f), Offset(100f, 0f))!!
        val g2 = VectorEdit.graph(rebuilt(s, VectorEdit.bendEdge(g, top, Offset(50f, -40f))))
        assertEquals(4, g2.nodes.size)
        val bent = g2.edgeBetween(Offset(0f, 0f), Offset(100f, 0f))!!
        assertTrue(bent.curved)
        assertTrue("orta: ${bent.middle()}", near(bent.middle(), Offset(50f, -40f), 3f))
        // Düzleştir: kavis kalkar
        val g3 = VectorEdit.graph(rebuilt(s, VectorEdit.straighten(g2, bent)))
        assertTrue(!g3.edgeBetween(Offset(0f, 0f), Offset(100f, 0f))!!.curved)
    }

    @Test fun turningALineChangesItsSlopeAndDragsNeighbours() {
        val s = square()
        val g = VectorEdit.graph(s)
        val top = g.edgeBetween(Offset(0f, 0f), Offset(100f, 0f))!!
        assertEquals(0f, top.angle(), 0.01f)
        val turned = VectorEdit.graph(rebuilt(s, VectorEdit.rotateEdge(g, top, 90f)))
        // Orta (50,0) çevresinde 90°: uçlar (50,-50) ve (50,50)
        assertNotNull(turned.nodeNear(Offset(50f, -50f)))
        assertNotNull(turned.nodeNear(Offset(50f, 50f)))
        val e = turned.edgeBetween(Offset(50f, -50f), Offset(50f, 50f))!!
        assertEquals(90f, abs(e.angle()), 0.1f)
        // Komşu çizgiler döndürülen uçlara bağlı kalır
        assertEquals(4, turned.edges.size)
        assertEquals(4, turned.nodes.size)
        // Yataya getir
        val flat = VectorEdit.graph(rebuilt(s, VectorEdit.setAngle(turned, e, 0f)))
        val back = flat.edges.first { it.path == e.path && abs(it.start.y - it.end.y) < 0.1f && abs(it.start.x - it.end.x) > 50f }
        assertEquals(0f, back.angle() % 180f, 0.1f)
    }

    @Test fun cuttingACornerSeparatesItWithItsLines() {
        val s = square()
        val g = VectorEdit.graph(s)
        val corner = g.nodeNear(Offset(100f, 100f))!!
        val (rest, piece) = VectorEdit.cutNodes(g, setOf(corner.id))
        assertTrue(VectorEdit.drawable(rest))
        assertTrue(VectorEdit.drawable(piece))
        val r = VectorEdit.graph(rebuilt(s, rest))
        val p = VectorEdit.graph(rebuilt(s, piece))
        // Kalan: (100,0)-(0,0)-(0,100) açık yol; kesilen: (100,0)-(100,100)-(0,100)
        assertEquals(2, r.edges.size)
        assertEquals(2, p.edges.size)
        assertNull(r.nodeNear(Offset(100f, 100f)))
        assertNotNull(p.nodeNear(Offset(100f, 100f)))
        assertTrue(r.paths.none { it.closed } && p.paths.none { it.closed })
        // Silme: kalan kısım
        val deleted = VectorEdit.graph(rebuilt(s, VectorEdit.deleteEdge(g, g.edgeBetween(Offset(0f, 0f), Offset(100f, 0f))!!)))
        assertEquals(3, deleted.edges.size)
        assertTrue(deleted.paths.single().let { !it.closed && it.points.size == 4 })
    }

    @Test fun nodesSurviveTheCodec() {
        val s = square()
        val g = VectorEdit.graph(s)
        val (paths, _) = VectorEdit.insertNode(g, g.edgeAt(Offset(30f, 0f), 5f)!!)
        val edited = rebuilt(s, paths)
        val decoded = ContentCodec.decode(ContentCodec.encode(listOf(edited))).single()
        assertEquals(edited.copy(layerId = null), decoded.copy(layerId = null))
        assertEquals(listOf(0, 1, 2, 3, 4), decoded.shape!!.subpaths.single().nodes)
    }
}
