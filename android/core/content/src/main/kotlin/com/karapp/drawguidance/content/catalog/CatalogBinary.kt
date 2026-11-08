package com.karapp.drawguidance.content.catalog

import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.Fit

/**
 * v37 katalog bölümlerinin ikili aktarım biçimi: sunucu `Accept: application/vnd.drawguidance.catalog+bin` (ya da
 * `?format=bin`) isteyen istemciye vektörleri ve temel ağları JSON yerine bu biçimde gönderir (JSON öntanımlı kalır).
 * Vektörlerin yolları cihaz veritabanındaki biçimin ([CatalogCodec.packPaths]) aynısıdır: metin ayrıştırılmadan çözülür,
 * istenirse olduğu gibi veritabanına yazılır ([VectorEntry.packedPaths]). Ağ ağırlıkları base64'süz, ham baytlardır.
 *
 * Sunucu (Java, `CatalogBinary.java`) aynı veriden bayt bayt aynı çıktıyı üretir; iki taraf da ortak örnek dosyayla
 * (`catalog-binary-fixture.json` → `.hex`) sınanır. Biçimin tamamı `backend/docs/v37-api.md` belgesinde.
 *
 * Kap:
 * ```
 * 'D' 'G' 'C' 'B'        sihirli baytlar
 * biçim sürümü          1 bayt (= 1)
 * bölüm                 1 bayt (1 = vektörler, 2 = temel ağlar)
 * katalog sürümü        varint64
 * öğe sayısı            varint
 * öğeler                her biri: uzunluk (varint) + öğe baytları
 * ```
 * Sayılar [CatalogCodec.packPaths] ile aynı: varint (7 bitlik gruplar, küçük grup önce), işaretliler zigzag; yazılar
 * UTF-8 bayt uzunluğu (varint) + baytlar. Öğe uzunluğu sayesinde yeni sürümler öğenin sonuna alan ekleyebilir; eski
 * çözücüler tanımadıkları bayrakları ve öğenin kalanını atlar.
 *
 * Vektör öğesi: bayraklar (varint), `id`, sonra bayraktaki alanlar sırasıyla (`title`, `categoryId`, `categoryTitle`,
 * `section`, `audience`, `fit`, `aspect` (IEEE-754 float32, büyük uçlu), `tags`, `kinds` (sayı + yazılar)), en son
 * yollar (uzunluk + [CatalogCodec.packPaths] baytları). Bayrağı olmayan alan JSON'da yoktu demektir; çözücü JSON
 * çözücüsünün ([CatalogCodec.vector]) öntanımlılarını kullanır.
 *
 * Ağ öğesi: bayraklar (varint), `mode`, [N_VERSION] varsa sürüm (zigzag), [N_FORMAT] varsa biçim, en son ağırlıklar
 * (uzunluk + ham baytlar).
 */
object CatalogBinary {
    const val MEDIA_TYPE = "application/vnd.drawguidance.catalog+bin"
    const val FORMAT_VERSION = 1
    const val SECTION_VECTORS = 1
    const val SECTION_MODE_NETS = 2

    /** Vektör öğesinin isteğe bağlı alanları (bayrak bitleri). */
    const val V_TITLE = 1
    const val V_CATEGORY_ID = 2
    const val V_CATEGORY_TITLE = 4
    const val V_SECTION = 8
    const val V_AUDIENCE = 16
    const val V_FIT = 32
    const val V_ASPECT = 64
    const val V_TAGS = 128
    const val V_KINDS = 256
    private const val V_ALL = 511

    /** Ağ öğesinin isteğe bağlı alanları. */
    const val N_VERSION = 1
    const val N_FORMAT = 2

    private val MAGIC = byteArrayOf('D'.code.toByte(), 'G'.code.toByte(), 'C'.code.toByte(), 'B'.code.toByte())

    /** Çözülmüş bölüm: katalog sürümü ve öğeler (sunucudaki sırayla). */
    data class Section<T>(val section: Int, val version: Long, val items: List<T>)

    /**
     * Vektör öğesi bütün alanlarıyla: [kinds] (sunucunun tuval türü süzmesi için; [CatalogVector] taşımaz) ve yolların
     * paketli hali ([CatalogCodec.packPaths] biçimi; cihaz veritabanına yeniden kodlamadan yazılabilir).
     */
    class VectorEntry(val vector: CatalogVector, val kinds: List<String>, val packedPaths: ByteArray)

    /** Baytlar bu biçimde mi (sihirli baytlar)? JSON yanıtını ikiliden ayırmak için. */
    fun isBinary(bytes: ByteArray): Boolean = bytes.size >= 6 && (0 until 4).all { bytes[it] == MAGIC[it] }

    // ---------------------------------------------------------------- vektörler

    /** Vektörler; `kinds` JSON kodlayıcısındaki gibi kitleden hesaplanır ([CatalogKinds.forVector]). */
    fun encodeVectors(version: Long, vectors: List<CatalogVector>): ByteArray {
        val out = Out(256 + vectors.size * 512)
        header(out, SECTION_VECTORS, version, vectors.size)
        val item = Out(1024)
        for (v in vectors) {
            item.reset()
            item.varint(V_ALL)
            item.string(v.id)
            item.string(v.title)
            item.string(v.categoryId)
            item.string(v.categoryTitle)
            item.string(v.section)
            item.string(v.audience.name)
            item.string(v.fit.name)
            item.float32(v.aspect)
            item.strings(v.tags)
            item.strings(CatalogKinds.forVector(v.audience))
            item.bytes(CatalogCodec.packPaths(v.paths))
            out.bytes(item)
        }
        return out.toByteArray()
    }

    /** İkili vektör bölümünü çözer; biçim tanınmıyorsa ya da bozuksa [IllegalArgumentException]. */
    fun decodeVectors(bytes: ByteArray): Section<CatalogVector> =
        decodeVectorEntries(bytes).let { s -> Section(s.section, s.version, s.items.map { it.vector }) }

    fun decodeVectorEntries(bytes: ByteArray): Section<VectorEntry> = decode(bytes, SECTION_VECTORS) { r ->
        val mask = r.varint()
        val id = r.string()
        val title = if (mask and V_TITLE != 0) r.string() else null
        val categoryId = if (mask and V_CATEGORY_ID != 0) r.string() else null
        val categoryTitle = if (mask and V_CATEGORY_TITLE != 0) r.string() else null
        val section = if (mask and V_SECTION != 0) r.string() else null
        val audience = if (mask and V_AUDIENCE != 0) r.string() else null
        val fit = if (mask and V_FIT != 0) r.string() else null
        val aspect = if (mask and V_ASPECT != 0) r.float32() else null
        val tags = if (mask and V_TAGS != 0) r.strings() else emptyList()
        val kinds = if (mask and V_KINDS != 0) r.strings() else emptyList()
        val packed = r.bytes()
        // Öntanımlılar JSON çözücüsündekilerle ([CatalogCodec.vector]) aynı
        val vector = CatalogVector(
            id = id, title = title.orEmpty(), categoryId = categoryId ?: "remote", categoryTitle = categoryTitle ?: "Yeni şekiller",
            section = section.orEmpty(),
            audience = audience?.let { a -> Audience.entries.firstOrNull { it.name == a } } ?: Audience.ART,
            fit = fit?.let { f -> Fit.entries.firstOrNull { it.name == f } } ?: Fit.BOX,
            aspect = aspect ?: 1f, tags = tags, paths = CatalogCodec.unpackPaths(packed),
        )
        VectorEntry(vector, kinds, packed)
    }

    // ---------------------------------------------------------------- temel ağlar

    fun encodeModeNets(version: Long, nets: List<CatalogModeNet>): ByteArray {
        val out = Out(64 + nets.sumOf { it.weights.size + 48 })
        header(out, SECTION_MODE_NETS, version, nets.size)
        val item = Out(64)
        for (n in nets) {
            item.reset()
            item.varint(N_VERSION or N_FORMAT)
            item.string(n.mode)
            item.zigzag(n.version)
            item.string(n.format)
            item.bytes(n.weights)
            out.bytes(item)
        }
        return out.toByteArray()
    }

    fun decodeModeNets(bytes: ByteArray): Section<CatalogModeNet> = decode(bytes, SECTION_MODE_NETS) { r ->
        val mask = r.varint()
        val mode = r.string()
        val version = if (mask and N_VERSION != 0) r.zigzag() else 0
        val format = if (mask and N_FORMAT != 0) r.string() else CatalogInfo.MODE_NET_FORMAT
        CatalogModeNet(mode, version, format, r.bytes())
    }

    // ---------------------------------------------------------------- kap

    private fun header(out: Out, section: Int, version: Long, count: Int) {
        MAGIC.forEach { out.byte(it.toInt()) }
        out.byte(FORMAT_VERSION)
        out.byte(section)
        out.varlong(version)
        out.varint(count)
    }

    private fun <T> decode(bytes: ByteArray, section: Int, item: (Reader) -> T): Section<T> {
        require(isBinary(bytes)) { "ikili katalog değil" }
        val r = Reader(bytes, 4, bytes.size)
        val format = r.byte()
        require(format == FORMAT_VERSION) { "desteklenmeyen ikili katalog sürümü: $format" }
        val got = r.byte()
        require(got == section) { "beklenen bölüm $section, gelen $got" }
        val version = r.varlong()
        val count = r.varint()
        require(count >= 0) { "bozuk öğe sayısı" }
        val out = ArrayList<T>(minOf(count, 65_536))
        repeat(count) {
            val len = r.varint()
            require(len >= 0 && len <= r.remaining()) { "bozuk öğe uzunluğu" }
            // Öğe kendi sınırları içinde okunur; sonundaki tanınmayan (yeni sürüm) alanlar atlanır
            out += item(Reader(bytes, r.pos, r.pos + len))
            r.skip(len)
        }
        return Section(section, version, out)
    }

    /** Büyüyen bayt dizisi ([CatalogCodec.packPaths] yazıcısıyla aynı sayı kodlaması). */
    private class Out(capacity: Int) {
        private var buf = ByteArray(capacity.coerceAtLeast(16))
        private var size = 0

        private fun room(n: Int) {
            if (size + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + n))
        }

        fun reset() { size = 0 }

        fun byte(v: Int) { room(1); buf[size++] = v.toByte() }

        fun varint(value: Int) {
            room(5)
            var v = value
            while (v and 0x7F.inv() != 0) { buf[size++] = ((v and 0x7F) or 0x80).toByte(); v = v ushr 7 }
            buf[size++] = v.toByte()
        }

        fun varlong(value: Long) {
            room(10)
            var v = value
            while (v and 0x7FL.inv() != 0L) { buf[size++] = ((v and 0x7F) or 0x80).toByte(); v = v ushr 7 }
            buf[size++] = v.toByte()
        }

        fun zigzag(v: Int) = varint((v shl 1) xor (v shr 31))

        fun float32(f: Float) {
            val bits = f.toBits()
            byte(bits ushr 24); byte(bits ushr 16); byte(bits ushr 8); byte(bits)
        }

        fun raw(b: ByteArray, off: Int, len: Int) {
            room(len)
            System.arraycopy(b, off, buf, size, len); size += len
        }

        fun bytes(b: ByteArray) { varint(b.size); raw(b, 0, b.size) }

        fun bytes(o: Out) { varint(o.size); raw(o.buf, 0, o.size) }

        fun string(s: String) = bytes(s.toByteArray(Charsets.UTF_8))

        fun strings(list: List<String>) { varint(list.size); list.forEach { string(it) } }

        fun toByteArray(): ByteArray = buf.copyOf(size)
    }

    /** Sınırlı okuyucu: sınır aşılırsa [IllegalArgumentException]. */
    private class Reader(private val buf: ByteArray, var pos: Int, private val end: Int) {
        fun remaining(): Int = end - pos

        fun byte(): Int {
            require(pos < end) { "ikili katalog beklenenden kısa" }
            return buf[pos++].toInt() and 0xFF
        }

        fun skip(n: Int) { require(n in 0..remaining()) { "ikili katalog beklenenden kısa" }; pos += n }

        fun varint(): Int {
            var shift = 0; var out = 0
            while (true) {
                val b = byte()
                out = out or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) return out
                shift += 7
                require(shift < 35) { "bozuk sayı" }
            }
        }

        fun varlong(): Long {
            var shift = 0; var out = 0L
            while (true) {
                val b = byte()
                out = out or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return out
                shift += 7
                require(shift < 70) { "bozuk sayı" }
            }
        }

        fun zigzag(): Int { val v = varint(); return (v ushr 1) xor -(v and 1) }

        fun float32(): Float = Float.fromBits((byte() shl 24) or (byte() shl 16) or (byte() shl 8) or byte())

        fun bytes(): ByteArray {
            val n = varint()
            require(n in 0..remaining()) { "bozuk uzunluk" }
            return buf.copyOfRange(pos, pos + n).also { pos += n }
        }

        fun string(): String {
            val n = varint()
            require(n in 0..remaining()) { "bozuk yazı" }
            return String(buf, pos, n, Charsets.UTF_8).also { pos += n }
        }

        fun strings(): List<String> {
            val n = varint()
            require(n in 0..remaining()) { "bozuk liste" }
            return List(n) { string() }
        }
    }
}
