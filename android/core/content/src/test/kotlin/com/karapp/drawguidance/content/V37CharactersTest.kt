package com.karapp.drawguidance.content

import com.karapp.drawguidance.content.comic.CharExpression
import com.karapp.drawguidance.content.comic.CharKind
import com.karapp.drawguidance.content.comic.CharPose
import com.karapp.drawguidance.content.comic.CharacterDef
import com.karapp.drawguidance.content.comic.ComicCharacters
import com.karapp.drawguidance.content.comic.Skel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File

/**
 * v37 karakterler (anime / çizgi roman stili): 15 tür × 13 ifade × 26 poz. Eski kimlikler (v36'nın 8 ifadesi ve 9
 * pozu) aynı kalır; istenen aksiyon/duygu pozları vardır; her birleşim çizilir, yolları boş değil, sayılar sonlu ve
 * kutunun içinde; pozlar ve ifadeler her türde birbirinden farklı; yeni adların altı dilde çevirisi var. -PpreviewDir
 * ile önizleme sayfaları (v37-characters-*.png).
 */
class V37CharactersTest {

    @Test
    fun keysAreStableAndRequiredPosesExist() {
        assertEquals(15, CharKind.entries.size)
        assertEquals(13, CharExpression.entries.size)
        assertEquals(26, CharPose.entries.size)
        // v36 kimlikleri (kayıtlı çizimler) değişmedi
        assertEquals(
            listOf("calm", "happy", "sad", "angry", "surprised", "scared", "thinking", "shy"),
            CharExpression.entries.take(8).map { it.id },
        )
        assertEquals(listOf("stand", "walk", "run", "sit", "wave", "point", "crossed", "jump", "think"), CharPose.entries.take(9).map { it.id })
        assertEquals(
            listOf("kid", "student", "office", "elder", "teacher", "doctor", "police", "detective", "chef", "athlete", "hero", "astronaut", "robot", "knight", "wizard"),
            CharKind.entries.map { it.id },
        )
        // İstenen pozlar (saldırı, yumruk, tekme, dövüş duruşu, kavga, bağırma, koşma, zıplama, savunma, düşme/yaralı,
        // zafer, ağlama, gülme, düşünme, işaret, oturma, uçma, güç toplama)
        val required = listOf(
            "Saldırıyor", "Yumruk atıyor", "Tekme atıyor", "Dövüş duruşu", "Kavga ediyor", "Bağırıyor", "Koşuyor", "Zıplıyor",
            "Savunuyor", "Yere düşüyor", "Yaralı", "Zafer", "Ağlıyor", "Gülüyor", "Düşünüyor", "İşaret ediyor", "Oturuyor", "Uçuyor", "Güç topluyor",
        )
        val titles = CharPose.entries.map { it.title }
        for (r in required) assertTrue("eksik poz: $r", r in titles)
        for (r in listOf("Öfkeli bağırma", "Kararlı", "Şaşkın", "Alaycı")) assertTrue("eksik ifade: $r", r in CharExpression.entries.map { it.title })
        // Kimlikler benzersiz ve geri çözülür
        assertEquals(CharPose.entries.size, CharPose.entries.map { it.id }.toSet().size)
        assertEquals(CharExpression.entries.size, CharExpression.entries.map { it.id }.toSet().size)
        assertEquals(15 * 13 * 26, ComicCharacters.all.size)
        for (d in ComicCharacters.all.filterIndexed { i, _ -> i % 53 == 0 }) {
            assertEquals(d, ComicCharacters.parse(d.id))
            assertNotNull(ComicCharacters.shape("lib." + d.id + ".m"))
        }
        assertEquals("chr.knight.furious.kick", ComicCharacters.def(CharKind.KNIGHT, CharExpression.FURIOUS, CharPose.KICK).id)
        // Arama: yeni poz ve ifade adlarıyla
        assertEquals(15, ComicCharacters.search("yumruk", expression = CharExpression.HAPPY).size)
        assertEquals(26, ComicCharacters.search("robot alaycı").size)
    }

    @Test
    fun everyCombinationBuildsInsideItsBox() {
        val t0 = System.nanoTime()
        var total = 0L
        val outside = LinkedHashSet<String>()
        for (d in ComicCharacters.all) {
            val paths = ComicCharacters.shape(d.id)!!.paths(96f)
            assertTrue(d.id, paths.size > 60)
            for (p in paths) {
                assertTrue("${d.id} boş yol", p.points.isNotEmpty())
                for (q in p.points) {
                    assertTrue("${d.id} sonlu değil: $q", q.x.isFinite() && q.y.isFinite())
                    assertTrue("${d.id} kutu dışında: $q", q.x in -0.02f..1.02f && q.y in -0.02f..1.02f)
                }
                assertTrue("${d.id} kalınlık", p.width.isFinite() && p.width >= 0f && p.alpha in 0f..1f)
            }
            val b = d.bounds
            // Küçük resimdeki görünür alan (CharactersSheet: 250 birim, zemin y=200): tepe −50'nin altında, taban ≈200 (gölge ≤206), en ≤ 250
            if (!(b[3] >= 100f && b[2] in 40f..250f && b[1] >= -50f && b[1] + b[3] <= 206f)) outside += "${d.kind.id}.${d.pose.id} (y ${b[1].toInt()}, boy ${b[3].toInt()}, en ${b[2].toInt()})"
            total += paths.sumOf { it.points.size }
        }
        assertTrue("${outside.size} karakter çerçeve dışında: ${outside.take(20)}", outside.isEmpty())
        println("${ComicCharacters.all.size} karakter: ${(System.nanoTime() - t0) / 1_000_000} ms, $total nokta")
    }

    private fun sig(d: CharacterDef) = ComicCharacters.shape(d.id)!!.paths(64f).joinToString(";") { p -> p.points.joinToString(",") { "${(it.x * 400).toInt()}:${(it.y * 400).toInt()}" } }

    @Test
    fun posesAndExpressionsDifferForEveryKind() {
        for (k in CharKind.entries) {
            val poses = CharPose.entries.map { sig(ComicCharacters.def(k, CharExpression.CALM, it)) }
            assertEquals("$k pozlar", poses.size, poses.toSet().size)
            val faces = CharExpression.entries.map { sig(ComicCharacters.def(k, it, CharPose.STAND)) }
            assertEquals("$k ifadeler", faces.size, faces.toSet().size)
        }
        // Pozlar gözle görülür biçimde farklı: el ve ayak noktaları en az birinde belirgin farklı
        val look = ComicCharacters.look(CharKind.OFFICE)
        val joints = CharPose.entries.associateWith { p ->
            val s = Skel(look.body, ComicCharacters.pose(p))
            listOf(s.handL, s.handR, s.ankleL, s.ankleR, s.head)
        }
        for ((a, ja) in joints) for ((b, jb) in joints) if (a.ordinal < b.ordinal) {
            val diff = ja.zip(jb).maxOf { (u, v) -> kotlin.math.hypot(u.x - v.x, u.y - v.y) }
            val extra = ComicCharacters.pose(a).fx != ComicCharacters.pose(b).fx || ComicCharacters.pose(a).mod != ComicCharacters.pose(b).mod
            assertTrue("$a ve $b çok benzer ($diff)", diff > 8f || (diff > 3f && extra))
        }
        // Çocuk yetişkinden kısa, kahraman en uzun
        fun h(k: CharKind) = ComicCharacters.def(k, CharExpression.CALM, CharPose.STAND).bounds[3]
        assertTrue(h(CharKind.KID) < h(CharKind.OFFICE))
        assertTrue(h(CharKind.HERO) > h(CharKind.OFFICE))
    }

    @Test
    fun newNamesAreTranslatedInAllSixLanguages() {
        val dir = listOf(File("../../app/src/main/resources/i18n"), File("android/app/src/main/resources/i18n")).firstOrNull { it.isDirectory }
            ?: return println("i18n klasörü bulunamadı; atlandı")
        val names = CharKind.entries.map { it.title } + CharExpression.entries.map { it.title } + CharPose.entries.map { it.title }
        for (lang in listOf("en", "zh", "ja", "ru", "ko", "ar")) {
            val keys = File(dir, "$lang.tsv").readLines().mapNotNull { l -> l.split('\t').takeIf { it.size >= 2 && it[1].isNotBlank() }?.get(0) }.toSet()
            val missing = names.filter { it !in keys && it != "Robot" }
            assertTrue("$lang çevirisi eksik: $missing", missing.isEmpty())
        }
    }

    @Test
    fun previewSheets() {
        if (Preview.dir == null) return
        Preview.write(sheet("Türler (mutlu, ayakta)", CharKind.entries.map { ComicCharacters.def(it, CharExpression.HAPPY, CharPose.STAND) }, 5, 330), "v37-characters-kinds")
        Preview.write(sheet("Türler (kararlı, dövüş duruşu)", CharKind.entries.map { ComicCharacters.def(it, CharExpression.DETERMINED, CharPose.STANCE) }, 5, 330), "v37-characters-kinds-stance")
        for (k in CharKind.entries) {
            Preview.write(sheet("${k.title} · pozlar", CharPose.entries.map { ComicCharacters.def(k, poseFace(it), it) }, 7, 300), "v37-characters-poses-${k.id}")
        }
        for (k in listOf(CharKind.STUDENT, CharKind.HERO, CharKind.ROBOT, CharKind.KID, CharKind.WIZARD, CharKind.DETECTIVE)) {
            Preview.write(faceSheet("${k.title} · ifadeler", CharExpression.entries.map { ComicCharacters.def(k, it, CharPose.STAND) }, 5, 260), "v37-characters-faces-${k.id}")
        }
        Preview.write(faceSheet("Yüzler", CharKind.entries.map { ComicCharacters.def(it, CharExpression.CALM, CharPose.STAND) }, 5, 260), "v37-characters-faces-all")
        Preview.write(faceSheet("Yakın", listOf(CharKind.KID, CharKind.STUDENT, CharKind.OFFICE).map { ComicCharacters.def(it, CharExpression.CALM, CharPose.STAND) }, 3, 520), "v37-characters-face-zoom")
        // Uygulamadaki küçük resim boyunda (96 px yükseklik)
        Preview.write(sheet("Küçük resim", CharKind.entries.map { ComicCharacters.def(it, CharExpression.HAPPY, CharPose.STAND) }, 15, 110), "v37-characters-thumbs")
    }

    /** Pozun önizlemesi için uygun ifade (bağırırken öfkeli, ağlarken üzgün...). */
    private fun poseFace(p: CharPose): CharExpression = when (p) {
        CharPose.ATTACK, CharPose.SHOUT, CharPose.FIGHT -> CharExpression.FURIOUS
        CharPose.PUNCH, CharPose.KICK, CharPose.STANCE, CharPose.CHARGE, CharPose.BLOCK, CharPose.FLY -> CharExpression.DETERMINED
        CharPose.CRY, CharPose.HURT -> CharExpression.SAD
        CharPose.COWER -> CharExpression.SCARED
        CharPose.FALL -> CharExpression.SHOCKED
        CharPose.THINK -> CharExpression.THINKING
        CharPose.CROSSED -> CharExpression.SMUG
        CharPose.VSIGN, CharPose.JUMP, CharPose.VICTORY -> CharExpression.EXCITED
        else -> CharExpression.HAPPY
    }

    /** Karakterler aynı ölçekte (tasarım birimi → piksel), altında adlarıyla. */
    private fun sheet(title: String, defs: List<CharacterDef>, columns: Int, cell: Int = 260): BufferedImage {
        val rows = (defs.size + columns - 1) / columns
        val img = BufferedImage(columns * cell, rows * (cell + 20) + 40, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(0xF4F2EC); g.fillRect(0, 0, img.width, img.height)
        g.color = Color(0x333333); g.font = Font(Font.SANS_SERIF, Font.BOLD, 18)
        g.drawString(title, 10, 26)
        val unit = (cell - 12) / 262f
        defs.forEachIndexed { i, d ->
            val cx = (i % columns) * cell; val cy = 40 + (i / columns) * (cell + 20)
            val b = d.bounds
            val w = b[2] * unit; val h = b[3] * unit
            val x = cx + (cell - w) / 2f
            val y = cy + cell - 6f - (200f - b[1]) * unit
            Preview.draw(g, ComicCharacters.shape(d.id)!!.paths(kotlin.math.max(w, h)), x, y, w, h, inkWidth = unit)
            if (cell >= 160) {
                g.color = Color(0x444444); g.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
                g.drawString("${d.kind.title} · ${d.expression.title} · ${d.pose.title}", cx + 6, cy + cell + 14)
            }
        }
        g.dispose()
        return img
    }

    /** Yüz yakın çekimi: karakter büyük çizilir, baş bölgesi kırpılır. */
    private fun faceSheet(title: String, defs: List<CharacterDef>, columns: Int, cell: Int): BufferedImage {
        val rows = (defs.size + columns - 1) / columns
        val img = BufferedImage(columns * cell, rows * (cell + 20) + 40, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(0xF4F2EC); g.fillRect(0, 0, img.width, img.height)
        g.color = Color(0x333333); g.font = Font(Font.SANS_SERIF, Font.BOLD, 18)
        g.drawString(title, 10, 26)
        defs.forEachIndexed { i, d ->
            val cx = (i % columns) * cell; val cy = 40 + (i / columns) * (cell + 20)
            val look = ComicCharacters.look(d.kind)
            val s = Skel(look.body, ComicCharacters.pose(d.pose))
            val unit = cell / (look.body.headR * 5.2f)
            val b = d.bounds
            val w = b[2] * unit; val h = b[3] * unit
            val x = cx + cell / 2f - (s.head.x - b[0]) * unit
            val y = cy + cell * 0.52f - (s.head.y - b[1]) * unit
            val clip = g.clip
            g.clip(Rectangle2D.Float(cx.toFloat(), cy.toFloat(), cell.toFloat(), cell.toFloat()))
            Preview.draw(g, ComicCharacters.shape(d.id)!!.paths(kotlin.math.max(w, h)), x, y, w, h, inkWidth = unit)
            g.clip = clip
            g.color = Color(0x444444); g.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
            g.drawString("${d.kind.title} · ${d.expression.title}", cx + 6, cy + cell + 14)
        }
        g.dispose()
        return img
    }
}
