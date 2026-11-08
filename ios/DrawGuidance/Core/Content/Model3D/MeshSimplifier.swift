import Foundation

/// Üçgen ağ (içe aktarma için).
struct TriMesh {
    let pos: [Float]
    let tris: [Int]
    let uv: [Float]?
    var vertexCount: Int { pos.count / 3 }
    var triangleCount: Int { tris.count / 3 }
}

/// v24 ağ sadeleştirme: kaynak, ızgara kümeleme ve QEM büzülmesi (Android `MeshSimplifier`).
enum MeshSimplifier {

    static func simplify(_ mesh: TriMesh, _ target: Int) -> TriMesh {
        var m = weld(mesh)
        if m.triangleCount <= target { return m }
        if m.triangleCount > max(60_000, target * 8) { m = cluster(m, max(target * 6, 20_000)) }
        if m.triangleCount <= target { return m }
        return Qem(m).run(target)
    }

    static func weld(_ mesh: TriMesh) -> TriMesh {
        let n = mesh.vertexCount
        if n == 0 { return mesh }
        var ax = Float.infinity, ay = Float.infinity, az = Float.infinity
        var bx = -Float.infinity, by = -Float.infinity, bz = -Float.infinity
        let p = mesh.pos
        for i in 0..<n {
            ax = min(ax, p[3 * i]); ay = min(ay, p[3 * i + 1]); az = min(az, p[3 * i + 2])
            bx = max(bx, p[3 * i]); by = max(by, p[3 * i + 1]); bz = max(bz, p[3 * i + 2])
        }
        let ext = max(bx - ax, max(by - ay, bz - az)).coerceAtLeast(1e-9)
        let q = ext * 1e-5
        var map = [Int](repeating: 0, count: n)
        var keys: [Int64: Int] = [:]
        keys.reserveCapacity(n * 2)
        var outPos = [Float](repeating: 0, count: n * 3)
        var k = 0
        for i in 0..<n {
            let qx = ktLong((p[3 * i] - ax) / q), qy = ktLong((p[3 * i + 1] - ay) / q), qz = ktLong((p[3 * i + 2] - az) / q)
            let key = (qx &* 73856093) ^ (qy &* 19349663) ^ (qz &* 83492791) ^ (qx << 42) ^ (qy << 21) ^ qz
            if let existing = keys[key], abs(outPos[3 * existing] - p[3 * i]) <= q, abs(outPos[3 * existing + 1] - p[3 * i + 1]) <= q,
               abs(outPos[3 * existing + 2] - p[3 * i + 2]) <= q {
                map[i] = existing
            } else {
                keys[key] = k
                outPos[3 * k] = p[3 * i]; outPos[3 * k + 1] = p[3 * i + 1]; outPos[3 * k + 2] = p[3 * i + 2]
                map[i] = k; k += 1
            }
        }
        return rebuild(Array(outPos.prefix(k * 3)), mesh.tris.map { map[$0] }, mesh.uv)
    }

    /// Kotlin `Float.toLong()` (sıfıra doğru, doygun).
    @inline(__always) private static func ktLong(_ v: Float) -> Int64 {
        if v.isNaN { return 0 }
        if v >= 9.2233715e18 { return Int64.max }
        if v <= -9.2233715e18 { return Int64.min }
        return Int64(v)
    }

    private static func rebuild(_ pos: [Float], _ tris: [Int], _ uv: [Float]?) -> TriMesh {
        var keepTri: [Int] = []
        for t in 0..<(tris.count / 3) {
            let a = tris[3 * t], b = tris[3 * t + 1], c = tris[3 * t + 2]
            if a == b || b == c || a == c { continue }
            keepTri.append(t)
        }
        let n = pos.count / 3
        var used = [Int](repeating: -1, count: n)
        var k = 0
        var outTris = [Int](repeating: 0, count: keepTri.count * 3)
        var outUv: [Float]? = uv != nil ? [Float](repeating: 0, count: keepTri.count * 6) : nil
        for (i, t) in keepTri.enumerated() {
            for c in 0..<3 {
                let v = tris[3 * t + c]
                if used[v] < 0 { used[v] = k; k += 1 }
                outTris[3 * i + c] = used[v]
                if let uv { outUv![6 * i + 2 * c] = uv[6 * t + 2 * c]; outUv![6 * i + 2 * c + 1] = uv[6 * t + 2 * c + 1] }
            }
        }
        var outPos = [Float](repeating: 0, count: k * 3)
        for v in 0..<n where used[v] >= 0 { let j = used[v]; outPos[3 * j] = pos[3 * v]; outPos[3 * j + 1] = pos[3 * v + 1]; outPos[3 * j + 2] = pos[3 * v + 2] }
        return TriMesh(pos: outPos, tris: outTris, uv: outUv)
    }

    static func cluster(_ mesh: TriMesh, _ target: Int) -> TriMesh {
        let p = mesh.pos
        let n = mesh.vertexCount
        var ax = Float.infinity, ay = Float.infinity, az = Float.infinity
        var bx = -Float.infinity, by = -Float.infinity, bz = -Float.infinity
        for i in 0..<n {
            ax = min(ax, p[3 * i]); ay = min(ay, p[3 * i + 1]); az = min(az, p[3 * i + 2])
            bx = max(bx, p[3 * i]); by = max(by, p[3 * i + 1]); bz = max(bz, p[3 * i + 2])
        }
        var cells = Int((Double(target) / 2.0).squareRoot()).coerceIn(8, 1024)
        var result = mesh
        for _ in 0..<4 {
            let ext = max(bx - ax, max(by - ay, bz - az)).coerceAtLeast(1e-9)
            let size = ext / Float(cells)
            var map = [Int](repeating: 0, count: n)
            var ids: [Int64: Int] = [:]
            var sum: [[Float]] = []
            let cl = Int64(cells)
            for i in 0..<n {
                let cx = min(ktLong((p[3 * i] - ax) / size), cl), cy = min(ktLong((p[3 * i + 1] - ay) / size), cl), cz = min(ktLong((p[3 * i + 2] - az) / size), cl)
                let key = (cx &* 1_000_003 &+ cy) &* 1_000_033 &+ cz
                let id: Int
                if let e = ids[key] { id = e } else { sum.append([0, 0, 0, 0]); id = sum.count - 1; ids[key] = id }
                sum[id][0] += p[3 * i]; sum[id][1] += p[3 * i + 1]; sum[id][2] += p[3 * i + 2]; sum[id][3] += 1
                map[i] = id
            }
            var pos = [Float](repeating: 0, count: sum.count * 3)
            for (i, s) in sum.enumerated() { pos[3 * i] = s[0] / s[3]; pos[3 * i + 1] = s[1] / s[3]; pos[3 * i + 2] = s[2] / s[3] }
            result = rebuild(pos, mesh.tris.map { map[$0] }, mesh.uv)
            if Double(result.triangleCount) <= Double(target) * 1.3 || cells <= 8 { return result }
            cells = Int(Double(cells) * 0.7).coerceAtLeast(8)
        }
        return result
    }

    private static func edgeKey(_ a: Int, _ b: Int) -> Int64 { a < b ? (Int64(a) << 32) | Int64(b) : (Int64(b) << 32) | Int64(a) }

    /// QEM büzülmesi.
    private final class Qem {
        var pos: [Float]
        var tris: [Int]
        let uv: [Float]?
        let nv: Int, nt: Int
        var q: [Double]
        var alive: [Bool]
        var vAlive: [Bool]
        var version: [Int]
        var vtris: [[Int]]
        var liveTris: Int
        private var tmpN = [Double](repeating: 0, count: 3)
        private var qq = [Double](repeating: 0, count: 10)

        init(_ m: TriMesh) {
            pos = m.pos; tris = m.tris; uv = m.uv
            nv = m.vertexCount; nt = m.triangleCount
            q = [Double](repeating: 0, count: nv * 10)
            alive = [Bool](repeating: true, count: nt)
            vAlive = [Bool](repeating: true, count: nv)
            version = [Int](repeating: 0, count: nv)
            vtris = [[Int]](repeating: [], count: nv)
            liveTris = nt
            for t in 0..<nt { for c in 0..<3 { vtris[tris[3 * t + c]].append(t) } }
            for t in 0..<nt { addPlane(t) }
            addBoundaryPlanes()
        }

        @discardableResult
        func normal(_ t: Int) -> Double {
            let a = tris[3 * t], b = tris[3 * t + 1], c = tris[3 * t + 2]
            let e1x = Double(pos[3 * b] - pos[3 * a]), e1y = Double(pos[3 * b + 1] - pos[3 * a + 1]), e1z = Double(pos[3 * b + 2] - pos[3 * a + 2])
            let e2x = Double(pos[3 * c] - pos[3 * a]), e2y = Double(pos[3 * c + 1] - pos[3 * a + 1]), e2z = Double(pos[3 * c + 2] - pos[3 * a + 2])
            let nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x
            let l = (nx * nx + ny * ny + nz * nz).squareRoot()
            if l < 1e-20 { tmpN[0] = 0; tmpN[1] = 0; tmpN[2] = 0; return 0 }
            tmpN[0] = nx / l; tmpN[1] = ny / l; tmpN[2] = nz / l
            return l * 0.5
        }

        func addPlane(_ t: Int) {
            let area = normal(t)
            if area <= 0 { return }
            let a = tris[3 * t]
            let d = -(tmpN[0] * Double(pos[3 * a]) + tmpN[1] * Double(pos[3 * a + 1]) + tmpN[2] * Double(pos[3 * a + 2]))
            for c in 0..<3 { addQuadric(tris[3 * t + c], tmpN[0], tmpN[1], tmpN[2], d, area) }
        }

        func addQuadric(_ v: Int, _ a: Double, _ b: Double, _ c: Double, _ d: Double, _ w: Double) {
            let o = v * 10
            q[o] += w * a * a; q[o + 1] += w * a * b; q[o + 2] += w * a * c; q[o + 3] += w * a * d
            q[o + 4] += w * b * b; q[o + 5] += w * b * c; q[o + 6] += w * b * d
            q[o + 7] += w * c * c; q[o + 8] += w * c * d
            q[o + 9] += w * d * d
        }

        func addBoundaryPlanes() {
            var count: [Int64: Int] = [:], owner: [Int64: Int] = [:]
            var order: [Int64] = []
            for t in 0..<nt { for c in 0..<3 {
                let key = MeshSimplifier.edgeKey(tris[3 * t + c], tris[3 * t + (c + 1) % 3])
                if count[key] == nil { order.append(key) }
                count[key, default: 0] += 1
                owner[key] = t
            } }
            for key in javaHashOrder(order, hash: javaLongHash) {
                if count[key] != 1 { continue }
                let a = Int(key >> 32), b = Int(key & 0xFFFF_FFFF)
                guard let t = owner[key] else { continue }
                normal(t)
                let ex = Double(pos[3 * b] - pos[3 * a]), ey = Double(pos[3 * b + 1] - pos[3 * a + 1]), ez = Double(pos[3 * b + 2] - pos[3 * a + 2])
                var px = ey * tmpN[2] - ez * tmpN[1], py = ez * tmpN[0] - ex * tmpN[2], pz = ex * tmpN[1] - ey * tmpN[0]
                let l = (px * px + py * py + pz * pz).squareRoot()
                if l < 1e-20 { continue }
                px /= l; py /= l; pz /= l
                let d = -(px * Double(pos[3 * a]) + py * Double(pos[3 * a + 1]) + pz * Double(pos[3 * a + 2]))
                let w = (ex * ex + ey * ey + ez * ez) * 10.0
                addQuadric(a, px, py, pz, d, w); addQuadric(b, px, py, pz, d, w)
            }
        }

        func error(_ x: Double, _ y: Double, _ z: Double) -> Double {
            // Android ile aynı toplama sırası (soldan sağa)
            var s = qq[0] * x * x
            s += 2 * qq[1] * x * y; s += 2 * qq[2] * x * z; s += 2 * qq[3] * x
            s += qq[4] * y * y; s += 2 * qq[5] * y * z; s += 2 * qq[6] * y
            s += qq[7] * z * z; s += 2 * qq[8] * z; s += qq[9]
            return s
        }

        func cost(_ a: Int, _ b: Int, _ out: inout [Double]) -> Double {
            for i in 0..<10 { qq[i] = q[a * 10 + i] + q[b * 10 + i] }
            let a00 = qq[0], a01 = qq[1], a02 = qq[2], a11 = qq[4], a12 = qq[5], a22 = qq[7]
            let det = a00 * (a11 * a22 - a12 * a12) - a01 * (a01 * a22 - a12 * a02) + a02 * (a01 * a12 - a11 * a02)
            let scale = abs(a00) + abs(a11) + abs(a22)
            if abs(det) > 1e-12 * scale * scale * scale && scale > 0 {
                let b0 = -qq[3], b1 = -qq[6], b2 = -qq[8]
                let x = (b0 * (a11 * a22 - a12 * a12) - a01 * (b1 * a22 - a12 * b2) + a02 * (b1 * a12 - a11 * b2)) / det
                let y = (a00 * (b1 * a22 - a12 * b2) - b0 * (a01 * a22 - a12 * a02) + a02 * (a01 * b2 - b1 * a02)) / det
                let z = (a00 * (a11 * b2 - b1 * a12) - a01 * (a01 * b2 - b1 * a02) + b0 * (a01 * a12 - a11 * a02)) / det
                let mx = Double(pos[3 * a] + pos[3 * b]) * 0.5, my = Double(pos[3 * a + 1] + pos[3 * b + 1]) * 0.5, mz = Double(pos[3 * a + 2] + pos[3 * b + 2]) * 0.5
                let ex = Double(pos[3 * a] - pos[3 * b]), ey = Double(pos[3 * a + 1] - pos[3 * b + 1]), ez = Double(pos[3 * a + 2] - pos[3 * b + 2])
                let len2 = ex * ex + ey * ey + ez * ez
                let dx = x - mx, dy = y - my, dz = z - mz
                if dx * dx + dy * dy + dz * dz <= len2 * 4.0 {
                    out[0] = x; out[1] = y; out[2] = z
                    return error(x, y, z)
                }
            }
            var best = Double.greatestFiniteMagnitude
            for k in 0...2 {
                let t = Double(k) / 2.0
                let x = Double(pos[3 * a]) + Double(pos[3 * b] - pos[3 * a]) * t
                let y = Double(pos[3 * a + 1]) + Double(pos[3 * b + 1] - pos[3 * a + 1]) * t
                let z = Double(pos[3 * a + 2]) + Double(pos[3 * b + 2] - pos[3 * a + 2]) * t
                let e = error(x, y, z)
                if e < best { best = e; out[0] = x; out[1] = y; out[2] = z }
            }
            return best
        }

        func flips(_ v: Int, _ other: Int, _ px: Double, _ py: Double, _ pz: Double) -> Bool {
            for t in vtris[v] {
                if !alive[t] { continue }
                let a = tris[3 * t], b = tris[3 * t + 1], c = tris[3 * t + 2]
                if a == other || b == other || c == other { continue }
                normal(t)
                let n0x = tmpN[0], n0y = tmpN[1], n0z = tmpN[2]
                func cx(_ i: Int) -> Double { i == v ? px : Double(pos[3 * i]) }
                func cy(_ i: Int) -> Double { i == v ? py : Double(pos[3 * i + 1]) }
                func cz(_ i: Int) -> Double { i == v ? pz : Double(pos[3 * i + 2]) }
                let e1x = cx(b) - cx(a), e1y = cy(b) - cy(a), e1z = cz(b) - cz(a)
                let e2x = cx(c) - cx(a), e2y = cy(c) - cy(a), e2z = cz(c) - cz(a)
                let nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x
                let l = (nx * nx + ny * ny + nz * nz).squareRoot()
                if l < 1e-20 { return true }
                if (nx * n0x + ny * n0y + nz * n0z) / l < 0.2 { return true }
            }
            return false
        }

        func run(_ target: Int) -> TriMesh {
            var heap = EdgeHeap()
            var out = [Double](repeating: 0, count: 3)
            var seen = Set<Int64>()
            for t in 0..<nt { for c in 0..<3 {
                let a = tris[3 * t + c], b = tris[3 * t + (c + 1) % 3]
                if !seen.insert(MeshSimplifier.edgeKey(a, b)).inserted { continue }
                heap.push(cost(a, b, &out), a, b, version[a], version[b])
            } }
            while liveTris > target && heap.size > 0 {
                let e = heap.pop()
                let a = e.a, b = e.b
                if !vAlive[a] || !vAlive[b] || version[a] != e.va || version[b] != e.vb { continue }
                let c = cost(a, b, &out)
                if flips(a, b, out[0], out[1], out[2]) || flips(b, a, out[0], out[1], out[2]) { continue }
                pos[3 * a] = Float(out[0]); pos[3 * a + 1] = Float(out[1]); pos[3 * a + 2] = Float(out[2])
                for i in 0..<10 { q[a * 10 + i] += q[b * 10 + i] }
                for t in vtris[b] {
                    if !alive[t] { continue }
                    let hasA = tris[3 * t] == a || tris[3 * t + 1] == a || tris[3 * t + 2] == a
                    if hasA { alive[t] = false; liveTris -= 1; continue }
                    for k in 0..<3 where tris[3 * t + k] == b { tris[3 * t + k] = a }
                    vtris[a].append(t)
                }
                vAlive[b] = false
                vtris[b].removeAll()
                vtris[a].removeAll { !alive[$0] }
                version[a] += 1
                var nbOrder: [Int] = [], nbSeen = Set<Int>()
                for t in vtris[a] { for k in 0..<3 { let v = tris[3 * t + k]; if v != a && nbSeen.insert(v).inserted { nbOrder.append(v) } } }
                for nb in javaHashOrder(nbOrder) { heap.push(cost(a, nb, &out), a, nb, version[a], version[nb]) }
                if c.isNaN { break }
            }
            var keep: [Int] = []
            for t in 0..<nt where alive[t] { keep.append(t) }
            var outTris = [Int](repeating: 0, count: keep.count * 3)
            var outUv: [Float]? = uv != nil ? [Float](repeating: 0, count: keep.count * 6) : nil
            for (i, t) in keep.enumerated() {
                for k in 0..<3 {
                    outTris[3 * i + k] = tris[3 * t + k]
                    if let uv { outUv![6 * i + 2 * k] = uv[6 * t + 2 * k]; outUv![6 * i + 2 * k + 1] = uv[6 * t + 2 * k + 1] }
                }
            }
            return MeshSimplifier.rebuild(pos, outTris, outUv)
        }
    }

    private struct EdgeEntry { let cost: Double, a: Int, b: Int, va: Int, vb: Int }

    /// En küçük maliyet önde (ikili yığın; Android ile aynı yer değiştirme kuralları).
    private struct EdgeHeap {
        private var items: [EdgeEntry] = []
        var size: Int { items.count }

        mutating func push(_ cost: Double, _ a: Int, _ b: Int, _ va: Int, _ vb: Int) {
            let e = EdgeEntry(cost: cost, a: a, b: b, va: va, vb: vb)
            items.append(e)
            var i = items.count - 1
            while i > 0 {
                let p = (i - 1) / 2
                if items[p].cost <= cost { break }
                items[i] = items[p]; i = p
            }
            items[i] = e
        }

        mutating func pop() -> EdgeEntry {
            let top = items[0]
            let last = items.removeLast()
            let size = items.count
            if size > 0 {
                var i = 0
                while true {
                    let l = 2 * i + 1, r = l + 1
                    if l >= size { break }
                    let c = r < size && items[r].cost < items[l].cost ? r : l
                    if items[c].cost >= last.cost { break }
                    items[i] = items[c]; i = c
                }
                items[i] = last
            }
            return top
        }
    }
}
