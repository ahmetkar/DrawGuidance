import Foundation

/// Grafiklerin ortak ölçüleri (kısa kenar = 1 birimlik "fiziksel" koordinatlarda) (Android `Style`).
enum GStyle {
    static let PAD: Float = 0.035
    static let AXIS_EXT: Float = 0.05
    static let ARROW_LEN: Float = 0.032
    static let ARROW_HALF: Float = 0.012
    static let TICK: Float = 0.011
    static let TICK_SIZE: Float = 0.038
    static let NAME_SIZE: Float = 0.05
    static let FORMULA_SIZE: Float = 0.046
    static let KEY_SIZE: Float = 0.034
    static let GAP: Float = 0.009
    static let DOT: Float = 0.0125

    static let GRID = "#DDE3EA"
    static let GRID_MINOR = "#EEF1F5"
    static let ASYMPTOTE = "#90A4AE"
    static let GUIDE = "#90A4AE"
    static let WHITE = "#FFFFFF"
    static let BOX_BORDER = "#C9D3DE"
    static let TEXT_DARK = "#263238"

    static let W_AXIS: Float = 1
    static let W_TICK: Float = 0.9
    static let W_GRID: Float = 0.45
    static let W_MINOR: Float = 0.3
    static let W_CURVE: Float = 2

    static let PALETTE = [
        "#5B9BD5", "#F4A259", "#6CC08B", "#E86F68", "#9B8AD1", "#F2C94C",
        "#4DB6AC", "#F48FB1", "#A1887F", "#90A4AE", "#AED581", "#7986CB",
    ]
}

/// v18 kağıda uyan renkler.
struct GraphPalette {
    let paper: String, grid: String, gridMinor: String, guide: String, boxBorder: String

    private static let WHITE = GraphPalette(paper: GStyle.WHITE, grid: GStyle.GRID, gridMinor: GStyle.GRID_MINOR, guide: GStyle.GUIDE, boxBorder: GStyle.BOX_BORDER)

    static func of(_ paperHex: String) -> GraphPalette {
        let paper = GColors.valid(paperHex, GStyle.WHITE)
        if paper == GStyle.WHITE { return WHITE }
        let dark = GColors.luminance(paper) < 0.45
        let tone = dark ? "#CFD8DC" : "#5B7083"
        return GraphPalette(paper: paper, grid: GColors.mix(paper, tone, dark ? 0.24 : 0.2), gridMinor: GColors.mix(paper, tone, dark ? 0.12 : 0.1),
                            guide: GColors.mix(paper, dark ? "#ECEFF1" : "#37474F", 0.55), boxBorder: GColors.mix(paper, tone, dark ? 0.4 : 0.3))
    }
}

/// Fiziksel koordinatlarda dikdörtgen (yazı çarpışması için).
struct GBox: Equatable {
    let l: Float, t: Float, r: Float, b: Float
    init(_ l: Float, _ t: Float, _ r: Float, _ b: Float) { self.l = l; self.t = t; self.r = r; self.b = b }
    func overlaps(_ o: GBox, _ m: Float = 0) -> Bool { l < o.r + m && o.l < r + m && t < o.b + m && o.t < b + m }
    func contains(_ x: Float, _ y: Float) -> Bool { x >= l && x <= r && y >= t && y <= b }
    var w: Float { r - l }
    var h: Float { b - t }
}

/// Yazı genişliği tahmini (yazı boyuna oranla).
enum GMetrics {
    private static let SUPER: Set<Character> = Set("⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺ⁿˣʸ⁽⁾")
    static func char(_ c: Character) -> Float {
        if c.isDigitK { return 0.6 }
        if ".,:;'|!".contains(c) { return 0.28 }
        if c == " " { return 0.3 }
        if "−+=±<>".contains(c) { return 0.7 }
        if c == "-" { return 0.4 }
        if "()[]{}⌊⌋⌈⌉⟦⟧".contains(c) { return 0.4 }
        if c == "/" { return 0.36 }
        if c == "π" { return 0.62 }
        if c == "√" || c == "∛" { return 0.62 }
        if c == "°" { return 0.4 }
        if c == "%" { return 0.86 }
        if c == "·" { return 0.3 }
        if SUPER.contains(c) { return 0.38 }
        if "ilIjtfr".contains(c) { return 0.32 }
        if "mwMWŞ".contains(c) { return 0.85 }
        if c.isUppercase { return 0.68 }
        if c.isLetter { return 0.55 }
        return 0.6
    }

    static func width(_ s: String, _ size: Float) -> Float {
        var sum = 0.0
        for c in s { sum += Double(char(c)) }
        return Float(sum) * size
    }
}

/// Sayı biçimleri (Türkçe ondalık virgül, gerçek eksi işareti), π kesirleri ve "güzel" adımlar (Android `Fmt`).
enum Fmt {
    static let MINUS = "−"

    static func decimals(_ step: Double) -> Int {
        for d in 0...6 {
            let v = step * Foundation.pow(10.0, Double(d))
            if abs(v - v.rounded(.toNearestOrEven)) < 1e-6 * max(1.0, abs(v)) { return d }
        }
        return 6
    }

    /// Java `String.format("%.nf")`: en kısa ondalık yazımın yarım yukarı yuvarlanması.
    static func javaFixed(_ v: Double, _ decimals: Int) -> String {
        if v.isNaN { return "NaN" }
        if v.isInfinite { return v < 0 ? "-Infinity" : "Infinity" }
        let negative = v < 0 || (v == 0 && v.sign == .minus)
        var text = "\(abs(v))"
        var exp = 0
        if let e = text.firstIndex(where: { $0 == "e" || $0 == "E" }) {
            exp = Int(text[text.index(after: e)...]) ?? 0
            text = String(text[..<e])
        }
        let plain = Decimals.plain(text, exp)
        let parts = plain.split(separator: ".", omittingEmptySubsequences: false)
        var ip = Array(String(parts[0])), fp = Array(parts.count > 1 ? String(parts[1]) : "")
        let roundUp = fp.count > decimals && fp[decimals].wholeNumberValue! >= 5
        if fp.count > decimals { fp = Array(fp.prefix(decimals)) } else { fp += Array(repeating: "0", count: decimals - fp.count) }
        if roundUp {
            var digits = ip + fp
            var k = digits.count - 1
            while k >= 0 {
                if digits[k] == "9" { digits[k] = "0"; k -= 1 } else { digits[k] = Character(String(digits[k].wholeNumberValue! + 1)); break }
            }
            if k < 0 { digits.insert("1", at: 0) }
            ip = Array(digits.prefix(digits.count - decimals))
            fp = Array(digits.suffix(decimals))
        }
        let body = String(ip) + (decimals > 0 ? "." + String(fp) : "")
        return (negative ? "-" : "") + body
    }

    static func num(_ v: Double, _ decimals: Int) -> String {
        var s = javaFixed(v, decimals)
        if s.hasPrefix("-") {
            s.removeFirst()
            if s.contains(where: { ("1"..."9").contains($0) }) { s = MINUS + s }
        }
        return s.replacingOccurrences(of: ".", with: ",")
    }

    /// En fazla [maxDec] ondalık, sondaki sıfırlar atılır.
    static func auto(_ v: Double, _ maxDec: Int = 2) -> String {
        var s = num(v, maxDec)
        if !s.contains(",") { return s }
        while s.hasSuffix("0") { s.removeLast() }
        while s.hasSuffix(",") { s.removeLast() }
        return s
    }

    static func gcd(_ a: Int, _ b: Int) -> Int { b == 0 ? abs(a) : gcd(b, a % b) }

    /// v ≈ n·π/d ise (n, d) (sadeleşmiş), değilse nil.
    static func piFraction(_ v: Double, _ maxDen: Int = 12) -> (Int, Int)? {
        if !v.isFinite { return nil }
        let r = v / .pi
        for d in 1...maxDen {
            let n = r * Double(d)
            let rn = n.rounded(.toNearestOrEven)
            if abs(n - rn) < 1e-7 * Double(d) && abs(rn) < 1e6 {
                let g = max(1, gcd(Int(rn), d))
                return (Int(rn) / g, d / g)
            }
        }
        return nil
    }

    static func piLabel(_ n: Int, _ d: Int) -> String {
        if n == 0 { return "0" }
        let g = max(1, gcd(n, d))
        let nn = n / g, dd = d / g
        let a = abs(nn)
        let head = a == 1 ? "π" : "\(a)π"
        return (nn < 0 ? MINUS : "") + head + (dd != 1 ? "/\(dd)" : "")
    }

    /// Bir koordinat değeri: periyodikse π kesri, değilse ≤2 ondalık.
    static func coord(_ v: Double, _ preferPi: Bool) -> String {
        if preferPi && abs(v) > 1e-9, let (n, d) = piFraction(v), d <= 12 { return piLabel(n, d) }
        return auto(v, 2)
    }

    /// Nokta yazısı: tamsayılarda "(2, 3)", ondalıkta "(1,5; 2)".
    static func point(_ x: String, _ y: String) -> String { x.contains(",") || y.contains(",") ? "(\(x); \(y))" : "(\(x), \(y))" }

    private static func split(_ raw: Double) -> (Double, Double) {
        let e = Foundation.log10(raw).rounded(.down)
        let b = Foundation.pow(10.0, e)
        return (raw / b, b)
    }

    static func niceCeil(_ raw: Double) -> Double {
        if !(raw > 0) || !raw.isFinite { return 1 }
        let (m, b) = split(raw)
        let n: Double = m <= 1 + 1e-9 ? 1 : m <= 2 + 1e-9 ? 2 : m <= 5 + 1e-9 ? 5 : 10
        return n * b
    }

    static func niceRound(_ raw: Double) -> Double {
        if !(raw > 0) || !raw.isFinite { return 1 }
        let (m, b) = split(raw)
        let n: Double = m < 1.4 ? 1 : m < 2.9 ? 2 : m < 6.5 ? 5 : 10
        return n * b
    }

    static func smaller(_ step: Double) -> Double {
        let (m, b) = split(step)
        return m < 1.5 ? 0.5 * b : m < 3.5 ? 1.0 * b : 2.0 * b
    }

    static func larger(_ step: Double) -> Double {
        let (m, b) = split(step)
        return m < 1.5 ? 2.0 * b : m < 3.5 ? 5.0 * b : 10.0 * b
    }
}

/// Renk yardımcıları ("#RRGGBB") (Android `Colors`).
enum GColors {
    static func rgb(_ hex: String) -> [Int] {
        var h = hex
        if h.hasPrefix("#") { h.removeFirst() }
        guard let v = UInt64(String(h.prefix(6)), radix: 16) else { return [30, 136, 229] }
        let i = Int(Int32(truncatingIfNeeded: v))
        return [(i >> 16) & 255, (i >> 8) & 255, i & 255]
    }

    static func hex(_ r: Int, _ g: Int, _ b: Int) -> String {
        String(format: "#%02X%02X%02X", min(max(r, 0), 255), min(max(g, 0), 255), min(max(b, 0), 255))
    }

    /// Kotlin `Float.roundToInt()`.
    static func ri(_ v: Float) -> Int { Int((Double(v) + 0.5).rounded(.down)) }

    static func mix(_ a: String, _ b: String, _ t: Float) -> String {
        let x = rgb(a), y = rgb(b)
        return hex(ri(Float(x[0]) + Float(y[0] - x[0]) * t), ri(Float(x[1]) + Float(y[1] - x[1]) * t), ri(Float(x[2]) + Float(y[2] - x[2]) * t))
    }

    static func lighten(_ c: String, _ t: Float) -> String { mix(c, "#FFFFFF", t) }
    static func darken(_ c: String, _ t: Float) -> String { mix(c, "#000000", t) }
    static func scale(_ c: String, _ k: Float) -> String {
        let x = rgb(c)
        return hex(ri(Float(x[0]) * k), ri(Float(x[1]) * k), ri(Float(x[2]) * k))
    }

    static func luminance(_ c: String) -> Float {
        let x = rgb(c)
        return (0.299 * Float(x[0]) + 0.587 * Float(x[1]) + 0.114 * Float(x[2])) / 255
    }

    /// Geçerli "#RRGGBB" değilse varsayılan.
    static func valid(_ c: String, _ fallback: String = "#1E88E5") -> String {
        let a = Array(c)
        guard a.count == 7, a[0] == "#", a.dropFirst().allSatisfy(\.isHexDigit) else { return fallback }
        return c.uppercased()
    }
}

/// Çizim yüzeyi: "fiziksel" koordinatlarda (kısa kenar = 1, y aşağı) çizer, birim kutuya dönüştürüp katmanlara
/// ekler (Android graph `Canvas`).
final class GCanvas {
    let p: GraphParams
    let W: Float
    let H: Float
    let res: Float
    let px: Float
    private var layers = [[VPath]](repeating: [], count: 8)
    var layer = 0
    var boxes: [GBox] = []
    private(set) var defaults: [String: String] = [:]
    private(set) var defaultOrder: [String] = []
    let color: String
    let pal: GraphPalette

    init(_ aspect: Float, _ resolution: Float, _ p: GraphParams) {
        self.p = p
        let a: Float = aspect.isFinite && aspect > 0 ? min(max(aspect, 0.1), 10) : 1
        if a >= 1 { W = a; H = 1 } else { W = 1; H = 1 / a }
        res = min(max(resolution.isFinite ? resolution : 512, 48), 4096)
        px = 1 / res
        color = GColors.valid(p.curveColor)
        pal = GraphPalette.of(p.paper)
    }

    func paths() -> [VPath] { layers.flatMap { $0 } }

    private func u(_ x: Float, _ y: Float) -> P { P(x / W, y / H) }

    func add(_ pts: [P], closed: Bool = false, fill: String? = nil, stroke: String? = nil, width: Float = 1,
             line: LineStyle = .SOLID, alpha: Float = 1, tag: String? = nil) {
        if pts.count < 2 { return }
        if pts.contains(where: { !$0.x.isFinite || !$0.y.isFinite }) { return }
        layers[layer].append(VPath(points: pts.map { u($0.x, $0.y) }, closed: closed, fill: fill, stroke: stroke, width: width, line: line, alpha: alpha, tag: tag))
    }

    func line(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float, stroke: String? = nil, width: Float = 1,
              line: LineStyle = .SOLID, alpha: Float = 1, tag: String? = nil) {
        add([P(x1, y1), P(x2, y2)], stroke: stroke, width: width, line: line, alpha: alpha, tag: tag)
    }

    func circlePoints(_ cx: Float, _ cy: Float, _ r: Float, _ rMin: Int = 12) -> [P] {
        let n = min(max(Int(2 * Double.pi * Double(r) * Double(res) / 4), rMin), 240)
        return (0..<n).map { i in
            let a = 2 * Double.pi * Double(i) / Double(n)
            return P(cx + r * Float(cos(a)), cy + r * Float(sin(a)))
        }
    }

    /// Dolu nokta (kağıt renginde kenarlı).
    func dot(_ cx: Float, _ cy: Float, r: Float = GStyle.DOT, fill: String? = nil, stroke: String? = nil, width: Float = 0.7, tag: String? = nil) {
        add(circlePoints(cx, cy, r), closed: true, fill: fill ?? color, stroke: stroke ?? pal.paper, width: width, tag: tag)
    }

    /// Boş nokta (kağıt içli, renkli kenarlı).
    func openDot(_ cx: Float, _ cy: Float, r: Float = GStyle.DOT, stroke: String? = nil, tag: String? = nil) {
        add(circlePoints(cx, cy, r), closed: true, fill: pal.paper, stroke: stroke ?? color, width: 1.1, tag: tag)
    }

    /// Dolgusuz çember.
    func ring(_ cx: Float, _ cy: Float, _ r: Float, stroke: String? = nil, width: Float = 1, line: LineStyle = .SOLID, alpha: Float = 1, tag: String? = nil) {
        let pts = circlePoints(cx, cy, r, 24)
        add(pts + [pts[0]], stroke: stroke, width: width, line: line, alpha: alpha, tag: tag)
    }

    func roundRectPoints(_ x: Float, _ y: Float, _ w: Float, _ h: Float, _ r: Float) -> [P] {
        let rr = min(r, min(w, h) / 2)
        var pts: [P] = []
        func corner(_ cx: Float, _ cy: Float, _ from: Double) {
            for i in 0...5 {
                let a = from + (Double.pi / 2) * Double(i) / 5
                pts.append(P(cx + rr * Float(cos(a)), cy + rr * Float(sin(a))))
            }
        }
        corner(x + w - rr, y + rr, -Double.pi / 2)
        corner(x + w - rr, y + h - rr, 0)
        corner(x + rr, y + h - rr, Double.pi / 2)
        corner(x + rr, y + rr, Double.pi)
        return pts
    }

    /// Dolu ok ucu: ucu (tx, ty), yönü (dx, dy).
    func arrowHead(_ tx: Float, _ ty: Float, _ dx: Float, _ dy: Float, len: Float = GStyle.ARROW_LEN, half: Float = GStyle.ARROW_HALF, color: String? = nil) {
        let l = max(hypotf(dx, dy), 1e-6)
        let ux = dx / l, uy = dy / l
        let nx = -uy, ny = ux
        let bx = tx - ux * len, by = ty - uy * len
        add([P(bx + nx * half, by + ny * half), P(tx, ty), P(bx - nx * half, by - ny * half), P(bx + ux * len * 0.18, by + uy * len * 0.18)],
            closed: true, fill: color ?? INK_COLOR, stroke: color, width: 0.4)
    }

    func measure(_ s: String, _ size: Float) -> Float { GMetrics.width(s, size) }

    func textBox(_ x: Float, _ y: Float, _ s: String, _ size: Float) -> GBox { GBox(x, y, x + measure(s, size), y + size) }

    /// Yazı (sol-üst çapa) ekler ve alanını ayırır.
    @discardableResult
    func text(_ x: Float, _ y: Float, _ s: String, _ size: Float, color: String? = nil, tag: String? = nil, reserve: Bool = true) -> GBox {
        layers[layer].append(VPath(points: [u(x, y)], stroke: color, label: s, labelSize: size, tag: tag))
        let b = textBox(x, y, s, size)
        if reserve { boxes.append(b) }
        return b
    }

    /// Kutu içinde mi ve ayrılmış alanlarla çakışmıyor mu?
    func fits(_ b: GBox, margin: Float = 0.004, slack: Float = 0.012) -> Bool {
        b.l >= -slack && b.t >= -slack && b.r <= W + slack && b.b <= H + slack && !boxes.contains { $0.overlaps(b, margin) }
    }

    /// Çentik yazısı: kullanıcı değiştirdiyse onun metni (zorunlu), değilse varsayılan. "" → nil (gizli).
    /// Varsayılan metni yazar (Kotlin `defaults[key] = …`, ekleme sırası korunur).
    func setDefault(_ key: String, _ def: String) {
        if defaults[key] == nil { defaultOrder.append(key) }
        defaults[key] = def
    }

    func tickText(_ key: String, _ def: String) -> (String?, Bool) {
        if defaults[key] == nil { defaultOrder.append(key) }
        defaults[key] = def
        let o = p.tickLabels[key]
        let t = o ?? def
        return (t.isEmpty ? nil : t, o != nil)
    }
}

/// Kotlin `Double.toInt()`: sıfıra doğru keser, NaN → 0, taşanlar sınıra doyar (Swift `Int(_:)` çökerdi).
@inline(__always) func ktInt(_ v: Double) -> Int {
    if v.isNaN { return 0 }
    if v >= 2147483647 { return 2147483647 }
    if v <= -2147483648 { return -2147483648 }
    return Int(v)
}

/// Kotlin `Float.toInt()`.
@inline(__always) func ktInt(_ v: Float) -> Int { ktInt(Double(v)) }

/// Kotlin `Double.roundToInt()` (Math.round: floor(x + 0.5)).
@inline(__always) func ktRound(_ v: Double) -> Int { ktInt((v + 0.5).rounded(.down)) }

/// Kotlin `Float.roundToInt()`.
@inline(__always) func ktRound(_ v: Float) -> Int { ktRound(Double(v)) }

@inline(__always) func clampF(_ v: Float, _ lo: Float, _ hi: Float) -> Float { min(max(v, lo), hi) }
@inline(__always) func clampD(_ v: Double, _ lo: Double, _ hi: Double) -> Double { min(max(v, lo), hi) }
@inline(__always) func clampI(_ v: Int, _ lo: Int, _ hi: Int) -> Int { min(max(v, lo), hi) }

/// Java `HashMap<Int, …>` anahtar dolaşma sırası (kova sırası, kova içinde ekleme sırası); Swift sözlükleri
/// süreç başına rastgele sıralandığından Android ile aynı çıktı için kullanılır.
func javaHashOrder(_ keysInInsertionOrder: [Int]) -> [Int] {
    var cap = 16
    while Double(keysInInsertionOrder.count) > Double(cap) * 0.75 { cap *= 2 }
    func bucket(_ k: Int) -> Int {
        let h = Int32(truncatingIfNeeded: k)
        let spread = h ^ Int32(bitPattern: UInt32(bitPattern: h) >> 16)
        return Int(spread) & (cap - 1)
    }
    return keysInInsertionOrder.enumerated().sorted { a, b in
        let ba = bucket(a.element), bb = bucket(b.element)
        return ba != bb ? ba < bb : a.offset < b.offset
    }.map(\.element)
}
