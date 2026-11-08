import CoreGraphics
import CoreText
import Foundation
import UIKit

/// Yazı elemanlarının ölçümü ve çizimi (Android `TextRenderer`): yazı satırlara dizilir, düzen elemanın dörtgenine
/// (taşıma/döndürme/ölçekleme sonrası) eşlenerek çizilir.
enum TextRenderer {
    static let PADDING: Float = 0.12
    static let BOX_PADDING: Float = 0.4
    private static let TR = Locale(identifier: "tr_TR")

    /// Kurulmuş yazı düzeni: satır dizisi, genişlik ve yükseklik.
    final class Layout {
        let frame: CTFrame
        let width: CGFloat
        let height: CGFloat
        let fakeBold: Bool
        let fontSize: CGFloat
        init(frame: CTFrame, width: CGFloat, height: CGFloat, fakeBold: Bool, fontSize: CGFloat) {
            self.frame = frame; self.width = width; self.height = height; self.fakeBold = fakeBold; self.fontSize = fontSize
        }
    }

    private static var layouts: [(spec: TextSpec, size: Float, layout: Layout)] = []
    private static let lock = NSLock()

    static func effectiveSize(_ spec: TextSpec, _ sizePx: Float) -> Float { sizePx * spec.paragraph.sizeFactor }

    /// İç boşluklar (sol, üst, sağ, alt).
    static func pads(_ spec: TextSpec, _ sizePx: Float) -> [Float] {
        let size = effectiveSize(spec, sizePx)
        let base = size * PADDING
        guard let box = spec.box else { return [base, base, base, base] }
        if let b = box.balloon { return BalloonRenderer.pads(b, box.width ?? size * 8, size) }
        if let p = box.padding { return [p, p, p, p] }
        if let c = BackgroundRenderer.boxContent(box) { return c.map { max(base, Float($0) * box.patternScale) } }
        let p = box.hasBackground ? max(base, size * BOX_PADDING) : base
        return [p, p, p, p]
    }

    static func displayText(_ spec: TextSpec) -> String {
        let t = spec.content.isEmpty ? " " : spec.content
        return spec.box?.effects?.caps == true ? t.uppercased(with: TR) : t
    }

    private static func attributes(_ spec: TextSpec, _ sizePx: Float) -> ([NSAttributedString.Key: Any], CTFont, Bool) {
        let size = CGFloat(max(1, effectiveSize(spec, sizePx)))
        let bold = spec.bold || spec.paragraph.heading
        var (font, fakeBold, fakeItalic) = FontRegistry.font(spec.font, size: size, bold: bold, italic: spec.italic)
        if fakeItalic {
            var m = CGAffineTransform(a: 1, b: 0, c: 0.25, d: 1, tx: 0, ty: 0)
            font = CTFontCreateCopyWithAttributes(font, size, &m, nil)
        }
        let para = NSMutableParagraphStyle()
        switch spec.align {
        case .LEFT: para.alignment = .left
        case .CENTER: para.alignment = .center
        case .RIGHT: para.alignment = .right
        }
        para.lineBreakMode = .byWordWrapping
        var attrs: [NSAttributedString.Key: Any] = [.font: font, .paragraphStyle: para, .foregroundColor: UIColor.black]
        if spec.underline { attrs[.underlineStyle] = NSUnderlineStyle.single.rawValue }
        if let ls = spec.box?.effects?.letterSpacing, ls != 0 { attrs[.kern] = CGFloat(min(max(ls, -0.2), 1)) * size }
        return (attrs, font, fakeBold)
    }

    /// Yazının düzeni (önbellekten).
    static func layout(_ spec: TextSpec, _ sizePx: Float) -> Layout {
        lock.lock()
        if let hit = layouts.first(where: { $0.size == sizePx && $0.spec == spec }) { lock.unlock(); return hit.layout }
        lock.unlock()
        let (attrs, font, fakeBold) = attributes(spec, sizePx)
        let text = displayText(spec)
        let str = NSAttributedString(string: text, attributes: attrs)
        let setter = CTFramesetterCreateWithAttributedString(str)
        let fontSize = CTFontGetSize(font)
        let width: CGFloat
        if let bw = spec.box?.width {
            let pad = pads(spec, sizePx)
            width = (CGFloat(max(bw - pad[0] - pad[2], max(1, Float(fontSize))))).rounded(.up) - 2
        } else {
            var widest: CGFloat = 1
            for line in text.split(separator: "\n", omittingEmptySubsequences: false) {
                let l = CTLineCreateWithAttributedString(NSAttributedString(string: String(line), attributes: attrs))
                widest = max(widest, CGFloat(CTLineGetTypographicBounds(l, nil, nil, nil)).rounded(.up))
            }
            width = widest
        }
        let w = width + 2
        let fit = CTFramesetterSuggestFrameSizeWithConstraints(setter, CFRange(location: 0, length: 0), nil,
                                                               CGSize(width: w, height: .greatestFiniteMagnitude), nil)
        // Android includePad: satırların üstünde ve altında yazı tipinin taşan kısmı kadar pay
        let extra = max(0, (CTFontGetAscent(font) + CTFontGetDescent(font)) * 0.08)
        let h = (fit.height + 2 * extra).rounded(.up)
        let path = CGPath(rect: CGRect(x: 0, y: extra, width: w, height: max(fit.height + 1, 1)), transform: nil)
        let frame = CTFramesetterCreateFrame(setter, CFRange(location: 0, length: 0), path, nil)
        let l = Layout(frame: frame, width: w, height: h, fakeBold: fakeBold, fontSize: fontSize)
        lock.lock()
        layouts.append((spec, sizePx, l))
        if layouts.count > 64 { layouts.removeFirst() }
        lock.unlock()
        return l
    }

    /// Yazı kutusunun (boşluklar dahil) genişliği ve yüksekliği.
    static func measure(_ spec: TextSpec, _ sizePx: Float) -> (Float, Float) {
        if let p = spec.path { return (p.w, p.h) }
        let l = layout(spec, sizePx)
        let pad = pads(spec, sizePx)
        let w = spec.box?.width ?? (Float(l.width) + pad[0] + pad[2])
        let h = max(Float(l.height) + pad[1] + pad[3], spec.box?.height ?? 0)
        return (w, h)
    }

    /// Sol-üst köşesi [x],[y] olan, döndürülmemiş yazı kutusunun 4 köşesi.
    static func quadAt(_ spec: TextSpec, _ sizePx: Float, _ x: Float, _ y: Float) -> [StrokePoint] {
        let (w, h) = measure(spec, sizePx)
        return [StrokePoint(x, y), StrokePoint(x + w, y), StrokePoint(x + w, y + h), StrokePoint(x, y + h)]
    }

    /// Ayarları değişen yazının yeni kutusu: köşe, yön ve en/boy esnemesi korunur.
    static func refit(_ old: Stroke, _ spec: TextSpec, _ sizePx: Float) -> [StrokePoint] {
        let q = old.points
        guard let oldSpec = old.text, q.count >= 4 else { return quadAt(spec, sizePx, q.first?.x ?? 0, q.first?.y ?? 0) }
        let (ow, oh) = measure(oldSpec, old.sizePx)
        let (nw, nh) = measure(spec, sizePx)
        let ux = q[1].x - q[0].x, uy = q[1].y - q[0].y, vx = q[3].x - q[0].x, vy = q[3].y - q[0].y
        let ulen = hypotf(ux, uy), vlen = hypotf(vx, vy)
        if ulen < 1e-3 || vlen < 1e-3 { return quadAt(spec, sizePx, q[0].x, q[0].y) }
        let su = ulen / ow, sv = vlen / oh
        let ax = ux / ulen * nw * su, ay = uy / ulen * nw * su, bx = vx / vlen * nh * sv, by = vy / vlen * nh * sv
        let tl = q[0]
        return [StrokePoint(tl.x, tl.y), StrokePoint(tl.x + ax, tl.y + ay), StrokePoint(tl.x + ax + bx, tl.y + ay + by), StrokePoint(tl.x + bx, tl.y + by)]
    }

    /// Kutu (0,0)-(w,h) → elemanın dörtgeni (paralelkenar varsayımıyla afin eşleme).
    static func boxTransform(_ pts: [StrokePoint], _ w: Float, _ h: Float) -> CGAffineTransform? {
        guard pts.count >= 4, w > 0, h > 0 else { return nil }
        let p0 = pts[0], p1 = pts[1], p3 = pts[3]
        return CGAffineTransform(a: CGFloat((p1.x - p0.x) / w), b: CGFloat((p1.y - p0.y) / w),
                                 c: CGFloat((p3.x - p0.x) / h), d: CGFloat((p3.y - p0.y) / h), tx: CGFloat(p0.x), ty: CGFloat(p0.y))
    }

    /// Yazıyı elemanın dörtgenine çizer; [coverage] silinme denetimi için tam opak siyah çizer.
    static func draw(_ c: CGContext, _ stroke: Stroke, color: UInt32, coverage: Bool = false) {
        guard let spec = stroke.text, stroke.points.count >= 4, !spec.content.isEmpty else { return }
        let clip = c.boundingBoxOfClipPath
        if !clip.isNull && !clip.isInfinite {
            var bb = BoundsBuilder()
            for p in stroke.points { bb.add(p.x, p.y) }
            if !clip.intersects(bb.rect(0)) { return }
        }
        let alpha = coverage ? 255 : Int(min(max(stroke.opacity, 0), 1) * 255)
        let ink = coverage ? ArgbColor.black : ArgbColor.withAlpha(color, alpha)
        if let p = spec.path { drawOnPath(c, stroke, spec, p, ink: ink, alpha: alpha, coverage: coverage); return }
        let l = layout(spec, stroke.sizePx)
        let pad = pads(spec, stroke.sizePx)
        let (w, h) = measure(spec, stroke.sizePx)
        guard let m = boxTransform(stroke.points, w, h) else { return }
        c.saveGState()
        defer { c.restoreGState() }
        c.concatenate(m)
        let size = effectiveSize(spec, stroke.sizePx)
        if let box = spec.box {
            if let balloon = box.balloon {
                BalloonRenderer.draw(c, balloon, w, h, size, alpha: Float(alpha) / 255, coverage: coverage, gradient: box.fillGradient)
            } else {
                drawBox(c, box, w, h, alpha: Float(alpha) / 255, coverage: coverage)
            }
        }
        c.translateBy(x: CGFloat(pad[0]), y: CGFloat(pad[1]))
        let fx = coverage ? nil : spec.box?.effects
        let shadow: (CGSize, CGFloat, CGColor)? = fx?.shadowHex.flatMap { hex in
            let col = ArgbColor.hex(hex)
            let a = ArgbColor.alpha(col) * alpha / 255
            return (CGSize(width: CGFloat(fx!.shadowDx * size), height: CGFloat(fx!.shadowDy * size)),
                    CGFloat(max(0.01, fx!.shadowBlur * size)), ArgbColor.cgColor(ArgbColor.withAlpha(col, a)))
        }
        let outline = spec.outlineColorHex
        let hasOutline = outline != nil && spec.outlineWidth > 0.05
        if hasOutline {
            c.saveGState()
            if let s = shadow { setShadow(c, s) }
            c.setLineJoin(.round)
            c.setLineWidth(CGFloat(spec.outlineWidth * 2))
            c.setStrokeColor(ArgbColor.cgColor(coverage ? ArgbColor.black : ArgbColor.withAlpha(ArgbColor.hex(outline!), alpha)))
            drawFrame(c, l, mode: .stroke, color: nil)
            c.restoreGState()
        }
        c.saveGState()
        if !hasOutline, let s = shadow { setShadow(c, s) }
        if let g = fx?.gradient, g.valid {
            // Gradyan dolgu: yazının biçimi kırpma olarak kullanılır
            c.beginTransparencyLayer(auxiliaryInfo: nil)
            drawFrame(c, l, mode: .fill, color: ArgbColor.cgColor(ArgbColor.black))
            c.setBlendMode(.sourceIn)
            _ = Gradients.fill(c, g, l: 0, t: 0, r: Float(l.width), b: Float(l.height), alpha: Float(alpha) / 255)
            c.endTransparencyLayer()
        } else {
            drawFrame(c, l, mode: l.fakeBold ? .fillStroke : .fill, color: ArgbColor.cgColor(ink))
        }
        c.restoreGState()
    }

    /// Android gölge katmanı: bulanıklık yarıçapı ve kayma tuval biriminde (CG gölgesi cihaz biriminde).
    private static func setShadow(_ c: CGContext, _ s: (CGSize, CGFloat, CGColor)) {
        let m = c.ctm
        let off = CGSize(width: s.0.width, height: s.0.height).applying(CGAffineTransform(a: m.a, b: m.b, c: m.c, d: m.d, tx: 0, ty: 0))
        let scale = (m.a * m.a + m.b * m.b).squareRoot()
        c.setShadow(offset: off, blur: s.1 * scale, color: s.2)
    }

    /// Düzeni y aşağı bakan bağlamda, sol-üst köşesi (0,0) olacak biçimde çizer.
    private static func drawFrame(_ c: CGContext, _ l: Layout, mode: CGTextDrawingMode, color: CGColor?) {
        c.saveGState()
        c.translateBy(x: 0, y: l.height)
        c.scaleBy(x: 1, y: -1)
        c.textMatrix = .identity
        c.setTextDrawingMode(mode)
        if let color { c.setFillColor(color); c.setStrokeColor(color) }
        // Yapay kalın: harf kenarı yazı boyunun ~1/24'ü kalınlıkta çizilir
        if mode == .fillStroke { c.setLineWidth(max(0.5, l.fontSize / 24)); c.setLineJoin(.round) }
        // Satırlar tek tek çizilir: renk, düzenin siyah özniteliğinin yerine bağlam rengi olsun
        let lines = CTFrameGetLines(l.frame) as! [CTLine]
        var origins = [CGPoint](repeating: .zero, count: lines.count)
        CTFrameGetLineOrigins(l.frame, CFRange(location: 0, length: 0), &origins)
        let frameBox = CTFrameGetPath(l.frame).boundingBox
        for (i, line) in lines.enumerated() {
            c.textPosition = CGPoint(x: frameBox.minX + origins[i].x, y: frameBox.minY + origins[i].y)
            for run in CTLineGetGlyphRuns(line) as! [CTRun] {
                let n = CTRunGetGlyphCount(run)
                if n == 0 { continue }
                let attrs = CTRunGetAttributes(run) as NSDictionary
                let font = attrs[kCTFontAttributeName] as! CTFont
                var glyphs = [CGGlyph](repeating: 0, count: n)
                var pos = [CGPoint](repeating: .zero, count: n)
                CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
                CTRunGetPositions(run, CFRange(location: 0, length: 0), &pos)
                let base = c.textPosition
                let shifted = pos.map { CGPoint(x: $0.x + base.x, y: $0.y + base.y) }
                CTFontDrawGlyphs(font, glyphs, shifted, n, c)
                if attrs[NSAttributedString.Key.underlineStyle.rawValue] != nil, mode != .stroke {
                    let start = shifted[0].x
                    let end = base.x + CGFloat(CTRunGetTypographicBounds(run, CFRange(location: 0, length: 0), nil, nil, nil)) + pos[0].x
                    let y = base.y + CTFontGetUnderlinePosition(font)
                    c.fill(CGRect(x: start, y: y - CTFontGetUnderlineThickness(font) / 2, width: end - start, height: max(1, CTFontGetUnderlineThickness(font))))
                }
            }
        }
        c.restoreGState()
    }

    /// Yazı kutusunun arka planı (renk, gradyan, hazır resim, desen, kenar).
    static func drawBox(_ c: CGContext, _ box: TextBox, _ w: Float, _ h: Float, alpha: Float, coverage: Bool) {
        BackgroundRenderer.drawBox(c, box, w, h, alpha: alpha, coverage: coverage)
    }

    // MARK: Yol üzerinde yazı

    static func singleLine(_ spec: TextSpec) -> String {
        let t = spec.content.replacingOccurrences(of: "\n", with: " ")
        return spec.box?.effects?.caps == true ? t.uppercased(with: TR) : t
    }

    private static func pathPoints(_ p: TextPathSpec) -> [CGPoint] {
        var pts = (p.reversed ? p.points.reversed() : p.points).map { CGPoint(x: CGFloat($0.x), y: CGFloat($0.y)) }
        if p.closed, let f = pts.first { pts.append(f) }
        return pts
    }

    private static func drawOnPath(_ c: CGContext, _ stroke: Stroke, _ spec: TextSpec, _ p: TextPathSpec, ink: UInt32, alpha: Int, coverage: Bool) {
        guard p.points.count >= 2, p.w > 0, p.h > 0, let m = boxTransform(stroke.points, p.w, p.h) else { return }
        let pts = pathPoints(p)
        var cum: [CGFloat] = [0]
        for i in 1..<pts.count { cum.append(cum[i - 1] + hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)) }
        let length = cum.last!
        var (attrs, font, _) = attributes(spec, stroke.sizePx)
        attrs[.paragraphStyle] = nil
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: singleLine(spec), attributes: attrs))
        let width = CGFloat(CTLineGetTypographicBounds(line, nil, nil, nil))
        let start = CGFloat(min(max(p.start, 0), 1)) * length
        let hOff: CGFloat
        switch spec.align {
        case .LEFT: hOff = start
        case .CENTER: hOff = (length - width) / 2 + start
        case .RIGHT: hOff = length - width - start
        }
        let size = CTFontGetSize(font)
        // Yolun üstünde: taban çizgisi yolun biraz üstünde; altında: harflerin tepesi yolun biraz altında
        let vOff = p.below ? CTFontGetAscent(font) + size * 0.08 : -size * 0.12
        func at(_ d: CGFloat) -> (CGPoint, CGFloat) {
            var i = 1
            while i < cum.count - 1 && cum[i] < d { i += 1 }
            let a = pts[i - 1], b = pts[i]
            let seg = max(cum[i] - cum[i - 1], 1e-6)
            let t = (d - cum[i - 1]) / seg
            return (CGPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t), atan2(b.y - a.y, b.x - a.x))
        }
        c.saveGState()
        defer { c.restoreGState() }
        c.concatenate(m)
        let fx = coverage ? nil : spec.box?.effects
        if let hex = fx?.shadowHex {
            let col = ArgbColor.hex(hex)
            setShadow(c, (CGSize(width: CGFloat(fx!.shadowDx) * size, height: CGFloat(fx!.shadowDy) * size),
                           CGFloat(max(0.01, fx!.shadowBlur)) * size, ArgbColor.cgColor(ArgbColor.withAlpha(col, ArgbColor.alpha(col) * alpha / 255))))
        }
        func glyphs(_ mode: CGTextDrawingMode) {
            for run in CTLineGetGlyphRuns(line) as! [CTRun] {
                let n = CTRunGetGlyphCount(run)
                if n == 0 { continue }
                let runFont = (CTRunGetAttributes(run) as NSDictionary)[kCTFontAttributeName] as! CTFont
                var gl = [CGGlyph](repeating: 0, count: n), pos = [CGPoint](repeating: .zero, count: n), adv = [CGSize](repeating: .zero, count: n)
                CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &gl)
                CTRunGetPositions(run, CFRange(location: 0, length: 0), &pos)
                CTRunGetAdvances(run, CFRange(location: 0, length: 0), &adv)
                for k in 0..<n {
                    let mid = hOff + pos[k].x + adv[k].width / 2
                    if mid < 0 || mid > length { continue }
                    let (pt, ang) = at(mid)
                    c.saveGState()
                    c.translateBy(x: pt.x, y: pt.y)
                    c.rotate(by: ang)
                    c.translateBy(x: -adv[k].width / 2, y: vOff)
                    c.scaleBy(x: 1, y: -1)
                    c.textMatrix = .identity
                    c.setTextDrawingMode(mode)
                    var g = gl[k]
                    var o = CGPoint.zero
                    CTFontDrawGlyphs(runFont, &g, &o, 1, c)
                    c.restoreGState()
                }
            }
        }
        if let outline = spec.outlineColorHex, spec.outlineWidth > 0.05 {
            c.saveGState()
            c.setLineJoin(.round); c.setLineWidth(CGFloat(spec.outlineWidth * 2))
            c.setStrokeColor(ArgbColor.cgColor(coverage ? ArgbColor.black : ArgbColor.withAlpha(ArgbColor.hex(outline), alpha)))
            glyphs(.stroke)
            c.restoreGState()
            c.setShadow(offset: .zero, blur: 0, color: nil)
        }
        c.setFillColor(ArgbColor.cgColor(ink))
        glyphs(.fill)
    }
}
