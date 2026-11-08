package com.karapp.drawguidance.content.model3d.gl

import com.karapp.drawguidance.content.model3d.Camera3
import com.karapp.drawguidance.content.model3d.Catalog3D
import com.karapp.drawguidance.content.model3d.Material
import com.karapp.drawguidance.content.model3d.MaterialKind
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.content.model3d.ModelEdit
import com.karapp.drawguidance.content.model3d.PrimitiveKind
import com.karapp.drawguidance.content.model3d.RenderOptions
import com.karapp.drawguidance.content.model3d.Renderer
import com.karapp.drawguidance.content.model3d.Texture
import com.karapp.drawguidance.content.model3d.TextureCatalog
import com.karapp.drawguidance.content.model3d.V3
import com.karapp.drawguidance.content.model3d.View3
import com.karapp.drawguidance.content.model3d.Samples3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * v24 ekran kartı çizicisi: Mesa'nın ekransız OpenGL ES 2.0'ı ile gerçekten çizilir. Gölgelendiriciler derlenmeli,
 * izdüşüm yazılım çizicisiyle aynı piksellere düşmeli (aynı görünüm → aynı siluet), taşıma/döndürme/ölçeklemede ağ
 * yeniden yüklenmemeli ve renkler yazılım çizicisine yakın olmalı.
 */
class GlRendererTest {

    private lateinit var gl: LwjglGl

    @Before
    fun setUp() {
        assumeTrue("EGL/GLES yok", LwjglGl.ensureContext())
        gl = LwjglGl()
    }

    private fun previewDir(): File? = System.getProperty("previewDir")?.let { File(it).apply { mkdirs() } }

    private val textures = HashMap<String, Texture>()
    private fun tex(id: String): Texture? = textures.getOrPut(id) { TextureCatalog.byId(id)?.generate(128) ?: return null }

    private fun glRender(r: GlRenderer3, target: OffscreenTarget, model: Model3D, view: View3, opt: GlOptions): IntArray {
        r.render(model, view, opt, target.fbo)
        assertEquals("GL hatası", GL.NO_ERROR, gl.getError())
        return target.read()
    }

    private fun coverage(px: IntArray) = BooleanArray(px.size) { (px[it] ushr 24) > 128 }

    private fun iou(a: BooleanArray, b: BooleanArray): Double {
        var inter = 0; var union = 0
        for (i in a.indices) { if (a[i] && b[i]) inter++; if (a[i] || b[i]) union++ }
        return if (union == 0) 1.0 else inter.toDouble() / union
    }

    @Test
    fun shadersCompileAndSilhouetteMatchesSoftwareRenderer() {
        val r = GlRenderer3(gl)
        assertTrue("Derleme: ${r.lastError}", r.init())
        val w = 240; val h = 200
        val target = OffscreenTarget(gl, w, h)
        val models = listOf(
            Samples3D.all.first(),
            Catalog3D.byId("geo.cube_diag")!!.build(),
            Catalog3D.byId("chem.benzene")!!.build(),
            Catalog3D.byId("draw.chair")!!.build(),
        )
        for ((i, m0) in models.withIndex()) {
            for (persp in listOf(0f, 0.35f, 0.9f)) {
                val m = m0.copy(camera = m0.camera.copy(persp = persp), scene = m0.scene.copy(edges = false))
                val view = View3.fit(m, m.camera, w, h, 0.1f)
                val glPx = glRender(r, target, m, view, GlOptions(ground = false, shadow = false, edges = false))
                val sw = Renderer.render(m, view, RenderOptions(supersample = 1, ground = false, shadow = false, background = 0)).color
                // Kenar yok: yalnız yüzler; saydam (cam) yüzler eşikte farklı olabilir
                val score = iou(coverage(glPx), coverage(sw))
                if (score <= 0.93) previewDir()?.let { dir ->
                    val img = BufferedImage(w * 2, h, BufferedImage.TYPE_INT_ARGB)
                    img.setRGB(0, 0, w, h, glPx, 0, w); img.setRGB(w, 0, w, h, sw, 0, w)
                    ImageIO.write(img, "png", File(dir, "v24-gl-diff-$i-$persp.png"))
                }
                assertTrue("Model $i persp $persp: siluet uyumu $score", score > 0.93)
            }
        }
        target.release()
        r.release()
    }

    @Test
    fun transformsDoNotReuploadMeshesAndColorsMatch() {
        val r = GlRenderer3(gl)
        assertTrue(r.init())
        val w = 200; val h = 200
        val target = OffscreenTarget(gl, w, h)
        val part = ModelEdit.newPart(PrimitiveKind.SPHERE, "p1", 0).copy(smooth = true, material = Material(color = 0xFFD05A3C.toInt(), kind = MaterialKind.PLASTIC))
        var m = Model3D("m", "küre", listOf(part), camera = Camera3(zoom = 1.3f))
        val view0 = View3.editor(m.camera, w, h)
        glRender(r, target, m, view0, GlOptions(selected = setOf("p1"), primary = "p1"))
        assertTrue("ilk karede yükleme", r.stats.uploads > 0)
        // Taşı, döndür, ölçekle: yalnız matris değişir
        repeat(12) { k ->
            m = m.replacePart(m.parts[0].copy(position = V3(k * 0.02f, 0f, 0f), rotation = V3(0f, k * 7f, k * 3f), scale = V3(1f + k * 0.03f, 1f, 1f)))
            r.render(m, View3.editor(m.camera, w, h), GlOptions(selected = setOf("p1"), primary = "p1"), target.fbo)
            assertEquals("kare $k: köşe tamponu yeniden yüklenmemeli", 0, r.stats.uploads)
        }
        assertTrue("az çizim çağrısı: ${r.stats.drawCalls}", r.stats.drawCalls < 20)

        // Renk: yazılım çizicisiyle aynı ışık modeli (orta piksel)
        val plain = Model3D("m", "küre", listOf(part), camera = Camera3(zoom = 1.3f, persp = 0f))
        val view = View3.editor(plain.camera, w, h)
        val glPx = glRender(r, target, plain, view, GlOptions(ground = false, shadow = false, edges = false, clearColor = 0xFFFFFFFF.toInt()))
        val sw = Renderer.render(plain, view, RenderOptions(supersample = 1, ground = false, shadow = false, background = 0xFFFFFFFF.toInt())).color
        var diff = 0.0; var n = 0
        for (y in 60 until 140 step 4) for (x in 60 until 140 step 4) {
            val a = glPx[y * w + x]; val b = sw[y * w + x]
            for (sh in intArrayOf(16, 8, 0)) diff += abs((a shr sh and 0xFF) - (b shr sh and 0xFF))
            n += 3
        }
        previewDir()?.let { dir ->
            val img = BufferedImage(w * 2, h, BufferedImage.TYPE_INT_RGB)
            img.setRGB(0, 0, w, h, glPx, 0, w); img.setRGB(w, 0, w, h, sw, 0, w)
            ImageIO.write(img, "png", File(dir, "v24-gl-vs-software.png"))
        }
        assertTrue("ortalama renk farkı ${diff / n}", diff / n < 12.0)
        target.release()
        r.release()
    }

    /** Hazır şekillerin ekran kartıyla çizimi (önizleme kâğıdı; gölge, kenar, ızgara, doku ve saydamlıkla). */
    @Test
    fun catalogSheetRendersWithAllEffects() {
        val r = GlRenderer3(gl)
        assertTrue(r.init())
        val cell = 200
        val target = OffscreenTarget(gl, cell, cell)
        val items = Catalog3D.all
        val dir = previewDir()
        val cols = 8
        val sheet = if (dir != null) BufferedImage(cols * cell, (items.size + cols - 1) / cols * cell, BufferedImage.TYPE_INT_RGB) else null
        var micros = 0L
        for ((i, item) in items.withIndex()) {
            val m = item.build()
            val view = View3.fit(m, m.camera, cell, cell, 0.08f)
            val px = glRender(r, target, m, view, GlOptions(textures = ::tex, clearColor = 0xFFFFFFFF.toInt(), lineScale = 1f))
            micros += r.stats.cpuMicros
            val drawn = px.count { it != -1 }
            assertTrue("${item.id}: çizim boş", drawn > cell * cell / 40)
            sheet?.setRGB((i % cols) * cell, (i / cols) * cell, cell, cell, px, 0, cell)
        }
        println("GL katalog: ${items.size} model, ortalama CPU ${micros / items.size} µs/kare")
        if (dir != null && sheet != null) ImageIO.write(sheet, "png", File(dir, "v24-gl-catalog.png"))
        target.release()
        r.release()
    }

    /** Düzenleyici görünümü: seçili parça (vurgu kenarları), Kenar aracında tüm kenarlar, seçili yüzler; dört kalite. */
    @Test
    fun editorSelectionStates() {
        val r = GlRenderer3(gl)
        assertTrue(r.init())
        val w = 360; val h = 300
        val target = OffscreenTarget(gl, w, h)
        val m = Samples3D.all[1]
        val view = View3.editor(m.camera.copy(zoom = 1.1f), w, h)
        val first = m.parts[0].id
        val states = listOf(
            GlOptions(textures = ::tex, clearColor = 0xFFEEF1F6.toInt()),
            GlOptions(textures = ::tex, clearColor = 0xFFEEF1F6.toInt(), selected = setOf(first), primary = first),
            GlOptions(textures = ::tex, clearColor = 0xFFEEF1F6.toInt(), selected = setOf(first), primary = first, wireframe = true, lineScale = 1.4f),
            GlOptions(textures = ::tex, clearColor = 0xFFEEF1F6.toInt(), selected = setOf(first), primary = first, selectedFaces = setOf(0, 2)),
        )
        val sheet = BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_RGB)
        states.forEachIndexed { i, o ->
            val px = glRender(r, target, m, view, o)
            sheet.setRGB((i % 2) * w, (i / 2) * h, w, h, px, 0, w)
        }
        previewDir()?.let { ImageIO.write(sheet, "png", File(it, "v24-gl-editor-states.png")) }
        target.release()
        r.release()
    }

    @Test
    fun contextLossRecoversAndReleaseFreesObjects() {
        val r = GlRenderer3(gl)
        assertTrue(r.init())
        val target = OffscreenTarget(gl, 64, 64)
        val m = Catalog3D.byId("geo.cube_diag")!!.build()
        r.render(m, View3.fit(m, m.camera, 64, 64), GlOptions(), target.fbo)
        r.release()
        assertTrue(!r.ready)
        // Yeniden başlatma
        r.render(m, View3.fit(m, m.camera, 64, 64), GlOptions(), target.fbo)
        assertTrue(r.ready)
        assertEquals(GL.NO_ERROR, gl.getError())
        target.release()
        r.release()
    }
}
