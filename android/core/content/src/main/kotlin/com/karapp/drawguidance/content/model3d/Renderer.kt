package com.karapp.drawguidance.content.model3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Çizim ayarları. */
class RenderOptions(
    /** Doku kimliğinden doku (yoksa malzeme rengi kullanılır). */
    val textures: (String) -> Texture? = { null },
    /** Kenar yumuşatma için örnekleme katı (1 hızlı, 2 kaliteli). */
    val supersample: Int = 2,
    /** Doku örneklemesi çift doğrusal mı (false: en yakın, hızlı). */
    val bilinear: Boolean = true,
    /** Zemin ızgarası (null: sahne ayarı). */
    val ground: Boolean? = null,
    /** Gölge (null: sahne ayarı). */
    val shadow: Boolean? = null,
    /** Arka plan (null: sahne ayarı; 0 saydam). */
    val background: Int? = null,
    /** Seçili parça: kenarları vurgu rengiyle; [wireframe] ise tüm kenarları. */
    val selectedPart: String? = null,
    val selectedFaces: Set<Int> = emptySet(),
    /** v26 birlikte seçili diğer parçalar (grup): onların kenarları da vurgulanır. */
    val selectedParts: Set<String> = emptySet(),
    val wireframe: Boolean = false,
    val highlight: Int = 0xFF2F80ED.toInt(),
)

/**
 * Çizilmiş kare: [color] ARGB (önçarpımsız), [depth] kamera derinliği (yoksa −∞), [part] parça sırası + 1 (çizgide
 * −(çizgi sırası + 1), boşta 0), [face] yüz sırası. [view] kare çözünürlüğündeki izdüşüm (dokunuş ve işaretler için).
 */
class Frame(
    val width: Int,
    val height: Int,
    val color: IntArray,
    val depth: FloatArray,
    val part: IntArray,
    val face: IntArray,
    val view: View3,
) {
    fun index(x: Int, y: Int) = y * width + x

    /** Ekran noktasındaki parça (sıra) ve yüz; boşsa null. */
    fun pick(x: Float, y: Float, radius: Int = 0): Pair<Int, Int>? {
        val cx = x.roundToInt(); val cy = y.roundToInt()
        var best: Pair<Int, Int>? = null; var bestD = Int.MAX_VALUE
        for (j in -radius..radius) for (i in -radius..radius) {
            val px = cx + i; val py = cy + j
            if (px !in 0 until width || py !in 0 until height) continue
            val p = part[index(px, py)]
            if (p == 0) continue
            val d = i * i + j * j
            if (d < bestD) { bestD = d; best = p to face[index(px, py)] }
        }
        return best
    }
}

/**
 * v23 yazılım 3B çizici (z-tampon): Android'in grafik kütüphanesine ya da OpenGL'e bağımlı değil; aynı kod telefonda,
 * dışa aktarmada ve testlerde aynı sonucu verir.
 *
 * Sıra: zemin ızgarası → zemine düşen gölge (ışık yönünde izdüşüm, yumuşatılmış) → opak yüzler (piksel başına Lambert
 * + Blinn-Phong ışık, doku, metal yansıması; kapalı ağlarda arka yüzler atlanır) → 3B çizgiler → kenar çizgileri
 * (keskin kenarlar ve dış hat, derinlik sınamalı) → saydam yüzler (uzaktan yakına, karıştırılarak) → örnekleme
 * katından çıkış çözünürlüğüne ortalama. Perspektifte öznitelikler perspektife göre düzeltilerek ara değerlenir.
 */
object Renderer {

    private const val CREASE_COS = 0.7f
    private const val FEATURE_COS = 0.82f
    private const val VS = 9 // sx, sy, z, iw, nx, ny, nz, u, v

    fun render(model: Model3D, view: View3, options: RenderOptions = RenderOptions()): Frame {
        val q = options.supersample.coerceIn(1, 3)
        val w = view.width * q; val h = view.height * q
        val ss = View3(view.camera, w, h, view.scale * q, view.centerX * q, view.centerY * q)
        val r = Raster(w, h, ss, model, options, q)
        r.draw()
        return r.output(view)
    }

    /**
     * v29 modelin görünen çizgileri (vektör aktarma için): dış hat, sınır ve kıvrım kenarlarının derinlik tamponuna göre
     * görünen parçaları, ekran koordinatlarında çoklu çizgiler (x0, y0, x1, y1, …). Uç uca eklenen kenarlar tek çizgi
     * olur. [step] görünürlük örneklemesinin aralığı (piksel).
     */
    fun outlines(model: Model3D, view: View3, step: Float = 1.5f): List<FloatArray> {
        val r = Raster(view.width, view.height, view, model, RenderOptions(supersample = 1, ground = false, shadow = false, background = 0), 1)
        r.prepare()
        for (p in r.prepared) r.drawPart(p, transparentPass = false)
        // Birkaç piksellik kırıntılar atılır
        return chain(dedupe(r.visibleEdges(step.coerceAtLeast(0.5f)))).filter { pl ->
            var len = 0f
            for (i in 2 until pl.size step 2) len += kotlin.math.hypot(pl[i] - pl[i - 2], pl[i + 1] - pl[i - 1])
            len >= 4f
        }
    }

    /**
     * Üst üste binen parçalar bir kez: düz gölgeli cisimlerde her köşe yüz başına ayrı olduğundan bir kenar iki kez
     * (iki yüzün sınırı olarak) bulunur.
     */
    internal fun dedupe(segments: List<FloatArray>, tolerance: Float = 1.5f): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        val seen = HashMap<Long, MutableList<FloatArray>>()
        fun cell(x: Float, y: Float): Long = (Math.round(x / 4f).toLong() shl 32) xor (Math.round(y / 4f).toLong() and 0xFFFFFFFFL)
        fun same(a: FloatArray, b: FloatArray): Boolean {
            fun close(x0: Float, y0: Float, x1: Float, y1: Float) = abs(x0 - x1) <= tolerance && abs(y0 - y1) <= tolerance
            return (close(a[0], a[1], b[0], b[1]) && close(a[2], a[3], b[2], b[3])) || (close(a[0], a[1], b[2], b[3]) && close(a[2], a[3], b[0], b[1]))
        }
        for (s in segments) {
            val mx = (s[0] + s[2]) / 2f; val my = (s[1] + s[3]) / 2f
            val k = cell(mx, my)
            val list = seen.getOrPut(k) { ArrayList() }
            if (list.any { same(it, s) }) continue
            list += s
            out += s
        }
        return out
    }

    /** Uç uca değen parçaları çoklu çizgilere birleştirir. */
    internal fun chain(segments: List<FloatArray>, tolerance: Float = 2f): List<FloatArray> {
        if (segments.isEmpty()) return emptyList()
        val cell = tolerance.coerceAtLeast(0.5f)
        fun key(cx: Int, cy: Int): Long = (cx.toLong() shl 32) xor (cy.toLong() and 0xFFFFFFFFL)
        val grid = HashMap<Long, MutableList<Int>>()
        fun add(x: Float, y: Float, i: Int) { grid.getOrPut(key(floor(x / cell).toInt(), floor(y / cell).toInt())) { ArrayList() }.add(i) }
        segments.forEachIndexed { i, s -> add(s[0], s[1], i); add(s[2], s[3], i) }
        val used = BooleanArray(segments.size)
        /** [x], [y] ucuna [tolerance] içinde değen kullanılmamış parça ve onun hangi ucunun değdiği. */
        fun near(x: Float, y: Float): Pair<Int, Boolean>? {
            val gx = floor(x / cell).toInt(); val gy = floor(y / cell).toInt()
            var best: Pair<Int, Boolean>? = null; var bd = tolerance * tolerance
            for (oy in -1..1) for (ox in -1..1) {
                val list = grid[key(gx + ox, gy + oy)] ?: continue
                for (i in list) {
                    if (used[i]) continue
                    val s = segments[i]
                    val d0 = (s[0] - x) * (s[0] - x) + (s[1] - y) * (s[1] - y)
                    val d1 = (s[2] - x) * (s[2] - x) + (s[3] - y) * (s[3] - y)
                    if (d0 <= bd) { bd = d0; best = i to true }
                    if (d1 <= bd) { bd = d1; best = i to false }
                }
            }
            return best
        }
        val out = ArrayList<FloatArray>()
        for (start in segments.indices) {
            if (used[start]) continue
            used[start] = true
            val pts = ArrayDeque<Float>()
            val s0 = segments[start]
            pts.addLast(s0[0]); pts.addLast(s0[1]); pts.addLast(s0[2]); pts.addLast(s0[3])
            // Sona doğru, sonra başa doğru uzatılır
            for (forward in booleanArrayOf(true, false)) {
                while (true) {
                    val x = if (forward) pts[pts.size - 2] else pts[0]
                    val y = if (forward) pts[pts.size - 1] else pts[1]
                    val (next, atStart) = near(x, y) ?: break
                    used[next] = true
                    val s = segments[next]
                    val nx = if (atStart) s[2] else s[0]
                    val ny = if (atStart) s[3] else s[1]
                    if (forward) { pts.addLast(nx); pts.addLast(ny) } else { pts.addFirst(ny); pts.addFirst(nx) }
                }
            }
            out.add(pts.toFloatArray())
        }
        return out
    }

    /** Görünür kenarda doldurulan en uzun kesinti (örnek). */
    private const val GAP_FILL = 3

    private class Prepared(
        val index: Int,
        val part: Part,
        val world: FloatArray,
        val screen: FloatArray,
        val normals: FloatArray,
        val corners: Array<FloatArray>?,
        val uv: Array<FloatArray>,
        val cull: Boolean,
    )

    private class Raster(
        val w: Int,
        val h: Int,
        val view: View3,
        val model: Model3D,
        val opt: RenderOptions,
        val q: Int,
    ) {
        val color = IntArray(w * h)
        val depth = FloatArray(w * h) { Float.NEGATIVE_INFINITY }
        val partId = IntArray(w * h)
        val faceId = IntArray(w * h)
        val scene = model.scene
        val light = scene.light
        val L = light.direction
        val T = view.toViewer
        val H = (L + T).normalized()
        val lightR = (light.color shr 16 and 0xFF) / 255f
        val lightG = (light.color shr 8 and 0xFF) / 255f
        val lightB = (light.color and 0xFF) / 255f
        val prepared = ArrayList<Prepared>()
        var groundY = 0f
        var extent = 1f

        fun draw() {
            val bg = opt.background ?: scene.background
            if (bg != 0) color.fill(bg)
            prepare()
            if (opt.ground ?: scene.ground) drawGround()
            if (opt.shadow ?: scene.shadow) drawShadow()
            for (p in prepared) drawPart(p, transparentPass = false)
            drawStrokes()
            if (scene.edges || opt.selectedPart != null || opt.selectedParts.isNotEmpty()) drawEdges()
            drawTransparent()
        }

        // ------------------------------------------------------------------------------------------ hazırlık

        fun prepare() {
            var minY = Float.POSITIVE_INFINITY
            var box: Box3? = null
            model.parts.forEachIndexed { index, part ->
                if (!part.visible || part.mesh.vertexCount == 0) return@forEachIndexed
                val t = Transform3.of(part)
                val mesh = part.mesh
                val n = mesh.vertexCount
                val world = FloatArray(n * 3)
                val screen = FloatArray(n * 4)
                for (i in 0 until n) {
                    t.apply(mesh.pos[3 * i], mesh.pos[3 * i + 1], mesh.pos[3 * i + 2], world, 3 * i)
                    view.project(world[3 * i], world[3 * i + 1], world[3 * i + 2], screen, 4 * i)
                    minY = min(minY, world[3 * i + 1])
                }
                val b = boundsOf(world)
                box = box?.union(b) ?: b
                val fn = mesh.faceNormals
                val normals = FloatArray(mesh.faceCount * 3)
                for (f in 0 until mesh.faceCount) t.normal(fn[3 * f], fn[3 * f + 1], fn[3 * f + 2], normals, 3 * f)
                val corners = if (part.smooth) {
                    val cn = mesh.cornerNormals(CREASE_COS)
                    Array(mesh.faceCount) { f ->
                        val src = cn[f]
                        val out = FloatArray(src.size)
                        for (k in 0 until src.size / 3) t.normal(src[3 * k], src[3 * k + 1], src[3 * k + 2], out, 3 * k)
                        out
                    }
                } else null
                prepared += Prepared(index, part, world, screen, normals, corners, uvs(part), mesh.closed)
            }
            model.strokes.forEach { s -> for (i in 0 until s.count) minY = min(minY, s.points[3 * i + 1]) }
            groundY = if (minY.isFinite()) min(0f, minY) else 0f
            extent = box?.radius?.coerceAtLeast(0.25f) ?: 1f
        }

        fun boundsOf(p: FloatArray): Box3 {
            var ax = Float.POSITIVE_INFINITY; var ay = Float.POSITIVE_INFINITY; var az = Float.POSITIVE_INFINITY
            var bx = Float.NEGATIVE_INFINITY; var by = Float.NEGATIVE_INFINITY; var bz = Float.NEGATIVE_INFINITY
            var i = 0
            while (i < p.size) { ax = min(ax, p[i]); ay = min(ay, p[i + 1]); az = min(az, p[i + 2]); bx = max(bx, p[i]); by = max(by, p[i + 1]); bz = max(bz, p[i + 2]); i += 3 }
            return Box3(V3(ax, ay, az), V3(bx, by, bz))
        }

        /** Yüz köşelerinin doku koordinatları ([UvMapper]; aynı ağ ve ölçek için önbellekten). */
        fun uvs(part: Part): Array<FloatArray> = UvMapper.cached(part)

        // ------------------------------------------------------------------------------------------ zemin ve gölge

        fun drawGround() {
            val size = max(2f, ceil(extent * 2.5f))
            val step = if (size > 6f) 1f else 0.5f
            val gc = 0x2E000000 or (scene.edgeColor and 0x00FFFFFF)
            val a = FloatArray(4); val b = FloatArray(4)
            var v = -size
            while (v <= size + 1e-3f) {
                view.project(v, groundY, -size, a); view.project(v, groundY, size, b)
                lineNoDepth(a, b, if (abs(v) < 1e-3f) 0x804A90E2.toInt() else gc, q.toFloat())
                view.project(-size, groundY, v, a); view.project(size, groundY, v, b)
                lineNoDepth(a, b, if (abs(v) < 1e-3f) 0x80E25C4A.toInt() else gc, q.toFloat())
                v += step
            }
        }

        fun drawShadow() {
            if (L.y < 0.08f) return
            val mask = FloatArray(w * h)
            val ly = max(L.y, 0.3f)
            val sp = FloatArray(12)
            for (p in prepared) {
                if (p.part.material.transparent && p.part.material.opacity < 0.5f) continue
                val m = p.part.mesh
                // Köşelerin zemindeki gölge izdüşümü bir kez (üçgenler paylaşır)
                val n = m.vertexCount
                val proj = FloatArray(n * 4)
                for (i in 0 until n) {
                    val x = p.world[3 * i]; val y = p.world[3 * i + 1]; val z = p.world[3 * i + 2]
                    val t = (y - groundY) / ly
                    view.project(x - L.x * t, groundY, z - L.z * t, proj, 4 * i)
                }
                for (f in 0 until m.faceCount) {
                    val face = m.faces[f]
                    val tris = m.triangles[f]
                    var k = 0
                    while (k + 2 < tris.size) {
                        var ok = true
                        for (c in 0 until 3) {
                            val i = face[tris[k + c]]
                            for (j in 0 until 4) sp[4 * c + j] = proj[4 * i + j]
                            if (sp[4 * c + 3] <= 0f) ok = false
                        }
                        if (ok) fillMask(mask, sp)
                        k += 3
                    }
                }
            }
            val radius = max(1, (min(w, h) * 0.012f).roundToInt())
            blur(mask, radius)
            blur(mask, radius)
            val strength = 0.34f * light.intensity.coerceIn(0f, 1.5f)
            for (i in mask.indices) {
                val m = mask[i]
                if (m <= 0.002f) continue
                color[i] = over(color[i], 0, (m * strength).coerceIn(0f, 0.6f))
            }
        }

        fun fillMask(mask: FloatArray, v: FloatArray) {
            val x0 = v[0]; val y0 = v[1]; val x1 = v[4]; val y1 = v[5]; val x2 = v[8]; val y2 = v[9]
            var area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if (abs(area) < 1e-6f) return
            val minX = max(0, floor(min(x0, min(x1, x2))).toInt()); val maxX = min(w - 1, ceil(max(x0, max(x1, x2))).toInt())
            val minY = max(0, floor(min(y0, min(y1, y2))).toInt()); val maxY = min(h - 1, ceil(max(y0, max(y1, y2))).toInt())
            val sign = if (area < 0f) -1f else 1f
            area *= sign
            for (py in minY..maxY) {
                val cy = py + 0.5f
                for (px in minX..maxX) {
                    val cx = px + 0.5f
                    val w0 = ((x2 - x1) * (cy - y1) - (y2 - y1) * (cx - x1)) * sign
                    val w1 = ((x0 - x2) * (cy - y2) - (y0 - y2) * (cx - x2)) * sign
                    val w2 = ((x1 - x0) * (cy - y0) - (y1 - y0) * (cx - x0)) * sign
                    if (w0 >= 0f && w1 >= 0f && w2 >= 0f) mask[py * w + px] = 1f
                }
            }
        }

        fun blur(m: FloatArray, r: Int) {
            val tmp = FloatArray(m.size)
            val win = 2 * r + 1f
            for (y in 0 until h) {
                var acc = 0f
                val row = y * w
                for (x in -r..r) acc += m[row + x.coerceIn(0, w - 1)]
                for (x in 0 until w) {
                    tmp[row + x] = acc / win
                    acc += m[row + (x + r + 1).coerceAtMost(w - 1)] - m[row + (x - r).coerceAtLeast(0)]
                }
            }
            for (x in 0 until w) {
                var acc = 0f
                for (y in -r..r) acc += tmp[y.coerceIn(0, h - 1) * w + x]
                for (y in 0 until h) {
                    m[y * w + x] = acc / win
                    acc += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
                }
            }
        }

        // ------------------------------------------------------------------------------------------ yüzler

        private val tri = FloatArray(VS * 3)
        private val transparent = ArrayList<LongArray>()

        fun drawPart(p: Prepared, transparentPass: Boolean, onlyFace: Int = -1) {
            val m = p.part.mesh
            val faces = if (onlyFace >= 0) intArrayOf(onlyFace) else IntArray(m.faceCount) { it }
            // v27 gradyan: parçanın kendi ekseni boyunca 0..1 (eksen başına bir kez)
            var gAxis = -1; var gLo = 0f; var gInv = 0f
            for (f in faces) {
                val mat = p.part.materialOf(f)
                if (mat.gradient != null && mat.gradientAxis != gAxis) {
                    gAxis = mat.gradientAxis.coerceIn(0, 2)
                    var lo = Float.POSITIVE_INFINITY; var hi = Float.NEGATIVE_INFINITY
                    var i = gAxis
                    while (i < m.pos.size) { val c = m.pos[i]; if (c < lo) lo = c; if (c > hi) hi = c; i += 3 }
                    gLo = lo; gInv = if (hi - lo > 1e-6f) 1f / (hi - lo) else 0f
                }
                if (mat.transparent != transparentPass) {
                    if (!transparentPass && mat.transparent) {
                        // Saydam yüz sonra (derinliğe göre) çizilir: (derinlik bitleri, parça, yüz)
                        val c = faceDepth(p, f)
                        transparent += longArrayOf(c.toRawBits().toLong(), p.index.toLong(), f.toLong())
                    }
                    continue
                }
                val face = m.faces[f]
                if (face.size < 3) continue
                val nx = p.normals[3 * f]; val ny = p.normals[3 * f + 1]; val nz = p.normals[3 * f + 2]
                val facing = facingViewer(p, f, nx, ny, nz)
                if (p.cull && !facing && !transparentPass) continue
                val selectedFace = opt.selectedPart == p.part.id && f in opt.selectedFaces
                val shader = Shader(mat, opt.textures, opt.bilinear, selectedFace, opt.highlight)
                val uv = p.uv[f]
                val corners = p.corners?.get(f)
                val tris = m.triangles[f]
                var tk = 0
                while (tk + 2 < tris.size) {
                    val t0 = tk
                    tk += 3
                    var ok = true
                    for (c in 0 until 3) {
                        val ci = tris[t0 + c]
                        val v = face[ci]
                        val o = VS * c
                        val s = p.screen[4 * v + 3]
                        if (s <= 0f) { ok = false; break }
                        tri[o] = p.screen[4 * v]; tri[o + 1] = p.screen[4 * v + 1]; tri[o + 2] = p.screen[4 * v + 2]
                        tri[o + 3] = if (view.perspective) s else 1f
                        if (corners != null) { tri[o + 4] = corners[3 * ci]; tri[o + 5] = corners[3 * ci + 1]; tri[o + 6] = corners[3 * ci + 2] }
                        else { tri[o + 4] = nx; tri[o + 5] = ny; tri[o + 6] = nz }
                        if (mat.gradient != null) {
                            tri[o + 7] = 0f; tri[o + 8] = (m.pos[3 * v + gAxis] - gLo) * gInv
                        } else {
                            val ts = 1f / mat.textureScale.coerceAtLeast(0.01f)
                            tri[o + 7] = uv[2 * ci] * ts; tri[o + 8] = uv[2 * ci + 1] * ts
                        }
                    }
                    if (ok) fillTriangle(shader, p.index + 1, f, corners != null, transparentPass)
                }
            }
        }

        fun facingViewer(p: Prepared, f: Int, nx: Float, ny: Float, nz: Float): Boolean {
            return if (!view.perspective) nx * T.x + ny * T.y + nz * T.z > 0f
            else {
                val e = view.eye
                val face = p.part.mesh.faces[f]
                val v = face[0]
                (e.x - p.world[3 * v]) * nx + (e.y - p.world[3 * v + 1]) * ny + (e.z - p.world[3 * v + 2]) * nz > 0f
            }
        }

        fun faceDepth(p: Prepared, f: Int): Float {
            var s = 0f
            val face = p.part.mesh.faces[f]
            for (v in face) s += p.screen[4 * v + 2]
            return s / face.size
        }

        fun drawTransparent() {
            if (transparent.isEmpty()) return
            transparent.sortBy { Float.fromBits(it[0].toInt()) }
            val byIndex = prepared.associateBy { it.index }
            for (t in transparent) {
                val p = byIndex[t[1].toInt()] ?: continue
                drawPart(p, transparentPass = true, onlyFace = t[2].toInt())
            }
        }

        /** Üçgeni ([tri]) çizer: kenar fonksiyonlu tarama, perspektife göre düzeltilmiş ara değerler. */
        fun fillTriangle(sh: Shader, pid: Int, fid: Int, smooth: Boolean, blend: Boolean) {
            val x0 = tri[0]; val y0 = tri[1]; val x1 = tri[VS]; val y1 = tri[VS + 1]; val x2 = tri[2 * VS]; val y2 = tri[2 * VS + 1]
            var area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if (abs(area) < 1e-7f) return
            val minX = max(0, floor(min(x0, min(x1, x2))).toInt()); val maxX = min(w - 1, ceil(max(x0, max(x1, x2))).toInt())
            val minY = max(0, floor(min(y0, min(y1, y2))).toInt()); val maxY = min(h - 1, ceil(max(y0, max(y1, y2))).toInt())
            if (minX > maxX || minY > maxY) return
            val sign = if (area < 0f) -1f else 1f
            area *= sign
            val inv = 1f / area
            val iw0 = tri[3]; val iw1 = tri[VS + 3]; val iw2 = tri[2 * VS + 3]
            val z0 = tri[2] * iw0; val z1 = tri[VS + 2] * iw1; val z2 = tri[2 * VS + 2] * iw2
            val u0 = tri[7] * iw0; val u1 = tri[VS + 7] * iw1; val u2 = tri[2 * VS + 7] * iw2
            val v0 = tri[8] * iw0; val v1 = tri[VS + 8] * iw1; val v2 = tri[2 * VS + 8] * iw2
            val ax0 = tri[4] * iw0; val ax1 = tri[VS + 4] * iw1; val ax2 = tri[2 * VS + 4] * iw2
            val ay0 = tri[5] * iw0; val ay1 = tri[VS + 5] * iw1; val ay2 = tri[2 * VS + 5] * iw2
            val az0 = tri[6] * iw0; val az1 = tri[VS + 6] * iw1; val az2 = tri[2 * VS + 6] * iw2
            val fnx = tri[4]; val fny = tri[5]; val fnz = tri[6]
            // Kenar fonksiyonlarının x ve y adımları
            val e0dx = -(y2 - y1) * sign; val e0dy = (x2 - x1) * sign
            val e1dx = -(y0 - y2) * sign; val e1dy = (x0 - x2) * sign
            val e2dx = -(y1 - y0) * sign; val e2dy = (x1 - x0) * sign
            val sx0 = minX + 0.5f; val sy0 = minY + 0.5f
            var r0 = ((x2 - x1) * (sy0 - y1) - (y2 - y1) * (sx0 - x1)) * sign
            var r1 = ((x0 - x2) * (sy0 - y2) - (y0 - y2) * (sx0 - x2)) * sign
            var r2 = ((x1 - x0) * (sy0 - y0) - (y1 - y0) * (sx0 - x0)) * sign
            for (py in minY..maxY) {
                var w0 = r0; var w1 = r1; var w2 = r2
                var idx = py * w + minX
                for (px in minX..maxX) {
                    if (w0 >= 0f && w1 >= 0f && w2 >= 0f) {
                        val b0 = w0 * inv; val b1 = w1 * inv; val b2 = w2 * inv
                        val iw = b0 * iw0 + b1 * iw1 + b2 * iw2
                        val rw = 1f / iw
                        val z = (b0 * z0 + b1 * z1 + b2 * z2) * rw
                        if (z > depth[idx]) {
                            val nx: Float; val ny: Float; val nz: Float
                            if (smooth) {
                                var a = (b0 * ax0 + b1 * ax1 + b2 * ax2); var b = (b0 * ay0 + b1 * ay1 + b2 * ay2); var c = (b0 * az0 + b1 * az1 + b2 * az2)
                                val l = 1f / sqrt(a * a + b * b + c * c).coerceAtLeast(1e-9f)
                                a *= l; b *= l; c *= l
                                nx = a; ny = b; nz = c
                            } else { nx = fnx; ny = fny; nz = fnz }
                            val u = (b0 * u0 + b1 * u1 + b2 * u2) * rw
                            val v = (b0 * v0 + b1 * v1 + b2 * v2) * rw
                            val c = sh.shade(nx, ny, nz, u, v, this)
                            if (blend) {
                                color[idx] = over(color[idx], c and 0x00FFFFFF, (c ushr 24) / 255f)
                                partId[idx] = pid; faceId[idx] = fid
                            } else {
                                color[idx] = c or (0xFF shl 24)
                                depth[idx] = z
                                partId[idx] = pid; faceId[idx] = fid
                            }
                        }
                    }
                    w0 += e0dx; w1 += e1dx; w2 += e2dx
                    idx++
                }
                r0 += e0dy; r1 += e1dy; r2 += e2dy
            }
        }

        // ------------------------------------------------------------------------------------------ çizgiler

        fun drawStrokes() {
            val a = FloatArray(4); val b = FloatArray(4)
            model.strokes.forEachIndexed { si, s ->
                if (s.count == 0) return@forEachIndexed
                val id = -(si + 1)
                for (i in 0 until s.count) {
                    view.project(s.points[3 * i], s.points[3 * i + 1], s.points[3 * i + 2], b)
                    if (i == 0) {
                        if (b[3] > 0f) disc(b[0], b[1], b[2], s.width * 0.5f * view.scale * b[3], s.color, id)
                    } else if (a[3] > 0f && b[3] > 0f) {
                        val ra = s.width * 0.5f * view.scale * a[3]; val rb = s.width * 0.5f * view.scale * b[3]
                        val len = sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]))
                        val step = max(0.75f, min(ra, rb) * 0.5f)
                        val n = max(1, ceil(len / step).toInt())
                        for (k in 1..n) {
                            val t = k / n.toFloat()
                            disc(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, ra + (rb - ra) * t, s.color, id)
                        }
                    }
                    a[0] = b[0]; a[1] = b[1]; a[2] = b[2]; a[3] = b[3]
                }
            }
        }

        fun disc(cx: Float, cy: Float, z: Float, radius: Float, c: Int, id: Int) {
            val r = max(radius, 0.6f)
            val x0 = max(0, floor(cx - r).toInt()); val x1 = min(w - 1, ceil(cx + r).toInt())
            val y0 = max(0, floor(cy - r).toInt()); val y1 = min(h - 1, ceil(cy + r).toInt())
            val rr = r * r
            for (y in y0..y1) for (x in x0..x1) {
                val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
                if (dx * dx + dy * dy > rr) continue
                val i = y * w + x
                if (z + extent * 0.004f < depth[i]) continue
                color[i] = c or (0xFF shl 24); depth[i] = max(depth[i], z); partId[i] = id; faceId[i] = 0
            }
        }

        fun drawEdges() {
            val a = FloatArray(4); val b = FloatArray(4)
            val edgeW = q * 1.1f
            val selW = q * 1.8f
            for (p in prepared) {
                val selected = opt.selectedPart == p.part.id || p.part.id in opt.selectedParts
                if (!scene.edges && !selected) continue
                val m = p.part.mesh
                val e = m.edges
                val fn = m.faceNormals
                val col = if (selected) opt.highlight else (scene.edgeColor and 0x00FFFFFF) or (0xB0 shl 24)
                var i = 0
                while (i < e.size) {
                    val va = e[i]; val vb = e[i + 1]; val f1 = e[i + 2]; val f2 = e[i + 3]
                    i += 4
                    val draw = when {
                        selected && opt.wireframe -> true
                        f2 < 0 -> true
                        else -> {
                            val front1 = facingViewer(p, f1, p.normals[3 * f1], p.normals[3 * f1 + 1], p.normals[3 * f1 + 2])
                            val front2 = facingViewer(p, f2, p.normals[3 * f2], p.normals[3 * f2 + 1], p.normals[3 * f2 + 2])
                            val dot = fn[3 * f1] * fn[3 * f2] + fn[3 * f1 + 1] * fn[3 * f2 + 1] + fn[3 * f1 + 2] * fn[3 * f2 + 2]
                            front1 != front2 || (dot < FEATURE_COS && (!p.part.smooth || dot < CREASE_COS))
                        }
                    }
                    if (!draw) continue
                    for (k in 0 until 4) { a[k] = p.screen[4 * va + k]; b[k] = p.screen[4 * vb + k] }
                    if (a[3] <= 0f || b[3] <= 0f) continue
                    line(a, b, col, if (selected) selW else edgeW, extent * 0.012f)
                }
            }
        }

        /** v29 çizilecek kenarların ([drawEdges] kuralıyla, sahne ayarından bağımsız) görünen parçaları. */
        fun visibleEdges(step: Float): List<FloatArray> {
            val out = ArrayList<FloatArray>()
            val a = FloatArray(4); val b = FloatArray(4)
            val bias = extent * 0.012f
            for (p in prepared) {
                val m = p.part.mesh
                val e = m.edges
                val fn = m.faceNormals
                var i = 0
                while (i < e.size) {
                    val va = e[i]; val vb = e[i + 1]; val f1 = e[i + 2]; val f2 = e[i + 3]
                    i += 4
                    val draw = f2 < 0 || run {
                        val front1 = facingViewer(p, f1, p.normals[3 * f1], p.normals[3 * f1 + 1], p.normals[3 * f1 + 2])
                        val front2 = facingViewer(p, f2, p.normals[3 * f2], p.normals[3 * f2 + 1], p.normals[3 * f2 + 2])
                        val dot = fn[3 * f1] * fn[3 * f2] + fn[3 * f1 + 1] * fn[3 * f2 + 1] + fn[3 * f1 + 2] * fn[3 * f2 + 2]
                        front1 != front2 || (dot < FEATURE_COS && (!p.part.smooth || dot < CREASE_COS))
                    }
                    if (!draw) continue
                    for (k in 0 until 4) { a[k] = p.screen[4 * va + k]; b[k] = p.screen[4 * vb + k] }
                    if (a[3] <= 0f || b[3] <= 0f) continue
                    val dx = b[0] - a[0]; val dy = b[1] - a[1]
                    val n = max(1, ceil(kotlin.math.hypot(dx, dy) / step).toInt())
                    val vis = BooleanArray(n + 1) { k ->
                        val t = k / n.toFloat()
                        val x = a[0] + dx * t; val y = a[1] + dy * t; val z = a[2] + (b[2] - a[2]) * t
                        val px = floor(x).toInt(); val py = floor(y).toInt()
                        if (px < -1 || py < -1 || px > w || py > h) false
                        else {
                            // Komşu piksellerden birinde önünde bir yüzey yoksa görünür (dış hatta zemin, kıvrımda kendi
                            // yüzeyi); arkada kalan kenarın bütün komşuları öndeki yüzeydir
                            var visible = false
                            for (oy in -1..1) for (ox in -1..1) {
                                val qx = px + ox; val qy = py + oy
                                if (qx !in 0 until w || qy !in 0 until h || z + bias >= depth[qy * w + qx]) visible = true
                            }
                            visible
                        }
                    }
                    // Kısa kesintiler (örnekleme gürültüsü) doldurulur, tek başına kalan örnekler atılır
                    var k = 0
                    while (k <= n) {
                        if (!vis[k]) {
                            var e = k
                            while (e <= n && !vis[e]) e++
                            if (k > 0 && e <= n && e - k <= GAP_FILL) for (j in k until e) vis[j] = true
                            k = e
                        } else k++
                    }
                    var runStart = -1
                    for (j in 0..n + 1) {
                        val on = j <= n && vis[j]
                        if (on && runStart < 0) runStart = j
                        if (!on && runStart >= 0) {
                            val end = j - 1
                            if (end > runStart) {
                                val t0 = runStart / n.toFloat(); val t1 = end / n.toFloat()
                                out.add(floatArrayOf(a[0] + dx * t0, a[1] + dy * t0, a[0] + dx * t1, a[1] + dy * t1))
                            }
                            runStart = -1
                        }
                    }
                }
            }
            return out
        }

        fun line(a: FloatArray, b: FloatArray, c: Int, width: Float, bias: Float) {
            val dx = b[0] - a[0]; val dy = b[1] - a[1]
            val len = max(abs(dx), abs(dy))
            val n = max(1, ceil(len).toInt())
            val r = width * 0.5f
            val alpha = (c ushr 24) / 255f
            for (k in 0..n) {
                val t = k / n.toFloat()
                val x = a[0] + dx * t; val y = a[1] + dy * t; val z = a[2] + (b[2] - a[2]) * t
                val x0 = max(0, floor(x - r).toInt()); val x1 = min(w - 1, floor(x + r).toInt())
                val y0 = max(0, floor(y - r).toInt()); val y1 = min(h - 1, floor(y + r).toInt())
                for (py in y0..y1) for (px in x0..x1) {
                    val i = py * w + px
                    if (z + bias < depth[i]) continue
                    color[i] = over(color[i], c and 0x00FFFFFF, alpha)
                }
            }
        }

        fun lineNoDepth(a: FloatArray, b: FloatArray, c: Int, width: Float) {
            if (a[3] <= 0f || b[3] <= 0f) return
            val dx = b[0] - a[0]; val dy = b[1] - a[1]
            val len = max(abs(dx), abs(dy))
            val n = max(1, ceil(len).toInt())
            val r = width * 0.5f
            val alpha = (c ushr 24) / 255f
            for (k in 0..n) {
                val t = k / n.toFloat()
                val x = a[0] + dx * t; val y = a[1] + dy * t
                val x0 = max(0, floor(x - r).toInt()); val x1 = min(w - 1, floor(x + r - 0.001f).toInt())
                val y0 = max(0, floor(y - r).toInt()); val y1 = min(h - 1, floor(y + r - 0.001f).toInt())
                for (py in y0..y1) for (px in x0..x1) {
                    val i = py * w + px
                    color[i] = over(color[i], c and 0x00FFFFFF, alpha)
                }
            }
        }

        // ------------------------------------------------------------------------------------------ çıkış

        fun output(out: View3): Frame {
            val ow = out.width; val oh = out.height
            if (q == 1) return Frame(ow, oh, color, depth, partId, faceId, out)
            val c = IntArray(ow * oh); val d = FloatArray(ow * oh); val p = IntArray(ow * oh); val f = IntArray(ow * oh)
            val k = q * q
            val mid = q / 2
            for (y in 0 until oh) for (x in 0 until ow) {
                var a = 0; var r = 0; var g = 0; var b = 0
                for (j in 0 until q) for (i in 0 until q) {
                    val s = color[(y * q + j) * w + x * q + i]
                    val sa = s ushr 24
                    a += sa; r += (s shr 16 and 0xFF) * sa; g += (s shr 8 and 0xFF) * sa; b += (s and 0xFF) * sa
                }
                val o = y * ow + x
                if (a > 0) c[o] = ((a / k) shl 24) or ((r / a) shl 16) or ((g / a) shl 8) or (b / a)
                val src = (y * q + mid) * w + x * q + mid
                d[o] = depth[src]; p[o] = partId[src]; f[o] = faceId[src]
            }
            return Frame(ow, oh, c, d, p, f, out)
        }
    }

    /** [dst] üstüne [rgb] rengini [alpha] örtücülükle bindirir (önçarpımsız ARGB). */
    internal fun over(dst: Int, rgb: Int, alpha: Float): Int {
        if (alpha <= 0f) return dst
        val da = (dst ushr 24) / 255f
        val oa = alpha + da * (1f - alpha)
        if (oa <= 1e-6f) return 0
        fun ch(sh: Int): Int {
            val s = (rgb shr sh and 0xFF) / 255f; val d = (dst shr sh and 0xFF) / 255f
            return (((s * alpha + d * da * (1f - alpha)) / oa) * 255f + 0.5f).toInt().coerceIn(0, 255)
        }
        return ((oa * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Malzemenin piksel rengi (ışık, doku, parlama, metal yansıması, cam saydamlığı). */
    private class Shader(
        val mat: Material,
        textures: (String) -> Texture?,
        val bilinear: Boolean,
        val selected: Boolean,
        val highlight: Int,
    ) {
        // v27 gradyanlı malzemede doku yok; v koordinatı gradyan konumu (0..1)
        val tex: Texture? = if (mat.gradient != null) null else mat.texture?.let { textures(it) }
        val cr = (mat.color shr 16 and 0xFF) / 255f
        val cg = (mat.color shr 8 and 0xFF) / 255f
        val cb = (mat.color and 0xFF) / 255f
        val grad = mat.gradient
        val gr = ((grad ?: 0) shr 16 and 0xFF) / 255f
        val gg = ((grad ?: 0) shr 8 and 0xFF) / 255f
        val gb = ((grad ?: 0) and 0xFF) / 255f
        val specStrength = when (mat.kind) {
            MaterialKind.MATTE -> 0f
            MaterialKind.PLASTIC -> 0.12f + 0.5f * mat.shine
            MaterialKind.METAL -> 0.55f + 0.45f * mat.shine
            MaterialKind.GLASS -> 0.9f
            MaterialKind.GLOW -> 0f
        }
        val lut = specLut(8f + mat.shine.coerceIn(0f, 1f) * 120f)

        fun shade(nx0: Float, ny0: Float, nz0: Float, u: Float, v: Float, r: Raster): Int {
            var nx = nx0; var ny = ny0; var nz = nz0
            val ndv = nx * r.T.x + ny * r.T.y + nz * r.T.z
            if (ndv < 0f) { nx = -nx; ny = -ny; nz = -nz }
            var br: Float; var bg: Float; var bb: Float
            val t = tex
            if (t != null) {
                val texel = sample(t, u, v)
                br = (texel shr 16 and 0xFF) / 255f * cr; bg = (texel shr 8 and 0xFF) / 255f * cg; bb = (texel and 0xFF) / 255f * cb
            } else if (grad != null) {
                val k = v.coerceIn(0f, 1f)
                br = cr + (gr - cr) * k; bg = cg + (gg - cg) * k; bb = cb + (gb - cb) * k
            } else { br = cr; bg = cg; bb = cb }
            if (mat.kind == MaterialKind.GLOW) return pack(br * 1.08f, bg * 1.08f, bb * 1.08f, mat.opacity)
            val light = r.light
            val ndl = nx * r.L.x + ny * r.L.y + nz * r.L.z
            val amb = light.ambient * (0.82f + 0.18f * ny)
            val diff = max(0f, ndl) * light.intensity * (1f - light.ambient * 0.5f)
            val metal = mat.kind == MaterialKind.METAL
            val lit = (amb + diff * (if (metal) 0.75f else 1f))
            var or = br * lit * (0.6f + 0.4f * r.lightR)
            var og = bg * lit * (0.6f + 0.4f * r.lightG)
            var ob = bb * lit * (0.6f + 0.4f * r.lightB)
            if (specStrength > 0f && ndl > 0f) {
                val ndh = max(0f, nx * r.H.x + ny * r.H.y + nz * r.H.z)
                val s = specStrength * lut[(ndh * 255f).toInt().coerceIn(0, 255)] * light.intensity
                if (metal) { or += s * (0.3f + 0.7f * br); og += s * (0.3f + 0.7f * bg); ob += s * (0.3f + 0.7f * bb) }
                else { or += s * r.lightR; og += s * r.lightG; ob += s * r.lightB }
            }
            if (metal) {
                // Gökyüzü/zemin yansıması: yansıyan bakış yönünün yüksekliğine göre
                val rv = 2f * abs(ndv) * ny - r.T.y
                val e = (rv * 0.5f + 0.5f).coerceIn(0f, 1f)
                val env = 0.18f + 0.55f * e * e
                or += env * br * 0.6f; og += env * bg * 0.6f; ob += env * bb * 0.6f
            }
            var alpha = mat.opacity
            if (mat.kind == MaterialKind.GLASS) {
                val fr = (1f - abs(ndv)).let { it * it }
                alpha = (mat.opacity * (0.3f + 0.6f * fr) + 0.08f).coerceIn(0.05f, 1f)
                or += fr * 0.25f; og += fr * 0.25f; ob += fr * 0.3f
            }
            if (selected) {
                val hr = (highlight shr 16 and 0xFF) / 255f; val hg = (highlight shr 8 and 0xFF) / 255f; val hb = (highlight and 0xFF) / 255f
                or = or * 0.55f + hr * 0.45f; og = og * 0.55f + hg * 0.45f; ob = ob * 0.55f + hb * 0.45f
            }
            return pack(or, og, ob, alpha)
        }

        fun pack(r: Float, g: Float, b: Float, a: Float): Int =
            ((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24) or
                ((r.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 16) or
                ((g.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 8) or
                (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()

        fun sample(t: Texture, u: Float, v: Float): Int {
            val fu = (u - floor(u)) * t.w; val fv = (v - floor(v)) * t.h
            if (!bilinear) {
                val x = fu.toInt().coerceIn(0, t.w - 1); val y = fv.toInt().coerceIn(0, t.h - 1)
                return t.px[y * t.w + x]
            }
            val x0f = fu - 0.5f; val y0f = fv - 0.5f
            val xi = floor(x0f).toInt(); val yi = floor(y0f).toInt()
            val ax = x0f - xi; val ay = y0f - yi
            val xa = Math.floorMod(xi, t.w); val xb = Math.floorMod(xi + 1, t.w)
            val ya = Math.floorMod(yi, t.h); val yb = Math.floorMod(yi + 1, t.h)
            val c00 = t.px[ya * t.w + xa]; val c10 = t.px[ya * t.w + xb]; val c01 = t.px[yb * t.w + xa]; val c11 = t.px[yb * t.w + xb]
            fun mix(sh: Int): Int {
                val top = (c00 shr sh and 0xFF) * (1f - ax) + (c10 shr sh and 0xFF) * ax
                val bot = (c01 shr sh and 0xFF) * (1f - ax) + (c11 shr sh and 0xFF) * ax
                return (top * (1f - ay) + bot * ay + 0.5f).toInt()
            }
            return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }

        companion object {
            private val luts = java.util.concurrent.ConcurrentHashMap<Int, FloatArray>()

            fun specLut(exp: Float): FloatArray = luts.getOrPut(exp.roundToInt()) {
                FloatArray(256) { i -> Math.pow(i / 255.0, exp.toDouble()).toFloat() }
            }
        }
    }
}
