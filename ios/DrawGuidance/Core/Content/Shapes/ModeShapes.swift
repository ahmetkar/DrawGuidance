import Foundation

/// v29 moda özel arama kapsamı.
final class ShapeScope {
    let mode: ShapeMode
    let keys: Set<String>
    let net: ShapeClassifier?
    let netWeight: Float
    let usage: [String: Int]
    let rejects: [String: Int]
    init(_ mode: ShapeMode, _ keys: Set<String>, _ net: ShapeClassifier?, _ netWeight: Float, _ usage: [String: Int], _ rejects: [String: Int]) {
        self.mode = mode; self.keys = keys; self.net = net; self.netWeight = netWeight; self.usage = usage; self.rejects = rejects
    }
}

/// v29 modların şablon kapsamı (Android `ModeCatalog`).
enum ModeCatalog {
    static let NOTES_PREFIX = "ders."
    static let COMIC_PREFIX = "roman."
    static let KIDS_PREFIX = "cocuk."
    /// v50 yetişkin alıştırma şekillerinin şablonları (resim modları; basit resimde yalnız temel olanlar).
    static let PRACTICE_PREFIX = "alistirma."
    static let PRACTICE_CATEGORY = "Alıştırma"
    static let PRACTICE_ADVANCED_CATEGORY = "Alıştırma (ileri)"
    static let OBJECT_PREFIX = LibraryTemplates.OBJECT_PREFIX
    private static let notesCategories: Set<String> = ["Simge", "3B cisim", "Nesne", "Teknoloji", "Alet", "Gökyüzü ve doğa", "Bitki", "Meyve", "Müzik"]
    private static let comicExcluded: Set<String> = ["3B cisim", "Teknoloji"]
    private static let kidsCategories: Set<String> = [
        "Hayvan", "Meyve", "Sebze", "Gökyüzü ve doğa", "Bitki", "Taşıt", "Spor ve oyun", "Yiyecek ve mutfak", "Hayal",
        "Simge", "Nesne", "Müzik", "Yapı",
    ]
    private static let simpleArtExcluded: Set<String> = [
        "Vücut", "Hayvan uzvu", "Saç", "Çatı", "Kapı", "Pencere", "Teknoloji", "Fen", "Matematik", "Şema ve grafik",
        "Çizgi roman", "Sandalye", "Alet", PRACTICE_ADVANCED_CATEGORY,
    ]

    static func allowsSimpleArt(_ key: String, _ category: String) -> Bool { allows(.ART, key, category) && !simpleArtExcluded.contains(category) }

    static func isModeOnly(_ key: String) -> Bool {
        key.hasPrefix(NOTES_PREFIX) || key.hasPrefix(COMIC_PREFIX) || key.hasPrefix(KIDS_PREFIX) || key.hasPrefix(OBJECT_PREFIX) ||
            key.hasPrefix(PRACTICE_PREFIX)
    }

    private static func general(_ key: String) -> Bool { !isModeOnly(key) || key.hasPrefix(OBJECT_PREFIX) }

    static func allows(_ mode: ShapeMode, _ key: String, _ category: String) -> Bool {
        switch mode {
        case .ART: return general(key) || key.hasPrefix(PRACTICE_PREFIX)
        case .NOTES: return key.hasPrefix(NOTES_PREFIX) || (general(key) && notesCategories.contains(category))
        case .COMIC: return key.hasPrefix(COMIC_PREFIX) || (general(key) && !comicExcluded.contains(category))
        case .KIDS: return key.hasPrefix(KIDS_PREFIX) || (!isModeOnly(key) && kidsCategories.contains(category))
        }
    }

    static func keys(_ mode: ShapeMode, _ metas: [TemplateMeta]) -> [String] { metas.filter { allows(mode, $0.key, $0.category) }.map(\.key) }
}

/// v29 moda özel, cihazda eğitilen sinir ağı (float).
final class ModeNet: ShapeClassifier {
    let keys: [String]
    let inDim: Int
    let hidden: Int
    var w1: [Float], b1: [Float], w2: [Float], b2: [Float]
    private let keyIndex: [String: Int]
    private let lock = NSLock()
    var outDim: Int { keys.count }

    init(_ keys: [String], _ inDim: Int, _ hidden: Int, _ w1: [Float], _ b1: [Float], _ w2: [Float], _ b2: [Float]) {
        self.keys = keys; self.inDim = inDim; self.hidden = hidden; self.w1 = w1; self.b1 = b1; self.w2 = w2; self.b2 = b2
        var m: [String: Int] = [:]
        for (i, k) in keys.enumerated() { m[k] = i }
        keyIndex = m
    }

    func indexOf(_ key: String) -> Int { keyIndex[key] ?? -1 }

    private func hiddenOf(_ x: [Float]) -> [Float] {
        var h = [Float](repeating: 0, count: hidden)
        for j in 0..<hidden {
            var acc = b1[j]
            let o = j * inDim
            for i in 0..<inDim { acc += w1[o + i] * x[i] }
            h[j] = acc > 0 ? acc : 0
        }
        return h
    }

    private func outOf(_ h: [Float]) -> [Float] {
        var out = [Float](repeating: 0, count: outDim)
        var mx = -Float.infinity
        for k in 0..<outDim {
            var acc = b2[k]
            let o = k * hidden
            for j in 0..<hidden { acc += w2[o + j] * h[j] }
            out[k] = acc
            if acc > mx { mx = acc }
        }
        var sum: Float = 0
        for k in 0..<outDim { out[k] = Float(exp(Double(out[k] - mx))); sum += out[k] }
        for k in 0..<outDim { out[k] /= sum }
        return out
    }

    func probs(_ x: [Float]) -> [Float] { lock.withLock { outOf(hiddenOf(x)) } }

    private func step(_ x: [Float], _ h: [Float], _ grad: [Float], _ lr: Float, _ lrHidden: Float) {
        var dh: [Float]? = lrHidden > 0 ? [Float](repeating: 0, count: hidden) : nil
        for k in 0..<outDim {
            let g = grad[k]
            if g == 0 { continue }
            let o = k * hidden
            if dh != nil { for j in 0..<hidden { dh![j] += g * w2[o + j] } }
            for j in 0..<hidden { w2[o + j] -= lr * g * h[j] }
            b2[k] -= lr * g
        }
        if let dh {
            for j in 0..<hidden {
                if h[j] <= 0 { continue }
                let g = dh[j].coerceIn(-1, 1)
                let o = j * inDim
                for i in 0..<inDim { w1[o + i] -= lrHidden * g * x[i] }
                b1[j] -= lrHidden * g
            }
        }
    }

    @discardableResult
    func train(_ x: [Float], _ target: Int, _ lr: Float, _ lrHidden: Float = 0) -> Float {
        lock.withLock {
            if !(0..<outDim).contains(target) { return 0 }
            let h = hiddenOf(x)
            let p = outOf(h)
            var grad = p
            grad[target] -= 1
            step(x, h, grad, lr, lrHidden)
            return -Float(log(Double(max(p[target], 1e-7))))
        }
    }

    func unlearn(_ x: [Float], _ k: Int, _ lr: Float, _ lrHidden: Float = 0) {
        lock.withLock {
            if !(0..<outDim).contains(k) { return }
            let h = hiddenOf(x)
            let p = outOf(h)
            let factor = min(p[k] / max(1 - p[k], 1e-4), 2)
            if factor < 1e-4 { return }
            var grad = p.map { -factor * $0 }
            grad[k] += factor
            step(x, h, grad, lr, lrHidden)
        }
    }

    func write(_ d: inout JavaDataWriter) {
        d.int(Int32(inDim)); d.int(Int32(hidden)); d.int(Int32(keys.count))
        for k in keys { d.utf(k) }
        for v in w1 { d.float(v) }; for v in b1 { d.float(v) }; for v in w2 { d.float(v) }; for v in b2 { d.float(v) }
    }

    static func read(_ d: inout JavaDataReader) throws -> ModeNet {
        let inDim = Int(try d.int()), hidden = Int(try d.int()), k = Int(try d.int())
        var keys: [String] = []
        for _ in 0..<k { keys.append(try d.utf()) }
        func fl(_ n: Int) throws -> [Float] { var a: [Float] = []; a.reserveCapacity(n); for _ in 0..<n { a.append(try d.float()) }; return a }
        let w1 = try fl(inDim * hidden), b1 = try fl(hidden), w2 = try fl(hidden * k), b2 = try fl(k)
        return ModeNet(keys, inDim, hidden, w1, b1, w2, b2)
    }

    static func initNet(_ keys: [String], _ base: ShapeNet?, _ prev: ModeNet? = nil) -> ModeNet {
        let inDim = ShapeNet.IN_DIM
        let src = prev?.inDim == inDim ? prev : nil
        let hidden = src?.hidden ?? base?.hidden ?? 96
        var w1: [Float], b1: [Float]
        if let src { w1 = src.w1; b1 = src.b1 }
        else if let base { (w1, b1) = base.hiddenLayer() }
        else {
            var rnd = JavaRandom(29)
            w1 = (0..<(inDim * hidden)).map { _ in (rnd.nextFloat() - 0.5) * 0.3 }
            b1 = [Float](repeating: 0.01, count: hidden)
        }
        var w2 = [Float](repeating: 0, count: hidden * keys.count)
        var b2 = [Float](repeating: 0, count: keys.count)
        var known = [Bool](repeating: false, count: keys.count)
        for (k, key) in keys.enumerated() {
            let pi = src?.indexOf(key) ?? -1
            if pi >= 0, let src {
                for j in 0..<hidden { w2[k * hidden + j] = src.w2[pi * hidden + j] }
                b2[k] = src.b2[pi]; known[k] = true
                continue
            }
            let bi = (base != nil && base!.hidden == hidden) ? base!.indexOf(key) : -1
            if bi >= 0, let base {
                let row = base.outputRow(bi)
                for j in 0..<hidden { w2[k * hidden + j] = row[j] }
                b2[k] = base.outputBias(bi); known[k] = true
            }
        }
        let low: Float = known.contains(true) ? b2.indices.filter { known[$0] }.map { b2[$0] }.min()! - 0.5 : 0
        for k in keys.indices where !known[k] { b2[k] = low }
        return ModeNet(keys, inDim, hidden, w1, b1, w2, b2)
    }
}

struct ModeInfo: Equatable { let mode: ShapeMode; let templates: Int; let accepted: Int; let rejected: Int; let trainedSamples: Int; let ready: Bool }

/// Java `DataOutputStream` yazıcısı (büyük uçlu, writeUTF değiştirilmiş UTF-8).
struct JavaDataWriter {
    private(set) var bytes: [UInt8] = []
    mutating func int(_ v: Int32) { let u = UInt32(bitPattern: v); bytes += [UInt8(u >> 24), UInt8((u >> 16) & 0xFF), UInt8((u >> 8) & 0xFF), UInt8(u & 0xFF)] }
    mutating func float(_ v: Float) { int(Int32(bitPattern: v.bitPattern)) }
    mutating func utf(_ s: String) {
        var out: [UInt8] = []
        for u in s.utf16 {
            if u >= 1 && u <= 0x7F { out.append(UInt8(u)) }
            else if u <= 0x7FF { out += [UInt8(0xC0 | (u >> 6)), UInt8(0x80 | (u & 0x3F))] }
            else { out += [UInt8(0xE0 | (u >> 12)), UInt8(0x80 | ((u >> 6) & 0x3F)), UInt8(0x80 | (u & 0x3F))] }
        }
        bytes += [UInt8(out.count >> 8), UInt8(out.count & 0xFF)] + out
    }
}

/// v29 bir modun modeli: sinir ağı, kabul/ret sayaçları ve tekrar tamponu.
final class ModeModel {
    let mode: ShapeMode
    var net: ModeNet
    var usage: [String: Int]
    var rejects: [String: Int]
    var replay: [(String, [Float])]
    var trained: Int
    var catalogVersion: Int
    var baseVersion: Int
    let format: Int
    private let rnd: JRandom
    private let lock = NSRecursiveLock()

    static let MAGIC: Int32 = 0x4D4F_4E54
    static let FORMAT = 3
    static let LR: Float = 0.05
    static let LR_HIDDEN: Float = 0.004
    static let REPLAY_STEPS = 6
    static let MAX_REPLAY = 240
    static let MAX_NET_WEIGHT: Float = 0.32

    init(_ mode: ShapeMode, _ net: ModeNet, usage: [String: Int] = [:], rejects: [String: Int] = [:], replay: [(String, [Float])] = [],
         trained: Int = 0, catalogVersion: Int = ShapeTemplates.VERSION, baseVersion: Int = ShapeNet.MODE_NET_VERSION, format: Int = ModeModel.FORMAT) {
        self.mode = mode; self.net = net; self.usage = usage; self.rejects = rejects; self.replay = replay; self.trained = trained
        self.catalogVersion = catalogVersion; self.baseVersion = baseVersion; self.format = format
        rnd = JRandom(Int64(mode.ordinal) * 31 + 7)
    }

    var accepted: Int { usage.values.reduce(0, +) }
    var rejected: Int { rejects.values.reduce(0, +) }
    var netWeight: Float { min(AdvancedWeights.net + 0.02 * Float(trained), Self.MAX_NET_WEIGHT) }

    func scope(_ keys: Set<String>) -> ShapeScope { lock.withLock { ShapeScope(mode, keys, net, netWeight, usage, rejects) } }

    func bootstrap(_ unknown: [ShapeTemplate], _ known: [ShapeTemplate], samples: Int = 20, epochs: Int = 6) {
        lock.withLock {
            if unknown.isEmpty { return }
            var data: [(Int, [Float])] = []
            let r = JRandom(4049 + Int64(mode.ordinal))
            for t in unknown {
                let k = net.indexOf(t.key)
                if k < 0 { continue }
                for i in 0..<samples {
                    let drawn = ShapeAugment.manyElements(t.strokes, r, part: i % 5 == 4, amount: i % 3 == 0 ? 1.4 : 1, strong: i % 4 == 3)
                    if let x = ShapeNet.inputOf(drawn) { data.append((k, x)) }
                }
            }
            for t in r.shuffled(known).prefix(min(known.count, unknown.count * 4)) {
                let k = net.indexOf(t.key)
                if k < 0 { continue }
                if let x = ShapeNet.inputOf(ShapeAugment.manyElements(t.strokes, r)) { data.append((k, x)) }
            }
            for e in 0..<epochs {
                r.shuffle(&data)
                let lr = 0.12 * (1 - Float(e) / (Float(epochs) + 1))
                for (k, x) in data { net.train(x, k, lr) }
            }
        }
    }

    func relearn(epochs: Int = 3) {
        lock.withLock {
            if replay.isEmpty { return }
            for _ in 0..<epochs {
                for (key, x) in rnd.shuffled(replay) { let k = net.indexOf(key); if k >= 0 { net.train(x, k, Self.LR, Self.LR_HIDDEN) } }
            }
        }
    }

    func accept(_ key: String, _ x: [Float]?) {
        lock.withLock {
            usage[key] = (usage[key] ?? 0) + 1
            let k = net.indexOf(key)
            guard let x, k >= 0 else { return }
            for _ in 0..<2 { net.train(x, k, Self.LR, Self.LR_HIDDEN) }
            if !replay.isEmpty {
                for _ in 0..<min(Self.REPLAY_STEPS, replay.count) {
                    let (rk, rx) = replay[rnd.nextInt(replay.count)]
                    let ri = net.indexOf(rk)
                    if ri >= 0 { net.train(rx, ri, Self.LR * 0.5, 0) }
                }
            }
            replay.append((key, x))
            while replay.count > Self.MAX_REPLAY { replay.removeFirst() }
            trained += 1
        }
    }

    func reject(_ keys: [String], _ x: [Float]?) {
        lock.withLock {
            for k in keys { rejects[k] = (rejects[k] ?? 0) + 1 }
            guard let x else { return }
            for key in keys { let k = net.indexOf(key); if k >= 0 { net.unlearn(x, k, Self.LR * 0.4, Self.LR_HIDDEN * 0.5) } }
            trained += 1
        }
    }

    func info(_ templates: Int, ready: Bool = true) -> ModeInfo {
        ModeInfo(mode: mode, templates: templates, accepted: accepted, rejected: rejected, trainedSamples: trained, ready: ready)
    }

    func encode() -> Data {
        lock.withLock {
            var d = JavaDataWriter()
            d.int(Self.MAGIC); d.int(Int32(Self.FORMAT))
            d.utf(mode.rawValue); d.int(Int32(catalogVersion)); d.int(Int32(trained)); d.int(Int32(baseVersion))
            net.write(&d)
            d.int(Int32(usage.count)); for (k, v) in usage.sorted(by: { $0.key < $1.key }) { d.utf(k); d.int(Int32(v)) }
            d.int(Int32(rejects.count)); for (k, v) in rejects.sorted(by: { $0.key < $1.key }) { d.utf(k); d.int(Int32(v)) }
            d.int(Int32(replay.count))
            for (k, x) in replay { d.utf(k); d.int(Int32(x.count)); for v in x { d.float(v) } }
            return Data(d.bytes)
        }
    }

    static func decode(_ bytes: Data) -> ModeModel? {
        var d = JavaDataReader(bytes)
        do {
            guard try d.int() == MAGIC else { return nil }
            let format = Int(try d.int())
            guard (1...FORMAT).contains(format), let mode = ShapeMode(rawValue: try d.utf()) else { return nil }
            let version = Int(try d.int()), trained = Int(try d.int())
            let base = format >= 2 ? Int(try d.int()) : 0
            let net = try ModeNet.read(&d)
            var usage: [String: Int] = [:], rejects: [String: Int] = [:]
            for _ in 0..<Int(try d.int()) { let k = try d.utf(); usage[k] = Int(try d.int()) }
            for _ in 0..<Int(try d.int()) { let k = try d.utf(); rejects[k] = Int(try d.int()) }
            var replay: [(String, [Float])] = []
            for _ in 0..<Int(try d.int()) {
                let k = try d.utf(); let n = Int(try d.int())
                var x: [Float] = []
                for _ in 0..<n { x.append(try d.float()) }
                replay.append((k, x))
            }
            return ModeModel(mode, net, usage: usage, rejects: rejects, replay: replay, trained: trained, catalogVersion: version, baseVersion: base, format: format)
        } catch { return nil }
    }
}

/// v29 modların modelleri (dosyada saklanır; [dir] nil ise yalnız bellekte).
final class ShapeModeModels {
    private let dir: URL?
    private var models: [ShapeMode: ModeModel] = [:]
    private var keyCache: [ShapeMode: (Int, [String])] = [:]
    private let lock = NSRecursiveLock()

    init(_ dir: URL?) { self.dir = dir }

    private func file(_ mode: ShapeMode) -> URL? { dir?.appendingPathComponent("shape_mode_\(mode.rawValue.lowercased()).bin") }

    /// Modun aranacak şablonları (sıralı).
    func keyList(_ mode: ShapeMode, _ index: ShapeIndex) -> [String] {
        lock.withLock {
            if let c = keyCache[mode], c.0 == index.templateCount { return c.1 }
            var seen = Set<String>()
            let keys = ModeCatalog.keys(mode, index.metas).filter { seen.insert($0).inserted }
            keyCache[mode] = (index.templateCount, keys)
            return keys
        }
    }

    func keys(_ mode: ShapeMode, _ index: ShapeIndex) -> Set<String> { Set(keyList(mode, index)) }

    func model(_ mode: ShapeMode, _ index: ShapeIndex) -> ModeModel {
        if let m = lock.withLock({ models[mode] }) { return m }
        let keys = keyList(mode, index)
        let loaded = load(mode)
        let (modeBase, baseVersion) = ShapeNet.baseFor(mode)
        let base = modeBase ?? ShapeNet.bundled
        let model: ModeModel
        if let loaded, loaded.net.keys == keys, loaded.catalogVersion == ShapeTemplates.VERSION, loaded.baseVersion == baseVersion { model = loaded }
        else {
            let newBase = loaded == nil || loaded!.baseVersion != baseVersion
            let net = ModeNet.initNet(keys, base, newBase ? nil : loaded?.net)
            let keySet = Set(keys)
            let m = ModeModel(mode, net, usage: loaded?.usage ?? [:], rejects: loaded?.rejects ?? [:],
                              replay: (loaded?.replay ?? []).filter { keySet.contains($0.0) }, trained: loaded?.trained ?? 0, baseVersion: baseVersion)
            let learnt: Set<String> = newBase ? [] : Set(loaded?.net.keys ?? [])
            let bundledKeys = Set(base?.keys ?? [])
            let unknown = keys.filter { !learnt.contains($0) && !bundledKeys.contains($0) }.compactMap { ShapeTemplates.byKey($0) }
            let known = keys.filter { bundledKeys.contains($0) || learnt.contains($0) }.compactMap { ShapeTemplates.byKey($0) }
            m.bootstrap(unknown, known)
            if newBase { m.relearn() }
            save(m)
            model = m
        }
        return lock.withLock {
            if let m = models[mode] { return m }
            models[mode] = model
            return model
        }
    }

    private func load(_ mode: ShapeMode) -> ModeModel? {
        guard let f = file(mode), let data = try? Data(contentsOf: f), let m = ModeModel.decode(data) else { return nil }
        if Self.isStale(m) { try? FileManager.default.removeItem(at: f); return nil }
        return m
    }

    func scope(_ mode: ShapeMode, _ index: ShapeIndex) -> ShapeScope { model(mode, index).scope(keys(mode, index)) }

    func scopeIfReady(_ mode: ShapeMode, _ index: ShapeIndex) -> ShapeScope {
        if let m = lock.withLock({ models[mode] }) { return m.scope(keys(mode, index)) }
        return ShapeScope(mode, keys(mode, index), nil, AdvancedWeights.net, [:], [:])
    }

    func isReady(_ mode: ShapeMode) -> Bool { lock.withLock { models[mode] != nil } }

    func invalidate() { lock.withLock { models.removeAll() } }

    func accepted(_ mode: ShapeMode, _ index: ShapeIndex, _ key: String, _ drawn: [[P]]) {
        let m = model(mode, index)
        m.accept(key, ShapeComplexity.usesNet(drawn.count) ? ShapeNet.inputOf(drawn) : nil)
        save(m)
    }

    func rejected(_ mode: ShapeMode, _ index: ShapeIndex, _ keys: [String], _ drawn: [[P]]) {
        if keys.isEmpty { return }
        let m = model(mode, index)
        m.reject(keys, ShapeComplexity.usesNet(drawn.count) ? ShapeNet.inputOf(drawn) : nil)
        save(m)
    }

    func reset(_ mode: ShapeMode) {
        _ = lock.withLock { models.removeValue(forKey: mode) }
        if let f = file(mode) { try? FileManager.default.removeItem(at: f) }
    }

    func info(_ mode: ShapeMode, _ index: ShapeIndex?) -> ModeInfo {
        let templates = index.map { keys(mode, $0).count } ?? 0
        if let m = lock.withLock({ models[mode] }) { return m.info(templates) }
        if let f = file(mode), let data = try? Data(contentsOf: f), let m = ModeModel.decode(data), !Self.isStale(m) { return m.info(templates, ready: false) }
        return ModeInfo(mode: mode, templates: templates, accepted: 0, rejected: 0, trainedSamples: 0, ready: false)
    }

    static func isStale(_ m: ModeModel) -> Bool {
        let reset = ShapeNet.resetBelow(m.mode)
        return reset > 0 && (m.baseVersion < reset || m.format < 3)
    }

    private func save(_ m: ModeModel) {
        guard let f = file(m.mode) else { return }
        try? FileManager.default.createDirectory(at: f.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? m.encode().write(to: f, options: .atomic)
    }
}

func relativeProb(_ probs: [Float], _ idx: Int, _ maxP: Float) -> Float {
    idx < 0 ? 0 : (probs[idx] / max(maxP, 1e-6)).coerceIn(0, 1)
}
