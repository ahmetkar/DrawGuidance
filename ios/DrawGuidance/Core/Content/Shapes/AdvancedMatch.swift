import Foundation

// v27 ek yeniden sıralayıcılar (Android AdvancedMatch.kt ile birebir): $Q nokta bulutu, parça ilişki grafiği, IDM.

@inline(__always) func javaRoundF(_ v: Float) -> Int { Int((Double(v) + 0.5).rounded(.down)) }

final class QCloud {
    let xs: [Float], ys: [Float], lut: [Int]
    init(_ xs: [Float], _ ys: [Float], _ lut: [Int]) { self.xs = xs; self.ys = ys; self.lut = lut }
}

enum QDollar {
    static let N = 32
    private static let M = 64

    static func cloud(_ polys: [Poly]) -> QCloud? {
        let strokes = polys.filter { $0.n > 0 }
        if strokes.isEmpty { return nil }
        var tot = 0.0
        for s in strokes { tot += Double(s.length) }
        let total = Float(tot)
        var xs = [Float](repeating: 0, count: N), ys = xs
        var k = 0
        if total < 1e-6 {
            for i in 0..<N { let p = strokes[i % strokes.count]; xs[i] = p.x[0]; ys[i] = p.y[0] }
            k = N
        } else {
            var alloc = strokes.indices.map { i in max(1, javaRoundF(Float(N - strokes.count) * strokes[i].length / total)) }
            var sum = alloc.reduce(0, +)
            var guardN = 0
            while sum != N && guardN < 4 * N {
                guardN += 1
                var i = 0
                if sum > N { for j in alloc.indices where alloc[j] > alloc[i] { i = j } }
                else { for j in alloc.indices where strokes[j].length / Float(alloc[j]) > strokes[i].length / Float(alloc[i]) { i = j } }
                if sum > N && alloc[i] > 1 { alloc[i] -= 1; sum -= 1 } else if sum < N { alloc[i] += 1; sum += 1 } else { break }
            }
            for (si, st) in strokes.enumerated() {
                let c = alloc[si]
                if c <= 1 || st.length < 1e-6 || st.n < 2 {
                    for _ in 0..<c where k < N { xs[k] = st.x[0]; ys[k] = st.y[0]; k += 1 }
                    continue
                }
                let r = Geo.resample(st, st.length / Float(c - 1))
                for i in 0..<c where k < N { let j = min(i, r.n - 1); xs[k] = r.x[j]; ys[k] = r.y[j]; k += 1 }
            }
            while k < N { xs[k] = xs[k - 1]; ys[k] = ys[k - 1]; k += 1 }
        }
        var minX = Float.infinity, maxX = -Float.infinity, minY = Float.infinity, maxY = -Float.infinity
        for i in 0..<N { minX = min(minX, xs[i]); maxX = max(maxX, xs[i]); minY = min(minY, ys[i]); maxY = max(maxY, ys[i]) }
        let size = max(max(maxX - minX, maxY - minY), 1e-6)
        var cx: Float = 0, cy: Float = 0
        let m1 = Float(M - 1)
        for i in 0..<N { xs[i] = (xs[i] - minX) / size * m1; ys[i] = (ys[i] - minY) / size * m1; cx += xs[i]; cy += ys[i] }
        cx /= Float(N); cy /= Float(N)
        let sx = m1 / 2 - cx, sy = m1 / 2 - cy
        for i in 0..<N { xs[i] = (xs[i] + sx).coerceIn(0, m1); ys[i] = (ys[i] + sy).coerceIn(0, m1) }
        var lut = [Int](repeating: 0, count: M * M)
        for gy in 0..<M { for gx in 0..<M {
            var best = 0
            var bd = Float.greatestFiniteMagnitude
            for i in 0..<N {
                let dx = xs[i] - Float(gx), dy = ys[i] - Float(gy)
                let d = dx * dx + dy * dy
                if d < bd { bd = d; best = i }
            }
            lut[gy * M + gx] = best
        } }
        return QCloud(xs, ys, lut)
    }

    static func distance(_ a: QCloud, _ b: QCloud) -> Float {
        let step = max(Int(Double(N).squareRoot().rounded(.down)), 1)
        var minSoFar = Float.greatestFiniteMagnitude
        let lb1 = lowerBound(a, b, step), lb2 = lowerBound(b, a, step)
        var i = 0, idx = 0
        while i < N {
            if lb1[idx] < minSoFar { minSoFar = min(minSoFar, cloudDistance(a, b, i, minSoFar)) }
            if lb2[idx] < minSoFar { minSoFar = min(minSoFar, cloudDistance(b, a, i, minSoFar)) }
            i += step; idx += 1
        }
        let weights = Float(N * (N + 1)) / 2
        return (minSoFar / weights).squareRoot() / Float(M - 1)
    }

    private static func cloudDistance(_ p: QCloud, _ t: QCloud, _ start: Int, _ minSoFar: Float) -> Float {
        var unmatched = [Bool](repeating: true, count: N)
        var i = start
        var weight = Float(N)
        var sum: Float = 0
        repeat {
            var best = -1
            var bd = Float.greatestFiniteMagnitude
            for j in 0..<N where unmatched[j] {
                let dx = p.xs[i] - t.xs[j], dy = p.ys[i] - t.ys[j]
                let d = dx * dx + dy * dy
                if d < bd { bd = d; best = j }
            }
            unmatched[best] = false
            sum += weight * bd
            if sum >= minSoFar { return sum }
            weight -= 1
            i = (i + 1) % N
        } while i != start
        return sum
    }

    private static func lowerBound(_ p: QCloud, _ t: QCloud, _ step: Int) -> [Float] {
        let count = (N + step - 1) / step
        var lb = [Float](repeating: 0, count: count)
        var sat = [Float](repeating: 0, count: N)
        for i in 0..<N {
            let gx = p.xs[i].toInt().coerceIn(0, M - 1), gy = p.ys[i].toInt().coerceIn(0, M - 1)
            let j = t.lut[gy * M + gx]
            let dx = p.xs[i] - t.xs[j], dy = p.ys[i] - t.ys[j]
            let d = dx * dx + dy * dy
            sat[i] = i == 0 ? d : sat[i - 1] + d
            lb[0] += Float(N - i) * d
        }
        var k = 1, i = step
        while i < N && k < count {
            lb[k] = lb[0] + Float(i) * sat[N - 1] - Float(N) * sat[i - 1]
            i += step; k += 1
        }
        return lb
    }
}

final class GPart {
    let type: Int
    let cx: Float, cy: Float, w: Float, h: Float
    let weight: Float
    let desc: [Float]
    init(_ type: Int, _ cx: Float, _ cy: Float, _ w: Float, _ h: Float, _ weight: Float, _ desc: [Float]) {
        self.type = type; self.cx = cx; self.cy = cy; self.w = w; self.h = h; self.weight = weight; self.desc = desc
    }
    var size: Float { max(w, h) }
}

final class PGraph { let parts: [GPart]; init(_ parts: [GPart]) { self.parts = parts } }

struct PartialMatch { let mapping: [Int]; let cost: Float }

enum PartGraph {
    static let DOT = 0, LINE = 1, ARC = 2, ROUND = 3, POLY = 4, OPEN = 5
    static let PARTIAL_MAX: Float = 1.0

    static func of(_ polys: [Poly]) -> PGraph {
        var t = 0.0
        for p in polys { t += Double(max(p.length, max(p.extent, 0.02))) }
        let total = max(Float(t), 1e-6)
        return PGraph(polys.map { part($0, max($0.length, max($0.extent, 0.02)) / total) })
    }

    static func part(_ p: Poly, _ weight: Float) -> GPart {
        if p.n < 2 || p.extent < 0.03 { return GPart(DOT, p.cx, p.cy, p.w, p.h, weight, [1, 0, 1, 0, 0.5, 0]) }
        let closed = ShapeSignatures.isClosed(p)
        let chord = kHypot(p.x[p.n - 1] - p.x[0], p.y[p.n - 1] - p.y[0])
        let straight: Float = p.length > 1e-6 ? (chord / p.length).coerceIn(0, 1) : 0
        let corners = ShapeSignatures.cornersOf(p)
        let turn = Geo.turning(p, 0.02)
        var area: Float = 0
        for i in 0..<p.n { let j = (i + 1) % p.n; area += p.x[i] * p.y[j] - p.x[j] * p.y[i] }
        area = abs(area) / 2
        let per = p.length + chord
        let round: Float = per > 1e-6 ? (4 * Float.pi * area / (per * per)).coerceIn(0, 1) : 0
        let aspect = Float(atan2(Double(p.w), Double(p.h)) / (Double.pi / 2))
        let type: Int
        if closed && round > 0.62 && corners <= 2 { type = ROUND }
        else if closed { type = POLY }
        else if straight > 0.93 && corners <= 1 { type = LINE }
        else if turn.absTurns < 0.7 && turn.reversals == 0 { type = ARC }
        else { type = OPEN }
        return GPart(type, p.cx, p.cy, p.w, p.h, weight, [closed ? 1 : 0, straight, round, Float(min(corners, 6)) / 6, aspect, min(turn.absTurns, 3) / 3])
    }

    private static func typeCost(_ a: Int, _ b: Int) -> Float {
        if a == b { return 0 }
        if (a == ROUND && b == POLY) || (a == POLY && b == ROUND) { return 0.35 }
        if (a == ARC && b == OPEN) || (a == OPEN && b == ARC) || (a == LINE && b == ARC) || (a == ARC && b == LINE) { return 0.3 }
        if a == DOT || b == DOT { return 0.8 }
        return 0.65
    }

    static func intrinsic(_ a: GPart, _ b: GPart) -> Float {
        var d = typeCost(a.type, b.type)
        if a.type != DOT && b.type != DOT {
            var s: Float = 0
            for k in 0..<6 { s += abs(a.desc[k] - b.desc[k]) }
            d += 0.35 * s
        }
        return d
    }

    private static func placed(_ a: GPart, _ b: GPart) -> Float {
        intrinsic(a, b) * 0.6 + 1.4 * kHypot(a.cx - b.cx, a.cy - b.cy) + 0.8 * abs(a.size - b.size)
    }

    static func distance(_ q: PGraph, _ t: PGraph) -> Float {
        let n = q.parts.count, m = t.parts.count
        if n == 0 || m == 0 { return 1 }
        let size = max(n, m)
        let miss: Float = 0.9
        var cost = [[Float]](repeating: [Float](repeating: 0, count: size), count: size)
        for i in 0..<size { for j in 0..<size {
            if i < n && j < m { cost[i][j] = placed(q.parts[i], t.parts[j]) * (q.parts[i].weight + t.parts[j].weight) * 0.5 }
            else if i < n { cost[i][j] = miss * q.parts[i].weight }
            else if j < m { cost[i][j] = miss * t.parts[j].weight }
        } }
        let assign = Hungarian.solve(cost)
        var total: Float = 0
        for i in 0..<size { total += cost[i][assign[i]] }
        var rel: Float = 0
        var pairs = 0
        let pairsMax = 40
        outer: for i1 in 0..<n {
            let j1 = assign[i1]
            if j1 >= m { continue }
            if i1 + 1 < n {
                for i2 in (i1 + 1)..<n {
                    let j2 = assign[i2]
                    if j2 >= m { continue }
                    let a1 = q.parts[i1], a2 = q.parts[i2], b1 = t.parts[j1], b2 = t.parts[j2]
                    let dq = kHypot((a2.cx - a1.cx) - (b2.cx - b1.cx), (a2.cy - a1.cy) - (b2.cy - b1.cy))
                    let inQ = inside(a1, a2) != inside(b1, b2)
                    rel += (dq + (inQ ? 0.3 : 0)) * min(a1.weight + a2.weight, 0.5)
                    pairs += 1
                    if pairs >= pairsMax { break outer }
                }
            }
        }
        return total + 0.8 * rel
    }

    private static func inside(_ a: GPart, _ b: GPart) -> Int {
        func within(_ x: GPart, _ y: GPart) -> Bool {
            x.cx - x.w / 2 >= y.cx - y.w / 2 - 0.01 && x.cx + x.w / 2 <= y.cx + y.w / 2 + 0.01 &&
                x.cy - x.h / 2 >= y.cy - y.h / 2 - 0.01 && x.cy + x.h / 2 <= y.cy + y.h / 2 + 0.01
        }
        if within(a, b) && b.size > a.size * 1.2 { return -1 }
        if within(b, a) && a.size > b.size * 1.2 { return 1 }
        return 0
    }

    static func partial(_ q: PGraph, _ t: PGraph, _ maxCost: Float = PARTIAL_MAX) -> PartialMatch? {
        let n = q.parts.count, m = t.parts.count
        if n == 0 || n > 3 || m <= n { return nil }
        let intr = (0..<n).map { k in (0..<m).map { j in intrinsic(q.parts[k], t.parts[j]) } }
        let rowMin = intr.map { $0.min()! }
        var lower: Float = 0
        for k in 0..<n { lower += rowMin[k] }
        if lower / Float(n) > maxCost { return nil }
        let qb = boxOf(q.parts)
        let qx = (0..<n).map { (q.parts[$0].cx - qb[0]) / qb[2] }
        let qy = (0..<n).map { (q.parts[$0].cy - qb[1]) / qb[3] }
        let qs = (0..<n).map { q.parts[$0].size / qb[4] }
        var bestCost = maxCost
        var bestMap: [Int]?
        var map = [Int](repeating: 0, count: n)
        var used = [Bool](repeating: false, count: m)
        func rec(_ i: Int, _ acc: Float) {
            var rest: Float = 0
            for k in i..<n { rest += rowMin[k] }
            if (acc + rest) / Float(n) >= bestCost { return }
            if i == n {
                var c = acc / Float(n)
                var share: Float = 0
                for k in 0..<n { share += t.parts[map[k]].size }
                if share < 0.28 { return }
                if n >= 2 {
                    var l = Float.greatestFiniteMagnitude, tp = Float.greatestFiniteMagnitude, r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
                    for k in 0..<n { let p = t.parts[map[k]]; l = min(l, p.cx - p.w / 2); tp = min(tp, p.cy - p.h / 2); r = max(r, p.cx + p.w / 2); b = max(b, p.cy + p.h / 2) }
                    let tw = max(r - l, 1e-3), th = max(b - tp, 1e-3), tl = max(tw, th)
                    for k in 0..<n {
                        let p = t.parts[map[k]]
                        let bx = (p.cx - l) / tw, by = (p.cy - tp) / th
                        c += 0.5 * kHypot(qx[k] - bx, qy[k] - by) / Float(n)
                        c += 0.3 * abs(qs[k] - p.size / tl) / Float(n)
                        if c >= bestCost { return }
                    }
                }
                if c < bestCost { bestCost = c; bestMap = map }
                return
            }
            for j in 0..<m where !used[j] {
                used[j] = true; map[i] = j
                rec(i + 1, acc + intr[i][j])
                used[j] = false
            }
        }
        rec(0, 0)
        return bestMap.map { PartialMatch(mapping: $0, cost: bestCost) }
    }

    private static func boxOf(_ parts: [GPart]) -> [Float] {
        var l = Float.greatestFiniteMagnitude, tp = Float.greatestFiniteMagnitude, r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for p in parts { l = min(l, p.cx - p.w / 2); tp = min(tp, p.cy - p.h / 2); r = max(r, p.cx + p.w / 2); b = max(b, p.cy + p.h / 2) }
        let w = max(r - l, 1e-3), h = max(b - tp, 1e-3)
        return [l, tp, w, h, max(w, h)]
    }

    static func confidence(_ cost: Float) -> Float { Float(exp(Double(-cost / 0.32))) }
}

enum Hungarian {
    static func solve(_ cost: [[Float]]) -> [Int] {
        let n = cost.count
        var u = [Double](repeating: 0, count: n + 1), v = u
        var p = [Int](repeating: 0, count: n + 1), way = p
        if n == 0 { return [] }
        for i in 1...n {
            p[0] = i
            var j0 = 0
            var minv = [Double](repeating: Double.greatestFiniteMagnitude, count: n + 1)
            var used = [Bool](repeating: false, count: n + 1)
            repeat {
                used[j0] = true
                let i0 = p[j0]
                var delta = Double.greatestFiniteMagnitude
                var j1 = 0
                for j in 1...n where !used[j] {
                    let cur = Double(cost[i0 - 1][j - 1]) - u[i0] - v[j]
                    if cur < minv[j] { minv[j] = cur; way[j] = j0 }
                    if minv[j] < delta { delta = minv[j]; j1 = j }
                }
                for j in 0...n { if used[j] { u[p[j]] += delta; v[j] -= delta } else { minv[j] -= delta } }
                j0 = j1
            } while p[j0] != 0
            repeat {
                let j1 = way[j0]
                p[j0] = p[j1]
                j0 = j1
            } while j0 != 0
        }
        var ans = [Int](repeating: 0, count: n)
        for j in 1...n where p[j] > 0 { ans[p[j] - 1] = j - 1 }
        return ans
    }
}

enum Idm {
    private static let G = 8, CH = 5
    private static let SHIFT: Float = 0.0008

    static func distance(_ q: [Float], _ qo: Int, _ t: [Float], _ to: Int) -> Float {
        var total: Float = 0
        for y in 0..<G { for x in 0..<G {
            var best = Float.greatestFiniteMagnitude
            for dy in -1...1 {
                let yy = y + dy
                if yy < 0 || yy >= G { continue }
                for dx in -1...1 {
                    let xx = x + dx
                    if xx < 0 || xx >= G { continue }
                    var s: Float = (dx != 0 || dy != 0) ? SHIFT : 0
                    for c in 0..<CH {
                        let a = q[qo + c * G * G + y * G + x], b = t[to + c * G * G + yy * G + xx]
                        s += (a - b) * (a - b)
                    }
                    if s < best { best = s }
                }
            }
            total += best
        } }
        return total
    }
}
