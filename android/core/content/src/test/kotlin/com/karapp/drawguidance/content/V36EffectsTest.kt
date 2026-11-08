package com.karapp.drawguidance.content

import com.karapp.drawguidance.content.comic.ComicEffects
import com.karapp.drawguidance.content.comic.ComicLine
import com.karapp.drawguidance.content.comic.ComicLines
import com.karapp.drawguidance.content.comic.ComicLinesKind
import com.karapp.drawguidance.content.comic.ComicSfx
import com.karapp.drawguidance.content.comic.EffectDef
import com.karapp.drawguidance.content.comic.EffectGroup
import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.vector.bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.max

/**
 * v36 (8-2) efektler: aksiyon, büyü/enerji, duygu ve atmosfer efektlerinin hepsi gerçekten çizilir, kutuda kalır,
 * kütüphanede kimliğiyle bulunur ama şekil seçicide listelenmez; ses efektlerinde istenen yazılar hazır; odak
 * çizgilerinin yeni türleri ve odak/yön/uzunluk ayarları çizgi üretir. -PpreviewDir ile önizleme sayfaları.
 */
class V36EffectsTest {

    @Test
    fun everyRequestedEffectExists() {
        val want = mapOf(
            EffectGroup.ACTION to listOf("speed", "impact", "collision", "explosion", "shatter", "dust", "punch_trail"),
            EffectGroup.MAGIC to listOf("lightning", "flame", "ice", "aura", "magic_circle", "light_beam", "portal_glow"),
            EffectGroup.EMOTION to listOf("hearts", "tears", "sweat", "anger", "surprise", "tremble"),
            EffectGroup.ATMOSPHERE to listOf("rain", "snow", "fog", "smoke", "sparks", "glow", "dots"),
        )
        want.forEach { (g, keys) -> assertEquals(g.title, keys, ComicEffects.of(g).map { it.key }) }
        val ids = ComicEffects.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        // Ses efektleri: istenen yazıların hepsi hazır
        val sfx = ComicSfx.presets.map { it.text }
        listOf("POW!", "BAM!", "BOOM!", "CRASH!", "WHOOSH!", "ZAP!").forEach { assertTrue(it, it in sfx) }
        assertEquals(listOf("POW!", "BAM!", "BOOM!", "CRASH!", "WHOOSH!", "ZAP!"), ComicSfx.featured)
    }

    @Test
    fun effectsAreDrawnInsideTheirBoxAndHiddenFromThePicker() {
        ComicEffects.all.forEach { e ->
            val s = e.shape()
            val paths = s.paths(256f)
            assertTrue("${e.key} boş", paths.size >= 3)
            val b = paths.bounds()
            assertTrue("${e.key} kutu dışında: ${b.toList()}", b[0] >= -0.04f && b[1] >= -0.04f && b[2] <= 1.04f && b[3] <= 1.04f)
            assertTrue("${e.key} kutuyu doldurmalı: ${b.toList()}", b[2] - b[0] > 0.6f && b[3] - b[1] > 0.5f)
            assertNotNull("${e.id} kütüphanede", ShapeLibrary.find(ShapeLibrary.PREFIX + e.id))
            assertEquals(e, ComicEffects.byId(ShapeLibrary.PREFIX + e.id))
            assertTrue("${e.key} renk", e.color.matches(Regex("#[0-9A-F]{6}")))
        }
        val cats = ShapeLibrary.bundledCategories.filter { c -> c.shapes.any { it.id.startsWith(ComicEffects.ID_PREFIX) } }
        assertEquals(4, cats.size)
        assertTrue(cats.all { !it.listed && it.audience == Audience.ART })
        assertFalse(ShapeLibrary.forAudience(Audience.ART).filter { it.listed }.any { c -> c.shapes.any { it.id.startsWith(ComicEffects.ID_PREFIX) } })
    }

    @Test
    fun focusLinesNewKindsAndControls() {
        listOf(ComicLinesKind.ZOOM, ComicLinesKind.PERSPECTIVE, ComicLinesKind.BEAMS, ComicLinesKind.MANGA_DENSE, ComicLinesKind.ANIME_SOFT).forEach { k ->
            val lines = ComicLines.generate(k, 0f, 0f, 1000f, 800f)
            assertTrue("$k çizgi yok", lines.size >= 8)
            assertTrue("$k alanda", lines.all { l -> l.xs.all { it in -1f..1001f } && l.ys.all { it in -1f..801f } })
        }
        // Varsayılanlarla eski çıktı aynı (eski kayıtlar değişmez)
        val old = ComicLines.generate(ComicLinesKind.FOCUS, 0f, 0f, 1000f, 800f)
        fun key(lines: List<ComicLine>) = lines.map { it.xs.toList() + it.ys.toList() + it.pressures.toList() }
        assertEquals(key(old), key(ComicLines.generate(ComicLinesKind.FOCUS, 0f, 0f, 1000f, 800f, focusX = 0.5f, focusY = 0.5f, angle = 0f, length = 1f)))
        // Odak noktası kayar: çizgiler o noktaya doğru
        val moved = ComicLines.generate(ComicLinesKind.FOCUS, 0f, 0f, 1000f, 800f, focusX = 0.2f, focusY = 0.3f)
        assertTrue(key(moved) != key(old))
        fun near(lines: List<ComicLine>, x: Float, y: Float) = lines.map { l ->
            minOf(kotlin.math.hypot(l.xs.first() - x, l.ys.first() - y), kotlin.math.hypot(l.xs.last() - x, l.ys.last() - y))
        }.average()
        assertTrue("odak (200,240) yakını", near(moved, 200f, 240f) < near(moved, 500f, 400f))
        // Uzunluk: kısa çizgiler
        val short = ComicLines.generate(ComicLinesKind.FOCUS, 0f, 0f, 1000f, 800f, length = 0.4f)
        fun len(lines: List<ComicLine>) = lines.sumOf { l -> (1 until l.xs.size).sumOf { i -> kotlin.math.hypot((l.xs[i] - l.xs[i - 1]).toDouble(), (l.ys[i] - l.ys[i - 1]).toDouble()) } }
        assertTrue(len(short) < len(old) * 0.8)
        // Yön: hız çizgileri döner
        val h = ComicLines.generate(ComicLinesKind.SPEED_H, 0f, 0f, 1000f, 800f)
        val r = ComicLines.generate(ComicLinesKind.SPEED_H, 0f, 0f, 1000f, 800f, angle = 30f)
        val dy = r.map { (it.ys.last() - it.ys.first()).toDouble() }.average()
        assertTrue(h.all { kotlin.math.abs(it.ys.last() - it.ys.first()) < 2f })
        assertTrue("yön 30°: $dy", kotlin.math.abs(dy) > 20.0)
        assertTrue(ComicLines.usesFocus(ComicLinesKind.ZOOM) && !ComicLines.usesFocus(ComicLinesKind.SPEED_H))
        assertTrue(ComicLines.usesAngle(ComicLinesKind.SPEED_H) && !ComicLines.usesAngle(ComicLinesKind.FOCUS))
    }

    @Test
    fun previewSheets() {
        if (Preview.dir == null) return
        EffectGroup.entries.forEach { g ->
            val defs = ComicEffects.of(g)
            if (defs.isNotEmpty()) Preview.write(sheet(g.title, defs), "v36-effects-${g.id}")
        }
        Preview.write(linesSheet(), "v36-effects-lines")
    }

    /** Her efekt üç renkte: varsayılan, kırmızı, siyah (renk değişince tonlar da değişir). */
    private fun sheet(title: String, defs: List<EffectDef>, cell: Int = 220): BufferedImage {
        val colors = listOf<String?>(null, "#E53935", "#222222")
        val img = BufferedImage(colors.size * cell, defs.size * (cell + 22) + 50, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(0xEEF0F4); g.fillRect(0, 0, img.width, img.height)
        g.color = Color(0x333333); g.font = Font(Font.SANS_SERIF, Font.BOLD, 20)
        g.drawString("$title (${defs.size})", 12, 30)
        defs.forEachIndexed { i, d ->
            colors.forEachIndexed { k, c ->
                val cx = k * cell; val cy = 40 + i * (cell + 22)
                // Açık zeminde açık efektler görünsün diye üçüncü sütun koyu zemin
                g.color = if (k == 2) Color(0x6A7080) else Color.WHITE
                g.fillRoundRect(cx + 4, cy + 4, cell - 8, cell - 8, 14, 14)
                val s = d.shape()
                val inner = cell - 28f
                val w = if (s.aspect >= 1f) inner else inner * s.aspect
                val h = if (s.aspect >= 1f) inner / s.aspect else inner
                val fill = if (k == 2) "#FFFFFF" else c ?: d.color
                val ink = if (d.lineOnly) Preview.color(fill, Color.BLACK) else dark(fill)
                Preview.draw(g, s.paths(inner), cx + (cell - w) / 2f, cy + (cell - h) / 2f, w, h, inkWidth = 2.2f * max(w, h) / 200f, ink = ink, elementFill = fill)
                g.color = Color(0x444444); g.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
                g.drawString(d.title, cx + 8, cy + cell + 12)
            }
        }
        g.dispose()
        return img
    }

    private fun dark(hex: String): Color {
        val c = Preview.color(hex, Color.BLACK)
        return Color((c.red * 0.4f).toInt(), (c.green * 0.4f).toInt(), (c.blue * 0.4f).toInt())
    }

    private fun linesSheet(cell: Int = 260): BufferedImage {
        val kinds = ComicLinesKind.entries
        val cols = 4
        val rows = (kinds.size + cols - 1) / cols + 1
        val img = BufferedImage(cols * cell, rows * (cell + 22) + 40, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(0xEEF0F4); g.fillRect(0, 0, img.width, img.height)
        fun panel(i: Int, label: String, lines: List<ComicLine>, kind: ComicLinesKind) {
            val cx = (i % cols) * cell; val cy = 40 + (i / cols) * (cell + 22)
            g.color = Color.WHITE; g.fillRect(cx + 6, cy + 6, cell - 12, cell - 12)
            val k = (cell - 12) / 1000f
            lines.forEachIndexed { n, l ->
                val c = if (kind == ComicLinesKind.ANIME_SOFT) Preview.color(ComicLines.SOFT_COLORS[n % ComicLines.SOFT_COLORS.size], Color.BLACK) else Color(0x1D1D24)
                for (j in 1 until l.xs.size) {
                    g.stroke = BasicStroke(max(0.5f, 2.4f * ComicLines.widthFactor(kind) * k * 4f * (l.pressures[j - 1] + l.pressures[j]) / 2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g.color = c
                    g.drawLine((cx + 6 + l.xs[j - 1] * k).toInt(), (cy + 6 + l.ys[j - 1] * k).toInt(), (cx + 6 + l.xs[j] * k).toInt(), (cy + 6 + l.ys[j] * k).toInt())
                }
            }
            g.color = Color(0x444444); g.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
            g.drawString(label, cx + 8, cy + cell + 12)
        }
        kinds.forEachIndexed { i, kind -> panel(i, kind.name, ComicLines.generate(kind, 0f, 0f, 1000f, 1000f), kind) }
        val base = kinds.size + (cols - kinds.size % cols) % cols
        panel(base, "FOCUS odak (0.25,0.3)", ComicLines.generate(ComicLinesKind.FOCUS, 0f, 0f, 1000f, 1000f, focusX = 0.25f, focusY = 0.3f), ComicLinesKind.FOCUS)
        panel(base + 1, "SPEED_H yön 25°", ComicLines.generate(ComicLinesKind.SPEED_H, 0f, 0f, 1000f, 1000f, angle = 25f), ComicLinesKind.SPEED_H)
        panel(base + 2, "BURST uzunluk 0.5", ComicLines.generate(ComicLinesKind.BURST, 0f, 0f, 1000f, 1000f, length = 0.5f), ComicLinesKind.BURST)
        panel(base + 3, "ZOOM sık 1.6", ComicLines.generate(ComicLinesKind.ZOOM, 0f, 0f, 1000f, 1000f, density = 1.6f), ComicLinesKind.ZOOM)
        g.dispose()
        return img
    }
}
