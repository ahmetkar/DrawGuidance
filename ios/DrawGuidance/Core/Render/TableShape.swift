import Foundation

/// v52 not almada tablo (Android `TableShape` ile aynı anahtarlar ve aynı yerleşim): tek bir şekil elemanı
/// (kind [KIND]); bütün içerik ve ayarlar şeklin değerlerinde (ShapeSpec.params). Şekil her değişiklikte bulunduğu
/// kutuda yeniden kurulur.
///
/// Genel anahtarlar: rows, cols, hr, hc, zebra, border (all | outer | rows | header | none), bc, bw, hf, ht, zf, bg,
/// tc, fs, al. Satır ayarları `R.<ayar>.<satır>`, sütun ayarları `C.<ayar>.<sütun>` (h / w, a, f, t, b). Hücre yazısı
/// `T.<satır>.<sütun>`. Öncelik: satır > sütun > başlık > dönüşümlü satır > genel.
enum TableShape {
    static let KIND = "dg.table"
    static let MAX_ROWS = 40
    static let MAX_COLS = 12
    /// Hücre yazısı etiketinin tanımı: "tbl:" + hizalama (l/c/r) + kalınsa "b".
    static let TAG = "tbl:"

    static func isTable(_ kind: String?) -> Bool { kind == KIND }

    struct Preset: Identifiable, Equatable {
        let id: String, border: String, headerFill: String, headerText: String, zebraFill: String
        var bg = "#FFFFFF", text = "#263238", borderStyle = "all"
    }

    static let presets: [Preset] = [
        Preset(id: "classic", border: "#455A64", headerFill: "#ECEFF1", headerText: "#263238", zebraFill: "#F5F7F8"),
        Preset(id: "blue", border: "#1565C0", headerFill: "#1565C0", headerText: "#FFFFFF", zebraFill: "#E3F2FD"),
        Preset(id: "green", border: "#2E7D32", headerFill: "#2E7D32", headerText: "#FFFFFF", zebraFill: "#E8F5E9"),
        Preset(id: "orange", border: "#E65100", headerFill: "#EF6C00", headerText: "#FFFFFF", zebraFill: "#FFF3E0"),
        Preset(id: "purple", border: "#6A1B9A", headerFill: "#7B1FA2", headerText: "#FFFFFF", zebraFill: "#F3E5F5"),
        Preset(id: "minimal", border: "#9E9E9E", headerFill: "#FFFFFF", headerText: "#111111", zebraFill: "#FAFAFA", borderStyle: "rows"),
    ]

    static let borderStyles = ["all", "outer", "rows", "header", "none"]

    static let palette = [
        "#FFFFFF", "#F5F5F5", "#ECEFF1", "#FFF9C4", "#FFE0B2", "#FFCDD2", "#F8BBD0", "#E1BEE7", "#BBDEFB", "#B2EBF2", "#C8E6C9", "#DCEDC8",
        "#263238", "#000000", "#455A64", "#1565C0", "#2E7D32", "#C62828", "#AD1457", "#6A1B9A", "#E65100", "#F9A825", "#00838F", "#5D4037",
    ]

    static func create(rows: Int, cols: Int, header: Bool, headerCol: Bool, zebra: Bool, borderStyle: String, preset: Preset) -> [String: String] {
        var m: [String: String] = [
            "rows": String(min(max(rows, 1), MAX_ROWS)), "cols": String(min(max(cols, 1), MAX_COLS)),
            "hr": header ? "1" : "0", "hc": headerCol ? "1" : "0", "zebra": zebra ? "1" : "0", "border": borderStyle,
        ]
        applyPreset(&m, preset)
        return m
    }

    static func applyPreset(_ m: inout [String: String], _ p: Preset) {
        m["bc"] = p.border; m["hf"] = p.headerFill; m["ht"] = p.headerText; m["zf"] = p.zebraFill; m["bg"] = p.bg; m["tc"] = p.text
    }

    // MARK: Okuma

    static func rows(_ p: [String: String]?) -> Int { p?["rows"].flatMap { Int($0) }.map { min(max($0, 1), MAX_ROWS) } ?? 3 }
    static func cols(_ p: [String: String]?) -> Int { p?["cols"].flatMap { Int($0) }.map { min(max($0, 1), MAX_COLS) } ?? 3 }
    static func flag(_ p: [String: String]?, _ key: String, _ def: Bool = false) -> Bool { p?[key].map { $0 == "1" } ?? def }
    static func cell(_ p: [String: String]?, _ r: Int, _ c: Int) -> String { p?["T.\(r).\(c)"] ?? "" }
    static func rowProp(_ p: [String: String]?, _ prop: String, _ r: Int) -> String? { p?["R.\(prop).\(r)"].flatMap { $0.isEmpty ? nil : $0 } }
    static func colProp(_ p: [String: String]?, _ prop: String, _ c: Int) -> String? { p?["C.\(prop).\(c)"].flatMap { $0.isEmpty ? nil : $0 } }
    private static func g(_ p: [String: String]?, _ key: String, _ def: String) -> String { p?[key].flatMap { $0.isEmpty ? nil : $0 } ?? def }

    static func isHeader(_ p: [String: String]?, _ r: Int, _ c: Int) -> Bool { (r == 0 && flag(p, "hr", true)) || (c == 0 && flag(p, "hc")) }

    static func fillOf(_ p: [String: String]?, _ r: Int, _ c: Int) -> String {
        if let v = rowProp(p, "f", r) { return v }
        if let v = colProp(p, "f", c) { return v }
        if isHeader(p, r, c) { return g(p, "hf", "#ECEFF1") }
        let body = r - (flag(p, "hr", true) ? 1 : 0)
        if flag(p, "zebra") && body >= 0 && body % 2 == 1 { return g(p, "zf", "#F5F7F8") }
        return g(p, "bg", "#FFFFFF")
    }

    static func textOf(_ p: [String: String]?, _ r: Int, _ c: Int) -> String {
        rowProp(p, "t", r) ?? colProp(p, "t", c) ?? (isHeader(p, r, c) ? g(p, "ht", "#263238") : g(p, "tc", "#263238"))
    }

    static func boldOf(_ p: [String: String]?, _ r: Int, _ c: Int) -> Bool {
        (rowProp(p, "b", r) ?? colProp(p, "b", c)).map { $0 == "1" } ?? isHeader(p, r, c)
    }

    static func alignOf(_ p: [String: String]?, _ r: Int, _ c: Int) -> String {
        rowProp(p, "a", r) ?? colProp(p, "a", c) ?? ((r == 0 && flag(p, "hr", true)) ? "c" : g(p, "al", "l"))
    }

    private static func weight(_ v: String?) -> Float { v.flatMap { Float($0) }.map { min(max($0, 0.2), 8) } ?? 1 }

    // MARK: Çizim

    static func paths(_ start: P, _ end: P, ink: Float, params p: [String: String]?) -> [ShapePath] {
        let left = min(start.x, end.x), top = min(start.y, end.y)
        let width = max(8, abs(end.x - start.x)), height = max(8, abs(end.y - start.y))
        let nr = rows(p), nc = cols(p)
        let rw = (0..<nr).map { weight(rowProp(p, "h", $0)) }
        let cw = (0..<nc).map { weight(colProp(p, "w", $0)) }
        var xs = [Float](repeating: 0, count: nc + 1), ys = [Float](repeating: 0, count: nr + 1)
        do { var s: Float = 0; let t = cw.reduce(0, +); for i in 0..<nc { xs[i] = left + width * s / t; s += cw[i] }; xs[nc] = left + width }
        do { var s: Float = 0; let t = rw.reduce(0, +); for i in 0..<nr { ys[i] = top + height * s / t; s += rw[i] }; ys[nr] = top + height }
        var out: [ShapePath] = []
        for r in 0..<nr { for c in 0..<nc {
            out.append(rect(xs[c], ys[r], xs[c + 1], ys[r + 1], fillOf(p, r, c), "cell:\(r):\(c)"))
        } }
        let bc = g(p, "bc", "#455A64")
        let bw = min(max(p?["bw"].flatMap { Float($0) } ?? 1, 0.25), 4)
        let style = g(p, "border", "all")
        let header = flag(p, "hr", true)
        func line(_ a: P, _ b: P, _ scale: Float, _ tag: String) {
            out.append(ShapePath(points: [a, b], strokeColorHex: bc, widthScale: scale * bw, tag: tag))
        }
        if style != "none" {
            if style == "rows" {
                line(P(left, top), P(left + width, top), 1, "border:top")
                line(P(left, top + height), P(left + width, top + height), 1, "border:bottom")
            } else {
                out.append(ShapePath(points: [P(left, top), P(left + width, top), P(left + width, top + height), P(left, top + height), P(left, top)],
                                     closed: true, strokeColorHex: bc, widthScale: bw, tag: "border:outer"))
            }
            if nr > 1 { for r in 1..<nr {
                let headLine = header && r == 1
                let draw: Bool
                switch style { case "all", "rows": draw = true; case "header": draw = headLine; default: draw = false }
                if draw || (headLine && style != "outer") { line(P(left, ys[r]), P(left + width, ys[r]), headLine ? 2 : 0.6, "border:r:\(r)") }
            } }
            if style == "all" && nc > 1 { for c in 1..<nc {
                let headLine = flag(p, "hc") && c == 1
                line(P(xs[c], top), P(xs[c], top + height), headLine ? 2 : 0.6, "border:c:\(c)")
            } }
        }
        let fs = min(max(p?["fs"].flatMap { Float($0) } ?? 0.42, 0.15), 0.9)
        let minRow = (0..<nr).map { ys[$0 + 1] - ys[$0] }.min() ?? 1
        for r in 0..<nr { for c in 0..<nc {
            let text = cell(p, r, c)
            if text.trimmingCharacters(in: .whitespaces).isEmpty { continue }
            let x0 = xs[c], x1 = xs[c + 1], y0 = ys[r], y1 = ys[r + 1]
            let bold = boldOf(p, r, c)
            let pad = min(minRow * 0.22, (x1 - x0) * 0.08)
            var size = min(minRow, y1 - y0) * fs
            let est = Float(text.count) * size * (bold ? 0.6 : 0.55)
            if est > x1 - x0 - 2 * pad { size = max(4, size * (x1 - x0 - 2 * pad) / est) }
            let align = alignOf(p, r, c)
            let x: Float = align == "c" ? (x0 + x1) / 2 : align == "r" ? x1 - pad : x0 + pad
            let y = (y0 + y1) / 2 - size * 0.5
            out.append(ShapePath(points: [P(x, y)], label: text, labelSize: size, strokeColorHex: textOf(p, r, c), tag: TAG + align + (bold ? "b" : "")))
        } }
        return out
    }

    private static func rect(_ l: Float, _ t: Float, _ r: Float, _ b: Float, _ fill: String, _ tag: String) -> ShapePath {
        ShapePath(points: [P(l, t), P(r, t), P(r, b), P(l, b), P(l, t)], closed: true, fillColorHex: fill, widthScale: 0, tag: tag)
    }

    // MARK: Düzenleme

    static func insertRow(_ p: [String: String], _ at: Int) -> [String: String] {
        let n = rows(p); if n >= MAX_ROWS { return p }
        var m = shift(p, isRow: true, from: at, delta: 1); m["rows"] = String(n + 1); return m
    }

    static func deleteRow(_ p: [String: String], _ at: Int) -> [String: String] {
        let n = rows(p); if n <= 1 { return p }
        var m = shift(p.filter { idx($0.key, true) != at }, isRow: true, from: at + 1, delta: -1); m["rows"] = String(n - 1); return m
    }

    static func insertCol(_ p: [String: String], _ at: Int) -> [String: String] {
        let n = cols(p); if n >= MAX_COLS { return p }
        var m = shift(p, isRow: false, from: at, delta: 1); m["cols"] = String(n + 1); return m
    }

    static func deleteCol(_ p: [String: String], _ at: Int) -> [String: String] {
        let n = cols(p); if n <= 1 { return p }
        var m = shift(p.filter { idx($0.key, false) != at }, isRow: false, from: at + 1, delta: -1); m["cols"] = String(n - 1); return m
    }

    static func resize(_ p: [String: String], rows nr0: Int, cols nc0: Int) -> [String: String] {
        let nr = min(max(nr0, 1), MAX_ROWS), nc = min(max(nc0, 1), MAX_COLS)
        var m = p.filter { k, _ in (idx(k, true).map { $0 < nr } ?? true) && (idx(k, false).map { $0 < nc } ?? true) }
        m["rows"] = String(nr); m["cols"] = String(nc)
        return m
    }

    private static func idx(_ key: String, _ isRow: Bool) -> Int? {
        let parts = key.split(separator: ".", omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 3 else { return nil }
        if parts[0] == "T" { return Int(isRow ? parts[1] : parts[2]) }
        if parts[0] == (isRow ? "R" : "C") { return Int(parts[2]) }
        return nil
    }

    private static func shift(_ p: [String: String], isRow: Bool, from: Int, delta: Int) -> [String: String] {
        var out: [String: String] = [:]
        for (k, v) in p {
            guard let i = idx(k, isRow), i >= from else { out[k] = v; continue }
            var parts = k.split(separator: ".", omittingEmptySubsequences: false).map(String.init)
            if parts[0] == "T" { parts[isRow ? 1 : 2] = String(i + delta) } else { parts[2] = String(i + delta) }
            out[parts.joined(separator: ".")] = v
        }
        return out
    }

    static func setLineProp(_ p: [String: String], isRow: Bool, _ prop: String, _ index: Int, _ value: String?) -> [String: String] {
        var m = p
        m["\(isRow ? "R" : "C").\(prop).\(index)"] = value
        return m
    }

    static func setGlobal(_ p: [String: String], _ key: String, _ value: String?, clearProp: String? = nil) -> [String: String] {
        var m = p
        m[key] = value
        if let c = clearProp { m = m.filter { !($0.key.hasPrefix("R.\(c).") || $0.key.hasPrefix("C.\(c).")) } }
        return m
    }

    static func setCell(_ p: [String: String], _ r: Int, _ c: Int, _ text: String) -> [String: String] {
        var m = p
        m["T.\(r).\(c)"] = text.isEmpty ? nil : text
        return m
    }

    static func colName(_ c: Int) -> String { String(UnicodeScalar(UInt8(65 + c))) }

    static func weightSum(_ p: [String: String]?, isRow: Bool) -> Float {
        isRow ? (0..<rows(p)).map { weight(rowProp(p, "h", $0)) }.reduce(0, +) : (0..<cols(p)).map { weight(colProp(p, "w", $0)) }.reduce(0, +)
    }
}
