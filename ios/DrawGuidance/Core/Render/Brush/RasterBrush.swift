import Foundation

/// v51 piksel tabanlı fırça motoru (Android `RasterBrush`; aynı adımlar): boya fırçaları (akrilik, yağlı boya,
/// bulut/duman, karıştırıcı) ve yardımcı fırçalar (bulanıklaştır, keskinleştir, aç, koyulaştır, sünger, klon, onar).
///
/// Girdi, izin altındaki aynı katman içeriğinin ön çarpımlı RGBA görüntüsüdür (`Patch`); çıktı aynı bölge için sonuç
/// rengi ve karışım maskesidir: `ekran = alt × (1 − maske) + sonuç × maske`. Rastgelelik tuval koordinatından ve çizim
/// kimliğinden türetilir: aynı çizim her seferinde aynı görünür.
enum RasterBrush {

    /// Bir bölgenin pikselleri: sol-üst köşesi (tuval birimi), ölçek (piksel/tuval birimi), boyut ve ön çarpımlı RGBA (0..1).
    final class Patch {
        let left: Float, top: Float, scale: Float
        let w: Int, h: Int
        var rgba: [Float]

        init(left: Float, top: Float, scale: Float, w: Int, h: Int) {
            self.left = left; self.top = top; self.scale = scale; self.w = w; self.h = h
            rgba = [Float](repeating: 0, count: w * h * 4)
        }
    }

    /// Sonuç: ön çarpımlı renk (maske uygulanmamış) ve maske (0..1).
    struct Result {
        let patch: Patch
        let color: [Float]
        let mask: [Float]
    }

    private static func tip(_ minSize: Float, _ maxSize: Float, _ hardness: Float, _ softness: Float, _ aspect: Float, _ angle: Float,
                            _ rotation: Float, _ roundness: Float, _ shape: TipShape, _ edge: Float, _ density: Float, _ buildUp: Float,
                            _ wetness: Float, _ paintAmount: Float, _ thickness: Float, _ colorAmount: Float, _ smudge: Float,
                            _ blend: Float) -> TipParams {
        var t = TipParams()
        t.minSize = minSize; t.maxSize = maxSize; t.hardness = hardness; t.softness = softness; t.aspect = aspect; t.angle = angle
        t.rotation = rotation; t.roundness = roundness; t.shape = shape; t.edge = edge; t.density = density; t.buildUp = buildUp
        t.wetness = wetness; t.paintAmount = paintAmount; t.thickness = thickness; t.colorAmount = colorAmount; t.smudge = smudge
        t.blend = blend
        return t
    }

    /// Fırçanın kendi varsayılan uç ve boya ayarları (kullanıcı ayarı nil ise bunlar kullanılır).
    static func defaults(_ type: BrushType) -> TipParams {
        switch type {
        case .ACRYLIC: return tip(0.55, 1, 0.8, 0.1, 0.8, 0, 0, 0.6, .FLAT, 0.6, 0.95, 0, 0.1, 0.85, 0.3, 0.9, 0.15, 0.15)
        case .OIL_PAINT: return tip(0.5, 1, 0.7, 0.15, 0.75, 0, 0, 0.9, .BRISTLE, 0.5, 0.9, 0.2, 0.45, 0.75, 0.65, 0.75, 0.4, 0.35)
        case .CLOUD_SMOKE: return tip(0.6, 1, 0, 0.6, 1, 0, 1, 1, .CLOUD, 0.2, 0.7, 0.4, 0.3, 1, 0, 1, 0.1, 0.2)
        case .FX_MIXER: return tip(0.6, 1, 0.45, 0.3, 1, 0, 0, 1, .ROUND, 0.4, 1, 0, 0.7, 1, 0, 0.15, 0.75, 0.55)
        default: return tip(0.6, 1, 0.3, 0.3, 1, 0, 0, 1, .ROUND, 0.4, 1, 0, 0, 1, 0, 1, 0, 0)
        }
    }

    /// Kullanıcı ayarlarıyla birleşmiş etkin uç ayarları (hiç nil alan kalmaz).
    static func effectiveTip(_ type: BrushType, _ params: BrushParams?) -> TipParams {
        let d = defaults(type)
        guard let u = params?.tip else { return d }
        var t = d
        for (_, kp) in TipParams.floatKeys { t[keyPath: kp] = u[keyPath: kp] ?? d[keyPath: kp] }
        t.shape = u.shape ?? d.shape
        return t
    }

    /// Boya bırakan (karışım motoru) fırçalar; diğerleri süzgeç uygular.
    static func paints(_ type: BrushType) -> Bool {
        type == .ACRYLIC || type == .OIL_PAINT || type == .CLOUD_SMOKE || type == .FX_MIXER
    }

    /// Klon/onarma kaynağının hedefe göre kayması (tuval birimi).
    static func sourceOffset(_ type: BrushType, _ params: BrushParams?, _ width: Float) -> (Float, Float)? {
        guard type == .FX_CLONE || type == .FX_HEAL else { return nil }
        if let dx = params?.fx?.srcDx, let dy = params?.fx?.srcDy { return (dx, dy) }
        return (width * 1.2, width * 0.4)
    }

    /// Süzgeçlerin okuduğu ek pay (tuval birimi).
    static func readMargin(_ type: BrushType, _ width: Float, _ strength: Float) -> Float {
        switch type {
        case .FX_BLUR: return blurRadius(width, strength) * 3
        case .FX_SHARPEN: return width * 0.2
        case .FX_HEAL: return width * 1.2
        default: return 2
        }
    }

    private static func blurRadius(_ width: Float, _ strength: Float) -> Float { max(1, width * (0.06 + 0.14 * clamp01(strength))) }

    @inline(__always) static func clamp01(_ v: Float) -> Float { min(max(v, 0), 1) }
    @inline(__always) private static func clamp(_ v: Float, _ lo: Float, _ hi: Float) -> Float { min(max(v, lo), hi) }

    /// Kotlin `Float.toInt()` gibi doyuran tamsayı dönüşümü (NaN → 0).
    @inline(__always) static func toI(_ v: Float) -> Int32 {
        if v.isNaN { return 0 }
        if v >= 2_147_483_520 { return Int32.max }
        if v <= -2_147_483_648 { return Int32.min }
        return Int32(v)
    }

    @inline(__always) private static func toInt(_ v: Float) -> Int { Int(toI(v)) }

    /// İzi hesaplar. [base] izin altındaki içerik (boşsa şeffaf). Noktalar tuval biriminde; kalem dizileri yoksa nil.
    /// [color] fırça rengi (ARGB).
    static func render(_ base: Patch, type: BrushType, color: UInt32, sizePx: Float, opacity: Float, flow: Float, strength: Float,
                       seed: Int32, xs: [Float], ys: [Float], count: Int,
                       pp: [Float]?, pt: [Float]?, po: [Float]?, pr: [Float]?, pb: [Float]?, params: BrushParams?) -> Result {
        let w = base.w, h = base.h, s = base.scale
        let n = w * h
        let tip = effectiveTip(type, params)
        let style = type.style
        let width = type.inkWidth(sizePx, strength: strength)
        let pen = PenModel.of(params.map { p -> BrushParams in var q = p; q.tip = tip; return q })
        let data = PenModel.PointData(xs, ys, count, pp, pt, po, pr, pb, width: width)
        let dynamics = BrushDynamics.of(type).with(params)
        let hasPen = pp != nil || pt != nil
        let spacing = clamp(params?.spacing ?? style.spacing, 0.02, 3)
        let step = max(0.5 / s, width * spacing)
        let paints = paints(type)
        let thickness = tip.thickness ?? 0

        let mask = UnsafeMutablePointer<Float>.allocate(capacity: max(1, n)); mask.initialize(repeating: 0, count: max(1, n))
        let dep = UnsafeMutablePointer<Float>.allocate(capacity: max(1, n)); dep.initialize(repeating: 0, count: max(1, n))
        let cur = UnsafeMutablePointer<Float>.allocate(capacity: max(4, n * 4))
        if paints { base.rgba.withUnsafeBufferPointer { cur.initialize(from: $0.baseAddress!, count: n * 4) } }
        else { cur.initialize(repeating: 0, count: max(4, n * 4)) }
        let heightMap: UnsafeMutablePointer<Float>? = (paints && thickness > 0.01) ? {
            let p = UnsafeMutablePointer<Float>.allocate(capacity: max(1, n)); p.initialize(repeating: 0, count: max(1, n)); return p
        }() : nil
        defer {
            mask.deallocate(); dep.deallocate(); cur.deallocate(); heightMap?.deallocate()
        }

        let opacityCeil = clamp01(opacity * style.alphaFactor)
        let buildUp = tip.buildUp ?? 0
        let ceiling = opacityCeil + (1 - opacityCeil) * buildUp
        let dabAlpha = BrushPainter.dabFlow(clamp(flow, 0.02, 1), spacing) * (0.35 + 0.65 * clamp01(strength))
        let bc: [Float] = [Float((color >> 16) & 0xFF) / 255, Float((color >> 8) & 0xFF) / 255, Float(color & 0xFF) / 255]
        var carried: [Float] = [bc[0], bc[1], bc[2], 1]
        var carriedInit = false
        let shape = tip.shape ?? .ROUND
        let hardness = clamp01(tip.hardness ?? 0.5)
        let softness = clamp01(tip.softness ?? 0)
        let aspectRaw: Float
        switch shape {
        case .FLAT: aspectRaw = min(tip.aspect ?? 1, 0.35)
        case .CHISEL: aspectRaw = min(tip.aspect ?? 1, 0.55)
        default: aspectRaw = tip.aspect ?? 1
        }
        let aspect = clamp(aspectRaw, 0.05, 1)
        let roundness = clamp01(tip.roundness ?? 1)
        let edge = clamp01(tip.edge ?? 0.5)
        let density = clamp01(tip.density ?? 1)
        let wet = clamp01(tip.wetness ?? 0)
        let load0 = clamp01(tip.paintAmount ?? 1)
        let colorAmount = clamp01(tip.colorAmount ?? 1)
        let smudge = clamp01(tip.smudge ?? 0)
        let blend = clamp01(tip.blend ?? 0)
        let baseAngle = (tip.angle ?? 0) * Float.pi / 180
        let rotJitter = clamp01(tip.rotation ?? 0)
        let squareish = shape == .SQUARE || shape == .FLAT || shape == .CHISEL
        let exponent: Float = squareish ? 2 + (1 - roundness) * 14 : 2
        let outer: Float = 1 + softness * 0.6
        let inner = min(hardness * (1 - softness * 0.5), outer - 0.02)
        let overrides = pen?.overridesPressure == true

        var k = 0
        func dab(_ cx: Float, _ cy: Float, _ dx: Float, _ dy: Float, _ along: Float, _ raw: Int) {
            let baseW: Float = !hasPen ? 1 : dynamics.width(overrides ? 1 : data.pressure(raw), data.tilt(raw))
            let sizeF = pen?.size(baseW, data, raw) ?? baseW
            let alphaF = (!hasPen ? 1 : dynamics.alpha(data.pressure(raw), data.tilt(raw))) * (pen?.alpha(data, raw) ?? 1)
            let rx = max(0.5, width * 0.5 * sizeF * s)
            let ry = max(0.5, rx * aspect)
            var ang = baseAngle + (pen?.turn(data, raw, dx, dy) ?? 0)
            if rotJitter > 0 { ang += (DabRandom.unit(seed, k, 31) - 0.5) * 2 * Float.pi * rotJitter }
            let ca = cos(ang), sa = sin(ang)
            let load: Float = (paints && load0 < 0.999) ? max(0.06, exp(-along / max(1, width * (1.5 + 40 * load0)))) : 1
            let a0 = dabAlpha * alphaF * load
            if a0 <= 0.0005 { return }
            let px = (cx - base.left) * s, py = (cy - base.top) * s
            let reach = max(rx, ry) * outer + 1
            let x0 = max(0, toInt(floor(px - reach))), x1 = min(w - 1, toInt(ceil(px + reach)))
            let y0 = max(0, toInt(floor(py - reach))), y1 = min(h - 1, toInt(ceil(py + reach)))
            if x1 < x0 || y1 < y0 { return }
            // Boya: dokunulan yerin rengini al, taşınan rengi güncelle, fırça rengiyle karıştır
            var mr = bc[0], mg = bc[1], mb = bc[2]
            if paints {
                let pick = sampleAverage(cur, w, h, px, py, rx * 0.5)
                if pick.3 > 0.01 {
                    let ur = pick.0 / pick.3, ug = pick.1 / pick.3, ub = pick.2 / pick.3
                    if !carriedInit { carried[0] = ur; carried[1] = ug; carried[2] = ub; carriedInit = true }
                    let keep = smudge * 0.95
                    carried[0] = carried[0] * keep + ur * (1 - keep)
                    carried[1] = carried[1] * keep + ug * (1 - keep)
                    carried[2] = carried[2] * keep + ub * (1 - keep)
                    let pickupW = (1 - colorAmount) * pick.3
                    mr = bc[0] * (1 - pickupW) + carried[0] * pickupW
                    mg = bc[1] * (1 - pickupW) + carried[1] * pickupW
                    mb = bc[2] * (1 - pickupW) + carried[2] * pickupW
                    let wetMix = wet * 0.45 * pick.3
                    mr = mr * (1 - wetMix) + ur * wetMix; mg = mg * (1 - wetMix) + ug * wetMix; mb = mb * (1 - wetMix) + ub * wetMix
                }
            }
            let ndx: Float = (dx == 0 && dy == 0) ? 1 : dx
            let ndy = dy
            for yy in y0...y1 {
                let fy = Float(yy) + 0.5 - py
                for xx in x0...x1 {
                    let fx = Float(xx) + 0.5 - px
                    let lx = (fx * ca + fy * sa) / rx
                    let ly = (-fx * sa + fy * ca) / ry
                    let d: Float
                    switch shape {
                    case .SQUARE, .FLAT: d = powf(powf(abs(lx), exponent) + powf(abs(ly), exponent), 1 / exponent)
                    case .CHISEL: d = abs(lx) * 0.7 + abs(ly) * 0.7 + abs(lx - ly) * 0.3
                    default: d = (lx * lx + ly * ly).squareRoot()
                    }
                    if d >= outer { continue }
                    var c: Float
                    if d <= inner { c = 1 } else {
                        let t = clamp01((d - inner) / (outer - inner))
                        let ts = clamp01((t - 0.5) * (1 + edge * 6) + 0.5)
                        c = 1 - ts * ts * (3 - 2 * ts)
                    }
                    // Tuvale sabit doku ve uç dokusu
                    let gx = base.left + (Float(xx) + 0.5) / s, gy = base.top + (Float(yy) + 0.5) / s
                    switch shape {
                    case .BRISTLE:
                        let perp = (fx * -ndy + fy * ndx) / s / max(0.8, width * 0.045)
                        c *= 0.45 + 0.55 * hash1(toI(floor(perp)), seed)
                    case .SPONGE:
                        let sc = max(1, width * 0.12)
                        c *= smooth(0.3, 0.7, valueNoise(gx / sc, gy / sc, seed))
                    case .SPLATTER:
                        let sc = max(1, width * 0.08)
                        c *= hash2(toI(floor(gx / sc)), toI(floor(gy / sc)), seed &+ Int32(truncatingIfNeeded: k)) > 0.72 ? 1 : 0
                    case .CLOUD:
                        let sc = max(2, width * 0.35)
                        c *= smooth(0.2, 0.9, fbm(gx / sc, gy / sc, seed))
                    case .FLAT:
                        if type == .ACRYLIC {
                            let perp = (fx * -ndy + fy * ndx) / s / max(0.8, width * 0.06)
                            c *= 0.75 + 0.25 * hash1(toI(floor(perp)), seed)
                        }
                    default: break
                    }
                    if density < 0.999 { c *= smooth(1 - density - 0.12, 1 - density + 0.12, valueNoise(gx * 0.6, gy * 0.6, seed ^ 0x5bd1)) }
                    if c <= 0.001 { continue }
                    let i = yy * w + xx
                    var a = c * a0
                    if paints {
                        let room = ceiling - dep[i]
                        if room <= 0 { continue }
                        if a > room { a = room }
                        dep[i] += a
                        let o = i * 4
                        var tr = mr, tg = mg, tb = mb
                        let ca0 = cur[o + 3]
                        if blend > 0 && ca0 > 0.01 {
                            let bw = blend * 0.6 * ca0
                            tr = tr * (1 - bw) + cur[o] / ca0 * bw; tg = tg * (1 - bw) + cur[o + 1] / ca0 * bw; tb = tb * (1 - bw) + cur[o + 2] / ca0 * bw
                        }
                        cur[o] = tr * a + cur[o] * (1 - a)
                        cur[o + 1] = tg * a + cur[o + 1] * (1 - a)
                        cur[o + 2] = tb * a + cur[o + 2] * (1 - a)
                        cur[o + 3] = a + ca0 * (1 - a)
                        if let hm = heightMap { hm[i] += a * thickness }
                        mask[i] = 1
                    } else {
                        let m = mask[i]
                        mask[i] = min(ceiling, m + a * (1 - m))
                    }
                }
            }
            k += 1
        }

        if count == 1 {
            dab(xs[0], ys[0], 1, 0, 0, 0)
        } else if count > 1 {
            var acc: Float = 0
            var next: Float = 0
            for i in 1..<count {
                let ax = xs[i - 1], ay = ys[i - 1], bx = xs[i], by = ys[i]
                let len = hypotf(bx - ax, by - ay)
                if len <= 0 { continue }
                let ux = (bx - ax) / len, uy = (by - ay) / len
                while next <= acc + len {
                    let t = (next - acc) / len
                    dab(ax + (bx - ax) * t, ay + (by - ay) * t, ux, uy, next, i)
                    next += step
                }
                acc += len
            }
            if k == 0 { dab(xs[0], ys[0], 1, 0, 0, 0) }
        }

        let maskArr = Array(UnsafeBufferPointer(start: mask, count: n))
        let out: [Float]
        if paints {
            if let hm = heightMap { impasto(cur, hm, w, h, mask) }
            out = Array(UnsafeBufferPointer(start: cur, count: n * 4))
        } else {
            out = filter(type, base, params, width, strength)
        }
        return Result(patch: base, color: out, mask: maskArr)
    }

    /// Kalın boya: yükseklik haritasından sol-üstten gelen ışıkla kabartma gölgesi.
    private static func impasto(_ cur: UnsafeMutablePointer<Float>, _ hm: UnsafeMutablePointer<Float>, _ w: Int, _ h: Int,
                                _ mask: UnsafeMutablePointer<Float>) {
        guard w > 2, h > 2 else { return }
        for y in 1..<(h - 1) {
            for x in 1..<(w - 1) {
                let i = y * w + x
                if mask[i] <= 0 { continue }
                let gx = hm[i + 1] - hm[i - 1]
                let gy = hm[i + w] - hm[i - w]
                let shade = clamp(-(gx + gy) * 0.9, -0.35, 0.35)
                if abs(shade) < 0.002 { continue }
                let o = i * 4
                let a = cur[o + 3]
                if shade > 0 {
                    for c in 0..<3 { cur[o + c] = cur[o + c] + (a - cur[o + c]) * shade }
                } else {
                    for c in 0..<3 { cur[o + c] = cur[o + c] * (1 + shade) }
                }
            }
        }
    }

    /// Yardımcı fırçaların süzgeci: alttaki içerikten (ön çarpımlı) sonuç rengi.
    private static func filter(_ type: BrushType, _ base: Patch, _ params: BrushParams?, _ width: Float, _ strength: Float) -> [Float] {
        let w = base.w, h = base.h, s = base.scale
        let src = base.rgba
        let n = w * h
        var out = [Float](repeating: 0, count: n * 4)
        let fx = params?.fx
        switch type {
        case .FX_BLUR:
            let r = max(1, Bitmaps.roundToInt(blurRadius(width, strength) * s))
            out = boxBlur3(src, w, h, r)
        case .FX_SHARPEN:
            let r = max(1, Bitmaps.roundToInt(width * 0.06 * s))
            let b = boxBlur3(src, w, h, r)
            let amount = 0.8 + 1.2 * clamp01(strength)
            for i in 0..<n {
                let o = i * 4
                let a = src[o + 3]
                out[o + 3] = a
                for c in 0..<3 { out[o + c] = clamp(src[o + c] + amount * (src[o + c] - b[o + c]), 0, a) }
            }
        case .FX_DODGE, .FX_BURN:
            let dodge = type == .FX_DODGE
            let k = 0.25 + 0.45 * clamp01(strength)
            let range = fx?.mode ?? 1
            for i in 0..<n {
                let o = i * 4
                let a = src[o + 3]
                out[o + 3] = a
                if a <= 0.001 { continue }
                let r = src[o] / a, g = src[o + 1] / a, b = src[o + 2] / a
                let lum = 0.299 * r + 0.587 * g + 0.114 * b
                let wgt: Float
                switch range {
                case 0: wgt = (1 - lum) * (1 - lum)
                case 2: wgt = lum * lum
                default: wgt = 1 - abs(2 * lum - 1)
                }
                let kk = k * (0.35 + 0.65 * wgt)
                let nr = dodge ? r + (1 - r) * kk : r * (1 - kk)
                let ng = dodge ? g + (1 - g) * kk : g * (1 - kk)
                let nb = dodge ? b + (1 - b) * kk : b * (1 - kk)
                out[o] = nr * a; out[o + 1] = ng * a; out[o + 2] = nb * a
            }
        case .FX_SPONGE:
            let saturate = (fx?.mode ?? 0) == 1
            let k = 0.3 + 0.6 * clamp01(strength)
            for i in 0..<n {
                let o = i * 4
                let a = src[o + 3]
                out[o + 3] = a
                if a <= 0.001 { continue }
                let r = src[o] / a, g = src[o + 1] / a, b = src[o + 2] / a
                let lum = 0.299 * r + 0.587 * g + 0.114 * b
                let f: Float = saturate ? 1 + k : 1 - k
                out[o] = clamp01(lum + (r - lum) * f) * a
                out[o + 1] = clamp01(lum + (g - lum) * f) * a
                out[o + 2] = clamp01(lum + (b - lum) * f) * a
            }
        case .FX_CLONE, .FX_HEAL:
            let (sdx, sdy) = sourceOffset(type, params, width) ?? (0, 0)
            let ox = Bitmaps.roundToInt(sdx * s), oy = Bitmaps.roundToInt(sdy * s)
            var shifted = [Float](repeating: 0, count: n * 4)
            for y in 0..<h {
                let sy = y + oy
                if sy < 0 || sy >= h { continue }
                for x in 0..<w {
                    let sx = x + ox
                    if sx < 0 || sx >= w { continue }
                    let so = (sy * w + sx) * 4, d = (y * w + x) * 4
                    shifted[d] = src[so]; shifted[d + 1] = src[so + 1]; shifted[d + 2] = src[so + 2]; shifted[d + 3] = src[so + 3]
                }
            }
            if type == .FX_CLONE {
                out = shifted
            } else {
                // Onarma: kaynağın ayrıntısı (yüksek frekans) + hedefin ışığı ve rengi (düşük frekans)
                let r = max(1, Bitmaps.roundToInt(width * 0.3 * s))
                let lowDst = boxBlur3(src, w, h, r)
                let lowSrc = boxBlur3(shifted, w, h, r)
                var i = 0
                while i < n * 4 {
                    let a = clamp01(lowDst[i + 3] + (shifted[i + 3] - lowSrc[i + 3]))
                    out[i + 3] = a
                    for c in 0..<3 { out[i + c] = clamp(lowDst[i + c] + (shifted[i + c] - lowSrc[i + c]), 0, a) }
                    i += 4
                }
            }
        default:
            out = src
        }
        return out
    }

    /// Ortalama renk (ön çarpımlı): damga merkezi ve çevresindeki 4 nokta.
    private static func sampleAverage(_ img: UnsafeMutablePointer<Float>, _ w: Int, _ h: Int, _ px: Float, _ py: Float, _ r: Float)
        -> (Float, Float, Float, Float) {
        var o0: Float = 0, o1: Float = 0, o2: Float = 0, o3: Float = 0
        var cnt = 0
        let offs: [(Float, Float)] = [(0, 0), (r, 0), (-r, 0), (0, r), (0, -r)]
        for (ox, oy) in offs {
            let x = toInt(px + ox), y = toInt(py + oy)
            if x < 0 || y < 0 || x >= w || y >= h { continue }
            let o = (y * w + x) * 4
            o0 += img[o]; o1 += img[o + 1]; o2 += img[o + 2]; o3 += img[o + 3]
            cnt += 1
        }
        if cnt == 0 { return (0, 0, 0, 0) }
        let f = Float(cnt)
        return (o0 / f, o1 / f, o2 / f, o3 / f)
    }

    /// Üç geçişli kutu bulanıklığı (Gauss'a yakın), ön çarpımlı RGBA.
    static func boxBlur3(_ src: [Float], _ w: Int, _ h: Int, _ r: Int) -> [Float] {
        var a = src
        var b = [Float](repeating: 0, count: src.count)
        guard w > 0, h > 0 else { return a }
        a.withUnsafeMutableBufferPointer { pa in
            b.withUnsafeMutableBufferPointer { pb in
                for _ in 0..<3 {
                    boxPass(pa.baseAddress!, pb.baseAddress!, w, h, r, horizontal: true)
                    boxPass(pb.baseAddress!, pa.baseAddress!, w, h, r, horizontal: false)
                }
            }
        }
        return a
    }

    private static func boxPass(_ src: UnsafeMutablePointer<Float>, _ dst: UnsafeMutablePointer<Float>, _ w: Int, _ h: Int, _ r: Int,
                                horizontal: Bool) {
        let lines = horizontal ? h : w
        let len = horizontal ? w : h
        let stride = horizontal ? 4 : w * 4
        let inv = 1 / Float(2 * r + 1)
        for line in 0..<lines {
            let start = horizontal ? line * w * 4 : line * 4
            var s0: Float = 0, s1: Float = 0, s2: Float = 0, s3: Float = 0
            for t in -r...r {
                let idx = start + min(max(t, 0), len - 1) * stride
                s0 += src[idx]; s1 += src[idx + 1]; s2 += src[idx + 2]; s3 += src[idx + 3]
            }
            for t in 0..<len {
                let o = start + t * stride
                dst[o] = s0 * inv; dst[o + 1] = s1 * inv; dst[o + 2] = s2 * inv; dst[o + 3] = s3 * inv
                let add = start + min(t + r + 1, len - 1) * stride
                let rem = start + max(t - r, 0) * stride
                s0 += src[add] - src[rem]; s1 += src[add + 1] - src[rem + 1]
                s2 += src[add + 2] - src[rem + 2]; s3 += src[add + 3] - src[rem + 3]
            }
        }
    }

    private static func smooth(_ e0: Float, _ e1: Float, _ x: Float) -> Float {
        let t = clamp01((x - e0) / (e1 - e0))
        return t * t * (3 - 2 * t)
    }

    @inline(__always) private static func ushr(_ v: Int32, _ n: Int32) -> Int32 { Int32(bitPattern: UInt32(bitPattern: v) >> UInt32(n)) }

    private static func mix32(_ v: Int32) -> Int32 {
        var x = v
        x = x ^ ushr(x, 16); x = x &* 0x7feb352d; x = x ^ ushr(x, 15); x = x &* -0x7b935975; x = x ^ ushr(x, 16)
        return x
    }

    static func hash1(_ i: Int32, _ seed: Int32) -> Float {
        Float(UInt32(bitPattern: mix32(i &* 0x27d4eb2d &+ seed &* 0x165667b1)) >> 8) / 16777216
    }

    static func hash2(_ x: Int32, _ y: Int32, _ seed: Int32) -> Float {
        Float(UInt32(bitPattern: mix32(x &* 0x1f123bb5 &+ y &* 0x5f356495 &+ seed &* 0x2c1b3c6d)) >> 8) / 16777216
    }

    /// Tuvale sabit değer gürültüsü (0..1).
    static func valueNoise(_ x: Float, _ y: Float, _ seed: Int32) -> Float {
        let fx0 = floor(x), fy0 = floor(y)
        let x0 = toI(fx0), y0 = toI(fy0)
        let fx = x - fx0, fy = y - fy0
        let ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy)
        let a = hash2(x0, y0, seed), b = hash2(x0 &+ 1, y0, seed)
        let c = hash2(x0, y0 &+ 1, seed), d = hash2(x0 &+ 1, y0 &+ 1, seed)
        return (a + (b - a) * ux) * (1 - uy) + (c + (d - c) * ux) * uy
    }

    static func fbm(_ x: Float, _ y: Float, _ seed: Int32) -> Float {
        var sum: Float = 0, amp: Float = 0.5, f: Float = 1
        for o in 0..<4 {
            sum += amp * valueNoise(x * f, y * f, seed &+ Int32(o * 101))
            amp *= 0.5; f *= 2.03
        }
        return sum / 0.9375
    }
}
