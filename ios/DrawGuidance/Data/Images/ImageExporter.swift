import CoreGraphics
import CoreText
import Foundation
import ImageIO
import UniformTypeIdentifiers
#if canImport(Photos)
import Photos
#endif

/// Tuvali resim, PDF, CMYK ve CBZ olarak kaydeder (Android `ImageExporter`).
/// Varsayılan yer: uygulamanın Belgeler › DrawGuidance klasörü (Dosyalar uygulamasında görünür).
final class ImageExporter {
    static let FOLDER = "DrawGuidance"
    static var MAX_SIDE: Int { Budget3D.exportMaxSide >= 3072 ? 4096 : Budget3D.exportMaxSide }

    var settings: () -> ExportSettings = { ExportSettings.load() }
    let root: URL

    init(root: URL? = nil) {
        self.root = root ?? FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent(Self.FOLDER, isDirectory: true)
    }

    // MARK: Boyut

    func outputSize(_ canvasWidth: Int, _ canvasHeight: Int, _ scale: Float) -> (Int, Int) {
        let f = factorFor(canvasWidth, canvasHeight, scale)
        return (max(Int((Float(canvasWidth) * f).rounded()), 1), max(Int((Float(canvasHeight) * f).rounded()), 1))
    }

    private func factorFor(_ canvasWidth: Int, _ canvasHeight: Int, _ scale: Float) -> Float {
        let longest = max(max(canvasWidth, canvasHeight), 1)
        let wanted = scale.coerceIn(0.1, 8)
        return Float(longest) * wanted > Float(Self.MAX_SIDE) ? Float(Self.MAX_SIDE) / Float(longest) : wanted
    }

    // MARK: Çizim

    /// Bir sayfanın içeriği tuval biriminde (y aşağı): kağıt, arka plan, kılavuz, sayfa yazıları, çizimler.
    static func drawPage(_ c: CGContext, _ canvas: CanvasUi, page: Int, paper: UInt32, decor: Bool, includeGuides: Bool) {
        let w = CGFloat(canvas.width), h = CGFloat(canvas.height)
        let raw = canvas.pages.indices.contains(page) ? canvas.pages[page] : canvas.strokes
        let strokes = Layers.visible(raw, canvas.layers)
        c.saveGState()
        c.clip(to: CGRect(x: 0, y: 0, width: w, height: h))
        c.setFillColor(ArgbColor.cgColor(paper | 0xFF00_0000))
        c.fill(CGRect(x: 0, y: 0, width: w, height: h))
        let spec = canvas.backgroundOf(page)
        if decor && !spec.isEmpty {
            BackgroundRenderer.drawCanvasBackground(c, spec, 0, 0, Float(w), Float(h), scale: 1, paper: paper, vector: true)
        }
        if includeGuides && !canvas.guide.isEmpty {
            GuideRenderer.draw(c, canvas.guide, paper: CGRect(x: 0, y: 0, width: w, height: h), scale: 1)
        }
        if !canvas.pageSettings.isEmpty {
            PageDecorRenderer.draw(c, canvas.pageSettings, page: page, pageCount: canvas.pageCount, width: Float(w), height: Float(h),
                                   paper: paper, cover: canvas.hasCover)
        }
        c.beginTransparencyLayer(auxiliaryInfo: nil)
        StrokeRenderer.drawStrokeList(c, strokes)
        c.endTransparencyLayer()
        c.restoreGState()
    }

    static func paperArgb(_ canvas: CanvasUi, _ page: Int) -> UInt32 { ArgbColor.parse(canvas.paperOf(page)) ?? 0xFFFF_FFFF }

    func render(_ canvas: CanvasUi, scale: Float = 1, includeGuides: Bool? = nil, page: Int? = nil, background: UInt32? = nil,
                decor: Bool = true) -> CGImage {
        let p = page ?? canvas.currentPage
        let f = factorFor(canvas.width, canvas.height, scale)
        let (w, h) = outputSize(canvas.width, canvas.height, scale)
        let guides = includeGuides ?? GuideRenderer.exports(canvas.guide)
        return Bitmaps.draw(w, h) { c in
            c.scaleBy(x: CGFloat(f), y: CGFloat(f))
            Self.drawPage(c, canvas, page: p, paper: background ?? Self.paperArgb(canvas, p), decor: decor, includeGuides: guides)
        }
    }

    // MARK: Resim

    func defaultOptions(_ canvas: CanvasUi) -> ExportOptions {
        let s = settings()
        return ExportOptions(fileName: Self.defaultFileName(canvas.name), format: s.defaultFormat, scale: s.defaultScale,
                             includeGuides: GuideRenderer.exports(canvas.guide))
    }

    func export(_ canvas: CanvasUi) throws -> ExportedImage {
        guard let first = try export(canvas, defaultOptions(canvas)).first else { throw ExportError.failed }
        return first
    }

    /// Seçilen ayarlarla kaydeder; tüm sayfalar seçildiyse her sayfa ayrı resim olur (tuvalin adını taşıyan klasöre).
    func export(_ canvas: CanvasUi, _ options: ExportOptions, folder: String? = nil) throws -> [ExportedImage] {
        let pages = options.allPages && canvas.pageCount > 1 ? Array(canvas.pages.indices) : [canvas.currentPage]
        let base = Self.safeName(options.fileName.isBlank ? Self.defaultFileName(canvas.name) : options.fileName)
        let subfolder = pages.count > 1 ? Self.safeName(folder ?? canvas.name) : nil
        let converter = options.cmyk.map { CmykConverter(options: $0) }
        return try pages.enumerated().map { i, page in
            let image = render(canvas, scale: options.scale, includeGuides: options.includeGuides, page: page)
            let name = (pages.count > 1 ? "\(base)_s\(i + 1)" : base) + "." + options.ext
            let bytes: Data
            if let converter {
                bytes = try Self.encodeCmyk(image, converter, options.cmykFormat, options.quality)
            } else {
                bytes = try Self.encode(image, options.format, options.quality)
            }
            let file = try write(bytes, name, subfolder: subfolder, target: pages.count > 1 ? .defaultLocation : options.target)
            if converter == nil && settings().saveToPhotos { Self.addToPhotos(file.url) }
            return ExportedImage(url: file.url, location: file.location, width: image.width, height: image.height, sizeBytes: file.sizeBytes)
        }
    }

    static func encode(_ image: CGImage, _ format: ImageFormat, _ quality: Int) throws -> Data {
        let out = NSMutableData()
        let type = (format == .PNG ? UTType.png : UTType.jpeg).identifier as CFString
        guard let dest = CGImageDestinationCreateWithData(out, type, 1, nil) else { throw ExportError.failed }
        var props: [CFString: Any] = [:]
        var img = image
        if format == .JPEG {
            props[kCGImageDestinationLossyCompressionQuality] = Double(quality.coerceIn(1, 100)) / 100
            // JPEG saydamlık taşımaz: beyazın üstüne
            img = Bitmaps.draw(image.width, image.height) { c in
                c.setFillColor(ArgbColor.cgColor(0xFFFF_FFFF))
                c.fill(CGRect(x: 0, y: 0, width: image.width, height: image.height))
                Bitmaps.drawUpright(c, image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
            }
        }
        CGImageDestinationAddImage(dest, img, props as CFDictionary)
        guard CGImageDestinationFinalize(dest) else { throw ExportError.failed }
        return out as Data
    }

    // MARK: CMYK

    /// Görüntünün ARGB pikselleri.
    static func argbPixels(_ image: CGImage) -> [UInt32] { HarmonyRenderer.argbPixels(image, image.width, image.height) }

    /// ARGB görüntüyü DeviceCMYK (8 bit, 4 kanal) görüntüye çevirir.
    static func cmykImage(_ image: CGImage, _ converter: CmykConverter) -> CGImage? {
        let w = image.width, h = image.height
        let px = argbPixels(image)
        var bytes = [UInt8](repeating: 0, count: w * h * 4)
        for i in 0..<(w * h) where i < px.count { converter.convert(px[i], &bytes, i * 4) }
        guard let provider = CGDataProvider(data: Data(bytes) as CFData) else { return nil }
        return CGImage(width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceCMYK(),
                       bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.none.rawValue), provider: provider, decode: nil,
                       shouldInterpolate: true, intent: .defaultIntent)
    }

    static func encodeCmyk(_ image: CGImage, _ converter: CmykConverter, _ format: CmykFormat, _ quality: Int) throws -> Data {
        guard let cmyk = cmykImage(image, converter) else { throw ExportError.failed }
        switch format {
        case .PDF:
            return try cmykPdf([(cmyk, CGRect(x: 0, y: 0, width: image.width, height: image.height), CGSize(width: image.width, height: image.height), nil)])
        case .JPEG, .TIFF:
            let out = NSMutableData()
            let type = (format == .JPEG ? UTType.jpeg : UTType.tiff).identifier as CFString
            guard let dest = CGImageDestinationCreateWithData(out, type, 1, nil) else { throw ExportError.failed }
            var props: [CFString: Any] = [kCGImagePropertyDPIWidth: 300, kCGImagePropertyDPIHeight: 300]
            if format == .JPEG { props[kCGImageDestinationLossyCompressionQuality] = Double(quality.coerceIn(1, 100)) / 100 }
            else { props[kCGImagePropertyTIFFDictionary] = [kCGImagePropertyTIFFCompression: 5] }
            CGImageDestinationAddImage(dest, cmyk, props as CFDictionary)
            guard CGImageDestinationFinalize(dest) else { throw ExportError.failed }
            return out as Data
        }
    }

    /// CMYK sayfalardan PDF: her sayfa (görüntü, sayfadaki yeri [y aşağı], sayfa boyu, sayfa numarası yazısı).
    static func cmykPdf(_ pages: [(CGImage, CGRect, CGSize, String?)]) throws -> Data {
        let out = NSMutableData()
        guard let consumer = CGDataConsumer(data: out as CFMutableData) else { throw ExportError.failed }
        var box = CGRect(x: 0, y: 0, width: 1, height: 1)
        guard let pdf = CGContext(consumer: consumer, mediaBox: &box, [kCGPDFContextCreator: "DrawGuidance"] as CFDictionary) else { throw ExportError.failed }
        for (img, rect, size, number) in pages {
            var media = CGRect(origin: .zero, size: size)
            let info = [kCGPDFContextMediaBox: Data(bytes: &media, count: MemoryLayout<CGRect>.size)] as CFDictionary
            pdf.beginPDFPage(info)
            pdf.setFillColorSpace(CGColorSpaceCreateDeviceCMYK())
            pdf.setFillColor(CGColor(colorSpace: CGColorSpaceCreateDeviceCMYK(), components: [0, 0, 0, 0, 1])!)
            pdf.fill(media)
            // y aşağı dikdörtgeni PDF'in y yukarı düzenine çevir
            pdf.draw(img, in: CGRect(x: rect.minX, y: size.height - rect.maxY, width: rect.width, height: rect.height))
            if let number { drawNumber(pdf, number, size, footer: max(size.height - rect.maxY, PdfLayout.FOOTER), paper: 0xFFFF_FFFF, flipped: false) }
            pdf.endPDFPage()
        }
        pdf.closePDF()
        return out as Data
    }

    func exportCmykPdf(_ canvas: CanvasUi, _ options: ExportOptions) throws -> ExportedFile {
        var pdf = PdfOptions(fileName: options.fileName, pages: options.allPages ? .ALL : .CURRENT, includeGuides: options.includeGuides)
        pdf.cmyk = options.cmyk ?? CmykOptions()
        pdf.target = options.target
        pdf.imageScale = max(options.scale, 1)
        return try exportPdf(canvas, pdf)
    }

    private func cmykPdfBytes(_ canvas: CanvasUi, _ options: PdfOptions) throws -> Data {
        let converter = CmykConverter(options: options.cmyk ?? CmykOptions())
        let layout = PdfLayout.of(canvas.width, canvas.height, options)
        let cw = CGFloat(canvas.width), ch = CGFloat(canvas.height)
        var pages: [(CGImage, CGRect, CGSize, String?)] = []
        for index in options.pageIndices(canvas.pageCount, canvas.currentPage) {
            var copy = canvas; copy.currentPage = index
            let paper: UInt32 = options.background ? Self.paperArgb(canvas, index) : 0xFFFF_FFFF
            let img = render(copy, scale: options.imageScale, includeGuides: options.includeGuides, page: index, background: paper, decor: options.background)
            guard let cmyk = Self.cmykImage(img, converter) else { throw ExportError.failed }
            let rect = CGRect(x: CGFloat(layout.left), y: CGFloat(layout.top), width: cw * CGFloat(layout.scale), height: ch * CGFloat(layout.scale))
            pages.append((cmyk, rect, CGSize(width: layout.pageWidth, height: layout.pageHeight),
                          options.pageNumbers ? Self.pdfNumberText(canvas, index) : nil))
        }
        return try Self.cmykPdf(pages)
    }

    // MARK: PDF

    func exportPdf(_ canvas: CanvasUi, _ options: PdfOptions? = nil) throws -> ExportedFile {
        let o = options ?? PdfOptions(fileName: Self.defaultFileName(canvas.name))
        let name = Self.safeName(o.fileName.isBlank ? Self.defaultFileName(canvas.name) : o.fileName) + ".pdf"
        let bytes = o.cmyk != nil ? try cmykPdfBytes(canvas, o) : try renderPdf(canvas, o)
        return try write(bytes, name, subfolder: nil, target: o.target)
    }

    /// PDF'in baytları: keskin (vektör) ya da resim kalitesinde.
    func renderPdf(_ canvas: CanvasUi, _ options: PdfOptions) throws -> Data {
        let layout = PdfLayout.of(canvas.width, canvas.height, options)
        let cw = CGFloat(canvas.width), ch = CGFloat(canvas.height)
        let out = NSMutableData()
        guard let consumer = CGDataConsumer(data: out as CFMutableData) else { throw ExportError.failed }
        var media = CGRect(x: 0, y: 0, width: layout.pageWidth, height: layout.pageHeight)
        let meta = [kCGPDFContextCreator: "DrawGuidance", kCGPDFContextTitle: canvas.name] as CFDictionary
        guard let pdf = CGContext(consumer: consumer, mediaBox: &media, meta) else { throw ExportError.failed }
        for index in options.pageIndices(canvas.pageCount, canvas.currentPage) {
            let paper: UInt32 = options.background ? Self.paperArgb(canvas, index) : 0xFFFF_FFFF
            let raw = canvas.pages.indices.contains(index) ? canvas.pages[index] : canvas.strokes
            pdf.beginPDFPage(nil)
            // y aşağı (Android sayfası gibi)
            pdf.translateBy(x: 0, y: media.height)
            pdf.scaleBy(x: 1, y: -1)
            pdf.setFillColor(ArgbColor.cgColor(paper | 0xFF00_0000))
            pdf.fill(media)
            let vector = options.quality == .SHARP && !raw.contains { $0.brushType == .ERASER }
            let rect = CGRect(x: CGFloat(layout.left), y: CGFloat(layout.top), width: cw * CGFloat(layout.scale), height: ch * CGFloat(layout.scale))
            if vector {
                pdf.saveGState()
                pdf.translateBy(x: rect.minX, y: rect.minY)
                pdf.scaleBy(x: CGFloat(layout.scale), y: CGFloat(layout.scale))
                Self.drawPage(pdf, canvas, page: index, paper: paper, decor: options.background, includeGuides: options.includeGuides)
                pdf.restoreGState()
            } else {
                var copy = canvas; copy.currentPage = index
                let img = render(copy, scale: options.imageScale, includeGuides: options.includeGuides, page: index, background: paper, decor: options.background)
                pdf.interpolationQuality = .high
                Bitmaps.drawUpright(pdf, img, in: rect)
            }
            if options.pageNumbers, let text = Self.pdfNumberText(canvas, index) {
                Self.drawNumber(pdf, text, media.size, footer: CGFloat(max(layout.footer, 1)), paper: paper, flipped: true)
            }
            pdf.endPDFPage()
        }
        pdf.closePDF()
        return out as Data
    }

    /// Sayfa numarası: alttaki şeridin ortasında, kağıda göre koyu ya da açık gri (9 pt).
    static func drawNumber(_ c: CGContext, _ text: String, _ size: CGSize, footer: CGFloat, paper: UInt32, flipped: Bool) {
        let dark = DiagramThemes.luminance(paper) < 0.45
        let font = CTFontCreateWithName("Helvetica" as CFString, 9, nil)
        let attrs: [NSAttributedString.Key: Any] = [
            NSAttributedString.Key(kCTFontAttributeName as String): font,
            NSAttributedString.Key(kCTForegroundColorAttributeName as String): ArgbColor.cgColor(dark ? 0xFFBD_BDBD : 0xFF61_6161),
        ]
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attrs))
        let width = CTLineGetTypographicBounds(line, nil, nil, nil)
        let ascent = CTFontGetAscent(font), descent = CTFontGetDescent(font)
        c.saveGState()
        // Metin y yukarı düzende çizilir
        let centerFromBottom = footer / 2
        if flipped {
            c.translateBy(x: 0, y: size.height)
            c.scaleBy(x: 1, y: -1)
        }
        c.textMatrix = .identity
        c.textPosition = CGPoint(x: (size.width - CGFloat(width)) / 2, y: centerFromBottom - (ascent - descent) / 2)
        CTLineDraw(line, c)
        c.restoreGState()
    }

    // MARK: CBZ

    func exportCbz(_ canvas: CanvasUi, _ options: CbzOptions) throws -> ExportedFile {
        let name = Self.safeName(options.fileName.isBlank ? Self.defaultFileName(canvas.name) : options.fileName) + ".cbz"
        return try write(cbzBytes(canvas, options), name, subfolder: nil, target: options.target)
    }

    func cbzBytes(_ canvas: CanvasUi, _ options: CbzOptions) throws -> Data {
        var writer = CbzWriter(title: canvas.name, language: Locale.current.language.languageCode?.identifier)
        for index in options.pageIndices(canvas.pageCount, canvas.currentPage) {
            var copy = canvas; copy.currentPage = index
            let img = render(copy, scale: options.scale, includeGuides: false, page: index)
            writer.addPage(try Self.encode(img, options.format, options.quality), img.width, img.height, options.format.ext)
        }
        return writer.finish()
    }

    // MARK: Yazma

    /// Baytları varsayılan klasöre ya da seçilen klasöre yazar; aynı ad varsa "(2)" gibi ek alır.
    func write(_ bytes: Data, _ name: String, subfolder: String?, target: SaveTarget) throws -> ExportedFile {
        switch target {
        case .defaultLocation:
            var dir = root
            if let subfolder { dir.appendPathComponent(subfolder, isDirectory: true) }
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let url = Self.uniqueFile(dir, name)
            try bytes.write(to: url, options: .atomic)
            let label = subfolder.map { "\(Self.FOLDER)/\($0)" } ?? Self.FOLDER
            return ExportedFile(url: url, location: label, sizeBytes: Int64(bytes.count))
        case let .folder(bookmark, label):
            var stale = false
            let dir = try URL(resolvingBookmarkData: bookmark, bookmarkDataIsStale: &stale)
            let ok = dir.startAccessingSecurityScopedResource()
            defer { if ok { dir.stopAccessingSecurityScopedResource() } }
            var target = dir
            if let subfolder { target.appendPathComponent(subfolder, isDirectory: true); try FileManager.default.createDirectory(at: target, withIntermediateDirectories: true) }
            let url = Self.uniqueFile(target, name)
            try bytes.write(to: url, options: .atomic)
            return ExportedFile(url: url, location: label, sizeBytes: Int64(bytes.count))
        }
    }

    static func uniqueFile(_ dir: URL, _ name: String) -> URL {
        let fm = FileManager.default
        var url = dir.appendingPathComponent(name)
        if !fm.fileExists(atPath: url.path) { return url }
        let ext = (name as NSString).pathExtension, stem = (name as NSString).deletingPathExtension
        var n = 2
        repeat {
            url = dir.appendingPathComponent(ext.isEmpty ? "\(stem) (\(n))" : "\(stem) (\(n)).\(ext)")
            n += 1
        } while fm.fileExists(atPath: url.path)
        return url
    }

    func delete(_ url: URL) { try? FileManager.default.removeItem(at: url) }

    func sizeOf(_ url: URL) -> Int64 {
        ((try? FileManager.default.attributesOfItem(atPath: url.path))?[.size] as? NSNumber)?.int64Value ?? 0
    }

    /// Resmi Fotoğraflar'a da ekler (yalnız ekleme izni; reddedilirse sessizce geçer).
    static func addToPhotos(_ url: URL) {
        #if canImport(Photos)
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            guard status == .authorized || status == .limited else { return }
            PHPhotoLibrary.shared().performChanges { PHAssetCreationRequest.creationRequestForAssetFromImage(atFileURL: url) }
        }
        #endif
    }

    // MARK: Adlar

    /// PDF kenar şeridindeki sayfa numarası ("3 / 12"); kapaklı çizgi romanda kapak numarasız.
    static func pdfNumberText(_ canvas: CanvasUi, _ index: Int) -> String? {
        let cover = canvas.hasCover
        guard let n = PageNumbering.number(page: index, cover: cover) else { return nil }
        return "\(n) / \(max(PageNumbering.count(pageCount: canvas.pageCount, cover: cover), 1))"
    }
}

enum ExportError: Error { case failed }

extension ImageExporter {
    /// Dosya sisteminde güvenli ad: harf, rakam, _ ve - dışındakiler _ olur (en çok 60 karakter).
    static func safeName(_ name: String) -> String {
        let t = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let src = t.isEmpty ? "Tuval" : t
        var out = ""
        var lastUnderscore = false
        for ch in src {
            let ok = ch.isLetter || ch.isNumber || ch == "_" || ch == "-"
            if ok { out.append(ch); lastUnderscore = false }
            else if !lastUnderscore { out.append("_"); lastUnderscore = true }
        }
        return String(out.prefix(60))
    }

    /// Varsayılan dosya adı: tuval adı + tarih/saat.
    static func defaultFileName(_ canvasName: String, date: Date = Date()) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyyMMdd_HHmmss"
        let t = canvasName.trimmingCharacters(in: .whitespacesAndNewlines)
        return "\(t.isEmpty ? "Tuval" : t)_\(f.string(from: date))"
    }
}
