import Foundation

/// v45 basit 3B molekül yerleşimi (Android `chem/Embed3D.kt`; top-çubuk model, VSEPR).
enum Embed3D {
    struct V {
        let x: Double, y: Double, z: Double
        init(_ x: Double, _ y: Double, _ z: Double) { self.x = x; self.y = y; self.z = z }
        static func + (a: V, o: V) -> V { V(a.x + o.x, a.y + o.y, a.z + o.z) }
        static func - (a: V, o: V) -> V { V(a.x - o.x, a.y - o.y, a.z - o.z) }
        static func * (a: V, k: Double) -> V { V(a.x * k, a.y * k, a.z * k) }
        func dot(_ o: V) -> Double { x * o.x + y * o.y + z * o.z }
        func cross(_ o: V) -> V { V(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x) }
        func len() -> Double { dot(self).squareRoot() }
        func norm() -> V { let l = len(); return l < 1e-12 ? V(1, 0, 0) : self * (1 / l) }
    }

    static func bondLength(_ m: Molecule, _ a: Int, _ b: Int, _ order: Int) -> Double {
        let ra = Elements[m.atoms[a].el]?.radius ?? 0.75
        let rb = Elements[m.atoms[b].el]?.radius ?? 0.75
        let k = order == 2 ? 0.87 : order == 3 ? 0.78 : 1.0
        return (ra + rb) * k
    }

    private static func perp(_ v: V) -> V {
        let t = abs(v.x) < 0.9 ? V(1, 0, 0) : V(0, 1, 0)
        return v.cross(t).norm()
    }

    private static func rotate(_ v: V, _ axis: V, _ ang: Double) -> V {
        let k = axis.norm()
        return v * cos(ang) + k.cross(v) * sin(ang) + k * (k.dot(v) * (1 - cos(ang)))
    }

    private static func angleFor(_ sn: Int, _ lp: Int) -> Double {
        switch sn {
        case 2: return 180
        case 3: return lp > 0 ? 117 : 120
        case 4: return 109.47 - 2.5 * Double(lp)
        default: return 90
        }
    }

    private static func newDirs(_ e: [V], _ sn: Int, _ lp: Int, _ k: Int, _ ref: V?) -> [V] {
        if k == 0 { return [] }
        let PI = Double.pi
        let ang = angleFor(sn, lp) * PI / 180
        if e.isEmpty {
            let dirs: [V]
            switch sn {
            case 1: dirs = [V(1, 0, 0)]
            case 2: dirs = [V(1, 0, 0), V(-1, 0, 0)]
            case 3: dirs = k == 2 && lp == 1 ? bent(ang) : (0..<3).map { V(cos(PI / 2 + 2 * PI * Double($0) / 3), sin(PI / 2 + 2 * PI * Double($0) / 3), 0) }
            case 4: dirs = k == 2 ? bent(ang) : k == 3 ? pyramid(ang) : tetra()
            case 5: dirs = [V(0, 1, 0), V(0, -1, 0)] + (0..<3).map { V(cos(2 * PI * Double($0) / 3), 0, sin(2 * PI * Double($0) / 3)) }
            default: dirs = [V(1, 0, 0), V(-1, 0, 0), V(0, 1, 0), V(0, -1, 0), V(0, 0, 1), V(0, 0, -1)]
            }
            return Array(dirs.prefix(k))
        }
        if e.count == 1 {
            let d0 = e[0]
            if sn <= 2 { return Array([d0 * -1].prefix(k)) }
            var u = perp(d0)
            if let r = ref { let p = r - d0 * r.dot(d0); if p.len() > 1e-6 { u = p.norm() } }
            let w = d0.cross(u)
            let count = max(sn - 1, k)
            return Array((0..<count).map { j -> V in
                let phi = PI + 2 * PI * Double(j) / Double(count)
                let radial = u * cos(phi) + w * sin(phi)
                return (d0 * cos(ang) + radial * sin(ang)).norm()
            }.prefix(k))
        }
        let s = e.reduce(V(0, 0, 0), +)
        let neg = s * -1
        let out = neg.len() < 1e-6 ? perp(e[0]) : neg.norm()
        if k == 1 { return [out] }
        let c = e[0].cross(e[1])
        let n = c.len() < 1e-6 ? perp(e[0]) : c.norm()
        if sn >= 4 {
            let half = 54.7 * PI / 180
            return (0..<k).map { j in let sg = j % 2 == 0 ? 1.0 : -1.0; return (out * cos(half) + n * (sg * sin(half))).norm() }
        }
        return (0..<k).map { j in rotate(out, n, (Double(j) - Double(k - 1) / 2) * 0.6) }
    }

    private static func bent(_ ang: Double) -> [V] { [V(sin(ang / 2), -cos(ang / 2), 0), V(-sin(ang / 2), -cos(ang / 2), 0)] }

    private static func pyramid(_ ang: Double) -> [V] {
        let c = cos(ang)
        let cb2 = (2 * c + 1) / 3
        let b = acos(cb2.coerceIn(0, 1).squareRoot())
        return (0..<3).map { j in let p = 2 * Double.pi * Double(j) / 3; return V(sin(b) * cos(p), -cos(b), sin(b) * sin(p)) }
    }

    private static func tetra() -> [V] {
        let k = 1 / 3.0.squareRoot()
        return [V(k, k, k), V(-k, -k, k), V(-k, k, -k), V(k, -k, -k)]
    }

    /// m açık H'li olmalı.
    static func embed(_ m: Molecule) -> [V] {
        let n = m.atoms.count
        var pos = [V?](repeating: nil, count: n)
        let rings = m.rings()
        if !rings.isEmpty {
            let ringAtoms = Set(rings.flatMap { $0 })
            let (sub, keep) = m.collapsedHIndices()
            let p2 = Layout2D.layout(sub)
            for (si, orig) in keep.enumerated() where ringAtoms.contains(orig) { pos[orig] = V(p2[si].x * 1.4, p2[si].y * 1.4, 0) }
        }
        var shift = 0.0
        for comp in m.components() {
            // Kotlin maxByOrNull: eşitlikte ilk eleman
            let seedFixed = comp.first { pos[$0] != nil } ?? comp.reduce(comp[0]) { m.degree($1) > m.degree($0) ? $1 : $0 }
            if pos[seedFixed] == nil { pos[seedFixed] = V(shift, 0, 0) }
            var queue = comp.filter { pos[$0] != nil }
            var head = 0
            var done = Set<Int>()
            while head < queue.count {
                let i = queue[head]; head += 1
                if !done.insert(i).inserted { continue }
                let p = pos[i]!
                let nbs = m.neighbors(i)
                let placed = nbs.filter { pos[$0] != nil }
                let todo = nbs.filter { pos[$0] == nil }.enumerated()
                    .sorted { ((m.atoms[$0.element].el == "H" ? 1 : 0), $0.offset) < ((m.atoms[$1.element].el == "H" ? 1 : 0), $1.offset) }.map(\.element)
                if todo.isEmpty { continue }
                let lp = m.lonePairs(i)
                let bs = m.bondsOf(i)
                let lin = bs.contains { $0.order == 3 } || bs.filter { $0.order == 2 }.count >= 2
                let sn = lin && nbs.count == 2 ? 2 : nbs.count + lp
                let e = placed.map { (pos[$0]! - p).norm() }
                var ref: V? = nil
                if let par = placed.first, let g = m.neighbors(par).first(where: { $0 != i && pos[$0] != nil }) { ref = pos[g]! - pos[par]! }
                let dirs = newDirs(e, sn, lp, todo.count, ref)
                for (k, c) in todo.enumerated() {
                    let d = k < dirs.count ? dirs[k] : perp(dirs.last ?? V(1, 0, 0))
                    let order = m.bondBetween(i, c)?.order ?? 1
                    pos[c] = p + d * bondLength(m, i, c, order)
                    queue.append(c)
                }
            }
            for i in comp where pos[i] == nil { pos[i] = V(shift, 0, 0) }
            shift = comp.map { pos[$0]!.x }.max()! + 3
        }
        return orient(m, pos.map { $0! })
    }

    /// Ana eksenlere döndürme (Jacobi özdeğer); kök atom yukarıda kalır.
    private static func orient(_ m: Molecule, _ p: [V]) -> [V] {
        if p.count < 2 { return p }
        let c = p.reduce(V(0, 0, 0), +) * (1.0 / Double(p.count))
        let q = p.map { $0 - c }
        var a = [[Double]](repeating: [0, 0, 0], count: 3)
        for v in q {
            let r = [v.x, v.y, v.z]
            for i in 0...2 { for j in 0...2 { a[i][j] += r[i] * r[j] } }
        }
        var vec = (0..<3).map { i in (0..<3).map { j in i == j ? 1.0 : 0.0 } }
        for _ in 0..<50 {
            for pI in 0...1 { for qI in (pI + 1)...2 {
                if abs(a[pI][qI]) < 1e-12 { continue }
                let th = 0.5 * atan2(2 * a[pI][qI], a[qI][qI] - a[pI][pI])
                let cs = cos(th), sn = sin(th)
                for k in 0...2 { let akp = a[k][pI], akq = a[k][qI]; a[k][pI] = cs * akp - sn * akq; a[k][qI] = sn * akp + cs * akq }
                for k in 0...2 { let apk = a[pI][k], aqk = a[qI][k]; a[pI][k] = cs * apk - sn * aqk; a[qI][k] = sn * apk + cs * aqk }
                for k in 0...2 { let vkp = vec[k][pI], vkq = vec[k][qI]; vec[k][pI] = cs * vkp - sn * vkq; vec[k][qI] = sn * vkp + cs * vkq }
            } }
        }
        let diag: [Double] = [a[0][0], a[1][1], a[2][2]]
        let order: [Int] = [0, 1, 2].sorted { (l: Int, r: Int) -> Bool in diag[l] != diag[r] ? diag[l] > diag[r] : l < r }
        let ax = order.map { j in V(vec[0][j], vec[1][j], vec[2][j]).norm() }
        let e1 = ax[0]
        var e2 = ax[1]
        var e3 = e1.cross(e2)
        var out = q.map { V($0.dot(e1), $0.dot(e2), $0.dot(e3)) }
        var root = 0, best = Int.min
        for i in p.indices { let s = m.degree(i) * 10 + (m.atoms[i].el == "H" ? 0 : 1); if s > best { best = s; root = i } }
        if out[root].y < 0 { e2 = e2 * -1; e3 = e1.cross(e2); out = q.map { V($0.dot(e1), $0.dot(e2), $0.dot(e3)) } }
        return out
    }

    static func view(_ p: [V], _ yaw: Double, _ pitch: Double) -> [V] {
        let cy = cos(yaw * .pi / 180), sy = sin(yaw * .pi / 180)
        let cp = cos(pitch * .pi / 180), sp = sin(pitch * .pi / 180)
        return p.map { v in
            let x1 = v.x * cy + v.z * sy, z1 = -v.x * sy + v.z * cy
            let y2 = v.y * cp - z1 * sp, z2 = v.y * sp + z1 * cp
            return V(x1, y2, z2)
        }
    }

    static func planar(_ p: [V]) -> Bool { p.allSatisfy { abs($0.z) < 0.15 } }
}

extension Molecule {
    /// H atomlarını katlayan alt molekül ve ağır atomların özgün indeksleri.
    func collapsedHIndices() -> (Molecule, [Int]) {
        let keep = atoms.indices.filter { atoms[$0].el != "H" || neighbors($0).allSatisfy { atoms[$0].el == "H" } }
        var map: [Int: Int] = [:]
        let sub = Molecule()
        for i in keep { map[i] = sub.add(atoms[i].copy()) }
        for b in bonds {
            let a = map[b.a], c = map[b.b]
            if let a, let c { sub.bond(a, c, b.order) }
            else if let a { sub.atoms[a].h += 1 } else if let c { sub.atoms[c].h += 1 }
        }
        return (sub, keep)
    }
}
