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

/** Bağlantı yolu düzeyinde tanışma (TCP'nin ilk çerçevesi; iOS `LanTransport.swift` ile aynı). */
@Serializable
internal data class LanHello(val id: String, val name: String, val platform: String)

/**
 * v51 aynı Wi-Fi ağındaki cihazlar (Android ↔ iOS, Android ↔ Android): Bonjour/DNS-SD ([ShareWire.LAN_SERVICE_TYPE])
 * ile görünür olur ve arar; bağlantı TCP üzerindendir. Her çerçeve [u32 BE uzunluk][veri]. İlk çerçeve bağlantı
 * yolunun tanışmasıdır: bağlanan 0x7F + [LanHello] gönderir, kabul eden 0x7E + [LanHello] ile yanıtlar.
 */
class LanTransport(context: Context) : ShareTransport {
    override val route = ShareRoute.LAN
    override var listener: ShareTransport.Listener? = null

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var me = LanHello("", "Android", "android")

    private class Peer(val host: InetAddress, val port: Int, val name: String, val platform: String)
    private inner class Conn(val socket: Socket, val endpoint: String) {
        val out = DataOutputStream(socket.getOutputStream().buffered(64 * 1024))
        val writer: ExecutorService = Executors.newSingleThreadExecutor()
        fun write(bytes: ByteArray) {
            writer.execute {
                runCatching { synchronized(out) { out.writeInt(bytes.size); out.write(bytes); out.flush() } }
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
    private val pending = ConcurrentHashMap<String, Pair<Socket, LanHello>>()
    private val resolveQueue = java.util.concurrent.LinkedBlockingQueue<NsdServiceInfo>()
    @Volatile private var resolving = false

    override fun start(name: String, instanceId: String) {
        if (server != null) return
        me = LanHello(instanceId, name, "android")
        val ss = runCatching { ServerSocket(0) }.getOrElse { fail("Sunucu açılamadı", it); return }
        server = ss
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
            s.soTimeout = 0
            pending[hello.id]?.let { runCatching { it.first.close() } }
            pending[hello.id] = s to hello
            listener?.onRequest(route, hello.id, hello.name, hello.platform, null)
        }.onFailure { runCatching { s.close() } }
    }

    override fun stop() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        runCatching { server?.close() }
        server = null
        found.clear()
        serviceToEndpoint.clear()
        resolveQueue.clear()
    }

    override fun connect(endpoint: String) {
        val peer = found[endpoint] ?: run { listener?.onDisconnected(route, endpoint); return }
        Thread({
            runCatching {
                val s = Socket()
                s.connect(InetSocketAddress(peer.host, peer.port), 6_000)
                val out = DataOutputStream(s.getOutputStream())
                val hello = byteArrayOf(HELLO_REQ) + ShareWire.json.encodeToString(LanHello.serializer(), me).encodeToByteArray()
                out.writeInt(hello.size); out.write(hello); out.flush()
                // Karşı taraf kullanıcıya sorar: en çok 2 dakika beklenir
                s.soTimeout = 120_000
                val input = DataInputStream(s.getInputStream().buffered(64 * 1024))
                val reply = readFrame(input, null)
                require(reply.isNotEmpty() && reply[0] == HELLO_OK) { "reddedildi" }
                val them = ShareWire.json.decodeFromString(LanHello.serializer(), reply.copyOfRange(1, reply.size).decodeToString())
                s.soTimeout = 0
                open(s, endpoint, input, them.name.ifBlank { peer.name }, them.platform.ifBlank { peer.platform })
            }.onFailure {
                AppLog.w("Share", "LAN bağlantısı olmadı", it)
                listener?.onDisconnected(route, endpoint)
            }
        }, "dg-share-out").apply { isDaemon = true }.start()
    }

    override fun accept(endpoint: String) {
        val (s, them) = pending.remove(endpoint) ?: return
        Thread({
            runCatching {
                val out = DataOutputStream(s.getOutputStream())
                val hello = byteArrayOf(HELLO_OK) + ShareWire.json.encodeToString(LanHello.serializer(), me).encodeToByteArray()
                out.writeInt(hello.size); out.write(hello); out.flush()
                open(s, endpoint, DataInputStream(s.getInputStream().buffered(64 * 1024)), them.name, them.platform)
            }.onFailure { runCatching { s.close() }; listener?.onDisconnected(route, endpoint) }
        }, "dg-share-acc").apply { isDaemon = true }.start()
    }

    override fun reject(endpoint: String) {
        pending.remove(endpoint)?.let { runCatching { it.first.close() } }
    }

    /** Bağlantı kuruldu: okuma döngüsü bu iş parçacığında sürer. */
    private fun open(s: Socket, endpoint: String, input: DataInputStream, name: String, platform: String) {
        conns.remove(endpoint)?.close()
        val conn = Conn(s, endpoint)
        conns[endpoint] = conn
        listener?.onConnected(route, endpoint, name, platform)
        try {
            while (true) {
                val frame = readFrame(input) { f -> listener?.onProgress(route, endpoint, f) }
                listener?.onMessage(route, endpoint, frame)
            }
        } catch (_: Exception) {
        } finally {
            conn.close()
        }
    }

    private fun readFrame(input: DataInputStream, progress: ((Float) -> Unit)?): ByteArray {
        val n = input.readInt()
        if (n < 0 || n > ShareWire.MAX_MESSAGE) throw EOFException("çerçeve bozuk")
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
        pending.values.forEach { runCatching { it.first.close() } }
        pending.clear()
    }

    private fun fail(what: String, e: Throwable) {
        AppLog.w("Share", "LAN: $what", e)
        listener?.onError(route, what)
    }

    private companion object {
        const val HELLO_REQ: Byte = 0x7F
        const val HELLO_OK: Byte = 0x7E
    }
}
