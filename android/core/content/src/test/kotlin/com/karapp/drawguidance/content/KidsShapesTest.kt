package com.karapp.drawguidance.content

import com.karapp.drawguidance.content.fill.RegionFill
import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.library.kids.KID_INK
import com.karapp.drawguidance.content.library.kids.KID_OUTLINE
import com.karapp.drawguidance.content.library.kids.KID_DETAIL
import com.karapp.drawguidance.content.library.kids.KID_PAPER
import com.karapp.drawguidance.content.library.kids.KidPen
import com.karapp.drawguidance.content.library.kids.KidsShapes
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min

/**
 * Çocuklar için çizim: 86 boyama kitabı çizimi (v34: +36 çizgi film türü karakter, yer ve nesne). Kimlikler tekil, çizimler birim kutuda, her çizimde 3–12 beyaz dolgulu
 * kalın kenarlı parça; her parçanın görünen her bölgesine dokununca uygulamadaki Alan boya ([RegionFill], aynı girdilerle)
 * o bölgenin tamamını bulur (gizli çizgi alanı bölmez, alan dışarı taşmaz). -PpreviewDir ile önizleme sayfaları.
 */
class KidsShapesTest {

    private val shapes: List<LibShape> get() = KidsShapes.categories.flatMap { it.shapes }

    /** Uygulamadaki öntanımlı şekil kalem kalınlığı (ShapeStyle.strokeWidth). */
    private val pen = 4f

    /** Testte şeklin yerleştirildiği boy (uzun kenar, tuval birimi) ve kenar payı. */
    private val size = 500
    private val pad = 12

    private fun isRegion(p: VPath) = p.closed && p.label == null && p.fill == KID_PAPER && p.width > 0f

    @Test
    fun catalog() {
        assertEquals(86, shapes.size)
        assertEquals(86, shapes.map { it.id }.toSet().size)
        assertEquals(10, KidsShapes.categories.size)
        KidsShapes.categories.forEach { c ->
            assertEquals(Audience.KIDS, c.audience)
            assertEquals("Çocuklar", c.section)
            assertTrue(c.id, c.listed && c.title.isNotBlank() && c.shapes.size in 6..14)
        }
        shapes.forEach { s ->
            assertTrue(s.id, s.id.matches(Regex("^kid_[a-z0-9_]+$")))
            assertTrue(s.id, s.title.isNotBlank() && s.tags.size >= 4)
            assertTrue(s.id, s.aspect in 0.5f..2.2f)
            assertTrue(s.id, ShapeLibrary.find("lib.${s.id}") === s)
        }
        // Kütüphane genelinde kimlikler tekil (ShapeLibrary kurulurken de denetler)
        val all = ShapeLibrary.categories.flatMap { it.shapes }
        assertEquals(all.size, all.map { it.id }.toSet().size)
        // Çocuk kategorileri yalnız çocuk modunda
        val kidIds = KidsShapes.categories.map { it.id }.toSet()
        for (a in listOf(Audience.ART, Audience.NOTES, Audience.BOTH)) {
            assertTrue("$a", ShapeLibrary.forAudience(a).none { it.audience == Audience.KIDS || it.id in kidIds })
        }
        assertEquals(kidIds, ShapeLibrary.forAudience(Audience.KIDS).map { it.id }.toSet())
        assertTrue(ShapeLibrary.forAudience(Audience.ART).isNotEmpty() && ShapeLibrary.forAudience(Audience.NOTES).isNotEmpty())
    }

    @Test
    fun styleAndBounds() {
        val dropped = ArrayList<String>()
        shapes.forEach { s ->
            KidPen.dropped.clear()
            val paths = s.paths(size.toFloat())
            KidPen.dropped.forEach { dropped += "${s.id}: $it" }
            paths.forEach { p ->
                p.points.forEach { pt -> assertTrue("${s.id}: kutu dışı $pt", pt.x in -0.01f..1.01f && pt.y in -0.01f..1.01f) }
                if (p.width > 0f) assertEquals("${s.id}: kenar rengi", KID_INK, p.stroke)
                if (p.closed) assertTrue("${s.id}: dolgu ${p.fill}", p.fill == KID_PAPER || p.fill == KID_INK)
                else assertEquals("${s.id}: açık çizgi dolgusuz", null, p.fill)
                // v47 desen parçaları ince kenarlı ve etiketli
                if (isRegion(p)) assertEquals(s.id, if (p.tag == "kid_detail") KID_DETAIL else KID_OUTLINE, p.width, 1e-6f)
            }
            val regions = paths.count { isRegion(it) && it.tag != "kid_detail" }
            assertTrue("${s.id}: $regions bölge", regions in 3..12)
            val details = paths.count { isRegion(it) && it.tag == "kid_detail" }
            assertTrue("${s.id}: $details desen", details <= 32)
            assertTrue("${s.id}: çok fazla nokta", paths.sumOf { it.points.size } < 20_000)
        }
        assertTrue("Atılan parçalar: $dropped", dropped.isEmpty())
    }

    /** Bir şeklin tuvaldeki yerleşimi: birim kutu → piksel/tuval birimi. */
    private class Placed(val shape: LibShape, val w: Int, val h: Int, val pad: Int, val paths: List<VPath>) {
        fun map(p: P) = P(pad + p.x * w, pad + p.y * h)
    }

    private fun place(s: LibShape): Placed {
        val w = if (s.aspect >= 1f) size else (size * s.aspect).toInt()
        val h = if (s.aspect >= 1f) (size / s.aspect).toInt() else size
        return Placed(s, w, h, pad, s.paths(max(w, h).toFloat()))
    }

    /** Uygulamadaki ShapeController.wallsOf ile aynı: kenarlı parçalar kalınlığıyla, kenarsız dolgulu kapalılar ince. */
    private fun walls(pl: Placed): List<RegionFill.Line> = pl.paths.mapNotNull { p ->
        if (p.label != null || p.tag == "guide" || p.points.size < 2) return@mapNotNull null
        val width = when {
            p.width > 0f -> max(0.3f, pen * p.width)
            p.closed && p.fill != null -> 0.3f
            else -> return@mapNotNull null
        }
        RegionFill.Line(p.points.map { pl.map(it) }, p.closed, width)
    }

    /** Uygulamadaki ShapeController.regionAt: önce tam, bulunamazsa küçük boşluklar kapatılarak. */
    private fun regionAt(walls: List<RegionFill.Line>, tap: P): Pair<List<P>?, Boolean> {
        RegionFill.region(walls, tap)?.let { return it to false }
        return RegionFill.region(walls, tap, gap = (pen * 1.5f).coerceIn(1.5f, 8f)) to true
    }

    /** Uygulamadaki çizim sırasıyla kimlik görüntüsü: her parça önce kendi kimliğiyle dolar, sonra kenarı çizilir. */
    private fun idImage(pl: Placed): IntArray {
        val iw = pl.w + 2 * pl.pad; val ih = pl.h + 2 * pl.pad
        val img = BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        g.color = Color(0); g.fillRect(0, 0, iw, ih)
        pl.paths.forEachIndexed { i, p ->
            if (p.label != null || p.points.size < 2) return@forEachIndexed
            val path = Path2D.Float()
            p.points.forEachIndexed { k, pt -> val q = pl.map(pt); if (k == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y) }
            if (p.closed) path.closePath()
            if (p.closed && p.fill != null) { g.color = Color(i + 1); g.fill(path) }
            if (p.width > 0f) {
                g.color = Color(WALL)
                g.stroke = BasicStroke(pen * p.width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.draw(path)
            }
        }
        g.dispose()
        return img.getRGB(0, 0, iw, ih, null, 0, iw).map { it and 0xFFFFFF }.toIntArray()
    }

    /** Görünen bölge: piksel sayısı, 2 piksel çizginin altına genişletilmiş hali (üst sınır), en içteki nokta. */
    private class Comp(val size: Int, val grown: Int, val tap: P, val depth: Int, val part: Int)

    /** Kimliği [id] olan piksellerin bağlı bileşenleri; her birinin en içteki noktası (sınıra en uzak piksel). */
    private fun components(ids: IntArray, iw: Int, ih: Int, id: Int): List<Comp> {
        val seen = BooleanArray(ids.size)
        val out = ArrayList<Comp>()
        val queue = IntArray(ids.size)
        val dist = IntArray(ids.size) { -1 }
        for (start in ids.indices) {
            if (ids[start] != id || seen[start]) continue
            var qh = 0; var qt = 0
            queue[qt++] = start; seen[start] = true
            val members = ArrayList<Int>()
            while (qh < qt) {
                val i = queue[qh++]; members += i
                val x = i % iw; val y = i / iw
                for (j in intArrayOf(if (x > 0) i - 1 else -1, if (x < iw - 1) i + 1 else -1, if (y > 0) i - iw else -1, if (y < ih - 1) i + iw else -1)) {
                    if (j >= 0 && !seen[j] && ids[j] == id) { seen[j] = true; queue[qt++] = j }
                }
            }
            // Sınırdan içeri uzaklık (BFS)
            qh = 0; qt = 0
            for (i in members) {
                val x = i % iw; val y = i / iw
                val edge = x == 0 || y == 0 || x == iw - 1 || y == ih - 1 || ids[i - 1] != id || ids[i + 1] != id || ids[i - iw] != id || ids[i + iw] != id
                if (edge) { dist[i] = 0; queue[qt++] = i }
            }
            var best = members[0]
            while (qh < qt) {
                val i = queue[qh++]
                if (dist[i] > dist[best]) best = i
                val x = i % iw; val y = i / iw
                for (j in intArrayOf(if (x > 0) i - 1 else -1, if (x < iw - 1) i + 1 else -1, if (y > 0) i - iw else -1, if (y < ih - 1) i + iw else -1)) {
                    if (j >= 0 && ids[j] == id && dist[j] < 0) { dist[j] = dist[i] + 1; queue[qt++] = j }
                }
            }
            // Çizginin altına 2 piksel genişleme (Alan boya sınırı çizginin iç kenarının biraz altına girer)
            val grown = HashSet<Int>(members)
            var ring: Collection<Int> = members
            repeat(2) {
                val next = ArrayList<Int>()
                for (i in ring) {
                    val x = i % iw; val y = i / iw
                    for (dy in -1..1) for (dx in -1..1) {
                        val nx = x + dx; val ny = y + dy
                        if (nx < 0 || ny < 0 || nx >= iw || ny >= ih) continue
                        val j = ny * iw + nx
                        if (ids[j] == WALL && grown.add(j)) next += j
                    }
                }
                ring = next
            }
            out += Comp(members.size, grown.size, P(best % iw + 0.5f, best / iw + 0.5f), dist[best], id - 1)
        }
        return out
    }

    /** Boyanan bölgeler (renkli önizleme için): şekil kimliği → (bölge çokgeni, renk sırası). */
    private val filled = HashMap<String, MutableList<List<P>>>()

    @Test
    fun everyPartFillsByTap() {
        val problems = ArrayList<String>()
        var checked = 0
        var worst = 1f to ""
        shapes.forEach { s ->
            val pl = place(s)
            val iw = pl.w + 2 * pl.pad; val ih = pl.h + 2 * pl.pad
            val ids = idImage(pl)
            val w = walls(pl)
            val regions = ArrayList<List<P>>()
            val comps = ArrayList<Comp>()
            pl.paths.forEachIndexed { i, p ->
                if (!isRegion(p)) return@forEachIndexed
                val mine = components(ids, iw, ih, i + 1)
                // Görünen ama parmakla dokunulamayacak kadar küçük kırpıntı kalmamalı
                // (12 pikselden küçükler çizgi kavşaklarındaki tarama kırıntısı, çizimde görünmez)
                mine.filter { it.size in 12 until MIN_PX }.forEach { problems += "${s.id}#$i: çok küçük görünen parça (${it.size} px)" }
                val big = mine.filter { it.size >= MIN_PX }
                if (big.isEmpty()) problems += "${s.id}#$i: görünür alan yok"
                comps += big
            }
            comps.forEach { c ->
                checked++
                val (poly, usedGap) = regionAt(w, c.tap)
                if (poly == null) { problems += "${s.id}#${c.part}: alan bulunamadı (${c.tap})"; return@forEach }
                if (usedGap) problems += "${s.id}#${c.part}: alan yalnız boşluk kapatılarak bulundu"
                val area = RegionFill.areaOf(poly)
                // Alt sınır: görünen alanın tamamı (gizli çizgi bölmüyor); üst sınır: çizgi altına 2 px taşma
                if (area < c.size * 0.92f || area > c.grown * 1.03f) {
                    problems += "${s.id}#${c.part}: alan ${area.toInt()} px, görünen ${c.size}, üst sınır ${c.grown}"
                }
                // Başka bir bölgeye taşmıyor
                comps.forEach { o -> if (o !== c && RegionFill.contains(poly, o.tap)) problems += "${s.id}#${c.part}: #${o.part} bölgesine taşıyor" }
                val ratio = area / c.size
                if (kotlin.math.abs(ratio - 1f) > kotlin.math.abs(worst.first - 1f)) worst = ratio to "${s.id}#${c.part} (${c.size} px)"
                regions += poly
            }
            filled[s.id] = regions
        }
        println("KidsShapesTest: $checked bölge denetlendi; en uzak oran ${worst.first} ${worst.second}")
        writeSheets()
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    // --- Önizleme sayfaları ---

    private val palette = listOf(
        0xFF5A5F, 0xFFB400, 0x00A699, 0x7B61FF, 0x3FA9F5, 0x8CD867, 0xFF8ACB, 0xFF7A1A, 0x2EC4B6, 0xF7DC6F, 0xA066E0, 0x5BC0EB,
    ).map { Color(it) }

    private fun sheet(list: List<LibShape>, cell: Int, columns: Int, color: Boolean, title: String): BufferedImage {
        val rows = (list.size + columns - 1) / columns
        val img = BufferedImage(columns * cell, rows * (cell + 24) + 44, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(0xEEF0F4); g.fillRect(0, 0, img.width, img.height)
        g.color = Color(0x333333); g.font = Font(Font.SANS_SERIF, Font.BOLD, 20)
        g.drawString(title, 12, 30)
        list.forEachIndexed { i, s ->
            val cx = (i % columns) * cell
            val cy = 44 + (i / columns) * (cell + 24)
            g.color = Color.WHITE; g.fillRoundRect(cx + 5, cy + 5, cell - 10, cell - 10, 14, 14)
            val inner = cell - 30f
            val w = if (s.aspect >= 1f) inner else inner * s.aspect
            val h = if (s.aspect >= 1f) inner / s.aspect else inner
            val x0 = cx + (cell - w) / 2f; val y0 = cy + (cell - h) / 2f
            // Uygulamadaki gibi: kalem kalınlığı 4, şekil ~500 birim boyunda yerleştirilmiş gibi ölçeklenir
            Preview.draw(g, s.paths(inner), x0, y0, w, h, inkWidth = pen * max(w, h) / size, ink = Color(0x2A2A33))
            if (color) {
                val pl = place(s)
                filled[s.id]?.forEachIndexed { k, poly ->
                    val path = Path2D.Float()
                    poly.forEachIndexed { j, q ->
                        val px = x0 + (q.x - pl.pad) / pl.w * w; val py = y0 + (q.y - pl.pad) / pl.h * h
                        if (j == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.closePath()
                    g.color = palette[(k * 5 + i) % palette.size]
                    g.fill(path)
                }
            }
            g.color = Color(0x444444); g.font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
            g.drawString(s.title, cx + 10, cy + cell + 12)
        }
        g.dispose()
        return img
    }

    private fun writeSheets() {
        if (Preview.dir == null) return
        Preview.write(sheet(shapes, 200, 10, false, "Çocuklar için çizim (${shapes.size})"), "kids-sheet")
        Preview.write(sheet(shapes, 200, 10, true, "Çocuklar için çizim — Alan boya ile boyanmış (${shapes.size})"), "kids-sheet-color")
        KidsShapes.categories.forEach { c ->
            Preview.write(sheet(c.shapes, 300, 4, false, c.title), "kids-${c.id}")
            Preview.write(sheet(c.shapes, 300, 4, true, c.title), "kids-${c.id}-color")
        }
    }

    private companion object {
        const val WALL = 0xFFFFFF
        /** 500 birimlik yerleşimde en küçük görünen bölge (≈ 13×13 px, parmak ucuyla dokunulabilir). */
        const val MIN_PX = 170
    }
}
