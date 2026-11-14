import PhotosUI
import SwiftUI

/// v48 basit resim ve basit not alma (Android `SimpleMenus` + `SimpleMenuColumn`): üç menü alt alta, her biri tek satır
/// (sığmazsa yatay kaydırılır). Çiz menüsünde geri al / yinele satırın başında sabit.
/// 1. Ekle: şekil, yazı, formül, diyagram, panel, arka plan, resim.
/// 2. Çiz: kalem, silgi, boya, damlalık, cetvel, kalıp.
/// 3. Seç ve düzenle: seç, taşı, dikdörtgen seçim, genişlet; Elemanlar, Katmanlar, Özellikler.
struct SimpleToolbar: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var brush: BrushController
    @ObservedObject var ruler: RulerController
    @Binding var sheet: EditorSheet?
    @Binding var brushPickerOpen: Bool
    @State private var photo: PhotosPickerItem?
    @State private var panelsOpen = false
    @State private var models3dOpen = false

    struct Tool: Identifiable {
        let id: String; let icon: String; let label: String; var selected = false; var enabled = true; let run: () -> Void
    }

    private var notes: Bool { store.canvas?.kind == .SIMPLE_NOTES }
    private var erasing: Bool { brush.settings.brushType == .ERASER }

    private var addTools: [Tool] {
        var t = [
            Tool(id: "shape", icon: "square.on.circle", label: L("add_shape")) { vm.shapes.openPicker() },
            Tool(id: "text", icon: "textformat", label: L("add_text"), selected: vm.mode == .TEXT) { vm.startAction(.TEXT, target: []) },
        ]
        if notes {
            t += [
                Tool(id: "formula", icon: "function", label: L("add_formula")) { vm.formulas.openPicker() },
                Tool(id: "diagram", icon: "point.3.connected.trianglepath.dotted", label: L("add_diagram")) { vm.diagrams.openPicker() },
                Tool(id: "panels", icon: "rectangle.split.2x2", label: L("v45_panels_add")) { panelsOpen = true },
            ]
        }
        t.append(Tool(id: "model3d", icon: "cube.transparent", label: L("add_model3d")) { models3dOpen = true })
        t.append(Tool(id: "background", icon: "photo.on.rectangle", label: L("add_background")) { sheet = .background })
        return t
    }

    private var drawTools: [Tool] {
        let drawing = vm.mode == .DRAW
        return [
            Tool(id: "brush", icon: "pencil.tip", label: L("mode_draw"), selected: drawing && !erasing) {
                vm.chooseMode(.DRAW); if erasing { brush.toggleEraser() }
            },
            Tool(id: "eraser", icon: "eraser", label: L("brush_eraser"), selected: drawing && erasing) {
                vm.chooseMode(.DRAW); if !erasing { brush.toggleEraser() }
            },
            Tool(id: "paint", icon: "drop.fill", label: L("mode_paint"), selected: vm.mode == .PAINT) { vm.chooseMode(.PAINT) },
            Tool(id: "eyedropper", icon: "eyedropper", label: L("v43_eyedropper"), selected: vm.canvasPick == .EYEDROPPER) {
                if vm.mode != .DRAW { vm.chooseMode(.DRAW) }
                vm.toggleCanvasPick(.EYEDROPPER)
            },
            Tool(id: "ruler", icon: "ruler", label: L("v40_ruler"), selected: ruler.session == .RULER) {
                if ruler.session == .RULER { ruler.finish() } else { openRuler() }
            },
            Tool(id: "template", icon: "star.square.on.square", label: L("v40_template"),
                 selected: ruler.session == .TEMPLATE || ruler.session == .TEMPLATE_PICK) {
                if ruler.session == .TEMPLATE || ruler.session == .TEMPLATE_PICK { ruler.finish() } else { ruler.openTemplate(); ruler.libraryOpen = true }
            },
        ] + (store.canvas?.kind == .SIMPLE_ART ? [
            // v50 yetişkin çizim alıştırmaları
            Tool(id: "practice", icon: "figure.strengthtraining.traditional", label: L("v50_practice"), selected: vm.practice.active) { vm.practice.listOpen = true },
        ] : [])
    }

    private var editTools: [Tool] {
        let has = !store.selectedIds.isEmpty
        return [
            Tool(id: "select", icon: "hand.tap", label: L("mode_select"), selected: vm.mode == .SELECT && vm.canvasPick != .RECT_SELECT) {
                vm.chooseMode(.SELECT)
            },
            // v48 Taşı eleman seçiliyken, Genişlet tuval seçiliyken etkin
            Tool(id: "move", icon: "arrow.up.and.down.and.arrow.left.and.right", label: L("mode_move"), selected: vm.mode == .MOVE,
                 enabled: has || vm.mode == .MOVE) { vm.chooseMode(.MOVE) },
            Tool(id: "rect_select", icon: "rectangle.dashed", label: L("v43_select_rect"), selected: vm.canvasPick == .RECT_SELECT) {
                if vm.mode != .SELECT { vm.chooseMode(.SELECT) }
                vm.toggleCanvasPick(.RECT_SELECT)
            },
            Tool(id: "expand", icon: "aspectratio", label: L("mode_expand"), selected: vm.mode == .EXPAND,
                 enabled: !has || vm.mode == .EXPAND) { vm.chooseMode(.EXPAND) },
            Tool(id: "elements", icon: "list.bullet.rectangle", label: L("tab_elements")) { sheet = .elements },
            Tool(id: "layers", icon: "square.3.layers.3d", label: L("tab_layers")) { sheet = .layers },
            Tool(id: "brush_settings", icon: "slider.horizontal.3", label: L("brush_settings_toggle")) { brushPickerOpen = true },
        ]
    }

    var body: some View {
        VStack(spacing: 6) {
            menu("simple_menu_add", fixed: []) {
                ForEach(addTools) { pill($0) }
                PhotosPicker(selection: $photo, matching: .images) {
                    Label(L("add_image"), systemImage: "photo").font(.caption.weight(.semibold)).lineLimit(1)
                        .padding(.horizontal, 10).padding(.vertical, 7)
                        .background(Capsule().fill(Color.primary.opacity(0.06)))
                }
                .accessibilityIdentifier("simple_tool_image")
                .tutorialAnchor(TutorialAnchor.simple("image"))
            }
            menu("simple_menu_draw", fixed: [
                Tool(id: "undo", icon: "arrow.uturn.backward", label: L("undo"), enabled: store.canUndo) { vm.undo() },
                Tool(id: "redo", icon: "arrow.uturn.forward", label: L("redo"), enabled: store.canRedo) { vm.redo() },
            ]) {
                ForEach(drawTools) { pill($0) }
            }
            if ruler.session != .NONE { RulerBar(ruler: ruler).padding(.horizontal, 12) }
            menu("simple_menu_edit", fixed: []) {
                ForEach(editTools) { pill($0) }
            }
        }
        .padding(.horizontal, 8)
        .padding(.bottom, 6)
        .accessibilityIdentifier("simple_tool_row")
        .sheet(isPresented: $models3dOpen) {
            if let repo = EditorDependencies.shared.models3d {
                Model3DPickerSheet(vm: vm, repository: repo, kind: store.canvas?.kind ?? .SIMPLE_ART)
            }
        }
        .sheet(isPresented: $panelsOpen) { NotesPanelsSheet { id in vm.v45.insertPanels(id); panelsOpen = false } }
        .sheet(isPresented: Binding(get: { ruler.libraryOpen }, set: { ruler.libraryOpen = $0 })) {
            ShapePickerView(kind: store.canvas?.kind ?? .SIMPLE_ART) { id in
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

    private func menu<Content: View>(_ tag: String, fixed: [Tool], @ViewBuilder _ content: () -> Content) -> some View {
        HStack(spacing: 6) {
            ForEach(fixed) { pill($0) }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) { content() }
            }
        }
        .padding(.horizontal, 8).padding(.vertical, 6)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.primary.opacity(0.04)))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(tag)
    }

    private func pill(_ t: Tool) -> some View {
        Button(action: t.run) {
            Label(t.label, systemImage: t.icon)
                .font(.caption.weight(.semibold)).lineLimit(1)
                .padding(.horizontal, 10).padding(.vertical, 7)
                .foregroundStyle(t.selected ? Color.accentColor : Color.primary)
                .background(Capsule().fill(t.selected ? Color.accentColor.opacity(0.18) : Color.primary.opacity(0.06)))
        }
        .buttonStyle(.plain)
        .disabled(!t.enabled)
        .opacity(t.enabled ? 1 : 0.4)
        .accessibilityIdentifier("simple_tool_\(t.id)")
        .tutorialAnchor(t.id == "undo" ? TutorialAnchor.UNDO : t.id == "redo" ? TutorialAnchor.REDO : TutorialAnchor.simple(t.id))
    }

    private func openRuler() {
        let v = vm.viewport
        let center = v.toCanvas(P(v.viewSize.w / 2, v.viewSize.h / 2))
        ruler.openRuler(center: center, visibleWidth: v.viewSize.w / max(v.scale, 0.01), thickness: GuideMetrics.THICKNESS / max(v.scale, 0.01))
    }
}

/// v45 not alma panel yerleşimi seçimi (Android "Panel ekle" penceresi).
struct NotesPanelsSheet: View {
    let onPick: (String) -> Void

    var body: some View {
        NavigationStack {
            List {
                Section { Text(L("v45_panels_hint")).font(.footnote).foregroundStyle(.secondary) }
                ForEach(NotesPanels.layouts) { l in
                    Button { onPick(l.id) } label: {
                        HStack(spacing: 12) {
                            PanelLayoutThumb(layout: l).frame(width: 44, height: 58)
                            Text(I18n.tr(l.title)).foregroundStyle(.primary)
                        }
                    }
                    .accessibilityIdentifier("notes_panels_\(l.id)")
                }
            }
            .navigationTitle(L("v45_panels_title"))
            .navigationBarTitleDisplayMode(.inline)
        }
        .dockedEditorSheet()
    }
}

private struct PanelLayoutThumb: View {
    let layout: NotesPanels.Layout
    var body: some View {
        Canvas { g, size in
            g.stroke(Path(roundedRect: CGRect(origin: .zero, size: size), cornerRadius: 3), with: .color(.secondary.opacity(0.5)), lineWidth: 1)
            for r in layout.rects(Float(size.width), Float(size.height)) {
                let rect = CGRect(x: CGFloat(r[0]), y: CGFloat(r[1]), width: CGFloat(r[2] - r[0]), height: CGFloat(r[3] - r[1]))
                g.fill(Path(roundedRect: rect, cornerRadius: 2), with: .color(.accentColor.opacity(0.15)))
                g.stroke(Path(roundedRect: rect, cornerRadius: 2), with: .color(.accentColor.opacity(0.6)), lineWidth: 1)
            }
        }
    }
}
