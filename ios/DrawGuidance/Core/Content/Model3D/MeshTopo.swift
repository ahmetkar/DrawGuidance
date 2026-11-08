import Foundation

/// Yüz sırası değişen işlemin sonucu: yeni ağ ve her yeni yüzün malzemesini alacağı eski yüz (−1: parçanın kendi).
struct TopoResult { let mesh: Mesh; let origin: [Int] }

/// Halkadan kesmenin sonucu.
struct CutResult { let rest: TopoResult; let piece: TopoResult; let away: V3 }

/// v25 ağın yapısını değiştiren düzenlemeler (Android `MeshTopo`). Bölge parametreleri sıralı (Kotlin LinkedHashSet) dizilerdir.
enum MeshTopo {

    // MARK: nokta silme

    static func deleteVertex(_ mesh: Mesh, _ v: Int) -> TopoResult? {
        if v < 0 || v >= mesh.vertexCount { return nil }
        if mesh.vertexFaces[v].isEmpty { return nil }
        var faces: [[Int]] = [], origin: [Int] = []
        var next: [Int: Int] = [:], nextOrder: [Int] = []
        var capOrigin = -1
        for f in mesh.faces.indices {
            let face = mesh.faces[f]
            guard let i = face.firstIndex(of: v) else { faces.append(face); origin.append(f); continue }
            let n = face.count
            let a = face[(i - 1 + n) % n], b = face[(i + 1) % n]
            if a != b {
                if next[a] == nil { nextOrder.append(a) }
                next[a] = b
                if capOrigin < 0 { capOrigin = f }
            }
            let reduced = face.filter { $0 != v }
            if reduced.count >= 3 { faces.append(reduced); origin.append(f) }
        }
        var visited = Set<Int>()
        for start in javaHashOrder(nextOrder) {
            if visited.contains(start) { continue }
            var loop: [Int] = []
            var cur = start
            var closed = false
            while true {
                if !visited.insert(cur).inserted { closed = cur == start; break }
                loop.append(cur)
                guard let nx = next[cur] else { break }
                cur = nx
                if cur == start { closed = true; break }
            }
            if closed && loop.count >= 3 { faces.append(loop.reversed()); origin.append(capOrigin) }
        }
        if faces.isEmpty { return nil }
        return compacted(Mesh(mesh.pos, faces), origin)
    }

    // MARK: iki noktayı birleştirme

    static func commonFace(_ mesh: Mesh, _ a: Int, _ b: Int) -> Int {
        if a < 0 || a >= mesh.vertexCount || b < 0 || b >= mesh.vertexCount || a == b { return -1 }
        let bf = Set(mesh.vertexFaces[b])
        for f in mesh.vertexFaces[a] where bf.contains(f) {
            let face = mesh.faces[f]
            let ia = face.firstIndex(of: a)!, ib = face.firstIndex(of: b)!
            let d = abs(ia - ib)
            if d == 1 || d == face.count - 1 { continue }
            return f
        }
        return -1
    }

    static func isEdge(_ mesh: Mesh, _ a: Int, _ b: Int) -> Bool {
        a != b && a >= 0 && a < mesh.vertexCount && b >= 0 && b < mesh.vertexCount && !MeshOps.facesOfEdge(mesh, EdgeRef.of(a, b)).isEmpty
    }

    static func connect(_ mesh: Mesh, _ a: Int, _ b: Int) -> TopoResult? {
        let f = commonFace(mesh, a, b)
        if f < 0 { return nil }
        let face = mesh.faces[f]
        let n = face.count
        let ia = face.firstIndex(of: a)!, ib = face.firstIndex(of: b)!
        func arc(_ from: Int, _ to: Int) -> [Int] {
            var out: [Int] = []
            var i = from
            while true { out.append(i); if i == to { break }; i = (i + 1) % n }
            return out
        }
        let k1 = arc(ia, ib), k2 = arc(ib, ia)
        var faces = mesh.faces + [[]]
        faces[f] = k1.map { face[$0] }
        faces[mesh.faceCount] = k2.map { face[$0] }
        let uvs: [[Float]]? = mesh.uvs.map { u in
            (0..<faces.count).map { i in
                if i == f { return (0..<(k1.count * 2)).map { j in u[f][2 * k1[j / 2] + j % 2] } }
                if i == mesh.faceCount { return (0..<(k2.count * 2)).map { j in u[f][2 * k2[j / 2] + j % 2] } }
                return u[i]
            }
        }
        let origin = (0..<faces.count).map { $0 < mesh.faceCount ? $0 : f }
        return TopoResult(mesh: Mesh(mesh.pos, faces, uvs), origin: origin)
    }

    // MARK: halkadan kesme

    static func cutLoop(_ mesh: Mesh, _ loop: [Int]) -> CutResult? {
        if loop.count < 3 || Set(loop).count != loop.count { return nil }
        let n = loop.count
        var loopEdges = Set<EdgeRef>()
        for i in 0..<n {
            let a = loop[i], b = loop[(i + 1) % n]
            if !isEdge(mesh, a, b) { return nil }
            loopEdges.insert(EdgeRef.of(a, b))
        }
        var parent = Array(0..<mesh.faceCount)
        func find(_ x: Int) -> Int { var r = x; while parent[r] != r { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
        func union(_ a: Int, _ b: Int) { let ra = find(a), rb = find(b); if ra != rb { parent[ra] = rb } }
        let e = mesh.edges
        var i = 0
        while i < e.count {
            let f1 = e[i + 2], f2 = e[i + 3]
            if f2 >= 0 && !loopEdges.contains(EdgeRef.of(e[i], e[i + 1])) { union(f1, f2) }
            i += 4
        }
        var left = -1, right = -1
        for k in 0..<n {
            let a = loop[k], b = loop[(k + 1) % n]
            for f in MeshOps.facesOfEdge(mesh, EdgeRef.of(a, b)) {
                let face = mesh.faces[f]
                let ia = face.firstIndex(of: a)!
                let dir = face[(ia + 1) % face.count] == b
                let c = find(f)
                if dir { if left < 0 { left = c } else if left != c { return nil } }
                else { if right < 0 { right = c } else if right != c { return nil } }
            }
        }
        if left < 0 || right < 0 || left == right { return nil }
        let leftFaces = (0..<mesh.faceCount).filter { find($0) == left }
        let rightSet = (0..<mesh.faceCount).filter { find($0) == right }
        func area(_ fs: [Int]) -> Double { fs.reduce(0.0) { $0 + faceArea(mesh, $1) } }
        let pieceIsLeft = area(leftFaces) <= area(rightSet)
        let pieceFaces = pieceIsLeft ? leftFaces : rightSet
        let pieceSet = Set(pieceFaces)
        let restFaces = (0..<mesh.faceCount).filter { !pieceSet.contains($0) }
        let forward = loop, backward = Array(loop.reversed())
        let pieceCap = pieceIsLeft ? backward : forward
        let restCap = pieceIsLeft ? forward : backward
        let capOriginPiece = pieceFaces.first ?? -1
        let other = pieceIsLeft ? right : left
        let capOriginRest = restFaces.first { find($0) == other } ?? -1
        func build(_ fs: [Int], _ cap: [Int], _ capOrigin: Int) -> TopoResult {
            compacted(Mesh(mesh.pos, fs.map { mesh.faces[$0] } + [cap]), fs + [capOrigin])
        }
        let rest = build(restFaces, restCap, capOriginRest)
        let piece = build(pieceFaces, pieceCap, capOriginPiece)
        let d = centroid(piece.mesh) - centroid(rest.mesh)
        let away = d.length < 1e-6 ? loopNormal(mesh, loop) : d.normalized()
        return CutResult(rest: rest, piece: piece, away: away)
    }

    private static func loopNormal(_ mesh: Mesh, _ loop: [Int]) -> V3 {
        var nx: Float = 0, ny: Float = 0, nz: Float = 0
        for i in loop.indices {
            let a = mesh.vertex(loop[i]), b = mesh.vertex(loop[(i + 1) % loop.count])
            nx += (a.y - b.y) * (a.z + b.z); ny += (a.z - b.z) * (a.x + b.x); nz += (a.x - b.x) * (a.y + b.y)
        }
        return V3(nx, ny, nz).normalized()
    }

    static func faceArea(_ mesh: Mesh, _ f: Int) -> Double {
        let face = mesh.faces[f]
        var nx = 0.0, ny = 0.0, nz = 0.0
        let p = mesh.pos
        for i in face.indices {
            let a = face[i] * 3, b = face[(i + 1) % face.count] * 3
            nx += Double(p[a + 1] - p[b + 1]) * Double(p[a + 2] + p[b + 2])
            ny += Double(p[a + 2] - p[b + 2]) * Double(p[a] + p[b])
            nz += Double(p[a] - p[b]) * Double(p[a + 1] + p[b + 1])
        }
        return (nx * nx + ny * ny + nz * nz).squareRoot() * 0.5
    }

    static func centroid(_ mesh: Mesh) -> V3 {
        if mesh.vertexCount == 0 { return .ZERO }
        var x = 0.0, y = 0.0, z = 0.0
        for i in 0..<mesh.vertexCount { x += Double(mesh.pos[3 * i]); y += Double(mesh.pos[3 * i + 1]); z += Double(mesh.pos[3 * i + 2]) }
        let k = 1.0 / Double(mesh.vertexCount)
        return V3(Float(x * k), Float(y * k), Float(z * k))
    }

    // MARK: yüzey bölgesi

    /// Dokunulan yüzün yüzeyi (sıralı, Kotlin LinkedHashSet gibi).
    static func surfaceRegion(_ mesh: Mesh, _ seed: Int, _ maxAngleDeg: Float = 32, _ limit: Int = 20_000) -> [Int] {
        if seed < 0 || seed >= mesh.faceCount { return [] }
        let cosMax = Float(cos(toRadians(Double(maxAngleDeg))))
        let adj = adjacency(mesh)
        let fn = mesh.faceNormals
        var out = [seed]
        var inOut: Set<Int> = [seed]
        var queue = [seed], head = 0
        while head < queue.count && out.count < limit {
            let f = queue[head]; head += 1
            for g in adj[f] where !inOut.contains(g) {
                let dot = fn[3 * f] * fn[3 * g] + fn[3 * f + 1] * fn[3 * g + 1] + fn[3 * f + 2] * fn[3 * g + 2]
                if dot >= cosMax { out.append(g); inOut.insert(g); queue.append(g) }
            }
        }
        return out
    }

    static func adjacency(_ mesh: Mesh) -> [[Int]] {
        var lists = [[Int]](repeating: [], count: mesh.faceCount)
        let e = mesh.edges
        var i = 0
        while i < e.count {
            let f1 = e[i + 2], f2 = e[i + 3]
            if f2 >= 0 && f1 != f2 { lists[f1].append(f2); lists[f2].append(f1) }
            i += 4
        }
        return lists
    }

    static func components(_ mesh: Mesh) -> [[Int]] {
        let adj = adjacency(mesh)
        var parent = Array(0..<mesh.faceCount)
        func find(_ x: Int) -> Int { var r = x; while parent[r] != r { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
        for f in adj.indices { for g in adj[f] { let a = find(f), b = find(g); if a != b { parent[a] = b } } }
        for vf in mesh.vertexFaces where vf.count > 1 { for k in 1..<vf.count { let a = find(vf[0]), b = find(vf[k]); if a != b { parent[a] = b } } }
        var order: [Int] = [], groups: [Int: [Int]] = [:]
        for f in 0..<mesh.faceCount {
            let r = find(f)
            if groups[r] == nil { order.append(r); groups[r] = [] }
            groups[r]!.append(f)
        }
        return order.map { groups[$0]! }
    }

    static func subMesh(_ mesh: Mesh, _ faces: [Int]) -> TopoResult {
        compacted(Mesh(mesh.pos, faces.map { mesh.faces[$0] }), faces)
    }

    // MARK: bölge yükseltme

    static func extrudeRegion(_ mesh: Mesh, _ region: [Int], _ distance: Float) -> TopoResult {
        let faces = region.filter { $0 >= 0 && $0 < mesh.faceCount }.ktDistinct()
        let faceSet = Set(faces)
        if faces.isEmpty || abs(distance) < 1e-7 { return TopoResult(mesh: mesh, origin: Array(0..<mesh.faceCount)) }
        let fn = mesh.faceNormals
        var normals: [Int: [Float]] = [:], normalOrder: [Int] = []
        for f in faces { for v in mesh.faces[f] {
            if normals[v] == nil { normals[v] = [0, 0, 0]; normalOrder.append(v) }
            normals[v]![0] += fn[3 * f]; normals[v]![1] += fn[3 * f + 1]; normals[v]![2] += fn[3 * f + 2]
        } }
        let keys = javaHashOrder(normalOrder)
        var newIndex: [Int: Int] = [:]
        var count = mesh.vertexCount
        for v in keys { newIndex[v] = count; count += 1 }
        var pos = mesh.pos + [Float](repeating: 0, count: (count - mesh.vertexCount) * 3)
        for v in keys {
            let acc = normals[v]!
            let l = (acc[0] * acc[0] + acc[1] * acc[1] + acc[2] * acc[2]).squareRoot().coerceAtLeast(1e-9)
            let nx = acc[0] / l, ny = acc[1] / l, nz = acc[2] / l
            var minDot: Float = 1
            for f in mesh.vertexFaces[v] where faceSet.contains(f) { minDot = Swift.min(minDot, nx * fn[3 * f] + ny * fn[3 * f + 1] + nz * fn[3 * f + 2]) }
            let d = distance / Swift.max(0.35, minDot)
            let j = newIndex[v]!
            pos[3 * j] = mesh.pos[3 * v] + nx * d; pos[3 * j + 1] = mesh.pos[3 * v + 1] + ny * d; pos[3 * j + 2] = mesh.pos[3 * v + 2] + nz * d
        }
        var directed = Set<Int64>()
        for f in faces { let fc = mesh.faces[f]; for i in fc.indices { directed.insert(key(fc[i], fc[(i + 1) % fc.count])) } }
        var out: [[Int]] = [], origin: [Int] = []
        for f in 0..<mesh.faceCount {
            let fc = mesh.faces[f]
            out.append(faceSet.contains(f) ? fc.map { newIndex[$0]! } : fc)
            origin.append(f)
        }
        for f in faces {
            let fc = mesh.faces[f]
            for i in fc.indices {
                let a = fc[i], b = fc[(i + 1) % fc.count]
                if directed.contains(key(b, a)) { continue }
                out.append([a, b, newIndex[b]!, newIndex[a]!])
                origin.append(f)
            }
        }
        return TopoResult(mesh: Mesh(pos, out), origin: origin)
    }

    static func scaleRegion(_ mesh: Mesh, _ region: [Int], _ s: Float) -> Mesh {
        let verts = region.filter { $0 >= 0 && $0 < mesh.faceCount }.flatMap { mesh.faces[$0] }.ktDistinct()
        if verts.isEmpty { return mesh }
        var cx: Float = 0, cy: Float = 0, cz: Float = 0
        for v in verts { cx += mesh.pos[3 * v]; cy += mesh.pos[3 * v + 1]; cz += mesh.pos[3 * v + 2] }
        let k = Float(verts.count)
        cx /= k; cy /= k; cz /= k
        var p = mesh.pos
        for v in verts {
            p[3 * v] = cx + (p[3 * v] - cx) * s; p[3 * v + 1] = cy + (p[3 * v + 1] - cy) * s; p[3 * v + 2] = cz + (p[3 * v + 2] - cz) * s
        }
        return Mesh(p, mesh.faces, mesh.uvs)
    }

    static func deleteFaces(_ mesh: Mesh, _ region: [Int]) -> TopoResult? {
        let r = Set(region)
        let keep = (0..<mesh.faceCount).filter { !r.contains($0) }
        if keep.isEmpty || keep.count == mesh.faceCount { return nil }
        return subMesh(mesh, keep)
    }

    private static func key(_ a: Int, _ b: Int) -> Int64 { (Int64(a) << 32) | (Int64(b) & 0xFFFF_FFFF) }

    private static func compacted(_ mesh: Mesh, _ origin: [Int]) -> TopoResult {
        let c = mesh.compact()
        return TopoResult(mesh: Mesh(c.pos, c.faces), origin: origin)
    }
}
