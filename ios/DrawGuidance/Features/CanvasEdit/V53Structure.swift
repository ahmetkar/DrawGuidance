import SwiftUI

/// v53 yapı kılavuzu durumu: seçili kılavuz, tuvaldeki yeri (merkez ve yükseklik, tuval birimi), saydamlık ve aynalama.
/// Yalnız görünüm: tuvale yazılmaz, kaydedilmez; düzenleyici kapanınca biter.
@MainActor
final class StructureGuideState: ObservableObject {
    @Published var sheet = false
    @Published var guideId: String?
    @Published var center = P(0, 0)
    @Published var height: Float = 600
    @Published var opacity: Double = 0.75
    @Published var flipped = false

    var active: Bool { guideId != nil }
    var guide: StructureGuide? { guideId.flatMap(StructureGuides.find) }

    func place(_ id: String, canvas: CanvasUi) {
        guideId = id
        let g = StructureGuides.find(id)
        let a = g?.aspect ?? 1
        let w = Float(canvas.width), h = Float(canvas.height)
        height = min(h * 0.7, w * 0.7 / max(a, 0.1))
        center = P(w / 2, h / 2)
        flipped = false
    }

    func clear() { guideId = nil }
}

/// v54 çizim yardımcısının çizgileri tuval koordinatında kontur olarak (şekil kalıbı için): çizgiler, çokgenler, daireler ve
/// elipsler; eksen, ölçü çizgileri ve eklem noktaları alınmaz. Kutunun ortası `center`, yüksekliği `height` (Android ile aynı).
enum StructureContours {
    static func of(_ g: StructureGuide, center: P, height: Float) -> [Contour] {
        let left = center.x - height * g.aspect / 2
        let top = center.y - height / 2
        func map(_ pts: [Float]) -> [Float] { pts.enumerated().map { i, v in i % 2 == 0 ? left + v * height : top + v * height } }
        func oval(_ x: Float, _ y: Float, _ rx: Float, _ ry: Float, _ deg: Float) -> [Float] {
            let n = 40
            let a = Double(deg) * .pi / 180
            var out = [Float](repeating: 0, count: n * 2)
            for k in 0..<n {
                let t = 2 * Double.pi * Double(k) / Double(n)
                let ex = Double(rx) * cos(t), ey = Double(ry) * sin(t)
                out[2 * k] = left + (x + Float(ex * cos(a) - ey * sin(a))) * height
                out[2 * k + 1] = top + (y + Float(ex * sin(a) + ey * cos(a))) * height
            }
            return out
        }
        var out: [Contour] = []
        for p in g.prims {
            switch p {
            case let .line(pts), let .poly(pts): out.append((map(pts), false))
            case let .polygon(pts): out.append((map(pts), true))
            case let .circle(x, y, r): out.append((oval(x, y, r, r, 0), true))
            case let .ellipse(x, y, rx, ry, d): out.append((oval(x, y, rx, ry, d), true))
            default: break
            }
        }
        return out
    }
}

/// Kılavuzun çizimi (SwiftUI Canvas ve küçük resimlerde ortak).
enum StructurePainter {
    static func draw(_ ctx: inout GraphicsContext, _ g: StructureGuide, rect: CGRect, flipped: Bool, color: Color, lineWidth: CGFloat) {
        let s = rect.height
        func pt(_ x: Float, _ y: Float) -> CGPoint {
            let gx = flipped ? CGFloat(g.aspect - x) : CGFloat(x)
            return CGPoint(x: rect.minX + gx * s, y: rect.minY + CGFloat(y) * s)
        }
        func poly(_ n: [Float], closed: Bool) -> Path {
            var p = Path()
            var i = 0
            while i + 1 < n.count {
                let q = pt(n[i], n[i + 1])
                if i == 0 { p.move(to: q) } else { p.addLine(to: q) }
                i += 2
            }
            if closed { p.closeSubpath() }
            return p
        }
        let solid = StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round)
        let dashed = StrokeStyle(lineWidth: max(lineWidth * 0.7, 0.6), lineCap: .round, dash: [lineWidth * 4, lineWidth * 3])
        let halo = StrokeStyle(lineWidth: lineWidth + 2, lineCap: .round, lineJoin: .round)
        for prim in g.prims {
            var path = Path()
            var style = solid
            var faint = false
            switch prim {
            case let .measure(n):
                if n > 1 {
                    for k in 1..<n {
                        let y = Float(k) / Float(n)
                        path.move(to: pt(0, y)); path.addLine(to: pt(g.aspect, y))
                    }
                }
                style = dashed; faint = true
            case let .axis(n): path = poly(n, closed: false); style = dashed; faint = true
            case let .line(n), let .poly(n): path = poly(n, closed: false)
            case let .polygon(n): path = poly(n, closed: true)
            case let .circle(x, y, r):
                let c = pt(x, y); let rr = CGFloat(r) * s
                path.addEllipse(in: CGRect(x: c.x - rr, y: c.y - rr, width: rr * 2, height: rr * 2))
            case let .ellipse(x, y, rx, ry, deg):
                let c = pt(x, y)
                let r = CGRect(x: -CGFloat(rx) * s, y: -CGFloat(ry) * s, width: CGFloat(rx) * s * 2, height: CGFloat(ry) * s * 2)
                let a = CGFloat(flipped ? -deg : deg) * .pi / 180
                path.addEllipse(in: r, transform: CGAffineTransform(translationX: c.x, y: c.y).rotated(by: a))
            case let .joint(x, y):
                let c = pt(x, y); let rr = max(lineWidth * 1.8, 2.5)
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - rr, y: c.y - rr, width: rr * 2, height: rr * 2)), with: .color(color))
                continue
            case let .vanish(x, y):
                let c = pt(x, y); let rr = max(lineWidth * 3, 4)
                var cross = Path()
                cross.move(to: CGPoint(x: c.x - rr, y: c.y)); cross.addLine(to: CGPoint(x: c.x + rr, y: c.y))
                cross.move(to: CGPoint(x: c.x, y: c.y - rr)); cross.addLine(to: CGPoint(x: c.x, y: c.y + rr))
                ctx.stroke(cross, with: .color(.orange), style: solid)
                continue
            }
            if !faint { ctx.stroke(path, with: .color(.white.opacity(0.65)), style: halo) }
            ctx.stroke(path, with: .color(faint ? color.opacity(0.45) : color), style: style)
        }
    }
}

/// Tuvalin üstünde kılavuz: dokunuşu tutmaz; köşedeki tutamaklarla taşınır ve boyutlanır.
struct StructureOverlay: View {
    @ObservedObject var state: StructureGuideState
    @ObservedObject var viewport: ViewportState
    @State private var dragMove: CGSize = .zero
    @State private var dragSize: CGFloat = 0

    var body: some View {
        if let g = state.guide {
            let scale = CGFloat(viewport.scale)
            let h = max(CGFloat(state.height) * scale + dragSize, 40)
            let w = h * CGFloat(g.aspect)
            let c = viewport.toScreen(state.center)
            let rect = CGRect(x: CGFloat(c.x) - w / 2 + dragMove.width, y: CGFloat(c.y) - h / 2 + dragMove.height, width: w, height: h)
            ZStack {
                Canvas { ctx, _ in
                    StructurePainter.draw(&ctx, g, rect: rect, flipped: state.flipped, color: .accentColor, lineWidth: 1.6)
                }
                .opacity(state.opacity)
                .allowsHitTesting(false)
                // Taşıma tutamağı (sol üst) ve boyut tutamağı (sağ alt)
                handle("arrow.up.and.down.and.arrow.left.and.right")
                    .position(x: rect.minX, y: rect.minY)
                    .gesture(DragGesture().onChanged { dragMove = $0.translation }.onEnded { v in
                        state.center = P(state.center.x + Float(v.translation.width / scale), state.center.y + Float(v.translation.height / scale))
                        dragMove = .zero
                    })
                    .accessibilityIdentifier("structure_move")
                handle("arrow.up.left.and.arrow.down.right")
                    .position(x: rect.maxX, y: rect.maxY)
                    .gesture(DragGesture().onChanged { dragSize = $0.translation.height }.onEnded { v in
                        state.height = max(state.height + Float(v.translation.height / scale), 60)
                        dragSize = 0
                    })
                    .accessibilityIdentifier("structure_resize")
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("structure_overlay")
        }
    }

    private func handle(_ icon: String) -> some View {
        Image(systemName: icon).font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
            .frame(width: 28, height: 28)
            .background(Circle().fill(Color.accentColor).shadow(radius: 2))
    }
}

/// Kılavuz seçimi: Canlılar, Nesneler, Eşyalar; her biri küçük resmi ve adıyla. Seçilince saydamlık ve aynalama.
struct StructureGuideSheet: View {
    @ObservedObject var state: StructureGuideState
    let canvas: CanvasUi?
    /// v54 şekil kalıbı için seçim: verilirse seçilen yardımcı tuvale konmaz, buna verilir.
    var onPick: ((StructureGuide) -> Void)? = nil
    @Environment(\.dismiss) private var dismiss
    @State private var group: StructureGroup = .LIVING

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text(L(onPick != nil ? "v54_template_helper_hint" : "v53_struct_hint")).font(.footnote).foregroundStyle(.secondary)
                    Picker("", selection: $group) {
                        ForEach(StructureGroup.allCases, id: \.self) { Text(L("v53_struct_group_" + $0.rawValue.lowercased())).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .accessibilityIdentifier("structure_groups")
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 12)], spacing: 12) {
                        ForEach(StructureGuides.byGroup(group)) { g in
                            Button {
                                if let onPick { onPick(g) } else if let canvas { state.place(g.id, canvas: canvas) }
                                dismiss()
                            } label: { card(g) }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("structure_\(g.id)")
                        }
                    }
                    if state.active && onPick == nil {
                        PropSection(title: L("v53_struct_settings")) {
                            PropSlider(label: L("v53_struct_opacity"), value: $state.opacity, range: 0.15...1, format: { "\(Int($0 * 100))%" }, id: "structure_opacity")
                            Toggle(L("v53_struct_flip"), isOn: $state.flipped).accessibilityIdentifier("structure_flip")
                            Button(role: .destructive) { state.clear(); dismiss() } label: {
                                Label(L("v53_struct_remove"), systemImage: "trash")
                            }
                            .accessibilityIdentifier("structure_remove")
                        }
                    }
                }
                .padding(16)
            }
            .navigationTitle(L("v53_struct_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("close")) { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }

    private func card(_ g: StructureGuide) -> some View {
        let selected = onPick == nil && state.guideId == g.id
        return VStack(spacing: 6) {
            Canvas { ctx, size in
                let h = min(size.height, size.width / CGFloat(max(g.aspect, 0.1))) * 0.9
                let w = h * CGFloat(g.aspect)
                StructurePainter.draw(&ctx, g, rect: CGRect(x: (size.width - w) / 2, y: (size.height - h) / 2, width: w, height: h),
                                      flipped: false, color: .accentColor, lineWidth: 1.1)
            }
            .frame(height: 76)
            Text(L(g.nameKey)).font(.caption.weight(.medium)).lineLimit(2).multilineTextAlignment(.center).foregroundStyle(.primary)
        }
        .padding(8)
        .frame(maxWidth: .infinity, minHeight: 116)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(selected ? Color.accentColor.opacity(0.14) : Color.primary.opacity(0.045)))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(selected ? Color.accentColor : .clear, lineWidth: 1.5))
    }
}


/// v54 şekil kalıbı pencereleri (basit ve gelişmiş modlarda ortak): şekil kütüphanesi, seçili şekil/resim yokken
/// "Çizim yardımcıları" ya da "Şekiller"e yönlendiren uyarı ve kalıp için çizim yardımcısı seçimi.
struct RulerSheetsHost: ViewModifier {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var ruler: RulerController

    func body(content: Content) -> some View {
        content
            .sheet(isPresented: Binding(get: { ruler.libraryOpen }, set: { ruler.libraryOpen = $0 })) {
                ShapePickerView(kind: vm.store.canvas?.kind ?? .SIMPLE_ART) { id in
                    let v = vm.viewport
                    let center = v.toCanvas(P(v.viewSize.w / 2, v.viewSize.h / 2))
                    _ = ruler.placeLibrary(id, center: center, size: min(v.viewSize.w, v.viewSize.h) * 0.4 / max(v.scale, 0.01))
                    ruler.libraryOpen = false
                }
            }
            .sheet(isPresented: Binding(get: { ruler.helperPickOpen }, set: { ruler.helperPickOpen = $0 })) {
                StructureGuideSheet(state: vm.structure, canvas: vm.store.canvas, onPick: { g in
                    ruler.helperPickOpen = false
                    RulerOpen.templateFromHelper(vm, ruler, g)
                })
            }
            .alert(L("v40_template"), isPresented: Binding(get: { ruler.chooserOpen }, set: { ruler.chooserOpen = $0 })) {
                Button(L("v53_struct_title")) { ruler.chooserOpen = false; ruler.helperPickOpen = true }
                    .accessibilityIdentifier("template_choose_helpers")
                Button(L("v54_template_choose_shapes")) { ruler.chooserOpen = false; ruler.libraryOpen = true }
                    .accessibilityIdentifier("template_choose_shapes")
                Button(L("v54_template_choose_canvas"), role: .cancel) { ruler.chooserOpen = false }
                    .accessibilityIdentifier("template_choose_canvas")
            } message: {
                Text(L("v54_template_choose_message"))
            }
    }
}
