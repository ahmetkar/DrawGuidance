import Foundation

/// Çizim ayarları (Android `RenderOptions`).
struct RenderOptions {
    var textures: (String) -> Texture? = { _ in nil }
    var supersample: Int = 2
    var bilinear: Bool = true
    var ground: Bool? = nil
    var shadow: Bool? = nil
    var background: UInt32? = nil
    var selectedPart: String? = nil
    var selectedFaces: Set<Int> = []
    var selectedParts: Set<String> = []
    var wireframe: Bool = false
    var highlight: UInt32 = 0xFF2F_80ED
}

/// Çizilmiş kare (Android `Frame`): ARGB, derinlik, parça (+1; çizgide −(sıra+1)), yüz.
final class Frame3 {
    let width: Int, height: Int
    let color: [UInt32]
    let depth: [Float]
    let part: [Int32]
    let face: [Int32]
    let view: View3

    init(_ width: Int, _ height: Int, _ color: [UInt32], _ depth: [Float], _ part: [Int32], _ face: [Int32], _ view: View3) {
        self.width = width; self.height = height; self.color = color; self.depth = depth; self.part = part; self.face = face; self.view = view
    }

    func index(_ x: Int, _ y: Int) -> Int { y * width + x }

    func pick(_ x: Float, _ y: Float, _ radius: Int = 0) -> (Int, Int)? {
        let cx = javaRoundF(x), cy = javaRoundF(y)
        var best: (Int, Int)?, bestD = Int.max
        for j in -radius...radius { for i in -radius...radius {
            let px = cx + i, py = cy + j
            if px < 0 || px >= width || py < 0 || py >= height { continue }
            let p = Int(part[index(px, py)])
            if p == 0 { continue }
            let d = i * i + j * j
            if d < bestD { bestD = d; best = (p, Int(face[index(px, py)])) }
        } }
        return best
    }
}

/// v23 yazılım 3B çizici (z-tampon) (Android `Renderer`): aynı kod düzenleyicide, dışa aktarmada ve testlerde aynı sonucu verir.
enum Renderer3D {

    private static let CREASE_COS: Float = 0.7
    private static let FEATURE_COS: Float = 0.82
    fileprivate static let VS = 9
    private static let GAP_FILL = 3

    static func render(_ model: Model3D, _ view: View3, _ options: RenderOptions = RenderOptions()) -> Frame3 {
        let q = options.supersample.coerceIn(1, 3)
        let w = view.width * q, h = view.height * q
        let ss = View3(view.camera, w, h, view.scale * Float(q), view.centerX * Float(q), view.centerY * Float(q))
        let r = Raster(w, h, ss, model, options, q)
        r.draw()
        return r.output(view)
    }

    /// v29 modelin görünen çizgileri (vektör aktarma için), ekran koordinatlarında çoklu çizgiler.
    static func outlines(_ model: Model3D, _ view: View3, _ step: Float = 1.5) -> [[Float]] {
        var opt = RenderOptions()
        opt.supersample = 1; opt.ground = false; opt.shadow = false; opt.background = 0
        let r = Raster(view.width, view.height, view, model, opt, 1)
        r.prepare()
        for p in r.prepared { r.drawPart(p, false) }
        return chain(dedupe(r.visibleEdges(step.coerceAtLeast(0.5)))).filter { pl in
            var len: Float = 0
            var i = 2
            while i < pl.count { len += Float(hypot(Double(pl[i] - pl[i - 2]), Double(pl[i + 1] - pl[i - 1]))); i += 2 }
            return len >= 4
        }
    }

    static func dedupe(_ segments: [[Float]], _ tolerance: Float = 1.5) -> [[Float]] {
        var out: [[Float]] = []
        var seen: [Int64: [[Float]]] = [:]
        func cell(_ x: Float, _ y: Float) -> Int64 { (Int64(javaRoundF(x / 4)) << 32) ^ (Int64(javaRoundF(y / 4)) & 0xFFFF_FFFF) }
        func close(_ x0: Float, _ y0: Float, _ x1: Float, _ y1: Float) -> Bool { abs(x0 - x1) <= tolerance && abs(y0 - y1) <= tolerance }
        func same(_ a: [Float], _ b: [Float]) -> Bool {
            (close(a[0], a[1], b[0], b[1]) && close(a[2], a[3], b[2], b[3])) || (close(a[0], a[1], b[2], b[3]) && close(a[2], a[3], b[0], b[1]))
        }
        for s in segments {
            let k = cell((s[0] + s[2]) / 2, (s[1] + s[3]) / 2)
            if seen[k, default: []].contains(where: { same($0, s) }) { continue }
            seen[k, default: []].append(s)
            out.append(s)
        }
        return out
    }

    static func chain(_ segments: [[Float]], _ tolerance: Float = 2) -> [[Float]] {
        if segments.isEmpty { return [] }
        let cell = tolerance.coerceAtLeast(0.5)
        func key(_ cx: Int, _ cy: Int) -> Int64 { (Int64(cx) << 32) ^ (Int64(cy) & 0xFFFF_FFFF) }
        func g(_ v: Float) -> Int { Int((v / cell).rounded(.down)) }
        var grid: [Int64: [Int]] = [:]
        for (i, s) in segments.enumerated() {
            grid[key(g(s[0]), g(s[1])), default: []].append(i)
            grid[key(g(s[2]), g(s[3])), default: []].append(i)
        }
        var used = [Bool](repeating: false, count: segments.count)
        func near(_ x: Float, _ y: Float) -> (Int, Bool)? {
            let gx = g(x), gy = g(y)
            var best: (Int, Bool)?, bd = tolerance * tolerance
            for oy in -1...1 { for ox in -1...1 {
                guard let list = grid[key(gx + ox, gy + oy)] else { continue }
                for i in list where !used[i] {
                    let s = segments[i]
                    let d0 = (s[0] - x) * (s[0] - x) + (s[1] - y) * (s[1] - y)
                    let d1 = (s[2] - x) * (s[2] - x) + (s[3] - y) * (s[3] - y)
                    if d0 <= bd { bd = d0; best = (i, true) }
                    if d1 <= bd { bd = d1; best = (i, false) }
                }
            } }
            return best
        }
        var out: [[Float]] = []
        for start in segments.indices where !used[start] {
            used[start] = true
            let s0 = segments[start]
            var tail: [Float] = [s0[0], s0[1], s0[2], s0[3]]
            var head: [Float] = []   // ters sırada başa eklenenler (x, y çiftleri olarak y, x)
            for forward in [true, false] {
                while true {
                    let x: Float, y: Float
                    if forward { x = tail[tail.count - 2]; y = tail[tail.count - 1] }
                    else if head.count >= 2 { x = head[head.count - 1]; y = head[head.count - 2] }
                    else { x = tail[0]; y = tail[1] }
                    guard let (next, atStart) = near(x, y) else { break }
                    used[next] = true
                    let s = segments[next]
                    let nx = atStart ? s[2] : s[0], ny = atStart ? s[3] : s[1]
                    if forward { tail += [nx, ny] } else { head += [ny, nx] }
                }
            }
            out.append(head.reversed() + tail)
        }
        return out
    }

    fileprivate final class Prepared {
        let index: Int
        let part: Part
        let world: [Float], screen: [Float], normals: [Float]
        let corners: [[Float]]?
        let uv: [[Float]]
        let cull: Bool
        init(_ index: Int, _ part: Part, _ world: [Float], _ screen: [Float], _ normals: [Float], _ corners: [[Float]]?, _ uv: [[Float]], _ cull: Bool) {
            self.index = index; self.part = part; self.world = world; self.screen = screen; self.normals = normals
            self.corners = corners; self.uv = uv; self.cull = cull
        }
    }

    fileprivate final class Raster {
        let w: Int, h: Int
        let view: View3
        let model: Model3D
        let opt: RenderOptions
        let q: Int
        var color: [UInt32]
        var depth: [Float]
        var partId: [Int32]
        var faceId: [Int32]
        let scene: SceneSettings
        let light: Light
        let L: V3, T: V3, H: V3
        let lightR: Float, lightG: Float, lightB: Float
        var prepared: [Prepared] = []
        var groundY: Float = 0
        var extent: Float = 1
        private var tri = [Float](repeating: 0, count: Renderer3D.VS * 3)
        private var transparent: [(Float, Int, Int)] = []

        init(_ w: Int, _ h: Int, _ view: View3, _ model: Model3D, _ opt: RenderOptions, _ q: Int) {
            self.w = w; self.h = h; self.view = view; self.model = model; self.opt = opt; self.q = q
            color = [UInt32](repeating: 0, count: w * h)
            depth = [Float](repeating: -.infinity, count: w * h)
            partId = [Int32](repeating: 0, count: w * h)
            faceId = [Int32](repeating: 0, count: w * h)
            scene = model.scene
            light = scene.light
            L = light.direction
            T = view.toViewer
            H = (L + T).normalized()
            let lc = UInt32(bitPattern: light.color)
            lightR = Float((lc >> 16) & 0xFF) / 255; lightG = Float((lc >> 8) & 0xFF) / 255; lightB = Float(lc & 0xFF) / 255
        }

        func draw() {
            let bg = opt.background ?? UInt32(bitPattern: scene.background)
            if bg != 0 { for i in color.indices { color[i] = bg } }
            prepare()
            if opt.ground ?? scene.ground { drawGround() }
            if opt.shadow ?? scene.shadow { drawShadow() }
            for p in prepared { drawPart(p, false) }
            drawStrokes()
            if scene.edges || opt.selectedPart != nil || !opt.selectedParts.isEmpty { drawEdges() }
            drawTransparent()
        }

        // MARK: hazırlık

        func prepare() {
            var minY = Float.infinity
            var box: Box3?
            for (index, part) in model.parts.enumerated() {
                if !part.visible || part.mesh.vertexCount == 0 { continue }
                let t = Transform3.of(part)
                let mesh = part.mesh
                let n = mesh.vertexCount
                var world = [Float](repeating: 0, count: n * 3)
                var screen = [Float](repeating: 0, count: n * 4)
                for i in 0..<n {
                    t.apply(mesh.pos[3 * i], mesh.pos[3 * i + 1], mesh.pos[3 * i + 2], &world, 3 * i)
                    view.project(world[3 * i], world[3 * i + 1], world[3 * i + 2], &screen, 4 * i)
                    minY = min(minY, world[3 * i + 1])
                }
                let b = boundsOf(world)
                box = box?.union(b) ?? b
                let fn = mesh.faceNormals
                var normals = [Float](repeating: 0, count: mesh.faceCount * 3)
                for f in 0..<mesh.faceCount { t.normal(fn[3 * f], fn[3 * f + 1], fn[3 * f + 2], &normals, 3 * f) }
                var corners: [[Float]]?
                if part.smooth {
                    let cn = mesh.cornerNormals(Renderer3D.CREASE_COS)
                    corners = cn.map { src in
                        var out = [Float](repeating: 0, count: src.count)
                        for k in 0..<(src.count / 3) { t.normal(src[3 * k], src[3 * k + 1], src[3 * k + 2], &out, 3 * k) }
                        return out
                    }
                }
                prepared.append(Prepared(index, part, world, screen, normals, corners, UvMapper.cached(part), mesh.closed))
            }
            for s in model.strokes { for i in 0..<s.count { minY = min(minY, s.points[3 * i + 1]) } }
            groundY = minY.isFinite ? min(0, minY) : 0
            extent = box.map { $0.radius.coerceAtLeast(0.25) } ?? 1
        }

        func boundsOf(_ p: [Float]) -> Box3 {
            var ax = Float.infinity, ay = Float.infinity, az = Float.infinity
            var bx = -Float.infinity, by = -Float.infinity, bz = -Float.infinity
            var i = 0
            while i < p.count {
                ax = min(ax, p[i]); ay = min(ay, p[i + 1]); az = min(az, p[i + 2]); bx = max(bx, p[i]); by = max(by, p[i + 1]); bz = max(bz, p[i + 2])
                i += 3
            }
            return Box3(V3(ax, ay, az), V3(bx, by, bz))
        }

        // MARK: zemin ve gölge

        func drawGround() {
            let size = max(2, (extent * 2.5).rounded(.up))
            let step: Float = size > 6 ? 1 : 0.5
            let gc: UInt32 = 0x2E00_0000 | (UInt32(bitPattern: scene.edgeColor) & 0x00FF_FFFF)
            var a = [Float](repeating: 0, count: 4), b = a
            var v = -size
            while v <= size + 1e-3 {
                view.project(v, groundY, -size, &a); view.project(v, groundY, size, &b)
                lineNoDepth(a, b, abs(v) < 1e-3 ? 0x804A_90E2 : gc, Float(q))
                view.project(-size, groundY, v, &a); view.project(size, groundY, v, &b)
                lineNoDepth(a, b, abs(v) < 1e-3 ? 0x80E2_5C4A : gc, Float(q))
                v += step
            }
        }

        func drawShadow() {
            if L.y < 0.08 { return }
            var mask = [Float](repeating: 0, count: w * h)
            let ly = max(L.y, 0.3)
            var sp = [Float](repeating: 0, count: 12)
            for p in prepared {
                if p.part.material.transparent && p.part.material.opacity < 0.5 { continue }
                let m = p.part.mesh
                let n = m.vertexCount
                var proj = [Float](repeating: 0, count: n * 4)
                for i in 0..<n {
                    let x = p.world[3 * i], y = p.world[3 * i + 1], z = p.world[3 * i + 2]
                    let t = (y - groundY) / ly
                    view.project(x - L.x * t, groundY, z - L.z * t, &proj, 4 * i)
                }
                let triangles = m.triangles
                for f in 0..<m.faceCount {
                    let face = m.faces[f], tris = triangles[f]
                    var k = 0
                    while k + 2 < tris.count {
                        var ok = true
                        for c in 0..<3 {
                            let i = face[tris[k + c]]
                            for j in 0..<4 { sp[4 * c + j] = proj[4 * i + j] }
                            if sp[4 * c + 3] <= 0 { ok = false }
                        }
                        if ok { fillMask(&mask, sp) }
                        k += 3
                    }
                }
            }
            let radius = max(1, javaRoundF(Float(min(w, h)) * 0.012))
            blur(&mask, radius)
            blur(&mask, radius)
            let strength = 0.34 * light.intensity.coerceIn(0, 1.5)
            for i in mask.indices {
                let m = mask[i]
                if m <= 0.002 { continue }
                color[i] = Renderer3D.over(color[i], 0, (m * strength).coerceIn(0, 0.6))
            }
        }

        func fillMask(_ mask: inout [Float], _ v: [Float]) {
            let x0 = v[0], y0 = v[1], x1 = v[4], y1 = v[5], x2 = v[8], y2 = v[9]
            let area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if abs(area) < 1e-6 { return }
            let minX = max(0, Int(min(x0, min(x1, x2)).rounded(.down))), maxX = min(w - 1, Int(max(x0, max(x1, x2)).rounded(.up)))
            let minY = max(0, Int(min(y0, min(y1, y2)).rounded(.down))), maxY = min(h - 1, Int(max(y0, max(y1, y2)).rounded(.up)))
            if minX > maxX || minY > maxY { return }
            let sign: Float = area < 0 ? -1 : 1
            for py in minY...maxY {
                let cy = Float(py) + 0.5
                for px in minX...maxX {
                    let cx = Float(px) + 0.5
                    let w0 = ((x2 - x1) * (cy - y1) - (y2 - y1) * (cx - x1)) * sign
                    let w1 = ((x0 - x2) * (cy - y2) - (y0 - y2) * (cx - x2)) * sign
                    let w2 = ((x1 - x0) * (cy - y0) - (y1 - y0) * (cx - x0)) * sign
                    if w0 >= 0 && w1 >= 0 && w2 >= 0 { mask[py * w + px] = 1 }
                }
            }
        }

        func blur(_ m: inout [Float], _ r: Int) {
            var tmp = [Float](repeating: 0, count: m.count)
            let win = Float(2 * r) + 1
            for y in 0..<h {
                var acc: Float = 0
                let row = y * w
                for x in -r...r { acc += m[row + x.coerceIn(0, w - 1)] }
                for x in 0..<w {
                    tmp[row + x] = acc / win
                    acc += m[row + (x + r + 1).coerceAtMost(w - 1)] - m[row + (x - r).coerceAtLeast(0)]
                }
            }
            for x in 0..<w {
                var acc: Float = 0
                for y in -r...r { acc += tmp[y.coerceIn(0, h - 1) * w + x] }
                for y in 0..<h {
                    m[y * w + x] = acc / win
                    acc += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
                }
            }
        }

        // MARK: yüzler

        func drawPart(_ p: Prepared, _ transparentPass: Bool, _ onlyFace: Int = -1) {
            let m = p.part.mesh
            let faces = onlyFace >= 0 ? [onlyFace] : Array(0..<m.faceCount)
            var gAxis = -1, gLo: Float = 0, gInv: Float = 0
            let triangles = m.triangles
            let VS = Renderer3D.VS
            for f in faces {
                let mat = p.part.materialOf(f)
                if mat.gradient != nil && mat.gradientAxis != gAxis {
                    gAxis = mat.gradientAxis.coerceIn(0, 2)
                    var lo = Float.infinity, hi = -Float.infinity
                    var i = gAxis
                    while i < m.pos.count { let c = m.pos[i]; if c < lo { lo = c }; if c > hi { hi = c }; i += 3 }
                    gLo = lo; gInv = hi - lo > 1e-6 ? 1 / (hi - lo) : 0
                }
                if mat.transparent != transparentPass {
                    if !transparentPass && mat.transparent { transparent.append((faceDepth(p, f), p.index, f)) }
                    continue
                }
                let face = m.faces[f]
                if face.count < 3 { continue }
                let nx = p.normals[3 * f], ny = p.normals[3 * f + 1], nz = p.normals[3 * f + 2]
                let facing = facingViewer(p, f, nx, ny, nz)
                if p.cull && !facing && !transparentPass { continue }
                let selectedFace = opt.selectedPart == p.part.id && opt.selectedFaces.contains(f)
                let shader = Shader(mat, opt.textures, opt.bilinear, selectedFace, opt.highlight)
                let uv = p.uv[f]
                let corners = p.corners?[f]
                let tris = triangles[f]
                var tk = 0
                while tk + 2 < tris.count {
                    let t0 = tk
                    tk += 3
                    var ok = true
                    for c in 0..<3 {
                        let ci = tris[t0 + c]
                        let v = face[ci]
                        let o = VS * c
                        let s = p.screen[4 * v + 3]
                        if s <= 0 { ok = false; break }
                        tri[o] = p.screen[4 * v]; tri[o + 1] = p.screen[4 * v + 1]; tri[o + 2] = p.screen[4 * v + 2]
                        tri[o + 3] = view.perspective ? s : 1
                        if let corners { tri[o + 4] = corners[3 * ci]; tri[o + 5] = corners[3 * ci + 1]; tri[o + 6] = corners[3 * ci + 2] }
                        else { tri[o + 4] = nx; tri[o + 5] = ny; tri[o + 6] = nz }
                        if mat.gradient != nil {
                            tri[o + 7] = 0; tri[o + 8] = (m.pos[3 * v + gAxis] - gLo) * gInv
                        } else {
                            let ts = 1 / mat.textureScale.coerceAtLeast(0.01)
                            tri[o + 7] = uv[2 * ci] * ts; tri[o + 8] = uv[2 * ci + 1] * ts
                        }
                    }
                    if ok { fillTriangle(shader, Int32(p.index + 1), Int32(f), corners != nil, transparentPass) }
                }
            }
        }

        func facingViewer(_ p: Prepared, _ f: Int, _ nx: Float, _ ny: Float, _ nz: Float) -> Bool {
            if !view.perspective { return nx * T.x + ny * T.y + nz * T.z > 0 }
            let e = view.eye
            let v = p.part.mesh.faces[f][0]
            return (e.x - p.world[3 * v]) * nx + (e.y - p.world[3 * v + 1]) * ny + (e.z - p.world[3 * v + 2]) * nz > 0
        }

        func faceDepth(_ p: Prepared, _ f: Int) -> Float {
            var s: Float = 0
            let face = p.part.mesh.faces[f]
            for v in face { s += p.screen[4 * v + 2] }
            return s / Float(face.count)
        }

        func drawTransparent() {
            if transparent.isEmpty { return }
            let sorted = transparent.enumerated().sorted { $0.element.0 != $1.element.0 ? $0.element.0 < $1.element.0 : $0.offset < $1.offset }.map(\.element)
            var byIndex: [Int: Prepared] = [:]
            for p in prepared { byIndex[p.index] = p }
            for t in sorted {
                guard let p = byIndex[t.1] else { continue }
                drawPart(p, true, t.2)
            }
        }

        func fillTriangle(_ sh: Shader, _ pid: Int32, _ fid: Int32, _ smooth: Bool, _ blend: Bool) {
            let VS = Renderer3D.VS
            let x0 = tri[0], y0 = tri[1], x1 = tri[VS], y1 = tri[VS + 1], x2 = tri[2 * VS], y2 = tri[2 * VS + 1]
            var area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if abs(area) < 1e-7 { return }
            let minX = max(0, Int(min(x0, min(x1, x2)).rounded(.down))), maxX = min(w - 1, Int(max(x0, max(x1, x2)).rounded(.up)))
            let minY = max(0, Int(min(y0, min(y1, y2)).rounded(.down))), maxY = min(h - 1, Int(max(y0, max(y1, y2)).rounded(.up)))
            if minX > maxX || minY > maxY { return }
            let sign: Float = area < 0 ? -1 : 1
            area *= sign
            let inv = 1 / area
            let iw0 = tri[3], iw1 = tri[VS + 3], iw2 = tri[2 * VS + 3]
            let z0 = tri[2] * iw0, z1 = tri[VS + 2] * iw1, z2 = tri[2 * VS + 2] * iw2
            let u0 = tri[7] * iw0, u1 = tri[VS + 7] * iw1, u2 = tri[2 * VS + 7] * iw2
            let v0 = tri[8] * iw0, v1 = tri[VS + 8] * iw1, v2 = tri[2 * VS + 8] * iw2
            let ax0 = tri[4] * iw0, ax1 = tri[VS + 4] * iw1, ax2 = tri[2 * VS + 4] * iw2
            let ay0 = tri[5] * iw0, ay1 = tri[VS + 5] * iw1, ay2 = tri[2 * VS + 5] * iw2
            let az0 = tri[6] * iw0, az1 = tri[VS + 6] * iw1, az2 = tri[2 * VS + 6] * iw2
            let fnx = tri[4], fny = tri[5], fnz = tri[6]
            let e0dx = -(y2 - y1) * sign, e0dy = (x2 - x1) * sign
            let e1dx = -(y0 - y2) * sign, e1dy = (x0 - x2) * sign
            let e2dx = -(y1 - y0) * sign, e2dy = (x1 - x0) * sign
            let sx0 = Float(minX) + 0.5, sy0 = Float(minY) + 0.5
            var r0 = ((x2 - x1) * (sy0 - y1) - (y2 - y1) * (sx0 - x1)) * sign
            var r1 = ((x0 - x2) * (sy0 - y2) - (y0 - y2) * (sx0 - x2)) * sign
            var r2 = ((x1 - x0) * (sy0 - y0) - (y1 - y0) * (sx0 - x0)) * sign
            for py in minY...maxY {
                var w0 = r0, w1 = r1, w2 = r2
                var idx = py * w + minX
                for _ in minX...maxX {
                    if w0 >= 0 && w1 >= 0 && w2 >= 0 {
                        let b0 = w0 * inv, b1 = w1 * inv, b2 = w2 * inv
                        let iw = b0 * iw0 + b1 * iw1 + b2 * iw2
                        let rw = 1 / iw
                        let z = (b0 * z0 + b1 * z1 + b2 * z2) * rw
                        if z > depth[idx] {
                            var nx = fnx, ny = fny, nz = fnz
                            if smooth {
                                let a = b0 * ax0 + b1 * ax1 + b2 * ax2, b = b0 * ay0 + b1 * ay1 + b2 * ay2, c = b0 * az0 + b1 * az1 + b2 * az2
                                let l = 1 / (a * a + b * b + c * c).squareRoot().coerceAtLeast(1e-9)
                                nx = a * l; ny = b * l; nz = c * l
                            }
                            let u = (b0 * u0 + b1 * u1 + b2 * u2) * rw
                            let v = (b0 * v0 + b1 * v1 + b2 * v2) * rw
                            let c = sh.shade(nx, ny, nz, u, v, self)
                            if blend {
                                color[idx] = Renderer3D.over(color[idx], c & 0x00FF_FFFF, Float(c >> 24) / 255)
                            } else {
                                color[idx] = c | 0xFF00_0000
                                depth[idx] = z
                            }
                            partId[idx] = pid; faceId[idx] = fid
                        }
                    }
                    w0 += e0dx; w1 += e1dx; w2 += e2dx
                    idx += 1
                }
                r0 += e0dy; r1 += e1dy; r2 += e2dy
            }
        }

        // MARK: çizgiler

        func drawStrokes() {
            var a = [Float](repeating: 0, count: 4), b = a
            for (si, s) in model.strokes.enumerated() {
                if s.count == 0 { continue }
                let id = Int32(-(si + 1))
                let col = UInt32(bitPattern: s.color)
                for i in 0..<s.count {
                    view.project(s.points[3 * i], s.points[3 * i + 1], s.points[3 * i + 2], &b)
                    if i == 0 {
                        if b[3] > 0 { disc(b[0], b[1], b[2], s.width * 0.5 * view.scale * b[3], col, id) }
                    } else if a[3] > 0 && b[3] > 0 {
                        let ra = s.width * 0.5 * view.scale * a[3], rb = s.width * 0.5 * view.scale * b[3]
                        let len = ((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1])).squareRoot()
                        let step = max(0.75, min(ra, rb) * 0.5)
                        let n = max(1, Int((len / step).rounded(.up)))
                        for k in 1...n {
                            let t = Float(k) / Float(n)
                            disc(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, ra + (rb - ra) * t, col, id)
                        }
                    }
                    a = b
                }
            }
        }

        func disc(_ cx: Float, _ cy: Float, _ z: Float, _ radius: Float, _ c: UInt32, _ id: Int32) {
            let r = max(radius, 0.6)
            let x0 = max(0, Int((cx - r).rounded(.down))), x1 = min(w - 1, Int((cx + r).rounded(.up)))
            let y0 = max(0, Int((cy - r).rounded(.down))), y1 = min(h - 1, Int((cy + r).rounded(.up)))
            if x0 > x1 || y0 > y1 { return }
            let rr = r * r
            for y in y0...y1 { for x in x0...x1 {
                let dx = Float(x) + 0.5 - cx, dy = Float(y) + 0.5 - cy
                if dx * dx + dy * dy > rr { continue }
                let i = y * w + x
                if z + extent * 0.004 < depth[i] { continue }
                color[i] = c | 0xFF00_0000; depth[i] = max(depth[i], z); partId[i] = id; faceId[i] = 0
            } }
        }

        private func edgeVisible(_ p: Prepared, _ f1: Int, _ f2: Int, _ fn: [Float]) -> Bool {
            let front1 = facingViewer(p, f1, p.normals[3 * f1], p.normals[3 * f1 + 1], p.normals[3 * f1 + 2])
            let front2 = facingViewer(p, f2, p.normals[3 * f2], p.normals[3 * f2 + 1], p.normals[3 * f2 + 2])
            let dot = fn[3 * f1] * fn[3 * f2] + fn[3 * f1 + 1] * fn[3 * f2 + 1] + fn[3 * f1 + 2] * fn[3 * f2 + 2]
            return front1 != front2 || (dot < Renderer3D.FEATURE_COS && (!p.part.smooth || dot < Renderer3D.CREASE_COS))
        }

        func drawEdges() {
            var a = [Float](repeating: 0, count: 4), b = a
            let edgeW = Float(q) * 1.1, selW = Float(q) * 1.8
            for p in prepared {
                let selected = opt.selectedPart == p.part.id || opt.selectedParts.contains(p.part.id)
                if !scene.edges && !selected { continue }
                let m = p.part.mesh
                let e = m.edges, fn = m.faceNormals
                let col: UInt32 = selected ? opt.highlight : (UInt32(bitPattern: scene.edgeColor) & 0x00FF_FFFF) | (0xB0 << 24)
                var i = 0
                while i < e.count {
                    let va = e[i], vb = e[i + 1], f1 = e[i + 2], f2 = e[i + 3]
                    i += 4
                    let draw: Bool
                    if selected && opt.wireframe { draw = true } else if f2 < 0 { draw = true } else { draw = edgeVisible(p, f1, f2, fn) }
                    if !draw { continue }
                    for k in 0..<4 { a[k] = p.screen[4 * va + k]; b[k] = p.screen[4 * vb + k] }
                    if a[3] <= 0 || b[3] <= 0 { continue }
                    line(a, b, col, selected ? selW : edgeW, extent * 0.012)
                }
            }
        }

        func visibleEdges(_ step: Float) -> [[Float]] {
            var out: [[Float]] = []
            var a = [Float](repeating: 0, count: 4), b = a
            let bias = extent * 0.012
            for p in prepared {
                let m = p.part.mesh
                let e = m.edges, fn = m.faceNormals
                var i = 0
                while i < e.count {
                    let va = e[i], vb = e[i + 1], f1 = e[i + 2], f2 = e[i + 3]
                    i += 4
                    if !(f2 < 0 || edgeVisible(p, f1, f2, fn)) { continue }
                    for k in 0..<4 { a[k] = p.screen[4 * va + k]; b[k] = p.screen[4 * vb + k] }
                    if a[3] <= 0 || b[3] <= 0 { continue }
                    let dx = b[0] - a[0], dy = b[1] - a[1]
                    let n = max(1, Int((Float(hypot(Double(dx), Double(dy))) / step).rounded(.up)))
                    var vis = (0...n).map { k -> Bool in
                        let t = Float(k) / Float(n)
                        let x = a[0] + dx * t, y = a[1] + dy * t, z = a[2] + (b[2] - a[2]) * t
                        let px = Int(x.rounded(.down)), py = Int(y.rounded(.down))
                        if px < -1 || py < -1 || px > w || py > h { return false }
                        for oy in -1...1 { for ox in -1...1 {
                            let qx = px + ox, qy = py + oy
                            if qx < 0 || qx >= w || qy < 0 || qy >= h || z + bias >= depth[qy * w + qx] { return true }
                        } }
                        return false
                    }
                    var k = 0
                    while k <= n {
                        if !vis[k] {
                            var e2 = k
                            while e2 <= n && !vis[e2] { e2 += 1 }
                            if k > 0 && e2 <= n && e2 - k <= Renderer3D.GAP_FILL { for j in k..<e2 { vis[j] = true } }
                            k = e2
                        } else { k += 1 }
                    }
                    var runStart = -1
                    for j in 0...(n + 1) {
                        let on = j <= n && vis[j]
                        if on && runStart < 0 { runStart = j }
                        if !on && runStart >= 0 {
                            let end = j - 1
                            if end > runStart {
                                let t0 = Float(runStart) / Float(n), t1 = Float(end) / Float(n)
                                out.append([a[0] + dx * t0, a[1] + dy * t0, a[0] + dx * t1, a[1] + dy * t1])
                            }
                            runStart = -1
                        }
                    }
                }
            }
            return out
        }

        func line(_ a: [Float], _ b: [Float], _ c: UInt32, _ width: Float, _ bias: Float) {
            let dx = b[0] - a[0], dy = b[1] - a[1]
            let n = max(1, Int(max(abs(dx), abs(dy)).rounded(.up)))
            let r = width * 0.5
            let alpha = Float(c >> 24) / 255
            for k in 0...n {
                let t = Float(k) / Float(n)
                let x = a[0] + dx * t, y = a[1] + dy * t, z = a[2] + (b[2] - a[2]) * t
                let x0 = max(0, Int((x - r).rounded(.down))), x1 = min(w - 1, Int((x + r).rounded(.down)))
                let y0 = max(0, Int((y - r).rounded(.down))), y1 = min(h - 1, Int((y + r).rounded(.down)))
                if x0 > x1 || y0 > y1 { continue }
                for py in y0...y1 { for px in x0...x1 {
                    let i = py * w + px
                    if z + bias < depth[i] { continue }
                    color[i] = Renderer3D.over(color[i], c & 0x00FF_FFFF, alpha)
                } }
            }
        }

        func lineNoDepth(_ a: [Float], _ b: [Float], _ c: UInt32, _ width: Float) {
            if a[3] <= 0 || b[3] <= 0 { return }
            let dx = b[0] - a[0], dy = b[1] - a[1]
            let n = max(1, Int(max(abs(dx), abs(dy)).rounded(.up)))
            let r = width * 0.5
            let alpha = Float(c >> 24) / 255
            for k in 0...n {
                let t = Float(k) / Float(n)
                let x = a[0] + dx * t, y = a[1] + dy * t
                let x0 = max(0, Int((x - r).rounded(.down))), x1 = min(w - 1, Int((x + r - 0.001).rounded(.down)))
                let y0 = max(0, Int((y - r).rounded(.down))), y1 = min(h - 1, Int((y + r - 0.001).rounded(.down)))
                if x0 > x1 || y0 > y1 { continue }
                for py in y0...y1 { for px in x0...x1 {
                    let i = py * w + px
                    color[i] = Renderer3D.over(color[i], c & 0x00FF_FFFF, alpha)
                } }
            }
        }

        // MARK: çıkış

        func output(_ out: View3) -> Frame3 {
            let ow = out.width, oh = out.height
            if q == 1 { return Frame3(ow, oh, color, depth, partId, faceId, out) }
            var c = [UInt32](repeating: 0, count: ow * oh), d = [Float](repeating: 0, count: ow * oh)
            var p = [Int32](repeating: 0, count: ow * oh), f = p
            let k = q * q, mid = q / 2
            for y in 0..<oh { for x in 0..<ow {
                var a = 0, r = 0, g = 0, b = 0
                for j in 0..<q { for i in 0..<q {
                    let s = color[(y * q + j) * w + x * q + i]
                    let sa = Int(s >> 24)
                    a += sa; r += Int((s >> 16) & 0xFF) * sa; g += Int((s >> 8) & 0xFF) * sa; b += Int(s & 0xFF) * sa
                } }
                let o = y * ow + x
                if a > 0 {
                    let ca: Int = (a / k) << 24, cr: Int = (r / a) << 16, cg: Int = (g / a) << 8, cb: Int = b / a
                    c[o] = UInt32(truncatingIfNeeded: ca | cr | cg | cb)
                }
                let src = (y * q + mid) * w + x * q + mid
                d[o] = depth[src]; p[o] = partId[src]; f[o] = faceId[src]
            } }
            return Frame3(ow, oh, c, d, p, f, out)
        }
    }

    /// [dst] üstüne [rgb] rengini [alpha] örtücülükle bindirir (önçarpımsız ARGB).
    static func over(_ dst: UInt32, _ rgb: UInt32, _ alpha: Float) -> UInt32 {
        if alpha <= 0 { return dst }
        let da = Float(dst >> 24) / 255
        let oa = alpha + da * (1 - alpha)
        if oa <= 1e-6 { return 0 }
        func ch(_ sh: UInt32) -> UInt32 {
            let s = Float((rgb >> sh) & 0xFF) / 255, d = Float((dst >> sh) & 0xFF) / 255
            return UInt32(ktIntF(((s * alpha + d * da * (1 - alpha)) / oa) * 255 + 0.5).coerceIn(0, 255))
        }
        return UInt32(ktIntF(oa * 255 + 0.5).coerceIn(0, 255)) << 24 | ch(16) << 16 | ch(8) << 8 | ch(0)
    }

    /// Kotlin `Float.toInt()` (sıfıra doğru, doygun; NaN → 0).
    @inline(__always) static func ktIntF(_ v: Float) -> Int {
        if v.isNaN { return 0 }
        if v >= 2147483647 { return Int(Int32.max) }
        if v <= -2147483648 { return Int(Int32.min) }
        return Int(v)
    }

    /// Malzemenin piksel rengi.
    fileprivate final class Shader {
        let mat: Material
        let bilinear: Bool, selected: Bool, highlight: UInt32
        let tex: Texture?
        let cr: Float, cg: Float, cb: Float
        let grad: UInt32?
        let gr: Float, gg: Float, gb: Float
        let specStrength: Float
        let lut: [Float]

        init(_ mat: Material, _ textures: (String) -> Texture?, _ bilinear: Bool, _ selected: Bool, _ highlight: UInt32) {
            self.mat = mat; self.bilinear = bilinear; self.selected = selected; self.highlight = highlight
            tex = mat.gradient != nil ? nil : mat.texture.flatMap(textures)
            let c = UInt32(bitPattern: mat.color)
            cr = Float((c >> 16) & 0xFF) / 255; cg = Float((c >> 8) & 0xFF) / 255; cb = Float(c & 0xFF) / 255
            grad = mat.gradient.map { UInt32(bitPattern: $0) }
            let g = grad ?? 0
            gr = Float((g >> 16) & 0xFF) / 255; gg = Float((g >> 8) & 0xFF) / 255; gb = Float(g & 0xFF) / 255
            switch mat.kind {
            case .MATTE, .GLOW: specStrength = 0
            case .PLASTIC: specStrength = 0.12 + 0.5 * mat.shine
            case .METAL: specStrength = 0.55 + 0.45 * mat.shine
            case .GLASS: specStrength = 0.9
            }
            lut = Shader.specLut(8 + mat.shine.coerceIn(0, 1) * 120)
        }

        func shade(_ nx0: Float, _ ny0: Float, _ nz0: Float, _ u: Float, _ v: Float, _ r: Raster) -> UInt32 {
            var nx = nx0, ny = ny0, nz = nz0
            let ndv = nx * r.T.x + ny * r.T.y + nz * r.T.z
            if ndv < 0 { nx = -nx; ny = -ny; nz = -nz }
            var br: Float, bg: Float, bb: Float
            if let t = tex {
                let texel = sample(t, u, v)
                br = Float((texel >> 16) & 0xFF) / 255 * cr; bg = Float((texel >> 8) & 0xFF) / 255 * cg; bb = Float(texel & 0xFF) / 255 * cb
            } else if grad != nil {
                let k = v.coerceIn(0, 1)
                br = cr + (gr - cr) * k; bg = cg + (gg - cg) * k; bb = cb + (gb - cb) * k
            } else { br = cr; bg = cg; bb = cb }
            if mat.kind == .GLOW { return pack(br * 1.08, bg * 1.08, bb * 1.08, mat.opacity) }
            let light = r.light
            let ndl = nx * r.L.x + ny * r.L.y + nz * r.L.z
            let amb = light.ambient * (0.82 + 0.18 * ny)
            let diff = max(0, ndl) * light.intensity * (1 - light.ambient * 0.5)
            let metal = mat.kind == .METAL
            let lit = amb + diff * (metal ? 0.75 : 1)
            var or = br * lit * (0.6 + 0.4 * r.lightR)
            var og = bg * lit * (0.6 + 0.4 * r.lightG)
            var ob = bb * lit * (0.6 + 0.4 * r.lightB)
            if specStrength > 0 && ndl > 0 {
                let ndh = max(0, nx * r.H.x + ny * r.H.y + nz * r.H.z)
                let s = specStrength * lut[Renderer3D.ktIntF(ndh * 255).coerceIn(0, 255)] * light.intensity
                if metal { or += s * (0.3 + 0.7 * br); og += s * (0.3 + 0.7 * bg); ob += s * (0.3 + 0.7 * bb) }
                else { or += s * r.lightR; og += s * r.lightG; ob += s * r.lightB }
            }
            if metal {
                let rv = 2 * abs(ndv) * ny - r.T.y
                let e = (rv * 0.5 + 0.5).coerceIn(0, 1)
                let env = 0.18 + 0.55 * e * e
                or += env * br * 0.6; og += env * bg * 0.6; ob += env * bb * 0.6
            }
            var alpha = mat.opacity
            if mat.kind == .GLASS {
                let f1 = 1 - abs(ndv)
                let fr = f1 * f1
                alpha = (mat.opacity * (0.3 + 0.6 * fr) + 0.08).coerceIn(0.05, 1)
                or += fr * 0.25; og += fr * 0.25; ob += fr * 0.3
            }
            if selected {
                let hr = Float((highlight >> 16) & 0xFF) / 255, hg = Float((highlight >> 8) & 0xFF) / 255, hb = Float(highlight & 0xFF) / 255
                or = or * 0.55 + hr * 0.45; og = og * 0.55 + hg * 0.45; ob = ob * 0.55 + hb * 0.45
            }
            return pack(or, og, ob, alpha)
        }

        func pack(_ r: Float, _ g: Float, _ b: Float, _ a: Float) -> UInt32 {
            func q(_ v: Float) -> UInt32 { UInt32(Renderer3D.ktIntF(v.coerceIn(0, 1) * 255 + 0.5)) }
            return q(a) << 24 | q(r) << 16 | q(g) << 8 | q(b)
        }

        func sample(_ t: Texture, _ u: Float, _ v: Float) -> UInt32 {
            let fu = (u - u.rounded(.down)) * Float(t.w), fv = (v - v.rounded(.down)) * Float(t.h)
            if !bilinear {
                let x = Renderer3D.ktIntF(fu).coerceIn(0, t.w - 1), y = Renderer3D.ktIntF(fv).coerceIn(0, t.h - 1)
                return t.px[y * t.w + x]
            }
            let x0f = fu - 0.5, y0f = fv - 0.5
            let xi = Int(x0f.rounded(.down)), yi = Int(y0f.rounded(.down))
            let ax = x0f - Float(xi), ay = y0f - Float(yi)
            let xa = floorModI(xi, t.w), xb = floorModI(xi + 1, t.w)
            let ya = floorModI(yi, t.h), yb = floorModI(yi + 1, t.h)
            let c00 = t.px[ya * t.w + xa], c10 = t.px[ya * t.w + xb], c01 = t.px[yb * t.w + xa], c11 = t.px[yb * t.w + xb]
            func mix(_ sh: UInt32) -> UInt32 {
                let top = Float((c00 >> sh) & 0xFF) * (1 - ax) + Float((c10 >> sh) & 0xFF) * ax
                let bot = Float((c01 >> sh) & 0xFF) * (1 - ax) + Float((c11 >> sh) & 0xFF) * ax
                return UInt32(Renderer3D.ktIntF(top * (1 - ay) + bot * ay + 0.5))
            }
            return 0xFF00_0000 | mix(16) << 16 | mix(8) << 8 | mix(0)
        }

        private static let lutLock = NSLock()
        private static var luts: [Int: [Float]] = [:]

        static func specLut(_ exp: Float) -> [Float] {
            let key = javaRoundF(exp)
            lutLock.lock(); defer { lutLock.unlock() }
            if let l = luts[key] { return l }
            let l = (0..<256).map { Float(pow(Double($0) / 255.0, Double(exp))) }
            luts[key] = l
            return l
        }
    }
}
