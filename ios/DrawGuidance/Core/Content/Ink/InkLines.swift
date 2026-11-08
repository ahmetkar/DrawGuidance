import Foundation

/// v38 çizgi önerisinin çoklu çizgi adımı (Android `InkLines` ile birebir): uç noktalarının tek bağlantılı kümelemeyle
/// yapıştırılması, çember uydurması ve yaylar.
enum InkLines {
    struct Item {
        let kind: InkShapeKind?
        let points: [InkPoint]
        init(_ kind: InkShapeKind?, _ points: [InkPoint]) { self.kind = kind; self.points = points }
        var closed: Bool {
            switch kind {
            case .CIRCLE, .ELLIPSE, .TRIANGLE, .RECTANGLE, .SQUARE, .POLYGON: return true
            default: return points.count > 2 && points[0] == points[points.count - 1]
            }
        }
    }

    struct Result { let items: [Item]; let joints: Int }

    static let ARC_MIN_SWEEP = 0.7
    static let ARC_MAX_RMS: Float = 0.035
    static let EPS_SHARE: Float = 0.12
    static let EPS_MIN: Float = 8
    static let EPS_MAX: Float = 48
    private static let STRAIGHT_TOL: Float = 1.5
    private static let SIN_MIN_ANGLE: Float = 0.34

    static func snapEndpoints(_ items: [Item], eps: Float? = nil) -> Result {
        if items.count < 2 { return Result(items: items, joints: 0) }
        let e: Float
        if let eps { e = eps } else {
            let ext = items.map { extent($0.points) }.sorted()
            e = (ext[ext.count / 2] * EPS_SHARE).coerceIn(EPS_MIN, EPS_MAX)
        }
        var ends: [(Int, Int)] = []
        for (i, it) in items.enumerated() where !it.closed && it.points.count >= 2 { ends.append((i, 0)); ends.append((i, 1)) }
        if ends.count < 2 { return Result(items: items, joints: 0) }
        func pt(_ end: (Int, Int)) -> InkPoint { let p = items[end.0].points; return end.1 == 0 ? p[0] : p[p.count - 1] }
        var parent = Array(0..<ends.count)
        func find(_ x: Int) -> Int {
            var r = x
            while parent[r] != r { r = parent[r] }
            var y = x
            while parent[y] != r { let n = parent[y]; parent[y] = r; y = n }
            return r
        }
        for a in ends.indices { for b in (a + 1)..<max(ends.count, a + 1) {
            if ends[a].0 == ends[b].0 { continue }
            let pa = pt(ends[a]), pb = pt(ends[b])
            if kHypot(pa.x - pb.x, pa.y - pb.y) <= e { let fa = find(a); parent[fa] = find(b) }
        } }
        var keys: [Int] = []
        var groups: [Int: [Int]] = [:]
        for i in ends.indices {
            let r = find(i)
            if groups[r] == nil { keys.append(r); groups[r] = [] }
            groups[r]!.append(i)
        }
        let clusters = keys.map { groups[$0]! }.filter { c in Set(c.map { ends[$0].0 }).count >= 2 }
        if clusters.isEmpty { return Result(items: items, joints: 0) }
        var out = items.map(\.points)
        var joints = 0
        for c in clusters {
            let members = c.map { ends[$0] }
            guard let t = target(members.map { (items[$0.0], $0.1) }, members.map(pt), e) else { continue }
            for m in members { move(&out[m.0], items[m.0].kind, m.1, t) }
            joints += 1
        }
        return Result(items: items.enumerated().map { Item($0.element.kind, out[$0.offset]) }, joints: joints)
    }

    private static func avg(_ v: [Float]) -> Float {
        var s = 0.0
        for x in v { s += Double(x) }
        return Float(s / Double(v.count))
    }

    private static func target(_ ends: [(Item, Int)], _ pts: [InkPoint], _ eps: Float) -> InkPoint? {
        let cx = avg(pts.map(\.x)), cy = avg(pts.map(\.y))
        if ends.count == 2, let d1 = direction(ends[0].0.points, ends[0].1), let d2 = direction(ends[1].0.points, ends[1].1) {
            let cross = d1.1.x * d2.1.y - d1.1.y * d2.1.x
            if abs(cross) > SIN_MIN_ANGLE {
                let p = d1.0, q = d2.0
                let t = ((q.x - p.x) * d2.1.y - (q.y - p.y) * d2.1.x) / cross
                let ix = p.x + d1.1.x * t, iy = p.y + d1.1.y * t
                if kHypot(ix - cx, iy - cy) <= 2 * eps { return InkPoint(ix, iy) }
            }
        }
        return InkPoint(cx, cy)
    }

    private static func direction(_ p: [InkPoint], _ end: Int) -> (InkPoint, InkPoint)? {
        let run = straightRun(p, end)
        let total = length(p)
        if run.1 < max(0.25 * total, 8) { return nil }
        let a = p[run.0], b = end == 0 ? p[0] : p[p.count - 1]
        let dx = b.x - a.x, dy = b.y - a.y
        let l = kHypot(dx, dy)
        if !(l > 1e-3) { return nil }
        return (b, InkPoint(dx / l, dy / l))
    }

    private static func straightRun(_ p: [InkPoint], _ end: Int) -> (Int, Float) {
        let n = p.count
        let idx: [Int] = end == 0 ? Array(0..<n) : Array((0..<n).reversed())
        let e = p[idx[0]]
        var last = idx[0]
        if idx.count > 2 {
            for k in 2..<idx.count {
                let a = p[idx[k]]
                var ok = true
                for m in 1..<k where segDist(p[idx[m]], e, a) > STRAIGHT_TOL { ok = false; break }
                if !ok { break }
                last = idx[k]
            }
        }
        if last == idx[0] && n >= 2 { last = idx[1] }
        return (last, kHypot(p[last].x - e.x, p[last].y - e.y))
    }

    private static func move(_ p: inout [InkPoint], _ kind: InkShapeKind?, _ end: Int, _ target: InkPoint) {
        if p.count < 2 { return }
        if kind == .LINE {
            let a = end == 0 ? target : p[0]
            let b = end == 0 ? p[p.count - 1] : target
            p = InkShapes.densify([a, b], false)
            return
        }
        let n = p.count
        let endIdx = end == 0 ? 0 : n - 1
        let (anchor, runLen) = straightRun(p, end)
        let total = length(p)
        if kind == .POLYLINE || runLen >= 0.3 * total {
            let a = p[anchor]
            let range: [Int] = end == 0 ? Array(0..<anchor) : Array((anchor + 1)..<max(n, anchor + 1))
            let count = range.count
            for (k, i) in range.enumerated() {
                let t: Float = end == 0 ? 1 - (Float(k) / Float(count)) : (Float(k) + 1) / Float(count)
                p[i] = InkPoint(a.x + (target.x - a.x) * t, a.y + (target.y - a.y) * t)
            }
            return
        }
        let dx = target.x - p[endIdx].x, dy = target.y - p[endIdx].y
        let span = max(1, min(0.35 * total, 4 * kHypot(dx, dy) + 12))
        var acc: Float = 0
        let order: [Int] = end == 0 ? Array(0..<n) : Array((0..<n).reversed())
        var prev = p[endIdx]
        for i in order {
            acc += kHypot(p[i].x - prev.x, p[i].y - prev.y)
            prev = p[i]
            if acc > span { break }
            let w = 1 - acc / span
            p[i] = InkPoint(p[i].x + dx * w, p[i].y + dy * w)
        }
    }

    private static func extent(_ p: [InkPoint]) -> Float {
        if p.isEmpty { return 0 }
        return max(p.map(\.x).max()! - p.map(\.x).min()!, p.map(\.y).max()! - p.map(\.y).min()!)
    }

    private static func length(_ p: [InkPoint]) -> Float {
        var l: Float = 0
        if p.count > 1 { for i in 1..<p.count { l += kHypot(p[i].x - p[i - 1].x, p[i].y - p[i - 1].y) } }
        return l
    }

    private static func segDist(_ p: InkPoint, _ a: InkPoint, _ b: InkPoint) -> Float {
        let dx = b.x - a.x, dy = b.y - a.y
        let l2 = dx * dx + dy * dy
        if l2 < 1e-6 { return kHypot(p.x - a.x, p.y - a.y) }
        let t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / l2).coerceIn(0, 1)
        return kHypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    struct Circle { let cx: Float, cy: Float, r: Float, rms: Float }

    static func fitCircle(_ points: [InkPoint]) -> Circle? {
        if points.count < 5 { return nil }
        var sx = 0.0, sy = 0.0
        for p in points { sx += Double(p.x) }
        for p in points { sy += Double(p.y) }
        let mx = sx / Double(points.count), my = sy / Double(points.count)
        var suu = 0.0, svv = 0.0, suv = 0.0, suuu = 0.0, svvv = 0.0, suvv = 0.0, svuu = 0.0
        for p in points {
            let u = Double(p.x) - mx, v = Double(p.y) - my
            suu += u * u; svv += v * v; suv += u * v
            suuu += u * u * u; svvv += v * v * v; suvv += u * v * v; svuu += v * u * u
        }
        let det = suu * svv - suv * suv
        if abs(det) < 1e-9 { return nil }
        let bu = 0.5 * (suuu + suvv), bv = 0.5 * (svvv + svuu)
        let uc = (bu * svv - bv * suv) / det, vc = (bv * suu - bu * suv) / det
        let cx = Float(uc + mx), cy = Float(vc + my)
        let r = avg(points.map { kHypot($0.x - cx, $0.y - cy) })
        if !r.isFinite || r <= 0 { return nil }
        var s2 = 0.0
        for p in points { let d = kHypot(p.x - cx, p.y - cy) - r; s2 += Double(d * d) }
        let rms = Float(sqrt(s2 / Double(points.count))) / r
        return Circle(cx: cx, cy: cy, r: r, rms: rms)
    }

    static func sweep(_ points: [InkPoint], _ c: Circle) -> Double {
        var total = 0.0
        var prev = atan2(Double(points[0].y - c.cy), Double(points[0].x - c.cx))
        if points.count > 1 {
            for i in 1..<points.count {
                let a = atan2(Double(points[i].y - c.cy), Double(points[i].x - c.cx))
                var d = a - prev
                while d > Double.pi { d -= 2 * Double.pi }
                while d < -Double.pi { d += 2 * Double.pi }
                total += d; prev = a
            }
        }
        return total
    }

    static func arcOn(_ points: [InkPoint], _ c: Circle) -> [InkPoint] {
        let a0 = atan2(Double(points[0].y - c.cy), Double(points[0].x - c.cx))
        let sw = sweep(points, c)
        let raw = abs(sw) * Double(c.r) / 3.0
        let n = max(8, min(raw.isNaN ? 0 : Int(max(-2147483648, min(2147483647, raw.rounded(.towardZero)))), 400))
        return (0...n).map { i in
            let a = a0 + sw * Double(i) / Double(n)
            return InkPoint(Float(Double(c.cx) + Double(c.r) * cos(a)), Float(Double(c.cy) + Double(c.r) * sin(a)))
        }
    }

    static func arcs(_ strokes: [[InkPoint]]) -> [[InkPoint]?] {
        let usable = strokes.map { $0.count >= 5 }
        if usable.filter({ $0 }).count >= 2 {
            let pts = strokes.indices.filter { usable[$0] }.flatMap { strokes[$0] }
            if let common = fitCircle(pts), common.rms <= ARC_MAX_RMS,
               strokes.indices.allSatisfy({ !usable[$0] || abs(sweep(strokes[$0], common)) >= ARC_MIN_SWEEP }) {
                return strokes.indices.map { usable[$0] ? arcOn(strokes[$0], common) : nil }
            }
        }
        return strokes.map { s in
            if s.count < 5 { return nil }
            guard let c = fitCircle(s), c.rms <= ARC_MAX_RMS, abs(sweep(s, c)) >= ARC_MIN_SWEEP else { return nil }
            return arcOn(s, c)
        }
    }
}
