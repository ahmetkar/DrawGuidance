import Foundation

/// v27 seçim önerileri.
enum SelectionSuggest {
    static let PART_MAX = 3
    static let SHAPE_MIN = 2
    static let PART_MIN_CONFIDENCE: Float = 0.42
    static let MAX_ELEMENTS = 24
}

/// Kısmi eşleşmenin eksik parçalarını seçili çizimin yanına yerleştirir.
enum PartFit {
    static func missing(_ r: PartialResult, _ query: [[P]], maxStretch: Float = 1.6) -> [[P]] {
        let geo = r.geometry
        let mapped = Set(r.mapping.filter { geo.indices.contains($0) })
        if mapped.isEmpty || mapped.count >= geo.count { return [] }
        let mappedPts = geo.indices.filter { mapped.contains($0) }.flatMap { geo[$0] }
        guard let tb = box(mappedPts), let qb = box(query.flatMap { $0 }) else { return [] }
        let tw = tb[2] - tb[0], th = tb[3] - tb[1]
        let qw = qb[2] - qb[0], qh = qb[3] - qb[1]
        let te = max(max(tw, th), 1e-4)
        var sx: Float = tw > te * 0.15 ? qw / tw : .nan
        var sy: Float = th > te * 0.15 ? qh / th : .nan
        if sx.isNaN && sy.isNaN { sx = max(qw, qh) / te; sy = sx }
        if sx.isNaN { sx = sy }
        if sy.isNaN { sy = sx }
        if !sx.isFinite || !sy.isFinite || sx <= 0 || sy <= 0 { return [] }
        let g = (sx * sy).squareRoot(), k = maxStretch.squareRoot()
        sx = sx.coerceIn(g / k, g * k); sy = sy.coerceIn(g / k, g * k)
        let tcx = (tb[0] + tb[2]) / 2, tcy = (tb[1] + tb[3]) / 2
        let qcx = (qb[0] + qb[2]) / 2, qcy = (qb[1] + qb[3]) / 2
        return geo.indices.filter { !mapped.contains($0) }.map { i in geo[i].map { P(qcx + ($0.x - tcx) * sx, qcy + ($0.y - tcy) * sy) } }
    }

    static func box(_ points: [P]) -> [Float]? {
        if points.isEmpty { return nil }
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude, r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
        for p in points { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
        return [l, t, r, b]
    }
}

/// v27 hazır vektör şekillere benzerlik ($Q bulutu).
final class VectorShapeMatcher {
    struct Item { let id: String; let strokes: [[P]]; var stretch: Bool = false }
    struct Match { let id: String; let confidence: Float; let distance: Float }
    private struct Entry { let id: String; let cloud: QCloud; let aspect: Float; let stretch: Bool }

    static let TAU: Float = 0.16
    static let ASPECT_W: Float = 0.25
    static let MIN_CONFIDENCE: Float = 0.6
    static let STRETCH_MIN_SIDE: Float = 0.18
    static let STRETCH_COST: Float = 0.015

    private let entries: [Entry]

    init(_ items: [Item]) {
        entries = items.compactMap { item in
            let clean = item.strokes.filter { $0.count >= 2 }
            if clean.isEmpty { return nil }
            let polys = ShapeSignatures.polys(item.stretch ? Self.stretched(clean) : ShapeSignatures.normalize(clean))
            guard let c = QDollar.cloud(polys) else { return nil }
            return Entry(id: item.id, cloud: c, aspect: ShapeFeatures.aspect(polys), stretch: item.stretch)
        }
    }

    var size: Int { entries.count }

    func match(_ query: [[P]], limit: Int = 2, minConfidence: Float = VectorShapeMatcher.MIN_CONFIDENCE) -> [Match] {
        let clean = query.filter { $0.count >= 2 }
        if clean.isEmpty || entries.isEmpty { return [] }
        let polys = ShapeSignatures.polys(ShapeSignatures.normalize(clean))
        guard let qc = QDollar.cloud(polys) else { return [] }
        let qa = ShapeFeatures.aspect(polys)
        let b = PartFit.box(clean.flatMap { $0 })
        let w = b.map { $0[2] - $0[0] } ?? 0, h = b.map { $0[3] - $0[1] } ?? 0
        let boxy = min(w, h) >= max(w, h) * Self.STRETCH_MIN_SIDE
        let qs = boxy && entries.contains(where: \.stretch) ? QDollar.cloud(ShapeSignatures.polys(Self.stretched(clean))) : nil
        var out: [Match] = []
        for e in entries {
            let d: Float
            if e.stretch {
                guard let qs else { continue }
                d = QDollar.distance(qs, e.cloud) + Self.STRETCH_COST
            } else {
                if abs(e.aspect - qa) >= 0.3 { continue }
                d = QDollar.distance(qc, e.cloud) + Self.ASPECT_W * abs(e.aspect - qa)
            }
            let m = Match(id: e.id, confidence: Float(exp(Double(-d / Self.TAU))), distance: d)
            if m.confidence >= minConfidence { out.append(m) }
        }
        out = out.enumerated().sorted { $0.element.distance != $1.element.distance ? $0.element.distance < $1.element.distance : $0.offset < $1.offset }.map(\.element)
        return Array(out.prefix(limit))
    }

    private static func stretched(_ strokes: [[P]]) -> [[P]] {
        guard let b = PartFit.box(strokes.flatMap { $0 }) else { return strokes }
        let w = max(b[2] - b[0], 1e-4), h = max(b[3] - b[1], 1e-4)
        return strokes.map { s in s.map { P(($0.x - b[0]) / w, ($0.y - b[1]) / h) } }
    }
}
