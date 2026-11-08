package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.drawing.ShapeFactory.EditablePath
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * v16 vektör düzenleme: şekil, **vektör noktaları** ve aralarındaki **çizgiler** olarak ele alınır.
 *
 * - Her parçanın (alt yol) vektör noktaları [SubPath.nodes] ile saklanır; yoksa belirgin noktalar (köşeler, eğrinin
 *   dönüm yerleri) bulunur. İki vektör noktası arasındaki ara noktalar o çizginin kavisidir.
 * - Aynı yerdeki noktalar (farklı parçalarda da olsa) tek vektör noktasıdır: nokta taşınınca ona bağlı tüm çizgiler
 *   birlikte gelir (kavisli çizgiler kırılmadan esner).
 * - İşlemler: nokta taşıma, çizgiye nokta ekleme, serbest nokta, iki nokta arasına çizgi (açık uçlar birleşir, aynı
 *   yolun iki ucu bağlanınca yol kapanır), çizgiye kavis verme / düzleştirme, çizginin eğimini (açısını) değiştirme,
 *   seçili noktaları bağlı çizgilerle birlikte kesme (ayrı parça) ya da silme.
 *
 * Hepsi saf işlemlerdir: girdi değişmez, sonuç yeni parça listesidir ([ShapeFactory.fromEditable] ile şekle döner).
 */
object VectorEdit {

    /** Serbest vektör noktasının (tek noktalık, çizilmeyen parça) etiketi. */
    const val VNODE = "vnode"

    /** v17: alan boyama dolgusu (çizgilerle çevrili alanın rengi; vektör düzenlemede görünmez, çizgiler değişince yenilenir). */
    const val REGION = "region"
    /** v45 çocuk boya fırçasının şekle eklediği boya parçası (bkz. KidsBrush.TAG). */
    const val BRUSH_PAINT = "kbrush"

    /** Bu uzaklıktaki (tuval birimi) noktalar aynı vektör noktası sayılır. */
    const val MERGE = 0.6f

    /** Bu kadar ya da daha az noktalı parçada her nokta vektör noktasıdır. */
    private const val ALL_NODES = 16

    /** v36 basit düzenleme (basit çizim türleri): bir parçada en çok bu kadar vektör noktası gösterilir (v37 8 → 6). */
    const val SIMPLE_NODES = 6

    /**
     * v37 basit düzenlemede bir şeklin bütün parçalarında toplam nokta hedefi: çok parçalı şekilde parça başına sınır
     * küçülür (kapalı parçada en az 3, açıkta 2 nokta).
     */
    const val SIMPLE_TOTAL = 16

    /** v36 basit düzenlemede belirgin nokta eşiği (köşegenin oranı; olağan düzenlemede 0.02; v37 0.04 → 0.06). */
    private const val COARSE_EPS = 0.06f

    /** Kavis verilen çizginin örnek sayısı. */
    private const val BEND_STEPS = 16

    /** Bir parçadaki nokta: [path] parçasının [index] noktası. */
    data class Ref(val path: Int, val index: Int)

    /** Vektör noktası: konumu ve bu konumdaki parça noktaları. */
    class Node(val id: Int, val point: Offset, val refs: List<Ref>)

    /**
     * İki vektör noktası ([a], [b]) arasındaki çizgi: [path] parçasının [from]..[to] noktaları (kapalı parçanın son
     * çizgisi sona sarıp başa döner). [points] uçlar dahil çizginin noktalarıdır.
     */
    class Edge(val id: Int, val path: Int, val from: Int, val to: Int, val a: Int, val b: Int, val points: List<Offset>) {
        val start: Offset get() = points.first()
        val end: Offset get() = points.last()
        val curved: Boolean get() = points.size > 2

        /** Yay uzunluğunun ortası (kavis tutamağının yeri). */
        fun middle(): Offset {
            val total = length()
            if (total < 1e-4f) return (start + end) / 2f
            var left = total / 2f
            for (i in 0 until points.size - 1) {
                val seg = (points[i + 1] - points[i]).getDistance()
                if (seg >= left && seg > 0f) return points[i] + (points[i + 1] - points[i]) * (left / seg)
                left -= seg
            }
            return end
        }

        fun length(): Float = (0 until points.size - 1).sumOf { (points[it + 1] - points[it]).getDistance().toDouble() }.toFloat()

        /** Çizginin (uçtan uca) eğimi, derece: 0 yatay, 90 aşağı doğru dikey. */
        fun angle(): Float = Math.toDegrees(atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())).toFloat()
    }

    /** Dokunulan çizgi: çizgi üstündeki en yakın nokta ve oraya eklenecek noktanın parçadaki sırası. */
    class EdgeHit(val edge: Edge, val point: Offset, val distance: Float, val insertAt: Int)

    /** Şeklin vektör görünümü. [paths] vektör noktaları yazılmış (dondurulmuş) parçalardır; işlemler bunlarla yapılır. */
    class Graph(
        val paths: List<EditablePath>,
        val nodeIndices: List<List<Int>>,
        val nodes: List<Node>,
        val edges: List<Edge>,
    ) {
        private val refNode = HashMap<Ref, Int>().also { map -> nodes.forEach { n -> n.refs.forEach { map[it] = n.id } } }

        fun nodeOf(ref: Ref): Int? = refNode[ref]

        /** [p] noktasına [tolerance] içindeki en yakın vektör noktası. */
        fun nodeAt(p: Offset, tolerance: Float, exclude: Int? = null): Node? {
            var best: Node? = null
            var bestD = tolerance
            nodes.forEach { n ->
                if (n.id == exclude) return@forEach
                val d = (n.point - p).getDistance()
                if (d <= bestD) { bestD = d; best = n }
            }
            return best
        }

        /** Bu konumdaki vektör noktası (kayıt yuvarlaması payıyla). */
        fun nodeNear(p: Offset): Node? = nodeAt(p, MERGE)

        /** [p] noktasına [tolerance] içindeki en yakın çizgi. */
        fun edgeAt(p: Offset, tolerance: Float): EdgeHit? {
            var best: EdgeHit? = null
            edges.forEach { e ->
                val n = paths[e.path].points.size
                for (s in 0 until e.points.size - 1) {
                    val proj = project(p, e.points[s], e.points[s + 1])
                    val d = (proj - p).getDistance()
                    if (d <= tolerance && (best == null || d < best!!.distance)) {
                        val idx = (e.from + s) % n
                        best = EdgeHit(e, proj, d, idx + 1)
                    }
                }
            }
            return best
        }

        /** Uçları [a] ve [b] olan çizgi (yönü önemsiz). */
        fun edgeBetween(a: Offset, b: Offset): Edge? = edges.firstOrNull {
            (near(it.start, a) && near(it.end, b)) || (near(it.start, b) && near(it.end, a))
        }

        /** Noktaya bağlı çizgi sayısı. */
        fun degree(node: Int): Int = edges.count { it.a == node || it.b == node }

        /** Serbest (hiçbir çizgiye bağlı olmayan) nokta mı. */
        fun isFree(node: Int): Boolean = nodes.getOrNull(node)?.refs?.all { paths[it.path].tag == VNODE } == true
    }

    // --- Görünüm ---

    fun graph(stroke: Stroke, coarse: Boolean = false): Graph = graph(ShapeFactory.subPaths(stroke), diagonal(stroke), coarse)

    /**
     * Parçalardan vektör görünümü; vektör noktası yazılmamış parçalarda belirgin noktalar bulunur. v36 [coarse]: basit
     * düzenleme — belirgin noktalar daha seyrek ve parça başına en çok [SIMPLE_NODES] (yazılı noktalar olduğu gibi kalır).
     */
    fun graph(raw: List<EditablePath>, diagonal: Float = 0f, coarse: Boolean = false): Graph {
        val eps = max(0.8f, diagonal * if (coarse) COARSE_EPS else 0.02f)
        // v37 çok parçalı şekilde parça başına sınır küçülür (toplam [SIMPLE_TOTAL] civarı)
        val parts = if (coarse) raw.count { editable(it) && it.tag != VNODE && it.points.size >= 2 }.coerceAtLeast(1) else 1
        val limit = if (coarse) (SIMPLE_TOTAL / parts).coerceIn(2, SIMPLE_NODES) else 0
        val paths = ArrayList<EditablePath>(raw.size)
        val indices = ArrayList<List<Int>>(raw.size)
        raw.forEach { p ->
            var ns = nodeIndices(p, eps, if (limit > 0 && p.closed) max(3, limit) else limit)
            var q = p
            if (ns.isNotEmpty() && editable(p)) {
                // Kapalı parçada ilk nokta vektör noktası olsun (son çizgi başa sarar)
                if (p.closed && ns[0] != 0) {
                    val r = ns[0]
                    q = q.copy(points = p.points.drop(r) + p.points.take(r))
                    ns = ns.map { it - r }
                }
                q = q.copy(nodes = ns)
            }
            paths += q
            indices += ns
        }
        // Noktalar: aynı yerdekiler birleşir (ızgara ile hızlı arama)
        val points = ArrayList<Offset>()
        val refs = ArrayList<MutableList<Ref>>()
        val grid = HashMap<Long, MutableList<Int>>()
        fun cell(x: Float, y: Float): Long = (floor(x.toDouble()).toLong() shl 32) xor (floor(y.toDouble()).toLong() and 0xffffffffL)
        paths.forEachIndexed { i, p ->
            indices[i].forEach { k ->
                val pt = p.points[k]
                var found = -1
                val cx = floor(pt.x.toDouble()).toLong()
                val cy = floor(pt.y.toDouble()).toLong()
                loop@ for (dx in -1L..1L) for (dy in -1L..1L) {
                    val key = ((cx + dx) shl 32) xor ((cy + dy) and 0xffffffffL)
                    grid[key]?.forEach { id -> if (found < 0 && near(points[id], pt)) found = id }
                    if (found >= 0) break@loop
                }
                if (found < 0) {
                    found = points.size
                    points += pt
                    refs += mutableListOf<Ref>()
                    grid.getOrPut(cell(pt.x, pt.y)) { mutableListOf() } += found
                }
                refs[found] += Ref(i, k)
            }
        }
        val nodes = points.indices.map { Node(it, points[it], refs[it]) }
        val refNode = HashMap<Ref, Int>()
        nodes.forEach { n -> n.refs.forEach { refNode[it] = n.id } }
        val edges = ArrayList<Edge>()
        paths.forEachIndexed { i, p ->
            val ns = indices[i]
            if (p.tag == VNODE || ns.isEmpty() || p.points.size < 2) return@forEachIndexed
            fun add(from: Int, to: Int) {
                val a = refNode[Ref(i, from)] ?: return
                val b = refNode[Ref(i, to)] ?: return
                edges += Edge(edges.size, i, from, to, a, b, segment(p.points, from, to, p.closed))
            }
            for (k in 0 until ns.size - 1) add(ns[k], ns[k + 1])
            if (p.closed) add(ns.last(), ns[0])
        }
        return Graph(paths, indices, nodes, edges)
    }

    /** Parçanın vektör noktaları (sıralı). */
    fun nodeIndices(path: EditablePath, eps: Float = 0.8f, limit: Int = 0): List<Int> {
        val n = path.points.size
        if (n == 0 || !editable(path)) return emptyList()
        if (path.tag == VNODE || n == 1) return listOf(0)
        path.nodes?.let { explicit ->
            val valid = explicit.filter { it in 0 until n }.distinct().sorted().toMutableList()
            if (!path.closed) {
                if (valid.firstOrNull() != 0) valid.add(0, 0)
                if (valid.last() != n - 1) valid += n - 1
            } else if (valid.isEmpty()) valid += 0
            return valid
        }
        return derived(path.points, path.closed, eps, limit)
    }

    // v45 fırça boyası (kbrush) da düzenlenebilir çizgi değildir
    private fun editable(p: EditablePath) = p.label == null && p.tag != "frame" && p.tag != REGION && p.tag != BRUSH_PAINT

    /**
     * Belirgin noktalar: az noktalı parçada hepsi; çok noktalıda Ramer–Douglas–Peucker (kapalıda en uzak nokta da).
     * v36 [limit] > 0: nokta sayısı sınırı aşarsa eşik büyütülerek yeniden seyreltilir (en belirgin noktalar kalır).
     */
    private fun derived(points: List<Offset>, closed: Boolean, eps: Float, limit: Int = 0): List<Int> {
        val n = points.size
        // v37 basit düzenlemede az noktalı parça da sınıra iner (ör. 12 köşeli çokgen)
        if (n <= (if (limit > 0) limit else ALL_NODES)) return (0 until n).toList()
        return if (limit > 0) rdpLimited(points, closed, eps, limit) else rdp(points, closed, eps)
    }

    private fun rdp(points: List<Offset>, closed: Boolean, eps: Float): List<Int> {
        val n = points.size
        if (!closed) {
            val keep = BooleanArray(n)
            keep[0] = true; keep[n - 1] = true
            simplify(points, 0, n - 1, eps, keep)
            return keep.indices.filter { keep[it] }
        }
        val ext = points + points[0]
        val far = (1 until n).maxByOrNull { (points[it] - points[0]).getDistance() } ?: (n / 2)
        val keep = BooleanArray(n + 1)
        keep[0] = true; keep[far] = true; keep[n] = true
        simplify(ext, 0, far, eps, keep)
        simplify(ext, far, n, eps, keep)
        return (0 until n).filter { keep[it] }
    }

    private fun simplify(points: List<Offset>, a: Int, b: Int, eps: Float, keep: BooleanArray) {
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(a to b)
        while (stack.isNotEmpty()) {
            val (s, e) = stack.removeLast()
            if (e <= s + 1) continue
            var maxD = -1f
            var idx = -1
            for (i in s + 1 until e) {
                val d = (project(points[i], points[s], points[e]) - points[i]).getDistance()
                if (d > maxD) { maxD = d; idx = i }
            }
            if (idx >= 0 && maxD > eps) {
                keep[idx] = true
                stack.addLast(s to idx); stack.addLast(idx to e)
            }
        }
    }

    private fun segment(points: List<Offset>, from: Int, to: Int, closed: Boolean): List<Offset> =
        if (to > from) points.subList(from, to + 1)
        else if (closed) points.subList(from, points.size) + points.subList(0, to + 1)
        else points.subList(to, from + 1).reversed()

    /** Alt yolların kapsadığı kutunun köşegeni. */
    fun diagonal(paths: List<EditablePath>): Float {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        paths.forEach { p -> p.points.forEach { l = minOf(l, it.x); t = minOf(t, it.y); r = maxOf(r, it.x); b = maxOf(b, it.y) } }
        return if (l > r) 0f else hypot(r - l, b - t)
    }

    fun diagonal(stroke: Stroke): Float {
        if (stroke.points.isEmpty()) return 0f
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        stroke.points.forEach { l = minOf(l, it.x); t = minOf(t, it.y); r = maxOf(r, it.x); b = maxOf(b, it.y) }
        return hypot(r - l, b - t)
    }

    // --- İşlemler ---

    /**
     * Noktaları taşır: [deltas] vektör noktası → kayma. Bağlı çizgilerin kavisi iki ucun kaymasına göre esner.
     * [rigid] verilirse o çizginin ara noktaları verilen dönüşümle (katı) taşınır.
     */
    fun moveNodes(g: Graph, deltas: Map<Int, Offset>, rigid: Pair<Int, (Offset) -> Offset>? = null): List<EditablePath> {
        val pts = g.paths.map { it.points.toMutableList() }
        deltas.forEach { (id, d) ->
            g.nodes.getOrNull(id)?.refs?.forEach { r -> pts[r.path][r.index] = g.paths[r.path].points[r.index] + d }
        }
        g.edges.forEach { e ->
            val inner = e.points.size - 2
            if (inner <= 0) return@forEach
            val src = g.paths[e.path].points
            val n = src.size
            if (rigid != null && rigid.first == e.id) {
                for (k in 1..inner) { val idx = (e.from + k) % n; pts[e.path][idx] = rigid.second(src[idx]) }
                return@forEach
            }
            val dA = deltas[e.a] ?: Offset.Zero
            val dB = deltas[e.b] ?: Offset.Zero
            if (dA == Offset.Zero && dB == Offset.Zero) return@forEach
            val cum = FloatArray(e.points.size)
            for (k in 1 until e.points.size) cum[k] = cum[k - 1] + (e.points[k] - e.points[k - 1]).getDistance()
            val total = cum.last()
            for (k in 1..inner) {
                val t = if (total > 1e-4f) cum[k] / total else k / (inner + 1f)
                val idx = (e.from + k) % n
                pts[e.path][idx] = src[idx] + dA * (1f - t) + dB * t
            }
        }
        return tidy(g.paths.mapIndexed { i, p -> p.copy(points = pts[i]) })
    }

    /** Çizginin üstüne vektör noktası ekler; eklenen (ya da oradaki) noktanın yeri ile. */
    fun insertNode(g: Graph, hit: EdgeHit): Pair<List<EditablePath>, Offset> {
        val e = hit.edge
        // Uca çok yakınsa yeni nokta eklenmez, uç kullanılır
        if ((hit.point - e.start).getDistance() <= MERGE * 2f) return g.paths to e.start
        if ((hit.point - e.end).getDistance() <= MERGE * 2f) return g.paths to e.end
        val p = g.paths[e.path]
        val at = hit.insertAt.coerceIn(1, p.points.size)
        val pts = p.points.toMutableList().apply { add(at, hit.point) }
        val ns = (g.nodeIndices[e.path].map { if (it >= at) it + 1 else it } + at).distinct().sorted()
        val out = g.paths.toMutableList()
        out[e.path] = p.copy(points = pts, nodes = ns)
        return out to hit.point
    }

    /** Hiçbir çizgiye bağlı olmayan (çizilmeyen) serbest vektör noktası ekler. */
    fun addFreeNode(g: Graph, at: Offset): List<EditablePath> =
        if (g.nodeNear(at) != null) g.paths else g.paths + EditablePath(listOf(at), tag = VNODE, nodes = listOf(0))

    /**
     * [from] ile [to] noktaları arasına düz çizgi çizer. Açık bir yolun ucundan çizilirse yol uzar; iki açık yolun
     * uçları bağlanırsa yollar birleşir; aynı yolun iki ucu bağlanırsa yol kapanır (içi boyanabilir). Diğer durumlarda
     * yeni bir çizgi parçası eklenir ([style] ile biçimlenir). Aynı noktaysa null.
     */
    fun connect(g: Graph, from: Offset, to: Offset, style: (EditablePath) -> EditablePath = { it }): List<EditablePath>? {
        if ((from - to).getDistance() <= MERGE) return null
        val fromNode = g.nodeNear(from)
        val toNode = g.nodeNear(to)
        if (fromNode != null && fromNode.id == toNode?.id) return null
        val a = fromNode?.point ?: from
        val b = toNode?.point ?: to
        // Zaten bu iki nokta arasında düz bir çizgi varsa yeni çizgi eklenmez
        g.edgeBetween(a, b)?.let { if (!it.curved) return null }
        fun openEnd(node: Node?): Ref? = node?.refs?.firstOrNull { r ->
            val p = g.paths[r.path]
            !p.closed && editable(p) && p.tag != VNODE && p.points.size >= 2 && (r.index == 0 || r.index == p.points.size - 1)
        }
        val fe = openEnd(fromNode)
        val te = openEnd(toNode)
        val out = g.paths.toMutableList()
        fun nodesOf(i: Int) = g.nodeIndices[i]
        fun oriented(i: Int, endLast: Boolean, ref: Ref): Pair<List<Offset>, List<Int>> {
            val p = g.paths[i]
            val n = p.points.size
            val isLast = ref.index == n - 1
            return if (isLast == endLast) p.points to nodesOf(i)
            else p.points.reversed() to nodesOf(i).map { n - 1 - it }.sorted()
        }
        when {
            fe != null && te != null && fe.path == te.path -> {
                val p = g.paths[fe.path]
                if (p.points.size < 3) return null
                out[fe.path] = p.copy(closed = true)
            }
            fe != null && te != null -> {
                val (pa, na) = oriented(fe.path, endLast = true, ref = fe)
                val (pb, nb) = oriented(te.path, endLast = false, ref = te)
                val merged = g.paths[fe.path].copy(points = pa + pb, nodes = na + nb.map { it + pa.size })
                out[fe.path] = merged
                out.removeAt(te.path)
            }
            fe != null -> {
                val (pa, na) = oriented(fe.path, endLast = true, ref = fe)
                out[fe.path] = g.paths[fe.path].copy(points = pa + b, nodes = na + pa.size)
            }
            te != null -> {
                val (pb, nb) = oriented(te.path, endLast = false, ref = te)
                out[te.path] = g.paths[te.path].copy(points = listOf(a) + pb, nodes = listOf(0) + nb.map { it + 1 })
            }
            else -> out += style(EditablePath(listOf(a, b), nodes = listOf(0, 1)))
        }
        return tidy(out)
    }

    /** Çizgiye, [through] noktasından geçen yumuşak bir kavis verir (uçlar yerinde kalır). */
    fun bendEdge(g: Graph, edge: Edge, through: Offset): List<EditablePath> {
        val a = edge.start
        val b = edge.end
        val c = through * 2f - (a + b) / 2f
        val inner = (1 until BEND_STEPS).map { k ->
            val t = k / BEND_STEPS.toFloat()
            val m = 1f - t
            a * (m * m) + c * (2f * m * t) + b * (t * t)
        }
        return replaceInner(g, edge, inner)
    }

    /** Çizgiyi düzleştirir (kavisi kalkar). */
    fun straighten(g: Graph, edge: Edge): List<EditablePath> = replaceInner(g, edge, emptyList())

    /** Çizgiyi ortası çevresinde [degrees] kadar döndürür (eğimi değişir); bağlı çizgiler uçlarla birlikte esner. */
    fun rotateEdge(g: Graph, edge: Edge, degrees: Float): List<EditablePath> {
        if (edge.a == edge.b || abs(degrees) < 1e-4f) return g.paths
        val pivot = (edge.start + edge.end) / 2f
        val rad = Math.toRadians(degrees.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val rot: (Offset) -> Offset = { p ->
            val x = p.x - pivot.x
            val y = p.y - pivot.y
            Offset(pivot.x + x * c - y * s, pivot.y + x * s + y * c)
        }
        val deltas = mapOf(edge.a to rot(edge.start) - edge.start, edge.b to rot(edge.end) - edge.end)
        return moveNodes(g, deltas, rigid = edge.id to rot)
    }

    /** Çizginin eğimini [target] dereceye getirir (en yakın yönüyle: 0 yatay, 90 dikey). */
    fun setAngle(g: Graph, edge: Edge, target: Float): List<EditablePath> {
        var diff = (target - edge.angle()) % 180f
        if (diff > 90f) diff -= 180f
        if (diff <= -90f) diff += 180f
        return rotateEdge(g, edge, diff)
    }

    /**
     * Seçili noktaları ve onlara bağlı çizgileri ayırır: (kalan, kesilen) parçalar. Bir yolun bir kısmı kesilirse yol
     * kesilen ve kalan açık parçalara bölünür; tamamı kesilen kapalı yol (dolgusuyla) olduğu gibi gider.
     */
    fun cutNodes(g: Graph, nodes: Set<Int>): Pair<List<EditablePath>, List<EditablePath>> {
        val cut = g.edges.filter { it.a in nodes || it.b in nodes }.mapTo(HashSet()) { it.id }
        return split(g, cut, nodes)
    }

    /** Çizgiyi siler (yol oradan açılır ya da ikiye bölünür). */
    fun deleteEdge(g: Graph, edge: Edge): List<EditablePath> = split(g, setOf(edge.id), emptySet()).first

    /** Çizilebilir (en az iki noktalı, yazı olmayan) parça var mı. */
    /**
     * v36 basit düzenleme: noktası [limit]'ten çok olan parçalarda vektör noktaları en belirginlerine indirilir (şeklin
     * görünümü değişmez; düşen noktalar çizgilerin kavisi olur). Başka parçalarla ortak noktalar (birleşim yerleri) ve
     * açık parçanın uçları korunur. Değişen parça yoksa girdi döner.
     */
    fun simplifyNodes(g: Graph, diagonal: Float, limit: Int = SIMPLE_NODES): List<EditablePath> {
        val eps = max(0.8f, diagonal * COARSE_EPS)
        var changed = false
        val out = g.paths.mapIndexed { i, p ->
            val ns = g.nodeIndices[i]
            if (ns.size <= limit || !editable(p) || p.tag == VNODE || p.points.size <= limit) return@mapIndexed p
            val shared = ns.filter { k ->
                val id = g.nodeOf(Ref(i, k)) ?: return@filter false
                g.nodes[id].refs.any { it.path != i }
            }
            val coarse = (rdpLimited(p.points, p.closed, eps, limit) + shared).distinct().sorted()
            if (coarse.size >= ns.size) return@mapIndexed p
            changed = true
            p.copy(nodes = coarse)
        }
        return if (changed) out else g.paths
    }

    /** Sadeleştirilebilir mi: noktası [limit]'ten çok olan düzenlenebilir parça var mı. */
    fun canSimplify(g: Graph, limit: Int = SIMPLE_NODES): Boolean =
        g.paths.indices.any { i -> g.nodeIndices[i].size > limit && editable(g.paths[i]) && g.paths[i].tag != VNODE && g.paths[i].points.size > limit }

    private fun rdpLimited(points: List<Offset>, closed: Boolean, eps: Float, limit: Int): List<Int> {
        var e = eps
        var out = rdp(points, closed, e)
        var guard = 0
        while (out.size > limit && guard++ < 24) {
            e *= 1.5f
            out = rdp(points, closed, e)
        }
        return out
    }

    fun drawable(paths: List<EditablePath>): Boolean = paths.any { it.label == null && it.tag != VNODE && it.tag != REGION && it.tag != BRUSH_PAINT && it.points.size >= 2 }

    private fun split(g: Graph, cut: Set<Int>, pieceNodes: Set<Int>): Pair<List<EditablePath>, List<EditablePath>> {
        val rest = ArrayList<EditablePath>()
        val piece = ArrayList<EditablePath>()
        val byPath = g.edges.groupBy { it.path }
        g.paths.forEachIndexed { i, p ->
            if (!editable(p)) { rest += p; return@forEachIndexed }
            if (p.tag == VNODE) {
                if (g.nodeOf(Ref(i, 0)) in pieceNodes) piece += p else rest += p
                return@forEachIndexed
            }
            val es = byPath[i].orEmpty()
            if (es.isEmpty()) { rest += p; return@forEachIndexed }
            val flags = es.map { it.id in cut }
            if (flags.none { it }) { rest += p; return@forEachIndexed }
            if (flags.all { it }) { piece += p; return@forEachIndexed }
            var order = es.indices.toList()
            if (p.closed) {
                val first = es.indices.first { flags[it] != flags[(it - 1 + es.size) % es.size] }
                order = order.drop(first) + order.take(first)
            }
            var k = 0
            while (k < order.size) {
                val f = flags[order[k]]
                val run = ArrayList<Int>()
                while (k < order.size && flags[order[k]] == f) { run += order[k]; k++ }
                val pts = ArrayList<Offset>()
                val ns = ArrayList<Int>()
                run.forEachIndexed { r, ei ->
                    val e = es[ei]
                    if (r == 0) { ns += 0; pts += e.points } else pts += e.points.drop(1)
                    ns += pts.size - 1
                }
                val np = p.copy(points = pts, closed = false, nodes = ns.distinct())
                if (f) piece += np else rest += np
            }
        }
        return tidy(rest) to piece
    }

    /** Çizginin ara noktalarını değiştirir; sonraki vektör noktalarının sıraları kayar. */
    private fun replaceInner(g: Graph, edge: Edge, inner: List<Offset>): List<EditablePath> {
        val p = g.paths[edge.path]
        val n = p.points.size
        val ns = g.nodeIndices[edge.path]
        val wrap = p.closed && edge.to <= edge.from
        val out = g.paths.toMutableList()
        out[edge.path] = if (wrap) {
            p.copy(points = p.points.subList(0, edge.from + 1) + inner, nodes = ns.filter { it <= edge.from })
        } else {
            val shift = inner.size - (edge.to - edge.from - 1)
            p.copy(
                points = p.points.subList(0, edge.from + 1) + inner + p.points.subList(edge.to, n),
                nodes = ns.map { if (it >= edge.to) it + shift else it },
            )
        }
        return out
    }

    /** Başka bir çizginin noktasıyla çakışan serbest noktalar kaldırılır (nokta artık o çizgidedir). */
    private fun tidy(paths: List<EditablePath>): List<EditablePath> {
        if (paths.none { it.tag == VNODE }) return paths
        val anchored = ArrayList<Offset>()
        paths.forEach { p ->
            if (p.tag == VNODE || !editable(p)) return@forEach
            nodeIndices(p).forEach { anchored += p.points[it] }
        }
        val seen = ArrayList<Offset>()
        return paths.filter { p ->
            if (p.tag != VNODE) return@filter true
            val pt = p.points.firstOrNull() ?: return@filter false
            if (anchored.any { near(it, pt) } || seen.any { near(it, pt) }) return@filter false
            seen += pt
            true
        }
    }

    // --- Tutamaklar ---

    /** Kavis tutamağı: çizginin ortası. */
    fun bendHandle(edge: Edge): Offset = edge.middle()

    /** Eğim (döndürme) tutamağı: çizginin bitiş ucunun [distance] kadar ötesinde. */
    fun turnHandle(edge: Edge, distance: Float): Offset {
        val d = edge.end - edge.start
        val len = d.getDistance()
        val dir = if (len < 1e-4f) Offset(1f, 0f) else d / len
        return edge.end + dir * distance
    }

    fun near(a: Offset, b: Offset): Boolean = abs(a.x - b.x) <= MERGE && abs(a.y - b.y) <= MERGE

    private fun project(p: Offset, a: Offset, b: Offset): Offset {
        val vx = b.x - a.x
        val vy = b.y - a.y
        val len2 = vx * vx + vy * vy
        if (len2 < 1e-9f) return a
        val t = (((p.x - a.x) * vx + (p.y - a.y) * vy) / len2).coerceIn(0f, 1f)
        return Offset(a.x + vx * t, a.y + vy * t)
    }
}
