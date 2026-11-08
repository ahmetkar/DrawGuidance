import Foundation

/// Bölge sınırlarının izlenmesi (piksel kenarı takibi; Android `Tracer` ile birebir). Sınırlar kavşaklarda yaylara
/// bölünür; iki komşu bölge ortak yayı paylaşır ve yay yalnız bir kez sadeleştirilir (komşular arasında boşluk olmaz).
final class Tracer {

    /// Yay: ham noktalar (x,y çiftleri, piksel), loop: kavşaksız kapalı sınır.
    final class Arc {
        let raw: [Float]
        let loop: Bool
        var fitted: [Float] = []
        init(_ raw: [Float], _ loop: Bool) { self.raw = raw; self.loop = loop }
    }

    /// Sınır: yay başvuruları (yay*2 + ters?1:0) ve piksel alanı (dış sınırda pozitif).
    struct Contour {
        let refs: [Int]
        let area: Int
    }

    private let map: RegionMap
    private let w: Int
    private let h: Int
    private let rid: [Int]
    private let hCount: Int
    private var arcOf: [Int]
    var arcs: [Arc] = []
    private var outerAbove: [Bool]
    private var holeDone: [Bool]
    var outer: [Contour?]
    var holes: [[Contour]?]
    private var stepX: [Int] = []
    private var stepY: [Int] = []
    private var stepD: [Int] = []

    private static let OUT = -2
    private static let DX = [1, 0, -1, 0]
    private static let DY = [0, 1, 0, -1]

    init(_ map: RegionMap) {
        self.map = map
        w = map.w; h = map.h; rid = map.rid
        hCount = w * (h + 1)
        arcOf = [Int](repeating: -1, count: hCount + (w + 1) * h)
        outerAbove = [Bool](repeating: false, count: hCount)
        holeDone = [Bool](repeating: false, count: hCount)
        outer = [Contour?](repeating: nil, count: map.count)
        holes = [[Contour]?](repeating: nil, count: map.count)
    }

    @inline(__always) private func at(_ x: Int, _ y: Int) -> Int { x < 0 || y < 0 || x >= w || y >= h ? Self.OUT : rid[y * w + x] }

    func traceAll(_ cancel: () -> Bool) throws {
        let n = w * h
        var first = [Int](repeating: -1, count: map.count)
        for p in 0..<n where first[rid[p]] < 0 { first[rid[p]] = p }
        for r in 0..<map.count {
            if map.label[r] < 0 { continue }
            if r & 255 == 0 { try checkCancel(cancel) }
            let p = first[r]
            outer[r] = trace(r, p % w, p / w, 0, markOuter: true, markHole: false)
        }
        // Kenara değmeyen saydam bölgeler: çevreleyen her bölgede deliği oy
        var border = [Bool](repeating: false, count: map.count)
        for x in 0..<w { border[rid[x]] = true; border[rid[(h - 1) * w + x]] = true }
        for y in 0..<h { border[rid[y * w]] = true; border[rid[y * w + w - 1]] = true }
        var seen = [Int](repeating: -1, count: map.count)
        for t in 0..<map.count {
            if map.label[t] >= 0 || border[t] { continue }
            try checkCancel(cancel)
            let p = first[t]
            let tx = p % w, ty = p / w
            var prev = t
            var y = ty - 1
            while y >= 0 {
                defer { y -= 1 }
                let cur = rid[y * w + tx]
                if cur == prev { continue }
                prev = cur
                if map.label[cur] < 0 || seen[cur] == t { continue }
                seen[cur] = t
                let e = (y + 1) * w + tx
                if outerAbove[e] || holeDone[e] { continue }
                let c = trace(cur, tx + 1, y + 1, 2, markOuter: false, markHole: true)
                if holes[cur] == nil { holes[cur] = [] }
                holes[cur]!.append(c)
            }
        }
    }

    /// q bölgesinin sınırını (x0,y0) köşesinden d0 yönünde, bölge sağda kalacak şekilde izler (0 sağ, 1 aşağı, 2 sol, 3 yukarı).
    private func trace(_ q: Int, _ x0: Int, _ y0: Int, _ d0: Int, markOuter: Bool, markHole: Bool) -> Contour {
        stepX.removeAll(keepingCapacity: true); stepY.removeAll(keepingCapacity: true); stepD.removeAll(keepingCapacity: true)
        var vx = x0, vy = y0, d = d0
        var area2 = 0
        repeat {
            stepX.append(vx); stepY.append(vy); stepD.append(d)
            let nx = vx + Self.DX[d], ny = vy + Self.DY[d]
            area2 += vx * ny - nx * vy
            if d == 2 {
                let e = vy * w + vx - 1
                if markOuter { outerAbove[e] = true }
                if markHole { holeDone[e] = true }
            }
            vx = nx; vy = ny
            let right: Int, left: Int
            switch d {
            case 0: right = at(vx, vy); left = at(vx, vy - 1)
            case 1: right = at(vx - 1, vy); left = at(vx, vy)
            case 2: right = at(vx - 1, vy - 1); left = at(vx - 1, vy)
            default: right = at(vx, vy - 1); left = at(vx - 1, vy - 1)
            }
            d = right != q ? (d + 1) & 3 : left == q ? (d + 3) & 3 : d
        } while vx != x0 || vy != y0 || d != d0

        let m = stepX.count
        var junctions: [Int] = []
        for i in 0..<m where isJunction(stepX[i], stepY[i]) { junctions.append(i) }
        var refs: [Int] = []
        if junctions.isEmpty {
            let a = arcOf[edgeId(0)]
            refs.append(a >= 0 ? a * 2 + 1 : newArc(0, m, loop: true) * 2)
        } else {
            for k in 0..<junctions.count {
                let i = junctions[k]
                let j = junctions[(k + 1) % junctions.count]
                var len = (j - i + m) % m
                if len == 0 { len = m }
                let a = arcOf[edgeId(i)]
                refs.append(a >= 0 ? a * 2 + 1 : newArc(i, len, loop: false) * 2)
            }
        }
        // Kotlin Long / 2: sıfıra doğru
        return Contour(refs: refs, area: area2 / 2)
    }

    private func edgeId(_ step: Int) -> Int {
        let vx = stepX[step], vy = stepY[step]
        switch stepD[step] {
        case 0: return vy * w + vx
        case 2: return vy * w + vx - 1
        case 1: return hCount + vy * (w + 1) + vx
        default: return hCount + (vy - 1) * (w + 1) + vx
        }
    }

    private func newArc(_ start: Int, _ len: Int, loop: Bool) -> Int {
        let m = stepX.count
        let id = arcs.count
        var pts: [Float] = []
        pts.reserveCapacity((len + (loop ? 0 : 2)) * 2)
        if !loop { pts.append(Float(stepX[start])); pts.append(Float(stepY[start])) }
        for k in 0..<len {
            let s = (start + k) % m
            let d = stepD[s]
            pts.append(Float(stepX[s]) + Float(Self.DX[d]) * 0.5)
            pts.append(Float(stepY[s]) + Float(Self.DY[d]) * 0.5)
            arcOf[edgeId(s)] = id
        }
        if !loop {
            let e = (start + len) % m
            pts.append(Float(stepX[e])); pts.append(Float(stepY[e]))
        }
        arcs.append(Arc(pts, loop))
        return id
    }

    private func isJunction(_ vx: Int, _ vy: Int) -> Bool {
        if (vx == 0 || vx == w) && (vy == 0 || vy == h) { return true }
        let a = at(vx - 1, vy - 1), b = at(vx, vy - 1), c = at(vx - 1, vy), d = at(vx, vy)
        var distinct = 1
        if b != a { distinct += 1 }
        if c != a && c != b { distinct += 1 }
        if d != a && d != b && d != c { distinct += 1 }
        if distinct >= 3 { return true }
        return distinct == 2 && a == d && b == c
    }

    /// Tüm yayları sadeleştirir/eğri uydurur.
    func fit(_ p: FitParams, _ cancel: () -> Bool) throws {
        var out: [Float] = []
        out.reserveCapacity(4096)
        for (i, arc) in arcs.enumerated() {
            if i & 255 == 0 { try checkCancel(cancel) }
            out.removeAll(keepingCapacity: true)
            let n = arc.raw.count / 2
            if arc.loop { CurveFit.loop(arc.raw, n, p, &out) } else { CurveFit.open(arc.raw, n, p, &out) }
            arc.fitted = out
        }
    }

    /// Sınırın (sadeleştirilmiş) çokgeni, piksel biriminde; ardışık tekrarlar atılır.
    func polygon(_ c: Contour) -> [Float] {
        var out: [Float] = []
        func add(_ x: Float, _ y: Float) {
            let s = out.count
            if s >= 2 && abs(out[s - 2] - x) < 1e-4 && abs(out[s - 1] - y) < 1e-4 { return }
            out.append(x); out.append(y)
        }
        for ref in c.refs {
            let arc = arcs[ref >> 1]
            let f = arc.fitted
            let n = f.count / 2
            let count = arc.loop ? n : n - 1
            if count <= 0 { continue }
            if ref & 1 == 0 {
                for i in 0..<count { add(f[i * 2], f[i * 2 + 1]) }
            } else {
                for k in 0..<count { let i = n - 1 - k; add(f[i * 2], f[i * 2 + 1]) }
            }
        }
        while out.count >= 4 && abs(out[0] - out[out.count - 2]) < 1e-4 && abs(out[1] - out[out.count - 1]) < 1e-4 {
            out.removeLast(); out.removeLast()
        }
        return out
    }
}

/// Delikleri dış çokgene sıfır genişlikli köprülerle ekler (deliğin en üst noktasından yukarı dikey ışın).
func traceBridgeHoles(_ outer: [Float], _ holes: [[Float]]) -> [Float] {
    var poly = outer
    let sorted = holes.filter { $0.count >= 6 }.enumerated()
        .sorted { a, b in
            let ya = a.element[topIndex(a.element) * 2 + 1], yb = b.element[topIndex(b.element) * 2 + 1]
            return ya != yb ? ya < yb : a.offset < b.offset
        }.map(\.element)
    for hole in sorted {
        let hn = hole.count / 2
        let ti = topIndex(hole)
        let hx = hole[ti * 2], hy = hole[ti * 2 + 1]
        let n = poly.count / 2
        var bestSeg = -1
        var bestY = -Float.greatestFiniteMagnitude
        for i in 0..<n {
            let j = (i + 1) % n
            let ax = poly[i * 2], ay = poly[i * 2 + 1], bx = poly[j * 2], by = poly[j * 2 + 1]
            if (ax - hx) * (bx - hx) > 0 || ax == bx { continue }
            let t = (hx - ax) / (bx - ax)
            let y = ay + t * (by - ay)
            if y < hy && y > bestY { bestY = y; bestSeg = i }
        }
        let ix: Float, iy: Float
        if bestSeg < 0 {
            var bi = 0
            var bd = Float.greatestFiniteMagnitude
            for i in 0..<n {
                let ddx = poly[i * 2] - hx, ddy = poly[i * 2 + 1] - hy
                let dd = ddx * ddx + ddy * ddy
                if dd < bd { bd = dd; bi = i }
            }
            bestSeg = bi; ix = poly[bi * 2]; iy = poly[bi * 2 + 1]
        } else { ix = hx; iy = bestY }
        var res: [Float] = []
        res.reserveCapacity(poly.count + hole.count + 8)
        for i in 0...bestSeg { res.append(poly[i * 2]); res.append(poly[i * 2 + 1]) }
        res.append(ix); res.append(iy)
        for k in 0...hn { let i = (ti + k) % hn; res.append(hole[i * 2]); res.append(hole[i * 2 + 1]) }
        res.append(ix); res.append(iy)
        if bestSeg + 1 < n { for i in (bestSeg + 1)..<n { res.append(poly[i * 2]); res.append(poly[i * 2 + 1]) } }
        poly = res
    }
    return poly
}

private func topIndex(_ h: [Float]) -> Int {
    var best = 0
    for i in stride(from: 1, to: h.count / 2, by: 1) {
        let y = h[i * 2 + 1], by = h[best * 2 + 1]
        if y < by || (y == by && h[i * 2] < h[best * 2]) { best = i }
    }
    return best
}

/// Kapalı çokgenin işaretli alanı (y aşağı: saat yönü pozitif).
func traceSignedArea(_ p: [Float]) -> Double {
    let n = p.count / 2
    var s = 0.0
    for i in 0..<n {
        let j = (i + 1) % n
        s += Double(p[i * 2]) * Double(p[j * 2 + 1]) - Double(p[j * 2]) * Double(p[i * 2 + 1])
    }
    return s / 2
}
