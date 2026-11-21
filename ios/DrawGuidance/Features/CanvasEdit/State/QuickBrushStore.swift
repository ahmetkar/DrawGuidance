import Foundation

/// v56 hızlı fırça seçicinin durumu (planın 2. bölümü): mod başına son kullanılanlar ve favoriler, şeridin açıklığı,
/// düğmenin köşesi. Kayıt `UserDefaults` içinde `brushQuick.<mod>` ve `brushQuickCorner.<mod>` anahtarlarında.
@MainActor
final class QuickBrushStore: ObservableObject {
    @Published private(set) var lists = QuickBrushLists()
    @Published var open = false
    @Published private(set) var corner: QuickCorner = .TOP_START
    private var kind: CanvasKind?
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) { self.defaults = defaults }

    func bind(_ kind: CanvasKind) {
        if self.kind == kind { return }
        self.kind = kind
        lists = QuickBrushLists.decode(defaults.string(forKey: "brushQuick.\(kind.rawValue)"))
        corner = defaults.string(forKey: "brushQuickCorner.\(kind.rawValue)").flatMap(QuickCorner.init(rawValue:)) ?? .TOP_START
    }

    private func store(_ next: QuickBrushLists) {
        lists = next
        if let kind { defaults.set(next.encode(), forKey: "brushQuick.\(kind.rawValue)") }
    }

    func used(_ type: BrushType) {
        let next = lists.used(type)
        if next != lists { store(next) }
    }

    @discardableResult
    func toggleFavorite(_ type: BrushType) -> Bool {
        let (next, ok) = lists.toggleFavorite(type)
        if ok { store(next) }
        return ok
    }

    func toggle() { open.toggle() }

    func setCorner(_ c: QuickCorner) {
        corner = c
        if let kind { defaults.set(c.rawValue, forKey: "brushQuickCorner.\(kind.rawValue)") }
    }
}
