package com.karapp.drawguidance.ui.screens.canvasedit.state

import com.karapp.drawguidance.data.model.Snapping
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.TransformFilter
import com.karapp.drawguidance.data.model.TransformKind
import com.karapp.drawguidance.data.model.TransformOps
import com.karapp.drawguidance.data.model.TransformState
import com.karapp.drawguidance.data.model.WarpMesh
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/** v56 yaslama ayarları (mod başına kayıtlı): mıknatıslar, yaslama, mesafe (ekran px), hız eşiği (ekran px/sn). */
data class SnapPrefs(val magnets: Boolean = true, val snap: Boolean = true, val distance: Float = 10f, val speed: Float = 1500f) {
    fun encode(): String = "m=${if (magnets) 1 else 0};s=${if (snap) 1 else 0};d=${distance.roundToInt()};v=${speed.roundToInt()}"

    companion object {
        fun decode(text: String?): SnapPrefs {
            if (text.isNullOrBlank()) return SnapPrefs()
            val m = text.split(";").mapNotNull { it.split("=").takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }.toMap()
            return SnapPrefs(
                magnets = m["m"] != "0", snap = m["s"] != "0",
                distance = m["d"]?.toFloatOrNull()?.coerceIn(2f, 40f) ?: 10f, speed = m["v"]?.toFloatOrNull()?.coerceIn(0f, 6000f) ?: 1500f,
            )
        }
    }
}

/**
 * v56 aşama 6 Dönüştür paneli durumu (planın 2A bölümü): seçili elemanların başlangıç kopyası, dönüşüm durumu, canlı
 * önizleme (geçmişe girmez), yaslama kılavuzları. "Bitti" tek geri alma adımı; "Sıfırla" ve "İptal" belgeyi değiştirmez.
 */
class TransformController(
    private val store: EditorStore,
    private val loadSnap: () -> String? = { null },
    private val saveSnap: (String) -> Unit = {},
    /** Tuvale sığdır hedefi (çizgi romanda seçimin bulunduğu panel). */
    private val fitTarget: (cx: Float, cy: Float) -> FloatArray? = { _, _ -> null },
) {
    private val _state = MutableStateFlow<TransformState?>(null)
    val state: StateFlow<TransformState?> = _state.asStateFlow()

    private val _snap = MutableStateFlow(SnapPrefs())
    val snap: StateFlow<SnapPrefs> = _snap.asStateFlow()

    /** Yaslanınca görünen kılavuz çizgileri (tuval x ve y). */
    private val _guides = MutableStateFlow<Pair<Float?, Float?>>(null to null)
    val guides: StateFlow<Pair<Float?, Float?>> = _guides.asStateFlow()

    /** Bozma/Çarpıtma'da değişmeyen yazı, şema ve formül sayısı (kullanıcıya bilgi). */
    private val _skipped = MutableStateFlow(0)
    val skipped: StateFlow<Int> = _skipped.asStateFlow()

    private val _advancedMesh = MutableStateFlow(false)
    val advancedMesh: StateFlow<Boolean> = _advancedMesh.asStateFlow()

    private var ids: Set<String> = emptySet()
    private var original: List<Stroke> = emptyList()
    private var targetsX: List<Float> = emptyList()
    private var targetsY: List<Float> = emptyList()

    val isOpen: Boolean get() = _state.value != null

    /** Seçim varsa paneli açar (öntanımlı Tekdüze). */
    fun open(kind: TransformKind = TransformKind.UNIFORM): Boolean {
        if (isOpen) return true
        val c = store.canvas.value ?: return false
        val sel = store.selection
        if (sel.isEmpty()) return false
        val strokes = c.strokes
        val b = ElementOps.selectionBounds(strokes, sel) ?: return false
        _snap.value = SnapPrefs.decode(loadSnap())
        ids = sel.toSet()
        original = strokes
        // Yaslama hedefleri: tuval kenar ve merkezi, öteki elemanların kenar ve merkezleri
        val xs = arrayListOf(0f, c.width / 2f, c.width.toFloat())
        val ys = arrayListOf(0f, c.height / 2f, c.height.toFloat())
        strokes.filter { it.id !in ids && !it.hidden }.take(400).forEach { s ->
            ElementOps.bounds(s)?.let { r -> xs += listOf(r.left, r.center.x, r.right); ys += listOf(r.top, r.center.y, r.bottom) }
        }
        targetsX = xs; targetsY = ys
        _skipped.value = 0
        _state.value = TransformOps.setKind(TransformState.start(b.left, b.top, b.right, b.bottom), kind, meshSize())
        return true
    }

    private fun meshSize() = if (_advancedMesh.value) WarpMesh.ADVANCED else WarpMesh.BASIC

    private fun preview(s: TransformState) {
        _state.value = s
        val applied = TransformOps.apply(original, ids, s)
        _skipped.value = applied.skipped.size
        store.previewStrokes(applied.strokes)
    }

    private inline fun change(f: (TransformState) -> TransformState) {
        val s = _state.value ?: return
        preview(f(s))
    }

    fun setKind(kind: TransformKind) = change { TransformOps.setKind(it, kind, meshSize()) }

    fun setAdvancedMesh(on: Boolean) {
        _advancedMesh.value = on
        change { s -> if (s.kind == TransformKind.WARP) TransformOps.setKind(s.copy(mesh = s.mesh?.let { m -> WarpMesh.resample(m, WarpMesh.sizeOf(m), meshSize()) }), TransformKind.WARP, meshSize()) else s }
    }

    fun setFilter(f: TransformFilter) = change { it.copy(filter = f) }

    fun setSnap(p: SnapPrefs) { _snap.value = p; saveSnap(p.encode()) }

    /**
     * Taşıma: [dx], [dy] tuval birimi; [scale] görünüm ölçeği (yaslama mesafesi ekran px), [speed] ekran px/sn.
     * [axisLock]: mıknatısla baskın eksene kilitli taşıma (çağıran toplam sürüklemeye göre verir).
     */
    fun translate(dx: Float, dy: Float, scale: Float = 1f, speed: Float = 0f) {
        val s = _state.value ?: return
        var moved = TransformOps.translate(s, dx, dy)
        val p = _snap.value
        if (p.snap && Snapping.active(speed, p.speed)) {
            val q = moved.mesh?.let { m -> val n = WarpMesh.sizeOf(m); listOf(m[0], m[n - 1], m[n * n - 1], m[n * (n - 1)]) } ?: moved.quad
            val l = q.minOf { it.x }; val r = q.maxOf { it.x }; val t = q.minOf { it.y }; val b = q.maxOf { it.y }
            val res = Snapping.snapBox(l, t, r, b, targetsX, targetsY, p.distance / scale.coerceAtLeast(0.01f))
            if (res.dx != 0f || res.dy != 0f) moved = TransformOps.translate(moved, res.dx, res.dy)
            _guides.value = res.guideX to res.guideY
        } else _guides.value = null to null
        preview(moved)
    }

    fun endDrag() { _guides.value = null to null }

    fun dragCorner(i: Int, x: Float, y: Float) = change { s ->
        var next = TransformOps.dragCorner(s, i, x, y)
        // Mıknatıs: Tekdüze'de ölçek tam oranlara (%25 adımları)
        if (_snap.value.magnets && s.kind == TransformKind.UNIFORM) {
            val w0 = s.r - s.l
            val k = TransformOps.widthOf(next.quad) / w0
            val ks = Snapping.angle(k * 100f, step = 25f, tolerance = 3f) / 100f
            if (ks != k && ks > 0f) {
                val o = next.quad[(i + 2) % 4]; val c = next.quad[i]
                next = TransformOps.dragCorner(s, i, o.x + (c.x - o.x) * ks / k, o.y + (c.y - o.y) * ks / k)
            }
        }
        next
    }

    fun dragEdge(e: Int, dx: Float, dy: Float) = change { TransformOps.dragEdge(it, e, dx, dy) }
    fun rotateTo(degrees: Float) = change { TransformOps.rotateTo(it, degrees, _snap.value.magnets) }
    fun dragMesh(index: Int, x: Float, y: Float) = change { TransformOps.dragMesh(it, index, x, y) }
    fun dragMeshInside(px: Float, py: Float, dx: Float, dy: Float) = change { TransformOps.dragMeshInside(it, px, py, dx, dy) }
    fun flip(horizontal: Boolean) = change { TransformOps.flip(it, horizontal) }

    /** 45° döndür (uzun basınca 90°). */
    fun rotateStep(degrees: Float = 45f) = change { TransformOps.rotate(it, degrees) }

    fun fitToCanvas() = change { s ->
        val c = store.canvas.value ?: return@change s
        val cx = s.quad.sumOf { it.x.toDouble() }.toFloat() / 4f; val cy = s.quad.sumOf { it.y.toDouble() }.toFloat() / 4f
        val box = fitTarget(cx, cy) ?: floatArrayOf(0f, 0f, c.width.toFloat(), c.height.toFloat())
        TransformOps.fit(s, box[0], box[1], box[2], box[3])
    }

    fun numeric(x: Float, y: Float, w: Float, h: Float, degrees: Float) = change { TransformOps.numeric(it, x, y, w, h, degrees) }

    fun reset() = change { TransformOps.reset(it) }

    /** Uygula: tek geri alma adımı. */
    fun done() {
        val s = _state.value ?: return
        store.endPreview()
        if (!s.isIdentity) {
            val applied = TransformOps.apply(original, ids, s).strokes
            store.commit(applied, useArea = false)
            store.select(ids)
        }
        close()
    }

    fun cancel() {
        if (_state.value == null) return
        store.endPreview()
        close()
    }

    private fun close() {
        _state.value = null
        _guides.value = null to null
        original = emptyList()
        ids = emptySet()
    }

    /** Görünen durum için kutu bilgileri: (x, y, en, boy, açı). */
    fun metrics(): FloatArray? {
        val q = _state.value?.quad ?: return null
        return floatArrayOf(q[0].x, q[0].y, TransformOps.widthOf(q), TransformOps.heightOf(q), TransformOps.angleOf(q))
    }
}
