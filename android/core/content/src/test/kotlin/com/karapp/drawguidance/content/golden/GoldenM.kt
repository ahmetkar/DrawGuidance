package com.karapp.drawguidance.content.golden

import com.karapp.drawguidance.content.model3d.*
import org.junit.Test
import java.io.File

class GoldenM {
    private fun hm(m: Mesh): String {
        var h = 17L
        for (f in m.pos) h = h * 31 + f.toRawBits()
        for (face in m.faces) { h = h * 31 + face.size; for (v in face) h = h * 31 + v }
        return "v=${m.vertexCount} f=${m.faceCount} t=${m.triangleCount} h=${java.lang.Long.toHexString(h)}"
    }
    private fun hp(p: Part?): String = if (p == null) "null" else "${hm(p.mesh)} pos=${p.position} fm=${p.faceMaterials.keys.sorted().take(6)}"

    @Test fun run() {
        val sb = StringBuilder()
        for (k in PrimitiveKind.entries) sb.append("$k ${hm(Primitives.build(Primitives.defaultSpec(k)))} closed=${Primitives.build(Primitives.defaultSpec(k)).closed}\n")
        sb.append("sph ${hm(Primitives.sphere(13, 7))}\n")
        val c = Primitives.cube()
        sb.append("extrude ${hm(c.extrudeFace(0, 0.5f))}\n")
        sb.append("inset ${hm(c.insetFace(2, 0.3f))}\n")
        sb.append("scale ${hm(c.scaleFace(3, 1.4f))}\n")
        sb.append("del ${hm(c.deleteFace(1))}\n")
        sb.append("bend ${hm(MeshOps.bend(c, listOf(EdgeRef.of(4, 5), EdgeRef.of(5, 6)), 0.3f))}\n")
        sb.append("stretch ${hm(MeshOps.stretch(c, listOf(EdgeRef.of(4, 5)), 1.5f))}\n")
        sb.append("push ${hm(MeshOps.push(c, listOf(EdgeRef.of(4, 5), EdgeRef.of(0, 1)), 0.2f))}\n")
        sb.append("round ${MeshOps.round(c, EdgeRef.of(4, 5), 0.1f)?.let { hm(it) }}\n")
        sb.append("split ${hm(MeshOps.splitEdge(c, EdgeRef.of(2, 6), 0.3f).first)}\n")
        val sphere = Primitives.build(Primitives.defaultSpec(PrimitiveKind.SPHERE))
        sb.append("delv ${MeshTopo.deleteVertex(c, 3)?.let { hm(it.mesh) + " " + it.origin.toList() }}\n")
        sb.append("delv2 ${MeshTopo.deleteVertex(sphere, 0)?.let { hm(it.mesh) }}\n")
        sb.append("conn ${MeshTopo.connect(c, 4, 6)?.let { hm(it.mesh) }}\n")
        val cyl = Primitives.build(Primitives.defaultSpec(PrimitiveKind.CYLINDER))
        val region = MeshTopo.surfaceRegion(cyl, 0)
        sb.append("region ${region.toList()}\n")
        sb.append("exreg ${hm(MeshTopo.extrudeRegion(cyl, region, 0.2f).mesh)}\n")
        sb.append("screg ${hm(MeshTopo.scaleRegion(cyl, region, 0.8f))}\n")
        // halka: silindirin orta kesiti yok; küpün bir yüz kuşağı
        val ring = listOf(0, 1, 2, 3)
        sb.append("cut ${MeshTopo.cutLoop(c, ring)?.let { hm(it.rest.mesh) + " / " + hm(it.piece.mesh) + " " + it.away }}\n")
        sb.append("comps ${MeshTopo.components(Mesh.concat(listOf(c, sphere))).map { it.size }}\n")
        // CSG
        val a = ModelEdit.newPart(PrimitiveKind.CUBE, "a", 0)
        val b = ModelEdit.newPart(PrimitiveKind.SPHERE, "b", 1, V3(0.4f, 0.3f, 0.2f))
        sb.append("union ${hp(Solid3.boolean(listOf(a, b), Solid3.Op.UNION, "u", "U"))}\n")
        sb.append("sub ${hp(Solid3.boolean(listOf(a, b), Solid3.Op.SUBTRACT, "s", "S"))}\n")
        sb.append("int ${hp(Solid3.boolean(listOf(a, b), Solid3.Op.INTERSECT, "i", "I"))}\n")
        sb.append("hole ${hp(Solid3.hole(a, V3(0f, 1f, 0f), V3(0f, 1f, 0f), 0.2f))}\n")
        val sl = Solid3.slice(a.copy(rotation = V3(10f, 20f, 5f)), V3(0.1f, 0.5f, 0f), V3(1f, 0.3f, 0f))
        sb.append("slice ${hp(sl?.first)} | ${hp(sl?.second)}\n")
        sb.append("sep ${Solid3.separate(ModelEdit.merge(listOf(a, b.copy(position = V3(3f, 0f, 0f))), "m", "M")!!) { "n" }.map { hp(it) }}\n")
        // simplify
        val big = Primitives.sphere(48, 24)
        sb.append("simp ${hm(Solid3.simplified(big, 300))}\n")
        sb.append("simp2 ${hm(Solid3.simplified(Primitives.torus(40, 20, 0.4f), 150))}\n")
        // polyhedra & generators
        sb.append("tet ${hm(Generators3D.tetrahedron())} oct ${hm(Generators3D.octahedron())} ico ${hm(Generators3D.icosahedron())} dod ${hm(Generators3D.dodecahedron())}\n")
        sb.append("arrow ${hm(Generators3D.arrow(V3(0f, 0f, 0f), V3(1f, 2f, 0.5f), 0.05f))}\n")
        val path = List(40) { i -> val t = i / 39f; V3(t * 2f - 1f, 0.3f + 0.4f * kotlin.math.sin(t * 6f), 0.1f * t) }
        sb.append("tubeg ${hm(Generators3D.tube(path, 0.08f))}\n")
        // brush
        val circle = List(60) { i -> val t = i / 60.0 * 2 * Math.PI; V3((0.5 * Math.cos(t) + 0.1 * Math.cos(3 * t)).toFloat(), (0.5 * Math.sin(t)).toFloat(), 0f) }
        val prs = FloatArray(40) { 0.3f + 0.7f * it / 39f }
        for (kind in listOf(Brush3Kind.TUBE, Brush3Kind.RIBBON, Brush3Kind.WALL, Brush3Kind.LATHE, Brush3Kind.INFLATE)) {
            val spec = BrushShapeSpec(kind, 0.1f)
            val open = BrushShapes.build(BrushInput(path, prs, V3(0f, 0f, 1f), V3.UP), spec)
            val closed = BrushShapes.build(BrushInput(circle, null, V3(0f, 0f, 1f), V3.UP), spec)
            sb.append("brush $kind ${open?.let { hm(it) }} | ${closed?.let { hm(it) }}\n")
        }
        val inflBig = BrushShapes.build(BrushInput(circle, null, V3(0f, 0f, 1f), V3.UP), BrushShapeSpec(Brush3Kind.INFLATE, 0.1f, maxSamples = 240))
        sb.append("inflate3 ${inflBig?.let { hm(it) }}\n")
        // star-dışı şişirme
        val cshape = List(50) { i -> val t = i / 50.0 * 2 * Math.PI; val r = 0.5 + 0.35 * Math.cos(2 * t); V3((r * Math.cos(t)).toFloat(), (r * Math.sin(t)).toFloat(), 0f) }
        sb.append("inflateC ${BrushShapes.build(BrushInput(cshape, null, V3(0f, 0f, 1f), V3.UP), BrushShapeSpec(Brush3Kind.INFLATE, 0.1f))?.let { hm(it) }}\n")
        // textures
        for (d in TextureCatalog.all) {
            val t = d.generate(64)
            var h = 7L
            for (p in t.px) h = h * 31 + p
            sb.append("tex ${d.id} ${java.lang.Long.toHexString(h)} avg=${Integer.toHexString(TextureCatalog.average(t))}\n")
        }
        // render
        val model = Model3D(
            "m", "M",
            listOf(
                a.copy(material = Material(color = 0xFF4F8BD6.toInt(), texture = "wood.oak")),
                b.copy(material = Material(color = 0xFFE07A5F.toInt(), kind = MaterialKind.METAL, shine = 0.8f)),
                ModelEdit.newPart(PrimitiveKind.TORUS, "c", 2, V3(-0.8f, 0f, 0.3f)).copy(material = Material(opacity = 0.5f, kind = MaterialKind.GLASS)),
                ModelEdit.newPart(PrimitiveKind.CONE, "d", 3, V3(0.9f, 0f, -0.5f)).copy(material = Material(color = 0xFF81B29A.toInt(), gradient = 0xFFF2CC8F.toInt())),
            ),
            strokes = listOf(Stroke3("s1", floatArrayOf(-1f, 1f, 0f, 0f, 1.5f, 0f, 1f, 1f, 0.2f), 0xFF222222.toInt(), 0.03f)),
        )
        val texCache = HashMap<String, Texture>()
        for (persp in listOf(0.35f, 0f)) {
            val view = View3.fit(model, model.camera.copy(persp = persp), 96, 72)
            val fr = Renderer.render(model, view, RenderOptions(textures = { id -> texCache.getOrPut(id) { TextureCatalog.byId(id)!!.generate(64) } }, selectedPart = "b", selectedFaces = setOf(3, 4)))
            var h = 7L; var hp = 7L
            for (p in fr.color) h = h * 31 + p
            for (i in fr.part.indices) hp = hp * 31 + fr.part[i] * 1000 + fr.face[i]
            sb.append("render $persp ${java.lang.Long.toHexString(h)} ${java.lang.Long.toHexString(hp)} pick=${fr.pick(48f, 36f, 2)}\n")
            val ol = Renderer.outlines(model, view)
            var ho = 7L
            for (pl in ol) { ho = ho * 31 + pl.size; for (v in pl) ho = ho * 31 + v.toRawBits() }
            sb.append("outlines ${ol.size} ${java.lang.Long.toHexString(ho)}\n")
            val hit = Pick3.pick(model, view, 48f, 36f)
            sb.append("pick3 $hit v=${Pick3.vertex(model, view, 0, 40f, 30f, 30f)} e=${Pick3.edge(model, view, 0, 40f, 30f, 30f)}\n")
            sb.append("pickV ${ModelEdit.pickVertex(model.parts[0], fr, 40f, 30f, 30f, 0)}\n")
        }
        File("/tmp/claude-0/golden/k_GM.txt").writeText(sb.toString())
    }
}
