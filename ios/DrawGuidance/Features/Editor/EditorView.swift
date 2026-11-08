import SwiftUI

/// İlk düzenleyici: serbest çizim, silgi, renk, kalınlık, geri al/yinele (Android `EditHistory` ile).
/// Android v49 düzenleyicisinin aktarımı (araç menüleri, katmanlar, şekiller…) bunun üzerine kurulacak.
struct EditorView: View {
    @EnvironmentObject private var store: CanvasStore
    @State private var canvas: CanvasUi
    @State private var current: Stroke?
    @State private var color: Color = .black
    @State private var width: Double = 4
    @State private var eraser = false
    /// Şekil aracında seçili şekil ("lib.<kimlik>"); nil: kalem/silgi.
    @State private var shapeId: String?
    @State private var shapeStart: P?
    @State private var pickerOpen = false
    @State private var historyTick = 0
    private let history = EditHistory<[Stroke]>()

    init(canvas: CanvasUi) {
        _canvas = State(initialValue: canvas)
    }

    private var strokes: [Stroke] { canvas.strokes }

    var body: some View {
        VStack(spacing: 0) {
            toolbar
            Divider()
            GeometryReader { geo in
                drawingSurface(fit: geo.size)
            }
            .background(Color(white: 0.92))
        }
        .navigationTitle(canvas.name)
        .navigationBarTitleDisplayMode(.inline)
        .onDisappear { store.save(canvas) }
    }

    // v49: basit modlarda menü tek satır, sığmazsa yatay kayar; geri al/yinele başta sabit.
    private var toolbar: some View {
        HStack(spacing: 8) {
            let _ = historyTick
            Button { apply(history.undo()) } label: { Image(systemName: "arrow.uturn.backward") }
                .disabled(!history.canUndo).accessibilityIdentifier("undo")
            Button { apply(history.redo()) } label: { Image(systemName: "arrow.uturn.forward") }
                .disabled(!history.canRedo).accessibilityIdentifier("redo")
            Divider().frame(height: 24)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    Toggle(isOn: Binding(get: { !eraser && shapeId == nil }, set: { if $0 { eraser = false; shapeId = nil } })) {
                        Label(LocalizedStringKey("tool.brush"), systemImage: "paintbrush.pointed")
                    }.toggleStyle(.button).accessibilityIdentifier("tool_brush")
                    Toggle(isOn: Binding(get: { eraser && shapeId == nil }, set: { eraser = $0; if $0 { shapeId = nil } })) {
                        Label(LocalizedStringKey("tool.eraser"), systemImage: "eraser")
                    }.toggleStyle(.button).accessibilityIdentifier("tool_eraser")
                    Toggle(isOn: Binding(get: { shapeId != nil }, set: { if $0 { pickerOpen = true } else { shapeId = nil } })) {
                        Label(LocalizedStringKey("tool.shape"), systemImage: "square.on.circle")
                    }.toggleStyle(.button).accessibilityIdentifier("tool_shape")
                    ColorPicker(LocalizedStringKey("tool.color"), selection: $color).labelsHidden()
                    Slider(value: $width, in: 1...40).frame(width: 120)
                }
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .sheet(isPresented: $pickerOpen) {
            ShapePickerView(kind: canvas.kind) { id in
                shapeId = id
                eraser = false
            }
        }
    }

    /// Kağıt tuval biriminde (ör. 1080×1080) çizilir, ekrana sığdırılır.
    private func drawingSurface(fit: CGSize) -> some View {
        let w = CGFloat(canvas.width), h = CGFloat(canvas.height)
        let scale = max(0.01, min((fit.width - 16) / w, (fit.height - 16) / h))
        let paper = Color(hex: canvas.pagePaperHex) ?? .white
        return Canvas { ctx, _ in
            ctx.scaleBy(x: scale, y: scale)
            ctx.fill(Path(CGRect(x: 0, y: 0, width: w, height: h)), with: .color(paper))
            for s in Layers.visible(strokes, canvas.layers) + (current.map { [$0] } ?? []) {
                if s.brushType == .SHAPE {
                    ctx.withCGContext { ShapeRenderer.draw($0, s) }
                } else {
                    draw(s, paper: paper, in: &ctx)
                }
            }
        }
        .frame(width: w * scale, height: h * scale)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { v in
                    if let shapeId {
                        let p = P(Float(v.location.x / scale), Float(v.location.y / scale))
                        let start = shapeStart ?? p
                        shapeStart = start
                        current = ShapeFactory.build(shapeId, start, p, style: newShapeStyle(), id: current?.id ?? UUID().uuidString.lowercased(),
                                                     order: strokes.count)
                        return
                    }
                    let p = StrokePoint(ContentCodec.quantize(Float(v.location.x / scale)), ContentCodec.quantize(Float(v.location.y / scale)))
                    if current == nil {
                        current = Stroke(id: UUID().uuidString.lowercased(), points: [p], colorHex: color.hexRGB,
                                         brushType: eraser ? .ERASER : .PENCIL, sizePx: Float(width / scale),
                                         opacity: 1, order: strokes.count)
                    } else {
                        current?.points.append(p)
                    }
                }
                .onEnded { v in
                    if let shapeId {
                        let end = P(Float(v.location.x / scale), Float(v.location.y / scale))
                        let start = shapeStart ?? end
                        let s = hypotf(end.x - start.x, end.y - start.y) < 2 / Float(scale)
                            ? shapeAt(shapeId, center: start) : current
                        if let s { commit(strokes + [s]) }
                        shapeStart = nil
                        current = nil
                        return
                    }
                    if let s = current { commit(strokes + [s]) }
                    current = nil
                }
        )
        .accessibilityIdentifier("drawing_canvas")
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func draw(_ s: Stroke, paper: Color, in ctx: inout GraphicsContext) {
        guard let first = s.points.first else { return }
        var path = Path()
        path.move(to: CGPoint(x: CGFloat(first.x), y: CGFloat(first.y)))
        if s.points.count == 1 { path.addLine(to: CGPoint(x: CGFloat(first.x) + 0.1, y: CGFloat(first.y))) }
        for p in s.points.dropFirst() { path.addLine(to: CGPoint(x: CGFloat(p.x), y: CGFloat(p.y))) }
        let c: Color = s.brushType == .ERASER ? paper : (Color(hex: s.colorHex) ?? .black).opacity(Double(s.opacity))
        ctx.stroke(path, with: .color(c), style: StrokeStyle(lineWidth: CGFloat(s.sizePx), lineCap: .round, lineJoin: .round))
    }

    /// Yeni şekillerin biçimi: kenar rengi seçili renk, kalınlık fırçayı izler (Android `newShapeStyle`).
    private func newShapeStyle() -> ShapeStyle {
        ShapeStyle(strokeColorHex: color.hexRGB, strokeWidth: Float(max(1, width / 2)))
    }

    /// Dokunulan noktaya tuvalin küçük kenarının %22'si boyutunda şekil (Android `addShapeAt`).
    private func shapeAt(_ id: String, center: P) -> Stroke? {
        let line = ShapeFactory.fit(id) == .LINE
        let aspect: Float = ShapeFactory.fit(id) == .SQUARE ? 1 : ShapeFactory.aspect(id)
        let half = Float(min(canvas.width, canvas.height)) * 0.22
        let hw = line ? half : (aspect >= 1 ? half : half * aspect)
        let hh = line ? half : (aspect >= 1 ? half / aspect : half)
        let start = line ? P(center.x - half, center.y) : P(center.x - hw, center.y - hh)
        let end = line ? P(center.x + half, center.y) : P(center.x + hw, center.y + hh)
        return ShapeFactory.build(id, start, end, style: newShapeStyle(), id: UUID().uuidString.lowercased(), order: strokes.count)
    }

    private func commit(_ next: [Stroke]) {
        history.record(before: strokes, after: next)
        setStrokes(next)
    }

    private func apply(_ state: [Stroke]?) {
        guard let state else { return }
        setStrokes(state)
    }

    private func setStrokes(_ s: [Stroke]) {
        if canvas.pages.isEmpty { canvas.pages = [[]] }
        let i = canvas.pages.indices.contains(canvas.currentPage) ? canvas.currentPage : 0
        canvas.pages[i] = s
        historyTick += 1
        store.save(canvas)
    }
}

extension Color {
    /// "#RRGGBB" ya da "#AARRGGBB" (Android biçimi).
    init?(hex: String) {
        guard hex.hasPrefix("#"), let v = UInt64(hex.dropFirst(), radix: 16) else { return nil }
        let n = hex.count - 1
        let a, r, g, b: Double
        if n == 6 {
            a = 1; r = Double(v >> 16 & 0xFF) / 255; g = Double(v >> 8 & 0xFF) / 255; b = Double(v & 0xFF) / 255
        } else if n == 8 {
            a = Double(v >> 24 & 0xFF) / 255; r = Double(v >> 16 & 0xFF) / 255; g = Double(v >> 8 & 0xFF) / 255; b = Double(v & 0xFF) / 255
        } else {
            return nil
        }
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }

    /// "#RRGGBB" (büyük harf).
    var hexRGB: String {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(self).getRed(&r, green: &g, blue: &b, alpha: &a)
        func c(_ x: CGFloat) -> Int { Int(max(0, min(255, (x * 255).rounded()))) }
        return String(format: "#%02X%02X%02X", c(r), c(g), c(b))
    }
}
