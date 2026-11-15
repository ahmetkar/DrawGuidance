package com.karapp.drawguidance

import androidx.compose.ui.geometry.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BackgroundSpec
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.model.TextParagraph
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialArt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v52 yeni not alma ve çizgi roman şablonlarının dillere göre adları ve açıklamaları. */
object V52TemplateInfo {
    private fun l(tr: String, en: String, ar: String, ja: String, ko: String, ru: String, zh: String) =
        mapOf("tr" to tr, "en" to en, "ar" to ar, "ja" to ja, "ko" to ko, "ru" to ru, "zh" to zh)

    /** Basit not almada listelenenler (katalogda tür SIMPLE_NOTES). */
    val SIMPLE = setOf("simple_reading", "simple_shopping", "simple_exam")

    val INFO: Map<String, Pair<Map<String, String>, Map<String, String>>> = mapOf(
        "notes_chemistry" to (
            l("Kimya: tepkimeler ve moleküller", "Chemistry: reactions and molecules", "الكيمياء: التفاعلات والجزيئات", "化学：反応と分子", "화학: 반응과 분자", "Химия: реакции и молекулы", "化学：反应与分子") to
                l("Formülden çizilen moleküller (H₂O, CO₂, glikoz), denkleştirilmiş tepkime, mol hesabı ve laboratuvar şekilleri.",
                    "Molecules drawn from formulas (H₂O, CO₂, glucose), a balanced reaction, a mole calculation and lab shapes.",
                    "جزيئات مرسومة من الصيغ (H₂O وCO₂ والجلوكوز)، ومعادلة موزونة، وحساب المول، وأشكال المختبر.",
                    "化学式から描いた分子（H₂O、CO₂、グルコース）、係数をそろえた反応式、モル計算、実験器具。",
                    "화학식으로 그린 분자(H₂O, CO₂, 포도당), 균형 맞춘 반응식, 몰 계산, 실험 기구.",
                    "Молекулы по формулам (H₂O, CO₂, глюкоза), уравненная реакция, расчёт молей и лабораторная посуда.",
                    "由化学式绘制的分子（H₂O、CO₂、葡萄糖）、配平的反应式、摩尔计算和实验器材。")
            ),
        "notes_physics" to (
            l("Fizik: elektrik ve hareket", "Physics: electricity and motion", "الفيزياء: الكهرباء والحركة", "物理：電気と運動", "물리: 전기와 운동", "Физика: электричество и движение", "物理：电与运动") to
                l("Ohm yasası, değerden çizilen devre grafiği, eğik atış ve çözümlü hesap örnekleri.",
                    "Ohm's law, a circuit graph drawn from values, projectile motion and worked calculations.",
                    "قانون أوم، ورسم دائرة من القيم، والمقذوفات، وأمثلة حسابية محلولة.",
                    "オームの法則、値から描くグラフ、斜方投射、計算の解答例。",
                    "옴의 법칙, 값으로 그리는 그래프, 포물선 운동, 풀이 예제.",
                    "Закон Ома, график по значениям, движение тела под углом и решённые задачи.",
                    "欧姆定律、由数值绘制的图像、斜抛运动和计算例题。")
            ),
        "notes_vocab" to (
            l("Kelime defteri", "Vocabulary notebook", "دفتر المفردات", "単語ノート", "단어장", "Словарная тетрадь", "单词本") to
                l("Kelime, anlam ve örnek cümle sütunları; tekrar kutuları.",
                    "Columns for word, meaning and example sentence; review boxes.",
                    "أعمدة للكلمة والمعنى والجملة المثال؛ ومربعات للمراجعة.",
                    "単語・意味・例文の欄と復習チェック。",
                    "단어, 뜻, 예문 칸과 복습 체크.",
                    "Колонки: слово, значение, пример; отметки повторения.",
                    "单词、释义和例句栏；复习勾选框。")
            ),
        "notes_timeline" to (
            l("Tarih zaman çizelgesi", "History timeline", "خط زمني تاريخي", "歴史年表", "역사 연표", "Историческая лента времени", "历史时间轴") to
                l("Olaylar zaman çizelgesi, neden-sonuç diyagramı ve özet; Sanayi Devrimi örneği.",
                    "Event timeline, a cause-and-effect diagram and summary; Industrial Revolution example.",
                    "خط زمني للأحداث، ومخطط السبب والنتيجة، وملخص؛ مثال الثورة الصناعية.",
                    "出来事の年表、因果関係の図、まとめ（産業革命の例）。",
                    "사건 연표, 원인과 결과 도표, 요약(산업 혁명 예시).",
                    "Лента событий, схема причин и следствий и итог; пример — промышленная революция.",
                    "事件时间轴、因果图和总结（工业革命示例）。")
            ),
        "simple_reading" to (
            l("Okuma günlüğü", "Reading log", "سجل القراءة", "読書記録", "독서 기록", "Дневник чтения", "阅读日志") to
                l("Kitap bilgisi, karakterler, sevdiğim bölüm ve puan.",
                    "Book details, characters, favourite part and rating.",
                    "معلومات الكتاب والشخصيات والجزء المفضل والتقييم.",
                    "本の情報、登場人物、好きな場面、評価。",
                    "책 정보, 등장인물, 좋아하는 부분, 평점.",
                    "О книге, герои, любимый эпизод и оценка.",
                    "书籍信息、人物、喜欢的部分和评分。")
            ),
        "simple_shopping" to (
            l("Alışveriş listesi", "Shopping list", "قائمة التسوق", "買い物リスト", "장보기 목록", "Список покупок", "购物清单") to
                l("Manav, market ve diğer bölümleriyle işaretlenebilir liste.",
                    "Checkable list with produce, grocery and other sections.",
                    "قائمة قابلة للتأشير بأقسام الخضار والبقالة وغيرها.",
                    "青果・食料品・その他に分かれたチェックリスト。",
                    "채소·식료품·기타로 나뉜 체크 목록.",
                    "Список с отметками: овощи, продукты и прочее.",
                    "分为果蔬、杂货和其他的勾选清单。")
            ),
        "simple_exam" to (
            l("Sınav çalışma planı", "Exam study plan", "خطة المذاكرة للامتحان", "試験勉強プラン", "시험 공부 계획", "План подготовки к экзамену", "考试复习计划") to
                l("Konular, gün gün plan, tekrar listesi ve hedef.",
                    "Topics, a day-by-day plan, a review list and a goal.",
                    "المواضيع وخطة يومية وقائمة مراجعة وهدف.",
                    "範囲、日ごとの計画、復習リスト、目標。",
                    "범위, 날짜별 계획, 복습 목록, 목표.",
                    "Темы, план по дням, список повторения и цель.",
                    "考点、每日计划、复习清单和目标。")
            ),
        "comic_strips" to (
            l("Gazete şeritleri", "Newspaper strips", "شرائط الصحف المصورة", "新聞の4コマ・コマ漫画", "신문 연재 만화", "Газетные стрипы", "报纸连环画") to
                l("4 sayfa kısa şerit: 4 kare, 3 kare, 2 kare ve 4'lü ızgara; balonlar okuma sırasında.",
                    "4 pages of short strips: 4 panels, 3 panels, 2 panels and a 2×2 grid; balloons in reading order.",
                    "4 صفحات من الشرائط القصيرة: 4 لوحات و3 و2 وشبكة 2×2؛ الفقاعات بترتيب القراءة.",
                    "短いコマ漫画4ページ：4コマ、3コマ、2コマ、2×2。フキダシは読み順に配置。",
                    "짧은 연재 4페이지: 4칸, 3칸, 2칸, 2×2 격자. 말풍선은 읽는 순서로.",
                    "4 страницы коротких стрипов: 4, 3 и 2 панели и сетка 2×2; баллоны по порядку чтения.",
                    "4页短篇条漫：4格、3格、2格和2×2网格；气泡按阅读顺序排列。")
            ),
        "comic_blank_action" to (
            l("Aksiyon sayfaları", "Action pages", "صفحات أكشن", "アクションのページ", "액션 페이지", "Страницы экшена", "动作页面") to
                l("4 sayfa: tam sayfa açılış, büyük altta, altılı ızgara ve eğik aksiyon panelleri; bağırma balonları.",
                    "4 pages: a full-page splash, big bottom panel, six-panel grid and slanted action panels; shout balloons.",
                    "4 صفحات: لوحة افتتاحية كاملة، ولوحة كبيرة بالأسفل، وشبكة سداسية، ولوحات أكشن مائلة؛ فقاعات صراخ.",
                    "4ページ：見開き風の大ゴマ、下に大ゴマ、6コマ、斜めのアクションコマ。叫びのフキダシ。",
                    "4페이지: 한 페이지 오프닝, 아래 큰 칸, 6칸 격자, 사선 액션 칸. 외침 말풍선.",
                    "4 страницы: разворот на всю страницу, большая нижняя панель, сетка 6 и косые панели; крики.",
                    "4页：整页开场、下方大格、六格网格和斜切动作格；喊叫气泡。")
            ),
    )
}

/**
 * v52 yeni not alma şablonları (yalnız `-PtemplateOut=<klasör>` verilince): kimya (formülden moleküller), fizik
 * (değerden çizilen şekiller), kelime defteri, tarih zaman çizelgesi; basit not almada okuma günlüğü, alışveriş listesi
 * ve sınav planı. Çizgi roman şablonları V48TemplateExport'ta (comicStrips, comicBlankAction).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "tr-w800dp-h1280dp-xhdpi")
class V52TemplateExport : TemplateExportBase() {

    private fun bottomOf(s: com.karapp.drawguidance.data.model.Stroke?, fallback: Float) = s?.points?.maxOf { it.y } ?: fallback

    @Test
    fun notesChemistry() {
        open("Kimyasal Tepkimeler", CanvasKind.NOTES, 1240, 1754, pages = 1, cover = false, background = BackgroundSpec(patternId = "note.grid5"))
        page(0)
        val (head, left, right) = notesPanels("header_two_cols").let { Triple(it[0], it[1], it[2]) }
        val pad = 22f
        val hw = head.width - 2 * pad
        val title = text("Kimyasal Tepkimeler ve Mol", head.left + pad, head.top + pad, 44f, INK, TextFont.POPPINS, bold = true, width = hw)
        val sub = text("Kimya · Ders notu · Tarih: ________", head.left + pad, title.bottom() + 8f, 24f, MUTED, width = hw)
        add(title, sub, text("Molekül şekilleri formülden çizilir: şekle dokunup formülü değiştir (ör. C2H5OH, CH3COOH).", head.left + pad, sub.bottom() + 10f, 24f, BLUE, width = hw))
        val lx = left.left + pad
        val lw = left.width - 2 * pad
        val h1 = heading("Moleküller", lx, left.top + pad)
        val m1 = shape("lib.sci.chem.molecule_3d", Rect(lx, h1.bottom() + 12f, lx + lw * 0.48f, h1.bottom() + 12f + lw * 0.40f), params = mapOf("formula" to "H2O"))
        val m2 = shape("lib.sci.chem.molecule_3d", Rect(lx + lw * 0.52f, h1.bottom() + 12f, lx + lw, h1.bottom() + 12f + lw * 0.40f), params = mapOf("formula" to "CO2"))
        val mb = maxOf(bottomOf(m1, h1.bottom() + lw * 0.42f), bottomOf(m2, h1.bottom() + lw * 0.42f))
        val cap = text("Su (açısal, 104,5°)   ·   Karbondioksit (doğrusal)", lx, mb + 8f, 22f, MUTED, width = lw)
        val m3 = shape("lib.sci.chem.molecule", Rect(lx + lw * 0.05f, cap.bottom() + 16f, lx + lw * 0.95f, cap.bottom() + 16f + lw * 0.58f), params = mapOf("formula" to "C6H12O6"))
        val m3b = bottomOf(m3, cap.bottom() + lw * 0.6f)
        val cap2 = text("Glikoz C₆H₁₂O₆ (formülden çizilen yapı)", lx, m3b + 8f, 22f, MUTED, width = lw)
        val h2 = heading("Laboratuvar", lx, cap2.bottom() + 30f)
        val lab1 = shape("lib.sci.chem.erlenmeyer", Rect(lx, h2.bottom() + 12f, lx + lw * 0.3f, h2.bottom() + 12f + lw * 0.36f))
        val lab2 = shape("lib.sci.chem.beaker", Rect(lx + lw * 0.35f, h2.bottom() + 12f, lx + lw * 0.65f, h2.bottom() + 12f + lw * 0.36f))
        val lab3 = shape("lib.sci.chem.bunsen", Rect(lx + lw * 0.7f, h2.bottom() + 12f, lx + lw, h2.bottom() + 12f + lw * 0.36f))
        add(h1, m1, m2, cap, m3, cap2, h2, lab1, lab2, lab3)
        val rx = right.left + pad
        val rw = right.width - 2 * pad
        val h3 = heading("Tepkime denklemi", rx, right.top + pad)
        val eq = text("CH₄ + 2 O₂ → CO₂ + 2 H₂O", rx, h3.bottom() + 14f, 34f, INK, TextFont.POPPINS, bold = true, width = rw)
        val eqn = text("Denkleştirme: her elementin atom sayısı iki tarafta eşit olmalı.\nC: 1 = 1   ·   H: 4 = 2·2   ·   O: 2·2 = 2 + 2", rx, eq.bottom() + 12f, 24f, MUTED, width = rw)
        val h4 = heading("Mol hesabı", rx, eqn.bottom() + 34f)
        val f = TutorialArt.formula("physics.ideal_gas", rx, h4.bottom() + 14f, 36f, BLUE)
        val fb = bottomOf(f, h4.bottom() + 60f)
        val steps = text("n = m / M_A\n16 g CH₄ → n = 16 / 16 = 1 mol\n1 mol CH₄ için 2 mol O₂ gerekir\nOluşan su: 2 mol · 18 g/mol = 36 g", rx, fb + 16f, 28f, INK, TextFont.CAVEAT, width = rw, paragraph = TextParagraph.NUMBERED)
        val ans = text("Sonuç: 36 g H₂O", rx, steps.bottom() + 14f, 32f, GREEN, TextFont.POPPINS, bold = true)
        val h5 = heading("pH ölçeği", rx, ans.bottom() + 34f)
        val ph = shape("lib.sci.chem.ph_scale", Rect(rx, h5.bottom() + 12f, rx + rw, h5.bottom() + 12f + rw * 0.32f))
        val phb = bottomOf(ph, h5.bottom() + rw * 0.34f)
        val q = text("Soru: 44 g CO₂ kaç moldür? (C = 12, O = 16)", rx, phb + 20f, 28f, RED, TextFont.CAVEAT, width = rw)
        add(h3, eq, eqn, h4, f, steps, ans, h5, ph, q)
        export("notes_chemistry", CanvasKind.NOTES, landing = 0, coverPage = 0)
    }

    @Test
    fun notesPhysics() {
        open("Elektrik ve Hareket", CanvasKind.NOTES, 1240, 1754, pages = 1, cover = false, background = BackgroundSpec(patternId = "note.ruled_college"))
        page(0)
        val (cue, notes, summary) = notesPanels("cornell").let { Triple(it[0], it[1], it[2]) }
        val pad = 22f
        val nx = notes.left + pad
        val nw = notes.width - 2 * pad
        val title = text("Elektrik ve Hareket", nx, notes.top + pad, 44f, INK, TextFont.POPPINS, bold = true, width = nw)
        val sub = text("Fizik · Formül ve hesap · Şekiller değerlerden çizilir", nx, title.bottom() + 6f, 22f, MUTED, width = nw)
        val h1 = heading("1. Ohm yasası", nx, sub.bottom() + 28f)
        val f1 = TutorialArt.formula("physics.ohm", nx, h1.bottom() + 12f, 40f, BLUE)
        val f1b = bottomOf(f1, h1.bottom() + 60f)
        val g1 = shape("lib.sci.phys.ohm_graph", Rect(nx, f1b + 14f, nx + nw * 0.55f, f1b + 14f + nw * 0.4f), params = mapOf("R" to "20", "Imax" to "3"))
        val g1b = f1b + 14f + nw * 0.4f
        val c1 = text("R = 20 Ω, I = 3 A → V = I · R = 60 V", nx, g1b + 10f, 28f, INK, TextFont.CAVEAT, width = nw)
        val f2 = TutorialArt.formula("physics.electric_power", nx, c1.bottom() + 16f, 36f, BLUE)
        val f2b = bottomOf(f2, c1.bottom() + 56f)
        val c2 = text("P = V · I = 60 · 3 = 180 W", nx, f2b + 8f, 28f, INK, TextFont.CAVEAT, width = nw)
        val h2 = heading("2. Eğik atış", nx, c2.bottom() + 30f)
        val p = shape("lib.sci.phys.projectile", Rect(nx + nw * 0.1f, h2.bottom() + 12f, nx + nw * 0.9f, h2.bottom() + 12f + nw * 0.4f), params = mapOf("v" to "20", "theta" to "45"))
        val pb = h2.bottom() + 12f + nw * 0.4f
        val c3 = text("v = 20 m/s, θ = 45° → menzil R = v²·sin2θ / g ≈ 40,8 m; en büyük yükseklik ≈ 10,2 m", nx, pb + 10f, 24f, INK, width = nw)
        add(title, sub, h1, f1, g1, c1, f2, c2, h2, p, c3)
        val cx = cue.left + 18f
        val cw = cue.width - 36f
        add(
            heading("Sorular", cx, cue.top + pad, size = 24f),
            text("Direnç iki katına çıkarsa akım?", cx, h1.top, 28f, RED, TextFont.CAVEAT, width = cw),
            text("Güç birimi nedir?", cx, f2b - 30f, 28f, RED, TextFont.CAVEAT, width = cw),
            text("Hangi açıda menzil en büyük?", cx, h2.top, 28f, RED, TextFont.CAVEAT, width = cw),
        )
        val sx = summary.left + pad
        val sh = heading("Özet", sx, summary.top + pad - 4f)
        add(sh, text("Şekle dokunup değerleri değiştir (R, I, v, θ): grafik, yörünge ve sonuçlar yeniden hesaplanır. Formüller her harfiyle düzenlenebilir.", sx, sh.bottom() + 10f, 24f, width = summary.width - 2 * pad))
        export("notes_physics", CanvasKind.NOTES, landing = 0, coverPage = 0)
    }

    @Test
    fun notesVocab() {
        open("Kelime Defteri", CanvasKind.NOTES, 1240, 1754, pages = 1, cover = false, background = BackgroundSpec(patternId = "note.ruled_college"))
        page(0)
        val m = 70f
        val w = 1240f - 2 * m
        val title = text("Kelime Defteri", m, 60f, 52f, INK, TextFont.POPPINS, bold = true, width = w)
        val sub = text("Dil: ________   ·   Ünite: ____   ·   Tarih: ________", m, title.bottom() + 8f, 24f, MUTED, width = w)
        val top = sub.bottom() + 30f
        val cols = listOf(0f, 0.28f, 0.55f, 1f)
        val heads = listOf("Kelime", "Anlam", "Örnek cümle")
        val items = ArrayList<com.karapp.drawguidance.data.model.Stroke?>()
        items += title; items += sub
        items += shape("ROUNDED_RECT", Rect(m, top, m + w, top + 64f), ShapeStyle("#90A4AE", 2f, fillColorHex = "#E3F2FD", cornerRadius = 14f))
        heads.forEachIndexed { i, h -> items += heading(h, m + w * cols[i] + 16f, top + 14f, size = 28f) }
        val words = listOf(
            Triple("journey", "yolculuk", "The journey took three days."),
            Triple("curious", "meraklı", "Cats are curious animals."),
            Triple("improve", "geliştirmek", "Practice helps you improve."),
            Triple("borrow", "ödünç almak", "Can I borrow your pen?"),
            Triple("", "", ""), Triple("", "", ""), Triple("", "", ""), Triple("", "", ""),
        )
        val rowH = 120f
        words.forEachIndexed { r, (a, b, c) ->
            val y = top + 80f + r * rowH
            items += shape("LINE", Rect(m, y + rowH - 8f, m + w, y + rowH - 8f), ShapeStyle("#B0BEC5", 1.5f))
            if (a.isNotEmpty()) {
                items += text(a, m + 16f, y + 14f, 32f, BLUE, TextFont.POPPINS, bold = true, width = w * 0.26f)
                items += text(b, m + w * cols[1] + 16f, y + 16f, 28f, INK, width = w * 0.25f)
                items += text(c, m + w * cols[2] + 16f, y + 16f, 26f, MUTED, TextFont.CAVEAT, width = w * 0.43f)
            }
        }
        val y = top + 80f + words.size * rowH + 20f
        items += heading("Tekrar", m, y, color = GREEN)
        items += text("Ertesi gün\n3 gün sonra\n1 hafta sonra\n2 hafta sonra", m, y + 50f, 28f, INK, width = w, paragraph = TextParagraph.CHECKLIST)
        add(items.filterNotNull())
        export("notes_vocab", CanvasKind.NOTES, landing = 0, coverPage = 0)
    }

    @Test
    fun notesTimeline() {
        open("Sanayi Devrimi", CanvasKind.NOTES, 1240, 1754, pages = 1, cover = false, background = BackgroundSpec(patternId = "note.dots"))
        page(0)
        val m = 80f
        val w = 1240f - 2 * m
        val title = text("Sanayi Devrimi", m, 60f, 52f, INK, TextFont.POPPINS, bold = true, width = w)
        val sub = text("Tarih · Zaman çizelgesi · 1760–1900", m, title.bottom() + 8f, 24f, MUTED, width = w)
        val h1 = heading("Önemli olaylar", m, sub.bottom() + 34f)
        val tl = TutorialArt.diagram(
            DiagramKind.TIMELINE, Rect(m, h1.bottom() + 14f, m + w, h1.bottom() + 14f + 340f), ShapeStyle(INK, 2.5f), "ocean",
            listOf("1769 Buhar makinesi", "1825 İlk demiryolu", "1837 Telgraf", "1879 Ampul", "1885 Otomobil"),
        )
        val tb = tl.points.maxOf { it.y }
        val h2 = heading("Neden ve sonuçlar", m, tb + 36f)
        val fish = TutorialArt.diagram(
            DiagramKind.FISHBONE, Rect(m, h2.bottom() + 14f, m + w, h2.bottom() + 14f + 460f), ShapeStyle(INK, 2.5f), "forest",
            listOf("Sanayi Devrimi", "Kömür ve demir", "Nüfus artışı", "Sömürgeler", "Yeni buluşlar"),
        )
        val fb = fish.points.maxOf { it.y }
        val h3 = heading("Özet", m, fb + 36f)
        val sum = text("Makineleşme üretimi atölyelerden fabrikalara taşıdı; kentler büyüdü, ulaşım ve iletişim hızlandı, işçi sınıfı ortaya çıktı.", m, h3.bottom() + 12f, 28f, INK, width = w)
        val q = text("Soru: Buhar makinesi ulaşımı nasıl değiştirdi?", m, sum.bottom() + 20f, 30f, RED, TextFont.CAVEAT, width = w)
        add(title, sub, h1, tl, h2, fish, h3, sum, q)
        export("notes_timeline", CanvasKind.NOTES, landing = 0, coverPage = 0)
    }

    // ================================================================ basit not alma

    @Test
    fun simpleReading() {
        open("Okuma Günlüğü", CanvasKind.NOTES, 1240, 1754, pages = 1, cover = false, background = BackgroundSpec(patternId = "note.ruled_college"))
        page(0)
        val m = 150f
        val w = 1240f - m - 80f
        val title = text("Okuma Günlüğü", m, 70f, 54f, INK, TextFont.POPPINS, bold = true, width = w)
        val info = text("Kitap: ______________________\nYazar: ______________________\nBaşlama: __.__   ·   Bitiş: __.__   ·   Sayfa: ____", m, title.bottom() + 16f, 28f, MUTED, width = w)
        val h1 = heading("Karakterler", m, info.bottom() + 40f)
        val l1 = text("Ana karakter:\nYardımcı karakter:\nKötü karakter:", m, h1.bottom() + 12f, 28f, INK, width = w, paragraph = TextParagraph.BULLET)
        val h2 = heading("Kısaca konu", m, l1.bottom() + 40f)
        val box = shape("ROUNDED_RECT", Rect(m, h2.bottom() + 12f, m + w, h2.bottom() + 12f + 300f), ShapeStyle("#90A4AE", 2f, fillColorHex = "#FFFDE7", cornerRadius = 18f))
        val h3 = heading("En sevdiğim bölüm", m, h2.bottom() + 340f)
        val box2 = shape("ROUNDED_RECT", Rect(m, h3.bottom() + 12f, m + w, h3.bottom() + 12f + 240f), ShapeStyle("#90A4AE", 2f, fillColorHex = "#E8F5E9", cornerRadius = 18f))
        val h4 = heading("Puanım", m, h3.bottom() + 280f)
        val stars = (0 until 5).map { i -> shape("STAR", Rect(m + i * 90f, h4.bottom() + 12f, m + i * 90f + 72f, h4.bottom() + 84f), ShapeStyle("#F59E0B", 3f)) }
        add((listOf(title, info, h1, l1, h2, box, h3, box2, h4) + stars).filterNotNull())
        export("simple_reading", CanvasKind.NOTES, landing = 0, coverPage = 0)
    }

    @Test
    fun simpleShopping() {
        open("Alışveriş Listesi", CanvasKind.NOTES, 1240, 1754, pages = 1, cover = false, background = BackgroundSpec(patternId = "note.dots"))
        page(0)
        val m = 90f
        val w = 1240f - 2 * m
        val title = text("Alışveriş Listesi", m, 80f, 54f, INK, TextFont.POPPINS, bold = true, width = w)
        val sub = text("Tarih: __.__.____   ·   Bütçe: ______", m, title.bottom() + 8f, 26f, MUTED, width = w)
        val h1 = heading("Manav", m, sub.bottom() + 46f, color = GREEN)
        val l1 = text("Domates\nElma\nMuz\nMaydanoz", m, h1.bottom() + 14f, 30f, INK, width = w, paragraph = TextParagraph.CHECKLIST)
        val h2 = heading("Market", m, l1.bottom() + 40f, color = BLUE)
        val l2 = text("Süt\nEkmek\nPeynir\nYumurta\nPirinç", m, h2.bottom() + 14f, 30f, INK, width = w, paragraph = TextParagraph.CHECKLIST)
        val h3 = heading("Diğer", m, l2.bottom() + 40f, color = RED)
        val l3 = text("Diş macunu\nDeterjan\nPil", m, h3.bottom() + 14f, 30f, INK, width = w, paragraph = TextParagraph.CHECKLIST)
        add(title, sub, h1, l1, h2, l2, h3, l3, text("Unutma: bez çanta!", m, l3.bottom() + 50f, 32f, MUTED, TextFont.CAVEAT, width = w))
        export("simple_shopping", CanvasKind.NOTES, landing = 0, coverPage = 0)
    }

    @Test
    fun simpleExam() {
        open("Sınav Planı", CanvasKind.NOTES, 1240, 1754, pages = 1, cover = false, background = BackgroundSpec(patternId = "note.grid5"))
        page(0)
        val m = 80f
        val w = 1240f - 2 * m
        val title = text("Sınav Çalışma Planı", m, 60f, 52f, INK, TextFont.POPPINS, bold = true, width = w)
        val sub = text("Ders: __________   ·   Sınav tarihi: __.__.____", m, title.bottom() + 8f, 26f, MUTED, width = w)
        val h1 = heading("Konular", m, sub.bottom() + 36f)
        val l1 = text("Ünite 1\nÜnite 2\nÜnite 3\nDeneme sınavı", m, h1.bottom() + 12f, 28f, INK, width = w, paragraph = TextParagraph.CHECKLIST)
        val h2 = heading("Gün gün", m, l1.bottom() + 36f)
        val top = h2.bottom() + 14f
        val gap = 18f
        val cw = (w - 2 * gap) / 3f
        val ch = 220f
        val days = listOf("1. gün", "2. gün", "3. gün", "4. gün", "5. gün", "Tekrar")
        val fills = listOf("#E3F2FD", "#E8F5E9", "#FFF8E1", "#FCE4EC", "#EDE7F6", "#F5F5F5")
        val items = ArrayList<com.karapp.drawguidance.data.model.Stroke?>()
        items += title; items += sub; items += h1; items += l1; items += h2
        days.forEachIndexed { i, d ->
            val x = m + (i % 3) * (cw + gap)
            val y = top + (i / 3) * (ch + gap)
            items += shape("ROUNDED_RECT", Rect(x, y, x + cw, y + ch), ShapeStyle("#90A4AE", 2.5f, fillColorHex = fills[i], cornerRadius = 16f))
            items += heading(d, x + 18f, y + 14f, color = if (i == 5) MUTED else ACCENT, size = 28f)
        }
        val y = top + 2 * (ch + gap) + 20f
        items += heading("Hedefim", m, y, color = GREEN)
        items += text("Puan: ____   ·   Her gün 2 saat   ·   Ara: 50 dk çalış, 10 dk dinlen", m, y + 50f, 28f, INK, TextFont.CAVEAT, width = w)
        add(items.filterNotNull())
        export("simple_exam", CanvasKind.NOTES, landing = 0, coverPage = 0)
    }
}
