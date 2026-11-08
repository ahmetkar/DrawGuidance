package com.karapp.drawguidance.ui.drawing

import android.graphics.Canvas
import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.model.DiagramBox
import com.karapp.drawguidance.data.model.DiagramEdge
import com.karapp.drawguidance.data.model.DiagramSide
import com.karapp.drawguidance.data.diagram.DiagramRouting
import com.karapp.drawguidance.data.diagram.RoutePoint
import com.karapp.drawguidance.data.diagram.RouteRect
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.DiagramNode
import com.karapp.drawguidance.data.model.DiagramSpec
import com.karapp.drawguidance.data.model.Stroke
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Diyagramların çizimi ve yerleşimi. Diyagram 0..1 alanında tanımlanır ve elemanın 4 köşesine eşlenir;
 * böylece taşıma, döndürme ve ölçekleme diğer elemanlarla aynı şekilde çalışır.
 *
 * Yerleşim türe göre otomatik hesaplanır: akış/süreç dikey ya da yatay zincir, organizasyon ağaç,
 * zaman çizelgesi yatay çizgi, durum diyagramı halka, ilişki (Venn) daireler, karşılaştırma iki sütun.
 */
object DiagramRenderer {

    private const val UNIT = 1000f

    /** Yeni bir diyagramın varsayılan düğümleri. */
    fun defaultNodes(kind: DiagramKind): List<DiagramNode> = DiagramCatalog.template(kind)?.nodes ?: when (kind) {
        DiagramKind.FLOW -> listOf(node("Başla", DiagramBox.PILL), node("Adım"), node("Karar", DiagramBox.DIAMOND), node("Bitir", DiagramBox.PILL))
        DiagramKind.PROCESS -> listOf(node("1. Aşama"), node("2. Aşama"), node("3. Aşama"))
        DiagramKind.ARCHITECTURE -> listOf(node("İstemci"), node("Sunucu", role = "focal"), node("Veritabanı", DiagramBox.CYLINDER, "store"))
        DiagramKind.ORG -> listOf(node("Yönetici", role = "hub"), node("Ekip A"), node("Ekip B"), node("Ekip C"))
        DiagramKind.TIMELINE -> listOf(node("Ocak"), node("Mart"), node("Haziran"), node("Eylül"))
        DiagramKind.STATE -> listOf(node("Boşta", DiagramBox.ROUNDED), node("Çalışıyor", DiagramBox.ROUNDED), node("Bitti", DiagramBox.ROUNDED))
        DiagramKind.RELATION -> listOf(node("A", DiagramBox.CIRCLE), node("B", DiagramBox.CIRCLE))
        DiagramKind.COMPARISON -> listOf(node("Seçenek A"), node("Seçenek B"), node("Artı"), node("Eksi"))
        DiagramKind.CYCLE -> listOf(node("Buharlaşma"), node("Yoğuşma"), node("Yağış"), node("Toplanma"))
        DiagramKind.MIND_MAP -> listOf(
            node("Ana konu", DiagramBox.PILL, "hub"), node("Tanım"), node("Özellikler"), node("Örnekler"),
            node("Nedenler"), node("Sonuçlar"), node("Sorular"),
        )
        DiagramKind.PYRAMID -> listOf(
            node("Tatlılar", DiagramBox.TRIANGLE, "focal"), node("Et ve süt", DiagramBox.TRAPEZOID),
            node("Sebze ve meyve", DiagramBox.TRAPEZOID), node("Tahıllar", DiagramBox.TRAPEZOID),
        )
        DiagramKind.VENN -> listOf(node("A", DiagramBox.CIRCLE), node("B", DiagramBox.CIRCLE), node("C", DiagramBox.CIRCLE))
        DiagramKind.MATRIX -> listOf(
            node("Güçlü yönler", DiagramBox.RECT), node("Zayıf yönler", DiagramBox.RECT),
            node("Fırsatlar", DiagramBox.RECT), node("Tehditler", DiagramBox.RECT),
        )
        DiagramKind.FUNNEL -> listOf(
            node("Gözlem", DiagramBox.TRAPEZOID), node("Soru", DiagramBox.TRAPEZOID), node("Hipotez", DiagramBox.TRAPEZOID),
            node("Deney", DiagramBox.TRAPEZOID), node("Sonuç", DiagramBox.TRAPEZOID),
        )
        DiagramKind.FISHBONE -> listOf(
            node("Sonuç", DiagramBox.ROUNDED, "focal"), node("İnsan"), node("Yöntem"), node("Malzeme"), node("Ortam"),
        )
        DiagramKind.CONCEPT_MAP -> listOf(
            node("Canlılar", DiagramBox.PILL, "hub"), node("Bitkiler"), node("Hayvanlar"), node("Mantarlar"),
            node("Çiçekli"), node("Omurgalı"), node("Şapkalı"),
        )
        DiagramKind.TREE -> listOf(
            node("Madde", role = "hub"), node("Saf madde"), node("Karışım"), node("Element"), node("Bileşik"), node("Homojen"), node("Heterojen"),
        )
        DiagramKind.KWL -> listOf(
            node("Biliyorum", DiagramBox.PILL), node("Merak ediyorum", DiagramBox.PILL), node("Öğrendim", DiagramBox.PILL),
            node(" ", DiagramBox.RECT), node(" ", DiagramBox.RECT), node(" ", DiagramBox.RECT),
        )
        DiagramKind.STEPS -> listOf(node("1. Adım"), node("2. Adım"), node("3. Adım"), node("4. Adım", role = "focal"))
        DiagramKind.HUB -> listOf(node("Neden 1"), node("Neden 2"), node("Neden 3"), node("Sonuç", DiagramBox.ROUNDED, "focal"))
        else -> listOf(node("Adım 1"), node("Adım 2"))
    }

    private val counter = java.util.concurrent.atomic.AtomicInteger()
    private fun node(text: String, box: DiagramBox = DiagramBox.ROUNDED, role: String? = null) =
        DiagramNode(id = "n${counter.getAndIncrement()}", text = text, x = 0f, y = 0f, w = 0.3f, h = 0.12f, box = box, role = role)

    /** Türüne göre varsayılan oklar (zincir, ağaç, halka; v28 türlerinde hazır örneğin okları). */
    fun defaultEdges(kind: DiagramKind, nodes: List<DiagramNode>): List<DiagramEdge> = DiagramCatalog.template(kind)?.let { t ->
        val ids = nodes.map { it.id }.toHashSet()
        t.edges.filter { it.from in ids && it.to in ids }
    } ?: when (kind) {
        DiagramKind.ORG -> nodes.drop(1).map { DiagramEdge(nodes.first().id, it.id) }
        DiagramKind.RELATION, DiagramKind.COMPARISON, DiagramKind.PYRAMID, DiagramKind.VENN, DiagramKind.MATRIX,
        DiagramKind.FUNNEL, DiagramKind.FISHBONE, DiagramKind.KWL, DiagramKind.TIMELINE -> emptyList()
        DiagramKind.CYCLE -> nodes.zipWithNext { a, b -> DiagramEdge(a.id, b.id) } +
            listOfNotNull(if (nodes.size > 2) DiagramEdge(nodes.last().id, nodes.first().id) else null)
        DiagramKind.MIND_MAP -> nodes.drop(1).map { DiagramEdge(nodes.first().id, it.id, arrow = false) }
        DiagramKind.CONCEPT_MAP, DiagramKind.TREE -> treeParents(kind, nodes.size).toList().mapIndexedNotNull { i, parent ->
            if (parent < 0) null
            else DiagramEdge(
                nodes[parent].id, nodes[i].id,
                label = if (kind == DiagramKind.CONCEPT_MAP) (if (parent == 0) "içerir" else "örnek") else "",
                arrow = kind == DiagramKind.CONCEPT_MAP,
                fromSide = if (kind == DiagramKind.TREE) DiagramSide.BOTTOM else DiagramSide.AUTO,
                toSide = if (kind == DiagramKind.TREE) DiagramSide.TOP else DiagramSide.AUTO,
            )
        }
        DiagramKind.HUB -> nodes.dropLast(1).map { DiagramEdge(it.id, nodes.last().id) }
        DiagramKind.STATE -> nodes.zipWithNext { a, b -> DiagramEdge(a.id, b.id) } + listOfNotNull(
            if (nodes.size > 2) DiagramEdge(nodes.last().id, nodes.first().id, dashed = true) else null,
        )
        else -> nodes.zipWithNext { a, b -> DiagramEdge(a.id, b.id) }
    }

    /** Düğümleri türe göre 0..1 alanına yerleştirir (yeni düğüm eklendiğinde yeniden çağrılır). */
    fun layout(kind: DiagramKind, nodes: List<DiagramNode>): List<DiagramNode> {
        if (nodes.isEmpty()) return nodes
        if (DiagramCatalog.isNew(kind)) return DiagramCatalog.layout(kind, nodes)
        val n = nodes.size
        return when (kind) {
            DiagramKind.FLOW, DiagramKind.PROCESS, DiagramKind.STATE -> {
                val vertical = kind == DiagramKind.FLOW
                val bw = if (vertical) 0.34f else min(0.23f, 0.94f / n)
                val bh = if (vertical) min(0.11f, 0.94f / n) else 0.12f
                nodes.mapIndexed { i, node ->
                    // Kutular baştan sona eşit aralıkla, alanın içinde kalacak şekilde dizilir
                    val span = if (vertical) bh else bw
                    val t = if (n == 1) 0.5f else (span / 2f + 0.02f) + (0.96f - span) * i / (n - 1f)
                    if (vertical) node.copy(x = 0.5f - bw / 2f, y = t - bh / 2f, w = bw, h = bh)
                    else node.copy(x = t - bw / 2f, y = 0.44f, w = bw, h = bh)
                }
            }
            DiagramKind.ARCHITECTURE -> nodes.mapIndexed { i, node ->
                val cols = if (n <= 3) n else 3
                val row = i / cols
                val col = i % cols
                val rows = (n + cols - 1) / cols
                node.copy(
                    x = 0.06f + col * (0.88f / cols) + 0.02f,
                    y = 0.1f + row * (0.8f / rows),
                    w = 0.88f / cols - 0.04f,
                    h = min(0.2f, 0.62f / rows),
                )
            }
            DiagramKind.ORG -> {
                val children = nodes.drop(1)
                val head = nodes.first().copy(x = 0.35f, y = 0.05f, w = 0.3f, h = 0.12f)
                listOf(head) + children.mapIndexed { i, node ->
                    val m = children.size
                    val w = min(0.28f, 0.92f / m - 0.03f)
                    node.copy(x = 0.04f + i * (0.92f / m) + (0.92f / m - w) / 2f, y = 0.55f, w = w, h = 0.12f)
                }
            }
            DiagramKind.TIMELINE -> nodes.mapIndexed { i, node ->
                val w = min(0.2f, 0.94f / n)
                val t = if (n == 1) 0.5f else (w / 2f + 0.02f) + (0.96f - w) * i / (n - 1f)
                node.copy(x = t - w / 2f, y = if (i % 2 == 0) 0.2f else 0.62f, w = w, h = 0.12f, box = DiagramBox.NOTE)
            }
            DiagramKind.RELATION -> nodes.mapIndexed { i, node ->
                val m = max(2, n)
                val r = 0.3f
                val angle = -Math.PI / 2 + i * 2 * Math.PI / m
                node.copy(
                    x = 0.5f + (if (m == 2) (if (i == 0) -0.22f else 0.22f) else (r * cos(angle)).toFloat()) - r / 2f,
                    y = 0.5f + (if (m == 2) 0f else (r * sin(angle)).toFloat()) - r / 2f,
                    w = r, h = r, box = DiagramBox.CIRCLE,
                )
            }
            DiagramKind.COMPARISON -> nodes.mapIndexed { i, node ->
                val col = i % 2
                val row = i / 2
                val rows = (n + 1) / 2
                node.copy(x = 0.06f + col * 0.48f, y = 0.08f + row * (0.84f / rows), w = 0.4f, h = min(0.16f, 0.7f / rows))
            }
            DiagramKind.CYCLE -> nodes.mapIndexed { i, node ->
                val w = min(0.3f, 1.6f / (n + 2)); val h = min(0.16f, 0.9f / (n + 2))
                val a = -Math.PI / 2 + i * 2 * Math.PI / n
                node.copy(x = 0.5f + 0.36f * cos(a).toFloat() - w / 2f, y = 0.5f + 0.36f * sin(a).toFloat() - h / 2f, w = w, h = h)
            }
            DiagramKind.MIND_MAP -> {
                val center = nodes.first().copy(x = 0.36f, y = 0.42f, w = 0.28f, h = 0.16f)
                val rest = nodes.drop(1)
                val m = max(1, rest.size)
                listOf(center) + rest.mapIndexed { i, node ->
                    val a = -Math.PI / 2 + (i + 0.5) * 2 * Math.PI / m
                    val w = 0.22f; val h = 0.11f
                    node.copy(
                        x = (0.5f + 0.37f * cos(a).toFloat() - w / 2f).coerceIn(0.01f, 0.99f - w),
                        y = (0.5f + 0.38f * sin(a).toFloat() - h / 2f).coerceIn(0.01f, 0.99f - h), w = w, h = h,
                    )
                }
            }
            DiagramKind.PYRAMID -> nodes.mapIndexed { i, node ->
                val h = 0.92f / n
                val w = 0.92f * (i + 1) / n
                node.copy(x = 0.5f - w / 2f, y = 0.04f + i * h, w = w, h = h, box = if (i == 0) DiagramBox.TRIANGLE else DiagramBox.TRAPEZOID)
            }
            DiagramKind.FUNNEL -> nodes.mapIndexed { i, node ->
                val h = 0.92f / n
                val w = 0.94f * (n - i) / n
                node.copy(x = 0.5f - w / 2f, y = 0.04f + i * h, w = w, h = h - 0.01f, box = DiagramBox.TRAPEZOID)
            }
            DiagramKind.VENN -> nodes.mapIndexed { i, node ->
                val r = if (n <= 2) 0.3f else 0.26f
                val (cx, cy) = when {
                    n <= 2 -> (if (i == 0) 0.38f else 0.62f) to 0.5f
                    n == 3 -> listOf(0.38f to 0.38f, 0.62f to 0.38f, 0.5f to 0.6f)[i]
                    else -> {
                        val a = -Math.PI / 2 + i * 2 * Math.PI / n
                        (0.5f + 0.16f * cos(a).toFloat()) to (0.5f + 0.16f * sin(a).toFloat())
                    }
                }
                node.copy(x = cx - r, y = cy - r, w = r * 2, h = r * 2, box = DiagramBox.CIRCLE)
            }
            DiagramKind.MATRIX -> {
                val cols = if (n <= 4) 2 else 3
                val rows = (n + cols - 1) / cols
                nodes.mapIndexed { i, node ->
                    val w = 0.94f / cols; val h = 0.94f / rows
                    node.copy(x = 0.03f + (i % cols) * w, y = 0.03f + (i / cols) * h, w = w - 0.01f, h = h - 0.01f)
                }
            }
            DiagramKind.FISHBONE -> {
                val head = nodes.first().copy(x = 0.76f, y = 0.4f, w = 0.22f, h = 0.2f)
                val causes = nodes.drop(1)
                val pairs = (causes.size + 1) / 2
                listOf(head) + causes.mapIndexed { i, node ->
                    val col = i / 2
                    val top = i % 2 == 0
                    val w = min(0.22f, 0.66f / pairs - 0.02f)
                    node.copy(x = 0.04f + col * (0.68f / pairs), y = if (top) 0.06f else 0.8f, w = w, h = 0.14f)
                }
            }
            DiagramKind.CONCEPT_MAP, DiagramKind.TREE -> {
                val parents = treeParents(kind, n)
                val depth = IntArray(n)
                for (i in 1 until n) depth[i] = depth[parents[i].coerceAtLeast(0)] + 1
                val levels = (depth.maxOrNull() ?: 0) + 1
                val h = min(0.14f, 0.8f / levels)
                nodes.mapIndexed { i, node ->
                    val sameLevel = (0 until n).filter { depth[it] == depth[i] }
                    val k = sameLevel.indexOf(i)
                    val m = sameLevel.size
                    val w = min(0.3f, 0.96f / m - 0.02f)
                    val slot = 0.98f / m
                    node.copy(
                        x = 0.01f + k * slot + (slot - w) / 2f,
                        y = 0.04f + depth[i] * ((0.92f - h) / max(1, levels - 1)),
                        w = w, h = h,
                    )
                }
            }
            DiagramKind.KWL -> nodes.mapIndexed { i, node ->
                val col = i % 3
                val header = i < 3
                val row = i / 3
                val bodyRows = max(1, (n - 3 + 2) / 3)
                val w = 0.31f
                if (header) node.copy(x = 0.02f + col * 0.33f, y = 0.03f, w = w, h = 0.13f)
                else node.copy(x = 0.02f + col * 0.33f, y = 0.19f + (row - 1) * (0.78f / bodyRows), w = w, h = 0.78f / bodyRows - 0.02f)
            }
            DiagramKind.STEPS -> nodes.mapIndexed { i, node ->
                val w = min(0.3f, 0.96f / n - 0.02f)
                val h = 0.16f
                val x = 0.02f + i * ((0.96f - w) / max(1, n - 1))
                val y = 0.8f - h - i * ((0.76f - h) / max(1, n - 1))
                node.copy(x = x, y = y, w = w, h = h)
            }
            DiagramKind.HUB -> {
                val effect = nodes.last().copy(x = 0.64f, y = 0.4f, w = 0.32f, h = 0.2f)
                val causes = nodes.dropLast(1)
                val m = max(1, causes.size)
                causes.mapIndexed { i, node ->
                    val h = min(0.16f, 0.9f / m - 0.03f)
                    node.copy(x = 0.04f, y = 0.05f + i * (0.9f / m) + (0.9f / m - h) / 2f, w = 0.34f, h = h)
                } + effect
            }
            else -> DiagramCatalog.layout(kind, nodes)
        }
    }

    /**
     * Ağaç türlerinde (kavram haritası, sınıflandırma) her düğümün ebeveyni (-1: kök): kök, ardından ikinci seviye
     * (kavram haritasında 3, ağaçta 2 düğüm), kalanlar sırayla ikinci seviyeye dağıtılır.
     */
    fun treeParents(kind: DiagramKind, n: Int): IntArray {
        val parents = IntArray(n) { -1 }
        if (n <= 1) return parents
        val second = min(n - 1, if (kind == DiagramKind.TREE) 2 else 3)
        for (i in 1..second) parents[i] = 0
        for (i in second + 1 until n) parents[i] = 1 + (i - second - 1) % second
        return parents
    }

    /** Diyagram alanındaki (0..1) bir noktanın tuval koordinatı. */
    fun toCanvas(stroke: Stroke, ux: Float, uy: Float): Offset? {
        val m = matrix(stroke) ?: return null
        val f = stroke.diagram?.frame ?: com.karapp.drawguidance.data.model.DiagramFrame()
        // Tasarım alanı → çerçeveye göre 0..1 → dörtgen
        val pts = floatArrayOf((ux - f.left) / f.width * UNIT, (uy - f.top) / f.height * UNIT)
        m.mapPoints(pts)
        return Offset(pts[0], pts[1])
    }

    /** Tuvaldeki bir noktanın diyagram alanındaki (0..1) karşılığı. */
    fun toUnit(stroke: Stroke, p: Offset): Offset? {
        val m = matrix(stroke) ?: return null
        val inv = Matrix()
        if (!m.invert(inv)) return null
        val pts = floatArrayOf(p.x, p.y)
        inv.mapPoints(pts)
        val f = stroke.diagram?.frame ?: com.karapp.drawguidance.data.model.DiagramFrame()
        return Offset(f.left + pts[0] / UNIT * f.width, f.top + pts[1] / UNIT * f.height)
    }

    /** Düğümün tuvaldeki dört köşesi (sol-üst, sağ-üst, sağ-alt, sol-alt). */
    fun nodeCorners(stroke: Stroke, node: DiagramNode): List<Offset> = listOfNotNull(
        toCanvas(stroke, node.x, node.y), toCanvas(stroke, node.x + node.w, node.y),
        toCanvas(stroke, node.x + node.w, node.y + node.h), toCanvas(stroke, node.x, node.y + node.h),
    )

    /** Tuvaldeki [p] noktasındaki düğüm. */
    fun nodeAt(stroke: Stroke, p: Offset): DiagramNode? {
        val spec = stroke.diagram ?: return null
        val u = toUnit(stroke, p) ?: return null
        return spec.nodes.lastOrNull { u.x in it.x..(it.x + it.w) && u.y in it.y..(it.y + it.h) }
    }

    /** Tuvaldeki [p] noktasına [tolerance] (tuval birimi) içinde en yakın okun sırası (okun gerçek yolu üzerinden). */
    fun edgeAt(stroke: Stroke, p: Offset, tolerance: Float): Int? {
        val spec = stroke.diagram ?: return null
        val nodes = spec.nodes.associateBy { it.id }
        var best: Int? = null
        var bestDistance = Float.MAX_VALUE
        spec.edges.forEachIndexed { i, edge ->
            val a = nodes[edge.from] ?: return@forEachIndexed
            val b = nodes[edge.to] ?: return@forEachIndexed
            val path = edgePath(stroke, edge, a, b)
            if (path.size < 2) return@forEachIndexed
            var d = Float.MAX_VALUE
            for (k in 1 until path.size) d = min(d, segmentDistance(p, path[k - 1], path[k]))
            if (d < bestDistance) { bestDistance = d; best = i }
        }
        return best?.takeIf { bestDistance <= tolerance }
    }

    /** Okun tuvaldeki yolu (dik açılı ya da düz). */
    fun edgePath(stroke: Stroke, edge: DiagramEdge, from: DiagramNode, to: DiagramNode): List<Offset> {
        val spec = stroke.diagram ?: return emptyList()
        val index = spec.edges.indexOf(edge)
        return routeOf(spec, index, edge, from, to).mapNotNull { toCanvas(stroke, it.x / UNIT, it.y / UNIT) }
    }

    /** Düğümün bir tarafının orta noktası (tuvalde): ok tutamakları burada çizilir. */
    fun portPoint(stroke: Stroke, node: DiagramNode, side: DiagramSide): Offset? {
        val p = DiagramRouting.port(DiagramRouting.rectOf(node), side)
        return toCanvas(stroke, p.x, p.y)
    }

    /**
     * [p] noktasına [tolerance] içinde en yakın ok tutamağı (düğüm ve taraf). [only] verilirse yalnızca o düğümün
     * tutamaklarına bakılır.
     */
    fun portAt(stroke: Stroke, p: Offset, tolerance: Float, only: String? = null): Pair<DiagramNode, DiagramSide>? {
        val spec = stroke.diagram ?: return null
        var best: Pair<DiagramNode, DiagramSide>? = null
        var bestDistance = Float.MAX_VALUE
        spec.nodes.forEach { node ->
            if (only != null && node.id != only) return@forEach
            Sides.forEach { side ->
                val q = portPoint(stroke, node, side) ?: return@forEach
                val d = (q - p).getDistance()
                if (d < bestDistance) { bestDistance = d; best = node to side }
            }
        }
        return best?.takeIf { bestDistance <= tolerance }
    }

    /**
     * Düğümde dokunulan yere göre taraf (okun istenen tarafa bağlanması için): kenara yakın dokunuş o tarafı,
     * ortaya dokunuş otomatik tarafı verir.
     */
    fun sideNear(stroke: Stroke, node: DiagramNode, p: Offset): DiagramSide {
        val u = toUnit(stroke, p) ?: return DiagramSide.AUTO
        return DiagramRouting.sideAt(DiagramRouting.rectOf(node), com.karapp.drawguidance.data.diagram.RoutePoint(u.x, u.y))
    }

    /** Ok tutamaklarının tarafları (otomatik hariç). */
    val Sides = listOf(DiagramSide.TOP, DiagramSide.RIGHT, DiagramSide.BOTTOM, DiagramSide.LEFT)

    private fun segmentDistance(p: Offset, a: Offset, b: Offset): Float {
        val vx = b.x - a.x; val vy = b.y - a.y
        val len2 = vx * vx + vy * vy
        val t = if (len2 < 1e-6f) 0f else (((p.x - a.x) * vx + (p.y - a.y) * vy) / len2).coerceIn(0f, 1f)
        return (p - Offset(a.x + vx * t, a.y + vy * t)).getDistance()
    }

    /** Elemanın dörtgenine eşleyen matris (yazı elemanıyla aynı mantık). */
    private fun matrix(stroke: Stroke): Matrix? {
        if (stroke.points.size < 4) return null
        val src = floatArrayOf(0f, 0f, UNIT, 0f, UNIT, UNIT, 0f, UNIT)
        val dst = FloatArray(8)
        for (i in 0 until 4) {
            dst[i * 2] = stroke.points[i].x
            dst[i * 2 + 1] = stroke.points[i].y
        }
        val m = Matrix()
        return if (m.setPolyToPoly(src, 0, dst, 0, 4)) m else null
    }

    /**
     * Diyagramı çizer. [labels] false ise yazılar çizilmez (küçük resimler). Çizim [DiagramPainter]'da: editoryal
     * temada kağıt zemin ve rol temelli kutular, diğer temalarda temanın renkleri; ikisinde de aynı çizgi dili.
     */
    fun draw(canvas: Canvas, stroke: Stroke, offsetX: Float = 0f, offsetY: Float = 0f, coverage: Boolean = false, labels: Boolean = true) {
        val spec = stroke.diagram ?: return
        val m = matrix(stroke) ?: return
        val save = canvas.save()
        if (offsetX != 0f || offsetY != 0f) canvas.translate(offsetX, offsetY)
        // Görünür alanın dışındaki diyagramlar çizilmez
        val clip = android.graphics.Rect()
        if (canvas.getClipBounds(clip)) {
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
            stroke.points.forEach { l = min(l, it.x); t = min(t, it.y); r = max(r, it.x); b = max(b, it.y) }
            if (r < clip.left || l > clip.right || b < clip.top || t > clip.bottom) {
                canvas.restoreToCount(save)
                return
            }
        }
        canvas.concat(m)
        // Çerçeve büyüdüyse tasarım alanı dörtgene sığdırılır (adımların tuvaldeki yeri değişmez)
        val frame = spec.frame
        if (!frame.isDefault && frame.width > 0f && frame.height > 0f) {
            canvas.scale(1f / frame.width, 1f / frame.height)
            canvas.translate(-frame.left * UNIT, -frame.top * UNIT)
        }
        DiagramPainter.paint(canvas, spec, coverage, aspectK(stroke), labels)
        canvas.restoreToCount(save)
    }

    /**
     * Yatay sıkıştırma: tasarım alanında bir birimin ekrandaki dikey boyunun yatay boyuna oranı. Geniş diyagramda
     * yazı ve noktalar bu oranla daraltılır, böylece harfler ve daireler bozulmaz.
     */
    fun aspectK(stroke: Stroke): Float {
        val pts = stroke.points
        if (pts.size < 4) return 1f
        val f = stroke.diagram?.frame ?: com.karapp.drawguidance.data.model.DiagramFrame()
        val w = kotlin.math.hypot(pts[1].x - pts[0].x, pts[1].y - pts[0].y) / max(1e-3f, f.width)
        val h = kotlin.math.hypot(pts[3].x - pts[0].x, pts[3].y - pts[0].y) / max(1e-3f, f.height)
        if (w < 1e-3f || h < 1e-3f) return 1f
        return (h / w).coerceIn(0.2f, 5f)
    }

    // ------------------------------------------------------------------------------------------ ok yolları

    /** Sıra diyagramında etkinlik çubuğunun yarı genişliği (tasarım birimi). */
    const val ACTIVATION = 7f

    /** Sıra diyagramında [index]. mesajın yüksekliği (tasarım birimi). */
    fun sequenceY(spec: DiagramSpec, index: Int): Float {
        val top = ((spec.nodes.maxOfOrNull { it.y + it.h } ?: 0.12f) + 0.05f) * UNIT
        val m = max(1, spec.edges.size)
        val step = min(0.12f * UNIT, (0.96f * UNIT - top) / m)
        return top + step * (index + 0.5f)
    }

    /** Wardley / dörtlü konum noktalarında ok, yazının değil noktanın kutusuna bağlanır. */
    private fun anchorRect(n: DiagramNode): RouteRect {
        if (n.box == DiagramBox.DOT && n.w > n.h * 1.2f) {
            val cx = (n.x + n.h * 0.35f) * UNIT; val cy = (n.y + n.h / 2f) * UNIT; val r = n.h * 0.22f * UNIT
            return RouteRect(cx - r, cy - r, cx + r, cy + r)
        }
        return DiagramRouting.rectOf(n, UNIT)
    }

    /** Taraf seçilmemiş dirsekli okun tarafları: kutular dikeyde ayrıksa alt→üst, yatayda ayrıksa sağ→sol. */
    private fun autoSides(a: RouteRect, b: RouteRect): Pair<DiagramSide, DiagramSide> {
        val gapX = max(b.left - a.right, a.left - b.right)
        val gapY = max(b.top - a.bottom, a.top - b.bottom)
        return if (gapY >= gapX) {
            if (b.centerY > a.centerY) DiagramSide.BOTTOM to DiagramSide.TOP else DiagramSide.TOP to DiagramSide.BOTTOM
        } else {
            if (b.centerX > a.centerX) DiagramSide.RIGHT to DiagramSide.LEFT else DiagramSide.LEFT to DiagramSide.RIGHT
        }
    }

    /**
     * Okun tasarım alanındaki (0..[UNIT]) yolu; çizim, dokunma ve panel konumu aynı yolu kullanır. Sıra diyagramında
     * yatay mesaj, döngülerde çember yayı, madalyon katmanlarında üstten kavis, Sankey'de şeridin orta çizgisi;
     * diğerlerinde düz ya da dik açılı yol.
     */
    fun routeOf(spec: DiagramSpec, index: Int, edge: DiagramEdge, a: DiagramNode, b: DiagramNode): List<RoutePoint> {
        val kind = spec.kind
        return when {
            kind == DiagramKind.SEQUENCE && index >= 0 -> {
                val y = sequenceY(spec, index)
                val ax = (a.x + a.w / 2f) * UNIT; val bx = (b.x + b.w / 2f) * UNIT
                val dir = if (bx >= ax) 1f else -1f
                val sa = if (a.role == "focal") ACTIVATION else 0f
                val sb = if (b.role == "focal") ACTIVATION else 0f
                listOf(RoutePoint(ax + dir * sa, y), RoutePoint(bx - dir * sb, y))
            }
            edge.role == "arc" -> arcOver(a, b)
            kind == DiagramKind.SANKEY && index >= 0 -> {
                val r = DiagramCatalog.ribbons(spec.nodes, spec.edges)[index]
                if (r == null) DiagramRouting.route(edge, a, b, UNIT) else {
                    val x0 = r.x0 * UNIT; val x1 = r.x1 * UNIT; val y0 = (r.y0 + r.h / 2f) * UNIT; val y1 = (r.y1 + r.h / 2f) * UNIT
                    (0..16).map { i ->
                        val t = i / 16f; val u = 1f - t
                        val mx = (x0 + x1) / 2f
                        RoutePoint(u * u * u * x0 + 3 * u * u * t * mx + 3 * u * t * t * mx + t * t * t * x1, u * u * u * y0 + 3 * u * u * t * y0 + 3 * u * t * t * y1 + t * t * t * y1)
                    }
                }
            }
            (kind == DiagramKind.LOOP || kind == DiagramKind.CYCLE) && edge.role != "spoke" &&
                edge.fromSide == DiagramSide.AUTO && edge.toSide == DiagramSide.AUTO && a.role != "hub" && b.role != "hub" ->
                ringArc(a, b) ?: DiagramRouting.route(edge, a, b, UNIT)
            else -> {
                val ra = anchorRect(a); val rb = anchorRect(b)
                var fs = edge.fromSide; var ts = edge.toSide
                if (fs == DiagramSide.AUTO && ts == DiagramSide.AUTO) {
                    if (!DiagramCatalog.elbow(kind)) {
                        val straight = DiagramRouting.route(ra, rb, fs, ts, 0.03f * UNIT)
                        // Düz ok başka bir adımın içinden geçiyorsa (durum diyagramının geri oku) dirsekle, o da
                        // geçiyorsa kutuların altından dolaşır
                        if (kind in DiagramCatalog.straightKinds || !crossesOthers(spec, straight, a, b)) return straight
                        val (sa, sb) = autoSides(ra, rb)
                        val elbow = DiagramRouting.route(ra, rb, sa, sb, 0.03f * UNIT)
                        if (!crossesOthers(spec, elbow, a, b)) return elbow
                        return DiagramRouting.route(ra, rb, DiagramSide.BOTTOM, DiagramSide.BOTTOM, 0.05f * UNIT)
                    }
                    val sides = autoSides(ra, rb)
                    fs = sides.first; ts = sides.second
                }
                DiagramRouting.route(ra, rb, fs, ts, 0.03f * UNIT)
            }
        }
    }

    /** Yol [a] ve [b] dışındaki bir adımın (kapsayıcılar ve yazılar hariç) içinden geçiyor mu. */
    private fun crossesOthers(spec: DiagramSpec, route: List<RoutePoint>, a: DiagramNode, b: DiagramNode): Boolean {
        val skip = setOf(DiagramBox.GROUP, DiagramBox.LANE, DiagramBox.COLUMN, DiagramBox.LABEL)
        return spec.nodes.any { n ->
            if (n.id == a.id || n.id == b.id || n.box in skip) return@any false
            val r = DiagramRouting.rectOf(n, UNIT)
            val inset = min(r.width, r.height) * 0.08f
            (1 until route.size).any { i -> segmentHits(route[i - 1], route[i], r.left + inset, r.top + inset, r.right - inset, r.bottom - inset) }
        }
    }

    /** Parça dikdörtgeni kesiyor mu (Liang–Barsky). */
    private fun segmentHits(p: RoutePoint, q: RoutePoint, l: Float, t: Float, r: Float, b: Float): Boolean {
        var t0 = 0f; var t1 = 1f
        val dx = q.x - p.x; val dy = q.y - p.y
        val ps = floatArrayOf(-dx, dx, -dy, dy)
        val qs = floatArrayOf(p.x - l, r - p.x, p.y - t, b - p.y)
        for (i in 0 until 4) {
            if (abs(ps[i]) < 1e-6f) { if (qs[i] < 0f) return false; continue }
            val u = qs[i] / ps[i]
            if (ps[i] < 0f) { if (u > t1) return false; if (u > t0) t0 = u } else { if (u < t0) return false; if (u < t1) t1 = u }
        }
        return t0 <= t1
    }

    /** Madalyon katmanları: kartların üstünden kavis (yazısı tepede). */
    private fun arcOver(a: DiagramNode, b: DiagramNode): List<RoutePoint> {
        val x0 = (a.x + a.w / 2f) * UNIT; val y0 = a.y * UNIT
        val x1 = (b.x + b.w / 2f) * UNIT; val y1 = b.y * UNIT
        val rise = max(0.07f * UNIT, abs(x1 - x0) * 0.32f)
        val cy = min(y0, y1) - rise
        val cx = (x0 + x1) / 2f
        return (0..20).map { i ->
            val t = i / 20f; val u = 1f - t
            RoutePoint(u * u * x0 + 2 * u * t * cx + t * t * x1, u * u * y0 + 2 * u * t * cy + t * t * y1)
        }
    }

    /** Döngü: iki istasyon arasında çember yayı (kısa yön), kutuların içinde kalan kısım atılır. */
    private fun ringArc(a: DiagramNode, b: DiagramNode): List<RoutePoint>? {
        val cx = 0.5f * UNIT; val cy = 0.5f * UNIT
        val ax = (a.x + a.w / 2f) * UNIT - cx; val ay = (a.y + a.h / 2f) * UNIT - cy
        val bx = (b.x + b.w / 2f) * UNIT - cx; val by = (b.y + b.h / 2f) * UNIT - cy
        val ra = kotlin.math.hypot(ax, ay); val rb = kotlin.math.hypot(bx, by)
        if (ra < 0.1f * UNIT || rb < 0.1f * UNIT) return null
        val r = (ra + rb) / 2f
        val ta = atan2(ay, ax).toDouble(); var tb = atan2(by, bx).toDouble()
        var d = tb - ta
        while (d <= -Math.PI) d += 2 * Math.PI
        while (d > Math.PI) d -= 2 * Math.PI
        tb = ta + d
        val ea = DiagramRouting.rectOf(a, UNIT); val eb = DiagramRouting.rectOf(b, UNIT)
        val m = 6f
        fun inside(px: Float, py: Float, q: RouteRect) = px > q.left - m && px < q.right + m && py > q.top - m && py < q.bottom + m
        val pts = (0..48).map { i ->
            val t = ta + (tb - ta) * i / 48.0
            RoutePoint(cx + r * cos(t).toFloat(), cy + r * sin(t).toFloat())
        }.filter { !inside(it.x, it.y, ea) && !inside(it.x, it.y, eb) }
        return pts.takeIf { it.size >= 2 }
    }
}
