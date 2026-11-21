import Foundation

// v56 aşama 9C (2E, yalnız Resim): yol işlemleri, şekil oluşturucu, konturu yola çevir, bıçak, yol ayır/kapat/ters
// çevir, efektler ve zarf. Android `V56PathOps.kt` ile aynı hesap.

enum V56Bool {
    struct P: Hashable { var x: Double; var y: Double; init(_ x: Double, _ y: Double) { self.x = x; self.y = y } }
    enum Op { case UNION, INTERSECT, SUBTRACT, EXCLUDE }

    private static let EPS = 1e-7

    static func area(_ r: [P]) -> Double {
        var a = 0.0
        for i in r.indices { let p = r[i], q = r[(i + 1) % r.count]; a += p.x * q.y - q.x * p.y }
        return a / 2
    }

    static func inside(_ p: P, _ rings: [[P]]) -> Bool {
        var c = false
        for r in rings {
            var j = r.count - 1
            for i in r.indices {
                let a = r[i], b = r[j]
                if (a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x { c.toggle() }
                j = i
            }
        }
        return c
    }

    static func normalize(_ rings: [[P]]) -> [[P]] {
        let clean: [[P]] = rings.map { r in
            var out: [P] = []
            for p in r where out.last != p { out.append(p) }
            while out.count > 1 && out.first == out.last { out.removeLast() }
            return out
        }.filter { $0.count >= 3 && abs(area($0)) > 1e-9 }
        return clean.enumerated().map { (i, r) in
            let probe = sample(r)
            let depth = clean.indices.filter { j in j != i && inside(probe, [clean[j]]) }.count
            let positive = area(r) > 0
            return (depth % 2 == 0) == positive ? r : Array(r.reversed())
        }
    }

    private static func sample(_ r: [P]) -> P {
        let a = r[0], b = r[1]
        let mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2
        let len = max(hypot(b.x - a.x, b.y - a.y), 1e-9)
        let nx = -(b.y - a.y) / len, ny = (b.x - a.x) / len
        let s = area(r) > 0 ? 1e-4 : -1e-4
        return P(mx + nx * s, my + ny * s)
    }

    private final class Seg {
        let a: P, b: P
        var cuts: [(Double, P)] = []
        init(_ a: P, _ b: P) { self.a = a; self.b = b }
    }

    private static func segsOf(_ rings: [[P]]) -> [Seg] { rings.flatMap { r in r.indices.map { Seg(r[$0], r[($0 + 1) % r.count]) } } }

    private static func cross(_ ax: Double, _ ay: Double, _ bx: Double, _ by: Double) -> Double { ax * by - ay * bx }

    private static func intersect(_ s: Seg, _ t: Seg) {
        let rx = s.b.x - s.a.x, ry = s.b.y - s.a.y
        let qx = t.b.x - t.a.x, qy = t.b.y - t.a.y
        let den = cross(rx, ry, qx, qy)
        let wx = t.a.x - s.a.x, wy = t.a.y - s.a.y
        let lr = rx * rx + ry * ry, lq = qx * qx + qy * qy
        if lr < EPS * EPS || lq < EPS * EPS { return }
        if abs(den) <= 1e-12 * (lr * lq).squareRoot() {
            if abs(cross(wx, wy, rx, ry)) > 1e-9 * lr.squareRoot() * 1e3 { return }
            func proj(_ p: P, _ o: Seg) -> Double { let dx = o.b.x - o.a.x, dy = o.b.y - o.a.y; return ((p.x - o.a.x) * dx + (p.y - o.a.y) * dy) / (dx * dx + dy * dy) }
            for p in [t.a, t.b] { let u = proj(p, s); if u > 1e-9 && u < 1 - 1e-9 { s.cuts.append((u, p)) } }
            for p in [s.a, s.b] { let u = proj(p, t); if u > 1e-9 && u < 1 - 1e-9 { t.cuts.append((u, p)) } }
            return
        }
        let u = cross(wx, wy, qx, qy) / den
        let v = cross(wx, wy, rx, ry) / den
        if u < -1e-9 || u > 1 + 1e-9 || v < -1e-9 || v > 1 + 1e-9 { return }
        let p: P
        if u <= 1e-9 { p = s.a } else if u >= 1 - 1e-9 { p = s.b } else if v <= 1e-9 { p = t.a } else if v >= 1 - 1e-9 { p = t.b }
        else { p = P(s.a.x + rx * u, s.a.y + ry * u) }
        if u > 1e-9 && u < 1 - 1e-9 { s.cuts.append((u, p)) }
        if v > 1e-9 && v < 1 - 1e-9 { t.cuts.append((v, p)) }
    }

    private final class Edge { let a: P, b: P; var used = false; init(_ a: P, _ b: P) { self.a = a; self.b = b } }

    private static func pieces(_ s: Seg) -> [(P, P)] {
        var pts = [s.a]
        for (_, p) in s.cuts.sorted(by: { $0.0 < $1.0 }) where pts.last != p { pts.append(p) }
        if pts.last != s.b { pts.append(s.b) }
        return (0..<(pts.count - 1)).map { (pts[$0], pts[$0 + 1]) }
    }

    private static func classify(_ a: P, _ b: P, _ other: [Seg], _ otherRings: [[P]]) -> Int {
        let m = P((a.x + b.x) / 2, (a.y + b.y) / 2)
        for o in other {
            let dx = o.b.x - o.a.x, dy = o.b.y - o.a.y
            let l2 = dx * dx + dy * dy
            if l2 < 1e-18 { continue }
            let t = ((m.x - o.a.x) * dx + (m.y - o.a.y) * dy) / l2
            if t < -1e-9 || t > 1 + 1e-9 { continue }
            let px = o.a.x + dx * t, py = o.a.y + dy * t
            if hypot(m.x - px, m.y - py) < 1e-6 {
                let dot = (b.x - a.x) * dx + (b.y - a.y) * dy
                return dot > 0 ? 2 : 3
            }
        }
        return inside(m, otherRings) ? 1 : 0
    }

    static func op(_ a0: [[P]], _ b0: [[P]], _ op: Op) -> [[P]] {
        let a = normalize(a0), b = normalize(b0)
        if a.isEmpty { return op == .UNION || op == .EXCLUDE ? b : [] }
        if b.isEmpty { return op == .INTERSECT ? [] : a }
        let sa = segsOf(a), sb = segsOf(b)
        for s in sa { for t in sb { intersect(s, t) } }
        var edges: [Edge] = []
        for s in sa {
            for (p, q) in pieces(s) {
                let c = classify(p, q, sb, b)
                let keep: Bool
                switch op {
                case .UNION: keep = c == 0 || c == 2
                case .INTERSECT: keep = c == 1 || c == 2
                case .SUBTRACT: keep = c == 0 || c == 3
                case .EXCLUDE: keep = c == 0 || c == 1
                }
                if keep { edges.append(op == .EXCLUDE && c == 1 ? Edge(q, p) : Edge(p, q)) }
            }
        }
        for s in sb {
            for (p, q) in pieces(s) {
                let c = classify(p, q, sa, a)
                if c >= 2 { continue }
                switch op {
                case .UNION: if c == 0 { edges.append(Edge(p, q)) }
                case .INTERSECT: if c == 1 { edges.append(Edge(p, q)) }
                case .SUBTRACT: if c == 1 { edges.append(Edge(q, p)) }
                case .EXCLUDE: edges.append(c == 1 ? Edge(q, p) : Edge(p, q))
                }
            }
        }
        return chain(edges)
    }

    private static func chain(_ edges: [Edge]) -> [[P]] {
        var out: [P: [Edge]] = [:]
        for e in edges { out[e.a, default: []].append(e) }
        var rings: [[P]] = []
        for e0 in edges {
            if e0.used { continue }
            var ring: [P] = []
            var e = e0
            var ok = false
            var guardN = 0
            while guardN < edges.count + 2 {
                guardN += 1
                e.used = true
                ring.append(e.a)
                if e.b == e0.a { ok = true; break }
                let cands = (out[e.b] ?? []).filter { !$0.used }
                if cands.isEmpty { break }
                let inAng = atan2(e.b.y - e.a.y, e.b.x - e.a.x)
                func turn(_ c: Edge) -> Double {
                    var d = atan2(c.b.y - c.a.y, c.b.x - c.a.x) - inAng
                    while d <= -Double.pi { d += 2 * Double.pi }
                    while d > Double.pi { d -= 2 * Double.pi }
                    return d
                }
                e = cands.min(by: { turn($0) < turn($1) })!
            }
            if ok && ring.count >= 3 {
                let simple = simplify(ring)
                if simple.count >= 3 && abs(area(simple)) > 0.01 { rings.append(simple) }
            }
        }
        return rings
    }

    static func simplify(_ r: [P]) -> [P] {
        if r.count < 4 { return r }
        var out: [P] = []
        let n = r.count
        for i in 0..<n {
            let p = r[(i + n - 1) % n], q = r[i], s = r[(i + 1) % n]
            let c = cross(q.x - p.x, q.y - p.y, s.x - q.x, s.y - q.y)
            let l = hypot(q.x - p.x, q.y - p.y) * hypot(s.x - q.x, s.y - q.y)
            if abs(c) > 1e-9 * max(l, 1e-12) || (q.x - p.x) * (s.x - q.x) + (q.y - p.y) * (s.y - q.y) < 0 { out.append(q) }
        }
        return out
    }
}

enum V56PathOps {
    typealias P = V56Bool.P
    typealias Region = (rings: [[P]], src: Stroke)

    enum PathOp: CaseIterable { case UNITE, MINUS_FRONT, INTERSECT, EXCLUDE, DIVIDE, TRIM, MERGE, CROP, OUTLINE }

    static func pt(_ p: StrokePoint) -> P { P(Double(p.x), Double(p.y)) }
    static func sp(_ p: P) -> StrokePoint { StrokePoint(Float(p.x), Float(p.y)) }

    static func paths(_ s: Stroke) -> [([StrokePoint], Bool)] {
        guard let spec = s.shape else { return [(s.points, s.brushType == .LASSO_FILL)] }
        var out: [([StrokePoint], Bool)] = []
        var start = 0
        for sub in spec.subpaths {
            let end = min(s.points.count, start + sub.count)
            if end > start && sub.label == nil { out.append((Array(s.points[start..<end]), sub.closed || sub.fillColorHex != nil)) }
            start = end
        }
        return out
    }

    static func operable(_ s: Stroke) -> Bool {
        if s.hidden { return false }
        switch s.brushType {
        case .SHAPE: return s.shape != nil && s.shape!.graph == nil && s.points.count >= 2
        case .LASSO_FILL: return s.points.count >= 3
        case .TEXT, .IMAGE, .ERASER: return false
        default: return !s.brushType.isRaster && s.points.count >= 2
        }
    }

    static func strokeWidth(_ s: Stroke) -> Float { s.brushType == .SHAPE ? (s.shape?.style.strokeWidth ?? 0) : s.sizePx }

    static func rings(_ s: Stroke) -> [[P]] {
        var out: [[P]] = []
        let width = strokeWidth(s)
        for (pts, closed) in paths(s) {
            if closed && pts.count >= 3 { out.append(dedupe(pts.map(pt))) }
            else if pts.count >= 2 && width > 0 {
                out += outlineRings(pts.map(pt), false, Double(width) / 2, s.style2?.cap ?? .ROUND, s.style2?.join ?? .ROUND)
            }
        }
        return out
    }

    private static func dedupe(_ r: [P]) -> [P] {
        var out: [P] = []
        for p in r where out.last != p { out.append(p) }
        while out.count > 1 && out.first == out.last { out.removeLast() }
        return out
    }

    static func fillOf(_ s: Stroke) -> String {
        if s.brushType == .SHAPE, let f = s.shape?.style.fillColorHex, paths(s).contains(where: { $0.1 }) { return f }
        if s.brushType == .SHAPE { return s.shape?.style.strokeColorHex ?? s.colorHex }
        return s.colorHex
    }

    static func shapeOf(_ rings: [[P]], _ like: Stroke, _ id: String, _ fill: String, _ stroke: ShapeStyle? = nil) -> Stroke? {
        let rs = rings.filter { $0.count >= 3 }
        if rs.isEmpty { return nil }
        let pts = rs.flatMap { $0.map(sp) }
        let style = stroke ?? ShapeStyle(strokeColorHex: fill, strokeWidth: 0, fillColorHex: fill)
        return Stroke(id: id, points: pts, colorHex: fill, brushType: .SHAPE, sizePx: like.sizePx, opacity: like.opacity, order: like.order,
                      groupId: like.groupId, shape: ShapeSpec(kind: "path", subpaths: rs.map { SubPath(count: $0.count, closed: true) }, style: style),
                      clip: like.clip, layerId: like.layerId, mask: like.mask)
    }

    private static func styleOf(_ s: Stroke) -> ShapeStyle? {
        if s.brushType == .SHAPE, s.shape?.style.fillColorHex != nil, paths(s).contains(where: { $0.1 }) { return s.shape!.style }
        return nil
    }
    private static func noStroke(_ s: Stroke) -> ShapeStyle? { styleOf(s).map { var st = $0; st.strokeWidth = 0; return st } }

    private static func fold(_ sel: [Stroke], _ op: V56Bool.Op) -> [[P]] {
        sel.dropFirst().reduce(rings(sel[0])) { acc, s in V56Bool.op(acc, rings(s), op) }
    }

    private static func place(_ strokes: [Stroke], _ sel: [Stroke], _ produced: [Stroke]) -> [Stroke] {
        let removed = Set(sel.map(\.id))
        let firstIndex = strokes.firstIndex { removed.contains($0.id) } ?? 0
        let rest = strokes.filter { !removed.contains($0.id) }
        let at = strokes[0..<firstIndex].filter { !removed.contains($0.id) }.count
        return Array(rest[0..<at]) + produced + Array(rest[at...])
    }

    static func apply(_ strokes: [Stroke], _ ids: Set<String>, _ op: PathOp, newId: () -> String) -> [Stroke]? {
        let sel = strokes.filter { ids.contains($0.id) && operable($0) }
        if sel.isEmpty || (sel.count < 2 && op != .OUTLINE && op != .DIVIDE) { return nil }
        let bottom = sel.first!, top = sel.last!
        var produced: [Stroke] = []
        switch op {
        case .UNITE: if let s = shapeOf(fold(sel, .UNION), top, newId(), fillOf(top), styleOf(top)) { produced = [s] }
        case .INTERSECT: if let s = shapeOf(fold(sel, .INTERSECT), top, newId(), fillOf(top), styleOf(top)) { produced = [s] }
        case .EXCLUDE: if let s = shapeOf(fold(sel, .EXCLUDE), top, newId(), fillOf(top), styleOf(top)) { produced = [s] }
        case .MINUS_FRONT:
            let front = sel.dropFirst().reduce([[P]]()) { acc, s in V56Bool.op(acc, rings(s), .UNION) }
            if let s = shapeOf(V56Bool.op(rings(bottom), front, .SUBTRACT), bottom, newId(), fillOf(bottom), styleOf(bottom)) { produced = [s] }
        case .DIVIDE: produced = divide(sel).compactMap { shapeOf($0.rings, $0.src, newId(), fillOf($0.src), styleOf($0.src)) }
        case .TRIM: produced = trim(sel).compactMap { shapeOf($0.rings, $0.src, newId(), fillOf($0.src), noStroke($0.src)) }
        case .MERGE:
            var keys: [String] = []
            var groups: [String: ([[P]], Stroke)] = [:]
            for (rs, src) in trim(sel) {
                let k = fillOf(src).uppercased()
                if let prev = groups[k] { groups[k] = (V56Bool.op(prev.0, rs, .UNION), src) } else { groups[k] = (rs, src); keys.append(k) }
            }
            produced = keys.compactMap { k in let g = groups[k]!; return shapeOf(g.0, g.1, newId(), fillOf(g.1), noStroke(g.1)) }
        case .CROP:
            let clip = rings(top)
            produced = sel.dropLast().compactMap { s in shapeOf(V56Bool.op(rings(s), clip, .INTERSECT), s, newId(), fillOf(s), noStroke(s)) }
        case .OUTLINE: produced = divide(sel).flatMap { outlinePieces($0.rings, $0.src, newId) }
        }
        if produced.isEmpty { return nil }
        return place(strokes, sel, produced)
    }

    static func divide(_ sel: [Stroke]) -> [Region] {
        var regions: [Region] = []
        var covered: [[P]] = []
        for s in sel {
            let r = rings(s)
            var next: [Region] = []
            for (rs, src) in regions {
                let inter = V56Bool.op(rs, r, .INTERSECT)
                let rest = V56Bool.op(rs, r, .SUBTRACT)
                for g in splitRegions(inter) { next.append((g, s)) }
                for g in splitRegions(rest) { next.append((g, src)) }
            }
            for g in splitRegions(V56Bool.op(r, covered, .SUBTRACT)) { next.append((g, s)) }
            covered = V56Bool.op(covered, r, .UNION)
            regions = next
        }
        return regions
    }

    static func trim(_ sel: [Stroke]) -> [Region] {
        var out: [Region] = []
        var above: [[P]] = []
        for s in sel.reversed() {
            let r = rings(s)
            let visible = V56Bool.op(r, above, .SUBTRACT)
            if !visible.isEmpty { out.append((visible, s)) }
            above = V56Bool.op(above, r, .UNION)
        }
        return out.reversed()
    }

    static func splitRegions(_ rings: [[P]]) -> [[[P]]] {
        if rings.isEmpty { return [] }
        let n = V56Bool.normalize(rings)
        let outers = n.filter { V56Bool.area($0) > 0 }
        let holes = n.filter { V56Bool.area($0) <= 0 }
        if outers.count <= 1 { return [n] }
        var groups = outers.map { [$0] }
        for h in holes {
            let probe = h[0]
            let owners = outers.indices.filter { V56Bool.inside(probe, [outers[$0]]) }
            let owner = owners.min(by: { abs(V56Bool.area(outers[$0])) < abs(V56Bool.area(outers[$1])) })
            groups[owner ?? 0].append(h)
        }
        return groups
    }

    private static func outlinePieces(_ rings: [[P]], _ src: Stroke, _ newId: () -> String) -> [Stroke] {
        if rings.isEmpty { return [] }
        let color = fillOf(src)
        let pts = rings.flatMap { r in (r + [r[0]]).map(sp) }
        return [Stroke(id: newId(), points: pts, colorHex: color, brushType: .SHAPE, sizePx: 1, opacity: src.opacity, order: src.order, groupId: src.groupId,
                       shape: ShapeSpec(kind: "path", subpaths: rings.map { SubPath(count: $0.count + 1) }, style: ShapeStyle(strokeColorHex: color, strokeWidth: 1)),
                       layerId: src.layerId)]
    }

    // MARK: şekil oluşturucu

    static func builderRegions(_ sel: [Stroke]) -> [Region] { divide(sel.filter(operable)) }

    static func regionAt(_ regions: [Region], _ x: Float, _ y: Float) -> Int {
        regions.lastIndex { V56Bool.inside(P(Double(x), Double(y)), $0.rings) } ?? -1
    }

    static func build(_ strokes: [Stroke], _ ids: Set<String>, merge: [Int], remove: Set<Int>, newId: () -> String) -> [Stroke]? {
        let sel = strokes.filter { ids.contains($0.id) && operable($0) }
        if sel.isEmpty { return nil }
        let regions = builderRegions(sel)
        var produced: [Stroke] = []
        let mergeSet = merge.filter { regions.indices.contains($0) && !remove.contains($0) }
        for (i, r) in regions.enumerated() where !remove.contains(i) && !mergeSet.contains(i) {
            if let s = shapeOf(r.rings, r.src, newId(), fillOf(r.src), noStroke(r.src)) { produced.append(s) }
        }
        if let f = mergeSet.first {
            let first = regions[f].src
            let union = mergeSet.dropFirst().reduce(regions[f].rings) { acc, i in V56Bool.op(acc, regions[i].rings, .UNION) }
            if let s = shapeOf(union, first, newId(), fillOf(first), noStroke(first)) { produced.append(s) }
        }
        return place(strokes, sel, produced)
    }

    // MARK: konturu yola çevir

    private static func norm(_ dx: Double, _ dy: Double) -> (Double, Double) { let l = hypot(dx, dy); return l < 1e-12 ? (0, 0) : (dx / l, dy / l) }

    static func offsetLine(_ pts: [P], _ closed: Bool, _ h: Double, _ join: JoinStyle, miterLimit: Double = 4) -> [P] {
        let n = pts.count
        if n < 2 { return pts }
        var out: [P] = []
        func normalAt(_ i: Int) -> (Double, Double) { let a = pts[i], b = pts[(i + 1) % n]; let (ux, uy) = norm(b.x - a.x, b.y - a.y); return (-uy, ux) }
        let segs = closed ? n : n - 1
        for i in 0..<n {
            let p = pts[i]
            let hasPrev = closed || i > 0
            let hasNext = closed || i < n - 1
            if !hasPrev { let (nx, ny) = normalAt(0); out.append(P(p.x + nx * h, p.y + ny * h)); continue }
            if !hasNext { let (nx, ny) = normalAt(segs - 1); out.append(P(p.x + nx * h, p.y + ny * h)); continue }
            let (n1x, n1y) = normalAt((i - 1 + n) % n)
            let (n2x, n2y) = normalAt(i % n)
            let turn = n1x * n2y - n1y * n2x
            let dot = n1x * n2x + n1y * n2y
            if dot > 0.9999 { out.append(P(p.x + n2x * h, p.y + n2y * h)); continue }
            let outer = turn * h < 0
            if !outer || join == .MITER {
                let (mx, my) = norm(n1x + n2x, n1y + n2y)
                let cosHalf = max(mx * n2x + my * n2y, 0.05)
                let len = h / cosHalf
                if !outer || abs(len / h) <= miterLimit { out.append(P(p.x + mx * len, p.y + my * len)); continue }
            }
            if join == .ROUND {
                let a1 = atan2(n1y, n1x), a2 = atan2(n2y, n2x)
                var d = a2 - a1
                while d > Double.pi { d -= 2 * Double.pi }
                while d < -Double.pi { d += 2 * Double.pi }
                let steps = max(2, Int((abs(d) / (Double.pi / 8)).rounded(.up)))
                for k in 0...steps { let a = a1 + d * Double(k) / Double(steps); out.append(P(p.x + cos(a) * h, p.y + sin(a) * h)) }
            } else {
                out.append(P(p.x + n1x * h, p.y + n1y * h)); out.append(P(p.x + n2x * h, p.y + n2y * h))
            }
        }
        return out
    }

    static func outlineRings(_ pts0: [P], _ closed: Bool, _ h: Double, _ cap: CapStyle, _ join: JoinStyle) -> [[P]] {
        var pts = dedupe(pts0)
        if pts.count == 1 { pts = [pts[0], P(pts[0].x + 0.01, pts[0].y)] }
        if pts.count < 2 { return [] }
        if closed && pts.count >= 3 {
            return [offsetLine(pts, true, h, join), offsetLine(pts, true, -h, join).reversed()]
        }
        var ring = offsetLine(pts, false, h, join)
        capPoints(pts[pts.count - 2], pts[pts.count - 1], h, cap, &ring)
        ring += offsetLine(pts, false, -h, join).reversed()
        capPoints(pts[1], pts[0], h, cap, &ring)
        return [ring]
    }

    private static func capPoints(_ from: P, _ end: P, _ h: Double, _ cap: CapStyle, _ out: inout [P]) {
        let (ux, uy) = norm(end.x - from.x, end.y - from.y)
        let nx = -uy, ny = ux
        switch cap {
        case .BUTT: break
        case .SQUARE:
            out.append(P(end.x + nx * h + ux * h, end.y + ny * h + uy * h)); out.append(P(end.x - nx * h + ux * h, end.y - ny * h + uy * h))
        case .ROUND:
            let a0 = atan2(ny, nx)
            for k in 1..<8 { let a = a0 - Double.pi * Double(k) / 8; out.append(P(end.x + cos(a) * h, end.y + sin(a) * h)) }
        }
    }

    static func outlineStroke(_ strokes: [Stroke], _ ids: Set<String>, newId: () -> String) -> [Stroke]? {
        var changed = false
        let out = strokes.flatMap { s -> [Stroke] in
            if !ids.contains(s.id) || !operable(s) || s.brushType == .LASSO_FILL { return [s] }
            let w = strokeWidth(s)
            if w <= 0 { return [s] }
            let color = s.brushType == .SHAPE ? s.shape!.style.strokeColorHex : s.colorHex
            let rs = paths(s).flatMap { outlineRings($0.0.map(pt), $0.1, Double(w) / 2, s.style2?.cap ?? .ROUND, s.style2?.join ?? .ROUND) }
            guard let outline = shapeOf(rs, s, newId(), color) else { return [s] }
            changed = true
            var result: [Stroke] = []
            if s.brushType == .SHAPE, s.shape!.style.fillColorHex != nil { var f = s; f.shape!.style.strokeWidth = 0; result.append(f) }
            result.append(outline)
            return result
        }
        return changed ? out : nil
    }

    // MARK: bıçak, ayır, kapat, ters çevir

    static func knife(_ strokes: [Stroke], _ ids: Set<String>, _ cut: [StrokePoint], newId: () -> String, locked: Set<String> = []) -> [Stroke]? {
        if cut.count < 2 { return nil }
        let blade = outlineRings(cut.map(pt), false, 0.02, .BUTT, .MITER)
        var changed = false
        let out = strokes.flatMap { s -> [Stroke] in
            if (!ids.isEmpty && !ids.contains(s.id)) || !operable(s) || locked.contains(s.layerId ?? "\u{0}") || (s.brushType != .SHAPE && s.brushType != .LASSO_FILL) { return [s] }
            let ps = paths(s)
            let closedAll = !ps.isEmpty && ps.allSatisfy { $0.1 }
            if closedAll {
                let parts = splitRegions(V56Bool.op(ps.map { dedupe($0.0.map(pt)) }, blade, .SUBTRACT))
                if parts.count < 2 { return [s] }
                changed = true
                return parts.compactMap { shapeOf($0, s, newId(), fillOf(s), styleOf(s) ?? s.shape?.style) }
            }
            let pieces = ps.flatMap { cutOpen($0.0, $0.1, cut) }
            if pieces.count <= ps.count { return [s] }
            changed = true
            var style = s.shape?.style ?? ShapeStyle(strokeColorHex: s.colorHex, strokeWidth: s.sizePx)
            style.fillColorHex = nil
            return pieces.map { pts in
                var n = Stroke(id: newId(), points: pts, colorHex: s.colorHex, brushType: .SHAPE, sizePx: s.sizePx, opacity: s.opacity, order: s.order,
                               groupId: s.groupId, shape: ShapeSpec(kind: "path", subpaths: [SubPath(count: pts.count)], style: style), layerId: s.layerId)
                if var st = s.style2 { st.effects = []; st.effectBase = nil; n.style2 = st }
                return n
            }
        }
        return changed ? out : nil
    }

    private static func cutOpen(_ pts0: [StrokePoint], _ closed: Bool, _ cut: [StrokePoint]) -> [[StrokePoint]] {
        let pts = closed && pts0.count >= 3 ? pts0 + [pts0[0]] : pts0
        var pieces: [[StrokePoint]] = []
        var cur = [pts[0]]
        for i in 0..<(pts.count - 1) {
            let a = pts[i], b = pts[i + 1]
            var hits: [(Float, StrokePoint)] = []
            for j in 0..<(cut.count - 1) { if let h = segHit(a, b, cut[j], cut[j + 1]) { hits.append(h) } }
            for (_, p) in hits.sorted(by: { $0.0 < $1.0 }) { cur.append(p); pieces.append(cur); cur = [p] }
            cur.append(b)
        }
        pieces.append(cur)
        let valid = pieces.filter { $0.count >= 2 }
        if closed && valid.count >= 3 { return [valid.last! + valid.first!.dropFirst()] + Array(valid[1..<(valid.count - 1)]) }
        return valid
    }

    private static func segHit(_ a: StrokePoint, _ b: StrokePoint, _ c: StrokePoint, _ d: StrokePoint) -> (Float, StrokePoint)? {
        let rx = b.x - a.x, ry = b.y - a.y, qx = d.x - c.x, qy = d.y - c.y
        let den = rx * qy - ry * qx
        if abs(den) < 1e-9 { return nil }
        let wx = c.x - a.x, wy = c.y - a.y
        let u = (wx * qy - wy * qx) / den, v = (wx * ry - wy * rx) / den
        if u <= 1e-4 || u >= 1 - 1e-4 || v < 0 || v > 1 { return nil }
        return (u, a.at(a.x + rx * u, a.y + ry * u))
    }

    static func separate(_ strokes: [Stroke], _ ids: Set<String>, newId: () -> String) -> [Stroke]? {
        var changed = false
        let out = strokes.flatMap { s -> [Stroke] in
            guard ids.contains(s.id), let spec = s.shape, spec.subpaths.count >= 2 else { return [s] }
            changed = true
            var start = 0
            return spec.subpaths.compactMap { sub -> Stroke? in
                let end = min(s.points.count, start + sub.count)
                let pts = Array(s.points[start..<end])
                start = end
                if pts.isEmpty { return nil }
                var n = s
                n.id = newId(); n.points = pts; n.shape = spec; n.shape!.subpaths = [sub]; n.cloneOf = nil; n.`repeat` = nil
                if var st = n.style2 { st.effects = []; st.effectBase = nil; n.style2 = st }
                return n
            }
        }
        return changed ? out : nil
    }

    static func closePaths(_ strokes: [Stroke], _ ids: Set<String>) -> [Stroke]? {
        var changed = false
        let out = strokes.map { s -> Stroke in
            guard ids.contains(s.id), let spec = s.shape, spec.subpaths.contains(where: { !$0.closed && $0.count >= 3 }) else { return s }
            changed = true
            var n = s
            n.shape!.subpaths = spec.subpaths.map { sub in var c = sub; if c.count >= 3 { c.closed = true }; return c }
            return n
        }
        return changed ? out : nil
    }

    static func reversePaths(_ strokes: [Stroke], _ ids: Set<String>) -> [Stroke]? {
        var changed = false
        let out = strokes.map { s -> Stroke in
            if !ids.contains(s.id) || s.points.count < 2 || !operable(s) { return s }
            changed = true
            var n = s
            guard let spec = s.shape else { n.points = s.points.reversed(); return n }
            var pts: [StrokePoint] = []
            var start = 0
            for sub in spec.subpaths { let end = min(s.points.count, start + sub.count); pts += s.points[start..<end].reversed(); start = end }
            if start < s.points.count { pts += s.points[start...] }
            n.points = pts
            return n
        }
        return changed ? out : nil
    }

    // MARK: efektler

    static func resample(_ pts: [P], _ closed: Bool, _ spacing: Double) -> [P] {
        if pts.count < 2 || spacing <= 0 { return pts }
        let src = closed ? pts + [pts[0]] : pts
        var out = [src[0]]
        for i in 0..<(src.count - 1) {
            let a = src[i], b = src[i + 1]
            let len = hypot(b.x - a.x, b.y - a.y)
            let k = max(1, Int((len / spacing).rounded(.up)))
            for j in 1...k { let t = Double(j) / Double(k); out.append(P(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)) }
        }
        if closed { out.removeLast() }
        return out
    }

    private static func normals(_ pts: [P], _ closed: Bool) -> [(Double, Double)] {
        pts.indices.map { i in
            let prev = i > 0 ? pts[i - 1] : (closed ? pts[pts.count - 1] : pts[i])
            let next = i < pts.count - 1 ? pts[i + 1] : (closed ? pts[0] : pts[i])
            let (ux, uy) = norm(next.x - prev.x, next.y - prev.y)
            return (-uy, ux)
        }
    }

    /// Deterministik sözde rastgele (-1..1); Android ile aynı.
    static func noise(_ seed: Int, _ i: Int) -> Double {
        let mask: UInt64 = 0xFFFF_FFFF
        var x = (UInt64(bitPattern: Int64(seed)) &* 374_761_393 &+ UInt64(bitPattern: Int64(i)) &* 668_265_263) & mask
        x = ((x ^ (x >> 13)) &* 1_274_126_177) & mask
        x = x ^ (x >> 16)
        return Double(x % 20001) / 10000.0 - 1.0
    }

    static func effect(_ paths: [([P], Bool)], _ e: VectorEffect) -> [([P], Bool)] {
        if paths.isEmpty { return paths }
        let all = paths.flatMap { $0.0 }
        let l = all.map(\.x).min()!, r = all.map(\.x).max()!, t = all.map(\.y).min()!, b = all.map(\.y).max()!
        switch e.kind {
        case .OFFSET:
            return paths.map { (pts, closed) in
                if closed && pts.count >= 3 {
                    let outward: Double = V56Bool.area(pts) > 0 ? -1 : 1
                    return (offsetLine(pts, true, Double(e.amount) * outward, .MITER), true)
                }
                return (offsetLine(pts, false, Double(e.amount), .MITER), false)
            }
        case .BEND:
            let w = max(r - l, 1e-6), cx = (l + r) / 2
            let k = min(max(Double(e.amount), -1), 1)
            if abs(k) < 1e-4 { return paths }
            let radius = w / (k * Double.pi)
            return paths.map { (pts, closed) in
                (resample(pts, closed, max(w, b - t) / 80).map { p in
                    let a = (p.x - cx) / radius
                    let rr = radius - (p.y - t)
                    return P(cx + rr * sin(a), t + radius - rr * cos(a))
                }, closed)
            }
        case .ROUGHEN:
            let spacing = 100.0 / Double(min(max(e.detail, 1), 100))
            var idx = 0
            return paths.map { (pts, closed) in
                (resample(pts, closed, spacing).map { p in
                    let dx = noise(e.seed, idx * 2) * Double(e.size), dy = noise(e.seed, idx * 2 + 1) * Double(e.size)
                    idx += 1
                    return P(p.x + dx, p.y + dy)
                }, closed)
            }
        case .ZIGZAG, .WAVE:
            let spacing = 100.0 / Double(min(max(e.detail, 1), 100)) / 2
            let wave = e.kind == .WAVE
            return paths.map { (pts, closed) in
                let rs = resample(pts, closed, spacing / (wave ? 4 : 1))
                let ns = normals(rs, closed)
                let step = wave ? 4.0 : 1.0
                let moved = rs.enumerated().map { (i, p) -> P in
                    let (nx, ny) = ns[i]
                    let amp = wave ? sin(Double(i) * Double.pi / (2 * step) * 2) * Double(e.size) : (i % 2 == 0 ? 1.0 : -1.0) * Double(e.size)
                    if !closed && (i == 0 || i == rs.count - 1) { return p }
                    return P(p.x + nx * amp, p.y + ny * amp)
                }
                return (moved, closed)
            }
        }
    }

    static func withEffects(_ s: Stroke, _ effects: [VectorEffect]) -> Stroke {
        var style = s.style2 ?? VectorStyle()
        let base = style.effectBase ?? EffectBase(points: s.points, counts: s.shape?.subpaths.map(\.count))
        var baseStroke = restore(s, base)
        if effects.isEmpty {
            style.effects = []; style.effectBase = nil
            baseStroke.style2 = style.isDefault ? nil : style
            return baseStroke
        }
        var ps = paths(baseStroke).map { ($0.0.map(pt), $0.1) }
        for e in effects { ps = effect(ps, e) }
        var out = baseStroke
        out.points = ps.flatMap { $0.0.map(sp) }
        if let spec = baseStroke.shape {
            let visible = spec.subpaths.filter { $0.label == nil }
            out.shape!.subpaths = ps.enumerated().map { (i, p) in
                var sub = i < visible.count ? visible[i] : SubPath(count: p.0.count)
                sub.count = p.0.count; sub.closed = p.1
                return sub
            }
        }
        style.effects = effects; style.effectBase = base
        out.style2 = style
        return out
    }

    private static func restore(_ s: Stroke, _ base: EffectBase) -> Stroke {
        var n = s
        n.points = base.points
        if let spec = s.shape, let counts = base.counts {
            if counts.count == spec.subpaths.count {
                n.shape!.subpaths = spec.subpaths.enumerated().map { (i, sub) in var c = sub; c.count = counts[i]; return c }
            } else {
                n.shape!.subpaths = counts.map { SubPath(count: $0) }
            }
        }
        return n
    }

    static func effectable(_ s: Stroke) -> Bool { operable(s) && s.brushType != .LASSO_FILL }

    // MARK: zarf bozma

    static func envelope(_ strokes: [Stroke], _ ids: Set<String>) -> [Stroke]? {
        let sel = strokes.filter { ids.contains($0.id) && operable($0) }
        if sel.count < 2 { return nil }
        let env = sel.last!
        guard let ring0 = paths(env).first(where: { $0.1 })?.0 else { return nil }
        let ring = dedupe(ring0.map(pt))
        if ring.count < 4 { return nil }
        let targets = Array(sel.dropLast())
        guard let box = V56Vector.pointBounds(targets) else { return nil }
        let bw = max(box[2] - box[0], 1e-3), bh = max(box[3] - box[1], 1e-3)
        let sd = sides(ring)
        let top = sd[0], right = sd[1], bottom = sd[2], left = sd[3]
        func at(_ side: [P], _ u: Double) -> P {
            var lens = [Double](repeating: 0, count: side.count)
            for i in 1..<side.count { lens[i] = lens[i - 1] + hypot(side[i].x - side[i - 1].x, side[i].y - side[i - 1].y) }
            let total = max(lens.last!, 1e-9)
            let target = min(max(u, 0), 1) * total
            var i = 1
            while i < side.count - 1 && lens[i] < target { i += 1 }
            let seg = max(lens[i] - lens[i - 1], 1e-9)
            let t = min(max((target - lens[i - 1]) / seg, 0), 1)
            return P(side[i - 1].x + (side[i].x - side[i - 1].x) * t, side[i - 1].y + (side[i].y - side[i - 1].y) * t)
        }
        let c00 = top.first!, c10 = top.last!, c11 = bottom.last!, c01 = bottom.first!
        func map(_ p: StrokePoint) -> StrokePoint {
            let u = Double((p.x - box[0]) / bw), v = Double((p.y - box[1]) / bh)
            let tp = at(top, u), bp = at(bottom, u), lp = at(left, v), rp = at(right, v)
            let x = (1 - v) * tp.x + v * bp.x + (1 - u) * lp.x + u * rp.x - ((1 - u) * (1 - v) * c00.x + u * (1 - v) * c10.x + u * v * c11.x + (1 - u) * v * c01.x)
            let y = (1 - v) * tp.y + v * bp.y + (1 - u) * lp.y + u * rp.y - ((1 - u) * (1 - v) * c00.y + u * (1 - v) * c10.y + u * v * c11.y + (1 - u) * v * c01.y)
            return p.at(Float(x), Float(y))
        }
        let ids2 = Set(targets.map(\.id))
        return strokes.filter { $0.id != env.id }.map { s in
            if !ids2.contains(s.id) { return s }
            var d = densify(s, max(bw, bh) / 60)
            d.points = d.points.map(map)
            d.style2 = V56Vector.mapStyle(d.style2, map)
            return d
        }
    }

    private static func sides(_ ring0: [P]) -> [[P]] {
        let ring: [P] = V56Bool.area(ring0) < 0 ? Array(ring0.reversed()) : ring0
        let l = ring.map(\.x).min()!, r = ring.map(\.x).max()!, t = ring.map(\.y).min()!, b = ring.map(\.y).max()!
        func nearest(_ x: Double, _ y: Double) -> Int { ring.indices.min(by: { hypot(ring[$0].x - x, ring[$0].y - y) < hypot(ring[$1].x - x, ring[$1].y - y) })! }
        let iTL = nearest(l, t), iTR = nearest(r, t), iBR = nearest(r, b), iBL = nearest(l, b)
        func walk(_ from: Int, _ to: Int) -> [P] {
            var out = [ring[from]]; var i = from; var g = 0
            while i != to && g < ring.count { g += 1; i = (i + 1) % ring.count; out.append(ring[i]) }
            return out
        }
        return [walk(iTL, iTR), walk(iTR, iBR), walk(iBR, iBL).reversed(), walk(iBL, iTL).reversed()]
    }

    static func densify(_ s: Stroke, _ step: Float) -> Stroke {
        if step <= 0 { return s }
        var n = s
        guard let spec = s.shape else { n.points = resample(s.points.map(pt), false, Double(step)).map(sp); return n }
        if spec.subpaths.contains(where: { $0.label != nil }) { return s }
        let newPaths = paths(s).map { resample($0.0.map(pt), $0.1, Double(step)).map(sp) }
        n.points = newPaths.flatMap { $0 }
        n.shape!.subpaths = newPaths.enumerated().map { (i, pp) in var c = spec.subpaths[i]; c.count = pp.count; return c }
        return n
    }
}
