package com.karapp.drawguidance.content

import com.karapp.drawguidance.content.comic.ComicTemplates
import com.karapp.drawguidance.content.comic.PanelNode
import com.karapp.drawguidance.content.comic.PanelPage
import com.karapp.drawguidance.content.comic.PanelShape
import com.karapp.drawguidance.content.comic.TemplateGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import kotlin.math.abs

/**
 * v36 (8.6) çizgi roman şablonları: tek, iki, üç, dört, altı panel; dikey webtoon; manga; aksiyon, komedi,
 * romantizm, korku, fantastik, bilim kurgu. Paneller sayfanın içinde, üst üste binmez, aralıklar eşit; düzen metne
 * yazılıp geri okunur; boyut, sıra, aralık, kenar boşluğu, ekleme ve silme çalışır. -PpreviewDir ile önizleme.
 */
class V36TemplatesTest {
    private val w = 1000f
    private val h = 1414f

    @Test
    fun templatesCoverRequestedLayouts() {
        val all = ComicTemplates.all
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertTrue(all.size >= 20)
        val counts = all.associate { it.id to it.panelCount }
        assertEquals(1, counts["single"])
        assertEquals(2, counts["two_rows"]); assertEquals(2, counts["two_cols"])
        assertEquals(3, counts["three_rows"]); assertEquals(3, counts["three_big"])
        assertEquals(4, counts["four_grid"]); assertEquals(6, counts["six_grid"]); assertEquals(6, counts["six_mix"])
        for (g in TemplateGroup.entries) assertTrue(g.id, ComicTemplates.inGroup(g).isNotEmpty())
        for (id in listOf("webtoon", "manga", "action", "comedy", "romance", "horror", "fantasy", "scifi")) assertNotNull(id, ComicTemplates.byId(id))
        assertTrue(ComicTemplates.inGroup(TemplateGroup.MANGA).all { it.page.rtl })
    }

    @Test
    fun panelsInsidePageAndNotOverlapping() {
        for (t in ComicTemplates.all) {
            val shapes = t.page.shapes(w, h)
            assertEquals(t.id, t.panelCount, shapes.size)
            assertEquals(t.id, (1..shapes.size).toList(), shapes.map { it.number })
            for (s in shapes) {
                val b = s.bounds()
                assertTrue("${t.id} ${s.id} sayfa dışında", b[0] >= 0f && b[1] >= 0f && b[2] <= w && b[3] <= h)
                assertTrue("${t.id} ${s.id} çok küçük", b[2] - b[0] > 40f && b[3] - b[1] > 40f)
            }
            // Ağaç panelleri birbirine binmez (iç paneller binebilir): her panelin merkezi başka panelin içinde değil
            val tree = shapes.filter { !it.inset }
            for (a in tree) for (b in tree) if (a !== b) {
                val c = center(a)
                assertTrue("${t.id}: ${a.id} ${b.id} üst üste", !b.contains(c[0], c[1]))
            }
            // Metin biçimi geri okunur
            val decoded = PanelPage.decode(t.page.encode())
            assertEquals(t.id, t.page, decoded)
        }
    }

    @Test
    fun gutterIsEqualEverywhere() {
        val page = ComicTemplates.byId("four_grid")!!.page
        val s = page.shapes(w, h)
        val g = PanelPage.DEFAULT_GUTTER * w
        val b = s.map { it.bounds() }
        assertEquals(g, b[1][0] - b[0][2], 0.5f)   // yatay aralık
        assertEquals(g, b[2][1] - b[0][3], 0.5f)   // dikey aralık
        assertEquals(b[0][2] - b[0][0], b[1][2] - b[1][0], 0.5f) // eşit genişlik
        val m = PanelPage.DEFAULT_MARGIN * w
        assertEquals(m, b[0][0], 0.5f); assertEquals(m, b[0][1], 0.5f)
        // Eğik çizgide de dik uzaklık aralığa eşit
        val action = ComicTemplates.byId("action")!!.page.shapes(w, h)
        assertTrue(action.any { it.size >= 4 && !isAxisAligned(it) })
        // Daha geniş aralık ve kenar
        val wide = page.withGutter(0.05f).withMargin(0.08f).shapes(w, h).map { it.bounds() }
        assertEquals(0.05f * w, wide[1][0] - wide[0][2], 0.5f)
        assertEquals(0.08f * w, wide[0][0], 0.5f)
        assertEquals(PanelPage.MAX_GUTTER, page.withGutter(1f).gutter)
    }

    @Test
    fun mangaReadsRightToLeft() {
        val s = ComicTemplates.byId("manga")!!.page.shapes(w, h)
        // İkinci satır: 2 numaralı panel sağda, 3 solda
        val two = s.first { it.number == 2 }.bounds(); val three = s.first { it.number == 3 }.bounds()
        assertTrue(two[0] > three[0])
        val ltr = ComicTemplates.byId("four_grid")!!.page.shapes(w, h)
        assertTrue(ltr.first { it.number == 1 }.bounds()[0] < ltr.first { it.number == 2 }.bounds()[0])
    }

    @Test
    fun resizeMoveAddAndDelete() {
        var page = ComicTemplates.byId("three_big")!!.page
        val (top, left, right) = page.leafIds()
        // Boyut: üst panel sayfanın yarısı
        assertEquals(1.4f / 2.4f, page.heightOf(top)!!, 1e-4f)
        assertNull(page.widthOf(top))
        page = page.setHeight(top, 0.5f)
        assertEquals(0.5f, page.heightOf(top)!!, 1e-4f)
        assertEquals(0.5f, page.widthOf(left)!!, 1e-4f)
        page = page.setWidth(left, 0.7f)
        val sb = page.shapes(w, h).associateBy { it.id }
        assertTrue(sb.getValue(left).bounds().let { it[2] - it[0] } > 2f * sb.getValue(right).bounds().let { it[2] - it[0] })
        assertEquals(PanelPage.MAX_PART, page.setWidth(left, 5f).widthOf(left)!!, 1e-4f)
        // Sıra: aynı bölmede komşuyla yer değiştirir ve boyutunu götürür
        val moved = page.move(left, forward = true)!!
        assertEquals(listOf(top, right, left), moved.leafIds())
        val mb = moved.shapes(w, h).associateBy { it.id }
        assertEquals(0.7f, moved.widthOf(left)!!, 1e-4f)
        assertTrue(mb.getValue(left).bounds()[0] > mb.getValue(right).bounds()[0])
        assertNull(page.move(top, forward = false))
        // Bölmenin ucunda: okuma sırasındaki komşuyla (farklı bölme) yer değişir
        val across = page.move(top, forward = true)!!
        assertEquals(listOf(left, top, right), across.leafIds())
        // Ekle: yanına, altına, sona
        val (split1, a) = page.split(right, rows = false)!!
        assertEquals(listOf(top, left, right, a), split1.leafIds())
        val (split2, b) = split1.split(top, rows = true)!!
        assertEquals(listOf(top, b, left, right, a), split2.leafIds())
        val (appended, c) = split2.append()
        assertEquals(c, appended.leafIds().last())
        assertEquals(6, appended.shapes(w, h).size)
        // Sil: komşular yeri doldurur; son panel silinince düzen boşalır
        var shrunk = appended.remove(a).remove(b)
        assertEquals(listOf(top, left, right, c), shrunk.leafIds())
        val rb = shrunk.shapes(w, h).associateBy { it.id }
        val inner = w - 2 * PanelPage.DEFAULT_MARGIN * w
        assertEquals(inner, rb.getValue(top).bounds().let { it[2] - it[0] }, 0.5f)
        for (id in shrunk.leafIds()) shrunk = shrunk.remove(id)
        assertTrue(shrunk.isEmpty)
        assertNull(shrunk.root)
        assertEquals(PanelPage.decode(appended.encode()), appended)
    }

    @Test
    fun mangaAddsToTheLeftAndInsetsResize() {
        val page = ComicTemplates.byId("manga")!!.page
        val first = page.leafIds()[1]
        val (added, id) = page.split(first, rows = false)!!
        val s = added.shapes(w, h).associateBy { it.id }
        // Sağdan sola: yeni panel solda ve hemen arkasından okunur
        assertTrue(s.getValue(id).bounds()[0] < s.getValue(first).bounds()[0])
        assertEquals(added.leafIds().indexOf(first) + 1, added.leafIds().indexOf(id))
        // İç panel (romantizm): büyük panelin arkasından okunur, büyütülür, silinir
        val romance = ComicTemplates.byId("romance")!!.page
        val inset = romance.insets.single()
        assertEquals(1, romance.leafIds().indexOf(inset.id))
        assertTrue(romance.shapes(w, h).first { it.id == inset.id }.inset)
        val bigger = romance.setWidth(inset.id, 0.5f)
        assertEquals(0.5f, bigger.widthOf(inset.id)!!, 1e-4f)
        assertNull(romance.move(inset.id, true))
        assertNull(romance.split(inset.id, true))
        assertEquals(3, romance.remove(inset.id).leafIds().size)
        // Bozuk metin
        assertNull(PanelPage.decode("P1|x"))
        assertNull(PanelPage.decode("bozuk"))
    }

    @Test
    fun previewSheet() {
        if (Preview.dir == null) return
        val all = ComicTemplates.all
        val cols = 6; val cw = 210; val ch = 330
        val rows = (all.size + cols - 1) / cols
        val img = BufferedImage(cols * cw, rows * ch + 40, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(0xE9E6DE); g.fillRect(0, 0, img.width, img.height)
        g.color = Color(0x333333); g.font = Font(Font.SANS_SERIF, Font.BOLD, 18)
        g.drawString("Şablonlar (${all.size})", 10, 26)
        all.forEachIndexed { i, t ->
            val ox = (i % cols) * cw + 15f; val oy = 40 + (i / cols) * ch + 6f
            val pw = 180f; val ph = pw * h / w
            g.color = Color.WHITE; g.fill(java.awt.geom.Rectangle2D.Float(ox, oy, pw, ph))
            for (s in t.page.shapes(pw, ph)) {
                val path = Path2D.Float()
                path.moveTo(ox + s.x(0), oy + s.y(0))
                for (k in 1 until s.size) path.lineTo(ox + s.x(k), oy + s.y(k))
                path.closePath()
                if (s.inset) { g.color = Color.WHITE; g.fill(path) }
                g.color = Color(0x111111); g.stroke = BasicStroke(1.6f * t.line); g.draw(path)
                val c = center(s)
                g.font = Font(Font.SANS_SERIF, Font.BOLD, 11); g.color = Color(0x2563EB)
                g.drawString("${s.number}", ox + c[0] - 3, oy + c[1] + 4)
            }
            g.color = Color(0x333333); g.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
            g.drawString(t.title, ox, oy + ph + 16)
        }
        g.dispose()
        Preview.write(img, "v36-templates")
    }

    private fun center(s: PanelShape): FloatArray {
        val b = s.bounds()
        return floatArrayOf((b[0] + b[2]) / 2f, (b[1] + b[3]) / 2f)
    }

    private fun isAxisAligned(s: PanelShape): Boolean {
        for (i in 0 until s.size) {
            val j = (i + 1) % s.size
            if (abs(s.x(i) - s.x(j)) > 0.5f && abs(s.y(i) - s.y(j)) > 0.5f) return false
        }
        return true
    }

    @Suppress("unused")
    private fun leaf(n: PanelNode?) = n as? PanelNode.Leaf
}
