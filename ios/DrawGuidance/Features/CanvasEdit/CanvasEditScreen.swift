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
    @Environment(\.dismiss) private var dismiss

    init(canvasLocalId: String) {
        _vm = StateObject(wrappedValue: EditorViewModel(canvasLocalId: canvasLocalId, deps: .shared))
    }

    var body: some View {
        VStack(spacing: 0) {
            EditorTopBar(vm: vm, store: vm.store, brush: vm.brush, brushPickerOpen: $brushPickerOpen, sheet: $sheet)
            if vm.loaded {
                if vm.store.canvas?.kind == .KIDS {
                    KidsToolbar(vm: vm, store: vm.store, brush: vm.brush, shapes: vm.shapes, challengeUi: challengeUi)
                } else if vm.store.canvas?.kind.simple == true {
                    SimpleToolbar(vm: vm, store: vm.store, brush: vm.brush, ruler: vm.ruler, sheet: $sheet, brushPickerOpen: $brushPickerOpen)
                } else {
                    ModeToolRow(vm: vm, store: vm.store, ruler: vm.ruler)
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
            .overlay(alignment: .top) { if vm.loaded { ParamControlsHost(vm: vm, shapes: vm.shapes) } }
            .overlay(alignment: .bottom) { if vm.loaded { Draw3dHost(vm: vm, store: vm.store) } }
            .overlay(alignment: .bottom) { if vm.loaded { SuggestionLayer(vm: vm, hw: vm.handwriting, texts: vm.texts, ctl: vm.challenge) } }
        }
        .background(Color(uiColor: CanvasColors.background))
        .overlay { EditorToastHost(events: vm.events) }
        .overlayPreferenceValue(TutorialAnchorKey.self) { anchors in
            GeometryReader { geo in
                TutorialOverlay(ctl: vm.tutorial, viewport: vm.viewport, rects: anchors.mapValues { geo[$0] },
                                mascot: vm.store.canvas?.kind == .KIDS)
            }
        }
        .onChange(of: vm.loaded) { _, loaded in
            if loaded, let kind = vm.store.canvas?.kind { vm.tutorial.maybeOffer(kind) }
        }
        .sheet(isPresented: Binding(get: { vm.tutorial.offer }, set: { if !$0 { vm.tutorial.closeOffer() } })) {
            TutorialOfferHost(vm: vm, ctl: vm.tutorial)
        }
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { if actions == nil { actions = vm.makeCanvasActions(ui) } }
        .onDisappear { vm.close(); AdsCoordinator.shared.onCanvasClosed(vm.store.canvas?.kind) }
        .sheet(isPresented: $brushPickerOpen) { brushPicker }
        .sheet(item: $sheet) { s in
            switch s {
            case .elements: ElementsPanel(vm: vm, store: vm.store)
            case .layers: LayersPanel(vm: vm, store: vm.store)
            case .save: SaveSheet(vm: vm, images: vm.images, v40: vm.v40)
            case .guide: GuideSheet(vm: vm, store: vm.store)
            case .background: BackgroundSheet(vm: vm, store: vm.store)
            }
        }
        .sheet(isPresented: Binding(get: { vm.loaded && vm.diagrams.isPickerOpen }, set: { if !$0 { vm.closeDiagramPicker() } })) {
            DiagramPickerSheet(vm: vm)
        }
        .sheet(isPresented: Binding(get: { vm.loaded && vm.formulas.isPickerOpen }, set: { if !$0 { vm.closeFormulaPicker() } })) {
            FormulaPickerSheet(vm: vm)
        }
        .sheet(isPresented: Binding(get: { vm.loaded && vm.shapes.isPickerOpen }, set: { if !$0 { vm.closeShapePicker() } })) {
            ShapePickerView(kind: vm.store.canvas?.kind ?? .ART) { id in
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
            color: ArgbColor.hex(b.settings.colorHex))
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

/// Üst satır: geri, geri al / yinele, modlar, fırça, renk ve silgi.
private struct EditorTopBar: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var brush: BrushController
    @Binding var brushPickerOpen: Bool
    @Binding var sheet: EditorSheet?
    @Environment(\.dismiss) private var dismiss

    private let modes: [(EditMode, String, String)] = [
        (.DRAW, "pencil.tip", "mode_draw"), (.SELECT, "hand.tap", "mode_select"), (.MOVE, "arrow.up.and.down.and.arrow.left.and.right", "mode_move"),
        (.ROTATE, "rotate.right", "mode_rotate"), (.SCALE, "arrow.up.left.and.arrow.down.right", "mode_scale"),
        (.SHAPE, "square.on.circle", "mode_shape"), (.TEXT, "textformat", "mode_text"), (.PEN, "pencil.and.outline", "mode_pen"),
        (.PAINT, "paintbrush.fill", "mode_paint"), (.EXPAND, "aspectratio", "mode_expand"), (.CROP, "crop", "mode_crop"),
    ]

    var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 14) {
                Button { if vm.tutorial.active { vm.tutorial.cancel() }; vm.close(); dismiss() } label: { Image(systemName: "chevron.backward") }
                    .accessibilityIdentifier("editor_back")
                Text(store.canvas?.name ?? "").font(.headline).lineLimit(1)
                Spacer()
                if !vm.tutorial.groups(store.canvas?.kind ?? .ART).isEmpty {
                    Button { vm.tutorial.openOffer() } label: { Image(systemName: "graduationcap") }
                        .accessibilityLabel(L("tut_menu")).accessibilityIdentifier("tutorial_menu").tutorialAnchor(TutorialAnchor.MORE)
                }
                Button { sheet = .elements } label: { Image(systemName: "list.bullet.rectangle") }.accessibilityIdentifier("elements_button").tutorialAnchor(TutorialAnchor.ELEMENTS)
                Button { sheet = .layers } label: { Image(systemName: "square.3.layers.3d") }.accessibilityIdentifier("layers_button").tutorialAnchor(TutorialAnchor.LAYERS)
                Button { sheet = .save } label: { Image(systemName: "square.and.arrow.down") }.accessibilityIdentifier("save_button").tutorialAnchor(TutorialAnchor.SAVE)
                Button { vm.undo() } label: { Image(systemName: "arrow.uturn.backward") }
                    .disabled(!store.canUndo).accessibilityLabel(L("undo")).accessibilityIdentifier("undo").tutorialAnchor(TutorialAnchor.UNDO)
                Button { vm.redo() } label: { Image(systemName: "arrow.uturn.forward") }
                    .disabled(!store.canRedo).accessibilityLabel(L("redo")).accessibilityIdentifier("redo").tutorialAnchor(TutorialAnchor.REDO)
            }
            .font(.title3)
            if store.canvas?.kind.simple != true {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(modes, id: \.2) { m in
                        Button { vm.chooseMode(m.0) } label: {
                            Label(L(m.2), systemImage: m.1).labelStyle(.iconOnly).frame(width: 36, height: 36)
                                .background(RoundedRectangle(cornerRadius: 10).fill(vm.mode == m.0 ? Color.accentColor.opacity(0.2) : .clear))
                        }
                        .accessibilityIdentifier("mode_\(m.0)")
                        .tutorialAnchor(TutorialAnchor.mode(m.0))
                    }
                    Divider().frame(height: 28)
                    ToolButton(icon: "grid", label: L("add_guide"), id: "guide_button") { sheet = .guide }
                    ToolButton(icon: "photo.on.rectangle", label: L("add_background"), id: "background_button", anchor: TutorialAnchor.BACKGROUND) { sheet = .background }
                    Button { brushPickerOpen = true } label: {
                        Image(systemName: "paintbrush.pointed").frame(width: 36, height: 36)
                    }
                    .accessibilityIdentifier("brush_button")
                    .tutorialAnchor(TutorialAnchor.BRUSH)
                    Button { brush.toggleEraser() } label: {
                        Image(systemName: "eraser").frame(width: 36, height: 36)
                            .background(RoundedRectangle(cornerRadius: 10).fill(brush.settings.brushType == .ERASER ? Color.accentColor.opacity(0.2) : .clear))
                    }
                    .accessibilityIdentifier("eraser_button")
                    .tutorialAnchor(TutorialAnchor.ERASER)
                    ColorPicker(L("brush_color_label"), selection: Binding(
                        get: { Color(hex: brush.settings.colorHex) ?? .black },
                        set: { brush.updateColor($0.hexRGB) }), supportsOpacity: false)
                        .labelsHidden()
                        .accessibilityIdentifier("color_button")
                        .tutorialAnchor(TutorialAnchor.BRUSH_SETTINGS)
                }
                .padding(.horizontal, 12)
            }
            }
        }
        .padding(.top, 8)
        .padding(.horizontal, 12)
        .padding(.bottom, 6)
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
    case elements, layers, save, guide, background
    var id: String { rawValue }
}
