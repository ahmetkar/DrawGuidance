import Foundation

/// v40 eğitimleri (Android `TutorialV40`): gelişmiş türlerde (resim, not alma, çizgi roman) bu sürümün yenilikleri ayrı bir
/// grupta, basit resim ve basit not için kısa, sade dilli tanıtımlar. Gösterilenler: 5 sn basılı tutup taşıma, arka planı
/// taşıma, cetvel (tür, tutamaklar, birim, ölçek, kenar boyunca çizip Bitir) ve şekil kalıbı, kaydetme yeri, sayfa
/// kaydırıcısı, zeminli/zeminsiz diyagram, sayfa numarası ve üst/alt bilgi, hazır çizgi roman karakterleri ve arka
/// planları, basit türlerde yazı kutusunu boyutlandırma ve boya.
@MainActor
enum TutorialV40 {
    static let INK = "#2B2F4A"
    static let NOTE_INK = "#1C1B19"
    static let BALLPOINT = "#1E3A8A"
    static let COMIC_INK = "#111111"

    /// Evin parçalarının yeri (kâğıdın oranı; HOUSE şeklinin kutusu).
    static let HOUSE = FRect(0.30, 0.44, 0.70, 0.86)

    // MARK: - Resim: yenilikler

    static let art = TutorialGroup(
        id: "art_v40", kind: .ART, title: "tut40_art", summary: "tut40_art_text", icon: "star.circle", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.CANVAS, "tut40_hold", "tut40_hold_text") { d in
                d.host.setPaperGradient(GradientSpec(colors: ["#BFE3FF", "#FFF1DC"]))
                let w = d.u * 0.004
                guard let sun = await d.drag(d.p(0.12, 0.1), d.p(0.36, 0.34), durationMs: 450, { a, b in
                    tutList(TutorialArt.shape("SUN", a, b, tutShapeStyle("#F59E0B", w, fill: "#FFC94D")))
                }).first else { return }
                await d.pause(300)
                await d.holdMove(sun, P(d.w * 0.46, d.h * 0.06))
            },
            TutorialStep(TutorialAnchor.BACKGROUND, "tut40_bg", "tut40_bg_text") { d in
                await d.tapAnchor(TutorialAnchor.BACKGROUND)
            },
            TutorialStep(TutorialAnchor.RULER, "tut40_ruler", "tut40_ruler_text", mode: .DRAW) { d in
                await d.tapAnchor(TutorialAnchor.RULER)
                guard let g = d.ruler(center: d.p(0.5, 0.55)) else { return }
                await d.pause(400)
                await d.movePose(g, .ROTATE) { var p = $0; p.angle = -15; return p }
                let len = d.w * 0.7
                await d.movePose(g, .SIZE) { var p = $0; p.length = len; return p }
            },
            TutorialStep(TutorialAnchor.RULER_BAR, "tut40_ruler_bar", "tut40_ruler_bar_text", guide: true) { d in
                guard let g = d.ruler(center: d.p(0.5, 0.55)) else { return }
                await d.tapAnchor(TutorialAnchor.RULER_TYPE)
                g.setType(.PROTRACTOR); await d.pause(800)
                g.setType(.SET_SQUARE_45); await d.pause(800)
                g.setType(.STRAIGHT); await d.pause(300)
                await d.tapAnchor(TutorialAnchor.RULER_UNIT)
                g.setUnit(.MM); await d.pause(500)
                await d.tapAnchor(TutorialAnchor.RULER_SCALE)
                g.setScale(2); await d.pause(500)
            },
            TutorialStep(TutorialAnchor.CANVAS, "tut40_ruler_draw", "tut40_ruler_draw_text", guide: true) { d in
                guard let g = d.ruler(center: d.p(0.5, 0.55)) else { return }
                await d.alongEdge(g, .INK_PEN, INK, d.u * 0.006, top: true)
                await d.alongEdge(g, .INK_PEN, INK, d.u * 0.006, top: false)
                await d.pause(250)
                await d.finishGuide(g)
            },
            TutorialStep(TutorialAnchor.TEMPLATE, "tut40_template", "tut40_template_text", mode: .DRAW) { d in
                await d.tapAnchor(TutorialAnchor.TEMPLATE)
                await d.traceTemplate("STAR", d.p(0.74, 0.8), d.u * 0.3, .INK_PEN, "#E11D48", d.u * 0.006)
            },
            TutorialStep(TutorialAnchor.SAVE, "tut40_save", "tut40_save_text") { d in
                await d.tapAnchor(TutorialAnchor.SAVE)
            },
        ])

    // MARK: - Not alma: yenilikler

    static let notes = TutorialGroup(
        id: "notes_v40", kind: .NOTES, title: "tut40_notes", summary: "tut40_notes_text", icon: "star.circle", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.PAGES, "tut40_scrubber", "tut40_scrubber_text") { d in
                await d.longPressAnchor(TutorialAnchor.PAGES)
            },
            TutorialStep(TutorialAnchor.add("diagram"), "tut40_diagram_bg", "tut40_diagram_bg_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("diagram"))
                await d.twoDiagrams(d.h * 0.1, min(d.h * 0.16, d.u * 0.3), d.w * 0.08, d.w * 0.92, NOTE_INK)
            },
            TutorialStep(TutorialAnchor.MORE, "tut40_page_settings", "tut40_page_settings_text") { d in
                await d.tapAnchor(TutorialAnchor.MORE)
                await d.pause(300)
                var s = PageSettings()
                s.pageNumbers = true; s.numberFormat = .NUMBER_OF_TOTAL; s.header = I18n.tr("Ders notları"); s.headerAlign = .LEFT
                d.host.setPageSettings(s)
                await d.pause(500)
            },
            TutorialStep(TutorialAnchor.RULER, "tut40_ruler", "tut40_ruler_text", mode: .DRAW) { d in
                await d.tapAnchor(TutorialAnchor.RULER)
                guard let g = d.ruler(center: d.p(0.5, 0.62)) else { return }
                await d.pause(400)
                await d.movePose(g, .ROTATE) { var p = $0; p.angle = 0; return p }
                let len = d.w * 0.8
                await d.movePose(g, .SIZE) { var p = $0; p.length = len; return p }
            },
            TutorialStep(TutorialAnchor.CANVAS, "tut40_ruler_draw", "tut40_ruler_draw_text", guide: true) { d in
                guard let g = d.ruler(center: d.p(0.5, 0.62)) else { return }
                await d.alongEdge(g, .NOTE_BALLPOINT, BALLPOINT, d.u * 0.004, top: true)
                await d.pause(250)
                await d.finishGuide(g)
            },
            TutorialStep(TutorialAnchor.CANVAS, "tut40_hold", "tut40_hold_text") { d in
                guard let diagram = d.firstOf(.DIAGRAM) else { return }
                await d.holdMove(diagram, P(0, d.h * 0.05))
            },
            TutorialStep(TutorialAnchor.MORE, "tut40_notes_pdf", "tut40_notes_pdf_text") { d in
                await d.tapAnchor(TutorialAnchor.MORE)
                await d.pause(300)
                await d.tapAnchor(TutorialAnchor.SAVE)
            },
        ])

    // MARK: - Çizgi roman: yenilikler

    static let comic = TutorialGroup(
        id: "comic_v40", kind: .COMIC, title: "tut40_comic", summary: "tut40_comic_text", icon: "star.circle", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.add("characters"), "tut40_chars", "tut40_chars_text", mode: .ADD) { d in
                if d.strokes.isEmpty { d.host.commitElements(d.panels("big_top")) }
                await d.pause(300)
                await d.tapAnchor(TutorialAnchor.add("characters"))
            },
            // v41 hazır arka planlar Ekle → Mekân arka planı'nda
            TutorialStep(TutorialAnchor.add("locations"), "tut40_comic_bg", "tut40_comic_bg_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("locations"))
            },
            TutorialStep(TutorialAnchor.MORE, "tut40_page_settings", "tut40_page_settings_text") { d in
                await d.tapAnchor(TutorialAnchor.MORE)
                await d.pause(300)
                var s = PageSettings()
                s.pageNumbers = true; s.header = I18n.tr("Bölüm 1"); s.headerAlign = .CENTER
                d.host.setPageSettings(s)
                await d.pause(500)
            },
            TutorialStep(TutorialAnchor.MORE, "tut40_comic_export", "tut40_comic_export_text") { d in
                await d.tapAnchor(TutorialAnchor.MORE)
            },
            TutorialStep(TutorialAnchor.RULER, "tut40_ruler", "tut40_ruler_text", mode: .DRAW) { d in
                await d.tapAnchor(TutorialAnchor.RULER)
                guard let g = d.ruler(center: d.p(0.5, 0.3)) else { return }
                await d.pause(400)
                await d.movePose(g, .ROTATE) { var p = $0; p.angle = 20; return p }
                let len = d.w * 0.6
                await d.movePose(g, .SIZE) { var p = $0; p.length = len; return p }
            },
            TutorialStep(TutorialAnchor.CANVAS, "tut40_ruler_draw", "tut40_ruler_draw_text", guide: true) { d in
                guard let g = d.ruler(center: d.p(0.5, 0.3)) else { return }
                await d.alongEdge(g, .INK_PEN, COMIC_INK, d.u * 0.005, top: true)
                await d.alongEdge(g, .INK_PEN, COMIC_INK, d.u * 0.005, top: false)
                await d.pause(250)
                await d.finishGuide(g)
            },
            TutorialStep(TutorialAnchor.CANVAS, "tut40_hold", "tut40_hold_text") { d in
                guard let target = d.strokes.last(where: { $0.brushType == .SHAPE }) ?? d.firstOf(.INK_PEN) else { return }
                await d.holdMove(target, P(0, d.h * 0.05))
            },
        ])

    // MARK: - Basit resim

    static let simpleArt = TutorialGroup(
        id: "simple_art_intro", kind: .SIMPLE_ART, title: "tut40_simple_art", summary: "tut40_simple_art_text", icon: "paintbrush.pointed", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.simple("brush"), "tut40_sa_brush", "tut40_sa_brush_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("brush"))
                let hill = (0...12).map { i in d.p(Float(i) / 12, 0.9 + 0.025 * sinf(Float(i) * 0.9)) }
                await d.stroke(TutorialArt.smooth(hill, samples: 6), .MARKER, "#7CB342", d.u * 0.03, speed: 1.8, taper: false)
                let off = d.u * 0.035
                await d.stroke(TutorialArt.smooth(hill.map { P($0.x, $0.y + off) }, samples: 6), .MARKER, "#558B2F", d.u * 0.03, speed: 1.8, taper: false)
            },
            TutorialStep(TutorialAnchor.simple("shape"), "tut40_sa_shape", "tut40_sa_shape_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("shape"))
                let w = d.u * 0.005, w2 = d.u * 0.004
                await d.drag(d.p(HOUSE.left, HOUSE.top), d.p(HOUSE.right, HOUSE.bottom)) { a, b in tutList(TutorialArt.shape("HOUSE", a, b, tutShapeStyle(INK, w))) }
                await d.drag(d.p(0.08, 0.08), d.p(0.28, 0.28), durationMs: 450) { a, b in
                    tutList(TutorialArt.shape("SUN", a, b, tutShapeStyle("#F59E0B", w2, fill: "#FFC94D")))
                }
            },
            TutorialStep(TutorialAnchor.simple("paint"), "tut40_sa_paint", "tut40_sa_paint_text", mode: .PAINT) { d in
                let spots: [(P, String)] = [
                    (d.house(0.30, 0.36), "#E53935"), (d.house(0.62, 0.40), "#E53935"), (d.house(0.22, 0.90), "#FFD54F"),
                    (d.house(0.47, 0.82), "#8D6E63"), (d.house(0.22, 0.60), "#81D4FA"), (d.house(0.70, 0.60), "#81D4FA"),
                ]
                for (at, color) in spots {
                    await d.tap(at)
                    _ = d.host.paint(at, color: color, whole: false)
                    await d.pause(250)
                }
            },
            TutorialStep(TutorialAnchor.simple("move"), "tut40_sa_move", "tut40_sa_move_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("select"))
                await d.tapAnchor(TutorialAnchor.simple("move"))
                guard let sun = d.firstShape("SUN") else { return }
                await d.holdMove(sun, P(d.w * 0.6, 0))
            },
            TutorialStep(TutorialAnchor.simple("text"), "tut40_sa_text", "tut40_sa_text_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("text"))
                let t = TutorialArt.text(I18n.tr("Merhaba!"), d.w * 0.08, d.h * 0.06, d.u * 0.06, INK, font: .SANS, bold: true)
                await d.place(d.p(0.2, 0.09), [t])
                await d.resizeText(t.id, P(d.w * 0.18, d.h * 0.06))
            },
            TutorialStep(TutorialAnchor.simple("background"), "tut40_sa_bg", "tut40_sa_bg_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("background"))
                d.host.setPaperGradient(GradientSpec(colors: ["#90CAF9", "#E3F2FD"]))
                await d.pause(500)
            },
            TutorialStep(TutorialAnchor.simple("ruler"), "tut40_sa_ruler", "tut40_sa_ruler_text") { d in
                await d.simpleRuler(TutorialAnchor.simple("ruler"), d.p(0.5, 0.38), -8, .MARKER, "#5E35B1", d.u * 0.008,
                                    templateShape: "HEART", templateAt: d.p(0.82, 0.62), templateSize: d.u * 0.16)
            },
            TutorialStep(TutorialAnchor.SAVE, "tut40_sa_save", "tut40_sa_save_text") { d in
                await d.tapAnchor(TutorialAnchor.SAVE)
            },
        ])

    // MARK: - Basit not

    static let simpleNotes = TutorialGroup(
        id: "simple_notes_intro", kind: .SIMPLE_NOTES, title: "tut40_simple_notes", summary: "tut40_simple_notes_text", icon: "square.and.pencil",
        ai: false,
        steps: [
            TutorialStep(TutorialAnchor.simple("text"), "tut40_sn_text", "tut40_sn_text_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("text"))
                let t = TutorialArt.text(I18n.tr("Günün notları"), d.w * 0.08, d.h * 0.05, d.u * 0.06, NOTE_INK, font: .MERRIWEATHER, bold: true)
                await d.place(d.p(0.3, 0.07), [t])
                await d.resizeText(t.id, P(d.w * 0.12, d.h * 0.02))
            },
            TutorialStep(TutorialAnchor.simple("brush"), "tut40_sn_pen", "tut40_sn_pen_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("brush"))
                let top = d.h * 0.05 + d.u * 0.2
                for s in TutorialArt.handwriting("ödev", seed: 4, box: FRect(d.w * 0.08, top, d.w * 0.5, top + d.u * 0.075)) {
                    await d.stroke(s, .NOTE_BALLPOINT, BALLPOINT, d.u * 0.0045, speed: 1.6)
                }
            },
            TutorialStep(TutorialAnchor.simple("diagram"), "tut40_sn_diagram", "tut40_sn_diagram_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("diagram"))
                await d.twoDiagrams(d.h * 0.05 + d.u * 0.36, min(d.h * 0.14, d.u * 0.24), d.w * 0.08, d.w * 0.92, NOTE_INK)
            },
            TutorialStep(TutorialAnchor.simple("ruler"), "tut40_sn_ruler", "tut40_sn_ruler_text") { d in
                let y = d.h * 0.05 + d.u * 0.3
                await d.simpleRuler(TutorialAnchor.simple("ruler"), P(d.w * 0.5, y + d.u * 0.05), 0, .NOTE_BALLPOINT, BALLPOINT, d.u * 0.004)
            },
            TutorialStep(TutorialAnchor.CANVAS, "tut40_hold_simple", "tut40_hold_simple_text") { d in
                guard let diagram = d.firstOf(.DIAGRAM) else { return }
                await d.holdMove(diagram, P(0, d.h * 0.04))
            },
            TutorialStep(TutorialAnchor.PAGES, "tut40_sn_pages", "tut40_sn_pages_text") { d in
                await d.longPressAnchor(TutorialAnchor.PAGES)
            },
            TutorialStep(TutorialAnchor.MORE, "tut40_sn_pdf", "tut40_sn_pdf_text") { d in
                await d.tapAnchor(TutorialAnchor.MORE)
                await d.pause(300)
                var s = PageSettings()
                s.pageNumbers = true; s.footer = I18n.tr("Günün notları"); s.footerAlign = .LEFT; s.numberAlign = .RIGHT
                d.host.setPageSettings(s)
                await d.pause(500)
            },
        ])
}

// MARK: - v40 ortak gösterimler

extension TutorialDemo {
    /// Çizimin sınır kutusunun ortası.
    func center(_ s: Stroke) -> P {
        let xs = s.points.map(\.x), ys = s.points.map(\.y)
        return P(((xs.min() ?? 0) + (xs.max() ?? 0)) / 2, ((ys.min() ?? 0) + (ys.max() ?? 0)) / 2)
    }

    /// 5 sn basılı tutunca Taşı: parmak elemanın üstünde basılı kalır, halka dolar; eleman seçilir, Taşı açılır ve parmak
    /// kalkmadan sürüklenince eleman [by] kadar kayar.
    func holdMove(_ stroke: Stroke, _ by: P) async {
        let at = center(stroke)
        await hold(at)
        host.select([stroke.id])
        mode(.MOVE)
        let n = fast ? 1 : 26
        for i in 1...n {
            host.moveSelected(by.x / Float(n), by.y / Float(n))
            if !fast { live(TutorialLive(pointer: at + by * (Float(i) / Float(n)), pressed: true)) }
            await pause(30)
        }
        live(TutorialLive())
        await pause(300)
    }

    /// Cetvel açılır (açıksa yerinde kalır); [type] seçilir (ayar eğitim bitince eski haline döner).
    func ruler(type: RulerType = .STRAIGHT, center: P? = nil) -> RulerController? {
        guard let g = host.tutorialGuides else { return nil }
        if g.settings.type != type { g.setType(type) }
        if g.session != .RULER { g.openRuler(center: center ?? p(0.5, 0.5), visibleWidth: w * 0.9, thickness: u * 0.075) }
        return g
    }

    /// Cetvelin duruşu [target]'a doğru yavaşça değişir; parmak [handle] tutamağını sürükler.
    func movePose(_ g: RulerController, _ handle: RulerHandle, durationMs: Int = 800, _ target: (RulerPose) -> RulerPose) async {
        guard let start = g.pose else { return }
        let end = target(start)
        if !fast {
            let gap = u * 0.035
            if let hp = RulerShape(start).handle(handle, gap: gap) { live(TutorialLive(pointer: hp)); await pause(260) }
            let frames = max(durationMs / TutorialDemo.FRAME_MS, 2)
            for f in 1...frames {
                if fast { break }
                let t = Float(f) / Float(frames)
                let e = 1 - (1 - t) * (1 - t)
                var pose = start
                pose.cx = start.cx + (end.cx - start.cx) * e; pose.cy = start.cy + (end.cy - start.cy) * e
                pose.angle = start.angle + (end.angle - start.angle) * e; pose.length = start.length + (end.length - start.length) * e
                g.setPose(pose)
                live(TutorialLive(pointer: RulerShape(pose).handle(handle, gap: gap), pressed: true))
                await pause(TutorialDemo.FRAME_MS)
            }
            live(TutorialLive())
        }
        g.setPose(end)
    }

    /// Cetvelin üst (ya da alt) kenarı boyunca çizilen düz çizgi (gerçek çizimde çizgi kenara yapışır).
    @discardableResult
    func alongEdge(_ g: RulerController, _ brush: BrushType, _ color: String, _ width: Float, top: Bool = true) async -> Stroke? {
        guard let pose = g.pose else { return nil }
        let shape = RulerShape(pose)
        let half = pose.length / 2
        let y = (top ? -1 : 1) * (pose.thickness / 2 + width * 0.6)
        return await stroke([shape.toWorld(-half * 0.86, y), shape.toWorld(half * 0.86, y)], brush, color, width, speed: 0.8, taper: false)
    }

    /// Bitir'e dokunulur: normal çizime dönülür.
    func finishGuide(_ g: RulerController) async {
        await tapAnchor(TutorialAnchor.RULER_FINISH)
        g.finish()
    }

    /// Şekil kalıbı: kütüphaneden [shape] seçilir, [center] çevresinde [size] boyunda kalıp olur ve kenarı boyunca çizilerek
    /// kopyalanır. Kalıp açık kalır.
    @discardableResult
    func traceTemplate(_ shape: String, _ center: P, _ size: Float, _ brush: BrushType, _ color: String, _ width: Float) async -> Stroke? {
        guard let g = host.tutorialGuides else { return nil }
        if g.session != .TEMPLATE || g.template == nil {
            g.openTemplate()
            await pause(500)
            await tapAnchor(TutorialAnchor.TEMPLATE_LIBRARY)
            if !g.placeLibrary(shape, center: center, size: size) { return nil }
            await pause(450)
        }
        guard let ghost = g.template, let best = ghost.contours.max(by: { $0.points.count < $1.points.count }) else { return nil }
        var path = (0..<(best.points.count / 2)).map { P(best.points[2 * $0], best.points[2 * $0 + 1]) }
        if best.closed, let f = path.first { path.append(f) }
        if path.count < 2 { return nil }
        return await stroke(path, brush, color, width, speed: 1.3, taper: false)
    }

    /// Basit türlerde tek adımda cetvel (Cetvel düğmesi; kenar boyunca çizip Bitir) ve isteğe bağlı şekil kalıbı.
    func simpleRuler(_ anchor: String, _ rulerAt: P, _ angle: Float, _ brush: BrushType, _ color: String, _ width: Float,
                     templateShape: String? = nil, templateAt: P = P(0, 0), templateSize: Float = 0,
                     templateAnchor: String = TutorialAnchor.simple("template")) async {
        await tapAnchor(anchor)
        guard let g = ruler(center: rulerAt) else { return }
        await movePose(g, .ROTATE, durationMs: 600) { var p = $0; p.cx = rulerAt.x; p.cy = rulerAt.y; p.angle = angle; return p }
        await alongEdge(g, brush, color, width)
        await pause(250)
        await finishGuide(g)
        guard let templateShape else { return }
        await pause(300)
        await tapAnchor(templateAnchor)
        await traceTemplate(templateShape, templateAt, templateSize, brush, color, width)
        await pause(250)
        await finishGuide(g)
    }

    /// Basit türlerde kullanılan cetvel adımı (çocuk eğitimi).
    func kidsRuler(_ at: P, _ angle: Float, _ color: String) async {
        await simpleRuler(TutorialAnchor.simple("ruler"), at, angle, .MARKER, color, u * 0.014)
    }

    /// Yazı kutusunun köşe tutamağı sürüklenir: kutu büyür (basit türlerde Seç'te de tutamaklar görünür).
    func resizeText(_ id: String, _ by: P) async {
        mode(.SELECT)
        host.select([id])
        await pause(400)
        guard var cur = strokes.first(where: { $0.id == id }),
              let start = TextBoxResize.handles(cur).first(where: { $0.1 == TextBoxResize.BOTH })?.0 else { return }
        if !fast { live(TutorialLive(pointer: start)); await pause(300) }
        let n = fast ? 1 : 22
        for i in 1...n {
            let pt = start + by * (Float(i) / Float(n))
            if let next = TextBoxResize.resize(cur, TextBoxResize.BOTH, pt) {
                host.mutate { list in list.map { $0.id == id ? next : $0 } }
                cur = next
            }
            if !fast { live(TutorialLive(pointer: pt, pressed: true)) }
            await pause(30)
        }
        live(TutorialLive())
        await pause(300)
    }

    /// Sayfa şeridine uzun basma (kaydırıcı açılır).
    func longPressAnchor(_ anchor: String) async {
        if fast { return }
        live(TutorialLive(pointerAnchor: anchor))
        await pause(300)
        live(TutorialLive(pointerAnchor: anchor, pressed: true))
        await pause(1_100)
        live(TutorialLive())
    }

    /// Zeminli ve zeminsiz iki diyagram alt alta.
    func twoDiagrams(_ top: Float, _ height: Float, _ left: Float, _ right: Float, _ ink: String) async {
        let style = tutShapeStyle(ink, u * 0.003)
        let a = FRect(left, top, right, top + height)
        await drag(a.topLeft, a.bottomRight, durationMs: 600) { x, y in [TutorialArt.diagram(.PROCESS, FRect(x.x, x.y, y.x, y.y), style, DiagramThemes.EDITORIAL)] }
        await pause(300)
        let b = a.translate(0, height + u * 0.06)
        await drag(b.topLeft, b.bottomRight, durationMs: 600) { x, y in
            [TutorialArt.diagram(.PROCESS, FRect(x.x, x.y, y.x, y.y), style, DiagramThemes.withBackground(DiagramThemes.EDITORIAL, false))]
        }
    }

    /// Basit resimdeki evin kutusunda (0..1) nokta → kâğıt noktası.
    func house(_ x: Float, _ y: Float) -> P {
        let r = TutorialV40.HOUSE
        let a = p(r.left, r.top), b = p(r.right, r.bottom)
        return P(a.x + (b.x - a.x) * x, a.y + (b.y - a.y) * y)
    }
}
