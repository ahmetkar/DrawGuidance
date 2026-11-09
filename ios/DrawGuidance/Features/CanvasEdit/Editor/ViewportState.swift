import Foundation

/// Çizim alanının görünümü (Android `ViewportState`): ekran = offset + tuval × scale. `settledScale`/`settledOffset`
/// son oturmuş görünüm (çizim önbelleği bu değerlerle üretilir).
@MainActor
final class ViewportState: ObservableObject {
    static let MIN_ZOOM: Float = 0.25
    static let MAX_ZOOM: Float = 12

    @Published private(set) var scale: Float = 1
    @Published private(set) var offset = P(0, 0)
    @Published private(set) var settledScale: Float = 1
    @Published private(set) var settledOffset = P(0, 0)
    @Published private(set) var fitScale: Float = 1
    @Published private(set) var viewSize: (w: Float, h: Float) = (0, 0)

    private var canvasWidth = 1
    private var canvasHeight = 1
    private var margin: Float = 0
    private var initialized = false

    var zoomPercent: Int { fitScale == 0 ? 100 : Int((scale / fitScale * 100).rounded()) }

    func layout(_ w: Float, _ h: Float, canvasW: Int, canvasH: Int, margin: Float) {
        if w <= 0 || h <= 0 { return }
        canvasWidth = max(canvasW, 1)
        canvasHeight = max(canvasH, 1)
        self.margin = margin
        let oldFit = fitScale
        fitScale = computeFit(w, h)
        if !initialized {
            viewSize = (w, h)
            initialized = true
            fit()
        } else if w != viewSize.w || h != viewSize.h {
            let atFit = abs(scale - oldFit) <= oldFit * 1e-3
            let old = viewSize
            viewSize = (w, h)
            if atFit { fit() } else {
                offset = P(offset.x + (w - old.w) / 2, offset.y + (h - old.h) / 2)
                settle()
            }
        }
    }

    func fit() {
        if viewSize.w <= 0 { return }
        fitScale = computeFit(viewSize.w, viewSize.h)
        scale = fitScale
        offset = P((viewSize.w - Float(canvasWidth) * scale) / 2, (viewSize.h - Float(canvasHeight) * scale) / 2)
        settle()
    }

    func zoomBy(_ factor: Float, focus: P) {
        let newScale = (scale * factor).coerceIn(fitScale * Self.MIN_ZOOM, fitScale * Self.MAX_ZOOM)
        let k = newScale / scale
        offset = P(focus.x - (focus.x - offset.x) * k, focus.y - (focus.y - offset.y) * k)
        scale = newScale
    }

    func zoomStep(_ factor: Float) {
        zoomBy(factor, focus: P(viewSize.w / 2, viewSize.h / 2))
        settle()
    }

    func panBy(_ dx: Float, _ dy: Float) { offset = P(offset.x + dx, offset.y + dy) }

    func settle() {
        settledScale = scale
        settledOffset = offset
    }

    func shiftForResize(_ canvasDx: Float, _ canvasDy: Float) {
        offset = P(offset.x + canvasDx * scale, offset.y + canvasDy * scale)
        settle()
    }

    /// v32 eğitim: kâğıdı görünümün `area` bölgesine sığdıran ölçek ve kaydırma.
    func fitTarget(_ area: FRect) -> (Float, P) {
        let s = min(max(area.width - 2 * margin, 1) / Float(canvasWidth), max(area.height - 2 * margin, 1) / Float(canvasHeight))
            .coerceIn(fitScale * Self.MIN_ZOOM, fitScale)
        return (s, P(area.center.x - Float(canvasWidth) * s / 2, area.center.y - Float(canvasHeight) * s / 2))
    }

    func setView(_ scale: Float, _ offset: P, settle: Bool) {
        self.scale = scale
        self.offset = offset
        if settle { self.settle() }
    }

    func toCanvas(_ screen: P) -> P { P((screen.x - offset.x) / scale, (screen.y - offset.y) / scale) }
    func toScreen(_ canvas: P) -> P { P(offset.x + canvas.x * scale, offset.y + canvas.y * scale) }
    func toScreen(_ r: FRect) -> FRect {
        let a = toScreen(r.topLeft), b = toScreen(r.bottomRight)
        return FRect(a.x, a.y, b.x, b.y)
    }

    private func computeFit(_ w: Float, _ h: Float) -> Float {
        min(max(w - 2 * margin, 1) / Float(canvasWidth), max(h - 2 * margin, 1) / Float(canvasHeight))
    }
}
