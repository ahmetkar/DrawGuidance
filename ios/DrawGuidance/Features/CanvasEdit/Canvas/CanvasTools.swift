import Foundation

/// Hareket sürerken gösterilen, henüz kaydedilmemiş dönüşüm ya da yardımcı çizim (tuval koordinatı) (Android `Preview`).
enum CanvasPreview {
    case move(P)
    case rotate(Float, P)
    case scale(Float, Float, P)
    case expand(FRect)
    case shape(String, P, P)
    case box(P, P)
    case guideline(P, P)
    case shapeEdit(Stroke)
    case lasso([P])

    var editedStroke: Stroke? { if case let .shapeEdit(s) = self { return s }; return nil }
}

enum CropHandle { case LEFT, TOP, RIGHT, BOTTOM, MOVE }
enum ScaleHandle { case CENTER, CORNER, HORIZONTAL, VERTICAL }

let DOUBLE_TAP_MS: Int64 = 320
let LONG_PRESS_MS: Int64 = 2000

/// Çift dokunmayı tanımak için son dokunuş.
final class TapMemory {
    var time: Int64 = 0
    var position = P(0, 0)
    func isDouble(_ upTime: Int64, _ at: P, _ maxDistance: Float) -> Bool {
        let double = upTime - time <= DOUBLE_TAP_MS && hypot(at.x - position.x, at.y - position.y) <= maxDistance
        if double { time = 0 } else { time = upTime; position = at }
        return double
    }
}

/// Çizim alanındaki hareketlerin görünüm modeline iletildiği geri çağrılar (Android `CanvasActions`).
@MainActor
final class CanvasActions {
    var onStrokeFinished: ([StrokePoint], String) -> Void = { _, _ in }
    var onPenEraserFinished: ([StrokePoint], String) -> Void = { _, _ in }
    var onSelectAt: (P, Float) -> Bool = { _, _ in false }
    var onDeselectAt: (P, Float) -> Bool = { _, _ in false }
    var onMove: (Float, Float) -> Void = { _, _ in }
    var onRotate: (Float, P) -> Void = { _, _ in }
    var onScale: (Float, Float, P) -> Void = { _, _, _ in }
    var onResize: (Int, Int, Int, Int) -> Void = { _, _, _, _ in }
    var onLongPressSelect: (P, Float) -> Bool = { _, _ in false }
    var onEdgeHold: () -> Bool = { false }
    var onPickAt: (P, Float) -> Bool = { _, _ in false }
    var onSlotTap: (P) -> Bool = { _ in false }
    var buildShape: (String, P, P) -> [Stroke] = { _, _, _ in [] }
    var onShapeFinished: (String, P, P) -> Void = { _, _, _ in }
    var onShapeTap: (String, P) -> Void = { _, _ in }
    var onDiagramFinished: (String, P, P) -> Void = { _, _, _ in }
    var onDiagramTap: (String, P) -> Void = { _, _ in }
    var onShapeEditTap: (P, Float) -> Void = { _, _ in }
    var onPaintTap: (P) -> Void = { _ in }
    var onPaintHover: (P?, Int) -> Void = { _, _ in }
    var onPaintWhole: (P) -> Bool = { _ in false }
    var paintBrush: () -> Bool = { false }
    var onBrushDown: (P) -> Bool = { _ in false }
    var onBrushMove: (P) -> Void = { _ in }
    var onBrushUp: (P?) -> Bool = { _ in false }
    var onBrushCancel: () -> Void = {}
    var onShapeDragBegin: (P, Float, Float) -> ShapeDrag = { _, _, _ in .NONE }
    var onShapeDragPreview: (ShapeDrag, P, P, [P], Float) -> Stroke? = { _, _, _, _, _ in nil }
    var onShapeDragEnd: (ShapeDrag, P, P, [P], Float) -> Void = { _, _, _, _, _ in }
    var onVanishMove: (Int, Float, Float, Bool) -> Void = { _, _, _, _ in }
    var onDiagramEditTap: (P, Float) -> Void = { _, _ in }
    var onTextTap: (P, Float) -> Void = { _, _ in }
    var onTextBox: (P, P, Float) -> Void = { _, _, _ in }
    var textDrafting: () -> Bool = { false }
    var onCropChange: (FRect) -> Void = { _ in }
    var onFit: () -> Void = {}
    var onClearSelection: () -> Void = {}
    var onEmptyTap: (P) -> Bool = { _ in false }
    var onAddTap: (P, P) -> Void = { _, _ in }
    var onEditAt: (P, Float) -> Void = { _, _ in }
    var onContextMenu: (P, P) -> Void = { _, _ in }
    var onThreeFingerSwipe: (Int) -> Void = { _ in }
    var onDiagramHasHandle: (P, Float) -> Bool = { _, _ in false }
    var onDiagramLinkDrag: (P, P, Float) -> Void = { _, _, _ in }
    var onFormulaFinished: (String, P, P) -> Void = { _, _, _ in }
    var onFormulaTap: (String, P) -> Void = { _, _ in }
    var onPenTap: (P, Float) -> Void = { _, _ in }
    var onPenDrag: (P, P, Bool) -> Void = { _, _, _ in }
    var onPenGrab: (P, Float) -> Bool = { _, _ in false }
    var onPenEditDrag: (P, Bool) -> Void = { _, _ in }
    var canvasPick: () -> CanvasPick = { .NONE }
    var onEyedrop: (P) -> Void = { _ in }
    var onRectSelect: (P, P) -> Void = { _, _ in }
    var onLassoSelect: ([P]) -> Void = { _ in }
    var eraserSize: () -> Float = { 24 }
    var draw3d: () -> Depth3D? = { nil }
    var scene3d: () -> SceneCamera? = { nil }
    var onReplaceStroke: (Stroke) -> Void = { _ in }
    var onHoldMove: (P, Float) -> Bool = { _, _ in false }
    var ruler: () -> RulerController? = { nil }
}

/// Bir dokunma hareketinin araçlara verdiği bağlam (Android `GestureContext`).
@MainActor
final class GestureContext {
    let viewport: ViewportState
    let base: CanvasUi
    let selected: Set<String>
    let actions: CanvasActions
    let brush: BrushSettings
    let crop: FRect?
    let shapeTool: ShapeTool
    let live: LiveStroke
    /// Parmağın indiği yer (ekran) ve tuvaldeki karşılığı.
    let start: P
    let startCanvas: P
    let handleTolerance: Float
    let edgeTolerance: Float
    let hitTolerancePx: Float
    let taps: TapMemory
    let setPreview: (CanvasPreview?) -> Void
    let setFinger: (P?) -> Void
    let refreshLive: () -> Void
    var pressure: Float = 1
    var tilt: Float = 0
    var orient: Float = 0
    var eventTime: Int64 = 0
    /// Süren önizleme (araçlar `end`'de okur).
    var preview: CanvasPreview?

    init(viewport: ViewportState, base: CanvasUi, selected: Set<String>, actions: CanvasActions, brush: BrushSettings, crop: FRect?,
         shapeTool: ShapeTool, live: LiveStroke, start: P, handleTolerance: Float, edgeTolerance: Float, hitTolerancePx: Float,
         taps: TapMemory, setPreview: @escaping (CanvasPreview?) -> Void, setFinger: @escaping (P?) -> Void, refreshLive: @escaping () -> Void) {
        self.viewport = viewport; self.base = base; self.selected = selected; self.actions = actions; self.brush = brush; self.crop = crop
        self.shapeTool = shapeTool; self.live = live; self.start = start; startCanvas = viewport.toCanvas(start)
        self.handleTolerance = handleTolerance; self.edgeTolerance = edgeTolerance; self.hitTolerancePx = hitTolerancePx; self.taps = taps
        self.setPreview = setPreview; self.setFinger = setFinger; self.refreshLive = refreshLive
    }

    var hitTolerance: Float { hitTolerancePx / viewport.scale }
    var guideCapture: Float { hitTolerancePx * 2 / max(viewport.scale, 0.01) }

    func show(_ p: CanvasPreview?) { preview = p; setPreview(p) }
}

private func newId() -> String { UUID().uuidString.lowercased() }

/// Çizim alanındaki bir modun dokunuş davranışı (Strategy; her hareket için yeni araç) (Android `CanvasTool`).
@MainActor
class CanvasTool {
    var longPressSelects: Bool { false }
    var needsTicks: Bool { false }
    func tick(_ ctx: GestureContext, _ time: Int64) {}
    /// Sürükleme başlarken; false dönerse sürükleme görünümü kaydırır.
    func begin(_ ctx: GestureContext) -> Bool { false }
    func move(_ ctx: GestureContext, _ position: P, _ point: P) {}
    func end(_ ctx: GestureContext) {}
    func cancel(_ ctx: GestureContext) { ctx.show(nil) }
    func tap(_ ctx: GestureContext, _ point: P, _ upTime: Int64) {}
    func down(_ ctx: GestureContext) {}
    func longPress(_ ctx: GestureContext) -> Bool { ctx.actions.onLongPressSelect(ctx.startCanvas, ctx.hitTolerance) }
    func up(_ ctx: GestureContext) {}
}

@MainActor
enum CanvasTools {
    static func create(_ mode: EditMode, _ pick: CanvasPick, _ paintBrush: Bool) -> CanvasTool {
        if paintBrush && mode == .PAINT && pick != .EYEDROPPER { return PaintBrushTool() }
        if pick == .EYEDROPPER && (mode == .DRAW || mode == .SELECT || mode == .PAINT) { return EyedropperTool() }
        if pick == .RECT_SELECT && mode == .SELECT { return RectSelectTool() }
        if pick == .LASSO_SELECT && mode == .SELECT { return LassoSelectTool() }
        return create(mode)
    }

    static func create(_ mode: EditMode) -> CanvasTool {
        switch mode {
        case .DRAW: return DrawTool()
        case .SELECT: return SelectTool()
        case .ADD: return AddTool()
        case .EDIT_PICK: return EditPickTool()
        case .MOVE: return MoveTool()
        case .ROTATE: return RotateTool()
        case .SCALE: return ScaleTool()
        case .EXPAND: return ExpandTool()
        case .CROP: return CropTool()
        case .SHAPE: return ShapePlaceTool()
        case .DIAGRAM: return BoxPlaceTool({ $0.onDiagramFinished }, { $0.onDiagramTap })
        case .TEXT: return TextTool()
        case .SHAPE_EDIT: return ShapeEditTool()
        case .DIAGRAM_EDIT: return DiagramEditTool()
        case .GUIDE, .IMAGE_EDIT: return PanTool()
        case .PERSPECTIVE: return PerspectiveTool()
        case .FORMULA: return BoxPlaceTool({ $0.onFormulaFinished }, { $0.onFormulaTap })
        case .PEN: return PenTool()
        case .PAINT: return PaintTool()
        }
    }
}

final class PanTool: CanvasTool {}

/// v33 Şekil boyama; v37 çocuk tuvalinde kova imleci ve 2 sn basılı tutunca bütün şekil.
final class PaintTool: CanvasTool {
    private static var holds = 0
    private var kids = false
    private var anchor = P(0, 0)
    private var holdStart: Int64 = 0
    private var hold = -1
    private var last = P(0, 0)
    private var done = false

    override var longPressSelects: Bool { true }
    override var needsTicks: Bool { kids }

    override func down(_ ctx: GestureContext) {
        kids = ctx.base.kind == .KIDS
        if kids { startHold(ctx, ctx.startCanvas) }
    }

    private func startHold(_ ctx: GestureContext, _ point: P) {
        anchor = point; last = point; holdStart = ctx.eventTime; done = false
        Self.holds += 1
        hold = Self.holds
        ctx.actions.onPaintHover(point, hold)
    }

    override func begin(_ ctx: GestureContext) -> Bool { kids }

    override func move(_ ctx: GestureContext, _ position: P, _ point: P) {
        if !kids { return }
        last = point
        if dist(point, anchor) > ctx.hitTolerance { startHold(ctx, point) }
        else if !done { ctx.actions.onPaintHover(point, hold); checkHold(ctx, ctx.eventTime) }
    }

    override func tick(_ ctx: GestureContext, _ time: Int64) { checkHold(ctx, time) }

    private func checkHold(_ ctx: GestureContext, _ time: Int64) {
        if !kids || done || time - holdStart < LONG_PRESS_MS { return }
        _ = whole(ctx, last)
    }

    private func whole(_ ctx: GestureContext, _ point: P) -> Bool {
        done = true
        _ = ctx.actions.onPaintWhole(point)
        ctx.actions.onPaintHover(point, -1)
        return true
    }

    override func longPress(_ ctx: GestureContext) -> Bool { kids && !done && whole(ctx, ctx.startCanvas) }
    override func end(_ ctx: GestureContext) { if kids && !done { ctx.actions.onPaintTap(last) } }
    override func tap(_ ctx: GestureContext, _ point: P, _ upTime: Int64) { if !done { ctx.actions.onPaintTap(point) } }
    override func cancel(_ ctx: GestureContext) { super.cancel(ctx); if kids { ctx.actions.onPaintHover(nil, -1) } }
    override func up(_ ctx: GestureContext) { if kids { ctx.actions.onPaintHover(nil, -1) } }
}

/// v45 çocuk boya fırçası.
final class PaintBrushTool: CanvasTool {
    private var started = false
    private var last = P(0, 0)
    override func begin(_ ctx: GestureContext) -> Bool {
        started = ctx.actions.onBrushDown(ctx.startCanvas)
        last = ctx.startCanvas
        return started
    }
    override func move(_ ctx: GestureContext, _ position: P, _ point: P) {
        if !started { return }
        last = point
        ctx.actions.onBrushMove(point)
    }
    override func end(_ ctx: GestureContext) {
        if !started { return }
        started = false
        _ = ctx.actions.onBrushUp(last)
    }
    override func cancel(_ ctx: GestureContext) {
        super.cancel(ctx)
        if started { ctx.actions.onBrushCancel() }
        started = false
    }
    override func tap(_ ctx: GestureContext, _ point: P, _ upTime: Int64) {
        if ctx.actions.onBrushDown(point) { _ = ctx.actions.onBrushUp(point) }
    }
}

/// Çiz: sürükleme yeni çizim, dokunma nokta (v18 kalem basıncı/eğimi; v20 süzgeç; v21 3B; v40 cetvele yapışma).
final class DrawTool: CanvasTool {
    let penEraser: Bool
    private var filter: InkFilter?
    private var lastRaw: InkFilter.Point?
    private var plane: Plane3D = .FREE
    private var anchor = P(0, 0)
    private var mapping: Scene3D.DrawMapping?
    private var guide: StrokeGuide?
    private var guideLock = -1

    init(penEraser: Bool = false) { self.penEraser = penEraser }

    override var longPressSelects: Bool { true }
    override var needsTicks: Bool { filter?.needsTicks == true }
    var filtering: Bool { filter != nil }

    private func add(_ ctx: GestureContext, _ x0: Float, _ y0: Float, _ pressure: Float, _ tilt: Float, _ orient: Float) {
        var x = x0, y = y0
        if let g = guide, guideLock >= 0 { let p = g.project(guideLock, x0, y0); x = p.x; y = p.y }
        let q = mapping?.map(x, y) ?? Iso3D.project(plane, anchor, x, y)
        ctx.live.add(q.x, q.y, pressure, tilt, orient)
    }

    private func emit(_ ctx: GestureContext, _ points: [InkFilter.Point]) {
        for p in points { add(ctx, p.x, p.y, p.pressure, p.tilt, p.orient) }
    }

    override func begin(_ ctx: GestureContext) -> Bool {
        let brush = ctx.brush
        let eraser = penEraser && brush.brushType != .ERASER
        let type: BrushType = eraser ? .ERASER : brush.brushType
        var depth = type == .ERASER ? nil : ctx.actions.draw3d()
        plane = depth?.plane ?? .FREE
        anchor = ctx.startCanvas
        if let d = depth, let cam = ctx.actions.scene3d() {
            let m = Scene3D.DrawMapping(Scene3D.effectivePlane(d.plane, cam), cam, anchor.x, anchor.y)
            mapping = m
            depth = Depth3D(plane: m.plane, depth: d.depth, ex: m.extrude.x, ey: m.extrude.y)
        } else { mapping = nil }
        guide = type == .ERASER || depth != nil ? nil : ctx.actions.ruler()?.guide()
        guideLock = guide?.lock(ctx.startCanvas.x, ctx.startCanvas.y, captureDistance: ctx.guideCapture) ?? -1
        var params: BrushParams? = nil
        if type != .ERASER {
            if let depth { var p = brush.params ?? BrushParams(); p.depth3d = depth; params = p } else { params = brush.params }
        }
        ctx.live.start(newId(), type, brush.colorHex, eraser ? ctx.actions.eraserSize() : brush.sizePx,
                       eraser ? 1 : brush.opacity, eraser ? 1 : brush.flow, eraser ? 1 : brush.strength, params: params)
        if type == .ERASER { filter = nil } else {
            let f = InkFilter(stabilize: brush.tuning.stabilizeAmount, speed: brush.tuning.speed, unit: 1 / ctx.viewport.scale)
            filter = f.active ? f : nil
        }
        let first = InkFilter.Point(x: ctx.startCanvas.x, y: ctx.startCanvas.y, pressure: ctx.pressure, tilt: ctx.tilt, orient: ctx.orient)
        lastRaw = first
        if let f = filter { emit(ctx, f.start(first, ctx.eventTime)) } else { add(ctx, first.x, first.y, first.pressure, first.tilt, first.orient) }
        ctx.setFinger(ctx.start)
        ctx.refreshLive()
        return true
    }

    override func move(_ ctx: GestureContext, _ position: P, _ point: P) {
        let p = InkFilter.Point(x: point.x, y: point.y, pressure: ctx.pressure, tilt: ctx.tilt, orient: ctx.orient)
        lastRaw = p
        if let f = filter { emit(ctx, f.move(p, ctx.eventTime)) } else { add(ctx, p.x, p.y, p.pressure, p.tilt, p.orient) }
        ctx.setFinger(position)
        ctx.refreshLive()
    }

    override func tick(_ ctx: GestureContext, _ time: Int64) {
        guard let f = filter else { return }
        let out = f.tick(time)
        if out.isEmpty { return }
        emit(ctx, out)
        ctx.refreshLive()
    }

    override func end(_ ctx: GestureContext) {
        let id = ctx.live.strokeId
        if let f = filter { emit(ctx, f.finish(lastRaw, ctx.eventTime)) }
        ctx.live.finish()
        if let id {
            if penEraser { ctx.actions.onPenEraserFinished(ctx.live.points, id) } else { ctx.actions.onStrokeFinished(ctx.live.points, id) }
        }
        ctx.setFinger(nil)
        ctx.refreshLive()
    }

    override func cancel(_ ctx: GestureContext) {
        ctx.live.release()
        ctx.setFinger(nil)
        ctx.show(nil)
        ctx.refreshLive()
    }

    override func tap(_ ctx: GestureContext, _ point: P, _ upTime: Int64) {
        var p = point
        if ctx.brush.brushType != .ERASER, let g = ctx.actions.ruler()?.guide() {
            let lock = g.lock(point.x, point.y, captureDistance: ctx.guideCapture)
            if lock >= 0 { p = g.project(lock, point.x, point.y) }
        }
        ctx.actions.onStrokeFinished([StrokePoint(p.x, p.y)], newId())
    }
}

/// v27 tek balon seçiliyken parmak bir kuyruk ucuna indi mi.
@MainActor
func balloonTailAt(_ ctx: GestureContext) -> (Stroke, Int)? {
    guard ctx.selected.count == 1, let s = ctx.base.strokes.first(where: { ctx.selected.contains($0.id) }), s.text?.box?.balloon != nil else { return nil }
    let pos = BalloonTails.positions(s)
    guard let i = pos.indices.min(by: { dist(ctx.viewport.toScreen(pos[$0]), ctx.start) < dist(ctx.viewport.toScreen(pos[$1]), ctx.start) }),
          dist(ctx.viewport.toScreen(pos[i]), ctx.start) <= ctx.handleTolerance * 1.3 else { return nil }
    return (s, i)
}

struct BoxResizeGesture { let stroke: Stroke; let handle: Int }

@MainActor
func textBoxHandleAt(_ ctx: GestureContext) -> BoxResizeGesture? {
    guard ctx.selected.count == 1, let s = ctx.base.strokes.first(where: { ctx.selected.contains($0.id) }), s.brushType == .TEXT else { return nil }
    guard let h = TextBoxResize.handles(s).min(by: { dist(ctx.viewport.toScreen($0.0), ctx.start) < dist(ctx.viewport.toScreen($1.0), ctx.start) }),
          dist(ctx.viewport.toScreen(h.0), ctx.start) <= ctx.handleTolerance else { return nil }
    return BoxResizeGesture(stroke: s, handle: h.1)
}

/// v40 basit türlerde yazı kutusu tutamakları (Android `SimpleTextResize`).
enum SimpleTextResize {
    static func handlesShown(_ mode: EditMode, _ kind: CanvasKind) -> Bool { mode == .SCALE || (kind.simple && (mode == .SELECT || mode == .MOVE)) }

    @MainActor
    static func preview(_ ctx: GestureContext, _ box: BoxResizeGesture, _ point: P) {
        if let s = TextBoxResize.resize(box.stroke, box.handle, point) { ctx.show(.shapeEdit(s)) }
    }

    static func inside(_ quad: [P], _ p: P) -> Bool {
        if quad.count < 4 { return false }
        var sign = 0
        for i in 0..<4 {
            let a = quad[i], b = quad[(i + 1) % 4]
            let cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
            let s = cross > 0 ? 1 : cross < 0 ? -1 : 0
            if s == 0 { continue }
            if sign == 0 { sign = s } else if s != sign { return false }
        }
        return true
    }
}

@MainActor
func simpleTextBoxHandleAt(_ ctx: GestureContext) -> BoxResizeGesture? {
    guard ctx.base.kind.simple, let g = textBoxHandleAt(ctx) else { return nil }
    let quad = g.stroke.points.prefix(4).map { ctx.viewport.toScreen(P($0.x, $0.y)) }
    if SimpleTextResize.inside(Array(quad), ctx.start) {
        let near = TextBoxResize.handles(g.stroke).contains { dist(ctx.viewport.toScreen($0.0), ctx.start) <= ctx.hitTolerancePx * 0.6 }
        if !near { return nil }
    }
    return g
}

struct CornerResizeGesture { let stroke: Stroke; let pivot: P; let corner: P }

/// v43 basit türlerde şekil/resim köşe tutamakları (Android `SimpleElementResize`).
enum SimpleElementResize {
    static let MIN_FACTOR: Float = 0.08
    static func handlesShown(_ mode: EditMode, _ kind: CanvasKind) -> Bool { kind.simple && (mode == .SELECT || mode == .MOVE) }
    static func eligible(_ s: Stroke) -> Bool { s.brushType == .IMAGE || (s.shape != nil && s.brushType != .TEXT) }
    static func bounds(_ s: Stroke) -> FRect? { ElementOps.selectionBounds([s], [s.id]) }
    static func corners(_ b: FRect) -> [(P, P)] {
        [(b.topLeft, b.bottomRight), (P(b.right, b.top), P(b.left, b.bottom)), (b.bottomRight, b.topLeft), (P(b.left, b.bottom), P(b.right, b.top))]
    }
    static func factor(_ g: CornerResizeGesture, _ point: P) -> Float {
        let d0 = dist(g.corner, g.pivot)
        if d0 < 1e-3 { return 1 }
        let dx = (g.corner.x - g.pivot.x) / d0, dy = (g.corner.y - g.pivot.y) / d0
        let along = (point.x - g.pivot.x) * dx + (point.y - g.pivot.y) * dy
        return (along / d0).coerceIn(MIN_FACTOR, ElementOps.MAX_SCALE)
    }
    static func resized(_ g: CornerResizeGesture, _ point: P) -> Stroke {
        let f = factor(g, point)
        return ElementOps.scale([g.stroke], [g.stroke.id], f, f, pivot: g.pivot)[0]
    }
    @MainActor static func preview(_ ctx: GestureContext, _ g: CornerResizeGesture, _ point: P) { ctx.show(.shapeEdit(resized(g, point))) }
}

@MainActor
func simpleCornerAt(_ ctx: GestureContext) -> CornerResizeGesture? {
    guard ctx.base.kind.simple, ctx.selected.count == 1, let s = ctx.base.strokes.first(where: { ctx.selected.contains($0.id) }),
          SimpleElementResize.eligible(s), let b = SimpleElementResize.bounds(s) else { return nil }
    guard let c = SimpleElementResize.corners(b).min(by: { dist(ctx.viewport.toScreen($0.0), ctx.start) < dist(ctx.viewport.toScreen($1.0), ctx.start) }),
          dist(ctx.viewport.toScreen(c.0), ctx.start) <= ctx.handleTolerance else { return nil }
    return CornerResizeGesture(stroke: s, pivot: c.1, corner: c.0)
}

/// v17 Seç: dokunma seçer, boş yere dokunma seçimi kaldırır; çift dokunma seçimden çıkarır ya da kağıdı sığdırır.
final class SelectTool: CanvasTool {
    private var tail: (Stroke, Int)?
    private var box: BoxResizeGesture?
    private var corner: CornerResizeGesture?

    override var longPressSelects: Bool { true }

    override func begin(_ ctx: GestureContext) -> Bool {
        tail = balloonTailAt(ctx)
        box = tail == nil ? simpleTextBoxHandleAt(ctx) : nil
        corner = tail == nil && box == nil ? simpleCornerAt(ctx) : nil
        return tail != nil || box != nil || corner != nil
    }

    override func move(_ ctx: GestureContext, _ position: P, _ point: P) {
        if let b = box { SimpleTextResize.preview(ctx, b, point); return }
        if let c = corner { SimpleElementResize.preview(ctx, c, point); return }
        guard let (s, i) = tail, let m = BalloonTails.moved(s, i, point) else { return }
        ctx.show(.shapeEdit(m))
    }

    override func end(_ ctx: GestureContext) {
        if tail == nil && box == nil && corner == nil { return }
        tail = nil; box = nil; corner = nil
        if let s = ctx.preview?.editedStroke { ctx.actions.onReplaceStroke(s) }
        ctx.show(nil)
    }

    override func tap(_ ctx: GestureContext, _ point: P, _ upTime: Int64) {
        let double = ctx.taps.isDouble(upTime, ctx.start, ctx.handleTolerance)
        if ctx.actions.onSlotTap(point) { return }
        if double {
            if !ctx.actions.onDeselectAt(point, ctx.hitTolerance) { ctx.actions.onFit() }
        } else if !ctx.actions.onSelectAt(point, ctx.hitTolerance) {
            if !ctx.actions.onEmptyTap(point) { ctx.actions.onClearSelection() }
        }
    }
}

final class AddTool: CanvasTool {
    override func tap(_ ctx: GestureContext, _ point: P, _ upTime: Int64) { ctx.actions.onAddTap(ctx.start, point) }
}

final class EditPickTool: CanvasTool {
    override func tap(_ ctx: GestureContext, _ point: P, _ upTime: Int64) { ctx.actions.onEditAt(point, ctx.hitTolerance) }
}

final class EyedropperTool: CanvasTool {
    private var last = P(0, 0)
    override func begin(_ ctx: GestureContext) -> Bool { last = ctx.startCanvas; ctx.setFinger(ctx.start); return true }
    override func move(_ ctx: GestureContext, _ position: P, _ point: P) { last = point; ctx.setFinger(position) }
    override func end(_ ctx: GestureContext) { ctx.setFinger(nil); ctx.actions.onEyedrop(last) }
    override func cancel(_ ctx: GestureContext) { ctx.setFinger(nil) }
    override func tap(_ ctx: GestureContext, _ point: P, _ upTime: Int64) { ctx.actions.onEyedrop(point) }
}

final class RectSelectTool: CanvasTool {
    private var last = P(0, 0)
    override func begin(_ ctx: GestureContext) -> Bool { last = ctx.startCanvas; return true }
    override func move(_ ctx: GestureContext, _ position: P, _ point: P) { last = point; ctx.show(.box(ctx.startCanvas, point)) }
    override func end(_ ctx: GestureContext) { ctx.show(nil); ctx.actions.onRectSelect(ctx.startCanvas, last) }
    override func tap(_ ctx: GestureContext, _ point: P, _ upTime: Int64) {
        if !ctx.actions.onSelectAt(point, ctx.hitTolerance) { ctx.actions.onClearSelection() }
    }
}

final class LassoSelectTool: CanvasTool {
    private var points: [P] = []
    override func begin(_ ctx: GestureContext) -> Bool { points = [ctx.startCanvas]; return true }
    override func move(_ ctx: GestureContext, _ position: P, _ point: P) {
        if let l = points.last, dist(l, point) * ctx.viewport.scale >= 3 { points.append(point) }
        ctx.show(.lasso(points))
    }
    override func end(_ ctx: GestureContext) { ctx.show(nil); ctx.actions.onLassoSelect(points); points = [] }
    override func cancel(_ ctx: GestureContext) { points = []; ctx.show(nil) }
    override func tap(_ ctx: GestureContext, _ point: P, _ upTime: Int64) {
        if !ctx.actions.onSelectAt(point, ctx.hitTolerance) { ctx.actions.onClearSelection() }
    }
}

/// Yazı: dokunulan yere yazılır; v37 sürüklenerek paragraf kutusu çizilir.
final class TextTool: CanvasTool {
    private var last = P(0, 0)
    override func begin(_ ctx: GestureContext) -> Bool {
        if ctx.actions.textDrafting() { return false }
        last = ctx.startCanvas
        return true
    }
    override func move(_ ctx: GestureContext, _ position: P, _ point: P) { last = point; ctx.show(.box(ctx.startCanvas, point)) }
    override func end(_ ctx: GestureContext) { ctx.show(nil); ctx.actions.onTextBox(ctx.startCanvas, last, ctx.hitTolerance) }
    override func tap(_ ctx: GestureContext, _ point: P, _ upTime: Int64) { ctx.actions.onTextTap(point, ctx.hitTolerance) }
}

class TransformTool: CanvasTool {
    override var longPressSelects: Bool { true }
    override func tap(_ ctx: GestureContext, _ point: P, _ upTime: Int64) {
        if ctx.selected.isEmpty { _ = ctx.actions.onPickAt(point, ctx.hitTolerance) } else { _ = ctx.actions.onSlotTap(point) }
    }
}

final class MoveTool: TransformTool {
    private var box: BoxResizeGesture?
    private var corner: CornerResizeGesture?

    override func begin(_ ctx: GestureContext) -> Bool {
        box = simpleTextBoxHandleAt(ctx)
        corner = box == nil ? simpleCornerAt(ctx) : nil
        if box != nil || corner != nil { return true }
        return ElementOps.selectionBounds(ctx.base.strokes, ctx.selected) != nil || ctx.actions.onPickAt(ctx.startCanvas, ctx.hitTolerance)
    }

    override func move(_ ctx: GestureContext, _ position: P, _ point: P) {
        if let b = box { SimpleTextResize.preview(ctx, b, point); return }
        if let c = corner { SimpleElementResize.preview(ctx, c, point); return }
        ctx.show(.move(P(point.x - ctx.startCanvas.x, point.y - ctx.startCanvas.y)))
    }

    override func end(_ ctx: GestureContext) {
        if box != nil || corner != nil {
            box = nil; corner = nil
            if let s = ctx.preview?.editedStroke { ctx.actions.onReplaceStroke(s) }
            ctx.show(nil)
            return
        }
        if case let .move(d)? = ctx.preview { ctx.actions.onMove(d.x, d.y) }
    }
}

final class RotateTool: TransformTool {
    private var pivot = P(0, 0)
    private var lastAngle: Float = 0
    private var total: Float = 0

    override func begin(_ ctx: GestureContext) -> Bool {
        guard let sel = ElementOps.selectionBounds(ctx.base.strokes, ctx.selected) else { return false }
        pivot = sel.center
        let p = ctx.viewport.toScreen(pivot)
        lastAngle = atan2(ctx.start.y - p.y, ctx.start.x - p.x)
        total = 0
        return true
    }

    override func move(_ ctx: GestureContext, _ position: P, _ point: P) {
        let ps = ctx.viewport.toScreen(pivot)
        let angle = atan2(position.y - ps.y, position.x - ps.x)
        var step = angle - lastAngle
        if step > .pi { step -= 2 * .pi }
        if step < -.pi { step += 2 * .pi }
        total += step
        lastAngle = angle
        ctx.show(.rotate(snapAngle(total * 180 / .pi), pivot))
    }

    override func end(_ ctx: GestureContext) {
        if case let .rotate(deg, pv)? = ctx.preview { ctx.actions.onRotate(deg, pv) }
    }
}

final class ScaleTool: TransformTool {
    private var pivot = P(0, 0)
    private var handle: ScaleHandle = .CENTER
    private var box: BoxResizeGesture?
    private var tail: (Stroke, Int)?

    override func begin(_ ctx: GestureContext) -> Bool {
        box = nil
        tail = balloonTailAt(ctx)
        if tail != nil { return true }
        if let b = textBoxHandleAt(ctx) { box = b; return true }
        guard let sel = ElementOps.selectionBounds(ctx.base.strokes, ctx.selected) else { return false }
        if isSingleText(ctx) {
            handle = .CENTER
            pivot = sel.center
            return dist(ctx.startCanvas, pivot) * ctx.viewport.scale >= 8
        }
        let b = ctx.viewport.toScreen(sel)
        let cy = (b.top + b.bottom) / 2, cx = (b.left + b.right) / 2
        let scy = (sel.top + sel.bottom) / 2, scx = (sel.left + sel.right) / 2
        let handles: [(P, ScaleHandle, P)] = [
            (P(b.left, b.top), .CORNER, P(sel.right, sel.bottom)), (P(b.right, b.top), .CORNER, P(sel.left, sel.bottom)),
            (P(b.left, b.bottom), .CORNER, P(sel.right, sel.top)), (P(b.right, b.bottom), .CORNER, P(sel.left, sel.top)),
            (P(b.left, cy), .HORIZONTAL, P(sel.right, scy)), (P(b.right, cy), .HORIZONTAL, P(sel.left, scy)),
            (P(cx, b.top), .VERTICAL, P(scx, sel.bottom)), (P(cx, b.bottom), .VERTICAL, P(scx, sel.top)),
        ]
        if let n = handles.min(by: { dist($0.0, ctx.start) < dist($1.0, ctx.start) }), dist(n.0, ctx.start) <= ctx.handleTolerance {
            handle = n.1; pivot = n.2
        } else { handle = .CENTER; pivot = sel.center }
        return dist(ctx.startCanvas, pivot) * ctx.viewport.scale >= 8
    }

    override func move(_ ctx: GestureContext, _ position: P, _ point: P) {
        if let (s, i) = tail { if let m = BalloonTails.moved(s, i, point) { ctx.show(.shapeEdit(m)) }; return }
        if let b = box { if let r = TextBoxResize.resize(b.stroke, b.handle, point) { ctx.show(.shapeEdit(r)) }; return }
        let (sx, sy) = scaleFactors(handle, pivot, ctx.startCanvas, point)
        ctx.show(.scale(sx, sy, pivot))
    }

    override func end(_ ctx: GestureContext) {
        if box != nil || tail != nil {
            box = nil; tail = nil
            if let s = ctx.preview?.editedStroke { ctx.actions.onReplaceStroke(s) }
            ctx.show(nil)
            return
        }
        if case let .scale(sx, sy, pv)? = ctx.preview { ctx.actions.onScale(sx, sy, pv) }
    }

    private func isSingleText(_ ctx: GestureContext) -> Bool {
        ctx.selected.count == 1 && ctx.base.strokes.contains { ctx.selected.contains($0.id) && $0.brushType == .TEXT }
    }
}

/// Ekrandaki noktanın kağıdın hangi kenarlarına yakın olduğu (sol, üst, sağ, alt).
@MainActor
func paperEdgesAt(_ viewport: ViewportState, _ width: Int, _ height: Int, _ point: P, _ tolerance: Float) -> [Bool] {
    let paper = viewport.toScreen(FRect(0, 0, Float(width), Float(height)))
    let inY = point.y >= paper.top - tolerance && point.y <= paper.bottom + tolerance
    let inX = point.x >= paper.left - tolerance && point.x <= paper.right + tolerance
    return [inY && abs(point.x - paper.left) <= tolerance, inX && abs(point.y - paper.top) <= tolerance,
            inY && abs(point.x - paper.right) <= tolerance, inX && abs(point.y - paper.bottom) <= tolerance]
}

final class ExpandTool: CanvasTool {
    private var edges = [false, false, false, false]

    override func begin(_ ctx: GestureContext) -> Bool {
        edges = paperEdgesAt(ctx.viewport, ctx.base.width, ctx.base.height, ctx.start, ctx.edgeTolerance)
        return edges.contains(true)
    }

    override func move(_ ctx: GestureContext, _ position: P, _ point: P) {
        let dx = point.x - ctx.startCanvas.x, dy = point.y - ctx.startCanvas.y
        let w = Float(ctx.base.width), h = Float(ctx.base.height)
        let minS = Float(FrameController.MIN_CANVAS), maxS = Float(FrameController.MAX_CANVAS)
        var left: Float = edges[0] ? dx : 0, top: Float = edges[1] ? dy : 0
        var right: Float = edges[2] ? w + dx : w, bottom: Float = edges[3] ? h + dy : h
        if edges[0] { left = left.coerceIn(right - maxS, right - minS) }
        if edges[2] { right = right.coerceIn(left + minS, left + maxS) }
        if edges[1] { top = top.coerceIn(bottom - maxS, bottom - minS) }
        if edges[3] { bottom = bottom.coerceIn(top + minS, top + maxS) }
        ctx.show(.expand(FRect(left.rounded(), top.rounded(), right.rounded(), bottom.rounded())))
    }

    override func end(_ ctx: GestureContext) {
        guard case let .expand(r)? = ctx.preview else { return }
        let left = Int(r.left.rounded()), top = Int(r.top.rounded())
        ctx.viewport.shiftForResize(Float(left), Float(top))
        ctx.actions.onResize(left, top, Int(r.right.rounded()), Int(r.bottom.rounded()))
        ctx.show(nil)
    }
}

final class CropTool: CanvasTool {
    private var handles = Set<CropHandle>()
    private var startRect = FRect.zero

    override func begin(_ ctx: GestureContext) -> Bool {
        startRect = ctx.crop ?? FRect(0, 0, Float(ctx.base.width), Float(ctx.base.height))
        let b = ctx.viewport.toScreen(startRect)
        let s = ctx.start, t = ctx.edgeTolerance
        let inY = s.y >= b.top - t && s.y <= b.bottom + t
        let inX = s.x >= b.left - t && s.x <= b.right + t
        if inY && abs(s.x - b.left) <= t { handles.insert(.LEFT) }
        if inX && abs(s.y - b.top) <= t { handles.insert(.TOP) }
        if inY && abs(s.x - b.right) <= t { handles.insert(.RIGHT) }
        if inX && abs(s.y - b.bottom) <= t { handles.insert(.BOTTOM) }
        if handles.isEmpty && s.x >= b.left && s.x <= b.right && s.y >= b.top && s.y <= b.bottom { handles.insert(.MOVE) }
        return !handles.isEmpty
    }

    override func move(_ ctx: GestureContext, _ position: P, _ point: P) {
        let dx = point.x - ctx.startCanvas.x, dy = point.y - ctx.startCanvas.y
        let w = Float(ctx.base.width), h = Float(ctx.base.height), r = startRect
        let next: FRect
        if handles.contains(.MOVE) {
            let mx = dx.coerceIn(-r.left, w - r.right), my = dy.coerceIn(-r.top, h - r.bottom)
            next = FRect(r.left + mx, r.top + my, r.right + mx, r.bottom + my)
        } else {
            next = FRect(handles.contains(.LEFT) ? r.left + dx : r.left, handles.contains(.TOP) ? r.top + dy : r.top,
                         handles.contains(.RIGHT) ? r.right + dx : r.right, handles.contains(.BOTTOM) ? r.bottom + dy : r.bottom)
        }
        ctx.actions.onCropChange(next)
    }
}

final class ShapePlaceTool: CanvasTool {
    private let gestureId = newId()
    private var last = P(0, 0)
    override func begin(_ ctx: GestureContext) -> Bool { last = ctx.startCanvas; return true }
    override func move(_ ctx: GestureContext, _ position: P, _ point: P) { last = point; ctx.show(.shape(gestureId, ctx.startCanvas, point)) }
    override func end(_ ctx: GestureContext) { ctx.show(nil); ctx.actions.onShapeFinished(gestureId, ctx.startCanvas, last) }
    override func tap(_ ctx: GestureContext, _ point: P, _ upTime: Int64) { ctx.actions.onShapeTap(newId(), point) }
}

/// Kutuya yerleştirme (formül, diyagram).
final class BoxPlaceTool: CanvasTool {
    private let gestureId = newId()
    private var last = P(0, 0)
    private let finished: (CanvasActions) -> (String, P, P) -> Void
    private let tapped: (CanvasActions) -> (String, P) -> Void
    init(_ finished: @escaping (CanvasActions) -> (String, P, P) -> Void, _ tapped: @escaping (CanvasActions) -> (String, P) -> Void) {
        self.finished = finished; self.tapped = tapped
    }
    override func begin(_ ctx: GestureContext) -> Bool { last = ctx.startCanvas; return true }
    override func move(_ ctx: GestureContext, _ position: P, _ point: P) { last = point; ctx.show(.box(ctx.startCanvas, point)) }
    override func end(_ ctx: GestureContext) { ctx.show(nil); finished(ctx.actions)(gestureId, ctx.startCanvas, last) }
    override func tap(_ ctx: GestureContext, _ point: P, _ upTime: Int64) { tapped(ctx.actions)(newId(), point) }
}

final class PenTool: CanvasTool {
    private var last = P(0, 0)
    private var editing = false
    override func begin(_ ctx: GestureContext) -> Bool {
        last = ctx.startCanvas
        editing = ctx.actions.onPenGrab(ctx.startCanvas, ctx.hitTolerance)
        return true
    }
    override func move(_ ctx: GestureContext, _ position: P, _ point: P) {
        last = point
        if editing { ctx.actions.onPenEditDrag(point, false) } else { ctx.actions.onPenDrag(ctx.startCanvas, point, false) }
    }
    override func end(_ ctx: GestureContext) {
        if editing { editing = false; ctx.actions.onPenEditDrag(last, true) } else { ctx.actions.onPenDrag(ctx.startCanvas, last, true) }
    }
    override func cancel(_ ctx: GestureContext) {
        ctx.show(nil)
        if editing { editing = false; ctx.actions.onPenEditDrag(ctx.startCanvas, true); return }
        ctx.actions.onPenDrag(ctx.startCanvas, ctx.startCanvas, false)
    }
    override func tap(_ ctx: GestureContext, _ point: P, _ upTime: Int64) { ctx.actions.onPenTap(point, ctx.hitTolerance) }
}

final class ShapeEditTool: CanvasTool {
    private var kind: ShapeDrag = .NONE
    private var last = P(0, 0)
    private var path: [P] = []

    override func begin(_ ctx: GestureContext) -> Bool {
        last = ctx.startCanvas
        path = [ctx.startCanvas]
        kind = ctx.actions.onShapeDragBegin(ctx.startCanvas, ctx.hitTolerance, ctx.handleTolerance * 1.3 / ctx.viewport.scale)
        return kind != .NONE
    }

    override func move(_ ctx: GestureContext, _ position: P, _ point: P) {
        last = point
        if let l = path.last, dist(l, point) > ctx.hitTolerance * 0.15 { path.append(point) }
        ctx.show(ctx.actions.onShapeDragPreview(kind, ctx.startCanvas, point, path, ctx.hitTolerance).map { .shapeEdit($0) })
    }

    override func end(_ ctx: GestureContext) {
        ctx.show(nil)
        if path.last != last { path.append(last) }
        ctx.actions.onShapeDragEnd(kind, ctx.startCanvas, last, path, ctx.hitTolerance)
    }

    override func tap(_ ctx: GestureContext, _ point: P, _ upTime: Int64) { ctx.actions.onShapeEditTap(point, ctx.hitTolerance) }
}

final class DiagramEditTool: CanvasTool {
    private var last = P(0, 0)
    override func begin(_ ctx: GestureContext) -> Bool { last = ctx.startCanvas; return ctx.actions.onDiagramHasHandle(ctx.startCanvas, ctx.hitTolerance) }
    override func move(_ ctx: GestureContext, _ position: P, _ point: P) { last = point; ctx.show(.guideline(ctx.startCanvas, point)) }
    override func end(_ ctx: GestureContext) { ctx.show(nil); ctx.actions.onDiagramLinkDrag(ctx.startCanvas, last, ctx.hitTolerance) }
    override func tap(_ ctx: GestureContext, _ point: P, _ upTime: Int64) { ctx.actions.onDiagramEditTap(point, ctx.hitTolerance) }
}

/// Perspektif: kaçış noktasının yakınından başlayan sürükleme onu taşır.
final class PerspectiveTool: CanvasTool {
    private var index = -1
    private var last = P(0, 0)
    /// Kaçış noktalarının ekran konumu (görünüm verir; GuideRenderer CoreGraphics'te).
    static var vanishingPoints: (GuideSpec, FRect) -> [P] = { _, _ in [] }

    override func begin(_ ctx: GestureContext) -> Bool {
        let paper = ctx.viewport.toScreen(FRect(0, 0, Float(ctx.base.width), Float(ctx.base.height)))
        let vps = Self.vanishingPoints(ctx.base.guide, paper)
        if let n = vps.indices.min(by: { dist(vps[$0], ctx.start) < dist(vps[$1], ctx.start) }), dist(vps[n], ctx.start) <= ctx.handleTolerance * 1.6 {
            index = n
        } else { index = -1 }
        last = ctx.startCanvas
        return index >= 0
    }

    override func move(_ ctx: GestureContext, _ position: P, _ point: P) {
        last = point
        ctx.actions.onVanishMove(index, point.x / Float(ctx.base.width), point.y / Float(ctx.base.height), false)
    }

    override func end(_ ctx: GestureContext) {
        ctx.actions.onVanishMove(index, last.x / Float(ctx.base.width), last.y / Float(ctx.base.height), true)
    }
}

/// 15°'nin katlarına yakın açıları o kata yapıştırır.
func snapAngle(_ degrees: Float) -> Float {
    let snapped = (degrees / 15).rounded() * 15
    return abs(degrees - snapped) <= 2.5 ? snapped : degrees
}

func scaleFactors(_ handle: ScaleHandle, _ pivot: P, _ start: P, _ current: P) -> (Float, Float) {
    func ratio(_ now: Float, _ begin: Float) -> Float { abs(begin) < 1e-3 ? 1 : now / begin }
    switch handle {
    case .CENTER, .CORNER:
        let begin = dist(start, pivot)
        let f = begin < 1e-3 ? 1 : dist(current, pivot) / begin
        let c = f.coerceIn(ElementOps.MIN_SCALE, ElementOps.MAX_SCALE)
        return (c, c)
    case .HORIZONTAL: return (ratio(current.x - pivot.x, start.x - pivot.x).coerceIn(ElementOps.MIN_SCALE, ElementOps.MAX_SCALE), 1)
    case .VERTICAL: return (1, ratio(current.y - pivot.y, start.y - pivot.y).coerceIn(ElementOps.MIN_SCALE, ElementOps.MAX_SCALE))
    }
}
