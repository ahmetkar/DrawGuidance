package com.karapp.drawguidance.ui.screens.canvasedit.challenge

import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.settings.PracticePrefs
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

/** v50 süren alıştırma: listedeki sırası, hedefi, kurulum sayacı ve başlarken kâğıtta olan elemanlar ([baseline]). */
data class PracticeRun(val index: Int, val target: PracticeTarget, val seq: Int, val baseline: Set<String>) {
    val level: PracticeLevel get() = target.level
}

/** Bitti'ye dokununca: puan, yıldız, geçti mi, en iyi puan, kullanılan ipucu ve son alıştırma mı. */
data class PracticeResult(val index: Int, val score: Int, val stars: Int, val passed: Boolean, val best: Int, val hints: Int, val last: Boolean)

/**
 * v50 yetişkin çizim alıştırmaları (basit ve gelişmiş resim). Çocuk görevinden farkları: kâğıttaki eski çizim
 * silinmez (yalnız alıştırma sırasında çizilenler puanlanır; yeni alıştırmaya geçerken o deneme tek geri alma adımında
 * kaldırılır), hazır şekiller sayılmaz (elle çizim), toleranslar dar ve bütün alıştırmalar açıktır (kilit yok).
 */
class PracticeController(
    private val store: EditorStore,
    private val scope: CoroutineScope,
    private val before: () -> Unit = {},
    private val after: () -> Unit = {},
    prefs: (() -> PracticePrefs?)? = null,
) {
    private val prefs: PracticePrefs? by lazy {
        prefs?.invoke() ?: runCatching { org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<PracticePrefs>() }.getOrNull()
    }
    private val memory = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Alıştırmaların en iyi puanları (kimlik → puan). */
    val best: StateFlow<Map<String, Int>> by lazy { this.prefs?.best ?: memory }

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

    private var seq = 0

    init {
        scope.launch {
            combine(store.canvas, _run, _hints) { c, r, h -> Triple(c?.strokes, r, h) }.collectLatest { (strokes, r, h) ->
                if (r == null || strokes == null) {
                    _live.value = null
                    return@collectLatest
                }
                _live.value = withContext(Dispatchers.Default) { runCatching { evaluate(r, strokes, h) }.getOrNull() }
            }
        }
    }

    val active: Boolean get() = _run.value != null

    /** Tuval türünün alıştırmaları. */
    fun levels(): List<PracticeLevel> = PracticeLevels.forCanvas(store.canvas.value?.kind)

    fun bestOf(level: PracticeLevel): Int? = best.value[level.id]

    /**
     * [index] alıştırmasını kurar: önceki denemenin çizgileri (alıştırma sırasında çizilenler) tek geri alma adımında
     * kaldırılır, kâğıttaki eski çizim kalır.
     */
    fun open(index: Int): Boolean {
        val list = levels()
        val level = list.getOrNull(index) ?: return false
        val canvas = store.canvas.value ?: return false
        if (store.inSandbox) return false
        before()
        val previous = _run.value
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
        _hints.value = 0
        _result.value = null
        _run.value = PracticeRun(index, target, ++seq, strokes.mapTo(HashSet()) { it.id })
        return true
    }

    /** Alıştırmayı kapatır (çizim olduğu gibi kalır). */
    fun stop() {
        _result.value = null
        _run.value = null
        _hints.value = 0
    }

    /** Kaybolan kılavuzda ipucu (kılavuz bir süre yeniden görünür; puandan [PracticeLevels.HINT_PENALTY] düşer). */
    fun hint() {
        if (_run.value?.level?.kind != PracticeKind.FADE) return
        _hints.value += 1
    }

    fun finish(): PracticeResult? {
        val r = _run.value ?: return null
        val strokes = store.canvas.value?.strokes.orEmpty()
        val score = evaluate(r, strokes, _hints.value)
        val best = record(r.level.id, score)
        val result = PracticeResult(
            r.index, score, PracticeLevels.stars(score), score >= PracticeLevels.PASS, best, _hints.value, r.index == levels().lastIndex,
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

    private fun record(id: String, score: Int): Int {
        prefs?.let { return it.record(id, score) }
        val best = max(memory.value[id] ?: 0, score)
        memory.value = memory.value + (id to best)
        return best
    }

    companion object {
        /**
         * Denemenin puanı: alıştırma başladıktan sonra elle çizilen çizgilerin (hazır şekiller ve görünmeyenler hariç)
         * hedefin bütün çizgilerine yakınlığı, türün toleransıyla; ipuçları puandan düşer.
         */
        fun evaluate(run: PracticeRun, strokes: List<Stroke>, hints: Int): Int {
            val mine = strokes.filter { it.id !in run.baseline && !it.hidden && it.brushType != BrushType.SHAPE }
            if (mine.none { it.brushType != BrushType.ERASER }) return 0
            val kind = run.level.kind
            val raw = ChallengeScore.outline(KidsChallengeController.drawnLines(mine), run.target.lines, kind.near, kind.far)
            return (raw - hints * PracticeLevels.HINT_PENALTY).coerceIn(0, 100)
        }
    }
}
