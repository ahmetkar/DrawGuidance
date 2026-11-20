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
/** v54 bağlanan cihazda eşleşme şifresi sorusu (karşı cihazın ekranındaki şifre girilir). */
data class ShareCodePrompt(val key: String, val name: String, val wrong: Boolean = false)
/** v56 canlı yayın onay sorusu (şifreden önce): [title] yayınlanacak tuval. */
data class ShareLiveRequest(val key: String, val name: String, val platform: String, val title: String)
data class ShareIncomingOffer(val key: String, val peerName: String, val offer: ShareOffer)
data class ShareTransferStatus(val peerName: String, val title: String, val sending: Boolean, val progress: Float? = null)
data class ShareLiveOut(val canvasId: String, val peerKey: String, val peerName: String, val paused: Boolean = false, val startedAt: Long = System.currentTimeMillis(), val sentFrames: Int = 0)
data class ShareLiveIn(val canvasId: String, val peerKey: String, val peerName: String, val ended: Boolean = false, val paused: Boolean = false, val pausedAt: Long = 0L)
/** v56 alan tarafta çizilmekte olan (henüz kaydedilmemiş) çizgi: renk, boyut ve noktalar (x, y, basınç). */
data class LiveInkStroke(val id: String, val brush: String, val color: String, val size: Float, val opacity: Float, val pts: List<Float>, val page: Int)

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
    /** v56 karşı cihazın uygulaması eski: katman modları, maske ve gren gibi yeni özellikler düzleştirilerek gönderildi. */
    data class Flattened(val to: String) : ShareEvent
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
    private val _codePrompt = MutableStateFlow<ShareCodePrompt?>(null)
    val codePrompt: StateFlow<ShareCodePrompt?> = _codePrompt.asStateFlow()
    private val _liveRequest = MutableStateFlow<ShareLiveRequest?>(null)
    /** v56 alan cihazda canlı yayın onayı: Kabul et / Reddet; şifre ancak kabulden sonra gösterilir. */
    val liveRequest: StateFlow<ShareLiveRequest?> = _liveRequest.asStateFlow()
    private val approval = LiveApprovalPolicy()
    private var liveRequestTimeout: Job? = null
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
    private val _liveInk = MutableStateFlow<Map<String, LiveInkStroke>>(emptyMap())
    /** v56 alan tarafta canlı mürekkep (geçici katman); LIVE_UPDATE gelince temizlenir. */
    val liveInk: StateFlow<Map<String, LiveInkStroke>> = _liveInk.asStateFlow()
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
    /** v56 fark paketi: son gönderilen kare, o karenin sırası, son tam paketten beri kare sayısı, tam paket isteği. */
    private var liveLastSent: CanvasUi? = null
    private var liveLastSeq = 0L
    private var liveSinceFull = 0
    @Volatile private var liveResync = false
    /** v56 alan tarafta farkla güncellenen tuvalin veritabanına toplu yazımı. */
    private var livePersistJob: kotlinx.coroutines.Job? = null
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
    private fun whenConnected(key: String, intent: String? = null, title: String? = null, action: () -> Unit) {
        val peer = _peers.value.firstOrNull { it.key == key } ?: return fail("Cihaz bulunamadı")
        if (peer.state == PeerState.CONNECTED) { action(); return }
        afterConnect.getOrPut(key) { java.util.Collections.synchronizedList(mutableListOf()) }.add(action)
        if (peer.state != PeerState.CONNECTING) {
            setState(key, PeerState.CONNECTING)
            transports[peer.route]?.connect(peer.endpoint, intent, title)
        }
    }

    fun connect(peer: SharePeer) = whenConnected(peer.key) {}

    fun disconnect(peer: SharePeer) {
        send(peer.key, ShareWire.encode(ShareWire.BYE, "{}"))
        transports[peer.route]?.disconnect(peer.endpoint)
    }

    /** v56 canlı yayın onayı yanıtlandı: kabulde bağlantı kabul edilir (gerekirse şifre şimdi gösterilir). */
    fun answerLiveRequest(accept: Boolean) {
        val r = _liveRequest.value ?: return
        _liveRequest.value = null
        liveRequestTimeout?.cancel()
        val (route, endpoint) = split(r.key) ?: return
        if (accept) {
            approval.onApproved(r.key)
            setState(r.key, PeerState.CONNECTING, r.name, r.platform)
            transports[route]?.accept(endpoint)
        } else {
            approval.onRejected(r.key)
            transports[route]?.reject(endpoint, REJECT_REASON)
        }
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
        // v52 yalnız çizim, not alma ve çizgi roman tuvalleri yayınlanır
        if (canvas.kind == com.karapp.drawguidance.data.model.CanvasKind.KIDS) return@launch
        stopLive()
        val id = UUID.randomUUID().toString()
        outgoing[id] = Outgoing(peerKey, ShareWire.TYPE_LIVE, canvas.name, canvas.localId, null)
        val offer = ShareOffer(id, ShareWire.TYPE_LIVE, canvas.name, packages.canvasSize(canvas.localId), canvas.kind.name)
        // v56 bağlantının amacı (canlı yayın) tanışmada bildirilir: alan cihaz şifreden önce onay sorar
        whenConnected(peerKey, CONNECT_INTENT_LIVE, canvas.name) { send(peerKey, ShareWire.encode(ShareWire.OFFER, ShareWire.text(offer))) }
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
        // v56 farkla güncellenip henüz yazılmamış çizimler kaybolmasın
        if (livePersistJob?.isActive == true) {
            livePersistJob?.cancel(); livePersistJob = null
            _liveCanvas.value?.takeIf { it.localId == inn.canvasId }?.let { c -> scope.launch { installLock.withLock { runCatching { packages.persistLive(c) } } } }
        }
        _liveIn.value = null
        _liveCanvas.value = null
        _liveInk.value = emptyMap()
        if (!inn.ended) send(inn.peerKey, ShareWire.encode(ShareWire.LIVE_STOP, "{}"))
    }

    /** Düzenleyici yayın yapılan tuvalin her değişikliğini bildirir (en son hali, ayarlanan sıklıkta gönderilir). */
    fun publishLive(canvas: CanvasUi) {
        val out = _liveOut.value ?: return
        if (out.canvasId == canvas.localId && !out.paused) liveFrames.trySend(canvas)
    }

    // ---- v56 ara ver / sürdür sahipleri ----
    //
    // Eskiden pauseLive/resumeLive sayaçsızdı: yayın kabul edilince açılan yeni düzenleyici yayını sürdürüyor, hemen
    // ardından kapanan eski düzenleyicinin onDispose'u pauseLive çağırıyordu; son çağrı "ara ver" olduğu için yayın
    // kalıcı olarak duruyordu (alan taraf ilk hali görüyordu). Artık her düzenleyici örneği bir sahip kimliğiyle
    // kaydolur ([attachEditor]); yayın yalnız (a) tuval hiçbir düzenleyicide açık değilse ya da (b) açık bir
    // düzenleyici kendi adına ara verdiyse durur. Bir düzenleyici yalnız kendi arasını kaldırabilir; ayrılan
    // düzenleyicinin araları da onunla silinir.

    private val owners = LivePauseOwners()

    /** v56 [owner] düzenleyicisi [canvasId] tuvalini açtı. */
    fun attachEditor(canvasId: String, owner: String) { owners.attach(canvasId, owner); recomputePause(canvasId) }

    /** v56 [owner] düzenleyicisi kapandı; onun verdiği aralar da kalkar. */
    fun detachEditor(canvasId: String, owner: String) { owners.detach(canvasId, owner); recomputePause(canvasId) }

    /** v56 tuval şu an bir düzenleyicide açık mı. */
    fun isOpenInEditor(canvasId: String): Boolean = owners.isOpen(canvasId)

    /** v52 yayına ara ver (notta şekil/resim başka tuvalde düzenleniyor, foto düzenleyici, uygulama arka planda). */
    fun pauseLive(canvasId: String, owner: String) { owners.pause(canvasId, owner); recomputePause(canvasId) }

    /** v52 [owner]'ın verdiği ara kalkar; başka ara veren yoksa yayın sürer (düzenleyici son hali [publishLive] ile gönderir). */
    fun resumeLive(canvasId: String, owner: String) { owners.resume(canvasId, owner); recomputePause(canvasId) }

    internal fun shouldPause(canvasId: String): Boolean = owners.shouldPause(canvasId)

    private fun recomputePause(canvasId: String) {
        val out = _liveOut.value?.takeIf { it.canvasId == canvasId } ?: return
        val pause = shouldPause(canvasId)
        if (pause == out.paused) return
        _liveOut.value = out.copy(paused = pause)
        send(out.peerKey, ShareWire.encode(if (pause) ShareWire.LIVE_PAUSE else ShareWire.LIVE_RESUME, "{}"))
    }

    // ---- v56 canlı mürekkep (gönderen) ----
    private var inkStroke: String? = null
    private var inkSent = 0
    private var inkLastAt = 0L

    /**
     * v56 düzenleyici çizilmekte olan çizginin noktalarını bildirir; yeni noktalar en çok ~60 ms'de bir küçük pakette
     * gider. Çizgi bitince normal kare ([publishLive]) gider; alan taraf geçici çizgiyi o zaman siler.
     */
    fun publishInk(canvasId: String, strokeId: String, brush: String, colorHex: String, size: Float, opacity: Float, points: List<com.karapp.drawguidance.data.model.StrokePoint>, page: Int = -1) {
        val out = _liveOut.value ?: return
        if (out.canvasId != canvasId || out.paused) return
        val now = System.currentTimeMillis()
        if (strokeId != inkStroke) { inkStroke = strokeId; inkSent = 0; inkLastAt = 0L }
        if (now - inkLastAt < INK_INTERVAL_MS || points.size <= inkSent) return
        val from = inkSent
        val part = points.subList(from, points.size)
        val flat = ArrayList<Float>(part.size * 3)
        part.forEach { flat += it.x; flat += it.y; flat += it.pressure }
        inkSent = points.size
        inkLastAt = now
        send(out.peerKey, ShareWire.encode(ShareWire.LIVE_INK, ShareWire.json.encodeToString(ShareInk.serializer(), ShareInk(strokeId, brush, colorHex, size, opacity, from, flat, page))))
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

    override fun onRequest(route: ShareRoute, endpoint: String, name: String, platform: String, needsCode: Boolean, intent: String?, title: String?) {
        val key = key(route, endpoint)
        if (intent == CONNECT_INTENT_LIVE) {
            // v56 canlı yayın her zaman önce sorulur; şifre (ilk eşleşmede) ancak kabulden sonra gösterilir
            if (approval.isBlocked(key)) { transports[route]?.reject(endpoint, REJECT_BLOCKED); return }
            _liveRequest.value?.let { old -> split(old.key)?.let { (r, e) -> transports[r]?.reject(e, REJECT_REASON) } }
            _liveRequest.value = ShareLiveRequest(key, name, platform, title.orEmpty())
            liveRequestTimeout?.cancel()
            liveRequestTimeout = scope.launch {
                delay(LiveApprovalPolicy.ANSWER_TIMEOUT_MS)
                if (_liveRequest.value?.key == key) {
                    _liveRequest.value = null
                    transports[route]?.reject(endpoint, REJECT_TIMEOUT)
                }
            }
            return
        }
        // Tuval/resim gönderme bağlantısı kendiliğinden kabul edilir; içerik ancak teklif onaylanınca alınır.
        // İlk eşleşmede şifre kabulden sonra [onPairCode] ile gelir ve ekranda gösterilir
        setState(key, PeerState.CONNECTING, name, platform)
        transports[route]?.accept(endpoint)
    }

    override fun onPairCode(route: ShareRoute, endpoint: String, name: String, platform: String, code: String) {
        _request.value = ShareConnectionRequest(key(route, endpoint), name, platform, code)
    }

    override fun onCodeNeeded(route: ShareRoute, endpoint: String, name: String) {
        _codePrompt.value = ShareCodePrompt(key(route, endpoint), name)
    }

    /** v54 karşı cihazın ekranındaki eşleşme şifresi girildi. */
    fun submitCode(code: String) {
        val p = _codePrompt.value ?: return
        _codePrompt.value = null
        val (route, endpoint) = split(p.key) ?: return
        transports[route]?.submitCode(endpoint, code)
    }

    /** v54 eşleşmeden vazgeçildi (bağlanan cihaz). */
    fun cancelCode() {
        val p = _codePrompt.value ?: return
        _codePrompt.value = null
        val (route, endpoint) = split(p.key) ?: return
        transports[route]?.cancelCode(endpoint)
    }

    override fun onConnected(route: ShareRoute, endpoint: String, name: String, platform: String) {
        val key = key(route, endpoint)
        if (_request.value?.key == key) _request.value = null
        setState(key, PeerState.CONNECTED, name, platform)
        send(key, ShareWire.encode(ShareWire.HELLO, ShareWire.text(ShareHello(name = settings.deviceName.value, platform = "android", app = BuildConfig.VERSION_NAME, codec = com.karapp.drawguidance.data.local.ContentCodec.LATEST_VERSION))))
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
        if (_codePrompt.value?.key == key) _codePrompt.value = null
        if (_liveRequest.value?.key == key) { _liveRequest.value = null; liveRequestTimeout?.cancel() }
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
        _errors.value = localize(message)
        // v54 eşleşme hataları paylaşım sayfası kapalıyken de görünsün
        if (message.startsWith("Eşleşme") || message.startsWith("Karşı cihazın") || message in V56_PEER_ANSWERS) {
            _status.value = null
            _events.tryEmit(ShareEvent.Failed(localize(message)))
        }
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
                // v56 karşı tarafın içerik sürümü (eski uygulamalar bildirmez: V10)
                peerCodec[key] = if (h.codec > 0) h.codec else 10
                setState(key, PeerState.CONNECTED, h.name, h.platform)
            }
            ShareWire.OFFER -> {
                val o = ShareWire.header<ShareOffer>(m)
                // v56 canlı yayın daveti her zaman sorulur (askBeforeLive ayarı kalktı); bağlanırken onaylandıysa
                // yeniden sorulmaz. Tuval/resim için askBeforeReceive ayarı geçerli
                if (o.type == ShareWire.TYPE_LIVE) {
                    if (approval.consumeApproval(key)) reply(key, o, true)
                    else _offer.value = ShareIncomingOffer(key, peerName(key), o)
                } else if (settings.askBeforeReceive.value) _offer.value = ShareIncomingOffer(key, peerName(key), o)
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
                livePersistJob?.cancel(); livePersistJob = null
                installLock.withLock { packages.install(pkg, liveIds, existingId = inn.canvasId) }
                refreshLiveCanvas(inn.canvasId)
                // v56 biten çizgiler artık tuvalde: geçici mürekkep temizlenir
                _liveInk.value = emptyMap()
            }
            ShareWire.LIVE_DIFF -> {
                val inn = _liveIn.value ?: return
                if (inn.peerKey != key || inn.ended) return
                val pkg = SharePackage.decode(m)
                val h = pkg.header
                if (h.seq <= liveSeqIn) return
                val cur = _liveCanvas.value?.takeIf { it.localId == inn.canvasId }
                if (cur == null || h.diffBase != liveSeqIn || h.diffPage !in cur.pages.indices) {
                    // Arada kare kaçtı: gönderen sonraki karede tam paket yollar
                    send(key, ShareWire.encode(ShareWire.LIVE_RESYNC, "{}"))
                    return
                }
                val updated = installLock.withLock { packages.applyLiveDiff(pkg, liveIds, cur) }
                liveSeqIn = h.seq
                if (h.assets.isNotEmpty()) runCatching { assets.ensureLoaded(updated) }
                _liveCanvas.value = updated
                _liveInk.value = emptyMap()
                if (livePersistJob?.isActive != true) livePersistJob = scope.launch {
                    delay(2_000)
                    _liveCanvas.value?.takeIf { it.localId == inn.canvasId }?.let { c -> installLock.withLock { runCatching { packages.persistLive(c) } } }
                }
            }
            ShareWire.LIVE_RESYNC -> if (_liveOut.value?.peerKey == key) liveResync = true
            ShareWire.LIVE_INK -> {
                val inn = _liveIn.value ?: return
                if (inn.peerKey != key || inn.ended) return
                val ink = ShareWire.json.decodeFromString(ShareInk.serializer(), m.json)
                _liveInk.update { map -> map + (ink.stroke to LiveInk.merge(map[ink.stroke], ink)) }
            }
            ShareWire.LIVE_PAUSE, ShareWire.LIVE_RESUME -> {
                val pause = m.type == ShareWire.LIVE_PAUSE
                _liveIn.value?.takeIf { it.peerKey == key && !it.ended }?.let {
                    _liveIn.value = it.copy(paused = pause, pausedAt = if (pause) (it.pausedAt.takeIf { at -> it.paused && at > 0 } ?: System.currentTimeMillis()) else 0L)
                }
                if (pause) _liveInk.value = emptyMap()
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
        if (!accept && o.type == ShareWire.TYPE_LIVE) approval.onRejected(key)
        val type = if (accept) ShareWire.ACCEPT else ShareWire.REJECT
        send(key, ShareWire.encode(type, ShareWire.text(ShareReply(o.id))))
        if (accept) _status.value = ShareTransferStatus(peerName(key), o.title, sending = false)
    }

    /** v56 bağlı cihazların içerik sürümleri (HELLO'dan). */
    private val peerCodec = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private fun codecOf(key: String): Int = peerCodec[key] ?: 10

    private suspend fun deliver(id: String, out: Outgoing) {
        val from = settings.deviceName.value
        when (out.type) {
            ShareWire.TYPE_CANVAS -> {
                val pkg = packages.canvasPackage(out.canvasId ?: return, ShareWire.TYPE_CANVAS, id, from, codecOf(out.key)) ?: return fail("Tuval bulunamadı")
                if (pkg.flattened) _events.tryEmit(ShareEvent.Flattened(peerName(out.key)))
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
                liveLastSent = null
                liveLastSeq = 0
                liveSinceFull = 0
                liveResync = false
                liveOfferId = id
                // İlk paket kayıtlı tuvalin tamamı; gönderilen resimler yayın boyunca yeniden gönderilmez
                val pkg = packages.canvasPackage(canvasId, ShareWire.TYPE_LIVE, id, from, codecOf(out.key)) ?: return fail("Tuval bulunamadı")
                if (pkg.flattened) _events.tryEmit(ShareEvent.Flattened(peerName(out.key)))
                liveSent += pkg.header.assets.map { it.id }
                send(out.key, pkg.encode(ShareWire.LIVE_START))
                // v56 gönderen zaten bu tuvalin düzenleyicisindeyse düzenleyici yeniden açılmaz (yeniden açılış eski
                // düzenleyicinin kapanışıyla yarışıp yayını ara verilmiş bırakıyordu)
                val open = isOpenInEditor(canvasId)
                _liveOut.value = ShareLiveOut(canvasId, out.key, peerName(out.key), paused = false)
                _status.value = null
                startLiveLoop(id)
                if (!open) _events.tryEmit(ShareEvent.OpenBroadcast(canvasId))
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
                    // v56 2C-1 karşı taraf fark paketini biliyorsa (içerik sürümü 11+) yalnız değişen sayfanın farkı gider;
                    // her 20 karede bir, sayfa ya da tuval ayarı değişince ve alan taraf isteyince tam paket
                    val prev = liveLastSent
                    val page = if (prev != null && codecOf(out.peerKey) >= 11 && !liveResync && liveSinceFull < com.karapp.drawguidance.data.model.LiveDiff.FULL_EVERY)
                        com.karapp.drawguidance.data.model.LiveDiff.changedPage(prev, frame) else null
                    when {
                        page == -1 -> Unit
                        page != null && prev != null -> {
                            val d = com.karapp.drawguidance.data.model.LiveDiff.diff(prev.pages[page], frame.pages[page])
                            val pkg = packages.liveDiffPackage(frame, page, d, id, ++liveSeqOut, liveLastSeq, liveSent, settings.deviceName.value)
                            send(out.peerKey, pkg.encode(ShareWire.LIVE_DIFF))
                            liveSinceFull++
                            liveLastSent = frame; liveLastSeq = liveSeqOut
                        }
                        else -> {
                            liveResync = false
                            val pkg = packages.livePackage(frame, id, ++liveSeqOut, liveSent, settings.deviceName.value, codecOf(out.peerKey))
                            send(out.peerKey, pkg.encode(ShareWire.LIVE_UPDATE))
                            liveSinceFull = 0
                            liveLastSent = frame; liveLastSeq = liveSeqOut
                        }
                    }
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
                // v54 gelen resim: türü dosyanın imzasından; yapı ve gömülü kod denetiminden geçmeli
                val mime = com.karapp.drawguidance.data.images.ImageImportValidator.sniffMime(pkg.content)
                if (mime == null || com.karapp.drawguidance.data.images.ImageSecurity.inspect(pkg.content, mime) != com.karapp.drawguidance.data.images.ImageSecurity.Verdict.CLEAN) {
                    send(key, ShareWire.encode(ShareWire.REJECT, ShareWire.text(ShareReply(h.id))))
                    _status.value = null
                    _events.tryEmit(ShareEvent.Failed(context.getString(com.karapp.drawguidance.R.string.v54_image_err_suspicious)))
                    return
                }
                images.saveReceived(pkg.content, h.name, mime, h.imageW, h.imageH, "share:$from")
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

    /** v52 aktarım katmanının (Türkçe kayıt) hata metinleri kullanıcının diline çevrilir. */
    private fun localize(message: String): String = when (message) {
        "Sunucu açılamadı" -> context.getString(com.karapp.drawguidance.R.string.v52_share_err_server)
        "Ağda görünür olunamadı" -> context.getString(com.karapp.drawguidance.R.string.v52_share_err_visible)
        "Ağda arama yapılamadı" -> context.getString(com.karapp.drawguidance.R.string.v52_share_err_discover)
        "Cihaz bulunamadı" -> context.getString(com.karapp.drawguidance.R.string.v52_share_err_no_device)
        "Tuval bulunamadı" -> context.getString(com.karapp.drawguidance.R.string.v52_share_err_no_canvas)
        "Resim okunamadı" -> context.getString(com.karapp.drawguidance.R.string.v52_share_err_image)
        // v54 eşleşme
        "Eşleşme şifresi yanlış" -> context.getString(com.karapp.drawguidance.R.string.v54_share_pair_wrong)
        "Eşleşme yenilenmeli" -> context.getString(com.karapp.drawguidance.R.string.v54_share_pair_renew)
        "Karşı cihazın uygulaması güncel değil" -> context.getString(com.karapp.drawguidance.R.string.v54_share_pair_old)
        // v56 yayın onayı
        "Reddedildi" -> context.getString(com.karapp.drawguidance.R.string.v56_live_rejected)
        "Yanıt yok" -> context.getString(com.karapp.drawguidance.R.string.v56_live_no_answer)
        "Geçici olarak engellendi" -> context.getString(com.karapp.drawguidance.R.string.v56_live_blocked)
        else -> message
    }

    private fun fail(message: String) {
        _status.value = null
        _events.tryEmit(ShareEvent.Failed(localize(message)))
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

/** v56 bağlanan cihaza karşı cihazın yanıtı (yayın onayı). */
private val V56_PEER_ANSWERS = setOf("Reddedildi", "Yanıt yok", "Geçici olarak engellendi")

/** v56 canlı mürekkep parçalarının birleştirilmesi (saf, testli). */
object LiveInk {
    fun merge(old: LiveInkStroke?, ink: ShareInk): LiveInkStroke {
        val have = old?.pts ?: emptyList()
        val haveCount = have.size / 3
        // Önceki parçaların tekrarı atlanır; kayıp parça varsa yeni noktalar yine eklenir (boşluk düz çizgi olur)
        val skip = (haveCount - ink.from).coerceAtLeast(0) * 3
        val add = if (skip >= ink.pts.size) emptyList() else ink.pts.subList(skip, ink.pts.size)
        return LiveInkStroke(ink.stroke, ink.brush, ink.color, ink.size, ink.opacity, have + add, ink.page)
    }
}

private const val INK_INTERVAL_MS = 60L

/**
 * v56 canlı yayının ara ver / sürdür sahipleri (saf, testli). Düzenleyici örnekleri sahip kimliğiyle kaydolur; ara
 * kimliği "<düzenleyici>" ya da "<düzenleyici>:<neden>". Yayın, tuval hiçbir düzenleyicide açık değilse ya da açık bir
 * düzenleyici ara verdiyse durur. Ayrılan düzenleyicinin araları onunla silinir.
 */
class LivePauseOwners {
    private val editors = HashMap<String, MutableSet<String>>()
    private val pauses = HashMap<String, MutableSet<String>>()

    @Synchronized fun attach(canvasId: String, owner: String) { editors.getOrPut(canvasId) { HashSet() } += owner }

    @Synchronized fun detach(canvasId: String, owner: String) {
        editors[canvasId]?.let { it -= owner; if (it.isEmpty()) editors.remove(canvasId) }
        pauses[canvasId]?.let { set -> set.removeAll { it == owner || it.startsWith("$owner:") }; if (set.isEmpty()) pauses.remove(canvasId) }
    }

    @Synchronized fun pause(canvasId: String, owner: String) { pauses.getOrPut(canvasId) { HashSet() } += owner }

    @Synchronized fun resume(canvasId: String, owner: String) {
        pauses[canvasId]?.let { it -= owner; if (it.isEmpty()) pauses.remove(canvasId) }
    }

    @Synchronized fun isOpen(canvasId: String): Boolean = !editors[canvasId].isNullOrEmpty()

    @Synchronized fun shouldPause(canvasId: String): Boolean {
        val open = editors[canvasId].orEmpty()
        return open.isEmpty() || pauses[canvasId].orEmpty().any { it.substringBefore(':') in open }
    }
}
