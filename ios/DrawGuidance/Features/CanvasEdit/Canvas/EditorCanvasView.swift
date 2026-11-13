import Foundation
import SwiftUI
import UIKit

/// Tuval üstü yardımcılar (Android `CanvasOverlay`): kalem aracının noktaları, formül yuvaları, vektör seçimi, düzeltme önerisi.
struct CanvasOverlay {
    var penAnchors: [PenAnchor] = []
    var penPending: PenAnchor?
    var formulaSlots = false
    var formulaSlot: String?
    var vector: VectorSelection?
    var correction: [[P]]?
    var diagramFocus: DiagramFocus?
    var diagramLink: DiagramLink?
    var cropRect: FRect?
    var cropArea: FRect?
}

/// Düzenleyici renkleri (Android `NeuColors` açık/koyu).
enum CanvasColors {
    static var dark: Bool { UITraitCollection.current.userInterfaceStyle == .dark }
    static var accent: UIColor { dark ? UIColor(argb: 0xFF92_90FA) : UIColor(argb: 0xFF5A_5BD8) }
    static var background: UIColor { dark ? UIColor(argb: 0xFF27_2B33) : UIColor(argb: 0xFFE4_E9F1) }
    static var content: UIColor { dark ? UIColor(argb: 0xFFE3_E8F0) : UIColor(argb: 0xFF2C_3547) }
    static var muted: UIColor { content.withAlphaComponent(0.55) }
}

extension UIColor {
    convenience init(argb c: UInt32) {
        self.init(red: CGFloat(c >> 16 & 0xFF) / 255, green: CGFloat(c >> 8 & 0xFF) / 255, blue: CGFloat(c & 0xFF) / 255,
                  alpha: CGFloat(c >> 24 & 0xFF) / 255)
    }
}

private extension P { var cg: CGPoint { CGPoint(x: CGFloat(x), y: CGFloat(y)) } }
private extension FRect { var cg: CGRect { CGRect(x: CGFloat(left), y: CGFloat(top), width: CGFloat(right - left), height: CGFloat(bottom - top)) } }
private extension CGPoint { var p: P { P(Float(x), Float(y)) } }

private let TWO_FINGER_MENU_MS: Int64 = 550
private let TICK_MS: Int64 = 16
private let THREE_FINGER_DISTANCE: Float = 60
private let TOUCH_SLOP: Float = 8

/// Ana çizim alanı (Android `DrawingCanvas`). Kağıt yakınlaştırılabilir/kaydırılabilir bir görünümde gösterilir;
/// dokunuşlar tuval koordinatına çevrilip moda göre araçlara (`CanvasTools`) verilir.
///
/// - İki parmak her modda yakınlaştırır ve kaydırır; kıpırdamadan uzun basmak bağlam işlemini açar.
/// - Üç parmakla yana kaydırma geri al / yinele.
/// - 2 sn hareketsiz basılı tutma: altındaki eleman seçilir (kağıt kenarında genişletme).
/// - Apple Pencil: basınç, eğim ve yön; kalem kullanıldıysa Çiz'de parmak kaydırır (avuç reddi), havada fırça imleci.
///
/// Tamamlanmış çizimler görünüm boyutunda tek bir önbellek görüntüsünde tutulur; yeni çizim eklenince yalnızca o eklenir.
@MainActor
final class EditorCanvasView: UIView {
    weak var vm: EditorViewModel?
    var actions = CanvasActions()
    var overlay = CanvasOverlay()

    private let live = LiveStroke()
    private let taps = TapMemory()
    private var preview: (strokes: [Stroke], w: Int, h: Int, p: CanvasPreview)?
    private var finger: P?
    private var hover: P?
    /// v18 kalem kullanıldı: Çiz'de parmak çizmez, kaydırır.
    private static var pencilUsed = false

    // MARK: Hareket durumu
    private var touches: [UITouch] = []
    private var tool: CanvasTool?
    private var ctx: GestureContext?
    private var guideDrag: GuideDrag?
    private var guideMoved = false
    private var downTime: Int64 = 0
    private var dragging = false
    private var panOnly = false
    private var multiTouch = false
    private var longPressed = false
    private var atEdge = false
    private var twoFingerPending = false
    private var twoFingerCentroid = P(0, 0)
    private var twoFingerMoved: Float = 0
    private var twoFingerZoom: Float = 1
    private var threeFinger = false
    private var threeStart = P(0, 0)
    private var threeDone = false
    private var lastCentroid: P?
    private var lastSpan: Float = 0
    private var longPressTimer: Timer?
    private var twoFingerTimer: Timer?
    private var tickTimer: Timer?

    // MARK: Önbellek
    private struct CacheKey: Equatable {
        let w: Int, h: Int, scale: Float, offset: P, canvasW: Int, canvasH: Int, excluded: Set<String>, page: Int
    }
    private var cacheImage: CGImage?
    private var cacheKey: CacheKey?
    private var cacheDrawn: [Stroke] = []
    private var cacheVersion: Int64 = .min
    private var renderTask: Task<Void, Never>?
    private var requested: (CacheKey, Int64)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        isMultipleTouchEnabled = true
        backgroundColor = CanvasColors.background
        contentMode = .redraw
        accessibilityIdentifier = "drawing_canvas"
        PerspectiveTool.vanishingPoints = { spec, r in GuideRenderer.vanishingPoints(spec, r.cg).map { $0.p } }
        if #available(iOS 13.0, *) {
            let hoverer = UIHoverGestureRecognizer(target: self, action: #selector(onHover(_:)))
            addGestureRecognizer(hoverer)
        }
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) kullanılmaz") }

    private var viewport: ViewportState? { vm?.viewport }
    private var canvas: CanvasUi? { vm?.store.canvas }
    private var mode: EditMode { vm?.mode ?? .DRAW }

    private static func now() -> Int64 { Int64(ProcessInfo.processInfo.systemUptime * 1000) }
    private static func time(_ t: TimeInterval) -> Int64 { Int64(t * 1000) }

    override func layoutSubviews() {
        super.layoutSubviews()
        layoutViewport()
        setNeedsDisplay()
    }

    private func layoutViewport() {
        guard let v = viewport, let c = canvas else { return }
        v.layout(Float(bounds.width), Float(bounds.height), canvasW: c.width, canvasH: c.height, margin: 24)
    }

    func refresh() {
        layoutViewport()
        setNeedsDisplay()
    }

    @objc private func onHover(_ g: UIHoverGestureRecognizer) {
        switch g.state {
        case .began, .changed: hover = g.location(in: self).p
        default: hover = nil
        }
        if mode == .DRAW { setNeedsDisplay() }
    }

    // MARK: Dokunuşlar

    private func position(_ t: UITouch) -> P { t.location(in: self).p }

    private func centroid() -> P {
        let pts = touches.map(position)
        let n = Float(max(pts.count, 1))
        return P(pts.reduce(0) { $0 + $1.x } / n, pts.reduce(0) { $0 + $1.y } / n)
    }

    private func span() -> Float {
        guard touches.count >= 2 else { return 0 }
        let a = position(touches[0]), b = position(touches[1])
        return hypot(a.x - b.x, a.y - b.y)
    }

    private func applyPen(_ t: UITouch) {
        guard let ctx else { return }
        if t.type == .pencil {
            let maxF = t.maximumPossibleForce
            ctx.pressure = maxF > 0 ? Float(t.force / maxF).coerceIn(0.05, 1) : 1
            ctx.tilt = Float(.pi / 2 - t.altitudeAngle)
            ctx.orient = Float(t.azimuthAngle(in: self))
        } else {
            ctx.pressure = 1; ctx.tilt = 0; ctx.orient = 0
        }
    }

    override func touchesBegan(_ set: Set<UITouch>, with event: UIEvent?) {
        guard let vm, let viewport, let canvas else { return }
        let fresh = touches.isEmpty
        for t in set where !touches.contains(t) { touches.append(t) }
        if fresh, let first = set.first {
            startGesture(first, vm: vm, viewport: viewport, canvas: canvas)
            return
        }
        if guideDrag != nil {
            if touches.count >= 2 { guideDrag?.twoFinger(viewport.toCanvas(position(touches[0])), viewport.toCanvas(position(touches[1]))) }
            return
        }
        if longPressed { return }
        // Çiz modunda kalem değdi: süren parmak hareketi bırakılır, kalemle yeni çizim başlar
        if mode == .DRAW, let pen = set.first(where: { $0.type == .pencil }) {
            if dragging && !panOnly { tool?.cancel(ctx!) }
            if multiTouch || panOnly { viewport.settle() }
            touches = [pen]
            startGesture(pen, vm: vm, viewport: viewport, canvas: canvas)
            return
        }
        multiTouchChanged()
    }

    private func startGesture(_ first: UITouch, vm: EditorViewModel, viewport: ViewportState, canvas: CanvasUi) {
        resetGesture()
        let start = position(first)
        downTime = Self.time(first.timestamp)
        if first.type == .pencil { Self.pencilUsed = true }
        // v40 cetvel/kalıp bu hareketi alır
        if let r = actions.ruler(), let g = GuideDrag.begin(r, at: viewport.toCanvas(start), scale: viewport.scale) {
            guideDrag = g
            guideMoved = false
            return
        }
        let fingerPans = mode == .DRAW && Self.pencilUsed && first.type != .pencil
        let t: CanvasTool = fingerPans ? PanTool() : CanvasTools.create(mode, actions.canvasPick(), actions.paintBrush())
        let c = GestureContext(
            viewport: viewport, base: canvas, selected: vm.store.selectedIds, actions: actions, brush: vm.brush.settings,
            crop: overlay.cropRect, shapeTool: vm.shapes.tool, live: live, start: start, handleTolerance: 30, edgeTolerance: 36,
            hitTolerancePx: 14, taps: taps,
            setPreview: { [weak self] p in
                guard let self else { return }
                self.preview = p.map { (canvas.strokes, canvas.width, canvas.height, $0) }
                self.setNeedsDisplay()
            },
            setFinger: { [weak self] p in self?.finger = p },
            refreshLive: { [weak self] in self?.setNeedsDisplay() })
        tool = t
        ctx = c
        applyPen(first)
        c.eventTime = downTime
        atEdge = mode != .EXPAND && paperEdgesAt(viewport, canvas.width, canvas.height, start, 36).contains(true)
        if t.longPressSelects || atEdge {
            longPressTimer = Timer.scheduledTimer(withTimeInterval: Double(LONG_PRESS_MS) / 1000, repeats: false) { [weak self] _ in
                MainActor.assumeIsolated { self?.onLongPress() }
            }
        }
        if t.needsTicks { startTicks() }
        t.down(c)
        // Kalem değdiği anda çizmeye başlar (dokunma eşiği beklenmez)
        if first.type == .pencil && mode == .DRAW && !fingerPans {
            dragging = true
            panOnly = !t.begin(c)
            cancelLongPress()
        }
    }

    private func startTicks() {
        if tickTimer != nil { return }
        tickTimer = Timer.scheduledTimer(withTimeInterval: Double(TICK_MS) / 1000, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated { self?.onTick() }
        }
    }

    private func onTick() {
        guard let tool, let ctx else { return }
        if dragging && !panOnly && !multiTouch && tool.needsTicks {
            tool.tick(ctx, Self.now())
        } else if !tool.needsTicks {
            tickTimer?.invalidate(); tickTimer = nil
        }
    }

    private func cancelLongPress() { longPressTimer?.invalidate(); longPressTimer = nil }

    private func onLongPress() {
        longPressTimer = nil
        guard let tool, let ctx, !dragging, !multiTouch, !longPressed else { return }
        if atEdge && actions.onEdgeHold() {
            haptic(); longPressed = true
        } else if tool.longPressSelects && tool.longPress(ctx) {
            haptic(); longPressed = true
        }
        setNeedsDisplay()
    }

    private func haptic() { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }

    private func multiTouchChanged() {
        guard let viewport, let ctx else { return }
        let c = centroid()
        if touches.count >= 3 {
            if !threeFinger {
                threeFinger = true
                twoFingerPending = false; twoFingerTimer?.invalidate()
                if dragging && !panOnly { tool?.cancel(ctx) }
                if multiTouch { viewport.settle() }
                multiTouch = true
                threeStart = c
            }
            return
        }
        if touches.count == 2 && !multiTouch {
            multiTouch = true
            cancelLongPress()
            if dragging && !panOnly { tool?.cancel(ctx) }
            if !dragging {
                twoFingerPending = true
                twoFingerCentroid = c
                twoFingerMoved = 0
                twoFingerZoom = 1
                twoFingerTimer = Timer.scheduledTimer(withTimeInterval: Double(TWO_FINGER_MENU_MS) / 1000, repeats: false) { [weak self] _ in
                    MainActor.assumeIsolated { self?.onTwoFingerHold() }
                }
            }
        }
        lastCentroid = c
        lastSpan = span()
    }

    private func onTwoFingerHold() {
        guard twoFingerPending, let viewport else { return }
        twoFingerPending = false
        haptic()
        actions.onContextMenu(twoFingerCentroid, viewport.toCanvas(twoFingerCentroid))
        longPressed = true
    }

    override func touchesMoved(_ set: Set<UITouch>, with event: UIEvent?) {
        guard let viewport else { return }
        if let g = guideDrag {
            if touches.count >= 2 {
                g.twoFinger(viewport.toCanvas(position(touches[0])), viewport.toCanvas(position(touches[1])))
            } else if let t = touches.first {
                let p = position(t)
                if g.isPick {
                    if let prev = Optional(t.previousLocation(in: self).p) { viewport.panBy(p.x - prev.x, p.y - prev.y) }
                } else { g.move(viewport.toCanvas(p)) }
            }
            guideMoved = true
            setNeedsDisplay()
            return
        }
        guard let tool, let ctx, let first = touches.first else { return }
        ctx.eventTime = Self.time(first.timestamp)
        if longPressed { return }
        if threeFinger {
            let c = centroid()
            if !threeDone && touches.count >= 3 {
                let dx = c.x - threeStart.x, dy = c.y - threeStart.y
                if abs(dx) > THREE_FINGER_DISTANCE && abs(dx) > abs(dy) * 1.5 {
                    threeDone = true
                    UISelectionFeedbackGenerator().selectionChanged()
                    actions.onThreeFingerSwipe(dx < 0 ? -1 : 1)
                }
            }
            return
        }
        if multiTouch {
            guard touches.count >= 2 else { return }
            let c = centroid(), s = span()
            if let last = lastCentroid {
                let dx = c.x - last.x, dy = c.y - last.y
                let zoom = lastSpan > 1 && s > 1 ? s / lastSpan : 1
                if twoFingerPending {
                    twoFingerMoved += hypot(dx, dy)
                    twoFingerZoom *= zoom
                    if twoFingerMoved > TOUCH_SLOP || abs(twoFingerZoom - 1) > 0.08 { twoFingerPending = false; twoFingerTimer?.invalidate() }
                }
                viewport.panBy(dx, dy)
                viewport.zoomBy(zoom, focus: c)
            }
            lastCentroid = c
            lastSpan = s
            setNeedsDisplay()
            return
        }
        let pos = position(first)
        if !dragging {
            if hypot(pos.x - ctx.start.x, pos.y - ctx.start.y) > TOUCH_SLOP {
                dragging = true
                cancelLongPress()
                panOnly = !tool.begin(ctx)
                drag(first, pos, event)
            }
        } else {
            drag(first, pos, event)
        }
    }

    private func drag(_ t: UITouch, _ pos: P, _ event: UIEvent?) {
        guard let tool, let ctx, let viewport else { return }
        if panOnly {
            let prev = t.previousLocation(in: self).p
            viewport.panBy(pos.x - prev.x, pos.y - prev.y)
            setNeedsDisplay()
            return
        }
        // v18: çizerken ara noktalar da (hızlı çizgide köşeler oluşmasın)
        if tool is DrawTool, let all = event?.coalescedTouches(for: t), all.count > 1 {
            for h in all {
                applyPen(h)
                ctx.eventTime = max(ctx.eventTime, Self.time(h.timestamp))
                let hp = h.location(in: self).p
                tool.move(ctx, hp, viewport.toCanvas(hp))
            }
        } else {
            applyPen(t)
            tool.move(ctx, pos, viewport.toCanvas(pos))
        }
        if tool.needsTicks { startTicks() }
        setNeedsDisplay()
    }

    override func touchesEnded(_ set: Set<UITouch>, with event: UIEvent?) { finish(set, cancelled: false) }
    override func touchesCancelled(_ set: Set<UITouch>, with event: UIEvent?) { finish(set, cancelled: true) }

    private func finish(_ set: Set<UITouch>, cancelled: Bool) {
        let upTime = set.first.map { Self.time($0.timestamp) } ?? Self.now()
        touches.removeAll { set.contains($0) }
        if touches.count < 2 { twoFingerPending = false; twoFingerTimer?.invalidate() }
        if touches.count >= 2 { lastCentroid = centroid(); lastSpan = span() }
        if !touches.isEmpty { return }
        guard let viewport else { resetGesture(); return }
        if let g = guideDrag {
            g.end(moved: guideMoved, scale: viewport.scale)
            if g.isPick && guideMoved { viewport.settle() }
            resetGesture()
            setNeedsDisplay()
            return
        }
        guard let tool, let ctx else { resetGesture(); return }
        ctx.eventTime = upTime
        if cancelled {
            if dragging && !panOnly { tool.cancel(ctx) }
            if multiTouch || panOnly { viewport.settle() }
        } else if threeFinger {
        } else if longPressed {
            if multiTouch { viewport.settle() }
        } else if multiTouch || (dragging && panOnly) {
            viewport.settle()
        } else if dragging {
            tool.end(ctx)
        } else {
            tool.tap(ctx, viewport.toCanvas(ctx.start), upTime)
        }
        tool.up(ctx)
        resetGesture()
        setNeedsDisplay()
    }

    private func resetGesture() {
        cancelLongPress()
        twoFingerTimer?.invalidate(); twoFingerTimer = nil
        tickTimer?.invalidate(); tickTimer = nil
        tool = nil; ctx = nil; guideDrag = nil
        dragging = false; panOnly = false; multiTouch = false; longPressed = false; atEdge = false
        twoFingerPending = false; threeFinger = false; threeDone = false; lastCentroid = nil; finger = nil
    }

    // MARK: Önbellek

    private func activePreview(_ c: CanvasUi) -> CanvasPreview? {
        guard let p = preview, p.w == c.width, p.h == c.height, p.strokes.count == c.strokes.count else { return nil }
        return p.p
    }

    private func excludedIds(_ c: CanvasUi, _ selected: Set<String>, _ ap: CanvasPreview?) -> Set<String> {
        if mode.transformsSelection { return selected }
        if mode == .SHAPE_EDIT, let s = c.strokes.first(where: { selected.contains($0.id) && $0.shape != nil }) { return [s.id] }
        if mode == .SELECT, let s = ap?.editedStroke { return [s.id] }
        return []
    }

    private static func isAppend(_ previous: [Stroke], _ next: [Stroke]) -> Bool {
        if next.count < previous.count { return false }
        for i in previous.indices where previous[i] != next[i] { return false }
        return true
    }

    /// Önbelleği güncel tutar: görüntü değişmediyse bir şey yapmaz, yalnızca yeni çizim eklendiyse onu mevcut görüntüye
    /// çizer, aksi halde arka planda baştan üretir (bu sırada eski görüntü + yeni çizimler gösterilir).
    private func updateCache(_ key: CacheKey, _ target: [Stroke], _ version: Int64) {
        if let img = cacheImage, cacheKey == key {
            if cacheVersion == version { cacheDrawn = target; return }
            if Self.isAppend(cacheDrawn, target) {
                let added = Array(target[cacheDrawn.count...])
                if !added.contains(where: { $0.brushType == .ERASER }) {
                    let px = contentScaleFactor
                    cacheImage = Bitmaps.draw(img.width, img.height) { g in
                        Bitmaps.drawUpright(g, img, in: CGRect(x: 0, y: 0, width: img.width, height: img.height))
                        g.scaleBy(x: px, y: px)
                        Self.clipPaper(g, key)
                        g.translateBy(x: CGFloat(key.offset.x), y: CGFloat(key.offset.y))
                        g.scaleBy(x: CGFloat(key.scale), y: CGFloat(key.scale))
                        StrokeRenderer.drawStrokeList(g, added)
                    }
                    cacheDrawn = target
                    cacheVersion = version
                    return
                }
            }
        }
        if let r = requested, r.0 == key, r.1 == version { return }
        requested = (key, version)
        renderTask?.cancel()
        let px = contentScaleFactor
        renderTask = Task { [weak self] in
            let img = await Task.detached(priority: .userInitiated) { () -> CGImage? in
                let w = max(1, Int(CGFloat(key.w) * px)), h = max(1, Int(CGFloat(key.h) * px))
                if Task.isCancelled { return nil }
                return Bitmaps.draw(w, h) { g in
                    g.scaleBy(x: px, y: px)
                    Self.clipPaper(g, key)
                    g.translateBy(x: CGFloat(key.offset.x), y: CGFloat(key.offset.y))
                    g.scaleBy(x: CGFloat(key.scale), y: CGFloat(key.scale))
                    g.beginTransparencyLayer(auxiliaryInfo: nil)
                    StrokeRenderer.drawStrokeList(g, target)
                    g.endTransparencyLayer()
                }
            }.value
            guard let self, let img, !Task.isCancelled else { return }
            self.cacheImage = img
            self.cacheKey = key
            self.cacheDrawn = target
            self.cacheVersion = version
            self.requested = nil
            if self.live.finished { self.live.release() }
            self.setNeedsDisplay()
        }
    }

    nonisolated private static func clipPaper(_ g: CGContext, _ key: CacheKey) {
        g.clip(to: CGRect(x: CGFloat(key.offset.x), y: CGFloat(key.offset.y),
                          width: CGFloat(Float(key.canvasW) * key.scale), height: CGFloat(Float(key.canvasH) * key.scale)))
    }

    // MARK: Çizim

    override func draw(_ rect: CGRect) {
        guard let g = UIGraphicsGetCurrentContext(), let vm, let viewport, let c = canvas else { return }
        let accent = CanvasColors.accent, bg = CanvasColors.background
        let scale = viewport.scale, offset = viewport.offset
        let ap = activePreview(c)
        let selected = vm.store.selectedIds
        let visible = Layers.visible(c.strokes, c.layers)
        let excluded = excludedIds(c, selected, ap)
        let target = excluded.isEmpty ? visible : visible.filter { !excluded.contains($0.id) }
        let key = CacheKey(w: Int(bounds.width), h: Int(bounds.height), scale: viewport.settledScale, offset: viewport.settledOffset,
                           canvasW: c.width, canvasH: c.height, excluded: excluded, page: c.currentPage)
        updateCache(key, target, vm.store.visualVersion)
        // Önbellek güncellendiyse canlı katman bırakılır
        if live.finished && cacheVersion == vm.store.visualVersion && cacheKey == key { live.release() }

        var paperCanvas = FRect(0, 0, Float(c.width), Float(c.height))
        if case let .expand(r)? = ap { paperCanvas = r }
        let paper = viewport.toScreen(paperCanvas)
        let paperCG = paper.cg
        let paperColor = BackgroundRenderer.parseColor(c.pagePaperHex, ArgbColor.white)

        // Kağıt ve gölgesi
        g.saveGState()
        g.setShadow(offset: CGSize(width: 4, height: 6), blur: 18, color: UIColor.black.withAlphaComponent(0.18).cgColor)
        g.setFillColor(ArgbColor.cgColor(paperColor | 0xFF00_0000))
        g.addPath(UIBezierPath(roundedRect: paperCG, cornerRadius: 6).cgPath)
        g.fillPath()
        g.restoreGState()
        // Arka plan resmi ve deseni (kağıt renginin üstünde, kılavuz ve çizimlerin altında)
        let decor = c.pageBackground
        if !decor.isEmpty {
            g.saveGState()
            g.addPath(UIBezierPath(roundedRect: paperCG, cornerRadius: 6).cgPath); g.clip()
            BackgroundRenderer.drawCanvasBackground(g, decor, paper.left, paper.top, paper.right, paper.bottom, scale: scale, paper: paperColor, vector: false)
            g.restoreGState()
        }
        GuideRenderer.draw(g, c.guide, paper: paperCG, scale: scale, highlightVanish: mode == .PERSPECTIVE)
        GuideRenderer.drawDraw3d(g, c.guide, paper: paperCG, scale: scale, canvasW: c.width, canvasH: c.height)
        if !c.pageSettings.isEmpty {
            let origin = viewport.toScreen(FRect(0, 0, Float(c.width), Float(c.height)))
            PageDecorRenderer.draw(g, c.pageSettings, page: c.currentPage, pageCount: c.pageCount, width: Float(c.width), height: Float(c.height),
                                   paper: paperColor, left: origin.left, top: origin.top, scale: scale, cover: c.hasCover)
        }

        let fresh = cacheKey != nil && cacheDrawn.count <= target.count && Self.isAppend(cacheDrawn, target)
        let pending: [Stroke] = (cacheVersion != vm.store.visualVersion && fresh)
            ? Array(target[(cacheImage == nil ? 0 : cacheDrawn.count)...]).filter { $0.brushType != .ERASER } : []
        let liveVisible = live.isActive && !(live.finished && cacheVersion == vm.store.visualVersion)
        let moving = visible.filter { excluded.contains($0.id) }

        g.saveGState()
        g.clip(to: paperCG)
        g.beginTransparencyLayer(auxiliaryInfo: nil)
        if let img = cacheImage, let k = cacheKey {
            let f = CGFloat(scale / k.scale)
            g.saveGState()
            g.translateBy(x: CGFloat(offset.x) - CGFloat(k.offset.x) * f, y: CGFloat(offset.y) - CGFloat(k.offset.y) * f)
            g.scaleBy(x: f, y: f)
            Bitmaps.drawUpright(g, img, in: CGRect(x: 0, y: 0, width: CGFloat(k.w), height: CGFloat(k.h)))
            g.restoreGState()
        }
        g.saveGState()
        g.translateBy(x: CGFloat(offset.x), y: CGFloat(offset.y))
        g.scaleBy(x: CGFloat(scale), y: CGFloat(scale))
        for s in pending where !(liveVisible && s.id == live.strokeId) { StrokeRenderer.drawStroke(g, s) }
        drawPreviewShapes(g, ap, scale, accent)
        if !moving.isEmpty { drawMoving(g, moving, ap) }
        if overlay.formulaSlots, let f = c.strokes.first(where: { selected.contains($0.id) && $0.brushType == .FORMULA }) {
            FormulaRenderer.drawSlots(g, f, activeSlot: overlay.formulaSlot, color: accent.argb)
        }
        if mode == .SHAPE_EDIT, let v = overlay.vector,
           let edited = ap?.editedStroke ?? c.strokes.first(where: { selected.contains($0.id) && $0.shape != nil }) {
            drawVectorEditing(g, VectorEdit.graph(edited, coarse: c.kind.simple), vm.shapes.tool, v, scale, dragging: ap?.editedStroke != nil)
        }
        if mode == .PEN { drawPenPath(g, scale) }
        if let parts = overlay.correction?.filter({ $0.count >= 2 }), !parts.isEmpty {
            let path = CGMutablePath()
            for pts in parts { path.move(to: pts[0].cg); for p in pts.dropFirst() { path.addLine(to: p.cg) } }
            g.setLineCap(.round)
            g.addPath(path); g.setStrokeColor(bg.withAlphaComponent(0.85).cgColor); g.setLineWidth(CGFloat(6 / scale)); g.strokePath()
            g.addPath(path); g.setStrokeColor(accent.cgColor); g.setLineWidth(CGFloat(3 / scale))
            g.setLineDash(phase: 0, lengths: [CGFloat(12 / scale), CGFloat(7 / scale)]); g.strokePath()
            g.setLineDash(phase: 0, lengths: [])
        }
        if liveVisible, let s = live.stroke() { StrokeRenderer.drawStroke(g, s) }
        // Silgi nesneleri silmez: silerken nesneler canlı silginin üstünde gösterilir
        if liveVisible && live.isEraser {
            for s in visible where s.brushType.isObject && !s.hidden { StrokeRenderer.drawStroke(g, s) }
        }
        g.restoreGState()
        g.endTransparencyLayer()
        g.restoreGState()

        drawRuler(g, viewport)

        // Kalem havadayken fırça boyu imleci
        if let h = hover, mode == .DRAW {
            let b = vm.brush.settings
            let w = b.brushType.inkWidth(b.sizePx, strength: b.strength)
            let r = CGFloat(max(3, w * scale / 2))
            let rect = CGRect(x: CGFloat(h.x) - r, y: CGFloat(h.y) - r, width: r * 2, height: r * 2)
            g.setStrokeColor(UIColor.white.withAlphaComponent(0.9).cgColor); g.setLineWidth(3); g.strokeEllipse(in: rect.insetBy(dx: -1, dy: -1))
            g.setStrokeColor(b.brushType == .ERASER ? CanvasColors.content.cgColor : ArgbColor.cgColor(ArgbColor.hex(b.colorHex)))
            g.setLineWidth(1.5); g.strokeEllipse(in: rect)
        }

        drawSelectionOverlays(g, vm, c, selected, ap, paper, paperCanvas)
        if mode == .SHAPE_EDIT && vm.shapes.tool == .PARAMS { drawParamHandles(g, vm.shapes.paramShapes.handles(ap?.editedStroke), viewport, accent) }
        if let f = finger, mode == .DRAW || mode == .PAINT { drawFingerBubble(g, f, vm.brush.settings) }
    }

    private func drawPreviewShapes(_ g: CGContext, _ ap: CanvasPreview?, _ scale: Float, _ accent: UIColor) {
        guard let ap else { return }
        let dash: [CGFloat] = [CGFloat(10 / scale), CGFloat(8 / scale)]
        switch ap {
        case let .shape(id, s, e):
            for st in actions.buildShape(id, s, e) { StrokeRenderer.drawStroke(g, st) }
        case let .box(s, e):
            let r = CGRect(x: CGFloat(min(s.x, e.x)), y: CGFloat(min(s.y, e.y)), width: CGFloat(abs(e.x - s.x)), height: CGFloat(abs(e.y - s.y)))
            g.setStrokeColor(accent.cgColor); g.setLineWidth(CGFloat(2 / scale)); g.setLineDash(phase: 0, lengths: dash)
            g.stroke(r); g.setLineDash(phase: 0, lengths: [])
        case let .lasso(pts) where pts.count >= 2:
            let path = CGMutablePath()
            path.move(to: pts[0].cg); for p in pts.dropFirst() { path.addLine(to: p.cg) }; path.closeSubpath()
            g.addPath(path); g.setFillColor(accent.withAlphaComponent(0.10).cgColor); g.fillPath()
            g.addPath(path); g.setStrokeColor(accent.cgColor); g.setLineWidth(CGFloat(2 / scale)); g.setLineDash(phase: 0, lengths: dash)
            g.strokePath(); g.setLineDash(phase: 0, lengths: [])
        case let .guideline(a, b):
            g.move(to: a.cg); g.addLine(to: b.cg)
            g.setStrokeColor(accent.cgColor); g.setLineWidth(CGFloat(2 / scale))
            g.setLineDash(phase: 0, lengths: [CGFloat(12 / scale), CGFloat(8 / scale)]); g.strokePath(); g.setLineDash(phase: 0, lengths: [])
        default: break
        }
    }

    /// Taşı/Döndür önizlemesi tuval dönüşümüyle çizilir (her karede bütün noktalar yeniden hesaplanmaz).
    private func drawMoving(_ g: CGContext, _ moving: [Stroke], _ ap: CanvasPreview?) {
        g.saveGState()
        g.beginTransparencyLayer(auxiliaryInfo: nil)
        switch ap {
        case let .move(d)?:
            g.translateBy(x: CGFloat(d.x), y: CGFloat(d.y)); StrokeRenderer.drawStrokeList(g, moving)
        case let .rotate(deg, pv)?:
            g.translateBy(x: CGFloat(pv.x), y: CGFloat(pv.y)); g.rotate(by: CGFloat(deg) * .pi / 180)
            g.translateBy(x: -CGFloat(pv.x), y: -CGFloat(pv.y)); StrokeRenderer.drawStrokeList(g, moving)
        case let .scale(sx, sy, pv)?:
            let ids = Set(moving.map(\.id))
            StrokeRenderer.drawStrokeList(g, ElementOps.scale(moving, ids, sx, sy, pivot: pv))
        case let .shapeEdit(s)?:
            StrokeRenderer.drawStrokeList(g, moving.map { $0.id == s.id ? s : $0 })
        default:
            StrokeRenderer.drawStrokeList(g, moving)
        }
        g.endTransparencyLayer()
        g.restoreGState()
    }

    private func drawVectorEditing(_ g: CGContext, _ graph: VectorEdit.Graph, _ tool: ShapeTool, _ sel: VectorSelection, _ scale: Float, dragging: Bool) {
        let accent = CanvasColors.accent, bg = CanvasColors.background
        let px = CGFloat(1 / scale)
        let lines = tool == .LINES
        g.setLineCap(.round)
        func polyline(_ pts: [P], _ color: UIColor, _ w: CGFloat) {
            guard pts.count >= 2 else { return }
            g.move(to: pts[0].cg); for p in pts.dropFirst() { g.addLine(to: p.cg) }
            g.setStrokeColor(color.cgColor); g.setLineWidth(w); g.strokePath()
        }
        func circle(_ p: P, _ r: CGFloat, fill: UIColor? = nil, stroke: UIColor? = nil, width: CGFloat = 0) {
            let rect = CGRect(x: CGFloat(p.x) - r, y: CGFloat(p.y) - r, width: r * 2, height: r * 2)
            if let fill { g.setFillColor(fill.cgColor); g.fillEllipse(in: rect) }
            if let stroke { g.setStrokeColor(stroke.cgColor); g.setLineWidth(width); g.strokeEllipse(in: rect) }
        }
        if lines { for e in graph.edges { polyline(e.points, accent.withAlphaComponent(0.35), 2.5 * px) } }
        let edge = sel.edge.flatMap { graph.edgeBetween($0.0, $0.1) }
        if let edge {
            polyline(edge.points, accent.withAlphaComponent(0.25), 12 * px)
            polyline(edge.points, accent, 4 * px)
        }
        let chosen: Set<Int> = dragging && !lines ? [] : Set(sel.nodes.compactMap { graph.nodeNear($0)?.id })
        let nodeR = (lines ? 3.5 : 5.5) * px
        for n in graph.nodes where !chosen.contains(n.id) {
            let free = graph.isFree(n.id)
            if free { g.setLineDash(phase: 0, lengths: [3 * px, 2 * px]) }
            circle(n.point, nodeR, fill: bg, stroke: free ? CanvasColors.muted : accent, width: 2 * px)
            g.setLineDash(phase: 0, lengths: [])
        }
        for id in chosen {
            let p = graph.nodes[id].point
            circle(p, 11 * px, fill: accent.withAlphaComponent(0.25))
            circle(p, 7 * px, fill: accent)
            circle(p, 2.5 * px, fill: bg)
        }
        if let from = sel.linkFrom { circle(from, 13 * px, stroke: accent, width: 2.5 * px) }
        if let edge, lines {
            let mid = VectorEdit.bendHandle(edge)
            circle(mid, 9 * px, fill: bg, stroke: accent, width: 2.5 * px)
            circle(mid, 3.5 * px, fill: accent)
            if !dragging {
                let h = VectorEdit.turnHandle(edge, 39 / scale)
                polyline([edge.end, h], accent, 1.5 * px)
                circle(h, 11 * px, fill: bg, stroke: accent, width: 2.5 * px)
            }
        }
    }

    private func drawPenPath(_ g: CGContext, _ scale: Float) {
        let anchors = overlay.penAnchors + (overlay.penPending.map { [$0] } ?? [])
        if anchors.isEmpty { return }
        let accent = CanvasColors.accent, bg = CanvasColors.background
        let px = CGFloat(1 / scale)
        let pts = PenPath.sample(anchors, closed: false)
        if pts.count >= 2 {
            g.move(to: pts[0].cg); for p in pts.dropFirst() { g.addLine(to: p.cg) }
            g.setStrokeColor(accent.cgColor); g.setLineWidth(2 * px); g.setLineCap(.round); g.strokePath()
        }
        for (i, a) in anchors.enumerated() {
            if let h = a.handle {
                let back = P(a.point.x * 2 - h.x, a.point.y * 2 - h.y)
                g.move(to: back.cg); g.addLine(to: h.cg)
                g.setStrokeColor(accent.withAlphaComponent(0.6).cgColor); g.setLineWidth(1.2 * px); g.strokePath()
                g.setFillColor(accent.cgColor)
                for q in [h, back] { g.fillEllipse(in: CGRect(x: CGFloat(q.x) - 3 * px, y: CGFloat(q.y) - 3 * px, width: 6 * px, height: 6 * px)) }
            }
            let s = (i == 0 && anchors.count > 2 ? 7 : 5) * px
            let r = CGRect(x: CGFloat(a.point.x) - s, y: CGFloat(a.point.y) - s, width: s * 2, height: s * 2)
            g.setFillColor(bg.cgColor); g.fill(r)
            g.setStrokeColor(accent.cgColor); g.setLineWidth(1.8 * px); g.stroke(r)
        }
    }

    /// v40 cetvel ve şekil kalıbı: gövde, kenarlar ve tutamaklar.
    private func drawRuler(_ g: CGContext, _ viewport: ViewportState) {
        guard let r = actions.ruler() else { return }
        let accent = CanvasColors.accent
        let s = max(viewport.scale, 0.01)
        let gap = GuideMetrics.HANDLE_GAP / s
        func path(_ pts: [Float], _ closed: Bool) -> CGPath {
            let p = CGMutablePath()
            var i = 0
            while i + 1 < pts.count {
                let q = viewport.toScreen(P(pts[i], pts[i + 1])).cg
                if i == 0 { p.move(to: q) } else { p.addLine(to: q) }
                i += 2
            }
            if closed { p.closeSubpath() }
            return p
        }
        func knob(_ p: P, _ filled: Bool) {
            let q = viewport.toScreen(p)
            let rad = CGFloat(GuideMetrics.HANDLE_RADIUS * 0.6)
            let rect = CGRect(x: CGFloat(q.x) - rad, y: CGFloat(q.y) - rad, width: rad * 2, height: rad * 2)
            g.setFillColor((filled ? accent : CanvasColors.background).cgColor); g.fillEllipse(in: rect)
            g.setStrokeColor(accent.cgColor); g.setLineWidth(2); g.strokeEllipse(in: rect)
        }
        if let shape = r.shape(), r.session == .RULER {
            let fill = CGMutablePath()
            for c in shape.contours where c.closed { fill.addPath(path(c.points, true)) }
            g.addPath(fill); g.setFillColor(UIColor(argb: 0x55B0_C4DE).cgColor); g.fillPath(using: .evenOdd)
            for c in shape.contours { g.addPath(path(c.points, c.closed)) }
            g.setStrokeColor(accent.cgColor); g.setLineWidth(1.5); g.strokePath()
            if !r.locked {
                for h in [RulerHandle.ROTATE, .SIZE, .MINOR] { if let p = shape.handle(h, gap: gap) { knob(p, h == .ROTATE) } }
            }
            // Ölçü yazısı
            let len = RulerUnits.format(shape.measuredLength, r.settings.unit, r.settings.scale)
            let mid = viewport.toScreen(P(shape.pose.cx, shape.pose.cy))
            drawChip(g, len, P(mid.x, mid.y), CanvasColors.content, CanvasColors.background, above: false, centered: true)
        } else if let ghost = r.template, r.session == .TEMPLATE {
            for c in ghost.contours { g.addPath(path(c.points, c.closed)) }
            g.setStrokeColor(UIColor(argb: ArgbColor.withAlpha(ArgbColor.hex(ghost.colorHex), 0xAA)).cgColor)
            g.setLineWidth(2); g.setLineDash(phase: 0, lengths: [8, 6]); g.strokePath(); g.setLineDash(phase: 0, lengths: [])
            if !r.locked { knob(ghost.moveHandle(gap: gap), true); knob(ghost.scaleHandle(gap: gap), false) }
        }
    }

    private func drawChip(_ g: CGContext, _ text: String, _ anchor: P, _ background: UIColor, _ content: UIColor,
                          above: Bool = true, centered: Bool = false) {
        let attrs: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 11, weight: .medium), .foregroundColor: content]
        let size = (text as NSString).size(withAttributes: attrs)
        let w = size.width + 12, h = size.height + 6
        let left = centered ? CGFloat(anchor.x) - w / 2 : CGFloat(anchor.x)
        let top = above ? CGFloat(anchor.y) - h - 3 : CGFloat(anchor.y) + 3
        let r = CGRect(x: left, y: top, width: w, height: h)
        g.setFillColor(background.cgColor)
        g.addPath(UIBezierPath(roundedRect: r, cornerRadius: h / 2).cgPath); g.fillPath()
        UIGraphicsPushContext(g)
        (text as NSString).draw(at: CGPoint(x: left + 6, y: top + 3), withAttributes: attrs)
        UIGraphicsPopContext()
    }

    private func transformRect(_ r: FRect, _ ap: CanvasPreview?) -> FRect {
        switch ap {
        case let .move(d)?: return r.translate(d.x, d.y)
        case let .scale(sx, sy, pv)?:
            let ax = pv.x + (r.left - pv.x) * sx, ay = pv.y + (r.top - pv.y) * sy
            let bx = pv.x + (r.right - pv.x) * sx, by = pv.y + (r.bottom - pv.y) * sy
            return FRect(min(ax, bx), min(ay, by), max(ax, bx), max(ay, by))
        default: return r
        }
    }

    private func withRotation(_ g: CGContext, _ ap: CanvasPreview?, _ viewport: ViewportState, _ body: () -> Void) {
        g.saveGState()
        if case let .rotate(deg, pv)? = ap {
            let c = viewport.toScreen(pv)
            g.translateBy(x: CGFloat(c.x), y: CGFloat(c.y)); g.rotate(by: CGFloat(deg) * .pi / 180)
            g.translateBy(x: -CGFloat(c.x), y: -CGFloat(c.y))
        }
        body()
        g.restoreGState()
    }

    private func drawSelectionOverlays(_ g: CGContext, _ vm: EditorViewModel, _ c: CanvasUi, _ selected: Set<String>, _ ap: CanvasPreview?,
                                       _ paper: FRect, _ paperCanvas: FRect) {
        guard let viewport else { return }
        let accent = CanvasColors.accent, bg = CanvasColors.background, content = CanvasColors.content
        let selectedStrokes = selected.isEmpty ? [] : c.strokes.filter { selected.contains($0.id) }
        let transformPreview = mode.transformsSelection ? ap : nil
        func dot(_ p: P, _ r: CGFloat, _ color: UIColor) {
            g.setFillColor(color.cgColor); g.fillEllipse(in: CGRect(x: CGFloat(p.x) - r, y: CGFloat(p.y) - r, width: r * 2, height: r * 2))
        }
        // Seçili elemanların kutusu ve köşesinde adı
        if mode != .CROP && mode != .EXPAND && !selected.isEmpty {
            let byId = Dictionary(c.strokes.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            for e in vm.store.elements.selectedElements(selected) {
                guard let b = ElementOps.bounds(e.strokeIds.compactMap { byId[$0] }) else { continue }
                let box = viewport.toScreen(transformRect(b, transformPreview)).inflate(3)
                withRotation(g, transformPreview, viewport) {
                    g.setStrokeColor(accent.cgColor); g.setLineWidth(1.5); g.setLineDash(phase: 0, lengths: [12, 8])
                    g.stroke(box.cg); g.setLineDash(phase: 0, lengths: [])
                    drawChip(g, "\(L(e.brushType.titleKey)) \(e.number)", box.topLeft, accent, .white)
                }
            }
        }
        let singleText = selected.count == 1 && selectedStrokes.contains { $0.brushType == .TEXT }
        if singleText && SimpleTextResize.handlesShown(mode, c.kind), let text = selectedStrokes.first(where: { $0.brushType == .TEXT }) {
            let shown = ap?.editedStroke.flatMap { $0.id == text.id ? $0 : nil } ?? text
            for (p, _) in TextBoxResize.handles(shown) {
                let q = viewport.toScreen(p)
                dot(q, 9, bg); dot(q, 7, accent)
            }
        }
        if selected.count == 1 && SimpleElementResize.handlesShown(mode, c.kind),
           let el = selectedStrokes.first, SimpleElementResize.eligible(el) {
            let shown = ap?.editedStroke.flatMap { $0.id == el.id ? $0 : nil } ?? el
            if let b = SimpleElementResize.bounds(shown) {
                for (p, _) in SimpleElementResize.corners(b) { let q = viewport.toScreen(p); dot(q, 9, bg); dot(q, 7, accent) }
            }
        }
        // v27 seçili balonun kuyruk uçları
        if (mode == .SELECT || mode == .SCALE) && selected.count == 1, let balloon = selectedStrokes.first(where: { $0.text?.box?.balloon != nil }) {
            let shown = ap?.editedStroke.flatMap { $0.id == balloon.id ? $0 : nil } ?? balloon
            for p in BalloonTails.positions(shown) {
                let q = viewport.toScreen(p)
                dot(q, 9, bg); dot(q, 7, UIColor(argb: 0xFFFF_B300))
                g.setStrokeColor(content.cgColor); g.setLineWidth(1.5)
                g.strokeEllipse(in: CGRect(x: CGFloat(q.x) - 7, y: CGFloat(q.y) - 7, width: 14, height: 14))
            }
        }
        if mode.transformsSelection, let sb = ElementOps.selectionBounds(c.strokes, selected) {
            drawTransformFrame(g, sb, transformPreview, viewport, squareHandles: !singleText)
        }
        if mode == .EXPAND { drawExpandFrame(g, paper, paperCanvas) }
        if mode == .DIAGRAM_EDIT, let d = selectedStrokes.first(where: { $0.diagram != nil }) { drawDiagramEditing(g, d, viewport) }
        if mode == .CROP, let crop = overlay.cropRect {
            let box = viewport.toScreen(crop)
            let area = overlay.cropArea.map { viewport.toScreen($0) } ?? paper
            g.setFillColor(UIColor.black.withAlphaComponent(overlay.cropArea != nil ? 0.35 : 0.45).cgColor)
            g.fill(CGRect(x: CGFloat(area.left), y: CGFloat(area.top), width: CGFloat(area.width), height: CGFloat(max(0, box.top - area.top))))
            g.fill(CGRect(x: CGFloat(area.left), y: CGFloat(box.bottom), width: CGFloat(area.width), height: CGFloat(max(0, area.bottom - box.bottom))))
            g.fill(CGRect(x: CGFloat(area.left), y: CGFloat(box.top), width: CGFloat(max(0, box.left - area.left)), height: CGFloat(box.height)))
            g.fill(CGRect(x: CGFloat(box.right), y: CGFloat(box.top), width: CGFloat(max(0, area.right - box.right)), height: CGFloat(box.height)))
            if overlay.cropArea != nil {
                g.setStrokeColor(accent.withAlphaComponent(0.6).cgColor); g.setLineWidth(1.5); g.setLineDash(phase: 0, lengths: [8, 6])
                g.stroke(area.cg); g.setLineDash(phase: 0, lengths: [])
            }
            drawExpandFrame(g, box, crop)
        }
    }

    private func drawTransformFrame(_ g: CGContext, _ bounds: FRect, _ ap: CanvasPreview?, _ viewport: ViewportState, squareHandles: Bool) {
        let accent = CanvasColors.accent, bg = CanvasColors.background
        let box = viewport.toScreen(transformRect(bounds, ap)).inflate(8)
        let h: CGFloat = 10
        withRotation(g, ap, viewport) {
            g.setStrokeColor(accent.withAlphaComponent(0.9).cgColor); g.setLineWidth(1.5); g.stroke(box.cg)
            let cx = box.center.x, cy = box.center.y
            if mode == .SCALE && squareHandles {
                for p in [box.topLeft, P(cx, box.top), box.topRight, P(box.left, cy), P(box.right, cy), box.bottomLeft, P(cx, box.bottom), box.bottomRight] {
                    let r = CGRect(x: CGFloat(p.x) - h / 2, y: CGFloat(p.y) - h / 2, width: h, height: h)
                    g.setFillColor(bg.cgColor); g.fill(r)
                    g.setStrokeColor(accent.cgColor); g.setLineWidth(2); g.stroke(r)
                }
            } else if mode == .ROTATE {
                let top = P(cx, box.top), knob = P(cx, box.top - 30)
                g.move(to: top.cg); g.addLine(to: knob.cg); g.setStrokeColor(accent.cgColor); g.setLineWidth(1.5); g.strokePath()
                let kr = CGRect(x: CGFloat(knob.x) - 8, y: CGFloat(knob.y) - 8, width: 16, height: 16)
                g.setFillColor(bg.cgColor); g.fillEllipse(in: kr)
                g.setLineWidth(2); g.strokeEllipse(in: kr)
                g.setFillColor(accent.cgColor); g.fillEllipse(in: CGRect(x: CGFloat(cx) - 3, y: CGFloat(cy) - 3, width: 6, height: 6))
            }
        }
        var label: String?
        switch ap {
        case let .rotate(d, _)?: label = "\(Int(d.rounded()))°"
        case let .scale(sx, sy, _)?:
            label = sx == sy ? "%\(Int((sx * 100).rounded()))" : "%\(Int((sx * 100).rounded())) × %\(Int((sy * 100).rounded()))"
        default: break
        }
        if let label { drawChip(g, label, P(box.center.x, box.bottom + 10), CanvasColors.content, bg, above: false, centered: true) }
    }

    private func drawExpandFrame(_ g: CGContext, _ paper: FRect, _ paperCanvas: FRect) {
        let accent = CanvasColors.accent, bg = CanvasColors.background
        g.setStrokeColor(accent.cgColor); g.setLineWidth(2); g.setLineDash(phase: 0, lengths: [16, 10])
        g.stroke(paper.cg); g.setLineDash(phase: 0, lengths: [])
        let long: CGFloat = 34, thick: CGFloat = 8
        let cy = CGFloat(paper.center.y), cx = CGFloat(paper.center.x)
        g.setFillColor(accent.cgColor)
        for x in [CGFloat(paper.left), CGFloat(paper.right)] {
            g.addPath(UIBezierPath(roundedRect: CGRect(x: x - thick / 2, y: cy - long / 2, width: thick, height: long), cornerRadius: thick / 2).cgPath)
        }
        for y in [CGFloat(paper.top), CGFloat(paper.bottom)] {
            g.addPath(UIBezierPath(roundedRect: CGRect(x: cx - long / 2, y: y - thick / 2, width: long, height: thick), cornerRadius: thick / 2).cgPath)
        }
        g.fillPath()
        for p in [paper.topLeft, paper.topRight, paper.bottomLeft, paper.bottomRight] {
            let r = CGRect(x: CGFloat(p.x) - 8, y: CGFloat(p.y) - 8, width: 16, height: 16)
            g.setFillColor(bg.cgColor); g.fillEllipse(in: r)
            g.setStrokeColor(accent.cgColor); g.setLineWidth(2.5); g.strokeEllipse(in: r)
        }
        drawChip(g, "\(Int(paperCanvas.width.rounded())) × \(Int(paperCanvas.height.rounded()))", P(paper.center.x, paper.top - 14),
                 CanvasColors.content, bg, centered: true)
    }

    private func drawDiagramEditing(_ g: CGContext, _ d: Stroke, _ viewport: ViewportState) {
        guard let spec = d.diagram else { return }
        let accent = CanvasColors.accent, bg = CanvasColors.background
        let focusedNode: String? = { if case let .node(id)? = overlay.diagramFocus { return id }; return nil }()
        let sourceId = overlay.diagramLink?.fromId
        for node in spec.nodes {
            let corners = DiagramRenderer.nodeCorners(d, node).map { viewport.toScreen($0) }
            if corners.count < 4 { continue }
            g.move(to: corners[0].cg); for q in corners.dropFirst() { g.addLine(to: q.cg) }; g.closePath()
            let strong = node.id == focusedNode || node.id == sourceId
            g.setStrokeColor(accent.withAlphaComponent(strong ? 1 : 0.45).cgColor); g.setLineWidth(strong ? 4 : 1.5)
            g.setLineDash(phase: 0, lengths: strong ? [] : [7, 6]); g.strokePath(); g.setLineDash(phase: 0, lengths: [])
        }
        if case let .edge(i)? = overlay.diagramFocus, spec.edges.indices.contains(i) {
            let edge = spec.edges[i]
            if let a = spec.nodes.first(where: { $0.id == edge.from }), let b = spec.nodes.first(where: { $0.id == edge.to }) {
                let route = DiagramRenderer.edgePath(d, edge, a, b).map { viewport.toScreen($0) }
                if route.count >= 2 {
                    g.move(to: route[0].cg); for q in route.dropFirst() { g.addLine(to: q.cg) }
                    g.setStrokeColor(accent.withAlphaComponent(0.5).cgColor); g.setLineWidth(10); g.setLineCap(.round); g.setLineJoin(.round); g.strokePath()
                }
            }
        }
        let knob: CGFloat = 9
        if let hn = spec.nodes.first(where: { $0.id == (sourceId ?? focusedNode) }) {
            for side in DiagramRenderer.sides {
                guard let pp = DiagramRenderer.portPoint(d, hn, side) else { continue }
                let p = viewport.toScreen(pp)
                let chosen = overlay.diagramLink != nil && overlay.diagramLink?.fromSide == side
                let r = CGRect(x: CGFloat(p.x) - knob, y: CGFloat(p.y) - knob, width: knob * 2, height: knob * 2)
                g.setFillColor((chosen ? accent : bg).cgColor); g.fillEllipse(in: r)
                g.setStrokeColor(accent.cgColor); g.setLineWidth(2.5); g.strokeEllipse(in: r)
                if !chosen {
                    let arm = knob * 0.5
                    g.move(to: CGPoint(x: CGFloat(p.x) - arm, y: CGFloat(p.y))); g.addLine(to: CGPoint(x: CGFloat(p.x) + arm, y: CGFloat(p.y)))
                    g.move(to: CGPoint(x: CGFloat(p.x), y: CGFloat(p.y) - arm)); g.addLine(to: CGPoint(x: CGFloat(p.x), y: CGFloat(p.y) + arm))
                    g.setLineWidth(2); g.strokePath()
                }
            }
        }
        if let sourceId {
            for node in spec.nodes where node.id != sourceId {
                for side in DiagramRenderer.sides {
                    guard let pp = DiagramRenderer.portPoint(d, node, side) else { continue }
                    let p = viewport.toScreen(pp), k = knob * 0.55
                    let r = CGRect(x: CGFloat(p.x) - k, y: CGFloat(p.y) - k, width: k * 2, height: k * 2)
                    g.setFillColor(bg.cgColor); g.fillEllipse(in: r)
                    g.setStrokeColor(accent.withAlphaComponent(0.8).cgColor); g.setLineWidth(2); g.strokeEllipse(in: r)
                }
            }
        }
    }

    /// Çizim sırasında parmağın yanında seçili fırçanın rengi.
    /// v45 parametreli şeklin tutamakları (Android `ParamHandlesLayer`): beyaz dolgu, vurgu halkası ve merkez.
    private func drawParamHandles(_ g: CGContext, _ handles: [(String, P)], _ viewport: ViewportState, _ accent: UIColor) {
        for (_, p) in handles {
            let sp = viewport.toScreen(p)
            let r: CGFloat = 11
            let rect = CGRect(x: CGFloat(sp.x) - r, y: CGFloat(sp.y) - r, width: r * 2, height: r * 2)
            g.setFillColor(UIColor.white.cgColor); g.fillEllipse(in: rect)
            g.setStrokeColor(accent.cgColor); g.setLineWidth(3); g.strokeEllipse(in: rect)
            g.setFillColor(accent.cgColor); g.fillEllipse(in: rect.insetBy(dx: r - 4, dy: r - 4))
        }
    }

    private func drawFingerBubble(_ g: CGContext, _ f: P, _ b: BrushSettings) {
        let size: CGFloat = 34
        let c = CGPoint(x: CGFloat(f.x) + size * 0.45 + size / 2, y: CGFloat(f.y) - size * 1.5 + size / 2)
        let r = CGRect(x: c.x - size / 2, y: c.y - size / 2, width: size, height: size)
        g.saveGState()
        g.setShadow(offset: CGSize(width: 1, height: 2), blur: 4, color: UIColor.black.withAlphaComponent(0.25).cgColor)
        g.setFillColor(CanvasColors.background.cgColor); g.fillEllipse(in: r)
        g.restoreGState()
        let inner = r.insetBy(dx: 9, dy: 9)
        g.setFillColor(b.brushType == .ERASER ? CanvasColors.content.cgColor : ArgbColor.cgColor(ArgbColor.hex(b.colorHex)))
        g.fillEllipse(in: inner)
    }
}

extension UIColor {
    /// 0xAARRGGBB.
    var argb: UInt32 {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        getRed(&r, green: &g, blue: &b, alpha: &a)
        func c(_ x: CGFloat) -> UInt32 { UInt32(max(0, min(255, (x * 255).rounded()))) }
        return c(a) << 24 | c(r) << 16 | c(g) << 8 | c(b)
    }
}

/// SwiftUI sarmalayıcı: görünüm modelinin durumu değişince tuval yeniden çizilir.
struct EditorCanvas: UIViewRepresentable {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var viewport: ViewportState
    let actions: CanvasActions
    var overlay: CanvasOverlay

    func makeUIView(context: Context) -> EditorCanvasView {
        let v = EditorCanvasView(frame: .zero)
        v.vm = vm
        v.actions = actions
        v.overlay = overlay
        return v
    }

    func updateUIView(_ v: EditorCanvasView, context: Context) {
        v.vm = vm
        v.actions = actions
        v.overlay = overlay
        v.refresh()
    }
}
