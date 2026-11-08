package com.karapp.drawguidance

import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushParams
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TubeMaterial
import com.karapp.drawguidance.data.model.TubeParams
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v20: fırça görünüm ayarları ve kalem yönü kayıtta saklanır; bunlar yoksa eski sürüm (v4/v5) yazılır. */
class V20ModelTest {

    private fun line(id: String, params: BrushParams? = null, orient: Boolean = false, type: BrushType = BrushType.PENCIL) = Stroke(
        id, List(12) { i ->
            if (orient) StrokePoint(i * 4f, 2f, ContentCodec.quantizeUnit(0.6f), ContentCodec.quantizeUnit(0.3f), ContentCodec.quantizeUnit(i / 12f))
            else StrokePoint(i * 4f, 2f)
        },
        "#1E1E1E", type, 10f, 1f, 0, params = params,
    )

    @Test
    fun paramsAndOrientRoundTrip() {
        val tube = BrushParams(tube = TubeParams(lightAngle = 45f, relief = 0.4f, gloss = 0.9f, material = TubeMaterial.METAL, shadow = 0.5f, shadowSoftness = 0.2f))
        val strokes = listOf(
            line("a", BrushParams(spacing = 0.8f, tiltWidth = 1.5f, tiltLight = 0.2f, tiltThreshold = 25f)),
            line("b", BrushParams(nibFollow = true), orient = true, type = BrushType.CALLIGRAPHY),
            line("c", tube, type = BrushType.BRUSH_3D),
            line("d"),
        )
        val bytes = ContentCodec.encode(strokes)
        assertEquals("v20 içerik v6", 6, bytes[2].toInt())
        val back = ContentCodec.decode(bytes)
        assertEquals(strokes, back)
        assertTrue(back[1].points.drop(1).all { it.orient > 0f })
        assertEquals(TubeMaterial.METAL, back[2].params!!.tube!!.material)

        // Ayarsız ve yönsüz içerik eski biçimde kalır
        assertEquals(4, ContentCodec.encode(listOf(line("x")))[2].toInt())
        val penOnly = line("p").copy(points = List(4) { StrokePoint(it * 2f, 1f, ContentCodec.quantizeUnit(0.5f)) })
        assertEquals(5, ContentCodec.encode(listOf(penOnly))[2].toInt())
    }

    @Test
    fun paramsJsonIsCompactAndTolerant() {
        val json = Json { encodeDefaults = false }
        assertEquals("{}", json.encodeToString(BrushParams.serializer(), BrushParams()))
        assertEquals("""{"spacing":0.5}""", Json { encodeDefaults = true }.encodeToString(BrushParams.serializer(), BrushParams(spacing = 0.5f)))
        // Eski JSON kayıtlarında (params yok) çizim aynen okunur; yön yoksa 0
        val s = Json { ignoreUnknownKeys = true }.decodeFromString(Stroke.serializer(),
            """{"id":"a","points":[{"x":1.0,"y":2.0,"pressure":0.5}],"colorHex":"#000000","brushType":"PENCIL","sizePx":4.0,"opacity":1.0,"order":0}""")
        assertEquals(null, s.params)
        assertEquals(0f, s.points.single().orient)
        assertTrue(s.points.single().hasDynamics)
        assertTrue(StrokePoint(0f, 0f, orient = 0.3f).hasDynamics)
    }
}
