import CoreGraphics
import Foundation

/// Çizim listesini CGContext'e (tuval birimi, y aşağı) çizer (Android `StrokeRenderer`).
enum StrokeRenderer {
    /// Tek çizim: kırpma dörtgeni varsa içinde.
    static func drawStroke(_ c: CGContext, _ stroke: Stroke) {
        if let clip = stroke.clip, clip.count == 4 {
            c.saveGState()
            let path = CGMutablePath()
            path.move(to: CGPoint(x: CGFloat(clip[0].x), y: CGFloat(clip[0].y)))
            for i in 1..<4 { path.addLine(to: CGPoint(x: CGFloat(clip[i].x), y: CGFloat(clip[i].y))) }
            path.closeSubpath()
            c.addPath(path); c.clip()
            drawUnclipped(c, stroke)
            c.restoreGState()
            return
        }
        drawUnclipped(c, stroke)
    }

    private static func drawUnclipped(_ c: CGContext, _ stroke: Stroke) {
        switch stroke.brushType {
        case .SHAPE: ShapeRenderer.draw(c, stroke)
        case .TEXT: TextRenderer.draw(c, stroke, color: ArgbColor.hex(stroke.colorHex))
        case .IMAGE, .DIAGRAM, .FORMULA: return // ayrı çiziciler (sonraki aşama)
        default:
            let n = stroke.points.count
            if n == 0 { return }
            if let r = paintBounds(stroke) {
                let clip = c.boundingBoxOfClipPath
                if !clip.isNull && !clip.isInfinite && !clip.intersects(r) { return }
            }
            let xs = stroke.points.map(\.x), ys = stroke.points.map(\.y)
            let d = BrushPainter.dynamicsOf(stroke.points)
            BrushPainter.paint(c, xs: xs, ys: ys, color: ArgbColor.hex(stroke.colorHex), type: stroke.brushType, sizePx: stroke.sizePx,
                               opacity: stroke.opacity, seed: stroke.id.javaHashCode, flow: stroke.flow, strength: stroke.strength,
                               pressures: d.pressures, tilts: d.tilts, params: stroke.params, orients: d.orients)
        }
    }

    /// Silgiler yalnız kendi katmanlarındaki, kendilerinden önceki fırça izlerini siler; nesneler etkilenmez.
    static func drawStrokeList(_ c: CGContext, _ strokes: [Stroke]) {
        if !strokes.contains(where: { $0.brushType == .ERASER }) {
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
                for m in i..<j where !strokes[m].hidden { drawStroke(c, strokes[m]) }
                for e in laterErasers where !e.hidden { drawStroke(c, e) }
                c.endTransparencyLayer()
            }
            i = j
        }
    }

    /// Kağıt rengi + çizimler (silgi kâğıdı silmesin diye çizimler ayrı katmanda).
    static func drawCanvasContent(_ c: CGContext, paper: UInt32, strokes: [Stroke], width: CGFloat, height: CGFloat) {
        c.setFillColor(ArgbColor.cgColor(paper | 0xFF00_0000))
        c.fill(CGRect(x: 0, y: 0, width: width, height: height))
        c.beginTransparencyLayer(auxiliaryInfo: nil)
        drawStrokeList(c, strokes)
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
        case .IMAGE, .TEXT, .DIAGRAM, .FORMULA: pad = 2
        case .SHAPE:
            let label = stroke.shape?.subpaths.map { sub in sub.label.map { sub.labelSize * Float($0.count + 1) } ?? 0 }.max() ?? 0
            pad = (stroke.shape?.style.strokeWidth ?? stroke.sizePx) + 4 + label
        default:
            pad = stroke.sizePx * stroke.brushType.reachFactor(stroke.params) + 4 +
                Iso3D.extrudeReach(stroke.params?.depth3d, stroke.brushType.inkWidth(stroke.sizePx, strength: stroke.strength))
        }
        return bb.rect(pad)
    }

    /// Çizimleri [w]×[h] piksel görüntüye ([scale] piksel/tuval birimi) çizer (önbellek ve dışa aktarma için).
    static func render(_ strokes: [Stroke], canvasWidth: CGFloat, canvasHeight: CGFloat, scale: CGFloat, paper: UInt32? = nil) -> CGImage {
        let w = max(1, Int((canvasWidth * scale).rounded())), h = max(1, Int((canvasHeight * scale).rounded()))
        return Bitmaps.draw(w, h) { c in
            c.scaleBy(x: scale, y: scale)
            if let paper {
                drawCanvasContent(c, paper: paper, strokes: strokes, width: canvasWidth, height: canvasHeight)
            } else {
                drawStrokeList(c, strokes)
            }
        }
    }
}
