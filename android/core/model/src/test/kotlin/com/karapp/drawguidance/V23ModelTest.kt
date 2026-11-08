package com.karapp.drawguidance

import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BackgroundSpec
import com.karapp.drawguidance.data.model.BoxFit
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.model.Model3DLink
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextBox
import com.karapp.drawguidance.data.model.TextSpec
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v23: yazı kutusu ve 3B şekil kayıtta saklanır (v8); bunlar yoksa eski sürümler yazılır. Arka plan JSON'u. */
class V23ModelTest {

    private fun quad(id: String, type: BrushType, text: TextSpec? = null, model: Model3DLink? = null, asset: String? = null) = Stroke(
        id, listOf(StrokePoint(0f, 0f), StrokePoint(100f, 0f), StrokePoint(100f, 40f), StrokePoint(0f, 40f)),
        "#1E1E1E", type, 24f, 1f, 0, assetId = asset, text = text, model = model,
    )

    @Test
    fun textBoxAndModelRoundTrip() {
        val box = TextBox(width = 320f, height = 180f, fillHex = "#FFF3B0", patternId = "box.lined", patternColorHex = "#7C9CC9",
            patternScale = 1.5f, imageId = "box.sticky", imageFit = BoxFit.STRETCH, corner = 12f, padding = 8f, borderHex = "#333333")
        val strokes = listOf(
            quad("t1", BrushType.TEXT, TextSpec("Merhaba\ndünya", box = box)),
            quad("t2", BrushType.TEXT, TextSpec("kutusuz")),
            quad("m1", BrushType.IMAGE, model = Model3DLink("model-1", "data-1"), asset = "img-1"),
            Stroke("p", listOf(StrokePoint(1f, 2f), StrokePoint(3f, 4f)), "#FF0000", BrushType.PENCIL, 4f, 1f, 3),
        )
        val bytes = ContentCodec.encode(strokes)
        assertEquals("v23 içerik v8", 8, bytes[2].toInt())
        val back = ContentCodec.decode(bytes)
        assertEquals(strokes, back)
        assertEquals(box, back[0].text!!.box)
        assertNull(back[1].text!!.box)
        assertEquals(Model3DLink("model-1", "data-1"), back[2].model)
        // Sayfalı ve katmanlı belgede de
        val layers = listOf(Layer.Default, Layer("l2", "Katman 2"))
        val doc = ContentCodec.decodeDocument(ContentCodec.encodePages(listOf(strokes, strokes.map { it.copy(layerId = "l2") }), layers = layers))
        assertEquals(2, doc.pages.size)
        assertEquals(box, doc.pages[1][0].text!!.box)
    }

    @Test
    fun olderVersionWhenNotNeeded() {
        val plain = listOf(quad("t", BrushType.TEXT, TextSpec("yazı")))
        assertEquals(4, ContentCodec.encode(plain)[2].toInt())
        // Önbellek v8 değişince boşalır: aynı çizimler sonra v4 olarak doğru kodlanır
        val cache = ContentCodec.EncodeCache()
        val withBox = plain + quad("b", BrushType.TEXT, TextSpec("k", box = TextBox(width = 100f)))
        assertEquals(8, ContentCodec.encodePages(listOf(withBox), cache)[2].toInt())
        val again = ContentCodec.encodePages(listOf(plain), cache)
        assertEquals(4, again[2].toInt())
        assertEquals(plain, ContentCodec.decode(again))
    }

    @Test
    fun backgroundJson() {
        val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }
        val spec = BackgroundSpec(patternId = "note.grid5", patternColorHex = "#7C89A6", patternScale = 1.2f, imageId = "asset:abc", imageFit = BoxFit.TILE, imageOpacity = 0.6f)
        val back = json.decodeFromString(BackgroundSpec.serializer(), json.encodeToString(BackgroundSpec.serializer(), spec))
        assertEquals(spec, back)
        assertEquals("abc", back.assetId)
        assertNull(back.paperImageId)
        assertTrue(BackgroundSpec().isEmpty)
        assertEquals("{}", json.encodeToString(BackgroundSpec.serializer(), BackgroundSpec()))
        assertEquals("sky", BackgroundSpec(imageId = "paper:sky").paperImageId)
        assertNull(TextBox().orNull())
    }
}
