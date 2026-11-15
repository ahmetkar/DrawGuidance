import Foundation

/// v51 yetişkin alıştırmalarının gelişmiş puanı (Android `PracticeScore`): isabet, kapsama, akıcılık ve süreli çizimde
/// hız. Toplam: süresizde %80 çizgi puanı + %20 akıcılık; süreli çizimde %70 + %15 + %15 hız; çizgi puanını en çok
/// [SMOOTH_BONUS_MAX] geçer; her ipucu [PracticeLevels.HINT_PENALTY] düşer.
struct PracticeScore: Equatable {
    let total: Int
    let outline: Int
    let accuracy: Int
    let coverage: Int
    let smoothness: Int
    let speed: Int?
    let hints: Int

    static let ZERO = PracticeScore(total: 0, outline: 0, accuracy: 0, coverage: 0, smoothness: 0, speed: nil, hints: 0)
    static let SMOOTH_FULL: Float = 0.05
    static let SMOOTH_ZERO: Float = 0.40
    static let SMOOTH_BONUS_MAX = 12
    private static let SMOOTH_SAMPLES: Float = 90

    static func of(_ parts: ChallengeScore.OutlineParts, smoothness: Int, speed: Int?, hints: Int) -> PracticeScore {
        if parts.score <= 0 {
            return PracticeScore(total: 0, outline: 0, accuracy: 0, coverage: 0, smoothness: 0, speed: speed, hints: hints)
        }
        let total = min(max(strict(parts, speed: speed) - hints * PracticeLevels.HINT_PENALTY, 0), 100)
        return PracticeScore(total: total, outline: parts.score, accuracy: parts.precision, coverage: parts.recall,
                             smoothness: smoothness, speed: speed, hints: hints)
    }

    /// v52 sıkı doğruluk puanı (Android `PracticeScore.strict`): akıcılık toplamı etkilemez; F1 üs 2 ile
    /// sıkılaştırılır, isabet ile kapsama farkı 15'i geçerse fazlasının yarısı düşer; süreli çizimde %85 + %15 hız
    /// (hız doğruluğu geçiremez).
    static let STRICT_CURVE: Float = 2
    /// v52 kılavuzun üstünden çizimde kaydırılmış çizimin çarpanı.
    static let ALIGNED: Float = 0.82
    static let BALANCE_FREE = 15
    static func strict(_ parts: ChallengeScore.OutlineParts, speed: Int?) -> Int {
        let f = min(max(Float(parts.score) / 100, 0), 1)
        var acc = 100 * pow(f, STRICT_CURVE)
        let gap = abs(parts.precision - parts.recall) - BALANCE_FREE
        if gap > 0 { acc -= Float(gap) * 0.5 }
        acc = min(max(acc, 0), 100)
        let raw = speed.map { min(acc, 0.85 * acc + 0.15 * Float($0)) } ?? acc
        return Int(raw.rounded())
    }

    /// Süreli çizimde hız: sürenin yarısında 100, sonunda 50.
    static func speed(elapsedMs: Int64, limitMs: Int64) -> Int {
        if limitMs <= 0 { return 100 }
        let r = min(max(Float(elapsedMs) / Float(limitMs), 0), 1)
        return r <= 0.5 ? 100 : min(max(Int((100 - (r - 0.5) * 100).rounded()), 50), 100)
    }

    /// Çizgilerin akıcılığı (0–100): eğrilik değişiminin ortancası.
    static func smoothness(_ lines: [[P]], diag: Float) -> Int {
        let step = max(diag / SMOOTH_SAMPLES, 1)
        var changes: [Float] = []
        for line in lines {
            let pts = ChallengeScore.resample([line], step)
            if pts.count < 4 { continue }
            var prevDir = Float.nan, prevTurn = Float.nan
            for i in 1..<pts.count {
                let dx = pts[i].x - pts[i - 1].x, dy = pts[i].y - pts[i - 1].y
                if hypot(dx, dy) < step * 0.25 { continue }
                let dir = atan2(dy, dx)
                if !prevDir.isNaN {
                    let turn = wrap(dir - prevDir)
                    if !prevTurn.isNaN { changes.append(abs(turn - prevTurn)) }
                    prevTurn = turn
                }
                prevDir = dir
            }
        }
        if changes.count < 3 { return 0 }
        changes.sort()
        let median = changes[changes.count / 2]
        let k = min(max((SMOOTH_ZERO - median) / (SMOOTH_ZERO - SMOOTH_FULL), 0), 1)
        return Int((k * 100).rounded())
    }

    private static func wrap(_ a: Float) -> Float {
        var x = a
        while x > .pi { x -= 2 * .pi }
        while x < -.pi { x += 2 * .pi }
        return x
    }
}
