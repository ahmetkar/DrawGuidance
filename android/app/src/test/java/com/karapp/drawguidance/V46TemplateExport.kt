package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.comic.ComicLinesKind
import com.karapp.drawguidance.content.comic.ComicSfx
import com.karapp.drawguidance.data.comicassets.ComicAssets
import com.karapp.drawguidance.data.local.CanvasAssetDao
import com.karapp.drawguidance.data.local.CanvasDao
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BackgroundSpec
import com.karapp.drawguidance.data.model.BalloonStyle
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.data.model.TextBox
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.model.TextParagraph
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.data.templates.CanvasTemplates
import com.karapp.drawguidance.data.templates.TemplateAsset
import com.karapp.drawguidance.data.templates.TemplateCatalog
import com.karapp.drawguidance.data.templates.TemplateEntry
import com.karapp.drawguidance.data.templates.TemplateMeta
import com.karapp.drawguidance.ui.drawing.DiagramThemes
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import com.karapp.drawguidance.ui.drawing.TailDirection
import com.karapp.drawguidance.ui.drawing.TextRenderer
import com.karapp.drawguidance.ui.screens.canvasedit.state.BalloonTextOptions
import com.karapp.drawguidance.ui.screens.canvasedit.state.ComicCover
import com.karapp.drawguidance.ui.screens.canvasedit.state.NotesPanels
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialArt
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * v46 hazır tuval şablonlarının üretimi (yalnız `-PtemplateOut=<klasör>` verilince): her şablon düzenleyicinin
 * kendisi sürülerek kurulur — panel şablonları, hazır mekânlar ve karakterler (yalnız Free olanlar), balonlar, efekt
 * yazıları, hız/odak çizgileri, not alanları, yazılar, formüller, grafikler, diyagramlar ve bilim şekilleri; yani
 * yalnız uygulamada olan elemanlar. Sonra kaydedilen belge, kullandığı resimler ve kapak resmi
 * `<klasör>/templates/<kimlik>/` altına yazılır; `catalog.json` dillere göre ad ve açıklamalarla güncellenir.
 *
 * Üretilen dosyalar `app/src/main/assets/templates`'e kopyalanır (V46TemplatesTest bunları doğrular).
 */
/** v47 şablon üreticilerinin ortak yardımcıları (düzenleyici sürülerek kurulur, dışa aktarılır). */
abstract class TemplateExportBase : V32EditorBase() {
    protected val out: File? = System.getProperty("templateOut")?.let { File(it, CanvasTemplates.DIR) }

    protected fun repo() = GlobalContext.get().get<CanvasRepository>()
    protected fun dao() = GlobalContext.get().get<CanvasDao>()
    protected fun assetDao() = GlobalContext.get().get<CanvasAssetDao>()
    protected fun host() = vm().tutorialHost
    protected val q = ContentCodec::quantize

    // ---------------------------------------------------------------- tuval

    protected var titleId: String? = null

    protected fun open(name: String, kind: CanvasKind, w: Int, h: Int, pages: Int, cover: Boolean, background: BackgroundSpec = BackgroundSpec(), paper: String = "#FFFFFF") {
        assumeTrue(out != null)
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val coverStrokes = if (cover) ComicCover.build(w, h, name, ComicCover.SUBTITLE_TEXT) else null
        titleId = coverStrokes?.getOrNull(1)?.id
        runBlocking { repo().createCanvas(name, w, h, paper, aiEnabled = true, kind = kind, pageCount = pages, background = background, cover = coverStrokes) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
        settleFor(500)
    }

    protected fun page(i: Int) {
        compose.runOnIdle { vm().goToPage(i) }
        waitFor { live().currentPage == i }
    }

    protected fun strokes() = live().strokes
    protected fun images() = strokes().filter { it.brushType == BrushType.IMAGE }

    protected fun add(vararg s: Stroke?) = add(s.filterNotNull())
    protected fun add(list: List<Stroke>) {
        if (list.isEmpty()) return
        val before = strokes().size
        compose.runOnIdle { host().commitElements(list) }
        waitFor { strokes().size >= before + list.size }
    }

    // ---------------------------------------------------------------- çizgi roman

    /** Panel şablonu; panelleri okuma sırasıyla (üstten alta; [rtl] sağdan sola) döndürür. */
    protected fun panels(template: String, count: Int, rtl: Boolean = false, extraRows: Int = 0): List<Pair<Int, Rect>> {
        compose.runOnIdle { vm().insertTemplate(template) }
        repeat(extraRows) { compose.runOnIdle { vm().addPanel(null, rows = true) } }
        waitFor { vm().comicPanelRects().size == count }
        compose.runOnIdle { vm().clearSelection() }
        return readingOrder(rtl)
    }

    protected fun readingOrder(rtl: Boolean): List<Pair<Int, Rect>> =
        vm().comicPanelRects().entries.map { it.key to it.value }
            .sortedWith(compareBy({ (it.second.top / 20f).toInt() }, { if (rtl) -it.second.left else it.second.left }))

    protected fun backdrop(id: String, leaf: Int?) {
        val b = ComicAssets.catalog.backgrounds.first { it.id == id }
        require(com.karapp.drawguidance.data.billing.PremiumRules.backgroundFeature(b) == null) { "Premium mekân: $id" }
        val before = images().size
        compose.runOnIdle { vm().insertLocationBackground(b, leaf) }
        waitFor(20_000) { images().size == before + 1 }
    }

    protected fun strokeRect(id: String): Rect {
        val s = strokes().first { it.id == id }
        return Rect(s.points.minOf { it.x }, s.points.minOf { it.y }, s.points.maxOf { it.x }, s.points.maxOf { it.y })
    }

    /**
     * Karakter çeşidi [panel] içine: yatayda [cx] ortalı, boyu panelin [height] payı; alt kenarı panelin altına
     * basar ([bottom] payı kadar yukarıda).
     */
    protected fun character(id: String, variant: String, panel: Rect, cx: Float, height: Float, bottom: Float = 0.005f) {
        val c = ComicAssets.catalog.characters.first { it.id == id }
        require(com.karapp.drawguidance.data.billing.PremiumRules.characterFeature(c) == null) { "Premium karakter: $id" }
        val v = c.variants.first { it.id == variant }
        val before = images().size
        compose.runOnIdle { vm().addHere("characters", panel.center); vm().insertCharacter(v) }
        waitFor(20_000) { images().size == before + 1 }
        val img = images().last()
        compose.runOnIdle { vm().clearSelection(); host().select(setOf(img.id)) }
        compose.runOnIdle {
            val b = strokeRect(img.id)
            val f = panel.height * height / b.height
            vm().scaleSelected(f, f, b.center)
        }
        compose.runOnIdle {
            val b = strokeRect(img.id)
            vm().moveSelectionTo(cx - b.width / 2f, panel.bottom - b.height - panel.height * bottom)
        }
        compose.runOnIdle { vm().clearSelection() }
        compose.waitForIdle()
    }

    protected fun balloon(at: Offset, text: String, style: BalloonStyle = BalloonStyle.SPEECH, tail: TailDirection = TailDirection.BOTTOM_LEFT, size: Float? = null, speakers: Int = 1) {
        val before = strokes().size
        compose.runOnIdle {
            vm().addHere("balloon", at)
            vm().insertBalloon(style, speakers, BalloonTextOptions(content = text, tail = tail, sizePx = size))
        }
        waitFor { strokes().size > before }
        compose.runOnIdle { vm().finishAction(); vm().clearSelection() }
    }

    protected fun narration(at: Offset, text: String, size: Float? = null) = balloon(at, text, BalloonStyle.NARRATION, size = size, speakers = 0)

    protected fun sfx(text: String, at: Offset, size: Float) {
        val preset = ComicSfx.presets.first { it.text == text }
        add(host().sfx(preset, at, size))
    }

    protected fun lines(kind: ComicLinesKind, area: Rect, density: Float = 1f, ink: String = "#111111") {
        require(!com.karapp.drawguidance.data.billing.PremiumRules.isAdvancedLines(kind))
        add(host().comicLines(kind, density, area, ink))
    }

    // ---------------------------------------------------------------- not alma

    protected fun text(
        content: String, x: Float, y: Float, size: Float, color: String = INK, font: TextFont = TextFont.NUNITO,
        bold: Boolean = false, width: Float? = null, paragraph: TextParagraph = TextParagraph.NORMAL,
        align: TextAlignKind = TextAlignKind.LEFT, italic: Boolean = false,
    ): Stroke {
        // Liste paragraflarında işaretler yazının içindedir (düzenleyicideki gibi: TextLists)
        val body = if (paragraph.isList) com.karapp.drawguidance.data.text.TextLists.apply(content, paragraph) else content
        val spec = TextSpec(body, font = font, bold = bold, italic = italic, align = align, paragraph = paragraph, box = width?.let { TextBox(width = it) })
        val points = TextRenderer.quadAt(spec, size, x, y).map { StrokePoint(q(it.x), q(it.y)) }
        return Stroke(UUID.randomUUID().toString(), points, color, BrushType.TEXT, size, 1f, 0, text = spec)
    }

    /** Yazının yüksekliği (yerleşim için). */
    protected fun Stroke.bottom(): Float = points.maxOf { it.y }
    protected val Stroke.top: Float get() = points.minOf { it.y }

    protected fun heading(content: String, x: Float, y: Float, color: String = ACCENT, size: Float = 30f) =
        text(content.uppercase(java.util.Locale("tr")), x, y, size, color, TextFont.POPPINS, bold = true)

    protected fun shape(kind: String, r: Rect, style: ShapeStyle = ShapeStyle(INK, 3f), params: Map<String, String>? = null, graph: com.karapp.drawguidance.data.model.GraphSpec? = null): Stroke? =
        ShapeFactory.build(kind, r.topLeft, r.bottomRight, style, graph, UUID.randomUUID().toString(), 0, params = params)

    protected fun graph(kind: String, r: Rect, edit: (com.karapp.drawguidance.data.model.GraphSpec) -> com.karapp.drawguidance.data.model.GraphSpec): Stroke? {
        val g = edit(ShapeRegistry.defaultGraph(kind))
        return shape(kind, r, ShapeStyle(INK, 3f), graph = g)
    }

    protected fun notesPanels(layout: String): List<Rect> {
        val c = live()
        val l = NotesPanels.byId(layout)!!
        add(NotesPanels.build(l, c.width.toFloat(), c.height.toFloat()))
        return l.rects(c.width.toFloat(), c.height.toFloat()).map { Rect(it[0], it[1], it[2], it[3]) }
    }

    // ---------------------------------------------------------------- dışa aktarma

    protected fun export(id: String, kind: CanvasKind, landing: Int, coverPage: Int) {
        val dir = File(out!!, id).apply { deleteRecursively(); mkdirs() }
        page(landing)
        compose.runOnIdle { vm().clearSelection() }
        if (vm().mode.value.kind != com.karapp.drawguidance.ui.screens.canvasedit.ModeKind.BASE) compose.runOnIdle { vm().finishAction() }
        compose.runOnIdle { vm().chooseMode(com.karapp.drawguidance.ui.screens.canvasedit.EditMode.DRAW) }
        settleFor(800)
        val localId = live().localId
        SystemBack.press()
        waitFor(20_000) { !exists("drawing_canvas") }
        val e = runBlocking { dao().getByLocalId(localId)!! }
        val doc = ContentCodec.decodeDocument(e.content)
        val used = doc.pages.flatten().mapNotNullTo(LinkedHashSet()) { it.assetId }
        val all = runBlocking { assetDao().idsForCanvas(localId) }
        all.filter { e.background.contains(it) }.forEach { used += it }
        val rows = runBlocking { assetDao().getByIds(used.toList()) }.associateBy { it.id }
        val assets = used.mapIndexed { i, a ->
            val row = rows.getValue(a)
            val ext = if (row.mimeType.contains("png")) "png" else "webp"
            val file = "img_${i + 1}.$ext"
            File(dir, file).writeBytes(row.data)
            val (w, h) = if (row.width > 0) row.width to row.height else android.graphics.BitmapFactory.decodeByteArray(row.data, 0, row.data.size).let { it.width to it.height }
            TemplateAsset(a, file, row.mimeType, w, h)
        }
        File(dir, "content.bin").writeBytes(e.content)
        val meta = TemplateMeta(
            width = e.width, height = e.height, backgroundColorHex = e.backgroundColorHex, background = e.background,
            guide = e.guide, currentPage = e.currentPage, titleId = titleId?.takeIf { t -> doc.pages.firstOrNull()?.any { it.id == t } == true },
            assets = assets,
        )
        File(dir, "meta.json").writeText(CanvasTemplates.encodeMeta(meta))
        // Kapak resmi: kapak sayfası (yoksa ilk sayfa), kart için küçük
        val ui = runBlocking { repo().getCanvas(localId)!! }
        runBlocking { GlobalContext.get().get<com.karapp.drawguidance.data.images.CanvasAssetStore>().ensureLoaded(ui) }
        val exporter = com.karapp.drawguidance.data.images.ImageExporter(compose.activity)
        val bmp = exporter.render(ui, scale = COVER_SIDE / max(ui.width, ui.height).toFloat(), page = coverPage)
        File(dir, "cover.webp").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, 82, it) }
        // Önizleme (inceleme için büyük PNG; uygulamaya girmez)
        System.getProperty("templateOut")?.let { root ->
            val prev = File(root, "preview").apply { mkdirs() }
            for (p in doc.pages.indices) {
                val big = exporter.render(ui, scale = 900f / max(ui.width, ui.height), page = p)
                File(prev, "$id-$p.png").outputStream().use { big.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        writeCatalog(id, kind, e.width, e.height, doc.pages.size)
    }

    protected fun writeCatalog(id: String, kind: CanvasKind, w: Int, h: Int, pages: Int) {
        val file = File(out!!, "catalog.json")
        val old = if (file.exists()) CanvasTemplates.parseCatalog(file.readText()).templates else emptyList()
        val info = INFO.getValue(id)
        val entry = TemplateEntry(id, kind.name, info.first, info.second, pages, w, h)
        val merged = (old.filter { it.id != id } + entry).sortedBy { ORDER.indexOf(it.id) }
        file.writeText(CanvasTemplates.encodeCatalog(TemplateCatalog(1, merged)))
    }

    companion object {
        const val COVER_SIDE = 420f
        /** 4 karede balon yazısının boyu (şeritler alçak). */
        const val INK = "#263238"
        const val MUTED = "#607D8B"
        const val ACCENT = "#1565C0"
        const val BLUE = "#1E63C6"
        const val RED = "#C62828"
        const val GREEN = "#2E7D32"

        /** v48 katalog sırası: çizgi roman (boş sayfalar), not alma, basit not alma. */
        val ORDER = listOf(
            "comic_blank_pages", "comic_blank_manga", "comic_blank_webtoon",
            "notes_cornell", "notes_math", "notes_lab", "notes_meeting", "notes_mindmap",
            "simple_lesson", "simple_todo", "simple_week",
        )

        fun l(tr: String, en: String, ar: String, ja: String, ko: String, ru: String, zh: String) =
            mapOf("tr" to tr, "en" to en, "ar" to ar, "ja" to ja, "ko" to ko, "ru" to ru, "zh" to zh)

        /** Şablonların dillere göre adları ve kısa açıklamaları. */
        val INFO: Map<String, Pair<Map<String, String>, Map<String, String>>> = V48TemplateInfo.INFO + V47TemplateInfo.INFO + mapOf(
            "notes_cornell" to (l("Cornell ders notu", "Cornell lecture notes", "ملاحظات كورنيل", "コーネル式ノート", "코넬 노트", "Конспект Корнелла", "康奈尔笔记") to
                l("Anahtar sorular, not alanı ve özet; Newton yasaları örneği.", "Key questions, notes and summary; Newton's laws example.", "أسئلة رئيسية وملاحظات وملخص؛ مثال قوانين نيوتن.", "キークエスチョン・ノート・まとめ（ニュートンの法則の例）。", "핵심 질문, 노트, 요약(뉴턴 법칙 예시).", "Ключевые вопросы, записи и итог; пример — законы Ньютона.", "关键问题、笔记和总结（牛顿定律示例）。")),
            "notes_math" to (l("Matematik çalışma kâğıdı", "Math worksheet", "ورقة عمل رياضيات", "数学ワークシート", "수학 학습지", "Рабочий лист по математике", "数学练习页") to
                l("Formüller, çözümlü örnek, grafik ve alıştırmalar.", "Formulas, a worked example, a graph and exercises.", "صيغ ومثال محلول ورسم بياني وتمارين.", "公式、解答例、グラフ、練習問題。", "공식, 풀이 예제, 그래프, 연습 문제.", "Формулы, разобранный пример, график и упражнения.", "公式、例题、图像和练习。")),
            "notes_lab" to (l("Deney raporu", "Lab report", "تقرير تجربة", "実験レポート", "실험 보고서", "Отчёт о лабораторной", "实验报告") to
                l("Amaç, malzemeler, yöntem, veri grafiği ve sonuç.", "Aim, materials, method, data graph and conclusion.", "الهدف والمواد والطريقة ورسم البيانات والنتيجة.", "目的・材料・方法・データのグラフ・結論。", "목적, 재료, 방법, 데이터 그래프, 결론.", "Цель, материалы, метод, график данных и вывод.", "目的、材料、方法、数据图和结论。")),
            "notes_meeting" to (l("Toplantı notu", "Meeting notes", "محضر اجتماع", "議事録", "회의록", "Протокол встречи", "会议记录") to
                l("Gündem, kararlar, takvim ve sorumlu-tarihli yapılacaklar.", "Agenda, decisions, timeline and to-dos with owners and dates.", "جدول الأعمال والقرارات والجدول الزمني والمهام مع المسؤول والتاريخ.", "議題、決定事項、スケジュール、担当と期限付きのToDo。", "안건, 결정 사항, 일정, 담당자와 기한이 있는 할 일.", "Повестка, решения, сроки и задачи с ответственными и датами.", "议程、决定、时间线及带负责人和日期的待办。")),
            "notes_mindmap" to (l("Zihin haritası", "Mind map", "خريطة ذهنية", "マインドマップ", "마인드맵", "Интеллект-карта", "思维导图") to
                l("Merkez konu ve dallar, tepkime, çizim ve anahtar kavramlar.", "Central topic with branches, a reaction, a drawing and key terms.", "موضوع مركزي وفروع ومعادلة ورسم ومفاهيم رئيسية.", "中心テーマと枝、反応式、図、キーワード。", "중심 주제와 가지, 반응식, 그림, 핵심 개념.", "Центральная тема с ветвями, реакция, рисунок и ключевые понятия.", "中心主题和分支、反应式、插图和关键概念。")),
        )
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "tr-w800dp-h1280dp-xhdpi")
class V46TemplateExport : TemplateExportBase() {
    // v47 çizgi roman şablonları V47TemplateExport.kt dosyasında (eski beş şablon kaldırıldı)

    // ================================================================ not alma şablonları

    /** Cornell ders notu: anahtar sorular sütunu, not alanı ve özet şeridi (Newton'un hareket yasaları). */
    @Test
    fun notesCornell() {
        open("Newton'un Hareket Yasaları", CanvasKind.NOTES, 1240, 1754, pages = 1, cover = false, background = BackgroundSpec(patternId = "note.ruled_college"))
        page(0)
        val (cue, notes, summary) = notesPanels("cornell").let { Triple(it[0], it[1], it[2]) }
        val pad = 22f
        val nx = notes.left + pad
        val nw = notes.width - 2 * pad
        val title = text("Newton'un Hareket Yasaları", nx, notes.top + pad, 46f, INK, TextFont.POPPINS, bold = true, width = nw)
        val sub = text("Fizik · Dinamik · Ders notu", nx, title.bottom() + 6f, 24f, MUTED, width = nw)
        val h1 = heading("1. Eylemsizlik yasası", nx, sub.bottom() + 34f)
        val t1 = text("Net kuvvet sıfırsa duran cisim durur, hareket eden cisim sabit hızla doğrusal hareketine devam eder.", nx, h1.bottom() + 10f, 26f, width = nw)
        val h2 = heading("2. Temel yasa", nx, t1.bottom() + 30f)
        val t2 = text("Net kuvvet, kütle ile ivmenin çarpımına eşittir. Kuvvet ile ivme aynı yöndedir.", nx, h2.bottom() + 10f, 26f, width = nw)
        val f = TutorialArt.formula("physics.newton2", nx, t2.bottom() + 16f, 44f, BLUE)
        val fBottom = f?.points?.maxOf { it.y } ?: (t2.bottom() + 60f)
        val fb = shape("lib.sci.phys.free_body", Rect(nx + nw * 0.08f, fBottom + 18f, nx + nw * 0.08f + 560f, fBottom + 18f + 430f),
            params = mapOf("m" to "4", "F" to "30", "alpha" to "30", "mu" to "0.2"))
        val fbBottom = fb?.points?.maxOf { it.y } ?: (fBottom + 450f)
        val h3 = heading("3. Etki–tepki yasası", nx, fbBottom + 26f)
        val t3 = text("Bir cisim ötekine kuvvet uygularsa öteki de ona eşit büyüklükte ve zıt yönde kuvvet uygular. Kuvvetler farklı cisimlere etki ettiği için birbirini yok etmez.", nx, h3.bottom() + 10f, 26f, width = nw)
        add(title, sub, h1, t1, h2, t2, f, fb, h3, t3)
        // Anahtar sorular: notlardaki konuların hizasında, el yazısı
        val cx = cue.left + 18f
        val cw = cue.width - 36f
        add(
            heading("Anahtar sorular", cx, cue.top + pad, size = 24f),
            text("Konu: Dinamik\nTarih: ____", cx, title.top + 70f, 26f, MUTED, TextFont.CAVEAT, width = cw),
            text("Cisim neden hareketini sürdürür?", cx, h1.top, 30f, RED, TextFont.CAVEAT, width = cw),
            text("F = m·a neyi anlatır? Birimler?", cx, h2.top, 30f, RED, TextFont.CAVEAT, width = cw),
            text("Sürtünme ivmeyi nasıl değiştirir?", cx, fBottom + 120f, 30f, RED, TextFont.CAVEAT, width = cw),
            text("Etki ve tepki birbirini yok eder mi?", cx, h3.top, 30f, RED, TextFont.CAVEAT, width = cw),
        )
        val sx = summary.left + pad
        val sh = heading("Özet", sx, summary.top + pad - 4f)
        add(
            sh,
            text("Kuvvet hareketi değil, hareketteki değişimi (ivmeyi) belirler: a = F_net / m. Kuvvetler her zaman çiftler hâlinde oluşur; serbest cisim diyagramında yalnız cisme etki edenler çizilir.", sx, sh.bottom() + 10f, 26f, width = summary.width - 2 * pad),
        )
        export("notes_cornell", CanvasKind.NOTES, landing = 0, coverPage = 0)
    }

    /** Matematik çalışma kâğıdı: formüller, çözümlü örnek, grafik ve alıştırmalar. */
    @Test
    fun notesMath() {
        open("İkinci Dereceden Denklemler", CanvasKind.NOTES, 1240, 1754, pages = 1, cover = false, background = BackgroundSpec(patternId = "note.grid5"))
        page(0)
        val (head, left, right) = notesPanels("header_two_cols").let { Triple(it[0], it[1], it[2]) }
        val pad = 22f
        val title = text("İkinci Dereceden Denklemler", head.left + pad, head.top + pad, 46f, INK, TextFont.POPPINS, bold = true, width = head.width - 2 * pad)
        val info = text("Çalışma kâğıdı   ·   Ad: ______________   ·   Tarih: ________", head.left + pad, title.bottom() + 10f, 26f, MUTED, width = head.width - 2 * pad)
        add(title, info, text("Hedef: ax² + bx + c = 0 denklemini çarpanlara ayırarak ve diskriminantla çözmek.", head.left + pad, info.bottom() + 12f, 26f, INK, width = head.width - 2 * pad))
        val lx = left.left + pad
        val lw = left.width - 2 * pad
        val h1 = heading("Formüller", lx, left.top + pad)
        val f1 = TutorialArt.formula("algebra.quadratic", lx, h1.bottom() + 18f, 38f, INK)
        val f1b = f1?.points?.maxOf { it.y } ?: (h1.bottom() + 70f)
        val f2 = TutorialArt.formula("algebra.discriminant", lx, f1b + 22f, 38f, BLUE)
        val f2b = f2?.points?.maxOf { it.y } ?: (f1b + 70f)
        val f3 = TutorialArt.formula("algebra.quadratic_formula_delta", lx, f2b + 22f, 38f, BLUE)
        val f3b = f3?.points?.maxOf { it.y } ?: (f2b + 90f)
        val rule = text("Δ > 0: iki farklı kök   ·   Δ = 0: çakışık kök   ·   Δ < 0: gerçek kök yok", lx, f3b + 16f, 24f, MUTED, width = lw)
        val h2 = heading("Çözümlü örnek", lx, rule.bottom() + 36f)
        val steps = text("x² − 4x + 3 = 0\na = 1,  b = −4,  c = 3\nΔ = b² − 4ac = 16 − 12 = 4\nx₁ = (4 + 2) / 2 = 3\nx₂ = (4 − 2) / 2 = 1", lx, h2.bottom() + 14f, 30f, INK, TextFont.CAVEAT, width = lw, paragraph = TextParagraph.NUMBERED)
        val ans = text("Ç = { 1, 3 }", lx, steps.bottom() + 18f, 34f, GREEN, TextFont.POPPINS, bold = true)
        add(h1, f1, f2, f3, rule, h2, steps, ans)
        val rx = right.left + pad
        val rw = right.width - 2 * pad
        val h3 = heading("Grafik", rx, right.top + pad)
        val g = graph("graph.FUNCTION", Rect(rx, h3.bottom() + 14f, rx + rw, h3.bottom() + 14f + rw)) {
            it.copy(formula = "x^2 - 4x + 3", xMin = -1f, xMax = 5f)
        }
        val gb = g?.points?.maxOf { it.y } ?: (h3.bottom() + rw)
        val cap = text("Kökler, parabolün x eksenini kestiği noktalardır: x = 1 ve x = 3. Tepe noktası (2, −1).", rx, gb + 14f, 24f, MUTED, width = rw)
        val h4 = heading("Alıştırmalar", rx, cap.bottom() + 34f)
        val ex = text("x² − 5x + 6 = 0\n2x² + 3x − 2 = 0\nx² + 6x + 9 = 0\nx² + x + 1 = 0  (Δ < 0 ise?)", rx, h4.bottom() + 14f, 28f, INK, width = rw, paragraph = TextParagraph.CHECKLIST)
        add(h3, g, cap, h4, ex)
        export("notes_math", CanvasKind.NOTES, landing = 0, coverPage = 0)
    }

    /** Deney raporu: amaç ve hipotez, malzemeler, yöntem, veriler (grafik), sonuç ve hata kaynakları. */
    @Test
    fun notesLab() {
        open("Deney Raporu: Basit Sarkaç", CanvasKind.NOTES, 1240, 1754, pages = 1, cover = false, background = BackgroundSpec(patternId = "note.dots"))
        page(0)
        val (head, left, right) = notesPanels("header_two_cols").let { Triple(it[0], it[1], it[2]) }
        val pad = 22f
        val hw = head.width - 2 * pad
        val title = text("Deney Raporu: Basit Sarkacın Periyodu", head.left + pad, head.top + pad, 40f, INK, TextFont.POPPINS, bold = true, width = hw)
        val aim = text("Amaç: Periyodun ip uzunluğuna bağlılığını ölçmek.   Hipotez: T, √L ile artar; kütleye bağlı değildir.", head.left + pad, title.bottom() + 10f, 24f, MUTED, width = hw)
        add(title, aim, text("Ad: ______________   ·   Sınıf: ______   ·   Tarih: ________", head.left + pad, aim.bottom() + 12f, 24f, INK, width = hw))
        val lx = left.left + pad
        val lw = left.width - 2 * pad
        val h1 = heading("Malzemeler", lx, left.top + pad)
        val pend = shape("lib.sci.phys.pendulum", Rect(lx + lw * 0.18f, h1.bottom() + 14f, lx + lw * 0.82f, h1.bottom() + 14f + lw * 0.64f))
        val pb = pend?.points?.maxOf { it.y } ?: (h1.bottom() + lw * 0.66f)
        val mat = text("Statif ve ip\n50 g ve 100 g kütle\nKronometre\nŞerit metre", lx, pb + 12f, 26f, INK, width = lw, paragraph = TextParagraph.BULLET)
        val h2 = heading("Yöntem", lx, mat.bottom() + 30f)
        val met = text("İpi 20 cm uzunluğa ayarla.\nKütleyi 10°'den küçük açıyla bırak.\n10 tam salınımın süresini ölç, 10'a böl.\nÖlçümü 20 cm artırarak 100 cm'ye kadar tekrarla.\nKütleyi değiştirip bir ölçümü yinele.", lx, h2.bottom() + 12f, 26f, INK, width = lw, paragraph = TextParagraph.NUMBERED)
        add(h1, pend, mat, h2, met)
        val rx = right.left + pad
        val rw = right.width - 2 * pad
        val h3 = heading("Veriler", rx, right.top + pad)
        val data = graph("graph.LINE", Rect(rx, h3.bottom() + 14f, rx + rw, h3.bottom() + 14f + rw * 0.78f)) {
            it.copy(
                values = listOf(0.90f, 1.27f, 1.55f, 1.79f, 2.01f),
                valueLabels = mapOf(0 to "20", 1 to "40", 2 to "60", 3 to "80", 4 to "100"),
                xLabel = "L (cm)", yLabel = "T (s)",
            )
        }
        val db = data?.points?.maxOf { it.y } ?: (h3.bottom() + rw * 0.8f)
        val f = TutorialArt.formula("physics.pendulum", rx, db + 18f, 40f, BLUE)
        val fb = f?.points?.maxOf { it.y } ?: (db + 70f)
        val h4 = heading("Sonuç", rx, fb + 28f)
        val res = text("Ölçülen periyotlar T = 2π√(L/g) ile uyumludur: uzunluk 4 katına çıkınca periyot 2 katına çıkar. Kütle değişince periyot değişmedi; hipotez doğrulandı.", rx, h4.bottom() + 12f, 25f, INK, width = rw)
        val h5 = heading("Hata kaynakları", rx, res.bottom() + 26f, size = 26f)
        val err = text("Kronometreyi başlatma ve durdurma gecikmesi\nBüyük açılarda yaklaşımın bozulması\nHava direnci", rx, h5.bottom() + 10f, 24f, MUTED, width = rw, paragraph = TextParagraph.BULLET)
        add(h3, data, f, h4, res, h5, err)
        export("notes_lab", CanvasKind.NOTES, landing = 0, coverPage = 0)
    }

    /** Toplantı notu: bilgiler, gündem, kararlar, zaman çizelgesi ve sorumlu-tarihli yapılacaklar. */
    @Test
    fun notesMeeting() {
        open("Toplantı Notu", CanvasKind.NOTES, 1240, 1754, pages = 1, cover = false, background = BackgroundSpec(patternId = "note.dots"))
        page(0)
        val (head, left, right) = notesPanels("header_two_cols").let { Triple(it[0], it[1], it[2]) }
        val pad = 22f
        val hw = head.width - 2 * pad
        val title = text("Haftalık Ürün Toplantısı", head.left + pad, head.top + pad, 46f, INK, TextFont.POPPINS, bold = true, width = hw)
        val info = text("Tarih: __.__.____   ·   Saat: 10.00–10.45   ·   Katılımcılar: Ayşe, Can, Ece, Mert", head.left + pad, title.bottom() + 10f, 25f, MUTED, width = hw)
        add(title, info, text("Amaç: Sürüm takvimini kesinleştirmek ve geri bildirimleri önceliklendirmek.", head.left + pad, info.bottom() + 12f, 25f, INK, width = hw))
        val lx = left.left + pad
        val lw = left.width - 2 * pad
        val h1 = heading("Gündem", lx, left.top + pad)
        val ag = text("Geçen haftanın değerlendirmesi\nYeni sürüm takvimi\nKullanıcı geri bildirimleri\nRiskler ve engeller", lx, h1.bottom() + 12f, 27f, INK, width = lw, paragraph = TextParagraph.NUMBERED)
        val h2 = heading("Notlar", lx, ag.bottom() + 30f)
        val nt = text("Geri bildirimlerin çoğu arama hızıyla ilgili.\nÖdeme ekranında iki hata bildirildi.\nTest ortamı perşembeye hazır olacak.", lx, h2.bottom() + 12f, 27f, INK, width = lw, paragraph = TextParagraph.BULLET)
        val h3 = heading("Kararlar", lx, nt.bottom() + 30f)
        val dc = text("Sürüm 20 Mart'ta mağazaya gönderilecek.\nArama iyileştirmesi bu sürüme alınacak.", lx, h3.bottom() + 12f, 27f, GREEN, width = lw, paragraph = TextParagraph.BULLET, bold = true)
        val h4 = heading("Sürüm takvimi", lx, dc.bottom() + 30f)
        val tl = TutorialArt.diagram(
            DiagramKind.TIMELINE, Rect(lx, h4.bottom() + 12f, lx + lw, h4.bottom() + 12f + 300f), ShapeStyle(INK, 2.5f), "ocean",
            listOf("Kod dondurma · 14 Mart", "Test · 15–18 Mart", "Mağaza · 20 Mart", "Yayın · 22 Mart"),
        )
        add(h1, ag, h2, nt, h3, dc, h4, tl)
        val rx = right.left + pad
        val rw = right.width - 2 * pad
        val h5 = heading("Yapılacaklar", rx, right.top + pad)
        val todo = text("Sürüm notlarını yaz — Ayşe · 14 Mart\nTest planını güncelle — Mert · 15 Mart\nÖdeme hatalarını düzelt — Can · 16 Mart\nAnket sonuçlarını özetle — Ece · 17 Mart", rx, h5.bottom() + 12f, 27f, INK, width = rw, paragraph = TextParagraph.CHECKLIST)
        val h6 = heading("Açık sorular", rx, todo.bottom() + 30f)
        val qs = text("Arama için ek sunucu gerekli mi?\nMağaza görselleri kimde?", rx, h6.bottom() + 12f, 27f, RED, width = rw, paragraph = TextParagraph.BULLET)
        val h7 = heading("Sonraki toplantı", rx, qs.bottom() + 30f)
        val nx = text("Pazartesi 10.00 · Konu: test sonuçları", rx, h7.bottom() + 12f, 27f, MUTED, width = rw)
        add(h5, todo, h6, qs, h7, nx)
        export("notes_meeting", CanvasKind.NOTES, landing = 0, coverPage = 0)
    }

    /** Zihin haritası: merkez konu ve dallar (fotosentez), tepkime, hücre çizimi ve anahtar kavramlar. */
    @Test
    fun notesMindMap() {
        open("Fotosentez", CanvasKind.NOTES, 1240, 1754, pages = 1, cover = false, background = BackgroundSpec(patternId = "note.dots"))
        page(0)
        val w = 1240f
        val m = 62f
        val title = text("Fotosentez · Zihin Haritası", m, 60f, 48f, INK, TextFont.POPPINS, bold = true, width = w - 2 * m)
        val sub = text("Biyoloji · Canlılarda enerji dönüşümleri", m, title.bottom() + 8f, 25f, MUTED, width = w - 2 * m)
        val map = TutorialArt.diagram(
            DiagramKind.MIND_MAP, Rect(m, sub.bottom() + 30f, w - m, sub.bottom() + 30f + 760f), ShapeStyle(INK, 2.5f), "pastel",
            listOf("Fotosentez", "Işık enerjisi → kimyasal enerji", "Gerekenler: CO₂, su, ışık", "Ürünler: glikoz ve O₂", "Evreler: ışık + Calvin", "Etkenler: ışık, sıcaklık", "Önemi: besin zinciri"),
        )
        val mb = map.points.maxOf { it.y }
        val h1 = heading("Tepkime", m, mb + 36f)
        val eq = text("6CO₂ + 6H₂O  →  C₆H₁₂O₆ + 6O₂", m, h1.bottom() + 14f, 38f, GREEN, TextFont.POPPINS, bold = true)
        val note = text("(ışık ve klorofil varlığında, kloroplastta)", m, eq.bottom() + 6f, 25f, MUTED, TextFont.CAVEAT)
        val cell = shape("lib.sci.bio.plant_cell", Rect(w * 0.56f, mb + 40f, w - m, mb + 40f + 400f))
        val h2 = heading("Anahtar kavramlar", m, note.bottom() + 36f)
        val keys = text("Klorofil: ışığı soğuran yeşil pigment\nKloroplast: fotosentezin yapıldığı organel\nStoma: gaz alışverişini sağlayan gözenek\nATP: hücrenin enerji taşıyıcısı", m, h2.bottom() + 12f, 26f, INK, width = w * 0.48f, paragraph = TextParagraph.BULLET)
        add(title, sub, map, h1, eq, note, cell, h2, keys)
        export("notes_mindmap", CanvasKind.NOTES, landing = 0, coverPage = 0)
    }
}
