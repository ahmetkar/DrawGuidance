import Foundation

/// Diyagramın tasarım alanı ile elemanın dörtgeni arasındaki dönüşüm.
enum DiagramFrames {
    static func toCanvas(_ corners: [StrokePoint], _ frame: DiagramFrame, _ x: Float, _ y: Float) -> StrokePoint? {
        if corners.count < 4 || frame.width <= 0 || frame.height <= 0 { return nil }
        let u = (x - frame.left) / frame.width
        let v = (y - frame.top) / frame.height
        let p0 = corners[0], p1 = corners[1], p3 = corners[3]
        return StrokePoint(p0.x + u * (p1.x - p0.x) + v * (p3.x - p0.x), p0.y + u * (p1.y - p0.y) + v * (p3.y - p0.y))
    }

    static func toDesign(_ corners: [StrokePoint], _ frame: DiagramFrame, _ x: Float, _ y: Float) -> StrokePoint? {
        if corners.count < 4 { return nil }
        let p0 = corners[0], p1 = corners[1], p3 = corners[3]
        let ax = p1.x - p0.x, ay = p1.y - p0.y
        let bx = p3.x - p0.x, by = p3.y - p0.y
        let det = ax * by - ay * bx
        if abs(det) < 1e-6 { return nil }
        let dx = x - p0.x, dy = y - p0.y
        let u = (dx * by - dy * bx) / det
        let v = (ax * dy - ay * dx) / det
        return StrokePoint(frame.left + u * frame.width, frame.top + v * frame.height)
    }

    static func grow(_ corners: [StrokePoint], _ spec: DiagramSpec, left: Float, top: Float, right: Float, bottom: Float,
                     margin: Float = 0.02) -> ([StrokePoint], DiagramSpec)? {
        let f = spec.frame
        let grown = DiagramFrame(left: min(f.left, left - margin), top: min(f.top, top - margin),
                                 right: max(f.right, right + margin), bottom: max(f.bottom, bottom + margin))
        if grown == f { return nil }
        guard let tl = toCanvas(corners, f, grown.left, grown.top),
              let tr = toCanvas(corners, f, grown.right, grown.top),
              let br = toCanvas(corners, f, grown.right, grown.bottom),
              let bl = toCanvas(corners, f, grown.left, grown.bottom) else { return nil }
        var s = spec
        s.frame = grown
        return ([tl, tr, br, bl], s)
    }
}
