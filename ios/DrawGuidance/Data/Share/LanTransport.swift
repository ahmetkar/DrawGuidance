import CryptoKit
import Foundation
import Network

/// Bağlantı yolu düzeyinde tanışma (TCP'nin ilk çerçevesi; Android `LanHello` ile aynı). v54: `v` protokol, `pk` geçici
/// P-256 açık anahtarı (base64), `k` bağlanan bu cihazla daha önce eşleşmiş mi, `m` kabul edenin seçtiği eşleşme yolu.
struct LanHello: Codable {
    var id: String
    var name: String
    var platform: String
    var v: Int?
    var pk: String?
    var k: Bool?
    var m: String?
    /// v56 bağlantının amacı ("live": canlı yayın; karşı cihazda önce onay sorulur) ve yayınlanan tuvalin adı.
    var intent: String?
    var title: String?
    /// v56 ret nedeni (HELLO_NO yanıtında: rejected / timeout / blocked).
    var reason: String?
}

protocol LanTransportDelegate: AnyObject {
    func lanFound(_ endpoint: String, name: String, platform: String)
    func lanLost(_ endpoint: String)
    func lanConnected(_ endpoint: String, name: String, platform: String)
    func lanDisconnected(_ endpoint: String)
    func lanMessage(_ endpoint: String, _ bytes: Data)
    func lanProgress(_ endpoint: String, _ fraction: Double)
    func lanError(_ message: String)
    /// v56 gelen bağlantı isteği: `accept` ya da `reject` ile yanıtlanır (şifre ancak kabulden sonra üretilir).
    func lanRequest(_ endpoint: String, name: String, platform: String, needsCode: Bool, intent: String?, title: String?)
    /// v54 kabul eden cihazda gösterilecek eşleşme şifresi (v56: yalnız kabulden sonra ve ilk eşleşmede).
    func lanPairCode(_ endpoint: String, name: String, code: String?)
    /// v54 bağlanan cihazda karşı cihazın ekranındaki şifre girilmeli (`submitCode`).
    func lanCodeNeeded(_ endpoint: String, name: String)
}

/// v51 aynı Wi-Fi ağındaki cihazlar (iOS ↔ Android, iOS ↔ iOS): Bonjour ile görünür olur ve arar (TXT: id, p, n), bağlantı
/// TCP'dir. Her çerçeve [u32 BE uzunluk][veri]; ilk çerçeve tanışmadır: bağlanan 0x7F + `LanHello`, kabul eden 0x7E +
/// `LanHello`. Gelen bağlantılar kendiliğinden kabul edilir (içerik ayrıca teklif onayıyla alınır).
///
/// v54 her bağlantı eşleşme şifresiyle doğrulanır ve şifrelenir (`SharePairing`): kabul eden cihaz 6 haneli şifre
/// gösterir, bağlanan cihazda girilir (daha önce eşleşmişlerde saklanan güven anahtarı). Bağlanan 0x7D + doğrulama kodu,
/// kabul eden 0x7B + kendi kodu (yanlışsa 0x7C) gönderir; sonraki çerçeveler AES-GCM ile şifrelidir.
final class LanTransport {
    weak var delegate: LanTransportDelegate?
    private let queue = DispatchQueue(label: "drawguidance.share.lan")
    private var listener: NWListener?
    private var browser: NWBrowser?
    private var me = LanHello(id: "", name: "iPhone", platform: "ios")
    private var found: [String: (endpoint: NWEndpoint, name: String, platform: String)] = [:]
    private var conns: [String: NWConnection] = [:]
    private var sealers: [String: SharePairing.Sealer] = [:]
    /// Şifre girilmesini bekleyen giden bağlantılar (A).
    private struct Awaiting { let c: NWConnection; let z: Data; let transcript: Data; let name: String; let platform: String }
    private var awaiting: [String: Awaiting] = [:]
    /// Doğrulaması süren gelen bağlantılar (B; vazgeçilince kapatılır).
    private var handshaking: [String: NWConnection] = [:]
    /// v56 kullanıcı onayı bekleyen gelen bağlantılar (B).
    private struct Incoming { let c: NWConnection; let hello: LanHello; let frame: Data; let key: P256.KeyAgreement.PrivateKey; let stored: Data?; let gate: PairingCodeGate }
    private var pending: [String: Incoming] = [:]

    private static let HELLO_REQ: UInt8 = 0x7F
    private static let HELLO_OK: UInt8 = 0x7E
    private static let PAIR_PROOF: UInt8 = 0x7D
    private static let PAIR_FAIL: UInt8 = 0x7C
    private static let PAIR_OK: UInt8 = 0x7B
    private static let HELLO_NO: UInt8 = 0x7A
    static let CONNECT_INTENT_LIVE = "live"
    static let REJECT_REASON = "rejected", REJECT_TIMEOUT = "timeout", REJECT_BLOCKED = "blocked"
    private static let MODE_CODE = "code", MODE_KNOWN = "known"

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

    func connect(_ endpoint: String, intent: String? = nil, title: String? = nil) {
        queue.async { [self] in
            guard let peer = found[endpoint] else { delegate?.lanDisconnected(endpoint); return }
            let c = NWConnection(to: peer.endpoint, using: .tcp)
            c.stateUpdateHandler = { [weak self] s in
                guard let self else { return }
                switch s {
                case .ready:
                    let key = SharePairing.newKey()
                    let stored = ShareTrustStore.get(endpoint)
                    var hello = self.me
                    hello.v = SharePairing.VERSION; hello.pk = SharePairing.publicRaw(key).base64EncodedString(); hello.k = stored != nil
                    hello.intent = intent; hello.title = title.map { String($0.prefix(80)) }
                    let request = Data([Self.HELLO_REQ]) + ((try? JSONEncoder().encode(hello)) ?? Data())
                    self.sendFrame(c, request)
                    self.readFrame(c, endpoint: nil) { frame in
                        if let frame, frame.first == Self.HELLO_NO {
                            // v56 karşı cihaz reddetti / yanıt vermedi / geçici olarak engelledi
                            let why = (try? JSONDecoder().decode(LanHello.self, from: frame.dropFirst()))?.reason
                            self.delegate?.lanError(why == Self.REJECT_TIMEOUT ? "Yanıt yok" : why == Self.REJECT_BLOCKED ? "Geçici olarak engellendi" : "Reddedildi")
                            c.cancel(); self.delegate?.lanDisconnected(endpoint); return
                        }
                        guard let frame, frame.first == Self.HELLO_OK,
                              let them = try? JSONDecoder().decode(LanHello.self, from: frame.dropFirst()) else {
                            c.cancel(); self.delegate?.lanDisconnected(endpoint); return
                        }
                        guard (them.v ?? 1) >= SharePairing.VERSION, let pk = them.pk.flatMap({ Data(base64Encoded: $0) }),
                              let z = try? SharePairing.sharedSecret(key, pk) else {
                            self.delegate?.lanError("Karşı cihazın uygulaması güncel değil")
                            c.cancel(); self.delegate?.lanDisconnected(endpoint); return
                        }
                        let transcript = SharePairing.transcript(request, frame)
                        let name = them.name.isEmpty ? peer.name : them.name
                        let platform = them.platform.isEmpty ? peer.platform : them.platform
                        if them.m == Self.MODE_KNOWN, let stored {
                            self.confirmOutgoing(c, endpoint, SharePairing.derive(z: z, secret: stored, transcript: transcript), name, platform, known: true)
                        } else {
                            if stored != nil { ShareTrustStore.remove(endpoint) }
                            self.awaiting[endpoint] = Awaiting(c: c, z: z, transcript: transcript, name: name, platform: platform)
                            self.delegate?.lanCodeNeeded(endpoint, name: name)
                        }
                    }
                case .failed, .cancelled:
                    if self.conns[endpoint] === c { self.conns[endpoint] = nil }
                    if self.awaiting[endpoint]?.c === c { self.awaiting[endpoint] = nil }
                    self.delegate?.lanDisconnected(endpoint)
                default: break
                }
            }
            c.start(queue: queue)
        }
    }

    /// v54 bağlanan cihazda girilen eşleşme şifresiyle doğrulama sürer.
    func submitCode(_ endpoint: String, _ code: String) {
        queue.async { [self] in
            guard let a = awaiting.removeValue(forKey: endpoint) else { return }
            let keys = SharePairing.derive(z: a.z, secret: Data(SharePairing.normalizeCode(code).utf8), transcript: a.transcript)
            confirmOutgoing(a.c, endpoint, keys, a.name, a.platform, known: false)
        }
    }

    func cancelCode(_ endpoint: String) {
        queue.async { [self] in
            awaiting.removeValue(forKey: endpoint)?.c.cancel()
        }
    }

    /// A: doğrulama kodunu gönderir, B'ninkini doğrular; doğruysa şifreli bağlantı açılır.
    private func confirmOutgoing(_ c: NWConnection, _ endpoint: String, _ keys: SharePairing.Keys, _ name: String, _ platform: String, known: Bool) {
        sendFrame(c, Data([Self.PAIR_PROOF]) + SharePairing.confirmTag(keys, "A"))
        readFrame(c, endpoint: nil) { [weak self] reply in
            guard let self else { return }
            guard let reply, reply.first == Self.PAIR_OK, SharePairing.same(Data(reply.dropFirst()), SharePairing.confirmTag(keys, "B")) else {
                if known { ShareTrustStore.remove(endpoint) }
                self.delegate?.lanError(known ? "Eşleşme yenilenmeli" : "Eşleşme şifresi yanlış")
                c.cancel()
                return
            }
            ShareTrustStore.put(endpoint, keys.trust)
            self.open(c, endpoint, name: name, platform: platform, sealer: SharePairing.Sealer(keys.aToB), opener: SharePairing.Opener(keys.bToA))
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
                    // Şifresiz (eski sürüm) bağlantı kabul edilmez
                    guard (hello.v ?? 1) >= SharePairing.VERSION, hello.pk.flatMap({ Data(base64Encoded: $0) }) != nil else {
                        self.delegate?.lanError("Karşı cihazın uygulaması güncel değil")
                        c.cancel(); return
                    }
                    // v56 şifre ancak kullanıcı kabul edince üretilir (PairingCodeGate); önce istek sorulur
                    let stored = (hello.k ?? false) ? ShareTrustStore.get(hello.id) : nil
                    self.pending.removeValue(forKey: hello.id)?.c.cancel()
                    self.pending[hello.id] = Incoming(c: c, hello: hello, frame: frame, key: SharePairing.newKey(), stored: stored,
                                                      gate: PairingCodeGate(known: stored != nil))
                    self.delegate?.lanRequest(hello.id, name: hello.name, platform: hello.platform, needsCode: stored == nil,
                                              intent: hello.intent, title: hello.title)
                }
            } else if case .failed = s {
                c.cancel()
            } else if case .cancelled = s, let id = self.pending.first(where: { $0.value.c === c })?.key {
                // Bağlanan cihaz onay beklerken vazgeçti: soru kapanır
                self.pending[id] = nil
                self.delegate?.lanDisconnected(id)
            }
        }
        c.start(queue: queue)
    }

    /// v56 gelen bağlantı kabul edildi: ilk eşleşmede şifre şimdi üretilip gösterilir, tanışma yanıtı gönderilir.
    func accept(_ endpoint: String) {
        queue.async { [self] in
            guard let p = pending.removeValue(forKey: endpoint), let pk = p.hello.pk.flatMap({ Data(base64Encoded: $0) }) else { return }
            let c = p.c, hello = p.hello, frame = p.frame
            let code = p.gate.approve()
            var me = self.me
            me.v = SharePairing.VERSION; me.pk = SharePairing.publicRaw(p.key).base64EncodedString(); me.m = code == nil ? Self.MODE_KNOWN : Self.MODE_CODE
            let reply = Data([Self.HELLO_OK]) + ((try? JSONEncoder().encode(me)) ?? Data())
            guard let z = try? SharePairing.sharedSecret(p.key, pk), let secret = code.map({ Data($0.utf8) }) ?? p.stored else {
                c.cancel(); delegate?.lanDisconnected(endpoint); return
            }
            let keys = SharePairing.derive(z: z, secret: secret, transcript: SharePairing.transcript(frame, reply))
            handshaking[hello.id]?.cancel()
            handshaking[hello.id] = c
            if let code { delegate?.lanPairCode(hello.id, name: hello.name, code: code) }
            sendFrame(c, reply)
            // Şifrenin girilmesi beklenir; doğrulama kodu gelince
            readFrame(c, endpoint: nil) { [self] proof in
                if self.handshaking[hello.id] === c { self.handshaking[hello.id] = nil }
                guard let proof, proof.first == Self.PAIR_PROOF, SharePairing.same(Data(proof.dropFirst()), SharePairing.confirmTag(keys, "A")) else {
                    if proof != nil { self.sendFrame(c, Data([Self.PAIR_FAIL])) }
                    if code == nil { ShareTrustStore.remove(hello.id) }
                    if proof != nil { self.delegate?.lanError(code == nil ? "Eşleşme yenilenmeli" : "Eşleşme şifresi yanlış") }
                    self.delegate?.lanDisconnected(hello.id)
                    self.queue.asyncAfter(deadline: .now() + 0.3) { c.cancel() }
                    return
                }
                self.sendFrame(c, Data([Self.PAIR_OK]) + SharePairing.confirmTag(keys, "B"))
                ShareTrustStore.put(hello.id, keys.trust)
                self.open(c, hello.id, name: hello.name, platform: hello.platform, sealer: SharePairing.Sealer(keys.bToA), opener: SharePairing.Opener(keys.aToB))
            }
        }
    }

    /// v54 kabul eden cihazda eşleşmeden vazgeçildi. v56 onay beklenirken reddedilirse neden bağlanan cihaza bildirilir
    /// (eski sürüm yalnız bağlantının kapandığını görür).
    func reject(_ endpoint: String, reason: String = LanTransport.REJECT_REASON) {
        queue.async { [self] in
            if let p = pending.removeValue(forKey: endpoint) {
                var me = self.me
                me.reason = reason
                let no = Data([Self.HELLO_NO]) + ((try? JSONEncoder().encode(me)) ?? Data())
                p.c.send(content: ShareWire.u32(no.count) + no, completion: .contentProcessed { _ in p.c.cancel() })
                return
            }
            handshaking.removeValue(forKey: endpoint)?.cancel()
        }
    }

    private func open(_ c: NWConnection, _ endpoint: String, name: String, platform: String, sealer: SharePairing.Sealer, opener: SharePairing.Opener) {
        if let old = conns[endpoint], old !== c { old.cancel() }
        conns[endpoint] = c
        sealers[endpoint] = sealer
        c.stateUpdateHandler = { [weak self] s in
            guard let self else { return }
            switch s {
            case .failed, .cancelled:
                if self.conns[endpoint] === c { self.conns[endpoint] = nil; self.sealers[endpoint] = nil; self.delegate?.lanDisconnected(endpoint) }
            default: break
            }
        }
        delegate?.lanConnected(endpoint, name: name, platform: platform)
        loop(c, endpoint, opener)
    }

    private func loop(_ c: NWConnection, _ endpoint: String, _ opener: SharePairing.Opener) {
        readFrame(c, endpoint: endpoint) { [weak self] frame in
            guard let self else { return }
            // Doğrulanamayan (değiştirilmiş) çerçeve bağlantıyı kapatır
            guard let frame, let plain = try? opener.open(frame) else { c.cancel(); return }
            self.delegate?.lanMessage(endpoint, plain)
            self.loop(c, endpoint, opener)
        }
    }

    func disconnect(_ endpoint: String) {
        queue.async { [self] in conns[endpoint]?.cancel() }
    }

    func send(_ endpoint: String, _ message: Data) {
        queue.async { [self] in
            guard let c = conns[endpoint], let sealer = sealers[endpoint], let sealed = try? sealer.seal(message) else { delegate?.lanError("no connection"); return }
            sendFrame(c, sealed)
        }
    }

    func shutdown() {
        stop()
        queue.async { [self] in
            conns.values.forEach { $0.cancel() }; conns.removeAll(); sealers.removeAll()
            awaiting.values.forEach { $0.c.cancel() }; awaiting.removeAll()
            handshaking.values.forEach { $0.cancel() }; handshaking.removeAll()
            pending.values.forEach { $0.c.cancel() }; pending.removeAll()
        }
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
            guard n >= 0, n <= ShareWire.MAX_MESSAGE + 16 else { done(nil); return }
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
