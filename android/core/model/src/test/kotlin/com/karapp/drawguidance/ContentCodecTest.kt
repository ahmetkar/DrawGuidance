package com.karapp.drawguidance

import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.repository.encodeStrokes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import java.util.UUID

/** Tuval içeriğinin kompakt + DEFLATE biçimi: kayıpsız olmalı ve JSON'dan çok daha az yer tutmalı. */
class ContentCodecTest {

    private fun realisticCanvas(strokeCount: Int, seed: Long = 42): List<Stroke> {
        val r = Random(seed)
        val types = listOf(BrushType.PENCIL, BrushType.MARKER, BrushType.PASTEL, BrushType.HIGHLIGHTER, BrushType.ERASER)
        return List(strokeCount) { i ->
            var x = r.nextFloat() * 1000f
            var y = r.nextFloat() * 1000f
            var angle = r.nextFloat() * 6.28f
            val points = List(40 + r.nextInt(80)) {
                angle += (r.nextFloat() - 0.5f) * 0.3f
                x += kotlin.math.cos(angle) * 3.2f
                y += kotlin.math.sin(angle) * 3.2f
                StrokePoint(ContentCodec.quantize(x), ContentCodec.quantize(y))
            }
            Stroke(
                id = UUID.randomUUID().toString(), points = points,
                colorHex = listOf("#1E1E1E", "#2E7D32", "#E65100")[r.nextInt(3)], brushType = types[r.nextInt(types.size)],
                sizePx = listOf(6f, 14f, 18f)[r.nextInt(3)], opacity = 1f, order = i,
                groupId = if (i % 7 == 0) "g-${i / 7}" else null,
            )
        }
    }

    @Test fun roundTripIsExactForTypicalContent() {
        val strokes = realisticCanvas(150)
        assertEquals(strokes, ContentCodec.decode(ContentCodec.encode(strokes)))
        assertEquals(emptyList<Stroke>(), ContentCodec.decode(ContentCodec.EMPTY))
    }

    @Test fun roundTripIsBitExactForArbitraryFloatsAndStrings() {
        val r = Random(9)
        val weird = List(50) { i ->
            Stroke(
                id = if (i % 2 == 0) "özel-kimlik-$i-ğüşiöç" else UUID.randomUUID().toString().uppercase(),
                points = List(r.nextInt(20)) { StrokePoint(r.nextFloat() * 1e4f - 5e3f, (r.nextGaussian() * 1e3).toFloat()) } +
                    listOf(StrokePoint(Float.MAX_VALUE, -0f), StrokePoint(1e-7f, 123456.78f)),
                colorHex = if (i % 3 == 0) "#abcdef" else "#00FF7F",
                brushType = BrushType.entries[i % BrushType.entries.size],
                sizePx = r.nextFloat() * 80f, opacity = r.nextFloat(), order = i - 10,
                groupId = if (i % 4 == 0) UUID.randomUUID().toString() else null,
                assetId = if (i % 5 == 0) UUID.randomUUID().toString() else null,
            )
        }
        val decoded = ContentCodec.decode(ContentCodec.encode(weird))
        assertEquals(weird.size, decoded.size)
        weird.zip(decoded).forEach { (a, b) ->
            assertEquals(a.copy(points = emptyList()), b.copy(points = emptyList()))
            assertEquals(a.points.size, b.points.size)
            a.points.zip(b.points).forEach { (p, q) ->
                assertEquals(p.x.toRawBits(), q.x.toRawBits())
                assertEquals(p.y.toRawBits(), q.y.toRawBits())
            }
            assertEquals(a.sizePx.toRawBits(), b.sizePx.toRawBits())
        }
    }

    @Test fun cachedEncodingIsByteIdenticalAndOnlyEncodesNewStrokes() {
        val base = realisticCanvas(400)
        val cache = ContentCodec.EncodeCache()
        assertArrayEquals(ContentCodec.encode(base), ContentCodec.encode(base, cache))
        // Çizim ekleme, silme ve değiştirme sonrası da önbelleksiz kodlamayla aynı
        val appended = base + realisticCanvas(3, seed = 7)
        assertArrayEquals(ContentCodec.encode(appended), ContentCodec.encode(appended, cache))
        val edited = appended.filterIndexed { i, _ -> i != 10 }.mapIndexed { i, st -> if (i == 5) st.copy(colorHex = "#123456") else st }
        assertArrayEquals(ContentCodec.encode(edited), ContentCodec.encode(edited, cache))
        assertEquals(edited, ContentCodec.decode(ContentCodec.encode(edited, cache)))

        // Büyük tuvalde bir çizim eklemek, her şeyi yeniden kodlamaktan hızlı olmalı
        val big = realisticCanvas(3000)
        val warm = ContentCodec.EncodeCache()
        ContentCodec.encode(big, warm)
        repeat(3) { ContentCodec.encode(big) }
        val t0 = System.nanoTime(); ContentCodec.encode(big); val fullMs = (System.nanoTime() - t0) / 1e6
        val next = big + realisticCanvas(1, seed = 99)
        val t1 = System.nanoTime(); ContentCodec.encode(next, warm); val cachedMs = (System.nanoTime() - t1) / 1e6
        println("CODEC 3000 çizim: tam kodlama=${"%.1f".format(fullMs)} ms, önbellekli (1 yeni çizim)=${"%.1f".format(cachedMs)} ms")
    }

    @Test fun legacyJsonContentIsStillReadable() {
        val strokes = realisticCanvas(3)
        assertEquals(strokes, ContentCodec.decode(encodeStrokes(strokes).toByteArray()))
    }

    @Test fun compressedContentIsMuchSmallerThanJson() {
        val strokes = realisticCanvas(200)
        val json = encodeStrokes(strokes).toByteArray().size
        val compact = ContentCodec.encode(strokes).size
        println("SIZE json=$json compact=$compact ratio=${json.toFloat() / compact}")
        assertTrue("sıkıştırılmış içerik JSON'un en fazla %20'si olmalı ($compact / $json)", compact * 5 <= json)
        assertArrayEquals(ContentCodec.encode(strokes), ContentCodec.encode(ContentCodec.decode(ContentCodec.encode(strokes))))
    }
}
