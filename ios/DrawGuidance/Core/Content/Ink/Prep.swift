import Foundation

/// Giriş temizliği, kaba kümeler (karakter yüksekliği tahmini) ve "yazı mı?" sezgileri (Android `Prep`).
enum Prep {
    static func polys(_ strokes: [InkStroke]) -> [(Int, Poly)] {
        var out: [(Int, Poly)] = []
        for (i, s) in strokes.enumerated() {
            var xs: [Float] = [], ys: [Float] = []
            for p in s.points {
                if !p.x.isFinite || !p.y.isFinite { continue }
                if let lx = xs.last, lx == p.x && ys.last! == p.y { continue }
                xs.append(p.x); ys.append(p.y)
            }
            if !xs.isEmpty { out.append((i, Poly(xs, ys))) }
        }
        return out
    }

    struct Cluster {
        let members: [Int]
        let box: Box
    }

    /// Yatayda belirgin örtüşen (küçük olmayan) çizgilerin birleşimi — kabaca karakterler.
    static func clusters(_ ps: [Poly], _ tinyFrac: Float = 0.2) -> [Cluster] {
        if ps.isEmpty { return [] }
        let mx = max(ps.map(\.extent).max()!, 1e-4)
        var big = ps.indices.filter { ps[$0].extent >= tinyFrac * mx }
        if big.isEmpty { big = Array(ps.indices) }
        var parent = Array(ps.indices)
        func find(_ i: Int) -> Int { var r = i; while parent[r] != r { r = parent[r] }; parent[i] = r; return r }
        let floor = 0.12 * mx
        for a in big.indices {
            for b in (a + 1)..<max(big.count, a + 1) {
                let p = ps[big[a]], q = ps[big[b]]
                let wp = max(p.w, floor), wq = max(q.w, floor)
                let ovl = min(p.cx + wp / 2, q.cx + wq / 2) - max(p.cx - wp / 2, q.cx - wq / 2)
                if ovl >= 0.3 * min(wp, wq) { let ra = find(big[a]); parent[ra] = find(big[b]) }
            }
        }
        // groupBy: ilk görülme sırası
        var keys: [Int] = []
        var groups: [Int: [Int]] = [:]
        for i in big {
            let k = find(i)
            if groups[k] == nil { keys.append(k); groups[k] = [] }
            groups[k]!.append(i)
        }
        let cl = keys.map { k -> Cluster in let m = groups[k]!; return Cluster(members: m, box: Box.of(m.map { ps[$0] })) }
        return cl.enumerated().sorted { $0.element.box.cx != $1.element.box.cx ? $0.element.box.cx < $1.element.box.cx : $0.offset < $1.offset }.map(\.element)
    }

    /// Tipik karakter yüksekliği (büyük harf/rakam boyu civarı).
    static func capHeight(_ ps: [Poly]) -> Float {
        let cl = clusters(ps)
        if cl.isEmpty { return 1 }
        let sizes = cl.map { max($0.box.h, 0.7 * $0.box.w) }.sorted()
        let mx = sizes[sizes.count - 1]
        let tall = sizes.filter { $0 >= 0.6 * mx }
        return max(tall[tall.count / 2], 1e-3)
    }

    static func isFrame(_ ps: [Poly], _ b: Box) -> Bool {
        let size = max(b.w, b.h)
        if b.w < 0.25 * size || b.h < 0.25 * size { return false }
        let tol = 0.07 * size
        let bins = 20
        var top = [Bool](repeating: false, count: bins), bot = top, lef = top, rig = top
        for p in ps {
            let r = Geo.resample(p, size / 80)
            for i in 0..<r.n {
                let x = r.x[i], y = r.y[i]
                let bx = min(max(Int((x - b.minX) / max(b.w, 1e-4) * Float(bins)), 0), bins - 1)
                let by = min(max(Int((y - b.minY) / max(b.h, 1e-4) * Float(bins)), 0), bins - 1)
                if y - b.minY < tol { top[bx] = true }
                if b.maxY - y < tol { bot[bx] = true }
                if x - b.minX < tol { lef[by] = true }
                if b.maxX - x < tol { rig[by] = true }
            }
        }
        func cov(_ a: [Bool]) -> Float { Float(a.filter { $0 }.count) / Float(bins) }
        return cov(top) >= 0.75 && cov(bot) >= 0.75 && cov(lef) >= 0.75 && cov(rig) >= 0.75
    }

    /// v37: çizgilerin en az %80'i aynı yönde düz çizgi: tarama.
    static func isHatching(_ ps: [Poly]) -> Bool {
        if ps.count < 8 { return false }
        var bins = [Int](repeating: 0, count: 4)
        var straight = 0
        let fpi = Float(Double.pi)
        for p in ps {
            let dx = p.x[p.n - 1] - p.x[0], dy = p.y[p.n - 1] - p.y[0]
            let chord = kHypot(dx, dy)
            if chord <= 0 || p.length > 1.15 * chord { continue }
            straight += 1
            var a = kAtan2(dy, dx)
            if a < 0 { a += fpi }
            bins[Int(a / (fpi / 4) + 0.5) % 4] += 1
        }
        return Float(straight) >= 0.8 * Float(ps.count) && Float(bins.max()!) >= 0.8 * Float(ps.count)
    }

    static func looksLikeWriting(_ ps: [Poly], _ script: InkScript = .LATIN) -> Bool {
        let cjk = script == .JAPANESE || script == .KOREAN
        if ps.isEmpty || ps.count > (cjk ? Handwriting.MAX_STROKES_CJK : Handwriting.MAX_STROKES) { return false }
        let all = Box.of(ps)
        if max(all.w, all.h) <= 0 { return false }
        let hc = capHeight(ps)
        for p in ps {
            if p.extent < 0.25 * hc { continue }
            let t = Geo.turning(p, max(p.extent, 0.5 * hc) / 28)
            if t.absTurns > 4.6 || (t.absTurns > 1.8 && Geo.winding(p) > 1.8) { return false }
            if t.absTurns > 2.6 && Geo.selfIntersections(p, max(p.extent, 0.5 * hc) / 20) >= 3 { return false }
            if t.sharpTurns >= 6 || t.reversals >= 5 { return false }
        }
        if ps.count == 1 {
            let p = ps[0]
            let chord = kHypot(p.x[p.n - 1] - p.x[0], p.y[p.n - 1] - p.y[0])
            if p.w > 4 * p.h && p.length < 1.15 * chord { return false }
        }
        if (!cjk || ps.count == 1) && isFrame(ps, all) { return false }
        for c in clusters(ps) where c.members.count > (cjk ? 24 : 7) { return false }
        if cjk && ps.count > Handwriting.MAX_STROKES && isHatching(ps) { return false }
        if all.h > 3.2 * hc && ps.count > 1 { return false }
        return true
    }
}

/// v37 yazı ağının girdisi (tek karakterin çizgileri; Android `TextFeatures`): 12×12 ızgarada 4 doğrultu kanalı,
/// 6×6 uç/nokta kanalı ve 4 genel değer; 616 boyut.
enum TextFeatures {
    static let G = 12
    static let E = 6
    static let DIR = 4 * G * G
    static let DIM = DIR + E * E + 4
    private static let STEP: Float = 0.02
    private static let END_W: Float = 0.3
    private static let DOT_W: Float = 1

    static func extract(_ strokes: [Poly]) -> [Float] {
        var f = [Float](repeating: 0, count: DIM)
        if strokes.isEmpty { return f }
        let b = Box.of(strokes)
        let s = max(max(b.w, b.h), 1e-4)
        let cx = b.cx, cy = b.cy
        var total: Float = 0
        var dots = 0
        for st in strokes {
            let p = Poly(st.x.map { ($0 - cx) / s + 0.5 }, st.y.map { ($0 - cy) / s + 0.5 })
            if p.n < 2 || p.length < STEP {
                dots += 1
                splat(&f, DIR, E, p.cx, p.cy, DOT_W)
                continue
            }
            let r = Geo.resample(Geo.smooth(Geo.resample(p, STEP * 0.5), 1), STEP)
            for i in 0..<(r.n - 1) {
                let dx = r.x[i + 1] - r.x[i], dy = r.y[i + 1] - r.y[i]
                let len = kHypot(dx, dy)
                if len < 1e-6 { continue }
                var a = atan2(Double(dy), Double(dx))
                if a < 0 { a += Double.pi }
                let bin = a / (Double.pi / 4)
                let b0 = Int(bin.rounded(.down))
                let fr = Float(bin - Double(b0))
                let mx = (r.x[i] + r.x[i + 1]) * 0.5, my = (r.y[i] + r.y[i + 1]) * 0.5
                splat(&f, (b0 % 4) * G * G, G, mx, my, len * (1 - fr))
                splat(&f, ((b0 + 1) % 4) * G * G, G, mx, my, len * fr)
            }
            total += r.length
            let gap = kHypot(r.x[r.n - 1] - r.x[0], r.y[r.n - 1] - r.y[0])
            let ext = max(max(r.maxX - r.minX, r.maxY - r.minY), 1e-4)
            if gap > 0.15 * ext || r.length < 1.5 * ext {
                splat(&f, DIR, E, r.x[0], r.y[0], END_W)
                splat(&f, DIR, E, r.x[r.n - 1], r.y[r.n - 1], END_W)
            }
        }
        for ch in 0..<4 { blur(&f, ch * G * G, G) }
        blur(&f, DIR, E)
        normalize(&f, 0, DIR, 1)
        normalize(&f, DIR, E * E, 0.5)
        let o = DIR + E * E
        f[o] = Float(atan2(Double(b.w), Double(b.h)) / (Double.pi / 2))
        f[o + 1] = min(max(Float(log(Double(Float(strokes.count)))) / Float(log(16.0)), 0), 1.5)
        f[o + 2] = min(total / 8, 1.5)
        f[o + 3] = Float(dots) / Float(strokes.count)
        return f
    }

    private static func splat(_ f: inout [Float], _ base: Int, _ g: Int, _ u: Float, _ v: Float, _ w: Float) {
        let gx = min(max(u * Float(g) - 0.5, 0), Float(g) - 1)
        let gy = min(max(v * Float(g) - 0.5, 0), Float(g) - 1)
        let x0 = min(Int(gx.rounded(.down)), g - 2), y0 = min(Int(gy.rounded(.down)), g - 2)
        let fx = gx - Float(x0), fy = gy - Float(y0)
        f[base + y0 * g + x0] += w * (1 - fx) * (1 - fy)
        f[base + y0 * g + x0 + 1] += w * fx * (1 - fy)
        f[base + (y0 + 1) * g + x0] += w * (1 - fx) * fy
        f[base + (y0 + 1) * g + x0 + 1] += w * fx * fy
    }

    private static func blur(_ f: inout [Float], _ base: Int, _ g: Int) {
        var tmp = [Float](repeating: 0, count: g * g)
        for y in 0..<g {
            for x in 0..<g {
                var s = f[base + y * g + x] * 0.5
                if x > 0 { s += f[base + y * g + x - 1] * 0.25 }
                if x < g - 1 { s += f[base + y * g + x + 1] * 0.25 }
                tmp[y * g + x] = s
            }
        }
        for y in 0..<g {
            for x in 0..<g {
                var s = tmp[y * g + x] * 0.5
                if y > 0 { s += tmp[(y - 1) * g + x] * 0.25 }
                if y < g - 1 { s += tmp[(y + 1) * g + x] * 0.25 }
                f[base + y * g + x] = s
            }
        }
    }

    private static func normalize(_ f: inout [Float], _ from: Int, _ len: Int, _ scale: Float) {
        var n: Float = 0
        for i in from..<(from + len) { n += f[i] * f[i] }
        n = n.squareRoot()
        if n > 0 { for i in from..<(from + len) { f[i] = f[i] / n * scale } }
    }
}
