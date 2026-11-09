import Foundation

/// v45 2B molekül yerleşimi (Android `chem/Layout2D.kt`; bağ boyu 1, y yukarı).
enum Layout2D {
    final class Pt { var x: Double; var y: Double; init(_ x: Double, _ y: Double) { self.x = x; self.y = y } }

    static func layout(_ m: Molecule, crossH: Bool = false) -> [Pt] {
        let n = m.atoms.count
        var pos = [Pt?](repeating: nil, count: n)
        let comps = m.components()
        var offset = 0.0
        let rings = m.rings()
        for comp in comps.enumerated().sorted(by: { ($0.element.min() ?? 0, $0.offset) < ($1.element.min() ?? 0, $1.offset) }).map(\.element) {
            placeComponent(m, comp, &pos, rings, crossH)
            let xs = comp.map { pos[$0]!.x }, ys = comp.map { pos[$0]!.y }
            let minX = xs.min()!, maxX = xs.max()!
            let cy = (ys.min()! + ys.max()!) / 2
            for i in comp { pos[i]!.x += offset - minX; pos[i]!.y -= cy }
            offset += (maxX - minX) + 1.6
        }
        let out = pos.map { $0! }
        relax(m, out)
        return out
    }

    private static func linear(_ m: Molecule, _ i: Int) -> Bool {
        let bs = m.bondsOf(i)
        if bs.count + m.atoms[i].h != 2 { return false }
        return bs.contains { $0.order == 3 } || bs.filter { $0.order == 2 }.count == 2
    }

    private static func placeComponent(_ m: Molecule, _ comp: [Int], _ pos: inout [Pt?], _ allRings: [[Int]], _ crossH: Bool) {
        let inComp = Set(comp)
        let rings = allRings.filter { r in r.allSatisfy { inComp.contains($0) } }
        var ringOf: [Int: [[Int]]] = [:]
        for r in rings { for a in r { ringOf[a, default: []].append(r) } }
        var heavy = comp.filter { m.atoms[$0].el != "H" }
        if heavy.isEmpty { heavy = comp }
        let heavySet = Set(heavy)
        let root: Int
        if !rings.isEmpty && heavy.count <= Set(rings.flatMap { $0 }).count + 1 { root = rings[0][0] }
        else if heavy.count == 1 { root = heavy[0] }
        else { root = farthest(m, farthest(m, heavy[0], heavySet), heavySet) }
        let mainPath = heavy.count > 1 ? pathTo(m, root, farthest(m, root, heavySet), heavySet) : [root]
        let mainSet = Set(mainPath)
        var turn: [Int: Int] = [:]
        var placedRings: [[Int]] = []
        var placedSet = Set<[Int]>()
        var ringCenter: [[Int]: Pt] = [:]

        func placeRing(_ r: [Int], _ entry: Int, _ outward: Double?) {
            let k = r.count
            let rad = 1.0 / (2 * sin(Double.pi / Double(k)))
            let placedIdx = r.indices.filter { pos[r[$0]] != nil }
            let ordered: [Int]
            let center: Pt
            if placedIdx.count >= 2 {
                var i0 = -1
                for j in r.indices where pos[r[j]] != nil && pos[r[(j + 1) % k]] != nil { i0 = j; break }
                if i0 < 0 { i0 = placedIdx[0] }
                let a = pos[r[i0]]!, b = pos[r[(i0 + 1) % k]]!
                let mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2
                let nx = -(b.y - a.y), ny = b.x - a.x
                let nl = hypot(nx, ny)
                let apo = rad * cos(Double.pi / Double(k))
                let old = ringOf[r[i0]]?.first(where: { $0 != r && placedSet.contains($0) }).flatMap { ringCenter[$0] }
                var sgn = 1.0
                if let old, (old.x - mx) * nx + (old.y - my) * ny > 0 { sgn = -1 }
                center = Pt(mx + sgn * nx / nl * apo, my + sgn * ny / nl * apo)
                ordered = (0..<k).map { r[(i0 + $0) % k] }
            } else {
                let e = pos[entry]!
                let ang = outward ?? (Double.pi / 2)
                center = Pt(e.x + rad * cos(ang), e.y + rad * sin(ang))
                let ei = r.firstIndex(of: entry) ?? 0
                ordered = (0..<k).map { r[(ei + $0) % k] }
            }
            let a0 = atan2(pos[ordered[0]]!.y - center.y, pos[ordered[0]]!.x - center.x)
            var sense = 1.0
            if let p1 = pos[ordered[1]] {
                var d = atan2(p1.y - center.y, p1.x - center.x) - a0
                while d > .pi { d -= 2 * .pi }
                while d < -.pi { d += 2 * .pi }
                sense = d >= 0 ? 1 : -1
            }
            for j in 1..<k where pos[ordered[j]] == nil {
                let a = a0 + sense * 2 * Double.pi * Double(j) / Double(k)
                pos[ordered[j]] = Pt(center.x + rad * cos(a), center.y + rad * sin(a))
            }
            if placedSet.insert(r).inserted { placedRings.append(r) }
            ringCenter[r] = center
        }

        func placeRingsAt(_ i: Int, _ outward: Double?) {
            guard let rs = ringOf[i] else { return }
            for r in rs where !placedSet.contains(r) {
                placeRing(r, i, outward)
                var changed = true
                while changed {
                    changed = false
                    for r2 in rings where !placedSet.contains(r2) && r2.filter({ pos[$0] != nil }).count >= 2 {
                        placeRing(r2, r2.first { pos[$0] != nil }!, nil); changed = true
                    }
                }
            }
        }

        pos[root] = Pt(0, 0)
        if let r = ringOf[root]?.first {
            let rad = 1.0 / (2 * sin(Double.pi / Double(r.count)))
            pos[root] = Pt(0, rad)
            placeRing(r, root, -Double.pi / 2)
            placeRingsAt(root, nil)
        }
        var queue: [Int] = [root]
        var head = 0
        var done = Set<Int>()
        for r in placedRings { for a in r where a != root { queue.append(a) } }
        while head < queue.count {
            let i = queue[head]; head += 1
            if !done.insert(i).inserted { continue }
            let p = pos[i]!
            let nbs = m.neighbors(i).filter { inComp.contains($0) }
            let placed = nbs.filter { pos[$0] != nil }
            let todo = nbs.filter { pos[$0] == nil }.enumerated().sorted { l, r in
                let kl = (mainSet.contains(l.element) ? 0 : 1, m.atoms[l.element].el == "H" ? 1 : 0, -subtreeSize(m, l.element, i))
                let kr = (mainSet.contains(r.element) ? 0 : 1, m.atoms[r.element].el == "H" ? 1 : 0, -subtreeSize(m, r.element, i))
                return kl != kr ? kl < kr : l.offset < r.offset
            }.map(\.element)
            if todo.isEmpty { continue }
            let dirs = placed.map { atan2(pos[$0]!.y - p.y, pos[$0]!.x - p.x) }
            let PI = Double.pi
            let angles: [Double]
            if dirs.isEmpty {
                angles = rootAngles(m, i, todo.count)
            } else if dirs.count == 1 {
                let back = dirs[0]
                let t = Double(turn[i] ?? 1)
                if linear(m, i) { angles = [back + PI] }
                else if todo.count == 1 { angles = [back + PI - t * PI / 3] }
                else if todo.count == 2 {
                    if crossH && todo.filter({ m.atoms[$0].el == "H" }).count == 2 && m.bondsOf(i).allSatisfy({ $0.order == 1 }) {
                        angles = [back + PI / 2, back - PI / 2]
                    } else { angles = [back + PI - t * PI / 3, back + PI + t * PI / 3] }
                } else if todo.count == 3 && crossH { angles = [back + PI, back + PI / 2, back - PI / 2] }
                else {
                    let cnt = Double(todo.count + 1)
                    angles = (0..<todo.count).map { (j: Int) -> Double in back + 2 * PI * Double(j + 1) / cnt }
                }
            } else {
                let sx = dirs.reduce(0) { $0 + cos($1) }, sy = dirs.reduce(0) { $0 + sin($1) }
                let out = hypot(sx, sy) < 1e-6 ? dirs[0] + PI / 2 : atan2(-sy, -sx)
                switch todo.count {
                case 1: angles = [out]
                case 2: angles = [out + PI / 5, out - PI / 5]
                default:
                    let mid = Double(todo.count - 1) / 2
                    angles = (0..<todo.count).map { (j: Int) -> Double in out + (Double(j) - mid) * PI / 4 }
                }
            }
            for (k, c) in todo.enumerated() where pos[c] == nil {
                let a = angles[k]
                pos[c] = Pt(p.x + cos(a), p.y + sin(a))
                turn[c] = -(turn[i] ?? 1)
                if ringOf[c] != nil { placeRingsAt(c, a) }
                queue.append(c)
                for r in placedRings { for x in r where !done.contains(x) { queue.append(x) } }
            }
        }
        for i in comp where pos[i] == nil { pos[i] = Pt(0, 0) }
    }

    private static func rootAngles(_ m: Molecule, _ i: Int, _ n: Int) -> [Double] {
        let PI = Double.pi
        let allH = m.neighbors(i).allSatisfy { m.atoms[$0].el == "H" }
        switch n {
        case 1: return [0]
        case 2:
            if linear(m, i) { return [0, PI] }
            if allH { return [-PI / 6, -5 * PI / 6] }
            return [0, 2 * PI / 3]
        case 3: return allH && m.lonePairs(i) > 0 ? [-PI / 2, PI / 6, 5 * PI / 6] : [0, 2 * PI / 3, 4 * PI / 3]
        case 4: return [0, PI / 2, PI, 3 * PI / 2]
        default: return (0..<n).map { 2 * PI * Double($0) / Double(n) }
        }
    }

    private static func subtreeSize(_ m: Molecule, _ start: Int, _ from: Int) -> Int {
        var seen: Set<Int> = [from, start]
        var st = [start]
        while let x = st.popLast() { for y in m.neighbors(x) where seen.insert(y).inserted { st.append(y) } }
        return seen.count - 1
    }

    private static func farthest(_ m: Molecule, _ s: Int, _ allowed: Set<Int>) -> Int {
        var d: [Int: Int] = [s: 0]
        var q = [s]
        var head = 0
        var best = s
        while head < q.count {
            let x = q[head]; head += 1
            if d[x]! > d[best]! { best = x }
            for y in m.neighbors(x) where allowed.contains(y) && d[y] == nil { d[y] = d[x]! + 1; q.append(y) }
        }
        return best
    }

    private static func pathTo(_ m: Molecule, _ s: Int, _ t: Int, _ allowed: Set<Int>) -> [Int] {
        var prev: [Int: Int] = [s: s]
        var q = [s]
        var head = 0
        while head < q.count {
            let x = q[head]; head += 1
            if x == t { break }
            for y in m.neighbors(x) where allowed.contains(y) && prev[y] == nil { prev[y] = x; q.append(y) }
        }
        var out: [Int] = []
        var c = t
        while c != s { out.append(c); guard let p = prev[c] else { return [s] }; c = p }
        out.append(s)
        return out.reversed()
    }

    static func minNonBonded(_ m: Molecule, _ p: [Pt]) -> Double {
        var best = Double.greatestFiniteMagnitude
        for i in p.indices { for j in (i + 1)..<max(p.count, i + 1) where m.bondBetween(i, j) == nil {
            best = min(best, hypot(p[i].x - p[j].x, p[i].y - p[j].y))
        } }
        return best
    }

    private static func relax(_ m: Molecule, _ p: [Pt]) {
        if p.count < 3 || minNonBonded(m, p) >= 0.55 { return }
        let n = p.count
        var bonded = [[Bool]](repeating: [Bool](repeating: false, count: n), count: n)
        for b in m.bonds { bonded[b.a][b.b] = true; bonded[b.b][b.a] = true }
        for _ in 0..<300 {
            for i in 0..<n { for j in (i + 1)..<max(n, i + 1) where !bonded[i][j] {
                let dx = p[j].x - p[i].x, dy = p[j].y - p[i].y
                let d = max((dx * dx + dy * dy).squareRoot(), 1e-3)
                if d >= 1.2 { continue }
                let f = (1.2 - d) * 0.15
                p[i].x -= f * dx / d; p[i].y -= f * dy / d
                p[j].x += f * dx / d; p[j].y += f * dy / d
            } }
            for _ in 0..<4 {
                for b in m.bonds {
                    let a = p[b.a], c = p[b.b]
                    let dx = c.x - a.x, dy = c.y - a.y
                    let d = max((dx * dx + dy * dy).squareRoot(), 1e-6)
                    let k = (d - 1) / d * 0.5
                    a.x += dx * k; a.y += dy * k
                    c.x -= dx * k; c.y -= dy * k
                }
            }
        }
    }
}
