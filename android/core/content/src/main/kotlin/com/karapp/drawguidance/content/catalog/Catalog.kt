package com.karapp.drawguidance.content.catalog

import com.karapp.drawguidance.content.ink.HandwritingNet
import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.LibCategory
import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.shapes.ModeCatalog
import com.karapp.drawguidance.content.shapes.ShapeMode
import com.karapp.drawguidance.content.shapes.ShapeNet
import com.karapp.drawguidance.content.shapes.ShapeSignatures
import com.karapp.drawguidance.content.shapes.ShapeTemplates
import com.karapp.drawguidance.content.vector.LineStyle
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VPath
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * v33 hazır katalog (şekil şablonları, vektör şekiller, modların temel sinir ağları): uygulamayla gelir ve sunucuda
 * (MongoDB) da tutulur; uygulama günde bir sunucudakiyle eşitler. **Sürümü yüksek olan önceliklidir**: sunucunun
 * kataloğu uygulamayla gelenden yeniyse onun kayıtları aynı anahtarlı yerleşik kayıtların yerine geçer, yeni kayıtlar
 * eklenir. Kişisel öğrenme (kabul/ret sayaçları, kullanıcı örnekleri, moda özel ağın cihazda öğrendikleri) cihazda
 * kalır. Biçim sunucu sözleşmesiyle aynıdır (bkz. `backend` README).
 */
object CatalogInfo {
    /**
     * Uygulamayla gelen kataloğun sürümü (sunucunun tohum dosyası da bu sürümle üretilir). v38: 38000 (4+ elemanla
     * yeniden eğitilen mod ağları ve yazı ağının "yazı mı" kapısı). v43: 43000 (nesne kütüphanesi kaldırıldı; v42 genel
     * şekiller).
     */
    const val VERSION: Long = 43000L

    /** Modların temel ağlarının biçimi. */
    const val MODE_NET_FORMAT = "modenet-int8-v1"

    /**
     * v37 not alma modunun yazı düzeltme ağı ([HandwritingNet]) katalogda ayrı bir "mod" kaydıdır: modu
     * [HandwritingNet.CATALOG_MODE] ("NOTES_TEXT"), biçimi bu. Şekil modu olmadığı için eski uygulamalar yok sayar.
     */
    const val TEXT_NET_MODE = HandwritingNet.CATALOG_MODE
    const val TEXT_NET_FORMAT = HandwritingNet.FORMAT
}

/** Sunucudan gelen (ya da sunucuya tohum olarak giden) şekil şablonu: uygulamanın `shape_templates` satırıyla aynı. */
data class CatalogTemplate(
    val key: String,
    val templateKey: String,
    val name: String,
    val category: String,
    val group: String,
    val description: String,
    val solid: String?,
    val strokeCount: Int,
    val closedMask: Int,
    val aspectBucket: Int,
    val cornerCount: Int,
    val geometry: ByteArray,
    val descriptor: ByteArray,
    val lshKey: Int,
    val mode: String = "ALL",
    /** v34 şablonun kullanıldığı tuval türleri (sunucuda türe göre süzme; eski uygulamalar yok sayar). */
    val kinds: List<String> = emptyList(),
)

/** Sunucudaki vektör şekil (kütüphane şekli): çizgileri hazır (birim kutuda). */
data class CatalogVector(
    val id: String,
    val title: String,
    val categoryId: String,
    val categoryTitle: String,
    val section: String,
    val audience: Audience,
    val fit: Fit,
    val aspect: Float,
    val tags: List<String>,
    val paths: List<VPath>,
)

/** Modun temel sinir ağı ağırlıkları ([ShapeNet] ikili biçimi). */
data class CatalogModeNet(val mode: String, val version: Int, val format: String, val weights: ByteArray)

/** Katalog JSON kodlayıcısı (sunucu sözleşmesi; alanların içeriğini sunucu yorumlamaz). */
object CatalogCodec {
    private val b64e = Base64.getEncoder()
    private val b64d = Base64.getDecoder()
    val json = Json { ignoreUnknownKeys = true }

    // ---------------------------------------------------------------- şablonlar

    fun template(t: CatalogTemplate): JsonObject = buildJsonObject {
        put("key", t.key); put("templateKey", t.templateKey); put("name", t.name); put("category", t.category)
        put("group", t.group); put("description", t.description); put("solid", t.solid?.let { JsonPrimitive(it) } ?: JsonNull)
        put("strokeCount", t.strokeCount); put("closedMask", t.closedMask); put("aspectBucket", t.aspectBucket)
        put("cornerCount", t.cornerCount); put("geometry", b64e.encodeToString(t.geometry))
        put("descriptor", b64e.encodeToString(t.descriptor)); put("lshKey", t.lshKey); put("mode", t.mode)
        if (t.kinds.isNotEmpty()) put("kinds", JsonArray(t.kinds.map { JsonPrimitive(it) }))
    }

    fun template(o: JsonObject): CatalogTemplate? = runCatching {
        CatalogTemplate(
            key = o.str("key")!!, templateKey = o.str("templateKey") ?: o.str("key")!!, name = o.str("name").orEmpty(),
            category = o.str("category").orEmpty(), group = o.str("group").orEmpty(), description = o.str("description").orEmpty(),
            solid = o.str("solid"), strokeCount = o.int("strokeCount") ?: 1, closedMask = o.int("closedMask") ?: 0,
            aspectBucket = o.int("aspectBucket") ?: 0, cornerCount = o.int("cornerCount") ?: 0,
            geometry = b64d.decode(o.str("geometry")!!), descriptor = b64d.decode(o.str("descriptor")!!),
            lshKey = o.int("lshKey") ?: 0, mode = o.str("mode") ?: "ALL",
            kinds = (o["kinds"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty(),
        )
    }.getOrNull()

    // ---------------------------------------------------------------- vektörler

    fun vector(v: CatalogVector): JsonObject = buildJsonObject {
        put("id", v.id); put("title", v.title); put("categoryId", v.categoryId); put("categoryTitle", v.categoryTitle)
        put("section", v.section); put("audience", v.audience.name); put("fit", v.fit.name); put("aspect", v.aspect)
        put("tags", JsonArray(v.tags.map { JsonPrimitive(it) }))
        put("kinds", JsonArray(CatalogKinds.forVector(v.audience).map { JsonPrimitive(it) }))
        put("paths", encodePaths(v.paths))
    }

    fun vector(o: JsonObject): CatalogVector? = runCatching {
        CatalogVector(
            id = o.str("id")!!, title = o.str("title").orEmpty(), categoryId = o.str("categoryId") ?: "remote",
            categoryTitle = o.str("categoryTitle") ?: "Yeni şekiller", section = o.str("section").orEmpty(),
            audience = runCatching { Audience.valueOf(o.str("audience")!!) }.getOrDefault(Audience.ART),
            fit = runCatching { Fit.valueOf(o.str("fit")!!) }.getOrDefault(Fit.BOX),
            aspect = o["aspect"]?.jsonPrimitive?.floatOrNull ?: 1f,
            tags = (o["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty(),
            paths = decodePaths(o.str("paths")!!),
        )
    }.getOrNull()

    /** Yollar tek JSON metni: `[{"p":[x,y,x,y…],"c":1,"f":"#..","s":"#..","w":1.2,"l":"DASHED","a":0.5,"t":"…","lb":"…","ls":0.1}]`. */
    fun encodePaths(paths: List<VPath>): String = buildJsonArray {
        paths.forEach { p ->
            add(buildJsonObject {
                put("p", JsonArray(p.points.flatMap { listOf(JsonPrimitive(round4(it.x)), JsonPrimitive(round4(it.y))) }))
                if (p.closed) put("c", 1)
                p.fill?.let { put("f", it) }
                p.stroke?.let { put("s", it) }
                if (p.width != 1f) put("w", round4(p.width))
                if (p.line != LineStyle.SOLID) put("l", p.line.name)
                if (p.alpha != 1f) put("a", round4(p.alpha))
                p.tag?.let { put("t", it) }
                p.label?.let { put("lb", it) }
                if (p.labelSize != 0f) put("ls", round4(p.labelSize))
            })
        }
    }.toString()

    fun decodePaths(text: String): List<VPath> = json.parseToJsonElement(text).jsonArray.map { e ->
        val o = e.jsonObject
        val xy = o["p"]!!.jsonArray.map { it.jsonPrimitive.floatOrNull ?: 0f }
        VPath(
            points = List(xy.size / 2) { i -> P(xy[2 * i], xy[2 * i + 1]) },
            closed = (o["c"]?.jsonPrimitive?.intOrNull ?: 0) == 1 || o["c"]?.jsonPrimitive?.booleanOrNull == true,
            fill = o.str("f"), stroke = o.str("s"), width = o["w"]?.jsonPrimitive?.floatOrNull ?: 1f,
            line = o.str("l")?.let { runCatching { LineStyle.valueOf(it) }.getOrNull() } ?: LineStyle.SOLID,
            alpha = o["a"]?.jsonPrimitive?.floatOrNull ?: 1f, label = o.str("lb"), labelSize = o["ls"]?.jsonPrimitive?.floatOrNull ?: 0f,
            tag = o.str("t"),
        )
    }

    /**
     * v36 yolların cihaz veritabanındaki ikili biçimi (JSON'un yerine; sunucu biçimi değişmez). Koordinatlar JSON'daki
     * gibi 4 basamağa yuvarlanır ve tamsayıya çevrilir; her nokta bir öncekinden farkıyla (zigzag + değişken uzunluk)
     * yazılır, böylece çoğu koordinat 1–3 bayt tutar. Ayrıştırma metin taramadan, doğrudan baytlardan yapılır.
     *
     * Biçim: `'V' 1`, yol sayısı; her yol için bayrak, nokta sayısı, noktalar, sonra bayraktaki alanlar sırasıyla.
     */
    fun packPaths(paths: List<VPath>): ByteArray {
        val w = Bytes(64 + paths.sumOf { it.points.size } * 4)
        w.byte('V'.code); w.byte(PACK_VERSION)
        w.varint(paths.size)
        var px = 0; var py = 0
        for (p in paths) {
            var flags = 0
            if (p.closed) flags = flags or F_CLOSED
            if (p.fill != null) flags = flags or F_FILL
            if (p.stroke != null) flags = flags or F_STROKE
            if (p.width != 1f) flags = flags or F_WIDTH
            if (p.line != LineStyle.SOLID) flags = flags or F_LINE
            if (p.alpha != 1f) flags = flags or F_ALPHA
            if (p.tag != null) flags = flags or F_TAG
            if (p.label != null) flags = flags or F_LABEL
            if (p.labelSize != 0f) flags = flags or F_LABEL_SIZE
            w.varint(flags)
            w.varint(p.points.size)
            for (pt in p.points) {
                val x = q4(pt.x); val y = q4(pt.y)
                w.zigzag(x - px); w.zigzag(y - py)
                px = x; py = y
            }
            p.fill?.let { w.string(it) }
            p.stroke?.let { w.string(it) }
            if (flags and F_WIDTH != 0) w.zigzag(q4(p.width))
            if (flags and F_LINE != 0) w.string(p.line.name)
            if (flags and F_ALPHA != 0) w.zigzag(q4(p.alpha))
            p.tag?.let { w.string(it) }
            p.label?.let { w.string(it) }
            if (flags and F_LABEL_SIZE != 0) w.zigzag(q4(p.labelSize))
        }
        return w.toByteArray()
    }

    /** [packPaths] biçimini çözer; tanınmayan ya da bozuk veride boş liste. */
    fun unpackPaths(bytes: ByteArray): List<VPath> = runCatching {
        val r = Reader(bytes)
        if (r.byte() != 'V'.code || r.byte() != PACK_VERSION) return emptyList()
        val n = r.varint()
        val out = ArrayList<VPath>(n)
        var px = 0; var py = 0
        repeat(n) {
            val flags = r.varint()
            val count = r.varint()
            val pts = ArrayList<P>(count)
            repeat(count) {
                px += r.zigzag(); py += r.zigzag()
                pts += P(px / 10000f, py / 10000f)
            }
            val fill = if (flags and F_FILL != 0) r.string() else null
            val stroke = if (flags and F_STROKE != 0) r.string() else null
            val width = if (flags and F_WIDTH != 0) r.zigzag() / 10000f else 1f
            val line = if (flags and F_LINE != 0) r.string().let { runCatching { LineStyle.valueOf(it) }.getOrDefault(LineStyle.SOLID) } else LineStyle.SOLID
            val alpha = if (flags and F_ALPHA != 0) r.zigzag() / 10000f else 1f
            val tag = if (flags and F_TAG != 0) r.string() else null
            val label = if (flags and F_LABEL != 0) r.string() else null
            val labelSize = if (flags and F_LABEL_SIZE != 0) r.zigzag() / 10000f else 0f
            out += VPath(pts, flags and F_CLOSED != 0, fill, stroke, width, line, alpha, label, labelSize, tag)
        }
        out
    }.getOrDefault(emptyList())

    private const val PACK_VERSION = 1
    private const val F_CLOSED = 1
    private const val F_FILL = 2
    private const val F_STROKE = 4
    private const val F_WIDTH = 8
    private const val F_LINE = 16
    private const val F_ALPHA = 32
    private const val F_TAG = 64
    private const val F_LABEL = 128
    private const val F_LABEL_SIZE = 256

    /** JSON'daki [round4] ile aynı yuvarlama, tamsayı olarak. */
    private fun q4(v: Float): Int = Math.round(v * 10000f)

    /** Büyüyen bayt dizisi (kilitsiz, kutulamasız). */
    private class Bytes(capacity: Int) {
        private var buf = ByteArray(capacity.coerceAtLeast(16))
        private var size = 0

        private fun room(n: Int) {
            if (size + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + n))
        }

        fun byte(v: Int) { room(1); buf[size++] = v.toByte() }

        fun varint(value: Int) {
            room(5)
            var v = value
            while (v and 0x7F.inv() != 0) { buf[size++] = ((v and 0x7F) or 0x80).toByte(); v = v ushr 7 }
            buf[size++] = v.toByte()
        }

        fun zigzag(v: Int) = varint((v shl 1) xor (v shr 31))

        fun string(s: String) {
            val b = s.toByteArray(Charsets.UTF_8)
            varint(b.size); room(b.size)
            System.arraycopy(b, 0, buf, size, b.size); size += b.size
        }

        fun toByteArray(): ByteArray = buf.copyOf(size)
    }

    private class Reader(private val buf: ByteArray) {
        private var pos = 0

        fun byte(): Int = buf[pos++].toInt() and 0xFF

        fun varint(): Int {
            var shift = 0; var out = 0
            while (true) {
                val b = buf[pos++].toInt()
                out = out or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) return out
                shift += 7
                require(shift < 35) { "bozuk sayı" }
            }
        }

        fun zigzag(): Int { val v = varint(); return (v ushr 1) xor -(v and 1) }

        fun string(): String {
            val n = varint()
            require(n >= 0 && pos + n <= buf.size) { "bozuk yazı" }
            return String(buf, pos, n, Charsets.UTF_8).also { pos += n }
        }
    }

    // ---------------------------------------------------------------- ağlar

    fun modeNet(n: CatalogModeNet): JsonObject = buildJsonObject {
        put("mode", n.mode); put("version", n.version); put("format", n.format); put("weights", b64e.encodeToString(n.weights))
    }

    fun modeNet(o: JsonObject): CatalogModeNet? = runCatching {
        CatalogModeNet(o.str("mode")!!, o.int("version") ?: 0, o.str("format") ?: CatalogInfo.MODE_NET_FORMAT, b64d.decode(o.str("weights")!!))
    }.getOrNull()

    /** Bir liste yanıtının (`{"version": n, "items": [...]}`) öğeleri. */
    fun items(text: String): Pair<Long, List<JsonObject>> {
        val o = json.parseToJsonElement(text).jsonObject
        val version = o["version"]?.jsonPrimitive?.longOrNull ?: 0L
        return version to (o["items"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    }

    private fun round4(v: Float): Float = Math.round(v * 10000f) / 10000f
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull

    // ---------------------------------------------------------------- uygulamayla gelen katalog (tohum)

    /** Uygulamayla gelen şablonlar (imzalarıyla; cihazdaki tohumlamayla aynı hesap). */
    fun bundledTemplates(): List<CatalogTemplate> = ShapeTemplates.catalog.map { t ->
        val sig = ShapeSignatures.of(t.strokes, augment = true)
        CatalogTemplate(
            key = t.key, templateKey = t.key, name = t.name, category = t.category, group = t.group, description = t.description,
            solid = t.solid, strokeCount = sig.strokeCount, closedMask = sig.closedMask, aspectBucket = sig.aspectBucket,
            cornerCount = sig.corners, geometry = ShapeSignatures.encodeGeometry(sig.geometry),
            descriptor = ShapeSignatures.encodeDescriptors(sig.descriptors), lshKey = sig.lsh,
            kinds = CatalogKinds.forTemplate(t.key, t.category),
        )
    }

    /** Uygulamayla gelen kütüphane şekilleri (yolları 512 çözünürlükte örneklenmiş). */
    fun bundledVectors(): List<CatalogVector> = bundledVectorSequence().toList()

    /** Yerleşik vektörler tek tek üretilir: veritabanına yazarken bütün çizgiler aynı anda bellekte tutulmaz. */
    fun bundledVectorSequence(): Sequence<CatalogVector> = ShapeLibrary.bundledCategories.asSequence().flatMap { c ->
        c.shapes.asSequence().map { s ->
            CatalogVector(s.id, s.title, c.id, c.title, c.section, c.audience, s.fit, s.aspect, s.tags, s.paths(512f))
        }
    }

    /**
     * v37 uygulamayla gelen ağlar (tohum dosyası için): her şekil modunun temel ağı kendi sürümüyle
     * ([ShapeNet.modeNetVersion]) ve not alma modunun yazı ağı ([CatalogInfo.TEXT_NET_MODE], [HandwritingNet.VERSION]).
     */
    fun bundledModeNets(): List<CatalogModeNet> {
        val out = ArrayList<CatalogModeNet>()
        for (m in ShapeMode.entries) {
            val bytes = ShapeNet::class.java.getResourceAsStream("/shapenet/mode_${m.name.lowercase()}.bin")?.use { it.readBytes() } ?: continue
            out += CatalogModeNet(m.name, ShapeNet.modeNetVersion(m), CatalogInfo.MODE_NET_FORMAT, bytes)
        }
        HandwritingNet::class.java.getResourceAsStream(HandwritingNet.RESOURCE)?.use { it.readBytes() }?.let {
            out += CatalogModeNet(CatalogInfo.TEXT_NET_MODE, HandwritingNet.VERSION, CatalogInfo.TEXT_NET_FORMAT, it)
        }
        return out
    }

    /**
     * v37 sunucudan gelen ağı kurar: şekil modu ise [ShapeNet.installRemote], yazı ağı ise [HandwritingNet.installRemote];
     * bilinmeyen mod ya da biçim null (yok sayılır), bozuk ağ false.
     */
    fun installNet(n: CatalogModeNet): Boolean? = when {
        n.mode == CatalogInfo.TEXT_NET_MODE ->
            if (n.format == CatalogInfo.TEXT_NET_FORMAT) HandwritingNet.installRemote(n.version, n.weights) else null
        n.format != CatalogInfo.MODE_NET_FORMAT -> null
        else -> ShapeMode.entries.firstOrNull { it.name == n.mode }?.let { ShapeNet.installRemote(it, n.version, n.weights) }
    }

    /** Uygulamayla gelen tohum dosyasının JSON'u (sunucunun `catalog.json.gz` dosyası). */
    fun seed(modeNets: List<CatalogModeNet>, generatedAt: String): JsonElement = buildJsonObject {
        put("version", CatalogInfo.VERSION)
        put("generatedAt", generatedAt)
        put("templates", JsonArray(bundledTemplates().map { template(it) }))
        put("vectors", JsonArray(bundledVectors().map { vector(it) }))
        put("modeNets", JsonArray(modeNets.map { modeNet(it) }))
    }

    /** Sunucudan gelen vektörlerden kütüphane şekilleri (çizgiler olduğu gibi eklenir). */
    fun libShape(v: CatalogVector): LibShape = LibShape(v.id, v.title, v.fit, v.aspect, v.tags) { v.paths.forEach { addPath(it) } }

    /** Sunucunun yeni kategorileri (kimliği yerleşiklerde olmayanlar). */
    fun newCategories(vectors: List<CatalogVector>, known: Set<String>): List<LibCategory> =
        vectors.filter { it.categoryId !in known }.groupBy { it.categoryId }.map { (id, list) ->
            LibCategory(id, list.first().categoryTitle, list.first().audience, list.map { libShape(it) }, section = list.first().section)
        }
}

/**
 * v34 katalog öğesinin kullanıldığı tuval türleri (CanvasKind adları): sunucu `?kind=` ile süzer, belge ve istatistik
 * için. Uygulama kendi kapsamını [ModeCatalog] ile hesaplar; bu alan yalnız bilgi amaçlıdır.
 */
object CatalogKinds {
    fun forTemplate(key: String, category: String): List<String> = buildList {
        if (ModeCatalog.allows(ShapeMode.ART, key, category)) add("ART")
        if (ModeCatalog.allowsSimpleArt(key, category)) add("SIMPLE_ART")
        if (ModeCatalog.allows(ShapeMode.NOTES, key, category)) { add("NOTES"); add("SIMPLE_NOTES") }
        if (ModeCatalog.allows(ShapeMode.COMIC, key, category)) add("COMIC")
        if (ModeCatalog.allows(ShapeMode.KIDS, key, category)) add("KIDS")
    }

    fun forVector(audience: Audience): List<String> = when (audience) {
        Audience.KIDS -> listOf("KIDS")
        Audience.NOTES -> listOf("NOTES")
        Audience.ART -> listOf("ART", "COMIC")
        else -> listOf("ART", "NOTES", "COMIC")
    }
}
