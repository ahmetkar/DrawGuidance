import Combine
import Foundation

/// v50 yetişkin alıştırmalarının en iyi puanları (Android `PracticePrefs`): yalnız cihazda. v51 ödüllü reklamla açılanlar.
@MainActor
final class PracticePrefs: ObservableObject {
    static let shared = PracticePrefs()
    private let d = UserDefaults.standard
    @Published private(set) var best: [String: Int]
    @Published private(set) var unlocked: Set<String>

    private init() {
        best = KidsPrefs.parseBest(d.string(forKey: "adult_practice.best"))
        unlocked = Set(d.stringArray(forKey: "adult_practice.unlocked_v51") ?? [])
    }

    @discardableResult
    func record(_ id: String, _ score: Int) -> Int {
        let b = max(best[id] ?? 0, min(max(score, 0), 100))
        best[id] = b
        d.set(best.map { "\($0.key):\($0.value)" }.joined(separator: ","), forKey: "adult_practice.best")
        return b
    }

    func unlock(_ id: String) {
        unlocked.insert(id)
        d.set(Array(unlocked), forKey: "adult_practice.unlocked_v51")
    }
}

/// v51 yeni alıştırma yeni tuvalde açılırken aktarılan sıra (Android `PracticeHandoff`).
@MainActor
enum PracticeHandoff {
    /// v52 ana sayfadan gelindi: alıştırma listesi açılır.
    static let LIST = -1
    private static var pending: (String, Int)?
    static func put(_ canvasLocalId: String, _ index: Int) { pending = (canvasLocalId, index) }
    static func take(_ canvasLocalId: String) -> Int? {
        guard let p = pending, p.0 == canvasLocalId else { return nil }
        pending = nil
        return p.1
    }
}

/// Süren alıştırma: listedeki sırası, hedefi, kurulum sayacı, başlarken kâğıtta olan elemanlar; v51 süreli çizimde
/// ilk çizginin zamanı.
struct PracticeRun {
    let index: Int
    let target: PracticeTarget
    let seq: Int
    let baseline: Set<String>
    var startedAt: Int64? = nil
    var level: PracticeLevel { target.level }
}

struct PracticeResult: Equatable {
    let index: Int; let score: Int; let stars: Int; let passed: Bool; let best: Int; let hints: Int; let last: Bool
    var detail: PracticeScore = .ZERO
    var record = false
    var timeUp = false
}

/// v50 yetişkin çizim alıştırmaları (Android `PracticeController`). v51: başka alıştırmaya geçerken önceki alıştırmada
/// çizim varsa tuval kaydedilir ve yeni alıştırma yeni tuvalde açılır ([newCanvas]); ödüllü reklamla açılan alıştırmalar;
/// ayrıntılı puan ve süreli çizim.
@MainActor
final class PracticeController: ObservableObject {
    private let store: EditorStore
    private let before: () -> Void
    private let after: () -> Void
    private let prefs = PracticePrefs.shared
    /// v51 alıştırma yeni tuvalde açılsın (nil: aynı tuvalde).
    var newCanvas: ((Int) -> Void)?
    /// v51 Premium (ödüllü reklam gerekmez).
    var adFree: () -> Bool = { !SubscriptionStore.shared.showsAds }

    @Published private(set) var run: PracticeRun?
    @Published private(set) var result: PracticeResult?
    @Published private(set) var live: Int?
    @Published private(set) var hints = 0
    /// v51 süreli çizimde kalan saniye.
    @Published private(set) var remaining: Int?
    /// v51 ödüllü reklam bekleyen alıştırma (sıra).
    @Published var locked: Int?
    /// Alıştırma listesi açık mı (menüdeki "Alıştırmalar" düğmesi açar).
    @Published var listOpen = false

    private var seq = 0
    private var bag = Set<AnyCancellable>()
    private var evalTask: Task<Void, Never>?
    private var timer: Task<Void, Never>?

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

    func isLocked(_ level: PracticeLevel) -> Bool { level.reward && !prefs.unlocked.contains(level.id) && !adFree() }

    private static func now() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    private func reevaluate(_ strokes: [Stroke]?, _ run: PracticeRun?, _ hints: Int) {
        evalTask?.cancel()
        guard let run, let strokes else { live = nil; return }
        if run.level.kind == .TIMED && run.startedAt == nil && !Self.mine(run, strokes).isEmpty { startTimer(run); return }
        let now = Self.now()
        evalTask = Task { [weak self] in
            let s = await Task.detached(priority: .userInitiated) { Self.score(run, strokes, hints, now: now).total }.value
            if Task.isCancelled { return }
            self?.live = s
        }
    }

    func unlockAndOpen(_ index: Int) {
        let list = levels()
        guard list.indices.contains(index) else { return }
        let level = list[index]
        prefs.unlock(level.id)
        locked = nil
        open(index)
    }

    @discardableResult
    func open(_ index: Int) -> Bool {
        let list = levels()
        guard list.indices.contains(index), let canvas = store.canvas, !store.inSandbox else { return false }
        if isLocked(list[index]) { locked = index; return false }
        if let previous = run, previous.index != index, let newCanvas, !Self.mine(previous, canvas.strokes).isEmpty {
            stop()
            newCanvas(index)
            return true
        }
        before()
        if let previous = run {
            let kept = canvas.strokes.filter { previous.baseline.contains($0.id) }
            if kept.count != canvas.strokes.count { store.select([]); store.commit(kept) }
        }
        after()
        let strokes = store.canvas?.strokes ?? []
        let target = PracticeLevels.target(list[index], canvas.width, canvas.height)
        stopTimer()
        hints = 0
        result = nil
        remaining = list[index].kind == .TIMED ? target.seconds : nil
        seq += 1
        run = PracticeRun(index: index, target: target, seq: seq, baseline: Set(strokes.map(\.id)))
        return true
    }

    func stop() { stopTimer(); result = nil; run = nil; hints = 0; remaining = nil }

    func hint() { if run?.level.kind == .FADE { hints += 1 } }

    @discardableResult
    func finish() -> PracticeResult? { finish(timeUp: false) }

    @discardableResult
    private func finish(timeUp: Bool) -> PracticeResult? {
        guard let r = run else { return nil }
        stopTimer()
        let detail = Self.score(r, store.canvas?.strokes ?? [], hints, now: Self.now())
        let previous = prefs.best[r.level.id] ?? 0
        let best = prefs.record(r.level.id, detail.total)
        let res = PracticeResult(index: r.index, score: detail.total, stars: PracticeLevels.stars(detail.total),
                                 passed: detail.total >= PracticeLevels.PASS, best: best, hints: hints, last: r.index == levels().count - 1,
                                 detail: detail, record: detail.total > previous && previous > 0, timeUp: timeUp)
        result = res
        return res
    }

    @discardableResult func next() -> Bool { guard let r = result, !r.last else { return false }; return open(r.index + 1) }
    @discardableResult func retry() -> Bool { guard let i = result?.index ?? run?.index else { return false }; return open(i) }
    func dismissResult() { result = nil }

    private func startTimer(_ r: PracticeRun) {
        var started = r
        started.startedAt = Self.now()
        run = started
        timer?.cancel()
        let limit = Int64(started.target.seconds) * 1000, seq = started.seq, t0 = started.startedAt!
        timer = Task { [weak self] in
            while !Task.isCancelled {
                let left = Int((limit - (Self.now() - t0)) / 1000)
                guard let self else { return }
                self.remaining = max(left, 0)
                if left <= 0 {
                    if self.run?.seq == seq && self.result == nil { self.finish(timeUp: true) }
                    return
                }
                try? await Task.sleep(nanoseconds: 250_000_000)
            }
        }
    }

    private func stopTimer() { timer?.cancel(); timer = nil }

    nonisolated static func mine(_ run: PracticeRun, _ strokes: [Stroke]) -> [Stroke] {
        strokes.filter { !run.baseline.contains($0.id) && !$0.hidden && $0.brushType != .SHAPE }
    }

    /// v51 ayrıntılı puan ([PracticeScore]).
    nonisolated static func score(_ run: PracticeRun, _ strokes: [Stroke], _ hints: Int, now: Int64) -> PracticeScore {
        let mine = mine(run, strokes)
        if !mine.contains(where: { $0.brushType != .ERASER }) {
            return PracticeScore(total: 0, outline: 0, accuracy: 0, coverage: 0, smoothness: 0, speed: nil, hints: hints)
        }
        let kind = run.level.kind
        let lines = KidsChallengeController.drawnLines(mine)
        let parts = ChallengeScore.outlineParts(lines, run.target.lines, near: kind.near, far: kind.far,
                                                alignedFactor: kind == .COPY ? 1 : PracticeScore.ALIGNED)
        let box = run.target.box
        let smooth = PracticeScore.smoothness(lines, diag: hypot(box.width, box.height))
        let speed: Int? = kind == .TIMED ? PracticeScore.speed(elapsedMs: now - (run.startedAt ?? now), limitMs: Int64(run.target.seconds) * 1000) : nil
        return PracticeScore.of(parts, smoothness: smooth, speed: speed, hints: hints)
    }

    /// Denemenin toplam puanı (eski arayüz; testler).
    nonisolated static func evaluate(_ run: PracticeRun, _ strokes: [Stroke], _ hints: Int) -> Int {
        score(run, strokes, hints, now: Int64(Date().timeIntervalSince1970 * 1000)).total
    }
}
