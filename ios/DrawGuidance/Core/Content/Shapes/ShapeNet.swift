import Foundation

/// v29 tahminin yapıldığı mod.
enum ShapeMode: String, CaseIterable { case ART, NOTES, COMIC, KIDS
    var ordinal: Int { Self.allCases.firstIndex(of: self)! }
}

/// Şablon olasılıkları veren sınıflandırıcı.
protocol ShapeClassifier: AnyObject {
    var keys: [String] { get }
    func indexOf(_ key: String) -> Int
    func probs(_ x: [Float]) -> [Float]
}

/// v27 küçük sinir ağı (int8, Android `ShapeNet` ile birebir).
final class ShapeNet: ShapeClassifier {
    let keys: [String]
    let inDim: Int
    let hidden: Int
    private let w1: [Int8], s1: [Float], b1: [Float]
    private let w2: [Int8], s2: [Float], b2: [Float]
    private let keyIndex: [String: Int]
    var outDim: Int { keys.count }

    init(_ keys: [String], _ inDim: Int, _ hidden: Int, _ w1: [Int8], _ s1: [Float], _ b1: [Float], _ w2: [Int8], _ s2: [Float], _ b2: [Float]) {
        self.keys = keys; self.inDim = inDim; self.hidden = hidden
        self.w1 = w1; self.s1 = s1; self.b1 = b1; self.w2 = w2; self.s2 = s2; self.b2 = b2
        var m: [String: Int] = [:]
        for (i, k) in keys.enumerated() { m[k] = i }
        keyIndex = m
    }

    func indexOf(_ key: String) -> Int { keyIndex[key] ?? -1 }

    func hiddenLayer() -> ([Float], [Float]) {
        ((0..<(hidden * inDim)).map { Float(w1[$0]) * s1[$0 / inDim] }, b1)
    }
    func outputRow(_ k: Int) -> [Float] { (0..<hidden).map { Float(w2[k * hidden + $0]) * s2[k] } }
    func outputBias(_ k: Int) -> Float { b2[k] }

    func probs(_ x: [Float]) -> [Float] {
        var h = [Float](repeating: 0, count: hidden)
        w1.withUnsafeBufferPointer { w in
            for j in 0..<hidden {
                let o = j * inDim
                var acc: Float = 0
                for i in 0..<inDim { acc += Float(w[o + i]) * x[i] }
                let v = acc * s1[j] + b1[j]
                h[j] = v > 0 ? v : 0
            }
        }
        var out = [Float](repeating: 0, count: outDim)
        var mx = -Float.infinity
        w2.withUnsafeBufferPointer { w in
            for k in 0..<outDim {
                var acc: Float = 0
                let o = k * hidden
                for j in 0..<hidden { acc += Float(w[o + j]) * h[j] }
                let v = acc * s2[k] + b2[k]
                out[k] = v
                if v > mx { mx = v }
            }
        }
        var sum: Float = 0
        for k in 0..<outDim { out[k] = Float(exp(Double(out[k] - mx))); sum += out[k] }
        for k in 0..<outDim { out[k] /= sum }
        return out
    }

    static let RESOURCE = "shape_net.bin"
    static let IN_DIM = 83
    private static let MAGIC: Int32 = 0x5348_4E54
    static let MODE_NET_VERSION = 1
    static let V38 = 38
    static func modeNetVersion(_ mode: ShapeMode) -> Int { V38 }
    static func resetBelow(_ mode: ShapeMode) -> Int { mode == .NOTES ? 37 : 0 }

    private static let lock = NSLock()
    private static var modeNets: [ShapeMode: ShapeNet?] = [:]
    private static var remoteNets: [ShapeMode: (Int, ShapeNet)] = [:]

    static func installRemote(_ mode: ShapeMode, _ version: Int, _ bytes: Data) -> Bool {
        if version <= modeNetVersion(mode) { lock.withLock { remoteNets[mode] = nil }; return true }
        guard let net = decode(bytes) else { return false }
        lock.withLock { remoteNets[mode] = (version, net) }
        return true
    }

    static func baseFor(_ mode: ShapeMode) -> (ShapeNet?, Int) {
        if let r = lock.withLock({ remoteNets[mode] }) { return (r.1, r.0) }
        let b = bundledFor(mode)
        return (b, b != nil ? modeNetVersion(mode) : 0)
    }

    static func bundledFor(_ mode: ShapeMode) -> ShapeNet? {
        if let n = lock.withLock({ modeNets[mode] }) { return n }
        let net = BundledResources.data("mode_\(mode.rawValue.lowercased()).bin").flatMap(decode)
        lock.withLock { modeNets[mode] = .some(net) }
        return net
    }

    static let bundled: ShapeNet? = BundledResources.data(RESOURCE).flatMap(decode)

    static func decode(_ bytes: Data) -> ShapeNet? {
        var d = JavaDataReader(bytes)
        do {
            guard try d.int() == MAGIC, try d.int() == 1 else { return nil }
            let inDim = Int(try d.int()), hidden = Int(try d.int()), k = Int(try d.int())
            var keys: [String] = []
            for _ in 0..<k { keys.append(try d.utf()) }
            let w1 = try d.bytes(inDim * hidden)
            var s1: [Float] = [], b1: [Float] = []
            for _ in 0..<hidden { s1.append(try d.float()) }
            for _ in 0..<hidden { b1.append(try d.float()) }
            let w2 = try d.bytes(hidden * k)
            var s2: [Float] = [], b2: [Float] = []
            for _ in 0..<k { s2.append(try d.float()) }
            for _ in 0..<k { b2.append(try d.float()) }
            return ShapeNet(keys, inDim, hidden, w1, s1, b1, w2, s2, b2)
        } catch { return nil }
    }

    static func input(_ polys: [Poly], _ coarse: [Float], _ aspect: Float) -> [Float] {
        var x = [Float](repeating: 0, count: IN_DIM)
        for i in 0..<min(coarse.count, 80) { x[i] = coarse[i] }
        x[80] = aspect
        x[81] = (Float(log(Double(Float(max(polys.count, 1))))) / Float(log(Double(Float(12))))).coerceIn(0, 1.2)
        x[82] = polys.isEmpty ? 0 : Float(polys.filter { ShapeSignatures.isClosed($0) }.count) / Float(polys.count)
        return x
    }

    static func inputOf(_ strokes: [[P]]) -> [Float]? {
        let clean = strokes.filter { !$0.isEmpty }
        if clean.isEmpty { return nil }
        let norm = ShapeSignatures.normalize(clean)
        let raw = ShapeSignatures.polys(norm)
        let polys = ShapeSignatures.collapseSmall(raw)
        let f = ShapeFeatures.extract(polys)
        return input(polys, ShapeSignatures.pool(f), ShapeFeatures.aspect(raw))
    }
}

/// v27 el çizimi benzetimi (Android `ShapeAugment`, `java.util.Random` ile aynı dizi): mod ağlarının cihazda kurulması.
enum ShapeAugment {
    static func atLeast(_ strokes: [[P]], _ min0: Int, _ rnd: JRandom) -> [[P]] {
        var out = strokes.filter { !$0.isEmpty }
        var guardN = 0
        while out.count < min0 && guardN < 4 * min0 {
            guardN += 1
            var i = -1
            var bestL = -Float.infinity
            for j in out.indices where out[j].count >= 2 { let l = lengthOf(out[j]); if i < 0 || l > bestL { i = j; bestL = l } }
            if i < 0 { break }
            let s = out[i]
            let len = lengthOf(s)
            if len <= 0 { break }
            let dense = s.count >= 12 ? s : TemplateFit.densify(s, len / 24)
            if dense.count < 4 { break }
            let cut = ((0.3 + 0.4 * rnd.nextFloat()) * Float(dense.count - 1)).toInt().coerceIn(1, dense.count - 2)
            let gap = rnd.nextFloat() < 0.35 && dense.count > 12 ? 1 : 0
            out[i] = Array(dense[0...cut])
            out.insert(Array(dense[(cut + gap)...]), at: i + 1)
        }
        return out
    }

    private static func lengthOf(_ s: [P]) -> Float {
        var l: Float = 0
        if s.count > 1 { for i in 1..<s.count { l += kHypot(s[i].x - s[i - 1].x, s[i].y - s[i - 1].y) } }
        return l
    }

    static func manyElements(_ strokes: [[P]], _ rnd: JRandom, min: Int = SuggestRules.SHAPE_MIN, part: Bool = false, amount: Float = 1, strong: Bool = false) -> [[P]] {
        var src = strokes
        if part && src.count > min { src = Array(src.prefix(min + rnd.nextInt(src.count - min))) }
        let target: Int
        if src.count < min { target = min + rnd.nextInt(3) }
        else { target = src.count + (rnd.nextFloat() < 0.2 ? 1 + rnd.nextInt(2) : 0) }
        let drawn = handDrawn(atLeast(src, target, rnd), rnd, amount: amount, strong: strong)
        return drawn.count >= min ? drawn : atLeast(drawn, min, rnd)
    }

    static func handDrawn(_ strokes: [[P]], _ rnd: JRandom, amount: Float = 1, strong: Bool = false) -> [[P]] {
        let size = 200 + rnd.nextFloat() * 200
        let ox = 100 + rnd.nextFloat() * 300
        let oy = 100 + rnd.nextFloat() * 300
        let rot = ((rnd.nextFloat() - 0.5) * (strong ? 22 : 10) * amount) * Float.pi / 180
        let sx = 1 + (rnd.nextFloat() - 0.5) * (strong ? 0.5 : 0.24) * amount
        let c = Float(cos(Double(rot))), s = Float(sin(Double(rot)))
        func map(_ p: P, _ dx: Float, _ dy: Float) -> P {
            let x = (p.x - 0.5 + dx) * size * sx, y = (p.y - 0.5 + dy) * size
            return P(ox + c * x - s * y, oy + s * x + c * y)
        }
        var src = strokes
        if strong && strokes.count >= 3 && rnd.nextFloat() < 0.4 {
            // Kotlin filterIndexed: her eleman için yeni rastgele sayı
            var kept: [[P]] = []
            for (i, st) in strokes.enumerated() where i != rnd.nextInt(strokes.count) { kept.append(st) }
            src = kept
        }
        var out: [[P]] = []
        for st in src {
            let ext = max(st.map(\.x).max()! - st.map(\.x).min()!, st.map(\.y).max()! - st.map(\.y).min()!)
            let dx: Float = strong ? (rnd.nextFloat() - 0.5) * 0.06 : 0
            let dy: Float = strong ? (rnd.nextFloat() - 0.5) * 0.06 : 0
            if src.count > 1 && ext < 0.12 && rnd.nextBoolean() {
                var sxs = 0.0, sys = 0.0
                for p in st { sxs += Double(p.x) }
                for p in st { sys += Double(p.y) }
                let cc = map(P(Float(sxs / Double(st.count)), Float(sys / Double(st.count))), dx, dy)
                out.append([cc, P(cc.x + 1.5, cc.y + 1), P(cc.x + 0.5, cc.y + 2)])
                continue
            }
            var pts = TemplateFit.densify(st.map { map($0, dx, dy) }, 3)
            let closed = st.count > 3 && kHypot(st[0].x - st[st.count - 1].x, st[0].y - st[st.count - 1].y) < 0.02
            if closed && pts.count > 8 {
                let k = rnd.nextInt(pts.count - 1)
                pts = Array(pts[k..<(pts.count - 1)]) + Array(pts[0...k])
                let cut = (rnd.nextFloat() * 0.03 * Float(pts.count)).toInt()
                if cut > 0 && rnd.nextBoolean() { pts = Array(pts.dropLast(cut)) }
            }
            let f1 = rnd.nextFloat() * 3 + 1, f2 = rnd.nextFloat() * 7 + 3
            let ph1 = rnd.nextFloat() * 6.28, ph2 = rnd.nextFloat() * 6.28
            let amp = size * 0.012 * amount * (strong ? 2 : 1)
            var total: Float = 0
            var lens = [Float](repeating: 0, count: pts.count)
            if pts.count > 1 { for i in 1..<pts.count { total += kHypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y); lens[i] = total } }
            var wob: [P] = []
            for (i, p) in pts.enumerated() {
                let t: Float = total > 0 ? lens[i] / total : 0
                let nx = amp * kSin(f1 * 6.28 * t + ph1) + amp * 0.5 * kSin(f2 * 6.28 * t + ph2) + (rnd.nextFloat() - 0.5) * 0.8
                let ny = amp * kCos(f1 * 6.28 * t + ph2) + amp * 0.5 * kCos(f2 * 6.28 * t + ph1) + (rnd.nextFloat() - 0.5) * 0.8
                wob.append(P(p.x + nx, p.y + ny))
            }
            out.append(rnd.nextBoolean() ? wob.reversed() : wob)
        }
        return rnd.shuffled(out)
    }
}
