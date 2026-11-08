import Foundation

/// Doku: w×h ARGB piksel (kenarları döşenebilir).
final class Texture {
    let w: Int, h: Int
    let px: [UInt32]
    init(_ w: Int, _ h: Int, _ px: [UInt32]) { precondition(px.count == w * h); self.w = w; self.h = h; self.px = px }
}

/// Hazır dokunun tanımı.
struct TextureDef {
    let id: String, name: String, category: String
    let generate: (Int) -> Texture
}

/// v23 hazır 3B dokuları: program içinde üretilen döşenebilir resimler (Android `TextureCatalog` ile piksel piksel aynı).
enum TextureCatalog {

    static let VERSION = 1
    static let SIZE = 256

    private static let WOOD = "Ahşap", STONE = "Taş ve yapı", METAL = "Metal", FABRIC = "Kumaş ve deri", NATURE = "Doğa", PATTERN = "Desen"

    private static func C(_ v: UInt32) -> UInt32 { v }

    static let all: [TextureDef] = [
        TextureDef(id: "wood.oak", name: "Meşe", category: WOOD) { wood($0, 0xFFB98B55, 0xFF8A5C2E, 7, 11) },
        TextureDef(id: "wood.walnut", name: "Ceviz", category: WOOD) { wood($0, 0xFF7A4E2D, 0xFF4A2C17, 9, 23) },
        TextureDef(id: "wood.pine", name: "Çam", category: WOOD) { wood($0, 0xFFE2C08A, 0xFFB98A4E, 5, 37) },
        TextureDef(id: "wood.planks", name: "Tahta döşeme", category: WOOD) { planks($0) },
        TextureDef(id: "stone.marble", name: "Mermer", category: STONE) { marble($0, 0xFFF1EFEA, 0xFF9AA0A6, 3) },
        TextureDef(id: "stone.marble_dark", name: "Siyah mermer", category: STONE) { marble($0, 0xFF2B2D31, 0xFFD9D4C7, 5) },
        TextureDef(id: "stone.granite", name: "Granit", category: STONE) { granite($0) },
        TextureDef(id: "stone.brick", name: "Tuğla", category: STONE) { brick($0) },
        TextureDef(id: "stone.cobble", name: "Parke taşı", category: STONE) { cobble($0) },
        TextureDef(id: "stone.tiles", name: "Seramik karo", category: STONE) { tiles($0) },
        TextureDef(id: "stone.concrete", name: "Beton", category: STONE) { noiseColor($0, 0xFFA7A9AC, 0xFF8B8E92, 6, 0.9, 41) },
        TextureDef(id: "metal.brushed", name: "Fırçalanmış metal", category: METAL) { brushed($0, 0xFFC9CDD2) },
        TextureDef(id: "metal.gold", name: "Altın", category: METAL) { brushed($0, 0xFFE2B84B) },
        TextureDef(id: "metal.rust", name: "Paslı demir", category: METAL) { rust($0) },
        TextureDef(id: "metal.plate", name: "Baklavalı sac", category: METAL) { diamondPlate($0) },
        TextureDef(id: "fabric.canvas", name: "Kanvas", category: FABRIC) { weave($0, 0xFFE9DFC8, 0xFFCDBF9F, 4) },
        TextureDef(id: "fabric.denim", name: "Kot", category: FABRIC) { denim($0) },
        TextureDef(id: "fabric.knit", name: "Örgü", category: FABRIC) { knit($0) },
        TextureDef(id: "fabric.leather", name: "Deri", category: FABRIC) { leather($0) },
        TextureDef(id: "fabric.plaid", name: "Ekose", category: FABRIC) { plaid($0) },
        TextureDef(id: "nature.grass", name: "Çimen", category: NATURE) { grass($0) },
        TextureDef(id: "nature.sand", name: "Kum", category: NATURE) { noiseColor($0, 0xFFE6CF9A, 0xFFC9AC70, 5, 0.6, 53) },
        TextureDef(id: "nature.water", name: "Su", category: NATURE) { water($0) },
        TextureDef(id: "nature.lava", name: "Lav", category: NATURE) { lava($0) },
        TextureDef(id: "nature.ice", name: "Buz", category: NATURE) { ice($0) },
        TextureDef(id: "nature.bark", name: "Ağaç kabuğu", category: NATURE) { bark($0) },
        TextureDef(id: "pattern.checker", name: "Dama", category: PATTERN) { checker($0) },
        TextureDef(id: "pattern.stripes", name: "Şerit", category: PATTERN) { stripes($0) },
        TextureDef(id: "pattern.dots", name: "Puantiye", category: PATTERN) { dots($0) },
        TextureDef(id: "pattern.carbon", name: "Karbon fiber", category: PATTERN) { carbon($0) },
        TextureDef(id: "pattern.hex", name: "Petek", category: PATTERN) { hexes($0) },
    ]

    static func byId(_ id: String) -> TextureDef? { all.first { $0.id == id } }

    static var categories: [String] { all.map(\.category).ktDistinct() }

    // MARK: gürültü

    private static func hash(_ x: Int, _ y: Int, _ seed: Int) -> Float {
        var h = Int32(truncatingIfNeeded: x) &* 374761393 &+ Int32(truncatingIfNeeded: y) &* 668265263 &+ Int32(truncatingIfNeeded: seed) &* 1442695041
        h = (h ^ Int32(bitPattern: UInt32(bitPattern: h) >> 13)) &* 1274126177
        h = h ^ Int32(bitPattern: UInt32(bitPattern: h) >> 16)
        return Float(h & 0x7FFF_FFFF) / 2147483647
    }

    private static func smooth(_ t: Float) -> Float { t * t * (3 - 2 * t) }

    private static func vnoise(_ x: Float, _ y: Float, _ cells: Int, _ seed: Int) -> Float { vnoise2(x, y, cells, cells, seed) }

    private static func vnoise2(_ x: Float, _ y: Float, _ cx: Int, _ cy: Int, _ seed: Int) -> Float {
        let fx = x * Float(cx), fy = y * Float(cy)
        let ix = Int(fx.rounded(.down)), iy = Int(fy.rounded(.down))
        let tx = smooth(fx - Float(ix)), ty = smooth(fy - Float(iy))
        func c(_ i: Int, _ j: Int) -> Float { hash(floorModI(i, cx), floorModI(j, cy), seed) }
        let a = c(ix, iy), b = c(ix + 1, iy), cc = c(ix, iy + 1), d = c(ix + 1, iy + 1)
        let top = a + (b - a) * tx
        let bot = cc + (d - cc) * tx
        return top + (bot - top) * ty
    }

    private static func fbm(_ x: Float, _ y: Float, _ cells: Int, _ octaves: Int, _ seed: Int, _ gain: Float = 0.5) -> Float {
        var sum: Float = 0, amp: Float = 1, norm: Float = 0, c = cells
        for o in 0..<octaves {
            sum += amp * vnoise(x, y, c, seed + o * 97)
            norm += amp; amp *= gain; c *= 2
        }
        return sum / norm
    }

    private static func ch(_ c: UInt32, _ sh: UInt32) -> Int { Int((c >> sh) & 0xFF) }

    private static func lerpColor(_ a: UInt32, _ b: UInt32, _ t: Float) -> UInt32 {
        let k = t.coerceIn(0, 1)
        let r = javaRoundF(Float(ch(a, 16)) + Float(ch(b, 16) - ch(a, 16)) * k)
        let g = javaRoundF(Float(ch(a, 8)) + Float(ch(b, 8) - ch(a, 8)) * k)
        let bl = javaRoundF(Float(ch(a, 0)) + Float(ch(b, 0) - ch(a, 0)) * k)
        return 0xFF00_0000 | UInt32(truncatingIfNeeded: r << 16 | g << 8 | bl)
    }

    private static func shade(_ c: UInt32, _ f: Float) -> UInt32 {
        let r = javaRoundF(Float(ch(c, 16)) * f).coerceIn(0, 255)
        let g = javaRoundF(Float(ch(c, 8)) * f).coerceIn(0, 255)
        let b = javaRoundF(Float(ch(c, 0)) * f).coerceIn(0, 255)
        return 0xFF00_0000 | UInt32(r << 16 | g << 8 | b)
    }

    private static func make(_ n: Int, _ f: (Float, Float) -> UInt32) -> Texture {
        var px = [UInt32](repeating: 0, count: n * n)
        for j in 0..<n { for i in 0..<n { px[j * n + i] = f((Float(i) + 0.5) / Float(n), (Float(j) + 0.5) / Float(n)) } }
        return Texture(n, n, px)
    }

    private static func fract(_ v: Float) -> Float { v - v.rounded(.down) }
    private static func fl(_ v: Float) -> Int { Int(v.rounded(.down)) }

    // MARK: üreticiler

    private static func wood(_ n: Int, _ light: UInt32, _ dark: UInt32, _ rings: Float, _ seed: Int) -> Texture {
        make(n) { x, y in
            let warp = fbm(x, y, 4, 4, seed) - 0.5
            let grain = fbm(x, y, 2, 3, seed + 5)
            let v = fract((x + warp * 0.25) * rings + grain * 1.5)
            let ring = smooth(abs(v * 2 - 1))
            let fiber = vnoise(x * 1, y, 64, seed + 9) * 0.25
            return lerpColor(light, dark, ring * 0.75 + fiber)
        }
    }

    private static func planks(_ n: Int) -> Texture {
        make(n) { x, y in
            let rows = 4
            let row = fl(y * Float(rows))
            let off = (Float(row) * 0.37).truncatingRemainder(dividingBy: 1)
            let px = fract(x + off)
            let board = fl(px * 2)
            let base: UInt32 = (row + board) % 2 == 0 ? 0xFFB98B55 : 0xFFA87A47
            let warp = fbm(px, y, 4, 3, 71 + row) - 0.5
            let ring = smooth(abs(fract((y * Float(rows) - Float(row)) * 3 + warp * 0.8 + Float(board) * 0.3) * 2 - 1))
            var c = lerpColor(base, 0xFF6E4524, ring * 0.45)
            let edgeY = abs(fract(y * Float(rows)) - 0.5) * 2
            let edgeX = abs(fract(px * 2) - 0.5) * 2
            if edgeY > 0.965 || edgeX > 0.985 { c = shade(c, 0.55) }
            return c
        }
    }

    private static func marble(_ n: Int, _ base: UInt32, _ vein: UInt32, _ seed: Int) -> Texture {
        make(n) { x, y in
            let t = fbm(x, y, 3, 5, seed)
            let v = ktSinF(((x + y) * 3 + t * 6) * PI_F)
            let veins = 1 - abs(v).squareRoot()
            let soft = fbm(x, y, 6, 3, seed + 3) * 0.12
            return lerpColor(base, vein, (veins * veins * 0.9 + soft).coerceIn(0, 1))
        }
    }

    private static func granite(_ n: Int) -> Texture {
        make(n) { x, y in
            let a = vnoise(x, y, 96, 5), b = vnoise(x, y, 48, 6), c = fbm(x, y, 8, 3, 7)
            var col = lerpColor(0xFF8D8A86, 0xFFB9B5AE, c)
            if a > 0.82 { col = 0xFF2E2C2A } else if b > 0.86 { col = 0xFFD8C8B8 }
            return col
        }
    }

    private static func brick(_ n: Int) -> Texture {
        make(n) { x, y in
            let rows = 8
            let ry = y * Float(rows)
            let row = fl(ry)
            let bx = x * 4 + (row % 2 == 0 ? 0 : 0.5)
            let mortarY = fract(ry) < 0.1
            let mortarX = fract(bx) < 0.05
            if mortarY || mortarX { return lerpColor(0xFFD7D2C8, 0xFFB3ADA3, vnoise(x, y, 64, 3)) }
            let id = floorModI(fl(bx), 4) + row * 7
            let tone = hash(id, row, 11)
            let base = lerpColor(0xFFB5543A, 0xFF8E3B27, tone)
            return shade(base, 0.85 + 0.3 * fbm(x, y, 16, 3, 13))
        }
    }

    private static func cobble(_ n: Int) -> Texture {
        make(n) { x, y in
            let cells = 6
            let fx = x * Float(cells), fy = y * Float(cells)
            let ix = fl(fx), iy = fl(fy)
            var d1: Float = 9, d2: Float = 9, id = 0
            for j in -1...1 { for i in -1...1 {
                let cx = ix + i, cy = iy + j
                let px = Float(cx) + 0.2 + 0.6 * hash(floorModI(cx, cells), floorModI(cy, cells), 21)
                let py = Float(cy) + 0.2 + 0.6 * hash(floorModI(cx, cells), floorModI(cy, cells), 22)
                let d = ((fx - px) * (fx - px) + (fy - py) * (fy - py)).squareRoot()
                if d < d1 { d2 = d1; d1 = d; id = floorModI(cx, cells) * 31 + floorModI(cy, cells) } else if d < d2 { d2 = d }
            } }
            let edge = d2 - d1
            if edge < 0.08 { return 0xFF4A4642 }
            let tone = hash(id, 3, 23)
            let base = lerpColor(0xFF9C958C, 0xFF6F6A64, tone)
            return shade(base, 0.8 + 0.35 * min(1, edge * 2.2) + 0.1 * fbm(x, y, 16, 2, 24))
        }
    }

    private static func tiles(_ n: Int) -> Texture {
        make(n) { x, y in
            let k: Float = 4
            let gx = fract(x * k), gy = fract(y * k)
            if gx < 0.04 || gy < 0.04 { return 0xFFCFCAC0 }
            let tone = hash(fl(x * k), fl(y * k), 31)
            let base = lerpColor(0xFFF4F6F7, 0xFFDCE6EA, tone * 0.6)
            let gloss = 1 + 0.06 * (1 - abs(gx - gy))
            return shade(base, gloss)
        }
    }

    private static func noiseColor(_ n: Int, _ a: UInt32, _ b: UInt32, _ cells: Int, _ contrast: Float, _ seed: Int) -> Texture {
        make(n) { x, y in
            let t = fbm(x, y, cells, 5, seed)
            let speck = vnoise(x, y, 128, seed + 1)
            var c = lerpColor(a, b, ((t - 0.5) * 2 * contrast + 0.5).coerceIn(0, 1))
            if speck > 0.9 { c = shade(c, 0.82) }
            return c
        }
    }

    private static func brushed(_ n: Int, _ base: UInt32) -> Texture {
        make(n) { x, y in
            let line = vnoise2(x, y, 2, 128, 61) * 0.6 + vnoise2(x, y, 4, 256, 62) * 0.4
            let s = ktSinF((y * 2 + 0.1) * PI_F * 2)
            let band = 0.85 + 0.25 * ((s + 1) / 2)
            return shade(base, (0.78 + 0.32 * line) * band)
        }
    }

    private static func rust(_ n: Int) -> Texture {
        make(n) { x, y in
            let t = fbm(x, y, 4, 5, 81)
            let spots = fbm(x, y, 8, 4, 82)
            let metal = lerpColor(0xFF6E737A, 0xFF50555B, vnoise(x, y, 64, 83))
            let rustC = lerpColor(0xFFB3561F, 0xFF6B2E12, spots)
            return lerpColor(metal, rustC, ((t - 0.35) * 3).coerceIn(0, 1))
        }
    }

    private static func diamondPlate(_ n: Int) -> Texture {
        make(n) { x, y in
            let k: Float = 8
            let gx = fract(x * k), gy = fract(y * k)
            let odd = (fl(x * k) + fl(y * k)) % 2 == 0
            let ux = gx - 0.5, uy = gy - 0.5
            let a = odd ? ux + uy : ux - uy
            let b = odd ? ux - uy : ux + uy
            let ridge = abs(a) < 0.32 && abs(b) < 0.08
            let base = shade(0xFFB0B5BB, 0.85 + 0.2 * vnoise(x, y, 128, 91))
            return ridge ? shade(base, b > 0 ? 1.25 : 0.8) : base
        }
    }

    private static func weave(_ n: Int, _ a: UInt32, _ b: UInt32, _ cells: Int) -> Texture {
        make(n) { x, y in
            let k: Float = 48
            let gx = fract(x * k), gy = fract(y * k)
            let over = (fl(x * k) + fl(y * k)) % 2 == 0
            let thread = over ? ktSinF(gx * PI_F) : ktSinF(gy * PI_F)
            let c = lerpColor(a, b, fbm(x, y, cells, 3, 101) * 0.6)
            return shade(c, 0.82 + 0.22 * thread)
        }
    }

    private static func denim(_ n: Int) -> Texture {
        make(n) { x, y in
            let k: Float = 64
            let d = fract((x + y) * k)
            let twill = 0.75 + 0.3 * ktSinF(d * PI_F)
            let c = lerpColor(0xFF3A5A8C, 0xFF223A63, fbm(x, y, 8, 3, 111))
            let fleck = vnoise(x, y, 256, 112)
            return shade(fleck > 0.88 ? lerpColor(c, 0xFFB8C6DB, 0.5) : c, twill)
        }
    }

    private static func knit(_ n: Int) -> Texture {
        make(n) { x, y in
            let k: Float = 16
            let gx = fract(x * k), gy = fract(y * k * 1.5)
            let v = abs(gx - 0.5) * 2
            let loop = 1 - abs(gy - v * 0.6 - 0.2) * 2.2
            return shade(0xFFC2453F, 0.6 + 0.55 * loop.coerceIn(0, 1))
        }
    }

    private static func leather(_ n: Int) -> Texture {
        make(n) { x, y in
            let cells = 24
            let fx = x * Float(cells), fy = y * Float(cells)
            let ix = fl(fx), iy = fl(fy)
            var d1: Float = 9, d2: Float = 9
            for j in -1...1 { for i in -1...1 {
                let cx = ix + i, cy = iy + j
                let px = Float(cx) + hash(floorModI(cx, cells), floorModI(cy, cells), 121)
                let py = Float(cy) + hash(floorModI(cx, cells), floorModI(cy, cells), 122)
                let d = (fx - px) * (fx - px) + (fy - py) * (fy - py)
                if d < d1 { d2 = d1; d1 = d } else if d < d2 { d2 = d }
            } }
            let crease = (d2.squareRoot() - d1.squareRoot()).coerceIn(0, 0.3) / 0.3
            let c = lerpColor(0xFF6B3F22, 0xFF8A5530, fbm(x, y, 4, 3, 123))
            return shade(c, 0.7 + 0.35 * crease)
        }
    }

    private static func plaid(_ n: Int) -> Texture {
        make(n) { x, y in
            func band(_ v: Float) -> Float {
                let f = fract(v * 4)
                if f < 0.18 { return 1 }
                if f >= 0.45 && f <= 0.52 { return 0.6 }
                return 0
            }
            let h = band(x), v = band(y)
            var c: UInt32 = 0xFF2F5D3A
            c = lerpColor(c, 0xFFB23A3A, max(h, v) * 0.8)
            if h > 0 && v > 0 { c = shade(c, 0.75) }
            let tw = fract((x + y) * 96) < 0.5
            return shade(c, tw ? 1.04 : 0.96)
        }
    }

    private static func grass(_ n: Int) -> Texture {
        make(n) { x, y in
            let t = fbm(x, y, 8, 4, 131)
            let blades = vnoise2(x, y, 192, 32, 132)
            let c = lerpColor(0xFF3E7D2E, 0xFF6FAE3F, t)
            return shade(c, 0.75 + 0.45 * blades)
        }
    }

    private static func water(_ n: Int) -> Texture {
        make(n) { x, y in
            let t = fbm(x + 0.1 * ktSinF(y * 6.283 * 2), y, 4, 4, 141)
            let caustic = 1 - abs(ktSinF((t * 8) * PI_F))
            let c = lerpColor(0xFF1C6EA4, 0xFF3FA7D6, t)
            return lerpColor(c, 0xFFD8F1FF, caustic * caustic * caustic * 0.55)
        }
    }

    private static func lava(_ n: Int) -> Texture {
        make(n) { x, y in
            let t = fbm(x, y, 4, 5, 151)
            let cracks = 1 - abs(ktSinF((t * 7) * PI_F))
            let rock = lerpColor(0xFF1F1A18, 0xFF3A2E2A, fbm(x, y, 16, 2, 152))
            return lerpColor(rock, lerpColor(0xFFFF7A00, 0xFFFFD34D, cracks), (cracks * cracks * 1.6 - 0.15).coerceIn(0, 1))
        }
    }

    private static func ice(_ n: Int) -> Texture {
        make(n) { x, y in
            let t = fbm(x, y, 3, 5, 161)
            let cr = 1 - abs(ktSinF((fbm(x, y, 6, 3, 162) * 9) * PI_F))
            let c = lerpColor(0xFFDDEFFA, 0xFFA9D4EE, t)
            return lerpColor(c, 0xFFFFFFFF, (cr - 0.85).coerceAtLeast(0) * 5)
        }
    }

    private static func bark(_ n: Int) -> Texture {
        make(n) { x, y in
            let warp = fbm(x, y, 4, 3, 171) - 0.5
            let ridges = abs(ktSinF(((x + warp * 0.15) * 10) * PI_F))
            let noise = vnoise2(x, y, 16, 4, 172) * 0.6 + vnoise2(x, y, 32, 8, 173) * 0.4
            let c = lerpColor(0xFF4A3424, 0xFF7A5B40, noise)
            return shade(c, 0.55 + 0.6 * ridges)
        }
    }

    private static func checker(_ n: Int) -> Texture {
        make(n) { x, y in (fl(x * 8) + fl(y * 8)) % 2 == 0 ? 0xFFF5F5F5 : 0xFF2B2B2B }
    }

    private static func stripes(_ n: Int) -> Texture {
        make(n) { x, y in
            switch floorModI(fl((x + y) * 9), 3) {
            case 0: return 0xFFF2C14E
            case 1: return 0xFFF78154
            default: return 0xFF4D9078
            }
        }
    }

    private static func dots(_ n: Int) -> Texture {
        make(n) { x, y in
            let k: Float = 8
            let gx = fract(x * k) - 0.5, gy = fract(y * k) - 0.5
            let gx2 = fract(x * k + 0.5) - 0.5, gy2 = fract(y * k + 0.5) - 0.5
            let inDot = gx * gx + gy * gy < 0.06 || gx2 * gx2 + gy2 * gy2 < 0.025
            return inDot ? 0xFFFFFFFF : 0xFFE5487A
        }
    }

    private static func carbon(_ n: Int) -> Texture {
        make(n) { x, y in
            let k: Float = 32
            let gx = fract(x * k), gy = fract(y * k)
            let cell = (fl(x * k) / 2 + fl(y * k) / 2) % 2 == 0
            let v = cell ? ktSinF(gx * PI_F) : ktSinF(gy * PI_F)
            return shade(0xFF2A2D31, 0.65 + 0.7 * v)
        }
    }

    private static func hexes(_ n: Int) -> Texture {
        make(n) { x, y in
            let cols: Float = 6, rows: Float = 8
            let fx = x * cols, fy = y * rows
            var d1: Float = 9, d2: Float = 9
            let jy = fl(fy)
            for j in (jy - 1)...(jy + 1) {
                let shift: Float = floorModI(j, 2) == 0 ? 0 : 0.5
                let ix = fl(fx - shift)
                for i in (ix - 1)...(ix + 1) {
                    let cx = Float(i) + shift + 0.5, cy = Float(j) + 0.5
                    let ddx = fx - cx, ddy = (fy - cy) * 0.75
                    let d = (ddx * ddx + ddy * ddy).squareRoot()
                    if d < d1 { d2 = d1; d1 = d } else if d < d2 { d2 = d }
                }
            }
            return d2 - d1 < 0.07 ? 0xFF7A5C12 : shade(0xFFF3B61F, 1.08 - d1 * 0.45)
        }
    }

    /// Doku piksellerinin ortalama rengi.
    static func average(_ t: Texture) -> UInt32 {
        var r = 0, g = 0, b = 0
        for c in t.px { r += Int((c >> 16) & 0xFF); g += Int((c >> 8) & 0xFF); b += Int(c & 0xFF) }
        let n = max(t.px.count, 1)
        return 0xFF00_0000 | UInt32((r / n) << 16 | (g / n) << 8 | (b / n))
    }
}
