package com.karapp.drawguidance.ui.screens.canvasedit.challenge

import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.content.fill.RegionFill
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.isErasable
import com.karapp.drawguidance.data.settings.KidsPrefs
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapeRenderer
import com.karapp.drawguidance.ui.drawing.VectorEdit
import com.karapp.drawguidance.ui.screens.canvasedit.state.EditorStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/** v38 süren seviye: sıra numarası (0'dan), hedefi ve kurulum sayacı (her kurulumda artar: maskot baştan konuşur). */
data class ChallengeRun(val index: Int, val target: ChallengeTarget, val seq: Int) {
    val level: ChallengeLevel get() = target.level
}

/** Boyanacak yer: tuvaldeki yeri, beklenen renk ve doğru renge boyandı mı. */
data class PaintMark(val at: Offset, val color: String, val done: Boolean)

/** Çizimin o anki değerlendirmesi: puan, (yerleştir ve boyada) şekil kondu mu ve boyanacak yerler. */
data class ChallengeEval(val score: Int, val drawn: Boolean, val marks: List<PaintMark>)

/**
 * Bitti'ye dokununca değerlendirme: puan, yıldız, geçti mi, seviyenin en iyi puanı ve son seviye mi.
 */
data class ChallengeResult(val index: Int, val score: Int, val stars: Int, val passed: Boolean, val best: Int, val last: Boolean)

/**
 * v38 çocuk modunda çizim görevi ("Çizim Görevi"): seviye seviye görevler. Mod açıkken kâğıtta seviyenin hedefi
 * görünür (kesikli çizgiler ya da boyanacak yerler), çocuk çizer; Bitti'ye dokununca çizim değerlendirilir
 * ([ChallengeScore]) ve sonuç gösterilir. Seviyelerin en iyi puanları kalıcıdır ([KidsPrefs.challengeBest]): ilk seviye
 * ve geçilen seviyelerin bir sonrakine kadar olanlar açıktır, ötekiler kilitli. v40: 50 seviye; son 15'i
 * ([ChallengeLevels.FREE_LEVELS]'ten sonrakiler) Premium hakkı ister.
 *
 * Bir seviye kurulurken tuval tek geri alma adımında temizlenir (boyama görevinde boyanacak şekil konur);
 * [before] düzenleyiciyi çizim moduna alır, [after] tuval değişince düzenleyicinin geçici durumlarını temizler.
 */
class KidsChallengeController(
    private val store: EditorStore,
    private val scope: CoroutineScope,
    private val before: () -> Unit = {},
    private val after: () -> Unit = {},
    prefs: (() -> KidsPrefs?)? = null,
    private val progressRepo: (() -> com.karapp.drawguidance.data.challenge.ChallengeProgressRepository?)? = null,
    premium: (() -> Boolean)? = null,
    entriesRepo: (() -> com.karapp.drawguidance.data.challenge.KidsEntryRepository?)? = null,
) {
    private val levels get() = ChallengeLevels.all

    /**
     * v40 Premium seviyeler açık mı ([com.karapp.drawguidance.data.billing.FeatureKeys.KIDS_CHALLENGE_ADVANCED]
     * hakkı; varsayılan: aboneliğin o anki hakları, abonelik deposu yoksa kapalı).
     */
    private val premium: () -> Boolean = premium ?: {
        runCatching {
            org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<com.karapp.drawguidance.data.billing.SubscriptionRepository>()
                ?.entitlements?.value?.has(com.karapp.drawguidance.data.billing.FeatureKeys.KIDS_CHALLENGE_ADVANCED)
        }.getOrNull() ?: false
    }

    /** v40 çocuk tuvaline girişlerin sayacı (maskotun görev önerisi). */
    private val entries: com.karapp.drawguidance.data.challenge.KidsEntryRepository? by lazy {
        entriesRepo?.invoke() ?: runCatching {
            org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<com.karapp.drawguidance.data.challenge.KidsEntryRepository>()
        }.getOrNull()
    }

    /** İlerleme deposu (yoksa yalnız bellekte). */
    private val prefs: KidsPrefs? by lazy {
        prefs?.invoke() ?: runCatching { org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<KidsPrefs>() }.getOrNull()
    }
    private val memory = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** v39 ilerleme tablosu (challenge_progress; eşitlenir). Yoksa eski ayar dosyası. */
    private val progress: com.karapp.drawguidance.data.challenge.ChallengeProgressRepository? by lazy {
        progressRepo?.invoke() ?: runCatching {
            org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<com.karapp.drawguidance.data.challenge.ChallengeProgressRepository>()
        }.getOrNull()
    }

    /**
     * Seviyelerin en iyi puanları (seviye kimliği → puan). v39 tablo ve eski ayar dosyası birlikte okunur (her
     * seviyede büyüğü): taşınmamış ya da başka yoldan yazılmış puan da kilitleri açar.
     */
    val best: StateFlow<Map<String, Int>> by lazy {
        val table = progress?.best
        val legacy = this.prefs?.challengeBest
        when {
            table != null && legacy != null -> combine(table, legacy, ::maxMerge).stateIn(scope, SharingStarted.Eagerly, maxMerge(table.value, legacy.value))
            table != null -> table
            legacy != null -> legacy
            else -> memory
        }
    }

    private fun maxMerge(a: Map<String, Int>, b: Map<String, Int>): Map<String, Int> =
        (a.keys + b.keys).associateWith { k -> max(a[k] ?: 0, b[k] ?: 0) }

    private val _run = MutableStateFlow<ChallengeRun?>(null)
    /** Süren seviye (null: mod kapalı). */
    val run: StateFlow<ChallengeRun?> = _run.asStateFlow()

    private val _active = MutableStateFlow(false)
    /** Mod açık mı. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _result = MutableStateFlow<ChallengeResult?>(null)
    /** Son değerlendirme (sonuç ekranı; null: gösterilmiyor). */
    val result: StateFlow<ChallengeResult?> = _result.asStateFlow()

    private val _live = MutableStateFlow<ChallengeEval?>(null)
    /** Çizim değiştikçe arka planda yapılan değerlendirme (maskotun yüzü ve boyanacak yerlerin işaretleri). */
    val live: StateFlow<ChallengeEval?> = _live.asStateFlow()

    private var seq = 0

    init {
        scope.launch {
            combine(store.canvas, _run) { c, r -> c?.strokes to r }.collectLatest { (strokes, r) ->
                if (r == null || strokes == null) {
                    _live.value = null
                    return@collectLatest
                }
                _live.value = withContext(Dispatchers.Default) { runCatching { evaluate(r.target, strokes) }.getOrNull() }
            }
        }
    }

    fun bestOf(index: Int): Int? = levels.getOrNull(index)?.let { best.value[it.id] }

    /** Seviye geçildi mi (en iyi puanı geçme sınırında ya da üstünde). */
    fun passed(index: Int): Boolean = (bestOf(index) ?: -1) >= ChallengeScore.PASS

    /** Sıra kilidi açık mı: ilk seviye, geçilen seviyeler ve geçilen bir seviyenin sonraki. */
    fun unlocked(index: Int): Boolean = index in levels.indices && (index == 0 || passed(index) || passed(index - 1))

    /** v40 seviye Premium gerektiriyor ve hak yok (36–50; Free planda kilitli). */
    fun premiumLocked(index: Int): Boolean = ChallengeLevels.isPremium(index) && !premium()

    /** Seviye açılabilir mi: sıra kilidi açık ve (Premium seviyeyse) Premium hakkı var. */
    fun reachable(index: Int): Boolean = unlocked(index) && !premiumLocked(index)

    /** Başlat'ta açılacak seviye: geçilmemiş ilk seviye (hepsi geçildiyse sonuncusu). Premium kilidine bakılmaz. */
    fun nextIndex(): Int = levels.indices.firstOrNull { !passed(it) } ?: levels.lastIndex

    /** Modu başlatır: sıradaki seviye kurulur (Premium kilitliyse açılmaz; bkz. [premiumLocked]). */
    fun start(): Boolean = open(nextIndex())

    /**
     * [index] seviyesini kurar (mod kapalıysa açılır, açıksa o seviyeye geçilir): tuval tek adımda temizlenir (geri
     * alınabilir), boyama görevinde şekil kâğıda konur. Kilitli seviye açılmaz (false).
     */
    fun open(index: Int): Boolean {
        if (!reachable(index)) return false
        val canvas = store.canvas.value ?: return false
        if (store.inSandbox) return false
        before()
        val level = levels[index]
        val target = ChallengeLevels.target(level, canvas.width, canvas.height)
        val setup = if (level.kind == ChallengeKind.PAINT) {
            listOfNotNull(ChallengeLevels.build(level, target.box, java.util.UUID.randomUUID().toString()))
        } else emptyList()
        val current = store.canvas.value ?: return false
        store.select(emptySet())
        if (current.strokes.isNotEmpty() || setup.isNotEmpty()) store.commit(setup)
        after()
        _result.value = null
        _run.value = ChallengeRun(index, target, ++seq)
        _active.value = true
        _suggest.value = false
        return true
    }

    /** Modu kapatır (çizim olduğu gibi kalır). */
    fun stop() {
        _result.value = null
        _run.value = null
        _active.value = false
    }

    /** Bitti: çizim değerlendirilir, en iyi puan kaydedilir ve sonuç ekranı açılır. */
    fun finish(): ChallengeResult? {
        val r = _run.value ?: return null
        val strokes = store.canvas.value?.strokes.orEmpty()
        val eval = evaluate(r.target, strokes)
        val level = r.level
        val best = record(level.id, eval.score)
        val result = ChallengeResult(
            index = r.index, score = eval.score, stars = ChallengeScore.stars(eval.score),
            passed = ChallengeScore.passed(eval.score), best = best, last = r.index == levels.lastIndex,
        )
        _result.value = result
        return result
    }

    /** Sonuç ekranından: sonraki seviye (yalnız geçildiyse). */
    fun next(): Boolean {
        val r = _result.value ?: return false
        if (!r.passed || r.last) return false
        return open(r.index + 1)
    }

    /** v40 sonuç ekranından sonraki seviye Premium yüzünden açılamıyor mu (Sonraki'de "Premium gerekli" açılır). */
    fun nextNeedsPremium(): Boolean {
        val r = _result.value ?: return false
        return r.passed && !r.last && premiumLocked(r.index + 1)
    }

    private val _suggest = MutableStateFlow(false)
    /** v40 maskot çizim görevini öneriyor mu (çocuk tuvaline ilk [KidsEntryRepository.SUGGEST_ENTRIES] girişte). */
    val suggest: StateFlow<Boolean> = _suggest.asStateFlow()

    private var entryCounted = false

    /**
     * v40 çocuk tuvaline giriş: giriş bir kez sayılır (ekran dönse de aynı düzenleyicide yeniden sayılmaz); ilk 5
     * girişte görev açık değilse öneri gösterilir. Sayaç yoksa (önizleme) öneri yok.
     */
    fun onKidsEntry() {
        if (entryCounted) return
        entryCounted = true
        val repo = entries ?: return
        scope.launch {
            val row = runCatching { repo.recordEntry() }.getOrNull() ?: return@launch
            if (repo.suggestionsEnabled && com.karapp.drawguidance.data.challenge.KidsEntryRepository.shouldSuggest(row.entries, _active.value)) {
                _suggest.value = true
                runCatching { repo.recordSuggested() }
            }
        }
    }

    /** Öneri sonucu: başladı, Premium gerekli (geçilmemiş ilk seviye Premium) ya da açılamadı. */
    enum class SuggestOutcome { STARTED, NEEDS_PREMIUM, FAILED }

    /** v40 önerideki Başlat: görev geçilmemiş ilk seviyeden başlar (Premium kilidine uyulur). */
    fun acceptSuggestion(): SuggestOutcome {
        _suggest.value = false
        entries?.let { repo -> scope.launch { runCatching { repo.recordAnswer(started = true) } } }
        val next = nextIndex()
        if (premiumLocked(next)) return SuggestOutcome.NEEDS_PREMIUM
        return if (open(next)) SuggestOutcome.STARTED else SuggestOutcome.FAILED
    }

    /** v40 önerideki İptal: balon bu giriş için kapanır. */
    fun cancelSuggestion() {
        if (!_suggest.value) return
        _suggest.value = false
        entries?.let { repo -> scope.launch { runCatching { repo.recordAnswer(started = false) } } }
    }

    /** Sonuç ekranından: aynı seviye baştan (tuval temizlenir; geri alınabilir). */
    fun retry(): Boolean {
        val index = _result.value?.index ?: _run.value?.index ?: return false
        return open(index)
    }

    /** Sonuç ekranı kapanır, mod sürer (ör. son seviyeden sonra İlerleme). */
    fun dismissResult() {
        _result.value = null
    }

    private fun record(id: String, score: Int): Int {
        // v39 tabloya yazılır (girişliyse sunucuyla eşitlenir); eski ayar dosyası da güncel tutulur
        val p = progress
        if (p != null) {
            val legacy = prefs?.recordChallenge(id, score) ?: 0
            return max(p.record(id, score), legacy)
        }
        prefs?.let { return it.recordChallenge(id, score) }
        val best = max(memory.value[id] ?: 0, score)
        memory.value = memory.value + (id to best)
        return best
    }

    companion object {
        /**
         * Çizimin hedefe göre değerlendirmesi (bkz. [ChallengeScore]): çizme görevinde çizgiler, boyama görevinde
         * boyanacak yerlerin renkleri, yerleştir ve boyada ikisi birden (boyanacak yerler en iyi uyan şekilde aranır).
         */
        fun evaluate(target: ChallengeTarget, strokes: List<Stroke>): ChallengeEval {
            val level = target.level
            val visible = strokes.filter { !it.hidden }
            val outline = if (level.kind == ChallengeKind.PAINT) 0 else ChallengeScore.outline(drawnLines(visible), target.lines)
            if (level.kind == ChallengeKind.TRACE) return ChallengeEval(outline, visible.isNotEmpty(), emptyList())
            // Kâğıttaki aynı türden şekillerden boyası en iyi uyan
            val candidates = visible.filter { it.shape?.kind == level.shape }
            var bestScore = -1
            var bestMarks = emptyList<PaintMark>()
            for (shape in candidates) {
                val paths = ShapeFactory.subPaths(shape)
                val bounds = ChallengeLevels.outlineBounds(paths) ?: continue
                val checks = target.probes.map { pr ->
                    val at = Offset(bounds.left + pr.u * bounds.width, bounds.top + pr.v * bounds.height)
                    Triple(at, pr.color, colorAt(shape, paths, at))
                }
                val score = ChallengeScore.paint(checks.map { it.second to it.third })
                if (score > bestScore) {
                    bestScore = score
                    bestMarks = checks.map { (at, want, got) -> PaintMark(at, want, ChallengeScore.paintCredit(want, got) >= 0.99f) }
                }
            }
            val paint = bestScore.coerceAtLeast(0)
            val score = if (level.kind == ChallengeKind.PAINT) paint else ChallengeScore.combine(outline, paint)
            return ChallengeEval(score, candidates.isNotEmpty(), bestMarks)
        }

        /** Şeklin [at] noktasında görünen dolgu rengi (üstteki parça öndedir; dolgusuz parça arkayı örtmez). */
        fun colorAt(shape: Stroke, paths: List<ShapeFactory.EditablePath>, at: Offset): String? {
            val spec = shape.shape ?: return null
            val q = P(at.x, at.y)
            var color: String? = null
            for (p in paths) {
                if (!p.closed || p.points.size < 3 || p.label != null || p.tag == VectorEdit.VNODE) continue
                if (!RegionFill.contains(p.points.map { P(it.x, it.y) }, q)) continue
                ShapeRenderer.resolveFill(p.fillColorHex, spec.style)?.takeIf { it.isNotEmpty() }?.let { color = it }
            }
            return color
        }

        /**
         * Kâğıttaki çizgiler: fırça izleri (sonradan silgiyle silinen yerleri çıkarılır, iz orada bölünür) ve şekillerin
         * çizgileri (boyanmış alanlar ve yazılar hariç). Yazı, resim ve diyagram sayılmaz.
         */
        fun drawnLines(strokes: List<Stroke>): List<List<Offset>> {
            val out = ArrayList<List<Offset>>()
            val erasers = strokes.withIndex().filter { it.value.brushType == BrushType.ERASER && it.value.points.isNotEmpty() }
                .map { IndexedValue(it.index, Rub(it.value)) }
            for ((i, s) in strokes.withIndex()) {
                when {
                    s.brushType == BrushType.SHAPE -> ShapeFactory.subPaths(s).forEach { p ->
                        if (p.label != null || com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush.isPaintLayer(p.tag) || p.tag == VectorEdit.VNODE || p.points.size < 2) return@forEach
                        if ((p.widthScale ?: 1f) <= 0f) return@forEach
                        out += if (p.closed) p.points + p.points.first() else p.points
                    }
                    s.brushType.isErasable && s.space == null -> {
                        val pts = s.points.map { Offset(it.x, it.y) }
                        val later = erasers.filter { it.index > i }.map { it.value }
                        if (later.isEmpty()) { out += pts; continue }
                        var piece = ArrayList<Offset>()
                        for (p in pts) {
                            if (later.any { e -> e.covers(p) }) {
                                if (piece.isNotEmpty()) out += piece
                                piece = ArrayList()
                            } else piece += p
                        }
                        if (piece.isNotEmpty()) out += piece
                    }
                }
            }
            return out
        }

        /** Silgi izi: kutusu bir kez hesaplanır (noktanın silinip silinmediği hızlı bakılır). */
        private class Rub(eraser: Stroke) {
            private val r = eraser.sizePx / 2f
            private val pts = eraser.points.map { Offset(it.x, it.y) }
            private val minX = pts.minOf { it.x } - r
            private val maxX = pts.maxOf { it.x } + r
            private val minY = pts.minOf { it.y } - r
            private val maxY = pts.maxOf { it.y } + r

            fun covers(p: Offset): Boolean {
                if (p.x < minX || p.x > maxX || p.y < minY || p.y > maxY) return false
                if (pts.size == 1) return (pts[0] - p).getDistance() <= r
                for (k in 1 until pts.size) if (ChallengeScore.segment(p, pts[k - 1], pts[k]) <= r) return true
                return false
            }
        }
    }
}
