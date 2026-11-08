import Foundation

enum MatchMode { case SINGLE, GROUP }

struct TemplateMeta {
    let key: String, name: String, category: String, group: String
    var description: String = ""
    var solid: String? = nil
    var usage: Int = 0
    var rejects: Int = 0
}

struct IndexEntry {
    let id: Int64
    let templateKey: String
    let signature: ShapeSignature
    var user: Bool = false
}

struct TemplateMatch {
    let key: String, name: String, category: String, group: String
    let solid: String?
    let confidence: Float
    let distance: Float
    let mode: MatchMode
    let geometry: [[P]]
    var generic: Bool = false
}

struct PartialResult {
    let key: String, name: String, category: String
    let confidence: Float
    let mapping: [Int]
    let geometry: [[P]]
}

/// v27 ek yeniden sıralayıcıların ağırlıkları.
enum AdvancedWeights {
    static var enabled = true
    static var q: Float = 1.2
    static var q1: Float = 0.6
    static var idm: Float = 0.8
    static var graph: Float = 0.35
    static var net: Float = 0.12
    static var netMany: Float = 4
    static var netCapMany: Float = 6
    static var netCandidatesMany = 8
    static var netRerankMany = 6
    static var cap: Float = 0.5
    static var confShare: Float = 0.15
}

/// v21 şekil arama dizini (Android `ShapeIndex` ile birebir).
final class ShapeIndex {
    private let metaList: [TemplateMeta]
    private let keyIndex: [String: Int]
    private var usage: [Int]
    private var rejects: [Int]
    private let popularity: [Int]
    private let entryList: [IndexEntry]
    private let baseGeometry: [[[P]]?]
    private let entryKey: [Int]
    private let generic: [Bool]
    private let modeOnly: [Bool]
    private let generalMask: [Bool]
    private let rowEntry: [Int]
    private let fine: [Float]
    private let coarse: [Float]
    private let rowLsh: [Int]
    private let lshBuckets: [Int: [Int]]
    private var clouds: [[Float]?]
    private var seqs: [[Float]?]
    private var parts: [[[Float]]?]
    fileprivate var qClouds: [QCloud??]
    fileprivate var graphs: [PGraph?]
    private let lock = NSRecursiveLock()

    static let DIM = ShapeSignature.DIM
    static var PD: Int { ShapeSignatures.POOLED }
    static let ASPECT_W: Float = 1.2
    static let CHAMFER_W: Float = 2.0
    static let SEQ_W: Float = 1.5
    static let CLOSED_W: Float = 0.25
    static let CORNER_W: Float = 0.05
    static let PART_W: Float = 1.2
    static let COUNT_W: Float = 0.3
    static let PRIOR_W: Float = 0.05
    static let POP_W: Float = 0.05
    static let DROP_W: Float = 0.55
    static let DROP_FIXED: Float = 0.04
    static let SUBSET_MAX_D: Float = 0.42
    static let SUBSET_SKIP_D: Float = 0.2
    static let SUBSET_MAX_PARTS = 12
    static let SUBSET_LIMIT = 40
    static let FREQUENT_BY_USAGE = 12
    static let TAU: Float = 0.9
    static let GENERIC_D: Float = 0.4
    static let SEQ_N = 32
    static let COARSE_KEEP = 14
    static let RERANK = 6
    static let LSH_MIN_ROWS = 3000
    static let NET_CANDIDATES = 3
    static let MIN_LSH_CANDIDATES = 48

    init(_ metas: [TemplateMeta], _ entries: [IndexEntry]) {
        metaList = metas
        var ki: [String: Int] = [:]
        for (i, m) in metas.enumerated() { ki[m.key] = i }
        keyIndex = ki
        usage = metas.map(\.usage)
        rejects = metas.map(\.rejects)
        popularity = metas.map { TemplatePopularity.of($0.key) }
        let el = entries.filter { ki[$0.templateKey] != nil && $0.signature.variants > 0 }
        entryList = el
        var bg = [[[P]]?](repeating: nil, count: metas.count)
        for e in el where !e.user { bg[ki[e.templateKey]!] = e.signature.geometry }
        for e in el { let k = ki[e.templateKey]!; if bg[k] == nil { bg[k] = e.signature.geometry } }
        baseGeometry = bg
        entryKey = el.map { ki[$0.templateKey]! }
        generic = (0..<metas.count).map { k in
            guard let g = bg[k], g.count == 1 else { return false }
            let p = ShapeSignatures.polys(g)[0]
            return !ShapeSignatures.isClosed(p) && ShapeSignatures.cornersOf(p) <= 1 && Geo.turning(p, 0.02).absTurns < 0.6
        }
        let mo = metas.map { ModeCatalog.isModeOnly($0.key) }
        modeOnly = mo
        generalMask = mo.map { !$0 }
        let rows = el.reduce(0) { $0 + $1.signature.variants }
        let DIM = Self.DIM, PD = Self.PD
        var re = [Int](repeating: 0, count: rows)
        var fi = [Float](repeating: 0, count: rows * DIM)
        var co = [Float](repeating: 0, count: rows * PD)
        var rl = [Int](repeating: 0, count: rows)
        var r = 0
        for (e, entry) in el.enumerated() {
            let d = entry.signature.descriptors
            for v in 0..<entry.signature.variants {
                re[r] = e
                for j in 0..<DIM { fi[r * DIM + j] = d[v * DIM + j] }
                let pooled = ShapeSignatures.pool(d, v * DIM)
                for j in 0..<PD { co[r * PD + j] = pooled[j] }
                rl[r] = ShapeSignatures.simHashPooled(pooled)
                r += 1
            }
        }
        rowEntry = re; fine = fi; coarse = co; rowLsh = rl
        var tmp: [Int: [Int]] = [:]
        for i in 0..<rows { tmp[rl[i], default: []].append(i) }
        lshBuckets = tmp
        clouds = [[Float]?](repeating: nil, count: el.count)
        seqs = [[Float]?](repeating: nil, count: el.count)
        parts = [[[Float]]?](repeating: nil, count: el.count)
        qClouds = [QCloud??](repeating: nil, count: el.count)
        graphs = [PGraph?](repeating: nil, count: el.count)
    }

    var templateCount: Int { metaList.count }
    var entryCount: Int { entryList.count }
    var rowCount: Int { rowEntry.count }
    var metas: [TemplateMeta] { metaList }

    func meta(_ key: String) -> TemplateMeta? { keyIndex[key].map { metaList[$0] } }
    func isGeneric(_ key: String) -> Bool { keyIndex[key].map { generic[$0] } ?? false }
    func geometryOf(_ key: String) -> [[P]]? { keyIndex[key].flatMap { baseGeometry[$0] } }

    func recordFeedback(_ key: String, accepted: Bool) {
        lock.withLock {
            guard let k = keyIndex[key] else { return }
            if accepted { usage[k] += 1 } else { rejects[k] += 1 }
        }
    }
    func usageOf(_ key: String) -> Int { lock.withLock { keyIndex[key].map { usage[$0] } ?? 0 } }
    func rejectsOf(_ key: String) -> Int { lock.withLock { keyIndex[key].map { rejects[$0] } ?? 0 } }

    func plus(_ entry: IndexEntry) -> ShapeIndex {
        lock.withLock {
            let metas = metaList.enumerated().map { i, m in
                TemplateMeta(key: m.key, name: m.name, category: m.category, group: m.group, description: m.description, solid: m.solid, usage: usage[i], rejects: rejects[i])
            }
            let next = ShapeIndex(metas, entryList + [entry])
            for e in entryList.indices where e < next.qClouds.count { next.qClouds[e] = qClouds[e]; next.graphs[e] = graphs[e] }
            return next
        }
    }

    func warmAdvanced() {
        if !AdvancedWeights.enabled { return }
        lock.withLock {
            _ = netFor()
            for e in entryList.indices where !entryList[e].user { _ = qOf(e); _ = graphOf(e) }
        }
    }

    func userExemplars(_ key: String) -> Int { entryList.filter { $0.user && $0.templateKey == key }.count }

    private func maskFor(_ scope: ShapeScope?) -> [Bool] {
        guard let scope else { return generalMask }
        return metaList.map { scope.keys.contains($0.key) }
    }
    private func usageIn(_ scope: ShapeScope?, _ k: Int) -> Int { scope == nil ? usage[k] : scope!.usage[metaList[k].key] ?? 0 }
    private func rejectsIn(_ scope: ShapeScope?, _ k: Int) -> Int { scope == nil ? rejects[k] : scope!.rejects[metaList[k].key] ?? 0 }

    private static func stableSorted<T>(_ a: [T], _ key: (T) -> Float) -> [T] {
        a.enumerated().sorted { let ka = key($0.element), kb = key($1.element); return ka != kb ? ka < kb : $0.offset < $1.offset }.map(\.element)
    }
    private static func distinct(_ a: [Int]) -> [Int] { var s = Set<Int>(); return a.filter { s.insert($0).inserted } }

    func search(_ query: [[P]], _ mode: MatchMode, limit: Int = 3, minConfidence: Float = 0, useLsh: Bool? = nil, scope: ShapeScope? = nil) -> [TemplateMatch] {
        lock.withLock {
            let main = searchCore(query, mode, max(limit, Self.RERANK), useLsh, maskFor(scope), advanced: AdvancedWeights.enabled, scope: scope)
            let strong = (main.first?.1 ?? .infinity) <= Self.SUBSET_SKIP_D
            let merged = mode == .GROUP && !strong ? mergeBest(main, frequentSubsets(query, useLsh, scope)) : main
            var out: [TemplateMatch] = []
            for (k, d) in merged {
                if out.count >= limit { break }
                let m = metaList[k]
                let tm = TemplateMatch(key: m.key, name: m.name, category: m.category, group: m.group, solid: m.solid,
                                       confidence: Float(exp(Double(-d / Self.TAU))), distance: d, mode: mode, geometry: baseGeometry[k] ?? [], generic: generic[k])
                if tm.confidence >= minConfidence { out.append(tm) }
            }
            return out
        }
    }

    private func mergeBest(_ a: [(Int, Float)], _ b: [(Int, Float)]) -> [(Int, Float)] {
        if b.isEmpty { return a }
        var best: [Int: Float] = [:]
        var order: [Int] = []
        for (k, d) in a + b {
            if best[k] == nil { order.append(k) }
            if d < (best[k] ?? .infinity) { best[k] = d }
        }
        return Self.stableSorted(javaHashOrder(order).map { ($0, best[$0]!) }) { $0.1 }
    }

    private func frequentMask(_ scope: ShapeScope?) -> [Bool] {
        let within = maskFor(scope)
        let u = (0..<metaList.count).map { usageIn(scope, $0) }
        let cand = u.indices.filter { u[$0] > 0 && within[$0] }
        let byUsage = Set(cand.enumerated().sorted { u[$0.element] != u[$1.element] ? u[$0.element] > u[$1.element] : $0.offset < $1.offset }
            .prefix(Self.FREQUENT_BY_USAGE).map(\.element))
        return (0..<metaList.count).map { k in within[k] && (popularity[k] >= 4 || u[k] >= 2 || byUsage.contains(k) || (scope != nil && modeOnly[k])) }
    }

    private func frequentSubsets(_ query: [[P]], _ useLsh: Bool?, _ scope: ShapeScope?) -> [(Int, Float)] {
        let strokes = query.filter { !$0.isEmpty }
        let n = strokes.count
        if n < 3 || n > Self.SUBSET_MAX_PARTS { return [] }
        let mask = frequentMask(scope)
        if !mask.contains(true) { return [] }
        let lengths = strokes.map { st -> Float in
            var l: Float = 0
            if st.count > 1 { for i in 1..<st.count { l += kHypot(st[i].x - st[i - 1].x, st[i].y - st[i - 1].y) } }
            return max(l, 1)
        }
        var total: Float = 0
        for l in lengths { total += l }
        var subsets: [[Int]] = []
        for i in 0..<n { subsets.append([i]) }
        if n >= 4 { for i in 0..<n { for j in (i + 1)..<n { subsets.append([i, j]) } } }
        var out: [(Int, Float)] = []
        for drop in subsets.prefix(Self.SUBSET_LIMIT) {
            var s = 0.0
            for i in drop { s += Double(lengths[i]) }
            let share = Float(s) / total
            if share > 0.45 { continue }
            let sub = strokes.indices.filter { !drop.contains($0) }.map { strokes[$0] }
            for (k, d) in searchCore(sub, .GROUP, 2, useLsh, mask, scope: scope) where d <= Self.SUBSET_MAX_D {
                out.append((k, d + Self.DROP_W * share + Self.DROP_FIXED * Float(drop.count)))
            }
        }
        return out
    }

    private func searchCore(_ query: [[P]], _ mode: MatchMode, _ limit: Int, _ useLsh: Bool?, _ only: [Bool]?, advanced: Bool = false, scope: ShapeScope? = nil) -> [(Int, Float)] {
        let strokes = query.filter { !$0.isEmpty }
        if strokes.isEmpty || entryList.isEmpty { return [] }
        let DIM = Self.DIM, PD = Self.PD
        let norm = ShapeSignatures.normalize(strokes)
        let rawPolys = ShapeSignatures.polys(norm)
        let polys = ShapeSignatures.collapseSmall(rawPolys)
        let qn = polys.count
        let q = ShapeFeatures.extract(polys)
        let qc = ShapeSignatures.pool(q)
        let qa = ShapeFeatures.aspect(rawPolys)

        let allowed = entryList.indices.map { e in (only == nil || only![entryKey[e]]) && compatible(mode, qn, entryList[e].signature.strokeCount) }
        let nk = metaList.count
        var coarseBest = [Float](repeating: .infinity, count: nk)
        let lsh = useLsh ?? (rowEntry.count >= Self.LSH_MIN_ROWS)
        var scanned = 0
        if lsh {
            let key = ShapeSignatures.simHashPooled(qc)
            for probe in ShapeSignatures.probes(key, 2) {
                guard let rows = lshBuckets[probe] else { continue }
                for r in rows { scanned += coarseRow(r, qc, qa, allowed, &coarseBest) }
            }
        }
        if !lsh || scanned < Self.MIN_LSH_CANDIDATES {
            for r in rowEntry.indices { _ = coarseRow(r, qc, qa, allowed, &coarseBest) }
        }
        let top0 = Array(Self.stableSorted((0..<nk).filter { coarseBest[$0].isFinite }) { coarseBest[$0] }.prefix(Self.COARSE_KEEP))
        let net: ShapeClassifier? = advanced && ShapeComplexity.usesNet(strokes.count) ? (scope?.net ?? netFor()) : nil
        let netProbs = net?.probs(ShapeNet.input(polys, qc, qa))
        let modeNet = net != nil && scope?.net != nil
        var netTop: [Int] = []
        if let net, let netProbs {
            let order = netProbs.indices.enumerated().sorted { netProbs[$0.element] != netProbs[$1.element] ? netProbs[$0.element] > netProbs[$1.element] : $0.offset < $1.offset }.map(\.element)
            netTop = order.prefix(modeNet ? AdvancedWeights.netCandidatesMany : Self.NET_CANDIDATES).compactMap { keyIndex[net.keys[$0]] }
                .filter { k in coarseBest[k].isFinite || entryList.indices.contains { e in entryKey[e] == k && allowed[e] } }
        }
        let top = Self.distinct(top0 + netTop)
        if top.isEmpty { return [] }

        let topSet = Set(top)
        var fineBest = [Float](repeating: .infinity, count: nk)
        var bestEntry = [Int](repeating: -1, count: nk)
        var bestRow = [Int](repeating: -1, count: nk)
        for r in rowEntry.indices {
            let e = rowEntry[r]
            if !allowed[e] { continue }
            let k = entryKey[e]
            if !topSet.contains(k) { continue }
            let d = 2 - 2 * Self.dot(q, 0, fine, r * DIM, DIM) + Self.ASPECT_W * Self.sq(qa - entryList[e].signature.aspect)
            if d < fineBest[k] { fineBest[k] = d; bestEntry[k] = e; bestRow[k] = r }
        }

        let rer = Self.distinct(Array(Self.stableSorted(top.filter { bestEntry[$0] >= 0 }) { fineBest[$0] }.prefix(Self.RERANK)) +
            Array(netTop.filter { bestEntry[$0] >= 0 }.prefix(modeNet ? AdvancedWeights.netRerankMany : 2)))
        let qCloud = ShapeFeatures.cloud(polys)
        let qSeq = mode == .SINGLE ? sequence(polys[0]) : nil
        let qClosed = mode == .SINGLE ? ShapeSignatures.isClosed(polys[0]) : false
        let qCorners = mode == .SINGLE ? ShapeSignatures.cornersOf(polys[0]) : 0
        let qParts = mode == .GROUP ? partsOf(polys) : nil
        let qQ = advanced ? QDollar.cloud(polys) : nil
        let qGraph = advanced && mode == .GROUP ? PartGraph.of(polys) : nil
        let maxP = netProbs.map { max($0.max() ?? 0, 1e-6) } ?? 1
        let netW: Float = modeNet ? max(scope!.netWeight, AdvancedWeights.netMany) : (scope?.net != nil ? scope!.netWeight : AdvancedWeights.net)
        let adv = advanced ? advancedPenalties(mode, rer, bestEntry, bestRow, q, qQ, qGraph, net, netProbs, maxP, netW, modeNet ? AdvancedWeights.netCapMany : AdvancedWeights.cap) : nil
        var scored: [(Int, Float, Float)] = []
        for (ri, k) in rer.enumerated() {
            let e = bestEntry[k]
            let sig = entryList[e].signature
            var d = fineBest[k]
            d += Self.CHAMFER_W * ShapeFeatures.chamfer(qCloud, cloudOf(e))
            if mode == .SINGLE {
                let tClosed = sig.closedMask & 1 != 0
                d += Self.SEQ_W * seqDistance(qSeq!, seqOf(e), qClosed && tClosed)
                if qClosed != tClosed { d += Self.CLOSED_W }
                d += Self.CORNER_W * Float(min(abs(qCorners - sig.corners), 4))
            } else {
                d += Self.PART_W * partDistance(qParts!, partsOfEntry(e))
                d += Self.COUNT_W * Float(abs(qn - sig.strokeCount)) / Float(max(qn, sig.strokeCount))
            }
            d -= Self.PRIOR_W * Float(log(Double((Float(usageIn(scope, k)) + 1) / (Float(rejectsIn(scope, k)) + 1)))).coerceIn(-2, 2)
            d -= Self.POP_W * Float(log(Double(Float(popularity[k]) / 2)))
            if generic[k] { d += Self.GENERIC_D }
            let a = adv?[ri] ?? 0
            scored.append((k, max(d + a, 0), max(d + AdvancedWeights.confShare * a, 0)))
        }
        scored = Self.stableSorted(scored) { $0.1 }
        var out: [(Int, Float)] = []
        var prev: Float = 0
        for t in scored {
            if out.count >= limit { break }
            let d = max(t.2, prev)
            out.append((t.0, d))
            prev = d + 1e-4
        }
        return out
    }

    // MARK: ek yeniden sıralayıcılar

    private func qOf(_ e: Int) -> QCloud? {
        if let c = qClouds[e] { return c }
        let c = QDollar.cloud(entryPolys(e))
        qClouds[e] = .some(c)
        return c
    }

    func graphOf(_ e: Int) -> PGraph {
        if let g = graphs[e] { return g }
        let g = PartGraph.of(entryPolys(e))
        graphs[e] = g
        return g
    }

    private func netFor() -> ShapeNet? {
        guard let n = ShapeNet.bundled, !n.keys.isEmpty, n.keys.filter({ keyIndex[$0] != nil }).count >= n.keys.count / 2 else { return nil }
        return n
    }

    private func advancedPenalties(_ mode: MatchMode, _ rer: [Int], _ bestEntry: [Int], _ bestRow: [Int], _ q: [Float], _ qQ: QCloud?, _ qGraph: PGraph?,
                                   _ net: ShapeClassifier?, _ netProbs: [Float]?, _ maxP: Float, _ netWeight: Float, _ netCap: Float) -> [Float] {
        let w = AdvancedWeights.self
        let n = rer.count
        var out = [Float](repeating: 0, count: n)
        if n == 0 { return out }
        var terms = [[Float]](repeating: [Float](repeating: .nan, count: n), count: 4)
        for (i, k) in rer.enumerated() {
            let e = bestEntry[k]
            if let qQ, let t = qOf(e) { terms[0][i] = QDollar.distance(qQ, t) }
            let row = bestRow[k]
            if row >= 0 { terms[1][i] = Idm.distance(q, 0, fine, row * Self.DIM) }
            if let qGraph { terms[2][i] = PartGraph.distance(qGraph, graphOf(e)) }
            if let net, let netProbs {
                let ni = net.indexOf(metaList[k].key)
                terms[3][i] = ni >= 0 ? 1 - (netProbs[ni] / maxP).coerceIn(0, 1) : 0
            }
        }
        let weights: [Float] = [mode == .SINGLE ? w.q1 : w.q, w.idm, w.graph, netWeight]
        for t in 0..<4 {
            let col = terms[t]
            var mn = Float.infinity
            for v in col where !v.isNaN && v < mn { mn = v }
            if !mn.isFinite { continue }
            let cap = t == 3 ? netCap : w.cap
            for i in 0..<n where !col[i].isNaN { out[i] += weights[t] * min(col[i] - mn, cap) }
        }
        return out
    }

    func partialMatches(_ query: [[P]], limit: Int = 4, minConfidence: Float = 0.45, scope: ShapeScope? = nil) -> [PartialResult] {
        lock.withLock {
            let strokes = query.filter { !$0.isEmpty }
            if strokes.isEmpty || strokes.count > 3 { return [] }
            let norm = ShapeSignatures.normalize(strokes)
            let polys = ShapeSignatures.polys(norm)
            let qg = PartGraph.of(polys)
            let mask = maskFor(scope)
            let net = ShapeComplexity.usesNet(strokes.count) ? scope?.net : nil
            let probs = net.flatMap { n in ShapeNet.inputOf(strokes).map { n.probs($0) } }
            let maxP = probs?.max() ?? 1
            var best: [Int: (Int, PartialMatch)] = [:]
            var order: [Int] = []
            for e in entryList.indices {
                if entryList[e].user { continue }
                if !mask[entryKey[e]] { continue }
                let sig = entryList[e].signature
                if sig.strokeCount <= strokes.count || sig.strokeCount > 24 { continue }
                let k = entryKey[e]
                let prev = best[k]
                guard let pm = PartGraph.partial(qg, graphOf(e), min(PartGraph.PARTIAL_MAX, prev?.1.cost ?? .infinity)) else { continue }
                if prev == nil || pm.cost < prev!.1.cost {
                    if prev == nil { order.append(k) }
                    best[k] = (e, pm)
                }
            }
            var res: [PartialResult] = []
            for k in javaHashOrder(order) {
                let v = best[k]!
                let lr = Float(log(Double((Float(usageIn(scope, k)) + 1) / (Float(rejectsIn(scope, k)) + 1)))).coerceIn(-1, 1)
                var conf = PartGraph.confidence(v.1.cost) * (1 + 0.03 * Float(popularity[k] - 3)) * (1 + 0.05 * lr)
                if let net, let probs { conf *= 1 + 0.5 * scope!.netWeight * (relativeProb(probs, net.indexOf(metaList[k].key), maxP) - 0.5) }
                let r = PartialResult(key: metaList[k].key, name: metaList[k].name, category: metaList[k].category, confidence: conf.coerceIn(0, 1),
                                      mapping: v.1.mapping, geometry: entryList[v.0].signature.geometry)
                if r.confidence >= minConfidence { res.append(r) }
            }
            res = res.enumerated().sorted { $0.element.confidence != $1.element.confidence ? $0.element.confidence > $1.element.confidence : $0.offset < $1.offset }.map(\.element)
            return Array(res.prefix(limit))
        }
    }

    private func coarseRow(_ r: Int, _ qc: [Float], _ qa: Float, _ allowed: [Bool], _ best: inout [Float]) -> Int {
        let e = rowEntry[r]
        if !allowed[e] { return 0 }
        let d = 2 - 2 * Self.dot(qc, 0, coarse, r * Self.PD, Self.PD) + Self.ASPECT_W * Self.sq(qa - entryList[e].signature.aspect)
        let k = entryKey[e]
        if d < best[k] { best[k] = d }
        return 1
    }

    private func compatible(_ mode: MatchMode, _ qn: Int, _ tn: Int) -> Bool {
        switch mode {
        case .SINGLE: return tn == 1
        case .GROUP: return tn >= 2 && (abs(qn - tn) <= 2 || Float(min(qn, tn)) / Float(max(qn, tn)) >= 0.5)
        }
    }

    private func cloudOf(_ e: Int) -> [Float] {
        if let c = clouds[e] { return c }
        let c = ShapeFeatures.cloud(entryPolys(e))
        clouds[e] = c
        return c
    }

    private func entryPolys(_ e: Int) -> [Poly] { ShapeSignatures.collapseSmall(ShapeSignatures.polys(entryList[e].signature.geometry)) }

    private func seqOf(_ e: Int) -> [Float] {
        if let s = seqs[e] { return s }
        let s = sequence(ShapeSignatures.polys(entryList[e].signature.geometry)[0])
        seqs[e] = s
        return s
    }

    private func sequence(_ p: Poly) -> [Float] {
        let N = Self.SEQ_N
        var out = [Float](repeating: 0, count: N * 2)
        let s = max(p.extent, 1e-4)
        let len = p.length
        if len < 1e-6 || p.n < 2 { return out }
        let step = len / Float(N - 1)
        var k = 0
        var acc: Float = 0
        var i = 1
        var px = p.x[0], py = p.y[0]
        out[0] = px; out[1] = py; k = 1
        while i < p.n && k < N - 1 {
            let qx = p.x[i], qy = p.y[i]
            let d = kHypot(qx - px, qy - py)
            if acc + d >= step && d > 0 {
                let t = (step - acc) / d
                px += t * (qx - px); py += t * (qy - py)
                out[2 * k] = px; out[2 * k + 1] = py; k += 1; acc = 0
            } else { acc += d; px = qx; py = qy; i += 1 }
        }
        while k < N { out[2 * k] = p.x[p.n - 1]; out[2 * k + 1] = p.y[p.n - 1]; k += 1 }
        for j in 0..<N { out[2 * j] = (out[2 * j] - p.cx) / s; out[2 * j + 1] = (out[2 * j + 1] - p.cy) / s }
        return out
    }

    private func seqDistance(_ a: [Float], _ b: [Float], _ cyclic: Bool) -> Float {
        let N = Self.SEQ_N
        var best = Float.infinity
        let shifts = cyclic ? N - 1 : 1
        for rev in 0...1 { for sh in 0..<shifts {
            var sum: Float = 0
            for i in 0..<N {
                var j = rev == 0 ? i : N - 1 - i
                if cyclic { j = (j + sh) % (N - 1) }
                sum += kHypot(a[2 * i] - b[2 * j], a[2 * i + 1] - b[2 * j + 1])
                if sum >= best * Float(N) { break }
            }
            best = min(best, sum / Float(N))
        } }
        return best
    }

    private func partsOfEntry(_ e: Int) -> [[Float]] {
        if let p = parts[e] { return p }
        let p = partsOf(entryPolys(e))
        parts[e] = p
        return p
    }

    private func partsOf(_ polys: [Poly]) -> [[Float]] {
        var a = Float.infinity, b = Float.infinity, c = -Float.infinity, d = -Float.infinity
        for p in polys { a = min(a, p.minX); b = min(b, p.minY); c = max(c, p.maxX); d = max(d, p.maxY) }
        let s = max(max(c - a, d - b), 1e-4)
        var t = 0.0
        for p in polys { t += Double(max(max(p.length, p.extent), 0.02 * s)) }
        let total = Float(t)
        return polys.map { p in [(p.cx - a) / s, (p.cy - b) / s, p.extent / s, max(max(p.length, p.extent), 0.02 * s) / total] }
    }

    private func partDistance(_ a: [[Float]], _ b: [[Float]]) -> Float {
        func oneWay(_ p: [[Float]], _ q: [[Float]]) -> Float {
            var sum: Float = 0
            for x in p {
                var best = Float.infinity
                for y in q {
                    let c = kHypot(x[0] - y[0], x[1] - y[1]) + 0.5 * abs(x[2] - y[2])
                    if c < best { best = c }
                }
                sum += x[3] * best
            }
            return sum
        }
        return 0.5 * (oneWay(a, b) + oneWay(b, a))
    }

    private static func sq(_ v: Float) -> Float { v * v }

    private static func dot(_ q: [Float], _ qo: Int, _ m: [Float], _ off: Int, _ len: Int) -> Float {
        var a0: Float = 0, a1: Float = 0, a2: Float = 0, a3: Float = 0
        var k = 0
        while k < len {
            a0 += q[qo + k] * m[off + k]
            a1 += q[qo + k + 1] * m[off + k + 1]
            a2 += q[qo + k + 2] * m[off + k + 2]
            a3 += q[qo + k + 3] * m[off + k + 3]
            k += 4
        }
        return (a0 + a1) + (a2 + a3)
    }

    static func fromCatalog(_ templates: [ShapeTemplate] = ShapeTemplates.all) -> ShapeIndex {
        let metas = templates.map { TemplateMeta(key: $0.key, name: $0.name, category: $0.category, group: $0.group, description: $0.description, solid: $0.solid) }
        let entries = templates.enumerated().map { IndexEntry(id: Int64($0.offset), templateKey: $0.element.key, signature: ShapeSignatures.of($0.element.strokes)) }
        return ShapeIndex(metas, entries)
    }
}
