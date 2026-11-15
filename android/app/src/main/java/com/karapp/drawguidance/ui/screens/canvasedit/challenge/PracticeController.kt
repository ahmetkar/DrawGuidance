package com.karapp.drawguidance.ui.screens.canvasedit.challenge

import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.settings.PracticePrefs
import com.karapp.drawguidance.ui.screens.canvasedit.state.EditorStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max

/**
 * v50 süren alıştırma: listedeki sırası, hedefi, kurulum sayacı ve başlarken kâğıtta olan elemanlar ([baseline]).
 * v51 [startedAt]: süreli çizimde ilk çizginin zamanı (süre o an başlar).
 */
data class PracticeRun(val index: Int, val target: PracticeTarget, val seq: Int, val baseline: Set<String>, val startedAt: Long? = null) {
    val level: PracticeLevel get() = target.level
}

/**
 * Bitti'ye dokununca (ya da süre bitince): puan ve parçaları ([detail]), yıldız, geçti mi, en iyi puan, yeni rekor mu,
 * kullanılan ipucu ve son alıştırma mı.
 */
data class PracticeResult(
    val index: Int,
    val score: Int,
    val stars: Int,
    val passed: Boolean,
    val best: Int,
    val hints: Int,
    val last: Boolean,
    val detail: PracticeScore = PracticeScore.ZERO,
    val record: Boolean = false,
    val timeUp: Boolean = false,
)

/**
 * v50 yetişkin çizim alıştırmaları (basit ve gelişmiş resim). Çocuk görevinden farkları: hazır şekiller sayılmaz (elle
 * çizim), toleranslar dar.
 *
 * v51:
 *  - Başka bir alıştırmaya geçerken önceki alıştırmada çizim varsa tuval kaydedilir ve yeni alıştırma yeni bir tuvalde
 *    açılır ([newCanvas]; düzenleyici yeni tuvale geçer). Aynı alıştırmayı yeniden denemek denemeyi tek geri alma
 *    adımında siler (eskisi gibi).
 *  - Ödüllü reklamla açılan alıştırmalar ([PracticeLevel.reward]): açılmamışsa [open] kurmaz, [locked] döner.
 *  - Puan [PracticeScore] (isabet, kapsama, akıcılık, süreli çizimde hız).
 */
class PracticeController(
    private val store: EditorStore,
    private val scope: CoroutineScope,
    private val before: () -> Unit = {},
    private val after: () -> Unit = {},
    prefs: (() -> PracticePrefs?)? = null,
    /** v51 alıştırma [index]'i yeni tuvalde açılsın (eski tuval kaydedilir); null: aynı tuvalde devam. */
    private val newCanvas: ((index: Int) -> Unit)? = null,
    /** v51 ödüllü reklam gerekmiyor mu (Premium). */
    private val adFree: () -> Boolean = { false },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val prefs: PracticePrefs? by lazy {
        prefs?.invoke() ?: runCatching { org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<PracticePrefs>() }.getOrNull()
    }
    private val memory = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val memoryUnlocked = MutableStateFlow<Set<String>>(emptySet())

    /** Alıştırmaların en iyi puanları (kimlik → puan). */
    val best: StateFlow<Map<String, Int>> by lazy { this.prefs?.best ?: memory }

    /** v51 ödüllü reklamla açılmış alıştırmalar. */
    val unlocked: StateFlow<Set<String>> by lazy { this.prefs?.unlocked ?: memoryUnlocked }

    private val _run = MutableStateFlow<PracticeRun?>(null)
    val run: StateFlow<PracticeRun?> = _run.asStateFlow()

    private val _result = MutableStateFlow<PracticeResult?>(null)
    val result: StateFlow<PracticeResult?> = _result.asStateFlow()

    private val _live = MutableStateFlow<Int?>(null)
    /** Çizim değiştikçe arka planda hesaplanan puan (kartta gösterilir). */
    val live: StateFlow<Int?> = _live.asStateFlow()

    private val _hints = MutableStateFlow(0)
    /** Kaybolan kılavuzda kullanılan ipucu sayısı. */
    val hints: StateFlow<Int> = _hints.asStateFlow()

    private val _remaining = MutableStateFlow<Int?>(null)
    /** v51 süreli çizimde kalan saniye (süreli değilse null). */
    val remaining: StateFlow<Int?> = _remaining.asStateFlow()

    private val _locked = MutableStateFlow<Int?>(null)
    /** v51 açılmak istenen ama ödüllü reklamla açılması gereken alıştırma (sıra); pencere gösterilir. */
    val locked: StateFlow<Int?> = _locked.asStateFlow()

    private var seq = 0
    private var timer: Job? = null

    init {
        scope.launch {
            combine(store.canvas, _run, _hints) { c, r, h -> Triple(c?.strokes, r, h) }.collectLatest { (strokes, r, h) ->
                if (r == null || strokes == null) {
                    _live.value = null
                    return@collectLatest
                }
                // Süreli çizimde süre ilk çizgiyle başlar
                if (r.level.kind == PracticeKind.TIMED && r.startedAt == null && mine(r, strokes).isNotEmpty()) {
                    startTimer(r)
                    return@collectLatest
                }
                _live.value = withContext(Dispatchers.Default) { runCatching { score(r, strokes, h, clock()).total }.getOrNull() }
            }
        }
    }

    val active: Boolean get() = _run.value != null

    /** Tuval türünün alıştırmaları. */
    fun levels(): List<PracticeLevel> = PracticeLevels.forCanvas(store.canvas.value?.kind)

    fun bestOf(level: PracticeLevel): Int? = best.value[level.id]

    /** v51 alıştırma ödüllü reklam bekliyor mu. */
    fun isLocked(level: PracticeLevel): Boolean = level.reward && level.id !in unlocked.value && !adFree()

    /** v51 ödüllü reklam izlendi: alıştırma açılır ve kurulur. */
    fun unlockAndOpen(index: Int): Boolean {
        val level = levels().getOrNull(index) ?: return false
        prefs?.unlock(level.id) ?: run { memoryUnlocked.value = memoryUnlocked.value + level.id }
        _locked.value = null
        return open(index)
    }

    fun dismissLocked() {
        _locked.value = null
    }

    /**
     * [index] alıştırmasını kurar. Kilitliyse ([isLocked]) kurulmaz, [locked] dolar. Önceki alıştırmada çizim varsa:
     * başka alıştırmaya geçerken yeni tuval açılır ([newCanvas]), aynı alıştırmada deneme tek geri alma adımında silinir.
     */
    fun open(index: Int): Boolean {
        val list = levels()
        val level = list.getOrNull(index) ?: return false
        val canvas = store.canvas.value ?: return false
        if (store.inSandbox) return false
        if (isLocked(level)) {
            _locked.value = index
            return false
        }
        val previous = _run.value
        if (previous != null && previous.index != index && newCanvas != null && mine(previous, canvas.strokes).isNotEmpty()) {
            stop()
            newCanvas.invoke(index)
            return true
        }
        before()
        if (previous != null) {
            val kept = canvas.strokes.filter { it.id in previous.baseline }
            if (kept.size != canvas.strokes.size) {
                store.select(emptySet())
                store.commit(kept)
            }
        }
        after()
        val strokes = store.canvas.value?.strokes.orEmpty()
        val target = PracticeLevels.target(level, canvas.width, canvas.height)
        stopTimer()
        _hints.value = 0
        _result.value = null
        _remaining.value = if (level.kind == PracticeKind.TIMED) target.seconds else null
        _run.value = PracticeRun(index, target, ++seq, strokes.mapTo(HashSet()) { it.id })
        return true
    }

    /** Alıştırmayı kapatır (çizim olduğu gibi kalır). */
    fun stop() {
        stopTimer()
        _result.value = null
        _run.value = null
        _hints.value = 0
        _remaining.value = null
    }

    /** Kaybolan kılavuzda ipucu (kılavuz bir süre yeniden görünür; puandan [PracticeLevels.HINT_PENALTY] düşer). */
    fun hint() {
        if (_run.value?.level?.kind != PracticeKind.FADE) return
        _hints.value += 1
    }

    fun finish(): PracticeResult? = finish(timeUp = false)

    private fun finish(timeUp: Boolean): PracticeResult? {
        val r = _run.value ?: return null
        stopTimer()
        val strokes = store.canvas.value?.strokes.orEmpty()
        val detail = score(r, strokes, _hints.value, clock())
        val previousBest = best.value[r.level.id] ?: 0
        val best = record(r.level.id, detail.total)
        val result = PracticeResult(
            r.index, detail.total, PracticeLevels.stars(detail.total), detail.total >= PracticeLevels.PASS, best, _hints.value,
            r.index == levels().lastIndex, detail, record = detail.total > previousBest && previousBest > 0, timeUp = timeUp,
        )
        _result.value = result
        return result
    }

    fun next(): Boolean {
        val r = _result.value ?: return false
        if (r.last) return false
        return open(r.index + 1)
    }

    fun retry(): Boolean {
        val index = _result.value?.index ?: _run.value?.index ?: return false
        return open(index)
    }

    fun dismissResult() {
        _result.value = null
    }

    private fun startTimer(r: PracticeRun) {
        val started = r.copy(startedAt = clock())
        _run.value = started
        timer?.cancel()
        timer = scope.launch {
            val limit = started.target.seconds * 1000L
            while (isActive) {
                val left = ((limit - (clock() - started.startedAt!!)) / 1000L).toInt()
                _remaining.value = left.coerceAtLeast(0)
                if (left <= 0) {
                    if (_run.value?.seq == started.seq && _result.value == null) finish(timeUp = true)
                    break
                }
                delay(250)
            }
        }
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    private fun record(id: String, score: Int): Int {
        prefs?.let { return it.record(id, score) }
        val best = max(memory.value[id] ?: 0, score)
        memory.value = memory.value + (id to best)
        return best
    }

    companion object {
        /** Alıştırma başladıktan sonra elle çizilen çizgiler (hazır şekiller ve görünmeyenler hariç). */
        fun mine(run: PracticeRun, strokes: List<Stroke>): List<Stroke> =
            strokes.filter { it.id !in run.baseline && !it.hidden && it.brushType != BrushType.SHAPE }

        /** v51 denemenin ayrıntılı puanı ([PracticeScore]). */
        fun score(run: PracticeRun, strokes: List<Stroke>, hints: Int, now: Long = System.currentTimeMillis()): PracticeScore {
            val mine = mine(run, strokes)
            if (mine.none { it.brushType != BrushType.ERASER }) return PracticeScore.ZERO.copy(hints = hints)
            val kind = run.level.kind
            val lines = KidsChallengeController.drawnLines(mine)
            val aligned = if (kind == PracticeKind.COPY) 1f else PracticeScore.ALIGNED
            val parts = ChallengeScore.outlineParts(lines, run.target.lines, kind.near, kind.far, aligned)
            val box = run.target.box
            val smooth = PracticeScore.smoothness(lines, hypot(box.width, box.height))
            val speed = if (kind == PracticeKind.TIMED) {
                val started = run.startedAt ?: now
                PracticeScore.speed(now - started, run.target.seconds * 1000L)
            } else null
            return PracticeScore.of(parts, smooth, speed, hints)
        }

        /** Denemenin toplam puanı (eski arayüz; testler). */
        fun evaluate(run: PracticeRun, strokes: List<Stroke>, hints: Int): Int = score(run, strokes, hints).total
    }
}
