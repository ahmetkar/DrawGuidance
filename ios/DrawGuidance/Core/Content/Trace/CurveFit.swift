import Foundation

/// Eğri uydurma ayarları (piksel biriminde).
struct FitParams {
    let eps: Float
    let smooth: Bool
    let cornerCos: Float
    let cornerScale: Float
    let flatTol: Float
}

/// Ham sınır çizgilerini sadeleştirir: smooth kapalıysa Ramer–Douglas–Peucker, açıksa köşeler arasına Schneider
/// yöntemiyle kübik Bézier uydurulur ve eğriler yeniden noktalara açılır (Android `CurveFit` ile birebir).
enum CurveFit {
    private static let POLY_EPS: Float = 0.75

    @inline(__always) private static func sq(_ v: Float) -> Float { v * v }

    /// Açık çizgi (uçlar sabit, dahil). Noktalar out'a eklenir.
    static func open(_ src: [Float], _ n: Int, _ p: FitParams, _ out: inout [Float]) {
        if n <= 2 { out.append(contentsOf: src[0..<(n * 2)]); return }
        var d = Array(src[0..<(n * 2)])
        let corners = Self.corners(&d, n, false, p)
        var keep: [Bool]? = p.smooth ? nil : [Bool](repeating: false, count: n)
        out.append(d[0]); out.append(d[1])
        var a = 0
        for c in corners { piece(&d, a, c, p, &keep, &out); a = c }
        piece(&d, a, n - 1, p, &keep, &out)
    }

    /// Kapalı çizgi (ilk nokta tekrarlanmaz).
    static func loop(_ src: [Float], _ n: Int, _ p: FitParams, _ out: inout [Float]) {
        if n <= 3 { out.append(contentsOf: src[0..<(n * 2)]); return }
        var d0 = Array(src[0..<(n * 2)])
        let corners = Self.corners(&d0, n, true, p)
        if corners.isEmpty && !p.smooth {
            var far = 0
            var fd: Float = -1
            for i in 1..<n {
                let dd = sq(d0[i * 2] - d0[0]) + sq(d0[i * 2 + 1] - d0[1])
                if dd > fd { fd = dd; far = i }
            }
            var ext = d0 + [d0[0], d0[1]]
            var keep = [Bool](repeating: false, count: n + 1)
            let e2 = sq(p.eps * POLY_EPS)
            rdp(&ext, 0, far, e2, &keep)
            rdp(&ext, far, n, e2, &keep)
            for i in 0..<n where keep[i] { out.append(ext[i * 2]); out.append(ext[i * 2 + 1]) }
            return
        }
        if corners.isEmpty {
            // Köşesiz: döngüsel yumuşatma ([1,2,1]/4, iki kez)
            var tmp = [Float](repeating: 0, count: n * 2)
            for _ in 0..<2 {
                for i in 0..<n {
                    let a = (i - 1 + n) % n, b = (i + 1) % n
                    tmp[i * 2] = (d0[a * 2] + 2 * d0[i * 2] + d0[b * 2]) * 0.25
                    tmp[i * 2 + 1] = (d0[a * 2 + 1] + 2 * d0[i * 2 + 1] + d0[b * 2 + 1]) * 0.25
                }
                d0 = tmp
            }
        }
        let start = corners.first ?? 0
        var ext = [Float](repeating: 0, count: n * 2 + 2)
        for i in 0...n { let j = (start + i) % n; ext[i * 2] = d0[j * 2]; ext[i * 2 + 1] = d0[j * 2 + 1] }
        var tmp: [Float] = [ext[0], ext[1]]
        if corners.isEmpty {
            let mid = n / 2
            let t0 = centreTangentCyclic(d0, n, start, p.cornerScale)
            let tm = centreTangentCyclic(d0, n, (start + mid) % n, p.cornerScale)
            schneider(&ext, 0, mid, -t0.0, -t0.1, tm.0, tm.1, p, &tmp)
            schneider(&ext, mid, n, -tm.0, -tm.1, t0.0, t0.1, p, &tmp)
        } else {
            var keep: [Bool]? = p.smooth ? nil : [Bool](repeating: false, count: n + 1)
            var a = 0
            for k in 1..<max(corners.count, 1) {
                let c = (corners[k] - start + n) % n
                piece(&ext, a, c, p, &keep, &tmp)
                a = c
            }
            piece(&ext, a, n, p, &keep, &tmp)
        }
        // Son nokta ilk noktanın tekrarıdır
        if tmp.count > 2 { out.append(contentsOf: tmp[0..<(tmp.count - 2)]) }
    }

    // MARK: Köşeler

    private static func corners(_ d: inout [Float], _ n: Int, _ closed: Bool, _ p: FitParams) -> [Int] {
        if n < 5 { return [] }
        let L = p.cornerScale
        var candIdx: [Int] = [], cand1: [Float] = [], cand2: [Float] = []
        let lo = closed ? 0 : 2
        let hi = closed ? n : n - 2
        if lo < hi {
            for i in lo..<hi {
                let c1 = deflectionCos(d, n, i, L, closed)
                let c2 = deflectionCos(d, n, i, L * 2, closed)
                if c1 < p.cornerCos || c2 < p.cornerCos { candIdx.append(i); cand1.append(c1); cand2.append(c2) }
            }
        }
        if candIdx.isEmpty { return [] }
        var accepted: [Int] = []
        func accept(_ order: [Int], _ sep: Int) {
            for o in order {
                let i = candIdx[o]
                var ok = true
                for a in accepted {
                    var dist = abs(a - i)
                    if closed { dist = min(dist, n - dist) }
                    if dist <= sep { ok = false; break }
                }
                if ok { accepted.append(i) }
            }
        }
        accept(candIdx.indices.filter { cand1[$0] < p.cornerCos }
            .sorted { cand1[$0] != cand1[$1] ? cand1[$0] < cand1[$1] : candIdx[$0] < candIdx[$1] }, Int((L * 2).rounded(.up)))
        accept(candIdx.indices.filter { cand1[$0] >= p.cornerCos }
            .sorted { cand2[$0] != cand2[$1] ? cand2[$0] < cand2[$1] : candIdx[$0] < candIdx[$1] }, Int((L * 4).rounded(.up)))
        let res = accepted.sorted()
        for i in res { refineCorner(&d, n, i, L, closed) }
        return res
    }

    private static func deflectionCos(_ d: [Float], _ n: Int, _ i: Int, _ len: Float, _ closed: Bool) -> Float {
        let a = walk(d, n, i, -1, len, closed)
        let b = walk(d, n, i, 1, len, closed)
        if a < 0 || b < 0 { return 1 }
        let ux = d[i * 2] - d[a * 2], uy = d[i * 2 + 1] - d[a * 2 + 1]
        let vx = d[b * 2] - d[i * 2], vy = d[b * 2 + 1] - d[i * 2 + 1]
        let lu = (ux * ux + uy * uy).squareRoot(), lv = (vx * vx + vy * vy).squareRoot()
        if lu < 1e-4 || lv < 1e-4 { return 1 }
        return (ux * vx + uy * vy) / (lu * lv)
    }

    private static func walk(_ d: [Float], _ n: Int, _ i: Int, _ dir: Int, _ len: Float, _ closed: Bool) -> Int {
        let x = d[i * 2], y = d[i * 2 + 1]
        let l2 = len * len
        var j = i
        for _ in stride(from: 1, to: n, by: 1) {
            var nj = j + dir
            if nj < 0 || nj >= n {
                if !closed { return sq(d[j * 2] - x) + sq(d[j * 2 + 1] - y) >= l2 * 0.25 ? j : -1 }
                nj = (nj + n) % n
            }
            j = nj
            if sq(d[j * 2] - x) + sq(d[j * 2 + 1] - y) >= l2 { return j }
        }
        return -1
    }

    private static func refineCorner(_ d: inout [Float], _ n: Int, _ i: Int, _ L: Float, _ closed: Bool) {
        let a1 = walk(d, n, i, -1, L, closed), a2 = walk(d, n, i, -1, L * 2.5, closed)
        let b1 = walk(d, n, i, 1, L, closed), b2 = walk(d, n, i, 1, L * 2.5, closed)
        if a1 < 0 || a2 < 0 || b1 < 0 || b2 < 0 || a1 == a2 || b1 == b2 { return }
        let px = d[a2 * 2], py = d[a2 * 2 + 1]
        let rx = d[a1 * 2] - px, ry = d[a1 * 2 + 1] - py
        let qx = d[b1 * 2], qy = d[b1 * 2 + 1]
        let sx = d[b2 * 2] - qx, sy = d[b2 * 2 + 1] - qy
        let den = rx * sy - ry * sx
        if abs(den) < 1e-6 * ((rx * rx + ry * ry) * (sx * sx + sy * sy)).squareRoot() { return }
        let t = ((qx - px) * sy - (qy - py) * sx) / den
        let ix = px + t * rx, iy = py + t * ry
        if sq(ix - d[i * 2]) + sq(iy - d[i * 2 + 1]) <= 1.2 * 1.2 { d[i * 2] = ix; d[i * 2 + 1] = iy }
    }

    // MARK: Parça uydurma

    private static func piece(_ d: inout [Float], _ a: Int, _ b: Int, _ p: FitParams, _ keep: inout [Bool]?, _ out: inout [Float]) {
        if b - a <= 1 { out.append(d[b * 2]); out.append(d[b * 2 + 1]); return }
        if keep != nil {
            rdp(&d, a, b, sq(p.eps * POLY_EPS), &keep!)
            for i in (a + 1)...b where keep![i] { out.append(d[i * 2]); out.append(d[i * 2 + 1]) }
            return
        }
        presmooth(&d, a, b)
        if maxChordDev2(d, a, b) <= p.eps * p.eps * 0.36 { out.append(d[b * 2]); out.append(d[b * 2 + 1]); return }
        let t1 = sideTangent(d, a, b, 1, p.cornerScale)
        let t2 = sideTangent(d, b, a, -1, p.cornerScale)
        schneider(&d, a, b, t1.0, t1.1, t2.0, t2.1, p, &out)
    }

    private static func presmooth(_ d: inout [Float], _ a: Int, _ b: Int) {
        if b - a < 3 { return }
        let n = b - a + 1
        var tmp = [Float](repeating: 0, count: n * 2)
        for _ in 0..<2 {
            for k in 1..<(n - 1) {
                let i = a + k
                tmp[k * 2] = (d[i * 2 - 2] + 2 * d[i * 2] + d[i * 2 + 2]) * 0.25
                tmp[k * 2 + 1] = (d[i * 2 - 1] + 2 * d[i * 2 + 1] + d[i * 2 + 3]) * 0.25
            }
            for k in 1..<(n - 1) { d[(a + k) * 2] = tmp[k * 2]; d[(a + k) * 2 + 1] = tmp[k * 2 + 1] }
        }
    }

    private static func maxChordDev2(_ d: [Float], _ a: Int, _ b: Int) -> Float {
        let ax = d[a * 2], ay = d[a * 2 + 1], bx = d[b * 2], by = d[b * 2 + 1]
        var m: Float = 0
        for i in (a + 1)..<b {
            let dd = segDist2(d[i * 2], d[i * 2 + 1], ax, ay, bx, by)
            if dd > m { m = dd }
        }
        return m
    }

    private static func sideTangent(_ d: [Float], _ from: Int, _ to: Int, _ dir: Int, _ len: Float) -> (Float, Float) {
        let x = d[from * 2], y = d[from * 2 + 1]
        var j = from + dir
        while j != to && sq(d[j * 2] - x) + sq(d[j * 2 + 1] - y) < len * len { j += dir }
        return unit(d[j * 2] - x, d[j * 2 + 1] - y)
    }

    private static func centreTangentCyclic(_ d: [Float], _ n: Int, _ i: Int, _ len: Float) -> (Float, Float) {
        var a = walk(d, n, i, -1, len, true); if a < 0 { a = (i - 1 + n) % n }
        var b = walk(d, n, i, 1, len, true); if b < 0 { b = (i + 1) % n }
        return unit(d[a * 2] - d[b * 2], d[a * 2 + 1] - d[b * 2 + 1])
    }

    private static func unit(_ x: Float, _ y: Float) -> (Float, Float) {
        let l = (x * x + y * y).squareRoot()
        return l < 1e-9 ? (1, 0) : (x / l, y / l)
    }

    /// Schneider kübik Bézier uydurma, yığınla. first noktası yazılmış kabul edilir.
    private static func schneider(_ d: inout [Float], _ first0: Int, _ last0: Int, _ t1x0: Float, _ t1y0: Float, _ t2x0: Float, _ t2y0: Float,
                                  _ p: FitParams, _ out: inout [Float]) {
        let e2 = p.eps * p.eps
        var stackI: [Int] = [first0, last0]
        var stackF: [Float] = [t1x0, t1y0, t2x0, t2y0]
        var u = [Float](repeating: 0, count: last0 - first0 + 1)
        var bez = [Float](repeating: 0, count: 8)
        while !stackI.isEmpty {
            let last = stackI[stackI.count - 1], first = stackI[stackI.count - 2]
            let t2y = stackF[stackF.count - 1], t2x = stackF[stackF.count - 2]
            let t1y = stackF[stackF.count - 3], t1x = stackF[stackF.count - 4]
            stackI.removeLast(2); stackF.removeLast(4)
            if last - first <= 1 { out.append(d[last * 2]); out.append(d[last * 2 + 1]); continue }
            chordParams(d, first, last, &u)
            generate(d, first, last, u, t1x, t1y, t2x, t2y, &bez)
            var (split, err) = maxError(d, first, last, u, bez)
            if err > e2 && err < e2 * 16 {
                for _ in 0..<4 {
                    reparam(d, first, last, &u, bez)
                    generate(d, first, last, u, t1x, t1y, t2x, t2y, &bez)
                    (split, err) = maxError(d, first, last, u, bez)
                    if err <= e2 { break }
                }
            }
            if err <= e2 { flatten(bez, p.flatTol, &out); continue }
            if split <= first || split >= last { split = (first + last) / 2 }
            let len = p.cornerScale
            let sx = d[split * 2], sy = d[split * 2 + 1]
            var a = split - 1
            while a > first && sq(d[a * 2] - sx) + sq(d[a * 2 + 1] - sy) < len * len { a -= 1 }
            var b = split + 1
            while b < last && sq(d[b * 2] - sx) + sq(d[b * 2 + 1] - sy) < len * len { b += 1 }
            let tc = unit(d[a * 2] - d[b * 2], d[a * 2 + 1] - d[b * 2 + 1])
            // Önce sol yarı işlensin diye sağ yarı önce yığına
            stackI.append(split); stackI.append(last); stackF.append(contentsOf: [-tc.0, -tc.1, t2x, t2y])
            stackI.append(first); stackI.append(split); stackF.append(contentsOf: [t1x, t1y, tc.0, tc.1])
        }
    }

    private static func chordParams(_ d: [Float], _ first: Int, _ last: Int, _ u: inout [Float]) {
        u[0] = 0
        for i in (first + 1)...last {
            u[i - first] = u[i - first - 1] + (sq(d[i * 2] - d[i * 2 - 2]) + sq(d[i * 2 + 1] - d[i * 2 - 1])).squareRoot()
        }
        let total = u[last - first]
        if total <= 0 { for i in 0...(last - first) { u[i] = Float(i) / Float(last - first) }; return }
        for i in 1...(last - first) { u[i] /= total }
    }

    private static func generate(_ d: [Float], _ first: Int, _ last: Int, _ u: [Float],
                                 _ t1x: Float, _ t1y: Float, _ t2x: Float, _ t2y: Float, _ bez: inout [Float]) {
        let p0x = d[first * 2], p0y = d[first * 2 + 1]
        let p3x = d[last * 2], p3y = d[last * 2 + 1]
        let P0x = Double(p0x), P0y = Double(p0y), P3x = Double(p3x), P3y = Double(p3y)
        var c00 = 0.0, c01 = 0.0, c11 = 0.0, x0 = 0.0, x1 = 0.0
        for i in 0...(last - first) {
            let t = Double(u[i]), m = 1 - t
            let b0 = m * m * m, b1 = 3 * t * m * m, b2 = 3 * t * t * m, b3 = t * t * t
            let a1x = Double(t1x) * b1, a1y = Double(t1y) * b1
            let a2x = Double(t2x) * b2, a2y = Double(t2y) * b2
            c00 += a1x * a1x + a1y * a1y
            c01 += a1x * a2x + a1y * a2y
            c11 += a2x * a2x + a2y * a2y
            let px = Double(d[(first + i) * 2]) - (P0x * (b0 + b1) + P3x * (b2 + b3))
            let py = Double(d[(first + i) * 2 + 1]) - (P0y * (b0 + b1) + P3y * (b2 + b3))
            x0 += a1x * px + a1y * py
            x1 += a2x * px + a2y * py
        }
        let det = c00 * c11 - c01 * c01
        var al = abs(det) < 1e-12 ? 0.0 : (x0 * c11 - x1 * c01) / det
        var ar = abs(det) < 1e-12 ? 0.0 : (c00 * x1 - c01 * x0) / det
        let seg = Double((sq(p3x - p0x) + sq(p3y - p0y)).squareRoot())
        let eps = 1e-6 * seg
        if al < eps || ar < eps || al > seg * 1.5 || ar > seg * 1.5 { al = seg / 3; ar = seg / 3 }
        bez[0] = p0x; bez[1] = p0y
        bez[2] = Float(P0x + Double(t1x) * al); bez[3] = Float(P0y + Double(t1y) * al)
        bez[4] = Float(P3x + Double(t2x) * ar); bez[5] = Float(P3y + Double(t2y) * ar)
        bez[6] = p3x; bez[7] = p3y
    }

    /// En büyük kare sapma ve noktası.
    private static func maxError(_ d: [Float], _ first: Int, _ last: Int, _ u: [Float], _ bez: [Float]) -> (Int, Float) {
        var maxD: Float = 0
        var split = (first + last) / 2
        if first + 1 < last {
            for i in (first + 1)..<last {
                let t = u[i - first], m = 1 - t
                let b0 = m * m * m, b1 = 3 * t * m * m, b2 = 3 * t * t * m, b3 = t * t * t
                let x = b0 * bez[0] + b1 * bez[2] + b2 * bez[4] + b3 * bez[6]
                let y = b0 * bez[1] + b1 * bez[3] + b2 * bez[5] + b3 * bez[7]
                let dd = sq(x - d[i * 2]) + sq(y - d[i * 2 + 1])
                if dd >= maxD { maxD = dd; split = i }
            }
        }
        return (split, maxD)
    }

    /// Newton–Raphson ile parametrelerin iyileştirilmesi.
    private static func reparam(_ d: [Float], _ first: Int, _ last: Int, _ u: inout [Float], _ bez: [Float]) {
        if first + 1 < last {
            for i in (first + 1)..<last {
                let t = u[i - first], m = 1 - t
                let k0: Float = m * m * m, k1: Float = 3 * t * m * m, k2: Float = 3 * t * t * m, k3: Float = t * t * t
                let qx: Float = k0 * bez[0] + k1 * bez[2] + k2 * bez[4] + k3 * bez[6]
                let qy: Float = k0 * bez[1] + k1 * bez[3] + k2 * bez[5] + k3 * bez[7]
                let mm: Float = m * m, tm2: Float = 2 * t * m, tt: Float = t * t
                let q1x: Float = 3 * (mm * (bez[2] - bez[0]) + tm2 * (bez[4] - bez[2]) + tt * (bez[6] - bez[4]))
                let q1y: Float = 3 * (mm * (bez[3] - bez[1]) + tm2 * (bez[5] - bez[3]) + tt * (bez[7] - bez[5]))
                let q2x: Float = 6 * (m * (bez[4] - 2 * bez[2] + bez[0]) + t * (bez[6] - 2 * bez[4] + bez[2]))
                let q2y: Float = 6 * (m * (bez[5] - 2 * bez[3] + bez[1]) + t * (bez[7] - 2 * bez[5] + bez[3]))
                let dx = qx - d[i * 2], dy = qy - d[i * 2 + 1]
                let num = dx * q1x + dy * q1y
                let den = q1x * q1x + q1y * q1y + dx * q2x + dy * q2y
                if abs(den) > 1e-9 { u[i - first] = min(max(t - num / den, 0), 1) }
            }
        }
        if last - first > 1 { for i in 1..<(last - first) where u[i] < u[i - 1] { u[i] = u[i - 1] } }
    }

    /// Kübik Bézier'i sapma ≤ tol olacak kadar doğru parçasına açar (başlangıç noktası yazılmaz).
    private static func flatten(_ b: [Float], _ tol: Float, _ out: inout [Float]) {
        let ax = b[0] - 2 * b[2] + b[4], ay = b[1] - 2 * b[3] + b[5]
        let bx = b[2] - 2 * b[4] + b[6], by = b[3] - 2 * b[5] + b[7]
        let acc = 6 * max(ax * ax + ay * ay, bx * bx + by * by).squareRoot()
        let steps = min(max(Int((acc / (8 * tol)).squareRoot().rounded(.up)), 1), 32)
        for s in 1...steps {
            let t = Float(s) / Float(steps), m = 1 - t
            let b0 = m * m * m, b1 = 3 * t * m * m, b2 = 3 * t * t * m, b3 = t * t * t
            out.append(b0 * b[0] + b1 * b[2] + b2 * b[4] + b3 * b[6])
            out.append(b0 * b[1] + b1 * b[3] + b2 * b[5] + b3 * b[7])
        }
    }

    // MARK: RDP

    private static func rdp(_ d: inout [Float], _ first: Int, _ last: Int, _ eps2: Float, _ keep: inout [Bool]) {
        keep[first] = true; keep[last] = true
        var stack = [first, last]
        while !stack.isEmpty {
            let b = stack.removeLast(), a = stack.removeLast()
            if b - a < 2 { continue }
            var best = -1
            var bd = eps2
            let ax = d[a * 2], ay = d[a * 2 + 1], bx = d[b * 2], by = d[b * 2 + 1]
            for i in (a + 1)..<b {
                let dd = segDist2(d[i * 2], d[i * 2 + 1], ax, ay, bx, by)
                if dd > bd { bd = dd; best = i }
            }
            if best >= 0 {
                keep[best] = true
                stack.append(contentsOf: [a, best, best, b])
            }
        }
    }

    static func segDist2(_ px: Float, _ py: Float, _ ax: Float, _ ay: Float, _ bx: Float, _ by: Float) -> Float {
        let dx = bx - ax, dy = by - ay
        let l2 = dx * dx + dy * dy
        if l2 < 1e-12 { return sq(px - ax) + sq(py - ay) }
        let t = min(max(((px - ax) * dx + (py - ay) * dy) / l2, 0), 1)
        return sq(px - (ax + t * dx)) + sq(py - (ay + t * dy))
    }
}
