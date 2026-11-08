package com.karapp.drawguidance.content.model3d

import kotlin.math.abs
import kotlin.math.max

/**
 * v25 parça düzeyinde katı cisim ve yapı işlemleri (3B düzenleyicinin İşlem paneli ve Nokta/Yüz araçları):
 * - [boolean]: birleştir (kaynaştır), çıkar (ilk parçadan diğerleri), kesişim,
 * - [hole]: yüzden geçen silindir delik,
 * - [slice]: düzlemle ikiye parçalama (iki taraf kapalı),
 * - [separate]: kopuk parçaları ayrı parçalara ayırma,
 * - [cut]: Nokta aracındaki halkadan kesme.
 * Malzemeler korunur (yüz boyamaları dahil); delik ve parçalamada parçanın konumu/dönüşü/ölçeği değişmez.
 * Ağ çok ayrıntılıysa önce [budget] üçgene sadeleştirilir (her cihazda makul sürede bitsin).
 */
object Solid3 {

    enum class Op { UNION, SUBTRACT, INTERSECT }

    /** Malzeme etiketleri: her ayrı malzemeye bir sıra. */
    private class Mats(base: Material) {
        val list = arrayListOf(base)
        fun tag(m: Material): Int { val i = list.indexOf(m); if (i >= 0) return i; list += m; return list.size - 1 }
    }

    private fun prepared(part: Part, budget: Int): Mesh =
        if (budget > 0 && part.mesh.triangleCount > budget) simplified(part.mesh, budget) else part.mesh

    /** Ağın üçgenleri sadeleştirilmiş kopyası. */
    fun simplified(m: Mesh, target: Int): Mesh {
        val tris = IntArray(m.triangleCount * 3)
        var k = 0
        for (f in 0 until m.faceCount) { val face = m.faces[f]; for (c in m.triangles[f]) tris[k++] = face[c] }
        val r = MeshSimplifier.simplify(TriMesh(m.pos, tris, null), target)
        return Mesh(r.pos, Array(r.tris.size / 3) { intArrayOf(r.tris[3 * it], r.tris[3 * it + 1], r.tris[3 * it + 2]) })
    }

    private fun polys(part: Part, mats: Mats, budget: Int): List<Csg.CsgPoly> {
        val mesh = prepared(part, budget)
        val sameFaces = mesh === part.mesh
        return Csg.polygons(mesh, Transform3.of(part)) { f -> mats.tag(if (sameFaces) part.materialOf(f) else part.material) }
    }

    /**
     * Parçalar üzerinde birleştir/çıkar/kesişim: sonuç dünya uzayında yeni parça (konumu tabanının ortası). Sonuç boşsa
     * (ör. kesişmeyen parçalarda kesişim) null.
     */
    fun boolean(parts: List<Part>, op: Op, newId: String, name: String, budget: Int = 0): Part? {
        if (parts.size < 2) return null
        val base = parts.first()
        val mats = Mats(base.material)
        val each = if (budget > 0) budget / parts.size else 0
        var acc = polys(base, mats, each)
        for (p in parts.drop(1)) {
            val b = polys(p, mats, each)
            acc = when (op) {
                Op.UNION -> Csg.union(acc, b)
                Op.SUBTRACT -> Csg.subtract(acc, b)
                Op.INTERSECT -> Csg.intersect(acc, b)
            }
        }
        return worldPart(acc, mats, base, newId, name)
    }

    private fun worldPart(polys: List<Csg.CsgPoly>, mats: Mats, base: Part, id: String, name: String): Part? {
        val tags = ArrayList<Int>()
        val world = Csg.toMesh(polys, tags)
        if (world.faceCount == 0) return null
        val b = world.bounds
        val origin = V3(b.center.x, b.min.y, b.center.z)
        return Part(
            id = id, name = name, mesh = world.mapPositions { it - origin }, primitive = null, position = origin,
            material = mats.list[0], faceMaterials = faceMats(tags, mats), smooth = base.smooth, uv = if (base.uv == UvMode.MESH) UvMode.BOX else base.uv,
        )
    }

    private fun faceMats(tags: List<Int>, mats: Mats): Map<Int, Material> {
        val out = HashMap<Int, Material>()
        tags.forEachIndexed { f, t -> if (t != 0) out[f] = mats.list[t] }
        return out
    }

    /** Dünya ağını parçanın kendi uzayına geri çevirerek parçayı günceller (konum, dönüş, ölçek korunur). */
    private fun localPart(part: Part, polys: List<Csg.CsgPoly>, mats: Mats): Part? {
        val tags = ArrayList<Int>()
        val world = Csg.toMesh(polys, tags)
        if (world.faceCount == 0) return null
        val inv = Inverse3.of(Transform3.of(part)) ?: return null
        return part.copy(
            mesh = world.mapPositions { inv.point(it) }, primitive = null, material = mats.list[0], faceMaterials = faceMats(tags, mats),
            uv = if (part.uv == UvMode.MESH) UvMode.BOX else part.uv,
        )
    }

    /**
     * Delik: [center] noktasından [axis] (dünya, birim; yüzün dışa normali) yönünde parçayı boydan boya geçen [radius]
     * yarıçaplı silindir çıkarılır.
     */
    fun hole(part: Part, center: V3, axis: V3, radius: Float, segments: Int = 24, budget: Int = 0): Part? {
        val mats = Mats(part.material)
        val a = polys(part, mats, budget)
        val ext = ModelEdit.worldBounds(part).radius * 2.2f + 0.1f
        val n = axis.normalized()
        val cutter = Generators3D.cylinder(center + n * (ext * 0.5f), center - n * ext, radius, segments)
        val cut = Csg.polygons(cutter, null) { mats.tag(part.materialOf(0)) }
        return localPart(part, Csg.subtract(a, cut), mats)
    }

    /**
     * Düzlemle ikiye parçalar: [point] ve [normal] (dünya). Her iki taraf kesik yerinden kapatılır. Düzlem parçayı
     * kesmiyorsa null. Birinci parça normalin gösterdiği taraftır.
     */
    fun slice(part: Part, point: V3, normal: V3, budget: Int = 0): Pair<Part, Part>? {
        val mats = Mats(part.material)
        val a = polys(part, mats, budget)
        val n = normal.normalized()
        val r = ModelEdit.worldBounds(part).radius * 4f + 1f
        val t1 = (if (abs(n.y) < 0.9f) V3.UP else V3(1f, 0f, 0f)).let { (it - n * (it dot n)).normalized() }
        val t2 = (t1 cross n).normalized()
        fun half(sign: Float): List<Csg.CsgPoly> {
            val box = Primitives.cube().mapPositions { c ->
                point + t1 * (c.x * 2f * r) + n * (sign * (c.y + 0.5f) * r) + t2 * (c.z * 2f * r)
            }.let { if (sign < 0f) Mesh(it.pos, Array(it.faceCount) { f -> it.faces[f].reversedArray() }) else it }
            return Csg.polygons(box, null) { mats.tag(part.material) }
        }
        val front = localPart(part, Csg.intersect(a, half(1f)), mats) ?: return null
        val back = localPart(part, Csg.intersect(a, half(-1f)), mats) ?: return null
        return front to back
    }

    /** Kopuk parçaları ayırır (tek parçaysa boş liste). */
    fun separate(part: Part, newId: () -> String): List<Part> {
        val comps = MeshTopo.components(part.mesh)
        if (comps.size < 2) return emptyList()
        return comps.sortedByDescending { it.size }.mapIndexed { i, faces ->
            val sub = MeshTopo.subMesh(part.mesh, faces)
            part.copy(
                id = if (i == 0) part.id else newId(), name = if (i == 0) part.name else "${part.name} (${i + 1})",
                mesh = sub.mesh, primitive = null, faceMaterials = remap(part, sub.origin),
            )
        }
    }

    /**
     * Nokta aracındaki halkadan keser: kalan şekil parçanın yerinde kalır, kesilen parça yeni parça olur ve görünsün diye
     * biraz uzaklaştırılır. Halka yüzeyi ikiye ayırmıyorsa null.
     */
    fun cut(part: Part, loop: List<Int>, newId: String): Pair<Part, Part>? {
        val r = MeshTopo.cutLoop(part.mesh, loop) ?: return null
        val rest = part.copy(mesh = r.rest.mesh, primitive = null, faceMaterials = remap(part, r.rest.origin))
        // Kesilen parça görünsün diye aralanır (bakış yönündeki kesiklerde de seçilebilsin)
        val gap = MeshOps.extent(part.mesh) * 0.16f
        val piece = part.copy(
            id = newId, name = "${part.name} (kesik)",
            mesh = r.piece.mesh.mapPositions { it + r.away * gap }, primitive = null, faceMaterials = remap(part, r.piece.origin),
        )
        return rest to piece
    }

    /** Yeni yüzlerin malzemeleri: kaynak yüzün boyaması. */
    fun remap(part: Part, origin: IntArray): Map<Int, Material> {
        if (part.faceMaterials.isEmpty()) return emptyMap()
        val out = HashMap<Int, Material>()
        origin.forEachIndexed { f, o -> if (o >= 0) part.faceMaterials[o]?.let { out[f] = it } }
        return out
    }

    /** Parçayı yapı değiştiren bir ağ işleminin sonucuyla günceller. */
    fun apply(part: Part, r: TopoResult): Part = part.copy(mesh = r.mesh, primitive = null, faceMaterials = remap(part, r.origin))

    /** İşlem için toplam üçgen bütçesi (cihaz sınıfına göre çağıran verir). */
    fun estimate(parts: List<Part>): Int = parts.sumOf { it.mesh.triangleCount }

    @Suppress("unused")
    private fun maxOf3(a: Float, b: Float, c: Float) = max(a, max(b, c))
}
