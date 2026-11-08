import Foundation

/// Birim kutuda bir nokta: x sağa, y aşağı (Android `content.vector.P`).
struct P: Equatable {
    var x: Float
    var y: Float
    init(_ x: Float, _ y: Float) { self.x = x; self.y = y }
    static func + (a: P, b: P) -> P { P(a.x + b.x, a.y + b.y) }
    static func - (a: P, b: P) -> P { P(a.x - b.x, a.y - b.y) }
    static func * (a: P, k: Float) -> P { P(a.x * k, a.y * k) }
}

/// Renk yerine yazılabilen özel değer: elemanın kalem (kenar) rengi.
let INK_COLOR = "@ink"

enum LineStyle: String, CaseIterable { case SOLID, DASHED, DOTTED }

/// Vektör çizimin tek parçası (uygulamada şeklin bir alt yolu).
struct VPath: Equatable {
    var points: [P]
    var closed = false
    var fill: String? = nil
    var stroke: String? = nil
    var width: Float = 1
    var line: LineStyle = .SOLID
    var alpha: Float = 1
    var label: String? = nil
    var labelSize: Float = 0
    var tag: String? = nil
}

/// Şeklin parmakla yerleştirilişi.
enum Fit: String, CaseIterable { case BOX, SQUARE, LINE }

/// Şeklin sunulduğu tuval türleri.
enum Audience: String, CaseIterable { case ART, NOTES, BOTH, KIDS }

/// Kütüphanedeki bir şekil. iOS'ta hazır şekillerin yolları uygulamayla gelen katalogdan okunur.
struct LibShape: Identifiable {
    let id: String
    let title: String
    var fit: Fit = .BOX
    var aspect: Float = 1
    var tags: [String] = []
    let paths: [VPath]
}

struct LibCategory: Identifiable {
    let id: String
    let title: String
    let audience: Audience
    var shapes: [LibShape]
    var section = ""
    var listed = true
}
