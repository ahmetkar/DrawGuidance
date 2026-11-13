import Foundation

/// v21 şekil veritabanının kaydı (Android `ShapeTemplate`): birim kutuda (0..1, y aşağı) elle çizilmiş gibi çizgiler.
struct ShapeTemplate: Equatable {
    let key: String
    let name: String
    let category: String
    let group: String
    let strokes: [[P]]
    var description: String = ""
    var solid: String? = nil
    var strokeCount: Int { strokes.count }
}

/// Şablon kataloğu: Android'deki Kotlin tanımlarından üretilen `shape_templates.bin` (ios/tools/ExportShapeTemplates)
/// ve kütüphaneden üretilen `library_templates.bin`.
enum ShapeTemplates {
    static let VERSION = 8
    private static let MAGIC: Int32 = 0x5348_5450

    private static let loaded: ([ShapeTemplate], [ShapeTemplate]) = {
        guard let data = BundledResources.data("shape_templates.bin") else { return ([], []) }
        var d = JavaDataReader(data)
        func list() throws -> [ShapeTemplate] {
            let n = Int(try d.int())
            var out: [ShapeTemplate] = []
            out.reserveCapacity(n)
            for _ in 0..<n {
                let key = try d.utf(), name = try d.utf(), category = try d.utf(), group = try d.utf(), desc = try d.utf()
                let solid: String? = try d.u8() != 0 ? try d.utf() : nil
                var strokes: [[P]] = []
                for _ in 0..<Int(try d.int()) {
                    var s: [P] = []
                    for _ in 0..<Int(try d.int()) { let x = try d.float(); let y = try d.float(); s.append(P(x, y)) }
                    strokes.append(s)
                }
                out.append(ShapeTemplate(key: key, name: name, category: category, group: group, strokes: strokes, description: desc, solid: solid))
            }
            return out
        }
        do {
            guard try d.int() == MAGIC else { return ([], []) }
            _ = try d.int()
            let all = try list()
            let modeOnly = try list()
            return (all, modeOnly)
        } catch { return ([], []) }
    }()

    /// Genel şablonlar (kategori sırasıyla).
    static var all: [ShapeTemplate] { loaded.0 }
    /// Moda özel şablonlar (not alma, çizgi roman, çocuk) ve kütüphane şablonları.
    static let modeOnly: [ShapeTemplate] = loaded.1 + LibraryTemplates.all
    static let catalog: [ShapeTemplate] = all + modeOnly
    private static let byKeyMap: [String: ShapeTemplate] = {
        var m: [String: ShapeTemplate] = [:]
        for t in catalog where m[t.key] == nil { m[t.key] = t }
        return m
    }()
    static func byKey(_ key: String) -> ShapeTemplate? { byKeyMap[key] }
    static var categories: [String] { var seen = Set<String>(); return all.map(\.category).filter { seen.insert($0).inserted } }
}

/// v37 kütüphaneden üretilen şablonlar (Android `LibraryTemplates`, aynı kaynak dosyası).
enum LibraryTemplates {
    static let OBJECT_PREFIX = "obje."
    static let CHARACTER_PREFIX = "roman.karakter."
    static let EFFECT_PREFIX = "roman.efekt."
    private static let MAGIC: Int32 = 0x4C42_5450

    struct Entry { let template: ShapeTemplate; let libraryId: String }

    static let entries: [Entry] = {
        guard let data = BundledResources.data("library_templates.bin") else { return [] }
        var d = JavaDataReader(data)
        do {
            guard try d.int() == MAGIC, try d.int() == 1 else { return [] }
            let n = Int(try d.int())
            var out: [Entry] = []
            for _ in 0..<n {
                let key = try d.utf(), name = try d.utf(), category = try d.utf(), group = try d.utf(), desc = try d.utf(), lib = try d.utf()
                var strokes: [[P]] = []
                for _ in 0..<Int(try d.short()) {
                    var s: [P] = []
                    for _ in 0..<Int(try d.short()) {
                        let x = Float(Int16(bitPattern: try d.short())) / 10000, y = Float(Int16(bitPattern: try d.short())) / 10000
                        s.append(P(x, y))
                    }
                    strokes.append(s)
                }
                out.append(Entry(template: ShapeTemplate(key: key, name: name, category: category, group: group, strokes: strokes, description: desc), libraryId: lib))
            }
            return out
        } catch { return [] }
    }()

    static var all: [ShapeTemplate] { entries.map(\.template) }
    private static let byKey: [String: String] = {
        var m: [String: String] = [:]
        for e in entries { m[e.template.key] = e.libraryId }
        return m
    }()
    static func libraryId(_ templateKey: String) -> String? { byKey[templateKey] }
    static func isLibraryKey(_ key: String) -> Bool { key.hasPrefix(OBJECT_PREFIX) || key.hasPrefix(CHARACTER_PREFIX) || key.hasPrefix(EFFECT_PREFIX) }
}

/// v23 en çok çizilen şekillerin önseli (1..5).
enum TemplatePopularity {
    private static let table: [String: Int] = ["nesne.kalp": 5, "doga.yildiz": 5, "doga.yildiz_cizgi": 5, "doga.gunes": 5, "doga.bulut": 5, "yapi.ev.besik": 5, "bitki.agac": 5, "bitki.agac_yuvarlak": 5, "bitki.papatya": 5, "bitki.cicek_basit": 5, "yuz.ifade.gulen": 5, "vucut.cop_adam": 5, "simge.tik": 5, "simge.ok_sag": 5, "hayvan.balik": 4, "hayvan.kedi_yuz": 4, "hayvan.kedi": 4, "hayvan.kopek": 4, "hayvan.kus": 4, "hayvan.kus_ucan": 4, "hayvan.kelebek": 4, "tasit.araba": 4, "tasit.ucak": 4, "tasit.tekne": 4, "tasit.yelkenli": 4, "tasit.roket": 4, "doga.hilal": 4, "doga.gokkusagi": 4, "doga.dag": 4, "doga.simsek": 4, "nesne.balon": 4, "nesne.ampul": 4, "meyve.elma": 4, "yiyecek.pasta": 4, "yiyecek.dondurma": 4, "doga.kardan_adam": 4, "simge.carpi": 4, "simge.nota": 4, "nesne.top": 4, "yuz.goz.badem": 4, "yapi.ev.bacali": 4, "bitki.lale": 4, "bitki.cam": 4, "nesne.semsiye": 3, "nesne.hediye": 3, "yiyecek.pizza": 3, "meyve.muz": 3, "meyve.cilek": 3, "hayvan.orumcek": 3, "hayvan.ari": 3, "hayvan.salyangoz": 3, "yapi.kale": 3, "tasit.tren": 3, "tasit.bisiklet": 3, "tasit.otobus": 3, "simge.sonsuz": 3, "simge.konum": 3, "simge.buyutec": 3, "nesne.kupa": 3, "nesne.kitap": 3, "nesne.kalem": 3, "nesne.saat": 3, "nesne.anahtar": 3, "esya.makas": 3, "esya.gitar": 3, "esya.futbol_topu": 3, "vucut.el": 3, "hayvan.baykus": 3, "hayvan.penguen": 3, "hayvan.yilan": 3, "hayvan.kaplumbaga": 3, "hayvan.ordek": 3, "yiyecek.hamburger": 3, "yiyecek.kek": 3, "esya.telefon": 3, "esya.mum": 3, "hayal.hayalet": 3, "hayal.robot": 3, "simge.ok_yukari": 3, "simge.arti": 3, "doga.yagmur": 3, "doga.kar_tanesi": 3, "doga.ates": 3, "doga.dalga": 3, "nesne.tac": 3, "nesne.elmas": 3, "nesne.zarf": 3, "nesne.konusma": 3, "simge.dusunce": 3, "simge.parilti": 3, "yuz.tam.kulakli": 4, "yapi.ev.tam": 4, "insan.basit_govde": 4, "bitki.cicek_saksi": 4, "doga.dag_gunes": 4, "yuz.tam.sacli": 3, "bitki.agac_elmali": 3, "hayvan.tirtil": 3, "tasit.araba_pencereli": 3, "nesne.robot_kafa": 3]
    static func of(_ key: String) -> Int { table[key] ?? 2 }
    static var frequent: Set<String> { Set(table.filter { $0.value >= 4 }.keys) }
}

/// v21 aramaya hazır imza (Android `ShapeSignature`).
struct ShapeSignature {
    let strokeCount: Int
    let closedMask: Int
    let aspect: Float
    let corners: Int
    let descriptors: [Float]
    let lsh: Int
    let geometry: [[P]]
    var variants: Int { descriptors.count / ShapeSignature.DIM }
    var aspectBucket: Int { (aspect * 8).toInt().coerceIn(0, 7) }
    static let DIM = ShapeFeatures.DIM
}

enum ShapeSignatures {
    private struct Distort { let slantDeg: Float, rotDeg: Float, sx: Float }
    private static let distortions: [Distort] = [
        Distort(slantDeg: 0, rotDeg: 0, sx: 1),
        Distort(slantDeg: 10, rotDeg: 0, sx: 1), Distort(slantDeg: -10, rotDeg: 0, sx: 1),
        Distort(slantDeg: 0, rotDeg: 8, sx: 1), Distort(slantDeg: 0, rotDeg: -8, sx: 1),
        Distort(slantDeg: 0, rotDeg: 0, sx: 0.8), Distort(slantDeg: 0, rotDeg: 0, sx: 1.25),
    ]
    static var VARIANTS: Int { distortions.count }
    static let DOT_PART: Float = 0.12

    static func normalize(_ strokes: [[P]]) -> [[P]] {
        var a = Float.infinity, b = Float.infinity, c = -Float.infinity, d = -Float.infinity
        for s in strokes { for p in s { a = min(a, p.x); b = min(b, p.y); c = max(c, p.x); d = max(d, p.y) } }
        if !a.isFinite { return strokes }
        let size = max(max(c - a, d - b), 1e-4)
        let cx = (a + c) / 2, cy = (b + d) / 2
        return strokes.map { s in s.map { P(($0.x - cx) / size + 0.5, ($0.y - cy) / size + 0.5) } }
    }

    static func aspectOf(_ strokes: [[P]]) -> Float { ShapeFeatures.aspect(polys(strokes)) }

    static func polys(_ strokes: [[P]]) -> [Poly] { strokes.filter { !$0.isEmpty }.map { s in Poly(s.map(\.x), s.map(\.y)) } }

    static func collapseSmall(_ polys: [Poly]) -> [Poly] {
        if polys.count < 2 { return polys }
        let b = Box.of(polys)
        let size = max(max(b.w, b.h), 1e-4)
        return polys.map { p in p.extent < DOT_PART * size && p.n > 1 ? Poly([p.cx], [p.cy]) : p }
    }

    static func of(_ strokes: [[P]], augment: Bool = true) -> ShapeSignature {
        let norm = normalize(strokes.filter { !$0.isEmpty })
        let raw = polys(norm)
        let ps = collapseSmall(raw)
        let list = augment ? distortions : Array(distortions.prefix(1))
        var desc = [Float](repeating: 0, count: list.count * ShapeSignature.DIM)
        for (i, d) in list.enumerated() {
            let f = ShapeFeatures.extract(distort(ps, d))
            for j in 0..<ShapeSignature.DIM { desc[i * ShapeSignature.DIM + j] = f[j] }
        }
        return ShapeSignature(strokeCount: ps.count, closedMask: closedMask(raw), aspect: ShapeFeatures.aspect(raw), corners: corners(raw),
                              descriptors: desc, lsh: simHash(desc, 0), geometry: norm)
    }

    private static func distort(_ polys: [Poly], _ d: Distort) -> [Poly] {
        if d.slantDeg == 0 && d.rotDeg == 0 && d.sx == 1 { return polys }
        let b = Box.of(polys)
        let sh = Float(tan(toRadians(Double(d.slantDeg))))
        let r = toRadians(Double(d.rotDeg))
        let c = Float(cos(r)), s = Float(sin(r))
        return polys.map { p in
            p.affine(d.sx, -sh, -d.sx * b.cx + sh * b.cy + b.cx, 0, 1, 0)
                .affine(c, -s, b.cx - c * b.cx + s * b.cy, s, c, b.cy - s * b.cx - c * b.cy)
        }
    }

    static func isClosed(_ p: Poly) -> Bool {
        if p.n < 4 { return false }
        let ext = max(p.extent, 1e-4)
        return kHypot(p.x[p.n - 1] - p.x[0], p.y[p.n - 1] - p.y[0]) <= 0.15 * ext && p.length > 2 * ext
    }

    static func closedMask(_ polys: [Poly]) -> Int {
        var m = 0
        for (i, p) in polys.prefix(30).enumerated() where isClosed(p) { m |= 1 << i }
        return m
    }

    static func corners(_ polys: [Poly]) -> Int {
        var total = 0
        for p in polys { total += cornersOf(p) }
        return min(total, 63)
    }

    static func cornersOf(_ p: Poly) -> Int {
        if p.length < 0.08 { return 0 }
        let r = Geo.resample(p, 0.02)
        if r.n < 5 { return 0 }
        let k = 2
        var ang = [Float](repeating: 0, count: r.n)
        for i in k..<(r.n - k) {
            let ax = r.x[i] - r.x[i - k], ay = r.y[i] - r.y[i - k]
            let bx = r.x[i + k] - r.x[i], by = r.y[i + k] - r.y[i]
            let la = kHypot(ax, ay), lb = kHypot(bx, by)
            if la < 1e-6 || lb < 1e-6 { continue }
            ang[i] = kAcos(((ax * bx + ay * by) / (la * lb)).coerceIn(-1, 1))
        }
        let th = Float(toRadians(55.0))
        var n = 0
        var i = k
        while i < r.n - k {
            if ang[i] > th {
                var j = i
                while j + 1 < r.n - k && ang[j + 1] > th { j += 1 }
                n += 1
                i = j + 2
            } else { i += 1 }
        }
        return n
    }

    // MARK: sıkıştırılmış saklama (kullanıcı örnekleri)

    static func encodeDescriptors(_ desc: [Float]) -> [UInt8] {
        desc.map { v in UInt8(truncatingIfNeeded: Int((Double(sqrt(v.coerceIn(0, 1)) * 255) + 0.5).rounded(.down))) }
    }
    static func decodeDescriptors(_ bytes: [UInt8]) -> [Float] { bytes.map { let q = Float($0) / 255; return q * q } }

    // MARK: yerellik duyarlı özet

    private static let LSH_BITS = 16
    private static let poolDim = (ShapeFeatures.G / 2) * (ShapeFeatures.G / 2) * ShapeFeatures.CH
    static var POOLED: Int { poolDim }

    private static let planes: [[Float]] = {
        var rnd = JavaRandom(2121)
        return (0..<LSH_BITS).map { _ in (0..<poolDim).map { _ in Float(rnd.nextGaussian()) } }
    }()

    static func simHash(_ desc: [Float], _ offset: Int) -> Int { simHashPooled(pool(desc, offset)) }

    static func simHashPooled(_ pooled: [Float]) -> Int {
        let mean = 1 / Float(Double(poolDim).squareRoot())
        var key = 0
        for b in 0..<LSH_BITS {
            var s: Float = 0
            let pl = planes[b]
            for k in pooled.indices { s += pl[k] * (pooled[k] - mean) }
            if s >= 0 { key |= 1 << b }
        }
        return key
    }

    static func pool(_ f: [Float], _ offset: Int = 0) -> [Float] {
        let g = ShapeFeatures.G, h = g / 2
        var out = [Float](repeating: 0, count: h * h * ShapeFeatures.CH)
        for ch in 0..<ShapeFeatures.CH { for y in 0..<g { for x in 0..<g {
            out[ch * h * h + (y / 2) * h + x / 2] += f[offset + ch * g * g + y * g + x]
        } } }
        var n: Float = 0
        for v in out { n += v * v }
        n = n.squareRoot()
        if n > 0 { for i in out.indices { out[i] /= n } }
        return out
    }

    static func probes(_ key: Int, _ radius: Int) -> [Int] {
        var out = [key]
        if radius >= 1 { for i in 0..<LSH_BITS { out.append(key ^ (1 << i)) } }
        if radius >= 2 { for i in 0..<LSH_BITS { for j in (i + 1)..<LSH_BITS { out.append(key ^ (1 << i) ^ (1 << j)) } } }
        return out
    }
}

/// v30 karmaşıklığa göre algoritma seçimi.
enum ShapeComplexity {
    static let NET_MIN_ELEMENTS = SuggestRules.SHAPE_MIN
    enum Tier { case PRIMITIVE, SINGLE, FEW, MANY }
    static func tier(_ elements: Int, primitive: Bool = false) -> Tier {
        if elements <= 1 && primitive { return .PRIMITIVE }
        if elements <= 1 { return .SINGLE }
        if elements < NET_MIN_ELEMENTS { return .FEW }
        return .MANY
    }
    static func usesNet(_ elements: Int) -> Bool { elements >= NET_MIN_ELEMENTS }
    static func algorithms(_ tier: Tier) -> [String] {
        switch tier {
        case .PRIMITIVE: return ["Geometrik uydurma"]
        case .SINGLE: return ["LSH", "Kosinüs", "kNN", "Yön dizisi", "Chamfer", "$Q", "IDM"]
        case .FEW: return ["LSH", "Kosinüs", "kNN", "Chamfer", "$Q", "IDM", "Parça grafiği"]
        case .MANY: return ["LSH", "Kosinüs", "kNN", "Chamfer", "$Q", "IDM", "Parça grafiği", "Sinir ağı"]
        }
    }
}

/// v38 öneri kuralları (Android `SuggestRules`).
enum SuggestRules {
    static let LINE_MIN = 2
    static let TEXT_MIN = 2
    static let LINE_SINGLE_LENGTH: Float = 0.75
    static let LINE_SINGLE_MIN_LENGTH: Float = 400
    static let LINE_SINGLE_SPAN: Float = 0.25
    static let LINE_SINGLE_MIN_SPAN: Float = 120
    static let LINE_SINGLE_POINTS = 40
    static let TEXT_SINGLE_LENGTH: Float = 0.3
    static let TEXT_SINGLE_MIN_LENGTH: Float = 200
    static let TEXT_SINGLE_HEIGHT: Float = 0.06
    static let TEXT_SINGLE_MIN_HEIGHT: Float = 40
    static let TEXT_SINGLE_POINTS = 30
    static let TEXT_SINGLE_CONFIDENCE: Float = 0.9
    static let DEFAULT_CANVAS_SIDE: Float = 1000
    static let SHAPE_MIN = 4
    static let SHAPE_MAX = SelectionSuggest.MAX_ELEMENTS
    static let LINE_MAX = 60
    static let SHAPE_PAD: Float = 0.2
    static let SHAPE_MIN_PAD: Float = 12
    static let LINE_PAD: Float = 0.6
    static let LINE_MIN_PAD: Float = 24

    static func side(_ w: Float, _ h: Float) -> Float { w > 0 && h > 0 ? min(w, h) : DEFAULT_CANVAS_SIDE }

    static func bigLine(length: Float, points: Int, w: Float, h: Float, canvasW: Float, canvasH: Float) -> Bool {
        let side = side(canvasW, canvasH)
        return points >= LINE_SINGLE_POINTS && length >= max(LINE_SINGLE_MIN_LENGTH, side * LINE_SINGLE_LENGTH) &&
            max(w, h) >= max(LINE_SINGLE_MIN_SPAN, side * LINE_SINGLE_SPAN)
    }

    static func bigText(length: Float, points: Int, w: Float, h: Float, canvasW: Float, canvasH: Float) -> Bool {
        let side = side(canvasW, canvasH)
        return points >= TEXT_SINGLE_POINTS && length >= max(TEXT_SINGLE_MIN_LENGTH, side * TEXT_SINGLE_LENGTH) &&
            h >= max(TEXT_SINGLE_MIN_HEIGHT, side * TEXT_SINGLE_HEIGHT)
    }

    static func pathLength(_ xs: [Float], _ ys: [Float]) -> Float {
        var len: Float = 0
        let n = min(xs.count, ys.count)
        if n > 1 { for i in 1..<n { len += kHypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]) } }
        return len
    }

    static func near(_ boxes: [[Float]], _ seed: Int, _ pad: Float, _ minPad: Float, _ maxCount: Int = Int.max) -> [Int] {
        TemplateGrouping.cluster(boxes, seed, pad: pad, minPad: minPad, maxCount: maxCount)
    }

    static func clusters(_ boxes: [[Float]], _ pad: Float, _ minPad: Float) -> [[Int]] {
        var seen = [Bool](repeating: false, count: boxes.count)
        var out: [[Int]] = []
        for i in boxes.indices.reversed() {
            if seen[i] { continue }
            let c = near(boxes, i, pad, minPad, max(1, boxes.count))
            for j in c { seen[j] = true }
            out.append(c)
        }
        return out.enumerated().sorted { a, b in
            if a.element.count != b.element.count { return a.element.count > b.element.count }
            let ma = a.element.max()!, mb = b.element.max()!
            if ma != mb { return ma > mb }
            return a.offset < b.offset
        }.map(\.element)
    }
}

/// Şablonun çizime oturtulması.
enum TemplateFit {
    static func fit(_ geometry: [[P]], _ left: Float, _ top: Float, _ right: Float, _ bottom: Float, stretch: Float = 1.4) -> [[P]] {
        var a = Float.infinity, b = Float.infinity, c = -Float.infinity, d = -Float.infinity
        for s in geometry { for p in s { a = min(a, p.x); b = min(b, p.y); c = max(c, p.x); d = max(d, p.y) } }
        if !a.isFinite { return [] }
        let tw = max(c - a, 1e-3), th = max(d - b, 1e-3)
        let qw = max(right - left, 1), qh = max(bottom - top, 1)
        var sx = qw / tw, sy = qh / th
        let g = (sx * sy).squareRoot()
        sx = g * (sx / g).coerceIn(1 / stretch, stretch)
        sy = g * (sy / g).coerceIn(1 / stretch, stretch)
        if tw < 0.08 || th < 0.08 { let s = max(qw, qh) / max(tw, th); sx = s; sy = s }
        let cx = (left + right) / 2, cy = (top + bottom) / 2
        let tcx = (a + c) / 2, tcy = (b + d) / 2
        return geometry.map { s in s.map { P(cx + ($0.x - tcx) * sx, cy + ($0.y - tcy) * sy) } }
    }

    static func densify(_ stroke: [P], _ step: Float) -> [P] {
        if stroke.count < 2 || step <= 0 { return stroke }
        var out: [P] = [stroke[0]]
        out.reserveCapacity(stroke.count * 2)
        for i in 1..<stroke.count {
            let p = stroke[i - 1], q = stroke[i]
            let n = Int((kHypot(q.x - p.x, q.y - p.y) / step).rounded(.up))
            if n > 1 { for k in 1..<n { let t = Float(k) / Float(n); out.append(P(p.x + (q.x - p.x) * t, p.y + (q.y - p.y) * t)) } }
            out.append(q)
        }
        return out
    }
}

/// Birden çok elemanın oluşturduğu şeklin elemanları.
enum TemplateGrouping {
    static func cluster(_ boxes: [[Float]], _ seed: Int, pad: Float = 0.2, minPad: Float = 12, maxCount: Int = 24) -> [Int] {
        if !boxes.indices.contains(seed) { return [] }
        var inGroup = [Bool](repeating: false, count: boxes.count)
        inGroup[seed] = true
        var order = [seed]
        func ext(_ b: [Float]) -> Float { max(b[2] - b[0], b[3] - b[1]) }
        var changed = true
        while changed && order.count < maxCount {
            changed = false
            for i in boxes.indices.reversed() {
                if inGroup[i] { continue }
                let b = boxes[i]
                let touch = order.contains { j in
                    let g = boxes[j]
                    let gap = max(max(g[0] - b[2], b[0] - g[2]), max(g[1] - b[3], b[1] - g[3]))
                    return gap <= max(minPad, pad * max(ext(g), ext(b)))
                }
                if touch { inGroup[i] = true; order.append(i); changed = true; if order.count >= maxCount { break } }
            }
        }
        return order.sorted()
    }
}

enum TemplateSuggest {
    static let MIN_SIMILARITY: Float = 0.5
    static let MIN_SINGLE = MIN_SIMILARITY
    static let MIN_GROUP = MIN_SIMILARITY
    static let STRONG_SINGLE: Float = 0.86
    static let STRONG_GROUP: Float = 0.84
}
