import SwiftUI

/// v56 aşama 11 **3B Çizim** ekranı (Android `Sketch3DScreen`): üst çubuk (geri, ad, geri al/yinele, katmanlar,
/// özellikler, kaydet, menü), sahne (Metal ya da yazılım çizici, üstte düzlem ve işaretler), moda göre araç satırı ve
/// alt mod çubuğu (Çiz · Düzlem · Seç · Ekle · Görünüm).
struct Sketch3DView: View {
    @StateObject private var vm: Sketch3DModel
    @Environment(\.dismiss) private var dismiss
    @State private var panel: Panel?
    @State private var renaming = false
    @State private var savingAs = false
    @State private var nameText = ""
    @State private var exportOpen = false
    @State private var exitAsk = false
    @State private var toast: String?
    @State private var videoOpen = false
    @State private var shareURL: URL?
    var onBack: (() -> Void)?
    /// v56 aşama 12 tuvalden açıldıysa "Tuvale ekle": PNG tuvale verilir, ekran kapanır.
    var onAddToCanvas: ((Data) -> Void)?

    enum Panel: String, Identifiable { case layers, props; var id: String { rawValue } }

    init(repository: Model3DRepository?, modelId: String?, initialName: String? = nil, onBack: (() -> Void)? = nil, onAddToCanvas: ((Data) -> Void)? = nil) {
        _vm = StateObject(wrappedValue: Sketch3DModel(repository: repository, modelId: modelId, initialName: initialName))
        self.onBack = onBack
        self.onAddToCanvas = onAddToCanvas
    }

    /// Testler ve önizleme için hazır modelle.
    init(model: Sketch3DModel, onBack: (() -> Void)? = nil) {
        _vm = StateObject(wrappedValue: model)
        self.onBack = onBack
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ZStack(alignment: .top) {
                if vm.model != nil {
                    Sketch3DViewport(vm: vm)
                } else {
                    ProgressView(L("loading_model")).frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                if vm.mode == .PLANE || vm.mode == .DRAW, vm.model != nil, vm.planeTilted() {
                    Text(L("sk3_plane_tilted"))
                        .font(.footnote.weight(.semibold)).padding(.horizontal, 12).padding(.vertical, 6)
                        .background(Color.orange.opacity(0.9), in: Capsule()).foregroundStyle(.white)
                        .padding(.top, 8)
                        .accessibilityIdentifier("sketch3d_tilt_warning")
                }
                if let t = toast {
                    Text(t).font(.footnote).padding(.horizontal, 14).padding(.vertical, 8)
                        .background(.ultraThinMaterial, in: Capsule()).padding(.top, 44)
                        .transition(.opacity)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            toolRow
            modeBar
        }
        .background(Color(uiColor: .systemBackground))
        .navigationBarHidden(true)
        .accessibilityIdentifier("sketch3d_screen")
        .sheet(item: $panel) { p in
            NavigationStack {
                ScrollView {
                    if p == .layers { LayersPanel3(vm: vm) } else { PropsPanel3(vm: vm) }
                }
                .navigationTitle(L(p == .layers ? "sk3_layers" : "sk3_props"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("close")) { panel = nil } } }
            }
            .presentationDetentsCompat()
            .accessibilityIdentifier("sketch3d_panel")
        }
        .sheet(isPresented: $exportOpen) { ImageExportSheet3(vm: vm) }
        .sheet(isPresented: $videoOpen) { VideoSheet3(vm: vm) }
        .sheet(item: Binding(get: { shareURL.map { ShareItem3(url: $0) } }, set: { shareURL = $0?.url })) { item in ShareSheet(items: [item.url]) }
        .alert(L("model3d_rename"), isPresented: $renaming) {
            TextField(L("model3d_name"), text: $nameText)
            Button(L("cancel_button"), role: .cancel) {}
            Button(L("model3d_rename")) { vm.rename(nameText) }
        }
        .alert(L("model3d_save_as"), isPresented: $savingAs) {
            TextField(L("model3d_name"), text: $nameText)
            Button(L("cancel_button"), role: .cancel) {}
            Button(L("model3d_save")) { vm.saveAs(nameText) }
        }
        .alert(L("model3d_exit_title"), isPresented: $exitAsk) {
            Button(L("model3d_save")) { vm.save(exitAfter: true) }
            Button(L("model3d_discard"), role: .destructive) { leave() }
            Button(L("cancel_button"), role: .cancel) {}
        } message: { Text(L("model3d_exit_text")) }
        .onChange(of: vm.event) { e in
            guard let e else { return }
            vm.event = nil
            switch e {
            case .saved(let name): show(L("model3d_saved", name))
            case .exit: leave()
            case .message(let m): show(m)
            case .videoSaved(let url):
                videoOpen = false
                OrbitVideo.save(url) { ok in Task { @MainActor in if ok { show(L("sk3_video_saved")) } } }
                shareURL = url
            }
        }
    }

    private func show(_ text: String) {
        withAnimation { toast = text }
        let t = text
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            if toast == t { withAnimation { toast = nil } }
        }
    }

    private func leave() { if let onBack { onBack() } else { dismiss() } }

    // MARK: üst çubuk

    private var topBar: some View {
        HStack(spacing: 4) {
            Button { vm.dirty ? (exitAsk = true) : leave() } label: { Image(systemName: "chevron.backward").frame(width: 40, height: 40) }
                .accessibilityLabel(L("back"))
            Text(vm.model?.name ?? L("sk3_title")).font(.headline).lineLimit(1)
                .onTapGesture { nameText = vm.model?.name ?? ""; renaming = true }
            Spacer()
            Button { vm.undo() } label: { Image(systemName: "arrow.uturn.backward").frame(width: 38, height: 40) }
                .disabled(!vm.canUndo).accessibilityLabel(L("undo")).accessibilityIdentifier("sketch3d_undo")
            Button { vm.redo() } label: { Image(systemName: "arrow.uturn.forward").frame(width: 38, height: 40) }
                .disabled(!vm.canRedo).accessibilityLabel(L("redo")).accessibilityIdentifier("sketch3d_redo")
            Button { panel = .layers } label: { Image(systemName: "square.3.layers.3d").frame(width: 38, height: 40) }
                .accessibilityLabel(L("sk3_layers")).accessibilityIdentifier("sketch3d_layers")
            Button { panel = .props } label: { Image(systemName: "slider.horizontal.3").frame(width: 38, height: 40) }
                .accessibilityLabel(L("sk3_props")).accessibilityIdentifier("sketch3d_props")
            Button { vm.save() } label: { Image(systemName: "square.and.arrow.down").frame(width: 38, height: 40) }
                .accessibilityLabel(L("model3d_save")).accessibilityIdentifier("sketch3d_save")
            Menu {
                Button(L("model3d_rename")) { nameText = vm.model?.name ?? ""; renaming = true }
                Button(L("model3d_save_as")) { nameText = vm.model?.name ?? ""; savingAs = true }
                Button(L("model3d_fit")) { vm.fitView() }
                Button(L("model3d_export_image")) { exportOpen = true }.accessibilityIdentifier("sketch3d_export_image")
                if let add = onAddToCanvas {
                    Button(L("sk3_add_to_canvas")) {
                        Task { if let png = await vm.canvasImage() { if vm.dirty { vm.save() }; add(png) } }
                    }.accessibilityIdentifier("sketch3d_add_to_canvas")
                }
                Button(L("sk3_video")) { videoOpen = true }.accessibilityIdentifier("sketch3d_video")
            } label: { Image(systemName: "ellipsis.circle").frame(width: 38, height: 40) }
                .accessibilityLabel(L("more_options"))
        }
        .padding(.horizontal, 6)
        .frame(height: 48)
    }

    // MARK: araç satırı

    @ViewBuilder private var toolRow: some View {
        ChipRow3 {
            switch vm.mode {
            case .DRAW:
                tool(L("sk3_pen"), "pencil", vm.tool == .PEN && vm.brush == .PEN, "sketch3d_brush_pen") { vm.setBrush(.PEN) }
                tool(L("sk3_ink"), "pencil.tip", vm.tool == .PEN && vm.brush == .INK, "sketch3d_brush_ink") { vm.setBrush(.INK) }
                tool(L("sk3_marker"), "highlighter", vm.tool == .PEN && vm.brush == .MARKER, "sketch3d_brush_marker") { vm.setBrush(.MARKER) }
                tool(L("sk3_wide"), "paintbrush.pointed", vm.tool == .PEN && vm.brush == .WIDE, "sketch3d_brush_wide") { vm.setBrush(.WIDE) }
                tool(L("sk3_fill"), "drop.fill", vm.tool == .FILL, "sketch3d_fill") { vm.setTool(.FILL) }
                tool(L("sk3_eraser"), "eraser", vm.tool == .ERASER, "sketch3d_eraser") { vm.setTool(.ERASER) }
                ColorRow3(selected: vm.color) { vm.setColor($0) }.frame(maxWidth: 260)
            case .PLANE:
                tool(L("sk3_plane_front"), "square", vm.plane.id == "front", "sketch3d_plane_front") { vm.setPlanePreset(.FRONT) }
                tool(L("sk3_plane_side"), "square.righthalf.filled", vm.plane.id == "side", "sketch3d_plane_side") { vm.setPlanePreset(.SIDE) }
                tool(L("sk3_plane_top"), "square.bottomhalf.filled", vm.plane.id == "top", "sketch3d_plane_top") { vm.setPlanePreset(.TOP) }
                tool(L("sk3_plane_camera"), "camera", vm.plane.id == "camera", "sketch3d_plane_camera") { vm.planeToCamera() }
                tool(L("sk3_tilt_fwd"), "rotate.3d", false, "sketch3d_plane_tilt") { vm.rotatePlane(0, PlaneSnap.STEP_DEG) }
                tool(L("sk3_turn_side"), "arrow.left.and.right.righttriangle.left.righttriangle.right", false, "sketch3d_plane_turn") { vm.rotatePlane(1, PlaneSnap.STEP_DEG) }
                tool(L("sk3_spin"), "arrow.clockwise", false, "sketch3d_plane_spin") { vm.rotatePlane(2, PlaneSnap.STEP_DEG) }
            case .SELECT:
                tool(L("sk3_xf_move"), "arrow.up.and.down.and.arrow.left.and.right", vm.xform == .MOVE, "sketch3d_xf_move") { vm.setXform(.MOVE) }
                tool(L("sk3_xf_rotate"), "rotate.right", vm.xform == .ROTATE, "sketch3d_xf_rotate") { vm.setXform(.ROTATE) }
                tool(L("sk3_xf_scale"), "arrow.up.left.and.arrow.down.right", vm.xform == .SCALE, "sketch3d_xf_scale") { vm.setXform(.SCALE) }
                if vm.xform == .ROTATE {
                    ForEach(0..<3, id: \.self) { a in Chip3(["X", "Y", "Z"][a], vm.axis == a) { vm.setAxis(a) } }
                }
                tool(L("sk3_select_all"), "checkmark.circle", false, "sketch3d_select_all") { vm.selectAll() }
                tool(L("sk3_duplicate"), "plus.square.on.square", false, "sketch3d_duplicate") { vm.duplicateSelected() }
                tool(L("sk3_flip"), "arrow.left.and.right.square", false, "sketch3d_flip") { vm.flipSelected() }
                let selParts = vm.model?.parts.filter { vm.selection.contains($0.id) } ?? []
                let glowing = !selParts.isEmpty && selParts.allSatisfy { $0.material.kind == .GLOW }
                tool(L("sk3_glow"), "sparkles", glowing, "sketch3d_glow") { vm.materialSelected(glowing ? .PLASTIC : .GLOW) }
                tool(L("sk3_ground"), "arrow.down.to.line", false, "sketch3d_ground") { vm.dropSelectedToGround() }
                tool(L("sk3_delete"), "trash", false, "sketch3d_delete") { vm.deleteSelected() }
            case .ADD:
                ForEach([(PrimitiveKind.CUBE, "sk3_cube", "cube", "cube"), (.CYLINDER, "sk3_cylinder", "cylinder", "cylinder"),
                         (.SPHERE, "sk3_sphere", "circle", "sphere"), (.CONE, "sk3_cone", "cone", "cone"), (.PLANE, "sk3_slab", "square", "slab")], id: \.1) { item in
                    tool(L(item.1), item.2, false, "sketch3d_add_\(item.3)") { vm.addPrimitive(item.0) }
                }
            case .VIEW:
                ForEach([(RenderStyle.SOFT, "sk3_style_soft"), (RenderStyle.TOON, "sk3_style_toon"), (RenderStyle.LINES, "sk3_style_lines")], id: \.0) { item in
                    Chip3(L(item.1), vm.style == item.0) { vm.setStyle(item.0) }.accessibilityIdentifier("sketch3d_style_\(item.0)")
                }
                tool(L("sk3_iso"), "cube.transparent", false, "sketch3d_view_iso") { vm.presetView(0) }
                tool(L("sk3_plane_front"), "square", false, "sketch3d_view_front") { vm.presetView(1) }
                tool(L("sk3_plane_side"), "square.righthalf.filled", false, "sketch3d_view_side") { vm.presetView(2) }
                tool(L("sk3_plane_top"), "square.bottomhalf.filled", false, "sketch3d_view_top") { vm.presetView(3) }
                tool(L("model3d_fit"), "viewfinder", false, "sketch3d_view_fit") { vm.fitView() }
                tool(L("sk3_perspective"), "perspective", vm.camera.persp > 0.01, "sketch3d_view_persp") { vm.setPerspective(vm.camera.persp > 0.01 ? 0 : 0.35) }
            }
        }
        .padding(.horizontal, 10)
        .frame(height: 50)
    }

    private func tool(_ label: String, _ icon: String, _ selected: Bool, _ id: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(label, systemImage: icon).font(.footnote).lineLimit(1).fixedSize()
                .padding(.horizontal, 12).frame(height: 36)
                .foregroundStyle(selected ? Color.accentColor : .primary)
                .background(selected ? Color.accentColor.opacity(0.15) : Color(uiColor: .secondarySystemBackground), in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    // MARK: mod çubuğu

    private var modeBar: some View {
        HStack(spacing: 0) {
            ForEach([(SketchMode.DRAW, "sk3_mode_draw", "pencil.and.outline"), (.PLANE, "sk3_mode_plane", "square.grid.3x3"),
                     (.SELECT, "sk3_mode_select", "cursorarrow.rays"), (.ADD, "sk3_mode_add", "plus.square"), (.VIEW, "sk3_mode_view", "eye")], id: \.1) { item in
                let on = vm.mode == item.0
                Button { vm.setMode(item.0) } label: {
                    VStack(spacing: 2) {
                        Image(systemName: item.2).font(.system(size: 18))
                        Text(L(item.1)).font(.caption2).lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .foregroundStyle(on ? Color.accentColor : .secondary)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("sketch3d_mode_\(item.0.rawValue.lowercased())")
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .background(Color(uiColor: .secondarySystemBackground))
        .accessibilityIdentifier("sketch3d_modes")
    }
}

// MARK: - paneller

/// Katmanlar: etkin katman, görünür/kilitli, ad, sıra, silme, seçimi taşıma.
struct LayersPanel3: View {
    @ObservedObject var vm: Sketch3DModel
    @State private var renaming: Layer3?
    @State private var nameText = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(vm.model?.layers ?? [], id: \.id) { l in
                HStack(spacing: 8) {
                    Image(systemName: vm.activeLayer == l.id ? "largecircle.fill.circle" : "circle")
                        .foregroundStyle(Color.accentColor)
                    Text(l.name).lineLimit(1)
                    Spacer()
                    Button { vm.toggleLayerVisible(l.id) } label: { Image(systemName: l.visible ? "eye" : "eye.slash") }
                        .accessibilityLabel(L("sk3_layer_hidden"))
                    Button { vm.toggleLayerLocked(l.id) } label: { Image(systemName: l.locked ? "lock" : "lock.open") }
                        .accessibilityLabel(L("sk3_layer_locked"))
                    Menu {
                        Button(L("sk3_rename_layer")) { nameText = l.name; renaming = l }
                        Button(L("sk3_layer_to")) { vm.moveSelectionToLayer(l.id) }.disabled(vm.selection.isEmpty)
                        Button("↑") { vm.moveLayer(l.id, -1) }
                        Button("↓") { vm.moveLayer(l.id, 1) }
                        Button(L("sk3_delete_layer"), role: .destructive) { vm.deleteLayer(l.id) }.disabled((vm.model?.layers.count ?? 0) <= 1)
                    } label: { Image(systemName: "ellipsis") .frame(width: 30, height: 30) }
                }
                .padding(10)
                .background(vm.activeLayer == l.id ? Color.accentColor.opacity(0.1) : Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
                .contentShape(Rectangle())
                .onTapGesture { vm.setActiveLayer(l.id) }
                .accessibilityIdentifier("sketch3d_layer_\(l.id)")
            }
            Button { vm.addLayer() } label: { Label(L("sk3_add_layer"), systemImage: "plus") }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("sketch3d_add_layer")
        }
        .padding(16)
        .alert(L("sk3_rename_layer"), isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
            TextField(L("model3d_name"), text: $nameText)
            Button(L("cancel_button"), role: .cancel) { renaming = nil }
            Button(L("model3d_rename")) { if let l = renaming { vm.renameLayer(l.id, nameText) }; renaming = nil }
        }
    }
}

/// Özellikler: renk, kalınlık, örtücülük, silgi boyu, parmakla çiz, ışık ve gölge.
struct PropsPanel3: View {
    @ObservedObject var vm: Sketch3DModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Section3(title: L("sk3_color")) { ColorRow3(selected: vm.color) { vm.setColor($0); vm.colorSelected($0) } }
            Section3(title: L("sk3_mode_draw")) {
                ValueSlider3(L("sk3_size"), "\(Int(vm.size))", vm.size, 1...40) { vm.setSize($0) }
                ValueSlider3(L("sk3_opacity"), "\(Int((vm.opacity * 100).rounded()))%", vm.opacity, 0.05...1) { vm.setOpacity($0) }
                ValueSlider3(L("sk3_eraser_size"), "\(Int(vm.eraserSize))", vm.eraserSize, 6...60) { vm.setEraserSize($0) }
                Toggle(L("sk3_finger"), isOn: Binding(get: { vm.fingerDraw }, set: { vm.setFingerDraw($0) }))
                    .accessibilityIdentifier("sketch3d_finger")
                Text(L("sk3_finger_hint")).font(.caption).foregroundStyle(.secondary)
                Text(L("sk3_hold_hint")).font(.caption).foregroundStyle(.secondary)
                Button(L("sk3_clear"), role: .destructive) { vm.clearDrawings() }
            }
            if let scene = vm.model?.scene {
                Section3(title: L("sk3_light_dir")) {
                    ValueSlider3(L("sk3_light_dir"), "\(Int(scene.light.yaw))°", scene.light.yaw, 0...360) { v in var s = scene; s.light.yaw = v; vm.setScene(s) }
                    ValueSlider3(L("sk3_light_height"), "\(Int(scene.light.pitch))°", scene.light.pitch, 5...90) { v in var s = scene; s.light.pitch = v; vm.setScene(s) }
                    ValueSlider3(L("sk3_ambient"), "\(Int((scene.light.ambient * 100).rounded()))%", scene.light.ambient, 0...1) { v in var s = scene; s.light.ambient = v; vm.setScene(s) }
                    Toggle(L("sk3_shadow"), isOn: Binding(get: { scene.shadow }, set: { var s = scene; s.shadow = $0; vm.setScene(s) }))
                    Toggle(L("sk3_grid"), isOn: Binding(get: { scene.ground }, set: { var s = scene; s.ground = $0; vm.setScene(s) }))
                }
            }
        }
        .padding(16)
    }
}

extension View {
    /// iOS 16+: alt sayfa orta ve tam yükseklikte açılır.
    func presentationDetentsCompat() -> some View { presentationDetents([.medium, .large]) }
}

private struct ShareItem3: Identifiable { let url: URL; var id: String { url.path } }

/// v56 aşama 13 dönen video: biçim (MP4 1080p / GIF), hız ve ilerleme (Android `VideoDialog`).
struct VideoSheet3: View {
    @ObservedObject var vm: Sketch3DModel
    @Environment(\.dismiss) private var dismiss
    @State private var gif = false
    @State private var speed: Float = 1

    var body: some View {
        NavigationStack {
            Form {
                Text(L("sk3_video_hint")).font(.callout)
                Section(L("sk3_video_format")) {
                    Picker(L("sk3_video_format"), selection: $gif) {
                        Text("MP4 1080p").tag(false)
                        Text("GIF").tag(true)
                    }.pickerStyle(.segmented).accessibilityIdentifier("sketch3d_video_format")
                }
                Section(L("sk3_video_speed")) {
                    Picker(L("sk3_video_speed"), selection: $speed) {
                        Text("0.5×").tag(Float(0.5))
                        Text("1×").tag(Float(1))
                        Text("2×").tag(Float(2))
                    }.pickerStyle(.segmented).accessibilityIdentifier("sketch3d_video_speed")
                }
                if let p = vm.videoProgress {
                    ProgressView(value: Double(p)).accessibilityIdentifier("sketch3d_video_progress")
                }
            }
            .disabled(vm.videoProgress != nil)
            .navigationTitle(L("sk3_video"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("cancel_button")) { dismiss() }.disabled(vm.videoProgress != nil) }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("sk3_video_start")) {
                        vm.exportVideo(gif: gif, speed: speed, outDir: FileManager.default.temporaryDirectory.appendingPathComponent("sketch3d-video"))
                    }.disabled(vm.videoProgress != nil).accessibilityIdentifier("sketch3d_video_start")
                }
            }
        }
        .accessibilityIdentifier("sketch3d_video_sheet")
    }
}
