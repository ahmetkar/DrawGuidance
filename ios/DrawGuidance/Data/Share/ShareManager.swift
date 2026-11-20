import Foundation
import UIKit

enum PeerState { case found, connecting, connected }

/// Paylaşım sayfasındaki bir cihaz (`id` = karşı uygulamanın kalıcı kimliği).
struct SharePeer: Identifiable, Equatable {
    let id: String
    var name: String
    var platform: String
    var state: PeerState
}

struct ShareIncomingOffer: Identifiable, Equatable {
    let peerId: String
    let peerName: String
    let offer: ShareOffer
    var id: String { offer.id }
}

struct ShareTransferStatus: Equatable {
    var peerName: String
    var title: String
    var sending: Bool
    var progress: Double?
}

struct ShareLiveOut: Equatable { let canvasId: String; let peerId: String; let peerName: String; var paused = false; var startedAt = Date() }
struct ShareLiveIn: Equatable {
    let canvasId: String; let peerId: String; let peerName: String; var ended = false; var paused = false
    /// v56 ara verildiği an (5 sn sürerse "Yayıncı başka bir ekranda" ipucu).
    var pausedAt: Date?
}
/// v56 canlı yayın isteği (kabul eden cihazda, şifreden önce sorulur).
struct ShareLiveRequest: Identifiable, Equatable { let peerId: String; let name: String; let platform: String; let title: String; var id: String { peerId } }

/// Alınan içerik bildirimi ("Tuvali aç" düğmeli pencere).
struct ShareReceived: Identifiable, Equatable {
    let id = UUID()
    let type: String
    let title: String
    let from: String
    let canvasId: String?
}

/// v54 kabul eden cihazda gösterilen eşleşme şifresi.
struct SharePairShow: Identifiable, Equatable { let peerId: String; let name: String; let code: String; var id: String { peerId } }
/// v54 bağlanan cihazda eşleşme şifresi sorusu.
struct ShareCodePrompt: Identifiable, Equatable { let peerId: String; let name: String; var id: String { peerId } }

/// Düzenleyicide açılacak tuval (yayın başlatan taraf ya da alınan tuval).
struct ShareOpenCanvas: Identifiable, Equatable { let id: String }

/// v51 cihazlar arası paylaşımın merkezi (Android `ShareManager`): aynı Wi-Fi ağındaki cihazları kendiliğinden bulur,
/// bir cihaza tuval/resim gönderilince ya da yayın başlatılınca bağlantıyı kendiliğinden kurar, teklif onaylarını,
/// gönderip almayı ve canlı tuval yayınını yürütür.
@MainActor
final class ShareManager: ObservableObject {
    static let shared = ShareManager()

    let settings: ShareSettings
    let registry: SharedCanvasRegistry
    private let transport = LanTransport()
    private var packages: SharePackages { SharePackages(store: EditorDependencies.shared.canvases) }
    private var store: CanvasStore { EditorDependencies.shared.canvases }

    @Published private(set) var active = false
    @Published private(set) var peers: [SharePeer] = []
    @Published var offer: ShareIncomingOffer?
    @Published private(set) var status: ShareTransferStatus?
    @Published private(set) var liveOut: ShareLiveOut?
    @Published private(set) var liveIn: ShareLiveIn?
    /// Yayını alan tarafta tuvalin son hali.
    @Published private(set) var liveCanvas: CanvasUi?
    @Published var showLive = false
    @Published var received: ShareReceived?
    @Published var openCanvas: ShareOpenCanvas?
    @Published var toast: String?
    @Published private(set) var error: String?
    /// v54 eşleşme şifresi (kabul eden: gösterilir; bağlanan: girilir).
    @Published var pairShow: SharePairShow?
    @Published var codePrompt: ShareCodePrompt?
    /// v56 yanıt bekleyen canlı yayın isteği.
    @Published private(set) var liveRequest: ShareLiveRequest?
    /// v56 alan tarafta çizilmekte olan geçici çizgiler (çizgi bitip kare gelince silinir).
    @Published private(set) var liveInk: [String: LiveInkStroke] = [:]
    private let approval = LiveApprovalPolicy()
    private var liveRequestTimeout: Task<Void, Never>?
    private var inkStroke: String?
    private var inkSent = 0
    private var inkLastAt: Date = .distantPast

    private struct Outgoing { let peerId: String; let type: String; let title: String; let canvasId: String?; let image: SavedImage? }
    private var outgoing: [String: Outgoing] = [:]
    private var afterConnect: [String: [() -> Void]] = [:]
    private var liveIds: [String: String] = [:]
    private var liveSeqIn: Int64 = -1
    private var liveOfferId: String?
    private var liveSent = Set<String>()
    private var liveSeqOut: Int64 = 0
    /// v56 fark paketi: son gönderilen kare, sırası, son tam paketten beri kare sayısı, tam paket isteği.
    private var liveLastSent: CanvasUi?
    private var liveLastSeq: Int64 = 0
    private var liveSinceFull = 0
    private var liveResync = false
    /// v56 alan tarafta farkla güncellenen tuvalin toplu kaydı.
    private var livePersistTask: Task<Void, Never>?
    private var liveFrame: CanvasUi?
    private var liveTask: Task<Void, Never>?
    private var foreground = true
    private var pages = 0
    private var attached = false

    private init() {
        settings = ShareSettings.shared
        registry = SharedCanvasRegistry.shared
        transport.delegate = self
    }

    // MARK: Görünürlük
    //
    // Keşif kendiliğindendir: uygulama ön plandayken (ve "görünür ol" açıkken) ya da paylaşım sayfası açıkken cihaz
    // aynı Wi-Fi'deki diğer cihazlarda görünür ve onları bulur. Bağlantı ayrıca istenmez.

    /// Uygulama açılışında bir kez.
    func attach() {
        guard !attached else { return }
        attached = true
        foreground = UIApplication.shared.applicationState != .background
        let nc = NotificationCenter.default
        nc.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.foreground = true; self?.refresh() }
        }
        nc.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.foreground = false; self?.refresh() }
        }
        refresh()
    }

    func pageOpened() { pages += 1; refresh() }
    func pageClosed() { pages = max(0, pages - 1); refresh() }

    func setDiscoverable(_ on: Bool) { settings.setDiscoverable(on); refresh() }

    func rename(_ name: String) {
        settings.setDeviceName(name)
        guard active else { return }
        transport.stop()
        active = false
        refresh()
    }

    private func refresh() {
        let want = pages > 0 || (foreground && settings.discoverable)
        guard want != active else { return }
        if want {
            transport.start(name: settings.deviceName, instanceId: settings.instanceId)
        } else {
            transport.stop()
            peers.removeAll { $0.state == .found }
        }
        active = want
    }

    // MARK: Bağlantı

    private func whenConnected(_ id: String, intent: String? = nil, title: String? = nil, _ action: @escaping () -> Void) {
        guard let peer = peers.first(where: { $0.id == id }) else { fail(L("v51_share_failed", "?")); return }
        if peer.state == .connected { action(); return }
        afterConnect[id, default: []].append(action)
        if peer.state != .connecting {
            setState(id, .connecting)
            transport.connect(id, intent: intent, title: title)
        }
    }

    func connect(_ peer: SharePeer) { whenConnected(peer.id) {} }

    func disconnect(_ peer: SharePeer) {
        send(peer.id, ShareWire.encode(ShareWire.BYE, Data("{}".utf8)))
        transport.disconnect(peer.id)
    }

    // MARK: Gönderme

    func sendCanvas(_ peerId: String, _ canvas: CanvasUi) {
        offerOut(peerId, type: ShareWire.TYPE_CANVAS, title: canvas.name, canvasId: canvas.localId, image: nil,
                 bytes: packages.canvasSize(canvas.localId), kind: canvas.kind.rawValue)
    }

    func sendImage(_ peerId: String, _ image: SavedImage) {
        offerOut(peerId, type: ShareWire.TYPE_IMAGE, title: image.title, canvasId: nil, image: image, bytes: image.sizeBytes, kind: nil)
    }

    /// Yayın teklifi: karşı taraf kabul edince tuval ona açılır ve bu tuvaldeki değişiklikler ona akar.
    /// v52 canlı yayına açık tuval türleri.
    nonisolated static func liveAllowed(_ kind: CanvasKind?) -> Bool { kind != nil && kind != .KIDS }

    func startLive(_ peerId: String, _ canvas: CanvasUi) {
        // v52 yayın yalnız çizim modları, not alma ve çizgi romanda (çocuk modunda yok)
        guard Self.liveAllowed(canvas.kind) else { return }
        stopLive()
        offerOut(peerId, type: ShareWire.TYPE_LIVE, title: canvas.name, canvasId: canvas.localId, image: nil,
                 bytes: packages.canvasSize(canvas.localId), kind: canvas.kind.rawValue)
    }

    /// v56 canlı yayın isteği yanıtlandı: kabulde bağlantı kabul edilir (ilk eşleşmede şifre şimdi gösterilir).
    func answerLiveRequest(_ accept: Bool) {
        guard let r = liveRequest else { return }
        liveRequest = nil
        liveRequestTimeout?.cancel(); liveRequestTimeout = nil
        if accept {
            approval.onApproved(r.peerId)
            setState(r.peerId, .connecting, name: r.name, platform: r.platform)
            transport.accept(r.peerId)
        } else {
            approval.onRejected(r.peerId)
            transport.reject(r.peerId, reason: LanTransport.REJECT_REASON)
        }
    }

    private func offerOut(_ peerId: String, type: String, title: String, canvasId: String?, image: SavedImage?, bytes: Int64, kind: String?) {
        let id = UUID().uuidString.lowercased()
        outgoing[id] = Outgoing(peerId: peerId, type: type, title: title, canvasId: canvasId, image: image)
        let o = ShareOffer(id: id, type: type, title: title, bytes: bytes, canvasKind: kind)
        status = ShareTransferStatus(peerName: peerName(peerId), title: title, sending: true)
        // v56 yayın bağlantısı amacını bildirir: karşı cihaz önce onay sorar, şifre ondan sonra gösterilir
        whenConnected(peerId, intent: type == ShareWire.TYPE_LIVE ? LanTransport.CONNECT_INTENT_LIVE : nil, title: title) { [weak self] in
            self?.send(peerId, ShareWire.encode(ShareWire.OFFER, o))
        }
    }

    func stopLive() {
        guard let out = liveOut else { return }
        liveOut = nil
        liveTask?.cancel(); liveTask = nil
        send(out.peerId, ShareWire.encode(ShareWire.LIVE_STOP, ShareReply(id: liveOfferId ?? "", canvasId: nil)))
    }

    /// Yayını alan taraf yayından ayrılır (tuval kayıtlı kalır).
    func leaveLive() {
        guard let inn = liveIn else { return }
        // v56 farkla güncellenip henüz kaydedilmemiş çizimler kaybolmasın
        if livePersistTask != nil {
            livePersistTask?.cancel(); livePersistTask = nil
            if let c = liveCanvas, c.localId == inn.canvasId { packages.persistLive(c) }
        }
        liveIn = nil
        liveCanvas = nil
        liveInk = [:]
        showLive = false
        if !inn.ended { send(inn.peerId, ShareWire.encode(ShareWire.LIVE_STOP, Data("{}".utf8))) }
    }

    /// Düzenleyici yayın yapılan tuvalin her değişikliğini bildirir; en son hali ayarlanan sıklıkta gönderilir.
    /// v56 düzenleyici çizilmekte olan çizginin noktalarını bildirir; yeni noktalar en çok ~60 ms'de bir küçük pakette
    /// gider (Android `publishInk`). Çizgi bitince normal kare gider; alan taraf geçici çizgiyi o zaman siler.
    func publishInk(_ canvasId: String, stroke: String, brush: String, color: String, size: Float, opacity: Float, points: [StrokePoint], page: Int = -1) {
        guard let out = liveOut, out.canvasId == canvasId, !out.paused else { return }
        let now = Date()
        if stroke != inkStroke { inkStroke = stroke; inkSent = 0; inkLastAt = .distantPast }
        guard now.timeIntervalSince(inkLastAt) >= 0.06, points.count > inkSent else { return }
        let from = inkSent
        var flat: [Float] = []
        flat.reserveCapacity((points.count - from) * 3)
        for p in points[from...] { flat.append(p.x); flat.append(p.y); flat.append(p.pressure) }
        inkSent = points.count
        inkLastAt = now
        send(out.peerId, ShareWire.encode(ShareWire.LIVE_INK, ShareInk(stroke: stroke, brush: brush, color: color, size: size,
                                                                        opacity: opacity, from: from, pts: flat, page: page)))
    }

    func publishLive(_ canvas: CanvasUi) {
        guard liveOut?.canvasId == canvas.localId, liveOut?.paused != true else { return }
        liveFrame = canvas
    }

    // MARK: - v56 ara ver / sürdür sahipleri (Android `ShareManager.attachEditor`)
    //
    // Eskiden pauseLive/resumeLive sayaçsızdı; kapanan eski düzenleyicinin geç gelen pauseLive'ı yeni düzenleyicinin
    // yayınını kalıcı olarak durduruyordu. Artık her düzenleyici bir sahip kimliğiyle kaydolur; yayın yalnız tuval hiçbir
    // düzenleyicide açık değilse ya da açık bir düzenleyici kendi adına ara verdiyse durur.

    private var editorOwners: [String: Set<String>] = [:]
    private var pauseOwners: [String: Set<String>] = [:]

    func attachEditor(_ canvasId: String, owner: String) {
        editorOwners[canvasId, default: []].insert(owner)
        recomputePause(canvasId)
    }

    func detachEditor(_ canvasId: String, owner: String) {
        editorOwners[canvasId]?.remove(owner)
        if editorOwners[canvasId]?.isEmpty == true { editorOwners[canvasId] = nil }
        pauseOwners[canvasId] = pauseOwners[canvasId]?.filter { $0 != owner && !$0.hasPrefix(owner + ":") }
        if pauseOwners[canvasId]?.isEmpty == true { pauseOwners[canvasId] = nil }
        recomputePause(canvasId)
    }

    func isOpenInEditor(_ canvasId: String) -> Bool { !(editorOwners[canvasId]?.isEmpty ?? true) }

    /// v52 yayına ara ver (notta şekil/resim başka tuvalde düzenleniyor, foto düzenleyici, arka plan).
    func pauseLive(_ canvasId: String, owner: String) {
        pauseOwners[canvasId, default: []].insert(owner)
        recomputePause(canvasId)
    }

    /// v52 [owner]'ın verdiği ara kalkar; başka ara veren yoksa yayın sürer.
    func resumeLive(_ canvasId: String, owner: String) {
        pauseOwners[canvasId]?.remove(owner)
        if pauseOwners[canvasId]?.isEmpty == true { pauseOwners[canvasId] = nil }
        recomputePause(canvasId)
    }

    /// Sahip kimliği "<düzenleyici>" ya da "<düzenleyici>:<neden>"; ara yalnız açık düzenleyicilerin araları sayılır.
    func shouldPause(_ canvasId: String) -> Bool {
        let editors = editorOwners[canvasId] ?? []
        if editors.isEmpty { return true }
        return (pauseOwners[canvasId] ?? []).contains { p in editors.contains(p.split(separator: ":").first.map(String.init) ?? p) }
    }

    private func recomputePause(_ canvasId: String) {
        guard var out = liveOut, out.canvasId == canvasId else { return }
        let pause = shouldPause(canvasId)
        guard pause != out.paused else { return }
        out.paused = pause
        liveOut = out
        send(out.peerId, ShareWire.encode(pause ? ShareWire.LIVE_PAUSE : ShareWire.LIVE_RESUME, Data("{}".utf8)))
    }

    func isBroadcasting(_ canvasId: String) -> Bool { liveOut?.canvasId == canvasId }

    func answerOffer(_ accept: Bool) {
        guard let o = offer else { return }
        offer = nil
        reply(o.peerId, o.offer, accept)
    }

    func clearError() { error = nil }

    // MARK: v54 eşleşme şifresi

    /// Karşı cihazın ekranındaki şifre girildi.
    func submitCode(_ code: String) {
        guard let p = codePrompt else { return }
        codePrompt = nil
        transport.submitCode(p.peerId, code)
    }

    /// Bağlanan cihazda eşleşmeden vazgeçildi.
    func cancelCode() {
        guard let p = codePrompt else { return }
        codePrompt = nil
        transport.cancelCode(p.peerId)
    }

    /// Kabul eden cihazda şifre gösterilirken vazgeçildi.
    func cancelPair() {
        guard let p = pairShow else { return }
        pairShow = nil
        transport.reject(p.peerId)
    }

    fileprivate func pairCode(_ id: String, _ name: String, _ code: String?) {
        setState(id, .connecting, name: name)
        if let code { pairShow = SharePairShow(peerId: id, name: name, code: code) }
    }

    fileprivate func codeNeeded(_ id: String, _ name: String) { codePrompt = ShareCodePrompt(peerId: id, name: name) }

    /// v56 gelen bağlantı isteği: canlı yayın her zaman önce sorulur (şifre kabulden sonra); tuval/resim bağlantısı
    /// kendiliğinden kabul edilir (içerik ayrıca teklif onayıyla alınır).
    fileprivate func request(_ id: String, _ name: String, _ platform: String, intent: String?, title: String?) {
        guard intent == LanTransport.CONNECT_INTENT_LIVE else {
            setState(id, .connecting, name: name, platform: platform)
            transport.accept(id)
            return
        }
        if approval.isBlocked(id) { transport.reject(id, reason: LanTransport.REJECT_BLOCKED); return }
        if let old = liveRequest, old.peerId != id { transport.reject(old.peerId, reason: LanTransport.REJECT_REASON) }
        liveRequest = ShareLiveRequest(peerId: id, name: name, platform: platform, title: title ?? "")
        liveRequestTimeout?.cancel()
        liveRequestTimeout = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(LiveApprovalPolicy.ANSWER_TIMEOUT_MS) * 1_000_000)
            guard let self, !Task.isCancelled, self.liveRequest?.peerId == id else { return }
            self.liveRequest = nil
            self.transport.reject(id, reason: LanTransport.REJECT_TIMEOUT)
        }
    }

    // MARK: Gelen mesajlar

    fileprivate func found(_ id: String, _ name: String, _ platform: String) {
        if let i = peers.firstIndex(where: { $0.id == id }) {
            peers[i].name = name; peers[i].platform = platform
        } else {
            peers.append(SharePeer(id: id, name: name, platform: platform, state: .found))
        }
    }

    fileprivate func lost(_ id: String) { peers.removeAll { $0.id == id && $0.state == .found } }

    fileprivate func connected(_ id: String, _ name: String, _ platform: String) {
        if pairShow?.peerId == id { pairShow = nil }
        setState(id, .connected, name: name, platform: platform)
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        send(id, ShareWire.encode(ShareWire.HELLO, ShareHello(name: settings.deviceName, platform: "ios", app: version, codec: ContentCodec.LATEST_VERSION)))
        afterConnect.removeValue(forKey: id)?.forEach { $0() }
    }

    fileprivate func disconnected(_ id: String) {
        let peer = peers.first { $0.id == id }
        let wasConnected = peer?.state == .connected
        if pairShow?.peerId == id { pairShow = nil }
        if codePrompt?.peerId == id { codePrompt = nil }
        if liveRequest?.peerId == id { liveRequest = nil; liveRequestTimeout?.cancel(); liveRequestTimeout = nil }
        if active {
            if let i = peers.firstIndex(where: { $0.id == id }) { peers[i].state = .found }
        } else {
            peers.removeAll { $0.id == id }
        }
        if afterConnect.removeValue(forKey: id) != nil && !wasConnected { fail(L("v51_share_failed", peer?.name ?? "")) }
        if offer?.peerId == id { offer = nil }
        if liveOut?.peerId == id { liveOut = nil; liveTask?.cancel(); liveTask = nil }
        if liveIn?.peerId == id { liveIn?.ended = true }
        outgoing = outgoing.filter { $0.value.peerId != id }
        status = nil
        if wasConnected { toast = L("v51_share_disconnected", peer?.name ?? "") }
    }

    fileprivate func progress(_ id: String, _ fraction: Double) {
        if status == nil { status = ShareTransferStatus(peerName: peerName(id), title: "", sending: false) }
        status?.progress = fraction
    }

    fileprivate func failed(_ message: String) {
        // v54 eşleşme hataları kullanıcının dilinde; sayfa kapalıyken de görünsün
        let text: String
        switch message {
        case "Eşleşme şifresi yanlış": text = L("v54_share_pair_wrong")
        case "Eşleşme yenilenmeli": text = L("v54_share_pair_renew")
        case "Karşı cihazın uygulaması güncel değil": text = L("v54_share_pair_old")
        case "Reddedildi": text = L("v56_live_rejected"); status = nil
        case "Yanıt yok": text = L("v56_live_no_answer"); status = nil
        case "Geçici olarak engellendi": text = L("v56_live_blocked"); status = nil
        default: error = message; return
        }
        error = text
        toast = text
    }

    fileprivate func message(_ id: String, _ bytes: Data) {
        guard let m = try? ShareWire.decode(bytes) else { return }
        do { try handle(id, m) } catch {
            status = nil
            fail(L("v51_share_failed", error.localizedDescription))
        }
    }

    private func handle(_ id: String, _ m: ShareWire.Message) throws {
        switch m.type {
        case ShareWire.HELLO:
            let h = try m.header(ShareHello.self)
            // v56 karşı tarafın içerik sürümü (eski uygulamalar bildirmez: V10)
            peerCodec[id] = h.codec > 0 ? h.codec : 10
            setState(id, .connected, name: h.name, platform: h.platform)
        case ShareWire.OFFER:
            let o = try m.header(ShareOffer.self)
            // v56 canlı yayın daveti her zaman sorulur (askBeforeLive ayarı kalktı); bağlanırken onaylandıysa yeniden
            // sorulmaz. Tuval/resim için askBeforeReceive ayarı geçerli
            if o.type == ShareWire.TYPE_LIVE {
                if approval.consumeApproval(id) { reply(id, o, true) }
                else { offer = ShareIncomingOffer(peerId: id, peerName: peerName(id), offer: o) }
            } else if settings.askBeforeReceive { offer = ShareIncomingOffer(peerId: id, peerName: peerName(id), offer: o) }
            else { reply(id, o, true) }
        case ShareWire.ACCEPT:
            let r = try m.header(ShareReply.self)
            guard let out = outgoing.removeValue(forKey: r.id) else { return }
            deliver(r.id, out)
        case ShareWire.REJECT:
            let r = try m.header(ShareReply.self)
            guard let out = outgoing.removeValue(forKey: r.id) else { return }
            status = nil
            toast = L("v51_share_rejected", peerName(id))
        case ShareWire.PACKAGE:
            try receivePackage(id, SharePackage.decode(m))
        case ShareWire.RECEIVED:
            let title = status?.title ?? ""
            status = nil
            toast = L("v51_share_sent", title, peerName(id))
        case ShareWire.LIVE_START:
            let pkg = try SharePackage.decode(m)
            var ids: [String: String] = [:]
            let canvasId = try packages.install(pkg, ids: &ids)
            liveIds = ids
            liveSeqIn = pkg.header.seq
            registry.mark(canvasId, SharedCanvasInfo(from: pkg.header.from.isEmpty ? peerName(id) : pkg.header.from,
                                                     platform: platformOf(id), at: CanvasStore.nowMs(), live: true))
            liveIn = ShareLiveIn(canvasId: canvasId, peerId: id, peerName: peerName(id))
            status = nil
            refreshLiveCanvas(canvasId)
            // v52 kapalıysa yayın, Canlı yayın sayfasındaki karttan açılır
            if settings.liveAutoOpen { showLive = true }
        case ShareWire.LIVE_UPDATE:
            guard let inn = liveIn, inn.peerId == id, !inn.ended else { return }
            let pkg = try SharePackage.decode(m)
            guard pkg.header.seq > liveSeqIn else { return }
            liveSeqIn = pkg.header.seq
            livePersistTask?.cancel(); livePersistTask = nil
            _ = try packages.install(pkg, ids: &liveIds, existingId: inn.canvasId)
            refreshLiveCanvas(inn.canvasId)
            // v56 biten çizgiler artık tuvalde: geçici mürekkep temizlenir
            liveInk = [:]
        case ShareWire.LIVE_DIFF:
            guard let inn = liveIn, inn.peerId == id, !inn.ended else { return }
            let pkg = try SharePackage.decode(m)
            let h = pkg.header
            guard h.seq > liveSeqIn else { return }
            guard let cur = liveCanvas, cur.localId == inn.canvasId, h.diffBase == liveSeqIn,
                  let page = h.diffPage, cur.pages.indices.contains(page) else {
                // Arada kare kaçtı: gönderen sonraki karede tam paket yollar
                send(id, ShareWire.encode(ShareWire.LIVE_RESYNC, Data("{}".utf8)))
                return
            }
            let updated = try packages.applyLiveDiff(pkg, ids: &liveIds, current: cur)
            liveSeqIn = h.seq
            if !h.assets.isEmpty { CanvasAssetStore.shared.ensureLoaded(updated) }
            liveCanvas = updated
            liveInk = [:]
            if livePersistTask == nil {
                let canvasId = inn.canvasId
                livePersistTask = Task { [weak self] in
                    try? await Task.sleep(nanoseconds: 2_000_000_000)
                    guard let self, !Task.isCancelled else { return }
                    self.livePersistTask = nil
                    if let c = self.liveCanvas, c.localId == canvasId { self.packages.persistLive(c) }
                }
            }
        case ShareWire.LIVE_RESYNC:
            if liveOut?.peerId == id { liveResync = true }
        case ShareWire.LIVE_INK:
            guard let inn = liveIn, inn.peerId == id, !inn.ended else { return }
            let ink = try m.header(ShareInk.self)
            liveInk[ink.stroke] = LiveInk.merge(liveInk[ink.stroke], ink)
        case ShareWire.LIVE_PAUSE, ShareWire.LIVE_RESUME:
            let pause = m.type == ShareWire.LIVE_PAUSE
            if liveIn?.peerId == id, liveIn?.ended == false {
                if pause { if liveIn?.paused != true || liveIn?.pausedAt == nil { liveIn?.pausedAt = Date() } } else { liveIn?.pausedAt = nil }
                liveIn?.paused = pause
            }
            if pause { liveInk = [:] }
        case ShareWire.LIVE_STOP:
            if liveIn?.peerId == id { liveIn?.ended = true }
            if liveOut?.peerId == id { liveOut = nil; liveTask?.cancel(); liveTask = nil }
        case ShareWire.BYE:
            transport.disconnect(id)
        default:
            break
        }
    }

    private func reply(_ id: String, _ o: ShareOffer, _ accept: Bool) {
        if accept && o.type == ShareWire.TYPE_LIVE { leaveLive() }
        if !accept && o.type == ShareWire.TYPE_LIVE { approval.onRejected(id) }
        send(id, ShareWire.encode(accept ? ShareWire.ACCEPT : ShareWire.REJECT, ShareReply(id: o.id, canvasId: nil)))
        if accept { status = ShareTransferStatus(peerName: peerName(id), title: o.title, sending: false) }
    }

    /// v56 bağlı cihazların içerik sürümleri (HELLO'dan).
    private var peerCodec: [String: Int] = [:]
    private func codecOf(_ id: String) -> Int { peerCodec[id] ?? 10 }

    private func noteFlattened(_ pkg: SharePackage, _ peerId: String) {
        if pkg.flattened { toast = L("v56_share_flattened", peerName(peerId)) }
    }

    private func deliver(_ id: String, _ out: Outgoing) {
        let from = settings.deviceName
        switch out.type {
        case ShareWire.TYPE_CANVAS:
            guard let cid = out.canvasId, let pkg = packages.canvasPackage(cid, type: ShareWire.TYPE_CANVAS, id: id, from: from, peerCodec: codecOf(out.peerId)) else {
                fail(L("v51_share_failed", out.title)); return
            }
            noteFlattened(pkg, out.peerId)
            send(out.peerId, pkg.encode(ShareWire.PACKAGE))
        case ShareWire.TYPE_IMAGE:
            guard let img = out.image, let repo = EditorDependencies.shared.images,
                  let bytes = try? Data(contentsOf: repo.url(img)) else { fail(L("v51_share_failed", out.title)); return }
            var h = SharePackageHeader(id: id, type: ShareWire.TYPE_IMAGE, name: img.title)
            h.contentLen = bytes.count
            h.mime = img.mime
            h.imageW = img.width; h.imageH = img.height
            h.from = from
            send(out.peerId, SharePackage(header: h, content: bytes, assets: []).encode(ShareWire.PACKAGE))
        case ShareWire.TYPE_LIVE:
            guard let cid = out.canvasId, let pkg = packages.canvasPackage(cid, type: ShareWire.TYPE_LIVE, id: id, from: from, peerCodec: codecOf(out.peerId)) else {
                fail(L("v51_share_failed", out.title)); return
            }
            noteFlattened(pkg, out.peerId)
            liveSent = Set(pkg.header.assets.map(\.id))
            liveSeqOut = 0
            liveLastSent = nil; liveLastSeq = 0; liveSinceFull = 0; liveResync = false
            liveOfferId = id
            send(out.peerId, pkg.encode(ShareWire.LIVE_START))
            liveOut = ShareLiveOut(canvasId: cid, peerId: out.peerId, peerName: peerName(out.peerId))
            status = nil
            startLiveLoop(id)
            // v56 gönderen zaten bu tuvalin düzenleyicisindeyse düzenleyici yeniden açılmaz
            if !isOpenInEditor(cid) { openCanvas = ShareOpenCanvas(id: cid) }
        default:
            break
        }
    }

    private func startLiveLoop(_ id: String) {
        liveTask?.cancel()
        liveFrame = nil
        let packages = self.packages
        liveTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self, let out = self.liveOut else { return }
                if let frame = self.liveFrame, frame.localId == out.canvasId {
                    self.liveFrame = nil
                    let before = self.liveSent
                    let from = self.settings.deviceName
                    let codec = self.codecOf(out.peerId)
                    // v56 2C-1 karşı taraf fark paketini biliyorsa (içerik sürümü 11+) yalnız değişen sayfanın farkı gider;
                    // her 20 karede bir, sayfa ya da tuval ayarı değişince ve alan taraf isteyince tam paket
                    let prev = self.liveLastSent
                    var page: Int?
                    if let prev, codec >= 11, !self.liveResync, self.liveSinceFull < LiveDiff.FULL_EVERY { page = LiveDiff.changedPage(prev, frame) }
                    if page == -1 {
                        // Değişiklik yok
                    } else {
                        self.liveSeqOut += 1
                        let seq = self.liveSeqOut
                        let base = self.liveLastSeq
                        let diffPage = page
                        // Paket arka planda hazırlanır (içerik kodlama ve resim okuma)
                        let (pkg, newSent) = await Task.detached(priority: .userInitiated) { () -> (SharePackage, Set<String>) in
                            var sent = before
                            if let p = diffPage, let prev {
                                let d = LiveDiff.diff(prev.pages[p], frame.pages[p])
                                return (packages.liveDiffPackage(frame, page: p, diff: d, id: id, seq: seq, base: base, sent: &sent, from: from), sent)
                            }
                            return (packages.livePackage(frame, id: id, seq: seq, sent: &sent, from: from, peerCodec: codec), sent)
                        }.value
                        guard !Task.isCancelled, self.liveOut == out else { return }
                        self.liveSent = newSent
                        if diffPage != nil {
                            self.send(out.peerId, pkg.encode(ShareWire.LIVE_DIFF))
                            self.liveSinceFull += 1
                        } else {
                            self.send(out.peerId, pkg.encode(ShareWire.LIVE_UPDATE))
                            self.liveSinceFull = 0
                            self.liveResync = false
                        }
                        self.liveLastSent = frame
                        self.liveLastSeq = seq
                    }
                }
                let interval = self.settings.liveInterval
                try? await Task.sleep(nanoseconds: UInt64(interval * 1_000_000_000))
            }
        }
    }

    private func receivePackage(_ id: String, _ pkg: SharePackage) throws {
        let h = pkg.header
        let from = h.from.isEmpty ? peerName(id) : h.from
        if h.type == ShareWire.TYPE_IMAGE {
            guard let repo = EditorDependencies.shared.images else { throw ShareError.failed("images") }
            // v54 gelen resim: türü dosyanın imzasından; yapı ve gömülü kod denetiminden geçmeli
            guard let mime = ImageImportValidator.sniffMime(pkg.content), ImageSecurity.inspect(pkg.content, mime: mime) == .CLEAN else {
                send(id, ShareWire.encode(ShareWire.REJECT, ShareReply(id: h.id, canvasId: nil)))
                status = nil
                fail(L("v54_image_err_suspicious"))
                return
            }
            _ = try repo.saveReceived(pkg.content, title: h.name, mime: mime, width: h.imageW, height: h.imageH,
                                      source: "share:" + from)
            send(id, ShareWire.encode(ShareWire.RECEIVED, ShareReply(id: h.id, canvasId: nil)))
            status = nil
            received = ShareReceived(type: ShareWire.TYPE_IMAGE, title: h.name, from: from, canvasId: nil)
        } else {
            var ids: [String: String] = [:]
            let canvasId = try packages.install(pkg, ids: &ids)
            registry.mark(canvasId, SharedCanvasInfo(from: from, platform: platformOf(id), at: CanvasStore.nowMs()))
            send(id, ShareWire.encode(ShareWire.RECEIVED, ShareReply(id: h.id, canvasId: canvasId)))
            status = nil
            received = ShareReceived(type: ShareWire.TYPE_CANVAS, title: h.name, from: from, canvasId: canvasId)
        }
    }

    #if DEBUG
    /// Mağaza görüntüsü: ağ olmadan, verilen tuvali canlı yayın izleme ekranındaymış gibi gösterir.
    func debugShowLive(_ c: CanvasUi, peer: String) {
        liveIn = ShareLiveIn(canvasId: c.localId, peerId: "debug", peerName: peer)
        refreshLiveCanvas(c.localId)
        if liveCanvas == nil { CanvasAssetStore.shared.ensureLoaded(c); liveCanvas = c }
    }
    #endif

    private func refreshLiveCanvas(_ canvasId: String) {
        guard let c = store.getCanvas(canvasId) else { return }
        CanvasAssetStore.shared.ensureLoaded(c)
        liveCanvas = c
    }

    // MARK: Yardımcılar

    private func fail(_ message: String) {
        status = nil
        toast = message
    }

    private func send(_ id: String, _ bytes: Data) { transport.send(id, bytes) }

    private func setState(_ id: String, _ state: PeerState, name: String? = nil, platform: String? = nil) {
        if let i = peers.firstIndex(where: { $0.id == id }) {
            peers[i].state = state
            if let name { peers[i].name = name }
            if let platform { peers[i].platform = platform }
        } else {
            peers.append(SharePeer(id: id, name: name ?? "?", platform: platform ?? "", state: state))
        }
    }

    func peerName(_ id: String) -> String { peers.first { $0.id == id }?.name ?? "?" }
    private func platformOf(_ id: String) -> String { peers.first { $0.id == id }?.platform ?? "" }
}

extension ShareManager: LanTransportDelegate {
    /// Taşıma kuyruğundan ana iş parçacığına sırayı koruyarak geçer (mesaj sırası önemli).
    nonisolated private func main(_ run: @escaping @MainActor (ShareManager) -> Void) {
        DispatchQueue.main.async { MainActor.assumeIsolated { run(self) } }
    }
    nonisolated func lanFound(_ endpoint: String, name: String, platform: String) { main { $0.found(endpoint, name, platform) } }
    nonisolated func lanLost(_ endpoint: String) { main { $0.lost(endpoint) } }
    nonisolated func lanConnected(_ endpoint: String, name: String, platform: String) { main { $0.connected(endpoint, name, platform) } }
    nonisolated func lanDisconnected(_ endpoint: String) { main { $0.disconnected(endpoint) } }
    nonisolated func lanMessage(_ endpoint: String, _ bytes: Data) { main { $0.message(endpoint, bytes) } }
    nonisolated func lanProgress(_ endpoint: String, _ fraction: Double) { main { $0.progress(endpoint, fraction) } }
    nonisolated func lanError(_ message: String) { main { $0.failed(message) } }
    nonisolated func lanPairCode(_ endpoint: String, name: String, code: String?) { main { $0.pairCode(endpoint, name, code) } }
    nonisolated func lanCodeNeeded(_ endpoint: String, name: String) { main { $0.codeNeeded(endpoint, name) } }
    nonisolated func lanRequest(_ endpoint: String, name: String, platform: String, needsCode: Bool, intent: String?, title: String?) {
        main { $0.request(endpoint, name, platform, intent: intent, title: title) }
    }
}
