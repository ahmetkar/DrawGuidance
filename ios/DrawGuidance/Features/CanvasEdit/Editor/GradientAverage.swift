import Foundation

extension Gradients {
    /// Gradyan renklerinin ortalaması (ARGB).
    static func average(_ spec: GradientSpec) -> UInt32 {
        let cols = colors(spec)
        var a: UInt32 = 0, r: UInt32 = 0, g: UInt32 = 0, b: UInt32 = 0
        for c in cols { a += c >> 24; r += (c >> 16) & 0xFF; g += (c >> 8) & 0xFF; b += c & 0xFF }
        let n = UInt32(max(cols.count, 1))
        return ((a / n) << 24) | ((r / n) << 16) | ((g / n) << 8) | (b / n)
    }
}
