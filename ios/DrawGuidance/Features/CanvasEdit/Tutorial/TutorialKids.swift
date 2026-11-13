import Foundation

/// v47–v49 eğitimlerin yenilikleri (Android `TutorialV48`): değişen menüleri tanıtır. Adımlar yalnız değişen düğmeyi
/// vurgular (gösterim yok): kısa ve her ekran boyutunda güvenli.
@MainActor
enum TutorialV48 {
    private static func step(_ anchor: String, _ title: String, _ body: String, mode: EditMode? = nil) -> TutorialStep {
        TutorialStep(anchor, title, body, mode: mode)
    }

    private static var cache: [String: TutorialGroup] = [:]
    private static func cached(_ id: String, _ make: () -> TutorialGroup) -> TutorialGroup {
        if let g = cache[id] { return g }
        let g = make(); cache[id] = g; return g
    }

    /// Gelişmiş modlar (resim, not alma, çizgi roman): yeni mod menüsü, Özellikler, Taşı/Genişlet.
    static func advanced(_ kind: CanvasKind) -> TutorialGroup {
        let id = "v48_advanced_\(kind.rawValue.lowercased())"
        return cached(id) {
            TutorialGroup(id: id, kind: kind, title: "v48_tut_title", summary: "v48_tut_summary", icon: "star.circle", ai: false, steps: [
                step(TutorialAnchor.mode(.SELECT), "v48_tut_modes_t", "v48_tut_modes_b", mode: .SELECT),
                step(TutorialAnchor.BRUSH_SETTINGS, "v48_tut_props_t", "v48_tut_props_b", mode: .DRAW),
                step(TutorialAnchor.mode(.EDIT_PICK), "v48_tut_move_t", "v48_tut_move_b", mode: .EDIT_PICK),
            ])
        }
    }

    /// Basit resim ve basit not alma: üç menü ve Özellikler.
    static func simple(_ kind: CanvasKind) -> TutorialGroup {
        let id = "v48_simple_\(kind.rawValue.lowercased())"
        return cached(id) {
            TutorialGroup(id: id, kind: kind, title: "v48_tut_title", summary: "v48_tut_summary", icon: "star.circle", ai: false, steps: [
                step(TutorialAnchor.simple("shape"), "v48_tut_three_t", "v48_tut_three_b"),
                step(TutorialAnchor.simple("brush_settings"), "v48_tut_props_t", "v48_tut_props_b"),
            ])
        }
    }

    /// Çocuk modu: yeni sıra ve 3B'nin kaldırılması.
    static let kids = TutorialGroup(id: "v48_kids", kind: .KIDS, title: "v48_tut_title", summary: "v48_tut_summary", icon: "star.circle", ai: false,
                                    steps: [
                                        TutorialStep(TutorialAnchor.simple("shape"), "v48_tut_kids_t", "v48_tut_kids_b"),
                                        TutorialStep(TutorialAnchor.simple("challenge"), "v48_tut_kids2_t", "v48_tut_kids2_b"),
                                    ])
}

/// v37 çocuklar için çizimin eğitimi (Android `TutorialKids`): maskot Boyacık ilk girişte araçları (fırça, hazır resimler,
/// boya kovası, silgi, geri al, kâğıt rengi) tanıtır ve adım adım örnek bir resim yapar: güneş, ev, balonlar, çimen ve
/// gökyüzü. Her adımda maskot başka bir yüz ifadesine ve el hareketine bürünür; açıklamalar onun konuşma balonunda yazılır.
@MainActor
enum TutorialKids {
    static let INK = "#2A2A33"

    /// Örnek resmin yerleri (kâğıdın oranı): ev ve balonlar.
    static let HOUSE = FRect(0.30, 0.46, 0.70, 0.86)
    static let BALLOONS = FRect(0.68, 0.06, 0.94, 0.346)

    /// Eğitimdeki basılı tutma sayacı (imlecin halkası her basışta baştan dolar).
    static var holds = 1000
    /// Son imleç yeri (kova bir sonraki yere oradan kayar).
    static var lastBucket: P?

    static let intro = TutorialGroup(
        id: "kids_intro", kind: .KIDS, title: "tut_kids", summary: "tut_kids_text", icon: "face.smiling", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.CANVAS, "tut_kids_1", "tut_kids_1_text").looking(.HAPPY, .WAVE),
            TutorialStep(TutorialAnchor.simple("brush"), "v45_tut_kids_pen", "v45_tut_kids_pen_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("brush"))
                let c = d.p(0.2, 0.2)
                let r = d.u * 0.075
                // Güneş: kalın sarı daire ve turuncu ışınlar
                await d.stroke(TutorialArt.roughCircle(c, r * 0.5, seed: 3), .MARKER, "#FDD835", r * 1.15, speed: 1.4, taper: false)
                await d.stroke(TutorialArt.roughCircle(c, r, seed: 4), .MARKER, "#FBC02D", d.u * 0.022, taper: false)
                for i in 0..<8 {
                    let a = Float.pi / 4 * Float(i)
                    let dir = P(cosf(a), sinf(a))
                    await d.stroke([c + dir * (r * 1.4), c + dir * (r * 1.9)], .MARKER, "#FB8C00", d.u * 0.018, speed: 2.4)
                }
            }.looking(.EXPLAINING, .BRUSH),
            TutorialStep(TutorialAnchor.simple("shape"), "tut_kids_3", "tut_kids_3_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("shape"))
                await d.drag(d.p(HOUSE.left, HOUSE.top), d.p(HOUSE.right, HOUSE.bottom)) { a, b in
                    tutList(TutorialArt.shape("lib.kid_house", a, b, tutShapeStyle(INK, 4)))
                }
                await d.drag(d.p(BALLOONS.left, BALLOONS.top), d.p(BALLOONS.right, BALLOONS.bottom), durationMs: 500) { a, b in
                    tutList(TutorialArt.shape("lib.kid_balloons", a, b, tutShapeStyle(INK, 4)))
                }
            }.looking(.SURPRISED, .POINT),
            TutorialStep(TutorialAnchor.simple("paint"), "tut_kids_4", "tut_kids_4_text", mode: .PAINT) { d in
                // Evin parçaları: çatı, duvar, kapı, yuvarlak pencere, baca (tasarım biriminde 0..100)
                await d.bucket(d.inBox(HOUSE, 30, 40), "#E53935")
                await d.bucket(d.inBox(HOUSE, 30, 84), "#FDD835")
                await d.bucket(d.inBox(HOUSE, 50, 86), "#8D6E63")
                await d.bucket(d.inBox(HOUSE, 50, 33), "#4FC3F7")
                await d.bucket(d.inBox(HOUSE, 69, 21), "#8D6E63")
            }.looking(.EXCITED, .THUMBS_UP),
            TutorialStep(TutorialAnchor.simple("paint"), "tut_kids_5", "tut_kids_5_text", mode: .PAINT) { d in
                await d.bucket(d.inBox(BALLOONS, 50, 44), "#EC407A", whole: true)
            }.looking(.WINKING, .POINT),
            // v46 v45'in boya fırçası: şeklin içinde başlayan boya dışarı taşmaz (duvara ışık)
            TutorialStep(TutorialAnchor.simple("paint_brush"), "v46_tut_kids_brush", "v46_tut_kids_brush_text", mode: .PAINT) { d in
                await d.tapAnchor(TutorialAnchor.simple("paint_brush"))
                let wall = (0...8).map { i in d.inBox(HOUSE, 36 + Float(i) * 3.5, i % 2 == 0 ? 62 : 70) }
                await d.stroke(TutorialArt.smooth(wall, samples: 5), .MARKER, "#FFE082", d.u * 0.035, opacity: 0.85, speed: 1.6, taper: false)
            }.looking(.EXCITED, .BRUSH),
            TutorialStep(TutorialAnchor.simple("eraser"), "tut_kids_6", "tut_kids_6_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("eraser"))
                let scribble = (0...10).map { i in d.p(0.06 + Float(i) * 0.018, i % 2 == 0 ? 0.9 : 0.95) }
                await d.stroke(scribble, .MARKER, "#90A4AE", d.u * 0.012, speed: 1.6)
                await d.pause(250)
                await d.stroke(scribble, .ERASER, "#000000", d.u * 0.06, speed: 1.2, taper: false)
            }.looking(.EXPLAINING, .WAVE),
            TutorialStep(TutorialAnchor.UNDO, "tut_kids_7", "tut_kids_7_text") { d in
                await d.undoRedo()
            }.looking(.THINKING, .THINK),
            TutorialStep(TutorialAnchor.simple("background"), "tut_kids_8", "tut_kids_8_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("background"))
                d.host.setPaperGradient(GradientSpec(colors: ["#81D4FA", "#E1F5FE"]))
                await d.pause(500)
            }.looking(.HAPPY, .POINT),
            // v40 cetvel: kenarından çizilen çizgi dümdüz (uçurtma ipi gibi)
            TutorialStep(TutorialAnchor.simple("ruler"), "tut40_kids_ruler", "tut40_kids_ruler_text") { d in
                await d.kidsRuler(d.p(0.42, 0.3), -24, "#7E57C2")
            }.looking(.EXCITED, .BRUSH),
            // v40 50 seviyelik çizim görevi
            TutorialStep(TutorialAnchor.simple("challenge"), "tut40_kids_challenge", "tut40_kids_challenge_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("challenge"))
                await d.pause(400)
            }.looking(.SURPRISED, .THUMBS_UP),
            TutorialStep(TutorialAnchor.CANVAS, "tut_kids_9", "tut40_kids_end_text") { d in
                // Çimen: evin altında dalgalı yeşil çizgiler
                let grass = (0...20).map { i in d.p(Float(i) / 20, 0.925 + (i % 2 == 0 ? 0 : 0.012)) }
                await d.stroke(TutorialArt.smooth(grass, samples: 4), .MARKER, "#7CB342", d.u * 0.05, speed: 2.2, taper: false)
                let off = d.u * 0.04
                await d.stroke(TutorialArt.smooth(grass.map { P($0.x, $0.y + off) }, samples: 4), .MARKER, "#558B2F", d.u * 0.05, speed: 2.2, taper: false)
                await d.pause(300)
            }.looking(.PROUD, .CHEER),
        ])

    /// v40 eski tanıtımı bitirmiş çocuklara yenilikler (bir kez kendiliğinden): cetvel, 5 sn basılı tutup taşıma ve
    /// 50 seviyelik çizim görevi.
    static let whatsNew = TutorialGroup(
        id: "kids_v40", kind: .KIDS, title: "tut40_kids_new", summary: "tut40_kids_new_text", icon: "face.smiling", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.CANVAS, "tut40_kids_hi", "tut40_kids_hi_text").looking(.HAPPY, .WAVE),
            TutorialStep(TutorialAnchor.simple("ruler"), "tut40_kids_ruler", "tut40_kids_ruler_text") { d in
                await d.kidsRuler(d.p(0.5, 0.3), -15, "#7E57C2")
            }.looking(.EXPLAINING, .BRUSH),
            TutorialStep(TutorialAnchor.CANVAS, "tut40_kids_hold", "tut40_kids_hold_text") { d in
                guard let balloons = await d.drag(d.p(BALLOONS.left, BALLOONS.top + 0.3), d.p(BALLOONS.right, BALLOONS.bottom + 0.3), durationMs: 500, { a, b in
                    tutList(TutorialArt.shape("lib.kid_balloons", a, b, tutShapeStyle(INK, 4)))
                }).first else { return }
                await d.pause(300)
                await d.holdMove(balloons, P(-d.w * 0.5, -d.h * 0.25))
            }.looking(.SURPRISED, .POINT),
            TutorialStep(TutorialAnchor.simple("challenge"), "tut40_kids_challenge", "tut40_kids_challenge_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("challenge"))
                await d.pause(400)
            }.looking(.EXCITED, .CHEER),
        ])

    /// Maskotun bu adımdaki yüz ifadesi ve el hareketi.
    static func lookOf(_ step: TutorialStep) -> MascotLook { step.look ?? .idle }
}

extension TutorialDemo {
    /// Çizimin tasarım kutusunda (0..100) nokta → kâğıt noktası.
    func inBox(_ box: FRect, _ x: Float, _ y: Float) -> P {
        let a = p(box.left, box.top), b = p(box.right, box.bottom)
        return P(a.x + (b.x - a.x) * x / 100, a.y + (b.y - a.y) * y / 100)
    }

    /// Kova imleci [at] noktasına kayar, altındaki alan parlar; dokunur ([whole] ise 2 sn basılı tutar, halka dolar) ve alan
    /// ya da bütün şekil [color] ile boyanır.
    func bucket(_ at: P, _ color: String, whole: Bool = false) async {
        if !fast {
            let from = TutorialKids.lastBucket ?? (at + P(u * 0.15, u * 0.1))
            let frames = 14
            for f in 1...frames {
                if fast { break }
                host.paintCursor(from + (at - from) * (Float(f) / Float(frames)), hold: -1, color: color)
                await pause(TutorialDemo.FRAME_MS)
            }
            var hold = -1
            if whole { TutorialKids.holds += 1; hold = TutorialKids.holds }
            host.paintCursor(at, hold: hold, color: color)
            await pause(whole ? 2_050 : 520)
        }
        _ = host.paint(at, color: color, whole: whole)
        TutorialKids.lastBucket = at
        if !fast {
            host.paintCursor(at, hold: -1, color: color)
            await pause(300)
        }
        host.paintCursor(nil, hold: -1, color: nil)
    }
}
