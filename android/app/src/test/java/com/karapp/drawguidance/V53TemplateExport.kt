package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BalloonStyle
import com.karapp.drawguidance.content.comic.ComicLinesKind
import com.karapp.drawguidance.ui.drawing.TailDirection
import com.karapp.drawguidance.data.comicassets.ComicAssets
import com.karapp.drawguidance.data.comicassets.VariantGroup
import com.karapp.drawguidance.data.model.CanvasKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** v53 dolu çizgi roman şablonlarının dillere göre adları ve açıklamaları. */
object V53TemplateInfo {
    private fun l(tr: String, en: String, ar: String, ja: String, ko: String, ru: String, zh: String) =
        mapOf("tr" to tr, "en" to en, "ar" to ar, "ja" to ja, "ko" to ko, "ru" to ru, "zh" to zh)

    val INFO: Map<String, Pair<Map<String, String>, Map<String, String>>> = mapOf(
        "comic_story_school" to (
            l("Okul macerası", "School adventure", "مغامرة مدرسية", "学校の冒険", "학교 모험", "Школьное приключение", "校园冒险") to
                l(
                    "Hazır hikâye: okul arka planları, iki öğrenci karakteri, diyaloglar, anlatı kutuları ve ses efektleri.",
                    "Ready story: school backgrounds, two student characters, dialogue, captions and sound effects.",
                    "قصة جاهزة: خلفيات مدرسية وشخصيتا طالبين وحوارات ومربعات سرد ومؤثرات صوتية.",
                    "完成済みのお話：学校の背景、生徒2人、セリフ、ナレーション、効果音。",
                    "완성된 이야기: 학교 배경, 학생 캐릭터 둘, 대사, 내레이션, 효과음.",
                    "Готовая история: школьные фоны, два ученика, диалоги, подписи и звуки.",
                    "现成故事：校园背景、两位学生角色、对白、旁白和音效。",
                )
            ),
        "comic_story_chase" to (
            l("Gece takibi", "Night chase", "مطاردة ليلية", "夜の追跡", "밤의 추격", "Ночная погоня", "夜间追逐") to
                l(
                    "Polisiye hikâye: yağmurlu şehir arka planları, dedektif ve hırsız, hız çizgileri, BAM! ve WHOOSH! efektleri.",
                    "Detective story: rainy city backgrounds, a detective and a thief, speed lines, BAM! and WHOOSH! effects.",
                    "قصة بوليسية: خلفيات مدينة ممطرة ومحققة ولص وخطوط سرعة ومؤثرات.",
                    "探偵もの：雨の街の背景、探偵と怪盗、スピード線、効果音。",
                    "탐정 이야기: 비 오는 도시 배경, 탐정과 도둑, 속도선, 효과음.",
                    "Детектив: дождливый город, сыщица и вор, линии скорости, звуки.",
                    "侦探故事：雨夜城市背景、侦探与怪盗、速度线和音效。",
                )
            ),
        "comic_story_strip" to (
            l("Şaka şeridi", "Gag strip", "شريط فكاهي", "4コマ漫画", "4컷 개그 만화", "Комикс-шутка", "四格笑话") to l(
                "Dört karelik hazır şaka: karakterler, ev ve park arka planları, diyalog ve HAHAHA efekti.",
                "A ready four-panel gag: characters, home and park backgrounds, dialogue and a HAHAHA effect.",
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
 * v53 dolu çizgi roman şablonları (yalnız `-PtemplateOut=<klasör>` verilince): boş şablonların aksine mekân arka
 * planları, karakterler, gerçek diyaloglar, anlatı kutuları, ses efektleri ve hız çizgileri içerir (hepsi Free).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "tr-w800dp-h1280dp-xhdpi")
class V53TemplateExport : TemplateExportBase() {

    private fun Rect.at(u: Float, v: Float) = Offset(left + width * u, top + height * v)

    private fun panelsAny(template: String): List<Rect> {
        val before = vm().comicPanelRects().size
        compose.runOnIdle { vm().insertTemplate(template) }
        waitFor { vm().comicPanelRects().size > before }
        settleFor(300)
        compose.runOnIdle { vm().clearSelection() }
        return readingOrder(false).map { it.second }
    }

    private fun who(id: String, group: VariantGroup, panel: Rect, u: Float, height: Float) {
        val c = ComicAssets.catalog.characters.first { it.id == id }
        val v = c.variants(group).firstOrNull() ?: c.variants.first()
        character(id, v.id, panel, panel.left + panel.width * u, height)
    }

    private fun size(r: Rect, base: Float) = (base * (r.width / 1100f).coerceIn(0.7f, 1.1f)).coerceAtLeast(20f)

    @Test
    fun comicStorySchool() {
        open("Okul macerası", CanvasKind.COMIC, 1200, 1800, pages = 2, cover = false)
        page(0)
        panelsAny("three_big_bottom")
        val leaves = readingOrder(false)
        backdrop("okul_koridor", leaves[0].first)
        backdrop("sinif", leaves[1].first)
        backdrop("okul_cati", leaves[2].first)
        val r = leaves.map { it.second }
        who("ogrenci_arda", VariantGroup.POSE, r[0], 0.3f, 0.8f)
        who("ogrenci_ece", VariantGroup.POSE, r[0], 0.72f, 0.78f)
        narration(r[0].at(0.24f, 0.08f), "Pazartesi sabahı, okulun ilk günü…", size(r[0], 30f))
        balloon(r[0].at(0.62f, 0.2f), "Arda! Ödevini getirdin mi?", BalloonStyle.SPEECH, TailDirection.BOTTOM_RIGHT, size(r[0], 30f))
        who("ogrenci_arda", VariantGroup.EXPRESSION, r[1], 0.5f, 0.82f)
        balloon(r[1].at(0.5f, 0.16f), "Hangi ödev?!", BalloonStyle.SHOUT, TailDirection.BOTTOM_LEFT, size(r[1], 30f))
        who("ogrenci_ece", VariantGroup.EXPRESSION, r[2], 0.5f, 0.82f)
        balloon(r[2].at(0.5f, 0.16f), "Yine mi unuttun…", BalloonStyle.THOUGHT, TailDirection.BOTTOM_RIGHT, size(r[2], 28f))
        page(1)
        val p = panelsAny("four_grid")
        val l2 = readingOrder(false)
        backdrop("okul_koridor2", l2[0].first)
        backdrop("sinif", l2[1].first)
        backdrop("avlu", l2[2].first)
        backdrop("okul_cati", l2[3].first)
        val q = l2.map { it.second }
        who("ogrenci_arda", VariantGroup.POSE, q[0], 0.5f, 0.85f)
        balloon(q[0].at(0.5f, 0.15f), "Bir planım var!", BalloonStyle.SPEECH, TailDirection.BOTTOM_LEFT, size(q[0], 26f))
        who("ogrenci_ece", VariantGroup.EXPRESSION, q[1], 0.5f, 0.85f)
        balloon(q[1].at(0.5f, 0.15f), "Bu iyi bitmeyecek.", BalloonStyle.SPEECH, TailDirection.BOTTOM_RIGHT, size(q[1], 26f))
        lines(ComicLinesKind.SPEED_H, q[2])
        who("ogrenci_arda", VariantGroup.POSE, q[2], 0.5f, 0.8f)
        sfx("VUUUŞ", q[2].at(0.5f, 0.2f), size(q[2], 70f))
        who("ogrenci_ece", VariantGroup.POSE, q[3], 0.35f, 0.82f)
        who("ogrenci_arda", VariantGroup.EXPRESSION, q[3], 0.72f, 0.8f)
        balloon(q[3].at(0.4f, 0.14f), "Ödev çatıda mıydı?!", BalloonStyle.SPEECH, TailDirection.BOTTOM_LEFT, size(q[3], 24f))
        sfx("HAHAHA", q[3].at(0.72f, 0.3f), size(q[3], 54f))
        check(p.size == 4)
        export("comic_story_school", CanvasKind.COMIC, landing = 0, coverPage = 0)
    }

    @Test
    fun comicStoryChase() {
        open("Gece takibi", CanvasKind.COMIC, 1200, 1800, pages = 2, cover = false)
        page(0)
        panelsAny("three_big_bottom")
        val l = readingOrder(false)
        backdrop("neon_cadde", l[0].first)
        backdrop("yagmur_cadde", l[1].first)
        backdrop("kopru_alti", l[2].first)
        val r = l.map { it.second }
        who("maskeli_hirsiz", VariantGroup.POSE, r[0], 0.66f, 0.82f)
        narration(r[0].at(0.25f, 0.08f), "Gece yarısı. Müzeden bir elmas kayboldu.", size(r[0], 28f))
        balloon(r[0].at(0.62f, 0.18f), "Kimse beni yakalayamaz!", BalloonStyle.SPEECH, TailDirection.BOTTOM_RIGHT, size(r[0], 28f))
        who("dedektif_selin", VariantGroup.EXPRESSION, r[1], 0.5f, 0.82f)
        balloon(r[1].at(0.5f, 0.15f), "Dur! Polis!", BalloonStyle.SHOUT, TailDirection.BOTTOM_LEFT, size(r[1], 30f))
        lines(ComicLinesKind.FOCUS, r[2])
        who("maskeli_hirsiz", VariantGroup.ACTION, r[2], 0.5f, 0.8f)
        sfx("WHOOSH!", r[2].at(0.5f, 0.18f), size(r[2], 64f))
        page(1)
        panelsAny("four_grid")
        val l2 = readingOrder(false)
        backdrop("cati_teras", l2[0].first)
        backdrop("neon_ara_sokak", l2[1].first)
        backdrop("cati_teras2", l2[2].first)
        backdrop("liman", l2[3].first)
        val q = l2.map { it.second }
        lines(ComicLinesKind.SPEED_H, q[0])
        who("dedektif_selin", VariantGroup.POSE, q[0], 0.5f, 0.85f)
        balloon(q[0].at(0.5f, 0.14f), "Çatıdan kaçıyor!", BalloonStyle.SPEECH, TailDirection.BOTTOM_LEFT, size(q[0], 26f))
        who("maskeli_hirsiz", VariantGroup.EXPRESSION, q[1], 0.5f, 0.85f)
        balloon(q[1].at(0.5f, 0.14f), "Bu kadar hızlı mı?", BalloonStyle.THOUGHT, TailDirection.BOTTOM_RIGHT, size(q[1], 24f))
        lines(ComicLinesKind.BURST, q[2])
        sfx("BAM!", q[2].at(0.5f, 0.45f), size(q[2], 90f))
        who("dedektif_selin", VariantGroup.EXPRESSION, q[3], 0.32f, 0.8f)
        who("maskeli_hirsiz", VariantGroup.EXPRESSION, q[3], 0.72f, 0.78f)
        balloon(q[3].at(0.35f, 0.13f), "Elmas nerede?", BalloonStyle.SPEECH, TailDirection.BOTTOM_LEFT, size(q[3], 24f))
        balloon(q[3].at(0.72f, 0.3f), "Hangi elmas?", BalloonStyle.WHISPER, TailDirection.BOTTOM_RIGHT, size(q[3], 22f))
        export("comic_story_chase", CanvasKind.COMIC, landing = 0, coverPage = 0)
    }

    @Test
    fun comicStoryStrip() {
        open("Şaka şeridi", CanvasKind.COMIC, 1200, 1800, pages = 1, cover = false)
        page(0)
        panelsAny("four_grid")
        val l = readingOrder(false)
        backdrop("nehir_park", l[0].first)
        backdrop("sonbahar_park", l[1].first)
        backdrop("carsi", l[2].first)
        backdrop("avlu2", l[3].first)
        val q = l.map { it.second }
        who("hasan_dede", VariantGroup.POSE, q[0], 0.32f, 0.8f)
        who("can", VariantGroup.EXPRESSION, q[0], 0.74f, 0.7f)
        balloon(q[0].at(0.32f, 0.13f), "Dede, eskiden okula nasıl giderdin?", BalloonStyle.SPEECH, TailDirection.BOTTOM_RIGHT, size(q[0], 22f))
        who("hasan_dede", VariantGroup.EXPRESSION, q[1], 0.5f, 0.82f)
        balloon(q[1].at(0.5f, 0.13f), "Karda, iki saat yürüyerek!", BalloonStyle.SPEECH, TailDirection.BOTTOM_LEFT, size(q[1], 24f))
        who("can", VariantGroup.EXPRESSION, q[2], 0.5f, 0.8f)
        balloon(q[2].at(0.5f, 0.13f), "Yazın da mı kar vardı?", BalloonStyle.THOUGHT, TailDirection.BOTTOM_LEFT, size(q[2], 24f))
        who("hasan_dede", VariantGroup.EXPRESSION, q[3], 0.32f, 0.78f)
        who("can", VariantGroup.EXPRESSION, q[3], 0.74f, 0.7f)
        balloon(q[3].at(0.34f, 0.13f), "Hikâye bu, evlat!", BalloonStyle.SPEECH, TailDirection.BOTTOM_LEFT, size(q[3], 24f))
        sfx("HAHAHA", q[3].at(0.72f, 0.24f), size(q[3], 50f))
        export("comic_story_strip", CanvasKind.COMIC, landing = 0, coverPage = 0)
    }
}
