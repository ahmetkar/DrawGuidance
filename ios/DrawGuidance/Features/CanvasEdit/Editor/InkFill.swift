import Foundation

/// v43 alanla seçim yardımcıları (Android `AreaSelect`).
enum AreaSelect {
    static func inside(_ poly: [P], _ p: P) -> Bool {
        if poly.count < 3 { return false }
        var inside = false
        var j = poly.count - 1
        for i in poly.indices {
            let a = poly[i], b = poly[j]
            if (a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x { inside.toggle() }
            j = i
        }
        return inside
    }

    static func usable(_ poly: [P], minSize: Float) -> Bool {
        if poly.count < 3 { return false }
        let xs = poly.map(\.x), ys = poly.map(\.y)
        return xs.max()! - xs.min()! >= minSize && ys.max()! - ys.min()! >= minSize
    }
}

/// v44 kalemle çizilmiş kapalı şeklin içini boyama (Android `InkFill`).
enum InkFill {
    static let PREFIX = "inkfill-"
    static let FREE_PATH = "PATH"

    static func isInk(_ s: Stroke) -> Bool {
        s.shape == nil && s.text == nil && s.diagram == nil && s.formula == nil &&
            ![BrushType.ERASER, .IMAGE, .TEXT, .SHAPE, .DIAGRAM, .FORMULA].contains(s.brushType) && s.points.count >= 8
    }

    static func isClosed(_ s: Stroke) -> Bool {
        if !isInk(s) { return false }
        let p = s.points
        let gap = hypotf(p[0].x - p[p.count - 1].x, p[0].y - p[p.count - 1].y)
        var length: Float = 0
        for i in 1..<p.count { length += hypotf(p[i].x - p[i - 1].x, p[i].y - p[i - 1].y) }
        if length < 40 { return false }
        if gap > max(24, s.sizePx * 3) && gap > length * 0.08 { return false }
        return abs(area(p.map(\.p))) > length * max(2, s.sizePx) * 0.5
    }

    static func closedAt(_ strokes: [Stroke], _ point: P, locked: Set<String>) -> Stroke? {
        strokes.reversed().first { s in !s.hidden && !locked.contains(s.id) && isClosed(s) && AreaSelect.inside(s.points.map(\.p), point) }
    }

    static func apply(_ strokes: [Stroke], _ ids: Set<String>, _ colorHex: String) -> [Stroke] {
        if ids.isEmpty { return strokes }
        var out: [Stroke] = []
        out.reserveCapacity(strokes.count + ids.count)
        var existing = Set<String>()
        for s in strokes where s.id.hasPrefix(PREFIX) { existing.insert(String(s.id.dropFirst(PREFIX.count))) }
        for s in strokes {
            if s.id.hasPrefix(PREFIX) && ids.contains(String(s.id.dropFirst(PREFIX.count))) {
                guard var shape = s.shape else { continue }
                shape.style.fillColorHex = colorHex
                shape.style.strokeColorHex = colorHex
                var c = s; c.colorHex = colorHex; c.shape = shape
                out.append(c)
                continue
            }
            if ids.contains(s.id) && !existing.contains(s.id) && isClosed(s) {
                let group = s.groupId ?? UUID().uuidString.lowercased()
                if var f = fill(s, colorHex) { f.groupId = group; f.layerId = s.layerId; out.append(f) }
                var c = s; c.groupId = group
                out.append(c)
                continue
            }
            out.append(s)
        }
        return out
    }

    static func fill(_ s: Stroke, _ colorHex: String) -> Stroke? {
        let pts = s.points.map(\.p)
        let path = ShapePath(points: pts + [pts[0]], closed: true)
        let style = ShapeStyle(strokeColorHex: colorHex, strokeWidth: 0.5, fillColorHex: colorHex)
        return ShapeFactory.fromPaths(FREE_PATH, [path], style: style, graph: nil, id: PREFIX + s.id, order: s.order)
    }

    private static func area(_ p: [P]) -> Float {
        var a: Float = 0
        for i in p.indices { let j = (i + 1) % p.count; a += p[i].x * p[j].y - p[j].x * p[i].y }
        return a / 2
    }
}
