import SwiftUI

/*
 * v56 aşama 9C (2E, yalnız Resim; Android `V56PathUi.kt`): Nesne menüsünün Yol işlemleri / Efektler / Yol düzenle
 * bölümleri, şekil oluşturucu ve bıçak alt panelleri, düğüm türü ve yolu uzat.
 */

private func pathChip(_ label: String, on: Bool = false, id: String, _ action: @escaping () -> Void) -> some View {
    Button(action: action) {
        Text(label).font(.footnote.weight(.medium)).lineLimit(1)
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(Capsule().fill(on ? Color.accentColor.opacity(0.22) : Color.secondary.opacity(0.10)))
    }
    .buttonStyle(.plain)
    .accessibilityIdentifier(id)
}

private func pathRow<C: View>(@ViewBuilder _ content: () -> C) -> some View {
    ScrollView(.horizontal, showsIndicators: false) { HStack(spacing: 6) { content() } }
}

/// Nesne menüsünde: Yol işlemleri (Pathfinder) ve şekil oluşturucu.
struct V56PathfinderSection: View {
    @ObservedObject var vm: EditorViewModel
    let dismiss: () -> Void

    private static let ops: [(V56PathOps.PathOp, String)] = [
        (.UNITE, "v56_pf_unite"), (.MINUS_FRONT, "v56_pf_minus_front"), (.INTERSECT, "v56_pf_intersect"),
        (.EXCLUDE, "v56_pf_exclude"), (.DIVIDE, "v56_pf_divide"), (.TRIM, "v56_pf_trim"),
        (.MERGE, "v56_pf_merge"), (.CROP, "v56_pf_crop"), (.OUTLINE, "v56_pf_outline"),
    ]

    var body: some View {
        if vm.v56(.ADV_PATHFINDER) {
            let count = vm.pathOperableCount()
            Text(L("v56_pathfinder")).font(.subheadline.weight(.semibold))
            if count < 2 { Text(L("v56_pathfinder_hint")).font(.caption).foregroundStyle(.secondary) }
            pathRow {
                ForEach(Self.ops, id: \.0) { op, key in
                    let enabled = count >= 2 || ((op == .OUTLINE || op == .DIVIDE) && count >= 1)
                    pathChip(L(key), id: "v56_pf_\(String(describing: op).lowercased())") { if enabled && vm.pathOp(op) { dismiss() } }
                }
                pathChip(L("v56_shape_builder"), id: "v56_shape_builder") { if vm.startShapeBuilder() { dismiss() } }
            }
        }
    }
}

/// Nesne menüsünde: Yol düzenle (konturu yola çevir, bıçak, yolu ayır, kapat, ters çevir).
struct V56PathEditSection: View {
    @ObservedObject var vm: EditorViewModel
    let dismiss: () -> Void

    var body: some View {
        if vm.v56(.ADV_NODES) {
            Text(L("v56_path_edit")).font(.subheadline.weight(.semibold))
            pathRow {
                pathChip(L("v56_outline_stroke"), id: "v56_outline_stroke") { if vm.outlineStroke() { dismiss() } }
                pathChip(L("v56_knife"), id: "v56_knife") { vm.startKnife(); dismiss() }
                pathChip(L("v56_path_separate"), id: "v56_path_separate") { if vm.separatePaths() { dismiss() } }
                pathChip(L("v56_path_close"), id: "v56_path_close") { if vm.closeSelectedPaths() { dismiss() } }
                pathChip(L("v56_path_reverse"), id: "v56_path_reverse") { if vm.reverseSelectedPaths() { dismiss() } }
            }
        }
    }
}

/// Nesne › Efektler'e eklenenler: değiştirilebilir efekt listesi ve zarf bozma.
struct V56EffectChips: View {
    @ObservedObject var vm: EditorViewModel
    let dismiss: () -> Void
    @State private var open = false

    var body: some View {
        pathChip(L("v56_effects_list"), id: "v56_effects_list") { if vm.canApplyEffects() { open = true } }
            .sheet(isPresented: $open) { V56EffectsSheet(vm: vm) }
        pathChip(L("v56_envelope"), id: "v56_envelope") { if vm.envelopeDistort() { dismiss() } }
    }
}

func effectLabelKey(_ kind: EffectKind) -> String {
    switch kind {
    case .OFFSET: return "v56_fx_offset"
    case .BEND: return "v56_fx_bend"
    case .ROUGHEN: return "v56_fx_roughen"
    case .ZIGZAG: return "v56_fx_zigzag"
    case .WAVE: return "v56_fx_wave"
    }
}

/// Efekt listesi: ekle, ayarla, kaldır; önizlemeli, Uygula tek geri alma adımı.
struct V56EffectsSheet: View {
    @ObservedObject var vm: EditorViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var list: [VectorEffect] = []
    @State private var applied = false

    private func slider(_ value: Float, _ range: ClosedRange<Float>, round: Bool, _ set: @escaping (Float) -> Void) -> some View {
        Slider(value: Binding(get: { Double(value) }, set: { set(round ? Float($0).rounded() : Float($0)) }),
               in: Double(range.lowerBound)...Double(range.upperBound))
    }

    private func update(_ i: Int, _ f: (inout VectorEffect) -> Void) {
        guard list.indices.contains(i) else { return }
        var e = list[i]; f(&e); list[i] = e
        vm.previewEffects(list)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    pathRow {
                        ForEach(EffectKind.allCases, id: \.self) { k in
                            pathChip("+ " + L(effectLabelKey(k)), id: "v56_fx_add_\(k.rawValue.lowercased())") {
                                switch k {
                                case .OFFSET: list.append(VectorEffect(k, amount: 8))
                                case .BEND: list.append(VectorEffect(k, amount: 0.3))
                                default: list.append(VectorEffect(k, size: 6, detail: 6, seed: list.count + 1))
                                }
                                vm.previewEffects(list)
                            }
                        }
                    }
                    if list.isEmpty { Text(L("v56_effects_empty")).font(.caption).foregroundStyle(.secondary) }
                }
                ForEach(Array(list.enumerated()), id: \.offset) { i, e in
                    Section {
                        HStack {
                            Text(L(effectLabelKey(e.kind))).font(.subheadline.weight(.medium))
                            Spacer()
                            Button(L("v56_remove")) { list.remove(at: i); vm.previewEffects(list) }.accessibilityIdentifier("v56_fx_remove_\(i)")
                        }
                        switch e.kind {
                        case .OFFSET:
                            Text(L("v56_fx_distance", Int(e.amount.rounded()))).font(.caption).foregroundStyle(.secondary)
                            slider(e.amount, -60...60, round: true) { v in update(i) { $0.amount = v } }
                        case .BEND:
                            Text(L("v56_fx_amount") + " · \(Int((e.amount * 100).rounded()))%").font(.caption).foregroundStyle(.secondary)
                            slider(e.amount, -1...1, round: false) { v in update(i) { $0.amount = v } }
                        default:
                            Text(L("v56_fx_size", Int(e.size.rounded()))).font(.caption).foregroundStyle(.secondary)
                            slider(e.size, 1...60, round: true) { v in update(i) { $0.size = v } }
                            Text(L("v56_fx_detail", Int(e.detail.rounded()))).font(.caption).foregroundStyle(.secondary)
                            slider(e.detail, 1...40, round: true) { v in update(i) { $0.detail = v } }
                        }
                    }
                }
            }
            .accessibilityIdentifier("v56_effects_dialog")
            .navigationTitle(L("v56_effects_list"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("cancel_button")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("ok_button")) { applied = true; vm.applyEffects(list); dismiss() }.accessibilityIdentifier("v56_fx_list_apply")
                }
            }
        }
        .presentationDetents([.medium, .large])
        .onAppear { list = vm.selectedEffects(); vm.previewEffects(list) }
        .onDisappear { if !applied { vm.cancelEffectsPreview() } }
    }
}

/// Bıçak ve şekil oluşturucu alt panelleri (açıkken).
struct V56PathBars: View {
    @ObservedObject var vm: EditorViewModel

    var body: some View {
        if vm.knife || vm.shapeBuilder != nil {
            VStack(alignment: .leading, spacing: 6) {
                if vm.knife {
                    Text(L("v56_knife_hint")).font(.caption).foregroundStyle(.secondary)
                    pathRow { pathChip(L("v56_done"), id: "v56_knife_done") { vm.stopKnife() } }
                } else if let b = vm.shapeBuilder {
                    Text(L("v56_builder_hint")).font(.caption).foregroundStyle(.secondary)
                    pathRow {
                        pathChip(L("v56_builder_apply", b.merge.count, b.remove.count), id: "v56_builder_apply") { vm.applyShapeBuilder() }
                        pathChip(L("cancel_button"), id: "v56_builder_cancel") { vm.cancelShapeBuilder() }
                    }
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 14).fill(.regularMaterial))
            .padding(.horizontal, 12).padding(.vertical, 6)
            .accessibilityIdentifier(vm.knife ? "v56_knife_bar" : "v56_builder_bar")
        }
    }
}

/// Şekil düzenlemenin nokta paneli ekleri (Resim modunda): düğüm türü ve yolu uzat.
struct V56NodeRow: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var shapes: ShapeController

    var body: some View {
        if vm.bezierOn() && shapes.tool == .NODES {
            VStack(alignment: .leading, spacing: 4) {
                pathRow {
                    pathChip(L("v56_node_corner"), on: !shapes.smoothNodes, id: "v56_node_corner") { vm.setNodeSmooth(false) }
                    pathChip(L("v56_node_smooth"), on: shapes.smoothNodes, id: "v56_node_smooth") { vm.setNodeSmooth(true) }
                    pathChip(L("v56_path_extend"), id: "v56_path_extend") { vm.extendFromSelectedNode() }
                }
                Text(L("v56_node_hint")).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
            }
        }
    }
}

/// Tuvalin altında: bıçak / şekil oluşturucu çubuğu ve şekil düzenlemede düğüm türü satırı.
struct V56PathOverlay: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var shapes: ShapeController

    var body: some View {
        VStack(spacing: 0) {
            V56PathBars(vm: vm)
            if vm.mode == .SHAPE_EDIT && vm.bezierOn() && shapes.tool == .NODES {
                V56NodeRow(vm: vm, shapes: shapes)
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(RoundedRectangle(cornerRadius: 14).fill(.regularMaterial))
                    .padding(.horizontal, 12).padding(.vertical, 6)
            }
        }
        .frame(maxWidth: 560)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
    }
}
