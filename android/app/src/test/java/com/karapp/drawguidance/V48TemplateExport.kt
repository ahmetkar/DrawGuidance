package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BalloonStyle
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.drawing.TailDirection
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v48 boş çizgi roman şablonlarının dillere göre adları ve açıklamaları. */
object V48TemplateInfo {
    private fun l(tr: String, en: String, ar: String, ja: String, ko: String, ru: String, zh: String) =
        mapOf("tr" to tr, "en" to en, "ar" to ar, "ja" to ja, "ko" to ko, "ru" to ru, "zh" to zh)

    val INFO: Map<String, Pair<Map<String, String>, Map<String, String>>> = mapOf(
        "comic_blank_pages" to (
            l("Çizgi roman sayfaları", "Comic pages", "صفحات قصة مصورة", "コミックのページ", "코믹 페이지", "Страницы комикса", "漫画页面") to
                l(
                    "4 sayfa, her biri farklı panel düzeninde; konuşma balonları ve anlatı kutuları soldan sağa okuma sırasında.",
                    "4 pages, each with a different panel layout; speech balloons and captions in left-to-right reading order.",
                    "4 صفحات بتخطيط لوحات مختلف لكل منها؛ فقاعات الكلام ومربعات السرد بترتيب القراءة من اليسار إلى اليمين.",
                    "4ページ、それぞれ別のコマ割り。フキダシとナレーションは左から右の読み順で配置。",
                    "4페이지, 페이지마다 다른 칸 배치. 말풍선과 내레이션은 왼쪽에서 오른쪽 읽기 순서로.",
                    "4 страницы с разной раскладкой панелей; баллоны и подписи по порядку чтения слева направо.",
                    "4页，每页分格不同；对白气泡和旁白框按从左到右的阅读顺序排列。",
                )
            ),
        "comic_blank_manga" to (
            l("Manga sayfaları", "Manga pages", "صفحات مانغا", "マンガのページ", "만화 페이지", "Страницы манги", "日漫页面") to
                l(
                    "Sağdan sola okunan 4 sayfa, her biri farklı panel düzeninde; balonlar manga okuma sırasında.",
                    "4 right-to-left pages, each with a different panel layout; balloons in manga reading order.",
                    "4 صفحات تُقرأ من اليمين إلى اليسار بتخطيطات مختلفة؛ الفقاعات بترتيب قراءة المانغا.",
                    "右から左に読む4ページ、それぞれ別のコマ割り。フキダシはマンガの読み順で配置。",
                    "오른쪽에서 왼쪽으로 읽는 4페이지, 페이지마다 다른 칸 배치. 말풍선은 만화 읽기 순서로.",
                    "4 страницы справа налево с разной раскладкой; баллоны в порядке чтения манги.",
                    "4页从右向左阅读，每页分格不同；气泡按日漫阅读顺序排列。",
                )
            ),
        "comic_blank_webtoon" to (
            l("Webtoon bölümü", "Webtoon episode", "حلقة ويبتون", "縦読み漫画のエピソード", "웹툰 에피소드", "Эпизод вебтуна", "条漫章节") to
                l(
                    "Telefon için 4 dikey sayfa, her biri farklı düzende; balonlar panellerde ve aralardaki boşluklarda.",
                    "4 vertical phone pages, each with a different layout; balloons inside panels and in the gaps between them.",
                    "4 صفحات عمودية للهاتف بتخطيطات مختلفة؛ الفقاعات داخل اللوحات وفي الفراغات بينها.",
                    "スマホ向け縦4ページ、それぞれ別の構成。フキダシはコマの中とコマ間の余白にも。",
                    "휴대폰용 세로 4페이지, 페이지마다 다른 구성. 말풍선은 칸 안과 칸 사이 여백에도.",
                    "4 вертикальные страницы для телефона с разной раскладкой; баллоны в панелях и в промежутках.",
                    "4页手机竖版，每页布局不同；气泡位于格内和格间空白处。",
                )
            ),
    )
}

/**
 * v48 boş çizgi roman şablonları (yalnız `-PtemplateOut=<klasör>` verilince): arka plan ve karakter yok, yalnız panel
 * düzenleri ile doğru yerleştirilmiş konuşma balonları ve anlatı kutuları. Her şablon 4 sayfa, her sayfa başka düzende.
 *
 * Yerleştirme kuralları (çizgi roman ve manga sayfa düzeni geleneği):
 * - Balonlar okuma sırasındadır: çizgi romanda soldan sağa ve yukarıdan aşağı, mangada sağdan sola; panelde ilk konuşan
 *   okuma yönünün başındaki üst köşeye, cevap çapraz aşağıya. Kuyruklar konuşanın duracağı boş alana (aşağıya) bakar;
 *   balonlar panelin üst bölümünde kalır, çizim alanını kapatmaz.
 * - Anlatı kutusu sayfanın ilk panelinde, okuma yönünün başındaki üst köşede.
 * - Webtoon'da balonların bir kısmı paneller arasındaki boşlukta (dikey kaydırmada okuma ritmi).
 * - Balonların içinde yer tutucu üç nokta ("…") var; dokununca yazılır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "tr-w800dp-h1280dp-xhdpi")
class V48TemplateExport : TemplateExportBase() {

    private fun Rect.at(u: Float, v: Float) = Offset(left + width * u, top + height * v)

    /** Yer tutucu yazılar: kısa, orta, uzun konuşma. */
    private val short = "… …"
    private val medium = "… … …\n… …"
    private val long = "… … … …\n… … …"

    /** Okuma yönüne göre yatay konum (mangada aynalı). */
    private fun u(x: Float, rtl: Boolean) = if (rtl) 1f - x else x

    /** Okuma yönüne göre kuyruk (mangada aynalı). */
    private fun tail(t: TailDirection, rtl: Boolean): TailDirection = if (!rtl) t else when (t) {
        TailDirection.BOTTOM_LEFT -> TailDirection.BOTTOM_RIGHT
        TailDirection.BOTTOM_RIGHT -> TailDirection.BOTTOM_LEFT
        TailDirection.LEFT -> TailDirection.RIGHT
        TailDirection.RIGHT -> TailDirection.LEFT
        else -> t
    }

    /** Panelin genişliğine göre yazı boyu ([max] üst sınır). */
    private fun fontFor(r: Rect, max: Float) = (r.width * 0.075f).coerceIn(18f, max)

    /**
     * Son eklenen balonu panelin içine alır (kenarlardan [pad] payı; altta kuyruğa yer). Eğik panellerde pay büyük.
     * Balonun kutusunu döndürür.
     */
    private fun fit(panel: Rect, pad: Float): Rect {
        var b = strokes().last { it.text?.box?.balloon != null }
        // Balonun genişliği yazının uzunluğuna ve panele göre (öntanımlı kutu geniş paneller için fazla geniş)
        val lines = b.text!!.content.split('\n')
        val longest = lines.maxOf { it.length }
        // Yazılacak konuşmaya yer: kısa söz panelin ~%32'si, orta ~%42'si, uzun ~%52'si (en az yazının kendisi kadar)
        val frac = when { longest <= 4 -> 0.32f; lines.size == 1 || longest <= 6 -> 0.42f; else -> 0.52f }
        val target = maxOf(b.sizePx * (longest * 0.62f + 3.2f), panel.width * frac).coerceAtMost(panel.width * (1f - 2f * pad) * 0.86f)
        val spec = b.text!!.copy(box = b.text!!.box!!.copy(width = target))
        val top = b.points.minOf { it.y }
        val cx = (b.points.minOf { it.x } + b.points.maxOf { it.x }) / 2f
        val quad = com.karapp.drawguidance.ui.drawing.TextRenderer.quadAt(spec, b.sizePx, cx - target / 2f, top)
        val qw = quad.maxOf { it.x } - quad.minOf { it.x }
        val shifted = com.karapp.drawguidance.ui.drawing.TextRenderer.quadAt(spec, b.sizePx, cx - qw / 2f, top)
        b = b.copy(text = spec, points = shifted.map { com.karapp.drawguidance.data.model.StrokePoint(q(it.x), q(it.y)) })
        val resized = b
        compose.runOnIdle { vm().replaceStroke(resized) }
        waitFor { strokes().first { it.id == resized.id }.text?.box?.width == target }
        val box = strokeRect(b.id)
        // Diken ve kalın çizgi için az pay
        val ex = box.width * 0.04f
        val ey = box.height * 0.08f
        val r = Rect(box.left - ex, box.top - ey, box.right + ex, box.bottom + ey)
        val m = minOf(panel.width, panel.height) * pad
        val area = Rect(panel.left + m, panel.top + m, panel.right - m, panel.bottom - m - r.height * 0.45f)
        val x = r.left.coerceIn(area.left, maxOf(area.left, area.right - r.width)) + ex
        val y = r.top.coerceIn(area.top, maxOf(area.top, area.bottom - r.height)) + ey
        if (x != box.left || y != box.top) {
            compose.runOnIdle { vm().clearSelection(); host().select(setOf(b.id)) }
            compose.runOnIdle { vm().moveSelected(x - box.left, y - box.top) }
            compose.runOnIdle { vm().clearSelection() }
            compose.waitForIdle()
        }
        return strokeRect(b.id)
    }

    /** Panelin içinde ([u], [v] oranında) balon; panele sığdırılır. */
    private fun put(r: Rect, x: Float, v: Float, text: String, style: BalloonStyle, t: TailDirection, rtl: Boolean, size: Float, pad: Float): Rect {
        balloon(r.at(u(x, rtl), v), text, style, tail(t, rtl), fontFor(r, size))
        return fit(r, pad)
    }

    /** İki kişilik konuşma: ilk konuşan okuma yönünün başındaki üst köşede, cevap çapraz aşağıda (ilkinin altında). */
    private fun dialogue(r: Rect, rtl: Boolean, size: Float, first: String = medium, reply: String = short, v: Float = 0.16f, pad: Float = 0.05f) {
        val a = put(r, 0.3f, v, first, BalloonStyle.SPEECH, TailDirection.BOTTOM_LEFT, rtl, size, pad)
        val below = ((a.bottom + a.height * 0.5f - r.top) / r.height).coerceAtMost(0.8f)
        balloon(r.at(u(0.7f, rtl), below + 0.08f), reply, BalloonStyle.SPEECH, tail(TailDirection.BOTTOM_RIGHT, rtl), fontFor(r, size))
        fit(r, pad)
    }

    /** Tek balon, panelin üst bölümünde. */
    private fun single(
        r: Rect, rtl: Boolean, size: Float, text: String = medium, style: BalloonStyle = BalloonStyle.SPEECH,
        x: Float = 0.5f, v: Float = 0.2f, t: TailDirection = TailDirection.BOTTOM_LEFT, pad: Float = 0.05f,
    ) = put(r, x, v, text, style, t, rtl, size, pad)

    /** Anlatı kutusu: okuma yönünün başındaki üst köşe. */
    private fun caption(r: Rect, rtl: Boolean, size: Float, pad: Float = 0.04f) {
        narration(r.at(u(0.26f, rtl), 0.1f), medium, fontFor(r, size))
        fit(r, pad)
    }

    // ================================================================ çizgi roman (soldan sağa)

    @Test
    fun comicBlankPages() {
        open("Çizgi roman sayfaları", CanvasKind.COMIC, 1200, 1800, pages = 4, cover = false)
        val s = 32f
        // Sayfa 1: büyük açılış paneli + iki panel
        page(0)
        var r = panels("three_big", 3).map { it.second }
        caption(r[0], false, s)
        single(r[0], false, s, long, x = 0.66f, v = 0.3f, t = TailDirection.BOTTOM_RIGHT)
        dialogue(r[1], false, s)
        single(r[2], false, s, medium, x = 0.42f, v = 0.2f)
        // Sayfa 2: üstte üç küçük panel, ortada geniş panel, altta iki panel
        page(1)
        r = panels("six_mix", 6).map { it.second }
        caption(r[0], false, 26f)
        single(r[1], false, 26f, short, v = 0.24f)
        single(r[2], false, 26f, short, BalloonStyle.THOUGHT, v = 0.24f)
        dialogue(r[3], false, s, long, medium)
        single(r[4], false, s, medium, x = 0.4f)
        single(r[5], false, s, short, BalloonStyle.WHISPER, x = 0.6f, t = TailDirection.BOTTOM_RIGHT)
        // Sayfa 3: aksiyon (eğik paneller): kısa sözler ve bağırma
        page(2)
        r = panels("action", 6).map { it.second }
        single(r[0], false, s, short, BalloonStyle.SHOUT, x = 0.42f, v = 0.26f, pad = 0.12f)
        single(r[1], false, s, short, x = 0.5f, v = 0.26f, t = TailDirection.BOTTOM_RIGHT, pad = 0.12f)
        caption(r[2], false, s, pad = 0.12f)
        single(r[2], false, s, long, BalloonStyle.SHOUT, x = 0.64f, v = 0.32f, t = TailDirection.BOTTOM_RIGHT, pad = 0.12f)
        single(r[3], false, 20f, short, v = 0.28f, pad = 0.14f)
        single(r[4], false, 20f, short, BalloonStyle.THOUGHT, v = 0.28f, pad = 0.14f)
        single(r[5], false, 20f, short, v = 0.28f, t = TailDirection.BOTTOM_RIGHT, pad = 0.14f)
        // Sayfa 4: dört panel (geniş alt), kapanış
        page(3)
        r = panels("four_mix", 4).map { it.second }
        caption(r[0], false, s)
        single(r[0], false, s, medium, x = 0.7f, v = 0.32f, t = TailDirection.BOTTOM_RIGHT)
        single(r[1], false, s, medium, x = 0.45f)
        single(r[2], false, s, short, x = 0.55f, t = TailDirection.BOTTOM_RIGHT)
        dialogue(r[3], false, s, long, medium, v = 0.14f)
        export("comic_blank_pages", CanvasKind.COMIC, landing = 0, coverPage = 0)
    }

    // ================================================================ manga (sağdan sola)

    @Test
    fun comicBlankManga() {
        open("Manga sayfaları", CanvasKind.COMIC, 1200, 1800, pages = 4, cover = false)
        val s = 30f
        // Sayfa 1: manga klasik (geniş üst, sonra ikili sıralar)
        page(0)
        var r = panels("manga", 5, rtl = true).map { it.second }
        caption(r[0], true, s)
        single(r[0], true, s, long, x = 0.66f, v = 0.32f, t = TailDirection.BOTTOM_RIGHT)
        dialogue(r[1], true, s)
        single(r[2], true, s, short, BalloonStyle.THOUGHT)
        single(r[3], true, s, medium, x = 0.45f)
        single(r[4], true, s, short, x = 0.55f, t = TailDirection.BOTTOM_RIGHT)
        // Sayfa 2: manga aksiyon (eğik)
        page(1)
        r = panels("manga_action", 6, rtl = true).map { it.second }
        single(r[0], true, s, short, BalloonStyle.SHOUT, x = 0.45f, v = 0.28f, pad = 0.12f)
        single(r[1], true, s, short, x = 0.5f, v = 0.28f, pad = 0.12f)
        dialogue(r[2], true, s, medium, short, v = 0.14f, pad = 0.12f)
        single(r[3], true, 20f, short, v = 0.3f, pad = 0.14f)
        single(r[4], true, 20f, short, BalloonStyle.SHOUT, v = 0.3f, pad = 0.14f)
        single(r[5], true, 20f, short, BalloonStyle.THOUGHT, v = 0.3f, pad = 0.14f)
        // Sayfa 3: 4 kare (yonkoma): her şeritte bir balon, sağ sütundan başlayarak
        page(2)
        r = panels("yonkoma", 8, rtl = true).map { it.second }
        val strips = r.sortedWith(compareBy({ -it.left }, { it.top }))
        strips.forEachIndexed { i, strip ->
            val style = if (i % 4 == 3) BalloonStyle.SHOUT else BalloonStyle.SPEECH
            single(strip, true, 26f, if (i % 4 == 2) medium else short, style, x = 0.34f, v = 0.26f)
        }
        // Sayfa 4: fantastik (geniş üst sahne + üç panel)
        page(3)
        r = panels("fantasy", 4, rtl = true).map { it.second }
        caption(r[0], true, s)
        single(r[0], true, s, long, x = 0.62f, v = 0.3f, t = TailDirection.BOTTOM_RIGHT)
        single(r[1], true, 22f, short, v = 0.24f)
        single(r[2], true, 22f, short, BalloonStyle.WHISPER, v = 0.24f)
        single(r[3], true, 22f, short, BalloonStyle.THOUGHT, v = 0.24f)
        export("comic_blank_manga", CanvasKind.COMIC, landing = 0, coverPage = 0)
    }

    // ================================================================ webtoon (dikey)

    @Test
    fun comicBlankWebtoon() {
        open("Webtoon bölümü", CanvasKind.COMIC, 800, 1280, pages = 4, cover = false)
        val s = 22f
        /** İki panel arasındaki boşluğun ortasına balon (webtoon'da okuma ritmi). */
        fun gap(a: Rect, b: Rect, x: Float, text: String, style: BalloonStyle = BalloonStyle.SPEECH, t: TailDirection = TailDirection.BOTTOM_LEFT) =
            balloon(Offset(a.left + a.width * x, (a.bottom + b.top) / 2f), text, style, t, s)
        // Sayfa 1: dikey webtoon (dört panel, büyük boşluklar)
        page(0)
        var r = panels("webtoon", 4).map { it.second }
        caption(r[0], false, s)
        gap(r[0], r[1], 0.32f, medium)
        single(r[1], false, s, short, x = 0.68f, v = 0.14f, t = TailDirection.BOTTOM_RIGHT)
        gap(r[1], r[2], 0.62f, short, BalloonStyle.THOUGHT, TailDirection.BOTTOM_RIGHT)
        single(r[3], false, s, medium, x = 0.36f, v = 0.16f)
        // Sayfa 2: geniş sahne
        page(1)
        r = panels("webtoon_scene", 3).map { it.second }
        single(r[0], false, s, short, x = 0.4f, v = 0.3f)
        gap(r[0], r[1], 0.62f, medium, t = TailDirection.BOTTOM_RIGHT)
        dialogue(r[1], false, s, medium, short, v = 0.12f)
        gap(r[1], r[2], 0.3f, short, BalloonStyle.WHISPER)
        single(r[2], false, s, short, x = 0.6f, v = 0.3f, t = TailDirection.BOTTOM_RIGHT)
        // Sayfa 3: akış (ortalı panel, sonra geniş ve ikili)
        page(2)
        r = panels("webtoon_flow", 5).map { it.second }
        single(r[0], false, s, medium, x = 0.34f, v = 0.2f)
        gap(r[0], r[1], 0.7f, short, t = TailDirection.BOTTOM_RIGHT)
        single(r[2], false, s, short, BalloonStyle.SHOUT, x = 0.5f, v = 0.24f)
        single(r[3], false, s, short, x = 0.5f, v = 0.24f)
        single(r[4], false, s, short, BalloonStyle.THOUGHT, x = 0.5f, v = 0.24f)
        // Sayfa 4: iki büyük panel (bölüm sonu)
        page(3)
        r = panels("two_rows", 2).map { it.second }
        dialogue(r[0], false, s, medium, short, v = 0.12f)
        gap(r[0], r[1], 0.5f, short, BalloonStyle.WHISPER)
        single(r[1], false, s, long, x = 0.5f, v = 0.16f)
        narration(r[1].at(0.74f, 0.9f), short, s)
        export("comic_blank_webtoon", CanvasKind.COMIC, landing = 0, coverPage = 0)
    }
}
