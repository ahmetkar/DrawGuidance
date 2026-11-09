import SwiftUI

/// v49 düzenleyici ekranı (Android `CanvasEditScreen`): üstte mod ve araç satırı, ortada çizim alanı.
/// Menüler, paneller ve pencereler bu iskelete ekleniyor.
struct CanvasEditScreen: View {
    @StateObject private var vm: EditorViewModel
    @StateObject private var ui = CanvasUiEvents()
    @State private var actions: CanvasActions?
    @State private var brushPickerOpen = false
    @Environment(\.dismiss) private var dismiss

    init(canvasLocalId: String) {
        _vm = StateObject(wrappedValue: EditorViewModel(canvasLocalId: canvasLocalId, deps: .shared))
    }

    var body: some View {
        VStack(spacing: 0) {
            EditorTopBar(vm: vm, store: vm.store, brush: vm.brush, brushPickerOpen: $brushPickerOpen)
            if vm.loaded { ModeToolRow(vm: vm, store: vm.store, ruler: vm.ruler) }
            ZStack {
                if vm.loaded, vm.store.canvas != nil, let actions {
                    EditorCanvasHost(vm: vm, store: vm.store, pen: vm.pen, shapes: vm.shapes, diagrams: vm.diagrams,
                                     frame: vm.frame, actions: actions)
                } else {
                    ProgressView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .overlay(alignment: .bottom) { if vm.loaded { TextDraftBar(vm: vm, texts: vm.texts) } }
        }
        .background(Color(uiColor: CanvasColors.background))
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { if actions == nil { actions = vm.makeCanvasActions(ui) } }
        .onDisappear { vm.close() }
        .sheet(isPresented: $brushPickerOpen) { brushPicker }
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
        return o
    }
}

/// Üst satır: geri, geri al / yinele, modlar, fırça, renk ve silgi.
private struct EditorTopBar: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var brush: BrushController
    @Binding var brushPickerOpen: Bool
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
                Button { vm.close(); dismiss() } label: { Image(systemName: "chevron.backward") }
                    .accessibilityIdentifier("editor_back")
                Text(store.canvas?.name ?? "").font(.headline).lineLimit(1)
                Spacer()
                Button { vm.undo() } label: { Image(systemName: "arrow.uturn.backward") }
                    .disabled(!store.canUndo).accessibilityLabel(L("undo")).accessibilityIdentifier("undo")
                Button { vm.redo() } label: { Image(systemName: "arrow.uturn.forward") }
                    .disabled(!store.canRedo).accessibilityLabel(L("redo")).accessibilityIdentifier("redo")
            }
            .font(.title3)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(modes, id: \.2) { m in
                        Button { vm.chooseMode(m.0) } label: {
                            Label(L(m.2), systemImage: m.1).labelStyle(.iconOnly).frame(width: 36, height: 36)
                                .background(RoundedRectangle(cornerRadius: 10).fill(vm.mode == m.0 ? Color.accentColor.opacity(0.2) : .clear))
                        }
                        .accessibilityIdentifier("mode_\(m.0)")
                    }
                    Divider().frame(height: 28)
                    Button { brushPickerOpen = true } label: {
                        Image(systemName: "paintbrush.pointed").frame(width: 36, height: 36)
                    }
                    .accessibilityIdentifier("brush_button")
                    Button { brush.toggleEraser() } label: {
                        Image(systemName: "eraser").frame(width: 36, height: 36)
                            .background(RoundedRectangle(cornerRadius: 10).fill(brush.settings.brushType == .ERASER ? Color.accentColor.opacity(0.2) : .clear))
                    }
                    .accessibilityIdentifier("eraser_button")
                    ColorPicker(L("brush_color_label"), selection: Binding(
                        get: { Color(hex: brush.settings.colorHex) ?? .black },
                        set: { brush.updateColor($0.hexRGB) }), supportsOpacity: false)
                        .labelsHidden()
                        .accessibilityIdentifier("color_button")
                }
                .padding(.horizontal, 12)
            }
        }
        .padding(.top, 8)
        .padding(.horizontal, 12)
        .padding(.bottom, 6)
    }
}
