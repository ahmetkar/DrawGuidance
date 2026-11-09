import Foundation

/// v40 cetvel ve şekil kalıbının yapışma kenarları (Android `SnapEdges`): doğru parçaları ve yaylar, konturlara bölünmüş.
final class SnapEdges: @unchecked Sendable {
    static let LINE = 0
    static let ARC = 1
    private static let STRIDE = 5
    private static let TWO_PI = 2 * Float.pi
    static let Empty = Builder().build()

    private let kinds: [Int]
    private let data: [Float]
    private let contourStart: [Int]

    private init(_ kinds: [Int], _ data: [Float], _ contourStart: [Int]) { self.kinds = kinds; self.data = data; self.contourStart = contourStart }

    var contourCount: Int { contourStart.count - 1 }
    var segmentCount: Int { kinds.count }
    var isEmpty: Bool { kinds.isEmpty }

    /// En yakın konturun sırası (yoksa -1) ve en yakın nokta + uzaklık.
    func lock(_ x: Float, _ y: Float, maxDistance: Float) -> (Int, P, Float) {
        var best = -1, bestD = maxDistance, bp = P(0, 0)
        for c in 0..<max(contourCount, 0) {
            guard let (p, d) = projectContour(c, x, y) else { continue }
            if d <= bestD { bestD = d; best = c; bp = p }
        }
        return (best, bp, bestD)
    }

    func project(_ contour: Int, _ x: Float, _ y: Float) -> (P, Float) {
        guard contour >= 0, contour < contourCount, let r = projectContour(contour, x, y) else { return (P(x, y), 0) }
        return r
    }

    private func projectContour(_ c: Int, _ x: Float, _ y: Float) -> (P, Float)? {
        let from = contourStart[c], to = contourStart[c + 1]
        if from >= to { return nil }
        var bestD2 = Float.greatestFiniteMagnitude, bx = x, by = y
        for i in from..<to {
            let b = i * Self.STRIDE
            var px: Float, py: Float
            if kinds[i] == Self.LINE {
                let x1 = data[b], y1 = data[b + 1], dx = data[b + 2], dy = data[b + 3]
                let t = (((x - x1) * dx + (y - y1) * dy) * data[b + 4]).coerceIn(0, 1)
                px = x1 + dx * t; py = y1 + dy * t
            } else {
                let cx = data[b], cy = data[b + 1], r = data[b + 2], start = data[b + 3], sweep = data[b + 4]
                var a = atan2(y - cy, x - cx) - start
                while a < 0 { a += Self.TWO_PI }
                while a >= Self.TWO_PI { a -= Self.TWO_PI }
                let angle: Float = a <= sweep ? start + a : ((a - sweep) < (Self.TWO_PI - a) ? start + sweep : start)
                px = cx + r * cos(angle); py = cy + r * sin(angle)
            }
            let ddx = px - x, ddy = py - y, d2 = ddx * ddx + ddy * ddy
            if d2 < bestD2 { bestD2 = d2; bx = px; by = py }
        }
        return (P(bx, by), bestD2.squareRoot())
    }

    final class Builder {
        private var kinds: [Int] = []
        private var data: [Float] = []
        private var starts = [0]

        @discardableResult
        func line(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float) -> Builder {
            let dx = x2 - x1, dy = y2 - y1, len2 = dx * dx + dy * dy
            if len2 < 1e-8 { return self }
            kinds.append(SnapEdges.LINE)
            data += [x1, y1, dx, dy, 1 / len2]
            return self
        }

        @discardableResult
        func arc(_ cx: Float, _ cy: Float, _ r: Float, _ start: Float, _ sweep: Float) -> Builder {
            if r <= 0 || sweep == 0 { return self }
            var s = start, w = sweep
            if w < 0 { s += w; w = -w }
            w = min(w, SnapEdges.TWO_PI)
            kinds.append(SnapEdges.ARC)
            data += [cx, cy, r, s, w]
            return self
        }

        @discardableResult
        func polyline(_ points: [Float], closed: Bool) -> Builder {
            let n = points.count / 2
            if n >= 2 { for i in 0..<(n - 1) { line(points[2 * i], points[2 * i + 1], points[2 * i + 2], points[2 * i + 3]) } }
            if closed && n > 2 { line(points[2 * n - 2], points[2 * n - 1], points[0], points[1]) }
            return self
        }

        @discardableResult
        func endContour() -> Builder {
            if starts.last != kinds.count { starts.append(kinds.count) }
            return self
        }

        func build() -> SnapEdges {
            endContour()
            return SnapEdges(kinds, data, starts)
        }
    }
}

/// v40 çizgiyi kenara yapıştıran kaynak (Android `StrokeGuide`).
protocol StrokeGuide: AnyObject {
    func lock(_ x: Float, _ y: Float, captureDistance: Float) -> Int
    func project(_ lock: Int, _ x: Float, _ y: Float) -> P
}

final class EdgesGuide: StrokeGuide {
    let edges: SnapEdges
    private let captureAll: Bool
    init(_ edges: SnapEdges, captureAll: Bool = false) { self.edges = edges; self.captureAll = captureAll }
    func lock(_ x: Float, _ y: Float, captureDistance: Float) -> Int {
        edges.lock(x, y, maxDistance: captureAll ? .greatestFiniteMagnitude : captureDistance).0
    }
    func project(_ lock: Int, _ x: Float, _ y: Float) -> P { edges.project(lock, x, y).0 }
}

/// v40 şekil kalıbı: seçilen şeklin belge dışındaki hayalet kopyası (Android `TemplateGhost`).
final class TemplateGhost {
    static let MIN_SCALE: Float = 0.05
    static let MAX_SCALE: Float = 40

    let local: [Contour]
    let cx: Float, cy: Float, scale: Float
    let colorHex: String
    let localBounds: FRect

    init(_ local: [Contour], _ cx: Float, _ cy: Float, scale: Float = 1, colorHex: String = "#3B82F6") {
        self.local = local; self.cx = cx; self.cy = cy; self.scale = scale; self.colorHex = colorHex
        var l = Float.greatestFiniteMagnitude, t = l, r = -l, b = -l
        for c in local {
            for i in 0..<(c.points.count / 2) {
                let x = c.points[2 * i], y = c.points[2 * i + 1]
                l = min(l, x); r = max(r, x); t = min(t, y); b = max(b, y)
            }
        }
        localBounds = l > r ? .zero : FRect(l, t, r, b)
    }

    private(set) lazy var contours: [Contour] = local.map { c in
        (c.points.enumerated().map { i, v in i % 2 == 0 ? cx + v * scale : cy + v * scale }, c.closed)
    }

    private(set) lazy var edges: SnapEdges = {
        let b = SnapEdges.Builder()
        for c in contours { b.polyline(c.points, closed: c.closed).endContour() }
        return b.build()
    }()

    var bounds: FRect {
        FRect(cx + localBounds.left * scale, cy + localBounds.top * scale, cx + localBounds.right * scale, cy + localBounds.bottom * scale)
    }

    func moved(_ dx: Float, _ dy: Float) -> TemplateGhost { TemplateGhost(local, cx + dx, cy + dy, scale: scale, colorHex: colorHex) }
    func scaled(_ s: Float) -> TemplateGhost { TemplateGhost(local, cx, cy, scale: s.coerceIn(Self.MIN_SCALE, Self.MAX_SCALE), colorHex: colorHex) }

    func moveHandle(gap: Float) -> P { let b = bounds; return P(b.left - gap, b.top - gap) }
    func scaleHandle(gap: Float) -> P { let b = bounds; return P(b.right + gap, b.bottom + gap) }

    func scaleFor(_ p: P, startScale: Float, startHandle: P) -> Float {
        let d0 = hypot(startHandle.x - cx, startHandle.y - cy)
        if d0 < 1e-3 { return startScale }
        return (startScale * hypot(p.x - cx, p.y - cy) / d0).coerceIn(Self.MIN_SCALE, Self.MAX_SCALE)
    }

    /// Çizimlerin konturları: şekillerde alt yollar, dörtgen elemanlarda çerçeve, serbest çizimde noktalar.
    static func contoursOf(_ strokes: [Stroke]) -> [Contour] {
        var out: [Contour] = []
        for s in strokes where s.brushType != .ERASER && s.points.count >= 2 {
            if let shape = s.shape, !shape.subpaths.isEmpty {
                var at = 0
                for sub in shape.subpaths {
                    let end = min(at + sub.count, s.points.count)
                    if end - at >= 2 { out.append((s.points[at..<end].flatMap { [$0.x, $0.y] }, sub.closed)) }
                    at = end
                }
            } else {
                out.append((s.points.flatMap { [$0.x, $0.y] }, s.brushType.isQuad))
            }
        }
        return out
    }

    static func of(_ contours: [Contour], colorHex: String = "#3B82F6") -> TemplateGhost? {
        if contours.isEmpty { return nil }
        let b = TemplateGhost(contours, 0, 0).localBounds
        if b.width <= 0 && b.height <= 0 { return nil }
        let c = b.center
        let local: [Contour] = contours.map { ct in (ct.points.enumerated().map { i, v in v - (i % 2 == 0 ? c.x : c.y) }, ct.closed) }
        return TemplateGhost(local, c.x, c.y, scale: 1, colorHex: colorHex)
    }
}
