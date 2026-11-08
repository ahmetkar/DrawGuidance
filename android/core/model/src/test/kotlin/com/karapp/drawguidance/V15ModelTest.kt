package com.karapp.drawguidance

import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.DiagramNode
import com.karapp.drawguidance.data.model.DiagramSpec
import com.karapp.drawguidance.data.model.FormulaSpec
import com.karapp.drawguidance.data.model.GraphSpec
import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.model.Layers
import com.karapp.drawguidance.data.model.ShapeSpec
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.SubPath
import com.karapp.drawguidance.data.model.isErasable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** v15 veri modeli: katmanlar, formül, parça biçimleri (v16: vektör noktaları), grafik ayarları, parça biçimleri ve yeni fırçalar kayıtta korunur. */
class V15ModelTest {

    private val quad = listOf(StrokePoint(0f, 0f), StrokePoint(40f, 0f), StrokePoint(40f, 20f), StrokePoint(0f, 20f))

    private fun ink(id: String, layer: String? = null, type: BrushType = BrushType.PENCIL, order: Int = 0) =
        Stroke(id, listOf(StrokePoint(1f, 1f), StrokePoint(9f, 9f)), "#112233", type, 4f, 1f, order, layerId = layer)

    @Test fun layersAndEveryNewElementSurviveTheCodec() {
        val layers = listOf(Layer.Default, Layer("l2", "Gökyüzü", visible = false), Layer("l3", "Yazılar", locked = true))
        val formula = Stroke("f", quad, "#000000", BrushType.FORMULA, 56f, 1f, 2, formula = FormulaSpec("x = \\frac{-@b}{2@a}", "algebra.vertex"), layerId = "l3")
        val pattern = Stroke(
            "p", quad + quad, "#2E7D32", BrushType.SHAPE, 2f, 1f, 3, layerId = "l2",
            shape = ShapeSpec(
                "pat.grass",
                listOf(
                    SubPath(4, closed = true, fillColorHex = "#A5D6A7", widthScale = 0f, alpha = 0.5f, tag = "fill"),
                    SubPath(4, dotted = true, widthScale = 1.5f, strokeColorHex = "#1B5E20", nodes = listOf(0, 2, 3)),
                ),
            ),
        )
        val graph = Stroke(
            "g", quad, "#000000", BrushType.SHAPE, 2f, 1f, 4,
            shape = ShapeSpec(
                "graph.FUNCTION", listOf(SubPath(4, tag = "curve")),
                graph = GraphSpec(kind = "FUNCTION", preset = "sinus", repeat = 3, ticks = 7, tickLabels = mapOf("x:1" to "π/2"), values = listOf(1f, 2f), fillUnder = true),
            ),
        )
        val diagram = Stroke(
            "d", quad, "#000000", BrushType.DIAGRAM, 2f, 1f, 5,
            diagram = DiagramSpec(DiagramKind.VENN, listOf(DiagramNode("a", "A", 0.1f, 0.1f, 0.5f, 0.5f)), emptyList(), theme = "ocean"),
        )
        val page1 = listOf(ink("a", order = 0), ink("dot", "l2", BrushType.DOT_PEN, 1), formula, pattern, graph, diagram, ink("b3", "l3", BrushType.BRUSH_3D, 6))
        val page2 = listOf(ink("c", "l3"))
        val doc = ContentCodec.decodeDocument(ContentCodec.encodePages(listOf(page1, page2), layers = layers))
        assertEquals(layers, doc.layers)
        assertEquals(listOf(page1, page2), doc.pages)
    }

    @Test fun firstLayerIsStoredWithoutIdAndUnknownIdsFallBack() {
        val layers = listOf(Layer("base", "Katman 1"), Layer("top", "Üst"))
        val strokes = listOf(ink("a", "base"), ink("b", "yok"), ink("c", "top"))
        val decoded = ContentCodec.decodeDocument(ContentCodec.encodePages(listOf(strokes), layers = layers)).pages.single()
        assertEquals(listOf(null, null, "top"), decoded.map { it.layerId })
        assertEquals(listOf("base", "base", "top"), decoded.map { Layers.of(it, layers) })
    }

    @Test fun oldSingleLayerDataDecodesWithDefaultLayer() {
        val strokes = listOf(ink("a"), ink("b"))
        // v4 öncesi gibi: katman bilgisi olmadan yazılmış içerik tek katmanlı açılır
        val doc = ContentCodec.decodeDocument(ContentCodec.encode(strokes))
        assertEquals(listOf(Layer.Default), doc.layers)
        assertEquals(strokes, doc.pages.single())
    }

    @Test fun layerHelpersSortHideAndLock() {
        val layers = listOf(Layer("base", "1"), Layer("mid", "2", locked = true), Layer("top", "3", visible = false))
        val strokes = listOf(ink("t", "top", order = 0), ink("a", order = 1), ink("m", "mid", order = 2), ink("b", order = 3))
        val sorted = Layers.sort(strokes, layers)
        assertEquals(listOf("a", "b", "m", "t"), sorted.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), sorted.map { it.order })
        assertSame("zaten sıralıysa aynı liste", sorted, Layers.sort(sorted, layers))
        assertEquals(listOf("a", "b", "m"), Layers.visible(sorted, layers).map { it.id })
        assertEquals(setOf("m", "t"), Layers.lockedIds(sorted, layers))
        val allOpen = layers.map { it.copy(visible = true, locked = false) }
        assertSame(sorted, Layers.visible(sorted, allOpen))
        assertTrue(Layers.lockedIds(sorted, allOpen).isEmpty())
    }

    @Test fun onlyInkIsErasable() {
        val objects = setOf(BrushType.SHAPE, BrushType.TEXT, BrushType.DIAGRAM, BrushType.IMAGE, BrushType.FORMULA, BrushType.ERASER)
        BrushType.entries.forEach { type ->
            if (type in objects) assertFalse("$type silinmemeli", type.isErasable) else assertTrue("$type silinebilmeli", type.isErasable)
        }
    }
}
