import Foundation

/// Glif biçemi: değişkenler İTALİK; rakamlar, fonksiyon adları, işleçler ve \text DÜZ; \mathbf KALIN (Android `GlyphStyle`).
enum GlyphStyle: Int { case ROMAN, ITALIC, BOLD, BOLD_ITALIC }

/// Platformun yazı ölçüleri (Android `GlyphMetrics`): boyutlar dizgi koordinatlarıyla aynı birimde.
protocol GlyphMetrics {
    func advance(_ text: String, _ size: Float, _ style: GlyphStyle) -> Float
    func ascent(_ size: Float) -> Float
    func descent(_ size: Float) -> Float
    /// [left, top, right, bottom] (y aşağı, taban çizgisi 0); yoksa nil (motor tahmin eder).
    func inkBounds(_ text: String, _ size: Float, _ style: GlyphStyle) -> [Float]?
}

extension GlyphMetrics {
    func inkBounds(_ text: String, _ size: Float, _ style: GlyphStyle) -> [Float]? { nil }
}

/// Çizim komutları (y aşağı).
enum DrawOp: Equatable {
    /// Taban çizgisi y'de olan yazı; slot: ait olduğu düzenlenebilir yuva.
    case glyph(text: String, x: Float, y: Float, size: Float, style: GlyphStyle, slot: String?)
    /// Düz çizgi (uçlar düz).
    case rule(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float)
    /// Çoklu çizgi ya da dolu çokgen.
    case stroke(points: [Float], thickness: Float, closed: Bool, filled: Bool)

    static func line(_ pts: [Float], _ t: Float, closed: Bool = false, filled: Bool = false) -> DrawOp {
        .stroke(points: pts, thickness: t, closed: closed, filled: filled)
    }

    func shifted(_ dx: Float, _ dy: Float) -> DrawOp {
        if dx == 0 && dy == 0 { return self }
        switch self {
        case let .glyph(t, x, y, s, st, slot): return .glyph(text: t, x: x + dx, y: y + dy, size: s, style: st, slot: slot)
        case let .rule(x1, y1, x2, y2, th): return .rule(x1: x1 + dx, y1: y1 + dy, x2: x2 + dx, y2: y2 + dy, thickness: th)
        case let .stroke(p, th, c, f):
            var q = p
            for k in q.indices { q[k] += k % 2 == 0 ? dx : dy }
            return .stroke(points: q, thickness: th, closed: c, filled: f)
        }
    }

    var isFinite: Bool {
        switch self {
        case let .glyph(_, x, y, s, _, _): return x.isFinite && y.isFinite && s.isFinite
        case let .rule(x1, y1, x2, y2, _): return x1.isFinite && y1.isFinite && x2.isFinite && y2.isFinite
        case let .stroke(p, _, _, _): return p.allSatisfy(\.isFinite)
        }
    }
}

/// Düzenlenebilir yuvanın dokunma/vurgulama alanı.
struct SlotRegion: Equatable {
    var name: String
    var left: Float, top: Float, right: Float, bottom: Float
}

/// Dizilmiş formül: başlangıç ana taban çizgisinin sol ucu, x sağa, y aşağı.
struct FormulaLayout {
    let width: Float, ascent: Float, descent: Float
    let ops: [DrawOp]
    let slots: [SlotRegion]
    var height: Float { ascent + descent }
}

struct Slot: Equatable { let name: String; let value: String }
struct FormulaTemplate { let id: String; let title: String; let source: String }
struct FormulaCategory { let id: String; let title: String; let templates: [FormulaTemplate] }

// MARK: - Kotlin karakter yardımcıları

extension Character {
    var isAsciiLetterK: Bool { ("a"..."z").contains(self) || ("A"..."Z").contains(self) }
    var isAsciiDigitK: Bool { ("0"..."9").contains(self) }
    /// Kotlin `Char.isDigit()` (Unicode ondalık rakam).
    var isDigitK: Bool { unicodeScalars.first.map { $0.properties.generalCategory == .decimalNumber } ?? false }
    var isLetterOrDigitK: Bool { isLetter || isDigitK }
}

/// Kotlin `String` dizin işlemleri için karakter dizisi yardımcıları.
extension Array where Element == Character {
    func str(_ a: Int, _ b: Int) -> String { a >= b ? "" : String(self[a..<Swift.min(b, count)]) }
    func starts(with p: String, at i: Int) -> Bool {
        let q = Array(p)
        if i < 0 || i + q.count > count { return false }
        for k in 0..<q.count where self[i + k] != q[k] { return false }
        return true
    }
    func index(of c: Character, from i: Int) -> Int {
        var k = Swift.max(i, 0)
        while k < count { if self[k] == c { return k }; k += 1 }
        return -1
    }
}
