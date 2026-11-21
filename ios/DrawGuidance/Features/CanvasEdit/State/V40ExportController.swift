import Foundation

/// v40 sayfa ayarları (sayfa numarası, üst ve alt bilgi; tuval başına kaydedilir) ve çizgi roman arşivi (.cbz)
/// dışa aktarma (Android `V40ExportController`).
@MainActor
final class V40ExportController: ObservableObject {
    private let store: EditorStore
    private let images: ImagesRepository?
    private weak var repository: CanvasPersistence?

    init(_ store: EditorStore, images: ImagesRepository?, repository: CanvasPersistence?) {
        self.store = store; self.images = images; self.repository = repository
    }

    // MARK: Sayfa ayarları

    @Published private(set) var pageSettingsOpen = false

    func openPageSettings() { pageSettingsOpen = true }
    func closePageSettings() { pageSettingsOpen = false }

    /// Ayarları tuvale uygular (hemen görünür) ve kaydeder.
    func setPageSettings(_ settings: PageSettings) {
        guard let current = store.canvas, current.pageSettings != settings else { return }
        store.updateCanvas { c in var n = c; n.pageSettings = settings; return n }
        repository?.savePageSettings(store.canvasLocalId, settings)
    }

    // MARK: CBZ

    @Published private(set) var cbzOptions: CbzOptions?
    @Published private(set) var cbz: PdfExportState = .idle

    func openCbz() {
        guard let current = store.canvas, cbz != .exporting else { return }
        cbzOptions = CbzOptions(fileName: ImageExporter.defaultFileName(current.name), toPage: max(current.pageCount, 1))
    }

    func closeCbz() { cbzOptions = nil }

    func exportCbz(_ options: CbzOptions) {
        guard let current = store.canvas, let images else { return }
        cbzOptions = nil
        if cbz == .exporting { return }
        cbz = .exporting
        Task {
            await Task.yield()
            do { cbz = .done(try images.exportCbz(current, options)) } catch {
                AppLog.e("CbzExport", "CBZ kaydedilemedi", error)
                cbz = .failed
            }
        }
    }

    func dismissCbz() { cbz = .idle }

    // MARK: v56 aşama 9 SVG / PSD / DXF

    enum DocState: Equatable {
        case idle, exporting, failed
        case done(ExportedFile, Int)
        static func == (a: DocState, b: DocState) -> Bool {
            switch (a, b) {
            case (.idle, .idle), (.exporting, .exporting), (.failed, .failed): return true
            case let (.done(f1, s1), .done(f2, s2)): return f1.url == f2.url && s1 == s2
            default: return false
            }
        }
    }

    @Published private(set) var doc: DocState = .idle

    func exportDoc(_ format: DocFormat, _ fileName: String) {
        guard let current = store.canvas, let images, doc != .exporting else { return }
        doc = .exporting
        Task {
            await Task.yield()
            do { let r = try images.exportDoc(current, format, fileName); doc = .done(r.file, r.skipped) } catch {
                AppLog.e("DocExport", "\(format.rawValue) kaydedilemedi", error)
                doc = .failed
            }
        }
    }

    func dismissDoc() { doc = .idle }

    func defaultDocName() -> String { store.canvas.map { ImageExporter.defaultFileName($0.name) } ?? "" }

    var documentLocation: String { images?.pdfLocationLabel ?? ImageExporter.FOLDER }
}
