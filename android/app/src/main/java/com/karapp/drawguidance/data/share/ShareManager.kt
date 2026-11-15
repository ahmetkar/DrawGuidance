package com.karapp.drawguidance.data.share

import android.content.Context
import android.net.Uri
import com.karapp.drawguidance.BuildConfig
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.ImagesRepository
import com.karapp.drawguidance.data.images.SavedImageEntity
import com.karapp.drawguidance.data.local.CanvasDao
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.repository.toUi
import com.karapp.drawguidance.diagnostics.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class PeerState { FOUND, CONNECTING, CONNECTED }

/** Paylaşım sayfasındaki bir cihaz. [key] = "<yol>:<uç nokta>". */
data class SharePeer(
    val key: String,
    val route: ShareRoute,
    val endpoint: String,
    val name: String,
    val platform: String,
    val state: PeerState,
)

data class ShareConnectionRequest(val key: String, val name: String, val platform: String, val code: String?)
data class ShareIncomingOffer(val key: String, val peerName: String, val offer: ShareOffer)
data class ShareTransferStatus(val peerName: String, val title: String, val sending: Boolean, val progress: Float? = null)
data class ShareLiveOut(val canvasId: String, val peerKey: String, val peerName: String)
data class ShareLiveIn(val canvasId: String, val peerKey: String, val peerName: String, val ended: Boolean = false)

sealed interface ShareEvent {
    data class Received(val type: String, val title: String, val from: String, val canvasId: String?) : ShareEvent
    data class Sent(val title: String, val to: String) : ShareEvent
    data class Rejected(val title: String, val by: String) : ShareEvent
    data class Failed(val message: String) : ShareEvent
    /** Yayını alan taraf: yayın tuvali açılsın. */
    data class OpenLive(val canvasId: String) : ShareEvent
    /** Yayını başlatan taraf: yayın yapılan tuval düzenleyicide açılsın. */
    data class OpenBroadcast(val canvasId: String) : ShareEvent
    data class Disconnected(val name: String) : ShareEvent
}

/**
 * v51 cihazlar arası paylaşımın merkezi (uygulama ömrü boyunca tek): bağlantı yollarını ([NearbyTransport],
 * [LanTransport]) birlikte yönetir, cihaz listesini, bağlantı ve teklif onaylarını, tuval/resim gönderip almayı ve
 * canlı tuval yayınını yürütür. Ekranlar yalnız akışları izler ve işlemleri çağırır.
 */
class ShareManager(
    private val context: Context,
    private val settings: ShareSettings,
    private val registry: SharedCanvasRegistry,
    private val packages: SharePackages,
    private val canvasDao: CanvasDao,
    private val assets: CanvasAssetStore,
    private val images: ImagesRepository,
    transports: List<ShareTransport>,
) : ShareTransport.Listener, androidx.lifecycle.DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transports = transports.associateBy { it.route }
    private val installLock = Mutex()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()
    private val _peers = MutableStateFlow<List<SharePeer>>(emptyList())
    val peers: StateFlow<List<SharePeer>> = _peers.asStateFlow()
    private val _request = MutableStateFlow<ShareConnectionRequest?>(null)
    val request: StateFlow<ShareConnectionRequest?> = _request.asStateFlow()
    private val _offer = MutableStateFlow<ShareIncomingOffer?>(null)
    val offer: StateFlow<ShareIncomingOffer?> = _offer.asStateFlow()
    private val _status = MutableStateFlow<ShareTransferStatus?>(null)
    val status: StateFlow<ShareTransferStatus?> = _status.asStateFlow()
    private val _liveOut = MutableStateFlow<ShareLiveOut?>(null)
    val liveOut: StateFlow<ShareLiveOut?> = _liveOut.asStateFlow()
    private val _liveIn = MutableStateFlow<ShareLiveIn?>(null)
    val liveIn: StateFlow<ShareLiveIn?> = _liveIn.asStateFlow()
    /** Yayını alan tarafta tuvalin son hali (görüntüleyici bunu çizer). */
    private val _liveCanvas = MutableStateFlow<CanvasUi?>(null)
    val liveCanvas: StateFlow<CanvasUi?> = _liveCanvas.asStateFlow()
    private val _events = MutableSharedFlow<ShareEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ShareEvent> = _events.asSharedFlow()
    private val _errors = MutableStateFlow<String?>(null)
    /** Son bağlantı yolu hatası (ör. Nearby kullanılamıyor); sayfada küçük uyarı. */
    val errors: StateFlow<String?> = _errors.asStateFlow()

    /** Gönderilmeyi bekleyen teklifler: teklif kimliği → (cihaz, tür, tuval ya da resim). */
    private data class Outgoing(val key: String, val type: String, val title: String, val canvasId: String?, val image: SavedImageEntity?)
    private val outgoing = ConcurrentHashMap<String, Outgoing>()
    /** Yayın: alınan resim kimlikleri eşlemesi, son sıra numarası; gönderen tarafta gönderilmiş resimler. */
    private var liveIds = HashMap<String, String>()
    private var liveSeqIn = -1L
    private var liveOfferId: String? = null
    private val liveSent = HashSet<String>()
    private var liveSeqOut = 0L
    private val liveFrames = Channel<CanvasUi>(Channel.CONFLATED)
    private var liveJob: Job? = null

    init {
        transports.forEach { it.listener = this }
    }

    // ---- Görünürlük ve bağlantı ----
    //
    // Keşif kendiliğindendir: uygulama ön plandayken (ve "görünür ol" açıkken) ya da paylaşım sayfası açıkken cihaz
    // aynı Wi-Fi'deki diğer cihazlarda görünür ve onları bulur. Bağlantı ayrıca istenmez: bir cihaza tuval/resim
    // gönderilince ya da yayın başlatılınca bağlantı kendiliğinden kurulur, karşı taraf da kendiliğinden kabul eder
    // (gelen her teklif ayarlara göre ayrıca onaylanır).

    @Volatile private var foreground = false
    @Volatile private var pages = 0

    override fun onStart(owner: androidx.lifecycle.LifecycleOwner) { foreground = true; refresh() }
    override fun onStop(owner: androidx.lifecycle.LifecycleOwner) { foreground = false; refresh() }

    /** Uygulama açılışında süreç yaşam döngüsüne bağlanır (ana iş parçacığında). */
    fun attachToProcess() {
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch { settings.discoverable.collect { refresh() } }
    }

    /** Paylaşım sayfası açıldı / kapandı: sayfa açıkken "görünür ol" kapalı olsa da görünür olunur. */
    fun pageOpened() { pages++; refresh() }
    fun pageClosed() { pages = (pages - 1).coerceAtLeast(0); refresh() }

    @Synchronized
    private fun refresh() {
        val want = pages > 0 || (foreground && settings.discoverable.value)
        if (want == _active.value) return
        if (want) {
            val name = settings.deviceName.value
            transports.values.forEach { t -> runCatching { t.start(name, settings.instanceId) }.onFailure { onError(t.route, it.message ?: "") } }
            _active.value = true
        } else {
            transports.values.forEach { runCatching { it.stop() } }
            _active.value = false
            _peers.update { list -> list.filter { it.state != PeerState.FOUND } }
        }
    }

    /** Ad değişince görünürlük yeni adla yeniden başlar. */
    @Synchronized
    fun restartIfActive() {
        if (!_active.value) return
        transports.values.forEach { runCatching { it.stop() } }
        _active.value = false
        refresh()
    }

    /** Bağlantı kurulunca yapılacak işler (cihaz anahtarı → işler). */
    private val afterConnect = ConcurrentHashMap<String, MutableList<() -> Unit>>()

    /** Cihaz bağlıysa [action] hemen; değilse bağlantı kendiliğinden kurulur ve kurulunca yapılır. */
    private fun whenConnected(key: String, action: () -> Unit) {
        val peer = _peers.value.firstOrNull { it.key == key } ?: return fail("Cihaz bulunamadı")
        if (peer.state == PeerState.CONNECTED) { action(); return }
        afterConnect.getOrPut(key) { java.util.Collections.synchronizedList(mutableListOf()) }.add(action)
        if (peer.state != PeerState.CONNECTING) {
            setState(key, PeerState.CONNECTING)
            transports[peer.route]?.connect(peer.endpoint)
        }
    }

    fun connect(peer: SharePeer) = whenConnected(peer.key) {}

    fun disconnect(peer: SharePeer) {
        send(peer.key, ShareWire.encode(ShareWire.BYE, "{}"))
        transports[peer.route]?.disconnect(peer.endpoint)
    }

    fun answerRequest(accept: Boolean) {
        val r = _request.value ?: return
        _request.value = null
        val (route, endpoint) = split(r.key) ?: return
        if (accept) {
            setState(r.key, PeerState.CONNECTING, r.name, r.platform)
            transports[route]?.accept(endpoint)
        } else transports[route]?.reject(endpoint)
    }

    // ---- Gönderme ----

    fun sendCanvas(peerKey: String, canvas: CanvasUi) = scope.launch {
        val id = UUID.randomUUID().toString()
        outgoing[id] = Outgoing(peerKey, ShareWire.TYPE_CANVAS, canvas.name, canvas.localId, null)
        val offer = ShareOffer(id, ShareWire.TYPE_CANVAS, canvas.name, packages.canvasSize(canvas.localId), canvas.kind.name)
        whenConnected(peerKey) { send(peerKey, ShareWire.encode(ShareWire.OFFER, ShareWire.text(offer))) }
        _status.value = ShareTransferStatus(peerName(peerKey), canvas.name, sending = true)
    }

    fun sendImage(peerKey: String, image: SavedImageEntity) = scope.launch {
        val id = UUID.randomUUID().toString()
        outgoing[id] = Outgoing(peerKey, ShareWire.TYPE_IMAGE, image.title, null, image)
        val offer = ShareOffer(id, ShareWire.TYPE_IMAGE, image.title, image.sizeBytes)
        whenConnected(peerKey) { send(peerKey, ShareWire.encode(ShareWire.OFFER, ShareWire.text(offer))) }
        _status.value = ShareTransferStatus(peerName(peerKey), image.title, sending = true)
    }

    /** Yayın teklifi: karşı taraf kabul edince tuval ona açılır ve bu tuvaldeki değişiklikler ona akar. */
    fun startLive(peerKey: String, canvas: CanvasUi) = scope.launch {
        stopLive()
        val id = UUID.randomUUID().toString()
        outgoing[id] = Outgoing(peerKey, ShareWire.TYPE_LIVE, canvas.name, canvas.localId, null)
        val offer = ShareOffer(id, ShareWire.TYPE_LIVE, canvas.name, packages.canvasSize(canvas.localId), canvas.kind.name)
        whenConnected(peerKey) { send(peerKey, ShareWire.encode(ShareWire.OFFER, ShareWire.text(offer))) }
        _status.value = ShareTransferStatus(peerName(peerKey), canvas.name, sending = true)
    }

    fun stopLive() {
        val out = _liveOut.value ?: return
        _liveOut.value = null
        liveJob?.cancel()
        liveJob = null
        send(out.peerKey, ShareWire.encode(ShareWire.LIVE_STOP, ShareWire.text(ShareReply(liveOfferId.orEmpty()))))
    }

    /** Yayını alan taraf yayından ayrılır (tuval kayıtlı kalır). */
    fun leaveLive() {
        val inn = _liveIn.value ?: return
        _liveIn.value = null
        _liveCanvas.value = null
        if (!inn.ended) send(inn.peerKey, ShareWire.encode(ShareWire.LIVE_STOP, "{}"))
    }

    /** Düzenleyici yayın yapılan tuvalin her değişikliğini bildirir (en son hali, ayarlanan sıklıkta gönderilir). */
    fun publishLive(canvas: CanvasUi) {
        if (_liveOut.value?.canvasId == canvas.localId) liveFrames.trySend(canvas)
    }

    fun isBroadcasting(canvasId: String): Boolean = _liveOut.value?.canvasId == canvasId

    fun answerOffer(accept: Boolean) {
        val o = _offer.value ?: return
        _offer.value = null
        reply(o.key, o.offer, accept)
    }

    fun clearStatus() { _status.value = null }
    fun clearError() { _errors.value = null }

    // ---- Bağlantı yollarından gelenler ----

    override fun onFound(route: ShareRoute, endpoint: String, name: String, platform: String) {
        val key = key(route, endpoint)
        _peers.update { list ->
            val old = list.firstOrNull { it.key == key }
            if (old != null) list.map { if (it.key == key) it.copy(name = name, platform = platform) else it }
            else list + SharePeer(key, route, endpoint, name, platform, PeerState.FOUND)
        }
    }

    override fun onLost(route: ShareRoute, endpoint: String) {
        val key = key(route, endpoint)
        _peers.update { list -> list.filterNot { it.key == key && it.state == PeerState.FOUND } }
    }

    override fun onRequest(route: ShareRoute, endpoint: String, name: String, platform: String, code: String?) {
        // Bağlantı kendiliğinden kabul edilir; içerik ancak teklif onaylanınca alınır
        setState(key(route, endpoint), PeerState.CONNECTING, name, platform)
        transports[route]?.accept(endpoint)
    }

    override fun onConnected(route: ShareRoute, endpoint: String, name: String, platform: String) {
        val key = key(route, endpoint)
        setState(key, PeerState.CONNECTED, name, platform)
        send(key, ShareWire.encode(ShareWire.HELLO, ShareWire.text(ShareHello(name = settings.deviceName.value, platform = "android", app = BuildConfig.VERSION_NAME))))
        afterConnect.remove(key)?.let { list -> synchronized(list) { list.toList() }.forEach { runCatching { it() } } }
    }

    override fun onDisconnected(route: ShareRoute, endpoint: String) {
        val key = key(route, endpoint)
        val peer = _peers.value.firstOrNull { it.key == key }
        val wasConnected = peer?.state == PeerState.CONNECTED
        _peers.update { list ->
            list.mapNotNull { p -> if (p.key != key) p else if (_active.value) p.copy(state = PeerState.FOUND) else null }
        }
        if (_request.value?.key == key) _request.value = null
        if (afterConnect.remove(key) != null && !wasConnected) _events.tryEmit(ShareEvent.Failed(peer?.name ?: ""))
        if (_offer.value?.key == key) _offer.value = null
        if (_liveOut.value?.peerKey == key) { _liveOut.value = null; liveJob?.cancel(); liveJob = null }
        _liveIn.value?.takeIf { it.peerKey == key }?.let { _liveIn.value = it.copy(ended = true) }
        outgoing.entries.removeIf { it.value.key == key }
        if (_status.value != null) _status.value = null
        if (wasConnected) _events.tryEmit(ShareEvent.Disconnected(peer?.name ?: ""))
    }

    override fun onProgress(route: ShareRoute, endpoint: String, fraction: Float) {
        _status.update { s -> s?.copy(progress = fraction) ?: ShareTransferStatus(peerName(key(route, endpoint)), "", sending = false, progress = fraction) }
    }

    override fun onError(route: ShareRoute, message: String) {
        _errors.value = message
    }

    override fun onMessage(route: ShareRoute, endpoint: String, bytes: ByteArray) {
        val key = key(route, endpoint)
        val m = runCatching { ShareWire.decode(bytes) }.getOrElse { AppLog.w("Share", "Bozuk mesaj", it); return }
        scope.launch {
            runCatching { handle(key, m) }.onFailure {
                AppLog.e("Share", "Mesaj işlenemedi (${m.type})", it)
                _status.value = null
                _events.tryEmit(ShareEvent.Failed(it.message ?: it.javaClass.simpleName))
            }
        }
    }

    private suspend fun handle(key: String, m: ShareWire.Message) {
        when (m.type) {
            ShareWire.HELLO -> {
                val h = ShareWire.header<ShareHello>(m)
                setState(key, PeerState.CONNECTED, h.name, h.platform)
            }
            ShareWire.OFFER -> {
                val o = ShareWire.header<ShareOffer>(m)
                // v52 canlı yayın daveti kendi ayarıyla (tuval/resim alma ayarından ayrı)
                val ask = if (o.type == ShareWire.TYPE_LIVE) settings.askBeforeLive.value else settings.askBeforeReceive.value
                if (ask) _offer.value = ShareIncomingOffer(key, peerName(key), o)
                else reply(key, o, true)
            }
            ShareWire.ACCEPT -> {
                val r = ShareWire.header<ShareReply>(m)
                val out = outgoing.remove(r.id) ?: return
                deliver(r.id, out)
            }
            ShareWire.REJECT -> {
                val r = ShareWire.header<ShareReply>(m)
                val out = outgoing.remove(r.id) ?: return
                _status.value = null
                _events.tryEmit(ShareEvent.Rejected(out.title, peerName(key)))
            }
            ShareWire.PACKAGE -> receivePackage(key, SharePackage.decode(m))
            ShareWire.RECEIVED -> {
                val title = _status.value?.title.orEmpty()
                _status.value = null
                _events.tryEmit(ShareEvent.Sent(title, peerName(key)))
            }
            ShareWire.LIVE_START -> {
                val pkg = SharePackage.decode(m)
                val ids = HashMap<String, String>()
                val canvasId = installLock.withLock { packages.install(pkg, ids) }
                liveIds = ids
                liveSeqIn = pkg.header.seq
                registry.mark(canvasId, SharedCanvasInfo(pkg.header.from.ifBlank { peerName(key) }, platformOf(key), System.currentTimeMillis(), live = true))
                _liveIn.value = ShareLiveIn(canvasId, key, peerName(key))
                _status.value = null
                refreshLiveCanvas(canvasId)
                if (settings.liveAutoOpen.value) _events.tryEmit(ShareEvent.OpenLive(canvasId))
            }
            ShareWire.LIVE_UPDATE -> {
                val inn = _liveIn.value ?: return
                if (inn.peerKey != key || inn.ended) return
                val pkg = SharePackage.decode(m)
                if (pkg.header.seq <= liveSeqIn) return
                liveSeqIn = pkg.header.seq
                installLock.withLock { packages.install(pkg, liveIds, existingId = inn.canvasId) }
                refreshLiveCanvas(inn.canvasId)
            }
            ShareWire.LIVE_STOP -> {
                _liveIn.value?.takeIf { it.peerKey == key }?.let { _liveIn.value = it.copy(ended = true) }
                if (_liveOut.value?.peerKey == key) { _liveOut.value = null; liveJob?.cancel(); liveJob = null }
            }
            ShareWire.BYE -> split(key)?.let { (route, endpoint) -> transports[route]?.disconnect(endpoint) }
        }
    }

    private fun reply(key: String, o: ShareOffer, accept: Boolean) {
        if (accept && o.type == ShareWire.TYPE_LIVE) {
            // Önceki yayından çıkılır (aynı anda tek yayın alınır)
            leaveLive()
        }
        val type = if (accept) ShareWire.ACCEPT else ShareWire.REJECT
        send(key, ShareWire.encode(type, ShareWire.text(ShareReply(o.id))))
        if (accept) _status.value = ShareTransferStatus(peerName(key), o.title, sending = false)
    }

    private suspend fun deliver(id: String, out: Outgoing) {
        val from = settings.deviceName.value
        when (out.type) {
            ShareWire.TYPE_CANVAS -> {
                val pkg = packages.canvasPackage(out.canvasId ?: return, ShareWire.TYPE_CANVAS, id, from) ?: return fail("Tuval bulunamadı")
                send(out.key, pkg.encode(ShareWire.PACKAGE))
                _status.value = ShareTransferStatus(peerName(out.key), out.title, sending = true)
            }
            ShareWire.TYPE_IMAGE -> {
                val img = out.image ?: return
                val bytes = runCatching { readUri(img.uri) }.getOrNull() ?: return fail("Resim okunamadı")
                val header = SharePackageHeader(
                    id = id, type = ShareWire.TYPE_IMAGE, name = img.title, contentLen = bytes.size,
                    mime = img.mime, imageW = img.width, imageH = img.height, from = from,
                )
                send(out.key, SharePackage(header, bytes, emptyList()).encode(ShareWire.PACKAGE))
            }
            ShareWire.TYPE_LIVE -> {
                val canvasId = out.canvasId ?: return
                liveSent.clear()
                liveSeqOut = 0
                liveOfferId = id
                // İlk paket kayıtlı tuvalin tamamı; gönderilen resimler yayın boyunca yeniden gönderilmez
                val pkg = packages.canvasPackage(canvasId, ShareWire.TYPE_LIVE, id, from) ?: return fail("Tuval bulunamadı")
                liveSent += pkg.header.assets.map { it.id }
                send(out.key, pkg.encode(ShareWire.LIVE_START))
                _liveOut.value = ShareLiveOut(canvasId, out.key, peerName(out.key))
                _status.value = null
                startLiveLoop(id)
                _events.tryEmit(ShareEvent.OpenBroadcast(canvasId))
            }
        }
    }

    private fun startLiveLoop(id: String) {
        liveJob?.cancel()
        // Bekleyen eski kare atılır
        liveFrames.tryReceive()
        liveJob = scope.launch {
            for (frame in liveFrames) {
                val out = _liveOut.value ?: break
                if (frame.localId != out.canvasId) continue
                runCatching {
                    val pkg = packages.livePackage(frame, id, ++liveSeqOut, liveSent, settings.deviceName.value)
                    send(out.peerKey, pkg.encode(ShareWire.LIVE_UPDATE))
                }.onFailure { AppLog.w("Share", "Yayın karesi gönderilemedi", it) }
                delay(settings.liveIntervalMs)
            }
        }
    }

    private suspend fun receivePackage(key: String, pkg: SharePackage) {
        val h = pkg.header
        val from = h.from.ifBlank { peerName(key) }
        when (h.type) {
            ShareWire.TYPE_IMAGE -> {
                images.saveReceived(pkg.content, h.name, h.mime ?: "image/png", h.imageW, h.imageH, "share:$from")
                send(key, ShareWire.encode(ShareWire.RECEIVED, ShareWire.text(ShareReply(h.id))))
                _status.value = null
                _events.tryEmit(ShareEvent.Received(ShareWire.TYPE_IMAGE, h.name, from, null))
            }
            else -> {
                val canvasId = installLock.withLock { packages.install(pkg, HashMap()) }
                registry.mark(canvasId, SharedCanvasInfo(from, platformOf(key), System.currentTimeMillis()))
                send(key, ShareWire.encode(ShareWire.RECEIVED, ShareWire.text(ShareReply(h.id, canvasId))))
                _status.value = null
                _events.tryEmit(ShareEvent.Received(ShareWire.TYPE_CANVAS, h.name, from, canvasId))
            }
        }
    }

    private suspend fun refreshLiveCanvas(canvasId: String) {
        val c = canvasDao.getByLocalId(canvasId)?.toUi() ?: return
        runCatching { assets.ensureLoaded(c) }
        _liveCanvas.value = c
    }

    private fun readUri(uri: String): ByteArray {
        val u = Uri.parse(uri)
        return if (u.scheme == "file") java.io.File(requireNotNull(u.path)).readBytes()
        else requireNotNull(context.contentResolver.openInputStream(u)).use { it.readBytes() }
    }

    private fun fail(message: String) {
        _status.value = null
        _events.tryEmit(ShareEvent.Failed(message))
    }

    private fun send(key: String, bytes: ByteArray) {
        val (route, endpoint) = split(key) ?: return
        runCatching { transports[route]?.send(endpoint, bytes) }.onFailure { AppLog.w("Share", "Gönderilemedi", it) }
    }

    private fun setState(key: String, state: PeerState, name: String? = null, platform: String? = null) {
        _peers.update { list ->
            val old = list.firstOrNull { it.key == key }
            if (old != null) list.map { if (it.key == key) it.copy(state = state, name = name ?: it.name, platform = platform ?: it.platform) else it }
            else {
                val (route, endpoint) = split(key) ?: return@update list
                list + SharePeer(key, route, endpoint, name ?: "?", platform ?: "", state)
            }
        }
    }

    private fun peerName(key: String) = _peers.value.firstOrNull { it.key == key }?.name ?: "?"
    private fun platformOf(key: String) = _peers.value.firstOrNull { it.key == key }?.platform ?: ""
    private fun key(route: ShareRoute, endpoint: String) = "${route.name}:$endpoint"
    private fun split(key: String): Pair<ShareRoute, String>? {
        val i = key.indexOf(':')
        if (i <= 0) return null
        val route = runCatching { ShareRoute.valueOf(key.substring(0, i)) }.getOrNull() ?: return null
        return route to key.substring(i + 1)
    }
}
