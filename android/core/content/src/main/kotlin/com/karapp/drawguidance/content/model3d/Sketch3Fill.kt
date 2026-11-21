package com.karapp.drawguidance.content.model3d

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * v56 aşama 12 dolgu: kapalı bir 3B çizgiden ya da düzlemde çizilen kementten iki yüzlü düz bir parça ("fill-"
 * kimlikli, cisim değil) üretilir. Çokgen [MeshOps.triangulate] ile üçgenlenir (içbükey kenarlar kulak kesme). Parça
 * merkezde durur; böylece seçilip taşınabilir, silgi ve sil düğmesi onu da kaldırır.
 */
object Sketch3Fill {

    const val PREFIX = "fill-"
    /** Bir dolgudaki en çok köşe (uzun çizgiler seyreltilir). */
    const val MAX_POINTS = 160

    fun isFill(part: Part): Boolean = part.id.startsWith(PREFIX)

    /** Çizginin uçları yeterince yakınsa kapalı sayılır (çevrenin %12'si ya da 3 kalınlık). */
    fun isClosed(points: FloatArray, width: Float): Boolean {
        val n = points.size / 3
        if (n < 3) return false
        var len = 0f
        for (i in 1 until n) len += dist(points, i - 1, i)
        if (len < 1e-4f) return false
        return dist(points, 0, n - 1) <= max(len * 0.12f, width * 3f)
    }

    /** Ardışık çok yakın noktaları atar, sonra en çok [MAX_POINTS] noktaya seyreltir; kapanış tekrarını da atar. */
    fun simplify(points: FloatArray): FloatArray {
        val n = points.size / 3
        if (n == 0) return points
        var len = 0f
        for (i in 1 until n) len += dist(points, i - 1, i)
        val eps = len / (MAX_POINTS * 4f)
        val keep = ArrayList<Int>(n)
        keep += 0
        for (i in 1 until n) if (dist(points, keep.last(), i) > eps) keep += i
        if (keep.size > 2 && dist(points, keep.first(), keep.last()) <= eps * 2f) keep.removeAt(keep.size - 1)
        val step = max(1, (keep.size + MAX_POINTS - 1) / MAX_POINTS)
        val out = ArrayList<Float>()
        var k = 0
        while (k < keep.size) { val i = keep[k] * 3; out += points[i]; out += points[i + 1]; out += points[i + 2]; k += step }
        return out.toFloatArray()
    }

    /** Newell normali (birim; düz değilse en iyi uyan düzlem). */
    fun normal(points: FloatArray): V3 {
        val n = points.size / 3
        var nx = 0f; var ny = 0f; var nz = 0f
        for (i in 0 until n) {
            val a = i * 3; val b = ((i + 1) % n) * 3
            nx += (points[a + 1] - points[b + 1]) * (points[a + 2] + points[b + 2])
            ny += (points[a + 2] - points[b + 2]) * (points[a] + points[b])
            nz += (points[a] - points[b]) * (points[a + 1] + points[b + 1])
        }
        return V3(nx, ny, nz).normalized()
    }

    /**
     * Kapalı halka [loop] (dünya noktaları) için iki yüzlü dolgu parçası; 3'ten az nokta ya da sıfır alan için null.
     * Noktalar en iyi uyan düzleme yassılanır (elle çizilen hafif eğri halkalar da düz dolgu verir).
     */
    fun fromLoop(loop: FloatArray, id: String, color: Int, opacity: Float = 1f, layer: String? = null, name: String = "Dolgu"): Part? {
        val pts = simplify(loop)
        val n = pts.size / 3
        if (n < 3) return null
        val nrm = normal(pts)
        if (nrm.length < 0.5f) return null
        var cx = 0f; var cy = 0f; var cz = 0f
        for (i in 0 until n) { cx += pts[3 * i]; cy += pts[3 * i + 1]; cz += pts[3 * i + 2] }
        val c = V3(cx / n, cy / n, cz / n)
        val pos = FloatArray(n * 3)
        for (i in 0 until n) {
            val p = V3(pts[3 * i], pts[3 * i + 1], pts[3 * i + 2]) - c
            val q = p - nrm * (p dot nrm)
            pos[3 * i] = q.x; pos[3 * i + 1] = q.y; pos[3 * i + 2] = q.z
        }
        if (area(pos, nrm) < 1e-6f) return null
        val front = IntArray(n) { it }
        val back = IntArray(n) { n - 1 - it }
        val mesh = Mesh(pos, arrayOf(front, back))
        return Part(
            "$PREFIX$id", name, mesh, null, position = c,
            material = Material(color = color, opacity = opacity.coerceIn(0.05f, 1f), kind = MaterialKind.MATTE, shine = 0f),
            uv = UvMode.PLANAR, layer = layer,
        )
    }

    /** Ekran çokgeni [poly] (x, y…) içinde mi (çift-tek kuralı). */
    fun inside(poly: FloatArray, x: Float, y: Float): Boolean {
        val n = poly.size / 2
        if (n < 3) return false
        var c = false
        var j = n - 1
        for (i in 0 until n) {
            val xi = poly[2 * i]; val yi = poly[2 * i + 1]; val xj = poly[2 * j]; val yj = poly[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi + 1e-12f) + xi) c = !c
            j = i
        }
        return c
    }

    /** Ekran çokgeninin alanı (piksel²). */
    fun screenArea(poly: FloatArray): Float {
        val n = poly.size / 2
        var a = 0f
        for (i in 0 until n) { val j = (i + 1) % n; a += poly[2 * i] * poly[2 * j + 1] - poly[2 * j] * poly[2 * i + 1] }
        return abs(a) / 2f
    }

    private fun area(pos: FloatArray, n: V3): Float {
        val k = pos.size / 3
        var s = V3.ZERO
        for (i in 0 until k) {
            val j = (i + 1) % k
            s += V3(pos[3 * i], pos[3 * i + 1], pos[3 * i + 2]) cross V3(pos[3 * j], pos[3 * j + 1], pos[3 * j + 2])
        }
        return abs(s dot n) / 2f
    }

    private fun dist(p: FloatArray, i: Int, j: Int): Float {
        val dx = p[3 * i] - p[3 * j]; val dy = p[3 * i + 1] - p[3 * j + 1]; val dz = p[3 * i + 2] - p[3 * j + 2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

/**
 * v56 aşama 13 yüzeye boya (GENİŞ fırça): ekrandaki fırça yolu cisimlerin yüzeyine ışınla düşürülür ve yüzey
 * normali boyunca biraz dışarı alınır (yüzeyin içine gömülmesin). Işının boşa gittiği yerlerde çizgi bölünür.
 */
object SurfacePaint {

    /** Yüzeyden dışarı alma payı (dünya birimi). */
    const val OFFSET = 0.004f

    /**
     * [screen] (x, y…) noktalarını [model] yüzeyine düşürür; dönen her parça en az iki noktalı dünya noktaları ve
     * aynı sıradaki ekran noktası sıraları (basıncı eşlemek için).
     */
    fun cast(model: Model3D, view: View3, screen: FloatArray, offset: Float = OFFSET): List<Pair<FloatArray, IntArray>> {
        val out = ArrayList<Pair<FloatArray, IntArray>>()
        var cur = ArrayList<Float>(); var idx = ArrayList<Int>()
        fun flush() {
            if (idx.size >= 2) out += cur.toFloatArray() to idx.toIntArray()
            cur = ArrayList(); idx = ArrayList()
        }
        for (i in 0 until screen.size / 2) {
            val hit = Pick3.pick(model, view, screen[2 * i], screen[2 * i + 1])
            if (hit == null) { flush(); continue }
            val n = PlaneSnap.onFace(model, hit).normal
            val toCam = view.ray(screen[2 * i], screen[2 * i + 1]).second * -1f
            val nn = if ((n dot toCam) < 0f) -n else n
            val p = hit.point + nn * offset
            cur += p.x; cur += p.y; cur += p.z; idx += i
        }
        flush()
        return out
    }
}
