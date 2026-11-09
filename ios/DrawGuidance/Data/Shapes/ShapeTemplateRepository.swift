import Foundation

/// v21 şekil veritabanının deposu (Android `ShapeTemplateRepository` + Room `shape_templates`). Katalog imzaları
/// açılışta arka planda hesaplanır; kabul/ret sayaçları ve kullanıcının öğrettiği örnekler (şablon başına en çok
/// `MAX_USER_PER_TEMPLATE`) Application Support'taki JSON dosyasında saklanır.
final class ShapeTemplateRepository: TemplateSource, @unchecked Sendable {
    static let MAX_USER_PER_TEMPLATE = 4
    private static let TAG = "ShapeTemplates"

    /// Saklanan öğrenme durumu.
    struct Stored: Codable {
        struct Count: Codable { var usage = 0; var rejects = 0 }
        struct Sample: Codable { let id: Int64; let key: String; let strokes: [[[Float]]] }
        var version = ShapeTemplates.VERSION
        var counts: [String: Count] = [:]
        var samples: [Sample] = []
    }

    private let file: URL
    private let lock = NSLock()
    private var stored = Stored()
    private var current: ShapeIndex?
    private var started = false
    /// Dizin hazır olunca (ana iş parçacığında) çağrılır.
    var onReady: ((ShapeIndex) -> Void)?

    init(directory: URL? = nil) {
        let dir = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        file = dir.appendingPathComponent("shape_templates.json")
    }

    var searchScope: ShapeScope? { nil }
    var canvasKind: CanvasKind? { nil }

    var index: ShapeIndex? {
        let idx = lock.withLock { current }
        if idx == nil { warmUp() }
        return idx
    }

    /// Dizini arka planda hazırlar (bir kez).
    func warmUp() {
        let first: Bool = lock.withLock { if started { return false }; started = true; return true }
        if !first { return }
        Task.detached(priority: .utility) { [self] in
            let idx = self.load()
            await MainActor.run { self.onReady?(idx) }
            idx.warmAdvanced()
        }
    }

    /// Dizin (gerekirse dosyadan okuyup kurar).
    @discardableResult
    func load() -> ShapeIndex {
        if let idx = lock.withLock({ current }) { return idx }
        var s = Stored()
        if let data = try? Data(contentsOf: file) {
            do { s = try JSONDecoder().decode(Stored.self, from: data) } catch { AppLog.e(Self.TAG, "öğrenme dosyası okunamadı", error) }
        }
        let idx = build(s)
        lock.withLock { stored = s; current = idx }
        return idx
    }

    private func build(_ s: Stored) -> ShapeIndex {
        let catalog = ShapeTemplates.catalog
        let metas = catalog.map { t -> TemplateMeta in
            let c = s.counts[t.key]
            return TemplateMeta(key: t.key, name: t.name, category: t.category, group: t.group, description: t.description, solid: t.solid,
                                usage: c?.usage ?? 0, rejects: c?.rejects ?? 0)
        }
        var entries = catalog.enumerated().map { IndexEntry(id: Int64($0.offset), templateKey: $0.element.key, signature: ShapeSignatures.of($0.element.strokes)) }
        let known = Set(catalog.map(\.key))
        for sample in s.samples where known.contains(sample.key) {
            entries.append(IndexEntry(id: sample.id, templateKey: sample.key, signature: ShapeSignatures.of(Self.points(sample.strokes), augment: false), user: true))
        }
        return ShapeIndex(metas, entries)
    }

    private static func points(_ raw: [[[Float]]]) -> [[P]] { raw.map { s in s.compactMap { $0.count >= 2 ? P($0[0], $0[1]) : nil } } }

    private func persist() {
        let snapshot = lock.withLock { stored }
        do { try JSONEncoder().encode(snapshot).write(to: file, options: .atomic) } catch { AppLog.e(Self.TAG, "öğrenme kaydedilemedi", error) }
    }

    func accepted(_ key: String, _ drawn: [[P]]) {
        lock.withLock { current }?.recordFeedback(key, accepted: true)
        Task.detached(priority: .utility) { [self] in
            var addedEntry: IndexEntry?
            var trimmed = false
            lock.withLock {
                stored.counts[key, default: Stored.Count()].usage += 1
            }
            if drawn.contains(where: { $0.count >= 2 }), lock.withLock({ current?.meta(key) }) != nil {
                let sig = ShapeSignatures.of(drawn, augment: false)
                let id = Int64(Date().timeIntervalSince1970 * 1000) + 1_000_000
                lock.withLock {
                    let mine = stored.samples.filter { $0.key == key }
                    if mine.count >= Self.MAX_USER_PER_TEMPLATE, let oldest = mine.min(by: { $0.id < $1.id }) {
                        stored.samples.removeAll { $0.id == oldest.id }
                        trimmed = true
                    }
                    stored.samples.append(Stored.Sample(id: id, key: key, strokes: drawn.map { $0.map { [$0.x, $0.y] } }))
                }
                addedEntry = IndexEntry(id: id, templateKey: key, signature: sig, user: true)
            }
            persist()
            guard let entry = addedEntry else { return }
            // v37 yalnız örnek eklendiyse dizin yeniden kurulmaz; eski örnek silindiyse baştan kurulur
            if trimmed {
                let idx = build(lock.withLock { stored })
                lock.withLock { current = idx }
            } else {
                lock.withLock { current = current?.plus(entry) }
            }
        }
    }

    func rejected(_ keys: [String], _ drawn: [[P]]) {
        if keys.isEmpty { return }
        if let idx = lock.withLock({ current }) { keys.forEach { idx.recordFeedback($0, accepted: false) } }
        lock.withLock { for k in keys { stored.counts[k, default: Stored.Count()].rejects += 1 } }
        Task.detached(priority: .utility) { [self] in persist() }
    }
}

/// Bir şablon kaynağını açık tuvalin moduna/türüne bağlar (Android'de düzenleyici görünüm modelinin sarmalayıcısı).
final class ScopedTemplateSource: TemplateSource {
    private let base: TemplateSource
    private let scope: () -> ShapeScope?
    private let kind: () -> CanvasKind?
    private let onAccepted: (String, [[P]]) -> Void
    private let onRejected: ([String], [[P]]) -> Void

    init(_ base: TemplateSource, scope: @escaping () -> ShapeScope?, kind: @escaping () -> CanvasKind?,
         onAccepted: @escaping (String, [[P]]) -> Void = { _, _ in }, onRejected: @escaping ([String], [[P]]) -> Void = { _, _ in }) {
        self.base = base; self.scope = scope; self.kind = kind; self.onAccepted = onAccepted; self.onRejected = onRejected
    }

    var index: ShapeIndex? { base.index }
    var searchScope: ShapeScope? { scope() }
    var canvasKind: CanvasKind? { kind() }
    func accepted(_ key: String, _ drawn: [[P]]) { base.accepted(key, drawn); onAccepted(key, drawn) }
    func acceptedPart(_ key: String, _ drawn: [[P]]) { base.accepted(key, []); onAccepted(key, drawn) }
    func rejected(_ keys: [String], _ drawn: [[P]]) { base.rejected(keys, drawn); onRejected(keys, drawn) }
}
