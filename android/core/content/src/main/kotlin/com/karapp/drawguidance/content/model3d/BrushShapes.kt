package com.karapp.drawguidance.content.model3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** v25 3B fırça türleri: düz çizgi ya da çizgiden üretilen 3B cisim. */
enum class Brush3Kind { LINE, TUBE, RIBBON, WALL, LATHE, INFLATE }

/**
 * 3B fırçanın cisim ayarları (dünya biriminde): [width] kalınlık (tüp çapı, şerit/duvar eni), [height] duvar yüksekliği,
 * [bulge] şişirmede kabarıklık (0,2..1,5), [taper] uç incelmesi (0..1), [smoothing] yumuşatma (0..1), [pressure] kalem
 * basıncı kalınlığı değiştirsin mi; [sides] tüp/torna çevre dilimi ve [maxSamples] çizgi noktası sınırı (cihaz sınıfına
 * göre).
 */
data class BrushShapeSpec(
    val kind: Brush3Kind,
    val width: Float,
    val height: Float = 0.5f,
    val bulge: Float = 0.8f,
    val taper: Float = 0.3f,
    val smoothing: Float = 0.4f,
    val pressure: Boolean = true,
    val sides: Int = 10,
    val maxSamples: Int = 160,
)

/**
 * Çizim düzlemindeki fırça izi: dünya noktaları, (varsa) basınçlar 0..1, düzlemin normali ve düzlemdeki yukarı yön.
 * v26 [closed] izin kapalı/açık olduğunu zorlar (null: uçların yakınlığına göre), [axis] tornada eksenin geçtiği nokta
 * (null: izin ilk noktası). Silgi bölünen parçaları bunlarla yeniden kurar.
 */
class BrushInput(
    val points: List<V3>,
    val pressures: FloatArray?,
    val normal: V3,
    val up: V3,
    val closed: Boolean? = null,
    val axis: V3? = null,
)

/**
 * v25 3B fırçanın cisimleri (saf Kotlin): düzlemde çizilen iz önce yumuşatılır ve eşit aralıkla yeniden örneklenir,
 * sonra türüne göre kapalı (dolu) bir ağa dönüşür:
 * - **Tüp:** iz boyunca yuvarlak boru (uçlarda incelme, basınçla kalınlık), uçları kapalı; kapalı izde halka.
 * - **Şerit:** düzlemde yatan ince, yassı şerit.
 * - **Duvar:** açık iz düzlemin normali yönünde yükseltilmiş duvar; kapalı iz içi dolu prizma (zemine çizilen kat planı,
 *   yıldız, harf…).
 * - **Torna:** iz, başlangıç noktasından geçen düzlemdeki dikey eksen çevresinde döndürülür (vazo, şişe, kadeh).
 * - **Şişir:** kapalı iz yumuşak, iki yanı kabarık bir cisme dönüşür (yaprak, bulut, taş).
 * Sonuç dünya uzayındadır; [toPart] parçaya (tabanının ortası konum) çevirir.
 */
object BrushShapes {

    /** Düzlemin çerçevesi: birim normal, düzlemdeki yukarı ve sağ (sağ elli). */
    fun frame(normal: V3, upHint: V3): Triple<V3, V3, V3> {
        val n = normal.normalized()
        val up = (upHint - n * (upHint dot n)).normalized().takeIf { it.length > 0.5f } ?: perpendicular(n)
        return Triple(n, up, (up cross n).normalized())
    }

    /** İz kapalı mı (uçlar birbirine yakın ve iz yeterince uzun). */
    fun isClosed(raw: List<V3>, spec: BrushShapeSpec): Boolean {
        val rawLength = pathLength(raw)
        return raw.size > 6 && dist(raw.first(), raw.last()) < max(spec.width * 1.5f, rawLength * 0.08f) && rawLength > spec.width * 4f
    }

    fun build(input: BrushInput, spec: BrushShapeSpec): Mesh? {
        if (input.points.size < 2 || spec.kind == Brush3Kind.LINE) return null
        val (n, up, right) = frame(input.normal, input.up)
        val raw = input.points
        val rawLength = pathLength(raw)
        if (rawLength < spec.width * 0.6f && spec.kind != Brush3Kind.TUBE) return null
        val closed = input.closed ?: isClosed(raw, spec)
        // Kapalı izde son noktalar başlangıca çok yakınsa atılır; yumuşatma halka boyunca (dikiş izi kalmaz)
        val ring = if (closed && raw.size > 3) raw.dropLastWhile { it !== raw.first() && dist(it, raw.first()) < spec.width * 0.25f } else raw
        val smoothed = smooth(ring, (spec.smoothing.coerceIn(0f, 1f) * 5f).roundToInt(), closed && spec.kind != Brush3Kind.LATHE)
        val length = pathLength(smoothed)
        val spacing = max(spec.width * 0.5f, length / spec.maxSamples.coerceAtLeast(8))
        val (pts, prs) = resample(smoothed, input.pressures, spacing, closed && spec.kind != Brush3Kind.LATHE)
        if (pts.size < 2) return null
        val mesh = when (spec.kind) {
            Brush3Kind.TUBE -> tube(pts, widths(pts.size, prs, spec, closed).map { it * 0.5f }, spec.sides.coerceIn(6, 24), closed)
            Brush3Kind.RIBBON -> strip(pts, widths(pts.size, prs, spec, closed).map { it * 0.5f }, n, 0f, max(spec.width * 0.12f, 0.004f), closed)
            Brush3Kind.WALL -> if (closed) prism(pts, n, right, up, spec.height.coerceAtLeast(0.01f))
                else strip(pts, List(pts.size) { spec.width * 0.5f }, n, 0f, spec.height.coerceAtLeast(0.01f), false)
            Brush3Kind.LATHE -> lathe(pts, n, right, up, spec.sides.coerceIn(8, 48) * 2, input.axis ?: pts[0])
            Brush3Kind.INFLATE -> inflate(pts, n, right, up, spec.bulge.coerceIn(0.2f, 1.5f), if (spec.maxSamples >= 200) 3 else 2)
            Brush3Kind.LINE -> null
        } ?: return null
        if (mesh.faceCount == 0) return null
        return Generators3D.fixOrientation(mesh)
    }

    /**
     * Dünya ağını parçaya çevirir (konum: tabanın ortası). v26 [source] verilirse (dünya uzayında iz) parçanın yerel
     * uzayına taşınıp ağın imzasıyla saklanır: silgi parçayı bununla yeniden kurar.
     */
    fun toPart(world: Mesh, id: String, name: String, material: Material, source: BrushSource? = null): Part {
        val b = world.bounds
        val origin = V3(b.center.x, b.min.y, b.center.z)
        val mesh = world.mapPositions { it - origin }
        return Part(
            id = id, name = name, mesh = mesh, primitive = null, position = origin, material = material, smooth = true,
            brush = source?.shifted(origin)?.signed(mesh),
        )
    }

    /** Kaynaktan ağ (kaynağın uzayında). */
    fun rebuild(source: BrushSource): Mesh? = build(source.input(), source.spec)

    // ------------------------------------------------------------------------------------------ iz hazırlığı

    private fun perpendicular(n: V3): V3 {
        val h = if (abs(n.y) < 0.9f) V3.UP else V3(0f, 0f, -1f)
        return (h - n * (h dot n)).normalized()
    }

    private fun dist(a: V3, b: V3) = (a - b).length

    fun pathLength(p: List<V3>): Float { var l = 0f; for (i in 1 until p.size) l += dist(p[i - 1], p[i]); return l }

    /** Komşu ortalamasıyla yumuşatma (açık izde uçlar sabit, kapalı izde halka boyunca). */
    fun smooth(points: List<V3>, passes: Int, cyclic: Boolean = false): List<V3> {
        var p = points
        repeat(passes) {
            if (p.size < 3) return p
            val n = p.size
            p = List(n) { i ->
                if (!cyclic && (i == 0 || i == n - 1)) p[i]
                else p[(i - 1 + n) % n] * 0.25f + p[i] * 0.5f + p[(i + 1) % n] * 0.25f
            }
        }
        return p
    }

    /** Eşit aralıkla yeniden örnekleme; basınçlar da ara değerlenir. Kapalı izde son nokta ilkine bağlanır. */
    fun resample(points: List<V3>, pressures: FloatArray?, spacing: Float, closed: Boolean): Pair<List<V3>, FloatArray?> {
        val src = if (closed) points + points.first() else points
        val prs = pressures?.let { pr -> FloatArray(src.size) { i -> pr.getOrElse(if (i < points.size) i else 0) { 1f } } }
        val out = ArrayList<V3>(); val outP = ArrayList<Float>()
        out += src[0]; outP += prs?.get(0) ?: 1f
        var carry = 0f
        for (i in 1 until src.size) {
            val a = src[i - 1]; val b = src[i]
            val seg = dist(a, b)
            if (seg < 1e-7f) continue
            var t = spacing - carry
            while (t <= seg) {
                val k = t / seg
                out += a + (b - a) * k
                outP += prs?.let { it[i - 1] + (it[i] - it[i - 1]) * k } ?: 1f
                t += spacing
            }
            carry = seg - (t - spacing)
        }
        if (!closed) {
            if (dist(out.last(), src.last()) > spacing * 0.3f) { out += src.last(); outP += prs?.last() ?: 1f }
            else { out[out.size - 1] = src.last() }
        } else if (out.size > 2 && dist(out.last(), out.first()) < spacing * 0.5f) { out.removeAt(out.size - 1); outP.removeAt(outP.size - 1) }
        return out to (if (prs != null) outP.toFloatArray() else null)
    }

    /** Uç incelmesi ve basınçla eni. */
    private fun widths(count: Int, pressures: FloatArray?, spec: BrushShapeSpec, closed: Boolean): List<Float> = List(count) { i ->
        val t = if (count <= 1) 0.5f else i / (count - 1f)
        val taper = if (closed || spec.taper <= 0.01f) 1f else {
            val e = 0.35f * spec.taper
            val a = (t / e).coerceIn(0f, 1f); val b = ((1f - t) / e).coerceIn(0f, 1f)
            max(0.12f, sqrt(min(a, b)))
        }
        val pr = if (spec.pressure && pressures != null) 0.3f + 0.7f * pressures[i].coerceIn(0f, 1f) else 1f
        spec.width * taper * pr
    }

    // ------------------------------------------------------------------------------------------ tüp

    private fun tube(points: List<V3>, radii: List<Float>, sides: Int, closed: Boolean): Mesh {
        val n = points.size
        val tangents = List(n) { i ->
            val prev = if (i > 0) points[i - 1] else if (closed) points[n - 1] else points[i]
            val next = if (i < n - 1) points[i + 1] else if (closed) points[0] else points[i]
            (next - prev).normalized()
        }
        var nx = perpendicular(tangents[0])
        val pos = ArrayList<Float>(n * sides * 3 + 6)
        for (i in 0 until n) {
            val t = tangents[i]
            nx = (nx - t * (nx dot t)).normalized().let { if (it.length < 0.5f) perpendicular(t) else it }
            val bz = (t cross nx).normalized()
            for (s in 0 until sides) {
                val a = 2.0 * PI * s / sides
                val p = points[i] + nx * (radii[i] * cos(a).toFloat()) + bz * (radii[i] * sin(a).toFloat())
                pos += p.x; pos += p.y; pos += p.z
            }
        }
        val faces = ArrayList<IntArray>()
        val segs = if (closed) n else n - 1
        for (i in 0 until segs) {
            val j = (i + 1) % n
            for (s in 0 until sides) {
                val s1 = (s + 1) % sides
                faces += intArrayOf(i * sides + s, i * sides + s1, j * sides + s1, j * sides + s)
            }
        }
        if (!closed) {
            // Uç kapakları: merkez noktalı yelpaze (yuvarlak uç gibi biraz dışarıda)
            val c0 = points[0] - tangents[0] * (radii[0] * 0.35f)
            val c1 = points[n - 1] + tangents[n - 1] * (radii[n - 1] * 0.35f)
            val i0 = pos.size / 3; pos += c0.x; pos += c0.y; pos += c0.z
            val i1 = pos.size / 3; pos += c1.x; pos += c1.y; pos += c1.z
            for (s in 0 until sides) {
                val s1 = (s + 1) % sides
                faces += intArrayOf(i0, s1, s)
                faces += intArrayOf(i1, (n - 1) * sides + s, (n - 1) * sides + s1)
            }
        }
        return Mesh(pos.toFloatArray(), faces.toTypedArray())
    }

    // ------------------------------------------------------------------------------------------ şerit ve duvar

    /**
     * İz boyunca dikdörtgen kesitli şerit: yanlara [halfWidths], düzlem normali yönünde [h0]'dan [h1]'e. Yerel çerçeve
     * (teğet, sol, normal) sağ ellidir; yüzler buna göre dışa bakar.
     */
    private fun strip(points: List<V3>, halfWidths: List<Float>, n: V3, h0: Float, h1: Float, closed: Boolean): Mesh {
        val count = points.size
        val pos = FloatArray(count * 4 * 3)
        for (i in 0 until count) {
            val prev = if (i > 0) points[i - 1] else if (closed) points[count - 1] else points[i]
            val next = if (i < count - 1) points[i + 1] else if (closed) points[0] else points[i]
            val t = (next - prev).normalized()
            val side = (n cross t).normalized()
            val w = halfWidths[i]
            val corners = arrayOf(
                points[i] + side * w + n * h0, // A: sol alt
                points[i] - side * w + n * h0, // B: sağ alt
                points[i] - side * w + n * h1, // C: sağ üst
                points[i] + side * w + n * h1, // D: sol üst
            )
            corners.forEachIndexed { k, p -> val o = (i * 4 + k) * 3; pos[o] = p.x; pos[o + 1] = p.y; pos[o + 2] = p.z }
        }
        fun A(i: Int): Int = i * 4
        fun B(i: Int): Int = i * 4 + 1
        fun C(i: Int): Int = i * 4 + 2
        fun D(i: Int): Int = i * 4 + 3
        val faces = ArrayList<IntArray>()
        val segs = if (closed) count else count - 1
        for (i in 0 until segs) {
            val j = (i + 1) % count
            faces += intArrayOf(C(i), C(j), D(j), D(i))   // üst
            faces += intArrayOf(A(i), A(j), B(j), B(i))   // alt
            faces += intArrayOf(A(i), D(i), D(j), A(j))   // sol duvar
            faces += intArrayOf(B(i), B(j), C(j), C(i))   // sağ duvar
        }
        if (!closed) {
            faces += intArrayOf(A(0), B(0), C(0), D(0))
            val e = count - 1
            faces += intArrayOf(D(e), C(e), B(e), A(e))
        }
        return Mesh(pos, faces.toTypedArray())
    }

    /** v26 kapalı hattan prizma (silgide kesici): düzlemin normali yönünde [height] yükseklik. */
    fun prismOf(points: List<V3>, normal: V3, upHint: V3, height: Float): Mesh {
        val (n, up, right) = frame(normal, upHint)
        return Generators3D.fixOrientation(prism(points, n, right, up, height))
    }

    /** Kapalı izden içi dolu prizma (taban düzlemde, yükseklik normal yönünde). */
    private fun prism(points: List<V3>, n: V3, right: V3, up: V3, height: Float): Mesh {
        val origin = points[0]
        var pts = points
        // Düzlemde saat yönünün tersine (normalden bakınca)
        var area = 0f
        for (i in pts.indices) {
            val a = pts[i] - origin; val b = pts[(i + 1) % pts.size] - origin
            area += (a dot right) * (b dot up) - (b dot right) * (a dot up)
        }
        if (area < 0f) pts = pts.reversed()
        val m = pts.size
        val pos = FloatArray(m * 2 * 3)
        for (i in 0 until m) {
            val b = pts[i]; val t = pts[i] + n * height
            pos[3 * i] = b.x; pos[3 * i + 1] = b.y; pos[3 * i + 2] = b.z
            pos[3 * (m + i)] = t.x; pos[3 * (m + i) + 1] = t.y; pos[3 * (m + i) + 2] = t.z
        }
        val faces = ArrayList<IntArray>()
        faces += IntArray(m) { m + it }                 // üst (normal yönünde)
        faces += IntArray(m) { m - 1 - it }             // alt
        for (i in 0 until m) {
            val j = (i + 1) % m
            faces += intArrayOf(i, j, m + j, m + i)
        }
        return Mesh(pos, faces.toTypedArray())
    }

    // ------------------------------------------------------------------------------------------ torna

    /** Profil, [origin] noktasından (varsayılan izin başı) geçen düzlemdeki dikey eksen çevresinde döndürülür. */
    private fun lathe(points: List<V3>, n: V3, right: V3, up: V3, segments: Int, origin: V3): Mesh? {
        var profile = points.map { p -> val d = p - origin; abs(d dot right) to (d dot up) }
        if (profile.maxOf { it.first } < 1e-3f) return null
        // Aşağıdan yukarı
        if (profile.first().second > profile.last().second) profile = profile.reversed()
        val eps = 1e-3f * max(1e-3f, profile.maxOf { it.first })
        val prof = profile.map { (r, y) -> (if (r < eps) 0f else r) to y }
        val local = Primitives.revolve(prof, segments, capBottom = prof.first().first > 0f, capTop = prof.last().first > 0f)
        // Yerel (x, y, z) → (sağ, yukarı, normal): sağ elli taban, yüzler dışa bakmaya devam eder
        return local.mapPositions { p -> origin + right * p.x + up * p.y + n * p.z }
    }

    // ------------------------------------------------------------------------------------------ şişirme

    /**
     * Kapalı izden şişkin cisim. İz ağırlık merkezinden yıldız biçimliyse (yaprak, taş, bulut çoğu) merkeze doğru
     * eş merkezli halkalar kurulur (düzenli ağ); değilse iç üçgenlenip [subdiv] kez bölünür. Her iç nokta sınıra
     * uzaklığına göre yuvarlak bir kesitle iki yana kabartılır; sınır noktaları ortaktır, cisim kapalıdır.
     */
    private fun inflate(points: List<V3>, n: V3, right: V3, up: V3, bulge: Float, subdiv: Int): Mesh? {
        val origin = points[0]
        var xs = points.map { (it - origin) dot right }
        var ys = points.map { (it - origin) dot up }
        var area = 0f
        for (i in xs.indices) { val j = (i + 1) % xs.size; area += xs[i] * ys[j] - xs[j] * ys[i] }
        if (abs(area) < 1e-8f) return null
        if (area < 0f) { xs = xs.reversed(); ys = ys.reversed() }
        // Sınır az ve eşit aralıklı noktaya indirilir (halkalar seyrek, cisim hafif kalsın)
        val target = if (subdiv >= 3) 72 else 56
        if (xs.size > target) {
            val per = ArrayList<Float>(); var total = 0f
            for (i in xs.indices) { val j = (i + 1) % xs.size; val l = sqrt((xs[j] - xs[i]) * (xs[j] - xs[i]) + (ys[j] - ys[i]) * (ys[j] - ys[i])); per += l; total += l }
            val nx = ArrayList<Float>(); val ny = ArrayList<Float>()
            var acc = 0f; var seg = 0
            for (k in 0 until target) {
                val want = total * k / target
                while (seg < xs.size - 1 && acc + per[seg] < want) { acc += per[seg]; seg++ }
                val j = (seg + 1) % xs.size
                val t = if (per[seg] < 1e-9f) 0f else ((want - acc) / per[seg]).coerceIn(0f, 1f)
                nx += xs[seg] + (xs[j] - xs[seg]) * t; ny += ys[seg] + (ys[j] - ys[seg]) * t
            }
            xs = nx; ys = ny
        }
        val m = xs.size
        val px = ArrayList<Float>(xs); val py = ArrayList<Float>(ys)
        val boundary = ArrayList<Boolean>(List(m) { true })
        var tris = ArrayList<IntArray>()
        val cx = xs.average().toFloat(); val cy = ys.average().toFloat()
        val star = (0 until m).all { i -> val j = (i + 1) % m; (xs[i] - cx) * (ys[j] - cy) - (xs[j] - cx) * (ys[i] - cy) > 1e-9f }
        if (star) {
            // Eş merkezli halkalar: sınıra yakın sık (kenar yumuşak döner)
            val rings = if (subdiv >= 3) 12 else 9
            val ringStart = IntArray(rings)
            for (k in 1 until rings) {
                val u = kotlin.math.sin((rings - k) / rings.toFloat() * PI.toFloat() / 2f)
                ringStart[k] = px.size
                for (i in 0 until m) { px += cx + (xs[i] - cx) * u; py += cy + (ys[i] - cy) * u; boundary += false }
            }
            val center = px.size
            px += cx; py += cy; boundary += false
            fun ring(k: Int, i: Int) = if (k == 0) i % m else ringStart[k] + i % m
            for (k in 0 until rings - 1) for (i in 0 until m) {
                val a = ring(k, i); val b = ring(k, i + 1); val c = ring(k + 1, i + 1); val d = ring(k + 1, i)
                tris += intArrayOf(a, b, c); tris += intArrayOf(a, c, d)
            }
            for (i in 0 until m) tris += intArrayOf(ring(rings - 1, i), ring(rings - 1, i + 1), center)
        } else {
            val flat = FloatArray(m * 3) { k -> when (k % 3) { 0 -> xs[k / 3]; 1 -> ys[k / 3]; else -> 0f } }
            val tri = MeshOps.triangulate(flat, IntArray(m) { it })
            var k = 0
            while (k + 2 < tri.size) { tris += intArrayOf(tri[k], tri[k + 1], tri[k + 2]); k += 3 }
            val boundaryEdges = HashSet<Long>()
            for (i in 0 until m) boundaryEdges += ek(i, (i + 1) % m)
            repeat(subdiv) {
                val mids = HashMap<Long, Int>()
                fun mid(a: Int, b: Int): Int = mids.getOrPut(ek(a, b)) {
                    px += (px[a] + px[b]) * 0.5f; py += (py[a] + py[b]) * 0.5f
                    boundary += ek(a, b) in boundaryEdges
                    px.size - 1
                }
                val next = ArrayList<IntArray>(tris.size * 4)
                val nextBoundary = HashSet<Long>()
                for (t in tris) {
                    val a = t[0]; val b = t[1]; val c = t[2]
                    val ab = mid(a, b); val bc = mid(b, c); val ca = mid(c, a)
                    next += intArrayOf(a, ab, ca); next += intArrayOf(ab, b, bc); next += intArrayOf(ca, bc, c); next += intArrayOf(ab, bc, ca)
                    for ((p, q, r) in listOf(Triple(a, b, ab), Triple(b, c, bc), Triple(c, a, ca))) {
                        if (ek(p, q) in boundaryEdges) { nextBoundary += ek(p, r); nextBoundary += ek(r, q) }
                    }
                }
                tris = next
                boundaryEdges.clear(); boundaryEdges.addAll(nextBoundary)
            }
        }
        // Sınıra uzaklık ve yükseklik (yuvarlak kesit)
        val count = px.size
        val d = FloatArray(count) { i ->
            if (boundary[i]) 0f else {
                var best = Float.MAX_VALUE
                for (j in 0 until m) {
                    val jj = (j + 1) % m
                    best = min(best, segDist(px[i], py[i], xs[j], ys[j], xs[jj], ys[jj]))
                }
                best
            }
        }
        val dmax = d.max().coerceAtLeast(1e-6f)
        val hmax = dmax * bulge
        val pos = ArrayList<Float>(count * 6)
        val top = IntArray(count); val bottom = IntArray(count)
        fun add(p: V3): Int { pos += p.x; pos += p.y; pos += p.z; return pos.size / 3 - 1 }
        for (i in 0 until count) {
            val base = origin + right * px[i] + up * py[i]
            val u = 1f - d[i] / dmax
            val h = hmax * sqrt((1f - u * u).coerceAtLeast(0f))
            if (boundary[i] || h < 1e-6f) { val id = add(base); top[i] = id; bottom[i] = id }
            else { top[i] = add(base + n * h); bottom[i] = add(base - n * h) }
        }
        val faces = ArrayList<IntArray>(tris.size * 2)
        for (t in tris) {
            // Üç noktası da sınırda olan üçgen yassıdır: üst ve alt yüzü üst üste düşer, ikisi de atılır (ağ kapalı kalır)
            if (top[t[0]] == bottom[t[0]] && top[t[1]] == bottom[t[1]] && top[t[2]] == bottom[t[2]]) continue
            faces += intArrayOf(top[t[0]], top[t[1]], top[t[2]])
            faces += intArrayOf(bottom[t[2]], bottom[t[1]], bottom[t[0]])
        }
        val valid = faces.filter { it.toSet().size == 3 }
        return Mesh(pos.toFloatArray(), valid.toTypedArray()).compact()
    }

    private fun ek(a: Int, b: Int): Long = if (a < b) (a.toLong() shl 32) or b.toLong() else (b.toLong() shl 32) or a.toLong()

    private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val l2 = dx * dx + dy * dy
        val t = if (l2 < 1e-12f) 0f else (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f)
        val qx = ax + dx * t - px; val qy = ay + dy * t - py
        return sqrt(qx * qx + qy * qy)
    }
}
