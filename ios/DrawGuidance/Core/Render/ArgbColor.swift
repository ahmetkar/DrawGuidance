import CoreGraphics
import Foundation

/// Android `android.graphics.Color` işlemlerinin karşılığı: renkler 32 bit ARGB (`0xAARRGGBB`) tutulur, böylece
/// ton/karışım hesapları Android ile aynı tamsayı sonuçları verir.
enum ArgbColor {
    static let black: UInt32 = 0xFF00_0000
    static let white: UInt32 = 0xFFFF_FFFF

    private static let names: [String: UInt32] = [
        "black": 0xFF000000, "darkgray": 0xFF444444, "gray": 0xFF888888, "lightgray": 0xFFCCCCCC,
        "white": 0xFFFFFFFF, "red": 0xFFFF0000, "green": 0xFF00FF00, "blue": 0xFF0000FF, "yellow": 0xFFFFFF00,
        "cyan": 0xFF00FFFF, "magenta": 0xFFFF00FF, "aqua": 0xFF00FFFF, "fuchsia": 0xFFFF00FF,
        "darkgrey": 0xFF444444, "grey": 0xFF888888, "lightgrey": 0xFFCCCCCC, "lime": 0xFF00FF00,
        "maroon": 0xFF800000, "navy": 0xFF000080, "olive": 0xFF808000, "purple": 0xFF800080,
        "silver": 0xFFC0C0C0, "teal": 0xFF008080, "transparent": 0x00000000,
    ]

    /// `Color.parseColor`: "#RRGGBB", "#AARRGGBB" ya da renk adı; geçersizse nil.
    static func parse(_ text: String) -> UInt32? {
        if text.hasPrefix("#") {
            let body = text.dropFirst()
            guard body.count == 6 || body.count == 8, let v = UInt32(body, radix: 16) else { return nil }
            return body.count == 6 ? (v | 0xFF00_0000) : v
        }
        return names[text.lowercased()]
    }

    /// Uygulamadaki `hexToColor`: çözülemeyen renk siyahtır.
    static func hex(_ text: String) -> UInt32 { parse(text) ?? black }

    static func alpha(_ c: UInt32) -> Int { Int(c >> 24 & 0xFF) }
    static func red(_ c: UInt32) -> Int { Int(c >> 16 & 0xFF) }
    static func green(_ c: UInt32) -> Int { Int(c >> 8 & 0xFF) }
    static func blue(_ c: UInt32) -> Int { Int(c & 0xFF) }

    static func rgb(_ r: Int, _ g: Int, _ b: Int) -> UInt32 {
        0xFF00_0000 | UInt32(r & 0xFF) << 16 | UInt32(g & 0xFF) << 8 | UInt32(b & 0xFF)
    }

    static func withAlpha(_ c: UInt32, _ a: Int) -> UInt32 { (c & 0x00FF_FFFF) | UInt32(max(0, min(255, a))) << 24 }

    /// Kotlin `Float.roundToInt()`.
    static func roundToInt(_ v: Float) -> Int { Int((Double(v) + 0.5).rounded(.down)) }

    /// `SurfaceTextures.mix`.
    static func mix(_ a: UInt32, _ b: UInt32, _ t: Float) -> UInt32 {
        let f = min(max(t, 0), 1)
        func ch(_ x: Int, _ y: Int) -> Int { min(max(roundToInt(Float(x) + Float(y - x) * f), 0), 255) }
        return rgb(ch(red(a), red(b)), ch(green(a), green(b)), ch(blue(a), blue(b)))
    }

    /// `SurfaceTextures.tone`: 0.5 rengin kendisi, altı koyu, üstü açık.
    static func tone(_ base: UInt32, _ l: Float) -> UInt32 {
        let v = min(max(l, 0), 1)
        if v <= 0.5 {
            let f = 0.25 + 1.5 * v
            return rgb(roundToInt(Float(red(base)) * f), roundToInt(Float(green(base)) * f), roundToInt(Float(blue(base)) * f))
        }
        return mix(base, white, (v - 0.5) * 1.5)
    }

    static func cgColor(_ c: UInt32) -> CGColor {
        CGColor(srgbRed: CGFloat(red(c)) / 255, green: CGFloat(green(c)) / 255, blue: CGFloat(blue(c)) / 255,
                alpha: CGFloat(alpha(c)) / 255)
    }

    /// Android `Paint.color` + `Paint.alpha` (0–255 tamsayı saydamlık; rengin kendi saydamlığının yerine geçer).
    static func cgColor(_ c: UInt32, alpha a: Int) -> CGColor { cgColor(withAlpha(c, a)) }

    static func hexRGB(_ c: UInt32) -> String { String(format: "#%06X", c & 0x00FF_FFFF) }
}

/// v27 gradyanlar (Android `Gradients`): doğrusal gradyan kutunun merkezinden geçer, dairesel merkezden köşelere.
enum Gradients {
    static let presets: [GradientSpec] = [
        GradientSpec(colors: ["#87CEEB", "#E0F7FF"], angle: 90),
        GradientSpec(colors: ["#FF7E5F", "#FEB47B"], angle: 90),
        GradientSpec(colors: ["#654EA3", "#EAAFC8"], angle: 45),
        GradientSpec(colors: ["#11998E", "#38EF7D"], angle: 0),
        GradientSpec(colors: ["#FFF6B7", "#F6416C"], angle: 135),
        GradientSpec(colors: ["#0F2027", "#203A43", "#2C5364"], angle: 90),
        GradientSpec(colors: ["#FFFFFF", "#FFE082"], angle: 0, radial: true),
        GradientSpec(colors: ["#FFFFFF", "#90CAF9"], angle: 0, radial: true),
        GradientSpec(colors: ["#F8FF00", "#3AD59F"], angle: 90),
        GradientSpec(colors: ["#FF0844", "#FFB199"], angle: 45),
        GradientSpec(colors: ["#000000", "#434343"], angle: 90),
        GradientSpec(colors: ["#FDFBFB", "#EBEDEE"], angle: 90),
    ]

    static func colors(_ spec: GradientSpec, alpha: Float = 1) -> [UInt32] {
        spec.colors.map { s in
            let c = ArgbColor.parse(s) ?? ArgbColor.white
            let a = min(max(Int(Float(c >> 24) * min(max(alpha, 0), 1)), 0), 255)
            return ArgbColor.withAlpha(c, a)
        }
    }

    /// Verilen kutuda gradyanı çizer (geçerli kırpma alanının içine). Geçersizse false.
    @discardableResult
    static func fill(_ ctx: CGContext, _ spec: GradientSpec?, l: Float, t: Float, r: Float, b: Float, alpha: Float = 1) -> Bool {
        guard let spec, spec.valid, r - l >= 1e-3, b - t >= 1e-3 else { return false }
        let cols = colors(spec, alpha: alpha).map { ArgbColor.cgColor($0) } as CFArray
        guard let g = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: cols, locations: nil) else { return false }
        let cx = CGFloat(l + r) / 2, cy = CGFloat(t + b) / 2
        let opts: CGGradientDrawingOptions = [.drawsBeforeStartLocation, .drawsAfterEndLocation]
        if spec.radial {
            let radius = max(CGFloat(hypotf(r - l, b - t)) / 2, 1)
            ctx.drawRadialGradient(g, startCenter: CGPoint(x: cx, y: cy), startRadius: 0, endCenter: CGPoint(x: cx, y: cy), endRadius: radius, options: opts)
        } else {
            let a = Double(spec.angle) * .pi / 180
            let dx = CGFloat(cos(a)), dy = CGFloat(sin(a))
            let half = (abs(CGFloat(r - l) * dx) + abs(CGFloat(b - t) * dy)) / 2
            ctx.drawLinearGradient(g, start: CGPoint(x: cx - dx * half, y: cy - dy * half), end: CGPoint(x: cx + dx * half, y: cy + dy * half), options: opts)
        }
        return true
    }
}
