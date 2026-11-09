import Foundation

/// Fırça çiziminin titreşimini yumuşatır (Android `InkSmoothing`).
enum InkSmoothing {
    static func smooth(_ points: [StrokePoint], strength: Float = 1) -> [StrokePoint] {
        if points.count < 4 { return points }
        let n = points.count
        let closed = points[0].x == points[n - 1].x && points[0].y == points[n - 1].y
        var acc = [Float](repeating: 0, count: n)
        for i in 1..<n { acc[i] = acc[i - 1] + hypotf(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y) }
        let length = acc[n - 1]
        if length < 1 { return points }
        var xs = [Float](repeating: 0, count: n), ys = xs, ps = xs, ts = xs, os = xs
        var j = 0
        for i in 0..<n {
            let d = length * Float(i) / Float(n - 1)
            while j < n - 2 && acc[j + 1] < d { j += 1 }
            let seg = max(acc[j + 1] - acc[j], 1e-6)
            let u = ((d - acc[j]) / seg).coerceIn(0, 1)
            let a = points[j], b = points[j + 1]
            xs[i] = a.x + (b.x - a.x) * u; ys[i] = a.y + (b.y - a.y) * u
            ps[i] = a.pressure + (b.pressure - a.pressure) * u
            ts[i] = a.tilt + (b.tilt - a.tilt) * u
            os[i] = u < 0.5 ? a.orient : b.orient
        }
        let spacing = length / Float(n - 1)
        let sigmaPx = (length * 0.04 * strength).coerceIn(4, 40)
        let sigma = max(0.8, sigmaPx / spacing)
        let radius = max(Int((sigma * 2.5).rounded()), 1)
        let weights = (0...radius).map { k in expf(-Float(k * k) / (2 * sigma * sigma)) }
        func floorMod(_ a: Int, _ m: Int) -> Int { ((a % m) + m) % m }
        func sample(_ arr: [Float], _ k: Int) -> Float {
            if closed { return arr[floorMod(k, n - 1)] }
            if k < 0 { return 2 * arr[0] - arr[-k] }
            if k >= n { return 2 * arr[n - 1] - arr[2 * (n - 1) - k] }
            return arr[k]
        }
        var ox = [Float](repeating: 0, count: n), oy = ox
        for i in 0..<n {
            var sx: Float = 0, sy: Float = 0, sw: Float = 0
            for k in -radius...radius {
                let idx = (i + k).coerceIn(-(n - 1), 2 * (n - 1))
                let w = weights[abs(k)]
                sx += sample(xs, idx) * w; sy += sample(ys, idx) * w; sw += w
            }
            ox[i] = sx / sw; oy[i] = sy / sw
        }
        if closed { ox[n - 1] = ox[0]; oy[n - 1] = oy[0] }
        let q = ContentCodec.quantize
        return (0..<n).map { i in
            if !closed && i == 0 { return points[0] }
            if !closed && i == n - 1 { return points[n - 1] }
            return StrokePoint(q(ox[i]), q(oy[i]), pressure: ps[i], tilt: ts[i], orient: os[i])
        }
    }
}

/// Seçili çizgiler arasındaki küçük boşlukları köprülerle kapatır (Android `GapFiller`).
enum GapFiller {
    struct Result { let strokes: [Stroke]; let bridges: [Stroke] }

    private final class End {
        let stroke: Stroke, strokeIndex: Int, isStart: Bool, x: Float, y: Float, dx: Float, dy: Float
        init(_ stroke: Stroke, _ strokeIndex: Int, _ isStart: Bool, _ x: Float, _ y: Float, _ dx: Float, _ dy: Float) {
            self.stroke = stroke; self.strokeIndex = strokeIndex; self.isStart = isStart; self.x = x; self.y = y; self.dx = dx; self.dy = dy
        }
    }

    static func fill(_ strokes: [Stroke], _ selectedIds: Set<String>) -> Result {
        let candidates = strokes.enumerated().filter { _, s in
            selectedIds.contains(s.id) && s.brushType != .ERASER && !s.brushType.isQuad && s.points.count >= 2 && length(s) >= 2
        }
        if candidates.isEmpty { return Result(strokes: strokes, bridges: []) }
        guard let bounds = ElementOps.bounds(candidates.map(\.element)) else { return Result(strokes: strokes, bridges: []) }
        let diagonal = hypotf(bounds.width, bounds.height)
        let avgWidth = candidates.map { ElementOps.halfWidth($0.element) * 2 }.reduce(0, +) / Float(candidates.count)
        let maxGap = max(max(60, avgWidth * 10), diagonal * 0.35)
        var ends: [End] = []
        for (index, stroke) in candidates {
            let half = ElementOps.halfWidth(stroke)
            let first = stroke.points[0], last = stroke.points[stroke.points.count - 1]
            if hypotf(first.x - last.x, first.y - last.y) <= half * 2 { continue }
            for isStart in [true, false] {
                let end = isStart ? first : last
                let (dx, dy) = outwardDirection(stroke, isStart, max(8, half * 3))
                let touchesOther = candidates.contains { otherIndex, other in
                    otherIndex != index && ElementOps.distanceToPolyline(P(end.x, end.y), other.points) <= half + ElementOps.halfWidth(other) + 1
                }
                if !touchesOther { ends.append(End(stroke, index, isStart, end.x, end.y, dx, dy)) }
            }
        }
        var pairs: [(a: End, b: End, score: Float)] = []
        for i in ends.indices {
            for j in (i + 1)..<max(i + 1, ends.count) {
                let a = ends[i], b = ends[j]
                let d = hypotf(b.x - a.x, b.y - a.y)
                if d < 0.5 || d > maxGap { continue }
                if a.strokeIndex == b.strokeIndex && d > length(a.stroke) * 0.3 { continue }
                let ux = (b.x - a.x) / d, uy = (b.y - a.y) / d
                let cosA = a.dx * ux + a.dy * uy
                let cosB = -(b.dx * ux + b.dy * uy)
                if cosA < -0.3 && cosB < -0.3 { continue }
                pairs.append((a, b, d * (1 + (1 - cosA) * 0.35 + (1 - cosB) * 0.35)))
            }
        }
        let sorted = pairs.enumerated().sorted { $0.element.score != $1.element.score ? $0.element.score < $1.element.score : $0.offset < $1.offset }.map(\.element)
        var used = Set<ObjectIdentifier>()
        var bridges: [Stroke] = []
        for pair in sorted {
            if used.contains(ObjectIdentifier(pair.a)) || used.contains(ObjectIdentifier(pair.b)) { continue }
            used.insert(ObjectIdentifier(pair.a)); used.insert(ObjectIdentifier(pair.b))
            bridges.append(bridge(pair.a, pair.b, strokes.count + bridges.count))
        }
        return Result(strokes: strokes + bridges, bridges: bridges)
    }

    private static func length(_ s: Stroke) -> Float {
        var total: Float = 0
        for i in 1..<max(1, s.points.count) { total += hypotf(s.points[i].x - s.points[i - 1].x, s.points[i].y - s.points[i - 1].y) }
        return total
    }

    private static func outwardDirection(_ stroke: Stroke, _ isStart: Bool, _ reach: Float) -> (Float, Float) {
        let pts = isStart ? stroke.points : stroke.points.reversed()
        let end = pts[0]
        var walked: Float = 0
        var inner = pts[1]
        for i in 1..<pts.count {
            walked += hypotf(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
            inner = pts[i]
            if walked >= reach { break }
        }
        let dx = end.x - inner.x, dy = end.y - inner.y
        let l = sqrtf(dx * dx + dy * dy)
        return l == 0 ? (1, 0) : (dx / l, dy / l)
    }

    private static func bridge(_ a: End, _ b: End, _ order: Int) -> Stroke {
        let d = hypotf(b.x - a.x, b.y - a.y)
        let k = min(d * 0.38, d)
        let c1x = a.x + a.dx * k, c1y = a.y + a.dy * k
        let c2x = b.x + b.dx * k, c2y = b.y + b.dy * k
        let half = ElementOps.halfWidth(a.stroke)
        let n = min(max(6, Int((d / max(2, half)).rounded(.up)) + 1), 400)
        let points = (0..<n).map { i -> StrokePoint in
            let t = Float(i) / Float(n - 1), u = 1 - t
            let x = u * u * u * a.x + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * b.x
            let y = u * u * u * a.y + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t * t * t * b.y
            return StrokePoint(ContentCodec.quantize(x), ContentCodec.quantize(y))
        }
        let s = a.stroke
        return Stroke(id: UUID().uuidString.lowercased(), points: points, colorHex: s.colorHex, brushType: s.brushType, sizePx: s.sizePx,
                      opacity: s.opacity, order: order, groupId: s.groupId, flow: s.flow, strength: s.strength)
    }
}
