package com.karapp.drawguidance.content.model3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/** v56 aşama 11: D3 v2, düzlem ve yapıştırma, eski kayıtların çevrimi. iOS `Sketch3DCoreTests` aynı vektörleri sınar. */
class Sketch3DCoreTest {

    /** iOS ile ortak örnek çizim. */
    private fun sample(): Model3D {
        // Ağ elle verilir (iki platformun cisim üreticisine bağlı kalmasın)
        val quad = Mesh(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f), arrayOf(intArrayOf(0, 1, 2, 3)), arrayOf(floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)))
        val cube = Part("c", "Küp", quad, PrimitiveSpec(PrimitiveKind.CUBE, 4, 1), position = V3(0f, 0.5f, 0f), layer = "L2", group = "g1")
        val locked = Part("k", "Kilitli", Mesh(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f), arrayOf(intArrayOf(0, 1, 2))), null, locked = true,
            material = Material(0xFF336699.toInt(), gradient = 0xFFFFCC00.toInt(), gradientAxis = 2))
        val s1 = Stroke3("s1", floatArrayOf(0f, 0f, 0f, 1f, 0.5f, 0f, 2f, 0f, 0f), 0xFF263238.toInt(), 0.02f, time = 1_700_000_000_123L,
            widths = floatArrayOf(0.2f, 1f, 0.6f), opacity = 0.8f, brush = Brush3D.INK.ordinal, plane = "front", layer = "L1")
        val s2 = Stroke3("s2", floatArrayOf(0f, 1f, 0f, 0f, 1f, 1f), 0xFFE53935.toInt(), 0.05f)
        return Model3D(
            "m-1", "Sokak köşesi", listOf(cube, locked), listOf(s1, s2), camera = Camera3(yaw = 30f, pitch = 20f),
            groups = listOf(Group3("g1", "Grup")), layers = listOf(Layer3("L1", "Katman 1"), Layer3("L2", "Gölge", visible = false, locked = true)),
            plane = Plane3(V3(0f, 0.5f, 0f), V3(1f, 0f, 0f), V3(0f, 1f, 0f), "front"),
        )
    }

    companion object {
        /** Ortak örneğin D3 v2 gövdesinin SHA-256'sı (iOS `Sketch3DCoreTests` ile aynı). */
        const val D3V2_SHA = "40afdf2bdf748c586ea46b2bda6a648af9b9c567122a60412fcc5050a4635088"
    }

    @Test fun codecV2RoundTripAndReadsV1() {
        val m = sample()
        val back = Model3DCodec.decode(Model3DCodec.encode(m))
        assertEquals(m, back)
        assertEquals(2, Model3DCodec.encode(m)[2].toInt())
        assertEquals(0.6f, back.strokes[0].widths!![2], 0f)
        assertTrue(back.parts[1].locked)
        assertEquals(0xFFFFCC00.toInt(), back.parts[1].material.gradient)
        // Ortak bayt vektörü (iOS aynı özeti üretir)
        val sha = MessageDigest.getInstance("SHA-256").digest(Model3DCodec.rawBody(m)).joinToString("") { "%02x".format(it) }
        println("D3v2 sha256=$sha")
        assertEquals(D3V2_SHA, sha)
        // Sürüm 1: alanlar varsayılan olur
        val v1 = Model3DCodecV1Writer.encode(m)
        val old = Model3DCodec.decode(v1)
        assertEquals(m.strokes.size, old.strokes.size)
        assertNull(old.strokes[0].widths)
        assertTrue(old.layers.isEmpty())
        assertTrue(Model3DStore.isLegacy(v1))
        assertFalse(Model3DStore.isLegacy(Model3DStore.encode(m)))
    }

    @Test fun legacyGlbBecomesLockedParts() {
        val cube = ModelEdit.newPart(PrimitiveKind.CUBE, "c", 0)
        val edited = cube.copy(id = "e", primitive = null)
        val glb = Gltf3.write(Model3D("x", "Eski", listOf(cube, edited)))
        assertTrue(Model3DStore.isLegacy(glb))
        val conv = Model3DStore.fromLegacy(Model3DStore.decode(glb))
        assertFalse(conv.parts.first { it.id == "c" }.locked)
        assertTrue(conv.parts.first { it.id == "e" }.locked)
    }

    @Test fun planePresetsProjectAndTilt() {
        val view = View3.editor(Camera3(yaw = 0f, pitch = 0f, persp = 0f, target = V3.ZERO), 400, 400)
        val front = PlaneSnap.preset(PlaneSnap.Preset.FRONT)
        val p = PlaneSnap.project(front, view, 300f, 100f)!!
        assertEquals(0f, p.z, 1e-4f)
        assertTrue(p.x > 0f && p.y > 0f)
        assertEquals(0f, PlaneSnap.tilt(front, view), 0.01f)
        val top = PlaneSnap.preset(PlaneSnap.Preset.TOP)
        assertTrue(PlaneSnap.tooTilted(top, view))
        // Kameraya çevir: yatıklık kalkar
        val cam = PlaneSnap.faceCamera(top, View3.editor(Camera3(yaw = 40f, pitch = 25f), 400, 400))
        assertEquals(0f, PlaneSnap.tilt(cam, View3.editor(Camera3(yaw = 40f, pitch = 25f, persp = 0f), 400, 400)), 0.5f)
        // Normal u × v
        assertEquals(1f, front.normal.z, 1e-5f)
        assertEquals(1f, top.normal.y, 1e-5f)
        assertEquals(1f, PlaneSnap.preset(PlaneSnap.Preset.SIDE).normal.x, 1e-5f)
    }

    @Test fun snapRotationFaceEndpointAndIntersection() {
        // 15° mıknatıs
        assertEquals(30f, PlaneSnap.snapAngle(37f), 0f)
        assertEquals(45f, PlaneSnap.snapAngle(38f), 0f)
        val r = PlaneSnap.rotate(PlaneSnap.preset(PlaneSnap.Preset.FRONT), 1, 88f)
        assertEquals(1f, kotlin.math.abs(r.normal.x), 1e-4f) // 90° döndü: yan düzlem
        // Yüze yapıştırma: küpün üst yüzü
        val cube = ModelEdit.newPart(PrimitiveKind.CUBE, "c", 0)
        val m = Model3D("m", "M", listOf(cube))
        val hit = Pick3.ray(m, V3(cube.position.x, 5f, cube.position.z), V3(0f, -1f, 0f))!!
        val face = PlaneSnap.onFace(m, hit)
        assertEquals(1f, face.normal.y, 1e-4f)
        assertEquals(hit.point.y, face.origin.y, 1e-4f)
        // Çizgi ucu ve kesişim
        val view = View3.editor(Camera3(yaw = 0f, pitch = 0f, persp = 0f, target = V3.ZERO), 400, 400)
        val a = Stroke3("a", floatArrayOf(-1f, 0f, 0f, 1f, 0f, 0f), 0, 0.01f)
        val b = Stroke3("b", floatArrayOf(0.3f, -1f, 0.5f, 0.3f, 1f, 0.5f), 0, 0.01f)
        val sm = Model3D("s", "S", emptyList(), listOf(a, b))
        val end = view.project(V3(1f, 0f, 0f))
        assertEquals(V3(1f, 0f, 0f), PlaneSnap.snapPoint(sm, view, end[0] + 3f, end[1], 20f))
        val x = view.project(V3(0.3f, 0f, 0f))
        val ix = PlaneSnap.snapPoint(sm, view, x[0] + 2f, x[1] - 2f, 20f)
        assertNotNull(ix)
        assertEquals(0.3f, ix!!.x, 1e-3f)
        assertEquals(0.25f, ix.z, 1e-3f) // iki çizginin 3B orta noktası
        assertNull(PlaneSnap.snapPoint(sm, view, 10f, 10f, 20f))
    }

    @Test fun hiddenLayersAreNotDrawn() {
        val m = sample()
        val v = m.visibleLayers()
        assertFalse(v.parts.first { it.id == "c" }.visible)
        assertEquals(m.strokes.size, v.strokes.size) // s1 L1, s2 ilk katman (görünür)
        assertTrue(m.layerLocked("L2"))
    }
}

/** Testte sürüm 1 dosyası üretmek için (eski uygulamanın yazdığı biçim). */
object Model3DCodecV1Writer {
    fun encode(model: Model3D): ByteArray {
        val o = java.io.ByteArrayOutputStream()
        fun b(v: Int) = o.write(v and 0xFF)
        fun i(v: Int) { b(v shr 24); b(v shr 16); b(v shr 8); b(v) }
        fun f(v: Float) = i(v.toRawBits())
        fun v3(v: V3) { f(v.x); f(v.y); f(v.z) }
        fun u(value: Int) { var v = value.toLong(); while (v and 0x7FL.inv() != 0L) { b(((v and 0x7F) or 0x80).toInt()); v = v ushr 7 }; b(v.toInt()) }
        fun str(s: String) { val bytes = s.toByteArray(); u(bytes.size); o.write(bytes) }
        str(model.id); str(model.name)
        val s = model.scene
        f(s.light.yaw); f(s.light.pitch); f(s.light.intensity); f(s.light.ambient); i(s.light.color)
        b(1); b(1); i(s.edgeColor); i(s.background); b(1)
        val c = model.camera
        f(c.yaw); f(c.pitch); f(c.persp); f(c.zoom); v3(c.target)
        u(0)
        u(model.strokes.size)
        for (st in model.strokes) { str(st.id); i(st.color); f(st.width); u(st.count); st.points.forEach { f(it) } }
        val d = java.util.zip.Deflater(9, true)
        d.setInput(o.toByteArray()); d.finish()
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf('D'.code.toByte(), '3'.code.toByte(), 1))
        val buf = ByteArray(8192)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }
}
