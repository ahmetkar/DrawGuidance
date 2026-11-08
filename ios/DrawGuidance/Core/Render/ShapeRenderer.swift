import CoreGraphics
import Foundation
import UIKit

/// Vektör şekillerin çizimi (Android `ui.drawing.ShapeRenderer`): alt yollar (kapalı/kesik), dolgu rengi, kenar
/// rengi/kalınlığı ve köşe yuvarlaklığı. Yollar şekil başına önbelleklenir.
enum ShapeRenderer {
    /// Parça renginde "kalem rengi" anlamındaki özel değer.
    static let INK = "@ink"
    /// v21 gölgeli cisimlerin yüz dolgusu: "@shade:0.72".
    static let SHADE = "@shade:"
    /// v21 dolgu almayan kapalı parça.
    static let NO_FILL = "@none"

    static func shade(_ level: Float) -> String {
        let v = min(max(level, 0), 1)
        return SHADE + String(format: "%.2f", Double(ContentCodec.roundToLong(v * 100)) / 100)
    }

    static func isShade(_ hex: String?) -> Bool { hex?.hasPrefix(SHADE) == true }

    /// Parçanın gerçek dolgu rengi (özel değerler çözülür); dolgu yoksa nil.
    static func resolveFill(_ own: String?, _ style: ShapeStyle, ink: String? = nil) -> String? {
        let ink = ink ?? style.strokeColorHex
        guard let own else { return style.fillColorHex }
        if own == INK { return ink }
        if own == NO_FILL { return nil }
        if own.hasPrefix(SHADE) {
            let level = Float(own.dropFirst(SHADE.count)) ?? 0.5
            let fill = (style.fillColorHex?.isEmpty == false) ? style.fillColorHex : nil
            var base = ArgbColor.hex(fill ?? ink)
            // Dolgusuz cisimde çok koyu kenar rengi açılır (gölge tonları görünsün)
            if fill == nil {
                let lum = (0.3 * Float(ArgbColor.red(base)) + 0.59 * Float(ArgbColor.green(base)) + 0.11 * Float(ArgbColor.blue(base))) / 255
                if lum < 0.35 { base = ArgbColor.mix(base, ArgbColor.white, 0.5) }
            }
            return ArgbColor.hexRGB(ArgbColor.tone(base, level))
        }
        return own
    }

    /// Köşe sayılan en küçük yön değişimi (daire gibi eğrilerde yuvarlama uygulanmaz).
    private static let CORNER_COS: Float = 0.94

    private final class Item {
        let path = CGMutablePath()
        let fill: Bool
        let ownFill: String?
        let stroke: String?
        let widthScale: Float
        let dashed: Bool
        let dotted: Bool
        let alpha: Float

        init(fill: Bool, ownFill: String?, stroke: String?, widthScale: Float, dashed: Bool, dotted: Bool, alpha: Float) {
            self.fill = fill; self.ownFill = ownFill; self.stroke = stroke; self.widthScale = widthScale
            self.dashed = dashed; self.dotted = dotted; self.alpha = alpha
        }

        func sameStyle(_ o: Item) -> Bool {
            fill == o.fill && ownFill == o.ownFill && stroke == o.stroke && widthScale == o.widthScale &&
                dashed == o.dashed && dotted == o.dotted && alpha == o.alpha
        }
    }

    private final class Cached {
        let points: [StrokePoint]
        let radius: Float
        let subpaths: [SubPath]
        let items: [Item]
        let left, top, right, bottom: Float
        init(points: [StrokePoint], radius: Float, subpaths: [SubPath], items: [Item], left: Float, top: Float, right: Float, bottom: Float) {
            self.points = points; self.radius = radius; self.subpaths = subpaths; self.items = items
            self.left = left; self.top = top; self.right = right; self.bottom = bottom
        }
    }

    private static let cache: NSCache<NSString, Cached> = {
        let c = NSCache<NSString, Cached>()
        c.totalCostLimit = 400_000
        return c
    }()

    /// Alt yol dolgu alır mı: kapalı yollar ve neredeyse kapalı yollar.
    static func fills(_ sub: SubPath, _ pts: ArraySlice<StrokePoint>, strokeWidth: Float) -> Bool {
        if sub.label != nil || pts.count < 3 { return false }
        if sub.fillColorHex == NO_FILL { return false }
        if sub.fillColorHex != nil { return true }
        if sub.dashed { return false }
        if sub.closed { return true }
        let f = pts.first!, l = pts.last!
        let gap = hypotf(f.x - l.x, f.y - l.y)
        if gap > max(14, strokeWidth * 6) { return false }
        var length: Float = 0
        var prev = f
        for p in pts.dropFirst() { length += hypotf(p.x - prev.x, p.y - prev.y); prev = p }
        return length > gap * 8
    }

    /// Şeklin dolgu alabilen bir yolu var mı.
    static func isFillable(_ stroke: Stroke) -> Bool {
        guard let spec = stroke.shape else { return false }
        let rs = ranges(spec, stroke.points.count)
        for (i, r) in rs.enumerated() where i < spec.subpaths.count {
            if fills(spec.subpaths[i], stroke.points[r], strokeWidth: spec.style.strokeWidth) { return true }
        }
        return false
    }

    /// Şeklin alt yollarının nokta aralıkları.
    static func ranges(_ spec: ShapeSpec, _ total: Int) -> [Range<Int>] {
        var out: [Range<Int>] = []
        out.reserveCapacity(spec.subpaths.count)
        var start = 0
        for sub in spec.subpaths {
            let end = min(total, start + sub.count)
            if end > start { out.append(start..<end) }
            start = end
        }
        if out.isEmpty && total > 0 { out.append(0..<total) }
        return out
    }

    private static func build(_ stroke: Stroke, _ spec: ShapeSpec, useCache: Bool) -> Cached {
        let radius = spec.style.cornerRadius
        let key = stroke.id as NSString
        if useCache, let c = cache.object(forKey: key), c.radius == radius, c.points == stroke.points, c.subpaths == spec.subpaths {
            return c
        }
        var items: [Item] = []
        for (i, range) in ranges(spec, stroke.points.count).enumerated() {
            let sub = i < spec.subpaths.count ? spec.subpaths[i] : SubPath(count: range.count)
            let pts = stroke.points[range]
            if pts.count < 2 || sub.label != nil { continue }
            let fills = fills(sub, pts, strokeWidth: spec.style.strokeWidth)
            let next = Item(fill: fills, ownFill: sub.fillColorHex, stroke: sub.strokeColorHex, widthScale: sub.widthScale ?? 1,
                            dashed: sub.dashed, dotted: sub.dotted, alpha: min(max(sub.alpha ?? 1, 0), 1))
            let target: Item
            if let last = items.last, last.sameStyle(next) { target = last } else { items.append(next); target = next }
            addPath(target.path, Array(pts), closed: sub.closed || fills, radius: radius)
        }
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude
        var r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for p in stroke.points { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
        let c = Cached(points: stroke.points, radius: radius, subpaths: spec.subpaths, items: items, left: l, top: t, right: r, bottom: b)
        if useCache { cache.setObject(c, forKey: key, cost: stroke.points.count + 32) }
        return c
    }

    private static func pt(_ x: Float, _ y: Float) -> CGPoint { CGPoint(x: CGFloat(x), y: CGFloat(y)) }

    static func addPath(_ path: CGMutablePath, _ pts: [StrokePoint], closed: Bool, radius: Float) {
        let n = pts.count
        if radius <= 0.01 {
            path.move(to: pt(pts[0].x, pts[0].y))
            for i in 1..<n { path.addLine(to: pt(pts[i].x, pts[i].y)) }
            if closed { path.closeSubpath() }
            return
        }
        // Köşeleri yuvarla: köşeden iki kenara doğru geri çekilip köşe noktası kontrol noktası olur
        let last = (closed && pts[0].x == pts[n - 1].x && pts[0].y == pts[n - 1].y) ? n - 1 : n
        var started = false
        for i in 0..<last {
            let p = pts[i]
            let prev = pts[(i - 1 + last) % last]
            let next = pts[(i + 1) % last]
            let open = !closed && (i == 0 || i == last - 1)
            let v1x = prev.x - p.x, v1y = prev.y - p.y
            let v2x = next.x - p.x, v2y = next.y - p.y
            let l1 = hypotf(v1x, v1y), l2 = hypotf(v2x, v2y)
            let cos: Float = (l1 < 1e-3 || l2 < 1e-3) ? 1 : (v1x * v2x + v1y * v2y) / (l1 * l2)
            if open || cos < -CORNER_COS || cos > CORNER_COS || l1 < 1e-3 || l2 < 1e-3 {
                if !started { path.move(to: pt(p.x, p.y)); started = true } else { path.addLine(to: pt(p.x, p.y)) }
                continue
            }
            let d = min(radius, min(l1, l2) / 2)
            let a = pt(p.x + v1x / l1 * d, p.y + v1y / l1 * d)
            let b = pt(p.x + v2x / l2 * d, p.y + v2y / l2 * d)
            if !started { path.move(to: a); started = true } else { path.addLine(to: a) }
            path.addQuadCurve(to: b, control: pt(p.x, p.y))
        }
        if closed { path.closeSubpath() }
    }

    /// Android `paint.alpha = (x * 255).toInt()`.
    private static func alpha255(_ v: Float) -> Int { Int(v * 255) }

    /// Şekli çizer: önce dolgu, sonra kenar (kesik alt yollar kesik çizgiyle). Bağlam y ekseni aşağı bakar.
    static func draw(_ ctx: CGContext, _ stroke: Stroke, offsetX: Float = 0, offsetY: Float = 0, coverage: Bool = false, cache useCache: Bool = true) {
        guard let spec = stroke.shape, stroke.points.count >= 2 else { return }
        let cached = build(stroke, spec, useCache: useCache)
        ctx.saveGState()
        defer { ctx.restoreGState() }
        if offsetX != 0 || offsetY != 0 { ctx.translateBy(x: CGFloat(offsetX), y: CGFloat(offsetY)) }
        // Görünür alanın tamamen dışındaki şekiller çizilmez
        let clip = ctx.boundingBoxOfClipPath
        if !clip.isNull && !clip.isInfinite {
            let pad = spec.style.strokeWidth + 2
            if CGFloat(cached.right + pad) < clip.minX || CGFloat(cached.left - pad) > clip.maxX ||
                CGFloat(cached.bottom + pad) < clip.minY || CGFloat(cached.top - pad) > clip.maxY { return }
        }
        let alpha: Float = coverage ? 1 : min(max(stroke.opacity, 0), 1)
        let style = spec.style
        let baseWidth = max(0.2, style.strokeWidth)
        for item in cached.items {
            let ink = (item.stroke != nil && item.stroke != INK) ? item.stroke! : style.strokeColorHex
            if item.fill {
                let hex = resolveFill(item.ownFill, style, ink: ink)
                if hex != nil || coverage {
                    let opacity: Float = item.ownFill != nil ? 1 : min(max(style.fillOpacity, 0), 1)
                    if !coverage, item.ownFill == nil, let g = style.fillGradient, g.valid {
                        ctx.saveGState()
                        ctx.addPath(item.path)
                        ctx.clip()
                        ctx.setAlpha(CGFloat(alpha255(opacity * alpha * item.alpha)) / 255)
                        Gradients.fill(ctx, g, l: cached.left, t: cached.top, r: cached.right, b: cached.bottom)
                        ctx.restoreGState()
                    } else {
                        let c = coverage ? ArgbColor.black : ArgbColor.hex(hex!)
                        ctx.setFillColor(ArgbColor.cgColor(c, alpha: alpha255(coverage ? 1 : opacity * alpha * item.alpha)))
                        ctx.addPath(item.path)
                        ctx.fillPath()
                    }
                }
            }
            if item.widthScale <= 0 { continue }
            let width = baseWidth * item.widthScale
            ctx.setLineWidth(CGFloat(max(0.2, width)))
            ctx.setLineCap(.round)
            ctx.setLineJoin(.round)
            let c = coverage ? ArgbColor.black : ArgbColor.hex(ink)
            ctx.setStrokeColor(ArgbColor.cgColor(c, alpha: alpha255(coverage ? 1 : alpha * item.alpha)))
            if item.dotted {
                ctx.setLineDash(phase: 0, lengths: [0.01, CGFloat(max(width * 2.2, 5))])
            } else if item.dashed {
                let on = max(width * 3, 9)
                ctx.setLineDash(phase: 0, lengths: [CGFloat(on), CGFloat(on * 0.7)])
            } else {
                ctx.setLineDash(phase: 0, lengths: [])
            }
            ctx.addPath(item.path)
            ctx.strokePath()
        }
        ctx.setLineDash(phase: 0, lengths: [])
        drawLabels(ctx, stroke, spec, alpha: alpha, coverage: coverage)
    }

    /// Eksen etiketleri ve formül yazısı (tek noktalık alt yollar).
    private static func drawLabels(_ ctx: CGContext, _ stroke: Stroke, _ spec: ShapeSpec, alpha: Float, coverage: Bool) {
        guard spec.subpaths.contains(where: { $0.label != nil }) else { return }
        UIGraphicsPushContext(ctx)
        defer { UIGraphicsPopContext() }
        var start = 0
        for sub in spec.subpaths {
            let end = min(stroke.points.count, start + sub.count)
            if let text = sub.label, end > start, !text.isBlank {
                let p = stroke.points[start]
                let color: UInt32
                let a: Int
                if coverage {
                    color = ArgbColor.black; a = 255
                } else {
                    let own = sub.strokeColorHex
                    color = ArgbColor.hex((own != nil && own != INK) ? own! : spec.style.strokeColorHex)
                    a = alpha255(alpha * min(max(sub.alpha ?? 1, 0), 1))
                }
                let size = CGFloat(max(4, sub.labelSize))
                let font = UIFont.systemFont(ofSize: size)
                // Android'de taban çizgisi y + 0.82·boy; UIKit üst kenardan çizer
                let baseline = CGFloat(p.y) + size * 0.82
                let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor(cgColor: ArgbColor.cgColor(color, alpha: a))]
                (text as NSString).draw(at: CGPoint(x: CGFloat(p.x), y: baseline - font.ascender), withAttributes: attrs)
            }
            start = end
        }
    }

    /// Şeklin kapladığı en geniş alan payı (kenar kalınlığının yarısı).
    static func halfWidth(_ stroke: Stroke) -> Float { max(0.5, (stroke.shape?.style.strokeWidth ?? 1) / 2) }

    /// Noktanın şekle uzaklığı (kenara ya da dolgunun içinde 0).
    static func distanceTo(_ stroke: Stroke, _ point: P) -> Float {
        guard let spec = stroke.shape else { return .greatestFiniteMagnitude }
        var best = Float.greatestFiniteMagnitude
        for (i, range) in ranges(spec, stroke.points.count).enumerated() {
            let pts = stroke.points[range]
            let sub = i < spec.subpaths.count ? spec.subpaths[i] : nil
            var list = Array(pts)
            if sub?.closed == true && pts.count > 2 { list.append(pts.first!) }
            best = min(best, Geometry.distanceToPolyline(point, list))
            if best <= 0 { return 0 }
            if let sub, fills(sub, pts, strokeWidth: spec.style.strokeWidth), resolveFill(sub.fillColorHex, spec.style) != nil,
               Geometry.insidePolygon(point, Array(pts)) { return 0 }
        }
        return best
    }
}

/// Düzlem geometrisi yardımcıları (Android `ElementOps` ve `ShapeRenderer` içindekiler).
enum Geometry {
    static func distanceToSegment(_ p: P, _ a: StrokePoint, _ b: StrokePoint) -> Float {
        let dx = b.x - a.x, dy = b.y - a.y
        let len2 = dx * dx + dy * dy
        if len2 < 1e-6 { return hypotf(p.x - a.x, p.y - a.y) }
        let t = min(max(((p.x - a.x) * dx + (p.y - a.y) * dy) / len2, 0), 1)
        return hypotf(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    static func distanceToPolyline(_ p: P, _ pts: [StrokePoint]) -> Float {
        guard let first = pts.first else { return .greatestFiniteMagnitude }
        if pts.count == 1 { return hypotf(p.x - first.x, p.y - first.y) }
        var best = Float.greatestFiniteMagnitude
        for i in 1..<pts.count { best = min(best, distanceToSegment(p, pts[i - 1], pts[i])) }
        return best
    }

    static func insidePolygon(_ p: P, _ pts: [StrokePoint]) -> Bool {
        var inside = false
        var j = pts.count - 1
        for i in pts.indices {
            let a = pts[i], b = pts[j]
            if (a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y + 1e-6) + a.x { inside.toggle() }
            j = i
        }
        return inside
    }
}
