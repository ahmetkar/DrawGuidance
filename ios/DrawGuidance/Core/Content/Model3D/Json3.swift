import Foundation

/// Küçük JSON ağacı (kotlinx.serialization `JsonElement` karşılığı): nesne anahtar sırası korunur, sayılar yazıldığı
/// metinle saklanır (Kotlin `intOrNull`/`doubleOrNull` anlamlarıyla aynı okunur).
indirect enum JV {
    case null_
    case bool(Bool)
    case num(String)
    case str(String)
    case arr([JV])
    case obj([(String, JV)])

    static func i(_ v: Int) -> JV { .num(String(v)) }
    static func i(_ v: Int32) -> JV { .num(String(v)) }
    static func l(_ v: Int64) -> JV { .num(String(v)) }
    static func f(_ v: Float) -> JV { .num(javaFloatString(v)) }
    static func d(_ v: Double) -> JV { .num(javaDoubleString(v)) }
    static func s(_ v: String) -> JV { .str(v) }
    static func b(_ v: Bool) -> JV { .bool(v) }

    // MARK: okuma (Kotlin `JsonObject`/`JsonPrimitive` uzantıları)

    subscript(_ key: String) -> JV? {
        if case .obj(let kv) = self { return kv.first { $0.0 == key }?.1 }
        return nil
    }
    var object: [(String, JV)]? { if case .obj(let kv) = self { return kv }; return nil }
    var array: [JV]? { if case .arr(let a) = self { return a }; return nil }
    var isPrimitive: Bool { switch self { case .arr, .obj: return false; default: return true } }

    /// `JsonPrimitive.contentOrNull`.
    var content: String? {
        switch self {
        case .null_: return nil
        case .bool(let b): return b ? "true" : "false"
        case .num(let s), .str(let s): return s
        default: return nil
        }
    }
    var str: String? { if case .str(let s) = self { return s }; return isPrimitive ? content : nil }
    var int: Int? { guard let c = content, let v = Int32(c) else { return nil }; return Int(v) }
    var int32: Int32? { content.flatMap { Int32($0) } }
    var long: Int64? { content.flatMap { Int64($0) } }
    var double: Double? { content.flatMap { ktParseDouble($0) } }
    var float: Float? { double.map { Float($0) } }
    var bool: Bool? { switch content { case "true": return true; case "false": return false; default: return nil } }
    var floats: [Float]? { array?.compactMap { $0.isPrimitive ? $0.float : nil } }

    // MARK: yazma (kotlinx `toString` biçimi)

    var text: String { var s = ""; write(&s); return s }

    private func write(_ o: inout String) {
        switch self {
        case .null_: o += "null"
        case .bool(let b): o += b ? "true" : "false"
        case .num(let s): o += s
        case .str(let s): JV.quote(s, &o)
        case .arr(let a):
            o += "["
            for (i, e) in a.enumerated() { if i > 0 { o += "," }; e.write(&o) }
            o += "]"
        case .obj(let kv):
            o += "{"
            for (i, (k, v)) in kv.enumerated() { if i > 0 { o += "," }; JV.quote(k, &o); o += ":"; v.write(&o) }
            o += "}"
        }
    }

    private static func quote(_ s: String, _ o: inout String) {
        o += "\""
        for u in s.unicodeScalars {
            switch u {
            case "\"": o += "\\\""
            case "\\": o += "\\\\"
            case "\n": o += "\\n"
            case "\r": o += "\\r"
            case "\t": o += "\\t"
            case "\u{08}": o += "\\b"
            case "\u{0C}": o += "\\f"
            default:
                if u.value < 0x20 { o += String(format: "\\u%04x", u.value) } else { o.unicodeScalars.append(u) }
            }
        }
        o += "\""
    }

    // MARK: ayrıştırma

    struct ParseError: Error {}

    static func parse(_ bytes: [UInt8]) throws -> JV {
        var p = Parser(b: bytes)
        p.ws()
        let v = try p.value(0)
        p.ws()
        if p.i != bytes.count { throw ParseError() }
        return v
    }

    private struct Parser {
        let b: [UInt8]
        var i = 0

        mutating func ws() { while i < b.count, b[i] == 0x20 || b[i] == 0x0A || b[i] == 0x0D || b[i] == 0x09 { i += 1 } }

        mutating func value(_ depth: Int) throws -> JV {
            guard i < b.count, depth < 512 else { throw ParseError() }
            switch b[i] {
            case UInt8(ascii: "{"):
                i += 1; ws()
                var kv: [(String, JV)] = []
                if i < b.count, b[i] == UInt8(ascii: "}") { i += 1; return .obj(kv) }
                while true {
                    ws()
                    guard i < b.count, b[i] == UInt8(ascii: "\"") else { throw ParseError() }
                    let k = try string()
                    ws()
                    guard i < b.count, b[i] == UInt8(ascii: ":") else { throw ParseError() }
                    i += 1; ws()
                    let v = try value(depth + 1)
                    if let idx = kv.firstIndex(where: { $0.0 == k }) { kv[idx].1 = v } else { kv.append((k, v)) }
                    ws()
                    guard i < b.count else { throw ParseError() }
                    if b[i] == UInt8(ascii: ",") { i += 1; continue }
                    if b[i] == UInt8(ascii: "}") { i += 1; return .obj(kv) }
                    throw ParseError()
                }
            case UInt8(ascii: "["):
                i += 1; ws()
                var a: [JV] = []
                if i < b.count, b[i] == UInt8(ascii: "]") { i += 1; return .arr(a) }
                while true {
                    ws()
                    a.append(try value(depth + 1))
                    ws()
                    guard i < b.count else { throw ParseError() }
                    if b[i] == UInt8(ascii: ",") { i += 1; continue }
                    if b[i] == UInt8(ascii: "]") { i += 1; return .arr(a) }
                    throw ParseError()
                }
            case UInt8(ascii: "\""): return .str(try string())
            case UInt8(ascii: "t"): try lit("true"); return .bool(true)
            case UInt8(ascii: "f"): try lit("false"); return .bool(false)
            case UInt8(ascii: "n"): try lit("null"); return .null_
            default:
                let s = i
                while i < b.count, (b[i] >= 0x30 && b[i] <= 0x39) || b[i] == UInt8(ascii: "-") || b[i] == UInt8(ascii: "+") ||
                    b[i] == UInt8(ascii: ".") || b[i] == UInt8(ascii: "e") || b[i] == UInt8(ascii: "E") { i += 1 }
                if s == i { throw ParseError() }
                return .num(String(decoding: b[s..<i], as: UTF8.self))
            }
        }

        mutating func lit(_ w: String) throws {
            let u = Array(w.utf8)
            guard i + u.count <= b.count, Array(b[i..<i + u.count]) == u else { throw ParseError() }
            i += u.count
        }

        mutating func hex4() throws -> UInt32 {
            guard i + 4 <= b.count, let v = UInt32(String(decoding: b[i..<i + 4], as: UTF8.self), radix: 16) else { throw ParseError() }
            i += 4
            return v
        }

        mutating func string() throws -> String {
            i += 1
            var out = [UInt8]()
            while true {
                guard i < b.count else { throw ParseError() }
                let c = b[i]; i += 1
                if c == UInt8(ascii: "\"") { break }
                if c != UInt8(ascii: "\\") { out.append(c); continue }
                guard i < b.count else { throw ParseError() }
                let e = b[i]; i += 1
                switch e {
                case UInt8(ascii: "n"): out.append(0x0A)
                case UInt8(ascii: "r"): out.append(0x0D)
                case UInt8(ascii: "t"): out.append(0x09)
                case UInt8(ascii: "b"): out.append(0x08)
                case UInt8(ascii: "f"): out.append(0x0C)
                case UInt8(ascii: "u"):
                    var v = try hex4()
                    if (0xD800..<0xDC00).contains(v), i + 6 <= b.count, b[i] == UInt8(ascii: "\\"), b[i + 1] == UInt8(ascii: "u") {
                        i += 2
                        let lo = try hex4()
                        let hi: UInt32 = (v - 0xD800) << 10
                        let low: UInt32 = (lo &- 0xDC00) & 0x3FF
                        v = 0x10000 + hi + low
                    }
                    out.append(contentsOf: Array(String(Unicode.Scalar(v).map(Character.init) ?? "\u{FFFD}").utf8))
                default: out.append(e)
                }
            }
            return String(decoding: out, as: UTF8.self)
        }
    }
}

/// Kotlin `String.toDoubleOrNull` (Java `Double.parseDouble`): baştaki/sondaki boşluk, "NaN", "Infinity" kabul edilir.
func ktParseDouble(_ s: String) -> Double? {
    let t = s.trimmingCharacters(in: .whitespaces)
    if t.isEmpty { return nil }
    switch t {
    case "NaN", "+NaN", "-NaN": return .nan
    case "Infinity", "+Infinity": return .infinity
    case "-Infinity": return -.infinity
    default: break
    }
    var u = t
    if let last = u.last, "fFdD".contains(last) { u.removeLast() }
    if u.lowercased().contains("inf") || u.lowercased().contains("nan") || u.hasPrefix("0x") || u.hasPrefix("0X") { return nil }
    return Double(u)
}

/// Java `Float.toString` biçimi (en kısa basamaklar; 1e-3 ≤ |v| < 1e7 düz, dışı "1.0E-5").
func javaFloatString(_ v: Float) -> String { javaNumberString("\(v)", v.isNaN, v.isInfinite, v < 0, v == 0) }
/// Java `Double.toString` biçimi.
func javaDoubleString(_ v: Double) -> String { javaNumberString("\(v)", v.isNaN, v.isInfinite, v < 0, v == 0) }

private func javaNumberString(_ swift: String, _ nan: Bool, _ inf: Bool, _ neg: Bool, _ zero: Bool) -> String {
    if nan { return "NaN" }
    if inf { return neg ? "-Infinity" : "Infinity" }
    if zero { return swift.hasPrefix("-") ? "-0.0" : "0.0" }
    // Swift en kısa basamaklarını ve üssünü çıkar
    var s = swift
    let negative = s.hasPrefix("-")
    if negative { s.removeFirst() }
    var exp = 0
    if let e = s.firstIndex(where: { $0 == "e" || $0 == "E" }) {
        exp = Int(s[s.index(after: e)...].replacingOccurrences(of: "+", with: "")) ?? 0
        s = String(s[..<e])
    }
    var intPart = s, frac = ""
    if let dot = s.firstIndex(of: ".") { intPart = String(s[..<dot]); frac = String(s[s.index(after: dot)...]) }
    var digits = intPart + frac
    var pointPos = intPart.count + exp // ondalık noktanın basamak dizisindeki yeri
    // baştaki sıfırlar
    while digits.count > 1, digits.first == "0" { digits.removeFirst(); pointPos -= 1 }
    while digits.count > 1, digits.last == "0" { digits.removeLast() }
    // Değer = 0.digits × 10^pointPos ; bilimsel üs = pointPos - 1
    let sciExp = pointPos - 1
    var out = negative ? "-" : ""
    if sciExp >= -3 && sciExp < 7 {
        if pointPos <= 0 {
            out += "0." + String(repeating: "0", count: -pointPos) + digits
        } else if pointPos >= digits.count {
            out += digits + String(repeating: "0", count: pointPos - digits.count) + ".0"
        } else {
            let idx = digits.index(digits.startIndex, offsetBy: pointPos)
            out += String(digits[..<idx]) + "." + String(digits[idx...])
        }
    } else {
        let first = digits.prefix(1), rest = digits.dropFirst()
        out += first + "." + (rest.isEmpty ? "0" : String(rest)) + "E" + String(sciExp)
    }
    return out
}
