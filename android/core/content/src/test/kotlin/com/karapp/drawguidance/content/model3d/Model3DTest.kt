package com.karapp.drawguidance.content.model3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/** v23 3B motoru: cisimlerin ağları, düzenleme işlemleri, kayıt biçimi, dokular ve yazılım çizici. */
class Model3DTest {

    private fun previewDir(): File? = System.getProperty("previewDir")?.let { File(it).apply { mkdirs() } }

    private fun save(frame: Frame, name: String) {
        val dir = previewDir() ?: return
        val img = BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, frame.width, frame.height, frame.color, 0, frame.width)
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    private fun textures(): (String) -> Texture? {
        val cache = HashMap<String, Texture>()
        return { id -> cache.getOrPut(id) { TextureCatalog.byId(id)!!.generate(128) } }
    }

    @Test
    fun primitivesAreClosedAndFaceOutward() {
        PrimitiveKind.entries.forEach { kind ->
            val mesh = Primitives.build(Primitives.defaultSpec(kind))
            assertTrue("$kind boş", mesh.vertexCount > 0 && mesh.faceCount > 0)
            val b = mesh.bounds
            assertTrue("$kind birim kutuda: $b", b.size.x <= 1.001f && b.size.y <= 1.001f && b.size.z <= 1.001f)
            if (kind != PrimitiveKind.PLANE) assertTrue("$kind kapalı değil", mesh.closed)
            // Hacim (diverjans teoremi) artı: yüzler dışarı bakıyor
            var vol = 0.0
            for (f in mesh.faces) for (k in 1 until f.size - 1) {
                val a = mesh.vertex(f[0]); val c = mesh.vertex(f[k]); val d = mesh.vertex(f[k + 1])
                vol += (a dot (c cross d)) / 6.0
            }
            if (kind != PrimitiveKind.PLANE) assertTrue("$kind hacim $vol", vol > 0.01)
            assertTrue("$kind düzlemsel olmayan yüz", mesh.maxNonPlanarity() < 1e-3f)
        }
        // Düzlemin normali yukarı
        assertTrue(Primitives.plane(2).faceNormal(0).y > 0.99f)
    }

    @Test
    fun extrudeInsetMergeAndDelete() {
        val cube = ModelEdit.newPart(PrimitiveKind.CUBE, "c", 0)
        val top = (0 until cube.mesh.faceCount).first { cube.mesh.faceNormal(it).y > 0.9f }
        val ex = ModelEdit.extrudeFace(cube, top, 0.5f)
        assertEquals(10, ex.mesh.faceCount)
        assertTrue(ex.mesh.closed)
        assertEquals(1.5f, ModelEdit.worldBounds(ex).size.y, 1e-4f)
        assertEquals(null, ex.primitive)
        // Ölçekli parçada uzatma dünya biriminde
        val tall = cube.copy(scale = V3(1f, 2f, 1f))
        assertEquals(2.5f, ModelEdit.worldBounds(ModelEdit.extrudeFace(tall, top, 0.5f)).size.y, 1e-4f)
        // İçe kaydırma: 4 yeni halka yüzü, iç yüz aynı sırada
        val inset = ModelEdit.insetFace(cube, top, 0.3f)
        assertEquals(10, inset.mesh.faceCount)
        assertTrue(inset.mesh.faceNormal(top).y > 0.99f)
        // Yüz boyama uzatmada yan yüzlere geçer
        val painted = ModelEdit.paintFace(cube, top, Material(0xFFFF0000.toInt()))
        val ex2 = ModelEdit.extrudeFace(painted, top, 0.2f)
        assertEquals(5, ex2.faceMaterials.size)
        // Yüz silme sıraları kaydırır
        val del = ModelEdit.deleteFace(painted, 0)
        assertEquals(5, del.mesh.faceCount)
        assertEquals(1, del.faceMaterials.size)
        assertTrue(del.faceMaterials.keys.single() == top - 1 || top == 0)
        // Birleştirme dönüşümleri işler
        val s = ModelEdit.newPart(PrimitiveKind.SPHERE, "s", 1, V3(2f, 0f, 0f))
        val merged = ModelEdit.merge(listOf(cube, s), "m", "Birleşik")!!
        val mb = ModelEdit.worldBounds(merged)
        assertEquals(-0.5f, mb.min.x, 1e-3f); assertEquals(2.5f, mb.max.x, 1e-3f); assertEquals(0f, mb.min.y, 1e-3f)
        assertTrue(merged.faceMaterials.isNotEmpty())
    }

    @Test
    fun moveVerticesUsesWorldSpace() {
        val p = ModelEdit.newPart(PrimitiveKind.CUBE, "c", 0).copy(rotation = V3(0f, 90f, 0f), scale = V3(2f, 1f, 1f))
        val v = 0
        val t = Transform3.of(p)
        val before = t.apply(p.mesh.vertex(v))
        val moved = ModelEdit.moveVertices(p, listOf(v), V3(0.3f, 0.1f, -0.2f))
        val after = Transform3.of(moved).apply(moved.mesh.vertex(v))
        assertEquals(0.3f, after.x - before.x, 1e-4f)
        assertEquals(0.1f, after.y - before.y, 1e-4f)
        assertEquals(-0.2f, after.z - before.z, 1e-4f)
    }

    @Test
    fun codecRoundTrip() {
        Samples3D.all.forEach { m ->
            val withStroke = m.copy(strokes = listOf(Stroke3("s1", floatArrayOf(0f, 0f, 0f, 1f, 1f, 0.5f), 0xFF112233.toInt(), 0.03f)))
            val bytes = Model3DCodec.encode(withStroke)
            val back = Model3DCodec.decode(bytes)
            assertEquals(withStroke, back)
            assertTrue("${m.name} ${bytes.size} bayt", bytes.size < 40_000)
        }
    }

    @Test
    fun texturesTile() {
        TextureCatalog.all.forEach { def ->
            val t = def.generate(64)
            // Kenarlar komşu kenarlarla benzer: sol ve sağ sütun, üst ve alt satır arası fark sınırlı
            fun diff(a: Int, b: Int) = abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) + abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))
            var edge = 0.0; var inner = 0.0
            for (y in 0 until 64) { edge += diff(t.px[y * 64], t.px[y * 64 + 63]); inner += diff(t.px[y * 64 + 31], t.px[y * 64 + 32]) }
            for (x in 0 until 64) { edge += diff(t.px[x], t.px[63 * 64 + x]); inner += diff(t.px[31 * 64 + x], t.px[32 * 64 + x]) }
            assertTrue("${def.id} dikiş: kenar $edge iç $inner", edge <= inner * 3.0 + 64 * 2 * 40)
        }
        assertEquals(TextureCatalog.all.size, TextureCatalog.all.map { it.id }.toSet().size)
    }

    @Test
    fun renderCoverageDepthAndPicking() {
        val cube = ModelEdit.newPart(PrimitiveKind.CUBE, "c", 0)
        val model = Model3D("m", "Küp", listOf(cube), scene = SceneSettings(ground = false, shadow = false))
        val view = View3.editor(model.camera, 200, 200)
        val frame = Renderer.render(model, view, RenderOptions(supersample = 1))
        val covered = frame.part.count { it == 1 }
        assertTrue("kaplama $covered", covered in 4000..20000)
        // Ortadaki piksel küpün
        val center = frame.pick(100f, 100f)
        assertNotNull(center)
        assertEquals(1, center!!.first)
        // Kameraya bakan 3 yüz görünür
        val faces = frame.part.indices.filter { frame.part[it] == 1 }.map { frame.face[it] }.toSet()
        assertEquals(3, faces.size)
        // Sığdırma: model ekranı doldurur
        val fit = Renderer.render(model, View3.fit(model, model.camera, 200, 200, 0.05f), RenderOptions(supersample = 1))
        assertTrue(fit.part.count { it == 1 } > covered)
        // Köşe seçimi: ekrandaki en yakın görünür köşe
        val sv = ModelEdit.screenVertices(cube, frame, 0)
        val visible = (0 until cube.mesh.vertexCount).filter { sv[3 * it + 2] > 0f }
        assertTrue("görünür köşe ${visible.size}", visible.size >= 6)
        val v0 = visible.first()
        assertEquals(v0, ModelEdit.pickVertex(cube, frame, sv[3 * v0] + 2f, sv[3 * v0 + 1] - 1f, 12f, 0))
    }

    @Test
    fun renderSamplesAndPerformance() {
        val tex = textures()
        Samples3D.all.forEach { m ->
            val view = View3.fit(m, m.camera, 320, 320)
            val frame = Renderer.render(m, view, RenderOptions(textures = tex, supersample = 2))
            assertTrue(m.name, frame.color.count { it ushr 24 > 0 } > 320 * 320 / 10)
            save(frame, "v23-3d-${m.id}")
        }
        // Perspektif ve saydamlık: hata vermeden çizilir
        val house = Samples3D.all.first { it.id == "sample-house" }
        val p = Renderer.render(house.copy(camera = house.camera.copy(persp = 1f)), View3.fit(house, house.camera.copy(persp = 1f), 300, 300), RenderOptions(textures = tex))
        save(p, "v23-3d-house-persp")
        // Hız: 640×640, örnekleme katı 1 (sürüklerken) — kardan adam
        val snow = Samples3D.all.first { it.id == "sample-snowman" }
        val view = View3.fit(snow, snow.camera, 640, 640)
        repeat(3) { Renderer.render(snow, view, RenderOptions(textures = tex, supersample = 1, bilinear = false)) }
        val t0 = System.nanoTime()
        repeat(5) { Renderer.render(snow, view, RenderOptions(textures = tex, supersample = 1, bilinear = false)) }
        val ms = (System.nanoTime() - t0) / 5e6
        println("3B çizim 640×640: $ms ms")
        assertTrue("$ms ms", ms < 120.0)
    }

    @Test
    fun planeHitAndAxisDrag() {
        val cam = Camera3(yaw = 0f, pitch = 0f, persp = 0f, zoom = 1f, target = V3.ZERO)
        val view = View3.editor(cam, 400, 400)
        // Önden bakışta ekran merkezinin ön düzlemle kesişimi hedef
        val hit = ModelEdit.hitPlane(view, 200f, 200f, V3.ZERO, V3(0f, 0f, 1f))!!
        assertEquals(0f, hit.x, 1e-3f); assertEquals(0f, hit.y, 1e-3f)
        // Üst düzlem önden kenar üstü görünür: kesişim yok
        assertEquals(null, ModelEdit.hitPlane(view, 200f, 200f, V3.ZERO, V3(0f, 1f, 0f)))
        // Sağa sürükleme X ekseninde artı
        val d = ModelEdit.dragDelta(view, 50f, 0f, 0)
        assertTrue(d.x > 0f && d.y == 0f && d.z == 0f)
        val k = view.scale
        assertEquals(50f / k, d.x, 1e-4f)
        // Y ekseninde yukarı sürükleme (ekranda eksi y) artı
        assertTrue(ModelEdit.dragDelta(view, 0f, -40f, 1).y > 0f)
    }
}
