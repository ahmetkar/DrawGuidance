import SwiftUI

/// 3B çizim modunun alt çubuğu (Android `Draw3dBar`): çizim düzlemi (serbest, ön, yan, üst), derinlik; v22 döndürme
/// alanı (sürükleyince sahne döner, içinde X, Y, Z eksenleri), hazır açılar ve perspektif. Küçültülebilir; Kapat modu kapatır.
struct Draw3dBar: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @State private var collapsed = false
    @State private var depth: Double?
    @State private var persp: Double?

    private var guide: GuideSpec { store.canvas?.guide ?? GuideSpec() }

    private static func planeLabel(_ p: Plane3D) -> String {
        switch p {
        case .FREE: return L("draw3d_plane_free")
        case .FRONT: return L("draw3d_plane_front")
        case .SIDE: return L("draw3d_plane_side")
        case .TOP: return L("draw3d_plane_top")
        }
    }

    private static func isPreset(_ p: CameraPreset, _ yaw: Float, _ pitch: Float) -> Bool {
        func near(_ a: Float, _ b: Float) -> Bool { abs(a - b) < 0.5 }
        switch p {
        case .ISO: return near(yaw, GuideSpec.DEFAULT_YAW) && near(pitch, GuideSpec.DEFAULT_PITCH)
        case .FRONT: return (near(yaw, 0) || near(yaw, 360)) && near(pitch, 0)
        case .SIDE: return near(yaw, 90) && near(pitch, 0)
        case .TOP: return near(pitch, 90)
        }
    }

    var body: some View {
        let g = guide
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Image(systemName: "cube").foregroundStyle(Color.accentColor)
                Text(L("draw3d_title")).font(.subheadline.weight(.semibold))
                Spacer()
                Button { collapsed.toggle() } label: { Image(systemName: collapsed ? "chevron.up" : "chevron.down") }
                    .accessibilityLabel(L(collapsed ? "draw3d_expand" : "draw3d_collapse"))
                    .accessibilityIdentifier("draw3d_collapse")
                Button { vm.toggleDraw3d() } label: { Image(systemName: "xmark") }
                    .accessibilityLabel(L("draw3d_close"))
                    .accessibilityIdentifier("draw3d_close")
            }
            if !collapsed {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Plane3D.allCases, id: \.self) { p in
                            chip(Self.planeLabel(p), p == g.plane3d, "draw3d_plane_\(p.rawValue.lowercased())") { vm.setPlane3d(p) }
                        }
                    }
                }
                HStack(alignment: .center, spacing: 12) {
                    OrbitPad(yaw: g.yaw3d, pitch: g.pitch3d) { dx, dy, done in vm.orbit3d(dx, dy, persist: done) }
                    VStack(alignment: .leading, spacing: 4) {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 6) {
                                ForEach([(CameraPreset.ISO, "draw3d_view_iso", "iso"), (.FRONT, "draw3d_view_front", "front"),
                                         (.SIDE, "draw3d_view_side", "side"), (.TOP, "draw3d_view_top", "top")], id: \.2) { item in
                                    chip(L(item.1), Self.isPreset(item.0, g.yaw3d, g.pitch3d), "draw3d_view_\(item.2)") { vm.presetCamera3d(item.0) }
                                }
                            }
                        }
                        slider(L("draw3d_persp"), "draw3d_persp", value: persp ?? Double(g.persp3d), set: { persp = $0 }) { v, done in
                            vm.setCamera3d(g.yaw3d, g.pitch3d, Float(v), persist: done)
                            if done { persp = nil }
                        }
                        slider(L("draw3d_depth"), "draw3d_depth", value: depth ?? Double(g.depth3d), set: { depth = $0 }) { v, done in
                            vm.setDepth3d(Float(v), persist: done)
                            if done { depth = nil }
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22))
        .overlay(RoundedRectangle(cornerRadius: 22).stroke(Color.secondary.opacity(0.2), lineWidth: 1))
        .frame(maxWidth: 560)
        .padding(10)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("draw3d_bar")
    }

    private func chip(_ label: String, _ on: Bool, _ id: String, _ run: @escaping () -> Void) -> some View {
        Button(action: run) {
            Text(label).font(.caption.weight(on ? .semibold : .regular)).lineLimit(1)
                .padding(.horizontal, 10).padding(.vertical, 6)
                .foregroundStyle(on ? Color.accentColor : Color.primary)
                .background(Capsule().fill(on ? Color.accentColor.opacity(0.18) : Color.primary.opacity(0.06)))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }

    private func slider(_ title: String, _ id: String, value: Double, set: @escaping (Double) -> Void,
                        apply: @escaping (Double, Bool) -> Void) -> some View {
        HStack(spacing: 8) {
            Text(title).font(.caption.weight(.semibold))
            Slider(value: Binding(get: { value }, set: { set($0); apply($0, false) }), in: 0...1, onEditingChanged: { editing in
                if !editing { apply(value, true) }
            })
            .accessibilityIdentifier(id)
            Text("%\(Int((value * 100).rounded()))").font(.caption2).foregroundStyle(.secondary).frame(width: 36, alignment: .trailing)
        }
    }
}

/// 3B çubuğunu yalnız 3B çizim açıkken gösterir (tuvalin altında).
struct Draw3dHost: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore

    var body: some View {
        if store.canvas?.guide.draw3d == true && store.canvas?.kind != .KIDS && vm.texts.draft == nil {
            Draw3dBar(vm: vm, store: store)
        }
    }
}

/// Döndürme alanı (Android `OrbitPad`): sürükleyince sahne döner; içinde X (kırmızı), Y (yeşil), Z (mavi) eksenleri.
/// Hareket birikip en çok ~30 kez/sn uygulanır; parmak kalkınca son hali kaydedilir.
private struct OrbitPad: View {
    let yaw: Float
    let pitch: Float
    let onOrbit: (Float, Float, Bool) -> Void
    @State private var last: CGSize = .zero
    @State private var acc: (x: Float, y: Float) = (0, 0)
    @State private var lastSent = Date.distantPast
    private let degPerPt: Float = 0.45

    var body: some View {
        Canvas { g, size in
            let cam = SceneCamera(yaw, pitch, 0, 0, 0, 1)
            let c = CGPoint(x: size.width / 2, y: size.height / 2)
            let len = min(size.width, size.height) * 0.46 - 14
            g.fill(Path(ellipseIn: CGRect(x: c.x - len, y: c.y - len, width: len * 2, height: len * 2)), with: .color(.secondary.opacity(0.18)))
            let axes: [([Float], Color, Float)] = [
                ([1, 0, 0], Color(red: 0.90, green: 0.22, blue: 0.21), cam.depth(1, 0, 0)),
                ([0, 1, 0], Color(red: 0.26, green: 0.63, blue: 0.28), cam.depth(0, 1, 0)),
                ([0, 0, 1], Color(red: 0.12, green: 0.53, blue: 0.90), cam.depth(0, 0, 1)),
            ].sorted { $0.2 < $1.2 }
            for (v, color, _) in axes {
                let p = cam.project(v[0], v[1], v[2])
                let end = CGPoint(x: c.x + CGFloat(p.x) * len, y: c.y + CGFloat(p.y) * len)
                var path = Path(); path.move(to: c); path.addLine(to: end)
                g.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 3, lineCap: .round))
                g.fill(Path(ellipseIn: CGRect(x: end.x - 3.5, y: end.y - 3.5, width: 7, height: 7)), with: .color(color))
            }
        }
        .frame(width: 92, height: 92)
        .background(Circle().fill(Color(uiColor: .systemBackground)))
        .overlay(Circle().stroke(Color.accentColor.opacity(0.45), lineWidth: 1))
        .clipShape(Circle())
        .contentShape(Circle())
        .gesture(
            DragGesture(minimumDistance: 1)
                .onChanged { v in
                    acc.x += Float(v.translation.width - last.width) * degPerPt
                    acc.y += Float(v.translation.height - last.height) * degPerPt
                    last = v.translation
                    if Date().timeIntervalSince(lastSent) >= 0.033 {
                        lastSent = Date()
                        onOrbit(acc.x, acc.y, false); acc = (0, 0)
                    }
                }
                .onEnded { _ in onOrbit(acc.x, acc.y, true); acc = (0, 0); last = .zero }
        )
        .accessibilityLabel(L("draw3d_orbit"))
        .accessibilityIdentifier("draw3d_orbit")
    }
}
