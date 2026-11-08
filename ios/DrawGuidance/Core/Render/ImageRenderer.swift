import CoreGraphics
import CoreImage
import Foundation

/// Resim çizimi (Android `StrokeRenderer.drawImageStroke`): resim dört köşe noktasına (sol üst, sağ üst, sağ alt, sol alt)
/// oturtulur. Paralelkenarda doğrudan afin dönüşüm; perspektifli dörtgende resim Core Image ile bükülüp önbelleğe alınır.
enum ImageRenderer {
    private static let warped: NSCache<NSString, CGImage> = {
        let c = NSCache<NSString, CGImage>()
        c.totalCostLimit = 32 * 1024 * 1024
        return c
    }()
    private static let ci = CIContext(options: [.useSoftwareRenderer: false])
    private static let placeholder: UInt32 = 0x3380_8A99

    static func draw(_ c: CGContext, _ stroke: Stroke) {
        guard stroke.points.count >= 4 else { return }
        let q = (0..<4).map { CGPoint(x: CGFloat(stroke.points[$0].x), y: CGFloat(stroke.points[$0].y)) }
        guard let image = CanvasImageCache.get(stroke.assetId) else {
            let path = CGMutablePath()
            path.addLines(between: q); path.closeSubpath()
            c.setFillColor(ArgbColor.cgColor(placeholder)); c.addPath(path); c.fillPath()
            return
        }
        let alpha = Int(min(max(stroke.opacity, 0), 1) * 255)
        // v43 arka planla uyum (gölge, renk/ışık, kenar): özgün resim değişmeden çizimde uygulanır
        if let harmony = stroke.harmony {
            if let t = affine(q, CGFloat(image.width), CGFloat(image.height)) {
                HarmonyRenderer.draw(c, image, key: stroke.assetId, spec: harmony, transform: t, alpha: alpha)
            } else {
                let p = HarmonyRenderer.processed(image, key: stroke.assetId, spec: harmony)
                c.saveGState(); c.setAlpha(CGFloat(alpha) / 255)
                draw(c, p.image, quad: q, key: (stroke.assetId ?? "") + "|h" + harmony.cacheKey)
                c.restoreGState()
            }
            return
        }
        c.saveGState()
        defer { c.restoreGState() }
        c.setAlpha(CGFloat(alpha) / 255)
        c.interpolationQuality = .high
        draw(c, image, quad: q, key: stroke.assetId ?? "")
    }

    /// [image]'yi dörtgene (y aşağı bağlamda) çizer.
    static func draw(_ c: CGContext, _ image: CGImage, quad q: [CGPoint], key: String) {
        let w = CGFloat(image.width), h = CGFloat(image.height)
        if let t = affine(q, w, h) {
            c.saveGState()
            c.concatenate(t)
            Bitmaps.drawUpright(c, image, in: CGRect(x: 0, y: 0, width: w, height: h))
            c.restoreGState()
            return
        }
        let minX = q.map(\.x).min()!, maxX = q.map(\.x).max()!, minY = q.map(\.y).min()!, maxY = q.map(\.y).max()!
        let bw = maxX - minX, bh = maxY - minY
        if bw < 0.5 || bh < 0.5 { return }
        // Çözünürlük: resmin kendi yoğunluğu (en çok 2048 px)
        let quadDiag = max(hypot(q[2].x - q[0].x, q[2].y - q[0].y), hypot(q[3].x - q[1].x, q[3].y - q[1].y), 1)
        var k = hypot(w, h) / quadDiag
        k = min(k, 2048 / max(bw, bh))
        let pw = max(1, Int((bw * k).rounded(.up))), ph = max(1, Int((bh * k).rounded(.up)))
        let cacheKey = "\(key)|\(image.width)x\(image.height)|" + q.map { "\(Int($0.x * 4)),\(Int($0.y * 4))" }.joined(separator: ";") as NSString
        var out = warped.object(forKey: cacheKey)
        if out == nil {
            // Core Image y yukarı: piksel koordinatına çevir
            func v(_ p: CGPoint) -> CIVector { CIVector(x: (p.x - minX) * k, y: CGFloat(ph) - (p.y - minY) * k) }
            let input = CIImage(cgImage: image)
            if let f = CIFilter(name: "CIPerspectiveTransform") {
                f.setValue(input, forKey: kCIInputImageKey)
                f.setValue(v(q[0]), forKey: "inputTopLeft")
                f.setValue(v(q[1]), forKey: "inputTopRight")
                f.setValue(v(q[2]), forKey: "inputBottomRight")
                f.setValue(v(q[3]), forKey: "inputBottomLeft")
                if let result = f.outputImage {
                    out = ci.createCGImage(result, from: CGRect(x: 0, y: 0, width: pw, height: ph))
                }
            }
            if let out { warped.setObject(out, forKey: cacheKey, cost: pw * ph * 4) }
        }
        guard let out else { return }
        Bitmaps.drawUpright(c, out, in: CGRect(x: minX, y: minY, width: bw, height: bh))
    }

    /// Resim pikselinden (y aşağı) dörtgene afin dönüşüm; dörtgen paralelkenar değilse nil.
    static func affine(_ q: [CGPoint], _ w: CGFloat, _ h: CGFloat) -> CGAffineTransform? {
        let ex = q[1].x + q[3].x - q[0].x, ey = q[1].y + q[3].y - q[0].y
        let size = max(hypot(q[1].x - q[0].x, q[1].y - q[0].y), hypot(q[3].x - q[0].x, q[3].y - q[0].y), 1)
        if hypot(q[2].x - ex, q[2].y - ey) > max(0.5, size * 0.002) { return nil }
        if w <= 0 || h <= 0 { return nil }
        return CGAffineTransform(a: (q[1].x - q[0].x) / w, b: (q[1].y - q[0].y) / w,
                                 c: (q[3].x - q[0].x) / h, d: (q[3].y - q[0].y) / h, tx: q[0].x, ty: q[0].y)
    }
}
