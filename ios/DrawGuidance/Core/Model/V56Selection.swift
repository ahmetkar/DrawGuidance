import Foundation

/// v56 seçim işlemleri (Android `V56Selection` ile aynı kural): seçim alanı, genişlet/daralt, kırpma maskesi sınırı,
/// sihirli değnek benzerliği, kement dolgu.
enum V56Selection {
    enum WandCriterion: CaseIterable { case COLOR, STROKE, APPEARANCE }

    static func rect(_ left: Float, _ top: Float, _ right: Float, _ bottom: Float) -> [StrokePoint] {
        [StrokePoint(left, top), StrokePoint(right, top), StrokePoint(right, bottom), StrokePoint(left, bottom)]
    }

    static func signedArea(_ p: [StrokePoint]) -> Float {
        var a: Float = 0
        for i in p.indices { let q = p[i], r = p[(i + 1) % p.count]; a += q.x * r.y - r.x * q.y }
        return a / 2
    }

    /// Alanı `d` kadar genişletir (eksi: daraltır); katlanan ya da çok küçülen alan için nil.
    static func offset(_ points: [StrokePoint], _ d: Float) -> [StrokePoint]? {
        let p = dedupe(points)
        if p.count < 3 { return nil }
        let sign: Float = signedArea(p) >= 0 ? 1 : -1
        let n = p.count
        var out: [StrokePoint] = []
        out.reserveCapacity(n)
        for i in 0..<n {
            let a = p[(i - 1 + n) % n], b = p[i], c = p[(i + 1) % n]
            let n1 = normal(a, b, sign), n2 = normal(b, c, sign)
            var mx = n1.0 + n2.0, my = n1.1 + n2.1
            let ml = hypotf(mx, my)
            if ml < 1e-4 { mx = n1.0; my = n1.1 } else { mx /= ml; my /= ml }
            let cos = max(mx * n1.0 + my * n1.1, 1 / 3)
            let k = d / cos
            out.append(StrokePoint(b.x + mx * k, b.y + my * k))
        }
        let area = signedArea(out)
        if abs(area) < 1 || (area >= 0) != (sign > 0) { return nil }
        for i in 0..<n {
            let a = p[i], b = p[(i + 1) % n], c = out[i], e = out[(i + 1) % n]
            if (b.x - a.x) * (e.x - c.x) + (b.y - a.y) * (e.y - c.y) <= 0 { return nil }
        }
        return out
    }

    private static func normal(_ a: StrokePoint, _ b: StrokePoint, _ sign: Float) -> (Float, Float) {
        let dx = b.x - a.x, dy = b.y - a.y
        let l = hypotf(dx, dy)
        if l <= 1e-6 { return (0, 0) }
        return (sign * dy / l, sign * -dx / l)
    }

    private static func dedupe(_ points: [StrokePoint]) -> [StrokePoint] {
        var out: [StrokePoint] = []
        for q in points where out.isEmpty || hypotf(out.last!.x - q.x, out.last!.y - q.y) > 0.5 { out.append(q) }
        if out.count > 1, hypotf(out[0].x - out.last!.x, out[0].y - out.last!.y) <= 0.5 { out.removeLast() }
        return out
    }

    static func inside(_ p: [StrokePoint], _ x: Float, _ y: Float) -> Bool {
        var inside = false
        var j = p.count - 1
        for i in p.indices {
            let a = p[i], b = p[j]
            if (a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x { inside.toggle() }
            j = i
        }
        return inside
    }

    /// Kırpma maskesinin sınırı olabilecek nesnenin dış çizgisi.
    static func outline(_ s: Stroke) -> [StrokePoint]? {
        if s.brushType == .SHAPE, let spec = s.shape {
            var start = 0
            for sub in spec.subpaths {
                if sub.closed && sub.count >= 3 && start + sub.count <= s.points.count { return Array(s.points[start..<(start + sub.count)]) }
                start += sub.count
            }
            return s.points.count >= 3 ? s.points : nil
        }
        if s.brushType == .LASSO_FILL { return s.points.count >= 3 ? s.points : nil }
        if s.brushType.isQuad { return s.points.count == 4 ? s.points : nil }
        return nil
    }

    static func canClip(_ selected: [Stroke]) -> Bool { selected.count >= 2 && outline(selected.last!) != nil }

    static func clip(_ selected: [Stroke], groupId: String) -> [Stroke] {
        guard canClip(selected), let top = selected.last, let pts = outline(top) else { return [] }
        let mask = ClipMask(points: pts, source: top.id)
        var out = selected.dropLast().map { s -> Stroke in var c = s; c.mask = mask; c.groupId = groupId; return c }
        var t = top; t.hidden = true; t.groupId = groupId
        out.append(t)
        return out
    }

    static func release(_ page: [Stroke], _ selectedIds: Set<String>) -> [Stroke] {
        let sources = Set(page.filter { selectedIds.contains($0.id) }.compactMap { $0.mask?.source })
        if sources.isEmpty { return page }
        let masked = Set(page.filter { s in s.mask?.source.map { sources.contains($0) } ?? false }.map(\.id))
        return page.map { s in
            var c = s
            if sources.contains(s.id) { c.hidden = false; c.groupId = nil }
            else if masked.contains(s.id) { c.mask = nil; c.groupId = nil }
            return c
        }
    }

    static func colorDistance(_ a: String, _ b: String) -> Float {
        guard let x = rgb(a), let y = rgb(b) else { return 1 }
        let dr = Int((x >> 16) & 0xFF) - Int((y >> 16) & 0xFF)
        let dg = Int((x >> 8) & 0xFF) - Int((y >> 8) & 0xFF)
        let db = Int(x & 0xFF) - Int(y & 0xFF)
        return Float(Double(dr * dr + dg * dg + db * db).squareRoot()) / 441.673
    }

    private static func rgb(_ hex: String) -> UInt32? {
        let h = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        guard let v = UInt64(h, radix: 16) else { return nil }
        switch h.count { case 6: return UInt32(v); case 8: return UInt32(v & 0xFFFFFF); default: return nil }
    }

    static func colorOf(_ s: Stroke) -> String { s.shape.map { $0.style.fillColorHex ?? $0.style.strokeColorHex } ?? s.colorHex }

    static func similar(_ target: Stroke, _ candidates: [Stroke], _ criterion: WandCriterion, _ tolerance: Float) -> [Stroke] {
        let tol = min(max(tolerance, 0), 1)
        let color = colorOf(target)
        func sizeClose(_ s: Stroke) -> Bool { abs(s.sizePx - target.sizePx) / max(max(s.sizePx, target.sizePx), 1) <= tol + 1e-4 }
        return candidates.filter { s in
            if s.hidden || s.brushType == .ERASER { return false }
            switch criterion {
            case .COLOR: return colorDistance(colorOf(s), color) <= tol + 1e-4
            case .STROKE: return s.brushType == target.brushType && sizeClose(s)
            case .APPEARANCE:
                return s.brushType == target.brushType && sizeClose(s) && colorDistance(colorOf(s), color) <= tol + 1e-4 &&
                    abs(s.opacity - target.opacity) <= tol + 1e-4
            }
        }
    }

    static func lassoFill(id: String, _ points: [StrokePoint], colorHex: String, opacity: Float, order: Int, layerId: String? = nil) -> Stroke {
        Stroke(id: id, points: points, colorHex: colorHex, brushType: .LASSO_FILL, sizePx: 1, opacity: min(max(opacity, 0), 1),
               order: order, layerId: layerId)
    }

    static func applyArea(_ before: Set<String>, _ after: [Stroke], _ area: ClipMask?) -> [Stroke] {
        guard let area else { return after }
        return after.map { s in
            if before.contains(s.id) || s.mask != nil || s.brushType.isObject || s.brushType == .LASSO_FILL { return s }
            var c = s; c.mask = area; return c
        }
    }
}
