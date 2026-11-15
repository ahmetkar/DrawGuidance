import SwiftUI

/// v52 not almada tablo pencereleri (Android `V52TableSheets`): "Tablo ekle" ve seçili tablonun düzenleme penceresi.
private func presetName(_ id: String) -> String {
    switch id {
    case "blue": return L("v52_table_preset_blue")
    case "green": return L("v52_table_preset_green")
    case "orange": return L("v52_table_preset_orange")
    case "purple": return L("v52_table_preset_purple")
    case "minimal": return L("v52_table_preset_minimal")
    default: return L("v52_table_preset_classic")
    }
}

private func borderName(_ id: String) -> String {
    switch id {
    case "outer": return L("v52_table_border_outer")
    case "rows": return L("v52_table_border_rows")
    case "header": return L("v52_table_border_header")
    case "none": return L("v52_table_border_none")
    default: return L("v52_table_border_all")
    }
}

private func hexColor(_ hex: String) -> Color { Color(hex: hex) ?? .white }

/// Renk satırı; [allowDefault] ise başta "Varsayılan" (ayarı kaldırır, nil).
private struct Swatches: View {
    let label: String
    let selected: String?
    let allowDefault: Bool
    let onPick: (String?) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.subheadline).foregroundStyle(.secondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    if allowDefault {
                        Button(L("v52_table_default")) { onPick(nil) }.buttonStyle(.bordered).tint(selected == nil ? .accentColor : .secondary)
                    }
                    ForEach(TableShape.palette, id: \.self) { hex in
                        let on = selected?.caseInsensitiveCompare(hex) == .orderedSame
                        Circle().fill(hexColor(hex)).frame(width: 30, height: 30)
                            .overlay(Circle().stroke(on ? Color.accentColor : Color.secondary.opacity(0.5), lineWidth: on ? 3 : 1))
                            .onTapGesture { onPick(hex) }
                    }
                }.padding(.vertical, 2)
            }
        }
    }
}

private struct AlignRow: View {
    let selected: String?
    let allowDefault: Bool
    let onPick: (String?) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L("v52_table_align")).font(.subheadline).foregroundStyle(.secondary)
            HStack(spacing: 8) {
                if allowDefault { chip(L("v52_table_default"), nil) }
                chip(L("v52_table_align_l"), "l")
                chip(L("v52_table_align_c"), "c")
                chip(L("v52_table_align_r"), "r")
            }
        }
    }

    private func chip(_ t: String, _ v: String?) -> some View {
        Button(t) { onPick(v) }.buttonStyle(.bordered).tint(selected == v ? .accentColor : .secondary)
            .accessibilityIdentifier("table_align_\(v ?? "default")")
    }
}

/// Küçük tablo önizlemesi.
private struct TablePreview: View {
    let p: [String: String]

    var body: some View {
        Canvas { ctx, size in
            let nr = TableShape.rows(p), nc = TableShape.cols(p)
            let cw = size.width / CGFloat(nc), rh = min(size.height / CGFloat(nr), 28)
            let h = rh * CGFloat(nr)
            for r in 0..<nr { for c in 0..<nc {
                ctx.fill(Path(CGRect(x: CGFloat(c) * cw, y: CGFloat(r) * rh, width: cw, height: rh)), with: .color(hexColor(TableShape.fillOf(p, r, c))))
            } }
            let style = p["border"] ?? "all"
            let bc = hexColor(p["bc"] ?? "#455A64")
            let header = TableShape.flag(p, "hr", true)
            guard style != "none" else { return }
            func line(_ a: CGPoint, _ b: CGPoint, _ w: CGFloat) { var path = Path(); path.move(to: a); path.addLine(to: b); ctx.stroke(path, with: .color(bc), lineWidth: w) }
            if style != "rows" { ctx.stroke(Path(CGRect(x: 0, y: 0, width: size.width, height: h)), with: .color(bc), lineWidth: 1.5) }
            else { line(.zero, CGPoint(x: size.width, y: 0), 1.5); line(CGPoint(x: 0, y: h), CGPoint(x: size.width, y: h), 1.5) }
            if nr > 1 { for r in 1..<nr where style == "all" || style == "rows" || (r == 1 && header && style != "outer") {
                line(CGPoint(x: 0, y: CGFloat(r) * rh), CGPoint(x: size.width, y: CGFloat(r) * rh), r == 1 && header ? 2 : 0.7)
            } }
            if style == "all" && nc > 1 { for c in 1..<nc { line(CGPoint(x: CGFloat(c) * cw, y: 0), CGPoint(x: CGFloat(c) * cw, y: h), 0.7) } }
        }
    }
}

/// "Tablo ekle" penceresi.
struct TableCreateSheet: View {
    let onCreate: ([String: String]) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var rows = 4
    @State private var cols = 3
    @State private var header = true
    @State private var headerCol = false
    @State private var zebra = true
    @State private var border = "all"
    @State private var preset = "classic"

    private var params: [String: String] {
        TableShape.create(rows: rows, cols: cols, header: header, headerCol: headerCol, zebra: zebra, borderStyle: border,
                          preset: TableShape.presets.first { $0.id == preset } ?? TableShape.presets[0])
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TablePreview(p: params).frame(height: 140)
                } footer: { Text(L("v52_table_create_hint")) }
                Section {
                    Stepper(value: $rows, in: 1...TableShape.MAX_ROWS) { LabeledContent(L("v52_table_rows"), value: "\(rows)") }
                        .accessibilityIdentifier("table_rows")
                    Stepper(value: $cols, in: 1...TableShape.MAX_COLS) { LabeledContent(L("v52_table_cols"), value: "\(cols)") }
                        .accessibilityIdentifier("table_cols")
                    Toggle(L("v52_table_header_row"), isOn: $header).accessibilityIdentifier("table_header_row")
                    Toggle(L("v52_table_header_col"), isOn: $headerCol).accessibilityIdentifier("table_header_col")
                    Toggle(L("v52_table_zebra"), isOn: $zebra).accessibilityIdentifier("table_zebra")
                }
                Section(L("v52_table_borders")) {
                    Picker(L("v52_table_borders"), selection: $border) {
                        ForEach(TableShape.borderStyles, id: \.self) { Text(borderName($0)).tag($0) }
                    }
                    .pickerStyle(.inline).labelsHidden()
                }
                Section(L("v52_table_style")) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(TableShape.presets) { pr in
                                Button(presetName(pr.id)) { preset = pr.id; if pr.id == "minimal" { border = "rows" } }
                                    .buttonStyle(.bordered).tint(preset == pr.id ? .accentColor : .secondary)
                                    .accessibilityIdentifier("table_preset_\(pr.id)")
                            }
                        }
                    }
                }
                Section {
                    Button { onCreate(params) } label: { Text(L("v52_table_insert")).frame(maxWidth: .infinity) }
                        .buttonStyle(.borderedProminent).accessibilityIdentifier("table_insert")
                }
            }
            .navigationTitle(L("v52_add_table"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("cancel_button")) { dismiss() } } }
        }
    }
}

/// Seçili tablonun düzenleme penceresi.
struct TableEditSheet: View {
    @ObservedObject var ctl: TableController
    @ObservedObject var store: EditorStore
    @Environment(\.dismiss) private var dismiss
    @State private var scopeRow = -1
    @State private var scopeCol = -1

    var body: some View {
        let p = ctl.params
        let nr = TableShape.rows(p), nc = TableShape.cols(p)
        NavigationStack {
            Form {
                Section {
                    ScrollView([.horizontal, .vertical]) {
                        VStack(spacing: 0) {
                            HStack(spacing: 0) {
                                header("", selected: false, width: 36) { scopeRow = -1; scopeCol = -1 }
                                ForEach(0..<nc, id: \.self) { c in
                                    header(TableShape.colName(c), selected: scopeCol == c, width: 120) { scopeCol = c; scopeRow = -1 }
                                        .accessibilityIdentifier("table_col_\(c)")
                                }
                            }
                            ForEach(0..<nr, id: \.self) { r in
                                HStack(spacing: 0) {
                                    header("\(r + 1)", selected: scopeRow == r, width: 36) { scopeRow = r; scopeCol = -1 }
                                        .accessibilityIdentifier("table_row_\(r)")
                                    ForEach(0..<nc, id: \.self) { c in cellField(p, r, c) }
                                }
                            }
                        }
                    }
                    .frame(maxHeight: 300)
                } footer: { Text(L("v52_table_edit_hint")) }
                Section {
                    HStack(spacing: 8) {
                        Button(L("v52_table_scope_all")) { scopeRow = -1; scopeCol = -1 }
                            .buttonStyle(.bordered).tint(scopeRow < 0 && scopeCol < 0 ? .accentColor : .secondary)
                            .accessibilityIdentifier("table_scope_all")
                        if scopeRow >= 0 && scopeRow < nr { Text(String(format: L("v52_table_scope_row"), scopeRow + 1)).font(.subheadline.weight(.semibold)) }
                        if scopeCol >= 0 && scopeCol < nc { Text(String(format: L("v52_table_scope_col"), TableShape.colName(scopeCol))).font(.subheadline.weight(.semibold)) }
                    }
                }
                if scopeRow >= 0 && scopeRow < nr { lineSettings(p, isRow: true, index: scopeRow) }
                else if scopeCol >= 0 && scopeCol < nc { lineSettings(p, isRow: false, index: scopeCol) }
                else { allSettings(p) }
            }
            .navigationTitle(L("v52_table_edit"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("close")) { dismiss() } } }
        }
        .accessibilityIdentifier("table_edit_sheet")
    }

    private func header(_ t: String, selected: Bool, width: CGFloat, _ action: @escaping () -> Void) -> some View {
        Text(t).font(.subheadline.weight(.semibold)).foregroundStyle(selected ? Color.accentColor : .primary)
            .frame(width: width, height: t.first?.isLetter == true || t.isEmpty ? 30 : 42)
            .background(selected ? Color.accentColor.opacity(0.18) : Color.secondary.opacity(0.08))
            .overlay(Rectangle().stroke(Color.secondary.opacity(0.3), lineWidth: 0.5))
            .contentShape(Rectangle())
            .onTapGesture(perform: action)
    }

    private func cellField(_ p: [String: String], _ r: Int, _ c: Int) -> some View {
        let hl = scopeRow == r || scopeCol == c
        let align = TableShape.alignOf(p, r, c)
        let id = ctl.editing ?? ""
        return TextField("", text: Binding(
            get: { TableShape.cell(ctl.params, r, c) },
            set: { v in ctl.update(coalesce: "table-\(id)-cell-\(r)-\(c)") { TableShape.setCell($0, r, c, v.replacingOccurrences(of: "\n", with: " ")) } }
        ))
        .font(.system(size: 14, weight: TableShape.boldOf(p, r, c) ? .bold : .regular))
        .foregroundStyle(hexColor(TableShape.textOf(p, r, c)))
        .multilineTextAlignment(align == "c" ? .center : align == "r" ? .trailing : .leading)
        .padding(.horizontal, 8)
        .frame(width: 120, height: 42)
        .background(hexColor(TableShape.fillOf(p, r, c)))
        .overlay(Rectangle().stroke(hl ? Color.accentColor : Color.gray.opacity(0.35), lineWidth: hl ? 2 : 0.5))
        .accessibilityIdentifier("table_cell_\(r)_\(c)")
    }

    @ViewBuilder private func allSettings(_ p: [String: String]) -> some View {
        let id = ctl.editing ?? ""
        Section {
            Stepper(value: Binding(get: { TableShape.rows(ctl.params) }, set: { n in ctl.update { TableShape.resize($0, rows: n, cols: TableShape.cols($0)) } }),
                    in: 1...TableShape.MAX_ROWS) { LabeledContent(L("v52_table_rows"), value: "\(TableShape.rows(p))") }
            Stepper(value: Binding(get: { TableShape.cols(ctl.params) }, set: { n in ctl.update { TableShape.resize($0, rows: TableShape.rows($0), cols: n) } }),
                    in: 1...TableShape.MAX_COLS) { LabeledContent(L("v52_table_cols"), value: "\(TableShape.cols(p))") }
            Toggle(L("v52_table_header_row"), isOn: flagBinding("hr", true))
            Toggle(L("v52_table_header_col"), isOn: flagBinding("hc", false))
            Toggle(L("v52_table_zebra"), isOn: flagBinding("zebra", false))
        }
        Section(L("v52_table_borders")) {
            Picker(L("v52_table_borders"), selection: Binding(get: { ctl.params["border"] ?? "all" }, set: { b in ctl.update { m in var x = m; x["border"] = b; return x } })) {
                ForEach(TableShape.borderStyles, id: \.self) { Text(borderName($0)).tag($0) }
            }
            .pickerStyle(.menu)
            VStack(alignment: .leading) {
                Text(L("v52_table_line_width")).font(.subheadline).foregroundStyle(.secondary)
                Slider(value: Binding(get: { Double(ctl.params["bw"].flatMap { Float($0) } ?? 1) },
                                      set: { v in ctl.update(coalesce: "table-\(id)-bw") { m in var x = m; x["bw"] = String(format: "%.2f", v); return x } }), in: 0.25...4)
            }
            Swatches(label: L("v52_table_border_color"), selected: p["bc"], allowDefault: false) { v in ctl.update { TableShape.setGlobal($0, "bc", v) } }
        }
        Section(L("v52_table_style")) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(TableShape.presets) { pr in
                        Button(presetName(pr.id)) { ctl.update { m in var x = m; TableShape.applyPreset(&x, pr); return x } }.buttonStyle(.bordered)
                    }
                }
            }
            AlignRow(selected: p["al"] ?? "l", allowDefault: false) { v in ctl.update { TableShape.setGlobal($0, "al", v, clearProp: "a") } }
            VStack(alignment: .leading) {
                Text(L("v52_table_text_size")).font(.subheadline).foregroundStyle(.secondary)
                Slider(value: Binding(get: { Double(ctl.params["fs"].flatMap { Float($0) } ?? 0.42) },
                                      set: { v in ctl.update(coalesce: "table-\(id)-fs") { m in var x = m; x["fs"] = String(format: "%.2f", v); return x } }), in: 0.2...0.8)
            }
            Swatches(label: L("v52_table_bg"), selected: p["bg"], allowDefault: false) { v in ctl.update { TableShape.setGlobal($0, "bg", v, clearProp: "f") } }
            Swatches(label: L("v52_table_text_color"), selected: p["tc"], allowDefault: false) { v in ctl.update { TableShape.setGlobal($0, "tc", v, clearProp: "t") } }
            Swatches(label: L("v52_table_header_fill"), selected: p["hf"], allowDefault: false) { v in ctl.update { TableShape.setGlobal($0, "hf", v) } }
            Swatches(label: L("v52_table_header_text"), selected: p["ht"], allowDefault: false) { v in ctl.update { TableShape.setGlobal($0, "ht", v) } }
            Swatches(label: L("v52_table_zebra_fill"), selected: p["zf"], allowDefault: false) { v in ctl.update { TableShape.setGlobal($0, "zf", v) } }
        }
        Section {
            Button(L("v52_table_reset_lines")) { ctl.update { $0.filter { !($0.key.hasPrefix("R.") || $0.key.hasPrefix("C.")) } } }
                .accessibilityIdentifier("table_reset_lines")
        }
    }

    private func flagBinding(_ key: String, _ def: Bool) -> Binding<Bool> {
        Binding(get: { TableShape.flag(ctl.params, key, def) }, set: { v in ctl.update { m in var x = m; x[key] = v ? "1" : "0"; return x } })
    }

    @ViewBuilder private func lineSettings(_ p: [String: String], isRow: Bool, index: Int) -> some View {
        let id = ctl.editing ?? ""
        let prop: (String) -> String? = { name in isRow ? TableShape.rowProp(p, name, index) : TableShape.colProp(p, name, index) }
        let set: (String, String?) -> Void = { name, v in ctl.update { TableShape.setLineProp($0, isRow: isRow, name, index, v) } }
        let sizeKey = isRow ? "h" : "w"
        let headerLine = (isRow && index == 0 && TableShape.flag(p, "hr", true)) || (!isRow && index == 0 && TableShape.flag(p, "hc"))
        Section {
            VStack(alignment: .leading) {
                Text(L(isRow ? "v52_table_height" : "v52_table_width")).font(.subheadline).foregroundStyle(.secondary)
                Slider(value: Binding(get: { Double((isRow ? TableShape.rowProp(ctl.params, sizeKey, index) : TableShape.colProp(ctl.params, sizeKey, index)).flatMap { Float($0) } ?? 1) },
                                      set: { v in ctl.update(coalesce: "table-\(id)-\(sizeKey)-\(index)") { TableShape.setLineProp($0, isRow: isRow, sizeKey, index, String(format: "%.2f", v)) } }),
                       in: 0.4...4)
            }
            Toggle(L("v52_table_bold"), isOn: Binding(get: { prop("b").map { $0 == "1" } ?? headerLine }, set: { set("b", $0 ? "1" : "0") }))
                .accessibilityIdentifier("table_line_bold")
            AlignRow(selected: prop("a"), allowDefault: true) { set("a", $0) }
            Swatches(label: L("v52_table_fill"), selected: prop("f"), allowDefault: true) { set("f", $0) }
            Swatches(label: L("v52_table_text_color"), selected: prop("t"), allowDefault: true) { set("t", $0) }
        }
        Section {
            if isRow {
                Button(L("v52_table_add_above")) { ctl.update { TableShape.insertRow($0, index) }; scopeRow = index + 1 }
                Button(L("v52_table_add_below")) { ctl.update { TableShape.insertRow($0, index + 1) } }
                Button(L("v52_table_delete_row"), role: .destructive) { ctl.update { TableShape.deleteRow($0, index) }; scopeRow = -1 }
                    .disabled(TableShape.rows(p) <= 1)
            } else {
                Button(L("v52_table_add_left")) { ctl.update { TableShape.insertCol($0, index) }; scopeCol = index + 1 }
                Button(L("v52_table_add_right")) { ctl.update { TableShape.insertCol($0, index + 1) } }
                Button(L("v52_table_delete_col"), role: .destructive) { ctl.update { TableShape.deleteCol($0, index) }; scopeCol = -1 }
                    .disabled(TableShape.cols(p) <= 1)
            }
            Button(L("v52_table_clear")) {
                ctl.update { m in m.filter { k, _ in
                    let parts = k.split(separator: ".").map(String.init)
                    return !(parts.count == 3 && parts[0] == "T" && (isRow ? parts[1] : parts[2]) == String(index))
                } }
            }
        }
    }
}

/// v52 tablo pencerelerinin sunucusu (düzenleyici ekranına bağlanır).
struct TableSheetsHost: ViewModifier {
    @ObservedObject var ctl: TableController
    let store: EditorStore
    let viewport: ViewportState

    func body(content: Content) -> some View {
        content
            .sheet(isPresented: $ctl.createOpen) {
                TableCreateSheet { params in
                    let center = viewport.toCanvas(P(viewport.viewSize.w / 2, viewport.viewSize.h / 2))
                    ctl.create(params, center: center)
                }
            }
            .sheet(isPresented: Binding(get: { ctl.editing != nil }, set: { if !$0 { ctl.closeEdit() } })) {
                TableEditSheet(ctl: ctl, store: store)
            }
    }
}
