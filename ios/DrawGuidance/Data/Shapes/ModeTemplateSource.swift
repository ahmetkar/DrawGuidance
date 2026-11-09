import Foundation

extension CanvasKind {
    /// v29 tuval türünün tahmin modu.
    var shapeMode: ShapeMode {
        switch self {
        case .SIMPLE_ART, .ART: return .ART
        case .KIDS: return .KIDS
        case .SIMPLE_NOTES, .NOTES: return .NOTES
        case .COMIC: return .COMIC
        }
    }
}

/// v29 açık tuvalin moduna özel şekil kaynağı (Android `ModeTemplateSource`): aramalar modun kapsamında; kabul/ret
/// hem şekil veritabanına hem yalnız bu modun sinir ağına gider.
final class ModeTemplateSource: TemplateSource, @unchecked Sendable {
    private let base: ShapeTemplateRepository
    private let models: ShapeModeModels
    private let mode: () -> ShapeMode?
    private let kind: () -> CanvasKind?
    private let lock = NSLock()
    private var warming = false
    private var narrowed: (keys: Set<String>, scope: ShapeScope)?

    init(_ base: ShapeTemplateRepository, _ models: ShapeModeModels, mode: @escaping () -> ShapeMode?, kind: @escaping () -> CanvasKind? = { nil }) {
        self.base = base; self.models = models; self.mode = mode; self.kind = kind
    }

    var index: ShapeIndex? { base.index }

    var searchScope: ShapeScope? {
        guard let m = mode(), let idx = base.index else { return nil }
        if !models.isReady(m) { warm(m, idx) }
        let s = models.scopeIfReady(m, idx)
        return kind() == .SIMPLE_ART ? simpleArt(s, idx) : s
    }

    var canvasKind: CanvasKind? { kind() }

    /// v34 basit resim: parça/teknik şablonlar aranmaz.
    private func simpleArt(_ s: ShapeScope, _ idx: ShapeIndex) -> ShapeScope {
        if let n = lock.withLock({ narrowed }), n.keys == s.keys, n.scope.net === s.net { return n.scope }
        let allowed = Set(idx.metas.filter { ModeCatalog.allowsSimpleArt($0.key, $0.category) }.map(\.key))
        let scope = ShapeScope(s.mode, s.keys.filter { allowed.contains($0) }, s.net, s.netWeight, s.usage, s.rejects)
        lock.withLock { narrowed = (s.keys, scope) }
        return scope
    }

    /// Modun modeli arka planda hazırlanır.
    func warm() {
        guard let m = mode() else { return }
        Task.detached(priority: .utility) { [base, models] in
            let idx = base.load()
            _ = models.model(m, idx)
        }
    }

    private func warm(_ m: ShapeMode, _ idx: ShapeIndex) {
        let go: Bool = lock.withLock { if warming { return false }; warming = true; return true }
        if !go { return }
        Task.detached(priority: .utility) { [self] in
            _ = models.model(m, idx)
            lock.withLock { warming = false }
        }
    }

    func accepted(_ key: String, _ drawn: [[P]]) {
        base.accepted(key, drawn)
        train { m, idx, models in models.accepted(m, idx, key, drawn) }
    }

    func acceptedPart(_ key: String, _ drawn: [[P]]) {
        base.accepted(key, [])
        train { m, idx, models in models.accepted(m, idx, key, drawn) }
    }

    func rejected(_ keys: [String], _ drawn: [[P]]) {
        base.rejected(keys, [])
        if !keys.isEmpty { train { m, idx, models in models.rejected(m, idx, keys, drawn) } }
    }

    private func train(_ block: @escaping @Sendable (ShapeMode, ShapeIndex, ShapeModeModels) -> Void) {
        guard let m = mode(), let idx = base.index else { return }
        let models = self.models
        Task.detached(priority: .utility) { block(m, idx, models) }
    }
}

/// Kabul/retleri koşula bağlı ileten kaynak (v32 eğitimde öğrenme yok).
final class GatedTemplateSource: TemplateSource {
    private let base: TemplateSource
    private let learn: () -> Bool
    private let kind: () -> CanvasKind?
    init(_ base: TemplateSource, learn: @escaping () -> Bool, kind: @escaping () -> CanvasKind?) { self.base = base; self.learn = learn; self.kind = kind }
    var index: ShapeIndex? { base.index }
    var searchScope: ShapeScope? { base.searchScope }
    var canvasKind: CanvasKind? { kind() }
    func accepted(_ key: String, _ drawn: [[P]]) { if learn() { base.accepted(key, drawn) } }
    func acceptedPart(_ key: String, _ drawn: [[P]]) { if learn() { base.acceptedPart(key, drawn) } }
    func rejected(_ keys: [String], _ drawn: [[P]]) { if learn() { base.rejected(keys, drawn) } }
}
