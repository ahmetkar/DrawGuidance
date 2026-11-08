import Foundation

/// Hazır arka plan resminin yerleşimi (Android `ImageLayout`): COVER, TILE, NINE (dokuz parçalı).
struct ImageLayout: Equatable {
    let kind: Int
    var insets: [Int] = [0, 0, 0, 0]
    var content: [Int] = [0, 0, 0, 0]
    var repeat_ = false

    static let COVER = 0
    static let TILE = 1
    static let NINE = 2

    /// Veritabanındaki kısa yazımı: "cover", "tile", "9:l,t,r,b|l,t,r,b" ya da "9r:…".
    func encode() -> String {
        switch kind {
        case ImageLayout.TILE: return "tile"
        case ImageLayout.NINE:
            return (repeat_ ? "9r:" : "9:") + insets.map(String.init).joined(separator: ",") + "|" + content.map(String.init).joined(separator: ",")
        default: return "cover"
        }
    }

    static func decode(_ s: String) -> ImageLayout {
        if s == "tile" { return ImageLayout(kind: TILE) }
        if s.hasPrefix("9") {
            let repeat_ = s.hasPrefix("9r:")
            let body = s.firstIndex(of: ":").map { String(s[s.index(after: $0)...]) } ?? s
            let parts = body.components(separatedBy: "|")
            func ints(_ p: String?) -> [Int] {
                guard let p else { return [0, 0, 0, 0] }
                let v = p.components(separatedBy: ",").compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }
                return v.count == 4 ? v : [0, 0, 0, 0]
            }
            return ImageLayout(kind: NINE, insets: ints(parts.first), content: ints(parts.count > 1 ? parts[1] : nil), repeat_: repeat_)
        }
        return ImageLayout(kind: COVER)
    }
}

/// Hazır arka plan resmi: program içinde üretilir ([generate] ARGB, önçarpımsız).
struct PaperImageDef {
    let id: String
    let name: String
    let usage: PaperUsage
    let w: Int
    let h: Int
    let layout: ImageLayout
    /// Saydam pikseli yok.
    let opaque: Bool
    let generate: () -> [UInt32]
}

/// v23 hazır arka plan resimleri (Android `PaperImages`): gürültü, işaretli uzaklık alanı ve basit ışıklandırmayla
/// burada üretilir (internetten alınmış resim yok). Android ile aynı algoritma ve tohumlar.
enum PaperImages {
    static let VERSION = 1

    private static let N = PaperUsage.NOTE
    private static let A = PaperUsage.ART
    private static let B = PaperUsage.BOX

    private static func cover() -> ImageLayout { ImageLayout(kind: ImageLayout.COVER) }
    private static func tile() -> ImageLayout { ImageLayout(kind: ImageLayout.TILE) }
    private static func nine(_ l: Int, _ t: Int, _ r: Int, _ b: Int, _ content: [Int], repeat_: Bool = false) -> ImageLayout {
        ImageLayout(kind: ImageLayout.NINE, insets: [l, t, r, b], content: content, repeat_: repeat_)
    }

    static let all: [PaperImageDef] = {
        var l: [PaperImageDef] = []
        // not tuvali
        l.append(PaperImageDef(id: "note.img.cream", name: "Krem kağıt", usage: N, w: 512, h: 512, layout: tile(), opaque: true) { cream(512) })
        l.append(PaperImageDef(id: "note.img.recycled", name: "Geri dönüşüm", usage: N, w: 512, h: 512, layout: tile(), opaque: true) { recycled(512) })
        l.append(PaperImageDef(id: "note.img.kraft", name: "Açık kraft", usage: N, w: 512, h: 512, layout: tile(), opaque: true) { kraft(512) })
        l.append(PaperImageDef(id: "note.img.linen", name: "Keten", usage: N, w: 256, h: 256, layout: tile(), opaque: true) { weave(256, 64, 0xFFF3_EFE6, 0.05, 71) })
        l.append(PaperImageDef(id: "note.img.rice", name: "Pirinç kağıdı", usage: N, w: 512, h: 512, layout: tile(), opaque: true) { rice(512) })
        l.append(PaperImageDef(id: "note.img.parchment", name: "Parşömen", usage: N, w: 620, h: 877, layout: cover(), opaque: true) { parchment(620, 877) })
        l.append(PaperImageDef(id: "note.img.wash", name: "Suluboya köşeler", usage: N, w: 620, h: 877, layout: cover(), opaque: true) { wash(620, 877) })
        l.append(PaperImageDef(id: "note.img.sky", name: "Açık mavi geçiş", usage: N, w: 400, h: 566, layout: cover(), opaque: true) {
            gradient(400, 566, [0xFFDC_EBF9, 0xFFF4_F9FE, 0xFFFF_FFFF], [0, 0.55, 1])
        })
        // resim tuvali
        l.append(PaperImageDef(id: "art.img.coldpress", name: "Suluboya kağıdı", usage: A, w: 512, h: 512, layout: tile(), opaque: true) { coldPress(512) })
        l.append(PaperImageDef(id: "art.img.canvas", name: "Tuval bezi", usage: A, w: 256, h: 256, layout: tile(), opaque: true) { weave(256, 48, 0xFFEA_E2D0, 0.11, 83) })
        l.append(PaperImageDef(id: "art.img.charcoal", name: "Gri karakalem kağıdı", usage: A, w: 512, h: 512, layout: tile(), opaque: true) { charcoal(512) })
        l.append(PaperImageDef(id: "art.img.sunset", name: "Gün batımı", usage: A, w: 720, h: 720, layout: cover(), opaque: true) { sunset(720) })
        l.append(PaperImageDef(id: "art.img.night", name: "Yıldızlı gece", usage: A, w: 720, h: 720, layout: cover(), opaque: true) { night(720) })
        l.append(PaperImageDef(id: "art.img.clouds", name: "Bulutlu gök", usage: A, w: 720, h: 720, layout: cover(), opaque: true) { clouds(720) })
        l.append(PaperImageDef(id: "art.img.marble", name: "Mermer", usage: A, w: 512, h: 512, layout: tile(), opaque: true) { marble(512) })
        l.append(PaperImageDef(id: "art.img.wood", name: "Ahşap masa", usage: A, w: 512, h: 512, layout: tile(), opaque: true) { woodTable(512) })
        // yazı kutusu (dokuz parçalı)
        l.append(PaperImageDef(id: "box.img.sticky_yellow", name: "Sarı yapışkan not", usage: B, w: 200, h: 200,
                               layout: nine(18, 30, 46, 46, [20, 32, 24, 26]), opaque: false) { sticky(0xFFFF_EE7A) })
        l.append(PaperImageDef(id: "box.img.sticky_pink", name: "Pembe yapışkan not", usage: B, w: 200, h: 200,
                               layout: nine(18, 30, 46, 46, [20, 32, 24, 26]), opaque: false) { sticky(0xFFFF_C6DC) })
        l.append(PaperImageDef(id: "box.img.tape", name: "Bantlı kağıt", usage: B, w: 220, h: 200,
                               layout: nine(62, 48, 62, 30, [22, 40, 22, 22]), opaque: false) { taped() })
        l.append(PaperImageDef(id: "box.img.spiral", name: "Spiral defter", usage: B, w: 240, h: 200,
                               layout: nine(36, 48, 36, 24, [20, 50, 20, 18], repeat_: true), opaque: false) { spiral() })
        l.append(PaperImageDef(id: "box.img.torn", name: "Yırtık kenarlı kağıt", usage: B, w: 200, h: 200,
                               layout: nine(40, 34, 40, 34, [18, 30, 18, 30], repeat_: true), opaque: false) { torn() })
        l.append(PaperImageDef(id: "box.img.kraft_label", name: "Kraft etiket", usage: B, w: 220, h: 160,
                               layout: nine(40, 40, 40, 40, [26, 24, 26, 24], repeat_: true), opaque: false) { kraftLabel() })
        l.append(PaperImageDef(id: "box.img.index_card", name: "Fiş kartı", usage: B, w: 240, h: 180,
                               layout: nine(20, 46, 20, 26, [20, 42, 20, 16], repeat_: true), opaque: false) { indexCard() })
        l.append(PaperImageDef(id: "box.img.clip", name: "Ataçlı not", usage: B, w: 220, h: 200,
                               layout: nine(64, 64, 24, 24, [22, 30, 22, 22]), opaque: false) { clipped() })
        l.append(PaperImageDef(id: "box.img.card_blue", name: "Mavi çerçeveli kart", usage: B, w: 200, h: 200,
                               layout: nine(26, 26, 26, 26, [22, 20, 22, 20]), opaque: false) { card() })
        l.append(PaperImageDef(id: "box.img.notebook", name: "Defter sayfası", usage: B, w: 240, h: 220,
                               layout: nine(56, 44, 20, 44, [54, 34, 16, 26], repeat_: true), opaque: false) { notebook() })
        return l
    }()

    static func byId(_ id: String) -> PaperImageDef? { all.first { $0.id == id } }

    static func of(_ usage: PaperUsage) -> [PaperImageDef] { all.filter { $0.usage == usage } }

    // MARK: - Kotlin sayı uyumu

    @inline(__always) private static func fsin(_ x: Float) -> Float { Float(sin(Double(x))) }
    @inline(__always) private static func fcos(_ x: Float) -> Float { Float(cos(Double(x))) }
    @inline(__always) private static func fexp(_ x: Float) -> Float { Float(exp(Double(x))) }
    /// Kotlin `Float.roundToInt()`.
    @inline(__always) private static func rnd(_ v: Float) -> Int { Int((Double(v) + 0.5).rounded(.down)) }
    @inline(__always) private static func clamp(_ v: Int, _ lo: Int, _ hi: Int) -> Int { min(max(v, lo), hi) }
    @inline(__always) private static func clampf(_ v: Float, _ lo: Float, _ hi: Float) -> Float { min(max(v, lo), hi) }
    @inline(__always) private static func floorMod(_ a: Int, _ b: Int) -> Int { let m = a % b; return m < 0 ? m + b : m }
    /// Kotlin Float `%` (kesilmiş kalan).
    @inline(__always) private static func fmod(_ a: Float, _ b: Float) -> Float { a.truncatingRemainder(dividingBy: b) }
    private static let PI = Double.pi
    private static let PIf = Float(Double.pi)

    // MARK: - gürültü

    private static func hash(_ x: Int, _ y: Int, _ seed: Int) -> Float {
        var h = Int32(truncatingIfNeeded: x) &* 374_761_393 &+ Int32(truncatingIfNeeded: y) &* 668_265_263 &+ Int32(truncatingIfNeeded: seed) &* 1_442_695_041
        h = (h ^ Int32(bitPattern: UInt32(bitPattern: h) >> 13)) &* 1_274_126_177
        h = h ^ Int32(bitPattern: UInt32(bitPattern: h) >> 16)
        return Float(h & 0x7FFF_FFFF) / 2_147_483_647
    }

    @inline(__always) private static func smooth(_ t: Float) -> Float { t * t * (3 - 2 * t) }

    /// Periyodik değer gürültüsü.
    private static func vnoise(_ u: Float, _ v: Float, _ cx: Int, _ cy: Int, _ seed: Int) -> Float {
        let fx = u * Float(cx), fy = v * Float(cy)
        let ix = Int(fx.rounded(.down)), iy = Int(fy.rounded(.down))
        let tx = smooth(fx - Float(ix)), ty = smooth(fy - Float(iy))
        func c(_ i: Int, _ j: Int) -> Float { hash(floorMod(i, cx), floorMod(j, cy), seed) }
        let a = c(ix, iy), b = c(ix + 1, iy), cc = c(ix, iy + 1), d = c(ix + 1, iy + 1)
        let top = a + (b - a) * tx
        return top + ((cc + (d - cc) * tx) - top) * ty
    }

    /// Kesirli Brown hareketi (periyodik), 0..1.
    private static func fbm(_ u: Float, _ v: Float, _ cells: Int, _ octaves: Int, _ seed: Int, _ gain: Float = 0.5, cellsY: Int? = nil) -> Float {
        var sum: Float = 0, amp: Float = 1, norm: Float = 0, c = cells, cy = cellsY ?? cells
        for o in 0..<octaves {
            sum += amp * vnoise(u, v, c, cy, seed + o * 97)
            norm += amp; amp *= gain; c *= 2; cy *= 2
        }
        return sum / norm
    }

    private static func smoothstep(_ a: Float, _ b: Float, _ x: Float) -> Float { smooth(clampf((x - a) / (b - a), 0, 1)) }

    private static func mix(_ a: UInt32, _ b: UInt32, _ t: Float) -> UInt32 {
        let k = clampf(t, 0, 1)
        func ch(_ s: UInt32) -> UInt32 {
            let x = Int(a >> s & 0xFF), y = Int(b >> s & 0xFF)
            return UInt32(clamp(rnd(Float(x) + Float(y - x) * k), 0, 255))
        }
        return ch(24) << 24 | ch(16) << 16 | ch(8) << 8 | ch(0)
    }

    private static func shade(_ c: UInt32, _ f: Float) -> UInt32 {
        func ch(_ s: UInt32) -> UInt32 { UInt32(clamp(rnd(Float(c >> s & 0xFF) * f), 0, 255)) }
        return (c & 0xFF00_0000) | ch(16) << 16 | ch(8) << 8 | ch(0)
    }

    /// Renk geçişi (duraklar [stops] 0..1).
    private static func ramp(_ colors: [UInt32], _ stops: [Float], _ t: Float) -> UInt32 {
        if t <= stops[0] { return colors[0] }
        for i in 1..<stops.count where t <= stops[i] { return mix(colors[i - 1], colors[i], (t - stops[i - 1]) / (stops[i] - stops[i - 1])) }
        return colors[colors.count - 1]
    }

    // MARK: - çizim yüzeyi

    /// Önçarpımsız ARGB piksel tablosu; şekiller işaretli uzaklıkla yumuşak kenarlı çizilir.
    private final class Raster {
        let w: Int, h: Int
        var px: [UInt32]

        init(_ w: Int, _ h: Int) { self.w = w; self.h = h; px = [UInt32](repeating: 0, count: w * h) }

        func each(_ f: (Int, Int) -> UInt32) {
            px.withUnsafeMutableBufferPointer { buf in
                var i = 0
                for y in 0..<h { for x in 0..<w { buf[i] = f(x, y); i += 1 } }
            }
        }

        /// Kaynak-üstüne karıştırma.
        func blend(_ i: Int, _ c: UInt32, _ alpha: Float) {
            let sa = (Float(c >> 24) / 255) * alpha
            if sa <= 0.002 { return }
            let d = px[i]
            let da = Float(d >> 24) / 255
            let oa = sa + da * (1 - sa)
            if oa <= 0 { return }
            func ch(_ s: UInt32) -> UInt32 {
                UInt32(PaperImages.clamp(PaperImages.rnd((Float(c >> s & 0xFF) * sa + Float(d >> s & 0xFF) * da * (1 - sa)) / oa), 0, 255))
            }
            px[i] = UInt32(PaperImages.clamp(PaperImages.rnd(oa * 255), 0, 255)) << 24 | ch(16) << 16 | ch(8) << 8 | ch(0)
        }

        /// [sdf] < 0 içeride; kenar 1 piksel yumuşak.
        func shape(_ l: Int, _ t: Int, _ r: Int, _ b: Int, _ sdf: (Float, Float) -> Float, _ color: (Int, Int) -> UInt32, _ alpha: Float = 1) {
            let y0 = max(0, t), y1 = min(h, b), x0 = max(0, l), x1 = min(w, r)
            if y0 >= y1 || x0 >= x1 { return }
            for y in y0..<y1 {
                for x in x0..<x1 {
                    let d = sdf(Float(x) + 0.5, Float(y) + 0.5)
                    let cov = PaperImages.clampf(0.5 - d, 0, 1)
                    if cov > 0 { blend(y * w + x, color(x, y), cov * alpha) }
                }
            }
        }

        /// Yumuşak gölge: [sdf] şeklinin [blur] piksel yayılmış hali.
        func shadow(_ sdf: (Float, Float) -> Float, _ blur: Float, _ alpha: Float, _ color: UInt32 = 0xFF00_0000) {
            for y in 0..<h {
                for x in 0..<w {
                    let d = sdf(Float(x) + 0.5, Float(y) + 0.5)
                    if d > blur { continue }
                    let k = 1 - smoothstepS(-blur * 0.6, blur, d)
                    if k > 0 { blend(y * w + x, color, k * alpha) }
                }
            }
        }

        func smoothstepS(_ a: Float, _ b: Float, _ x: Float) -> Float {
            let t = PaperImages.clampf((x - a) / (b - a), 0, 1)
            return t * t * (3 - 2 * t)
        }
    }

    private static func roundRect(_ x: Float, _ y: Float, _ l: Float, _ t: Float, _ r: Float, _ b: Float, _ rad: Float) -> Float {
        let cx = (l + r) / 2, cy = (t + b) / 2
        let qx = abs(x - cx) - ((r - l) / 2 - rad), qy = abs(y - cy) - ((b - t) / 2 - rad)
        let ox = max(qx, 0), oy = max(qy, 0)
        return (ox * ox + oy * oy).squareRoot() + min(max(qx, qy), 0) - rad
    }

    private static func segment(_ x: Float, _ y: Float, _ ax: Float, _ ay: Float, _ bx: Float, _ by: Float) -> Float {
        let px = x - ax, py = y - ay, dx = bx - ax, dy = by - ay
        let t = clampf((px * dx + py * dy) / max(dx * dx + dy * dy, 1e-6), 0, 1)
        let ex = px - dx * t, ey = py - dy * t
        return (ex * ex + ey * ey).squareRoot()
    }

    // MARK: - not tuvali

    private static func cream(_ n: Int) -> [UInt32] {
        let base: UInt32 = 0xFFFB_F6E9
        let r = Raster(n, n)
        let nf = Float(n)
        r.each { x, y in
            let u = Float(x) / nf, v = Float(y) / nf
            var f: Float = 1 + (fbm(u, v, 8, 4, 11) - 0.5) * 0.05
            f += (vnoise(u, v, 64, 6, 13) - 0.5) * 0.025 + (vnoise(u, v, 6, 64, 17) - 0.5) * 0.02
            if hash(x, y, 19) > 0.9975 { f -= 0.07 }
            return shade(base, f)
        }
        return r.px
    }

    private static func recycled(_ n: Int) -> [UInt32] {
        let base: UInt32 = 0xFFEC_E8DF
        let flecks: [UInt32] = [0xFF8C_8478, 0xFF6F_7F94, 0xFFA2_8A6A, 0xFFB9_B2A6]
        let cells = 48
        let cell = Float(n) / Float(cells)
        let r = Raster(n, n)
        let nf = Float(n)
        r.each { x, y in
            let u = Float(x) / nf, v = Float(y) / nf
            var c = shade(base, 1 + (fbm(u, v, 6, 4, 23) - 0.5) * 0.07 + (vnoise(u, v, 96, 96, 29) - 0.5) * 0.03)
            let ci = min(Int(Float(x) / cell), cells - 1), cj = min(Int(Float(y) / cell), cells - 1)
            if hash(ci, cj, 31) > 0.55 {
                let fx = (Float(ci) + 0.2 + 0.6 * hash(ci, cj, 37)) * cell, fy = (Float(cj) + 0.2 + 0.6 * hash(ci, cj, 41)) * cell
                let rad = 0.5 + 1.2 * hash(ci, cj, 43)
                let ddx = Float(x) + 0.5 - fx, ddy = Float(y) + 0.5 - fy
                let d = (ddx * ddx + ddy * ddy).squareRoot() - rad
                let cov = clampf(0.5 - d, 0, 1)
                if cov > 0 { c = mix(c, flecks[min(Int(hash(ci, cj, 47) * Float(flecks.count)), flecks.count - 1)], cov * 0.55) }
            }
            return c
        }
        return r.px
    }

    private static func kraft(_ n: Int) -> [UInt32] {
        let base: UInt32 = 0xFFE2_CCA4
        let r = Raster(n, n)
        let nf = Float(n)
        r.each { x, y in
            let u = Float(x) / nf, v = Float(y) / nf
            var f: Float = 1 + (fbm(u, v, 5, 4, 53) - 0.5) * 0.08
            f += (vnoise(u, v, 128, 8, 59) - 0.5) * 0.05 + (vnoise(u, v, 8, 128, 61) - 0.5) * 0.035
            if hash(x, y, 67) > 0.996 { f -= 0.12 }
            return shade(base, f)
        }
        return r.px
    }

    /// Dokuma (keten, tuval bezi): [threads] iplik, [depth] gölge derinliği.
    private static func weave(_ n: Int, _ threads: Int, _ base: UInt32, _ depth: Float, _ seed: Int) -> [UInt32] {
        let r = Raster(n, n)
        let nf = Float(n), tf = Float(threads)
        r.each { x, y in
            let u = Float(x) / nf, v = Float(y) / nf
            let su = Float(sin(Double(u * tf * 2) * PI)), sv = Float(sin(Double(v * tf * 2) * PI))
            let over = ((Int((u * tf).rounded(.down)) + Int((v * tf).rounded(.down))) & 1) == 0
            let warp = 0.5 + 0.5 * abs(su), weft = 0.5 + 0.5 * abs(sv)
            let slubH = vnoise(u, v, 3, threads, seed), slubV = vnoise(u, v, threads, 3, seed + 5)
            let thread = over ? weft * (0.8 + 0.4 * slubH) : warp * (0.8 + 0.4 * slubV)
            var f = 1 - depth * (1 - thread)
            f += (fbm(u, v, 4, 3, seed + 9) - 0.5) * 0.04
            return shade(base, f)
        }
        return r.px
    }

    private static func rice(_ n: Int) -> [UInt32] {
        let base: UInt32 = 0xFFFA_F8F2
        let r = Raster(n, n)
        let nf = Float(n)
        r.each { x, y in
            let u = Float(x) / nf, v = Float(y) / nf
            return shade(base, 1 + (fbm(u, v, 6, 4, 73) - 0.5) * 0.035)
        }
        for k in 0..<150 {
            var x = hash(k, 1, 79) * nf, y = hash(k, 2, 79) * nf
            var a = hash(k, 3, 79) * 2 * PIf
            let width = 0.5 + hash(k, 4, 79) * 0.9
            let dark = hash(k, 5, 79) > 0.55
            let color: UInt32 = dark ? 0xFFD9_D2C2 : 0xFFFF_FFFF
            let alpha: Float = dark ? 0.35 + 0.3 * hash(k, 6, 79) : 0.8
            let steps = 8 + Int(hash(k, 7, 79) * 16)
            for s in 0..<steps {
                a += (hash(k, 100 + s, 83) - 0.5) * 0.7
                let nx = x + fcos(a) * 7, ny = y + fsin(a) * 7
                for ox in -1...1 {
                    for oy in -1...1 {
                        let ax = x + Float(ox) * nf, ay = y + Float(oy) * nf, bx = nx + Float(ox) * nf, by = ny + Float(oy) * nf
                        let l = Int(min(ax, bx) - 3), t = Int(min(ay, by) - 3)
                        let rr = Int(max(ax, bx) + 4), bb = Int(max(ay, by) + 4)
                        if rr < 0 || bb < 0 || l >= n || t >= n { continue }
                        r.shape(l, t, rr, bb, { px, py in segment(px, py, ax, ay, bx, by) - width / 2 }, { _, _ in color }, alpha)
                    }
                }
                x = nx; y = ny
            }
        }
        return r.px
    }

    private static func parchment(_ w: Int, _ h: Int) -> [UInt32] {
        let light: UInt32 = 0xFFF5_E7C6, dark: UInt32 = 0xFFD4_B47C
        let r = Raster(w, h)
        r.each { x, y in
            let u = Float(x) / Float(w), v = Float(y) / Float(h)
            let blot = fbm(u, v, 3, 5, 89)
            var c = mix(light, dark, smoothstep(0.45, 0.85, blot) * 0.55)
            c = shade(c, 1 + (vnoise(u, v, 90, 90, 97) - 0.5) * 0.03)
            let ex = min(u, 1 - u) * 2, ey = min(v, 1 - v) * 2
            let edge = 1 - smoothstep(0, 0.32, min(ex, ey * 1.2) + (fbm(u, v, 6, 3, 101) - 0.5) * 0.12)
            return mix(c, 0xFFA9_7E45, edge * 0.55)
        }
        return r.px
    }

    private static func wash(_ w: Int, _ h: Int) -> [UInt32] {
        let blue: UInt32 = 0xFFB4_D0EC, peach: UInt32 = 0xFFF6_CDB4
        let r = Raster(w, h)
        r.each { x, y in
            let u = Float(x) / Float(w), v = Float(y) / Float(h)
            var c = shade(0xFFFF_FFFF, 1 + (fbm(u, v, 10, 3, 103) - 0.5) * 0.025)
            func bloom(_ cx: Float, _ cy: Float, _ rad: Float, _ color: UInt32, _ seed: Int) {
                let dx = (u - cx) * Float(w) / Float(h), dy = v - cy
                let d = (dx * dx + dy * dy).squareRoot() + (fbm(u, v, 4, 4, seed) - 0.5) * 0.18
                let inside = 1 - smoothstep(rad * 0.75, rad, d)
                let rim = smoothstep(rad * 0.82, rad * 0.97, d) * (1 - smoothstep(rad * 0.97, rad * 1.02, d))
                let a = inside * (0.38 + 0.12 * fbm(u, v, 8, 3, seed + 3)) + rim * 0.25
                c = mix(c, color, a)
            }
            bloom(0, 0, 0.42, blue, 107)
            bloom(1, 1, 0.40, peach, 113)
            return c
        }
        return r.px
    }

    private static func gradient(_ w: Int, _ h: Int, _ colors: [UInt32], _ stops: [Float]) -> [UInt32] {
        let r = Raster(w, h)
        r.each { x, y in
            let c = ramp(colors, stops, Float(y) / (Float(h) - 1))
            return shade(c, 1 + (hash(x, y, 127) - 0.5) * 0.012)
        }
        return r.px
    }

    // MARK: - resim tuvali

    private static func coldPress(_ n: Int) -> [UInt32] {
        let base: UInt32 = 0xFFFB_FAF6
        var hgt = [Float](repeating: 0, count: n * n)
        let nf = Float(n)
        for y in 0..<n {
            for x in 0..<n { hgt[y * n + x] = fbm(Float(x) / nf, Float(y) / nf, 16, 4, 131, 0.55) }
        }
        let r = Raster(n, n)
        r.each { x, y in
            let dx = hgt[y * n + (x + 1) % n] - hgt[y * n + (x - 1 + n) % n]
            let dy = hgt[((y + 1) % n) * n + x] - hgt[((y - 1 + n) % n) * n + x]
            let light = (-dx - dy) * 2.1
            return shade(base, 0.972 + clampf(light, -0.045, 0.04))
        }
        return r.px
    }

    private static func charcoal(_ n: Int) -> [UInt32] {
        let base: UInt32 = 0xFFCF_CAC1
        let r = Raster(n, n)
        let nf = Float(n)
        r.each { x, y in
            let u = Float(x) / nf, v = Float(y) / nf
            var f: Float = 1 + (fbm(u, v, 8, 3, 137) - 0.5) * 0.05
            f += (vnoise(u, v, 160, 160, 139) - 0.5) * 0.08
            f += Float(sin(Double(u * 64 * 2) * PI)) * 0.012
            return shade(base, f)
        }
        return r.px
    }

    private static func sunset(_ n: Int) -> [UInt32] {
        let colors: [UInt32] = [0xFF2D_3A86, 0xFF7A_4C9C, 0xFFD4_618A, 0xFFF2_9457, 0xFFFA_D07A, 0xFFFC_E6B0]
        let stops: [Float] = [0, 0.28, 0.5, 0.68, 0.82, 1]
        let r = Raster(n, n)
        let nf = Float(n)
        r.each { x, y in
            let u = Float(x) / nf, v = Float(y) / nf
            var c = ramp(colors, stops, v + (fbm(u, v, 3, 3, 149, cellsY: 6) - 0.5) * 0.05)
            let dx = u - 0.5, dy = (v - 0.8) * 1.3
            let glow = fexp(-(dx * dx + dy * dy) * 14)
            c = mix(c, 0xFFFF_F4D6, glow * 0.65)
            return shade(c, 1 + (hash(x, y, 151) - 0.5) * 0.012)
        }
        return r.px
    }

    private static func night(_ n: Int) -> [UInt32] {
        let r = Raster(n, n)
        let nf = Float(n)
        r.each { x, y in
            let u = Float(x) / nf, v = Float(y) / nf
            var c = mix(0xFF0A_1130, 0xFF26_3463, v)
            let band = fexp(-((u - v) * (u - v)) * 18)
            c = mix(c, 0xFF5B_6AA6, band * fbm(u, v, 5, 5, 157) * 0.5)
            return shade(c, 1 + (hash(x, y, 163) - 0.5) * 0.02)
        }
        let cells = 80
        let cell = nf / Float(cells)
        for j in 0..<cells {
            for i in 0..<cells {
                if hash(i, j, 167) < 0.78 { continue }
                let sx = (Float(i) + 0.15 + 0.7 * hash(i, j, 173)) * cell, sy = (Float(j) + 0.15 + 0.7 * hash(i, j, 179)) * cell
                let big = hash(i, j, 181) > 0.95
                let rad: Float = big ? 1.4 : 0.45 + 0.6 * hash(i, j, 191)
                let bright = 0.55 + 0.45 * hash(i, j, 193)
                let tint: UInt32 = hash(i, j, 197) > 0.7 ? 0xFFFF_E9C4 : 0xFFF2_F5FF
                let reach: Float = big ? 9 : 4
                r.shape(Int(sx - reach), Int(sy - reach), Int(sx + reach + 1), Int(sy + reach + 1),
                        { px, py in ((px - sx) * (px - sx) + (py - sy) * (py - sy)).squareRoot() - rad }, { _, _ in tint }, bright)
                let py0 = Int(sy - reach), py1 = Int(sy + reach), px0 = Int(sx - reach), px1 = Int(sx + reach)
                if py0 > py1 || px0 > px1 { continue }
                for py in py0...py1 {
                    for px in px0...px1 {
                        if px < 0 || px >= n || py < 0 || py >= n { continue }
                        let dx = Float(px) + 0.5 - sx, dy = Float(py) + 0.5 - sy
                        var g = fexp(-(dx * dx + dy * dy) / (rad * rad * 6)) * 0.35
                        if big { g += (fexp(-dy * dy * 3) * fexp(-abs(dx) / 3) + fexp(-dx * dx * 3) * fexp(-abs(dy) / 3)) * 0.25 }
                        r.blend(py * n + px, tint, g * bright)
                    }
                }
            }
        }
        return r.px
    }

    private static func clouds(_ n: Int) -> [UInt32] {
        let r = Raster(n, n)
        let nf = Float(n)
        r.each { x, y in
            let u = Float(x) / nf, v = Float(y) / nf
            var c = mix(0xFF6F_A6DA, 0xFFD3_E8F8, v)
            let f = fbm(u, v * 1.6, 4, 6, 211, 0.52)
            let cloud = smoothstep(0.5, 0.72, f)
            let under = fbm(u, (v + 0.02) * 1.6, 4, 6, 211, 0.52)
            let shadeK = 1 - 0.12 * smoothstep(0.55, 0.8, under) * (1 - smoothstep(0.6, 0.85, f))
            c = mix(c, shade(0xFFFF_FFFF, shadeK), cloud * 0.95)
            return shade(c, 1 + (hash(x, y, 223) - 0.5) * 0.01)
        }
        return r.px
    }

    private static func marble(_ n: Int) -> [UInt32] {
        let base: UInt32 = 0xFFF5_F3EF, vein: UInt32 = 0xFF8E_949B
        let r = Raster(n, n)
        let nf = Float(n)
        r.each { x, y in
            let u = Float(x) / nf, v = Float(y) / nf
            let t = fbm(u, v, 3, 6, 227)
            // Kotlin: ((u*2 + v*1) * 2 * PI + t * 9).toFloat() — Double hesap
            let a1: Float = (u * 2 + v * 1) * 2, b1: Float = t * 9
            let s = abs(fsin(Float(Double(a1) * PI + Double(b1))))
            let main = 1 - smoothstep(0, 0.09, s)
            let a2: Float = (u * 1 - v * 3) * 2, b2: Float = fbm(u, v, 4, 5, 229) * 11
            let s2 = abs(fsin(Float(Double(a2) * PI + Double(b2))))
            let thin = 1 - smoothstep(0, 0.04, s2)
            var c = shade(base, 1 + (fbm(u, v, 6, 3, 233) - 0.5) * 0.05)
            c = mix(c, vein, main * 0.55 + thin * 0.3)
            return c
        }
        return r.px
    }

    private static func woodTable(_ n: Int) -> [UInt32] {
        let light: UInt32 = 0xFFCD_A06A, dark: UInt32 = 0xFF94_663A
        let planks = 4
        let r = Raster(n, n)
        let nf = Float(n)
        r.each { x, y in
            let u = Float(x) / nf, v = Float(y) / nf
            let pi = min(Int((v * Float(planks)).rounded(.down)), planks - 1)
            let local = v * Float(planks) - Float(pi)
            let warp = fbm(u, v, 2, 4, 241 + pi * 7, cellsY: 8) * 1.6
            let ring = fmod(fmod(local * 9 + warp, 1) + 1, 1)
            let line = smoothstep(0.82, 0.97, ring) * (1 - smoothstep(0.97, 1, ring))
            let fine = vnoise(u, v, 3, 220, 251 + pi)
            let tone = fbm(u, v, 4, 3, 259 + pi, cellsY: 16)
            var c = mix(light, dark, tone * 0.35 + line * 0.45 + fine * 0.18 + hash(pi, 0, 257) * 0.12)
            let seam = min(local, 1 - local) * nf / Float(planks)
            if seam < 1.6 { c = shade(c, 0.55 + 0.28 * seam) }
            let joint = fmod(hash(pi, 1, 263) + 0.5 * Float(pi & 1), 1)
            let dj = abs(u - joint) * nf
            if min(dj, nf - dj) < 1.3 { c = shade(c, 0.62) }
            return c
        }
        return r.px
    }

    // MARK: - yazı kutusu

    private static func paperBase(_ color: UInt32, _ seed: Int, periodX: Float = 0, periodY: Float = 0) -> (Int, Int) -> UInt32 {
        { x, y in
            let u = periodX > 0 ? Float(x) / periodX : Float(x) / 97
            let v = periodY > 0 ? Float(y) / periodY : Float(y) / 97
            return shade(color, 1 + (vnoise(u, v, 6, 6, seed) - 0.5) * 0.03)
        }
    }

    private static func sticky(_ color: UInt32) -> [UInt32] {
        let w = 200, h = 200
        let r = Raster(w, h)
        let l: Float = 8, t: Float = 6, rr: Float = 190, bb: Float = 188
        r.shadow({ x, y in roundRect(x, y, l + 2, t + 5, rr + 1, bb + 4, 2) }, 9, 0.22)
        r.shadow({ x, y in roundRect(x, y, rr - 22, bb - 18, rr + 2, bb + 5, 10) }, 6, 0.1)
        let base = paperBase(color, 271)
        r.shape(0, 0, w, h, { x, y in roundRect(x, y, l, t, rr, bb, 1.5) }, { x, y in
            var c = base(x, y)
            if Float(y) < t + 24 { c = shade(c, 0.975) }
            let d = ((rr - Float(x)) + (bb - Float(y))) / 56
            if d < 1 { c = shade(c, 0.9 + 0.1 * d + (d < 0.25 ? 0.06 : 0)) }
            return c
        })
        return r.px
    }

    private static func taped() -> [UInt32] {
        let w = 220, h = 200
        let r = Raster(w, h)
        let l: Float = 10, t: Float = 12, rr: Float = 208, bb: Float = 188
        r.shadow({ x, y in roundRect(x, y, l + 1, t + 4, rr + 1, bb + 4, 2) }, 8, 0.2)
        let base = paperBase(0xFFFF_FEFA, 277)
        r.shape(0, 0, w, h, { x, y in roundRect(x, y, l, t, rr, bb, 1) }, base)
        func tape(_ cx: Float, _ cy: Float, _ angle: Float, _ color: UInt32) {
            let ca = fcos(angle), sa = fsin(angle)
            let hl: Float = 28, hw: Float = 9.5
            r.shape(Int(cx - 34), Int(cy - 34), Int(cx + 34), Int(cy + 34), { x, y in
                let dx = x - cx, dy = y - cy
                let a = dx * ca + dy * sa, b = -dx * sa + dy * ca
                let jag = abs(fmod(fmod(b * 0.9, 3) + 3, 3) - 1.5) * 0.8
                return max(abs(a) - hl + jag, abs(b) - hw)
            }, { x, y in
                let dx = Float(x) - cx, dy = Float(y) - cy
                let b = -dx * sa + dy * ca
                return Int((b + 20) / 4) % 2 == 0 ? shade(color, 0.94) : color
            }, 0.78)
        }
        tape(30, 20, Float(-38 * PI / 180), 0xFF9B_D3C8)
        tape(190, 20, Float(38 * PI / 180), 0xFFF5_B7C5)
        return r.px
    }

    private static func spiral() -> [UInt32] {
        let w = 240, h = 200
        let r = Raster(w, h)
        let l: Float = 6, t: Float = 22, rr: Float = 234, bb: Float = 192
        r.shadow({ x, y in roundRect(x, y, l + 1, t + 4, rr + 1, bb + 4, 2) }, 7, 0.2)
        let base = paperBase(0xFFFF_FFFF, 281, periodX: 168, periodY: 128)
        r.shape(0, 0, w, h, { x, y in roundRect(x, y, l, t, rr, bb, 1.5) }, base)
        var hx: Float = 12
        while hx < Float(w) + 12 {
            let cx = hx
            r.shape(Int(cx - 7), 28, Int(cx + 8), 42, { x, y in ((x - cx) * (x - cx) + (y - 35) * (y - 35)).squareRoot() - 4.5 },
                    { _, _ in 0xFF4D_5258 }, 0.85)
            r.shape(Int(cx - 8), 0, Int(cx + 8), 40, { x, y in
                let k = clampf((35 - y) / 31, 0, 1)
                let wx = cx + 5 * fsin(k * PIf)
                return (y < 4 || y > 35) ? 99 : abs(x - wx) - 2.4
            }, { x, _ in
                let k = clampf((Float(x) - cx) / 6, -1, 1)
                return mix(0xFFE9_ECEF, 0xFF7E_8690, 0.5 + 0.5 * k)
            })
            hx += 24
        }
        return r.px
    }

    /// Periyodik yırtık kenar yüksekliği.
    private static func tornEdge(_ x: Float, _ period: Float, _ seed: Int) -> Float {
        let u = x / period
        return (vnoise(u, 0.5, 6, 1, seed) - 0.5) * 5 + (vnoise(u, 0.5, 24, 1, seed + 1) - 0.5) * 2.5 + (vnoise(u, 0.5, 60, 1, seed + 2) - 0.5) * 1.2
    }

    private static func torn() -> [UInt32] {
        let w = 200, h = 200
        let r = Raster(w, h)
        let period: Float = 120
        let top: Float = 18, bottom: Float = 182, l: Float = 8, rr: Float = 192
        func sdf(_ x: Float, _ y: Float, _ dy: Float) -> Float {
            let tt = top + tornEdge(x, period, 293) + dy
            let bt = bottom + tornEdge(x + 37, period, 307) + dy
            let vertical = max(tt - y, y - bt)
            let periodY: Float = 132
            let lt = l + tornEdge(y, periodY, 311) * 0.25
            let rt = rr + tornEdge(y + 11, periodY, 313) * 0.25
            return max(vertical, max(lt - x, x - rt))
        }
        r.shadow({ x, y in sdf(x, y, 3) }, 6, 0.2)
        let base = paperBase(0xFFFD_FCF7, 317, periodX: period, periodY: 132)
        r.shape(0, 0, w, h, { x, y in sdf(x, y, 0) }, { x, y in
            let d = -sdf(Float(x) + 0.5, Float(y) + 0.5, 0)
            return d < 2.2 ? 0xFFFF_FFFF : base(x, y)
        })
        return r.px
    }

    private static func kraftLabel() -> [UInt32] {
        let w = 220, h = 160
        let r = Raster(w, h)
        let px: Float = 140, py: Float = 80
        r.shadow({ x, y in roundRect(x, y, 7, 8, 214, 155, 14) }, 6, 0.2)
        r.shape(0, 0, w, h, { x, y in roundRect(x, y, 6, 5, 214, 152, 14) }, { x, y in
            let u = Float(x) / px, v = Float(y) / py
            var f: Float = 1 + (vnoise(u, v, 4, 2, 331) - 0.5) * 0.06 + (vnoise(u, v, 28, 2, 337) - 0.5) * 0.05
            if hash(floorMod(x, 140), floorMod(y, 80), 347) > 0.996 { f -= 0.1 }
            return shade(0xFFD5_B588, f)
        })
        r.shape(0, 0, w, h, { x, y in
            let d = abs(roundRect(x, y, 16, 15, 204, 142, 8)) - 0.9
            let along = (abs(y - 15) < 3 || abs(y - 142) < 3) ? x : y
            let dash: Float = fmod(fmod(along, 10) + 10, 10) < 6 ? 0 : 9
            return d + dash
        }, { _, _ in 0xFFFF_F6E2 }, 0.9)
        return r.px
    }

    private static func indexCard() -> [UInt32] {
        let w = 240, h = 180
        let r = Raster(w, h)
        r.shadow({ x, y in roundRect(x, y, 7, 8, 233, 176, 3) }, 6, 0.2)
        r.shape(0, 0, w, h, { x, y in roundRect(x, y, 6, 5, 234, 173, 3) }, { x, y in
            var c: UInt32 = 0xFFFF_FFFF
            let yy = Float(y) + 0.5
            if abs(yy - 34) < 0.9 { c = mix(c, 0xFFE5_7373, 0.85) }
            else if yy > 46 {
                let k = fmod(fmod(yy - 52, 18) + 18, 18)
                if k < 1.1 || k > 17.6 { c = mix(c, 0xFF8F_B3D9, 0.7) }
            }
            return shade(c, 1 + (vnoise(Float(x) / 200, Float(y) / 108, 4, 4, 353) - 0.5) * 0.02)
        })
        return r.px
    }

    private static func clipped() -> [UInt32] {
        let w = 220, h = 200
        let r = Raster(w, h)
        let l: Float = 10, t: Float = 16, rr: Float = 208, bb: Float = 188
        r.shadow({ x, y in roundRect(x, y, l + 1, t + 4, rr + 1, bb + 4, 2) }, 8, 0.2)
        r.shape(0, 0, w, h, { x, y in roundRect(x, y, l, t, rr, bb, 1) }, paperBase(0xFFFF_FDF8, 359))
        let ang = Float(-12 * PI / 180)
        let ca = fcos(ang), sa = fsin(ang)
        let cx: Float = 40, cy: Float = 22
        func loop(_ x: Float, _ y: Float, _ half: Float, _ rad: Float, _ oy: Float) -> Float {
            let dx = x - cx, dy = y - cy
            let a = dx * ca + dy * sa, b = -dx * sa + dy * ca - oy
            let seg = segment(a, b, 0, -half, 0, half)
            return abs(seg - rad)
        }
        let sdf: (Float, Float) -> Float = { x, y in min(loop(x, y, 18, 7, 0), max(loop(x, y, 12, 4, 4), -1)) - 1.6 }
        r.shadow({ x, y in sdf(x - 1.5, y - 2) }, 3, 0.25)
        r.shape(20, 0, 64, 60, sdf, { x, y in mix(0xFFEE_F1F4, 0xFF8A_939C, Float((x + y) % 7) / 7 * 0.4 + 0.2) })
        return r.px
    }

    private static func card() -> [UInt32] {
        let w = 200, h = 200
        let r = Raster(w, h)
        r.shadow({ x, y in roundRect(x, y, 9, 10, 192, 194, 16) }, 8, 0.18)
        r.shape(0, 0, w, h, { x, y in roundRect(x, y, 8, 6, 192, 190, 16) }, { _, _ in 0xFF6F_A3D8 })
        r.shape(0, 0, w, h, { x, y in roundRect(x, y, 11, 9, 189, 187, 13) }, { _, _ in 0xFFF6_FAFF })
        return r.px
    }

    private static func notebook() -> [UInt32] {
        let w = 240, h = 220
        let r = Raster(w, h)
        let l: Float = 6, t: Float = 6, rr: Float = 234, bb: Float = 214
        r.shadow({ x, y in roundRect(x, y, l + 1, t + 4, rr + 1, bb + 4, 2) }, 6, 0.2)
        r.shape(0, 0, w, h, { x, y in roundRect(x, y, l, t, rr, bb, 1.5) }, { x, y in
            var c: UInt32 = 0xFFFF_FFFF
            let yy = Float(y) + 0.5, xx = Float(x) + 0.5
            let k = fmod(fmod(yy - 55, 22) + 22, 22)
            if yy > 40 && yy < 205 && (k < 1 || k > 21.6) { c = mix(c, 0xFF9D_BEE0, 0.75) }
            if abs(xx - 46) < 0.9 { c = mix(c, 0xFFE5_7373, 0.8) }
            return shade(c, 1 + (vnoise(Float(x) / 164, Float(y) / 132, 4, 4, 367) - 0.5) * 0.02)
        })
        var hy: Float = 22
        while hy < Float(h) + 22 {
            let cy = hy
            r.shape(8, Int(cy - 8), 26, Int(cy + 9), { x, y in ((x - 17) * (x - 17) + (y - cy) * (y - cy)).squareRoot() - 5.5 },
                    { _, _ in 0xFFDF_E3E8 })
            hy += 44
        }
        return r.px
    }
}
