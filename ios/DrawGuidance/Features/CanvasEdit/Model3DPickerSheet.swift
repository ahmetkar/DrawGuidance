import SwiftUI
import UniformTypeIdentifiers

/// v56 aşama 10 tuvalde "3B şekil ekle" penceresi (Android `Model3DPickerSheet`): iki kaynak, **Kütüphane** (kategoriler
/// ve arama) ve **GLB dosyası** (dosya seç, ayrıntı düzeyi; basit çizimde yok). Şekil seçilince açı adımı gelir:
/// önizleme parmakla çevrilir, "3B şekil ekle" tuvale koyar. 3B Şekillerim, kalemle düzenleme ve "Yeni 3B şekil" yoktur.
struct Model3DPickerSheet: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var repository: Model3DRepository
    let kind: CanvasKind
    @Environment(\.dismiss) private var dismiss
    @State private var glbTab = false
    @State private var chosen: String?
    @State private var query = ""
    @State private var picking = false
    @State private var glb: (name: String, bytes: [UInt8])?
    @State private var detail = Budget3D.importDetail
    @State private var loading = false
    @State private var model: Model3D?

    /// Basit resim ve çocuk çiziminde 10 hazır 3B şekil (Android `SimpleCatalog.models3d`); öteki türlerde bütün katalog.
    static func catalogOnly(_ kind: CanvasKind) -> [String]? {
        guard kind.simple, !kind.isNotes else { return nil }
        return ["geo.cuboid", "geo.sphere", "geo.cylinder", "geo.cone", "geo.pyramid",
                "geo.torus", "draw.house", "draw.tree", "draw.car", "draw.apple"]
    }

    private var chosenName: String? {
        if glbTab { return glb?.name }
        return chosen.flatMap { id in Catalog3D.all.first { $0.id == id }?.name.tr }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        if !kind.simple {
                            Picker("", selection: $glbTab) {
                                Text(L("v56_m3_library")).tag(false)
                                Text(L("v56_m3_glb")).tag(true)
                            }
                            .pickerStyle(.segmented)
                            .accessibilityIdentifier("model3d_tabs")
                        }
                        if glbTab { glbSection } else {
                            Text(L("model3d_add_pick_hint")).font(.footnote).foregroundStyle(.secondary)
                            if Self.catalogOnly(kind) == nil {
                                TextField(L("v56_m3_search"), text: $query)
                                    .textFieldStyle(.roundedBorder)
                                    .accessibilityIdentifier("model3d_search")
                            }
                            CatalogGrid3(columns: 5, tile: 64, selected: chosen, query: query, only: Self.catalogOnly(kind)) { item in
                                chosen = chosen == item.id ? nil : item.id
                            }
                        }
                    }
                    .padding(16)
                }
                Divider()
                // Alt çubuk her ekran yönünde görünür
                HStack(spacing: 12) {
                    Text(chosenName.map { L("model3d_add_selected", $0) } ?? L("model3d_add_none"))
                        .font(.subheadline).lineLimit(2)
                        .foregroundStyle(chosenName != nil ? Color.accentColor : .secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if loading { ProgressView() }
                    Button { next() } label: { Label(L("v56_m3_next"), systemImage: "chevron.right") }
                        .buttonStyle(.borderedProminent)
                        .disabled(chosenName == nil || loading)
                        .accessibilityIdentifier("model3d_add_button")
                }
                .padding(.horizontal, 20).padding(.vertical, 12)
            }
            .navigationTitle(L("model3d_add_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("close")) { dismiss() } } }
            .navigationDestination(isPresented: Binding(get: { model != nil }, set: { if !$0 { model = nil } })) {
                if let m = model {
                    Model3DAngleView(model: m, title: L("v56_m3_angle_title"), confirm: L("model3d_add_button"),
                                     preview: { m, y, p, s in await vm.previewModel3d(m, yaw: y, pitch: p, size: s) }) { yaw, pitch in
                        vm.insertModel3d(m, yaw: yaw, pitch: pitch)
                        dismiss()
                    }
                }
            }
        }
        .accessibilityIdentifier("model3d_picker")
        .fileImporter(isPresented: $picking, allowedContentTypes: [.glb, .gltf, .data]) { r in
            guard case .success(let url) = r else { return }
            let ok = url.startAccessingSecurityScopedResource()
            defer { if ok { url.stopAccessingSecurityScopedResource() } }
            if let d = try? Data(contentsOf: url) { glb = (url.deletingPathExtension().lastPathComponent, [UInt8](d)) }
        }
        .task { await repository.warmUp() }
    }

    @ViewBuilder private var glbSection: some View {
        Text(L("v56_m3_glb_hint")).font(.footnote).foregroundStyle(.secondary)
        Button { picking = true } label: { Label(L("v56_m3_glb_pick"), systemImage: "square.and.arrow.down").frame(maxWidth: .infinity) }
            .buttonStyle(.bordered)
            .accessibilityIdentifier("model3d_import_glb")
        if let g = glb {
            let bytes = g.bytes.count
            let size = bytes >= 1_048_576 ? String(format: "%.1f MB", Double(bytes) / 1_048_576) : "\(bytes / 1024) KB"
            Text(L("glb_import_file", g.name, size)).font(.subheadline)
            Text(L("glb_import_detail")).font(.subheadline.weight(.semibold))
            Picker("", selection: $detail) {
                ForEach(0..<3, id: \.self) { i in
                    Text(L(["model3d_detail_low", "model3d_detail_mid", "model3d_detail_high"][i]).components(separatedBy: " (")[0]).tag(i)
                }
            }
            .pickerStyle(.segmented)
        }
    }

    private func next() {
        if glbTab {
            guard let g = glb else { return }
            loading = true
            Task {
                model = await vm.glbModel3d(g.bytes, name: g.name, maxTriangles: Model3DRepository.DETAIL_LEVELS[detail])
                loading = false
            }
        } else if let id = chosen {
            model = vm.catalogModel3d(id)
        }
    }
}

/// Açı seçimi (ekleme ve "Açıyı değiştir"): önizleme parmakla sürüklenince döner (yatay: dönüş, dikey: eğim); hazır
/// açılar ve kaydırıcılar. Onay modeli değiştirmez, yalnız bakış açısını verir.
struct Model3DAngleView: View {
    let model: Model3D
    let title: String
    let confirm: String
    let preview: (Model3D, Float, Float, Int) async -> CGImage?
    let onConfirm: (Float, Float) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var yaw: Float = 35
    @State private var pitch: Float = 24
    @State private var image: CGImage?
    @State private var dragBase: (Float, Float)?

    private static let presets: [(String, Float, Float)] = [("v56_m3_front", 0, 0), ("v56_m3_side", 90, 0), ("v56_m3_top", 0, 80), ("v56_m3_iso", 35, 24)]

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Text(L("v56_m3_angle_hint")).font(.footnote).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading)
                ZStack {
                    RoundedRectangle(cornerRadius: 16).fill(Color.secondary.opacity(0.08))
                    if let img = image { Image(decorative: img, scale: 1).resizable().scaledToFit() } else { ProgressView() }
                }
                .aspectRatio(1, contentMode: .fit)
                .frame(maxWidth: 360)
                .gesture(DragGesture().onChanged { g in
                    let base = dragBase ?? (yaw, pitch)
                    dragBase = base
                    yaw = ((base.0 - Float(g.translation.width) * 0.5).truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)
                    pitch = min(max(base.1 + Float(g.translation.height) * 0.4, -80), 80)
                }.onEnded { _ in dragBase = nil })
                .accessibilityIdentifier("model3d_angle_preview")
                HStack(spacing: 6) {
                    ForEach(Self.presets, id: \.0) { key, y, p in
                        Button(L(key)) { yaw = y; pitch = p }
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier("model3d_angle_\(Int(y))_\(Int(p))")
                    }
                }
                Text(L("v56_m3_yaw", Int(yaw.rounded()))).font(.caption).frame(maxWidth: .infinity, alignment: .leading)
                Slider(value: Binding(get: { Double(yaw) }, set: { yaw = Float($0) }), in: 0...360).accessibilityIdentifier("model3d_angle_yaw")
                Text(L("v56_m3_pitch", Int(pitch.rounded()))).font(.caption).frame(maxWidth: .infinity, alignment: .leading)
                Slider(value: Binding(get: { Double(pitch) }, set: { pitch = Float($0) }), in: -80...80).accessibilityIdentifier("model3d_angle_pitch")
            }
            .padding(16)
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(confirm) { onConfirm(yaw, pitch) }.accessibilityIdentifier("model3d_angle_confirm")
            }
        }
        .accessibilityIdentifier("model3d_angle")
        .onAppear { yaw = model.camera.yaw; pitch = model.camera.pitch }
        .task(id: "\(Int(yaw))_\(Int(pitch))") {
            try? await Task.sleep(nanoseconds: 40_000_000)
            image = await preview(model, yaw, pitch, 320)
        }
    }
}

/// "Açıyı değiştir" penceresi (seçili 3B şekil).
struct Model3DChangeAngleSheet: View {
    @ObservedObject var vm: EditorViewModel
    let model: Model3D
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Model3DAngleView(model: model, title: L("v56_m3_change_angle"), confirm: L("ok_button"),
                             preview: { m, y, p, s in await vm.previewModel3d(m, yaw: y, pitch: p, size: s) }) { yaw, pitch in
                vm.changeModel3dAngle(yaw: yaw, pitch: pitch)
                dismiss()
            }
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("cancel_button")) { dismiss() } } }
        }
    }
}
