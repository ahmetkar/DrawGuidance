import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

/// v56 aşama 9 (Android `V56DocExport`): açık sayfayı SVG, katmanlı PSD ya da DXF olarak üretir (1x tuval boyutu).
enum V56DocExport {
    struct Result { var file: ExportedFile; var skipped: Int }

    static func doc(_ canvas: CanvasUi) -> ExportDoc {
        ExportDoc(width: canvas.width, height: canvas.height, paperHex: canvas.paperOf(canvas.currentPage), layers: canvas.layers,
                  strokes: canvas.currentPage < canvas.pages.count ? canvas.pages[canvas.currentPage] : canvas.strokes, title: canvas.name)
    }

    /// Çizimleri saydam zeminde, katman etkisi olmadan çizer.
    static func renderStrokes(_ canvas: CanvasUi, _ strokes: [Stroke]) -> CGImage {
        let plain = strokes.map { s -> Stroke in var c = s; c.layerId = nil; return c }
        return StrokeRenderer.render(plain, canvasWidth: CGFloat(max(canvas.width, 1)), canvasHeight: CGFloat(max(canvas.height, 1)), scale: 1)
    }

    static func png(_ img: CGImage) -> Data? { try? ImageExporter.encode(img, .PNG, 100) }

    static func svg(_ canvas: CanvasUi) -> String {
        SvgWriter.write(doc(canvas)) { run in png(renderStrokes(canvas, run)) }
    }

    private static func pixels(_ img: CGImage, _ w: Int, _ h: Int) -> [UInt32] { HarmonyRenderer.argbPixels(img, w, h) }

    private static func paperArgb(_ hex: String) -> UInt32 {
        var h = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        if h.count == 8 { h = String(h.dropFirst(2)) }
        return 0xFF00_0000 | (UInt32(h, radix: 16) ?? 0xFFFFFF)
    }

    static func psd(_ exporter: ImageExporter, _ canvas: CanvasUi) -> Data {
        let d = doc(canvas)
        let layers = canvas.layers.isEmpty ? [Layer.Default] : canvas.layers
        var out = [PsdWriter.PsdLayer(name: "Arka plan", pixels: [UInt32](repeating: paperArgb(d.paperHex), count: d.width * d.height))]
        for l in layers {
            let own = d.strokes.filter { Layers.of($0, layers) == l.id && !$0.hidden }
            out.append(PsdWriter.PsdLayer(name: l.name, pixels: pixels(renderStrokes(canvas, own), d.width, d.height), visible: l.visible,
                                          opacity: l.opacity, blend: l.blend, clipping: l.clipToBelow, alphaLock: l.alphaLock))
        }
        let merged = exporter.render(canvas, scale: 1, includeGuides: false)
        return PsdWriter.write(d.width, d.height, out, pixels(merged, d.width, d.height))
    }

    static func bytes(_ exporter: ImageExporter, _ canvas: CanvasUi, _ format: DocFormat) -> (Data, Int) {
        switch format {
        case .SVG: return (Data(svg(canvas).utf8), 0)
        case .PSD: return (psd(exporter, canvas), 0)
        case .DXF: let r = DxfWriter.write(doc(canvas)); return (Data(r.text.utf8), r.skipped)
        }
    }
}

extension ImagesRepository {
    /// v56 aşama 9 SVG / PSD / DXF (Kaydedilenler'e eklenir).
    func exportDoc(_ canvas: CanvasUi, _ format: DocFormat, _ fileName: String) throws -> V56DocExport.Result {
        assets.ensureLoaded(canvas)
        let (data, skipped) = V56DocExport.bytes(exporter, canvas, format)
        let name = ImageExporter.safeName(fileName.isBlank ? ImageExporter.defaultFileName(canvas.name) : fileName) + "." + format.ext
        let file = try exporter.write(data, name, subfolder: nil, target: .defaultLocation)
        recordDoc(canvas, file, format.mime)
        return V56DocExport.Result(file: file, skipped: skipped)
    }
}
