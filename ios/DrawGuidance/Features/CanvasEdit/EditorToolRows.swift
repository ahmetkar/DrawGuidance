import PhotosUI
import SwiftUI

/// Bir araç düğmesi (Android `ToolItem`).
struct ToolButton: View {
    let icon: String
    let label: String
    var selected = false
    var enabled = true
    var id: String? = nil
    /// Eğitimde tanıtılan aracın yeri.
    var anchor: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon).frame(width: 36, height: 36)
                .background(RoundedRectangle(cornerRadius: 10).fill(selected ? Color.accentColor.opacity(0.2) : .clear))
        }
        .disabled(!enabled)
        .accessibilityLabel(label)
        .accessibilityIdentifier(id ?? label)
        .tutorialAnchor(anchor)
    }
}

/// v18 moda göre değişen ikinci araç satırı: Çiz'de cetvel/damlalık, Seç'te kopyala/sil/grup/sıra, Kırp'ta uygula, Ekle'de eklenecekler.
struct ModeToolRow: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var ruler: RulerController
    @State private var photo: PhotosPickerItem?
    @State private var comicOpen = false
    @State private var charactersOpen = false
    @State private var locationsOpen = false
    @State private var objectsOpen = false
    @State private var models3dOpen = false
    @State private var panelsOpen = false

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) { items }.padding(.horizontal, 12)
        }
        .frame(height: 40)
        .sheet(isPresented: $comicOpen) { ComicInsertSheet(vm: vm) }
        .sheet(isPresented: $charactersOpen) { CharactersSheet { v in vm.insertCharacter(v) } }
        .sheet(isPresented: $locationsOpen) { LocationBackgroundsSheet { b in vm.pickLocation(b) } }
        .sheet(isPresented: $models3dOpen) {
            if let repo = EditorDependencies.shared.models3d {
                Model3DPickerSheet(vm: vm, repository: repo, kind: store.canvas?.kind ?? .ART)
            }
        }
        .sheet(isPresented: $objectsOpen) { ComicObjectsSheet { p in vm.insertComicProp(.OBJECT, p) } }
        .sheet(isPresented: $panelsOpen) { NotesPanelsSheet { id in vm.v45.insertPanels(id); panelsOpen = false } }
        .sheet(isPresented: Binding(get: { ruler.libraryOpen }, set: { ruler.libraryOpen = $0 })) {
            ShapePickerView(kind: store.canvas?.kind ?? .ART) { id in
                let v = vm.viewport
                let center = v.toCanvas(P(v.viewSize.w / 2, v.viewSize.h / 2))
                _ = ruler.placeLibrary(id, center: center, size: min(v.viewSize.w, v.viewSize.h) * 0.4 / max(v.scale, 0.01))
                ruler.libraryOpen = false
            }
        }
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
            ToolButton(icon: "ruler", label: L("v40_ruler"), selected: ruler.session == .RULER, id: "ruler_button", anchor: TutorialAnchor.RULER) {
                if ruler.session == .RULER { ruler.finish() } else { openRuler() }
            }
            ToolButton(icon: "star.square.on.square", label: L("v40_template"), selected: ruler.session == .TEMPLATE || ruler.session == .TEMPLATE_PICK,
                       id: "template_button", anchor: TutorialAnchor.TEMPLATE) {
                if ruler.session == .TEMPLATE || ruler.session == .TEMPLATE_PICK { ruler.finish() } else { ruler.openTemplate(); ruler.libraryOpen = true }
            }
            if ruler.session != .NONE { RulerBar(ruler: ruler) }
            if store.canvas?.kind == .ART {
                ToolButton(icon: "figure.strengthtraining.traditional", label: L("v50_practice"), selected: vm.practice.active, id: "practice_button") {
                    vm.practice.listOpen = true
                }
            }
            ToolButton(icon: "eyedropper", label: "eyedropper", selected: vm.canvasPick == .EYEDROPPER, id: "eyedropper", anchor: TutorialAnchor.simple("eyedropper")) {
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
            ToolButton(icon: "rectangle.dashed", label: "rect", selected: vm.canvasPick == .RECT_SELECT, id: "rect_select", anchor: TutorialAnchor.simple("rect_select")) {
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
            ToolButton(icon: "square.on.circle", label: L("add_shape"), id: "add_shape", anchor: TutorialAnchor.add("shape")) { vm.shapes.openPicker() }
            ToolButton(icon: "textformat", label: L("add_text"), id: "add_text", anchor: TutorialAnchor.add("text")) { vm.startAction(.TEXT, target: []) }
            if store.canvas?.kind == .COMIC {
                ToolButton(icon: "bubble.left", label: L("add_balloon"), id: "add_comic", anchor: TutorialAnchor.add("balloon")) { comicOpen = true }
                ToolButton(icon: "figure.wave", label: L("add_characters"), id: "add_characters", anchor: TutorialAnchor.add("characters")) { charactersOpen = true }
                ToolButton(icon: "mountain.2", label: L("v41_add_location"), id: "add_locations", anchor: TutorialAnchor.add("locations")) { locationsOpen = true }
                ToolButton(icon: "shippingbox", label: L("v42_add_objects"), id: "add_objects", anchor: TutorialAnchor.add("objects")) { objectsOpen = true }
            }
            if store.canvas?.kind.isNotes == true {
                ToolButton(icon: "point.3.connected.trianglepath.dotted", label: L("add_diagram"), id: "add_diagram", anchor: TutorialAnchor.add("diagram")) { vm.diagrams.openPicker() }
                ToolButton(icon: "function", label: L("add_formula"), id: "add_formula", anchor: TutorialAnchor.add("formula")) { vm.formulas.openPicker() }
                ToolButton(icon: "rectangle.split.2x2", label: L("v45_panels_add"), id: "add_panels", anchor: TutorialAnchor.add("panels")) { panelsOpen = true }
            }
            ToolButton(icon: "cube.transparent", label: L("add_model3d"), id: "add_model3d", anchor: TutorialAnchor.add("model3d")) { models3dOpen = true }
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

/// v40 cetvel ve kalıp çubuğu (Android cetvel çubuğu): tür, birim, ölçek, kilit ve Bitir.
struct RulerBar: View {
    @ObservedObject var ruler: RulerController

    private static let types: [(RulerType, String)] = [
        (.STRAIGHT, "v40_ruler_straight"), (.PROTRACTOR, "v40_ruler_protractor"), (.SET_SQUARE_45, "v40_ruler_set45"),
        (.SET_SQUARE_30, "v40_ruler_set30"), (.COMPASS, "v40_ruler_compass"), (.ELLIPSE, "v40_ruler_ellipse"), (.FRENCH_CURVE, "v40_ruler_french"),
    ]

    var body: some View {
        HStack(spacing: 6) {
            if ruler.session == .RULER {
                Menu {
                    ForEach(Self.types, id: \.1) { t in
                        Button { ruler.setType(t.0) } label: {
                            if ruler.settings.type == t.0 { Label(L(t.1), systemImage: "checkmark") } else { Text(L(t.1)) }
                        }
                    }
                } label: { Image(systemName: "triangle").frame(width: 36, height: 36) }
                .accessibilityLabel(L("v40_ruler_type")).accessibilityIdentifier("ruler_type").tutorialAnchor(TutorialAnchor.RULER_TYPE)
                Menu {
                    ForEach(RulerUnit.allCases, id: \.self) { u in Button(u.label) { ruler.setUnit(u) } }
                } label: { Text(ruler.settings.unit.label).font(.caption.bold()).frame(width: 36, height: 36) }
                .accessibilityLabel(L("v40_ruler_unit")).accessibilityIdentifier("ruler_unit").tutorialAnchor(TutorialAnchor.RULER_UNIT)
                Menu {
                    ForEach([1, 2, 5, 10, 20, 50, 100], id: \.self) { k in Button("1:\(k)") { ruler.setScale(k) } }
                } label: { Text("1:\(ruler.settings.scale)").font(.caption.bold()).frame(minWidth: 36, minHeight: 36) }
                .accessibilityLabel(L("v40_ruler_scale")).accessibilityIdentifier("ruler_scale").tutorialAnchor(TutorialAnchor.RULER_SCALE)
            } else {
                ToolButton(icon: "books.vertical", label: L("v40_template_library"), id: "template_library", anchor: TutorialAnchor.TEMPLATE_LIBRARY) {
                    ruler.libraryOpen = true
                }
                if ruler.session == .TEMPLATE {
                    ToolButton(icon: ruler.locked ? "lock.fill" : "lock.open", label: L(ruler.locked ? "v40_template_unlock" : "v40_template_lock"),
                               selected: ruler.locked, id: "template_lock") { ruler.toggleLock() }
                }
            }
            Button(L("v40_ruler_finish")) { ruler.finish() }
                .font(.caption.bold()).buttonStyle(.borderedProminent)
                .accessibilityIdentifier("ruler_finish").tutorialAnchor(TutorialAnchor.RULER_FINISH)
        }
        .tutorialAnchor(TutorialAnchor.RULER_BAR)
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
