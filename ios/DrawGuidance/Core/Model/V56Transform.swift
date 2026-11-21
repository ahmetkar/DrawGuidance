import Foundation

/// v56 aşama 6 Dönüştür paneli geometrisi (planın 2A bölümü; saf, Android `V56Transform.kt` ile aynı):
/// homografi (Bozma), ağ enterpolasyonu (Çarpıtma), yaslama, çevirme/döndürme/sığdırma ve dönüşüm durumu.
/// SHEAR (2E, yalnız Resim): kenar paralel kayar, köşe paralelkenarı kendi eksenlerinde ölçekler.
enum TransformKind: String, CaseIterable { case FREE, UNIFORM, DISTORT, WARP, SHEAR }

/// Resim ara değerlemesi: ImageWarp.filter kodları.
enum TransformFilter: Int, CaseIterable {
    case NEAREST = 0, BILINEAR = 1, BICUBIC = 2
    var code: Int { rawValue }
    static func of(_ code: Int) -> TransformFilter { TransformFilter(rawValue: code) ?? .BILINEAR }
}

enum Homography {
    /// `src` dört noktayı (x0,y0,…,x3,y3) `dst`'ye eşleyen 3×3 matris (satır satır, h[8] = 1); tekil durumda nil.
    static func solve(_ src: [Float], _ dst: [Float]) -> [Float]? {
        var a = [[Double]](repeating: [Double](repeating: 0, count: 9), count: 8)
        for i in 0..<4 {
            let x = Double(src[i * 2]), y = Double(src[i * 2 + 1])
            let u = Double(dst[i * 2]), v = Double(dst[i * 2 + 1])
            a[i * 2] = [x, y, 1, 0, 0, 0, -u * x, -u * y, u]
            a[i * 2 + 1] = [0, 0, 0, x, y, 1, -v * x, -v * y, v]
        }
        for c in 0..<8 {
            var p = c
            for r in (c + 1)..<8 where abs(a[r][c]) > abs(a[p][c]) { p = r }
            if abs(a[p][c]) < 1e-12 { return nil }
            a.swapAt(c, p)
            for r in 0..<8 where r != c {
                let f = a[r][c] / a[c][c]
                if f != 0 { for k in c..<9 { a[r][k] -= f * a[c][k] } }
            }
        }
        return (0..<9).map { $0 == 8 ? 1 : Float(a[$0][8] / a[$0][$0]) }
    }

    static func map(_ h: [Float], _ x: Float, _ y: Float) -> (Float, Float) {
        let w = h[6] * x + h[7] * y + h[8]
        let ww = abs(w) < 1e-9 ? 1e-9 : w
        return ((h[0] * x + h[1] * y + h[2]) / ww, (h[3] * x + h[4] * y + h[5]) / ww)
    }

    /// Dörtgen dışbükey ve köşe sırası korunmuş mu.
    static func convex(_ q: [StrokePoint]) -> Bool {
        if q.count != 4 { return false }
        var sign = 0
        for i in 0..<4 {
            let a = q[i], b = q[(i + 1) % 4], c = q[(i + 2) % 4]
            let z = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            let s = z > 0 ? 1 : (z < 0 ? -1 : 0)
            if s != 0 { if sign == 0 { sign = s } else if s != sign { return false } }
        }
        return sign != 0
    }
}

enum WarpMesh {
    static let BASIC = 4
    static let ADVANCED = 6
    static let LIQUIFY = 17

    static func sizeOf(_ mesh: [StrokePoint]) -> Int {
        let n = Int(Double(mesh.count).squareRoot().rounded())
        return n >= 2 && n * n == mesh.count ? n : 0
    }

    static func corners(_ m: [StrokePoint]) -> [StrokePoint] {
        let n = sizeOf(m)
        return [m[0], m[n - 1], m[n * n - 1], m[n * (n - 1)]]
    }

    static func identity(_ l: Float, _ t: Float, _ r: Float, _ b: Float, _ n: Int) -> [StrokePoint] {
        (0..<(n * n)).map { i in StrokePoint(l + (r - l) * Float(i % n) / Float(n - 1), t + (b - t) * Float(i / n) / Float(n - 1)) }
    }

    static func onQuad(_ q: [StrokePoint], _ n: Int) -> [StrokePoint] {
        (0..<(n * n)).map { i in
            let u = Float(i % n) / Float(n - 1), v = Float(i / n) / Float(n - 1)
            let tx = q[0].x + (q[1].x - q[0].x) * u, ty = q[0].y + (q[1].y - q[0].y) * u
            let bx = q[3].x + (q[2].x - q[3].x) * u, by = q[3].y + (q[2].y - q[3].y) * u
            return StrokePoint(tx + (bx - tx) * v, ty + (by - ty) * v)
        }
    }

    private static func cr(_ p0: Float, _ p1: Float, _ p2: Float, _ p3: Float, _ t: Float) -> Float {
        let t2 = t * t, t3 = t2 * t
        let a = 2 * p1 + (-p0 + p2) * t
        let b = (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
        let c = (-p0 + 3 * p1 - 3 * p2 + p3) * t3
        return 0.5 * (a + b + c)
    }

    /// Ağ yüzeyinde (u, v) noktası (Catmull-Rom; kenarda doğrusal uzatma).
    static func eval(_ mesh: [StrokePoint], _ n: Int, _ u: Float, _ v: Float) -> (Float, Float) {
        func at(_ ix: Int, _ iy: Int) -> StrokePoint {
            let cx = ix.coerceIn(0, n - 1), cy = iy.coerceIn(0, n - 1)
            let p = mesh[cy * n + cx]
            if cx == ix && cy == iy { return p }
            let qx = (2 * cx - ix).coerceIn(0, n - 1), qy = (2 * cy - iy).coerceIn(0, n - 1)
            let q = mesh[qy * n + qx]
            return StrokePoint(2 * p.x - q.x, 2 * p.y - q.y)
        }
        let fu = u.coerceIn(0, 1) * Float(n - 1), fv = v.coerceIn(0, 1) * Float(n - 1)
        let iu = min(Int(fu), n - 2), iv = min(Int(fv), n - 2)
        let tu = fu - Float(iu), tv = fv - Float(iv)
        var rx = [Float](repeating: 0, count: 4), ry = [Float](repeating: 0, count: 4)
        for k in 0..<4 {
            let row = iv - 1 + k
            let a = at(iu - 1, row), b = at(iu, row), c = at(iu + 1, row), d = at(iu + 2, row)
            rx[k] = cr(a.x, b.x, c.x, d.x, tu); ry[k] = cr(a.y, b.y, c.y, d.y, tu)
        }
        return (cr(rx[0], rx[1], rx[2], rx[3], tv), cr(ry[0], ry[1], ry[2], ry[3], tv))
    }

    static func map(_ mesh: [StrokePoint], _ n: Int, _ l: Float, _ t: Float, _ r: Float, _ b: Float, _ x: Float, _ y: Float) -> (Float, Float) {
        eval(mesh, n, r > l ? (x - l) / (r - l) : 0, b > t ? (y - t) / (b - t) : 0)
    }

    static func resample(_ mesh: [StrokePoint], _ n: Int, _ m: Int) -> [StrokePoint] {
        (0..<(m * m)).map { i in
            let p = eval(mesh, n, Float(i % m) / Float(m - 1), Float(i / m) / Float(m - 1))
            return StrokePoint(p.0, p.1)
        }
    }

    static func movePoint(_ mesh: [StrokePoint], _ index: Int, _ x: Float, _ y: Float) -> [StrokePoint] {
        mesh.enumerated().map { $0.offset == index ? StrokePoint(x, y) : $0.element }
    }

    static func smoothDrag(_ mesh: [StrokePoint], _ px: Float, _ py: Float, _ dx: Float, _ dy: Float, _ radius: Float) -> [StrokePoint] {
        let r2 = max(1, radius * radius)
        return mesh.map { p in
            let d2 = (p.x - px) * (p.x - px) + (p.y - py) * (p.y - py)
            let w = exp(-d2 / r2 * 2)
            return w < 1e-3 ? p : StrokePoint(p.x + dx * w, p.y + dy * w)
        }
    }

    enum Liquify: String, CaseIterable { case PUSH, TWIRL, PINCH, BLOAT }

    static func liquify(_ mesh: [StrokePoint], _ mode: Liquify, _ cx: Float, _ cy: Float, _ dx: Float, _ dy: Float, _ radius: Float, _ strength: Float) -> [StrokePoint] {
        let r = max(1, radius)
        let s = strength.coerceIn(0, 1)
        return mesh.map { p in
            let ox = p.x - cx, oy = p.y - cy
            let d = hypot(ox, oy)
            if d >= r { return p }
            let g = 1 - d / r
            let f = g * g * s
            switch mode {
            case .PUSH: return StrokePoint(p.x + dx * f, p.y + dy * f)
            case .TWIRL:
                let a = 0.35 * f
                return StrokePoint(cx + ox * cos(a) - oy * sin(a), cy + ox * sin(a) + oy * cos(a))
            case .PINCH: return StrokePoint(p.x - ox * 0.2 * f, p.y - oy * 0.2 * f)
            case .BLOAT: return StrokePoint(p.x + ox * 0.2 * f, p.y + oy * 0.2 * f)
            }
        }
    }
}

enum Snapping {
    static let ANGLE_STEP: Float = 15

    static func angle(_ degrees: Float, step: Float = ANGLE_STEP, tolerance: Float? = nil) -> Float {
        let tol = tolerance ?? step / 2
        let k = (degrees / step).rounded() * step
        return abs(degrees - k) <= tol ? k : degrees
    }

    static func nearest(_ value: Float, _ targets: [Float], _ distance: Float) -> Float? {
        guard let m = targets.min(by: { abs($0 - value) < abs($1 - value) }), abs(m - value) <= distance else { return nil }
        return m
    }

    static func active(_ speed: Float, _ threshold: Float) -> Bool { threshold <= 0 || speed <= threshold }

    static func edges(_ l: Float, _ t: Float, _ r: Float, _ b: Float) -> ([Float], [Float]) { ([l, (l + r) / 2, r], [t, (t + b) / 2, b]) }

    struct SnapResult: Equatable { var dx: Float; var dy: Float; var guideX: Float?; var guideY: Float? }

    static func snapBox(_ l: Float, _ t: Float, _ r: Float, _ b: Float, _ xs: [Float], _ ys: [Float], _ distance: Float) -> SnapResult {
        let (bx, by) = edges(l, t, r, b)
        var bestX: (Float, Float)? = nil
        for e in bx { if let g = nearest(e, xs, distance), bestX == nil || abs(g - e) < abs(bestX!.0) { bestX = (g - e, g) } }
        var bestY: (Float, Float)? = nil
        for e in by { if let g = nearest(e, ys, distance), bestY == nil || abs(g - e) < abs(bestY!.0) { bestY = (g - e, g) } }
        return SnapResult(dx: bestX?.0 ?? 0, dy: bestY?.0 ?? 0, guideX: bestX?.1, guideY: bestY?.1)
    }
}

/// Dönüşüm durumu: başlangıç sınır kutusu, güncel dörtgen ve (Çarpıtma'da) ağ.
struct TransformState: Equatable {
    var kind: TransformKind
    var l: Float, t: Float, r: Float, b: Float
    var quad: [StrokePoint]
    var mesh: [StrokePoint]? = nil
    var filter: TransformFilter = .BILINEAR

    var box: [StrokePoint] { [StrokePoint(l, t), StrokePoint(r, t), StrokePoint(r, b), StrokePoint(l, b)] }
    var isIdentity: Bool {
        if let m = mesh { return m == WarpMesh.identity(l, t, r, b, WarpMesh.sizeOf(m)) }
        return quad == box
    }

    static func start(_ l: Float, _ t: Float, _ r: Float, _ b: Float, kind: TransformKind = .UNIFORM) -> TransformState {
        let rr = r - l < 1 ? l + 1 : r
        let bb = b - t < 1 ? t + 1 : b
        let box = [StrokePoint(l, t), StrokePoint(rr, t), StrokePoint(rr, bb), StrokePoint(l, bb)]
        return TransformState(kind: kind, l: l, t: t, r: rr, b: bb, quad: box, mesh: kind == .WARP ? WarpMesh.identity(l, t, rr, bb, WarpMesh.BASIC) : nil)
    }
}

enum TransformOps {
    private static func center(_ q: [StrokePoint]) -> (Float, Float) {
        (q.reduce(0) { $0 + $1.x } / Float(q.count), q.reduce(0) { $0 + $1.y } / Float(q.count))
    }

    static func angleOf(_ q: [StrokePoint]) -> Float { Float(atan2(Double(q[1].y - q[0].y), Double(q[1].x - q[0].x)) * 180 / .pi) }
    static func widthOf(_ q: [StrokePoint]) -> Float { hypot(q[1].x - q[0].x, q[1].y - q[0].y) }
    static func heightOf(_ q: [StrokePoint]) -> Float { hypot(q[3].x - q[0].x, q[3].y - q[0].y) }

    static func setKind(_ s: TransformState, _ kind: TransformKind, meshSize: Int = WarpMesh.BASIC) -> TransformState {
        var o = s
        o.kind = kind
        if kind == .WARP {
            if let m = s.mesh, WarpMesh.sizeOf(m) == meshSize { o.mesh = m } else { o.mesh = WarpMesh.onQuad(s.quad, meshSize) }
        } else if let m = s.mesh {
            o.mesh = nil
            o.quad = WarpMesh.corners(m)
        }
        return o
    }

    private static func mapAll(_ s: TransformState, _ f: (Float, Float) -> (Float, Float)) -> TransformState {
        var o = s
        o.quad = s.quad.map { let p = f($0.x, $0.y); return StrokePoint(p.0, p.1) }
        o.mesh = s.mesh?.map { let p = f($0.x, $0.y); return StrokePoint(p.0, p.1) }
        return o
    }

    static func translate(_ s: TransformState, _ dx: Float, _ dy: Float) -> TransformState { mapAll(s) { ($0 + dx, $1 + dy) } }

    static func rotate(_ s: TransformState, _ degrees: Float, pivot: (Float, Float)? = nil) -> TransformState {
        let (cx, cy) = pivot ?? center(s.quad)
        let a = Double(degrees) * .pi / 180
        let c = Float(cos(a)), sn = Float(sin(a))
        return mapAll(s) { x, y in (cx + (x - cx) * c - (y - cy) * sn, cy + (x - cx) * sn + (y - cy) * c) }
    }

    static func rotateTo(_ s: TransformState, _ degrees: Float, magnet: Bool, pivot: (Float, Float)? = nil) -> TransformState {
        let target = magnet ? Snapping.angle(degrees) : degrees
        return rotate(s, target - angleOf(s.quad), pivot: pivot)
    }

    static func flip(_ s: TransformState, horizontal: Bool, pivot: (Float, Float)? = nil) -> TransformState {
        let (cx, cy) = pivot ?? center(s.quad)
        let a = Double(angleOf(s.quad)) * .pi / 180
        let ux = Float(cos(a)), uy = Float(sin(a))
        return mapAll(s) { x, y in
            let ox = x - cx, oy = y - cy
            var pu = ox * ux + oy * uy, pv = -ox * uy + oy * ux
            if horizontal { pu = -pu } else { pv = -pv }
            return (cx + pu * ux - pv * uy, cy + pu * uy + pv * ux)
        }
    }

    static func dragCorner(_ s: TransformState, _ i: Int, _ x: Float, _ y: Float) -> TransformState {
        if s.kind == .SHEAR {
            let q = s.quad
            let o = q[(i + 2) % 4], na = q[(i + 1) % 4], nb = q[(i + 3) % 4]
            let ax = na.x - o.x, ay = na.y - o.y, bx = nb.x - o.x, by = nb.y - o.y
            let det = ax * by - ay * bx
            if abs(det) < 1e-6 { return s }
            let px = x - o.x, py = y - o.y
            let a = (px * by - py * bx) / det, b = (ax * py - ay * px) / det
            if a * hypot(ax, ay) < 1 || b * hypot(bx, by) < 1 { return s }
            var nq = q
            nq[(i + 1) % 4] = StrokePoint(o.x + a * ax, o.y + a * ay)
            nq[(i + 3) % 4] = StrokePoint(o.x + b * bx, o.y + b * by)
            nq[i] = StrokePoint(o.x + a * ax + b * bx, o.y + a * ay + b * by)
            var out = s; out.quad = nq
            return out
        }
        if s.kind == .DISTORT {
            var q = s.quad; q[i] = StrokePoint(x, y)
            if Homography.convex(q) { var o = s; o.quad = q; return o }
            return s
        }
        let q = s.quad
        let o = q[(i + 2) % 4]
        let a = Double(angleOf(q)) * .pi / 180
        let ux = Float(cos(a)), uy = Float(sin(a))
        let oldW = widthOf(q), oldH = heightOf(q)
        let pu0 = (x - o.x) * ux + (y - o.y) * uy
        let pv0 = -(x - o.x) * uy + (y - o.y) * ux
        let su: Float = (i == 1 || i == 2) ? 1 : -1
        let sv: Float = (i == 2 || i == 3) ? 1 : -1
        var w = max(1, pu0 * su), h = max(1, pv0 * sv)
        if s.kind == .UNIFORM && oldW > 0 && oldH > 0 {
            let k = max(w / oldW, h / oldH)
            w = oldW * k; h = oldH * k
        }
        let pu = w * su, pv = h * sv
        func at(_ du: Float, _ dv: Float) -> StrokePoint { StrokePoint(o.x + du * ux - dv * uy, o.y + du * uy + dv * ux) }
        let u0 = min(0, pu), u1 = max(0, pu), v0 = min(0, pv), v1 = max(0, pv)
        var out = s
        out.quad = [at(u0, v0), at(u1, v0), at(u1, v1), at(u0, v1)]
        return out
    }

    static func dragEdge(_ s: TransformState, _ e: Int, _ dx: Float, _ dy: Float) -> TransformState {
        let q = s.quad
        let i0 = e, i1 = (e + 1) % 4
        if s.kind == .DISTORT || s.kind == .SHEAR {
            let ex = q[i1].x - q[i0].x, ey = q[i1].y - q[i0].y, len = max(1e-3, hypot(ex, ey))
            let along = (dx * ex + dy * ey) / len
            let mx = ex / len * along, my = ey / len * along
            let nq = q.enumerated().map { ($0.offset == i0 || $0.offset == i1) ? StrokePoint($0.element.x + mx, $0.element.y + my) : $0.element }
            if Homography.convex(nq) { var o = s; o.quad = nq; return o }
            return s
        }
        let a = Double(angleOf(q)) * .pi / 180
        let ux = Float(cos(a)), uy = Float(sin(a))
        let du = dx * ux + dy * uy, dv = -dx * uy + dy * ux
        let w = widthOf(q), h = heightOf(q)
        var nw = w, nh = h
        switch e { case 0: nh = max(1, h - dv); case 2: nh = max(1, h + dv); case 1: nw = max(1, w + du); default: nw = max(1, w - du) }
        if s.kind == .UNIFORM {
            let k = (e == 0 || e == 2) ? nh / max(1e-3, h) : nw / max(1e-3, w)
            nw = w * k; nh = h * k
        }
        let fix: StrokePoint
        switch e { case 0: fix = q[3]; case 1, 2: fix = q[0]; default: fix = q[1] }
        func at(_ a0: Float, _ b0: Float) -> StrokePoint { StrokePoint(fix.x + a0 * ux - b0 * uy, fix.y + a0 * uy + b0 * ux) }
        let nq: [StrokePoint]
        switch e {
        case 0: nq = [at(0, -nh), at(nw, -nh), at(nw, 0), q[3]]
        case 1, 2: nq = [q[0], at(nw, 0), at(nw, nh), at(0, nh)]
        default: nq = [at(-nw, 0), q[1], at(0, nh), at(-nw, nh)]
        }
        var o = s; o.quad = nq
        return o
    }

    static func dragMesh(_ s: TransformState, _ index: Int, _ x: Float, _ y: Float) -> TransformState {
        guard let m = s.mesh else { return s }
        var o = s; o.mesh = WarpMesh.movePoint(m, index, x, y); return o
    }

    static func dragMeshInside(_ s: TransformState, _ px: Float, _ py: Float, _ dx: Float, _ dy: Float) -> TransformState {
        guard let m = s.mesh else { return s }
        var o = s; o.mesh = WarpMesh.smoothDrag(m, px, py, dx, dy, max(widthOf(s.quad), heightOf(s.quad)) / 3); return o
    }

    static func fit(_ s: TransformState, _ l: Float, _ t: Float, _ r: Float, _ b: Float) -> TransformState {
        let bw = s.r - s.l, bh = s.b - s.t
        let k = min((r - l) / max(1e-3, bw), (b - t) / max(1e-3, bh))
        let w = bw * k, h = bh * k
        let x0 = l + (r - l - w) / 2, y0 = t + (b - t - h) / 2
        let q = [StrokePoint(x0, y0), StrokePoint(x0 + w, y0), StrokePoint(x0 + w, y0 + h), StrokePoint(x0, y0 + h)]
        var o = s; o.quad = q; o.mesh = s.mesh.map { WarpMesh.onQuad(q, WarpMesh.sizeOf($0)) }
        return o
    }

    static func reset(_ s: TransformState) -> TransformState {
        var o = s; o.quad = s.box; o.mesh = s.mesh.map { WarpMesh.identity(s.l, s.t, s.r, s.b, WarpMesh.sizeOf($0)) }
        return o
    }

    static func numeric(_ s: TransformState, _ x: Float, _ y: Float, _ w: Float, _ h: Float, _ degrees: Float) -> TransformState {
        let a = Double(degrees) * .pi / 180
        let ux = Float(cos(a)), uy = Float(sin(a))
        let ww = max(1, w), hh = max(1, h)
        let q = [StrokePoint(x, y), StrokePoint(x + ww * ux, y + ww * uy), StrokePoint(x + ww * ux - hh * uy, y + ww * uy + hh * ux), StrokePoint(x - hh * uy, y + hh * ux)]
        var o = s; o.quad = q; o.mesh = s.mesh.map { WarpMesh.onQuad(q, WarpMesh.sizeOf($0)) }
        return o
    }

    static func mapper(_ s: TransformState) -> (Float, Float) -> (Float, Float) {
        if let m = s.mesh { let n = WarpMesh.sizeOf(m); return { WarpMesh.map(m, n, s.l, s.t, s.r, s.b, $0, $1) } }
        let src: [Float] = [s.l, s.t, s.r, s.t, s.r, s.b, s.l, s.b]
        let dst: [Float] = (0..<8).map { $0 % 2 == 0 ? s.quad[$0 / 2].x : s.quad[$0 / 2].y }
        guard let h = Homography.solve(src, dst) else { return { ($0, $1) } }
        return { Homography.map(h, $0, $1) }
    }

    static func widthFactor(_ s: TransformState) -> Float {
        let q = s.mesh.map { WarpMesh.corners($0) } ?? s.quad
        var area: Float = 0
        for i in q.indices { let c = q[i], d = q[(i + 1) % q.count]; area += c.x * d.y - d.x * c.y }
        area = abs(area) / 2
        let a0 = (s.r - s.l) * (s.b - s.t)
        return a0 <= 0 ? 1 : (area / a0).squareRoot().coerceIn(0.05, 20)
    }

    struct Applied { var strokes: [Stroke]; var skipped: Set<String> }

    static func apply(_ strokes: [Stroke], _ ids: Set<String>, _ s: TransformState) -> Applied {
        let f = mapper(s)
        let wf = widthFactor(s)
        let nonAffine = s.kind == .DISTORT || s.kind == .WARP
        var skipped = Set<String>()
        func mp(_ p: StrokePoint) -> StrokePoint { let r = f(p.x, p.y); return p.at(r.0, r.1) }
        let out = strokes.map { st -> Stroke in
            guard ids.contains(st.id) else { return st }
            var o = st
            if st.brushType == .IMAGE && st.points.count >= 4 {
                let corners = Array(st.points.prefix(4))
                let oldMesh = st.warp?.mesh
                let oldN = oldMesh.map { WarpMesh.sizeOf($0) } ?? 0
                let n = max(oldN, s.mesh.map { WarpMesh.sizeOf($0) } ?? 0)
                var base: [StrokePoint]? = nil
                if n > 0 {
                    if oldN == n { base = oldMesh } else if oldN > 0 { base = WarpMesh.resample(oldMesh!, oldN, n) } else { base = WarpMesh.onQuad(corners, n) }
                }
                let grid = base?.map { mp($0) }
                let newCorners = grid.map { g in [g[0], g[n - 1], g[n * n - 1], g[n * (n - 1)]] } ?? corners.map { mp($0) }
                var warp = st.warp ?? ImageWarp()
                warp.mesh = grid; warp.filter = s.filter.code
                o.points = newCorners + st.points.dropFirst(4).map { mp($0) }
                o.warp = warp.isEmpty ? nil : warp
                return o
            }
            if nonAffine && st.brushType.isQuad { skipped.insert(st.id); return st }
            o.points = st.points.map { mp($0) }
            o.clip = st.clip?.map { mp($0) }
            o.style2 = V56Vector.mapStyle(st.style2) { mp($0) }
            if !st.brushType.isQuad { o.sizePx = (st.sizePx * wf).coerceIn(0.5, 2000) }
            if var spec = st.shape {
                spec.style.strokeWidth = (spec.style.strokeWidth * wf).coerceIn(0.2, 400)
                spec.style.cornerRadius = spec.style.cornerRadius * wf
                o.shape = spec
            }
            if var m = st.mask { m.points = m.points.map { mp($0) }; o.mask = m }
            return o
        }
        return Applied(strokes: out, skipped: skipped)
    }
}

/// 2E göreli afin dönüşüm: p' = c + (dx, dy) + L·(p − c); L = [[a, b], [c, d]].
struct RelativeTransform: Equatable {
    var a: Float, b: Float, c: Float, d: Float, dx: Float, dy: Float
    var isIdentity: Bool { abs(a - 1) < 1e-5 && abs(b) < 1e-5 && abs(c) < 1e-5 && abs(d - 1) < 1e-5 && abs(dx) < 1e-3 && abs(dy) < 1e-3 }
    func map(_ cx: Float, _ cy: Float, _ x: Float, _ y: Float) -> (Float, Float) {
        (cx + dx + a * (x - cx) + b * (y - cy), cy + dy + c * (x - cx) + d * (y - cy))
    }
}

extension TransformOps {
    /// Durumun göreli afin karşılığı; ağ ya da perspektif ise nil.
    static func relative(_ s: TransformState) -> RelativeTransform? {
        if s.mesh != nil { return nil }
        let q = s.quad
        let w = s.r - s.l, h = s.b - s.t
        if w <= 0 || h <= 0 { return nil }
        let ex = q[1].x + q[3].x - q[0].x, ey = q[1].y + q[3].y - q[0].y
        if hypot(q[2].x - ex, q[2].y - ey) > max(0.5, max(widthOf(q), heightOf(q)) * 0.002) { return nil }
        let a = (q[1].x - q[0].x) / w, c = (q[1].y - q[0].y) / w
        let b = (q[3].x - q[0].x) / h, d = (q[3].y - q[0].y) / h
        let cx = (s.l + s.r) / 2, cy = (s.t + s.b) / 2
        let mx = q[0].x + a * (cx - s.l) + b * (cy - s.t), my = q[0].y + c * (cx - s.l) + d * (cy - s.t)
        return RelativeTransform(a: a, b: b, c: c, d: d, dx: mx - cx, dy: my - cy)
    }

    static func pointsBounds(_ strokes: [Stroke]) -> [Float]? {
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude, r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for s in strokes { for p in s.points { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) } }
        return l > r ? nil : [l, t, r, b]
    }

    /// Seçimin birimleri: aynı gruptakiler birlikte (belge sırasıyla).
    static func units(_ strokes: [Stroke], _ ids: Set<String>) -> [Set<String>] {
        var order: [String] = []
        var groups: [String: Set<String>] = [:]
        for s in strokes where ids.contains(s.id) {
            let k = s.groupId ?? ("#" + s.id)
            if groups[k] == nil { order.append(k); groups[k] = [] }
            groups[k]!.insert(s.id)
        }
        return order.map { groups[$0]! }
    }

    static func applyRelative(_ strokes: [Stroke], _ ids: Set<String>, _ rel: RelativeTransform, each: Bool) -> [Stroke] {
        let groups = each ? units(strokes, ids) : [ids]
        var out = strokes
        for g in groups {
            guard let bb = pointsBounds(out.filter { g.contains($0.id) }) else { continue }
            var st = TransformState.start(bb[0], bb[1], bb[2], bb[3], kind: .FREE)
            let cx = (st.l + st.r) / 2, cy = (st.t + st.b) / 2
            st.quad = st.box.map { let m = rel.map(cx, cy, $0.x, $0.y); return StrokePoint(m.0, m.1) }
            out = apply(out, g, st).strokes
        }
        return out
    }

    static func applyEach(_ strokes: [Stroke], _ ids: Set<String>, _ s: TransformState) -> Applied {
        guard let rel = relative(s) else { return apply(strokes, ids, s) }
        return Applied(strokes: applyRelative(strokes, ids, rel, each: true), skipped: [])
    }

    /// Referans noktası: 9 noktadan biri (0 sol-üst … 4 merkez … 8 sağ-alt).
    static func anchorPoint(_ q: [StrokePoint], _ index: Int) -> (Float, Float) {
        let u = Float(index % 3) / 2, v = Float(index / 3) / 2
        let tx = q[0].x + (q[1].x - q[0].x) * u, ty = q[0].y + (q[1].y - q[0].y) * u
        let bx = q[3].x + (q[2].x - q[3].x) * u, by = q[3].y + (q[2].y - q[3].y) * u
        return (tx + (bx - tx) * v, ty + (by - ty) * v)
    }
}

enum Reflection {
    static func offset(_ w: WaveSpec, _ x: Float, _ y: Float) -> (Float, Float) {
        if w.amplitude == 0 || w.wavelength <= 0 { return (0, 0) }
        let depth: Float = w.bottom > w.top ? ((y - w.top) / (w.bottom - w.top)).coerceIn(0, 1) : 1
        let twoPi = Float.pi * 2
        if w.ripple {
            let dx = x - w.cx, dy = y - w.top
            let d = hypot(dx, dy)
            if d < 1e-3 { return (0, 0) }
            let k = w.amplitude * depth * sin(twoPi * (d / w.wavelength + w.phase))
            return (dx / d * k, dy / d * k * 0.5)
        }
        return (w.amplitude * depth * sin(twoPi * ((y - w.top) / w.wavelength + w.phase)), 0)
    }

    static func fade(_ w: WaveSpec, _ y: Float) -> Float {
        if w.bottom <= w.top { return w.fadeFrom }
        let t = ((y - w.top) / (w.bottom - w.top)).coerceIn(0, 1)
        return w.fadeFrom + (w.fadeTo - w.fadeFrom) * t
    }

    static func create(
        _ strokes: [Stroke], _ ids: Set<String>, _ l: Float, _ t: Float, _ r: Float, _ b: Float, gap: Float, newId: (Stroke) -> String,
        wave: WaveSpec = WaveSpec(amplitude: 4, wavelength: 36, fadeFrom: 0.55, fadeTo: 0)
    ) -> [Stroke] {
        let sel = strokes.filter { ids.contains($0.id) && !$0.hidden }
        if sel.isEmpty { return strokes }
        let axis = b + gap / 2
        var spec = wave
        spec.top = b + gap; spec.bottom = b + gap + (b - t); spec.cx = (l + r) / 2
        func m(_ p: StrokePoint) -> StrokePoint { p.at(p.x, 2 * axis - p.y) }
        let copies = sel.enumerated().map { (i, s) -> Stroke in
            var c = s
            c.id = newId(s); c.order = strokes.count + i; c.groupId = s.groupId.map { "\($0)-r" }
            c.points = s.points.map(m); c.clip = s.clip?.map(m); c.style2 = V56Vector.mapStyle(s.style2, m)
            if var mk = s.mask { mk.points = mk.points.map(m); c.mask = mk }
            var w = s.warp ?? ImageWarp()
            w.wave = spec; w.mesh = s.warp?.mesh?.map(m); w.quad = s.warp?.quad?.map(m)
            c.warp = w
            return c
        }
        return strokes + copies
    }
}
