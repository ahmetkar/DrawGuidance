package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushParams
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Depth3D
import com.karapp.drawguidance.data.model.GuideSpec
import com.karapp.drawguidance.data.model.Plane3D
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.Scene3D
import com.karapp.drawguidance.ui.drawing.SceneCamera
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * v22 döndürülebilir 3B sahne: kamera matematiği (varsayılan izometrik, hazır açılar), çizim aracının düzleme yatırması,
 * izlerin sahnedeki yeri (döndürüp geri döndürünce aynı yer), 2B'de taşınan izin yeri, kenar üstü düzlem, gerçek 3B
 * cisimler ve içerik biçiminde (v7) saklama.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V22SceneTest {

    @After
    fun tearDown() = org.koin.core.context.stopKoin()

    private val w = 1000; private val h = 800
    private fun cam(yaw: Float = GuideSpec.DEFAULT_YAW, pitch: Float = GuideSpec.DEFAULT_PITCH, persp: Float = 0f) =
        Scene3D.camera(GuideSpec(draw3d = true, yaw3d = yaw, pitch3d = pitch, persp3d = persp), w, h)

    private fun axis(c: SceneCamera, x: Float, y: Float, z: Float): Offset {
        val o = c.project(0f, 0f, 0f); val p = c.project(x, y, z)
        return Offset(p.x - o.x, p.y - o.y)
    }

    private fun angle(v: Offset) = Math.toDegrees(atan2(v.y.toDouble(), v.x.toDouble()))

    @Test
    fun cameraAxesAndPresets() {
        val iso = cam()
        // İzometrik: X sağ-aşağı 30°, Z sol-aşağı, Y yukarı; eksenler eşit kısalır (0,816)
        val x = axis(iso, 100f, 0f, 0f); val z = axis(iso, 0f, 0f, 100f); val y = axis(iso, 0f, 100f, 0f)
        assertEquals(30.0, angle(x), 0.1)
        assertEquals(150.0, angle(z), 0.1)
        assertEquals(-90.0, angle(y), 0.1)
        assertEquals(81.65f, hypot(x.x, x.y), 0.1f)
        // Önden: X sağa, Y yukarı, Z görünmez
        val front = cam(0f, 0f)
        assertEquals(Offset(100f, 0f), axis(front, 100f, 0f, 0f))
        assertEquals(-100f, axis(front, 0f, 100f, 0f).y, 0.01f)
        assertEquals(0f, hypot(axis(front, 0f, 0f, 100f).x, axis(front, 0f, 0f, 100f).y), 0.01f)
        // Üstten: Z aşağı doğru görünür
        val top = cam(0f, 90f)
        assertEquals(100f, axis(top, 0f, 0f, 100f).y, 0.01f)
        // Perspektif: yakın (bakana doğru) nokta büyür
        val p = cam(0f, 0f, 0.8f)
        assertTrue(hypot(axis(p, 0f, 0f, 0f).x, 0f) < 1e-3f)
        val near = p.project(100f, 0f, 200f).x - p.project(0f, 0f, 200f).x
        val far = p.project(100f, 0f, -200f).x - p.project(0f, 0f, -200f).x
        assertTrue("yakın $near > uzak $far", near > far * 1.2f)
    }

    /** Çizim aracının yaptığı gibi: parmak yatay çizer, iz düzleme yatırılır. */
    private fun drawn(plane: Plane3D, c: SceneCamera, x0: Float, y0: Float, len: Float, id: String = "s"): Stroke {
        val eff = Scene3D.effectivePlane(plane, c)
        val m = Scene3D.DrawMapping(eff, c, x0, y0)
        val pts = (0..40).map { i ->
            val o = m.map(x0 + len * i / 40f, y0 + 20f * kotlin.math.sin(i / 6f))
            StrokePoint(ContentCodec.quantize(o.x), ContentCodec.quantize(o.y))
        }
        return Stroke(id, pts, "#224466", BrushType.MARKER, 8f, 1f, 0, params = BrushParams(depth3d = Depth3D(plane, 0.5f)))
    }

    private fun maxDist(a: List<StrokePoint>, b: List<StrokePoint>): Float {
        assertEquals(a.size, b.size)
        return a.indices.maxOf { hypot(a[it].x - b[it].x, a[it].y - b[it].y) }
    }

    @Test
    fun strokesKeepTheirPlaceWhenTheSceneRotates() {
        val iso = cam()
        for (plane in Plane3D.entries) {
            val s0 = drawn(plane, iso, 300f, 300f, 240f)
            // Çizerken iz parmağın altında (yüzün üzerine çizilir): yatay hareket yatay kalır
            val d = Offset(s0.points.last().x - s0.points.first().x, s0.points.last().y - s0.points.first().y)
            assertEquals(0.0, angle(d), 5.0)
            val placed = Scene3D.sync(s0, iso)
            assertNotNull(placed.space)
            assertEquals(s0.points, placed.points)
            assertNotNull(placed.params?.depth3d?.ex)
            // Döndür: noktalar değişir; geri döndür: aynı yer
            val turned = Scene3D.sync(placed, cam(80f, 10f, 0.4f))
            assertTrue("$plane dönünce değişmeli", maxDist(turned.points, placed.points) > 20f)
            val back = Scene3D.sync(turned, iso)
            assertTrue("$plane geri dönünce aynı yer: ${maxDist(back.points, placed.points)}", maxDist(back.points, placed.points) < 0.6f)
            // Kamera aynıysa hiçbir şey değişmez (aynı nesne)
            assertTrue(Scene3D.sync(back, iso) === back)
        }
    }

    @Test
    fun edgeOnPlaneFallsBackToScreenPlane() {
        // Önden bakışta yan yüz kenardan görünür: çizim ekran düzlemine geçer
        val front = cam(0f, 0f)
        assertEquals(Plane3D.FREE, Scene3D.effectivePlane(Plane3D.SIDE, front))
        assertEquals(Plane3D.FRONT, Scene3D.effectivePlane(Plane3D.FRONT, front))
        val s = Scene3D.sync(drawn(Plane3D.SIDE, front, 200f, 200f, 200f), front)
        assertEquals(Plane3D.FREE, s.params?.depth3d?.plane)
        // Ekran düzleminde çizgi parmağın altında (yatay); önden bakınca da yatay kalır
        assertEquals(s.points.first().y, s.points.last().y, 21f)
        val side = Scene3D.sync(s, cam(0f, 0f))
        assertTrue(side === s)
    }

    /** Ön yüze izometrik görünümden yatay çizilen çizgi önden bakınca ön yüzdeki gerçek eğimiyle (−26,6°) görünür. */
    @Test
    fun frontPlaneLineSeenFromTheFront() {
        val iso = cam()
        val s = Scene3D.sync(drawn(Plane3D.FRONT, iso, 300f, 300f, 240f).let { it.copy(points = listOf(it.points.first(), it.points.first().at(it.points.first().x + 240f, it.points.first().y))) }, iso)
        val front = Scene3D.sync(s, cam(0f, 0f))
        val d = Offset(front.points.last().x - front.points.first().x, front.points.last().y - front.points.first().y)
        assertEquals(-26.57, angle(d), 0.5)
    }

    @Test
    fun movedStrokeKeepsItsNewPlaceInTheScene() {
        val iso = cam()
        val placed = Scene3D.sync(drawn(Plane3D.TOP, iso, 400f, 400f, 200f), iso)
        // 2B'de taşı (Taşı modu)
        val moved = placed.copy(points = placed.points.map { it.at(it.x + 60f, it.y - 25f) })
        val synced = Scene3D.sync(moved, iso)
        assertNotEquals(placed.space, synced.space)
        assertTrue(maxDist(synced.points, moved.points) < 0.01f)
        val back = Scene3D.sync(Scene3D.sync(synced, cam(200f, 50f)), iso)
        assertTrue("taşınan yer korunmalı: ${maxDist(back.points, moved.points)}", maxDist(back.points, moved.points) < 0.6f)
    }

    @Test
    fun solidsAreRealMeshesThatTurn() {
        val iso = cam()
        val style = ShapeStyle(strokeColorHex = "#1E88E5", strokeWidth = 3f)
        val cube = Scene3D.solidStroke("SOLID_CUBE", Offset(300f, 250f), Offset(500f, 450f), style, iso, "k", 0)!!
        assertNotNull(cube.space)
        assertTrue(cube.space!!.isSolid)
        // İzometrik görünümde 3 yüz + dış hat
        assertEquals(4, cube.shape!!.subpaths.size)
        val xs = cube.points.map { it.x }; val ys = cube.points.map { it.y }
        assertTrue(xs.min() >= 295f && xs.max() <= 505f && ys.min() >= 245f && ys.max() <= 455f)
        // Önden: tek yüz + dış hat
        val front = Scene3D.sync(cube, cam(0f, 0f))
        assertEquals(2, front.shape!!.subpaths.size)
        // Küre: çok sayıda eğri yüz parçası, kenarsız
        val sphere = Scene3D.solidStroke("SOLID_SPHERE", Offset(100f, 100f), Offset(300f, 300f), style, iso, "k2", 1)!!
        assertTrue(sphere.shape!!.subpaths.size > 60)
        listOf("SOLID_BOX", "SOLID_CYLINDER", "SOLID_CONE", "SOLID_DOME", "SOLID_PYRAMID", "SOLID_PRISM", "SOLID_HEX_PRISM").forEach { k ->
            val sld = Scene3D.solidStroke(k, Offset(100f, 100f), Offset(300f, 260f), style, iso, k, 0)
            assertNotNull(k, sld)
            // Döndürünce yeniden kurulur, geri döndürünce aynı
            val t = Scene3D.sync(Scene3D.sync(sld!!, cam(130f, -20f)), iso)
            assertTrue(k, maxDist(t.points, sld.points) < 0.6f)
        }
        // 2B'de taşınan küp: sahnede de taşınır
        val moved = cube.copy(points = cube.points.map { it.at(it.x + 50f, it.y) })
        val synced = Scene3D.sync(moved, iso)
        val cx0 = cube.points.map { it.x }.average(); val cx1 = synced.points.map { it.x }.average()
        assertEquals(50.0, cx1 - cx0, 2.0)
    }

    @Test
    fun spaceIsStoredInContentV7() {
        val iso = cam()
        val s = Scene3D.sync(drawn(Plane3D.FRONT, iso, 300f, 300f, 200f), iso)
        val cube = Scene3D.solidStroke("SOLID_CUBE", Offset(300f, 250f), Offset(500f, 450f), ShapeStyle(), iso, "k", 1)!!
        val bytes = ContentCodec.encode(listOf(s, cube))
        assertEquals(7, bytes[2].toInt())
        val back = ContentCodec.decode(bytes)
        assertEquals(s.space!!.pointCount, back[0].space!!.pointCount)
        assertTrue(s.space!!.frame.contentEquals(back[0].space!!.frame))
        assertEquals(s.space!!.stamp, back[0].space!!.stamp)
        assertEquals(s.space!!.view, back[0].space!!.view)
        for (i in s.space!!.local.indices) assertEquals(s.space!!.local[i], back[0].space!!.local[i], 0.04f)
        assertTrue(back[1].space!!.isSolid)
        // Çözülen iz aynı kamerada değişmez; döndürülünce aynı biçimde döner
        assertTrue(Scene3D.sync(back[0], iso) === back[0])
        val a = Scene3D.sync(s, cam(100f, 20f)); val b = Scene3D.sync(back[0], cam(100f, 20f))
        assertTrue(maxDist(a.points, b.points) < 0.5f)
        // Düz çizimler v7 gerektirmez
        assertEquals(4, ContentCodec.encode(listOf(Stroke("p", listOf(StrokePoint(1f, 2f), StrokePoint(3f, 4f)), "#000000", BrushType.PENCIL, 3f, 1f, 0)))[2].toInt())
    }

    @Test
    fun syncPagesLeavesFlatDrawingsAlone() {
        val flat = Stroke("p", listOf(StrokePoint(1f, 2f), StrokePoint(30f, 4f)), "#000000", BrushType.PENCIL, 3f, 1f, 0)
        val g = GuideSpec(draw3d = true)
        assertEquals(null, Scene3D.syncPages(listOf(listOf(flat)), g, w, h))
        val s = drawn(Plane3D.FRONT, cam(), 300f, 300f, 200f, "d")
        val pages = Scene3D.syncPages(listOf(listOf(flat, s)), g, w, h)!!
        assertTrue(pages[0][0] === flat)
        assertNotNull(pages[0][1].space)
        // Kamera değişince yalnız 3B iz değişir
        val turned = Scene3D.syncPages(pages, g.copy(yaw3d = 120f), w, h)!!
        assertTrue(turned[0][0] === flat)
        assertTrue(maxDist(turned[0][1].points, pages[0][1].points) > 10f)
    }
}

/** v22 3B cisim ağları farklı açılardan ve perspektifle (DG_SHOTS verilince). */
@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class V22SolidMeshShotTest {
    @Test
    fun meshes() {
        val dir = System.getenv("DG_SHOTS") ?: return
        val kinds = listOf("SOLID_CUBE", "SOLID_BOX", "SOLID_CYLINDER", "SOLID_CONE", "SOLID_SPHERE", "SOLID_DOME", "SOLID_PYRAMID", "SOLID_PRISM", "SOLID_HEX_PRISM")
        val views = listOf(Triple(GuideSpec.DEFAULT_YAW, GuideSpec.DEFAULT_PITCH, 0f), Triple(20f, 15f, 0.5f), Triple(160f, -25f, 0.3f), Triple(0f, 75f, 0f))
        val cell = 170
        val bmp = android.graphics.Bitmap.createBitmap(cell * kinds.size, cell * views.size, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(0xFFF7F4EC.toInt())
        val fills = listOf("#E53935", "#1E88E5", null, "#FDD835", "#43A047", "#8E24AA", "#FB8C00", "#00897B", null)
        views.forEachIndexed { r, (yaw, pitch, persp) ->
            kinds.forEachIndexed { i, k ->
                val x = i * cell.toFloat(); val y = r * cell.toFloat()
                // Her hücre kendi sahnesi: merkez hücrenin ortası
                val cam = SceneCamera(yaw, pitch, persp, x + cell / 2f, y + cell / 2f, 500f)
                val style = ShapeStyle(strokeColorHex = "#263238", strokeWidth = 2.5f, fillColorHex = fills[i])
                val s = Scene3D.solidStroke(k, Offset(x + 25f, y + 25f), Offset(x + cell - 25f, y + cell - 25f), style, cam, "$k$r", 0)!!
                com.karapp.drawguidance.ui.drawing.ShapeRenderer.draw(c, s)
            }
        }
        java.io.File(dir, "v22-meshes.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
