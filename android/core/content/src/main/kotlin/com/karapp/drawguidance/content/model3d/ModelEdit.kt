package com.karapp.drawguidance.content.model3d

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
