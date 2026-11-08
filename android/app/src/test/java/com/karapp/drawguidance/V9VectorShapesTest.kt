package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.GraphSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.DiagramRenderer
import com.karapp.drawguidance.ui.drawing.Formula
import com.karapp.drawguidance.ui.drawing.GraphShapes
import com.karapp.drawguidance.ui.drawing.ShapeEditor
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapeKind
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * v9 vektör şekiller: tek eleman olarak kurulum, veritabanında vektör bilgisiyle saklanma,
 * biçim değişiklikleri, şekil düzenleme işlemleri, formül ayrıştırma ve diyagram yerleşimi.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V9VectorShapesTest {

    @org.junit.After fun tearDown() = org.koin.core.context.stopKoin()

    private val style = ShapeStyle(strokeColorHex = "#1E88E5", strokeWidth = 5f, fillColorHex = "#FFE082", cornerRadius = 12f)

    private fun build(kind: ShapeKind, graph: GraphSpec? = null) = ShapeFactory.build(
        kind, Offset(100f, 100f), Offset(400f, 400f), style, graph, id = "s1", order = 0,
    )!!

    @Test fun shapeIsOneVectorElementWithItsSubpathsAndStyle() {
        val cube = build(ShapeKind.CUBE)
        assertEquals(BrushType.SHAPE, cube.brushType)
        assertEquals("CUBE", cube.shape!!.kind)
        assertEquals(style, cube.shape!!.style)
        // Alt yolların nokta sayıları toplamı elemanın nokta sayısına eşittir
        assertEquals(cube.points.size, cube.shape!!.subpaths.sumOf { it.count })
        assertTrue("görünmeyen ayrıtlar kesik", cube.shape!!.subpaths.any { it.dashed })
        // Tek eleman: başka çizimlerle birleşmez
        val line = Stroke("l", listOf(StrokePoint(100f, 250f), StrokePoint(400f, 250f)), "#000000", BrushType.PENCIL, 6f, 1f, 1)
        assertEquals(2, ElementOps.index(listOf(cube, line)).elements.size)
    }

    @Test fun vectorInformationSurvivesTheDatabaseRoundTrip() {
        val shapes = listOf(ShapeKind.HEXAGON, ShapeKind.CUBE, ShapeKind.FUNCTION_PLOT, ShapeKind.PIE_CHART)
            .mapIndexed { i, kind ->
                build(kind, if (kind in GraphShapes.kinds) GraphSpec(xLabel = "t", yLabel = "v", formula = "sin(x)") else null)
                    .copy(id = "s$i", order = i)
            }
        val decoded = ContentCodec.decode(ContentCodec.encode(shapes))
        assertEquals(shapes.size, decoded.size)
        decoded.forEachIndexed { i, stroke ->
            assertEquals(shapes[i].shape!!.kind, stroke.shape!!.kind)
            assertEquals(shapes[i].shape!!.subpaths, stroke.shape!!.subpaths)
            assertEquals(style, stroke.shape!!.style)
            assertEquals(shapes[i].shape!!.graph, stroke.shape!!.graph)
            assertEquals(shapes[i].points.size, stroke.points.size)
        }
        // Sayfalar da saklanır (çalışma/not tuvali)
        val pages = listOf(shapes, emptyList(), shapes.take(1))
        assertEquals(pages, ContentCodec.decodePages(ContentCodec.encodePages(pages)))

        // Yazının dolgu (iç renk) ve kenarlık (çizgi rengi, kalınlığı) biçimi saklanır; kenarlıksız yazılar eskisi gibi okunur
        val outlined = com.karapp.drawguidance.data.model.TextSpec("Başlık", outlineColorHex = "#1E88E5", outlineWidth = 3f)
        val plain = com.karapp.drawguidance.data.model.TextSpec("Alt başlık")
        val texts = listOf(outlined, plain).mapIndexed { i, spec ->
            Stroke(
                "x$i",
                listOf(StrokePoint(0f, 0f), StrokePoint(200f, 0f), StrokePoint(200f, 60f), StrokePoint(0f, 60f)),
                "#1E1E1E", BrushType.TEXT, 40f, 1f, i, text = spec,
            )
        }
        val back = ContentCodec.decode(ContentCodec.encode(texts))
        assertEquals(outlined, back[0].text)
        assertEquals(plain, back[1].text)
        assertNull("kenarlıksız yazıda kenarlık rengi olmaz", back[1].text!!.outlineColorHex)
    }

    @Test fun styleAndGraphChangesApplyToTheSameElement() {
        val plot = build(ShapeKind.FUNCTION_PLOT, GraphSpec(formula = "x^2"))
        val wider = ShapeFactory.rebuild(plot, style.copy(strokeWidth = 12f), GraphSpec(xLabel = "zaman", yLabel = "hız", formula = "2x+1"))!!
        assertEquals(12f, wider.shape!!.style.strokeWidth, 0.01f)
        assertEquals("zaman", wider.shape!!.graph!!.xLabel)
        assertTrue("etiketler yazı çapası olarak saklanır", wider.shape!!.subpaths.any { it.label == "zaman" })
        // Şekil aynı kutuda kalır
        val a = ElementOps.bounds(plot)!!
        val b = ElementOps.bounds(wider)!!
        // v15: eski grafik yeni grafik motoruyla yeniden kurulur; aynı kutuya yerleşir (motorlar arası küçük pay)
        assertEquals(a.left, b.left, a.width * 0.06f)
        assertEquals(a.width, b.width, a.width * 0.06f)

        // Yeni motorla kurulan grafik art arda yeniden kurulunca kaymaz ve büyümez (kutu içeriğin payından bulunur)
        var g = com.karapp.drawguidance.ui.drawing.ShapeFactory.build(
            "graph.FUNCTION", Offset(100f, 100f), Offset(700f, 500f), style, GraphSpec(kind = "FUNCTION", formula = "x^2"), id = "g", order = 0,
        )!!
        val first = ElementOps.bounds(g)!!
        repeat(5) { i ->
            g = ShapeFactory.rebuild(g, style, g.shape!!.graph!!.copy(xLabel = "x$i", repeat = 2 + i % 2))!!
        }
        val last = ElementOps.bounds(g)!!
        assertEquals(first.left, last.left, 3f)
        assertEquals(first.top, last.top, 3f)
        assertEquals(first.width, last.width, 3f)
        assertEquals(first.height, last.height, 3f)
    }

    @Test fun shapeEditingAddsPointsSplitsVisiblyAndConnectsPoints() {
        val square = ShapeFactory.build(
            ShapeKind.SQUARE, Offset(100f, 100f), Offset(400f, 400f), style.copy(cornerRadius = 0f), null, id = "sq", order = 0,
        )!!
        val before = square.points.size
        // Nokta ekle: dokunulan kenara yeni nokta; şekil tek eleman kalır
        val added = ShapeEditor.insertPoint(square, Offset(250f, 102f))!!
        assertEquals(before + 1, added.points.size)
        assertEquals("sq", added.id)
        assertTrue("yeni nokta tutamak olarak görünür", ShapeEditor.handles(added).any { (it.point - Offset(250f, 100f)).getDistance() < 3f })
        // Noktaları çoğalt: her kenarın ortasına nokta
        val doubled = ShapeEditor.subdivide(square)!!
        assertEquals(before * 2, doubled.points.size)
        // Noktayı taşı
        val moved = ShapeEditor.movePoint(square, Offset(100f, 100f), Offset(60f, 60f), 12f)!!
        assertTrue(moved.points.any { abs(it.x - 60f) < 0.5f && abs(it.y - 60f) < 0.5f })

        // Parçala: kapalı kare açılır, kopan yerde görünür bir boşluk kalır
        val gap = ShapeEditor.splitGap(square)
        val split = ShapeEditor.split(square, Offset(250f, 100f), gap)!!
        assertEquals("parçalanan şekil yine tek eleman", "sq", split.id)
        assertTrue("kare açıldı", split.shape!!.subpaths.none { it.closed })
        val ends = ShapeEditor.handles(split).filter { it.openEnd }
        assertEquals("kopuk iki uç", 2, ends.size)
        val distance = (ends[0].point - ends[1].point).getDistance()
        assertTrue("uçlar arasında boşluk görünür: $distance", distance >= gap * 1.5f)
        // Açık yolu ikinci kez parçala: iki ayrı yol
        val twice = ShapeEditor.split(split, Offset(250f, 400f), gap)!!
        assertEquals(2, twice.shape!!.subpaths.size)

        // Bağla: iki kopuk uç doğrudan birleşir (yol yeniden kapanır)
        val joined = ShapeEditor.connect(split, ends[0].point, ends[1].point, 12f)!!
        assertTrue("yol yeniden kapandı", joined.shape!!.subpaths.any { it.closed })
        // Bağla: iki köşe arasına düz kenar (köşegen) eklenir
        val diagonal = ShapeEditor.connect(square, Offset(100f, 100f), Offset(400f, 400f), 12f)!!
        assertEquals(square.shape!!.subpaths.size + 1, diagonal.shape!!.subpaths.size)
        assertNull("uzakta nokta yoksa bağlanmaz", ShapeEditor.connect(square, Offset(250f, 250f), Offset(400f, 400f), 12f))
    }

    @Test fun formulaParserHandlesCommonExpressionsAndRejectsGarbage() {
        assertEquals(4.0, Formula.parse("y = x^2")!!.eval(2.0, 0.0), 1e-6)
        assertEquals(7.0, Formula.parse("2x + 3")!!.eval(2.0, 0.0), 1e-6)
        assertEquals(1.0, Formula.parse("sin(pi/2)")!!.eval(0.0, 0.0), 1e-6)
        assertEquals(6.0, Formula.parse("z = x*y")!!.eval(2.0, 3.0), 1e-6)
        assertEquals(1.5, Formula.parse("1,5")!!.eval(0.0, 0.0), 1e-6)
        assertNull(Formula.parse("2 +"))
        assertNull(Formula.parse("sin("))
        assertNull(Formula.parse("bilinmeyen(3)"))
        // Formül grafiğe çizilir
        val plot = build(ShapeKind.FUNCTION_PLOT, GraphSpec(formula = "x^2"))
        val plain = build(ShapeKind.FUNCTION_PLOT, GraphSpec(formula = ""))
        assertTrue("formül eğrisi eklenmeli", plot.points.size > plain.points.size)
    }

    @Test fun diagramsLayOutTheirNodesInsideTheUnitSquare() {
        DiagramKind.entries.forEach { kind ->
            val nodes = DiagramRenderer.layout(kind, DiagramRenderer.defaultNodes(kind))
            assertTrue("$kind düğüm üretmeli", nodes.size >= 2)
            nodes.forEach {
                // x, y düğümün sol-üst köşesidir
                assertTrue("$kind düğümü alanın içinde: $it", it.x >= -0.01f && it.x + it.w <= 1.01f)
                assertTrue("$kind düğümü alanın içinde: $it", it.y >= -0.01f && it.y + it.h <= 1.01f)
            }
            val edges = DiagramRenderer.defaultEdges(kind, nodes)
            assertTrue("$kind okları düğümlere bağlı", edges.all { e -> nodes.any { it.id == e.from } && nodes.any { it.id == e.to } })
        }
    }
}
