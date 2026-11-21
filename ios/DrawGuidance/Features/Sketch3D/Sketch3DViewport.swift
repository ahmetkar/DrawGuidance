import SwiftUI

/// v56 3B Çizim sahnesi (Android `Sketch3DViewport`): model Metal'le ([Metal3DView]) ya da yazılım çizicisiyle
/// (`Renderer3D`, arka planda) çizilir; üstte görünür çizim düzlemi (ızgara, yatıksa turuncu, Düzlem modunda merkez
/// tutamacı), seçili çizgiler, çizim önizlemesi, silgi dairesi ve eksen göstergesi.
///
/// Dokunuşlar: kalem (ya da Parmakla çiz açıkken parmak) çizer; çizgiyi bitirirken basılı tutmak düz çizgi yapar.
/// Tek parmak (çizmiyorken) döndürür, iki parmak kaydırır (parmakla çizimde döndürür) ve kıstırma yakınlaştırır;
/// iki parmakla kısa dokunma geri alır. Düzlem modunda dokunma yapıştırır, tutamacı sürüklemek düzlemi kaydırır;
/// Seç modunda dokunma seçer, seçiliyi sürüklemek dönüştürür.
struct Sketch3DViewport: View {
    @ObservedObject var vm: Sketch3DModel
    @State private var size: CGSize = .zero
    @State private var image: CGImage?
    @State private var interacting = false
    @State private var drawPath: [CGPoint] = []
    @State private var eraserAt: CGPoint?
    @State private var g = SketchGesture()

    static let ORBIT: Float = 0.4
    static let HOLD_NS: UInt64 = 450_000_000
    static let RADIUS: Float = 22

    var body: some View {
        GeometryReader { geo in
            let shown = vm.model?.visibleLayers()
            ZStack {
                Color(uiColor: .systemBackground)
                if let m = shown {
                    if vm.gpu {
                        Metal3DView(model: m, selected: vm.selection.filter { id in m.parts.contains { $0.id == id } },
                                    clear: Color(uiColor: .systemBackground), tick: vm.renderTick, style: vm.style, onFail: { vm.gpuFailed() })
                    } else if let img = image {
                        Image(decorative: img, scale: 1).resizable().interpolation(.low).frame(width: geo.size.width, height: geo.size.height)
                    }
                }
                overlay(shown)
                TouchSurface(identifier: "sketch3d_viewport") { phase, touches in handle(phase, touches) }
            }
            .onAppear { setSize(geo.size) }
            .onChange(of: geo.size) { setSize($0) }
            .task(id: renderKey(shown)) { await renderSoftware(shown) }
        }
    }

    private func setSize(_ s: CGSize) {
        size = s
        vm.setViewport(Int(s.width), Int(s.height))
    }

    // MARK: yazılım çizici

    private struct RenderKey: Equatable {
        let model: Model3D?
        let selection: Set<String>
        let size: CGSize
        let interacting: Bool
        let tick: Int
        let gpu: Bool
        let style: Int
    }

    private func renderKey(_ m: Model3D?) -> RenderKey {
        RenderKey(model: m, selection: vm.selection, size: size, interacting: interacting, tick: vm.renderTick, gpu: vm.gpu, style: vm.style)
    }

    private func renderSoftware(_ shown: Model3D?) async {
        guard !vm.gpu, let m = shown, size.width > 0, size.height > 0 else { return }
        let target: CGFloat = interacting ? 420 : 760
        let k = min(1, target * CGFloat(Budget3D.softwareScale) / max(size.width, size.height))
        let w = max(16, Int(size.width * k)), h = max(16, Int(size.height * k))
        let sel = vm.selection
        let busy = interacting
        let tex = vm.textures
        let st = vm.style
        let img = await Task.detached(priority: .userInitiated) { () -> CGImage? in
            let f = Renderer3D.render(m, View3.editor(m.camera, w, h),
                                      RenderOptions(textures: { tex.cached($0) }, supersample: busy ? 1 : 2, bilinear: !busy,
                                                    shadow: busy ? false : nil, selectedPart: sel.first { id in m.parts.contains { $0.id == id } },
                                                    selectedParts: sel, style: st))
            return Bitmaps.image(argb: f.color, w, h)
        }.value
        if !Task.isCancelled { image = img }
    }

    // MARK: işaretler

    private func overlay(_ shown: Model3D?) -> some View {
        Canvas { ctx, _ in
            guard let view = vm.currentView(), let m = shown else { return }
            let accent = Color.accentColor
            if vm.mode == .DRAW || vm.mode == .PLANE { drawPlane(ctx, view, vm.plane, vm.planeHalf(), accent, handles: vm.mode == .PLANE) }
            if !vm.selection.isEmpty { drawSelected(ctx, m, view, accent) }
            if drawPath.count >= 2 {
                var p = Path()
                p.move(to: drawPath[0])
                for q in drawPath.dropFirst() { p.addLine(to: q) }
                if vm.tool == .FILL {
                    p.closeSubpath()
                    ctx.fill(p, with: .color(Color(argb: vm.color).opacity(0.3)))
                    ctx.stroke(p, with: .color(Color(argb: vm.color)), lineWidth: 2)
                } else {
                    ctx.stroke(p, with: .color(Color(argb: vm.color)), style: StrokeStyle(lineWidth: CGFloat(vm.size), lineCap: .round, lineJoin: .round))
                }
            }
            if let e = eraserAt {
                let r = CGFloat(vm.eraserSize)
                let rect = CGRect(x: e.x - r, y: e.y - r, width: 2 * r, height: 2 * r)
                ctx.fill(Path(ellipseIn: rect), with: .color(.white.opacity(0.5)))
                ctx.stroke(Path(ellipseIn: rect), with: .color(Color(red: 0.9, green: 0.22, blue: 0.21)), lineWidth: 2)
            }
            // Eksen göstergesi (sol alt)
            let o = CGPoint(x: 32, y: size.height - 32)
            for (a, col) in [(V3(1, 0, 0), Color.red), (V3(0, 1, 0), Color.green), (V3(0, 0, 1), Color.blue)] {
                var p = Path()
                p.move(to: o)
                p.addLine(to: CGPoint(x: o.x + CGFloat(a.dot(view.right)) * 20, y: o.y + CGFloat(a.dot(view.down)) * 20))
                ctx.stroke(p, with: .color(col), style: StrokeStyle(lineWidth: 3, lineCap: .round))
            }
        }
        .allowsHitTesting(false)
    }

    private func drawPlane(_ ctx: GraphicsContext, _ view: View3, _ plane: Plane3, _ half: Float, _ accent: Color, handles: Bool) {
        let col = PlaneSnap.tooTilted(plane, view) ? Color.orange : accent
        var s = [Float](repeating: 0, count: 4)
        func p(_ w: V3) -> CGPoint? { view.project(w.x, w.y, w.z, &s); return s[3] > 0 ? CGPoint(x: CGFloat(s[0]), y: CGFloat(s[1])) : nil }
        var corners: [CGPoint] = []
        for (a, b) in [(Float(-1), Float(-1)), (1, -1), (1, 1), (-1, 1)] {
            guard let c = p(plane.world(a * half, b * half)) else { return }
            corners.append(c)
        }
        var path = Path()
        path.move(to: corners[0])
        for c in corners.dropFirst() { path.addLine(to: c) }
        path.closeSubpath()
        ctx.fill(path, with: .color(col.opacity(0.07)))
        let step: Float = half > 6 ? 1 : half > 2.5 ? 0.5 : 0.25
        var grid = Path()
        for (a, b) in PlaneSnap.grid(plane, half, step) {
            guard let pa = p(a), let pb = p(b) else { continue }
            grid.move(to: pa); grid.addLine(to: pb)
        }
        ctx.stroke(grid, with: .color(col.opacity(0.22)), lineWidth: 1)
        ctx.stroke(path, with: .color(col.opacity(0.8)), lineWidth: 2)
        if handles, let c = p(plane.origin) {
            if let n = p(plane.origin + plane.normal * (half * 0.35)) {
                var l = Path(); l.move(to: c); l.addLine(to: n)
                ctx.stroke(l, with: .color(col), style: StrokeStyle(lineWidth: 3, lineCap: .round, dash: [8, 5]))
            }
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - 13, y: c.y - 13, width: 26, height: 26)), with: .color(.white))
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - 10, y: c.y - 10, width: 20, height: 20)), with: .color(col))
        }
    }

    private func drawSelected(_ ctx: GraphicsContext, _ m: Model3D, _ view: View3, _ accent: Color) {
        var s = [Float](repeating: 0, count: 4)
        for st in m.strokes where vm.selection.contains(st.id) && st.count >= 2 {
            var path = Path()
            var started = false
            for i in 0..<st.count {
                view.project(st.points[3 * i], st.points[3 * i + 1], st.points[3 * i + 2], &s)
                if s[3] <= 0 { started = false; continue }
                let pt = CGPoint(x: CGFloat(s[0]), y: CGFloat(s[1]))
                if !started { path.move(to: pt); started = true } else { path.addLine(to: pt) }
            }
            ctx.stroke(path, with: .color(accent.opacity(0.45)), style: StrokeStyle(lineWidth: 8, lineCap: .round, lineJoin: .round))
        }
    }

    // MARK: dokunuşlar

    private func handle(_ phase: TouchSurface.Phase, _ touches: [TouchSurface.Touch]) {
        switch phase {
        case .began where !g.active:
            guard let t = touches.first else { return }
            start(t)
            if touches.count >= 2 { goMulti(touches) }
        case .began, .moved:
            if touches.count >= 2 { goMulti(touches); return }
            if g.multi { return }
            guard let t = touches.first else { return }
            move(t)
        case .ended:
            finish()
        }
    }

    private func start(_ t: TouchSurface.Touch) {
        let m = vm.mode
        g = SketchGesture()
        g.active = true
        g.mode = m
        g.tool = vm.tool
        if t.stylus { vm.stylusSeen() }
        g.drawing = m == .DRAW && (t.stylus || vm.fingerDraw)
        g.start = t.point; g.prev = t.point
        g.multiStart = Date()
        g.lastMoveAt = Date(); g.lastMovePos = t.point
        interacting = true
        drawPath = []
        let x = Float(t.point.x), y = Float(t.point.y)
        if g.drawing && (g.tool == .PEN || g.tool == .FILL) { addPoint(t.point, t.stylus ? Float(t.pressure) : 1) }
        if g.drawing && g.tool == .ERASER { vm.beginErase(); vm.eraseAt(x, y, vm.eraserSize); eraserAt = t.point }
        if m == .PLANE, let v = vm.currentView() {
            let s = v.project(vm.plane.origin)
            g.grabbedPlane = s[3] > 0 && hypot(CGFloat(s[0]) - t.point.x, CGFloat(s[1]) - t.point.y) < CGFloat(Self.RADIUS * 1.6)
        }
        g.grabbedSel = m == .SELECT && !vm.selection.isEmpty && vm.onSelection(x, y, Self.RADIUS)
        scheduleHold()
    }

    private func addPoint(_ p: CGPoint, _ pressure: Float) {
        g.xy += [Float(p.x), Float(p.y)]
        g.pressures.append(pressure)
        drawPath.append(p)
    }

    /// Kalem durunca (450 ms) düz çizgi.
    private func scheduleHold() {
        g.holdTask?.cancel()
        guard g.drawing && g.tool == .PEN else { return }
        let gesture = g
        g.holdTask = Task { @MainActor in
            while !Task.isCancelled && gesture.active {
                try? await Task.sleep(nanoseconds: 120_000_000)
                if gesture.moved && !gesture.straight && gesture.xy.count >= 6 && !gesture.multi,
                   Date().timeIntervalSince(gesture.lastMoveAt) * 1_000_000_000 > Double(Self.HOLD_NS) {
                    gesture.straight = true
                    if let f = drawPath.first, let l = drawPath.last { drawPath = [f, l] }
                }
            }
        }
    }

    private func goMulti(_ touches: [TouchSurface.Touch]) {
        let a = touches[0].point, b = touches[1].point
        let mid = CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
        let dist = hypot(a.x - b.x, a.y - b.y)
        if !g.multi {
            if g.live { vm.endLive(); g.live = false }
            drawPath = []; g.xy = []; g.pressures = []; eraserAt = nil
            g.multi = true
            g.pinchMid = mid; g.pinchDist = dist
            return
        }
        let dx = Float(mid.x - g.pinchMid.x), dy = Float(mid.y - g.pinchMid.y)
        if abs(dx) + abs(dy) > 0.5 || abs(dist - g.pinchDist) > 1 { g.multiMoved = true }
        if g.mode == .DRAW && vm.fingerDraw { vm.orbit(-dx * Self.ORBIT, dy * Self.ORBIT) } else { vm.pan(dx, dy) }
        if g.pinchDist > 1 && dist > 1 { vm.zoom(Float(dist / g.pinchDist)) }
        g.pinchMid = mid; g.pinchDist = dist
    }

    private func move(_ t: TouchSurface.Touch) {
        if t.stylus { vm.stylusSeen() }
        let p = t.point
        let dx = Float(p.x - g.prev.x), dy = Float(p.y - g.prev.y)
        g.prev = p
        if !g.moved && hypot(p.x - g.start.x, p.y - g.start.y) > 8 {
            g.moved = true
            if g.drawing {
            } else if g.grabbedPlane { g.planeDrag = true } else if g.grabbedSel { vm.beginLive(); g.live = true } else { g.orbitDrag = true }
        }
        guard g.moved else { return }
        if hypot(p.x - g.lastMovePos.x, p.y - g.lastMovePos.y) > 3 { g.lastMoveAt = Date(); g.lastMovePos = p }
        if g.orbitDrag {
            vm.orbit(-dx * Self.ORBIT, dy * Self.ORBIT)
        } else if g.planeDrag {
            vm.dragPlane(dy)
        } else if g.live {
            switch vm.xform {
            case .MOVE: vm.moveSelectedBy(dx, dy)
            case .ROTATE: vm.rotateSelected(dx * 0.5)
            case .SCALE: vm.scaleSelected(exp((dx - dy) / 260))
            }
        } else if g.drawing && g.tool == .PEN {
            if !g.straight {
                addPoint(p, t.stylus ? Float(t.pressure) : 1)
            } else if g.xy.count >= 2 {
                drawPath = [CGPoint(x: CGFloat(g.xy[0]), y: CGFloat(g.xy[1])), p]
                g.xy += [Float(p.x), Float(p.y)]; g.pressures.append(1)
            }
        } else if g.drawing && g.tool == .FILL {
            addPoint(p, 1)
        } else if g.drawing && g.tool == .ERASER {
            vm.eraseAt(Float(p.x), Float(p.y), vm.eraserSize)
            eraserAt = p
        }
    }

    private func finish() {
        guard g.active else { return }
        g.active = false
        g.holdTask?.cancel()
        interacting = false
        eraserAt = nil
        if g.drawing && g.tool == .ERASER { vm.endErase() }
        if g.live { vm.endLive() }
        if g.planeDrag { vm.endPlaneDrag() }
        if g.multi && !g.multiMoved && Date().timeIntervalSince(g.multiStart) < 0.35 { vm.undo() }
        if !g.moved && !g.multi {
            let x = Float(g.start.x), y = Float(g.start.y)
            switch g.mode {
            case .PLANE: vm.snapPlaneAt(x, y, Self.RADIUS)
            case .SELECT: vm.tapSelect(x, y, Self.RADIUS)
            default: break
            }
        }
        // v56 aşama 12 dolgu: dokunma kapalı çizginin içini, sürükleme kementi doldurur
        if g.drawing && g.tool == .FILL && !g.multi {
            if !g.moved { vm.fillAt(Float(g.start.x), Float(g.start.y)) } else { vm.fillLasso(g.xy) }
        }
        if g.drawing && g.tool == .PEN && !g.multi && g.xy.count >= 4 {
            let anyStylus = g.pressures.contains { $0 != 1 }
            vm.finishStroke(g.xy, anyStylus ? g.pressures : nil, straight: g.straight)
        }
        drawPath = []
    }
}

/// Bir dokunuş hareketinin durumu (yalnız ana iş parçacığında kullanılır).
final class SketchGesture {
    var active = false
    var mode: SketchMode = .DRAW
    var tool: SketchTool = .PEN
    var drawing = false
    var start = CGPoint.zero, prev = CGPoint.zero
    var moved = false, multi = false, multiMoved = false, live = false, planeDrag = false, orbitDrag = false
    var grabbedPlane = false, grabbedSel = false
    var straight = false
    var xy: [Float] = []
    var pressures: [Float] = []
    var multiStart = Date()
    var lastMoveAt = Date()
    var lastMovePos = CGPoint.zero
    var pinchMid = CGPoint.zero
    var pinchDist: CGFloat = 0
    var holdTask: Task<Void, Never>?
}
