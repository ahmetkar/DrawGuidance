import SwiftUI

/// v56 tuval üstü seçim çubukları (Android `V56SelectionOverlay`): seçim alanı varken (üstte) ters çevir, yumuşat,
/// genişlet/daralt, doldur, kaldır; sihirli değnek açıkken (altta) ölçüt ve tolerans.
struct V56SelectionOverlay: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore

    private var wand: Bool { vm.canvasPick == .MAGIC_WAND && vm.mode == .SELECT }

    var body: some View {
        if store.area != nil || wand {
            VStack {
                if let a = store.area {
                    bar {
                        Text(L("v56_area_title")).font(.caption).foregroundStyle(.secondary)
                        chip(L("v56_area_invert"), on: a.inverted, id: "v56_area_invert") { vm.selection.invertArea() }
                        chip(L("v56_area_feather", Int(a.feather.rounded())), on: a.feather > 0, id: "v56_area_feather") { vm.selection.cycleFeather() }
                        chip(L("v56_area_grow"), id: "v56_area_grow") { vm.selection.growArea(8) }
                        chip(L("v56_area_shrink"), id: "v56_area_shrink") { vm.selection.growArea(-8) }
                        if !a.inverted && vm.v56(.LASSO_FILL) {
                            chip(L("v56_area_fill"), id: "v56_area_fill") { vm.lassoFill() }
                        }
                        chip(L("v56_area_clear"), id: "v56_area_clear") { vm.selection.clearArea() }
                    }
                    .padding(.top, 8)
                }
                Spacer(minLength: 0)
                if wand {
                    bar {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 6) {
                                wandChip("v56_wand_color", .COLOR)
                                wandChip("v56_wand_stroke", .STROKE)
                                wandChip("v56_wand_appearance", .APPEARANCE)
                            }
                            Text(L("v56_wand_tolerance", Int((vm.wandTolerance * 100).rounded()))).font(.caption).foregroundStyle(.secondary)
                            Slider(value: Binding(get: { Double(vm.wandTolerance) }, set: { vm.setWandTolerance(Float($0)) }), in: 0...1)
                                .frame(minWidth: 200, maxWidth: 320)
                                .accessibilityIdentifier("v56_wand_tolerance")
                        }
                    }
                    .padding(.bottom, 8)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func wandChip(_ key: String, _ value: V56Selection.WandCriterion) -> some View {
        chip(L(key), on: vm.wandCriterion == value, id: "v56_wand_\(String(describing: value).lowercased())") { vm.wandCriterion = value }
    }

    private func chip(_ label: String, on: Bool = false, id: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.footnote.weight(.medium)).lineLimit(1)
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(Capsule().fill(on ? Color.accentColor.opacity(0.22) : Color.secondary.opacity(0.10)))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }

    private func bar<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) { content() }.padding(.horizontal, 8).padding(.vertical, 6)
        }
        .fixedSize(horizontal: false, vertical: true)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color(.systemBackground).opacity(0.92)))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.secondary.opacity(0.22), lineWidth: 1))
        .padding(.horizontal, 8)
    }
}
