package com.karapp.drawguidance.content.model3d.gl

import com.karapp.drawguidance.content.model3d.Material
import com.karapp.drawguidance.content.model3d.MaterialKind
import com.karapp.drawguidance.content.model3d.Mesh
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.content.model3d.Part
import com.karapp.drawguidance.content.model3d.Stroke3
import com.karapp.drawguidance.content.model3d.Texture
import com.karapp.drawguidance.content.model3d.TextureResize
import com.karapp.drawguidance.content.model3d.Transform3
import com.karapp.drawguidance.content.model3d.UvMapper
import com.karapp.drawguidance.content.model3d.UvMode
import com.karapp.drawguidance.content.model3d.V3
import com.karapp.drawguidance.content.model3d.View3
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Ekran kartı çiziminin ayarları. */
class GlOptions(
    /** Doku kimliğinden doku (bellekte yoksa null: malzeme rengiyle çizilir). */
    val textures: (String) -> Texture? = { null },
    /** Sahnenin arka planı saydamsa ekranın temizleme rengi (ARGB; 0 saydam). */
    val clearColor: Int = 0,
    /** Seçili parçalar (kenarları vurgu rengiyle); [primary] asıl parça, [selectedFaces] onun seçili yüzleri. */
    val selected: Set<String> = emptySet(),
    val primary: String? = null,
    val selectedFaces: Set<Int> = emptySet(),
    /** Seçili parçanın tüm kenarları (köşe/kenar düzenlemede). */
    val wireframe: Boolean = false,
    val highlight: Int = 0xFF2F80ED.toInt(),
    /** Çizgi kalınlıklarının katı (ekran yoğunluğuna göre). */
    val lineScale: Float = 1f,
    val ground: Boolean? = null,
    val shadow: Boolean? = null,
    val edges: Boolean? = null,
)

/** Son karenin sayaçları (başarım göstergesi ve testler). */
class GlStats {
    var drawCalls = 0
    var triangles = 0
    /** Bu karede yeniden yüklenen köşe tamponları (taşı/döndür/ölçekle sırasında 0 olmalı). */
    var uploads = 0
    var cpuMicros = 0L
}

/**
 * v24 ekran kartı (OpenGL ES 2.0) 3B çizicisi. Yazılım çizicisiyle ([com.karapp.drawguidance.content.model3d.Renderer])
 * aynı görünüm: zemin ızgarası, yumuşak zemin gölgesi, Lambert/Blinn-Phong ışık, doku, metal ve cam, keskin kenar ve dış
 * hat çizgileri, 3B fırça çizgileri ve saydam parçalar.
 *
 * Hız: her parçanın ağı bir kez köşe tamponuna yüklenir (malzemeye göre gruplanmış üçgenler; yumuşak normaller ve doku
 * koordinatları hazır) ve parçanın konumu/dönüşü/ölçeği yalnız bir matris olarak gönderilir; böylece taşıma, döndürme ve
 * ölçekleme sırasında karede yalnız birkaç çizim çağrısı yapılır, hiçbir ağ yeniden hesaplanmaz. Kenarlar da bir kez
 * yüklenir; dış hat sınaması köşe gölgelendiricisinde yapılır. Gölge küçük bir dokuya çizilip bulanıklaştırılır.
 *
 * Tüm çağrılar GL iş parçacığında yapılmalı. Bağlam kaybolursa [contextLost] ve yeniden [init] çağrılır.
 */
class GlRenderer3(private val gl: GlApi, private val shadowSize: Int = 256) {

    val stats = GlStats()

    /** Son hata (derleme/bağlama). */
    var lastError: String? = null
        private set

    private var surface: Prog? = null
    private var line: Prog? = null
    private var shadowProg: Prog? = null
    private var blurProg: Prog? = null
    private var groundProg: Prog? = null

    private val meshes = HashMap<String, MeshEntry>()
    private val edges = HashMap<String, EdgeEntry>()
    private val strokes = HashMap<String, StrokeEntry>()
    private val textures = HashMap<String, TexEntry>()
    private var grid: GridEntry? = null

    private var quadVbo = 0
    private var groundVbo = 0
    private val shadowFbo = IntArray(2)
    private val shadowTex = IntArray(2)
    private var maxTex = 2048
    private var frameNo = 0L
    private var enabledAttribs = 0

    val ready: Boolean get() = surface != null

    // ------------------------------------------------------------------------------------------ yaşam döngüsü

    /** Gölgelendiricileri derler; başarısızsa false ([lastError]). */
    fun init(): Boolean {
        if (ready) return true
        return try {
            surface = program(GlShaders.SURFACE_VS, GlShaders.SURFACE_FS, "aPos", "aNormal", "aUv")
            line = program(GlShaders.LINE_VS, GlShaders.LINE_FS, "aP0", "aP1", "aN1", "aN2", "aInfo")
            shadowProg = program(GlShaders.SHADOW_VS, GlShaders.WHITE_FS, "aPos")
            blurProg = program(GlShaders.BLUR_VS, GlShaders.BLUR_FS, "aPos")
            groundProg = program(GlShaders.GROUND_VS, GlShaders.GROUND_FS, "aPos")
            maxTex = gl.maxTextureSize().coerceIn(256, 4096)
            quadVbo = gl.genBuffer()
            gl.bindBuffer(GL.ARRAY_BUFFER, quadVbo)
            gl.bufferData(GL.ARRAY_BUFFER, floats(floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f)), GL.STATIC_DRAW)
            groundVbo = gl.genBuffer()
            for (i in 0..1) {
                shadowTex[i] = gl.genTexture()
                gl.bindTexture(GL.TEXTURE_2D, shadowTex[i])
                gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.LINEAR)
                gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.LINEAR)
                gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE)
                gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE)
                gl.texImage2D(GL.TEXTURE_2D, 0, shadowSize, shadowSize, null)
                shadowFbo[i] = gl.genFramebuffer()
                gl.bindFramebuffer(GL.FRAMEBUFFER, shadowFbo[i])
                gl.framebufferTexture2D(GL.FRAMEBUFFER, GL.COLOR_ATTACHMENT0, GL.TEXTURE_2D, shadowTex[i], 0)
                if (gl.checkFramebufferStatus(GL.FRAMEBUFFER) != GL.FRAMEBUFFER_COMPLETE) shadowFbo[i] = 0
            }
            gl.bindFramebuffer(GL.FRAMEBUFFER, 0)
            gl.bindBuffer(GL.ARRAY_BUFFER, 0)
            true
        } catch (e: Exception) {
            lastError = e.message
            contextLost()
            false
        }
    }

    /** Bağlam kayboldu: GL nesneleri geçersiz, yalnız unutulur (silinmez). */
    fun contextLost() {
        surface = null; line = null; shadowProg = null; blurProg = null; groundProg = null
        meshes.clear(); edges.clear(); strokes.clear(); textures.clear(); grid = null
        quadVbo = 0; groundVbo = 0
        shadowFbo.fill(0); shadowTex.fill(0)
        enabledAttribs = 0
    }

    /** GL nesnelerini siler (bağlam hâlâ geçerliyken). */
    fun release() {
        listOfNotNull(surface, line, shadowProg, blurProg, groundProg).forEach { gl.deleteProgram(it.id) }
        meshes.values.forEach { gl.deleteBuffer(it.vbo) }
        edges.values.forEach { gl.deleteBuffer(it.vbo) }
        strokes.values.forEach { gl.deleteBuffer(it.vbo) }
        textures.values.forEach { gl.deleteTexture(it.id) }
        grid?.let { gl.deleteBuffer(it.vbo) }
        if (quadVbo != 0) gl.deleteBuffer(quadVbo)
        if (groundVbo != 0) gl.deleteBuffer(groundVbo)
        for (i in 0..1) {
            if (shadowFbo[i] != 0) gl.deleteFramebuffer(shadowFbo[i])
            if (shadowTex[i] != 0) gl.deleteTexture(shadowTex[i])
        }
        contextLost()
    }

    private fun program(vs: String, fs: String, vararg attribs: String): Prog {
        val v = shader(GL.VERTEX_SHADER, vs)
        val f = shader(GL.FRAGMENT_SHADER, fs)
        val p = gl.createProgram()
        gl.attachShader(p, v)
        gl.attachShader(p, f)
        attribs.forEachIndexed { i, a -> gl.bindAttribLocation(p, i, a) }
        gl.linkProgram(p)
        gl.deleteShader(v); gl.deleteShader(f)
        if (!gl.programLinked(p)) {
            val log = gl.programInfoLog(p)
            gl.deleteProgram(p)
            throw IllegalStateException("GL program bağlanamadı: $log")
        }
        return Prog(p, gl, attribs.size)
    }

    private fun shader(type: Int, src: String): Int {
        val s = gl.createShader(type)
        gl.shaderSource(s, src)
        gl.compileShader(s)
        if (!gl.shaderCompiled(s)) {
            val log = gl.shaderInfoLog(s)
            gl.deleteShader(s)
            throw IllegalStateException("GL gölgelendirici derlenemedi: $log")
        }
        return s
    }

    // ------------------------------------------------------------------------------------------ kare

    private class PartDraw(
        val part: Part,
        val model: FloatArray,
        val normal: FloatArray,
        val mirrored: Boolean,
        val min: FloatArray,
        val max: FloatArray,
    ) {
        lateinit var mesh: MeshEntry
        val center: V3 get() = V3((min[0] + max[0]) / 2f, (min[1] + max[1]) / 2f, (min[2] + max[2]) / 2f)
    }

    /**
     * Modeli [view] boyutunda [framebuffer]'a çizer (0: ekran). [view] kameranın izdüşümüdür; dokunuşla seçim aynı
     * görünümle ([com.karapp.drawguidance.content.model3d.Pick3]) yapılır.
     */
    fun render(model: Model3D, view: View3, opt: GlOptions = GlOptions(), framebuffer: Int = 0) {
        if (!ready && !init()) return
        val t0 = System.nanoTime()
        frameNo++
        stats.drawCalls = 0; stats.triangles = 0; stats.uploads = 0
        val scene = model.scene
        val w = view.width; val h = view.height

        gl.bindFramebuffer(GL.FRAMEBUFFER, framebuffer)
        gl.viewport(0, 0, w, h)
        val bg = if (scene.background != 0) scene.background else opt.clearColor
        gl.clearColor((bg shr 16 and 0xFF) / 255f, (bg shr 8 and 0xFF) / 255f, (bg and 0xFF) / 255f, (bg ushr 24) / 255f)
        gl.colorMask(true, true, true, true)
        gl.depthMask(true)
        gl.disable(GL.BLEND)
        gl.disable(GL.CULL_FACE)
        gl.clear(GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT)

        // Parçaların dönüşümü ve dünya kutuları (köşelerden; zemin yüksekliği için tam)
        val parts = ArrayList<PartDraw>(model.parts.size)
        var minY = Float.POSITIVE_INFINITY
        val box = floatArrayOf(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
        for (part in model.parts) {
            if (!part.visible || part.mesh.vertexCount == 0 || part.mesh.faceCount == 0) continue
            val t = Transform3.of(part)
            val lo = FloatArray(3) { Float.POSITIVE_INFINITY }; val hi = FloatArray(3) { Float.NEGATIVE_INFINITY }
            worldBounds(part.mesh, t.m, lo, hi)
            val m = t.m
            val det = m[0] * (m[5] * m[10] - m[6] * m[9]) - m[1] * (m[4] * m[10] - m[6] * m[8]) + m[2] * (m[4] * m[9] - m[5] * m[8])
            parts += PartDraw(part, mat4(m), mat3(t.n), det < 0f, lo, hi)
            minY = min(minY, lo[1])
            for (k in 0..2) { box[k] = min(box[k], lo[k]); box[3 + k] = max(box[3 + k], hi[k]) }
        }
        for (s in model.strokes) for (i in 0 until s.count) {
            for (k in 0..2) { val v = s.points[3 * i + k]; box[k] = min(box[k], v); box[3 + k] = max(box[3 + k], v) }
            minY = min(minY, s.points[3 * i + 1])
        }
        val groundY = if (minY.isFinite()) min(0f, minY) else 0f
        val extent = if (box[0].isFinite()) (sqrt(sq(box[3] - box[0]) + sq(box[4] - box[1]) + sq(box[5] - box[2])) * 0.5f).coerceAtLeast(0.25f) else 1f
        val showGround = opt.ground ?: scene.ground
        val gridSize = max(2f, ceil(extent * 2.5f))
        if (showGround || !box[0].isFinite()) {
            val g = if (showGround) gridSize else 1f
            box[0] = min(box[0], -g); box[2] = min(box[2], -g); box[3] = max(box[3], g); box[5] = max(box[5], g)
            box[1] = min(box[1], groundY); box[4] = max(box[4], groundY)
        }
        val viewProj = viewProjection(view, box)

        if (showGround) drawGrid(view, viewProj, groundY, gridSize, scene.edgeColor, opt)
        // Yüzlerin köşe tamponları (yalnız ağ değişince yeniden)
        for (pd in parts) pd.mesh = meshEntry(pd.part, opt)
        val light = scene.light
        val L = light.direction
        if ((opt.shadow ?: scene.shadow) && parts.isNotEmpty() && L.y >= 0.08f) drawShadow(parts, view, viewProj, groundY, L, light.intensity, framebuffer)
        drawSurfaces(parts, view, viewProj, model, opt, transparent = false)
        if (model.strokes.isNotEmpty()) drawStrokes(model.strokes, view, viewProj, extent)
        val showEdges = opt.edges ?: scene.edges
        if (showEdges || opt.selected.isNotEmpty()) drawEdges(parts, view, viewProj, scene.edgeColor, showEdges, extent, opt)
        drawSurfaces(parts, view, viewProj, model, opt, transparent = true)

        // Durumu temizle
        attribs(0)
        gl.disable(GL.BLEND); gl.disable(GL.CULL_FACE); gl.disable(GL.POLYGON_OFFSET_FILL)
        gl.depthMask(true)
        gl.bindBuffer(GL.ARRAY_BUFFER, 0)
        prune(model)
        stats.cpuMicros = (System.nanoTime() - t0) / 1000
    }

    /** Silinen parça/çizgi/dokuların GL nesnelerini bırakır. */
    private fun prune(model: Model3D) {
        if (meshes.size > model.parts.size) {
            val ids = model.parts.mapTo(HashSet()) { it.id }
            meshes.keys.filter { it !in ids }.forEach { gl.deleteBuffer(meshes.remove(it)!!.vbo) }
            edges.keys.filter { it !in ids }.forEach { gl.deleteBuffer(edges.remove(it)!!.vbo) }
        }
        if (strokes.size > model.strokes.size) {
            val ids = model.strokes.mapTo(HashSet()) { it.id }
            strokes.keys.filter { it !in ids }.forEach { gl.deleteBuffer(strokes.remove(it)!!.vbo) }
        }
        if (textures.size > 24) {
            textures.entries.filter { frameNo - it.value.used > 120 }.map { it.key }.forEach { gl.deleteTexture(textures.remove(it)!!.id) }
        }
    }

    // ------------------------------------------------------------------------------------------ yüzler

    private fun drawSurfaces(parts: List<PartDraw>, view: View3, viewProj: FloatArray, model: Model3D, opt: GlOptions, transparent: Boolean) {
        val list = if (!transparent) parts else parts.filter { pd -> pd.mesh.groups.any { (it.material ?: pd.part.material).transparent } }
            .sortedBy { view.depth(it.center.x, it.center.y, it.center.z) }
        if (list.isEmpty()) return
        val p = surface!!
        gl.useProgram(p.id)
        attribs(3)
        gl.uniformMatrix4(p.u("uViewProj"), viewProj)
        val light = model.scene.light
        val L = light.direction
        val T = view.toViewer
        val H = (L + T).normalized()
        gl.uniform3f(p.u("uL"), L.x, L.y, L.z)
        gl.uniform3f(p.u("uH"), H.x, H.y, H.z)
        gl.uniform3f(p.u("uT"), T.x, T.y, T.z)
        gl.uniform3f(p.u("uLightColor"), (light.color shr 16 and 0xFF) / 255f, (light.color shr 8 and 0xFF) / 255f, (light.color and 0xFF) / 255f)
        gl.uniform1f(p.u("uAmbient"), light.ambient)
        gl.uniform1f(p.u("uIntensity"), light.intensity)
        val hl = opt.highlight
        gl.uniform3f(p.u("uHighlight"), (hl shr 16 and 0xFF) / 255f, (hl shr 8 and 0xFF) / 255f, (hl and 0xFF) / 255f)
        gl.uniform1i(p.u("uTex"), 0)
        gl.activeTexture(GL.TEXTURE0)
        gl.enable(GL.DEPTH_TEST)
        gl.depthFunc(GL.LEQUAL)
        if (transparent) {
            gl.enable(GL.BLEND)
            gl.blendFuncSeparate(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA, GL.ONE, GL.ONE_MINUS_SRC_ALPHA)
            gl.depthMask(false)
            gl.disable(GL.POLYGON_OFFSET_FILL)
        } else {
            gl.disable(GL.BLEND)
            gl.depthMask(true)
            // Yüzler biraz geriye: kenar çizgileri üstte kalsın
            gl.enable(GL.POLYGON_OFFSET_FILL)
            gl.polygonOffset(1f, 1f)
        }
        for (pd in list) {
            val e = pd.mesh
            gl.bindBuffer(GL.ARRAY_BUFFER, e.vbo)
            gl.vertexAttribPointer(0, 3, 32, 0)
            gl.vertexAttribPointer(1, 3, 32, 12)
            gl.vertexAttribPointer(2, 2, 32, 24)
            gl.uniformMatrix4(p.u("uModel"), pd.model)
            gl.uniformMatrix3(p.u("uNormal"), pd.normal)
            gl.frontFace(if (pd.mirrored) GL.CW else GL.CCW)
            // v27 gradyan: parçanın ekseni ve ağın o eksendeki aralığı (gradyanlı malzeme yoksa etkisiz)
            gradientRange(p, pd.part)
            val closed = pd.part.mesh.closed
            // Saydam kapalı parçada önce arka, sonra ön yüzler
            val passes = if (transparent && closed) intArrayOf(GL.FRONT, GL.BACK) else if (closed) intArrayOf(GL.BACK) else intArrayOf(0)
            for (cull in passes) {
                if (cull == 0) gl.disable(GL.CULL_FACE) else { gl.enable(GL.CULL_FACE); gl.cullFace(cull) }
                for (g in e.groups) {
                    val mat = g.material ?: pd.part.material
                    if (mat.transparent != transparent) continue
                    material(p, mat, g.selected, opt)
                    gl.drawArrays(GL.TRIANGLES, g.first, g.count)
                    stats.drawCalls++; stats.triangles += g.count / 3
                }
            }
        }
        gl.disable(GL.CULL_FACE)
        gl.disable(GL.POLYGON_OFFSET_FILL)
        gl.depthMask(true)
        gl.disable(GL.BLEND)
    }

    /** v27 gradyan ekseni ve aralığı (ağın kendi koordinatlarında; ağ başına önbellekli). */
    private fun gradientRange(p: Prog, part: Part) {
        val mat = part.material.takeIf { it.gradient != null } ?: part.faceMaterials.values.firstOrNull { it.gradient != null }
        val axis = (mat?.gradientAxis ?: 1).coerceIn(0, 2)
        val m = part.mesh
        val key = System.identityHashCode(m) * 3 + axis
        val range = gradRanges[key]?.takeIf { it.first === m }?.second ?: run {
            var lo = Float.POSITIVE_INFINITY; var hi = Float.NEGATIVE_INFINITY
            var i = axis
            while (i < m.pos.size) { val c = m.pos[i]; if (c < lo) lo = c; if (c > hi) hi = c; i += 3 }
            val r = floatArrayOf(if (lo.isFinite()) lo else 0f, if (hi - lo > 1e-6f) 1f / (hi - lo) else 0f)
            if (gradRanges.size > 256) gradRanges.clear()
            gradRanges[key] = m to r
            r
        }
        gl.uniform3f(p.u("uGradAxis"), if (axis == 0) 1f else 0f, if (axis == 1) 1f else 0f, if (axis == 2) 1f else 0f)
        gl.uniform2f(p.u("uGradRange"), range[0], range[1])
    }

    private val gradRanges = HashMap<Int, Pair<com.karapp.drawguidance.content.model3d.Mesh, FloatArray>>()

    private fun material(p: Prog, mat: Material, selected: Boolean, opt: GlOptions) {
        gl.uniform4f(p.u("uColor"), (mat.color shr 16 and 0xFF) / 255f, (mat.color shr 8 and 0xFF) / 255f, (mat.color and 0xFF) / 255f, mat.opacity.coerceIn(0f, 1f))
        gl.uniform1f(p.u("uKind"), when (mat.kind) { MaterialKind.MATTE -> 0f; MaterialKind.PLASTIC -> 1f; MaterialKind.METAL -> 2f; MaterialKind.GLASS -> 3f; MaterialKind.GLOW -> 4f })
        gl.uniform1f(p.u("uSpec"), specStrength(mat))
        gl.uniform1f(p.u("uExp"), 8f + mat.shine.coerceIn(0f, 1f) * 120f)
        gl.uniform1f(p.u("uSelected"), if (selected) 1f else 0f)
        val grad = mat.gradient
        gl.uniform4f(p.u("uGradColor"), ((grad ?: 0) shr 16 and 0xFF) / 255f, ((grad ?: 0) shr 8 and 0xFF) / 255f, ((grad ?: 0) and 0xFF) / 255f, if (grad != null) 1f else 0f)
        val tex = if (grad != null) 0 else mat.texture?.let { texture(it, opt) } ?: 0
        gl.uniform1f(p.u("uTextured"), if (tex != 0) 1f else 0f)
        gl.uniform1f(p.u("uUvScale"), 1f / mat.textureScale.coerceAtLeast(0.01f))
        if (tex != 0) gl.bindTexture(GL.TEXTURE_2D, tex)
    }

    private fun texture(id: String, opt: GlOptions): Int? {
        val src = opt.textures(id) ?: return null
        val e = textures[id]
        if (e != null && e.src === src) { e.used = frameNo; return e.id }
        val t = TextureResize.toPowerOfTwo(src, min(1024, maxTex))
        val buf = bytes(t.w * t.h * 4)
        for (c in t.px) { buf.put((c shr 16 and 0xFF).toByte()); buf.put((c shr 8 and 0xFF).toByte()); buf.put((c and 0xFF).toByte()); buf.put(0xFF.toByte()) }
        flip(buf)
        val tid = e?.id ?: gl.genTexture()
        gl.bindTexture(GL.TEXTURE_2D, tid)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.LINEAR_MIPMAP_LINEAR)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.LINEAR)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.REPEAT)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.REPEAT)
        gl.texImage2D(GL.TEXTURE_2D, 0, t.w, t.h, buf)
        gl.generateMipmap(GL.TEXTURE_2D)
        textures[id] = TexEntry(src, tid, frameNo)
        return tid
    }

    // ------------------------------------------------------------------------------------------ ağ tamponları

    private class Group(val material: Material?, val selected: Boolean, val first: Int, val count: Int)

    private class MeshEntry(
        val vbo: Int,
        val mesh: Mesh,
        val smooth: Boolean,
        val uvMode: UvMode?,
        val uvScale: V3?,
        val faceMaterials: Map<Int, Material>,
        val selectedFaces: Set<Int>,
        val groups: List<Group>,
    )

    private data class GroupKey(val material: Material?, val selected: Boolean)

    private fun meshEntry(part: Part, opt: GlOptions): MeshEntry {
        val textured = part.material.texture != null || part.faceMaterials.values.any { it.texture != null }
        val uvMode = if (textured) part.uv else null
        // Doku koordinatları kutu/silindir eşlemesinde ölçeğe bağlı; ağın kendi koordinatlarında değil
        val uvScale = if (textured && part.uv != UvMode.MESH) part.scale else null
        val sel = if (part.id == opt.primary) opt.selectedFaces else emptySet()
        val old = meshes[part.id]
        if (old != null && old.mesh === part.mesh && old.smooth == part.smooth && old.uvMode == uvMode && old.uvScale == uvScale &&
            (old.faceMaterials === part.faceMaterials || old.faceMaterials == part.faceMaterials) && old.selectedFaces == sel
        ) return old
        val m = part.mesh
        val fn = m.faceNormals
        val corners = if (part.smooth) m.cornerNormals(CREASE_COS) else null
        val uv = if (textured) UvMapper.cached(part) else null
        val groups = LinkedHashMap<GroupKey, ArrayList<Int>>()
        var total = 0
        for (f in 0 until m.faceCount) {
            val tris = m.triangles[f]
            if (tris.isEmpty()) continue
            groups.getOrPut(GroupKey(part.faceMaterials[f], f in sel)) { ArrayList() }.add(f)
            total += tris.size
        }
        val buf = scratch(total * 8)
        val out = ArrayList<Group>(groups.size)
        var written = 0
        for ((key, faces) in groups) {
            val first = written
            for (f in faces) {
                val face = m.faces[f]
                val tris = m.triangles[f]
                val cn = corners?.get(f)
                val fuv = uv?.get(f)
                for (ci in tris) {
                    val v = face[ci]
                    buf.put(m.pos[3 * v]); buf.put(m.pos[3 * v + 1]); buf.put(m.pos[3 * v + 2])
                    if (cn != null) { buf.put(cn[3 * ci]); buf.put(cn[3 * ci + 1]); buf.put(cn[3 * ci + 2]) }
                    else { buf.put(fn[3 * f]); buf.put(fn[3 * f + 1]); buf.put(fn[3 * f + 2]) }
                    if (fuv != null && 2 * ci + 1 < fuv.size) { buf.put(fuv[2 * ci]); buf.put(fuv[2 * ci + 1]) } else { buf.put(0f); buf.put(0f) }
                    written++
                }
            }
            out += Group(key.material, key.selected, first, written - first)
        }
        flip(buf)
        val vbo = old?.vbo ?: gl.genBuffer()
        gl.bindBuffer(GL.ARRAY_BUFFER, vbo)
        gl.bufferData(GL.ARRAY_BUFFER, buf, GL.STATIC_DRAW)
        stats.uploads++
        val e = MeshEntry(vbo, m, part.smooth, uvMode, uvScale, part.faceMaterials, sel, out)
        meshes[part.id] = e
        return e
    }

    private class EdgeEntry(val vbo: Int, val mesh: Mesh, val smooth: Boolean, val count: Int)

    /** Kenarlar: keskin ve açık kenarlar her zaman (tür 0), diğerleri dış hat adayı (tür 1). */
    private fun edgeEntry(part: Part): EdgeEntry {
        val old = edges[part.id]
        if (old != null && old.mesh === part.mesh && old.smooth == part.smooth) return old
        val m = part.mesh
        val e = m.edges
        val fn = m.faceNormals
        val n = e.size / 4
        val buf = scratch(n * 6 * LINE_FLOATS)
        var i = 0
        while (i < e.size) {
            val a = e[i]; val b = e[i + 1]; val f1 = e[i + 2]; val f2 = e[i + 3]
            i += 4
            val kind = if (f2 < 0) 0f else {
                val dot = fn[3 * f1] * fn[3 * f2] + fn[3 * f1 + 1] * fn[3 * f2 + 1] + fn[3 * f1 + 2] * fn[3 * f2 + 2]
                if (dot < FEATURE_COS && (!part.smooth || dot < CREASE_COS)) 0f else 1f
            }
            val g = if (f2 < 0) f1 else f2
            segment(
                buf, m.pos[3 * a], m.pos[3 * a + 1], m.pos[3 * a + 2], m.pos[3 * b], m.pos[3 * b + 1], m.pos[3 * b + 2],
                fn[3 * f1], fn[3 * f1 + 1], fn[3 * f1 + 2], fn[3 * g], fn[3 * g + 1], fn[3 * g + 2], kind,
            )
        }
        flip(buf)
        val vbo = old?.vbo ?: gl.genBuffer()
        gl.bindBuffer(GL.ARRAY_BUFFER, vbo)
        gl.bufferData(GL.ARRAY_BUFFER, buf, GL.STATIC_DRAW)
        stats.uploads++
        val entry = EdgeEntry(vbo, m, part.smooth, n * 6)
        edges[part.id] = entry
        return entry
    }

    private fun segment(
        buf: FloatBuffer, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        n1x: Float, n1y: Float, n1z: Float, n2x: Float, n2y: Float, n2z: Float, kind: Float,
    ) {
        for (c in 0 until 6) {
            buf.put(ax); buf.put(ay); buf.put(az); buf.put(bx); buf.put(by); buf.put(bz)
            buf.put(n1x); buf.put(n1y); buf.put(n1z); buf.put(n2x); buf.put(n2y); buf.put(n2z)
            buf.put(CORNER_T[c]); buf.put(CORNER_SIDE[c]); buf.put(kind)
        }
    }

    // ------------------------------------------------------------------------------------------ çizgiler

    private fun lineProgram(view: View3, viewProj: FloatArray): Prog {
        val p = line!!
        gl.useProgram(p.id)
        attribs(5)
        gl.uniformMatrix4(p.u("uViewProj"), viewProj)
        val e = if (view.perspective) view.eye else V3.ZERO
        val t = view.toViewer
        gl.uniform3f(p.u("uEye"), e.x, e.y, e.z)
        gl.uniform3f(p.u("uToViewer"), t.x, t.y, t.z)
        gl.uniform1f(p.u("uPersp"), if (view.perspective) 1f else 0f)
        gl.uniform2f(p.u("uHalf"), view.width / 2f, view.height / 2f)
        return p
    }

    private fun bindLineBuffer(vbo: Int) {
        gl.bindBuffer(GL.ARRAY_BUFFER, vbo)
        val stride = LINE_FLOATS * 4
        gl.vertexAttribPointer(0, 3, stride, 0)
        gl.vertexAttribPointer(1, 3, stride, 12)
        gl.vertexAttribPointer(2, 3, stride, 24)
        gl.vertexAttribPointer(3, 3, stride, 36)
        gl.vertexAttribPointer(4, 3, stride, 48)
    }

    private fun color(p: Prog, c: Int, alpha: Float = (c ushr 24) / 255f) =
        gl.uniform4f(p.u("uColor"), (c shr 16 and 0xFF) / 255f, (c shr 8 and 0xFF) / 255f, (c and 0xFF) / 255f, alpha)

    private class GridEntry(val vbo: Int, val size: Float, val step: Float, val lines: Int)

    private fun drawGrid(view: View3, viewProj: FloatArray, groundY: Float, size: Float, edgeColor: Int, opt: GlOptions) {
        val step = if (size > 6f) 1f else 0.5f
        var g = grid
        if (g == null || g.size != size || g.step != step) {
            // Izgara y = 0'da yüklenir; zemin yüksekliği matristen (taşırken yeniden yüklenmez)
            // Sıra: sıradan çizgiler, Z ekseni (x = 0), X ekseni (z = 0)
            val normal = ArrayList<FloatArray>()
            var v = -size
            while (v <= size + 1e-3f) {
                if (abs(v) >= 1e-3f) {
                    normal += floatArrayOf(v, 0f, -size, v, 0f, size)
                    normal += floatArrayOf(-size, 0f, v, size, 0f, v)
                }
                v += step
            }
            val all = normal + listOf(floatArrayOf(0f, 0f, -size, 0f, 0f, size), floatArrayOf(-size, 0f, 0f, size, 0f, 0f))
            val buf = scratch(all.size * 6 * LINE_FLOATS)
            for (s in all) segment(buf, s[0], s[1], s[2], s[3], s[4], s[5], 0f, 1f, 0f, 0f, 1f, 0f, 0f)
            flip(buf)
            val vbo = g?.vbo ?: gl.genBuffer()
            gl.bindBuffer(GL.ARRAY_BUFFER, vbo)
            gl.bufferData(GL.ARRAY_BUFFER, buf, GL.STATIC_DRAW)
            stats.uploads++
            g = GridEntry(vbo, size, step, normal.size)
            grid = g
        }
        val p = lineProgram(view, viewProj)
        bindLineBuffer(g.vbo)
        gl.uniformMatrix4(p.u("uModel"), floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, groundY, 0f, 1f))
        gl.uniformMatrix3(p.u("uNormal"), IDENTITY3)
        gl.uniform1f(p.u("uWidth"), 1f * opt.lineScale)
        gl.uniform1f(p.u("uWorldWidth"), 0f)
        gl.uniform1f(p.u("uBias"), 0f)
        gl.uniform1f(p.u("uWire"), 1f)
        gl.disable(GL.DEPTH_TEST)
        gl.depthMask(false)
        gl.enable(GL.BLEND)
        gl.blendFuncSeparate(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA, GL.ONE, GL.ONE_MINUS_SRC_ALPHA)
        color(p, 0x2E000000 or (edgeColor and 0x00FFFFFF))
        draw(GL.TRIANGLES, 0, g.lines * 6)
        color(p, 0x804A90E2.toInt())
        draw(GL.TRIANGLES, g.lines * 6, 6)
        color(p, 0x80E25C4A.toInt())
        draw(GL.TRIANGLES, g.lines * 6 + 6, 6)
        gl.depthMask(true)
        gl.disable(GL.BLEND)
    }

    private class StrokeEntry(val vbo: Int, val stroke: Stroke3, val count: Int)

    private fun drawStrokes(list: List<Stroke3>, view: View3, viewProj: FloatArray, extent: Float) {
        val p = lineProgram(view, viewProj)
        gl.uniformMatrix4(p.u("uModel"), IDENTITY4)
        gl.uniformMatrix3(p.u("uNormal"), IDENTITY3)
        gl.uniform1f(p.u("uBias"), extent * 0.004f)
        gl.uniform1f(p.u("uWire"), 1f)
        gl.uniform1f(p.u("uWidth"), 1f)
        gl.enable(GL.DEPTH_TEST)
        gl.depthFunc(GL.LEQUAL)
        gl.depthMask(true)
        gl.disable(GL.BLEND)
        val k = view.scale * (if (view.perspective) view.focal else 1f)
        for (s in list) {
            if (s.count == 0) continue
            var e = strokes[s.id]
            if (e == null || e.stroke !== s) {
                val segs = max(1, s.count - 1)
                val buf = scratch(segs * 6 * LINE_FLOATS)
                val pt = s.points
                if (s.count == 1) segment(buf, pt[0], pt[1], pt[2], pt[0], pt[1], pt[2], 0f, 0f, 0f, 0f, 0f, 0f, 0f)
                else for (i in 0 until s.count - 1) {
                    val a = 3 * i; val b = 3 * (i + 1)
                    segment(buf, pt[a], pt[a + 1], pt[a + 2], pt[b], pt[b + 1], pt[b + 2], 0f, 0f, 0f, 0f, 0f, 0f, 0f)
                }
                flip(buf)
                val vbo = e?.vbo ?: gl.genBuffer()
                gl.bindBuffer(GL.ARRAY_BUFFER, vbo)
                gl.bufferData(GL.ARRAY_BUFFER, buf, GL.STATIC_DRAW)
                stats.uploads++
                e = StrokeEntry(vbo, s, segs * 6)
                strokes[s.id] = e
            }
            bindLineBuffer(e.vbo)
            gl.uniform1f(p.u("uWorldWidth"), s.width * k)
            color(p, s.color, 1f)
            draw(GL.TRIANGLES, 0, e.count)
        }
    }

    private fun drawEdges(parts: List<PartDraw>, view: View3, viewProj: FloatArray, edgeColor: Int, sceneEdges: Boolean, extent: Float, opt: GlOptions) {
        val p = lineProgram(view, viewProj)
        gl.uniform1f(p.u("uWorldWidth"), 0f)
        gl.uniform1f(p.u("uBias"), extent * 0.012f)
        gl.enable(GL.DEPTH_TEST)
        gl.depthFunc(GL.LEQUAL)
        gl.depthMask(false)
        gl.enable(GL.BLEND)
        gl.blendFuncSeparate(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA, GL.ONE, GL.ONE_MINUS_SRC_ALPHA)
        for (pd in parts) {
            val selected = pd.part.id in opt.selected
            if (!sceneEdges && !selected) continue
            val e = edgeEntry(pd.part)
            if (e.count == 0) continue
            bindLineBuffer(e.vbo)
            gl.uniformMatrix4(p.u("uModel"), pd.model)
            gl.uniformMatrix3(p.u("uNormal"), pd.normal)
            gl.uniform1f(p.u("uWire"), if (selected && opt.wireframe) 1f else 0f)
            gl.uniform1f(p.u("uWidth"), (if (selected) 1.8f else 1.1f) * opt.lineScale)
            if (selected) color(p, opt.highlight, 1f) else color(p, edgeColor, 0xB0 / 255f)
            draw(GL.TRIANGLES, 0, e.count)
        }
        gl.depthMask(true)
        gl.disable(GL.BLEND)
    }

    // ------------------------------------------------------------------------------------------ gölge

    private fun drawShadow(parts: List<PartDraw>, view: View3, viewProj: FloatArray, groundY: Float, L: V3, intensity: Float, framebuffer: Int) {
        if (shadowFbo[0] == 0 || shadowFbo[1] == 0) return
        val ly = max(L.y, 0.3f)
        var ax = Float.POSITIVE_INFINITY; var az = Float.POSITIVE_INFINITY; var bx = Float.NEGATIVE_INFINITY; var bz = Float.NEGATIVE_INFINITY
        val casters = parts.filter { !(it.part.material.transparent && it.part.material.opacity < 0.5f) }
        if (casters.isEmpty()) return
        for (pd in casters) for (c in 0 until 8) {
            val x = if (c and 1 == 0) pd.min[0] else pd.max[0]
            val y = if (c and 2 == 0) pd.min[1] else pd.max[1]
            val z = if (c and 4 == 0) pd.min[2] else pd.max[2]
            val t = (y - groundY) / ly
            val px = x - L.x * t; val pz = z - L.z * t
            ax = min(ax, px); bx = max(bx, px); az = min(az, pz); bz = max(bz, pz)
        }
        val worldR = 0.012f * min(view.width, view.height) / view.scale.coerceAtLeast(1e-3f)
        val half = max(bx - ax, bz - az) / 2f + worldR * 3f + 0.02f
        val cx = (ax + bx) / 2f; val cz = (az + bz) / 2f
        val size = shadowSize

        // 1) Zemin maskesi
        gl.bindFramebuffer(GL.FRAMEBUFFER, shadowFbo[0])
        gl.viewport(0, 0, size, size)
        gl.clearColor(0f, 0f, 0f, 0f)
        gl.clear(GL.COLOR_BUFFER_BIT)
        gl.disable(GL.DEPTH_TEST); gl.disable(GL.BLEND); gl.disable(GL.CULL_FACE)
        val sp = shadowProg!!
        gl.useProgram(sp.id)
        attribs(1)
        gl.uniform3f(sp.u("uL"), L.x, L.y, L.z)
        gl.uniform1f(sp.u("uLy"), ly)
        gl.uniform1f(sp.u("uGround"), groundY)
        gl.uniform3f(sp.u("uRegion"), cx, cz, half)
        for (pd in casters) {
            val e = pd.mesh
            gl.bindBuffer(GL.ARRAY_BUFFER, e.vbo)
            gl.vertexAttribPointer(0, 3, 32, 0)
            gl.uniformMatrix4(sp.u("uModel"), pd.model)
            val n = e.groups.sumOf { it.count }
            draw(GL.TRIANGLES, 0, n)
        }

        // 2) Bulanıklaştırma (iki kez yatay + dikey)
        val bp = blurProg!!
        gl.useProgram(bp.id)
        attribs(1)
        gl.bindBuffer(GL.ARRAY_BUFFER, quadVbo)
        gl.vertexAttribPointer(0, 2, 8, 0)
        gl.uniform1i(bp.u("uTex"), 0)
        gl.activeTexture(GL.TEXTURE0)
        val texel = 2f * half / size
        val step = (worldR / texel / 4f) / size
        repeat(2) {
            gl.bindFramebuffer(GL.FRAMEBUFFER, shadowFbo[1])
            gl.bindTexture(GL.TEXTURE_2D, shadowTex[0])
            gl.uniform2f(bp.u("uStep"), step, 0f)
            draw(GL.TRIANGLES, 0, 6)
            gl.bindFramebuffer(GL.FRAMEBUFFER, shadowFbo[0])
            gl.bindTexture(GL.TEXTURE_2D, shadowTex[1])
            gl.uniform2f(bp.u("uStep"), 0f, step)
            draw(GL.TRIANGLES, 0, 6)
        }

        // 3) Zemine gölge (parçalardan önce; ızgaranın üstüne)
        gl.bindFramebuffer(GL.FRAMEBUFFER, framebuffer)
        gl.viewport(0, 0, view.width, view.height)
        val gp = groundProg!!
        gl.useProgram(gp.id)
        attribs(1)
        val quad = scratch(18)
        val x0 = cx - half; val x1 = cx + half; val z0 = cz - half; val z1 = cz + half
        quad.put(floatArrayOf(x0, groundY, z0, x1, groundY, z0, x1, groundY, z1, x0, groundY, z0, x1, groundY, z1, x0, groundY, z1))
        flip(quad)
        gl.bindBuffer(GL.ARRAY_BUFFER, groundVbo)
        gl.bufferData(GL.ARRAY_BUFFER, quad, GL.DYNAMIC_DRAW)
        gl.vertexAttribPointer(0, 3, 12, 0)
        gl.uniformMatrix4(gp.u("uViewProj"), viewProj)
        gl.uniform3f(gp.u("uRegion"), cx, cz, half)
        gl.uniform1f(gp.u("uStrength"), 0.34f * intensity.coerceIn(0f, 1.5f))
        gl.uniform1i(gp.u("uTex"), 0)
        gl.bindTexture(GL.TEXTURE_2D, shadowTex[0])
        gl.enable(GL.BLEND)
        gl.blendFuncSeparate(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA, GL.ONE, GL.ONE_MINUS_SRC_ALPHA)
        gl.depthMask(false)
        draw(GL.TRIANGLES, 0, 6)
        gl.depthMask(true)
        gl.disable(GL.BLEND)
    }

    // ------------------------------------------------------------------------------------------ yardımcılar

    private fun draw(mode: Int, first: Int, count: Int) {
        if (count <= 0) return
        gl.drawArrays(mode, first, count)
        stats.drawCalls++
        stats.triangles += count / 3
    }

    /** İlk [n] özniteliği açar, kalanları kapatır. */
    private fun attribs(n: Int) {
        for (i in n until enabledAttribs) gl.disableVertexAttribArray(i)
        for (i in enabledAttribs until n) gl.enableVertexAttribArray(i)
        enabledAttribs = n
    }

    private var scratchF: FloatBuffer = ByteBuffer.allocateDirect(4096 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var scratchB: ByteBuffer = ByteBuffer.allocateDirect(4096).order(ByteOrder.nativeOrder())

    private fun scratch(n: Int): FloatBuffer {
        if (scratchF.capacity() < n) scratchF = ByteBuffer.allocateDirect(Integer.highestOneBit(n) * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        (scratchF as Buffer).clear()
        return scratchF
    }

    private fun bytes(n: Int): ByteBuffer {
        if (scratchB.capacity() < n) scratchB = ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder())
        (scratchB as Buffer).clear()
        return scratchB
    }

    private fun floats(a: FloatArray): FloatBuffer = scratch(a.size).put(a).also { flip(it) }

    /** Buffer türünden (eski Android sürümlerinde FloatBuffer.flip() yöntemi yok). */
    private fun flip(b: Buffer) { b.flip() }

    private class Prog(val id: Int, private val gl: GlApi, val attribs: Int) {
        private val locs = HashMap<String, Int>()
        fun u(name: String): Int = locs.getOrPut(name) { gl.uniformLocation(id, name) }
    }

    private class TexEntry(val src: Texture, val id: Int, var used: Long)

    companion object {
        private const val CREASE_COS = 0.7f
        private const val FEATURE_COS = 0.82f
        private const val LINE_FLOATS = 15
        private val CORNER_T = floatArrayOf(0f, 1f, 1f, 0f, 1f, 0f)
        private val CORNER_SIDE = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f)
        private val IDENTITY4 = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        private val IDENTITY3 = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

        private fun sq(v: Float) = v * v

        private fun specStrength(mat: Material) = when (mat.kind) {
            MaterialKind.MATTE -> 0f
            MaterialKind.PLASTIC -> 0.12f + 0.5f * mat.shine
            MaterialKind.METAL -> 0.55f + 0.45f * mat.shine
            MaterialKind.GLASS -> 0.9f
            MaterialKind.GLOW -> 0f
        }

        /** Transform3'ün 3×4 satır düzeninden sütun düzeninde 4×4. */
        internal fun mat4(m: FloatArray) = floatArrayOf(
            m[0], m[4], m[8], 0f,
            m[1], m[5], m[9], 0f,
            m[2], m[6], m[10], 0f,
            m[3], m[7], m[11], 1f,
        )

        internal fun mat3(n: FloatArray) = floatArrayOf(n[0], n[3], n[6], n[1], n[4], n[7], n[2], n[5], n[8])

        /** Ağın dönüştürülmüş köşelerinin kutusu. */
        internal fun worldBounds(mesh: Mesh, m: FloatArray, lo: FloatArray, hi: FloatArray) {
            val p = mesh.pos
            var i = 0
            while (i < p.size) {
                val x = p[i]; val y = p[i + 1]; val z = p[i + 2]
                val wx = m[0] * x + m[1] * y + m[2] * z + m[3]
                val wy = m[4] * x + m[5] * y + m[6] * z + m[7]
                val wz = m[8] * x + m[9] * y + m[10] * z + m[11]
                if (wx < lo[0]) lo[0] = wx; if (wx > hi[0]) hi[0] = wx
                if (wy < lo[1]) lo[1] = wy; if (wy > hi[1]) hi[1] = wy
                if (wz < lo[2]) lo[2] = wz; if (wz > hi[2]) hi[2] = wz
                i += 3
            }
        }

        /**
         * [View3] ile aynı ekran izdüşümü (sütun düzeninde 4×4): x, y aynı piksele düşer; derinlik [box] (dünya kutusu:
         * min x, y, z, max x, y, z) sahnenin kamera derinlik aralığına yayılır.
         */
        fun viewProjection(view: View3, box: FloatArray): FloatArray {
            val o = view.camera.target
            val r = floatArrayOf(view.rx, view.ry, view.rz, -(view.rx * o.x + view.ry * o.y + view.rz * o.z))
            val d = floatArrayOf(view.dx, view.dy, view.dz, -(view.dx * o.x + view.dy * o.y + view.dz * o.z))
            val t = floatArrayOf(view.tx, view.ty, view.tz, -(view.tx * o.x + view.ty * o.y + view.tz * o.z))
            var zMin = Float.POSITIVE_INFINITY; var zMax = Float.NEGATIVE_INFINITY
            if (box[0].isFinite()) for (c in 0 until 8) {
                val x = if (c and 1 == 0) box[0] else box[3]
                val y = if (c and 2 == 0) box[1] else box[4]
                val z = if (c and 4 == 0) box[2] else box[5]
                val zc = view.depth(x, y, z)
                zMin = min(zMin, zc); zMax = max(zMax, zc)
            }
            if (!zMin.isFinite()) { zMin = -2f; zMax = 2f }
            val hw = view.width / 2f; val hh = view.height / 2f
            val offX = (view.centerX - hw) / hw
            val offY = (view.centerY - hh) / hh
            val ax = view.scale / hw; val ay = view.scale / hh
            val rows = Array(4) { FloatArray(4) }
            if (view.perspective) {
                val f = view.focal
                val wRow = FloatArray(4) { k -> (if (k == 3) f else 0f) - t[k] }
                val distMin = f - zMax; val distMax = f - zMin
                val range = (distMax - distMin).coerceAtLeast(1e-3f)
                val near = max(f * 0.02f, distMin - range * 0.05f - 0.01f)
                var far = distMax + range * 0.05f + 0.01f
                if (far <= near * 1.001f) far = near * 2f
                val a = (far + near) / (far - near)
                val b = -2f * far * near / (far - near)
                for (k in 0..3) {
                    rows[0][k] = offX * wRow[k] + f * ax * r[k]
                    rows[1][k] = -offY * wRow[k] - f * ay * d[k]
                    rows[2][k] = a * wRow[k] + (if (k == 3) b else 0f)
                    rows[3][k] = wRow[k]
                }
            } else {
                val c = (zMin + zMax) / 2f
                val half = ((zMax - zMin) / 2f * 1.1f + 0.01f)
                for (k in 0..3) {
                    rows[0][k] = ax * r[k] + (if (k == 3) offX else 0f)
                    rows[1][k] = -ay * d[k] + (if (k == 3) -offY else 0f)
                    rows[2][k] = -t[k] / half + (if (k == 3) c / half else 0f)
                    rows[3][k] = if (k == 3) 1f else 0f
                }
            }
            return FloatArray(16) { i -> rows[i % 4][i / 4] }
        }
    }
}
