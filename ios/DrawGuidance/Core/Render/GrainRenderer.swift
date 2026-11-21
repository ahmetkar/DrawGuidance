import CoreGraphics
import Foundation

/// v56 aşama 5 gren ve resimden fırça çizimi (Android `GrainRenderer`). Gren: iz saydamlık katmanında çizilir, sonra
/// hazır doku karosu (alfa = `V56Textures.alpha`) `destinationIn` ile çarpılır; doku "Kayar"da tuvale, değilse izin ilk
/// noktasına sabittir. Resim ucu: uç resmi (beyaz + alfa) iz boyunca aralıklı damgalanır ve fırça rengiyle boyanır.
enum GrainRenderer {
    private static let lock = NSLock()
    private static var tiles: [String: CGImage] = [:]
    private static var tinted: [String: CGImage] = [:]

    /// Doku karosu: beyaz + alfa; güç 10 düzeyde önbellekte.
    static func tile(_ id: String, strength: Float) -> CGImage {
        let level = Bitmaps.roundToInt(min(max(strength, 0), 1) * 10)
        let key = "\(id)-\(level)"
        lock.lock(); defer { lock.unlock() }
        if let t = tiles[key] { return t }
        let n = V56Textures.SIZE
        let values = V56Textures.tile(id)
        let px: [UInt32] = values.map { v in
            let a = UInt32(min(max(Bitmaps.roundToInt(V56Textures.alpha(v, Float(level) / 10) * 255), 0), 255))
            return (a << 24) | 0xFFFFFF
        }
        let image = Bitmaps.image(argb: px, n, n)
        tiles[key] = image
        return image
    }

    static func preview(_ id: String) -> CGImage { tile(id, strength: 1) }

    static func grainOf(_ s: Stroke) -> GrainSpec? { s.points.isEmpty ? nil : s.params?.grain }

    /// `body` ile çizilen izi grenle çarpar.
    static func withGrain(_ c: CGContext, _ spec: GrainSpec, firstX: Float, firstY: Float, _ body: () -> Void) {
        c.saveGState()
        c.beginTransparencyLayer(auxiliaryInfo: nil)
        body()
        c.saveGState()
        c.setBlendMode(.destinationIn)
        let (ax, ay) = V56Textures.anchor(spec, firstX, firstY)
        c.translateBy(x: CGFloat(ax), y: CGFloat(ay))
        Bitmaps.tile(c, tile(spec.textureId, strength: spec.strength), scale: CGFloat(V56Textures.scaleOf(spec)), interpolate: false)
        c.restoreGState()
        c.endTransparencyLayer()
        c.restoreGState()
    }

    private static func tintedTip(_ id: String, _ tip: CGImage, _ color: UInt32) -> CGImage {
        let key = "\(id)|\(color & 0xFFFFFF)"
        lock.lock()
        if let t = tinted[key] { lock.unlock(); return t }
        lock.unlock()
        let w = tip.width, h = tip.height
        let image = Bitmaps.draw(w, h) { ctx in
            Bitmaps.drawUpright(ctx, tip, in: CGRect(x: 0, y: 0, width: w, height: h))
            ctx.setBlendMode(.sourceIn)
            ctx.setFillColor(ArgbColor.cgColor(color | 0xFF00_0000))
            ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
        }
        lock.lock()
        if tinted.count > 32 { tinted.removeAll() }
        tinted[key] = image
        lock.unlock()
        return image
    }

    /// Resimden fırça: uç her `size × spacing` px'de bir, iz yönüne dönerek damgalanır. Uç yüklenmediyse false.
    @discardableResult
    static func drawStamps(_ c: CGContext, _ stroke: Stroke, color: UInt32) -> Bool {
        guard let id = stroke.params?.stampAssetId, let tip = CanvasImageCache.get(id) else { return false }
        let pts = stroke.points
        if pts.isEmpty { return true }
        let image = tintedTip(id, tip, color)
        let aspect = CGFloat(tip.height) / CGFloat(max(1, tip.width))
        let spacing = min(max(stroke.params?.spacing ?? 0.25, 0.02), 3)
        c.saveGState()
        c.setAlpha(CGFloat(min(max(stroke.opacity, 0), 1) * min(max(stroke.flow, 0.05), 1)))
        c.interpolationQuality = .default
        func stamp(_ x: Float, _ y: Float, _ pressure: Float, _ angle: CGFloat) {
            let w = CGFloat(max(1, stroke.sizePx * min(max(pressure, 0.2), 1.5)))
            c.saveGState()
            c.translateBy(x: CGFloat(x), y: CGFloat(y))
            c.rotate(by: angle)
            Bitmaps.drawUpright(c, image, in: CGRect(x: -w / 2, y: -w * aspect / 2, width: w, height: w * aspect))
            c.restoreGState()
        }
        stamp(pts[0].x, pts[0].y, pts[0].pressure, 0)
        var carry: Float = 0
        for i in 1..<max(1, pts.count) where pts.count > 1 {
            let a = pts[i - 1], b = pts[i]
            let len = hypotf(b.x - a.x, b.y - a.y)
            if len <= 0 { continue }
            let angle = CGFloat(atan2(b.y - a.y, b.x - a.x))
            var d = carry
            while d <= len {
                let t = d / len
                let p = a.pressure + (b.pressure - a.pressure) * t
                let step = max(1, stroke.sizePx * min(max(p, 0.2), 1.5) * spacing)
                if d > 0 { stamp(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, p, angle) }
                d += step
            }
            carry = d - len
        }
        c.restoreGState()
        return true
    }

    /// Seçilen resimden fırça ucu: en çok `maxSide` px; alfa = (1 − parlaklık) × resim alfası; renk beyaz. Boş kenarlar kırpılır.
    static func tipFrom(_ source: CGImage, maxSide: Int = 256) -> CGImage {
        let scale = min(1, CGFloat(maxSide) / CGFloat(max(source.width, source.height)))
        let w = max(1, Int((CGFloat(source.width) * scale).rounded())), h = max(1, Int((CGFloat(source.height) * scale).rounded()))
        var rgba = [UInt8](repeating: 0, count: w * h * 4)
        rgba.withUnsafeMutableBytes { buf in
            let ctx = CGContext(data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: Bitmaps.space,
                                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
            ctx.interpolationQuality = .high
            ctx.draw(source, in: CGRect(x: 0, y: 0, width: w, height: h))
        }
        // Bellekteki 0. satır üst satırdır (CGBitmapContext belleği yukarıdan aşağı)
        var out = [UInt32](repeating: 0, count: w * h)
        var minX = w, minY = h, maxX = -1, maxY = -1
        for y in 0..<h {
            for x in 0..<w {
                let i = (y * w + x) * 4
                let a = Float(rgba[i + 3])
                // Önçarpımdan çıkar
                let r = a > 0 ? Float(rgba[i]) * 255 / a : 0, g = a > 0 ? Float(rgba[i + 1]) * 255 / a : 0, b = a > 0 ? Float(rgba[i + 2]) * 255 / a : 0
                let lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255
                let alpha = UInt32(min(max(Bitmaps.roundToInt((1 - lum) * a), 0), 255))
                out[y * w + x] = (alpha << 24) | 0xFFFFFF
                if alpha > 8 { minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y) }
            }
        }
        guard maxX >= minX, maxY >= minY else { return Bitmaps.image(argb: out, w, h) }
        let cw = maxX - minX + 1, ch = maxY - minY + 1
        var crop = [UInt32](repeating: 0, count: cw * ch)
        for y in 0..<ch { for x in 0..<cw { crop[y * cw + x] = out[(y + minY) * w + x + minX] } }
        return Bitmaps.image(argb: crop, cw, ch)
    }
}
