import CoreGraphics
import CoreText
import Foundation

/// Formül elemanının çizimi (Android `FormulaRenderer`): formül sabit boyutta dizilir, çizim komutları elemanın
/// dörtgenine eşlenir. Formül yazıdır: her boyutta keskin; düzenlenebilir yuvalar istenirse vurgulanır.
enum FormulaRenderer {
    static let LAYOUT_SIZE: Float = 100
    /// Kutunun her yanındaki pay (em oranı): italik taşmaları kesilmesin.
    static let PAD: Float = 0.18

    // MARK: Yazı ölçüleri

    /// Android `Typeface.SERIF` karşılığı: Times New Roman (eksik simgeler CoreText yedeğinden gelir).
    private static let fontNames: [GlyphStyle: String] = [
        .ROMAN: "TimesNewRomanPSMT", .ITALIC: "TimesNewRomanPS-ItalicMT",
        .BOLD: "TimesNewRomanPS-BoldMT", .BOLD_ITALIC: "TimesNewRomanPS-BoldItalicMT",
    ]

    private static let lock = NSLock()
    private static var fonts: [String: CTFont] = [:]
    private static var layouts: [String: FormulaLayout] = [:]

    static func font(_ style: GlyphStyle, _ size: Float) -> CTFont {
        let key = "\(style.rawValue)|\(size)"
        lock.lock(); defer { lock.unlock() }
        if let f = fonts[key] { return f }
        if fonts.count > 512 { fonts.removeAll() }
        let f = CTFontCreateWithName(fontNames[style]! as CFString, CGFloat(size), nil)
        fonts[key] = f
        return f
    }

    static func line(_ text: String, _ size: Float, _ style: GlyphStyle) -> CTLine {
        let attrs: [NSAttributedString.Key: Any] = [
            NSAttributedString.Key(kCTFontAttributeName as String): font(style, size),
            NSAttributedString.Key(kCTForegroundColorFromContextAttributeName as String): true,
        ]
        return CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attrs))
    }

    struct Metrics: GlyphMetrics {
        func advance(_ text: String, _ size: Float, _ style: GlyphStyle) -> Float {
            Float(CTLineGetTypographicBounds(FormulaRenderer.line(text, size, style), nil, nil, nil))
        }
        func ascent(_ size: Float) -> Float { Float(CTFontGetAscent(FormulaRenderer.font(.ROMAN, size))) }
        func descent(_ size: Float) -> Float { Float(CTFontGetDescent(FormulaRenderer.font(.ROMAN, size))) }
        /// Android `getTextBounds` gibi tamsayıya dışa yuvarlanmış mürekkep kutusu (y aşağı).
        func inkBounds(_ text: String, _ size: Float, _ style: GlyphStyle) -> [Float]? {
            let b = CTLineGetBoundsWithOptions(FormulaRenderer.line(text, size, style), .useGlyphPathBounds)
            if b.isNull || b.width <= 0 || b.height <= 0 { return nil }
            return [Float(b.minX.rounded(.down)), Float((-b.maxY).rounded(.down)), Float(b.maxX.rounded(.up)), Float((-b.minY).rounded(.up))]
        }
    }

    /// Kaynağın dizgisi (önbellekli).
    static func layout(_ source: String) -> FormulaLayout {
        lock.lock()
        if let l = layouts[source] { lock.unlock(); return l }
        lock.unlock()
        let l = Formula.layout(source, LAYOUT_SIZE, Metrics())
        lock.lock()
        if layouts.count >= 256 { layouts.removeAll() }
        layouts[source] = l
        lock.unlock()
        return l
    }

    /// Dizginin paylı kutusu (dizgi birimi).
    static func box(_ l: FormulaLayout) -> CGRect {
        let pad = LAYOUT_SIZE * PAD
        return CGRect(x: CGFloat(-pad), y: CGFloat(-l.ascent - pad), width: CGFloat(l.width + 2 * pad), height: CGFloat(l.ascent + l.descent + 2 * pad))
    }

    /// [size] yazı boyutunda formülün dörtgeni (sol-üst, sağ-üst, sağ-alt, sol-alt).
    static func quadAt(_ source: String, _ size: Float, _ left: Float, _ top: Float) -> [StrokePoint] {
        let b = box(layout(source))
        let k = size / LAYOUT_SIZE
        let w = Float(b.width) * k, h = Float(b.height) * k
        return [StrokePoint(left, top), StrokePoint(left + w, top), StrokePoint(left + w, top + h), StrokePoint(left, top + h)]
    }

    /// Kaynak değişince dörtgen yeni dizgiye göre yeniden ölçülür: sol-üst köşe, dönüş ve ölçek korunur.
    static func refit(_ stroke: Stroke, _ source: String) -> [StrokePoint] {
        let old = stroke.formula?.source ?? source
        let ob = box(layout(old)), nb = box(layout(source))
        if stroke.points.count < 4 { return stroke.points }
        let p0 = stroke.points[0], p1 = stroke.points[1], p3 = stroke.points[3]
        let ow = Float(ob.width), oh = Float(ob.height)
        let ux = (p1.x - p0.x) / ow, uy = (p1.y - p0.y) / ow, vx = (p3.x - p0.x) / oh, vy = (p3.y - p0.y) / oh
        let w = Float(nb.width), h = Float(nb.height)
        func at(_ a: Float, _ b: Float) -> StrokePoint { StrokePoint(p0.x + ux * a + vx * b, p0.y + uy * a + vy * b) }
        return [at(0, 0), at(w, 0), at(w, h), at(0, h)]
    }

    /// Dizgi kutusundan elemanın dörtgenine (0, 1 ve 3. köşeden) dönüşüm.
    static func matrix(_ stroke: Stroke, _ l: FormulaLayout) -> CGAffineTransform? {
        guard stroke.points.count >= 4 else { return nil }
        let b = box(l)
        guard b.width > 0, b.height > 0 else { return nil }
        let p0 = stroke.points[0], p1 = stroke.points[1], p3 = stroke.points[3]
        let a = CGFloat(p1.x - p0.x) / b.width, bb = CGFloat(p1.y - p0.y) / b.width
        let c = CGFloat(p3.x - p0.x) / b.height, d = CGFloat(p3.y - p0.y) / b.height
        let m = CGAffineTransform(a: a, b: bb, c: c, d: d, tx: CGFloat(p0.x), ty: CGFloat(p0.y))
        if abs(a * d - bb * c) < 1e-9 { return nil }
        return CGAffineTransform(translationX: -b.minX, y: -b.minY).concatenating(m)
    }

    /// Tuvaldeki bir noktanın dizgi koordinatı.
    static func toLayout(_ stroke: Stroke, _ x: Float, _ y: Float) -> CGPoint? {
        guard let source = stroke.formula?.source, let m = matrix(stroke, layout(source)) else { return nil }
        return CGPoint(x: CGFloat(x), y: CGFloat(y)).applying(m.inverted())
    }

    /// Yuvanın tuvaldeki dörtgeni.
    static func slotQuad(_ stroke: Stroke, _ name: String) -> [StrokePoint]? {
        guard let source = stroke.formula?.source else { return nil }
        let l = layout(source)
        guard let r = l.slots.first(where: { $0.name == name }), let m = matrix(stroke, l) else { return nil }
        let pad = LAYOUT_SIZE * 0.04
        let pts = [(r.left - pad, r.top - pad), (r.right + pad, r.top - pad), (r.right + pad, r.bottom + pad), (r.left - pad, r.bottom + pad)]
        return pts.map { p in
            let q = CGPoint(x: CGFloat(p.0), y: CGFloat(p.1)).applying(m)
            return StrokePoint(Float(q.x), Float(q.y))
        }
    }

    /// Dokunulan yuvanın adı.
    static func slotAt(_ stroke: Stroke, _ x: Float, _ y: Float) -> String? {
        guard let source = stroke.formula?.source, let q = toLayout(stroke, x, y) else { return nil }
        let pad = LAYOUT_SIZE * 0.08
        let qx = Float(q.x), qy = Float(q.y)
        return layout(source).slots.first { qx >= $0.left - pad && qx <= $0.right + pad && qy >= $0.top - pad && qy <= $0.bottom + pad }?.name
    }

    private static func slotRect(_ r: SlotRegion) -> CGPath {
        let pad = CGFloat(LAYOUT_SIZE * 0.04), rad = CGFloat(LAYOUT_SIZE * 0.06)
        let rect = CGRect(x: CGFloat(r.left) - pad, y: CGFloat(r.top) - pad, width: CGFloat(r.right - r.left) + 2 * pad, height: CGFloat(r.bottom - r.top) + 2 * pad)
        return CGPath(roundedRect: rect, cornerWidth: min(rad, rect.width / 2), cornerHeight: min(rad, rect.height / 2), transform: nil)
    }

    /// Yalnızca düzenlenebilir yuvaların vurgusu (seçili formülün üstüne).
    static func drawSlots(_ c: CGContext, _ stroke: Stroke, activeSlot: String?, color: UInt32) {
        guard let source = stroke.formula?.source else { return }
        let l = layout(source)
        guard !l.slots.isEmpty, let m = matrix(stroke, l) else { return }
        c.saveGState()
        c.concatenate(m)
        for r in l.slots {
            let p = slotRect(r)
            let on = r.name == activeSlot
            c.setFillColor(ArgbColor.cgColor(color, alpha: on ? 70 : 22))
            c.addPath(p); c.fillPath()
            c.setStrokeColor(ArgbColor.cgColor(color, alpha: on ? 220 : 60))
            c.setLineWidth(CGFloat(LAYOUT_SIZE * 0.012))
            c.addPath(p); c.strokePath()
        }
        c.restoreGState()
    }

    /// Formülü çizer; [coverage] silinme denetimi için tam opak siyah.
    static func draw(_ c: CGContext, _ stroke: Stroke, color: UInt32, highlightSlots: Bool = false, activeSlot: String? = nil,
                     highlightColor: UInt32 = 0xFF4F_6BED, coverage: Bool = false) {
        guard let source = stroke.formula?.source else { return }
        let clip = c.boundingBoxOfClipPath
        if !clip.isNull && !clip.isInfinite, let r = StrokeRenderer.paintBounds(stroke), !clip.intersects(r) { return }
        let l = layout(source)
        guard let m = matrix(stroke, l) else { return }
        c.saveGState()
        c.concatenate(m)
        let alpha = coverage ? 255 : Int(min(max(stroke.opacity, 0), 1) * 255)
        let ink = ArgbColor.cgColor(coverage ? ArgbColor.black : color, alpha: alpha)
        if highlightSlots && !coverage {
            for r in l.slots {
                c.setFillColor(ArgbColor.cgColor(highlightColor, alpha: r.name == activeSlot ? 90 : 38))
                c.addPath(slotRect(r)); c.fillPath()
            }
        }
        c.setFillColor(ink)
        c.setStrokeColor(ink)
        for op in l.ops {
            switch op {
            case let .glyph(text, x, y, size, style, _):
                c.saveGState()
                c.translateBy(x: CGFloat(x), y: CGFloat(y))
                c.scaleBy(x: 1, y: -1)
                c.textMatrix = .identity
                c.textPosition = .zero
                CTLineDraw(line(text, size, style), c)
                c.restoreGState()
            case let .rule(x1, y1, x2, y2, th):
                c.setLineWidth(CGFloat(max(0.5, th)))
                c.setLineCap(.butt)
                c.move(to: CGPoint(x: CGFloat(x1), y: CGFloat(y1)))
                c.addLine(to: CGPoint(x: CGFloat(x2), y: CGFloat(y2)))
                c.strokePath()
            case let .stroke(pts, th, closed, filled):
                if pts.count < 4 { continue }
                c.move(to: CGPoint(x: CGFloat(pts[0]), y: CGFloat(pts[1])))
                var i = 2
                while i + 1 < pts.count { c.addLine(to: CGPoint(x: CGFloat(pts[i]), y: CGFloat(pts[i + 1]))); i += 2 }
                if closed || filled { c.closePath() }
                if filled { c.fillPath() } else {
                    c.setLineWidth(CGFloat(max(0.5, th)))
                    c.setLineCap(.round); c.setLineJoin(.round)
                    c.strokePath()
                }
            }
        }
        c.restoreGState()
    }
}
