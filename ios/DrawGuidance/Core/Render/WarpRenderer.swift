import CoreGraphics
import Foundation

/// v56 aşama 6 resim çarpıtması ve yansıma çizimi (Android `WarpRenderer`): Çarpıtma ağı (ImageWarp.mesh) 16×16 alt
/// bölmenin üçgenlerine afin parçalarla çizilip önbelleğe alınır; yansıma dalgası noktaları (vektör) ya da ağı (resim)
/// kaydırır, alfa gradyanı `destinationIn` ile uygulanır.
enum WarpRenderer {
    static let SUB = 16

    private static let cache: NSCache<NSString, CGImage> = {
        let c = NSCache<NSString, CGImage>()
        c.totalCostLimit = 32 * 1024 * 1024
        MemoryHygiene.register(editor: true) { c.removeAllObjects() }
        return c
    }()

    static func hasWave(_ s: Stroke) -> Bool {
        guard let w = s.warp?.wave else { return false }
        return w.amplitude != 0 || w.fadeFrom < 1 || w.fadeTo < 1
    }

    /// Vektör çizgide dalga kayması uygulanmış kopya.
    static func displaced(_ s: Stroke) -> Stroke {
        guard let w = s.warp?.wave, w.amplitude != 0 else { return s }
        var o = s
        o.points = s.points.map { p in let d = Reflection.offset(w, p.x, p.y); return p.at(p.x + d.0, p.y + d.1) }
        return o
    }

    /// Resmin ağ köşe noktaları (SUB+1)²: ağ varsa ağ yüzeyinden, yoksa 4 köşeden; dalga varsa kaydırılmış. Gerekmiyorsa nil.
    static func verts(_ corners: [StrokePoint], _ warp: ImageWarp?) -> [CGPoint]? {
        let mesh = warp?.mesh
        let n = mesh.map { WarpMesh.sizeOf($0) } ?? 0
        let wave = warp?.wave.flatMap { $0.amplitude != 0 ? $0 : nil }
        if n == 0 && wave == nil { return nil }
        var out = [CGPoint]()
        out.reserveCapacity((SUB + 1) * (SUB + 1))
        for j in 0...SUB {
            for i in 0...SUB {
                let u = Float(i) / Float(SUB), v = Float(j) / Float(SUB)
                var x: Float, y: Float
                if n > 0 {
                    (x, y) = WarpMesh.eval(mesh!, n, u, v)
                } else {
                    let tx = corners[0].x + (corners[1].x - corners[0].x) * u, ty = corners[0].y + (corners[1].y - corners[0].y) * u
                    let bx = corners[3].x + (corners[2].x - corners[3].x) * u, by = corners[3].y + (corners[2].y - corners[3].y) * u
                    x = tx + (bx - tx) * v; y = ty + (by - ty) * v
                }
                if let wave { let d = Reflection.offset(wave, x, y); x += d.0; y += d.1 }
                out.append(CGPoint(x: CGFloat(x), y: CGFloat(y)))
            }
        }
        return out
    }

    /// Üç noktadan üç noktaya afin dönüşüm.
    private static func affine(_ s0: CGPoint, _ s1: CGPoint, _ s2: CGPoint, _ d0: CGPoint, _ d1: CGPoint, _ d2: CGPoint) -> CGAffineTransform? {
        let det = (s1.x - s0.x) * (s2.y - s0.y) - (s2.x - s0.x) * (s1.y - s0.y)
        if abs(det) < 1e-9 { return nil }
        // Kaynak taban → birim, birim → hedef taban
        let src = CGAffineTransform(a: s1.x - s0.x, b: s1.y - s0.y, c: s2.x - s0.x, d: s2.y - s0.y, tx: s0.x, ty: s0.y)
        let dst = CGAffineTransform(a: d1.x - d0.x, b: d1.y - d0.y, c: d2.x - d0.x, d: d2.y - d0.y, tx: d0.x, ty: d0.y)
        return src.inverted().concatenating(dst)
    }

    /// Resmi ağ noktalarına çizer ([filter] 0 en yakın, 1 çift doğrusal, 2 çift kübik); bükülmüş görüntü önbelleklenir.
    static func drawMesh(_ c: CGContext, _ image: CGImage, verts: [CGPoint], filter: Int, key: String) {
        let minX = verts.map(\.x).min()!, maxX = verts.map(\.x).max()!, minY = verts.map(\.y).min()!, maxY = verts.map(\.y).max()!
        let bw = maxX - minX, bh = maxY - minY
        if bw < 0.5 || bh < 0.5 { return }
        let w = CGFloat(image.width), h = CGFloat(image.height)
        var k = hypot(w, h) / max(1, hypot(bw, bh))
        k = min(k, 2048 / max(bw, bh))
        let pw = max(1, Int((bw * k).rounded(.up))), ph = max(1, Int((bh * k).rounded(.up)))
        var hash = Hasher()
        verts.forEach { hash.combine(Int($0.x * 4)); hash.combine(Int($0.y * 4)) }
        let cacheKey = "\(key)|\(image.width)x\(image.height)|\(filter)|\(hash.finalize())" as NSString
        var out = cache.object(forKey: cacheKey)
        if out == nil {
            out = Bitmaps.draw(pw, ph) { g in
                g.interpolationQuality = filter == 0 ? .none : (filter == 2 ? .high : .medium)
                g.scaleBy(x: k, y: k)
                g.translateBy(x: -minX, y: -minY)
                let n = SUB + 1
                for j in 0..<SUB {
                    for i in 0..<SUB {
                        let sx0 = w * CGFloat(i) / CGFloat(SUB), sx1 = w * CGFloat(i + 1) / CGFloat(SUB)
                        let sy0 = h * CGFloat(j) / CGFloat(SUB), sy1 = h * CGFloat(j + 1) / CGFloat(SUB)
                        let a = verts[j * n + i], b = verts[j * n + i + 1], cc = verts[(j + 1) * n + i + 1], d = verts[(j + 1) * n + i]
                        let tris: [([CGPoint], [CGPoint])] = [
                            ([CGPoint(x: sx0, y: sy0), CGPoint(x: sx1, y: sy0), CGPoint(x: sx1, y: sy1)], [a, b, cc]),
                            ([CGPoint(x: sx0, y: sy0), CGPoint(x: sx1, y: sy1), CGPoint(x: sx0, y: sy1)], [a, cc, d]),
                        ]
                        for (s, t) in tris {
                            guard let m = affine(s[0], s[1], s[2], t[0], t[1], t[2]) else { continue }
                            g.saveGState()
                            // Komşu üçgenler arasında ince boşluk kalmasın diye üçgen biraz genişletilir
                            let cx = (t[0].x + t[1].x + t[2].x) / 3, cy = (t[0].y + t[1].y + t[2].y) / 3
                            let grow: CGFloat = 0.6 / k
                            let p = CGMutablePath()
                            p.addLines(between: t.map { q in
                                let dx = q.x - cx, dy = q.y - cy, l = max(1e-6, hypot(dx, dy))
                                return CGPoint(x: q.x + dx / l * grow, y: q.y + dy / l * grow)
                            })
                            p.closeSubpath()
                            g.addPath(p); g.clip()
                            g.concatenate(m)
                            Bitmaps.drawUpright(g, image, in: CGRect(x: 0, y: 0, width: w, height: h))
                            g.restoreGState()
                        }
                    }
                }
            }
            if let out { cache.setObject(out, forKey: cacheKey, cost: pw * ph * 4) }
        }
        guard let out else { return }
        Bitmaps.drawUpright(c, out, in: CGRect(x: minX, y: minY, width: bw, height: bh))
    }

    /// Yansımanın alfa gradyanı: [body] saydamlık katmanında çizilir, gradyan `destinationIn` ile çarpılır.
    static func withFade(_ c: CGContext, _ w: WaveSpec, top: Float, bottom: Float, _ body: () -> Void) {
        if w.fadeFrom >= 0.999 && w.fadeTo >= 0.999 { body(); return }
        let y0 = CGFloat(w.bottom > w.top ? w.top : top)
        var y1 = CGFloat(w.bottom > w.top ? w.bottom : bottom)
        if y1 <= y0 { y1 = y0 + 1 }
        c.saveGState()
        c.beginTransparencyLayer(auxiliaryInfo: nil)
        body()
        let colors = [CGColor(gray: 1, alpha: CGFloat(w.fadeFrom.coerceIn(0, 1))), CGColor(gray: 1, alpha: CGFloat(w.fadeTo.coerceIn(0, 1)))] as CFArray
        if let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceGray(), colors: colors, locations: [0, 1]) {
            c.setBlendMode(.destinationIn)
            c.drawLinearGradient(g, start: CGPoint(x: 0, y: y0), end: CGPoint(x: 0, y: y1), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        }
        c.endTransparencyLayer()
        c.restoreGState()
    }
}
