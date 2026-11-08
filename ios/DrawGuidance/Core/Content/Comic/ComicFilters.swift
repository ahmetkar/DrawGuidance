import Foundation

/// v27 resim filtreleri (çizgi roman görünümü).
enum ComicFilter: String, CaseIterable { case COMIC, HALFTONE, MANGA, POP }

/// v27 çizgi roman resim filtreleri (Android `ComicFilters.kt` ile aynı; ARGB piksel dizileri, 0xAARRGGBB).
/// - COMIC: hafif bulanıklık + renk basamaklama + Sobel kenarlarından mürekkep çizgileri.
/// - HALFTONE: ton noktaları (gazete baskısı). - MANGA: siyah-beyaz screentone. - POP: doygun, kalın kenarlı pop art.
enum ComicFilters {
    static func apply(_ kind: ComicFilter, _ src: [UInt32], _ w: Int, _ h: Int, cancelled: () -> Bool = { false }) -> [UInt32] {
        precondition(src.count >= w * h)
        switch kind {
        case .COMIC: return posterEdges(src, w, h, levels: 5, saturation: 1.1, edge: 0.22, thick: false, cancelled)
        case .POP: return posterEdges(src, w, h, levels: 3, saturation: 1.6, edge: 0.18, thick: true, cancelled)
        case .HALFTONE: return halftone(src, w, h, manga: false)
        case .MANGA: return halftone(src, w, h, manga: true)
        }
    }

    @inline(__always) private static func lum(_ c: UInt32) -> Float {
        (0.299 * Float(c >> 16 & 0xFF) + 0.587 * Float(c >> 8 & 0xFF) + 0.114 * Float(c & 0xFF)) / 255
    }

    /// Kotlin `Math.round(Float)`: floor(x + 0.5).
    @inline(__always) private static func roundK(_ v: Float) -> Int { Int((v + 0.5).rounded(.down)) }

    private static func posterEdges(_ src: [UInt32], _ w: Int, _ h: Int, levels: Int, saturation: Float, edge: Float, thick: Bool,
                                    _ cancelled: () -> Bool) -> [UInt32] {
        let n = w * h
        let l = (0..<n).map { lum(src[$0]) }
        var out = [UInt32](repeating: 0, count: n)
        let step = 255 / Float(levels - 1)
        @inline(__always) func L(_ xx: Int, _ yy: Int) -> Float { l[min(max(yy, 0), h - 1) * w + min(max(xx, 0), w - 1)] }
        for y in 0..<h {
            if y % 64 == 0 && cancelled() { return src }
            for x in 0..<w {
                let i = y * w + x
                // 3×3 kutu bulanıklığı (gürültü basamaklarda leke yapmasın)
                var r = 0, g = 0, b = 0, k = 0
                for dy in -1...1 {
                    let yy = min(max(y + dy, 0), h - 1)
                    for dx in -1...1 {
                        let c = src[yy * w + min(max(x + dx, 0), w - 1)]
                        r += Int(c >> 16 & 0xFF); g += Int(c >> 8 & 0xFF); b += Int(c & 0xFF); k += 1
                    }
                }
                let a = src[i] >> 24
                var rf = Float(r) / Float(k), gf = Float(g) / Float(k), bf = Float(b) / Float(k)
                let m = (rf + gf + bf) / 3
                rf = m + (rf - m) * saturation; gf = m + (gf - m) * saturation; bf = m + (bf - m) * saturation
                let rq = UInt32(Float(roundK(rf.coerceIn(0, 255) / step)) * step)
                let gq = UInt32(Float(roundK(gf.coerceIn(0, 255) / step)) * step)
                let bq = UInt32(Float(roundK(bf.coerceIn(0, 255) / step)) * step)
                let gx = -L(x - 1, y - 1) - 2 * L(x - 1, y) - L(x - 1, y + 1) + L(x + 1, y - 1) + 2 * L(x + 1, y) + L(x + 1, y + 1)
                let gy = -L(x - 1, y - 1) - 2 * L(x, y - 1) - L(x + 1, y - 1) + L(x - 1, y + 1) + 2 * L(x, y + 1) + L(x + 1, y + 1)
                let mag = (gx * gx + gy * gy).squareRoot()
                out[i] = mag > edge ? (a << 24) | 0x111111 : (a << 24) | (rq << 16) | (gq << 8) | bq
            }
        }
        if thick && w > 2 && h > 2 {
            // Kenarlar bir piksel kalınlaştırılır
            let copy = out
            for y in 1..<(h - 1) { for x in 1..<(w - 1) {
                let i = y * w + x
                if copy[i] & 0xFFFFFF == 0x111111 { continue }
                if copy[i - 1] & 0xFFFFFF == 0x111111 && copy[i + 1] & 0xFFFFFF == 0x111111 { out[i] = (copy[i] & 0xFF000000) | 0x111111 }
            } }
        }
        return out
    }

    private static func halftone(_ src: [UInt32], _ w: Int, _ h: Int, manga: Bool) -> [UInt32] {
        var out = [UInt32](repeating: 0, count: w * h)
        let cell = max(4, min(w, h) / 110)
        let half = Float(cell) / 2
        for cy in stride(from: 0, to: h, by: cell) { for cx in stride(from: 0, to: w, by: cell) {
            // Hücrenin ortalama koyuluğu
            var sum: Float = 0, cnt = 0
            for y in cy..<min(h, cy + cell) { for x in cx..<min(w, cx + cell) { sum += lum(src[y * w + x]); cnt += 1 } }
            let dark = 1 - sum / Float(max(cnt, 1))
            // Manga: çok koyu siyah, çok açık beyaz, ara tonlar nokta
            let mode = !manga ? 0 : (dark > 0.78 ? 1 : (dark < 0.18 ? 2 : 0))
            let radius = half * 1.25 * dark.coerceIn(0, 1).squareRoot()
            for y in cy..<min(h, cy + cell) { for x in cx..<min(w, cx + cell) {
                let i = y * w + x
                let a = src[i] >> 24
                let black: Bool
                switch mode {
                case 1: black = true
                case 2: black = false
                default:
                    let dx = Float(x - cx) - half + 0.5, dy = Float(y - cy) - half + 0.5
                    black = dx * dx + dy * dy <= radius * radius
                }
                out[i] = (a << 24) | (black ? 0x101010 : 0xFFFFFF)
            } }
        } }
        return out
    }
}
