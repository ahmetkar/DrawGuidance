import SwiftUI

/// v56 aşama 7 (2D-4) renk ayarı denetimleri (Android `V56LayerColorSection.kt`): Ton/Doygunluk, Renk Dengesi, Eğriler.
/// [layerId] verilirse o katmana, yoksa seçili elemanlara; kaydırırken canlı önizleme.
struct V56ColorAdjustControls: View {
    @ObservedObject var vm: EditorViewModel
    let layerId: String?
    @Binding var adj: ColorAdjust
    @State private var tab = 0
    @State private var range = 1
    @State private var channel = 0

    private func chip(_ label: String, _ on: Bool, _ id: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.footnote.weight(.medium)).lineLimit(1)
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(Capsule().fill(on ? Color.accentColor.opacity(0.22) : Color.secondary.opacity(0.10)))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }

    private func slider(_ label: String, _ value: Float, _ lo: Float, _ hi: Float, _ id: String, _ set: @escaping (Float) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("\(label) · \(Int(value.rounded()))").font(.caption).foregroundStyle(.secondary)
            Slider(value: Binding(get: { Double(value) }, set: { set(Float($0).rounded()) }), in: Double(lo)...Double(hi))
                .accessibilityIdentifier(id)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    chip(L("v56_ca_hsl"), tab == 0, "v56_ca_tab_hsl") { tab = 0 }
                    chip(L("v56_ca_balance"), tab == 1, "v56_ca_tab_balance") { tab = 1 }
                    chip(L("v56_ca_curves"), tab == 2, "v56_ca_tab_curves") { tab = 2 }
                }
            }
            switch tab {
            case 0:
                slider(L("v56_ca_hue"), adj.hue, -180, 180, "v56_ca_hue") { adj.hue = $0 }
                slider(L("v56_ca_saturation"), adj.saturation, -100, 100, "v56_ca_saturation") { adj.saturation = $0 }
                slider(L("v56_ca_lightness"), adj.lightness, -100, 100, "v56_ca_lightness") { adj.lightness = $0 }
                Toggle(L("v56_ca_colorize"), isOn: $adj.colorize).accessibilityIdentifier("v56_ca_colorize")
            case 1:
                HStack(spacing: 6) {
                    ForEach(Array(["v56_ca_shadows", "v56_ca_midtones", "v56_ca_highlights"].enumerated()), id: \.offset) { i, key in
                        chip(L(key), range == i, "v56_ca_range_\(i)") { range = i }
                    }
                }
                ForEach(Array(["v56_ca_cyan_red", "v56_ca_magenta_green", "v56_ca_yellow_blue"].enumerated()), id: \.offset) { c, key in
                    slider(L(key), adj.balance[range][c], -100, 100, "v56_ca_balance_\(c)") { adj = adj.withBalance(range, c, $0) }
                }
                Toggle(L("v56_ca_preserve"), isOn: $adj.preserveLuminosity).accessibilityIdentifier("v56_ca_preserve")
            default:
                HStack(spacing: 6) {
                    ForEach(Array(["RGB", "R", "G", "B"].enumerated()), id: \.offset) { i, label in
                        chip(label, channel == i, "v56_ca_channel_\(i)") { channel = i }
                    }
                }
                CurveEditorView(ys: adj.curves[channel], color: curveColor(channel)) { i, v in adj = adj.withCurve(channel, i, v) }
                    .frame(height: 160)
                    .accessibilityIdentifier("v56_ca_curve")
                Text(L("v56_ca_curve_hint")).font(.caption).foregroundStyle(.secondary)
            }
        }
        .onChangeCompat(of: adj, initial: true) { _, new in vm.previewColorAdjust(new, layerId: layerId) }
    }

    private func curveColor(_ c: Int) -> Color {
        switch c { case 1: return .red; case 2: return .green; case 3: return .blue; default: return .gray }
    }
}

/// 5 noktalı eğri: noktalar dikey sürüklenir (x sabit).
struct CurveEditorView: View {
    let ys: [Float]
    let color: Color
    let onSet: (Int, Float) -> Void
    @State private var drag: Int?

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width, h = geo.size.height
            Canvas { ctx, size in
                var grid = Path()
                for i in 1...3 {
                    let x = size.width * CGFloat(i) / 4, y = size.height * CGFloat(i) / 4
                    grid.move(to: CGPoint(x: x, y: 0)); grid.addLine(to: CGPoint(x: x, y: size.height))
                    grid.move(to: CGPoint(x: 0, y: y)); grid.addLine(to: CGPoint(x: size.width, y: y))
                }
                grid.move(to: CGPoint(x: 0, y: size.height)); grid.addLine(to: CGPoint(x: size.width, y: 0))
                ctx.stroke(grid, with: .color(.secondary.opacity(0.3)), lineWidth: 1)
                let lut = ColorAdjustOps.curveLut(ys)
                var curve = Path()
                for i in 0...255 {
                    let p = CGPoint(x: CGFloat(i) / 255 * size.width, y: (1 - CGFloat(lut[i]) / 255) * size.height)
                    if i == 0 { curve.move(to: p) } else { curve.addLine(to: p) }
                }
                ctx.stroke(curve, with: .color(color), lineWidth: 3)
                for (i, x) in ColorAdjust.curveX.enumerated() {
                    let c = CGPoint(x: CGFloat(x) * size.width, y: (1 - CGFloat(ys[i])) * size.height)
                    ctx.fill(Path(ellipseIn: CGRect(x: c.x - 6, y: c.y - 6, width: 12, height: 12)), with: .color(color))
                }
            }
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { g in
                if drag == nil {
                    drag = ColorAdjust.curveX.indices.min { abs(CGFloat(ColorAdjust.curveX[$0]) * w - g.startLocation.x) < abs(CGFloat(ColorAdjust.curveX[$1]) * w - g.startLocation.x) }
                }
                if let d = drag { onSet(d, Float(1 - g.location.y / max(h, 1))) }
            }.onEnded { _ in drag = nil })
        }
    }
}

/// Özellikler'deki "Katman renkleri" (eleman seçili değilken, etkin katmana; kilitli katmanda pasif).
struct V56LayerColorSection: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @State private var adj = ColorAdjust()

    var body: some View {
        let layers = store.canvas?.layers ?? [Layer.Default]
        let layer = layers.first { $0.id == store.activeLayer } ?? layers.last ?? Layer.Default
        VStack(alignment: .leading, spacing: 10) {
            Text(L("v56_layer_colors", layer.name)).font(.subheadline.weight(.semibold))
            if layer.locked {
                Text(L("v56_layer_colors_locked")).font(.caption).foregroundStyle(.secondary)
            } else {
                V56ColorAdjustControls(vm: vm, layerId: layer.id, adj: $adj)
                HStack(spacing: 8) {
                    Button(L("v56_ca_reset")) { adj = ColorAdjust(); vm.cancelColorAdjust() }
                        .buttonStyle(.bordered).frame(maxWidth: .infinity).accessibilityIdentifier("v56_ca_reset")
                    Button(L("v56_ca_apply")) { vm.applyColorAdjust(adj, layerId: layer.id); adj = ColorAdjust() }
                        .buttonStyle(.borderedProminent).frame(maxWidth: .infinity)
                        .disabled(adj.isIdentity || vm.colorApplying).accessibilityIdentifier("v56_ca_apply")
                }
            }
        }
        .onChangeCompat(of: store.activeLayer, initial: false) { _, _ in adj = ColorAdjust(); vm.cancelColorAdjust() }
        .onDisappear { vm.cancelColorAdjust() }
        .accessibilityIdentifier("v56_layer_colors")
    }
}

/// Seçimin "Renk ayarları" penceresi (resimler piksel olarak işlenir).
struct V56ColorAdjustSheet: View {
    @ObservedObject var vm: EditorViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var adj = ColorAdjust()
    @State private var applied = false

    var body: some View {
        NavigationStack {
            ScrollView { V56ColorAdjustControls(vm: vm, layerId: nil, adj: $adj).padding() }
                .navigationTitle(L("v56_color_adjust"))
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button(L("cancel_button")) { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(L("v56_ca_apply")) { applied = true; vm.applyColorAdjust(adj, layerId: nil); dismiss() }
                            .accessibilityIdentifier("v56_ca_dialog_apply")
                    }
                }
        }
        .presentationDetents([.medium, .large])
        .onDisappear { if !applied { vm.cancelColorAdjust() } }
        .accessibilityIdentifier("v56_color_adjust_dialog")
    }
}
