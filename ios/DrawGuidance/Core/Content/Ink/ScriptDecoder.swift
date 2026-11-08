import Foundation

/// Kotlin `Char.isDigit()` (Unicode Nd) — dizenin tüm birimleri.
@inline(__always) func ktIsDigitText(_ s: String) -> Bool { !s.isEmpty && s.unicodeScalars.allSatisfy { $0.properties.generalCategory == .decimalNumber } }
@inline(__always) func ktFloatLn(_ v: Float) -> Float { Float(log(Double(v))) }
@inline(__always) func ktFloatExp(_ v: Float) -> Float { Float(exp(Double(v))) }

/// Latin dışı yazı sistemlerinin çözücüsü (Android `ScriptDecoder` ile birebir): Kiril, Arapça, Japonca, Korece.
final class ScriptDecoder {
    static let BEAM = 8
    static let OPTS = 4
    static var LAMBDA: Float = 0.5
    static var LAMBDA_CJK: Float = 0.9
    static var WIDE_CJK: Float = 4
    static let DIGIT_FACTOR: Float = 0.35
    static let SWITCH: Float = 1.2
    static let MIN_CONFIDENCE: Float = 0.3
    static let JAMO_FACTOR: Float = 0.3
    static var HINT_PRIOR: Float = 200
    static var LATIN_PRIOR: Float = 6
    static var LATIN_SINGLE_PRIOR: Float = 1000
    static var LATIN_DOUBLE_PRIOR: Float = 10
    static let TOUCH: Float = 1.2

    private static let DIGIT_LABELS: [String: String] = ["O": "0", "|": "1", "1": "1", "2": "2", "3": "3", "4": "4", "5": "5", "6": "6", "7": "7", "8": "8", "9": "9"]

    static let HANGUL_L = Array("ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ".unicodeScalars)
    static let HANGUL_V = Array("ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ".unicodeScalars)
    static let HANGUL_T = Array("ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ".unicodeScalars)
    private static let SMALL_KANA = Set("ぁぃぅぇぉっゃゅょァィゥェォッャュョ".unicodeScalars)

    static func isKatakana(_ c: Unicode.Scalar) -> Bool { (0x30A0...0x30FF).contains(c.value) }
    static func isHiragana(_ c: Unicode.Scalar) -> Bool { (0x3040...0x309F).contains(c.value) }
    static func isKanji(_ c: Unicode.Scalar) -> Bool { (0x4E00...0x9FFF).contains(c.value) }
    private static func first(_ s: String) -> Unicode.Scalar { s.unicodeScalars.first! }

    static func detect(_ ps: [Poly], _ net: HandwritingNet, _ hint: InkScript?) -> (InkScript, [Float])? {
        guard var (acc, count) = evidence(ps, net) else { return nil }
        let n = acc.count
        let all = InkScript.allCases
        if let hint { acc[hint.ordinal] += Float(count) * ktFloatLn(HINT_PRIOR) }
        else {
            let extra: Float = count == 1 ? ktFloatLn(LATIN_SINGLE_PRIOR) : (count == 2 ? ktFloatLn(LATIN_DOUBLE_PRIOR) : 0)
            acc[InkScript.LATIN.ordinal] += ktFloatLn(LATIN_PRIOR) + extra
        }
        var best = 0
        for i in acc.indices where acc[i] > acc[best] { best = i }
        let m = acc.max()!
        var z: Float = 0
        var p = [Float](repeating: 0, count: n)
        for k in 0..<n { let v = ktFloatExp(acc[k] - m); p[k] = v; z += v }
        for k in 0..<n { p[k] /= z }
        return (all[best], p)
    }

    static func evidence(_ ps: [Poly], _ net: HandwritingNet) -> ([Float], Int)? {
        if ps.isEmpty { return nil }
        let cl = Prep.clusters(ps)
        let n = InkScript.allCases.count
        var acc = [Float](repeating: 0, count: n)
        var wsum: Float = 0
        var count = 0
        guard let mx = cl.map({ max($0.box.w, $0.box.h) }).max() else { return nil }
        for c in cl {
            if c.members.count > 20 { continue }
            let size = max(c.box.w, c.box.h)
            if size < 0.25 * mx { continue }
            var members = Set(c.members)
            for i in ps.indices where !members.contains(i) && ps[i].extent < 0.25 * mx && ps[i].cx >= c.box.minX && ps[i].cx <= c.box.maxX { members.insert(i) }
            let pr = net.scriptProbs(net.hidden(TextFeatures.extract(members.sorted().map { ps[$0] })))
            let w = size
            for k in 0..<n { acc[k] += w * ktFloatLn(max(pr[k], 1e-4)) }
            wsum += w
            count += 1
        }
        if wsum <= 0 { return nil }
        for k in 0..<n { acc[k] *= Float(count) / wsum }
        return (acc, count)
    }

    private enum Kind { case LETTER, DIGIT }

    private final class Opt {
        let text: String, outputs: [String], p: Float, kind: Kind
        init(_ text: String, _ outputs: [String], _ p: Float, _ kind: Kind) { self.text = text; self.outputs = outputs; self.p = p; self.kind = kind }
    }

    private final class Group {
        let a: Int, b: Int, members: [Int], box: Box, opts: [Opt], prior: Float
        init(_ a: Int, _ b: Int, _ members: [Int], _ box: Box, _ opts: [Opt], _ prior: Float) { self.a = a; self.b = b; self.members = members; self.box = box; self.opts = opts; self.prior = prior }
    }

    private final class Node {
        let cost: Float, opt: Opt?, g: Group?, prev: Node?, key: Int64
        init(_ cost: Float, _ opt: Opt?, _ g: Group?, _ prev: Node?, _ key: Int64) { self.cost = cost; self.opt = opt; self.g = g; self.prev = prev; self.key = key }
    }

    private let orig: [Int]
    private let ps: [Poly]
    private let n: Int
    private let script: InkScript
    private let net: HandwritingNet
    private let cjk: Bool
    private let maxStrokes: Int
    private let order: [Int]
    private let cs: Float
    private var groups: [Int: Group?] = [:]
    private lazy var coarse: [Poly] = ps.map { Geo.resample($0, cs / 14) }
    private var touchCache: [Int: Bool] = [:]
    private var lambda: Float { cjk ? Self.LAMBDA_CJK : Self.LAMBDA }

    init(_ input: [(Int, Poly)], _ script: InkScript, _ net: HandwritingNet) {
        self.script = script; self.net = net
        orig = input.map(\.0)
        let ps = input.map(\.1)
        self.ps = ps
        n = ps.count
        let cjk = script == .JAPANESE || script == .KOREAN
        self.cjk = cjk
        maxStrokes = cjk ? 14 : 6
        order = ps.indices.sorted { a, b in
            if ps[a].cx != ps[b].cx { return ps[a].cx < ps[b].cx }
            if ps[a].minY != ps[b].minY { return ps[a].minY < ps[b].minY }
            return a < b
        }
        let cl = Prep.clusters(ps)
        let sizes = cl.map { cjk ? max($0.box.w, $0.box.h) : max($0.box.h, 0.8 * $0.box.w) }.sorted()
        cs = sizes.isEmpty ? 1 : max(sizes[min(sizes.count - 1, (sizes.count * 3) / 4)], 1e-3)
    }

    private func sortedDesc<T>(_ a: [T], _ key: (T) -> Float) -> [T] {
        a.enumerated().sorted { let ka = key($0.element), kb = key($1.element); return ka != kb ? ka > kb : $0.offset < $1.offset }.map(\.element)
    }

    private func group(_ a: Int, _ b: Int) -> Group? {
        let k = a * 256 + b
        if let g = groups[k] { return g }
        let g = analyze(a, b)
        groups[k] = .some(g)
        return g
    }

    private func analyze(_ a: Int, _ b: Int) -> Group? {
        let mem = (a...b).map { order[$0] }
        let polys = mem.map { ps[$0] }
        let box = Box.of(polys)
        let f: Float = script == .ARABIC ? 2.4 : (script == .CYRILLIC ? 1.7 : 1.45)
        if box.w > f * cs { return nil }
        var gapCost: Float = 0
        if mem.count > 1 {
            let iv = polys.map { ($0.minX, $0.maxX) }.enumerated()
                .sorted { $0.element.0 != $1.element.0 ? $0.element.0 < $1.element.0 : $0.offset < $1.offset }.map(\.element)
            var end = iv[0].1
            var gap: Float = 0
            for k in 1..<iv.count {
                gap = max(gap, iv[k].0 - end)
                end = max(end, iv[k].1)
            }
            if gap > (cjk ? 0.6 : 0.45) * cs { return nil }
            gapCost = 5 * max(0, gap / cs - (cjk ? 0.22 : 0.25))
        }
        let h = net.hidden(TextFeatures.extract(polys))
        let sp = net.scriptProbs(h)
        var opts: [Opt] = []
        if script == .KOREAN { koreanOpts(h, &opts) } else {
            guard let head = net.head(script.rawValue) else { return nil }
            let p = net.probs(h, script.rawValue)!
            for c in sortedDesc(Array(p.indices), { p[$0] }).prefix(Self.OPTS) {
                if c == head.junk { continue }
                let outs = head.outputs[c]
                let kind: Kind = outs.allSatisfy { o in o.unicodeScalars.allSatisfy { $0.properties.generalCategory == .decimalNumber } } ? .DIGIT : .LETTER
                opts.append(Opt(outs[0], outs, p[c], kind))
            }
        }
        if let lat = net.head(InkScript.LATIN.rawValue) {
            let pl = net.probs(h, InkScript.LATIN.rawValue)!
            let wl = sp[InkScript.LATIN.ordinal] / max(sp[InkScript.LATIN.ordinal] + sp[script.ordinal], 1e-6)
            var best: [String: Float] = [:]
            for c in pl.indices {
                guard let f0 = lat.outputs[c].first, let d = Self.DIGIT_LABELS[f0] else { continue }
                best[d] = max(best[d] ?? 0, pl[c] * wl * Self.DIGIT_FACTOR)
            }
            // HashMap sırası: tek karakterli anahtarlar karakter koduna göre
            let entries = best.sorted { $0.key < $1.key }
            for (d, v) in sortedDesc(entries, { $0.value }).prefix(2) where v > 1e-3 { opts.append(Opt(d, [d], v, .DIGIT)) }
        }
        if opts.isEmpty { return nil }
        var prior = gapCost
        let size = max(box.w, box.h)
        if cjk {
            if size < 0.55 * cs { prior += 2.2 * ktFloatLn(0.55 * cs / max(size, 1e-4)) }
            let narrow = 0.62 * cs
            if box.w < narrow && box.h > 0.7 * cs { prior += 1.6 * ktFloatLn(narrow / max(box.w, 1e-4)) }
            if box.w > 1.05 * cs { prior += Self.WIDE_CJK * (box.w / cs - 1.05) }
        } else {
            if size < 0.25 * cs { prior += 2 * ktFloatLn(0.25 * cs / max(size, 1e-4)) }
        }
        return Group(a, b, mem, box, Array(sortedDesc(opts, { $0.p }).prefix(Self.OPTS + 1)), prior)
    }

    private func koreanOpts(_ h: [Float], _ opts: inout [Opt]) {
        guard let pl = net.probs(h, HandwritingNet.KOREAN_L), let pv = net.probs(h, HandwritingNet.KOREAN_V),
              let pt = net.probs(h, HandwritingNet.KOREAN_T) else { return }
        let hl = net.head(HandwritingNet.KOREAN_L)!, hv = net.head(HandwritingNet.KOREAN_V)!, ht = net.head(HandwritingNet.KOREAN_T)!
        func top(_ p: [Float]) -> [Int] { Array(sortedDesc(Array(p.indices), { p[$0] }).prefix(3)) }
        var combos: [Opt] = []
        for l in top(pl) { for v in top(pv) { for t in top(pt) {
            if l == hl.junk { continue }
            let lo = hl.outputs[l][0], vo = hv.outputs[v][0], to = ht.outputs[t][0]
            let noL = lo == HandwritingNet.NONE, noV = vo == HandwritingNet.NONE, noT = to == HandwritingNet.NONE
            let text: String
            if noL && noV { continue }
            else if noV { if noT { text = lo } else { continue } }
            else if noL { if noT { text = vo } else { continue } }
            else {
                guard let ls = lo.unicodeScalars.first, let vs = vo.unicodeScalars.first,
                      let li = Self.HANGUL_L.firstIndex(of: ls), let vi = Self.HANGUL_V.firstIndex(of: vs) else { continue }
                var ti = -1
                if !noT { guard let ts = to.unicodeScalars.first, let x = Self.HANGUL_T.firstIndex(of: ts) else { continue }; ti = x }
                text = String(Character(Unicode.Scalar(UInt32(0xAC00 + (li * 21 + vi) * 28 + (ti + 1)))!))
            }
            let jamo = noL || noV
            combos.append(Opt(text, [text], pl[l] * pv[v] * pt[t] * (jamo ? Self.JAMO_FACTOR : 1), .LETTER))
        } } }
        opts += sortedDesc(combos, { $0.p }).prefix(Self.OPTS)
    }

    private func touches(_ i: Int, _ j: Int) -> Bool {
        let key = min(i, j) * 4096 + max(i, j)
        if let v = touchCache[key] { return v }
        let p = ps[i], q = ps[j]
        let tol = 0.04 * cs
        let v: Bool
        if p.minX - tol > q.maxX || q.minX - tol > p.maxX || p.minY - tol > q.maxY || q.minY - tol > p.maxY { v = false }
        else { v = Geo.minDistance(coarse[i], coarse[j]) < tol }
        touchCache[key] = v
        return v
    }

    private func boundary(_ p: Group?, _ g: Group) -> Float {
        guard let p else { return 0 }
        var cost: Float = 0
        let gap = g.box.minX - p.box.maxX
        if gap < 0 {
            let minW = max(min(p.box.w, g.box.w), 0.25 * cs)
            cost += cjk ? 6 * max(0, -gap / minW - 0.05) : 3.5 * max(0, -gap / minW - 0.15)
        }
        if gap < 0.05 * cs {
            var t = false
            outer: for i in p.members { for j in g.members where touches(i, j) { t = true; break outer } }
            if t { cost += Self.TOUCH }
        }
        return cost
    }

    private func ctx(_ prev: Opt?, _ o: Opt) -> Float {
        guard let prev else { return o.kind == .DIGIT ? 0.3 : 0 }
        return prev.kind != o.kind ? Self.SWITCH : 0
    }

    func decode() -> Recognition? {
        if n == 0 { return nil }
        var beams = [[Node]?](repeating: nil, count: n + 1)
        beams[0] = [Node(0, nil, nil, nil, 17)]
        for end in 1...n {
            var cand: [Node] = []
            for start in max(0, end - maxStrokes)..<end {
                guard let prevs = beams[start], !prevs.isEmpty else { continue }
                guard let g = group(start, end - 1) else { continue }
                for pv in prevs {
                    let bnd = boundary(pv.g, g)
                    for o in g.opts {
                        let cost = pv.cost + (-ktFloatLn(max(o.p, 1e-6))) + lambda + g.prior + bnd + ctx(pv.opt, o)
                        cand.append(Node(cost, o, g, pv, pv.key &* 1_000_003 &+ Int64(o.text.javaHashCode)))
                    }
                }
            }
            cand = cand.enumerated().sorted { $0.element.cost != $1.element.cost ? $0.element.cost < $1.element.cost : $0.offset < $1.offset }.map(\.element)
            var seen = Set<Int64>()
            var keep: [Node] = []
            for nd in cand {
                if !seen.insert(nd.key).inserted { continue }
                keep.append(nd)
                if keep.count >= Self.BEAM { break }
            }
            beams[end] = keep
        }
        guard let finals = beams[n], !finals.isEmpty else { return nil }
        return build(finals)
    }

    private func walk(_ nd: Node) -> [Node] {
        var out: [Node] = []
        var c: Node? = nd
        while let cc = c, cc.g != nil { out.append(cc); c = cc.prev }
        return out.reversed()
    }

    private func neighbours(_ nodes: [Node], _ i: Int) -> [Node] {
        var r: [Node] = []
        if i - 1 >= 0 { r.append(nodes[i - 1]) }
        if i + 1 < nodes.count { r.append(nodes[i + 1]) }
        return r
    }

    private func texts(_ nodes: [Node]) -> [String] {
        var t = nodes.map { $0.opt!.text }
        switch script {
        case .CYRILLIC:
            let letters = nodes.indices.filter { nodes[$0].opt!.kind == .LETTER }
            if !letters.isEmpty {
                var top: [Int: Float] = [:]
                for i in letters { top[i] = bodyTop(nodes[i].g!) }
                let tops = letters.map { top[$0]! }.sorted()
                let base = letters.map { nodes[$0].g!.box.maxY }.sorted()[letters.count / 2]
                let capTop = tops[0], mid = tops[tops.count / 2]
                let mixed = (mid - capTop) > 0.22 * max(base - capTop, 1e-4)
                func tall(_ i: Int) -> Bool { mixed && top[i]! < capTop + 0.4 * (mid - capTop) }
                var words: [[Int]] = []
                for i in nodes.indices {
                    if i == 0 || spaceBetween(nodes[i - 1], nodes[i]) { words.append([]) }
                    words[words.count - 1].append(i)
                }
                for w in words {
                    let wl = w.filter { top[$0] != nil }
                    let allTall = wl.count >= 2 && wl.allSatisfy { tall($0) }
                    for i in w {
                        let o = nodes[i].opt!
                        if o.outputs.count < 2 || o.kind != .LETTER { continue }
                        let upper = allTall || (i == w[0] && tall(i))
                        t[i] = upper ? o.outputs[1] : o.outputs[0]
                    }
                }
            }
        case .ARABIC:
            for (i, nd) in nodes.enumerated() {
                let o = nd.opt!
                if o.outputs.count > 1 {
                    let digitNear = neighbours(nodes, i).contains { $0.opt!.kind == .DIGIT }
                    t[i] = digitNear ? (o.outputs.first { ktIsDigitText($0) } ?? o.outputs[0]) : o.outputs[0]
                }
            }
        case .JAPANESE:
            for (i, nd) in nodes.enumerated() {
                let o = nd.opt!
                if o.outputs.count < 2 { continue }
                let small = o.outputs.first { Self.SMALL_KANA.contains(Self.first($0)) }
                let g = nd.g!
                let size = max(g.box.w, g.box.h)
                if small != nil && i > 0 && size < 0.55 * cs && g.box.cy > lineMid(nodes) + 0.08 * cs { t[i] = small!; continue }
                let big = o.outputs.filter { !Self.SMALL_KANA.contains(Self.first($0)) }
                if big.count < 2 { t[i] = big.first ?? o.outputs[0]; continue }
                let near = neighbours(nodes, i).map { Self.first($0.opt!.text) }
                if near.contains(where: Self.isKatakana) { t[i] = big.first { Self.isKatakana(Self.first($0)) } ?? big[0] }
                else if near.contains(where: { Self.isKanji($0) || Self.isHiragana($0) }) || near.isEmpty {
                    t[i] = big.first { Self.isKanji(Self.first($0)) || Self.isHiragana(Self.first($0)) } ?? big[0]
                } else { t[i] = big[0] }
            }
        default: break
        }
        return t
    }

    private func blocks(_ nodes: [Node], _ t: [String]) -> [ClosedRange<Int>] {
        if !script.rtl { return nodes.indices.map { $0...$0 } }
        var out: [ClosedRange<Int>] = []
        var i = nodes.count - 1
        while i >= 0 {
            var j = i
            if ktIsDigitText(t[i]) { while j - 1 >= 0 && ktIsDigitText(t[j - 1]) && !spaceBetween(nodes[j - 1], nodes[j]) { j -= 1 } }
            out.append(j...i)
            i = j - 1
        }
        return out
    }

    private func readingOrder(_ nodes: [Node], _ t: [String]) -> [Int] { blocks(nodes, t).flatMap { Array($0) } }

    private func spaceBetween(_ left: Node, _ right: Node) -> Bool {
        script != .JAPANESE && right.g!.box.minX - left.g!.box.maxX > (cjk ? 0.5 : 0.6) * cs
    }

    private func bodyTop(_ g: Group) -> Float {
        let mx = g.members.map { ps[$0].extent }.max()!
        return g.members.filter { ps[$0].extent >= 0.35 * mx }.map { ps[$0].minY }.min()!
    }

    private func lineMid(_ nodes: [Node]) -> Float { nodes.map { $0.g!.box.cy }.sorted()[nodes.count / 2] }

    private func join(_ nodes: [Node], _ t: [String]) -> String {
        var sb = ""
        var prev: ClosedRange<Int>?
        for b in blocks(nodes, t) {
            if let pr = prev {
                let (left, right) = b.lowerBound > pr.upperBound ? (pr.upperBound, b.lowerBound) : (b.upperBound, pr.lowerBound)
                if spaceBetween(nodes[left], nodes[right]) { sb += " " }
            }
            for k in b { sb += t[k] }
            prev = b
        }
        return sb
    }

    private func build(_ finals: [Node]) -> Recognition? {
        let best = finals[0]
        let nodes = walk(best)
        if nodes.isEmpty { return nil }
        let t = texts(nodes)
        let text = join(nodes, t)
        let reading = readingOrder(nodes, t)
        let chars = reading.map { i -> CharResult in
            let nd = nodes[i]
            let g = nd.g!
            var zs = 0.0
            for o in g.opts { zs += Double(o.p) }
            let z = max(Float(zs), 1e-6)
            var keys: [String] = [t[i]]
            var vals: [Float] = [nd.opt!.p]
            for o in g.opts where !keys.contains(o.text) && keys.count < 5 { keys.append(o.text); vals.append(o.p) }
            return CharResult(strokeIndices: g.members.map { orig[$0] }.sorted(),
                              candidates: keys.indices.map { Candidate(keys[$0], (vals[$0] / max(z, 1)).coerceIn(0, 1)) })
        }
        let logs = nodes.map { ktFloatLn(max($0.opt!.p, 1e-6)) }
        var ls = 0.0
        for v in logs { ls += Double(v) }
        let geo = ktFloatExp(Float(ls / Double(logs.count)))
        let weakest = ktFloatExp(logs.min()!)
        var ss = 0.0
        for nd in nodes { ss += Double(boundary(nd.prev?.g, nd.g!) + nd.g!.prior) }
        let seg = ktFloatExp(-0.3 * Float(ss) / Float(nodes.count))
        let conf = ((0.6 * geo + 0.4 * weakest) * seg).coerceIn(0, 1)
        var alts: [String] = []
        for f in finals.dropFirst() {
            if alts.count >= 3 { break }
            let ns = walk(f)
            let s = join(ns, texts(ns))
            if s != text && !alts.contains(s) { alts.append(s) }
        }
        return Recognition(text: text, confidence: conf, alternatives: alts, chars: chars)
    }
}
