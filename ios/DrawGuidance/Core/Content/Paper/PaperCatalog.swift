import Foundation

/// v23 hazır arka plan desenleri (Android `PaperCatalog`): not tuvali için 30, resim tuvali için 30, yazı kutusu için 10.
/// Ölçüler tuval birimidir (A4 not sayfası 1240×1754).
enum PaperCatalog {
    static let VERSION = 1

    private static let SQ3 = Float(3).squareRoot()

    private static func p(_ id: String, _ name: String, _ usage: PaperUsage, _ tw: Float, _ th: Float, _ color: UInt32 = 0xFF7C_89A6,
                          accent: UInt32 = 0xFFE5_7373, inset: [Float] = [0, 0, 0, 0], tint: UInt32 = 0, tintAlpha: Float = 0,
                          page: ((PatternBuilder) -> Void)? = nil, _ tile: (PatternBuilder) -> Void) -> PaperPattern {
        let t = PatternBuilder(); tile(t)
        var pg: [PatOp] = []
        if let page { let b = PatternBuilder(); page(b); pg = b.ops }
        return PaperPattern(id: id, name: name, usage: usage, tileW: tw, tileH: th, tile: t.ops, page: pg, inset: inset, color: color,
                            accent: accent, tint: tint, tintAlpha: tintAlpha)
    }

    private static let BLUE: UInt32 = 0xFF7C_9CC9
    private static let GREY: UInt32 = 0xFF8C_95A3
    private static let GREEN: UInt32 = 0xFF6F_A287
    private static let ORANGE: UInt32 = 0xFFD9_935B
    private static let WIDE: Float = 2400
    private static let ACC = PatOp.SLOT_ACCENT
    private static let LIGHT = PatOp.SLOT_LIGHT
    private static let DARK = PatOp.SLOT_DARK
    private static let PAPER = PatOp.SLOT_PAPER

    private static func ruled(_ b: PatternBuilder, _ h: Float, _ alpha: Float = 0.55, _ w: Float = 1.6, dash: Float = 0) {
        b.line(0, h - 1, WIDE, h - 1, w, 0, alpha, dash: dash)
    }

    private static func grid(_ b: PatternBuilder, _ s: Float, _ alpha: Float, _ w: Float = 1) {
        b.line(0, 0, s, 0, w, 0, alpha); b.line(0, 0, 0, s, w, 0, alpha)
    }

    /// Sivri tepeli altıgen ızgaranın karosu: iki merkez, her merkezde yalnız kendi 3 kenarı.
    private static func hexEdges(_ b: PatternBuilder, _ s: Float, _ w: Float, _ alpha: Float, _ slot: Int = 0, _ fillSlot: Int = -1,
                                 _ fillAlpha: Float = 0) {
        let hw = SQ3 * s
        let centers: [(Float, Float)] = [(0, 0), (hw / 2, 1.5 * s)]
        func rad(_ d: Double) -> Double { d * Double.pi / 180 }
        if fillSlot >= 0 {
            for (cx, cy) in centers {
                let pts = (0..<6).map { k -> (Float, Float) in
                    let a = rad(30.0 + 60.0 * Double(k))
                    return (Float(Double(cx) + Double(s) * cos(a)), Float(Double(cy) + Double(s) * sin(a)))
                }
                b.polyline(pts, fill: true, closed: true, slot: fillSlot, alpha: fillAlpha)
            }
        }
        for (cx, cy) in centers {
            for k in 0..<3 {
                let a1 = rad(30.0 + 60.0 * Double(k)), a2 = rad(30.0 + 60.0 * Double(k + 1))
                b.line(Float(Double(cx) + Double(s) * cos(a1)), Float(Double(cy) + Double(s) * sin(a1)),
                       Float(Double(cx) + Double(s) * cos(a2)), Float(Double(cy) + Double(s) * sin(a2)), w, slot, alpha)
            }
        }
    }

    private static func fsin(_ d: Double) -> Float { Float(sin(d)) }
    private static func fcos(_ d: Double) -> Float { Float(cos(d)) }

    // MARK: - not tuvali (30)

    static let note: [PaperPattern] = {
        let n = PaperUsage.NOTE
        let PI = Double.pi
        var l: [PaperPattern] = []
            l.append(p("note.ruled_wide", "Geniş çizgili", n, WIDE, 52, BLUE, inset: [0, 0.07, 0, 0.02],
              page: { $0.line(0.1, 0, 0.1, 1, 2, ACC, 0.7) }) { ruled($0, 52) })
            l.append(p("note.ruled_college", "Dar çizgili", n, WIDE, 42, BLUE, inset: [0, 0.07, 0, 0.02],
              page: { $0.line(0.1, 0, 0.1, 1, 2, ACC, 0.7) }) { ruled($0, 42) })
            l.append(p("note.ruled_plain", "Kenarsız çizgili", n, WIDE, 48, BLUE, inset: [0, 0.03, 0, 0]) { ruled($0, 48) })
            l.append(p("note.grid5", "Kareli (5 mm)", n, 30, 30, GREY) { grid($0, 30, 0.35) })
            l.append(p("note.grid10", "Kareli (1 cm)", n, 60, 60, GREY) { grid($0, 60, 0.45, 1.3) })
            l.append(p("note.millimeter", "Milimetrik", n, 60, 60, ORANGE) { b in
                for k in 1..<10 {
                    let v = Float(k) * 6; let a: Float = k == 5 ? 0.32 : 0.16; let w: Float = k == 5 ? 0.9 : 0.6
                    b.line(0, v, 60, v, w, 0, a); b.line(v, 0, v, 60, w, 0, a)
                }
                grid(b, 60, 0.5, 1.3)
            })
            l.append(p("note.dots", "Noktalı", n, 30, 30, GREY) { $0.circle(0, 0, 1.7, alpha: 0.6) })
            l.append(p("note.dots_dense", "Sık noktalı", n, 20, 20, GREY) { $0.circle(0, 0, 1.25, alpha: 0.55) })
            l.append(p("note.iso_dots", "İzometrik noktalı", n, 40, 40 * SQ3, GREY) { b in
                b.circle(0, 0, 1.7, alpha: 0.6); b.circle(20, 20 * SQ3, 1.7, alpha: 0.6)
            })
            l.append(p("note.iso_grid", "Üçgen ızgara", n, 40, 40 * SQ3, GREY) { b in
                let h = 20 * SQ3, a: Float = 0.3
                b.line(0, 0, 40, 0, 1, 0, a); b.line(0, h, 40, h, 1, 0, a)
                b.line(0, 0, 40, 2 * h, 1, 0, a); b.line(40, 0, 0, 2 * h, 1, 0, a)
                b.line(20, 0, 40, h, 1, 0, a); b.line(0, h, 20, 2 * h, 1, 0, a)
                b.line(20, 0, 0, h, 1, 0, a); b.line(40, h, 20, 2 * h, 1, 0, a)
            })
            l.append(p("note.hex", "Altıgen ızgara", n, SQ3 * 22, 66, GREY) { hexEdges($0, 22, 1, 0.35) })
            l.append(p("note.cornell", "Cornell not düzeni", n, WIDE, 44, BLUE, inset: [0, 0.09, 0, 0.2],
              page: { b in
                  b.line(0, 0.08, 1, 0.08, 2.4, 0, 0.75); b.line(0.3, 0.08, 0.3, 0.8, 2.4, ACC, 0.75)
                  b.line(0, 0.8, 1, 0.8, 2.4, 0, 0.75)
              }) { ruled($0, 44, 0.45) })
            l.append(p("note.music", "Müzik portesi", n, WIDE, 150, GREY, inset: [0, 0.04, 0, 0.02]) { b in
                for k in 0..<5 { b.line(0, 30 + Float(k) * 13, WIDE, 30 + Float(k) * 13, 1.3, 0, 0.7) }
            })
            l.append(p("note.handwriting", "Güzel yazı (dört çizgi)", n, WIDE, 120, BLUE, inset: [0, 0.04, 0, 0]) { b in
                b.line(0, 20, WIDE, 20, 1.2, 0, 0.45); b.line(0, 50, WIDE, 50, 1.2, 0, 0.45, dash: 10)
                b.line(0, 80, WIDE, 80, 1.9, ACC, 0.7); b.line(0, 110, WIDE, 110, 1.2, 0, 0.45)
            })
            l.append(p("note.slant", "Eğik yazı kılavuzu", n, 42, 60, GREY) { b in
                b.line(0, 0, 42, 0, 1, 0, 0.4); b.line(0, 60, 42, 0, 0.9, 0, 0.25)
            })
            l.append(p("note.todo", "Yapılacaklar listesi", n, WIDE, 56, BLUE, inset: [0, 0.06, 0, 0.02]) { b in
                ruled(b, 56, 0.4); b.rect(46, 18, 22, 22, fill: false, width: 1.8, alpha: 0.7)
            })
            l.append(p("note.weekly", "Haftalık plan", n, 0, 0, BLUE, page: { b in
                b.rect(0.06, 0.05, 0.88, 0.9, fill: false, width: 2.2, alpha: 0.7)
                for k in 1..<7 { let y = 0.05 + 0.9 * Float(k) / 7; b.line(0.06, y, 0.94, y, 1.6, 0, 0.55) }
                b.line(0.2, 0.05, 0.2, 0.95, 1.6, ACC, 0.6)
            }) { _ in })
            l.append(p("note.daily", "Günlük plan (saatli)", n, WIDE, 64, BLUE, inset: [0, 0.08, 0, 0.03],
              page: { b in b.line(0.16, 0.06, 0.16, 0.98, 2, ACC, 0.7); b.line(0, 0.06, 1, 0.06, 2.2, 0, 0.7) }) { b in
                ruled(b, 64, 0.5); b.line(0, 31, WIDE, 31, 0.9, 0, 0.25, dash: 8)
            })
            l.append(p("note.storyboard", "Hikaye tahtası", n, 0, 0, GREY, page: { b in
                for r in 0..<3 {
                    for c in 0..<2 {
                        let x = 0.06 + Float(c) * 0.46, y = 0.05 + Float(r) * 0.31
                        b.rect(x, y, 0.42, 0.2, fill: false, width: 2.2, alpha: 0.75)
                        b.line(x, y + 0.245, x + 0.42, y + 0.245, 1.3, 0, 0.45)
                        b.line(x, y + 0.28, x + 0.42, y + 0.28, 1.3, 0, 0.45)
                    }
                }
            }) { _ in })
            l.append(p("note.story", "Resimli hikaye kağıdı", n, WIDE, 70, BLUE, inset: [0, 0.52, 0, 0.02],
              page: { $0.rect(0.06, 0.05, 0.88, 0.43, fill: false, width: 2.2, alpha: 0.7) }) { b in
                ruled(b, 70, 0.55); b.line(0, 34, WIDE, 34, 1, 0, 0.35, dash: 9)
            })
            l.append(p("note.math", "Matematik defteri", n, 25, 25, GREY, page: { $0.line(0.09, 0, 0.09, 1, 2, ACC, 0.7) }) { grid($0, 25, 0.3, 0.9) })
            l.append(p("note.engineering", "Mühendislik kağıdı", n, 60, 60, GREEN) { b in
                for k in 1..<5 { let v = Float(k) * 12; b.line(0, v, 60, v, 0.7, 0, 0.25); b.line(v, 0, v, 60, 0.7, 0, 0.25) }
                grid(b, 60, 0.55, 1.4)
            })
            l.append(p("note.polar", "Kutupsal ızgara", n, 0, 0, GREY, page: { b in
                for k in 1...9 {
                    b.circle(0.5, 0.5, 0.05 * Float(k), fill: false, width: k % 3 == 0 ? 1.6 : 0.9, alpha: k % 3 == 0 ? 0.55 : 0.3, centered: true)
                }
                for k in 0..<24 {
                    let a = 2 * PI * Double(k) / 24
                    b.line(0.5, 0.5, Float(0.5 + 0.45 * cos(a)), Float(0.5 + 0.45 * sin(a)), k % 6 == 0 ? 1.4 : 0.8, 0,
                           k % 6 == 0 ? 0.55 : 0.28, centered: true)
                }
            }) { _ in })
            l.append(p("note.semilog", "Yarı logaritmik", n, 60, 300, ORANGE) { b in
                b.line(0, 0, 0, 300, 1, 0, 0.35)
                for k in 1...9 {
                    let y = 300 * (1 - log10f(Float(k)))
                    b.line(0, y, 60, y, k == 1 ? 1.4 : 0.8, 0, k == 1 ? 0.6 : 0.3)
                }
            })
            l.append(p("note.crossdots", "Artı noktalı", n, 30, 30, GREY) { b in
                b.line(11, 15, 19, 15, 1.1, 0, 0.55); b.line(15, 11, 15, 19, 1.1, 0, 0.55)
            })
            l.append(p("note.dashed", "Kesik çizgili", n, WIDE, 46, BLUE, inset: [0, 0.05, 0, 0.02]) { ruled($0, 46, 0.5, 1.4, dash: 10) })
            l.append(p("note.twocol", "İki sütunlu", n, WIDE, 44, BLUE, inset: [0, 0.06, 0, 0.02],
              page: { $0.line(0.5, 0.04, 0.5, 0.98, 2, ACC, 0.65) }) { ruled($0, 44, 0.5) })
            l.append(p("note.legal", "Sarı hukuk defteri", n, WIDE, 44, 0xFF6F_8FC6, inset: [0, 0.08, 0, 0.02], tint: 0xFFF7_E58C, tintAlpha: 0.55,
              page: { b in b.line(0.12, 0, 0.12, 1, 1.8, ACC, 0.75); b.line(0.128, 0, 0.128, 1, 1.8, ACC, 0.75) }) { ruled($0, 44, 0.6) })
            l.append(p("note.tianzige", "Hat ızgarası (田)", n, 90, 90, 0xFFC0_605A) { b in
                b.rect(0, 0, 90, 90, fill: false, width: 1.5, alpha: 0.6)
                b.line(45, 0, 45, 90, 1, 0, 0.4, dash: 6); b.line(0, 45, 90, 45, 1, 0, 0.4, dash: 6)
                b.line(0, 0, 90, 90, 0.8, 0, 0.25, dash: 5); b.line(90, 0, 0, 90, 0.8, 0, 0.25, dash: 5)
            })
            l.append(p("note.genko", "Kareli yazı (genkō)", n, 50, 66, 0xFF7F_A37F) { b in
                b.line(0, 0, 50, 0, 1.2, 0, 0.55); b.line(0, 50, 50, 50, 1.2, 0, 0.55); b.line(0, 0, 0, 50, 1.2, 0, 0.55)
            })
        return l
    }()

    // MARK: - resim tuvali (30)

    static let art: [PaperPattern] = {
        let a = PaperUsage.ART
        let PI = Double.pi
        let pink: UInt32 = 0xFFE8_829B, teal: UInt32 = 0xFF4F_A3A5, gold: UInt32 = 0xFFE0_B04E, navy: UInt32 = 0xFF3F_5E8C
        let coral: UInt32 = 0xFFEF_8354, leaf: UInt32 = 0xFF6B_A368, plum: UInt32 = 0xFF8E_6BBF, sky: UInt32 = 0xFF7D_B7E0
        let slots = [0, LIGHT, DARK, ACC]
        var l: [PaperPattern] = []
            l.append(p("art.polka", "Puantiye", a, 60, 60, pink) { b in b.circle(15, 15, 9); b.circle(45, 45, 9) })
            l.append(p("art.polka_small", "Küçük puantiye", a, 30, 30, navy) { $0.circle(15, 15, 4, alpha: 0.85) })
            l.append(p("art.stripes_diag", "Çapraz şerit", a, 40, 40, teal) { b in
                b.poly(0, 0, 14, 0, 40, 26, 40, 40); b.poly(0, 26, 14, 40, 0, 40)
            })
            l.append(p("art.stripes_h", "Yatay şerit", a, 100, 40, coral) { $0.rect(0, 0, 100, 16, alpha: 0.85) })
            l.append(p("art.gingham", "Pötikare", a, 40, 40, 0xFFD0_525F) { b in b.rect(0, 0, 20, 40, alpha: 0.35); b.rect(0, 0, 40, 20, alpha: 0.35) })
            l.append(p("art.tartan", "Ekose", a, 80, 80, 0xFF2F_6B47) { b in
                b.rect(0, 0, 80, 16, alpha: 0.45); b.rect(0, 0, 16, 80, alpha: 0.45)
                b.rect(0, 48, 80, 8, slot: ACC, alpha: 0.45); b.rect(48, 0, 8, 80, slot: ACC, alpha: 0.45)
                b.line(0, 34, 80, 34, 2, DARK, 0.6); b.line(34, 0, 34, 80, 2, DARK, 0.6)
            })
            l.append(p("art.chevron", "Zikzak", a, 60, 40, gold) { $0.poly(0, 0, 30, 20, 60, 0, 60, 14, 30, 34, 0, 14) })
            l.append(p("art.waves", "Dalga", a, 80, 40, sky) { b in
                var pts: [(Float, Float)] = []
                for k in 0...32 {
                    let x: Float = 80 * Float(k) / 32
                    let y: Float = 20 + 8 * Float(sin(2 * PI * Double(k) / 32))
                    pts.append((x, y))
                }
                b.polyline(pts, width: 3.5)
            })
            l.append(p("art.scales", "Balık pulu", a, 40, 20, teal) { b in
                b.arc(20, 0, 20, 0, 180, w: 2.2); b.arc(0, 10, 20, 0, 180, w: 2.2); b.arc(40, 10, 20, 0, 180, w: 2.2)
            })
            l.append(p("art.honeycomb", "Bal peteği", a, SQ3 * 26, 78, gold) { hexEdges($0, 26, 2.6, 0.9, DARK, LIGHT, 0.55) })
            l.append(p("art.argyle", "Baklava", a, 60, 80, plum) { b in
                b.poly(30, 0, 60, 40, 30, 80, 0, 40, slot: LIGHT, alpha: 0.8)
                b.line(0, 0, 60, 80, 1.4, DARK, 0.7, dash: 6); b.line(60, 0, 0, 80, 1.4, DARK, 0.7, dash: 6)
            })
            l.append(p("art.stars", "Yıldızlar", a, 70, 70, gold) { b in b.star(20, 20, 11); b.star(54, 52, 6.5, slot: LIGHT) })
            l.append(p("art.hearts", "Kalpler", a, 70, 70, pink) { b in b.heart(20, 22, 20); b.heart(54, 54, 12, slot: LIGHT) })
            l.append(p("art.flowers", "Çiçekler", a, 80, 80, coral) { b in
                for k in 0..<5 { let an = 2 * PI * Double(k) / 5 - PI / 2; b.circle(Float(24 + 9 * cos(an)), Float(24 + 9 * sin(an)), 7, slot: LIGHT) }
                b.circle(24, 24, 5, slot: DARK)
                for k in 0..<5 { let an = 2 * PI * Double(k) / 5 - PI / 2; b.circle(Float(62 + 6 * cos(an)), Float(62 + 6 * sin(an)), 4.5) }
                b.circle(62, 62, 3, slot: ACC)
            })
            l.append(p("art.leaves", "Yapraklar", a, 80, 80, leaf) { b in
                func leafAt(_ cx: Float, _ cy: Float, _ len: Float, _ ang: Double, _ slot: Int) {
                    func pt(_ k: Int, _ sign: Double) -> (Float, Float) {
                        let t = Double(k) / 16.0, s = sign * sin(PI * t) * 0.32
                        let x = (t - 0.5) * Double(len), y = s * Double(len)
                        return (cx + Float(x * cos(ang) - y * sin(ang)), cy + Float(x * sin(ang) + y * cos(ang)))
                    }
                    let pts = (0...16).map { pt($0, 1) } + stride(from: 16, through: 0, by: -1).map { pt($0, -1) }
                    b.polyline(pts, fill: true, closed: true, slot: slot)
                    b.line(Float(Double(cx) - Double(len) / 2 * cos(ang)), Float(Double(cy) - Double(len) / 2 * sin(ang)),
                           Float(Double(cx) + Double(len) / 2 * cos(ang)), Float(Double(cy) + Double(len) / 2 * sin(ang)), 1.2, DARK, 0.7)
                }
                leafAt(22, 24, 30, -0.6, 0); leafAt(60, 58, 24, 0.9, LIGHT)
            })
            l.append(p("art.brick", "Tuğla örgü", a, 80, 40, 0xFFB5_654A) { b in
                b.line(0, 0, 80, 0, 2, 0, 0.75); b.line(0, 20, 80, 20, 2, 0, 0.75)
                b.line(0, 0, 0, 20, 2, 0, 0.75); b.line(40, 20, 40, 40, 2, 0, 0.75)
            })
            l.append(p("art.herringbone", "Balıksırtı", a, 40, 40, 0xFFA9_7B50) { b in
                b.poly(0, 10, 20, 0, 20, 10, 0, 20, alpha: 0.85); b.poly(0, 30, 20, 20, 20, 30, 0, 40, slot: LIGHT)
                b.poly(20, 0, 40, 10, 40, 20, 20, 10, slot: LIGHT); b.poly(20, 20, 40, 30, 40, 40, 20, 30, alpha: 0.85)
            })
            l.append(p("art.basket", "Hasır örgü", a, 40, 40, 0xFFB0_8850) { b in
                for k in 1...3 {
                    let o = Float(k) * 5
                    b.line(0, o, 20, o, 1.6, 0, 0.8); b.line(20 + o, 0, 20 + o, 20, 1.6, 0, 0.8)
                    b.line(o, 20, o, 40, 1.6, 0, 0.8); b.line(20, 20 + o, 40, 20 + o, 1.6, 0, 0.8)
                }
            })
            l.append(p("art.checker", "Dama", a, 40, 40, navy) { b in b.rect(0, 0, 20, 20, alpha: 0.8); b.rect(20, 20, 20, 20, alpha: 0.8) })
            l.append(p("art.confetti", "Konfeti", a, 120, 120, coral) { b in
                var rnd = JavaRandom(7)
                for k in 0..<22 {
                    let x = rnd.nextFloat() * 120, y = rnd.nextFloat() * 120
                    let slot = slots[k % 4]
                    if k % 3 == 0 {
                        b.circle(x, y, 2.5 + rnd.nextFloat() * 2, slot: slot)
                    } else {
                        let an = Double(rnd.nextFloat()) * PI, l = 4 + rnd.nextFloat() * 4, w: Float = 2
                        let dx = Float(cos(an) * Double(l)), dy = Float(sin(an) * Double(l))
                        let nx = Float(-sin(an) * Double(w)), ny = Float(cos(an) * Double(w))
                        b.poly(x - dx - nx, y - dy - ny, x + dx - nx, y + dy - ny, x + dx + nx, y + dy + ny, x - dx + nx, y - dy + ny, slot: slot)
                    }
                }
            })
            l.append(p("art.seigaiha", "Japon dalgası", a, 60, 30, navy) { b in
                for (cx, cy) in [(Float(30), Float(15)), (0, 30), (60, 30)] {
                    for (i, r) in [Float(30), 22, 14, 6].enumerated() {
                        b.arc(cx, cy, r, 180, 360, slot: i % 2 == 0 ? PAPER : LIGHT, alpha: 1, fill: true)
                        b.arc(cx, cy, r, 180, 360, w: 1.8, slot: 0, alpha: 0.9)
                    }
                }
            })
            l.append(p("art.triangles", "Üçgen mozaik", a, 60, 52, teal) { b in
                b.poly(0, 52, 30, 0, 60, 52, alpha: 0.75); b.poly(30, 0, 60, 0, 60, 52, slot: LIGHT)
                b.poly(0, 0, 30, 0, 0, 52, slot: LIGHT)
            })
            l.append(p("art.quatrefoil", "Dörtyaprak", a, 60, 60, plum) { b in
                for (dx, dy) in [(Float(11), Float(0)), (-11, 0), (0, 11), (0, -11)] {
                    b.circle(30 + dx, 30 + dy, 11, fill: false, width: 2, alpha: 0.85)
                }
                b.poly(0, -6, 6, 0, 0, 6, -6, 0, slot: DARK)
            })
            l.append(p("art.rings", "Halka zinciri", a, 50, 50, gold) { b in
                b.circle(0, 0, 22, fill: false, width: 2.6); b.circle(25, 25, 22, fill: false, width: 2.6, slot: DARK)
            })
            l.append(p("art.raindrops", "Yağmur damlaları", a, 60, 60, sky) { b in
                func drop(_ cx: Float, _ cy: Float, _ s: Float, _ slot: Int) {
                    let pts = (0...20).map { k -> (Float, Float) in
                        let t = PI * 2 * Double(k) / 20
                        let x = sin(t) * (1 - cos(t)) * 0.5 * Double(s)
                        let y = -cos(t) * Double(s)
                        return (cx + Float(x), cy + Float(y))
                    }
                    b.polyline(pts, fill: true, closed: true, slot: slot)
                }
                drop(15, 22, 9, 0); drop(45, 50, 7, LIGHT)
            })
            l.append(p("art.clouds", "Bulutlar", a, 120, 80, sky) { b in
                b.circle(30, 40, 14, slot: LIGHT); b.circle(46, 32, 17, slot: LIGHT); b.circle(62, 40, 13, slot: LIGHT)
                b.rect(30, 40, 32, 13, slot: LIGHT)
                b.circle(92, 70, 8, slot: LIGHT); b.circle(101, 65, 10, slot: LIGHT); b.circle(110, 70, 7, slot: LIGHT)
            })
            l.append(p("art.snowflakes", "Kar taneleri", a, 80, 80, sky) { b in
                func flake(_ cx: Float, _ cy: Float, _ r: Float) {
                    let R = Double(r)
                    for k in 0..<6 {
                        let an = PI / 3 * Double(k)
                        let ex = Float(Double(cx) + R * cos(an)), ey = Float(Double(cy) + R * sin(an))
                        b.line(cx, cy, ex, ey, 1.6, 0, 0.85)
                        let mx = Float(Double(cx) + R * 0.55 * cos(an)), my = Float(Double(cy) + R * 0.55 * sin(an))
                        b.line(mx, my, Float(Double(mx) + R * 0.3 * cos(an + 0.7)), Float(Double(my) + R * 0.3 * sin(an + 0.7)), 1.2, 0, 0.8)
                        b.line(mx, my, Float(Double(mx) + R * 0.3 * cos(an - 0.7)), Float(Double(my) + R * 0.3 * sin(an - 0.7)), 1.2, 0, 0.8)
                    }
                }
                flake(20, 20, 11); flake(60, 58, 7)
            })
            l.append(p("art.moonstars", "Ay ve yıldızlar", a, 100, 100, gold) { b in
                b.circle(30, 30, 13); b.circle(36, 26, 11, slot: PAPER)
                b.star(75, 22, 5); b.star(62, 76, 4, slot: LIGHT); b.star(20, 82, 6); b.circle(88, 55, 1.6); b.circle(45, 60, 1.2)
            })
            l.append(p("art.fan", "Art deco yelpaze", a, 60, 30, 0xFF2E_6F73) { b in
                for (cx, cy) in [(Float(30), Float(15)), (0, 30), (60, 30)] {
                    b.arc(cx, cy, 28, 180, 360, slot: PAPER, fill: true)
                    b.arc(cx, cy, 28, 180, 360, w: 1.8)
                    b.arc(cx, cy, 10, 180, 360, w: 1.4, slot: DARK)
                    for k in 1..<6 {
                        let an = PI + PI * Double(k) / 6
                        b.line(cx + Float(10 * cos(an)), cy + Float(10 * sin(an)), cx + Float(28 * cos(an)), cy + Float(28 * sin(an)), 1.2, 0, 0.8)
                    }
                }
            })
            l.append(p("art.terrazzo", "Mozaik taş", a, 140, 140, 0xFFC9_7B63) { b in
                var rnd = JavaRandom(31)
                for k in 0..<26 {
                    let x = rnd.nextFloat() * 140, y = rnd.nextFloat() * 140, r = 2.5 + rnd.nextFloat() * 6
                    let slot = slots[k % 4]
                    var pts: [(Float, Float)] = []
                    for j in 0..<5 {
                        let an = 2 * PI * Double(j) / 5 + Double(rnd.nextFloat())
                        let rr = r * (0.6 + 0.5 * rnd.nextFloat())
                        pts.append((x + Float(Double(rr) * cos(an)), y + Float(Double(rr) * sin(an))))
                    }
                    b.polyline(pts, fill: true, closed: true, slot: slot, alpha: 0.85)
                }
            })
        return l
    }()

    // MARK: - yazı kutusu (10)

    static let box: [PaperPattern] = {
        let b0 = PaperUsage.BOX
        var l: [PaperPattern] = []
            l.append(p("box.lined", "Çizgili not", b0, WIDE, 34, BLUE, page: { $0.line(0.08, 0, 0.08, 1, 1.6, ACC, 0.6) }) { ruled($0, 34, 0.45, 1.2) })
            l.append(p("box.grid", "Kareli not", b0, 24, 24, GREY) { grid($0, 24, 0.3, 0.9) })
            l.append(p("box.dots", "Noktalı not", b0, 22, 22, GREY) { $0.circle(11, 11, 1.3, alpha: 0.5) })
            l.append(p("box.gingham", "Pastel pötikare", b0, 24, 24, 0xFFE8_9AA9) { b in b.rect(0, 0, 12, 24, alpha: 0.16); b.rect(0, 0, 24, 12, alpha: 0.16) })
            l.append(p("box.stripes", "Pastel şerit", b0, 40, 40, 0xFF8F_C1D4) { $0.rect(0, 0, 14, 40, alpha: 0.22) })
            l.append(p("box.polka", "Minik puantiye", b0, 26, 26, 0xFFF0_A35E) { b in b.circle(6, 6, 2.8, alpha: 0.32); b.circle(19, 19, 2.8, alpha: 0.32) })
            l.append(p("box.washi", "Washi bantlı", b0, 30, 30, 0xFF9F_C5A8, page: { b in
                b.rect(0, 0, 1, 0.14, slot: LIGHT, alpha: 0.85)
                for k in 0..<12 { b.line(Float(k) / 12, 0, Float(k) / 12 + 0.06, 0.14, 1.5, 0, 0.35) }
            }) { $0.circle(15, 15, 1.4, alpha: 0.18) })
            l.append(p("box.kraft", "Kraft benekli", b0, 60, 60, 0xFF8A_6A43, tint: 0xFFC9_A777, tintAlpha: 0.35) { b in
                var rnd = JavaRandom(5)
                for _ in 0..<9 {
                    let x = rnd.nextFloat() * 60, y = rnd.nextFloat() * 60, r = 0.8 + rnd.nextFloat() * 1.2
                    b.circle(x, y, r, alpha: 0.35)
                }
            })
            l.append(p("box.ticket", "Bilet / kupon", b0, 0, 0, 0xFFD9_935B, page: { b in
                b.rect(0.04, 0.08, 0.92, 0.84, fill: false, width: 2, alpha: 0.7, dash: 9)
                b.line(0.74, 0.08, 0.74, 0.92, 1.6, 0, 0.6, dash: 7)
            }) { _ in })
            l.append(p("box.index", "Dizin kartı", b0, WIDE, 30, BLUE, inset: [0, 0.26, 0, 0.02],
              page: { $0.line(0, 0.2, 1, 0.2, 2.2, ACC, 0.75) }) { ruled($0, 30, 0.4, 1.1) })
        return l
    }()

    static let all: [PaperPattern] = note + art + box

    private static let index: [String: PaperPattern] = {
        var m: [String: PaperPattern] = [:]
        for p in all where m[p.id] == nil { m[p.id] = p }
        return m
    }()

    static func byId(_ id: String) -> PaperPattern? { index[id] }

    static func of(_ usage: PaperUsage) -> [PaperPattern] {
        switch usage {
        case .NOTE: return note
        case .ART: return art
        case .BOX: return box
        }
    }
}
