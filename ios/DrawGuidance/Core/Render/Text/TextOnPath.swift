import Foundation

/// v42 yol üzerinde yazı (Android `TextOnPath`): yazı seçilen vektör yolu izler; yolun kopyası yazının içinde saklanır.
enum TextOnPath {
    static func canHost(_ s: Stroke) -> Bool {
        guard let shape = s.shape, s.brushType == .SHAPE, shape.graph == nil, !s.hidden, let first = shape.subpaths.first else { return false }
        return first.label == nil && first.count >= 2 && length(points(s)) > 1
    }

    static func isPenPath(_ s: Stroke) -> Bool { s.shape?.kind == InkFill.FREE_PATH }

    static func points(_ s: Stroke) -> [P] {
        let n = s.shape?.subpaths.first?.count ?? 0
        return s.points.prefix(n).map { P($0.x, $0.y) }
    }

    private static func length(_ pts: [P]) -> Float {
        var l: Float = 0
        for i in pts.indices.dropFirst() { l += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y) }
        return l
    }

    static func hostAt(_ strokes: [Stroke], _ p: P, tolerance: Float) -> Stroke? {
        let best = strokes.reversed().filter(canHost).min { distance(points($0), p) < distance(points($1), p) }
        guard let b = best, distance(points(b), p) <= tolerance else { return nil }
        return b
    }

    private static func distance(_ pts: [P], _ p: P) -> Float {
        var best = Float.greatestFiniteMagnitude
        for i in pts.indices.dropFirst() {
            let a = pts[i - 1], b = pts[i]
            let abx = b.x - a.x, aby = b.y - a.y
            let len2 = abx * abx + aby * aby
            let t: Float = len2 < 1e-6 ? 0 : (((p.x - a.x) * abx + (p.y - a.y) * aby) / len2).coerceIn(0, 1)
            best = min(best, hypot(a.x + abx * t - p.x, a.y + aby * t - p.y))
        }
        return best
    }

    static func attach(_ text: Stroke, _ host: Stroke) -> Stroke? {
        guard var spec = text.text, text.brushType == .TEXT, canHost(host) else { return nil }
        let pts = points(host)
        let m = TextRenderer.effectiveSize(spec, text.sizePx) * 1.4
        let minX = pts.map(\.x).min()! - m, minY = pts.map(\.y).min()! - m
        let maxX = pts.map(\.x).max()! + m, maxY = pts.map(\.y).max()! + m
        let old = spec.path
        spec.path = TextPathSpec(points: pts.map { StrokePoint($0.x - minX, $0.y - minY) }, w: maxX - minX, h: maxY - minY,
                                 start: old?.start ?? 0, below: old?.below ?? false, reversed: old?.reversed ?? false,
                                 closed: host.shape?.subpaths.first?.closed == true)
        spec.box = spec.box.flatMap { TextBox(effects: $0.effects).orNull() }
        var out = text
        out.text = spec
        out.points = [StrokePoint(minX, minY), StrokePoint(maxX, minY), StrokePoint(maxX, maxY), StrokePoint(minX, maxY)]
        return out
    }

    static func detach(_ text: Stroke) -> Stroke? {
        guard var spec = text.text, spec.path != nil else { return nil }
        spec.path = nil
        let tl = text.points.first ?? StrokePoint(0, 0)
        var out = text
        out.text = spec
        out.points = TextRenderer.quadAt(spec, text.sizePx, tl.x, tl.y)
        return out
    }

    static func adjust(_ text: Stroke, start: Float? = nil, below: Bool? = nil, reversed: Bool? = nil) -> Stroke? {
        guard var spec = text.text, var path = spec.path else { return nil }
        path.start = start?.coerceIn(0, 1) ?? path.start
        path.below = below ?? path.below
        path.reversed = reversed ?? path.reversed
        spec.path = path
        var out = text
        out.text = spec
        return out
    }
}
