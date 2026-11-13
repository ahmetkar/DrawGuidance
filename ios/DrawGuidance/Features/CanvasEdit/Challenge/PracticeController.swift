import Combine
import Foundation

/// v50 yetişkin alıştırmalarının en iyi puanları (Android `PracticePrefs`): yalnız cihazda.
@MainActor
final class PracticePrefs: ObservableObject {
    static let shared = PracticePrefs()
    private let d = UserDefaults.standard
    @Published private(set) var best: [String: Int]

    private init() { best = KidsPrefs.parseBest(d.string(forKey: "adult_practice.best")) }

    @discardableResult
    func record(_ id: String, _ score: Int) -> Int {
        let b = max(best[id] ?? 0, min(max(score, 0), 100))
        best[id] = b
        d.set(best.map { "\($0.key):\($0.value)" }.joined(separator: ","), forKey: "adult_practice.best")
        return b
    }
}

/// Süren alıştırma: listedeki sırası, hedefi, kurulum sayacı ve başlarken kâğıtta olan elemanlar.
struct PracticeRun {
    let index: Int
    let target: PracticeTarget
    let seq: Int
    let baseline: Set<String>
    var level: PracticeLevel { target.level }
}

struct PracticeResult: Equatable { let index: Int; let score: Int; let stars: Int; let passed: Bool; let best: Int; let hints: Int; let last: Bool }

/// v50 yetişkin çizim alıştırmaları (Android `PracticeController`): kâğıttaki eski çizim silinmez, yalnız alıştırma
/// sırasında elle çizilenler puanlanır; yeni alıştırmaya geçerken önceki deneme tek geri alma adımında kaldırılır.
@MainActor
final class PracticeController: ObservableObject {
    private let store: EditorStore
    private let before: () -> Void
    private let after: () -> Void
    private let prefs = PracticePrefs.shared

    @Published private(set) var run: PracticeRun?
    @Published private(set) var result: PracticeResult?
    @Published private(set) var live: Int?
    @Published private(set) var hints = 0
    /// Alıştırma listesi açık mı (menüdeki "Alıştırmalar" düğmesi açar).
    @Published var listOpen = false

    private var seq = 0
    private var bag = Set<AnyCancellable>()
    private var evalTask: Task<Void, Never>?

    init(_ store: EditorStore, before: @escaping () -> Void = {}, after: @escaping () -> Void = {}) {
        self.store = store; self.before = before; self.after = after
        store.$canvas.combineLatest($run, $hints)
            .debounce(for: .milliseconds(120), scheduler: RunLoop.main)
            .sink { [weak self] canvas, run, hints in self?.reevaluate(canvas?.strokes, run, hints) }
            .store(in: &bag)
    }

    var active: Bool { run != nil }
    var best: [String: Int] { prefs.best }
    func levels() -> [PracticeLevel] { PracticeLevels.forCanvas(store.canvas?.kind) }

    private func reevaluate(_ strokes: [Stroke]?, _ run: PracticeRun?, _ hints: Int) {
        evalTask?.cancel()
        guard let run, let strokes else { live = nil; return }
        evalTask = Task { [weak self] in
            let s = await Task.detached(priority: .userInitiated) { Self.evaluate(run, strokes, hints) }.value
            if Task.isCancelled { return }
            self?.live = s
        }
    }

    @discardableResult
    func open(_ index: Int) -> Bool {
        let list = levels()
        guard list.indices.contains(index), let canvas = store.canvas, !store.inSandbox else { return false }
        before()
        if let previous = run {
            let kept = canvas.strokes.filter { previous.baseline.contains($0.id) }
            if kept.count != canvas.strokes.count { store.select([]); store.commit(kept) }
        }
        after()
        let strokes = store.canvas?.strokes ?? []
        let target = PracticeLevels.target(list[index], canvas.width, canvas.height)
        hints = 0
        result = nil
        seq += 1
        run = PracticeRun(index: index, target: target, seq: seq, baseline: Set(strokes.map(\.id)))
        return true
    }

    func stop() { result = nil; run = nil; hints = 0 }

    func hint() { if run?.level.kind == .FADE { hints += 1 } }

    @discardableResult
    func finish() -> PracticeResult? {
        guard let r = run else { return nil }
        let score = Self.evaluate(r, store.canvas?.strokes ?? [], hints)
        let best = prefs.record(r.level.id, score)
        let res = PracticeResult(index: r.index, score: score, stars: PracticeLevels.stars(score), passed: score >= PracticeLevels.PASS,
                                 best: best, hints: hints, last: r.index == levels().count - 1)
        result = res
        return res
    }

    @discardableResult func next() -> Bool { guard let r = result, !r.last else { return false }; return open(r.index + 1) }
    @discardableResult func retry() -> Bool { guard let i = result?.index ?? run?.index else { return false }; return open(i) }
    func dismissResult() { result = nil }

    /// Denemenin puanı: alıştırmadan sonra elle çizilen çizgilerin (hazır şekiller hariç) hedefe yakınlığı; ipuçları düşer.
    nonisolated static func evaluate(_ run: PracticeRun, _ strokes: [Stroke], _ hints: Int) -> Int {
        let mine = strokes.filter { !run.baseline.contains($0.id) && !$0.hidden && $0.brushType != .SHAPE }
        if !mine.contains(where: { $0.brushType != .ERASER }) { return 0 }
        let kind = run.level.kind
        let raw = ChallengeScore.outline(KidsChallengeController.drawnLines(mine), run.target.lines, near: kind.near, far: kind.far)
        return min(max(raw - hints * PracticeLevels.HINT_PENALTY, 0), 100)
    }
}
