import Foundation
import CoreGraphics

/// Kaydedilenlerin listesi: Uygulama Desteği'nde tek JSON dosyası (Android Room `saved_images` tablosu).
@MainActor
final class SavedImagesStore: ObservableObject {
    @Published private(set) var images: [SavedImage] = []
    private let file: URL

    init(file: URL? = nil) {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        self.file = file ?? dir.appendingPathComponent("saved_images.json")
        if let d = try? Data(contentsOf: self.file), let list = try? JSONDecoder().decode([SavedImage].self, from: d) { images = list }
    }

    private func persist() {
        try? FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        if let d = try? JSONEncoder().encode(images) { try? d.write(to: file, options: .atomic) }
    }

    @discardableResult
    func insert(_ image: SavedImage) -> SavedImage {
        var e = image
        e.id = (images.map(\.id).max() ?? 0) + 1
        images.insert(e, at: 0)
        persist()
        return e
    }

    func get(_ id: Int64) -> SavedImage? { images.first { $0.id == id } }

    func update(_ id: Int64, _ change: (inout SavedImage) -> Void) {
        guard let i = images.firstIndex(where: { $0.id == id }) else { return }
        change(&images[i])
        persist()
    }

    func delete(_ id: Int64) { images.removeAll { $0.id == id }; persist() }
    func moveTo(_ ids: [Int64], _ folderId: String?) {
        for i in images.indices where ids.contains(images[i].id) { images[i].folderId = folderId }
        persist()
    }
    func inFolders(_ folderIds: [String]) -> [SavedImage] { images.filter { $0.folderId.map(folderIds.contains) ?? false } }
}

/// Tuvalleri dışa aktarıp Kaydedilenler'e yazar (Android `ImagesRepository`).
@MainActor
final class ImagesRepository {
    let store: SavedImagesStore
    let exporter: ImageExporter
    let assets: CanvasAssetStore
    /// Not tuvalinin tüm sayfaları için klasör (kimlik, ad); nil: klasörsüz.
    var folderForCanvas: ((CanvasUi) -> (id: String, name: String)?)? = nil

    init(store: SavedImagesStore, exporter: ImageExporter = ImageExporter(), assets: CanvasAssetStore = .shared) {
        self.store = store; self.exporter = exporter; self.assets = assets
    }

    func defaultOptions(_ canvas: CanvasUi) -> ExportOptions { exporter.defaultOptions(canvas) }
    func outputSize(_ canvas: CanvasUi, _ scale: Float) -> (Int, Int) { exporter.outputSize(canvas.width, canvas.height, scale) }
    var locationLabel: String { ImageExporter.FOLDER }
    var pdfLocationLabel: String { ImageExporter.FOLDER }

    func defaultPdfOptions(_ canvas: CanvasUi) -> PdfOptions {
        PdfOptions(fileName: ImageExporter.defaultFileName(canvas.name), includeGuides: GuideRenderer.exports(canvas.guide))
    }

    func url(_ image: SavedImage) -> URL {
        if let bm = image.bookmark {
            var stale = false
            if let u = try? URL(resolvingBookmarkData: bm, bookmarkDataIsStale: &stale) { return u.appendingPathComponent(image.path) }
        }
        return exporter.root.appendingPathComponent(image.path)
    }

    private func relative(_ url: URL, _ target: SaveTarget) -> (String, Data?) {
        switch target {
        case .defaultLocation:
            let root = exporter.root.standardizedFileURL.path
            let p = url.standardizedFileURL.path
            return (p.hasPrefix(root + "/") ? String(p.dropFirst(root.count + 1)) : url.lastPathComponent, nil)
        case let .folder(bookmark, _):
            return (url.lastPathComponent, bookmark)
        }
    }

    private static func now() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    func saveCanvasAsImage(_ canvas: CanvasUi) throws -> SavedImage {
        guard let first = try saveCanvasAsImage(canvas, defaultOptions(canvas)).first else { throw ExportError.failed }
        return first
    }

    func saveCanvasAsImage(_ canvas: CanvasUi, _ options: ExportOptions) throws -> [SavedImage] {
        assets.ensureLoaded(canvas)
        let folder = options.allPages && canvas.pageCount > 1 ? folderForCanvas?(canvas) : nil
        let exported = try exporter.export(canvas, options, folder: folder?.name)
        let multi = exported.count > 1
        let target = multi ? SaveTarget.defaultLocation : options.target
        return exported.enumerated().map { i, image in
            let (path, bm) = relative(image.url, target)
            return store.insert(SavedImage(id: 0, canvasLocalId: canvas.localId, title: multi ? "\(canvas.name) (\(i + 1))" : canvas.name,
                                           path: path, bookmark: bm, width: image.width, height: image.height, createdAt: Self.now(),
                                           location: image.location, sizeBytes: image.sizeBytes, folderId: multi ? folder?.id : nil,
                                           mime: options.mime, defaultLocation: target == .defaultLocation))
        }
    }

    /// v25 3B şeklin (ya da başka bir kaynağın) resmini Kaydedilenler'e yazar.
    func saveImageData(_ bytes: Data, width: Int, height: Int, title: String, source: String, format: ImageFormat,
                       target: SaveTarget = .defaultLocation) throws -> SavedImage {
        let name = ImageExporter.safeName(title) + "." + format.ext
        let file = try exporter.write(bytes, name, subfolder: nil, target: target)
        let (path, bm) = relative(file.url, target)
        return store.insert(SavedImage(id: 0, canvasLocalId: source, title: title, path: path, bookmark: bm, width: width, height: height,
                                       createdAt: Self.now(), location: file.location, sizeBytes: file.sizeBytes, mime: format.mime,
                                       defaultLocation: target == .defaultLocation))
    }

    private func record(_ canvas: CanvasUi, _ file: ExportedFile, _ mime: String, _ target: SaveTarget) {
        let (path, bm) = relative(file.url, target)
        store.insert(SavedImage(id: 0, canvasLocalId: canvas.localId, title: canvas.name, path: path, bookmark: bm, width: canvas.width,
                                height: canvas.height, createdAt: Self.now(), location: file.location, sizeBytes: file.sizeBytes, mime: mime,
                                defaultLocation: target == .defaultLocation))
    }

    func exportPdf(_ canvas: CanvasUi, _ options: PdfOptions? = nil) throws -> ExportedFile {
        let o = options ?? defaultPdfOptions(canvas)
        assets.ensureLoaded(canvas)
        let file = try exporter.exportPdf(canvas, o)
        record(canvas, file, "application/pdf", o.target)
        return file
    }

    /// v40 baskıya hazır belge (CMYK TIFF ya da PDF): PDF'te sayfalar tek dosyada, TIFF'te sayfa başına bir dosya.
    func exportCmykDocument(_ canvas: CanvasUi, _ options: ExportOptions) throws -> ExportedFile {
        assets.ensureLoaded(canvas)
        let file: ExportedFile
        if options.cmykFormat == .PDF {
            file = try exporter.exportCmykPdf(canvas, options)
        } else {
            let list = try exporter.export(canvas, options)
            guard let first = list.first else { throw ExportError.failed }
            file = ExportedFile(url: first.url, location: first.location, sizeBytes: list.reduce(0) { $0 + $1.sizeBytes })
        }
        record(canvas, file, options.cmykFormat.mime, options.allPages && canvas.pageCount > 1 ? .defaultLocation : options.target)
        return file
    }

    func exportCbz(_ canvas: CanvasUi, _ options: CbzOptions) throws -> ExportedFile {
        assets.ensureLoaded(canvas)
        let file = try exporter.exportCbz(canvas, options)
        record(canvas, file, SavedImage.CBZ_MIME, options.target)
        return file
    }

    func previewImage(_ canvas: CanvasUi, maxSide: Int) -> CGImage {
        assets.ensureLoaded(canvas)
        let scale = Float(maxSide) / Float(max(max(canvas.width, canvas.height), 1))
        return exporter.render(canvas, scale: min(scale, 1))
    }

    func moveToFolder(_ ids: [Int64], _ folderId: String?) { store.moveTo(ids, folderId) }

    func delete(_ id: Int64) {
        guard let e = store.get(id) else { return }
        let u = url(e)
        if let bm = e.bookmark {
            var stale = false
            if let dir = try? URL(resolvingBookmarkData: bm, bookmarkDataIsStale: &stale) {
                let ok = dir.startAccessingSecurityScopedResource()
                exporter.delete(u)
                if ok { dir.stopAccessingSecurityScopedResource() }
            }
        } else {
            exporter.delete(u)
        }
        store.delete(id)
    }

    func deleteInFolders(_ folderIds: [String]) { store.inFolders(folderIds).forEach { delete($0.id) } }
}
