import CoreGraphics
import Foundation

/// Silgiyle artık hiç görünmeyen çizimleri (ve boşa kalan silgileri) veriden çıkarır (Android `ErasedStrokePruner`).
/// Sonradan çizilen silginin değdiği her çizim gerçek piksellerle denetlenir.
enum ErasedStrokePruner {
    private static let VISIBLE_ALPHA: UInt8 = 26
    private static let LEFTOVER_PIXELS = 3
    private static let TARGET_SIDE: Float = 256
    private static let MAX_SIDE: Float = 1024

    static func prune(_ strokes: [Stroke], focus: FRect? = nil) -> [Stroke] {
        let erasers = strokes.enumerated().filter { $0.element.brushType == .ERASER && !$0.element.points.isEmpty }
        if erasers.isEmpty { return strokes }
        var boundsCache = [String: FRect?]()
        func boxOf(_ s: Stroke) -> FRect? {
            if let b = boundsCache[s.id] { return b }
            let b = inkBounds(s)
            boundsCache[s.id] = b
            return b
        }
        var removed = Set<String>()
        var removedArea: FRect?
        for (position, stroke) in strokes.enumerated() {
            if stroke.brushType == .ERASER || stroke.brushType.isObject || stroke.points.isEmpty { continue }
            guard let box = boxOf(stroke) else { continue }
            if let f = focus, !f.overlaps(box) { continue }
            let later = erasers.filter {
                $0.offset > position && ($0.element.layerId ?? "") == (stroke.layerId ?? "") && boxOf($0.element)?.overlaps(box) == true
            }.map(\.element)
            if later.isEmpty { continue }
            if !isVisible(stroke, later, box) {
                removed.insert(stroke.id)
                removedArea = removedArea.map { $0.union(box) } ?? box
            }
        }
        let gcArea: FRect? = focus.map { f in removedArea.map { f.union($0) } ?? f }
        var kept: [Stroke] = []
        var visibleBefore: [Stroke] = []
        for stroke in strokes {
            if removed.contains(stroke.id) { continue }
            if stroke.brushType != .ERASER {
                if !stroke.brushType.isObject { visibleBefore.append(stroke) }
                kept.append(stroke)
                continue
            }
            let box = boxOf(stroke)
            let inArea = gcArea == nil || (box != nil && gcArea!.overlaps(box!))
            if !inArea || stroke.points.isEmpty ||
                visibleBefore.contains(where: { ($0.layerId ?? "") == (stroke.layerId ?? "") && reaches(stroke, $0, box, boxOf($0)) }) {
                kept.append(stroke)
            }
        }
        if kept.count == strokes.count { return strokes }
        return ElementOps.reorder(kept)
    }

    private static func reaches(_ eraser: Stroke, _ target: Stroke, _ eraserBox: FRect?, _ targetBox: FRect?) -> Bool {
        guard let eb = eraserBox, let tb = targetBox, eb.overlaps(tb) else { return false }
        let eraserHalf = ElementOps.halfWidth(eraser)
        let targetReach: Float = target.brushType.isQuad ? 0 : reachRadius(target)
        let limit = eraserHalf + targetReach + 1
        let step = max(1, eraserHalf * 0.5)
        let pts = eraser.points
        func near(_ x: Float, _ y: Float) -> Bool {
            let p = P(x, y)
            return target.brushType.isQuad ? ElementOps.distanceToShape(p, target) <= eraserHalf + 1
                : ElementOps.distanceToPolyline(p, target.points) <= limit
        }
        if pts.count == 1 { return near(pts[0].x, pts[0].y) }
        for i in 1..<pts.count {
            let a = pts[i - 1], b = pts[i]
            let length = hypotf(b.x - a.x, b.y - a.y)
            var t: Float = 0
            while true {
                let f: Float = length == 0 ? 0 : t / length
                if near(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f) { return true }
                if t >= length { break }
                t = min(length, t + step)
            }
        }
        return false
    }

    private static func reachRadius(_ stroke: Stroke) -> Float {
        let half = ElementOps.halfWidth(stroke)
        return half + half * (stroke.brushType.style.reach - 1) * 1.6 + 2
    }

    private static func inkBounds(_ stroke: Stroke) -> FRect? {
        guard let b = ElementOps.bounds(stroke) else { return nil }
        if stroke.brushType.isQuad { return b.inflate(1) }
        let half = ElementOps.halfWidth(stroke)
        return b.inflate(half * (stroke.brushType.style.reach - 1) * 1.6 + 2)
    }

    /// Çizimden sonraki silgiler uygulandıktan sonra görünür mürekkep kalıyor mu?
    static func isVisible(_ stroke: Stroke, _ laterErasers: [Stroke], _ box: FRect) -> Bool {
        let side = max(max(box.width, box.height), 1)
        let ink = stroke.brushType.isQuad ? side : stroke.brushType.inkWidth(stroke.sizePx, strength: stroke.strength)
        var scale = min(max(TARGET_SIDE / side, 2 / ink), 2)
        if side * scale > MAX_SIDE { scale = MAX_SIDE / side }
        let w = max(1, Int((box.width * scale).rounded(.up)) + 2)
        let h = max(1, Int((box.height * scale).rounded(.up)) + 2)
        guard let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                  space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return true }
        // y aşağı tuval koordinatları
        ctx.translateBy(x: 0, y: CGFloat(h))
        ctx.scaleBy(x: 1, y: -1)
        ctx.translateBy(x: 1, y: 1)
        ctx.scaleBy(x: CGFloat(scale), y: CGFloat(scale))
        ctx.translateBy(x: CGFloat(-box.left), y: CGFloat(-box.top))
        drawCoverage(ctx, stroke)
        for e in laterErasers {
            let xs = e.points.map(\.x), ys = e.points.map(\.y)
            let d = BrushPainter.dynamicsOf(e.points)
            BrushPainter.paint(ctx, xs: xs, ys: ys, color: ArgbColor.black, type: .ERASER, sizePx: e.sizePx, opacity: e.opacity,
                               seed: e.id.javaHashCode, flow: e.flow, strength: e.strength, pressures: d.pressures, tilts: d.tilts)
        }
        guard let data = ctx.data else { return true }
        let px = data.bindMemory(to: UInt8.self, capacity: w * h * 4)
        var visible = 0
        for i in 0..<(w * h) where px[i * 4 + 3] > VISIBLE_ALPHA {
            visible += 1
            if visible > LEFTOVER_PIXELS { return true }
        }
        return false
    }

    private static func drawCoverage(_ c: CGContext, _ stroke: Stroke) {
        let pts = stroke.points
        if stroke.brushType == .TEXT { TextRenderer.draw(c, stroke, color: ArgbColor.black, coverage: true); return }
        if stroke.brushType == .IMAGE {
            if pts.count < 4 { return }
            let path = CGMutablePath()
            path.move(to: CGPoint(x: CGFloat(pts[0].x), y: CGFloat(pts[0].y)))
            for i in 1..<4 { path.addLine(to: CGPoint(x: CGFloat(pts[i].x), y: CGFloat(pts[i].y))) }
            path.closeSubpath()
            c.setFillColor(ArgbColor.cgColor(ArgbColor.black))
            c.addPath(path); c.fillPath()
            return
        }
        // Tam kapsama: opaklık ve akışkanlık dikkate alınmadan (soluk çizimler de sayılsın)
        let xs = pts.map(\.x), ys = pts.map(\.y)
        let d = BrushPainter.dynamicsOf(pts)
        BrushPainter.paint(c, xs: xs, ys: ys, color: ArgbColor.black, type: stroke.brushType, sizePx: stroke.sizePx, opacity: 1,
                           seed: stroke.id.javaHashCode, flow: 1, strength: stroke.strength, pressures: d.pressures, tilts: d.tilts,
                           params: stroke.params, orients: d.orients)
    }
}
