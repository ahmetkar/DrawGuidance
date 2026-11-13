import Combine
import Foundation

/// v43 tuval üstü seçme araçları (Android `CanvasPick`).
enum CanvasPick { case NONE, EYEDROPPER, RECT_SELECT, LASSO_SELECT }

/// Birleşik kaydetme penceresinin sekmeleri.
enum SaveTab { case IMAGE, PDF, CBZ }

/// Kağıdı kenardan genişletme yönü.
enum ExpandSide { case LEFT, TOP, RIGHT, BOTTOM }

/// Çizim ekranının görünüm modeli (Android `CanvasEditViewModel`): modları yönetir, denetleyicileri kurar ve mod/sayfa/
/// geri alma değişimlerini onlara bildirir. Ekran denetleyicilere doğrudan erişir (Android'deki aktarıcı işlevler yerine).
@MainActor
final class EditorViewModel: ObservableObject, EditorHost {
    static let SCENE_HARMONY = "harmony"
    static let SCENE_PLACE = "place"
    static let LINE_SUGGEST_OFF = "Çizgi önerileri kapatıldı (⋮ menüsünden açılabilir)"
    private static let EDITABLE_KINDS: Set<SelectionKind> = [.SHAPE, .DIAGRAM, .IMAGE, .TEXT, .FORMULA]

    let canvasLocalId: String
    let deps: EditorDependencies
    private let canvasPrefs: CanvasPrefs

    @Published private(set) var mode: EditMode = .DRAW
    @Published private(set) var panel: PanelTab?
    @Published private(set) var canvasPick: CanvasPick = .NONE
    @Published private(set) var pendingShapeTool: ShapeTool?
    @Published private(set) var pendingSceneTool: String?
    /// v41 panele bırakılmayı bekleyen mekân arka planı (yerleştirme katmanı açık)
    @Published var placingLocation: ComicBackground?
    @Published private(set) var paintBrush = false
    @Published private(set) var textPathPick: String?
    @Published private(set) var transferring = false
    @Published private(set) var saveTab: SaveTab?
    @Published private(set) var lastAddItem: String?
    @Published private(set) var loaded = false
    /// Ekranın gösterdiği bildirimler.
    let events = PassthroughSubject<EditEvent, Never>()

    // MARK: Denetleyiciler

    let store: EditorStore
    let viewport = ViewportState()
    private(set) var frame: FrameController!
    private(set) var backgroundTransform: BackgroundTransformController!
    private(set) var ruler: RulerController!
    private(set) var style: StyleController!
    private(set) var brush: BrushController!
    private(set) var shapes: ShapeController!
    private(set) var diagrams: DiagramController!
    private(set) var texts: TextController!
    private(set) var selection: SelectionController!
    private(set) var comic: ComicController!
    private(set) var drawing: DrawingController!
    private(set) var pages: PageController!
    private(set) var guides: GuideController!
    private(set) var images: ImageController!
    private(set) var v40: V40ExportController!
    private(set) var v45: V45NotesComicController!
    private(set) var formulas: FormulaController!
    private(set) var pen: PenController!
    private(set) var scene: ObjectSceneController!
    private(set) var layers: LayerController!
    private(set) var imageEdit: ImageEditController!
    private(set) var models3d: Model3DController!
    private(set) var handwriting: HandwritingController!
    private(set) var kidsPaint: KidsPaintController!
    private(set) var kidsBrush: KidsBrushController!
    private var modeTemplates: ModeTemplateSource?

    private var returnStack: [(EditMode, Set<String>)] = []
    private var addAnchor: P?
    private var bag = Set<AnyCancellable>()

    /// v32 eğitim sürüyor mu (eğitim denetleyicisi bağlanınca ayarlanır).
    var inTutorial: () -> Bool = { false }
    /// v32 eğitimin yapay zekâ adımında mı.
    var tutorialAiStep: () -> Bool = { false }
    /// v44 çocuk görevi sürüyor mu.
    var inChallenge: () -> Bool = { false }

    /// v38 çocuk modunda çizim görevi (seviye kurulurken çizim moduna geçilir, öneriler temizlenir).
    private(set) lazy var challenge: KidsChallengeController = {
        let c = KidsChallengeController(store, before: { [unowned self] in chooseMode(.DRAW); handwriting.reset() },
                                        after: { [unowned self] in afterContentReplaced() })
        inChallenge = { [unowned self, unowned c] in (store.canvas?.kind == .KIDS && c.active) || practiceActive() }
        return c
    }()

    /// v50 yetişkin alıştırması sürüyor mu (sürerken çizim önerisi verilmez).
    private var practiceActive: () -> Bool = { false }

    /// v50 yetişkin çizim alıştırmaları (basit ve gelişmiş resim).
    private(set) lazy var practice: PracticeController = {
        let p = PracticeController(store, before: { [unowned self] in chooseMode(.DRAW); handwriting.reset() },
                                   after: { [unowned self] in afterContentReplaced() })
        practiceActive = { [unowned p] in p.active }
        let kids = inChallenge
        inChallenge = { [unowned p] in kids() || p.active }
        return p
    }()

    /// v32 eğitim modu: araç gruplarının adım adım eğitimi (çizim, not alma, çizgi roman, basit türler, çocuk).
    private(set) lazy var tutorial: TutorialController = {
        let t = TutorialController(host: self, store: store)
        inTutorial = { [unowned t] in t.active }
        tutorialAiStep = { [unowned t] in t.aiStep }
        return t
    }()

    init(canvasLocalId: String, deps: EditorDependencies) {
        self.canvasLocalId = canvasLocalId
        self.deps = deps
        canvasPrefs = deps.prefs.canvas(canvasLocalId)
        lastAddItem = canvasPrefs.lastAddItem
        store = EditorStore(saveQueue: deps.canvases.saveQueue, canvasLocalId: canvasLocalId)
        build()
        Task { await load() }
    }

    private func build() {
        let repo = deps.canvases
        let id = canvasLocalId
        store.onPageBackgrounds = { c in repo.saveBackgroundSpec(id, c.background, c.pageBackgrounds) }
        frame = FrameController(store, self)
        backgroundTransform = BackgroundTransformController(store, frame) { [unowned self] in backgroundPerPage() }
        ruler = RulerController { [unowned self] in store.elements }
        ruler.attach(RulerPrefs())
        style = StyleController(store, frame) { [unowned self] in backgroundPerPage() }
        shapes = ShapeController(store, self, brush: { [unowned self] in brush }, style: style)
        diagrams = DiagramController(store, self) { [unowned self] in shapes.newShapeStyle() }
        texts = TextController(store, brush: { [unowned self] in brush }, autoWrap: { [unowned self] in store.canvas?.kind.isNotes == true }) { [unowned self] id in
            if store.canvas?.kind.isNotes == true { inserted([id]) }
        }
        selection = SelectionController(store, self)
        comic = ComicController(store, self, texts: texts) { [unowned self] in brush }
        drawing = DrawingController(store, self) { [unowned self] in brush }
        pages = PageController(store, beforeChange: { [unowned self] in beforePageChange() }, onLimit: { [unowned self] in emit(.pageLimit($0)) })
        guides = GuideController(store, self, repo)
        images = ImageController(store, self, assets: deps.assets, images: deps.images)
        v40 = V40ExportController(store, images: deps.images, repository: repo)
        v45 = V45NotesComicController(store, self, pages)
        formulas = FormulaController(store, self) { [unowned self] in brush }
        pen = PenController(store, self) { [unowned self] in shapes.newShapeStyle() }
        scene = ObjectSceneController(store)
        layers = LayerController(store)
        imageEdit = ImageEditController(store, self)
        models3d = Model3DController(store, self, assets: deps.assets, repository: deps.models3d, results: deps.model3dResults)
        let mt = ModeTemplateSource(deps.shapeDb, deps.shapeModels, mode: { [weak store] in store?.canvas?.kind.shapeMode },
                                    kind: { [weak store] in store?.canvas?.kind })
        modeTemplates = mt
        let learning = GatedTemplateSource(mt, learn: { [unowned self] in !inTutorial() }, kind: { [weak store] in store?.canvas?.kind })
        let prefs = deps.prefs
        handwriting = HandwritingController(
            store, self,
            enabled: { [unowned self] in mode == .DRAW && !inChallenge() && (inTutorial() ? tutorialAiStep() : prefs.handwriting && aiOn()) },
            templates: learning,
            textEnabled: { [unowned self] in inTutorial() || prefs.textSuggest },
            shapeEnabled: { [unowned self] in inTutorial() || prefs.shapeSuggest },
            selectionEnabled: { [unowned self] in
                !inTutorial() && !inChallenge() && aiOn() && prefs.handwriting && prefs.selectionSuggest && (mode.kind == .BASE || mode.transformsSelection)
            },
            lineEnabled: { [unowned self] in inTutorial() || prefs.lineSuggest })
        kidsPaint = KidsPaintController { [unowned self] p in shapes.paintTarget(p, locked: store.lockedIds()) }
        kidsBrush = KidsBrushController(
            regionAt: { [unowned self] p in shapes.brushRegion(p, locked: store.lockedIds()) },
            color: { [unowned self] in paintColor() },
            size: { [unowned self] in brush.settings.sizePx },
            commit: { [unowned self] region, points, size, color in shapes.commitBrush(region, points, sizePx: size, colorHex: color) })
        deps.shapeDb.warmUp()
        brush = BrushController(store, self, palette: deps.palette, onDrawingWidth: { [unowned self] in shapes.setDrawingWidth($0) }, prefs: canvasPrefs)
        store.autosave = prefs.autosave
        prefs.$autosave.sink { [weak self] in self?.store.autosave = $0 }.store(in: &bag)
        store.$selectedIds.sink { [weak self] ids in
            guard let self else { return }
            formulas.onSelectionChanged(ids)
            handwriting.onSelectionChanged(ids)
        }.store(in: &bag)
        if let fill = canvasPrefs.shapeFill { shapes.setNewShapeFill(fill.isEmpty ? nil : fill) }
        // Alt denetleyicilerin değişimleri ekranı yenilesin
        store.objectWillChange.sink { [weak self] _ in self?.objectWillChange.send() }.store(in: &bag)
        shapes.objectWillChange.sink { [weak self] _ in self?.objectWillChange.send() }.store(in: &bag)
        texts.objectWillChange.sink { [weak self] _ in self?.objectWillChange.send() }.store(in: &bag)
        diagrams.objectWillChange.sink { [weak self] _ in self?.objectWillChange.send() }.store(in: &bag)
        formulas.objectWillChange.sink { [weak self] _ in self?.objectWillChange.send() }.store(in: &bag)
    }

    private func load() async {
        let repo = deps.canvases
        await Task.detached { [queue = repo.saveQueue] in queue.flushNow() }.value
        guard var c = repo.getCanvas(canvasLocalId) else { store.load(nil); loaded = true; return }
        deps.recent.opened(canvasLocalId)
        deps.assets.ensureLoaded(c)
        deps.assets.prune(canvasLocalId, c.pages.flatMap { $0 },
                          keep: Set([c.background.assetId].compactMap { $0 } + c.pageBackgrounds.compactMap { $0?.spec?.assetId }))
        brush.adaptTo(c.kind)
        modeTemplates?.warm()
        brush.restore(c.kind, canvasPrefs.lastBrush.flatMap(BrushType.init(rawValue:)), canvasPrefs.lastColor)
        texts.adaptTo(c.width, c.height)
        if let (spec, size) = canvasPrefs.lastText { texts.restore(spec, sizePx: size > 0 ? size : nil) }
        formulas.adaptTo(c.width, c.height)
        // v18 kağıt rengi başka yerde değiştiyse grafikler kağıda uydurulur (v24 her sayfa kendi kağıdına)
        let snapshot = c
        let recolored: [[Stroke]] = await Task.detached {
            if !snapshot.hasPageBackgrounds { return GraphPaper.recolor(snapshot.pages, snapshot.backgroundColorHex) }
            return snapshot.pages.enumerated().map { i, page in GraphPaper.recolor([page], snapshot.paperOf(i))[0] }
        }.value
        if recolored != c.pages { c.pages = recolored }
        // v23 3B çizim tuvalden 3B düzenleyiciye taşındı: eski tuvallerde açık kalmışsa kapanır
        if c.guide.draw3d { c.guide.draw3d = false; repo.saveGuide(c.localId, c.guide) }
        store.load(c)
        store.indexNow()
        loaded = true
        await drawing.cleanOnOpen(c)
    }

    /// Ekran kapanırken: bekleyen kayıt yazılır, eğitim yarıda kaldıysa geri alınır.
    func close() {
        imageEdit.cancelTrace()
        texts.commitOpenDraft()
        store.saveNow()
        deps.canvases.saveQueue.flushNow()
        if store.canvas != nil { deps.recent.closed(canvasLocalId) }
    }

    func emit(_ event: EditEvent) { events.send(event) }

    private func aiOn() -> Bool { store.canvas?.aiEnabled == true }

    // MARK: Panel

    func togglePanel(_ tab: PanelTab) { panel = panel == tab ? nil : tab }
    func closePanel() { panel = nil }

    // MARK: Mod politikası

    func startAction(_ mode: EditMode, target: Set<String>) {
        let current = self.mode
        if current.kind == .BASE { returnStack = [(current, [])] }
        else if current.kind == .EDIT && mode.kind == .TOOL { returnStack.append((current, store.selection)) }
        switch mode.kind {
        case .INSERT: store.select([])
        case .EDIT: if !target.isEmpty { store.select(store.currentIndex().expand(target)) }
        default: break
        }
        applyMode(mode)
    }

    func continueAction(_ mode: EditMode) { applyMode(mode) }

    func finishAction() {
        let current = mode
        switch current.kind {
        case .BASE:
            if current == .DRAW { return }
            if current == .PAINT { setMode(.DRAW) }
            else if current == .SELECT && !store.selection.isEmpty { store.select([]) }
            else if current == .SELECT { setMode(.DRAW) }
            else { toSelect() }
        case .EDIT: toSelect()
        default:
            let (m, sel) = returnStack.popLast() ?? (.SELECT, [])
            if m == .EDIT_PICK { store.select([]); setMode(.EDIT_PICK); return }
            if m.kind == .EDIT {
                let existing = Set(store.strokes.map(\.id))
                let restored = sel.filter { existing.contains($0) }
                if restored.isEmpty { toSelect(); return }
                store.select(restored)
                setMode(m)
            } else { toSelect() }
        }
    }

    private func toSelect() {
        store.select([])
        setMode(.SELECT)
    }

    func endAction() { toSelect() }

    func inserted(_ ids: Set<String>) {
        store.select(store.currentIndex().expand(ids))
        continueAction(.MOVE)
    }

    func onInk(_ stroke: Stroke) {
        if inChallenge() { return }
        if ruler.onTemplate(stroke.points.map(\.x), stroke.points.map(\.y)) { handwriting.exclude(stroke) } else { handwriting.onInk(stroke) }
    }

    /// v37 basit çizimde kağıdın kenarına basılı tutunca genişletme modu.
    func holdPaperEdge() -> Bool {
        guard let kind = store.canvas?.kind, kind.simple, kind != .SIMPLE_NOTES else { return false }
        chooseMode(.EXPAND)
        return true
    }

    func chooseMode(_ mode: EditMode) {
        if mode == .PAINT { paintBrush = false }
        if mode == self.mode && mode.kind != .BASE { finishAction() }
        else if mode.kind == .BASE { setMode(mode) }
        else { startAction(mode) }
    }

    func setMode(_ mode: EditMode) {
        if mode.kind == .BASE || mode.kind == .EDIT { returnStack.removeAll() }
        applyMode(mode)
    }

    private func applyMode(_ mode: EditMode) {
        let from = self.mode
        texts.onModeChange(mode)
        frame.onModeChange(from, mode)
        diagrams.onModeChange(mode)
        shapes.onModeChange(mode)
        pen.onModeChange(mode)
        formulas.onModeChange(mode)
        if mode != from { handwriting.reset() }
        if mode != .SELECT && mode != .SCALE && mode != .DRAW { canvasPick = .NONE }
        if mode == .DRAW && canvasPick != .EYEDROPPER { canvasPick = .NONE }
        if mode != .EDIT_PICK && mode != .SHAPE_EDIT { pendingShapeTool = nil }
        if mode != .EDIT_PICK { pendingSceneTool = nil }
        if mode != from { scene.endAll() }
        if mode != .PAINT { paintBrush = false; kidsBrush.cancel() }
        self.mode = mode
        if mode != .DRAW && mode != .TEXT && panel != .BRUSH { panel = nil }
    }

    private func beforePageChange() {
        handwriting.reset()
        formulas.closeEditor()
        texts.commitOpenDraft()
        diagrams.reset()
        shapes.reset()
        switch mode.kind {
        case .EDIT: endAction()
        case .TOOL: if mode != .TEXT { finishAction() }
        default: break
        }
    }

    // MARK: Geri al / yinele

    func undo() { scene.endAll(); if store.undo() { afterContentReplaced() } }
    func redo() { scene.endAll(); if store.redo() { afterContentReplaced() } }

    /// v20 üç parmakla yana kaydırma.
    func swipeUndo(_ direction: Int) {
        let redo = direction > 0
        let done = redo ? store.redo() : store.undo()
        if done { afterContentReplaced() }
        emit(.swiped(redo: redo, done: done))
    }

    func afterContentReplaced() {
        handwriting.reset()
        formulas.onContentReplaced()
        diagrams.onContentReplaced()
        shapes.onContentReplaced()
        texts.onContentReplaced()
        frame.onContentReplaced()
    }

    // MARK: v43 sahne araçları

    func startSceneTool(_ tool: String) {
        let opened = tool == Self.SCENE_PLACE ? scene.openPlace() : scene.openHarmony()
        if opened { pendingSceneTool = nil; return }
        pendingSceneTool = pendingSceneTool == tool ? nil : tool
        if mode != .EDIT_PICK { setMode(.EDIT_PICK) }
    }

    // MARK: Fırça

    func selectBrush(_ type: BrushType) {
        brush.select(type)
        if type != .ERASER { canvasPrefs.lastBrush = type.rawValue }
    }

    func updateBrushColor(_ hex: String) {
        brush.updateColor(hex)
        _ = texts.setColor(hex)
        if canvasPrefs.lastColor != brush.settings.colorHex { canvasPrefs.lastColor = brush.settings.colorHex }
    }

    // MARK: v43 damlalık ve alanla seçim

    func toggleCanvasPick(_ pick: CanvasPick) {
        let next: CanvasPick = canvasPick == pick ? .NONE : pick
        canvasPick = next
        if (next == .RECT_SELECT || next == .LASSO_SELECT) && mode != .SELECT { setMode(.SELECT) }
    }

    @discardableResult
    func eyedrop(_ point: P) -> String? {
        guard let c = store.canvas else { return nil }
        canvasPick = .NONE
        guard let hex = ColorSampler.sample(c, point.x, point.y) else { return nil }
        updateBrushColor(hex)
        if mode == .PAINT { shapes.setRegionColor(hex) }
        return hex
    }

    func rectSelect(_ a: P, _ b: P) {
        let r = FRect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
        if r.width < 1 || r.height < 1 { return }
        if selection.selectInArea(add: false, { $0.x >= r.left && $0.x <= r.right && $0.y >= r.top && $0.y <= r.bottom }) > 0 { startAction(.SCALE) }
    }

    func lassoSelect(_ points: [P]) {
        if !AreaSelect.usable(points, minSize: 4) { return }
        _ = selection.selectInArea(add: false) { AreaSelect.inside(points, $0) }
    }

    // MARK: Seçim

    func holdMoveAt(_ x: Float, _ y: Float, tolerance: Float) -> Bool {
        let moved = selection.holdMoveAt(x, y, tolerance: tolerance)
        if moved { kidsPaint.hover(nil, hold: -1) }
        return moved
    }

    func arrowFromSelection() {
        guard let c = store.canvas, let bounds = ElementOps.selectionBounds(c.strokes, store.selection) else { return }
        shapes.startArrowFrom(bounds)
    }

    func hideElements(_ elementIds: [String]? = nil) {
        let n = elementIds.map { selection.hide($0) } ?? selection.hideSelected()
        if n > 0 { emit(.hiddenChanged(hidden: n, shown: 0)) }
    }

    func showElements(_ elementIds: [String]? = nil) {
        let n = selection.show(elementIds)
        if n > 0 { emit(.hiddenChanged(hidden: 0, shown: n)) }
    }

    func selectionOrigin() -> P? { ElementOps.selectionBounds(store.strokes, store.selection)?.topLeft }

    func moveSelectionTo(_ x: Float, _ y: Float) {
        guard let o = selectionOrigin() else { return }
        selection.move(x - o.x, y - o.y)
    }

    func centerSelection() {
        guard let c = store.canvas, let b = ElementOps.selectionBounds(store.strokes, store.selection) else { return }
        selection.move(Float(c.width) / 2 - b.center.x, Float(c.height) / 2 - b.center.y)
    }

    // MARK: Kağıt

    func expandSide(_ side: ExpandSide) {
        guard let c = store.canvas else { return }
        func step(_ size: Int) -> Int { max(Int((Float(size) * 0.1 / 10).rounded()) * 10, 10) }
        switch side {
        case .LEFT: frame.resizeCanvas(-step(c.width), 0, c.width, c.height)
        case .RIGHT: frame.resizeCanvas(0, 0, c.width + step(c.width), c.height)
        case .TOP: frame.resizeCanvas(0, -step(c.height), c.width, c.height)
        case .BOTTOM: frame.resizeCanvas(0, 0, c.width, c.height + step(c.height))
        }
    }

    func setCanvasSize(_ width: Int, _ height: Int, anchorX: Float, anchorY: Float) {
        guard let c = store.canvas else { return }
        let w = width.coerceIn(FrameController.MIN_CANVAS, FrameController.MAX_CANVAS)
        let h = height.coerceIn(FrameController.MIN_CANVAS, FrameController.MAX_CANVAS)
        let left = -Int((Float(w - c.width) * anchorX.coerceIn(0, 1)).rounded())
        let top = -Int((Float(h - c.height) * anchorY.coerceIn(0, 1)).rounded())
        frame.resizeCanvas(left, top, left + w, top + h)
    }

    func backgroundPerPage() -> Bool {
        guard let c = store.canvas else { return false }
        return c.kind.paged && c.pageCount > 1
    }

    func setBackgroundColor(_ hex: String, coalesce: String? = nil) {
        _ = frame.setBackgroundColor(hex, coalesce: coalesce, pageOnly: backgroundPerPage())
    }

    func setBackgroundSpec(_ spec: BackgroundSpec) {
        guard let c = store.canvas else { return }
        if backgroundPerPage() { _ = frame.setPageSpec(spec, coalesce: "page-background"); return }
        _ = frame.clearPageSpecs()
        if c.background == spec { return }
        store.updateCanvas { var n = $0; n.background = spec; return n }
        persistBackgrounds()
    }

    func applyBackgroundToAllPages() {
        guard let spec = frame.applyPageToAll() else { return }
        store.updateCanvas { var n = $0; n.background = spec; return n }
        persistBackgrounds()
    }

    private func persistBackgrounds() {
        guard let c = store.canvas else { return }
        deps.canvases.saveBackgroundSpec(canvasLocalId, c.background, c.pageBackgrounds)
    }

    func importBackground(_ data: Data) {
        images.importBackground(data) { [weak self] assetId in
            guard let self else { return }
            var spec = store.canvas?.pageBackground ?? BackgroundSpec()
            spec.imageId = BackgroundSpec.ASSET + assetId
            spec.imageFit = .COVER
            setBackgroundSpec(spec)
        }
    }

    func applyBackground(_ paperHex: String, _ spec: BackgroundSpec, allPages: Bool) {
        guard let c = store.canvas else { return }
        let key = "background-apply-\(DispatchTime.now().uptimeNanoseconds)"
        let paperChanged = c.pagePaperHex.caseInsensitiveCompare(paperHex) != .orderedSame
        let done = paperChanged && !backgroundTransform.onCurrentPage() && frame.setBackgroundColor(paperHex, coalesce: key, pageOnly: false, shared: spec)
        if !done {
            if paperChanged { setBackgroundColor(paperHex, coalesce: key) }
            backgroundTransform.commit(spec, coalesce: key)
        }
        if allPages { applyBackgroundToAllPages() }
    }

    func backgroundSelectable(_ x: Float, _ y: Float) -> Bool {
        guard let c = store.canvas else { return false }
        return !c.pageBackground.isEmpty && x >= 0 && x <= Float(c.width) && y >= 0 && y <= Float(c.height)
    }

    func removeBackground() { backgroundTransform.commit(BackgroundSpec()) }

    func applyFillGradient(_ g: GradientSpec?) {
        style.applyFillGradient(g) { [weak self] g in
            guard let self, let c = store.canvas else { return }
            var spec = c.pageBackground
            spec.gradient = g
            setBackgroundSpec(spec)
        }
    }

    func setTextColor(_ hex: String) {
        if texts.setColor(hex) { return }
        if texts.draft != nil { updateBrushColor(hex) } else { style.setTextFill(hex) }
    }

    // MARK: Ekle

    func takeAddAnchor() -> P? { defer { addAnchor = nil }; return addAnchor }

    func rememberAddItem(_ id: String) {
        lastAddItem = id
        canvasPrefs.lastAddItem = id
    }

    func addHere(_ itemId: String, at: P) {
        rememberAddItem(itemId)
        addAnchor = nil
        switch itemId {
        case "text": startAction(.TEXT); texts.beginAt(at.x, at.y, tolerance: 0)
        case "shape": addAnchor = at; shapes.openPicker()
        case "formula": addAnchor = at; formulas.openPicker()
        case "diagram": addAnchor = at; diagrams.openPicker()
        case "image", "model3d", "balloon", "sfx", "effects", "characters", "locations", "objects", "packs": addAnchor = at
        case "pen": pen.start()
        default: break
        }
    }

    func closeShapePicker() { addAnchor = nil; shapes.closePicker() }
    func selectShapeId(_ id: String) {
        shapes.selectShape(id)
        if let a = takeAddAnchor() { shapes.addShapeAt(UUID().uuidString.lowercased(), a) }
    }
    func setNewShapeFill(_ hex: String?) {
        shapes.setNewShapeFill(hex)
        canvasPrefs.shapeFill = hex
    }
    func closeDiagramPicker() { addAnchor = nil; diagrams.closePicker() }
    func selectDiagram(_ kind: DiagramKind) {
        diagrams.selectKind(kind)
        if let a = takeAddAnchor() { diagrams.addAt(UUID().uuidString.lowercased(), a) }
    }
    func closeFormulaPicker() { addAnchor = nil; formulas.closePicker() }
    func selectFormula(_ template: FormulaTemplate) {
        formulas.selectTemplate(template)
        if let a = takeAddAnchor() { formulas.addAt(UUID().uuidString.lowercased(), a) }
    }

    func confirmText(_ content: String) {
        texts.confirm(content)
        if mode == .TEXT { finishAction() }
    }

    func updateTextSettings(_ settings: TextSettings, before: TextSettings? = nil) {
        texts.updateSettings(settings, before: before)
        let d = texts.defaults
        canvasPrefs.lastText = (d.spec, d.sizePx)
    }

    func replaceStroke(_ stroke: Stroke) {
        guard let c = store.canvas, c.strokes.contains(where: { $0.id == stroke.id }) else { return }
        store.commit(c.strokes.map { $0.id == stroke.id ? stroke : $0 })
    }

    // MARK: v42 yol üzerinde yazı

    func hasTextPathHosts() -> Bool { store.canvas?.strokes.contains(where: TextOnPath.canHost) == true }

    func startTextOnPath(_ content: String) {
        let editing = texts.draft?.editingId
        confirmText(content)
        guard let id = editing ?? (store.selectedIds.count == 1 ? store.selectedIds.first : nil) else { return }
        startTextOnPathFor(id)
    }

    func startTextOnPathFor(_ id: String) {
        guard let c = store.canvas, let text = c.strokes.first(where: { $0.id == id && $0.brushType == .TEXT }) else { return }
        let hosts = c.strokes.filter(TextOnPath.canHost)
        if hosts.count == 1 { _ = attachText(text.id, hosts[0].id) } else if hosts.count > 1 { textPathPick = text.id }
    }

    func pickTextPath(_ p: P, tolerance: Float) -> Bool {
        guard let textId = textPathPick, let c = store.canvas, let host = TextOnPath.hostAt(c.strokes, p, tolerance: tolerance) else { return false }
        textPathPick = nil
        return attachText(textId, host.id)
    }

    func cancelTextPathPick() { textPathPick = nil }

    private func attachText(_ textId: String, _ hostId: String) -> Bool {
        guard let c = store.canvas, let text = c.strokes.first(where: { $0.id == textId }), let host = c.strokes.first(where: { $0.id == hostId }),
              let updated = TextOnPath.attach(text, host) else { return false }
        store.commit(c.strokes.map { $0.id == textId ? updated : $0 })
        store.select([textId])
        return true
    }

    func adjustTextPath(_ id: String, start: Float? = nil, below: Bool? = nil, reversed: Bool? = nil) {
        guard let c = store.canvas, let text = c.strokes.first(where: { $0.id == id }),
              let updated = TextOnPath.adjust(text, start: start, below: below, reversed: reversed) else { return }
        store.commit(c.strokes.map { $0.id == id ? updated : $0 })
    }

    func detachTextFromPath(_ id: String) {
        guard let c = store.canvas, let text = c.strokes.first(where: { $0.id == id }), let updated = TextOnPath.detach(text) else { return }
        store.commit(c.strokes.map { $0.id == id ? updated : $0 })
    }

    // MARK: v29 aktarma

    func transferTargets() -> [CanvasUi] { deps.transfer.targets([.NOTES, .COMIC], exclude: canvasLocalId) }

    func transferTo(_ target: TransferTarget, vector: Bool, selectionOnly: Bool) {
        guard let c = store.canvas, !transferring else { return }
        texts.commitOpenDraft()
        let visible = store.visibleStrokes()
        let ids = store.selection
        let items = selectionOnly && !ids.isEmpty ? visible.filter { ids.contains($0.id) } : visible
        if !items.contains(where: { $0.brushType != .ERASER }) { emit(.transferred(name: nil, empty: true)); return }
        transferring = true
        Task {
            var result: TransferResult?
            if vector { result = deps.transfer.sendStrokes(target, c.name, items) }
            else if let img = await Task.detached(operation: { CanvasTransfer.render(items) }).value {
                result = deps.transfer.sendImage(target, c.name, img)
            }
            if result == nil { AppLog.e("Transfer", "Aktarılamadı") }
            transferring = false
            emit(.transferred(name: result?.name, empty: false))
        }
    }

    // MARK: Kaydetme penceresi

    func saveTabs() -> [SaveTab] {
        switch store.canvas?.kind {
        case .COMIC: return [.PDF, .CBZ, .IMAGE]
        case .NOTES, .SIMPLE_NOTES: return [.PDF, .IMAGE]
        default: return [.IMAGE]
        }
    }

    func openUnifiedSave() {
        guard let c = store.canvas else { return }
        images.openSaveDialog()
        saveTab = saveTabs().first
        if !c.kind.paged { return }
        images.openPdfDialog()
        images.adjustSaveDefaults(image: { o in
            var n = o
            if c.kind == .COMIC && n.cmyk == nil { n.cmyk = CmykOptions() }
            return n
        }, pdf: { o in var n = o; if n.cmyk == nil { n.cmyk = CmykOptions() }; return n })
        if c.kind == .COMIC { v40.openCbz() }
    }

    func selectSaveTab(_ tab: SaveTab) { if saveTab != nil { saveTab = tab } }

    func closeUnifiedSave() {
        saveTab = nil
        images.closeSaveDialog()
        images.closePdfDialog()
        v40.closeCbz()
    }

    func saveNow() {
        texts.commitOpenDraft()
        store.saveNow()
        emit(.saved)
    }

    func saveBeforeLeaving() {
        texts.commitOpenDraft()
        store.saveNow()
    }

    // MARK: Öneri ayarları

    func setSuggestions(_ v: Bool) { deps.prefs.setHandwriting(v); if !v { handwriting.dismiss() } }
    func turnOffSuggestions() { setSuggestions(false); emit(.suggestionsOff) }
    func setTextSuggest(_ v: Bool) { deps.prefs.setTextSuggest(v); if !v { handwriting.dismissText() } }
    func setShapeSuggest(_ v: Bool) { deps.prefs.setShapeSuggest(v); if !v { handwriting.dismissShape() } }
    func turnOffTextSuggestions() { setTextSuggest(false); emit(.textSuggestionsOff) }
    func turnOffShapeSuggestions() { setShapeSuggest(false); emit(.shapeSuggestionsOff) }
    func setLineSuggest(_ v: Bool) { deps.prefs.setLineSuggest(v); if !v { handwriting.dismissLine() } }
    func turnOffLineSuggestions() { setLineSuggest(false); emit(.message(I18n.tr(Self.LINE_SUGGEST_OFF))) }
    func setSelectionSuggest(_ v: Bool) { deps.prefs.setSelectionSuggest(v) }
    func turnOffSelectionSuggestions() { setSelectionSuggest(false); emit(.selectionSuggestionsOff) }

    var aiEnabled: Bool { store.canvas?.aiEnabled == true }

    func setAiEnabled(_ on: Bool) {
        if store.canvas == nil || aiOn() == on { return }
        store.updateCanvas { var n = $0; n.aiEnabled = on; return n }
        if !on { handwriting.reset() }
        deps.canvases.setAiEnabled(canvasLocalId, on)
        emit(on ? .aiOn : .aiOff)
    }

    func canSuggestForSelection(_ ids: Set<String>? = nil) -> Bool {
        let s = ids ?? store.selection
        return aiOn() && handwriting.canSuggest(store.strokes.filter { s.contains($0.id) })
    }

    func suggestInkCorrection() {
        if !aiOn() { emit(.aiOff); return }
        handwriting.suggestForSelection { [weak self] found in if !found { self?.emit(.noCorrection) } }
    }

    // MARK: Boyama (v33 kova, v45 boya fırçası)

    func paintAt(_ point: P) {
        if !shapes.paintAt(point, locked: store.lockedIds()) { emit(.paintMiss) }
        kidsPaint.refresh()
    }

    @discardableResult
    func paintWhole(_ point: P) -> Bool {
        let ok = shapes.paintWhole(point, locked: store.lockedIds())
        if !ok { emit(.paintMiss) }
        kidsPaint.refresh()
        return ok
    }

    func choosePaintBrush() {
        setMode(.PAINT)
        paintBrush = true
    }

    func paintColor() -> String {
        if let c = shapes.regionColor, !c.isEmpty { return c }
        return shapes.regionInk()
    }

    func brushDown(_ point: P) -> Bool {
        guard let c = store.canvas else { return false }
        if let active = c.layers.first(where: { $0.id == store.activeLayer }), !active.visible || active.locked {
            emit(.layerLocked)
            return false
        }
        kidsBrush.begin(point)
        return true
    }

    @discardableResult
    func brushUp(_ point: P? = nil) -> Bool {
        let ok = kidsBrush.end(point)
        kidsPaint.refresh()
        return ok
    }

    // MARK: Düzenle

    func editAt(_ x: Float, _ y: Float, tolerance: Float) -> Bool {
        let index = store.currentIndex()
        guard let element = ElementOps.hitTest(index, P(x, y), tolerance: tolerance, skip: store.lockedIds()) else { return false }
        store.select(index.expand(Set(element.allIds)))
        if let tool = pendingSceneTool, element.brushType == .IMAGE {
            pendingSceneTool = nil
            _ = tool == Self.SCENE_PLACE ? scene.openPlace() : scene.openHarmony()
            return true
        }
        switch Self.kindOf(element.brushType) {
        case .SHAPE:
            let pending = pendingShapeTool
            openShapeWith(pending)
            if pending == .FILL && mode == .SHAPE_EDIT { _ = shapes.paintSelectedAt(P(x, y)) }
        case .DIAGRAM: diagrams.editSelected()
        case .IMAGE: imageEdit.editSelected()
        case .TEXT: setMode(.SELECT); texts.editSelected()
        case .FORMULA: setMode(.SELECT); formulas.edit(nil)
        default: setMode(.SELECT)
        }
        return true
    }

    @discardableResult
    func editSelection() -> Bool {
        switch selectionKind() {
        case .SHAPE: shapes.editSelected()
        case .DIAGRAM: diagrams.editSelected()
        case .IMAGE: imageEdit.editSelected()
        case .TEXT: setMode(.SELECT); texts.editSelected()
        case .FORMULA: setMode(.SELECT); formulas.edit(nil)
        default: return false
        }
        return true
    }

    func editShapeWith(_ tool: ShapeTool) {
        if selectionKind() == .SHAPE { openShapeWith(tool); return }
        pendingShapeTool = pendingShapeTool == tool ? nil : tool
        if mode != .EDIT_PICK { setMode(.EDIT_PICK) }
    }

    func simpleShapeEdit() {
        if mode == .SHAPE_EDIT { return }
        if mode == .EDIT_PICK && pendingShapeTool == .NODES { setMode(.SELECT) } else { editShapeWith(.NODES) }
    }

    func editShapeRegions() {
        shapes.editSelected()
        if mode == .SHAPE_EDIT { shapes.selectTool(.FILL) }
    }

    private func openShapeWith(_ tool: ShapeTool?) {
        shapes.editSelected()
        if let tool, mode == .SHAPE_EDIT { shapes.selectTool(tool) }
        pendingShapeTool = nil
    }

    func canEditSelection(_ ids: Set<String>? = nil) -> Bool { Self.EDITABLE_KINDS.contains(selectionKind(ids)) }

    static func kindOf(_ type: BrushType) -> SelectionKind {
        switch type {
        case .TEXT: return .TEXT
        case .SHAPE: return .SHAPE
        case .FORMULA: return .FORMULA
        case .IMAGE: return .IMAGE
        case .DIAGRAM: return .DIAGRAM
        case .ERASER: return .NONE
        default: return .INK
        }
    }

    func selectionKind(_ ids: Set<String>? = nil) -> SelectionKind {
        let s = ids ?? store.selection
        if s.isEmpty { return .NONE }
        let kinds = Set(store.strokes.filter { s.contains($0.id) }.map { Self.kindOf($0.brushType) }.filter { $0 != .NONE })
        switch kinds.count {
        case 0: return .NONE
        case 1: return kinds.first!
        default: return .MIXED
        }
    }

    func selectedInkCount(_ ids: Set<String>? = nil) -> Int {
        let s = ids ?? store.selection
        return store.strokes.filter { s.contains($0.id) && Self.kindOf($0.brushType) == .INK }.count
    }

    // MARK: Çizim düzeltme

    func recolorSelectedInk(_ hex: String) { inkEdit("ink-color") { var c = $0; c.colorHex = hex; return c } }
    func scaleSelectedInkWidth(_ factor: Float) { inkEdit(nil) { var c = $0; c.sizePx = (c.sizePx * factor).coerceIn(0.5, BrushTuning.MAX_SIZE); return c } }
    func smoothSelectedInk() { inkEdit(nil) { s in if s.points.count < 4 { return s }; var c = s; c.points = InkSmoothing.smooth(s.points); return c } }

    func selectedInkColor(text: Bool = false) -> String {
        let want: SelectionKind = text ? .TEXT : .INK
        return store.strokes.first { store.selection.contains($0.id) && Self.kindOf($0.brushType) == want }?.colorHex ?? brush.settings.colorHex
    }

    private func inkEdit(_ coalesce: String?, _ block: (Stroke) -> Stroke) {
        guard let c = store.canvas else { return }
        let ids = store.selection
        if ids.isEmpty { return }
        var changed = false
        let updated = c.strokes.map { s -> Stroke in
            guard ids.contains(s.id), Self.kindOf(s.brushType) == .INK else { return s }
            let n = block(s)
            if n != s { changed = true }
            return n
        }
        if changed { store.commit(updated, coalesce: coalesce.map { "\($0)-\(ids.sorted().joined().javaHashCode)" }) }
    }

    // MARK: v21–v23 3B çizim ve kamera

    func toggleDraw3d() {
        guard var g = store.canvas?.guide else { return }
        g.draw3d.toggle()
        guides.setGuide(g)
        emit(g.draw3d ? .draw3dOn : .draw3dOff)
    }

    func setPlane3d(_ plane: Plane3D) {
        guard var g = store.canvas?.guide else { return }
        g.draw3d = true; g.plane3d = plane
        guides.setGuide(g)
    }

    func setDepth3d(_ depth: Float, persist: Bool = true) {
        guard var g = store.canvas?.guide else { return }
        g.depth3d = depth.coerceIn(0, 1)
        guides.setGuide(g, persist: persist)
    }

    func setCamera3d(_ yaw: Float, _ pitch: Float, _ persp: Float, persist: Bool = true) {
        guard let g = store.canvas?.guide else { return }
        var y = yaw.truncatingRemainder(dividingBy: 360)
        if y < 0 { y += 360 }
        var next = g
        next.draw3d = true; next.yaw3d = y; next.pitch3d = pitch.coerceIn(-90, 90); next.persp3d = persp.coerceIn(0, 1)
        if next == g && !persist { return }
        guides.setGuide(next, persist: persist)
        store.applyView(persist: persist)
    }

    func orbit3d(_ dYaw: Float, _ dPitch: Float, persist: Bool) {
        guard let g = store.canvas?.guide else { return }
        setCamera3d(g.yaw3d + dYaw, g.pitch3d + dPitch, g.persp3d, persist: persist)
    }

    func presetCamera3d(_ preset: CameraPreset) {
        guard let g = store.canvas?.guide else { return }
        let (y, p): (Float, Float)
        switch preset {
        case .ISO: (y, p) = (GuideSpec.DEFAULT_YAW, GuideSpec.DEFAULT_PITCH)
        case .FRONT: (y, p) = (0, 0)
        case .SIDE: (y, p) = (90, 0)
        case .TOP: (y, p) = (0, 90)
        }
        setCamera3d(y, p, g.persp3d)
    }

    // MARK: Çizgi roman

    func insertBalloon(_ style: BalloonStyle, speakers: Int, options: BalloonTextOptions = BalloonTextOptions()) {
        comic.insertBalloon(style, speakers: speakers, center: takeAddAnchor(), options: options)
    }
    func insertSfx(_ preset: SfxPreset) { comic.insertSfx(preset, center: takeAddAnchor()) }
    func insertSfxText(_ options: BalloonTextOptions) { comic.insertSfxText(options, center: takeAddAnchor()) }
    func insertEffect(_ def: EffectDef, options: EffectOptions) { comic.insertEffect(def, center: takeAddAnchor(), options: options) }

    private func centerOrAnchor() -> P? {
        guard let c = store.canvas else { return nil }
        return takeAddAnchor() ?? P(Float(c.width) / 2, Float(c.height) / 2)
    }

    func insertCharacter(_ variant: ComicVariant) {
        guard let center = centerOrAnchor() else { return }
        let fraction: Float = variant.group == VariantGroup.EXPRESSION.id ? 0.32 : ComicController.CHARACTER_HEIGHT
        images.insertAssetImage(variant.file, heightFraction: fraction, center: center)
    }

    func insertComicProp(_ kind: ComicAssetKind, _ prop: ComicProp) {
        guard let center = centerOrAnchor() else { return }
        let longSide: Float = kind == .EFFECT ? 0.34 : 0.26
        images.insertAssetImage(prop.file, heightFraction: longSide * Float(prop.h) / Float(max(prop.w, prop.h, 1)), center: center)
    }

    func insertPackImage(_ path: String, _ w: Int, _ h: Int) {
        guard let center = centerOrAnchor() else { return }
        images.insertAssetImage(path, heightFraction: 0.30 * Float(max(h, 1)) / Float(max(w, h, 1)), center: center)
    }

    func comicPanelRects() -> [Int: FRect] { comic.panelSet()?.strokes.mapValues { Backdrop.bounds($0) } ?? [:] }

    func panelAt(_ p: P) -> Int? {
        comicPanelRects().first { p.x >= $0.value.left && p.x <= $0.value.right && p.y >= $0.value.top && p.y <= $0.value.bottom }?.key
    }

    func insertLocationBackground(_ b: ComicBackground, leaf: Int?, onDone: @escaping (String?) -> Void = { _ in }) {
        guard let c = store.canvas else { return }
        let panel = leaf.flatMap { comic.panelSet()?.strokes[$0] }
        let rect = panel.map { Backdrop.bounds($0) } ?? FRect(0, 0, Float(c.width), Float(c.height))
        images.insertBackdrop(b.file, target: rect, clip: panel.flatMap { Backdrop.panelClip($0) }, onDone: onDone)
    }

    func placeLocationBackgroundDirectly(_ b: ComicBackground) -> Bool {
        let anchor = takeAddAnchor()
        let panels = comicPanelRects()
        let leaf = anchor.flatMap { a in panels.first { a.x >= $0.value.left && a.x <= $0.value.right && a.y >= $0.value.top && a.y <= $0.value.bottom }?.key }
        if leaf != nil || panels.isEmpty { insertLocationBackground(b, leaf: leaf); return true }
        return false
    }

    /// v41 mekân seçildi: panel yoksa ya da ekle noktası bir paneldeyse hemen, yoksa yerleştirme katmanıyla.
    func pickLocation(_ b: ComicBackground) {
        if !placeLocationBackgroundDirectly(b) { placingLocation = b }
    }

    func placeLocation(_ leaf: Int?) {
        guard let b = placingLocation else { return }
        placingLocation = nil
        insertLocationBackground(b, leaf: leaf)
    }

    func addPanel(_ leaf: Int?, rows: Bool) -> Int? {
        var added: Int?
        comic.editPanels { page in
            guard let r = leaf == nil ? page.append() : page.split(leaf!, rows: rows) else { return nil }
            added = r.1
            return r.0
        }
        return added
    }

    // MARK: 3B şekiller

    func insertModel3d(_ modelId: String) { models3d.insert(modelId, center: takeAddAnchor()) }
    func insertCatalogModel3d(_ catalogId: String) { models3d.insertCatalog(catalogId, center: takeAddAnchor()) }
    func importGlbToCanvas(_ bytes: [UInt8], name: String, maxTriangles: Int, save: Bool) {
        models3d.importGlb(bytes, name: name, maxTriangles: maxTriangles, save: save, center: takeAddAnchor())
    }
}
