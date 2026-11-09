import Foundation

/// v36 sayfadaki çizgi roman şablonu (Android `PanelSets`): panel elemanları aynı set kimliğini ve düzeni taşır.
enum PanelSets {
    static let KIND = "comic.panel"
    static let INSET_FILL = "#FFFFFF"

    struct Found {
        let set: String
        let page: PanelPage
        let strokes: [Int: Stroke]
        func strokeIds() -> Set<String> { Set(strokes.values.map(\.id)) }
    }

    static func isPanel(_ s: Stroke) -> Bool { s.shape?.panel != nil }

    static func find(_ strokes: [Stroke]) -> Found? {
        guard let ref = strokes.last(where: isPanel)?.shape?.panel else { return nil }
        var members = [Int: Stroke]()
        var layout: PanelPage?
        for s in strokes {
            guard let r = s.shape?.panel, r.set == ref.set, members[r.leaf] == nil else { continue }
            members[r.leaf] = s
            if layout == nil { layout = PanelPage.decode(r.layout) }
        }
        guard var page = layout else { return nil }
        for id in page.leafIds() where members[id] == nil { page = page.remove(id) }
        return Found(set: ref.set, page: page, strokes: members)
    }

    static func build(_ page: PanelPage, _ set: String, _ w: Float, _ h: Float, _ style: ShapeStyle, _ order: Int) -> [Stroke] {
        let layout = page.encode()
        return page.shapes(w, h).enumerated().compactMap { i, s in
            var st = style
            if s.inset { st.fillColorHex = INSET_FILL }
            return stroke(s, PanelRef(set: set, leaf: s.id, layout: layout), st, UUID().uuidString.lowercased(), order + i)
        }
    }

    private static func stroke(_ s: PanelShape, _ ref: PanelRef, _ style: ShapeStyle, _ id: String, _ order: Int) -> Stroke? {
        guard s.size > 0 else { return nil }
        var pts = (0..<s.size).map { P(s.x($0), s.y($0)) }
        pts.append(pts[0])
        guard var built = ShapeFactory.fromPaths(KIND, [ShapePath(points: pts, closed: true)], style: style, graph: nil, id: id, order: order) else { return nil }
        built.shape?.panel = ref
        return built
    }

    /// Şablonu [page] düzenine göre yeniden kurar (Android `PanelSets.apply`).
    static func apply(_ strokes: [Stroke], _ found: Found, _ page: PanelPage, _ w: Float, _ h: Float, _ defaultStyle: ShapeStyle,
                      follow: Bool = true, added: ((Int, String) -> Void)? = nil) -> [Stroke] {
        let layout = page.encode()
        let shapes = Dictionary(page.shapes(w, h).map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        let old = follow ? Dictionary(found.page.shapes(w, h).map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a }) : [:]
        var out: [Stroke] = []
        out.reserveCapacity(strokes.count + 4)
        var lastPanel = -1
        for s in strokes {
            if let r = s.shape?.panel, r.set == found.set, let member = found.strokes[r.leaf], member == s {
                guard let shape = shapes[r.leaf], var base = s.shape?.style else { continue }
                if shape.inset && base.fillColorHex == nil { base.fillColorHex = INSET_FILL }
                guard var rebuilt = stroke(shape, PanelRef(set: found.set, leaf: r.leaf, layout: layout), base, s.id, s.order) else { continue }
                rebuilt.groupId = s.groupId; rebuilt.layerId = s.layerId; rebuilt.opacity = s.opacity; rebuilt.hidden = s.hidden; rebuilt.clip = nil
                out.append(rebuilt)
                lastPanel = out.count - 1
            } else {
                out.append(s)
            }
        }
        var style = found.strokes.values.first?.shape?.style ?? defaultStyle
        if found.strokes.values.first?.shape?.style != nil { style.fillColorHex = nil }
        let fresh: [Stroke] = page.leafIds().filter { found.strokes[$0] == nil }.compactMap { leaf in
            guard let shape = shapes[leaf] else { return nil }
            let id = UUID().uuidString.lowercased()
            added?(leaf, id)
            var st = style
            if shape.inset { st.fillColorHex = INSET_FILL }
            return stroke(shape, PanelRef(set: found.set, leaf: leaf, layout: layout), st, id, 0)
        }
        out.insert(contentsOf: fresh, at: lastPanel >= 0 ? lastPanel + 1 : out.count)
        let ordered = out.enumerated().map { i, s -> Stroke in var c = s; if c.order != i { c.order = i }; return c }
        if !follow || old.isEmpty { return ordered }
        return followContent(ordered, found, old, shapes)
    }

    private static func followContent(_ strokes: [Stroke], _ found: Found, _ old: [Int: PanelShape], _ now: [Int: PanelShape]) -> [Stroke] {
        let panelIds = found.strokeIds()
        let index = ElementOps.index(strokes)
        let byId = Dictionary(strokes.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        var owner = [Int: Set<String>]()
        let olds = old.values.sorted { $0.id < $1.id }
        for e in index.elements {
            if e.strokeIds.contains(where: { panelIds.contains($0) || byId[$0]?.shape?.panel?.set == found.set }) { continue }
            guard let box = ElementOps.bounds(e.strokeIds.compactMap { byId[$0] }) else { continue }
            let cx = box.center.x, cy = box.center.y
            guard let panel = olds.first(where: { $0.inset && $0.contains(cx, cy) }) ?? olds.first(where: { !$0.inset && $0.contains(cx, cy) }) else { continue }
            owner[panel.id, default: []].formUnion(e.allIds)
        }
        var out = strokes
        for (leaf, inside) in owner {
            guard let a = old[leaf]?.bounds(), let b = now[leaf]?.bounds() else { continue }
            if a == b { continue }
            let ids = index.expand(inside)
            guard let box = ElementOps.bounds(out.filter { ids.contains($0.id) }) else { continue }
            let ow = a[2] - a[0], oh = a[3] - a[1]
            if ow <= 0 || oh <= 0 { continue }
            let sx = (b[2] - b[0]) / ow, sy = (b[3] - b[1]) / oh
            let k = min(sx, sy)
            let nc = P(b[0] + (box.center.x - a[0]) * sx, b[1] + (box.center.y - a[1]) * sy)
            out = ElementOps.translate(out, ids, nc.x - box.center.x, nc.y - box.center.y)
            if k < 0.995 || k > 1.005 { out = ElementOps.scale(out, ids, k, k, pivot: nc) }
        }
        return out
    }
}

/// v45 çizgi roman kapak sayfası şablonu (Android `ComicCover`).
enum ComicCover {
    static let ART_PAGE = PanelPage(PanelNode.Leaf(0), 0)
    static let TITLE_SIZE: Float = 0.11
    static let TITLE_WIDTH: Float = 0.86
    static let TITLE_TOP: Float = 0.05
    static let SUBTITLE_SIZE: Float = 0.042
    static let SUBTITLE_WIDTH: Float = 0.7
    static let SUBTITLE_BOTTOM: Float = 0.95
    static let SUBTITLE_TEXT = "Yazar adı"
    static let TITLE_TEXT = "Başlık"
    static let TITLE_FONT_COLOR = "#111111"
    static let OUTLINE = "#FFFFFF"
    static let PANEL_INK = "#111111"

    static func subtitlePlaceholder() -> String { I18n.tr(SUBTITLE_TEXT) }
    static func titleFor(_ canvasName: String) -> String {
        let t = canvasName.trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? I18n.tr(TITLE_TEXT) : t
    }

    static func build(_ width: Int, _ height: Int, _ title: String, _ subtitle: String?) -> [Stroke] {
        let w = Float(width), h = Float(height), short = min(w, h)
        var out: [Stroke] = []
        let line = (short * 0.006).coerceIn(2, 14)
        out += PanelSets.build(ART_PAGE, UUID().uuidString.lowercased(), w, h, ShapeStyle(strokeColorHex: PANEL_INK, strokeWidth: line), 0)
        let titleSize = (short * TITLE_SIZE).coerceIn(TextController.MIN_TEXT_SIZE, TextController.MAX_TEXT_SIZE)
        var titleSpec = TextSpec(content: titleFor(title))
        titleSpec.font = .BANGERS; titleSpec.align = .CENTER
        titleSpec.outlineColorHex = OUTLINE; titleSpec.outlineWidth = titleSize * 0.06
        var tb = TextBox(); tb.width = w * TITLE_WIDTH; tb.effects = TextEffects(caps: true, letterSpacing: 0.02)
        titleSpec.box = tb
        let (tw, _) = TextRenderer.measure(titleSpec, titleSize)
        out.append(text(titleSpec, titleSize, (w - tw) / 2, h * TITLE_TOP))
        let sub = subtitle?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !sub.isEmpty {
            let subSize = (short * SUBTITLE_SIZE).coerceIn(TextController.MIN_TEXT_SIZE, TextController.MAX_TEXT_SIZE)
            var subSpec = TextSpec(content: sub)
            subSpec.font = .COMIC_RELIEF_BOLD; subSpec.align = .CENTER
            subSpec.outlineColorHex = OUTLINE; subSpec.outlineWidth = subSize * 0.05
            var sb = TextBox(); sb.width = w * SUBTITLE_WIDTH
            subSpec.box = sb
            let (sw, sh) = TextRenderer.measure(subSpec, subSize)
            out.append(text(subSpec, subSize, (w - sw) / 2, h * SUBTITLE_BOTTOM - sh))
        }
        return out.enumerated().map { i, s in var c = s; c.order = i; return c }
    }

    static func retitle(_ title: Stroke, _ pageWidth: Int, _ canvasName: String) -> Stroke {
        guard var next = title.text else { return title }
        next.content = titleFor(canvasName)
        let (tw, _) = TextRenderer.measure(next, title.sizePx)
        let top = title.points.map(\.y).min() ?? 0
        var c = title
        c.text = next
        c.points = TextRenderer.quadAt(next, title.sizePx, (Float(pageWidth) - tw) / 2, top).map { StrokePoint(ContentCodec.quantize($0.x), ContentCodec.quantize($0.y)) }
        return c
    }

    private static func text(_ spec: TextSpec, _ size: Float, _ left: Float, _ top: Float) -> Stroke {
        Stroke(id: UUID().uuidString.lowercased(),
               points: TextRenderer.quadAt(spec, size, left, top).map { StrokePoint(ContentCodec.quantize($0.x), ContentCodec.quantize($0.y)) },
               colorHex: TITLE_FONT_COLOR, brushType: .TEXT, sizePx: size, opacity: 1, order: 0, text: spec)
    }
}

/// v45 not alma panelleri (Android `NotesPanels`).
enum NotesPanels {
    static let KIND = "notes.panel"
    static let MARGIN: Float = 0.05
    static let GAP: Float = 0.024
    static let FRAME_COLOR = "#90A4AE"
    static let TINT_COLOR = "#E3F2FD"
    static let TINT_OPACITY: Float = 0.45
    static let CORNER: Float = 0.018
    static let LINE: Float = 0.0022
    static let SQUARE_SHARE: Float = 0.6

    struct Layout: Identifiable {
        let id: String
        let title: String
        let areaCount: Int
        let page: PanelPage?
        let square: Bool
        let rectsOf: (Float, Float) -> [[Float]]
        func rects(_ w: Float, _ h: Float) -> [[Float]] { rectsOf(w, h) }
    }

    private final class B {
        var next = 0
        func p() -> PanelNode { defer { next += 1 }; return PanelNode.Leaf(next) }
        func rows(_ w: [Float], _ c: [PanelNode]) -> PanelNode { .split(PanelNode.Split(true, c, w)) }
        func cols(_ w: [Float], _ c: [PanelNode]) -> PanelNode { .split(PanelNode.Split(false, c, w)) }
    }

    private static func layout(_ id: String, _ title: String, _ root: (B) -> PanelNode) -> Layout {
        let page = PanelPage(root(B()), MARGIN, GAP)
        return Layout(id: id, title: title, areaCount: page.leafIds().count, page: page, square: false) { w, h in page.shapes(w, h).map { $0.bounds() } }
    }

    private static func squares(_ id: String, _ title: String, _ cols: Int, _ rows: Int) -> Layout {
        Layout(id: id, title: title, areaCount: cols * rows, page: nil, square: true) { w, h in
            let s = min(w, h), m = s * MARGIN, g = s * GAP
            let side = min((w - 2 * m - Float(cols - 1) * g) / Float(cols), (h - 2 * m - Float(rows - 1) * g) / Float(rows))
            let left = (w - (Float(cols) * side + Float(cols - 1) * g)) / 2
            return (0..<(cols * rows)).map { i in
                let l = left + Float(i % cols) * (side + g), t = m + Float(i / cols) * (side + g)
                return [l, t, l + side, t + side]
            }
        }
    }

    private static func squareAndNotes(_ id: String, _ title: String) -> Layout {
        Layout(id: id, title: title, areaCount: 2, page: nil, square: true) { w, h in
            let s = min(w, h), m = s * MARGIN, g = s * GAP, iw = w - 2 * m, ih = h - 2 * m
            if ih >= iw {
                let side = min(iw, ih * SQUARE_SHARE), l = (w - side) / 2
                return [[l, m, l + side, m + side], [m, m + side + g, w - m, h - m]]
            }
            let side = min(ih, iw * SQUARE_SHARE), t = (h - side) / 2
            return [[m, t, m + side, t + side], [m + side + g, m, w - m, h - m]]
        }
    }

    static let layouts: [Layout] = [
        layout("two_cols", "İki sütun") { b in b.cols([1, 1], [b.p(), b.p()]) },
        layout("three_cols", "Üç sütun") { b in b.cols([1, 1, 1], [b.p(), b.p(), b.p()]) },
        layout("two_rows", "İki satır") { b in b.rows([1, 1], [b.p(), b.p()]) },
        layout("three_rows", "Üç satır") { b in b.rows([1, 1, 1], [b.p(), b.p(), b.p()]) },
        layout("grid_2x2", "2 × 2 ızgara") { b in b.rows([1, 1], (0..<2).map { _ in b.cols([1, 1], [b.p(), b.p()]) }) },
        layout("grid_2x3", "2 × 3 ızgara") { b in b.rows([1, 1, 1], (0..<3).map { _ in b.cols([1, 1], [b.p(), b.p()]) }) },
        layout("main_side", "Büyük alan ve yan sütun") { b in b.cols([2.2, 1], [b.p(), b.p()]) },
        layout("side_main", "Yan sütun ve büyük alan") { b in b.cols([1, 2.2], [b.p(), b.p()]) },
        layout("cornell", "Cornell not düzeni") { b in b.rows([0.78, 0.22], [b.cols([0.3, 0.7], [b.p(), b.p()]), b.p()]) },
        layout("header_two_cols", "Başlık ve iki sütun") { b in b.rows([0.16, 0.84], [b.p(), b.cols([1, 1], [b.p(), b.p()])]) },
        squares("square_2x2", "2 × 2 kare", 2, 2),
        squares("square_3x3", "3 × 3 kare", 3, 3),
        squareAndNotes("square_notes", "Kare alan ve not alanı"),
    ]

    static func byId(_ id: String) -> Layout? { layouts.first { $0.id == id } }
    static func isNotesPanel(_ s: Stroke) -> Bool { s.shape?.kind == KIND }

    static func style(_ w: Float, _ h: Float) -> ShapeStyle {
        let s = min(w, h)
        return ShapeStyle(strokeColorHex: FRAME_COLOR, strokeWidth: (s * LINE).coerceIn(1.5, 6), fillColorHex: TINT_COLOR,
                          fillOpacity: TINT_OPACITY, cornerRadius: s * CORNER)
    }

    static func build(_ layout: Layout, _ w: Float, _ h: Float, order: Int = 0) -> [Stroke] {
        let st = style(w, h)
        return layout.rects(w, h).enumerated().compactMap { i, r in
            let pts = [P(r[0], r[1]), P(r[2], r[1]), P(r[2], r[3]), P(r[0], r[3]), P(r[0], r[1])]
            return ShapeFactory.fromPaths(KIND, [ShapePath(points: pts, closed: true)], style: st, graph: nil, id: UUID().uuidString.lowercased(), order: order + i)
        }
    }
}
