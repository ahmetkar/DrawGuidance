package com.karapp.drawguidance.content.library.science

import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.LibCategory
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Geometri: çember ve üçgen bağıntıları, açınımlar ve cisimler (gizli ayrıtlar kesikli). */
internal object GeometryPlus {

    val category: LibCategory by lazy {
        LibCategory(
            id = "sci_geometry",
            title = "Geometri",
            audience = Audience.NOTES,
            section = "Geometri",
            shapes = listOf(
                TriangleModel.shape, CircleModel.shape, PolygonModel.shape, ParabolaModel.shape,
                circleParts, inscribedAngle, incircle, circumcircle, pythagoras, similarTriangles,
                thales, triangleLines, trapezoidMidline, coneNet, cylinderNet, pyramidNet,
                cubeNet, sphere, cuboid, frustum, octahedron, dodecahedron,
            ),
        )
    }

    private const val FILL_A = "#E3F2FD"
    private const val FILL_B = "#FFF3E0"
    private const val FILL_C = "#E8F5E9"
    private const val HILITE = "#1E88E5"

    // ------------------------------------------------------------------ Yardımcılar

    /** Köşe adı: köşeden [center]'ın tersi yönünde [d] uzaklıkta. */
    private fun Lb.vertex(v: P, center: P, name: String, d: Float = 0.06f, size: Float = 0.075f) {
        val u = unit(v.x - center.x, v.y - center.y)
        c(v.x + u.x * d, v.y + u.y * d, name, size)
    }

    /** İç açı yayı (V köşesinde P1 ve P2 yönleri arasında); [count] iç içe yay. */
    private fun VectorBuilder.innerArc(v: P, p1: P, p2: P, r: Float, count: Int = 1, color: String? = null) {
        val a1 = angleOf(v.x, v.y, p1.x, p1.y)
        var sw = angleOf(v.x, v.y, p2.x, p2.y) - a1
        while (sw > 180f) sw -= 360f
        while (sw < -180f) sw += 360f
        for (i in 0 until count) arc(v.x, v.y, r + i * 0.018f, r + i * 0.018f, a1, sw).width(0.8f).stroke(color)
    }

    private fun centroid(vararg p: P) = P(p.sumOf { it.x.toDouble() }.toFloat() / p.size, p.sumOf { it.y.toDouble() }.toFloat() / p.size)

    /** P noktasının AB doğrusu üzerindeki dik izdüşümü. */
    private fun foot(p: P, a: P, b: P): P {
        val dx = b.x - a.x; val dy = b.y - a.y
        val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)
        return P(a.x + dx * t, a.y + dy * t)
    }

    /** İki onaltılık rengin karışımı. */
    private fun mix(c1: String, c2: String, t: Float): String {
        val a = c1.removePrefix("#").toInt(16); val b = c2.removePrefix("#").toInt(16)
        fun ch(v: Int, s: Int) = (v shr s) and 0xFF
        val tt = t.coerceIn(0f, 1f)
        val r = (ch(a, 16) + (ch(b, 16) - ch(a, 16)) * tt).toInt()
        val g = (ch(a, 8) + (ch(b, 8) - ch(a, 8)) * tt).toInt()
        val bl = (ch(a, 0) + (ch(b, 0) - ch(a, 0)) * tt).toInt()
        return "#%02X%02X%02X".format(r, g, bl)
    }

    // --- 3B çok yüzlü (dik izdüşüm, arka yüz ayıklama) ---

    private class V3(val x: Double, val y: Double, val z: Double) {
        operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
        operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
        operator fun times(k: Double) = V3(x * k, y * k, z * k)
        infix fun dot(o: V3) = x * o.x + y * o.y + z * o.z
        infix fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
        fun norm(): V3 { val l = sqrt(this dot this); return V3(x / l, y / l, z / l) }
    }

    /** Dışbükey cismin yüzleri: her [normals] yönünde en uçtaki köşeler, çevresel sırada. */
    private fun facesByNormals(verts: List<V3>, normals: List<V3>): List<List<Int>> = normals.map { n ->
        val d = verts.map { it dot n }
        val max = d.maxOrNull()!!
        val idx = verts.indices.filter { d[it] > max - 1e-6 * (1 + abs(max)) }
        val c = idx.map { verts[it] }.reduce { a, b -> a + b } * (1.0 / idx.size)
        val u = (verts[idx[0]] - c).norm()
        val w = n.norm() cross u
        idx.sortedBy { i -> val q = verts[i] - c; atan2(q dot w, q dot u) }
    }

    /**
     * Cismi ([yaw],[pitch] derece) döndürüp dik izdüşümle (x0,y0,w,h) kutusuna sığdırarak çizer: görünen yüzler
     * ışığa göre tonlanmış açık dolgu, gizli ayrıtlar kesikli, görünen ayrıtlar düz. Köşelerin ekran noktalarını döner.
     */
    private fun VectorBuilder.solid(
        verts: List<V3>, faces: List<List<Int>>, yaw: Double, pitch: Double,
        x0: Float, y0: Float, w: Float, h: Float,
        light: String = "#EAF4FC", dark: String = "#B3D4EF",
    ): List<P> {
        val cy = cos(Math.toRadians(yaw)); val sy = sin(Math.toRadians(yaw))
        val cp = cos(Math.toRadians(pitch)); val sp = sin(Math.toRadians(pitch))
        val rv = verts.map { v ->
            val x1 = v.x * cy + v.z * sy; val z1 = -v.x * sy + v.z * cy
            V3(x1, v.y * cp - z1 * sp, v.y * sp + z1 * cp)
        }
        val minX = rv.minOf { it.x }; val maxX = rv.maxOf { it.x }; val minY = rv.minOf { it.y }; val maxY = rv.maxOf { it.y }
        val s = minOf(w / (maxX - minX), h / (maxY - minY))
        val ox = x0 + (w - (maxX - minX) * s) / 2; val oy = y0 + (h - (maxY - minY) * s) / 2
        val pts = rv.map { P((ox + (it.x - minX) * s).toFloat(), (oy + (maxY - it.y) * s).toFloat()) }
        val center = rv.reduce { a, b -> a + b } * (1.0 / rv.size)
        val lightDir = V3(-0.4, 0.7, 0.6).norm()
        val visible = BooleanArray(faces.size)
        val shade = DoubleArray(faces.size)
        faces.forEachIndexed { fi, f ->
            var n = V3(0.0, 0.0, 0.0)
            for (k in f.indices) n = n + (rv[f[k]] cross rv[f[(k + 1) % f.size]])
            n = n.norm()
            val fc = f.map { rv[it] }.reduce { a, b -> a + b } * (1.0 / f.size)
            if ((fc - center) dot n < 0) n = n * -1.0
            visible[fi] = n.z > 1e-6
            shade[fi] = (n dot lightDir).coerceIn(0.0, 1.0)
        }
        faces.forEachIndexed { fi, f ->
            if (visible[fi]) points(f.map { pts[it] }, closed = true).fill(mix(dark, light, shade[fi].toFloat())).noStroke()
        }
        val edges = LinkedHashMap<Pair<Int, Int>, Boolean>()
        faces.forEachIndexed { fi, f ->
            for (k in f.indices) {
                val a = f[k]; val b = f[(k + 1) % f.size]
                val key = if (a < b) a to b else b to a
                edges[key] = (edges[key] ?: false) || visible[fi]
            }
        }
        for ((e, vis) in edges) if (!vis) line(pts[e.first].x, pts[e.first].y, pts[e.second].x, pts[e.second].y).dashed().width(0.8f)
        for ((e, vis) in edges) if (vis) line(pts[e.first].x, pts[e.first].y, pts[e.second].x, pts[e.second].y).width(1.3f)
        return pts
    }

    // ------------------------------------------------------------------ Çember

    private val circleParts = sci(
        "sci.geo.circle_parts", "Çemberin elemanları", 1f, 1f, Fit.SQUARE,
        tags = listOf("çember", "merkez", "yarıçap", "kiriş", "teğet", "yay"),
    ) { lb ->
        val o = P(0.5f, 0.48f); val r = 0.34f
        circle(o.x, o.y, r).width(1.4f)
        arc(o.x, o.y, r, r, -122f, 50f).stroke(HILITE).width(2.8f)
        val a = onCircle(o.x, o.y, r, -35f)
        line(o.x, o.y, a.x, a.y).width(1.2f)
        val b = onCircle(o.x, o.y, r, 150f); val c = onCircle(o.x, o.y, r, 222f)
        line(b.x, b.y, c.x, c.y).width(1.2f)
        val t = onCircle(o.x, o.y, r, 62f)
        val d = P(-sin(Math.toRadians(62.0)).toFloat(), cos(Math.toRadians(62.0)).toFloat())
        line(t.x - d.x * 0.3f, t.y - d.y * 0.3f, t.x + d.x * 0.3f, t.y + d.y * 0.3f).width(1.2f)
        line(o.x, o.y, t.x, t.y).dashed().width(0.8f)
        rightMark(t.x, t.y, o.x - t.x, o.y - t.y, d.x, d.y, 0.04f)
        for (p in listOf(o, a, b, c, t)) dot(p.x, p.y, 0.012f)
        lb.c(o.x - 0.04f, o.y + 0.04f, "O")
        val m = lerp(o, a, 0.5f)
        lb.c(m.x + 0.03f, m.y + 0.045f, "r")
        for ((n, ang) in listOf("A" to -35f, "B" to 150f, "C" to 222f, "T" to 62f)) {
            val q = onCircle(o.x, o.y, r + 0.065f, ang)
            lb.c(q.x, q.y, n)
        }
    }

    private val inscribedAngle = sci(
        "sci.geo.inscribed_angle", "Merkez açı ve çevre açı", 1f, 1f, Fit.SQUARE,
        tags = listOf("çember", "merkez açı", "çevre açı", "yay", "açı"),
    ) { lb ->
        val o = P(0.5f, 0.5f); val r = 0.37f
        circle(o.x, o.y, r).width(1.4f)
        arc(o.x, o.y, r, r, 30f, 120f).stroke(HILITE).width(2.8f)
        val a = onCircle(o.x, o.y, r, 30f); val b = onCircle(o.x, o.y, r, 150f); val c = onCircle(o.x, o.y, r, -90f)
        poly(a.x, a.y, o.x, o.y, b.x, b.y).width(1.2f)
        poly(a.x, a.y, c.x, c.y, b.x, b.y).width(1.2f)
        arc(o.x, o.y, 0.07f, 0.07f, 30f, 120f).width(0.9f)
        innerArc(c, a, b, 0.1f)
        for (p in listOf(o, a, b, c)) dot(p.x, p.y, 0.012f)
        lb.c(o.x, o.y + 0.12f, "2α", 0.07f)
        lb.c(c.x, c.y + 0.15f, "α", 0.07f)
        lb.c(o.x + 0.055f, o.y - 0.02f, "O")
        lb.vertex(a, o, "A"); lb.vertex(b, o, "B"); lb.vertex(c, o, "C")
    }

    // ------------------------------------------------------------------ Üçgen

    private val incircle = sci(
        "sci.geo.incircle", "Üçgenin iç teğet çemberi", 1f, 1f, Fit.SQUARE,
        tags = listOf("üçgen", "iç teğet çember", "açıortay", "iç merkez"),
    ) { lb ->
        val a = P(0.08f, 0.86f); val b = P(0.92f, 0.86f); val c = P(0.34f, 0.1f)
        val la = dist(b, c); val lbb = dist(c, a); val lc = dist(a, b)
        val per = la + lbb + lc
        val i = P((la * a.x + lbb * b.x + lc * c.x) / per, (la * a.y + lbb * b.y + lc * c.y) / per)
        val area = abs((b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y)) / 2f
        val r = area / (per / 2f)
        poly(a.x, a.y, b.x, b.y, c.x, c.y, closed = true).fill("#FAFCFF").width(1.4f)
        circle(i.x, i.y, r).fill(FILL_A).stroke(HILITE).width(1.3f)
        for (v in listOf(a, b, c)) line(v.x, v.y, i.x, i.y).dashed().width(0.8f)
        for ((p, q) in listOf(a to b, b to c, c to a)) {
            val f = foot(i, p, q)
            line(i.x, i.y, f.x, f.y).width(0.8f)
            rightMark(f.x, f.y, i.x - f.x, i.y - f.y, q.x - p.x, q.y - p.y, 0.028f)
            dot(f.x, f.y, 0.01f)
        }
        dot(i.x, i.y, 0.012f)
        lb.c(i.x + 0.045f, i.y + 0.05f, "I", 0.07f)
        val f0 = foot(i, a, b)
        lb.c(i.x - 0.03f, (i.y + f0.y) / 2f, "r", 0.07f)
        val g = centroid(a, b, c)
        lb.vertex(a, g, "A"); lb.vertex(b, g, "B"); lb.vertex(c, g, "C")
    }

    private val circumcircle = sci(
        "sci.geo.circumcircle", "Üçgenin çevrel çemberi", 1f, 1f, Fit.SQUARE,
        tags = listOf("üçgen", "çevrel çember", "kenar orta dikme", "çevrel merkez"),
    ) { lb ->
        val o = P(0.5f, 0.5f); val r = 0.4f
        val a = onCircle(o.x, o.y, r, 150f); val b = onCircle(o.x, o.y, r, 30f); val c = onCircle(o.x, o.y, r, -100f)
        circle(o.x, o.y, r).stroke(HILITE).width(1.3f)
        poly(a.x, a.y, b.x, b.y, c.x, c.y, closed = true).fill("#FAFCFF").width(1.4f)
        var n = 1
        for ((p, q) in listOf(a to b, b to c, c to a)) {
            val m = lerp(p, q, 0.5f)
            val u = unit(o.x - m.x, o.y - m.y)
            val len = dist(m, o) + 0.07f
            line(m.x, m.y, m.x + u.x * len, m.y + u.y * len).dashed().width(0.8f)
            rightMark(m.x, m.y, u.x, u.y, q.x - p.x, q.y - p.y, 0.028f)
            ticks(p.x, p.y, m.x, m.y, n, 0.018f, 0.014f)
            ticks(m.x, m.y, q.x, q.y, n, 0.018f, 0.014f)
            n++
        }
        line(o.x, o.y, a.x, a.y).stroke(HILITE).width(1f)
        dot(o.x, o.y, 0.012f)
        lb.c(o.x + 0.045f, o.y + 0.035f, "O", 0.07f)
        val mr = lerp(o, a, 0.55f)
        lb.c(mr.x, mr.y - 0.05f, "R", 0.07f, HILITE)
        lb.vertex(a, o, "A"); lb.vertex(b, o, "B"); lb.vertex(c, o, "C")
    }

    private val pythagoras = sci(
        "sci.geo.pythagoras", "Pisagor bağıntısı", 1f, 1f, Fit.SQUARE,
        tags = listOf("pisagor", "dik üçgen", "hipotenüs", "kare", "3-4-5"),
    ) { lb ->
        val u = 0.08f
        val c = P(0.36f, 0.58f); val a = P(c.x + 4 * u, c.y); val b = P(c.x, c.y - 3 * u)
        fun square(p0: P, p1: P, p2: P, p3: P, n: Int, fill: String) {
            points(listOf(p0, p1, p2, p3), closed = true).fill(fill).width(1.2f)
            for (k in 1 until n) {
                val s1 = lerp(p0, p1, k / n.toFloat()); val s2 = lerp(p3, p2, k / n.toFloat())
                line(s1.x, s1.y, s2.x, s2.y).width(0.4f)
                val t1 = lerp(p0, p3, k / n.toFloat()); val t2 = lerp(p1, p2, k / n.toFloat())
                line(t1.x, t1.y, t2.x, t2.y).width(0.4f)
            }
        }
        // b² (alt), a² (sol), c² (hipotenüs üzerinde, dışa doğru)
        square(c, a, P(a.x, a.y + 4 * u), P(c.x, c.y + 4 * u), 4, "#C8E6C9")
        square(b, c, P(c.x - 3 * u, c.y), P(b.x - 3 * u, b.y), 3, "#BBDEFB")
        val nrm = P((a.y - b.y), -(a.x - b.x))
        square(b, a, P(a.x + nrm.x, a.y + nrm.y), P(b.x + nrm.x, b.y + nrm.y), 5, "#FFE0B2")
        poly(a.x, a.y, b.x, b.y, c.x, c.y, closed = true).fill("#FFFFFF").width(1.5f)
        rightMark(c.x, c.y, 1f, 0f, 0f, -1f, 0.035f)
        lb.c(c.x + 2 * u, c.y + 2 * u, "b²", 0.09f)
        lb.c(c.x - 1.5f * u, c.y - 1.5f * u, "a²", 0.09f)
        val hc = centroid(b, a, P(a.x + nrm.x, a.y + nrm.y), P(b.x + nrm.x, b.y + nrm.y))
        lb.c(hc.x, hc.y, "c²", 0.09f)
        val tg = centroid(a, b, c)
        lb.c((c.x + a.x) / 2f, c.y - 0.035f, "b", 0.06f)
        lb.c(c.x + 0.03f, (c.y + b.y) / 2f, "a", 0.06f)
        val hm = lerp(lerp(a, b, 0.5f), tg, 0.3f)
        lb.c(hm.x + 0.01f, hm.y - 0.01f, "c", 0.06f)
    }

    private val similarTriangles = sci(
        "sci.geo.similar_triangles", "Benzer üçgenler", 1.6f, 1f,
        tags = listOf("benzerlik", "üçgen", "oran", "açı-açı", "benzerlik oranı"),
    ) { lb ->
        val a = P(0.08f, 0.82f); val b = P(0.54f, 0.82f); val c = P(0.2f, 0.44f)
        val k = 1.55f; val d = P(0.7f, 0.92f)
        fun big(p: P) = P(d.x + k * (p.x - a.x), d.y + k * (p.y - a.y))
        val e = big(b); val f = big(c)
        poly(a.x, a.y, b.x, b.y, c.x, c.y, closed = true).fill(FILL_A).width(1.4f)
        poly(d.x, d.y, e.x, e.y, f.x, f.y, closed = true).fill(FILL_B).width(1.4f)
        innerArc(a, b, c, 0.06f, 1); innerArc(d, e, f, 0.08f, 1)
        innerArc(b, c, a, 0.06f, 2); innerArc(e, f, d, 0.08f, 2)
        innerArc(c, a, b, 0.05f, 3); innerArc(f, d, e, 0.07f, 3)
        val g1 = centroid(a, b, c); val g2 = centroid(d, e, f)
        lb.vertex(a, g1, "A"); lb.vertex(b, g1, "B"); lb.vertex(c, g1, "C")
        lb.vertex(d, g2, "D"); lb.vertex(e, g2, "E"); lb.vertex(f, g2, "F")
    }

    private val thales = sci(
        "sci.geo.thales", "Temel orantı teoremi (Tales)", 1f, 1f, Fit.SQUARE,
        tags = listOf("tales", "paralel", "orantı", "benzerlik", "üçgen"),
    ) { lb ->
        val a = P(0.44f, 0.08f); val b = P(0.08f, 0.9f); val c = P(0.92f, 0.9f)
        val d = lerp(a, b, 0.45f); val e = lerp(a, c, 0.45f)
        poly(a.x, a.y, b.x, b.y, c.x, c.y, closed = true).fill("#FAFCFF").width(1.4f)
        poly(a.x, a.y, d.x, d.y, e.x, e.y, closed = true).fill(FILL_A).noStroke()
        poly(a.x, a.y, b.x, b.y, c.x, c.y, closed = true).width(1.4f)
        line(d.x, d.y, e.x, e.y).stroke(HILITE).width(1.6f)
        // paralellik işaretleri
        for ((p, q) in listOf(d to e, b to c)) {
            val m = lerp(p, q, 0.5f)
            arrowHead(m.x + 0.02f, m.y, 1f, 0f, 0.035f).width(1f)
            arrowHead(m.x + 0.045f, m.y, 1f, 0f, 0.035f).width(1f)
        }
        for (p in listOf(d, e)) dot(p.x, p.y, 0.011f)
        val g = centroid(a, b, c)
        lb.vertex(a, g, "A"); lb.vertex(b, g, "B"); lb.vertex(c, g, "C")
        lb.c(d.x - 0.05f, d.y, "D"); lb.c(e.x + 0.05f, e.y, "E")
    }

    private val triangleLines = sci(
        "sci.geo.triangle_lines", "Üçgende yükseklik, kenarortay, açıortay", 1.4f, 1f,
        tags = listOf("üçgen", "yükseklik", "kenarortay", "açıortay", "yardımcı elemanlar"),
    ) { lb ->
        val a = P(0.42f, 0.1f); val b = P(0.08f, 0.86f); val c = P(1.32f, 0.86f)
        poly(a.x, a.y, b.x, b.y, c.x, c.y, closed = true).fill("#FAFCFF").width(1.4f)
        val h = foot(a, b, c)
        val m = lerp(b, c, 0.5f)
        val ab = dist(a, b); val ac = dist(a, c)
        val n = lerp(b, c, ab / (ab + ac))
        val cH = "#1E88E5"; val cV = "#43A047"; val cN = "#E53935"
        line(a.x, a.y, h.x, h.y).stroke(cH).width(1.3f)
        line(a.x, a.y, m.x, m.y).stroke(cV).width(1.3f)
        line(a.x, a.y, n.x, n.y).stroke(cN).width(1.3f)
        rightMark(h.x, h.y, 0f, -1f, -1f, 0f, 0.035f)
        ticks(b.x, b.y, m.x, m.y, 1); ticks(m.x, m.y, c.x, c.y, 1)
        innerArc(a, b, n, 0.1f, 1, cN); innerArc(a, n, c, 0.12f, 1, cN)
        for (p in listOf(h, m, n)) dot(p.x, p.y, 0.011f)
        lb.c(h.x - 0.02f, h.y + 0.06f, "H", 0.065f, cH)
        lb.c(n.x + 0.005f, n.y + 0.06f, "N", 0.065f, cN)
        lb.c(m.x + 0.02f, m.y + 0.06f, "D", 0.065f, cV)
        val g = centroid(a, b, c)
        lb.vertex(a, g, "A"); lb.vertex(b, g, "B"); lb.vertex(c, g, "C")
    }

    private val trapezoidMidline = sci(
        "sci.geo.trapezoid_midline", "Yamuk ve orta taban", 1.5f, 1f,
        tags = listOf("yamuk", "orta taban", "dörtgen", "taban"),
    ) { lb ->
        val a = P(0.1f, 0.84f); val b = P(1.4f, 0.84f); val c = P(1.08f, 0.2f); val d = P(0.38f, 0.2f)
        points(listOf(a, b, c, d), closed = true).fill(FILL_C).width(1.4f)
        val e = lerp(a, d, 0.5f); val f = lerp(b, c, 0.5f)
        line(e.x, e.y, f.x, f.y).stroke(HILITE).width(1.6f)
        ticks(a.x, a.y, e.x, e.y, 1); ticks(e.x, e.y, d.x, d.y, 1)
        ticks(b.x, b.y, f.x, f.y, 2); ticks(f.x, f.y, c.x, c.y, 2)
        dot(e.x, e.y, 0.011f); dot(f.x, f.y, 0.011f)
        val g = centroid(a, b, c, d)
        lb.vertex(a, g, "A"); lb.vertex(b, g, "B"); lb.vertex(c, g, "C"); lb.vertex(d, g, "D")
        lb.c(e.x - 0.06f, e.y, "E"); lb.c(f.x + 0.06f, f.y, "F")
        lb.c((a.x + b.x) / 2f, a.y + 0.06f, "a", 0.075f)
        lb.c((c.x + d.x) / 2f, c.y - 0.06f, "c", 0.075f)
        lb.c((e.x + f.x) / 2f, e.y - 0.06f, "(a + c) / 2", 0.07f, HILITE)
    }

    // ------------------------------------------------------------------ Açınımlar

    private val coneNet = sci(
        "sci.geo.cone_net", "Koni açınımı", 1.6f, 1f,
        tags = listOf("koni", "açınım", "ana doğru", "yanal alan", "daire dilimi"),
    ) { lb ->
        // cisim
        val ap = P(0.3f, 0.06f); val bc = P(0.3f, 0.84f); val rx = 0.24f; val ry = 0.07f
        path(closed = true) { moveTo(ap.x, ap.y); lineTo(bc.x + rx, bc.y); arcTo(bc.x, bc.y, rx, ry, 0f, 180f) }.fill(FILL_B).noStroke()
        arc(bc.x, bc.y, rx, ry, 0f, 180f).width(1.3f)
        arc(bc.x, bc.y, rx, ry, 180f, 180f).dashed().width(0.8f)
        line(ap.x, ap.y, bc.x - rx, bc.y).width(1.3f)
        line(ap.x, ap.y, bc.x + rx, bc.y).width(1.3f)
        line(ap.x, ap.y, bc.x, bc.y).dashed().width(0.8f)
        line(bc.x, bc.y, bc.x + rx, bc.y).dashed().width(0.8f)
        rightMark(bc.x, bc.y, 0f, -1f, 1f, 0f, 0.03f)
        dot(bc.x, bc.y, 0.01f)
        lb.c(bc.x - 0.035f, (ap.y + bc.y) / 2f, "h", 0.07f)
        lb.c(bc.x + rx / 2f, bc.y - 0.04f, "r", 0.07f)
        lb.c((ap.x + bc.x + rx) / 2f + 0.05f, (ap.y + bc.y) / 2f, "l", 0.07f)
        // açınım: yarıçapı l, merkez açısı 360·r/l olan daire dilimi + taban dairesi
        val p = P(1.12f, 0.04f); val l = 0.5f; val rn = l / 3f
        arc(p.x, p.y, l, l, 30f, 120f, pie = true).fill(FILL_B).width(1.3f)
        circle(p.x, p.y + l + rn, rn).fill(FILL_A).width(1.3f)
        line(p.x, p.y + l + rn, p.x + rn, p.y + l + rn).dashed().width(0.8f)
        dot(p.x, p.y + l + rn, 0.01f)
        lb.c(p.x + rn / 2f, p.y + l + rn - 0.04f, "r", 0.07f)
        val q = onCircle(p.x, p.y, l / 2f, 150f)
        lb.c(q.x - 0.035f, q.y - 0.03f, "l", 0.07f)
        arc(p.x, p.y, 0.06f, 0.06f, 30f, 120f).width(0.8f)
        lb.c(p.x, p.y + 0.1f, "α", 0.06f)
    }

    private val cylinderNet = sci(
        "sci.geo.cylinder_net", "Silindir açınımı", 1.6f, 1f,
        tags = listOf("silindir", "açınım", "yanal yüz", "taban", "dikdörtgen"),
    ) { lb ->
        val cx = 0.28f; val top = 0.16f; val bot = 0.84f; val rx = 0.2f; val ry = 0.06f
        path(closed = true) { moveTo(cx - rx, top); lineTo(cx - rx, bot); arcTo(cx, bot, rx, ry, 180f, -180f); lineTo(cx + rx, top) }.fill(FILL_C).noStroke()
        ellipse(cx, top, rx, ry).fill(FILL_A).width(1.3f)
        line(cx - rx, top, cx - rx, bot).width(1.3f)
        line(cx + rx, top, cx + rx, bot).width(1.3f)
        arc(cx, bot, rx, ry, 0f, 180f).width(1.3f)
        arc(cx, bot, rx, ry, 180f, 180f).dashed().width(0.8f)
        line(cx, top, cx + rx, top).dashed().width(0.8f)
        dot(cx, top, 0.01f)
        lb.c(cx + rx / 2f, top - 0.035f, "r", 0.065f)
        lb.c(cx + rx + 0.04f, (top + bot) / 2f, "h", 0.07f)
        // açınım
        val rn = 0.12f; val w = 2f * Math.PI.toFloat() * rn; val x0 = 0.72f; val y0 = 0.28f; val hh = 0.44f
        rect(x0, y0, w, hh).fill(FILL_C).width(1.3f)
        val mx = x0 + w / 2f
        circle(mx, y0 - rn, rn).fill(FILL_A).width(1.3f)
        circle(mx, y0 + hh + rn, rn).fill(FILL_A).width(1.3f)
        line(mx, y0 - rn, mx + rn, y0 - rn).dashed().width(0.8f)
        lb.c(mx + rn / 2f, y0 - rn - 0.035f, "r", 0.065f)
        lb.c(x0 + w + 0.04f, y0 + hh / 2f, "h", 0.07f)
        lb.c(x0 + w * 0.2f, y0 + hh / 2f, "2πr", 0.07f)
        dim(x0, y0 + hh / 2f + 0.07f, x0 + w, y0 + hh / 2f + 0.07f, 0.03f)
    }

    private val pyramidNet = sci(
        "sci.geo.pyramid_net", "Kare piramit açınımı", 1f, 1f, Fit.SQUARE,
        tags = listOf("piramit", "açınım", "kare dik piramit", "yanal yüz", "taban"),
    ) { lb ->
        val l = 0.34f; val r = 0.66f; val t = 0.34f; val b = 0.66f
        rect(l, t, r - l, b - t).fill(FILL_A).noStroke()
        for (tri in listOf(
            listOf(P(l, t), P(r, t), P(0.5f, 0.1f)), listOf(P(r, t), P(r, b), P(0.9f, 0.5f)),
            listOf(P(r, b), P(l, b), P(0.5f, 0.9f)), listOf(P(l, b), P(l, t), P(0.1f, 0.5f)),
        )) points(tri, closed = true).fill(FILL_B).noStroke()
        points(listOf(P(l, t), P(0.5f, 0.1f), P(r, t), P(0.9f, 0.5f), P(r, b), P(0.5f, 0.9f), P(l, b), P(0.1f, 0.5f)), closed = true).width(1.4f)
        rect(l, t, r - l, b - t).dashed().width(0.9f)
        line(0.5f, t, 0.5f, 0.1f).dotted().width(0.8f)
        lb.c(0.53f, 0.24f, "h", 0.065f)
        lb.c(0.5f, b - 0.045f, "a", 0.07f)
    }

    private val cubeNet = sci(
        "sci.geo.cube_net", "Küp açınımı", 1f, 0.75f,
        tags = listOf("küp", "açınım", "yüz", "ayrıt"),
    ) { _ ->
        val s = 0.22f; val y0 = 0.265f; val x0 = 0.06f
        val cols = listOf(FILL_A, FILL_B, FILL_C, "#F3E5F5")
        for (i in 0..3) rect(x0 + i * s, y0, s, s).fill(cols[i]).noStroke()
        rect(x0 + s, y0 - s, s, s).fill("#FFFDE7").noStroke()
        rect(x0 + s, y0 + s, s, s).fill("#FCE4EC").noStroke()
        points(
            listOf(
                P(x0 + s, y0 - s), P(x0 + 2 * s, y0 - s), P(x0 + 2 * s, y0), P(x0 + 4 * s, y0), P(x0 + 4 * s, y0 + s),
                P(x0 + 2 * s, y0 + s), P(x0 + 2 * s, y0 + 2 * s), P(x0 + s, y0 + 2 * s), P(x0 + s, y0 + s), P(x0, y0 + s),
                P(x0, y0), P(x0 + s, y0),
            ),
            closed = true,
        ).width(1.4f)
        line(x0 + s, y0, x0 + 2 * s, y0).dashed().width(0.9f)
        line(x0 + s, y0 + s, x0 + 2 * s, y0 + s).dashed().width(0.9f)
        for (i in 1..3) line(x0 + i * s, y0, x0 + i * s, y0 + s).dashed().width(0.9f)
    }

    // ------------------------------------------------------------------ Cisimler

    private val sphere = sci(
        "sci.geo.sphere", "Küre", 1f, 1f, Fit.SQUARE,
        tags = listOf("küre", "yarıçap", "büyük çember", "merkez", "cisim"),
    ) { lb ->
        val c = 0.5f; val r = 0.4f
        circle(c, c, r).fill("#EEF6FB").width(1.4f)
        ellipse(c - r * 0.38f, c - r * 0.42f, r * 0.22f, r * 0.13f, rotate = -35f).fill("#FFFFFF").noStroke().alpha(0.8f)
        arc(c, c, r, r * 0.26f, 0f, 180f).width(1.2f)
        arc(c, c, r, r * 0.26f, 180f, 180f).dashed().width(0.8f)
        line(c, c, c + r, c).width(1.1f)
        dot(c, c, 0.012f)
        lb.c(c - 0.04f, c + 0.045f, "O", 0.075f)
        lb.c(c + r / 2f, c - 0.045f, "r", 0.075f)
    }

    private val cuboid = sci(
        "sci.geo.cuboid", "Dikdörtgenler prizması", 1.3f, 1f,
        tags = listOf("prizma", "dikdörtgenler prizması", "kutu", "ayrıt", "hacim", "cisim"),
    ) { lb ->
        val hx = 0.9; val hy = 0.5; val hz = 0.55
        val verts = ArrayList<V3>()
        for (x in listOf(-hx, hx)) for (y in listOf(-hy, hy)) for (z in listOf(-hz, hz)) verts += V3(x, y, z)
        val normals = listOf(V3(1.0, 0.0, 0.0), V3(-1.0, 0.0, 0.0), V3(0.0, 1.0, 0.0), V3(0.0, -1.0, 0.0), V3(0.0, 0.0, 1.0), V3(0.0, 0.0, -1.0))
        val yaw = -32.0; val pitch = 20.0
        val p = solid(verts, facesByNormals(verts, normals), yaw, pitch, 0.12f, 0.1f, 1.06f, 0.8f)
        // öndeki alt köşe: izdüşümde en alttaki köşe
        val k0 = p.indices.maxByOrNull { p[it].y }!!
        val v0 = verts[k0]
        fun other(dx: Boolean, dy: Boolean, dz: Boolean) = verts.indexOfFirst {
            (if (dx) it.x == -v0.x else it.x == v0.x) && (if (dy) it.y == -v0.y else it.y == v0.y) && (if (dz) it.z == -v0.z else it.z == v0.z)
        }
        val g = centroid(*p.toTypedArray())
        val kx = other(true, false, false)
        val kxy = verts.indexOfFirst { it.x == verts[kx].x && it.y == -verts[kx].y && it.z == verts[kx].z }
        for ((e, name) in listOf((k0 to kx) to "a", (k0 to other(false, false, true)) to "b", (kx to kxy) to "c")) {
            val m = lerp(p[e.first], p[e.second], 0.5f)
            val u = unit(m.x - g.x, m.y - g.y)
            lb.c(m.x + u.x * 0.06f, m.y + u.y * 0.06f, name, 0.08f)
        }
    }

    private val frustum = sci(
        "sci.geo.frustum", "Kesik piramit", 1f, 1f, Fit.SQUARE,
        tags = listOf("kesik piramit", "piramit", "cisim", "gizli ayrıt"),
    ) { _ ->
        val h = 0.75; val t = 0.5
        val verts = ArrayList<V3>()
        for (x in listOf(-1.0, 1.0)) for (z in listOf(-1.0, 1.0)) { verts += V3(x, -h, z); verts += V3(x * t, h, z * t) }
        val normals = listOf(
            V3(0.0, 1.0, 0.0), V3(0.0, -1.0, 0.0),
            V3(2 * h, 1 - t, 0.0), V3(-2 * h, 1 - t, 0.0), V3(0.0, 1 - t, 2 * h), V3(0.0, 1 - t, -2 * h),
        )
        solid(verts, facesByNormals(verts, normals), -28.0, 20.0, 0.08f, 0.1f, 0.84f, 0.8f)
    }

    private val octahedron = sci(
        "sci.geo.octahedron", "Düzgün sekizyüzlü", 1f, 1f, Fit.SQUARE,
        tags = listOf("sekizyüzlü", "oktahedron", "düzgün çokyüzlü", "platon cismi", "cisim"),
    ) { _ ->
        val verts = listOf(V3(1.0, 0.0, 0.0), V3(-1.0, 0.0, 0.0), V3(0.0, 1.0, 0.0), V3(0.0, -1.0, 0.0), V3(0.0, 0.0, 1.0), V3(0.0, 0.0, -1.0))
        val normals = ArrayList<V3>()
        for (x in listOf(-1.0, 1.0)) for (y in listOf(-1.0, 1.0)) for (z in listOf(-1.0, 1.0)) normals += V3(x, y, z)
        solid(verts, facesByNormals(verts, normals), 22.0, 16.0, 0.1f, 0.06f, 0.8f, 0.88f)
    }

    private val dodecahedron = sci(
        "sci.geo.dodecahedron", "Düzgün onikiyüzlü", 1f, 1f, Fit.SQUARE,
        tags = listOf("onikiyüzlü", "dodekahedron", "düzgün çokyüzlü", "platon cismi", "beşgen", "cisim"),
    ) { _ ->
        val phi = (1 + sqrt(5.0)) / 2; val ip = 1 / phi
        val verts = ArrayList<V3>()
        for (x in listOf(-1.0, 1.0)) for (y in listOf(-1.0, 1.0)) for (z in listOf(-1.0, 1.0)) verts += V3(x, y, z)
        for (a in listOf(-1.0, 1.0)) for (b in listOf(-1.0, 1.0)) {
            verts += V3(0.0, a * ip, b * phi); verts += V3(a * ip, b * phi, 0.0); verts += V3(a * phi, 0.0, b * ip)
        }
        val normals = ArrayList<V3>()
        for (a in listOf(-1.0, 1.0)) for (b in listOf(-1.0, 1.0)) {
            normals += V3(0.0, a * phi, b); normals += V3(a, 0.0, b * phi); normals += V3(a * phi, b, 0.0)
        }
        solid(verts, facesByNormals(verts, normals), 14.0, 48.0, 0.08f, 0.08f, 0.84f, 0.84f)
    }
}
