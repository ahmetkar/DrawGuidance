package com.karapp.drawguidance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.DiagramBox
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.DiagramSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.DiagramCatalog
import com.karapp.drawguidance.ui.drawing.DiagramRenderer
import com.karapp.drawguidance.ui.drawing.DiagramThemes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * v28 editoryal diyagramlar: her türün hazır örneği geçerli (oklar var olan adımlara bağlı, yerleşim alanın içinde),
 * nicel türlerde değerler yazıdan okunur, oklar çizilebilir yol verir ve her tür her temada hatasız çizilir.
 * DG_SHOTS verilince her türün editoryal (açık/koyu) ve pastel çizimi PNG olarak yazılır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V28DiagramTest {
    @After fun tearDown() = stopKoin()

    private fun spec(kind: DiagramKind, theme: String = DiagramThemes.EDITORIAL): DiagramSpec {
        val nodes = DiagramRenderer.layout(kind, DiagramRenderer.defaultNodes(kind))
        return DiagramSpec(
            kind = kind, nodes = nodes, edges = DiagramRenderer.defaultEdges(kind, nodes), style = ShapeStyle(), theme = theme,
            manual = DiagramCatalog.template(kind)?.manual ?: false,
        )
    }

    private fun stroke(spec: DiagramSpec, w: Float, h: Float, ox: Float = 0f, oy: Float = 0f) = Stroke(
        "d", listOf(StrokePoint(ox, oy), StrokePoint(ox + w, oy), StrokePoint(ox + w, oy + h), StrokePoint(ox, oy + h)),
        "#1E1E1E", BrushType.DIAGRAM, 4f, 1f, 0, diagram = spec,
    )

    @Test fun everyKindHasAValidTemplateAndLayout() {
        DiagramKind.entries.forEach { kind ->
            val s = spec(kind)
            assertTrue("$kind: adım var", s.nodes.isNotEmpty())
            assertEquals("$kind: kimlikler tekil", s.nodes.size, s.nodes.map { it.id }.toSet().size)
            val ids = s.nodes.map { it.id }.toSet()
            s.edges.forEach { e -> assertTrue("$kind: ok adımlara bağlı ${e.from}->${e.to}", e.from in ids && e.to in ids) }
            s.nodes.forEach { n ->
                assertTrue("$kind/${n.text}: genişlik", n.w > 0f && n.h > 0f)
                assertTrue("$kind/${n.text}: alanın içinde (${n.x}, ${n.y}, ${n.w}, ${n.h})",
                    n.x >= -0.02f && n.y >= -0.02f && n.x + n.w <= 1.02f && n.y + n.h <= 1.02f)
            }
            // Her okun çizilebilir yolu var
            s.edges.forEachIndexed { i, e ->
                val a = s.nodes.first { it.id == e.from }; val b = s.nodes.first { it.id == e.to }
                assertTrue("$kind: ok yolu", DiagramRenderer.routeOf(s, i, e, a, b).size >= 2)
            }
        }
        // Eski türler korunur (akış: 4 adım, 3 ok)
        val flow = spec(DiagramKind.FLOW)
        assertEquals(4, flow.nodes.size)
        assertEquals(3, flow.edges.size)
    }

    @Test fun valuesAreReadFromText() {
        val a = DiagramCatalog.parse("Kadro +64")
        assertEquals("Kadro", a.label); assertEquals(listOf(64f), a.values); assertTrue(a.signed)
        val b = DiagramCatalog.parse("Son =")
        assertTrue(b.computed)
        val c = DiagramCatalog.parse("Tasarım 2-5")
        assertEquals(listOf(2f, 5f), c.values)
        assertEquals("00–03", DiagramCatalog.label(DiagramKind.POLAR, "00–03 32"))
        assertEquals("Kampanya", DiagramCatalog.label(DiagramKind.SCATTER, "Kampanya 78 52"))
        assertEquals("Hız", DiagramCatalog.label(DiagramKind.RADAR, "Hız 9 6"))
        assertEquals("Son", DiagramCatalog.label(DiagramKind.WATERFALL, "Son ="))
        // Şelale: 240 + 64 + 32 − 38 − 22 = 276
        val wf = spec(DiagramKind.WATERFALL)
        assertEquals(276f, DiagramCatalog.waterfallLevels(wf.nodes).last().second, 0.01f)
        // Çubuk boyu değerle orantılı: Haz (210) Oca'dan (120) uzun
        val bars = spec(DiagramKind.BAR).nodes
        assertTrue(bars[5].h > bars[0].h)
        assertEquals(210f / 120f, bars[5].h / bars[0].h, 0.02f)
        // Gantt: aralık hafta ızgarasına
        val g = spec(DiagramKind.GANTT).nodes
        assertTrue("API (4-9) Araştırma'dan (1-3) uzun", g[2].w > g[0].w)
        // Sankey şeritleri değerle orantılı
        val sk = spec(DiagramKind.SANKEY)
        val ribbons = DiagramCatalog.ribbons(sk.nodes, sk.edges)
        assertEquals(sk.edges.size, ribbons.size)
        val r0 = ribbons[0]!!; val r1 = ribbons[1]!!
        assertEquals(5200f / 4000f, r0.h / r1.h, 0.01f)
    }

    @Test fun renamingDataNodeRelayoutsChart() {
        val s = spec(DiagramKind.BAR)
        val renamed = s.nodes.map { if (it.id == s.nodes[0].id) it.copy(text = "Oca 400") else it }
        val laid = DiagramRenderer.layout(s.kind, renamed)
        assertTrue("400 en uzun çubuk", laid[0].h >= laid.maxOf { it.h } - 1e-4f)
    }

    @Test fun drawsEveryKindInEveryTheme() {
        val bitmap = Bitmap.createBitmap(600, 420, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        DiagramKind.entries.forEach { kind ->
            DiagramThemes.all.forEach { theme ->
                listOf(true, false).forEach { labels ->
                    canvas.drawColor(Color.WHITE)
                    DiagramRenderer.draw(canvas, stroke(spec(kind, theme.id), 580f, 400f, 10f, 10f), labels = labels)
                }
            }
        }
    }

    @Test fun editorialDiagramHasPaperAndAccent() {
        val bitmap = Bitmap.createBitmap(800, 520, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        DiagramRenderer.draw(canvas, stroke(spec(DiagramKind.BAR), 800f, 520f))
        val paper = Color.parseColor(DiagramThemes.editorialLight.paper)
        assertEquals("kağıt zemin", paper, bitmap.getPixel(400, 6))
        // Odak çubuğu (Nis 188) vurgu renginde
        val s = spec(DiagramKind.BAR)
        val focal = s.nodes.first { it.role == "focal" }
        val px = ((focal.x + focal.w / 2f) * 800f).toInt(); val py = ((focal.y + focal.h * 0.6f) * 520f).toInt()
        assertEquals(Color.parseColor(DiagramThemes.editorialLight.accent), bitmap.getPixel(px, py))
    }

    @Test fun wideDiagramKeepsDotsRound() {
        val s = spec(DiagramKind.QUADRANT)
        val st = stroke(s, 1200f, 400f)
        assertEquals(1f / 3f, DiagramRenderer.aspectK(st), 0.01f)
        val square = stroke(s, 500f, 500f)
        assertEquals(1f, DiagramRenderer.aspectK(square), 0.01f)
    }

    @Test fun editorialIsDefaultAndHasNoShadow() {
        val t = DiagramThemes.find(DiagramThemes.EDITORIAL)
        assertNotNull(t)
        assertFalse(t!!.shadow)
        assertTrue(DiagramThemes.isEditorial(DiagramThemes.EDITORIAL_DARK))
        assertEquals(DiagramThemes.EDITORIAL, DiagramThemes.all.first().id)
    }

    @Test fun newStepsInheritStructureAndValues() {
        assertEquals("stage", DiagramCatalog.inheritRole("stage"))
        assertEquals(null, DiagramCatalog.inheritRole("focal"))
        assertEquals("Yeni 210", DiagramCatalog.newNodeText(DiagramKind.BAR, 7, "Haz 210"))
        assertEquals("Yeni iş 13-14", DiagramCatalog.newNodeText(DiagramKind.GANTT, 7, "Yayın 11-12"))
        assertEquals("Adım 3", DiagramCatalog.newNodeText(DiagramKind.FLOW, 3, "Karar"))
        // Isı haritasına eklenen hücre ızgarada yeni satıra
        val s = spec(DiagramKind.HEATMAP)
        val extra = s.nodes + s.nodes.last().copy(id = "x", text = "9", role = null, box = DiagramBox.CELL)
        val laid = DiagramRenderer.layout(s.kind, extra)
        val last = laid.last()
        assertTrue(last.y + last.h <= 1.01f)
    }

    // ------------------------------------------------------------------------------------------ görsel kontrol

    @Test fun renderShots() {
        val dir = System.getenv("DG_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        File(dir).mkdirs()
        val themes = (System.getenv("DG_THEMES") ?: "editorial,editorial_dark,pastel").split(',')
        for (theme in themes) {
            for (kind in DiagramKind.entries) {
                val aspect = DiagramCatalog.aspect(kind) ?: (0.6f / 0.7f)
                val w = 900f
                val h = w / aspect
                val bmp = Bitmap.createBitmap((w + 40).toInt(), (h + 40).toInt(), Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                c.drawColor(Color.WHITE)
                DiagramRenderer.draw(c, stroke(spec(kind, theme), w, h, 20f, 20f))
                File(dir, "v28-$theme-${kind.name.lowercase()}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
