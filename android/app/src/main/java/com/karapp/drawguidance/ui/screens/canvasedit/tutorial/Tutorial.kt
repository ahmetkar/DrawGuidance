package com.karapp.drawguidance.ui.screens.canvasedit.tutorial

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.GradientSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.settings.TutorialPrefs
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.state.EditorStore
import com.karapp.drawguidance.ui.screens.canvasedit.state.ShapeFix
import com.karapp.drawguidance.ui.screens.canvasedit.state.ShapeSuggestion
import com.karapp.drawguidance.ui.screens.canvasedit.state.TextSuggestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v32 eğitim modunda tanıtılan araçların ekrandaki yerleri (araç düğmelerine `Modifier.tutorialAnchor` ile verilir;
 * açıklama kutusu bu aracın hemen altında açılır).
 */
object TutorialAnchor {
    const val UNDO = "undo"
    const val REDO = "redo"
    const val BRUSH = "brush"
    const val ERASER = "eraser"
    const val PEN = "pen"
    const val FILL = "fill"
    const val BACKGROUND = "background"
    const val ELEMENTS = "elements"
    const val BRUSH_SETTINGS = "brush_settings"
    const val LAYERS = "layers"
    /** v47 Düzenle satırındaki "Taşı ve genişlet" düğmesi. */
    const val MOVE_EXPAND = "move_expand"
    const val MORE = "more_menu"
    const val CANVAS = "canvas"
    /** Şekil önerisi balonu (v38 yazı ve çizgi balonlarının yerleri ayrı). */
    const val SUGGEST = "suggest_balloon"
    const val SUGGEST_TEXT = "suggest_text_balloon"
    const val SUGGEST_LINE = "suggest_line_balloon"
    /** Öneri balonlarının yerleri (eğitimde balonun etrafı açık kalır). */
    val SUGGESTS = setOf(SUGGEST, SUGGEST_TEXT, SUGGEST_LINE)
    const val PAGES = "pages"
    fun mode(mode: EditMode) = "mode_${mode.name}"
    fun add(id: String) = "add_$id"
    /** v46 seçili elemanın köşesindeki seçim menüsünün düğmesi (Taşı, Ölçekle, Kopyala, Sil, Düzenle). */
    fun selection(id: String) = "selection_$id"
    /** v37 alttaki mod çubuğu (çocuk eğitiminde maskot bunun üstünde durur). */
    const val MODE_BAR = "mode_bar"
    /** v37 basit çizimin tek menüsündeki araç (çocuk eğitimi). */
    fun simple(id: String) = "simple_$id"
    /** v40 başlıktaki "Resim olarak kaydet" düğmesi (kaydetme penceresi: CMYK, konum seç). */
    const val SAVE = "save_image"
    /** v40 cetvel/kalıp çubuğu ve düğmeleri (cetvel açıkken). */
    const val RULER_BAR = "ruler_bar"
    const val RULER_TYPE = "ruler_type"
    const val RULER_UNIT = "ruler_unit"
    const val RULER_SCALE = "ruler_scale"
    const val RULER_FINISH = "ruler_finish"
    const val TEMPLATE_LIBRARY = "template_library"
    /** v42 gelişmiş modlarda Çiz satırındaki Cetvel ve Şekil kalıbı düğmeleri. */
    const val RULER = "tool_ruler"
    const val TEMPLATE = "tool_template"
}

/**
 * Eğitimin bir adımı: [anchor] aracın altında [title] ve [body] açıklaması görünür, [demo] çizim alanında o araçla
 * profesyonel bir çizimin bir bölümünü adım adım çizer. [mode] adım başında açılır (araç üst menüde görünsün diye).
 * [ai] adımlarında yapay zekâ önerileri (tuvalin ayarından bağımsız) çalışır; diğer adımlarda öneri balonu çıkmaz.
 */
class TutorialStep(
    val anchor: String,
    @StringRes val title: Int,
    @StringRes val body: Int,
    val ai: Boolean = false,
    val mode: EditMode? = null,
    /** v40 önceki adımda açılan cetvel/kalıp bu adımda da açık kalır (öteki adımlar başlarken kapanır). */
    val guide: Boolean = false,
    val demo: (suspend TutorialDemo.() -> Unit)? = null,
)

/** Kullanıcının seçtiği araç grubunun eğitimi (ör. Çizim araçları, Yapay zekâ önerileri). */
class TutorialGroup(
    val id: String,
    val kind: CanvasKind,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val icon: ImageVector,
    val ai: Boolean,
    val steps: List<TutorialStep>,
)

/** Açık eğitim adımı. [focus] vurgulanan araç (yapay zekâ adımında öneri balonuna geçer); [hint] o anki ek açıklama. */
data class TutorialUi(
    val group: TutorialGroup,
    val index: Int,
    val focus: String,
    val running: Boolean,
    @StringRes val hint: Int? = null,
) {
    val step: TutorialStep get() = group.steps[index]
    val last: Boolean get() = index == group.steps.lastIndex
    val count: Int get() = group.steps.size
}

/**
 * Çizim alanında gösterilen, henüz eklenmemiş çizim (çizilmekte olan çizgi, sürüklenen şekil) ve "parmak". Parmak tuvalde
 * ([pointer], tuval koordinatı) ya da bir aracın üstünde ([pointerAnchor]) durur.
 */
data class TutorialLive(
    val strokes: List<Stroke> = emptyList(),
    val pointer: Offset? = null,
    val pointerAnchor: String? = null,
    val pressed: Boolean = false,
    /** v40 basılı tutma halkası (0..1; 5 sn basılı tutunca Taşı). */
    val hold: Float? = null,
)

/** Eğitimin düzenleyiciden istedikleri (görünümün modeli uygular; eğitim kayıt dışı "kum havuzunda" çalışır). */
interface TutorialHost {
    val scope: CoroutineScope
    val shapeSuggestion: StateFlow<ShapeSuggestion?>
    val textSuggestion: StateFlow<TextSuggestion?>
    /** v38 çizgi önerisi. */
    val lineSuggestion: StateFlow<com.karapp.drawguidance.ui.screens.canvasedit.state.LineSuggestion?>
    fun canvas(): CanvasUi?
    /** Fırça çizgisi (kullanıcının fırça ayarları değişmez); [suggest] ise yapay zekâ önerileri denenir. */
    fun commitStroke(stroke: Stroke, suggest: Boolean)
    /** Hazır elemanlar (şekil, yazı, formül, diyagram, balon) eklenir. */
    fun commitElements(strokes: List<Stroke>)
    /** Çizimler değiştirilir (tek geri alma adımı). */
    fun mutate(block: (List<Stroke>) -> List<Stroke>)
    fun setMode(mode: EditMode)
    fun select(ids: Set<String>)
    fun moveSelected(dx: Float, dy: Float)
    fun scaleSelected(factor: Float)
    fun undo()
    fun redo()
    /** Kâğıdın gradyanı (kaydedilmez; eğitim bitince eski kâğıt döner). */
    fun setPaperGradient(gradient: GradientSpec?)
    fun balloon(style: com.karapp.drawguidance.data.model.BalloonStyle, speakers: Int, center: Offset, text: String): Stroke?
    fun sfx(preset: com.karapp.drawguidance.content.comic.SfxPreset, center: Offset, size: Float): Stroke?
    fun comicLines(kind: com.karapp.drawguidance.content.comic.ComicLinesKind, density: Float, area: Rect, ink: String): List<Stroke>
    fun acceptTemplate(position: Int)
    /** v38 çizgi önerisini uygular (dikkate alınan bütün çizgiler, tek geri alma adımı). */
    fun acceptLines()
    fun acceptText(text: String)
    fun dismissSuggestions()
    /** Çizimin düzgün geometrisi (öneri gelmezse yedek). */
    fun fit(stroke: Stroke): ShapeFix?
    fun closePanel()
    /** Eğitim bitirildi (bildirim). */
    fun finished(group: TutorialGroup) {}
    /** v37 çocuk eğitiminde kova imleci ([point] tuval noktası, null gizler; [hold] basılı tutma sayacı, -1 yok). */
    fun paintCursor(point: Offset?, hold: Int, color: String?) {}
    /** v37 çocuk eğitiminde boyama: dokunulan alan ya da ([whole]) bütün şekil [color] ile. Boyandıysa true. */
    fun paint(point: Offset, color: String, whole: Boolean): Boolean = false
    /** v40 cetvel ve şekil kalıbı (eğitimde gösterilir; belgeye girmez, eğitim bitince kapanır). */
    val guides: com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerController? get() = null
    /** v40 sayfa numarası, üst ve alt bilgi (kaydedilmez; eğitim bitince eski ayarlar döner). */
    fun setPageSettings(settings: com.karapp.drawguidance.data.model.PageSettings) {}
    /**
     * v46 hazır resim (çizgi roman mekânı ya da karakteri; eklenmez): [fill] ise [target]'ı doldurur, değilse içine
     * sığar ve alt kenara basar. Okunamazsa null.
     */
    suspend fun assetImage(path: String, target: Rect, fill: Boolean): Stroke? = null
}

/**
 * v32 eğitim modu: araç grubunun adımlarını oynatır. Eğitim [EditorStore.beginSandbox] ile kayıt dışı çalışır — boş
 * bir sayfada gösterilir, hiçbir şey kaydedilmez, kullanıcının geri alma geçmişine girmez; iptal edilince ya da
 * bitince tuval eğitimden önceki haline döner. "Sonraki adım"a çizim bitmeden basılırsa adımın çizimi hemen tamamlanır.
 */
class TutorialController(
    private val host: TutorialHost,
    private val store: EditorStore,
    private val prefs: TutorialPrefs?,
) {
    private val _ui = MutableStateFlow<TutorialUi?>(null)
    val ui: StateFlow<TutorialUi?> = _ui.asStateFlow()

    private val _live = MutableStateFlow(TutorialLive())
    val live: StateFlow<TutorialLive> = _live.asStateFlow()

    private val _offer = MutableStateFlow(false)
    /** Araç grubu seçme penceresi açık mı (ilk girişte ya da ⋮ menüsünden). */
    val offer: StateFlow<Boolean> = _offer.asStateFlow()

    val completed: StateFlow<Set<String>> = prefs?.completed ?: MutableStateFlow(emptySet())

    val active: Boolean get() = _ui.value != null

    /** Yapay zekâ adımı sürüyor: öneriler tuvalin ve ayarların durumundan bağımsız açık. */
    val aiStep: Boolean get() = _ui.value?.step?.ai == true

    private var job: Job? = null
    @Volatile internal var fast = false
    private var stepStart: CanvasUi? = null

    fun groups(kind: CanvasKind): List<TutorialGroup> = TutorialScripts.groups(kind)

    /** Tuval açıldı: bu türde ilk girişse eğitim önerilir. */
    fun maybeOffer(kind: CanvasKind) {
        val p = prefs ?: return
        if (!p.autoOffer || groups(kind).isEmpty()) return
        if (p.offered(kind.name)) {
            // v40 eski eğitimi bitirmiş kullanıcıya bu sürümün yenilikleri bir kez gösterilir
            if (p.version(kind.name) >= TutorialScripts.VERSION) return
            p.markVersion(kind.name, TutorialScripts.VERSION)
            val fresh = TutorialScripts.whatsNew(kind) ?: return
            val done = p.completed.value
            if (groups(kind).any { it.id != fresh.id && it.id in done } && fresh.id !in done) start(fresh)
            return
        }
        p.markOffered(kind.name)
        p.markVersion(kind.name, TutorialScripts.VERSION)
        // v37 çocuk tuvalinde seçim penceresi yok: maskot tanıtımı hemen başlatır
        if (kind == CanvasKind.KIDS) start(groups(kind).first()) else _offer.value = true
    }

    fun openOffer() { if (!active) _offer.value = true }
    fun closeOffer() { _offer.value = false }

    /** v40 eğitimden önceki cetvel ayarları (eğitim türü, birimi ve ölçeği değiştirir; bitince geri döner). */
    private var guideSettings: com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerSettings? = null

    fun start(group: TutorialGroup) {
        _offer.value = false
        if (active) stop()
        guideSettings = host.guides?.let { g -> g.finish(); g.settings.value }
        host.closePanel()
        host.dismissSuggestions()
        store.beginSandbox()
        host.setMode(EditMode.DRAW)
        run(group, 0)
    }

    private fun run(group: TutorialGroup, index: Int) {
        val step = group.steps[index]
        fast = false
        host.dismissSuggestions()
        // v40 cetvel/kalıp yalnız onu kullanan adımlarda açık kalır
        if (!step.guide) closeGuides()
        host.setMode(step.mode ?: EditMode.DRAW)
        stepStart = store.sandboxState()
        _live.value = TutorialLive()
        _ui.value = TutorialUi(group, index, step.anchor, running = step.demo != null)
        val demo = step.demo ?: return
        job = host.scope.launch {
            try {
                pause(700)
                TutorialDemo(this@TutorialController, host).demo()
            } finally {
                _live.value = TutorialLive()
                _ui.update { u -> if (u != null && u.group === group && u.index == index) u.copy(running = false, focus = step.anchor, hint = null) else u }
            }
        }
    }

    /** Sonraki adım (son adımda eğitim biter). Çizim sürüyorsa önce hemen tamamlanır. */
    fun next() {
        val u = _ui.value ?: return
        if (advancing) return
        advancing = true
        host.scope.launch {
            try {
                completeCurrent()
                val now = _ui.value
                if (now == null || now.group !== u.group || now.index != u.index) return@launch
                if (u.last) finish() else run(u.group, u.index + 1)
            } finally {
                advancing = false
            }
        }
    }

    /** "Sonraki adım"a art arda basılınca adım atlanmasın. */
    private var advancing = false

    /** Adımın çizimini baştan gösterir. */
    fun replay() {
        val u = _ui.value ?: return
        host.scope.launch {
            job?.cancelAndJoin()
            job = null
            host.dismissSuggestions()
            stepStart?.let(store::restoreSandboxState)
            run(u.group, u.index)
        }
    }

    /** Eğitimi iptal et: tuval eğitimden önceki haline döner. */
    fun cancel() {
        job?.cancel()
        job = null
        stop()
    }

    /** Eğitimi bitir: grup tamamlandı sayılır, tuval eğitimden önceki haline döner. */
    fun finish() {
        val u = _ui.value ?: return
        job?.cancel()
        job = null
        prefs?.markCompleted(u.group.id)
        stop()
        host.finished(u.group)
    }

    private suspend fun completeCurrent() {
        val j = job ?: return
        if (j.isActive) {
            fast = true
            j.join()
        }
        job = null
    }

    private fun closeGuides() {
        val g = host.guides ?: return
        g.libraryOpen.value = false
        if (g.active) g.finish()
    }

    private fun stop() {
        _ui.value = null
        _live.value = TutorialLive()
        closeGuides()
        guideSettings?.let { saved ->
            val g = host.guides
            if (g != null && g.settings.value != saved) {
                g.setType(saved.type); g.setUnit(saved.unit); g.setScale(saved.scale)
            }
        }
        guideSettings = null
        host.dismissSuggestions()
        store.endSandbox()
        host.setMode(EditMode.DRAW)
        fast = false
    }

    // --- Oynatıcının kullandıkları ---

    internal suspend fun pause(ms: Long) {
        if (!fast && ms > 0) delay(ms)
    }

    internal fun setLive(live: TutorialLive) { _live.value = live }

    internal fun focus(anchor: String, @StringRes hint: Int?) {
        _ui.update { it?.copy(focus = anchor, hint = hint) }
    }
}

/**
 * Bir adımın çizimini oynatan yardımcılar: çizgiler parmakla çiziliyormuş gibi yavaşça belirir (basınçla incelip
 * kalınlaşarak), şekiller sürüklenerek büyür, öneri balonuna dokunulur. "Sonraki adım"a basılınca ([fast]) her şey
 * beklemeden tamamlanır.
 */
class TutorialDemo internal constructor(private val c: TutorialController, val host: TutorialHost) {
    private val canvas: CanvasUi get() = host.canvas() ?: error("tuval yok")
    val w: Float get() = canvas.width.toFloat()
    val h: Float get() = canvas.height.toFloat()
    /** Kısa kenar (kalınlık ve boyutlar buna göre). */
    val u: Float get() = minOf(w, h)
    val kind: CanvasKind get() = canvas.kind
    val fast: Boolean get() = c.fast

    /** Kâğıtta oransal nokta (0..1). */
    fun p(fx: Float, fy: Float) = Offset(w * fx, h * fy)

    suspend fun pause(ms: Long) = c.pause(ms)

    /** v40 parmak ve canlı çizim katmanı (cetvel ve basılı tutma gösterimleri). */
    fun live(live: TutorialLive) = c.setLive(live)

    /**
     * v40 parmak [at] noktasında basılı kalır, çevresindeki halka dolar (5 sn basılı tutma; eğitimde kısaltılmış
     * [ms] sürede).
     */
    suspend fun hold(at: Offset, ms: Long = 1_600) {
        if (fast) return
        val frames = (ms / FRAME_MS).toInt().coerceAtLeast(2)
        for (f in 0..frames) {
            if (fast) break
            c.setLive(TutorialLive(pointer = at, pressed = true, hold = f / frames.toFloat()))
            delay(FRAME_MS)
        }
    }

    fun focus(anchor: String, @StringRes hint: Int? = null) = c.focus(anchor, hint)

    fun mode(mode: EditMode) = host.setMode(mode)

    /**
     * Fırça çizgisi: [path] boyunca parmak gezinir, çizgi belirir; bitince tuvale eklenir. Basınç uçlarda azalır.
     * [suggest] ise kullanıcının çizimi gibi yapay zekâ önerileri denenir. Eklenen çizgi döner.
     */
    suspend fun stroke(
        path: List<Offset>,
        brush: BrushType,
        color: String,
        size: Float,
        opacity: Float = 1f,
        suggest: Boolean = false,
        speed: Float = 1f,
        taper: Boolean = true,
    ): Stroke {
        val pts = resample(path, (u * 0.006f).coerceAtLeast(1.5f))
        val total = pts.size
        val points = pts.mapIndexed { i, o ->
            val t = if (total <= 1) 0.5f else i / (total - 1f)
            val pressure = if (taper && brush != BrushType.ERASER) (0.45f + 0.55f * sqrt(sin(PI.toFloat() * t).coerceAtLeast(0f))).coerceIn(0.35f, 1f) else 1f
            StrokePoint(o.x, o.y, pressure)
        }
        val stroke = Stroke(
            id = UUID.randomUUID().toString(), points = points, colorHex = color, brushType = brush, sizePx = size,
            opacity = opacity, order = 0,
        )
        if (!fast && points.size > 1) {
            val length = length(path)
            val duration = (length / (u * 1.15f * speed) * 1000f).toLong().coerceIn(220L, 1600L)
            val frames = (duration / FRAME_MS).toInt().coerceAtLeast(2)
            for (f in 1..frames) {
                if (fast) break
                val n = (points.size * f / frames).coerceIn(1, points.size)
                c.setLive(TutorialLive(listOf(stroke.copy(points = points.subList(0, n))), pointer = Offset(points[n - 1].x, points[n - 1].y), pressed = true))
                delay(FRAME_MS)
            }
        }
        c.setLive(TutorialLive())
        host.commitStroke(stroke, suggest)
        return stroke
    }

    /** Hazır elemanlar sürüklenerek büyür: [build] başlangıç ve bitiş noktasından elemanları kurar; bitince eklenir. */
    suspend fun drag(from: Offset, to: Offset, durationMs: Long = 650, build: (Offset, Offset) -> List<Stroke>): List<Stroke> {
        if (!fast) {
            c.setLive(TutorialLive(pointer = from, pressed = true))
            delay(120)
            val frames = (durationMs / FRAME_MS).toInt().coerceAtLeast(2)
            for (f in 1..frames) {
                if (fast) break
                val t = easeOut(f / frames.toFloat())
                val cur = from + (to - from) * t
                c.setLive(TutorialLive(build(from, cur), pointer = cur, pressed = true))
                delay(FRAME_MS)
            }
        }
        c.setLive(TutorialLive())
        val result = build(from, to)
        host.commitElements(result)
        return result
    }

    /** Parmak bir noktaya dokunur (tuvalde). */
    suspend fun tap(at: Offset) {
        if (fast) return
        c.setLive(TutorialLive(pointer = at, pressed = false))
        delay(220)
        c.setLive(TutorialLive(pointer = at, pressed = true))
        delay(180)
        c.setLive(TutorialLive(pointer = at, pressed = false))
        delay(140)
        c.setLive(TutorialLive())
    }

    /** Parmak bir aracın (ör. öneri balonu, ⋮) üstüne dokunur. */
    suspend fun tapAnchor(anchor: String) {
        if (fast) return
        c.setLive(TutorialLive(pointerAnchor = anchor, pressed = false))
        delay(320)
        c.setLive(TutorialLive(pointerAnchor = anchor, pressed = true))
        delay(200)
        c.setLive(TutorialLive(pointerAnchor = anchor, pressed = false))
        delay(120)
        c.setLive(TutorialLive())
    }

    /** Hazır elemanları beklemeden ekler (parmak dokunuşuyla). */
    suspend fun place(at: Offset?, strokes: List<Stroke>) {
        if (at != null) tap(at)
        host.commitElements(strokes)
    }

    /**
     * Yapay zekâ şekil önerisini bekler ([accept] koşulunu sağlayan); gelince balon vurgulanır, kullanıcı dokunabilir
     * ya da kısa süre sonra parmak dokunur ve [apply] ile uygulanır. Öneri gelmezse (ya da adım hızlandırıldıysa)
     * [fallback] çalışır.
     */
    suspend fun aiShape(
        @StringRes hint: Int,
        accept: (ShapeSuggestion) -> Boolean = { true },
        apply: (ShapeSuggestion) -> Unit,
        fallback: () -> Unit,
    ) {
        val s = if (fast) null else withTimeoutOrNull(AI_WAIT_MS) { host.shapeSuggestion.first { it != null && accept(it) } }
        if (s == null) {
            host.dismissSuggestions()
            fallback()
            return
        }
        focus(TutorialAnchor.SUGGEST, hint)
        // Kullanıcı balona kendisi dokunabilir; dokunmazsa parmak dokunur
        val changed = if (fast) null else withTimeoutOrNull(USER_WAIT_MS) { host.shapeSuggestion.first { it !== s } }
        if (changed == null && host.shapeSuggestion.value === s) {
            tapAnchor(TutorialAnchor.SUGGEST)
            if (host.shapeSuggestion.value === s) apply(s)
        }
        pause(500)
    }

    /** El yazısının yazı önerisini bekler ve uygular (gelmezse [fallback]). */
    suspend fun aiText(@StringRes hint: Int, fallback: () -> Unit) {
        val s = if (fast) null else withTimeoutOrNull(AI_WAIT_MS) { host.textSuggestion.first { it != null } }
        if (s == null) {
            host.dismissSuggestions()
            fallback()
            return
        }
        focus(TutorialAnchor.SUGGEST_TEXT, hint)
        val changed = if (fast) null else withTimeoutOrNull(USER_WAIT_MS) { host.textSuggestion.first { it !== s } }
        if (changed == null && host.textSuggestion.value === s) {
            tapAnchor(TutorialAnchor.SUGGEST_TEXT)
            if (host.textSuggestion.value === s) host.acceptText(s.text)
        }
        pause(500)
    }

    /**
     * v38 çizgi önerisini bekler ([accept] koşulunu sağlayan; en az iki çizgi) ve tek düğmeyle uygular (gelmezse
     * [fallback]).
     */
    suspend fun aiLines(
        @StringRes hint: Int,
        accept: (com.karapp.drawguidance.ui.screens.canvasedit.state.LineSuggestion) -> Boolean = { true },
        fallback: () -> Unit,
    ) {
        val s = if (fast) null else withTimeoutOrNull(AI_WAIT_MS) { host.lineSuggestion.first { it != null && accept(it) } }
        if (s == null) {
            host.dismissSuggestions()
            fallback()
            return
        }
        focus(TutorialAnchor.SUGGEST_LINE, hint)
        val changed = if (fast) null else withTimeoutOrNull(USER_WAIT_MS) { host.lineSuggestion.first { it !== s } }
        if (changed == null && host.lineSuggestion.value === s) {
            tapAnchor(TutorialAnchor.SUGGEST_LINE)
            if (host.lineSuggestion.value === s) host.acceptLines()
        }
        pause(500)
    }

    companion object {
        const val FRAME_MS = 16L
        const val AI_WAIT_MS = 6_000L
        const val USER_WAIT_MS = 3_500L

        fun length(path: List<Offset>): Float = path.zipWithNext { a, b -> (b - a).getDistance() }.sum()

        private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t)

        /** Yolu yaklaşık [step] aralıklı noktalara böler (canlı çizimde düzgün ilerlesin). */
        fun resample(path: List<Offset>, step: Float): List<Offset> {
            if (path.size < 2) return path
            val out = ArrayList<Offset>()
            out += path[0]
            for (i in 1 until path.size) {
                val a = path[i - 1]; val b = path[i]
                val d = hypot(b.x - a.x, b.y - a.y)
                val n = (d / step).toInt()
                for (k in 1..n) out += a + (b - a) * (k / (n + 1f))
                out += b
            }
            return out
        }
    }
}

/** Şekil biçimi kısaltması (kenar rengi, kalınlık, dolgu, gradyan). */
internal fun shapeStyle(stroke: String, width: Float, fill: String? = null, gradient: GradientSpec? = null, fillOpacity: Float = 1f) =
    ShapeStyle(strokeColorHex = stroke, strokeWidth = width, fillColorHex = fill, fillGradient = gradient, fillOpacity = fillOpacity)
