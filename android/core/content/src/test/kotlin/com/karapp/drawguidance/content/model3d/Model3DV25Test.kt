package com.karapp.drawguidance.content.model3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * v25 ağ yapısı işlemleri (nokta silme, iki noktayı birleştirme, halkadan kesme, yüzey bölgesi, bölge yükseltme), katı
 * cisim işlemleri (birleştir, çıkar, kesişim, delik, parçalama, ayırma) ve 3B fırça cisimleri: sonuçlar kapalı (dolu)
 * ağ ve hacimleri beklenen değerde.
 */
class Model3DV25Test {

    private fun previewDir(): File? = System.getProperty("previewDir")?.let { File(it).apply { mkdirs() } }

    /** İşaretli hacim (kapalı, dışa bakan ağda artı). */
    private fun volume(m: Mesh): Double {
        var v = 0.0
        val p = m.pos
        for (f in 0 until m.faceCount) {
            val face = m.faces[f]; val t = m.triangles[f]
            var k = 0
            while (k + 2 < t.size) {
                val a = face[t[k]] * 3; val b = face[t[k + 1]] * 3; val c = face[t[k + 2]] * 3
                v += (p[a] * (p[b + 1] * p[c + 2] - p[b + 2] * p[c + 1]) - p[a + 1] * (p[b] * p[c + 2] - p[b + 2] * p[c]) + p[a + 2] * (p[b] * p[c + 1] - p[b + 1] * p[c])).toDouble()
                k += 3
            }
        }
        return v / 6.0
    }

    private fun worldVolume(part: Part): Double {
        val t = Transform3.of(part)
        return volume(part.mesh.mapPositions { t.apply(it) })
    }

    private fun cubePart(id: String, at: V3 = V3.ZERO, size: Float = 1f) =
        Part(id, id, Primitives.cube(), null, position = at, scale = V3(size, size, size))

    @Test
    fun deleteVertexKeepsSolidClosed() {
        val cube = Primitives.cube()
        val r = MeshTopo.deleteVertex(cube, 0)!!
        assertTrue("köşe silinince kapalı", r.mesh.closed)
        assertEquals(7, r.mesh.vertexCount)
        assertEquals(7, r.mesh.faceCount)
        assertEquals(1.0 - 1.0 / 6.0, volume(r.mesh), 1e-4)
        // Kenara eklenen nokta silinince iz kalmaz
        val (split, v) = MeshOps.splitEdge(cube, EdgeRef.of(0, 1), 0.5f)
        val back = MeshTopo.deleteVertex(split, v)!!
        assertTrue(back.mesh.closed)
        assertEquals(8, back.mesh.vertexCount)
        assertEquals(6, back.mesh.faceCount)
        assertEquals(1.0, volume(back.mesh), 1e-5)
    }

    @Test
    fun connectAndCutLoopMakesTwoClosedHalves() {
        var m = Primitives.cube()
        // Küpün dört dikey kenarının ortasına nokta, sonra yan yüzlerde birleştir: yatay halka
        val vertical = MeshOps.edgeList(m).filter { e -> abs(m.vertex(e.a).y - m.vertex(e.b).y) > 0.5f }
        assertEquals(4, vertical.size)
        val loopPoints = ArrayList<Int>()
        for (e in vertical) { val (nm, v) = MeshOps.splitEdge(m, e, 0.5f); m = nm; loopPoints += v }
        // Halka sırası: komşu yan yüzlerde ortak olanlar art arda
        val ordered = arrayListOf(loopPoints[0])
        val left = loopPoints.drop(1).toMutableList()
        while (left.isNotEmpty()) {
            val last = ordered.last()
            val next = left.first { MeshTopo.commonFace(m, last, it) >= 0 }
            ordered += next; left -= next
        }
        for (i in ordered.indices) {
            val a = ordered[i]; val b = ordered[(i + 1) % ordered.size]
            if (!MeshTopo.isEdge(m, a, b)) m = MeshTopo.connect(m, a, b)!!.mesh
        }
        assertEquals(10, m.faceCount)
        assertTrue(m.closed)
        val cut = MeshTopo.cutLoop(m, ordered)
        assertNotNull(cut)
        assertTrue("kalan kapalı", cut!!.rest.mesh.closed)
        assertTrue("kesilen kapalı", cut.piece.mesh.closed)
        assertEquals(0.5, volume(cut.rest.mesh), 1e-4)
        assertEquals(0.5, volume(cut.piece.mesh), 1e-4)
        // Halka olmayan seçim kesmez
        assertNull(MeshTopo.cutLoop(m, ordered.take(2)))
    }

    @Test
    fun surfaceRegionAndRegionExtrude() {
        val cyl = Primitives.build(PrimitiveSpec(PrimitiveKind.CYLINDER, 24, 1))
        val side = cyl.faces.indices.first { abs(cyl.faceNormal(it).y) < 0.1f }
        val region = MeshTopo.surfaceRegion(cyl, side)
        assertEquals("silindirin yanı", 24, region.size)
        val cube = Primitives.cube()
        val top = cube.faces.indices.first { cube.faceNormal(it).y > 0.9f }
        val r = MeshTopo.extrudeRegion(cube, setOf(top), 0.5f)
        assertTrue(r.mesh.closed)
        assertEquals(1.0f, r.mesh.bounds.max.y, 1e-5f)
        assertEquals(1.5, volume(r.mesh), 1e-4)
        // İki komşu yüz birlikte: iç kenarda duvar yok
        val front = cube.faces.indices.first { cube.faceNormal(it).z > 0.9f }
        val r2 = MeshTopo.extrudeRegion(cube, setOf(top, front), 0.2f)
        assertTrue(r2.mesh.closed)
        assertEquals(6 + 6, r2.mesh.faceCount)
    }

    @Test
    fun booleansHoleSliceAndSeparate() {
        val a = cubePart("a")
        val b = cubePart("b", V3(0.5f, 0.5f, 0.5f))
        val union = Solid3.boolean(listOf(a, b), Solid3.Op.UNION, "u", "u")!!
        val sub = Solid3.boolean(listOf(a, b), Solid3.Op.SUBTRACT, "s", "s")!!
        val inter = Solid3.boolean(listOf(a, b), Solid3.Op.INTERSECT, "i", "i")!!
        assertEquals(2.0 - 0.125, worldVolume(union), 1e-3)
        assertEquals(1.0 - 0.125, worldVolume(sub), 1e-3)
        assertEquals(0.125, worldVolume(inter), 1e-3)
        assertTrue("birleşim kapalı (T-kavşakları giderildi)", union.mesh.closed)
        assertTrue(sub.mesh.closed)
        assertNull("kesişmeyen parçalarda kesişim boş", Solid3.boolean(listOf(a, cubePart("c", V3(5f, 0f, 0f))), Solid3.Op.INTERSECT, "x", "x"))
        // Delik: üst yüzden boydan boya 0,2 yarıçaplı
        val holed = Solid3.hole(a, V3(0f, 0.5f, 0f), V3(0f, 1f, 0f), 0.2f, 32)!!
        val expected = 1.0 - 32 * 0.5 * 0.04 * sin(2 * PI / 32)
        assertEquals(expected, worldVolume(holed), 2e-3)
        assertTrue(holed.mesh.closed)
        assertEquals("delikte parça yerinde", a.position, holed.position)
        // Parçalama: küre ortadan
        val sphere = Part("s", "küre", Primitives.build(PrimitiveSpec(PrimitiveKind.SPHERE, 20, 10)), null, position = V3(0f, 1f, 0f))
        val (up, down) = Solid3.slice(sphere, V3(0f, 1f, 0f), V3(0f, 1f, 0f))!!
        val whole = worldVolume(sphere)
        assertEquals(whole, worldVolume(up) + worldVolume(down), 2e-3)
        assertEquals(whole / 2, worldVolume(up), 0.02)
        assertTrue(up.mesh.closed && down.mesh.closed)
        assertNull("düzlem dışarıda", Solid3.slice(sphere, V3(0f, 5f, 0f), V3(0f, 1f, 0f)))
        // Ayırma: iki kopuk küp
        val merged = ModelEdit.merge(listOf(a, cubePart("d", V3(3f, 0f, 0f))), "m", "m")!!
        var k = 0
        val parts = Solid3.separate(merged) { "n${k++}" }
        assertEquals(2, parts.size)
        assertEquals(1.0, worldVolume(parts[0]), 1e-4)
    }

    @Test
    fun brushShapesAreClosedSolids() {
        val n = V3(0f, 0f, 1f); val up = V3(0f, 1f, 0f)
        val wave = (0..60).map { i -> val t = i / 60f; V3(t * 2f - 1f, 0.3f * sin(t * 6f), 0f) }
        val circle = (0..80).map { i -> val a = i / 80.0 * 2 * PI; V3(0.6f * cos(a).toFloat(), 0.6f * sin(a).toFloat() + 0.6f, 0f) }
        val star = (0..10).map { i -> val a = i / 10.0 * 2 * PI; val r = if (i % 2 == 0) 0.7f else 0.3f; V3((r * cos(a)).toFloat(), 0f, (r * sin(a)).toFloat()) }
        val vase = listOf(V3(0f, 0f, 0f), V3(0.3f, 0.02f, 0f), V3(0.42f, 0.3f, 0f), V3(0.25f, 0.7f, 0f), V3(0.16f, 0.9f, 0f), V3(0.22f, 1.05f, 0f))
        val cases = listOf(
            Triple("tüp", BrushInput(wave, FloatArray(wave.size) { 0.5f + 0.5f * it / wave.size }, n, up), BrushShapeSpec(Brush3Kind.TUBE, 0.12f)),
            Triple("halka tüp", BrushInput(circle, null, n, up), BrushShapeSpec(Brush3Kind.TUBE, 0.1f)),
            Triple("şerit", BrushInput(wave, null, n, up), BrushShapeSpec(Brush3Kind.RIBBON, 0.2f)),
            Triple("duvar", BrushInput(wave, null, n, up), BrushShapeSpec(Brush3Kind.WALL, 0.08f, height = 0.4f)),
            Triple("prizma", BrushInput(star + star.first(), null, V3(0f, 1f, 0f), V3(0f, 0f, -1f)), BrushShapeSpec(Brush3Kind.WALL, 0.05f, height = 0.3f)),
            Triple("torna", BrushInput(vase, null, n, up), BrushShapeSpec(Brush3Kind.LATHE, 0.05f, sides = 12)),
            Triple("şişir", BrushInput(circle, null, n, up), BrushShapeSpec(Brush3Kind.INFLATE, 0.05f)),
        )
        val models = ArrayList<Pair<String, Model3D>>()
        for ((name, input, spec) in cases) {
            val mesh = BrushShapes.build(input, spec)
            assertNotNull(name, mesh)
            assertTrue("$name kapalı", mesh!!.closed)
            assertTrue("$name hacmi artı: ${volume(mesh)}", volume(mesh) > 1e-5)
            assertTrue("$name hafif: ${mesh.triangleCount}", mesh.triangleCount < 6000)
            val part = BrushShapes.toPart(mesh, "p", name, Material(color = 0xFF4F8BD6.toInt()))
            models += name to Model3D(name, name, listOf(part))
        }
        // Kapalı izden prizma: yıldızın alanı × yükseklik
        val prism = BrushShapes.build(BrushInput(star + star.first(), null, V3(0f, 1f, 0f), V3(0f, 0f, -1f)), BrushShapeSpec(Brush3Kind.WALL, 0.05f, height = 0.3f, smoothing = 0f))!!
        assertEquals(0.3f, prism.bounds.size.y, 1e-3f)
        val dir = previewDir() ?: return
        val cell = 180
        val sheet = BufferedImage(cell * models.size, cell, BufferedImage.TYPE_INT_RGB)
        models.forEachIndexed { i, (_, m) ->
            val f = Renderer.render(m, View3.fit(m, m.camera, cell, cell, 0.1f), RenderOptions(supersample = 2, ground = false, background = -1))
            sheet.setRGB(i * cell, 0, cell, cell, f.color, 0, cell)
        }
        ImageIO.write(sheet, "png", File(dir, "v25-brush-shapes.png"))
    }

    /** Görsel denetim: delik, birleşim, çıkarma ve parçalama sonuçları. */
    @Test
    fun solidsPreviewSheet() {
        val dir = previewDir() ?: return
        val a = cubePart("a").copy(material = Material(color = 0xFFE07A5F.toInt()))
        val cyl = Part("c", "c", Primitives.build(PrimitiveSpec(PrimitiveKind.CYLINDER, 24, 1)), null, position = V3(0.4f, 0.3f, 0.2f), scale = V3(0.7f, 1.4f, 0.7f), material = Material(color = 0xFF81B29A.toInt()))
        val results = listOf(
            Solid3.hole(a, V3(0f, 0.5f, 0f), V3(0f, 1f, 0f), 0.25f, 24)!!,
            Solid3.boolean(listOf(a, cyl), Solid3.Op.UNION, "u", "u")!!,
            Solid3.boolean(listOf(a, cyl), Solid3.Op.SUBTRACT, "s", "s")!!,
            Solid3.boolean(listOf(a, cyl), Solid3.Op.INTERSECT, "i", "i")!!,
        )
        val cell = 200
        val sheet = BufferedImage(cell * results.size, cell, BufferedImage.TYPE_INT_RGB)
        results.forEachIndexed { i, p ->
            val m = Model3D("m", "m", listOf(p))
            val f = Renderer.render(m, View3.fit(m, m.camera, cell, cell, 0.1f), RenderOptions(supersample = 2, ground = false, background = -1))
            sheet.setRGB(i * cell, 0, cell, cell, f.color, 0, cell)
        }
        ImageIO.write(sheet, "png", File(dir, "v25-solids.png"))
    }
}
