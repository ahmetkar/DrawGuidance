import Foundation

/// Desenin kullanıldığı yer: not tuvali, resim tuvali ya da yazı kutusu (Android `content.paper`).
enum PaperUsage: Int, CaseIterable { case NOTE, ART, BOX }

/// Desen çizim komutu (Android `PatOp`). [slot] renk yuvası: 0 desen rengi, 1 açık, 2 koyu, 3 kağıt, 4 vurgu.
struct PatOp {
    let type: Int
    let pts: [Float]
    var width: Float = 1.5
    var slot: Int = 0
    var alpha: Float = 1
    var fill = false
    var dash: Float = 0
    var closed = false
    var centered = false

    static let LINE = 0
    static let RECT = 1
    static let CIRCLE = 2
    static let POLY = 3

    static let SLOT_MAIN = 0
    static let SLOT_LIGHT = 1
    static let SLOT_DARK = 2
    static let SLOT_PAPER = 3
    static let SLOT_ACCENT = 4
}

/// v23 arka plan deseni: döşenen karo ve sayfaya bir kez çizilen ögeler.
struct PaperPattern {
    let id: String
    let name: String
    let usage: PaperUsage
    let tileW: Float
    let tileH: Float
    let tile: [PatOp]
    var page: [PatOp] = []
    var inset: [Float] = [0, 0, 0, 0]
    var color: UInt32 = 0xFF7C_89A6
    var accent: UInt32 = 0xFFE5_7373
    var tint: UInt32 = 0
    var tintAlpha: Float = 0

    var hasTile: Bool { !tile.isEmpty && tileW > 0 && tileH > 0 }
}

/// Karo ve sayfa komutlarının kısa yazımı.
final class PatternBuilder {
    var ops: [PatOp] = []

    func line(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float, _ w: Float = 1.5, _ slot: Int = 0, _ alpha: Float = 1,
              dash: Float = 0, centered: Bool = false) {
        ops.append(PatOp(type: PatOp.LINE, pts: [x1, y1, x2, y2], width: w, slot: slot, alpha: alpha, dash: dash, centered: centered))
    }

    func rect(_ x: Float, _ y: Float, _ w: Float, _ h: Float, fill: Bool = true, width: Float = 1.5, slot: Int = 0, alpha: Float = 1,
              dash: Float = 0) {
        ops.append(PatOp(type: PatOp.RECT, pts: [x, y, w, h], width: width, slot: slot, alpha: alpha, fill: fill, dash: dash))
    }

    func circle(_ cx: Float, _ cy: Float, _ r: Float, fill: Bool = true, width: Float = 1.5, slot: Int = 0, alpha: Float = 1,
                centered: Bool = false, dash: Float = 0) {
        ops.append(PatOp(type: PatOp.CIRCLE, pts: [cx, cy, r], width: width, slot: slot, alpha: alpha, fill: fill, dash: dash, centered: centered))
    }

    func poly(_ xy: Float..., fill: Bool = true, closed: Bool = true, width: Float = 1.5, slot: Int = 0, alpha: Float = 1, dash: Float = 0) {
        ops.append(PatOp(type: PatOp.POLY, pts: xy, width: width, slot: slot, alpha: alpha, fill: fill, dash: dash, closed: closed || fill))
    }

    func polyline(_ pts: [(Float, Float)], fill: Bool = false, closed: Bool = false, width: Float = 1.5, slot: Int = 0, alpha: Float = 1) {
        var a: [Float] = []
        a.reserveCapacity(pts.count * 2)
        for p in pts { a.append(p.0); a.append(p.1) }
        ops.append(PatOp(type: PatOp.POLY, pts: a, width: width, slot: slot, alpha: alpha, fill: fill, dash: 0, closed: closed || fill))
    }

    /// Merkezi ([cx], [cy]), yarıçapı [r] olan yay (derece; 0 sağ, 90 aşağı), [n] parçalı.
    func arc(_ cx: Float, _ cy: Float, _ r: Float, _ from: Float, _ to: Float, w: Float = 1.5, slot: Int = 0, alpha: Float = 1,
             fill: Bool = false, n: Int = 24) {
        let pts = (0...n).map { k -> (Float, Float) in
            let a = Double(from + (to - from) * Float(k) / Float(n)) * Double.pi / 180
            return (Float(Double(cx) + Double(r) * cos(a)), Float(Double(cy) + Double(r) * sin(a)))
        }
        polyline(pts, fill: fill, closed: fill, width: w, slot: slot, alpha: alpha)
    }

    /// [points] uçlu yıldız.
    func star(_ cx: Float, _ cy: Float, _ r: Float, points: Int = 5, inner: Float = 0.45, slot: Int = 0, alpha: Float = 1, rot: Float = -90) {
        let n = points * 2
        let pts = (0..<n).map { k -> (Float, Float) in
            let rr = k % 2 == 0 ? r : r * inner
            let a = (Double(rot) + 360.0 * Double(k) / Double(n)) * Double.pi / 180
            return (Float(Double(cx) + Double(rr) * cos(a)), Float(Double(cy) + Double(rr) * sin(a)))
        }
        polyline(pts, fill: true, closed: true, slot: slot, alpha: alpha)
    }

    /// Kalp: merkez ([cx], [cy]), genişlik [s].
    func heart(_ cx: Float, _ cy: Float, _ s: Float, slot: Int = 0, alpha: Float = 1) {
        let pts = (0..<40).map { k -> (Float, Float) in
            let t = 2 * Double.pi * Double(k) / 40
            let st = sin(t)
            let x = 16 * st * st * st
            let y = -(13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t))
            return (cx + Float(x / 34.0 * Double(s)), cy + Float(y / 34.0 * Double(s)))
        }
        polyline(pts, fill: true, closed: true, slot: slot, alpha: alpha)
    }
}

/// Desenin çizileceği yüzey; renkler önçarpımsız ARGB.
protocol PatternSurface {
    func save()
    func restore()
    func translate(_ dx: Float, _ dy: Float)
    func scale(_ s: Float)
    func clipRect(_ l: Float, _ t: Float, _ r: Float, _ b: Float)
    func line(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float, _ width: Float, _ color: UInt32, _ dash: Float)
    func rect(_ x: Float, _ y: Float, _ w: Float, _ h: Float, _ fill: Bool, _ width: Float, _ color: UInt32, _ dash: Float)
    func circle(_ cx: Float, _ cy: Float, _ r: Float, _ fill: Bool, _ width: Float, _ color: UInt32, _ dash: Float)
    func poly(_ xy: [Float], _ closed: Bool, _ fill: Bool, _ width: Float, _ color: UInt32, _ dash: Float)
}

/// Desen renkleri: ana renk ve ondan türeyen tonlar, kağıt rengi ve vurgu.
struct PatternColors: Hashable {
    let main: UInt32
    let paper: UInt32
    let light: UInt32
    let dark: UInt32
    let accent: UInt32
    let opacity: Float

    init(_ main: UInt32, _ paper: UInt32, _ accent: UInt32, _ opacity: Float = 1) {
        self.main = main | 0xFF00_0000
        self.paper = paper
        self.light = PatternColors.mix(self.main, 0xFFFF_FFFF, 0.55)
        self.dark = PatternColors.mix(self.main, 0xFF00_0000, 0.3)
        self.accent = accent | 0xFF00_0000
        self.opacity = opacity
    }

    func slot(_ s: Int) -> UInt32 {
        switch s {
        case PatOp.SLOT_LIGHT: return light
        case PatOp.SLOT_DARK: return dark
        case PatOp.SLOT_PAPER: return paper | 0xFF00_0000
        case PatOp.SLOT_ACCENT: return accent
        default: return main
        }
    }

    /// Kotlin `roundToInt` (yarım yukarı).
    private static func round(_ v: Float) -> Int { Int((Double(v) + 0.5).rounded(.down)) }

    static func mix(_ a: UInt32, _ b: UInt32, _ t: Float) -> UInt32 {
        func ch(_ sh: UInt32) -> UInt32 {
            let x = Int(a >> sh & 0xFF), y = Int(b >> sh & 0xFF)
            return UInt32(min(max(round(Float(x) + Float(y - x) * t), 0), 255))
        }
        return 0xFF00_0000 | ch(16) << 16 | ch(8) << 8 | ch(0)
    }

    static func withAlpha(_ c: UInt32, _ a: Float) -> UInt32 {
        UInt32(round(min(max(a, 0), 1) * 255)) << 24 | (c & 0x00FF_FFFF)
    }
}

/// v23 desen çizimi (yüzeyden bağımsız): sayfa rengi tonu → karolar → sayfa ögeleri.
enum PatternPainter {
    static func paint(_ p: PaperPattern, _ s: PatternSurface, _ x: Float, _ y: Float, _ w: Float, _ h: Float, _ colors: PatternColors,
                      scale: Float = 1, visible: [Float]? = nil, tiles: Bool = true, tint: Bool = true) {
        if w <= 0 || h <= 0 { return }
        let op = colors.opacity
        if tint && p.tint != 0 && p.tintAlpha > 0 { s.rect(x, y, w, h, true, 0, PatternColors.withAlpha(p.tint, p.tintAlpha * op), 0) }
        if tiles && p.hasTile {
            let rl = x + p.inset[0] * w, rt = y + p.inset[1] * h
            let rr = x + w - p.inset[2] * w, rb = y + h - p.inset[3] * h
            let tw = p.tileW * scale, th = p.tileH * scale
            if rr > rl && rb > rt && tw > 0.5 && th > 0.5 {
                let vl = max(rl, visible?[0] ?? rl), vt = max(rt, visible?[1] ?? rt)
                let vr = min(rr, visible?[2] ?? rr), vb = min(rb, visible?[3] ?? rb)
                if vr > vl && vb > vt {
                    s.save()
                    s.clipRect(rl, rt, rr, rb)
                    let i0 = Int(((vl - rl) / tw).rounded(.down)) - 1, i1 = Int(((vr - rl) / tw).rounded(.up))
                    let j0 = Int(((vt - rt) / th).rounded(.down)) - 1, j1 = Int(((vb - rt) / th).rounded(.up))
                    if j0 <= j1 && i0 <= i1 {
                        for j in j0...j1 {
                            for i in i0...i1 {
                                s.save()
                                s.translate(rl + Float(i) * tw, rt + Float(j) * th)
                                s.scale(scale)
                                drawOps(p.tile, s, colors)
                                s.restore()
                            }
                        }
                    }
                    s.restore()
                }
            }
        }
        if !p.page.isEmpty {
            s.save()
            s.clipRect(x, y, x + w, y + h)
            drawPage(p.page, s, colors, x, y, w, h, scale)
            s.restore()
        }
    }

    /// Tek karoyu (komşu karolardan taşan parçalarla, 3×3 kopya) çizer.
    static func paintTile(_ p: PaperPattern, _ s: PatternSurface, _ colors: PatternColors, _ scale: Float) {
        if !p.hasTile { return }
        let tw = p.tileW * scale, th = p.tileH * scale
        s.save()
        s.clipRect(0, 0, tw, th)
        for j in -1...1 {
            for i in -1...1 {
                s.save()
                s.translate(Float(i) * tw, Float(j) * th)
                s.scale(scale)
                drawOps(p.tile, s, colors)
                s.restore()
            }
        }
        s.restore()
    }

    /// Karoların döşendiği alan (sayfanın iç payı): sol, üst, sağ, alt.
    static func tileArea(_ p: PaperPattern, _ x: Float, _ y: Float, _ w: Float, _ h: Float) -> [Float] {
        [x + p.inset[0] * w, y + p.inset[1] * h, x + w - p.inset[2] * w, y + h - p.inset[3] * h]
    }

    private static func drawOps(_ ops: [PatOp], _ s: PatternSurface, _ colors: PatternColors) {
        for o in ops {
            let c = PatternColors.withAlpha(colors.slot(o.slot), o.alpha * colors.opacity)
            let pt = o.pts
            switch o.type {
            case PatOp.LINE: s.line(pt[0], pt[1], pt[2], pt[3], o.width, c, o.dash)
            case PatOp.RECT: s.rect(pt[0], pt[1], pt[2], pt[3], o.fill, o.width, c, o.dash)
            case PatOp.CIRCLE: s.circle(pt[0], pt[1], pt[2], o.fill, o.width, c, o.dash)
            default: s.poly(pt, o.closed, o.fill, o.width, c, o.dash)
            }
        }
    }

    private static func drawPage(_ ops: [PatOp], _ s: PatternSurface, _ colors: PatternColors, _ x: Float, _ y: Float, _ w: Float, _ h: Float,
                                 _ scale: Float) {
        let m = min(w, h)
        func px(_ o: PatOp, _ v: Float) -> Float { o.centered ? x + w / 2 + (v - 0.5) * m : x + v * w }
        func py(_ o: PatOp, _ v: Float) -> Float { o.centered ? y + h / 2 + (v - 0.5) * m : y + v * h }
        for o in ops {
            let c = PatternColors.withAlpha(colors.slot(o.slot), o.alpha * colors.opacity)
            let pt = o.pts
            let lw = o.width * scale, dash = o.dash * scale
            switch o.type {
            case PatOp.LINE: s.line(px(o, pt[0]), py(o, pt[1]), px(o, pt[2]), py(o, pt[3]), lw, c, dash)
            case PatOp.RECT:
                let ww = o.centered ? pt[2] * m : pt[2] * w, hh = o.centered ? pt[3] * m : pt[3] * h
                s.rect(px(o, pt[0]), py(o, pt[1]), ww, hh, o.fill, lw, c, dash)
            case PatOp.CIRCLE: s.circle(px(o, pt[0]), py(o, pt[1]), o.centered ? pt[2] * m : pt[2] * w, o.fill, lw, c, dash)
            default:
                var a = [Float](repeating: 0, count: pt.count)
                for i in 0..<(pt.count / 2) { a[2 * i] = px(o, pt[2 * i]); a[2 * i + 1] = py(o, pt[2 * i + 1]) }
                s.poly(a, o.closed, o.fill, lw, c, dash)
            }
        }
    }
}
