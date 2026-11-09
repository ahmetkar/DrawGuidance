import Foundation

/// Tuval hareketlerinin görünüm modeline bağlanması (Android `CanvasEditScreen` içindeki `CanvasActions(...)`).
@MainActor
final class CanvasUiEvents: ObservableObject {
    /// v41 boş yere dokununca arka plan seçildi (arka plan düzenleme araçları açılır).
    @Published var backgroundEdit = false
    /// v17 Ekle modunda dokunulan yer ("buraya ekle" menüsü).
    @Published var addTap: P?
    /// v45 iki parmakla uzun basma: seçimin işlemleri Düzenle listesinde açılır.
    @Published var contextEdit = false
}

extension EditorViewModel {
    static let CONTEXT_TOLERANCE_PX: Float = 24

    func draw3dFor() -> Depth3D? { store.canvas?.guide.draw3dSpec }

    func scene3dFor() -> SceneCamera? {
        guard let c = store.canvas, c.guide.draw3d else { return nil }
        return Scene3D.camera(c.guide, c.width, c.height)
    }

    func makeCanvasActions(_ ui: CanvasUiEvents) -> CanvasActions {
        let a = CanvasActions()
        a.onStrokeFinished = { [unowned self] p, id in drawing.onStrokeFinished(p, strokeId: id) }
        a.onPenEraserFinished = { [unowned self] p, id in drawing.onStrokeFinished(p, strokeId: id, penEraser: true) }
        a.onSelectAt = { [unowned self] p, t in selection.selectAt(p.x, p.y, tolerance: t) }
        a.onDeselectAt = { [unowned self] p, t in selection.deselectAt(p.x, p.y, tolerance: t) }
        a.onClearSelection = { [unowned self] in selection.clear() }
        a.onEmptyTap = { [unowned self] p in
            if !store.selectedIds.isEmpty { return false }
            if backgroundSelectable(p.x, p.y) { ui.backgroundEdit = true; return true }
            return false
        }
        a.onAddTap = { _, p in ui.addTap = p }
        a.onEditAt = { [unowned self] p, t in _ = editAt(p.x, p.y, tolerance: t) }
        a.onContextMenu = { [unowned self] _, p in
            let tol = Self.CONTEXT_TOLERANCE_PX / max(viewport.scale, 0.01)
            if selection.selectForContext(p.x, p.y, tolerance: tol) || !store.selectedIds.isEmpty {
                if mode != .SELECT { setMode(.SELECT) }
                ui.contextEdit = store.canvas?.kind != .KIDS
            }
        }
        a.onThreeFingerSwipe = { [unowned self] d in swipeUndo(d) }
        a.draw3d = { [unowned self] in draw3dFor() }
        a.scene3d = { [unowned self] in scene3dFor() }
        a.onMove = { [unowned self] dx, dy in selection.move(dx, dy) }
        a.onRotate = { [unowned self] d, pv in selection.rotate(d, pivot: pv) }
        a.onScale = { [unowned self] sx, sy, pv in selection.scale(sx, sy, pivot: pv) }
        a.onResize = { [unowned self] l, t, r, b in frame.resizeCanvas(l, t, r, b) }
        a.onLongPressSelect = { [unowned self] p, t in selection.selectOnlyAt(p.x, p.y, tolerance: t) }
        a.onHoldMove = { [unowned self] p, t in selection.holdMoveAt(p.x, p.y, tolerance: t) }
        a.ruler = { [unowned self] in ruler.active ? ruler : nil }
        a.onEdgeHold = { [unowned self] in holdPaperEdge() }
        a.onPickAt = { [unowned self] p, t in selection.pickAt(p.x, p.y, tolerance: t) }
        a.onReplaceStroke = { [unowned self] s in replaceStroke(s) }
        a.onSlotTap = { [unowned self] p in formulas.tapSlot(p) }
        a.buildShape = { [unowned self] id, s, e in shapes.shapeStrokes(id, s, e) }
        a.onShapeFinished = { [unowned self] id, s, e in shapes.addShape(id, s, e) }
        a.onShapeTap = { [unowned self] id, p in shapes.addShapeAt(id, p) }
        a.onTextTap = { [unowned self] p, t in texts.beginAt(p.x, p.y, tolerance: t) }
        a.onTextBox = { [unowned self] f, to, t in texts.beginBox(f, to, tolerance: t) }
        a.textDrafting = { [unowned self] in texts.draft != nil }
        a.onCropChange = { [unowned self] r in frame.updateCrop(r) }
        a.onDiagramFinished = { [unowned self] id, s, e in diagrams.add(id, s, e) }
        a.onDiagramTap = { [unowned self] id, p in diagrams.addAt(id, p) }
        a.onShapeEditTap = { [unowned self] p, t in shapes.applyTool(p, tolerance: t) }
        a.onPaintTap = { [unowned self] p in paintAt(p) }
        a.onPaintHover = { [unowned self] p, h in kidsPaint.hover(p, hold: h) }
        a.onPaintWhole = { [unowned self] p in paintWhole(p) }
        a.paintBrush = { [unowned self] in paintBrush }
        a.onBrushDown = { [unowned self] p in brushDown(p) }
        a.onBrushMove = { [unowned self] p in kidsBrush.move(p) }
        a.onBrushUp = { [unowned self] p in brushUp(p) }
        a.onBrushCancel = { [unowned self] in kidsBrush.cancel() }
        a.onShapeDragBegin = { [unowned self] p, t, h in shapes.beginDrag(p, tolerance: t, handleDistance: h) }
        a.onShapeDragPreview = { [unowned self] k, f, to, _, t in shapes.dragPreview(k, f, to, tolerance: t) }
        a.onShapeDragEnd = { [unowned self] k, f, to, _, t in shapes.endDrag(k, f, to, tolerance: t) }
        a.onVanishMove = { [unowned self] i, fx, fy, done in guides.moveVanishingPoint(i, fx, fy, persist: done) }
        a.onDiagramEditTap = { [unowned self] p, t in diagrams.tap(p, tolerance: t) }
        a.onFit = { [unowned self] in viewport.fit() }
        a.onDiagramHasHandle = { [unowned self] p, t in diagrams.hasHandleAt(p, tolerance: t) }
        a.onDiagramLinkDrag = { [unowned self] f, to, t in diagrams.dragLink(f, to, tolerance: t) }
        a.onFormulaFinished = { [unowned self] id, s, e in formulas.add(id, s, e) }
        a.onFormulaTap = { [unowned self] id, p in formulas.addAt(id, p) }
        a.onPenTap = { [unowned self] p, t in pen.tap(p, tolerance: t) }
        a.onPenDrag = { [unowned self] s, c, done in pen.drag(s, c, done: done) }
        a.onPenGrab = { [unowned self] p, t in pen.grab(p, tolerance: t) }
        a.onPenEditDrag = { [unowned self] p, done in pen.editDrag(p, done: done) }
        a.canvasPick = { [unowned self] in canvasPick }
        a.onEyedrop = { [unowned self] p in _ = eyedrop(p) }
        a.onRectSelect = { [unowned self] s, e in rectSelect(s, e) }
        a.onLassoSelect = { [unowned self] pts in lassoSelect(pts) }
        a.eraserSize = { [unowned self] in brush.eraserSize() }
        return a
    }
}
