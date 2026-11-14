import Foundation
import Network

/// Bağlantı yolu düzeyinde tanışma (TCP'nin ilk çerçevesi; Android `LanHello` ile aynı).
struct LanHello: Codable {
    var id: String
    var name: String
    var platform: String
}

protocol LanTransportDelegate: AnyObject {
    func lanFound(_ endpoint: String, name: String, platform: String)
    func lanLost(_ endpoint: String)
    func lanConnected(_ endpoint: String, name: String, platform: String)
    func lanDisconnected(_ endpoint: String)
    func lanMessage(_ endpoint: String, _ bytes: Data)
    func lanProgress(_ endpoint: String, _ fraction: Double)
    func lanError(_ message: String)
}

/// v51 aynı Wi-Fi ağındaki cihazlar (iOS ↔ Android, iOS ↔ iOS): Bonjour ile görünür olur ve arar (TXT: id, p, n), bağlantı
/// TCP'dir. Her çerçeve [u32 BE uzunluk][veri]; ilk çerçeve tanışmadır: bağlanan 0x7F + `LanHello`, kabul eden 0x7E +
/// `LanHello`. Gelen bağlantılar kendiliğinden kabul edilir (içerik ayrıca teklif onayıyla alınır).
final class LanTransport {
    weak var delegate: LanTransportDelegate?
    private let queue = DispatchQueue(label: "drawguidance.share.lan")
    private var listener: NWListener?
    private var browser: NWBrowser?
    private var me = LanHello(id: "", name: "iPhone", platform: "ios")
    private var found: [String: (endpoint: NWEndpoint, name: String, platform: String)] = [:]
    private var conns: [String: NWConnection] = [:]

    private static let HELLO_REQ: UInt8 = 0x7F
    private static let HELLO_OK: UInt8 = 0x7E

    func start(name: String, instanceId: String) {
        queue.async { [self] in
            guard listener == nil else { return }
            me = LanHello(id: instanceId, name: name, platform: "ios")
            do {
                let l = try NWListener(using: .tcp)
                var txt = NWTXTRecord()
                txt["id"] = instanceId
                txt["p"] = "ios"
                txt["n"] = String(name.prefix(60))
                l.service = NWListener.Service(name: String(name.prefix(30)) + " · DG", type: ShareWire.LAN_SERVICE_TYPE, domain: nil, txtRecord: txt)
                l.newConnectionHandler = { [weak self] c in self?.incoming(c) }
                l.stateUpdateHandler = { [weak self] s in if case let .failed(e) = s { self?.delegate?.lanError(e.localizedDescription) } }
                l.start(queue: queue)
                listener = l
            } catch {
                delegate?.lanError(error.localizedDescription)
            }
            let b = NWBrowser(for: .bonjourWithTXTRecord(type: ShareWire.LAN_SERVICE_TYPE, domain: nil), using: .tcp)
            b.browseResultsChangedHandler = { [weak self] results, _ in self?.update(results) }
            b.start(queue: queue)
            browser = b
        }
    }

    func stop() {
        queue.async { [self] in
            listener?.cancel(); listener = nil
            browser?.cancel(); browser = nil
            found.removeAll()
        }
    }

    private func update(_ results: Set<NWBrowser.Result>) {
        var seen = Set<String>()
        for r in results {
            guard case let .bonjour(txt) = r.metadata, let id = txt["id"], !id.isEmpty, id != me.id else { continue }
            var serviceName = ""
            if case let .service(n, _, _, _) = r.endpoint { serviceName = n }
            let name = (txt["n"]?.isEmpty == false ? txt["n"]! : serviceName.replacingOccurrences(of: " · DG", with: ""))
            let platform = txt["p"] ?? "android"
            seen.insert(id)
            if found[id] == nil { delegate?.lanFound(id, name: name, platform: platform) }
            found[id] = (r.endpoint, name, platform)
        }
        for id in found.keys where !seen.contains(id) {
            found[id] = nil
            if conns[id] == nil { delegate?.lanLost(id) }
        }
    }

    // MARK: Bağlantılar

    func connect(_ endpoint: String) {
        queue.async { [self] in
            guard let peer = found[endpoint] else { delegate?.lanDisconnected(endpoint); return }
            let c = NWConnection(to: peer.endpoint, using: .tcp)
            c.stateUpdateHandler = { [weak self] s in
                guard let self else { return }
                switch s {
                case .ready:
                    self.sendFrame(c, Data([Self.HELLO_REQ]) + ((try? JSONEncoder().encode(self.me)) ?? Data()))
                    self.readFrame(c, endpoint: nil) { frame in
                        guard let frame, frame.first == Self.HELLO_OK,
                              let them = try? JSONDecoder().decode(LanHello.self, from: frame.dropFirst()) else {
                            c.cancel(); self.delegate?.lanDisconnected(endpoint); return
                        }
                        self.open(c, endpoint, name: them.name.isEmpty ? peer.name : them.name, platform: them.platform.isEmpty ? peer.platform : them.platform)
                    }
                case .failed, .cancelled:
                    if self.conns[endpoint] === c { self.conns[endpoint] = nil }
                    self.delegate?.lanDisconnected(endpoint)
                default: break
                }
            }
            c.start(queue: queue)
        }
    }

    private func incoming(_ c: NWConnection) {
        c.stateUpdateHandler = { [weak self] s in
            guard let self else { return }
            if case .ready = s {
                self.readFrame(c, endpoint: nil) { frame in
                    guard let frame, frame.first == Self.HELLO_REQ,
                          let hello = try? JSONDecoder().decode(LanHello.self, from: frame.dropFirst()), !hello.id.isEmpty, hello.id != self.me.id else {
                        c.cancel(); return
                    }
                    self.sendFrame(c, Data([Self.HELLO_OK]) + ((try? JSONEncoder().encode(self.me)) ?? Data()))
                    self.open(c, hello.id, name: hello.name, platform: hello.platform)
                }
            } else if case .failed = s {
                c.cancel()
            }
        }
        c.start(queue: queue)
    }

    private func open(_ c: NWConnection, _ endpoint: String, name: String, platform: String) {
        if let old = conns[endpoint], old !== c { old.cancel() }
        conns[endpoint] = c
        c.stateUpdateHandler = { [weak self] s in
            guard let self else { return }
            switch s {
            case .failed, .cancelled:
                if self.conns[endpoint] === c { self.conns[endpoint] = nil; self.delegate?.lanDisconnected(endpoint) }
            default: break
            }
        }
        delegate?.lanConnected(endpoint, name: name, platform: platform)
        loop(c, endpoint)
    }

    private func loop(_ c: NWConnection, _ endpoint: String) {
        readFrame(c, endpoint: endpoint) { [weak self] frame in
            guard let self else { return }
            guard let frame else { c.cancel(); return }
            self.delegate?.lanMessage(endpoint, frame)
            self.loop(c, endpoint)
        }
    }

    func disconnect(_ endpoint: String) {
        queue.async { [self] in conns[endpoint]?.cancel() }
    }

    func send(_ endpoint: String, _ message: Data) {
        queue.async { [self] in
            guard let c = conns[endpoint] else { delegate?.lanError("no connection"); return }
            sendFrame(c, message)
        }
    }

    func shutdown() {
        stop()
        queue.async { [self] in conns.values.forEach { $0.cancel() }; conns.removeAll() }
    }

    // MARK: Çerçeveler

    private func sendFrame(_ c: NWConnection, _ data: Data) {
        c.send(content: ShareWire.u32(data.count) + data, completion: .contentProcessed { [weak self] e in
            if let e { self?.delegate?.lanError(e.localizedDescription) }
        })
    }

    private func readFrame(_ c: NWConnection, endpoint: String?, _ done: @escaping (Data?) -> Void) {
        c.receive(minimumIncompleteLength: 4, maximumLength: 4) { [weak self] head, _, _, error in
            guard let head, head.count == 4, error == nil else { done(nil); return }
            let n = Int(ShareWire.readU32([UInt8](head), 0))
            guard n >= 0, n <= ShareWire.MAX_MESSAGE else { done(nil); return }
            if n == 0 { done(Data()); return }
            self?.readBody(c, n, Data(capacity: n), endpoint: endpoint, done)
        }
    }

    private func readBody(_ c: NWConnection, _ n: Int, _ acc: Data, endpoint: String?, _ done: @escaping (Data?) -> Void) {
        c.receive(minimumIncompleteLength: 1, maximumLength: min(256 * 1024, n - acc.count)) { [weak self] chunk, _, _, error in
            guard let chunk, error == nil else { done(nil); return }
            var all = acc
            all.append(chunk)
            if let endpoint, n > 256 * 1024 { self?.delegate?.lanProgress(endpoint, Double(all.count) / Double(n)) }
            if all.count >= n { done(all) } else { self?.readBody(c, n, all, endpoint: endpoint, done) }
        }
    }
}
