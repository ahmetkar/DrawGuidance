import CoreGraphics
import Foundation

/// v52 Foto düzenleme modu (Android `PhotoEditController`): seçili resmin bütün raster düzenlemeleri tek durumda
/// ([PhotoEdit]). Önizleme küçük kopyadan, kayıt tam çözünürlüklü kopyadan baştan hesaplanır; geri al/yinele durum
/// geçmişidir. Kaydedince yeni resim verisi tuvale yazılır ve resim elemanı onu gösterir (tek geri alma adımı).
@MainActor
final class PhotoEditController: ObservableObject {
    private let store: EditorStore
    private unowned let host: EditorHost
    private let assets: CanvasAssetStore

    private struct Session {
        let strokeId: String
        let full: [UInt32]; let fw: Int; let fh: Int
        let small: [UInt32]; let sw: Int; let sh: Int
    }
    private var session: Session?

    /// Düzenlenen resim elemanının kimliği (ekran açık).
    @Published private(set) var openId: String?
    @Published private(set) var loading = false
    @Published private(set) var edit = PhotoEdit()
    @Published private(set) var preview: CGImage?
    @Published private(set) var original: CGImage?
    @Published private(set) var rendering = false
    @Published private(set) var saving = false
    @Published private(set) var cropMode = false
    @Published private(set) var canUndo = false
    @Published private(set) var canRedo = false

    private var undoStack: [PhotoEdit] = []
    private var redoStack: [PhotoEdit] = []
    private var lastKey: String?
    private var renderTask: Task<Void, Never>?
    private var renderGen = 0

    static let previewSide = 1024

    init(_ store: EditorStore, _ host: EditorHost, assets: CanvasAssetStore = .shared) {
        self.store = store; self.host = host; self.assets = assets
    }

    var sourceSize: (Int, Int) { session.map { ($0.sw, $0.sh) } ?? (1, 1) }

    /// Seçili (ya da [strokeId]) resmi foto düzenlemede açar.
    func open(_ strokeId: String? = nil) {
        guard openId == nil, let c = store.canvas else { return }
        let found: Stroke? = strokeId.flatMap { id in c.strokes.first { $0.id == id } } ?? store.selectedStroke { $0.brushType == .IMAGE }
        guard let image = found, image.brushType == .IMAGE, let assetId = image.assetId else { return }
        openId = image.id
        edit = PhotoEdit(); preview = nil; original = nil
        undoStack = []; redoStack = []; lastKey = nil; publishHistory()
        cropMode = false
        loading = true
        let assets = self.assets
        let id = image.id
        Task {
            defer { loading = false }
            let s: Session? = await Task.detached(priority: .userInitiated) { () -> Session? in
                guard let img = CanvasImageCache.get(assetId) ?? assets.bytes(assetId).flatMap({ CanvasImageCache.decode($0) }) else { return nil }
                let maxFull = Budget3D.tier == .LOW ? 1600 : 2400
                guard let full = PixelCodec.texture(img, maxSide: maxFull) else { return nil }
                let small = Self.downscale(full.px, full.w, full.h, Self.previewSide)
                return Session(strokeId: id, full: full.px, fw: full.w, fh: full.h, small: small.px, sw: small.w, sh: small.h)
            }.value
            guard openId == id else { return }
            guard let s else { failAndClose(); return }
            session = s
            original = Bitmaps.image(argb: s.small, s.sw, s.sh)
            preview = original
        }
    }

    /// Durumu değiştirir; aynı [key] ile art arda gelen değişiklikler (kaydırıcı) tek geri alma adımıdır.
    func update(_ key: String? = nil, _ block: (inout PhotoEdit) -> Void) {
        let before = edit
        var after = before
        block(&after)
        if after == before { return }
        if key == nil || key != lastKey {
            undoStack.append(before)
            if undoStack.count > 80 { undoStack.removeFirst() }
            redoStack.removeAll()
        }
        lastKey = key
        edit = after
        publishHistory()
        render()
    }

    func endGesture() { lastKey = nil }

    func undo() {
        guard let prev = undoStack.popLast() else { return }
        redoStack.append(edit); edit = prev; lastKey = nil
        publishHistory(); render()
    }

    func redo() {
        guard let next = redoStack.popLast() else { return }
        undoStack.append(edit); edit = next; lastKey = nil
        publishHistory(); render()
    }

    func reset() { update { $0 = PhotoEdit() } }

    func setCropMode(_ on: Bool) {
        guard cropMode != on else { return }
        cropMode = on
        render()
    }

    /// Önizlemedeki (0..1) noktanın kaynak resimdeki karşılığı (fırça izi, arka plan dokunuşu).
    func toSource(_ u: Float, _ v: Float) -> (Float, Float) {
        let (w, h) = sourceSize
        return PhotoOps.toSource(u, v, w, h, edit, skipCrop: cropMode)
    }

    private func render() {
        guard let s = session else { return }
        let e = edit
        let skip = cropMode
        renderTask?.cancel()
        renderGen += 1
        let gen = renderGen
        rendering = true
        renderTask = Task {
            try? await Task.sleep(nanoseconds: 16_000_000)
            if Task.isCancelled { return }
            let img: CGImage? = await Task.detached(priority: .userInitiated) { () -> CGImage? in
                let r = PhotoOps.render(s.small, s.sw, s.sh, e, skipCrop: skip)
                return Bitmaps.image(argb: r.px, r.w, r.h)
            }.value
            guard gen == renderGen else { return }
            if let img { preview = img }
            rendering = false
        }
    }

    /// Tam çözünürlükte uygular; yeni resim tuvaldeki resmin yerine geçer ve ekran kapanır.
    func save() {
        guard let s = session, !saving else { return }
        let e = edit
        if e.isIdentity { close(); return }
        saving = true
        let assets = self.assets
        let canvasId = store.canvasLocalId
        Task {
            defer { saving = false }
            let prepared: PreparedImage? = await Task.detached(priority: .userInitiated) { () -> PreparedImage? in
                let r = PhotoOps.render(s.full, s.fw, s.fh, e)
                let opaque = r.px.allSatisfy { $0 >> 24 == 0xFF }
                let cg = Bitmaps.image(argb: r.px, r.w, r.h)
                guard let bytes = PixelCodec.encode(cg, jpeg: opaque, quality: 0.92) else { return nil }
                return PreparedImage(bytes: bytes, mimeType: opaque ? "image/jpeg" : "image/png", width: r.w, height: r.h, image: cg,
                                     originalBytes: bytes.count, detailScore: 0)
            }.value
            guard let prepared else { host.emit(.message(L("v52_photo_failed"))); return }
            let newId = assets.save(canvasId, prepared)
            CanvasImageCache.put(newId, prepared.image)
            if let current = store.canvas, current.strokes.contains(where: { $0.id == s.strokeId }) {
                let result = TempCanvases.Result(tempId: "", sourceId: canvasId, strokeId: s.strokeId, assetId: newId,
                                                 width: prepared.width, height: prepared.height,
                                                 reshaped: TempImageGeometry.reshaped(s.fw, s.fh, prepared.width, prepared.height))
                store.commit(current.strokes.map { $0.id == s.strokeId ? TempImageGeometry.replace($0, result) : $0 })
                host.emit(.message(L("v52_photo_saved")))
            }
            close()
        }
    }

    func close() {
        renderTask?.cancel()
        renderGen += 1
        session = nil
        openId = nil
        preview = nil; original = nil
        edit = PhotoEdit()
        rendering = false
        undoStack = []; redoStack = []; publishHistory()
    }

    private func failAndClose() {
        host.emit(.message(L("v52_photo_failed")))
        close()
    }

    private func publishHistory() { canUndo = !undoStack.isEmpty; canRedo = !redoStack.isEmpty }

    nonisolated private static func downscale(_ px: [UInt32], _ w: Int, _ h: Int, _ maxSide: Int) -> (px: [UInt32], w: Int, h: Int) {
        guard max(w, h) > maxSide else { return (px, w, h) }
        let k = Double(maxSide) / Double(max(w, h))
        let nw = max(1, Int(Double(w) * k)), nh = max(1, Int(Double(h) * k))
        let cg = Bitmaps.image(argb: px, w, h)
        if let t = PixelCodec.texture(cg, maxSide: maxSide) { return (t.px, t.w, t.h) }
        // yedek: en yakın komşu
        var out = [UInt32](repeating: 0, count: nw * nh)
        for y in 0..<nh { for x in 0..<nw { out[y * nw + x] = px[min(h - 1, y * h / nh) * w + min(w - 1, x * w / nw)] } }
        return (out, nw, nh)
    }
}
