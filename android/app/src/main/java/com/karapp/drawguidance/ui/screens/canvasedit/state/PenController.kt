package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapePath
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Kalem aracının bir noktası: [handle] verilmişse eğri noktası (çıkış kontrol noktası; giriş simetriği).
 * v44 [bend]: bu noktadan sonrakine giden çizgi elle eğildiyse ikinci derece Bezier'in kontrol noktası (o çizgide
 * tutamakların yerine geçer).
 */
data class PenAnchor(val point: Offset, val handle: Offset? = null, val bend: Offset? = null)

/**
 * Basit kalem (pen) aracı: dokunulan yerlere köşe noktası, sürüklenen yerlere eğri noktası eklenir; ilk noktaya
 * dokununca yol kapanır, "Yolu bitir" açık yolu tamamlar. v44: eklenmiş iki nokta arasındaki çizgi parmakla
 * tutulup sürüklenerek eğilir (çizgi parmağın geçtiği yerden geçer); başka nokta düzenlemesi yoktur. Sonuç
 * düzenlenebilir bir vektör şekildir.
 */
class PenController(
    private val store: EditorStore,
    private val host: EditorHost,
    private val shapes: ShapeController,
) {
    private val _anchors = MutableStateFlow<List<PenAnchor>>(emptyList())
    val anchors: StateFlow<List<PenAnchor>> = _anchors.asStateFlow()

    /** Sürüklenirken eklenmekte olan eğri noktası. */
    private val _pending = MutableStateFlow<PenAnchor?>(null)
    val pending: StateFlow<PenAnchor?> = _pending.asStateFlow()

    /** v44 eğilen çizginin başlangıç noktası (sürükleme sürerken). */
    private var bending: Int? = null

    fun start() {
        _anchors.value = emptyList()
        _pending.value = null
        bending = null
        host.startAction(EditMode.PEN)
    }

    /**
     * v44 sürükleme iki nokta arasındaki bir çizginin üstünde mi başladı (noktaların kendisinden uzakta): öyleyse o
     * çizgi eğilir, yeni nokta eklenmez.
     */
    fun grab(point: Offset, tolerance: Float): Boolean {
        val list = _anchors.value
        if (list.size < 2) return false
        // Noktaların üstünde başlayan sürükleme yeni (eğri) nokta ekler
        if (list.any { (it.point - point).getDistance() <= tolerance }) return false
        val seg = PenPath.segmentAt(list, point, tolerance) ?: return false
        bending = seg
        return true
    }

    /** v44 tutulan çizgi parmağın geçtiği yerden geçecek biçimde eğilir ([done]: parmak kalktı). */
    fun editDrag(point: Offset, done: Boolean) {
        val i = bending ?: return
        val list = _anchors.value
        if (i !in list.indices || i + 1 !in list.indices) { bending = null; return }
        val a = list[i].point; val b = list[i + 1].point
        // t = 0,5'te eğri parmaktan geçer: kontrol = 2P − (A + B) / 2
        val control = point * 2f - (a + b) / 2f
        val straight = PenPath.distanceToLine(point, a, b) < 2f
        _anchors.value = list.toMutableList().also { it[i] = it[i].copy(bend = if (straight) null else control) }
        if (done) bending = null
    }

    fun onModeChange(to: EditMode) {
        if (to != EditMode.PEN) {
            // Moddan çıkarken yarım yol tamamlanır (kaybolmasın)
            if (_anchors.value.size >= 2) finish(closed = false, stay = false)
            _anchors.value = emptyList()
            _pending.value = null
            bending = null
        }
    }

    fun tap(point: Offset, tolerance: Float) {
        val list = _anchors.value
        if (list.size >= 2 && (list.first().point - point).getDistance() <= tolerance * 1.8f) {
            finish(closed = true)
            return
        }
        _anchors.value = list + PenAnchor(point)
    }

    /** Sürükleme: [start] noktasına, [current] yönünde kontrol noktalı eğri noktası. */
    fun drag(start: Offset, current: Offset, done: Boolean) {
        val anchor = PenAnchor(start, current.takeIf { (it - start).getDistance() > 2f })
        if (done) {
            _pending.value = null
            val list = _anchors.value
            if (list.size >= 2 && (list.first().point - start).getDistance() < 1f) finish(closed = true)
            else _anchors.value = list + anchor
        } else {
            _pending.value = anchor
        }
    }

    fun undoAnchor() {
        _anchors.value = _anchors.value.dropLast(1).let { l -> if (l.isEmpty()) l else l.dropLast(1) + l.last().copy(bend = null) }
    }

    fun cancelPath() {
        _anchors.value = emptyList()
        _pending.value = null
        bending = null
    }

    fun sample(anchors: List<PenAnchor>, closed: Boolean): List<Offset> = PenPath.sample(anchors, closed)

    /** Yolu tamamlayıp şekil olarak ekler; eklenen yol seçili olur ve yeni yol çizilebilir. */
    fun finish(closed: Boolean, @Suppress("UNUSED_PARAMETER") stay: Boolean = true) {
        val list = _anchors.value
        _anchors.value = emptyList()
        _pending.value = null
        bending = null
        if (list.size < 2) return
        val current = store.canvas.value ?: return
        val (pts, nodes) = PenPath.sampleWithNodes(list, closed)
        val style: ShapeStyle = shapes.newShapeStyle()
        // v16: kalemin noktaları yolun vektör noktalarıdır (şekil düzenlemede aynen görünür)
        val path = ShapePath(if (closed) pts + pts.first() else pts, closed = closed, nodes = nodes)
        val stroke = ShapeFactory.fromPaths(ShapeRegistry.FREE_PATH, listOf(path), style, null, UUID.randomUUID().toString(), current.strokes.size) ?: return
        store.commit(current.strokes + stroke)
        store.select(setOf(stroke.id))
    }
}

/** Kalem noktalarından yol (Bezier eğrileri örneklenerek); tuval önizlemesi de bunu kullanır. */
object PenPath {
    /** v44 [p] noktasına [tolerance] kadar yakın çizginin başlangıç noktasının sırası (yoksa null). */
    fun segmentAt(anchors: List<PenAnchor>, p: Offset, tolerance: Float): Int? {
        var best: Int? = null
        var bestD = tolerance
        for (i in 0 until anchors.size - 1) {
            val pts = sampleWithNodes(listOf(anchors[i], anchors[i + 1].copy(bend = null)), false).first
            for (k in 1 until pts.size) {
                val d = distanceToLine(p, pts[k - 1], pts[k], segment = true)
                if (d < bestD) { bestD = d; best = i }
            }
        }
        return best
    }

    /** Noktanın doğruya (ya da [segment] ise doğru parçasına) uzaklığı. */
    fun distanceToLine(p: Offset, a: Offset, b: Offset, segment: Boolean = false): Float {
        val ab = b - a
        val len2 = ab.x * ab.x + ab.y * ab.y
        if (len2 < 1e-6f) return (p - a).getDistance()
        var t = ((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2
        if (segment) t = t.coerceIn(0f, 1f)
        return (p - (a + ab * t)).getDistance()
    }

    /** Noktalardan yol (Bezier eğrileri örneklenerek). */
    fun sample(anchors: List<PenAnchor>, closed: Boolean): List<Offset> = sampleWithNodes(anchors, closed).first

    /** Örneklenen yol ve kalem noktalarının yoldaki sıraları (vektör noktaları). */
    fun sampleWithNodes(anchors: List<PenAnchor>, closed: Boolean): Pair<List<Offset>, List<Int>> {
        if (anchors.size < 2) return anchors.map { it.point } to anchors.indices.toList()
        val out = ArrayList<Offset>()
        val nodes = arrayListOf(0)
        val segments = if (closed) anchors.size else anchors.size - 1
        out += anchors[0].point
        for (i in 0 until segments) {
            val a = anchors[i]
            val b = anchors[(i + 1) % anchors.size]
            val c1 = a.handle ?: a.point
            val c2 = b.handle?.let { b.point * 2f - it } ?: b.point
            val q = a.bend
            if (q != null) {
                // v44 elle eğilmiş çizgi: ikinci derece Bezier
                val n = 24
                for (k in 1..n) {
                    val t = k / n.toFloat()
                    val m = 1 - t
                    out += a.point * (m * m) + q * (2 * m * t) + b.point * (t * t)
                }
            } else if (a.handle == null && b.handle == null) {
                out += b.point
            } else {
                val n = 24
                for (k in 1..n) {
                    val t = k / n.toFloat()
                    val m = 1 - t
                    out += a.point * (m * m * m) + c1 * (3 * m * m * t) + c2 * (3 * m * t * t) + b.point * (t * t * t)
                }
            }
            nodes += out.size - 1
        }
        if (closed && out.size > 2 && (out.last() - out.first()).getDistance() < 0.5f) out.removeAt(out.size - 1)
        return out to nodes.filter { it < out.size }.distinct()
    }
}
