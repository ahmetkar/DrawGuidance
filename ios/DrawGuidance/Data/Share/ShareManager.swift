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
struct ShareLiveIn: Equatable { let canvasId: String; let peerId: String; let peerName: String; var ended = false; var paused = false }

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

    private struct Outgoing { let peerId: String; let type: String; let title: String; let canvasId: String?; let image: SavedImage? }
    private var outgoing: [String: Outgoing] = [:]
    private var afterConnect: [String: [() -> Void]] = [:]
    private var liveIds: [String: String] = [:]
    private var liveSeqIn: Int64 = -1
    private var liveOfferId: String?
    private var liveSent = Set<String>()
    private var liveSeqOut: Int64 = 0
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

    private func whenConnected(_ id: String, _ action: @escaping () -> Void) {
        guard let peer = peers.first(where: { $0.id == id }) else { fail(L("v51_share_failed", "?")); return }
        if peer.state == .connected { action(); return }
        afterConnect[id, default: []].append(action)
        if peer.state != .connecting {
            setState(id, .connecting)
            transport.connect(id)
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

    private func offerOut(_ peerId: String, type: String, title: String, canvasId: String?, image: SavedImage?, bytes: Int64, kind: String?) {
        let id = UUID().uuidString.lowercased()
        outgoing[id] = Outgoing(peerId: peerId, type: type, title: title, canvasId: canvasId, image: image)
        let o = ShareOffer(id: id, type: type, title: title, bytes: bytes, canvasKind: kind)
        status = ShareTransferStatus(peerName: peerName(peerId), title: title, sending: true)
        whenConnected(peerId) { [weak self] in self?.send(peerId, ShareWire.encode(ShareWire.OFFER, o)) }
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
        liveIn = nil
        liveCanvas = nil
        showLive = false
        if !inn.ended { send(inn.peerId, ShareWire.encode(ShareWire.LIVE_STOP, Data("{}".utf8))) }
    }

    /// Düzenleyici yayın yapılan tuvalin her değişikliğini bildirir; en son hali ayarlanan sıklıkta gönderilir.
    func publishLive(_ canvas: CanvasUi) {
        guard liveOut?.canvasId == canvas.localId, liveOut?.paused != true else { return }
        liveFrame = canvas
    }

    /// v52 yayına ara ver (yayın yapılan notta şekil/resim başka tuvalde düzenleniyor); izleyene bildirilir.
    func pauseLive(_ canvasId: String) {
        guard var out = liveOut, out.canvasId == canvasId, !out.paused else { return }
        out.paused = true
        liveOut = out
        send(out.peerId, ShareWire.encode(ShareWire.LIVE_PAUSE, Data("{}".utf8)))
    }

    /// v52 ara verilen yayın sürer (düzenleyici son hali [publishLive] ile gönderir).
    func resumeLive(_ canvasId: String) {
        guard var out = liveOut, out.canvasId == canvasId, out.paused else { return }
        out.paused = false
        liveOut = out
        send(out.peerId, ShareWire.encode(ShareWire.LIVE_RESUME, Data("{}".utf8)))
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
        send(id, ShareWire.encode(ShareWire.HELLO, ShareHello(name: settings.deviceName, platform: "ios", app: version)))
        afterConnect.removeValue(forKey: id)?.forEach { $0() }
    }

    fileprivate func disconnected(_ id: String) {
        let peer = peers.first { $0.id == id }
        let wasConnected = peer?.state == .connected
        if pairShow?.peerId == id { pairShow = nil }
        if codePrompt?.peerId == id { codePrompt = nil }
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
            setState(id, .connected, name: h.name, platform: h.platform)
        case ShareWire.OFFER:
            let o = try m.header(ShareOffer.self)
            // v52 canlı yayın daveti kendi ayarıyla
            let ask = o.type == ShareWire.TYPE_LIVE ? settings.askBeforeLive : settings.askBeforeReceive
            if ask { offer = ShareIncomingOffer(peerId: id, peerName: peerName(id), offer: o) }
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
            _ = try packages.install(pkg, ids: &liveIds, existingId: inn.canvasId)
            refreshLiveCanvas(inn.canvasId)
        case ShareWire.LIVE_PAUSE, ShareWire.LIVE_RESUME:
            if liveIn?.peerId == id, liveIn?.ended == false { liveIn?.paused = m.type == ShareWire.LIVE_PAUSE }
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
        send(id, ShareWire.encode(accept ? ShareWire.ACCEPT : ShareWire.REJECT, ShareReply(id: o.id, canvasId: nil)))
        if accept { status = ShareTransferStatus(peerName: peerName(id), title: o.title, sending: false) }
    }

    private func deliver(_ id: String, _ out: Outgoing) {
        let from = settings.deviceName
        switch out.type {
        case ShareWire.TYPE_CANVAS:
            guard let cid = out.canvasId, let pkg = packages.canvasPackage(cid, type: ShareWire.TYPE_CANVAS, id: id, from: from) else {
                fail(L("v51_share_failed", out.title)); return
            }
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
            guard let cid = out.canvasId, let pkg = packages.canvasPackage(cid, type: ShareWire.TYPE_LIVE, id: id, from: from) else {
                fail(L("v51_share_failed", out.title)); return
            }
            liveSent = Set(pkg.header.assets.map(\.id))
            liveSeqOut = 0
            liveOfferId = id
            send(out.peerId, pkg.encode(ShareWire.LIVE_START))
            liveOut = ShareLiveOut(canvasId: cid, peerId: out.peerId, peerName: peerName(out.peerId))
            status = nil
            startLiveLoop(id)
            openCanvas = ShareOpenCanvas(id: cid)
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
                    self.liveSeqOut += 1
                    let seq = self.liveSeqOut
                    let before = self.liveSent
                    let from = self.settings.deviceName
                    // Paket arka planda hazırlanır (içerik kodlama ve resim okuma)
                    let (pkg, newSent) = await Task.detached(priority: .userInitiated) { () -> (SharePackage, Set<String>) in
                        var sent = before
                        let p = packages.livePackage(frame, id: id, seq: seq, sent: &sent, from: from)
                        return (p, sent)
                    }.value
                    guard !Task.isCancelled, self.liveOut == out else { return }
                    self.liveSent = newSent
                    self.send(out.peerId, pkg.encode(ShareWire.LIVE_UPDATE))
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
}
