import Foundation

/// Vektörleştirme / kenar izleme iptal edildi.
struct TraceCancelled: Error {}

@inline(__always) func checkCancel(_ cancel: () -> Bool) throws {
    if cancel() { throw TraceCancelled() }
}

/// Çalışma çözünürlüğündeki görüntü: 0xRRGGBB renkler; saydam (arka plan) pikseller için opaque false.
struct Raster {
    let w: Int
    let h: Int
    let rgb: [Int]
    let opaque: [Bool]
}

/// Alan ortalamalı küçültme (uzun kenar ≤ maxSide). Renkler alfa ağırlıklı ortalanır (Android `Downscale`).
enum Downscale {
    static func run(_ argb: [UInt32], _ width: Int, _ height: Int, _ maxSide: Int, _ keepAlpha: Bool, _ cancel: () -> Bool) throws -> Raster {
        let longer = max(width, height)
        let scale = longer > maxSide ? Double(maxSide) / Double(longer) : 1.0
        let w = min(max(Int((Double(width) * scale + 0.5).rounded(.down)), 1), width)
        let h = min(max(Int((Double(height) * scale + 0.5).rounded(.down)), 1), height)
        var rgb = [Int](repeating: 0, count: w * h)
        var opaque = [Bool](repeating: false, count: w * h)
        if w == width && h == height {
            for i in 0..<(w * h) {
                let c = argb[i]
                let a = Int(c >> 24)
                store(&rgb, &opaque, i, a, Int(c >> 16 & 255) * a, Int(c >> 8 & 255) * a, Int(c & 255) * a, 1, keepAlpha)
            }
            return Raster(w: w, h: h, rgb: rgb, opaque: opaque)
        }
        let xMap = (0..<width).map { $0 * w / width }
        var sa = [Int](repeating: 0, count: w), sr = sa, sg = sa, sb = sa, cnt = sa
        var y = 0
        for dy in 0..<h {
            for i in 0..<w { sa[i] = 0; sr[i] = 0; sg[i] = 0; sb[i] = 0; cnt[i] = 0 }
            while y < height && (y * h) / height == dy {
                let row = y * width
                for x in 0..<width {
                    let c = argb[row + x]
                    let a = Int(c >> 24)
                    let dx = xMap[x]
                    sa[dx] += a
                    sr[dx] += Int(c >> 16 & 255) * a
                    sg[dx] += Int(c >> 8 & 255) * a
                    sb[dx] += Int(c & 255) * a
                    cnt[dx] += 1
                }
                y += 1
            }
            let base = dy * w
            for dx in 0..<w { store(&rgb, &opaque, base + dx, sa[dx], sr[dx], sg[dx], sb[dx], max(1, cnt[dx]), keepAlpha) }
            if dy & 31 == 0 { try checkCancel(cancel) }
        }
        return Raster(w: w, h: h, rgb: rgb, opaque: opaque)
    }

    private static func store(_ rgb: inout [Int], _ opaque: inout [Bool], _ i: Int, _ a: Int, _ r: Int, _ g: Int, _ b: Int, _ n: Int, _ keepAlpha: Bool) {
        if keepAlpha {
            if a >= 128 * n && a > 0 {
                opaque[i] = true
                rgb[i] = ch(r, a) << 16 | ch(g, a) << 8 | ch(b, a)
            }
        } else {
            // Beyaz zemin üzerine bindirilir
            let den = 255 * n
            let white = 255 * (den - a)
            opaque[i] = true
            rgb[i] = ch(r + white, den) << 16 | ch(g + white, den) << 8 | ch(b + white, den)
        }
    }

    @inline(__always) private static func ch(_ sum: Int, _ den: Int) -> Int { min(max((sum + den / 2) / den, 0), 255) }
}

/// sRGB → CIE L*a*b* (D65).
enum TraceLab {
    @inline(__always) private static func lin(_ c: Double) -> Double { let v = c / 255.0; return v <= 0.04045 ? v / 12.92 : pow((v + 0.055) / 1.055, 2.4) }
    @inline(__always) private static func f(_ t: Double) -> Double { t > 0.008856 ? cbrt(t) : 7.787 * t + 16.0 / 116.0 }

    static func from(_ r: Double, _ g: Double, _ b: Double, _ out: inout [Float], _ o: Int) {
        let rl = lin(r), gl = lin(g), bl = lin(b)
        let x = (0.4124564 * rl + 0.3575761 * gl + 0.1804375 * bl) / 0.95047
        let y = 0.2126729 * rl + 0.7151522 * gl + 0.0721750 * bl
        let z = (0.0193339 * rl + 0.1191920 * gl + 0.9503041 * bl) / 1.08883
        let fx = f(x), fy = f(y), fz = f(z)
        out[o] = Float(116.0 * fy - 16.0)
        out[o + 1] = Float(500.0 * (fx - fy))
        out[o + 2] = Float(200.0 * (fy - fz))
    }
}

/// Nicemleme sonucu: piksel başına palet sırası (saydam: -1) ve palet (çoktan aza).
struct Quantized {
    let labels: [Int]
    let paletteRgb: [Int]
}

/// Renk nicemleme (Android `Quantizer` ile birebir): 15 bitlik histogram üzerinde Lab uzayında ağırlıklı k-means
/// (k-means++ başlangıç, sabit tohum), küçük belirgin renkler için merkez takası, yakın merkezlerin birleşmesi.
enum Quantizer {
    private static let BINS = 1 << 15
    private static let FAR_DE: Float = 14
    private static let GROUP_DE: Float = 14

    @inline(__always) private static func key(_ c: Int) -> Int { (c >> 9) & 0x7C00 | (c >> 6) & 0x3E0 | (c >> 3) & 0x1F }

    static func run(_ img: Raster, _ k: Int, _ mergeDE: Float, _ cancel: () -> Bool) throws -> Quantized {
        let n = img.w * img.h
        var cnt = [Int](repeating: 0, count: BINS)
        var sr = cnt, sg = cnt, sb = cnt
        for i in 0..<n where img.opaque[i] {
            let c = img.rgb[i]
            let kk = key(c)
            cnt[kk] += 1
            sr[kk] += (c >> 16) & 255; sg[kk] += (c >> 8) & 255; sb[kk] += c & 255
        }
        var m = 0
        for b in 0..<BINS where cnt[b] > 0 { m += 1 }
        var labels = [Int](repeating: -1, count: n)
        if m == 0 { return Quantized(labels: labels, paletteRgb: []) }

        var binKey = [Int](repeating: 0, count: m)
        var weight = [Double](repeating: 0, count: m)
        var rgb = [Double](repeating: 0, count: m * 3)
        var lab = [Float](repeating: 0, count: m * 3)
        do {
            var j = 0
            for b in 0..<BINS {
                let c = cnt[b]
                if c == 0 { continue }
                binKey[j] = b; weight[j] = Double(c)
                rgb[j * 3] = Double(sr[b]) / Double(c); rgb[j * 3 + 1] = Double(sg[b]) / Double(c); rgb[j * 3 + 2] = Double(sb[b]) / Double(c)
                TraceLab.from(rgb[j * 3], rgb[j * 3 + 1], rgb[j * 3 + 2], &lab, j * 3)
                j += 1
            }
        }
        var totalWeight = 0.0
        for v in weight { totalWeight += v }
        try checkCancel(cancel)

        // k-means++ başlangıcı
        let kk0 = min(k, m)
        var centre = [Float](repeating: 0, count: kk0 * 3)
        var rnd = JavaRandom(0x7EACE5)
        var d2 = [Double](repeating: Double.greatestFiniteMagnitude, count: m)
        func pickWeighted(_ score: (Int) -> Double, _ total: Double) -> Int {
            var r = rnd.nextDouble() * total
            for i in 0..<m { r -= score(i); if r <= 0 { return i } }
            return m - 1
        }
        var kk = 0
        func addCentre(_ i: Int) {
            centre[kk * 3] = lab[i * 3]; centre[kk * 3 + 1] = lab[i * 3 + 1]; centre[kk * 3 + 2] = lab[i * 3 + 2]
            let o = kk * 3
            for j in 0..<m {
                let d = Double(dist2(lab, j * 3, centre, o))
                if d < d2[j] { d2[j] = d }
            }
            kk += 1
        }
        addCentre(pickWeighted({ weight[$0] }, totalWeight))
        while kk < kk0 {
            var total = 0.0
            for j in 0..<m { total += weight[j] * d2[j] }
            if total <= 1e-6 { break }
            addCentre(pickWeighted({ weight[$0] * d2[$0] }, total))
        }

        // Lloyd yinelemeleri
        var assign = [Int](repeating: -1, count: m)
        var acc = [Double](repeating: 0, count: kk * 4)
        var cw = [Double](repeating: 0, count: kk)
        func lloyd(_ maxIter: Int) throws {
            for iter in 0..<maxIter {
                var changed = 0
                for j in 0..<m {
                    let a = nearest(lab, j * 3, centre, kk)
                    if a != assign[j] { assign[j] = a; changed += 1 }
                }
                if changed == 0 && iter > 0 { break }
                for i in acc.indices { acc[i] = 0 }
                for j in 0..<m {
                    let o = assign[j] * 4, w = weight[j]
                    acc[o] += w; acc[o + 1] += w * Double(lab[j * 3]); acc[o + 2] += w * Double(lab[j * 3 + 1]); acc[o + 3] += w * Double(lab[j * 3 + 2])
                }
                for c in 0..<kk {
                    let w = acc[c * 4]
                    cw[c] = w
                    if w <= 0 { continue }
                    centre[c * 3] = Float(acc[c * 4 + 1] / w); centre[c * 3 + 1] = Float(acc[c * 4 + 2] / w)
                    centre[c * 3 + 2] = Float(acc[c * 4 + 3] / w)
                }
                try checkCancel(cancel)
            }
        }
        try lloyd(16)

        // Takas: küçük ama belirgin renk grubu için en ucuz iki merkez birleştirilir
        let minGroup = max(16.0, totalWeight * 0.002)
        var gc = [Float](repeating: 0, count: 3)
        for _ in 0..<4 {
            if kk < 2 { break }
            var worst = -1
            var worstScore = 0.0
            for j in 0..<m {
                let dd = dist2(lab, j * 3, centre, assign[j] * 3)
                if dd > FAR_DE * FAR_DE && weight[j] * Double(dd) > worstScore { worstScore = weight[j] * Double(dd); worst = j }
            }
            if worst < 0 { break }
            var gw = 0.0, gl = 0.0, ga = 0.0, gb = 0.0
            for j in 0..<m {
                let dw = dist2(lab, j * 3, lab, worst * 3)
                if dw < GROUP_DE * GROUP_DE && dw < dist2(lab, j * 3, centre, assign[j] * 3) {
                    let w = weight[j]
                    gw += w; gl += w * Double(lab[j * 3]); ga += w * Double(lab[j * 3 + 1]); gb += w * Double(lab[j * 3 + 2])
                }
            }
            if gw < minGroup { break }
            gc[0] = Float(gl / gw); gc[1] = Float(ga / gw); gc[2] = Float(gb / gw)
            var gain = 0.0
            for j in 0..<m {
                let dNew = dist2(lab, j * 3, gc, 0), dOld = dist2(lab, j * 3, centre, assign[j] * 3)
                if dNew < dOld { gain += weight[j] * Double(dOld - dNew) }
            }
            var bi = -1, bj = -1
            var cost = Double.greatestFiniteMagnitude
            for i in 0..<kk {
                for j in (i + 1)..<max(kk, i + 1) {
                    let wi = cw[i], wj = cw[j]
                    let c = wi <= 0 || wj <= 0 ? 0.0 : wi * wj / (wi + wj) * Double(dist2(centre, i * 3, centre, j * 3))
                    if c < cost { cost = c; bi = i; bj = j }
                }
            }
            if bi < 0 || gain <= cost * 0.5 { break }
            let wi = cw[bi], wj = cw[bj], t = wi + wj
            if t > 0 { for c in 0..<3 { centre[bi * 3 + c] = Float((Double(centre[bi * 3 + c]) * wi + Double(centre[bj * 3 + c]) * wj) / t) } }
            centre[bj * 3] = gc[0]; centre[bj * 3 + 1] = gc[1]; centre[bj * 3 + 2] = gc[2]
            try lloyd(10)
        }

        // Yakın merkezleri birleştir
        for i in cw.indices { cw[i] = 0 }
        for j in 0..<m { cw[assign[j]] += weight[j] }
        var alive = (0..<kk).map { cw[$0] > 0 }
        let thr2 = mergeDE * mergeDE
        while true {
            var bi = -1, bj = -1
            var best = thr2
            for i in 0..<kk where alive[i] {
                for j in (i + 1)..<max(kk, i + 1) where alive[j] {
                    let d = dist2(centre, i * 3, centre, j * 3)
                    if d < best { best = d; bi = i; bj = j }
                }
            }
            if bi < 0 { break }
            let wi = cw[bi], wj = cw[bj], t = wi + wj
            for c in 0..<3 { centre[bi * 3 + c] = Float((Double(centre[bi * 3 + c]) * wi + Double(centre[bj * 3 + c]) * wj) / t) }
            cw[bi] = t; cw[bj] = 0; alive[bj] = false
        }
        let liveIdx = (0..<kk).filter { alive[$0] }
        let liveCentre: [Float] = (0..<(liveIdx.count * 3)).map { centre[liveIdx[$0 / 3] * 3 + $0 % 3] }
        var sums = [Double](repeating: 0, count: liveIdx.count * 4)
        for j in 0..<m {
            let a = nearest(lab, j * 3, liveCentre, liveIdx.count)
            assign[j] = a
            let w = weight[j]
            sums[a * 4] += w; sums[a * 4 + 1] += w * rgb[j * 3]; sums[a * 4 + 2] += w * rgb[j * 3 + 1]; sums[a * 4 + 3] += w * rgb[j * 3 + 2]
        }
        // Piksel sayısına göre sırala (eşitlikte sabit sıra)
        let live: [Int] = (0..<liveIdx.count).filter { sums[$0 * 4] > 0 }
        let order: [Int] = live.sorted { (a: Int, b: Int) -> Bool in
            let sa: Double = sums[a * 4], sb: Double = sums[b * 4]
            return sa != sb ? sa > sb : a < b
        }
        var remap = [Int](repeating: -1, count: liveIdx.count)
        for (i, c) in order.enumerated() { remap[c] = i }
        func rnd8(_ v: Double) -> Int { min(max(Int((v + 0.5).rounded(.down)), 0), 255) }
        let palette: [Int] = order.map { c in
            let w = sums[c * 4]
            return rnd8(sums[c * 4 + 1] / w) << 16 | rnd8(sums[c * 4 + 2] / w) << 8 | rnd8(sums[c * 4 + 3] / w)
        }
        var binLabel = [Int](repeating: -1, count: BINS)
        for j in 0..<m { binLabel[binKey[j]] = remap[assign[j]] }
        for i in 0..<n where img.opaque[i] { labels[i] = binLabel[key(img.rgb[i])] }
        return Quantized(labels: labels, paletteRgb: palette)
    }

    @inline(__always) static func dist2(_ a: [Float], _ ao: Int, _ b: [Float], _ bo: Int) -> Float {
        let d0 = a[ao] - b[bo], d1 = a[ao + 1] - b[bo + 1], d2 = a[ao + 2] - b[bo + 2]
        return d0 * d0 + d1 * d1 + d2 * d2
    }

    private static func nearest(_ lab: [Float], _ o: Int, _ centre: [Float], _ k: Int) -> Int {
        var best = 0
        var bd = Float.greatestFiniteMagnitude
        for c in 0..<k {
            let d = dist2(lab, o, centre, c * 3)
            if d < bd { bd = d; best = c }
        }
        return best
    }
}

/// Kenar koruyan gürültü süzgeci (sigma süzgeci, 3×3).
func traceDenoise(_ img: Raster, _ threshold: Int, _ passes: Int, _ cancel: () -> Bool) throws -> Raster {
    let w = img.w, h = img.h
    let t2 = threshold * threshold
    var src = img.rgb
    for _ in 0..<passes {
        var dst = [Int](repeating: 0, count: w * h)
        for y in 0..<h {
            let y0 = max(0, y - 1), y1 = min(h - 1, y + 1)
            for x in 0..<w {
                let p = y * w + x
                let c = src[p]
                if !img.opaque[p] { dst[p] = c; continue }
                let cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255
                var sr = 0, sg = 0, sb = 0, n = 0
                for yy in y0...y1 {
                    let row = yy * w
                    for xx in max(0, x - 1)...min(w - 1, x + 1) {
                        let q = row + xx
                        if !img.opaque[q] { continue }
                        let o = src[q]
                        let r = (o >> 16) & 255, g = (o >> 8) & 255, b = o & 255
                        let dr = r - cr, dg = g - cg, db = b - cb
                        if dr * dr + dg * dg + db * db >= t2 { continue }
                        sr += r; sg += g; sb += b; n += 1
                    }
                }
                dst[p] = ((sr + n / 2) / n) << 16 | ((sg + n / 2) / n) << 8 | ((sb + n / 2) / n)
            }
            if y & 63 == 0 { try checkCancel(cancel) }
        }
        src = dst
    }
    return Raster(w: w, h: h, rgb: src, opaque: img.opaque)
}

/// 3×3 çoğunluk süzgeci (Android `modeFilter`).
func traceModeFilter(_ labels: [Int], _ w: Int, _ h: Int, _ k: Int, _ maxOwn: Int, _ minBest: Int, _ soft: [Bool]?) -> [Int] {
    var out = labels
    var counts = [Int](repeating: 0, count: k + 1)
    for y in 0..<h {
        let y0 = max(0, y - 1), y1 = min(h - 1, y + 1)
        for x in 0..<w {
            let x0 = max(0, x - 1), x1 = min(w - 1, x + 1)
            let own = labels[y * w + x]
            var best = -2, bestC = 0
            for yy in y0...y1 {
                let row = yy * w
                for xx in x0...x1 {
                    let l = labels[row + xx]
                    counts[l + 1] += 1
                    let c = counts[l + 1]
                    if l != own && (c > bestC || (c == bestC && l < best)) { bestC = c; best = l }
                }
            }
            let ownC = counts[own + 1]
            for yy in y0...y1 { let row = yy * w; for xx in x0...x1 { counts[labels[row + xx] + 1] = 0 } }
            if best < -1 { continue }
            if ownC <= maxOwn && bestC >= minBest && bestC > ownC { out[y * w + x] = best }
            else if let soft, own >= 0 && best >= 0 && ownC <= 4 && bestC >= 5 && soft[own * k + best] { out[y * w + x] = best }
        }
    }
    return out
}

/// Palet renk çiftlerinden birbirine yakın (ΔE < de) olanlar: k×k matris.
func traceSoftPairs(_ palette: [Int], _ de: Float) -> [Bool] {
    let k = palette.count
    var lab = [Float](repeating: 0, count: k * 3)
    for i in 0..<k {
        let c = palette[i]
        TraceLab.from(Double((c >> 16) & 255), Double((c >> 8) & 255), Double(c & 255), &lab, i * 3)
    }
    return (0..<(k * k)).map { idx in
        let i = idx / k, j = idx % k
        if i == j { return false }
        let d0 = lab[i * 3] - lab[j * 3], d1 = lab[i * 3 + 1] - lab[j * 3 + 1], d2 = lab[i * 3 + 2] - lab[j * 3 + 2]
        return d0 * d0 + d1 * d1 + d2 * d2 < de * de
    }
}

/// RGB uzaklığı (0..441).
func traceRgbDist(_ a: Int, _ b: Int) -> Float {
    let dr = ((a >> 16) & 255) - ((b >> 16) & 255)
    let dg = ((a >> 8) & 255) - ((b >> 8) & 255)
    let db = (a & 255) - (b & 255)
    return Float(dr * dr + dg * dg + db * db).squareRoot()
}
