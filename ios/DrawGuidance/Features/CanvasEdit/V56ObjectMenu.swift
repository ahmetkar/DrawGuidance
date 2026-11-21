import SwiftUI

/// v56 aşama 6A Nesne menüsü (Android `V56ObjectMenu.kt`, yalnız Resim): Hizala ve dağıt (kâğıt / seçim / ana nesne),
/// Efektler (girdap, büzüştür, şişir, yansıma oluştur), Tekrarla (dönüşümü tekrarla / çoğalt).
struct V56ObjectMenu: View {
    @ObservedObject var vm: EditorViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var ref: V56Arrange.Ref = .SELECTION
    @State private var effect: V56Arrange.Effect?

    private func chip(_ label: String, on: Bool = false, id: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.footnote.weight(.medium)).lineLimit(1)
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(Capsule().fill(on ? Color.accentColor.opacity(0.22) : Color.secondary.opacity(0.10)))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }

    private func row<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        ScrollView(.horizontal, showsIndicators: false) { HStack(spacing: 6) { content() } }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    if vm.v56(.ADV_ALIGN) {
                        Text(L("v56_align_title")).font(.subheadline.weight(.semibold))
                        row {
                            ForEach([(V56Arrange.Ref.PAPER, "v56_align_ref_paper"), (.SELECTION, "v56_align_ref_selection"), (.KEY, "v56_align_ref_key")], id: \.0) { r, key in
                                chip(L(key), on: ref == r, id: "v56_align_ref_\(r.rawValue.lowercased())") { ref = r }
                            }
                        }
                        if ref == .KEY { Text(L("v56_align_key_hint")).font(.caption).foregroundStyle(.secondary) }
                        row {
                            ForEach([(V56Arrange.Align.LEFT, "v56_align_left"), (.HCENTER, "v56_align_hcenter"), (.RIGHT, "v56_align_right"),
                                     (.TOP, "v56_align_top"), (.VCENTER, "v56_align_vcenter"), (.BOTTOM, "v56_align_bottom")], id: \.0) { a, key in
                                chip(L(key), id: "v56_align_\(a.rawValue.lowercased())") { vm.alignObjects(a, ref) }
                            }
                        }
                        row {
                            ForEach([(V56Arrange.Distribute.HORIZONTAL, "v56_distribute_h"), (.VERTICAL, "v56_distribute_v"),
                                     (.SPACE_H, "v56_space_h"), (.SPACE_V, "v56_space_v")], id: \.0) { d, key in
                                chip(L(key), id: "v56_distribute_\(d.rawValue.lowercased())") { vm.distributeObjects(d) }
                            }
                        }
                    }
                    if vm.v56(.ADV_EFFECTS) {
                        Text(L("v56_effects_title")).font(.subheadline.weight(.semibold))
                        row {
                            ForEach([(V56Arrange.Effect.TWIRL, "v56_fx_twirl"), (.PUCKER, "v56_fx_pucker"), (.BLOAT, "v56_fx_bloat")], id: \.0) { e, key in
                                chip(L(key), id: "v56_fx_\(e.rawValue.lowercased())") { if vm.canEffect() { effect = e } }
                            }
                            if vm.v56(.REFLECTION_LIQUIFY) {
                                chip(L("v56_reflection"), id: "v56_fx_reflection") { vm.reflectSelection(); dismiss() }
                            }
                        }
                    }
                    if vm.v56(.ADV_TRANSFORM) {
                        Text(L("v56_repeat_title")).font(.subheadline.weight(.semibold))
                        row {
                            chip(L("v56_tf_repeat"), id: "v56_obj_repeat") { vm.repeatTransform(duplicate: false) }
                            chip(L("v56_tf_duplicate"), id: "v56_obj_duplicate") { vm.repeatTransform(duplicate: true) }
                        }
                    }
                }
                .padding()
            }
            .navigationTitle(L("v56_object_menu"))
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("close")) { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
        .accessibilityIdentifier("v56_object_menu")
        .sheet(item: Binding(get: { effect.map { EffectItem(effect: $0) } }, set: { effect = $0?.effect })) { item in
            V56EffectSheet(vm: vm, effect: item.effect)
        }
    }
}

private struct EffectItem: Identifiable { let effect: V56Arrange.Effect; var id: String { effect.rawValue } }

/// Efekt miktarı (önizlemeli): Uygula tek geri alma adımı, İptal belgeyi değiştirmez.
private struct V56EffectSheet: View {
    @ObservedObject var vm: EditorViewModel
    let effect: V56Arrange.Effect
    @Environment(\.dismiss) private var dismiss
    @State private var amount: Float = 0
    @State private var applied = false

    var body: some View {
        let label = L(effect == .TWIRL ? "v56_fx_twirl" : effect == .PUCKER ? "v56_fx_pucker" : "v56_fx_bloat")
        NavigationStack {
            Form {
                Section(L("v56_fx_amount") + " · " + (effect == .TWIRL ? "\(Int(amount.rounded()))°" : "\(Int((amount * 100).rounded()))%")) {
                    Slider(value: Binding(get: { Double(amount) }, set: { amount = Float($0); vm.previewEffect(effect, amount) }),
                           in: Double(V56Arrange.range(effect).lowerBound)...Double(V56Arrange.range(effect).upperBound))
                        .accessibilityIdentifier("v56_fx_amount")
                }
            }
            .navigationTitle(label)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("cancel_button")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("ok_button")) { applied = true; vm.applyEffect(effect, amount); dismiss() }.accessibilityIdentifier("v56_fx_apply")
                }
            }
        }
        .presentationDetents([.height(240)])
        .onAppear { amount = V56Arrange.defaultAmount(effect); vm.previewEffect(effect, amount) }
        .onDisappear { if !applied { vm.cancelEffect() } }
    }
}
