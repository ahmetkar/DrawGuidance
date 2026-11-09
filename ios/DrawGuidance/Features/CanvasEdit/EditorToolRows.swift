import PhotosUI
import SwiftUI

/// Bir araç düğmesi (Android `ToolItem`).
struct ToolButton: View {
    let icon: String
    let label: String
    var selected = false
    var enabled = true
    var id: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon).frame(width: 36, height: 36)
                .background(RoundedRectangle(cornerRadius: 10).fill(selected ? Color.accentColor.opacity(0.2) : .clear))
        }
        .disabled(!enabled)
        .accessibilityLabel(label)
        .accessibilityIdentifier(id ?? label)
    }
}

/// v18 moda göre değişen ikinci araç satırı: Çiz'de cetvel/damlalık, Seç'te kopyala/sil/grup/sıra, Kırp'ta uygula, Ekle'de eklenecekler.
struct ModeToolRow: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var ruler: RulerController
    @State private var photo: PhotosPickerItem?
    @State private var comicOpen = false

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) { items }.padding(.horizontal, 12)
        }
        .frame(height: 40)
        .sheet(isPresented: $comicOpen) { ComicInsertSheet(vm: vm) }
        .onChange(of: photo) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self) {
                    let c = vm.viewport.toCanvas(P(vm.viewport.viewSize.w / 2, vm.viewport.viewSize.h / 2))
                    vm.images.importImage(data, center: c)
                }
                photo = nil
            }
        }
    }

    private var hasSelection: Bool { !store.selectedIds.isEmpty }

    @ViewBuilder private var items: some View {
        switch vm.mode {
        case .DRAW, .PEN:
            ToolButton(icon: "ruler", label: L("v40_ruler"), selected: ruler.session == .RULER, id: "ruler_button") {
                if ruler.session == .RULER { ruler.finish() } else { openRuler() }
            }
            ToolButton(icon: "eyedropper", label: "eyedropper", selected: vm.canvasPick == .EYEDROPPER, id: "eyedropper") {
                vm.toggleCanvasPick(.EYEDROPPER)
            }
            ToolButton(icon: "cube", label: "3D", selected: store.canvas?.guide.draw3d == true, id: "draw3d") { vm.toggleDraw3d() }
            ToolButton(icon: "scribble.variable", label: L("mode_pen"), selected: vm.mode == .PEN, id: "pen_tool") {
                vm.chooseMode(vm.mode == .PEN ? .DRAW : .PEN)
            }
            if vm.mode == .PEN {
                ToolButton(icon: "arrow.uturn.backward.circle", label: L("undo"), id: "pen_undo") { vm.pen.undoAnchor() }
                ToolButton(icon: "checkmark.circle", label: L("apply_button"), id: "pen_finish") { vm.pen.finish(closed: false) }
            }
        case .SELECT, .MOVE, .ROTATE, .SCALE:
            ToolButton(icon: "rectangle.dashed", label: "rect", selected: vm.canvasPick == .RECT_SELECT, id: "rect_select") {
                if vm.mode != .SELECT { vm.chooseMode(.SELECT) }
                vm.toggleCanvasPick(.RECT_SELECT)
            }
            ToolButton(icon: "lasso", label: "lasso", selected: vm.canvasPick == .LASSO_SELECT, id: "lasso_select") {
                if vm.mode != .SELECT { vm.chooseMode(.SELECT) }
                vm.toggleCanvasPick(.LASSO_SELECT)
            }
            Divider().frame(height: 26)
            ToolButton(icon: "plus.square.on.square", label: L("duplicate_selection"), enabled: hasSelection, id: "duplicate") { vm.selection.duplicate() }
            ToolButton(icon: "trash", label: L("delete_selection"), enabled: hasSelection, id: "delete") { vm.selection.delete() }
            ToolButton(icon: "square.stack.3d.up", label: "group", enabled: hasSelection, id: "group") { vm.selection.group() }
            ToolButton(icon: "square.stack.3d.down.dottedline", label: "ungroup", enabled: hasSelection, id: "ungroup") { vm.selection.ungroup() }
            ToolButton(icon: "square.2.layers.3d.top.filled", label: "front", enabled: hasSelection, id: "bring_front") { vm.selection.reorder(1) }
            ToolButton(icon: "square.2.layers.3d.bottom.filled", label: "back", enabled: hasSelection, id: "send_back") { vm.selection.reorder(-1) }
            ToolButton(icon: "eye.slash", label: L("hide_elements"), enabled: hasSelection, id: "hide") { vm.hideElements() }
            ToolButton(icon: "slider.horizontal.3", label: L("mode_edit_pick"), enabled: hasSelection, id: "edit_selection") { _ = vm.editSelection() }
        case .CROP:
            ToolButton(icon: "checkmark", label: L("apply_button"), id: "crop_apply") { _ = vm.frame.applyCrop(); vm.finishAction() }
            ToolButton(icon: "arrow.counterclockwise", label: "reset", id: "crop_reset") { vm.frame.resetCrop() }
        case .SHAPE_EDIT:
            ShapeEditToolRow(vm: vm, shapes: vm.shapes)
        case .EXPAND, .SHAPE, .DIAGRAM, .FORMULA, .GUIDE, .PERSPECTIVE, .DIAGRAM_EDIT, .IMAGE_EDIT:
            ToolButton(icon: "checkmark", label: L("apply_button"), id: "finish_action") { vm.finishAction() }
        default:
            EmptyView()
        }
        if vm.mode.family == .DRAW || vm.mode == .SELECT {
            Divider().frame(height: 26)
            ToolButton(icon: "square.on.circle", label: L("add_shape"), id: "add_shape") { vm.shapes.openPicker() }
            ToolButton(icon: "textformat", label: L("add_text"), id: "add_text") { vm.startAction(.TEXT, target: []) }
            if store.canvas?.kind == .COMIC {
                ToolButton(icon: "bubble.left", label: L("add_balloon"), id: "add_comic") { comicOpen = true }
            }
            if store.canvas?.kind.isNotes == true {
                ToolButton(icon: "point.3.connected.trianglepath.dotted", label: L("add_diagram"), id: "add_diagram") { vm.diagrams.openPicker() }
                ToolButton(icon: "function", label: L("add_formula"), id: "add_formula") { vm.formulas.openPicker() }
            }
            PhotosPicker(selection: $photo, matching: .images) {
                Image(systemName: "photo.badge.plus").frame(width: 36, height: 36)
            }
            .accessibilityLabel(L("add_image"))
            .accessibilityIdentifier("add_image")
        }
    }

    private func openRuler() {
        let v = vm.viewport
        let center = v.toCanvas(P(v.viewSize.w / 2, v.viewSize.h / 2))
        ruler.openRuler(center: center, visibleWidth: v.viewSize.w / max(v.scale, 0.01), thickness: GuideMetrics.THICKNESS / max(v.scale, 0.01))
    }
}

/// Yazı modunda açık taslak: metin alanı, onay ve vazgeç (Android `InlineTextEditor`).
struct TextDraftBar: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var texts: TextController
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        if let draft = texts.draft {
            HStack(spacing: 8) {
                TextField(L("add_text"), text: $text, axis: .vertical)
                    .lineLimit(1...4)
                    .textFieldStyle(.roundedBorder)
                    .focused($focused)
                    .accessibilityIdentifier("text_draft_field")
                    .onChange(of: text) { _, t in texts.updateDraftText(t) }
                Button { vm.confirmText(text); text = "" } label: { Image(systemName: "checkmark.circle.fill").font(.title2) }
                    .accessibilityIdentifier("text_confirm")
                Button { texts.cancel(); text = "" } label: { Image(systemName: "xmark.circle").font(.title2) }
                    .accessibilityIdentifier("text_cancel")
            }
            .padding(10)
            .background(.regularMaterial)
            .onAppear { text = draft.content; focused = true }
            .onChange(of: draft.anchor) { _, _ in text = draft.content }
        }
    }
}
