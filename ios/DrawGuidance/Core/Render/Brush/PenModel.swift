import Foundation

/// v51 kalem ve hareket dinamikleri (`PenDynamics`) ile boyut aralığının (`TipParams.minSize`, `TipParams.maxSize`)
/// bir ham noktadaki etkisi (Android `PenModel`; aynı formüller).
///
/// Girdiler ham nokta dizileridir; hız, yön, mesafe ve zaman noktalardan türetilir (zaman: nokta sırası, ~120 Hz).
/// Böylece kaydedilen çizim her açılışta aynı görünür.
final class PenModel {
    static let DISTANCE_SPAN: Float = 40
    static let TIME_SPAN: Float = 240

    private let tip: TipParams?
    private let pen: PenDynamics?

    private init(tip: TipParams?, pen: PenDynamics?) { self.tip = tip; self.pen = pen }

    /// Ayar yoksa nil (motorlar eskisi gibi çizer).
    static func of(_ params: BrushParams?) -> PenModel? {
        let tip = params?.tip.flatMap { $0.minSize != nil || $0.maxSize != nil ? $0 : nil }
        let pen = params?.pen?.orNil
        if tip == nil && pen == nil { return nil }
        return PenModel(tip: tip, pen: pen)
    }

    /// Basıncın boyuta etkisi ayarlandı mı (ayarlanmadıysa fırçanın kendi basınç eğrisi kullanılır).
    var overridesPressure: Bool { pen?.pressure != nil }

    @inline(__always) private static func clamp(_ v: Float, _ lo: Float, _ hi: Float) -> Float { min(max(v, lo), hi) }

    /// Boyut çarpanı: [base] fırçanın kendi dinamiklerinden gelen çarpan; önce kalem dinamikleriyle çarpılır, sonra
    /// en küçük/en büyük boyut aralığına yerleştirilir.
    func size(_ base: Float, _ p: PointData, _ i: Int) -> Float {
        var f = base
        if let d = pen {
            if let amt = d.pressure {
                let pr = Self.clamp(p.pressure(i), 0, 1)
                f *= 1 - Self.clamp(amt, 0, 1) * (1 - Float(pow(Double(pr), 0.7)))
            }
            let tilt = p.tilt(i)
            if tilt > 0 {
                let az = p.azimuth(i)
                let tx = tilt * cos(az), ty = tilt * sin(az)
                if let v = d.tiltX { f *= max(1 + v * abs(tx), 0.05) }
                if let v = d.tiltY { f *= max(1 + v * abs(ty), 0.05) }
            }
            if let amt = d.velocity { f *= Self.clamp(1 + amt * (p.speed(i) - 0.35) * 1.4, 0.1, 2.5) }
            if let amt = d.distance {
                f *= max(1 - Self.clamp(amt, 0, 1) * min(p.along(i) / max(1, p.width * Self.DISTANCE_SPAN), 1), 0.05)
            }
            if let amt = d.barrel { f *= max(1 + amt * p.barrel(i), 0.05) }
        }
        guard let t = tip else { return f }
        let lo = Self.clamp(t.minSize ?? 0, 0, 1)
        let hi = Self.clamp(t.maxSize ?? 1, 0.1, 3)
        return (lo + (1 - lo) * f) * hi
    }

    /// Opaklık çarpanı (basıncın opaklığa etkisi ve zamanla solma).
    func alpha(_ p: PointData, _ i: Int) -> Float {
        guard let d = pen else { return 1 }
        var a: Float = 1
        if let amt = d.pressureOpacity { a *= 1 - Self.clamp(amt, 0, 1) * (1 - Self.clamp(p.pressure(i), 0, 1)) }
        if let amt = d.time { a *= max(1 - Self.clamp(amt, 0, 1) * min(Float(i) / Self.TIME_SPAN, 1), 0.05) }
        return Self.clamp(a, 0.02, 1)
    }

    /// Uç açısına eklenecek dönüş (radyan): kalemin yöneldiği yön, gövde dönüşü ve çizgi yönü.
    func turn(_ p: PointData, _ i: Int, _ dirX: Float, _ dirY: Float) -> Float {
        guard let d = pen else { return 0 }
        var a: Float = 0
        if let v = d.tiltDirection, p.tilt(i) > 0 { a += v * p.azimuth(i) }
        if let v = d.rotation { a += v * p.roll(i) * 2 * Float.pi }
        if let v = d.direction { a += v * atan2(dirY, dirX) }
        return a
    }

    /// Bir çizginin ham nokta dizileri ve iz genişliği (hız/mesafe ölçeği için).
    final class PointData {
        private(set) var xs: [Float], ys: [Float]
        private(set) var count: Int
        private(set) var pp: [Float]?, pt: [Float]?, po: [Float]?, pr: [Float]?, pb: [Float]?
        let width: Float
        private var lengths: [Float] = []
        private var measured = 0

        init(_ xs: [Float], _ ys: [Float], _ count: Int, _ pp: [Float]?, _ pt: [Float]?, _ po: [Float]?, _ pr: [Float]?, _ pb: [Float]?,
             width: Float) {
            self.xs = xs; self.ys = ys; self.count = count
            self.pp = pp; self.pt = pt; self.po = po; self.pr = pr; self.pb = pb
            self.width = width
        }

        /// Canlı çizimde diziler büyüyünce güncellenir; ölçülen uzunluklar önek kararlı olduğundan korunur.
        func update(_ xs: [Float], _ ys: [Float], _ count: Int, _ pp: [Float]?, _ pt: [Float]?, _ po: [Float]?, _ pr: [Float]?, _ pb: [Float]?) {
            self.xs = xs; self.ys = ys; self.count = count
            self.pp = pp; self.pt = pt; self.po = po; self.pr = pr; self.pb = pb
            if measured > count { measured = 0 }
        }

        @inline(__always) private func at(_ a: [Float]?, _ i: Int, _ def: Float) -> Float {
            guard let a, count > 0 else { return def }
            let k = min(max(i, 0), count - 1)
            return k < a.count ? a[k] : def
        }

        func pressure(_ i: Int) -> Float { at(pp, i, 1) }
        func tilt(_ i: Int) -> Float { at(pt, i, 0) }
        func azimuth(_ i: Int) -> Float { at(po, i, 0) * 2 * Float.pi }
        func roll(_ i: Int) -> Float { at(pr, i, 0) }
        func barrel(_ i: Int) -> Float { at(pb, i, 0) }

        /// Hız (0..1): ardışık iki ham nokta arasındaki uzaklığın iz genişliğine oranı.
        func speed(_ i: Int) -> Float {
            if count < 2 { return 0 }
            let k = min(max(i, 1), count - 1)
            let d = hypotf(xs[k] - xs[k - 1], ys[k] - ys[k - 1])
            return min(max(d / max(4, width * 0.6), 0), 1)
        }

        /// Çizginin başından ham nokta [i]'ye kadarki uzunluk (artımlı ölçülür).
        func along(_ i: Int) -> Float {
            if count == 0 { return 0 }
            let k = min(max(i, 0), count - 1)
            if lengths.count < count { lengths.append(contentsOf: [Float](repeating: 0, count: max(count, lengths.count * 2) - lengths.count)) }
            if measured == 0 { lengths[0] = 0; measured = 1 }
            while measured <= k {
                let m = measured
                lengths[m] = lengths[m - 1] + hypotf(xs[m] - xs[m - 1], ys[m] - ys[m - 1])
                measured += 1
            }
            return lengths[k]
        }
    }
}
