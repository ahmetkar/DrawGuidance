import Foundation

/// v45 çocuk modunda boya fırçası (Android `KidsBrush`): fırça bir alanda başlarsa boya o alanın içinde kalır.
enum KidsBrush {
    static let TAG = VectorEdit.BRUSH_PAINT
    static let PREFIX = "kbrush-"
    static let FREE_BRUSH: BrushType = .MARKER

    static func isPaintLayer(_ tag: String?) -> Bool { tag == VectorEdit.REGION || tag == TAG }

    final class Region {
        let strokeId: String
        let ink: Bool
        let walls: [RegionFill.Line]
        let gap: Float
        let seed: P
        let outline: [P]
        private(set) lazy var mask: RegionFill.Mask? = RegionFill.fillMask(walls, seed, gap: gap)

        init(_ strokeId: String, _ ink: Bool, _ walls: [RegionFill.Line], _ gap: Float, _ seed: P, _ outline: [P]) {
            self.strokeId = strokeId; self.ink = ink; self.walls = walls; self.gap = gap; self.seed = seed; self.outline = outline
        }
    }

    static func clip(_ region: Region, _ points: [P], _ radius: Float) -> [[P]] {
        guard let mask = region.mask else { return [] }
        return RegionClip.clip(mask, points, radius)
    }

    static func parts(_ polys: [[P]], _ colorHex: String) -> [EditablePath] {
        polys.filter { $0.count >= 3 }.map { EditablePath(points: $0, closed: true, fillColorHex: colorHex, widthScale: 0, tag: TAG) }
    }

    static func inkPaint(_ ink: Stroke, _ polys: [[P]], _ colorHex: String) -> Stroke? {
        let paths = polys.filter { $0.count >= 3 }.map { ShapePath(points: $0 + [$0[0]], closed: true, fillColorHex: colorHex, widthScale: 0, tag: TAG) }
        if paths.isEmpty { return nil }
        let style = ShapeStyle(strokeColorHex: colorHex, strokeWidth: 0.5, fillColorHex: colorHex)
        guard var s = ShapeFactory.fromPaths(InkFill.FREE_PATH, paths, style: style, graph: nil, id: PREFIX + UUID().uuidString.lowercased(), order: ink.order) else { return nil }
        s.layerId = ink.layerId
        return s
    }

    static func insertInkPaint(_ strokes: [Stroke], _ inkId: String, _ paint: Stroke) -> [Stroke] {
        guard let at = strokes.firstIndex(where: { $0.id == inkId }) else { return strokes }
        let group = strokes[at].groupId ?? UUID().uuidString.lowercased()
        var out: [Stroke] = []
        out.reserveCapacity(strokes.count + 1)
        for (i, s) in strokes.enumerated() {
            if i == at {
                var p = paint; p.groupId = group; out.append(p)
                var c = s; c.groupId = group; out.append(c)
            } else if s.id == InkFill.PREFIX + inkId {
                var c = s; c.groupId = group; out.append(c)
            } else {
                out.append(s)
            }
        }
        return out
    }

    static func freeStroke(_ points: [P], _ colorHex: String, _ sizePx: Float, _ order: Int, _ layerId: String?) -> Stroke {
        var s = Stroke(id: UUID().uuidString.lowercased(), points: points.map { StrokePoint($0.x, $0.y) }, colorHex: colorHex,
                       brushType: FREE_BRUSH, sizePx: sizePx, opacity: 1, order: order)
        s.layerId = layerId
        return s
    }

    static func radius(_ sizePx: Float) -> Float { max(sizePx / 2, 3) }
}
