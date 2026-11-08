import CoreGraphics
import Foundation

/// Bir kez hesaplanıp çizilen fırça izi parçası (Android `StrokeChunk`): gövde ve kâğıda düşen gölge işlemleri.
struct BrushChunk {
    var bounds: CGRect
    var ops: [(CGContext) -> Void]
    var shadowOps: [(CGContext) -> Void] = []
    var hasShadow: Bool { !shadowOps.isEmpty }
    func draw(_ c: CGContext) { for op in ops { op(c) } }
    func drawShadow(_ c: CGContext) { for op in shadowOps { op(c) } }
}

/// Fırça izlerini CGContext'e çizen motor (Android `BrushPainter` + `StrokeStream` motorlarının bitmiş çizim hâli).
/// Bağlam tuval biriminde ve y ekseni aşağı bakar. Rastgelelik çizim kimliğinden türetilir: aynı çizim Android'deki
/// gibi her seferinde aynı görünür.
enum BrushPainter {
    static let FULL_FLOW: Float = 0.97

    static func paint(_ ctx: CGContext, xs: [Float], ys: [Float], color: UInt32, type: BrushType, sizePx: Float, opacity: Float,
                      seed: Int32, flow: Float, strength: Float, pressures: [Float]? = nil, tilts: [Float]? = nil,
                      params: BrushParams? = nil, orients: [Float]? = nil) {
        let n = min(xs.count, ys.count)
        if n <= 0 || type == .IMAGE || type == .TEXT { return }
        let s = StaticBrush(type: type, color: color, sizePx: sizePx, opacity: opacity, seed: seed, flow: flow, strength: strength,
                            params: params, xs: xs, ys: ys, count: n, pp: pressures, pt: tilts, po: orients)
        s.drawStatic(ctx)
    }

    /// Kalem dinamikleri: nokta başına basınç, eğim ve yön; kalemle çizilmemişse nil.
    static func dynamicsOf(_ points: [StrokePoint]) -> (pressures: [Float]?, tilts: [Float]?, orients: [Float]?) {
        if !points.contains(where: { $0.pressure != 1 || $0.tilt != 0 || $0.orient != 0 }) { return (nil, nil, nil) }
        let p = points.contains { $0.pressure != 1 } ? points.map(\.pressure) : nil
        let t = points.contains { $0.tilt != 0 } ? points.map(\.tilt) : nil
        let o = points.contains { $0.orient != 0 } ? points.map(\.orient) : nil
        return (p, t, o)
    }

    /// Ham noktalardan (yumuşatılmış) yol.
    static func buildPath(_ xs: [Float], _ ys: [Float], _ count: Int, smooth: Bool) -> CGMutablePath {
        let path = CGMutablePath()
        path.move(to: pt(xs[0], ys[0]))
        if !smooth || count < 3 {
            if count > 1 { for i in 1..<count { path.addLine(to: pt(xs[i], ys[i])) } }
            return path
        }
        for i in 1..<count {
            path.addQuadCurve(to: pt((xs[i - 1] + xs[i]) / 2, (ys[i - 1] + ys[i]) / 2), control: pt(xs[i - 1], ys[i - 1]))
        }
        path.addLine(to: pt(xs[count - 1], ys[count - 1]))
        return path
    }

    @inline(__always) static func pt(_ x: Float, _ y: Float) -> CGPoint { CGPoint(x: CGFloat(x), y: CGFloat(y)) }

    /// Sık damgalarda tek geçişte akışkanlık oranında boya bırakmak için damga başına alfa.
    static func dabFlow(_ flow: Float, _ spacing: Float) -> Float {
        if flow >= 0.999 { return 1 }
        return min(max(1 - Float(pow(Double(1 - flow), Double(min(max(spacing, 0.02), 1)))), 0.012), 1)
    }

    /// Kovalardaki noktaları, her biri ayrı birleşen dolu daireler olarak çizer.
    static func drawDabs(_ c: CGContext, _ buckets: DabBuckets, color: UInt32, baseAlpha: Int) {
        buckets.forEach(baseAlpha: baseAlpha) { pts, d, a in
            c.setFillColor(ArgbColor.cgColor(color | 0xFF00_0000, alpha: a))
            let r = CGFloat(d) / 2
            var i = 0
            while i + 1 < pts.count {
                c.fillEllipse(in: CGRect(x: CGFloat(pts[i]) - r, y: CGFloat(pts[i + 1]) - r, width: 2 * r, height: 2 * r))
                i += 2
            }
        }
    }

    /// Android `texturedPaint` ile damga çizimi: renk, damga başına alfa ve (varsa) kâğıda sabit doku maskesi.
    static func texturedDabs(_ buckets: DabBuckets, color: UInt32, flow: Float, texture: CGImage?, textureScale: CGFloat = 1) -> (CGContext) -> Void {
        let alpha = min(max(Bitmaps.roundToInt(flow * 255), 1), 255)
        return { c in
            if let texture {
                c.beginTransparencyLayer(auxiliaryInfo: nil)
                drawDabs(c, buckets, color: color, baseAlpha: alpha)
                c.setBlendMode(.destinationIn)
                Bitmaps.tile(c, texture, scale: textureScale, interpolate: false)
                c.endTransparencyLayer()
            } else {
                drawDabs(c, buckets, color: color, baseAlpha: alpha)
            }
        }
    }

    /// Kâğıda sabit doku maskesiyle (Android `withFlow`/doku gölgelendiricisi) çizim: gövde opak çizilir, doku dışı silinir.
    static func masked(_ c: CGContext, _ mask: CGImage?, scale: CGFloat, _ body: () -> Void) {
        guard let mask else { body(); return }
        c.beginTransparencyLayer(auxiliaryInfo: nil)
        body()
        c.setBlendMode(.destinationIn)
        Bitmaps.tile(c, mask, scale: scale, interpolate: false)
        c.endTransparencyLayer()
    }

    /// Bulanık kenarlı dolgu (Android `BlurMaskFilter.NORMAL`): gölge hilesiyle (şekil görünmez, yalnız gölgesi).
    static func fillBlurred(_ c: CGContext, _ path: CGPath, color: CGColor, blur: CGFloat) {
        if blur < 0.5 {
            c.setFillColor(color); c.addPath(path); c.fillPath(); return
        }
        let box = path.boundingBoxOfPath
        let off = max(box.width, 1) + blur * 4 + 2000
        let m = c.ctm
        c.saveGState()
        let scale = (m.a * m.a + m.b * m.b).squareRoot()
        c.setShadow(offset: CGSize(width: off * m.a, height: off * m.b), blur: blur * scale * 1.2, color: color)
        c.translateBy(x: -off, y: 0)
        c.setFillColor(color)
        c.addPath(path)
        c.fillPath()
        c.restoreGState()
    }
}

/// Bir fırça izinin bitmiş çizimi (Android `StrokeStream` alt sınıflarının `drawStatic` yolu).
final class StaticBrush {
    let type: BrushType
    let color: UInt32
    let opacity: Float
    let seed: Int32
    let strength: Float
    let params: BrushParams?
    let style: BrushStyle
    let width: Float
    let flowA: Float
    let flowInk: Float
    let strokeAlpha: Float
    let isEraser: Bool
    let opaqueColor: UInt32
    let dynamics: BrushDynamics
    let px: [Float], py: [Float]
    let count: Int
    let pp: [Float]?, pt: [Float]?, po: [Float]?
    var hasDynamics: Bool { pp != nil || pt != nil }

    private let extrude: Depth3D?
    let extrudeLength: Float
    private let extrudeDir: P?
    var hasExtrusion: Bool { extrudeLength > 0.5 }

    init(type: BrushType, color: UInt32, sizePx: Float, opacity: Float, seed: Int32, flow: Float, strength: Float,
         params: BrushParams?, xs: [Float], ys: [Float], count: Int, pp: [Float]?, pt: [Float]?, po: [Float]?) {
        self.type = type; self.color = color; self.opacity = opacity; self.seed = seed; self.strength = strength
        self.params = params
        style = type.style
        width = type.inkWidth(sizePx, strength: strength)
        flowA = min(max(flow, 0.02), 1)
        flowInk = flowA >= BrushPainter.FULL_FLOW ? 1 : 0.35 + 0.65 * flowA
        strokeAlpha = min(max(opacity * style.alphaFactor * pressureAlpha(strength), 0), 1)
        isEraser = type == .ERASER
        opaqueColor = color | 0xFF00_0000
        dynamics = BrushDynamics.of(type).with(params)
        px = xs; py = ys; self.count = count
        self.pp = pp; self.pt = pt; self.po = po
        if let d = params?.depth3d, d.depth > 0.01, type != .ERASER, type.style.engine != .TUBE { extrude = d } else { extrude = nil }
        extrudeLength = Iso3D.extrudeLength(extrude, width)
        extrudeDir = extrude.map { Iso3D.extrudeVector($0) }
    }

    // MARK: - Ortak

    /// v21 az boya dokusu (yol fırçaları) ve ölçeği.
    lazy var flowGrain: CGImage? = (isEraser || flowA >= BrushPainter.FULL_FLOW) ? nil : BrushTextures.flowGrain(flowA)
    var flowGrainScale: CGFloat { CGFloat(min(max(width / 24, 0.5), 1.5)) }

    var pad: Float { width * max(style.reach * 1.6, 0.62 * dynamics.maxWidth) + 4 }

    private func dynAt(_ a: [Float]?, _ raw: Int, _ def: Float) -> Float {
        guard let a, count > 0 else { return def }
        let i = min(max(raw, 0), count - 1)
        return i == 0 ? a[0] : (a[i - 1] + a[i]) * 0.5
    }

    func wf(_ raw: Int) -> Float { hasDynamics ? dynamics.width(dynAt(pp, raw, 1), dynAt(pt, raw, 0)) : 1 }
    func af(_ raw: Int) -> Float { hasDynamics ? dynamics.alpha(dynAt(pp, raw, 1), dynAt(pt, raw, 0)) : 1 }

    func orientAt(_ raw: Int) -> Float? {
        guard let a = po, count > 0 else { return nil }
        let v = a[min(max(raw, 0), count - 1)]
        return v == 0 ? nil : v * 2 * Float.pi
    }

    var engine: BrushEngine {
        let fullEraser = isEraser && flowA >= 0.99 && opacity >= 0.99
        if isEraser { return fullEraser ? .PATH : .DABS }
        return style.engine
    }

    var compositeAlpha: Float {
        switch engine {
        case .PATH: return isEraser ? 1 : strokeAlpha * flowInk
        case .TAPERED_PATH, .RIBBON: return strokeAlpha * flowInk
        case .PATTERN where style.pattern == .ZIGZAG: return strokeAlpha * flowInk
        case .BRISTLE: return strokeAlpha
        case .TUBE, .PAINT: return strokeAlpha * flowA * (tubeGlass ? 0.74 : 1)
        case .SURFACE: return strokeAlpha * flowInk
        default: return isEraser ? 1 : strokeAlpha
        }
    }

    var blend: BrushBlend { isEraser ? .srcOver : style.blendMode }

    var tubeParams: TubeParams { params?.tube ?? TubeParams() }
    var tubeGlass: Bool { style.engine == .TUBE && tubeParams.material == .GLASS }

    var shadowAlpha: Float {
        if style.engine == .TUBE && !isEraser {
            return tubeParams.shadow <= 0.001 ? 0 : (tubeGlass ? 0.2 : 0.34)
        }
        return hasExtrusion ? 1 : 0
    }

    var shadowTint: UInt32? {
        guard hasExtrusion else { return nil }
        func d(_ c: UInt32) -> Int { Bitmaps.roundToInt(Float(c) * 0.58) }
        return ArgbColor.rgb(d(opaqueColor >> 16 & 0xFF), d(opaqueColor >> 8 & 0xFF), d(opaqueColor & 0xFF))
    }

    // MARK: - Çizim

    func build() -> BrushChunk? {
        if count == 0 { return nil }
        switch engine {
        case .PATH: return buildPath()
        case .TAPERED_PATH: return buildTapered()
        case .RIBBON: return buildRibbon()
        case .BRISTLE: return buildBristle()
        case .TUBE: return TubeBuilder(self, paintMode: false).build()
        case .PAINT: return TubeBuilder(self, paintMode: true).build()
        case .PATTERN where style.pattern == .ZIGZAG: return buildZigzag()
        case .IMAGE: return nil
        default: return SampleDabBuilder(self).build()
        }
    }

    private func extruded(_ chunk: BrushChunk) -> BrushChunk {
        guard hasExtrusion, let dir = extrudeDir else { return chunk }
        let len = extrudeLength
        let reach = len * max(1, hypotf(dir.x, dir.y))
        let steps = min(max(Int((reach / max(0.8, min(width * 0.25, 3))).rounded(.up)), 2), 48)
        var b = chunk.bounds
        b = b.union(b.offsetBy(dx: CGFloat(dir.x * len), dy: CGFloat(dir.y * len)))
        let walls: (CGContext) -> Void = { c in
            for i in stride(from: steps, through: 1, by: -1) {
                let t = len * Float(i) / Float(steps)
                c.saveGState()
                c.translateBy(x: CGFloat(dir.x * t), y: CGFloat(dir.y * t))
                chunk.draw(c)
                c.restoreGState()
            }
        }
        return BrushChunk(bounds: b, ops: [{ c in chunk.draw(c) }], shadowOps: [walls])
    }

    func drawStatic(_ c: CGContext) {
        guard let raw = build() else { return }
        let chunk = extruded(raw)
        if chunk.bounds.isNull { return }
        let shadow = chunk.hasShadow && shadowAlpha > 0
        c.saveGState()
        defer { c.restoreGState() }
        c.clip(to: chunk.bounds.insetBy(dx: -2, dy: -2))
        if isEraser {
            chunk.draw(c)
            return
        }
        c.setAlpha(CGFloat(Bitmaps.roundToInt(min(max(compositeAlpha, 0), 1) * 255)) / 255)
        if blend == .multiply { c.setBlendMode(.multiply) }
        c.beginTransparencyLayer(auxiliaryInfo: nil)
        if shadow {
            c.saveGState()
            c.setAlpha(CGFloat(Bitmaps.roundToInt(min(max(shadowAlpha, 0), 1) * 255)) / 255)
            c.beginTransparencyLayer(auxiliaryInfo: nil)
            chunk.drawShadow(c)
            if let tint = shadowTint {
                c.setBlendMode(.sourceIn)
                c.setFillColor(ArgbColor.cgColor(tint))
                c.fill(chunk.bounds.insetBy(dx: -2, dy: -2))
            }
            c.endTransparencyLayer()
            c.restoreGState()
        }
        chunk.draw(c)
        c.endTransparencyLayer()
    }

    private func cgColor(_ alpha: Int = 255) -> CGColor { ArgbColor.cgColor(isEraser ? ArgbColor.black : opaqueColor, alpha: alpha) }

    // MARK: Yol (kalem, teknik kalem, marker, fosforlu, sert silgi)

    private func buildPath() -> BrushChunk {
        var bounds = BoundsBuilder()
        for i in 0..<count { bounds.add(px[i], py[i]) }
        let rect = bounds.rect(pad)
        let eraser = isEraser
        let color = cgColor()
        let grain = flowGrain, gs = flowGrainScale
        let w = width
        if hasDynamics {
            // Kalem basıncı: her parça kendi kalınlığında, yuvarlak uçlu ayrı çizgi
            if count == 1 {
                let x = px[0], y = py[0], r = CGFloat(width * wf(0) / 2)
                return BrushChunk(bounds: rect, ops: [{ c in
                    c.saveGState()
                    if eraser { c.setBlendMode(.destinationOut) }
                    BrushPainter.masked(c, grain, scale: gs) {
                        c.setFillColor(color)
                        c.fillEllipse(in: CGRect(x: CGFloat(x) - r, y: CGFloat(y) - r, width: 2 * r, height: 2 * r))
                    }
                    c.restoreGState()
                }])
            }
            var segs: [(CGPoint, CGPoint, CGFloat)] = []
            for j in 1..<count { segs.append((BrushPainter.pt(px[j - 1], py[j - 1]), BrushPainter.pt(px[j], py[j]), CGFloat(max(0.5, width * wf(j))))) }
            return BrushChunk(bounds: rect, ops: [{ c in
                c.saveGState()
                if eraser { c.setBlendMode(.destinationOut) }
                BrushPainter.masked(c, grain, scale: gs) {
                    c.setStrokeColor(color)
                    c.setLineCap(.round); c.setLineJoin(.round)
                    for s in segs {
                        c.setLineWidth(s.2)
                        c.move(to: s.0); c.addLine(to: s.1); c.strokePath()
                    }
                }
                c.restoreGState()
            }])
        }
        if count == 1 {
            let x = px[0], y = py[0], r = CGFloat(w / 2)
            return BrushChunk(bounds: rect, ops: [{ c in
                c.saveGState()
                if eraser { c.setBlendMode(.destinationOut) }
                BrushPainter.masked(c, grain, scale: gs) {
                    c.setFillColor(color)
                    c.fillEllipse(in: CGRect(x: CGFloat(x) - r, y: CGFloat(y) - r, width: 2 * r, height: 2 * r))
                }
                c.restoreGState()
            }])
        }
        let path = BrushPainter.buildPath(px, py, count, smooth: style.smooth)
        let cap: CGLineCap = eraser ? .round : lineCap(style.cap)
        let join = lineJoin(style.join)
        return BrushChunk(bounds: rect, ops: [{ c in
            c.saveGState()
            if eraser { c.setBlendMode(.destinationOut) }
            BrushPainter.masked(c, grain, scale: gs) {
                c.setStrokeColor(color)
                c.setLineWidth(CGFloat(w)); c.setLineCap(cap); c.setLineJoin(join)
                c.addPath(path); c.strokePath()
            }
            c.restoreGState()
        }])
    }

    func lineCap(_ cap: BrushCap) -> CGLineCap {
        switch cap { case .square: return .square; case .butt: return .butt; case .round: return .round }
    }

    func lineJoin(_ j: BrushJoin) -> CGLineJoin {
        switch j { case .bevel: return .bevel; case .miter: return .miter; case .round: return .round }
    }

    // MARK: Sivrilen mürekkep

    private var gapSums: [Float] = []

    private func speedFactor(_ raw: Int) -> Float {
        if hasDynamics { return wf(raw) }
        if raw < 1 || raw >= count { return 1 }
        if gapSums.count < count {
            gapSums = [Float](repeating: 0, count: count)
            for i in 1..<count { gapSums[i] = gapSums[i - 1] + hypotf(px[i] - px[i - 1], py[i] - py[i - 1]) }
        }
        let gap = gapSums[raw] - gapSums[raw - 1]
        let mean = max(0.5, gapSums[raw] / Float(raw))
        let speed = gap / mean
        return min(max(1.15 - 0.3 * (speed - 1), 0.55), 1.15)
    }

    private func buildTapered() -> BrushChunk {
        let sampler = CurveSampler(max(0.8, width * 0.2))
        sampler.finish(px, py, count)
        let s = sampler.samples
        var bounds = BoundsBuilder()
        for i in 0..<s.size { bounds.add(s.x[i], s.y[i]) }
        let rect = bounds.rect(pad)
        let color = cgColor(), grain = flowGrain, gs = flowGrainScale
        if count == 1 || s.size < 2 {
            let x = s.x[0], y = s.y[0], r = CGFloat(width * 0.35)
            return BrushChunk(bounds: rect, ops: [{ c in
                BrushPainter.masked(c, grain, scale: gs) {
                    c.setFillColor(color)
                    c.fillEllipse(in: CGRect(x: CGFloat(x) - r, y: CGFloat(y) - r, width: 2 * r, height: 2 * r))
                }
            }])
        }
        let length = sampler.finalLength
        let maxTaper = width * 4
        let halves = (0..<s.size).map { i -> Float in
            let taper = max(min(length * 0.3, maxTaper), 1)
            let along = s.along[i]
            let ends = max(min(1, min(along, length - along) / taper), 0)
            return width / 2 * speedFactor(s.raw[i]) * (0.15 + 0.85 * ends.squareRoot())
        }
        let path = CGMutablePath()
        for i in 1..<s.size {
            let an = halves[i - 1], bn = halves[i]
            let ax = s.x[i - 1], ay = s.y[i - 1], bx = s.x[i], by = s.y[i]
            path.move(to: BrushPainter.pt(ax - s.dy[i - 1] * an, ay + s.dx[i - 1] * an))
            path.addLine(to: BrushPainter.pt(bx - s.dy[i] * bn, by + s.dx[i] * bn))
            path.addLine(to: BrushPainter.pt(bx + s.dy[i] * bn, by - s.dx[i] * bn))
            path.addLine(to: BrushPainter.pt(ax + s.dy[i - 1] * an, ay - s.dx[i - 1] * an))
            path.closeSubpath()
        }
        return BrushChunk(bounds: rect, ops: [{ c in
            BrushPainter.masked(c, grain, scale: gs) {
                c.setFillColor(color); c.addPath(path); c.fillPath(using: .winding)
            }
        }])
    }

    // MARK: Kaligrafi

    private func buildRibbon() -> BrushChunk {
        let sampler = CurveSampler(max(0.8, width * 0.15))
        sampler.finish(px, py, count)
        let s = sampler.samples
        let nbx = 0.7071 * width / 2, nby = -0.7071 * width / 2
        let nibFollow = params?.nibFollow == true
        func nibX(_ raw: Int) -> Float { if nibFollow, let o = orientAt(raw) { return cos(o) * width / 2 }; return nbx }
        func nibY(_ raw: Int) -> Float { if nibFollow, let o = orientAt(raw) { return sin(o) * width / 2 }; return nby }
        let path = CGMutablePath()
        func quad(_ ax: Float, _ ay: Float, _ bx: Float, _ by: Float, _ fa: Float, _ fb: Float, _ nax: Float, _ nay: Float, _ nbX: Float, _ nbY: Float) {
            let p0x = ax + nax * fa, p0y = ay + nay * fa, p1x = bx + nbX * fb, p1y = by + nbY * fb
            let p2x = bx - nbX * fb, p2y = by - nbY * fb, p3x = ax - nax * fa, p3y = ay - nay * fa
            var area: Float = 0
            area += p0x * p1y - p1x * p0y; area += p1x * p2y - p2x * p1y; area += p2x * p3y - p3x * p2y; area += p3x * p0y - p0x * p3y
            path.move(to: BrushPainter.pt(p0x, p0y))
            if area < 0 {
                path.addLine(to: BrushPainter.pt(p3x, p3y)); path.addLine(to: BrushPainter.pt(p2x, p2y)); path.addLine(to: BrushPainter.pt(p1x, p1y))
            } else {
                path.addLine(to: BrushPainter.pt(p1x, p1y)); path.addLine(to: BrushPainter.pt(p2x, p2y)); path.addLine(to: BrushPainter.pt(p3x, p3y))
            }
            path.closeSubpath()
        }
        var bounds = BoundsBuilder()
        if s.size == 1 { quad(s.x[0] - 0.5, s.y[0], s.x[0] + 0.5, s.y[0], 1, 1, nbx, nby, nbx, nby) }
        if s.size > 1 {
            for i in 1..<s.size {
                let a = s.raw[i - 1], b = s.raw[i]
                quad(s.x[i - 1], s.y[i - 1], s.x[i], s.y[i], wf(a), wf(b), nibX(a), nibY(a), nibX(b), nibY(b))
            }
        }
        for i in 0..<s.size { bounds.add(s.x[i], s.y[i]) }
        let center = BrushPainter.buildPath(px, py, count, smooth: true)
        let lineWidth = CGFloat(max(1, width * 0.12))
        let color = cgColor(), grain = flowGrain, gs = flowGrainScale
        return BrushChunk(bounds: bounds.rect(pad), ops: [{ c in
            BrushPainter.masked(c, grain, scale: gs) {
                c.setFillColor(color); c.addPath(path); c.fillPath(using: .winding)
                c.setStrokeColor(color); c.setLineWidth(lineWidth); c.setLineCap(.round); c.setLineJoin(.round)
                c.addPath(center); c.strokePath()
            }
        }])
    }

    // MARK: Zikzak

    private func buildZigzag() -> BrushChunk? {
        let sampler = CurveSampler(max(1.5, width * min(max(params?.spacing ?? 0.55, 0.1), 3)))
        sampler.finish(px, py, count)
        let s = sampler.samples
        if s.size < 2 { return nil }
        let path = CGMutablePath()
        var bounds = BoundsBuilder()
        for i in 0..<s.size {
            let side: Float = s.index[i] % 2 == 0 ? 1 : -1
            let x = s.x[i] - s.dy[i] * width * 0.45 * side * wf(s.raw[i])
            let y = s.y[i] + s.dx[i] * width * 0.45 * side * wf(s.raw[i])
            if i == 0 { path.move(to: BrushPainter.pt(x, y)) } else { path.addLine(to: BrushPainter.pt(x, y)) }
            bounds.add(x, y)
        }
        let lineWidth = CGFloat(max(1, width * 0.16))
        let color = cgColor(), grain = flowGrain, gs = flowGrainScale
        return BrushChunk(bounds: bounds.rect(pad), ops: [{ c in
            BrushPainter.masked(c, grain, scale: gs) {
                c.setStrokeColor(color); c.setLineWidth(lineWidth); c.setLineJoin(.miter); c.setLineCap(.square)
                c.addPath(path); c.strokePath()
            }
        }])
    }

    // MARK: Yassı fırça

    private func buildBristle() -> BrushChunk {
        let sampler = CurveSampler(max(0.8, width * 0.2))
        sampler.finish(px, py, count)
        let s = sampler.samples
        let bristles = min(max(Bitmaps.roundToInt(width / 2.2), 5), 16)
        let halfWidth = max(0.8, width / Float(bristles) * 2.1) / 2
        var offsets = [Float](repeating: 0, count: bristles)
        var dryStart = [Float](repeating: 0, count: bristles), dryEnd = [Float](repeating: 0, count: bristles)
        var alphas = [Int](repeating: 0, count: bristles)
        for b in 0..<bristles {
            offsets[b] = ((Float(b) + 0.5) / Float(bristles) - 0.5) * width + DabRandom.gaussian(seed, b, 100) * width * 0.02
            let edge = abs(Float(b) / (Float(bristles) - 1) - 0.5) * 2
            let load = (0.55 + 0.45 * DabRandom.unit(seed, b, 101)) * (1 - edge * (1 - strength) * 0.6)
            if DabRandom.unit(seed, b, 102) < 0.25 {
                dryStart[b] = DabRandom.unit(seed, b, 103) * width * 40
                dryEnd[b] = dryStart[b] + width * (1 + 2.5 * DabRandom.unit(seed, b, 104))
            } else {
                dryStart[b] = .greatestFiniteMagnitude; dryEnd[b] = .greatestFiniteMagnitude
            }
            alphas[b] = min(max(Bitmaps.roundToInt(flowA * load * 255), 1), 255)
        }
        func dry(_ b: Int, _ along: Float) -> Bool { along >= dryStart[b] && along <= dryEnd[b] }
        let paths: [CGMutablePath] = (0..<bristles).map { _ in CGMutablePath() }
        var bounds = BoundsBuilder()
        let single = s.size == 1
        for b in 0..<bristles {
            let o = offsets[b]
            if single {
                let x = s.x[0], y = s.y[0] + o
                paths[b].addRect(CGRect(x: CGFloat(x - 0.5), y: CGFloat(y - halfWidth), width: 1, height: CGFloat(2 * halfWidth)))
                bounds.add(x, y)
                continue
            }
            for i in 1..<s.size {
                if dry(b, s.along[i]) || dry(b, s.along[i - 1]) { continue }
                let fa = wf(s.raw[i - 1]), fb = wf(s.raw[i])
                let ax = s.x[i - 1] - s.dy[i - 1] * o * fa, ay = s.y[i - 1] + s.dx[i - 1] * o * fa
                let bx = s.x[i] - s.dy[i] * o * fb, by = s.y[i] + s.dx[i] * o * fb
                let anx = -s.dy[i - 1] * halfWidth * fa, any = s.dx[i - 1] * halfWidth * fa
                let bnx = -s.dy[i] * halfWidth * fb, bny = s.dx[i] * halfWidth * fb
                paths[b].move(to: BrushPainter.pt(ax + anx, ay + any))
                paths[b].addLine(to: BrushPainter.pt(bx + bnx, by + bny))
                paths[b].addLine(to: BrushPainter.pt(bx - bnx, by - bny))
                paths[b].addLine(to: BrushPainter.pt(ax - anx, ay - any))
                paths[b].closeSubpath()
            }
        }
        for i in 0..<s.size { bounds.add(s.x[i], s.y[i]) }
        let col = opaqueColor
        let frozen = paths
        return BrushChunk(bounds: bounds.rect(pad), ops: [{ c in
            for b in 0..<bristles where !frozen[b].isEmpty {
                c.setFillColor(ArgbColor.cgColor(col, alpha: alphas[b]))
                c.addPath(frozen[b]); c.fillPath(using: .winding)
            }
        }])
    }
}
