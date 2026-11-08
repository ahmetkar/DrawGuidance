import CoreGraphics
import Foundation

/// Kılavuz ve perspektif çizgileri (Android `GuideRenderer`): kağıdın üstünde, çizimlerin altında. [paper] kağıdın
/// bu bağlamdaki dikdörtgeni, [scale] tuval biriminin bu bağlamdaki boyu (aralık tuval biriminde saklanır).
enum GuideRenderer {
    /// Kaçış noktalarından çıkan ışınların uzunluğu (kağıt köşegeninin katı).
    private static let RAY_REACH: CGFloat = 2.4

    static func draw(_ c: CGContext, _ spec: GuideSpec, paper: CGRect, scale: Float, highlightVanish: Bool = false) {
        let width = CGFloat(max(0.7, 1.1 * min(1.5, max(0.6, scale))))
        let base = ArgbColor.parse(spec.colorHex) ?? 0xFF7C_89A6
        c.saveGState()
        defer { c.restoreGState() }
        c.clip(to: paper)
        c.setLineCap(.butt)
        if spec.kind != .NONE && spec.opacity > 0.01 {
            let color = withAlpha(base, CGFloat(clampF(spec.opacity, 0, 1)))
            let step = CGFloat(max(6, spec.spacing * scale))
            switch spec.kind {
            case .GRID: grid(c, paper, step, color, width)
            case .RULED: ruled(c, paper, step, color, width)
            case .DOTS: dots(c, paper, step, color, width)
            case .ISOMETRIC: isometric(c, paper, step, color, width)
            default: break
            }
        }
        if (1...3).contains(spec.perspective) && spec.perspectiveOpacity > 0.01 {
            perspective(c, paper, spec, base, CGFloat(clampF(spec.perspectiveOpacity, 0, 1)), width, highlightVanish)
        }
    }

    /// Kaçış noktalarının kağıttaki konumları (perspektif ayar modunda sürüklemek için).
    static func vanishingPoints(_ spec: GuideSpec, _ paper: CGRect) -> [CGPoint] {
        let all = points(spec, paper)
        switch spec.perspective {
        case 1: return [all[0]]
        case 2: return [all[0], all[1]]
        case 3: return all
        default: return []
        }
    }

    /// Dışa aktarmada kılavuzun çizilip çizilmeyeceği.
    static func exports(_ spec: GuideSpec) -> Bool { spec.includeInExport && !(spec.kind == .NONE && spec.perspective == 0) }

    private static func points(_ spec: GuideSpec, _ paper: CGRect) -> [CGPoint] {
        let horizonY = paper.minY + paper.height * CGFloat(clampF(spec.horizon, 0, 1))
        return [CGPoint(x: paper.minX + paper.width * CGFloat(clampF(spec.vanishA, -1, 2)), y: horizonY),
                CGPoint(x: paper.minX + paper.width * CGFloat(clampF(spec.vanishB, -1, 2)), y: horizonY),
                CGPoint(x: paper.minX + paper.width * 0.5, y: paper.minY + paper.height * CGFloat(clampF(spec.vanishC, -1, 2)))]
    }

    private static func withAlpha(_ argb: UInt32, _ a: CGFloat) -> CGColor {
        ArgbColor.cgColor(ArgbColor.withAlpha(argb, Int((min(a, 1) * 255).rounded())))
    }

    private static func line(_ c: CGContext, _ a: CGPoint, _ b: CGPoint, _ color: CGColor, _ w: CGFloat) {
        c.setStrokeColor(color); c.setLineWidth(w)
        c.beginPath(); c.move(to: a); c.addLine(to: b); c.strokePath()
    }

    private static func grid(_ c: CGContext, _ p: CGRect, _ step: CGFloat, _ color: CGColor, _ w: CGFloat) {
        var x = p.minX
        while x <= p.maxX { line(c, CGPoint(x: x, y: p.minY), CGPoint(x: x, y: p.maxY), color, w); x += step }
        var y = p.minY
        while y <= p.maxY { line(c, CGPoint(x: p.minX, y: y), CGPoint(x: p.maxX, y: y), color, w); y += step }
    }

    private static func ruled(_ c: CGContext, _ p: CGRect, _ step: CGFloat, _ color: CGColor, _ w: CGFloat) {
        var y = p.minY + step
        while y <= p.maxY { line(c, CGPoint(x: p.minX, y: y), CGPoint(x: p.maxX, y: y), color, w); y += step }
        // Sol kenar payı
        let margin = p.minX + p.width * 0.1
        line(c, CGPoint(x: margin, y: p.minY), CGPoint(x: margin, y: p.maxY), color.copy(alpha: color.alpha * 0.8) ?? color, w * 1.4)
    }

    private static func dots(_ c: CGContext, _ p: CGRect, _ step: CGFloat, _ color: CGColor, _ w: CGFloat) {
        let r = max(0.8, w * 1.2)
        c.setFillColor(color)
        var y = p.minY
        while y <= p.maxY {
            var x = p.minX
            while x <= p.maxX { c.fillEllipse(in: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)); x += step }
            y += step
        }
    }

    private static func isometric(_ c: CGContext, _ p: CGRect, _ step: CGFloat, _ color: CGColor, _ w: CGFloat) {
        // 30° eğimli iki yön ve dikey çizgiler
        let span = p.width
        var y = p.minY - span * 0.6
        while y <= p.maxY + span * 0.6 {
            line(c, CGPoint(x: p.minX, y: y), CGPoint(x: p.maxX, y: y + span * 0.5774), color, w)
            line(c, CGPoint(x: p.minX, y: y), CGPoint(x: p.maxX, y: y - span * 0.5774), color, w)
            y += step
        }
        var x = p.minX
        while x <= p.maxX { line(c, CGPoint(x: x, y: p.minY), CGPoint(x: x, y: p.maxY), color, w * 0.8); x += step * 2 }
    }

    private static func perspective(_ c: CGContext, _ p: CGRect, _ spec: GuideSpec, _ base: UInt32, _ alpha: CGFloat, _ w: CGFloat,
                                    _ highlight: Bool) {
        let horizonY = p.minY + p.height * CGFloat(clampF(spec.horizon, 0, 1))
        c.saveGState()
        c.setLineDash(phase: 0, lengths: [14, 10])
        line(c, CGPoint(x: p.minX, y: horizonY), CGPoint(x: p.maxX, y: horizonY), withAlpha(base, alpha * 1.6), w * 1.6)
        c.restoreGState()
        let reach = RAY_REACH * max(p.width, p.height)
        let rays = clampI(spec.rays, 6, 60)
        let color = withAlpha(base, alpha)
        for vp in vanishingPoints(spec, p) {
            for i in 0..<rays {
                let a = CGFloat.pi * CGFloat(i) / CGFloat(rays)
                let d = CGPoint(x: cos(a) * reach, y: sin(a) * reach)
                line(c, CGPoint(x: vp.x - d.x, y: vp.y - d.y), CGPoint(x: vp.x + d.x, y: vp.y + d.y), color, w)
            }
            let r = max(3, w * 3)
            c.setFillColor(withAlpha(base, alpha * 2))
            c.fillEllipse(in: CGRect(x: vp.x - r, y: vp.y - r, width: 2 * r, height: 2 * r))
            // Ayar modunda kaçış noktaları sürüklenebilir tutamak olarak belirginleşir
            if highlight {
                let h = CGRect(x: vp.x - 11, y: vp.y - 11, width: 22, height: 22)
                c.setFillColor(ArgbColor.cgColor(ArgbColor.white)); c.fillEllipse(in: h)
                c.setStrokeColor(withAlpha(base, 1)); c.setLineWidth(3); c.strokeEllipse(in: h)
            }
        }
    }
}
