import SwiftUI

/// İlk düzenleyici: serbest çizim, silgi, renk, kalınlık, geri al/yinele.
/// Android v49 düzenleyicisinin (araç menüleri, katmanlar, şekiller…) aktarımı bunun üzerine kurulacak.
struct EditorView: View {
    @EnvironmentObject private var library: CanvasLibrary
    @State private var doc: CanvasDocument
    @State private var history: UndoableStrokes
    @State private var current: Stroke?
    @State private var color: Color = .black
    @State private var width: Double = 4
    @State private var eraser = false

    init(document: CanvasDocument) {
        _doc = State(initialValue: document)
        _history = State(initialValue: UndoableStrokes(document.strokes))
    }

    var body: some View {
        VStack(spacing: 0) {
            toolbar
            Divider()
            canvas
        }
        .navigationTitle(doc.name)
        .navigationBarTitleDisplayMode(.inline)
        .onDisappear(perform: persist)
    }

    // v49: basit modlarda menü tek satır, sığmazsa yatay kayar; geri al/yinele başta sabit.
    private var toolbar: some View {
        HStack(spacing: 8) {
            Button { history.undo(); persist() } label: { Image(systemName: "arrow.uturn.backward") }
                .disabled(!history.canUndo).accessibilityIdentifier("undo")
            Button { history.redo(); persist() } label: { Image(systemName: "arrow.uturn.forward") }
                .disabled(!history.canRedo).accessibilityIdentifier("redo")
            Divider().frame(height: 24)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    Toggle(isOn: Binding(get: { !eraser }, set: { eraser = !$0 })) {
                        Label(LocalizedStringKey("tool.brush"), systemImage: "paintbrush.pointed")
                    }.toggleStyle(.button).accessibilityIdentifier("tool_brush")
                    Toggle(isOn: $eraser) {
                        Label(LocalizedStringKey("tool.eraser"), systemImage: "eraser")
                    }.toggleStyle(.button).accessibilityIdentifier("tool_eraser")
                    ColorPicker(LocalizedStringKey("tool.color"), selection: $color).labelsHidden()
                    Slider(value: $width, in: 1...40).frame(width: 120)
                }
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
    }

    private var canvas: some View {
        Canvas { ctx, _ in
            for s in history.strokes + (current.map { [$0] } ?? []) {
                draw(s, in: &ctx)
            }
        }
        .background(Color.white)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { v in
                    let p = StrokePoint(x: v.location.x, y: v.location.y)
                    if current == nil {
                        current = Stroke(points: [p], colorHex: color.hexRGBA, width: width, isEraser: eraser)
                    } else {
                        current?.points.append(p)
                    }
                }
                .onEnded { _ in
                    if let s = current { history.add(s) }
                    current = nil
                    persist()
                }
        )
        .accessibilityIdentifier("drawing_canvas")
    }

    private func draw(_ s: Stroke, in ctx: inout GraphicsContext) {
        guard let first = s.points.first else { return }
        var path = Path()
        path.move(to: first.cg)
        if s.points.count == 1 { path.addLine(to: CGPoint(x: first.x + 0.1, y: first.y)) }
        for p in s.points.dropFirst() { path.addLine(to: p.cg) }
        let c: Color = s.isEraser ? .white : Color(hexRGBA: s.colorHex)
        ctx.stroke(path, with: .color(c), style: StrokeStyle(lineWidth: s.width, lineCap: .round, lineJoin: .round))
    }

    private func persist() {
        doc.strokes = history.strokes
        library.update(doc)
    }
}

extension Color {
    init(hexRGBA v: UInt32) {
        self.init(.sRGB,
                  red: Double((v >> 24) & 0xFF) / 255,
                  green: Double((v >> 16) & 0xFF) / 255,
                  blue: Double((v >> 8) & 0xFF) / 255,
                  opacity: Double(v & 0xFF) / 255)
    }

    var hexRGBA: UInt32 {
        let ui = UIColor(self)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        ui.getRed(&r, green: &g, blue: &b, alpha: &a)
        func c(_ x: CGFloat) -> UInt32 { UInt32(max(0, min(255, (x * 255).rounded()))) }
        return c(r) << 24 | c(g) << 16 | c(b) << 8 | c(a)
    }
}
