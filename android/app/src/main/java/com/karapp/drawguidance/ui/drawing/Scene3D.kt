package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Depth3D
import com.karapp.drawguidance.data.model.GuideSpec
import com.karapp.drawguidance.data.model.Plane3D
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Space3D
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v22 döndürülebilir 3B sahnenin kamerası. Dünya koordinatı tuval birimindedir: başlangıç tuvalin ortası ([cx], [cy]),
 * X sağa, Y yukarı, Z bakana doğru. [yaw] yatay dönüş, [pitch] dikey eğim (derece); [persp] 0 paralel izdüşüm, 1 güçlü
 * perspektif. Kameranın ekseni: sağ (r), aşağı (d) ve bakana doğru (t) birim vektörleri.
 */
class SceneCamera(val yaw: Float, val pitch: Float, val persp: Float, val cx: Float, val cy: Float, extent: Float) {
    val rx: Float; val ry: Float; val rz: Float
    val dx: Float; val dy: Float; val dz: Float
    val tx: Float; val ty: Float; val tz: Float

    /** Odak uzaklığı (perspektifte kameranın merkeze uzaklığı); paralel izdüşümde sonsuz. */
    val focal: Float = if (persp < 0.01f) Float.POSITIVE_INFINITY else max(extent, 1f) / (persp.coerceIn(0.01f, 1f) * 1.0f)
    val perspective: Boolean get() = focal.isFinite()

    init {
        val y = Math.toRadians(yaw.toDouble()); val p = Math.toRadians(pitch.toDouble())
        val cp = cos(p).toFloat(); val sp = sin(p).toFloat(); val cyw = cos(y).toFloat(); val syw = sin(y).toFloat()
        tx = cp * syw; ty = sp; tz = cp * cyw
        rx = cyw; ry = 0f; rz = -syw
        // d = r × t
        dx = ry * tz - rz * ty; dy = rz * tx - rx * tz; dz = rx * ty - ry * tx
    }

    /** Görünümün anahtarı (izdüşümün hangi kamerayla yapıldığı). */
    val key: Int = run {
        var h = 17
        h = h * 31 + Math.round(yaw * 100f)
        h = h * 31 + Math.round(pitch * 100f)
        h = h * 31 + Math.round(persp * 1000f)
        h = h * 31 + Math.round(cx * 4f)
        h = h * 31 + Math.round(cy * 4f)
        h
    }

    /** Kameraya göre derinlik (bakana doğru pozitif). */
    fun depth(x: Float, y: Float, z: Float): Float = x * tx + y * ty + z * tz

    /** Perspektif ölçeği (paralelde 1). */
    fun scaleAt(zc: Float): Float = if (!perspective) 1f else focal / max(focal - zc, focal * 0.1f)

    /** Dünya noktasının tuvaldeki yeri. */
    fun project(x: Float, y: Float, z: Float): Offset {
        val xc = x * rx + y * ry + z * rz
        val yc = x * dx + y * dy + z * dz
        val s = scaleAt(x * tx + y * ty + z * tz)
        return Offset(cx + xc * s, cy + yc * s)
    }

    fun project(v: FloatArray, o: Int = 0): Offset = project(v[o], v[o + 1], v[o + 2])

    /** Tuvaldeki noktanın, kameraya dik ve sahne merkezinden geçen düzlemdeki (derinlik 0) dünya karşılığı. */
    fun atDepth0(sx: Float, sy: Float): FloatArray {
        val a = sx - cx; val b = sy - cy
        return floatArrayOf(rx * a + dx * b, ry * a + dy * b, rz * a + dz * b)
    }

    /** Tuvaldeki noktadan geçen görüş ışını: başlangıç (0..2) ve yön (3..5). */
    fun ray(sx: Float, sy: Float): FloatArray {
        val q = atDepth0(sx, sy)
        return if (!perspective) floatArrayOf(q[0], q[1], q[2], -tx, -ty, -tz)
        else {
            val c0 = tx * focal; val c1 = ty * focal; val c2 = tz * focal
            floatArrayOf(c0, c1, c2, q[0] - c0, q[1] - c1, q[2] - c2)
        }
    }

    fun withView(yaw: Float = this.yaw, pitch: Float = this.pitch, persp: Float = this.persp, extent: Float) =
        SceneCamera(yaw, pitch, persp, cx, cy, extent)
}

/**
 * v22 döndürülebilir 3B sahne: 3B modda çizilen izler çizildikleri düzlemde (ön: X–Y, yan: Z–Y, üst: X–Z, serbest:
 * çizildiği andaki ekran düzlemi), 3B cisimler gerçek 3B ağlar olarak saklanır; kamera değişince izdüşümleri yeniden
 * hesaplanır. Düz çizimler kağıtta sabit kalır.
 */
object Scene3D {

    /** Kameranın görüş yönüne neredeyse paralel (kenardan görünen) düzlemde çizim serbest düzleme geçer. */
    private const val EDGE_ON = 0.18f

    fun camera(guide: GuideSpec, width: Int, height: Int): SceneCamera =
        SceneCamera(guide.yaw3d, guide.pitch3d.coerceIn(-90f, 90f), guide.persp3d.coerceIn(0f, 1f), width / 2f, height / 2f, max(width, height).toFloat())

    /** Düzlemin eksenleri u, v (birim) ve kabarma yönü w (12'lik çerçevenin 3..11'i; başlangıç 0). */
    fun planeAxes(plane: Plane3D, cam: SceneCamera): FloatArray = when (plane) {
        Plane3D.FRONT -> floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f)
        Plane3D.SIDE -> floatArrayOf(0f, 0f, 0f, 0f, 0f, -1f, 0f, -1f, 0f, 1f, 0f, 0f)
        Plane3D.TOP -> floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, -1f, 0f)
        Plane3D.FREE -> floatArrayOf(
            0f, 0f, 0f, cam.rx, cam.ry, cam.rz, cam.dx, cam.dy, cam.dz,
            0.7071f * (cam.rx + cam.dx) - 0.7f * cam.tx, 0.7071f * (cam.ry + cam.dy) - 0.7f * cam.ty, 0.7071f * (cam.rz + cam.dz) - 0.7f * cam.tz,
        )
    }

    private fun normalOf(f: FloatArray): FloatArray {
        val nx = f[4] * f[8] - f[5] * f[7]; val ny = f[5] * f[6] - f[3] * f[8]; val nz = f[3] * f[7] - f[4] * f[6]
        val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
        return floatArrayOf(nx / l, ny / l, nz / l)
    }

    /** Düzlem kameradan kenar üstü mü görünüyor (çizim ve geri açma serbest düzleme geçer). */
    fun edgeOn(plane: Plane3D, cam: SceneCamera): Boolean {
        if (plane == Plane3D.FREE) return false
        val n = normalOf(planeAxes(plane, cam))
        return abs(n[0] * cam.tx + n[1] * cam.ty + n[2] * cam.tz) < EDGE_ON
    }

    /** Çizimde kullanılacak düzlem: kenar üstü görünen düzlemde serbest düzlem. */
    fun effectivePlane(plane: Plane3D, cam: SceneCamera): Plane3D = if (edgeOn(plane, cam)) Plane3D.FREE else plane

    /** Işının düzlemle kesişimi (düzlem [p] noktasından geçer, normali [n]); yoksa null. */
    private fun intersect(ray: FloatArray, p: FloatArray, n: FloatArray, perspective: Boolean): FloatArray? {
        val dn = ray[3] * n[0] + ray[4] * n[1] + ray[5] * n[2]
        val dl = sqrt(ray[3] * ray[3] + ray[4] * ray[4] + ray[5] * ray[5])
        if (abs(dn) < 1e-4f * dl) return null
        val t = ((p[0] - ray[0]) * n[0] + (p[1] - ray[1]) * n[1] + (p[2] - ray[2]) * n[2]) / dn
        if (perspective && t <= 0f) return null
        return floatArrayOf(ray[0] + t * ray[3], ray[1] + t * ray[4], ray[2] + t * ray[5])
    }

    /**
     * Çizime başlanan noktanın dünya karşılığı: ön/yan/üst düzlemler sahne merkezinden geçer (aynı yüze çizilen tüm izler
     * aynı düzlemde durur); serbest düzlem kameraya dik, merkezden geçer.
     */
    fun anchor(plane: Plane3D, cam: SceneCamera, sx: Float, sy: Float): FloatArray {
        if (plane == Plane3D.FREE) return cam.atDepth0(sx, sy)
        val n = normalOf(planeAxes(plane, cam))
        return intersect(cam.ray(sx, sy), floatArrayOf(0f, 0f, 0f), n, cam.perspective) ?: cam.atDepth0(sx, sy)
    }

    /**
     * Çizim aracı için: iz parmağın altında, seçilen yüzün (düzlemin) üzerine çizilir — parmaktan geçen görüş ışınının
     * düzlemle kesiştiği noktalar. Bu yüzden çizerken iz ekranda parmağın çizdiği gibi görünür; sahne döndürülünce
     * düzlemdeki gerçek yeri görünür (aynı yüze çizilen tüm izler birlikte döner). [plane] etkin düzlem olmalı
     * ([effectivePlane]); kabarma yönü başlangıç noktasındaki düzleme göre.
     */
    class DrawMapping(val plane: Plane3D, private val cam: SceneCamera, private val ax: Float, private val ay: Float) {
        private val f = planeAxes(plane, cam)
        private val a = anchor(plane, cam, ax, ay)

        /** Noktanın ekrandaki yeri: düzlemdeki nokta görünümden yine parmağın altında görünür. */
        fun map(x: Float, y: Float): Offset = Offset(x, y)

        /** Kabarmanın ekrandaki birim vektörü. */
        val extrude: Offset = extrudeVector(cam, a, f)
    }

    private fun extrudeVector(cam: SceneCamera, o: FloatArray, f: FloatArray): Offset {
        val p0 = cam.project(o[0], o[1], o[2])
        val p1 = cam.project(o[0] + f[9], o[1] + f[10], o[2] + f[11])
        return Offset(p1.x - p0.x, p1.y - p0.y)
    }

    /** Noktaların özeti (2B'de değiştirilip değiştirilmediği). */
    fun stamp(points: List<StrokePoint>): Long {
        var h = -3750763034362895579L
        for (p in points) {
            h = (h xor Math.round(p.x * 8f).toLong()) * 1099511628211L
            h = (h xor Math.round(p.y * 8f).toLong()) * 1099511628211L
        }
        return h xor points.size.toLong()
    }

    // --- İzler ---

    /** Yeni (ya da v21) 3B izin sahnedeki yeri: noktalar düzleme geri açılır. */
    private fun derive(s: Stroke, d3: Depth3D, cam: SceneCamera): Stroke {
        val plane = effectivePlane(d3.plane, cam)
        val f = planeAxes(plane, cam)
        val a = anchor(plane, cam, s.points[0].x, s.points[0].y)
        f[0] = a[0]; f[1] = a[1]; f[2] = a[2]
        val local = localOf(s.points, f, cam) ?: return s
        val ev = extrudeVector(cam, a, f)
        val sp = Space3D(f, local, stamp(s.points), cam.key)
        val params = s.params?.copy(depth3d = d3.copy(plane = plane, ex = ev.x, ey = ev.y))
        return s.copy(space = sp, params = params)
    }

    /** Noktaların düzlemdeki (çerçeve başlangıcına göre) koordinatları; düzlem kenar üstüyse null. */
    private fun localOf(points: List<StrokePoint>, f: FloatArray, cam: SceneCamera): FloatArray? {
        val n = normalOf(f)
        val o = floatArrayOf(f[0], f[1], f[2])
        val out = FloatArray(points.size * 2)
        for ((i, p) in points.withIndex()) {
            val w = intersect(cam.ray(p.x, p.y), o, n, cam.perspective) ?: return null
            val qx = w[0] - o[0]; val qy = w[1] - o[1]; val qz = w[2] - o[2]
            out[2 * i] = qx * f[3] + qy * f[4] + qz * f[5]
            out[2 * i + 1] = qx * f[6] + qy * f[7] + qz * f[8]
        }
        return out
    }

    /** 2B'de değiştirilen iz (taşıma, ölçekleme, silgiyle bölme…): aynı düzlemde yeni yer. */
    private fun rederive(s: Stroke, sp: Space3D, cam: SceneCamera): Stroke {
        if (s.points.isEmpty()) return s
        val f = sp.frame.copyOf()
        val n = normalOf(f)
        val a = intersect(cam.ray(s.points[0].x, s.points[0].y), floatArrayOf(f[0], f[1], f[2]), n, cam.perspective)
        if (a != null) { f[0] = a[0]; f[1] = a[1]; f[2] = a[2] }
        val local = if (a != null) localOf(s.points, f, cam) else null
        // Düzlem kenardan görünüyorsa yer değiştirilemez: iz olduğu gibi kalır
        return s.copy(space = if (local != null) Space3D(f, local, stamp(s.points), cam.key) else sp.with(stamp(s.points), cam.key))
    }

    /** Kamera değişti: iz yeni görünümden çizilir (basınç, eğim korunur). */
    private fun reprojectStroke(s: Stroke, sp: Space3D, cam: SceneCamera): Stroke {
        val f = sp.frame
        val n = sp.pointCount
        val same = s.points.size == n
        val q = ContentCodec::quantize
        val pts = List(n) { i ->
            val a = sp.local[2 * i]; val b = sp.local[2 * i + 1]
            val o = cam.project(f[0] + a * f[3] + b * f[6], f[1] + a * f[4] + b * f[7], f[2] + a * f[5] + b * f[8])
            if (same) s.points[i].at(q(o.x), q(o.y)) else StrokePoint(q(o.x), q(o.y))
        }
        val ev = extrudeVector(cam, floatArrayOf(f[0], f[1], f[2]), f)
        val params = s.params?.let { pr -> pr.depth3d?.let { pr.copy(depth3d = it.copy(ex = ev.x, ey = ev.y)) } ?: pr }
        return s.copy(points = pts, params = params, space = Space3D(f, sp.local, stamp(pts), cam.key))
    }

    // --- Cisimler ---

    /** Cisimlerin ağ oranları (yarı boyutlar: genişlik, yükseklik, derinlik). */
    private fun extents(kind: String): FloatArray = when (kind) {
        "SOLID_BOX" -> floatArrayOf(1.4f, 0.85f, 0.9f)
        "SOLID_CYLINDER" -> floatArrayOf(1f, 1.2f, 1f)
        "SOLID_CONE" -> floatArrayOf(1f, 1.2f, 1f)
        "SOLID_DOME" -> floatArrayOf(1f, 0.5f, 1f)
        "SOLID_PRISM" -> floatArrayOf(1.4f, 0.9f, 1f)
        else -> floatArrayOf(1f, 1f, 1f)
    }

    fun isSolidKind(kind: String?): Boolean = kind != null && kind.startsWith("SOLID_")

    /**
     * 3B modda eklenen cisim: [rect] (tuval) içine sığacak boyutta, merkezi dikdörtgenin ortasının derinlik 0'daki
     * karşılığında; eksenleri dünya eksenleriyle hizalı.
     */
    fun solidStroke(kind: String, start: Offset, end: Offset, style: ShapeStyle, cam: SceneCamera, id: String, order: Int, opacity: Float = 1f): Stroke? {
        val l = min(start.x, end.x); val r = max(start.x, end.x); val t = min(start.y, end.y); val b = max(start.y, end.y)
        val rw = max(r - l, 8f); val rh = max(b - t, 8f)
        val c = cam.atDepth0((l + r) / 2f, (t + b) / 2f)
        val e = extents(kind)
        val f = floatArrayOf(c[0], c[1], c[2], e[0], 0f, 0f, 0f, e[1], 0f, 0f, 0f, e[2])
        // Sığdır: köşelerin izdüşümünün kutusu
        val bx = boxOf(f, cam)
        val k = min(rw / max(bx.width, 1e-3f), rh / max(bx.height, 1e-3f))
        for (i in 3 until 12) f[i] *= k
        val paths = solidPaths(kind, f, cam)
        val built = ShapeFactory.fromPaths(kind, paths, style, null, id, order, opacity) ?: return null
        return built.copy(space = Space3D(f, FloatArray(0), stamp(built.points), cam.key))
    }

    /** Cismin 8 köşesinin izdüşüm kutusu. */
    private fun boxOf(f: FloatArray, cam: SceneCamera): Rect {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (su in intArrayOf(-1, 1)) for (sv in intArrayOf(-1, 1)) for (sw in intArrayOf(-1, 1)) {
            val p = cam.project(
                f[0] + su * f[3] + sv * f[6] + sw * f[9], f[1] + su * f[4] + sv * f[7] + sw * f[10], f[2] + su * f[5] + sv * f[8] + sw * f[11],
            )
            l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y)
        }
        return Rect(l, t, r, b)
    }

    private fun rebuildSolid(s: Stroke, f: FloatArray, cam: SceneCamera): Stroke {
        val spec = s.shape ?: return s
        val built = ShapeFactory.fromPaths(spec.kind, solidPaths(spec.kind, f, cam), spec.style, null, s.id, s.order, s.opacity) ?: return s
        return built.copy(
            groupId = s.groupId, layerId = s.layerId, params = s.params, flow = s.flow, strength = s.strength,
            space = Space3D(f, FloatArray(0), stamp(built.points), cam.key),
        )
    }

    /** 2B'de taşınan/ölçeklenen cisim: kutusunun kayması ve büyümesi sahnede aynı biçimde uygulanır. */
    private fun resolidify(s: Stroke, sp: Space3D, cam: SceneCamera): Stroke {
        val nb = pointBounds(s.points) ?: return s
        val ob = boxOf(sp.frame, cam)
        val f = sp.frame.copyOf()
        val same = abs(nb.center.x - ob.center.x) < 1.5f && abs(nb.center.y - ob.center.y) < 1.5f &&
            abs(nb.width - ob.width) < 0.03f * ob.width + 1f && abs(nb.height - ob.height) < 0.03f * ob.height + 1f
        if (!same) {
            val k = sqrt(max(nb.width, 1f) * max(nb.height, 1f) / (max(ob.width, 1f) * max(ob.height, 1f))).coerceIn(0.05f, 20f)
            for (i in 3 until 12) f[i] *= k
            // Merkezin kayması: ölçeklenmiş kutunun merkezi yeni kutunun merkezine gelsin
            val sb = boxOf(f, cam)
            val zc = cam.depth(f[0], f[1], f[2])
            val sc = cam.scaleAt(zc)
            val ddx = (nb.center.x - sb.center.x) / sc; val ddy = (nb.center.y - sb.center.y) / sc
            f[0] += cam.rx * ddx + cam.dx * ddy; f[1] += cam.ry * ddx + cam.dy * ddy; f[2] += cam.rz * ddx + cam.dz * ddy
        }
        return rebuildSolid(s, f, cam)
    }

    private fun pointBounds(points: List<StrokePoint>): Rect? {
        if (points.isEmpty()) return null
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (p in points) { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
        return Rect(l, t, r, b)
    }

    // --- Eşitleme ---

    /** Elemanın sahnede yeri var mı ya da kazanmalı mı (3B iz ya da 3B cisim). */
    fun involves(s: Stroke): Boolean = s.space != null || (s.params?.depth3d != null && s.brushType != BrushType.SHAPE && s.brushType != BrushType.ERASER)

    /**
     * Tek elemanı kameraya göre eşitler: yeri yoksa (yeni ya da v21 izi) oluşturulur; noktaları 2B'de değiştiyse yeri
     * yeniden hesaplanır; başka bir kamerayla çizildiyse yeniden izdüşürülür. Değişmezse aynı nesne döner.
     */
    fun sync(s: Stroke, cam: SceneCamera): Stroke {
        val sp = s.space
        if (sp == null) {
            val d3 = s.params?.depth3d ?: return s
            if (s.brushType == BrushType.SHAPE || s.brushType == BrushType.ERASER || s.points.isEmpty()) return s
            return derive(s, d3, cam)
        }
        val h = stamp(s.points)
        if (h != sp.stamp) return if (sp.isSolid) resolidify(s, sp, cam) else rederive(s, sp, cam)
        if (sp.view != cam.key) return if (sp.isSolid) rebuildSolid(s, sp.frame, cam) else reprojectStroke(s, sp, cam)
        return s
    }

    /** Sayfaların eşitlenmiş hali; hiçbir şey değişmediyse null. */
    fun syncPages(pages: List<List<Stroke>>, guide: GuideSpec, width: Int, height: Int): List<List<Stroke>>? {
        if (pages.none { page -> page.any(::involves) }) return null
        val cam = camera(guide, width, height)
        var changed = false
        val out = pages.map { page ->
            if (page.none(::involves)) page else {
                var pageChanged = false
                val next = page.map { s -> if (!involves(s)) s else sync(s, cam).also { if (it !== s) pageChanged = true } }
                if (pageChanged) { changed = true; next } else page
            }
        }
        return if (changed) out else null
    }

    // --- Cisim ağları ---

    /** Ağ yüzü: yerel köşeler (u, v, w ∈ [−1, 1]; v yukarı) ve eğri yüz parçası mı (kenar çizgisi çizilmez). */
    private class Face(val pts: Array<FloatArray>, val curved: Boolean)

    private const val SEG = 28

    private val meshes = HashMap<String, List<Face>>()

    private fun mesh(kind: String): List<Face> = synchronized(meshes) { meshes.getOrPut(kind) { buildMesh(kind) } }

    private fun v(u: Float, vv: Float, w: Float) = floatArrayOf(u, vv, w)

    private fun buildMesh(kind: String): List<Face> {
        val out = ArrayList<Face>()
        fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, curved: Boolean = false) { out += Face(arrayOf(a, b, c, d), curved) }
        fun tri(a: FloatArray, b: FloatArray, c: FloatArray, curved: Boolean = false) { out += Face(arrayOf(a, b, c), curved) }
        fun ring(n: Int, y: Float, r: Float = 1f): Array<FloatArray> = Array(n) { i ->
            val a = 2.0 * Math.PI * i / n
            v((r * cos(a)).toFloat(), y, (r * sin(a)).toFloat())
        }
        when (kind) {
            "SOLID_CYLINDER" -> {
                val top = ring(SEG, 1f); val bot = ring(SEG, -1f)
                for (i in 0 until SEG) { val j = (i + 1) % SEG; quad(bot[i], bot[j], top[j], top[i], curved = true) }
                out += Face(top, false); out += Face(bot.reversedArray(), false)
            }
            "SOLID_CONE" -> {
                val bot = ring(SEG, -1f); val apex = v(0f, 1f, 0f)
                for (i in 0 until SEG) { val j = (i + 1) % SEG; tri(bot[i], bot[j], apex, curved = true) }
                out += Face(bot.reversedArray(), false)
            }
            "SOLID_SPHERE", "SOLID_DOME" -> {
                val dome = kind == "SOLID_DOME"
                val lat = if (dome) 7 else 12
                val lon = 24
                fun p(i: Int, j: Int): FloatArray {
                    val a = if (dome) Math.PI / 2 * i / lat else -Math.PI / 2 + Math.PI * i / lat
                    val b = 2 * Math.PI * j / lon
                    val y = if (dome) -1f + 2f * sin(a).toFloat() else sin(a).toFloat()
                    return v((cos(a) * cos(b)).toFloat(), y, (cos(a) * sin(b)).toFloat())
                }
                for (i in 0 until lat) for (j in 0 until lon) {
                    val a = p(i, j); val b = p(i, j + 1); val c = p(i + 1, j + 1); val d = p(i + 1, j)
                    if (i == lat - 1) tri(a, b, c, curved = true) else if (!dome && i == 0) tri(a, c, d, curved = true) else quad(a, b, c, d, curved = true)
                }
                if (dome) out += Face(ring(SEG, -1f).reversedArray(), false)
            }
            "SOLID_PYRAMID" -> {
                val b = arrayOf(v(-1f, -1f, -1f), v(1f, -1f, -1f), v(1f, -1f, 1f), v(-1f, -1f, 1f)); val apex = v(0f, 1f, 0f)
                for (i in 0 until 4) tri(b[i], b[(i + 1) % 4], apex)
                out += Face(b, false)
            }
            "SOLID_PRISM" -> {
                // Üçgen kesit (v, w) u ekseni boyunca uzanır
                val sec = arrayOf(floatArrayOf(1f, 0f), floatArrayOf(-1f, -1f), floatArrayOf(-1f, 1f))
                val l = Array(3) { v(-1f, sec[it][0], sec[it][1]) }; val r = Array(3) { v(1f, sec[it][0], sec[it][1]) }
                out += Face(l, false); out += Face(r, false)
                for (i in 0 until 3) { val j = (i + 1) % 3; quad(l[i], l[j], r[j], r[i]) }
            }
            "SOLID_HEX_PRISM" -> {
                val top = ring(6, 1f); val bot = ring(6, -1f)
                for (i in 0 until 6) { val j = (i + 1) % 6; quad(bot[i], bot[j], top[j], top[i]) }
                out += Face(top, false); out += Face(bot, false)
            }
            else -> {
                // Küp ve kutu
                val c = Array(8) { i -> v(if (i and 1 != 0) 1f else -1f, if (i and 2 != 0) 1f else -1f, if (i and 4 != 0) 1f else -1f) }
                quad(c[0], c[1], c[3], c[2]); quad(c[4], c[5], c[7], c[6])
                quad(c[0], c[1], c[5], c[4]); quad(c[2], c[3], c[7], c[6])
                quad(c[0], c[2], c[6], c[4]); quad(c[1], c[3], c[7], c[5])
            }
        }
        return out
    }

    /** Yüz kenarı kalınlık çarpanı ve dış hat. */
    private const val EDGE = 0.55f

    /**
     * Cismin görünümü: kameraya bakan yüzler arkadan öne, ışığa göre tonlanarak (ışık sol üst önden); düz yüzlerde
     * ince kenar, eğri yüz parçalarında kenar yok (dikiş görünmesin diye hafif taşırılır); en üstte dış hat.
     */
    fun solidPaths(kind: String, f: FloatArray, cam: SceneCamera): List<ShapePath> {
        val faces = mesh(kind)
        fun world(p: FloatArray) = floatArrayOf(
            f[0] + p[0] * f[3] + p[1] * f[6] + p[2] * f[9],
            f[1] + p[0] * f[4] + p[1] * f[7] + p[2] * f[10],
            f[2] + p[0] * f[5] + p[1] * f[8] + p[2] * f[11],
        )
        // Işık: kameraya göre sol üst önden
        var lx = -0.45f * cam.rx - 0.65f * cam.dx + 0.6f * cam.tx
        var ly = -0.45f * cam.ry - 0.65f * cam.dy + 0.6f * cam.ty
        var lz = -0.45f * cam.rz - 0.65f * cam.dz + 0.6f * cam.tz
        val ll = sqrt(lx * lx + ly * ly + lz * lz); lx /= ll; ly /= ll; lz /= ll
        // Ağın merkezi (yüz normallerini dışa çevirmek için)
        val center = world(if (kind == "SOLID_DOME") floatArrayOf(0f, -0.6f, 0f) else floatArrayOf(0f, 0f, 0f))
        class Drawn(val depth: Float, val path: ShapePath)
        val drawn = ArrayList<Drawn>(faces.size)
        val all = ArrayList<Offset>()
        for (face in faces) {
            val w = face.pts.map(::world)
            var cxw = 0f; var cyw = 0f; var czw = 0f
            w.forEach { cxw += it[0]; cyw += it[1]; czw += it[2] }
            cxw /= w.size; cyw /= w.size; czw /= w.size
            // Normal (Newell yöntemi) ve dışa yönlendirme
            var nx = 0f; var ny = 0f; var nz = 0f
            for (i in w.indices) {
                val a = w[i]; val b = w[(i + 1) % w.size]
                nx += (a[1] - b[1]) * (a[2] + b[2]); ny += (a[2] - b[2]) * (a[0] + b[0]); nz += (a[0] - b[0]) * (a[1] + b[1])
            }
            val nl = sqrt(nx * nx + ny * ny + nz * nz)
            if (nl < 1e-9f) continue
            nx /= nl; ny /= nl; nz /= nl
            if (nx * (cxw - center[0]) + ny * (cyw - center[1]) + nz * (czw - center[2]) < 0f) { nx = -nx; ny = -ny; nz = -nz }
            // Görünürlük
            val vis = if (!cam.perspective) nx * cam.tx + ny * cam.ty + nz * cam.tz
            else nx * (cam.tx * cam.focal - cxw) + ny * (cam.ty * cam.focal - cyw) + nz * (cam.tz * cam.focal - czw)
            val pts = w.map { cam.project(it[0], it[1], it[2]) }
            all += pts
            if (vis <= 1e-4f) continue
            val diffuse = max(0f, nx * lx + ny * ly + nz * lz)
            val facing = max(0f, nx * cam.tx + ny * cam.ty + nz * cam.tz)
            val level = (0.26f + 0.6f * diffuse + 0.1f * facing).coerceIn(0.05f, 0.95f)
            val q = Math.round(level * 40f) / 40f
            val poly = if (face.curved) expand(pts, 0.45f) else pts
            drawn += Drawn(
                cam.depth(cxw, cyw, czw),
                ShapePath(poly + poly.first(), closed = true, fillColorHex = ShapeRenderer.shade(q), widthScale = if (face.curved) 0f else EDGE),
            )
        }
        drawn.sortBy { it.depth }
        val out = ArrayList<ShapePath>(drawn.size + 1)
        drawn.forEach { out += it.path }
        val hull = convexHull(all)
        if (hull.size >= 3) out += ShapePath(hull + hull.first(), closed = true, fillColorHex = ShapeRenderer.NO_FILL)
        return out
    }

    /** Çokgeni ağırlık merkezinden [d] kadar dışa taşırır (eğri yüz parçaları arasında dikiş kalmasın). */
    private fun expand(pts: List<Offset>, d: Float): List<Offset> {
        var cx = 0f; var cy = 0f
        pts.forEach { cx += it.x; cy += it.y }
        cx /= pts.size; cy /= pts.size
        return pts.map { p ->
            val ex = p.x - cx; val ey = p.y - cy
            val l = sqrt(ex * ex + ey * ey)
            if (l < 1e-4f) p else Offset(p.x + ex / l * d, p.y + ey / l * d)
        }
    }

    /** Dışbükey zarf (Andrew'un monoton zinciri). */
    fun convexHull(points: List<Offset>): List<Offset> {
        val pts = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (pts.size < 3) return pts
        fun cross(o: Offset, a: Offset, b: Offset) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = ArrayList<Offset>()
        for (p in pts) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0f) lower.removeAt(lower.size - 1); lower += p }
        val upper = ArrayList<Offset>()
        for (p in pts.asReversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0f) upper.removeAt(upper.size - 1); upper += p }
        lower.removeAt(lower.size - 1); upper.removeAt(upper.size - 1)
        return lower + upper
    }
}
