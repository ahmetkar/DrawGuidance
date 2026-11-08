import CoreGraphics
import Foundation

/// v43 "Arka planla uyumla"nın çizimi (Android `HarmonyRenderer`, yıkıcı olmayan): renk/ışık ve kenar bir kez piksel
/// işlemiyle hesaplanıp önbellekte tutulur (en çok 1024 px); gölge her çizimde işlenmiş resmin bulanık siluetinden basılır.
enum HarmonyRenderer {
    static let MAX_SIDE = 1024

    final class Processed {
        let image: CGImage
        let alpha: [UInt8]
        let w: Int, h: Int
        let foot: Float
        var shadows: [String: (CGImage, Int)] = [:]
        init(image: CGImage, alpha: [UInt8], w: Int, h: Int, foot: Float) { self.image = image; self.alpha = alpha; self.w = w; self.h = h; self.foot = foot }
    }

    private static let cache: NSCache<NSString, Processed> = {
        let c = NSCache<NSString, Processed>()
        c.totalCostLimit = 32 * 1024 * 1024
        return c
    }()

    static func clear() { cache.removeAllObjects() }

    /// İşlenmiş resim (önbellekten).
    static func processed(_ src: CGImage, key: String?, spec: HarmonySpec) -> Processed {
        let k = "\(key ?? String(UInt(bitPattern: ObjectIdentifier(src).hashValue))):\(src.width)x\(src.height):\(spec.cacheKey)" as NSString
        if let hit = cache.object(forKey: k) { return hit }
        let (px, w, h) = process(src, spec)
        let img = Bitmaps.image(argb: px, w, h)
        let alpha = px.map { UInt8($0 >> 24) }
        let p = Processed(image: img, alpha: alpha, w: w, h: h, foot: footY(alpha, w, h))
        cache.setObject(p, forKey: k, cost: w * h * 5)
        return p
    }

    /// Renk/ışık ve kenar ayarları uygulanmış ARGB pikseller (en çok [MAX_SIDE]).
    static func process(_ src: CGImage, _ spec: HarmonySpec) -> ([UInt32], Int, Int) {
        let scale = min(1, Float(MAX_SIDE) / Float(max(src.width, src.height)))
        let w = max(1, ktRound(Float(src.width) * scale)), h = max(1, ktRound(Float(src.height) * scale))
        var px = argbPixels(src, w, h)
        let t = clampF(spec.intensity, 0, 1)
        if spec.color && t > 0 { applyColor(&px, spec, t) }
        if spec.edge && t > 0 && (spec.feather > 0 || spec.wrap > 0) { applyEdge(&px, w, h, spec, t) }
        return (px, w, h)
    }

    /// Görüntünün [w]×[h] boyutunda önçarpımsız ARGB pikselleri (üst satır önce).
    static func argbPixels(_ img: CGImage, _ w: Int, _ h: Int) -> [UInt32] {
        var data = [UInt8](repeating: 0, count: w * h * 4)
        data.withUnsafeMutableBytes { buf in
            let ctx = CGContext(data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: Bitmaps.space,
                                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
            ctx.interpolationQuality = .high
            ctx.draw(img, in: CGRect(x: 0, y: 0, width: w, height: h))
        }
        var out = [UInt32](repeating: 0, count: w * h)
        for i in 0..<(w * h) {
            let a = UInt32(data[i * 4 + 3])
            if a == 0 { continue }
            func un(_ v: UInt8) -> UInt32 { min(255, (UInt32(v) * 255 + a / 2) / a) }
            out[i] = a << 24 | un(data[i * 4]) << 16 | un(data[i * 4 + 1]) << 8 | un(data[i * 4 + 2])
        }
        return out
    }

    /// Kanal kazancı, parlaklık kayması ve doygunluk ([t] ile özgünle karışık).
    static func applyColor(_ px: inout [UInt32], _ spec: HarmonySpec, _ t: Float) {
        let gr = 1 + (spec.gainR - 1) * t, gg = 1 + (spec.gainG - 1) * t, gb = 1 + (spec.gainB - 1) * t
        let lift = spec.lift * 255 * t
        let sat = 1 + (spec.saturation - 1) * t
        for i in px.indices {
            let c = px[i]
            let a = c >> 24
            if a == 0 { continue }
            var r = Float(c >> 16 & 0xFF) * gr + lift
            var g = Float(c >> 8 & 0xFF) * gg + lift
            var b = Float(c & 0xFF) * gb + lift
            let l = 0.299 * r + 0.587 * g + 0.114 * b
            r = l + (r - l) * sat; g = l + (g - l) * sat; b = l + (b - l) * sat
            px[i] = a << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b)
        }
    }

    /// Kenar yumuşatma (alfa, bulanık alfayla çarpılır) ve ışık sarması (kenar bandına arka plan rengi).
    static func applyEdge(_ px: inout [UInt32], _ w: Int, _ h: Int, _ spec: HarmonySpec, _ t: Float) {
        let alpha = px.map { Int($0 >> 24) }
        let side = Float(min(w, h))
        let featherR = ktRound(spec.feather * side)
        let soft = featherR >= 1 ? blur(alpha, w, h, featherR) : nil
        let band = spec.wrap > 0 ? blur(alpha, w, h, max(2, ktRound(0.02 * side))) : nil
        let wc = ArgbColor.parse(spec.wrapColorHex) ?? ArgbColor.white
        let wr = Int(wc >> 16 & 0xFF), wg = Int(wc >> 8 & 0xFF), wb = Int(wc & 0xFF)
        for i in px.indices {
            let a = alpha[i]
            if a == 0 { continue }
            let c = px[i]
            var r = Int(c >> 16 & 0xFF), g = Int(c >> 8 & 0xFF), b = Int(c & 0xFF)
            var na = a
            if let band {
                let k = min(Float(max(a - band[i], 0)) / 255 * 2, 1) * spec.wrap * t
                if k > 0 {
                    r = ktRound(Float(r) + Float(wr - r) * k); g = ktRound(Float(g) + Float(wg - g) * k); b = ktRound(Float(b) + Float(wb - b) * k)
                }
            }
            if let soft { na = clampI(ktRound(Float(a) + (Float(a) * Float(soft[i]) / 255 - Float(a)) * t), 0, 255) }
            px[i] = UInt32(na) << 24 | UInt32(clampI(r, 0, 255)) << 16 | UInt32(clampI(g, 0, 255)) << 8 | UInt32(clampI(b, 0, 255))
        }
    }

    /// İki geçişli kutu bulanıklığı (yatay + dikey, iki kez: yaklaşık Gauss).
    static func blur(_ src: [Int], _ w: Int, _ h: Int, _ radius: Int) -> [Int] {
        var a = src
        var tmp = [Int](repeating: 0, count: a.count)
        for _ in 0..<2 {
            boxH(a, &tmp, w, h, radius)
            boxV(tmp, &a, w, h, radius)
        }
        return a
    }

    private static func boxH(_ src: [Int], _ dst: inout [Int], _ w: Int, _ h: Int, _ r: Int) {
        let div = 2 * r + 1
        for y in 0..<h {
            let row = y * w
            var sum = 0
            for x in -r...r { sum += src[row + clampI(x, 0, w - 1)] }
            for x in 0..<w {
                dst[row + x] = sum / div
                sum += src[row + min(x + r + 1, w - 1)] - src[row + max(x - r, 0)]
            }
        }
    }

    private static func boxV(_ src: [Int], _ dst: inout [Int], _ w: Int, _ h: Int, _ r: Int) {
        let div = 2 * r + 1
        for x in 0..<w {
            var sum = 0
            for y in -r...r { sum += src[clampI(y, 0, h - 1) * w + x] }
            for y in 0..<h {
                dst[y * w + x] = sum / div
                sum += src[min(y + r + 1, h - 1) * w + x] - src[max(y - r, 0) * w + x]
            }
        }
    }

    @inline(__always) private static func clamp(_ v: Float) -> UInt32 { UInt32(clampI(ktRound(v), 0, 255)) }

    /// Siluetin en alt dolu satırı (ayak çizgisi).
    static func footY(_ alpha: [UInt8], _ w: Int, _ h: Int) -> Float {
        let need = max(1, w / 100)
        for y in stride(from: h - 1, through: 0, by: -1) {
            var n = 0
            for x in 0..<w where alpha[y * w + x] > 40 { n += 1 }
            if n >= need { return Float(y + 1) }
        }
        return Float(h)
    }

    /// Uyumlu resmi çizer: önce gölgeler, sonra işlenmiş resim. [m] özgün resmin piksellerinden (y aşağı) tuvale.
    static func draw(_ c: CGContext, _ src: CGImage, key: String?, spec: HarmonySpec, transform m: CGAffineTransform, alpha: Int) {
        let p = processed(src, key: key, spec: spec)
        let pm = CGAffineTransform(scaleX: CGFloat(src.width) / CGFloat(p.w), y: CGFloat(src.height) / CGFloat(p.h)).concatenating(m)
        c.saveGState()
        c.concatenate(pm)
        if spec.shadow && spec.intensity > 0 { drawShadows(c, p, spec, alpha) }
        c.setAlpha(CGFloat(alpha) / 255)
        c.interpolationQuality = .high
        Bitmaps.drawUpright(c, p.image, in: CGRect(x: 0, y: 0, width: p.w, height: p.h))
        c.restoreGState()
    }

    /// İşlenmiş resim pikselleri biriminde (y aşağı) gölgeler.
    private static func drawShadows(_ c: CGContext, _ p: Processed, _ spec: HarmonySpec, _ alpha: Int) {
        let w = CGFloat(p.w), h = CGFloat(p.h)
        let t = clampF(spec.intensity, 0, 1)
        let color = (ArgbColor.parse(spec.shadowColorHex) ?? 0xFF00_0000) & 0x00FF_FFFF
        let a = clampI(ktRound(clampF(spec.shadowOpacity, 0, 1) * t * Float(alpha)), 0, 255)
        if a == 0 { return }
        let foot = CGFloat(p.foot)
        c.saveGState()
        defer { c.restoreGState() }
        // Temas gölgesi: ayağın altında yassı radyal leke
        if spec.contact {
            c.saveGState()
            c.translateBy(x: w / 2, y: foot)
            c.scaleBy(x: w * 0.38, y: max(2, h * 0.035))
            c.addEllipse(in: CGRect(x: -1, y: -1, width: 2, height: 2)); c.clip()
            let cols = [ArgbColor.cgColor(UInt32(min(255, a * 2)) << 24 | color), ArgbColor.cgColor(color)] as CFArray
            if let g = CGGradient(colorsSpace: Bitmaps.space, colors: cols, locations: [0, 1]) {
                c.drawRadialGradient(g, startCenter: .zero, startRadius: 0, endCenter: .zero, endRadius: 1, options: [])
            }
            c.restoreGState()
        }
        // Düşen gölge: siluet ayak çizgisinden yatırılır ve basılır
        let blurPx = max(0.5, spec.shadowBlur * Float(max(p.w, p.h)))
        let (mask, pad) = shadowImage(p, color, a, blurPx)
        c.translateBy(x: CGFloat(spec.shadowDx) * w, y: CGFloat(spec.shadowDy) * h)
        c.translateBy(x: w / 2, y: foot)
        c.concatenate(CGAffineTransform(a: 1, b: 0, c: CGFloat(spec.castSkew), d: 1, tx: 0, ty: 0))
        c.scaleBy(x: 1, y: CGFloat(clampF(spec.castScaleY, 0.05, 1)))
        c.translateBy(x: -w / 2, y: -foot)
        Bitmaps.drawUpright(c, mask, in: CGRect(x: -pad, y: -pad, width: p.w + 2 * pad, height: p.h + 2 * pad))
    }

    /// Bulanık siluet (renkli, çevresinde bulanıklık payı); işlenmiş resimle birlikte önbellekte.
    private static func shadowImage(_ p: Processed, _ color: UInt32, _ a: Int, _ blurPx: Float) -> (CGImage, Int) {
        let key = "\(color)|\(a)|\(blurPx)"
        if let hit = p.shadows[key] { return hit }
        // BlurMaskFilter yarıçapı → Gauss sigması; iki kez kutu bulanıklığı aynı varyansı verecek yarıçap
        let sigma = Double(blurPx) * 0.57735 + 0.5
        let r = max(1, Int((-1 + (1 + 6 * sigma * sigma).squareRoot()) / 2 + 0.5))
        let pad = 2 * r + 1
        let w = p.w + 2 * pad, h = p.h + 2 * pad
        var alpha = [Int](repeating: 0, count: w * h)
        for y in 0..<p.h { for x in 0..<p.w { alpha[(y + pad) * w + x + pad] = Int(p.alpha[y * p.w + x]) } }
        let soft = blur(alpha, w, h, r)
        let px = soft.map { v -> UInt32 in UInt32(clampI(v * a / 255, 0, 255)) << 24 | color }
        let out = (Bitmaps.image(argb: px, w, h), pad)
        if p.shadows.count > 4 { p.shadows.removeAll() }
        p.shadows[key] = out
        return out
    }
}

extension HarmonySpec {
    /// Önbellek anahtarı (ayarların tamamı).
    var cacheKey: String {
        "\(intensity)|\(color)|\(gainR)|\(gainG)|\(gainB)|\(lift)|\(saturation)|\(edge)|\(feather)|\(wrap)|\(wrapColorHex)"
    }
}
