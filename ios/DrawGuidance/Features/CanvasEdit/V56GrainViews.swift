import PhotosUI
import SwiftUI

/// v56 hazır dokunun adı (7 dilde).
func textureLabel(_ id: String) -> String {
    let name = id.hasPrefix(V56Textures.PREFIX) ? String(id.dropFirst(V56Textures.PREFIX.count)) : id
    return L("v56_tex_" + (V56Textures.NAMES.contains(name) ? name : "hatch"))
}

/// Doku örneği (karo; koyu = boya tutan yer).
struct TextureSwatchView: View {
    let id: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Image(decorative: GrainRenderer.preview(id), scale: 1)
                    .renderingMode(.template)
                    .resizable()
                    .interpolation(.none)
                    .foregroundStyle(Color.primary)
                    .frame(width: 44, height: 44)
                    .padding(2)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color(uiColor: .systemBackground)))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(selected ? Color.accentColor : Color.secondary.opacity(0.4), lineWidth: selected ? 3 : 1))
                Text(textureLabel(id)).font(.caption2).lineLimit(1)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(textureLabel(id))
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// v56 Fırça Özellikleri › Gren bölümü (Android `GrainSection`): doku, ölçek, güç, kayar; resimden fırça oluştur.
struct GrainSectionView: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var brush: BrushController
    @State private var photo: PhotosPickerItem?

    private var type: BrushType { brush.settings.brushType }
    private var tuning: BrushTuning { brush.settings.tuning }
    private func pct(_ v: Float) -> String { "%\(Int((v * 100).rounded()))" }

    var body: some View {
        if vm.v56(.GRAIN) && BrushTuning.supportsGrain(type) {
            PropSection(title: L("v56_grain_title")) {
                Text(L("v56_grain_hint")).font(.caption).foregroundStyle(.secondary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        Button { vm.setGrainTexture(nil) } label: {
                            Text(L("v56_grain_none")).font(.footnote.weight(.medium))
                                .padding(.horizontal, 10).padding(.vertical, 6)
                                .background(Capsule().fill(tuning.grain == nil ? Color.accentColor.opacity(0.22) : Color.secondary.opacity(0.10)))
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("v56_grain_none")
                        ForEach(V56Textures.BUILTIN, id: \.self) { id in
                            TextureSwatchView(id: id, selected: tuning.grain?.textureId == id) { vm.setGrainTexture(id) }
                                .accessibilityIdentifier("v56_grain_\(id.dropFirst(V56Textures.PREFIX.count))")
                        }
                    }
                }
                if let g = tuning.grain {
                    PropSlider(label: L("v56_grain_scale"), value: Binding(get: { Double(g.scale) }, set: { v in vm.setGrain { var n = $0; n.scale = Float((v * 100).rounded() / 100); return n } }),
                               range: Double(V56Textures.MIN_SCALE)...Double(V56Textures.MAX_SCALE), format: { "%\(Int(($0 * 100).rounded()))" }, id: "v56_grain_scale")
                    PropSlider(label: L("v56_grain_strength"), value: Binding(get: { Double(g.strength) }, set: { v in vm.setGrain { var n = $0; n.strength = Float((v * 100).rounded() / 100); return n } }),
                               range: 0...1, format: { "%\(Int(($0 * 100).rounded()))" }, id: "v56_grain_strength")
                    Toggle(isOn: Binding(get: { g.moving }, set: { on in vm.setGrain { var n = $0; n.moving = on; return n } })) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(L("v56_grain_moving"))
                            Text(L("v56_grain_moving_hint")).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .accessibilityIdentifier("v56_grain_moving")
                }
                if BrushTuning.supportsImageTip(type) {
                    HStack {
                        PhotosPicker(selection: $photo, matching: .images) {
                            Label(L("v56_image_brush"), systemImage: "paintbrush.pointed")
                        }
                        .accessibilityIdentifier("v56_image_brush")
                        Spacer()
                        if tuning.stampAssetId != nil {
                            Button(L("v56_image_brush_remove")) { vm.removeImageBrush() }
                                .accessibilityIdentifier("v56_image_brush_remove")
                        }
                    }
                    if tuning.stampAssetId != nil {
                        Text(L("v56_image_brush_active")).font(.caption).foregroundStyle(.secondary)
                            .accessibilityIdentifier("v56_image_brush_active")
                    }
                }
            }
            .onChangeCompat(of: photo) { _, it in
                guard let it else { return }
                Task {
                    if let data = try? await it.loadTransferable(type: Data.self) { vm.createImageBrush(data) }
                    photo = nil
                }
            }
        }
    }
}

/// v56 doku seçici (Dokuyla doldur, katmanı dokuyla doldur, Ekle › Doku). `vm.textureFillTarget`:
/// "area" (seçim alanı), "page" (etkin katmanın sayfası) ya da "layer:<kimlik>".
struct TexturePickerSheet: View {
    @ObservedObject var vm: EditorViewModel
    let target: String

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 72), spacing: 12)], spacing: 12) {
                    ForEach(V56Textures.BUILTIN, id: \.self) { id in
                        TextureSwatchView(id: id, selected: false) { pick(id) }
                            .accessibilityIdentifier("v56_texture_\(id.dropFirst(V56Textures.PREFIX.count))")
                    }
                }
                .padding()
                .accessibilityIdentifier("v56_texture_picker")
            }
            .navigationTitle(L(target == "area" ? "v56_texture_fill" : "v56_texture_fill_layer"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("cancel_button")) { vm.textureFillTarget = nil } } }
        }
        .presentationDetents([.medium])
    }

    private func pick(_ id: String) {
        let t = target
        vm.textureFillTarget = nil
        if t == "area" { vm.textureFill(id) }
        else if t.hasPrefix("layer:") { vm.textureFillLayer(String(t.dropFirst(6)), id) }
        else { vm.textureFill(id, wholePage: true) }
    }
}
