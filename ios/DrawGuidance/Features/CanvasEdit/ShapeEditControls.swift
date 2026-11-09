import SwiftUI

/// Şekil düzenleme araçları (Android `EditorToolbar` SHAPE_EDIT satırı): noktalar, nokta ekle, bağla, çizgiler,
/// bölge boya; grafiklerde Grafik, parametreli şekillerde Değerler.
struct ShapeEditToolRow: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var shapes: ShapeController

    private var tools: [ShapeTool] {
        var out: [ShapeTool] = []
        if shapes.paramShapes.model() != nil { out.append(.PARAMS) }
        if shapes.selectedIsGraph() { out.append(.GRAPH) }
        return out + [.NODES, .ADD_NODE, .CONNECT, .LINES, .FILL]
    }

    var body: some View {
        ForEach(tools, id: \.self) { t in
            ToolButton(icon: Self.icon(t), label: Self.label(t), selected: shapes.tool == t, id: "shape_tool_\(t)") {
                shapes.paramShapes.clearError()
                shapes.selectTool(t)
            }
        }
        if shapes.tool.nodeFamily && shapes.canSimplify() {
            ToolButton(icon: "wand.and.stars", label: L("tool_subdivide"), id: "shape_simplify") { shapes.simplifySelected() }
        }
        ToolButton(icon: "checkmark", label: L("apply_button"), id: "finish_action") { vm.finishAction() }
    }

    static func icon(_ t: ShapeTool) -> String {
        switch t {
        case .NODES: return "point.topleft.down.to.point.bottomright.curvepath"
        case .ADD_NODE: return "plus.circle"
        case .CONNECT: return "point.3.connected.trianglepath.dotted"
        case .LINES: return "line.diagonal"
        case .FILL: return "drop.fill"
        case .GRAPH: return "chart.xyaxis.line"
        case .PARAMS: return "function"
        }
    }

    static func label(_ t: ShapeTool) -> String {
        switch t {
        case .NODES: return L("tool_nodes")
        case .ADD_NODE: return L("tool_add_node")
        case .CONNECT: return L("tool_connect")
        case .LINES: return L("tool_lines")
        case .FILL: return L("tool_region_fill")
        case .GRAPH: return L("tool_graph")
        case .PARAMS: return L("v45_tool_params")
        }
    }
}

/// v45 parametreli şeklin düzenleyicisi (Android `ParamControls`): formül (dokununca yazma penceresi), değer
/// adımlayıcıları, molekül görünümü ve "Vektör düzenle". Hata kırmızı yazılır; şekil son geçerli hâlinde kalır.
struct ParamControlsBar: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var shapes: ShapeController
    @ObservedObject var params: ParamShapeController
    @State private var editing = false
    @State private var draft = ""

    var body: some View {
        if let stroke = shapes.selectedShape, let model = params.model(stroke) {
            let raw = params.raw(stroke)
            let values = model.values(raw)
            let formula = model.editText(raw)
            VStack(alignment: .leading, spacing: 6) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        Button { draft = formula; editing = true } label: {
                            Label(formula, systemImage: "function").lineLimit(1).frame(maxWidth: 320)
                        }
                        .buttonStyle(.bordered)
                        .accessibilityIdentifier("param_formula")
                        ForEach(model.defs, id: \.key) { d in defControl(d, values) }
                        Button { params.clearError(); shapes.selectTool(.NODES) } label: {
                            Label(L("v45_vector_edit"), systemImage: "scope")
                        }
                        .buttonStyle(.bordered)
                        .accessibilityIdentifier("param_vector_edit")
                    }
                    .padding(.horizontal, 12)
                }
                if let e = params.error {
                    Text(Self.errorText(e)).font(.footnote).foregroundStyle(.red).lineLimit(2).padding(.horizontal, 16)
                        .accessibilityIdentifier("param_error")
                }
            }
            .padding(.vertical, 8)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
            .padding(.horizontal, 8)
            .accessibilityIdentifier("param_controls")
            .alert(L("v45_param_formula"), isPresented: $editing) {
                TextField(L("v45_param_formula_hint"), text: $draft)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                Button(L("apply_button")) { _ = params.applyFormula(draft) }
                Button(L("cancel_button"), role: .cancel) {}
            } message: { Text(L("v45_param_formula_hint")) }
        }
    }

    @ViewBuilder private func defControl(_ d: ParamDef, _ values: ParamValues) -> some View {
        if d.kind == .TEXT && !d.choices.isEmpty {
            let cur = values.text(d.key)
            ForEach(d.choices, id: \.self) { c in
                Button { params.setText(d.key, c) } label: { Text(Self.molViewLabel(c)) }
                    .buttonStyle(.bordered)
                    .tint(cur == c ? Color.accentColor : Color.secondary)
                    .accessibilityIdentifier("param_choice_\(c)")
            }
        } else if d.kind != .TEXT && !d.advanced {
            let v = values[d.key]
            let unit = d.unit.isEmpty || d.unit == "°" ? d.unit : " \(d.unit)"
            let label = "\(d.symbol) = \(ParamModel.num(v, 2))\(unit)"
            HStack(spacing: 4) {
                Button { _ = params.setValue(d.key, v - d.step) } label: { Image(systemName: "minus") }
                    .accessibilityLabel(L("v45_param_less", label))
                Text(label).font(.callout.weight(.medium)).lineLimit(1).padding(.horizontal, 4)
                Button { _ = params.setValue(d.key, v + d.step) } label: { Image(systemName: "plus") }
                    .accessibilityLabel(L("v45_param_more", label))
            }
            .padding(.horizontal, 10)
            .frame(height: 38)
            .background(Capsule().fill(Color(uiColor: .secondarySystemBackground)))
            .accessibilityIdentifier("param_step_\(d.key)")
        }
    }

    static func molViewLabel(_ view: String) -> String {
        switch view {
        case MolView.CONDENSED: return L("v45_mol_condensed")
        case MolView.SKELETAL: return L("v45_mol_skeletal")
        case MolView.BALL: return L("v45_mol_3d")
        default: return L("v45_mol_structural")
        }
    }

    static func mathErrorText(_ e: MathError) -> String {
        let at = max(e.pos + 1, 1)
        switch e.code {
        case .EMPTY: return L("v45_math_empty")
        case .UNEXPECTED: return L("v45_math_unexpected", e.detail, at)
        case .PARENTHESIS: return L("v45_math_parenthesis", at)
        case .MISSING_OPERAND: return L("v45_math_missing", at)
        case .UNKNOWN_NAME: return L("v45_math_unknown", e.detail)
        case .ARGUMENTS: return L("v45_math_arguments", e.detail)
        case .DIVISION_BY_ZERO: return L("v45_math_div_zero")
        case .DOMAIN: return L("v45_math_domain", e.detail)
        case .RELATION: return L("v45_math_relation")
        case .OVERFLOW: return L("v45_math_overflow")
        }
    }

    static func chemErrorText(_ detail: String) -> String {
        let parts = detail.split(separator: ":", maxSplits: 1).map(String.init)
        let code = parts.first.flatMap { ChemError.Code(rawValue: $0) }
        let arg = parts.count > 1 ? parts[1] : ""
        switch code {
        case .EMPTY: return L("v45_chem_empty")
        case .UNKNOWN_ELEMENT: return L("v45_chem_unknown_element", arg)
        case .VALENCE: return L("v45_chem_valence", arg)
        case .HYDROGEN_COUNT: return L("v45_chem_hydrogen")
        case .TOO_LARGE: return L("v45_chem_too_large", arg)
        case .IONIC: return L("v45_chem_ionic", arg)
        case .PARENTHESIS: return L("v45_chem_parenthesis")
        case .RING: return L("v45_chem_ring", arg)
        case .SYNTAX, nil: return L("v45_chem_syntax")
        }
    }

    static func errorText(_ e: ParamError) -> String {
        switch e.code {
        case .SYNTAX: return e.math.map(mathErrorText) ?? L("v45_math_empty")
        case .UNKNOWN_SYMBOL: return L("v45_math_unknown", e.detail)
        case .OUT_OF_RANGE: return L("v45_param_out_of_range", e.detail)
        case .NO_MATCH: return L("v45_param_no_match")
        case .NOT_NUMBER: return e.math.map(mathErrorText) ?? L("v45_math_div_zero")
        case .CHEMISTRY: return chemErrorText(e.detail)
        }
    }
}
