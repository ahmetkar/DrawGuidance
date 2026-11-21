import SwiftUI

/// v56 aşama 8 simetri adları.
enum V56SymmetryLabel {
    static func text(_ k: SymmetryKind) -> String {
        switch k {
        case .VERTICAL: return L("v56_sym_vertical")
        case .HORIZONTAL: return L("v56_sym_horizontal")
        case .QUAD: return L("v56_sym_quad")
        case .RADIAL: return L("v56_sym_radial")
        }
    }
}

/// Simetri açıkken Çiz, Kalem ve Boya modlarında alttaki çubuk (Android `V56SymmetryBar`).
struct V56SymmetryOverlay: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore

    var body: some View {
        if let c = store.canvas, let spec = c.guide.symmetry, vm.v56(.SYMMETRY), [.DRAW, .PEN, .PAINT].contains(vm.mode) {
            V56SymmetryBar(vm: vm, spec: spec, width: c.width, height: c.height)
                .padding(.horizontal, 12).padding(.bottom, 6)
                .frame(maxWidth: 560)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        }
    }
}

struct V56SymmetryBar: View {
    @ObservedObject var vm: EditorViewModel
    let spec: SymmetrySpec
    let width: Int
    let height: Int

    private func chip(_ label: String, _ on: Bool, _ id: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.footnote.weight(.medium)).lineLimit(1)
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(Capsule().fill(on ? Color.accentColor.opacity(0.22) : Color.secondary.opacity(0.10)))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }

    private func slider(_ label: String, _ value: Float, _ range: ClosedRange<Float>, _ id: String, _ set: @escaping (Float, Bool) -> Void) -> some View {
        HStack(spacing: 8) {
            Text(label).font(.caption).foregroundStyle(.secondary).frame(minWidth: 72, alignment: .leading)
            Slider(value: Binding(get: { Double(value) }, set: { set(Float($0), false) }), in: Double(range.lowerBound)...Double(range.upperBound),
                   onEditingChanged: { if !$0 { set(value, true) } })
                .accessibilityIdentifier(id)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(SymmetryKind.allCases, id: \.self) { k in
                        chip(V56SymmetryLabel.text(k), spec.kind == k, "v56_symbar_\(k.rawValue.lowercased())") { vm.setSymmetry(k) }
                    }
                    chip(L("v56_sym_live"), spec.live, "v56_sym_live") { var s = spec; s.live.toggle(); vm.updateSymmetry(s) }
                    chip(L("v56_sym_center"), false, "v56_sym_center") {
                        var s = spec; s.cx = Float(width) / 2; s.cy = Float(height) / 2; s.angle = 0; vm.updateSymmetry(s)
                    }
                    chip(L("v56_sym_close"), false, "v56_sym_close") { vm.setSymmetry(nil) }
                }
            }
            if spec.kind == .RADIAL {
                slider(L("v56_sym_arms", spec.arms), Float(spec.arms), 2...16, "v56_sym_arms") { v, p in
                    var s = spec; s.arms = Int(v.rounded()); vm.updateSymmetry(s, persist: p)
                }
            }
            slider(L("v56_sym_angle", Int(spec.angle.rounded())), spec.angle, -90...90, "v56_sym_angle") { v, p in
                var s = spec; s.angle = v.rounded(); vm.updateSymmetry(s, persist: p)
            }
            slider(L("v56_sym_cx"), spec.cx, 0...Float(max(width, 1)), "v56_sym_cx") { v, p in var s = spec; s.cx = v; vm.updateSymmetry(s, persist: p) }
            slider(L("v56_sym_cy"), spec.cy, 0...Float(max(height, 1)), "v56_sym_cy") { v, p in var s = spec; s.cy = v; vm.updateSymmetry(s, persist: p) }
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(RoundedRectangle(cornerRadius: 16).fill(.regularMaterial))
        .accessibilityIdentifier("v56_sym_bar")
    }
}

/// "Desen yap": seçim aralıkla tuval boyunca döşenir.
struct V56PatternSheet: View {
    @ObservedObject var vm: EditorViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var gap: Float = 20

    var body: some View {
        NavigationStack {
            Form {
                Text(L("v56_pattern_gap", Int(gap.rounded())))
                Slider(value: Binding(get: { Double(gap) }, set: { gap = Float($0).rounded() }), in: 0...200)
                    .accessibilityIdentifier("v56_pattern_gap")
                Text(L("v56_pattern_count", min(vm.patternCount(gap), 200))).font(.footnote).foregroundStyle(.secondary)
            }
            .navigationTitle(L("v56_pattern"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("cancel_button")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("ok_button")) { vm.makePattern(gap); dismiss() }.accessibilityIdentifier("v56_pattern_apply")
                }
            }
        }
        .presentationDetents([.medium])
        .accessibilityIdentifier("v56_pattern_dialog")
    }
}
