import Foundation

/// v27 çizgi roman yardımcıları (Android `content.comic.Comic`): sayfa panel düzenleri, hız/odak/patlama çizgileri ve
/// efekt yazısı hazırları. Çizim katmanı bunları tuval elemanlarına çevirir.

/// Panel düzeni: sayfanın kenar boşlukları içinde oranlarla verilmiş kareler (sol, üst, sağ, alt; 0..1).
struct PanelLayout {
    let id: String
    let name: String
    let cells: [[Float]]
}

enum ComicPanels {
    private static func r(_ l: Float, _ t: Float, _ rr: Float, _ b: Float) -> [Float] { [l, t, rr, b] }

    static let layouts: [PanelLayout] = [
        PanelLayout(id: "single", name: "Tek kare", cells: [r(0, 0, 1, 1)]),
        PanelLayout(id: "rows2", name: "İki şerit", cells: [r(0, 0, 1, 0.5), r(0, 0.5, 1, 1)]),
        PanelLayout(id: "cols2", name: "İki sütun", cells: [r(0, 0, 0.5, 1), r(0.5, 0, 1, 1)]),
        PanelLayout(id: "rows3", name: "Üç şerit", cells: [r(0, 0, 1, 1 / 3), r(0, 1 / 3, 1, 2 / 3), r(0, 2 / 3, 1, 1)]),
        PanelLayout(id: "grid4", name: "Dörtlü", cells: [r(0, 0, 0.5, 0.5), r(0.5, 0, 1, 0.5), r(0, 0.5, 0.5, 1), r(0.5, 0.5, 1, 1)]),
        PanelLayout(id: "grid6", name: "Altılı", cells: (0..<6).map { i in
            let c = Float(i % 2), row = Float(i / 2)
            return r(c / 2, row / 3, (c + 1) / 2, (row + 1) / 3)
        }),
        PanelLayout(id: "big_top", name: "Büyük + iki", cells: [r(0, 0, 1, 0.58), r(0, 0.58, 0.5, 1), r(0.5, 0.58, 1, 1)]),
        PanelLayout(id: "big_bottom", name: "İki + büyük", cells: [r(0, 0, 0.5, 0.42), r(0.5, 0, 1, 0.42), r(0, 0.42, 1, 1)]),
        PanelLayout(id: "manga", name: "Manga",
                    cells: [r(0, 0, 1, 0.3), r(0, 0.3, 0.62, 0.65), r(0.62, 0.3, 1, 0.65), r(0, 0.65, 0.38, 1), r(0.38, 0.65, 1, 1)]),
        PanelLayout(id: "strip3", name: "Gazete şeridi", cells: [r(0, 0, 1 / 3, 1), r(1 / 3, 0, 2 / 3, 1), r(2 / 3, 0, 1, 1)]),
    ]

    static func byId(_ id: String) -> PanelLayout? { layouts.first { $0.id == id } }

    /// Düzenin karelerinin tuval dikdörtgenleri: [w]×[h] sayfada [margin] kenar boşluğu ve kareler arası [gutter].
    static func rects(_ layout: PanelLayout, _ w: Float, _ h: Float, margin: Float = 0.045, gutter: Float = 0.022) -> [[Float]] {
        let m = min(w, h) * margin
        let g = min(w, h) * gutter / 2
        let iw = w - 2 * m, ih = h - 2 * m
        return layout.cells.map { c in
            let l = m + c[0] * iw + (c[0] > 0.001 ? g : 0)
            let t = m + c[1] * ih + (c[1] > 0.001 ? g : 0)
            let rr = m + c[2] * iw - (c[2] < 0.999 ? g : 0)
            let b = m + c[3] * ih - (c[3] < 0.999 ? g : 0)
            return [l, t, max(rr, l + 1), max(b, t + 1)]
        }
    }
}

/// Çizgi türleri (v27 hız/odak/patlama; v29 manga efekt çizgileri; v36 yakınlaştırma, derinlik, ışınlar…).
enum ComicLinesKind: String, KotlinEnum {
    case SPEED_H, SPEED_V, FOCUS, BURST
    case SPEED_DIAG, MOTION_CURVE, FOCUS_ROUGH, FLASH, RADIATE, DRIP, WAVER, BLUSH, SWIRL, RINGS
    case ZOOM, PERSPECTIVE, BEAMS, MANGA_DENSE, ANIME_SOFT
    static var fallback: ComicLinesKind { .FOCUS }
}

/// Tek çizgi: noktalar ve nokta başına basınç (incelme).
struct ComicLine {
    var xs: [Float]
    var ys: [Float]
    var pressures: [Float]
}

enum ComicLines {
    static let MIN_DENSITY: Float = 0.25
    static let MAX_DENSITY: Float = 3
    static let MIN_LENGTH: Float = 0.2
    static let MAX_LENGTH: Float = 1.6

    static func usesFocus(_ kind: ComicLinesKind) -> Bool { FOCUSED.contains(kind) }

    static func usesAngle(_ kind: ComicLinesKind) -> Bool { kind == .SPEED_H || kind == .SPEED_V || kind == .SPEED_DIAG || kind == .BEAMS }

    static func widthFactor(_ kind: ComicLinesKind) -> Float {
        switch kind {
        case .BEAMS: return 5
        case .MANGA_DENSE: return 1.7
        case .ANIME_SOFT: return 3.2
        default: return 1
        }
    }

    static let SOFT_COLORS = ["#FF9EC7", "#8FD3FF", "#C7A6FF", "#FFE08A", "#9FF0D0"]

    private static let FOCUSED: Set<ComicLinesKind> = [.FOCUS, .BURST, .FOCUS_ROUGH, .FLASH, .RADIATE, .SWIRL, .RINGS, .WAVER,
                                                       .ZOOM, .PERSPECTIVE, .BEAMS, .MANGA_DENSE, .ANIME_SOFT]

    private static let PI = Double.pi

    /// [l]…[b] alanında çizgiler; sabit tohumla her seferinde aynı düzen (Android ile aynı dizi).
    static func generate(_ kind: ComicLinesKind, _ l: Float, _ t: Float, _ r: Float, _ b: Float, seed: Int = 27, density: Float = 1,
                         focusX: Float = 0.5, focusY: Float = 0.5, angle: Float = 0, length: Float = 1) -> [ComicLine] {
        var rnd = KotlinRandom(seed)
        let w = r - l, h = b - t
        if w < 2 || h < 2 { return [] }
        let d = clampF(density, MIN_DENSITY, MAX_DENSITY)
        func count(_ base: Int, _ lo: Int, _ hi: Int) -> Int { clampI(ktInt(Float(base) * d), lo, hi) }
        let len = clampF(length, MIN_LENGTH, MAX_LENGTH)
        let cx = l + w * clampF(focusX, 0, 1), cy = t + h * clampF(focusY, 0, 1)
        let centered = abs(focusX - 0.5) < 1e-4 && abs(focusY - 0.5) < 1e-4
        func inner(_ base: Float) -> Float { len == 1 ? base : clampF(1 - (1 - base) * len, 0.02, 0.97) }
        func edge(_ ca: Float, _ sa: Float) -> Float { centered ? edgeDistance(ca, sa, w, h) : edgeFrom(cx, cy, ca, sa, l, t, r, b) }
        func innerAt(_ ca: Float, _ sa: Float, _ f: Float, _ outer: Float) -> (Float, Float) {
            let ex = ca * (w / 2) * f, ey = sa * (h / 2) * f
            let dist = kHypot(ex, ey)
            if centered || dist <= outer * 0.92 { return (cx + ex, cy + ey) }
            let k = outer * 0.92 / max(dist, 1e-3)
            return (cx + ex * k, cy + ey * k)
        }
        func dir(_ a: Double) -> (Float, Float) { (Float(cos(a)), Float(sin(a))) }
        var out: [ComicLine] = []
        if angle != 0 && (kind == .SPEED_H || kind == .SPEED_V || kind == .SPEED_DIAG) {
            let base: Double = kind == .SPEED_H ? 0 : (kind == .SPEED_V ? 90 : -28)
            parallel(&out, &rnd, (base + Double(angle)) * PI / 180, l, t, r, b, count(kind == .SPEED_DIAG ? 30 : 26, 4, 130), len)
            return out
        }
        switch kind {
        case .SPEED_H, .SPEED_V:
            let horizontal = kind == .SPEED_H
            let across = horizontal ? h : w, along = horizontal ? w : h
            let n = count(26, 4, 120)
            for i in 0..<n {
                let pos = (Float(i) + 0.5 + (rnd.nextFloat() - 0.5) * 0.8) / Float(n) * across
                let ln = min(along * (0.25 + rnd.nextFloat() * 0.6) * len, along)
                let start = rnd.nextFloat() * (along - ln)
                let thin = rnd.nextBoolean()
                out.append(taper(horizontal ? l + start : l + pos, horizontal ? t + pos : t + start,
                                 horizontal ? l + start + ln : l + pos, horizontal ? t + pos : t + start + ln, startThin: thin))
            }
        case .FOCUS:
            let n = count(90, 16, 320)
            for i in 0..<n {
                let a = 2 * PI * Double(Float(i) + rnd.nextFloat() * 0.6) / Double(n)
                let (ca, sa) = dir(a)
                let outer = edge(ca, sa)
                let (ix, iy) = innerAt(ca, sa, inner(0.36 + rnd.nextFloat() * 0.2), outer)
                out.append(taper(cx + ca * outer, cy + sa * outer, ix, iy, startThin: false))
            }
        case .BURST:
            let n = count(40, 8, 160)
            for i in 0..<n {
                let a = 2 * PI * Double(Float(i) + rnd.nextFloat() * 0.5) / Double(n)
                let (ca, sa) = dir(a)
                let r0 = 0.12 + rnd.nextFloat() * 0.12
                let r1 = r0 + (0.6 + rnd.nextFloat() * 0.4 - r0) * len
                out.append(taper(cx + ca * w / 2 * r0, cy + sa * h / 2 * r0, cx + ca * w / 2 * r1, cy + sa * h / 2 * r1, startThin: false))
            }
        case .SPEED_DIAG:
            let ang = -28.0 * PI / 180
            let (ux, uy) = dir(ang)
            let px = -uy, py = ux
            let span = kHypot(w, h)
            let n = count(30, 5, 130)
            for i in 0..<n {
                let off = ((Float(i) + 0.5 + (rnd.nextFloat() - 0.5) * 0.8) / Float(n) - 0.5) * span
                let ln = min(span * (0.2 + rnd.nextFloat() * 0.45) * len, span)
                let along = (rnd.nextFloat() - 0.5) * (span - ln)
                let sx = cx + px * off + ux * (along - ln / 2), sy = cy + py * off + uy * (along - ln / 2)
                if let c = clip(sx, sy, sx + ux * ln, sy + uy * ln, l, t, r, b) {
                    out.append(taper(c[0], c[1], c[2], c[3], startThin: rnd.nextBoolean()))
                }
            }
        case .MOTION_CURVE:
            let n = count(14, 3, 60)
            for i in 0..<n {
                let off = ((Float(i) + 0.5) / Float(n) - 0.5) * h * 0.55 + (rnd.nextFloat() - 0.5) * h * 0.03
                let f0 = rnd.nextFloat() * 0.35
                let f1 = min(f0 + 0.35 + rnd.nextFloat() * 0.3, 1)
                let m = 12
                var line = ComicLine(xs: [], ys: [], pressures: [])
                for k in 0..<m {
                    let f = f0 + (f1 - f0) * Float(k) / (Float(m) - 1)
                    let bx = l + w * (0.05 + 0.9 * f)
                    let by = b - h * 0.18 - (h * 0.5) * (f * f) + off * (1 - f * 0.4)
                    line.xs.append(bx); line.ys.append(clampF(by, t, b))
                    line.pressures.append(0.15 + 0.85 * (Float(k) / (Float(m) - 1)))
                }
                out.append(line)
            }
        case .FOCUS_ROUGH:
            let n = count(48, 10, 180)
            for i in 0..<n {
                let a = 2 * PI * Double(Float(i) + rnd.nextFloat() * 0.7) / Double(n)
                let (ca, sa) = dir(a)
                let outer = edge(ca, sa)
                let (x1, y1) = innerAt(ca, sa, inner(0.42 + rnd.nextFloat() * 0.18), outer)
                let x0 = cx + ca * outer, y0 = cy + sa * outer
                let m = 10
                var line = ComicLine(xs: [], ys: [], pressures: [])
                let jit = kHypot(x1 - x0, y1 - y0) * 0.025
                for k in 0..<m {
                    let f = Float(k) / (Float(m) - 1)
                    let j: Float = (k == 0 || k == m - 1) ? 0 : (rnd.nextFloat() - 0.5) * 2 * jit
                    line.xs.append(x0 + (x1 - x0) * f - sa * j); line.ys.append(y0 + (y1 - y0) * f + ca * j)
                    line.pressures.append(0.25 + 0.75 * (1 - f))
                }
                out.append(line)
            }
        case .FLASH:
            let n = count(160, 32, 480)
            for i in 0..<n {
                let a = 2 * PI * Double(Float(i) + rnd.nextFloat() * 0.8) / Double(n)
                let (ca, sa) = dir(a)
                let r0 = 0.3 + rnd.nextFloat() * 0.05
                let r1 = r0 + (0.55 + rnd.nextFloat() * 0.45 - r0) * len
                out.append(taper(cx + ca * w / 2 * r0, cy + sa * h / 2 * r0, cx + ca * w / 2 * r1, cy + sa * h / 2 * r1, startThin: true, end: 0.05))
            }
        case .RADIATE:
            let clusters = count(14, 4, 40)
            for c in 0..<clusters {
                let base = 2 * PI * Double(Float(c) + rnd.nextFloat() * 0.3) / Double(clusters)
                for k in 0..<3 {
                    let a = base + Double(k - 1) * 0.045
                    let (ca, sa) = dir(a)
                    let r0 = 0.22 + rnd.nextFloat() * 0.08
                    let outer = edge(ca, sa)
                    let (ix, iy) = innerAt(ca, sa, r0, outer)
                    let lk = min(len, 1)
                    let ox = ix + (cx + ca * outer - ix) * lk, oy = iy + (cy + sa * outer - iy) * lk
                    out.append(taper(ix, iy, ox, oy, startThin: true))
                }
            }
        case .DRIP:
            let n = count(40, 8, 160)
            for i in 0..<n {
                let x = l + (Float(i) + 0.5 + (rnd.nextFloat() - 0.5) * 0.7) / Float(n) * w
                let ln = min(h * (0.18 + rnd.nextFloat() * 0.6) * len, h)
                let m = 8
                var line = ComicLine(xs: [], ys: [], pressures: [])
                let wob = w * 0.004
                for k in 0..<m {
                    let f = Float(k) / (Float(m) - 1)
                    line.xs.append(x + kSin(f * 6 + Float(i)) * wob); line.ys.append(t + ln * f)
                    line.pressures.append(0.15 + 0.85 * (1 - f))
                }
                out.append(line)
            }
        case .WAVER:
            let rings = count(3, 1, 8)
            for center in [PI, 0.0] {
                for k in 0..<rings {
                    let rr: Float = rings <= 1 ? 0.72 : 0.62 + 0.34 * Float(k) / (Float(rings) - 1)
                    let m = 10
                    var line = ComicLine(xs: [], ys: [], pressures: [])
                    for j in 0..<m {
                        let a = center + (Double(j) / (Double(m) - 1) - 0.5) * 0.9
                        let (ca, sa) = dir(a)
                        line.xs.append(cx + ca * w / 2 * rr); line.ys.append(cy + sa * h / 2 * rr)
                        let f = Float(j) / (Float(m) - 1)
                        line.pressures.append(0.2 + 0.8 * (1 - abs(f - 0.5) * 2))
                    }
                    out.append(line)
                }
            }
        case .BLUSH:
            let n = count(6, 2, 18)
            for side: Float in [0.3, 0.7] {
                let gx = l + w * side, gy = cy
                let step = w * 0.22 / Float(n)
                for i in 0..<n {
                    let x = gx - w * 0.11 + step * (Float(i) + 0.5)
                    out.append(taper(x - w * 0.03, gy + h * 0.06, x + w * 0.03, gy - h * 0.06, startThin: true, end: 0.3))
                }
            }
        case .SWIRL:
            let arms = count(3, 1, 8)
            for k in 0..<arms {
                let m = 48
                var line = ComicLine(xs: [], ys: [], pressures: [])
                let phase = 2 * PI * Double(k) / Double(arms)
                for j in 0..<m {
                    let f = Float(j) / (Float(m) - 1)
                    let a = phase + Double(f) * 2.6 * PI
                    let rr = 0.08 + 0.85 * f
                    line.xs.append(cx + Float(cos(a)) * w / 2 * rr); line.ys.append(cy + Float(sin(a)) * h / 2 * rr)
                    line.pressures.append(0.2 + 0.8 * f)
                }
                out.append(line)
            }
        case .RINGS:
            let rings = count(4, 2, 10)
            for k in 0..<rings {
                let rr = 0.3 + 0.65 * Float(k + 1) / Float(rings)
                let segs = 5 + k
                for sgi in 0..<segs {
                    let a0 = 2 * PI * Double(Float(sgi) + rnd.nextFloat() * 0.2) / Double(segs)
                    let a1 = a0 + 2 * PI / Double(segs) * (0.55 + Double(rnd.nextFloat()) * 0.25)
                    let m = 10
                    var line = ComicLine(xs: [], ys: [], pressures: [])
                    for j in 0..<m {
                        let a = a0 + (a1 - a0) * Double(j) / (Double(m) - 1)
                        line.xs.append(cx + Float(cos(a)) * w / 2 * rr); line.ys.append(cy + Float(sin(a)) * h / 2 * rr)
                        let f = Float(j) / (Float(m) - 1)
                        line.pressures.append(0.2 + 0.8 * (1 - abs(f - 0.5) * 2))
                    }
                    out.append(line)
                }
            }
        case .ZOOM:
            let n = count(110, 20, 360)
            for i in 0..<n {
                let a = 2 * PI * Double(Float(i) + rnd.nextFloat() * 0.7) / Double(n)
                let (ca, sa) = dir(a)
                let outer = edgeFrom(cx, cy, ca, sa, l, t, r, b)
                let f = 0.12 + rnd.nextFloat() * 0.22
                let reach = outer * (1 - (1 - f) * min(len, 1) * 0.98)
                out.append(taper(cx + ca * outer, cy + sa * outer, cx + ca * reach, cy + sa * reach, startThin: false, end: 0.04))
            }
        case .PERSPECTIVE:
            let rays = count(24, 8, 72)
            for i in 0..<rays {
                let a = 2 * PI * (Double(i) + 0.5) / Double(rays)
                let (ca, sa) = dir(a)
                let outer = edgeFrom(cx, cy, ca, sa, l, t, r, b)
                let start = outer * (1 - 0.92 * min(len, 1))
                out.append(taper(cx + ca * start, cy + sa * start, cx + ca * outer, cy + sa * outer, startThin: true, end: 0.08))
            }
            let frames = count(5, 2, 12)
            if frames >= 1 {
                for k in 1...frames {
                    var f = Float(k) / (Float(frames) + 1)
                    f = f * f
                    let fl = cx + (l - cx) * f, ft = cy + (t - cy) * f, fr = cx + (r - cx) * f, fb = cy + (b - cy) * f
                    let p = 0.25 + 0.75 * f
                    out.append(ComicLine(xs: [fl, fr, fr, fl, fl], ys: [ft, ft, fb, fb, ft], pressures: [Float](repeating: p, count: 5)))
                }
            }
        case .BEAMS:
            let n = count(9, 3, 30)
            let spread = 70.0 * PI / 180
            let dirA = Double(angle) * PI / 180
            for i in 0..<n {
                let a = dirA + spread * ((Double(i) + 0.5 + Double(rnd.nextFloat() - 0.5) * 0.4) / Double(n) - 0.5)
                let (ca, sa) = dir(a)
                let outer = edgeFrom(cx, cy, ca, sa, l, t, r, b) * min(len, 1)
                if outer < 2 { continue }
                out.append(taper(cx, cy, cx + ca * outer, cy + sa * outer, startThin: true, end: 0.03))
            }
        case .MANGA_DENSE:
            let n = count(240, 40, 720)
            for i in 0..<n {
                let a = 2 * PI * Double(Float(i) + rnd.nextFloat()) / Double(n)
                let (ca, sa) = dir(a)
                let outer = edge(ca, sa)
                let (ix, iy) = innerAt(ca, sa, inner(0.46 + rnd.nextFloat() * 0.22), outer)
                out.append(taper(cx + ca * outer, cy + sa * outer, ix, iy, startThin: false, end: 0.02))
            }
        case .ANIME_SOFT:
            let n = count(56, 12, 180)
            for i in 0..<n {
                let a = 2 * PI * Double(Float(i) + rnd.nextFloat() * 0.8) / Double(n)
                let (ca, sa) = dir(a)
                let outer = edge(ca, sa)
                let (ix, iy) = innerAt(ca, sa, inner(0.55 + rnd.nextFloat() * 0.2), outer)
                out.append(taper(cx + ca * outer, cy + sa * outer, ix, iy, startThin: false, end: 0.1))
            }
        }
        return out
    }

    private static func parallel(_ out: inout [ComicLine], _ rnd: inout KotlinRandom, _ rad: Double, _ l: Float, _ t: Float, _ r: Float, _ b: Float,
                                 _ n: Int, _ len: Float) {
        let ux = Float(cos(rad)), uy = Float(sin(rad))
        let px = -uy, py = ux
        let cx = (l + r) / 2, cy = (t + b) / 2
        let span = kHypot(r - l, b - t)
        for i in 0..<n {
            let off = ((Float(i) + 0.5 + (rnd.nextFloat() - 0.5) * 0.8) / Float(n) - 0.5) * span
            let ln = min(span * (0.2 + rnd.nextFloat() * 0.45) * len, span)
            let along = (rnd.nextFloat() - 0.5) * (span - ln)
            let sx = cx + px * off + ux * (along - ln / 2), sy = cy + py * off + uy * (along - ln / 2)
            if let c = clip(sx, sy, sx + ux * ln, sy + uy * ln, l, t, r, b) {
                out.append(taper(c[0], c[1], c[2], c[3], startThin: rnd.nextBoolean()))
            }
        }
    }

    private static func edgeFrom(_ cx: Float, _ cy: Float, _ ca: Float, _ sa: Float, _ l: Float, _ t: Float, _ r: Float, _ b: Float) -> Float {
        let tx: Float = ca > 1e-6 ? (r - cx) / ca : (ca < -1e-6 ? (l - cx) / ca : .infinity)
        let ty: Float = sa > 1e-6 ? (b - cy) / sa : (sa < -1e-6 ? (t - cy) / sa : .infinity)
        return max(min(tx, ty) * 0.999, 0)
    }

    private static func clip(_ x0: Float, _ y0: Float, _ x1: Float, _ y1: Float, _ l: Float, _ t: Float, _ r: Float, _ b: Float) -> [Float]? {
        var u0: Float = 0, u1: Float = 1
        let dx = x1 - x0, dy = y1 - y0
        let p = [-dx, dx, -dy, dy]
        let q = [x0 - l, r - x0, y0 - t, b - y0]
        for i in 0..<4 {
            if p[i] == 0 { if q[i] < 0 { return nil }; continue }
            let u = q[i] / p[i]
            if p[i] < 0 { if u > u1 { return nil }; if u > u0 { u0 = u } } else { if u < u0 { return nil }; if u < u1 { u1 = u } }
        }
        if u1 - u0 < 0.05 { return nil }
        return [x0 + dx * u0, y0 + dy * u0, x0 + dx * u1, y0 + dy * u1]
    }

    /// İnce uçlu çizgi (basınç bir uçta 1, diğer uçta [end]).
    private static func taper(_ x0: Float, _ y0: Float, _ x1: Float, _ y1: Float, startThin: Bool, end: Float = 0.15) -> ComicLine {
        let n = 8
        var line = ComicLine(xs: [], ys: [], pressures: [])
        for i in 0..<n {
            let f = Float(i) / (Float(n) - 1)
            line.xs.append(x0 + (x1 - x0) * f); line.ys.append(y0 + (y1 - y0) * f)
            let k = startThin ? f : 1 - f
            line.pressures.append(end + (1 - end) * k)
        }
        return line
    }

    /// Çizgilerin kutusu (sol, üst, sağ, alt); boşsa nil.
    static func bounds(_ lines: [ComicLine]) -> [Float]? {
        if lines.isEmpty { return nil }
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude, r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for ln in lines { for i in ln.xs.indices { l = min(l, ln.xs[i]); t = min(t, ln.ys[i]); r = max(r, ln.xs[i]); b = max(b, ln.ys[i]) } }
        return [l, t, r, b]
    }

    private static func edgeDistance(_ ca: Float, _ sa: Float, _ w: Float, _ h: Float) -> Float {
        let tx: Float = abs(ca) < 1e-6 ? .infinity : (w / 2) / abs(ca)
        let ty: Float = abs(sa) < 1e-6 ? .infinity : (h / 2) / abs(sa)
        return min(tx, ty) * 0.999
    }

    /// Grup taşındı/ölçeklendiyse üretim alanının yeni yeri.
    static func movedArea(_ area: [Float], _ bounds: [Float], _ now: [Float]) -> [Float] {
        let bw = max(bounds[2] - bounds[0], 1e-3), bh = max(bounds[3] - bounds[1], 1e-3)
        let sx = (now[2] - now[0]) / bw, sy = (now[3] - now[1]) / bh
        func mx(_ x: Float) -> Float { now[0] + (x - bounds[0]) * sx }
        func my(_ y: Float) -> Float { now[1] + (y - bounds[1]) * sy }
        return [mx(area[0]), my(area[1]), mx(area[2]), my(area[3])]
    }
}

/// Efekt yazısı (ses efekti) hazırı.
struct SfxPreset {
    let text: String
    let font: String
    let colors: [String]
    let outline: String
    let shadow: String
    let tilt: Float
}

enum ComicSfx {
    static let presets: [SfxPreset] = [
        SfxPreset(text: "BAM!", font: "BANGERS", colors: ["#FFEB3B", "#FF5722"], outline: "#1A1A1A", shadow: "#000000", tilt: -8),
        SfxPreset(text: "POW!", font: "LUCKIEST_GUY", colors: ["#FFFFFF", "#29B6F6"], outline: "#0D47A1", shadow: "#000000", tilt: 6),
        SfxPreset(text: "BOOM!", font: "BUNGEE", colors: ["#FFD54F", "#E53935"], outline: "#3E2723", shadow: "#000000", tilt: -5),
        SfxPreset(text: "ZAP!", font: "BANGERS", colors: ["#B2FF59", "#00C853"], outline: "#1B5E20", shadow: "#000000", tilt: 10),
        SfxPreset(text: "ŞAK!", font: "LUCKIEST_GUY", colors: ["#FFF176", "#FB8C00"], outline: "#4E342E", shadow: "#000000", tilt: -10),
        SfxPreset(text: "VUUUŞ", font: "BANGERS", colors: ["#E1F5FE", "#4FC3F7"], outline: "#01579B", shadow: "#00000000", tilt: 0),
        SfxPreset(text: "KRAK!", font: "BUNGEE_SHADE", colors: ["#FFFFFF", "#BDBDBD"], outline: "#212121", shadow: "#000000", tilt: 4),
        SfxPreset(text: "HAHAHA", font: "RUBIK_BUBBLES", colors: ["#F8BBD0", "#EC407A"], outline: "#880E4F", shadow: "#000000", tilt: -4),
        SfxPreset(text: "AAAA!", font: "BANGERS", colors: ["#FFFFFF", "#FF1744"], outline: "#B71C1C", shadow: "#000000", tilt: 8),
        SfxPreset(text: "ZZZ…", font: "GRANDSTANDER", colors: ["#E8EAF6", "#7986CB"], outline: "#1A237E", shadow: "#00000000", tilt: -6),
        SfxPreset(text: "GÜM!", font: "BUNGEE", colors: ["#FFCC80", "#EF6C00"], outline: "#3E2723", shadow: "#000000", tilt: 7),
        SfxPreset(text: "TIK TAK", font: "BOOGALOO", colors: ["#FFFFFF", "#90A4AE"], outline: "#263238", shadow: "#00000000", tilt: 0),
        SfxPreset(text: "CRASH!", font: "BUNGEE", colors: ["#FFF59D", "#FF7043"], outline: "#3E2723", shadow: "#000000", tilt: -7),
        SfxPreset(text: "WHOOSH!", font: "BANGERS", colors: ["#E0F7FA", "#26C6DA"], outline: "#006064", shadow: "#00000000", tilt: 5),
    ]

    static let featured = ["POW!", "BAM!", "BOOM!", "CRASH!", "WHOOSH!", "ZAP!"]
}
