import Foundation

/// v56 aşama 6A (2E, yalnız Resim): Nesne › Hizala ve dağıt ile vektör girdap, büzüştür, şişir (Android `V56Arrange.kt`).
enum V56Arrange {
    enum Align: String, CaseIterable { case LEFT, HCENTER, RIGHT, TOP, VCENTER, BOTTOM }
    enum Ref: String, CaseIterable { case PAPER, SELECTION, KEY }
    enum Distribute: String, CaseIterable { case HORIZONTAL, VERTICAL, SPACE_H, SPACE_V }

    struct Unit: Equatable {
        var ids: Set<String>
        var l: Float, t: Float, r: Float, b: Float
        var cx: Float { (l + r) / 2 }
        var cy: Float { (t + b) / 2 }
    }

    static func units(_ strokes: [Stroke], _ ids: Set<String>, boundsOf: (Stroke) -> [Float]? = { TransformOps.pointsBounds([$0]) }) -> [Unit] {
        TransformOps.units(strokes, ids).compactMap { g in
            var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude, r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
            for s in strokes where g.contains(s.id) {
                if let bb = boundsOf(s) { l = min(l, bb[0]); t = min(t, bb[1]); r = max(r, bb[2]); b = max(b, bb[3]) }
            }
            return l > r ? nil : Unit(ids: g, l: l, t: t, r: r, b: b)
        }
    }

    static func align(_ units: [Unit], _ align: Align, _ ref: Ref, paperW: Float, paperH: Float, keyId: String? = nil) -> [(Float, Float)] {
        if units.isEmpty { return [] }
        let keyIndex = keyId.flatMap { k in units.firstIndex { $0.ids.contains(k) } }
        let box: [Float]
        if ref == .PAPER || (ref == .SELECTION && units.count == 1) {
            box = [0, 0, paperW, paperH]
        } else if ref == .KEY, let k = keyIndex {
            box = [units[k].l, units[k].t, units[k].r, units[k].b]
        } else {
            box = [units.map(\.l).min()!, units.map(\.t).min()!, units.map(\.r).max()!, units.map(\.b).max()!]
        }
        return units.enumerated().map { i, u in
            if ref == .KEY && i == keyIndex { return (0, 0) }
            switch align {
            case .LEFT: return (box[0] - u.l, 0)
            case .HCENTER: return ((box[0] + box[2]) / 2 - u.cx, 0)
            case .RIGHT: return (box[2] - u.r, 0)
            case .TOP: return (0, box[1] - u.t)
            case .VCENTER: return (0, (box[1] + box[3]) / 2 - u.cy)
            case .BOTTOM: return (0, box[3] - u.b)
            }
        }
    }

    static func distribute(_ units: [Unit], _ mode: Distribute) -> [(Float, Float)] {
        var out = units.map { _ in (Float(0), Float(0)) }
        if units.count < 3 { return out }
        let horizontal = mode == .HORIZONTAL || mode == .SPACE_H
        let order = units.indices.sorted { horizontal ? units[$0].cx < units[$1].cx : units[$0].cy < units[$1].cy }
        if mode == .HORIZONTAL || mode == .VERTICAL {
            let first = units[order.first!], last = units[order.last!]
            let a = horizontal ? first.cx : first.cy
            let step = ((horizontal ? last.cx : last.cy) - a) / Float(order.count - 1)
            for (k, i) in order.enumerated() {
                let d = a + step * Float(k) - (horizontal ? units[i].cx : units[i].cy)
                out[i] = horizontal ? (d, 0) : (0, d)
            }
        } else {
            let sizes = order.map { horizontal ? units[$0].r - units[$0].l : units[$0].b - units[$0].t }
            let start = horizontal ? units[order.first!].l : units[order.first!].t
            let end = horizontal ? units[order.last!].r : units[order.last!].b
            let gap = (end - start - sizes.reduce(0, +)) / Float(order.count - 1)
            var pos = start
            for (k, i) in order.enumerated() {
                let d = pos - (horizontal ? units[i].l : units[i].t)
                out[i] = horizontal ? (d, 0) : (0, d)
                pos += sizes[k] + gap
            }
        }
        return out
    }

    static func shift(_ s: Stroke, _ dx: Float, _ dy: Float) -> Stroke {
        if dx == 0 && dy == 0 { return s }
        func m(_ p: StrokePoint) -> StrokePoint { p.at(p.x + dx, p.y + dy) }
        var o = s
        o.points = s.points.map(m); o.clip = s.clip?.map(m)
        if var mk = s.mask { mk.points = mk.points.map(m); o.mask = mk }
        if var w = s.warp {
            w.mesh = w.mesh?.map(m); w.quad = w.quad?.map(m)
            if var wv = w.wave, wv.bottom > wv.top { wv.top += dy; wv.bottom += dy; wv.cx += dx; w.wave = wv }
            o.warp = w
        }
        return o
    }

    static func applyOffsets(_ strokes: [Stroke], _ units: [Unit], _ offsets: [(Float, Float)]) -> [Stroke] {
        var byId: [String: (Float, Float)] = [:]
        for (i, u) in units.enumerated() where i < offsets.count { for id in u.ids { byId[id] = offsets[i] } }
        return strokes.map { s in byId[s.id].map { shift(s, $0.0, $0.1) } ?? s }
    }

    // MARK: Efektler

    enum Effect: String, CaseIterable { case TWIRL, PUCKER, BLOAT }

    static func canEffect(_ s: Stroke) -> Bool { !s.brushType.isQuad && s.brushType != .ERASER && s.points.count >= 2 }

    private static func densify(_ points: [StrokePoint], closed: Bool, maxSeg: Float, anchorEvery: Int) -> ([StrokePoint], [Float]) {
        var out: [StrokePoint] = [], ts: [Float] = []
        let n = points.count
        let segs = closed ? n : n - 1
        let anchors = (0..<n).filter { $0 % anchorEvery == 0 || (!closed && $0 == n - 1) }
        for i in 0..<segs {
            let a = points[i], b = points[(i + 1) % n]
            let k = max(1, Int((hypot(b.x - a.x, b.y - a.y) / maxSeg).rounded(.up)))
            for j in 0..<k {
                let f = Float(j) / Float(k)
                out.append(a.at(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f))
                let pos = Float(i) + f
                let lo = anchors.last { Float($0) <= pos } ?? 0
                let hi = anchors.first { Float($0) > pos } ?? (closed ? n : n - 1)
                ts.append(hi > lo ? (pos - Float(lo)) / Float(hi - lo) : 0)
            }
        }
        if !closed { out.append(points[n - 1]); ts.append(0) }
        return (out, ts)
    }

    private static func effectPoint(_ p: StrokePoint, _ t: Float, _ effect: Effect, _ amount: Float, _ cx: Float, _ cy: Float, _ radius: Float) -> StrokePoint {
        let ox = p.x - cx, oy = p.y - cy
        switch effect {
        case .TWIRL:
            let d = hypot(ox, oy)
            let a = amount * Float.pi / 180 * max(0, 1 - d / max(1, radius))
            return p.at(cx + ox * cos(a) - oy * sin(a), cy + ox * sin(a) + oy * cos(a))
        case .PUCKER, .BLOAT:
            let k = effect == .PUCKER ? amount : -amount
            let w = t <= 1e-4 ? k : -k * sin(Float.pi * t)
            return p.at(p.x + ox * w, p.y + oy * w)
        }
    }

    static func effect(_ strokes: [Stroke], _ ids: Set<String>, _ effect: Effect, _ amount: Float) -> [Stroke] {
        let sel = strokes.filter { ids.contains($0.id) && canEffect($0) }
        guard let bb = TransformOps.pointsBounds(sel) else { return strokes }
        let cx = (bb[0] + bb[2]) / 2, cy = (bb[1] + bb[3]) / 2
        let radius = max(1, hypot(bb[2] - bb[0], bb[3] - bb[1]) / 2)
        let maxSeg = max(1, radius / 24)
        return strokes.map { s in
            guard ids.contains(s.id), canEffect(s) else { return s }
            var o = s
            if var shape = s.shape, !shape.subpaths.isEmpty, shape.subpaths.reduce(0, { $0 + $1.count }) == s.points.count {
                var at = 0
                var pts: [StrokePoint] = []
                shape.subpaths = shape.subpaths.map { sp in
                    let part = Array(s.points[at..<(at + sp.count)]); at += sp.count
                    let (d, ts) = part.count >= 2 ? densify(part, closed: sp.closed, maxSeg: maxSeg, anchorEvery: 1) : (part, part.map { _ in Float(0) })
                    for (i, p) in d.enumerated() { pts.append(effectPoint(p, ts[i], effect, amount, cx, cy, radius)) }
                    var n = sp; n.count = d.count
                    return n
                }
                o.points = pts; o.shape = shape
            } else {
                let (d, ts) = densify(s.points, closed: false, maxSeg: maxSeg, anchorEvery: max(1, s.points.count / 16))
                o.points = d.enumerated().map { effectPoint($0.element, ts[$0.offset], effect, amount, cx, cy, radius) }
            }
            return o
        }
    }

    static func range(_ effect: Effect) -> ClosedRange<Float> { effect == .TWIRL ? -720...720 : 0...1 }
    static func defaultAmount(_ effect: Effect) -> Float { effect == .TWIRL ? 90 : 0.3 }
}
