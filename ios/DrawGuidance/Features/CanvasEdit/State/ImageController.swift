import CoreGraphics
import Foundation

/// Resimler: tuvale resim ekleme (kontrol, onay), resim filtreleri/efektleri, tuvali resim, PDF, CMYK ve CBZ olarak
/// kaydetme (Android `ImageController`).
@MainActor
final class ImageController: ObservableObject {
    private let store: EditorStore
    private unowned let host: EditorHost
    private let assets: CanvasAssetStore
    private let images: ImagesRepository?

    init(_ store: EditorStore, _ host: EditorHost, assets: CanvasAssetStore = .shared, images: ImagesRepository?) {
        self.store = store; self.host = host; self.assets = assets; self.images = images
    }

    // MARK: v27 filtreler, v30 efektler ve arka plan kaldırma

    /// Seçili resim işleniyor.
    @Published private(set) var filtering = false

    func filterSelected(_ kind: ComicFilter) {
        processSelected(Self.MSG_FILTER_FAIL) { px, w, h in (ComicFilters.apply(kind, px, w, h), 0) }
    }

    func effectsSelected(_ look: ImageLook, _ adjust: ImageAdjust) {
        if look == .NONE && adjust.isIdentity { return }
        processSelected(Self.MSG_EFFECT_FAIL) { px, w, h in (ImageEffects.apply(px, w, h, look: look, adjust: adjust), 0) }
    }

    func removeBackgroundSelected(_ options: BackgroundOptions) {
        processSelected(Self.MSG_BG_FAIL, onDone: { [weak self] ratio in self?.host.emit(.backgroundRemoved(Int(ratio * 100))) }) { px, w, h in
            let mask = BackgroundRemover.mask(px, w, h, options)
            return (BackgroundRemover.apply(px, w, h, mask), mask.removedRatio)
        }
    }

    private func selectedImage() -> Stroke? {
        guard store.selection.count == 1 else { return nil }
        return store.selectedStroke { $0.brushType == .IMAGE }
    }

    /// Araç pencerelerinin önizlemesi için seçili resmin küçültülmüş pikselleri ([maxSide] kenar).
    func selectedPreviewSource(_ maxSide: Int) async -> Texture? {
        guard let assetId = selectedImage()?.assetId else { return nil }
        let assets = self.assets
        return await Task.detached(priority: .userInitiated) {
            guard let img = CanvasImageCache.get(assetId) ?? assets.bytes(assetId).flatMap({ CanvasImageCache.decode($0) }) else { return nil }
            return PixelCodec.texture(img, maxSide: maxSide)
        }.value
    }

    /// Seçili resmin piksellerini arka planda işler; sonuç yeni resim verisi olarak saklanır (tek geri alma adımı).
    private func processSelected(_ failMessage: String, onDone: ((Float) -> Void)? = nil,
                                 _ transform: @escaping @Sendable ([UInt32], Int, Int) -> ([UInt32], Float)) {
        guard let image = selectedImage(), let assetId = image.assetId, !filtering else { return }
        filtering = true
        let assets = self.assets
        let canvasId = store.canvasLocalId
        Task {
            defer { filtering = false }
            let result: (PreparedImage, Float)? = await Task.detached(priority: .userInitiated) { () -> (PreparedImage, Float)? in
                guard let img = CanvasImageCache.get(assetId) ?? assets.bytes(assetId).flatMap({ CanvasImageCache.decode($0) }) else { return nil }
                let maxSide = Budget3D.tier == .LOW ? 1100 : 1600
                guard let tex = PixelCodec.texture(img, maxSide: maxSide) else { return nil }
                let (out, ratio) = transform(tex.px, tex.w, tex.h)
                let opaque = out.allSatisfy { $0 >> 24 == 0xFF }
                let cg = Bitmaps.image(argb: out, tex.w, tex.h)
                guard let bytes = PixelCodec.encode(cg, jpeg: opaque, quality: 0.9) else { return nil }
                return (PreparedImage(bytes: bytes, mimeType: opaque ? "image/jpeg" : "image/png", width: tex.w, height: tex.h, image: cg,
                                      originalBytes: bytes.count, detailScore: 0), ratio)
            }.value
            guard let (prepared, ratio) = result else { host.emit(.message(I18n.tr(failMessage))); return }
            let newId = assets.save(canvasId, prepared)
            guard let current = store.canvas, current.strokes.contains(where: { $0.id == image.id }) else { return }
            store.commit(current.strokes.map { s in var c = s; if s.id == image.id { c.assetId = newId }; return c })
            onDone?(ratio)
        }
    }

    // MARK: Resim olarak kaydetme

    @Published private(set) var save: ImageSaveState = .idle
    /// Açık kaydetme penceresinin başlangıç ayarları (nil: kapalı).
    @Published private(set) var saveOptions: ExportOptions?

    var saveLocation: String { images?.locationLabel ?? ImageExporter.FOLDER }

    func openSaveDialog() {
        guard let current = store.canvas, let images, save != .saving else { return }
        saveOptions = images.defaultOptions(current)
    }

    func closeSaveDialog() { saveOptions = nil }

    func outputSize(_ scale: Float) -> (Int, Int) {
        guard let current = store.canvas, let images else { return (0, 0) }
        return images.outputSize(current, scale)
    }

    func saveAsImage() {
        guard let current = store.canvas, let images else { return }
        saveAsImage(images.defaultOptions(current))
    }

    func saveAsImage(_ options: ExportOptions) {
        guard let current = store.canvas, let images else { return }
        saveOptions = nil
        if options.isDocument { exportPrintDocument(current, options); return }
        if save == .saving { return }
        save = .saving
        Task {
            await Task.yield()
            do {
                let list = try images.saveCanvasAsImage(current, options)
                save = list.isEmpty ? .failed : .saved(list)
            } catch {
                AppLog.e("ImageSave", "Resim kaydedilemedi", error)
                save = .failed
            }
        }
    }

    func dismissSave() { save = .idle }

    // MARK: PDF

    @Published private(set) var pdf: PdfExportState = .idle
    @Published private(set) var pdfOptions: PdfOptions?
    private var lastPdf: PdfOptions?

    var pdfLocation: String { images?.pdfLocationLabel ?? ImageExporter.FOLDER }

    func openPdfDialog() {
        guard let current = store.canvas, let images, pdf != .exporting else { return }
        let defaults = images.defaultPdfOptions(current)
        if var last = lastPdf {
            last.fileName = defaults.fileName
            last.pages = defaults.pages; last.fromPage = 1; last.toPage = max(current.pageCount, 1)
            last.includeGuides = last.includeGuides && !current.guide.isEmpty
            pdfOptions = last
        } else {
            var d = defaults; d.toPage = max(current.pageCount, 1)
            pdfOptions = d
        }
    }

    func closePdfDialog() { pdfOptions = nil }

    /// v42 birleşik Kaydet penceresinde başlangıç ayarlarını değiştirir.
    func adjustSaveDefaults(image: (ExportOptions) -> ExportOptions, pdf change: (PdfOptions) -> PdfOptions) {
        if let s = saveOptions { saveOptions = image(s) }
        if let p = pdfOptions { pdfOptions = change(p) }
    }

    func exportPdf(_ options: PdfOptions? = nil) {
        guard let current = store.canvas, let images else { return }
        pdfOptions = nil
        if pdf == .exporting { return }
        let chosen = options ?? images.defaultPdfOptions(current)
        lastPdf = chosen
        pdf = .exporting
        Task {
            await Task.yield()
            do { pdf = .done(try images.exportPdf(current, chosen)) } catch {
                AppLog.e("PdfExport", "PDF kaydedilemedi", error)
                pdf = .failed
            }
        }
    }

    func dismissPdf() { pdf = .idle }

    private func exportPrintDocument(_ current: CanvasUi, _ options: ExportOptions) {
        guard let images, pdf != .exporting else { return }
        pdf = .exporting
        Task {
            await Task.yield()
            do { pdf = .printDone(try images.exportCmykDocument(current, options)) } catch {
                AppLog.e("PrintExport", "Baskı dosyası kaydedilemedi", error)
                pdf = .failed
            }
        }
    }

    /// v40 CBZ.
    func exportCbz(_ options: CbzOptions) {
        guard let current = store.canvas, let images, pdf != .exporting else { return }
        pdf = .exporting
        Task {
            await Task.yield()
            do { pdf = .done(try images.exportCbz(current, options)) } catch {
                AppLog.e("CbzExport", "CBZ kaydedilemedi", error)
                pdf = .failed
            }
        }
    }

    /// v40 CMYK provası için tuvalin küçük resmi.
    func previewImage(_ maxSide: Int = 320) -> CGImage? {
        guard let current = store.canvas, let images else { return nil }
        return images.previewImage(current, maxSide: maxSide)
    }

    // MARK: Resim ekleme

    @Published private(set) var importState: ImageImportState?

    private var isChecking: Bool { if case .checking = importState { return true }; return false }

    /// Seçilen resmi kontrol eder; hata varsa reddeder, uyarı varsa onay ister, yoksa ekler.
    func importImage(_ data: Data, center: P) {
        if isChecking { return }
        importState = .checking
        Task {
            let result = await Task.detached(priority: .userInitiated) { ImageImportValidator.check(data) }.value
            if !result.accepted || result.image == nil {
                importState = .rejected(errors: result.errors.isEmpty ? [.unreadable] : result.errors, warnings: result.warnings)
            } else if !result.warnings.isEmpty {
                importState = .needsConfirmation(warnings: result.warnings, image: result.image!, center: center)
            } else {
                importState = nil
                addImage(result.image!, center)
            }
        }
    }

    /// v29 Kaydedilenler'den resim: sınır kontrolü olmadan, küçültülerek eklenir.
    func importSaved(_ data: Data, center: P) {
        if isChecking { return }
        importState = .checking
        Task {
            let image = await Task.detached(priority: .userInitiated) { ImageImportValidator.prepareSaved(data) }.value
            guard let image else { importState = .rejected(errors: [.unreadable], warnings: []); return }
            importState = nil
            addImage(image, center)
        }
    }

    /// v23 arka plan resmi: kontrol edilir, tuvalin resim verisine eklenir; kimliği [onAdded]'a verilir.
    func importBackground(_ data: Data, onAdded: @escaping (String) -> Void) {
        let canvasId = store.canvasLocalId
        Task {
            let result = await Task.detached(priority: .userInitiated) { ImageImportValidator.check(data) }.value
            guard result.accepted, let image = result.image else {
                importState = .rejected(errors: result.errors.isEmpty ? [.unreadable] : result.errors, warnings: result.warnings)
                return
            }
            onAdded(assets.save(canvasId, image))
        }
    }

    func confirmImport() {
        guard case let .needsConfirmation(_, image, center) = importState else { return }
        importState = nil
        addImage(image, center)
    }

    func dismissImport() { importState = nil }

    nonisolated private static func loadAsset(_ path: String, fillAspect: Float?, quality: Double) -> PreparedImage? {
        guard let bytes = try? ComicAssets.bytes(path), let src = CanvasImageCache.decode(bytes) else { return nil }
        let r = fillAspect.map { Backdrop.cropRect(src.width, src.height, $0) } ?? Backdrop.Crop(left: 0, top: 0, width: src.width, height: src.height)
        if r.width == src.width && r.height == src.height {
            return PreparedImage(bytes: bytes, mimeType: "image/webp", width: src.width, height: src.height, image: src, originalBytes: bytes.count, detailScore: 0)
        }
        // CGImage kırpması üst-sol kökenlidir (görüntü satırları yukarıdan)
        guard let cropped = src.cropping(to: CGRect(x: r.left, y: r.top, width: r.width, height: r.height)) else { return nil }
        let opaque = cropped.alphaInfo == .none || cropped.alphaInfo == .noneSkipLast || cropped.alphaInfo == .noneSkipFirst
        guard let data = PixelCodec.encode(cropped, jpeg: opaque, quality: quality) else { return nil }
        return PreparedImage(bytes: data, mimeType: opaque ? "image/jpeg" : "image/png", width: cropped.width, height: cropped.height, image: cropped,
                             originalBytes: bytes.count, detailScore: 0)
    }

    /// v40 hazır karakter çeşidi: resim elemanı olarak eklenir; boyu sayfanın kısa kenarının [heightFraction] katı.
    func insertAssetImage(_ path: String, heightFraction: Float, center: P) {
        Task {
            let image = await Task.detached(priority: .userInitiated) { Self.loadAsset(path, fillAspect: nil, quality: 0.9) }.value
            guard let image else { AppLog.e("ComicAssets", "Karakter resmi okunamadı: \(path)", nil); return }
            addImage(image, center, heightFraction: heightFraction, announce: false)
        }
    }

    nonisolated private static func quad(_ l: Float, _ t: Float, _ r: Float, _ b: Float) -> [StrokePoint] {
        let q = ContentCodec.quantize
        return [StrokePoint(q(l), q(t)), StrokePoint(q(r), q(t)), StrokePoint(q(r), q(b)), StrokePoint(q(l), q(b))]
    }

    /// v41 hazır mekân arka planı: [target] oranına kırpılıp tam o dikdörtgene, bütün elemanların arkasına konur.
    func insertBackdrop(_ path: String, target: FRect, clip: [StrokePoint]? = nil, onDone: @escaping (String?) -> Void = { _ in }) {
        if target.width <= 1 || target.height <= 1 { onDone(nil); return }
        let canvasId = store.canvasLocalId
        Task {
            let aspect = target.width / target.height
            let image = await Task.detached(priority: .userInitiated) { Self.loadAsset(path, fillAspect: aspect, quality: 0.88) }.value
            guard let image else {
                AppLog.e("ComicAssets", "Mekân arka planı okunamadı: \(path)", nil)
                onDone(nil); return
            }
            let assetId = assets.save(canvasId, image)
            guard let latest = store.canvas else { return }
            let stroke = Stroke(id: UUID().uuidString.lowercased(), points: Self.quad(target.left, target.top, target.right, target.bottom),
                                colorHex: "#000000", brushType: .IMAGE, sizePx: 1, opacity: 1, order: 0, assetId: assetId,
                                clip: clip?.count == 4 ? clip : nil)
            let merged = [stroke] + latest.strokes
            store.commit(merged.enumerated().map { i, s in if s.order == i { return s }; var c = s; c.order = i; return c })
            onDone(stroke.id)
        }
    }

    /// v46 eğitim çizimleri için hazır resim elemanı (eklenmez).
    func assetImageStroke(_ path: String, target: FRect, fill: Bool) async -> Stroke? {
        if target.width <= 1 || target.height <= 1 { return nil }
        let aspect = target.width / target.height
        guard let image = await Task.detached(priority: .userInitiated, operation: { Self.loadAsset(path, fillAspect: fill ? aspect : nil, quality: 0.85) }).value
        else { return nil }
        let assetId = assets.save(store.canvasLocalId, image)
        var l = target.left, t = target.top, r = target.right, b = target.bottom
        if !fill {
            let k = min(target.width / Float(image.width), target.height / Float(image.height))
            let w = Float(image.width) * k, h = Float(image.height) * k
            let cx = (target.left + target.right) / 2
            l = cx - w / 2; r = cx + w / 2; t = target.bottom - h; b = target.bottom
        }
        return Stroke(id: UUID().uuidString.lowercased(), points: Self.quad(l, t, r, b), colorHex: "#000000", brushType: .IMAGE,
                      sizePx: 1, opacity: 1, order: 0, assetId: assetId)
    }

    private func addImage(_ image: PreparedImage, _ center: P, heightFraction: Float? = nil, announce: Bool = true) {
        guard let current = store.canvas else { return }
        let assetId = assets.save(store.canvasLocalId, image)
        let cw = Float(current.width), ch = Float(current.height), iw = Float(image.width), ih = Float(image.height)
        let fit: Float
        if let heightFraction {
            fit = min(min(cw, ch) * heightFraction / ih, min(cw * 0.9 / iw, ch * 0.9 / ih))
        } else {
            fit = min(1, min(cw * 0.6 / iw, ch * 0.6 / ih))
        }
        let halfW = iw * fit / 2, halfH = ih * fit / 2
        guard let latest = store.canvas else { return }
        let stroke = Stroke(id: UUID().uuidString.lowercased(), points: Self.quad(center.x - halfW, center.y - halfH, center.x + halfW, center.y + halfH),
                            colorHex: "#000000", brushType: .IMAGE, sizePx: 1, opacity: 1, order: latest.strokes.count, assetId: assetId)
        store.commit(latest.strokes + [stroke])
        if announce { host.emit(.imageAdded(storedBytes: image.bytes.count, originalBytes: image.originalBytes)) }
        host.inserted([stroke.id])
    }

    static let MSG_FILTER_FAIL = "Filtre uygulanamadı"
    static let MSG_EFFECT_FAIL = "Efekt uygulanamadı"
    static let MSG_BG_FAIL = "Arka plan kaldırılamadı"
}

/// v41 mekân arka planının kırpılması (saf).
enum Backdrop {
    struct Crop: Equatable { let left: Int; let top: Int; let width: Int; let height: Int }

    static func cropRect(_ srcW: Int, _ srcH: Int, _ aspect: Float) -> Crop {
        if srcW <= 0 || srcH <= 0 || !(aspect > 0) || aspect.isInfinite { return Crop(left: 0, top: 0, width: max(srcW, 0), height: max(srcH, 0)) }
        let srcAspect = Float(srcW) / Float(srcH)
        if srcAspect > aspect {
            let w = Int((Float(srcH) * aspect).rounded()).coerceIn(1, srcW)
            return Crop(left: (srcW - w) / 2, top: 0, width: w, height: srcH)
        }
        let h = Int((Float(srcW) / aspect).rounded()).coerceIn(1, srcH)
        return Crop(left: 0, top: (srcH - h) / 2, width: srcW, height: h)
    }

    static func bounds(_ s: Stroke) -> FRect {
        if s.points.isEmpty { return FRect(left: 0, top: 0, right: 0, bottom: 0) }
        return FRect(left: s.points.map(\.x).min()!, top: s.points.map(\.y).min()!, right: s.points.map(\.x).max()!, bottom: s.points.map(\.y).max()!)
    }

    /// v49 panelin dört köşesi (yalnız dört ayrı köşesi olan panellerde).
    static func panelClip(_ s: Stroke) -> [StrokePoint]? {
        var pts: [StrokePoint] = []
        for p in s.points {
            if let last = pts.last, abs(last.x - p.x) <= 0.5 && abs(last.y - p.y) <= 0.5 { continue }
            pts.append(p)
        }
        if pts.count >= 2, let a = pts.first, let b = pts.last, abs(a.x - b.x) <= 0.5 && abs(a.y - b.y) <= 0.5 { pts.removeLast() }
        return pts.count == 4 ? pts : nil
    }
}
