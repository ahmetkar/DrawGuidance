import Foundation

/// v16 vektör düzenleme (Android `VectorEdit`): şekil, vektör noktaları ve aralarındaki çizgiler olarak ele alınır.
/// Hepsi saf işlemlerdir: girdi değişmez, sonuç yeni parça listesidir ([ShapeFactory.fromEditable] ile şekle döner).
enum VectorEdit {
    static let VNODE = "vnode"
    static let REGION = "region"
    static let BRUSH_PAINT = "kbrush"
    static let MERGE: Float = 0.6
    private static let ALL_NODES = 16
    static let SIMPLE_NODES = 6
    static let SIMPLE_TOTAL = 16
    private static let COARSE_EPS: Float = 0.06
    private static let BEND_STEPS = 16

    struct Ref: Hashable { let path: Int; let index: Int }

    struct Node { let id: Int; let point: P; let refs: [Ref] }

    struct Edge {
        let id: Int, path: Int, from: Int, to: Int, a: Int, b: Int
        let points: [P]
        var start: P { points[0] }
        var end: P { points[points.count - 1] }
        var curved: Bool { points.count > 2 }

        func middle() -> P {
            let total = length()
            if total < 1e-4 { return (start + end) / 2 }
            var left = total / 2
            for i in 0..<(points.count - 1) {
                let seg = (points[i + 1] - points[i]).distance
                if seg >= left && seg > 0 { return points[i] + (points[i + 1] - points[i]) * (left / seg) }
                left -= seg
            }
            return end
        }

        func length() -> Float {
            var s: Double = 0
            for i in 0..<max(points.count - 1, 0) { s += Double((points[i + 1] - points[i]).distance) }
            return Float(s)
        }

        func angle() -> Float { Float(atan2(Double(end.y - start.y), Double(end.x - start.x)) * 180 / .pi) }
    }

    struct EdgeHit { let edge: Edge; let point: P; let distance: Float; let insertAt: Int }

    final class Graph {
        let paths: [EditablePath]
        let nodeIndices: [[Int]]
        let nodes: [Node]
        let edges: [Edge]
        private let refNode: [Ref: Int]

        init(_ paths: [EditablePath], _ nodeIndices: [[Int]], _ nodes: [Node], _ edges: [Edge]) {
            self.paths = paths; self.nodeIndices = nodeIndices; self.nodes = nodes; self.edges = edges
            var m = [Ref: Int]()
            for n in nodes { for r in n.refs { m[r] = n.id } }
            refNode = m
        }

        func nodeOf(_ ref: Ref) -> Int? { refNode[ref] }

        func nodeAt(_ p: P, _ tolerance: Float, exclude: Int? = nil) -> Node? {
            var best: Node?
            var bestD = tolerance
            for n in nodes where n.id != exclude {
                let d = (n.point - p).distance
                if d <= bestD { bestD = d; best = n }
            }
            return best
        }

        func nodeNear(_ p: P) -> Node? { nodeAt(p, MERGE) }

        func edgeAt(_ p: P, _ tolerance: Float) -> EdgeHit? {
            var best: EdgeHit?
            for e in edges {
                let n = paths[e.path].points.count
                for s in 0..<(e.points.count - 1) {
                    let proj = project(p, e.points[s], e.points[s + 1])
                    let d = (proj - p).distance
                    if d <= tolerance && (best == nil || d < best!.distance) {
                        best = EdgeHit(edge: e, point: proj, distance: d, insertAt: (e.from + s) % n + 1)
                    }
                }
            }
            return best
        }

        func edgeBetween(_ a: P, _ b: P) -> Edge? {
            edges.first { (near($0.start, a) && near($0.end, b)) || (near($0.start, b) && near($0.end, a)) }
        }

        func degree(_ node: Int) -> Int { edges.filter { $0.a == node || $0.b == node }.count }

        func isFree(_ node: Int) -> Bool {
            guard nodes.indices.contains(node) else { return false }
            return nodes[node].refs.allSatisfy { paths[$0.path].tag == VNODE }
        }
    }

    // MARK: Görünüm

    static func graph(_ stroke: Stroke, coarse: Bool = false) -> Graph { graph(ShapeFactory.subPaths(stroke), diagonal: diagonal(stroke), coarse: coarse) }

    static func graph(_ raw: [EditablePath], diagonal: Float = 0, coarse: Bool = false) -> Graph {
        let eps = max(0.8, diagonal * (coarse ? COARSE_EPS : 0.02))
        let parts = coarse ? max(raw.filter { editable($0) && $0.tag != VNODE && $0.points.count >= 2 }.count, 1) : 1
        let limit = coarse ? (SIMPLE_TOTAL / parts).coerceIn(2, SIMPLE_NODES) : 0
        var paths: [EditablePath] = []
        var indices: [[Int]] = []
        for p in raw {
            var ns = nodeIndices(p, eps: eps, limit: limit > 0 && p.closed ? max(3, limit) : limit)
            var q = p
            if !ns.isEmpty && editable(p) {
                if p.closed && ns[0] != 0 {
                    let r = ns[0]
                    q.points = Array(p.points[r...]) + Array(p.points[..<r])
                    ns = ns.map { $0 - r }
                }
                q.nodes = ns
            }
            paths.append(q)
            indices.append(ns)
        }
        var points: [P] = []
        var refs: [[Ref]] = []
        var grid = [Int64: [Int]]()
        func key(_ cx: Int64, _ cy: Int64) -> Int64 { (cx << 32) ^ (cy & 0xffffffff) }
        for (i, p) in paths.enumerated() {
            for k in indices[i] {
                let pt = p.points[k]
                var found = -1
                let cx = Int64(floor(Double(pt.x))), cy = Int64(floor(Double(pt.y)))
                outer: for dx in Int64(-1)...1 {
                    for dy in Int64(-1)...1 {
                        if let ids = grid[key(cx + dx, cy + dy)] {
                            for id in ids where found < 0 && near(points[id], pt) { found = id }
                        }
                        if found >= 0 { break outer }
                    }
                }
                if found < 0 {
                    found = points.count
                    points.append(pt)
                    refs.append([])
                    grid[key(cx, cy), default: []].append(found)
                }
                refs[found].append(Ref(path: i, index: k))
            }
        }
        let nodes = points.indices.map { Node(id: $0, point: points[$0], refs: refs[$0]) }
        var refNode = [Ref: Int]()
        for n in nodes { for r in n.refs { refNode[r] = n.id } }
        var edges: [Edge] = []
        for (i, p) in paths.enumerated() {
            let ns = indices[i]
            if p.tag == VNODE || ns.isEmpty || p.points.count < 2 { continue }
            func add(_ from: Int, _ to: Int) {
                guard let a = refNode[Ref(path: i, index: from)], let b = refNode[Ref(path: i, index: to)] else { return }
                edges.append(Edge(id: edges.count, path: i, from: from, to: to, a: a, b: b, points: segment(p.points, from, to, p.closed)))
            }
            for k in 0..<(ns.count - 1) { add(ns[k], ns[k + 1]) }
            if p.closed { add(ns[ns.count - 1], ns[0]) }
        }
        return Graph(paths, indices, nodes, edges)
    }

    static func nodeIndices(_ path: EditablePath, eps: Float = 0.8, limit: Int = 0) -> [Int] {
        let n = path.points.count
        if n == 0 || !editable(path) { return [] }
        if path.tag == VNODE || n == 1 { return [0] }
        if let explicit = path.nodes {
            var valid = Array(Set(explicit.filter { $0 >= 0 && $0 < n })).sorted()
            if !path.closed {
                if valid.first != 0 { valid.insert(0, at: 0) }
                if valid.last != n - 1 { valid.append(n - 1) }
            } else if valid.isEmpty { valid.append(0) }
            return valid
        }
        return derived(path.points, path.closed, eps, limit)
    }

    private static func editable(_ p: EditablePath) -> Bool { p.label == nil && p.tag != "frame" && p.tag != REGION && p.tag != BRUSH_PAINT }

    private static func derived(_ points: [P], _ closed: Bool, _ eps: Float, _ limit: Int) -> [Int] {
        let n = points.count
        if n <= (limit > 0 ? limit : ALL_NODES) { return Array(0..<n) }
        return limit > 0 ? rdpLimited(points, closed, eps, limit) : rdp(points, closed, eps)
    }

    private static func rdp(_ points: [P], _ closed: Bool, _ eps: Float) -> [Int] {
        let n = points.count
        if !closed {
            var keep = [Bool](repeating: false, count: n)
            keep[0] = true; keep[n - 1] = true
            simplify(points, 0, n - 1, eps, &keep)
            return keep.indices.filter { keep[$0] }
        }
        let ext = points + [points[0]]
        var far = n / 2, farD: Float = -1
        for i in 1..<n { let d = (points[i] - points[0]).distance; if d > farD { farD = d; far = i } }
        var keep = [Bool](repeating: false, count: n + 1)
        keep[0] = true; keep[far] = true; keep[n] = true
        simplify(ext, 0, far, eps, &keep)
        simplify(ext, far, n, eps, &keep)
        return (0..<n).filter { keep[$0] }
    }

    private static func simplify(_ points: [P], _ a: Int, _ b: Int, _ eps: Float, _ keep: inout [Bool]) {
        var stack = [(a, b)]
        while let (s, e) = stack.popLast() {
            if e <= s + 1 { continue }
            var maxD: Float = -1
            var idx = -1
            for i in (s + 1)..<e {
                let d = (project(points[i], points[s], points[e]) - points[i]).distance
                if d > maxD { maxD = d; idx = i }
            }
            if idx >= 0 && maxD > eps {
                keep[idx] = true
                stack.append((s, idx)); stack.append((idx, e))
            }
        }
    }

    private static func segment(_ points: [P], _ from: Int, _ to: Int, _ closed: Bool) -> [P] {
        if to > from { return Array(points[from...to]) }
        if closed { return Array(points[from...]) + Array(points[...to]) }
        return Array(points[to...from].reversed())
    }

    static func diagonal(_ paths: [EditablePath]) -> Float {
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude, r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for p in paths { for q in p.points { l = min(l, q.x); t = min(t, q.y); r = max(r, q.x); b = max(b, q.y) } }
        return l > r ? 0 : hypotf(r - l, b - t)
    }

    static func diagonal(_ stroke: Stroke) -> Float {
        if stroke.points.isEmpty { return 0 }
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude, r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for q in stroke.points { l = min(l, q.x); t = min(t, q.y); r = max(r, q.x); b = max(b, q.y) }
        return hypotf(r - l, b - t)
    }

    // MARK: İşlemler

    static func moveNodes(_ g: Graph, _ deltas: [Int: P], rigid: (Int, (P) -> P)? = nil) -> [EditablePath] {
        var pts = g.paths.map(\.points)
        for (id, d) in deltas where g.nodes.indices.contains(id) {
            for r in g.nodes[id].refs { pts[r.path][r.index] = g.paths[r.path].points[r.index] + d }
        }
        for e in g.edges {
            let inner = e.points.count - 2
            if inner <= 0 { continue }
            let src = g.paths[e.path].points
            let n = src.count
            if let rigid, rigid.0 == e.id {
                for k in 1...inner { let idx = (e.from + k) % n; pts[e.path][idx] = rigid.1(src[idx]) }
                continue
            }
            let dA = deltas[e.a] ?? .zero, dB = deltas[e.b] ?? .zero
            if dA == .zero && dB == .zero { continue }
            var cum = [Float](repeating: 0, count: e.points.count)
            for k in 1..<e.points.count { cum[k] = cum[k - 1] + (e.points[k] - e.points[k - 1]).distance }
            let total = cum[cum.count - 1]
            for k in 1...inner {
                let t = total > 1e-4 ? cum[k] / total : Float(k) / Float(inner + 1)
                let idx = (e.from + k) % n
                pts[e.path][idx] = src[idx] + dA * (1 - t) + dB * t
            }
        }
        return tidy(g.paths.enumerated().map { i, p in var c = p; c.points = pts[i]; return c })
    }

    static func insertNode(_ g: Graph, _ hit: EdgeHit) -> ([EditablePath], P) {
        let e = hit.edge
        if (hit.point - e.start).distance <= MERGE * 2 { return (g.paths, e.start) }
        if (hit.point - e.end).distance <= MERGE * 2 { return (g.paths, e.end) }
        var p = g.paths[e.path]
        let at = hit.insertAt.coerceIn(1, p.points.count)
        p.points.insert(hit.point, at: at)
        p.nodes = Array(Set(g.nodeIndices[e.path].map { $0 >= at ? $0 + 1 : $0 } + [at])).sorted()
        var out = g.paths
        out[e.path] = p
        return (out, hit.point)
    }

    static func addFreeNode(_ g: Graph, _ at: P) -> [EditablePath] {
        g.nodeNear(at) != nil ? g.paths : g.paths + [EditablePath(points: [at], tag: VNODE, nodes: [0])]
    }

    static func connect(_ g: Graph, _ from: P, _ to: P, style: (EditablePath) -> EditablePath = { $0 }) -> [EditablePath]? {
        if (from - to).distance <= MERGE { return nil }
        let fromNode = g.nodeNear(from), toNode = g.nodeNear(to)
        if let f = fromNode, f.id == toNode?.id { return nil }
        let a = fromNode?.point ?? from, b = toNode?.point ?? to
        if let e = g.edgeBetween(a, b), !e.curved { return nil }
        func openEnd(_ node: Node?) -> Ref? {
            node?.refs.first { r in
                let p = g.paths[r.path]
                return !p.closed && editable(p) && p.tag != VNODE && p.points.count >= 2 && (r.index == 0 || r.index == p.points.count - 1)
            }
        }
        let fe = openEnd(fromNode), te = openEnd(toNode)
        var out = g.paths
        func oriented(_ i: Int, endLast: Bool, _ ref: Ref) -> ([P], [Int]) {
            let p = g.paths[i]
            let n = p.points.count
            if (ref.index == n - 1) == endLast { return (p.points, g.nodeIndices[i]) }
            return (p.points.reversed(), g.nodeIndices[i].map { n - 1 - $0 }.sorted())
        }
        if let fe, let te, fe.path == te.path {
            if g.paths[fe.path].points.count < 3 { return nil }
            out[fe.path].closed = true
        } else if let fe, let te {
            let (pa, na) = oriented(fe.path, endLast: true, fe)
            let (pb, nb) = oriented(te.path, endLast: false, te)
            var merged = g.paths[fe.path]
            merged.points = pa + pb
            merged.nodes = na + nb.map { $0 + pa.count }
            out[fe.path] = merged
            out.remove(at: te.path)
        } else if let fe {
            let (pa, na) = oriented(fe.path, endLast: true, fe)
            out[fe.path].points = pa + [b]
            out[fe.path].nodes = na + [pa.count]
        } else if let te {
            let (pb, nb) = oriented(te.path, endLast: false, te)
            out[te.path].points = [a] + pb
            out[te.path].nodes = [0] + nb.map { $0 + 1 }
        } else {
            out.append(style(EditablePath(points: [a, b], nodes: [0, 1])))
        }
        return tidy(out)
    }

    static func bendEdge(_ g: Graph, _ edge: Edge, _ through: P) -> [EditablePath] {
        let a = edge.start, b = edge.end
        let c = through * 2 - (a + b) / 2
        let inner = (1..<BEND_STEPS).map { k -> P in
            let t = Float(k) / Float(BEND_STEPS), m = 1 - t
            return a * (m * m) + c * (2 * m * t) + b * (t * t)
        }
        return replaceInner(g, edge, inner)
    }

    static func straighten(_ g: Graph, _ edge: Edge) -> [EditablePath] { replaceInner(g, edge, []) }

    static func rotateEdge(_ g: Graph, _ edge: Edge, _ degrees: Float) -> [EditablePath] {
        if edge.a == edge.b || abs(degrees) < 1e-4 { return g.paths }
        let pivot = (edge.start + edge.end) / 2
        let rad = Double(degrees) * .pi / 180
        let c = Float(cos(rad)), s = Float(sin(rad))
        let rot: (P) -> P = { p in
            let x = p.x - pivot.x, y = p.y - pivot.y
            return P(pivot.x + x * c - y * s, pivot.y + x * s + y * c)
        }
        var deltas = [Int: P]()
        deltas[edge.a] = rot(edge.start) - edge.start
        deltas[edge.b] = rot(edge.end) - edge.end
        return moveNodes(g, deltas, rigid: (edge.id, rot))
    }

    static func setAngle(_ g: Graph, _ edge: Edge, _ target: Float) -> [EditablePath] {
        var diff = (target - edge.angle()).truncatingRemainder(dividingBy: 180)
        if diff > 90 { diff -= 180 }
        if diff <= -90 { diff += 180 }
        return rotateEdge(g, edge, diff)
    }

    static func cutNodes(_ g: Graph, _ nodes: Set<Int>) -> ([EditablePath], [EditablePath]) {
        let cut = Set(g.edges.filter { nodes.contains($0.a) || nodes.contains($0.b) }.map(\.id))
        return split(g, cut, nodes)
    }

    static func deleteEdge(_ g: Graph, _ edge: Edge) -> [EditablePath] { split(g, [edge.id], []).0 }

    static func simplifyNodes(_ g: Graph, _ diagonal: Float, limit: Int = SIMPLE_NODES) -> [EditablePath] {
        let eps = max(0.8, diagonal * COARSE_EPS)
        var changed = false
        let out = g.paths.enumerated().map { i, p -> EditablePath in
            let ns = g.nodeIndices[i]
            if ns.count <= limit || !editable(p) || p.tag == VNODE || p.points.count <= limit { return p }
            let shared = ns.filter { k in
                guard let id = g.nodeOf(Ref(path: i, index: k)) else { return false }
                return g.nodes[id].refs.contains { $0.path != i }
            }
            let coarse = Array(Set(rdpLimited(p.points, p.closed, eps, limit) + shared)).sorted()
            if coarse.count >= ns.count { return p }
            changed = true
            var c = p; c.nodes = coarse; return c
        }
        return changed ? out : g.paths
    }

    static func canSimplify(_ g: Graph, limit: Int = SIMPLE_NODES) -> Bool {
        g.paths.indices.contains { i in
            g.nodeIndices[i].count > limit && editable(g.paths[i]) && g.paths[i].tag != VNODE && g.paths[i].points.count > limit
        }
    }

    private static func rdpLimited(_ points: [P], _ closed: Bool, _ eps: Float, _ limit: Int) -> [Int] {
        var e = eps
        var out = rdp(points, closed, e)
        var guardN = 0
        while out.count > limit && guardN < 24 {
            guardN += 1
            e *= 1.5
            out = rdp(points, closed, e)
        }
        return out
    }

    static func drawable(_ paths: [EditablePath]) -> Bool {
        paths.contains { $0.label == nil && $0.tag != VNODE && $0.tag != REGION && $0.tag != BRUSH_PAINT && $0.points.count >= 2 }
    }

    private static func split(_ g: Graph, _ cut: Set<Int>, _ pieceNodes: Set<Int>) -> ([EditablePath], [EditablePath]) {
        var rest: [EditablePath] = [], piece: [EditablePath] = []
        let byPath = Dictionary(grouping: g.edges, by: \.path)
        for (i, p) in g.paths.enumerated() {
            if !editable(p) { rest.append(p); continue }
            if p.tag == VNODE {
                if let id = g.nodeOf(Ref(path: i, index: 0)), pieceNodes.contains(id) { piece.append(p) } else { rest.append(p) }
                continue
            }
            let es = byPath[i] ?? []
            if es.isEmpty { rest.append(p); continue }
            let flags = es.map { cut.contains($0.id) }
            if !flags.contains(true) { rest.append(p); continue }
            if !flags.contains(false) { piece.append(p); continue }
            var order = Array(es.indices)
            if p.closed, let first = es.indices.first(where: { flags[$0] != flags[($0 - 1 + es.count) % es.count] }) {
                order = Array(order[first...]) + Array(order[..<first])
            }
            var k = 0
            while k < order.count {
                let f = flags[order[k]]
                var run: [Int] = []
                while k < order.count && flags[order[k]] == f { run.append(order[k]); k += 1 }
                var pts: [P] = [], ns: [Int] = []
                for (r, ei) in run.enumerated() {
                    let e = es[ei]
                    if r == 0 { ns.append(0); pts += e.points } else { pts += e.points.dropFirst() }
                    ns.append(pts.count - 1)
                }
                var np = p
                np.points = pts; np.closed = false
                var seen = Set<Int>()
                np.nodes = ns.filter { seen.insert($0).inserted }
                if f { piece.append(np) } else { rest.append(np) }
            }
        }
        return (tidy(rest), piece)
    }

    private static func replaceInner(_ g: Graph, _ edge: Edge, _ inner: [P]) -> [EditablePath] {
        var p = g.paths[edge.path]
        let n = p.points.count
        let ns = g.nodeIndices[edge.path]
        let wrap = p.closed && edge.to <= edge.from
        var out = g.paths
        if wrap {
            p.points = Array(p.points[0...edge.from]) + inner
            p.nodes = ns.filter { $0 <= edge.from }
        } else {
            let shift = inner.count - (edge.to - edge.from - 1)
            p.points = Array(p.points[0...edge.from]) + inner + Array(p.points[edge.to..<n])
            p.nodes = ns.map { $0 >= edge.to ? $0 + shift : $0 }
        }
        out[edge.path] = p
        return out
    }

    private static func tidy(_ paths: [EditablePath]) -> [EditablePath] {
        if !paths.contains(where: { $0.tag == VNODE }) { return paths }
        var anchored: [P] = []
        for p in paths where p.tag != VNODE && editable(p) { for k in nodeIndices(p) { anchored.append(p.points[k]) } }
        var seen: [P] = []
        return paths.filter { p in
            if p.tag != VNODE { return true }
            guard let pt = p.points.first else { return false }
            if anchored.contains(where: { near($0, pt) }) || seen.contains(where: { near($0, pt) }) { return false }
            seen.append(pt)
            return true
        }
    }

    // MARK: Tutamaklar

    static func bendHandle(_ edge: Edge) -> P { edge.middle() }

    static func turnHandle(_ edge: Edge, _ distance: Float) -> P {
        let d = edge.end - edge.start
        let len = d.distance
        let dir = len < 1e-4 ? P(1, 0) : d / len
        return edge.end + dir * distance
    }

    static func near(_ a: P, _ b: P) -> Bool { abs(a.x - b.x) <= MERGE && abs(a.y - b.y) <= MERGE }

    private static func project(_ p: P, _ a: P, _ b: P) -> P {
        let vx = b.x - a.x, vy = b.y - a.y
        let len2 = vx * vx + vy * vy
        if len2 < 1e-9 { return a }
        let t = (((p.x - a.x) * vx + (p.y - a.y) * vy) / len2).coerceIn(0, 1)
        return P(a.x + vx * t, a.y + vy * t)
    }
}
