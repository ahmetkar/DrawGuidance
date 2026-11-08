import CoreGraphics
import Foundation

/// v27 çizgi roman konuşma balonları (Android `BalloonRenderer`): yazı kutusunun yerel düzleminde (0..w × 0..h).
/// Gövde ve kuyruklar tek yolda birleştirilir (kenar çizgisi kesintisiz); düşünce balonunda kuyruk kabarcıklardır.
enum BalloonRenderer {
    static let MIN_TAIL: Float = 0.15
    static let MAX_TAIL: Float = 1.6

    static func pads(_ b: BalloonSpec, _ width: Float, _ size: Float) -> [Float] {
        let spiky = b.style == .SHOUT || b.style == .FLASH || b.style == .ROUGH
        let oval = spiky || [.SPEECH, .WHISPER, .THOUGHT, .WAVY, .DOUBLE, .ICICLE, .DRIP].contains(b.style)
        if b.style == .FREE { return [size * 0.15, size * 0.15, size * 0.15, size * 0.15] }
        if b.style == .HEART {
            let side = max(width * 0.2, size * 0.7)
            return [side, size * 1.15, side, size * 1.9]
        }
        let side: Float = spiky ? max(width * 0.2, size * 0.6) : oval ? max(width * 0.15, size * 0.6)
            : b.style == .SCALLOP ? max(width * 0.09, size * 0.7) : max(width * 0.07, size * 0.5)
        let top: Float = (spiky || b.style == .THOUGHT) ? size * 1.1 : oval ? size * 0.85 : b.style == .SCALLOP ? size * 0.7 : size * 0.45
        let bottom = (b.style == .ICICLE || b.style == .DRIP) ? top * 1.45 : top
        return [side, top, side, bottom]
    }

    private static func pt(_ x: Float, _ y: Float) -> CGPoint { CGPoint(x: CGFloat(x), y: CGFloat(y)) }
    private static func rect(_ l: Float, _ t: Float, _ r: Float, _ b: Float) -> CGRect { CGRect(x: CGFloat(l), y: CGFloat(t), width: CGFloat(r - l), height: CGFloat(b - t)) }
    private static func circle(_ p: CGMutablePath, _ x: Float, _ y: Float, _ r: Float) { p.addEllipse(in: rect(x - r, y - r, x + r, y + r)) }

    /// Gövde (kuyruksuz) yolu.
    static func body(_ b: BalloonSpec, _ w: Float, _ h: Float) -> CGPath {
        let p = CGMutablePath()
        switch b.style {
        case .SPEECH, .WHISPER, .DOUBLE: p.addEllipse(in: rect(0, 0, w, h))
        case .ROUNDED, .ELECTRIC:
            let r = CGFloat(min(w, h) * 0.24)
            p.addRoundedRect(in: rect(0, 0, w, h), cornerWidth: r, cornerHeight: r)
        case .NARRATION, .FREE: p.addRect(rect(0, 0, w, h))
        case .THOUGHT: return cloud(w, h)
        case .SHOUT: burst(p, w, h)
        case .WAVY: wavy(p, w, h)
        case .ICICLE: icicle(p, w, h)
        case .DRIP: return drip(w, h)
        case .SCALLOP: return scallop(w, h)
        case .FLASH: radial(p, w, h, spikes: 44, inner: 0.84, jitter: 0.08)
        case .HEART: heart(p, w, h)
        case .CUT:
            let c = min(w, h) * 0.2
            p.move(to: pt(c, 0)); p.addLine(to: pt(w - c, 0)); p.addLine(to: pt(w, c)); p.addLine(to: pt(w, h - c))
            p.addLine(to: pt(w - c, h)); p.addLine(to: pt(c, h)); p.addLine(to: pt(0, h - c)); p.addLine(to: pt(0, c)); p.closeSubpath()
        case .ROUGH: rough(p, w, h)
        case .PHONE:
            let r = CGFloat(min(min(w, h) * 0.45, h / 2))
            p.addRoundedRect(in: rect(0, 0, w, h), cornerWidth: min(r, CGFloat(w) / 2), cornerHeight: r)
        }
        return p
    }

    private static var cache: [String: (CGPath, CGPath?)] = [:]
    private static let lock = NSLock()

    /// Gövde ve kuyruklar birleşik yol; düşünce balonunda kabarcıklar ikinci yol (önbellekli).
    static func outline(_ b: BalloonSpec, _ w: Float, _ h: Float) -> (CGPath, CGPath?) {
        let key = "\(b.style.rawValue)|\(b.tails.map { "\($0.x),\($0.y),\($0.curve)" })|\(Int((w * 2).rounded()))|\(Int((h * 2).rounded()))"
        lock.lock()
        if let v = cache[key] { lock.unlock(); return v }
        lock.unlock()
        let v = build(b, w, h)
        lock.lock()
        if cache.count > 64 { cache.removeAll() }
        cache[key] = v
        lock.unlock()
        return v
    }

    private static func build(_ b: BalloonSpec, _ w: Float, _ h: Float) -> (CGPath, CGPath?) {
        let bodyPath = body(b, w, h)
        if b.tails.isEmpty || w < 1 || h < 1 || b.style == .FREE { return (bodyPath, nil) }
        if b.style == .THOUGHT {
            let bubbles = CGMutablePath()
            for t in b.tails { thoughtBubbles(bubbles, t, w, h) }
            return (bodyPath, bubbles)
        }
        let tails = CGMutablePath()
        for t in b.tails { tail(tails, b.style, t, w, h) }
        return (bodyPath.union(tails), nil)
    }

    static func draw(_ c: CGContext, _ b: BalloonSpec, _ w: Float, _ h: Float, _ size: Float, alpha: Float, coverage: Bool, gradient: GradientSpec? = nil) {
        let (shape, extra) = outline(b, w, h)
        if coverage {
            c.setFillColor(ArgbColor.cgColor(ArgbColor.black))
            c.addPath(shape); c.fillPath()
            if let extra { c.addPath(extra); c.fillPath() }
            return
        }
        if b.style == .FREE { return }
        let stroke = CGFloat(max(1, b.strokeWidth * size))
        func withAlpha(_ col: UInt32) -> CGColor { ArgbColor.cgColor(ArgbColor.withAlpha(col, Int(Float(ArgbColor.alpha(col)) * min(max(alpha, 0), 1)))) }
        let fillPaths = [shape] + (extra.map { [$0] } ?? [])
        for p in fillPaths {
            c.saveGState()
            c.addPath(p); c.clip()
            if !Gradients.fill(c, gradient, l: 0, t: 0, r: w, b: h, alpha: alpha) {
                c.setFillColor(withAlpha(ArgbColor.parse(b.fillHex) ?? ArgbColor.white))
                c.addPath(p); c.fillPath()
            }
            c.restoreGState()
        }
        c.saveGState()
        c.setStrokeColor(withAlpha(ArgbColor.parse(b.strokeHex) ?? 0xFF11_1111))
        c.setLineWidth(stroke); c.setLineJoin(.round); c.setLineCap(.round)
        if b.style == .WHISPER { c.setLineDash(phase: 0, lengths: [stroke * 3, stroke * 2.2]) }
        for p in fillPaths { c.addPath(p); c.strokePath() }
        c.setLineDash(phase: 0, lengths: [])
        if b.style == .DOUBLE {
            let g = Float(stroke) * 2.4
            c.strokeEllipse(in: rect(-g, -g, w + g, h + g))
        }
        c.restoreGState()
    }

    static func tip(_ t: BalloonTail, _ w: Float, _ h: Float) -> (Float, Float) { (t.x * w, t.y * h) }

    private static func ellipseAt(_ w: Float, _ h: Float, _ dx: Float, _ dy: Float, _ k: Float) -> (Float, Float) {
        let a = w / 2, bb = h / 2
        let l = max(hypotf(dx, dy), 1e-4)
        let ux = dx / l, uy = dy / l
        let r = 1 / ((ux * ux) / (a * a) + (uy * uy) / (bb * bb)).squareRoot()
        return (w / 2 + ux * r * k, h / 2 + uy * r * k)
    }

    private static func tail(_ out: CGMutablePath, _ style: BalloonStyle, _ t: BalloonTail, _ w: Float, _ h: Float) {
        let (tx, ty) = tip(t, w, h)
        let dx = tx - w / 2, dy = ty - h / 2
        let dist = hypotf(dx, dy)
        if dist < 1e-3 { return }
        let (bx, by) = ellipseAt(w, h, dx, dy, 0.78)
        let ux = dx / dist, uy = dy / dist
        let px = -uy, py = ux
        let half = min(w, h) * (style == .PHONE ? 0.2 : 0.13)
        let ax = bx + px * half, ay = by + py * half, zx = bx - px * half, zy = by - py * half
        if style == .ELECTRIC {
            let steps = 4
            out.move(to: pt(ax, ay))
            for i in 1...steps {
                let f = Float(i) / Float(steps + 1)
                let mx = bx + (tx - bx) * f, my = by + (ty - by) * f
                let side: Float = i % 2 == 0 ? 1 : -1
                let wobble = half * (1 - f) * 0.9 * side
                out.addLine(to: pt(mx + px * wobble + px * half * (1 - f) * 0.4, my + py * wobble + py * half * (1 - f) * 0.4))
            }
            out.addLine(to: pt(tx, ty))
            for i in stride(from: steps, through: 1, by: -1) {
                let f = Float(i) / Float(steps + 1)
                let mx = bx + (tx - bx) * f, my = by + (ty - by) * f
                let side: Float = i % 2 == 0 ? 1 : -1
                let wobble = half * (1 - f) * 0.9 * side
                out.addLine(to: pt(mx + px * wobble - px * half * (1 - f) * 0.4, my + py * wobble - py * half * (1 - f) * 0.4))
            }
            out.addLine(to: pt(zx, zy))
            out.closeSubpath()
            return
        }
        let len = hypotf(tx - bx, ty - by)
        let bend = min(max(t.curve, -1), 1) * len * 0.35
        let mx = (bx + tx) / 2 + px * bend, my = (by + ty) / 2 + py * bend
        out.move(to: pt(ax, ay))
        out.addQuadCurve(to: pt(tx, ty), control: pt(mx + px * half * 0.35, my + py * half * 0.35))
        out.addQuadCurve(to: pt(zx, zy), control: pt(mx - px * half * 0.35, my - py * half * 0.35))
        out.closeSubpath()
    }

    private static func thoughtBubbles(_ out: CGMutablePath, _ t: BalloonTail, _ w: Float, _ h: Float) {
        let (tx, ty) = tip(t, w, h)
        let (bx, by) = ellipseAt(w, h, tx - w / 2, ty - h / 2, 1.02)
        let base = min(w, h) * 0.09
        for i in 0..<3 {
            let f = Float(i + 1) / 3.6
            circle(out, bx + (tx - bx) * f, by + (ty - by) * f, base * (1 - Float(i) * 0.28))
        }
        circle(out, tx, ty, base * 0.28)
    }

    private static func cloud(_ w: Float, _ h: Float) -> CGPath {
        let a = w / 2, b = h / 2
        let perim = Float.pi * (3 * (a + b) - ((3 * a + b) * (a + 3 * b)).squareRoot())
        let n = min(max(Int(perim / (min(w, h) * 0.42)), 8), 18)
        let bump = perim / Float(n) * 0.62
        let core = CGPath(ellipseIn: rect(w * 0.08, h * 0.1, w * 0.92, h * 0.9), transform: nil)
        let bumps = CGMutablePath()
        for k in 0..<n {
            let ang = 2 * Float.pi * Float(k) / Float(n)
            circle(bumps, a + (a - bump * 0.45) * cos(ang), b + (b - bump * 0.45) * sin(ang), bump * 0.6)
        }
        return core.union(bumps)
    }

    private static func burst(_ p: CGMutablePath, _ w: Float, _ h: Float) {
        let a = w / 2, b = h / 2
        let spikes = 14
        let jitter: [Float] = [1, 0.86, 1.08, 0.9, 1.04, 0.82, 1.1, 0.94, 1.02, 0.88, 1.06, 0.92, 1, 0.84]
        for k in 0..<(spikes * 2) {
            let ang = 2 * Double.pi * Double(k) / Double(spikes * 2) - Double.pi / 2
            let r: Float = k % 2 == 0 ? jitter[(k / 2) % jitter.count] : 0.78
            let q = pt(a + a * r * Float(cos(ang)), b + b * r * Float(sin(ang)))
            if k == 0 { p.move(to: q) } else { p.addLine(to: q) }
        }
        p.closeSubpath()
    }

    private static func radial(_ p: CGMutablePath, _ w: Float, _ h: Float, spikes: Int, inner: Float, jitter: Float) {
        let a = w / 2, b = h / 2
        for k in 0..<(spikes * 2) {
            let ang = 2 * Double.pi * Double(k) / Double(spikes * 2) - Double.pi / 2
            let j = 1 - jitter * (Float((k * 7919) % 13) / 13)
            let r = k % 2 == 0 ? j : inner
            let q = pt(a + a * r * Float(cos(ang)), b + b * r * Float(sin(ang)))
            if k == 0 { p.move(to: q) } else { p.addLine(to: q) }
        }
        p.closeSubpath()
    }

    private static func icicle(_ p: CGMutablePath, _ w: Float, _ h: Float) {
        let a = w / 2, b = h / 2
        let n = 160, teeth: Float = 9
        for k in 0...n {
            let ang = 2 * Double.pi * Double(k) / Double(n)
            var r: Float = 0.9
            let s = Float(sin(ang))
            if s > 0.15 {
                let u = min(max(Float((ang - 0.15) / (Double.pi - 0.3)), 0), 1) * teeth
                let frac = u - u.rounded(.down)
                let tooth = Float(Int(u) * 37 % 5) / 5
                let tri = 1 - abs(frac - 0.5) * 2
                r = 0.9 + tri * (0.12 + 0.12 * tooth) * s
            }
            let q = pt(a + a * r * Float(cos(ang)), b + b * r * s)
            if k == 0 { p.move(to: q) } else { p.addLine(to: q) }
        }
        p.closeSubpath()
    }

    private static func drip(_ w: Float, _ h: Float) -> CGPath {
        let core = CGPath(ellipseIn: rect(0, 0, w, h * 0.9), transform: nil)
        let drops = CGMutablePath()
        let xs: [Float] = [0.26, 0.43, 0.6, 0.74], lens: [Float] = [0.2, 0.32, 0.14, 0.24]
        let r = min(w, h) * 0.045
        for i in xs.indices {
            let x = w * xs[i]
            let ex = (x - w / 2) / (w / 2)
            let yEdge = h * 0.45 + h * 0.45 * max(1 - ex * ex, 0).squareRoot()
            let y1 = yEdge + h * lens[i]
            let rr = rect(x - r, yEdge - h * 0.08, x + r, y1)
            drops.addRoundedRect(in: rr, cornerWidth: min(CGFloat(r), rr.width / 2), cornerHeight: min(CGFloat(r), rr.height / 2))
            circle(drops, x, y1 - r * 0.3, r * 1.35)
        }
        return core.union(drops)
    }

    private static func scallop(_ w: Float, _ h: Float) -> CGPath {
        let inset = min(w, h) * 0.07
        let rr = CGFloat(min(w, h) * 0.2)
        let core = CGPath(roundedRect: rect(inset, inset, w - inset, h - inset), cornerWidth: rr, cornerHeight: rr, transform: nil)
        let bumps = CGMutablePath()
        let step = min(w, h) * 0.16
        let r = step * 0.62
        func along(_ x0: Float, _ y0: Float, _ x1: Float, _ y1: Float) {
            let len = hypotf(x1 - x0, y1 - y0)
            let n = max(2, Int(len / step))
            for i in 0...n {
                let f = Float(i) / Float(n)
                circle(bumps, x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, r)
            }
        }
        let a = inset + r * 0.3, bx = w - inset - r * 0.3, by = h - inset - r * 0.3
        along(a, a, bx, a); along(bx, a, bx, by); along(bx, by, a, by); along(a, by, a, a)
        return core.union(bumps)
    }

    private static func heart(_ p: CGMutablePath, _ w: Float, _ h: Float) {
        p.move(to: pt(w * 0.5, h * 0.22))
        p.addCurve(to: pt(w * 0.02, h * 0.3), control1: pt(w * 0.5, h * -0.02), control2: pt(w * 0.02, h * -0.05))
        p.addCurve(to: pt(w * 0.5, h), control1: pt(w * 0.02, h * 0.58), control2: pt(w * 0.3, h * 0.74))
        p.addCurve(to: pt(w * 0.98, h * 0.3), control1: pt(w * 0.7, h * 0.74), control2: pt(w * 0.98, h * 0.58))
        p.addCurve(to: pt(w * 0.5, h * 0.22), control1: pt(w * 0.98, h * -0.05), control2: pt(w * 0.5, h * -0.02))
        p.closeSubpath()
    }

    private static func rough(_ p: CGMutablePath, _ w: Float, _ h: Float) {
        let a = w / 2, b = h / 2
        let n = 58
        var rnd = JavaRandom(31)
        for k in 0..<n {
            let ang = 2 * Double.pi * (Double(k) + Double(rnd.nextFloat() - 0.5) * 0.5) / Double(n)
            let r = (k % 2 == 0 ? 0.99 : 0.9) + (rnd.nextFloat() - 0.5) * 0.05 + 0.02 * Float(sin(ang * 5 + 0.7))
            let q = pt(a + a * r * Float(cos(ang)), b + b * r * Float(sin(ang)))
            if k == 0 { p.move(to: q) } else { p.addLine(to: q) }
        }
        p.closeSubpath()
    }

    private static func wavy(_ p: CGMutablePath, _ w: Float, _ h: Float) {
        let a = w / 2, b = h / 2
        let n = 120
        for k in 0...n {
            let ang = 2 * Double.pi * Double(k) / Double(n)
            let r = 0.94 + 0.05 * Float(sin(ang * 18))
            let q = pt(a + a * r * Float(cos(ang)), b + b * r * Float(sin(ang)))
            if k == 0 { p.move(to: q) } else { p.addLine(to: q) }
        }
        p.closeSubpath()
    }

    /// Varsayılan kuyruklar: [count] konuşana (alt kenar boyunca yayılmış).
    static func defaultTails(_ count: Int) -> [BalloonTail] {
        switch count {
        case 0: return []
        case 1: return [BalloonTail(x: 0.3, y: 1.65)]
        default:
            return (0..<count).map { i in
                let f = Float(i) / Float(count - 1)
                return BalloonTail(x: 0.1 + 0.8 * f, y: 1.6 + 0.1 * abs(f - 0.5), curve: f < 0.5 ? 0.3 : -0.3)
            }
        }
    }
}
