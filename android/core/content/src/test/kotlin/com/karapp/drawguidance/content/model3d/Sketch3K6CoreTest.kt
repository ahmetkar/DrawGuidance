package com.karapp.drawguidance.content.model3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.sin

/** v56 aşama 12–13 çekirdek: dolgu, görünüm stilleri, yüzeye boya, GIF ve dönen kamera. */
class Sketch3K6CoreTest {

    private fun circle(n: Int, r: Float, z: Float = 0f, close: Boolean = true): FloatArray {
        val k = if (close) n + 1 else n
        return FloatArray(k * 3) { i ->
            val a = (i / 3) * 2.0 * Math.PI / n
            when (i % 3) { 0 -> (r * cos(a)).toFloat(); 1 -> (0.5f + r * sin(a)).toFloat(); else -> z }
        }
    }

    @Test fun fillFromClosedLoopIsDoubleSidedPlanarPart() {
        val loop = circle(40, 0.4f)
        assertTrue(Sketch3Fill.isClosed(loop, 0.02f))
        assertFalse(Sketch3Fill.isClosed(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 2f, 0f, 0f), 0.02f))
        val p = Sketch3Fill.fromLoop(loop, "a", 0xFFE53935.toInt(), layer = "L1")!!
        assertTrue(Sketch3Fill.isFill(p))
        assertNull(p.primitive)
        assertEquals(2, p.mesh.faceCount)
        assertEquals(40, p.mesh.faces[0].size)
        assertEquals(0.5f, p.position.y, 1e-3f)
        assertEquals("L1", p.layer)
        // Üçgenler çokgeni kaplar: alan ≈ π r²
        var area = 0.0
        val m = p.mesh; val f = m.faces[0]; val t = m.triangles[0]
        for (k in t.indices step 3) {
            val a = f[t[k]] * 3; val b = f[t[k + 1]] * 3; val c = f[t[k + 2]] * 3
            val ab = V3(m.pos[b] - m.pos[a], m.pos[b + 1] - m.pos[a + 1], m.pos[b + 2] - m.pos[a + 2])
            val ac = V3(m.pos[c] - m.pos[a], m.pos[c + 1] - m.pos[a + 1], m.pos[c + 2] - m.pos[a + 2])
            area += (ab cross ac).length / 2.0
        }
        assertEquals(Math.PI * 0.16, area, 0.02)
        // İçbükey (L) şekil de üçgenlenir
        val l = floatArrayOf(0f, 0f, 0f, 2f, 0f, 0f, 2f, 1f, 0f, 1f, 1f, 0f, 1f, 2f, 0f, 0f, 2f, 0f)
        val lp = Sketch3Fill.fromLoop(l, "l", 0)!!
        assertEquals(4, lp.mesh.triangles[0].size / 3)
        // Düz çizgi alan vermez
        assertNull(Sketch3Fill.fromLoop(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 2f, 0f, 0f), "x", 0))
        // Işın dolguya iki yüzden de değer
        val model = Model3D("m", "M", listOf(p))
        assertNotNull(Pick3.ray(model, V3(0f, 0.5f, 3f), V3(0f, 0f, -1f)))
        assertNotNull(Pick3.ray(model, V3(0f, 0.5f, -3f), V3(0f, 0f, 1f)))
    }

    @Test fun screenPolygonInsideAndArea() {
        val sq = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f)
        assertTrue(Sketch3Fill.inside(sq, 5f, 5f))
        assertFalse(Sketch3Fill.inside(sq, 15f, 5f))
        assertEquals(100f, Sketch3Fill.screenArea(sq), 1e-3f)
        val many = FloatArray(1000 * 3) { if (it % 3 == 0) it / 3 * 0.001f else 0f }
        assertTrue(Sketch3Fill.simplify(many).size / 3 <= Sketch3Fill.MAX_POINTS)
    }

    @Test fun toonAndLineStylesChangeTheImage() {
        val cube = ModelEdit.newPart(PrimitiveKind.SPHERE, "s", 0)
        val m = Model3D("m", "M", listOf(cube))
        val view = View3.editor(m.camera, 120, 120)
        fun colors(style: Int): Set<Int> {
            val fr = Renderer.render(m, view, RenderOptions(supersample = 1, style = style, ground = false, shadow = false, background = 0xFFFFFFFF.toInt()))
            return fr.color.toSet()
        }
        val soft = colors(RenderStyle.SOFT); val toon = colors(RenderStyle.TOON); val lines = colors(RenderStyle.LINES)
        assertTrue("toon bantlı az renk", toon.size < soft.size)
        assertTrue("çizgi stilinde mürekkep rengi", lines.contains(RenderStyle.INK) || lines.any { (it and 0xFFFFFF) < 0x404040 })
        assertEquals(0.5f, RenderStyle.toonBand(0.2f), 0f)
        assertEquals(1f, RenderStyle.toonBand(0.9f), 0f)
    }

    @Test fun surfacePaintFollowsTheCubeAndSplitsOnMiss() {
        val cube = ModelEdit.newPart(PrimitiveKind.CUBE, "c", 0)
        val m = Model3D("m", "M", listOf(cube), camera = Camera3(yaw = 0f, pitch = 0f, persp = 0f, zoom = 1f, target = cube.position))
        val view = View3.editor(m.camera, 400, 400)
        val c = view.project(cube.position)
        // Soldan sağa: küpün dışında başlayıp içinden geçip dışarıda biten yol
        val screen = FloatArray(41 * 2) { i -> if (i % 2 == 0) c[0] - 190f + (i / 2) * 9.5f else c[1] }
        val segs = SurfacePaint.cast(m, view, screen)
        assertEquals(1, segs.size)
        val (pts, idx) = segs[0]
        assertTrue(idx.size in 3 until 41)
        val front = ModelEdit.worldBounds(cube).max.z
        for (i in 0 until pts.size / 3) assertEquals(front + SurfacePaint.OFFSET, pts[3 * i + 2], 1e-3f)
        assertTrue(SurfacePaint.cast(m, view, floatArrayOf(2f, 2f, 5f, 5f)).isEmpty())
    }

    @Test fun gifRoundTripsThroughImageIO() {
        val w = 24; val h = 16
        val g = GifWriter(w, h, delayCs = 5)
        val colors = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
        for (c in colors) g.addFrame(IntArray(w * h) { if (it % w < w / 2) c else 0xFFFFFFFF.toInt() })
        val bytes = g.finish()
        assertEquals("GIF89a", String(bytes, 0, 6))
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        assertEquals(3, reader.getNumImages(true))
        for ((i, c) in colors.withIndex()) {
            val img = reader.read(i)
            assertEquals(w, img.width)
            assertEquals(c and 0xFFFFFF, img.getRGB(2, 3) and 0xFFFFFF)
            assertEquals(0xFFFFFF, img.getRGB(w - 2, 3) and 0xFFFFFF)
        }
        // Büyük, çok renkli kare (LZW sözlük sıfırlaması)
        val big = GifWriter(160, 120)
        big.addFrame(IntArray(160 * 120) { (0xFF shl 24) or (it * 2654435761L).toInt().and(0xFFFFFF) })
        val b2 = big.finish()
        val r2 = ImageIO.getImageReadersByFormatName("gif").next()
        r2.input = ImageIO.createImageInputStream(ByteArrayInputStream(b2))
        val px = IntArray(160 * 120) { (0xFF shl 24) or (it * 2654435761L).toInt().and(0xFFFFFF) }
        val img = r2.read(0)
        val k = 4321; val expect = GifWriter.PALETTE[GifWriter.index(px[k], intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)[((k / 160) and 3) * 4 + ((k % 160) and 3)])]
        assertEquals(expect, img.getRGB(k % 160, k / 160) and 0xFFFFFF)
    }

    @Test fun orbitTrackTurnsOnceAndSpeedChangesLength() {
        assertEquals(180, OrbitTrack.frameCount(30, 1f))
        assertEquals(90, OrbitTrack.frameCount(30, 2f))
        assertEquals(360, OrbitTrack.frameCount(30, 0.5f))
        val cams = OrbitTrack.cameras(Camera3(yaw = 10f), 4)
        assertEquals(listOf(10f, 100f, 190f, 280f), cams.map { it.yaw })
    }
}
