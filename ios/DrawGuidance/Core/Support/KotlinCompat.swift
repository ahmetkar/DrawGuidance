import Foundation

// Kotlin'den birebir taşınan kod (karakterler, çizgi roman) için küçük uyum yardımcıları. JVM'de Float matematik
// işlevleri Double'da hesaplanıp Float'a çevrilir; burada da aynısı yapılır (Android ile aynı çıktı).

@inline(__always) func toDegrees(_ rad: Double) -> Double { rad * 57.29577951308232 }
@inline(__always) func kSin(_ x: Double) -> Double { sin(x) }
@inline(__always) func kCos(_ x: Double) -> Double { cos(x) }
@inline(__always) func kAtan2(_ y: Double, _ x: Double) -> Double { atan2(y, x) }
@inline(__always) func kAtan2(_ y: Float, _ x: Float) -> Float { Float(atan2(Double(y), Double(x))) }
@inline(__always) func kAcos(_ x: Float) -> Float { Float(acos(Double(x))) }
@inline(__always) func kSign(_ x: Float) -> Float { x > 0 ? 1 : (x < 0 ? -1 : x) }

extension Float {
    @inline(__always) func toFloat() -> Float { self }
    @inline(__always) func toDouble() -> Double { Double(self) }
    /// Kotlin `toInt()`: sıfıra doğru keser (NaN → 0, taşma sınırda kalır).
    @inline(__always) func toInt() -> Int { isNaN ? 0 : Int(Swift.max(-2147483648, Swift.min(2147483647, self.rounded(.towardZero)))) }
    @inline(__always) func coerceIn(_ lo: Float, _ hi: Float) -> Float { Swift.min(Swift.max(self, lo), hi) }
    @inline(__always) func coerceAtLeast(_ lo: Float) -> Float { Swift.max(self, lo) }
    @inline(__always) func coerceAtMost(_ hi: Float) -> Float { Swift.min(self, hi) }
}

extension Double {
    @inline(__always) func toFloat() -> Float { Float(self) }
    @inline(__always) func toDouble() -> Double { self }
    @inline(__always) func coerceIn(_ lo: Double, _ hi: Double) -> Double { Swift.min(Swift.max(self, lo), hi) }
    @inline(__always) func coerceAtLeast(_ lo: Double) -> Double { Swift.max(self, lo) }
    @inline(__always) func coerceAtMost(_ hi: Double) -> Double { Swift.min(self, hi) }
}

extension Int {
    @inline(__always) func toFloat() -> Float { Float(self) }
    @inline(__always) func toDouble() -> Double { Double(self) }
    @inline(__always) func coerceIn(_ lo: Int, _ hi: Int) -> Int { Swift.min(Swift.max(self, lo), hi) }
    @inline(__always) func coerceAtLeast(_ lo: Int) -> Int { Swift.max(self, lo) }
    @inline(__always) func coerceAtMost(_ hi: Int) -> Int { Swift.min(self, hi) }
}

extension Array where Element == Float {
    @inline(__always) func toFloatArray() -> [Float] { self }
}
