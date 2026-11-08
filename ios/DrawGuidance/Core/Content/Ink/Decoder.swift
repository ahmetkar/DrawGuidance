import Foundation

enum MarkType { case DOT, TICK, DASH, BREVE }

struct InkMark {
    let poly: Poly
    let type: MarkType
}

/// Satır ölçüsü varsayımı: base taban çizgisi y'si, h büyük harf yüksekliği.
struct InkLine {
    let base: Float
    let h: Float
    var cap: Float { base - h }
    func u(_ y: Float) -> Float { (y - cap) / h }
}

/// Aday karakter grubu (sıralı çizgilerde a..b).
final class InkGroup {
    let a: Int, b: Int
    var valid = false
    var members: [Int] = []
    var main: [Int] = []
    var above: [InkMark] = []
    var below: [InkMark] = []
    var marksOnly = false
    var box = Box(0, 0, 0, 0)
    var mergeCost: Float = 0
    var cls: [ClassCost] = []
    init(_ a: Int, _ b: Int) { self.a = a; self.b = b }
}

struct ClassCost {
    let c: Int
    let base: Float
    let shape: Float
}

/// Latin el yazısı çözücüsü (Android `Decoder` ile birebir): gruplar, sınıf maliyetleri, satır varsayımları, ışın
/// aramalı dinamik programlama, bağlam ve Türkçe dil modeli.
final class InkDecoder {
    static let K = 5
    static let BEAM = 8
    static let OPTS = 7
    static let SHAPE_K: Float = 14
    static let LAMBDA: Float = 0.6
    static let MARKS_ONLY_SHAPE: Float = 1.2
    static let Q0: Float = 4.0
    static let Q1: Float = 1.8
    private static let CTX: [[Float]] = [
        [0.0, 1.0, 1.2, 0.0, 0.2, 0.1],
        [1.0, 0.0, 1.4, 0.4, 0.1, 0.4],
        [1.0, 0.0, 0.35, 0.5, 0.1, 0.4],
        [0.0, 0.4, 0.8, 1.0, 0.8, 0.1],
        [0.2, 0.3, 0.3, 0.8, 0.7, 0.4],
        [0.1, 0.2, 0.4, 0.3, 0.2, 0.5],
    ]
    private static let START: [Float] = [0.0, 0.0, 0.0, 0.5, 1.5, 0.2]
    private static let END: [Float] = [0.0, 0.0, 0.0, 0.4, 0.0, 0.1]
    private static let VARS = "xyabnkt"
    static let LM_W: Float = 0.3
    static let LEX_BONUS: Float = 1.6
    static let LEX_RERANK = 16
    /// v37 yazı ağının (LATIN başı) ağırlığı.
    static var NET_W: Float = 0.6

    private let mode: InkMode
    private let lexicon: Bool
    private let textNet: HandwritingNet?
    private let netIdx: [[Int]]?
    private let orig: [Int]
    private var ps: [Poly]
    private let n: Int
    private var hc: Float = 1
    private var order: [Int] = []
    private var groups: [Int: InkGroup] = [:]
    private let classes = Glyphs.classes
    private let allowed: [Bool]
    private var touchCache: [Int: Bool] = [:]
    private let textId: [Int64]
    private var coarse: [Poly] = []

    private final class Node {
        let cost: Float, c: Int, g: InkGroup?, prev: Node?, key: Int64
        init(_ cost: Float, _ c: Int, _ g: InkGroup?, _ prev: Node?, _ key: Int64) { self.cost = cost; self.c = c; self.g = g; self.prev = prev; self.key = key }
    }

    private struct Path { let cost: Float; let line: InkLine; let node: Node }

    init(_ input: [(Int, Poly)], _ mode: InkMode, _ lexicon: Bool = true) {
        self.mode = mode; self.lexicon = lexicon
        let net = HandwritingNet.active
        textNet = Self.NET_W > 0 && net?.head(InkScript.LATIN.rawValue) != nil ? net : nil
        if let h = textNet?.head(InkScript.LATIN.rawValue) {
            netIdx = Glyphs.classes.map { c in c.shapes.map { h.classOf($0) }.filter { $0 >= 0 } }
        } else { netIdx = nil }
        orig = input.map(\.0)
        ps = input.map(\.1)
        n = ps.count
        let cls = Glyphs.classes
        allowed = cls.map { Glyphs.allowed($0, mode) }
        textId = cls.indices.map { i in Int64(cls.firstIndex { $0.text == cls[i].text }! + 1) }
    }

    func decode() -> Recognition? {
        normalize()
        order = ps.indices.sorted { a, b in
            if ps[a].cx != ps[b].cx { return ps[a].cx < ps[b].cx }
            if ps[a].minY != ps[b].minY { return ps[a].minY < ps[b].minY }
            return a < b
        }
        coarse = ps.map { Geo.resample($0, hc / 14) }
        var paths: [Path] = []
        for l in hypotheses() { for nd in dp(l) { paths.append(Path(cost: nd.cost, line: l, node: nd)) } }
        if paths.isEmpty { return nil }
        paths = stableSorted(paths) { $0.cost }
        if lexicon && mode != .DIGITS {
            let top = Array(paths.prefix(Self.LEX_RERANK))
            let scored = top.map { p -> (Path, Float) in
                let words = finalText(walk(p.node), p.line).split(separator: " ", omittingEmptySubsequences: false)
                    .filter { w in w.count >= 2 && w.allSatisfy { $0.isLetter } }
                let hits = words.filter { TurkishLexicon.contains(String($0)) }.count
                return (p, p.cost - Self.LEX_BONUS * Float(hits))
            }
            let reranked = stableSorted(scored) { $0.1 }.map(\.0)
            return build(reranked + paths.dropFirst(Self.LEX_RERANK))
        }
        return build(paths)
    }

    private func stableSorted<T>(_ a: [T], _ key: (T) -> Float) -> [T] {
        a.enumerated().sorted { let ka = key($0.element), kb = key($1.element); return ka != kb ? ka < kb : $0.offset < $1.offset }.map(\.element)
    }

    // MARK: normalleştirme

    private func normalize() {
        hc = Prep.capHeight(ps)
        let cl = Prep.clusters(ps)
        if cl.count >= 3 {
            let maxH = cl.map(\.box.h).max()!
            let tall = cl.filter { $0.box.h >= 0.4 * maxH }
            if tall.count >= 3 {
                let ang = baselineAngle(tall.map(\.box))
                if abs(ang) > 0.015 && abs(ang) < 0.3 { rotate(-ang) }
            }
            hc = Prep.capHeight(ps)
        }
    }

    private func avg(_ v: [Float]) -> Float {
        var s = 0.0
        for x in v { s += Double(x) }
        return Float(s / Double(v.count))
    }

    private func baselineAngle(_ boxes: [Box]) -> Float {
        let xs = boxes.map(\.cx), ys = boxes.map(\.maxY)
        let tol = 0.1 * hc
        func score(_ slope: Float, _ x0: Float, _ y0: Float) -> Float {
            var s: Float = 0
            for i in xs.indices {
                let r = (ys[i] - (y0 + slope * (xs[i] - x0))) / tol
                s += max(0, 1 - r * r)
            }
            return s - 0.3 * abs(slope)
        }
        let med = ys.sorted()[ys.count / 2]
        var bestSlope: Float = 0
        var bx = avg(xs), by = med
        var best = score(0, bx, by)
        for i in xs.indices {
            for j in (i + 1)..<max(xs.count, i + 1) {
                let dx = xs[j] - xs[i]
                if abs(dx) < 0.3 * hc { continue }
                let sl = (ys[j] - ys[i]) / dx
                if abs(sl) > 0.32 { continue }
                let sc = score(sl, xs[i], ys[i])
                if sc > best { best = sc; bestSlope = sl; bx = xs[i]; by = ys[i] }
            }
        }
        let inl = xs.indices.filter { abs(ys[$0] - (by + bestSlope * (xs[$0] - bx))) < tol }
        if inl.count >= 3 {
            let mx = avg(inl.map { xs[$0] }), my = avg(inl.map { ys[$0] })
            var sxy: Float = 0, sxx: Float = 0
            for i in inl { sxy += (xs[i] - mx) * (ys[i] - my); sxx += (xs[i] - mx) * (xs[i] - mx) }
            if sxx > 0 { bestSlope = sxy / sxx }
        } else { return 0 }
        return Float(atan(Double(bestSlope)))
    }

    private func rotate(_ theta: Float) {
        let b = Box.of(ps)
        let c = Float(cos(Double(theta))), s = Float(sin(Double(theta)))
        ps = ps.map { $0.affine(c, -s, b.cx - c * b.cx + s * b.cy, s, c, b.cy - s * b.cx - c * b.cy) }
    }

    // MARK: satır varsayımları

    private func hypotheses() -> [InkLine] {
        let cl = Prep.clusters(ps)
        var out: [InkLine] = []
        func add(_ base: Float, _ h: Float) {
            if h <= 1e-4 { return }
            if out.contains(where: { abs($0.h - h) < 0.12 * h && abs($0.base - base) < 0.1 * h }) { return }
            out.append(InkLine(base: base, h: h))
        }
        let maxH = cl.map(\.box.h).max() ?? 0
        for c in cl {
            let h = c.box.h
            if h < 0.3 * maxH || h < 1e-4 { continue }
            add(c.box.maxY, h)
            add(c.box.maxY, h / 0.55)
            add(c.box.maxY - 0.45 * h, h)
        }
        let b = Box.of(ps)
        let fh = max(b.w, b.h) / 0.6
        add(b.cy + 0.4 * fh, fh)
        return out
    }

    // MARK: gruplar

    private func group(_ a: Int, _ b: Int) -> InkGroup {
        let k = a * 64 + b
        if let g = groups[k] { return g }
        let g = analyze(a, b)
        groups[k] = g
        return g
    }

    private func analyze(_ a: Int, _ b: Int) -> InkGroup {
        let g = InkGroup(a, b)
        let mem = (a...b).map { order[$0] }
        g.members = mem
        let maxExt = mem.map { ps[$0].extent }.max()!
        let ref = max(maxExt, 0.5 * hc)
        let thr = min(0.33 * ref, 0.3 * hc)
        var main = mem.filter { ps[$0].extent >= thr }
        var cand = mem.filter { ps[$0].extent < thr }
        var above: [InkMark] = [], below: [InkMark] = []
        if main.count > 1 {
            let core = main.filter { ps[$0].extent >= 0.65 * ref }
            if !core.isEmpty {
                let cb = Box.of(core.map { ps[$0] })
                let cw = max(cb.w, 0.3 * hc)
                let med = main.filter { i in
                    let p = ps[i]
                    if core.contains(i) { return false }
                    if !(p.cx >= cb.minX - 0.25 * cw && p.cx <= cb.maxX + 0.25 * cw) { return false }
                    let up = p.maxY < cb.minY + 0.02 * hc && p.cy < cb.minY - 0.05 * hc
                    let dn = p.cy > cb.maxY + 0.04 * hc && p.minY > cb.maxY - 0.05 * hc
                    if !(up || dn) { return false }
                    return !core.contains { c in Geo.minDistance(coarse[i], coarse[c]) < 0.04 * hc }
                }
                if !med.isEmpty {
                    let medSet = Set(med)
                    let rest = main.filter { !medSet.contains($0) }
                    let rb = Box.of(rest.map { ps[$0] })
                    for i in med {
                        let p = ps[i]
                        if p.cy < rb.minY { above.append(InkMark(poly: p, type: markType(p))) } else { below.append(InkMark(poly: p, type: markType(p))) }
                    }
                    main = rest
                }
            }
        }
        let medAbove = above, medBelow = below
        if main.isEmpty {
            if cand.count > 2 { return g }
            g.marksOnly = true
            g.box = Box.of(cand.map { ps[$0] })
            if g.box.w > 0.5 * hc { return g }
            g.above = cand.map { InkMark(poly: ps[$0], type: markType(ps[$0])) }
            g.main = []
        } else {
            for _ in 0..<2 {
                let mb = Box.of(main.map { ps[$0] })
                let mh = max(mb.h, 0.2 * hc), mw = max(mb.w, 0.3 * hc)
                above = medAbove; below = medBelow
                var back: [Int] = []
                for i in cand {
                    let p = ps[i]
                    let tolX = 0.2 * hc + 0.15 * mw
                    let hOk = p.cx >= mb.minX - tolX && p.cx <= mb.maxX + tolX
                    if hOk && p.cy < mb.minY && p.maxY <= mb.minY + 0.2 * mh { above.append(InkMark(poly: p, type: markType(p))) }
                    else if hOk && p.cy > mb.maxY && p.minY >= mb.maxY - 0.2 * mh { below.append(InkMark(poly: p, type: markType(p))) }
                    else { back.append(i) }
                }
                if back.isEmpty { break }
                main = main + back
                let backSet = Set(back)
                cand = cand.filter { !backSet.contains($0) }
            }
            g.main = main
            g.above = above; g.below = below
            g.box = Box.of(main.map { ps[$0] })
            if above.count + below.count > 2 { return g }
        }
        if g.box.w > 1.9 * hc { return g }
        if !g.marksOnly && g.main.count > 1 {
            let iv = g.main.map { (ps[$0].minX, ps[$0].maxX) }.enumerated()
                .sorted { $0.element.0 != $1.element.0 ? $0.element.0 < $1.element.0 : $0.offset < $1.offset }.map(\.element)
            var end = iv[0].1
            var gap: Float = 0
            for k in 1..<iv.count {
                if iv[k].0 > end + 0.03 * hc { gap = max(gap, iv[k].0 - end) }
                end = max(end, iv[k].1)
            }
            if gap > 0.3 * hc { return g }
            g.mergeCost = 12 * max(0, gap / hc - 0.04)
        }
        if !g.marksOnly { g.mergeCost += 1.5 * Float(g.main.filter { ps[$0].extent < 0.12 * hc }.count) }
        g.cls = classCosts(g)
        g.valid = !g.cls.isEmpty
        return g
    }

    private func markType(_ p: Poly) -> MarkType {
        if p.extent < 0.1 * hc || p.length < 0.14 * hc { return .DOT }
        let r = Geo.resample(p, max(p.length / 12, 1e-4))
        let mid = r.n / 2
        let endY = min(r.y[0], r.y[r.n - 1])
        let sag = r.y[mid] - endY
        if p.w >= 0.8 * p.h && sag > 0.25 * p.w && r.y[mid] >= max(r.y[0], r.y[r.n - 1]) - 0.1 * p.w { return .BREVE }
        if p.w >= 1.5 * p.h { return .DASH }
        return .TICK
    }

    private func markCost(_ spec: Marks, _ g: InkGroup) -> Float {
        let up = g.above, dn = g.below
        func dotLike(_ m: InkMark) -> Float {
            switch m.type { case .DOT: return 0; case .TICK: return 0.3; case .DASH: return 1.0; case .BREVE: return 1.5 }
        }
        switch spec {
        case .NONE: return 6 * Float(up.count + dn.count)
        case .DOT_ABOVE: return up.count == 1 && dn.isEmpty ? dotLike(up[0]) : 6
        case .TWO_DOTS_ABOVE:
            if !dn.isEmpty { return 6 }
            if up.count == 2 {
                let dx = abs(up[0].poly.cx - up[1].poly.cx), dy = abs(up[0].poly.cy - up[1].poly.cy)
                return (dx > dy ? 0 : 2.5) + 0.5 * (dotLike(up[0]) + dotLike(up[1]))
            }
            if up.count == 1 {
                switch up[0].type { case .DASH: return 0.6; case .BREVE: return 1.6; default: return 3.0 }
            }
            return 6
        case .BREVE_ABOVE:
            guard up.count == 1 && dn.isEmpty else { return 6 }
            switch up[0].type { case .BREVE: return 0; case .DASH: return 0.6; case .TICK: return 1.2; case .DOT: return 2.5 }
        case .CEDILLA_BELOW:
            guard dn.count == 1 && up.isEmpty else { return 6 }
            switch dn[0].type { case .TICK: return 0; case .DOT: return 0.5; case .BREVE: return 0.8; case .DASH: return 1.0 }
        case .DOT_BELOW:
            guard dn.count == 1 && up.isEmpty else { return 6 }
            switch dn[0].type { case .DOT: return 0; case .TICK: return 0.4; default: return 1.5 }
        case .DOTS_ABOVE_BELOW: return up.count == 1 && dn.count == 1 ? dotLike(up[0]) + dotLike(dn[0]) : 6
        default: return .infinity
        }
    }

    private func marksOnlyCost(_ spec: Marks, _ g: InkGroup) -> Float {
        let m = g.above
        switch spec {
        case .ONLY_DOT: return m.count == 1 ? (m[0].type == .DOT ? 0 : 1.5) : .infinity
        case .ONLY_TICK:
            guard m.count == 1 else { return .infinity }
            let p = m[0].poly
            return m[0].type != .DOT && p.h >= 0.6 * p.w ? 0 : 2.0
        case .ONLY_COLON:
            guard m.count == 2 else { return .infinity }
            let dx = abs(m[0].poly.cx - m[1].poly.cx), dy = abs(m[0].poly.cy - m[1].poly.cy)
            if dy > 1.2 * dx && dy >= 0.15 * hc {
                return 0.3 * Float(m[0].type == .DOT ? 0 : 1) + 0.3 * Float(m[1].type == .DOT ? 0 : 1)
            }
            return 5
        default: return .infinity
        }
    }

    private func classCosts(_ g: InkGroup) -> [ClassCost] {
        var out: [ClassCost] = []
        if g.marksOnly {
            for (ci, c) in classes.enumerated() {
                if !allowed[ci] { continue }
                let mc = marksOnlyCost(c.marks, g)
                if mc.isInfinite { continue }
                out.append(ClassCost(c: ci, base: Self.MARKS_ONLY_SHAPE + mc + c.prior, shape: Self.MARKS_ONLY_SHAPE))
            }
        } else {
            let mainPolys = g.main.map { ps[$0] }
            let dist = ShapeModel.distances(mainPolys)
            let np = textNet.flatMap { $0.probs($0.hidden(TextFeatures.extract(mainPolys)), InkScript.LATIN.rawValue) }
            let w = Self.NET_W
            for (ci, c) in classes.enumerated() {
                if !allowed[ci] || c.shapes.isEmpty { continue }
                var d = Float.infinity
                for l in c.shapes {
                    let li = ShapeModel.indexOf(l)
                    if li >= 0 { d = min(d, dist[li]) }
                }
                if d.isInfinite { continue }
                let shape = Self.SHAPE_K * d
                let mc = markCost(c.marks, g)
                var net: Float = 0
                if let np {
                    var m: Float = 0
                    for k in netIdx![ci] { m = max(m, np[k]) }
                    net = w * -Float(log(Double(max(m, 1e-3))))
                }
                out.append(ClassCost(c: ci, base: shape + mc + c.prior + net, shape: shape))
            }
        }
        out = stableSorted(out) { $0.base }
        if out.isEmpty { return out }
        let best = out[0].base
        return Array(out.filter { $0.base <= best + 9 }.prefix(18))
    }

    private func posCost(_ g: InkGroup, _ c: CharClass, _ l: InkLine) -> Float {
        let ut = l.u(g.box.minY), ub = l.u(g.box.maxY)
        let dt = (ut - c.bandTop) / c.sigma
        let db = (ub - c.bandBottom) / (c.sigma * 0.85)
        return 0.5 * (min(dt * dt, 30) + min(db * db, 30))
    }

    private func touches(_ i: Int, _ j: Int) -> Bool {
        let key = min(i, j) * 64 + max(i, j)
        if let v = touchCache[key] { return v }
        let p = ps[i], q = ps[j]
        let tol = 0.05 * hc
        let v: Bool
        if p.minX - tol > q.maxX || q.minX - tol > p.maxX || p.minY - tol > q.maxY || q.minY - tol > p.maxY { v = false }
        else { v = Geo.minDistance(coarse[i], coarse[j]) < tol }
        touchCache[key] = v
        return v
    }

    private func boundary(_ p: InkGroup?, _ g: InkGroup) -> Float {
        guard let p else { return 0 }
        let gap = g.box.minX - p.box.maxX
        var cost: Float = 0
        if gap < 0 {
            let minW = max(min(p.box.w, g.box.w), 0.25 * hc)
            cost += 3.5 * max(0, -gap / minW - 0.2)
        }
        if !p.marksOnly && !g.marksOnly {
            var t = false
            outer: for i in p.main { for j in g.main where touches(i, j) { t = true; break outer } }
            if t { cost += 1.2 }
        }
        return cost
    }

    private func catIndex(_ c: Cat) -> Int {
        switch c { case .DIGIT: return 0; case .LOWER: return 1; case .UPPER: return 2; case .SYMBOL: return 3; case .PUNCT: return 4; case .BRACKET: return 5 }
    }

    private func ctx(_ prev: Int, _ c: Int) -> Float {
        let cc = classes[c]
        if prev < 0 { return Self.START[catIndex(cc.cat)] }
        let pc = classes[prev]
        var v = Self.CTX[catIndex(pc.cat)][catIndex(cc.cat)]
        if pc.cat == .DIGIT && cc.cat == .LOWER && Self.VARS.contains(cc.text) { v = 0.4 }
        if pc.cat == .LOWER && Self.VARS.contains(pc.text) && (cc.cat == .SYMBOL || cc.cat == .BRACKET) { v = min(v, 0.15) }
        if pc.cat == .SYMBOL && cc.cat == .LOWER && Self.VARS.contains(cc.text) { v = min(v, 0.15) }
        return v
    }

    private func lmCost(_ prev: Int, _ c: Int, _ last: Bool) -> Float {
        if mode == .DIGITS { return 0 }
        let t = classes[c].text
        guard t.count == 1, let t0 = t.first, t0.isLetter else { return 0 }
        var p: Character?
        if prev >= 0 {
            let pt = classes[prev].text
            if pt.count == 1, let c0 = pt.first, c0.isLetter { p = c0 } else { return 0 }
        }
        if prev < 0 && last { return 0 }
        var v = TurkishLexicon.cost(p, t0)
        if last { v += TurkishLexicon.endCost(t0) }
        return Self.LM_W * v
    }

    // MARK: dinamik programlama

    private func dp(_ l: InkLine) -> [Node] {
        var beams = [[Node]?](repeating: nil, count: n + 1)
        beams[0] = [Node(0, -1, nil, nil, 17)]
        var optC = [Int](repeating: 0, count: Self.OPTS), optV = [Float](repeating: 0, count: Self.OPTS)
        if n == 0 { return [] }
        for end in 1...n {
            var cand: [Node] = []
            for start in max(0, end - Self.K)..<end {
                guard let prevs = beams[start], !prevs.isEmpty else { continue }
                let g = group(start, end - 1)
                if !g.valid { continue }
                var m = 0
                for cc in g.cls {
                    let v = cc.base + posCost(g, classes[cc.c], l)
                    if m < Self.OPTS { optC[m] = cc.c; optV[m] = v; m += 1 } else {
                        var w = 0
                        for k in 1..<Self.OPTS where optV[k] > optV[w] { w = k }
                        if v < optV[w] { optC[w] = cc.c; optV[w] = v }
                    }
                }
                for pv in prevs {
                    let bnd = boundary(pv.g, g)
                    for k in 0..<m {
                        let c = optC[k]
                        var cost = pv.cost + optV[k] + ctx(pv.c, c) + bnd + g.mergeCost + Self.LAMBDA
                        if end == n { cost += Self.END[catIndex(classes[c].cat)] }
                        if lexicon { cost += lmCost(pv.c, c, end == n) }
                        cand.append(Node(cost, c, g, pv, pv.key &* 1_000_003 &+ textId[c]))
                    }
                }
            }
            cand = stableSorted(cand) { $0.cost }
            var seen = Set<Int64>()
            var keep: [Node] = []
            for nd in cand {
                if !seen.insert(nd.key).inserted { continue }
                keep.append(nd)
                if keep.count >= Self.BEAM { break }
            }
            beams[end] = keep
        }
        return beams[n] ?? []
    }

    // MARK: sonuç

    private func walk(_ nd: Node) -> [Node] {
        var out: [Node] = []
        var c: Node? = nd
        while let cc = c, cc.g != nil { out.append(cc); c = cc.prev }
        return out.reversed()
    }

    private func family(_ a: CharClass, _ b: CharClass) -> Bool {
        a.marks == b.marks && (a === b || a.shapes.contains { b.shapes.contains($0) } || a.text == b.text)
    }

    private func finalText(_ nodes: [Node], _ l: InkLine) -> String { join(nodes, finalChars(nodes), l) }

    private static func isDigitChar(_ c: Character) -> Bool { c.isNumber && c.isASCII }

    /// Bağlama göre son biçim: "x"/"×", "−"/"-".
    private func finalChars(_ nodes: [Node]) -> [String] {
        var t = nodes.map { classes[$0.c].text }
        for i in t.indices {
            let prev: String? = i > 0 ? t[i - 1] : nil
            let next: String? = i + 1 < t.count ? t[i + 1] : nil
            func digitish(_ s: String?, _ open: String) -> Bool { guard let s else { return false }; return Self.isDigitChar(s.first!) || s == open }
            if t[i] == "x" || t[i] == "X" || t[i] == "×" {
                if digitish(prev, ")") && digitish(next, "(") { t[i] = "×" }
                else if mode == .DIGITS { t[i] = "×" }
                else if t[i] == "×" { t[i] = "x" }
            }
            if t[i] == "−" {
                let letter = (prev?.first?.isLetter ?? false) || (next?.first?.isLetter ?? false)
                if mode == .LETTERS || letter { t[i] = "-" }
            }
        }
        return t
    }

    private func join(_ nodes: [Node], _ t: [String], _ l: InkLine) -> String {
        let gaps = nodes.count > 1 ? (1..<nodes.count).map { nodes[$0].g!.box.minX - nodes[$0 - 1].g!.box.maxX } : []
        let spaceGap = 0.62 * l.h
        var sb = ""
        for i in t.indices {
            if i > 0 && gaps[i - 1] > spaceGap { sb += " " }
            sb += t[i]
        }
        return sb
    }

    @inline(__always) private func fexp(_ v: Float) -> Float { Float(exp(Double(v))) }

    private func build(_ paths: [Path]) -> Recognition {
        let best = paths[0]
        let l = best.line
        let nodes = walk(best.node)
        let shown = finalChars(nodes)
        let text = join(nodes, shown, l)
        var chars: [CharResult] = []
        var scores: [Float] = []
        for (ni, nd) in nodes.enumerated() {
            let g = nd.g!
            let opts = g.cls.map { cc -> (ClassCost, Float, Float) in
                let pc = posCost(g, classes[cc.c], l)
                return (cc, cc.base + posCost(g, classes[cc.c], l), pc)
            }
            let mn = opts.map(\.1).min()!
            var z: Float = 0
            let probs = opts.map { o -> Float in let v = fexp(-(o.1 - mn)); z += v; return v }
            let chosen = classes[nd.c]
            var fam: Float = 0
            for (i, o) in opts.enumerated() where family(classes[o.0.c], chosen) { fam += probs[i] / z }
            let mine = opts.first { $0.0.c == nd.c }!
            let rival = opts.filter { !family(classes[$0.0.c], chosen) }.map(\.1).min() ?? (mine.1 + 9)
            let margin = max(0, rival - mine.1)
            let q2a = fexp(-max(0, mine.0.shape - Self.Q0 - 1.5) / Self.Q1) * (1 - fexp(-margin / 1.5))
            let q = max(quality(mine.0.shape, mine.2), q2a * fexp(-max(0, mine.2 - 1.2) / 2.5))
            scores.append(q * (0.3 + 0.7 * fam))
            var candKeys: [String] = [shown[ni]]
            var candVals: [Float] = [q * (0.3 + 0.7 * fam)]
            let sortedIdx = opts.indices.sorted { opts[$0].1 != opts[$1].1 ? opts[$0].1 < opts[$1].1 : $0 < $1 }
            for i in sortedIdx {
                let o = opts[i]
                let txt = classes[o.0.c].text
                if !candKeys.contains(txt) && candKeys.count < 5 { candKeys.append(txt); candVals.append(quality(o.0.shape, o.2) * probs[i] / z) }
            }
            chars.append(CharResult(strokeIndices: g.members.map { orig[$0] }.sorted(),
                                    candidates: candKeys.indices.map { Candidate(candKeys[$0], min(max(candVals[$0], 0), 1)) }))
        }
        var segSum = 0.0
        for nd in nodes { segSum += Double(boundaryOf(nd) + nd.g!.mergeCost) }
        let segQ = fexp(-0.25 * Float(segSum))
        let mn = scores.min() ?? 0
        let mean = scores.isEmpty ? 0 : avg(scores)
        var conf = min(max((0.5 * mn + 0.5 * mean) * segQ, 0), 1)
        if text.allSatisfy({ $0 == "−" || $0 == "-" }) { conf *= 0.3 }
        if nodes.count == 1 && nodes[0].g!.members.count == 1 && classes[nodes[0].c].shapes.contains(where: { $0 == "O" || $0 == "|" }) { conf *= 0.7 }
        var alts: [String] = []
        for p in paths {
            if alts.count >= 3 { break }
            let t = finalText(walk(p.node), p.line)
            if t != text && !alts.contains(t) { alts.append(t) }
        }
        return Recognition(text: text, confidence: conf, alternatives: alts, chars: chars)
    }

    private func boundaryOf(_ nd: Node) -> Float { boundary(nd.prev?.g, nd.g!) }

    private func quality(_ shape: Float, _ pos: Float) -> Float {
        fexp(-max(0, shape - Self.Q0) / Self.Q1) * fexp(-max(0, pos - 1.2) / 2.5)
    }
}
