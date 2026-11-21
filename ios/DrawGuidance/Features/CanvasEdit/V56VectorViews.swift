import SwiftUI

private func v56Chip(_ label: String, _ on: Bool, _ id: String, _ action: @escaping () -> Void) -> some View {
    Button(action: action) {
        Text(label).font(.footnote.weight(.medium)).lineLimit(1)
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(Capsule().fill(on ? Color.accentColor.opacity(0.22) : Color.secondary.opacity(0.10)))
    }
    .buttonStyle(.plain)
    .accessibilityIdentifier(id)
}

private func v56Row<C: View>(@ViewBuilder _ content: () -> C) -> some View {
    ScrollView(.horizontal, showsIndicators: false) { HStack(spacing: 6) { content() } }
}

func repeatLabel(_ k: RepeatKind) -> String { L("v56_repeat_" + k.rawValue.lowercased()) }

/// v56 aşama 8A Nesne menüsündeki canlı tekrar (Android `V56RepeatSection`).
struct V56RepeatSection: View {
    @ObservedObject var vm: EditorViewModel
    @State private var open: RepeatSpec?

    var body: some View {
        let current = vm.selectedRepeat()
        v56Row {
            ForEach(RepeatKind.allCases, id: \.self) { k in
                v56Chip(repeatLabel(k), current?.kind == k, "v56_repeat_\(k.rawValue.lowercased())") {
                    open = current?.kind == k ? current : RepeatSpec(kind: k, count: k == .RADIAL ? 6 : 4)
                }
            }
            if current != nil { v56Chip(L("v56_repeat_remove"), false, "v56_repeat_remove") { vm.removeRepeat() } }
        }
        .sheet(item: Binding(get: { open.map { RepeatItem(spec: $0) } }, set: { open = $0?.spec })) { item in
            V56RepeatSheet(vm: vm, initial: item.spec)
        }
    }
}

private struct RepeatItem: Identifiable { let spec: RepeatSpec; var id: String { spec.kind.rawValue } }

/// Tekrar ayarları (önizlemeli): Uygula tek geri alma adımı; İptal belgeyi değiştirmez.
struct V56RepeatSheet: View {
    @ObservedObject var vm: EditorViewModel
    let initial: RepeatSpec
    @Environment(\.dismiss) private var dismiss
    @State private var spec: RepeatSpec
    @State private var applied = false

    init(vm: EditorViewModel, initial: RepeatSpec) {
        self.vm = vm; self.initial = initial; _spec = State(initialValue: initial)
    }

    private func slider(_ label: String, _ value: Float, _ lo: Float, _ hi: Float, _ id: String, _ set: @escaping (Float) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.footnote)
            Slider(value: Binding(get: { Double(value) }, set: { set(Float($0)) }), in: Double(lo)...Double(hi)).accessibilityIdentifier(id)
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                switch spec.kind {
                case .LINEAR:
                    slider(L("v56_repeat_count", spec.count), Float(spec.count), 1, 40, "v56_repeat_count") { spec.count = Int($0.rounded()) }
                    slider(L("v56_repeat_dx", Int(spec.dx)), spec.dx, -400, 400, "v56_repeat_dx") { spec.dx = $0.rounded() }
                    slider(L("v56_repeat_dy", Int(spec.dy)), spec.dy, -400, 400, "v56_repeat_dy") { spec.dy = $0.rounded() }
                case .GRID:
                    slider(L("v56_repeat_cols", spec.cols), Float(spec.cols), 1, 20, "v56_repeat_cols") { spec.cols = Int($0.rounded()) }
                    slider(L("v56_repeat_rows", spec.rows), Float(spec.rows), 1, 20, "v56_repeat_rows") { spec.rows = Int($0.rounded()) }
                    slider(L("v56_repeat_dx", Int(spec.gapX)), spec.gapX, 0, 200, "v56_repeat_gapx") { spec.gapX = $0.rounded() }
                    slider(L("v56_repeat_dy", Int(spec.gapY)), spec.gapY, 0, 200, "v56_repeat_gapy") { spec.gapY = $0.rounded() }
                case .RADIAL:
                    slider(L("v56_repeat_count", spec.count), Float(spec.count), 2, 36, "v56_repeat_count") { spec.count = Int($0.rounded()) }
                    slider(L("v56_repeat_radius", Int(spec.radius)), spec.radius, 0, 800, "v56_repeat_radius") { spec.radius = $0.rounded() }
                case .MIRROR:
                    slider(L("v56_repeat_angle", Int(spec.angle)), spec.angle, -90, 90, "v56_repeat_angle") { spec.angle = $0.rounded() }
                    slider(L("v56_repeat_dx", Int(spec.gapX)), spec.gapX, 0, 300, "v56_repeat_gapx") { spec.gapX = $0.rounded() }
                case .STEP:
                    slider(L("v56_repeat_count", spec.count), Float(spec.count), 1, 40, "v56_repeat_count") { spec.count = Int($0.rounded()) }
                    slider(L("v56_repeat_dx", Int(spec.dx)), spec.dx, -400, 400, "v56_repeat_dx") { spec.dx = $0.rounded() }
                    slider(L("v56_repeat_dy", Int(spec.dy)), spec.dy, -400, 400, "v56_repeat_dy") { spec.dy = $0.rounded() }
                    slider(L("v56_repeat_angle", Int(spec.angle)), spec.angle, -180, 180, "v56_repeat_angle") { spec.angle = $0.rounded() }
                    slider(L("v56_repeat_scale", Int((spec.scale * 100).rounded())), spec.scale, 0.5, 1.5, "v56_repeat_scale") { spec.scale = ($0 * 100).rounded() / 100 }
                }
                Text(L("v56_repeat_live_hint")).font(.caption).foregroundStyle(.secondary)
            }
            .navigationTitle(repeatLabel(spec.kind))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("cancel_button")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("ok_button")) { applied = true; _ = vm.applyRepeat(spec); dismiss() }.accessibilityIdentifier("v56_repeat_apply")
                }
            }
        }
        .presentationDetents([.medium, .large])
        .onChangeCompat(of: spec, initial: true) { _, new in vm.previewRepeat(new) }
        .onDisappear { if !applied { vm.cancelRepeatPreview() } }
        .accessibilityIdentifier("v56_repeat_dialog")
    }
}

/// Özellikler'de gelişmiş görünüm (yalnız Resim; Android `V56AppearanceSection`). [shape] false: yalnız karışım modu.
struct V56AppearanceView: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    let shape: Bool
    @State private var pickedFill = -1
    @State private var pickedStroke = -1
    @State private var pickedPoint = 0

    private func update(_ coalesce: String? = nil, _ f: @escaping (VectorStyle) -> VectorStyle) { vm.updateVectorStyle(coalesce: coalesce, f) }

    var body: some View {
        let st = vm.selectedVectorStyle()
        PropSection(title: L("v56_appearance")) {
            Menu {
                ForEach(LayerBlend.allCases, id: \.self) { b in
                    Button(layerBlendLabel(b)) { update { var s = $0; s.blend = b; return s } }
                }
            } label: {
                Text(L("v56_object_blend") + ": " + layerBlendLabel(st.blend)).font(.footnote)
            }
            .accessibilityIdentifier("v56_obj_blend")
            if shape { shapeOptions(st) }
        }
        .accessibilityIdentifier("v56_appearance")
    }

    @ViewBuilder private func shapeOptions(_ st: VectorStyle) -> some View {
        Text(L("v56_cap")).font(.caption).foregroundStyle(.secondary)
        v56Row {
            ForEach([(CapStyle.ROUND, "v56_cap_round"), (.BUTT, "v56_cap_butt"), (.SQUARE, "v56_cap_square")], id: \.0) { c, key in
                v56Chip(L(key), st.cap == c, "v56_cap_\(c.rawValue.lowercased())") { update { var s = $0; s.cap = c; return s } }
            }
        }
        Text(L("v56_join")).font(.caption).foregroundStyle(.secondary)
        v56Row {
            ForEach([(JoinStyle.ROUND, "v56_join_round"), (.MITER, "v56_join_miter"), (.BEVEL, "v56_join_bevel")], id: \.0) { j, key in
                v56Chip(L(key), st.join == j, "v56_join_\(j.rawValue.lowercased())") { update { var s = $0; s.join = j; return s } }
            }
        }
        if st.join == .MITER {
            PropSlider(label: L("v56_miter"), value: Binding(get: { Double(st.miterLimit) }, set: { v in update("miter") { var s = $0; s.miterLimit = Float(v).rounded(); return s } }),
                       range: 1...20, id: "v56_miter")
        }
        Text(L("v56_stroke_align")).font(.caption).foregroundStyle(.secondary)
        v56Row {
            ForEach([(StrokeAlign.CENTER, "v56_align_center"), (.INSIDE, "v56_align_inside"), (.OUTSIDE, "v56_align_outside")], id: \.0) { a, key in
                v56Chip(L(key), st.align == a, "v56_stroke_align_\(a.rawValue.lowercased())") { update { var s = $0; s.align = a; return s } }
            }
        }
        let dash = st.dash ?? []
        v56Chip(L("v56_dash_on"), !dash.isEmpty, "v56_dash_toggle") { update { var s = $0; s.dash = dash.isEmpty ? [12, 8] : nil; return s } }
        if dash.count >= 2 {
            PropSlider(label: L("v56_dash"), value: Binding(get: { Double(dash[0]) }, set: { v in update("dash") { var s = $0; s.dash = [Float(v).rounded(), dash[1]]; return s } }), range: 1...80, id: "v56_dash")
            PropSlider(label: L("v56_gap"), value: Binding(get: { Double(dash[1]) }, set: { v in update("gap") { var s = $0; s.dash = [dash[0], Float(v).rounded()]; return s } }), range: 1...80, id: "v56_gap")
        }
        ForEach([true, false], id: \.self) { start in
            Text(L(start ? "v56_arrow_start" : "v56_arrow_end")).font(.caption).foregroundStyle(.secondary)
            v56Row {
                ForEach(ArrowKind.allCases, id: \.self) { k in
                    v56Chip(k == .NONE ? L("v56_none") : L("v56_arrow_" + k.rawValue.lowercased()), (start ? st.arrowStart : st.arrowEnd) == k,
                            "v56_arrow_\(start ? "s" : "e")_\(k.rawValue.lowercased())") {
                        update { var s = $0; if start { s.arrowStart = k } else { s.arrowEnd = k }; return s }
                    }
                }
            }
        }
        Text(L("v56_pattern_fill")).font(.caption).foregroundStyle(.secondary)
        v56Row {
            v56Chip(L("v56_none"), st.patternId == nil, "v56_pattern_none") { update { var s = $0; s.patternId = nil; return s } }
            ForEach(V56Textures.BUILTIN, id: \.self) { id in
                v56Chip(textureLabel(id), st.patternId == id, "v56_pattern_\(id.replacingOccurrences(of: V56Textures.PREFIX, with: ""))") { update { var s = $0; s.patternId = id; return s } }
            }
        }
        if st.patternId != nil {
            PropSlider(label: L("v56_pattern_scale"), value: Binding(get: { Double(st.patternScale) }, set: { v in update("pscale") { var s = $0; s.patternScale = (Float(v) * 10).rounded() / 10; return s } }),
                       range: Double(V56Textures.MIN_SCALE)...Double(V56Textures.MAX_SCALE), format: { String(format: "%.1f×", $0) }, id: "v56_pattern_scale")
        }
        freeGradient(st)
        layerList(L("v56_extra_fills"), st.fills, stroke: false, picked: $pickedFill) { list in update { var s = $0; s.fills = list; return s } }
        layerList(L("v56_extra_strokes"), st.strokes, stroke: true, picked: $pickedStroke) { list in update { var s = $0; s.strokes = list; return s } }
    }

    @ViewBuilder private func freeGradient(_ st: VectorStyle) -> some View {
        let pts = st.freeGradient
        v56Chip(L("v56_free_gradient"), pts != nil, "v56_free_gradient") {
            update { var s = $0
                s.freeGradient = pts != nil ? nil : [GradientPoint(x: 0.1, y: 0.1, colorHex: "#E53935"), GradientPoint(x: 0.9, y: 0.2, colorHex: "#FDD835"), GradientPoint(x: 0.5, y: 0.9, colorHex: "#1E88E5")]
                return s }
        }
        if let pts {
            let i = min(max(pickedPoint, 0), pts.count - 1)
            v56Row {
                ForEach(Array(pts.enumerated()), id: \.offset) { j, p in
                    Circle().fill(Color(hex: p.colorHex) ?? .black).frame(width: 26, height: 26)
                        .overlay(Circle().stroke(j == i ? Color.accentColor : Color.secondary, lineWidth: j == i ? 3 : 1))
                        .onTapGesture { pickedPoint = j }.accessibilityIdentifier("v56_fg_point_\(j)")
                }
                if pts.count < 6 { v56Chip(L("v56_add"), false, "v56_fg_add") { update { var s = $0; s.freeGradient = pts + [GradientPoint(x: 0.5, y: 0.5, colorHex: "#FFFFFF")]; return s } } }
                if pts.count > 2 { v56Chip(L("v56_remove"), false, "v56_fg_remove") { pickedPoint = 0; update { var s = $0; var l = pts; l.remove(at: i); s.freeGradient = l; return s } } }
            }
            let p = pts[i]
            ColorRow(hex: p.colorHex, id: "v56_fg_color") { h in update { var s = $0; var l = pts; l[i].colorHex = h; s.freeGradient = l; return s } }
            PropSlider(label: "X", value: Binding(get: { Double(p.x) }, set: { v in update("fgx") { var s = $0; var l = pts; l[i].x = Float(v); s.freeGradient = l; return s } }), range: 0...1, format: { "\(Int($0 * 100))" }, id: "v56_fg_x")
            PropSlider(label: "Y", value: Binding(get: { Double(p.y) }, set: { v in update("fgy") { var s = $0; var l = pts; l[i].y = Float(v); s.freeGradient = l; return s } }), range: 0...1, format: { "\(Int($0 * 100))" }, id: "v56_fg_y")
        }
    }

    @ViewBuilder private func layerList(_ title: String, _ list: [PaintLayer], stroke: Bool, picked: Binding<Int>, _ onChange: @escaping ([PaintLayer]) -> Void) -> some View {
        let tag = stroke ? "v56_strokes" : "v56_fills"
        Text(title).font(.caption).foregroundStyle(.secondary)
        v56Row {
            ForEach(Array(list.enumerated()), id: \.offset) { j, l in
                Circle().fill(Color(hex: l.colorHex) ?? .black).frame(width: 26, height: 26)
                    .overlay(Circle().stroke(j == picked.wrappedValue ? Color.accentColor : Color.secondary, lineWidth: j == picked.wrappedValue ? 3 : 1))
                    .onTapGesture { picked.wrappedValue = picked.wrappedValue == j ? -1 : j }.accessibilityIdentifier("\(tag)_\(j)")
            }
            if list.count < 6 {
                v56Chip(L("v56_add"), false, "\(tag)_add") {
                    onChange(list + [PaintLayer(colorHex: stroke ? "#FFFFFF" : "#1E88E5", width: stroke ? 12 : 4, opacity: stroke ? 1 : 0.5)]); picked.wrappedValue = list.count
                }
            }
        }
        if list.indices.contains(picked.wrappedValue) {
            let i = picked.wrappedValue, l = list[i]
            ColorRow(hex: l.colorHex, id: "\(tag)_color") { h in var n = list; n[i].colorHex = h; onChange(n) }
            if stroke {
                PropSlider(label: L("v53_line_width"), value: Binding(get: { Double(l.width) }, set: { v in var n = list; n[i].width = Float(v).rounded(); onChange(n) }), range: 1...60, id: "\(tag)_width")
            }
            PropSlider(label: L("brush_opacity_label"), value: Binding(get: { Double(l.opacity) }, set: { v in var n = list; n[i].opacity = Float(v); onChange(n) }), range: 0.05...1, format: { "\(Int($0 * 100))%" }, id: "\(tag)_opacity")
            v56Row {
                if i > 0 { v56Chip("↑", false, "\(tag)_up") { var n = list; n.swapAt(i, i - 1); onChange(n); picked.wrappedValue = i - 1 } }
                if i < list.count - 1 { v56Chip("↓", false, "\(tag)_down") { var n = list; n.swapAt(i, i + 1); onChange(n); picked.wrappedValue = i + 1 } }
                v56Chip(L("v56_remove"), false, "\(tag)_remove") { var n = list; n.remove(at: i); onChange(n); picked.wrappedValue = -1 }
            }
        }
    }
}
