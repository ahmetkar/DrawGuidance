package com.karapp.drawguidance.content.model3d

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * 3B modelin veritabanında (ve tuvaldeki 3B şeklin verisinde) saklandığı kompakt, kayıpsız ikili biçim: "D3" + sürüm,
 * ardından DEFLATE ile sıkıştırılmış gövde. Ondalıklar ham bitleriyle (kayıpsız), sayılar değişken uzunlukta yazılır.
 */
object Model3DCodec {

    private const val M0: Byte = 'D'.code.toByte()
    private const val M1: Byte = '3'.code.toByte()
    private const val VERSION: Byte = 1

    fun encode(model: Model3D): ByteArray {
        val w = W()
        w.str(model.id); w.str(model.name)
        val s = model.scene
        w.f(s.light.yaw); w.f(s.light.pitch); w.f(s.light.intensity); w.f(s.light.ambient); w.i(s.light.color)
        w.b(if (s.shadow) 1 else 0); w.b(if (s.edges) 1 else 0); w.i(s.edgeColor); w.i(s.background); w.b(if (s.ground) 1 else 0)
        val c = model.camera
        w.f(c.yaw); w.f(c.pitch); w.f(c.persp); w.f(c.zoom); w.v3(c.target)
        w.u(model.parts.size)
        for (p in model.parts) {
            w.str(p.id); w.str(p.name)
            val prim = p.primitive
            if (prim == null) w.b(0) else {
                w.b(1); w.b(prim.kind.ordinal); w.u(prim.segments); w.u(prim.rings); w.f(prim.top); w.f(prim.thickness)
            }
            w.v3(p.position); w.v3(p.rotation); w.v3(p.scale)
            material(w, p.material)
            w.u(p.faceMaterials.size)
            p.faceMaterials.entries.sortedBy { it.key }.forEach { (f, m) -> w.u(f); material(w, m) }
            w.b((if (p.smooth) 1 else 0) or (if (p.visible) 2 else 0))
            w.b(p.uv.ordinal)
            w.u(p.mesh.vertexCount)
            p.mesh.pos.forEach { w.f(it) }
            w.u(p.mesh.faceCount)
            p.mesh.faces.forEach { face -> w.u(face.size); face.forEach { w.u(it) } }
        }
        w.u(model.strokes.size)
        for (st in model.strokes) {
            w.str(st.id); w.i(st.color); w.f(st.width); w.u(st.count)
            st.points.forEach { w.f(it) }
        }
        return compress(w.bytes())
    }

    fun decode(data: ByteArray): Model3D {
        require(data.size >= 3 && data[0] == M0 && data[1] == M1) { "3B model verisi değil" }
        require(data[2] == VERSION) { "Desteklenmeyen 3B model sürümü: ${data[2]}" }
        val r = R(inflate(data, 3))
        val id = r.str(); val name = r.str()
        val light = Light(r.f(), r.f(), r.f(), r.f(), r.i())
        val shadow = r.b() == 1; val edges = r.b() == 1; val edgeColor = r.i(); val bg = r.i(); val ground = r.b() == 1
        val camera = Camera3(r.f(), r.f(), r.f(), r.f(), r.v3())
        val parts = List(r.u()) {
            val pid = r.str(); val pname = r.str()
            val prim = if (r.b() == 1) {
                PrimitiveSpec(PrimitiveKind.entries.getOrElse(r.b()) { PrimitiveKind.CUBE }, r.u(), r.u(), r.f(), r.f())
            } else null
            val pos = r.v3(); val rot = r.v3(); val scale = r.v3()
            val mat = material(r)
            val faceMats = HashMap<Int, Material>()
            repeat(r.u()) { faceMats[r.u()] = material(r) }
            val flags = r.b()
            val uv = UvMode.entries.getOrElse(r.b()) { UvMode.BOX }
            val nv = r.u()
            val p = FloatArray(nv * 3) { r.f() }
            val faces = Array(r.u()) { IntArray(r.u()) { r.u() } }
            Part(pid, pname, Mesh(p, faces), prim, pos, rot, scale, mat, faceMats, smooth = flags and 1 != 0, uv = uv, visible = flags and 2 != 0)
        }
        val strokes = List(r.u()) {
            val sid = r.str(); val color = r.i(); val width = r.f(); val n = r.u()
            Stroke3(sid, FloatArray(n * 3) { r.f() }, color, width)
        }
        return Model3D(id, name, parts, strokes, SceneSettings(light, shadow, edges, edgeColor, bg, ground), camera)
    }

    private fun material(w: W, m: Material) {
        w.i(m.color); w.str(m.texture ?: ""); w.f(m.textureScale); w.f(m.opacity); w.b(m.kind.ordinal); w.f(m.shine)
    }

    private fun material(r: R): Material {
        val color = r.i(); val tex = r.str().ifEmpty { null }; val scale = r.f(); val opacity = r.f()
        val kind = MaterialKind.entries.getOrElse(r.b()) { MaterialKind.PLASTIC }
        return Material(color, tex, scale, opacity, kind, r.f())
    }

    private fun compress(body: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            d.setInput(body); d.finish()
            val out = ByteArrayOutputStream(body.size / 2 + 16)
            out.write(byteArrayOf(M0, M1, VERSION))
            val buf = ByteArray(8192)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            return out.toByteArray()
        } finally { d.end() }
    }

    private fun inflate(data: ByteArray, offset: Int): ByteArray {
        val inf = Inflater(true)
        try {
            inf.setInput(data, offset, data.size - offset)
            val out = ByteArrayOutputStream(data.size * 4)
            val buf = ByteArray(8192)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        } finally { inf.end() }
    }

    private class W {
        private val o = ByteArrayOutputStream()
        fun b(v: Int) = o.write(v and 0xFF)
        fun i(v: Int) { b(v shr 24); b(v shr 16); b(v shr 8); b(v) }
        fun f(v: Float) = i(v.toRawBits())
        fun v3(v: V3) { f(v.x); f(v.y); f(v.z) }
        fun u(value: Int) {
            var v = value.toLong() and 0xFFFFFFFFL
            while (v and 0x7FL.inv() != 0L) { b(((v and 0x7F) or 0x80).toInt()); v = v ushr 7 }
            b(v.toInt())
        }
        fun str(s: String) { val bytes = s.toByteArray(Charsets.UTF_8); u(bytes.size); o.write(bytes) }
        fun bytes(): ByteArray = o.toByteArray()
    }

    private class R(private val d: ByteArray) {
        private var p = 0
        fun b(): Int = d[p++].toInt() and 0xFF
        fun i(): Int = (b() shl 24) or (b() shl 16) or (b() shl 8) or b()
        fun f(): Float = Float.fromBits(i())
        fun v3() = V3(f(), f(), f())
        fun u(): Int {
            var shift = 0; var result = 0L
            while (true) {
                val x = b()
                result = result or ((x and 0x7F).toLong() shl shift)
                if (x and 0x80 == 0) return result.toInt()
                shift += 7
            }
        }
        fun str(): String { val n = u(); val s = String(d, p, n, Charsets.UTF_8); p += n; return s }
    }
}
