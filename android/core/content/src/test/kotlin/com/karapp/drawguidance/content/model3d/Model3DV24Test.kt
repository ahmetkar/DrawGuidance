package com.karapp.drawguidance.content.model3d

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max

/**
 * v24 3B: üçgenleme, kenar işlemleri (nokta ekle, eğ, ger, it, yuvarlat), ışınla seçim, glTF/GLB yazma-okuma ve içe
 * aktarma, ayrıntı düşürme, hazır şekiller (önizleme sayfası).
 */
class Model3DV24Test {

    private fun previewDir(): File? = System.getProperty("previewDir")?.let { File(it).apply { mkdirs() } }

    private fun area(mesh: Mesh): Double {
        var s = 0.0
        for (f in 0 until mesh.faceCount) {
            val face = mesh.faces[f]; val t = mesh.triangles[f]
            var k = 0
            while (k + 2 < t.size) {
                val a = mesh.vertex(face[t[k]]); val b = mesh.vertex(face[t[k + 1]]); val c = mesh.vertex(face[t[k + 2]])
                s += ((b - a) cross (c - a)).length / 2.0
                k += 3
            }
        }
        return s
    }

    private fun volume(mesh: Mesh): Double {
        var v = 0.0
        for (f in 0 until mesh.faceCount) {
            val face = mesh.faces[f]; val t = mesh.triangles[f]
            var k = 0
            while (k + 2 < t.size) {
                val a = mesh.vertex(face[t[k]]); val b = mesh.vertex(face[t[k + 1]]); val c = mesh.vertex(face[t[k + 2]])
                v += (a dot (b cross c)) / 6.0
                k += 3
            }
        }
        return v
    }

    @Test
    fun concaveFacesAreTriangulatedByEarClipping() {
        // L biçimli yüz (alan 3)
        val p = floatArrayOf(0f, 0f, 0f, 2f, 0f, 0f, 2f, 1f, 0f, 1f, 1f, 0f, 1f, 2f, 0f, 0f, 2f, 0f)
        val m = Mesh(p, arrayOf(intArrayOf(0, 1, 2, 3, 4, 5)))
        assertEquals(4, m.triangles[0].size / 3)
        assertEquals(3.0, area(m), 1e-5)
        // Küpün alanı 6, hacmi 1
        val cube = Primitives.cube()
        assertEquals(6.0, area(cube), 1e-5)
        assertEquals(1.0, volume(cube), 1e-5)
    }

    @Test
    fun edgeOperationsKeepTheSolidClosed() {
        val cube = Primitives.cube()
        val e = MeshOps.edgeList(cube).first()
        // Nokta ekle: iki yüz birer köşe kazanır, alan ve hacim değişmez
        val (split, v) = MeshOps.splitEdge(cube, e, 0.5f)
        assertEquals(9, split.vertexCount)
        assertEquals(8, v)
        assertTrue(split.closed)
        assertEquals(2, split.faces.count { it.size == 5 })
        assertEquals(1.0, volume(split), 1e-5)
        // Eğ: kenar 4 parçaya bölünür, dışa doğru bombe hacmi artırır
        val bent = MeshOps.bend(cube, listOf(e), 0.5f)
        assertEquals(11, bent.vertexCount)
        assertTrue(bent.closed)
        assertTrue("eğilince hacim artar: ${volume(bent)}", volume(bent) > 1.0)
        val inward = MeshOps.bend(cube, listOf(e), -0.5f)
        assertTrue(volume(inward) < 1.0)
        // Ger: kenar uzar
        val st = MeshOps.stretch(cube, listOf(e), 1.5f)
        assertEquals(1.5f, (st.vertex(e.a) - st.vertex(e.b)).length, 1e-4f)
        // İt: kenar dışarı
        val pushed = MeshOps.push(cube, listOf(e), 0.2f)
        assertTrue(volume(pushed) > 1.0)
        // Yuvarlat: 4 dilimli şerit, ağ kapalı, uçlarda kapak yüzlerine kavis noktaları
        val rounded = MeshOps.round(cube, e, 0.2f, 4)
        assertNotNull(rounded)
        rounded!!
        assertTrue(rounded.closed)
        assertEquals(6 + 4, rounded.faceCount)
        assertEquals(8 - 2 + 10, rounded.vertexCount)
        val vol = volume(rounded)
        assertTrue("yuvarlanınca hacim biraz azalır: $vol", vol < 1.0 && vol > 0.97)
        // Üç yüzün buluşmadığı uçlarda yuvarlatma yapılmaz
        val sphere = Primitives.sphere(8, 4)
        val poleEdge = MeshOps.edgeList(sphere).first { sphere.vertexFaces[it.a].size > 3 }
        assertNull(MeshOps.round(sphere, poleEdge, 0.05f))
        // Açık (tek yüzlü) kenar düzlemde eğilir: yükseklik değişmez
        val plane = Primitives.plane(1)
        val pe = MeshOps.edgeList(plane).first()
        val bentPlane = MeshOps.bend(plane, listOf(pe), 0.5f)
        assertTrue((0 until bentPlane.vertexCount).all { abs(bentPlane.vertex(it).y) < 1e-5f })
        assertTrue(area(bentPlane) > 1.0)
    }

    @Test
    fun rayPickingFindsPartsFacesVerticesAndEdges() {
        val a = ModelEdit.newPart(PrimitiveKind.CUBE, "a", 0)
        val b = ModelEdit.newPart(PrimitiveKind.SPHERE, "b", 1, V3(2f, 0f, 0f))
        val model = Model3D("m", "m", listOf(a, b), camera = Camera3(yaw = 0f, pitch = 0f, persp = 0f, target = V3(1f, 0.5f, 0f)))
        val view = View3.editor(model.camera, 800, 600)
        val s = view.project(V3(0f, 0.5f, 0.5f))
        val hit = Pick3.pick(model, view, s[0], s[1])
        assertNotNull(hit)
        assertEquals(0, hit!!.part)
        assertTrue(a.mesh.faceNormal(hit.face).z > 0.9f)
        val s2 = view.project(V3(2f, 0.5f, 0.5f))
        assertEquals(1, Pick3.pick(model, view, s2[0], s2[1])?.part)
        // Köşe: önden bakınca ön köşe görünür, arkadaki gizli
        val corner = view.project(V3(0.5f, 1f, 0.5f))
        val v = Pick3.vertex(model, view, 0, corner[0] + 3f, corner[1] - 2f, 20f)
        assertNotNull(v)
        assertTrue(v!!.visible)
        assertTrue(a.mesh.vertex(v.index).z > 0f)
        // Kenar: üst ön kenarın ortası
        val mid = view.project(V3(0f, 1f, 0.5f))
        val e = Pick3.edge(model, view, 0, mid[0], mid[1] + 2f, 15f)
        assertNotNull(e)
        val pa = a.mesh.vertex(e!!.first.a); val pb = a.mesh.vertex(e.first.b)
        assertTrue(pa.y > 0f && pb.y > 0f && pa.z > 0f && pb.z > 0f)
        // Boş yer
        assertNull(Pick3.pick(model, view, 5f, 5f))
    }

    private fun sampleModel(): Model3D {
        val cube = ModelEdit.newPart(PrimitiveKind.CUBE, "c", 0).copy(rotation = V3(10f, 20f, 30f), scale = V3(1f, 2f, 0.5f))
        val painted = ModelEdit.paintFace(cube, 2, Material(0xFF00FF00.toInt(), texture = "wood.oak", textureScale = 0.5f, kind = MaterialKind.METAL, shine = 0.8f))
        val sphere = ModelEdit.newPart(PrimitiveKind.SPHERE, "s", 1, V3(2f, 0f, 0f)).copy(material = Material(0x80FF0000.toInt(), opacity = 0.5f, kind = MaterialKind.GLASS))
        val edited = ModelEdit.extrudeFace(ModelEdit.newPart(PrimitiveKind.CYLINDER, "e", 2, V3(-2f, 0f, 0f)), 0, 0.3f).copy(visible = false)
        val tri = Mesh(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f), arrayOf(intArrayOf(0, 1, 2)), arrayOf(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f)))
        val withUv = Part("u", "Üçgen", tri, null, uv = UvMode.MESH, material = Material(texture = "imp.x"))
        return Model3D(
            "id-1", "Örnek ğüşıöç", listOf(painted, sphere, edited, withUv),
            listOf(Stroke3("s1", floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f, 2f, 0f, 1f), 0xFF123456.toInt(), 0.03f)),
            SceneSettings(Light(10f, 40f, 1.2f, 0.3f, 0xFFFFEEDD.toInt()), shadow = false, edges = true, edgeColor = 0xFF111111.toInt(), background = 0xFF222222.toInt(), ground = false),
            Camera3(12f, 34f, 0.2f, 1.5f, V3(1f, 2f, 3f)),
        )
    }

    @Test
    fun glbRoundTripIsLosslessAndStructurallyValid() {
        val model = sampleModel()
        val glb = Gltf3.write(model)
        assertTrue(Gltf3.isGlb(glb))
        val back = Gltf3.read(glb)!!
        assertEquals(model.id, back.id)
        assertEquals(model.name, back.name)
        assertEquals(model.scene, back.scene)
        assertEquals(model.camera, back.camera)
        assertEquals(model.strokes, back.strokes)
        assertEquals(model.parts.size, back.parts.size)
        model.parts.zip(back.parts).forEach { (a, b) ->
            assertEquals(a.copy(mesh = Mesh(FloatArray(0), emptyArray())), b.copy(mesh = Mesh(FloatArray(0), emptyArray())))
            assertEquals(a.mesh, b.mesh)
        }
        assertEquals(model, back)
        assertEquals(model, Model3DStore.decode(Model3DStore.encode(model)))
        // Eski "D3" biçimi okunmaya devam eder
        val legacyModel = model.copy(parts = model.parts.take(3))
        assertEquals(legacyModel, Model3DStore.decode(Model3DCodec.encode(legacyModel)))
        // Yapı: başlık, parçalar, erişimciler tampon içinde
        val bb = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(glb.size, bb.getInt(8))
        val jsonLen = bb.getInt(12)
        val root = Json.parseToJsonElement(String(glb, 20, jsonLen, Charsets.UTF_8)).jsonObject
        val binLen = bb.getInt(20 + jsonLen)
        val views = root["bufferViews"]!!.jsonArray
        views.forEach { v ->
            val o = v.jsonObject
            val off = o["byteOffset"]!!.jsonPrimitive.int; val len = o["byteLength"]!!.jsonPrimitive.int
            assertTrue(off % 4 == 0 && off + len <= binLen)
        }
        root["accessors"]!!.jsonArray.forEach { a ->
            val o = a.jsonObject
            val view = views[o["bufferView"]!!.jsonPrimitive.int].jsonObject
            val comps = when (o["type"]!!.jsonPrimitive.content) { "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; else -> 4 }
            val size = when (o["componentType"]!!.jsonPrimitive.int) { 5126, 5125 -> 4; 5123 -> 2; else -> 1 }
            assertTrue(o["count"]!!.jsonPrimitive.int * comps * size <= view["byteLength"]!!.jsonPrimitive.int)
        }
        assertEquals("2.0", root["asset"]!!.jsonObject["version"]!!.jsonPrimitive.content)
        previewDir()?.let { File(it, "v24-sample.glb").writeBytes(Gltf3.write(model, GltfWriteOptions(embedTextures = true, textures = { id -> TextureCatalog.byId(id)?.generate(64) }))) }
    }

    /** Uygulamaya özgü "extras" olmadan (başka programın dosyası gibi) GLB. */
    private fun foreign(model: Model3D): ByteArray {
        val glb = Gltf3.write(model)
        val bb = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN)
        val jsonLen = bb.getInt(12)
        val root = Json.parseToJsonElement(String(glb, 20, jsonLen, Charsets.UTF_8)).jsonObject
        val asset = JsonObject(root["asset"]!!.jsonObject.filterKeys { it != "extras" })
        val json = JsonObject(root + ("asset" to asset)).toString().toByteArray()
        val pad = (4 - json.size % 4) % 4
        val bin = glb.copyOfRange(20 + jsonLen + 8, glb.size)
        val total = 12 + 8 + json.size + pad + 8 + bin.size
        val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67); out.putInt(2); out.putInt(total)
        out.putInt(json.size + pad); out.putInt(0x4E4F534A); out.put(json); repeat(pad) { out.put(' '.code.toByte()) }
        out.putInt(bin.size); out.putInt(0x004E4942); out.put(bin)
        return out.array()
    }

    @Test
    fun foreignGlbIsImportedSimplifiedAndNormalized() {
        // Ayrıntılı küre ve küp (başka programın dosyası gibi)
        val big = Part("b", "Küre", Primitives.sphere(64, 32), null, position = V3(0f, 50f, 0f), scale = V3(100f, 100f, 100f), smooth = true)
        val box = Part("c", "Kutu", Primitives.cube(), null, position = V3(120f, 25f, 0f), scale = V3(50f, 50f, 50f))
        val src = Model3D("x", "x", listOf(big, box))
        val data = foreign(src)
        assertNull(Gltf3.read(data))
        val result = Gltf3.import(data, "Ders modeli", maxTriangles = 800, decodeImage = { _, _ -> null })
        assertTrue("kaynak ${result.sourceTriangles}", result.sourceTriangles > 3500)
        assertTrue("sonuç ${result.triangles}", result.triangles <= 820)
        val m = result.model
        assertEquals(2, m.parts.size)
        val b = ModelEdit.modelBounds(m)!!
        assertEquals(1.6f, max(b.size.x, max(b.size.y, b.size.z)), 0.02f)
        assertEquals(0f, b.min.y, 1e-3f)
        // Küre hâlâ küre: köşeler merkezden yaklaşık aynı uzaklıkta
        val sphere = m.parts.first { it.mesh.triangleCount > 100 }
        val t = Transform3.of(sphere)
        val pts = (0 until sphere.mesh.vertexCount).map { t.apply(sphere.mesh.vertex(it)) }
        val c = pts.reduce { a, p -> a + p } * (1f / pts.size)
        val radii = pts.map { (it - c).length }
        assertTrue("küre yarıçapı tutarlı: ${radii.min()}..${radii.max()}", radii.max() / radii.min() < 1.25f)
        // Kendi dosyamız kayıpsız içe aktarılır (yalnız kimlik/ad değişir)
        val own = Gltf3.import(Gltf3.write(sampleModel()), "Kopya", 100, { _, _ -> null }, newId = { "yeni" })
        assertEquals("yeni", own.model.id)
        assertEquals(sampleModel().parts, own.model.parts)
    }

    @Test
    fun simplifierKeepsShapeAndRespectsTarget() {
        val s = Primitives.sphere(48, 24)
        val tris = ArrayList<Int>()
        for (f in 0 until s.faceCount) { val face = s.faces[f]; s.triangles[f].forEach { tris += face[it] } }
        val tm = TriMesh(s.pos, tris.toIntArray(), null)
        val t0 = System.nanoTime()
        val out = MeshSimplifier.simplify(tm, 300)
        val ms = (System.nanoTime() - t0) / 1e6
        println("küre ${tm.triangleCount} → ${out.triangleCount} üçgen, ${"%.1f".format(ms)} ms")
        assertTrue(out.triangleCount in 200..300)
        for (i in 0 until out.vertexCount) {
            val r = V3(out.pos[3 * i], out.pos[3 * i + 1], out.pos[3 * i + 2]).length
            assertTrue("yüzeyde kalır: $r", abs(r - 0.5f) < 0.05f)
        }
        // Büyük ağ (kümeleme + QEM) makul sürede
        val bigSphere = Primitives.sphere(400, 200)
        val bt = ArrayList<Int>()
        for (f in 0 until bigSphere.faceCount) { val face = bigSphere.faces[f]; bigSphere.triangles[f].forEach { bt += face[it] } }
        val t1 = System.nanoTime()
        val small = MeshSimplifier.simplify(TriMesh(bigSphere.pos, bt.toIntArray(), null), 2000)
        val ms2 = (System.nanoTime() - t1) / 1e6
        println("büyük küre ${bt.size / 3} → ${small.triangleCount} üçgen, ${"%.0f".format(ms2)} ms")
        assertTrue(small.triangleCount <= 2000)
        assertTrue(ms2 < 15_000)
    }

    @Test
    fun catalogModelsBuildAreLightAndRoundTrip() {
        val textures = HashMap<String, Texture>()
        val frames = ArrayList<Pair<String, Frame>>()
        var worst = 0
        Catalog3D.all.forEach { item ->
            val m = item.build()
            assertTrue("${item.id} boş", m.parts.isNotEmpty())
            assertTrue("${item.id} kategori", item.category in Catalog3D.categories)
            worst = max(worst, m.triangleCount)
            assertTrue("${item.id} çok ayrıntılı: ${m.triangleCount}", m.triangleCount < 12_000)
            m.parts.forEach { p -> assertTrue("${item.id}/${p.name} boş ağ", p.mesh.faceCount > 0) }
            assertEquals(item.id, m, Gltf3.read(Gltf3.write(m)))
            if (previewDir() != null) {
                val view = View3.fit(m, m.camera, 200, 200, 0.08f)
                frames += item.name to Renderer.render(m, view, RenderOptions(textures = { id -> textures.getOrPut(id) { TextureCatalog.byId(id)!!.generate(128) } }, supersample = 2, ground = false, background = 0xFFFFFFFF.toInt()))
            }
        }
        println("hazır şekil: ${Catalog3D.all.size}, en çok üçgen $worst")
        assertTrue(Catalog3D.all.size >= 50)
        assertEquals(Catalog3D.all.size, Catalog3D.all.map { it.id }.toSet().size)
        val dir = previewDir() ?: return
        val cols = 8
        val rows = (frames.size + cols - 1) / cols
        val sheet = BufferedImage(cols * 200, rows * 216, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.color = java.awt.Color.WHITE; g.fillRect(0, 0, sheet.width, sheet.height)
        g.color = java.awt.Color.DARK_GRAY
        g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12)
        frames.forEachIndexed { i, (name, f) ->
            val img = BufferedImage(f.width, f.height, BufferedImage.TYPE_INT_ARGB)
            img.setRGB(0, 0, f.width, f.height, f.color, 0, f.width)
            val x = (i % cols) * 200; val y = (i / cols) * 216
            g.drawImage(img, x, y, null)
            g.drawString(name, x + 6, y + 211)
        }
        g.dispose()
        ImageIO.write(sheet, "png", File(dir, "v24-catalog.png"))
    }
}
