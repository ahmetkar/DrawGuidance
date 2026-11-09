import Combine
import Foundation

/// v38 süren seviye: sıra numarası, hedefi ve kurulum sayacı (her kurulumda artar: maskot baştan konuşur).
struct ChallengeRun {
    let index: Int
    let target: ChallengeTarget
    let seq: Int
    var level: ChallengeLevel { target.level }
}

/// Boyanacak yer: tuvaldeki yeri, beklenen renk ve doğru renge boyandı mı.
struct PaintMark: Equatable { let at: P; let color: String; let done: Bool }

/// Çizimin o anki değerlendirmesi: puan, (yerleştir ve boyada) şekil kondu mu ve boyanacak yerler.
struct ChallengeEval: Equatable { let score: Int; let drawn: Bool; let marks: [PaintMark] }

/// Bitti'ye dokununca değerlendirme.
struct ChallengeResult: Equatable { let index: Int; let score: Int; let stars: Int; let passed: Bool; let best: Int; let last: Bool }

/// v40 çocuk tuvaline girişlerin sayacı (Android `KidsEntryRepository`): ilk 5 girişte maskot görevi önerir.
enum KidsEntries {
    static let SUGGEST_ENTRIES = 5
    static func shouldSuggest(_ entry: Int, _ challengeActive: Bool) -> Bool { (1...SUGGEST_ENTRIES).contains(entry) && !challengeActive }
    static func recordEntry() -> Int {
        let n = UserDefaults.standard.integer(forKey: "kids.entries") + 1
        UserDefaults.standard.set(n, forKey: "kids.entries")
        return n
    }
    /// UI testlerinde öneri balonu çıkmaz.
    static var enabled: Bool { !ProcessInfo.processInfo.arguments.contains("-uiTesting") }
}

/// v38 çocuk modunda çizim görevi (Android `KidsChallengeController`): seviye kurulur (tuval tek geri alma adımında
/// temizlenir, boyama görevinde şekil konur), çizim arka planda değerlendirilir, Bitti'de puan ve en iyi puan yazılır.
@MainActor
final class KidsChallengeController: ObservableObject {
    private let store: EditorStore
    private let before: () -> Void
    private let after: () -> Void
    private let premium: @MainActor () -> Bool
    private let prefs = KidsPrefs.shared

    @Published private(set) var run: ChallengeRun?
    @Published private(set) var active = false
    @Published private(set) var result: ChallengeResult?
    @Published private(set) var live: ChallengeEval?
    @Published private(set) var suggest = false

    private var seq = 0
    private var entryCounted = false
    private var bag = Set<AnyCancellable>()
    private var evalTask: Task<Void, Never>?

    private var levels: [ChallengeLevel] { ChallengeLevels.all }

    init(_ store: EditorStore, before: @escaping () -> Void = {}, after: @escaping () -> Void = {},
         premium: (@MainActor () -> Bool)? = nil) {
        self.store = store; self.before = before; self.after = after
        self.premium = premium ?? { SubscriptionStore.shared.has(FeatureKeys.KIDS_CHALLENGE_ADVANCED) }
        store.$canvas.combineLatest($run)
            .debounce(for: .milliseconds(120), scheduler: RunLoop.main)
            .sink { [weak self] canvas, run in self?.reevaluate(canvas?.strokes, run) }
            .store(in: &bag)
    }

    private func reevaluate(_ strokes: [Stroke]?, _ run: ChallengeRun?) {
        evalTask?.cancel()
        guard let run, let strokes else { live = nil; return }
        let target = run.target
        evalTask = Task { [weak self] in
            let e = await Task.detached(priority: .userInitiated) { Self.evaluate(target, strokes) }.value
            if Task.isCancelled { return }
            self?.live = e
        }
    }

    var best: [String: Int] { prefs.challengeBest }
    func bestOf(_ index: Int) -> Int? { index < levels.count ? best[levels[index].id] : nil }
    func passed(_ index: Int) -> Bool { (bestOf(index) ?? -1) >= ChallengeScore.PASS }
    func unlocked(_ index: Int) -> Bool { levels.indices.contains(index) && (index == 0 || passed(index) || passed(index - 1)) }
    func premiumLocked(_ index: Int) -> Bool { ChallengeLevels.isPremium(index) && !premium() }
    func reachable(_ index: Int) -> Bool { unlocked(index) && !premiumLocked(index) }
    func nextIndex() -> Int { levels.indices.first { !passed($0) } ?? levels.count - 1 }

    @discardableResult func start() -> Bool { open(nextIndex()) }

    @discardableResult
    func open(_ index: Int) -> Bool {
        if !reachable(index) { return false }
        guard let canvas = store.canvas, !store.inSandbox else { return false }
        before()
        let level = levels[index]
        let target = ChallengeLevels.target(level, canvas.width, canvas.height)
        let setup: [Stroke] = level.kind == .PAINT ? [ChallengeLevels.build(level, target.box, id: UUID().uuidString)].compactMap { $0 } : []
        guard let current = store.canvas else { return false }
        store.select([])
        if !current.strokes.isEmpty || !setup.isEmpty { store.commit(setup) }
        after()
        result = nil
        seq += 1
        run = ChallengeRun(index: index, target: target, seq: seq)
        active = true
        suggest = false
        return true
    }

    func stop() { result = nil; run = nil; active = false }

    @discardableResult
    func finish() -> ChallengeResult? {
        guard let r = run else { return nil }
        let e = Self.evaluate(r.target, store.canvas?.strokes ?? [])
        let best = prefs.recordChallenge(r.level.id, e.score)
        let res = ChallengeResult(index: r.index, score: e.score, stars: ChallengeScore.stars(e.score), passed: ChallengeScore.passed(e.score),
                                  best: best, last: r.index == levels.count - 1)
        result = res
        return res
    }

    @discardableResult
    func next() -> Bool {
        guard let r = result, r.passed, !r.last else { return false }
        return open(r.index + 1)
    }

    func nextNeedsPremium() -> Bool {
        guard let r = result else { return false }
        return r.passed && !r.last && premiumLocked(r.index + 1)
    }

    func onKidsEntry() {
        if entryCounted { return }
        entryCounted = true
        guard KidsEntries.enabled else { return }
        let n = KidsEntries.recordEntry()
        if KidsEntries.shouldSuggest(n, active) { suggest = true }
    }

    enum SuggestOutcome { case STARTED, NEEDS_PREMIUM, FAILED }

    func acceptSuggestion() -> SuggestOutcome {
        suggest = false
        let next = nextIndex()
        if premiumLocked(next) { return .NEEDS_PREMIUM }
        return open(next) ? .STARTED : .FAILED
    }

    func cancelSuggestion() { suggest = false }

    @discardableResult
    func retry() -> Bool {
        guard let index = result?.index ?? run?.index else { return false }
        return open(index)
    }

    func dismissResult() { result = nil }

    // ------------------------------------------------------------------ değerlendirme

    nonisolated static func evaluate(_ target: ChallengeTarget, _ strokes: [Stroke]) -> ChallengeEval {
        let level = target.level
        let visible = strokes.filter { !$0.hidden }
        let outline = level.kind == .PAINT ? 0 : ChallengeScore.outline(drawnLines(visible), target.lines)
        if level.kind == .TRACE { return ChallengeEval(score: outline, drawn: !visible.isEmpty, marks: []) }
        let candidates = visible.filter { $0.shape?.kind == level.shape }
        var bestScore = -1
        var bestMarks: [PaintMark] = []
        for shape in candidates {
            let paths = ShapeFactory.subPaths(shape)
            guard let b = ChallengeLevels.outlineBounds(paths) else { continue }
            let checks: [(P, String, String?)] = target.probes.map { pr in
                let at = P(b.left + pr.u * b.width, b.top + pr.v * b.height)
                return (at, pr.color, colorAt(shape, paths, at))
            }
            let score = ChallengeScore.paint(checks.map { ($0.1, $0.2) })
            if score > bestScore {
                bestScore = score
                bestMarks = checks.map { PaintMark(at: $0.0, color: $0.1, done: ChallengeScore.paintCredit($0.1, $0.2) >= 0.99) }
            }
        }
        let paint = max(bestScore, 0)
        let score = level.kind == .PAINT ? paint : ChallengeScore.combine(outline, paint)
        return ChallengeEval(score: score, drawn: !candidates.isEmpty, marks: bestMarks)
    }

    /// Şeklin noktada görünen dolgu rengi (üstteki parça öndedir; dolgusuz parça arkayı örtmez).
    nonisolated static func colorAt(_ shape: Stroke, _ paths: [EditablePath], _ at: P) -> String? {
        guard let spec = shape.shape else { return nil }
        var color: String?
        for p in paths {
            if !p.closed || p.points.count < 3 || p.label != nil || p.tag == VectorEdit.VNODE { continue }
            if !RegionFill.contains(p.points, at) { continue }
            if let f = ShapeRenderer.resolveFill(p.fillColorHex, spec.style), !f.isEmpty { color = f }
        }
        return color
    }

    /// Kâğıttaki çizgiler: fırça izleri (sonraki silgiyle silinen yerleri çıkarılır) ve şekillerin çizgileri.
    nonisolated static func drawnLines(_ strokes: [Stroke]) -> [[P]] {
        var out: [[P]] = []
        let erasers: [(Int, Rub)] = strokes.enumerated().compactMap { i, s in
            s.brushType == .ERASER && !s.points.isEmpty ? (i, Rub(s)) : nil
        }
        for (i, s) in strokes.enumerated() {
            if s.brushType == .SHAPE {
                for p in ShapeFactory.subPaths(s) {
                    if p.label != nil || KidsBrush.isPaintLayer(p.tag) || p.tag == VectorEdit.VNODE || p.points.count < 2 { continue }
                    if (p.widthScale ?? 1) <= 0 { continue }
                    out.append(p.closed ? p.points + [p.points[0]] : p.points)
                }
            } else if s.brushType.isErasable && s.space == nil {
                let pts = s.points.map { P($0.x, $0.y) }
                let later = erasers.filter { $0.0 > i }.map(\.1)
                if later.isEmpty { out.append(pts); continue }
                var piece: [P] = []
                for p in pts {
                    if later.contains(where: { $0.covers(p) }) {
                        if !piece.isEmpty { out.append(piece) }
                        piece = []
                    } else { piece.append(p) }
                }
                if !piece.isEmpty { out.append(piece) }
            }
        }
        return out
    }

    private struct Rub {
        let r: Float
        let pts: [P]
        let minX: Float, maxX: Float, minY: Float, maxY: Float
        init(_ e: Stroke) {
            r = e.sizePx / 2
            pts = e.points.map { P($0.x, $0.y) }
            minX = pts.map(\.x).min()! - r; maxX = pts.map(\.x).max()! + r
            minY = pts.map(\.y).min()! - r; maxY = pts.map(\.y).max()! + r
        }
        func covers(_ p: P) -> Bool {
            if p.x < minX || p.x > maxX || p.y < minY || p.y > maxY { return false }
            if pts.count == 1 { return hypot(pts[0].x - p.x, pts[0].y - p.y) <= r }
            for k in 1..<pts.count where ChallengeScore.segment(p, pts[k - 1], pts[k]) <= r { return true }
            return false
        }
    }
}
