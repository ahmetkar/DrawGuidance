import CoreGraphics
import CoreText
import Foundation

/// v40 sayfa yazıları (Android `PageDecorRenderer`): üst bilgi sayfanın üst şeridine, alt bilgi ve sayfa numarası alt
/// şeridine çizilir. Tuvalde ve tüm çıktılarda aynı yerleşim; yazı rengi kağıdın açıklığına göre koyu ya da açık gri.
enum PageDecorRenderer {
    /// Kağıda göre okunur yazı rengi.
    static func inkFor(_ paper: UInt32) -> UInt32 { DiagramContrast.luminance(paper) < 0.4 ? 0xFFD0_D0D0 : 0xFF50_5050 }

    static func textSize(_ line: PageSettings.Line, _ w: Float, _ h: Float) -> Float { line.size.ratio * min(w, h) }

    /// Taban çizgisi: üst şeritte yazı boyunun 1,9 katı aşağıda, altta 1,1 katı yukarıda.
    static func baseline(_ line: PageSettings.Line, _ w: Float, _ h: Float) -> Float {
        let size = textSize(line, w, h)
        return line.top ? size * 1.9 : h - size * 1.1
    }

    /// [page] sayfasının yazılarını tuval biriminde (y aşağı) çizer; [cover] iken kapak yazısız kalır.
    static func draw(_ c: CGContext, _ settings: PageSettings, page: Int, pageCount: Int, width: Float, height: Float, paper: UInt32,
                     left: Float = 0, top: Float = 0, scale: Float = 1, cover: Bool = false) {
        let lines = settings.lines(page: page, count: pageCount, cover: cover)
        if lines.isEmpty { return }
        let ink = ArgbColor.cgColor(inkFor(paper))
        let inset = width * 0.045
        let avail = CGFloat((width - 2 * inset) * scale)
        for line in lines {
            let font = CTFontCreateUIFontForLanguage(.system, CGFloat(textSize(line, width, height) * scale), nil)
                ?? CTFontCreateWithName("Helvetica" as CFString, CGFloat(textSize(line, width, height) * scale), nil)
            let attrs: [NSAttributedString.Key: Any] = [NSAttributedString.Key(kCTFontAttributeName as String): font,
                                                        NSAttributedString.Key(kCTForegroundColorAttributeName as String): ink]
            var ct = CTLineCreateWithAttributedString(NSAttributedString(string: line.text, attributes: attrs))
            if CGFloat(CTLineGetTypographicBounds(ct, nil, nil, nil)) > avail {
                let dots = CTLineCreateWithAttributedString(NSAttributedString(string: "…", attributes: attrs))
                ct = CTLineCreateTruncatedLine(ct, Double(avail), .end, dots) ?? ct
            }
            let tw = Float(CTLineGetTypographicBounds(ct, nil, nil, nil))
            let x: Float
            switch line.align {
            case .LEFT: x = left + inset * scale
            case .CENTER: x = left + width / 2 * scale - tw / 2
            case .RIGHT: x = left + (width - inset) * scale - tw
            }
            let y = top + baseline(line, width, height) * scale
            c.saveGState()
            c.translateBy(x: CGFloat(x), y: CGFloat(y))
            c.scaleBy(x: 1, y: -1)
            c.textPosition = .zero
            CTLineDraw(ct, c)
            c.restoreGState()
        }
    }
}
