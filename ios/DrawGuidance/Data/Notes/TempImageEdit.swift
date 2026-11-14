import Combine
import CoreGraphics
import Foundation

/// v51 not almada resim düzenleme (Android `TempImageEdit.kt`): not alma modunda resim düzenleme araçları yok; düzenlenmek
/// istenen resim geçici bir basit resim tuvaline (`SIMPLE_ART`) eklenir. O tuvalde otomatik kayıt kapalıdır; Kaydet'e
/// dokununca tuval resme çevrilip nottaki resmin yerine konur ve geçici tuval silinir. Kaydetmeden çıkılırsa silinir.
///
/// Geçici tuvaller ve nota henüz aktarılmamış sonuçlar uygulama kapansa da kaybolmasın diye saklanır; geçici tuvaller
/// tuval listelerinde görünmez (`isTemp`).
@MainActor
enum TempCanvases {
    struct Session: Codable, Equatable {
        var tempId: String
        var sourceId: String
        var strokeId: String
        var width: Int
        var height: Int
        var createdAt: Int64
    }

    struct Result: Codable, Equatable {
        var tempId: String
        var sourceId: String
        var strokeId: String
        var assetId: String
        var width: Int
        var height: Int
        /// Geçici tuvalin oranı değişti: nottaki resmin çerçevesi yeni orana uydurulur.
        var reshaped: Bool
    }

    private struct State: Codable {
        var sessions: [String: Session] = [:]
        var results: [String: Result] = [:]
    }

    private static let KEY = "v51_temp_canvases"
    /// Testlerde ayrı bir depo verilebilir.
    static var defaults: UserDefaults = .standard { didSet { state = load() } }
    private static var state: State = load()
    private static var active = Set<String>()

    /// Nota aktarılacak yeni sonuçlar (açık not düzenleyicisi dinler).
    static let results = PassthroughSubject<Result, Never>()

    private static func load() -> State {
        guard let data = defaults.data(forKey: KEY), let s = try? JSONDecoder().decode(State.self, from: data) else { return State() }
        return s
    }

    private static func persist() {
        if let data = try? JSONEncoder().encode(state) { defaults.set(data, forKey: KEY) }
    }

    static var isEmpty: Bool { state.sessions.isEmpty }
    static func isTemp(_ id: String) -> Bool { state.sessions[id] != nil }
    static func session(_ id: String) -> Session? { state.sessions[id] }
    static var allSessions: [Session] { Array(state.sessions.values) }

    static func register(_ s: Session) { state.sessions[s.tempId] = s; persist() }
    static func remove(_ tempId: String) {
        if state.sessions.removeValue(forKey: tempId) != nil { persist() }
        active.remove(tempId)
    }

    static func putResult(_ r: Result) {
        state.results[r.tempId] = r
        persist()
        results.send(r)
    }

    static func pending(for sourceId: String) -> [Result] { state.results.values.filter { $0.sourceId == sourceId } }
    static func pendingAssetIds(_ sourceId: String) -> [String] { pending(for: sourceId).map(\.assetId) }
    static func consumed(_ r: Result) { if state.results.removeValue(forKey: r.tempId) != nil { persist() } }

    static func setActive(_ id: String, _ on: Bool) { if on { active.insert(id) } else { active.remove(id) } }
    static func isActive(_ id: String) -> Bool { active.contains(id) }

    /// Testler için.
    static func clearForTest() { state = State(); active = []; persist() }
}

/// v51 nottaki resim elemanının yeni resimle güncellenmesi (saf; birim testli).
enum TempImageGeometry {
    static let MAX_SIDE = 2400

    /// Geçici tuvalin boyutu değişmediyse çerçeve, döndürme ve kırpma aynen kalır; değiştiyse çerçevenin eni ve merkezi
    /// korunur, boyu yeni orana göre ayarlanır, kırpma kalkar.
    static func replace(_ image: Stroke, _ r: TempCanvases.Result) -> Stroke {
        var out = image
        out.assetId = r.assetId
        guard r.reshaped, image.points.count == 4, r.width > 0, r.height > 0 else { return out }
        let p = image.points
        let ux = p[1].x - p[0].x, uy = p[1].y - p[0].y
        let vx = p[3].x - p[0].x, vy = p[3].y - p[0].y
        let uLen = (ux * ux + uy * uy).squareRoot(), vLen = (vx * vx + vy * vy).squareRoot()
        guard uLen > 0, vLen > 0 else { return out }
        let aspect = Float(r.width) / Float(r.height)
        let k = (uLen / aspect) / vLen
        let nvx = vx * k, nvy = vy * k
        let cx = (p[0].x + p[1].x + p[2].x + p[3].x) / 4, cy = (p[0].y + p[1].y + p[2].y + p[3].y) / 4
        func pt(_ su: Float, _ sv: Float) -> StrokePoint {
            StrokePoint(ContentCodec.quantize(cx + ux * su + nvx * sv), ContentCodec.quantize(cy + uy * su + nvy * sv))
        }
        out.points = [pt(-0.5, -0.5), pt(0.5, -0.5), pt(0.5, 0.5), pt(-0.5, 0.5)]
        out.clip = nil
        return out
    }

    /// Geçici tuvalin boyutu: resmin piksel boyutu, uzun kenarı en çok [maxSide].
    static func canvasSize(_ width: Int, _ height: Int, maxSide: Int = MAX_SIDE) -> (Int, Int) {
        guard width > 0, height > 0 else { return (0, 0) }
        let scale = min(1, Float(maxSide) / Float(max(width, height)))
        return (max(Int((Float(width) * scale).rounded()), 16), max(Int((Float(height) * scale).rounded()), 16))
    }

    static func reshaped(_ w0: Int, _ h0: Int, _ w1: Int, _ h1: Int) -> Bool {
        guard w0 > 0, h0 > 0, w1 > 0, h1 > 0 else { return false }
        let a0 = Float(w0) / Float(h0), a1 = Float(w1) / Float(h1)
        return abs(a0 - a1) / a0 > 0.01
    }
}

/// v51 geçici resim düzenleme tuvallerini oluşturur, kaydedince nota aktarır ve siler.
@MainActor
enum TempImageEditor {
    /// [image] resmi için geçici basit resim tuvali oluşturur; kimliğini döner (olmazsa nil).
    static func start(sourceId: String, image: Stroke, deps: EditorDependencies) -> String? {
        guard let assetId = image.assetId else { return nil }
        let decoded = CanvasImageCache.get(assetId) ?? deps.assets.bytes(assetId).flatMap { CanvasImageCache.decode($0) }
        guard let bitmap = decoded else { return nil }
        let (w, h) = TempImageGeometry.canvasSize(bitmap.width, bitmap.height)
        guard w > 0, h > 0 else { return nil }
        let tempId = UUID().uuidString.lowercased()
        // Önce kaydedilir: tuval oluşur oluşmaz listelerden gizlenir
        TempCanvases.register(TempCanvases.Session(tempId: tempId, sourceId: sourceId, strokeId: image.id, width: w, height: h,
                                                   createdAt: CanvasStore.nowMs()))
        guard let copied = deps.assets.copyTo(assetId, tempId) else { TempCanvases.remove(tempId); return nil }
        let fw = Float(w), fh = Float(h)
        let stroke = Stroke(id: UUID().uuidString.lowercased(),
                            points: [StrokePoint(0, 0), StrokePoint(fw, 0), StrokePoint(fw, fh), StrokePoint(0, fh)],
                            colorHex: "#000000", brushType: .IMAGE, sizePx: 1, opacity: 1, order: 0, assetId: copied)
        let now = CanvasStore.nowMs()
        let c = CanvasUi(localId: tempId, name: L("v51_temp_image_name"), width: w, height: h, backgroundColorHex: "#FFFFFF",
                         aiEnabled: false, pages: [[stroke]], createdAt: now, updatedAt: now, kind: .SIMPLE_ART)
        deps.canvases.save(c)
        return tempId
    }

    /// Geçici tuvali resme çevirip nota aktarır (sonuç `TempCanvases.results` ile gelir) ve geçici tuvali siler.
    static func commit(_ tempId: String, canvas: CanvasUi, deps: EditorDependencies) {
        guard let s = TempCanvases.session(tempId) else { return }
        deps.assets.ensureLoaded(canvas)
        let image = ImageExporter().render(canvas, scale: 1, includeGuides: false)
        if let data = try? ImageExporter.encode(image, .PNG, 100) {
            let prepared = PreparedImage(bytes: data, mimeType: "image/png", width: image.width, height: image.height, image: image,
                                         originalBytes: data.count, detailScore: 0)
            let assetId = deps.assets.save(s.sourceId, prepared)
            TempCanvases.putResult(TempCanvases.Result(
                tempId: tempId, sourceId: s.sourceId, strokeId: s.strokeId, assetId: assetId, width: image.width, height: image.height,
                reshaped: TempImageGeometry.reshaped(s.width, s.height, canvas.width, canvas.height)))
        }
        delete(tempId, deps: deps)
    }

    /// Kaydetmeden çıkıldı: geçici tuval silinir.
    static func discard(_ tempId: String, deps: EditorDependencies) {
        guard TempCanvases.isTemp(tempId) else { return }
        delete(tempId, deps: deps)
    }

    /// Açık olmayan geçici tuvalleri siler: [sourceId] verilirse o notunkileri, verilmezse [olderThanMs]'den eskileri.
    static func cleanup(sourceId: String? = nil, olderThanMs: Int64 = 0, deps: EditorDependencies) {
        let now = CanvasStore.nowMs()
        for s in TempCanvases.allSessions where !TempCanvases.isActive(s.tempId)
            && (sourceId == nil || s.sourceId == sourceId) && now - s.createdAt >= olderThanMs {
            delete(s.tempId, deps: deps)
        }
    }

    private static func delete(_ tempId: String, deps: EditorDependencies) {
        deps.canvases.saveQueue.flushNow()
        deps.canvases.delete(tempId)
        deps.assets.deleteForCanvas(tempId)
        TempCanvases.remove(tempId)
    }
}
