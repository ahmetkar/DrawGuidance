import SwiftUI
import UniformTypeIdentifiers

/// 3B düzenleyici penceresi (Android `Model3DEditorScreen`):
/// üstte geri, ad, Parçalar, Ayarlar, Kaydet ve ⋯; ortada sahne; altında açılan panel; modun araç satırı; en altta mod
/// çubuğu: Seç · Düzenle · Çiz · Boya · Ekle.
struct Model3DEditorView: View {
    @StateObject private var vm: Model3DEditorModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.horizontalSizeClass) private var hsize
    @State private var exitAsk = false
    @State private var renaming = false
    @State private var savingAs = false
    @State private var imageExport = false
    @State private var clearAsk = false
    @State private var toast: String?
    @State private var nameText = ""
    @State private var shareItem: ShareFile?
    @State private var exportDoc: GlbDocument?

    init(repository: Model3DRepository, modelId: String?, canvasId: String? = nil, strokeId: String? = nil, initialName: String? = nil) {
        _vm = StateObject(wrappedValue: Model3DEditorModel(repository: repository, modelId: modelId, canvasId: canvasId, strokeId: strokeId, initialName: initialName))
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            GeometryReader { geo in
                if hsize == .regular {
                    HStack(spacing: 0) {
                        SceneArea3(vm: vm, toast: toast)
                        if let p = vm.panel { PanelFrame3(vm: vm, panel: p).frame(width: 340).padding(8) }
                    }
                } else {
                    VStack(spacing: 0) {
                        SceneArea3(vm: vm, toast: toast)
                        if let p = vm.panel { PanelFrame3(vm: vm, panel: p).frame(maxHeight: geo.size.height * 0.5).padding(.horizontal, 8).padding(.vertical, 4) }
                    }
                }
            }
            if let op = vm.op { OpBar3(vm: vm, op: op) } else { ContextRow3(vm: vm, onClearAll: { clearAsk = true }) }
            ModeBar3(mode: vm.mode) { vm.setMode($0) }
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .accessibilityIdentifier("model3d_editor")
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
        .overlay { if vm.model == nil { ProgressView(L("loading_model")).padding().background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14)) } }
        .onReceive(vm.$event) { e in
            guard let e = e?.1 else { return }
            switch e {
            case .saved(let name, let toCanvas): show(toCanvas ? L("model3d_saved_canvas", name) : L("model3d_saved", name))
            case .exit: dismiss()
            case .message(let t): show(t)
            }
        }
        .alert(L("model3d_exit_title"), isPresented: $exitAsk) {
            Button(L("model3d_save")) { vm.save(exitAfter: true) }
            Button(L("model3d_discard"), role: .destructive) { dismiss() }
            Button(L("cancel_button"), role: .cancel) {}
        } message: { Text(L(vm.fromCanvas ? "model3d_exit_text_canvas" : "model3d_exit_text")) }
        .alert(L("model3d_clear_all_title"), isPresented: $clearAsk) {
            Button(L("model3d_clear_confirm"), role: .destructive) { vm.clearDrawings() }
            Button(L("cancel_button"), role: .cancel) {}
        } message: { Text(L("model3d_clear_all_text")) }
        .alert(L("model3d_rename"), isPresented: $renaming) {
            TextField(L("model3d_name"), text: $nameText)
            Button("OK") { vm.rename(nameText) }
            Button(L("cancel_button"), role: .cancel) {}
        }
        .alert(L("model3d_save_as"), isPresented: $savingAs) {
            TextField(L("model3d_name"), text: $nameText)
            Button("OK") { vm.saveAs(nameText) }
            Button(L("cancel_button"), role: .cancel) {}
        }
        .sheet(isPresented: $imageExport) { ImageExportSheet3(vm: vm) }
        .sheet(item: $shareItem) { ShareSheet(items: [$0.url]) }
        .fileExporter(isPresented: Binding(get: { exportDoc != nil }, set: { if !$0 { exportDoc = nil } }), document: exportDoc,
                      contentType: .glb, defaultFilename: safeName) { r in
            if case .failure(let e) = r { vm.message(L("model3d_export_failed", e.localizedDescription)) } else { vm.message(L("model3d_exported")) }
        }
    }

    private var safeName: String {
        (vm.model?.name ?? "model").replacingOccurrences(of: "[\\\\/:*?\"<>|]", with: "_", options: .regularExpression)
    }

    private func show(_ text: String) {
        toast = text
        let current = text
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.2) { if toast == current { toast = nil } }
    }

    private func tryBack() { if vm.dirty { exitAsk = true } else { dismiss() } }

    private var topBar: some View {
        HStack(spacing: 6) {
            Button { tryBack() } label: { Image(systemName: "chevron.backward").frame(width: 38, height: 38) }
                .accessibilityLabel(L("back"))
            VStack(alignment: .leading, spacing: 0) {
                Text(vm.model?.name ?? "").font(.headline).lineLimit(1)
                if let m = vm.model { Text(L("model3d_info", m.parts.count, m.triangleCount)).font(.caption2).foregroundStyle(.secondary) }
            }
            Spacer()
            iconButton("square.3.layers.3d", L("model3d_panel_parts"), selected: vm.panel == .PARTS) { vm.togglePanel(.PARTS) }
            iconButton("gearshape", L("model3d_panel_settings"), selected: vm.panel == .SETTINGS) { vm.togglePanel(.SETTINGS) }
            iconButton("square.and.arrow.down", L("model3d_save"), selected: vm.dirty) { vm.save() }
            Menu {
                Button { nameText = vm.model?.name ?? ""; renaming = true } label: { Label(L("model3d_rename"), systemImage: "pencil") }
                Button { nameText = (vm.model?.name ?? "") + " (2)"; savingAs = true } label: { Label(L("model3d_save_as"), systemImage: "doc.on.doc") }
                Button { vm.fitView() } label: { Label(L("model3d_fit"), systemImage: "viewfinder") }
                Button { imageExport = true } label: { Label(L("model3d_export_image"), systemImage: "photo") }
                Button {
                    Task { if let b = await vm.exportGlb() { exportDoc = GlbDocument(data: Data(b)) } }
                } label: { Label(L("model3d_export"), systemImage: "square.and.arrow.down.on.square") }
                    .accessibilityIdentifier("model3d_export_item")
                Button {
                    Task {
                        guard let b = await vm.exportGlb() else { return }
                        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(safeName).glb")
                        do { try Data(b).write(to: url); shareItem = ShareFile(url: url) } catch { vm.message(L("model3d_export_failed", error.localizedDescription)) }
                    }
                } label: { Label(L("model3d_share"), systemImage: "square.and.arrow.up") }
                    .accessibilityIdentifier("model3d_share_item")
            } label: { Image(systemName: "ellipsis.circle").frame(width: 38, height: 38) }
                .accessibilityLabel(L("more_options"))
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
    }

    private func iconButton(_ icon: String, _ label: String, selected: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).frame(width: 38, height: 38)
                .background(selected ? Color.accentColor.opacity(0.15) : .clear, in: Circle())
        }
        .accessibilityLabel(label)
    }
}

struct ShareFile: Identifiable { let url: URL; var id: String { url.path } }

/// UIKit paylaşım penceresi.
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}

extension UTType {
    static let glb = UTType(filenameExtension: "glb") ?? .data
    static let gltf = UTType(filenameExtension: "gltf") ?? .json
}

/// GLB dosyası (dosya seçiciyle dışa aktarma).
struct GlbDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.glb] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

// MARK: - sahne alanı

private struct SceneArea3: View {
    @ObservedObject var vm: Model3DEditorModel
    let toast: String?
    @State private var hintOn = true
    @State private var hintTask: Task<Void, Never>?

    var body: some View {
        ZStack {
            Model3DViewport(vm: vm)
            VStack {
                HStack(alignment: .top) {
                    if hintOn {
                        Text(hint).font(.caption).padding(.horizontal, 10).padding(.vertical, 6)
                            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
                            .frame(maxWidth: 340, alignment: .leading)
                            .allowsHitTesting(false)
                            .accessibilityIdentifier("model3d_hint")
                    } else {
                        Button { showHint() } label: { Image(systemName: "questionmark.circle").font(.title3) }
                            .accessibilityLabel(L("model3d_hint_show"))
                    }
                    Spacer()
                    VStack(spacing: 8) {
                        round("plus.magnifyingglass", L("model3d_zoom_in")) { vm.zoom(1.25) }
                        round("minus.magnifyingglass", L("model3d_zoom_out")) { vm.zoom(0.8) }
                        round("viewfinder", L("model3d_fit")) { vm.fitView() }
                        Menu {
                            Button(L("model3d_view_iso")) { vm.presetView(0) }
                            Button(L("model3d_view_front")) { vm.presetView(1) }
                            Button(L("model3d_view_side")) { vm.presetView(2) }
                            Button(L("model3d_view_top")) { vm.presetView(3) }
                        } label: { Image(systemName: "rotate.3d").frame(width: 36, height: 36).background(.regularMaterial, in: Circle()) }
                            .accessibilityLabel(L("model3d_views"))
                    }
                }
                .padding(8)
                Spacer()
                if let s = vm.suggestion { SuggestionBubble3(vm: vm, s: s) }
                if let toast {
                    Text(toast).font(.footnote).foregroundStyle(.white).padding(.horizontal, 14).padding(.vertical, 8)
                        .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 12)).padding(.horizontal, 12)
                }
                SelectionChip3(vm: vm).padding(.bottom, 10)
            }
            if let label = vm.busy {
                HStack(spacing: 10) { ProgressView(); Text(label).font(.callout) }
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
                    .accessibilityIdentifier("model3d_busy")
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .onAppear { showHint() }
        .onChange(of: vm.tool) { _, _ in showHint() }
        .onChange(of: vm.mode) { _, _ in showHint() }
        .onChange(of: vm.faceMode) { _, _ in showHint() }
        .onChange(of: vm.paintScope) { _, _ in showHint() }
    }

    private func showHint() {
        hintOn = true
        hintTask?.cancel()
        hintTask = Task { try? await Task.sleep(nanoseconds: 4_500_000_000); if !Task.isCancelled { hintOn = false } }
    }

    private func round(_ icon: String, _ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) { Image(systemName: icon).frame(width: 36, height: 36).background(.regularMaterial, in: Circle()) }
            .accessibilityLabel(label)
    }

    private var hint: String {
        if vm.mode == .ADD { return L("model3d_hint_add") }
        if vm.tool == .PAINT {
            switch vm.paintScope {
            case .FACE: return L("model3d_hint_paint_face")
            case .SURFACE: return L("model3d_hint_paint_surface")
            case .PART: return L("model3d_hint_paint_part")
            case .ALL: return L("model3d_hint_paint_all")
            }
        }
        if vm.tool == .FACE && vm.faceMode == .SURFACE { return L("model3d_hint_face_surface") }
        switch vm.tool {
        case .VIEW: return L("model3d_hint_view")
        case .MOVE: return L("model3d_hint_move")
        case .ROTATE: return L("model3d_hint_rotate")
        case .SCALE: return L("model3d_hint_scale")
        case .POINT: return L("model3d_hint_point")
        case .EDGE: return L("model3d_hint_edge")
        case .FACE: return L("model3d_hint_face")
        case .PAINT: return L("model3d_hint_paint")
        case .DRAW: return L("model3d_hint_draw")
        case .ERASE: return L("model3d_hint_erase")
        }
    }
}

private struct SelectionChip3: View {
    @ObservedObject var vm: Model3DEditorModel
    var body: some View {
        if let m = vm.model {
            let parts = vm.selection.parts.compactMap { m.part($0) }
            if !parts.isEmpty {
                let g = parts[0].group
                let group: Group3? = g.flatMap { gid in
                    parts.allSatisfy({ $0.group == gid }) && parts.count == m.parts.filter({ $0.group == gid }).count ? m.groups.first { $0.id == gid } : nil
                }
                let text = group.map { L("model3d_selected_group", $0.name, parts.count) } ?? (parts.count == 1 ? parts[0].name : L("model3d_selected_parts", parts.count))
                Button { vm.togglePanel(.PARTS) } label: {
                    Label(text, systemImage: group != nil ? "square.stack.3d.up" : "cube").font(.footnote).lineLimit(1)
                        .padding(.horizontal, 12).frame(height: 32).background(.regularMaterial, in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("model3d_selection_chip")
            }
        }
    }
}

private struct SuggestionBubble3: View {
    @ObservedObject var vm: Model3DEditorModel
    let s: Model3DEditorModel.SuggestUi

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                Image(systemName: "sparkles").foregroundStyle(Color.accentColor).accessibilityLabel(L("model3d_suggest_title"))
                ForEach(Array(s.kinds.enumerated()), id: \.offset) { _, k in
                    Button { vm.applySuggestion(k) } label: {
                        Label(Model3DEditorModel.suggestName(k), systemImage: Self.icon(k)).font(.callout).lineLimit(1)
                            .padding(.horizontal, 12).frame(height: 38).background(Color.accentColor.opacity(0.12), in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("model3d_suggest_\(k)")
                }
                Button { vm.dismissSuggestion() } label: { Image(systemName: "xmark").frame(width: 34, height: 34) }
                    .accessibilityLabel(L("model3d_suggest_dismiss"))
            }
            .padding(4)
        }
        .background(.regularMaterial, in: Capsule())
        .padding(.horizontal, 10)
        .accessibilityIdentifier("model3d_suggest")
        .task(id: ObjectIdentifier(s)) {
            try? await Task.sleep(nanoseconds: 12_000_000_000)
            if !Task.isCancelled { vm.expireSuggestion(s) }
        }
    }

    static func icon(_ k: Suggest3Kind) -> String {
        switch k {
        case .SPHERE, .ELLIPSOID, .DOME: return "circle.fill"
        case .CUBE, .BOX, .SLAB: return "square"
        case .CONE, .PYRAMID, .PRISM3: return "triangle"
        case .TORUS: return "circle.circle"
        case .STAR: return "star"
        case .ROD: return "minus"
        case .SPRING: return "water.waves"
        case .TUBE, .RIBBON, .WALL, .LATHE, .INFLATE: return "paintbrush.pointed"
        default: return "cube"
        }
    }
}

// MARK: - mod çubuğu ve araç satırı

private struct ModeBar3: View {
    let mode: Mode3
    let onMode: (Mode3) -> Void
    private static let items: [(Mode3, String, String)] = [
        (.SELECT, "hand.tap", "model3d_mode_select"), (.EDIT, "point.3.connected.trianglepath.dotted", "model3d_mode_edit"),
        (.DRAW, "paintbrush.pointed", "model3d_mode_draw"), (.PAINT, "paintbrush", "model3d_mode_paint"), (.ADD, "plus.square", "model3d_mode_add"),
    ]

    var body: some View {
        HStack(spacing: 2) {
            ForEach(Self.items, id: \.2) { item in
                let on = item.0 == mode
                Button { onMode(item.0) } label: {
                    HStack(spacing: 5) {
                        Image(systemName: item.1)
                        if on { Text(L(item.2)).font(.footnote.weight(.semibold)).lineLimit(1) }
                    }
                    .foregroundStyle(on ? Color.accentColor : .secondary)
                    .frame(maxWidth: .infinity).frame(height: 44)
                    .background(on ? Color(uiColor: .systemBackground) : .clear, in: Capsule())
                }
                .buttonStyle(.plain)
                .layoutPriority(on ? 2 : 1)
                .accessibilityLabel(L(item.2))
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(5)
        .background(Color(uiColor: .secondarySystemBackground), in: Capsule())
        .padding(.horizontal, 10).padding(.vertical, 6)
        .accessibilityIdentifier("model3d_modes")
    }
}

/// Araç satırında düğme: simge ve kısa ad.
struct ActionPill3: View {
    let icon: String, label: String
    var enabled = true, danger = false, selected = false
    let action: () -> Void

    init(_ icon: String, _ label: String, enabled: Bool = true, danger: Bool = false, selected: Bool = false, action: @escaping () -> Void) {
        self.icon = icon; self.label = label; self.enabled = enabled; self.danger = danger; self.selected = selected; self.action = action
    }

    var body: some View {
        Button(action: action) {
            Label(label, systemImage: icon).font(.callout).lineLimit(1).fixedSize()
                .foregroundStyle(!enabled ? Color.secondary.opacity(0.5) : danger ? .red : selected ? Color.accentColor : .primary)
                .padding(.horizontal, 14).frame(height: 42)
                .background(selected ? Color.accentColor.opacity(0.15) : Color(uiColor: .secondarySystemBackground), in: Capsule())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

private struct Seg3 { let icon: String; let label: String; let selected: Bool; let action: () -> Void }

private struct SubToolBar3: View {
    let items: [Seg3]
    var body: some View {
        HStack(spacing: 4) {
            ForEach(Array(items.enumerated()), id: \.offset) { _, s in
                Button(action: s.action) {
                    Label(s.label, systemImage: s.icon).font(items.count >= 4 ? .footnote : .callout).lineLimit(1)
                        .foregroundStyle(s.selected ? Color.accentColor : .primary)
                        .frame(maxWidth: .infinity).frame(height: 42)
                        .background(s.selected ? Color(uiColor: .systemBackground) : .clear, in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(s.selected ? .isSelected : [])
            }
        }
        .padding(4)
        .background(Color(uiColor: .secondarySystemBackground), in: Capsule())
        .padding(.horizontal, 12).padding(.top, 6)
        .accessibilityIdentifier("model3d_subtools")
    }
}

private struct ContextRow3: View {
    @ObservedObject var vm: Model3DEditorModel
    let onClearAll: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            subTools
            HStack(spacing: 6) {
                Button { vm.undo() } label: { Image(systemName: "arrow.uturn.backward").frame(width: 42, height: 42) }
                    .disabled(!vm.canUndo).accessibilityLabel(L("undo")).accessibilityIdentifier("model3d_undo")
                Button { vm.redo() } label: { Image(systemName: "arrow.uturn.forward").frame(width: 42, height: 42) }
                    .disabled(!vm.canRedo).accessibilityLabel(L("redo")).accessibilityIdentifier("model3d_redo")
                Divider().frame(height: 28)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) { actions }.padding(.horizontal, 8).padding(.vertical, 6)
                }
                .accessibilityIdentifier("model3d_actions")
            }
            .padding(.leading, 12).padding(.top, 4)
        }
    }

    @ViewBuilder private var subTools: some View {
        let t = vm.tool
        switch vm.mode {
        case .SELECT:
            SubToolBar3(items: [
                Seg3(icon: "arrow.up.and.down.and.arrow.left.and.right", label: L("model3d_tool_move"), selected: t == .MOVE || t == .VIEW) { vm.setTool(.MOVE) },
                Seg3(icon: "arrow.clockwise", label: L("model3d_tool_rotate"), selected: t == .ROTATE) { vm.setTool(.ROTATE) },
                Seg3(icon: "arrow.up.left.and.arrow.down.right", label: L("model3d_tool_scale"), selected: t == .SCALE) { vm.setTool(.SCALE) },
            ])
        case .EDIT:
            SubToolBar3(items: [
                Seg3(icon: "smallcircle.filled.circle", label: L("model3d_tool_point"), selected: t == .POINT) { vm.setTool(.POINT) },
                Seg3(icon: "line.diagonal", label: L("model3d_tool_edge"), selected: t == .EDGE) { vm.setTool(.EDGE) },
                Seg3(icon: "square", label: L("model3d_tool_face"), selected: t == .FACE) { vm.setTool(.FACE) },
            ])
        case .DRAW:
            SubToolBar3(items: [
                Seg3(icon: "paintbrush.pointed", label: L("model3d_tool_brush"), selected: t == .DRAW) { vm.setTool(.DRAW) },
                Seg3(icon: "eraser", label: L("model3d_tool_eraser"), selected: t == .ERASE) { vm.setTool(.ERASE) },
            ])
        case .PAINT:
            let s = vm.paintScope
            SubToolBar3(items: [
                Seg3(icon: "square", label: L("model3d_paint_scope_face"), selected: s == .FACE) { vm.setPaintScope(.FACE) },
                Seg3(icon: "square.grid.3x3", label: L("model3d_paint_scope_surface"), selected: s == .SURFACE) { vm.setPaintScope(.SURFACE) },
                Seg3(icon: "cube", label: L("model3d_paint_scope_part"), selected: s == .PART) { vm.setPaintScope(.PART) },
                Seg3(icon: "square.stack.3d.up", label: L("model3d_paint_scope_all"), selected: s == .ALL) { vm.setPaintScope(.ALL) },
            ])
        case .ADD: EmptyView()
        }
    }

    private func guarded(_ ok: Bool, _ need: String, _ action: @escaping () -> Void) -> () -> Void { { ok ? action() : vm.message(need) } }

    private var axisChip: some View {
        let a = vm.axis
        let name = L(a == 0 ? "model3d_axis_x" : a == 1 ? "model3d_axis_y" : a == 2 ? "model3d_axis_z" : "model3d_axis_free")
        return ActionPill3("ruler", L("model3d_axis_cycle", name), selected: a >= 0) { vm.setAxis(a >= 2 ? -1 : a + 1) }
    }

    private func status(_ text: String, _ any: Bool, _ clear: @escaping () -> Void) -> some View {
        HStack(spacing: 2) {
            Text(text).font(.callout).foregroundStyle(any ? Color.accentColor : .secondary).lineLimit(1).fixedSize()
            if any { Button(action: clear) { Image(systemName: "xmark").frame(width: 34, height: 34) }.accessibilityLabel(L("clear_selection")) }
        }
        .padding(.leading, 12).padding(.trailing, any ? 2 : 12).frame(height: 42)
        .background(any ? Color.accentColor.opacity(0.12) : .clear, in: Capsule())
        .accessibilityIdentifier("model3d_edit_status")
    }

    @ViewBuilder private var actions: some View {
        let sel = vm.selection
        switch vm.mode {
        case .SELECT:
            let has = !sel.parts.isEmpty
            let needPart = L("model3d_need_part")
            let grouped = vm.model.map { m in sel.parts.contains { m.part($0)?.group != nil } } ?? false
            axisChip
            ActionPill3("square.stack.3d.up", L("model3d_group"), action: guarded(sel.parts.count >= 2, L("model3d_need_two_parts")) { vm.groupSelected() })
            if grouped { ActionPill3("link.badge.plus", L("model3d_ungroup")) { vm.ungroupSelected() } }
            ActionPill3("plus.square.on.square", L("model3d_duplicate"), action: guarded(has, needPart) { vm.duplicateSelected() })
            ActionPill3("arrow.down.to.line", L("model3d_ground"), action: guarded(has, needPart) { vm.dropSelectedToGround() })
            ActionPill3("circle.lefthalf.filled", L("model3d_panel_ops"), selected: vm.panel == .OPS) { vm.togglePanel(.OPS) }
            ActionPill3("paintpalette", L("model3d_panel_material"), selected: vm.panel == .MATERIAL) { vm.togglePanel(.MATERIAL) }
            ActionPill3("slider.horizontal.3", L("model3d_panel_shape"), selected: vm.panel == .SHAPE) { vm.togglePanel(.SHAPE) }
            ActionPill3("trash", L("model3d_delete"), danger: true, action: guarded(has, needPart) { vm.deleteSelected() })
        case .EDIT: editActions(sel)
        case .DRAW: drawActions
        case .PAINT:
            Button { vm.togglePanel(.MATERIAL) } label: { RoundedRectangle(cornerRadius: 12).fill(Color(argb: vm.paint.color)).frame(width: 38, height: 38) }
                .accessibilityLabel(String(format: "#%06X", UInt32(bitPattern: vm.paint.color) & 0xFFFFFF))
            ActionPill3("paintpalette", L("model3d_panel_material"), selected: vm.panel == .MATERIAL) { vm.togglePanel(.MATERIAL) }
        case .ADD:
            ActionPill3("square.grid.2x2", L("model3d_all_shapes"), selected: vm.panel == .ADD) { vm.togglePanel(.ADD) }
            ForEach([PrimitiveKind.CUBE, .SPHERE, .CYLINDER, .CONE, .PYRAMID, .TORUS], id: \.self) { k in
                ActionPill3("cube", ModelEdit.primitiveName(k).tr) { vm.addPart(k) }
            }
        }
    }

    @ViewBuilder private func editActions(_ sel: Selection3) -> some View {
        switch vm.tool {
        case .POINT:
            let n = sel.vertices.count
            let need = L("model3d_need_points")
            status(n > 0 ? L("model3d_points_selected", n) : L("model3d_none_points"), n > 0) { vm.clearEdgeSelection() }
            Divider().frame(height: 28)
            ActionPill3("point.topleft.down.to.point.bottomright.curvepath", L("model3d_point_connect"), action: guarded(n >= 2, need) { vm.connectSelectedPoints() })
            ActionPill3("scissors", L("model3d_point_cut"), action: guarded(n >= 3, need) { vm.cutSelectedLoop() })
            ActionPill3("trash", L("model3d_point_delete"), danger: true, action: guarded(n >= 1, need) { vm.deleteSelectedPoints() })
        case .EDGE:
            let n = sel.edges.count, has = n > 0
            let need = L("model3d_need_edges")
            status(has ? L("model3d_edges_selected", n) : L("model3d_none_edges"), has || !sel.vertices.isEmpty) { vm.clearEdgeSelection() }
            Divider().frame(height: 28)
            ActionPill3("scribble", L("model3d_edge_bend"), action: guarded(has, need) { vm.startOp(.BEND) })
            ActionPill3("arrow.left.and.right", L("model3d_edge_stretch"), action: guarded(has, need) { vm.startOp(.STRETCH) })
            ActionPill3("arrow.up.and.down", L("model3d_edge_push"), action: guarded(has, need) { vm.startOp(.PUSH) })
            ActionPill3("square.on.circle", L("model3d_edge_round"), action: guarded(has, need) { vm.startOp(.ROUND) })
            ActionPill3("plus.circle", L("model3d_edge_split"), action: guarded(has, need) { vm.splitSelectedEdges() })
            axisChip
        default:
            let n = sel.faces.count, has = n > 0
            let need = L("model3d_need_faces")
            status(has ? L("model3d_faces_selected", n) : L("model3d_none_faces"), has) { vm.clearFaceSelection() }
            ActionPill3("square", L("model3d_face_single"), selected: vm.faceMode == .SINGLE) { vm.setFaceMode(.SINGLE) }
            ActionPill3("square.grid.3x3", L("model3d_face_surface"), selected: vm.faceMode == .SURFACE) { vm.setFaceMode(.SURFACE) }
            Divider().frame(height: 28)
            ActionPill3("plus", L("model3d_extrude"), action: guarded(has, need) { vm.startOp(.EXTRUDE) })
            ActionPill3("square.inset.filled", L("model3d_inset"), action: guarded(has, need) { vm.startOp(.INSET) })
            ActionPill3("arrow.up.left.and.arrow.down.right", L("model3d_face_size"), action: guarded(has, need) { vm.startOp(.FACE_SCALE) })
            ActionPill3("circle.circle", L("model3d_hole"), action: guarded(has, need) { vm.startOp(.HOLE) })
            ActionPill3("square.on.square.dashed", L("model3d_detach"), action: guarded(has, need) { vm.detachFaces() })
            ActionPill3("paintbrush", L("model3d_paint_faces"), action: guarded(has, need) { vm.paintSelection() })
            ActionPill3("trash", L("model3d_delete_faces"), danger: true, action: guarded(has, need) { vm.deleteFaces() })
            axisChip
        }
    }

    @ViewBuilder private var drawActions: some View {
        let drawings = vm.model.map { Model3DEditorModel.hasDrawings($0) } ?? false
        if vm.tool == .ERASE {
            ForEach([(Float(14), "model3d_eraser_small"), (24, "model3d_eraser_medium"), (42, "model3d_eraser_large")], id: \.1) { item in
                ActionPill3("circle", L(item.1), selected: abs(vm.eraserSize - item.0) < 0.5) { vm.setEraserSize(item.0) }
            }
        } else {
            ActionPill3("paintbrush.pointed", L("model3d_brush_kind_chip", Model3DPanels.brushLabel(vm.brush.kind)), selected: vm.panel == .BRUSH) { vm.togglePanel(.BRUSH) }
            let planes: [(DrawPlane, String)] = [(.FRONT, "model3d_plane_front"), (.SIDE, "model3d_plane_side"), (.TOP, "model3d_plane_top"),
                                                 (.VIEW, "model3d_plane_view"), (.FACE, "model3d_plane_face")]
            let i = max(planes.firstIndex { $0.0 == vm.plane } ?? 0, 0)
            ActionPill3("square", L("model3d_plane_cycle", L(planes[i].1))) { vm.setPlane(planes[(i + 1) % planes.count].0) }
            ActionPill3("sparkles", L("model3d_suggest_toggle"), selected: vm.suggestEnabled) { vm.setSuggestEnabled(!vm.suggestEnabled) }
        }
        Divider().frame(height: 28)
        ActionPill3("delete.left", L("model3d_delete_last"), enabled: drawings) { vm.deleteLastDrawing() }
        ActionPill3("trash", L("model3d_clear_all"), enabled: drawings, danger: true, action: onClearAll)
    }
}

/// v24 kaydırıcı işlemi çubuğu: ad, açıklama, miktar, Tamam ve Vazgeç.
private struct OpBar3: View {
    @ObservedObject var vm: Model3DEditorModel
    let op: SliderOp

    var body: some View {
        let (title, hint): (String, String) = {
            switch op.kind {
            case .BEND: return ("model3d_edge_bend", "model3d_op_bend")
            case .STRETCH: return ("model3d_edge_stretch", "model3d_op_stretch")
            case .PUSH: return ("model3d_edge_push", "model3d_op_push")
            case .ROUND: return ("model3d_edge_round", "model3d_op_round")
            case .EXTRUDE: return ("model3d_extrude", "model3d_op_extrude")
            case .INSET: return ("model3d_inset", "model3d_op_inset")
            case .FACE_SCALE: return ("model3d_face_size", "model3d_op_face_scale")
            case .HOLE: return ("model3d_hole", "model3d_op_hole")
            }
        }()
        let value: String = {
            switch op.kind {
            case .STRETCH, .FACE_SCALE: return String(format: "×%.2f", op.amount)
            case .HOLE: return "%\(Int(op.amount * 100))"
            default: return String(format: "%+.2f", op.amount)
            }
        }()
        VStack(alignment: .leading, spacing: 6) {
            HStack { Text(L(title)).font(.subheadline.weight(.semibold)).foregroundStyle(Color.accentColor); Spacer(); Text(value).font(.callout.monospacedDigit()) }
            Text(L(op.failed ? "model3d_op_failed" : hint)).font(.caption).foregroundStyle(op.failed ? .red : .secondary)
            Slider(value: Binding(get: { Double(op.amount) }, set: { vm.setOpAmount(Float($0)) }), in: Double(op.kind.min)...Double(op.kind.max))
                .accessibilityIdentifier("model3d_op_slider")
            HStack(spacing: 8) {
                Button(L("model3d_op_cancel")) { vm.cancelOp() }.buttonStyle(.bordered).frame(maxWidth: .infinity)
                Button(L("model3d_op_ok")) { vm.applyOp() }.buttonStyle(.borderedProminent).frame(maxWidth: .infinity).disabled(op.failed)
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 8)
        .background(Color(uiColor: .systemBackground), in: RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, 10).padding(.vertical, 4)
        .accessibilityIdentifier("model3d_op")
    }
}

/// Panel çerçevesi: başlık ve kapatma düğmesi; içerik kendi içinde kayar.
private struct PanelFrame3: View {
    @ObservedObject var vm: Model3DEditorModel
    let panel: Panel3

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text(L(Self.title(panel))).font(.subheadline.weight(.semibold)).foregroundStyle(Color.accentColor)
                Spacer()
                Button { vm.closePanel() } label: { Image(systemName: "xmark").frame(width: 34, height: 34) }
                    .accessibilityLabel(L("model3d_close_panel")).accessibilityIdentifier("model3d_panel_close")
            }
            .padding(.leading, 16).padding(.trailing, 8).padding(.top, 8)
            ScrollView { Model3DPanels(vm: vm, panel: panel).padding(14) }
        }
        .background(Color(uiColor: .systemBackground), in: RoundedRectangle(cornerRadius: 18))
        .shadow(color: .black.opacity(0.08), radius: 6, y: 2)
        .accessibilityIdentifier("model3d_panel")
    }

    static func title(_ p: Panel3) -> String {
        switch p {
        case .ADD: return "model3d_panel_add"
        case .PARTS: return "model3d_panel_parts"
        case .MATERIAL: return "model3d_panel_material"
        case .SHAPE: return "model3d_panel_shape"
        case .SETTINGS: return "model3d_panel_settings"
        case .BRUSH: return "model3d_panel_brush"
        case .OPS: return "model3d_panel_ops"
        }
    }
}
