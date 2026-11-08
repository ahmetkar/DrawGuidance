package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.comic.ComicLinesKind
import com.karapp.drawguidance.content.image.ImageAdjust
import com.karapp.drawguidance.content.image.ImageLook
import com.karapp.drawguidance.data.comicassets.ComicAssetKind
import com.karapp.drawguidance.data.comicassets.ComicAssets
import com.karapp.drawguidance.data.model.BackgroundSpec
import com.karapp.drawguidance.data.model.BalloonStyle
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.GradientSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.model.TextParagraph
import com.karapp.drawguidance.ui.drawing.TailDirection
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialArt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v47 yeni şablonların dillere göre adları ve açıklamaları. */
object V47TemplateInfo {
    private fun l(tr: String, en: String, ar: String, ja: String, ko: String, ru: String, zh: String) =
        mapOf("tr" to tr, "en" to en, "ar" to ar, "ja" to ja, "ko" to ko, "ru" to ru, "zh" to zh)

    val INFO: Map<String, Pair<Map<String, String>, Map<String, String>>> = mapOf(
        "simple_lesson" to (
            l("Ders notu", "Lesson notes", "ملاحظات الدرس", "授業ノート", "수업 노트", "Конспект урока", "课堂笔记") to
                l(
                    "Başlık, anahtar kavramlar, döngü diyagramı ve özet; su döngüsü örneği.",
                    "Title, key terms, a cycle diagram and summary; water cycle example.",
                    "عنوان ومفاهيم رئيسية ومخطط دورة وملخص؛ مثال دورة الماء.",
                    "タイトル、キーワード、サイクル図、まとめ（水の循環の例）。",
                    "제목, 핵심 개념, 순환 도표, 요약(물의 순환 예시).",
                    "Заголовок, ключевые понятия, схема цикла и итог; пример — круговорот воды.",
                    "标题、关键概念、循环图和总结（水循环示例）。",
                )
            ),
        "simple_todo" to (
            l("Yapılacaklar listesi", "To-do list", "قائمة المهام", "やることリスト", "할 일 목록", "Список дел", "待办清单") to
                l(
                    "Okul, ev ve kişisel işler için işaretlenebilir listeler.",
                    "Checkable lists for school, home and personal tasks.",
                    "قوائم قابلة للتأشير للمدرسة والمنزل والمهام الشخصية.",
                    "学校・家・自分のことのチェックリスト。",
                    "학교, 집, 개인 일을 위한 체크 목록.",
                    "Списки с отметками: учёба, дом и личные дела.",
                    "学校、家庭和个人事务的勾选清单。",
                )
            ),
        "simple_week" to (
            l("Haftalık plan", "Weekly plan", "خطة أسبوعية", "週間プラン", "주간 계획", "План на неделю", "周计划") to
                l(
                    "Yedi gün için kutular ve notlar alanı.",
                    "Boxes for the seven days and a notes area.",
                    "مربعات لأيام الأسبوع السبعة ومساحة للملاحظات.",
                    "7日分の枠とメモ欄。",
                    "7일 칸과 메모 칸.",
                    "Блоки на семь дней и место для заметок.",
                    "七天的方框和备注区。",
                )
            ),
    )
}

/**
 * v47 basit not alma şablonları (yalnız `-PtemplateOut=<klasör>` verilince): ders notu, yapılacaklar listesi, haftalık
 * plan — yalnız basit modda olan araçlarla. (v47'nin çizgi roman şablonları v48'de kaldırıldı; yerine V48TemplateExport.)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "tr-w800dp-h1280dp-xhdpi")
class V47TemplateExport : TemplateExportBase() {

    // ================================================================ basit not alma

    /** Ders notu: su döngüsü (başlık, kavramlar, döngü diyagramı, özet). */
    @Test
    fun simpleLesson() {
        open("Su Döngüsü", CanvasKind.SIMPLE_NOTES, 1240, 1754, pages = 1, cover = false, background = BackgroundSpec(patternId = "note.ruled_college"))
        page(0)
        val m = 150f  // kenar çizgisinin sağında
        val w = 1240f - m - 80f
        val title = text("Su Döngüsü", m, 70f, 54f, INK, TextFont.POPPINS, bold = true, width = w)
        val sub = text("Fen bilimleri · Ders notu · Tarih: ________", m, title.bottom() + 8f, 26f, MUTED, width = w)
        val h1 = heading("Anahtar kavramlar", m, sub.bottom() + 40f)
        val keys = text(
            "Buharlaşma: Güneş'in ısıttığı su buhar olup havaya yükselir.\nYoğuşma: Yükselen buhar soğuyup küçük damlacıklara dönüşür, bulutlar oluşur.\nYağış: Damlacıklar büyüyüp ağırlaşınca yağmur, kar ya da dolu olarak düşer.\nToplanma: Su derelerde, göllerde, denizlerde ve yer altında birikir.",
            m, h1.bottom() + 14f, 28f, INK, width = w, paragraph = TextParagraph.BULLET,
        )
        val h2 = heading("Döngü", m, keys.bottom() + 40f)
        val cycle = TutorialArt.diagram(
            DiagramKind.CYCLE, Rect(m + w * 0.12f, h2.bottom() + 16f, m + w * 0.88f, h2.bottom() + 16f + 560f), ShapeStyle(INK, 2.5f), "ocean",
            listOf("Buharlaşma", "Yoğuşma", "Yağış", "Toplanma"),
        )
        val cb = cycle.points.maxOf { it.y }
        val h3 = heading("Özet", m, cb + 40f)
        val sum = text("Su, Güneş'ten aldığı enerjiyle sürekli yer değiştirir; miktarı değişmez, yalnız hâli (sıvı, gaz, katı) ve yeri değişir.", m, h3.bottom() + 12f, 28f, INK, width = w)
        val q = text("Soru: Bulutlar neden yüksekte oluşur?", m, sum.bottom() + 24f, 30f, RED, TextFont.CAVEAT, width = w)
        add(title, sub, h1, keys, h2, cycle, h3, sum, q)
        export("simple_lesson", CanvasKind.SIMPLE_NOTES, landing = 0, coverPage = 0)
    }

    /** Yapılacaklar listesi: okul, ev, kişisel (işaretlenebilir). */
    @Test
    fun simpleTodo() {
        open("Yapılacaklar", CanvasKind.SIMPLE_NOTES, 1240, 1754, pages = 1, cover = false, background = BackgroundSpec(patternId = "note.dots"))
        page(0)
        val m = 90f
        val w = 1240f - 2 * m
        val title = text("Bu haftanın yapılacakları", m, 80f, 54f, INK, TextFont.POPPINS, bold = true, width = w)
        val sub = text("Tarih: __.__.____ – __.__.____", m, title.bottom() + 8f, 26f, MUTED, width = w)
        val h1 = heading("Okul", m, sub.bottom() + 46f, color = BLUE)
        val l1 = text("Matematik ödevi\nFen deney raporu\nKitap okuma (her gün 30 dakika)\nYabancı dil kelime tekrarı", m, h1.bottom() + 14f, 30f, INK, width = w, paragraph = TextParagraph.CHECKLIST)
        val h2 = heading("Ev", m, l1.bottom() + 40f, color = GREEN)
        val l2 = text("Odayı toplamak\nBitkileri sulamak\nÇantayı hazırlamak", m, h2.bottom() + 14f, 30f, INK, width = w, paragraph = TextParagraph.CHECKLIST)
        val h3 = heading("Kendim için", m, l2.bottom() + 40f, color = RED)
        val l3 = text("Spor ya da yürüyüş\nResim çizmek\nErken uyumak", m, h3.bottom() + 14f, 30f, INK, width = w, paragraph = TextParagraph.CHECKLIST)
        val note = text("En önemli üç iş: ______________________", m, l3.bottom() + 50f, 30f, MUTED, TextFont.CAVEAT, width = w)
        add(title, sub, h1, l1, h2, l2, h3, l3, note)
        export("simple_todo", CanvasKind.SIMPLE_NOTES, landing = 0, coverPage = 0)
    }

    /** Haftalık plan: yedi gün ve notlar (renkli kutular). */
    @Test
    fun simpleWeek() {
        open("Haftalık Plan", CanvasKind.SIMPLE_NOTES, 1240, 1754, pages = 1, cover = false)
        page(0)
        val m = 70f
        val title = text("Haftalık Plan", m, 60f, 54f, INK, TextFont.POPPINS, bold = true, width = 1240f - 2 * m)
        val top = title.bottom() + 30f
        val gap = 24f
        val cw = (1240f - 2 * m - gap) / 2f
        val ch = (1754f - top - m - 3 * gap) / 4f
        val days = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar", "Notlar")
        val fills = listOf("#E3F2FD", "#E8F5E9", "#FFF8E1", "#FCE4EC", "#EDE7F6", "#E0F7FA", "#FFF3E0", "#F5F5F5")
        val items = ArrayList<com.karapp.drawguidance.data.model.Stroke?>()
        items += title
        days.forEachIndexed { i, d ->
            val x = m + (i % 2) * (cw + gap)
            val y = top + (i / 2) * (ch + gap)
            items += shape("ROUNDED_RECT", Rect(x, y, x + cw, y + ch), ShapeStyle("#90A4AE", 2.5f, fillColorHex = fills[i], cornerRadius = 18f))
            items += heading(d, x + 22f, y + 18f, color = if (i == 7) MUTED else ACCENT, size = 30f)
        }
        add(items.filterNotNull())
        export("simple_week", CanvasKind.SIMPLE_NOTES, landing = 0, coverPage = 0)
    }
}
