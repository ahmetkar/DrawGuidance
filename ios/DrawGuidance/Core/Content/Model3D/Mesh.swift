import Foundation

/// Çokgen ağ (Android `Mesh`): [pos] köşe konumları (x, y, z…), [faces] yüzlerin köşe sıraları (dışarıdan saat yönünün
/// tersine). Değişmezdir; düzenleme işlemleri yeni ağ döndürür. Türetilmiş bilgiler ilk istendiğinde hesaplanır.
final class Mesh: Equatable, Hashable {
    let pos: [Float]
    let faces: [[Int]]
    let uvs: [[Float]]?

    init(_ pos: [Float], _ faces: [[Int]], _ uvs: [[Float]]? = nil) { self.pos = pos; self.faces = faces; self.uvs = uvs }

    private let lock = NSLock()
    private var _triangles: [[Int]]?, _faceNormals: [Float]?, _vertexFaces: [[Int]]?, _edges: [Int]?
    private var _closed: Bool?, _bounds: Box3?
    private var cache: (Float, [[Float]])?

    private func memo<T>(_ slot: ReferenceWritableKeyPath<Mesh, T?>, _ make: () -> T) -> T {
        lock.lock(); if let v = self[keyPath: slot] { lock.unlock(); return v }; lock.unlock()
        let v = make()
        lock.lock(); self[keyPath: slot] = v; lock.unlock()
        return v
    }

    /// Yüzlerin üçgenleri (yüz içi köşe sıraları, üçlüler).
    var triangles: [[Int]] { memo(\._triangles) { faces.map { MeshOps.triangulate(pos, $0) } } }

    var triangleCount: Int { triangles.reduce(0) { $0 + $1.count / 3 } }
    var vertexCount: Int { pos.count / 3 }
    var faceCount: Int { faces.count }

    func vertex(_ i: Int) -> V3 { V3(pos[3 * i], pos[3 * i + 1], pos[3 * i + 2]) }

    /// Newell yüz normalleri (birim).
    var faceNormals: [Float] {
        memo(\._faceNormals) {
            var out = [Float](repeating: 0, count: faces.count * 3)
            for f in faces.indices {
                let face = faces[f]
                var nx: Float = 0, ny: Float = 0, nz: Float = 0
                for i in face.indices {
                    let a = face[i] * 3, b = face[(i + 1) % face.count] * 3
                    nx += (pos[a + 1] - pos[b + 1]) * (pos[a + 2] + pos[b + 2])
                    ny += (pos[a + 2] - pos[b + 2]) * (pos[a] + pos[b])
                    nz += (pos[a] - pos[b]) * (pos[a + 1] + pos[b + 1])
                }
                let l = Swift.max((nx * nx + ny * ny + nz * nz).squareRoot(), 1e-12)
                out[3 * f] = nx / l; out[3 * f + 1] = ny / l; out[3 * f + 2] = nz / l
            }
            return out
        }
    }

    func faceNormal(_ f: Int) -> V3 { let n = faceNormals; return V3(n[3 * f], n[3 * f + 1], n[3 * f + 2]) }

    func faceCenter(_ f: Int) -> V3 {
        let face = faces[f]
        var x: Float = 0, y: Float = 0, z: Float = 0
        for v in face { x += pos[3 * v]; y += pos[3 * v + 1]; z += pos[3 * v + 2] }
        let n = Float(Swift.max(face.count, 1))
        return V3(x / n, y / n, z / n)
    }

    /// Her köşeye değen yüzler.
    var vertexFaces: [[Int]] {
        memo(\._vertexFaces) {
            var out = [[Int]](repeating: [], count: vertexCount)
            for (fi, f) in faces.enumerated() { for v in f { out[v].append(fi) } }
            return out
        }
    }

    /// Kenarlar: (a, b, yüz1, yüz2) dörtlüleri; açık kenarda yüz2 = −1.
    var edges: [Int] {
        memo(\._edges) {
            var map: [Int64: Int] = [:]
            map.reserveCapacity(faces.count * 4)
            var list: [Int] = []
            list.reserveCapacity(faces.count * 8)
            for f in faces.indices {
                let face = faces[f]
                for i in face.indices {
                    let a = face[i], b = face[(i + 1) % face.count]
                    if a == b { continue }
                    let key = a < b ? (Int64(a) << 32) | Int64(b) : (Int64(b) << 32) | Int64(a)
                    if let at = map[key] {
                        if list[at + 3] == -1 && list[at + 2] != f { list[at + 3] = f }
                    } else {
                        map[key] = list.count
                        list += [Swift.min(a, b), Swift.max(a, b), f, -1]
                    }
                }
            }
            return list
        }
    }

    /// Ağ kapalı mı (her kenar iki yüzün).
    var closed: Bool {
        memo(\._closed) {
            let e = edges
            if e.isEmpty { return false }
            var i = 0
            while i < e.count { if e[i + 3] < 0 { return false }; i += 4 }
            return true
        }
    }

    /// Yumuşak gölgelemede yüz köşelerinin normalleri.
    func cornerNormals(_ creaseCos: Float = 0.7) -> [[Float]] {
        lock.lock(); if let c = cache, c.0 == creaseCos { lock.unlock(); return c.1 }; lock.unlock()
        let fn = faceNormals, vf = vertexFaces
        let out: [[Float]] = faces.indices.map { f in
            let face = faces[f]
            var n = [Float](repeating: 0, count: face.count * 3)
            let ax = fn[3 * f], ay = fn[3 * f + 1], az = fn[3 * f + 2]
            for i in face.indices {
                var sx: Float = 0, sy: Float = 0, sz: Float = 0
                for g in vf[face[i]] {
                    let bx = fn[3 * g], by = fn[3 * g + 1], bz = fn[3 * g + 2]
                    if ax * bx + ay * by + az * bz >= creaseCos { sx += bx; sy += by; sz += bz }
                }
                let l = (sx * sx + sy * sy + sz * sz).squareRoot()
                if l < 1e-9 { n[3 * i] = ax; n[3 * i + 1] = ay; n[3 * i + 2] = az }
                else { n[3 * i] = sx / l; n[3 * i + 1] = sy / l; n[3 * i + 2] = sz / l }
            }
            return n
        }
        lock.lock(); cache = (creaseCos, out); lock.unlock()
        return out
    }

    /// Nesne uzayındaki kutu.
    var bounds: Box3 {
        memo(\._bounds) {
            if pos.isEmpty { return Box3(.ZERO, .ZERO) }
            var ax = Float.infinity, ay = Float.infinity, az = Float.infinity
            var bx = -Float.infinity, by = -Float.infinity, bz = -Float.infinity
            var i = 0
            while i < pos.count {
                ax = Swift.min(ax, pos[i]); ay = Swift.min(ay, pos[i + 1]); az = Swift.min(az, pos[i + 2])
                bx = Swift.max(bx, pos[i]); by = Swift.max(by, pos[i + 1]); bz = Swift.max(bz, pos[i + 2])
                i += 3
            }
            return Box3(V3(ax, ay, az), V3(bx, by, bz))
        }
    }

    // MARK: düzenleme

    func moveVertices<C: Collection>(_ indices: C, _ d: V3) -> Mesh where C.Element == Int {
        var p = pos
        for i in Set(indices) where i >= 0 && i < vertexCount {
            p[3 * i] += d.x; p[3 * i + 1] += d.y; p[3 * i + 2] += d.z
        }
        return Mesh(p, faces, uvs)
    }

    func scaleFace(_ f: Int, _ s: Float) -> Mesh {
        if !faces.indices.contains(f) { return self }
        let c = faceCenter(f)
        var p = pos
        for v in Set(faces[f]) {
            p[3 * v] = c.x + (pos[3 * v] - c.x) * s
            p[3 * v + 1] = c.y + (pos[3 * v + 1] - c.y) * s
            p[3 * v + 2] = c.z + (pos[3 * v + 2] - c.z) * s
        }
        return Mesh(p, faces, uvs)
    }

    func extrudeFace(_ f: Int, _ distance: Float) -> Mesh {
        if !faces.indices.contains(f) { return self }
        let face = faces[f], n = faceNormal(f), base = vertexCount
        var p = pos + [Float](repeating: 0, count: face.count * 3)
        for i in face.indices {
            let v = face[i]
            p[3 * (base + i)] = pos[3 * v] + n.x * distance
            p[3 * (base + i) + 1] = pos[3 * v + 1] + n.y * distance
            p[3 * (base + i) + 2] = pos[3 * v + 2] + n.z * distance
        }
        var nf = faces
        nf[f] = (0..<face.count).map { base + $0 }
        for i in face.indices { let j = (i + 1) % face.count; nf.append([face[i], face[j], base + j, base + i]) }
        return Mesh(p, nf)
    }

    func insetFace(_ f: Int, _ amount: Float) -> Mesh {
        if !faces.indices.contains(f) { return self }
        let t = amount.coerceIn(0.02, 0.95)
        let face = faces[f], c = faceCenter(f), base = vertexCount
        var p = pos + [Float](repeating: 0, count: face.count * 3)
        for i in face.indices {
            let v = face[i]
            p[3 * (base + i)] = pos[3 * v] + (c.x - pos[3 * v]) * t
            p[3 * (base + i) + 1] = pos[3 * v + 1] + (c.y - pos[3 * v + 1]) * t
            p[3 * (base + i) + 2] = pos[3 * v + 2] + (c.z - pos[3 * v + 2]) * t
        }
        var nf = faces
        nf[f] = (0..<face.count).map { base + $0 }
        for i in face.indices { let j = (i + 1) % face.count; nf.append([face[i], face[j], base + j, base + i]) }
        return Mesh(p, nf)
    }

    func deleteFace(_ f: Int) -> Mesh {
        if !faces.indices.contains(f) || faces.count <= 1 { return self }
        var nf = faces; nf.remove(at: f)
        var nu = uvs; nu?.remove(at: f)
        return Mesh(pos, nf, nu).compact()
    }

    /// Kullanılmayan köşeleri atar.
    func compact() -> Mesh {
        var used = [Bool](repeating: false, count: vertexCount)
        for f in faces { for v in f { used[v] = true } }
        if used.allSatisfy({ $0 }) { return self }
        var map = [Int](repeating: -1, count: vertexCount)
        var k = 0
        for i in used.indices where used[i] { map[i] = k; k += 1 }
        var p = [Float](repeating: 0, count: k * 3)
        for i in used.indices where used[i] { let j = map[i]; p[3 * j] = pos[3 * i]; p[3 * j + 1] = pos[3 * i + 1]; p[3 * j + 2] = pos[3 * i + 2] }
        return Mesh(p, faces.map { $0.map { map[$0] } }, uvs)
    }

    func mapPositions(_ transform: (V3) -> V3) -> Mesh {
        var p = [Float](repeating: 0, count: pos.count)
        for i in 0..<vertexCount {
            let v = transform(vertex(i))
            p[3 * i] = v.x; p[3 * i + 1] = v.y; p[3 * i + 2] = v.z
        }
        return Mesh(p, faces, uvs)
    }

    func maxNonPlanarity() -> Float {
        var worst: Float = 0
        for f in faces.indices {
            let n = faceNormal(f), c = faceCenter(f)
            for v in faces[f] { worst = Swift.max(worst, abs((vertex(v) - c).dot(n))) }
        }
        return worst
    }

    static func == (a: Mesh, b: Mesh) -> Bool { a === b || (a.pos == b.pos && a.faces == b.faces && a.uvs == b.uvs) }

    func hash(into h: inout Hasher) { h.combine(pos.count); h.combine(faces.count); h.combine(pos.first) }

    func withoutUvs() -> Mesh { uvs == nil ? self : Mesh(pos, faces) }

    /// Ağların birleşimi.
    static func concat(_ meshes: [Mesh]) -> Mesh {
        var p: [Float] = []
        p.reserveCapacity(meshes.reduce(0) { $0 + $1.pos.count })
        var f: [[Int]] = []
        let keepUv = !meshes.isEmpty && meshes.allSatisfy { $0.uvs != nil }
        var uv: [[Float]] = []
        var off = 0
        for m in meshes {
            p += m.pos
            for face in m.faces { f.append(face.map { $0 + off }) }
            if keepUv { uv += m.uvs! }
            off += m.vertexCount
        }
        return Mesh(p, f, keepUv ? uv : nil)
    }
}
