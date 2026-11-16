package com.karapp.drawguidance

import com.karapp.drawguidance.data.model.BalloonStyle
import com.karapp.drawguidance.content.comic.ComicTones
import com.karapp.drawguidance.content.image.ClipBlend
import com.karapp.drawguidance.content.image.ClipFill
import com.karapp.drawguidance.content.image.PhotoBalance
import com.karapp.drawguidance.content.image.PhotoClip
import com.karapp.drawguidance.content.image.PhotoEdit
import com.karapp.drawguidance.content.image.PhotoHsl
import com.karapp.drawguidance.content.image.PhotoOps
import com.karapp.drawguidance.content.structure.StructureGroup
import com.karapp.drawguidance.content.structure.StructureGuides
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.ShapeSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.model.ToneFill
import com.karapp.drawguidance.ui.screens.canvasedit.state.ComicController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/** v53: ekran tonları ve desenler, yapı kılavuzları, foto düzenleme (HSL, renk dengesi, kırpma maskesi), balon yazısı. */
class V53FeaturesTest {

    @Test
    fun tonesAreDeterministicBoundedAndFast() {
        for (p in ComicTones.ALL) {
            val a = ComicTones.prims(p.id, p.density, p.scale, p.angle, 0f, 0f, 600f, 400f)
            val b = ComicTones.prims(p.id, p.density, p.scale, p.angle, 0f, 0f, 600f, 400f)
            assertTrue("${p.key} boş", a.isNotEmpty())
            assertEquals("${p.key} aynı girdide aynı sonuç vermeli", a.size, b.size)
            assertEquals(a.first().toString(), b.first().toString())
        }
        // Çok büyük alanda bile öğe sayısı sınırlı kalır (bellek) ve hızlı üretilir
        val ms = measureTimeMillis {
            for (p in ComicTones.ALL) {
                val n = ComicTones.prims(p.id, p.density, 0.3f, p.angle, 0f, 0f, 6000f, 9000f).size
                assertTrue("${p.key}: $n öğe", n <= ComicTones.MAX_PRIMS * 3)
            }
        }
        assertTrue("ton üretimi yavaş: $ms ms", ms < 8_000)
        assertEquals(10, ComicTones.TONES.size)
        assertEquals(14, ComicTones.PATTERNS.size)
    }

    @Test
    fun denserToneCoversMore() {
        fun ink(d: Float) = ComicTones.prims("tone_dot", d, 1f, 45f, 0f, 0f, 300f, 300f)
            .filterIsInstance<ComicTones.Dot>().sumOf { (it.r * it.r).toDouble() }
        assertTrue(ink(0.6f) > ink(0.2f) * 2)
    }

    @Test
    fun toneFillSurvivesTheCodecAndOldContentStaysCompatible() {
        val tone = ToneFill("pat_flowers", density = 0.6f, scale = 1.5f, angle = 30f, colorHex = "#E91E63")
        val shape = Stroke(
            "s1", listOf(StrokePoint(0f, 0f), StrokePoint(100f, 0f), StrokePoint(100f, 80f), StrokePoint(0f, 80f)), "#111111", BrushType.SHAPE, 2f, 1f, 0,
            shape = ShapeSpec(kind = "RECTANGLE", subpaths = listOf(com.karapp.drawguidance.data.model.SubPath(4, closed = true)), style = ShapeStyle(tone = tone)),
        )
        val back = ContentCodec.decode(ContentCodec.encode(listOf(shape))).single()
        assertEquals(tone, back.shape?.style?.tone)
        val plain = shape.copy(shape = shape.shape!!.copy(style = ShapeStyle()))
        val json = String(ContentCodec.encode(listOf(plain)), Charsets.ISO_8859_1)
        assertTrue("ton yoksa yazılmamalı", !json.contains("\"tone\""))
        assertNull(ContentCodec.decode(ContentCodec.encode(listOf(plain))).single().shape?.style?.tone)
    }

    @Test
    fun structureGuidesLoadAllGroups() {
        val all = StructureGuides.all
        // v54 32 yeni karmaşık çizim yardımcısı (insan pozları, hayvanlar, perspektif, taşıtlar, eşyalar)
        assertEquals(59, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        for (g in StructureGroup.entries) assertTrue(g.name, StructureGuides.byGroup(g).size >= 6)
        all.forEach { assertTrue(it.id, it.prims.size >= 4); assertTrue(it.aspect in 0.2f..2.5f) }
        assertTrue(StructureGuides.find("human_front")!!.prims.any { it is com.karapp.drawguidance.content.structure.StructurePrim.Measure })
    }

    private fun img(w: Int, h: Int, f: (Int, Int) -> Int) = IntArray(w * h) { f(it % w, it / w) }

    @Test
    fun hslChangesOnlyTheChosenColourRange() {
        val red = 0xFFE53935.toInt(); val blue = 0xFF1E88E5.toInt()
        val px = intArrayOf(red, blue)
        val out = PhotoOps.hsl(px, PhotoHsl().with(0, s = -1f))
        assertNotEquals(red, out[0])
        val r = (out[0] shr 16) and 0xFF; val g = (out[0] shr 8) and 0xFF
        assertTrue("kırmızı doygunluğu düşmeli", kotlin.math.abs(r - g) < 40)
        assertEquals("mavi değişmemeli", blue, out[1])
    }

    @Test
    fun colorBalanceShiftsShadowsAndPreservesLuminosity() {
        val dark = 0xFF303030.toInt()
        val out = PhotoOps.balance(intArrayOf(dark), PhotoBalance().with(0, 2, 1f))
        val r = (out[0] shr 16) and 0xFF; val b = out[0] and 0xFF
        assertTrue("gölgede mavi artmalı", b > r)
        val lum = { c: Int -> 0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF) }
        assertTrue(kotlin.math.abs(lum(out[0]) - lum(dark)) < 3)
    }

    @Test
    fun clippingMaskOnlyTouchesVisiblePixels() {
        val w = 4; val h = 4
        val px = img(w, h) { x, _ -> if (x < 2) 0 else 0xFF808080.toInt() }
        val out = PhotoOps.clip(px, w, h, PhotoClip(ClipFill.GRADIENT, 0xFFFF0000.toInt(), 0xFF0000FF.toInt(), 0f, ClipBlend.NORMAL, 1f))
        for (y in 0 until h) {
            assertEquals("saydam piksel değişmemeli", 0, out[y * w])
            assertEquals(0xFF, out[y * w + 3] ushr 24)
        }
        assertTrue(((out[2] shr 16) and 0xFF) > (out[3] shr 16 and 0xFF))
        // Bütün düzenleme hattı yeni alanlarla çalışır
        val edit = PhotoEdit(hsl = PhotoHsl().with(3, h = 0.5f), balance = PhotoBalance().with(1, 0, 0.3f), clip = PhotoClip())
        val (o, ow, oh) = PhotoOps.render(img(8, 8) { x, y -> 0xFF000000.toInt() or (x * 30 shl 16) or (y * 30 shl 8) }, 8, 8, edit)
        assertEquals(64, o.size); assertEquals(8, ow); assertEquals(8, oh)
    }

    @Test
    fun balloonTextFollowsTheBalloon() {
        assertEquals(TextFont.BANGERS, ComicController.balloonFont(BalloonStyle.SHOUT))
        assertEquals(TextFont.SHANTELL, ComicController.balloonFont(BalloonStyle.THOUGHT))
        assertEquals(TextFont.COMIC_RELIEF_BOLD, ComicController.balloonFont(BalloonStyle.SPEECH))
        assertEquals(true, ComicController.balloonEffects(BalloonStyle.SPEECH, null)?.caps)
        assertEquals(null, ComicController.balloonEffects(BalloonStyle.PHONE, null))
        val speech = ComicController.balloonTextSize(BalloonStyle.SPEECH, 1200f, 1800f)
        assertTrue(ComicController.balloonTextSize(BalloonStyle.SHOUT, 1200f, 1800f) > speech)
        assertTrue(ComicController.balloonTextSize(BalloonStyle.WHISPER, 1200f, 1800f) < speech)
        assertTrue(ComicController.balloonTextSize(BalloonStyle.SPEECH, 3000f, 3000f) > speech)
    }

    @Test
    fun comicSfxHaveStickerOutlineAndTurkishCaps() {
        val p = com.karapp.drawguidance.content.comic.ComicSfx.presets.first { it.text == "HAHAHA" }
        val spec = ComicController.sfxSpec(p, 100f)
        assertEquals("#FFFFFF", spec.box?.effects?.outerHex)
        assertTrue((spec.box?.effects?.outerWidth ?: 0f) > 0.05f)
        assertTrue(spec.outlineWidth >= 8f)
        assertEquals(true, spec.box?.effects?.caps)
        assertTrue(com.karapp.drawguidance.content.comic.ComicSfx.presets.none { it.font == "RUBIK_BUBBLES" || it.font == "BUNGEE_SHADE" })
        assertEquals("ÇİÇEK", com.karapp.drawguidance.ui.drawing.TextRenderer.upper("çiçek"))
    }
}
