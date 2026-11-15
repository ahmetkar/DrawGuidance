import Foundation

/// v52 not almada tablo (Android `TableController`): "Tablo ekle" penceresi ve seçili tablonun düzenleme penceresi
/// (hücre yazıları; her satır, her sütun ve hepsi için ayarlar). Tablo tek şekil elemanıdır ([TableShape]).
@MainActor
final class TableController: ObservableObject {
    private let store: EditorStore
    private unowned let host: EditorHost

    @Published var createOpen = false
    /// Düzenleme penceresi açık tablo (nil: kapalı).
    @Published var editing: String?

    init(_ store: EditorStore, _ host: EditorHost) { self.store = store; self.host = host }

    func openCreate() { createOpen = true }
    func closeCreate() { createOpen = false }

    /// Yeni tablo [center] çevresine (yoksa sayfanın ortasına) eklenir ve seçilir.
    @discardableResult
    func create(_ params: [String: String], center: P? = nil, ink: String = "#263238") -> String? {
        guard let current = store.canvas else { return nil }
        let w = Float(current.width), h = Float(current.height)
        let nr = TableShape.rows(params), nc = TableShape.cols(params)
        let colW = min(w * 0.86 / Float(nc), w * 0.24)
        var rowH = min(max(w * 0.06, 36), 120)
        if rowH * Float(nr) > h * 0.8 { rowH = h * 0.8 / Float(nr) }
        let tw = colW * Float(nc), th = rowH * Float(nr)
        let cx = min(max(center?.x ?? w / 2, tw / 2), max(tw / 2, w - tw / 2))
        let cy = min(max(center?.y ?? h / 2, th / 2), max(th / 2, h - th / 2))
        let id = UUID().uuidString
        let style = ShapeStyle(strokeColorHex: ink, strokeWidth: max(2, w / 600))
        guard let s = ShapeFactory.build(TableShape.KIND, P(cx - tw / 2, cy - th / 2), P(cx + tw / 2, cy + th / 2), style: style, id: id,
                                         order: current.strokes.count, params: params) else { return nil }
        store.commit(current.strokes + [s])
        createOpen = false
        host.inserted([id])
        return id
    }

    func edit(_ id: String) { editing = id }
    func closeEdit() { editing = nil }

    var table: Stroke? {
        guard let id = editing else { return nil }
        return store.canvas?.strokes.first { $0.id == id && TableShape.isTable($0.shape?.kind) }
    }

    var params: [String: String] { table?.shape?.params ?? [:] }

    /// Değerleri değiştirip tabloyu yeniden kurar; satır/sütun eklenince kutu büyür (hücreler aynı boyda kalır).
    func update(coalesce: String? = nil, _ block: ([String: String]) -> [String: String]) {
        guard let current = store.canvas, let s = table, let spec = s.shape else { return }
        let old = spec.params ?? [:]
        let next = block(old)
        if next == old { return }
        guard let box = ShapeFactory.box(s) else { return }
        let sx = TableShape.weightSum(next, isRow: false) / max(0.01, TableShape.weightSum(old, isRow: false))
        let sy = TableShape.weightSum(next, isRow: true) / max(0.01, TableShape.weightSum(old, isRow: true))
        let end = P(box.left + box.width * sx, box.top + box.height * sy)
        guard var built = ShapeFactory.build(TableShape.KIND, box.topLeft, end, style: spec.style, id: s.id, order: s.order,
                                             opacity: s.opacity, params: next) else { return }
        built.groupId = s.groupId; built.clip = s.clip; built.layerId = s.layerId
        store.commit(current.strokes.map { $0.id == s.id ? built : $0 }, coalesce: coalesce ?? "table-\(s.id)-\(UUID().uuidString)")
    }

    func onContentReplaced() { if editing != nil && table == nil { editing = nil } }
}
