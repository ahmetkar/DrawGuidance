import Foundation

/// v25 3B fırça türleri.
enum Brush3Kind: Int, CaseIterable { case LINE, TUBE, RIBBON, WALL, LATHE, INFLATE }

/// 3B fırçanın cisim ayarları (dünya biriminde).
struct BrushShapeSpec: Hashable {
    var kind: Brush3Kind
    var width: Float
    var height: Float = 0.5
    var bulge: Float = 0.8
    var taper: Float = 0.3
    var smoothing: Float = 0.4
    var pressure: Bool = true
    var sides: Int = 10
    var maxSamples: Int = 160
}

/// Çizim düzlemindeki fırça izi.
struct BrushInput {
    let points: [V3]
    let pressures: [Float]?
    let normal: V3
    let up: V3
    var closed: Bool? = nil
    var axis: V3? = nil
}

/// v25 3B fırçanın cisimleri (Android `BrushShapes`).
enum BrushShapes {

    static func frame(_ normal: V3, _ upHint: V3) -> (V3, V3, V3) {
        let n = normal.normalized()
        let u0 = (upHint - n * upHint.dot(n)).normalized()
        let up = u0.length > 0.5 ? u0 : perpendicular(n)
        return (n, up, up.cross(n).normalized())
    }

    static func isClosed(_ raw: [V3], _ spec: BrushShapeSpec) -> Bool {
        let rawLength = pathLength(raw)
        return raw.count > 6 && dist(raw.first!, raw.last!) < Swift.max(spec.width * 1.5, rawLength * 0.08) && rawLength > spec.width * 4
    }

    static func build(_ input: BrushInput, _ spec: BrushShapeSpec) -> Mesh? {
        if input.points.count < 2 || spec.kind == .LINE { return nil }
        let (n, up, right) = frame(input.normal, input.up)
        let raw = input.points
        let rawLength = pathLength(raw)
        if rawLength < spec.width * 0.6 && spec.kind != .TUBE { return nil }
        let closed = input.closed ?? isClosed(raw, spec)
        var ring = raw
        if closed && raw.count > 3 {
            // Kotlin dropLastWhile { it !== raw.first() && yakın }: ilk öğe (aynı nesne) asla atılmaz
            while ring.count > 1 && dist(ring.last!, raw.first!) < spec.width * 0.25 { ring.removeLast() }
        }
        let smoothed = smooth(ring, javaRoundF(spec.smoothing.coerceIn(0, 1) * 5), closed && spec.kind != .LATHE)
        let length = pathLength(smoothed)
        let spacing = Swift.max(spec.width * 0.5, length / Float(Swift.max(spec.maxSamples, 8)))
        let (pts, prs) = resample(smoothed, input.pressures, spacing, closed && spec.kind != .LATHE)
        if pts.count < 2 { return nil }
        let mesh: Mesh?
        switch spec.kind {
        case .TUBE: mesh = tube(pts, widths(pts.count, prs, spec, closed).map { $0 * 0.5 }, spec.sides.coerceIn(6, 24), closed)
        case .RIBBON: mesh = strip(pts, widths(pts.count, prs, spec, closed).map { $0 * 0.5 }, n, 0, Swift.max(spec.width * 0.12, 0.004), closed)
        case .WALL:
            mesh = closed ? prism(pts, n, right, up, spec.height.coerceAtLeast(0.01))
                : strip(pts, [Float](repeating: spec.width * 0.5, count: pts.count), n, 0, spec.height.coerceAtLeast(0.01), false)
        case .LATHE: mesh = lathe(pts, n, right, up, spec.sides.coerceIn(8, 48) * 2, input.axis ?? pts[0])
        case .INFLATE: mesh = inflate(pts, n, right, up, spec.bulge.coerceIn(0.2, 1.5), spec.maxSamples >= 200 ? 3 : 2)
        case .LINE: mesh = nil
        }
        guard let mesh, mesh.faceCount > 0 else { return nil }
        return Generators3D.fixOrientation(mesh)
    }

    static func toPart(_ world: Mesh, _ id: String, _ name: String, _ material: Material, _ source: BrushSource? = nil) -> Part {
        let b = world.bounds
        let origin = V3(b.center.x, b.min.y, b.center.z)
        let mesh = world.mapPositions { $0 - origin }
        return Part(id, name, mesh, nil, position: origin, material: material, smooth: true,
                    brush: source?.shifted(origin).signed(mesh))
    }

    static func rebuild(_ source: BrushSource) -> Mesh? { build(source.input(), source.spec) }

    // MARK: iz hazırlığı

    private static func perpendicular(_ n: V3) -> V3 {
        let h = abs(n.y) < 0.9 ? V3.UP : V3(0, 0, -1)
        return (h - n * h.dot(n)).normalized()
    }

    private static func dist(_ a: V3, _ b: V3) -> Float { (a - b).length }

    static func pathLength(_ p: [V3]) -> Float {
        var l: Float = 0
        if p.count > 1 { for i in 1..<p.count { l += dist(p[i - 1], p[i]) } }
        return l
    }

    static func smooth(_ points: [V3], _ passes: Int, _ cyclic: Bool = false) -> [V3] {
        var p = points
        for _ in 0..<Swift.max(passes, 0) {
            if p.count < 3 { return p }
            let n = p.count
            let q = p
            p = (0..<n).map { i in
                if !cyclic && (i == 0 || i == n - 1) { return q[i] }
                return q[(i - 1 + n) % n] * 0.25 + q[i] * 0.5 + q[(i + 1) % n] * 0.25
            }
        }
        return p
    }

    static func resample(_ points: [V3], _ pressures: [Float]?, _ spacing: Float, _ closed: Bool) -> ([V3], [Float]?) {
        let src = closed ? points + [points[0]] : points
        let prs: [Float]? = pressures.map { pr in
            (0..<src.count).map { i in let j = i < points.count ? i : 0; return j < pr.count ? pr[j] : 1 }
        }
        var out: [V3] = [src[0]]
        var outP: [Float] = [prs?[0] ?? 1]
        var carry: Float = 0
        if src.count > 1 {
            for i in 1..<src.count {
                let a = src[i - 1], b = src[i]
                let seg = dist(a, b)
                if seg < 1e-7 { continue }
                var t = spacing - carry
                while t <= seg {
                    let k = t / seg
                    out.append(a + (b - a) * k)
                    outP.append(prs.map { $0[i - 1] + ($0[i] - $0[i - 1]) * k } ?? 1)
                    t += spacing
                }
                carry = seg - (t - spacing)
            }
        }
        if !closed {
            if dist(out.last!, src.last!) > spacing * 0.3 { out.append(src.last!); outP.append(prs?.last ?? 1) }
            else { out[out.count - 1] = src.last! }
        } else if out.count > 2 && dist(out.last!, out[0]) < spacing * 0.5 { out.removeLast(); outP.removeLast() }
        return (out, prs != nil ? outP : nil)
    }

    private static func widths(_ count: Int, _ pressures: [Float]?, _ spec: BrushShapeSpec, _ closed: Bool) -> [Float] {
        (0..<count).map { i in
            let t: Float = count <= 1 ? 0.5 : Float(i) / (Float(count) - 1)
            let taper: Float
            if closed || spec.taper <= 0.01 { taper = 1 } else {
                let e = 0.35 * spec.taper
                let a = (t / e).coerceIn(0, 1), b = ((1 - t) / e).coerceIn(0, 1)
                taper = Swift.max(0.12, Swift.min(a, b).squareRoot())
            }
            let pr: Float = spec.pressure && pressures != nil ? 0.3 + 0.7 * pressures![i].coerceIn(0, 1) : 1
            return spec.width * taper * pr
        }
    }

    // MARK: tüp

    private static func tube(_ points: [V3], _ radii: [Float], _ sides: Int, _ closed: Bool) -> Mesh {
        let n = points.count
        let tangents: [V3] = (0..<n).map { i in
            let prev = i > 0 ? points[i - 1] : closed ? points[n - 1] : points[i]
            let next = i < n - 1 ? points[i + 1] : closed ? points[0] : points[i]
            return (next - prev).normalized()
        }
        var nx = perpendicular(tangents[0])
        var pos: [Float] = []
        pos.reserveCapacity(n * sides * 3 + 6)
        for i in 0..<n {
            let t = tangents[i]
            let q = (nx - t * nx.dot(t)).normalized()
            nx = q.length < 0.5 ? perpendicular(t) : q
            let bz = t.cross(nx).normalized()
            for s in 0..<sides {
                let a = 2.0 * Double.pi * Double(s) / Double(sides)
                let p = points[i] + nx * (radii[i] * Float(cos(a))) + bz * (radii[i] * Float(sin(a)))
                pos += [p.x, p.y, p.z]
            }
        }
        var faces: [[Int]] = []
        let segs = closed ? n : n - 1
        for i in 0..<segs {
            let j = (i + 1) % n
            for s in 0..<sides {
                let s1 = (s + 1) % sides
                faces.append([i * sides + s, i * sides + s1, j * sides + s1, j * sides + s])
            }
        }
        if !closed {
            let c0 = points[0] - tangents[0] * (radii[0] * 0.35)
            let c1 = points[n - 1] + tangents[n - 1] * (radii[n - 1] * 0.35)
            let i0 = pos.count / 3; pos += [c0.x, c0.y, c0.z]
            let i1 = pos.count / 3; pos += [c1.x, c1.y, c1.z]
            for s in 0..<sides {
                let s1 = (s + 1) % sides
                faces.append([i0, s1, s])
                faces.append([i1, (n - 1) * sides + s, (n - 1) * sides + s1])
            }
        }
        return Mesh(pos, faces)
    }

    // MARK: şerit ve duvar

    private static func strip(_ points: [V3], _ halfWidths: [Float], _ n: V3, _ h0: Float, _ h1: Float, _ closed: Bool) -> Mesh {
        let count = points.count
        var pos = [Float](repeating: 0, count: count * 4 * 3)
        for i in 0..<count {
            let prev = i > 0 ? points[i - 1] : closed ? points[count - 1] : points[i]
            let next = i < count - 1 ? points[i + 1] : closed ? points[0] : points[i]
            let t = (next - prev).normalized()
            let side = n.cross(t).normalized()
            let w = halfWidths[i]
            let corners = [points[i] + side * w + n * h0, points[i] - side * w + n * h0,
                           points[i] - side * w + n * h1, points[i] + side * w + n * h1]
            for (k, p) in corners.enumerated() { let o = (i * 4 + k) * 3; pos[o] = p.x; pos[o + 1] = p.y; pos[o + 2] = p.z }
        }
        func A(_ i: Int) -> Int { i * 4 }
        func B(_ i: Int) -> Int { i * 4 + 1 }
        func C(_ i: Int) -> Int { i * 4 + 2 }
        func D(_ i: Int) -> Int { i * 4 + 3 }
        var faces: [[Int]] = []
        let segs = closed ? count : count - 1
        for i in 0..<segs {
            let j = (i + 1) % count
            faces.append([C(i), C(j), D(j), D(i)])
            faces.append([A(i), A(j), B(j), B(i)])
            faces.append([A(i), D(i), D(j), A(j)])
            faces.append([B(i), B(j), C(j), C(i)])
        }
        if !closed {
            faces.append([A(0), B(0), C(0), D(0)])
            let e = count - 1
            faces.append([D(e), C(e), B(e), A(e)])
        }
        return Mesh(pos, faces)
    }

    static func prismOf(_ points: [V3], _ normal: V3, _ upHint: V3, _ height: Float) -> Mesh {
        let (n, up, right) = frame(normal, upHint)
        return Generators3D.fixOrientation(prism(points, n, right, up, height))
    }

    private static func prism(_ points: [V3], _ n: V3, _ right: V3, _ up: V3, _ height: Float) -> Mesh {
        let origin = points[0]
        var pts = points
        var area: Float = 0
        for i in pts.indices {
            let a = pts[i] - origin, b = pts[(i + 1) % pts.count] - origin
            area += a.dot(right) * b.dot(up) - b.dot(right) * a.dot(up)
        }
        if area < 0 { pts.reverse() }
        let m = pts.count
        var pos = [Float](repeating: 0, count: m * 2 * 3)
        for i in 0..<m {
            let b = pts[i], t = pts[i] + n * height
            pos[3 * i] = b.x; pos[3 * i + 1] = b.y; pos[3 * i + 2] = b.z
            pos[3 * (m + i)] = t.x; pos[3 * (m + i) + 1] = t.y; pos[3 * (m + i) + 2] = t.z
        }
        var faces: [[Int]] = []
        faces.append((0..<m).map { m + $0 })
        faces.append((0..<m).map { m - 1 - $0 })
        for i in 0..<m { let j = (i + 1) % m; faces.append([i, j, m + j, m + i]) }
        return Mesh(pos, faces)
    }

    // MARK: torna

    private static func lathe(_ points: [V3], _ n: V3, _ right: V3, _ up: V3, _ segments: Int, _ origin: V3) -> Mesh? {
        var profile: [(Float, Float)] = points.map { p in let d = p - origin; return (abs(d.dot(right)), d.dot(up)) }
        let maxR = profile.map { $0.0 }.max() ?? 0
        if maxR < 1e-3 { return nil }
        if profile.first!.1 > profile.last!.1 { profile.reverse() }
        let eps = 1e-3 * Swift.max(1e-3, maxR)
        let prof = profile.map { (r, y) -> (Float, Float) in (r < eps ? 0 : r, y) }
        let local = Primitives.revolve(prof, segments, capBottom: prof.first!.0 > 0, capTop: prof.last!.0 > 0)
        return local.mapPositions { p in origin + right * p.x + up * p.y + n * p.z }
    }

    // MARK: şişirme

    private static func inflate(_ points: [V3], _ n: V3, _ right: V3, _ up: V3, _ bulge: Float, _ subdiv: Int) -> Mesh? {
        let origin = points[0]
        var xs = points.map { ($0 - origin).dot(right) }
        var ys = points.map { ($0 - origin).dot(up) }
        var area: Float = 0
        for i in xs.indices { let j = (i + 1) % xs.count; area += xs[i] * ys[j] - xs[j] * ys[i] }
        if abs(area) < 1e-8 { return nil }
        if area < 0 { xs.reverse(); ys.reverse() }
        let target = subdiv >= 3 ? 72 : 56
        if xs.count > target {
            var per: [Float] = []; var total: Float = 0
            for i in xs.indices {
                let j = (i + 1) % xs.count
                let l = ((xs[j] - xs[i]) * (xs[j] - xs[i]) + (ys[j] - ys[i]) * (ys[j] - ys[i])).squareRoot()
                per.append(l); total += l
            }
            var nx: [Float] = [], ny: [Float] = []
            var acc: Float = 0; var seg = 0
            for k in 0..<target {
                let want = total * Float(k) / Float(target)
                while seg < xs.count - 1 && acc + per[seg] < want { acc += per[seg]; seg += 1 }
                let j = (seg + 1) % xs.count
                let t: Float = per[seg] < 1e-9 ? 0 : ((want - acc) / per[seg]).coerceIn(0, 1)
                nx.append(xs[seg] + (xs[j] - xs[seg]) * t); ny.append(ys[seg] + (ys[j] - ys[seg]) * t)
            }
            xs = nx; ys = ny
        }
        let m = xs.count
        var px = xs, py = ys
        var boundary = [Bool](repeating: true, count: m)
        var tris: [[Int]] = []
        let cx = Float(xs.reduce(0.0) { $0 + Double($1) } / Double(m))
        let cy = Float(ys.reduce(0.0) { $0 + Double($1) } / Double(m))
        let star = (0..<m).allSatisfy { i in let j = (i + 1) % m; return (xs[i] - cx) * (ys[j] - cy) - (xs[j] - cx) * (ys[i] - cy) > 1e-9 }
        if star {
            let rings = subdiv >= 3 ? 12 : 9
            var ringStart = [Int](repeating: 0, count: rings)
            for k in 1..<rings {
                let u = Float(sin(Double(Float(rings - k) / Float(rings) * PI_F / 2)))
                ringStart[k] = px.count
                for i in 0..<m { px.append(cx + (xs[i] - cx) * u); py.append(cy + (ys[i] - cy) * u); boundary.append(false) }
            }
            let center = px.count
            px.append(cx); py.append(cy); boundary.append(false)
            func ring(_ k: Int, _ i: Int) -> Int { k == 0 ? i % m : ringStart[k] + i % m }
            for k in 0..<(rings - 1) { for i in 0..<m {
                let a = ring(k, i), b = ring(k, i + 1), c = ring(k + 1, i + 1), d = ring(k + 1, i)
                tris.append([a, b, c]); tris.append([a, c, d])
            } }
            for i in 0..<m { tris.append([ring(rings - 1, i), ring(rings - 1, i + 1), center]) }
        } else {
            var flat = [Float](repeating: 0, count: m * 3)
            for i in 0..<m { flat[3 * i] = xs[i]; flat[3 * i + 1] = ys[i] }
            let tri = MeshOps.triangulate(flat, Array(0..<m))
            var k = 0
            while k + 2 < tri.count { tris.append([tri[k], tri[k + 1], tri[k + 2]]); k += 3 }
            var boundaryEdges = Set<Int64>()
            for i in 0..<m { boundaryEdges.insert(ek(i, (i + 1) % m)) }
            for _ in 0..<subdiv {
                var mids: [Int64: Int] = [:]
                func mid(_ a: Int, _ b: Int) -> Int {
                    let key = ek(a, b)
                    if let v = mids[key] { return v }
                    px.append((px[a] + px[b]) * 0.5); py.append((py[a] + py[b]) * 0.5)
                    boundary.append(boundaryEdges.contains(key))
                    mids[key] = px.count - 1
                    return px.count - 1
                }
                var next: [[Int]] = []
                next.reserveCapacity(tris.count * 4)
                var nextBoundary = Set<Int64>()
                for t in tris {
                    let a = t[0], b = t[1], c = t[2]
                    let ab = mid(a, b), bc = mid(b, c), ca = mid(c, a)
                    next.append([a, ab, ca]); next.append([ab, b, bc]); next.append([ca, bc, c]); next.append([ab, bc, ca])
                    for (p, q, r) in [(a, b, ab), (b, c, bc), (c, a, ca)] where boundaryEdges.contains(ek(p, q)) {
                        nextBoundary.insert(ek(p, r)); nextBoundary.insert(ek(r, q))
                    }
                }
                tris = next
                boundaryEdges = nextBoundary
            }
        }
        let count = px.count
        var d = [Float](repeating: 0, count: count)
        for i in 0..<count where !boundary[i] {
            var best = Float.greatestFiniteMagnitude
            for j in 0..<m {
                let jj = (j + 1) % m
                best = Swift.min(best, segDist(px[i], py[i], xs[j], ys[j], xs[jj], ys[jj]))
            }
            d[i] = best
        }
        let dmax = (d.max() ?? 0).coerceAtLeast(1e-6)
        let hmax = dmax * bulge
        var pos: [Float] = []
        pos.reserveCapacity(count * 6)
        var top = [Int](repeating: 0, count: count), bottom = top
        func add(_ p: V3) -> Int { pos += [p.x, p.y, p.z]; return pos.count / 3 - 1 }
        for i in 0..<count {
            let base = origin + right * px[i] + up * py[i]
            let u = 1 - d[i] / dmax
            let h = hmax * (1 - u * u).coerceAtLeast(0).squareRoot()
            if boundary[i] || h < 1e-6 { let id = add(base); top[i] = id; bottom[i] = id }
            else { top[i] = add(base + n * h); bottom[i] = add(base - n * h) }
        }
        var faces: [[Int]] = []
        for t in tris {
            if top[t[0]] == bottom[t[0]] && top[t[1]] == bottom[t[1]] && top[t[2]] == bottom[t[2]] { continue }
            faces.append([top[t[0]], top[t[1]], top[t[2]]])
            faces.append([bottom[t[2]], bottom[t[1]], bottom[t[0]]])
        }
        let valid = faces.filter { Set($0).count == 3 }
        return Mesh(pos, valid).compact()
    }

    private static func ek(_ a: Int, _ b: Int) -> Int64 { a < b ? (Int64(a) << 32) | Int64(b) : (Int64(b) << 32) | Int64(a) }

    private static func segDist(_ px: Float, _ py: Float, _ ax: Float, _ ay: Float, _ bx: Float, _ by: Float) -> Float {
        let dx = bx - ax, dy = by - ay
        let l2 = dx * dx + dy * dy
        let t: Float = l2 < 1e-12 ? 0 : (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0, 1)
        let qx = ax + dx * t - px, qy = ay + dy * t - py
        return (qx * qx + qy * qy).squareRoot()
    }
}
