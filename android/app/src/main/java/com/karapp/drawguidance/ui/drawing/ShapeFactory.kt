package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.GraphSpec
import com.karapp.drawguidance.data.model.ShapeSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.SubPath
import kotlin.math.max

/**
 * Katalogdaki şekilleri tek bir vektör elemana ([BrushType.SHAPE]) çevirir: noktalar alt yollar hâlinde
 * saklanır, biçim ve grafik bilgisi elemanın içindedir. Böylece şekil bir bütün olarak seçilir, taşınır,
 * düzenlenir ve veritabanında vektör olarak durur.
 */
object ShapeFactory {

    fun build(
        kind: ShapeKind,
        start: Offset,
        end: Offset,
        style: ShapeStyle,
        graph: GraphSpec? = null,
        id: String,
        order: Int,
        opacity: Float = 1f,
    ): Stroke? = build(kind.name, start, end, style, graph, id, order, opacity)

    /** Kayıttaki (yerleşik, grafik ya da kütüphane) şekil kimliğiyle kurar; bkz. [ShapeRegistry]. */
    fun build(
        kind: String,
        start: Offset,
        end: Offset,
        style: ShapeStyle,
        graph: GraphSpec? = null,
        id: String,
        order: Int,
        opacity: Float = 1f,
        params: Map<String, String>? = null,
    ): Stroke? {
        val graphSpec = if (ShapeRegistry.isGraph(kind)) (graph ?: ShapeRegistry.defaultGraph(kind)) else graph
        val paths = ShapeRegistry.paths(kind, start, end, max(1f, style.strokeWidth), graphSpec, params)
        return fromPaths(kind, paths, style, graphSpec, id, order, opacity, params)
    }

    fun fromPaths(
        kind: ShapeKind,
        paths: List<ShapePath>,
        style: ShapeStyle,
        graph: GraphSpec?,
        id: String,
        order: Int,
        opacity: Float = 1f,
    ): Stroke? = fromPaths(kind.name, paths, style, graph, id, order, opacity)

    fun fromPaths(
        kind: String,
        paths: List<ShapePath>,
        style: ShapeStyle,
        graph: GraphSpec?,
        id: String,
        order: Int,
        opacity: Float = 1f,
        params: Map<String, String>? = null,
    ): Stroke? {
        val points = ArrayList<StrokePoint>()
        val subpaths = ArrayList<SubPath>()
        val q = ContentCodec::quantize
        for (path in paths) {
            val pts = path.points
            if (path.label != null) {
                if (pts.isEmpty()) continue
                points += StrokePoint(q(pts[0].x), q(pts[0].y))
                subpaths += SubPath(
                    1, label = path.label, labelSize = path.labelSize, strokeColorHex = path.strokeColorHex,
                    alpha = path.alpha, tag = path.tag,
                )
                continue
            }
            // v16: serbest vektör noktası (tek noktalık, çizilmez; çizgiyle bağlanmayı bekler)
            if (pts.size == 1 && path.tag == VectorEdit.VNODE) {
                points += StrokePoint(q(pts[0].x), q(pts[0].y))
                subpaths += SubPath(1, tag = VectorEdit.VNODE, nodes = listOf(0))
                continue
            }
            if (pts.size < 2) continue
            val closed = path.closed && pts.first().x == pts.last().x && pts.first().y == pts.last().y
            val body = if (closed) pts.subList(0, pts.size - 1) else pts
            body.forEach { points += StrokePoint(q(it.x), q(it.y)) }
            subpaths += SubPath(
                body.size, closed = path.closed, dashed = path.dashed,
                strokeColorHex = path.strokeColorHex, fillColorHex = path.fillColorHex,
                widthScale = path.widthScale, dotted = path.dotted, alpha = path.alpha, tag = path.tag,
                nodes = path.nodes?.filter { it in body.indices }?.distinct()?.sorted()?.takeIf { it.isNotEmpty() },
            )
        }
        if (points.size < 2) return null
        return Stroke(
            id = id,
            points = points,
            colorHex = style.strokeColorHex,
            brushType = BrushType.SHAPE,
            sizePx = style.strokeWidth,
            opacity = opacity,
            order = order,
            shape = ShapeSpec(kind, subpaths, style, graph, params = params?.takeIf { it.isNotEmpty() }),
        )
    }

    /** Grafiğin etiketi/formülü ya da biçimi değişince şekli, bulunduğu kutuda yeniden kurar. */
    fun rebuild(stroke: Stroke, style: ShapeStyle, graph: GraphSpec?, params: Map<String, String>? = stroke.shape?.params): Stroke? {
        val spec = stroke.shape ?: return null
        // Eski grafik türleri yeni grafik motoruyla kurulur
        val kind = ShapeRegistry.upgradeGraphKind(spec.kind)
        if (!ShapeRegistry.canBuild(kind)) return null
        val box = frameOf(stroke, spec) ?: return null
        val rebuilt = build(kind, box.topLeft, box.bottomRight, style, graph, stroke.id, stroke.order, stroke.opacity, params) ?: return null
        return rebuilt.copy(groupId = stroke.groupId, clip = stroke.clip, layerId = stroke.layerId ?: "")
    }

    /**
     * Şeklin kurulduğu kutu: çizimin sınırları çoğu zaman kutuyu tam doldurmaz (grafik yazıları, oranı korunan
     * kütüphane şekilleri). Aynı şekil aynı boyutta bir kez daha kurulup içeriğin kutu içindeki payı ölçülür; kutu bu
     * paydan geri hesaplanır. Böylece yeniden kurulan şekil her düzenlemede kaymaz ya da büyümez.
     */
    private fun frameOf(stroke: Stroke, spec: com.karapp.drawguidance.data.model.ShapeSpec): Rect? {
        val sb = bounds(stroke) ?: return null
        if (sb.width < 2f || sb.height < 2f) return sb
        val canon = runCatching {
            build(spec.kind, Offset.Zero, Offset(sb.width, sb.height), spec.style, spec.graph, "frame", 0, params = spec.params)
        }.getOrNull() ?: return sb
        val cb = bounds(canon) ?: return sb
        val fx0 = cb.left / sb.width; val fx1 = cb.right / sb.width
        val fy0 = cb.top / sb.height; val fy1 = cb.bottom / sb.height
        // v45 parametreli şekillerde içerik değerlere göre kutunun küçük bir kısmını kaplayabilir (dik sarkaç, kısa
        // dalga); kutu yine bu paydan hesaplanır, yoksa her değer değişikliğinde şekil küçülürdü
        val minPart = if (ShapeRegistry.paramModel(spec.kind) != null) 0.02f else 0.3f
        if (fx1 - fx0 < minPart || fy1 - fy0 < minPart) return sb
        val bw = sb.width / (fx1 - fx0)
        val bh = sb.height / (fy1 - fy0)
        val left = sb.left - fx0 * bw
        val top = sb.top - fy0 * bh
        return Rect(left, top, left + bw, top + bh)
    }

    /** v45 şeklin kurulduğu kutu (parametreli şekillerin tutamakları bu kutuya göre yerleşir). */
    fun box(stroke: Stroke): Rect? = stroke.shape?.let { frameOf(stroke, it) }

    private fun bounds(stroke: Stroke): Rect? {
        if (stroke.points.isEmpty()) return null
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        stroke.points.forEach { l = minOf(l, it.x); t = minOf(t, it.y); r = maxOf(r, it.x); b = maxOf(b, it.y) }
        return Rect(l, t, r, b)
    }

    /** Elemanın alt yollarını düzenlenebilir listeye açar. */
    fun subPaths(stroke: Stroke): List<EditablePath> {
        val spec = stroke.shape ?: return emptyList()
        val out = ArrayList<EditablePath>()
        var start = 0
        for (sub in spec.subpaths) {
            val end = minOf(stroke.points.size, start + sub.count)
            if (end > start) {
                out += EditablePath(
                    points = stroke.points.subList(start, end).map { Offset(it.x, it.y) },
                    closed = sub.closed,
                    dashed = sub.dashed,
                    label = sub.label,
                    labelSize = sub.labelSize,
                    strokeColorHex = sub.strokeColorHex,
                    fillColorHex = sub.fillColorHex,
                    widthScale = sub.widthScale,
                    dotted = sub.dotted,
                    alpha = sub.alpha,
                    tag = sub.tag,
                    nodes = sub.nodes,
                )
            }
            start = end
        }
        return out
    }

    /** Düzenlenen alt yollardan yeni bir şekil elemanı kurar (boş kalırsa null). */
    fun fromEditable(stroke: Stroke, paths: List<EditablePath>, id: String = stroke.id): Stroke? {
        val spec = stroke.shape ?: return null
        val shapePaths = paths.mapNotNull { p ->
            if (p.label != null) ShapePath(p.points.take(1), label = p.label, labelSize = p.labelSize, strokeColorHex = p.strokeColorHex, alpha = p.alpha, tag = p.tag)
            else if (p.tag == VectorEdit.VNODE && p.points.size == 1) ShapePath(p.points, tag = VectorEdit.VNODE)
            else if (p.points.size >= 2) ShapePath(
                if (p.closed) p.points + p.points.first() else p.points, closed = p.closed, dashed = p.dashed,
                strokeColorHex = p.strokeColorHex, fillColorHex = p.fillColorHex,
                widthScale = p.widthScale, dotted = p.dotted, alpha = p.alpha, tag = p.tag, nodes = p.nodes,
            )
            else null
        }
        return fromPaths(spec.kind, shapePaths, spec.style, spec.graph, id, stroke.order, stroke.opacity, spec.params)
            // Parçalar kaynağın katmanında kalır ("" = ilk katman; etkin katmana taşınmaz)
            ?.copy(groupId = stroke.groupId, clip = stroke.clip, layerId = stroke.layerId ?: "")
    }

    /** Düzenleme sırasında bir alt yol. */
    data class EditablePath(
        val points: List<Offset>,
        val closed: Boolean = false,
        val dashed: Boolean = false,
        val label: String? = null,
        val labelSize: Float = 0f,
        val strokeColorHex: String? = null,
        val fillColorHex: String? = null,
        val widthScale: Float? = null,
        val dotted: Boolean = false,
        val alpha: Float? = null,
        val tag: String? = null,
        /** v16: vektör noktaları (nokta sıraları); nokta sayısı değişince null yapılır ya da güncellenir. */
        val nodes: List<Int>? = null,
    )
}
