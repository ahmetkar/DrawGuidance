import CoreGraphics
import Foundation

/// Çizim listesini CGContext'e (tuval birimi, y aşağı) çizer (Android `StrokeRenderer`).
enum StrokeRenderer {
    /// Tek çizim: kırpma dörtgeni varsa içinde.
    static func drawStroke(_ c: CGContext, _ stroke: Stroke) {
        // v56 aşama 6 yansıma: vektör noktaları dalgayla kayar (resimde ağ kayar), gradyan katmanla uygulanır
        if WarpRenderer.hasWave(stroke), let w = stroke.warp?.wave {
            let s = stroke.brushType == .IMAGE ? stroke : WarpRenderer.displaced(stroke)
            let ys = stroke.points.map(\.y)
            WarpRenderer.withFade(c, w, top: ys.min() ?? 0, bottom: ys.max() ?? 0) {
                clipped(c, s) { grained(c, s) { drawUnclipped(c, s) } }
            }
            return
        }
        clipped(c, stroke) { grained(c, stroke) { drawUnclipped(c, stroke) } }
    }

    /// v56 gren: iz saydamlık katmanında çizilip dokuyla çarpılır.
    private static func grained(_ c: CGContext, _ stroke: Stroke, _ body: () -> Void) {
        if let g = GrainRenderer.grainOf(stroke), !stroke.brushType.isObject {
            GrainRenderer.withGrain(c, g, firstX: stroke.points[0].x, firstY: stroke.points[0].y, body)
        } else {
            body()
        }
    }

    static func polygon(_ pts: [StrokePoint]) -> CGMutablePath {
        let path = CGMutablePath()
        path.move(to: CGPoint(x: CGFloat(pts[0].x), y: CGFloat(pts[0].y)))
        for i in 1..<pts.count { path.addLine(to: CGPoint(x: CGFloat(pts[i].x), y: CGFloat(pts[i].y))) }
        path.closeSubpath()
        return path
    }

    private static let FAR = CGRect(x: -1e6, y: -1e6, width: 2e6, height: 2e6)

    /// v56 seçim maskesi (Android `drawMasked`): sert kenarda kırpma yolu (ters seçimde çift-tek kuralıyla dışı);
    /// yumuşatılmışta çizim saydamlık katmanında, kademeli genişletilmiş alanlardan oluşan yumuşak maske `destinationIn`
    /// (tersinde `destinationOut`) ile uygulanır.
    private static func masked(_ c: CGContext, _ mask: ClipMask, raster: Bool, _ body: () -> Void) {
        let path = polygon(mask.points)
        if mask.feather <= 0.5 || raster {
            c.saveGState()
            if mask.inverted { path.addRect(FAR); c.addPath(path); c.clip(using: .evenOdd) } else { c.addPath(path); c.clip() }
            body()
            c.restoreGState()
            return
        }
        c.saveGState()
        c.beginTransparencyLayer(auxiliaryInfo: nil)
        body()
        c.saveGState()
        c.setBlendMode(mask.inverted ? .destinationOut : .destinationIn)
        c.beginTransparencyLayer(auxiliaryInfo: nil)
        let steps = 8
        c.setFillColor(CGColor(gray: 0, alpha: 0.3))
        for i in 0..<steps {
            let d = mask.feather * (Float(i) / Float(steps - 1) - 0.5)
            guard let ring = abs(d) < 0.01 ? mask.points : V56Selection.offset(mask.points, d) else { continue }
            c.addPath(polygon(ring)); c.fillPath()
        }
        c.endTransparencyLayer()
        c.restoreGState()
        c.endTransparencyLayer()
        c.restoreGState()
    }

    private static func clipped(_ c: CGContext, _ stroke: Stroke, _ body: () -> Void) {
        if let mask = stroke.mask, mask.points.count >= 3 {
            var plain = stroke
            plain.mask = nil
            masked(c, mask, raster: stroke.brushType.isRaster) { clipped(c, plain, body) }
            return
        }
        if let clip = stroke.clip, clip.count == 4 {
            c.saveGState()
            let path = CGMutablePath()
            path.move(to: CGPoint(x: CGFloat(clip[0].x), y: CGFloat(clip[0].y)))
            for i in 1..<4 { path.addLine(to: CGPoint(x: CGFloat(clip[i].x), y: CGFloat(clip[i].y))) }
            path.closeSubpath()
            c.addPath(path); c.clip()
            body()
            c.restoreGState()
            return
        }
        body()
    }

    /// v51 yardımcı fırçanın canlı önizleme rengi ve opaklığı.
    static let HELPER_PREVIEW_COLOR: UInt32 = 0xFF7A_8699
    static let HELPER_PREVIEW_ALPHA: Float = 0.35

    /// Parmak/kalem hâlâ ekrandayken canlı çizim: piksel motorlu fırçalar her noktada yeniden hesaplanmaz, yumuşak
    /// damgalarla önizlenir (yardımcı fırçalar işlenecek alanı yarı saydam gri gösterir). Diğer fırçalar `drawStroke` gibi.
    static func drawLive(_ c: CGContext, _ stroke: Stroke) {
        guard stroke.brushType.isRaster, !stroke.points.isEmpty else { drawStroke(c, stroke); return }
        clipped(c, stroke) { grained(c, stroke) {
            let helper = stroke.brushType.isHelper
            let xs = stroke.points.map(\.x), ys = stroke.points.map(\.y)
            let d = BrushPainter.dynamicsOf(stroke.points)
            BrushPainter.paint(c, xs: xs, ys: ys, color: helper ? HELPER_PREVIEW_COLOR : ArgbColor.hex(stroke.colorHex),
                               type: stroke.brushType, sizePx: stroke.sizePx, opacity: helper ? HELPER_PREVIEW_ALPHA : stroke.opacity,
                               seed: stroke.id.javaHashCode, flow: stroke.flow, strength: stroke.strength,
                               pressures: d.pressures, tilts: d.tilts, params: stroke.params, orients: d.orients,
                               rolls: d.rolls, barrels: d.barrels)
        } }
    }

    private static func drawUnclipped(_ c: CGContext, _ stroke: Stroke) {
        switch stroke.brushType {
        case .SHAPE: ShapeRenderer.draw(c, stroke)
        case .TEXT: TextRenderer.draw(c, stroke, color: ArgbColor.hex(stroke.colorHex))
        case .FORMULA: FormulaRenderer.draw(c, stroke, color: ArgbColor.hex(stroke.colorHex))
        case .DIAGRAM: DiagramRenderer.draw(c, stroke)
        case .IMAGE: ImageRenderer.draw(c, stroke)
        case .LASSO_FILL:
            // v56 kement dolgu: kapalı yolun içi (renk ve opaklık)
            guard stroke.points.count >= 3 else { return }
            let col = ArgbColor.hex(stroke.colorHex)
            let a = Int((Float((col >> 24) & 0xFF) * min(max(stroke.opacity, 0), 1)).rounded())
            c.setFillColor(ArgbColor.cgColor(col, alpha: a))
            c.addPath(polygon(stroke.points)); c.fillPath()
        default:
            let n = stroke.points.count
            if n == 0 { return }
            // v51 piksel tabanlı fırça: bağlamsız çizim (önizleme, bekleyen çizim); listede bağlamıyla çizilir
            if stroke.brushType.isRaster { RasterStrokes.draw(c, stroke, context: nil); return }
            // v56 resimden fırça: uç resmi iz boyunca damgalanır (uç yüklenmediyse fırçanın kendi ucu)
            if stroke.params?.stampAssetId != nil, GrainRenderer.drawStamps(c, stroke, color: ArgbColor.hex(stroke.colorHex)) { return }
            if let r = paintBounds(stroke) {
                let clip = c.boundingBoxOfClipPath
                if !clip.isNull && !clip.isInfinite && !clip.intersects(r) { return }
            }
            let xs = stroke.points.map(\.x), ys = stroke.points.map(\.y)
            let d = BrushPainter.dynamicsOf(stroke.points)
            BrushPainter.paint(c, xs: xs, ys: ys, color: ArgbColor.hex(stroke.colorHex), type: stroke.brushType, sizePx: stroke.sizePx,
                               opacity: stroke.opacity, seed: stroke.id.javaHashCode, flow: stroke.flow, strength: stroke.strength,
                               pressures: d.pressures, tilts: d.tilts, params: stroke.params, orients: d.orients,
                               rolls: d.rolls, barrels: d.barrels)
        }
    }

    /// Silgiler yalnız kendi katmanlarındaki, kendilerinden önceki fırça izlerini siler; nesneler etkilenmez.
    /// v56 `layers`: opaklık, karışım modu ya da kırpma varsa katman katman birleştirilir (`LayerCompositor`).
    static func drawStrokeList(_ c: CGContext, _ strokes: [Stroke], layers: [Layer] = []) {
        if !strokes.isEmpty && LayerCompositor.needed(layers) {
            LayerCompositor.drawLayered(c, strokes, layers) { drawPlainList(c, $0) }
            return
        }
        drawPlainList(c, strokes)
    }

    private static func isLocked(_ s: Stroke) -> Bool { s.params?.alphaLocked == true }

    private static func drawPlainList(_ c: CGContext, _ strokes: [Stroke]) {
        if !strokes.contains(where: { $0.brushType == .ERASER || $0.brushType.isRaster || isLocked($0) }) {
            for s in strokes where !s.hidden { drawStroke(c, s) }
            return
        }
        let n = strokes.count
        var i = 0
        while i < n {
            let first = strokes[i]
            if first.brushType.isObject { if !first.hidden { drawStroke(c, first) }; i += 1; continue }
            let layer = first.layerId ?? ""
            var j = i
            while j < n && !strokes[j].brushType.isObject && (strokes[j].layerId ?? "") == layer { j += 1 }
            var k = j
            var laterErasers: [Stroke] = []
            while k < n && (strokes[k].layerId ?? "") == layer {
                if strokes[k].brushType == .ERASER { laterErasers.append(strokes[k]) }
                k += 1
            }
            if (i..<j).contains(where: { strokes[$0].brushType != .ERASER }) {
                c.beginTransparencyLayer(auxiliaryInfo: nil)
                for m in i..<j where !strokes[m].hidden { drawInRun(c, strokes, m, layer) }
                for e in laterErasers where !e.hidden { drawStroke(c, e) }
                c.endTransparencyLayer()
            }
            i = j
        }
    }

    /// v51 katman ara katmanındaki bir çizim: piksel tabanlı fırça izi, aynı katmanda kendisinden önce çizilenleri
    /// (nesneler dahil) okuyarak çizilir; diğerleri her zamanki gibi.
    private static func drawInRun(_ c: CGContext, _ strokes: [Stroke], _ m: Int, _ layer: String) {
        let s = strokes[m]
        if isLocked(s) && s.brushType != .ERASER {
            // v56 alfa kilidi: yalnız katmanda önceden boyanmış yerlerde
            c.saveGState()
            c.setBlendMode(.sourceAtop)
            c.beginTransparencyLayer(auxiliaryInfo: nil)
            if s.brushType.isRaster { drawRaster(c, strokes, m, layer) } else { drawStroke(c, s) }
            c.endTransparencyLayer()
            c.restoreGState()
            return
        }
        guard s.brushType.isRaster else { drawStroke(c, s); return }
        drawRaster(c, strokes, m, layer)
    }

    private static func drawRaster(_ c: CGContext, _ strokes: [Stroke], _ m: Int, _ layer: String) {
        let s = strokes[m]
        var context: [Stroke] = []
        context.reserveCapacity(m)
        for q in 0..<m {
            let o = strokes[q]
            if (o.layerId ?? "") == layer && !o.hidden { context.append(o) }
        }
        clipped(c, s) { grained(c, s) { RasterStrokes.draw(c, s, context: context) } }
    }

    /// Kağıt rengi + arka plan (gradyan, resim, desen) + çizimler (silgi kâğıdı silmesin diye çizimler ayrı katmanda).
    static func drawCanvasContent(_ c: CGContext, paper: UInt32, strokes: [Stroke], width: CGFloat, height: CGFloat,
                                  decor: BackgroundSpec? = nil, layers: [Layer] = []) {
        c.setFillColor(ArgbColor.cgColor(paper | 0xFF00_0000))
        c.fill(CGRect(x: 0, y: 0, width: width, height: height))
        if let decor, !decor.isEmpty {
            BackgroundRenderer.drawCanvasBackground(c, decor, 0, 0, Float(width), Float(height), scale: 1, paper: paper, vector: true)
        }
        c.beginTransparencyLayer(auxiliaryInfo: nil)
        // v52 piksel fırçaları bu çizimin kağıdını okur
        RasterStrokes.withPaper(paper | 0xFF00_0000) { drawStrokeList(c, strokes, layers: layers) }
        c.endTransparencyLayer()
    }

    /// Çizimin boya bırakabileceği en geniş alan (tuval birimi).
    static func paintBounds(_ stroke: Stroke) -> CGRect? {
        let pts = stroke.points
        if pts.isEmpty { return nil }
        var bb = BoundsBuilder()
        for p in pts { bb.add(p.x, p.y) }
        let pad: Float
        switch stroke.brushType {
        case .IMAGE, .TEXT, .DIAGRAM, .FORMULA:
            pad = 2 + (stroke.warp?.wave.map { abs($0.amplitude) } ?? 0)
            if let m = stroke.warp?.mesh { for p in m { bb.add(p.x, p.y) } }
        case .SHAPE:
            let label = stroke.shape?.subpaths.map { sub in sub.label.map { sub.labelSize * Float($0.count + 1) } ?? 0 }.max() ?? 0
            pad = (stroke.shape?.style.strokeWidth ?? stroke.sizePx) + 4 + label
        default:
            pad = stroke.sizePx * stroke.brushType.reachFactor(stroke.params) + 4 + (stroke.warp?.wave.map { abs($0.amplitude) } ?? 0) +
                Iso3D.extrudeReach(stroke.params?.depth3d, stroke.brushType.inkWidth(stroke.sizePx, strength: stroke.strength))
        }
        return bb.rect(pad)
    }

    /// Çizimleri [w]×[h] piksel görüntüye ([scale] piksel/tuval birimi) çizer (önbellek ve dışa aktarma için).
    static func render(_ strokes: [Stroke], canvasWidth: CGFloat, canvasHeight: CGFloat, scale: CGFloat, paper: UInt32? = nil,
                       decor: BackgroundSpec? = nil, layers: [Layer] = []) -> CGImage {
        let w = max(1, Int((canvasWidth * scale).rounded())), h = max(1, Int((canvasHeight * scale).rounded()))
        return Bitmaps.draw(w, h) { c in
            c.scaleBy(x: scale, y: scale)
            if let paper {
                drawCanvasContent(c, paper: paper, strokes: strokes, width: canvasWidth, height: canvasHeight, decor: decor, layers: layers)
            } else {
                drawStrokeList(c, strokes, layers: layers)
            }
        }
    }
}
