import Foundation

/// Öneride kullanılan şekil kaynağı (Android `TemplateSource`): arama dizini ve öğrenme geri bildirimi.
protocol TemplateSource: AnyObject {
    var index: ShapeIndex? { get }
    var searchScope: ShapeScope? { get }
    var canvasKind: CanvasKind? { get }
    func accepted(_ key: String, _ drawn: [[P]])
    func acceptedPart(_ key: String, _ drawn: [[P]])
    func rejected(_ keys: [String], _ drawn: [[P]])
}

extension TemplateSource {
    func acceptedPart(_ key: String, _ drawn: [[P]]) { accepted(key, []) }
}

/// v21 çizimin düzgün geometrisi (Android `ShapeFix`).
struct ShapeFix: Equatable { let kind: InkShapeKind?; let strokeId: String; let points: [StrokePoint] }

/// Fırçanın öneri politikası (Android `SuggestPolicy`).
enum SuggestPolicy { case WRITING, GEOMETRY, NONE }

extension BrushType {
    var suggestPolicy: SuggestPolicy {
        switch self {
        case .PENCIL, .INK_PEN, .FINELINER, .CALLIGRAPHY, .MARKER, .HIGHLIGHTER, .NOTE_PENCIL, .NOTE_BALLPOINT, .NOTE_FOUNTAIN, .NOTE_GEL,
             .NOTE_FINELINER, .NOTE_FELT, .NOTE_HIGHLIGHTER: return .WRITING
        case .CHARCOAL, .FLAT_BRUSH, .HARD_ROUND, .PASTEL, .STAMP_STAR, .STAMP_HEART, .STAMP_LEAF, .PATTERN_DOTS, .PATTERN_ZIGZAG,
             .DOT_PEN, .BRUSH_3D, .INK_TRAIL, .DRIP_TRAIL, .PAINT_TRAIL: return .GEOMETRY
        default: return .NONE
        }
    }
}

/// v21 şekil veritabanından öneri (Android `TemplateFix`).
struct TemplateFix: Equatable {
    let key: String
    let name: String
    let category: String
    var confidence: Float
    let mode: MatchMode
    let strokeIds: [String]
    let bounds: FRect
    let strokes: [[StrokePoint]]
    var solid: String? = nil
    var drawn: [[P]] = []
    var spaces: [Space3D?] = []
    var vector = false
}

/// v38 şekil önerisi.
struct ShapeSuggestion: Equatable {
    let templates: [TemplateFix]
    let strokeIds: [String]
    let bounds: FRect
    var preview: [[StrokePoint]] { templates.first?.strokes ?? [] }
}

/// v38 çizgi önerisi.
struct LineSuggestion: Equatable {
    let fixes: [ShapeFix]
    let strokeIds: [String]
    let bounds: FRect
    var joints = 0
    var preview: [[StrokePoint]] { fixes.map(\.points) }
}

/// v23 yazı önerisi.
struct TextSuggestion: Equatable {
    let text: String
    let alternatives: [String]
    let strokeIds: [String]
    let bounds: FRect
    let confidence: Float
}

private func unionRect(_ rects: [FRect]) -> FRect? {
    guard var r = rects.first else { return nil }
    for b in rects.dropFirst() { r = FRect(min(r.left, b.left), min(r.top, b.top), max(r.right, b.right), max(r.bottom, b.bottom)) }
    return r
}

private func orderedIds(_ strokes: [Stroke]) -> [String] {
    var seen = Set<String>(); var out: [String] = []
    for s in strokes where seen.insert(s.id).inserted { out.append(s.id) }
    return out
}

/// v39 tek çizginin çizim miktarı (Android `SingleInk`).
enum SingleInk {
    private static func amount(_ s: Stroke) -> (length: Float, points: Int, w: Float, h: Float)? {
        if s.points.count < 2 { return nil }
        let xs = s.points.map(\.x), ys = s.points.map(\.y)
        return (SuggestRules.pathLength(xs, ys), s.points.count, xs.max()! - xs.min()!, ys.max()! - ys.min()!)
    }

    static func bigLine(_ s: Stroke, _ canvasW: Float = 0, _ canvasH: Float = 0) -> Bool {
        guard let a = amount(s) else { return false }
        return SuggestRules.bigLine(length: a.length, points: a.points, w: a.w, h: a.h, canvasW: canvasW, canvasH: canvasH)
    }

    static func bigText(_ s: Stroke, _ canvasW: Float = 0, _ canvasH: Float = 0) -> Bool {
        guard let a = amount(s) else { return false }
        return SuggestRules.bigText(length: a.length, points: a.points, w: a.w, h: a.h, canvasW: canvasW, canvasH: canvasH)
    }

    static func geometric(_ s: Stroke) -> Bool {
        guard let r = InkShapes.recognize(s.points.map { InkPoint($0.x, $0.y) }) else { return false }
        return r.kind != .POLYLINE && r.score >= GEOMETRIC_SCORE
    }

    static let GEOMETRIC_SCORE: Float = 0.5
}

/// Şablon arama (Android `TemplateSuggester`).
enum TemplateSuggester {
    private static let POOL = 40

    static func search(_ index: ShapeIndex, _ group: [Stroke], scope: ShapeScope? = nil, max maxCount: Int = 4) -> [TemplateFix] {
        if group.count < SuggestRules.SHAPE_MIN { return [] }
        let limit = maxCount > 4 ? 6 : 3
        let plane = planeOf(group[group.count - 1])
        let flats = group.map { flatten($0) }
        if extent(flats) < 16 { return [] }
        var out: [TemplateFix] = []
        for m in index.search(flats, .GROUP, limit: limit, minConfidence: TemplateSuggest.MIN_GROUP, scope: scope) {
            out += fixes(m, group, flats, plane)
        }
        var seen = Set<String>()
        return Array(out.sorted { $0.confidence > $1.confidence }.filter { seen.insert($0.key + "|" + ($0.solid ?? "null")).inserted }.prefix(maxCount))
    }

    static func group(_ all: [Stroke], _ last: Stroke, _ eligible: (Stroke) -> Bool) -> [Stroke] {
        let layer = last.layerId ?? ""
        var pool: [Stroke] = []
        for s in all.reversed() where s.id == last.id || (eligible(s) && (s.layerId ?? "") == layer && samePlane(s, last)) {
            pool.append(s)
            if pool.count >= POOL { break }
        }
        pool.reverse()
        guard let seed = pool.firstIndex(where: { $0.id == last.id }) else { return [] }
        let boxes = pool.map { box(flatten($0)) }
        return SuggestRules.near(boxes, seed, SuggestRules.SHAPE_PAD, SuggestRules.SHAPE_MIN_PAD, SuggestRules.SHAPE_MAX).map { pool[$0] }
    }

    static func planeOf(_ s: Stroke) -> Plane3D { s.params?.depth3d?.plane ?? .FREE }

    private static func samePlane(_ a: Stroke, _ b: Stroke) -> Bool {
        let sa = a.space.flatMap { $0.isSolid ? nil : $0 }, sb = b.space.flatMap { $0.isSolid ? nil : $0 }
        if sa != nil || sb != nil { return sa != nil && sb != nil }
        return planeOf(a) == planeOf(b)
    }

    static func flatten(_ s: Stroke) -> [P] {
        if s.space != nil { return s.points.map { P($0.x, $0.y) } }
        let plane = planeOf(s)
        if plane == .FREE || s.points.isEmpty { return s.points.map { P($0.x, $0.y) } }
        let a = P(s.points[0].x, s.points[0].y)
        return s.points.map { Iso3D.unproject(plane, a, $0.x, $0.y) }
    }

    static func box(_ pts: [P]) -> [Float] {
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude, r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for p in pts { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
        return [l, t, r, b]
    }

    private static func extent(_ strokes: [[P]]) -> Float {
        let b = box(strokes.flatMap { $0 })
        return max(b[2] - b[0], b[3] - b[1])
    }

    private static func fixes(_ m: TemplateMatch, _ group: [Stroke], _ flats: [[P]], _ plane: Plane3D) -> [TemplateFix] {
        let fb = box(flats.flatMap { $0 })
        var fitted = TemplateFit.fit(m.geometry, fb[0], fb[1], fb[2], fb[3])
        let visual = box(group.flatMap { s in s.points.map { P($0.x, $0.y) } })
        if plane != .FREE && !group.contains(where: { $0.space != nil }) {
            let anchor = P((fb[0] + fb[2]) / 2, (fb[1] + fb[3]) / 2)
            let projected = fitted.map { s in s.map { Iso3D.project(plane, anchor, $0.x, $0.y) } }
            let pb = box(projected.flatMap { $0 })
            let dx = (visual[0] + visual[2]) / 2 - (pb[0] + pb[2]) / 2
            let dy = (visual[1] + visual[3]) / 2 - (pb[1] + pb[3]) / 2
            fitted = projected.map { s in s.map { P($0.x + dx, $0.y + dy) } }
        }
        let size = max(visual[2] - visual[0], visual[3] - visual[1])
        let step = (size / 90).coerceIn(2, 6)
        let q = ContentCodec.quantize
        let ref = group.filter { $0.space?.isSolid == false }.max { $0.points.count < $1.points.count }?.space
        let strokes = fitted.map { s in TemplateFit.densify(s, step).map { StrokePoint(q($0.x), q($0.y)) } }
        let spaces: [Space3D?] = ref != nil ? strokes.map { _ in ref } : []
        let bounds = FRect(visual[0], visual[1], visual[2], visual[3])
        let base = TemplateFix(key: m.key, name: m.name, category: m.category, confidence: m.confidence, mode: m.mode, strokeIds: orderedIds(group),
                               bounds: bounds, strokes: strokes, drawn: flats, spaces: spaces)
        guard let solidId = m.solid else { return [base] }
        var solid = base
        solid.solid = solidId
        solid.confidence = m.confidence + (plane != .FREE || group.contains { $0.params?.depth3d != nil } ? 0.001 : -0.001)
        return [base, solid]
    }
}

/// v38 şekil önerisinin algoritması (Android `ShapeSuggester`).
final class ShapeSuggester: @unchecked Sendable {
    private weak var templates: TemplateSource?
    init(_ templates: TemplateSource?) { self.templates = templates }

    private lazy var vectors: VectorShapeMatcher = VectorShapeMatcher(Self.VECTOR_KINDS.compactMap { kind in
        let outline = Self.outline(kind.rawValue, FRect(0, 0, 100, 100))
        return outline.isEmpty ? nil : VectorShapeMatcher.Item(id: kind.rawValue, strokes: outline, stretch: kind.fit == .BOX)
    })

    func group(_ all: [Stroke], _ last: Stroke, _ isInk: (Stroke) -> Bool) -> [Stroke] { TemplateSuggester.group(all, last, isInk) }

    func suggest(_ group: [Stroke], _ all: [Stroke], _ isInk: (Stroke) -> Bool) -> ShapeSuggestion? {
        if !(SuggestRules.SHAPE_MIN...SuggestRules.SHAPE_MAX).contains(group.count) { return nil }
        let kind = templates?.canvasKind
        let maxCount = SimpleSuggest.maxTemplates(kind)
        var found: [TemplateFix] = []
        if let index = templates?.index { found = TemplateSuggester.search(index, group, scope: templates?.searchScope, max: maxCount) }
        let flat = !group.contains { $0.space != nil || TemplateSuggester.planeOf($0) != .FREE }
        var vector: [TemplateFix] = []
        if flat {
            vector = Self.vectorFixes(group, vectors, 2)
            if let kind { vector += SimpleSuggest.vectorFixes(kind, group) }
        }
        let fixes = SimpleSuggest.merge(found, vector, maxCount)
        guard let first = fixes.first else { return nil }
        return ShapeSuggestion(templates: fixes, strokeIds: orderedIds(group), bounds: first.bounds)
    }

    static let VECTOR_PREFIX = "vec."

    private static func square(_ b: FRect) -> FRect {
        let s = max(b.width, b.height), cx = (b.left + b.right) / 2, cy = (b.top + b.bottom) / 2
        return FRect(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
    }

    private static func aspectFrame(_ b: FRect, _ aspect: Float) -> FRect {
        let w: Float, h: Float
        if b.width / max(b.height, 1e-3) > aspect { h = b.height; w = h * aspect } else { w = b.width; h = w / aspect }
        let cx = (b.left + b.right) / 2, cy = (b.top + b.bottom) / 2
        return FRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    static func vectorFixes(_ ink: [Stroke], _ matcher: VectorShapeMatcher, _ limit: Int, _ minConfidence: Float = VectorShapeMatcher.MIN_CONFIDENCE) -> [TemplateFix] {
        let lines = ink.map { s in s.points.map { P($0.x, $0.y) } }.filter { $0.count >= 2 }
        if lines.isEmpty { return [] }
        guard let bx = PartFit.box(lines.flatMap { $0 }) else { return [] }
        let b = FRect(bx[0], bx[1], bx[2], bx[3])
        if max(b.width, b.height) < 24 { return [] }
        let ids = orderedIds(ink)
        let q = ContentCodec.quantize
        return matcher.match(lines, limit: limit, minConfidence: minConfidence).compactMap { m in
            if !ShapeFactory.canBuild(m.id) { return nil }
            let frame: FRect
            if ShapeFactory.fit(m.id) == .SQUARE { frame = square(b) }
            else if ShapeFactory.isLibrary(m.id) { frame = aspectFrame(b, ShapeFactory.aspect(m.id)) }
            else { frame = b }
            let outline = outline(m.id, frame)
            if outline.isEmpty { return nil }
            return TemplateFix(key: VECTOR_PREFIX + m.id, name: "", category: "", confidence: m.confidence, mode: .GROUP, strokeIds: ids,
                               bounds: frame, strokes: outline.map { s in s.map { StrokePoint(q($0.x), q($0.y)) } }, solid: m.id, vector: true)
        }
    }

    static let VECTOR_KINDS: [ShapeKind] = [
        .CIRCLE, .ELLIPSE, .SQUARE, .RECTANGLE, .ROUNDED_RECT, .TRIANGLE, .RIGHT_TRIANGLE, .DIAMOND, .PARALLELOGRAM, .TRAPEZOID,
        .PENTAGON, .HEXAGON, .OCTAGON, .STAR, .HEART, .CROSS, .ARROW_BLOCK, .KITE, .SEMICIRCLE, .MOON, .CLOUD, .DROP,
        .LEAF, .HOUSE, .SUN, .FLOWER, .APPLE, .FISH, .CUBE, .CYLINDER, .PYRAMID,
    ]

    /// Şeklin [frame] kutusundaki çizgileri (yazı ve kesik parçalar hariç).
    static func outline(_ kind: String, _ frame: FRect) -> [[P]] {
        ShapeFactory.paths(kind, P(frame.left, frame.top), P(frame.right, frame.bottom), ink: 2)
            .filter { $0.label == nil && !$0.dashed && $0.points.count >= 2 }
            .map(\.points)
    }
}

/// v34 basit çizim türlerinde öneriler (Android `SimpleSuggest`).
enum SimpleSuggest {
    private static let MIN_CONFIDENCE: Float = 0.42

    static func isSimple(_ kind: CanvasKind?) -> Bool { kind?.simple == true }
    static func maxTemplates(_ kind: CanvasKind?) -> Int { isSimple(kind) ? 8 : 4 }

    private static let lock = NSLock()
    nonisolated(unsafe) private static var matchers: [CanvasKind: VectorShapeMatcher] = [:]

    static func catalogIds(_ kind: CanvasKind) -> [String] {
        switch kind {
        case .KIDS: return ShapeLibrary.shared.forAudience(.KIDS).flatMap { c in c.shapes.map { ShapeLibrary.PREFIX + $0.id } }
        case .SIMPLE_ART:
            var seen = Set<String>()
            return ShapePicker.categories(kind).flatMap(\.items).filter { seen.insert($0).inserted }
        default: return ShapeCategory.forKind(kind).flatMap { c in c.shapes.map(\.rawValue) }
        }
    }

    static func matcher(_ kind: CanvasKind) -> VectorShapeMatcher {
        lock.lock()
        if let m = matchers[kind] { lock.unlock(); return m }
        lock.unlock()
        let m = VectorShapeMatcher(catalogIds(kind).compactMap { id in
            let aspect = ShapeFactory.isLibrary(id) ? ShapeFactory.aspect(id) : 1
            let frame = aspect >= 1 ? FRect(0, 0, 100, 100 / aspect) : FRect(0, 0, 100 * aspect, 100)
            let outline = ShapeSuggester.outline(id, frame)
            return outline.isEmpty ? nil : VectorShapeMatcher.Item(id: id, strokes: outline, stretch: !ShapeFactory.isLibrary(id) && ShapeFactory.fit(id) == .BOX)
        })
        lock.lock(); matchers[kind] = m; lock.unlock()
        return m
    }

    static func vectorFixes(_ kind: CanvasKind, _ ink: [Stroke], _ limit: Int = 4) -> [TemplateFix] {
        if !isSimple(kind) { return [] }
        let flat = ink.filter { $0.space == nil && TemplateSuggester.planeOf($0) == .FREE }
        if flat.isEmpty { return [] }
        return ShapeSuggester.vectorFixes(flat, matcher(kind), limit, MIN_CONFIDENCE)
    }

    static func merge(_ templates: [TemplateFix], _ vectors: [TemplateFix], _ maxCount: Int) -> [TemplateFix] {
        var seen = Set<String>()
        return Array((templates + vectors).sorted { $0.confidence > $1.confidence }.filter { seen.insert($0.key + "|" + ($0.solid ?? "null")).inserted }.prefix(maxCount))
    }
}

/// v38 çizgi önerisinin algoritması (Android `LineSuggester`).
final class LineSuggester: @unchecked Sendable {
    static let MIN_SHAPE_SCORE: Float = 0.2

    func fit(_ s: Stroke) -> ShapeFix? {
        guard s.points.count >= 2, let r = InkShapes.recognize(s.points.map { InkPoint($0.x, $0.y) }), r.score >= Self.MIN_SHAPE_SCORE else { return nil }
        let q = ContentCodec.quantize
        return ShapeFix(kind: r.kind, strokeId: s.id, points: r.points.map { StrokePoint(q($0.x), q($0.y)) })
    }

    func smoothFix(_ s: Stroke) -> ShapeFix? {
        if s.points.count < 4 { return nil }
        let sm = InkSmoothing.smooth(s.points)
        return Self.moved(s.points, sm) < 0.75 ? nil : ShapeFix(kind: nil, strokeId: s.id, points: sm)
    }

    func group(_ candidates: [Stroke], _ seed: Stroke) -> [Stroke] {
        let layer = seed.layerId ?? ""
        let pool = Array(candidates.filter { $0.id == seed.id || ($0.layerId ?? "") == layer }.suffix(SuggestRules.LINE_MAX))
        guard let si = pool.firstIndex(where: { $0.id == seed.id }) else { return [] }
        let boxes = pool.map { TemplateSuggester.box(TemplateSuggester.flatten($0)) }
        return SuggestRules.near(boxes, si, SuggestRules.LINE_PAD, SuggestRules.LINE_MIN_PAD).map { pool[$0] }
    }

    func suggest(_ strokes: [Stroke], canvasW: Float = 0, canvasH: Float = 0) -> LineSuggestion? {
        let single = strokes.count == 1
        if single && !SingleInk.bigLine(strokes[0], canvasW, canvasH) { return nil }
        if !single && strokes.count < SuggestRules.LINE_MIN { return nil }
        let considered = Array(strokes.prefix(SuggestRules.LINE_MAX))
        var own: [ShapeFix?] = considered.map { fit($0) ?? smoothFix($0) }
        let q = ContentCodec.quantize
        let open = considered.indices.filter { i in (own[i]?.kind == nil || own[i]?.kind == .POLYLINE) && considered[i].points.count >= 5 }
        if !open.isEmpty {
            let arcs = InkLines.arcs(open.map { i in considered[i].points.map { InkPoint($0.x, $0.y) } })
            for (k, i) in open.enumerated() {
                if let a = arcs[k] { own[i] = ShapeFix(kind: nil, strokeId: considered[i].id, points: a.map { StrokePoint(q($0.x), q($0.y)) }) }
            }
        }
        let items = considered.enumerated().map { i, s in
            InkLines.Item(own[i]?.kind, (own[i]?.points ?? s.points).map { InkPoint($0.x, $0.y) })
        }
        let snapped = InkLines.snapEndpoints(items)
        var fixes: [ShapeFix] = []
        for (i, s) in considered.enumerated() {
            let fix = own[i]
            let after = snapped.items[i].points
            if after != items[i].points {
                let base = fix?.points ?? s.points
                let pts: [StrokePoint] = after.count == base.count
                    ? after.enumerated().map { k, p in var c = base[k]; c.x = q(p.x); c.y = q(p.y); return c }
                    : after.map { StrokePoint(q($0.x), q($0.y)) }
                fixes.append(ShapeFix(kind: fix?.kind, strokeId: s.id, points: pts))
            } else if let fix, Self.moved(s.points, fix.points) >= 0.75 {
                fixes.append(fix)
            }
        }
        if fixes.count < (single ? 1 : SuggestRules.LINE_MIN) { return nil }
        var seen = Set<String>()
        let ids = fixes.map(\.strokeId).filter { seen.insert($0).inserted }
        guard let bounds = unionRect(considered.filter { ids.contains($0.id) }.compactMap { ElementOps.bounds($0) }) else { return nil }
        return LineSuggestion(fixes: fixes, strokeIds: ids, bounds: bounds, joints: snapped.joints)
    }

    static func moved(_ a: [StrokePoint], _ b: [StrokePoint]) -> Float {
        if a.count != b.count {
            if a.isEmpty || b.isEmpty { return .greatestFiniteMagnitude }
            let d = [abs(a.map(\.x).min()! - b.map(\.x).min()!), abs(a.map(\.x).max()! - b.map(\.x).max()!),
                     abs(a.map(\.y).min()! - b.map(\.y).min()!), abs(a.map(\.y).max()! - b.map(\.y).max()!)].max()!
            return max(d, 1)
        }
        var m: Float = 0
        for i in a.indices { m = max(m, abs(a[i].x - b[i].x) + abs(a[i].y - b[i].y)) }
        return m
    }
}

/// v23 yazı önerisinin algoritması (Android `TextSuggester`).
final class TextSuggester: @unchecked Sendable {
    static let MIN_CONFIDENCE: Float = 0.5
    static let POOL = 40

    private let language: () -> String?
    init(language: @escaping () -> String? = { I18n.language }) { self.language = language }

    private var cacheKey: [String] = []
    private var cacheValue: TextSuggestion?
    private var gateKey: [String] = []
    private var gateValue = false
    private var singleKey: [String] = []
    private var singleValue: TextSuggestion?

    func isWritingPen(_ s: Stroke) -> Bool { !s.points.isEmpty && s.brushType.suggestPolicy == .WRITING }

    func group(_ pool: [Stroke], _ seed: Stroke) -> [Stroke] {
        if !isWritingPen(seed) { return [] }
        let layer = seed.layerId ?? ""
        var candidates: [Stroke] = []
        for s in pool.reversed() where s.id == seed.id || (isWritingPen(s) && (s.layerId ?? "") == layer) {
            candidates.append(s)
            if candidates.count >= Self.POOL { break }
        }
        return line(candidates.reversed(), seed)
    }

    func line(_ recent: [Stroke], _ seed: Stroke? = nil) -> [Stroke] {
        guard let last = seed ?? recent.last, let lb = ElementOps.bounds(last) else { return [] }
        let candidates = recent.compactMap { s in ElementOps.bounds(s).map { (s, $0) } }
            .filter { _, b in b.bottom >= lb.top - lb.height * 0.6 && b.top <= lb.bottom + lb.height * 0.6 }
        if candidates.isEmpty { return [last] }
        let h = max(candidates.map { $0.1.height }.sorted()[candidates.count / 2], 8)
        let gap = h * 2.5
        var chosen: Set<String> = [last.id]
        var left = lb.left, right = lb.right
        var changed = true
        while changed {
            changed = false
            for (s, b) in candidates where !chosen.contains(s.id) {
                if b.right >= left - gap && b.left <= right + gap {
                    chosen.insert(s.id)
                    left = min(left, b.left); right = max(right, b.right)
                    changed = true
                }
            }
        }
        return recent.filter { chosen.contains($0.id) }
    }

    private func bounds(_ line: [Stroke]) -> FRect? { unionRect(line.compactMap { ElementOps.bounds($0) }) }
    private func ink(_ line: [Stroke]) -> [InkStroke] { line.map { s in InkStroke(s.points.map { InkPoint($0.x, $0.y) }) } }

    func looksLikeText(_ line: [Stroke], canvasW: Float = 0, canvasH: Float = 0) -> Bool {
        if line.isEmpty || !line.allSatisfy(isWritingPen) { return false }
        if line.count == 1 { return single(line[0], canvasW: canvasW, canvasH: canvasH) != nil }
        if line.count < SuggestRules.TEXT_MIN { return false }
        let lang = language()
        let key = line.map(\.id) + ["lang:\(lang ?? "null")"]
        if key == gateKey { return gateValue }
        let b = bounds(line)
        let ink = ink(line)
        let value = b != nil && (8...600).contains(b!.height) && Handwriting.looksLikeWriting(ink, language: lang) &&
            (Handwriting.writingScore(ink).map { $0 >= Handwriting.GATE_MIN } ?? true)
        gateKey = key
        gateValue = value
        return value
    }

    func single(_ s: Stroke, canvasW: Float = 0, canvasH: Float = 0, mode: InkMode = .MIXED) -> TextSuggestion? {
        if !isWritingPen(s) || !SingleInk.bigText(s, canvasW, canvasH) { return nil }
        let lang = language()
        let key = [s.id, "lang:\(lang ?? "null")", "mode:\(mode)", "c:\(SuggestRules.side(canvasW, canvasH))", "n:\(s.points.count)"]
        if key == singleKey { return singleValue }
        var value: TextSuggestion?
        if let bounds = ElementOps.bounds(s), bounds.height <= 600, !SingleInk.geometric(s),
           let r = Handwriting.recognize(ink([s]), mode: mode, language: lang), r.confidence >= SuggestRules.TEXT_SINGLE_CONFIDENCE, !r.text.isBlank {
            value = TextSuggestion(text: r.text, alternatives: Array(r.alternatives.filter { $0 != r.text }.prefix(3)), strokeIds: [s.id],
                                   bounds: bounds, confidence: r.confidence)
        }
        singleKey = key
        singleValue = value
        return value
    }

    func suggest(_ line: [Stroke], mode: InkMode = .MIXED, canvasW: Float = 0, canvasH: Float = 0) -> TextSuggestion? {
        if line.count == 1 { return single(line[0], canvasW: canvasW, canvasH: canvasH, mode: mode) }
        if line.count < SuggestRules.TEXT_MIN || !line.allSatisfy(isWritingPen) { return nil }
        let lang = language()
        let key = line.map(\.id) + ["lang:\(lang ?? "null")"]
        if key == cacheKey { return cacheValue }
        guard let bounds = bounds(line) else { return nil }
        var result: TextSuggestion?
        if looksLikeText(line), let r = Handwriting.recognize(ink(line), mode: mode, language: lang), r.confidence >= Self.MIN_CONFIDENCE, !r.text.isBlank {
            result = TextSuggestion(text: r.text, alternatives: Array(r.alternatives.filter { $0 != r.text }.prefix(3)), strokeIds: orderedIds(line),
                                    bounds: bounds, confidence: r.confidence)
        }
        cacheKey = key
        cacheValue = result
        return result
    }
}

/// v37 çocuk modunda maskotun önerileri (Android `KidsSuggest`, `KidsTopic`).
enum KidsTopic { case TEMPLATE, TEXT, LINE }

enum KidsSuggest {
    static func seed(_ s: ShapeSuggestion) -> Int64 {
        Int64((s.strokeIds.sorted().joined(separator: ", ") + "|" + (s.templates.first?.key ?? "null")).javaHashCode)
    }
    static func seed(_ s: TextSuggestion) -> Int64 { Int64((s.strokeIds.sorted().joined(separator: ", ")).javaHashCode) }
    static func seed(_ s: LineSuggestion) -> Int64 { Int64(("line|" + s.strokeIds.sorted().joined(separator: ", ")).javaHashCode) }
}
