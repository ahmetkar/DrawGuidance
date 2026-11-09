import Foundation

/// v43 cihazda sahne çözümlemesi (Android `SceneAnalyzer`; yalnız küçültülmüş piksellerle istatistik): uyum için arka
/// plan halkasının rengi/parlaklığı/keskinliği, ışık yönü ve zemin çizgisi; mekân için Sobel + Hough ile kaçış noktası.
enum SceneAnalyzer {
    struct Analysis: Equatable {
        let bgMean: [Float]
        let bgLum: Float
        let bgContrast: Float
        let bgSat: Float
        let bgSharpness: Float
        let objMean: [Float]
        let objLum: Float
        let objSat: Float
        let objSharpness: Float
        let light: P
        let lightStrength: Float
        let groundY: Float?
    }

    /// ARGB piksel görüntüsü.
    struct Pixels { let px: [UInt32]; let w: Int; let h: Int }

    /// [bg]: [region] alanının nesne olmadan çizimi; [objBounds]: nesnenin kutusu; [obj]: nesnenin (küçültülmüş) resmi.
    static func analyze(_ bg: Pixels, _ region: FRect, _ objBounds: FRect, _ obj: Pixels) -> Analysis {
        let w = bg.w, h = bg.h, px = bg.px
        let sx = Float(w) / region.width, sy = Float(h) / region.height
        let ox0 = Int((objBounds.left - region.left) * sx), ox1 = Int((objBounds.right - region.left) * sx)
        let oy0 = Int((objBounds.top - region.top) * sy), oy1 = Int((objBounds.bottom - region.top) * sy)
        func inObj(_ x: Int, _ y: Int) -> Bool { x >= ox0 && x <= ox1 && y >= oy0 && y <= oy1 }
        var lum = [Float](repeating: 0, count: w * h)
        var r = 0.0, g = 0.0, b = 0.0, lsum = 0.0, satSum = 0.0
        var n = 0
        for y in 0..<h { for x in 0..<w {
            let c = px[y * w + x]
            let cr = Float(c >> 16 & 0xFF) / 255, cg = Float(c >> 8 & 0xFF) / 255, cb = Float(c & 0xFF) / 255
            let l = 0.299 * cr + 0.587 * cg + 0.114 * cb
            lum[y * w + x] = l
            if inObj(x, y) { continue }
            r += Double(cr); g += Double(cg); b += Double(cb); lsum += Double(l); satSum += Double(sat(cr, cg, cb)); n += 1
        } }
        n = max(n, 1)
        let bgMean = [Float(r / Double(n)), Float(g / Double(n)), Float(b / Double(n))]
        let bgLum = Float(lsum / Double(n))
        var varSum = 0.0
        for y in 0..<h { for x in 0..<w where !inObj(x, y) { let d = lum[y * w + x] - bgLum; varSum += Double(d * d) } }
        let bgContrast = Float((varSum / Double(n)).squareRoot())
        let cx = Float(ox0 + ox1) / 2, cy = Float(oy0 + oy1) / 2
        var vx = 0.0, vy = 0.0, wsum = 0.0
        for y in 0..<h { for x in 0..<w {
            if inObj(x, y) { continue }
            let d = lum[y * w + x] - bgLum
            let dx = Float(x) - cx, dy = Float(y) - cy
            let dist = max(kHypot(dx, dy), 1)
            vx += Double(d * dx / dist); vy += Double(d * dy / dist); wsum += Double(abs(d))
        } }
        var light = P(-0.6, -0.8)
        var strength: Float = 0
        if wsum > 1e-6 {
            let lx = Float(vx / wsum), ly = Float(vy / wsum)
            let m = kHypot(lx, ly)
            if m > 0.05 { light = P(lx / m, ly / m); strength = (m * 2).coerceIn(0, 1) }
        }
        let bgSharp = laplaceVariance(lum, w, h) { x, y in !inObj(x, y) }
        let objH = max(oy1 - oy0, 1)
        var bestY = -1
        var best: Float = 0
        let yFrom = (oy1 - objH / 4).coerceIn(1, max(h - 2, 1)), yTo = (oy1 + objH / 4).coerceIn(1, max(h - 2, 1))
        if h >= 3 && yFrom <= yTo {
            for y in yFrom...yTo {
                var e: Float = 0
                let xa = max(0, ox0 - objH / 2), xb = min(w, ox1 + objH / 2)
                if xa < xb { for x in xa..<xb { e += abs(lum[(y + 1) * w + x] - lum[(y - 1) * w + x]) } }
                if e > best { best = e; bestY = y }
            }
        }
        let strongEdge = best > 0.08 * Float(max(ox1 - ox0 + objH, 1))
        let groundY: Float? = bestY >= 0 && strongEdge ? region.top + Float(bestY) / sy : nil
        let sw = obj.w, sh = obj.h, opx = obj.px
        var or = 0.0, og = 0.0, ob = 0.0, aw = 0.0, osat = 0.0
        var olum = [Float](repeating: 0, count: sw * sh)
        for i in opx.indices {
            let c = opx[i]
            let a = Float(c >> 24) / 255
            let cr = Float(c >> 16 & 0xFF) / 255, cg = Float(c >> 8 & 0xFF) / 255, cb = Float(c & 0xFF) / 255
            olum[i] = 0.299 * cr + 0.587 * cg + 0.114 * cb
            if a < 0.1 { continue }
            or += Double(cr * a); og += Double(cg * a); ob += Double(cb * a); osat += Double(sat(cr, cg, cb) * a); aw += Double(a)
        }
        aw = max(aw, 1e-6)
        let objMean = [Float(or / aw), Float(og / aw), Float(ob / aw)]
        let objSharp = laplaceVariance(olum, sw, sh) { x, y in opx[y * sw + x] >> 24 > 200 }
        return Analysis(bgMean: bgMean, bgLum: bgLum, bgContrast: bgContrast, bgSat: Float(satSum / Double(n)), bgSharpness: bgSharp,
                        objMean: objMean, objLum: 0.299 * objMean[0] + 0.587 * objMean[1] + 0.114 * objMean[2], objSat: Float(osat / aw),
                        objSharpness: objSharp, light: light, lightStrength: strength, groundY: groundY)
    }

    struct Options: Equatable {
        var shadow = true
        var color = true
        var edge = true
        var position = false
        var intensity: Float = 0.8
    }

    static func suggest(_ a: Analysis, _ o: Options) -> HarmonySpec {
        func gain(_ i: Int) -> Float {
            let bgC = a.bgMean[i] / max(a.bgLum, 0.02)
            let obC = a.objMean[i] / max(a.objLum, 0.02)
            let ratio: Float = obC < 1e-3 ? 1 : bgC / obC
            return (1 + (ratio - 1) * 0.35).coerceIn(0.75, 1.3)
        }
        let lift = ((a.bgLum - a.objLum) * 0.35).coerceIn(-0.2, 0.2)
        let satRatio: Float = a.objSat < 0.02 ? 1 : a.bgSat / a.objSat
        let saturation = (1 + (satRatio - 1) * 0.4).coerceIn(0.7, 1.15)
        let lx = a.light.x, ly = a.light.y
        let opacity = ((0.2 + a.bgContrast * 1.2).coerceIn(0.2, 0.55) * (0.6 + 0.4 * a.lightStrength)).coerceIn(0.15, 0.55)
        let blurrier = a.bgSharpness < a.objSharpness * 0.6
        var s = HarmonySpec()
        s.intensity = o.intensity.coerceIn(0, 1)
        s.color = o.color
        s.gainR = gain(0); s.gainG = gain(1); s.gainB = gain(2); s.lift = lift; s.saturation = saturation
        s.shadow = o.shadow
        s.shadowColorHex = hex(a.bgMean[0] * 0.22, a.bgMean[1] * 0.22, a.bgMean[2] * 0.25)
        s.shadowOpacity = opacity
        s.shadowDx = -lx * 0.02; s.shadowDy = 0.01
        s.shadowBlur = blurrier ? 0.045 : 0.03
        s.castSkew = (lx * 0.9).coerceIn(-1.2, 1.2)
        s.castScaleY = (0.15 + 0.35 * (1 - abs(ly))).coerceIn(0.12, 0.6)
        s.contact = true
        s.edge = o.edge
        s.feather = blurrier ? 0.012 : 0.005
        s.wrap = (0.25 + 0.2 * a.lightStrength).coerceIn(0, 0.6)
        s.wrapColorHex = hex(a.bgMean[0], a.bgMean[1], a.bgMean[2])
        s.lightAngle = Float(atan2(Double(ly), Double(lx)) * 180 / .pi)
        return s
    }

    struct Perspective: Equatable {
        let horizonY: Float
        let vanishing: P?
        let confidence: Float
        let lines: Int
    }

    static func perspective(_ bg: Pixels, _ canvasW: Float, _ canvasH: Float) -> Perspective {
        let w = bg.w, h = bg.h, px = bg.px
        let lum: [Float] = px.map { c -> Float in
            let r: Float = Float(c >> 16 & 0xFF), g: Float = Float(c >> 8 & 0xFF), b: Float = Float(c & 0xFF)
            return (0.299 * r + 0.587 * g + 0.114 * b) / 255
        }
        var mag = [Float](repeating: 0, count: w * h), ang = [Float](repeating: 0, count: w * h)
        var maxMag: Float = 0
        if w >= 3 && h >= 3 {
            for y in 1..<(h - 1) { for x in 1..<(w - 1) {
                let i = y * w + x
                let gx = (lum[i - w + 1] + 2 * lum[i + 1] + lum[i + w + 1]) - (lum[i - w - 1] + 2 * lum[i - 1] + lum[i + w - 1])
                let gy = (lum[i + w - 1] + 2 * lum[i + w] + lum[i + w + 1]) - (lum[i - w - 1] + 2 * lum[i - w] + lum[i - w + 1])
                mag[i] = kHypot(gx, gy); ang[i] = atan2f(gy, gx)
                if mag[i] > maxMag { maxMag = mag[i] }
            } }
        }
        let sx = canvasW / Float(w), sy = canvasH / Float(h)
        let fallback = Perspective(horizonY: canvasH * 0.4, vanishing: nil, confidence: 0.2, lines: 0)
        if maxMag < 1e-3 { return fallback }
        let thr = maxMag * 0.25
        let thetas = 180
        let diag = Int(kHypot(Float(w), Float(h))) + 1
        let cols = 2 * diag + 1
        var acc = [Int](repeating: 0, count: thetas * cols)
        let cosT = (0..<thetas).map { Float(cos(Double($0) * .pi / 180)) }
        let sinT = (0..<thetas).map { Float(sin(Double($0) * .pi / 180)) }
        for y in 1..<(h - 1) { for x in 1..<(w - 1) {
            let i = y * w + x
            if mag[i] < thr { continue }
            var nd = Int(Double(ang[i]) * 180 / .pi)
            if nd < 0 { nd += 180 }
            for d in -10...10 {
                let t = ((nd + d) % 180 + 180) % 180
                let rho = Int(Float(x) * cosT[t] + Float(y) * sinT[t]) + diag
                acc[t * cols + rho] += 1
            }
        } }
        struct Line { let theta: Int; let rho: Float; let votes: Int }
        let minVotes = Int(Float(min(w, h)) * 0.35)
        var lines: [Line] = []
        let order = acc.indices.filter { acc[$0] >= minVotes }.sorted { acc[$0] != acc[$1] ? acc[$0] > acc[$1] : $0 < $1 }
        for k in order {
            let t = k / cols, r = k % cols - diag
            if lines.contains(where: { l in let d = abs(l.theta - t); return min(d, 180 - d) < 6 && abs(l.rho - Float(r)) < Float(diag) * 0.04 }) { continue }
            lines.append(Line(theta: t, rho: Float(r), votes: acc[k]))
            if lines.count >= 24 { break }
        }
        if lines.isEmpty { return fallback }
        let oblique = lines.filter { l in min(l.theta, 180 - l.theta) > 12 && abs(l.theta - 90) > 12 }
        var points: [P] = []
        for i in oblique.indices { for j in (i + 1)..<max(oblique.count, i + 1) {
            let a = oblique[i], b = oblique[j]
            if abs(a.theta - b.theta) < 8 { continue }
            let det = cosT[a.theta] * sinT[b.theta] - sinT[a.theta] * cosT[b.theta]
            if abs(det) < 1e-3 { continue }
            let x = (a.rho * sinT[b.theta] - b.rho * sinT[a.theta]) / det
            let y = (cosT[a.theta] * b.rho - cosT[b.theta] * a.rho) / det
            if x < -Float(w) * 0.5 || x > Float(w) * 1.5 || y < -Float(h) * 0.3 || y > Float(h) { continue }
            points.append(P(x, y))
        } }
        if points.count >= 3 {
            let mx = points.map(\.x).sorted()[points.count / 2]
            let my = points.map(\.y).sorted()[points.count / 2]
            let near = points.filter { kHypot($0.x - mx, $0.y - my) < Float(max(w, h)) * 0.15 }.count
            if near * 2 >= points.count {
                return Perspective(horizonY: (my * sy).coerceIn(0, canvasH), vanishing: P(mx * sx, my * sy),
                                   confidence: min(0.5 + 0.5 * Float(near) / Float(points.count), 1), lines: lines.count)
            }
        }
        let horizontal = lines.filter { abs($0.theta - 90) <= 4 && $0.rho >= Float(h) * 0.1 && $0.rho <= Float(h) * 0.7 }
        if let best = horizontal.max(by: { $0.votes < $1.votes }) {
            return Perspective(horizonY: best.rho * sy, vanishing: nil, confidence: 0.5, lines: lines.count)
        }
        return Perspective(horizonY: fallback.horizonY, vanishing: nil, confidence: 0.2, lines: lines.count)
    }

    private static func laplaceVariance(_ l: [Float], _ w: Int, _ h: Int, _ use: (Int, Int) -> Bool) -> Float {
        var s = 0.0, s2 = 0.0
        var n = 0
        if w < 3 || h < 3 { return 0 }
        for y in 1..<(h - 1) { for x in 1..<(w - 1) {
            if !use(x, y) { continue }
            let i = y * w + x
            let v = Double(4 * l[i] - l[i - 1] - l[i + 1] - l[i - w] - l[i + w])
            s += v; s2 += v * v; n += 1
        } }
        if n == 0 { return 0 }
        let m = s / Double(n)
        return Float(s2 / Double(n) - m * m)
    }

    private static func sat(_ r: Float, _ g: Float, _ b: Float) -> Float {
        let mx = max(r, max(g, b)), mn = min(r, min(g, b))
        return mx <= 1e-4 ? 0 : (mx - mn) / mx
    }

    static func hex(_ r: Float, _ g: Float, _ b: Float) -> String {
        String(format: "#%02X%02X%02X", Int(r * 255).coerceIn(0, 255), Int(g * 255).coerceIn(0, 255), Int(b * 255).coerceIn(0, 255))
    }
}

/// v43 "Nesneyi Mekâna Yerleştir": ufka göre perspektif ölçeği (Android `ScenePlacement`).
enum ScenePlacement {
    struct Reference: Equatable { let footY: Float; let height: Float }
    struct Result: Equatable { let footX: Float; let footY: Float; let scale: Float; let k: Float; let references: Int }

    static let MIN_SCALE: Float = 0.25
    static let MAX_SCALE: Float = 4

    static func place(_ obj: FRect, _ horizonY: Float, _ canvasH: Float, _ refs: [Reference], footOverride: Float? = nil, scaleMul: Float = 1) -> Result {
        let minGap = canvasH * 0.03
        let foot0 = obj.bottom
        let footY = (footOverride ?? (foot0 > horizonY + minGap ? foot0 : horizonY + 0.35 * (canvasH - horizonY))).coerceIn(horizonY + minGap, canvasH)
        let valid = refs.filter { $0.footY > horizonY + minGap && $0.height > 1 }.map { $0.height / ($0.footY - horizonY) }.sorted()
        let k: Float
        if !valid.isEmpty { k = valid[valid.count / 2] }
        else if foot0 > horizonY + minGap { k = obj.height / (foot0 - horizonY) }
        else { k = 0.9 }
        let targetH = (k * (footY - horizonY) * scaleMul).coerceIn(canvasH * 0.02, canvasH * 0.95)
        let scale = (targetH / max(obj.height, 1)).coerceIn(MIN_SCALE, MAX_SCALE)
        return Result(footX: (obj.left + obj.right) / 2, footY: footY, scale: scale, k: k, references: valid.count)
    }
}
