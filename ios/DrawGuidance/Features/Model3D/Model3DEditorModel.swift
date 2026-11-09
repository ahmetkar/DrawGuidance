import CoreGraphics
import Foundation
import SwiftUI

/// v23 3B düzenleyicinin görünüm modeli (Android `Model3DEditorViewModel`): model, seçim, araç, kamera, geri al/yinele.
/// Dokunuşlar görünümün izdüşümüyle ([currentView], ekran noktası) ışın atılarak ([Pick3]) parçaya, yüze, kenara ve
/// köşeye çevrilir. Sürükleme süren değişiklikler ([beginLive]…[endLive]) geçmişe tek adım olarak girer.
@MainActor
final class Model3DEditorModel: ObservableObject {
    static let DEFAULT_NAME = "Yeni 3B şekil"
    static let GROUP = "Grup"
    private static let MAX_HISTORY = 60
    private static let COALESCE_MS: Int64 = 1500
    private static let ROUND_SEGMENTS = 3
    private static let SOLID_MIN_WIDTH: Float = 12
    private static let SOLID_DEFAULT_WIDTH: Float = 18
    private static let SUGGEST_MIN_PX: Float = 48
    private static let GLOBAL_PANELS: Set<Panel3> = [.PARTS, .SETTINGS]

    let repository: Model3DRepository
    let textures: TextureRepository
    let results: Model3DResults
    let prefs: Model3DPrefs
    private let modelId: String?
    let canvasId: String?
    private let strokeId: String?
    private let initialName: String?

    @Published private(set) var model: Model3D?
    @Published var selection = Selection3()
    @Published private(set) var tool: Tool3 = .MOVE
    @Published private(set) var mode: Mode3 = .SELECT
    @Published private(set) var axis = -1
    @Published private(set) var panel: Panel3?
    @Published private(set) var plane: DrawPlane = .FRONT
    @Published private(set) var brushColor: Int32 = Int32(bitPattern: 0xFF26_3238)
    @Published private(set) var brushWidth: Float = 6
    @Published private(set) var paint = Material(color: Int32(bitPattern: 0xFFE0_7A5F))
    @Published private(set) var paintScope: PaintScope = .FACE
    @Published private(set) var dirty = false
    @Published private(set) var canUndo = false
    @Published private(set) var canRedo = false
    @Published private(set) var op: SliderOp?
    @Published private(set) var quality: Int
    @Published private(set) var renderTick = 0
    @Published private(set) var faceMode: FaceMode = .SINGLE
    @Published private(set) var busy: String?
    @Published private(set) var slice: (SliceAxis, Float) = (.Y, 0)
    @Published private(set) var holeRatio: Float = 0.4
    @Published private(set) var brush = Brush3()
    @Published private(set) var suggestion: SuggestUi?
    @Published private(set) var suggestEnabled: Bool
    @Published private(set) var canvasAiOn = true
    @Published private(set) var eraserSize: Float = 24
    /// Olaylar (kaydedildi, çıkış, ileti): görünüm dinler.
    @Published private(set) var event: (Int, Event3)?
    private var eventSeq = 0

    var fromCanvas: Bool { canvasId != nil }

    init(repository: Model3DRepository, textures: TextureRepository = .shared, results: Model3DResults = .shared, prefs: Model3DPrefs = .shared,
         modelId: String?, canvasId: String? = nil, strokeId: String? = nil, initialName: String? = nil,
         canvasAi: ((String) async -> Bool)? = nil) {
        self.repository = repository; self.textures = textures; self.results = results; self.prefs = prefs
        self.modelId = modelId?.isEmpty == true ? nil : modelId
        self.canvasId = canvasId?.isEmpty == true ? nil : canvasId
        self.strokeId = strokeId?.isEmpty == true ? nil : strokeId
        self.initialName = initialName?.isEmpty == true ? nil : initialName
        quality = prefs.quality
        suggestEnabled = prefs.suggest
        Task { await self.loadInitial() }
        if let id = self.canvasId, let read = canvasAi {
            Task {
                let on = await read(id)
                self.canvasAiOn = on
                if !on { self.suggestion = nil }
            }
        }
    }

    private func loadInitial() async {
        let catalog = modelId.flatMap { $0.hasPrefix(Catalog3D.PREFIX) ? $0 : nil }
        var loaded: Model3D?
        if let catalog { loaded = repository.fromCatalog(catalog) } else if let id = modelId { loaded = await repository.load(id) }
        let m = loaded ?? Model3D((catalog == nil ? modelId : nil) ?? Self.newId(), initialName ?? Self.DEFAULT_NAME.tr,
                                  [ModelEdit.newPart(.CUBE, Self.newId(), 0)])
        let tex = textures
        await Task.detached { tex.preload(TextureRepository.ids(m)) }.value
        model = m
        if loaded == nil || catalog != nil { dirty = true }
    }

    nonisolated static func newId() -> String { UUID().uuidString.lowercased() }
    nonisolated static func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    func emit(_ e: Event3) { eventSeq += 1; event = (eventSeq, e) }
    func message(_ text: String) { emit(.message(text)) }

    // MARK: çizim tercihleri

    func setQuality(_ q: Int) { prefs.quality = q; quality = prefs.quality }

    // MARK: görünüm

    private var viewW = 0, viewH = 0

    /// Sahne alanının boyu (nokta; dokunuşlar bu görünüme göre çevrilir).
    func setViewport(_ w: Int, _ h: Int) { viewW = w; viewH = h }

    func currentView() -> View3? {
        guard let m = model, viewW > 0, viewH > 0 else { return nil }
        return View3.editor(m.camera, viewW, viewH)
    }

    /// Çizim için doku (bellekte değilse nil; yükleme başlar). Her iş parçacığından çağrılabilir.
    nonisolated func texture(_ id: String) -> Texture? {
        if let t = textures.cached(id) { return t }
        let tex = textures
        Task.detached(priority: .utility) {
            if tex.texture(id) != nil { await MainActor.run { self.renderTick += 1 } }
        }
        return nil
    }

    var textureItems: [TextureItem] { textures.items }

    // MARK: geçmiş

    private var undoStack: [Model3D] = []
    private var redoStack: [Model3D] = []
    private var lastCoalesce: String?
    private var lastCoalesceTime: Int64 = 0
    private var liveBase: Model3D?

    /// Modeli değiştirir (geri alınabilir). Aynı [coalesce] anahtarlı ardışık değişiklikler tek adım olur.
    private func commit(_ next0: Model3D, _ coalesce: String? = nil) {
        guard let current = model else { return }
        let next = normalizeGroups(next0)
        if liveBase != nil { model = next; dirty = true; return }
        if next == current { return }
        let now = Self.nowMs()
        let merge = coalesce != nil && coalesce == lastCoalesce && now - lastCoalesceTime < Self.COALESCE_MS
        if !merge { pushUndo(current) }
        lastCoalesce = coalesce
        lastCoalesceTime = now
        model = next
        dirty = true
    }

    private func pushUndo(_ m: Model3D) {
        undoStack.append(m)
        if undoStack.count > Self.MAX_HISTORY { undoStack.removeFirst() }
        redoStack.removeAll()
        refreshFlags()
    }

    func beginLive() { if liveBase == nil { liveBase = model } }

    func endLive() {
        guard let base = liveBase else { return }
        liveBase = nil
        lastCoalesce = nil
        guard let now = model else { return }
        if now.with(camera: base.camera) != base { pushUndo(base) }
    }

    func endCoalesce() { lastCoalesce = nil }

    func undo() {
        cancelOp(); dropSuggestion()
        guard let current = model, let prev = undoStack.popLast() else { return }
        redoStack.append(current)
        model = prev.with(camera: current.camera)
        lastCoalesce = nil; dirty = true
        cleanSelection(); refreshFlags()
    }

    func redo() {
        cancelOp(); dropSuggestion()
        guard let current = model, let next = redoStack.popLast() else { return }
        undoStack.append(current)
        model = next.with(camera: current.camera)
        lastCoalesce = nil; dirty = true
        cleanSelection(); refreshFlags()
    }

    private func refreshFlags() { canUndo = !undoStack.isEmpty; canRedo = !redoStack.isEmpty }

    private func cleanSelection() {
        guard let m = model else { return }
        let s = selection
        let parts = s.parts.filter { id in m.parts.contains { $0.id == id } }
        let primary = parts.first.flatMap { m.part($0) }
        let n = primary?.mesh.vertexCount ?? 0
        let fc = primary?.mesh.faceCount ?? 0
        selection = Selection3(parts,
                               faces: s.faces.filter { primary != nil && $0 < fc },
                               vertices: s.vertices.filter { $0 < n },
                               edges: s.edges.filter { e in e.a < n && e.b < n && primary.map { !MeshOps.facesOfEdge($0.mesh, e).isEmpty } == true })
    }

    // MARK: araçlar ve paneller

    private var lastTool: [Mode3: Tool3] = [:]

    func setTool(_ t: Tool3) {
        applyOp()
        let target = mode.tools.contains(t) ? mode : Mode3.of(t)
        if target != mode { enterMode(target) }
        lastTool[target] = t
        tool = t
        var s = selection
        if !(t == .FACE || t == .PAINT) { s.faces = OrderedSet() }
        if !(t == .EDGE || t == .POINT) { s.vertices = OrderedSet() }
        if t != .EDGE { s.edges = OrderedSet() }
        selection = s
        if t != .DRAW && panel == .BRUSH { panel = nil }
        if t == .DRAW && panel == nil { panel = .BRUSH }
    }

    func setMode(_ m: Mode3) {
        applyOp()
        if m == mode {
            if m == .ADD && panel == nil { panel = .ADD }
            return
        }
        enterMode(m)
        setTool(lastTool[m] ?? m.tools[0])
        if m == .ADD { panel = .ADD }
    }

    private func enterMode(_ m: Mode3) {
        if m != mode { dropSuggestion() }
        if m == .DRAW { prewarmSuggest() }
        mode = m
        if let p = panel, !Self.GLOBAL_PANELS.contains(p) { panel = nil }
    }

    func setAxis(_ a: Int) { axis = a }
    func togglePanel(_ p: Panel3) { panel = panel == p ? nil : p }
    func closePanel() { panel = nil }
    func setPlane(_ p: DrawPlane) { plane = p }
    func setBrushColor(_ c: Int32) { brushColor = c }
    func setBrushWidth(_ w: Float) { brushWidth = w.coerceIn(1, 60) }
    func setPaint(_ m: Material) { paint = m }
    func setPaintScope(_ v: PaintScope) { paintScope = v; paintedRegion = nil }

    // MARK: kamera

    func camera() -> Camera3 { model?.camera ?? Camera3() }

    /// Kamera geri alma geçmişine girmez, kaydedilir.
    private func setCamera(_ c: Camera3) {
        guard let m = model else { return }
        model = m.with(camera: c)
        dirty = true
    }

    func orbit(_ dYaw: Float, _ dPitch: Float) {
        var c = camera()
        c.yaw = ((c.yaw + dYaw).truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)
        c.pitch = (c.pitch + dPitch).coerceIn(-89, 89)
        setCamera(c)
    }

    func zoom(_ factor: Float) { var c = camera(); c.zoom = (c.zoom * factor).coerceIn(0.1, 30); setCamera(c) }

    func pan(_ dx: Float, _ dy: Float) {
        guard let view = currentView() else { return }
        var c = camera()
        c.target = c.target + view.screenDelta(-dx, -dy)
        setCamera(c)
    }

    func setPerspective(_ p: Float) { var c = camera(); c.persp = p.coerceIn(0, 1); setCamera(c) }

    /// Hazır açılar: 0 izometrik, 1 ön, 2 yan, 3 üst.
    func presetView(_ kind: Int) {
        guard let m = model else { return }
        let center = ModelEdit.modelBounds(m)?.center ?? V3(0, 0.5, 0)
        var c = camera()
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
        guard let m = model, let b = ModelEdit.modelBounds(m) else { return }
        let r = b.radius.coerceAtLeast(0.2)
        var c = camera()
        c.target = b.center
        c.zoom = (View3.REFERENCE_EXTENT / (r * 1.15)).coerceIn(0.1, 30)
        setCamera(c)
    }

    // MARK: parçalar

    private func besideX(_ m: Model3D, _ width: Float = 1) -> Float {
        guard let b = ModelEdit.modelBounds(m) else { return 0 }
        return b.max.x + 0.2 + width / 2
    }

    private func afterAdd() {
        if tool == .VIEW || tool == .DRAW || tool == .ERASE { if mode == .ADD { tool = .MOVE } else { setTool(.MOVE) } }
        panel = nil
    }

    func addPart(_ kind: PrimitiveKind) {
        applyOp()
        guard var m = model else { return }
        let part = ModelEdit.newPart(kind, Self.newId(), m.parts.count, V3(m.parts.isEmpty ? 0 : besideX(m), 0, 0))
        m.parts.append(part)
        commit(m)
        selection = Selection3([part.id])
        afterAdd()
    }

    /// v24 hazır şeklin parçalarını (ve çizgilerini) modele ekler: yeni kimliklerle, mevcutların yanına.
    func addCatalog(_ catalogId: String) {
        applyOp()
        guard var m = model, let entry = Catalog3D.byId(catalogId) else { return }
        let item = entry.build()
        let b = ModelEdit.modelBounds(item)
        let dx: Float = m.parts.isEmpty && m.strokes.isEmpty ? 0 : besideX(m, b?.size.x ?? 1) - (b?.center.x ?? 0)
        let shift = V3(dx, 0, 0)
        let parts = item.parts.map { p -> Part in var c = p; c.id = Self.newId(); c.position = p.position + shift; return c }
        let strokes = item.strokes.map { s -> Stroke3 in
            var p = s.points
            for i in 0..<s.count { p[3 * i] += dx }
            return Stroke3(Self.newId(), p, s.color, s.width)
        }
        m.parts += parts
        m.strokes += strokes
        commit(m)
        selection = Selection3(parts.map(\.id))
        afterAdd()
        message(I18n.trf("{0} eklendi", item.name.tr))
    }

    /// v27 GLB/glTF dosyasının parçaları bu modele eklenir (yanına, tek grup olarak, seçili).
    func importGlbParts(_ bytes: [UInt8], _ name: String, _ maxTriangles: Int, _ saveSeparately: Bool) {
        applyOp()
        let repo = repository
        runHeavy("İçe aktarılıyor…".tr, "GLB dosyası içe aktarılamadı".tr) { [weak self] in
            let r = try await repo.importGlb(bytes, name, maxTriangles)
            if saveSeparately { var c = r.model; c.id = Self.newId(); await repo.save(c) }
            let item = r.model
            return { mm in
                var mm = mm
                let b = ModelEdit.modelBounds(item)
                let dx: Float = mm.parts.isEmpty && mm.strokes.isEmpty ? 0 : (self?.besideX(mm, b?.size.x ?? 1) ?? 0) - (b?.center.x ?? 0)
                let gid: String? = item.parts.count >= 2 ? Self.newId() : nil
                let parts = item.parts.map { p -> Part in var c = p; c.id = Self.newId(); c.position = p.position + V3(dx, 0, 0); c.group = gid; return c }
                self?.selection = Selection3(parts.map(\.id))
                self?.message(saveSeparately ? I18n.trf("\"{0}\" eklendi ve 3B Şekillerim'e ayrıca kaydedildi ({1} üçgen)", item.name, r.triangles)
                              : I18n.trf("\"{0}\" eklendi ({1} üçgen)", item.name, r.triangles))
                mm.parts += parts
                if let gid { mm.groups.append(Group3(id: gid, name: item.name)) }
                return mm
            }
        }
        panel = nil
    }

    func selectPart(_ id: String, add: Bool = false) {
        applyOp()
        let s = selection
        if add { selection = Selection3(s.parts.contains(id) ? s.parts.filter { $0 != id } : s.parts + [id]) } else { selection = Selection3([id]) }
    }

    func clearSelection() { applyOp(); selection = Selection3() }

    func deleteSelected() {
        applyOp()
        guard var m = model else { return }
        let ids = Set(selection.parts)
        if ids.isEmpty { return }
        m.parts.removeAll { ids.contains($0.id) }
        commit(m)
        selection = Selection3()
    }

    func duplicateSelected() {
        applyOp()
        guard var m = model else { return }
        let ids = selection.parts
        if ids.isEmpty { return }
        var newGroups: [String: Group3] = [:]
        var groupOrder: [String] = []
        let copies = ids.compactMap { m.part($0) }.map { p -> Part in
            var gid: String?
            if let old = p.group {
                if newGroups[old] == nil {
                    newGroups[old] = Group3(id: Self.newId(), name: (m.groups.first { $0.id == old }?.name ?? Self.GROUP.tr) + " (2)")
                    groupOrder.append(old)
                }
                gid = newGroups[old]?.id
            }
            var c = ModelEdit.duplicate(p, Self.newId())
            c.position = c.position + V3(0.3, 0, 0.3)
            c.group = gid
            return c
        }
        m.parts += copies
        m.groups += groupOrder.compactMap { newGroups[$0] }
        commit(m)
        selection = Selection3(copies.map(\.id))
    }

    /// Seçili parçaları tek parçada birleştirir.
    func mergeSelected() {
        applyOp()
        guard var m = model else { return }
        let parts = selection.parts.compactMap { m.part($0) }
        if parts.count < 2 { return }
        guard let merged = ModelEdit.merge(parts, Self.newId(), parts[0].name) else { return }
        let ids = Set(parts.map(\.id))
        let at = m.parts.firstIndex { ids.contains($0.id) } ?? 0
        var rest = m.parts.filter { !ids.contains($0.id) }
        rest.insert(merged, at: at.coerceIn(0, rest.count))
        m.parts = rest
        commit(m)
        selection = Selection3([merged.id])
    }

    func renamePart(_ id: String, _ name: String) {
        guard let m = model, var p = m.part(id) else { return }
        if !name.trimmingCharacters(in: .whitespaces).isEmpty { p.name = name }
        commit(m.replacePart(p))
    }

    func togglePartVisible(_ id: String) {
        guard let m = model, var p = m.part(id) else { return }
        p.visible.toggle()
        commit(m.replacePart(p))
    }

    func dropSelectedToGround() { updateSelectedParts("ground") { ModelEdit.dropToGround($0) } }

    func movePartInList(_ id: String, _ direction: Int) {
        guard var m = model, let i = m.parts.firstIndex(where: { $0.id == id }) else { return }
        let j = i + direction
        guard m.parts.indices.contains(j) else { return }
        let p = m.parts.remove(at: i)
        m.parts.insert(p, at: j)
        commit(m)
    }

    private func updateSelectedParts(_ coalesce: String?, _ block: (Part) -> Part) {
        guard var m = model else { return }
        let ids = Set(selection.parts)
        if ids.isEmpty { return }
        m.parts = m.parts.map { ids.contains($0.id) ? block($0) : $0 }
        commit(m, coalesce)
    }

    // MARK: dönüşüm

    func moveSelected(_ d: V3, coalesce: String? = "move") { updateSelectedParts(coalesce) { var c = $0; c.position = $0.position + d; return c } }

    func rotateSelected(_ deg: Float, axis: Int? = nil, coalesce: String? = "rotate") {
        let ax = axis ?? self.axis
        let a = (0...2).contains(ax) ? ax : 1
        guard let pivot = selectionPivot() else {
            updateSelectedParts(coalesce) { p in
                var c = p
                let v = ((p.rotation.get(a) + deg).truncatingRemainder(dividingBy: 360) + 540).truncatingRemainder(dividingBy: 360) - 180
                c.rotation = p.rotation.with(a, v)
                return c
            }
            return
        }
        let rw = Transform3.rotation(V3.ZERO.with(a, deg))
        updateSelectedParts(coalesce) { p in
            let r = Transform3.rotation(p.rotation)
            var c = p
            c.position = pivot + Self.mul(rw, p.position - pivot)
            c.rotation = Self.euler(Self.mat(rw, r))
            return c
        }
    }

    private func selectionPivot() -> V3? {
        guard let m = model else { return nil }
        let parts = selection.parts.compactMap { m.part($0) }
        if parts.count < 2 { return nil }
        var c = V3.ZERO
        for p in parts { c = c + Self.partCenter(p) }
        return c * (1 / Float(parts.count))
    }

    func scaleSelected(_ factor: Float, axis: Int? = nil, coalesce: String? = "scale") {
        let ax = axis ?? self.axis
        let f = factor.coerceIn(0.2, 5)
        let pivot = selectionPivot()
        let one = (0...2).contains(ax)
        updateSelectedParts(coalesce) { p in
            var c = p
            if one { c.scale = p.scale.with(ax, (p.scale.get(ax) * f).coerceIn(0.01, 100)) } else {
                c.scale = V3((p.scale.x * f).coerceIn(0.01, 100), (p.scale.y * f).coerceIn(0.01, 100), (p.scale.z * f).coerceIn(0.01, 100))
            }
            if let pv = pivot {
                c.position = one ? p.position.with(ax, pv.get(ax) + (p.position.get(ax) - pv.get(ax)) * f) : pv + (p.position - pv) * f
            }
            return c
        }
    }

    /// Sayısal değerler (panelden).
    func setTransform(_ id: String, position: V3? = nil, rotation: V3? = nil, scale: V3? = nil) {
        guard let m = model, var p = m.part(id) else { return }
        if let position { p.position = position }
        if let rotation { p.rotation = rotation }
        if let scale { p.scale = scale }
        commit(m.replacePart(p), "transform-\(id)")
    }

    func rotateByDrag(_ dx: Float, _ dy: Float) { rotateSelected(abs(dx) >= abs(dy) ? dx * 0.5 : dy * 0.5) }

    func scaleByDrag(_ dx: Float, _ dy: Float) { scaleSelected(Float(exp(Double((dx - dy) / 260)))) }

    // MARK: nokta ve kenar (v24)

    private func primaryIndex(_ m: Model3D) -> Int {
        guard let id = selection.primary else { return -1 }
        return m.parts.firstIndex { $0.id == id } ?? -1
    }

    private func edgeTap(_ x: Float, _ y: Float, _ radius: Float) {
        guard let m = model, let view = currentView() else { return }
        let s = selection
        let hit = Pick3.pick(m, view, x, y)
        var pi = primaryIndex(m)
        if pi < 0 {
            guard let hit else { return }
            pi = hit.part
            selection = Selection3([m.parts[pi].id])
        }
        let e = Pick3.edge(m, view, pi, x, y, radius)
        var cur = selection
        if let e, e.1.visible || hit == nil || hit?.part == pi {
            cur.edges = cur.edges.toggling(e.0)
            cur.vertices = OrderedSet()
            selection = cur
        } else if let hit, hit.part != pi {
            selection = Selection3([m.parts[hit.part].id])
        } else {
            var c = s; c.edges = OrderedSet(); c.vertices = OrderedSet(); selection = c
        }
    }

    /// Basılı tutulan kenara yeni nokta ekler.
    @discardableResult
    func addPointAt(_ x: Float, _ y: Float, _ radius: Float) -> Bool {
        applyOp()
        guard let m = model, let view = currentView() else { return false }
        var pi = primaryIndex(m)
        if pi < 0 { guard let h = Pick3.pick(m, view, x, y) else { return false }; pi = h.part }
        guard let (edge, _) = Pick3.edge(m, view, pi, x, y, radius) else { return false }
        var part = m.parts[pi]
        let t = Pick3.edgeParam(m, view, pi, edge, x, y)
        let (mesh, v) = MeshOps.splitEdge(part.mesh, edge, t)
        part.mesh = mesh; part.primitive = nil
        commit(m.replacePart(part))
        selection = Selection3([part.id], vertices: OrderedSet([v]))
        message("Nokta eklendi: sürükleyerek taşıyın".tr)
        return true
    }

    private var dragVertices: [Int] = []
    private var dragDepth: Float = 0

    func beginVertexDrag(_ x: Float, _ y: Float, _ radius: Float) -> Bool {
        guard let m = model, let view = currentView() else { return false }
        var pi = primaryIndex(m)
        if pi < 0 { guard let h = Pick3.pick(m, view, x, y) else { return false }; pi = h.part }
        let part = m.parts[pi]
        let s = selection
        let grabbed: [Int]
        if let v = Pick3.vertex(m, view, pi, x, y, radius * 0.8) {
            grabbed = [v.index]
            selection = Selection3([part.id], vertices: OrderedSet(grabbed), edges: s.primary == part.id ? s.edges : OrderedSet())
        } else {
            guard let e = Pick3.edge(m, view, pi, x, y, radius) else { return false }
            grabbed = [e.0.a, e.0.b]
            selection = Selection3([part.id], vertices: OrderedSet(grabbed), edges: OrderedSet([e.0]))
        }
        let w = Transform3.of(part).apply(part.mesh.vertex(grabbed[0]))
        dragVertices = grabbed
        dragDepth = view.depth(w.x, w.y, w.z)
        beginLive()
        return true
    }

    func dragVerticesBy(_ dx: Float, _ dy: Float) {
        guard let view = currentView(), let m = model, let id = selection.primary, let p = m.part(id), !dragVertices.isEmpty else { return }
        let d = ModelEdit.dragDelta(view, dx, dy, axis, dragDepth)
        commit(m.replacePart(ModelEdit.moveVertices(p, dragVertices, d)))
    }

    func endVertexDrag() { dragVertices = []; endLive() }

    func clearEdgeSelection() { applyOp(); selection.edges = OrderedSet(); selection.vertices = OrderedSet() }
    func clearFaceSelection() { applyOp(); selection.faces = OrderedSet() }

    func splitSelectedEdges() {
        applyOp()
        guard let m = model, let id = selection.primary, var part = m.part(id), !selection.edges.isEmpty else { return }
        var mesh = part.mesh
        var added: [Int] = []
        for e in selection.edges {
            if MeshOps.facesOfEdge(mesh, e).isEmpty { continue }
            let (nm, v) = MeshOps.splitEdge(mesh, e, 0.5)
            mesh = nm; added.append(v)
        }
        part.mesh = mesh; part.primitive = nil
        commit(m.replacePart(part))
        selection = Selection3([part.id], vertices: OrderedSet(added))
    }

    // MARK: kaydırıcı işlemleri (v24)

    private var opBase: Model3D?
    private var opPart: String?

    func startOp(_ kind: OpKind) {
        applyOp()
        guard let m = model, let id = selection.primary, let part = m.part(id) else { return }
        if kind.face && selection.faces.isEmpty { return }
        if !kind.face && selection.edges.isEmpty { return }
        opBase = m
        opPart = part.id
        op = SliderOp(kind: kind, amount: kind.start)
    }

    func setOpAmount(_ v: Float) {
        guard var cur = op, let base = opBase, let pid = opPart, let part = base.part(pid) else { return }
        let amount = v.coerceIn(cur.kind.min, cur.kind.max)
        cur.amount = amount
        if cur.kind == .HOLE { op = cur; return }
        let camera = model?.camera ?? base.camera
        guard let next = compute(cur.kind, part, amount) else {
            model = base.with(camera: camera)
            cur.failed = true
            op = cur
            return
        }
        model = base.replacePart(next).with(camera: camera)
        dirty = true
        cur.failed = false
        op = cur
    }

    private func compute(_ kind: OpKind, _ part: Part, _ a: Float) -> Part? {
        let s = selection
        let k = ((abs(part.scale.x) + abs(part.scale.y) + abs(part.scale.z)) / 3).coerceAtLeast(1e-3)
        func meshed(_ mesh: Mesh) -> Part { var c = part; c.mesh = mesh; c.primitive = nil; return c }
        switch kind {
        case .BEND: return meshed(MeshOps.bend(part.mesh, s.edges.items, a))
        case .STRETCH: return meshed(MeshOps.stretch(part.mesh, s.edges.items, a))
        case .PUSH: return meshed(MeshOps.push(part.mesh, s.edges.items, a / k))
        case .ROUND:
            if a <= 0.001 { return part }
            guard let e = s.edges.first, let r = MeshOps.round(part.mesh, e, a * MeshOps.extent(part.mesh) * 0.5, Self.ROUND_SEGMENTS) else { return nil }
            var c = meshed(r); c.faceMaterials = [:]; return c
        case .EXTRUDE: return abs(a) < 1e-3 ? part : Solid3.apply(part, MeshTopo.extrudeRegion(part.mesh, s.faces.items, a / k))
        case .INSET: return a < 1e-3 ? part : faces(part) { p, f in ModelEdit.insetFace(p, f, a) }
        case .FACE_SCALE:
            return s.faces.count == 1 ? faces(part) { p, f in ModelEdit.scaleFace(p, f, a) } : meshed(MeshTopo.scaleRegion(part.mesh, s.faces.items, a))
        case .HOLE: return nil
        }
    }

    private func faces(_ part: Part, _ fn: (Part, Int) -> Part) -> Part {
        var p = part
        for f in selection.faces.items.sorted(by: >) where f < p.mesh.faceCount { p = fn(p, f) }
        return p
    }

    /// Etkin işlemi uygular (geçmişe tek adım).
    func applyOp() {
        guard let cur = op, let base = opBase else { return }
        opBase = nil; opPart = nil
        op = nil
        if cur.kind == .HOLE { holeThroughFaces(cur.amount); return }
        guard let now = model else { return }
        if now.with(camera: base.camera) != base { pushUndo(base); dirty = true }
        if !cur.kind.face && (cur.kind == .BEND || cur.kind == .ROUND) { selection.edges = OrderedSet(); selection.vertices = OrderedSet() }
        cleanSelection()
    }

    func cancelOp() {
        guard let base = opBase else { op = nil; return }
        opBase = nil; opPart = nil
        op = nil
        model = base.with(camera: model?.camera ?? base.camera)
    }

    // MARK: yüz

    private func updateFaces(_ coalesce: String?, _ fn: (Part, Int) -> Part) {
        guard let m = model, let id = selection.primary, let p0 = m.part(id), !selection.faces.isEmpty else { return }
        var p = p0
        for f in selection.faces.items.sorted(by: >) where f < p.mesh.faceCount { p = fn(p, f) }
        commit(m.replacePart(p), coalesce)
    }

    func moveFaces(_ d: V3) { updateFaces("face-move") { p, f in ModelEdit.moveFace(p, f, d) } }

    func deleteFaces() {
        applyOp()
        updateFaces(nil) { p, f in ModelEdit.deleteFace(p, f) }
        selection.faces = OrderedSet()
    }

    /// Seçili yüzleri (yüz yoksa seçili parçaları) boyar.
    func paintSelection(_ material: Material? = nil) {
        applyOp()
        let mat = material ?? paint
        if !selection.faces.isEmpty { updateFaces("paint") { p, f in ModelEdit.paintFace(p, f, mat) } } else {
            updateSelectedParts("paint") { var c = $0; c.material = mat; c.faceMaterials = [:]; return c }
        }
    }

    private var paintedRegion: (String, Mesh, Set<Int>)?

    private func paintTargets(_ p: Part, _ face: Int) -> Set<Int>? {
        switch paintScope {
        case .FACE: return [face]
        case .SURFACE:
            if let r = paintedRegion, r.0 == p.id, r.1 === p.mesh, r.2.contains(face) { return r.2 }
            let region = Set(MeshTopo.surfaceRegion(p.mesh, face))
            paintedRegion = (p.id, p.mesh, region)
            return region
        case .PART, .ALL: return nil
        }
    }

    /// v27 Boya'da parmak inince neyin boyanacağı önceden gösterilir.
    func previewPaint(_ x: Float, _ y: Float) {
        guard let m = model, let hit = pick(x, y), m.parts.indices.contains(hit.0) else { return }
        let p = m.parts[hit.0]
        switch paintScope {
        case .ALL: selection = Selection3(m.parts.filter(\.visible).map(\.id))
        case .PART: selection = Selection3([p.id])
        default: selection = Selection3([p.id], faces: OrderedSet((paintTargets(p, hit.1) ?? []).sorted()))
        }
    }

    func paintAt(_ partIndex: Int, _ face: Int) {
        guard var m = model, m.parts.indices.contains(partIndex) else { return }
        let p = m.parts[partIndex]
        let mat = paint
        if let t = mat.texture { _ = texture(t) }
        switch paintScope {
        case .ALL:
            if m.parts.allSatisfy({ !$0.visible || ($0.material == mat && $0.faceMaterials.isEmpty) }) { return }
            m.parts = m.parts.map { q in guard q.visible else { return q }; var c = q; c.material = mat; c.faceMaterials = [:]; return c }
            commit(m, "paint-drag")
            selection = Selection3()
            return
        case .PART:
            if p.material == mat && p.faceMaterials.isEmpty { return }
            var c = p; c.material = mat; c.faceMaterials = [:]
            commit(m.replacePart(c), "paint-drag")
        default:
            guard let faces = paintTargets(p, face) else { return }
            if faces.allSatisfy({ p.materialOf($0) == mat }) { selection = Selection3([p.id]); return }
            commit(m.replacePart(ModelEdit.paintFaces(p, faces.sorted(), mat)), "paint-drag")
        }
        selection = Selection3([p.id])
    }

    // MARK: malzeme ve cisim

    func selectedMaterial() -> Material? {
        guard let m = model, let id = selection.primary, let p = m.part(id) else { return nil }
        if let f = selection.faces.first { return p.materialOf(f) }
        return p.material
    }

    func setMaterial(_ mat: Material) {
        if let t = mat.texture { _ = texture(t) }
        if tool == .PAINT { paint = mat; return }
        if !selection.faces.isEmpty && (tool == .FACE || tool == .PAINT) {
            updateFaces("material") { p, f in ModelEdit.paintFace(p, f, mat) }
        } else {
            updateSelectedParts("material") { var c = $0; c.material = mat; return c }
        }
        paint = mat
    }

    func setSmooth(_ v: Bool) { updateSelectedParts(nil) { var c = $0; c.smooth = v; return c } }
    func setUv(_ mode: UvMode) { updateSelectedParts(nil) { var c = $0; c.uv = mode; return c } }

    func setPrimitiveSpec(_ spec: PrimitiveSpec) {
        var s = spec
        s.segments = spec.segments.coerceIn(3, Primitives.MAX_SEGMENTS)
        s.rings = spec.rings.coerceIn(2, Primitives.MAX_RINGS)
        updateSelectedParts("primitive") { ModelEdit.setPrimitive($0, s) }
    }

    // MARK: sahne

    func setScene(_ scene: SceneSettings, coalesce: String? = "scene") {
        guard var m = model else { return }
        m.scene = scene
        commit(m, coalesce)
    }

    // MARK: 3B çizgiler

    func drawPlane() -> (V3, V3)? {
        guard let view = currentView(), let m = model else { return nil }
        let p = selection.primary.flatMap { m.part($0) }
        if plane == .FACE {
            guard let f = selection.faces.first, let p else { return nil }
            return ModelEdit.faceFrame(p, f)
        }
        let origin = p.map { ModelEdit.worldBounds($0).center } ?? m.camera.target
        return ModelEdit.plane(plane, view, origin)
    }

    func planePoint(_ plane: (V3, V3), _ x: Float, _ y: Float) -> V3? {
        guard let view = currentView() else { return nil }
        return ModelEdit.hitPlane(view, x, y, plane.0, plane.1)
    }

    @discardableResult
    func addStroke(_ points: [Float], _ widthWorld: Float) -> String? {
        guard var m = model, points.count >= 6 else { return nil }
        let id = Self.newId()
        m.strokes.append(Stroke3(id, points, brushColor, widthWorld, time: Self.nowMs()))
        commit(m)
        return id
    }

    func eraseStroke(_ index: Int) {
        guard var m = model, m.strokes.indices.contains(index) else { return }
        m.strokes.remove(at: index)
        commit(m)
    }

    // MARK: dokunuş

    func pick(_ x: Float, _ y: Float) -> (Int, Int)? {
        guard let m = model, let view = currentView(), let h = Pick3.pick(m, view, x, y) else { return nil }
        return (h.part, h.face)
    }

    /// Ekrandaki noktaya dokunuldu (araca göre seçim, boyama, silme). [radius] parmak payı (nokta).
    func tapAt(_ x: Float, _ y: Float, _ radius: Float = 48) {
        guard let m = model else { return }
        switch tool {
        case .EDGE: applyOp(); edgeTap(x, y, radius)
        case .POINT: applyOp(); pointTap(x, y, radius)
        case .ERASE:
            guard let view = currentView() else { return }
            if let i = Pick3.stroke(m, view, x, y, radius) { eraseStroke(i) }
        case .PAINT: if let h = pick(x, y) { paintAt(h.0, h.1) }
        case .FACE:
            applyOp()
            guard let hit = pick(x, y) else { selection.faces = OrderedSet(); return }
            guard m.parts.indices.contains(hit.0) else { return }
            let part = m.parts[hit.0]
            let s = selection
            let region: [Int] = faceMode == .SURFACE ? MeshTopo.surfaceRegion(part.mesh, hit.1) : [hit.1]
            if s.primary == part.id {
                var c = s
                c.faces = s.faces.contains(hit.1) ? s.faces.removing(region) : s.faces.adding(region)
                selection = c
            } else { selection = Selection3([part.id], faces: OrderedSet(region)) }
        default:
            guard let hit = pick(x, y) else { clearSelection(); return }
            if m.parts.indices.contains(hit.0) { selectInScene(m.parts[hit.0].id) }
        }
    }

    /// Sürükleme başında parmağın altındaki parçayı (grubuyla) seçer; parça yoksa false (sahne döner).
    func grabAt(_ x: Float, _ y: Float) -> Bool {
        guard let hit = pick(x, y), let m = model, m.parts.indices.contains(hit.0) else { return false }
        let id = m.parts[hit.0].id
        if !selection.parts.contains(id) { selectInScene(id) }
        return true
    }

    func onSelectedFace(_ x: Float, _ y: Float) -> Bool {
        guard let hit = pick(x, y), let m = model, m.parts.indices.contains(hit.0) else { return false }
        return m.parts[hit.0].id == selection.primary && selection.faces.contains(hit.1)
    }

    func dragDelta(_ dxScreen: Float, _ dyScreen: Float) -> V3? {
        guard let view = currentView(), let m = model else { return nil }
        let p = selection.primary.flatMap { m.part($0) }
        let depth = p.map { view.depth($0.position.x, $0.position.y, $0.position.z) } ?? 0
        return ModelEdit.dragDelta(view, dxScreen, dyScreen, axis, depth)
    }

    func toScreen(_ v: V3) -> CGPoint? {
        guard let view = currentView() else { return nil }
        let s = view.project(v)
        return s[3] <= 0 ? nil : CGPoint(x: CGFloat(s[0]), y: CGFloat(s[1]))
    }

    func pixelToWorld() -> Float { currentView().map { 1 / $0.scale } ?? 0.01 }

    // MARK: v25 Nokta aracı

    private func pointTap(_ x: Float, _ y: Float, _ radius: Float) {
        guard let m = model, let view = currentView() else { return }
        let hit = Pick3.pick(m, view, x, y)
        var pi = primaryIndex(m)
        if pi < 0 {
            guard let hit else { return }
            pi = hit.part
            selection = Selection3([m.parts[pi].id])
        }
        var part = m.parts[pi]
        let s = selection
        if let v = Pick3.vertex(m, view, pi, x, y, radius * 0.8), v.visible || hit == nil || hit?.part == pi {
            var c = s; c.vertices = s.vertices.toggling(v.index); c.edges = OrderedSet(); selection = c
            return
        }
        if let e = Pick3.edge(m, view, pi, x, y, radius), e.1.visible || hit == nil || hit?.part == pi {
            let t = Pick3.edgeParam(m, view, pi, e.0, x, y)
            let (mesh, nv) = MeshOps.splitEdge(part.mesh, e.0, t)
            part.mesh = mesh; part.primitive = nil
            commit(m.replacePart(part))
            selection = Selection3([part.id], vertices: s.vertices.adding(nv))
            return
        }
        if let hit, hit.part != pi { selection = Selection3([m.parts[hit.part].id]); return }
        var c = s; c.vertices = OrderedSet(); c.edges = OrderedSet(); selection = c
    }

    func beginPointDrag(_ x: Float, _ y: Float, _ radius: Float) -> Bool {
        guard let m = model, let view = currentView() else { return false }
        let pi = primaryIndex(m)
        guard pi >= 0, let v = Pick3.vertex(m, view, pi, x, y, radius * 0.8) else { return false }
        let part = m.parts[pi]
        let w = Transform3.of(part).apply(part.mesh.vertex(v.index))
        dragVertices = [v.index]
        dragDepth = view.depth(w.x, w.y, w.z)
        beginLive()
        return true
    }

    func selectedPoints() -> [Int] { selection.vertices.items }

    func deleteSelectedPoints() {
        applyOp()
        guard let m = model, let id = selection.primary, var part = m.part(id), !selection.vertices.isEmpty else { return }
        let targets = selection.vertices.items.filter { $0 < part.mesh.vertexCount }.map { part.mesh.vertex($0) }
        var removed = 0
        for pt in targets {
            guard let idx = (0..<part.mesh.vertexCount).first(where: { part.mesh.vertex($0) == pt }),
                  let r = MeshTopo.deleteVertex(part.mesh, idx), r.mesh.faceCount >= 2 else { continue }
            part = Solid3.apply(part, r)
            removed += 1
        }
        if removed == 0 { message("Bu nokta silinemiyor".tr); return }
        commit(m.replacePart(part))
        selection = Selection3([part.id])
        message(I18n.trf("{0} nokta silindi", removed))
    }

    private func connected(_ part: Part, _ pts: [Int], _ close: Bool) -> Part? {
        var mesh = part.mesh
        var origin = Array(0..<mesh.faceCount)
        var pairs: [(Int, Int)] = (0..<max(pts.count - 1, 0)).map { (pts[$0], pts[$0 + 1]) }
        if close && pts.count >= 3 { pairs.append((pts[pts.count - 1], pts[0])) }
        for (i, pair) in pairs.enumerated() {
            if MeshTopo.isEdge(mesh, pair.0, pair.1) { continue }
            guard let r = MeshTopo.connect(mesh, pair.0, pair.1) else {
                message(I18n.trf("{0}. ve {1}. noktalar aynı yüzde değil: araya nokta ekleyip adım adım birleştirin", i + 1, i + 2 > pts.count ? 1 : i + 2))
                return nil
            }
            let prev = origin
            origin = r.origin.map { $0 >= 0 && $0 < prev.count ? prev[$0] : -1 }
            mesh = r.mesh
        }
        return mesh === part.mesh ? part : Solid3.apply(part, TopoResult(mesh: mesh, origin: origin))
    }

    @discardableResult
    func connectSelectedPoints(close: Bool = false) -> Bool {
        applyOp()
        guard let m = model, let id = selection.primary, let part = m.part(id) else { return false }
        let pts = selectedPoints()
        if pts.count < 2 { message("Birleştirmek için en az iki nokta seçin".tr); return false }
        guard let joined = connected(part, pts, close) else { return false }
        if joined.mesh !== part.mesh { commit(m.replacePart(joined)) }
        return true
    }

    func cutSelectedLoop() {
        applyOp()
        let pts = selectedPoints()
        if pts.count < 3 { message("Kesmek için halka oluşturan en az üç nokta seçin".tr); return }
        guard var m = model, let id = selection.primary, let part = m.part(id), let joined = connected(part, pts, true) else { return }
        guard let (rest, piece) = Solid3.cut(joined, pts, Self.newId()) else { message("Seçili noktalar şekli ikiye ayıran kapalı bir halka oluşturmuyor".tr); return }
        guard let at = m.parts.firstIndex(where: { $0.id == part.id }) else { return }
        m.parts[at] = rest
        m.parts.insert(piece, at: at + 1)
        commit(m)
        selection = Selection3([piece.id])
        message("Kesildi: kesilen parça seçili".tr)
    }

    // MARK: v25 yüz kipi ve yüzey

    func setFaceMode(_ m: FaceMode) { faceMode = m }

    func detachFaces() {
        applyOp()
        guard var m = model, let id = selection.primary, let part = m.part(id) else { return }
        let faces = selection.faces.items.filter { $0 < part.mesh.faceCount }
        if faces.isEmpty || faces.count == part.mesh.faceCount { return }
        let sub = MeshTopo.subMesh(part.mesh, faces)
        guard let rest = MeshTopo.deleteFaces(part.mesh, faces), let at = m.parts.firstIndex(where: { $0.id == part.id }) else { return }
        var piece = part
        piece.id = Self.newId()
        piece.name = I18n.trf("{0} (yüzey)", part.name)
        piece.mesh = sub.mesh
        piece.primitive = nil
        piece.faceMaterials = Solid3.remap(part, sub.origin)
        m.parts[at] = Solid3.apply(part, rest)
        m.parts.insert(piece, at: at + 1)
        commit(m)
        selection = Selection3([piece.id])
    }

    /// Seçili yüz bölgesinin dünya merkezi, ortalama normali ve eş alanlı yarıçapı.
    func faceRegionFrame() -> (V3, V3, Float)? {
        guard let m = model, let id = selection.primary, let part = m.part(id) else { return nil }
        let faces = selection.faces.items.filter { $0 < part.mesh.faceCount }
        if faces.isEmpty { return nil }
        let t = Transform3.of(part)
        var area = 0.0, cx = 0.0, cy = 0.0, cz = 0.0
        var nx: Float = 0, ny: Float = 0, nz: Float = 0
        var out = [Float](repeating: 0, count: 3)
        for f in faces {
            let world = Mesh(part.mesh.pos, [part.mesh.faces[f]]).mapPositions { t.apply($0) }
            let a = MeshTopo.faceArea(world, 0)
            let c = world.faceCenter(0)
            area += a; cx += Double(c.x) * a; cy += Double(c.y) * a; cz += Double(c.z) * a
            let fn = part.mesh.faceNormal(f)
            t.normal(fn.x, fn.y, fn.z, &out, 0)
            nx += out[0] * Float(a); ny += out[1] * Float(a); nz += out[2] * Float(a)
        }
        if area <= 1e-12 { return nil }
        let center = V3(Float(cx / area), Float(cy / area), Float(cz / area))
        return (center, V3(nx, ny, nz).normalized(), Float((area / Double.pi).squareRoot()))
    }

    func holePreview() -> (V3, V3, Float)? {
        guard let op, op.kind == .HOLE, let (c, n, r) = faceRegionFrame() else { return nil }
        return (c, n, r * op.amount)
    }

    private func holeThroughFaces(_ amount: Float) {
        guard let m = model, let id = selection.primary, let part = m.part(id), let (c, n, r) = faceRegionFrame() else { return }
        let seg = Budget3D.holeSegments, budget = Budget3D.csgTriangles
        runHeavy("Delik açılıyor…".tr) {
            guard let holed = Solid3.hole(part, c, n, r * amount, seg, budget) else { return nil }
            return { $0.replacePart(holed) }
        }
        selection.faces = OrderedSet()
    }

    // MARK: v25 katı cisim işlemleri

    private var heavyJob: Task<Void, Never>?

    /// Ağır işlemi arka planda yapar; sonuç modele uygulanacak dönüşüm olarak gelir (tek geri alma adımı).
    private func runHeavy(_ label: String, _ failMessage: String? = nil, _ work: @escaping @Sendable () async throws -> (@MainActor (Model3D) -> Model3D)?) {
        heavyJob?.cancel()
        let fail = failMessage ?? "İşlem yapılamadı".tr
        let previous = heavyJob
        heavyJob = Task { [weak self] in
            _ = previous
            self?.busy = label
            let r: Result<(@MainActor (Model3D) -> Model3D)?, Error>
            do { r = .success(try await Task.detached(priority: .userInitiated) { try await work() }.value) } catch { r = .failure(error) }
            guard let self else { return }
            self.busy = nil
            if Task.isCancelled { return }
            switch r {
            case .failure: self.message("İşlem yapılamadı".tr)
            case .success(nil): self.message(fail)
            case .success(let f?):
                guard let m = self.model else { return }
                self.commit(f(m))
            }
        }
    }

    func booleanSelected(_ opn: Solid3.Op) {
        applyOp()
        guard let m = model else { return }
        let parts = selection.parts.compactMap { m.part($0) }
        if parts.count < 2 { message("En az iki parça seçin (Parçalar panelinde kutucuklarla)".tr); return }
        let ids = Set(parts.map(\.id))
        let newId = Self.newId()
        let label: String
        switch opn {
        case .UNION: label = "Birleştiriliyor…".tr
        case .SUBTRACT: label = "Çıkarılıyor…".tr
        case .INTERSECT: label = "Kesişim alınıyor…".tr
        }
        let budget = Budget3D.csgTriangles
        runHeavy(label, opn == .INTERSECT ? "Parçalar kesişmiyor".tr : "İşlem yapılamadı".tr) { [weak self] in
            guard let result = Solid3.boolean(parts, opn, newId, parts[0].name, budget) else { return nil }
            return { mm in
                var mm = mm
                let at = mm.parts.firstIndex { ids.contains($0.id) } ?? 0
                var rest = mm.parts.filter { !ids.contains($0.id) }
                rest.insert(result, at: at.coerceIn(0, rest.count))
                self?.selection = Selection3([result.id])
                mm.parts = rest
                return mm
            }
        }
    }

    func separateSelected() {
        applyOp()
        guard var m = model, let id = selection.primary, let part = m.part(id) else { return }
        let pieces = Solid3.separate(part) { Self.newId() }
        if pieces.isEmpty { message("Parça zaten tek parça".tr); return }
        guard let at = m.parts.firstIndex(where: { $0.id == part.id }) else { return }
        m.parts.remove(at: at)
        m.parts.insert(contentsOf: pieces, at: at)
        commit(m)
        selection = Selection3(pieces.map(\.id))
        message(I18n.trf("{0} parçaya ayrıldı", pieces.count))
    }

    func setSlice(axis: SliceAxis? = nil, offset: Float? = nil) { slice = (axis ?? slice.0, (offset ?? slice.1).coerceIn(-0.49, 0.49)) }

    func slicePlane() -> (V3, V3)? {
        guard let m = model, let id = selection.primary, let part = m.part(id) else { return nil }
        let b = ModelEdit.worldBounds(part)
        let (ax, off) = slice
        let n: V3
        switch ax {
        case .X: n = V3(1, 0, 0)
        case .Y: n = V3(0, 1, 0)
        case .Z: n = V3(0, 0, 1)
        case .VIEW: n = currentView()?.right ?? V3(1, 0, 0)
        }
        let extent = abs(b.size.x * n.x) + abs(b.size.y * n.y) + abs(b.size.z * n.z)
        return (b.center + n * (off * extent), n)
    }

    func sliceSelected() {
        applyOp()
        guard let m = model, let id = selection.primary, let part = m.part(id), let (point, normal) = slicePlane() else { return }
        let newId = Self.newId()
        let budget = Budget3D.csgTriangles
        runHeavy("Parçalanıyor…".tr, "Düzlem parçayı kesmiyor".tr) { [weak self] in
            guard let (front, back) = Solid3.slice(part, point, normal, budget) else { return nil }
            let gap = ModelEdit.worldBounds(part).radius * 0.06
            var a = front; a.position = front.position + normal * gap
            var b = back; b.id = newId; b.name = "\(part.name) (2)"; b.position = back.position - normal * gap
            return { mm in
                guard let at = mm.parts.firstIndex(where: { $0.id == part.id }) else { return mm }
                var mm = mm
                mm.parts[at] = a
                mm.parts.insert(b, at: at + 1)
                self?.selection = Selection3([a.id, b.id])
                return mm
            }
        }
    }

    func setHoleRatio(_ r: Float) { holeRatio = r.coerceIn(0.05, 0.95) }

    func panelHoleFrame(_ ratio: Float? = nil) -> (V3, V3, Float)? {
        guard let m = model, let id = selection.primary, let part = m.part(id) else { return nil }
        let r = (ratio ?? holeRatio).coerceIn(0.05, 0.95)
        if let (c, a, base) = faceRegionFrame() { return (c, a, base * r) }
        let b = ModelEdit.worldBounds(part)
        return (b.center, currentView()?.toViewer ?? V3(0, 0, 1), min(b.size.x, min(b.size.y, b.size.z)) * 0.5 * r)
    }

    func holeSelected() {
        applyOp()
        guard let m = model, let id = selection.primary, let part = m.part(id), let (center, ax, radius) = panelHoleFrame() else { return }
        let seg = Budget3D.holeSegments, budget = Budget3D.csgTriangles
        runHeavy("Delik açılıyor…".tr) {
            guard let holed = Solid3.hole(part, center, ax, radius, seg, budget) else { return nil }
            return { $0.replacePart(holed) }
        }
    }

    // MARK: v25 3B fırça

    func setBrush(_ b: Brush3) {
        if brush.kind == .LINE && b.kind != .LINE && brushWidth < Self.SOLID_MIN_WIDTH { brushWidth = Self.SOLID_DEFAULT_WIDTH }
        var c = b
        c.height = b.height.coerceIn(0.02, 3)
        c.opacity = b.opacity.coerceIn(0.1, 1)
        brush = c
    }

    private func planeUp(_ normal: V3) -> V3 {
        let n = normal.normalized()
        if abs(n.y) < 0.9 { return V3.UP }
        if let d = currentView()?.down { return d * -1 }
        return V3(0, 0, -1)
    }

    /// Fırça izi bitti: Çizgi türünde 3B çizgi; diğer türlerde iz arka planda cisme dönüştürülür.
    func finishBrush(_ points: [Float], _ pressures: [Float]?, _ normal: V3) {
        let b = brush
        let widthWorld = brushWidth * pixelToWorld()
        if b.kind == .LINE {
            if let id = addStroke(points, widthWorld) { suggestFor(id, points, normal, planeUp(normal), b.kind, after: nil) }
            return
        }
        if points.count < 6 { return }
        let partId = Self.newId()
        let pts = (0..<(points.count / 3)).map { V3(points[3 * $0], points[3 * $0 + 1], points[3 * $0 + 2]) }
        let spec = BrushShapeSpec(kind: b.kind, width: widthWorld, height: b.height, bulge: b.bulge, taper: b.taper, smoothing: b.smoothing,
                                  pressure: b.pressure, sides: Budget3D.brushSides, maxSamples: Budget3D.brushSamples)
        let up = planeUp(normal)
        let input = BrushInput(points: pts, pressures: pressures, normal: normal, up: up)
        let material = Material(color: brushColor, opacity: b.opacity, kind: b.material, shine: 0.5)
        let name = Self.brushName(b.kind)
        let source = BrushSource(spec, points, pressures, normal, up, time: Self.nowMs())
        runHeavy("Cisim oluşturuluyor…".tr, "Çizgiden cisim oluşmadı: biraz daha uzun çizin".tr) { [weak self] in
            guard let mesh = BrushShapes.build(input, spec) else { return nil }
            let part = BrushShapes.toPart(mesh, partId, name, material, source)
            return { mm in
                self?.selection = Selection3([part.id])
                var mm = mm
                mm.parts.append(part)
                return mm
            }
        }
        suggestFor(partId, points, normal, up, b.kind, after: heavyJob)
    }

    // MARK: v27 çizim önerisi

    /// Açık öneri: kaynağı (çizgi ya da fırça cismi), çözümleme ve iz.
    final class SuggestUi {
        let sourceId: String
        let sketch: Sketch2
        let result: Sketch3Result
        let points: [V3]
        init(_ sourceId: String, _ sketch: Sketch2, _ result: Sketch3Result, _ points: [V3]) {
            self.sourceId = sourceId; self.sketch = sketch; self.result = result; self.points = points
        }
        var kinds: [Suggest3Kind] { result.suggestions.map(\.kind) }
    }

    func setSuggestEnabled(_ v: Bool) {
        suggestEnabled = v
        prefs.suggest = v
        if !v { suggestion = nil }
        message(v ? "Çizim önerileri açık".tr : "Çizim önerileri kapalı".tr)
    }

    private var _sketchModel: SketchModel?
    private let sketchLock = NSLock()

    /// Öneri modeli: ilk kullanımda hazırlanır, kişisel durum tercihlerden yüklenir. Her iş parçacığından.
    nonisolated private func sketchModel(_ saved: String?) -> SketchModel {
        sketchLock.lock(); defer { sketchLock.unlock() }
        if let m = _sketchModelUnsafe { return m }
        let m = SketchModel.create()
        m.decode(saved)
        _sketchModelUnsafe = m
        return m
    }

    nonisolated(unsafe) private var _sketchModelUnsafe: SketchModel?
    private var suggestJob: Task<Void, Never>?

    private func prewarmSuggest() {
        if !suggestEnabled || !canvasAiOn { return }
        let saved = prefs.sketchModel
        Task.detached(priority: .utility) { [weak self] in _ = self?.sketchModel(saved) }
    }

    private func suggestFor(_ sourceId: String, _ points: [Float], _ normal: V3, _ up: V3, _ drawn: Brush3Kind, after: Task<Void, Never>?) {
        suggestion = nil
        suggestJob?.cancel()
        if !suggestEnabled || !canvasAiOn || points.count < 9 { return }
        let pts = (0..<(points.count / 3)).map { V3(points[3 * $0], points[3 * $0 + 1], points[3 * $0 + 2]) }
        let minLength = Self.SUGGEST_MIN_PX * pixelToWorld()
        let saved = prefs.sketchModel
        suggestJob = Task { [weak self] in
            guard let self else { return }
            let r = await Task.detached(priority: .utility) { () -> (Sketch2, Sketch3Result)? in
                Sketch3Suggest.analyze(pts, normal, up, drawn, self.sketchModel(saved), minLength)
            }.value
            guard let r, !Task.isCancelled else { return }
            await after?.value
            if Task.isCancelled { return }
            self.suggestion = SuggestUi(sourceId, r.0, r.1, pts)
        }
    }

    func dismissSuggestion() {
        guard let s = suggestion else { return }
        suggestion = nil
        let saved = prefs.sketchModel
        let prefs = self.prefs
        Task.detached(priority: .utility) { [weak self] in
            guard let self else { return }
            let sm = self.sketchModel(saved)
            sm.learnDismiss(s.result.features, s.kinds)
            let enc = sm.encode()
            await MainActor.run { prefs.sketchModel = enc }
        }
    }

    func expireSuggestion(_ s: SuggestUi) { if suggestion === s { suggestion = nil } }

    private func dropSuggestion() { suggestJob?.cancel(); suggestion = nil }

    /// v27 öneri uygulanır: çizim önerilen cisimle değişir (tek geri alma adımı); kabul cihazda öğrenilir.
    func applySuggestion(_ kind: Suggest3Kind) {
        applyOp()
        guard let s = suggestion else { return }
        suggestion = nil
        guard var m = model else { return }
        let saved = prefs.sketchModel
        let prefs = self.prefs
        Task.detached(priority: .utility) { [weak self] in
            guard let self else { return }
            let sm = self.sketchModel(saved)
            sm.learnAccept(s.result.features, kind)
            let enc = sm.encode()
            await MainActor.run { prefs.sketchModel = enc }
        }
        let b = brush
        let material = Material(color: brushColor, opacity: b.opacity, kind: b.material, shine: 0.5)
        let label = Self.suggestName(kind)
        let name = "\(label) \(m.parts.count + 1)"
        let src = s.sourceId
        if kind.primitive != nil {
            guard var part = Sketch3Fit.solid(kind, s.sketch, Self.newId(), m.parts.count, material, 2 * pixelToWorld()) else {
                message("Öneri bu çizime uygulanamadı".tr); return
            }
            part.name = name
            m.strokes.removeAll { $0.id == src }
            m.parts.removeAll { $0.id == src }
            m.parts.append(part)
            commit(m)
            selection = Selection3([part.id])
            message(I18n.trf("{0} yapıldı · geri al ile çizime dönebilirsiniz", label))
            return
        }
        guard let fit = Sketch3Fit.brush(kind, s.sketch, s.points) else { message("Öneri bu çizime uygulanamadı".tr); return }
        let px = pixelToWorld()
        let spec = BrushShapeSpec(kind: fit.kind, width: fit.width ?? (max(brushWidth, Self.SOLID_DEFAULT_WIDTH) * px), height: b.height, bulge: b.bulge,
                                  taper: b.taper, smoothing: fit.smoothing ?? b.smoothing, pressure: false, sides: Budget3D.brushSides, maxSamples: Budget3D.brushSamples)
        var flat = [Float](repeating: 0, count: fit.input.points.count * 3)
        for (i, p) in fit.input.points.enumerated() { flat[3 * i] = p.x; flat[3 * i + 1] = p.y; flat[3 * i + 2] = p.z }
        let source = BrushSource(spec, flat, nil, fit.input.normal, fit.input.up, time: Self.nowMs())
        let input = fit.input
        let doneText = I18n.trf("{0} yapıldı · geri al ile çizime dönebilirsiniz", label)
        runHeavy("Cisim oluşturuluyor…".tr, "Öneri bu çizime uygulanamadı".tr) { [weak self] in
            guard let mesh = BrushShapes.build(input, spec) else { return nil }
            let part = BrushShapes.toPart(mesh, Self.newId(), name, material, source)
            return { mm in
                self?.selection = Selection3([part.id])
                self?.message(doneText)
                var mm = mm
                mm.strokes.removeAll { $0.id == src }
                mm.parts.removeAll { $0.id == src }
                mm.parts.append(part)
                return mm
            }
        }
    }

    // MARK: v25 resim olarak dışa aktarma

    /// Modelin resmi (ayarlarla), yazılım çizicisiyle.
    func renderImage(_ e: ImageExport, previewSide: Int? = nil) async -> CGImage? {
        guard let m = model else { return nil }
        let long = (previewSide ?? e.size).coerceIn(64, Budget3D.exportMaxSide)
        let w = e.aspect >= 1 ? long : max(Int(Float(long) * e.aspect), 16)
        let h = e.aspect >= 1 ? max(Int(Float(long) / e.aspect), 16) : long
        let ref = currentView()
        let tex = textures
        return await Task.detached(priority: .userInitiated) { () -> CGImage? in
            tex.preload(TextureRepository.ids(m))
            let maxSs = Int((Double(Budget3D.exportPixels) / Double(w * h)).squareRoot()).coerceIn(1, 3)
            let ss = e.smoothness.coerceIn(1, 3).coerceAtMost(maxSs)
            let bg: Int32 = e.jpeg && e.background == 0 ? -1 : e.background
            var mm = m
            mm.scene.edges = e.edges; mm.scene.shadow = e.shadow; mm.scene.ground = e.ground; mm.scene.background = bg
            let view: View3
            if e.fit { view = View3.fit(mm, m.camera, w, h, 0.06) } else if let ref {
                let k = min(Float(w) / Float(ref.width), Float(h) / Float(ref.height))
                view = View3(m.camera, w, h, ref.scale * k, Float(w) / 2, Float(h) / 2)
            } else { view = View3.editor(m.camera, w, h) }
            let f = Renderer3D.render(mm, view, RenderOptions(textures: { tex.cached($0) }, supersample: ss, ground: e.ground, shadow: e.shadow,
                                                              background: UInt32(bitPattern: bg)))
            return Bitmaps.image(argb: f.color, w, h)
        }.value
    }

    /// v24 GLB olarak dışa aktarma verisi (dokular gömülü).
    func exportGlb() async -> [UInt8]? {
        applyOp()
        guard let m = model else { return nil }
        return await repository.exportGlb(m)
    }

    /// Modelin görünen çizgileri (1200 piksellik görünümde; tuvale vektör aktarma).
    func outlineStrokes() async -> [[Float]] {
        guard let m = model else { return [] }
        return await Task.detached(priority: .userInitiated) {
            let view = View3.fit(m, m.camera, 1200, 1200, 0.06)
            return Renderer3D.outlines(m, view).filter { pl in
                guard pl.count >= 4 else { return false }
                var len: Float = 0
                var i = 2
                while i < pl.count { len += ktHypotF(pl[i] - pl[i - 2], pl[i + 1] - pl[i - 1]); i += 2 }
                return len >= 3
            }
        }.value
    }

    // MARK: v26 gruplar

    func selectInScene(_ id: String) {
        applyOp()
        guard let m = model else { return }
        if let g = m.part(id)?.group { selection = Selection3([id] + m.parts.filter { $0.group == g && $0.id != id }.map(\.id)) } else { selection = Selection3([id]) }
    }

    func selectGroup(_ groupId: String) {
        applyOp()
        guard let m = model else { return }
        let ids = m.parts.filter { $0.group == groupId }.map(\.id)
        if !ids.isEmpty { selection = Selection3(ids) }
    }

    func groupSelected() {
        applyOp()
        guard var m = model else { return }
        let ids = Set(selection.parts)
        if ids.count < 2 { message("Gruplamak için en az iki parça seçin".tr); return }
        let base = Self.GROUP.tr
        let n = (1...999).first { k in !m.groups.contains { $0.name == "\(base) \(k)" } } ?? 1
        let g = Group3(id: Self.newId(), name: "\(base) \(n)")
        m.parts = m.parts.map { p in guard ids.contains(p.id) else { return p }; var c = p; c.group = g.id; return c }
        m.groups.append(g)
        commit(m)
        message(I18n.trf("{0}: {1} parça birlikte hareket eder", g.name, ids.count))
    }

    func ungroupSelected() {
        applyOp()
        guard var m = model else { return }
        let groups = Set(selection.parts.compactMap { m.part($0)?.group })
        if groups.isEmpty { return }
        m.parts = m.parts.map { p in guard let g = p.group, groups.contains(g) else { return p }; var c = p; c.group = nil; return c }
        m.groups.removeAll { groups.contains($0.id) }
        commit(m)
    }

    func ungroup(_ groupId: String) {
        guard var m = model else { return }
        m.parts = m.parts.map { p in guard p.group == groupId else { return p }; var c = p; c.group = nil; return c }
        m.groups.removeAll { $0.id == groupId }
        commit(m)
    }

    func renameGroup(_ groupId: String, _ name: String) {
        guard var m = model else { return }
        let n = name.trimmingCharacters(in: .whitespaces)
        if n.isEmpty { return }
        m.groups = m.groups.map { $0.id == groupId ? Group3(id: $0.id, name: n) : $0 }
        commit(m)
    }

    func toggleGroupVisible(_ groupId: String) {
        guard var m = model else { return }
        let show = !m.parts.contains { $0.group == groupId && $0.visible }
        m.parts = m.parts.map { p in guard p.group == groupId else { return p }; var c = p; c.visible = show; return c }
        commit(m)
    }

    private func normalizeGroups(_ m: Model3D) -> Model3D {
        if m.groups.isEmpty && !m.parts.contains(where: { $0.group != nil }) { return m }
        var counts: [String: Int] = [:]
        for p in m.parts { if let g = p.group { counts[g, default: 0] += 1 } }
        let valid = Set(m.groups.filter { (counts[$0.id] ?? 0) >= 2 }.map(\.id))
        if valid.count == m.groups.count && m.parts.allSatisfy({ $0.group == nil || valid.contains($0.group!) }) { return m }
        var c = m
        c.parts = m.parts.map { p in guard let g = p.group, !valid.contains(g) else { return p }; var q = p; q.group = nil; return q }
        c.groups = m.groups.filter { valid.contains($0.id) }
        return c
    }

    // MARK: v26 silgi ve temizleme

    func setEraserSize(_ v: Float) { eraserSize = v.coerceIn(8, 60) }

    func beginErase() { applyOp(); beginLive() }

    /// Silgi izinin son parçası ([xs], [ys] ekran noktası, [radius]): çizgiler ve tüp/şerit/duvar/torna hemen silinir.
    func eraseAlong(_ xs: [Float], _ ys: [Float], _ radius: Float) {
        guard var m = model, let view = currentView(), !xs.isEmpty else { return }
        let e = BrushErase.Eraser(xs, ys, radius)
        var changed = false
        let strokes = m.strokes.flatMap { st -> [Stroke3] in
            if let r = BrushErase.eraseStroke(st, view, e, { Self.newId() }) { changed = true; return r }
            return [st]
        }
        let parts = m.parts.flatMap { p -> [Part] in
            if let r = BrushErase.erasePart(p, view, e, { Self.newId() }) { changed = true; return r }
            return [p]
        }
        if !changed { return }
        m.parts = parts; m.strokes = strokes
        commit(m)
        cleanSelection()
    }

    /// Silgi kalktı: şişir/prizma çizimleri bütün iz boyunca arka planda oyulur.
    func endErase(_ xs: [Float], _ ys: [Float], _ radius: Float) {
        endLive()
        guard let m = model, let view = currentView(), !xs.isEmpty else { return }
        let e = BrushErase.Eraser(xs, ys, radius)
        let areas = m.parts.filter { BrushErase.isArea($0) && BrushErase.onScreen($0, view, e) }
        if areas.isEmpty { return }
        runHeavy("Siliniyor…".tr, "Silgi bir çizime değmedi".tr) {
            var results: [String: [Part]] = [:]
            for p in areas { if let r = BrushErase.carvePart(p, view, e, { Self.newId() }) { results[p.id] = r } }
            if results.isEmpty { return nil }
            return { mm in var c = mm; c.parts = mm.parts.flatMap { results[$0.id] ?? [$0] }; return c }
        }
    }

    func deleteLastDrawing() {
        applyOp(); dropSuggestion()
        guard var m = model else { return }
        guard let latest = (m.strokes.map(\.time) + m.parts.compactMap { $0.brush?.time }).max() else { message("Silinecek çizim yok".tr); return }
        m.strokes.removeAll { $0.time == latest }
        m.parts.removeAll { $0.brush?.time == latest }
        commit(m)
        cleanSelection()
    }

    func clearDrawings() {
        applyOp(); dropSuggestion()
        guard var m = model else { return }
        if !Self.hasDrawings(m) { message("Silinecek çizim yok".tr); return }
        m.strokes = []
        m.parts.removeAll { $0.brush != nil }
        commit(m)
        cleanSelection()
    }

    // MARK: kaydetme

    func rename(_ name: String) {
        guard var m = model else { return }
        if !name.trimmingCharacters(in: .whitespaces).isEmpty { m.name = name }
        model = m
        dirty = true
    }

    func save(exitAfter: Bool = false) {
        applyOp()
        guard let m = model else { return }
        Task {
            await repository.save(m)
            if let canvasId { results.post(.init(canvasId: canvasId, strokeId: strokeId, model: m)) }
            dirty = false
            emit(.saved(name: m.name, toCanvas: canvasId != nil))
            if exitAfter { emit(.exit) }
        }
    }

    func saveAs(_ name: String) {
        applyOp()
        guard let m = model else { return }
        Task {
            let copy = await repository.saveCopy(m, name.trimmingCharacters(in: .whitespaces).isEmpty ? m.name : name)
            model = copy
            dirty = false
            emit(.saved(name: copy.name, toCanvas: false))
        }
    }

    // MARK: yardımcılar

    nonisolated static func hasDrawings(_ m: Model3D) -> Bool { !m.strokes.isEmpty || m.parts.contains { $0.brush != nil } }

    nonisolated private static func mul(_ r: [Float], _ v: V3) -> V3 {
        let x = r[0] * v.x + r[1] * v.y + r[2] * v.z
        let y = r[3] * v.x + r[4] * v.y + r[5] * v.z
        let z = r[6] * v.x + r[7] * v.y + r[8] * v.z
        return V3(x, y, z)
    }

    nonisolated private static func mat(_ a: [Float], _ b: [Float]) -> [Float] {
        (0..<9).map { (k: Int) -> Float in
            let i = k / 3, j = k % 3
            let s0: Float = a[3 * i] * b[j]
            let s1: Float = a[3 * i + 1] * b[3 + j]
            let s2: Float = a[3 * i + 2] * b[6 + j]
            return s0 + s1 + s2
        }
    }

    /// Rz·Ry·Rx matrisinden derece cinsinden açılar.
    nonisolated static func euler(_ r: [Float]) -> V3 {
        let sy = (-r[6]).coerceIn(-1, 1)
        let y = Float(asin(Double(sy)) * 57.29577951308232)
        if abs(sy) < 0.9999 {
            return V3(Float(atan2(Double(r[7]), Double(r[8])) * 57.29577951308232), y, Float(atan2(Double(r[3]), Double(r[0])) * 57.29577951308232))
        }
        return V3(Float(atan2(Double(-r[5]), Double(r[4])) * 57.29577951308232), y, 0)
    }

    nonisolated static func partCenter(_ p: Part) -> V3 { Transform3.of(p).apply(p.mesh.bounds.center) }

    nonisolated static func suggestName(_ k: Suggest3Kind) -> String {
        let s: String
        switch k {
        case .SPHERE: s = "Küre"
        case .ELLIPSOID: s = "Elipsoit"
        case .CYLINDER: s = "Silindir"
        case .TORUS: s = "Simit"
        case .DOME: s = "Kubbe"
        case .CAPSULE: s = "Kapsül"
        case .CUBE: s = "Küp"
        case .BOX: s = "Kutu"
        case .SLAB: s = "Levha"
        case .PYRAMID: s = "Piramit"
        case .CONE: s = "Koni"
        case .PRISM3: s = "Üçgen prizma"
        case .PRISM6: s = "Altıgen prizma"
        case .STAR: s = "Yıldız"
        case .ROD: s = "Çubuk"
        case .TUBE: s = "Tüp"
        case .RIBBON: s = "Şerit"
        case .WALL: s = "Duvar"
        case .LATHE: s = "Torna"
        case .INFLATE: s = "Şişirme"
        case .SPRING: s = "Yay"
        }
        return s.tr
    }

    nonisolated static func brushName(_ k: Brush3Kind) -> String {
        let s: String
        switch k {
        case .LINE: s = "Çizgi"
        case .TUBE: s = "Tüp"
        case .RIBBON: s = "Şerit"
        case .WALL: s = "Duvar"
        case .LATHE: s = "Torna"
        case .INFLATE: s = "Şişir"
        }
        return s.tr
    }

    nonisolated static func kindLabel(_ k: MaterialKind) -> String {
        let s: String
        switch k {
        case .MATTE: s = "Mat"
        case .PLASTIC: s = "Plastik"
        case .METAL: s = "Metal"
        case .GLASS: s = "Cam"
        case .GLOW: s = "Işıklı"
        }
        return s.tr
    }
}
