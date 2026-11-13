import SwiftUI

/// v45 grafiğin formül satırı ve grafik aracının ayarları (Android `GraphFormulaRow` + `GraphControls`): formüle dokununca
/// yazma penceresi (geçersiz formül uygulanmaz, hata kırmızı yazılır), derece/radyan; desen ve nokta sayısı, sayılar,
/// ızgara, eğrinin altını boyama, önemli noktalar.
struct GraphControlsBar: View {
    @ObservedObject var store: EditorStore
    @ObservedObject var shapes: ShapeController
    @State private var editing = false
    @State private var draft = ""
    @State private var error: MathError?

    var body: some View {
        if let p = shapes.selectedGraphParams() {
            VStack(alignment: .leading, spacing: 6) {
                if GraphFormula.accepts(p.kind) { formulaRow(p) }
                controls(p)
                Text(L("graph_tap_hint")).font(.caption2).foregroundStyle(.secondary).lineLimit(2).padding(.horizontal, 16)
            }
            .padding(.vertical, 8)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
            .padding(.horizontal, 8)
            .accessibilityIdentifier("graph_controls")
            .onChange(of: p.kind) { _, _ in error = nil }
        }
    }

    private func change(_ block: @escaping (GraphParams) -> GraphParams) { shapes.updateSelectedGraph(block) }

    @ViewBuilder private func formulaRow(_ p: GraphParams) -> some View {
        let meaning = GraphFormula.meaning(p.kind)
        let text = Graphs.formulaText(p)
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                Button { draft = text; editing = true } label: {
                    Label(text.isEmpty ? L("v45_graph_formula") : text, systemImage: "function").lineLimit(1).frame(maxWidth: 320)
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("graph_formula")
                if meaning == .CURVE || meaning == .SURFACE || meaning == .SAMPLED {
                    toggle(L("v45_graph_degrees"), p.angleDeg, "graph_degrees") { change { q in var r = q; r.angleDeg.toggle(); return r } }
                }
            }
            .padding(.horizontal, 12)
        }
        .accessibilityIdentifier("graph_formula_row")
        .alert(L("v45_graph_formula"), isPresented: $editing) {
            TextField(L("v45_graph_formula"), text: $draft)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
            Button(L("apply_button")) {
                let t = draft
                let e = t.trimmingCharacters(in: .whitespaces).isEmpty ? nil : GraphFormula.error(p.kind, t)
                error = e
                if e == nil { change { Graphs.withFormula($0, t) } }
            }
            Button(L("cancel_button"), role: .cancel) {}
        } message: { Text(L(Self.hintKey(meaning))) }
        if let error {
            Text(ParamControlsBar.mathErrorText(error)).font(.footnote).foregroundStyle(.red).lineLimit(2).padding(.horizontal, 16)
                .accessibilityIdentifier("graph_formula_error")
        }
    }

    private static func hintKey(_ m: GraphFormula.Meaning) -> String {
        switch m {
        case .SURFACE: return "v45_graph_hint_surface"
        case .SET: return "v45_graph_hint_set"
        case .SAMPLED: return "v45_graph_hint_sampled"
        default: return "v45_graph_hint_curve"
        }
    }

    private func controls(_ p: GraphParams) -> some View {
        let chart: Set<GraphKind> = [.BAR, .LINE, .PIE, .SCATTER, .HISTOGRAM]
        let isChart = chart.contains(p.kind)
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                stepper(isChart ? L("graph_items", Graphs.itemCount(p)) : L("graph_pattern", Graphs.patternValue(p)),
                        L("graph_pattern_less"), L("graph_pattern_more"), "graph_pattern",
                        minus: { change(Graphs.lessPattern) }, plus: { change(Graphs.morePattern) })
                if !isChart || p.kind == .LINE || p.kind == .SCATTER || p.kind == .BAR {
                    stepper(L("graph_points", p.ticks), L("graph_points_less"), L("graph_points_more"), "graph_points",
                            minus: { change(Graphs.lessPoints) }, plus: { change(Graphs.morePoints) })
                }
                toggle(L("graph_numbers"), p.showNumbers, "graph_numbers") { change { q in var r = q; r.showNumbers.toggle(); return r } }
                if p.kind != .PIE {
                    toggle(L("graph_grid"), p.grid, "graph_grid") { change { q in var r = q; r.grid.toggle(); return r } }
                }
                if p.kind == .FUNCTION {
                    toggle(L("graph_fill_under"), p.fillUnder, "graph_fill_under") { change { q in var r = q; r.fillUnder.toggle(); return r } }
                    toggle(L("graph_key_points"), p.keyPoints, "graph_key_points") { change { q in var r = q; r.keyPoints.toggle(); return r } }
                }
            }
            .padding(.horizontal, 12)
        }
    }

    private func stepper(_ label: String, _ less: String, _ more: String, _ id: String,
                         minus: @escaping () -> Void, plus: @escaping () -> Void) -> some View {
        HStack(spacing: 4) {
            Button(action: minus) { Image(systemName: "minus") }.accessibilityLabel(less).accessibilityIdentifier("\(id)_less")
            Text(label).font(.callout.weight(.medium)).lineLimit(1).padding(.horizontal, 4)
            Button(action: plus) { Image(systemName: "plus") }.accessibilityLabel(more).accessibilityIdentifier("\(id)_more")
        }
        .padding(.horizontal, 10)
        .frame(height: 38)
        .background(Capsule().fill(Color(uiColor: .secondarySystemBackground)))
    }

    private func toggle(_ label: String, _ on: Bool, _ id: String, _ run: @escaping () -> Void) -> some View {
        Button(action: run) {
            Text(label).font(.callout.weight(on ? .semibold : .regular)).lineLimit(1)
                .padding(.horizontal, 12).frame(height: 38)
                .foregroundStyle(on ? Color.accentColor : Color.primary)
                .background(Capsule().fill(on ? Color.accentColor.opacity(0.18) : Color(uiColor: .secondarySystemBackground)))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// Grafikte dokunulan sayı / değer / eksen adı / formül için giriş penceresi (Android `GraphValueDialog`).
struct GraphValueHost: View {
    @ObservedObject var shapes: ShapeController
    @State private var draft = ""

    var body: some View {
        let req = shapes.graphEdit
        Color.clear.frame(width: 0, height: 0)
            .alert(req.map { Self.title($0.tag) } ?? "", isPresented: Binding(get: { shapes.graphEdit != nil },
                                                                             set: { if !$0 { shapes.dismissGraphEdit() } })) {
                TextField(req.map { Self.title($0.tag) } ?? "", text: $draft)
                    .keyboardType(req?.tag.hasPrefix("value:") == true ? .decimalPad : .default)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                Button(L("apply_button")) { shapes.applyGraphEdit(draft) }
                Button(L("cancel_button"), role: .cancel) { shapes.dismissGraphEdit() }
            } message: {
                if req?.tag.hasPrefix("tick:") == true { Text(L("graph_edit_tick_hint")) }
            }
            .onChange(of: req) { _, r in if let r { draft = r.initial } }
            .accessibilityIdentifier("graph_value_sheet")
    }

    private static func title(_ tag: String) -> String {
        if tag.hasPrefix("tick:") { return L("graph_edit_tick") }
        if tag.hasPrefix("value:") { return L("graph_edit_value") }
        if tag.hasPrefix("vlabel:") { return L("graph_edit_label") }
        if tag == "formula" { return L("graph_edit_formula") }
        return L("graph_edit_axis")
    }
}
