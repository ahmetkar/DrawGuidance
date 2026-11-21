import CoreGraphics
import Foundation
import SwiftUI

/// v56 3B Çizim modları (alt mod çubuğu).
enum SketchMode: String, CaseIterable { case DRAW, PLANE, SELECT, ADD, VIEW }

/// Çiz modunun araçları.
enum SketchTool { case PEN, ERASER }

/// Seç modunda sürüklemenin yaptığı.
enum SketchXform: CaseIterable { case MOVE, ROTATE, SCALE }

/// Kaydetme, çıkış ve bildirim olayları.
enum SketchEvent: Equatable {
    case saved(String)
    case exit
    case message(String)
}

/// v56 3B Çizim ekranının modeli (Android `Sketch3DViewModel`): eskiz (çizgiler, cisimler, katmanlar, son düzlem), mod
/// ve araç, görünür çizim düzlemi (hazır yönler, yüze/çizgi ucuna/kesişime yapıştırma, 15° döndürme), basınçlı çizgi,
/// silgi (çizgiyi değdiği yerden böler), seçim ve dönüştürme, temel cisimler, katmanlar ve geri al/yinele.
/// Kayıt D3 v2 olarak 3B Çizimlerim'e yazılır; çıktı resimdir.
@MainActor
final class Sketch3DModel: ObservableObject {
    static let DEFAULT_NAME = "Yeni 3B çizim"
    static let DEFAULT_LAYER = "L1"
    static let MAX_HISTORY = 60
    // Türkçe kaynak metinler; arayüzde I18n.tr ile çevrilir (Android ile aynı tsv anahtarları)
    static let LAYER_NAME = "Katman %d"
    static let MSG_SNAP_POINT = "Düzlem noktaya taşındı"
    static let MSG_SNAP_FACE = "Düzlem yüze yapıştı"
    static let MSG_SNAP_NONE = "Yüze, çizgi ucuna ya da kesişime dokunun"
    static let MSG_LAYER_LOCKED = "Katman kilitli"
    static let MSG_LAYER_HIDDEN = "Katman gizli"
    static let GPU_FALLBACK = "Ekran kartı çizimi açılamadı; yazılımla çiziliyor"

    @Published private(set) var model: Model3D?
    @Published private(set) var mode: SketchMode = .DRAW
    @Published private(set) var tool: SketchTool = .PEN
    @Published private(set) var brush: Brush3D = .PEN
    @Published private(set) var color: Int32 = Int32(bitPattern: 0xFF26_3238)
    /// Fırça kalınlığı (ekran noktası; dünyaya kameranın ölçeğiyle çevrilir).
    @Published private(set) var size: Float = 6
    @Published private(set) var opacity: Float = 1
    @Published private(set) var eraserSize: Float = 20
    @Published private(set) var plane: Plane3 = PlaneSnap.preset(.FRONT, through: V3(0, 0.5, 0))
    @Published private(set) var selection: Set<String> = []
    @Published private(set) var xform: SketchXform = .MOVE
    @Published private(set) var axis = 1
    @Published private(set) var activeLayer = Sketch3DModel.DEFAULT_LAYER
    /// Parmakla çiz (kalemsiz cihazlar); kalem görülünce kapanır.
    @Published private(set) var fingerDraw = true
    @Published private(set) var dirty = false
    @Published private(set) var canUndo = false
    @Published private(set) var canRedo = false
    /// Metal çizici açık mı (açılamazsa yazılım çiziciye düşülür).
    @Published private(set) var gpu = true
    @Published var event: SketchEvent?
    @Published private(set) var renderTick = 0

    let repository: Model3DRepository?
    let textures: TextureRepository
    private let modelId: String?
    private var fingerDrawTouched = false
    private var undoStack: [Model3D] = []
    private var redoStack: [Model3D] = []
    private var liveBase: Model3D?
    private var erasing = false
    private var viewW = 0, viewH = 0

    init(repository: Model3DRepository?, textures: TextureRepository = .shared, modelId: String?, initialName: String? = nil,
         preloaded: Model3D? = nil) {
        self.repository = repository
        self.textures = textures
        self.modelId = modelId
        if let preloaded { install(preloaded, isNew: false); return }
        Task { [weak self] in
            guard let self else { return }
            var loaded: Model3D? = nil
            if let id = modelId, let repo = repository { loaded = await repo.load(id) }
            let base = loaded ?? Model3D(modelId ?? UUID().uuidString.lowercased(), initialName ?? Self.DEFAULT_NAME, [],
                                         camera: Camera3(yaw: 20, pitch: 16, persp: 0.35, zoom: 1, target: V3(0, 0.5, 0)))
            self.install(base, isNew: loaded == nil)
        }
    }

    private func install(_ base: Model3D, isNew: Bool) {
        let m = Self.withLayers(base)
        let tex = textures
        let ids = TextureRepository.ids(m)
        if !ids.isEmpty { Task.detached(priority: .utility) { tex.preload(ids) } }
        model = m
        if let p = m.plane { plane = p }
        activeLayer = m.layers.first?.id ?? Self.DEFAULT_LAYER
        if isNew { dirty = true }
    }

    /// Katmanı olmayan (eski) çizime ilk katman eklenir.
    static func withLayers(_ m: Model3D) -> Model3D {
        if !m.layers.isEmpty { return m }
        var c = m
        c.layers = [Layer3(DEFAULT_LAYER, String(format: I18n.tr(LAYER_NAME), 1))]
        return c
    }

    func message(_ text: String) { event = .message(I18n.tr(text)) }

    // MARK: dokular

    func texture(_ id: String) -> Texture? {
        if let t = textures.cached(id) { return t }
        let tex = textures
        Task.detached(priority: .utility) { [weak self] in
            if tex.texture(id) != nil { await MainActor.run { self?.renderTick += 1 } }
        }
        return nil
    }

    // MARK: geçmiş

    private func commit(_ next: Model3D) {
        guard let current = model else { return }
        if liveBase == nil && next != current { pushUndo(current) }
        model = next
        dirty = true
    }

    private func pushUndo(_ m: Model3D) {
        undoStack.append(m)
        while undoStack.count > Budget3D.historyLimit(Self.MAX_HISTORY) { undoStack.removeFirst() }
        redoStack.removeAll()
        refreshFlags()
    }

    func beginLive() { if liveBase == nil { liveBase = model } }

    func endLive() {
        guard let base = liveBase else { return }
        liveBase = nil
        guard let now = model else { return }
        if now.with(camera: base.camera) != base { pushUndo(base) }
    }

    func undo() {
        guard let current = model, let prev = undoStack.popLast() else { return }
        redoStack.append(current)
        model = prev.with(camera: current.camera)
        dirty = true
        cleanSelection(); refreshFlags()
    }

    func redo() {
        guard let current = model, let next = redoStack.popLast() else { return }
        undoStack.append(current)
        model = next.with(camera: current.camera)
        dirty = true
        cleanSelection(); refreshFlags()
    }

    private func refreshFlags() { canUndo = !undoStack.isEmpty; canRedo = !redoStack.isEmpty }

    private func cleanSelection() {
        guard let m = model else { return }
        let ids = Set(m.strokes.map(\.id)).union(m.parts.map(\.id))
        selection = selection.filter { ids.contains($0) }
    }

    // MARK: mod ve araç

    func setMode(_ m: SketchMode) {
        if mode == .SELECT && m != .SELECT { selection = [] }
        mode = m
    }
    func setTool(_ t: SketchTool) { tool = t; mode = .DRAW }
    func setBrush(_ b: Brush3D) { brush = b; tool = .PEN; mode = .DRAW }
    func setColor(_ c: Int32) { color = c }
    func setSize(_ px: Float) { size = px.coerceIn(1, 80) }
    func setOpacity(_ v: Float) { opacity = v.coerceIn(0.05, 1) }
    func setEraserSize(_ v: Float) { eraserSize = v.coerceIn(6, 60) }
    func setXform(_ x: SketchXform) { xform = x }
    func setAxis(_ a: Int) { axis = a.coerceIn(0, 2) }
    func setFingerDraw(_ v: Bool) { fingerDraw = v; fingerDrawTouched = true }

    /// Kalem görüldü: kullanıcı elle seçmediyse parmak çizimi kapanır.
    func stylusSeen() { if !fingerDrawTouched && fingerDraw { fingerDraw = false } }

    func gpuFailed() { if gpu { gpu = false; message(Self.GPU_FALLBACK) } }

    // MARK: görünüm

    func setViewport(_ w: Int, _ h: Int) { viewW = w; viewH = h }

    func currentView() -> View3? {
        guard let m = model, viewW > 0, viewH > 0 else { return nil }
        return View3.editor(m.camera, viewW, viewH)
    }

    var camera: Camera3 { model?.camera ?? Camera3() }

    private func setCamera(_ c: Camera3) {
        guard var m = model else { return }
        m.camera = c
        model = m
        dirty = true
    }

    func orbit(_ dYaw: Float, _ dPitch: Float) {
        var c = camera
        c.yaw = ((c.yaw + dYaw).truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)
        c.pitch = (c.pitch + dPitch).coerceIn(-89, 89)
        setCamera(c)
    }

    func zoom(_ factor: Float) { var c = camera; c.zoom = (c.zoom * factor).coerceIn(0.1, 30); setCamera(c) }

    func pan(_ dx: Float, _ dy: Float) {
        guard let view = currentView() else { return }
        var c = camera
        c.target = c.target + view.screenDelta(-dx, -dy)
        setCamera(c)
    }

    func setPerspective(_ p: Float) { var c = camera; c.persp = p.coerceIn(0, 1); setCamera(c) }

    /// Hazır açılar: 0 izometrik, 1 ön, 2 yan, 3 üst.
    func presetView(_ kind: Int) {
        guard let m = model else { return }
        let center = bounds(m)?.center ?? plane.origin
        var c = camera
        switch kind {
        case 1: c.yaw = 0; c.pitch = 0
        case 2: c.yaw = 90; c.pitch = 0
        case 3: c.yaw = 0; c.pitch = 89
        default: c.yaw = 35; c.pitch = 24
        }
        c.target = center
        setCamera(c)
    }

    func fitView() {
        guard let m = model, let b = bounds(m) else { return }
        let r = max(b.radius, 0.2)
        var c = camera
        c.target = b.center
        c.zoom = (View3.REFERENCE_EXTENT / (r * 1.15)).coerceIn(0.1, 30)
        setCamera(c)
    }

    private func bounds(_ m: Model3D) -> Box3? {
        var noStrokes = m
        noStrokes.strokes = []
        var box = ModelEdit.modelBounds(noStrokes)
        for s in m.strokes {
            for i in 0..<s.count {
                let p = V3(s.points[3 * i], s.points[3 * i + 1], s.points[3 * i + 2])
                let b = Box3(p, p)
                box = box?.union(b) ?? b
            }
        }
        return box
    }

    /// Bir ekran noktasının düzlemin merkezindeki dünya boyu.
    func pixelToWorld() -> Float {
        guard let view = currentView() else { return 0.01 }
        let s = view.project(plane.origin)
        let k: Float = s[3] > 0 ? s[3] : 1
        return 1 / (view.scale * k)
    }

    func setScene(_ scene: SceneSettings) { guard var m = model else { return }; m.scene = scene; commit(m) }

    // MARK: düzlem

    /// Görünen düzlemin yarı kenarı (çizimin boyuna göre).
    func planeHalf() -> Float { max(1.2, (model.flatMap { bounds($0)?.radius } ?? 0) * 1.3) }

    func setPlanePreset(_ kind: PlaneSnap.Preset) {
        plane = PlaneSnap.preset(kind, through: plane.origin, view: currentView())
        persistPlane()
    }

    /// Düzlemi dokunulan yere yapıştırır: çizgi ucu / kesişim (yön korunur) ya da yüz.
    @discardableResult
    func snapPlaneAt(_ x: Float, _ y: Float, _ radiusPx: Float) -> Bool {
        guard let m = model, let view = currentView() else { return false }
        let visible = m.visibleLayers()
        if let p = PlaneSnap.snapPoint(visible, view, x, y, radiusPx) {
            var pl = plane
            pl.origin = p; pl.id = "point"
            plane = pl
            persistPlane(); message(Self.MSG_SNAP_POINT); return true
        }
        if let hit = Pick3.pick(visible, view, x, y) {
            plane = PlaneSnap.onFace(visible, hit)
            persistPlane(); message(Self.MSG_SNAP_FACE); return true
        }
        message(Self.MSG_SNAP_NONE)
        return false
    }

    /// Düzlemi normali boyunca kaydırır (ekrandaki sürükleme noktası).
    func dragPlane(_ dyPx: Float) { plane = PlaneSnap.offset(plane, -dyPx * pixelToWorld()) }

    func endPlaneDrag() { persistPlane() }

    func rotatePlane(_ axis: Int, _ deg: Float) { plane = PlaneSnap.rotate(plane, axis, deg); persistPlane() }

    func planeToCamera() {
        guard let view = currentView() else { return }
        plane = PlaneSnap.faceCamera(plane, view)
        persistPlane()
    }

    func planeTilted() -> Bool { currentView().map { PlaneSnap.tooTilted(plane, $0) } ?? false }

    /// Düzlem kayda girer (geri alma geçmişine girmez).
    private func persistPlane() {
        guard var m = model else { return }
        m.plane = plane
        model = m
        dirty = true
    }

    // MARK: çizim

    private func layerWritable() -> Bool {
        guard let m = model else { return false }
        if m.layerLocked(activeLayer) { message(Self.MSG_LAYER_LOCKED); return false }
        if m.layerHidden(activeLayer) { message(Self.MSG_LAYER_HIDDEN); return false }
        return true
    }

    /// Ekran noktasının çizim düzlemindeki dünya karşılığı.
    func planePoint(_ x: Float, _ y: Float) -> V3? { currentView().flatMap { PlaneSnap.project(plane, $0, x, y) } }

    /// Ekranda çizilen izi ([xy] çiftler) düzleme izdüşürüp çizgi ekler. [pressures] kalem basıncı (parmakta nil);
    /// [straight] basılı tutulunca düz çizgi.
    @discardableResult
    func finishStroke(_ xy: [Float], _ pressures: [Float]?, straight: Bool) -> String? {
        guard let m = model, let view = currentView(), xy.count >= 4, layerWritable() else { return nil }
        let src = straight ? [xy[0], xy[1], xy[xy.count - 2], xy[xy.count - 1]] : Self.smooth(xy)
        let n = src.count / 2
        var world: [Float] = []
        world.reserveCapacity(n * 3)
        for i in 0..<n {
            guard let p = PlaneSnap.project(plane, view, src[2 * i], src[2 * i + 1]) else { continue }
            world += [p.x, p.y, p.z]
        }
        if world.count < 6 { return nil }
        let count = world.count / 3
        let b = brush
        var press: [Float]? = nil
        if let pr = pressures, !straight, pr.count * 2 == xy.count { press = Self.resample(pr, count) }
        let widths = Self.widthsFor(b, count, press)
        let op = b == .MARKER ? min(opacity, 0.6) : opacity
        let w = size * (b == .MARKER ? 1.8 : 1) * pixelToWorld()
        let id = UUID().uuidString.lowercased()
        let stroke = Stroke3(id, world, color, w, time: Int64(Date().timeIntervalSince1970 * 1000), widths: widths, opacity: op,
                             brush: b.rawValue, plane: plane.id, layer: activeLayer)
        var next = m
        next.strokes.append(stroke)
        commit(next)
        return id
    }

    /// Hafif yumuşatma (3 noktalı ortalama, uçlar korunur).
    static func smooth(_ xy: [Float]) -> [Float] {
        let n = xy.count / 2
        if n < 3 { return xy }
        var out = xy
        for i in 1..<(n - 1) { for c in 0...1 { out[2 * i + c] = (xy[2 * (i - 1) + c] + 2 * xy[2 * i + c] + xy[2 * (i + 1) + c]) / 4 } }
        return out
    }

    static func resample(_ a: [Float], _ n: Int) -> [Float] {
        if a.count == n { return a }
        return (0..<n).map { i in a[Int((Float(i) / Float(max(1, n - 1))) * Float(a.count - 1)).coerceIn(0, a.count - 1)] }
    }

    /// Fırçanın nokta başına kalınlık çarpanı: basınç (varsa) ve uç incelmesi.
    static func widthsFor(_ b: Brush3D, _ n: Int, _ press: [Float]?) -> [Float]? {
        if b == .MARKER || b == .WIDE { return nil }
        let taper: Float = b == .INK ? 0.22 : 0.08
        if press == nil && b == .PEN { return nil }
        return (0..<n).map { i in
            let t: Float = n <= 1 ? 0.5 : Float(i) / Float(n - 1)
            let edge = min(t, 1 - t)
            let tp: Float = edge < taper ? 0.35 + 0.65 * (edge / taper) : 1
            var p: Float = 1
            if let pr = press {
                let v = pr[i].coerceIn(0, 1)
                p = b == .INK ? 0.2 + 0.8 * pow(v, 1.4) : 0.3 + 0.7 * v
            }
            return (tp * p).coerceIn(0.08, 1.6)
        }
    }

    func beginErase() { if !erasing { erasing = true; beginLive() } }

    /// Silgi: ekranda ([x], [y]) çevresindeki [radiusPx] içinde kalan çizgi noktalarını siler; çizgi bölünür.
    func eraseAt(_ x: Float, _ y: Float, _ radiusPx: Float) {
        guard let m = model, let view = currentView() else { return }
        var s = [Float](repeating: 0, count: 4)
        let r2 = radiusPx * radiusPx
        var changed = false
        var out: [Stroke3] = []
        out.reserveCapacity(m.strokes.count)
        for st in m.strokes {
            if m.layerLocked(st.layer) || m.layerHidden(st.layer) { out.append(st); continue }
            var keep = [Bool](repeating: true, count: st.count)
            var any = false
            for i in 0..<st.count {
                view.project(st.points[3 * i], st.points[3 * i + 1], st.points[3 * i + 2], &s)
                let hit = s[3] > 0 && (s[0] - x) * (s[0] - x) + (s[1] - y) * (s[1] - y) <= r2
                keep[i] = !hit
                if hit { any = true }
            }
            if !any { out.append(st); continue }
            changed = true
            var i = 0, piece = 0
            while i < st.count {
                if !keep[i] { i += 1; continue }
                var j = i
                while j < st.count && keep[j] { j += 1 }
                if j - i >= 2 {
                    let pts = Array(st.points[(3 * i)..<(3 * j)])
                    let ws: [Float]? = st.widths.map { Array($0[i..<j]) }
                    let nid = piece == 0 ? st.id : "\(st.id)-\(UUID().uuidString.prefix(6).lowercased())"
                    out.append(st.copy(points: pts, widths: .some(ws), id: nid))
                    piece += 1
                }
                i = j
            }
        }
        if changed { var next = m; next.strokes = out; commit(next) }
    }

    func endErase() { if erasing { erasing = false; endLive() } }

    func clearDrawings() {
        guard var m = model else { return }
        let src = m
        m.strokes = src.strokes.filter { src.layerLocked($0.layer) }
        commit(m)
    }

    // MARK: seçim

    func tapSelect(_ x: Float, _ y: Float, _ radiusPx: Float, add: Bool = false) {
        guard let m = model, let view = currentView() else { return }
        let id = hitId(m.visibleLayers(), view, x, y, radiusPx)
        let cur = selection
        if let id {
            selection = add ? (cur.contains(id) ? cur.subtracting([id]) : cur.union([id])) : [id]
        } else if !add {
            selection = []
        }
    }

    private func hitId(_ m: Model3D, _ view: View3, _ x: Float, _ y: Float, _ radiusPx: Float) -> String? {
        var editable = m
        editable.strokes = m.strokes.filter { !m.layerLocked($0.layer) }
        if let i = Pick3.stroke(editable, view, x, y, radiusPx) { return editable.strokes[i].id }
        guard let hit = Pick3.pick(m, view, x, y) else { return nil }
        let part = m.parts[hit.part]
        return m.layerLocked(part.layer) ? nil : part.id
    }

    /// Dokunuş seçili bir öğenin üstünde mi (sürükleme onu dönüştürür).
    func onSelection(_ x: Float, _ y: Float, _ radiusPx: Float) -> Bool {
        guard let m = model, let view = currentView(), let id = hitId(m.visibleLayers(), view, x, y, radiusPx) else { return false }
        return selection.contains(id)
    }

    func selectAll() {
        guard let m = model else { return }
        let free: (String?) -> Bool = { !m.layerLocked($0) && !m.layerHidden($0) }
        selection = Set(m.strokes.filter { free($0.layer) }.map(\.id) + m.parts.filter { free($0.layer) }.map(\.id))
    }

    func clearSelection() { selection = [] }

    func deleteSelected() {
        guard var m = model, !selection.isEmpty else { return }
        let sel = selection
        m.strokes.removeAll { sel.contains($0.id) }
        m.parts.removeAll { sel.contains($0.id) }
        commit(m)
        selection = []
    }

    func duplicateSelected() {
        guard var m = model, !selection.isEmpty else { return }
        let sel = selection
        let d = plane.u * 0.15 + plane.v * -0.15
        var newIds = Set<String>()
        let strokes = m.strokes.filter { sel.contains($0.id) }.map { s -> Stroke3 in
            let id = UUID().uuidString.lowercased(); newIds.insert(id)
            return s.copy(points: Self.translate(s.points, d), id: id)
        }
        let parts = m.parts.filter { sel.contains($0.id) }.map { p -> Part in
            let id = UUID().uuidString.lowercased(); newIds.insert(id)
            var q = p; q.id = id; q.position = p.position + d
            return q
        }
        m.strokes += strokes
        m.parts += parts
        commit(m)
        selection = newIds
    }

    /// Seçimin merkezi (döndürme ve ölçekleme bunun çevresinde).
    func selectionCenter() -> V3? {
        guard let m = model else { return nil }
        let sel = selection
        var sx: Float = 0, sy: Float = 0, sz: Float = 0, n = 0
        for s in m.strokes where sel.contains(s.id) {
            for i in 0..<s.count { sx += s.points[3 * i]; sy += s.points[3 * i + 1]; sz += s.points[3 * i + 2]; n += 1 }
        }
        for p in m.parts where sel.contains(p.id) { let c = ModelEdit.worldBounds(p).center; sx += c.x; sy += c.y; sz += c.z; n += 1 }
        return n == 0 ? nil : V3(sx / Float(n), sy / Float(n), sz / Float(n))
    }

    /// Seçimi ekrandaki sürüklemeye göre çizim düzleminde taşır.
    func moveSelectedBy(_ dxPx: Float, _ dyPx: Float) {
        guard let view = currentView(), let c = selectionCenter() else { return }
        let s = view.project(c)
        var pl = plane
        pl.origin = c
        guard let a = PlaneSnap.project(pl, view, s[0], s[1]), let b = PlaneSnap.project(pl, view, s[0] + dxPx, s[1] + dyPx) else { return }
        moveSelected(b - a)
    }

    func moveSelected(_ d: V3) {
        updateSelected({ Self.translate($0, d) }) { p in var q = p; q.position = p.position + d; return q }
    }

    /// Seçimi [axis] (0 X, 1 Y, 2 Z) dünya ekseni çevresinde döndürür.
    func rotateSelected(_ deg: Float, axis ax: Int? = nil) {
        guard let c = selectionCenter() else { return }
        let a = ax ?? axis
        let k = a == 0 ? V3(1, 0, 0) : a == 1 ? V3.UP : V3(0, 0, 1)
        updateSelected({ pts in
            var out = pts
            for i in 0..<(pts.count / 3) {
                let p = PlaneSnap.rotateAround(V3(pts[3 * i], pts[3 * i + 1], pts[3 * i + 2]) - c, k, deg) + c
                out[3 * i] = p.x; out[3 * i + 1] = p.y; out[3 * i + 2] = p.z
            }
            return out
        }) { p in
            var q = p
            q.position = PlaneSnap.rotateAround(p.position - c, k, deg) + c
            q.rotation = p.rotation.with(a, p.rotation.get(a) + deg)
            return q
        }
    }

    func scaleSelected(_ f: Float) {
        guard let c = selectionCenter() else { return }
        updateSelected({ pts in
            var out = pts
            for i in 0..<(pts.count / 3) {
                out[3 * i] = c.x + (pts[3 * i] - c.x) * f
                out[3 * i + 1] = c.y + (pts[3 * i + 1] - c.y) * f
                out[3 * i + 2] = c.z + (pts[3 * i + 2] - c.z) * f
            }
            return out
        }, widthScale: f) { p in var q = p; q.position = c + (p.position - c) * f; q.scale = p.scale * f; return q }
    }

    /// Çevir: seçim çizim düzleminin u ekseni boyunca aynalanır.
    func flipSelected() {
        guard let c = selectionCenter() else { return }
        let u = plane.u.normalized()
        updateSelected({ pts in
            var out = pts
            for i in 0..<(pts.count / 3) {
                let p = V3(pts[3 * i], pts[3 * i + 1], pts[3 * i + 2])
                let q = p - u * (2 * (p - c).dot(u))
                out[3 * i] = q.x; out[3 * i + 1] = q.y; out[3 * i + 2] = q.z
            }
            return out
        }) { p in
            var q = p
            q.position = p.position - u * (2 * (p.position - c).dot(u))
            q.scale = p.scale.mul(V3(abs(u.x) > 0.5 ? -1 : 1, abs(u.y) > 0.5 ? -1 : 1, abs(u.z) > 0.5 ? -1 : 1))
            return q
        }
    }

    /// Seçili cisimleri zemine oturtur.
    func dropSelectedToGround() { updateSelected({ $0 }) { ModelEdit.dropToGround($0) } }

    private func updateSelected(_ strokeOp: ([Float]) -> [Float], widthScale: Float = 1, _ partOp: (Part) -> Part) {
        guard var m = model, !selection.isEmpty else { return }
        let sel = selection
        m.strokes = m.strokes.map { sel.contains($0.id) ? $0.copy(points: strokeOp($0.points), width: $0.width * widthScale) : $0 }
        m.parts = m.parts.map { sel.contains($0.id) ? partOp($0) : $0 }
        commit(m)
    }

    static func translate(_ pts: [Float], _ d: V3) -> [Float] {
        var out = pts
        for i in 0..<(pts.count / 3) { out[3 * i] += d.x; out[3 * i + 1] += d.y; out[3 * i + 2] += d.z }
        return out
    }

    /// Seçimin rengi (çizgi rengi, cisim malzemesi).
    func colorSelected(_ c: Int32) {
        guard var m = model, !selection.isEmpty else { return }
        let sel = selection
        m.strokes = m.strokes.map { sel.contains($0.id) ? $0.copy(color: c) : $0 }
        m.parts = m.parts.map { p in
            guard sel.contains(p.id) else { return p }
            var q = p; q.material.color = c; q.material.texture = nil
            return q
        }
        commit(m)
    }

    // MARK: cisimler

    func addPrimitive(_ kind: PrimitiveKind) {
        guard var m = model, layerWritable() else { return }
        let id = UUID().uuidString.lowercased()
        let at = plane.origin
        var part = ModelEdit.dropToGround(ModelEdit.newPart(kind, id, m.parts.count, V3(at.x, 0, at.z)))
        part.material = Material(color: Int32(bitPattern: 0xFFF5_F1E8), kind: .MATTE)
        part.layer = activeLayer
        m.parts.append(part)
        commit(m)
        selection = [id]
        mode = .SELECT
    }

    // MARK: katmanlar

    func setActiveLayer(_ id: String) { activeLayer = id }

    func addLayer() {
        guard var m = model else { return }
        let id = String(UUID().uuidString.lowercased().prefix(8))
        m.layers.append(Layer3(id, String(format: I18n.tr(Self.LAYER_NAME), m.layers.count + 1)))
        commit(m)
        activeLayer = id
    }

    func toggleLayerVisible(_ id: String) { updateLayer(id) { var l = $0; l.visible.toggle(); return l } }
    func toggleLayerLocked(_ id: String) { updateLayer(id) { var l = $0; l.locked.toggle(); return l } }
    func renameLayer(_ id: String, _ name: String) {
        let n = name.trimmingCharacters(in: .whitespaces)
        updateLayer(id) { var l = $0; if !n.isEmpty { l.name = n }; return l }
    }

    private func updateLayer(_ id: String, _ op: (Layer3) -> Layer3) {
        guard var m = model else { return }
        m.layers = m.layers.map { $0.id == id ? op($0) : $0 }
        commit(m)
        cleanSelection()
    }

    /// Katmanı içindekilerle siler (son katman silinmez).
    func deleteLayer(_ id: String) {
        guard var m = model, m.layers.count > 1, let first = m.layers.first?.id else { return }
        func owns(_ l: String?) -> Bool { (l ?? first) == id }
        m.layers.removeAll { $0.id == id }
        m.strokes.removeAll { owns($0.layer) }
        m.parts.removeAll { owns($0.layer) }
        commit(m)
        if activeLayer == id { activeLayer = m.layers[0].id }
        cleanSelection()
    }

    /// Katmanı listede yukarı (−1) ya da aşağı (+1) taşır.
    func moveLayer(_ id: String, _ dir: Int) {
        guard var m = model, let i = m.layers.firstIndex(where: { $0.id == id }) else { return }
        let j = i + dir
        guard j >= 0, j < m.layers.count else { return }
        m.layers.swapAt(i, j)
        commit(m)
    }

    /// Seçimi verilen katmana taşır.
    func moveSelectionToLayer(_ id: String) {
        guard var m = model, !selection.isEmpty else { return }
        let sel = selection
        m.strokes = m.strokes.map { sel.contains($0.id) ? $0.copy(layer: .some(id)) : $0 }
        m.parts = m.parts.map { p in guard sel.contains(p.id) else { return p }; var q = p; q.layer = id; return q }
        commit(m)
    }

    // MARK: kaydetme ve resim

    func rename(_ name: String) {
        guard var m = model else { return }
        let n = name.trimmingCharacters(in: .whitespaces)
        if !n.isEmpty { m.name = n }
        model = m
        dirty = true
    }

    func save(exitAfter: Bool = false) {
        guard let m = model else { return }
        Task {
            await repository?.save(m)
            dirty = false
            event = .saved(m.name)
            if exitAfter { event = .exit }
        }
    }

    func saveAs(_ name: String) {
        guard let m = model, let repo = repository else { return }
        let n = name.trimmingCharacters(in: .whitespaces)
        Task {
            let copy = await repo.saveCopy(m, n.isEmpty ? m.name : n)
            model = copy
            dirty = false
            event = .saved(copy.name)
        }
    }

    /// Resim olarak dışa aktarma (yazılım çizici; arka planda).
    func renderImage(_ e: ImageExport, previewSide: Int? = nil) async -> CGImage? {
        guard let m0 = model else { return nil }
        let m = m0.visibleLayers()
        let long = (previewSide ?? e.size).coerceIn(64, Budget3D.exportMaxSide)
        let w = e.aspect >= 1 ? long : max(16, Int(Float(long) * e.aspect))
        let h = e.aspect >= 1 ? max(16, Int(Float(long) / e.aspect)) : long
        let ref = currentView()
        let tex = textures
        return await Task.detached(priority: .userInitiated) { () -> CGImage? in
            tex.preload(TextureRepository.ids(m))
            let maxSs = Int((Double(Budget3D.exportPixels) / Double(w * h)).squareRoot()).coerceIn(1, 3)
            let ss = min(e.smoothness.coerceIn(1, 3), maxSs)
            let bg: Int32 = e.jpeg && e.background == 0 ? -1 : e.background
            var mm = m
            mm.scene.edges = e.edges; mm.scene.shadow = e.shadow; mm.scene.ground = e.ground; mm.scene.background = bg
            let view: View3
            if e.fit { view = View3.fit(mm, m.camera, w, h, 0.06) } else if let ref {
                let k = min(Float(w) / Float(ref.width), Float(h) / Float(ref.height))
                view = View3(m.camera, w, h, ref.scale * k, Float(w) / 2, Float(h) / 2)
            } else { view = View3.editor(m.camera, w, h) }
            let f = Renderer3D.render(mm, view, RenderOptions(textures: { tex.cached($0) }, supersample: ss, ground: e.ground, shadow: e.shadow, background: UInt32(bitPattern: bg)))
            return Bitmaps.image(argb: f.color, w, h)
        }.value
    }

    /// Ekran noktaları arası uzaklık (testler).
    func screenDistance(_ a: V3, _ b: V3) -> Float {
        guard let v = currentView() else { return 0 }
        let p = v.project(a), q = v.project(b)
        return ((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1])).squareRoot()
    }
}
