import SwiftUI
import UIKit

/// Çok parmaklı dokunuş yüzeyi: dokunuşları (konum, basınç, kalem mi) olduğu gibi iletir.
struct TouchSurface: UIViewRepresentable {
    struct Touch { let id: ObjectIdentifier; let point: CGPoint; let pressure: CGFloat; let stylus: Bool }
    enum Phase { case began, moved, ended }
    let onTouches: (Phase, [Touch]) -> Void

    func makeUIView(context: Context) -> TouchView {
        let v = TouchView()
        v.isMultipleTouchEnabled = true
        v.backgroundColor = .clear
        v.onTouches = onTouches
        // SwiftUI'nin kimliği boş UIView'a ulaşmıyor; UI testleri görünümü bu kimlikle bulur
        v.accessibilityIdentifier = "model3d_viewport"
        return v
    }

    func updateUIView(_ v: TouchView, context: Context) { v.onTouches = onTouches }

    final class TouchView: UIView {
        var onTouches: ((Phase, [Touch]) -> Void)?
        private var active: [ObjectIdentifier: UITouch] = [:]

        private func snapshot() -> [Touch] {
            active.values.map { t in
                let force = t.maximumPossibleForce > 0 ? t.force / t.maximumPossibleForce : 1
                return Touch(id: ObjectIdentifier(t), point: t.location(in: self), pressure: t.type == .pencil ? force : 1, stylus: t.type == .pencil)
            }
        }

        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
            for t in touches { active[ObjectIdentifier(t)] = t }
            onTouches?(.began, snapshot())
        }
        override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) { onTouches?(.moved, snapshot()) }
        override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
            for t in touches { active.removeValue(forKey: ObjectIdentifier(t)) }
            onTouches?(active.isEmpty ? .ended : .moved, snapshot())
        }
        override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { touchesEnded(touches, with: event) }
    }
}

/// Bir dokunuş hareketinin durumu (Android `awaitEachGesture` döngüsünün karşılığı).
@MainActor
final class Gesture3 {
    var tool: Tool3 = .MOVE
    var start = CGPoint.zero
    var prev = CGPoint.zero
    var moved = false, multi = false, longFired = false, live = false, vertexDrag = false, orbitDrag = false, grabbed = false
    var stylus = false
    var worldPts: [Float] = []
    var pressures: [Float] = []
    var plane: (V3, V3)?
    var ex: [Float] = [], ey: [Float] = []
    var applied = 0
    var erasing = false
    var eraserR: Float = 24
    var lastPaint = CGPoint.zero
    var pinchDist: CGFloat = 0
    var pinchMid = CGPoint.zero
    var longTimer: Task<Void, Never>?
    var active = false
}

/// 3B sahne görünümü: yazılım çizicisi arka planda çizer (etkileşim sürerken düşük çözünürlükte, gölgesiz); dokunuşlar
/// ve işaretler (noktalar, seçili kenarlar, eksen göstergesi) üstteki katmandadır.
struct Model3DViewport: View {
    @ObservedObject var vm: Model3DEditorModel
    @State private var image: CGImage?
    @State private var size: CGSize = .zero
    @State private var interacting = false
    @State private var drawPath: [CGPoint] = []
    @State private var erasePath: [CGPoint] = []
    @State private var gesture = Gesture3()
    @State private var renderTask: Task<Void, Never>?
    @State private var renderSeq = 0

    private static let ORBIT: Float = 0.4
    private static let MAX_DOTS = 160
    private static let SLOP: CGFloat = 8
    private static let RADIUS: Float = 22

    var body: some View {
        GeometryReader { geo in
            ZStack {
                background
                if let image {
                    Image(decorative: image, scale: 1).resizable().interpolation(.low).frame(width: geo.size.width, height: geo.size.height)
                }
                Canvas { ctx, sz in drawMarks(&ctx, sz) }
                    .allowsHitTesting(false)
                TouchSurface { phase, touches in handle(phase, touches) }
                    .accessibilityIdentifier("model3d_viewport")
            }
            .onAppear { size = geo.size; vm.setViewport(Int(geo.size.width), Int(geo.size.height)); scheduleRender() }
            .onChange(of: geo.size) { _, s in size = s; vm.setViewport(Int(s.width), Int(s.height)); scheduleRender() }
        }
        .onReceive(vm.$model) { _ in DispatchQueue.main.async { scheduleRender() } }
        .onReceive(vm.$selection) { _ in DispatchQueue.main.async { scheduleRender() } }
        .onReceive(vm.$tool) { _ in DispatchQueue.main.async { scheduleRender() } }
        .onReceive(vm.$renderTick) { _ in DispatchQueue.main.async { scheduleRender() } }
        .onReceive(vm.$quality) { _ in DispatchQueue.main.async { scheduleRender() } }
        .onChange(of: interacting) { _, _ in scheduleRender() }
    }

    private var background: some View {
        let bg = vm.model?.scene.background ?? 0
        return Group {
            if bg != 0 { Color(argb: bg) } else {
                LinearGradient(colors: [Color(white: 0.95), Color(white: 0.985)], startPoint: .top, endPoint: .bottom)
            }
        }
    }

    // MARK: çizim

    private func scheduleRender() {
        guard let m = vm.model, size.width > 0, size.height > 0 else { return }
        renderTask?.cancel()
        let q = vm.quality, inter = interacting, sel = vm.selection, t = vm.tool
        let target: CGFloat
        switch q {
        case Model3DPrefs.QUALITY_HIGH: target = inter ? 520 : 1000
        case Model3DPrefs.QUALITY_BALANCED: target = inter ? 420 : 760
        default: target = inter ? 320 : 560
        }
        // Ekran noktası başına en çok 2 piksel (Retina), hedef çözünürlüğe göre
        let k = min(2, target * CGFloat(Budget3D.softwareScale) / max(size.width, size.height))
        let w = max(Int(size.width * k), 16), h = max(Int(size.height * k), 16)
        let sx = Float(w) / Float(size.width)
        let vmRef = vm
        renderSeq += 1
        let seq = renderSeq
        renderTask = Task {
            let img = await Task.detached(priority: .userInitiated) { () -> CGImage? in
                // Görünüm ekran noktasında; çizim çözünürlüğüne ölçeklenir
                let base = View3.editor(m.camera, Int(size.width), Int(size.height))
                let view = View3(m.camera, w, h, base.scale * sx, base.centerX * sx, base.centerY * sx)
                var o = RenderOptions(textures: { vmRef.texture($0) })
                o.supersample = inter || q == Model3DPrefs.QUALITY_FAST ? 1 : 2
                o.bilinear = !inter
                o.shadow = inter ? false : nil
                o.selectedPart = sel.primary
                o.selectedParts = Set(sel.parts)
                o.selectedFaces = t == .FACE || t == .PAINT ? sel.faces.asSet : []
                o.wireframe = t == .EDGE || t == .POINT
                if Task.isCancelled { return nil }
                let f = Renderer3D.render(m, view, o)
                return Bitmaps.image(argb: f.color, w, h)
            }.value
            if let img, !Task.isCancelled, seq == renderSeq { image = img }
        }
    }

    private func drawMarks(_ ctx: inout GraphicsContext, _ sz: CGSize) {
        guard let m = vm.model, let view = vm.currentView() else { return }
        let accent = Color.accentColor
        let sel = vm.selection
        if vm.tool == .EDGE || vm.tool == .POINT { drawEdgeMarks(&ctx, m, view, sel, accent) }
        if vm.tool == .POINT { drawPointOrder(&ctx, m, view, sel, accent) }
        if vm.op?.kind == .HOLE, let (c, n, r) = vm.holePreview() { drawCircle3(&ctx, view, c, n, r, Color(red: 1, green: 0.44, blue: 0.26), filled: true) }
        if vm.panel == .OPS && vm.busy == nil, let pid = sel.primary, let part = m.part(pid) {
            if let (p, n) = vm.slicePlane() { drawPlane3(&ctx, view, p, n, ModelEdit.worldBounds(part).radius * 1.25, accent) }
            if let (c, n, r) = vm.panelHoleFrame() { drawCircle3(&ctx, view, c, n, r, Color(red: 1, green: 0.44, blue: 0.26), filled: false) }
        }
        if !erasePath.isEmpty {
            let r = CGFloat(vm.eraserSize)
            if erasePath.count >= 2 {
                var p = Path(); p.addLines(erasePath)
                ctx.stroke(p, with: .color(Color(red: 0.9, green: 0.22, blue: 0.21).opacity(0.2)), style: StrokeStyle(lineWidth: r * 2, lineCap: .round, lineJoin: .round))
            }
            let last = erasePath[erasePath.count - 1]
            let circle = Path(ellipseIn: CGRect(x: last.x - r, y: last.y - r, width: 2 * r, height: 2 * r))
            ctx.fill(circle, with: .color(.white.opacity(0.5)))
            ctx.stroke(circle, with: .color(Color(red: 0.9, green: 0.22, blue: 0.21)), lineWidth: 2)
        }
        if drawPath.count >= 2 {
            var p = Path(); p.addLines(drawPath)
            ctx.stroke(p, with: .color(Color(argb: vm.brushColor)), style: StrokeStyle(lineWidth: CGFloat(vm.brushWidth), lineCap: .round, lineJoin: .round))
        }
        // Eksen göstergesi (sol alt)
        let o = CGPoint(x: 36, y: sz.height - 36)
        let axes: [(V3, Color, String)] = [(V3(1, 0, 0), .red, "X"), (V3(0, 1, 0), .green, "Y"), (V3(0, 0, 1), .blue, "Z")]
        for (a, col, label) in axes {
            let dx = CGFloat(a.dot(view.right)), dy = CGFloat(a.dot(view.down))
            let end = CGPoint(x: o.x + dx * 24, y: o.y + dy * 24)
            var p = Path(); p.move(to: o); p.addLine(to: end)
            ctx.stroke(p, with: .color(col), style: StrokeStyle(lineWidth: 3, lineCap: .round))
            ctx.draw(Text(label).font(.system(size: 11, weight: .semibold)).foregroundColor(col), at: CGPoint(x: end.x + dx * 9, y: end.y + dy * 9))
        }
    }

    private func screen(_ view: View3, _ w: [Float], _ i: Int) -> CGPoint? {
        var s = [Float](repeating: 0, count: 4)
        view.project(w[3 * i], w[3 * i + 1], w[3 * i + 2], &s)
        return s[3] > 0 ? CGPoint(x: CGFloat(s[0]), y: CGFloat(s[1])) : nil
    }

    private func drawEdgeMarks(_ ctx: inout GraphicsContext, _ m: Model3D, _ view: View3, _ sel: Selection3, _ accent: Color) {
        guard let pid = sel.primary, let part = m.part(pid) else { return }
        let mesh = part.mesh
        let w = Pick3.worldVertices(part)
        for e in sel.edges where e.a < mesh.vertexCount && e.b < mesh.vertexCount {
            guard let a = screen(view, w, e.a), let b = screen(view, w, e.b) else { continue }
            var p = Path(); p.move(to: a); p.addLine(to: b)
            ctx.stroke(p, with: .color(.white), style: StrokeStyle(lineWidth: 7, lineCap: .round))
            ctx.stroke(p, with: .color(accent), style: StrokeStyle(lineWidth: 4, lineCap: .round))
        }
        var shown = Set<Int>()
        for e in sel.edges { shown.insert(e.a); shown.insert(e.b) }
        for v in sel.vertices { shown.insert(v) }
        if mesh.vertexCount <= Self.MAX_DOTS {
            let t = Transform3.of(part)
            let fn = mesh.faceNormals
            var n = [Float](repeating: 0, count: 3)
            let eye: V3? = view.perspective ? view.eye : nil
            let tv = view.toViewer
            for f in 0..<mesh.faceCount {
                t.normal(fn[3 * f], fn[3 * f + 1], fn[3 * f + 2], &n, 0)
                guard let v0 = mesh.faces[f].first else { continue }
                let facing: Bool
                if let eye {
                    let d0 = (eye.x - w[3 * v0]) * n[0], d1 = (eye.y - w[3 * v0 + 1]) * n[1], d2 = (eye.z - w[3 * v0 + 2]) * n[2]
                    facing = d0 + d1 + d2 > 0
                } else { facing = n[0] * tv.x + n[1] * tv.y + n[2] * tv.z > 0 }
                if facing { for i in mesh.faces[f] { shown.insert(i) } }
            }
        }
        let partIndex = m.parts.firstIndex { $0.id == part.id } ?? -1
        let check = m.parts.count > 1 && shown.count * m.visibleTriangles < 400_000
        for i in shown where i < mesh.vertexCount {
            guard let p = screen(view, w, i) else { continue }
            let chosen = sel.vertices.contains(i)
            let visible = !check || Pick3.visible(m, view, V3(w[3 * i], w[3 * i + 1], w[3 * i + 2]), partIndex)
            let a: Double = visible ? 1 : 0.35
            let r1: CGFloat = chosen ? 8 : 5, r2: CGFloat = chosen ? 6 : 3.5
            ctx.fill(Path(ellipseIn: CGRect(x: p.x - r1, y: p.y - r1, width: 2 * r1, height: 2 * r1)), with: .color(.white.opacity(a)))
            ctx.fill(Path(ellipseIn: CGRect(x: p.x - r2, y: p.y - r2, width: 2 * r2, height: 2 * r2)),
                     with: .color((chosen ? accent : Color(red: 0.22, green: 0.28, blue: 0.31)).opacity(a)))
        }
    }

    private func drawPointOrder(_ ctx: inout GraphicsContext, _ m: Model3D, _ view: View3, _ sel: Selection3, _ accent: Color) {
        guard let pid = sel.primary, let part = m.part(pid) else { return }
        let order = sel.vertices.items.filter { $0 < part.mesh.vertexCount }
        if order.isEmpty { return }
        let w = Pick3.worldVertices(part)
        let pts = order.map { screen(view, w, $0) }
        let dash = StrokeStyle(lineWidth: 2.5, lineCap: .round, dash: [10, 6])
        for k in 0..<max(pts.count - 1, 0) {
            guard let a = pts[k], let b = pts[k + 1] else { continue }
            var p = Path(); p.move(to: a); p.addLine(to: b)
            ctx.stroke(p, with: .color(accent), style: dash)
        }
        if pts.count >= 3, let a = pts.last ?? nil, let b = pts.first ?? nil {
            var p = Path(); p.move(to: a); p.addLine(to: b)
            ctx.stroke(p, with: .color(accent.opacity(0.45)), style: StrokeStyle(lineWidth: 2, lineCap: .round, dash: [10, 6]))
        }
        for (k, pp) in pts.enumerated() {
            guard let p = pp else { continue }
            let at = CGPoint(x: p.x + 13, y: p.y - 14)
            ctx.fill(Path(ellipseIn: CGRect(x: at.x - 8, y: at.y - 8, width: 16, height: 16)), with: .color(accent))
            ctx.draw(Text("\(k + 1)").font(.system(size: 10, weight: .bold)).foregroundColor(.white), at: at)
        }
    }

    private static func tangents(_ n: V3) -> (V3, V3) {
        let nn = n.normalized()
        let ref = abs(nn.y) < 0.9 ? V3.UP : V3(1, 0, 0)
        let t1 = (ref - nn * ref.dot(nn)).normalized()
        return (t1, nn.cross(t1).normalized())
    }

    private func drawPlane3(_ ctx: inout GraphicsContext, _ view: View3, _ p: V3, _ n: V3, _ half: Float, _ color: Color) {
        let (t1, t2) = Self.tangents(n)
        var pts: [CGPoint] = []
        for (a, b) in [(Float(-1), Float(-1)), (1, -1), (1, 1), (-1, 1)] {
            let w = p + t1 * (a * half) + t2 * (b * half)
            let s = view.project(w)
            if s[3] <= 0 { return }
            pts.append(CGPoint(x: CGFloat(s[0]), y: CGFloat(s[1])))
        }
        var path = Path(); path.addLines(pts); path.closeSubpath()
        ctx.fill(path, with: .color(color.opacity(0.16)))
        ctx.stroke(path, with: .color(color), lineWidth: 2)
    }

    private func drawCircle3(_ ctx: inout GraphicsContext, _ view: View3, _ c: V3, _ n: V3, _ r: Float, _ color: Color, filled: Bool) {
        let (t1, t2) = Self.tangents(n)
        var pts: [CGPoint] = []
        for k in 0...40 {
            let a = Float(k) / 40 * 2 * PI_F
            let w = c + t1 * (cos(a) * r) + t2 * (sin(a) * r)
            let s = view.project(w)
            if s[3] <= 0 { return }
            pts.append(CGPoint(x: CGFloat(s[0]), y: CGFloat(s[1])))
        }
        var path = Path(); path.addLines(pts); path.closeSubpath()
        if filled { ctx.fill(path, with: .color(color.opacity(0.25))) }
        ctx.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 2.5, dash: filled ? [] : [8, 5]))
    }

    // MARK: dokunuşlar

    private func handle(_ phase: TouchSurface.Phase, _ touches: [TouchSurface.Touch]) {
        let g = gesture
        switch phase {
        case .began where !g.active:
            guard let first = touches.first else { return }
            begin(first)
            if touches.count >= 2 { moveMulti(touches) }
        case .began, .moved:
            if touches.count >= 2 { moveMulti(touches) } else if let t = touches.first, !g.multi { moveSingle(t) }
        case .ended:
            end()
        }
    }

    private func begin(_ t: TouchSurface.Touch) {
        let g = Gesture3()
        gesture = g
        g.active = true
        g.tool = vm.tool
        g.start = t.point; g.prev = t.point; g.lastPaint = t.point
        g.stylus = t.stylus
        g.eraserR = vm.eraserSize
        interacting = true
        drawPath = []
        erasePath = []
        let x = Float(t.point.x), y = Float(t.point.y)
        if g.tool == .DRAW { g.plane = vm.drawPlane(); addDrawPoint(g, t.point, Float(t.pressure)) }
        g.grabbed = (g.tool == .MOVE || g.tool == .ROTATE || g.tool == .SCALE) && vm.grabAt(x, y)
        if g.tool == .PAINT { vm.previewPaint(x, y) }
        if g.tool == .EDGE {
            g.longTimer = Task { @MainActor in
                try? await Task.sleep(nanoseconds: 500_000_000)
                if Task.isCancelled || g.moved || g.multi || !g.active { return }
                g.longFired = vm.addPointAt(x, y, Self.RADIUS)
            }
        }
    }

    private func addDrawPoint(_ g: Gesture3, _ p: CGPoint, _ pressure: Float) {
        guard let pl = g.plane, let hit = vm.planePoint(pl, Float(p.x), Float(p.y)) else { return }
        g.worldPts += [hit.x, hit.y, hit.z]
        g.pressures.append(pressure)
        drawPath.append(p)
    }

    private func eraseTo(_ g: Gesture3, _ p: CGPoint, final: Bool) {
        guard g.tool == .ERASE else { return }
        g.ex.append(Float(p.x)); g.ey.append(Float(p.y))
        erasePath.append(p)
        if !g.erasing { vm.beginErase(); g.erasing = true }
        let li = max(g.applied - 1, 0)
        let dx = Float(p.x) - g.ex[li], dy = Float(p.y) - g.ey[li]
        let far = dx * dx + dy * dy > (g.eraserR * 0.5) * (g.eraserR * 0.5)
        if far || final || g.applied == 0 {
            vm.eraseAlong(Array(g.ex[li...]), Array(g.ey[li...]), g.eraserR)
            g.applied = g.ex.count
        }
    }

    private func finishErase(_ g: Gesture3) {
        guard g.erasing else { return }
        g.erasing = false
        vm.endErase(g.ex, g.ey, g.eraserR)
        g.ex = []; g.ey = []; g.applied = 0
    }

    private func moveMulti(_ touches: [TouchSurface.Touch]) {
        let g = gesture
        let a = touches[0].point, b = touches[1].point
        let mid = CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
        let dist = max(hypot(a.x - b.x, a.y - b.y), 1)
        if !g.multi {
            g.longTimer?.cancel()
            if g.live { vm.endLive(); g.live = false }
            if g.vertexDrag { vm.endVertexDrag(); g.vertexDrag = false }
            finishErase(g); erasePath = []
            g.pressures = []
            g.multi = true
            g.pinchDist = dist; g.pinchMid = mid
            drawPath = []; g.worldPts = []
            return
        }
        let pan = CGPoint(x: mid.x - g.pinchMid.x, y: mid.y - g.pinchMid.y)
        let zoom = Float(dist / g.pinchDist)
        g.pinchMid = mid; g.pinchDist = dist
        let mode = Mode3.of(g.tool)
        let pans = g.tool == .VIEW || mode == .SELECT || mode == .EDIT
        if pans { vm.pan(Float(pan.x), Float(pan.y)) } else { vm.orbit(-Float(pan.x) * Self.ORBIT, Float(pan.y) * Self.ORBIT) }
        if zoom != 1 { vm.zoom(zoom) }
    }

    private func moveSingle(_ t: TouchSurface.Touch) {
        let g = gesture
        guard g.active else { return }
        let p = t.point
        let d = CGPoint(x: p.x - g.prev.x, y: p.y - g.prev.y)
        g.prev = p
        let sx = Float(g.start.x), sy = Float(g.start.y)
        if !g.moved && hypot(p.x - g.start.x, p.y - g.start.y) > Self.SLOP {
            g.moved = true
            g.longTimer?.cancel()
            switch g.tool {
            case .MOVE, .ROTATE, .SCALE:
                if g.grabbed && !vm.selection.parts.isEmpty { vm.beginLive(); g.live = true } else { g.orbitDrag = true }
            case .ERASE: eraseTo(g, g.start, final: false)
            case .EDGE: if vm.beginVertexDrag(sx, sy, Self.RADIUS) { g.vertexDrag = true } else { g.orbitDrag = true }
            case .POINT: if vm.beginPointDrag(sx, sy, Self.RADIUS) { g.vertexDrag = true } else { g.orbitDrag = true }
            case .FACE: if vm.onSelectedFace(sx, sy) { vm.beginLive(); g.live = true } else { g.orbitDrag = true }
            case .PAINT: vm.beginLive(); g.live = true
            case .VIEW: g.orbitDrag = true
            case .DRAW: break
            }
        }
        guard g.moved else { return }
        let dx = Float(d.x), dy = Float(d.y)
        if g.orbitDrag { vm.orbit(-dx * Self.ORBIT, dy * Self.ORBIT); return }
        switch g.tool {
        case .MOVE: if let v = vm.dragDelta(dx, dy) { vm.moveSelected(v) }
        case .ROTATE: vm.rotateByDrag(dx, dy)
        case .SCALE: vm.scaleByDrag(dx, dy)
        case .EDGE, .POINT: if g.vertexDrag { vm.dragVerticesBy(dx, dy) }
        case .FACE: if let v = vm.dragDelta(dx, dy) { vm.moveFaces(v) }
        case .DRAW:
            if t.stylus { g.stylus = true }
            addDrawPoint(g, p, Float(t.pressure))
        case .PAINT:
            if hypot(p.x - g.lastPaint.x, p.y - g.lastPaint.y) > 14 {
                g.lastPaint = p
                vm.tapAt(Float(p.x), Float(p.y), Self.RADIUS)
            }
        case .ERASE: eraseTo(g, p, final: false)
        case .VIEW: break
        }
    }

    private func end() {
        let g = gesture
        guard g.active else { return }
        g.active = false
        g.longTimer?.cancel()
        interacting = false
        if g.tool == .ERASE && !g.multi {
            if !g.moved { eraseTo(g, g.start, final: true) } else if let lx = g.ex.last, let ly = g.ey.last { eraseTo(g, CGPoint(x: CGFloat(lx), y: CGFloat(ly)), final: true) }
            finishErase(g)
        }
        erasePath = []
        if g.vertexDrag { vm.endVertexDrag() }
        if g.live { vm.endLive() }
        vm.endCoalesce()
        if !g.moved && !g.multi && !g.longFired && g.tool != .ERASE { vm.tapAt(Float(g.start.x), Float(g.start.y), Self.RADIUS) }
        if g.tool == .DRAW && g.worldPts.count >= 6 && !g.multi, let plane = g.plane {
            vm.finishBrush(g.worldPts, g.stylus && g.pressures.count * 3 == g.worldPts.count ? g.pressures : nil, plane.1)
        }
        drawPath = []
    }
}

extension Color {
    /// ARGB tamsayıdan renk.
    init(argb c: Int32) {
        let u = UInt32(bitPattern: c)
        self.init(.sRGB, red: Double(u >> 16 & 0xFF) / 255, green: Double(u >> 8 & 0xFF) / 255, blue: Double(u & 0xFF) / 255,
                  opacity: Double(u >> 24 & 0xFF) / 255)
    }
}
