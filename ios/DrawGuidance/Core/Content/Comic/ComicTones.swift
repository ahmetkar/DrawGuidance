import Foundation

/// v53 çizgi roman ekran tonları ve desenleri (Android `content.comic.ComicTones` ile aynı hesap). Ton bir şeklin
/// dolgusu olarak saklanır (`ToneFill`) ve şeklin kapalı alanına kırpılarak çizilir.
enum ComicTones {
    enum Group { case TONE, PATTERN }

    struct Preset: Identifiable, Equatable {
        let key: String
        let id: String
        let group: Group
        let density: Float
        var scale: Float = 1
        var angle: Float = 45
        var nameKey: String { "v53_tone_\(key)" }
        var usageKey: String { "v53_tone_\(key)_use" }
    }

    static let TONES: [Preset] = [
        Preset(key: "skin", id: "tone_dot", group: .TONE, density: 0.10, scale: 0.7),
        Preset(key: "soft_shadow", id: "tone_dot", group: .TONE, density: 0.28),
        Preset(key: "deep_shadow", id: "tone_dot", group: .TONE, density: 0.55),
        Preset(key: "night", id: "tone_dot", group: .TONE, density: 0.8, scale: 0.8),
        Preset(key: "sky", id: "tone_dot_grad", group: .TONE, density: 0.6),
        Preset(key: "glow", id: "tone_dot_radial", group: .TONE, density: 0.55),
        Preset(key: "sand", id: "tone_sand", group: .TONE, density: 0.3),
        Preset(key: "lines", id: "tone_lines", group: .TONE, density: 0.3, scale: 1, angle: 45),
        Preset(key: "hatch", id: "tone_hatch", group: .TONE, density: 0.35, scale: 1, angle: 45),
        Preset(key: "kakeami", id: "tone_kakeami", group: .TONE, density: 0.45),
    ]

    static let PATTERNS: [Preset] = [
        Preset(key: "flowers", id: "pat_flowers", group: .PATTERN, density: 0.6),
        Preset(key: "sparkle", id: "pat_sparkle", group: .PATTERN, density: 0.6),
        Preset(key: "bubbles", id: "pat_bubbles", group: .PATTERN, density: 0.5),
        Preset(key: "nawaami", id: "pat_nawaami", group: .PATTERN, density: 0.5),
        Preset(key: "gloom", id: "pat_vlines", group: .PATTERN, density: 0.6),
        Preset(key: "stars", id: "pat_stars", group: .PATTERN, density: 0.5),
        Preset(key: "hearts", id: "pat_hearts", group: .PATTERN, density: 0.5),
        Preset(key: "zigzag", id: "pat_zigzag", group: .PATTERN, density: 0.5, scale: 1, angle: 0),
        Preset(key: "polka", id: "pat_polka", group: .PATTERN, density: 0.5),
        Preset(key: "stripes", id: "pat_stripes", group: .PATTERN, density: 0.5, scale: 1, angle: 45),
        Preset(key: "plaid", id: "pat_plaid", group: .PATTERN, density: 0.5, scale: 1, angle: 0),
        Preset(key: "check", id: "pat_check", group: .PATTERN, density: 0.5, scale: 1, angle: 0),
        Preset(key: "brick", id: "pat_brick", group: .PATTERN, density: 0.5, scale: 1, angle: 0),
        Preset(key: "wood", id: "pat_wood", group: .PATTERN, density: 0.5, scale: 1, angle: 0),
    ]

    static let ALL = TONES + PATTERNS

    static func find(_ key: String) -> Preset? { ALL.first { $0.key == key } }

    enum Prim {
        case dot(Float, Float, Float, Float)                 // x, y, r, alpha
        case ring(Float, Float, Float, Float, Float)         // x, y, r, w, alpha
        case seg(Float, Float, Float, Float, Float, Float)   // x1, y1, x2, y2, w, alpha
        case poly([Float], Bool, Bool, Float, Float)         // pts, closed, filled, w, alpha
    }

    static let MAX_PRIMS = 12_000
    static let BASE_SPACING: Float = 9

    /// Android `Rng` ile aynı dizi.
    struct Rng {
        private var s: Int64
        init(_ seed: Int64) { s = seed & 0x7fffffff }
        mutating func next() -> Float { s = (s &* 1103515245 &+ 12345) & 0x7fffffff; return Float(s) / 2147483648 }
    }

    static func prims(_ id: String, density: Float, scale: Float, angle: Float, _ l: Float, _ t: Float, _ r: Float, _ b: Float, seed: Int64 = 7) -> [Prim] {
        let w = r - l, h = b - t
        if w <= 0 || h <= 0 { return [] }
        let d = density.coerceIn(0.02, 0.95)
        var s = BASE_SPACING * scale.coerceIn(0.3, 6)
        let pattern = id.hasPrefix("pat_")
        let step0 = pattern ? s * 5 : s
        let cells = (w / step0) * (h / step0)
        if cells > Float(MAX_PRIMS) { s *= (cells / Float(MAX_PRIMS)).squareRoot() }
        var out: [Prim] = []
        var rng = Rng(seed)
        switch id {
        case "tone_dot":
            lattice(l, t, r, b, s, angle) { x, y in out.append(.dot(x, y, dotR(s, d), 1)) }
        case "tone_dot_grad":
            lattice(l, t, r, b, s, angle) { x, y in
                let k = 1 - ((y - t) / h).coerceIn(0, 1)
                let dd = d * k
                if dd > 0.015 { out.append(.dot(x, y, dotR(s, dd), 1)) }
            }
        case "tone_dot_radial":
            let cx = (l + r) / 2, cy = (t + b) / 2
            let maxD = hypotf(w / 2, h / 2)
            lattice(l, t, r, b, s, angle) { x, y in
                let k = (hypotf(x - cx, y - cy) / maxD).coerceIn(0, 1)
                let dd = d * k * k
                if dd > 0.015 { out.append(.dot(x, y, dotR(s, dd), 1)) }
            }
        case "tone_sand":
            let r0 = s * 0.16
            let n = min(Int(w * h * d / (Float.pi * r0 * r0 * 2.2)), MAX_PRIMS)
            for _ in 0..<max(n, 0) {
                let x = l + rng.next() * w
                let y = t + rng.next() * h
                out.append(.dot(x, y, r0 * (0.6 + rng.next() * 0.8), 1))
            }
        case "tone_lines":
            parallel(l, t, r, b, s, angle) { x1, y1, x2, y2 in out.append(.seg(x1, y1, x2, y2, s * d, 1)) }
        case "tone_hatch":
            parallel(l, t, r, b, s, angle) { x1, y1, x2, y2 in out.append(.seg(x1, y1, x2, y2, s * d * 0.55, 1)) }
            parallel(l, t, r, b, s, angle + 90) { x1, y1, x2, y2 in out.append(.seg(x1, y1, x2, y2, s * d * 0.55, 1)) }
        case "tone_kakeami":
            let cell = s * 4
            let lw = s * 0.11 * (0.6 + d)
            var row = 0
            var y = t
            while y < b {
                var col = 0
                var x = l
                while x < r {
                    let vertical = (row + col) % 2 == 0
                    let k = (2 + Int(d * 4)).coerceIn(2, 6)
                    for i in 0..<k {
                        let o = cell * (Float(i) + 0.5) / Float(k)
                        let j = (rng.next() - 0.5) * cell * 0.08
                        if vertical { out.append(.seg(x + o + j, y + cell * 0.06, x + o - j, y + cell * 0.94, lw, 1)) }
                        else { out.append(.seg(x + cell * 0.06, y + o + j, x + cell * 0.94, y + o - j, lw, 1)) }
                    }
                    x += cell; col += 1
                }
                y += cell; row += 1
            }
        default:
            patternPrims(id, d, s * 5, angle, l, t, r, b, &rng, &out)
        }
        return out
    }

    private static func dotR(_ s: Float, _ d: Float) -> Float { min(s * (d / Float.pi).squareRoot(), s * 0.72) }

    private static func lattice(_ l: Float, _ t: Float, _ r: Float, _ b: Float, _ s: Float, _ angle: Float, _ emit: (Float, Float) -> Void) {
        let a = angle * Float.pi / 180
        let ca = cos(a), sa = sin(a)
        let cx = (l + r) / 2, cy = (t + b) / 2
        let half = hypotf(r - l, b - t) / 2 + s
        let n = Int(ceil(half / s))
        for i in -n...n {
            for j in -n...n {
                let u = Float(i) * s, v = Float(j) * s
                let x = cx + u * ca - v * sa
                let y = cy + u * sa + v * ca
                if x >= l - s && x <= r + s && y >= t - s && y <= b + s { emit(x, y) }
            }
        }
    }

    private static func parallel(_ l: Float, _ t: Float, _ r: Float, _ b: Float, _ s: Float, _ angle: Float, _ emit: (Float, Float, Float, Float) -> Void) {
        let a = angle * Float.pi / 180
        let dx = cos(a), dy = sin(a)
        let nx = -dy, ny = dx
        let cx = (l + r) / 2, cy = (t + b) / 2
        let half = hypotf(r - l, b - t) / 2 + s
        let n = Int(ceil(half / s))
        for i in -n...n {
            let ox = cx + nx * Float(i) * s, oy = cy + ny * Float(i) * s
            emit(ox - dx * half, oy - dy * half, ox + dx * half, oy + dy * half)
        }
    }

    private static func circlePts(_ cx: Float, _ cy: Float, _ rad: Float, _ n: Int = 14) -> [Float] {
        var p = [Float](repeating: 0, count: n * 2)
        for i in 0..<n {
            let a = 2 * Float.pi * Float(i) / Float(n)
            p[2 * i] = cx + cos(a) * rad; p[2 * i + 1] = cy + sin(a) * rad
        }
        return p
    }

    private static func starPts(_ cx: Float, _ cy: Float, _ outer: Float, _ inner: Float, _ points: Int, _ rot: Float) -> [Float] {
        var p = [Float](repeating: 0, count: points * 4)
        for i in 0..<(points * 2) {
            let a = rot + Float.pi * Float(i) / Float(points) - Float.pi / 2
            let rr = i % 2 == 0 ? outer : inner
            p[2 * i] = cx + cos(a) * rr; p[2 * i + 1] = cy + sin(a) * rr
        }
        return p
    }

    private static func heartPts(_ cx: Float, _ cy: Float, _ size: Float) -> [Float] {
        let n = 20
        var p = [Float](repeating: 0, count: n * 2)
        for i in 0..<n {
            let tt = 2 * Float.pi * Float(i) / Float(n)
            let x = 16 * sin(tt) * sin(tt) * sin(tt)
            let y = -(13 * cos(tt) - 5 * cos(2 * tt) - 2 * cos(3 * tt) - cos(4 * tt))
            p[2 * i] = cx + x / 17 * size; p[2 * i + 1] = cy + y / 17 * size
        }
        return p
    }

    private static func patternPrims(_ id: String, _ d: Float, _ S: Float, _ angle: Float, _ l: Float, _ t: Float, _ r: Float, _ b: Float,
                                     _ rng: inout Rng, _ out: inout [Prim]) {
        let w = r - l, h = b - t
        let lw = S * 0.035 * (0.6 + d)
        func scatter(_ jitter: Float, _ emit: (Float, Float, Float, inout Rng) -> Void) {
            var row = 0
            var y = t - S / 2
            while y < b + S {
                var x = l - S / 2 + (row % 2 == 1 ? S / 2 : 0)
                while x < r + S {
                    let jx = (rng.next() - 0.5) * S * jitter
                    let jy = (rng.next() - 0.5) * S * jitter
                    let k = 0.65 + rng.next() * 0.7
                    emit(x + jx, y + jy, k, &rng)
                    x += S
                }
                y += S * 0.86; row += 1
            }
        }
        switch id {
        case "pat_flowers":
            scatter(0.5) { x, y, k, _ in
                let pr = S * 0.16 * k
                for i in 0..<5 {
                    let a = 2 * Float.pi * Float(i) / 5 + k
                    out.append(.poly(circlePts(x + cos(a) * pr * 1.25, y + sin(a) * pr * 1.25, pr), true, false, lw, 1))
                }
                out.append(.dot(x, y, pr * 0.55, 1))
            }
        case "pat_sparkle":
            scatter(0.8) { x, y, k, _ in
                let o = S * 0.22 * k * (0.5 + d)
                out.append(.poly(starPts(x, y, o, o * 0.18, 4, 0), true, true, 0, 1))
                if k > 1.1 { out.append(.dot(x + o * 1.3, y - o * 1.1, o * 0.12, 1)) }
            }
        case "pat_bubbles":
            scatter(0.9) { x, y, k, _ in
                let rr = S * 0.22 * k
                out.append(.ring(x, y, rr, lw, 1))
                out.append(.dot(x - rr * 0.42, y - rr * 0.42, rr * 0.16, 1))
            }
        case "pat_nawaami":
            let gap = S * 0.45
            var o = -h
            while o < w + h {
                for dir: Float in [1, -1] {
                    var pts: [Float] = []
                    var k: Float = 0
                    while k <= h + gap {
                        let x = dir > 0 ? l + o + k : r - o - k
                        let y = t + k
                        let wob = sin(k / gap * Float.pi) * gap * 0.18
                        pts.append(x + wob); pts.append(y - wob)
                        k += gap / 3
                    }
                    out.append(.poly(pts, false, false, lw * 1.1, 1))
                }
                o += gap * 2.2
            }
        case "pat_vlines":
            let gap = S * 0.16
            var x = l
            while x < r {
                let len = h * (0.25 + rng.next() * 0.55) * (0.6 + d * 0.6)
                let ww = gap * (0.18 + rng.next() * 0.25)
                out.append(.seg(x, t, x, t + len, ww, 1))
                x += gap * (0.7 + rng.next() * 0.8)
            }
        case "pat_stars":
            scatter(0.9) { x, y, k, rg in
                let o = S * 0.18 * k
                out.append(.poly(starPts(x, y, o, o * 0.45, 5, rg.next()), true, true, 0, 1))
            }
        case "pat_hearts":
            scatter(0.6) { x, y, k, _ in out.append(.poly(heartPts(x, y, S * 0.2 * k), true, true, 0, 1)) }
        case "pat_zigzag":
            let amp = S * 0.18
            var y = t
            while y < b + S {
                var pts: [Float] = []
                var x = l - S
                var up = true
                while x < r + S { pts.append(x); pts.append(y + (up ? -amp : amp)); x += S * 0.3; up.toggle() }
                out.append(.poly(pts, false, false, lw * 1.4, 1))
                y += S * 0.6
            }
        case "pat_polka":
            var row = 0
            var y = t
            while y < b + S {
                var x = l + (row % 2 == 1 ? S * 0.5 : 0)
                while x < r + S { out.append(.dot(x, y, S * 0.17 * (0.6 + d), 1)); x += S }
                y += S * 0.5; row += 1
            }
        case "pat_stripes":
            parallel(l, t, r, b, S * 0.5, angle) { x1, y1, x2, y2 in out.append(.seg(x1, y1, x2, y2, S * 0.2 * (0.5 + d), 1)) }
        case "pat_plaid":
            parallel(l, t, r, b, S * 0.9, angle) { x1, y1, x2, y2 in out.append(.seg(x1, y1, x2, y2, S * 0.28, 0.45)) }
            parallel(l, t, r, b, S * 0.9, angle + 90) { x1, y1, x2, y2 in out.append(.seg(x1, y1, x2, y2, S * 0.28, 0.45)) }
            parallel(l, t, r, b, S * 0.3, angle) { x1, y1, x2, y2 in out.append(.seg(x1, y1, x2, y2, lw * 0.7, 0.7)) }
        case "pat_check":
            let c = S * 0.45
            var row = 0
            var y = t
            while y < b {
                var col = 0
                var x = l
                while x < r {
                    if (row + col) % 2 == 0 { out.append(.poly([x, y, x + c, y, x + c, y + c, x, y + c], true, true, 0, 0.35 + d * 0.5)) }
                    x += c; col += 1
                }
                y += c; row += 1
            }
        case "pat_brick":
            let bh = S * 0.3, bw = S * 0.65
            var row = 0
            var y = t
            while y < b {
                out.append(.seg(l, y, r, y, lw, 1))
                var x = l + (row % 2 == 1 ? bw / 2 : 0)
                while x < r { out.append(.seg(x, y, x, min(y + bh, b), lw, 1)); x += bw }
                y += bh; row += 1
            }
        case "pat_wood":
            let gap = S * 0.22
            var y = t
            var i: Float = 0
            while y < b + gap {
                var pts: [Float] = []
                var x = l
                let ph = rng.next() * 6
                while x <= r + gap {
                    pts.append(x); pts.append(y + sin(x / (S * 0.9) + ph) * gap * 0.35 + sin(x / (S * 0.23) + i) * gap * 0.06)
                    x += gap
                }
                out.append(.poly(pts, false, false, lw * (0.6 + rng.next() * 0.8), 1))
                y += gap * (0.8 + rng.next() * 0.5); i += 1
            }
            let knots = max(1, min(Int(w * h / (S * S * 6)), 12))
            for _ in 0..<knots {
                let x = l + rng.next() * w
                let y = t + rng.next() * h
                var p = circlePts(x, y, S * 0.12, 12)
                var k = 0
                while k < p.count { p[k] = x + (p[k] - x) * 2.2; k += 2 }
                out.append(.poly(p, true, false, lw, 1))
            }
        default:
            break
        }
    }
}
