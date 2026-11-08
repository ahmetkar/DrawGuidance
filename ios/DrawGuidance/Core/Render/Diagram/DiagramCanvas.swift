import CoreGraphics
import CoreText
import Foundation
import UIKit

/// Android `Canvas`/`Paint`/`Path`/`RectF` çizim modelinin CGContext üstündeki küçük karşılığı (diyagram çizicisini
/// Android sürümüyle satır satır aynı tutmak için). Koordinatlar y aşağı.

struct RectF: Equatable {
    var left: Float, top: Float, right: Float, bottom: Float
    init(_ left: Float, _ top: Float, _ right: Float, _ bottom: Float) {
        self.left = left; self.top = top; self.right = right; self.bottom = bottom
    }
    init(_ r: RectF) { self = r }
    func width() -> Float { right - left }
    func height() -> Float { bottom - top }
    func centerX() -> Float { (left + right) / 2 }
    func centerY() -> Float { (top + bottom) / 2 }
    mutating func offset(_ dx: Float, _ dy: Float) { left += dx; right += dx; top += dy; bottom += dy }
    mutating func set(_ l: Float, _ t: Float, _ r: Float, _ b: Float) { left = l; top = t; right = r; bottom = b }
    var cg: CGRect { CGRect(x: CGFloat(left), y: CGFloat(top), width: CGFloat(right - left), height: CGFloat(bottom - top)) }
}

/// Android `Path`: işlemler kaydedilir (köşe yuvarlatma için düz çizgi zincirleri ayrıca saklanır).
final class APath {
    private(set) var cg = CGMutablePath()
    /// Yalnız moveTo/lineTo'dan oluşuyorsa alt yolların noktaları (köşe yuvarlatma için); başka işlem gelirse nil.
    private var polylines: [[CGPoint]]? = []
    private var closedFlags: [Bool] = []

    init() {}
    init(_ other: APath) {
        cg = other.cg.mutableCopy() ?? CGMutablePath()
        polylines = other.polylines
        closedFlags = other.closedFlags
    }

    var isEmpty: Bool { cg.isEmpty }

    private func pt(_ x: Float, _ y: Float) -> CGPoint { CGPoint(x: CGFloat(x), y: CGFloat(y)) }

    func moveTo(_ x: Float, _ y: Float) {
        cg.move(to: pt(x, y))
        polylines?.append([pt(x, y)]); closedFlags.append(false)
    }

    func lineTo(_ x: Float, _ y: Float) {
        if cg.isEmpty { moveTo(0, 0) }
        cg.addLine(to: pt(x, y))
        if polylines != nil, !polylines!.isEmpty { polylines![polylines!.count - 1].append(pt(x, y)) }
    }

    func cubicTo(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float, _ x3: Float, _ y3: Float) {
        if cg.isEmpty { moveTo(0, 0) }
        cg.addCurve(to: pt(x3, y3), control1: pt(x1, y1), control2: pt(x2, y2))
        polylines = nil
    }

    func quadTo(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float) {
        if cg.isEmpty { moveTo(0, 0) }
        cg.addQuadCurve(to: pt(x2, y2), control: pt(x1, y1))
        polylines = nil
    }

    func close() {
        if cg.isEmpty { return }
        cg.closeSubpath()
        if !closedFlags.isEmpty { closedFlags[closedFlags.count - 1] = true }
    }

    func addRect(_ r: RectF) { cg.addRect(r.cg); polylines = nil }

    func addRoundRect(_ r: RectF, _ rx: Float, _ ry: Float) {
        let w = CGFloat(max(0, r.width())), h = CGFloat(max(0, r.height()))
        let cw = min(CGFloat(max(0, rx)), w / 2), ch = min(CGFloat(max(0, ry)), h / 2)
        if cw <= 0 || ch <= 0 { cg.addRect(r.cg) } else { cg.addRoundedRect(in: r.cg, cornerWidth: cw, cornerHeight: ch) }
        polylines = nil
    }

    func addOval(_ r: RectF) { cg.addEllipse(in: r.cg); polylines = nil }

    /// Android `arcTo(oval, startAngle, sweepAngle)` (derece, saat yönü artı; boş yolda moveTo, değilse lineTo ile).
    func arcTo(_ r: RectF, _ start: Float, _ sweep: Float) {
        let cx = CGFloat(r.centerX()), cy = CGFloat(r.centerY())
        let rx = CGFloat(r.width() / 2), ry = CGFloat(r.height() / 2)
        let a0 = CGFloat(start) * .pi / 180, a1 = CGFloat(start + sweep) * .pi / 180
        let sp = CGPoint(x: cx + rx * cos(a0), y: cy + ry * sin(a0))
        if cg.isEmpty { cg.move(to: sp) } else { cg.addLine(to: sp) }
        if abs(rx) > 1e-6 && abs(ry) > 1e-6 {
            let t = CGAffineTransform(translationX: cx, y: cy).scaledBy(x: rx, y: ry)
            cg.addArc(center: .zero, radius: 1, startAngle: a0, endAngle: a1, clockwise: sweep < 0, transform: t)
        }
        polylines = nil
    }

    /// Android `CornerPathEffect(radius)`: düz çizgi zincirinin köşeleri yuvarlatılmış kopyası.
    func rounded(_ radius: Float) -> CGPath {
        guard radius > 0, let lines = polylines else { return cg }
        let out = CGMutablePath()
        let r = CGFloat(radius)
        for (li, pts) in lines.enumerated() {
            guard let first = pts.first else { continue }
            let closed = li < closedFlags.count && closedFlags[li]
            if pts.count < 3 {
                out.move(to: first)
                for q in pts.dropFirst() { out.addLine(to: q) }
                if closed { out.closeSubpath() }
                continue
            }
            out.move(to: first)
            for i in 1..<(pts.count - 1) {
                let a = pts[i - 1], b = pts[i], c = pts[i + 1]
                let l1 = hypot(b.x - a.x, b.y - a.y), l2 = hypot(c.x - b.x, c.y - b.y)
                if l1 < 1e-6 || l2 < 1e-6 { out.addLine(to: b); continue }
                let d1 = min(r, l1 / 2), d2 = min(r, l2 / 2)
                let p1 = CGPoint(x: b.x - (b.x - a.x) / l1 * d1, y: b.y - (b.y - a.y) / l1 * d1)
                let p2 = CGPoint(x: b.x + (c.x - b.x) / l2 * d2, y: b.y + (c.y - b.y) / l2 * d2)
                out.addLine(to: p1)
                out.addQuadCurve(to: p2, control: b)
            }
            out.addLine(to: pts[pts.count - 1])
            if closed { out.closeSubpath() }
        }
        return out
    }
}

enum PaintStyle { case FILL, STROKE }

/// Android `Paint`/`TextPaint`'in kullanılan kısmı.
struct APaint {
    var style: PaintStyle = .FILL
    var color: UInt32 = 0xFF00_0000
    var strokeWidth: Float = 1
    var cap: CGLineCap = .butt
    var join: CGLineJoin = .miter
    var dash: [Float]? = nil
    var corner: Float = 0
    // yazı
    var face: DiagramFace = .sans
    var textSize: Float = 12
    var letterSpacing: Float = 0

    mutating func reset() { self = APaint() }

    /// Android `paint.alpha = a` (rengin saydamlığını değiştirir).
    var alpha: Int {
        get { Int(color >> 24) }
        set { color = (color & 0x00FF_FFFF) | UInt32(max(0, min(255, newValue))) << 24 }
    }
}

/// Diyagram yazı tipleri (Android sans-serif, sans-serif-medium, monospace).
enum DiagramFace: Hashable {
    case sans, medium, mono

    private static var cache: [String: CTFont] = [:]
    private static let lock = NSLock()

    func font(_ size: Float) -> CTFont {
        let s = CGFloat(max(0.5, size))
        let key = "\(self)|\(Int((s * 16).rounded()))"
        DiagramFace.lock.lock(); defer { DiagramFace.lock.unlock() }
        if let f = DiagramFace.cache[key] { return f }
        let ui: UIFont
        switch self {
        case .sans: ui = .systemFont(ofSize: s, weight: .regular)
        case .medium: ui = .systemFont(ofSize: s, weight: .medium)
        case .mono: ui = .monospacedSystemFont(ofSize: s, weight: .regular)
        }
        let f = ui as CTFont
        if DiagramFace.cache.count > 512 { DiagramFace.cache.removeAll() }
        DiagramFace.cache[key] = f
        return f
    }

    func line(_ text: String, _ size: Float, _ spacing: Float, color: CGColor? = nil) -> CTLine {
        var attrs: [NSAttributedString.Key: Any] = [.font: font(size)]
        if spacing != 0 { attrs[.kern] = CGFloat(spacing * size) }
        if let color { attrs[.foregroundColor] = color }
        return CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attrs))
    }

    /// Android `measureText` karşılığı (ilerleme genişliği).
    func measure(_ text: String, _ size: Float, _ spacing: Float) -> Float {
        if text.isEmpty { return 0 }
        return Float(CTLineGetTypographicBounds(line(text, size, spacing), nil, nil, nil))
    }
}

/// Android `Canvas`'ın kullanılan kısmı.
final class ACanvas {
    let c: CGContext
    private var depth = 0

    init(_ c: CGContext) { self.c = c }

    @discardableResult
    func save() -> Int { c.saveGState(); depth += 1; return depth - 1 }

    func restoreToCount(_ n: Int) { while depth > n { c.restoreGState(); depth -= 1 } }

    func translate(_ dx: Float, _ dy: Float) { c.translateBy(x: CGFloat(dx), y: CGFloat(dy)) }
    func scale(_ sx: Float, _ sy: Float) { c.scaleBy(x: CGFloat(sx), y: CGFloat(sy)) }
    func concat(_ t: CGAffineTransform) { c.concatenate(t) }
    func clipPath(_ path: APath) { c.addPath(path.cg); c.clip() }
    func clipRect(_ l: Float, _ t: Float, _ r: Float, _ b: Float) { c.clip(to: RectF(l, t, r, b).cg) }

    private func apply(_ p: APaint) {
        let col = ArgbColor.cgColor(p.color)
        if p.style == .FILL {
            c.setFillColor(col)
        } else {
            c.setStrokeColor(col)
            c.setLineWidth(CGFloat(p.strokeWidth))
            c.setLineCap(p.cap)
            c.setLineJoin(p.join)
            if let d = p.dash { c.setLineDash(phase: 0, lengths: d.map { CGFloat($0) }) } else { c.setLineDash(phase: 0, lengths: []) }
        }
    }

    private func draw(_ path: CGPath, _ p: APaint) {
        if p.color >> 24 == 0 { return }
        c.saveGState()
        apply(p)
        c.addPath(path)
        if p.style == .FILL { c.fillPath() } else { c.strokePath() }
        c.restoreGState()
    }

    func drawPath(_ path: APath, _ p: APaint) {
        draw(p.style == .STROKE && p.corner > 0 ? path.rounded(p.corner) : path.cg, p)
    }

    func drawRect(_ r: RectF, _ p: APaint) { draw(CGPath(rect: r.cg, transform: nil), p) }
    func drawRect(_ l: Float, _ t: Float, _ r: Float, _ b: Float, _ p: APaint) { drawRect(RectF(l, t, r, b), p) }

    func drawRoundRect(_ r: RectF, _ rx: Float, _ ry: Float, _ p: APaint) {
        let path = APath(); path.addRoundRect(r, rx, ry); draw(path.cg, p)
    }

    func drawOval(_ r: RectF, _ p: APaint) { draw(CGPath(ellipseIn: r.cg, transform: nil), p) }

    func drawLine(_ x0: Float, _ y0: Float, _ x1: Float, _ y1: Float, _ p: APaint) {
        let path = CGMutablePath()
        path.move(to: CGPoint(x: CGFloat(x0), y: CGFloat(y0)))
        path.addLine(to: CGPoint(x: CGFloat(x1), y: CGFloat(y1)))
        var q = p; q.style = .STROKE
        draw(path, q)
    }

    /// Yazıyı taban çizgisi ([x], [y]) solundan çizer.
    func drawText(_ text: String, _ x: Float, _ y: Float, _ p: APaint) {
        if text.isEmpty || p.color >> 24 == 0 { return }
        let line = p.face.line(text, p.textSize, p.letterSpacing, color: ArgbColor.cgColor(p.color))
        c.saveGState()
        c.translateBy(x: CGFloat(x), y: CGFloat(y))
        c.scaleBy(x: 1, y: -1)
        c.textMatrix = .identity
        c.textPosition = .zero
        CTLineDraw(line, c)
        c.restoreGState()
    }
}
