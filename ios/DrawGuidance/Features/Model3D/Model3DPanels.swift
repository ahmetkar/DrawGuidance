import SwiftUI
import UniformTypeIdentifiers

/// 3B renk paleti.
private let Palette3D: [Int32] = [
    0xFFFFFFFF, 0xFFECEFF1, 0xFF9E9E9E, 0xFF37474F, 0xFF212121, 0xFFE53935, 0xFFE07A5F, 0xFFF2CC8F, 0xFFFFD54F, 0xFF81B29A,
    0xFF43A047, 0xFF6EC1E4, 0xFF4F8BD6, 0xFF3949AB, 0xFF9C6ADE, 0xFFE5989B, 0xFF8D6E63, 0xFFB98B55,
].map { Int32(bitPattern: UInt32($0)) }

/// Panellerin içeriği (Android `Model3DPanels`).
struct Model3DPanels: View {
    @ObservedObject var vm: Model3DEditorModel
    let panel: Panel3

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            switch panel {
            case .ADD: AddPanel3(vm: vm)
            case .PARTS: PartsPanel3(vm: vm)
            case .MATERIAL: MaterialPanel3(vm: vm)
            case .SHAPE: ShapePanel3(vm: vm)
            case .SETTINGS: SettingsPanel3(vm: vm)
            case .BRUSH: BrushPanel3(vm: vm)
            case .OPS: OpsPanel3(vm: vm)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityIdentifier("model3d_panel_\(panel.rawValue.lowercased())")
    }

    static func brushLabel(_ k: Brush3Kind) -> String {
        switch k {
        case .LINE: return L("model3d_brush_line")
        case .TUBE: return L("model3d_brush_tube")
        case .RIBBON: return L("model3d_brush_ribbon")
        case .WALL: return L("model3d_brush_wall")
        case .LATHE: return L("model3d_brush_lathe")
        case .INFLATE: return L("model3d_brush_inflate")
        }
    }

    static func brushHint(_ k: Brush3Kind) -> String {
        switch k {
        case .LINE: return L("model3d_brush_line_hint")
        case .TUBE: return L("model3d_brush_tube_hint")
        case .RIBBON: return L("model3d_brush_ribbon_hint")
        case .WALL: return L("model3d_brush_wall_hint")
        case .LATHE: return L("model3d_brush_lathe_hint")
        case .INFLATE: return L("model3d_brush_inflate_hint")
        }
    }
}

// MARK: ortak parçalar

struct ColorRow3: View {
    let selected: Int32?
    var extra: [Int32] = []
    let onPick: (Int32) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array((extra + Palette3D).enumerated()), id: \.offset) { _, c in
                    let chosen = selected.map { ($0 | Int32(bitPattern: 0xFF00_0000)) == (c | Int32(bitPattern: 0xFF00_0000)) } ?? false
                    Button { onPick(c) } label: {
                        Circle().fill(Color(argb: c)).frame(width: 22, height: 22).padding(4)
                            .overlay(Circle().stroke(chosen ? Color.accentColor : Color.secondary.opacity(0.4), lineWidth: chosen ? 3 : 1))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(String(format: "#%06X", UInt32(bitPattern: c) & 0xFFFFFF))
                    .accessibilityAddTraits(chosen ? .isSelected : [])
                }
            }
        }
    }
}

struct Chip3: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    init(_ label: String, _ selected: Bool, action: @escaping () -> Void) { self.label = label; self.selected = selected; self.action = action }
    var body: some View {
        Button(action: action) {
            Text(label).font(.footnote).lineLimit(1).fixedSize()
                .padding(.horizontal, 12).frame(height: 32)
                .foregroundStyle(selected ? Color.accentColor : .primary)
                .background(selected ? Color.accentColor.opacity(0.15) : Color(uiColor: .secondarySystemBackground), in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

struct ChipRow3<Content: View>: View {
    @ViewBuilder let content: () -> Content
    var body: some View { ScrollView(.horizontal, showsIndicators: false) { HStack(spacing: 8) { content() } } }
}

struct ValueSlider3: View {
    let label: String, value: String
    let current: Float
    let range: ClosedRange<Float>
    let onChange: (Float) -> Void

    init(_ label: String, _ value: String, _ current: Float, _ range: ClosedRange<Float>, _ onChange: @escaping (Float) -> Void) {
        self.label = label; self.value = value; self.current = current; self.range = range; self.onChange = onChange
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack { Text(label).font(.footnote); Spacer(); Text(value).font(.footnote.monospacedDigit()).foregroundStyle(.secondary) }
            Slider(value: Binding(get: { Double(current) }, set: { onChange(Float($0)) }), in: Double(range.lowerBound)...Double(range.upperBound))
                .accessibilityLabel(label)
        }
    }
}

struct Section3<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(Color.accentColor)
            content()
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.accentColor.opacity(0.05), in: RoundedRectangle(cornerRadius: 14))
    }
}

private func pct(_ v: Float) -> String { "\(Int((v * 100).rounded()))%" }
private func hint(_ s: String) -> some View { Text(s).font(.caption).foregroundStyle(.secondary) }

// MARK: Ekle

/// Hazır cisimlerin küçük resimleri (bir kez çizilir).
enum PrimitiveThumbs {
    private static let lock = NSLock()
    private static var cache: [PrimitiveKind: CGImage] = [:]

    static func get(_ kind: PrimitiveKind) -> CGImage {
        lock.lock(); if let c = cache[kind] { lock.unlock(); return c }; lock.unlock()
        let part = ModelEdit.newPart(kind, "t", 0)
        var scene = SceneSettings(); scene.shadow = false; scene.ground = false; scene.edges = true
        var cam = Camera3(); cam.yaw = 35; cam.pitch = 24
        let m = Model3D("t", "t", [part], scene: scene, camera: cam)
        let f = Renderer3D.render(m, View3.fit(m, m.camera, 112, 112, 0.12), RenderOptions(supersample: 2, ground: false, shadow: false, background: 0))
        let img = Bitmaps.image(argb: f.color, 112, 112)
        lock.lock(); cache[kind] = img; lock.unlock()
        return img
    }
}

/// Arka planda üretilip gösterilen küçük resim.
struct AsyncThumb: View {
    let key: String
    let make: @Sendable () -> CGImage?
    @State private var image: CGImage?
    var body: some View {
        ZStack {
            if let image { Image(decorative: image, scale: 1).resizable().scaledToFit() } else { Image(systemName: "cube").foregroundStyle(.secondary.opacity(0.4)) }
        }
        .task(id: key) { image = await Task.detached(priority: .utility) { make() }.value }
    }
}

private struct PendingGlb: Identifiable { let id = UUID(); let name: String; let bytes: [UInt8] }

private struct AddPanel3: View {
    @ObservedObject var vm: Model3DEditorModel
    @State private var picking = false
    @State private var pending: PendingGlb?

    var body: some View {
        Button { picking = true } label: { Label(L("glb_import_button"), systemImage: "square.and.arrow.down").frame(maxWidth: .infinity) }
            .buttonStyle(.bordered)
            .accessibilityIdentifier("model3d_import_glb")
            .fileImporter(isPresented: $picking, allowedContentTypes: [.glb, .gltf, .data]) { r in
                switch r {
                case .success(let url):
                    let ok = url.startAccessingSecurityScopedResource()
                    defer { if ok { url.stopAccessingSecurityScopedResource() } }
                    do { pending = PendingGlb(name: url.deletingPathExtension().lastPathComponent, bytes: [UInt8](try Data(contentsOf: url))) } catch {
                        vm.message(L("glb_import_failed", error.localizedDescription))
                    }
                case .failure(let e): vm.message(L("glb_import_failed", e.localizedDescription))
                }
            }
            .sheet(item: $pending) { p in
                GlbImportSheet(name: p.name, bytes: p.bytes.count, askSave: true) { tris, save in
                    pending = nil
                    vm.importGlbParts(p.bytes, p.name, tris, save)
                } onDismiss: { pending = nil }
            }
        Text(L("model3d_primitives")).font(.subheadline.weight(.semibold))
        hint(L("model3d_add_hint"))
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
            ForEach(PrimitiveKind.allCases, id: \.self) { kind in
                let name = ModelEdit.primitiveName(kind).tr
                Button { vm.addPart(kind) } label: {
                    VStack(spacing: 2) {
                        AsyncThumb(key: "prim-\(kind.rawValue)") { PrimitiveThumbs.get(kind) }.frame(width: 56, height: 56)
                        Text(name).font(.caption2).lineLimit(1)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel(name)
            }
        }
        Text(L("model3d_ready_shapes")).font(.subheadline.weight(.semibold))
        hint(L("model3d_ready_hint"))
        CatalogGrid3 { vm.addCatalog($0.id) }
    }
}

/// v24/v27 GLB içe aktarma: ayrıntı düzeyi ve (düzenleyicide) ayrıca kaydetme sorusu.
struct GlbImportSheet: View {
    let name: String
    let bytes: Int
    let askSave: Bool
    let onImport: (Int, Bool) -> Void
    let onDismiss: () -> Void
    @State private var detail = Budget3D.importDetail

    var body: some View {
        let size = bytes >= 1_048_576 ? String(format: "%.1f MB", Double(bytes) / 1_048_576) : "\(bytes / 1024) KB"
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                Text(L("glb_import_file", name, size))
                Text(L("glb_import_detail")).font(.subheadline.weight(.semibold))
                HStack(spacing: 8) {
                    ForEach(0..<3, id: \.self) { i in
                        let label = L(["model3d_detail_low", "model3d_detail_mid", "model3d_detail_high"][i])
                        Chip3(label.components(separatedBy: " (").first ?? label, detail == i) { detail = i }
                    }
                }
                if askSave {
                    Text(L("glb_import_save_question")).foregroundStyle(Color.accentColor)
                    Button(L("glb_import_save_yes")) { onImport(Model3DRepository.DETAIL_LEVELS[detail], true) }.buttonStyle(.borderedProminent)
                    Button(L("glb_import_save_no")) { onImport(Model3DRepository.DETAIL_LEVELS[detail], false) }.buttonStyle(.bordered)
                } else {
                    Button(L("model3d_import")) { onImport(Model3DRepository.DETAIL_LEVELS[detail], false) }.buttonStyle(.borderedProminent)
                }
                Spacer()
            }
            .padding(20)
            .navigationTitle(L("model3d_import_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("cancel_button"), action: onDismiss) } }
        }
        .presentationDetents([.medium])
    }
}

// MARK: Parçalar

private struct PartsPanel3: View {
    @ObservedObject var vm: Model3DEditorModel
    @State private var renaming: String?
    @State private var renamingGroup: String?
    @State private var collapsed: Set<String> = []
    @State private var text = ""

    var body: some View {
        if let m = vm.model {
            let sel = vm.selection
            hint(L("model3d_parts_hint"))
            let grouped = sel.parts.contains { m.part($0)?.group != nil }
            HStack(spacing: 8) {
                Button(L("model3d_group")) { vm.groupSelected() }.buttonStyle(.bordered).disabled(sel.parts.count < 2).frame(maxWidth: .infinity)
                Button(L("model3d_ungroup")) { vm.ungroupSelected() }.buttonStyle(.bordered).disabled(!grouped).frame(maxWidth: .infinity)
            }
            HStack(spacing: 8) {
                Button(L("model3d_merge")) { vm.mergeSelected() }.buttonStyle(.bordered).disabled(sel.parts.count < 2).frame(maxWidth: .infinity)
                Button(L("model3d_duplicate")) { vm.duplicateSelected() }.buttonStyle(.bordered).disabled(sel.parts.isEmpty).frame(maxWidth: .infinity)
            }
            let groupsById = Dictionary(m.groups.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            let reversed = Array(m.parts.reversed())
            let membersOf = Dictionary(grouping: reversed.filter { $0.group != nil }, by: { $0.group! })
            let rows = Self.rows(reversed, groupsById)
            LazyVStack(alignment: .leading, spacing: 2) {
                ForEach(rows, id: \.id) { row in
                    switch row {
                    case .part(let p): partRow(p, sel, indent: false)
                    case .group(let g):
                        let members = membersOf[g.id] ?? []
                        groupRow(g, members, sel)
                        if !collapsed.contains(g.id) { ForEach(members, id: \.id) { partRow($0, sel, indent: true) } }
                    }
                }
            }
            .alert(L("model3d_rename"), isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
                TextField(L("model3d_name"), text: $text)
                Button("OK") { if let id = renaming { vm.renamePart(id, text) }; renaming = nil }
                Button(L("cancel_button"), role: .cancel) { renaming = nil }
            }
            .alert(L("model3d_group_rename"), isPresented: Binding(get: { renamingGroup != nil }, set: { if !$0 { renamingGroup = nil } })) {
                TextField(L("model3d_name"), text: $text)
                Button("OK") { if let id = renamingGroup { vm.renameGroup(id, text) }; renamingGroup = nil }
                Button(L("cancel_button"), role: .cancel) { renamingGroup = nil }
            }
        }
    }

    private enum Row { case part(Part), group(Group3)
        var id: String { switch self { case .part(let p): return "p-" + p.id; case .group(let g): return "g-" + g.id } }
    }

    private static func rows(_ parts: [Part], _ groups: [String: Group3]) -> [Row] {
        var shown = Set<String>()
        var out: [Row] = []
        for p in parts {
            if let gid = p.group, let g = groups[gid] {
                if shown.insert(gid).inserted { out.append(.group(g)) }
            } else { out.append(.part(p)) }
        }
        return out
    }

    private func groupRow(_ g: Group3, _ members: [Part], _ sel: Selection3) -> some View {
        let open = !collapsed.contains(g.id)
        let all = members.allSatisfy { sel.parts.contains($0.id) }
        let anyVisible = members.contains { $0.visible }
        return HStack(spacing: 4) {
            Button { if open { collapsed.insert(g.id) } else { collapsed.remove(g.id) } } label: { Image(systemName: open ? "chevron.up" : "chevron.down").frame(width: 30, height: 30) }
                .accessibilityLabel(L("model3d_group_expand"))
            Image(systemName: open ? "folder" : "folder.fill").foregroundStyle(Color.accentColor)
            VStack(alignment: .leading, spacing: 0) {
                Text(g.name).font(.callout).lineLimit(1).foregroundStyle(anyVisible ? .primary : .secondary)
                Text(L("model3d_group_count", members.count)).font(.caption2).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture { vm.selectGroup(g.id) }
            smallButton(anyVisible ? "eye" : "eye.slash", L("model3d_part_visible")) { vm.toggleGroupVisible(g.id) }
            smallButton("pencil", L("model3d_group_rename")) { text = g.name; renamingGroup = g.id }
            smallButton("link", L("model3d_ungroup")) { vm.ungroup(g.id) }
        }
        .padding(.horizontal, 6).padding(.vertical, 4)
        .background(all ? Color.accentColor.opacity(0.12) : .clear, in: RoundedRectangle(cornerRadius: 12))
        .accessibilityIdentifier("model3d_group_row")
    }

    private func partRow(_ p: Part, _ sel: Selection3, indent: Bool) -> some View {
        let chosen = sel.parts.contains(p.id)
        return HStack(spacing: 4) {
            Button { vm.selectPart(p.id, add: true) } label: {
                Image(systemName: chosen ? "checkmark.square.fill" : "square").font(.title3).foregroundStyle(chosen ? Color.accentColor : .secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L("model3d_part_multi"))
            Circle().fill(Color(argb: p.material.color)).frame(width: 16, height: 16).padding(.horizontal, 4)
            Text(p.name).font(.callout).lineLimit(1).foregroundStyle(p.visible ? .primary : .secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
                .onTapGesture { vm.selectPart(p.id) }
            smallButton(p.visible ? "eye" : "eye.slash", L("model3d_part_visible")) { vm.togglePartVisible(p.id) }
            smallButton("chevron.up", L("layer_up")) { vm.movePartInList(p.id, 1) }
            smallButton("chevron.down", L("layer_down")) { vm.movePartInList(p.id, -1) }
            smallButton("pencil", L("model3d_rename")) { text = p.name; renaming = p.id }
            smallButton("trash", L("model3d_delete"), danger: true) { vm.selectPart(p.id); vm.deleteSelected() }
        }
        .padding(.leading, indent ? 22 : 0)
        .padding(.horizontal, 6).padding(.vertical, 4)
        .background(chosen ? Color.accentColor.opacity(0.12) : .clear, in: RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .contain)
        .accessibilityLabel(p.name)
    }

    private func smallButton(_ icon: String, _ label: String, danger: Bool = false, _ action: @escaping () -> Void) -> some View {
        Button(action: action) { Image(systemName: icon).foregroundStyle(danger ? .red : .primary).frame(width: 30, height: 30) }
            .buttonStyle(.plain)
            .accessibilityLabel(label)
    }
}

// MARK: Malzeme

private struct MaterialPanel3: View {
    @ObservedObject var vm: Model3DEditorModel
    @State private var customColor = Color.blue
    @State private var moreColor = false

    var body: some View {
        let target = vm.tool == .PAINT ? nil : vm.selectedMaterial()
        let mat = target ?? vm.paint
        let set: (Material) -> Void = { m in if target != nil { vm.setMaterial(m) } else { vm.setPaint(m) } }
        hint(L(target != nil ? (vm.selection.faces.isEmpty ? "model3d_material_parts" : "model3d_material_faces") : "model3d_material_paint"))
        Text(L("model3d_color")).font(.subheadline.weight(.semibold))
        ColorRow3(selected: mat.color) { c in var m = mat; m.color = c; set(m) }
        ColorPicker(L("model3d_more_colors"), selection: Binding(get: { Color(argb: mat.color) }, set: { c in
            var m = mat; m.color = Self.argb(c); set(m)
        }), supportsOpacity: false)
        Text(L("model3d_gradient")).font(.subheadline.weight(.semibold))
        ChipRow3 {
            Chip3(L("model3d_gradient_none"), mat.gradient == nil) { var m = mat; m.gradient = nil; set(m) }
            ForEach([(1, "model3d_gradient_vertical"), (0, "model3d_gradient_horizontal"), (2, "model3d_gradient_depth")], id: \.0) { item in
                Chip3(L(item.1), mat.gradient != nil && mat.gradientAxis == item.0) {
                    var m = mat; m.gradient = mat.gradient ?? Self.secondColor(mat.color); m.gradientAxis = item.0; set(m)
                }
            }
        }
        .accessibilityIdentifier("model3d_gradient")
        if let g = mat.gradient {
            hint(L("model3d_gradient_second"))
            ColorRow3(selected: g) { c in var m = mat; m.gradient = c; set(m) }
        }
        Text(L("model3d_texture")).font(.subheadline.weight(.semibold))
        let items = vm.textureItems
        let cats = Self.categories(items)
        ForEach(cats, id: \.0) { cat in
            Text(cat.0).font(.caption2).foregroundStyle(.secondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    if cat.0 == cats.first?.0 {
                        textureTile(L("model3d_no_texture"), mat.texture == nil, nil) { var m = mat; m.texture = nil; set(m) }
                    }
                    ForEach(cat.1) { t in
                        textureTile(t.name, mat.texture == t.id, t.id) {
                            var m = mat; m.texture = t.id; m.gradient = nil
                            if mat.texture == nil { m.color = -1 }
                            set(m)
                        }
                    }
                }
            }
        }
        if mat.texture != nil {
            ValueSlider3(L("model3d_texture_scale"), pct(mat.textureScale), mat.textureScale, 0.1...4) { v in var m = mat; m.textureScale = (v * 20).rounded() / 20; set(m) }
        }
        Text(L("model3d_material_kind")).font(.subheadline.weight(.semibold))
        ChipRow3 { ForEach(MaterialKind.allCases, id: \.self) { k in Chip3(Model3DEditorModel.kindLabel(k), mat.kind == k) { var m = mat; m.kind = k; set(m) } } }
        ValueSlider3(L("model3d_shine"), pct(mat.shine), mat.shine, 0...1) { v in var m = mat; m.shine = v; set(m) }
        ValueSlider3(L("model3d_opacity"), pct(mat.opacity), mat.opacity, 0.1...1) { v in var m = mat; m.opacity = v; set(m) }
        if let pid = vm.selection.primary, let p = vm.model?.part(pid) {
            Toggle(L("model3d_smooth"), isOn: Binding(get: { p.smooth }, set: { vm.setSmooth($0) }))
            Text(L("model3d_uv")).font(.subheadline.weight(.semibold))
            ChipRow3 {
                ForEach([(UvMode.BOX, "model3d_uv_box"), (.CYLINDER, "model3d_uv_cylinder"), (.SPHERE, "model3d_uv_sphere"), (.PLANAR, "model3d_uv_planar")], id: \.1) { item in
                    Chip3(L(item.1), p.uv == item.0) { vm.setUv(item.0) }
                }
            }
        }
    }

    private static func categories(_ items: [TextureItem]) -> [(String, [TextureItem])] {
        var order: [String] = []
        var map: [String: [TextureItem]] = [:]
        for t in items { if map[t.category] == nil { order.append(t.category) }; map[t.category, default: []].append(t) }
        return order.map { ($0, map[$0] ?? []) }
    }

    private func textureTile(_ name: String, _ selected: Bool, _ id: String?, _ action: @escaping () -> Void) -> some View {
        let tex = vm.textures
        return Button(action: action) {
            VStack(spacing: 2) {
                ZStack {
                    Color(uiColor: .secondarySystemBackground)
                    if let id {
                        AsyncThumb(key: "tex-\(id)") { tex.texture(id).map { Bitmaps.image(argb: $0.px, $0.w, $0.h) } }
                    } else { Image(systemName: "nosign").foregroundStyle(.secondary) }
                }
                .frame(width: 56, height: 56)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(selected ? Color.accentColor : Color.secondary.opacity(0.3), lineWidth: selected ? 3 : 1))
                Text(name).font(.caption2).lineLimit(1).foregroundStyle(selected ? Color.accentColor : .primary)
            }
            .frame(width: 64)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(name)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    static func argb(_ c: Color) -> Int32 {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(c).getRed(&r, green: &g, blue: &b, alpha: &a)
        func q(_ v: CGFloat) -> UInt32 { UInt32(max(0, min(255, (v * 255).rounded()))) }
        return Int32(bitPattern: 0xFF00_0000 | q(r) << 16 | q(g) << 8 | q(b))
    }

    /// v27 gradyanın varsayılan ikinci rengi: rengin açık tonu (çok açık renkte koyu tonu).
    static func secondColor(_ c: Int32) -> Int32 {
        let u = UInt32(bitPattern: c)
        let r = Int(u >> 16 & 0xFF), g = Int(u >> 8 & 0xFF), b = Int(u & 0xFF)
        let lum = (0.3 * Float(r) + 0.59 * Float(g) + 0.11 * Float(b)) / 255
        let target = lum > 0.75 ? 0 : 255
        let k: Float = lum > 0.75 ? 0.45 : 0.6
        func mix(_ v: Int) -> UInt32 { UInt32(Int(Float(v) + Float(target - v) * k).coerceIn(0, 255)) }
        return Int32(bitPattern: 0xFF00_0000 | mix(r) << 16 | mix(g) << 8 | mix(b))
    }
}

// MARK: Cisim

private struct ShapePanel3: View {
    @ObservedObject var vm: Model3DEditorModel

    var body: some View {
        if let pid = vm.selection.primary, let p = vm.model?.part(pid) {
            Text(p.name).font(.subheadline.weight(.semibold)).foregroundStyle(Color.accentColor)
            if let spec = p.primitive {
                hint(L("model3d_detail_hint"))
                ValueSlider3(L("model3d_segments"), "\(spec.segments)", Float(spec.segments), 3...Float(Primitives.MAX_SEGMENTS)) { v in
                    var s = spec; s.segments = Int(v.rounded()); vm.setPrimitiveSpec(s)
                }
                if [.SPHERE, .DOME, .CAPSULE, .TORUS, .PLANE].contains(spec.kind) {
                    ValueSlider3(L("model3d_rings"), "\(spec.rings)", Float(spec.rings), 2...Float(Primitives.MAX_RINGS)) { v in var s = spec; s.rings = Int(v.rounded()); vm.setPrimitiveSpec(s) }
                }
                if [.CYLINDER, .CONE, .PRISM3, .PRISM6, .PYRAMID].contains(spec.kind) {
                    ValueSlider3(L("model3d_top"), pct(spec.top), spec.top, 0...1.5) { v in var s = spec; s.top = (v * 20).rounded() / 20; vm.setPrimitiveSpec(s) }
                }
                if [.TORUS, .TUBE, .STAR].contains(spec.kind) {
                    ValueSlider3(L("model3d_thickness"), pct(spec.thickness), spec.thickness, 0.05...0.9) { v in var s = spec; s.thickness = v; vm.setPrimitiveSpec(s) }
                }
            } else { hint(L("model3d_mesh_edited")) }
            Text(L("model3d_position")).font(.subheadline.weight(.semibold))
            ForEach(0..<3, id: \.self) { i in
                ValueSlider3(["X", "Y", "Z"][i], String(format: "%.2f", p.position.get(i)), p.position.get(i), -6...6) { v in
                    vm.setTransform(p.id, position: p.position.with(i, (v * 50).rounded() / 50))
                }
            }
            Text(L("model3d_rotation")).font(.subheadline.weight(.semibold))
            ForEach(0..<3, id: \.self) { i in
                ValueSlider3(["X", "Y", "Z"][i], "\(Int(p.rotation.get(i).rounded()))°", p.rotation.get(i), -180...180) { v in
                    vm.setTransform(p.id, rotation: p.rotation.with(i, v.rounded()))
                }
            }
            Text(L("model3d_scale")).font(.subheadline.weight(.semibold))
            ForEach(0..<3, id: \.self) { i in
                ValueSlider3(["X", "Y", "Z"][i], String(format: "%.2f", p.scale.get(i)), p.scale.get(i), 0.05...6) { v in
                    vm.setTransform(p.id, scale: p.scale.with(i, (v * 50).rounded() / 50))
                }
            }
        } else { hint(L("model3d_select_part_hint")) }
    }
}

// MARK: Ayarlar

private struct SettingsPanel3: View {
    @ObservedObject var vm: Model3DEditorModel

    var body: some View {
        if let m = vm.model {
            let s = m.scene, l = s.light
            Section3(title: L("model3d_section_light")) {
                ValueSlider3(L("model3d_light_yaw"), "\(Int(l.yaw.rounded()))°", l.yaw, 0...360) { v in var c = s; c.light.yaw = v; vm.setScene(c) }
                ValueSlider3(L("model3d_light_pitch"), "\(Int(l.pitch.rounded()))°", l.pitch, 5...90) { v in var c = s; c.light.pitch = v; vm.setScene(c) }
                ValueSlider3(L("model3d_light_power"), pct(l.intensity), l.intensity, 0...2) { v in var c = s; c.light.intensity = v; vm.setScene(c) }
                ValueSlider3(L("model3d_ambient"), pct(l.ambient), l.ambient, 0...1) { v in var c = s; c.light.ambient = v; vm.setScene(c) }
                hint(L("model3d_light_color"))
                ColorRow3(selected: l.color, extra: [Int32(bitPattern: 0xFFFF_F3D6), Int32(bitPattern: 0xFFDD_EBFF)]) { v in var c = s; c.light.color = v; vm.setScene(c) }
            }
            Section3(title: L("model3d_section_look")) {
                Toggle(L("model3d_shadow"), isOn: Binding(get: { s.shadow }, set: { v in var c = s; c.shadow = v; vm.setScene(c, coalesce: nil) }))
                Toggle(L("model3d_edges"), isOn: Binding(get: { s.edges }, set: { v in var c = s; c.edges = v; vm.setScene(c, coalesce: nil) }))
                if s.edges { ColorRow3(selected: s.edgeColor) { v in var c = s; c.edgeColor = v; vm.setScene(c) } }
                Toggle(L("model3d_ground_grid"), isOn: Binding(get: { s.ground }, set: { v in var c = s; c.ground = v; vm.setScene(c, coalesce: nil) }))
                Text(L("model3d_background")).font(.subheadline.weight(.semibold))
                Chip3(L("model3d_transparent"), s.background == 0) { var c = s; c.background = 0; vm.setScene(c, coalesce: nil) }
                ColorRow3(selected: s.background == 0 ? nil : s.background) { v in var c = s; c.background = v | Int32(bitPattern: 0xFF00_0000); vm.setScene(c, coalesce: nil) }
            }
            Section3(title: L("model3d_section_camera")) {
                ValueSlider3(L("model3d_perspective"), pct(m.camera.persp), m.camera.persp, 0...1) { vm.setPerspective($0) }
                ChipRow3 {
                    ForEach([(0, "model3d_view_iso"), (1, "model3d_view_front"), (2, "model3d_view_side"), (3, "model3d_view_top")], id: \.0) { item in
                        Chip3(L(item.1), false) { vm.presetView(item.0) }
                    }
                }
            }
            Section3(title: L("model3d_section_suggest")) {
                Toggle(L("model3d_suggest_toggle_long"), isOn: Binding(get: { vm.suggestEnabled }, set: { vm.setSuggestEnabled($0) }))
                hint(L("model3d_suggest_info"))
            }
            Section3(title: L("model3d_section_performance")) {
                Text(L("model3d_quality")).font(.subheadline.weight(.semibold))
                ChipRow3 {
                    ForEach([(Model3DPrefs.QUALITY_HIGH, "model3d_quality_high"), (Model3DPrefs.QUALITY_BALANCED, "model3d_quality_balanced"),
                             (Model3DPrefs.QUALITY_FAST, "model3d_quality_fast")], id: \.0) { item in
                        Chip3(L(item.1), vm.quality == item.0) { vm.setQuality(item.0) }
                    }
                }
                hint(L("model3d_quality_hint"))
            }
        }
    }
}

// MARK: Fırça

private struct BrushPanel3: View {
    @ObservedObject var vm: Model3DEditorModel

    var body: some View {
        let b = vm.brush
        hint(L("model3d_brush_hint"))
        Text(L("model3d_brush_kind")).font(.subheadline.weight(.semibold))
        ChipRow3 { ForEach(Brush3Kind.allCases, id: \.self) { k in Chip3(Model3DPanels.brushLabel(k), b.kind == k) { var c = b; c.kind = k; vm.setBrush(c) } } }
            .accessibilityIdentifier("model3d_brush_kinds")
        Text(Model3DPanels.brushHint(b.kind)).font(.caption).foregroundStyle(Color.accentColor)
        ColorRow3(selected: vm.brushColor) { vm.setBrushColor($0) }
        ValueSlider3(L("model3d_brush_width"), "\(Int(vm.brushWidth.rounded())) px", vm.brushWidth, 1...60) { vm.setBrushWidth($0.rounded()) }
        if b.kind == .WALL {
            ValueSlider3(L("model3d_brush_height"), String(format: "%.2f", b.height), b.height, 0.05...3) { v in var c = b; c.height = (v * 20).rounded() / 20; vm.setBrush(c) }
        }
        if b.kind == .INFLATE { ValueSlider3(L("model3d_brush_bulge"), pct(b.bulge), b.bulge, 0.2...1.5) { v in var c = b; c.bulge = v; vm.setBrush(c) } }
        if b.kind == .TUBE || b.kind == .RIBBON || b.kind == .WALL {
            ValueSlider3(L("model3d_brush_taper"), pct(b.taper), b.taper, 0...1) { v in var c = b; c.taper = v; vm.setBrush(c) }
        }
        if b.kind != .LINE {
            ValueSlider3(L("model3d_brush_smoothing"), pct(b.smoothing), b.smoothing, 0...1) { v in var c = b; c.smoothing = v; vm.setBrush(c) }
            Toggle(L("model3d_brush_pressure"), isOn: Binding(get: { b.pressure }, set: { v in var c = b; c.pressure = v; vm.setBrush(c) }))
            Text(L("model3d_brush_material")).font(.subheadline.weight(.semibold))
            ChipRow3 { ForEach(MaterialKind.allCases, id: \.self) { k in Chip3(Model3DEditorModel.kindLabel(k), b.material == k) { var c = b; c.material = k; vm.setBrush(c) } } }
            ValueSlider3(L("model3d_brush_opacity"), pct(b.opacity), b.opacity, 0.1...1) { v in var c = b; c.opacity = v; vm.setBrush(c) }
        }
    }
}

// MARK: İşlemler

private struct OpsPanel3: View {
    @ObservedObject var vm: Model3DEditorModel

    var body: some View {
        if let m = vm.model {
            let parts = vm.selection.parts.compactMap { m.part($0) }
            let primary = parts.first
            let idle = vm.busy == nil
            Text(parts.isEmpty ? L("model3d_ops_none") : L("model3d_ops_selected", parts.map(\.name).joined(separator: ", ")))
                .font(.caption).foregroundStyle(parts.isEmpty ? Color.secondary : Color.accentColor)
                .accessibilityIdentifier("model3d_ops_selection")
            Section3(title: L("model3d_ops_booleans")) {
                hint(L("model3d_ops_hint"))
                let two = parts.count >= 2 && idle
                HStack(spacing: 8) {
                    opButton(L("model3d_union"), "circle.union", two) { vm.booleanSelected(.UNION) }
                    opButton(L("model3d_subtract"), "circle.subtract", two) { vm.booleanSelected(.SUBTRACT) }
                }
                HStack(spacing: 8) {
                    opButton(L("model3d_intersect"), "circle.intersect", two) { vm.booleanSelected(.INTERSECT) }
                    opButton(L("model3d_merge"), "arrow.triangle.merge", two) { vm.mergeSelected() }
                }
            }
            Section3(title: L("model3d_ops_slice")) {
                hint(L("model3d_ops_slice_hint"))
                ChipRow3 {
                    Chip3("X", vm.slice.0 == .X) { vm.setSlice(axis: .X) }
                    Chip3("Y", vm.slice.0 == .Y) { vm.setSlice(axis: .Y) }
                    Chip3("Z", vm.slice.0 == .Z) { vm.setSlice(axis: .Z) }
                    Chip3(L("model3d_slice_view"), vm.slice.0 == .VIEW) { vm.setSlice(axis: .VIEW) }
                }
                ValueSlider3(L("model3d_slice_offset"), pct(vm.slice.1), vm.slice.1, -0.49...0.49) { vm.setSlice(offset: ($0 * 50).rounded() / 50) }
                Button { vm.sliceSelected() } label: { Label(L("model3d_ops_slice"), systemImage: "rectangle.split.2x1").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).disabled(primary == nil || !idle)
            }
            Section3(title: L("model3d_ops_hole")) {
                hint(L("model3d_ops_hole_hint"))
                ValueSlider3(L("model3d_hole_size"), pct(vm.holeRatio), vm.holeRatio, 0.05...0.95) { vm.setHoleRatio($0) }
                opButton(L("model3d_hole"), "circle.circle", primary != nil && idle) { vm.holeSelected() }
            }
            Section3(title: L("model3d_ops_separate")) {
                hint(L("model3d_ops_separate_hint"))
                opButton(L("model3d_ops_separate"), "arrow.triangle.branch", primary != nil && idle) { vm.separateSelected() }
            }
        }
    }

    private func opButton(_ label: String, _ icon: String, _ enabled: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) { Label(label, systemImage: icon).lineLimit(1).frame(maxWidth: .infinity) }
            .buttonStyle(.bordered).disabled(!enabled)
    }
}
