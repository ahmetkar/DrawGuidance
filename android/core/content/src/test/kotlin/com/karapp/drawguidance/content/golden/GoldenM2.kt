package com.karapp.drawguidance.content.golden

import com.karapp.drawguidance.content.model3d.*
import org.junit.Test
import java.io.File
import kotlin.random.Random

class GoldenM2 {
    private fun hm(m: Mesh): String {
        var h = 17L
        for (f in m.pos) h = h * 31 + f.toRawBits()
        for (face in m.faces) { h = h * 31 + face.size; for (v in face) h = h * 31 + v }
        return "v=${m.vertexCount} f=${m.faceCount} h=${java.lang.Long.toHexString(h)}"
    }
    private fun hb(b: ByteArray): String { var h = 7L; for (x in b) h = h * 31 + x; return "${b.size}:${java.lang.Long.toHexString(h)}" }
    private fun hModel(m: Model3D): String {
        var h = 7L
        for (p in m.parts) {
            h = h * 31 + p.id.hashCode(); h = h * 31 + p.name.hashCode()
            for (f in p.mesh.pos) h = h * 31 + f.toRawBits()
            for (face in p.mesh.faces) for (v in face) h = h * 31 + v
            for (v in listOf(p.position, p.rotation, p.scale)) { h = h * 31 + v.x.toRawBits(); h = h * 31 + v.y.toRawBits(); h = h * 31 + v.z.toRawBits() }
            val mt = p.material
            h = h * 31 + mt.color; h = h * 31 + (mt.texture?.hashCode() ?: 0); h = h * 31 + mt.textureScale.toRawBits(); h = h * 31 + mt.opacity.toRawBits(); h = h * 31 + mt.kind.ordinal; h = h * 31 + mt.shine.toRawBits()
            h = h * 31 + (if (p.smooth) 1 else 0); h = h * 31 + p.uv.ordinal
        }
        for (s in m.strokes) { h = h * 31 + s.id.hashCode(); for (f in s.points) h = h * 31 + f.toRawBits(); h = h * 31 + s.color; h = h * 31 + s.width.toRawBits() }
        val c = m.camera
        for (f in listOf(c.yaw, c.pitch, c.persp, c.zoom, c.target.x, c.target.y, c.target.z)) h = h * 31 + f.toRawBits()
        return "p=${m.parts.size} s=${m.strokes.size} t=${m.triangleCount} h=${java.lang.Long.toHexString(h)}"
    }

    @Test fun run() {
        val sb = StringBuilder()
        for (it in Samples3D.all) sb.append("sample ${it.id} ${hModel(it)}\n")
        for (it in Catalog3D.all) sb.append("cat ${it.id} ${it.category} ${hModel(it.build())}\n")
        // glTF
        val a = ModelEdit.newPart(PrimitiveKind.CUBE, "a", 0).copy(faceMaterials = mapOf(2 to Material(color = 0xFF112233.toInt(), kind = MaterialKind.GLOW)), group = "g1")
        val b = ModelEdit.newPart(PrimitiveKind.SPHERE, "b", 1, V3(0.4f, 0.3f, 0.2f)).copy(rotation = V3(10f, 20f, 30f), smooth = true, material = Material(texture = "wood.oak", textureScale = 0.5f, gradient = 0xFF00FF00.toInt()))
        val circle = List(30) { i -> val t = i / 30.0 * 2 * Math.PI; V3((0.5 * Math.cos(t)).toFloat(), (0.5 * Math.sin(t)).toFloat(), 0f) }
        val bs = BrushShapeSpec(Brush3Kind.TUBE, 0.1f)
        val bm = BrushShapes.build(BrushInput(circle, null, V3(0f, 0f, 1f), V3.UP), bs)!!
        val pts = FloatArray(circle.size * 3).also { arr -> circle.forEachIndexed { i, p -> arr[3 * i] = p.x; arr[3 * i + 1] = p.y; arr[3 * i + 2] = p.z } }
        val c = Part("c", "Fırça \"x\"\n", bm, null, brush = BrushSource(bs, pts, null, V3(0f, 0f, 1f), V3.UP, true, null, 123L, MeshSig.of(bm)))
        val model = Model3D("m1", "Model ç", listOf(a, b, c), listOf(Stroke3("s1", floatArrayOf(-1f, 1f, 0f, 0f, 1.5f, 0f, 1f, 1f, 0.2f), 0xFF222222.toInt(), 0.03f, 99L)), groups = listOf(Group3("g1", "Grup 1")))
        val glb = Gltf3.write(model)
        sb.append("glb ${hb(glb)}\n")
        val jl = (glb[12].toInt() and 0xFF) or ((glb[13].toInt() and 0xFF) shl 8) or ((glb[14].toInt() and 0xFF) shl 16)
        File("/tmp/claude-0/golden/k_GM2.json").writeText(String(glb, 20, jl, Charsets.UTF_8))
        val back = Gltf3.read(glb)!!
        sb.append("read ${hModel(back)} groups=${back.groups} brush=${back.parts[2].brush?.spec} sig=${back.parts[2].brush?.sig} fm=${back.parts[0].faceMaterials} grp=${back.parts[0].group}\n")
        sb.append("rewrite ${hb(Gltf3.write(back))}\n")
        for (it in Catalog3D.all.take(12)) sb.append("glbcat ${it.id} ${hb(Gltf3.write(it.build()))}\n")
        sb.append("quat ${Gltf3.quaternion(V3(10f, 20f, 30f))} ${Gltf3.quaternion(V3(-90f, -90f, 0f))}\n")
        // import foreign (strip extras) — build a foreign GLB from our own by removing asset.extras
        val imp = Gltf3.import(glb, "x", 500, { _, _ -> null }, newId = { "id" })
        sb.append("importOwn ${hModel(imp.model)} ${imp.sourceTriangles}\n")
        // Sketch3
        val sm = SketchModel.create()
        val rnd = Random(55)
        for (cl in SketchClass.entries) {
            val (xs, ys) = SketchSynth.sample(cl, rnd)
            val f = SketchFeatures.of(xs, ys)
            var hf = 7L
            f?.forEach { hf = hf * 31 + it.toRawBits() }
            val r = f?.let { sm.suggest(it, Brush3Kind.LINE) }
            sb.append("sk $cl n=${xs.size} f=${java.lang.Long.toHexString(hf)} cls=${r?.sketchClass} conf=${r?.confidence} sug=${r?.suggestions}\n")
            if (f != null && r != null && r.suggestions.isNotEmpty()) { sm.learnAccept(f, r.suggestions[0].kind); sm.learnDismiss(f, r.suggestions.drop(1).map { it.kind }) }
        }
        val enc = sm.encode()
        sb.append("enc ${enc.length} ${enc.hashCode()}\n")
        val sm2 = SketchModel.create(); sm2.decode(enc)
        sb.append("enc2 ${sm2.encode() == enc}\n")
        // fits
        val rnd2 = Random(77)
        for (cl in SketchClass.entries) {
            val (xs, ys) = SketchSynth.sample(cl, rnd2)
            val p3 = xs.indices.map { V3(xs[it] * 0.3f + 0.1f, ys[it] * 0.3f + 0.5f, 0.2f) }
            val sk = Sketch2.of(p3, V3(0.1f, 0.2f, 1f), V3.UP)!!
            for (k in Suggest3Kind.entries) {
                val part = Sketch3Fit.solid(k, sk, "x", 0, Material())
                val br = Sketch3Fit.brush(k, sk, p3)
                if (part != null) sb.append("fit $cl $k ${part.position} ${part.rotation} ${part.scale} ${part.primitive}\n")
                if (br != null && k == Suggest3Kind.SPRING) { var h = 7L; br.input.points.forEach { h = h * 31 + it.x.toRawBits() + it.y.toRawBits() }; sb.append("spring $cl ${br.input.points.size} ${java.lang.Long.toHexString(h)} ${br.width}\n") }
            }
        }
        File("/tmp/claude-0/golden/k_GM2.txt").writeText(sb.toString())
    }
}
