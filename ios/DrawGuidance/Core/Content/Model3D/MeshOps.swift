import Foundation

/// Ağdaki bir kenar: iki köşe sırası (küçük önce).
struct EdgeRef: Hashable {
    let a: Int, b: Int
    init(_ a: Int, _ b: Int) { self.a = a; self.b = b }
    static func of(_ a: Int, _ b: Int) -> EdgeRef { a < b ? EdgeRef(a, b) : EdgeRef(b, a) }
}

extension Sequence where Element: Hashable {
    /// Kotlin `distinct()`: ilk görülme sırasını korur.
    func ktDistinct() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}

/// v24 ağ yardımcıları ve yüzeysel düzenleme işlemleri (Android `MeshOps`).
enum MeshOps {

    // MARK: üçgenleme

    static func triangulate(_ pos: [Float], _ face: [Int]) -> [Int] {
        let n = face.count
        if n < 3 { return [] }
        if n == 3 { return [0, 1, 2] }
        var nx: Float = 0, ny: Float = 0, nz: Float = 0
        for i in 0..<n {
            let a = face[i] * 3, b = face[(i + 1) % n] * 3
            nx += (pos[a + 1] - pos[b + 1]) * (pos[a + 2] + pos[b + 2])
            ny += (pos[a + 2] - pos[b + 2]) * (pos[a] + pos[b])
            nz += (pos[a] - pos[b]) * (pos[a + 1] + pos[b + 1])
        }
        let ax = abs(nx), ay = abs(ny), az = abs(nz)
        let u: Int, v: Int, sign: Float
        if ax >= ay && ax >= az { u = 1; v = 2; sign = nx >= 0 ? 1 : -1 }
        else if ay >= ax && ay >= az { u = 2; v = 0; sign = ny >= 0 ? 1 : -1 }
        else { u = 0; v = 1; sign = nz >= 0 ? 1 : -1 }
        let xs = (0..<n).map { pos[face[$0] * 3 + u] }
        let ys = (0..<n).map { pos[face[$0] * 3 + v] * sign }
        if convex(xs, ys) {
            var t = [Int](repeating: 0, count: (n - 2) * 3)
            for k in 0..<(n - 2) { t[3 * k] = 0; t[3 * k + 1] = k + 1; t[3 * k + 2] = k + 2 }
            return t
        }
        return earClip(xs, ys)
    }

    private static func cross(_ xs: [Float], _ ys: [Float], _ a: Int, _ b: Int, _ c: Int) -> Float {
        (xs[b] - xs[a]) * (ys[c] - ys[a]) - (ys[b] - ys[a]) * (xs[c] - xs[a])
    }

    private static func convex(_ xs: [Float], _ ys: [Float]) -> Bool {
        let n = xs.count
        for i in 0..<n where cross(xs, ys, i, (i + 1) % n, (i + 2) % n) < -1e-9 { return false }
        return true
    }

    private static func earClip(_ xs: [Float], _ ys: [Float]) -> [Int] {
        let n = xs.count
        var idx = Array(0..<n)
        var out: [Int] = []
        out.reserveCapacity((n - 2) * 3)
        var guardN = 0
        while idx.count > 3 && guardN < n * n {
            guardN += 1
            var clipped = false
            for k in idx.indices {
                let a = idx[(k + idx.count - 1) % idx.count], b = idx[k], c = idx[(k + 1) % idx.count]
                if cross(xs, ys, a, b, c) <= 1e-12 { continue }
                var inside = false
                for p in idx where p != a && p != b && p != c {
                    if inTri(xs[p], ys[p], xs[a], ys[a], xs[b], ys[b], xs[c], ys[c]) { inside = true; break }
                }
                if inside { continue }
                out += [a, b, c]
                idx.remove(at: k)
                clipped = true
                break
            }
            if !clipped { break }
        }
        if idx.count == 3 { out += [idx[0], idx[1], idx[2]] }
        else if idx.count > 3 { for k in 1..<(idx.count - 1) { out += [idx[0], idx[k], idx[k + 1]] } }
        return out
    }

    private static func inTri(_ px: Float, _ py: Float, _ ax: Float, _ ay: Float, _ bx: Float, _ by: Float, _ cx: Float, _ cy: Float) -> Bool {
        let d1 = (bx - ax) * (py - ay) - (by - ay) * (px - ax)
        let d2 = (cx - bx) * (py - by) - (cy - by) * (px - bx)
        let d3 = (ax - cx) * (py - cy) - (ay - cy) * (px - cx)
        return d1 >= 0 && d2 >= 0 && d3 >= 0
    }

    // MARK: kenar yardımcıları

    static func facesOfEdge(_ mesh: Mesh, _ e: EdgeRef) -> [Int] {
        var out: [Int] = []
        let vf = mesh.vertexFaces
        guard e.a >= 0 && e.a < vf.count else { return out }
        for f in vf[e.a] {
            let face = mesh.faces[f]
            for i in face.indices {
                let p = face[i], q = face[(i + 1) % face.count]
                if (p == e.a && q == e.b) || (p == e.b && q == e.a) { out.append(f); break }
            }
        }
        return out
    }

    static func edgeList(_ mesh: Mesh) -> [EdgeRef] {
        let e = mesh.edges
        return (0..<(e.count / 4)).map { EdgeRef(e[4 * $0], e[4 * $0 + 1]) }
    }

    static func edgeNormal(_ mesh: Mesh, _ e: EdgeRef) -> V3 {
        let adj = facesOfEdge(mesh, e)
        if adj.count == 1 {
            let f = adj[0]
            let fn = mesh.faceNormal(f)
            let pa = mesh.vertex(e.a), pb = mesh.vertex(e.b)
            var out = (pb - pa).cross(fn).normalized()
            let c = mesh.faceCenter(f)
            if ((pa + pb) * 0.5 - c).dot(out) < 0 { out = -out }
            return out
        }
        var n = V3.ZERO
        for f in adj { n = n + mesh.faceNormal(f) }
        return n.length < 1e-6 ? mesh.faceNormal(mesh.vertexFaces[e.a].first ?? 0) : n.normalized()
    }

    // MARK: nokta ekleme

    static func splitEdge(_ mesh: Mesh, _ e: EdgeRef, _ t: Float) -> (Mesh, Int) {
        let tt = t.coerceIn(0.02, 0.98)
        let pa = mesh.vertex(e.a), pb = mesh.vertex(e.b)
        let np = pa + (pb - pa) * tt
        let ni = mesh.vertexCount
        var pos = mesh.pos + [np.x, np.y, np.z]
        _ = pos
        var faces = mesh.faces
        var uvs = mesh.uvs
        for f in facesOfEdge(mesh, e) {
            let face = faces[f]
            let n = face.count
            for i in 0..<n {
                let p = face[i], q = face[(i + 1) % n]
                if !((p == e.a && q == e.b) || (p == e.b && q == e.a)) { continue }
                let local: Float = p == e.a ? tt : 1 - tt
                faces[f] = (0...n).map { (k: Int) -> Int in
                    if k <= i { return face[k] }
                    return k == i + 1 ? ni : face[k - 1]
                }
                if let u = uvs?[f] {
                    let j = (i + 1) % n
                    let nu = u[2 * i] + (u[2 * j] - u[2 * i]) * local
                    let nv = u[2 * i + 1] + (u[2 * j + 1] - u[2 * i + 1]) * local
                    uvs![f] = (0..<((n + 1) * 2)).map { (k: Int) -> Float in
                        let c = k / 2
                        if c <= i { return u[k] }
                        if c == i + 1 { return k % 2 == 0 ? nu : nv }
                        return u[k - 2]
                    }
                }
                break
            }
        }
        pos = mesh.pos + [np.x, np.y, np.z]
        return (Mesh(pos, faces, uvs), ni)
    }

    // MARK: eğme

    static func bend<C: Collection>(_ mesh: Mesh, _ edges: C, _ amount: Float, _ segments: Int = 4) -> Mesh where C.Element == EdgeRef {
        if edges.isEmpty || abs(amount) < 1e-4 { return mesh }
        var m = mesh
        let seg = segments.coerceIn(2, 12)
        for e0 in edges.ktDistinct() {
            if e0.a >= m.vertexCount || e0.b >= m.vertexCount || facesOfEdge(m, e0).isEmpty { continue }
            let pa = m.vertex(e0.a), pb = m.vertex(e0.b)
            let len = (pb - pa).length
            let n = edgeNormal(m, e0)
            var a = e0.a
            var added: [(Int, Float)] = []
            for k in 1..<seg {
                let t = Float(k) / Float(seg)
                let remainingStart = Float(k - 1) / Float(seg)
                let local = (t - remainingStart) / (1 - remainingStart)
                let split = splitEdge(m, EdgeRef.of(a, e0.b), a < e0.b ? local : 1 - local)
                m = split.0
                added.append((split.1, t))
                a = split.1
            }
            var pos = m.pos
            for (vi, t) in added {
                let h = amount * len * 2 * t * (1 - t)
                pos[3 * vi] += n.x * h; pos[3 * vi + 1] += n.y * h; pos[3 * vi + 2] += n.z * h
            }
            m = Mesh(pos, m.faces, m.uvs)
        }
        return m
    }

    // MARK: germe ve itme

    static func stretch<C: Collection>(_ mesh: Mesh, _ edges: C, _ factor: Float) -> Mesh where C.Element == EdgeRef {
        if edges.isEmpty { return mesh }
        var acc: [Int: V3] = [:], cnt: [Int: Int] = [:]
        for e in edges.ktDistinct() {
            if e.a >= mesh.vertexCount || e.b >= mesh.vertexCount { continue }
            let pa = mesh.vertex(e.a), pb = mesh.vertex(e.b)
            let mid = (pa + pb) * 0.5
            acc[e.a] = (acc[e.a] ?? .ZERO) + (pa - mid) * (factor - 1); cnt[e.a, default: 0] += 1
            acc[e.b] = (acc[e.b] ?? .ZERO) + (pb - mid) * (factor - 1); cnt[e.b, default: 0] += 1
        }
        return displace(mesh, acc, cnt)
    }

    static func push<C: Collection>(_ mesh: Mesh, _ edges: C, _ distance: Float) -> Mesh where C.Element == EdgeRef {
        if edges.isEmpty { return mesh }
        var acc: [Int: V3] = [:], cnt: [Int: Int] = [:]
        for e in edges.ktDistinct() {
            if e.a >= mesh.vertexCount || e.b >= mesh.vertexCount { continue }
            let d = edgeNormal(mesh, e) * distance
            acc[e.a] = (acc[e.a] ?? .ZERO) + d; cnt[e.a, default: 0] += 1
            acc[e.b] = (acc[e.b] ?? .ZERO) + d; cnt[e.b, default: 0] += 1
        }
        return displace(mesh, acc, cnt)
    }

    private static func displace(_ mesh: Mesh, _ acc: [Int: V3], _ cnt: [Int: Int]) -> Mesh {
        if acc.isEmpty { return mesh }
        var pos = mesh.pos
        for (i, d) in acc {
            let k = Float(cnt[i] ?? 1)
            pos[3 * i] += d.x / k; pos[3 * i + 1] += d.y / k; pos[3 * i + 2] += d.z / k
        }
        return Mesh(pos, mesh.faces, mesh.uvs)
    }

    // MARK: yuvarlatma

    static func round(_ mesh: Mesh, _ e: EdgeRef, _ radius: Float, _ segments: Int = 4) -> Mesh? {
        if e.a >= mesh.vertexCount || e.b >= mesh.vertexCount { return nil }
        let adj = facesOfEdge(mesh, e)
        if adj.count != 2 { return nil }
        func has(_ f: Int, _ p: Int, _ q: Int) -> Bool {
            let face = mesh.faces[f]
            for i in face.indices where face[i] == p && face[(i + 1) % face.count] == q { return true }
            return false
        }
        let f1 = has(adj[0], e.a, e.b) ? adj[0] : adj[1]
        let f2 = f1 == adj[0] ? adj[1] : adj[0]
        if !has(f2, e.b, e.a) { return nil }
        let a = e.a, b = e.b
        let vf = mesh.vertexFaces
        if vf[a].count != 3 || vf[b].count != 3 { return nil }
        func prev(_ f: Int, _ x: Int) -> Int { let face = mesh.faces[f]; let i = face.firstIndex(of: x) ?? -1; return face[(i - 1 + face.count) % face.count] }
        func next(_ f: Int, _ x: Int) -> Int { let face = mesh.faces[f]; let i = face.firstIndex(of: x) ?? -1; return face[(i + 1) % face.count] }
        let x1 = prev(f1, a), x2 = next(f2, a)
        let y1 = next(f1, b), y2 = prev(f2, b)
        guard let capA = vf[a].first(where: { $0 != f1 && $0 != f2 }),
              let capB = vf[b].first(where: { $0 != f1 && $0 != f2 }) else { return nil }
        if capA == capB { return nil }
        let pa = mesh.vertex(a), pb = mesh.vertex(b)
        let limit = 0.45 * Swift.min(Swift.min((mesh.vertex(x1) - pa).length, (mesh.vertex(x2) - pa).length),
                                     Swift.min((mesh.vertex(y1) - pb).length, (mesh.vertex(y2) - pb).length))
        let r = radius.coerceIn(1e-4, Swift.max(1e-4, limit))
        let k = segments.coerceIn(1, 12)
        func arc(_ p: V3, _ s: V3, _ t: V3) -> [V3] {
            let p1 = p + (s - p).normalized() * r
            let p2 = p + (t - p).normalized() * r
            return (0...k).map { i in
                let u = Float(i) / Float(k)
                return p1 * ((1 - u) * (1 - u)) + p * (2 * u * (1 - u)) + p2 * (u * u)
            }
        }
        let arcA = arc(pa, mesh.vertex(x1), mesh.vertex(x2))
        let arcB = arc(pb, mesh.vertex(y1), mesh.vertex(y2))
        let base = mesh.vertexCount
        var pos = mesh.pos + [Float](repeating: 0, count: (k + 1) * 6)
        func put(_ i: Int, _ p: V3) { pos[3 * i] = p.x; pos[3 * i + 1] = p.y; pos[3 * i + 2] = p.z }
        let ia = (0...k).map { base + $0 }
        let ib = (0...k).map { base + k + 1 + $0 }
        for i in 0...k { put(ia[i], arcA[i]); put(ib[i], arcB[i]) }
        var faces: [[Int]] = []
        for f in 0..<mesh.faceCount {
            let face = mesh.faces[f]
            switch f {
            case f1: faces.append(face.map { $0 == a ? ia[0] : $0 == b ? ib[0] : $0 })
            case f2: faces.append(face.map { $0 == a ? ia[k] : $0 == b ? ib[k] : $0 })
            case capA: faces.append(replaceInCap(face, a, x1, ia))
            case capB: faces.append(replaceInCap(face, b, y1, ib))
            default: faces.append(face)
            }
        }
        for i in 0..<k { faces.append([ib[i], ia[i], ia[i + 1], ib[i + 1]]) }
        return Mesh(pos, faces).compact()
    }

    private static func replaceInCap(_ face: [Int], _ corner: Int, _ side1: Int, _ arc: [Int]) -> [Int] {
        guard let i = face.firstIndex(of: corner) else { return face }
        let before = face[(i - 1 + face.count) % face.count]
        let seq = before == side1 ? arc : arc.reversed()
        var out: [Int] = []
        for j in face.indices { if j == i { out += seq } else { out.append(face[j]) } }
        return out
    }

    static func extent(_ mesh: Mesh) -> Float {
        let s = mesh.bounds.size
        return Swift.max(1e-4, Swift.max(s.x, Swift.max(s.y, s.z)))
    }

    static func dist(_ a: V3, _ b: V3) -> Float {
        ((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z)).squareRoot()
    }
}
