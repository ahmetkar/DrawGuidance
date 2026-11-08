import Foundation

/// v26 ağın imzası (Android `MeshSig`).
struct MeshSig: Hashable {
    let vertices: Int, faces: Int, sum: Double

    func matches(_ m: Mesh) -> Bool {
        let o = MeshSig.of(m)
        return o.vertices == vertices && o.faces == faces && abs(o.sum - sum) <= 1e-4 * (1.0 + abs(sum))
    }

    static func of(_ m: Mesh) -> MeshSig {
        var s = 0.0
        let p = m.pos
        for i in p.indices { s += Double(p[i]) * Double((i % 7) + 1) }
        return MeshSig(vertices: m.vertexCount, faces: m.faceCount, sum: s)
    }
}

/// v26 fırçayla çizilen parçanın kaynağı (parçanın yerel uzayında).
final class BrushSource {
    let spec: BrushShapeSpec
    let points: [Float]
    let pressures: [Float]?
    let normal: V3, up: V3
    let closed: Bool?
    let axis: V3?
    let time: Int64
    let sig: MeshSig?

    init(_ spec: BrushShapeSpec, _ points: [Float], _ pressures: [Float]?, _ normal: V3, _ up: V3, closed: Bool? = nil,
         axis: V3? = nil, time: Int64 = 0, sig: MeshSig? = nil) {
        self.spec = spec; self.points = points; self.pressures = pressures; self.normal = normal; self.up = up
        self.closed = closed; self.axis = axis; self.time = time; self.sig = sig
    }

    var count: Int { points.count / 3 }
    func point(_ i: Int) -> V3 { V3(points[3 * i], points[3 * i + 1], points[3 * i + 2]) }
    func input() -> BrushInput { BrushInput(points: (0..<count).map(point), pressures: pressures, normal: normal, up: up, closed: closed, axis: axis) }
    var isClosed: Bool { closed ?? BrushShapes.isClosed((0..<count).map(point), spec) }

    /// Kotlin `with(...)`: `.some(nil)` alanı boşaltır, `nil` (verilmedi) olduğu gibi bırakır.
    func with(points: [Float]? = nil, pressures: [Float]?? = nil, closed: Bool?? = nil, axis: V3?? = nil, sig: MeshSig?? = nil) -> BrushSource {
        BrushSource(spec, points ?? self.points, pressures ?? self.pressures, normal, up, closed: closed ?? self.closed,
                    axis: axis ?? self.axis, time: time, sig: sig ?? self.sig)
    }

    func shifted(_ d: V3) -> BrushSource {
        var p = points
        for i in 0..<count { p[3 * i] -= d.x; p[3 * i + 1] -= d.y; p[3 * i + 2] -= d.z }
        return with(points: p, axis: .some(axis.map { $0 - d }))
    }

    func signed(_ mesh: Mesh) -> BrushSource { with(sig: .some(MeshSig.of(mesh))) }

    func valid(_ mesh: Mesh) -> Bool { sig?.matches(mesh) == true }
}

/// v26 silgi (yalnız çizimlere) (Android `BrushErase`).
enum BrushErase {

    /// Silgi izi: ekran noktaları ve yarıçap (piksel).
    final class Eraser {
        let xs: [Float], ys: [Float], radius: Float
        private let minX: Float, maxX: Float, minY: Float, maxY: Float
        init(_ xs: [Float], _ ys: [Float], _ radius: Float) {
            self.xs = xs; self.ys = ys; self.radius = radius
            minX = xs.min() ?? 0; maxX = xs.max() ?? 0; minY = ys.min() ?? 0; maxY = ys.max() ?? 0
        }
        var size: Int { xs.count }

        func hitsBox(_ x0: Float, _ y0: Float, _ x1: Float, _ y1: Float, _ extra: Float = 0) -> Bool {
            let r = radius + extra
            return !(x1 < minX - r || x0 > maxX + r || y1 < minY - r || y0 > maxY + r)
        }

        func distToSegment(_ ax: Float, _ ay: Float, _ bx: Float, _ by: Float) -> Float {
            if size == 0 { return .greatestFiniteMagnitude }
            if size == 1 { return BrushErase.segPointDist(ax, ay, bx, by, xs[0], ys[0]) }
            var best = Float.greatestFiniteMagnitude
            for i in 0..<(size - 1) {
                best = Swift.min(best, BrushErase.segSegDist(xs[i], ys[i], xs[i + 1], ys[i + 1], ax, ay, bx, by))
                if best <= 0 { return 0 }
            }
            return best
        }

        func touches(_ ax: Float, _ ay: Float, _ bx: Float? = nil, _ by: Float? = nil, extra: Float = 0) -> Bool {
            distToSegment(ax, ay, bx ?? ax, by ?? ay) <= radius + extra
        }
    }

    // MARK: 3B çizgi

    static func eraseStroke(_ s: Stroke3, _ view: View3, _ e: Eraser, _ newId: () -> String) -> [Stroke3]? {
        let n = s.count
        if n < 2 { return nil }
        var sp = [Float](repeating: 0, count: 4)
        var x0 = Float.greatestFiniteMagnitude, y0 = Float.greatestFiniteMagnitude
        var x1 = -Float.greatestFiniteMagnitude, y1 = -Float.greatestFiniteMagnitude
        var scr = [Float](repeating: 0, count: n * 2)
        var vis = [Bool](repeating: false, count: n)
        for i in 0..<n {
            view.project(s.points[3 * i], s.points[3 * i + 1], s.points[3 * i + 2], &sp)
            scr[2 * i] = sp[0]; scr[2 * i + 1] = sp[1]; vis[i] = sp[3] > 0
            x0 = Swift.min(x0, sp[0]); y0 = Swift.min(y0, sp[1]); x1 = Swift.max(x1, sp[0]); y1 = Swift.max(y1, sp[1])
        }
        if !e.hitsBox(x0, y0, x1, y1) { return nil }
        var pts: [Float] = []
        var keep: [Bool] = []
        let step = Swift.max(2, e.radius * 0.5)
        for i in 0..<n {
            if i > 0 {
                let dx = scr[2 * i] - scr[2 * i - 2], dy = scr[2 * i + 1] - scr[2 * i - 1]
                let k = Int(((dx * dx + dy * dy).squareRoot() / step).rounded(.up)).coerceIn(1, 64)
                if k > 1 {
                    for j in 1..<k {
                        let t = Float(j) / Float(k)
                        let px = scr[2 * i - 2] + dx * t, py = scr[2 * i - 1] + dy * t
                        for c in 0...2 { pts.append(s.points[3 * (i - 1) + c] + (s.points[3 * i + c] - s.points[3 * (i - 1) + c]) * t) }
                        keep.append(!(vis[i] && vis[i - 1] && e.touches(px, py)))
                    }
                }
            }
            for c in 0...2 { pts.append(s.points[3 * i + c]) }
            keep.append(!(vis[i] && e.touches(scr[2 * i], scr[2 * i + 1])))
        }
        if keep.allSatisfy({ $0 }) { return nil }
        var out: [Stroke3] = []
        var run: [Float] = []
        func flush() {
            if run.count >= 6 { out.append(Stroke3(out.isEmpty ? s.id : newId(), run, s.color, s.width, time: s.time)) }
            run = []
        }
        for i in keep.indices {
            if keep[i] { for c in 0...2 { run.append(pts[3 * i + c]) } } else { flush() }
        }
        flush()
        return out
    }

    // MARK: fırça parçaları

    static func erasePart(_ part: Part, _ view: View3, _ e: Eraser, _ newId: () -> String) -> [Part]? {
        guard let src = part.brush else { return nil }
        if !part.visible || src.count < 2 || !src.valid(part.mesh) || isArea(part) { return nil }
        if !onScreen(part, view, e) { return nil }
        let t = Transform3.of(part)
        let k = ((abs(part.scale.x) + abs(part.scale.y) + abs(part.scale.z)) / 3).coerceAtLeast(1e-4)
        let sources: [BrushSource]?
        switch src.spec.kind {
        case .TUBE, .RIBBON: sources = openRuns(src, t, k, view, e, 0)
        case .WALL: sources = openRuns(src, t, k, view, e, src.spec.height)
        case .LATHE: sources = latheRuns(src, t, view, e)
        default: sources = nil
        }
        guard let sources else { return nil }
        var out: [Part] = []
        for s in sources {
            guard let mesh = BrushShapes.rebuild(s) else { continue }
            out.append(piece(part, s, mesh, out.isEmpty ? part.id : newId()))
        }
        return out
    }

    static func isArea(_ part: Part) -> Bool {
        guard let src = part.brush else { return false }
        return src.spec.kind == .INFLATE || (src.spec.kind == .WALL && src.isClosed)
    }

    static func onScreen(_ part: Part, _ view: View3, _ e: Eraser) -> Bool {
        let t = Transform3.of(part)
        let b = part.mesh.bounds
        var sp = [Float](repeating: 0, count: 4)
        var x0 = Float.greatestFiniteMagnitude, y0 = Float.greatestFiniteMagnitude
        var x1 = -Float.greatestFiniteMagnitude, y1 = -Float.greatestFiniteMagnitude
        for k in 0..<8 {
            let w = t.apply(V3(k & 1 == 0 ? b.min.x : b.max.x, k & 2 == 0 ? b.min.y : b.max.y, k & 4 == 0 ? b.min.z : b.max.z))
            view.project(w.x, w.y, w.z, &sp)
            if sp[3] <= 0 { continue }
            x0 = Swift.min(x0, sp[0]); y0 = Swift.min(y0, sp[1]); x1 = Swift.max(x1, sp[0]); y1 = Swift.max(y1, sp[1])
        }
        return x0 <= x1 && e.hitsBox(x0, y0, x1, y1)
    }

    private static func piece(_ part: Part, _ s: BrushSource, _ mesh: Mesh, _ id: String) -> Part {
        let b = mesh.bounds
        let o = V3(b.center.x, b.min.y, b.center.z)
        let local = mesh.mapPositions { $0 - o }
        let t = Transform3.of(part)
        var p = part
        p.id = id; p.mesh = local; p.primitive = nil; p.position = t.apply(o); p.faceMaterials = [:]
        p.brush = s.shifted(o).signed(local)
        return p
    }

    // MARK: açık iz

    private static func openRuns(_ src: BrushSource, _ t: Transform3, _ k: Float, _ view: View3, _ e: Eraser, _ spine: Float) -> [BrushSource]? {
        let n = BrushShapes.frame(src.normal, src.up).0
        let pts = densify(src, t, view, e.radius * 0.5)
        let count = pts.0.count / 3
        let prs = pts.1
        var keep = [Bool](repeating: false, count: count)
        var a = [Float](repeating: 0, count: 4), c = a
        let half = src.spec.width * 0.5 * k
        for i in 0..<count {
            let lp = V3(pts.0[3 * i], pts.0[3 * i + 1], pts.0[3 * i + 2])
            let w = t.apply(lp)
            view.project(w.x, w.y, w.z, &a)
            if a[3] <= 0 { keep[i] = true; continue }
            let extra = half * a[3] * view.scale
            if spine > 0 {
                let top = t.apply(lp + n * spine)
                view.project(top.x, top.y, top.z, &c)
                keep[i] = !e.touches(a[0], a[1], c[3] > 0 ? c[0] : a[0], c[3] > 0 ? c[1] : a[1], extra: extra)
            } else {
                keep[i] = !e.touches(a[0], a[1], extra: extra)
            }
        }
        if keep.allSatisfy({ $0 }) { return nil }
        return runs(keep, src.isClosed).map { idx -> BrushSource in
            var p = [Float](repeating: 0, count: idx.count * 3)
            for (j, i) in idx.enumerated() { for q in 0...2 { p[3 * j + q] = pts.0[3 * i + q] } }
            return src.with(points: p, pressures: .some(prs.map { pr in idx.map { pr[$0] } }), closed: .some(false), sig: .some(nil))
        }.filter { $0.count >= 2 }
    }

    private static func runs(_ keep: [Bool], _ cyclic: Bool) -> [[Int]] {
        var out: [[Int]] = []
        var open = false
        for i in keep.indices {
            if keep[i] {
                if !open { out.append([]); open = true }
                out[out.count - 1].append(i)
            } else { open = false }
        }
        if cyclic && out.count >= 2 && keep.first! && keep.last! {
            let first = out.removeFirst()
            out[out.count - 1] += first
        }
        return out
    }

    private static func densify(_ src: BrushSource, _ t: Transform3, _ view: View3, _ step: Float) -> ([Float], [Float]?) {
        let n = src.count
        var out: [Float] = [], outP: [Float] = []
        var a = [Float](repeating: 0, count: 4), b = a
        let st = Swift.max(1.5, step)
        for i in 0..<n {
            if i > 0 {
                let pw = t.apply(src.point(i - 1)), qw = t.apply(src.point(i))
                view.project(pw.x, pw.y, pw.z, &a); view.project(qw.x, qw.y, qw.z, &b)
                let d: Float = a[3] > 0 && b[3] > 0 ? ((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1])).squareRoot() : 0
                let k = Int((d / st).rounded(.up)).coerceIn(1, 48)
                if k > 1 {
                    for j in 1..<k {
                        let f = Float(j) / Float(k)
                        for c in 0...2 { out.append(src.points[3 * (i - 1) + c] + (src.points[3 * i + c] - src.points[3 * (i - 1) + c]) * f) }
                        if let pr = src.pressures { outP.append(pr[i - 1] + (pr[i] - pr[i - 1]) * f) }
                    }
                }
            }
            for c in 0...2 { out.append(src.points[3 * i + c]) }
            if let pr = src.pressures { outP.append(pr[i]) }
        }
        return (out, src.pressures != nil ? outP : nil)
    }

    // MARK: torna

    private static func latheRuns(_ src: BrushSource, _ t: Transform3, _ view: View3, _ e: Eraser) -> [BrushSource]? {
        let (n, up, right) = BrushShapes.frame(src.normal, src.up)
        let axis = src.axis ?? src.point(0)
        let pts = densify(src, t, view, e.radius * 0.5).0
        let count = pts.count / 3
        var keep = [Bool](repeating: true, count: count)
        let ring = 20
        var a = [Float](repeating: 0, count: 4), b = a
        for i in 0..<count {
            let d = V3(pts[3 * i], pts[3 * i + 1], pts[3 * i + 2]) - axis
            let r = abs(d.dot(right)), y = d.dot(up)
            var hit = false
            var prevOk = false
            for s in 0...ring {
                let ang = 2.0 * Double.pi * Double(s) / Double(ring)
                let lp = axis + up * y + right * (r * Float(cos(ang))) + n * (r * Float(sin(ang)))
                let w = t.apply(lp)
                view.project(w.x, w.y, w.z, &b)
                let ok = b[3] > 0
                if ok && prevOk && e.touches(a[0], a[1], b[0], b[1]) { hit = true; break }
                a[0] = b[0]; a[1] = b[1]; prevOk = ok
            }
            keep[i] = !hit
        }
        if keep.allSatisfy({ $0 }) { return nil }
        return runs(keep, false).map { idx -> BrushSource in
            var p = [Float](repeating: 0, count: idx.count * 3)
            for (j, i) in idx.enumerated() { for q in 0...2 { p[3 * j + q] = pts[3 * i + q] } }
            return src.with(points: p, pressures: .some(nil), closed: .some(false), axis: .some(axis), sig: .some(nil))
        }.filter { $0.count >= 2 }
    }

    // MARK: alan (şişir, prizma)

    static func carvePart(_ part: Part, _ view: View3, _ e: Eraser, _ newId: () -> String) -> [Part]? {
        guard let src = part.brush else { return nil }
        if !part.visible || !src.valid(part.mesh) || !isArea(part) || !onScreen(part, view, e) { return nil }
        let t = Transform3.of(part)
        guard let inv = Inverse3.of(t) else { return nil }
        let (n, up, right) = BrushShapes.frame(src.normal, src.up)
        let o = src.point(0)
        let mesh = part.mesh
        var bx0 = Float.greatestFiniteMagnitude, bx1 = -Float.greatestFiniteMagnitude
        var by0 = Float.greatestFiniteMagnitude, by1 = -Float.greatestFiniteMagnitude
        var h0 = Float.greatestFiniteMagnitude, h1 = -Float.greatestFiniteMagnitude
        for i in 0..<mesh.vertexCount {
            let d = mesh.vertex(i) - o
            let x = d.dot(right), y = d.dot(up), h = d.dot(n)
            bx0 = Swift.min(bx0, x); bx1 = Swift.max(bx1, x); by0 = Swift.min(by0, y); by1 = Swift.max(by1, y)
            h0 = Swift.min(h0, h); h1 = Swift.max(h1, h)
        }
        let planeO = o + n * ((h0 + h1) * 0.5)
        var ex = [Float](repeating: 0, count: e.size), ey = ex, er = ex
        var sp = [Float](repeating: 0, count: 4)
        for q in 0..<e.size {
            let (ro, rd) = view.ray(e.xs[q], e.ys[q])
            let lo = inv.point(ro), ld = inv.point(ro + rd) - lo
            let den = ld.dot(n)
            if abs(den) < 1e-6 { ex[q] = .nan; continue }
            let hit = lo + ld * ((planeO - lo).dot(n) / den)
            let d = hit - o
            ex[q] = d.dot(right); ey[q] = d.dot(up)
            let w = t.apply(hit)
            view.project(w.x, w.y, w.z, &sp)
            let pxPerLocal: Float = sp[3] > 0 ? sp[3] * view.scale * ((t.applyDir(right).length + t.applyDir(up).length) * 0.5) : 0
            er[q] = pxPerLocal > 1e-6 ? e.radius / pxPerLocal : 0
        }
        let span = Swift.max(bx1 - bx0, by1 - by0).coerceAtLeast(1e-5)
        let cell = span / Float(GRID)
        bx0 -= cell * 2; by0 -= cell * 2; bx1 += cell * 2; by1 += cell * 2
        let gx = Int(((bx1 - bx0) / cell).rounded(.up)).coerceAtLeast(4)
        let gy = Int(((by1 - by0) / cell).rounded(.up)).coerceAtLeast(4)
        let w = gx + 1
        var f = [Float](repeating: 0, count: w * (gy + 1))
        var any = false
        for jy in 0...gy { for ix in 0...gx {
            let x = bx0 + Float(ix) * cell, y = by0 + Float(jy) * cell
            var de = Float.greatestFiniteMagnitude
            for q in 0..<e.size {
                if ex[q].isNaN { continue }
                let dd: Float
                if q + 1 < e.size && !ex[q + 1].isNaN { dd = segPointDist(ex[q], ey[q], ex[q + 1], ey[q + 1], x, y) - Swift.min(er[q], er[q + 1]) }
                else { dd = ((ex[q] - x) * (ex[q] - x) + (ey[q] - y) * (ey[q] - y)).squareRoot() - er[q] }
                de = Swift.min(de, dd)
            }
            let border = ix == 0 || jy == 0 || ix == gx || jy == gy
            let v = border ? -cell : -de
            if v > 0 { any = true }
            f[jy * w + ix] = abs(v) < 1e-9 ? -1e-9 : v
        } }
        if !any { return nil }
        let margin = (h1 - h0) * 0.1 + cell
        let cutters: [Mesh] = contours(f, gx, gy).compactMap { loop in
            let xs = (0..<(loop.count / 2)).map { bx0 + loop[2 * $0] * cell }
            let ys = (0..<(loop.count / 2)).map { by0 + loop[2 * $0 + 1] * cell }
            var area: Float = 0
            for q in xs.indices { let r = (q + 1) % xs.count; area += xs[q] * ys[r] - xs[r] * ys[q] }
            if area * 0.5 < cell * cell { return nil }
            let (sx, sy) = simplify(xs, ys, cell * 0.2)
            if sx.count < 3 { return nil }
            let base = (0..<sx.count).map { o + right * sx[$0] + up * sy[$0] + n * (h0 - margin) }
            return BrushShapes.prismOf(base, n, up, (h1 - h0) + 2 * margin)
        }
        if cutters.isEmpty { return nil }
        var acc = Csg.polygons(mesh, nil) { _ in 0 }
        for c in cutters { acc = Csg.subtractLocal(acc, Csg.polygons(c, nil) { _ in 0 }) }
        var tags: [Int] = []
        let res = Csg.toMesh(acc, &tags)
        if res.faceCount == 0 { return [] }
        let v0 = volume(mesh), v1 = volume(res)
        if abs(v1 - v0) <= 1e-5 * Swift.max(1e-9, abs(v0)) { return nil }
        var out: [Part] = []
        let comps = vertexComponents(res).enumerated().sorted { $0.element.count != $1.element.count ? $0.element.count > $1.element.count : $0.offset < $1.offset }
        for (_, faces) in comps {
            if faces.count < 4 { continue }
            let sub = MeshTopo.subMesh(res, faces).mesh
            var p = part
            p.id = out.isEmpty ? part.id : newId(); p.mesh = sub; p.primitive = nil; p.faceMaterials = [:]; p.brush = src.signed(sub)
            out.append(p)
        }
        return out
    }

    private static func vertexComponents(_ m: Mesh) -> [[Int]] {
        var parent = Array(0..<m.vertexCount)
        func find(_ x: Int) -> Int { var r = x; while parent[r] != r { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
        for f in m.faces where f.count > 1 { for i in 1..<f.count { let a = find(f[0]), b = find(f[i]); if a != b { parent[a] = b } } }
        var order: [Int] = []
        var groups: [Int: [Int]] = [:]
        for (fi, f) in m.faces.enumerated() where !f.isEmpty {
            let r = find(f[0])
            if groups[r] == nil { order.append(r); groups[r] = [] }
            groups[r]!.append(fi)
        }
        return order.map { groups[$0]! }
    }

    private static func volume(_ m: Mesh) -> Double {
        var v = 0.0
        let p = m.pos, tri = m.triangles
        for f in 0..<m.faceCount {
            let face = m.faces[f], tr = tri[f]
            var k = 0
            while k + 2 < tr.count {
                let a = face[tr[k]] * 3, b = face[tr[k + 1]] * 3, c = face[tr[k + 2]] * 3
                let t1 = p[a] * (p[b + 1] * p[c + 2] - p[b + 2] * p[c + 1])
                let t2 = p[a + 1] * (p[b] * p[c + 2] - p[b + 2] * p[c])
                let t3 = p[a + 2] * (p[b] * p[c + 1] - p[b + 1] * p[c])
                v += Double(t1 - t2 + t3)
                k += 3
            }
        }
        return v / 6.0
    }

    /// Marching squares: alanın sıfır eğrisinden kapalı halkalar (ızgara biriminde).
    private static func contours(_ f: [Float], _ gx: Int, _ gy: Int) -> [[Float]] {
        let w = gx + 1
        func v(_ ix: Int, _ jy: Int) -> Float { f[jy * w + ix] }
        func hKey(_ ix: Int, _ jy: Int) -> Int { 2 * (jy * w + ix) }
        func vKey(_ ix: Int, _ jy: Int) -> Int { 2 * (jy * w + ix) + 1 }
        var next: [Int: Int] = [:]
        var nextOrder: [Int] = []
        var point: [Int: (Float, Float)] = [:]
        func setNext(_ k: Int, _ val: Int) { if next[k] == nil { nextOrder.append(k) }; next[k] = val }
        func cross(_ key: Int, _ ax: Int, _ ay: Int, _ bx: Int, _ by: Int) {
            if point[key] != nil { return }
            let fa = v(ax, ay), fb = v(bx, by)
            let tt = (fa / (fa - fb)).coerceIn(0, 1)
            point[key] = (Float(ax) + Float(bx - ax) * tt, Float(ay) + Float(by - ay) * tt)
        }
        for jy in 0..<gy { for ix in 0..<gx {
            let c = [ix, jy, ix + 1, jy, ix + 1, jy + 1, ix, jy + 1]
            let inside = (0..<4).map { v(c[2 * $0], c[2 * $0 + 1]) > 0 }
            if inside.allSatisfy({ $0 }) || !inside.contains(true) { continue }
            let keys = [hKey(ix, jy), vKey(ix + 1, jy), hKey(ix, jy + 1), vKey(ix, jy)]
            var outs: [Int] = [], ins: [Int] = []
            for k in 0..<4 {
                let k1 = (k + 1) % 4
                if inside[k] == inside[k1] { continue }
                cross(keys[k], c[2 * k], c[2 * k + 1], c[2 * k1], c[2 * k1 + 1])
                if inside[k] { outs.append(k) } else { ins.append(k) }
            }
            if outs.count == 1 {
                setNext(keys[outs[0]], keys[ins[0]])
            } else {
                let center = (v(ix, jy) + v(ix + 1, jy) + v(ix + 1, jy + 1) + v(ix, jy + 1)) * 0.25
                if center > 0 {
                    for q in outs.indices { setNext(keys[outs[q]], keys[ins.first { $0 == (outs[q] + 1) % 4 }!]) }
                } else {
                    for q in outs.indices { setNext(keys[outs[q]], keys[ins.first { $0 == (outs[q] + 3) % 4 }!]) }
                }
            }
        } }
        var loops: [[Float]] = []
        var used = Set<Int>()
        for start in javaHashOrder(nextOrder) {
            if used.contains(start) { continue }
            var pts: [Float] = []
            var cur = start
            var guardN = 0
            while !used.contains(cur) && guardN < next.count + 2 {
                guardN += 1
                used.insert(cur)
                guard let p = point[cur] else { break }
                pts += [p.0, p.1]
                guard let nx = next[cur] else { break }
                cur = nx
            }
            if cur == start && pts.count >= 6 { loops.append(pts) }
        }
        return loops
    }

    private static func simplify(_ xs: [Float], _ ys: [Float], _ tol: Float) -> ([Float], [Float]) {
        var x = xs, y = ys
        var changed = true
        while changed && x.count > 3 {
            changed = false
            var q = 0
            while q < x.count && x.count > 3 {
                let a = (q - 1 + x.count) % x.count, c = (q + 1) % x.count
                if segPointDist(x[a], y[a], x[c], y[c], x[q], y[q]) < tol { x.remove(at: q); y.remove(at: q); changed = true } else { q += 1 }
            }
        }
        if x.count > 200 {
            let stepK = Float(x.count) / 200
            var nx: [Float] = [], ny: [Float] = []
            var f: Float = 0
            while Int(f) < x.count { nx.append(x[Int(f)]); ny.append(y[Int(f)]); f += stepK }
            x = nx; y = ny
        }
        return (x, y)
    }

    private static let GRID = 72

    static func segPointDist(_ ax: Float, _ ay: Float, _ bx: Float, _ by: Float, _ px: Float, _ py: Float) -> Float {
        let dx = bx - ax, dy = by - ay
        let l2 = dx * dx + dy * dy
        let t: Float = l2 < 1e-12 ? 0 : (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0, 1)
        let qx = ax + dx * t - px, qy = ay + dy * t - py
        return (qx * qx + qy * qy).squareRoot()
    }

    fileprivate static func segSegDist(_ ax: Float, _ ay: Float, _ bx: Float, _ by: Float, _ cx: Float, _ cy: Float, _ dx: Float, _ dy: Float) -> Float {
        if intersects(ax, ay, bx, by, cx, cy, dx, dy) { return 0 }
        return Swift.min(Swift.min(segPointDist(ax, ay, bx, by, cx, cy), segPointDist(ax, ay, bx, by, dx, dy)),
                         Swift.min(segPointDist(cx, cy, dx, dy, ax, ay), segPointDist(cx, cy, dx, dy, bx, by)))
    }

    private static func intersects(_ ax: Float, _ ay: Float, _ bx: Float, _ by: Float, _ cx: Float, _ cy: Float, _ dx: Float, _ dy: Float) -> Bool {
        func o(_ px: Float, _ py: Float, _ qx: Float, _ qy: Float, _ rx: Float, _ ry: Float) -> Float { (qx - px) * (ry - py) - (qy - py) * (rx - px) }
        let d1 = o(cx, cy, dx, dy, ax, ay), d2 = o(cx, cy, dx, dy, bx, by)
        let d3 = o(ax, ay, bx, by, cx, cy), d4 = o(ax, ay, bx, by, dx, dy)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }
}
