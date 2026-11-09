import CoreGraphics
import Foundation

/// v43 damlalık (Android `ColorSampler`): noktanın 3×3 piksellik çevresi çizilir ve ortalaması alınır.
enum ColorSampler {
    static func sample(_ canvas: CanvasUi, _ x: Float, _ y: Float, page: Int? = nil) -> String? {
        let pg = page ?? canvas.currentPage
        if x < 0 || y < 0 || x > Float(canvas.width) || y > Float(canvas.height) { return nil }
        let strokes = Layers.visible(canvas.pages.indices.contains(pg) ? canvas.pages[pg] : canvas.strokes, canvas.layers)
        var px = [UInt8](repeating: 0, count: 3 * 3 * 4)
        let ok: Bool = px.withUnsafeMutableBytes { buf -> Bool in
            guard let ctx = CGContext(data: buf.baseAddress, width: 3, height: 3, bitsPerComponent: 8, bytesPerRow: 12,
                                      space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            // Tuval y aşağı: bağlamı çevir, noktayı ortadaki piksele getir
            ctx.translateBy(x: 0, y: 3)
            ctx.scaleBy(x: 1, y: -1)
            ctx.translateBy(x: CGFloat(1 - x), y: CGFloat(1 - y))
            StrokeRenderer.drawCanvasContent(ctx, paper: ArgbColor.parse(canvas.paperOf(pg)) ?? ArgbColor.white, strokes: strokes,
                                             width: CGFloat(canvas.width), height: CGFloat(canvas.height), decor: canvas.backgroundOf(pg))
            return true
        }
        if !ok { return nil }
        var r = 0, g = 0, b = 0
        for i in 0..<9 { r += Int(px[4 * i]); g += Int(px[4 * i + 1]); b += Int(px[4 * i + 2]) }
        return String(format: "#%02X%02X%02X", r / 9, g / 9, b / 9)
    }
}
