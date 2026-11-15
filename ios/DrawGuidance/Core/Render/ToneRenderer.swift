import CoreGraphics
import Foundation

/// v53 çizgi roman ekran tonlarını ve desenlerini çizer (Android `ToneRenderer`). Üretilen öğeler küçük bir önbellekte
/// tutulur (öğe sayısıyla sınırlı); bellek uyarısında [trim] ile boşaltılır.
enum ToneRenderer {
    private struct Key: Hashable { let tone: ToneFill; let l: Int; let t: Int; let r: Int; let b: Int }

    private final class Box { let prims: [ComicTones.Prim]; init(_ p: [ComicTones.Prim]) { prims = p } }

    private static let cache: NSCache<NSString, Box> = {
        let c = NSCache<NSString, Box>()
        c.totalCostLimit = 120_000
        return c
    }()

    static func prims(_ tone: ToneFill, l: Float, t: Float, r: Float, b: Float) -> [ComicTones.Prim] {
        let k = Key(tone: tone, l: Int((l / 4).rounded()), t: Int((t / 4).rounded()), r: Int((r / 4).rounded()), b: Int((b / 4).rounded()))
        let key = "\(k.tone.id)|\(k.tone.density)|\(k.tone.scale)|\(k.tone.angle)|\(k.l)|\(k.t)|\(k.r)|\(k.b)" as NSString
        if let hit = cache.object(forKey: key) { return hit.prims }
        let made = ComicTones.prims(tone.id, density: tone.density, scale: tone.scale, angle: tone.angle, l, t, r, b)
        cache.setObject(Box(made), forKey: key, cost: max(made.count, 1))
        return made
    }

    static func draw(_ ctx: CGContext, clip: CGPath, _ tone: ToneFill, l: Float, t: Float, r: Float, b: Float, alpha: Float) {
        let items = prims(tone, l: l, t: t, r: r, b: b)
        if items.isEmpty { return }
        let color = ArgbColor.hex(tone.colorHex)
        ctx.saveGState()
        defer { ctx.restoreGState() }
        ctx.addPath(clip)
        ctx.clip()
        ctx.setLineCap(.round)
        ctx.setLineJoin(.round)
        ctx.setLineDash(phase: 0, lengths: [])
        func a255(_ k: Float) -> Int { Int((alpha * k * 255).rounded()).coerceIn(0, 255) }
        for p in items {
            switch p {
            case let .dot(x, y, rr, k):
                ctx.setFillColor(ArgbColor.cgColor(color, alpha: a255(k)))
                ctx.fillEllipse(in: CGRect(x: CGFloat(x - rr), y: CGFloat(y - rr), width: CGFloat(rr * 2), height: CGFloat(rr * 2)))
            case let .ring(x, y, rr, w, k):
                ctx.setStrokeColor(ArgbColor.cgColor(color, alpha: a255(k)))
                ctx.setLineWidth(CGFloat(w))
                ctx.strokeEllipse(in: CGRect(x: CGFloat(x - rr), y: CGFloat(y - rr), width: CGFloat(rr * 2), height: CGFloat(rr * 2)))
            case let .seg(x1, y1, x2, y2, w, k):
                ctx.setStrokeColor(ArgbColor.cgColor(color, alpha: a255(k)))
                ctx.setLineWidth(CGFloat(w))
                ctx.move(to: CGPoint(x: CGFloat(x1), y: CGFloat(y1)))
                ctx.addLine(to: CGPoint(x: CGFloat(x2), y: CGFloat(y2)))
                ctx.strokePath()
            case let .poly(pts, closed, filled, w, k):
                if pts.count < 4 { continue }
                ctx.move(to: CGPoint(x: CGFloat(pts[0]), y: CGFloat(pts[1])))
                var i = 2
                while i + 1 < pts.count { ctx.addLine(to: CGPoint(x: CGFloat(pts[i]), y: CGFloat(pts[i + 1]))); i += 2 }
                if closed { ctx.closePath() }
                if filled {
                    ctx.setFillColor(ArgbColor.cgColor(color, alpha: a255(k)))
                    ctx.fillPath()
                } else {
                    ctx.setStrokeColor(ArgbColor.cgColor(color, alpha: a255(k)))
                    ctx.setLineWidth(CGFloat(w))
                    ctx.strokePath()
                }
            }
        }
    }

    /// Bellek uyarısında önbelleği boşaltır.
    static func trim() { cache.removeAllObjects() }
}
