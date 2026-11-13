import CoreGraphics
import Foundation

/// Kotlin `Math.round(Float)`: floor(x + 0.5) (Int32).
@inline(__always) func kRound(_ v: Float) -> Int32 {
    if v.isNaN { return 0 }
    let r = (v + 0.5).rounded(.down)
    return Int32(max(-2147483648, min(2147483647, r)))
}

/// v22 döndürülebilir 3B sahnenin kamerası (Android `SceneCamera`).
struct SceneCamera {
    let yaw: Float, pitch: Float, persp: Float, cx: Float, cy: Float
    let rx: Float, ry: Float, rz: Float
    let dx: Float, dy: Float, dz: Float
    let tx: Float, ty: Float, tz: Float
    let focal: Float
    let key: Int32

    init(_ yaw: Float, _ pitch: Float, _ persp: Float, _ cx: Float, _ cy: Float, _ extent: Float) {
        self.yaw = yaw; self.pitch = pitch; self.persp = persp; self.cx = cx; self.cy = cy
        focal = persp < 0.01 ? .infinity : max(extent, 1) / (persp.coerceIn(0.01, 1) * 1.0)
        let y = Double(yaw) * .pi / 180, p = Double(pitch) * .pi / 180
        let cp = Float(cos(p)), sp = Float(sin(p)), cyw = Float(cos(y)), syw = Float(sin(y))
        tx = cp * syw; ty = sp; tz = cp * cyw
        rx = cyw; ry = 0; rz = -syw
        dx = ry * tz - rz * ty; dy = rz * tx - rx * tz; dz = rx * ty - ry * tx
        var h: Int32 = 17
        h = h &* 31 &+ kRound(yaw * 100)
        h = h &* 31 &+ kRound(pitch * 100)
        h = h &* 31 &+ kRound(persp * 1000)
        h = h &* 31 &+ kRound(cx * 4)
        h = h &* 31 &+ kRound(cy * 4)
        key = h
    }

    var perspective: Bool { focal.isFinite }

    func depth(_ x: Float, _ y: Float, _ z: Float) -> Float { x * tx + y * ty + z * tz }

    func scaleAt(_ zc: Float) -> Float { !perspective ? 1 : focal / max(focal - zc, focal * 0.1) }

    func project(_ x: Float, _ y: Float, _ z: Float) -> P {
        let xc = x * rx + y * ry + z * rz
        let yc = x * dx + y * dy + z * dz
        let s = scaleAt(x * tx + y * ty + z * tz)
        return P(cx + xc * s, cy + yc * s)
    }

    func project(_ v: [Float], _ o: Int = 0) -> P { project(v[o], v[o + 1], v[o + 2]) }

    func atDepth0(_ sx: Float, _ sy: Float) -> [Float] {
        let a = sx - cx, b = sy - cy
        return [rx * a + dx * b, ry * a + dy * b, rz * a + dz * b]
    }

    func ray(_ sx: Float, _ sy: Float) -> [Float] {
        let q = atDepth0(sx, sy)
        if !perspective { return [q[0], q[1], q[2], -tx, -ty, -tz] }
        let c0 = tx * focal, c1 = ty * focal, c2 = tz * focal
        return [c0, c1, c2, q[0] - c0, q[1] - c1, q[2] - c2]
    }

    func withView(yaw: Float? = nil, pitch: Float? = nil, persp: Float? = nil, extent: Float) -> SceneCamera {
        SceneCamera(yaw ?? self.yaw, pitch ?? self.pitch, persp ?? self.persp, cx, cy, extent)
    }
}

/// v22 döndürülebilir 3B sahne (Android `Scene3D`).
enum Scene3D {
    private static let EDGE_ON: Float = 0.18

    static func camera(_ guide: GuideSpec, _ width: Int, _ height: Int) -> SceneCamera {
        SceneCamera(guide.yaw3d, guide.pitch3d.coerceIn(-90, 90), guide.persp3d.coerceIn(0, 1), Float(width) / 2, Float(height) / 2, Float(max(width, height)))
    }

    static func planeAxes(_ plane: Plane3D, _ cam: SceneCamera) -> [Float] {
        switch plane {
        case .FRONT: return [0, 0, 0, 1, 0, 0, 0, -1, 0, 0, 0, 1]
        case .SIDE: return [0, 0, 0, 0, 0, -1, 0, -1, 0, 1, 0, 0]
        case .TOP: return [0, 0, 0, 1, 0, 0, 0, 0, 1, 0, -1, 0]
        case .FREE:
            return [0, 0, 0, cam.rx, cam.ry, cam.rz, cam.dx, cam.dy, cam.dz,
                    0.7071 * (cam.rx + cam.dx) - 0.7 * cam.tx, 0.7071 * (cam.ry + cam.dy) - 0.7 * cam.ty, 0.7071 * (cam.rz + cam.dz) - 0.7 * cam.tz]
        }
    }

    private static func normalOf(_ f: [Float]) -> [Float] {
        let nx = f[4] * f[8] - f[5] * f[7], ny = f[5] * f[6] - f[3] * f[8], nz = f[3] * f[7] - f[4] * f[6]
        let l = max(sqrtf(nx * nx + ny * ny + nz * nz), 1e-6)
        return [nx / l, ny / l, nz / l]
    }

    static func edgeOn(_ plane: Plane3D, _ cam: SceneCamera) -> Bool {
        if plane == .FREE { return false }
        let n = normalOf(planeAxes(plane, cam))
        return abs(n[0] * cam.tx + n[1] * cam.ty + n[2] * cam.tz) < EDGE_ON
    }

    static func effectivePlane(_ plane: Plane3D, _ cam: SceneCamera) -> Plane3D { edgeOn(plane, cam) ? .FREE : plane }

    private static func intersect(_ ray: [Float], _ p: [Float], _ n: [Float], _ perspective: Bool) -> [Float]? {
        let dn = ray[3] * n[0] + ray[4] * n[1] + ray[5] * n[2]
        let dl = sqrtf(ray[3] * ray[3] + ray[4] * ray[4] + ray[5] * ray[5])
        if abs(dn) < 1e-4 * dl { return nil }
        let t = ((p[0] - ray[0]) * n[0] + (p[1] - ray[1]) * n[1] + (p[2] - ray[2]) * n[2]) / dn
        if perspective && t <= 0 { return nil }
        return [ray[0] + t * ray[3], ray[1] + t * ray[4], ray[2] + t * ray[5]]
    }

    static func anchor(_ plane: Plane3D, _ cam: SceneCamera, _ sx: Float, _ sy: Float) -> [Float] {
        if plane == .FREE { return cam.atDepth0(sx, sy) }
        let n = normalOf(planeAxes(plane, cam))
        return intersect(cam.ray(sx, sy), [0, 0, 0], n, cam.perspective) ?? cam.atDepth0(sx, sy)
    }

    /// Çizim aracı için düzlem eşlemesi.
    struct DrawMapping {
        let plane: Plane3D
        let extrude: P
        init(_ plane: Plane3D, _ cam: SceneCamera, _ ax: Float, _ ay: Float) {
            self.plane = plane
            let f = Scene3D.planeAxes(plane, cam)
            let a = Scene3D.anchor(plane, cam, ax, ay)
            extrude = Scene3D.extrudeVector(cam, a, f)
        }
        func map(_ x: Float, _ y: Float) -> P { P(x, y) }
    }

    fileprivate static func extrudeVector(_ cam: SceneCamera, _ o: [Float], _ f: [Float]) -> P {
        let p0 = cam.project(o[0], o[1], o[2])
        let p1 = cam.project(o[0] + f[9], o[1] + f[10], o[2] + f[11])
        return P(p1.x - p0.x, p1.y - p0.y)
    }

    static func stamp(_ points: [StrokePoint]) -> Int64 {
        var h: Int64 = -3750763034362895579
        for p in points {
            h = (h ^ Int64(kRound(p.x * 8))) &* 1099511628211
            h = (h ^ Int64(kRound(p.y * 8))) &* 1099511628211
        }
        return h ^ Int64(points.count)
    }

    // MARK: İzler

    private static func derive(_ s: Stroke, _ d3: Depth3D, _ cam: SceneCamera) -> Stroke {
        let plane = effectivePlane(d3.plane, cam)
        var f = planeAxes(plane, cam)
        let a = anchor(plane, cam, s.points[0].x, s.points[0].y)
        f[0] = a[0]; f[1] = a[1]; f[2] = a[2]
        guard let local = localOf(s.points, f, cam) else { return s }
        let ev = extrudeVector(cam, a, f)
        var c = s
        c.space = Space3D(frame: f, local: local, stamp: stamp(s.points), view: cam.key)
        if var pr = s.params {
            var d = d3; d.plane = plane; d.ex = ev.x; d.ey = ev.y
            pr.depth3d = d
            c.params = pr
        }
        return c
    }

    private static func localOf(_ points: [StrokePoint], _ f: [Float], _ cam: SceneCamera) -> [Float]? {
        let n = normalOf(f)
        let o = [f[0], f[1], f[2]]
        var out = [Float](repeating: 0, count: points.count * 2)
        for (i, p) in points.enumerated() {
            guard let w = intersect(cam.ray(p.x, p.y), o, n, cam.perspective) else { return nil }
            let qx = w[0] - o[0], qy = w[1] - o[1], qz = w[2] - o[2]
            out[2 * i] = qx * f[3] + qy * f[4] + qz * f[5]
            out[2 * i + 1] = qx * f[6] + qy * f[7] + qz * f[8]
        }
        return out
    }

    private static func rederive(_ s: Stroke, _ sp: Space3D, _ cam: SceneCamera) -> Stroke {
        if s.points.isEmpty { return s }
        var f = sp.frame
        let n = normalOf(f)
        let a = intersect(cam.ray(s.points[0].x, s.points[0].y), [f[0], f[1], f[2]], n, cam.perspective)
        if let a { f[0] = a[0]; f[1] = a[1]; f[2] = a[2] }
        let local = a != nil ? localOf(s.points, f, cam) : nil
        var c = s
        c.space = local.map { Space3D(frame: f, local: $0, stamp: stamp(s.points), view: cam.key) } ?? sp.with(stamp: stamp(s.points), view: cam.key)
        return c
    }

    private static func reprojectStroke(_ s: Stroke, _ sp: Space3D, _ cam: SceneCamera) -> Stroke {
        let f = sp.frame
        let n = sp.pointCount
        let same = s.points.count == n
        let q = ContentCodec.quantize
        let pts: [StrokePoint] = (0..<n).map { i in
            let a = sp.local[2 * i], b = sp.local[2 * i + 1]
            let o = cam.project(f[0] + a * f[3] + b * f[6], f[1] + a * f[4] + b * f[7], f[2] + a * f[5] + b * f[8])
            return same ? s.points[i].at(q(o.x), q(o.y)) : StrokePoint(q(o.x), q(o.y))
        }
        let ev = extrudeVector(cam, [f[0], f[1], f[2]], f)
        var c = s
        if var pr = s.params, var d = pr.depth3d { d.ex = ev.x; d.ey = ev.y; pr.depth3d = d; c.params = pr }
        c.points = pts
        c.space = Space3D(frame: f, local: sp.local, stamp: stamp(pts), view: cam.key)
        return c
    }

    // MARK: Cisimler

    private static func extents(_ kind: String) -> [Float] {
        switch kind {
        case "SOLID_BOX": return [1.4, 0.85, 0.9]
        case "SOLID_CYLINDER", "SOLID_CONE": return [1, 1.2, 1]
        case "SOLID_DOME": return [1, 0.5, 1]
        case "SOLID_PRISM": return [1.4, 0.9, 1]
        default: return [1, 1, 1]
        }
    }

    static func isSolidKind(_ kind: String?) -> Bool { kind?.hasPrefix("SOLID_") == true }

    static func solidStroke(_ kind: String, start: P, end: P, style: ShapeStyle, cam: SceneCamera, id: String, order: Int, opacity: Float = 1) -> Stroke? {
        let l = min(start.x, end.x), r = max(start.x, end.x), t = min(start.y, end.y), b = max(start.y, end.y)
        let rw = max(r - l, 8), rh = max(b - t, 8)
        let c = cam.atDepth0((l + r) / 2, (t + b) / 2)
        let e = extents(kind)
        var f: [Float] = [c[0], c[1], c[2], e[0], 0, 0, 0, e[1], 0, 0, 0, e[2]]
        let bx = boxOf(f, cam)
        let k = min(rw / max(bx.width, 1e-3), rh / max(bx.height, 1e-3))
        for i in 3..<12 { f[i] *= k }
        let paths = solidPaths(kind, f, cam)
        guard var built = ShapeFactory.fromPaths(kind, paths, style: style, graph: nil, id: id, order: order, opacity: opacity) else { return nil }
        built.space = Space3D(frame: f, local: [], stamp: stamp(built.points), view: cam.key)
        return built
    }

    private static func boxOf(_ f: [Float], _ cam: SceneCamera) -> FRect {
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude, r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for su: Float in [-1, 1] { for sv: Float in [-1, 1] { for sw: Float in [-1, 1] {
            let p = cam.project(f[0] + su * f[3] + sv * f[6] + sw * f[9], f[1] + su * f[4] + sv * f[7] + sw * f[10], f[2] + su * f[5] + sv * f[8] + sw * f[11])
            l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y)
        } } }
        return FRect(l, t, r, b)
    }

    private static func rebuildSolid(_ s: Stroke, _ f: [Float], _ cam: SceneCamera) -> Stroke {
        guard let spec = s.shape,
              var built = ShapeFactory.fromPaths(spec.kind, solidPaths(spec.kind, f, cam), style: spec.style, graph: nil, id: s.id, order: s.order, opacity: s.opacity)
        else { return s }
        built.groupId = s.groupId; built.layerId = s.layerId; built.params = s.params; built.flow = s.flow; built.strength = s.strength
        built.space = Space3D(frame: f, local: [], stamp: stamp(built.points), view: cam.key)
        return built
    }

    private static func resolidify(_ s: Stroke, _ sp: Space3D, _ cam: SceneCamera) -> Stroke {
        guard let nb = pointBounds(s.points) else { return s }
        let ob = boxOf(sp.frame, cam)
        var f = sp.frame
        let same = abs(nb.center.x - ob.center.x) < 1.5 && abs(nb.center.y - ob.center.y) < 1.5 &&
            abs(nb.width - ob.width) < 0.03 * ob.width + 1 && abs(nb.height - ob.height) < 0.03 * ob.height + 1
        if !same {
            let k = sqrtf(max(nb.width, 1) * max(nb.height, 1) / (max(ob.width, 1) * max(ob.height, 1))).coerceIn(0.05, 20)
            for i in 3..<12 { f[i] *= k }
            let sb = boxOf(f, cam)
            let zc = cam.depth(f[0], f[1], f[2])
            let sc = cam.scaleAt(zc)
            let ddx = (nb.center.x - sb.center.x) / sc, ddy = (nb.center.y - sb.center.y) / sc
            f[0] += cam.rx * ddx + cam.dx * ddy; f[1] += cam.ry * ddx + cam.dy * ddy; f[2] += cam.rz * ddx + cam.dz * ddy
        }
        return rebuildSolid(s, f, cam)
    }

    private static func pointBounds(_ points: [StrokePoint]) -> FRect? {
        if points.isEmpty { return nil }
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude, r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for p in points { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
        return FRect(l, t, r, b)
    }

    // MARK: Eşitleme

    static func involves(_ s: Stroke) -> Bool {
        s.space != nil || (s.params?.depth3d != nil && s.brushType != .SHAPE && s.brushType != .ERASER)
    }

    static func sync(_ s: Stroke, _ cam: SceneCamera) -> Stroke {
        guard let sp = s.space else {
            guard let d3 = s.params?.depth3d else { return s }
            if s.brushType == .SHAPE || s.brushType == .ERASER || s.points.isEmpty { return s }
            return derive(s, d3, cam)
        }
        let h = stamp(s.points)
        if h != sp.stamp { return sp.isSolid ? resolidify(s, sp, cam) : rederive(s, sp, cam) }
        if sp.view != cam.key { return sp.isSolid ? rebuildSolid(s, sp.frame, cam) : reprojectStroke(s, sp, cam) }
        return s
    }

    /// Sayfaların eşitlenmiş hali; hiçbir şey değişmediyse nil.
    static func syncPages(_ pages: [[Stroke]], _ guide: GuideSpec, _ width: Int, _ height: Int) -> [[Stroke]]? {
        if !pages.contains(where: { $0.contains(where: involves) }) { return nil }
        let cam = camera(guide, width, height)
        var changed = false
        let out = pages.map { page -> [Stroke] in
            if !page.contains(where: involves) { return page }
            var pageChanged = false
            let next = page.map { s -> Stroke in
                if !involves(s) { return s }
                let r = sync(s, cam)
                if r != s { pageChanged = true }
                return r
            }
            if pageChanged { changed = true; return next }
            return page
        }
        return changed ? out : nil
    }

    // MARK: Cisim ağları

    private struct Face { let pts: [[Float]]; let curved: Bool }
    private static let SEG = 28
    private static let meshLock = NSLock()
    nonisolated(unsafe) private static var meshes = [String: [Face]]()

    private static func mesh(_ kind: String) -> [Face] {
        meshLock.lock(); defer { meshLock.unlock() }
        if let m = meshes[kind] { return m }
        let m = buildMesh(kind)
        meshes[kind] = m
        return m
    }

    private static func buildMesh(_ kind: String) -> [Face] {
        var out: [Face] = []
        func v(_ u: Float, _ vv: Float, _ w: Float) -> [Float] { [u, vv, w] }
        func quad(_ a: [Float], _ b: [Float], _ c: [Float], _ d: [Float], curved: Bool = false) { out.append(Face(pts: [a, b, c, d], curved: curved)) }
        func tri(_ a: [Float], _ b: [Float], _ c: [Float], curved: Bool = false) { out.append(Face(pts: [a, b, c], curved: curved)) }
        func ring(_ n: Int, _ y: Float, _ r: Float = 1) -> [[Float]] {
            (0..<n).map { i in
                let a = 2.0 * Double.pi * Double(i) / Double(n)
                return v(Float(Double(r) * cos(a)), y, Float(Double(r) * sin(a)))
            }
        }
        switch kind {
        case "SOLID_CYLINDER":
            let top = ring(SEG, 1), bot = ring(SEG, -1)
            for i in 0..<SEG { let j = (i + 1) % SEG; quad(bot[i], bot[j], top[j], top[i], curved: true) }
            out.append(Face(pts: top, curved: false)); out.append(Face(pts: bot.reversed(), curved: false))
        case "SOLID_CONE":
            let bot = ring(SEG, -1), apex = v(0, 1, 0)
            for i in 0..<SEG { let j = (i + 1) % SEG; tri(bot[i], bot[j], apex, curved: true) }
            out.append(Face(pts: bot.reversed(), curved: false))
        case "SOLID_SPHERE", "SOLID_DOME":
            let dome = kind == "SOLID_DOME"
            let lat = dome ? 7 : 12
            let lon = 24
            func p(_ i: Int, _ j: Int) -> [Float] {
                let a = dome ? Double.pi / 2 * Double(i) / Double(lat) : -Double.pi / 2 + Double.pi * Double(i) / Double(lat)
                let b = 2 * Double.pi * Double(j) / Double(lon)
                let y: Float = dome ? -1 + 2 * Float(sin(a)) : Float(sin(a))
                return v(Float(cos(a) * cos(b)), y, Float(cos(a) * sin(b)))
            }
            for i in 0..<lat { for j in 0..<lon {
                let a = p(i, j), b = p(i, j + 1), c = p(i + 1, j + 1), d = p(i + 1, j)
                if i == lat - 1 { tri(a, b, c, curved: true) } else if !dome && i == 0 { tri(a, c, d, curved: true) } else { quad(a, b, c, d, curved: true) }
            } }
            if dome { out.append(Face(pts: ring(SEG, -1).reversed(), curved: false)) }
        case "SOLID_PYRAMID":
            let b = [v(-1, -1, -1), v(1, -1, -1), v(1, -1, 1), v(-1, -1, 1)], apex = v(0, 1, 0)
            for i in 0..<4 { tri(b[i], b[(i + 1) % 4], apex) }
            out.append(Face(pts: b, curved: false))
        case "SOLID_PRISM":
            let sec: [[Float]] = [[1, 0], [-1, -1], [-1, 1]]
            let l = (0..<3).map { v(-1, sec[$0][0], sec[$0][1]) }, r = (0..<3).map { v(1, sec[$0][0], sec[$0][1]) }
            out.append(Face(pts: l, curved: false)); out.append(Face(pts: r, curved: false))
            for i in 0..<3 { let j = (i + 1) % 3; quad(l[i], l[j], r[j], r[i]) }
        case "SOLID_HEX_PRISM":
            let top = ring(6, 1), bot = ring(6, -1)
            for i in 0..<6 { let j = (i + 1) % 6; quad(bot[i], bot[j], top[j], top[i]) }
            out.append(Face(pts: top, curved: false)); out.append(Face(pts: bot, curved: false))
        default:
            let c = (0..<8).map { i in v(i & 1 != 0 ? 1 : -1, i & 2 != 0 ? 1 : -1, i & 4 != 0 ? 1 : -1) }
            quad(c[0], c[1], c[3], c[2]); quad(c[4], c[5], c[7], c[6])
            quad(c[0], c[1], c[5], c[4]); quad(c[2], c[3], c[7], c[6])
            quad(c[0], c[2], c[6], c[4]); quad(c[1], c[3], c[7], c[5])
        }
        return out
    }

    private static let EDGE: Float = 0.55

    static func solidPaths(_ kind: String, _ f: [Float], _ cam: SceneCamera) -> [ShapePath] {
        let faces = mesh(kind)
        func world(_ p: [Float]) -> [Float] {
            [f[0] + p[0] * f[3] + p[1] * f[6] + p[2] * f[9],
             f[1] + p[0] * f[4] + p[1] * f[7] + p[2] * f[10],
             f[2] + p[0] * f[5] + p[1] * f[8] + p[2] * f[11]]
        }
        var lx = -0.45 * cam.rx - 0.65 * cam.dx + 0.6 * cam.tx
        var ly = -0.45 * cam.ry - 0.65 * cam.dy + 0.6 * cam.ty
        var lz = -0.45 * cam.rz - 0.65 * cam.dz + 0.6 * cam.tz
        let ll = sqrtf(lx * lx + ly * ly + lz * lz); lx /= ll; ly /= ll; lz /= ll
        let center = world(kind == "SOLID_DOME" ? [0, -0.6, 0] : [0, 0, 0])
        var drawn: [(depth: Float, path: ShapePath)] = []
        var all: [P] = []
        for face in faces {
            let w = face.pts.map(world)
            var cxw: Float = 0, cyw: Float = 0, czw: Float = 0
            for p in w { cxw += p[0]; cyw += p[1]; czw += p[2] }
            let cnt = Float(w.count)
            cxw /= cnt; cyw /= cnt; czw /= cnt
            var nx: Float = 0, ny: Float = 0, nz: Float = 0
            for i in w.indices {
                let a = w[i], b = w[(i + 1) % w.count]
                nx += (a[1] - b[1]) * (a[2] + b[2]); ny += (a[2] - b[2]) * (a[0] + b[0]); nz += (a[0] - b[0]) * (a[1] + b[1])
            }
            let nl = sqrtf(nx * nx + ny * ny + nz * nz)
            if nl < 1e-9 { continue }
            nx /= nl; ny /= nl; nz /= nl
            if nx * (cxw - center[0]) + ny * (cyw - center[1]) + nz * (czw - center[2]) < 0 { nx = -nx; ny = -ny; nz = -nz }
            let vis = !cam.perspective ? nx * cam.tx + ny * cam.ty + nz * cam.tz
                : nx * (cam.tx * cam.focal - cxw) + ny * (cam.ty * cam.focal - cyw) + nz * (cam.tz * cam.focal - czw)
            let pts = w.map { cam.project($0[0], $0[1], $0[2]) }
            all += pts
            if vis <= 1e-4 { continue }
            let diffuse = max(0, nx * lx + ny * ly + nz * lz)
            let facing = max(0, nx * cam.tx + ny * cam.ty + nz * cam.tz)
            let level = (0.26 + 0.6 * diffuse + 0.1 * facing).coerceIn(0.05, 0.95)
            let q = Float(kRound(level * 40)) / 40
            let poly = face.curved ? expand(pts, 0.45) : pts
            drawn.append((cam.depth(cxw, cyw, czw),
                          ShapePath(points: poly + [poly[0]], closed: true, fillColorHex: ShapeRenderer.shade(q), widthScale: face.curved ? 0 : EDGE)))
        }
        // Kotlin sortBy kararlıdır
        let sorted = drawn.enumerated().sorted { $0.element.depth != $1.element.depth ? $0.element.depth < $1.element.depth : $0.offset < $1.offset }
        var out = sorted.map { $0.element.path }
        let hull = convexHull(all)
        if hull.count >= 3 { out.append(ShapePath(points: hull + [hull[0]], closed: true, fillColorHex: ShapeRenderer.NO_FILL)) }
        return out
    }

    private static func expand(_ pts: [P], _ d: Float) -> [P] {
        var cx: Float = 0, cy: Float = 0
        for p in pts { cx += p.x; cy += p.y }
        cx /= Float(pts.count); cy /= Float(pts.count)
        return pts.map { p in
            let ex = p.x - cx, ey = p.y - cy
            let l = sqrtf(ex * ex + ey * ey)
            return l < 1e-4 ? p : P(p.x + ex / l * d, p.y + ey / l * d)
        }
    }

    /// Dışbükey zarf (Andrew'un monoton zinciri).
    static func convexHull(_ points: [P]) -> [P] {
        var seen = Set<[UInt32]>()
        var uniq: [P] = []
        for p in points where seen.insert([p.x.bitPattern, p.y.bitPattern]).inserted { uniq.append(p) }
        let pts = uniq.sorted { $0.x != $1.x ? $0.x < $1.x : $0.y < $1.y }
        if pts.count < 3 { return pts }
        func cross(_ o: P, _ a: P, _ b: P) -> Float { (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x) }
        var lower: [P] = []
        for p in pts { while lower.count >= 2 && cross(lower[lower.count - 2], lower[lower.count - 1], p) <= 0 { lower.removeLast() }; lower.append(p) }
        var upper: [P] = []
        for p in pts.reversed() { while upper.count >= 2 && cross(upper[upper.count - 2], upper[upper.count - 1], p) <= 0 { upper.removeLast() }; upper.append(p) }
        lower.removeLast(); upper.removeLast()
        return lower + upper
    }
}

extension GuideRenderer {
    /// v22 3B sahnenin ızgarası (Android `GuideRenderer.drawDraw3d`): üç düzlemin (ön X–Y, yan Z–Y, üst X–Z) sahne
    /// merkezinden geçen çizgileri; etkin düzlem belirgin, ötekiler soluk; eksenler biraz daha koyu. Kamera döndükçe döner.
    static func drawDraw3d(_ c: CGContext, _ spec: GuideSpec, paper: CGRect, scale: Float, canvasW: Int, canvasH: Int) {
        guard spec.draw3d else { return }
        let width = CGFloat(max(0.7, 1.1 * min(1.5, max(0.6, scale))))
        let base = ArgbColor.parse(spec.colorHex) ?? 0xFF7C_89A6
        func color(_ a: Double) -> CGColor { ArgbColor.cgColor(ArgbColor.withAlpha(base, Int((a * 255).rounded()))) }
        let weak = color(0.12), strong = color(0.4), axis = color(0.62)
        let cam = Scene3D.camera(spec, canvasW, canvasH)
        let kx = paper.width / CGFloat(max(1, canvasW)), ky = paper.height / CGFloat(max(1, canvasH))
        func screen(_ x: Float, _ y: Float, _ z: Float) -> CGPoint {
            let p = cam.project(x, y, z)
            return CGPoint(x: paper.minX + CGFloat(p.x) * kx, y: paper.minY + CGFloat(p.y) * ky)
        }
        func line(_ a: CGPoint, _ b: CGPoint, _ col: CGColor, _ w: CGFloat) {
            c.setStrokeColor(col); c.setLineWidth(w)
            c.beginPath(); c.move(to: a); c.addLine(to: b); c.strokePath()
        }
        let extent = Float(max(canvasW, canvasH))
        let reach = extent * 0.42
        let step = max(20, spec.spacing)
        let n = Int(reach / step)
        let r = Float(n) * step
        func grid(_ plane: Plane3D, _ col: CGColor, _ w: CGFloat) {
            guard n >= 0 else { return }
            for i in -n...n {
                let k = Float(i) * step
                let pts: [CGPoint]
                switch plane {
                case .FRONT: pts = [screen(k, -r, 0), screen(k, r, 0), screen(-r, k, 0), screen(r, k, 0)]
                case .SIDE: pts = [screen(0, -r, k), screen(0, r, k), screen(0, k, -r), screen(0, k, r)]
                default: pts = [screen(k, 0, -r), screen(k, 0, r), screen(-r, 0, k), screen(r, 0, k)]
                }
                line(pts[0], pts[1], col, w); line(pts[2], pts[3], col, w)
            }
        }
        let active = spec.plane3d
        c.saveGState()
        defer { c.restoreGState() }
        c.clip(to: paper)
        c.setLineCap(.butt)
        for p in [Plane3D.TOP, .FRONT, .SIDE] where p != active { grid(p, weak, width * 0.8) }
        if active != .FREE { grid(active, strong, width) }
        line(screen(-r, 0, 0), screen(r, 0, 0), axis, width * 1.3)
        line(screen(0, -r, 0), screen(0, r, 0), axis, width * 1.3)
        line(screen(0, 0, -r), screen(0, 0, r), axis, width * 1.3)
    }
}
