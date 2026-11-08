import Foundation

/// v25 katı cisim işlemleri (CSG, csg.js BSP algoritması) (Android `Csg`).
enum Csg {

    private static let EPS = 1e-5

    /// Çokgen (dışbükey, saat yönünün tersine), malzeme etiketiyle.
    final class CsgPoly {
        let v: [[Double]]
        let tag: Int
        let plane: Plane
        init(_ v: [[Double]], _ tag: Int, _ plane: Plane) { self.v = v; self.tag = tag; self.plane = plane }
        func flipped() -> CsgPoly { CsgPoly(v.reversed(), tag, plane.flipped()) }
    }

    struct Plane {
        let nx: Double, ny: Double, nz: Double, w: Double
        func flipped() -> Plane { Plane(nx: -nx, ny: -ny, nz: -nz, w: -w) }
        func dist(_ p: [Double]) -> Double { nx * p[0] + ny * p[1] + nz * p[2] - w }

        static func of(_ a: [Double], _ b: [Double], _ c: [Double]) -> Plane? {
            let ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2]
            let vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2]
            var nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx
            let l = (nx * nx + ny * ny + nz * nz).squareRoot()
            if l < 1e-12 { return nil }
            nx /= l; ny /= l; nz /= l
            return Plane(nx: nx, ny: ny, nz: nz, w: nx * a[0] + ny * a[1] + nz * a[2])
        }
    }

    private static let COPLANAR = 0, FRONT = 1, BACK = 2, SPANNING = 3

    private enum Dest { case coFront, coBack, front, back }

    /// [poly]'yi düzleme göre ayırır; sonuçlar sırasıyla dört hedefe (aynı dizi olabilir) eklenir.
    private static func split(_ plane: Plane, _ poly: CsgPoly, _ emit: (Dest, CsgPoly) -> Void) {
        var type = 0
        let n = poly.v.count
        var types = [Int](repeating: 0, count: n)
        for i in 0..<n {
            let t = plane.dist(poly.v[i])
            let ty = t < -EPS ? BACK : t > EPS ? FRONT : COPLANAR
            type |= ty
            types[i] = ty
        }
        switch type {
        case COPLANAR:
            let same = plane.nx * poly.plane.nx + plane.ny * poly.plane.ny + plane.nz * poly.plane.nz > 0
            emit(same ? .coFront : .coBack, poly)
        case FRONT: emit(.front, poly)
        case BACK: emit(.back, poly)
        default:
            var f: [[Double]] = [], b: [[Double]] = []
            for i in 0..<n {
                let j = (i + 1) % n
                let ti = types[i], tj = types[j]
                let vi = poly.v[i], vj = poly.v[j]
                if ti != BACK { f.append(vi) }
                if ti != FRONT { b.append(vi) }
                if (ti | tj) == SPANNING {
                    let dx = vj[0] - vi[0], dy = vj[1] - vi[1], dz = vj[2] - vi[2]
                    let t = (plane.w - (plane.nx * vi[0] + plane.ny * vi[1] + plane.nz * vi[2])) / (plane.nx * dx + plane.ny * dy + plane.nz * dz)
                    let p = [vi[0] + dx * t, vi[1] + dy * t, vi[2] + dz * t]
                    f.append(p); b.append(p)
                }
            }
            if f.count >= 3 { emit(.front, CsgPoly(f, poly.tag, poly.plane)) }
            if b.count >= 3 { emit(.back, CsgPoly(b, poly.tag, poly.plane)) }
        }
    }

    /// BSP düğümü (özyinelemesiz).
    private final class Node {
        var plane: Plane?
        var front: Node?
        var back: Node?
        var polys: [CsgPoly] = []

        func nodes() -> [Node] {
            var out: [Node] = []
            var stack: [Node] = [self]
            while let n = stack.popLast() {
                out.append(n)
                if let f = n.front { stack.append(f) }
                if let b = n.back { stack.append(b) }
            }
            return out
        }

        func invert() {
            for n in nodes() {
                n.polys = n.polys.map { $0.flipped() }
                n.plane = n.plane?.flipped()
                swap(&n.front, &n.back)
            }
        }

        func clip(_ input: [CsgPoly]) -> [CsgPoly] {
            var out: [CsgPoly] = []
            if plane == nil { return input }
            var stack: [(Node, [CsgPoly])] = [(self, input)]
            while let (node, list) = stack.popLast() {
                guard let pl = node.plane else { out += list; continue }
                var f: [CsgPoly] = [], b: [CsgPoly] = []
                for p in list {
                    split(pl, p) { d, q in if d == .coFront || d == .front { f.append(q) } else { b.append(q) } }
                }
                if let fn = node.front { if !f.isEmpty { stack.append((fn, f)) } } else { out += f }
                if let bn = node.back, !b.isEmpty { stack.append((bn, b)) }
            }
            return out
        }

        func clipTo(_ bsp: Node) { for n in nodes() { n.polys = bsp.clip(n.polys) } }

        func all() -> [CsgPoly] { var out: [CsgPoly] = []; for n in nodes() { out += n.polys }; return out }

        func build(_ input: [CsgPoly]) {
            if input.isEmpty { return }
            var stack: [(Node, [CsgPoly])] = [(self, input)]
            while let (node, list) = stack.popLast() {
                if list.isEmpty { continue }
                let pl: Plane
                if let p = node.plane { pl = p } else { pl = list[0].plane; node.plane = pl }
                var f: [CsgPoly] = [], b: [CsgPoly] = []
                for p in list {
                    split(pl, p) { d, q in
                        switch d {
                        case .coFront, .coBack: node.polys.append(q)
                        case .front: f.append(q)
                        case .back: b.append(q)
                        }
                    }
                }
                if !f.isEmpty { let fn = node.front ?? { let x = Node(); node.front = x; return x }(); stack.append((fn, f)) }
                if !b.isEmpty { let bn = node.back ?? { let x = Node(); node.back = x; return x }(); stack.append((bn, b)) }
            }
        }

        static func of(_ list: [CsgPoly]) -> Node { let n = Node(); n.build(list); return n }
    }

    // MARK: işlemler

    static func union(_ a: [CsgPoly], _ b: [CsgPoly]) -> [CsgPoly] {
        let na = Node.of(a), nb = Node.of(b)
        na.clipTo(nb); nb.clipTo(na); nb.invert(); nb.clipTo(na); nb.invert()
        na.build(nb.all())
        return na.all()
    }

    static func subtract(_ a: [CsgPoly], _ b: [CsgPoly]) -> [CsgPoly] {
        let na = Node.of(a), nb = Node.of(b)
        na.invert(); na.clipTo(nb); nb.clipTo(na); nb.invert(); nb.clipTo(na); nb.invert()
        na.build(nb.all()); na.invert()
        return na.all()
    }

    static func subtractLocal(_ a: [CsgPoly], _ b: [CsgPoly]) -> [CsgPoly] {
        if a.isEmpty || b.isEmpty { return a }
        var x0 = Double.greatestFiniteMagnitude, y0 = x0, z0 = x0
        var x1 = -Double.greatestFiniteMagnitude, y1 = x1, z1 = x1
        for p in b { for v in p.v {
            x0 = min(x0, v[0]); y0 = min(y0, v[1]); z0 = min(z0, v[2]); x1 = max(x1, v[0]); y1 = max(y1, v[1]); z1 = max(z1, v[2])
        } }
        let pad = EPS * 10
        var near: [CsgPoly] = [], far: [CsgPoly] = []
        for p in a {
            var px0 = Double.greatestFiniteMagnitude, py0 = px0, pz0 = px0
            var px1 = -Double.greatestFiniteMagnitude, py1 = px1, pz1 = px1
            for v in p.v {
                px0 = min(px0, v[0]); py0 = min(py0, v[1]); pz0 = min(pz0, v[2]); px1 = max(px1, v[0]); py1 = max(py1, v[1]); pz1 = max(pz1, v[2])
            }
            let hit = px1 >= x0 - pad && px0 <= x1 + pad && py1 >= y0 - pad && py0 <= y1 + pad && pz1 >= z0 - pad && pz0 <= z1 + pad
            if hit { near.append(p) } else { far.append(p) }
        }
        if near.isEmpty { return a }
        let nb = Node.of(b)
        let aOut = nb.clip(near)
        let na = Node.of(a)
        na.invert()
        let bIn = na.clip(b).map { $0.flipped() }
        return far + aOut + bIn
    }

    static func intersect(_ a: [CsgPoly], _ b: [CsgPoly]) -> [CsgPoly] {
        let na = Node.of(a), nb = Node.of(b)
        na.invert(); nb.clipTo(na); nb.invert(); na.clipTo(nb); nb.clipTo(na)
        na.build(nb.all()); na.invert()
        return na.all()
    }

    // MARK: dönüşümler

    static func polygons(_ mesh: Mesh, _ transform: Transform3?, _ tagOf: (Int) -> Int) -> [CsgPoly] {
        var world = mesh.pos
        if let transform { for i in 0..<mesh.vertexCount { transform.apply(mesh.pos[3 * i], mesh.pos[3 * i + 1], mesh.pos[3 * i + 2], &world, 3 * i) } }
        let mirrored = transform.map { det($0.m) < 0 } ?? false
        var out: [CsgPoly] = []
        let tri = mesh.triangles
        for f in 0..<mesh.faceCount {
            let face = mesh.faces[f], tris = tri[f]
            let tag = tagOf(f)
            var k = 0
            while k + 2 < tris.count {
                let ids = mirrored ? [face[tris[k]], face[tris[k + 2]], face[tris[k + 1]]] : [face[tris[k]], face[tris[k + 1]], face[tris[k + 2]]]
                let pts = ids.map { [Double(world[3 * $0]), Double(world[3 * $0 + 1]), Double(world[3 * $0 + 2])] }
                if let pl = Plane.of(pts[0], pts[1], pts[2]) { out.append(CsgPoly(pts, tag, pl)) }
                k += 3
            }
        }
        return out
    }

    private static func det(_ m: [Float]) -> Float {
        m[0] * (m[5] * m[10] - m[6] * m[9]) - m[1] * (m[4] * m[10] - m[6] * m[8]) + m[2] * (m[4] * m[9] - m[5] * m[8])
    }

    private static func cellKey(_ x: Int64, _ y: Int64, _ z: Int64) -> Int64 { (x &* 73856093) ^ (y &* 19349663) ^ (z &* 83492791) }

    static func toMesh(_ polys: [CsgPoly], _ tags: inout [Int]) -> Mesh {
        if polys.isEmpty { return Mesh([], []) }
        var minX = Double.greatestFiniteMagnitude, maxX = -Double.greatestFiniteMagnitude
        var minY = minX, maxY = maxX, minZ = minX, maxZ = maxX
        for p in polys { for v in p.v {
            minX = min(minX, v[0]); maxX = max(maxX, v[0]); minY = min(minY, v[1]); maxY = max(maxY, v[1]); minZ = min(minZ, v[2]); maxZ = max(maxZ, v[2])
        } }
        let size = max(maxX - minX, max(maxY - minY, maxZ - minZ)).coerceAtLeast(1e-6)
        let weld = size * 1e-6
        var grid: [Int64: [Int]] = [:]
        var pos: [Double] = []
        func indexOf(_ v: [Double]) -> Int {
            let cx = Int64((v[0] / (weld * 4)).rounded(.down)), cy = Int64((v[1] / (weld * 4)).rounded(.down)), cz = Int64((v[2] / (weld * 4)).rounded(.down))
            for dx in Int64(-1)...1 { for dy in Int64(-1)...1 { for dz in Int64(-1)...1 {
                guard let list = grid[cellKey(cx + dx, cy + dy, cz + dz)] else { continue }
                for i in list where abs(pos[3 * i] - v[0]) <= weld && abs(pos[3 * i + 1] - v[1]) <= weld && abs(pos[3 * i + 2] - v[2]) <= weld { return i }
            } } }
            let i = pos.count / 3
            pos += [v[0], v[1], v[2]]
            grid[cellKey(cx, cy, cz), default: []].append(i)
            return i
        }
        var faces: [[Int]] = []
        for p in polys {
            var ids: [Int] = []
            for v in p.v { let i = indexOf(v); if ids.isEmpty || ids.last! != i { ids.append(i) } }
            while ids.count > 1 && ids.first! == ids.last! { ids.removeLast() }
            if Set(ids).count >= 3 { faces.append(ids); tags.append(p.tag) }
        }
        let posF = pos.map { Float($0) }
        return stitch(fixTJunctions(Mesh(posF, faces), size * 2e-5), size * 2e-5)
    }

    static func stitch(_ mesh: Mesh, _ eps: Double) -> Mesh {
        let e = mesh.edges
        var open: [Int] = []
        var seen = Set<Int>()
        var i = 0
        while i < e.count {
            if e[i + 3] < 0 {
                if seen.insert(e[i]).inserted { open.append(e[i]) }
                if seen.insert(e[i + 1]).inserted { open.append(e[i + 1]) }
            }
            i += 4
        }
        if open.isEmpty || open.count > 4000 { return mesh }
        let list = open
        var remap = Array(0..<mesh.vertexCount)
        let p = mesh.pos
        var merged = false
        for a in list.indices {
            if remap[list[a]] != list[a] { continue }
            for b in (a + 1)..<list.count where a + 1 < list.count {
                let u = list[a], v = list[b]
                if remap[v] != v { continue }
                let dx = Double(p[3 * u] - p[3 * v]), dy = Double(p[3 * u + 1] - p[3 * v + 1]), dz = Double(p[3 * u + 2] - p[3 * v + 2])
                if dx * dx + dy * dy + dz * dz <= eps * eps { remap[v] = u; merged = true }
            }
        }
        if !merged { return mesh }
        var faces: [[Int]] = []
        for f in mesh.faces {
            var ids: [Int] = []
            for x in f { let r = remap[x]; if ids.isEmpty || ids.last! != r { ids.append(r) } }
            while ids.count > 1 && ids.first! == ids.last! { ids.removeLast() }
            if Set(ids).count >= 3 { faces.append(ids) }
        }
        return Mesh(p, faces).compact()
    }

    static func fixTJunctions(_ mesh: Mesh, _ eps: Double) -> Mesh {
        let n = mesh.vertexCount
        if n == 0 { return mesh }
        let p = mesh.pos
        var len = 0.0; var cnt = 0
        for face in mesh.faces { for i in face.indices {
            len += dist(p, face[i], face[(i + 1) % face.count]); cnt += 1
        } }
        let cell = (len / Double(max(cnt, 1))).coerceAtLeast(eps * 10)
        var grid: [Int64: [Int]] = [:]
        func c(_ v: Float) -> Int64 { Int64((Double(v) / cell).rounded(.down)) }
        for i in 0..<n { grid[cellKey(c(p[3 * i]), c(p[3 * i + 1]), c(p[3 * i + 2])), default: []].append(i) }
        var changed = false
        func fixFace(_ face: [Int]) -> [Int] {
            var out: [Int] = []
            for i in face.indices {
                let a = face[i], b = face[(i + 1) % face.count]
                out.append(a)
                let ax = p[3 * a], ay = p[3 * a + 1], az = p[3 * a + 2]
                let dx = p[3 * b] - ax, dy = p[3 * b + 1] - ay, dz = p[3 * b + 2] - az
                let l2 = Double(dx * dx + dy * dy + dz * dz)
                if l2 < eps * eps { continue }
                let x0 = c(min(ax, p[3 * b])), x1 = c(max(ax, p[3 * b]))
                let y0 = c(min(ay, p[3 * b + 1])), y1 = c(max(ay, p[3 * b + 1]))
                let z0 = c(min(az, p[3 * b + 2])), z1 = c(max(az, p[3 * b + 2]))
                if (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 4096 { continue }
                var hits: [(Double, Int)] = []
                for x in x0...x1 { for y in y0...y1 { for z in z0...z1 {
                    guard let cellList = grid[cellKey(x, y, z)] else { continue }
                    for v in cellList {
                        if v == a || v == b { continue }
                        let px = p[3 * v] - ax, py = p[3 * v + 1] - ay, pz = p[3 * v + 2] - az
                        let t = Double(px * dx + py * dy + pz * dz) / l2
                        if t <= 1e-6 || t >= 1 - 1e-6 { continue }
                        let qx = Double(px) - Double(dx) * t, qy = Double(py) - Double(dy) * t, qz = Double(pz) - Double(dz) * t
                        if qx * qx + qy * qy + qz * qz <= eps * eps { hits.append((t, v)) }
                    }
                } } }
                if !hits.isEmpty {
                    let sorted = hits.enumerated().sorted { $0.element.0 != $1.element.0 ? $0.element.0 < $1.element.0 : $0.offset < $1.offset }
                    for (_, h) in sorted where out.last! != h.1 { out.append(h.1) }
                    changed = true
                }
            }
            return out
        }
        let faces: [[Int]] = mesh.faces.map(fixFace)
        return changed ? Mesh(p, faces) : mesh
    }

    private static func dist(_ p: [Float], _ a: Int, _ b: Int) -> Double {
        let dx = p[3 * a] - p[3 * b], dy = p[3 * a + 1] - p[3 * b + 1], dz = p[3 * a + 2] - p[3 * b + 2]
        return Double(dx * dx + dy * dy + dz * dz).squareRoot()
    }

    static func box(_ min: V3, _ max: V3, _ tag: Int) -> [CsgPoly] {
        let m = Primitives.cube().mapPositions { V3(min.x + ($0.x + 0.5) * (max.x - min.x), min.y + ($0.y + 0.5) * (max.y - min.y), min.z + ($0.z + 0.5) * (max.z - min.z)) }
        return polygons(m, nil) { _ in tag }
    }
}
