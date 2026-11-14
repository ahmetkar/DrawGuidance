import Foundation

/// v36 odak çizgisi ayarları (Android `LinesOptions`).
struct LinesOptions: Equatable {
    var density: Float = 1
    var focusX: Float = 0.5
    var focusY: Float = 0.5
    var angle: Float = 0
    var length: Float = 1
    var thickness: Float = 1
    var colorHex: String? = nil
    var opacity: Float = 1
}

/// v36 yeni efektin ayarları (Android `EffectOptions`).
struct EffectOptions: Equatable {
    var size: Float = 0.4
    var angle: Float = 0
    var colorHex: String? = nil
    var opacity: Float = 1
}

/// v36 balon penceresinin yazı ve kuyruk ayarları (Android `BalloonTextOptions`).
struct BalloonTextOptions: Equatable {
    static let DEFAULT_TAIL_LENGTH: Float = 0.65
    static let DEFAULT_TAIL_CURVE: Float = 0.3

    var content = ""
    var font: TextFont? = nil
    var sizePx: Float? = nil
    var colorHex: String? = nil
    var align: TextAlignKind = .CENTER
    var tail: TailDirection = .BOTTOM_LEFT
    var length: Float = BalloonTextOptions.DEFAULT_TAIL_LENGTH
    var curve: Float = BalloonTextOptions.DEFAULT_TAIL_CURVE

    var customTail: Bool { tail != .BOTTOM_LEFT || length != Self.DEFAULT_TAIL_LENGTH || curve != Self.DEFAULT_TAIL_CURVE }
}

/// v27 çizgi roman tuvali (Android `ComicController`): konuşma balonları, panel şablonları, efekt yazıları, hız/odak
/// çizgileri ve efektler. Hepsi sıradan tuval elemanlarıdır.
@MainActor
final class ComicController {
    private let store: EditorStore
    private unowned let host: EditorHost
    private let texts: TextController
    private let brush: () -> BrushController

    static let DEFAULT_TEXT = "Merhaba!"
    static let PHONE_FILL = "#3B82F6"
    static let PHONE_BORDER = "#2563EB"
    static let PANEL_INK = "#111111"
    static let CHARACTER_HEIGHT: Float = 0.55
    static let MIN_THICKNESS: Float = 0.3
    static let MAX_THICKNESS: Float = 4
    static let MIN_EFFECT_SIZE: Float = 0.08
    static let MAX_EFFECT_SIZE: Float = 1

    init(_ store: EditorStore, _ host: EditorHost, texts: TextController, brush: @escaping () -> BrushController) {
        self.store = store; self.host = host; self.texts = texts; self.brush = brush
    }

    private func q(_ v: Float) -> Float { ContentCodec.quantize(v) }
    private func qp(_ pts: [StrokePoint]) -> [StrokePoint] { pts.map { StrokePoint(q($0.x), q($0.y)) } }
    private static func newId() -> String { UUID().uuidString.lowercased() }
    private static func reorder(_ s: [Stroke]) -> [Stroke] { s.enumerated().map { i, x in if x.order == i { return x }; var c = x; c.order = i; return c } }

    func selectedBalloon() -> Stroke? {
        guard store.selection.count == 1 else { return nil }
        return store.selectedStroke { $0.brushType == .TEXT && $0.text?.box?.balloon != nil }
    }

    func insertBalloon(_ style: BalloonStyle, speakers: Int, center: P?, options: BalloonTextOptions = BalloonTextOptions()) {
        guard let current = store.canvas else { return }
        let typed = !options.content.isBlank
        guard let stroke = balloonStroke(style, speakers: speakers, center: center, content: typed ? options.content : I18n.tr(Self.DEFAULT_TEXT), options: options) else { return }
        store.commit(current.strokes + [stroke])
        host.setMode(.SELECT)
        store.select([stroke.id])
        if !typed { texts.editSelected() }
    }

    func balloonStroke(_ style: BalloonStyle, speakers: Int, center: P?, content: String, options: BalloonTextOptions? = nil) -> Stroke? {
        guard let current = store.canvas else { return nil }
        let settings = texts.defaults
        let size = options?.sizePx?.coerceIn(TextController.MIN_TEXT_SIZE, TextController.MAX_TEXT_SIZE) ?? settings.sizePx
        let c = center ?? P(Float(current.width) / 2, Float(current.height) / 2)
        let width = (Float(min(current.width, current.height)) * 0.36).coerceIn(size * 6, size * 14)
        let tails: [BalloonTail]
        if style == .FREE || (style == .NARRATION && speakers == 0) { tails = [] }
        else if let options, options.customTail { tails = BalloonTails.tailsToward(options.tail, speakers, options.length, options.curve) }
        else if style == .PHONE { tails = BalloonTails.tailsToward(.BOTTOM_RIGHT, speakers, 0.22, 0.7) }
        else { tails = BalloonTails.defaultTails(speakers) }
        var balloon = BalloonSpec()
        balloon.style = style
        balloon.tails = tails
        balloon.fillHex = style == .NARRATION ? "#FFF59D" : style == .PHONE ? Self.PHONE_FILL : "#FFFFFF"
        balloon.strokeHex = style == .PHONE ? Self.PHONE_BORDER : "#111111"
        let font = options?.font ?? (settings.spec.font.group == .COMIC ? settings.spec.font : .COMIC_RELIEF)
        let spec = TextSpec(content: content, font: font, align: options?.align ?? .CENTER,
                            box: TextBox(width: width, balloon: balloon, effects: settings.spec.box?.effects))
        let (w, h) = TextRenderer.measure(spec, size)
        let points = qp(TextRenderer.quadAt(spec, size, c.x - w / 2, c.y - h / 2))
        let ink = options?.colorHex?.uppercased() ?? (style == .PHONE ? "#FFFFFF" : "#111111")
        return Stroke(id: Self.newId(), points: points, colorHex: ink, brushType: .TEXT, sizePx: size, opacity: 1, order: current.strokes.count, text: spec)
    }

    func updateBalloonText(content: String? = nil, font: TextFont? = nil, sizePx: Float? = nil, colorHex: String? = nil, align: TextAlignKind? = nil) {
        guard let s = selectedBalloon(), let text = s.text else { return }
        let size = sizePx?.coerceIn(TextController.MIN_TEXT_SIZE, TextController.MAX_TEXT_SIZE) ?? s.sizePx
        let k = size / s.sizePx
        var box = text.box
        if k != 1, var b = box {
            b.width = b.width.map { ($0 * k).coerceIn(TextController.MIN_BOX, TextController.MAX_BOX) }
            b.height = b.height.map { ($0 * k).coerceIn(TextController.MIN_BOX, TextController.MAX_BOX) }
            box = b
        }
        var spec = text
        if let content, !content.isBlank { spec.content = content }
        if let font { spec.font = font }
        if let align { spec.align = align }
        spec.box = box
        let ink = colorHex?.uppercased() ?? s.colorHex
        if spec == text && size == s.sizePx && ink == s.colorHex { return }
        guard let current = store.canvas else { return }
        var updated = s
        updated.text = spec; updated.sizePx = size; updated.colorHex = ink
        updated.points = qp(TextRenderer.refit(s, spec, size))
        let field = content != nil ? "content" : font != nil ? "font" : sizePx != nil ? "size" : colorHex != nil ? "color" : "align"
        store.commit(current.strokes.map { $0.id == s.id ? updated : $0 }, coalesce: "balloon-text-\(field)-\(s.id)")
    }

    func setBalloonTails(_ dir: TailDirection, length: Float, curve: Float) {
        guard let s = selectedBalloon(), var text = s.text, var box = text.box, var b = box.balloon, b.style != .FREE else { return }
        let tails = BalloonTails.tailsToward(dir, max(b.tails.count, 1), length, curve.coerceIn(-1, 1))
        if tails == b.tails { return }
        b.tails = tails; box.balloon = b; text.box = box
        replaceText(s, text, "balloon-tail-\(s.id)")
    }

    func updateBalloon(style: BalloonStyle? = nil, speakers: Int? = nil, fillHex: String? = nil, strokeHex: String? = nil) {
        guard let s = selectedBalloon(), var text = s.text, var box = text.box, let b = box.balloon else { return }
        var tails = b.tails
        if let speakers, speakers != tails.count {
            tails = speakers < tails.count ? Array(tails.prefix(speakers)) : tails + Array(BalloonTails.defaultTails(speakers).dropFirst(tails.count))
        }
        var nb = b
        nb.style = style ?? b.style; nb.tails = tails; nb.fillHex = fillHex ?? b.fillHex; nb.strokeHex = strokeHex ?? b.strokeHex
        if nb == b { return }
        box.balloon = nb; text.box = box
        replaceText(s, text, "balloon-\(s.id)")
    }

    func withTail(_ stroke: Stroke, _ index: Int, _ tail: BalloonTail) -> Stroke? {
        guard var text = stroke.text, var box = text.box, var b = box.balloon, b.tails.indices.contains(index) else { return nil }
        b.tails[index] = tail; box.balloon = b; text.box = box
        var s = stroke; s.text = text
        return s
    }

    private func replaceText(_ s: Stroke, _ spec: TextSpec, _ coalesce: String?) {
        guard let current = store.canvas else { return }
        var updated = s
        updated.text = spec
        updated.points = qp(TextRenderer.refit(s, spec, s.sizePx))
        store.commit(current.strokes.map { $0.id == s.id ? updated : $0 }, coalesce: coalesce)
    }

    // MARK: Panel şablonları

    @discardableResult
    func insertTemplate(_ templateId: String) -> Set<String> {
        guard let current = store.canvas, let template = ComicTemplates.byId(templateId) else { return [] }
        let w = Float(current.width), h = Float(current.height)
        let style = ShapeStyle(strokeColorHex: Self.PANEL_INK, strokeWidth: panelLine(w, h) * template.line)
        let old = PanelSets.find(current.strokes)?.strokeIds() ?? []
        let at = current.strokes.firstIndex { old.contains($0.id) }
        let rest = current.strokes.filter { !old.contains($0.id) }
        let added = PanelSets.build(template.page, Self.newId(), w, h, style, 0)
        if added.isEmpty { return [] }
        var merged = rest
        merged.insert(contentsOf: added, at: min(at ?? rest.count, rest.count))
        store.commit(Self.reorder(merged))
        let ids = Set(added.map(\.id))
        host.inserted(ids)
        return ids
    }

    func panelSet() -> PanelSets.Found? { store.canvas.flatMap { PanelSets.find($0.strokes) } }

    private struct PanelDrag { let key: String; let strokes: [Stroke]; let found: PanelSets.Found; var result: [Stroke]? }
    private var panelDrag: PanelDrag?

    @discardableResult
    func editPanels(coalesce: String? = nil, _ edit: (PanelPage) -> PanelPage?) -> Bool {
        guard let current = store.canvas, let found = PanelSets.find(current.strokes), let page = edit(found.page), page != found.page else { return false }
        let w = Float(current.width), h = Float(current.height)
        var drag: PanelDrag?
        if let key = coalesce {
            if let d = panelDrag, d.key == key, d.result == current.strokes { drag = d } else { drag = PanelDrag(key: key, strokes: current.strokes, found: found, result: nil) }
        }
        let base = drag?.strokes ?? current.strokes
        let baseFound = drag?.found ?? found
        let updated: [Stroke]
        if page.isEmpty {
            let ids = found.strokeIds()
            updated = current.strokes.filter { !ids.contains($0.id) }
        } else {
            updated = PanelSets.apply(base, baseFound, page, w, h, ShapeStyle(strokeColorHex: Self.PANEL_INK, strokeWidth: panelLine(w, h)))
        }
        store.commit(updated, coalesce: coalesce)
        drag?.result = store.canvas?.strokes
        panelDrag = drag
        return true
    }

    private func panelLine(_ w: Float, _ h: Float) -> Float { (min(w, h) * 0.006).coerceIn(2, 14) }

    // MARK: Efekt yazıları

    func insertSfx(_ preset: SfxPreset, center: P?) {
        guard let current = store.canvas, let stroke = sfxStroke(preset, center: center) else { return }
        store.commit(current.strokes + [stroke])
        host.inserted([stroke.id])
    }

    func insertSfxText(_ options: BalloonTextOptions, center: P?) {
        guard let current = store.canvas, let base = ComicSfx.presets.first else { return }
        let color = options.colorHex?.uppercased()
        let content = options.content.trimmed
        let preset = SfxPreset(text: content.isEmpty ? base.text : content, font: options.font?.rawValue ?? base.font,
                               colors: color.map { [Self.lighter($0), $0] } ?? base.colors, outline: color.map { Self.darker($0) } ?? base.outline,
                               shadow: base.shadow, tilt: base.tilt)
        guard let stroke = sfxStroke(preset, center: center, size: options.sizePx.map { ($0 * 2.4).coerceIn(24, 400) }) else { return }
        store.commit(current.strokes + [stroke])
        host.inserted([stroke.id])
    }

    func sfxStroke(_ preset: SfxPreset, center: P?, size size0: Float? = nil) -> Stroke? {
        guard let current = store.canvas else { return nil }
        let size = size0 ?? (texts.defaults.sizePx * 2.4).coerceIn(24, 400)
        let c = center ?? P(Float(current.width) / 2, Float(current.height) / 2)
        let font = TextFont(rawValue: preset.font) ?? .BANGERS
        let shadow: String? = preset.shadow.count == 7 || !preset.shadow.hasSuffix("00000000") ? preset.shadow : nil
        let spec = TextSpec(content: I18n.tr(preset.text), font: font, align: .CENTER, outlineColorHex: preset.outline, outlineWidth: size * 0.07,
                            box: TextBox(effects: TextEffects(shadowHex: shadow, shadowDx: 0.07, shadowDy: 0.08,
                                                              gradient: GradientSpec(colors: preset.colors, angle: 90), caps: true, letterSpacing: 0.02)))
        let (w, h) = TextRenderer.measure(spec, size)
        let a = Double(preset.tilt) * .pi / 180
        let ca = Float(cos(a)), sa = Float(sin(a))
        func rot(_ x: Float, _ y: Float) -> StrokePoint { StrokePoint(q(c.x + x * ca - y * sa), q(c.y + x * sa + y * ca)) }
        let points = [rot(-w / 2, -h / 2), rot(w / 2, -h / 2), rot(w / 2, h / 2), rot(-w / 2, h / 2)]
        return Stroke(id: Self.newId(), points: points, colorHex: preset.colors.last ?? "#111111", brushType: .TEXT, sizePx: size, opacity: 1,
                      order: current.strokes.count, text: spec)
    }

    // MARK: Hız / odak çizgileri

    func linesStrokes(_ kind: ComicLinesKind, density: Float, area: FRect, ink: String) -> [Stroke] {
        guard let current = store.canvas else { return [] }
        let seed = current.strokes.count * 31 + kind.ordinal
        return buildLines(kind, LinesOptions(density: density, colorHex: ink), seed, [area.left, area.top, area.right, area.bottom], Self.newId(), ink,
                          current.strokes.count)
    }

    func insertLines(_ kind: ComicLinesKind, density: Float = 1) { insertLines(kind, LinesOptions(density: density)) }

    func insertLines(_ kind: ComicLinesKind, _ options: LinesOptions) {
        guard let current = store.canvas else { return }
        let area = ElementOps.selectionBounds(current.strokes, store.selection) ?? FRect(0, 0, Float(current.width), Float(current.height))
        let seed = current.strokes.count * 31 + kind.ordinal
        let added = buildLines(kind, options, seed, [area.left, area.top, area.right, area.bottom], Self.newId(), options.colorHex ?? brush().inkColor(),
                               current.strokes.count)
        if added.isEmpty { return }
        store.commit(current.strokes + added)
        host.inserted(Set(added.map(\.id)))
    }

    private func baseWidth(_ kind: ComicLinesKind, _ area: [Float]) -> Float {
        (min(area[2] - area[0], area[3] - area[1]) * 0.006).coerceIn(1.5, 10) * ComicLines.widthFactor(kind)
    }

    private func buildLines(_ kind: ComicLinesKind, _ o: LinesOptions, _ seed: Int, _ area: [Float], _ group: String, _ ink: String, _ order0: Int,
                            layerId: String? = nil) -> [Stroke] {
        let lines = ComicLines.generate(kind, area[0], area[1], area[2], area[3], seed: seed, density: o.density, focusX: o.focusX, focusY: o.focusY,
                                        angle: o.angle, length: o.length)
        guard let b = ComicLines.bounds(lines) else { return [] }
        let w = baseWidth(kind, area) * o.thickness.coerceIn(Self.MIN_THICKNESS, Self.MAX_THICKNESS)
        let palette = kind == .ANIME_SOFT && o.colorHex == nil
        let params = BrushParams(comicLines: ComicLinesParams(kind: kind.rawValue, density: o.density, seed: Int32(truncatingIfNeeded: seed), area: area,
                                                              bounds: b, focusX: o.focusX, focusY: o.focusY, angle: o.angle, length: o.length, palette: palette))
        return lines.enumerated().map { n, l in
            Stroke(id: Self.newId(), points: l.xs.indices.map { i in StrokePoint(q(l.xs[i]), q(l.ys[i]), pressure: l.pressures[i], tilt: 0) },
                   colorHex: palette ? ComicLines.SOFT_COLORS[n % ComicLines.SOFT_COLORS.count] : ink, brushType: .INK_PEN, sizePx: max(w, 1),
                   opacity: o.opacity.coerceIn(0.05, 1), order: order0 + n, groupId: group, layerId: layerId, params: params)
        }
    }

    func selectedLinesDensity() -> Float? { selectedLines()?.0.density }

    private func selectedLines() -> (ComicLinesParams, [Stroke])? {
        guard let current = store.canvas else { return nil }
        let sel = current.strokes.filter { store.selection.contains($0.id) }
        guard let first = sel.first, let p = first.params?.comicLines, let group = first.groupId else { return nil }
        if sel.contains(where: { $0.groupId != group || $0.params?.comicLines == nil }) { return nil }
        return (p, current.strokes.filter { $0.groupId == group && $0.params?.comicLines != nil })
    }

    func selectedLinesOptions() -> (ComicLinesKind, LinesOptions)? {
        guard let (p, group) = selectedLines(), let kind = ComicLinesKind(rawValue: p.kind), let first = group.first else { return nil }
        let base = p.area.count == 4 ? baseWidth(kind, p.area) : first.sizePx
        return (kind, LinesOptions(density: p.density, focusX: p.focusX, focusY: p.focusY, angle: p.angle, length: p.length,
                                   thickness: (first.sizePx / base).coerceIn(Self.MIN_THICKNESS, Self.MAX_THICKNESS),
                                   colorHex: p.palette ? nil : first.colorHex, opacity: first.opacity))
    }

    func setLinesDensity(_ density: Float) {
        guard var (_, o) = selectedLinesOptions() else { return }
        let d = density.coerceIn(ComicLines.MIN_DENSITY, ComicLines.MAX_DENSITY)
        if abs(d - o.density) < 1e-3 { return }
        o.density = d
        setLinesOptions(o)
    }

    func setLinesOptions(_ options: LinesOptions) {
        guard let (p, group) = selectedLines(), let current = store.canvas, let kind = ComicLinesKind(rawValue: p.kind), let first = group.first,
              let groupId = first.groupId else { return }
        var o = options
        o.density = options.density.coerceIn(ComicLines.MIN_DENSITY, ComicLines.MAX_DENSITY)
        let ids = Set(group.map(\.id))
        guard let now = ElementOps.selectionBounds(current.strokes, ids) else { return }
        let nowArr = [now.left, now.top, now.right, now.bottom]
        let area = p.area.count == 4 && p.bounds.count == 4 ? ComicLines.movedArea(p.area, p.bounds, nowArr) : nowArr
        let index = current.strokes.firstIndex { $0.groupId == groupId } ?? 0
        let rebuilt = buildLines(kind, o, Int(p.seed), area, groupId, o.colorHex ?? first.colorHex, 0, layerId: first.layerId)
        if rebuilt.isEmpty { return }
        let kept = current.strokes.filter { !ids.contains($0.id) }
        let at = index.coerceIn(0, kept.count)
        let merged = Array(kept[..<at]) + rebuilt + Array(kept[at...])
        store.commit(Self.reorder(merged), coalesce: "lines-density-" + groupId)
        store.select(Set(rebuilt.map(\.id)))
    }

    // MARK: Efektler

    func insertEffect(_ def: EffectDef, center: P?, options: EffectOptions) {
        guard let current = store.canvas, let stroke = effectStroke(def, center: center, options: options) else { return }
        store.commit(current.strokes + [stroke])
        host.inserted([stroke.id])
    }

    func effectStroke(_ def: EffectDef, center: P?, options: EffectOptions) -> Stroke? {
        guard let current = store.canvas else { return nil }
        let c = center ?? P(Float(current.width) / 2, Float(current.height) / 2)
        let side = Float(min(current.width, current.height)) * options.size.coerceIn(Self.MIN_EFFECT_SIZE, Self.MAX_EFFECT_SIZE)
        let aspect = def.w / def.h
        let hw = aspect >= 1 ? side / 2 : side / 2 * aspect
        let hh = aspect >= 1 ? side / 2 / aspect : side / 2
        let color = options.colorHex ?? def.color
        guard let stroke = ShapeFactory.build(ShapeLibrary.PREFIX + def.id, P(c.x - hw, c.y - hh), P(c.x + hw, c.y + hh), style: effectStyle(def, color, side),
                                              graph: nil, id: Self.newId(), order: current.strokes.count, opacity: options.opacity.coerceIn(0.05, 1))
        else { return nil }
        let a = options.angle.truncatingRemainder(dividingBy: 360)
        return a == 0 ? stroke : ElementOps.rotate([stroke], [stroke.id], a, pivot: c)[0]
    }

    private func effectStyle(_ def: EffectDef, _ color: String, _ side: Float) -> ShapeStyle {
        ShapeStyle(strokeColorHex: def.lineOnly ? color : Self.darker(color), strokeWidth: (side * 0.012).coerceIn(1.2, 14), fillColorHex: color)
    }

    func selectedEffect() -> (Stroke, EffectDef)? {
        guard let current = store.canvas, store.selection.count == 1,
              let s = current.strokes.first(where: { store.selection.contains($0.id) }),
              let kind = s.shape?.kind, let def = ComicEffects.byId(kind) else { return nil }
        return (s, def)
    }

    func editEffect(scale: Float = 1, rotate: Float = 0, colorHex: String? = nil, opacity: Float? = nil) {
        guard let current = store.canvas, let (s, def) = selectedEffect(), !s.points.isEmpty else { return }
        let ids: Set<String> = [s.id]
        let n = Float(s.points.count)
        let pivot = P(s.points.reduce(0) { $0 + $1.x } / n, s.points.reduce(0) { $0 + $1.y } / n)
        var strokes = current.strokes
        var changed = false
        if scale != 1 { strokes = ElementOps.scale(strokes, ids, scale, scale, pivot: pivot); changed = true }
        if rotate.truncatingRemainder(dividingBy: 360) != 0 { strokes = ElementOps.rotate(strokes, ids, rotate, pivot: pivot); changed = true }
        if colorHex != nil || opacity != nil {
            changed = true
            strokes = strokes.map { st in
                guard st.id == s.id, var shape = st.shape else { return st }
                var style = shape.style
                if let colorHex {
                    style.fillColorHex = colorHex; style.fillGradient = nil
                    style.strokeColorHex = def.lineOnly ? colorHex : Self.darker(colorHex)
                }
                shape.style = style
                var c = st
                c.shape = shape; c.colorHex = style.strokeColorHex
                c.opacity = opacity?.coerceIn(0.05, 1) ?? st.opacity
                return c
            }
        }
        if !changed { return }
        store.commit(strokes, coalesce: "effect-" + s.id)
    }

    // MARK: Renk tonları

    private static func rgb(_ hex: String) -> Int? {
        let h = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        return Int(String(h.prefix(6)), radix: 16)
    }

    static func darker(_ hex: String) -> String {
        guard let v = rgb(hex) else { return "#1E1E1E" }
        func ch(_ shift: Int) -> Int { Int(Float(v >> shift & 0xFF) * 0.38).coerceIn(0, 255) }
        return String(format: "#%02X%02X%02X", ch(16), ch(8), ch(0))
    }

    static func lighter(_ hex: String) -> String {
        guard let v = rgb(hex) else { return "#FFFFFF" }
        func ch(_ shift: Int) -> Int { let c = v >> shift & 0xFF; return Int(Float(c) + Float(255 - c) * 0.45).coerceIn(0, 255) }
        return String(format: "#%02X%02X%02X", ch(16), ch(8), ch(0))
    }
}
