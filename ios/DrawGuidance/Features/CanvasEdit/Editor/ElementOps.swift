import Foundation

/// Bir eleman: birbirine değen (mürekkebi üst üste binen) çizimlerin oluşturduğu şekil. Hiçbir çizime değmeyen çizim
/// tek başına bir elemandır. Silgi çizgileri listede görünmez; sildikleri elemana [eraserIds] olarak bağlanır
/// (Android `Element`).
struct Element: Equatable, Identifiable {
    let id: String
    let number: Int
    let strokeIds: [String]
    let eraserIds: [String]
    let groupId: String?
    let brushType: BrushType
    let colorHex: String
    /// v30 gizlenmiş eleman.
    var hidden = false

    var allIds: Set<String> { Set(strokeIds).union(eraserIds) }
}

/// Elemanlar panelindeki bir satır: tek bir eleman ya da bir grup ve üyeleri.
enum ElementRow: Equatable {
    case single(Element)
    case group(groupId: String, number: Int, members: [Element])
}

/// Çizim listesinden türetilen eleman dizini (Android `ElementIndex`).
final class ElementIndex {
    let strokes: [Stroke]
    let elements: [Element]
    let groupNumbers: [String: Int]
    private let byStroke: [String: Int]
    private let byId: [String: Int]
    private var boxes: [[Float]?]

    init(_ strokes: [Stroke], _ elements: [Element], _ groupNumbers: [String: Int]) {
        self.strokes = strokes
        self.elements = elements
        self.groupNumbers = groupNumbers
        var bs = [String: Int](), bi = [String: Int]()
        for (k, e) in elements.enumerated() {
            for id in e.strokeIds { bs[id] = k }
            for id in e.eraserIds { bs[id] = k }
            bi[e.id] = k
        }
        byStroke = bs; byId = bi
        boxes = Array(repeating: nil, count: strokes.count)
    }

    static let empty = ElementIndex([], [], [:])

    func elementOf(_ strokeId: String) -> Element? { byStroke[strokeId].map { elements[$0] } }
    func element(_ id: String) -> Element? { byId[id].map { elements[$0] } }

    /// v37 çizimlerin nokta kutuları (sol, üst, sağ, alt).
    func pointBox(_ position: Int) -> [Float] {
        if let b = boxes[position] { return b }
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude
        var r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for p in strokes[position].points {
            if p.x < l { l = p.x }; if p.x > r { r = p.x }
            if p.y < t { t = p.y }; if p.y > b { b = p.y }
        }
        let box = [l, t, r, b]
        boxes[position] = box
        return box
    }

    /// Seçime değen elemanları ve bu elemanların gruplarındaki diğer elemanları döndürür.
    func expand(_ ids: Set<String>) -> Set<String> {
        var touched = Set<Int>()
        for id in ids { if let k = byStroke[id] { touched.insert(k) } }
        if touched.isEmpty { return [] }
        let groups = Set(touched.compactMap { elements[$0].groupId })
        var out = Set<String>()
        for (k, e) in elements.enumerated() where touched.contains(k) || (e.groupId.map { groups.contains($0) } ?? false) {
            out.formUnion(e.strokeIds); out.formUnion(e.eraserIds)
        }
        return out
    }

    /// Tamamen seçili elemanlar.
    func selectedElements(_ selectedIds: Set<String>) -> [Element] {
        elements.filter { !$0.strokeIds.isEmpty && $0.strokeIds.allSatisfy(selectedIds.contains) }
    }
}

/// Çizim elemanları üzerindeki saf işlemler (Android `ElementOps`). Tüm işlevler yeni bir liste döndürür.
enum ElementOps {
    static let MIN_SCALE: Float = 0.05
    static let MAX_SCALE: Float = 20
    fileprivate static let CHUNK = 12
    fileprivate static let COVER_MARGIN: Float = 0.5
    fileprivate static let TOUCH_EPSILON: Float = 0.5

    static func index(_ strokes: [Stroke]) -> ElementIndex { ElementIndexer().update(strokes) }

    /// Panel satırları: en son çizilen en üstte.
    static func rows(_ index: ElementIndex) -> [ElementRow] {
        var emitted = Set<String>()
        var rows: [ElementRow] = []
        var byGroup = [String: [Element]]()
        for e in index.elements { if let g = e.groupId { byGroup[g, default: []].append(e) } }
        for element in index.elements.reversed() {
            if let g = element.groupId {
                if emitted.insert(g).inserted {
                    rows.append(.group(groupId: g, number: index.groupNumbers[g] ?? 0, members: (byGroup[g] ?? []).reversed()))
                }
            } else {
                rows.append(.single(element))
            }
        }
        return rows
    }

    /// [point] noktasındaki en üstteki elemanı bulur.
    static func hitTest(_ index: ElementIndex, _ point: P, tolerance: Float, skip: Set<String> = []) -> Element? {
        let strokes = index.strokes
        for position in strokes.indices.reversed() {
            let stroke = strokes[position]
            if stroke.brushType == .ERASER || stroke.points.isEmpty || skip.contains(stroke.id) { continue }
            let half: Float = stroke.brushType.isQuad ? 0 : halfWidth(stroke)
            if stroke.brushType != .SHAPE && !nearBox(index.pointBox(position), point, half + tolerance) { continue }
            if !insideClip(stroke, point) { continue }
            if distanceToShape(point, stroke) <= half + tolerance { return index.elementOf(stroke.id) }
        }
        return nil
    }

    private static func nearBox(_ b: [Float], _ point: P, _ pad: Float) -> Bool {
        point.x >= b[0] - pad && point.x <= b[2] + pad && point.y >= b[1] - pad && point.y <= b[3] + pad
    }

    // MARK: Gruplar

    static func group(_ strokes: [Stroke], _ ids: Set<String>, newGroupId: String, current: ElementIndex? = nil) -> [Stroke] {
        let index = (current.flatMap { $0.strokes == strokes ? $0 : nil }) ?? self.index(strokes)
        let targets = index.expand(ids)
        let elementCount = index.elements.filter { $0.strokeIds.contains(where: targets.contains) }.count
        if elementCount < 2 { return strokes }
        return strokes.map { s in
            guard targets.contains(s.id) else { return s }
            var c = s; c.groupId = newGroupId; return c
        }
    }

    static func ungroup(_ strokes: [Stroke], _ ids: Set<String>, current: ElementIndex? = nil) -> [Stroke] {
        let targets = ((current.flatMap { $0.strokes == strokes ? $0 : nil }) ?? index(strokes)).expand(ids)
        return strokes.map { s in
            guard targets.contains(s.id), s.groupId != nil else { return s }
            var c = s; c.groupId = nil; return c
        }
    }

    // MARK: Dönüşümler

    static func translate(_ strokes: [Stroke], _ ids: Set<String>, _ dx: Float, _ dy: Float) -> [Stroke] {
        if dx == 0 && dy == 0 { return strokes }
        return transformPoints(strokes, ids) { $0.moved($0.x + dx, $0.y + dy) }
    }

    /// [degrees] saat yönünde [pivot] etrafında döndürür.
    static func rotate(_ strokes: [Stroke], _ ids: Set<String>, _ degrees: Float, pivot: P) -> [Stroke] {
        if degrees.truncatingRemainder(dividingBy: 360) == 0 { return strokes }
        let radians = Double(degrees) * .pi / 180
        let c = Float(cos(radians)), s = Float(sin(radians))
        return transformPoints(strokes, ids) {
            let x = $0.x - pivot.x, y = $0.y - pivot.y
            return $0.moved(pivot.x + x * c - y * s, pivot.y + x * s + y * c)
        }
    }

    /// [pivot] sabit kalacak şekilde ölçekler; fırça kalınlığı da orantılı değişir.
    static func scale(_ strokes: [Stroke], _ ids: Set<String>, _ sx: Float, _ sy: Float, pivot: P) -> [Stroke] {
        let fx = sx.coerceIn(MIN_SCALE, MAX_SCALE), fy = sy.coerceIn(MIN_SCALE, MAX_SCALE)
        if fx == 1 && fy == 1 { return strokes }
        let widthFactor = sqrtf(abs(fx * fy))
        return strokes.map { stroke in
            guard ids.contains(stroke.id) else { return stroke }
            var c = stroke
            c.points = stroke.points.map { $0.moved(pivot.x + ($0.x - pivot.x) * fx, pivot.y + ($0.y - pivot.y) * fy) }
            c.clip = stroke.clip?.map { point(pivot.x + ($0.x - pivot.x) * fx, pivot.y + ($0.y - pivot.y) * fy) }
            if stroke.brushType != .IMAGE { c.sizePx = (stroke.sizePx * widthFactor).coerceIn(0.5, 2000) }
            if var spec = stroke.shape {
                spec.style.strokeWidth = (spec.style.strokeWidth * widthFactor).coerceIn(0.2, 400)
                spec.style.cornerRadius = spec.style.cornerRadius * widthFactor
                c.shape = spec
            }
            return c
        }
    }

    /// v30 verilen çizimleri gizler ya da gösterir.
    static func setHidden(_ strokes: [Stroke], _ ids: Set<String>, _ hidden: Bool) -> [Stroke] {
        if ids.isEmpty || !strokes.contains(where: { ids.contains($0.id) && $0.hidden != hidden }) { return strokes }
        return strokes.map { s in
            guard ids.contains(s.id), s.hidden != hidden else { return s }
            var c = s; c.hidden = hidden; return c
        }
    }

    /// Renk verir; silgi ve resim atlanır.
    static func recolor(_ strokes: [Stroke], _ ids: Set<String>, _ colorHex: String) -> [Stroke] {
        strokes.map { s in
            if !ids.contains(s.id) || s.brushType == .ERASER || s.brushType == .IMAGE { return s }
            var c = s
            if var shape = s.shape {
                c.colorHex = colorHex
                shape.style.strokeColorHex = colorHex
                shape.subpaths = shape.subpaths.map { p in
                    guard p.strokeColorHex != nil else { return p }
                    var q = p; q.strokeColorHex = nil; return q
                }
                c.shape = shape
            } else if var diagram = s.diagram {
                c.colorHex = colorHex
                diagram.style.strokeColorHex = colorHex
                diagram.nodes = diagram.nodes.map { n in
                    if n.strokeColorHex == nil && n.textColorHex == nil { return n }
                    var m = n; m.strokeColorHex = nil; m.textColorHex = nil; return m
                }
                diagram.edges = diagram.edges.map { e in
                    guard e.colorHex != nil else { return e }
                    var f = e; f.colorHex = nil; return f
                }
                c.diagram = diagram
            } else if s.brushType == .TEXT, var text = s.text, var box = text.box, var fx = box.effects, fx.gradient != nil {
                c.colorHex = colorHex
                fx.gradient = nil
                box.effects = fx.isEmpty ? nil : fx
                text.box = box.orNull()
                c.text = text
            } else if s.colorHex.caseInsensitiveCompare(colorHex) == .orderedSame {
                return s
            } else {
                c.colorHex = colorHex
            }
            return c
        }
    }

    /// v27 gradyan dolgu: şekilde dolgu, yazıda harflerin dolgusu; nil ise gradyan kaldırılır.
    static func fillGradient(_ strokes: [Stroke], _ ids: Set<String>, _ gradient: GradientSpec?) -> [Stroke] {
        strokes.map { s in
            if !ids.contains(s.id) || !canFill(s) { return s }
            if var shape = s.shape, ShapeRenderer.isFillable(s) {
                if shape.style.fillGradient == gradient { return s }
                if let g = gradient { shape.style.fillColorHex = String(format: "#%06X", Gradients.average(g) & 0xFFFFFF) }
                shape.style.fillGradient = gradient
                var c = s; c.shape = shape; return c
            }
            if s.brushType == .TEXT, var text = s.text {
                var box = text.box ?? TextBox()
                if box.effects?.gradient == gradient { return s }
                var fx = box.effects ?? TextEffects()
                fx.gradient = gradient
                box.effects = fx.isEmpty ? nil : fx
                text.box = box.orNull()
                var c = s; c.text = text; return c
            }
            return s
        }
    }

    static func fillGradientOf(_ stroke: Stroke) -> GradientSpec? {
        if stroke.shape != nil { return stroke.shape?.style.fillGradient }
        if stroke.brushType == .TEXT { return stroke.text?.box?.effects?.gradient }
        return nil
    }

    static func canFill(_ stroke: Stroke) -> Bool { stroke.brushType != .ERASER && stroke.brushType != .IMAGE }

    /// Elemanın dolgu rengi.
    static func fillColorOf(_ stroke: Stroke) -> String? {
        if !canFill(stroke) { return nil }
        if let shape = stroke.shape {
            return ShapeRenderer.isFillable(stroke) ? shape.style.fillColorHex : shape.style.strokeColorHex
        }
        if let d = stroke.diagram { return d.style.fillColorHex }
        return stroke.colorHex
    }

    /// Seçili elemanların dolgu rengini verir.
    static func fill(_ strokes: [Stroke], _ ids: Set<String>, _ colorHex: String) -> [Stroke] {
        strokes.map { s in
            if !ids.contains(s.id) || !canFill(s) { return s }
            var c = s
            if var shape = s.shape, ShapeRenderer.isFillable(s) {
                let same = (shape.style.fillColorHex?.caseInsensitiveCompare(colorHex) == .orderedSame) && shape.style.fillGradient == nil
                    && !shape.subpaths.contains { p in p.fillColorHex.map { !keepsFill($0) } ?? false }
                if same { return s }
                shape.style.fillColorHex = colorHex
                shape.style.fillGradient = nil
                shape.subpaths = shape.subpaths.map { p in
                    guard let f = p.fillColorHex, !keepsFill(f) else { return p }
                    var q = p; q.fillColorHex = nil; return q
                }
                c.shape = shape
            } else if var shape = s.shape {
                if shape.style.strokeColorHex.caseInsensitiveCompare(colorHex) == .orderedSame { return s }
                c.colorHex = colorHex
                shape.style.strokeColorHex = colorHex
                c.shape = shape
            } else if var diagram = s.diagram {
                if diagram.style.fillColorHex?.caseInsensitiveCompare(colorHex) == .orderedSame && !diagram.nodes.contains(where: { $0.fillColorHex != nil }) { return s }
                diagram.style.fillColorHex = colorHex
                diagram.nodes = diagram.nodes.map { n in
                    guard n.fillColorHex != nil else { return n }
                    var m = n; m.fillColorHex = nil; return m
                }
                c.diagram = diagram
            } else if s.colorHex.caseInsensitiveCompare(colorHex) == .orderedSame {
                return s
            } else {
                c.colorHex = colorHex
            }
            return c
        }
    }

    /// Silginin değdiği nesne elemanları (bilgi amaçlı).
    static func objectsHitBy(_ strokes: [Stroke], _ eraser: Stroke) -> Set<String> {
        if eraser.brushType != .ERASER || eraser.points.isEmpty { return [] }
        let half = halfWidth(eraser)
        let samples = densePoints(eraser.points, max(2, half * 0.6))
        var out = Set<String>()
        for target in strokes where target.brushType.isObject {
            guard let box = bounds(target)?.inflate(half + 1) else { continue }
            if samples.contains(where: { box.contains($0) && distanceToShape($0, target) <= half + halfWidth(target) }) { out.insert(target.id) }
        }
        return out
    }

    static func delete(_ strokes: [Stroke], _ ids: Set<String>) -> [Stroke] {
        reorder(strokes.filter { !ids.contains($0.id) })
    }

    static func reorder(_ strokes: [Stroke]) -> [Stroke] {
        strokes.enumerated().map { order, s in
            if s.order == order { return s }
            var c = s; c.order = order; return c
        }
    }

    fileprivate static func point(_ x: Float, _ y: Float) -> StrokePoint { StrokePoint(ContentCodec.quantize(x), ContentCodec.quantize(y)) }

    // MARK: Tamamen silinenlerin temizliği

    /// Sonradan çizilen silgilerin tamamen örttüğü çizimleri, ardından hiçbir şeye değmeyen silgileri çıkarır.
    static func pruneErased(_ strokes: [Stroke], focus: FRect? = nil) -> [Stroke] {
        let erasers = strokes.enumerated().filter { $0.element.brushType == .ERASER && !$0.element.points.isEmpty }
        if erasers.isEmpty { return strokes }
        var eraserBounds = [String: FRect]()
        for e in erasers { eraserBounds[e.element.id] = fullBounds(e.element)! }

        var removed = Set<String>()
        for (position, stroke) in strokes.enumerated() {
            if stroke.brushType == .ERASER || stroke.points.isEmpty { continue }
            guard let box = bounds(stroke) else { continue }
            if let f = focus, !f.overlaps(box) { continue }
            let later = erasers.filter {
                $0.offset > position && $0.element.flow >= 0.99 && $0.element.opacity >= 0.99 && eraserBounds[$0.element.id]!.overlaps(box)
            }.map { $0.element }
            if later.isEmpty { continue }
            let union = later.map { eraserBounds[$0.id]! }.reduce(eraserBounds[later[0].id]!) { $0.union($1) }
            if union.left > box.left || union.top > box.top || union.right < box.right || union.bottom < box.bottom { continue }
            var covered = true
            coverageSamples(stroke) { sample in
                let ok = later.contains { eraser in
                    eraserBounds[eraser.id]!.contains(sample) && distanceToPolyline(sample, eraser.points) <= halfWidth(eraser) - COVER_MARGIN
                }
                if !ok { covered = false }
                return ok
            }
            if covered { removed.insert(stroke.id) }
        }

        let remaining = strokes.filter { !removed.contains($0.id) }
        var visibleBefore: [Stroke] = []
        var kept: [Stroke] = []
        kept.reserveCapacity(remaining.count)
        let gcArea: FRect? = focus.map { f in
            removed.compactMap { id in strokes.first { $0.id == id }.flatMap { bounds($0) } }.reduce(f) { $0.union($1) }
        }
        for stroke in remaining {
            if stroke.brushType != .ERASER {
                visibleBefore.append(stroke); kept.append(stroke)
            } else if let area = gcArea, bounds(stroke)?.overlaps(area) != true {
                kept.append(stroke)
            } else if !stroke.points.isEmpty && visibleBefore.contains(where: { eraserTouches(stroke, $0) }) {
                kept.append(stroke)
            }
        }
        if kept.count == strokes.count { return strokes }
        return reorder(kept)
    }

    private static func eraserTouches(_ eraser: Stroke, _ target: Stroke) -> Bool {
        guard let eb = bounds(eraser), let tb = bounds(target), eb.overlaps(tb) else { return false }
        if target.brushType.isQuad {
            let half = halfWidth(eraser)
            return densePoints(eraser.points, half).contains { distanceToShape($0, target) <= half }
        }
        return StrokeShape(eraser, 0).touches(StrokeShape(target, 0))
    }

    /// Örtme örnekleri; [visit] false dönerse durur.
    private static func coverageSamples(_ stroke: Stroke, _ visit: (P) -> Bool) {
        let pts = stroke.points
        if stroke.brushType.isQuad {
            if pts.count < 4 { return }
            let n = 16
            for i in 0...n { for j in 0...n {
                let u = Float(i) / Float(n), v = Float(j) / Float(n)
                let top = lerp(pts[0], pts[1], u), bottom = lerp(pts[3], pts[2], u)
                if !visit(P(top.x + (bottom.x - top.x) * v, top.y + (bottom.y - top.y) * v)) { return }
            } }
            return
        }
        let half = halfWidth(stroke)
        let r = half * stroke.brushType.style.reach
        let twoPi = 2 * Float.pi
        if pts.count == 1 {
            let c = P(pts[0].x, pts[0].y)
            if !visit(c) { return }
            for k in 0..<16 {
                let a = Float(k) / 16 * twoPi
                if !visit(P(c.x + cosf(a) * r, c.y + sinf(a) * r)) { return }
                if !visit(P(c.x + cosf(a) * r * 0.5, c.y + sinf(a) * r * 0.5)) { return }
            }
            return
        }
        let step = (half * 0.5).coerceIn(0.5, 4)
        let offsets: [Float] = [0, -1, 1, -0.5, 0.5]
        for i in 1..<pts.count {
            let a = pts[i - 1], b = pts[i]
            let length = hypotf(b.x - a.x, b.y - a.y)
            let dx: Float = length == 0 ? 1 : (b.x - a.x) / length
            let dy: Float = length == 0 ? 0 : (b.y - a.y) / length
            var t: Float = 0
            while true {
                let px = a.x + dx * t, py = a.y + dy * t
                for k in offsets { if !visit(P(px - dy * k * r, py + dx * k * r)) { return } }
                if t >= length { break }
                t = min(length, t + step)
            }
            for k in 0..<12 {
                let ang = Float(k) / 12 * twoPi
                if !visit(P(a.x + cosf(ang) * r, a.y + sinf(ang) * r)) { return }
            }
            if i == 1 { for k: Float in [-1, 0, 1] { if !visit(P(a.x - dx * r - dy * k * r, a.y - dy * r + dx * k * r)) { return } } }
            if i == pts.count - 1 {
                for k in 0..<12 {
                    let ang = Float(k) / 12 * twoPi
                    if !visit(P(b.x + cosf(ang) * r, b.y + sinf(ang) * r)) { return }
                }
                for k: Float in [-1, 0, 1] { if !visit(P(b.x + dx * r - dy * k * r, b.y + dy * r + dx * k * r)) { return } }
            }
        }
    }

    private static func lerp(_ a: StrokePoint, _ b: StrokePoint, _ t: Float) -> P { P(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t) }

    static func densePoints(_ points: [StrokePoint], _ spacing: Float) -> [P] {
        if points.count == 1 { return [P(points[0].x, points[0].y)] }
        var out: [P] = []
        let step = max(spacing, 1)
        for i in 1..<points.count {
            let a = points[i - 1], b = points[i]
            let length = hypotf(b.x - a.x, b.y - a.y)
            var t: Float = 0
            while t <= length {
                let f: Float = length == 0 ? 0 : t / length
                out.append(P(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f))
                t += step
            }
        }
        if let l = points.last { out.append(P(l.x, l.y)) }
        return out
    }

    /// Noktanın çizime (resimde dörtgenin içi 0) uzaklığı.
    static func distanceToShape(_ p: P, _ stroke: Stroke) -> Float {
        if stroke.brushType == .SHAPE { return ShapeRenderer.distanceTo(stroke, p) }
        if !stroke.brushType.isQuad || stroke.points.count < 4 { return distanceToPolyline(p, stroke.points) }
        let q = stroke.points
        if insideQuad(p, q) { return 0 }
        var best = Float.greatestFiniteMagnitude
        for i in 0..<4 {
            let a = q[i], b = q[(i + 1) % 4]
            best = min(best, pointSegmentDistance(p.x, p.y, a.x, a.y, b.x, b.y))
        }
        return best
    }

    static func insideQuad(_ p: P, _ q: [StrokePoint]) -> Bool {
        var positive = false, negative = false
        for i in 0..<4 {
            let a = q[i], b = q[(i + 1) % 4]
            let c = cross(a.x, a.y, b.x, b.y, p.x, p.y)
            if c > 0 { positive = true }
            if c < 0 { negative = true }
        }
        return !(positive && negative)
    }

    private static func transformPoints(_ strokes: [Stroke], _ ids: Set<String>, _ f: (StrokePoint) -> StrokePoint) -> [Stroke] {
        strokes.map { s in
            guard ids.contains(s.id) else { return s }
            var c = s
            c.points = s.points.map(f)
            c.clip = s.clip?.map(f)
            return c
        }
    }

    // MARK: Geometri

    static func halfWidth(_ stroke: Stroke) -> Float {
        stroke.brushType == .SHAPE ? ShapeRenderer.halfWidth(stroke) : stroke.brushType.inkWidth(stroke.sizePx, strength: stroke.strength) / 2
    }

    /// Elemanın sınırları (fırça kalınlığı dahil; kırpılmışsa yalnız görünen kısım).
    static func bounds(_ stroke: Stroke) -> FRect? {
        guard let full = fullBounds(stroke) else { return nil }
        guard let clip = stroke.clip, clip.count == 4 else { return full }
        let c = FRect(clip.map(\.x).min()!, clip.map(\.y).min()!, clip.map(\.x).max()!, clip.map(\.y).max()!)
        if !full.overlaps(c) { return nil }
        return full.intersect(c)
    }

    static func fullBounds(_ stroke: Stroke) -> FRect? {
        if stroke.points.isEmpty { return nil }
        let half: Float = stroke.brushType.isQuad ? 0 : halfWidth(stroke)
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude
        var r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for p in stroke.points { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
        return FRect(l - half, t - half, r + half, b + half)
    }

    static func insideClip(_ stroke: Stroke, _ p: P) -> Bool {
        guard let clip = stroke.clip, clip.count == 4 else { return true }
        return insideQuad(p, clip)
    }

    static func bounds(_ strokes: [Stroke]) -> FRect? {
        strokes.compactMap { bounds($0) }.reduce(nil as FRect?) { acc, b in acc.map { $0.union(b) } ?? b }
    }

    static func selectionBounds(_ strokes: [Stroke], _ ids: Set<String>) -> FRect? {
        bounds(strokes.filter { ids.contains($0.id) && $0.brushType != .ERASER })
    }

    static func distanceToPolyline(_ p: P, _ points: [StrokePoint]) -> Float {
        if points.isEmpty { return .greatestFiniteMagnitude }
        if points.count == 1 { return hypotf(p.x - points[0].x, p.y - points[0].y) }
        var best = Float.greatestFiniteMagnitude
        for i in 1..<points.count {
            best = min(best, pointSegmentDistance(p.x, p.y, points[i - 1].x, points[i - 1].y, points[i].x, points[i].y))
        }
        return best
    }

    static func pointSegmentDistance(_ px: Float, _ py: Float, _ ax: Float, _ ay: Float, _ bx: Float, _ by: Float) -> Float {
        let dx = bx - ax, dy = by - ay
        let lengthSq = dx * dx + dy * dy
        let t: Float = lengthSq == 0 ? 0 : (((px - ax) * dx + (py - ay) * dy) / lengthSq).coerceIn(0, 1)
        return hypotf(px - (ax + t * dx), py - (ay + t * dy))
    }

    fileprivate static func segmentDistance(_ ax: Float, _ ay: Float, _ bx: Float, _ by: Float, _ cx: Float, _ cy: Float, _ dx: Float, _ dy: Float) -> Float {
        if segmentsIntersect(ax, ay, bx, by, cx, cy, dx, dy) { return 0 }
        return min(min(pointSegmentDistance(ax, ay, cx, cy, dx, dy), pointSegmentDistance(bx, by, cx, cy, dx, dy)),
                   min(pointSegmentDistance(cx, cy, ax, ay, bx, by), pointSegmentDistance(dx, dy, ax, ay, bx, by)))
    }

    private static func segmentsIntersect(_ ax: Float, _ ay: Float, _ bx: Float, _ by: Float, _ cx: Float, _ cy: Float, _ dx: Float, _ dy: Float) -> Bool {
        let d1 = cross(cx, cy, dx, dy, ax, ay), d2 = cross(cx, cy, dx, dy, bx, by)
        let d3 = cross(ax, ay, bx, by, cx, cy), d4 = cross(ax, ay, bx, by, dx, dy)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    static func cross(_ ax: Float, _ ay: Float, _ bx: Float, _ by: Float, _ px: Float, _ py: Float) -> Float {
        (bx - ax) * (py - ay) - (by - ay) * (px - ax)
    }

    /// Değme testini hızlandırmak için çizimi küçük parçalara böler.
    final class StrokeShape {
        let stroke: Stroke
        let position: Int
        let isImage: Bool
        let layer: String
        let half: Float
        fileprivate let xs: [Float]
        fileprivate let ys: [Float]
        let expandedBounds: FRect
        fileprivate let chunks: [ClosedRange<Int>]
        let chunkBounds: [FRect]

        init(_ stroke: Stroke, _ position: Int) {
            self.stroke = stroke
            self.position = position
            isImage = stroke.brushType.isObject
            layer = (stroke.layerId ?? "") + (stroke.hidden ? "\u{0}gizli" : "")
            half = isImage ? 0 : ElementOps.halfWidth(stroke)
            xs = stroke.points.map(\.x)
            ys = stroke.points.map(\.y)
            expandedBounds = (ElementOps.fullBounds(stroke) ?? .zero).inflate(TOUCH_EPSILON)
            let n = xs.count
            var ranges: [ClosedRange<Int>] = []
            if n == 1 { ranges.append(0...0) } else {
                var start = 0
                while start < n - 1 {
                    let end = min(start + CHUNK, n - 1)
                    ranges.append(start...end)
                    start = end
                }
            }
            chunks = ranges
            let h = half, xs = xs, ys = ys
            chunkBounds = ranges.map { r in
                var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude
                var rr = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
                for i in r { l = min(l, xs[i]); t = min(t, ys[i]); rr = max(rr, xs[i]); b = max(b, ys[i]) }
                return FRect(l - h, t - h, rr + h, b + h)
            }
        }

        func touches(_ other: StrokeShape) -> Bool {
            if !expandedBounds.overlaps(other.expandedBounds) { return false }
            let limit = half + other.half + TOUCH_EPSILON
            for a in chunks.indices {
                let boxA = chunkBounds[a].inflate(TOUCH_EPSILON)
                for b in other.chunks.indices {
                    if !boxA.overlaps(other.chunkBounds[b]) { continue }
                    if chunkDistance(chunks[a], other, other.chunks[b]) <= limit { return true }
                }
            }
            return false
        }

        private func chunkDistance(_ range: ClosedRange<Int>, _ other: StrokeShape, _ otherRange: ClosedRange<Int>) -> Float {
            var best = Float.greatestFiniteMagnitude
            let singleA = range.lowerBound == range.upperBound
            let singleB = otherRange.lowerBound == otherRange.upperBound
            if singleA && singleB {
                return hypotf(xs[range.lowerBound] - other.xs[otherRange.lowerBound], ys[range.lowerBound] - other.ys[otherRange.lowerBound])
            }
            if singleA {
                for j in otherRange.lowerBound..<otherRange.upperBound {
                    best = min(best, pointSegmentDistance(xs[range.lowerBound], ys[range.lowerBound], other.xs[j], other.ys[j], other.xs[j + 1], other.ys[j + 1]))
                }
                return best
            }
            if singleB {
                for i in range.lowerBound..<range.upperBound {
                    best = min(best, pointSegmentDistance(other.xs[otherRange.lowerBound], other.ys[otherRange.lowerBound], xs[i], ys[i], xs[i + 1], ys[i + 1]))
                }
                return best
            }
            for i in range.lowerBound..<range.upperBound {
                for j in otherRange.lowerBound..<otherRange.upperBound {
                    best = min(best, segmentDistance(xs[i], ys[i], xs[i + 1], ys[i + 1], other.xs[j], other.ys[j], other.xs[j + 1], other.ys[j + 1]))
                    if best == 0 { return 0 }
                }
            }
            return best
        }

        /// İki çizimin mürekkeplerinin değdiği yerler: (x, y, r) üçlüleri.
        func contacts(_ other: StrokeShape, maxContacts: Int = 128) -> [Float] {
            if !expandedBounds.overlaps(other.expandedBounds) { return [] }
            let limit = half + other.half + TOUCH_EPSILON
            let step = max(0.5, max(min(half, other.half), 0.5))
            let out = ContactList(step)
            var nearestX: Float = 0, nearestY: Float = 0, nearest = Float.greatestFiniteMagnitude
            outer: for a in chunks.indices {
                let boxA = chunkBounds[a].inflate(TOUCH_EPSILON)
                for b in other.chunks.indices {
                    if !boxA.overlaps(other.chunkBounds[b]) { continue }
                    if chunkDistance(chunks[a], other, other.chunks[b]) > limit { continue }
                    let best = collect(chunks[a], other, other.chunks[b], limit, step, out)
                    if best[2] < nearest { nearest = best[2]; nearestX = best[0]; nearestY = best[1] }
                    if out.size >= maxContacts * 4 { break }
                }
            }
            if out.size == 0 && nearest <= limit + step { out.add(nearestX, nearestY, 0.5) }
            return out.toArray(maxContacts)
        }

        private func collect(_ range: ClosedRange<Int>, _ other: StrokeShape, _ otherRange: ClosedRange<Int>, _ limit: Float, _ step: Float, _ out: ContactList) -> [Float] {
            let maxR = max(min(half, other.half), 0.5)
            var nearest: [Float] = [0, 0, .greatestFiniteMagnitude]
            let aFirst = range.lowerBound, aLast = range.lowerBound == range.upperBound ? range.lowerBound + 1 : range.upperBound
            let bFirst = otherRange.lowerBound, bLast = otherRange.lowerBound == otherRange.upperBound ? otherRange.lowerBound + 1 : otherRange.upperBound
            let xs = self.xs, ys = self.ys, oxs = other.xs, oys = other.ys
            func ax(_ k: Int) -> Float { xs[min(k, xs.count - 1)] }
            func ay(_ k: Int) -> Float { ys[min(k, ys.count - 1)] }
            func bx(_ k: Int) -> Float { oxs[min(k, oxs.count - 1)] }
            func by(_ k: Int) -> Float { oys[min(k, oys.count - 1)] }
            func probe(_ px: Float, _ py: Float, _ againstOther: Bool) {
                var best = Float.greatestFiniteMagnitude, qx: Float = 0, qy: Float = 0
                let first = againstOther ? bFirst : aFirst, last = againstOther ? bLast : aLast
                for k in first..<last {
                    let x0 = againstOther ? bx(k) : ax(k), y0 = againstOther ? by(k) : ay(k)
                    let x1 = againstOther ? bx(k + 1) : ax(k + 1), y1 = againstOther ? by(k + 1) : ay(k + 1)
                    let dx = x1 - x0, dy = y1 - y0
                    let l2 = dx * dx + dy * dy
                    let t: Float = l2 == 0 ? 0 : (((px - x0) * dx + (py - y0) * dy) / l2).coerceIn(0, 1)
                    let cx = x0 + t * dx, cy = y0 + t * dy
                    let ddx = px - cx, ddy = py - cy
                    let d = sqrtf(ddx * ddx + ddy * ddy)
                    if d < best { best = d; qx = cx; qy = cy }
                }
                let mx = (px + qx) / 2, my = (py + qy) / 2
                if best < nearest[2] { nearest[0] = mx; nearest[1] = my; nearest[2] = best }
                if best <= limit { out.add(mx, my, ((limit - best) / 2).coerceIn(0.5, maxR)) }
            }
            for k in aFirst..<aLast {
                let x0 = ax(k), y0 = ay(k), x1 = ax(k + 1), y1 = ay(k + 1)
                let length = hypotf(x1 - x0, y1 - y0)
                let n = max(1, Int((length / step).rounded(.up)))
                for q in 0...n {
                    let f = Float(q) / Float(n)
                    probe(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, true)
                }
            }
            for k in bFirst...bLast { probe(bx(k), by(k), false) }
            return nearest
        }

        /// Bu çizimin (silginin) kaç noktası [target] çiziminin mürekkebine değiyor.
        func pointsTouching(_ target: StrokeShape) -> Int {
            let limit = half + target.half
            var count = 0
            for i in xs.indices where ElementOps.distanceToShape(P(xs[i], ys[i]), target.stroke) <= limit { count += 1 }
            return count
        }
    }

    /// Değme noktaları; aynı küçük hücreye düşenler tekilleştirilir.
    fileprivate final class ContactList {
        private let cell: Float
        private var data: [Float] = []
        private(set) var size = 0
        private var seen = Set<Int64>()
        init(_ cell: Float) { self.cell = cell; data.reserveCapacity(48) }
        func add(_ x: Float, _ y: Float, _ r: Float) {
            let key = (Int64(floorf(x / cell)) << 32) | (Int64(floorf(y / cell)) & 0xFFFF_FFFF)
            if !seen.insert(key).inserted { return }
            data.append(x); data.append(y); data.append(r)
            size += 3
        }
        func toArray(_ max: Int) -> [Float] {
            let count = size / 3
            if count <= max { return data }
            var out = [Float](repeating: 0, count: max * 3)
            for i in 0..<max {
                let src = Int(Int64(i) * Int64(count - 1) / Int64(Swift.max(max - 1, 1)))
                out[i * 3] = data[src * 3]; out[i * 3 + 1] = data[src * 3 + 1]; out[i * 3 + 2] = data[src * 3 + 2]
            }
            return out
        }
    }
}

private extension StrokePoint {
    /// v18: kalem basıncı ve eğimi korunarak yeni yer.
    func moved(_ x: Float, _ y: Float) -> StrokePoint { at(ContentCodec.quantize(x), ContentCodec.quantize(y)) }
}

/// v21 renge göre değişen özel dolgular boyamayla silinmez.
private func keepsFill(_ hex: String) -> Bool { ShapeRenderer.isShade(hex) || hex == ShapeRenderer.NO_FILL }

/// Eleman dizinini artımlı hesaplar (Android `ElementIndexer`). Kilitle korunur; arka planda çalıştırılabilir.
final class ElementIndexer: @unchecked Sendable {
    private static let CELL: Float = 96
    private let lock = NSLock()
    private var strokes: [Stroke] = []
    private var index = ElementIndex.empty
    private var shapes: [ElementOps.StrokeShape] = []
    private var parent = [Int](repeating: 0, count: 64)
    private var grid = [Int64: [Int]]()
    private var eraserOwner = [String: Int]()
    private var eraserIds: [String] = []
    private var fullErasers: [ElementOps.StrokeShape] = []
    private var eraserGrid = [Int64: [Int]]()
    private var pairConnected = [Int64: Bool]()
    private var rebuildMembers = Set<Int>()
    private var replaying = false
    private var rebuildAll = false
    private var needsRebuild = false

    func update(_ newStrokes: [Stroke]) -> ElementIndex {
        lock.lock(); defer { lock.unlock() }
        if newStrokes == strokes { return index }
        let appendOnly = newStrokes.count >= strokes.count && strokes.indices.allSatisfy { newStrokes[$0] == strokes[$0] }
        if !appendOnly { reset() }
        replaying = !appendOnly || strokes.isEmpty
        for position in strokes.count..<newStrokes.count { add(newStrokes[position], position) }
        replaying = false
        if needsRebuild {
            let roots = rebuildAll ? Set(shapes.indices.map { find($0) }) : Set(rebuildMembers.map { find($0) })
            rebuildWithErasers(roots)
            needsRebuild = false; rebuildAll = false
            rebuildMembers.removeAll()
        }
        strokes = newStrokes
        index = build()
        return index
    }

    private func reset() {
        strokes = []
        shapes.removeAll(); grid.removeAll(); eraserOwner.removeAll(); eraserIds.removeAll()
        fullErasers.removeAll(); eraserGrid.removeAll(); pairConnected.removeAll()
        needsRebuild = false; rebuildAll = false; rebuildMembers.removeAll()
    }

    private func find(_ i: Int) -> Int {
        var x = i
        while parent[x] != x { parent[x] = parent[parent[x]]; x = parent[x] }
        return x
    }

    private func cellsOf(_ rect: FRect, _ out: inout Set<Int64>) {
        let x0 = Int(floorf(rect.left / Self.CELL)), x1 = Int(floorf(rect.right / Self.CELL))
        let y0 = Int(floorf(rect.top / Self.CELL)), y1 = Int(floorf(rect.bottom / Self.CELL))
        if Int64(x1 - x0 + 1) * Int64(y1 - y0 + 1) > 4096 { out.insert(Int64.min); return }
        for cx in x0...x1 { for cy in y0...y1 { out.insert((Int64(cx) << 32) | (Int64(cy) & 0xFFFF_FFFF)) } }
    }

    private func candidates(_ shape: ElementOps.StrokeShape) -> Set<Int> {
        var cells = Set<Int64>()
        for b in shape.chunkBounds { cellsOf(b.inflate(1), &cells) }
        if cells.contains(Int64.min) { return Set(shapes.indices) }
        var result = Set<Int>()
        for c in cells { if let l = grid[c] { result.formUnion(l) } }
        if let l = grid[Int64.min] { result.formUnion(l) }
        return result
    }

    private func add(_ stroke: Stroke, _ position: Int) {
        if stroke.points.isEmpty { return }
        let shape = ElementOps.StrokeShape(stroke, position)
        if stroke.brushType == .ERASER {
            var tally = [Int: Int]()
            for s in candidates(shape).sorted() {
                let other = shapes[s]
                if !other.isImage && other.layer == shape.layer && other.expandedBounds.overlaps(shape.expandedBounds) {
                    let touching = shape.pointsTouching(other)
                    if touching > 0 { let k = find(s); tally[k, default: 0] += touching }
                }
            }
            // Kotlin maxByOrNull: eşitlikte ilk görülen (HashMap sırası) — burada en küçük kök
            if let best = tally.sorted(by: { $0.key < $1.key }).max(by: { $0.value < $1.value }) { eraserOwner[stroke.id] = best.key }
            eraserIds.append(stroke.id)
            if stroke.flow >= 0.99 && stroke.opacity >= 0.99 {
                let e = fullErasers.count
                fullErasers.append(shape)
                var cells = Set<Int64>()
                for b in shape.chunkBounds { cellsOf(b.inflate(1), &cells) }
                for c in cells { eraserGrid[c, default: []].append(e) }
                if replaying {
                    needsRebuild = true; rebuildAll = true
                } else {
                    let cut = cutMembers(shape)
                    if !cut.isEmpty { needsRebuild = true; rebuildMembers.formUnion(cut) }
                }
            }
            return
        }
        let i = shapes.count
        if i >= parent.count { parent += [Int](repeating: 0, count: parent.count) }
        parent[i] = i
        shapes.append(shape)
        if !shape.isImage {
            for j in candidates(shape).sorted() {
                let other = shapes[j]
                if j != i && !other.isImage && other.layer == shape.layer && find(i) != find(j) && shape.touches(other) { parent[find(j)] = find(i) }
            }
        }
        var cells = Set<Int64>()
        for b in shape.chunkBounds { cellsOf(b, &cells) }
        for c in cells { grid[c, default: []].append(i) }
    }

    private func survive(_ contacts: [Float], _ erasers: [ElementOps.StrokeShape]) -> Bool {
        var k = 0
        while k < contacts.count {
            let p = P(contacts[k], contacts[k + 1]), r = contacts[k + 2]
            if !erasers.contains(where: { ElementOps.distanceToPolyline(p, $0.stroke.points) + r <= $0.half }) { return true }
            k += 3
        }
        return false
    }

    private func blockers(_ a: ElementOps.StrokeShape, _ b: ElementOps.StrokeShape, _ upTo: Int) -> [ElementOps.StrokeShape] {
        if fullErasers.isEmpty { return [] }
        let after = max(a.position, b.position)
        let region = a.expandedBounds.intersect(b.expandedBounds)
        if region.width < 0 || region.height < 0 { return [] }
        var cells = Set<Int64>()
        cellsOf(region.inflate(1), &cells)
        var found = Set<Int>()
        if cells.contains(Int64.min) { found.formUnion(fullErasers.indices) } else { for c in cells { if let l = eraserGrid[c] { found.formUnion(l) } } }
        if let l = eraserGrid[Int64.min] { found.formUnion(l) }
        return found.sorted().map { fullErasers[$0] }.filter { $0.position > after && $0.position <= upTo && $0.expandedBounds.overlaps(region) }
    }

    private func pairKey(_ i: Int, _ j: Int) -> Int64 { (Int64(min(i, j)) << 32) | Int64(max(i, j)) }

    private func connected(_ i: Int, _ j: Int) -> Bool {
        let key = pairKey(i, j)
        if let v = pairConnected[key] { return v }
        let v = computeConnected(shapes[min(i, j)], shapes[max(i, j)])
        pairConnected[key] = v
        return v
    }

    private func computeConnected(_ a: ElementOps.StrokeShape, _ b: ElementOps.StrokeShape) -> Bool {
        let bl = blockers(a, b, Int.max)
        if bl.isEmpty { return a.touches(b) }
        if !a.touches(b) { return false }
        let contacts = a.contacts(b)
        return !contacts.isEmpty && survive(contacts, bl)
    }

    private func cutMembers(_ eraser: ElementOps.StrokeShape) -> Set<Int> {
        let under = candidates(eraser).filter {
            !shapes[$0].isImage && shapes[$0].layer == eraser.layer && shapes[$0].expandedBounds.overlaps(eraser.expandedBounds)
        }.sorted()
        var roots = Set<Int>()
        for x in under.indices {
            for y in (x + 1)..<max(x + 1, under.count) {
                let i = under[x], j = under[y]
                if find(i) != find(j) { continue }
                let a = shapes[i], b = shapes[j]
                if !a.expandedBounds.overlaps(b.expandedBounds) || !a.touches(b) { continue }
                let key = pairKey(i, j)
                if pairConnected[key] == false { continue }
                let all = blockers(a, b, eraser.position)
                if !all.contains(where: { $0 === eraser }) { continue }
                let contacts = a.contacts(b)
                if contacts.isEmpty { pairConnected[key] = false; roots.insert(i); continue }
                let earlier = all.filter { $0 !== eraser }
                if !survive(contacts, earlier) { pairConnected[key] = false; continue }
                let still = survive(contacts, all)
                pairConnected[key] = still
                if !still { roots.insert(i) }
            }
        }
        return roots
    }

    private func rebuildWithErasers(_ roots: Set<Int>) {
        let members = shapes.indices.filter { roots.contains(find($0)) }
        let memberSet = Set(members)
        for i in members { parent[i] = i }
        for i in members {
            let shape = shapes[i]
            if shape.isImage { continue }
            for j in candidates(shape).sorted() {
                if j >= i || !memberSet.contains(j) { continue }
                let other = shapes[j]
                if other.isImage || other.layer != shape.layer || find(i) == find(j) { continue }
                if connected(i, j) { parent[find(j)] = find(i) }
            }
        }
    }

    private func build() -> ElementIndex {
        if shapes.isEmpty { return ElementIndex(strokes, [], [:]) }
        var order: [Int] = []
        var members = [Int: [Int]]()
        for i in shapes.indices {
            let r = find(i)
            if members[r] == nil { order.append(r) }
            members[r, default: []].append(i)
        }
        let components = order.map { members[$0]! }.sorted { shapes[$0[0]].position < shapes[$1[0]].position }
        var componentOfRoot = [Int: Int]()
        for (c, list) in components.enumerated() { componentOfRoot[find(list[0])] = c }
        var erasersOf = [[String]](repeating: [], count: components.count)
        for id in eraserIds {
            if let owner = eraserOwner[id], let c = componentOfRoot[find(owner)] { erasersOf[c].append(id) }
        }
        let elements = components.enumerated().map { c, list -> Element in
            let memberStrokes = list.map { shapes[$0].stroke }
            let first = memberStrokes[0]
            return Element(id: first.id, number: c + 1, strokeIds: memberStrokes.map(\.id), eraserIds: erasersOf[c],
                           groupId: memberStrokes.lazy.compactMap(\.groupId).first, brushType: first.brushType,
                           colorHex: first.colorHex, hidden: first.hidden)
        }
        var groupNumbers = [String: Int]()
        for g in elements.compactMap(\.groupId) where groupNumbers[g] == nil { groupNumbers[g] = groupNumbers.count + 1 }
        return ElementIndex(strokes, elements, groupNumbers)
    }
}
