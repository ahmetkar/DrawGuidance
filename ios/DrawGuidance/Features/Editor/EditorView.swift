import PhotosUI
import SwiftUI

/// İlk düzenleyici: serbest çizim, silgi, renk, kalınlık, geri al/yinele (Android `EditHistory` ile).
/// Android v49 düzenleyicisinin aktarımı (araç menüleri, katmanlar, şekiller…) bunun üzerine kurulacak.
struct EditorView: View {
    @EnvironmentObject private var store: CanvasStore
    @State private var canvas: CanvasUi
    @State private var current: Stroke?
    @State private var color: Color = .black
    @State private var width: Double
    @State private var brush: BrushType
    @State private var flow: Double = 1
    @State private var opacity: Double = 1
    @State private var brushPickerOpen = false
    /// Kaydedilmiş çizimlerin görüntüsü (her karede fırça motoru yeniden çalışmasın).
    @State private var cache = RenderCache()
    @Environment(\.displayScale) private var displayScale
    @State private var eraser = false
    /// Şekil aracında seçili şekil ("lib.<kimlik>"); nil: kalem/silgi.
    @State private var shapeId: String?
    @State private var shapeStart: P?
    @State private var pickerOpen = false
    @State private var historyTick = 0
    // @State: görünüm yeniden kurulsa da (mağaza kaydı üst görünümü yeniler) geçmiş korunur
    @State private var history = EditHistory<[Stroke]>()
    @State private var photo: PhotosPickerItem?
    /// Resim kontrolünün sonucu (hata ya da onay gereken uyarılar).
    @State private var importResult: ImageCheckResult?

    init(canvas: CanvasUi) {
        _canvas = State(initialValue: canvas)
        let b = defaultBrush(canvas.kind)
        _brush = State(initialValue: b)
        _width = State(initialValue: Double(b.defaultSize))
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
        .onAppear {
            let c = canvas
            DispatchQueue.global(qos: .userInitiated).async { CanvasAssetStore.shared.ensureLoaded(c) }
        }
        .onReceive(NotificationCenter.default.publisher(for: CanvasImageCache.changed).receive(on: RunLoop.main)) { _ in historyTick += 1 }
        .onChange(of: photo) { _, item in
            guard let item else { return }
            photo = nil
            Task {
                guard let data = try? await item.loadTransferable(type: Data.self) else {
                    importResult = ImageCheckResult(errors: [.unreadable], warnings: [], image: nil); return
                }
                let r = await Task.detached { ImageImportValidator.check(data) }.value
                if r.accepted && r.warnings.isEmpty { addImage(r.image!) } else { importResult = r }
            }
        }
        .alert(L(importResult?.accepted == true ? "image_warning_title" : "image_rejected_title"), isPresented: Binding(get: { importResult != nil }, set: { if !$0 { importResult = nil } })) {
            if let r = importResult, r.accepted {
                Button(L("image_add_anyway")) { if let img = r.image { addImage(img) }; importResult = nil }
                Button(L("cancel_button"), role: .cancel) { importResult = nil }
            } else {
                Button(L("ok_button"), role: .cancel) { importResult = nil }
            }
        } message: {
            Text(((importResult?.errors ?? []) + (importResult?.warnings ?? [])).map(\.message).joined(separator: "\n"))
        }
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
                    Button { brushPickerOpen = true } label: {
                        Label(L(brush.titleKey), systemImage: "slider.horizontal.3")
                    }.accessibilityIdentifier("brush_menu")
                    Toggle(isOn: Binding(get: { eraser && shapeId == nil }, set: { eraser = $0; if $0 { shapeId = nil } })) {
                        Label(LocalizedStringKey("tool.eraser"), systemImage: "eraser")
                    }.toggleStyle(.button).accessibilityIdentifier("tool_eraser")
                    Toggle(isOn: Binding(get: { shapeId != nil }, set: { if $0 { pickerOpen = true } else { shapeId = nil } })) {
                        Label(LocalizedStringKey("tool.shape"), systemImage: "square.on.circle")
                    }.toggleStyle(.button).accessibilityIdentifier("tool_shape")
                    PhotosPicker(selection: $photo, matching: .images) {
                        Label(L("add_image"), systemImage: "photo")
                    }.accessibilityIdentifier("tool_image")
                    ColorPicker(LocalizedStringKey("tool.color"), selection: $color).labelsHidden()
                    Slider(value: $width, in: 1...200).frame(width: 120)
                }
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .sheet(isPresented: $brushPickerOpen) {
            BrushPickerView(kind: canvas.kind, brush: $brush, size: $width, flow: $flow, opacity: $opacity,
                            color: ArgbColor.hex(color.hexRGB))
                .onDisappear { eraser = false; shapeId = nil }
        }
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
        let paperArgb = ArgbColor.parse(canvas.pagePaperHex) ?? ArgbColor.white
        let decor = canvas.pageBackground
        let committed = Layers.visible(strokes, canvas.layers)
        let pixelScale = min(scale * displayScale, 3)
        let version = historyTick
        let cache = self.cache
        let live = current
        return Canvas { ctx, _ in
            ctx.scaleBy(x: scale, y: scale)
            ctx.withCGContext { c in
                c.setFillColor(ArgbColor.cgColor(paperArgb | 0xFF00_0000))
                c.fill(CGRect(x: 0, y: 0, width: w, height: h))
                // v23 arka plan resmi ve deseni (kağıt renginin üstünde, çizimlerin altında)
                BackgroundRenderer.drawCanvasBackground(c, decor, 0, 0, Float(w), Float(h), scale: 1, paper: paperArgb,
                                                        vector: false, density: Float(pixelScale))
                // Kılavuz ve perspektif çizgileri (ekran ölçeğinde çizgi kalınlığı)
                c.saveGState()
                c.scaleBy(x: 1 / scale, y: 1 / scale)
                GuideRenderer.draw(c, canvas.guide.normalized(), paper: CGRect(x: 0, y: 0, width: w * scale, height: h * scale), scale: Float(scale))
                c.restoreGState()
                c.beginTransparencyLayer(auxiliaryInfo: nil)
                let image = cache.image(version: version, pixelScale: pixelScale, width: w, height: h) { committed }
                Bitmaps.drawUpright(c, image, in: CGRect(x: 0, y: 0, width: w, height: h))
                if let live { StrokeRenderer.drawStroke(c, live) }
                c.endTransparencyLayer()
                // v40 sayfa numarası, üst ve alt bilgi
                PageDecorRenderer.draw(c, canvas.pageSettings, page: canvas.currentPage, pageCount: canvas.pageCount,
                                       width: Float(w), height: Float(h), paper: paperArgb, cover: canvas.hasCover)
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
                                         brushType: eraser ? .ERASER : brush, sizePx: Float(width),
                                         opacity: Float(opacity), order: strokes.count, flow: Float(flow))
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

    /// Seçilen resim tuvalin en fazla %60'ını kaplayacak şekilde, oranı korunarak ortaya eklenir (Android `addImage`).
    private func addImage(_ image: PreparedImage) {
        let assetId = CanvasAssetStore.shared.save(canvas.localId, image)
        let cw = Float(canvas.width), ch = Float(canvas.height)
        let fit = min(1, cw * 0.6 / Float(image.width), ch * 0.6 / Float(image.height))
        let hw = Float(image.width) * fit / 2, hh = Float(image.height) * fit / 2
        let cx = cw / 2, cy = ch / 2
        let q = ContentCodec.quantize
        let s = Stroke(id: UUID().uuidString.lowercased(),
                       points: [StrokePoint(q(cx - hw), q(cy - hh)), StrokePoint(q(cx + hw), q(cy - hh)),
                                StrokePoint(q(cx + hw), q(cy + hh)), StrokePoint(q(cx - hw), q(cy + hh))],
                       colorHex: "#000000", brushType: .IMAGE, sizePx: 1, opacity: 1, order: strokes.count, assetId: assetId)
        commit(strokes + [s])
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

/// Kaydedilmiş çizimlerin önbellek görüntüsü: sürüm (geçmiş sayacı) ve çözünürlük değişince yeniden çizilir.
final class RenderCache {
    private var key: (Int, CGFloat, CGFloat, CGFloat)?
    private var cached: CGImage?

    func image(version: Int, pixelScale: CGFloat, width: CGFloat, height: CGFloat, _ strokes: () -> [Stroke]) -> CGImage {
        if let cached, let k = key, k == (version, pixelScale, width, height) { return cached }
        let img = StrokeRenderer.render(strokes(), canvasWidth: width, canvasHeight: height, scale: pixelScale)
        cached = img
        key = (version, pixelScale, width, height)
        return img
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
