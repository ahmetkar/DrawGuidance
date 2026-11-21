import SwiftUI

/// v56 aşama 6 Dönüştür paneli ve Sıvılaştır arayüzü (Android `V56TransformPanel.kt`): tuval katmanı (dörtgen ya da eğri
/// ağ, köşe/kenar/döndürme tutamaçları, yaslama kılavuzları) ve alt panel.
struct V56TransformLayer: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var transform: TransformController
    @ObservedObject var liquify: LiquifyController
    @ObservedObject var viewport: ViewportState

    var body: some View {
        if transform.state != nil {
            ZStack(alignment: .bottom) {
                TransformOverlay(transform: transform, viewport: viewport)
                TransformPanel(transform: transform)
            }
        } else if liquify.target != nil {
            ZStack(alignment: .bottom) {
                LiquifyOverlay(liquify: liquify, viewport: viewport)
                LiquifyBar(liquify: liquify)
            }
        }
    }
}

private enum Grip: Equatable { case corner(Int), edge(Int), rotate, mesh(Int), move, meshInside }

private func pt(_ p: P) -> CGPoint { CGPoint(x: CGFloat(p.x), y: CGFloat(p.y)) }
private func dist(_ a: CGPoint, _ b: CGPoint) -> CGFloat { hypot(a.x - b.x, a.y - b.y) }

private func insideQuad(_ p: CGPoint, _ q: [CGPoint]) -> Bool {
    var inside = false
    var j = q.count - 1
    for i in q.indices {
        let a = q[i], b = q[j]
        if (a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x { inside.toggle() }
        j = i
    }
    return inside
}

/// Üst kenara dik, dörtgenin dışına bakan birim vektör.
private func outward(_ a: CGPoint, _ b: CGPoint, _ d: CGPoint) -> CGPoint {
    let ex = b.x - a.x, ey = b.y - a.y
    let len = max(1e-3, hypot(ex, ey))
    var n = CGPoint(x: ey / len, y: -ex / len)
    if (d.x - a.x) * n.x + (d.y - a.y) * n.y > 0 { n = CGPoint(x: -n.x, y: -n.y) }
    return n
}

private struct TransformOverlay: View {
    @ObservedObject var transform: TransformController
    @ObservedObject var viewport: ViewportState
    @State private var grip: Grip?
    @State private var last: CGPoint = .zero
    @State private var lastTime = Date()
    @State private var total = CGSize.zero
    @State private var applied = CGSize.zero

    private let hit: CGFloat = 22
    private let rotOff: CGFloat = 32

    private func screen(_ p: StrokePoint) -> CGPoint { pt(viewport.toScreen(P(p.x, p.y))) }

    var body: some View {
        Canvas { ctx, size in
            guard let s = transform.state else { return }
            let accent = Color.accentColor
            if let mesh = s.mesh {
                let n = WarpMesh.sizeOf(mesh)
                for k in 0..<n {
                    for horizontal in [true, false] {
                        var path = Path()
                        for j in 0...24 {
                            let t = Float(j) / 24, fixed = Float(k) / Float(n - 1)
                            let (x, y) = WarpMesh.eval(mesh, n, horizontal ? t : fixed, horizontal ? fixed : t)
                            let sp = pt(viewport.toScreen(P(x, y)))
                            if j == 0 { path.move(to: sp) } else { path.addLine(to: sp) }
                        }
                        ctx.stroke(path, with: .color(accent.opacity(0.8)), lineWidth: 1.5)
                    }
                }
                for p in mesh { handle(ctx, screen(p), 5, accent) }
            } else {
                let q = s.quad.map(screen)
                var path = Path(); path.addLines(q); path.closeSubpath()
                ctx.stroke(path, with: .color(accent), lineWidth: 1.5)
                let top = CGPoint(x: (q[0].x + q[1].x) / 2, y: (q[0].y + q[1].y) / 2)
                let n = outward(q[0], q[1], q[3])
                let rot = CGPoint(x: top.x + n.x * rotOff, y: top.y + n.y * rotOff)
                var stem = Path(); stem.move(to: top); stem.addLine(to: rot)
                ctx.stroke(stem, with: .color(accent), lineWidth: 1.5)
                handle(ctx, rot, 7, accent)
                q.forEach { handle(ctx, $0, 7, accent) }
                for e in 0..<4 { handle(ctx, CGPoint(x: (q[e].x + q[(e + 1) % 4].x) / 2, y: (q[e].y + q[(e + 1) % 4].y) / 2), 4.5, accent) }
            }
            let blue = Color(red: 0.18, green: 0.5, blue: 0.93)
            if let gx = transform.guides.0 {
                let x = CGFloat(viewport.toScreen(P(gx, 0)).x)
                var p = Path(); p.move(to: CGPoint(x: x, y: 0)); p.addLine(to: CGPoint(x: x, y: size.height))
                ctx.stroke(p, with: .color(blue), style: StrokeStyle(lineWidth: 1, dash: [6, 4]))
            }
            if let gy = transform.guides.1 {
                let y = CGFloat(viewport.toScreen(P(0, gy)).y)
                var p = Path(); p.move(to: CGPoint(x: 0, y: y)); p.addLine(to: CGPoint(x: size.width, y: y))
                ctx.stroke(p, with: .color(blue), style: StrokeStyle(lineWidth: 1, dash: [6, 4]))
            }
        }
        .contentShape(Rectangle())
        .gesture(DragGesture(minimumDistance: 1).onChanged(changed).onEnded { _ in grip = nil; transform.endDrag() })
        .accessibilityIdentifier("v56_transform_overlay")
    }

    private func handle(_ ctx: GraphicsContext, _ p: CGPoint, _ r: CGFloat, _ accent: Color) {
        let rect = CGRect(x: p.x - r, y: p.y - r, width: 2 * r, height: 2 * r)
        ctx.fill(Path(ellipseIn: rect), with: .color(.white))
        ctx.stroke(Path(ellipseIn: rect), with: .color(accent), lineWidth: 1.5)
    }

    private func pick(_ p: CGPoint, _ s: TransformState) -> Grip? {
        if s.kind == .WARP, let mesh = s.mesh {
            if let i = mesh.firstIndex(where: { dist(screen($0), p) < hit }) { return .mesh(i) }
            return insideQuad(p, WarpMesh.corners(mesh).map(screen)) ? .meshInside : nil
        }
        let q = s.quad.map(screen)
        let top = CGPoint(x: (q[0].x + q[1].x) / 2, y: (q[0].y + q[1].y) / 2)
        let n = outward(q[0], q[1], q[3])
        if dist(CGPoint(x: top.x + n.x * rotOff, y: top.y + n.y * rotOff), p) < hit { return .rotate }
        if let i = q.firstIndex(where: { dist($0, p) < hit }) { return .corner(i) }
        if let e = (0..<4).first(where: { dist(CGPoint(x: (q[$0].x + q[($0 + 1) % 4].x) / 2, y: (q[$0].y + q[($0 + 1) % 4].y) / 2), p) < hit }) { return .edge(e) }
        return insideQuad(p, q) ? .move : nil
    }

    private func changed(_ v: DragGesture.Value) {
        guard let s = transform.state else { return }
        if grip == nil {
            grip = pick(v.startLocation, s)
            last = v.startLocation; lastTime = Date(); total = .zero; applied = .zero
        }
        guard let g = grip else { return }
        let now = Date()
        let dt = max(0.001, now.timeIntervalSince(lastTime))
        let dScreen = CGSize(width: v.location.x - last.x, height: v.location.y - last.y)
        let speed = Float(hypot(dScreen.width, dScreen.height) / dt)
        last = v.location; lastTime = now
        let c = viewport.toCanvas(P(Float(v.location.x), Float(v.location.y)))
        let k = viewport.scale
        let dx = Float(dScreen.width) / k, dy = Float(dScreen.height) / k
        switch g {
        case .corner(let i): transform.dragCorner(i, c.x, c.y)
        case .edge(let e): transform.dragEdge(e, dx, dy)
        case .rotate:
            let cx = s.quad.reduce(0) { $0 + $1.x } / 4, cy = s.quad.reduce(0) { $0 + $1.y } / 4
            transform.rotateTo(Float(atan2(Double(c.y - cy), Double(c.x - cx)) * 180 / .pi) + 90)
        case .mesh(let i): transform.dragMesh(i, c.x, c.y)
        case .meshInside: transform.dragMeshInside(c.x, c.y, dx, dy)
        case .move:
            // Mıknatıs: toplam sürüklemenin baskın eksenine kilitli
            total.width += CGFloat(dx); total.height += CGFloat(dy)
            var target = total
            if transform.snap.magnets { target = abs(total.width) >= abs(total.height) ? CGSize(width: total.width, height: 0) : CGSize(width: 0, height: total.height) }
            let step = CGSize(width: target.width - applied.width, height: target.height - applied.height)
            applied = target
            transform.translate(Float(step.width), Float(step.height), scale: k, speed: speed)
        }
    }
}

private struct V56Chip: View {
    let label: String
    var on = false
    let id: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(.footnote.weight(.medium)).lineLimit(1)
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(Capsule().fill(on ? Color.accentColor.opacity(0.22) : Color.secondary.opacity(0.10)))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }
}

private struct PanelBox<C: View>: View {
    @ViewBuilder let content: () -> C
    var body: some View {
        VStack(alignment: .leading, spacing: 6) { content() }
            .padding(8)
            .background(RoundedRectangle(cornerRadius: 18).fill(Color(.systemBackground).opacity(0.95)))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.secondary.opacity(0.22), lineWidth: 1))
            .padding(8)
    }
}

private struct TransformPanel: View {
    @ObservedObject var transform: TransformController
    @State private var snapOpen = false
    @State private var numericOpen = false

    var body: some View {
        if let s = transform.state {
            PanelBox {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach([(TransformKind.FREE, "v56_tf_free"), (.UNIFORM, "v56_tf_uniform"), (.DISTORT, "v56_tf_distort"), (.WARP, "v56_tf_warp")], id: \.0) { k, key in
                            V56Chip(label: L(key), on: s.kind == k, id: "v56_tf_\(k.rawValue.lowercased())") { transform.setKind(k) }
                        }
                        if s.kind == .WARP {
                            V56Chip(label: L("v56_tf_advanced_mesh"), on: transform.advancedMesh, id: "v56_tf_advanced_mesh") { transform.setAdvancedMesh(!transform.advancedMesh) }
                        }
                    }
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        V56Chip(label: L("v56_tf_snapping"), on: transform.snap.snap || transform.snap.magnets, id: "v56_tf_snapping") { snapOpen = true }
                        V56Chip(label: L("v56_tf_flip_h"), id: "v56_tf_flip_h") { transform.flip(horizontal: true) }
                        V56Chip(label: L("v56_tf_flip_v"), id: "v56_tf_flip_v") { transform.flip(horizontal: false) }
                        V56Chip(label: L("v56_tf_rotate45"), id: "v56_tf_rotate45") { transform.rotateStep(45) }
                        V56Chip(label: L("v56_tf_rotate90"), id: "v56_tf_rotate90") { transform.rotateStep(90) }
                        V56Chip(label: L("v56_tf_fit"), id: "v56_tf_fit") { transform.fitToCanvas() }
                        V56Chip(label: L("v56_tf_filter", L(filterKey(s.filter))), id: "v56_tf_filter") {
                            transform.setFilter(TransformFilter.of((s.filter.code + 1) % TransformFilter.allCases.count))
                        }
                        V56Chip(label: L("v56_tf_numeric"), id: "v56_tf_numeric") { numericOpen = true }
                        V56Chip(label: L("v56_tf_reset"), id: "v56_tf_reset") { transform.reset() }
                        V56Chip(label: L("cancel_button"), id: "v56_tf_cancel") { transform.cancel() }
                        V56Chip(label: L("v56_tf_done"), on: true, id: "v56_tf_done") { transform.done() }
                    }
                }
                if transform.skipped > 0 && (s.kind == .DISTORT || s.kind == .WARP) {
                    Text(L("v56_tf_skipped", transform.skipped)).font(.caption).foregroundStyle(.secondary).accessibilityIdentifier("v56_tf_skipped")
                }
            }
            .sheet(isPresented: $snapOpen) { SnapSheet(transform: transform) }
            .sheet(isPresented: $numericOpen) { NumericSheet(transform: transform) }
        }
    }

    private func filterKey(_ f: TransformFilter) -> String {
        switch f { case .NEAREST: return "v56_tf_nearest"; case .BILINEAR: return "v56_tf_bilinear"; case .BICUBIC: return "v56_tf_bicubic" }
    }
}

private struct SnapSheet: View {
    @ObservedObject var transform: TransformController
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let p = transform.snap
        NavigationStack {
            Form {
                Toggle(isOn: Binding(get: { p.magnets }, set: { v in var n = p; n.magnets = v; transform.setSnap(n) })) {
                    VStack(alignment: .leading) {
                        Text(L("v56_tf_magnets"))
                        Text(L("v56_tf_magnets_hint")).font(.caption).foregroundStyle(.secondary)
                    }
                }.accessibilityIdentifier("v56_tf_magnets")
                Toggle(L("v56_tf_snap"), isOn: Binding(get: { p.snap }, set: { v in var n = p; n.snap = v; transform.setSnap(n) }))
                    .accessibilityIdentifier("v56_tf_snap")
                Section(L("v56_tf_snap_distance") + " · \(Int(p.distance.rounded()))") {
                    Slider(value: Binding(get: { Double(p.distance) }, set: { v in var n = p; n.distance = Float(v); transform.setSnap(n) }), in: 2...40)
                        .accessibilityIdentifier("v56_tf_snap_distance")
                }
                Section(L("v56_tf_snap_speed") + " · \(Int(p.speed.rounded()))") {
                    Slider(value: Binding(get: { Double(p.speed) }, set: { v in var n = p; n.speed = Float(v); transform.setSnap(n) }), in: 0...6000)
                        .accessibilityIdentifier("v56_tf_snap_speed")
                }
            }
            .navigationTitle(L("v56_tf_snapping"))
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("close")) { dismiss() } } }
        }
        .presentationDetents([.medium])
    }
}

private struct NumericSheet: View {
    @ObservedObject var transform: TransformController
    @Environment(\.dismiss) private var dismiss
    @State private var values: [String] = []

    private let keys = ["v56_tf_x", "v56_tf_y", "v56_tf_w", "v56_tf_h", "v56_tf_angle"]

    var body: some View {
        NavigationStack {
            Form {
                if values.count == 5 {
                    ForEach(0..<5, id: \.self) { i in
                        TextField(L(keys[i]), text: $values[i]).keyboardType(.numbersAndPunctuation)
                            .accessibilityIdentifier("v56_tf_num_\(["x", "y", "w", "h", "angle"][i])")
                    }
                }
            }
            .navigationTitle(L("v56_tf_numeric"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("cancel_button")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("ok_button")) {
                        if let m = transform.metrics() {
                            let v = (0..<5).map { Float(values[$0].replacingOccurrences(of: ",", with: ".")) ?? m[$0] }
                            transform.numeric(v[0], v[1], v[2], v[3], v[4])
                        }
                        dismiss()
                    }.accessibilityIdentifier("v56_tf_num_apply")
                }
            }
        }
        .onAppear { values = (transform.metrics() ?? [0, 0, 0, 0, 0]).map { String(Int($0.rounded())) } }
        .presentationDetents([.medium])
    }
}

private struct LiquifyOverlay: View {
    @ObservedObject var liquify: LiquifyController
    @ObservedObject var viewport: ViewportState
    @State private var cursor: CGPoint?
    @State private var last: CGPoint?

    var body: some View {
        Canvas { ctx, _ in
            guard let c = cursor else { return }
            let r = CGFloat(liquify.radius * viewport.scale)
            ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)), with: .color(.accentColor), lineWidth: 1.5)
        }
        .contentShape(Rectangle())
        .gesture(DragGesture(minimumDistance: 0)
            .onChanged { v in
                let prev = last ?? v.startLocation
                last = v.location; cursor = v.location
                let c = viewport.toCanvas(P(Float(v.location.x), Float(v.location.y)))
                let k = viewport.scale
                liquify.brush(c.x, c.y, Float(v.location.x - prev.x) / k, Float(v.location.y - prev.y) / k)
            }
            .onEnded { _ in last = nil; cursor = nil })
        .accessibilityIdentifier("v56_liquify_overlay")
    }
}

private struct LiquifyBar: View {
    @ObservedObject var liquify: LiquifyController

    var body: some View {
        PanelBox {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach([(WarpMesh.Liquify.PUSH, "v56_lq_push"), (.TWIRL, "v56_lq_twirl"), (.PINCH, "v56_lq_pinch"), (.BLOAT, "v56_lq_bloat")], id: \.0) { m, key in
                        V56Chip(label: L(key), on: liquify.mode == m, id: "v56_lq_\(m.rawValue.lowercased())") { liquify.mode = m }
                    }
                    V56Chip(label: L("cancel_button"), id: "v56_lq_cancel") { liquify.cancel() }
                    V56Chip(label: L("v56_tf_done"), on: true, id: "v56_lq_done") { liquify.done() }
                }
            }
            HStack(spacing: 8) {
                Text(L("v56_lq_size")).font(.caption)
                Slider(value: Binding(get: { Double(liquify.radius) }, set: { liquify.setRadius(Float($0)) }), in: 10...400).accessibilityIdentifier("v56_lq_size")
                Text(L("v56_lq_strength")).font(.caption)
                Slider(value: Binding(get: { Double(liquify.strength) }, set: { liquify.setStrength(Float($0)) }), in: 0.05...1).accessibilityIdentifier("v56_lq_strength")
            }
        }
    }
}
