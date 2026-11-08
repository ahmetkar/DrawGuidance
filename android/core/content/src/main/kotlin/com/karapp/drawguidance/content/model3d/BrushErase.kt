package com.karapp.drawguidance.content.model3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v26 ağın imzası: nokta ve yüz sayısı ile konumların ağırlıklı toplamı. Fırça kaynağı ancak ağ hâlâ fırçanın ürettiği
 * ağsa geçerlidir (başka araçla düzenlenince silgi o parçaya dokunmaz).
 */
data class MeshSig(val vertices: Int, val faces: Int, val sum: Double) {
    fun matches(m: Mesh): Boolean {
        val o = of(m)
        return o.vertices == vertices && o.faces == faces && abs(o.sum - sum) <= 1e-4 * (1.0 + abs(sum))
    }

    companion object {
        fun of(m: Mesh): MeshSig {
            var s = 0.0
            val p = m.pos
            for (i in p.indices) s += p[i].toDouble() * ((i % 7) + 1)
            return MeshSig(m.vertexCount, m.faceCount, s)
        }
    }
}

/**
 * v26 fırçayla çizilen parçanın kaynağı (parçanın yerel uzayında): ham iz noktaları (x, y, z…), basınçlar, düzlemin
 * normali ve yukarısı, cisim ayarları; [closed] kapalı/açık zorlaması (null: otomatik), [axis] torna ekseni, [time]
 * çizildiği an ("Son çizimi sil"), [sig] üretilen ağın imzası.
 */
class BrushSource(
    val spec: BrushShapeSpec,
    val points: FloatArray,
    val pressures: FloatArray?,
    val normal: V3,
    val up: V3,
    val closed: Boolean? = null,
    val axis: V3? = null,
    val time: Long = 0L,
    val sig: MeshSig? = null,
) {
    val count: Int get() = points.size / 3

    fun point(i: Int) = V3(points[3 * i], points[3 * i + 1], points[3 * i + 2])

    fun input(): BrushInput = BrushInput(List(count) { point(it) }, pressures, normal, up, closed, axis)

    /** İz kapalı mı (zorlanmışsa o, değilse uçlara göre). */
    val isClosed: Boolean get() = closed ?: BrushShapes.isClosed(List(count) { point(it) }, spec)

    fun with(
        points: FloatArray = this.points, pressures: FloatArray? = this.pressures, closed: Boolean? = this.closed,
        axis: V3? = this.axis, sig: MeshSig? = this.sig,
    ) = BrushSource(spec, points, pressures, normal, up, closed, axis, time, sig)

    /** Noktaları [d] kadar geri kaydırır (dünya → parçanın yerel uzayı, döndürme yokken). */
    fun shifted(d: V3): BrushSource {
        val p = points.copyOf()
        for (i in 0 until count) { p[3 * i] -= d.x; p[3 * i + 1] -= d.y; p[3 * i + 2] -= d.z }
        return with(points = p, axis = axis?.let { it - d })
    }

    fun signed(mesh: Mesh): BrushSource = with(sig = MeshSig.of(mesh))

    /** Kaynak bu ağı üretti mi (ağ başka araçla değiştirilmediyse). */
    fun valid(mesh: Mesh): Boolean = sig?.matches(mesh) == true
}

/**
 * v26 silgi (yalnız çizimlere): ekrandaki silgi izinin üstünden geçtiği kısım silinir.
 * - **3B çizgi:** silinen noktalar çıkar, kalan kısımlar ayrı çizgiler olur.
 * - **Tüp, şerit, açık duvar:** izin silinen örnekleri çıkar; kalan her kısım ayrı parça olarak yeniden kurulur (hızlı,
 *   sürerken uygulanabilir).
 * - **Torna:** profilin, dönen halkası silgiye değen örnekleri çıkar (vazo bantlara ayrılır).
 * - **Şişir, kapalı duvar (prizma):** silgi izinin çizim düzlemindeki izdüşümü boydan boya oyulur ([carvePart]); biçimin
 *   kalanı aynen kalır.
 * Önce ekran kutusuyla sınanır; yalnız silgiye değen çizimler işlenir.
 */
object BrushErase {

    /** Silgi izi: ekran noktaları ve yarıçap (piksel). */
    class Eraser(val xs: FloatArray, val ys: FloatArray, val radius: Float) {
        val size: Int get() = xs.size
        private val minX = xs.minOrNull() ?: 0f
        private val maxX = xs.maxOrNull() ?: 0f
        private val minY = ys.minOrNull() ?: 0f
        private val maxY = ys.maxOrNull() ?: 0f

        /** Ekran kutusu (silgi yarıçapıyla) değiyor mu. */
        fun hitsBox(x0: Float, y0: Float, x1: Float, y1: Float, extra: Float = 0f): Boolean {
            val r = radius + extra
            return !(x1 < minX - r || x0 > maxX + r || y1 < minY - r || y0 > maxY + r)
        }

        /** Ekran parçasının (a–b) silgi izine uzaklığı. */
        fun distToSegment(ax: Float, ay: Float, bx: Float, by: Float): Float {
            if (size == 0) return Float.MAX_VALUE
            if (size == 1) return segPointDist(ax, ay, bx, by, xs[0], ys[0])
            var best = Float.MAX_VALUE
            for (i in 0 until size - 1) {
                best = min(best, segSegDist(xs[i], ys[i], xs[i + 1], ys[i + 1], ax, ay, bx, by))
                if (best <= 0f) return 0f
            }
            return best
        }

        fun touches(ax: Float, ay: Float, bx: Float = ax, by: Float = ay, extra: Float = 0f): Boolean =
            distToSegment(ax, ay, bx, by) <= radius + extra
    }

    // =========================================================================================== 3B çizgi

    /**
     * Çizginin silgiye değen kısmını siler. Değişmediyse null; kalan kısımlar (en az iki nokta) liste olarak döner
     * (ilk kısım çizginin kimliğini korur).
     */
    fun eraseStroke(s: Stroke3, view: View3, e: Eraser, newId: () -> String): List<Stroke3>? {
        val n = s.count
        if (n < 2) return null
        val sp = FloatArray(4)
        // Ekran kutusu
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        val scr = FloatArray(n * 2)
        val vis = BooleanArray(n)
        for (i in 0 until n) {
            view.project(s.points[3 * i], s.points[3 * i + 1], s.points[3 * i + 2], sp)
            scr[2 * i] = sp[0]; scr[2 * i + 1] = sp[1]; vis[i] = sp[3] > 0f
            x0 = min(x0, sp[0]); y0 = min(y0, sp[1]); x1 = max(x1, sp[0]); y1 = max(y1, sp[1])
        }
        if (!e.hitsBox(x0, y0, x1, y1)) return null
        // Seyrek noktalar ekranda sıklaştırılır (silgi iki nokta arasından geçebilir)
        val pts = ArrayList<Float>(n * 6)
        val keep = ArrayList<Boolean>(n * 2)
        val step = max(2f, e.radius * 0.5f)
        for (i in 0 until n) {
            if (i > 0) {
                val dx = scr[2 * i] - scr[2 * i - 2]; val dy = scr[2 * i + 1] - scr[2 * i - 1]
                val k = ceil(sqrt(dx * dx + dy * dy) / step).toInt().coerceIn(1, 64)
                for (j in 1 until k) {
                    val t = j / k.toFloat()
                    val px = scr[2 * i - 2] + dx * t; val py = scr[2 * i - 1] + dy * t
                    for (c in 0..2) pts += s.points[3 * (i - 1) + c] + (s.points[3 * i + c] - s.points[3 * (i - 1) + c]) * t
                    keep += !(vis[i] && vis[i - 1] && e.touches(px, py))
                }
            }
            for (c in 0..2) pts += s.points[3 * i + c]
            keep += !(vis[i] && e.touches(scr[2 * i], scr[2 * i + 1]))
        }
        if (keep.all { it }) return null
        val out = ArrayList<Stroke3>()
        var run = ArrayList<Float>()
        fun flush() {
            if (run.size >= 6) out += Stroke3(if (out.isEmpty()) s.id else newId(), run.toFloatArray(), s.color, s.width, s.time)
            run = ArrayList()
        }
        for (i in keep.indices) {
            if (keep[i]) for (c in 0..2) run += pts[3 * i + c] else flush()
        }
        flush()
        return out
    }

    // =========================================================================================== fırça parçaları

    /**
     * Fırçayla çizilmiş parçanın silgiye değen kısmını siler. Parça çizim değilse, kaynağı geçersizse ya da silgi
     * değmiyorsa null; kalan parçalar (hiç kalmadıysa boş liste) döner. Parçalar aynı konum, dönüş, ölçek, malzeme ve
     * grubu taşır; ilk parça kimliği korur.
     */
    fun erasePart(part: Part, view: View3, e: Eraser, newId: () -> String): List<Part>? {
        val src = part.brush ?: return null
        if (!part.visible || src.count < 2 || !src.valid(part.mesh) || isArea(part)) return null
        if (!onScreen(part, view, e)) return null
        val t = Transform3.of(part)
        val k = ((abs(part.scale.x) + abs(part.scale.y) + abs(part.scale.z)) / 3f).coerceAtLeast(1e-4f)
        val sources: List<BrushSource> = when (src.spec.kind) {
            Brush3Kind.TUBE, Brush3Kind.RIBBON -> openRuns(src, t, k, view, e, spine = 0f)
            Brush3Kind.WALL -> openRuns(src, t, k, view, e, spine = src.spec.height)
            Brush3Kind.LATHE -> latheRuns(src, t, view, e)
            else -> null
        } ?: return null
        val out = ArrayList<Part>()
        for (s in sources) {
            val mesh = BrushShapes.rebuild(s) ?: continue
            out += piece(part, s, mesh, if (out.isEmpty()) part.id else newId())
        }
        return out
    }

    /** Alan olarak silinen çizim mi (şişir, kapalı duvar/prizma): bunlar [carvePart] ile oyulur. */
    fun isArea(part: Part): Boolean {
        val src = part.brush ?: return false
        return src.spec.kind == Brush3Kind.INFLATE || (src.spec.kind == Brush3Kind.WALL && src.isClosed)
    }

    /** Parçanın ekran kutusu silgi izine değiyor mu (kaba, hızlı sınama). */
    fun onScreen(part: Part, view: View3, e: Eraser): Boolean {
        val t = Transform3.of(part)
        val b = part.mesh.bounds
        val sp = FloatArray(4)
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (k in 0 until 8) {
            val w = t.apply(V3(if (k and 1 == 0) b.min.x else b.max.x, if (k and 2 == 0) b.min.y else b.max.y, if (k and 4 == 0) b.min.z else b.max.z))
            view.project(w.x, w.y, w.z, sp)
            if (sp[3] <= 0f) continue
            x0 = min(x0, sp[0]); y0 = min(y0, sp[1]); x1 = max(x1, sp[0]); y1 = max(y1, sp[1])
        }
        return x0 <= x1 && e.hitsBox(x0, y0, x1, y1)
    }

    /** Kaynak uzayında kurulan ağdan parça: ağ tabanının ortasına taşınır, konum dönüşümle düzeltilir. */
    private fun piece(part: Part, s: BrushSource, mesh: Mesh, id: String): Part {
        val b = mesh.bounds
        val o = V3(b.center.x, b.min.y, b.center.z)
        val local = mesh.mapPositions { it - o }
        val t = Transform3.of(part)
        val newPos = t.apply(o)
        return part.copy(
            id = id, mesh = local, primitive = null, position = newPos, faceMaterials = emptyMap(),
            brush = s.shifted(o).signed(local),
        )
    }

    // ------------------------------------------------------------------------------------------ açık iz (tüp, şerit, duvar)

    /**
     * İz örneklerini silgiye göre ayırır. [spine] > 0 ise (duvar) her örnek normal yönünde o yükseklikte bir çizgi
     * olarak sınanır. Değişmediyse null.
     */
    private fun openRuns(src: BrushSource, t: Transform3, k: Float, view: View3, e: Eraser, spine: Float): List<BrushSource>? {
        val (n, _, _) = BrushShapes.frame(src.normal, src.up)
        val pts = densify(src, t, view, e.radius * 0.5f)
        val count = pts.first.size / 3
        val prs = pts.second
        val keep = BooleanArray(count)
        val a = FloatArray(4); val c = FloatArray(4)
        val half = src.spec.width * 0.5f * k
        for (i in 0 until count) {
            val w = t.apply(V3(pts.first[3 * i], pts.first[3 * i + 1], pts.first[3 * i + 2]))
            view.project(w.x, w.y, w.z, a)
            if (a[3] <= 0f) { keep[i] = true; continue }
            val extra = half * a[3] * view.scale
            keep[i] = if (spine > 0f) {
                val top = t.apply(V3(pts.first[3 * i], pts.first[3 * i + 1], pts.first[3 * i + 2]) + n * spine)
                view.project(top.x, top.y, top.z, c)
                !e.touches(a[0], a[1], if (c[3] > 0f) c[0] else a[0], if (c[3] > 0f) c[1] else a[1], extra)
            } else !e.touches(a[0], a[1], extra = extra)
        }
        if (keep.all { it }) return null
        return runs(keep, src.isClosed).map { idx ->
            val p = FloatArray(idx.size * 3)
            idx.forEachIndexed { j, i -> for (q in 0..2) p[3 * j + q] = pts.first[3 * i + q] }
            src.with(points = p, pressures = prs?.let { pr -> FloatArray(idx.size) { pr[idx[it]] } }, closed = false, sig = null)
        }.filter { it.count >= 2 }
    }

    /** Tutulan örneklerin ardışık dizileri (kapalı izde baştaki ve sondaki dizi birleşir). */
    private fun runs(keep: BooleanArray, cyclic: Boolean): List<List<Int>> {
        val out = ArrayList<ArrayList<Int>>()
        var cur: ArrayList<Int>? = null
        for (i in keep.indices) {
            if (keep[i]) { if (cur == null) { cur = ArrayList(); out += cur }; cur += i } else cur = null
        }
        if (cyclic && out.size >= 2 && keep.first() && keep.last()) {
            val first = out.removeAt(0)
            out.last().addAll(first)
        }
        return out
    }

    /** Ekranda birbirinden [step] pikselden uzak iz örnekleri arasına ara örnekler (basınçlar da). */
    private fun densify(src: BrushSource, t: Transform3, view: View3, step: Float): Pair<FloatArray, FloatArray?> {
        val n = src.count
        val out = ArrayList<Float>(n * 3); val outP = ArrayList<Float>(n)
        val a = FloatArray(4); val b = FloatArray(4)
        val st = max(1.5f, step)
        for (i in 0 until n) {
            if (i > 0) {
                val pw = t.apply(src.point(i - 1)); val qw = t.apply(src.point(i))
                view.project(pw.x, pw.y, pw.z, a); view.project(qw.x, qw.y, qw.z, b)
                val d = if (a[3] > 0f && b[3] > 0f) sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1])) else 0f
                val k = ceil(d / st).toInt().coerceIn(1, 48)
                for (j in 1 until k) {
                    val f = j / k.toFloat()
                    for (c in 0..2) out += src.points[3 * (i - 1) + c] + (src.points[3 * i + c] - src.points[3 * (i - 1) + c]) * f
                    src.pressures?.let { outP += it[i - 1] + (it[i] - it[i - 1]) * f }
                }
            }
            for (c in 0..2) out += src.points[3 * i + c]
            src.pressures?.let { outP += it[i] }
        }
        return out.toFloatArray() to (if (src.pressures != null) outP.toFloatArray() else null)
    }

    // ------------------------------------------------------------------------------------------ torna

    /** Profil örneklerinin dönen halkaları silgiye değiyorsa örnek çıkar; kalan diziler ayrı torna parçaları. */
    private fun latheRuns(src: BrushSource, t: Transform3, view: View3, e: Eraser): List<BrushSource>? {
        val (n, up, right) = BrushShapes.frame(src.normal, src.up)
        val axis = src.axis ?: src.point(0)
        val pts = densify(src, t, view, e.radius * 0.5f).first
        val count = pts.size / 3
        val keep = BooleanArray(count) { true }
        val ring = 20
        val a = FloatArray(4); val b = FloatArray(4)
        for (i in 0 until count) {
            val d = V3(pts[3 * i], pts[3 * i + 1], pts[3 * i + 2]) - axis
            val r = abs(d dot right); val y = d dot up
            var hit = false
            var prevOk = false
            for (s in 0..ring) {
                val ang = 2.0 * PI * s / ring
                val lp = axis + up * y + right * (r * cos(ang).toFloat()) + n * (r * sin(ang).toFloat())
                val w = t.apply(lp)
                view.project(w.x, w.y, w.z, b)
                val ok = b[3] > 0f
                if (ok && prevOk && e.touches(a[0], a[1], b[0], b[1])) { hit = true; break }
                a[0] = b[0]; a[1] = b[1]; prevOk = ok
            }
            keep[i] = !hit
        }
        if (keep.all { it }) return null
        return runs(keep, false).map { idx ->
            val p = FloatArray(idx.size * 3)
            idx.forEachIndexed { j, i -> for (q in 0..2) p[3 * j + q] = pts[3 * i + q] }
            src.with(points = p, pressures = null, closed = false, axis = axis, sig = null)
        }.filter { it.count >= 2 }
    }

    // ------------------------------------------------------------------------------------------ alan (şişir, prizma)

    /**
     * Alan çizimini (şişir, prizma) silgi izinin çizim düzlemindeki izdüşümü boyunca boydan boya oyar: biçimin kalanı
     * aynen kalır, kopan kısımlar ayrı parça olur. Silgi izi düzlemde bir ızgarada uzaklık alanına çevrilir, sınırı
     * (marching squares) prizma kesicilere dönüşür ve katı cisim çıkarmasıyla oyulur. Değmiyorsa null; tümü silindiyse
     * boş liste. Ağır işlem: arka planda çağrılmalı.
     */
    fun carvePart(part: Part, view: View3, e: Eraser, newId: () -> String): List<Part>? {
        val src = part.brush ?: return null
        if (!part.visible || !src.valid(part.mesh) || !isArea(part) || !onScreen(part, view, e)) return null
        val t = Transform3.of(part)
        val inv = Inverse3.of(t) ?: return null
        val (n, up, right) = BrushShapes.frame(src.normal, src.up)
        val o = src.point(0)
        val mesh = part.mesh
        // Ağın düzlemdeki kutusu ve normal yönündeki kalınlığı
        var bx0 = Float.MAX_VALUE; var bx1 = -Float.MAX_VALUE; var by0 = Float.MAX_VALUE; var by1 = -Float.MAX_VALUE
        var h0 = Float.MAX_VALUE; var h1 = -Float.MAX_VALUE
        for (i in 0 until mesh.vertexCount) {
            val d = mesh.vertex(i) - o
            val x = d dot right; val y = d dot up; val h = d dot n
            bx0 = min(bx0, x); bx1 = max(bx1, x); by0 = min(by0, y); by1 = max(by1, y); h0 = min(h0, h); h1 = max(h1, h)
        }
        // Silgi izi düzlemde (kalınlığın ortasında)
        val planeO = o + n * ((h0 + h1) * 0.5f)
        val ex = FloatArray(e.size); val ey = FloatArray(e.size); val er = FloatArray(e.size)
        val sp = FloatArray(4)
        for (q in 0 until e.size) {
            val (ro, rd) = view.ray(e.xs[q], e.ys[q])
            val lo = inv.point(ro); val ld = inv.point(ro + rd) - lo
            val den = ld dot n
            if (abs(den) < 1e-6f) { ex[q] = Float.NaN; continue }
            val hit = lo + ld * (((planeO - lo) dot n) / den)
            val d = hit - o
            ex[q] = d dot right; ey[q] = d dot up
            val w = t.apply(hit)
            view.project(w.x, w.y, w.z, sp)
            val pxPerLocal = if (sp[3] > 0f) sp[3] * view.scale * ((t.applyDir(right).length + t.applyDir(up).length) * 0.5f) else 0f
            er[q] = if (pxPerLocal > 1e-6f) e.radius / pxPerLocal else 0f
        }
        // Izgara (kenar düğümleri dışarıda: halkalar kapanır)
        val span = max(bx1 - bx0, by1 - by0).coerceAtLeast(1e-5f)
        val cell = span / GRID
        bx0 -= cell * 2; by0 -= cell * 2; bx1 += cell * 2; by1 += cell * 2
        val gx = ceil((bx1 - bx0) / cell).toInt().coerceAtLeast(4)
        val gy = ceil((by1 - by0) / cell).toInt().coerceAtLeast(4)
        val w = gx + 1
        val f = FloatArray(w * (gy + 1))
        var any = false
        for (jy in 0..gy) for (ix in 0..gx) {
            val x = bx0 + ix * cell; val y = by0 + jy * cell
            var de = Float.MAX_VALUE
            for (q in 0 until e.size) {
                if (ex[q].isNaN()) continue
                val dd = if (q + 1 < e.size && !ex[q + 1].isNaN()) segPointDist(ex[q], ey[q], ex[q + 1], ey[q + 1], x, y) - min(er[q], er[q + 1])
                else sqrt((ex[q] - x) * (ex[q] - x) + (ey[q] - y) * (ey[q] - y)) - er[q]
                de = min(de, dd)
            }
            val border = ix == 0 || jy == 0 || ix == gx || jy == gy
            val v = if (border) -cell else -de
            if (v > 0f) any = true
            f[jy * w + ix] = if (abs(v) < 1e-9f) -1e-9f else v
        }
        if (!any) return null
        val margin = (h1 - h0) * 0.1f + cell
        val cutters = contours(f, gx, gy).mapNotNull { loop ->
            val xs = FloatArray(loop.size / 2) { bx0 + loop[2 * it] * cell }
            val ys = FloatArray(loop.size / 2) { by0 + loop[2 * it + 1] * cell }
            var area = 0f
            for (q in xs.indices) { val r = (q + 1) % xs.size; area += xs[q] * ys[r] - xs[r] * ys[q] }
            // Yalnız dış hatlar (silgi halkasının içindeki ada da silinir)
            if (area * 0.5f < cell * cell) return@mapNotNull null
            val (sx, sy) = simplify(xs, ys, cell * 0.2f)
            if (sx.size < 3) return@mapNotNull null
            val base = List(sx.size) { o + right * sx[it] + up * sy[it] + n * (h0 - margin) }
            BrushShapes.prismOf(base, n, up, (h1 - h0) + 2f * margin)
        }
        if (cutters.isEmpty()) return null
        var acc = Csg.polygons(mesh, null) { 0 }
        for (c in cutters) acc = Csg.subtractLocal(acc, Csg.polygons(c, null) { 0 })
        val res = Csg.toMesh(acc, ArrayList())
        if (res.faceCount == 0) return emptyList()
        val v0 = volume(mesh); val v1 = volume(res)
        if (abs(v1 - v0) <= 1e-5 * max(1e-9, abs(v0))) return null
        val out = ArrayList<Part>()
        for (faces in vertexComponents(res).sortedByDescending { it.size }) {
            if (faces.size < 4) continue
            val sub = MeshTopo.subMesh(res, faces).mesh
            out += part.copy(id = if (out.isEmpty()) part.id else newId(), mesh = sub, primitive = null, faceMaterials = emptyMap(), brush = src.signed(sub))
        }
        return out
    }

    /** Ortak noktası olan yüzler aynı kısım (kenar paylaşımına bakılmaz: kıl payı çatlaklarda kısım bölünmez). */
    private fun vertexComponents(m: Mesh): List<List<Int>> {
        val parent = IntArray(m.vertexCount) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
        for (f in m.faces) for (i in 1 until f.size) { val a = find(f[0]); val b = find(f[i]); if (a != b) parent[a] = b }
        val groups = LinkedHashMap<Int, ArrayList<Int>>()
        m.faces.forEachIndexed { fi, f -> if (f.isNotEmpty()) groups.getOrPut(find(f[0])) { ArrayList() } += fi }
        return groups.values.toList()
    }

    private fun volume(m: Mesh): Double {
        var v = 0.0
        val p = m.pos
        for (f in 0 until m.faceCount) {
            val face = m.faces[f]; val tr = m.triangles[f]
            var k = 0
            while (k + 2 < tr.size) {
                val a = face[tr[k]] * 3; val b = face[tr[k + 1]] * 3; val c = face[tr[k + 2]] * 3
                v += (p[a] * (p[b + 1] * p[c + 2] - p[b + 2] * p[c + 1]) - p[a + 1] * (p[b] * p[c + 2] - p[b + 2] * p[c]) + p[a + 2] * (p[b] * p[c + 1] - p[b + 1] * p[c])).toDouble()
                k += 3
            }
        }
        return v / 6.0
    }

    /**
     * Marching squares: alanın sıfır eğrisinden kapalı halkalar (ızgara biriminde x, y…). İç taraf solda kalır (dış
     * hatlar saat yönünün tersine); belirsiz hücrelerde hücre ortası belirler.
     */
    private fun contours(f: FloatArray, gx: Int, gy: Int): List<FloatArray> {
        val w = gx + 1
        fun v(ix: Int, jy: Int) = f[jy * w + ix]
        // Kenar anahtarları: yatay (ix,jy)-(ix+1,jy) = 2*(jy*w+ix), dikey (ix,jy)-(ix,jy+1) = 2*(jy*w+ix)+1
        fun hKey(ix: Int, jy: Int) = 2 * (jy * w + ix)
        fun vKey(ix: Int, jy: Int) = 2 * (jy * w + ix) + 1
        val next = HashMap<Int, Int>()
        val point = HashMap<Int, FloatArray>()
        fun cross(key: Int, ax: Int, ay: Int, bx: Int, by: Int) {
            if (key in point) return
            val fa = v(ax, ay); val fb = v(bx, by)
            val tt = (fa / (fa - fb)).coerceIn(0f, 1f)
            point[key] = floatArrayOf(ax + (bx - ax) * tt, ay + (by - ay) * tt)
        }
        for (jy in 0 until gy) for (ix in 0 until gx) {
            // Köşeler saat yönünün tersine: v0 (sol alt), v1 (sağ alt), v2 (sağ üst), v3 (sol üst)
            val c = intArrayOf(ix, jy, ix + 1, jy, ix + 1, jy + 1, ix, jy + 1)
            val inside = BooleanArray(4) { v(c[2 * it], c[2 * it + 1]) > 0f }
            if (inside.all { it } || inside.none { it }) continue
            // Kenarlar (köşe k → k+1): e0 alt, e1 sağ, e2 üst, e3 sol
            val keys = intArrayOf(hKey(ix, jy), vKey(ix + 1, jy), hKey(ix, jy + 1), vKey(ix, jy))
            val outs = ArrayList<Int>(); val ins = ArrayList<Int>()
            for (k in 0 until 4) {
                val k1 = (k + 1) % 4
                if (inside[k] == inside[k1]) continue
                cross(keys[k], c[2 * k], c[2 * k + 1], c[2 * k1], c[2 * k1 + 1])
                if (inside[k]) outs += k else ins += k
            }
            if (outs.size == 1) {
                next[keys[outs[0]]] = keys[ins[0]]
            } else {
                // Belirsiz (çapraz) hücre: ortası içerideyse dış köşeler ayrılır
                val center = (v(ix, jy) + v(ix + 1, jy) + v(ix + 1, jy + 1) + v(ix, jy + 1)) * 0.25f
                // outs ve ins saat yönünün tersine sırada: out[i] → in[i] (orta içeride) ya da out[i] → bir önceki in
                if (center > 0f) {
                    for (q in outs.indices) next[keys[outs[q]]] = keys[ins.first { it == (outs[q] + 1) % 4 }]
                } else {
                    for (q in outs.indices) next[keys[outs[q]]] = keys[ins.first { it == (outs[q] + 3) % 4 }]
                }
            }
        }
        val loops = ArrayList<FloatArray>()
        val used = HashSet<Int>()
        for (start in next.keys) {
            if (start in used) continue
            val pts = ArrayList<Float>()
            var cur = start
            var guard = 0
            while (cur !in used && guard++ < next.size + 2) {
                used += cur
                val p = point[cur] ?: break
                pts += p[0]; pts += p[1]
                cur = next[cur] ?: break
            }
            if (cur == start && pts.size >= 6) loops += pts.toFloatArray()
        }
        return loops
    }

    /** Neredeyse doğrusal noktaları atar (kapalı hat); en çok 200 nokta. */
    private fun simplify(xs: FloatArray, ys: FloatArray, tol: Float): Pair<FloatArray, FloatArray> {
        var x = xs.toMutableList(); var y = ys.toMutableList()
        var changed = true
        while (changed && x.size > 3) {
            changed = false
            var q = 0
            while (q < x.size && x.size > 3) {
                val a = (q - 1 + x.size) % x.size; val c = (q + 1) % x.size
                if (segPointDist(x[a], y[a], x[c], y[c], x[q], y[q]) < tol) { x.removeAt(q); y.removeAt(q); changed = true } else q++
            }
        }
        if (x.size > 200) {
            val stepK = x.size / 200f
            val nx = ArrayList<Float>(); val ny = ArrayList<Float>()
            var f = 0f
            while (f.toInt() < x.size) { nx += x[f.toInt()]; ny += y[f.toInt()]; f += stepK }
            x = nx; y = ny
        }
        return x.toFloatArray() to y.toFloatArray()
    }



    /** Izgaranın uzun kenarındaki hücre sayısı. */
    private const val GRID = 72

    // ------------------------------------------------------------------------------------------ 2B uzaklıklar

    fun segPointDist(ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val l2 = dx * dx + dy * dy
        val t = if (l2 < 1e-12f) 0f else (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f)
        val qx = ax + dx * t - px; val qy = ay + dy * t - py
        return sqrt(qx * qx + qy * qy)
    }

    private fun segSegDist(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float): Float {
        if (intersects(ax, ay, bx, by, cx, cy, dx, dy)) return 0f
        return min(
            min(segPointDist(ax, ay, bx, by, cx, cy), segPointDist(ax, ay, bx, by, dx, dy)),
            min(segPointDist(cx, cy, dx, dy, ax, ay), segPointDist(cx, cy, dx, dy, bx, by)),
        )
    }

    private fun intersects(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float): Boolean {
        fun o(px: Float, py: Float, qx: Float, qy: Float, rx: Float, ry: Float) = (qx - px) * (ry - py) - (qy - py) * (rx - px)
        val d1 = o(cx, cy, dx, dy, ax, ay); val d2 = o(cx, cy, dx, dy, bx, by)
        val d3 = o(ax, ay, bx, by, cx, cy); val d4 = o(ax, ay, bx, by, dx, dy)
        return ((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) && ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))
    }
}
