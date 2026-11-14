package com.karapp.drawguidance.data.share

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/*
 * v51 cihazlar arası paylaşım protokolü (Android ve iOS'ta bayt düzeyinde aynı; iOS `ShareWire.swift`).
 *
 * Bağlantı türünden bağımsız "mesaj" katmanı:
 *   mesaj = [tür: 1 bayt][JSON uzunluğu: u32 BE][JSON (UTF-8)][ikili kuyruk]
 * İkili kuyruk, JSON başlığındaki uzunluklara göre bölünür (tuval içeriği, resimler).
 *
 * Taşıma: aynı Wi-Fi ağı (Android ve iOS). Cihazlar Bonjour/DNS-SD ile kendiliğinden bulunur, bağlantı TCP'dir ve her
 * mesaj [u32 BE uzunluk][mesaj] çerçevesiyle gider. [ShareChunker] mesaj tabanlı yollar (ileride) için parçalayıcıdır:
 * [mesaj no: u32][parça sırası: u32][parça sayısı: u32][veri].
 *
 * Tuval paketi, hazır şablonların biçimiyle aynı alanları taşır (boyut, kâğıt, `background`/`guide` JSON'u,
 * içerik = ContentCodec baytları, resimler). Alıcı resimlere yeni kimlik verir (tuvaller arası çakışmasın).
 */
object ShareWire {
    const val PROTOCOL = 1

    /** Aynı ağdaki (Wi-Fi) cihazlar için Bonjour/NSD hizmet türü (iOS ile ortak). */
    const val LAN_SERVICE_TYPE = "_dgshare._tcp."

    // Mesaj türleri
    const val HELLO = 1
    const val OFFER = 2
    const val ACCEPT = 3
    const val REJECT = 4
    const val PACKAGE = 5
    const val RECEIVED = 6
    const val LIVE_START = 10
    const val LIVE_UPDATE = 11
    const val LIVE_STOP = 12
    const val BYE = 20

    // Teklif / paket türleri
    const val TYPE_CANVAS = "canvas"
    const val TYPE_IMAGE = "image"
    const val TYPE_LIVE = "live"

    /** Tek mesajın üst sınırı (bozuk ya da kötü niyetli çerçeveye karşı). */
    const val MAX_MESSAGE = 96 * 1024 * 1024

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    class Message(val type: Int, val json: String, val tail: ByteArray) {
        /** Kuyruktaki ardışık parçalar ([lengths] sırasıyla). */
        fun parts(lengths: List<Int>): List<ByteArray> {
            var at = 0
            return lengths.map { n ->
                require(n >= 0 && at + n <= tail.size) { "paket eksik" }
                tail.copyOfRange(at, at + n).also { at += n }
            }
        }
    }

    fun encode(type: Int, header: String, vararg tail: ByteArray): ByteArray {
        val h = header.encodeToByteArray()
        val total = 5 + h.size + tail.sumOf { it.size }
        val out = ByteBuffer.allocate(total)
        out.put(type.toByte()).putInt(h.size).put(h)
        tail.forEach { out.put(it) }
        return out.array()
    }

    fun encode(type: Int, header: String, tail: List<ByteArray>): ByteArray = encode(type, header, *tail.toTypedArray())

    fun decode(bytes: ByteArray): Message {
        require(bytes.size >= 5) { "mesaj kısa" }
        val b = ByteBuffer.wrap(bytes)
        val type = b.get().toInt() and 0xFF
        val n = b.int
        require(n in 0..(bytes.size - 5)) { "başlık bozuk" }
        val header = String(bytes, 5, n, Charsets.UTF_8)
        return Message(type, header, bytes.copyOfRange(5 + n, bytes.size))
    }

    inline fun <reified T> header(m: Message): T = json.decodeFromString(m.json)
    inline fun <reified T> text(value: T): String = json.encodeToString(value)
}

/** Bağlantı kurulunca karşılıklı tanışma. [platform]: "android" | "ios". */
@Serializable
data class ShareHello(val v: Int = ShareWire.PROTOCOL, val name: String, val platform: String, val app: String = "")

/** Gönderme teklifi: alıcı kabul ederse ([ShareWire.ACCEPT]) paket gönderilir. */
@Serializable
data class ShareOffer(
    val id: String,
    val type: String,
    val title: String,
    val bytes: Long = 0,
    val canvasKind: String? = null,
)

@Serializable
data class ShareReply(val id: String, val canvasId: String? = null)

@Serializable
data class ShareAsset(val id: String, val mime: String, val w: Int = 0, val h: Int = 0, val len: Int)

/**
 * Tuval / resim paketi ve canlı yayın güncellemesinin başlığı. Kuyruk: önce [contentLen] bayt içerik (tuvalde
 * ContentCodec, resimde dosyanın kendisi), ardından [assets] sırasıyla resimler.
 *
 * Canlı yayın güncellemesinde [assets] yalnız alıcıya henüz gönderilmemiş resimlerdir; [seq] artarak gider.
 */
@Serializable
data class SharePackageHeader(
    val id: String,
    val type: String,
    val name: String,
    val canvasKind: String = "ART",
    val width: Int = 0,
    val height: Int = 0,
    val backgroundColorHex: String = "#FFFFFF",
    /** CanvasEntity.background biçimi (ortak arka plan + "pages" + "pageSettings"). */
    val background: String = "",
    val guide: String = "",
    val currentPage: Int = 0,
    val aiEnabled: Boolean = true,
    val contentLen: Int = 0,
    val assets: List<ShareAsset> = emptyList(),
    val seq: Long = 0,
    /** Resim paketinde dosya türü ve boyutu. */
    val mime: String? = null,
    val imageW: Int = 0,
    val imageH: Int = 0,
    /** Gönderen cihazın adı (alıcıda "paylaşılan tuval" bilgisi). */
    val from: String = "",
)

/**
 * Mesaj tabanlı bağlantılar için (Nearby, Multipeer) bölme ve birleştirme. Parçalar sırasız gelse de birleşir;
 * aynı anda birden çok mesaj olabilir.
 */
class ShareChunker(private val chunkSize: Int) {
    private var nextId = 1
    private val partial = HashMap<Int, Array<ByteArray?>>()
    private val received = HashMap<Int, Int>()

    @Synchronized
    fun split(message: ByteArray): List<ByteArray> {
        val id = nextId++
        val count = maxOf(1, (message.size + chunkSize - 1) / chunkSize)
        return List(count) { i ->
            val from = i * chunkSize
            val to = minOf(message.size, from + chunkSize)
            ByteBuffer.allocate(12 + (to - from)).putInt(id).putInt(i).putInt(count).put(message, from, to - from).array()
        }
    }

    /** Parçayı ekler; mesaj tamamlandıysa döner. [progress]: (alınan, toplam) parça. */
    @Synchronized
    fun accept(chunk: ByteArray, progress: ((Int, Int) -> Unit)? = null): ByteArray? {
        if (chunk.size < 12) return null
        val b = ByteBuffer.wrap(chunk)
        val id = b.int
        val index = b.int
        val count = b.int
        if (count <= 0 || index !in 0 until count || count.toLong() * chunkSize > ShareWire.MAX_MESSAGE + chunkSize) return null
        if (count == 1) return chunk.copyOfRange(12, chunk.size)
        val parts = partial.getOrPut(id) { arrayOfNulls(count) }
        if (parts.size != count) return null
        if (parts[index] == null) {
            parts[index] = chunk.copyOfRange(12, chunk.size)
            received[id] = (received[id] ?: 0) + 1
        }
        val got = received[id] ?: 0
        progress?.invoke(got, count)
        if (got < count) return null
        partial.remove(id); received.remove(id)
        val out = ByteArrayOutputStream(parts.sumOf { it?.size ?: 0 })
        parts.forEach { out.write(it!!) }
        return out.toByteArray()
    }

    @Synchronized
    fun reset() { partial.clear(); received.clear() }
}
