package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BalloonStyle
import com.karapp.drawguidance.content.comic.ComicLinesKind
import com.karapp.drawguidance.ui.drawing.TailDirection
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.ToneFill
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v53 dolu çizgi roman şablonlarının dillere göre adları ve açıklamaları. */
object V53TemplateInfo {
    private fun l(tr: String, en: String, ar: String, ja: String, ko: String, ru: String, zh: String) =
        mapOf("tr" to tr, "en" to en, "ar" to ar, "ja" to ja, "ko" to ko, "ru" to ru, "zh" to zh)

    val INFO: Map<String, Pair<Map<String, String>, Map<String, String>>> = mapOf(
        // v55 "Okul macerası" yerine örnek manga (sağdan sola okunur)
        "comic_story_manga" to (
            l("Manga: Avlu düellosu", "Manga: Courtyard duel", "مانغا: مبارزة الفناء", "マンガ：中庭の決闘", "만화: 안뜰의 결투", "Манга: дуэль во дворе", "漫画：庭院决斗") to
                l(
                    "Örnek manga: sağdan sola okunur; iki kılıç ustası, eski avlu, ekran tonları, hız ve darbe çizgileri, ses efektleri.",
                    "Sample manga: read right to left; two swordsmen, an old courtyard, screentones, speed and impact lines, sound effects.",
                    "مانغا نموذجية تُقرأ من اليمين إلى اليسار: مبارزان بالسيف وفناء قديم ودرجات شاشة وخطوط سرعة ومؤثرات صوتية.",
                    "マンガの見本（右から左へ読む）：剣士2人、古い中庭、スクリーントーン、スピード線、効果音。",
                    "만화 견본(오른쪽에서 왼쪽으로 읽기): 검객 둘, 오래된 안뜰, 스크린톤, 속도선, 효과음.",
                    "Образец манги (читается справа налево): два мечника, старый двор, скринтоны, линии скорости и звуки.",
                    "漫画示例（从右往左读）：两位剑客、古老庭院、网点、速度线和音效。",
                )
            ),
        "comic_story_chase" to (
            l("Gece takibi", "Night chase", "مطاردة ليلية", "夜の追跡", "밤의 추격", "Ночная погоня", "夜间追逐") to
                l(
                    "Polisiye hikâye: liman ve şehir arka planları, dedektif ve polis, hız ve odak çizgileri, BAM! ve WHOOSH! efektleri.",
                    "Detective story: harbor and city backgrounds, a detective and a police officer, speed and focus lines, BAM! and WHOOSH! effects.",
                    "قصة بوليسية: خلفيات ميناء ومدينة ومحقق وشرطي وخطوط سرعة وتركيز ومؤثرات.",
                    "探偵もの：港と街の背景、探偵と警官、スピード線と集中線、効果音。",
                    "탐정 이야기: 항구와 도시 배경, 탐정과 경찰, 속도선과 집중선, 효과음.",
                    "Детектив: порт и город, сыщик и полицейский, линии скорости и фокуса, звуки.",
                    "侦探故事：港口与城市背景、侦探与警察、速度线、集中线和音效。",
                )
            ),
        "comic_story_strip" to (
            l("Şaka şeridi", "Gag strip", "شريط فكاهي", "4コマ漫画", "4컷 개그 만화", "Комикс-шутка", "四格笑话") to l(
                "Dört karelik hazır şaka: dede ve torun, park arka planları, diyaloglar ve HAHAHA efekti.",
                "A ready four-panel gag: grandpa and grandson, park backgrounds, dialogue and a HAHAHA effect.",
                "نكتة جاهزة من أربع لوحات: شخصيات وخلفيات وحوار ومؤثر ضحك.",
                "完成済みの4コマギャグ：キャラクター、背景、セリフ、笑いの効果音。",
                "완성된 4컷 개그: 캐릭터, 배경, 대사, 웃음 효과음.",
                "Готовая шутка в четырёх кадрах: персонажи, фоны, диалог и смех.",
                "现成四格笑话：角色、背景、对白和笑声音效。",
            )
        ),
    )
}

/**
 * v53 dolu çizgi roman şablonları (yalnız `-PtemplateOut=<klasör>` verilince): mekân arka planları, karakterler,
 * gerçek diyaloglar, anlatı kutuları, ses efektleri ve hız/odak çizgileri (hepsi Free).
 *
 * Kurallar (çizgi roman sayfa düzeni): bir karede aynı çizim tarzındaki karakterler aynı çekim ölçeğinde yan yana
 * durur, üst üste binmez ve karenin içinde kalır; yüz ifadesi diyaloğa uyar; balonlar karenin üstünde, kuyrukları
 * konuşana döner ve yüzü örtmez. Hız/odak çizgili aksiyon karelerinde ayrıntılı mekân yerine sade (beyaz ya da
 * ekran tonlu) zemin kullanılır; çizgiler karakterin arkasında kalır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "tr-w800dp-h1280dp-xhdpi")
class V53TemplateExport : TemplateExportBase() {

    private fun Rect.at(u: Float, v: Float) = Offset(left + width * u, top + height * v)

    private fun panelsAny(template: String) {
        val before = vm().comicPanelRects().size
        compose.runOnIdle { vm().insertTemplate(template) }
        waitFor { vm().comicPanelRects().size > before }
        settleFor(300)
        compose.runOnIdle { vm().clearSelection() }
    }

    /**
     * Karakter [variant] karenin içinde: boyu karenin [height] payı, eni en çok [maxW] payı (aşarsa küçültülür),
     * yatayda [u] noktasında ortalı ama karenin içinde; alt kenarı karenin altına basar.
     */
    private fun who(id: String, variant: String, panel: Rect, u: Float, height: Float, maxW: Float = 0.9f) {
        character(id, variant, panel, panel.left + panel.width * u, height)
        val img = images().last()
        compose.runOnIdle { vm().clearSelection(); host().select(setOf(img.id)) }
        compose.runOnIdle {
            val b = strokeRect(img.id)
            val limit = panel.width * maxW
            if (b.width > limit) vm().scaleSelected(limit / b.width, limit / b.width, Offset(b.center.x, b.bottom))
        }
        compose.runOnIdle {
            val b = strokeRect(img.id)
            val inset = panel.width * 0.015f
            val x = (panel.left + panel.width * u - b.width / 2f).coerceIn(panel.left + inset, panel.right - inset - b.width)
            vm().moveSelectionTo(x, panel.bottom - b.height - panel.height * 0.004f)
        }
        compose.runOnIdle { vm().clearSelection() }
        compose.waitForIdle()
    }

    /** Sade zemin: karenin tamamını kaplayan dolgu ve isteğe bağlı ekran tonu / desen (kenarsız). */
    private fun ground(panel: Rect, fill: String, tone: ToneFill? = null) =
        add(shape("RECTANGLE", panel, ShapeStyle(strokeColorHex = fill, strokeWidth = 1f, fillColorHex = fill, tone = tone)))

    /**
     * Balon karenin içinde: eni karenin [w] payı; merkezi kenarlara taşmayacak biçimde kaydırılır. Bağırma büyük,
     * fısıltı küçük yazılır.
     */
    private fun talk(panel: Rect, u: Float, v: Float, text: String, tail: TailDirection, style: BalloonStyle = BalloonStyle.SPEECH, w: Float = 0.8f) {
        val size = when (style) {
            BalloonStyle.SHOUT -> 52f
            BalloonStyle.WHISPER -> 28f
            else -> 32f
        }
        // Düşünce balonunun bulut kenarı yazıya yer bırakır: daha dar ve yüksek
        val width = panel.width * (if (style == BalloonStyle.THOUGHT && w == 0.8f) 0.64f else w)
        val half = width / 2f + panel.width * 0.03f
        val x = (panel.left + panel.width * u).coerceIn(panel.left + half, panel.right - half)
        balloon(Offset(x, panel.top + panel.height * v), text, style, tail, size, width = width)
    }

    private fun caption(panel: Rect, text: String, w: Float = 0.56f) {
        val width = panel.width * w
        narration(Offset(panel.left + width / 2f + panel.width * 0.025f, panel.top + panel.height * 0.065f), text, 28f, width = width)
    }

    /**
     * v55 örnek manga (Okul macerası yerine; şövalye Aras ve beyaz saçlı kılıççı Doruk, ikisi de temel karakter): sağdan sola okuma sırası, manga kare düzeni, ekran tonlu sade zeminler,
     * hız/odak/darbe çizgileri. İlk karede avlu, sonra yüz yakın çekimleri ve düello.
     */
    @Test
    fun comicStoryManga() {
        open("Manga: Avlu düellosu", CanvasKind.COMIC, 1200, 1800, pages = 2, cover = false)
        page(0)
        panelsAny("manga")
        val l = readingOrder(true)
        val r = l.map { it.second }
        // 1 (geniş üst kare): eski avlu, iki samuray karşı karşıya; sağdaki önce konuşur
        backdrop("avlu2", l[0].first)
        who("kilicci_doruk", "poz_01", r[0], 0.74f, 0.7f, maxW = 0.36f)
        who("sovalye_aras", "poz_01", r[0], 0.26f, 0.72f, maxW = 0.36f)
        caption(r[0], "Eski konak avlusu, şafak vakti…", w = 0.38f)
        talk(r[0], 0.72f, 0.2f, "Sonunda geldin, Aras.", TailDirection.BOTTOM_RIGHT, w = 0.42f)
        // 2: Aras kararlı (dikey kasvet çizgileri)
        ground(r[1], "#F5F5F5", ToneFill("pat_vlines", 0.45f, colorHex = "#424242"))
        who("sovalye_aras", "ifade_01", r[1], 0.5f, 0.6f, maxW = 0.82f)
        talk(r[1], 0.5f, 0.15f, "Sözümü tutmaya geldim.", TailDirection.BOTTOM_LEFT)
        // 3: Doruk'un iç sesi (yumuşak nokta tonu)
        ground(r[2], "#FFFFFF", ToneFill("tone_dot_radial", 0.4f, colorHex = "#616161"))
        who("kilicci_doruk", "ifade_05", r[2], 0.5f, 0.6f, maxW = 0.82f)
        talk(r[2], 0.5f, 0.15f, "Eski dostum… rakibim.", TailDirection.BOTTOM_RIGHT, BalloonStyle.THOUGHT)
        // 4: Aras bağırır (odak çizgileri yüzüne toplanır)
        ground(r[3], "#FFFFFF")
        lines(ComicLinesKind.FOCUS, r[3], density = 1.8f)
        who("sovalye_aras", "ifade_03", r[3], 0.5f, 0.58f, maxW = 0.82f)
        talk(r[3], 0.5f, 0.14f, "Kılıcını çek!", TailDirection.BOTTOM_LEFT, BalloonStyle.SHOUT)
        // 5: Aras atılır (hız çizgileri arkasında)
        ground(r[4], "#FFFFFF", ToneFill("tone_dot_grad", 0.35f, angle = 90f, colorHex = "#424242"))
        lines(ComicLinesKind.SPEED_H, r[4], density = 1.8f)
        who("sovalye_aras", "aksiyon_01", r[4], 0.5f, 0.8f, maxW = 0.78f)
        sfx("VUUUŞ", r[4].at(0.7f, 0.16f), 88f)
        page(1)
        panelsAny("four_grid")
        val l2 = readingOrder(true)
        val q = l2.map { it.second }
        // 1: kılıçlar çarpışır (darbe çizgileri, büyük efekt)
        ground(q[0], "#FFFFFF", ToneFill("tone_dot_radial", 0.45f, colorHex = "#212121"))
        lines(ComicLinesKind.BURST, q[0], density = 1.6f)
        who("sovalye_aras", "aksiyon_02", q[0], 0.5f, 0.74f, maxW = 0.8f)
        sfx("ŞAK!", q[0].at(0.5f, 0.15f), 110f)
        // 2: Aras şaşkın
        ground(q[1], "#FFFFFF")
        lines(ComicLinesKind.FOCUS, q[1], density = 1.4f)
        who("sovalye_aras", "ifade_04", q[1], 0.5f, 0.56f, maxW = 0.8f)
        talk(q[1], 0.5f, 0.14f, "Bu hız… nasıl?!", TailDirection.BOTTOM_LEFT)
        // 3: Doruk gülümser (parıltı deseni)
        ground(q[2], "#FAFAFA", ToneFill("pat_sparkle", 0.45f, colorHex = "#757575"))
        who("kilicci_doruk", "ifade_02", q[2], 0.5f, 0.56f, maxW = 0.8f)
        talk(q[2], 0.5f, 0.14f, "Yıllarca bu anı bekledim.", TailDirection.BOTTOM_RIGHT)
        // 4: avluda ikisi de gülümser; "devam edecek"
        backdrop("avlu2", l2[3].first)
        who("kilicci_doruk", "ifade_02", q[3], 0.74f, 0.48f, maxW = 0.48f)
        who("sovalye_aras", "ifade_02", q[3], 0.26f, 0.48f, maxW = 0.48f)
        talk(q[3], 0.7f, 0.13f, "Berabere… bu seferlik.", TailDirection.BOTTOM_LEFT, w = 0.6f)
        caption(q[3], "Devam edecek…", w = 0.4f)
        export("comic_story_manga", CanvasKind.COMIC, landing = 0, coverPage = 0)
    }

    @Test
    fun comicStoryChase() {
        open("Gece takibi", CanvasKind.COMIC, 1200, 1800, pages = 2, cover = false)
        page(0)
        panelsAny("three_big_bottom")
        val l = readingOrder(false)
        val r = l.map { it.second }
        // 1: dedektif limanda iz sürüyor
        backdrop("liman", l[0].first)
        who("dedektif_cemal", "gunluk_02", r[0], 0.62f, 0.62f, maxW = 0.62f)
        caption(r[0], "Akşam, liman. Çalınan veriler burada bir yerde…")
        talk(r[0], 0.36f, 0.34f, "Taze ayak izleri!", TailDirection.BOTTOM_RIGHT, w = 0.6f)
        // 2: polis şüpheliyi görüyor
        backdrop("gunbatimi_cadde", l[1].first)
        who("polis_murat", "aksiyon_06", r[1], 0.6f, 0.74f, maxW = 0.6f)
        talk(r[1], 0.42f, 0.15f, "Dur! Polis!", TailDirection.BOTTOM_RIGHT, BalloonStyle.SHOUT)
        // 3: takip (sade zemin, hız çizgileri karakterlerin arkasında)
        ground(r[2], "#FFFFFF", ToneFill("tone_dot_grad", 0.4f, angle = 90f, colorHex = "#455A64"))
        lines(ComicLinesKind.SPEED_H, r[2], density = 1.8f)
        who("polis_murat", "aksiyon_01", r[2], 0.3f, 0.7f, maxW = 0.42f)
        who("dedektif_cemal", "aksiyon_01", r[2], 0.72f, 0.74f, maxW = 0.42f)
        sfx("WHOOSH!", r[2].at(0.5f, 0.14f), 110f)
        page(1)
        panelsAny("four_grid")
        val l2 = readingOrder(false)
        val q = l2.map { it.second }
        // 1: çatıya kaçıyor
        backdrop("cati_teras", l2[0].first)
        who("dedektif_cemal", "aksiyon_02", q[0], 0.5f, 0.72f, maxW = 0.7f)
        talk(q[0], 0.5f, 0.13f, "Çatıya çıktı!", TailDirection.BOTTOM_LEFT)
        // 2: polisin kararlı yüzü (odak çizgileri)
        ground(q[1], "#FFFFFF")
        lines(ComicLinesKind.FOCUS, q[1], density = 2f)
        who("polis_murat", "ifade_05", q[1], 0.5f, 0.54f, maxW = 0.78f)
        talk(q[1], 0.5f, 0.13f, "Bu kez kaçamaz.", TailDirection.BOTTOM_RIGHT, BalloonStyle.THOUGHT)
        // 3: kapı kırılıyor (darbe çizgileri, büyük efekt yazısı)
        ground(q[2], "#FFF3E0", ToneFill("tone_dot_radial", 0.5f, colorHex = "#FF7043"))
        lines(ComicLinesKind.BURST, q[2], density = 1.6f)
        sfx("BAM!", q[2].at(0.5f, 0.5f), 140f)
        // 4: veriler bulundu (iki yüz yan yana)
        backdrop("fabrika", l2[3].first)
        who("dedektif_cemal", "ifade_02", q[3], 0.26f, 0.5f, maxW = 0.48f)
        who("polis_murat", "ifade_02", q[3], 0.75f, 0.5f, maxW = 0.48f)
        talk(q[3], 0.4f, 0.11f, "Veriler bu çantada!", TailDirection.BOTTOM_LEFT, w = 0.74f)
        talk(q[3], 0.62f, 0.33f, "Dava kapandı.", TailDirection.BOTTOM_RIGHT, w = 0.64f)
        export("comic_story_chase", CanvasKind.COMIC, landing = 0, coverPage = 0)
    }

    @Test
    fun comicStoryStrip() {
        open("Şaka şeridi", CanvasKind.COMIC, 1200, 1800, pages = 1, cover = false)
        page(0)
        panelsAny("four_grid")
        val l = readingOrder(false)
        val q = l.map { it.second }
        // 1: torun soruyor (ikisi tam boy, yan yana)
        backdrop("nehir_park", l[0].first)
        who("ege", "poz_01", q[0], 0.28f, 0.68f, maxW = 0.42f)
        who("hasan_dede", "poz_01", q[0], 0.74f, 0.72f, maxW = 0.42f)
        talk(q[0], 0.36f, 0.14f, "Dede, eskiden okula nasıl giderdin?", TailDirection.BOTTOM_LEFT)
        // 2: dede övünüyor
        backdrop("sonbahar_park", l[1].first)
        who("hasan_dede", "ifade_09", q[1], 0.5f, 0.56f, maxW = 0.8f)
        talk(q[1], 0.5f, 0.14f, "Karda, iki saat yürüyerek!", TailDirection.BOTTOM_RIGHT)
        // 3: torun düşünüyor
        ground(q[2], "#E3F2FD", ToneFill("pat_bubbles", 0.4f, colorHex = "#64B5F6"))
        who("ege", "ifade_07", q[2], 0.5f, 0.56f, maxW = 0.8f)
        talk(q[2], 0.5f, 0.14f, "Yazın da mı kar vardı?", TailDirection.BOTTOM_LEFT, BalloonStyle.THOUGHT)
        // 4: ikisi de gülüyor (yüzler yan yana)
        backdrop("avlu", l[3].first)
        who("hasan_dede", "ifade_05", q[3], 0.26f, 0.48f, maxW = 0.48f)
        who("ege", "ifade_02", q[3], 0.75f, 0.48f, maxW = 0.48f)
        talk(q[3], 0.3f, 0.13f, "Hikâye bu, evlat!", TailDirection.BOTTOM_LEFT)
        sfx("HAHAHA", q[3].at(0.64f, 0.37f), 72f)
        export("comic_story_strip", CanvasKind.COMIC, landing = 0, coverPage = 0)
    }
}
