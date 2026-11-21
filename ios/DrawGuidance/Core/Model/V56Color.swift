import Foundation

/// v56 aşama 7 (2D-4): katman ve seçim renk ayarları (Android `V56Color.kt` ile aynı hesap, ≤ 1/255).
/// Sıra: HSL → renk dengesi → eğriler.
struct ColorAdjust: Equatable {
    /// Ton kaydırma (−180..180°); Renklendir'de ton.
    var hue: Float = 0
    /// Doygunluk ve parlaklık (−100..100).
    var saturation: Float = 0
    var lightness: Float = 0
    var colorize = false
    /// [aralık 0 gölgeler, 1 ara tonlar, 2 parlak alanlar][kanal 0 C–R, 1 M–G, 2 Y–B], −100..100.
    var balance: [[Float]] = ColorAdjust.zeroBalance
    var preserveLuminosity = true
    /// [0 RGB, 1 R, 2 G, 3 B] için x = 0, .25, .5, .75, 1 noktalarındaki çıkış (0..1).
    var curves: [[Float]] = ColorAdjust.identityCurves

    static let zeroBalance: [[Float]] = Array(repeating: [0, 0, 0], count: 3)
    static let curveX: [Float] = [0, 0.25, 0.5, 0.75, 1]
    static let identityCurves: [[Float]] = Array(repeating: curveX, count: 4)

    var hslIdentity: Bool { !colorize && hue == 0 && saturation == 0 && lightness == 0 }
    var balanceIdentity: Bool { balance.allSatisfy { $0.allSatisfy { $0 == 0 } } }
    var curvesIdentity: Bool { curves == ColorAdjust.identityCurves }
    var isIdentity: Bool { hslIdentity && balanceIdentity && curvesIdentity }

    func withBalance(_ range: Int, _ channel: Int, _ v: Float) -> ColorAdjust {
        var c = self; c.balance[range][channel] = v; return c
    }

    func withCurve(_ channel: Int, _ index: Int, _ v: Float) -> ColorAdjust {
        var c = self; c.curves[channel][index] = min(max(v, 0), 1); return c
    }
}

enum ColorAdjustOps {
    /// Eğri için 256 girdili arama tablosu (Catmull-Rom, uçlarda doğrusal uzatma; 0..255).
    static func curveLut(_ ys: [Float]) -> [Int] {
        let xs = ColorAdjust.curveX
        let n = xs.count
        return (0..<256).map { i in
            let x = Float(i) / 255
            var k = 0
            while k < n - 2 && x > xs[k + 1] { k += 1 }
            let t = min(max((x - xs[k]) / (xs[k + 1] - xs[k]), 0), 1)
            let p1 = ys[k], p2 = ys[k + 1]
            let p0 = k > 0 ? ys[k - 1] : 2 * p1 - p2
            let p3 = k + 2 < n ? ys[k + 2] : 2 * p2 - p1
            let t2 = t * t, t3 = t2 * t
            let a = 2 * p1 + (-p0 + p2) * t
            let b = (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
            let c = (-p0 + 3 * p1 - 3 * p2 + p3) * t3
            let v = 0.5 * (a + b + c)
            return Int((min(max(v, 0), 1) * 255).rounded())
        }
    }

    /// Ayarın piksel hesaplayıcısı (tablolar bir kez kurulur).
    struct Mapper {
        let adj: ColorAdjust
        private let luts: [[Int]]?

        init(_ adj: ColorAdjust) {
            self.adj = adj
            luts = adj.curvesIdentity ? nil : (0..<4).map { ColorAdjustOps.curveLut(adj.curves[$0]) }
        }

        func map(_ argb: UInt32) -> UInt32 {
            let a = argb >> 24
            if a == 0 || adj.isIdentity { return argb }
            var r = Float((argb >> 16) & 0xFF) / 255
            var g = Float((argb >> 8) & 0xFF) / 255
            var b = Float(argb & 0xFF) / 255
            if !adj.hslIdentity {
                let hsl = ColorAdjustOps.rgbToHsl(r, g, b)
                let k = adj.saturation / 100, lk = adj.lightness / 100
                let h: Float, s: Float
                if adj.colorize {
                    h = adj.hue
                    s = min(max(0.25 + (k >= 0 ? 0.75 * k : 0.25 * k), 0), 1)
                } else {
                    h = hsl.0 + adj.hue
                    s = min(max(k >= 0 ? hsl.1 + (1 - hsl.1) * k : hsl.1 * (1 + k), 0), 1)
                }
                let l = min(max(lk >= 0 ? hsl.2 + (1 - hsl.2) * lk : hsl.2 * (1 + lk), 0), 1)
                (r, g, b) = ColorAdjustOps.hslToRgb(h, s, l)
            }
            if !adj.balanceIdentity {
                let lum = 0.299 * r + 0.587 * g + 0.114 * b
                let ws = min(max((0.5 - lum) * 2, 0), 1)
                let wh = min(max((lum - 0.5) * 2, 0), 1)
                let wm = min(max(1 - abs(lum - 0.5) * 2, 0), 1)
                let bl = adj.balance
                let k: Float = 0.32 / 100
                r += (bl[0][0] * ws + bl[1][0] * wm + bl[2][0] * wh) * k
                g += (bl[0][1] * ws + bl[1][1] * wm + bl[2][1] * wh) * k
                b += (bl[0][2] * ws + bl[1][2] * wm + bl[2][2] * wh) * k
                if adj.preserveLuminosity {
                    let d = lum - (0.299 * r + 0.587 * g + 0.114 * b)
                    r += d; g += d; b += d
                }
            }
            var ri = ColorAdjustOps.to8(r), gi = ColorAdjustOps.to8(g), bi = ColorAdjustOps.to8(b)
            if let l = luts {
                ri = l[0][l[1][ri]]; gi = l[0][l[2][gi]]; bi = l[0][l[3][bi]]
            }
            return (a << 24) | (UInt32(ri) << 16) | (UInt32(gi) << 8) | UInt32(bi)
        }

        /// RGBA (önceden çarpılmamış) bayt dizisi üzerinde yerinde.
        func mapRGBA(_ px: inout [UInt8]) {
            var i = 0
            while i + 3 < px.count {
                let c = (UInt32(px[i + 3]) << 24) | (UInt32(px[i]) << 16) | (UInt32(px[i + 1]) << 8) | UInt32(px[i + 2])
                let o = map(c)
                px[i] = UInt8((o >> 16) & 0xFF); px[i + 1] = UInt8((o >> 8) & 0xFF); px[i + 2] = UInt8(o & 0xFF)
                i += 4
            }
        }
    }

    static func apply(_ argb: UInt32, _ adj: ColorAdjust) -> UInt32 { Mapper(adj).map(argb) }

    static func to8(_ v: Float) -> Int { Int((min(max(v, 0), 1) * 255).rounded()) }

    static func rgbToHsl(_ r: Float, _ g: Float, _ b: Float) -> (Float, Float, Float) {
        let mx = max(r, g, b), mn = min(r, g, b)
        let l = (mx + mn) / 2
        if mx == mn { return (0, 0, l) }
        let d = mx - mn
        let s = l > 0.5 ? d / (2 - mx - mn) : d / (mx + mn)
        let h: Float
        if mx == r { h = (g - b) / d + (g < b ? 6 : 0) } else if mx == g { h = (b - r) / d + 2 } else { h = (r - g) / d + 4 }
        return (h * 60, s, l)
    }

    private static func hue2rgb(_ p: Float, _ q: Float, _ t0: Float) -> Float {
        var t = t0
        if t < 0 { t += 1 }
        if t > 1 { t -= 1 }
        if t < 1.0 / 6 { return p + (q - p) * 6 * t }
        if t < 0.5 { return q }
        if t < 2.0 / 3 { return p + (q - p) * (2.0 / 3 - t) * 6 }
        return p
    }

    static func hslToRgb(_ h0: Float, _ s: Float, _ l: Float) -> (Float, Float, Float) {
        if s <= 0 { return (l, l, l) }
        let h = (h0.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360) / 360
        let q = l < 0.5 ? l * (1 + s) : l + s - l * s
        let p = 2 * l - q
        return (hue2rgb(p, q, h + 1.0 / 3), hue2rgb(p, q, h), hue2rgb(p, q, h - 1.0 / 3))
    }

    // MARK: renk metni

    static func parseHex(_ hex: String) -> UInt32? {
        var h = hex.trimmingCharacters(in: .whitespaces)
        if h.hasPrefix("#") { h.removeFirst() }
        guard let v = UInt32(h, radix: 16) else { return nil }
        switch h.count {
        case 6: return 0xFF00_0000 | v
        case 8: return v
        default: return nil
        }
    }

    static func formatHex(_ argb: UInt32, eightDigits: Bool) -> String {
        eightDigits ? String(format: "#%08X", argb) : String(format: "#%06X", argb & 0xFFFFFF)
    }

    static func mapHex(_ hex: String, _ m: Mapper) -> String {
        guard let c = parseHex(hex) else { return hex }
        let out = m.map(c)
        if out == c { return hex }
        var h = hex.trimmingCharacters(in: .whitespaces)
        if h.hasPrefix("#") { h.removeFirst() }
        return formatHex(out, eightDigits: h.count == 8)
    }

    private static func mapGradient(_ g: GradientSpec?, _ m: Mapper) -> GradientSpec? {
        guard var g else { return nil }
        g.colors = g.colors.map { mapHex($0, m) }
        return g
    }

    /// Vektör elemanın bütün renkleri; resim ve silgi değişmez.
    static func mapStroke(_ s: Stroke, _ m: Mapper) -> Stroke {
        if s.brushType == .IMAGE || s.brushType == .ERASER { return s }
        var o = s
        o.colorHex = mapHex(s.colorHex, m)
        if var sh = s.shape {
            sh.style.strokeColorHex = mapHex(sh.style.strokeColorHex, m)
            sh.style.fillColorHex = sh.style.fillColorHex.map { mapHex($0, m) }
            sh.style.fillGradient = mapGradient(sh.style.fillGradient, m)
            o.shape = sh
        }
        if var t = s.text {
            t.outlineColorHex = t.outlineColorHex.map { mapHex($0, m) }
            if var b = t.box {
                b.fillHex = b.fillHex.map { mapHex($0, m) }
                b.borderHex = b.borderHex.map { mapHex($0, m) }
                b.fillGradient = mapGradient(b.fillGradient, m)
                if var e = b.effects {
                    e.shadowHex = e.shadowHex.map { mapHex($0, m) }
                    e.outerHex = e.outerHex.map { mapHex($0, m) }
                    e.gradient = mapGradient(e.gradient, m)
                    b.effects = e
                }
                t.box = b
            }
            o.text = t
        }
        return o
    }

    static func mapStrokes(_ strokes: [Stroke], _ ids: Set<String>, _ adj: ColorAdjust) -> [Stroke] {
        if adj.isIdentity { return strokes }
        let m = Mapper(adj)
        return strokes.map { ids.contains($0.id) ? mapStroke($0, m) : $0 }
    }
}

/// v56 aşama 7 katman işlemleri (Android `LayerOps`).
enum LayerOps {
    static func needsRaster(_ layers: [Layer], _ ids: Set<String>) -> Bool { layers.contains { ids.contains($0.id) && $0.composited } }

    static func duplicate(_ layers: [Layer], _ pages: [[Stroke]], _ id: String, copyId: String, name: String, newId: () -> String) -> ([Layer], [[Stroke]]) {
        guard let i = layers.firstIndex(where: { $0.id == id }) else { return (layers, pages) }
        var copy = layers[i]
        copy.id = copyId; copy.name = name; copy.locked = false
        var outLayers = layers
        outLayers.insert(copy, at: i + 1)
        var groups: [String: String] = [:]
        let outPages = pages.map { page -> [Stroke] in
            var out = page
            for s in page where Layers.of(s, layers) == id {
                var c = s
                c.id = newId(); c.layerId = copyId
                if let g = s.groupId {
                    if let n = groups[g] { c.groupId = n } else { let n = newId(); groups[g] = n; c.groupId = n }
                }
                out.append(c)
            }
            return Layers.sort(out, outLayers)
        }
        return (outLayers, outPages)
    }

    static func mergeVector(_ layers: [Layer], _ pages: [[Stroke]], _ ids: Set<String>) -> ([Layer], [[Stroke]]) {
        let chosen = layers.filter { ids.contains($0.id) }
        guard chosen.count >= 2, let target = chosen.last else { return (layers, pages) }
        let outLayers = layers.filter { !ids.contains($0.id) || $0.id == target.id }
        let outPages = pages.map { page -> [Stroke] in
            let moved = page.enumerated().sorted { a, b in
                let ia = Layers.indexOf(a.element, layers), ib = Layers.indexOf(b.element, layers)
                return ia != ib ? ia < ib : a.offset < b.offset
            }.map(\.element)
            return Layers.sort(moved.map { s in
                guard ids.contains(Layers.of(s, layers)) else { return s }
                var c = s; c.layerId = target.id == outLayers.first?.id ? nil : target.id; return c
            }, outLayers)
        }
        return (outLayers, outPages)
    }

    static func below(_ layers: [Layer], _ id: String) -> Layer? {
        guard let i = layers.firstIndex(where: { $0.id == id }), i > 0 else { return nil }
        return layers[i - 1]
    }
}

/// v56 aşama 7 hazır ayarlar.
enum V56Presets {
    enum Kind { case LIGHT, SHADOW }
    struct LayerPreset { let blend: LayerBlend; let opacity: Float; let colorHex: String; let brush: BrushType; let size: Float; let brushOpacity: Float }

    static func layer(_ kind: Kind) -> LayerPreset {
        switch kind {
        case .LIGHT: return LayerPreset(blend: .SCREEN, opacity: 0.7, colorHex: "#FFE9A8", brush: .AIRBRUSH, size: 80, brushOpacity: 0.35)
        case .SHADOW: return LayerPreset(blend: .MULTIPLY, opacity: 0.6, colorHex: "#5B4B8A", brush: .AIRBRUSH, size: 80, brushOpacity: 0.35)
        }
    }

    static let glitter = LayerPreset(blend: .ADD, opacity: 1, colorHex: "#FFF3B8", brush: .STAMP_STAR, size: 22, brushOpacity: 0.9)
}
