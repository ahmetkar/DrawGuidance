import Foundation

/// Cihaz içi el yazısı tanıyıcı (Android `Handwriting` ile birebir): rakam, harf (Türkçe dahil), matematik simgeleri;
/// v37 yazı ağıyla Kiril, Arapça (ayrık), Japonca ve Korece.
enum Handwriting {
    static let MAX_STROKES = 40
    static let MAX_STROKES_CJK = 120
    static let MIN_CONFIDENCE: Float = 0.35
    static let GATE_MIN: Float = 0.5

    static func recognize(_ strokes: [InkStroke], mode: InkMode = .MIXED, lexicon: Bool = true, language: String? = nil) -> Recognition? {
        guard let r = analyze(strokes, mode: mode, lexicon: lexicon, language: language) else { return nil }
        return r.confidence >= MIN_CONFIDENCE ? r : nil
    }

    static func looksLikeWriting(_ strokes: [InkStroke], language: String? = nil) -> Bool {
        let polys = Prep.polys(strokes)
        if polys.isEmpty { return false }
        let ps = polys.map(\.1)
        if polys.count <= MAX_STROKES && Prep.looksLikeWriting(ps) { return true }
        if polys.count > MAX_STROKES_CJK { return false }
        guard let net = HandwritingNet.active else { return false }
        let hint = InkScript.fromHint(language)
        let script = (hint == .JAPANESE || hint == .KOREAN) ? hint : ScriptDecoder.detect(ps, net, hint)?.0
        guard let script, script == .JAPANESE || script == .KOREAN else { return false }
        return Prep.looksLikeWriting(ps, script)
    }

    static func writingScore(_ strokes: [InkStroke]) -> Float? {
        let ps = Prep.polys(strokes).map(\.1)
        if ps.isEmpty || ps.count > MAX_STROKES_CJK { return nil }
        return HandwritingNet.active?.writingProb(ps)
    }

    static func detectScript(_ strokes: [InkStroke], language: String? = nil) -> InkScript? {
        let ps = Prep.polys(strokes).map(\.1)
        if ps.count > MAX_STROKES_CJK { return nil }
        guard let net = HandwritingNet.active else { return nil }
        return ScriptDecoder.detect(ps, net, InkScript.fromHint(language))?.0
    }

    static func warmUp() {
        ShapeModel.warmUp()
        _ = HandwritingNet.active
    }

    static func analyze(_ strokes: [InkStroke], mode: InkMode = .MIXED, lexicon: Bool = true, language: String? = nil) -> Recognition? {
        let polys = Prep.polys(strokes)
        if polys.isEmpty || polys.count > MAX_STROKES_CJK { return nil }
        let ps = polys.map(\.1)
        let net = HandwritingNet.active
        var script = InkScript.LATIN
        if mode != .DIGITS, let net { script = ScriptDecoder.detect(ps, net, InkScript.fromHint(language))?.0 ?? .LATIN }
        if script == .LATIN || net == nil {
            if polys.count > MAX_STROKES { return nil }
            if !Prep.looksLikeWriting(ps) { return nil }
            return InkDecoder(polys, mode, lexicon).decode()
        }
        if !Prep.looksLikeWriting(ps, script) { return nil }
        return ScriptDecoder(polys, script, net!).decode()
    }
}
