package com.karapp.drawguidance.content.model3d

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** glTF okunamadı (bozuk dosya ya da desteklenmeyen özellik). */
class GltfException(message: String) : Exception(message)

/**
 * GLB yazma ayarları: [embedTextures] dokular PNG olarak dosyaya gömülsün mü (dışa aktarma; uygulamanın kendi kaydında
 * hazır dokular kimlikleriyle anılır), [textures] doku kimliğinden doku.
 */
class GltfWriteOptions(
    val embedTextures: Boolean = false,
    val textures: (String) -> Texture? = { null },
)

/** İçe aktarılan modelin gömülü dokusu (uygulamada "imp.…" kimliğiyle saklanır). */
class ImportedTexture(val id: String, val name: String, val texture: Texture)

/** İçe aktarma sonucu: model, dokuları, kaynak ve sonuç üçgen sayıları. */
class GltfImport(val model: Model3D, val textures: List<ImportedTexture>, val sourceTriangles: Int, val triangles: Int)

/**
 * v24 3B modellerin glTF 2.0 / GLB biçimi (saf Kotlin; dış kütüphane yok). Uygulama tüm 3B modelleri bu biçimde saklar
 * ve dışa aktarır: her parça bir düğüm (konum, döndürme dördeyi, ölçek) ve malzemelere göre üçgen ilkelleri
 * (POSITION, NORMAL, TEXCOORD_0, dizinler); malzemeler PBR (temel renk, metal, pürüzlülük, saydamlık, ışıma); 3B
 * çizgiler LINE_STRIP. Uygulamaya özgü düzenleme bilgileri (çokgen yüzler, cisim ayarları, yüz boyaları, sahne, kamera)
 * glTF'in "extras" alanında ve ayrı bir tampon görünümünde durur: başka programlar dosyayı normal bir GLB olarak açar,
 * uygulama kayıpsız geri okur. Başka programların glTF/GLB dosyaları [import] ile okunur: ayrıntı düşürülür
 * ([MeshSimplifier]), boyutu ve konumu uygulamanın ölçeğine getirilir, dokular küçültülür.
 */
object Gltf3 {

    const val MIME = "model/gltf-binary"
    private const val MAGIC = 0x46546C67
    private const val JSON_CHUNK = 0x4E4F534A
    private const val BIN_CHUNK = 0x004E4942
    private const val FLOAT = 5126
    private const val UBYTE = 5121
    private const val USHORT = 5123
    private const val UINT = 5125
    private const val BYTE = 5120
    private const val SHORT = 5122
    private const val ARRAY_BUFFER = 34962
    private const val ELEMENT_ARRAY_BUFFER = 34963
    private const val DG_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    /** Verinin GLB olup olmadığı. */
    fun isGlb(data: ByteArray): Boolean = data.size >= 12 && le(data, 0) == MAGIC

    // =========================================================================================== yazma

    fun write(model: Model3D, options: GltfWriteOptions = GltfWriteOptions()): ByteArray = Writer(model, options).build()

    private class Writer(val model: Model3D, val options: GltfWriteOptions) {
        val bin = ByteArrayOutputStream()
        val views = ArrayList<JsonElement>()
        val accessors = ArrayList<JsonElement>()
        val materials = ArrayList<JsonElement>()
        val materialIndex = HashMap<Material, Int>()
        val images = ArrayList<JsonElement>()
        val textures = ArrayList<JsonElement>()
        val textureIndex = HashMap<String, Int>()
        val meshes = ArrayList<JsonElement>()
        val nodes = ArrayList<JsonElement>()
        var unlit = false

        fun view(bytes: ByteArray, target: Int?): Int {
            while (bin.size() % 4 != 0) bin.write(0)
            val offset = bin.size()
            bin.write(bytes)
            val m = LinkedHashMap<String, JsonElement>()
            m["buffer"] = JsonPrimitive(0); m["byteOffset"] = JsonPrimitive(offset); m["byteLength"] = JsonPrimitive(bytes.size)
            if (target != null) m["target"] = JsonPrimitive(target)
            views += JsonObject(m)
            return views.size - 1
        }

        fun floats(data: FloatArray): ByteArray {
            val bb = ByteBuffer.allocate(data.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            data.forEach { bb.putFloat(it) }
            return bb.array()
        }

        fun accessor(view: Int, componentType: Int, count: Int, type: String, min: FloatArray? = null, max: FloatArray? = null): Int {
            val m = LinkedHashMap<String, JsonElement>()
            m["bufferView"] = JsonPrimitive(view); m["componentType"] = JsonPrimitive(componentType)
            m["count"] = JsonPrimitive(count); m["type"] = JsonPrimitive(type)
            if (min != null) m["min"] = JsonArray(min.map { JsonPrimitive(it) })
            if (max != null) m["max"] = JsonArray(max.map { JsonPrimitive(it) })
            accessors += JsonObject(m)
            return accessors.size - 1
        }

        fun vec3(data: FloatArray, withBounds: Boolean): Int {
            val n = data.size / 3
            var mn: FloatArray? = null; var mx: FloatArray? = null
            if (withBounds) {
                mn = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE); mx = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
                for (i in 0 until n) for (k in 0..2) { mn[k] = min(mn[k], data[3 * i + k]); mx[k] = max(mx[k], data[3 * i + k]) }
                if (n == 0) { mn = floatArrayOf(0f, 0f, 0f); mx = floatArrayOf(0f, 0f, 0f) }
            }
            return accessor(view(floats(data), ARRAY_BUFFER), FLOAT, n, "VEC3", mn, mx)
        }

        fun indices(idx: IntArray, vertexCount: Int): Int {
            return if (vertexCount <= 65535) {
                val bb = ByteBuffer.allocate(idx.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                idx.forEach { bb.putShort(it.toShort()) }
                accessor(view(bb.array(), ELEMENT_ARRAY_BUFFER), USHORT, idx.size, "SCALAR")
            } else {
                val bb = ByteBuffer.allocate(idx.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                idx.forEach { bb.putInt(it) }
                accessor(view(bb.array(), ELEMENT_ARRAY_BUFFER), UINT, idx.size, "SCALAR")
            }
        }

        fun textureFor(id: String): Int? {
            if (!options.embedTextures) return null
            textureIndex[id]?.let { return it }
            val t = options.textures(id) ?: return null
            val png = PngWriter.encode(t)
            val img = view(png, null)
            images += JsonObject(mapOf("bufferView" to JsonPrimitive(img), "mimeType" to JsonPrimitive("image/png"), "name" to JsonPrimitive(id)))
            textures += JsonObject(mapOf("source" to JsonPrimitive(images.size - 1), "sampler" to JsonPrimitive(0)))
            textureIndex[id] = textures.size - 1
            return textures.size - 1
        }

        fun material(mat: Material): Int {
            materialIndex[mat]?.let { return it }
            val r = (mat.color shr 16 and 0xFF) / 255f; val g = (mat.color shr 8 and 0xFF) / 255f; val b = (mat.color and 0xFF) / 255f
            val alpha = when (mat.kind) {
                MaterialKind.GLASS -> (mat.opacity * 0.5f).coerceIn(0.1f, 1f)
                else -> mat.opacity
            }
            val pbr = LinkedHashMap<String, JsonElement>()
            pbr["baseColorFactor"] = JsonArray(listOf(toLinear(r), toLinear(g), toLinear(b), alpha).map { JsonPrimitive(it) })
            pbr["metallicFactor"] = JsonPrimitive(if (mat.kind == MaterialKind.METAL) 1f else 0f)
            pbr["roughnessFactor"] = JsonPrimitive(
                when (mat.kind) {
                    MaterialKind.MATTE -> 1f
                    MaterialKind.PLASTIC -> 1f - 0.7f * mat.shine
                    MaterialKind.METAL -> 0.6f - 0.5f * mat.shine
                    MaterialKind.GLASS -> 0.05f
                    MaterialKind.GLOW -> 1f
                },
            )
            mat.texture?.let { id -> textureFor(id)?.let { pbr["baseColorTexture"] = JsonObject(mapOf("index" to JsonPrimitive(it))) } }
            val m = LinkedHashMap<String, JsonElement>()
            m["name"] = JsonPrimitive(mat.texture ?: String.format("#%06X", 0xFFFFFF and mat.color))
            m["pbrMetallicRoughness"] = JsonObject(pbr)
            if (alpha < 0.999f) m["alphaMode"] = JsonPrimitive("BLEND")
            if (mat.kind == MaterialKind.GLOW) m["emissiveFactor"] = JsonArray(listOf(toLinear(r), toLinear(g), toLinear(b)).map { JsonPrimitive(it) })
            m["extras"] = JsonObject(mapOf("dg" to materialExtras(mat)))
            materials += JsonObject(m)
            materialIndex[mat] = materials.size - 1
            return materials.size - 1
        }

        fun part(p: Part) {
            val mesh = p.mesh
            val primitives = ArrayList<JsonElement>()
            if (mesh.faceCount > 0) {
                val uvs = UvMapper.compute(p)
                val corners = if (p.smooth) mesh.cornerNormals(0.7f) else null
                val fn = mesh.faceNormals
                // Malzemeye göre gruplar
                val groups = LinkedHashMap<Int, MutableList<Int>>()
                for (f in 0 until mesh.faceCount) groups.getOrPut(material(p.materialOf(f))) { ArrayList() }.add(f)
                for ((mi, faces) in groups) {
                    val mat = p.materialOf(faces.first())
                    val ts = 1f / mat.textureScale.coerceAtLeast(0.01f)
                    val keys = HashMap<VKey, Int>()
                    val pos = FloatList(); val nrm = FloatList(); val tex = FloatList(); val idx = IntList()
                    for (f in faces) {
                        val face = mesh.faces[f]
                        val tris = mesh.triangles[f]
                        for (t in tris) {
                            val v = face[t]
                            val nx: Float; val ny: Float; val nz: Float
                            if (corners != null) { nx = corners[f][3 * t]; ny = corners[f][3 * t + 1]; nz = corners[f][3 * t + 2] }
                            else { nx = fn[3 * f]; ny = fn[3 * f + 1]; nz = fn[3 * f + 2] }
                            val u = uvs[f][2 * t] * ts; val w = uvs[f][2 * t + 1] * ts
                            val key = VKey(v, nx.toBits(), ny.toBits(), nz.toBits(), u.toBits(), w.toBits())
                            val existing = keys[key]
                            if (existing != null) { idx.add(existing); continue }
                            val k = pos.size / 3
                            pos.add(mesh.pos[3 * v]); pos.add(mesh.pos[3 * v + 1]); pos.add(mesh.pos[3 * v + 2])
                            nrm.add(nx); nrm.add(ny); nrm.add(nz)
                            tex.add(u); tex.add(w)
                            keys[key] = k
                            idx.add(k)
                        }
                    }
                    if (idx.size == 0) continue
                    val n = pos.size / 3
                    val attrs = LinkedHashMap<String, JsonElement>()
                    attrs["POSITION"] = JsonPrimitive(vec3(pos.toArray(), true))
                    attrs["NORMAL"] = JsonPrimitive(vec3(nrm.toArray(), false))
                    attrs["TEXCOORD_0"] = JsonPrimitive(accessor(view(floats(tex.toArray()), ARRAY_BUFFER), FLOAT, n, "VEC2"))
                    primitives += JsonObject(
                        mapOf("attributes" to JsonObject(attrs), "indices" to JsonPrimitive(indices(idx.toArray(), n)), "material" to JsonPrimitive(mi), "mode" to JsonPrimitive(4)),
                    )
                }
            }
            val node = LinkedHashMap<String, JsonElement>()
            node["name"] = JsonPrimitive(p.name)
            if (primitives.isNotEmpty()) {
                meshes += JsonObject(mapOf("name" to JsonPrimitive(p.name), "primitives" to JsonArray(primitives)))
                node["mesh"] = JsonPrimitive(meshes.size - 1)
            }
            node["translation"] = JsonArray(listOf(p.position.x, p.position.y, p.position.z).map { JsonPrimitive(it) })
            val q = quaternion(p.rotation)
            node["rotation"] = JsonArray(q.map { JsonPrimitive(it) })
            node["scale"] = JsonArray(listOf(p.scale.x, p.scale.y, p.scale.z).map { JsonPrimitive(it) })
            node["extras"] = JsonObject(mapOf("dg" to partExtras(p, view(polygonData(mesh), null))))
            nodes += JsonObject(node)
        }

        fun partExtras(p: Part, polyView: Int): JsonElement {
            val m = LinkedHashMap<String, JsonElement>()
            m["id"] = JsonPrimitive(p.id); m["name"] = JsonPrimitive(p.name)
            p.primitive?.let { s ->
                m["prim"] = JsonObject(
                    mapOf(
                        "kind" to JsonPrimitive(s.kind.name), "segments" to JsonPrimitive(s.segments), "rings" to JsonPrimitive(s.rings),
                        "top" to JsonPrimitive(s.top), "thickness" to JsonPrimitive(s.thickness),
                    ),
                )
            }
            m["position"] = v3(p.position); m["rotation"] = v3(p.rotation); m["scale"] = v3(p.scale)
            m["material"] = JsonPrimitive(material(p.material))
            if (p.faceMaterials.isNotEmpty()) {
                m["faceMaterials"] = JsonArray(p.faceMaterials.entries.sortedBy { it.key }.map { (f, mat) -> JsonArray(listOf(JsonPrimitive(f), JsonPrimitive(material(mat)))) })
            }
            m["smooth"] = JsonPrimitive(p.smooth); m["uv"] = JsonPrimitive(p.uv.name); m["visible"] = JsonPrimitive(p.visible)
            p.group?.let { m["group"] = JsonPrimitive(it) }
            p.brush?.let { m["brush"] = brushJson(it) }
            m["poly"] = JsonPrimitive(polyView)
            return JsonObject(m)
        }

        fun stroke(s: Stroke3) {
            if (s.count < 2) return
            unlit = true
            val mat = LinkedHashMap<String, JsonElement>()
            val r = (s.color shr 16 and 0xFF) / 255f; val g = (s.color shr 8 and 0xFF) / 255f; val b = (s.color and 0xFF) / 255f
            mat["name"] = JsonPrimitive("Çizgi")
            mat["pbrMetallicRoughness"] = JsonObject(mapOf("baseColorFactor" to JsonArray(listOf(toLinear(r), toLinear(g), toLinear(b), 1f).map { JsonPrimitive(it) }), "metallicFactor" to JsonPrimitive(0f)))
            mat["extensions"] = JsonObject(mapOf("KHR_materials_unlit" to JsonObject(emptyMap())))
            materials += JsonObject(mat)
            val mi = materials.size - 1
            val pa = vec3(s.points, true)
            meshes += JsonObject(mapOf("name" to JsonPrimitive("Çizgi"), "primitives" to JsonArray(listOf(JsonObject(mapOf("attributes" to JsonObject(mapOf("POSITION" to JsonPrimitive(pa))), "material" to JsonPrimitive(mi), "mode" to JsonPrimitive(3)))))))
            nodes += JsonObject(
                mapOf(
                    "name" to JsonPrimitive("Çizgi"), "mesh" to JsonPrimitive(meshes.size - 1),
                    "extras" to JsonObject(mapOf("dg" to JsonObject(mapOf("stroke" to JsonObject(mapOf("id" to JsonPrimitive(s.id), "color" to JsonPrimitive(s.color), "width" to JsonPrimitive(s.width), "points" to JsonPrimitive(pa), "t" to JsonPrimitive(s.time))))))),
                ),
            )
        }

        fun build(): ByteArray {
            model.parts.forEach { part(it) }
            model.strokes.forEach { stroke(it) }
            val root = LinkedHashMap<String, JsonElement>()
            root["asset"] = JsonObject(
                mapOf(
                    "version" to JsonPrimitive("2.0"), "generator" to JsonPrimitive("DrawGuidance"),
                    "extras" to JsonObject(mapOf("dg" to modelExtras(model))),
                ),
            )
            if (unlit) root["extensionsUsed"] = JsonArray(listOf(JsonPrimitive("KHR_materials_unlit")))
            root["scene"] = JsonPrimitive(0)
            root["scenes"] = JsonArray(listOf(JsonObject(mapOf("name" to JsonPrimitive(model.name), "nodes" to JsonArray(nodes.indices.map { JsonPrimitive(it) })))))
            root["nodes"] = JsonArray(nodes)
            if (meshes.isNotEmpty()) root["meshes"] = JsonArray(meshes)
            if (materials.isNotEmpty()) root["materials"] = JsonArray(materials)
            if (textures.isNotEmpty()) {
                root["textures"] = JsonArray(textures)
                root["images"] = JsonArray(images)
                root["samplers"] = JsonArray(listOf(JsonObject(mapOf("magFilter" to JsonPrimitive(9729), "minFilter" to JsonPrimitive(9987), "wrapS" to JsonPrimitive(10497), "wrapT" to JsonPrimitive(10497)))))
            }
            if (accessors.isNotEmpty()) root["accessors"] = JsonArray(accessors)
            while (bin.size() % 4 != 0) bin.write(0)
            val binBytes = bin.toByteArray()
            if (views.isNotEmpty()) root["bufferViews"] = JsonArray(views)
            root["buffers"] = JsonArray(listOf(JsonObject(mapOf("byteLength" to JsonPrimitive(binBytes.size)))))
            val jsonBytes = JsonObject(root).toString().toByteArray(Charsets.UTF_8)
            val jsonPad = (4 - jsonBytes.size % 4) % 4
            val total = 12 + 8 + jsonBytes.size + jsonPad + 8 + binBytes.size
            val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
            out.putInt(MAGIC); out.putInt(2); out.putInt(total)
            out.putInt(jsonBytes.size + jsonPad); out.putInt(JSON_CHUNK); out.put(jsonBytes); repeat(jsonPad) { out.put(' '.code.toByte()) }
            out.putInt(binBytes.size); out.putInt(BIN_CHUNK); out.put(binBytes)
            return out.array()
        }
    }

    private data class VKey(val v: Int, val nx: Int, val ny: Int, val nz: Int, val u: Int, val w: Int)

    /** Çokgen ağ (düzenleme için kayıpsız): köşe sayısı, yüz sayısı, konumlar, yüz boyları, sıralar, doku koordinatları. */
    private fun polygonData(mesh: Mesh): ByteArray {
        val corners = mesh.faces.sumOf { it.size }
        val uv = mesh.uvs
        val size = 8 + mesh.pos.size * 4 + mesh.faceCount * 4 + corners * 4 + 4 + (if (uv != null) corners * 8 else 0)
        val bb = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(mesh.vertexCount); bb.putInt(mesh.faceCount)
        mesh.pos.forEach { bb.putFloat(it) }
        mesh.faces.forEach { bb.putInt(it.size) }
        mesh.faces.forEach { f -> f.forEach { bb.putInt(it) } }
        bb.putInt(if (uv != null) 1 else 0)
        uv?.forEach { f -> f.forEach { bb.putFloat(it) } }
        return bb.array()
    }

    private fun readPolygon(data: ByteBuffer): Mesh {
        val nv = data.int; val nf = data.int
        require(nv in 0..5_000_000 && nf in 0..5_000_000)
        val pos = FloatArray(nv * 3) { data.float }
        val sizes = IntArray(nf) { data.int }
        val faces = Array(nf) { f -> IntArray(sizes[f]) { data.int } }
        val hasUv = data.int == 1
        val uvs = if (hasUv) Array(nf) { f -> FloatArray(sizes[f] * 2) { data.float } } else null
        return Mesh(pos, faces, uvs)
    }

    private fun v3(v: V3): JsonElement = JsonArray(listOf(JsonPrimitive(v.x), JsonPrimitive(v.y), JsonPrimitive(v.z)))

    private fun materialExtras(m: Material): JsonElement {
        val o = LinkedHashMap<String, JsonElement>()
        o["color"] = JsonPrimitive(m.color)
        m.texture?.let { o["texture"] = JsonPrimitive(it) }
        o["textureScale"] = JsonPrimitive(m.textureScale); o["opacity"] = JsonPrimitive(m.opacity)
        o["kind"] = JsonPrimitive(m.kind.name); o["shine"] = JsonPrimitive(m.shine)
        m.gradient?.let { o["gradient"] = JsonPrimitive(it); o["gradientAxis"] = JsonPrimitive(m.gradientAxis) }
        return JsonObject(o)
    }

    private fun modelExtras(model: Model3D): JsonElement {
        val s = model.scene; val l = s.light; val c = model.camera
        return JsonObject(
            mapOf(
                "v" to JsonPrimitive(DG_VERSION), "id" to JsonPrimitive(model.id), "name" to JsonPrimitive(model.name),
                "scene" to JsonObject(
                    mapOf(
                        "light" to JsonObject(mapOf("yaw" to JsonPrimitive(l.yaw), "pitch" to JsonPrimitive(l.pitch), "intensity" to JsonPrimitive(l.intensity), "ambient" to JsonPrimitive(l.ambient), "color" to JsonPrimitive(l.color))),
                        "shadow" to JsonPrimitive(s.shadow), "edges" to JsonPrimitive(s.edges), "edgeColor" to JsonPrimitive(s.edgeColor),
                        "background" to JsonPrimitive(s.background), "ground" to JsonPrimitive(s.ground),
                    ),
                ),
                "camera" to JsonObject(mapOf("yaw" to JsonPrimitive(c.yaw), "pitch" to JsonPrimitive(c.pitch), "persp" to JsonPrimitive(c.persp), "zoom" to JsonPrimitive(c.zoom), "target" to v3(c.target))),
                // v26 parça grupları
                "groups" to JsonArray(model.groups.map { g -> JsonArray(listOf(JsonPrimitive(g.id), JsonPrimitive(g.name))) }),
            ),
        )
    }

    /** v26 fırça kaynağı (parçanın yerel uzayında iz ve ayarlar). */
    private fun brushJson(b: BrushSource): JsonElement {
        val m = LinkedHashMap<String, JsonElement>()
        val sp = b.spec
        m["kind"] = JsonPrimitive(sp.kind.name); m["w"] = JsonPrimitive(sp.width); m["h"] = JsonPrimitive(sp.height)
        m["bulge"] = JsonPrimitive(sp.bulge); m["taper"] = JsonPrimitive(sp.taper); m["smooth"] = JsonPrimitive(sp.smoothing)
        m["pressure"] = JsonPrimitive(sp.pressure); m["sides"] = JsonPrimitive(sp.sides); m["max"] = JsonPrimitive(sp.maxSamples)
        m["pts"] = JsonArray(b.points.map { JsonPrimitive(it) })
        b.pressures?.let { pr -> m["prs"] = JsonArray(pr.map { JsonPrimitive(it) }) }
        m["n"] = v3(b.normal); m["up"] = v3(b.up)
        b.closed?.let { m["closed"] = JsonPrimitive(it) }
        b.axis?.let { m["axis"] = v3(it) }
        m["t"] = JsonPrimitive(b.time)
        b.sig?.let { g -> m["sig"] = JsonArray(listOf(JsonPrimitive(g.vertices), JsonPrimitive(g.faces), JsonPrimitive(g.sum))) }
        return JsonObject(m)
    }

    private fun brushFrom(o: JsonObject): BrushSource? {
        val kind = Brush3Kind.entries.firstOrNull { it.name == o.str("kind") } ?: return null
        val pts = o["pts"].floats()?.takeIf { it.size >= 6 && it.size % 3 == 0 } ?: return null
        fun vec(key: String) = o[key].floats()?.takeIf { it.size == 3 }?.let { V3(it[0], it[1], it[2]) }
        val spec = BrushShapeSpec(
            kind, o.float("w") ?: 0.05f, o.float("h") ?: 0.5f, o.float("bulge") ?: 0.8f, o.float("taper") ?: 0.3f,
            o.float("smooth") ?: 0.4f, (o["pressure"] as? JsonPrimitive)?.booleanOrNull ?: true, o.int("sides") ?: 10, o.int("max") ?: 160,
        )
        val sig = (o["sig"] as? JsonArray)?.takeIf { it.size == 3 }?.let { a ->
            val v = (a[0] as? JsonPrimitive)?.intOrNull; val f = (a[1] as? JsonPrimitive)?.intOrNull; val sum = (a[2] as? JsonPrimitive)?.doubleOrNull
            if (v != null && f != null && sum != null) MeshSig(v, f, sum) else null
        }
        return BrushSource(
            spec, pts, o["prs"].floats()?.takeIf { it.size == pts.size / 3 }, vec("n") ?: V3.UP, vec("up") ?: V3(0f, 0f, -1f),
            (o["closed"] as? JsonPrimitive)?.booleanOrNull, vec("axis"), (o["t"] as? JsonPrimitive)?.longOrNull ?: 0L, sig,
        )
    }

    /** Euler (derece; önce X, sonra Y, sonra Z) → dördey (x, y, z, w). */
    fun quaternion(deg: V3): List<Float> {
        fun q(axis: Int, d: Float): DoubleArray {
            val h = Math.toRadians(d.toDouble()) / 2
            val s = sin(h); val c = cos(h)
            return when (axis) { 0 -> doubleArrayOf(s, 0.0, 0.0, c); 1 -> doubleArrayOf(0.0, s, 0.0, c); else -> doubleArrayOf(0.0, 0.0, s, c) }
        }
        fun mul(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
            a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
            a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
            a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
            a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2],
        )
        val r = mul(mul(q(2, deg.z), q(1, deg.y)), q(0, deg.x))
        return r.map { it.toFloat() }
    }

    private fun toLinear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    private fun toSrgb(c: Float): Float = if (c <= 0.0031308f) c * 12.92f else (1.055 * c.toDouble().pow(1 / 2.4) - 0.055).toFloat()

    // =========================================================================================== okuma (ortak)

    private class Doc(val root: JsonObject, val bin: ByteArray?) {
        val views: List<JsonObject> = root["bufferViews"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
        val accessors: List<JsonObject> = root["accessors"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
        val buffers: List<ByteArray?> = (root["buffers"]?.jsonArray ?: JsonArray(emptyList())).mapIndexed { i, b ->
            val uri = (b as? JsonObject)?.get("uri")?.jsonPrimitive?.contentOrNull
            when {
                uri == null -> if (i == 0) bin else null
                uri.startsWith("data:") -> decodeDataUri(uri)
                else -> null
            }
        }

        fun viewBytes(index: Int): ByteBuffer {
            val v = views.getOrNull(index) ?: throw GltfException("Eksik bufferView $index")
            val buf = buffers.getOrNull(v.int("buffer") ?: 0) ?: throw GltfException("Harici .bin dosyası desteklenmiyor (GLB kullanın)")
            val off = v.int("byteOffset") ?: 0
            val len = v.int("byteLength") ?: 0
            if (off < 0 || len < 0 || off + len > buf.size) throw GltfException("Bozuk bufferView")
            return ByteBuffer.wrap(buf, off, len).slice().order(ByteOrder.LITTLE_ENDIAN)
        }

        /** Erişimciyi ondalık diziye okur (normalize bayrağına göre); [count] × bileşen. */
        fun read(index: Int): Pair<FloatArray, Int> {
            val a = accessors.getOrNull(index) ?: throw GltfException("Eksik accessor $index")
            val comps = when (a.str("type")) { "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; "MAT4" -> 16; else -> 1 }
            val count = a.int("count") ?: 0
            if (count < 0 || count > 20_000_000) throw GltfException("Çok büyük accessor")
            val type = a.int("componentType") ?: FLOAT
            val norm = a["normalized"]?.jsonPrimitive?.booleanOrNull == true
            val out = FloatArray(count * comps)
            val viewIndex = a.int("bufferView") ?: return out to comps
            val bb = viewBytes(viewIndex)
            val base = a.int("byteOffset") ?: 0
            val size = when (type) { FLOAT, UINT -> 4; USHORT, SHORT -> 2; else -> 1 }
            val stride = views[viewIndex].int("byteStride") ?: (size * comps)
            for (i in 0 until count) for (c in 0 until comps) {
                val p = base + i * stride + c * size
                if (p + size > bb.limit()) throw GltfException("Accessor tamponun dışında")
                out[i * comps + c] = when (type) {
                    FLOAT -> bb.getFloat(p)
                    UBYTE -> (bb.get(p).toInt() and 0xFF).let { if (norm) it / 255f else it.toFloat() }
                    BYTE -> bb.get(p).toInt().let { if (norm) max(it / 127f, -1f) else it.toFloat() }
                    USHORT -> (bb.getShort(p).toInt() and 0xFFFF).let { if (norm) it / 65535f else it.toFloat() }
                    SHORT -> bb.getShort(p).toInt().let { if (norm) max(it / 32767f, -1f) else it.toFloat() }
                    UINT -> (bb.getInt(p).toLong() and 0xFFFFFFFFL).toFloat()
                    else -> 0f
                }
            }
            return out to comps
        }

        fun readInts(index: Int): IntArray {
            val a = accessors.getOrNull(index) ?: throw GltfException("Eksik accessor $index")
            val count = a.int("count") ?: 0
            val type = a.int("componentType") ?: UINT
            val viewIndex = a.int("bufferView") ?: return IntArray(count)
            val bb = viewBytes(viewIndex)
            val base = a.int("byteOffset") ?: 0
            val size = when (type) { UINT -> 4; USHORT -> 2; else -> 1 }
            val stride = views[viewIndex].int("byteStride") ?: size
            return IntArray(count) { i ->
                val p = base + i * stride
                if (p + size > bb.limit()) throw GltfException("Dizin tamponun dışında")
                when (type) { UINT -> bb.getInt(p); USHORT -> bb.getShort(p).toInt() and 0xFFFF; else -> bb.get(p).toInt() and 0xFF }
            }
        }
    }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.float(key: String): Float? = (this[key] as? JsonPrimitive)?.doubleOrNull?.toFloat()
    private fun JsonElement?.floats(): FloatArray? = (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull?.toFloat() }?.toFloatArray()
    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject

    private fun decodeDataUri(uri: String): ByteArray? {
        val comma = uri.indexOf(',')
        if (comma < 0 || !uri.substring(0, comma).endsWith(";base64")) return null
        return runCatching { java.util.Base64.getDecoder().decode(uri.substring(comma + 1)) }.getOrNull()
    }

    private fun parse(data: ByteArray): Doc {
        if (isGlb(data)) {
            val total = le(data, 8)
            if (total > data.size || total < 20) throw GltfException("Bozuk GLB")
            var p = 12
            var jsonText: String? = null; var bin: ByteArray? = null
            while (p + 8 <= total) {
                val len = le(data, p); val type = le(data, p + 4)
                if (len < 0 || p + 8 + len > total) throw GltfException("Bozuk GLB parçası")
                if (type == JSON_CHUNK && jsonText == null) jsonText = String(data, p + 8, len, Charsets.UTF_8)
                else if (type == BIN_CHUNK && bin == null) bin = data.copyOfRange(p + 8, p + 8 + len)
                p += 8 + len
            }
            val root = runCatching { json.parseToJsonElement(jsonText ?: throw GltfException("JSON yok")).jsonObject }.getOrElse { throw GltfException("glTF JSON okunamadı") }
            return Doc(root, bin)
        }
        val text = String(data, Charsets.UTF_8).trimStart()
        if (!text.startsWith("{")) throw GltfException("glTF/GLB dosyası değil")
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse { throw GltfException("glTF JSON okunamadı") }
        return Doc(root, null)
    }

    private fun le(d: ByteArray, o: Int): Int =
        (d[o].toInt() and 0xFF) or ((d[o + 1].toInt() and 0xFF) shl 8) or ((d[o + 2].toInt() and 0xFF) shl 16) or ((d[o + 3].toInt() and 0xFF) shl 24)

    private fun checkSupported(doc: Doc) {
        val required = doc.root["extensionsRequired"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val compressed = required.filter { it == "KHR_draco_mesh_compression" || it == "EXT_meshopt_compression" || it == "KHR_meshopt_compression" }
        if (compressed.isNotEmpty()) throw GltfException("Sıkıştırılmış glTF desteklenmiyor (${compressed.joinToString()})")
    }

    // =========================================================================================== uygulamanın kendi GLB'si

    /**
     * Uygulamanın yazdığı GLB'yi kayıpsız okur ("extras" bilgileriyle). Başka bir programın dosyasıysa null (o zaman
     * [import] kullanılır).
     */
    fun read(data: ByteArray): Model3D? {
        val doc = parse(data)
        val dg = doc.root["asset"].obj()?.get("extras").obj()?.get("dg").obj() ?: return null
        val materials = doc.root["materials"]?.jsonArray?.map { m -> materialFrom(m.jsonObject) } ?: emptyList()
        val parts = ArrayList<Part>()
        val strokes = ArrayList<Stroke3>()
        val sceneIndex = doc.root.int("scene") ?: 0
        val sceneNodes = doc.root["scenes"]?.jsonArray?.getOrNull(sceneIndex)?.obj()?.get("nodes")?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }
        val nodes = doc.root["nodes"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
        for (ni in sceneNodes ?: nodes.indices.toList()) {
            val node = nodes.getOrNull(ni) ?: continue
            val ex = node["extras"].obj()?.get("dg").obj() ?: continue
            ex["stroke"].obj()?.let { s ->
                val acc = s.int("points") ?: return@let
                val (pts, comps) = doc.read(acc)
                if (comps == 3) strokes += Stroke3(
                    s.str("id") ?: "s${strokes.size}", pts, s.int("color") ?: 0xFF263238.toInt(), s.float("width") ?: 0.02f,
                    (s["t"] as? JsonPrimitive)?.longOrNull ?: 0L,
                )
            }
            val polyView = ex.int("poly") ?: continue
            val mesh = readPolygon(doc.viewBytes(polyView))
            val prim = ex["prim"].obj()?.let { p ->
                PrimitiveSpec(
                    PrimitiveKind.entries.firstOrNull { it.name == p.str("kind") } ?: PrimitiveKind.CUBE,
                    p.int("segments") ?: 24, p.int("rings") ?: 10, p.float("top") ?: 1f, p.float("thickness") ?: 0.3f,
                )
            }
            fun vec(key: String, def: V3) = ex[key].floats()?.takeIf { it.size == 3 }?.let { V3(it[0], it[1], it[2]) } ?: def
            val faceMats = HashMap<Int, Material>()
            ex["faceMaterials"]?.jsonArray?.forEach { e ->
                val a = e.jsonArray
                val f = a.getOrNull(0)?.jsonPrimitive?.intOrNull ?: return@forEach
                val mi = a.getOrNull(1)?.jsonPrimitive?.intOrNull ?: return@forEach
                materials.getOrNull(mi)?.let { faceMats[f] = it }
            }
            parts += Part(
                id = ex.str("id") ?: "p${parts.size}",
                name = ex.str("name") ?: node.str("name") ?: "Parça ${parts.size + 1}",
                mesh = mesh, primitive = prim,
                position = vec("position", V3.ZERO), rotation = vec("rotation", V3.ZERO), scale = vec("scale", V3.ONE),
                material = ex.int("material")?.let { materials.getOrNull(it) } ?: Material(),
                faceMaterials = faceMats,
                smooth = ex["smooth"]?.jsonPrimitive?.booleanOrNull ?: false,
                uv = UvMode.entries.firstOrNull { it.name == ex.str("uv") } ?: UvMode.BOX,
                visible = ex["visible"]?.jsonPrimitive?.booleanOrNull ?: true,
                group = ex.str("group"),
                brush = ex["brush"].obj()?.let { brushFrom(it) },
            )
        }
        val sc = dg["scene"].obj()
        val l = sc?.get("light").obj()
        val light = Light(
            l?.float("yaw") ?: 35f, l?.float("pitch") ?: 55f, l?.float("intensity") ?: 1f, l?.float("ambient") ?: 0.38f,
            l?.int("color") ?: 0xFFFFFFFF.toInt(),
        )
        val scene = SceneSettings(
            light, sc?.get("shadow")?.jsonPrimitive?.booleanOrNull ?: true, sc?.get("edges")?.jsonPrimitive?.booleanOrNull ?: true,
            sc?.int("edgeColor") ?: 0xFF263238.toInt(), sc?.int("background") ?: 0, sc?.get("ground")?.jsonPrimitive?.booleanOrNull ?: true,
        )
        val c = dg["camera"].obj()
        val camera = Camera3(
            c?.float("yaw") ?: 35f, c?.float("pitch") ?: 24f, c?.float("persp") ?: 0.35f, c?.float("zoom") ?: 1f,
            c?.get("target").floats()?.takeIf { it.size == 3 }?.let { V3(it[0], it[1], it[2]) } ?: V3(0f, 0.5f, 0f),
        )
        val groups = (dg["groups"] as? JsonArray)?.mapNotNull { e ->
            val a = e as? JsonArray ?: return@mapNotNull null
            val id = (a.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            Group3(id, (a.getOrNull(1) as? JsonPrimitive)?.contentOrNull ?: "Grup")
        } ?: emptyList()
        return Model3D(dg.str("id") ?: "model", dg.str("name") ?: "3B şekil", parts, strokes, scene, camera, groups)
    }

    private fun materialFrom(m: JsonObject): Material {
        val ex = m["extras"].obj()?.get("dg").obj()
        if (ex != null) {
            return Material(
                color = ex.int("color") ?: Material.DEFAULT_COLOR, texture = ex.str("texture"), textureScale = ex.float("textureScale") ?: 1f,
                opacity = ex.float("opacity") ?: 1f, kind = MaterialKind.entries.firstOrNull { it.name == ex.str("kind") } ?: MaterialKind.PLASTIC,
                shine = ex.float("shine") ?: 0.5f,
                gradient = ex.int("gradient"), gradientAxis = (ex.int("gradientAxis") ?: 1).coerceIn(0, 2),
            )
        }
        return pbrMaterial(m, null)
    }

    /** Başka programın PBR malzemesi → uygulamanın malzemesi. */
    private fun pbrMaterial(m: JsonObject, texture: String?): Material {
        val pbr = m["pbrMetallicRoughness"].obj()
        val f = pbr?.get("baseColorFactor").floats()?.takeIf { it.size >= 3 } ?: floatArrayOf(1f, 1f, 1f, 1f)
        val alpha = if (f.size >= 4) f[3] else 1f
        val r = (toSrgb(f[0].coerceIn(0f, 1f)) * 255f + 0.5f).toInt(); val g = (toSrgb(f[1].coerceIn(0f, 1f)) * 255f + 0.5f).toInt(); val b = (toSrgb(f[2].coerceIn(0f, 1f)) * 255f + 0.5f).toInt()
        val metallic = pbr?.float("metallicFactor") ?: 1f
        val rough = pbr?.float("roughnessFactor") ?: 1f
        val emissive = m["emissiveFactor"].floats()?.let { it.size >= 3 && (it[0] + it[1] + it[2]) > 0.3f } == true
        val blend = m.str("alphaMode") == "BLEND"
        val kind = when {
            emissive -> MaterialKind.GLOW
            metallic > 0.5f && pbr?.get("metallicRoughnessTexture") == null -> MaterialKind.METAL
            rough > 0.85f -> MaterialKind.MATTE
            else -> MaterialKind.PLASTIC
        }
        return Material(
            color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b, texture = texture, textureScale = 1f,
            opacity = if (blend) alpha.coerceIn(0.1f, 1f) else 1f, kind = kind, shine = (1f - rough).coerceIn(0f, 1f),
        )
    }

    // =========================================================================================== içe aktarma

    /**
     * Başka bir programın glTF/GLB dosyasını içe aktarır: sahnedeki tüm üçgen ilkelleri (düğüm dönüşümleriyle) parça
     * olur; toplam üçgen sayısı [maxTriangles]'a düşürülür; model 1,6 birim boyuna ölçeklenip zemine oturtulur; temel
     * renk dokuları [decodeImage] ile çözülüp en çok [textureSize] piksele küçültülür. Uygulamanın kendi dosyası ise
     * kayıpsız okunur (ayrıntı değişmez).
     */
    fun import(
        data: ByteArray,
        name: String,
        maxTriangles: Int,
        decodeImage: (ByteArray, String?) -> Texture?,
        newId: () -> String = { java.util.UUID.randomUUID().toString() },
        textureSize: Int = 256,
    ): GltfImport {
        val own = runCatching { read(data) }.getOrNull()
        if (own != null) {
            val m = own.copy(id = newId(), name = name.ifBlank { own.name })
            return GltfImport(m, emptyList(), m.triangleCount, m.triangleCount)
        }
        val doc = parse(data)
        checkSupported(doc)
        val nodes = doc.root["nodes"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
        val meshes = doc.root["meshes"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
        val materials = doc.root["materials"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
        // Kökler: varsayılan sahnenin düğümleri; sahne yoksa çocuğu olmayan tüm düğümler (ya da tüm ağlar)
        val sceneIndex = doc.root.int("scene") ?: 0
        val roots = doc.root["scenes"]?.jsonArray?.getOrNull(sceneIndex)?.obj()?.get("nodes")?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }
            ?: run {
                val children = nodes.flatMap { n -> n["children"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } ?: emptyList() }.toSet()
                nodes.indices.filter { it !in children }
            }
        class Raw(val name: String, val material: Int?, val pos: FloatArray, val tris: IntArray, val uv: FloatArray?)
        val raws = ArrayList<Raw>()
        fun addMesh(meshIndex: Int, world: FloatArray, nodeName: String?) {
            val mesh = meshes.getOrNull(meshIndex) ?: return
            val prims = mesh["primitives"]?.jsonArray ?: return
            for (pe in prims) {
                val p = pe.jsonObject
                val mode = p.int("mode") ?: 4
                if (mode !in 4..6) continue
                val attrs = p["attributes"].obj() ?: continue
                val posAcc = attrs.int("POSITION") ?: continue
                val (pos0, pc) = doc.read(posAcc)
                if (pc != 3) continue
                val n = pos0.size / 3
                val pos = FloatArray(n * 3)
                for (i in 0 until n) {
                    val x = pos0[3 * i]; val y = pos0[3 * i + 1]; val z = pos0[3 * i + 2]
                    pos[3 * i] = world[0] * x + world[4] * y + world[8] * z + world[12]
                    pos[3 * i + 1] = world[1] * x + world[5] * y + world[9] * z + world[13]
                    pos[3 * i + 2] = world[2] * x + world[6] * y + world[10] * z + world[14]
                }
                val idx0 = p.int("indices")?.let { doc.readInts(it) } ?: IntArray(n) { it }
                val tris = when (mode) {
                    4 -> idx0.copyOf(idx0.size / 3 * 3)
                    5 -> IntArray(max(0, idx0.size - 2) * 3).also { t -> for (i in 0 until idx0.size - 2) { if (i % 2 == 0) { t[3 * i] = idx0[i]; t[3 * i + 1] = idx0[i + 1] } else { t[3 * i] = idx0[i + 1]; t[3 * i + 1] = idx0[i] }; t[3 * i + 2] = idx0[i + 2] } }
                    else -> IntArray(max(0, idx0.size - 2) * 3).also { t -> for (i in 1 until idx0.size - 1) { t[3 * (i - 1)] = idx0[0]; t[3 * (i - 1) + 1] = idx0[i]; t[3 * (i - 1) + 2] = idx0[i + 1] } }
                }
                if (tris.any { it < 0 || it >= n }) continue
                // Düğüm matrisi aynalıyorsa (eksi belirleyici) üçgen yönü çevrilir
                val det = world[0] * (world[5] * world[10] - world[9] * world[6]) - world[4] * (world[1] * world[10] - world[9] * world[2]) + world[8] * (world[1] * world[6] - world[5] * world[2])
                if (det < 0f) for (t in 0 until tris.size / 3) { val s = tris[3 * t + 1]; tris[3 * t + 1] = tris[3 * t + 2]; tris[3 * t + 2] = s }
                val uvAcc = attrs.int("TEXCOORD_0")
                val uv = uvAcc?.let { a ->
                    val (u0, uc) = doc.read(a)
                    if (uc != 2) null else FloatArray(tris.size * 2).also { out -> for (k in tris.indices) { out[2 * k] = u0[2 * tris[k]]; out[2 * k + 1] = u0[2 * tris[k] + 1] } }
                }
                raws += Raw(nodeName ?: mesh.str("name") ?: "Parça", p.int("material"), pos, tris, uv)
            }
        }
        fun visit(index: Int, parent: FloatArray, depth: Int) {
            if (depth > 64) return
            val node = nodes.getOrNull(index) ?: return
            val world = Mat4.mul(parent, localMatrix(node))
            node.int("mesh")?.let { addMesh(it, world, node.str("name")) }
            node["children"]?.jsonArray?.forEach { c -> c.jsonPrimitive.intOrNull?.let { visit(it, world, depth + 1) } }
        }
        if (roots.isEmpty() && meshes.isNotEmpty()) meshes.indices.forEach { addMesh(it, Mat4.identity(), null) }
        else roots.forEach { visit(it, Mat4.identity(), 0) }
        if (raws.isEmpty()) throw GltfException("Dosyada üçgen ağ bulunamadı")
        // Çok parçalı modeller malzemeye göre birleştirilir
        val grouped: List<Raw> = if (raws.size <= MAX_PARTS) raws else raws.groupBy { it.material }.map { (mat, list) ->
            val total = list.sumOf { it.pos.size }
            val pos = FloatArray(total); val tris = ArrayList<Int>(); var off = 0
            val withUv = list.all { it.uv != null }
            val uv = ArrayList<Float>()
            list.forEach { r ->
                r.pos.copyInto(pos, off * 3)
                r.tris.forEach { tris += it + off }
                if (withUv) r.uv!!.forEach { uv += it }
                off += r.pos.size / 3
            }
            Raw(list.first().name, mat, pos, tris.toIntArray(), if (withUv) uv.toFloatArray() else null)
        }.take(MAX_PARTS)
        val sourceTris = grouped.sumOf { it.tris.size / 3 }
        val budget = maxTriangles.coerceAtLeast(64)
        // Dokular (temel renk): bir kez çözülür
        val textureIds = HashMap<Int, String?>()
        val imported = ArrayList<ImportedTexture>()
        fun textureOf(matIndex: Int?): String? {
            if (matIndex == null) return null
            return textureIds.getOrPut(matIndex) {
                val m = materials.getOrNull(matIndex) ?: return@getOrPut null
                val texIndex = m["pbrMetallicRoughness"].obj()?.get("baseColorTexture").obj()?.int("index") ?: return@getOrPut null
                val tex = doc.root["textures"]?.jsonArray?.getOrNull(texIndex)?.obj() ?: return@getOrPut null
                val src = tex.int("source") ?: return@getOrPut null
                val img = doc.root["images"]?.jsonArray?.getOrNull(src)?.obj() ?: return@getOrPut null
                val bytes = img.int("bufferView")?.let { v -> doc.viewBytes(v).let { b -> ByteArray(b.remaining()).also { b.get(it) } } }
                    ?: img.str("uri")?.takeIf { it.startsWith("data:") }?.let { decodeDataUri(it) }
                    ?: return@getOrPut null
                val decoded = runCatching { decodeImage(bytes, img.str("mimeType")) }.getOrNull() ?: return@getOrPut null
                val small = TextureResize.toPowerOfTwo(decoded, textureSize)
                val id = "imp." + newId()
                imported += ImportedTexture(id, img.str("name") ?: m.str("name") ?: name, small)
                id
            }
        }
        // Parçalar: ayrıntı düşürülür (bütçe üçgen sayısıyla orantılı paylaşılır)
        class Simple(val raw: Raw, val mesh: TriMesh)
        val simplified = grouped.map { r ->
            val share = (budget.toLong() * (r.tris.size / 3) / sourceTris.coerceAtLeast(1)).toInt().coerceAtLeast(16)
            Simple(r, MeshSimplifier.simplify(TriMesh(r.pos, r.tris, r.uv), share))
        }.filter { it.mesh.triangleCount > 0 }
        if (simplified.isEmpty()) throw GltfException("Dosyada üçgen ağ bulunamadı")
        // Ölçek ve konum: en uzun kenar 1,6 birim, zemine oturur, X–Z'de ortalanır
        var ax = Float.POSITIVE_INFINITY; var ay = Float.POSITIVE_INFINITY; var az = Float.POSITIVE_INFINITY
        var bx = Float.NEGATIVE_INFINITY; var by = Float.NEGATIVE_INFINITY; var bz = Float.NEGATIVE_INFINITY
        simplified.forEach { s -> val p = s.mesh.pos; for (i in 0 until p.size / 3) { ax = min(ax, p[3 * i]); ay = min(ay, p[3 * i + 1]); az = min(az, p[3 * i + 2]); bx = max(bx, p[3 * i]); by = max(by, p[3 * i + 1]); bz = max(bz, p[3 * i + 2]) } }
        val extent = max(bx - ax, max(by - ay, bz - az)).coerceAtLeast(1e-6f)
        val k = TARGET_EXTENT / extent
        val cx = (ax + bx) / 2f; val cz = (az + bz) / 2f
        val parts = simplified.mapIndexed { i, s ->
            val p = s.mesh.pos
            val world = FloatArray(p.size) { j -> when (j % 3) { 0 -> (p[j] - cx) * k; 1 -> (p[j] - ay) * k; else -> (p[j] - cz) * k } }
            // Parçanın konumu kendi taban merkezi (taşıma/döndürme merkezi)
            var px0 = Float.POSITIVE_INFINITY; var py0 = Float.POSITIVE_INFINITY; var pz0 = Float.POSITIVE_INFINITY
            var px1 = Float.NEGATIVE_INFINITY; var pz1 = Float.NEGATIVE_INFINITY
            for (v in 0 until world.size / 3) { px0 = min(px0, world[3 * v]); py0 = min(py0, world[3 * v + 1]); pz0 = min(pz0, world[3 * v + 2]); px1 = max(px1, world[3 * v]); pz1 = max(pz1, world[3 * v + 2]) }
            val origin = V3((px0 + px1) / 2f, py0, (pz0 + pz1) / 2f)
            val local = FloatArray(world.size) { j -> world[j] - when (j % 3) { 0 -> origin.x; 1 -> origin.y; else -> origin.z } }
            val tris = s.mesh.tris
            val faces = Array(tris.size / 3) { t -> intArrayOf(tris[3 * t], tris[3 * t + 1], tris[3 * t + 2]) }
            val texId = textureOf(s.raw.material)
            val uvs = if (texId != null) s.mesh.uv?.let { u -> Array(faces.size) { t -> floatArrayOf(u[6 * t], u[6 * t + 1], u[6 * t + 2], u[6 * t + 3], u[6 * t + 4], u[6 * t + 5]) } } else null
            val mat = s.raw.material?.let { mi -> materials.getOrNull(mi)?.let { pbrMaterial(it, texId) } } ?: Material(color = ModelEdit.palette[i % ModelEdit.palette.size])
            Part(
                id = newId(), name = s.raw.name.ifBlank { "Parça ${i + 1}" }.take(40),
                mesh = Mesh(local, faces, uvs), primitive = null, position = origin, material = mat.let { if (it.texture != null && uvs == null) it.copy(texture = null) else it },
                smooth = true, uv = if (uvs != null) UvMode.MESH else UvMode.BOX,
            )
        }
        val box = parts.map { ModelEdit.worldBounds(it) }.reduce { a, b -> a.union(b) }
        val model = Model3D(
            id = newId(), name = name.ifBlank { "İçe aktarılan model" }, parts = parts,
            camera = Camera3(yaw = 35f, pitch = 20f, target = box.center, zoom = (View3.REFERENCE_EXTENT / (box.radius * 1.15f)).coerceIn(0.1f, 30f)),
        )
        return GltfImport(model, imported.filter { t -> parts.any { it.material.texture == t.id } }, sourceTris, model.triangleCount)
    }

    private const val MAX_PARTS = 48
    private const val TARGET_EXTENT = 1.6f

    /** Düğümün yerel matrisi (sütun düzeninde 4×4): "matrix" ya da öteleme · döndürme · ölçek. */
    private fun localMatrix(node: JsonObject): FloatArray {
        node["matrix"].floats()?.takeIf { it.size == 16 }?.let { return it }
        val t = node["translation"].floats()?.takeIf { it.size == 3 } ?: floatArrayOf(0f, 0f, 0f)
        val r = node["rotation"].floats()?.takeIf { it.size == 4 } ?: floatArrayOf(0f, 0f, 0f, 1f)
        val s = node["scale"].floats()?.takeIf { it.size == 3 } ?: floatArrayOf(1f, 1f, 1f)
        return Mat4.trs(t, r, s)
    }

    @Suppress("unused")
    private fun unused(e: JsonElement) = e is JsonNull || abs(0f) > 0f
}

/** Sütun düzeninde 4×4 matris yardımcıları (glTF ve ekran kartı çizimi). */
object Mat4 {
    fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val o = FloatArray(16)
        for (c in 0..3) for (r in 0..3) {
            var s = 0f
            for (k in 0..3) s += a[k * 4 + r] * b[c * 4 + k]
            o[c * 4 + r] = s
        }
        return o
    }

    /** Öteleme · döndürme (dördey x, y, z, w) · ölçek. */
    fun trs(t: FloatArray, q: FloatArray, s: FloatArray): FloatArray {
        val x = q[0]; val y = q[1]; val z = q[2]; val w = q[3]
        val xx = x * x; val yy = y * y; val zz = z * z; val xy = x * y; val xz = x * z; val yz = y * z; val wx = w * x; val wy = w * y; val wz = w * z
        return floatArrayOf(
            (1 - 2 * (yy + zz)) * s[0], (2 * (xy + wz)) * s[0], (2 * (xz - wy)) * s[0], 0f,
            (2 * (xy - wz)) * s[1], (1 - 2 * (xx + zz)) * s[1], (2 * (yz + wx)) * s[1], 0f,
            (2 * (xz + wy)) * s[2], (2 * (yz - wx)) * s[2], (1 - 2 * (xx + yy)) * s[2], 0f,
            t[0], t[1], t[2], 1f,
        )
    }

    /** Parçanın dönüşümü (sütun düzeninde 4×4). */
    fun of(t: Transform3): FloatArray {
        val m = t.m
        return floatArrayOf(m[0], m[4], m[8], 0f, m[1], m[5], m[9], 0f, m[2], m[6], m[10], 0f, m[3], m[7], m[11], 1f)
    }
}

/** Doku küçültme (içe aktarma): kutu süzgeciyle en çok [max] piksele ve 2'nin kuvvetine (ekran kartında tekrar için). */
object TextureResize {
    fun toPowerOfTwo(t: Texture, max: Int): Texture {
        fun pot(v: Int): Int { var p = 1; while (p * 2 <= v && p * 2 <= max) p *= 2; return p.coerceAtLeast(1) }
        val w = pot(t.w); val h = pot(t.h)
        if (w == t.w && h == t.h) return t
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val x0 = x * t.w / w; val x1 = max((x + 1) * t.w / w, x0 + 1)
            val y0 = y * t.h / h; val y1 = max((y + 1) * t.h / h, y0 + 1)
            var a = 0L; var r = 0L; var g = 0L; var b = 0L; var n = 0
            for (yy in y0 until min(y1, t.h)) for (xx in x0 until min(x1, t.w)) {
                val c = t.px[yy * t.w + xx]
                a += c ushr 24 and 0xFF; r += c shr 16 and 0xFF; g += c shr 8 and 0xFF; b += c and 0xFF; n++
            }
            if (n > 0) out[y * w + x] = ((a / n).toInt() shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        }
        return Texture(w, h, out)
    }
}

/** Büyüyen ondalık/tamsayı listeleri (kutulamasız). */
internal class FloatList {
    private var a = FloatArray(256)
    var size = 0
        private set
    fun add(v: Float) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
    fun toArray(): FloatArray = a.copyOf(size)
}

internal class IntList {
    private var a = IntArray(256)
    var size = 0
        private set
    fun add(v: Int) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
    fun toArray(): IntArray = a.copyOf(size)
}
