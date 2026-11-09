import Foundation

/// Vektör şekil düzenleme yardımcıları (Android `ShapeEditor`): nokta ekleme/taşıma, parçalama, bağlama, parça seçimi,
/// parça dönüşümleri, ayırma ve boyama. Hepsi saf işlemlerdir.
enum ShapeEditor {
    private static let SHOW_ALL_VERTICES = 48
    private static let MAX_HANDLES = 160
    private static let MAX_POINTS = 4000
    private static let MAX_CURVE_POINTS = 160

    struct Handle: Equatable { let point: P; let openEnd: Bool }

    static func insertPoint(_ stroke: Stroke, _ at: P) -> Stroke? {
        let paths = ShapeFactory.subPaths(stroke)
        guard let hit = nearestSegment(paths, at) else { return nil }
        var path = paths[hit.path]
        path.points.insert(hit.point, at: min(hit.segment + 1, path.points.count))
        path.nodes = nil
        return rebuild(stroke, paths, hit.path, [path])
    }

    static func subdivide(_ stroke: Stroke) -> Stroke? {
        if stroke.points.count * 2 > MAX_POINTS { return nil }
        let paths = ShapeFactory.subPaths(stroke).map { path -> EditablePath in
            if path.label != nil || path.points.count < 2 { return path }
            let src = path.points
            var out: [P] = []
            out.reserveCapacity(src.count * 2)
            for i in src.indices {
                out.append(src[i])
                let next: P? = i + 1 < src.count ? src[i + 1] : (path.closed ? src[0] : nil)
                if let next { out.append(P((src[i].x + next.x) / 2, (src[i].y + next.y) / 2)) }
            }
            var c = path; c.points = out; c.nodes = nil; return c
        }
        return ShapeFactory.fromEditable(stroke, paths)
    }

    static func movePoint(_ stroke: Stroke, _ from: P, _ to: P, tolerance: Float) -> Stroke? {
        var paths = ShapeFactory.subPaths(stroke)
        guard let v = nearestVertex(paths, from, tolerance) else { return nil }
        paths[v.path].points[v.index] = to
        return ShapeFactory.fromEditable(stroke, paths)
    }

    static func split(_ stroke: Stroke, _ at: P, gap: Float) -> Stroke? {
        let paths = ShapeFactory.subPaths(stroke)
        guard let hit = nearestSegment(paths, at) else { return nil }
        let path = paths[hit.path]
        let pts = path.points
        let cut = hit.point
        func toward(_ target: P) -> P {
            let d = target - cut
            let len = d.distance
            if len < 1e-3 { return cut }
            return cut + d / len * min(gap, len * 0.45)
        }
        var replacement: [EditablePath] = []
        if path.closed {
            let n = pts.count
            let next = pts[(hit.segment + 1) % n], prev = pts[hit.segment]
            let rotated = (1...n).map { pts[(hit.segment + $0) % n] }
            var c = path; c.points = [toward(next)] + rotated + [toward(prev)]; c.closed = false; c.nodes = nil
            replacement.append(c)
        } else {
            let prev = pts[hit.segment], next = pts[hit.segment + 1]
            let a = Array(pts[0...hit.segment]) + [toward(prev)]
            let b = [toward(next)] + Array(pts[(hit.segment + 1)...])
            if a.count >= 2 { var c = path; c.points = a; c.nodes = nil; replacement.append(c) }
            if b.count >= 2 { var c = path; c.points = b; c.nodes = nil; replacement.append(c) }
        }
        return rebuild(stroke, paths, hit.path, replacement)
    }

    static func connect(_ stroke: Stroke, _ a: P, _ b: P, tolerance: Float) -> Stroke? {
        let paths = ShapeFactory.subPaths(stroke)
        guard let va = nearestVertex(paths, a, tolerance), let vb = nearestVertex(paths, b, tolerance, exclude: va) else { return nil }
        let pa = paths[va.path].points[va.index], pb = paths[vb.path].points[vb.index]
        if va.openEnd && vb.openEnd {
            var mutable = paths
            if va.path == vb.path {
                mutable[va.path].closed = true
                return ShapeFactory.fromEditable(stroke, mutable)
            }
            var first = paths[va.path]
            if va.index == 0 { first.points.reverse() }
            var second = paths[vb.path]
            if vb.index != 0 { second.points.reverse() }
            var merged = first
            merged.points = first.points + second.points
            merged.nodes = nil
            var out: [EditablePath] = []
            for (i, p) in paths.enumerated() where i != va.path && i != vb.path { out.append(p) }
            out.insert(merged, at: min(min(va.path, vb.path), out.count))
            return ShapeFactory.fromEditable(stroke, out)
        }
        return ShapeFactory.fromEditable(stroke, paths + [EditablePath(points: [pa, pb])])
    }

    static func handles(_ stroke: Stroke) -> [Handle] {
        var out: [Handle] = []
        for path in ShapeFactory.subPaths(stroke) {
            if path.label != nil { continue }
            let pts = path.points
            if pts.count < 2 { continue }
            if !path.closed { out.append(Handle(point: pts[0], openEnd: true)); out.append(Handle(point: pts[pts.count - 1], openEnd: true)) }
            let all = pts.count <= SHOW_ALL_VERTICES
            let range = path.closed ? Array(pts.indices) : Array(1..<max(pts.count - 1, 1))
            for i in range {
                let b = pts[i]
                if all { out.append(Handle(point: b, openEnd: false)); continue }
                let a = pts[(i - 1 + pts.count) % pts.count], c = pts[(i + 1) % pts.count]
                let v1 = P(a.x - b.x, a.y - b.y), v2 = P(c.x - b.x, c.y - b.y)
                let l1 = hypotf(v1.x, v1.y), l2 = hypotf(v2.x, v2.y)
                if l1 < 1e-3 || l2 < 1e-3 { continue }
                if (v1.x * v2.x + v1.y * v2.y) / (l1 * l2) > -0.9 { out.append(Handle(point: b, openEnd: false)) }
            }
        }
        return Array(out.prefix(MAX_HANDLES))
    }

    static func distanceToPaths(_ stroke: Stroke, _ at: P) -> Float {
        nearestSegment(ShapeFactory.subPaths(stroke), at)?.distance ?? .greatestFiniteMagnitude
    }

    // MARK: Yardımcılar

    private static func rebuild(_ stroke: Stroke, _ paths: [EditablePath], _ index: Int, _ replacement: [EditablePath]) -> Stroke? {
        var out: [EditablePath] = []
        for (i, p) in paths.enumerated() { if i == index { out += replacement } else { out.append(p) } }
        return ShapeFactory.fromEditable(stroke, out)
    }

    private struct SegmentHit { let path: Int; let segment: Int; let point: P; let distance: Float }

    private static func nearestSegment(_ paths: [EditablePath], _ at: P) -> SegmentHit? {
        var best: SegmentHit?
        for (pi, path) in paths.enumerated() where path.label == nil {
            let pts = path.closed && !path.points.isEmpty ? path.points + [path.points[0]] : path.points
            if pts.count < 2 { continue }
            for i in 0..<(pts.count - 1) {
                let p = project(at, pts[i], pts[i + 1])
                let d = (p - at).distance
                if best == nil || d < best!.distance { best = SegmentHit(path: pi, segment: i, point: p, distance: d) }
            }
        }
        return best
    }

    private struct VertexHit { let path: Int; let index: Int; let openEnd: Bool; let distance: Float }

    private static func nearestVertex(_ paths: [EditablePath], _ at: P, _ tolerance: Float, exclude: VertexHit? = nil) -> VertexHit? {
        var best: VertexHit?
        for (pi, path) in paths.enumerated() where path.label == nil && path.points.count >= 2 {
            for (k, p) in path.points.enumerated() {
                if let ex = exclude, ex.path == pi && ex.index == k { continue }
                let d = (p - at).distance
                if d > tolerance { continue }
                let open = !path.closed && (k == 0 || k == path.points.count - 1)
                let score = d - (open ? tolerance * 0.25 : 0)
                let bestScore = best.map { $0.distance - ($0.openEnd ? tolerance * 0.25 : 0) } ?? .greatestFiniteMagnitude
                if score < bestScore { best = VertexHit(path: pi, index: k, openEnd: open, distance: d) }
            }
        }
        return best
    }

    private static func project(_ p: P, _ a: P, _ b: P) -> P {
        let vx = b.x - a.x, vy = b.y - a.y
        let len2 = vx * vx + vy * vy
        if len2 < 1e-6 { return a }
        let t = (((p.x - a.x) * vx + (p.y - a.y) * vy) / len2).coerceIn(0, 1)
        return P(a.x + vx * t, a.y + vy * t)
    }

    // MARK: v13 parçalar

    static func partAt(_ stroke: Stroke, _ at: P, tolerance: Float) -> Int? {
        let paths = ShapeFactory.subPaths(stroke)
        var best: Int?
        var bestDistance = Float.greatestFiniteMagnitude
        for (i, path) in paths.enumerated() {
            let d: Float
            if let label = path.label {
                guard let p = path.points.first else { continue }
                let size = max(8, path.labelSize)
                let box = FRect(p.x - 2, p.y - 2, p.x + size * Float(max(1, label.count)) * 0.6, p.y + size * 1.2)
                d = box.contains(at) ? 0 : .greatestFiniteMagnitude
            } else {
                d = distanceToPath(path, at)
            }
            if d < bestDistance { bestDistance = d; best = i }
        }
        if let best, bestDistance <= tolerance { return best }
        return regionAt(stroke, at, paths: paths)
    }

    static func regionAt(_ stroke: Stroke, _ at: P) -> Int? { regionAt(stroke, at, paths: ShapeFactory.subPaths(stroke)) }

    private static func regionAt(_ stroke: Stroke, _ at: P, paths: [EditablePath]) -> Int? {
        let width = stroke.shape?.style.strokeWidth ?? 4
        var inside: Int?
        var insideArea = Float.greatestFiniteMagnitude
        for (i, path) in paths.enumerated() where path.label == nil && path.points.count >= 3 {
            if !(path.closed || fillsOpen(path, width)) || !contains(path.points, at) { continue }
            let a = abs(area(path.points))
            if a < insideArea { insideArea = a; inside = i }
        }
        return inside
    }

    static func lineAt(_ stroke: Stroke, _ at: P, tolerance: Float) -> Int? {
        var best: Int?
        var bestDistance = Float.greatestFiniteMagnitude
        for (i, path) in ShapeFactory.subPaths(stroke).enumerated() where path.label == nil {
            let d = distanceToPath(path, at)
            if d < bestDistance { bestDistance = d; best = i }
        }
        return bestDistance <= tolerance ? best : nil
    }

    private static func fillsOpen(_ path: EditablePath, _ width: Float) -> Bool {
        let pts = path.points
        if pts.count < 3 || path.dashed { return false }
        return (pts[0] - pts[pts.count - 1]).distance <= max(14, width * 6)
    }

    private static func distanceToPath(_ path: EditablePath, _ at: P) -> Float {
        let pts = path.closed && path.points.count > 2 ? path.points + [path.points[0]] : path.points
        if pts.isEmpty { return .greatestFiniteMagnitude }
        if pts.count == 1 { return (pts[0] - at).distance }
        var best = Float.greatestFiniteMagnitude
        for i in 0..<(pts.count - 1) { best = min(best, (project(at, pts[i], pts[i + 1]) - at).distance) }
        return best
    }

    private static func contains(_ pts: [P], _ p: P) -> Bool {
        var inside = false
        var j = pts.count - 1
        for i in pts.indices {
            let a = pts[i], b = pts[j]
            if (a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y + 1e-6) + a.x { inside.toggle() }
            j = i
        }
        return inside
    }

    private static func area(_ pts: [P]) -> Float {
        var a: Float = 0
        for i in pts.indices { let p = pts[i], q = pts[(i + 1) % pts.count]; a += p.x * q.y - q.x * p.y }
        return a / 2
    }

    static func partsBounds(_ stroke: Stroke, _ parts: Set<Int>) -> FRect? {
        let paths = ShapeFactory.subPaths(stroke)
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude, r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        var any = false
        for i in parts where paths.indices.contains(i) {
            for p in paths[i].points { any = true; l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
        }
        return any ? FRect(l, t, r, b) : nil
    }

    static func transformParts(_ stroke: Stroke, _ parts: Set<Int>, _ transform: (P) -> P) -> Stroke? {
        if parts.isEmpty { return nil }
        let paths = ShapeFactory.subPaths(stroke).enumerated().map { i, p -> EditablePath in
            guard parts.contains(i) else { return p }
            var c = p; c.points = p.points.map(transform); return c
        }
        return ShapeFactory.fromEditable(stroke, paths)
    }

    static func moveParts(_ stroke: Stroke, _ parts: Set<Int>, _ dx: Float, _ dy: Float) -> Stroke? {
        transformParts(stroke, parts) { P($0.x + dx, $0.y + dy) }
    }

    static func rotateParts(_ stroke: Stroke, _ parts: Set<Int>, _ degrees: Float, pivot: P) -> Stroke? {
        let rad = Double(degrees) * .pi / 180
        let c = Float(cos(rad)), sn = Float(sin(rad))
        return transformParts(stroke, parts) { p in
            let x = p.x - pivot.x, y = p.y - pivot.y
            return P(pivot.x + x * c - y * sn, pivot.y + x * sn + y * c)
        }
    }

    static func addPath(_ stroke: Stroke, _ points: [P]) -> Stroke? {
        if points.count < 2 { return nil }
        return ShapeFactory.fromEditable(stroke, ShapeFactory.subPaths(stroke) + [EditablePath(points: points)])
    }

    static func removeParts(_ stroke: Stroke, _ parts: Set<Int>) -> Stroke? {
        let remaining = ShapeFactory.subPaths(stroke).enumerated().filter { !parts.contains($0.offset) }.map(\.element)
        if !remaining.contains(where: { $0.label == nil }) { return nil }
        return ShapeFactory.fromEditable(stroke, remaining)
    }

    static func separate(_ stroke: Stroke, _ parts: Set<Int>, each: Bool, ids: () -> String) -> [Stroke]? {
        let paths = ShapeFactory.subPaths(stroke)
        let chosen = parts.filter { paths.indices.contains($0) }.sorted()
        let chosenSet = Set(chosen)
        let chosenLines = chosen.filter { paths[$0].label == nil }
        if chosenLines.isEmpty { return nil }
        let rest = paths.indices.filter { !chosenSet.contains($0) }
        let restLines = rest.filter { paths[$0].label == nil }
        if !each && restLines.isEmpty { return nil }
        if each && restLines.isEmpty && chosenLines.count < 2 { return nil }
        func nearest(_ label: Int, _ among: [Int]) -> Int {
            guard let p = paths[label].points.first else { return among[0] }
            return among.min { distanceToPath(paths[$0], p) < distanceToPath(paths[$1], p) } ?? among[0]
        }
        var groups: [[Int]] = []
        if !restLines.isEmpty { groups.append(rest) }
        if each {
            var byLine = [Int: [Int]]()
            for l in chosenLines { byLine[l] = [l] }
            for i in chosen where paths[i].label != nil { byLine[nearest(i, chosenLines), default: []].append(i) }
            for l in chosenLines { groups.append(byLine[l]!.sorted()) }
        } else {
            groups.append(chosen)
        }
        return groups.enumerated().compactMap { g, indices in
            ShapeFactory.fromEditable(stroke, indices.map { paths[$0] }, id: g == 0 ? stroke.id : ids())
        }
    }

    static func paintPart(_ stroke: Stroke, _ index: Int, _ colorHex: String?, fill: Bool) -> Stroke? {
        var paths = ShapeFactory.subPaths(stroke)
        guard paths.indices.contains(index) else { return nil }
        if fill { paths[index].fillColorHex = colorHex } else { paths[index].strokeColorHex = colorHex }
        return ShapeFactory.fromEditable(stroke, paths)
    }

    static func smoothCurve(_ points: [P], tolerance: Float) -> [P] {
        if points.count < 3 { return points }
        var pts = simplify(points, max(0.5, tolerance * 0.35))
        for _ in 0..<3 {
            if pts.count < 3 { break }
            var out: [P] = [pts[0]]
            for i in 0..<(pts.count - 1) {
                let a = pts[i], b = pts[i + 1]
                out.append(P(a.x * 0.75 + b.x * 0.25, a.y * 0.75 + b.y * 0.25))
                out.append(P(a.x * 0.25 + b.x * 0.75, a.y * 0.25 + b.y * 0.75))
            }
            out.append(pts[pts.count - 1])
            pts = out
        }
        return pts.count > MAX_CURVE_POINTS ? simplify(pts, tolerance * 0.08) : pts
    }

    private static func simplify(_ points: [P], _ epsilon: Float) -> [P] {
        if points.count < 3 { return points }
        var keep = [Bool](repeating: false, count: points.count)
        keep[0] = true; keep[points.count - 1] = true
        var stack = [(0, points.count - 1)]
        while let (a, b) = stack.popLast() {
            var index = -1
            var maxD: Float = 0
            if b > a + 1 {
                for i in (a + 1)..<b {
                    let d = (project(points[i], points[a], points[b]) - points[i]).distance
                    if d > maxD { maxD = d; index = i }
                }
            }
            if index >= 0 && maxD > epsilon {
                keep[index] = true
                stack.append((a, index)); stack.append((index, b))
            }
        }
        return points.indices.filter { keep[$0] }.map { points[$0] }
    }

    /// Şekli başka bir şekle dönüştürür: yeni şekil eskisinin kutusuna yerleşir; biçim ve kimlik korunur.
    static func convert(_ stroke: Stroke, _ kind: String) -> Stroke? {
        guard let spec = stroke.shape, !stroke.points.isEmpty else { return nil }
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude, r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for p in stroke.points { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
        if r - l < 1 && b - t < 1 { return nil }
        let minSide = max(r - l, b - t) * 0.35
        let line = ShapeFactory.fit(kind) == .LINE
        if !line {
            if r - l < minSide { let c = (l + r) / 2; l = c - minSide / 2; r = c + minSide / 2 }
            if b - t < minSide { let c = (t + b) / 2; t = c - minSide / 2; b = c + minSide / 2 }
        }
        let start = line ? P(l, (t + b) / 2) : P(l, t)
        let end = line ? P(r, (t + b) / 2) : P(r, b)
        let graph: GraphSpec? = GraphShapes.isGraph(kind) ? (spec.graph ?? GraphSpec()) : nil
        guard var out = ShapeFactory.build(kind, start, end, style: spec.style, graph: graph, id: stroke.id, order: stroke.order, opacity: stroke.opacity) else { return nil }
        out.groupId = stroke.groupId; out.clip = stroke.clip
        return out
    }

    static func splitGap(_ stroke: Stroke) -> Float { max(4, (stroke.shape?.style.strokeWidth ?? 4) * 2.2) }
}
