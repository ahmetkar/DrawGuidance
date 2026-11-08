import Foundation

/// Tuvalleri cihazda JSON olarak saklar (Room karşılığının ilk sürümü).
@MainActor
final class CanvasLibrary: ObservableObject {
    @Published private(set) var canvases: [CanvasDocument] = []
    private let url: URL

    init(directory: URL? = nil) {
        let dir = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        url = dir.appendingPathComponent("canvases.json")
        load()
    }

    func create(kind: CanvasKind, name: String) -> CanvasDocument {
        let doc = CanvasDocument(name: name, kind: kind)
        canvases.insert(doc, at: 0)
        save()
        return doc
    }

    func update(_ doc: CanvasDocument) {
        var d = doc
        d.updatedAt = Date()
        if let i = canvases.firstIndex(where: { $0.id == doc.id }) { canvases[i] = d } else { canvases.insert(d, at: 0) }
        save()
    }

    func delete(_ id: UUID) {
        canvases.removeAll { $0.id == id }
        save()
    }

    private func load() {
        guard let data = try? Data(contentsOf: url),
              let list = try? JSONDecoder().decode([CanvasDocument].self, from: data) else { return }
        canvases = list
    }

    private func save() {
        if let data = try? JSONEncoder().encode(canvases) { try? data.write(to: url, options: .atomic) }
    }
}
