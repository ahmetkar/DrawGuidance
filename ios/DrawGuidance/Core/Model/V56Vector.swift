import Foundation

// v56 aşama 8A (2E, yalnız Resim): dolgu/kontur/görünüm seçenekleri, bağlı kopya, opaklık maskesi, Tekrarla
// (Android `V56Vector.kt` ile aynı kurallar).

enum CapStyle: String, KotlinEnum { case ROUND, BUTT, SQUARE; static var fallback: CapStyle { .ROUND } }
enum JoinStyle: String, KotlinEnum { case ROUND, MITER, BEVEL; static var fallback: JoinStyle { .ROUND } }
enum StrokeAlign: String, KotlinEnum { case CENTER, INSIDE, OUTSIDE; static var fallback: StrokeAlign { .CENTER } }
enum ArrowKind: String, KotlinEnum { case NONE, TRIANGLE, OPEN, CIRCLE, BAR; static var fallback: ArrowKind { .NONE } }

/// Çoklu dolgu ya da kontur katmanı ([width] yalnız konturda).
struct PaintLayer: Codable, Equatable {
    var colorHex: String
    var width: Float = 4
    var opacity: Float = 1

    init(colorHex: String, width: Float = 4, opacity: Float = 1) { self.colorHex = colorHex; self.width = width; self.opacity = opacity }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        colorHex = try c.req("colorHex"); width = c.v("width", 4); opacity = c.v("opacity", 1)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(colorHex, "colorHex"); try c.e(width, "width", 4); try c.e(opacity, "opacity", 1)
    }
}

/// Serbest gradyan noktası: şeklin kutusunda (0..1) konum ve renk.
struct GradientPoint: Codable, Equatable, Hashable {
    var x: Float
    var y: Float
    var colorHex: String
}

struct VectorStyle: Codable, Equatable {
    var cap: CapStyle = .ROUND
    var join: JoinStyle = .ROUND
    var miterLimit: Float = 4
    var align: StrokeAlign = .CENTER
    var dash: [Float]? = nil
    var arrowStart: ArrowKind = .NONE
    var arrowEnd: ArrowKind = .NONE
    var patternId: String? = nil
    var patternScale: Float = 1
    var freeGradient: [GradientPoint]? = nil
    var blend: LayerBlend = .NORMAL
    var fills: [PaintLayer] = []
    var strokes: [PaintLayer] = []
    var opacityMask: [Stroke]? = nil

    init() {}

    var isDefault: Bool { self == VectorStyle() }
    var hasShapeOptions: Bool { var c = self; c.blend = .NORMAL; c.opacityMask = nil; return c != VectorStyle() }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        let d = VectorStyle()
        cap = c.v("cap", d.cap); join = c.v("join", d.join); miterLimit = c.v("miterLimit", d.miterLimit); align = c.v("align", d.align)
        dash = c.o("dash"); arrowStart = c.v("arrowStart", d.arrowStart); arrowEnd = c.v("arrowEnd", d.arrowEnd)
        patternId = c.o("patternId"); patternScale = c.v("patternScale", d.patternScale); freeGradient = c.o("freeGradient")
        blend = c.v("blend", d.blend); fills = c.v("fills", []); strokes = c.v("strokes", []); opacityMask = c.o("opacityMask")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        let d = VectorStyle()
        try c.e(cap, "cap", d.cap); try c.e(join, "join", d.join); try c.e(miterLimit, "miterLimit", d.miterLimit); try c.e(align, "align", d.align)
        try c.o(dash, "dash"); try c.e(arrowStart, "arrowStart", d.arrowStart); try c.e(arrowEnd, "arrowEnd", d.arrowEnd)
        try c.o(patternId, "patternId"); try c.e(patternScale, "patternScale", d.patternScale); try c.o(freeGradient, "freeGradient")
        try c.e(blend, "blend", d.blend); try c.e(fills, "fills", []); try c.e(strokes, "strokes", []); try c.o(opacityMask, "opacityMask")
    }
}

/// Bağlı kopya: kopya = M·kaynak + t. [repeat] true: Tekrarla'nın ürettiği kopya.
struct CloneLink: Codable, Equatable {
    var source: String
    var a: Float = 1, b: Float = 0, c: Float = 0, d: Float = 1, tx: Float = 0, ty: Float = 0
    var `repeat` = false

    init(source: String, a: Float = 1, b: Float = 0, c: Float = 0, d: Float = 1, tx: Float = 0, ty: Float = 0, repeat r: Bool = false) {
        self.source = source; self.a = a; self.b = b; self.c = c; self.d = d; self.tx = tx; self.ty = ty; self.repeat = r
    }

    func map(_ x: Float, _ y: Float) -> (Float, Float) { (a * x + b * y + tx, c * x + d * y + ty) }
    var scale: Float { min(max(abs(a * d - b * c).squareRoot(), 0.05), 20) }

    init(from decoder: Decoder) throws {
        let k = try decoder.container(keyedBy: AnyKey.self)
        source = try k.req("source"); a = k.v("a", 1); b = k.v("b", 0); c = k.v("c", 0); d = k.v("d", 1)
        tx = k.v("tx", 0); ty = k.v("ty", 0); self.repeat = k.v("repeat", false)
    }

    func encode(to encoder: Encoder) throws {
        var k = encoder.container(keyedBy: AnyKey.self)
        try k.r(source, "source"); try k.e(a, "a", 1); try k.e(b, "b", 0); try k.e(c, "c", 0); try k.e(d, "d", 1)
        try k.e(tx, "tx", 0); try k.e(ty, "ty", 0); try k.e(self.repeat, "repeat", false)
    }
}

enum RepeatKind: String, KotlinEnum { case LINEAR, GRID, RADIAL, MIRROR, STEP; static var fallback: RepeatKind { .LINEAR } }

struct RepeatSpec: Codable, Equatable {
    var kind: RepeatKind
    var count = 4
    var dx: Float = 40, dy: Float = 0
    var cols = 3, rows = 3
    var gapX: Float = 20, gapY: Float = 20
    var radius: Float = 150
    var angle: Float = 0
    var scale: Float = 1

    init(kind: RepeatKind, count: Int = 4, dx: Float = 40, dy: Float = 0, cols: Int = 3, rows: Int = 3, gapX: Float = 20, gapY: Float = 20,
         radius: Float = 150, angle: Float = 0, scale: Float = 1) {
        self.kind = kind; self.count = count; self.dx = dx; self.dy = dy; self.cols = cols; self.rows = rows
        self.gapX = gapX; self.gapY = gapY; self.radius = radius; self.angle = angle; self.scale = scale
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        kind = try c.req("kind"); count = c.v("count", 4); dx = c.v("dx", 40); dy = c.v("dy", 0); cols = c.v("cols", 3); rows = c.v("rows", 3)
        gapX = c.v("gapX", 20); gapY = c.v("gapY", 20); radius = c.v("radius", 150); angle = c.v("angle", 0); scale = c.v("scale", 1)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.r(kind, "kind"); try c.e(count, "count", 4); try c.e(dx, "dx", 40); try c.e(dy, "dy", 0); try c.e(cols, "cols", 3); try c.e(rows, "rows", 3)
        try c.e(gapX, "gapX", 20); try c.e(gapY, "gapY", 20); try c.e(radius, "radius", 150); try c.e(angle, "angle", 0); try c.e(scale, "scale", 1)
    }
}

/// ContentCodec V11'de elemanın 2E ek bilgileri (tek JSON).
struct StrokeExtras2: Codable, Equatable {
    var style2: VectorStyle? = nil
    var cloneOf: CloneLink? = nil
    var `repeat`: RepeatSpec? = nil

    static func of(_ s: Stroke) -> StrokeExtras2? {
        if s.style2 == nil && s.cloneOf == nil && s.repeat == nil { return nil }
        return StrokeExtras2(style2: s.style2, cloneOf: s.cloneOf, repeat: s.repeat)
    }

    init(style2: VectorStyle? = nil, cloneOf: CloneLink? = nil, repeat r: RepeatSpec? = nil) { self.style2 = style2; self.cloneOf = cloneOf; self.repeat = r }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        style2 = c.o("style2"); cloneOf = c.o("cloneOf"); self.repeat = c.o("repeat")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.o(style2, "style2"); try c.o(cloneOf, "cloneOf"); try c.o(self.repeat, "repeat")
    }
}

enum V56Vector {
    static let MAX_COPIES = 400

    static func pointBounds(_ strokes: [Stroke]) -> [Float]? {
        var l = Float.greatestFiniteMagnitude, t = l, r = -l, b = -l
        for s in strokes { for p in s.points { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) } }
        return l > r ? nil : [l, t, r, b]
    }

    private static func rad(_ deg: Float) -> Float { deg * .pi / 180 }

    static func about(_ cx: Float, _ cy: Float, _ deg: Float, _ scale: Float, _ dx: Float, _ dy: Float, flipX: Bool = false) -> CloneLink {
        let co = cos(rad(deg)) * scale, si = sin(rad(deg)) * scale
        let fx: Float = flipX ? -1 : 1
        let a = co * fx, b = -si, c = si * fx, d = co
        return CloneLink(source: "", a: a, b: b, c: c, d: d, tx: cx - a * cx - b * cy + dx, ty: cy - c * cx - d * cy + dy)
    }

    static func compose(_ then: CloneLink, _ first: CloneLink) -> CloneLink {
        CloneLink(source: "", a: then.a * first.a + then.b * first.c, b: then.a * first.b + then.b * first.d,
                  c: then.c * first.a + then.d * first.c, d: then.c * first.b + then.d * first.d,
                  tx: then.a * first.tx + then.b * first.ty + then.tx, ty: then.c * first.tx + then.d * first.ty + then.ty)
    }

    static func reflect(_ cx: Float, _ cy: Float, _ deg: Float) -> CloneLink {
        let t = 2 * rad(deg)
        let a = cos(t), b = sin(t), c = sin(t), d = -cos(t)
        return CloneLink(source: "", a: a, b: b, c: c, d: d, tx: cx - a * cx - b * cy, ty: cy - c * cx - d * cy)
    }

    static func transforms(_ spec: RepeatSpec, _ box: [Float]) -> [CloneLink] {
        let w = box[2] - box[0], h = box[3] - box[1]
        let cx = (box[0] + box[2]) / 2, cy = (box[1] + box[3]) / 2
        let n = min(max(spec.count, 1), MAX_COPIES)
        switch spec.kind {
        case .LINEAR: return (1...n).map { CloneLink(source: "", tx: spec.dx * Float($0), ty: spec.dy * Float($0)) }
        case .GRID:
            var out: [CloneLink] = []
            for j in 0..<min(max(spec.rows, 1), 40) {
                for i in 0..<min(max(spec.cols, 1), 40) {
                    if i == 0 && j == 0 { continue }
                    if out.count >= MAX_COPIES { break }
                    out.append(CloneLink(source: "", tx: Float(i) * (w + spec.gapX), ty: Float(j) * (h + spec.gapY)))
                }
            }
            return out
        case .RADIAL:
            let m = max(n, 2)
            return (1..<m).map { about(cx, cy + spec.radius, 360 * Float($0) / Float(m), 1, 0, 0) }
        case .MIRROR:
            return [reflect(box[2] + spec.gapX / 2, cy, 90 + spec.angle)]
        case .STEP:
            let step = about(cx, cy, spec.angle, min(max(spec.scale, 0.1), 4), spec.dx, spec.dy)
            var acc = step
            return (1...n).map { i in if i == 1 { return step }; acc = compose(step, acc); return acc }
        }
    }

    static func mapPoint(_ p: StrokePoint, _ m: CloneLink) -> StrokePoint { let (x, y) = m.map(p.x, p.y); return p.at(x, y) }

    static func apply(_ s: Stroke, _ m: CloneLink) -> Stroke {
        let f: (StrokePoint) -> StrokePoint = { mapPoint($0, m) }
        let k = m.scale
        let scaled = abs(k - 1) > 1e-4
        var o = s
        o.points = s.points.map(f); o.clip = s.clip?.map(f)
        if var mk = s.mask { mk.points = mk.points.map(f); o.mask = mk }
        if scaled && !s.brushType.isQuad { o.sizePx = min(max(s.sizePx * k, 0.5), 2000) }
        if scaled, var sp = s.shape {
            sp.style.strokeWidth = min(max(sp.style.strokeWidth * k, 0.2), 400); sp.style.cornerRadius *= k; o.shape = sp
        }
        o.style2 = mapStyle(s.style2, f)
        return o
    }

    static func mapStyle(_ style: VectorStyle?, _ f: (StrokePoint) -> StrokePoint) -> VectorStyle? {
        guard var st = style, let masks = st.opacityMask else { return style }
        st.opacityMask = masks.map { var m = $0; m.points = m.points.map(f); m.clip = m.clip?.map(f); return m }
        return st
    }

    // MARK: bağlı kopya

    static func clone(_ strokes: [Stroke], _ ids: Set<String>, _ dx: Float, _ dy: Float, newId: () -> String) -> [Stroke] {
        let sel = strokes.filter { ids.contains($0.id) && !$0.hidden }
        if sel.isEmpty { return strokes }
        var groups: [String: String] = [:]
        var copies: [Stroke] = []
        for (i, s) in sel.enumerated() {
            let link = CloneLink(source: s.id, tx: dx, ty: dy)
            var c = apply(s, link)
            c.id = newId(); c.order = strokes.count + i; c.cloneOf = link; c.repeat = nil
            if let g = s.groupId { if groups[g] == nil { groups[g] = newId() }; c.groupId = groups[g] } else { c.groupId = nil }
            copies.append(c)
        }
        return strokes + copies
    }

    static func unlink(_ strokes: [Stroke], _ ids: Set<String>) -> [Stroke] {
        strokes.map { s in if ids.contains(s.id) && s.cloneOf != nil { var c = s; c.cloneOf = nil; return c }; return s }
    }

    private static func center(_ s: Stroke) -> (Float, Float)? {
        pointBounds([s]).map { (($0[0] + $0[2]) / 2, ($0[1] + $0[3]) / 2) }
    }

    /// Kaynağı değişen bağlı kopyaları günceller (kopya kendi taşındıysa kayma korunur).
    static func sync(_ old: [Stroke], _ new: [Stroke]) -> [Stroke] {
        if !new.contains(where: { $0.cloneOf != nil }) { return new }
        var oldById: [String: Stroke] = [:]; for s in old { oldById[s.id] = s }
        var newById: [String: Stroke] = [:]; for s in new { newById[s.id] = s }
        var changed = false
        let out = new.map { cl -> Stroke in
            guard let link = cl.cloneOf, let src = newById[link.source], let oldSrc = oldById[link.source], src != oldSrc,
                  let oldCl = oldById[cl.id], oldCl == cl else { return cl }
            var l = link
            if let e = center(apply(oldSrc, link)), let a = center(oldCl) {
                let ddx = a.0 - e.0, ddy = a.1 - e.1
                if abs(ddx) > 0.01 || abs(ddy) > 0.01 { l.tx += ddx; l.ty += ddy }
            }
            changed = true
            var n = apply(src, l)
            n.id = cl.id; n.order = cl.order; n.layerId = cl.layerId; n.groupId = cl.groupId; n.hidden = cl.hidden; n.cloneOf = l; n.repeat = nil
            return n
        }
        return changed ? out : new
    }

    // MARK: Tekrarla

    static func `repeat`(_ strokes: [Stroke], _ ids: Set<String>, _ spec: RepeatSpec, newId: () -> String) -> ([Stroke], Int) {
        let base = removeRepeat(strokes, ids)
        let sel = base.filter { ids.contains($0.id) && !$0.hidden && $0.cloneOf?.repeat != true }
        guard let box = pointBounds(sel) else { return (strokes, 0) }
        let group = sel.compactMap(\.groupId).first ?? newId()
        let sources = Set(sel.map(\.id))
        let withSpec = base.map { s -> Stroke in
            guard sources.contains(s.id) else { return s }
            var c = s; c.repeat = spec; c.groupId = s.groupId ?? group; return c
        }
        var copies: [Stroke] = []
        for t in transforms(spec, box) {
            for s in sel {
                var link = t; link.source = s.id; link.repeat = true
                var c = apply(s, link)
                c.id = newId(); c.order = withSpec.count + copies.count; c.cloneOf = link; c.repeat = nil; c.groupId = s.groupId ?? group
                copies.append(c)
            }
        }
        return (withSpec + copies, copies.count)
    }

    static func removeRepeat(_ strokes: [Stroke], _ ids: Set<String>) -> [Stroke] {
        var sources = Set(strokes.filter { ids.contains($0.id) && $0.repeat != nil }.map(\.id))
        for s in strokes where ids.contains(s.id) { if let l = s.cloneOf, l.repeat { sources.insert(l.source) } }
        if sources.isEmpty { return strokes }
        return strokes.filter { s in !(s.cloneOf?.repeat == true && sources.contains(s.cloneOf!.source)) }
            .map { s in if sources.contains(s.id) { var c = s; c.repeat = nil; return c }; return s }
    }

    static func repeatOf(_ strokes: [Stroke], _ ids: Set<String>) -> RepeatSpec? {
        var byId: [String: Stroke] = [:]; for s in strokes { byId[s.id] = s }
        for s in strokes where ids.contains(s.id) {
            if let r = s.repeat { return r }
            if let l = s.cloneOf, l.repeat, let r = byId[l.source]?.repeat { return r }
        }
        return nil
    }

    // MARK: opaklık maskesi

    static func makeOpacityMask(_ strokes: [Stroke], _ ids: Set<String>) -> [Stroke] {
        let sel = strokes.enumerated().filter { ids.contains($0.element.id) && !$0.element.hidden }
        if sel.count < 2 { return strokes }
        let mask = sel.max { $0.offset < $1.offset }!.element
        let targets = Set(sel.map(\.element.id)).subtracting([mask.id])
        return strokes.filter { $0.id != mask.id }.map { s in
            guard targets.contains(s.id) else { return s }
            var st = s.style2 ?? VectorStyle()
            var m = mask; m.layerId = s.layerId
            st.opacityMask = (st.opacityMask ?? []) + [m]
            var c = s; c.style2 = st; return c
        }
    }

    static func releaseOpacityMask(_ strokes: [Stroke], _ ids: Set<String>) -> [Stroke] {
        var seen = Set<String>()
        var out: [Stroke] = []
        for s in strokes {
            if ids.contains(s.id), let masks = s.style2?.opacityMask, !masks.isEmpty {
                var st = s.style2!; st.opacityMask = nil
                var c = s; c.style2 = st.isDefault ? nil : st
                out.append(c)
                for m in masks where seen.insert(m.id).inserted { out.append(m) }
            } else { out.append(s) }
        }
        return out.enumerated().map { i, s in if s.order == i { return s }; var c = s; c.order = i; return c }
    }

    // MARK: serbest gradyan, ok uçları

    static func freeGradientColor(_ points: [GradientPoint], _ u: Float, _ v: Float, parse: (String) -> UInt32) -> UInt32 {
        if points.isEmpty { return 0 }
        var wr: Float = 0, wg: Float = 0, wb: Float = 0, wa: Float = 0, ws: Float = 0
        for p in points {
            let d2 = (p.x - u) * (p.x - u) + (p.y - v) * (p.y - v)
            let c = parse(p.colorHex)
            if d2 < 1e-6 { return c }
            let w = 1 / (d2 * d2)
            wa += Float((c >> 24) & 0xFF) * w; wr += Float((c >> 16) & 0xFF) * w; wg += Float((c >> 8) & 0xFF) * w; wb += Float(c & 0xFF) * w; ws += w
        }
        func ch(_ x: Float) -> UInt32 { UInt32(min(max(Int(x / ws), 0), 255)) }
        return (ch(wa) << 24) | (ch(wr) << 16) | (ch(wg) << 8) | ch(wb)
    }

    static func arrowHead(_ kind: ArrowKind, _ tx: Float, _ ty: Float, _ ux: Float, _ uy: Float, _ size: Float) -> [(Float, Float)] {
        let px = -uy, py = ux, s = max(size, 2)
        switch kind {
        case .NONE: return []
        case .TRIANGLE, .OPEN:
            return [(tx - ux * s + px * s * 0.5, ty - uy * s + py * s * 0.5), (tx, ty), (tx - ux * s - px * s * 0.5, ty - uy * s - py * s * 0.5)]
        case .CIRCLE: return [(tx - ux * s * 0.4, ty - uy * s * 0.4), (s * 0.4, 0)]
        case .BAR: return [(tx + px * s * 0.5, ty + py * s * 0.5), (tx - px * s * 0.5, ty - py * s * 0.5)]
        }
    }
}
