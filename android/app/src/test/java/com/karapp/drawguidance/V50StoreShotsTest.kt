package com.karapp.drawguidance

import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.comicassets.ComicAssets
import com.karapp.drawguidance.data.comicassets.VariantGroup
import com.karapp.drawguidance.data.model.BalloonStyle
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.drawing.TailDirection
import com.karapp.drawguidance.ui.screens.canvasedit.state.BalloonTextOptions
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialScripts
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v50 mağaza görselleri: uygulama dili DG_LANG ortam değişkeninden (tr, en, ar, ja, ko, ru, zh). */
class StoreShotsApp : TestDrawGuidanceApp() {
    override val initialLanguage: String? get() = System.getenv("DG_LANG")?.takeIf { it.isNotBlank() } ?: "tr"
}

/**
 * v50 Google Play ve App Store tanıtım görselleri için ham ekran görüntüleri (yalnız DG_SHOTS verilince): bütün
 * modlar ve "Resimden çizim" eğitimi (vektörleştirme, kalıpla çizme), her uygulama dilinde. Telefon (w393dp-h852dp,
 * xxhdpi: 1179×2556) ve tablet (w834dp-h1194dp, xhdpi: 1668×2388) çizilir; tanıtım çerçeveleri ve yazıları
 * tools/store-shots/compose.py ile eklenir. İçerik uygulamanın kendi hazır karakter, mekân ve nesneleriyle kurulur.
 */
abstract class V50StoreShotsBase : V32EditorBase() {
    protected abstract val device: String
    private val lang: String get() = System.getenv("DG_LANG")?.takeIf { it.isNotBlank() } ?: "tr"

    private fun t(key: String): String = (TEXTS[key] ?: error(key))[LANGS.indexOf(lang).coerceAtLeast(0)]

    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun shotName(name: String) = "$lang-$device-$name"

    private fun open(name: String, kind: CanvasKind, width: Int = 1000, height: Int = 1414) {
        settleFor(800)
        runBlocking { repo().createCanvas(name, width, height, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun images() = vm().canvas.value!!.strokes.filter { it.brushType == BrushType.IMAGE }

    private fun strokeBounds(id: String): Rect {
        val s = vm().canvas.value!!.strokes.first { it.id == id }
        return Rect(s.points.minOf { it.x }, s.points.minOf { it.y }, s.points.maxOf { it.x }, s.points.maxOf { it.y })
    }

    private fun character(id: String, group: VariantGroup, panel: Rect, x: Float, height: Float) {
        val c = ComicAssets.catalog.characters.first { it.id == id }
        val variant = c.variants(group).firstOrNull() ?: c.cover
        val before = images().size
        compose.runOnIdle { vm().addHere("characters", Offset(x, panel.center.y)); vm().insertCharacter(variant) }
        waitFor(20_000) { images().size == before + 1 }
        fit(images().last().id, panel, x, height)
    }

    private fun fit(id: String, panel: Rect, x: Float, height: Float) {
        compose.runOnIdle { val b0 = strokeBounds(id); vm().selectOnlyAt(b0.center.x, b0.center.y, 4f) }
        compose.runOnIdle { val b0 = strokeBounds(id); val f = panel.height * height / b0.height; vm().scaleSelected(f, f, b0.center) }
        compose.runOnIdle { val b = strokeBounds(id); vm().moveSelectionTo(x - b.width / 2f, panel.bottom - b.height - panel.height * 0.01f) }
        compose.waitForIdle()
    }

    private fun backdrop(id: String, leaf: Int?) {
        val b = ComicAssets.catalog.backgrounds.first { it.id == id }
        val before = images().size
        compose.runOnIdle { vm().insertLocationBackground(b, leaf) }
        waitFor(20_000) { images().size == before + 1 }
    }

    private fun balloon(at: Offset, text: String, style: BalloonStyle = BalloonStyle.SPEECH, tail: TailDirection = TailDirection.BOTTOM_LEFT) {
        val before = vm().canvas.value!!.strokes.size
        compose.runOnIdle { vm().addHere("balloon", at); vm().insertBalloon(style, 1, BalloonTextOptions(content = text, tail = tail)) }
        waitFor { vm().canvas.value!!.strokes.size > before }
    }

    private fun clean() {
        compose.runOnIdle { vm().clearSelection() }
        if (vm().mode.value.kind != com.karapp.drawguidance.ui.screens.canvasedit.ModeKind.BASE) compose.runOnIdle { vm().finishAction() }
        compose.runOnIdle { vm().chooseMode(com.karapp.drawguidance.ui.screens.canvasedit.EditMode.DRAW) }
        settleFor(1500)
    }

    /**
     * Eğitim grubunu oynatır. [shotAt] adımlarında (0'dan) adımın çizimi bitince açıklama kutusuyla görüntü alınır.
     * Son adımın çizimi döner.
     */
    private fun play(kind: CanvasKind, groupId: String, shotAt: Map<Int, String> = emptyMap(), fast: Boolean = true): CanvasUi {
        val group = TutorialScripts.groups(kind).first { it.id == groupId }
        compose.runOnIdle { vm().tutorial.fast = fast; vm().tutorial.start(group) }
        val kids = kind == CanvasKind.KIDS
        val shown = { if (kids) exists("kids_tutorial") else calloutShown() }
        waitFor { shown() }
        var snapshot: CanvasUi? = null
        for (i in 1..group.steps.size) {
            runUntil(120_000) { vm().tutorial.ui.value?.index == i - 1 }
            awaitDemo(120_000)
            advanceMain(300)
            shotAt[i - 1]?.let { settleFor(1200); shot(shotName(it)) }
            if (i == group.steps.size) snapshot = live()
            if (kids) {
                compose.onNodeWithTag("mascot_next", useUnmergedTree = true).performClick()
                compose.waitForIdle()
            } else next()
        }
        waitFor { !shown() }
        return snapshot!!
    }

    private fun tutorialShot(kind: CanvasKind, nameKey: String, groupId: String, shot: String) {
        assumeTrue(System.getenv("DG_SHOTS") != null)
        val name = t(nameKey)
        open(name, kind)
        val snap = play(kind, groupId)
        SystemBack.press()
        waitFor { !exists("drawing_canvas") }
        runBlocking {
            repo().saveDocument(snap.localId, snap.pages, snap.width, snap.height, true, snap.backgroundColorHex, snap.currentPage, snap.layers)
            repo().saveBackgroundSpec(snap.localId, snap.background, snap.pageBackgrounds)
        }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.strokes?.isNotEmpty() == true }
        settleFor(1500)
        shot(shotName(shot))
    }

    @Test
    fun comicPage() {
        assumeTrue(System.getenv("DG_SHOTS") != null)
        open(t("comic_name"), CanvasKind.COMIC)
        compose.runOnIdle { vm().insertTemplate("three_big") }
        waitFor { vm().comicPanelRects().size == 3 }
        val panels = vm().comicPanelRects().entries.sortedWith(compareBy({ it.value.top }, { it.value.left }))
        val (top, left, right) = Triple(panels[0], panels[1], panels[2])
        backdrop("gunbatimi_cadde", top.key)
        backdrop("neon_cadde", left.key)
        backdrop("cati_teras", right.key)
        val tp = top.value
        character("kilicci_doruk", VariantGroup.POSE, tp, tp.left + tp.width * 0.28f, 0.86f)
        character("buyucu_leyla", VariantGroup.POSE, tp, tp.left + tp.width * 0.72f, 0.82f)
        character("dedektif_selin", VariantGroup.EXPRESSION, left.value, left.value.center.x, 0.78f)
        character("hacker_ozan", VariantGroup.EXPRESSION, right.value, right.value.center.x, 0.78f)
        balloon(Offset(tp.left + tp.width * 0.30f, tp.top + tp.height * 0.13f), t("b1"), tail = TailDirection.BOTTOM_RIGHT)
        balloon(Offset(tp.left + tp.width * 0.74f, tp.top + tp.height * 0.16f), t("b2"))
        balloon(Offset(left.value.center.x, left.value.top + left.value.height * 0.15f), t("b3"), BalloonStyle.THOUGHT)
        balloon(Offset(right.value.center.x, right.value.top + right.value.height * 0.15f), t("b4"), tail = TailDirection.BOTTOM_RIGHT)
        compose.runOnIdle { vm().addHere("sfx", Offset(tp.left + tp.width * 0.5f, tp.top + tp.height * 0.45f)); vm().insertSfx(com.karapp.drawguidance.content.comic.ComicSfx.presets.first()) }
        clean()
        shot(shotName("01-cizgi-roman"))
    }

    @Test
    fun artScene() {
        assumeTrue(System.getenv("DG_SHOTS") != null)
        open(t("art_name"), CanvasKind.ART)
        backdrop("tropik_sahil", null)
        character("kasif_cem", VariantGroup.POSE, Rect(0f, 0f, 1000f, 1414f), 640f, 0.56f)
        clean()
        shot(shotName("02-resim"))
    }

    @Test fun notes() = tutorialShot(CanvasKind.NOTES, "notes_name", "notes_formula", "03-not")
    @Test fun kids() = tutorialShot(CanvasKind.KIDS, "kids_name", "kids_intro", "04-cocuk")
    @Test fun simpleArt() = tutorialShot(CanvasKind.SIMPLE_ART, "simple_name", "simple_art_intro", "05-basit-resim")
    @Test fun simpleNotes() = tutorialShot(CanvasKind.SIMPLE_NOTES, "simple_notes_name", "simple_notes_intro", "07-basit-not")

    @Test
    fun model3d() {
        assumeTrue(System.getenv("DG_SHOTS") != null)
        settleFor(800)
        val k = GlobalContext.get()
        val vm = com.karapp.drawguidance.ui.screens.model3d.Model3DEditorViewModel(k.get(), k.get(), k.get(), null, null, null, k.get(), images = k.get())
        compose.runOnUiThread {
            compose.activity.setContent {
                com.karapp.drawguidance.ui.theme.DrawGuidanceTheme(darkTheme = false) {
                    com.karapp.drawguidance.ui.screens.model3d.Model3DEditorScreen(null, null, null, onBack = {}, vm = vm)
                }
            }
        }
        waitFor { exists("model3d_editor") && vm.currentView() != null }
        compose.runOnIdle { vm.addPart(com.karapp.drawguidance.content.model3d.PrimitiveKind.SPHERE) }
        settleFor(600)
        compose.runOnIdle { vm.addPart(com.karapp.drawguidance.content.model3d.PrimitiveKind.CYLINDER) }
        waitFor(90_000) { vm.busy.value == null }
        settleFor(1200)
        shot(shotName("06-3b"))
    }

    /** v50 "Resimden çizim" eğitimi (resim modu): vektörleştirme ve kalıpla çizme adımları, sonra bitmiş sayfa. */
    @Test
    fun traceTutorial() {
        assumeTrue(System.getenv("DG_SHOTS") != null)
        val name = t("trace_name")
        open(name, CanvasKind.ART)
        val snap = play(CanvasKind.ART, "v50_trace_art", mapOf(4 to "09-kalipla-ciz", 5 to "10-kutuphane-kalibi"))
        SystemBack.press()
        waitFor { !exists("drawing_canvas") }
        runBlocking { repo().saveDocument(snap.localId, snap.pages, snap.width, snap.height, true, snap.backgroundColorHex, snap.currentPage, snap.layers) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.strokes?.isNotEmpty() == true }
        settleFor(1500)
        shot(shotName("11-resimden-cizim"))
    }

    /**
     * Vektörleştirme (açıklama kutusu olmadan): demlik resmi eklenir, otomatik vektörleştirilir, resim soluklaşır ve
     * Resmi düzenle satırı (vektörleştirme türleri) açık kalır.
     */
    @Test
    fun vectorizeScene() {
        assumeTrue(System.getenv("DG_SHOTS") != null)
        open(t("trace_name"), CanvasKind.ART)
        var done = false
        compose.runOnIdle {
            val vm = vm()
            val host = vm.tutorialHost
            kotlinx.coroutines.MainScope().launch {
                val c = host.canvas()!!
                val r = Rect(c.width * .08f, c.height * .06f, c.width * .92f, c.height * .62f)
                val img = host.assetImage(com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialV50.IMAGE, r, fill = false)!!
                host.commitElements(listOf(img))
                val id = host.canvas()!!.strokes.last { it.brushType == BrushType.IMAGE }.id
                host.vectorizeImage(id, com.karapp.drawguidance.content.trace.EdgeMode.AUTO, c.width * 0.004f, keepImage = true)
                host.mutate { list -> list.map { if (it.id == id) it.copy(opacity = 0.3f) else it } }
                host.editImage(id)
                done = true
            }
        }
        runUntil(60_000) { done }
        settleFor(1500)
        shot(shotName("08-vektorlestir"))
    }

    /** Basit resim ve çocuk modunda aynı eğitim (yalnız denetim görüntüleri). */
    @Test
    fun traceTutorialSimple() {
        assumeTrue(System.getenv("DG_SHOTS") != null && System.getenv("DG_ALL") != null)
        open(t("trace_name"), CanvasKind.SIMPLE_ART)
        play(CanvasKind.SIMPLE_ART, "v50_trace_simple_art", mapOf(1 to "x-basit-vektor", 4 to "x-basit-kalip"))
    }

    @Test
    fun traceTutorialKids() {
        assumeTrue(System.getenv("DG_SHOTS") != null && System.getenv("DG_ALL") != null)
        open(t("kids_name"), CanvasKind.KIDS)
        play(CanvasKind.KIDS, "v50_trace_kids", mapOf(1 to "x-cocuk-kalip"))
    }

    companion object {
        val LANGS = listOf("tr", "en", "ar", "ja", "ko", "ru", "zh")
        val TEXTS: Map<String, List<String>> = mapOf(
            "comic_name" to listOf("Gece Devriyesi", "Night Patrol", "دورية الليل", "夜のパトロール", "야간 순찰", "Ночной патруль", "夜间巡逻"),
            "art_name" to listOf("Sahil Macerası", "Beach Adventure", "مغامرة الشاطئ", "海辺の冒険", "해변 모험", "Приключение на пляже", "海滩探险"),
            "notes_name" to listOf("Fizik notu", "Physics notes", "ملاحظات الفيزياء", "物理ノート", "물리 노트", "Конспект по физике", "物理笔记"),
            "kids_name" to listOf("Boyacık", "Painty", "رسّام", "ぬりえ", "색칠놀이", "Раскраска", "小画家"),
            "simple_name" to listOf("Ev", "My house", "البيت", "おうち", "우리 집", "Дом", "我的家"),
            "simple_notes_name" to listOf("Ders notu", "Class notes", "ملاحظات الدرس", "授業ノート", "수업 노트", "Заметки урока", "课堂笔记"),
            "trace_name" to listOf("Demlik", "Teapot", "إبريق الشاي", "ティーポット", "찻주전자", "Чайник", "茶壶"),
            "b1" to listOf("Şehir bu gece bizim!", "The city is ours tonight!", "المدينة لنا الليلة!", "今夜、街は俺たちのものだ！", "오늘 밤 도시는 우리 거야!", "Сегодня город наш!", "今晚这座城市属于我们！"),
            "b2" to listOf("Önce planı dinle.", "Hear the plan first.", "استمع إلى الخطة أولًا.", "まず作戦を聞いて。", "먼저 계획부터 들어.", "Сначала выслушай план.", "先听听计划。"),
            "b3" to listOf("Bir ipucu buldum…", "I found a clue…", "وجدتُ دليلًا…", "手がかりを見つけた…", "단서를 찾았어…", "Я нашла улику…", "我找到线索了……"),
            "b4" to listOf("Sistem açıldı!", "System unlocked!", "تم فتح النظام!", "システム解除！", "시스템 열렸다!", "Система взломана!", "系统已解锁！"),
        )
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = StoreShotsApp::class, qualifiers = "w393dp-h852dp-xxhdpi")
class V50StorePhoneShotsTest : V50StoreShotsBase() { override val device = "phone" }

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = StoreShotsApp::class, qualifiers = "w834dp-h1194dp-xhdpi")
class V50StoreTabletShotsTest : V50StoreShotsBase() { override val device = "tablet" }
