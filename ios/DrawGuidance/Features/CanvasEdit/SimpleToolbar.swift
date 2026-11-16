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
    @ObservedObject var addUi: AddWindows
    @State private var photo: PhotosPickerItem?

    struct Tool: Identifiable {
        let id: String; let icon: String; let label: String; var selected = false; var enabled = true; let run: () -> Void
    }

    private var notes: Bool { store.canvas?.kind.isNotes == true }
    private var erasing: Bool { brush.settings.brushType == .ERASER }

    private var addTools: [Tool] {
        var t: [Tool] = []
        // v54 not almada yazı kutusu Ekle menüsünün en başında (Android ile aynı)
        if notes { t.append(Tool(id: "textbox", icon: "character.textbox", label: L("v53_quick_text"), selected: vm.quickText.active) { vm.toggleQuickText() }) }
        t += [
            Tool(id: "shape", icon: "square.on.circle", label: L("add_shape")) { vm.shapes.openPicker() },
            Tool(id: "text", icon: "textformat", label: L("add_text"), selected: vm.mode == .TEXT) { vm.startAction(.TEXT, target: []) },
        ]
        if notes {
            t += [
                Tool(id: "formula", icon: "function", label: L("add_formula")) { vm.formulas.openPicker() },
                // v52 grafik (şekil penceresinden ayrıldı) ve tablo ayrı düğmelerde
                Tool(id: "graph", icon: "chart.xyaxis.line", label: L("v52_add_graph")) { vm.shapes.openGraphPicker() },
                Tool(id: "table", icon: "tablecells", label: L("v52_add_table")) { vm.tables.openCreate() },
                Tool(id: "diagram", icon: "point.3.connected.trianglepath.dotted", label: L("add_diagram")) { vm.diagrams.openPicker() },
                Tool(id: "panels", icon: "rectangle.split.2x2", label: L("v45_panels_add")) { addUi.open = .panels },
            ]
        }
        t.append(Tool(id: "model3d", icon: "cube.transparent", label: L("add_model3d")) { addUi.open = .model3d })
        t.append(Tool(id: "background", icon: "photo.on.rectangle", label: L("add_background")) { sheet = .background })
        return t
    }

    private var drawTools: [Tool] {
        let drawing = vm.mode == .DRAW
        var t: [Tool] = [
            // v53 çizerken kaleme yeniden dokununca fırça penceresi açılır (fırça orada seçilir)
            Tool(id: "brush", icon: BrushSymbols.of(erasing ? vm.lastDrawingBrush : brush.settings.brushType), label: L("mode_draw"), selected: drawing && !erasing) {
                if drawing && !erasing { brushPickerOpen = true } else { vm.chooseMode(.DRAW); if erasing { brush.toggleEraser() } }
            },
            Tool(id: "eraser", icon: "eraser", label: L("brush_eraser"), selected: drawing && erasing) {
                vm.chooseMode(.DRAW); if !erasing { brush.toggleEraser() }
            },
            Tool(id: "paint", icon: "drop.fill", label: L("mode_paint"), selected: vm.mode == .PAINT) { vm.chooseMode(.PAINT) },
        ]
        if !notes {
            // v51 not almada damlalık yok
            t.append(Tool(id: "eyedropper", icon: "eyedropper", label: L("v43_eyedropper"), selected: vm.canvasPick == .EYEDROPPER) {
                if vm.mode != .DRAW { vm.chooseMode(.DRAW) }
                vm.toggleCanvasPick(.EYEDROPPER)
            })
        }
        t.append(Tool(id: "ruler", icon: "ruler", label: L("v40_ruler"), selected: ruler.session == .RULER) {
            if ruler.session == .RULER { ruler.finish() } else { openRuler() }
        })
        t.append(Tool(id: "template", icon: "star.square.on.square", label: L("v40_template"),
                      selected: ruler.session == .TEMPLATE || ruler.session == .TEMPLATE_PICK) {
            RulerOpen.template(vm, ruler)
        })
        if store.canvas?.kind == .SIMPLE_ART {
            // v50 yetişkin çizim alıştırmaları
            t.append(Tool(id: "practice", icon: "figure.strengthtraining.traditional", label: L("v50_practice"), selected: vm.practice.active) { vm.practice.listOpen = true })
            // v52 çizim yardımcıları
            t.append(Tool(id: "helpers", icon: "squareshape.split.3x3", label: L("v52_helpers_short"), selected: vm.helpers.active) { sheet = .helpers })
            // v53 canlı, nesne ve eşya yapı kılavuzları
            t.append(Tool(id: "structure", icon: "figure.stand", label: L("v53_struct_short"), selected: vm.structure.active) { vm.structure.sheet = true })
        }
        return t
    }

    private var editTools: [Tool] {
        let has = !store.selectedIds.isEmpty
        var t: [Tool] = [
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
            // v53 Elemanlar, Katmanlar ve Özellikler sağdan açılan panelde
            Tool(id: "elements", icon: "square.grid.2x2", label: L("tab_elements"), selected: vm.panel == .ELEMENTS) { withAnimation(.snappy(duration: 0.25)) { vm.togglePanel(.ELEMENTS) } },
            Tool(id: "layers", icon: "square.3.layers.3d", label: L("tab_layers"), selected: vm.panel == .LAYERS) { withAnimation(.snappy(duration: 0.25)) { vm.togglePanel(.LAYERS) } },
            Tool(id: "brush_settings", icon: "slider.horizontal.3", label: L("tab_brush"), selected: vm.panel == .BRUSH) { withAnimation(.snappy(duration: 0.25)) { vm.togglePanel(.BRUSH) } },
        ]
        if notes && (store.canvas?.guide.kind ?? .NONE) != .NONE {
            // v51 not almada kılavuz aracı yok: eski notlarda kalan kılavuz çizgileri buradan kaldırılır
            t.append(Tool(id: "remove_old_guides", icon: "grid", label: L("v51_notes_remove_old_guides")) { vm.guides.removeGuide() })
        }
        return t
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
            // v54 geri al / yinele Çiz menüsünün başında ama sabit değil: öteki düğmelerle birlikte kaydırılır
            menu("simple_menu_draw", fixed: []) {
                ForEach([
                    Tool(id: "undo", icon: "arrow.uturn.backward", label: L("undo"), enabled: store.canUndo) { vm.undo() },
                    Tool(id: "redo", icon: "arrow.uturn.forward", label: L("redo"), enabled: store.canRedo) { vm.redo() },
                ] + drawTools) { pill($0) }
            }
            // v53 seçili aracın ayarları menülerin altında (cetvel, fırça boyu, ekleme modlarında Bitti)
            ToolOptionsStrip(vm: vm, store: store, brush: brush, ruler: ruler, shapes: vm.shapes)
            if !notes && (vm.mode == .IMAGE_EDIT || vm.mode == .SHAPE_EDIT) { SimpleVectorRow(vm: vm, store: store, shapes: vm.shapes) }
            menu("simple_menu_edit", fixed: []) {
                ForEach(editTools) { pill($0) }
            }
        }
        .padding(.horizontal, 8)
        .padding(.bottom, 6)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("simple_tool_row")
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
        .padding(.horizontal, 6).padding(.vertical, 4)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(uiColor: .secondarySystemGroupedBackground))
                .shadow(color: .black.opacity(0.06), radius: 5, y: 2)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(tag)
    }

    private func pill(_ t: Tool) -> some View {
        Button(action: t.run) {
            HStack(spacing: 5) {
                Image(systemName: t.icon).font(.system(size: 14, weight: .medium))
                Text(t.label).font(.caption.weight(.semibold)).lineLimit(1).fixedSize()
            }
            .padding(.horizontal, 10).frame(height: 34)
            .foregroundStyle(t.selected ? Color.accentColor : Color.primary.opacity(0.85))
            .background(ChromeChip(selected: t.selected, radius: 11))
        }
        .buttonStyle(ChromePressStyle())
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

/// v52 basit resimde vektörleştirme ve vektör düzenleme (Android `SimpleVectorControls`): resim seçiliyken kırp ve
/// vektörleştir; vektör şekil düzenlenirken Noktalar / Çizgiler / Alan araçları ve "Ayrıntılı noktalar".
struct SimpleVectorRow: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var shapes: ShapeController

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                if vm.mode == .IMAGE_EDIT {
                    ToolButton(icon: "camera.filters", label: L("v52_photo_open"), id: "simple_image_photo_edit") { vm.openPhotoEditor() }
                    ToolButton(icon: "crop", label: L("image_crop"), id: "simple_image_crop") { vm.startAction(.CROP, target: store.selection) }
                    ToolButton(icon: "scribble.variable", label: L("vectorize_direct_auto"), selected: vm.imageEdit.tracing, id: "simple_vectorize_auto") {
                        vm.imageEdit.vectorize(EdgeOptions(mode: .AUTO), lineWidth: 2.5, keepImage: true)
                    }
                    ToolButton(icon: "viewfinder", label: L("vectorize_direct_edges"), id: "simple_vectorize_edges") {
                        vm.imageEdit.vectorize(EdgeOptions(mode: .EDGES), lineWidth: 2.5, keepImage: true)
                    }
                    ToolButton(icon: "point.topleft.down.to.point.bottomright.curvepath", label: L("vectorize_direct_lines"), id: "simple_vectorize_centerline") {
                        vm.imageEdit.vectorize(EdgeOptions(mode: .CENTERLINE), lineWidth: 2.5, keepImage: true)
                    }
                } else {
                    ForEach([ShapeTool.NODES, .LINES, .FILL], id: \.self) { t in
                        ToolButton(icon: ShapeEditToolRow.icon(t), label: ShapeEditToolRow.label(t),
                                   selected: t == .NODES ? shapes.tool.nodeFamily : shapes.tool == t, id: "simple_vector_\(t)") {
                            shapes.selectTool(t)
                        }
                    }
                    Toggle(L("v52_vector_fine_nodes"), isOn: Binding(get: { shapes.fineNodes }, set: { shapes.setFineNodes($0) }))
                        .font(.caption.weight(.semibold)).fixedSize()
                        .accessibilityIdentifier("simple_vector_fine")
                }
                ToolButton(icon: "checkmark", label: L("apply_button"), id: "simple_vector_done") { vm.finishAction() }
            }
            .padding(.horizontal, 8)
        }
        .padding(.vertical, 6)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.primary.opacity(0.04)))
        .accessibilityIdentifier("simple_vector_controls")
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
        .fullEditorSheet()
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
