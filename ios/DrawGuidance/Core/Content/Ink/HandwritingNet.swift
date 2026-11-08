import Foundation

/// v37 el yazısının yazı sistemi (Android `InkScript`): yazı ağı her biri için ayrı çıktı katmanı taşır.
enum InkScript: String, CaseIterable {
    case LATIN, CYRILLIC, ARABIC, JAPANESE, KOREAN

    var rtl: Bool { self == .ARABIC }
    var ordinal: Int { Self.allCases.firstIndex(of: self)! }

    /// Arayüz dilinden (BCP-47) ya da yazı sistemi adından yazı sistemi; bilinmiyorsa nil.
    static func fromHint(_ hint: String?) -> InkScript? {
        guard let hint, !hint.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        if let s = allCases.first(where: { $0.rawValue.caseInsensitiveCompare(hint) == .orderedSame }) { return s }
        let base = String(hint.lowercased().split(separator: "-", omittingEmptySubsequences: false)[0]
            .split(separator: "_", omittingEmptySubsequences: false)[0])
        switch base {
        case "tr", "en", "de", "fr", "es", "it", "pt", "nl", "az": return .LATIN
        case "ru", "uk", "be", "bg", "sr", "kk": return .CYRILLIC
        case "ar", "fa", "ur": return .ARABIC
        case "ja": return .JAPANESE
        case "ko": return .KOREAN
        default: return nil
        }
    }
}

/// v37 not alma modunun yazı düzeltme ağı (Android `HandwritingNet` ile aynı dosya biçimi ve çıkarım): girdi
/// TextFeatures (616) → h1 ReLU → h2 ReLU → adlı çıktı başları; ağırlıklar satır başına ölçekli int8.
final class HandwritingNet {
    let inDim: Int, h1: Int, h2: Int
    private let w1: [Int8], s1: [Float], b1: [Float]
    private let w2: [Int8], s2: [Float], b2: [Float]
    /// Adlı başlar (yazılış sırasıyla).
    let headNames: [String]
    let heads: [String: Head]

    final class Head {
        let outputs: [[String]]
        let w: [Int8], s: [Float], b: [Float]
        var size: Int { outputs.count }
        /// "Karakter değil" sınıfı (yoksa -1).
        let junk: Int
        private let index: [String: Int]
        init(_ outputs: [[String]], _ w: [Int8], _ s: [Float], _ b: [Float]) {
            self.outputs = outputs; self.w = w; self.s = s; self.b = b
            junk = outputs.firstIndex { $0.isEmpty } ?? -1
            var m: [String: Int] = [:]
            for (i, o) in outputs.enumerated() { for t in o where m[t] == nil { m[t] = i } }
            index = m
        }
        func classOf(_ text: String) -> Int { index[text] ?? -1 }
    }

    init(_ inDim: Int, _ h1: Int, _ h2: Int, _ w1: [Int8], _ s1: [Float], _ b1: [Float], _ w2: [Int8], _ s2: [Float], _ b2: [Float],
         _ names: [String], _ heads: [String: Head]) {
        self.inDim = inDim; self.h1 = h1; self.h2 = h2
        self.w1 = w1; self.s1 = s1; self.b1 = b1; self.w2 = w2; self.s2 = s2; self.b2 = b2
        headNames = names; self.heads = heads
    }

    /// Ortak gövdenin çıktısı (ikinci gizli katman).
    func hidden(_ x: [Float]) -> [Float] {
        let a = Self.layer(x, inDim, h1, w1, s1, b1, true)
        return Self.layer(a, h1, h2, w2, s2, b2, true)
    }

    func head(_ name: String) -> Head? { heads[name] }

    func probs(_ h: [Float], _ name: String) -> [Float]? {
        guard let hd = heads[name] else { return nil }
        return Self.softmax(Self.layer(h, h2, hd.size, hd.w, hd.s, hd.b, false))
    }

    func scriptProbs(_ h: [Float]) -> [Float] {
        probs(h, Self.SCRIPT) ?? [Float](repeating: 1 / Float(InkScript.allCases.count), count: InkScript.allCases.count)
    }

    func classify(_ strokes: [Poly], _ name: String) -> [Float]? { probs(hidden(TextFeatures.extract(strokes)), name) }

    func logits(_ h: [Float], _ name: String) -> [Float]? {
        guard let hd = heads[name] else { return nil }
        return Self.layer(h, h2, hd.size, hd.w, hd.s, hd.b, false)
    }

    var hasGate: Bool { heads[Self.WRITING] != nil && heads[Self.WRITING_MAX] != nil }

    /// v38 "yazı mı" kapısı: birbirine yakın elemanların yazı olma olasılığı (kapı yoksa nil).
    func writingProb(_ strokes: [Poly]) -> Float? {
        if !hasGate || strokes.isEmpty { return nil }
        guard let g = gateInput(strokes), let a = logits(g[0], Self.WRITING), let b = logits(g[1], Self.WRITING_MAX) else { return nil }
        let c = logits(g[2], Self.WRITING_ALL)
        var z = [Float](repeating: 0, count: a.count)
        for i in 0..<a.count { let ci: Float = c?[i] ?? 0; z[i] = a[i] + b[i] + ci }
        return Self.softmax(z)[Self.GATE_TEXT]
    }

    func gateInput(_ strokes: [Poly]) -> [[Float]]? {
        let clusters = Prep.clusters(strokes)
        if clusters.isEmpty { return nil }
        var mean = [Float](repeating: 0, count: h2), mx = mean
        for c in clusters.prefix(Self.GATE_MAX_CLUSTERS) {
            let h = hidden(TextFeatures.extract(c.members.map { strokes[$0] }))
            for i in 0..<h2 { mean[i] += h[i]; if h[i] > mx[i] { mx[i] = h[i] } }
        }
        let n = Float(min(clusters.count, Self.GATE_MAX_CLUSTERS))
        for i in 0..<h2 { mean[i] /= n }
        return [mean, mx, hidden(TextFeatures.extract(strokes))]
    }

    static let RESOURCE = "notes_text.bin"
    static let VERSION = 2
    static let CATALOG_MODE = "NOTES_TEXT"
    static let FORMAT = "inknet-int8-v1"
    static let SCRIPT = "SCRIPT"
    static let KOREAN_L = "KOREAN_L"
    static let KOREAN_V = "KOREAN_V"
    static let KOREAN_T = "KOREAN_T"
    static let WRITING = "WRITING"
    static let WRITING_MAX = "WRITING_MAX"
    static let WRITING_ALL = "WRITING_ALL"
    static let GATE_SHAPE = 0
    static let GATE_TEXT = 1
    static let GATE_MAX_CLUSTERS = 24
    static let NONE = "-"
    private static let MAGIC: Int32 = 0x494E_4B4E

    /// Uygulamayla gelen ağ (yoksa ya da bozuksa nil: yazı tanıma yalnız şablonlarla çalışır).
    static let bundled: HandwritingNet? = BundledResources.data(RESOURCE).flatMap(decode)

    private static let lock = NSLock()
    private static var remote: (Int, HandwritingNet)?

    /// Sunucudan inen yazı ağını kurar (bozuksa false). Aynı ya da eski sürüm gelirse sunucu ağı kaldırılır.
    @discardableResult
    static func installRemote(_ version: Int, _ bytes: Data) -> Bool {
        if version <= VERSION { lock.withLock { remote = nil }; return true }
        guard let net = decode(bytes) else { return false }
        lock.withLock { remote = (version, net) }
        return true
    }

    static func current() -> (HandwritingNet?, Int) {
        if let r = lock.withLock({ remote }) { return (r.1, r.0) }
        let b = bundled
        return (b, b != nil ? VERSION : 0)
    }

    /// Etkin ağ.
    static var active: HandwritingNet? { lock.withLock { remote?.1 } ?? bundled }

    static func decode(_ bytes: Data) -> HandwritingNet? {
        var d = JavaDataReader(bytes)
        do {
            if try d.int() != MAGIC || d.int() != 1 { return nil }
            let inDim = Int(try d.int()), h1 = Int(try d.int()), h2 = Int(try d.int())
            if inDim != TextFeatures.DIM || !(1...4096).contains(h1) || !(1...4096).contains(h2) { return nil }
            let n = Int(try d.int())
            if !(1...64).contains(n) { return nil }
            var meta: [(String, [[String]])] = []
            for _ in 0..<n {
                let name = try d.utf()
                let k = Int(try d.int())
                if !(1...20000).contains(k) { return nil }
                var outs: [[String]] = []
                for _ in 0..<k {
                    let c = Int(try d.u8())
                    var o: [String] = []
                    for _ in 0..<c { o.append(try d.utf()) }
                    outs.append(o)
                }
                meta.append((name, outs))
            }
            let w1 = try d.bytes(inDim * h1)
            var s1: [Float] = [], b1: [Float] = []
            for _ in 0..<h1 { s1.append(try d.float()) }
            for _ in 0..<h1 { b1.append(try d.float()) }
            let w2 = try d.bytes(h1 * h2)
            var s2: [Float] = [], b2: [Float] = []
            for _ in 0..<h2 { s2.append(try d.float()) }
            for _ in 0..<h2 { b2.append(try d.float()) }
            var heads: [String: Head] = [:]
            var names: [String] = []
            for (name, outs) in meta {
                let k = outs.count
                let w = try d.bytes(k * h2)
                var s: [Float] = [], b: [Float] = []
                for _ in 0..<k { s.append(try d.float()) }
                for _ in 0..<k { b.append(try d.float()) }
                if heads[name] == nil { names.append(name) }
                heads[name] = Head(outs, w, s, b)
            }
            if heads[SCRIPT]?.size != InkScript.allCases.count { return nil }
            return HandwritingNet(inDim, h1, h2, w1, s1, b1, w2, s2, b2, names, heads)
        } catch { return nil }
    }

    /// int8 ağırlık × float girdi; satır ölçeği sonda (Android ile aynı toplama sırası).
    static func layer(_ x: [Float], _ n: Int, _ m: Int, _ w: [Int8], _ s: [Float], _ b: [Float], _ relu: Bool) -> [Float] {
        var out = [Float](repeating: 0, count: m)
        var nzCount = 0
        for i in 0..<n where x[i] != 0 { nzCount += 1 }
        return w.withUnsafeBufferPointer { w in
            if nzCount < n / 2 {
                var nz = [Int](repeating: 0, count: nzCount)
                var xv = [Float](repeating: 0, count: nzCount)
                var c = 0
                for i in 0..<n where x[i] != 0 { nz[c] = i; xv[c] = x[i]; c += 1 }
                for j in 0..<m {
                    let o = j * n
                    var a0: Float = 0, a1: Float = 0
                    var k = 0
                    while k + 1 < nzCount {
                        a0 += Float(w[o + nz[k]]) * xv[k]; a1 += Float(w[o + nz[k + 1]]) * xv[k + 1]
                        k += 2
                    }
                    if k < nzCount { a0 += Float(w[o + nz[k]]) * xv[k] }
                    let v = (a0 + a1) * s[j] + b[j]
                    out[j] = relu && v < 0 ? 0 : v
                }
                return out
            }
            for j in 0..<m {
                let o = j * n
                var a0: Float = 0, a1: Float = 0, a2: Float = 0, a3: Float = 0
                var i = 0
                while i + 3 < n {
                    a0 += Float(w[o + i]) * x[i]; a1 += Float(w[o + i + 1]) * x[i + 1]
                    a2 += Float(w[o + i + 2]) * x[i + 2]; a3 += Float(w[o + i + 3]) * x[i + 3]
                    i += 4
                }
                while i < n { a0 += Float(w[o + i]) * x[i]; i += 1 }
                let v = ((a0 + a1) + (a2 + a3)) * s[j] + b[j]
                out[j] = relu && v < 0 ? 0 : v
            }
            return out
        }
    }

    static func softmax(_ z0: [Float]) -> [Float] {
        var z = z0
        var mx = -Float.infinity
        for v in z where v > mx { mx = v }
        var sum: Float = 0
        for k in z.indices { z[k] = Float(exp(Double(z[k] - mx))); sum += z[k] }
        for k in z.indices { z[k] /= sum }
        return z
    }
}
