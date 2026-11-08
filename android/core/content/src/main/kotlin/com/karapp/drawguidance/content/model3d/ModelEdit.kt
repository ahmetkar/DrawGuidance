package com.karapp.drawguidance.content.model3d

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 3B düzenleyicide çizim düzlemi: ön (X–Y), yan (Z–Y), üst (X–Z), ekran (bakışa dik) ya da seçili yüz. */
enum class DrawPlane { FRONT, SIDE, TOP, VIEW, FACE }

/**
 * v23 3B düzenleme işlemleri (saf işlevler; her biri yeni model/parça döndürür, geri alma düzenleyicide anlık
 * görüntülerle yapılır). Dünya ötelemesi parçanın nesne uzayına döndürme ve ölçeğin tersiyle çevrilir.
 */
object ModelEdit {

    /** Yeni parçalara sırayla verilen renkler. */
    val palette = intArrayOf(
        0xFF4F8BD6.toInt(), 0xFFE07A5F.toInt(), 0xFF81B29A.toInt(), 0xFFF2CC8F.toInt(),
        0xFF9C6ADE.toInt(), 0xFF3D405B.toInt(), 0xFFE5989B.toInt(), 0xFF6EC1E4.toInt(),
    )

    fun primitiveName(kind: PrimitiveKind): String = when (kind) {
        PrimitiveKind.CUBE -> "Küp"
        PrimitiveKind.SPHERE -> "Küre"
        PrimitiveKind.CYLINDER -> "Silindir"
        PrimitiveKind.CONE -> "Koni"
        PrimitiveKind.PYRAMID -> "Piramit"
        PrimitiveKind.PRISM3 -> "Üçgen prizma"
        PrimitiveKind.PRISM6 -> "Altıgen prizma"
        PrimitiveKind.TORUS -> "Simit"
        PrimitiveKind.PLANE -> "Düzlem"
        PrimitiveKind.DOME -> "Kubbe"
        PrimitiveKind.CAPSULE -> "Hap"
        PrimitiveKind.TUBE -> "Boru"
        PrimitiveKind.WEDGE -> "Rampa"
        PrimitiveKind.STAR -> "Yıldız"
    }

    /** Yeni parça: tabanı zeminde (y = 0) duran, [at] noktasında (x, z), sıradaki renkte. */
    fun newPart(kind: PrimitiveKind, id: String, index: Int, at: V3 = V3.ZERO): Part {
        val spec = Primitives.defaultSpec(kind)
        val mesh = Primitives.build(spec)
        val b = mesh.bounds
        return Part(
            id = id,
            name = "${primitiveName(kind)} ${index + 1}",
            mesh = mesh,
            primitive = spec,
            position = V3(at.x, at.y - b.min.y, at.z),
            material = Material(color = palette[index % palette.size]),
            smooth = kind.smoothByDefault,
            uv = kind.uvByDefault,
        )
    }

    /** Cisim ayarları değişti: ağ yeniden üretilir (yüz boyamaları sıfırlanır). Düzenlenmiş ağda değişmez. */
    fun setPrimitive(part: Part, spec: PrimitiveSpec): Part {
        if (part.primitive == null) return part
        return part.copy(primitive = spec, mesh = Primitives.build(spec), faceMaterials = emptyMap())
    }

    /** Parçanın dünya kutusu. */
    fun worldBounds(part: Part): Box3 {
        val t = Transform3.of(part)
        val out = FloatArray(3)
        var ax = Float.POSITIVE_INFINITY; var ay = Float.POSITIVE_INFINITY; var az = Float.POSITIVE_INFINITY
        var bx = Float.NEGATIVE_INFINITY; var by = Float.NEGATIVE_INFINITY; var bz = Float.NEGATIVE_INFINITY
        val p = part.mesh.pos
        for (i in 0 until part.mesh.vertexCount) {
            t.apply(p[3 * i], p[3 * i + 1], p[3 * i + 2], out, 0)
            ax = min(ax, out[0]); ay = min(ay, out[1]); az = min(az, out[2])
            bx = max(bx, out[0]); by = max(by, out[1]); bz = max(bz, out[2])
        }
        return if (ax.isFinite()) Box3(V3(ax, ay, az), V3(bx, by, bz)) else Box3(part.position, part.position)
    }

    fun modelBounds(model: Model3D): Box3? {
        var box: Box3? = null
        model.parts.filter { it.visible }.forEach { box = box?.union(worldBounds(it)) ?: worldBounds(it) }
        model.strokes.forEach { s ->
            for (i in 0 until s.count) {
                val v = V3(s.points[3 * i], s.points[3 * i + 1], s.points[3 * i + 2])
                box = box?.union(Box3(v, v)) ?: Box3(v, v)
            }
        }
        return box
    }

    /** Parçayı zemine oturtur (en alt noktası y = 0). */
    fun dropToGround(part: Part): Part = part.copy(position = part.position.copy(y = part.position.y - worldBounds(part).min.y))

    fun duplicate(part: Part, newId: String): Part =
        part.copy(id = newId, name = part.name + " (kopya)", position = part.position + V3(0.35f, 0f, 0.35f))

    /** Dünya ötelemesini parçanın nesne uzayına çevirir (döndürme ve ölçeğin tersi). */
    fun worldToObjectDelta(part: Part, d: V3): V3 {
        val r = Transform3.inverseRotate(part.rotation, d)
        fun div(a: Float, s: Float) = if (abs(s) < 1e-6f) 0f else a / s
        return V3(div(r.x, part.scale.x), div(r.y, part.scale.y), div(r.z, part.scale.z))
    }

    fun moveVertices(part: Part, vertices: Collection<Int>, worldDelta: V3): Part =
        part.copy(mesh = part.mesh.moveVertices(vertices, worldToObjectDelta(part, worldDelta)), primitive = null)

    /** Yüzü normali yönünde dünya biriminde [distance] kadar uzatır; yan yüzler aynı malzemeyi alır. */
    fun extrudeFace(part: Part, face: Int, distance: Float): Part {
        if (face !in 0 until part.mesh.faceCount) return part
        val n = part.mesh.faceNormal(face)
        val k = Transform3.of(V3.ZERO, part.rotation, part.scale).applyDir(n).length.coerceAtLeast(1e-6f)
        val before = part.mesh.faceCount
        val mesh = part.mesh.extrudeFace(face, distance / k)
        return part.copy(mesh = mesh, primitive = null, faceMaterials = inheritSides(part, face, before, mesh.faceCount))
    }

    fun insetFace(part: Part, face: Int, amount: Float): Part {
        if (face !in 0 until part.mesh.faceCount) return part
        val before = part.mesh.faceCount
        val mesh = part.mesh.insetFace(face, amount)
        return part.copy(mesh = mesh, primitive = null, faceMaterials = inheritSides(part, face, before, mesh.faceCount))
    }

    fun scaleFace(part: Part, face: Int, factor: Float): Part =
        if (face !in 0 until part.mesh.faceCount) part else part.copy(mesh = part.mesh.scaleFace(face, factor), primitive = null)

    fun moveFace(part: Part, face: Int, worldDelta: V3): Part =
        if (face !in 0 until part.mesh.faceCount) part else moveVertices(part, part.mesh.faces[face].toList(), worldDelta)

    fun deleteFace(part: Part, face: Int): Part {
        if (face !in 0 until part.mesh.faceCount || part.mesh.faceCount <= 1) return part
        val remapped = HashMap<Int, Material>()
        part.faceMaterials.forEach { (f, m) -> if (f < face) remapped[f] = m else if (f > face) remapped[f - 1] = m }
        return part.copy(mesh = part.mesh.deleteFace(face), primitive = null, faceMaterials = remapped)
    }

    private fun inheritSides(part: Part, face: Int, before: Int, after: Int): Map<Int, Material> {
        val own = part.faceMaterials[face] ?: return part.faceMaterials
        val out = HashMap(part.faceMaterials)
        for (f in before until after) out[f] = own
        return out
    }

    /** Yüz boyama: yüzün kendi malzemesi (null: parçanın malzemesine döner). */
    /** v27 birden çok yüzü tek kopyayla boyar (yüzey boyamada yüzlerce yüz). */
    fun paintFaces(part: Part, faces: Collection<Int>, material: Material?): Part {
        val out = HashMap(part.faceMaterials)
        val clear = material == null || material == part.material
        for (f in faces) if (f in 0 until part.mesh.faceCount) { if (clear) out.remove(f) else out[f] = material!! }
        return part.copy(faceMaterials = out)
    }

    fun paintFace(part: Part, face: Int, material: Material?): Part {
        val out = HashMap(part.faceMaterials)
        if (material == null || material == part.material) out.remove(face) else out[face] = material
        return part.copy(faceMaterials = out)
    }

    /**
     * Parçaları tek ağda birleştirir: dönüşümler ağa işlenir, yeni parçanın konumu birleşimin taban merkezidir. Malzemesi
     * ilk parçadan; ondan farklı malzemeli yüzler kendi malzemelerini yüz boyaması olarak korur.
     */
    fun merge(parts: List<Part>, newId: String, name: String): Part? {
        if (parts.isEmpty()) return null
        val meshes = ArrayList<Mesh>()
        val mats = HashMap<Int, Material>()
        val base = parts.first().material
        var offset = 0
        parts.forEach { p ->
            val t = Transform3.of(p)
            meshes += p.mesh.mapPositions { t.apply(it) }
            for (f in 0 until p.mesh.faceCount) {
                val m = p.materialOf(f)
                if (m != base) mats[offset + f] = m
            }
            offset += p.mesh.faceCount
        }
        val world = Mesh.concat(meshes)
        val b = world.bounds
        val origin = V3(b.center.x, b.min.y, b.center.z)
        return Part(
            id = newId, name = name, mesh = world.mapPositions { it - origin }, primitive = null, position = origin,
            material = base, faceMaterials = mats, smooth = parts.all { it.smooth }, uv = parts.first().uv,
        )
    }

    // ------------------------------------------------------------------------------------------ düzlemde çizim

    /** Çizim düzleminin bir noktası ve normali. */
    fun plane(kind: DrawPlane, view: View3, origin: V3, faceNormal: V3? = null): Pair<V3, V3> = when (kind) {
        DrawPlane.FRONT -> origin to V3(0f, 0f, 1f)
        DrawPlane.SIDE -> origin to V3(1f, 0f, 0f)
        DrawPlane.TOP -> origin to V3(0f, 1f, 0f)
        DrawPlane.VIEW -> origin to view.toViewer
        DrawPlane.FACE -> origin to (faceNormal ?: view.toViewer)
    }

    /** Ekran noktasından geçen ışının düzlemle kesişimi (kenardan görünen düzlemde null). */
    fun hitPlane(view: View3, sx: Float, sy: Float, point: V3, normal: V3): V3? {
        val (o, d) = view.ray(sx, sy)
        val dn = d dot normal
        if (abs(dn) < 0.08f) return null
        val t = ((point - o) dot normal) / dn
        if (view.perspective && t <= 0f) return null
        return o + d * t
    }

    /** Seçili yüzün dünya merkezi ve normali (yüze çizmek için). */
    fun faceFrame(part: Part, face: Int): Pair<V3, V3>? {
        if (face !in 0 until part.mesh.faceCount) return null
        val t = Transform3.of(part)
        val c = t.apply(part.mesh.faceCenter(face))
        val out = FloatArray(3)
        val n = part.mesh.faceNormal(face)
        t.normal(n.x, n.y, n.z, out, 0)
        val nn = V3(out[0], out[1], out[2])
        // Çizgi yüzeyin biraz önünde dursun (yüzle iç içe geçmesin)
        return (c + nn * 0.004f) to nn
    }

    // ------------------------------------------------------------------------------------------ seçim

    /** Ekranda [x], [y]'ye en yakın görünür köşe ([radius] piksel içinde); yoksa −1. */
    fun pickVertex(part: Part, frame: Frame, x: Float, y: Float, radius: Float, partIndex: Int): Int {
        val t = Transform3.of(part)
        val w = FloatArray(3); val s = FloatArray(4)
        var best = -1; var bestD = radius * radius
        var hiddenBest = -1; var hiddenD = radius * radius
        val p = part.mesh.pos
        for (i in 0 until part.mesh.vertexCount) {
            t.apply(p[3 * i], p[3 * i + 1], p[3 * i + 2], w, 0)
            frame.view.project(w[0], w[1], w[2], s)
            if (s[3] <= 0f) continue
            val d = (s[0] - x) * (s[0] - x) + (s[1] - y) * (s[1] - y)
            val px = s[0].toInt(); val py = s[1].toInt()
            val visible = px in 0 until frame.width && py in 0 until frame.height && run {
                val k = frame.index(px, py)
                frame.depth[k] <= s[2] + 0.02f || frame.part[k] == partIndex + 1
            }
            if (visible && d < bestD) { bestD = d; best = i }
            if (!visible && d < hiddenD) { hiddenD = d; hiddenBest = i }
        }
        return if (best >= 0) best else hiddenBest
    }

    /** Parçanın köşelerinin ekran konumları (x, y, görünür mü 1/0). */
    fun screenVertices(part: Part, frame: Frame, partIndex: Int): FloatArray {
        val t = Transform3.of(part)
        val n = part.mesh.vertexCount
        val out = FloatArray(n * 3)
        val w = FloatArray(3); val s = FloatArray(4)
        val p = part.mesh.pos
        for (i in 0 until n) {
            t.apply(p[3 * i], p[3 * i + 1], p[3 * i + 2], w, 0)
            frame.view.project(w[0], w[1], w[2], s)
            out[3 * i] = s[0]; out[3 * i + 1] = s[1]
            val px = s[0].toInt(); val py = s[1].toInt()
            val visible = s[3] > 0f && px in 0 until frame.width && py in 0 until frame.height && run {
                val k = frame.index(px, py)
                frame.depth[k] <= s[2] + 0.02f || frame.part[k] == partIndex + 1
            }
            out[3 * i + 2] = if (visible) 1f else 0f
        }
        return out
    }

    /** Eksene kısıtlanmış öteleme: dünya ötelemesinin [axis] (0 X, 1 Y, 2 Z) bileşeni; −1 kısıtsız. */
    fun constrain(d: V3, axis: Int): V3 = when (axis) {
        0 -> V3(d.x, 0f, 0f)
        1 -> V3(0f, d.y, 0f)
        2 -> V3(0f, 0f, d.z)
        else -> d
    }

    /**
     * Ekrandaki sürüklemenin dünyadaki karşılığı: kısıtsızda bakışa dik düzlemde; eksende, eksenin ekrandaki izdüşümü
     * boyunca (eksen kameraya dikse ekran yönü kullanılır).
     */
    fun dragDelta(view: View3, dxPx: Float, dyPx: Float, axis: Int, atDepth: Float = 0f): V3 {
        val free = view.screenDelta(dxPx, dyPx, atDepth)
        if (axis !in 0..2) return free
        val a = when (axis) { 0 -> V3(1f, 0f, 0f); 1 -> V3(0f, 1f, 0f); else -> V3(0f, 0f, 1f) }
        val sx = a dot view.right; val sy = a dot view.down
        val len2 = sx * sx + sy * sy
        if (len2 < 0.02f) return constrain(free, axis)
        val s = view.persp(atDepth).takeIf { it > 0f } ?: 1f
        val px = (dxPx * sx + dyPx * sy) / sqrt(len2)
        return a * (px / (view.scale * s * sqrt(len2)))
    }
}
