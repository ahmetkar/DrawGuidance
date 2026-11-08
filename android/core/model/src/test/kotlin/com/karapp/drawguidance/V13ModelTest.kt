package com.karapp.drawguidance

import com.karapp.drawguidance.data.diagram.DiagramFrames
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.DiagramEdge
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.DiagramNode
import com.karapp.drawguidance.data.model.DiagramSpec
import com.karapp.drawguidance.data.model.ShapeSpec
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.SubPath
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.model.TextParagraph
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.data.text.TextLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v13 veri modeli: yeni yazı tipleri ve paragraf biçimi, parça/bölge renkleri, diyagram çerçevesi, listeler. */
class V13ModelTest {

    private fun text(id: String, spec: TextSpec) = Stroke(
        id, listOf(StrokePoint(0f, 0f), StrokePoint(10f, 0f), StrokePoint(10f, 5f), StrokePoint(0f, 5f)),
        "#1E1E1E", BrushType.TEXT, 30f, 1f, 0, text = spec,
    )

    @Test fun everyFontAndParagraphSurvivesTheCodec() {
        val strokes = TextFont.entries.mapIndexed { i, font ->
            text("t$i", TextSpec("Yazı $i", font = font, paragraph = TextParagraph.entries[i % TextParagraph.entries.size]))
        }
        val decoded = ContentCodec.decode(ContentCodec.encode(strokes))
        assertEquals(strokes, decoded)
        // Kırpma ile birlikte de
        val clipped = strokes.first().copy(clip = listOf(StrokePoint(0f, 0f), StrokePoint(5f, 0f), StrokePoint(5f, 5f), StrokePoint(0f, 5f)))
            .let { it.copy(text = it.text!!.copy(paragraph = TextParagraph.H2)) }
        assertEquals(clipped, ContentCodec.decode(ContentCodec.encode(listOf(clipped))).single())
    }

    @Test fun partColorsAndDiagramColorsSurviveTheCodec() {
        val shape = Stroke(
            "s", listOf(StrokePoint(0f, 0f), StrokePoint(10f, 0f), StrokePoint(10f, 10f), StrokePoint(20f, 0f), StrokePoint(30f, 10f)),
            "#000000", BrushType.SHAPE, 4f, 1f, 0,
            shape = ShapeSpec("SQUARE", listOf(SubPath(3, closed = true, fillColorHex = "#FF0000"), SubPath(2, strokeColorHex = "#00FF00"))),
        )
        val diagram = Stroke(
            "d", listOf(StrokePoint(0f, 0f), StrokePoint(10f, 0f), StrokePoint(10f, 10f), StrokePoint(0f, 10f)),
            "#000000", BrushType.DIAGRAM, 4f, 1f, 1,
            diagram = DiagramSpec(
                DiagramKind.FLOW,
                listOf(DiagramNode("a", "A", 0.1f, 0.1f, 0.2f, 0.1f, fillColorHex = "#123456", textColorHex = "#FFFFFF"), DiagramNode("b", "B", 0.5f, 0.5f, 0.2f, 0.1f)),
                listOf(DiagramEdge("a", "b", colorHex = "#ABCDEF")),
                frame = com.karapp.drawguidance.data.model.DiagramFrame(-0.5f, 0f, 1f, 1.2f),
                manual = true,
            ),
        )
        assertEquals(listOf(shape, diagram), ContentCodec.decode(ContentCodec.encode(listOf(shape, diagram))))
    }

    @Test fun diagramFrameGrowsWithoutMovingNodes() {
        val corners = listOf(StrokePoint(100f, 100f), StrokePoint(300f, 100f), StrokePoint(300f, 500f), StrokePoint(100f, 500f))
        val spec = DiagramSpec(DiagramKind.FLOW, listOf(DiagramNode("a", "A", 0.2f, 0.2f, 0.3f, 0.1f)))
        val before = DiagramFrames.toCanvas(corners, spec.frame, 0.2f, 0.2f)!!
        val (grown, grownSpec) = DiagramFrames.grow(corners, spec, 1.1f, 0.4f, 1.4f, 0.5f)!!
        val after = DiagramFrames.toCanvas(grown, grownSpec.frame, 0.2f, 0.2f)!!
        assertEquals(before.x, after.x, 0.01f)
        assertEquals(before.y, after.y, 0.01f)
        assertTrue("sağa büyüdü", grown[1].x > 300f)
        assertEquals(100f, grown[0].x, 0.01f)
        // Tuvalden tasarım alanına dönüş
        val design = DiagramFrames.toDesign(grown, grownSpec.frame, after.x, after.y)!!
        assertEquals(0.2f, design.x, 1e-4f)
        assertEquals(0.2f, design.y, 1e-4f)
        assertNull("içerideki bölge büyütmez", DiagramFrames.grow(grown, grownSpec, 0.3f, 0.3f, 0.4f, 0.4f))
    }

    @Test fun bulletAndNumberedListsContinueAndRenumber() {
        assertEquals("boş yazıda ilk işaret", "• ", TextLists.apply("", TextParagraph.BULLET))
        assertEquals("1. ", TextLists.apply("", TextParagraph.NUMBERED))
        val bullets = TextLists.apply("Elma\nArmut", TextParagraph.BULLET)
        assertEquals("• Elma\n• Armut", bullets)
        // Enter: yeni öğe işaretle başlar
        val typed = bullets + "\n"
        val edit = TextLists.onEdit(bullets, typed, typed.length, TextParagraph.BULLET)
        assertEquals("• Elma\n• Armut\n• ", edit.text)
        assertEquals(edit.text.length, edit.cursor)
        // Boş öğede Enter listeyi bitirir
        val again = edit.text + "\n"
        val end = TextLists.onEdit(edit.text, again, again.length, TextParagraph.BULLET)
        assertEquals("• Elma\n• Armut\n", end.text)

        val numbered = TextLists.apply("Bir\nİki\nÜç", TextParagraph.NUMBERED)
        assertEquals("1. Bir\n2. İki\n3. Üç", numbered)
        // İkinci satırı içeri al: a. olur, sonraki 2. olur
        val cursor = numbered.indexOf("İki")
        val indented = TextLists.indent(numbered, cursor, TextParagraph.NUMBERED, +1)
        assertEquals("1. Bir\n    a. İki\n2. Üç", indented.text)
        assertEquals(indented.text.indexOf("İki"), indented.cursor)
        // Başa dönüş ve düz metne çevirme
        assertEquals("1. Bir\n2. İki\n3. Üç", TextLists.indent(indented.text, indented.cursor, TextParagraph.NUMBERED, -1).text)
        assertEquals("Bir\nİki\nÜç", TextLists.apply(numbered, TextParagraph.NORMAL))
        // Madde ↔ numara
        assertEquals("• Bir\n• İki\n• Üç", TextLists.apply(numbered, TextParagraph.BULLET))
        // Ortadaki satır silinince numaralar kayar
        val removed = "1. Bir\n3. Üç"
        assertEquals("1. Bir\n2. Üç", TextLists.onEdit(numbered, removed, 6, TextParagraph.NUMBERED).text)
    }
}
