package com.karapp.drawguidance.content.model3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * v26: silgi yalnız çizimlere (çizgi, tüp, şişir, torna) üstünden geçilen kısmı siler; fırça kaynağı, gruplar ve çizim
 * anları GLB'de saklanır.
 */
class Model3DV26Test {

    /** Önden (paralel) bakan görünüm: dünya XY düzlemi ekrana paralel. */
    private val view = View3.editor(Camera3(yaw = 0f, pitch = 0f, persp = 0f, zoom = 1f, target = V3(0f, 0f, 0f)), 800, 800)

    private fun screen(v: V3): Pair<Float, Float> { val s = view.project(v); return s[0] to s[1] }

    private fun eraserLine(a: V3, b: V3, radius: Float, steps: Int = 12): BrushErase.Eraser {
        val xs = FloatArray(steps + 1); val ys = FloatArray(steps + 1)
        for (i in 0..steps) { val (x, y) = screen(a + (b - a) * (i / steps.toFloat())); xs[i] = x; ys[i] = y }
        return BrushErase.Eraser(xs, ys, radius)
    }

    private var ids = 0
    private fun newId() = "n${ids++}"

    private fun brushPart(kind: Brush3Kind, pts: List<V3>, width: Float = 0.1f, normal: V3 = V3(0f, 0f, 1f), up: V3 = V3.UP): Part {
        val spec = BrushShapeSpec(kind, width, height = 0.4f)
        val input = BrushInput(pts, null, normal, up)
        val world = BrushShapes.build(input, spec)!!
        val src = BrushSource(spec, FloatArray(pts.size * 3) { pts[it / 3].get(it % 3) }, null, normal, up, time = 42L)
        return BrushShapes.toPart(world, "p", "Çizim", Material(), src)
    }

    private fun V3.get(i: Int) = when (i) { 0 -> x; 1 -> y; else -> z }

    @Test
    fun tubeErasedInTheMiddleSplitsIntoTwoClosedParts() {
        val line = (0..40).map { V3(-1f + it * 0.05f, 0.2f, 0f) }
        val part = brushPart(Brush3Kind.TUBE, line)
        assertTrue(part.brush!!.valid(part.mesh))
        // Uzaktaki silgi değmez
        assertNull(BrushErase.erasePart(part, view, eraserLine(V3(0f, 2f, 0f), V3(0.2f, 2f, 0f), 10f), ::newId))
        // Ortadan dikey silgi: iki parça
        val out = BrushErase.erasePart(part, view, eraserLine(V3(0f, -0.5f, 0f), V3(0f, 0.9f, 0f), 12f), ::newId)!!
        assertEquals(2, out.size)
        assertEquals("ilk parça kimliği korur", "p", out[0].id)
        out.forEach { p ->
            assertTrue(p.mesh.closed)
            assertTrue("kaynak yeni ağla geçerli", p.brush!!.valid(p.mesh))
            assertEquals(42L, p.brush!!.time)
            val b = Transform3.of(p).let { t -> p.mesh.mapPositions { t.apply(it) }.bounds }
            assertTrue("parça silginin bir yanında: ${b.min.x}..${b.max.x}", b.max.x < -0.02f || b.min.x > 0.02f)
        }
        // Tümü silinince boş liste
        val all = BrushErase.erasePart(part, view, eraserLine(V3(-1.2f, 0.2f, 0f), V3(1.2f, 0.2f, 0f), 20f, 40), ::newId)!!
        assertTrue(all.isEmpty())
    }

    @Test
    fun movedAndScaledDrawingIsErasedWhereItIsSeen() {
        val line = (0..40).map { V3(-1f + it * 0.05f, 0f, 0f) }
        val part = brushPart(Brush3Kind.RIBBON, line).let { it.copy(position = it.position + V3(0f, 0.5f, 0f), scale = V3(0.5f, 0.5f, 0.5f)) }
        // Dünyada parça x: -0.5..0.5 (ölçek 0,5), y ≈ 0,5
        val out = BrushErase.erasePart(part, view, eraserLine(V3(0.25f, 0.2f, 0f), V3(0.25f, 0.8f, 0f), 8f), ::newId)!!
        assertEquals(2, out.size)
        out.forEach { p ->
            assertEquals(part.scale, p.scale)
            val b = Transform3.of(p).let { t -> p.mesh.mapPositions { t.apply(it) }.bounds }
            assertTrue(b.max.x < 0.25f || b.min.x > 0.25f)
        }
        // Başka araçla düzenlenen ağ: kaynak geçersiz, silgi dokunmaz
        val edited = part.copy(mesh = part.mesh.mapPositions { it * 1.01f })
        assertNull(BrushErase.erasePart(edited, view, eraserLine(V3(0.25f, 0.2f, 0f), V3(0.25f, 0.8f, 0f), 8f), ::newId))
    }

    @Test
    fun strokeSplitsAndKeepsTime() {
        val pts = FloatArray(3 * 3).also { a -> listOf(V3(-1f, 0f, 0f), V3(0f, 0f, 0f), V3(1f, 0f, 0f)).forEachIndexed { i, v -> a[3 * i] = v.x; a[3 * i + 1] = v.y; a[3 * i + 2] = v.z } }
        val s = Stroke3("s", pts, 0xFF000000.toInt(), 0.02f, 7L)
        // Seyrek noktalar arasından geçen silgi de keser
        val out = BrushErase.eraseStroke(s, view, eraserLine(V3(0.5f, -0.3f, 0f), V3(0.5f, 0.3f, 0f), 6f), ::newId)!!
        assertEquals(2, out.size)
        assertEquals("s", out[0].id)
        assertTrue(out.all { it.time == 7L })
        assertTrue(out[0].points[0] < out[1].points[0])
        assertNull(BrushErase.eraseStroke(s, view, eraserLine(V3(0f, 1f, 0f), V3(0.2f, 1f, 0f), 6f), ::newId))
    }

    @Test
    fun inflateAndPrismAreCarvedWhereRubbed() {
        val circle = (0 until 64).map { val a = it / 64.0 * 2 * Math.PI; V3(cos(a).toFloat() * 0.8f, sin(a).toFloat() * 0.6f, 0f) }
        val part = brushPart(Brush3Kind.INFLATE, circle, width = 0.05f)
        assertTrue(BrushErase.isArea(part))
        assertNull("alan çizimi yeniden kurularak silinmez", BrushErase.erasePart(part, view, eraserLine(V3(0f, -1f, 0f), V3(0f, 1f, 0f), 14f), ::newId))
        val v0 = volume(part.mesh)
        // Ortada küçük delik: tek parça, kapalı, biçimin kalanı aynı (hacim az azalır)
        val c = screen(V3(0f, 0f, 0f))
        val hole = BrushErase.carvePart(part, view, BrushErase.Eraser(floatArrayOf(c.first), floatArrayOf(c.second), 30f), ::newId)!!
        assertEquals(1, hole.size)
        assertTrue("oyuk dikişsiz: ${openEdges(hole[0].mesh)}", openEdges(hole[0].mesh) <= 6)
        val v1 = volume(hole[0].mesh)
        assertTrue("delik: $v0 → $v1", v1 < v0 * 0.995 && v1 > v0 * 0.85)
        assertTrue("oyulan parça yine çizim (imzası yeni ağla)", hole[0].brush!!.valid(hole[0].mesh))
        // Dikey şerit: iki parça
        val split = BrushErase.carvePart(part, view, eraserLine(V3(0f, -1f, 0f), V3(0f, 1f, 0f), 14f), ::newId)!!
        assertEquals(2, split.size)
        split.forEach { assertTrue(openEdges(it.mesh) <= 6) }
        assertEquals("ilk parça kimliği korur", "p", split[0].id)
        // Uzak silgi: değişmez
        assertNull(BrushErase.carvePart(part, view, eraserLine(V3(2f, 2f, 0f), V3(2.2f, 2f, 0f), 10f), ::newId))
        // Kapalı duvar (prizma) da oyulur
        val prism = brushPart(Brush3Kind.WALL, circle, width = 0.05f)
        assertTrue(BrushErase.isArea(prism))
        val cut = BrushErase.carvePart(prism, view, eraserLine(V3(0f, -1f, 0f), V3(0f, 1f, 0f), 14f), ::newId)!!
        assertEquals(2, cut.size)
        cut.forEach { assertTrue("prizma: ${openEdges(it.mesh)}", openEdges(it.mesh) <= 6) }
    }

    @Test
    fun latheErasesBands() {
        // Eksenden başlayan vazo profili (sağa doğru)
        val profile = listOf(V3(0f, -0.6f, 0f), V3(0.35f, -0.6f, 0f), V3(0.45f, -0.3f, 0f), V3(0.3f, 0f, 0f), V3(0.35f, 0.3f, 0f), V3(0.25f, 0.6f, 0f), V3(0f, 0.6f, 0f))
        val dense = BrushShapes.resample(profile, null, 0.03f, false).first
        val part = brushPart(Brush3Kind.LATHE, dense)
        // Ortadan yatay silgi (sol tarafta da değer: dönen halka)
        val out = BrushErase.erasePart(part, view, eraserLine(V3(-0.6f, 0f, 0f), V3(-0.2f, 0f, 0f), 8f), ::newId)!!
        assertEquals(2, out.size)
        out.forEach { assertTrue(it.mesh.closed); assertNotNull(it.brush!!.axis) }
    }

    @Test
    fun glbKeepsGroupsBrushAndStrokeTime() {
        val line = (0..30).map { V3(-1f + it * 0.06f, 0.3f, 0f) }
        val drawn = brushPart(Brush3Kind.TUBE, line).copy(group = "g1")
        val cube = Part("c", "Küp", Primitives.cube(), null, group = "g1")
        val m = Model3D(
            "m", "Model", listOf(drawn, cube),
            strokes = listOf(Stroke3("s", floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f), 0xFF112233.toInt(), 0.02f, 99L)),
            groups = listOf(Group3("g1", "Masa")),
        )
        val back = Gltf3.read(Gltf3.write(m))!!
        assertEquals(listOf(Group3("g1", "Masa")), back.groups)
        assertEquals(listOf("g1", "g1"), back.parts.map { it.group })
        val src = back.parts[0].brush!!
        assertEquals(Brush3Kind.TUBE, src.spec.kind)
        assertEquals(42L, src.time)
        assertTrue("yeniden okunan ağla imza geçerli", src.valid(back.parts[0].mesh))
        assertTrue(src.points.contentEquals(drawn.brush!!.points))
        assertNull(back.parts[1].brush)
        assertEquals(99L, back.strokes.single().time)
        // Okunan parça da silinebilir
        val out = BrushErase.erasePart(back.parts[0], view, eraserLine(V3(0f, 0f, 0f), V3(0f, 0.6f, 0f), 10f), ::newId)
        assertEquals(2, out!!.size)
    }

    @Test
    fun erasePreviewSheet() {
        val dir = System.getProperty("previewDir")?.let { File(it).apply { mkdirs() } } ?: return
        val circle = (0 until 64).map { val a = it / 64.0 * 2 * Math.PI; V3(cos(a).toFloat() * 0.8f, sin(a).toFloat() * 0.6f, 0f) }
        val pillow = brushPart(Brush3Kind.INFLATE, circle, width = 0.05f)
        val holed = BrushErase.carvePart(pillow, view, BrushErase.Eraser(floatArrayOf(400f), floatArrayOf(400f), 40f), ::newId)!!
        val tube = brushPart(Brush3Kind.TUBE, (0..40).map { V3(-1f + it * 0.05f, -0.9f + 0.2f * sin(it / 5f), 0f) })
        val cut = BrushErase.erasePart(tube, view, eraserLine(V3(0f, -1.3f, 0f), V3(0f, -0.5f, 0f), 12f), ::newId)!!
        val m = Model3D("p", "p", holed + cut, camera = Camera3(yaw = 20f, pitch = 25f))
        val f = Renderer.render(m, View3.fit(m, m.camera, 600, 450), RenderOptions(supersample = 2))
        val img = java.awt.image.BufferedImage(600, 450, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, 600, 450, f.color, 0, 600)
        javax.imageio.ImageIO.write(img, "png", File(dir, "v26-erase.png"))
    }

    private fun openEdges(m: Mesh): Int {
        val e = m.edges
        var n = 0; var i = 0
        while (i < e.size) { if (e[i + 3] < 0) n++; i += 4 }
        return n
    }

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
}
