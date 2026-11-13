import Foundation

/// v50 "Resimden çizim" eğitimi (Android `TutorialV50`; çizim modları: resim, çizgi roman, basit resim; çocuk modunda
/// yalnız kalıp): hazır bir resim (demlik) eklenir, otomatik vektörleştirilir, resim kaldırılınca kalan vektör çizgiler
/// şekil kalıbı olur, kalıp yana taşınıp kenarları boyunca kalemle çizilerek kopyalanır; son adımda kütüphaneden bir kalıp
/// (kedi) seçilip çizilir. Resim uygulamanın kendi çizgi roman nesnelerinden; eğitim kayıt dışı çalışır.
@MainActor
enum TutorialV50 {
    /// Vektörleştirilen hazır resim (uygulamanın çizgi roman nesneleri: demlik).
    static let IMAGE = "comic/objects/it_food_14.webp"
    /// Kütüphaneden seçilen kalıp.
    static let CAT = "lib.sketch.cat"
    private static let COPY_INK = "#1565C0"
    private static let CAT_INK = "#C2185B"
    private static let VECTORIZE_AUTO = "vectorize_auto"
    private static let VECTORIZE_EDGES = "vectorize_edges"
    private static let VECTORIZE_LINES = "vectorize_centerline"

    private static func imageAnchor(_ simple: Bool) -> String { simple ? TutorialAnchor.simple("image") : TutorialAnchor.add("image") }
    private static func templateAnchor(_ simple: Bool) -> String { simple ? TutorialAnchor.simple("template") : TutorialAnchor.TEMPLATE }

    private static func imageRect(_ d: TutorialDemo) -> FRect {
        d.h >= d.w ? FRect(d.w * 0.05, d.h * 0.05, d.w * 0.53, d.h * 0.42) : FRect(d.w * 0.05, d.h * 0.1, d.w * 0.4, d.h * 0.85)
    }

    private static func copyShift(_ d: TutorialDemo) -> P { d.h >= d.w ? P(d.w * 0.43, 0) : P(d.w * 0.5, 0) }

    private static func image(_ d: TutorialDemo) -> Stroke? { d.host.tutorialCanvas()?.strokes.first { $0.brushType == .IMAGE } }
    private static func traced(_ d: TutorialDemo) -> Stroke? {
        d.host.tutorialCanvas()?.strokes.last { $0.shape?.kind == ImageEditController.TRACED }
    }

    private static func ensureImage(_ d: TutorialDemo) async -> Stroke? {
        if let img = image(d) { return img }
        let r = imageRect(d)
        guard let img = await d.host.assetImage(IMAGE, target: r, fill: false) else { return nil }
        await d.place(r.center, [img])
        await d.pause(500)
        return image(d)
    }

    private static func ensureTraced(_ d: TutorialDemo) async -> Stroke? {
        if let t = traced(d) { return t }
        guard let img = await ensureImage(d) else { return nil }
        return await d.host.vectorizeImage(img.id, mode: .AUTO, lineWidth: d.u * 0.005, keepImage: true)
    }

    /// Vektör çizgilerden kalıp kurulur ve [by] kadar yana taşınır (parmak taşıma tutamağını sürükler).
    private static func templateFromTraced(_ d: TutorialDemo, by: P) async -> TemplateGhost? {
        guard let g = d.host.tutorialGuides, let t = await ensureTraced(d),
              let start = TemplateGhost.of(TemplateGhost.contoursOf([t])) else { return nil }
        g.openTemplate()
        await d.pause(350)
        let xs = t.points.map(\.x), ys = t.points.map(\.y)
        await d.tap(P(xs.reduce(0, +) / Float(max(xs.count, 1)), ys.reduce(0, +) / Float(max(ys.count, 1))))
        g.setTemplate(start)
        await d.pause(400)
        let n = d.fast ? 1 : 24
        let gap = d.u * 0.035
        for i in 1...n {
            let ghost = start.moved(by.x * Float(i) / Float(n), by.y * Float(i) / Float(n))
            g.setTemplate(ghost)
            if !d.fast { d.live(TutorialLive(pointer: ghost.moveHandle(gap: gap), pressed: true)) }
            await d.pause(TutorialDemo.FRAME_MS)
        }
        d.live(TutorialLive())
        return g.template
    }

    /// Kalıbın kenarları kalemle çizilir: en uzun [animated] kontur canlı, kalanlar hemen eklenir.
    private static func traceGhost(_ d: TutorialDemo, _ ghost: TemplateGhost, _ color: String, _ width: Float, animated: Int = 4) async {
        func length(_ p: [P]) -> Float {
            zip(p, p.dropFirst()).reduce(Float(0)) { acc, ab in let dx = ab.1.x - ab.0.x, dy = ab.1.y - ab.0.y; return acc + (dx * dx + dy * dy).squareRoot() }
        }
        let paths: [[P]] = ghost.contours.map { c in
            var path = (0..<(c.points.count / 2)).map { P(c.points[2 * $0], c.points[2 * $0 + 1]) }
            if c.closed, let f = path.first { path.append(f) }
            return path
        }.filter { $0.count >= 2 }.sorted { length($0) > length($1) }
        for path in paths.prefix(animated) { await d.stroke(path, .INK_PEN, color, width, speed: 2.2, taper: false) }
        let rest: [Stroke] = paths.dropFirst(animated).map { path in
            Stroke(id: UUID().uuidString, points: path.map { StrokePoint($0.x, $0.y) }, colorHex: color, brushType: .INK_PEN,
                   sizePx: width, opacity: 1, order: 0)
        }
        if !rest.isEmpty { d.host.commitElements(rest) }
        await d.pause(300)
    }

    private static func steps(simple: Bool) -> [TutorialStep] {
        [
            // 1. Resim ekle: demlik
            TutorialStep(imageAnchor(simple), "v50_tut_image_t", "v50_tut_image_b", mode: simple ? nil : .ADD) { d in
                await d.tapAnchor(imageAnchor(simple))
                _ = await ensureImage(d)
            },
            // 2. Otomatik vektörleştir (resmin üstüne çizgiler; resim soluklaşır)
            TutorialStep(simple ? TutorialAnchor.CANVAS : VECTORIZE_AUTO, "v50_tut_vec_t", "v50_tut_vec_b") { d in
                guard let img = await ensureImage(d) else { return }
                d.host.select([img.id])
                d.host.editImage(img.id)
                await d.pause(500)
                if !simple { await d.tapAnchor(VECTORIZE_AUTO) }
                if traced(d) == nil { _ = await d.host.vectorizeImage(img.id, mode: .AUTO, lineWidth: d.u * 0.005, keepImage: true) }
                d.host.mutate { list in list.map { s in var c = s; if c.id == img.id { c.opacity = 0.35 }; return c } }
                d.host.editImage(img.id)
                await d.pause(500)
            },
            // 3. Türler ve ayarlar; resim kaldırılır, yalnız vektör çizgiler kalır
            TutorialStep(simple ? TutorialAnchor.CANVAS : VECTORIZE_LINES, "v50_tut_types_t", "v50_tut_types_b") { d in
                guard await ensureTraced(d) != nil else { return }
                if !simple { await d.tapAnchor(VECTORIZE_EDGES); await d.tapAnchor(VECTORIZE_LINES) }
                if let img = image(d) {
                    await d.tap(P(img.points[0].x + (img.points[1].x - img.points[0].x) * 0.9, img.points[0].y + (img.points[3].y - img.points[0].y) * 0.9))
                    d.host.mutate { list in list.filter { s in s.id != img.id } }
                }
                d.host.select([])
                d.mode(.DRAW)
                await d.pause(500)
            },
            // 4. Vektörü kalıp yap ve yana taşı
            TutorialStep(templateAnchor(simple), "v50_tut_pick_t", "v50_tut_pick_b", mode: simple ? nil : .DRAW) { d in
                d.host.select([])
                await d.tapAnchor(templateAnchor(simple))
                _ = await templateFromTraced(d, by: copyShift(d))
            },
            // 5. Kalıbın kenarı boyunca çiz (kalıp açık kalır)
            TutorialStep(TutorialAnchor.CANVAS, "v50_tut_trace_t", "v50_tut_trace_b", guide: true) { d in
                guard let g = d.host.tutorialGuides else { return }
                var ghost = g.session == .TEMPLATE ? g.template : nil
                if ghost == nil { ghost = await templateFromTraced(d, by: copyShift(d)) }
                guard let ghost else { return }
                await traceGhost(d, ghost, COPY_INK, d.u * 0.0045)
            },
            // 6. Kütüphaneden kalıp (kedi), çizilir; Bitir
            TutorialStep(TutorialAnchor.TEMPLATE_LIBRARY, "v50_tut_lib_t", "v50_tut_lib_b", guide: true) { d in
                guard let g = d.host.tutorialGuides else { return }
                if g.session != .TEMPLATE { await d.tapAnchor(templateAnchor(simple)) }
                g.openTemplate()
                await d.pause(400)
                await d.tapAnchor(TutorialAnchor.TEMPLATE_LIBRARY)
                let at = d.h >= d.w ? d.p(0.5, 0.72) : d.p(0.5, 0.5)
                guard g.placeLibrary(CAT, center: at, size: d.u * 0.42) else { return }
                await d.pause(450)
                if let t = g.template { await traceGhost(d, t, CAT_INK, d.u * 0.006, animated: 3) }
                await d.tapAnchor(TutorialAnchor.RULER_FINISH)
                g.finish()
            },
        ]
    }

    private static var cache: [String: TutorialGroup] = [:]

    /// Resim ve çizgi roman (gelişmiş).
    static func advanced(_ kind: CanvasKind) -> TutorialGroup {
        let id = "v50_trace_\(kind.rawValue.lowercased())"
        if let g = cache[id] { return g }
        let g = TutorialGroup(id: id, kind: kind, title: "v50_tut_title", summary: "v50_tut_summary", icon: "scribble.variable", ai: false,
                              steps: steps(simple: false))
        cache[id] = g
        return g
    }

    /// Basit resim.
    static let simpleArt = TutorialGroup(id: "v50_trace_simple_art", kind: .SIMPLE_ART, title: "v50_tut_title", summary: "v50_tut_summary",
                                         icon: "scribble.variable", ai: false, steps: steps(simple: true))

    /// Çocuk modu: yalnız kalıpla çizme (kütüphaneden kedi, kenarından çizilir).
    static let kids = TutorialGroup(
        id: "v50_trace_kids", kind: .KIDS, title: "v50_tut_kids_title", summary: "v50_tut_kids_summary", icon: "scribble.variable", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.simple("template"), "v50_tut_kids_t", "v50_tut_kids_b") { d in
                guard let g = d.host.tutorialGuides else { return }
                await d.tapAnchor(TutorialAnchor.simple("template"))
                g.openTemplate()
                await d.pause(400)
                await d.tapAnchor(TutorialAnchor.TEMPLATE_LIBRARY)
                guard g.placeLibrary(CAT, center: d.p(0.5, 0.5), size: d.u * 0.6) else { return }
                await d.pause(450)
            },
            TutorialStep(TutorialAnchor.CANVAS, "v50_tut_kids2_t", "v50_tut_kids2_b", guide: true) { d in
                guard let g = d.host.tutorialGuides else { return }
                if g.session != .TEMPLATE {
                    g.openTemplate()
                    guard g.placeLibrary(CAT, center: d.p(0.5, 0.5), size: d.u * 0.6) else { return }
                }
                if let t = g.template { await traceGhost(d, t, CAT_INK, d.u * 0.012, animated: 3) }
                await d.tapAnchor(TutorialAnchor.RULER_FINISH)
                g.finish()
            },
        ])
}
