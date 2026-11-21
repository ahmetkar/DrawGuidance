import CoreGraphics
import Foundation

/// v56 aşama 8A şekil görünümü (Android `V56VectorPaint`): serbest gradyan, desen ve ek dolgular, kontur biçimi ve hizası, ok uçları,
/// nesne karışım modu ve opaklık maskesi.
enum V56VectorPaint {
    private static let GRADIENT_SIZE = 48
    private static var gradients: [[GradientPoint]: CGImage] = [:]
    private static let lock = NSLock()

    static func freeGradientImage(_ points: [GradientPoint]) -> CGImage {
        lock.lock(); defer { lock.unlock() }
        if let g = gradients[points] { return g }
        let n = GRADIENT_SIZE
        var px = [UInt32](repeating: 0, count: n * n)
        for i in 0..<(n * n) {
            px[i] = V56Vector.freeGradientColor(points, (Float(i % n) + 0.5) / Float(n), (Float(i / n) + 0.5) / Float(n), parse: ArgbColor.hex)
        }
        let img = Bitmaps.image(argb: px, n, n)
        if gradients.count > 24 { gradients.removeAll() }
        gradients[points] = img
        return img
    }

    /// Serbest gradyan dolgu: yola kırpılmış, şeklin kutusuna gerilmiş.
    static func fillFreeGradient(_ ctx: CGContext, _ path: CGPath, _ points: [GradientPoint], l: Float, t: Float, r: Float, b: Float, alpha: Float) {
        ctx.saveGState()
        ctx.addPath(path); ctx.clip()
        ctx.setAlpha(CGFloat(alpha))
        ctx.interpolationQuality = .high
        Bitmaps.drawUpright(ctx, freeGradientImage(points), in: CGRect(x: CGFloat(l), y: CGFloat(t), width: CGFloat(max(r - l, 1)), height: CGFloat(max(b - t, 1))))
        ctx.restoreGState()
    }

    /// Desen dolgu (doku karosu dolgu renginde) ve ek dolgular.
    static func drawFillExtras(_ ctx: CGContext, _ path: CGPath, _ v: VectorStyle, colorHex: String, alpha: Float) {
        if let id = v.patternId, V56Textures.isBuiltin(id) {
            ctx.saveGState()
            ctx.addPath(path); ctx.clip()
            ctx.setAlpha(CGFloat(min(max(alpha, 0), 1)))
            ctx.beginTransparencyLayer(auxiliaryInfo: nil)
            ctx.setFillColor(ArgbColor.cgColor(ArgbColor.hex(colorHex)))
            ctx.addPath(path); ctx.fillPath()
            ctx.setBlendMode(.destinationIn)
            Bitmaps.tile(ctx, GrainRenderer.tile(id, strength: 1), scale: CGFloat(min(max(v.patternScale, V56Textures.MIN_SCALE), V56Textures.MAX_SCALE)))
            ctx.endTransparencyLayer()
            ctx.restoreGState()
        }
        for f in v.fills {
            ctx.setFillColor(ArgbColor.cgColor(ArgbColor.hex(f.colorHex), alpha: Int(min(max(f.opacity, 0), 1) * alpha * 255)))
            ctx.addPath(path); ctx.fillPath()
        }
    }

    static func styleStroke(_ ctx: CGContext, _ v: VectorStyle) {
        switch v.cap { case .ROUND: ctx.setLineCap(.round); case .BUTT: ctx.setLineCap(.butt); case .SQUARE: ctx.setLineCap(.square) }
        switch v.join { case .ROUND: ctx.setLineJoin(.round); case .MITER: ctx.setLineJoin(.miter); case .BEVEL: ctx.setLineJoin(.bevel) }
        ctx.setMiterLimit(CGFloat(min(max(v.miterLimit, 1), 20)))
        if let d = v.dash?.filter({ $0 > 0 }), d.count >= 2 { ctx.setLineDash(phase: 0, lengths: d.map { CGFloat($0) }) }
    }

    /// Kontur hizası: kapalı yolda iç (yola kırpılır) ya da dış (yolun dışı, çift-tek); kalınlık iki katı ([body] çarpanı alır).
    static func withAlign(_ ctx: CGContext, _ path: CGPath, closed: Bool, _ align: StrokeAlign, _ body: (CGFloat) -> Void) {
        guard closed, align != .CENTER else { body(1); return }
        ctx.saveGState()
        if align == .INSIDE {
            ctx.addPath(path); ctx.clip()
        } else {
            ctx.addRect(CGRect(x: -1e6, y: -1e6, width: 2e6, height: 2e6)); ctx.addPath(path); ctx.clip(using: .evenOdd)
        }
        body(2)
        ctx.restoreGState()
    }

    /// Açık alt yolların uçlarına ok başı.
    static func drawArrows(_ ctx: CGContext, _ stroke: Stroke, _ spec: ShapeSpec, _ v: VectorStyle, width: Float, color: UInt32, alpha: Float) {
        let size = max(width * 4, 10)
        let cg = ArgbColor.cgColor(color, alpha: Int(alpha * 255))
        ctx.saveGState()
        ctx.setLineDash(phase: 0, lengths: [])
        ctx.setLineWidth(CGFloat(width)); ctx.setLineCap(.round); ctx.setLineJoin(.round)
        ctx.setStrokeColor(cg); ctx.setFillColor(cg)
        for (i, range) in ShapeRenderer.ranges(spec, stroke.points.count).enumerated() {
            if let sub = spec.subpaths[safe: i], sub.closed || sub.label != nil { continue }
            let pts = Array(stroke.points[range])
            if pts.count < 2 { continue }
            func head(_ kind: ArrowKind, _ tip: Int, _ from: Int) {
                guard kind != .NONE else { return }
                let t = pts[tip]; var k = from; var f = pts[from]
                while hypot(t.x - f.x, t.y - f.y) < 0.5 && k > 0 && k < pts.count - 1 { k += from > tip ? 1 : -1; f = pts[k] }
                let len = hypot(t.x - f.x, t.y - f.y)
                guard len > 1e-3 else { return }
                let g = V56Vector.arrowHead(kind, t.x, t.y, (t.x - f.x) / len, (t.y - f.y) / len, size)
                func p(_ q: (Float, Float)) -> CGPoint { CGPoint(x: CGFloat(q.0), y: CGFloat(q.1)) }
                switch kind {
                case .TRIANGLE: ctx.move(to: p(g[0])); ctx.addLine(to: p(g[1])); ctx.addLine(to: p(g[2])); ctx.closePath(); ctx.fillPath()
                case .OPEN: ctx.move(to: p(g[0])); ctx.addLine(to: p(g[1])); ctx.addLine(to: p(g[2])); ctx.strokePath()
                case .CIRCLE:
                    let r = CGFloat(g[1].0)
                    ctx.fillEllipse(in: CGRect(x: CGFloat(g[0].0) - r, y: CGFloat(g[0].1) - r, width: r * 2, height: r * 2))
                case .BAR: ctx.move(to: p(g[0])); ctx.addLine(to: p(g[1])); ctx.strokePath()
                case .NONE: break
                }
            }
            head(v.arrowStart, 0, 1)
            head(v.arrowEnd, pts.count - 1, pts.count - 2)
        }
        ctx.restoreGState()
    }

    /// Nesne karışım modu ve opaklık maskesi: eleman saydamlık katmanında; maske siyah zeminde gri tonlu resme çizilip
    /// parlaklığı görünürlüğü verir (`clip(to:mask:)`).
    static func withStyled(_ ctx: CGContext, _ stroke: Stroke, _ st: VectorStyle, _ body: () -> Void) {
        ctx.saveGState()
        ctx.setBlendMode(LayerCompositor.blendMode(st.blend))
        ctx.beginTransparencyLayer(auxiliaryInfo: nil)
        ctx.setBlendMode(.normal)
        if let masks = st.opacityMask, !masks.isEmpty, let (img, rect) = maskImage(ctx, masks) {
            ctx.clip(to: rect, mask: img)
        }
        body()
        ctx.endTransparencyLayer()
        ctx.restoreGState()
    }

    private static func maskImage(_ ctx: CGContext, _ masks: [Stroke]) -> (CGImage, CGRect)? {
        let rects = masks.compactMap { StrokeRenderer.paintBounds($0) }
        guard let first = rects.first else { return nil }
        let r = rects.dropFirst().reduce(first) { $0.union($1) }.insetBy(dx: -2, dy: -2)
        let m = ctx.ctm
        let k = min(max(sqrt(abs(m.a * m.d - m.b * m.c)), 0.25), 4)
        let w = max(Int((r.width * k).rounded(.up)), 1), h = max(Int((r.height * k).rounded(.up)), 1)
        guard w * h <= 4096 * 4096,
              let g = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpaceCreateDeviceGray(),
                                bitmapInfo: CGImageAlphaInfo.none.rawValue) else { return nil }
        g.setFillColor(gray: 0, alpha: 1)
        g.fill(CGRect(x: 0, y: 0, width: w, height: h))
        // Çizim y aşağı tuval birimiyle; bit eşlemin satırları y ekseni yukarıdır, kırpma da y aşağı bağlamda yapılır
        g.scaleBy(x: k, y: k)
        g.translateBy(x: -r.minX, y: -r.minY)
        for s in masks { var ms = s; ms.style2?.opacityMask = nil; StrokeRenderer.drawStroke(g, ms) }
        guard let img = g.makeImage() else { return nil }
        return (img, r)
    }
}

private extension Array {
    subscript(safe i: Int) -> Element? { indices.contains(i) ? self[i] : nil }
}
