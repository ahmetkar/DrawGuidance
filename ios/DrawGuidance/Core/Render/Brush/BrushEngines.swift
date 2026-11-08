import CoreGraphics
import Foundation

/// Damga tabanlı motorlar (Android `SampleDabStream`): sert/yumuşak damga, şekil, sıçratma, raster, desen, doku, ıslak iz
/// ve kısmi silgi. Çizim bitmiş hâliyle tek parça olarak kurulur.
struct SampleDabBuilder {
    static let ALPHA_LEVELS = 6
    static let MIN_SPACING: Float = 0.02
    static let MAX_SPACING: Float = 3

    let b: StaticBrush
    let engine: BrushEngine
    let flowSpacing: Float
    let step: Float
    let textureLevel: Int
    let halftoneLevel: Int

    init(_ b: StaticBrush) {
        self.b = b
        let st = b.style
        engine = b.isEraser ? .DABS : st.engine
        let override: Float? = b.isEraser ? nil : b.params?.spacing.map { min(max($0, Self.MIN_SPACING), Self.MAX_SPACING) }
        flowSpacing = override ?? st.spacing
        let w = b.width
        if b.isEraser { step = max(1, w * 0.25) }
        else if let o = override { step = max(0.5, w * o) }
        else {
            switch engine {
            case .DABS: step = max(0.6, w * st.spacing)
            case .SOFT_DABS: step = max(0.8, w * st.spacing)
            case .STAMP_SHAPE: step = max(1, w * st.spacing)
            case .SCATTER: step = max(1, w * 0.45)
            case .HALFTONE, .SURFACE, .TRAIL: step = max(0.6, w * st.spacing)
            default:
                switch st.pattern {
                case .DOTS: step = max(2, w * 1.6)
                case .DOT_TRAIL: step = max(3, w * 2.2)
                default: step = max(0.6, w * 0.15)
                }
            }
        }
        textureLevel = Bitmaps.roundToInt(min(max(b.strength, 0), 1) * 4)
        halftoneLevel = Bitmaps.roundToInt(min(max(b.strength, 0), 1) * 7)
    }

    func build() -> BrushChunk? {
        let sampler = CurveSampler(step)
        sampler.finish(b.px, b.py, b.count)
        let s = sampler.samples
        if s.size == 0 { return nil }
        var bounds = BoundsBuilder()
        for i in 0..<s.size { bounds.add(s.x[i], s.y[i]) }
        var ops: [(CGContext) -> Void] = []
        if b.isEraser { buildEraser(s, &ops) } else {
            switch engine {
            case .DABS: buildDabs(s, &ops)
            case .SOFT_DABS: buildSoft(s, &ops)
            case .STAMP_SHAPE: buildShapes(s, &ops)
            case .SCATTER: buildScatter(s, &ops)
            case .HALFTONE: buildHalftone(s, &ops)
            case .PATTERN: buildPattern(s, &ops)
            case .SURFACE: buildSurface(s, &ops)
            case .TRAIL: buildTrail(s, &ops, finalIndex: s.size - 1)
            default: break
            }
        }
        return BrushChunk(bounds: bounds.rect(b.pad), ops: ops)
    }

    private var color: UInt32 { b.opaqueColor }
    private var width: Float { b.width }
    private var seed: Int32 { b.seed }

    private func buildEraser(_ s: SampleBuffer, _ ops: inout [(CGContext) -> Void]) {
        let alpha = min(max(Bitmaps.roundToInt(b.flowA * b.opacity * 255), 1), 255)
        var buckets = DabBuckets(width)
        for i in 0..<s.size { buckets.add(s.x[i], s.y[i], width * b.wf(s.raw[i])) }
        ops.append { c in
            c.saveGState()
            c.setBlendMode(.destinationOut)
            BrushPainter.drawDabs(c, buckets, color: ArgbColor.black, baseAlpha: alpha)
            c.restoreGState()
        }
    }

    private func buildDabs(_ s: SampleBuffer, _ ops: inout [(CGContext) -> Void]) {
        let st = b.style
        let texture = st.texture.map { BrushTextures.grain($0, textureLevel) }
        if b.type == .CHARCOAL {
            var smudge = DabBuckets(width)
            for i in 0..<s.size where s.index[i] % 3 == 0 { smudge.add(s.x[i], s.y[i], width * 0.9 * b.wf(s.raw[i])) }
            if !smudge.isEmpty { ops.append(BrushPainter.texturedDabs(smudge, color: color, flow: b.flowA * 0.08, texture: nil)) }
        }
        var main = DabBuckets(width, alphaLevels: b.hasDynamics ? Self.ALPHA_LEVELS : 1)
        for i in 0..<s.size {
            let k = s.index[i]
            let w = width * b.wf(s.raw[i])
            let jx = st.jitter > 0 ? DabRandom.gaussian(seed, k, 1) * st.jitter * w : 0
            let jy = st.jitter > 0 ? DabRandom.gaussian(seed, k, 2) * st.jitter * w : 0
            let d = w * (1 - st.sizeJitter * DabRandom.unit(seed, k, 3))
            main.add(s.x[i] + jx, s.y[i] + jy, d, alpha: b.af(s.raw[i]))
        }
        ops.append(BrushPainter.texturedDabs(main, color: color, flow: BrushPainter.dabFlow(b.flowA, flowSpacing), texture: texture))
        if b.type == .PASTEL {
            var crumbs = DabBuckets(width * 0.14)
            let chance = min(2.5 * step / max(1, width), 1)
            for i in 0..<s.size {
                let k = s.index[i]
                if DabRandom.unit(seed, k, 4) >= chance { continue }
                let side: Float = DabRandom.unit(seed, k, 5) < 0.5 ? 1 : -1
                let w = width * b.wf(s.raw[i])
                let dist = w * (0.45 + 0.3 * DabRandom.unit(seed, k, 6)) * side
                crumbs.add(s.x[i] - s.dy[i] * dist, s.y[i] + s.dx[i] * dist, w * (0.05 + 0.1 * DabRandom.unit(seed, k, 7)))
            }
            if !crumbs.isEmpty { ops.append(BrushPainter.texturedDabs(crumbs, color: color, flow: b.flowA * 0.85, texture: nil)) }
        }
    }

    private func buildSoft(_ s: SampleBuffer, _ ops: inout [(CGContext) -> Void]) {
        let spray = b.type == .AIRBRUSH
        let stamp = BrushTextures.tintedSoftStamp(b.style.hardness, color)
        let baseAlpha = min(max(Bitmaps.roundToInt(BrushPainter.dabFlow(b.flowA * (spray ? 0.85 : 0.95), flowSpacing) * 255), 1), 255)
        let r = width / 2
        var rects: [CGRect] = []
        var alphas: [CGFloat] = []
        rects.reserveCapacity(s.size); alphas.reserveCapacity(s.size)
        for i in 0..<s.size {
            let ri = r * b.wf(s.raw[i])
            rects.append(CGRect(x: CGFloat(s.x[i] - ri), y: CGFloat(s.y[i] - ri), width: CGFloat(2 * ri), height: CGFloat(2 * ri)))
            let a = b.hasDynamics ? min(max(Bitmaps.roundToInt(Float(baseAlpha) * b.af(s.raw[i])), 1), 255) : baseAlpha
            alphas.append(CGFloat(a) / 255)
        }
        ops.append { c in
            c.saveGState()
            c.interpolationQuality = .default
            for i in 0..<rects.count {
                c.setAlpha(alphas[i])
                c.draw(stamp, in: rects[i])
            }
            c.restoreGState()
        }
        if spray {
            var speckles = DabBuckets(max(1, width * 0.03))
            for i in 0..<s.size {
                let k = s.index[i]
                for j in 0..<6 {
                    let g = DabRandom.gaussian(seed, k, 20 + j * 3) * r * 0.45
                    let h = DabRandom.gaussian(seed, k, 21 + j * 3) * r * 0.45
                    speckles.add(s.x[i] + g, s.y[i] + h, max(0.8, width * (0.02 + 0.03 * DabRandom.unit(seed, k, 22 + j * 3))))
                }
            }
            ops.append(BrushPainter.texturedDabs(speckles, color: color, flow: b.flowA * 0.5, texture: nil))
        }
    }

    private func buildShapes(_ s: SampleBuffer, _ ops: inout [(CGContext) -> Void]) {
        let st = b.style
        guard let shape = st.shape else { return }
        let unit = BrushTextures.shapePath(shape)
        let fill = ArgbColor.cgColor(color, alpha: min(max(Bitmaps.roundToInt(b.flowA * 255), 1), 255))
        var paths: [CGPath] = []
        for i in 0..<s.size {
            let k = s.index[i]
            let w = width * b.wf(s.raw[i])
            let size = w * (1 - st.sizeJitter * DabRandom.unit(seed, k, 3))
            let angle: Float
            switch shape {
            case .STAR: angle = DabRandom.unit(seed, k, 8) * 72
            case .HEART: angle = DabRandom.gaussian(seed, k, 8) * 10
            case .LEAF: angle = atan2(s.dy[i], s.dx[i]) * 180 / Float.pi + DabRandom.gaussian(seed, k, 8) * 25
            }
            let jx = DabRandom.gaussian(seed, k, 1) * st.jitter * w
            let jy = DabRandom.gaussian(seed, k, 2) * st.jitter * w
            var m = CGAffineTransform(scaleX: CGFloat(size), y: CGFloat(size))
                .concatenating(CGAffineTransform(rotationAngle: CGFloat(angle) * .pi / 180))
                .concatenating(CGAffineTransform(translationX: CGFloat(s.x[i] + jx), y: CGFloat(s.y[i] + jy)))
            if let p = unit.copy(using: &m) { paths.append(p) }
        }
        ops.append { c in
            c.setFillColor(fill)
            for p in paths { c.addPath(p); c.fillPath() }
        }
    }

    private func buildScatter(_ s: SampleBuffer, _ ops: inout [(CGContext) -> Void]) {
        let st = b.style
        var buckets = DabBuckets(width * 0.5)
        for i in 0..<s.size {
            let k = s.index[i]
            let w = width * b.wf(s.raw[i])
            for j in 0..<3 {
                let big = DabRandom.unit(seed, k, 40 + j * 5) < 0.08
                let v = DabRandom.unit(seed, k, 41 + j * 5)
                let d = big ? w * (0.3 + 0.2 * v) : w * (0.06 + 0.12 * v)
                let spread = st.jitter * w * abs(DabRandom.gaussian(seed, k, 42 + j * 5)) * 0.6
                let a = DabRandom.unit(seed, k, 43 + j * 5) * 2 * Float.pi
                buckets.add(s.x[i] + cos(a) * spread, s.y[i] + sin(a) * spread, d)
            }
        }
        ops.append(BrushPainter.texturedDabs(buckets, color: color, flow: b.flowA, texture: nil))
    }

    private func buildHalftone(_ s: SampleBuffer, _ ops: inout [(CGContext) -> Void]) {
        guard let kind = b.style.halftone else { return }
        let pitch = min(max(Bitmaps.roundToInt(width * 0.22), 3), 40)
        let tile = BrushTextures.halftone(kind, pitch, halftoneLevel)
        var buckets = DabBuckets(width)
        for i in 0..<s.size { buckets.add(s.x[i], s.y[i], width * b.wf(s.raw[i])) }
        ops.append(BrushPainter.texturedDabs(buckets, color: color, flow: BrushPainter.dabFlow(b.flowA, flowSpacing), texture: tile))
    }

    private func buildPattern(_ s: SampleBuffer, _ ops: inout [(CGContext) -> Void]) {
        switch b.style.pattern {
        case .DOTS:
            var buckets = DabBuckets(width * 0.62)
            for i in 0..<s.size { buckets.add(s.x[i], s.y[i], width * 0.62 * b.wf(s.raw[i])) }
            ops.append(BrushPainter.texturedDabs(buckets, color: color, flow: b.flowA, texture: nil))
        case .DOT_TRAIL:
            var buckets = DabBuckets(width)
            for i in 0..<s.size { buckets.add(s.x[i], s.y[i], width * b.wf(s.raw[i])) }
            ops.append(BrushPainter.texturedDabs(buckets, color: color, flow: b.flowA, texture: nil))
        case .HATCH:
            let pitch = min(max(Bitmaps.roundToInt(width * 0.3), 3), 24)
            var buckets = DabBuckets(width)
            for i in 0..<s.size { buckets.add(s.x[i], s.y[i], width * b.wf(s.raw[i])) }
            ops.append(BrushPainter.texturedDabs(buckets, color: color, flow: BrushPainter.dabFlow(b.flowA, flowSpacing),
                                                 texture: BrushTextures.hatch(pitch)))
        default: break
        }
    }

    private func buildSurface(_ s: SampleBuffer, _ ops: inout [(CGContext) -> Void]) {
        guard let kind = b.style.surface else { return }
        let tile = SurfaceTextures.tile(kind, color)
        let scale = CGFloat(SurfaceTextures.scaleFor(kind, width))
        let grain = b.flowGrain, gs = b.flowGrainScale
        var buckets = DabBuckets(width, alphaLevels: b.hasDynamics ? Self.ALPHA_LEVELS : 1)
        for i in 0..<s.size { buckets.add(s.x[i], s.y[i], width * b.wf(s.raw[i]), alpha: b.af(s.raw[i])) }
        ops.append { c in
            c.beginTransparencyLayer(auxiliaryInfo: nil)
            BrushPainter.drawDabs(c, buckets, color: ArgbColor.black, baseAlpha: 255)
            c.setBlendMode(.sourceIn)
            Bitmaps.tile(c, tile, scale: scale, interpolate: true)
            if let grain {
                c.setBlendMode(.destinationIn)
                Bitmaps.tile(c, grain, scale: gs, interpolate: false)
            }
            c.endTransparencyLayer()
        }
    }

    private func buildTrail(_ s: SampleBuffer, _ ops: inout [(CGContext) -> Void], finalIndex: Int) {
        var body = DabBuckets(width)
        var extra = DabBuckets(width * 0.5)
        let inkBlotChance = min(0.35 * step / max(1, width), 1)
        let inkDropChance = min(0.6 * step / max(1, width), 1)
        let dripChance = min(step / max(1, width * 1.6), 1)
        for i in 0..<s.size {
            let k = s.index[i]
            let w = width * b.wf(s.raw[i])
            switch b.style.trail {
            case .INK:
                let wave = 0.82 + 0.3 * smoothNoise(s.along[i] / max(2, width * 0.8), 51)
                body.add(s.x[i], s.y[i], w * wave)
                if k < 3 { body.add(s.x[i], s.y[i], w * (1.35 - 0.1 * Float(k))) }
                if k == finalIndex { body.add(s.x[i], s.y[i], w * 1.3) }
                if DabRandom.unit(seed, k, 52) < inkBlotChance {
                    let a = DabRandom.unit(seed, k, 53) * 2 * Float.pi
                    let d = w * 0.22 * DabRandom.unit(seed, k, 54)
                    body.add(s.x[i] + cos(a) * d, s.y[i] + sin(a) * d, w * (1.15 + 0.45 * DabRandom.unit(seed, k, 55)))
                }
                if DabRandom.unit(seed, k, 56) < inkDropChance {
                    let side: Float = DabRandom.unit(seed, k, 57) < 0.5 ? 1 : -1
                    let dist = w * (0.75 + 0.75 * DabRandom.unit(seed, k, 58)) * side
                    extra.add(s.x[i] - s.dy[i] * dist, s.y[i] + s.dx[i] * dist, w * (0.07 + 0.16 * DabRandom.unit(seed, k, 59)))
                }
            case .DRIP:
                body.add(s.x[i], s.y[i], w * (0.92 + 0.12 * smoothNoise(s.along[i] / max(2, width * 0.6), 61)))
                if k == finalIndex { body.add(s.x[i], s.y[i], w * 1.15) }
                if DabRandom.unit(seed, k, 62) < dripChance {
                    addDrip(&extra, s.x[i] + (DabRandom.unit(seed, k, 63) - 0.5) * w * 0.6, s.y[i], w, k)
                }
            case nil:
                body.add(s.x[i], s.y[i], w)
            }
        }
        ops.append(BrushPainter.texturedDabs(body, color: color, flow: BrushPainter.dabFlow(b.flowA, flowSpacing), texture: nil))
        if !extra.isEmpty { ops.append(BrushPainter.texturedDabs(extra, color: color, flow: b.flowA, texture: nil)) }
    }

    private func addDrip(_ into: inout DabBuckets, _ x: Float, _ y: Float, _ w: Float, _ k: Int) {
        let u = DabRandom.unit(seed, k, 64)
        let length = w * (0.5 + (DRIP_MAX_LENGTH - 0.9) * u * u)
        let w0 = w * (0.22 + 0.2 * DabRandom.unit(seed, k, 65))
        var d: Float = 0
        let stepY = max(0.6, w0 * 0.3)
        while d <= length {
            let t = d / length
            into.add(x, y + d, w0 * (1 - 0.35 * t))
            d += stepY
        }
        into.add(x, y + length, w0 * 1.25)
    }

    private func smoothNoise(_ t: Float, _ salt: Int) -> Float {
        let f = t.rounded(.down)
        let a = DabRandom.unit(seed, Int(f), salt), bb = DabRandom.unit(seed, Int(f) + 1, salt)
        let u = t - f
        return a + (bb - a) * u * u * (3 - 2 * u)
    }
}

/// 3B tüp ve kalın boya izi (Android `TubeStream`, kayıtlı çizim yolu): kesit boyunca üst üste bantlar, ışıkla kayan
/// parlama, bulanık zemin gölgesi; boya izinde kıl şeritleri.
struct TubeBuilder {
    static let BANDS = 16
    static let PAINT_LANES = 15
    static let SHARP_TURN_COS: Float = 0.866
    static let WHITE: UInt32 = 0xFFFF_FFFF
    static let METAL_CURVE: [Float] = [0, 0.28, 0.28, 1.08, 0.52, 0.55, 0.78, 1.22, 1, 1.35]
    static let GLASS_CURVE: [Float] = [0, 0.5, 0.14, 0.95, 0.5, 1.12, 0.85, 1.2, 1, 1.3]

    let b: StaticBrush
    let paintMode: Bool
    let tube: TubeParams
    let material: TubeMaterial
    let relief: Float, gloss: Float
    let lx: Float, ly: Float
    let shadowDist: Float, blur: Float
    let glass: Bool
    let nBands: Int
    var bandHalf: [Float], bandOff: [Float], bandLat: [Float]
    var bandColor: [UInt32]

    init(_ b: StaticBrush, paintMode: Bool) {
        self.b = b
        self.paintMode = paintMode
        tube = b.params?.tube ?? TubeParams()
        material = tube.material
        relief = min(max(tube.relief, 0), 1)
        gloss = min(max(tube.gloss, 0), 1)
        shadowDist = paintMode ? 0 : min(max(tube.shadow, 0), 1) * TUBE_SHADOW_MAX * b.width
        blur = paintMode ? 0 : min(max(tube.shadowSoftness, 0), 1) * TUBE_BLUR_MAX * b.width
        glass = !paintMode && material == .GLASS
        nBands = paintMode ? 2 + Self.PAINT_LANES : Self.BANDS
        let rad = Double(tube.lightAngle) * Double.pi / 180
        lx = paintMode ? 0 : Float(cos(rad)); ly = paintMode ? 0 : Float(sin(rad))
        bandHalf = [Float](repeating: 0, count: nBands); bandOff = bandHalf; bandLat = bandHalf
        bandColor = [UInt32](repeating: 0, count: nBands)
        let base = b.opaqueColor
        if paintMode {
            bandHalf[0] = 1; bandColor[0] = Self.shade(base, 0.95)
            bandHalf[1] = 0.95; bandColor[1] = base
            for j in 0..<Self.PAINT_LANES {
                let k = 2 + j
                bandLat[k] = (DabRandom.unit(b.seed, j, 41) * 2 - 1) * 0.78
                bandHalf[k] = 0.025 + 0.055 * DabRandom.unit(b.seed, j, 42)
                let l: Float = DabRandom.unit(b.seed, j, 43) < 0.55 ? 0.86 + 0.09 * DabRandom.unit(b.seed, j, 44) : 1.05 + 0.1 * DabRandom.unit(b.seed, j, 45)
                bandColor[k] = Self.shade(base, l)
            }
        } else {
            let specTint = material == .METAL ? ArgbColor.mix(base, Self.WHITE, 0.72) : Self.WHITE
            for k in 0..<Self.BANDS {
                let u = Float(k) / Float(Self.BANDS - 1)
                bandHalf[k] = 1 - 0.88 * u
                bandOff[k] = u * (0.12 + 0.5 * relief)
                let diffuse = Self.shade(base, lum(u))
                bandColor[k] = ArgbColor.mix(diffuse, specTint, spec(u))
            }
        }
    }

    var pad: Float { (b.width * 0.55 + shadowDist) * b.dynamics.maxWidth + blur * 1.2 + 4 }

    private func halfAt(_ s: SampleBuffer, _ i: Int) -> Float {
        let h = b.width / 2 * b.wf(s.raw[i])
        if !paintMode { return h }
        let t = s.along[i] / max(2, b.width * 0.55)
        let f = t.rounded(.down)
        let a = DabRandom.unit(b.seed, Int(f), 47), c = DabRandom.unit(b.seed, Int(f) + 1, 47)
        let u = t - f
        let n = a + (c - a) * u * u * (3 - 2 * u)
        return h * (0.9 + 0.16 * n)
    }

    private func lum(_ u: Float) -> Float {
        switch material {
        case .MATTE: return 1 - 0.62 * relief * powf(1 - u, 1.5) + 0.16 * relief * u * u
        case .PLASTIC: return 1 - 0.58 * relief * powf(1 - u, 1.5) + 0.1 * relief * u * u
        case .METAL: return 1 + (Self.curve(u, Self.METAL_CURVE) - 1) * (0.35 + 0.65 * relief)
        case .GLASS: return 1 + (Self.curve(u, Self.GLASS_CURVE) - 1) * (0.35 + 0.65 * relief)
        }
    }

    private func spec(_ u: Float) -> Float {
        switch material {
        case .MATTE: return 0.12 * gloss * Self.smooth(0.7, 1, u)
        case .PLASTIC: return (0.3 + 0.62 * gloss) * Self.smooth(0.6 + 0.26 * gloss, 1, u)
        case .METAL: return (0.25 + 0.6 * gloss) * Self.smooth(0.78, 1, u)
        case .GLASS: return (0.55 + 0.45 * gloss) * Self.smooth(0.8, 1, u)
        }
    }

    private func bandPath(_ s: SampleBuffer, _ h: [Float], _ hf: Float, _ off: Float, _ dx: Float, _ dy: Float, lat: Float = 0) -> CGMutablePath {
        let path = CGMutablePath()
        if s.size < 2 { return path }
        for i in 1..<s.size {
            if abs(s.x[i] - s.x[i - 1]) + abs(s.y[i] - s.y[i - 1]) < 0.01 { continue }
            let ha = h[i - 1] * hf, hb = h[i] * hf
            let ax = s.x[i - 1] + (lx * off + dx - s.dy[i - 1] * lat) * h[i - 1], ay = s.y[i - 1] + (ly * off + dy + s.dx[i - 1] * lat) * h[i - 1]
            let bx = s.x[i] + (lx * off + dx - s.dy[i] * lat) * h[i], by = s.y[i] + (ly * off + dy + s.dx[i] * lat) * h[i]
            path.move(to: BrushPainter.pt(ax - s.dy[i - 1] * ha, ay + s.dx[i - 1] * ha))
            path.addLine(to: BrushPainter.pt(bx - s.dy[i] * hb, by + s.dx[i] * hb))
            path.addLine(to: BrushPainter.pt(bx + s.dy[i] * hb, by - s.dx[i] * hb))
            path.addLine(to: BrushPainter.pt(ax + s.dy[i - 1] * ha, ay - s.dx[i - 1] * ha))
            path.closeSubpath()
        }
        return path
    }

    private func capDisk(_ path: CGMutablePath, _ s: SampleBuffer, _ h: [Float], _ i: Int, _ hf: Float, _ off: Float, _ dx: Float, _ dy: Float, lat: Float = 0) {
        let cx = s.x[i] + (lx * off + dx - s.dy[i] * lat) * h[i]
        let cy = s.y[i] + (ly * off + dy + s.dx[i] * lat) * h[i]
        let r = CGFloat(max(0.3, h[i] * hf))
        path.addEllipse(in: CGRect(x: CGFloat(cx) - r, y: CGFloat(cy) - r, width: 2 * r, height: 2 * r))
    }

    private func sharpTurn(_ s: SampleBuffer, _ i: Int) -> Bool {
        i >= 1 && s.dx[i - 1] * s.dx[i] + s.dy[i - 1] * s.dy[i] < Self.SHARP_TURN_COS &&
            abs(s.x[i] - s.x[i - 1]) + abs(s.y[i] - s.y[i - 1]) >= 0.01
    }

    private func caps(_ s: SampleBuffer, _ h: [Float], _ hf: Float, _ dx: Float, _ dy: Float, _ off: Float, _ lat: Float = 0) -> CGMutablePath {
        let p = CGMutablePath()
        capDisk(p, s, h, 0, hf, off, dx, dy, lat: lat)
        capDisk(p, s, h, s.size - 1, hf, off, dx, dy, lat: lat)
        if s.size > 1 {
            for i in 1..<s.size where sharpTurn(s, i) {
                capDisk(p, s, h, i - 1, hf, off, dx, dy, lat: lat); capDisk(p, s, h, i, hf, off, dx, dy, lat: lat)
            }
        }
        return p
    }

    func build() -> BrushChunk? {
        let sampler = CurveSampler(max(0.6, min(b.width * 0.1, 6)))
        sampler.finish(b.px, b.py, b.count)
        let s = sampler.samples
        if s.size == 0 { return nil }
        let h = (0..<s.size).map { halfAt(s, $0) }
        var bounds = BoundsBuilder()
        for i in 0..<s.size { bounds.add(s.x[i], s.y[i]) }
        var bands: [(CGPath, CGColor)] = []
        for k in 0..<nBands {
            let path = bandPath(s, h, bandHalf[k], bandOff[k], 0, 0, lat: bandLat[k])
            path.addPath(caps(s, h, bandHalf[k], 0, 0, bandOff[k], bandLat[k]))
            bands.append((path, ArgbColor.cgColor(bandColor[k])))
        }
        if glass {
            let path = bandPath(s, h, 0.14, 0, -lx * 0.55, -ly * 0.55)
            path.addPath(caps(s, h, 0.14, -lx * 0.55, -ly * 0.55, 0))
            bands.append((path, ArgbColor.cgColor(ArgbColor.mix(b.opaqueColor, Self.WHITE, 0.45))))
        }
        let body: (CGContext) -> Void = { c in
            for (p, col) in bands {
                // Android: dörtgenler ve uç daireleri ayrı yollar; aynı renkte birleşik dolgu (WINDING) aynı kapsamayı verir
                c.setFillColor(col); c.addPath(p); c.fillPath(using: .winding)
            }
        }
        var shadow: [(CGContext) -> Void] = []
        if b.shadowAlpha > 0 {
            let k = shadowDist / max(0.5, b.width / 2)
            let path = bandPath(s, h, 1, 0, -lx * k, -ly * k)
            capDisk(path, s, h, 0, 1, 0, -lx * k, -ly * k)
            capDisk(path, s, h, s.size - 1, 1, 0, -lx * k, -ly * k)
            if s.size > 1 {
                for i in 1..<s.size where sharpTurn(s, i) {
                    capDisk(path, s, h, i - 1, 1, 0, -lx * k, -ly * k); capDisk(path, s, h, i, 1, 0, -lx * k, -ly * k)
                }
            }
            let blurR = CGFloat(blur)
            shadow.append { c in BrushPainter.fillBlurred(c, path, color: ArgbColor.cgColor(ArgbColor.black), blur: blurR) }
        }
        return BrushChunk(bounds: bounds.rect(pad), ops: [body], shadowOps: shadow)
    }

    static func curve(_ u: Float, _ pts: [Float]) -> Float {
        var i = 0
        while i + 2 < pts.count && u > pts[i + 2] { i += 2 }
        if i + 2 >= pts.count { return pts[pts.count - 1] }
        let u0 = pts[i], v0 = pts[i + 1], u1 = pts[i + 2], v1 = pts[i + 3]
        let t = u1 > u0 ? min(max((u - u0) / (u1 - u0), 0), 1) : 0
        return v0 + (v1 - v0) * t
    }

    static func smooth(_ a: Float, _ b: Float, _ x: Float) -> Float {
        let t = min(max((x - a) / max(1e-4, b - a), 0), 1)
        return t * t * (3 - 2 * t)
    }

    static func shade(_ c: UInt32, _ l: Float) -> UInt32 {
        if l <= 1 {
            let f = min(max(l, 0), 1)
            return ArgbColor.rgb(Bitmaps.roundToInt(Float(ArgbColor.red(c)) * f), Bitmaps.roundToInt(Float(ArgbColor.green(c)) * f),
                                 Bitmaps.roundToInt(Float(ArgbColor.blue(c)) * f))
        }
        return ArgbColor.mix(c, WHITE, min(max(l - 1, 0), 1))
    }
}

extension BrushTextures {
    private static let tinted = NSCache<NSString, CGImage>()

    /// Fırça renginde yumuşak damga (Android: beyaz damga + `SRC_IN` renk süzgeci).
    static func tintedSoftStamp(_ hardness: Float, _ color: UInt32) -> CGImage {
        let key = "\(Bitmaps.roundToInt(min(max(hardness, 0), 1) * 10))-\(color)" as NSString
        if let img = tinted.object(forKey: key) { return img }
        let stamp = softStamp(hardness)
        let img = Bitmaps.draw(stamp.width, stamp.height) { c in
            let r = CGRect(x: 0, y: 0, width: stamp.width, height: stamp.height)
            c.draw(stamp, in: r)
            c.setBlendMode(.sourceIn)
            c.setFillColor(ArgbColor.cgColor(color | 0xFF00_0000))
            c.fill(r)
        }
        tinted.setObject(img, forKey: key)
        return img
    }
}
