package com.karapp.drawguidance.content

import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.LibCategory
import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.vector.INK_COLOR
import com.karapp.drawguidance.content.vector.LineStyle
import com.karapp.drawguidance.content.vector.VectorBuilder
import com.karapp.drawguidance.content.vector.bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorDslTest {

    private val demo = LibShape("demo.house", "Ev (örnek)", Fit.BOX, aspect = 1.1f) {
        rect(0.15f, 0.45f, 0.7f, 0.5f).fill("#F2D7A6")
        poly(0.08f, 0.47f, 0.5f, 0.08f, 0.92f, 0.47f, closed = true).fill("#C0392B")
        roundRect(0.42f, 0.66f, 0.16f, 0.29f, 0.02f).fill("#7A4B2A")
        style(fill = "#BFE3F7") {
            rect(0.22f, 0.55f, 0.14f, 0.12f)
            rect(0.64f, 0.55f, 0.14f, 0.12f)
        }
        line(0.29f, 0.55f, 0.29f, 0.67f).width(0.6f)
        circle(0.55f, 0.8f, 0.012f).fill(INK_COLOR).noStroke()
        arc(0.5f, 0.3f, 0.08f, 0.08f, 180f, 180f).dashed()
        text(0.3f, 0.0f, "Ev", 0.1f)
        group(dx = 0.3f, sx = 0.5f, rotate = 20f) { star(0.5f, 0.2f, 0.1f, 0.04f).fill("#F1C40F") }
    }

    @Test
    fun builderProducesStyledPartsInOrder() {
        val paths = demo.paths(300f)
        assertEquals(10, paths.size)
        assertEquals("#F2D7A6", paths[0].fill)
        assertTrue(paths[0].closed)
        assertEquals("#BFE3F7", paths[3].fill)
        assertEquals(0.6f, paths[5].width)
        assertEquals(0f, paths[6].width)
        assertEquals(LineStyle.DASHED, paths[7].line)
        assertEquals("Ev", paths[8].label)
        val b = paths.bounds()
        assertTrue(b[0] >= 0f && b[2] <= 1f && b[1] >= 0f && b[3] <= 1f)
        // Çözünürlük arttıkça daire daha çok noktayla örneklenir
        val small = VectorBuilder(64f).apply { circle(0.5f, 0.5f, 0.5f) }.paths[0].points.size
        val big = VectorBuilder(2048f).apply { circle(0.5f, 0.5f, 0.5f) }.paths[0].points.size
        assertTrue("$small < $big", small < big)
    }

    @Test
    fun previewSheet() {
        val cat = LibCategory("demo", "Örnek", Audience.BOTH, listOf(demo))
        Preview.write(Preview.sheet(cat), "demo-sheet")
    }
}
