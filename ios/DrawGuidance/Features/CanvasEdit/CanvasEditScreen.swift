import SwiftUI

/// v49 düzenleyici ekranı (Android `CanvasEditScreen`): üstte mod ve araç satırı, ortada çizim alanı.
/// Menüler, paneller ve pencereler bu iskelete ekleniyor.
struct CanvasEditScreen: View {
    @StateObject private var vm: EditorViewModel
    @StateObject private var ui = CanvasUiEvents()
    @StateObject private var challengeUi = KidsChallengeUi()
    @State private var actions: CanvasActions?
    @State private var brushPickerOpen = false
    @State private var sheet: EditorSheet?
    @StateObject private var addUi = AddWindows()
    @Environment(\.dismiss) private var dismiss

    /// v51 başka bir tuvale geç (yeni alıştırma yeni tuvalde; bkz. [CanvasEditHost]).
    var onSwitchCanvas: (String) -> Void = { _ in }

    init(canvasLocalId: String, onSwitchCanvas: @escaping (String) -> Void = { _ in }) {
        _vm = StateObject(wrappedValue: EditorViewModel(canvasLocalId: canvasLocalId, deps: .shared))
        self.onSwitchCanvas = onSwitchCanvas
    }

    var body: some View {
        VStack(spacing: 0) {
            // v53 başlıkta yalnız tuval adı, Eğitim, Kaydet ve Diğer; öteki düğmeler alttaki menülerde
            EditorHeaderBar(vm: vm, store: vm.store, sheet: $sheet)
            if vm.loaded {
                if vm.store.canvas?.kind == .KIDS {
                    KidsToolbar(vm: vm, store: vm.store, brush: vm.brush, shapes: vm.shapes, challengeUi: challengeUi)
                } else if vm.store.canvas?.kind.simple == true {
                    SimpleToolbar(vm: vm, store: vm.store, brush: vm.brush, ruler: vm.ruler, sheet: $sheet, brushPickerOpen: $brushPickerOpen, addUi: addUi)
                } else {
                    AdvancedMenus(vm: vm, store: vm.store, brush: vm.brush, ruler: vm.ruler, shapes: vm.shapes, sheet: $sheet,
                                  brushPickerOpen: $brushPickerOpen, addUi: addUi)
                }
            }
            ZStack {
                if vm.loaded, vm.store.canvas != nil, let actions {
                    EditorCanvasHost(vm: vm, store: vm.store, pen: vm.pen, shapes: vm.shapes, diagrams: vm.diagrams,
                                     frame: vm.frame, hw: vm.handwriting, actions: actions)
                    if vm.store.canvas?.kind != .KIDS && vm.mode == .SELECT && vm.texts.draft == nil {
                        SelectionMenu(vm: vm, store: vm.store, viewport: vm.viewport)
                    }
                    if let b = vm.placingLocation {
                        LocationPlaceOverlay(background: b, panels: vm.comicPanelRects(), viewport: vm.viewport,
                                             onPlace: { vm.placeLocation($0) }, onCancel: { vm.placingLocation = nil })
                    }
                    if let c = vm.store.canvas, c.kind == .ART || c.kind == .SIMPLE_ART {
                        // v52 çizim yardımcıları: kompozisyon çizgileri ve köşede kontrol önizlemesi
                        CompositionOverlay(helpers: vm.helpers, canvas: c, viewport: vm.viewport)
                        CheckPreview(helpers: vm.helpers, canvas: c)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    }
                    if let c = vm.store.canvas, c.kind == .ART || c.kind == .SIMPLE_ART || c.kind == .COMIC {
                        // v53 canlı, nesne ve eşya yapı kılavuzu (yalnız görünüm)
                        StructureOverlay(state: vm.structure, viewport: vm.viewport)
                    }
                    if vm.store.canvas?.kind.isNotes == true {
                        // v53 hızlı yazı kutusu
                        QuickTextOverlay(vm: vm, qt: vm.quickText, viewport: vm.viewport, texts: vm.texts)
                    }
                    if PracticeLevels.available(vm.store.canvas?.kind) {
                        PracticeLayer(ctl: vm.practice, viewport: vm.viewport, tutorial: vm.inTutorial())
                    }
                    if vm.store.canvas?.kind == .KIDS {
                        KidsChallengeLayer(vm: vm, ctl: vm.challenge, ui: challengeUi, viewport: vm.viewport, hw: vm.handwriting,
                                           tutorial: vm.inTutorial())
                    }
                } else {
                    ProgressView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .tutorialAnchor(TutorialAnchor.CANVAS)
            .overlay { if vm.loaded { TutorialLiveLayer(ctl: vm.tutorial, viewport: vm.viewport) } }
            .overlay(alignment: .bottom) { if vm.loaded { TextDraftBar(vm: vm, texts: vm.texts) } }
            // v53 balon yazısı doğrudan balonun üstünde
            .overlay { if vm.loaded { BalloonInlineEditor(vm: vm, texts: vm.texts, store: vm.store, viewport: vm.viewport) } }
            .overlay(alignment: .top) { if vm.loaded { ParamControlsHost(vm: vm, shapes: vm.shapes) } }
            .overlay(alignment: .bottom) { if vm.loaded { Draw3dHost(vm: vm, store: vm.store) } }
            .overlay(alignment: .bottom) { if vm.loaded { SuggestionLayer(vm: vm, hw: vm.handwriting, texts: vm.texts, ctl: vm.challenge) } }
            // v51 canlı yayın: bu tuval yayındaysa değişiklikler karşı cihaza gider
            .overlay(alignment: .bottom) { if vm.loaded { LiveBroadcastBridge(store: vm.store, canvasLocalId: vm.canvasLocalId) } }
            // v53 Elemanlar, Katmanlar ve Özellikler sağdan açılan panelde
            .overlay { if vm.loaded && vm.store.canvas?.kind != .KIDS { EditorSidePanelHost(vm: vm, store: vm.store, brushPickerOpen: $brushPickerOpen) } }
            // v55 çizgi roman ve not almada sayfa şeridi (sayfalar arası geçiş, ekleme, çoğaltma, silme)
            if vm.loaded { PageStripView(vm: vm, store: vm.store) }
        }
        .background(Color(uiColor: CanvasColors.background))
        .overlay { EditorToastHost(events: vm.events) }
        .overlayPreferenceValue(TutorialAnchorKey.self) { anchors in
            GeometryReader { geo in
                TutorialOverlay(ctl: vm.tutorial, viewport: vm.viewport, rects: anchors.mapValues { geo[$0] },
                                mascot: vm.store.canvas?.kind == .KIDS)
            }
        }
        .onChangeCompat(of: vm.loaded) { _, loaded in
            guard loaded, let c = vm.store.canvas else { return }
            // v51 önceki tuvalden yeni alıştırmayla gelindiyse alıştırma hemen açılır (eğitim önerisi yerine)
            if let i = PracticeHandoff.take(c.localId) {
                if i == PracticeHandoff.LIST { vm.practice.listOpen = true } else { vm.practice.open(i) }
            } else { vm.tutorial.maybeOffer(c.kind) }
        }
        .onChangeCompat(of: vm.switchToCanvas) { _, id in if let id { onSwitchCanvas(id) } }
        .modifier(TutorialOfferPresenter(vm: vm, ctl: vm.tutorial))
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { if actions == nil { actions = vm.makeCanvasActions(ui) } }
        #if DEBUG
        // CI tanısı: `-dgPanel BRUSH` ile sağ panel açılışta açılır (ekran görüntüsü için)
        .task {
            let a = ProcessInfo.processInfo.arguments
            guard let i = a.firstIndex(of: "-dgPanel"), i + 1 < a.count, let t = PanelTab(rawValue: a[i + 1]) else { return }
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            if vm.panel != t { vm.togglePanel(t) }
        }
        #endif
        .onDisappear {
            let temp = vm.isTempImageEdit
            vm.close()
            if !temp { AdsCoordinator.shared.onCanvasClosed(vm.store.canvas?.kind) }
        }
        // v51 notta resim düzenleme: geçici basit resim tuvali tam ekran açılır; kapanınca resim nota aktarılmış olur
        .fullScreenCover(item: $vm.tempEditRequest, onDismiss: {
            // v52 nota dönüldü: ara verilen yayın sürer, son hali hemen gider
            ShareManager.shared.resumeLive(vm.canvasLocalId)
            if let c = vm.store.canvas { ShareManager.shared.publishLive(c) }
        }) { doc in
            NavigationStack { CanvasEditScreen(canvasLocalId: doc.id) }
        }
        .modifier(TempImageEditCloser(vm: vm))
        .modifier(TableSheetsHost(ctl: vm.tables, store: vm.store, viewport: vm.viewport))
        .modifier(PhotoEditorHost(vm: vm, photo: vm.photo))
        .sheet(isPresented: $brushPickerOpen) { brushPicker }
        .modifier(AddWindowsHost(vm: vm, addUi: addUi))
        .modifier(RulerSheetsHost(vm: vm, ruler: vm.ruler))
        .sheet(isPresented: Binding(get: { vm.structure.sheet }, set: { vm.structure.sheet = $0 })) {
            StructureGuideSheet(state: vm.structure, canvas: vm.store.canvas)
        }
        .sheet(item: $sheet) { s in
            switch s {
            case .save: SaveSheet(vm: vm, images: vm.images, v40: vm.v40)
            case .guide: GuideSheet(vm: vm, store: vm.store)
            case .background: BackgroundSheet(vm: vm, store: vm.store)
            // v52 paylaşım ve yayın çizim sayfasının Diğer menüsünden
            case .share: NavigationStack { ShareView(mode: .send).toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("close")) { sheet = nil } } } }
            case .live: NavigationStack { ShareView(mode: .live).toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("close")) { sheet = nil } } } }
            case .helpers: DrawHelpersSheet(helpers: vm.helpers)
            }
        }
        .sheet(isPresented: Binding(get: { vm.loaded && vm.diagrams.isPickerOpen }, set: { if !$0 { vm.closeDiagramPicker() } })) {
            DiagramPickerSheet(vm: vm)
        }
        .sheet(isPresented: Binding(get: { vm.loaded && vm.formulas.isPickerOpen }, set: { if !$0 { vm.closeFormulaPicker() } })) {
            FormulaPickerSheet(vm: vm)
        }
        .sheet(isPresented: Binding(get: { vm.loaded && vm.shapes.isPickerOpen }, set: { if !$0 { vm.closeShapePicker() } })) {
            let notes = vm.store.canvas?.kind.isNotes == true
            ShapePickerView(kind: vm.store.canvas?.kind ?? .ART, graphs: notes ? vm.shapes.graphsOnly : nil,
                            title: notes && vm.shapes.graphsOnly ? L("v52_add_graph") : nil) { id in
                vm.selectShapeId(id)
                vm.closeShapePicker()
                if vm.mode != .SHAPE { vm.startAction(.SHAPE, target: []) }
            }
        }
    }

    private var brushPicker: some View {
        let b = vm.brush!
        return BrushPickerView(
            kind: vm.store.canvas?.kind ?? .ART,
            brush: Binding(get: { b.settings.brushType }, set: { b.select($0) }),
            size: Binding(get: { Double(b.settings.sizePx) }, set: { b.updateSize(Float($0)) }),
            flow: Binding(get: { Double(b.settings.flow) }, set: { b.updateFlow(Float($0)) }),
            opacity: Binding(get: { Double(b.settings.opacity) }, set: { b.updateOpacity(Float($0)) }),
            color: ArgbColor.hex(b.settings.colorHex),
            controller: b)
    }
}

/// Çizim alanı: tuval ve tuval üstü yardımcılar (kalem yolu, vektör seçimi, diyagram odağı, kırpma).
private struct EditorCanvasHost: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var pen: PenController
    @ObservedObject var shapes: ShapeController
    @ObservedObject var diagrams: DiagramController
    @ObservedObject var frame: FrameController
    @ObservedObject var hw: HandwritingController
    let actions: CanvasActions

    var body: some View {
        EditorCanvas(vm: vm, store: store, viewport: vm.viewport, actions: actions, overlay: overlay)
            .ignoresSafeArea(edges: .bottom)
    }

    private var overlay: CanvasOverlay {
        var o = CanvasOverlay()
        o.penAnchors = pen.anchors
        o.penPending = pen.pending
        o.vector = vm.mode == .SHAPE_EDIT && shapes.tool != .GRAPH ? shapes.vector : nil
        o.formulaSlots = vm.formulas.editing != nil
        o.formulaSlot = vm.formulas.activeSlot
        o.diagramFocus = diagrams.focus
        o.diagramLink = diagrams.link
        o.cropRect = frame.cropRect
        o.cropArea = frame.cropArea
        // Önerinin düzeltmesi tuvalde kesik çizgiyle (önce şekil, yoksa çizgi önerisi)
        let shapePreview: [[StrokePoint]] = hw.shape?.preview ?? []
        let preview: [[StrokePoint]] = shapePreview.isEmpty ? (hw.line?.preview ?? []) : shapePreview
        o.correction = preview.isEmpty ? nil : preview.map { (part: [StrokePoint]) -> [P] in part.map { P($0.x, $0.y) } }
        return o
    }
}

/// Parametreli şekil düzenlenirken (Değerler aracı) tuvalin üstündeki değer/formül çubuğu.
private struct ParamControlsHost: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var shapes: ShapeController

    var body: some View {
        if vm.mode == .SHAPE_EDIT && shapes.tool == .PARAMS {
            ParamControlsBar(vm: vm, store: vm.store, shapes: shapes, params: shapes.paramShapes).padding(.top, 6)
        }
        if vm.mode == .SHAPE_EDIT && shapes.tool == .GRAPH {
            GraphControlsBar(store: vm.store, shapes: shapes).padding(.top, 6)
        }
        GraphValueHost(shapes: shapes)
    }
}

/// Öneriler: büyüklerde balonlar, çocuk tuvalinde maskot (yazı taslağı açıkken gizlenir).
private struct SuggestionLayer: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var hw: HandwritingController
    @ObservedObject var texts: TextController
    @ObservedObject var ctl: KidsChallengeController

    var body: some View {
        if texts.draft == nil {
            if vm.store.canvas?.kind == .KIDS {
                KidsMascotLayer(vm: vm, hw: hw, tutorial: vm.inTutorial(), challengeOn: ctl.active || ctl.suggest)
            } else {
                SuggestionsHost(vm: vm, hw: hw)
            }
        }
    }
}

/// Eğitim seçimi penceresi. Ekran yalnız `vm`'i izler; `tutorial.offer` değişince yenilensin diye denetleyici burada
/// ayrıca izlenir (yoksa "Eğitim modu" düğmesi pencereyi açmıyordu).
private struct TutorialOfferPresenter: ViewModifier {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var ctl: TutorialController

    func body(content: Content) -> some View {
        content.sheet(isPresented: Binding(get: { ctl.offer }, set: { if !$0 { ctl.closeOffer() } })) {
            TutorialOfferHost(vm: vm, ctl: ctl)
        }
    }
}

/// Eğitim grubu seçimi (tamamlananlar işaretli).
private struct TutorialOfferHost: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var ctl: TutorialController

    var body: some View {
        let kind = vm.store.canvas?.kind ?? .ART
        TutorialOfferSheet(kind: kind, groups: ctl.groups(kind), completed: ctl.completed, onStart: { ctl.start($0) }, onDismiss: { ctl.closeOffer() })
    }
}

enum EditorSheet: String, Identifiable {
    case save, guide, background, share, live, helpers
    var id: String { rawValue }
}

/// v51 geçici resim tuvali: kaydedilince (resim nota aktarılır) ya da bırakılınca ekran kapanır; açılışta ne olduğu söylenir.
private struct TempImageEditCloser: ViewModifier {
    @ObservedObject var vm: EditorViewModel
    @Environment(\.dismiss) private var dismiss

    func body(content: Content) -> some View {
        if vm.isTempImageEdit {
            content
                .onChangeCompat(of: vm.tempClosed) { _, closed in if closed { dismiss() } }
                .onAppear { vm.emit(.message(L(vm.isTempShapeEdit ? "v52_temp_shape_hint" : "v51_temp_image_hint"))) }
        } else {
            content
        }
    }
}

/// v51 düzenleyicinin kabı: alıştırma yeni tuvalde açılınca aynı gezinme yerinde yeni tuvalin düzenleyicisine geçer
/// (eski ekran kapanırken kaydeder).
struct CanvasEditHost: View {
    @State var canvasLocalId: String

    var body: some View {
        CanvasEditScreen(canvasLocalId: canvasLocalId) { canvasLocalId = $0 }
            .id(canvasLocalId)
    }
}

/// v52 resim düzenlemenin yeri (not alma, çizgi roman) ve tam ekran foto düzenleme.
struct PhotoEditorHost: ViewModifier {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var photo: PhotoEditController

    func body(content: Content) -> some View {
        content
            .sheet(isPresented: Binding(get: { vm.imageEditChoice != nil }, set: { if !$0 { vm.imageEditChoice = nil } })) {
                ImageEditChoiceView(comic: vm.store.canvas?.kind == .COMIC) { vm.chooseImageEdit($0) }
            }
            .fullScreenCover(isPresented: Binding(get: { photo.openId != nil }, set: { if !$0 { photo.close() } })) {
                PhotoEditorView(photo: photo, kind: vm.store.canvas?.kind)
            }
    }
}
