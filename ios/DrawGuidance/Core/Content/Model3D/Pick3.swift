import Foundation

/// Işının değdiği yer.
struct Hit3: Equatable { let part: Int, face: Int, t: Float, point: V3 }

/// Ekranda seçilen köşe/kenar/çizgi.
struct ScreenPick: Equatable { let part: Int, index: Int, distance: Float, visible: Bool }

/// v24 3B seçim (Android `Pick3`).
enum Pick3 {

    static func pick(_ model: Model3D, _ view: View3, _ sx: Float, _ sy: Float) -> Hit3? {
        let (o, d) = view.ray(sx, sy)
        return ray(model, o, d)
    }

    static func ray(_ model: Model3D, _ o: V3, _ d: V3) -> Hit3? {
        var best: Hit3?
        for (pi, part) in model.parts.enumerated() {
            if !part.visible || part.mesh.faceCount == 0 { continue }
            guard let hit = rayPart(part, o, d) else { continue }
            if best == nil || hit.1 < best!.t { best = Hit3(part: pi, face: hit.0, t: hit.1, point: o + d * hit.1) }
        }
        return best
    }

    static func rayPart(_ part: Part, _ o: V3, _ d: V3) -> (Int, Float)? {
        guard let inv = Inverse3.of(Transform3.of(part)) else { return nil }
        let lo = inv.point(o), ld = inv.dir(d)
        if !slab(lo, ld, part.mesh.bounds) { return nil }
        let m = part.mesh
        let p = m.pos, tri = m.triangles
        var bestT = Float.infinity, bestF = -1
        for f in 0..<m.faceCount {
            let face = m.faces[f], tris = tri[f]
            var k = 0
            while k + 2 < tris.count {
                let a = face[tris[k]] * 3, b = face[tris[k + 1]] * 3, c = face[tris[k + 2]] * 3
                let t = triangle(lo, ld, p[a], p[a + 1], p[a + 2], p[b], p[b + 1], p[b + 2], p[c], p[c + 1], p[c + 2])
                if t > 1e-5 && t < bestT { bestT = t; bestF = f }
                k += 3
            }
        }
        return bestF >= 0 ? (bestF, bestT) : nil
    }

    private static func triangle(_ o: V3, _ d: V3, _ ax: Float, _ ay: Float, _ az: Float, _ bx: Float, _ by: Float, _ bz: Float,
                                 _ cx: Float, _ cy: Float, _ cz: Float) -> Float {
        let e1x = bx - ax, e1y = by - ay, e1z = bz - az
        let e2x = cx - ax, e2y = cy - ay, e2z = cz - az
        let px = d.y * e2z - d.z * e2y, py = d.z * e2x - d.x * e2z, pz = d.x * e2y - d.y * e2x
        let det = e1x * px + e1y * py + e1z * pz
        if abs(det) < 1e-12 { return -1 }
        let inv = 1 / det
        let tx = o.x - ax, ty = o.y - ay, tz = o.z - az
        let u = (tx * px + ty * py + tz * pz) * inv
        if u < -1e-6 || u > 1 + 1e-6 { return -1 }
        let qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x
        let v = (d.x * qx + d.y * qy + d.z * qz) * inv
        if v < -1e-6 || u + v > 1 + 1e-6 { return -1 }
        return (e2x * qx + e2y * qy + e2z * qz) * inv
    }

    private static func slab(_ o: V3, _ d: V3, _ b: Box3) -> Bool {
        var t0 = -Float.infinity, t1 = Float.infinity
        let pad = Swift.max(1e-4, b.size.length * 0.01)
        for axis in 0...2 {
            let oo = o.get(axis), dd = d.get(axis)
            let lo = b.min.get(axis) - pad, hi = b.max.get(axis) + pad
            if abs(dd) < 1e-12 {
                if oo < lo || oo > hi { return false }
            } else {
                var a = (lo - oo) / dd, c = (hi - oo) / dd
                if a > c { swap(&a, &c) }
                t0 = Swift.max(t0, a); t1 = Swift.min(t1, c)
                if t0 > t1 { return false }
            }
        }
        return t1 > 0
    }

    static func visible(_ model: Model3D, _ view: View3, _ p: V3, _ ignorePart: Int = -1) -> Bool {
        let s = view.project(p)
        if s[3] <= 0 { return false }
        let (o, d) = view.ray(s[0], s[1])
        let tp = (p - o).dot(d)
        if tp <= 0 { return false }
        let eps = Swift.max(1e-3, tp * 0.004)
        for (pi, part) in model.parts.enumerated() {
            if !part.visible || pi == ignorePart { continue }
            if let hit = rayPart(part, o, d), hit.1 < tp - eps { return false }
        }
        if ignorePart >= 0, let hit = rayPart(model.parts[ignorePart], o, d), hit.1 < tp - eps { return false }
        return true
    }

    static func worldVertices(_ part: Part) -> [Float] {
        let t = Transform3.of(part)
        let n = part.mesh.vertexCount
        var out = [Float](repeating: 0, count: n * 3)
        let p = part.mesh.pos
        for i in 0..<n { t.apply(p[3 * i], p[3 * i + 1], p[3 * i + 2], &out, 3 * i) }
        return out
    }

    static func vertex(_ model: Model3D, _ view: View3, _ partIndex: Int, _ sx: Float, _ sy: Float, _ radius: Float) -> ScreenPick? {
        guard model.parts.indices.contains(partIndex) else { return nil }
        let part = model.parts[partIndex]
        let w = worldVertices(part)
        var s = [Float](repeating: 0, count: 4)
        var cands: [(Int, Float)] = []
        for i in 0..<part.mesh.vertexCount {
            view.project(w[3 * i], w[3 * i + 1], w[3 * i + 2], &s)
            if s[3] <= 0 { continue }
            let dd = ((s[0] - sx) * (s[0] - sx) + (s[1] - sy) * (s[1] - sy)).squareRoot()
            if dd <= radius { cands.append((i, dd)) }
        }
        func dep(_ i: Int) -> Float { view.depth(w[3 * i], w[3 * i + 1], w[3 * i + 2]) }
        let sorted = cands.enumerated().sorted { x, y in
            let bx = Int(x.element.1 / 1.5), by = Int(y.element.1 / 1.5)
            if bx != by { return bx < by }
            let dx = dep(x.element.0), dy = dep(y.element.0)
            if dx != dy { return dx > dy }
            return x.offset < y.offset
        }.map { $0.element }
        var hidden: ScreenPick?
        for (i, dd) in sorted.prefix(8) {
            let p = V3(w[3 * i], w[3 * i + 1], w[3 * i + 2])
            if visible(model, view, p, partIndex) { return ScreenPick(part: partIndex, index: i, distance: dd, visible: true) }
            if hidden == nil { hidden = ScreenPick(part: partIndex, index: i, distance: dd, visible: false) }
        }
        return hidden
    }

    static func edge(_ model: Model3D, _ view: View3, _ partIndex: Int, _ sx: Float, _ sy: Float, _ radius: Float) -> (EdgeRef, ScreenPick)? {
        guard model.parts.indices.contains(partIndex) else { return nil }
        let part = model.parts[partIndex]
        let w = worldVertices(part)
        let e = part.mesh.edges
        var sa = [Float](repeating: 0, count: 4), sb = sa
        var cands: [(Int, Float, Float)] = []
        var i = 0
        while i < e.count {
            let a = e[i], b = e[i + 1]
            view.project(w[3 * a], w[3 * a + 1], w[3 * a + 2], &sa)
            view.project(w[3 * b], w[3 * b + 1], w[3 * b + 2], &sb)
            if sa[3] > 0 && sb[3] > 0 {
                let dx = sb[0] - sa[0], dy = sb[1] - sa[1]
                let len2 = dx * dx + dy * dy
                let t: Float = len2 < 1e-6 ? 0 : (((sx - sa[0]) * dx + (sy - sa[1]) * dy) / len2).coerceIn(0, 1)
                let px = sa[0] + dx * t, py = sa[1] + dy * t
                let dd = ((px - sx) * (px - sx) + (py - sy) * (py - sy)).squareRoot()
                if dd <= radius { cands.append((i / 4, dd, t)) }
            }
            i += 4
        }
        func depthOf(_ k: Int) -> Float {
            let a = e[4 * k], b = e[4 * k + 1]
            return view.depth((w[3 * a] + w[3 * b]) / 2, (w[3 * a + 1] + w[3 * b + 1]) / 2, (w[3 * a + 2] + w[3 * b + 2]) / 2)
        }
        let sorted = cands.enumerated().sorted { x, y in
            let bx = Int(x.element.1 / 1.5), by = Int(y.element.1 / 1.5)
            if bx != by { return bx < by }
            let dx = depthOf(x.element.0), dy = depthOf(y.element.0)
            if dx != dy { return dx > dy }
            return x.offset < y.offset
        }.map { $0.element }
        var hidden: (EdgeRef, ScreenPick)?
        for (k, dd, t) in sorted.prefix(8) {
            let a = e[4 * k], b = e[4 * k + 1]
            let p = V3(w[3 * a] + (w[3 * b] - w[3 * a]) * t, w[3 * a + 1] + (w[3 * b + 1] - w[3 * a + 1]) * t, w[3 * a + 2] + (w[3 * b + 2] - w[3 * a + 2]) * t)
            let ref = EdgeRef(a, b)
            if visible(model, view, p, partIndex) { return (ref, ScreenPick(part: partIndex, index: k, distance: dd, visible: true)) }
            if hidden == nil { hidden = (ref, ScreenPick(part: partIndex, index: k, distance: dd, visible: false)) }
        }
        return hidden
    }

    static func edgeParam(_ model: Model3D, _ view: View3, _ partIndex: Int, _ edge: EdgeRef, _ sx: Float, _ sy: Float) -> Float {
        guard model.parts.indices.contains(partIndex) else { return 0.5 }
        let part = model.parts[partIndex]
        let t = Transform3.of(part)
        let sa = view.project(t.apply(part.mesh.vertex(edge.a))), sb = view.project(t.apply(part.mesh.vertex(edge.b)))
        let dx = sb[0] - sa[0], dy = sb[1] - sa[1]
        let len2 = dx * dx + dy * dy
        return len2 < 1e-6 ? 0.5 : (((sx - sa[0]) * dx + (sy - sa[1]) * dy) / len2).coerceIn(0.05, 0.95)
    }

    static func stroke(_ model: Model3D, _ view: View3, _ sx: Float, _ sy: Float, _ radius: Float) -> Int? {
        var s = [Float](repeating: 0, count: 4)
        var best = -1, bestD = radius
        for (si, st) in model.strokes.enumerated() {
            for i in 0..<st.count {
                view.project(st.points[3 * i], st.points[3 * i + 1], st.points[3 * i + 2], &s)
                if s[3] <= 0 { continue }
                let dd = ((s[0] - sx) * (s[0] - sx) + (s[1] - sy) * (s[1] - sy)).squareRoot()
                if dd < bestD { bestD = dd; best = si }
            }
        }
        return best >= 0 ? best : nil
    }
}

/// Parça dönüşümünün tersi (dünyadan nesne uzayına).
struct Inverse3 {
    private let a: [Float], t: [Float]

    func point(_ p: V3) -> V3 {
        let x = p.x - t[0], y = p.y - t[1], z = p.z - t[2]
        return V3(a[0] * x + a[1] * y + a[2] * z, a[3] * x + a[4] * y + a[5] * z, a[6] * x + a[7] * y + a[8] * z)
    }

    func dir(_ d: V3) -> V3 { V3(a[0] * d.x + a[1] * d.y + a[2] * d.z, a[3] * d.x + a[4] * d.y + a[5] * d.z, a[6] * d.x + a[7] * d.y + a[8] * d.z) }

    static func of(_ tr: Transform3) -> Inverse3? {
        let m = tr.m
        let a = m[0], b = m[1], c = m[2]
        let d = m[4], e = m[5], f = m[6]
        let g = m[8], h = m[9], i = m[10]
        let det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        if abs(det) < 1e-12 { return nil }
        let k = 1 / det
        let inv: [Float] = [(e * i - f * h) * k, (c * h - b * i) * k, (b * f - c * e) * k,
                            (f * g - d * i) * k, (a * i - c * g) * k, (c * d - a * f) * k,
                            (d * h - e * g) * k, (b * g - a * h) * k, (a * e - b * d) * k]
        return Inverse3(a: inv, t: [m[3], m[7], m[11]])
    }
}
