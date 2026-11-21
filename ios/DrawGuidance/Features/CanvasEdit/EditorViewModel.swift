import Combine
import Foundation

/// v43 tuval üstü seçme araçları (Android `CanvasPick`).
enum CanvasPick { case NONE, EYEDROPPER, RECT_SELECT, LASSO_SELECT, MAGIC_WAND, LASSO_FILL }

/// v51 notta açılan geçici resim tuvali (tam ekran pencere öğesi).
struct TempEditDoc: Identifiable, Equatable { let id: String }

/// Birleşik kaydetme penceresinin sekmeleri.
enum SaveTab { case IMAGE, PDF, CBZ, SVG, PSD, DXF }

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
    /// v56 canlı yayın sahip kimliği (bu düzenleyici örneği; `ShareManager.attachEditor`).
    let liveOwner = UUID().uuidString
    let deps: EditorDependencies
    private let canvasPrefs: CanvasPrefs

    @Published private(set) var mode: EditMode = .DRAW
    @Published private(set) var panel: PanelTab?
    @Published private(set) var canvasPick: CanvasPick = .NONE
    /// v56 hızlı fırça seçici (son kullanılanlar, favoriler, köşe; mod başına).
    let quickBrush = QuickBrushStore()
    /// v56 damlalığın canlı renk dairesi: tuvaldeki nokta, altındaki renk, önceki renk.
    struct Loupe: Equatable { var point: P; var hex: String?; var previous: String; var inside: Bool }
    @Published private(set) var loupe: Loupe?
    private var loupeSampleAt: P?
    /// v56 çizim sürüyor mu (hızlı fırça şeridi kapanır, düğme %40 saydam) ve kalemin son noktası.
    @Published private(set) var drawingNow = false
    @Published var penPoint: P?
    /// v56 renk penceresindeki damlalıktan sonra pencere yeniden açılacak mı.
    @Published var reopenColorSheet = false
    /// v56 aşama 5 doku seçicisinin hedefi: "area", "page" ya da "layer:<kimlik>"; nil: kapalı.
    @Published var textureFillTarget: String?
    /// v56 aşama 6A Nesne menüsü açık mı.
    @Published var objectMenuOpen = false
    /// v56 aşama 10 "Açıyı değiştir" penceresinin modeli (nil: kapalı).
    @Published var angle3dModel: Model3D?
    /// v56 sihirli değnek ölçütü ve benzerlik toleransı (0..1).
    @Published var wandCriterion: V56Selection.WandCriterion = .COLOR
    @Published private(set) var wandTolerance: Float = 0.15
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
    /// v51 bu tuval nottaki bir resmin geçici düzenleme tuvali mi (otomatik kayıt kapalı; Kaydet resmi nota aktarır).
    let tempSession: TempCanvases.Session?
    var isTempImageEdit: Bool { tempSession != nil }
    /// v52 geçici tuval nottaki bir şekil için.
    var isTempShapeEdit: Bool { tempSession?.shape == true }
    /// v51 geçici tuval kaydedildi ya da bırakıldı: ekran kapanır.
    @Published private(set) var tempClosed = false
    /// v51 notta açılacak geçici resim tuvali.
    @Published var tempEditRequest: TempEditDoc?
    private var tempFinished = false
    private var appliedTempResults = Set<String>()
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
    /// v56 2C-3 sayfalar arası kes, kopyala, yapıştır.
    private(set) var clipboard: ClipboardController!
    private(set) var comic: ComicController!
    private(set) var drawing: DrawingController!
    private(set) var pages: PageController!
    private(set) var guides: GuideController!
    private(set) var images: ImageController!
    /// v56 aşama 6 Dönüştür paneli ve Sıvılaştır.
    private(set) var transform: TransformController!
    private(set) var liquify: LiquifyController!
    private(set) var v40: V40ExportController!
    private(set) var v45: V45NotesComicController!
    private(set) var formulas: FormulaController!
    /// v52 not almada tablo ekleme ve düzenleme.
    private(set) var tables: TableController!
    /// v52 foto düzenleme modu.
    private(set) var photo: PhotoEditController!
    /// v52 not almada ve çizgi romanda resim düzenlemenin nerede yapılacağı sorulur (resim elemanının kimliği).
    @Published var imageEditChoice: String?
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

    /// v54 öneri yok: çocuk görevi, yetişkin alıştırması ya da şekil kalıbı oturumu (kalıp seçimi ve kalıpla çizim).
    func suggestionsBlocked() -> Bool {
        inChallenge() || ruler.session == .TEMPLATE || ruler.session == .TEMPLATE_PICK
    }

    /// v38 çocuk modunda çizim görevi (seviye kurulurken çizim moduna geçilir, öneriler temizlenir).
    private(set) lazy var challenge: KidsChallengeController = {
        let c = KidsChallengeController(store, before: { [unowned self] in chooseMode(.DRAW); handwriting.reset() },
                                        after: { [unowned self] in afterContentReplaced() })
        inChallenge = { [unowned self, unowned c] in (store.canvas?.kind == .KIDS && c.active) || practiceActive() }
        return c
    }()

    /// v50 yetişkin alıştırması sürüyor mu (sürerken çizim önerisi verilmez).
    /// v52 çizim yardımcıları (kompozisyon kılavuzu, kontrol önizlemesi; yalnız görünüm).
    let helpers = DrawHelpersState()
    /// v53 canlı, nesne ve eşya yapı kılavuzları (yalnız görünüm, tuvale yazılmaz).
    let structure = StructureGuideState()
    /// v53 not almada hızlı yazı kutusu.
    let quickText = QuickTextState()
    private var hygieneClosed = false

    private var practiceActive: () -> Bool = { false }

    /// v50 yetişkin çizim alıştırmaları (basit ve gelişmiş resim).
    private(set) lazy var practice: PracticeController = {
        let p = PracticeController(store, before: { [unowned self] in chooseMode(.DRAW); handwriting.reset() },
                                   after: { [unowned self] in afterContentReplaced() })
        practiceActive = { [unowned p] in p.active }
        p.newCanvas = { [unowned self] index in practiceInNewCanvas(index) }
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
        MemoryHygiene.editorOpened()
        canvasPrefs = deps.prefs.canvas(canvasLocalId)
        tempSession = TempCanvases.session(canvasLocalId)
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
        clipboard = ClipboardController(store, self)
        comic = ComicController(store, self, texts: texts) { [unowned self] in brush }
        drawing = DrawingController(store, self) { [unowned self] in brush }
        pages = PageController(store, beforeChange: { [unowned self] in beforePageChange() }, onLimit: { [unowned self] in emit(.pageLimit($0)) })
        guides = GuideController(store, self, repo)
        images = ImageController(store, self, assets: deps.assets, images: deps.images)
        transform = TransformController(
            store,
            loadSnap: { [unowned self] in UserDefaults.standard.string(forKey: "v56.snap.\(store.canvas?.kind.rawValue ?? "ART")") },
            saveSnap: { [unowned self] v in UserDefaults.standard.set(v, forKey: "v56.snap.\(store.canvas?.kind.rawValue ?? "ART")") },
            fitTarget: { [unowned self] x, y in
                comicPanelRects().values.first { $0.contains(P(x, y)) }.map { [$0.left, $0.top, $0.right, $0.bottom] }
            })
        liquify = LiquifyController(store)
        v40 = V40ExportController(store, images: deps.images, repository: repo)
        v45 = V45NotesComicController(store, self, pages)
        formulas = FormulaController(store, self) { [unowned self] in brush }
        tables = TableController(store, self)
        photo = PhotoEditController(store, self, assets: deps.assets)
        // v52 foto düzenleme açıkken yayına ara verilir, kapanınca sürer
        photo.$openId.map { $0 != nil }.removeDuplicates().dropFirst().sink { [weak self] open in
            guard let self else { return }
            if open { ShareManager.shared.pauseLive(canvasLocalId, owner: liveOwner + ":photo") }
            else { ShareManager.shared.resumeLive(canvasLocalId, owner: liveOwner + ":photo"); if let c = store.canvas { ShareManager.shared.publishLive(c) } }
        }.store(in: &bag)
        pen = PenController(store, self) { [unowned self] in shapes.newShapeStyle() }
        scene = ObjectSceneController(store)
        layers = LayerController(store)
        imageEdit = ImageEditController(store, self)
        models3d = Model3DController(store, self, assets: deps.assets, repository: deps.models3d)
        let mt = ModeTemplateSource(deps.shapeDb, deps.shapeModels, mode: { [weak store] in store?.canvas?.kind.shapeMode },
                                    kind: { [weak store] in store?.canvas?.kind })
        modeTemplates = mt
        let learning = GatedTemplateSource(mt, learn: { [unowned self] in !inTutorial() }, kind: { [weak store] in store?.canvas?.kind })
        let prefs = deps.prefs
        handwriting = HandwritingController(
            store, self,
            enabled: { [unowned self] in mode == .DRAW && !suggestionsBlocked() && (inTutorial() ? tutorialAiStep() : prefs.handwriting && aiOn()) },
            templates: learning,
            textEnabled: { [unowned self] in inTutorial() || prefs.textSuggest },
            shapeEnabled: { [unowned self] in inTutorial() || prefs.shapeSuggest },
            selectionEnabled: { [unowned self] in
                !inTutorial() && !suggestionsBlocked() && aiOn() && prefs.handwriting && prefs.selectionSuggest && (mode.kind == .BASE || mode.transformsSelection)
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
        if tempSession != nil {
            // v51 geçici resim tuvalinde otomatik kayıt hep kapalı
            store.autosave = false
            TempCanvases.setActive(canvasLocalId, true)
        } else {
            store.autosave = prefs.autosave
            prefs.$autosave.sink { [weak self] in self?.store.autosave = $0 }.store(in: &bag)
        }
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
        if tempSession == nil { deps.recent.opened(canvasLocalId) }
        deps.assets.ensureLoaded(c)
        // v51 geçici tuvalden gelip henüz nota aktarılmamış resimler de korunur
        deps.assets.prune(canvasLocalId, c.pages.flatMap { $0 },
                          keep: Set([c.background.assetId].compactMap { $0 } + c.pageBackgrounds.compactMap { $0?.spec?.assetId }
                                    + TempCanvases.pendingAssetIds(canvasLocalId)).union(brush.stampAssetIds()))
        brush.adaptTo(c.kind)
        modeTemplates?.warm()
        brush.restore(c.kind, canvasPrefs.lastBrush.flatMap(BrushType.init(rawValue:)), canvasPrefs.lastColor)
        // v56 hızlı fırça seçici: bu modun son kullanılanları ve favorileri
        quickBrush.bind(c.kind)
        if V56Flags.enabled(.QUICK_BRUSH, c.kind) { quickBrush.used(brush.settings.brushType) }
        // v56 resim uçları bu tuvalde de bulunsun (başka tuvalde oluşturulduysa kopyalanır)
        ensureStampAssets()
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
        // v51 notta: geçici tuvalde düzenlenen resimler nota aktarılır, açık kalmamış geçici tuvaller silinir
        if (c.kind.isNotes || c.kind == .COMIC) && tempSession == nil {
            TempCanvases.results.sink { [weak self] r in
                guard let self, r.sourceId == canvasLocalId else { return }
                applyTempResult(r)
            }.store(in: &bag)
            TempCanvases.pending(for: canvasLocalId).forEach { applyTempResult($0) }
            TempImageEditor.cleanup(sourceId: canvasLocalId, deps: deps)
        }
        await drawing.cleanOnOpen(c)
    }

    /// v51 notta seçili resim geçici gelişmiş çizim tuvalinde düzenlenir. v52 not almada ve çizgi romanda önce nerede
    /// düzenleneceği sorulur (foto düzenleme / gelişmiş çizim / çizgi romanda burada).
    func editImageElsewhere(_ image: Stroke) -> Bool {
        guard let c = store.canvas, c.kind.isNotes || c.kind == .COMIC, tempSession == nil else { return false }
        imageEditChoice = image.id
        return true
    }

    func chooseImageEdit(_ choice: ImageEditChoice) {
        guard let id = imageEditChoice else { return }
        imageEditChoice = nil
        guard let image = store.canvas?.strokes.first(where: { $0.id == id && $0.brushType == .IMAGE }) else { return }
        switch choice {
        case .ADVANCED: startTempImage(image)
        case .PHOTO: photo.open(image.id)
        case .HERE: store.select([image.id]); startAction(.IMAGE_EDIT, target: [image.id])
        }
    }

    /// v52 gelişmiş/basit çizimde Resmi düzenle satırındaki Foto düzenle.
    func openPhotoEditor() { photo.open() }

    private func startTempImage(_ image: Stroke) {
        guard tempSession == nil else { return }
        texts.commitOpenDraft()
        store.saveNow()
        if let id = TempImageEditor.start(sourceId: canvasLocalId, image: image, deps: deps) {
            ShareManager.shared.pauseLive(canvasLocalId, owner: liveOwner + ":temp")
            tempEditRequest = TempEditDoc(id: id)
        } else {
            emit(.message(L("v51_temp_image_failed")))
        }
    }

    /// v52 notta seçili düz şekil gelişmiş çizim tuvalinde düzenlenir; kaydedilince nota döner. Yayın varsa ara verilir.
    func editTable(_ id: String) -> Bool { tables.edit(id); return true }

    func editShapeElsewhere(_ shape: Stroke) -> Bool {
        guard let c = store.canvas, c.kind.isNotes, tempSession == nil else { return false }
        texts.commitOpenDraft()
        store.saveNow()
        if let id = TempImageEditor.startShape(sourceId: canvasLocalId, shape: shape, deps: deps) {
            ShareManager.shared.pauseLive(canvasLocalId, owner: liveOwner + ":temp")
            tempEditRequest = TempEditDoc(id: id)
        } else {
            emit(.message(L("v52_temp_edit_failed")))
        }
        return true
    }

    /// v51 geçici tuvalde düzenlenen resim nottaki resmin yerine geçer (tek geri alma adımı).
    private func applyTempResult(_ r: TempCanvases.Result) {
        guard appliedTempResults.insert(r.tempId).inserted, var c = store.canvas else { return }
        guard let page = c.pages.firstIndex(where: { $0.contains { $0.id == r.strokeId } }) else { TempCanvases.consumed(r); return }
        if page != c.currentPage {
            store.showPage(page)
            guard let shown = store.canvas else { return }
            c = shown
        }
        if let data = r.strokes {
            // v52 şekil: geçici tuvaldeki elemanlar şeklin yerine geçer
            let edited = TempShapeGeometry.decode(data)
            for a in edited.compactMap(\.assetId) where !CanvasImageCache.contains(a) {
                if let d = deps.assets.bytes(a), let img = CanvasImageCache.decode(d) { CanvasImageCache.put(a, img) }
            }
            store.commit(TempShapeGeometry.replace(c.strokes, r.strokeId, edited))
            TempCanvases.consumed(r)
            emit(.message(L("v52_temp_shape_saved")))
            return
        }
        if !CanvasImageCache.contains(r.assetId), let data = deps.assets.bytes(r.assetId), let img = CanvasImageCache.decode(data) {
            CanvasImageCache.put(r.assetId, img)
        }
        store.commit(c.strokes.map { $0.id == r.strokeId ? TempImageGeometry.replace($0, r) : $0 })
        TempCanvases.consumed(r)
        emit(.message(L("v51_temp_image_saved")))
    }

    /// v51 geçici tuvali kaydeder: resim nota aktarılır, geçici tuval silinir, ekran kapanır.
    func commitTempImage() {
        guard tempSession != nil, !tempFinished, let c = store.canvas else { return }
        texts.commitOpenDraft()
        tempFinished = true
        TempImageEditor.commit(canvasLocalId, canvas: store.canvas ?? c, deps: deps)
        tempClosed = true
    }

    /// v51 düzenleyici bu tuvale geçsin (yeni alıştırma yeni tuvalde).
    @Published var switchToCanvas: String?

    /// v51 başka bir alıştırmaya geçilirken önceki alıştırmada çizim vardı: bu tuval kaydedilir, aynı tür, boyut, kâğıt ve
    /// klasörde yeni tuval oluşturulur, alıştırma orada açılır (Android `practiceInNewCanvas`).
    private func practiceInNewCanvas(_ index: Int) {
        guard let c = store.canvas else { return }
        let list = PracticeLevels.forCanvas(c.kind)
        guard list.indices.contains(index) else { return }
        texts.commitOpenDraft()
        store.saveNow()
        deps.canvases.saveQueue.flushNow()
        let name = I18n.tr(list[index].title) + " · \(index + 1)"
        let created = deps.canvases.create(kind: c.kind, name: name, width: c.width, height: c.height, paperHex: c.backgroundColorHex,
                                           aiEnabled: c.aiEnabled, background: c.background, folderId: c.folderId)
        PracticeHandoff.put(created.localId, index)
        switchToCanvas = created.localId
    }

    /// Ekran kapanırken: bekleyen kayıt yazılır, eğitim yarıda kaldıysa geri alınır.
    func close() {
        // v53 kapanan tuvalin çizim önbellekleri bellekte tutulmaz (bkz. MemoryHygiene)
        if !hygieneClosed { hygieneClosed = true; MemoryHygiene.editorClosed() }
        structure.clear()
        quickText.close()
        // v51 geçici resim tuvali kaydedilmeden kapanırsa silinir (hiç yazılmaz)
        if tempSession != nil {
            imageEdit.cancelTrace()
            if !tempFinished { tempFinished = true; TempImageEditor.discard(canvasLocalId, deps: deps) }
            TempCanvases.setActive(canvasLocalId, false)
            tempClosed = true
            return
        }
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
        // v54 kalıpla çizilen çizgi sonra da öneriye katılmaz
        if suggestionsBlocked() { if !inChallenge() { handwriting.exclude(stroke) }; return }
        if ruler.onTemplate(stroke.points.map(\.x), stroke.points.map(\.y)) { handwriting.exclude(stroke) } else { handwriting.onInk(stroke) }
    }

    /// v37 basit çizimde kağıdın kenarına basılı tutunca genişletme modu.
    func holdPaperEdge() -> Bool {
        guard let kind = store.canvas?.kind, kind.simple, !kind.isNotes else { return false }
        chooseMode(.EXPAND)
        return true
    }

    func chooseMode(_ mode: EditMode) {
        // v51 not almada kalem aracı (yol) yok
        if mode == .PEN && store.canvas?.kind.isNotes == true { return }
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
        // v56 aşama 9C bıçak yalnız Çiz'de, şekil oluşturucu yalnız Seç'te
        if mode != .DRAW && knife { stopKnife() }
        if mode != .SELECT && shapeBuilder != nil { cancelShapeBuilder() }
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
        tables.onContentReplaced()
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

    /// v53 silgi seçiliyken fırça düğmesinde gösterilecek son çizim fırçası.
    var lastDrawingBrush: BrushType { canvasPrefs.lastBrush.flatMap { BrushType(rawValue: $0) }.flatMap { $0 == .ERASER ? nil : $0 } ?? .INK_PEN }

    func selectBrush(_ type: BrushType) {
        brush.select(type)
        if type != .ERASER { canvasPrefs.lastBrush = type.rawValue }
        if v56(.QUICK_BRUSH) { quickBrush.used(type) }
    }

    // MARK: v56 hızlı fırça seçici (2) ve canlı renk dairesi (2D-5)

    /// İki parmakla dokunma: son iki fırça arasında geçiş.
    @discardableResult
    func quickSwap() -> Bool {
        guard v56(.QUICK_BRUSH), mode.allowsDrawTools, let prev = quickBrush.lists.previous(brush.settings.brushType) else { return false }
        selectBrush(prev)
        return true
    }

    func drawingChanged(_ active: Bool) {
        drawingNow = active
        if active { quickBrush.open = false } else { penPoint = nil }
    }

    func eyedropLoupeOn() -> Bool { v56(.EYEDROPPER_LOUPE) }

    /// Damlalık sürüklenirken (nil: bırakıldı ya da iptal). 2 px'ten az kıpırdamada yeniden örneklenmez.
    func eyedropHover(_ point: P?) {
        guard let point, eyedropLoupeOn(), let c = store.canvas else { loupe = nil; loupeSampleAt = nil; return }
        let inside = EyedropLoupe.inside(point.x, point.y, Float(c.width), Float(c.height))
        var hex: String?
        if let prev = loupe, let last = loupeSampleAt, hypot(last.x - point.x, last.y - point.y) < 2 { hex = prev.hex }
        else if inside { hex = ColorSampler.sample(c, point.x, point.y); loupeSampleAt = point }
        loupe = Loupe(point: point, hex: hex, previous: loupe?.previous ?? brush.settings.colorHex, inside: inside)
    }

    func eyedropFromColorSheet() {
        reopenColorSheet = true
        if canvasPick != .EYEDROPPER { toggleCanvasPick(.EYEDROPPER) }
    }

    // MARK: v56 aşama 5: gren, resimden fırça, doku dolgusu

    /// Resim uçları bu tuvalin varlıklarında yoksa kopyalanır; hiçbir yerde yoksa fırça kendi ucuna döner.
    private func ensureStampAssets() {
        for id in brush.stampAssetIds() {
            switch deps.assets.canvasOf(id) {
            case nil: brush.replaceStamp(id, nil)
            case canvasLocalId?: deps.assets.ensureLoaded(id: id)
            default:
                if let n = deps.assets.copyTo(id, canvasLocalId) { brush.replaceStamp(id, n); deps.assets.ensureLoaded(id: n) }
            }
        }
    }

    /// Seçilen resimden fırça ucu: seçili fırça damgayla çizilemiyorsa keçeli kaleme geçilir.
    func createImageBrush(_ data: Data) {
        images.createImageBrush(data) { [weak self] id in
            guard let self else { return }
            if !BrushTuning.supportsImageTip(self.brush.settings.brushType) { self.selectBrush(.MARKER) }
            self.brush.updateTuning { t in var n = t; n.stampAssetId = id; return n }
        }
    }

    func removeImageBrush() { brush.updateTuning { t in var n = t; n.stampAssetId = nil; return n } }

    /// Gren: doku seç (nil: kapalı), ölçek, güç, kayar.
    func setGrainTexture(_ id: String?) {
        brush.updateTuning { t in
            var n = t
            if let id { var g = t.grain ?? GrainSpec(textureId: id); g.textureId = id; n.grain = g } else { n.grain = nil }
            return n
        }
    }

    func setGrain(_ change: (GrainSpec) -> GrainSpec) {
        brush.updateTuning { t in var n = t; n.grain = t.grain.map(change); return n }
    }

    /// Dokuyla doldur: seçim alanı varsa alanın içi, yoksa (ya da `wholePage`) etkin katmanın bütün sayfası; tek geri alma adımı.
    @discardableResult
    func textureFill(_ textureId: String, wholePage: Bool = false) -> Bool {
        guard let c = store.canvas else { return false }
        let area = wholePage ? nil : store.area.flatMap { $0.inverted ? nil : $0.points }
        let pts = area ?? V56Textures.pageRect(Float(c.width), Float(c.height))
        let fill = V56Selection.lassoFill(id: UUID().uuidString, pts, colorHex: brush.settings.colorHex, opacity: brush.settings.opacity,
                                          order: c.strokes.count, grain: GrainSpec(textureId: textureId))
        store.commit(c.strokes + [fill], useArea: false)
        return true
    }

    /// v56 Dönüştür panelini açar (seçim gerekir).
    @discardableResult
    func openTransform(_ kind: TransformKind = .UNIFORM) -> Bool {
        guard v56(.TRANSFORM_PANEL) else { return false }
        liquify.cancel()
        return transform.open(kind)
    }

    /// v56 Yansıma: seçimin altına dikey çevrilmiş, dalgalı ve solan kopya (tek geri alma adımı).
    @discardableResult
    func reflectSelection() -> Bool {
        guard let c = store.canvas else { return false }
        let ids = store.selection
        guard !ids.isEmpty, let b = ElementOps.selectionBounds(c.strokes, ids) else { return false }
        let before = Set(c.strokes.map(\.id))
        let out = Reflection.create(c.strokes, ids, b.left, b.top, b.right, b.bottom, gap: 4, newId: { _ in UUID().uuidString.lowercased() })
        if out.count == c.strokes.count { return false }
        store.commit(out, useArea: false)
        store.select(Set(out.map(\.id).filter { !before.contains($0) }))
        return true
    }

    /// v56 Sıvılaştır: seçili resim (yoksa seçim ya da etkin katman resme çevrilerek) ağla deforme edilir.
    func startLiquify() {
        guard v56(.REFLECTION_LIQUIFY), !liquify.isOpen, let c = store.canvas else { return }
        transform.cancel()
        let active = store.activeLayer
        var ids = store.selection
        if ids.isEmpty { ids = Set(c.strokes.filter { !$0.hidden && Layers.of($0, c.layers) == active }.map(\.id)) }
        if ids.isEmpty { return }
        let sel = c.strokes.filter { ids.contains($0.id) }
        if sel.count == 1, sel[0].brushType == .IMAGE { liquify.begin(sel[0].id); return }
        Task { if let id = await images.rasterize(ids) { liquify.begin(id) } }
    }

    // MARK: v56 aşama 6A (2E): hizala ve dağıt, vektör efektleri, dönüşümü tekrarla

    private func unitBounds(_ s: Stroke) -> [Float]? { ElementOps.bounds(s).map { [$0.left, $0.top, $0.right, $0.bottom] } }

    /// Ana nesne: seçimde en son dokunulan eleman (yoksa en büyük birim).
    func keyObjectId() -> String? {
        guard let c = store.canvas else { return nil }
        let sel = store.selection
        if let p = selection.lastPicked, sel.contains(p) { return p }
        return V56Arrange.units(c.strokes, sel, boundsOf: unitBounds).max { ($0.r - $0.l) * ($0.b - $0.t) < ($1.r - $1.l) * ($1.b - $1.t) }?.ids.first
    }

    @discardableResult
    func alignObjects(_ align: V56Arrange.Align, _ ref: V56Arrange.Ref) -> Bool {
        guard let c = store.canvas else { return false }
        let units = V56Arrange.units(c.strokes, store.selection, boundsOf: unitBounds)
        if units.isEmpty { return false }
        let off = V56Arrange.align(units, align, ref, paperW: Float(c.width), paperH: Float(c.height), keyId: keyObjectId())
        if off.allSatisfy({ $0.0 == 0 && $0.1 == 0 }) { return false }
        store.commit(V56Arrange.applyOffsets(c.strokes, units, off), useArea: false)
        return true
    }

    @discardableResult
    func distributeObjects(_ mode: V56Arrange.Distribute) -> Bool {
        guard let c = store.canvas else { return false }
        let units = V56Arrange.units(c.strokes, store.selection, boundsOf: unitBounds)
        let off = V56Arrange.distribute(units, mode)
        if off.allSatisfy({ $0.0 == 0 && $0.1 == 0 }) { return false }
        store.commit(V56Arrange.applyOffsets(c.strokes, units, off), useArea: false)
        return true
    }

    private var effectBase: [Stroke]?

    func previewEffect(_ effect: V56Arrange.Effect, _ amount: Float) {
        if effectBase == nil { effectBase = store.canvas?.strokes }
        guard let base = effectBase else { return }
        store.previewStrokes(V56Arrange.effect(base, store.selection, effect, amount))
    }

    @discardableResult
    func applyEffect(_ effect: V56Arrange.Effect, _ amount: Float) -> Bool {
        guard let base = effectBase ?? store.canvas?.strokes else { return false }
        store.endPreview(); effectBase = nil
        let out = V56Arrange.effect(base, store.selection, effect, amount)
        if out == base { return false }
        store.commit(out, useArea: false)
        return true
    }

    func cancelEffect() { if effectBase != nil { store.endPreview(); effectBase = nil } }

    func canEffect() -> Bool { store.canvas?.strokes.contains { store.selection.contains($0.id) && V56Arrange.canEffect($0) } == true }

    @discardableResult
    func repeatTransform(duplicate: Bool) -> Bool { transform.applyLast(duplicate: duplicate) }

    // MARK: v56 aşama 9C yol işlemleri, bıçak, efektler, şekil oluşturucu

    @discardableResult
    private func pathCommit(_ out: [Stroke]?, selectNew: Bool = true) -> Bool {
        guard let c = store.canvas, let out, out != c.strokes else { return false }
        let before = Set(c.strokes.map(\.id))
        store.commit(out, useArea: false)
        if selectNew {
            let fresh = Set(out.filter { !before.contains($0.id) }.map(\.id))
            store.select(fresh.isEmpty ? store.selection.filter { id in out.contains { $0.id == id } } : fresh)
        }
        return true
    }

    /// Seçimde yol işlemine girebilen eleman sayısı.
    func pathOperableCount() -> Int { store.canvas?.strokes.filter { store.selection.contains($0.id) && V56PathOps.operable($0) }.count ?? 0 }

    @discardableResult
    func pathOp(_ op: V56PathOps.PathOp) -> Bool {
        guard let c = store.canvas else { return false }
        return pathCommit(V56PathOps.apply(c.strokes, store.selection, op, newId: newId))
    }

    @discardableResult func outlineStroke() -> Bool { store.canvas.map { pathCommit(V56PathOps.outlineStroke($0.strokes, store.selection, newId: newId)) } ?? false }
    @discardableResult func separatePaths() -> Bool { store.canvas.map { pathCommit(V56PathOps.separate($0.strokes, store.selection, newId: newId)) } ?? false }
    @discardableResult func closeSelectedPaths() -> Bool { store.canvas.map { pathCommit(V56PathOps.closePaths($0.strokes, store.selection), selectNew: false) } ?? false }
    @discardableResult func reverseSelectedPaths() -> Bool { store.canvas.map { pathCommit(V56PathOps.reversePaths($0.strokes, store.selection), selectNew: false) } ?? false }
    @discardableResult func envelopeDistort() -> Bool { store.canvas.map { pathCommit(V56PathOps.envelope($0.strokes, store.selection), selectNew: false) } ?? false }

    /// Seçimin efektleri (ilk efekt uygulanabilir elemanın).
    func selectedEffects() -> [VectorEffect] {
        store.canvas?.strokes.first { store.selection.contains($0.id) && V56PathOps.effectable($0) }?.style2?.effects ?? []
    }

    func canApplyEffects() -> Bool { store.canvas?.strokes.contains { store.selection.contains($0.id) && V56PathOps.effectable($0) } == true }

    private func effected(_ strokes: [Stroke], _ effects: [VectorEffect]) -> [Stroke] {
        strokes.map { s in store.selection.contains(s.id) && V56PathOps.effectable(s) ? V56PathOps.withEffects(s, effects) : s }
    }

    /// Efekt önizlemesi (geçmişe girmez).
    func previewEffects(_ effects: [VectorEffect]) {
        guard let base = (store.inPreview ? store.endPreview() : store.canvas) else { return }
        store.previewStrokes(effected(base.strokes, effects))
    }

    func cancelEffectsPreview() { store.endPreview() }

    /// Efektleri uygular (tek geri alma adımı; liste boşsa efektler kaldırılır).
    func applyEffects(_ effects: [VectorEffect]) {
        store.endPreview()
        guard let c = store.canvas else { return }
        let out = effected(c.strokes, effects)
        if out != c.strokes { store.commit(out, useArea: false) }
    }

    /// Bıçak açık mı (çizilen çizgi kesme çizgisi olur).
    @Published private(set) var knife = false
    private var knifeIds: Set<String> = []

    /// Bıçağı açar: Çiz modunda çizilen her çizgi seçili (yoksa kilitsiz katmanlardaki tüm) şekilleri keser.
    func startKnife() {
        knifeIds = store.selection
        knife = true
        setMode(.DRAW)
        drawing.knife = { [weak self] pts in self?.applyKnife(pts); return true }
    }

    func stopKnife() { knife = false; drawing.knife = nil; knifeIds = [] }

    @discardableResult
    func applyKnife(_ points: [StrokePoint]) -> Bool {
        guard let c = store.canvas else { return false }
        let locked = Set(c.layers.filter { $0.locked || !$0.visible }.map(\.id))
        let ids = knifeIds.filter { id in c.strokes.contains { $0.id == id } }
        guard let out = V56PathOps.knife(c.strokes, ids, points, newId: newId, locked: locked) else { return false }
        let before = Set(c.strokes.map(\.id))
        store.commit(out, useArea: false)
        // Kesilen parçalar sonraki kesimlere de açık kalır
        if !knifeIds.isEmpty { knifeIds = knifeIds.filter { id in out.contains { $0.id == id } }.union(out.filter { !before.contains($0.id) }.map(\.id)) }
        return true
    }

    /// Şekil oluşturucu durumu: bölgeler (tuval koordinatı), birleşecekler (sırayla), silinecekler.
    struct ShapeBuilderUi: Equatable { var regions: [[[P]]]; var merge: [Int] = []; var remove: Set<Int> = [] }

    @Published private(set) var shapeBuilder: ShapeBuilderUi?
    private var builderIds: Set<String> = []
    private var builderRegions: [V56PathOps.Region] = []

    /// Şekil oluşturucuyu açar (seçimde en az iki yol işlemine girebilen eleman).
    @discardableResult
    func startShapeBuilder() -> Bool {
        guard let c = store.canvas else { return false }
        let sel = c.strokes.filter { store.selection.contains($0.id) && V56PathOps.operable($0) }
        if sel.count < 2 { return false }
        builderIds = Set(sel.map(\.id))
        builderRegions = V56PathOps.builderRegions(sel)
        shapeBuilder = ShapeBuilderUi(regions: builderRegions.map { r in r.rings.map { ring in ring.map { P(Float($0.x), Float($0.y)) } } })
        setMode(.SELECT)
        return true
    }

    /// Dokunuş: bölge birleşecekler listesine girer/çıkar; [remove] true: bölge silinecek olarak işaretlenir.
    @discardableResult
    func shapeBuilderTap(_ x: Float, _ y: Float, remove: Bool = false) -> Bool {
        guard var b = shapeBuilder else { return false }
        let i = V56PathOps.regionAt(builderRegions, x, y)
        if i < 0 { return true }
        if remove {
            if b.remove.contains(i) { b.remove.remove(i) } else { b.remove.insert(i) }
            b.merge.removeAll { $0 == i }
        } else {
            if b.merge.contains(i) { b.merge.removeAll { $0 == i } } else { b.merge.append(i) }
            b.remove.remove(i)
        }
        shapeBuilder = b
        return true
    }

    @discardableResult
    func applyShapeBuilder() -> Bool {
        guard let b = shapeBuilder else { return false }
        shapeBuilder = nil
        guard let c = store.canvas else { return false }
        return pathCommit(V56PathOps.build(c.strokes, builderIds, merge: b.merge, remove: b.remove, newId: newId))
    }

    func cancelShapeBuilder() { shapeBuilder = nil }

    /// Düğüm türü ve Bezier tutamakları (şekil düzenleme, Noktalar aracı).
    func selectedHandles() -> [(P, P)] { shapes.selectedHandles() }
    func setNodeSmooth(_ smooth: Bool) { shapes.setNodeSmooth(smooth) }
    var smoothNodes: Bool { shapes.smoothNodes }
    @discardableResult func extendFromSelectedNode() -> Bool { shapes.extendFromSelectedNode() }
    func selectedNodeIsEnd() -> Bool { shapes.selectedNodeIsEnd() }
    func bezierOn() -> Bool { shapes.bezierOn() }

    // MARK: v56 aşama 7: katman çoğalt/birleştir, renk ayarları, hazır ayarlar

    @Published private(set) var merging = false
    @Published private(set) var colorApplying = false
    @Published var colorAdjustOpen = false
    @Published var patternOpen = false

    func layersNeedRaster(_ ids: Set<String>) -> Bool { LayerOps.needsRaster(store.canvas?.layers ?? [Layer.Default], ids) }

    /// Hepsi Normal ve opaklık 1 ise vektör olarak; değilse her sayfada seçili katmanlar tek resme çevrilerek birleştirilir.
    func mergeLayers(_ ids: Set<String>) {
        let current = store.canvas?.layers ?? [Layer.Default]
        guard current.filter({ ids.contains($0.id) }).count >= 2, !merging, let canvas = store.canvas else { return }
        if !layersNeedRaster(ids) { layers.mergeVector(ids); return }
        guard let target = current.last(where: { ids.contains($0.id) }) else { return }
        merging = true
        Task {
            defer { merging = false }
            var imgs: [Stroke?] = []
            for page in canvas.pages {
                let part = page.filter { ids.contains(Layers.of($0, current)) }
                imgs.append(part.isEmpty ? nil : await images.renderToImage(part, layers: current, width: canvas.width, height: canvas.height, layerId: target.id))
            }
            layers.mergeRaster(ids, imgs)
        }
    }

    func mergeLayerDown(_ id: String) {
        guard let below = LayerOps.below(store.canvas?.layers ?? [], id) else { return }
        mergeLayers([id, below.id])
    }

    private var colorBase: [Stroke]?

    private func colorTargets(_ base: [Stroke], _ layerId: String?) -> Set<String> {
        guard let layerId else { return store.selection }
        let current = store.canvas?.layers ?? [Layer.Default]
        if current.first(where: { $0.id == layerId })?.locked == true { return [] }
        return Set(base.filter { Layers.of($0, current) == layerId }.map(\.id))
    }

    /// Renk ayarı önizlemesi (vektör renkleri canlı; resim pikselleri Uygula'da işlenir).
    func previewColorAdjust(_ adj: ColorAdjust, layerId: String?) {
        if colorBase == nil { colorBase = store.canvas?.strokes }
        guard let base = colorBase else { return }
        store.previewStrokes(ColorAdjustOps.mapStrokes(base, colorTargets(base, layerId), adj))
    }

    func cancelColorAdjust() { if colorBase != nil { store.endPreview(); colorBase = nil } }

    /// Renk ayarını uygula: vektör renkleri değişir, resimler piksel olarak işlenir; tek geri alma adımı.
    func applyColorAdjust(_ adj: ColorAdjust, layerId: String?) {
        guard let base = colorBase ?? store.canvas?.strokes else { return }
        store.endPreview(); colorBase = nil
        if adj.isIdentity { return }
        let ids = colorTargets(base, layerId)
        if ids.isEmpty { return }
        let mapped = ColorAdjustOps.mapStrokes(base, ids, adj)
        let imgs = base.filter { ids.contains($0.id) && $0.brushType == .IMAGE && $0.assetId != nil }
        if imgs.isEmpty { if mapped != base { store.commit(mapped, useArea: false) }; return }
        colorApplying = true
        Task {
            defer { colorApplying = false }
            let mapper = ColorAdjustOps.Mapper(adj)
            var newAssets: [String: String] = [:]
            for img in imgs {
                guard let old = img.assetId, newAssets[old] == nil else { continue }
                if let n = await images.transformAsset(old, { px in px.map { mapper.map($0) } }) { newAssets[old] = n }
            }
            let out = mapped.map { s -> Stroke in
                guard ids.contains(s.id), s.brushType == .IMAGE, let a = s.assetId, let n = newAssets[a] else { return s }
                var c = s; c.assetId = n; return c
            }
            if out != base { store.commit(out, useArea: false) }
        }
    }

    /// Ekle › Işık katmanı / Gölge katmanı.
    @discardableResult
    func addPresetLayer(_ kind: V56Presets.Kind) -> String {
        let p = V56Presets.layer(kind)
        let id = UUID().uuidString.lowercased()
        let name = I18n.tr(kind == .LIGHT ? "Işık katmanı" : "Gölge katmanı")
        store.commitLayers((store.canvas?.layers ?? [Layer.Default]) + [Layer(id: id, name: name, opacity: p.opacity, blend: p.blend)])
        store.activeLayer = id
        usePresetBrush(p)
        return id
    }

    func useGlitterBrush() { usePresetBrush(V56Presets.glitter) }

    // MARK: v56 aşama 8 simetri ve Desen yap

    func setSymmetry(_ kind: SymmetryKind?) { guides.setSymmetry(kind) }
    func updateSymmetry(_ spec: SymmetrySpec, persist: Bool = true) { guides.updateSymmetry(spec, persist: persist) }

    private func patternSelection() -> (CanvasUi, [Stroke], [Float])? {
        guard let c = store.canvas else { return nil }
        let sel = c.strokes.filter { store.selection.contains($0.id) && !$0.hidden }
        let rects = sel.compactMap { StrokeRenderer.paintBounds($0) }
        guard let first = rects.first else { return nil }
        let r = rects.dropFirst().reduce(first) { $0.union($1) }
        return (c, sel, [Float(r.minX), Float(r.minY), Float(r.maxX), Float(r.maxY)])
    }

    /// Seçili elemanlar [gap] aralıkla tuval boyunca döşenir (tek geri alma adımı).
    @discardableResult
    func makePattern(_ gap: Float) -> Int {
        guard let p = patternSelection() else { return 0 }
        let (c, sel, b) = p
        let out = V56Symmetry.pattern(sel, b, c.width, c.height, gap) { UUID().uuidString.lowercased() }
        guard let group = out.first?.groupId else { return 0 }
        let strokes = c.strokes.map { s -> Stroke in
            var n = s
            if store.selection.contains(s.id) && s.groupId == nil { n.groupId = group }
            return n
        } + out
        store.commit(strokes, useArea: false)
        return out.count
    }

    func patternCount(_ gap: Float) -> Int {
        guard let p = patternSelection() else { return 0 }
        let (c, b) = (p.0, p.2)
        return V56Symmetry.patternCount(b, c.width, c.height, gap)
    }

    // MARK: v56 aşama 8A (2E görünüm, bağlı kopya, opaklık maskesi, Tekrarla)

    private func newId() -> String { UUID().uuidString.lowercased() }

    func selectedVectorStyle() -> VectorStyle {
        store.canvas?.strokes.first { store.selection.contains($0.id) }?.style2 ?? VectorStyle()
    }

    /// Seçili elemanlara 2E görünüm değişikliği ([coalesce]: kaydırırken tek geri alma adımı).
    func updateVectorStyle(coalesce: String? = nil, _ change: (VectorStyle) -> VectorStyle) {
        guard let c = store.canvas, !store.selection.isEmpty else { return }
        var changed = false
        let out = c.strokes.map { s -> Stroke in
            guard store.selection.contains(s.id) else { return s }
            let n = change(s.style2 ?? VectorStyle())
            let next: VectorStyle? = n.isDefault ? nil : n
            if next == s.style2 { return s }
            changed = true
            var o = s; o.style2 = next; return o
        }
        if changed { store.commit(out, coalesce: coalesce.map { "v2-\($0)" }, useArea: false) }
    }

    @discardableResult
    func cloneSelection() -> Int {
        guard let c = store.canvas else { return 0 }
        let out = V56Vector.clone(c.strokes, store.selection, 24, 24, newId: newId)
        let added = out.dropFirst(c.strokes.count)
        if added.isEmpty { return 0 }
        store.commit(out, useArea: false)
        store.select(Set(added.map(\.id)))
        return added.count
    }

    func selectionHasClone() -> Bool { store.canvas?.strokes.contains { store.selection.contains($0.id) && $0.cloneOf != nil } == true }

    func unlinkClones() {
        guard let c = store.canvas else { return }
        let out = V56Vector.unlink(c.strokes, store.selection)
        if out != c.strokes { store.commit(out, useArea: false) }
    }

    func selectedRepeat() -> RepeatSpec? { store.canvas.flatMap { V56Vector.repeatOf($0.strokes, store.selection) } }

    private func repeatSources(_ strokes: [Stroke]) -> Set<String> {
        var byId: [String: Stroke] = [:]; for s in strokes { byId[s.id] = s }
        return Set(store.selection.compactMap { id in byId[id].map { s in (s.cloneOf?.repeat == true ? s.cloneOf?.source : nil) ?? s.id } })
    }

    func previewRepeat(_ spec: RepeatSpec) {
        guard let base = (store.inPreview ? store.endPreview() : store.canvas) else { return }
        let (out, _) = V56Vector.repeat(base.strokes, repeatSources(base.strokes), spec, newId: newId)
        store.previewStrokes(out)
    }

    func cancelRepeatPreview() { _ = store.endPreview() }

    @discardableResult
    func applyRepeat(_ spec: RepeatSpec) -> Int {
        _ = store.endPreview()
        guard let c = store.canvas else { return 0 }
        let sources = repeatSources(c.strokes)
        let (out, n) = V56Vector.repeat(c.strokes, sources, spec, newId: newId)
        if n == 0 { return 0 }
        store.commit(out, useArea: false)
        store.select(sources)
        return n
    }

    func removeRepeat() {
        _ = store.endPreview()
        guard let c = store.canvas else { return }
        let out = V56Vector.removeRepeat(c.strokes, store.selection)
        if out != c.strokes {
            store.commit(out, useArea: false)
            let ids = Set(out.map(\.id))
            store.select(store.selection.filter { ids.contains($0) })
        }
    }

    @discardableResult
    func makeOpacityMask() -> Bool {
        guard let c = store.canvas else { return false }
        let out = V56Vector.makeOpacityMask(c.strokes, store.selection)
        if out == c.strokes { return false }
        store.commit(out, useArea: false)
        let ids = Set(out.map(\.id))
        store.select(store.selection.filter { ids.contains($0) })
        return true
    }

    func selectionHasOpacityMask() -> Bool {
        store.canvas?.strokes.contains { store.selection.contains($0.id) && !($0.style2?.opacityMask ?? []).isEmpty } == true
    }

    func releaseOpacityMask() {
        guard let c = store.canvas else { return }
        let out = V56Vector.releaseOpacityMask(c.strokes, store.selection)
        if out != c.strokes { store.commit(out, useArea: false) }
    }

    private func usePresetBrush(_ p: V56Presets.LayerPreset) {
        brush.select(p.brush)
        updateBrushColor(p.colorHex)
        brush.updateSize(p.size)
        brush.updateOpacity(p.brushOpacity)
        setMode(.DRAW)
    }

    /// Katman menüsünden: o katman etkin yapılıp bütün sayfası dokuyla doldurulur.
    func textureFillLayer(_ layerId: String, _ textureId: String) {
        layers.setActive(layerId)
        textureFill(textureId, wholePage: true)
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
        if [.RECT_SELECT, .LASSO_SELECT, .MAGIC_WAND, .LASSO_FILL].contains(next) && mode != .SELECT { setMode(.SELECT) }
    }

    @discardableResult
    func eyedrop(_ point: P) -> String? {
        guard let c = store.canvas else { return nil }
        canvasPick = .NONE
        loupe = nil
        loupeSampleAt = nil
        // v56 parmak sayfanın dışında bırakıldıysa iptal (renk değişmez)
        if eyedropLoupeOn() && !EyedropLoupe.inside(point.x, point.y, Float(c.width), Float(c.height)) { return nil }
        guard let hex = ColorSampler.sample(c, point.x, point.y) else { return nil }
        updateBrushColor(hex)
        if mode == .PAINT { shapes.setRegionColor(hex) }
        return hex
    }

    func rectSelect(_ a: P, _ b: P) {
        let r = FRect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
        if r.width < 1 || r.height < 1 { return }
        store.lastArea = V56Selection.rect(r.left, r.top, r.right, r.bottom)
        if selection.selectInArea(add: false, { $0.x >= r.left && $0.x <= r.right && $0.y >= r.top && $0.y <= r.bottom }) > 0 { startAction(.SCALE) }
    }

    func lassoSelect(_ points: [P]) {
        if !AreaSelect.usable(points, minSize: 4) { return }
        store.lastArea = points.map { StrokePoint($0.x, $0.y) }
        _ = selection.selectInArea(add: false) { AreaSelect.inside(points, $0) }
    }

    // MARK: v56 seçim işlemleri ve seçim alanı

    /// v56 özellik bu tuvalin modunda açık mı ([V56Flags]).
    func v56(_ feature: V56Flags.Feature) -> Bool { V56Flags.enabled(feature, store.canvas?.kind ?? .ART) }

    func selectAt(_ x: Float, _ y: Float, tolerance: Float) -> Bool {
        // v56 aşama 9C şekil oluşturucu açıkken dokunuş bölge seçer
        if shapeBuilder != nil { return shapeBuilderTap(x, y) }
        // v56 2E grup içindeyken dokunuş yalnız o elemanı seçer (gruptan dışarı dokununca çıkılır)
        if v56(.ADV_SELECTION), let r = selection.selectInGroupAt(x, y, tolerance: tolerance) { return r }
        return selection.selectAt(x, y, tolerance: tolerance)
    }

    func deselectAt(_ x: Float, _ y: Float, tolerance: Float) -> Bool {
        // v56 aşama 9C şekil oluşturucu: çift dokunuş bölgeyi silinecek yapar
        if shapeBuilder != nil { return shapeBuilderTap(x, y, remove: true) }
        // v56 2E gruplu elemana çift dokunuş grubun içine girer
        if v56(.ADV_SELECTION) && selection.enterGroupAt(x, y, tolerance: tolerance) { return true }
        return selection.deselectAt(x, y, tolerance: tolerance)
    }

    func magicWand(_ point: P, tolerance: Float) {
        selection.magicWand(point.x, point.y, tolerance: tolerance, criterion: wandCriterion, similarity: wandTolerance)
    }

    func setWandTolerance(_ t: Float) { wandTolerance = min(max(t, 0), 1) }

    func paintInsideSelection() { if selection.areaFromSelection() { setMode(.DRAW) } }

    // MARK: v56 2C-3 pano (çocuk modunda yok)

    func canUseClipboard() -> Bool { v56(.CROSS_PAGE_PASTE) }

    /// Görünür alan, tuval biriminde [sol, üst, sağ, alt].
    func visibleCanvasRect() -> [Float] {
        let sc = max(viewport.scale, 0.01), o = viewport.offset, v = viewport.viewSize
        return [-o.x / sc, -o.y / sc, (v.w - o.x) / sc, (v.h - o.y) / sc]
    }

    func paste(anchor: P? = nil) {
        clipboard.paste(anchor: anchor, visible: anchor == nil ? visibleCanvasRect() : nil)
        // v56: yapıştırılan seçim Dönüştür panelinde açılır
        if !store.selection.isEmpty { openTransform() }
    }

    func lassoFill(_ points: [P]? = nil) {
        selection.lassoFill(points?.map { StrokePoint($0.x, $0.y) }, colorHex: brush.settings.colorHex, opacity: brush.settings.opacity)
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
        let base: [SaveTab]
        switch store.canvas?.kind {
        case .COMIC: base = [.PDF, .CBZ, .IMAGE]
        case .NOTES, .SIMPLE_NOTES: base = [.PDF, .IMAGE]
        default: base = [.IMAGE]
        }
        // v56 aşama 9 SVG, PSD ve DXF (çocuk tuvalinde yok)
        let docs = v56(.EXPORT_SVG_PSD_DXF) && store.canvas?.kind != .KIDS
        return base + (docs ? [.SVG, .PSD, .DXF] : [])
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
        // v51 geçici resim tuvalinde Kaydet resmi nota aktarır
        if tempSession != nil { commitTempImage(); return }
        texts.commitOpenDraft()
        store.saveNow()
        emit(.saved)
    }

    func saveBeforeLeaving() {
        if tempSession != nil { return }
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

    /// v56 aşama 12 3B Çizim'den "Tuvale ekle": resim sayfanın ortasına (ya da "buraya ekle" noktasına).
    func insert3DImage(_ png: Data) {
        guard let center = centerOrAnchor() else { return }
        images.insertPngData(png, heightFraction: 0.5, center: center)
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

    // v56 aşama 10: kütüphane + GLB, açı seçimi, Açıyı değiştir (tuvalden 3B düzenleyiciye yol yok)
    func catalogModel3d(_ catalogId: String) -> Model3D? { models3d.catalogModel(catalogId) }
    func glbModel3d(_ bytes: [UInt8], name: String, maxTriangles: Int) async -> Model3D? {
        await models3d.glbModel(bytes, name: name, maxTriangles: maxTriangles)
    }
    func previewModel3d(_ model: Model3D, yaw: Float, pitch: Float, size: Int) async -> CGImage? {
        await models3d.preview(model, yaw: yaw, pitch: pitch, size: size)
    }
    func insertModel3d(_ model: Model3D, yaw: Float, pitch: Float) { models3d.insert(model, yaw: yaw, pitch: pitch, center: takeAddAnchor()) }
    func selectedModel3dData() -> Model3D? { models3d.selectedModel() }
    func selectedIs3d() -> Bool { models3d.selected() != nil }
    func changeModel3dAngle(yaw: Float, pitch: Float) { models3d.changeAngle(yaw: yaw, pitch: pitch) }
}
