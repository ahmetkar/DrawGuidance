import Foundation

/// v46 eğitimlerin yenilikleri (Android `TutorialV46`): v44–v46'da değişen arayüze göre (iki parçalı üst menü, boş yere
/// dokununca ekleme menüsü, seçili elemanın köşesindeki seçim menüsü, Panel ekle, formülle yeniden çizilen bilim ve element
/// şekilleri, gerçek panel şablonları, hazır mekân ve karakterler, kapak ve hazır şablonlar).
@MainActor
enum TutorialV46 {
    static let INK = "#2B2F4A"
    static let NOTE_INK = "#263238"
    static let COMIC_INK = "#111111"
    static let ACCENT = "#1565C0"
    static let RED = "#C62828"

    static let FREE_BODY = "lib.sci.phys.free_body"
    static let FREE_BODY_ID = "tut46-free-body"
    static let BOHR = "lib.sci.chem.bohr_atom"
    static let BOHR_ID = "tut46-bohr"
    static let LEVER = "lib.sci.phys.lever"
    static let LEVER_ID = "tut46-lever"

    /// v45 seçim menüsünün düğmeleri (Android `SelectionMenuIds`).
    enum Sel { static let MOVE = "move", SCALE = "scale", COPY = "copy", DELETE = "delete", EDIT = "edit" }

    /// Kutulu yazı (satırlar [width]'e göre kayar).
    nonisolated static func text(_ content: String, _ left: Float, _ top: Float, _ size: Float, _ color: String, _ font: TextFont,
                                 bold: Bool = false, width: Float? = nil) -> Stroke {
        var spec = TextSpec(content: content)
        spec.font = font; spec.bold = bold
        if let width { var b = TextBox(); b.width = width; spec.box = b }
        let points = TextRenderer.quadAt(spec, size, left, top).map { StrokePoint(ContentCodec.quantize($0.x), ContentCodec.quantize($0.y)) }
        var s = Stroke(id: UUID().uuidString, points: points, colorHex: color, brushType: .TEXT, sizePx: size, opacity: 1, order: 0)
        s.text = spec
        return s
    }

    nonisolated static func bottom(_ s: Stroke) -> Float { s.points.map(\.y).max() ?? 0 }

    /// Formülle çizilen bilim şekli ([params] değerleri).
    nonisolated static func param(_ kind: String, _ r: FRect, _ params: [String: String], id: String = UUID().uuidString, width: Float = 3) -> Stroke? {
        ShapeFactory.build(kind, r.topLeft, r.bottomRight, style: ShapeStyle(strokeColorHex: NOTE_INK, strokeWidth: width), graph: nil, id: id,
                           order: 0, params: params)
    }

    // MARK: - Çizim

    static let art = TutorialGroup(
        id: "art_v46", kind: .ART, title: "v46_tut_art", summary: "v46_tut_art_text", icon: "star.circle", ai: false,
        steps: [
            // İki parçalı üst menü: gökyüzü gradyanı ve fırçayla dağ silueti
            TutorialStep(TutorialAnchor.BRUSH, "v46_tut_split", "v46_tut_split_text") { d in
                await d.tapAnchor(TutorialAnchor.BRUSH)
                d.host.setPaperGradient(GradientSpec(colors: ["#FFB88C", "#DE6262", "#3A1C71"]))
                await d.pause(350)
                let ridge: [(Float, Float)] = [(0, 0.64), (0.14, 0.55), (0.26, 0.6), (0.4, 0.46), (0.55, 0.58), (0.7, 0.5), (0.86, 0.6), (1, 0.54)]
                await d.stroke(TutorialArt.smooth(d.path(ridge), samples: 8), .SOFT_ROUND, "#2E1A47", d.u * 0.02, speed: 1.6, taper: false)
            },
            // Boş yere dokununca ekleme menüsü: dağlar ve güneş
            TutorialStep(TutorialAnchor.mode(.ADD), "v46_tut_addmenu", "v46_tut_addmenu_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.mode(.ADD))
                let w = d.u * 0.003
                await d.tap(d.p(0.62, 0.3))
                await d.drag(d.p(0.5, 0.14), d.p(0.74, 0.38), durationMs: 520) { a, b in
                    tutList(TutorialArt.shape("CIRCLE", a, b, tutShapeStyle("#FFB74D", w, fill: "#FFB74D",
                                                                           gradient: GradientSpec(colors: ["#FFF3B0", "#FFB74D", "#FF7043"]))))
                }
                await d.tap(d.p(0.3, 0.7))
                await d.drag(d.p(0.02, 0.42), d.p(0.56, 0.9), durationMs: 520) { a, b in
                    tutList(TutorialArt.shape("TRIANGLE", a, b, tutShapeStyle("#3A2F6B", w, fill: "#5B4B8A", gradient: GradientSpec(colors: ["#7B6BB0", "#3A2F6B"]))))
                }
                await d.drag(d.p(0.4, 0.5), d.p(1, 0.92), durationMs: 520) { a, b in
                    tutList(TutorialArt.shape("TRIANGLE", a, b, tutShapeStyle("#24304F", w, fill: "#33416B", gradient: GradientSpec(colors: ["#4F5D8C", "#24304F"]))))
                }
            },
            // Seçim menüsü: güneşin köşesinde Taşı, Ölçekle, Kopyala, Sil, Düzenle
            TutorialStep(TutorialAnchor.mode(.SELECT), "v46_tut_selmenu", "v46_tut_selmenu_text", mode: .SELECT) { d in
                await d.tapAnchor(TutorialAnchor.mode(.SELECT))
                guard let sun = d.firstShape("CIRCLE") else { return }
                await d.tap(d.center(sun))
                d.host.select([sun.id])
                await d.pause(500)
            },
            TutorialStep(TutorialAnchor.selection(Sel.SCALE), "v46_tut_scale", "v46_tut_scale_text", mode: .SELECT) { d in
                guard let sun = d.firstShape("CIRCLE") else { return }
                d.host.select([sun.id])
                await d.tapAnchor(TutorialAnchor.selection(Sel.SCALE))
                d.host.scaleSelected(0.8)
                await d.pause(300)
                // Kopyala: kuşlar (fırça çizgisi kopyalanıp kaydırılır)
                let bird = await d.stroke(TutorialArt.smooth([d.p(0.2, 0.22), d.p(0.235, 0.2), d.p(0.255, 0.225), d.p(0.275, 0.2), d.p(0.31, 0.22)], samples: 8),
                                          .INK_PEN, "#2E1A47", d.u * 0.005, speed: 1.4)
                d.host.select([bird.id])
                await d.tapAnchor(TutorialAnchor.selection(Sel.COPY))
                let copies: [Stroke] = [P(d.w * 0.08, -d.h * 0.03), P(d.w * 0.15, d.h * 0.02)].map { off in
                    var c = bird
                    c.id = UUID().uuidString
                    c.points = bird.points.map { var q = $0; q.x += off.x; q.y += off.y; return q }
                    return c
                }
                await d.place(nil, copies)
            },
            TutorialStep(TutorialAnchor.selection(Sel.EDIT), "v46_tut_edit", "v46_tut_edit_text", mode: .SELECT) { d in
                guard let sun = d.firstShape("CIRCLE") else { return }
                d.host.select([sun.id])
                await d.tapAnchor(TutorialAnchor.selection(Sel.EDIT))
                await d.pause(600)
                d.host.select([])
            },
        ])

    // MARK: - Not alma

    static let notes = TutorialGroup(
        id: "notes_v46", kind: .NOTES, title: "v46_tut_notes", summary: "v46_tut_notes_text", icon: "star.circle", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.add("panels"), "v46_tut_panels", "v46_tut_panels_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("panels"))
                guard let layout = NotesPanels.byId("cornell") else { return }
                for s in NotesPanels.build(layout, d.w, d.h) { await d.place(nil, [s]); await d.pause(220) }
            },
            TutorialStep(TutorialAnchor.add("text"), "v46_tut_titles", "v46_tut_titles_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("text"))
                let areas = d.cornell()
                guard areas.count >= 2 else { return }
                let cue = areas[0], notes = areas[1]
                let pad = d.u * 0.02
                let title = text(I18n.tr("Kuvvet ve hareket"), notes.left + pad, notes.top + pad, d.u * 0.045, NOTE_INK, .POPPINS, bold: true,
                                 width: notes.width - 2 * pad)
                await d.place(P(notes.center.x, notes.top + pad), [title])
                let q1 = text(I18n.tr("F = m·a neyi anlatır?"), cue.left + pad, bottom(title) + d.u * 0.06, d.u * 0.032, RED, .CAVEAT,
                              width: cue.width - 2 * pad)
                await d.place(P(cue.center.x, q1.points.map(\.y).min() ?? 0), [q1])
            },
            TutorialStep(TutorialAnchor.add("shape"), "v46_tut_param", "v46_tut_param_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("shape"))
                guard d.cornell().count >= 2 else { return }
                let r = d.freeBodyRect(d.cornell()[1])
                guard let s = param(FREE_BODY, r, ["m": "4", "F": "20", "alpha": "0", "mu": "0.2"], id: FREE_BODY_ID) else { return }
                await d.drag(r.topLeft, r.bottomRight, durationMs: 600) { _, _ in [s] }
            },
            TutorialStep(TutorialAnchor.CANVAS, "v46_tut_values", "v46_tut_values_text") { d in
                guard d.cornell().count >= 2 else { return }
                let r = d.freeBodyRect(d.cornell()[1])
                await d.redraw(FREE_BODY_ID, FREE_BODY, r, ["m": "4", "F": "40", "alpha": "0", "mu": "0.2"])
                await d.redraw(FREE_BODY_ID, FREE_BODY, r, ["m": "4", "F": "40", "alpha": "30", "mu": "0.2"])
            },
            TutorialStep(TutorialAnchor.add("shape"), "v46_tut_element", "v46_tut_element_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("shape"))
                guard d.cornell().count >= 2 else { return }
                let notes = d.cornell()[1]
                let side = min(notes.width * 0.34, notes.height * 0.24)
                let m = d.u * 0.03
                let r = FRect(notes.right - side - m, notes.bottom - side - m, notes.right - m, notes.bottom - m)
                guard let atom = param(BOHR, r, ["z": "11"], id: BOHR_ID, width: 2) else { return }
                await d.drag(r.topLeft, r.bottomRight, durationMs: 500) { _, _ in [atom] }
                await d.pause(300)
                await d.redraw(BOHR_ID, BOHR, r, ["z": "17"])
            },
            TutorialStep(TutorialAnchor.add("formula"), "v46_tut_formula", "v46_tut_formula_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("formula"))
                let areas = d.cornell()
                guard areas.count >= 3 else { return }
                let notes = areas[1], summary = areas[2]
                let r = d.freeBodyRect(notes)
                let f = TutorialArt.formula("physics.newton2", notes.left + d.u * 0.03, r.bottom + d.u * 0.02, d.u * 0.045, ACCENT)
                await d.place(P(notes.left + d.u * 0.1, r.bottom + d.u * 0.04), tutList(f))
                let pad = d.u * 0.02
                let sum = text(I18n.tr("Özet: Net kuvvet ivmeyi belirler; sürtünme net kuvveti azaltır."), summary.left + pad, summary.top + pad,
                               d.u * 0.03, NOTE_INK, .NUNITO, width: summary.width - 2 * pad)
                await d.place(P(summary.center.x, summary.top + pad), [sum])
            },
        ])

    // MARK: - Çizgi roman

    static func comicPanelStrokes(_ w: Float, _ h: Float, _ u: Float) -> [Stroke] {
        guard let t = ComicTemplates.byId("three_big") else { return [] }
        let line = min(max(u * 0.006, 2), 14) * t.line
        return PanelSets.build(t.page, UUID().uuidString, w, h, ShapeStyle(strokeColorHex: COMIC_INK, strokeWidth: line), 0)
    }

    static func bg(_ id: String) -> String? { ComicAssets.background(id)?.file }
    static func char(_ id: String, _ variant: String) -> String? { ComicAssets.character(id)?.variants.first { $0.id == variant }?.file }

    static let comic = TutorialGroup(
        id: "comic_v46", kind: .COMIC, title: "v46_tut_comic", summary: "v46_tut_comic_text", icon: "star.circle", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.add("templates"), "v46_tut_tpl", "v46_tut_tpl_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("templates"))
                for s in comicPanelStrokes(d.w, d.h, d.u) { await d.place(nil, [s]); await d.pause(240) }
            },
            TutorialStep(TutorialAnchor.add("locations"), "v46_tut_places", "v46_tut_places_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("locations"))
                let boxes = d.panelBoxes()
                for (id, r) in zip(["cati_teras", "liman", "gunbatimi_cadde"], boxes) {
                    guard let path = bg(id), let img = await d.host.assetImage(path, target: r, fill: true) else { continue }
                    // Mekân panelin arkasında (panel çizgisi üstte)
                    d.host.mutate { list in [img] + list }
                    await d.pause(260)
                }
            },
            TutorialStep(TutorialAnchor.add("characters"), "v46_tut_chars", "v46_tut_chars_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("characters"))
                let b = d.panelBoxes()
                if b.count < 3 { return }
                let cast: [(String?, FRect)] = [
                    (char("kaptan_hilal", "aksiyon_02"), FRect(b[0].left + b[0].width * 0.1, b[0].top + b[0].height * 0.2,
                                                               b[0].left + b[0].width * 0.62, b[0].bottom - b[0].height * 0.04)),
                    (char("dedektif_cemal", "ifade_01"), FRect(b[1].left + b[1].width * 0.12, b[1].top + b[1].height * 0.26,
                                                               b[1].right - b[1].width * 0.12, b[1].bottom)),
                    (char("polis_murat", "ifade_02"), FRect(b[2].left + b[2].width * 0.12, b[2].top + b[2].height * 0.26,
                                                            b[2].right - b[2].width * 0.12, b[2].bottom)),
                ]
                for (path, r) in cast {
                    guard let path, let img = await d.host.assetImage(path, target: r, fill: false) else { continue }
                    await d.place(r.center, [img])
                }
            },
            TutorialStep(TutorialAnchor.add("balloon"), "v46_tut_balloons", "v46_tut_balloons_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("balloon"))
                let b = d.panelBoxes()
                if b.count < 3 { return }
                let n = P(b[0].left + b[0].width * 0.22, b[0].top + b[0].height * 0.1)
                await d.place(n, tutList(d.host.balloon(.NARRATION, speakers: 0, center: n, text: I18n.tr("Şehrin çatılarında bir kovalamaca…"))))
                let s1 = P(b[1].center.x, b[1].top + b[1].height * 0.15)
                await d.place(s1, tutList(d.host.balloon(.SPEECH, speakers: 1, center: s1, text: I18n.tr("Limanda bir hareket var."))))
                let s2 = P(b[2].center.x, b[2].top + b[2].height * 0.15)
                await d.place(s2, tutList(d.host.balloon(.SHOUT, speakers: 1, center: s2, text: I18n.tr("Hemen geliyorum!"))))
            },
            TutorialStep(TutorialAnchor.add("effects"), "v46_tut_fx", "v46_tut_fx_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("effects"))
                guard let top = d.panelBoxes().first else { return }
                let lines = d.host.comicLines(.SPEED_H, density: 0.7, area: FRect(top.left, top.top + top.height * 0.3, top.right, top.bottom), ink: COMIC_INK)
                // Hız çizgileri karakterin arkasında: mekânın hemen üstüne
                d.host.mutate { list in
                    let last = list.lastIndex { s in s.brushType == .IMAGE && (s.points.map(\.y).min() ?? .infinity) <= top.top + 1 }
                    var out = list
                    let at = min(max((last ?? -1) + 1, 0), out.count)
                    out.insert(contentsOf: lines, at: at)
                    return out
                }
                await d.pause(300)
                let at = P(top.left + top.width * 0.78, top.top + top.height * 0.32)
                let preset = ComicSfx.presets.first { $0.text == "WHOOSH!" } ?? ComicSfx.presets[0]
                await d.place(at, tutList(d.host.sfx(preset, center: at, size: d.u * 0.1)))
            },
            TutorialStep(TutorialAnchor.PAGES, "v46_tut_cover", "v46_tut_cover_text") { d in
                await d.pause(500)
            },
        ])

    // MARK: - Basit resim

    static let simpleArt = TutorialGroup(
        id: "simple_art_v46", kind: .SIMPLE_ART, title: "v46_tut_simple", summary: "v46_tut_simple_art_text", icon: "star.circle", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.simple("brush"), "v46_tut_split", "v46_tut_split_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("brush"))
                d.host.setPaperGradient(GradientSpec(colors: ["#B3E5FC", "#E1F5FE"]))
                let hill = (0...12).map { i in d.p(Float(i) / 12, 0.82 + 0.03 * sinf(Float(i) * 0.8)) }
                await d.stroke(TutorialArt.smooth(hill, samples: 6), .MARKER, "#7CB342", d.u * 0.035, speed: 1.8, taper: false)
            },
            TutorialStep(TutorialAnchor.simple("shape"), "v46_tut_s_shape", "v46_tut_s_shape_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("shape"))
                let w = d.u * 0.005, w2 = d.u * 0.004
                await d.drag(d.p(0.3, 0.42), d.p(0.7, 0.8)) { a, b in tutList(TutorialArt.shape("HOUSE", a, b, tutShapeStyle(INK, w, fill: "#FFE0B2"))) }
                await d.drag(d.p(0.72, 0.08), d.p(0.9, 0.26), durationMs: 450) { a, b in
                    tutList(TutorialArt.shape("SUN", a, b, tutShapeStyle("#F59E0B", w2, fill: "#FFC94D")))
                }
            },
            TutorialStep(TutorialAnchor.simple("rect_select"), "v46_tut_s_select", "v46_tut_s_select_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("rect_select"))
                guard let house = d.firstShape("HOUSE") else { return }
                await d.drag(d.p(0.26, 0.38), d.p(0.74, 0.84), durationMs: 600) { _, _ in [] }
                d.host.select([house.id])
                await d.pause(400)
                d.host.scaleSelected(0.9)
                await d.pause(300)
                d.host.select([])
            },
            TutorialStep(TutorialAnchor.simple("eyedropper"), "v46_tut_s_eyedrop", "v46_tut_s_eyedrop_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("eyedropper"))
                await d.tap(d.p(0.81, 0.17))
                await d.pause(300)
            },
        ])

    // MARK: - Basit not

    static let simpleNotes = TutorialGroup(
        id: "simple_notes_v46", kind: .SIMPLE_NOTES, title: "v46_tut_simple", summary: "v46_tut_simple_notes_text", icon: "star.circle", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.simple("panels"), "v46_tut_panels", "v46_tut_panels_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("panels"))
                guard let layout = NotesPanels.byId("square_notes") else { return }
                for s in NotesPanels.build(layout, d.w, d.h) { await d.place(nil, [s]); await d.pause(220) }
            },
            TutorialStep(TutorialAnchor.simple("shape"), "v46_tut_param", "v46_tut_param_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("shape"))
                guard let r = d.leverRect(), let s = param(LEVER, r, ["P": "60", "a": "1", "b": "2"], id: LEVER_ID) else { return }
                await d.drag(r.topLeft, r.bottomRight, durationMs: 600) { _, _ in [s] }
            },
            TutorialStep(TutorialAnchor.CANVAS, "v46_tut_values", "v46_tut_values_text") { d in
                guard let r = d.leverRect() else { return }
                await d.redraw(LEVER_ID, LEVER, r, ["P": "60", "a": "1", "b": "3"])
                await d.redraw(LEVER_ID, LEVER, r, ["P": "90", "a": "1", "b": "3"])
            },
        ])
}

extension TutorialDemo {
    /// Şeklin değerleri değişir; şekil aynı kutuda yeniden çizilir (formül alanına yazmak gibi).
    func redraw(_ id: String, _ kind: String, _ r: FRect, _ params: [String: String]) async {
        guard let next = TutorialV46.param(kind, r, params, id: id) else { return }
        host.mutate { list in list.map { s in
            guard s.id == id else { return s }
            var n = next; n.order = s.order; n.layerId = s.layerId
            return n
        } }
        await pause(650)
    }

    /// Cornell alanları (anahtar sütun, not alanı, özet) bu sayfada.
    func cornell() -> [FRect] {
        (NotesPanels.byId("cornell")?.rects(w, h) ?? []).map { FRect($0[0], $0[1], $0[2], $0[3]) }
    }

    func freeBodyRect(_ notes: FRect) -> FRect {
        let top = notes.top + u * 0.17
        let bw = min(notes.width * 0.86, (notes.height * 0.34) * 1.3)
        return FRect(notes.left + u * 0.03, top, notes.left + u * 0.03 + bw, top + bw / 1.3)
    }

    /// Kare + not yerleşiminin karesinde kaldıraç (1,8:1 kutu).
    func leverRect() -> FRect? {
        guard let sq = NotesPanels.byId("square_notes")?.rects(w, h).first else { return nil }
        let side = sq[2] - sq[0]
        let bw = side * 0.9, bh = bw / 1.8
        let cx = (sq[0] + sq[2]) / 2, cy = (sq[1] + sq[3]) / 2
        return FRect(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2)
    }

    /// Sayfadaki panellerin kutuları (satır satır, soldan sağa).
    func panelBoxes() -> [FRect] {
        strokes.filter { PanelSets.isPanel($0) }.map { s in
            FRect(s.points.map(\.x).min() ?? 0, s.points.map(\.y).min() ?? 0, s.points.map(\.x).max() ?? 0, s.points.map(\.y).max() ?? 0)
        }.sorted { a, b in
            let ra = Int(a.top / 10), rb = Int(b.top / 10)
            return ra != rb ? ra < rb : a.left < b.left
        }
    }
}
