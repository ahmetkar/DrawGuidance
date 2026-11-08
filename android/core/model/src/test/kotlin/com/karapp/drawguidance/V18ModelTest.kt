package com.karapp.drawguidance

import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.GraphSpec
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v18: kalem basıncı ve eğimi kayıtta saklanır; kalemsiz içerik eski (v4) biçimde yazılır. */
class V18ModelTest {

    private fun pen(id: String, n: Int) = Stroke(
        id, List(n) { i ->
            StrokePoint(i * 3f, i * 1.5f, ContentCodec.quantizeUnit(0.2f + 0.8f * i / n), ContentCodec.quantizeUnit(if (i % 2 == 0) 0.4f else 0f))
        },
        "#1E1E1E", BrushType.PENCIL, 6f, 1f, 0,
    )

    @Test
    fun pressureAndTiltRoundTrip() {
        val strokes = listOf(pen("a", 40), Stroke("b", listOf(StrokePoint(1f, 2f), StrokePoint(3f, 4f)), "#FF0000", BrushType.MARKER, 8f, 1f, 1))
        val bytes = ContentCodec.encode(strokes)
        assertEquals("kalemli içerik v5", 5, bytes[2].toInt())
        val back = ContentCodec.decode(bytes)
        assertEquals(strokes, back)
        assertTrue(back[0].points.any { it.hasDynamics })
        assertFalse(back[1].points.any { it.hasDynamics })

        // Kalemsiz içerik eski uygulamalar da okusun diye v4
        val plain = ContentCodec.encode(listOf(strokes[1]))
        assertEquals(4, plain[2].toInt())
        assertEquals(listOf(strokes[1]), ContentCodec.decode(plain))
    }

    @Test
    fun quantizeUnitIsStable() {
        for (v in listOf(0f, 0.001f, 0.25f, 0.5f, 0.9999f, 1f, 1.7f, -0.3f)) {
            val q = ContentCodec.quantizeUnit(v)
            assertEquals(q, ContentCodec.quantizeUnit(q))
            assertTrue(q in 0f..1f)
        }
        assertEquals(1f, ContentCodec.quantizeUnit(1f))
        assertEquals(0f, ContentCodec.quantizeUnit(0f))
    }

    @Test
    fun pointJsonOmitsDefaults() {
        val json = Json { encodeDefaults = true }
        assertEquals("""{"x":1.0,"y":2.0}""", json.encodeToString(StrokePoint.serializer(), StrokePoint(1f, 2f)))
        val pen = StrokePoint(1f, 2f, 0.5f, 0.25f)
        assertEquals(pen, json.decodeFromString(StrokePoint.serializer(), json.encodeToString(StrokePoint.serializer(), pen)))
        // Eski kayıttaki grafik (desen ve kağıt yok) varsayılanlarla okunur
        val g = Json { ignoreUnknownKeys = true }.decodeFromString(GraphSpec.serializer(), """{"kind":"FUNCTION","preset":"sinus"}""")
        assertEquals(1, g.freq)
        assertEquals("", g.paper)
    }

    @Test
    fun pointMoveKeepsDynamics() {
        val p = StrokePoint(1f, 2f, 0.3f, 0.6f)
        val moved = p.at(10f, 20f)
        assertEquals(0.3f, moved.pressure)
        assertEquals(0.6f, moved.tilt)
        assertEquals(10f, moved.x)
    }
}
