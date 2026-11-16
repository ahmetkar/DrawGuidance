package com.karapp.drawguidance.data.share

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.karapp.drawguidance.diagnostics.AppLog
import kotlinx.serialization.Serializable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Bağlantı yolu düzeyinde tanışma (TCP'nin ilk çerçevesi; iOS `LanTransport.swift` ile aynı). v54: [v] protokol,
 * [pk] geçici P-256 açık anahtarı (base64), [k] bağlanan bu cihazla daha önce eşleşmiş mi, [m] kabul edenin seçtiği
 * eşleşme yolu ("code": ekrandaki şifre, "known": saklanan güven anahtarı).
 */
@Serializable
internal data class LanHello(
    val id: String,
    val name: String,
    val platform: String,
    val v: Int = 1,
    val pk: String? = null,
    val k: Boolean = false,
    val m: String? = null,
)

/** v54 eşleşmiş cihazların güven anahtarları (cihaz kimliği → anahtar). */
internal class ShareTrustStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dg-share-trust", Context.MODE_PRIVATE)
    fun get(id: String): ByteArray? = prefs.getString(id, null)?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }.getOrNull() }
    fun put(id: String, key: ByteArray) { prefs.edit().putString(id, android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)).apply() }
    fun remove(id: String) { prefs.edit().remove(id).apply() }
}

/**
 * v51 aynı Wi-Fi ağındaki cihazlar (Android ↔ iOS, Android ↔ Android): Bonjour/DNS-SD ([ShareWire.LAN_SERVICE_TYPE])
 * ile görünür olur ve arar; bağlantı TCP üzerindendir. Her çerçeve [u32 BE uzunluk][veri]. İlk çerçeve bağlantı
 * yolunun tanışmasıdır: bağlanan 0x7F + [LanHello] gönderir, kabul eden 0x7E + [LanHello] ile yanıtlar.
 *
 * v54 her bağlantı eşleşme şifresiyle doğrulanır ve şifrelenir ([SharePairing]): kabul eden cihaz 6 haneli şifre
 * gösterir, bağlanan cihazda girilir (daha önce eşleşmiş cihazlarda saklanan güven anahtarı kullanılır). Bağlanan
 * 0x7D + doğrulama kodu gönderir; kabul eden doğrularsa 0x7B + kendi kodu, değilse 0x7C ile yanıtlar. Sonraki her
 * çerçeve AES-GCM ile şifrelidir. Şifresiz (eski sürüm) bağlantı kabul edilmez.
 */
class LanTransport(context: Context) : ShareTransport {
    override val route = ShareRoute.LAN
    override var listener: ShareTransport.Listener? = null

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var me = LanHello("", "Android", "android")
    private val trust = ShareTrustStore(context)
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
    private var multicast: android.net.wifi.WifiManager.MulticastLock? = null

    private class Peer(val host: InetAddress, val port: Int, val name: String, val platform: String)
    private inner class Conn(val socket: Socket, val endpoint: String, val sealer: SharePairing.Sealer) {
        val out = DataOutputStream(socket.getOutputStream().buffered(64 * 1024))
        val writer: ExecutorService = Executors.newSingleThreadExecutor()
        fun write(bytes: ByteArray) {
            writer.execute {
                // Şifreleme yazma sırasıyla aynı iş parçacığında (sayaç sırası korunur)
                runCatching { val sealed = sealer.seal(bytes); synchronized(out) { out.writeInt(sealed.size); out.write(sealed); out.flush() } }
                    .onFailure { close() }
            }
        }
        fun close() {
            runCatching { socket.close() }
            writer.shutdown()
            if (conns.remove(endpoint, this)) listener?.onDisconnected(route, endpoint)
        }
    }

    private val found = ConcurrentHashMap<String, Peer>()
    private val serviceToEndpoint = ConcurrentHashMap<String, String>()
    private val conns = ConcurrentHashMap<String, Conn>()
    /** Kabul edilmeyi bekleyen gelen bağlantılar (B): soket, tanışma, geçici anahtar, ilk çerçeve, gösterilen şifre. */
    private class Incoming(val socket: Socket, val hello: LanHello, val request: ByteArray, val keys: java.security.KeyPair, val code: String?)
    private val pending = ConcurrentHashMap<String, Incoming>()
    /** Şifre girilmesini bekleyen giden bağlantılar (A). */
    private class Outgoing(val socket: Socket, val input: DataInputStream, val z: ByteArray, val transcript: ByteArray, val them: LanHello, val name: String, val platform: String)
    private val awaitingCode = ConcurrentHashMap<String, Outgoing>()
    private val resolveQueue = java.util.concurrent.LinkedBlockingQueue<NsdServiceInfo>()
    @Volatile private var resolving = false

    override fun start(name: String, instanceId: String) {
        if (server != null) return
        me = LanHello(instanceId, name, "android")
        val ss = runCatching { ServerSocket(0) }.getOrElse { fail("Sunucu açılamadı", it); return }
        server = ss
        // Bazı cihazlar çoklu yayın paketlerini kilitsiz süzer: keşif sürerken kilit tutulur
        multicast = runCatching { wifi?.createMulticastLock("dg-share")?.apply { setReferenceCounted(false); acquire() } }.getOrNull()
        Thread({ acceptLoop(ss) }, "dg-share-lan").apply { isDaemon = true }.start()
        register(ss.localPort)
        discover()
    }

    private fun register(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = me.name.take(30) + " · DG"
            serviceType = ShareWire.LAN_SERVICE_TYPE
            setPort(port)
            setAttribute("id", me.id)
            setAttribute("p", "android")
            setAttribute("n", me.name.take(60))
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { AppLog.w("Share", "NSD kaydı olmadı: $code") }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
        }
        registration = l
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { fail("Ağda görünür olunamadı", it) }
    }

    private fun discover() {
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { AppLog.w("Share", "NSD arama başlamadı: $code") }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                resolveQueue.offer(info)
                resolveNext()
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                val ep = serviceToEndpoint.remove(info.serviceName) ?: return
                found.remove(ep)
                if (!conns.containsKey(ep)) listener?.onLost(route, ep)
            }
        }
        discovery = l
        runCatching { nsd.discoverServices(ShareWire.LAN_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { fail("Ağda arama yapılamadı", it) }
    }

    /** Android 14'ten önce aynı anda tek çözümleme yapılabilir: sırayla. */
    @Synchronized
    private fun resolveNext() {
        if (resolving) return
        val info = resolveQueue.poll() ?: return
        resolving = true
        @Suppress("DEPRECATION")
        runCatching {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) { done() }
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val attrs = info.attributes
                    val id = attrs["id"]?.decodeToString().orEmpty()
                    val host = info.host
                    if (id.isNotBlank() && id != me.id && host != null) {
                        val name = attrs["n"]?.decodeToString()?.ifBlank { null } ?: info.serviceName.removeSuffix(" · DG")
                        val platform = attrs["p"]?.decodeToString().orEmpty().ifBlank { "android" }
                        found[id] = Peer(host, info.port, name, platform)
                        serviceToEndpoint[info.serviceName] = id
                        listener?.onFound(route, id, name, platform)
                    }
                    done()
                }
                fun done() { synchronized(this@LanTransport) { resolving = false }; resolveNext() }
            })
        }.onFailure { resolving = false }
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (!ss.isClosed) {
            val s = runCatching { ss.accept() }.getOrNull() ?: break
            Thread({ handshakeIncoming(s) }, "dg-share-in").apply { isDaemon = true }.start()
        }
    }

    private fun handshakeIncoming(s: Socket) {
        runCatching {
            s.soTimeout = 15_000
            val input = DataInputStream(s.getInputStream().buffered(64 * 1024))
            val first = readFrame(input, null)
            require(first.isNotEmpty() && first[0] == HELLO_REQ) { "tanışma yok" }
            val hello = ShareWire.json.decodeFromString(LanHello.serializer(), first.copyOfRange(1, first.size).decodeToString())
            require(hello.id.isNotBlank() && hello.id != me.id)
            if (hello.v < SharePairing.VERSION || hello.pk.isNullOrBlank()) {
                // Şifresiz eski sürüm: bağlantı kabul edilmez
                fail("Karşı cihazın uygulaması güncel değil", null)
                runCatching { s.close() }
                return
            }
            // Daha önce eşleşmişse saklanan anahtar; değilse ekranda gösterilecek yeni şifre
            val known = hello.k && trust.get(hello.id) != null
            pending[hello.id]?.let { runCatching { it.socket.close() } }
            pending[hello.id] = Incoming(s, hello, first, SharePairing.newKeyPair(), if (known) null else SharePairing.newCode())
            listener?.onRequest(route, hello.id, hello.name, hello.platform, pending[hello.id]?.code)
        }.onFailure { runCatching { s.close() } }
    }

    override fun stop() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        runCatching { server?.close() }
        server = null
        runCatching { multicast?.release() }
        multicast = null
        found.clear()
        serviceToEndpoint.clear()
        resolveQueue.clear()
    }

    override fun connect(endpoint: String) {
        val peer = found[endpoint] ?: run { listener?.onDisconnected(route, endpoint); return }
        Thread({
            var socket: Socket? = null
            runCatching {
                val s = Socket().also { socket = it }
                s.connect(InetSocketAddress(peer.host, peer.port), 6_000)
                val out = DataOutputStream(s.getOutputStream())
                val keys = SharePairing.newKeyPair()
                val stored = trust.get(endpoint)
                val hello = byteArrayOf(HELLO_REQ) + ShareWire.json.encodeToString(
                    LanHello.serializer(),
                    me.copy(v = SharePairing.VERSION, pk = b64(SharePairing.publicRaw(keys)), k = stored != null),
                ).encodeToByteArray()
                out.writeInt(hello.size); out.write(hello); out.flush()
                // Karşı taraf kullanıcıya sorar: en çok 2 dakika beklenir
                s.soTimeout = 120_000
                val input = DataInputStream(s.getInputStream().buffered(64 * 1024))
                val reply = readFrame(input, null)
                require(reply.isNotEmpty() && reply[0] == HELLO_OK) { "reddedildi" }
                val them = ShareWire.json.decodeFromString(LanHello.serializer(), reply.copyOfRange(1, reply.size).decodeToString())
                if (them.v < SharePairing.VERSION || them.pk.isNullOrBlank()) {
                    fail("Karşı cihazın uygulaması güncel değil", null)
                    error("eski sürüm")
                }
                val z = SharePairing.sharedSecret(keys, unb64(them.pk))
                val transcript = SharePairing.transcript(hello, reply)
                val name = them.name.ifBlank { peer.name }
                val platform = them.platform.ifBlank { peer.platform }
                if (them.m == MODE_KNOWN && stored != null) {
                    confirmOutgoing(s, input, endpoint, SharePairing.derive(z, stored, transcript), name, platform, known = true)
                } else {
                    // Şifre gerekiyor: karşı cihazın ekranındaki şifre girilince [submitCode] sürdürür
                    if (stored != null) trust.remove(endpoint)
                    awaitingCode[endpoint] = Outgoing(s, input, z, transcript, them, name, platform)
                    listener?.onCodeNeeded(route, endpoint, name)
                }
            }.onFailure {
                AppLog.w("Share", "LAN bağlantısı olmadı", it)
                runCatching { socket?.close() }
                listener?.onDisconnected(route, endpoint)
            }
        }, "dg-share-out").apply { isDaemon = true }.start()
    }

    /** v54 bağlanan cihazda girilen eşleşme şifresiyle doğrulama sürer. */
    override fun submitCode(endpoint: String, code: String) {
        val o = awaitingCode.remove(endpoint) ?: return
        Thread({
            runCatching {
                val keys = SharePairing.derive(o.z, SharePairing.normalizeCode(code).encodeToByteArray(), o.transcript)
                confirmOutgoing(o.socket, o.input, endpoint, keys, o.name, o.platform, known = false)
            }.onFailure {
                runCatching { o.socket.close() }
                listener?.onDisconnected(route, endpoint)
            }
        }, "dg-share-pair").apply { isDaemon = true }.start()
    }

    override fun cancelCode(endpoint: String) {
        awaitingCode.remove(endpoint)?.let { runCatching { it.socket.close() } }
        listener?.onDisconnected(route, endpoint)
    }

    /** A: doğrulama kodunu gönderir, B'ninkini doğrular; doğruysa şifreli bağlantı açılır. */
    private fun confirmOutgoing(s: Socket, input: DataInputStream, endpoint: String, keys: SharePairing.Keys, name: String, platform: String, known: Boolean) {
        val out = DataOutputStream(s.getOutputStream())
        val tag = byteArrayOf(PAIR_PROOF) + SharePairing.confirmTag(keys, "A")
        out.writeInt(tag.size); out.write(tag); out.flush()
        s.soTimeout = 30_000
        val reply = readFrame(input, null)
        if (reply.isEmpty() || reply[0] != PAIR_OK || !SharePairing.same(reply.copyOfRange(1, reply.size), SharePairing.confirmTag(keys, "B"))) {
            if (known) trust.remove(endpoint)
            fail(if (known) "Eşleşme yenilenmeli" else "Eşleşme şifresi yanlış", null)
            runCatching { s.close() }
            listener?.onDisconnected(route, endpoint)
            return
        }
        s.soTimeout = 0
        trust.put(endpoint, keys.trust)
        open(s, endpoint, input, name, platform, SharePairing.Sealer(keys.aToB), SharePairing.Opener(keys.bToA))
    }

    override fun accept(endpoint: String) {
        val p = pending.remove(endpoint) ?: return
        val s = p.socket
        acceptingSockets[endpoint] = s
        Thread({
            runCatching {
                val out = DataOutputStream(s.getOutputStream())
                val hello = byteArrayOf(HELLO_OK) + ShareWire.json.encodeToString(
                    LanHello.serializer(),
                    me.copy(v = SharePairing.VERSION, pk = b64(SharePairing.publicRaw(p.keys)), m = if (p.code == null) MODE_KNOWN else MODE_CODE),
                ).encodeToByteArray()
                out.writeInt(hello.size); out.write(hello); out.flush()
                val z = SharePairing.sharedSecret(p.keys, unb64(p.hello.pk!!))
                val secret = p.code?.encodeToByteArray() ?: trust.get(endpoint) ?: error("güven anahtarı yok")
                val keys = SharePairing.derive(z, secret, SharePairing.transcript(p.request, hello))
                // Şifrenin girilmesi beklenir (en çok 3 dakika)
                s.soTimeout = 180_000
                val input = DataInputStream(s.getInputStream().buffered(64 * 1024))
                val proof = readFrame(input, null)
                if (proof.isEmpty() || proof[0] != PAIR_PROOF || !SharePairing.same(proof.copyOfRange(1, proof.size), SharePairing.confirmTag(keys, "A"))) {
                    runCatching { val no = byteArrayOf(PAIR_FAIL); out.writeInt(no.size); out.write(no); out.flush() }
                    if (p.code == null) trust.remove(endpoint)
                    fail(if (p.code == null) "Eşleşme yenilenmeli" else "Eşleşme şifresi yanlış", null)
                    error("doğrulanamadı")
                }
                val ok = byteArrayOf(PAIR_OK) + SharePairing.confirmTag(keys, "B")
                out.writeInt(ok.size); out.write(ok); out.flush()
                s.soTimeout = 0
                trust.put(endpoint, keys.trust)
                acceptingSockets.remove(endpoint, s)
                open(s, endpoint, input, p.hello.name, p.hello.platform, SharePairing.Sealer(keys.bToA), SharePairing.Opener(keys.aToB))
            }.onFailure { acceptingSockets.remove(endpoint, s); runCatching { s.close() }; listener?.onDisconnected(route, endpoint) }
        }, "dg-share-acc").apply { isDaemon = true }.start()
    }

    override fun reject(endpoint: String) {
        pending.remove(endpoint)?.let { runCatching { it.socket.close() } }
        // Şifre beklenirken vazgeçildi (soket kapanınca kabul iş parçacığı biter)
        if (!conns.containsKey(endpoint)) handshaking(endpoint)
    }

    /** Doğrulama süren bağlantı: kabul iş parçacığı soketi kapatınca biter. */
    private fun handshaking(endpoint: String) = acceptingSockets.remove(endpoint)?.let { runCatching { it.close() } }
    private val acceptingSockets = ConcurrentHashMap<String, Socket>()

    /** Bağlantı kuruldu: okuma döngüsü bu iş parçacığında sürer. */
    private fun open(s: Socket, endpoint: String, input: DataInputStream, name: String, platform: String, sealer: SharePairing.Sealer, opener: SharePairing.Opener) {
        conns.remove(endpoint)?.close()
        val conn = Conn(s, endpoint, sealer)
        conns[endpoint] = conn
        listener?.onConnected(route, endpoint, name, platform)
        try {
            while (true) {
                val frame = readFrame(input) { f -> listener?.onProgress(route, endpoint, f) }
                // Doğrulanamayan (değiştirilmiş) çerçeve bağlantıyı kapatır
                listener?.onMessage(route, endpoint, opener.open(frame))
            }
        } catch (_: Exception) {
        } finally {
            conn.close()
        }
    }

    private fun readFrame(input: DataInputStream, progress: ((Float) -> Unit)?): ByteArray {
        val n = input.readInt()
        if (n < 0 || n > ShareWire.MAX_MESSAGE + 16) throw EOFException("çerçeve bozuk")
        val buf = ByteArray(n)
        var at = 0
        var reported = 0
        while (at < n) {
            val r = input.read(buf, at, minOf(64 * 1024, n - at))
            if (r < 0) throw EOFException()
            at += r
            if (progress != null && n > 256 * 1024 && at - reported > n / 20) { reported = at; progress(at.toFloat() / n) }
        }
        return buf
    }

    override fun disconnect(endpoint: String) {
        conns[endpoint]?.close()
    }

    override fun send(endpoint: String, message: ByteArray) {
        conns[endpoint]?.write(message) ?: listener?.onError(route, "Bağlantı yok")
    }

    override fun shutdown() {
        stop()
        conns.values.toList().forEach { it.close() }
        pending.values.forEach { runCatching { it.socket.close() } }
        pending.clear()
        awaitingCode.values.forEach { runCatching { it.socket.close() } }
        awaitingCode.clear()
        acceptingSockets.values.forEach { runCatching { it.close() } }
        acceptingSockets.clear()
    }

    private fun fail(what: String, e: Throwable?) {
        AppLog.w("Share", "LAN: $what", e)
        listener?.onError(route, what)
    }

    private companion object {
        const val HELLO_REQ: Byte = 0x7F
        const val HELLO_OK: Byte = 0x7E
        // v54 eşleşme doğrulaması
        const val PAIR_PROOF: Byte = 0x7D
        const val PAIR_FAIL: Byte = 0x7C
        const val PAIR_OK: Byte = 0x7B
        const val MODE_CODE = "code"
        const val MODE_KNOWN = "known"

        fun b64(b: ByteArray): String = java.util.Base64.getEncoder().encodeToString(b)
        fun unb64(s: String): ByteArray = java.util.Base64.getDecoder().decode(s)
    }
}
