import Foundation

/// Resim biçimi (Android `ImageFormat`).
enum ImageFormat: String, Codable, CaseIterable {
    case PNG, JPEG
    var ext: String { self == .PNG ? "png" : "jpg" }
    var mime: String { self == .PNG ? "image/png" : "image/jpeg" }
    var label: String { rawValue }
}

/// Resim kaydetme ayarları (Android `ExportSettings`; Ayarlar ekranından değişir).
struct ExportSettings: Codable, Equatable {
    var defaultScale: Float = 1
    var defaultFormat: ImageFormat = .PNG
    /// Fotoğraflar'a da kaydedilsin mi (Android'deki Galeri albümü karşılığı).
    var saveToPhotos = false

    static let SCALES: [Float] = [0.5, 1, 2, 4]
    static let FOLDER = "DrawGuidance"

    private static let key = "dg.exportSettings"
    static func load() -> ExportSettings {
        guard let d = UserDefaults.standard.data(forKey: key), let s = try? JSONDecoder().decode(ExportSettings.self, from: d) else { return ExportSettings() }
        return s
    }
    func save() { if let d = try? JSONEncoder().encode(self) { UserDefaults.standard.set(d, forKey: Self.key) } }
}

/// v40 kaydetme yeri: varsayılan (Dosyalar › DrawGuidance) ya da kullanıcının seçtiği klasör (güvenlik kapsamlı yer imi).
enum SaveTarget: Equatable {
    case defaultLocation
    case folder(bookmark: Data, label: String)
}

/// v40 baskıya hazır (CMYK) dosya türleri.
enum CmykFormat: String, CaseIterable {
    case JPEG, TIFF, PDF
    var ext: String { switch self { case .JPEG: return "jpg"; case .TIFF: return "tif"; case .PDF: return "pdf" } }
    var mime: String { switch self { case .JPEG: return "image/jpeg"; case .TIFF: return "image/tiff"; case .PDF: return "application/pdf" } }
    var label: String { rawValue }
    var isDocument: Bool { self != .JPEG }
}

/// v40 siyah üretimi (GCR).
enum BlackGeneration: CaseIterable {
    case LIGHT, MEDIUM, HEAVY
    var start: Float { switch self { case .LIGHT: return 0.5; case .MEDIUM: return 0.25; case .HEAVY: return 0 } }
}

/// v40 CMYK kayıt ayarları.
struct CmykOptions: Equatable {
    var blackGeneration: BlackGeneration = .MEDIUM
    /// Toplam mürekkep sınırı (yüzde; 0 sınırsız).
    var inkLimit = 300
    static let InkLimits = [0, 340, 300, 260]
}

/// RGB → CMYK (Android `CmykConverter` ile birebir; ICC profili olmadan).
struct CmykConverter {
    let options: CmykOptions

    func cmyk(_ r: Float, _ g: Float, _ b: Float) -> (Float, Float, Float, Float) {
        let c0 = 1 - r, m0 = 1 - g, y0 = 1 - b
        let k0 = min(c0, min(m0, y0))
        let s = options.blackGeneration.start
        let k = k0 <= s ? 0 : min((k0 - s) / (1 - s), k0)
        var c: Float = 0, m: Float = 0, y: Float = 0
        if k < 0.9999 {
            c = ((c0 - k) / (1 - k)).coerceIn(0, 1)
            m = ((m0 - k) / (1 - k)).coerceIn(0, 1)
            y = ((y0 - k) / (1 - k)).coerceIn(0, 1)
        }
        let limit = Float(options.inkLimit) / 100
        if (1...399).contains(options.inkLimit) {
            let cmy = c + m + y
            if cmy + k > limit && cmy > 0 {
                let f = ((limit - k) / cmy).coerceIn(0, 1)
                c *= f; m *= f; y *= f
            }
        }
        return (c, m, y, k)
    }

    /// ARGB piksel → 4 bayt CMYK (0 = mürekkep yok).
    func convert(_ argb: UInt32, _ out: inout [UInt8], _ offset: Int) {
        let a = Int(argb >> 24 & 0xFF)
        var r = Int(argb >> 16 & 0xFF), g = Int(argb >> 8 & 0xFF), b = Int(argb & 0xFF)
        if a < 255 {
            r = (r * a + 255 * (255 - a)) / 255
            g = (g * a + 255 * (255 - a)) / 255
            b = (b * a + 255 * (255 - a)) / 255
        }
        let v = cmyk(Float(r) / 255, Float(g) / 255, Float(b) / 255)
        func q(_ x: Float) -> UInt8 { UInt8(Int((x * 255).rounded()).coerceIn(0, 255)) }
        out[offset] = q(v.0); out[offset + 1] = q(v.1); out[offset + 2] = q(v.2); out[offset + 3] = q(v.3)
    }
}

/// Kaydetme penceresinde seçilen ayarlar (Android `ExportOptions`).
struct ExportOptions: Equatable {
    var fileName: String
    var format: ImageFormat = .PNG
    var scale: Float = 1
    var quality = 92
    var allPages = false
    var includeGuides = false
    var target: SaveTarget = .defaultLocation
    var cmyk: CmykOptions? = nil
    var cmykFormat: CmykFormat = .JPEG

    var ext: String { cmyk != nil ? cmykFormat.ext : format.ext }
    var mime: String { cmyk != nil ? cmykFormat.mime : format.mime }
    var isDocument: Bool { cmyk != nil && cmykFormat.isDocument }
}

enum PdfPages { case ALL, CURRENT, RANGE }

enum PdfPaper: CaseIterable {
    case CANVAS, A4, A5, LETTER
    var widthPt: Int { switch self { case .CANVAS: return 0; case .A4: return 595; case .A5: return 420; case .LETTER: return 612 } }
    var heightPt: Int { switch self { case .CANVAS: return 0; case .A4: return 842; case .A5: return 595; case .LETTER: return 792 } }
}

enum PdfOrientation: CaseIterable { case AUTO, PORTRAIT, LANDSCAPE }

enum PdfMargin: CaseIterable {
    case NONE, NARROW, NORMAL
    var pt: Int { switch self { case .NONE: return 0; case .NARROW: return 18; case .NORMAL: return 36 } }
}

enum PdfQuality { case SHARP, IMAGE }

/// v19 "PDF olarak kaydet" ayarları (Android `PdfOptions`).
struct PdfOptions: Equatable {
    var fileName: String
    var pages: PdfPages = .ALL
    var fromPage = 1
    var toPage = Int.max
    var paper: PdfPaper = .CANVAS
    var orientation: PdfOrientation = .AUTO
    var margin: PdfMargin = .NONE
    var quality: PdfQuality = .SHARP
    var imageScale: Float = 2
    var background = true
    var includeGuides = false
    var pageNumbers = false
    var target: SaveTarget = .defaultLocation
    var cmyk: CmykOptions? = nil

    static let ImageScales: [Float] = [1, 2, 3]

    func pageIndices(_ pageCount: Int, _ currentPage: Int) -> [Int] {
        let count = max(pageCount, 1)
        switch pages {
        case .ALL: return Array(0..<count)
        case .CURRENT: return [currentPage.coerceIn(0, count - 1)]
        case .RANGE:
            let a = min(fromPage, toPage).coerceIn(1, count)
            let b = max(fromPage, toPage).coerceIn(a, count)
            return Array((a - 1)..<b)
        }
    }
}

struct PdfPageLayout: Equatable {
    let pageWidth: Int, pageHeight: Int
    let left: Float, top: Float, scale: Float, footer: Float
}

enum PdfLayout {
    static let CANVAS_SHORT_SIDE: Float = 595
    static let FOOTER: Float = 22

    static func of(_ canvasWidth: Int, _ canvasHeight: Int, _ options: PdfOptions) -> PdfPageLayout {
        let cw = Float(max(canvasWidth, 1)), ch = Float(max(canvasHeight, 1))
        let margin = Float(options.margin.pt)
        let footer = options.pageNumbers ? FOOTER : 0
        let bottom = max(margin, footer)
        if options.paper == .CANVAS {
            let scale = CANVAS_SHORT_SIDE / min(cw, ch)
            let w = max(Int((cw * scale + 2 * margin).rounded()), 1)
            let h = max(Int((ch * scale + margin + bottom).rounded()), 1)
            return PdfPageLayout(pageWidth: w, pageHeight: h, left: margin, top: margin, scale: scale, footer: footer)
        }
        let landscape: Bool
        switch options.orientation {
        case .AUTO: landscape = cw > ch
        case .PORTRAIT: landscape = false
        case .LANDSCAPE: landscape = true
        }
        let pw = landscape ? options.paper.heightPt : options.paper.widthPt
        let ph = landscape ? options.paper.widthPt : options.paper.heightPt
        let areaW = max(Float(pw) - 2 * margin, 1), areaH = max(Float(ph) - margin - bottom, 1)
        let scale = min(areaW / cw, areaH / ch)
        return PdfPageLayout(pageWidth: pw, pageHeight: ph, left: margin + (areaW - cw * scale) / 2, top: margin + (areaH - ch * scale) / 2,
                             scale: scale, footer: footer)
    }
}

/// v40 çizgi roman arşivi ayarları.
struct CbzOptions: Equatable {
    var fileName: String
    var pages: PdfPages = .ALL
    var fromPage = 1
    var toPage = Int.max
    var format: ImageFormat = .JPEG
    var quality = 90
    var scale: Float = 1
    var target: SaveTarget = .defaultLocation

    func pageIndices(_ pageCount: Int, _ currentPage: Int) -> [Int] {
        PdfOptions(fileName: fileName, pages: pages, fromPage: fromPage, toPage: toPage).pageIndices(pageCount, currentPage)
    }
}

/// Kaydedilen dosya.
struct ExportedFile: Equatable { let url: URL; let location: String; let sizeBytes: Int64 }
struct ExportedImage: Equatable { let url: URL; let location: String; let width: Int; let height: Int; let sizeBytes: Int64 }

/// PDF dışa aktarma durumu (Android `PdfExportState`).
enum PdfExportState: Equatable {
    case idle
    case exporting
    case done(ExportedFile)
    case printDone(ExportedFile)
    case failed
}

/// Sıkıştırmasız (STORED) ZIP yazıcı: CBZ için (resimler zaten sıkıştırılmış).
struct ZipWriter {
    private(set) var data = Data()
    private var central = Data()
    private var count: UInt16 = 0

    private static let crcTable: [UInt32] = (0..<256).map { n -> UInt32 in
        var c = UInt32(n)
        for _ in 0..<8 { c = c & 1 != 0 ? 0xEDB88320 ^ (c >> 1) : c >> 1 }
        return c
    }

    static func crc32(_ d: Data) -> UInt32 {
        var c: UInt32 = 0xFFFFFFFF
        for b in d { c = crcTable[Int((c ^ UInt32(b)) & 0xFF)] ^ (c >> 8) }
        return c ^ 0xFFFFFFFF
    }

    private static func le16(_ v: UInt16) -> Data { withUnsafeBytes(of: v.littleEndian) { Data($0) } }
    private static func le32(_ v: UInt32) -> Data { withUnsafeBytes(of: v.littleEndian) { Data($0) } }

    mutating func add(_ name: String, _ bytes: Data) {
        let nameData = Data(name.utf8)
        let crc = Self.crc32(bytes)
        let offset = UInt32(data.count)
        var local = Data()
        local += Self.le32(0x04034b50); local += Self.le16(20); local += Self.le16(0x0800); local += Self.le16(0)
        local += Self.le16(0); local += Self.le16(0x21)
        local += Self.le32(crc); local += Self.le32(UInt32(bytes.count)); local += Self.le32(UInt32(bytes.count))
        local += Self.le16(UInt16(nameData.count)); local += Self.le16(0)
        data += local; data += nameData; data += bytes
        var c = Data()
        c += Self.le32(0x02014b50); c += Self.le16(20); c += Self.le16(20); c += Self.le16(0x0800); c += Self.le16(0)
        c += Self.le16(0); c += Self.le16(0x21)
        c += Self.le32(crc); c += Self.le32(UInt32(bytes.count)); c += Self.le32(UInt32(bytes.count))
        c += Self.le16(UInt16(nameData.count)); c += Self.le16(0); c += Self.le16(0); c += Self.le16(0); c += Self.le16(0)
        c += Self.le32(0); c += Self.le32(offset)
        central += c; central += nameData
        count += 1
    }

    mutating func finish() -> Data {
        let start = UInt32(data.count)
        data += central
        var end = Data()
        end += Self.le32(0x06054b50); end += Self.le16(0); end += Self.le16(0)
        end += Self.le16(count); end += Self.le16(count)
        end += Self.le32(UInt32(central.count)); end += Self.le32(start); end += Self.le16(0)
        data += end
        return data
    }
}

/// v40 CBZ yazıcı (Android `CbzWriter`): sayfa resimleri ve ComicInfo.xml.
struct CbzWriter {
    private var zip = ZipWriter()
    private var pages: [(Int, Int, Int)] = []
    let title: String
    let language: String?

    init(title: String, language: String?) { self.title = title; self.language = language }

    static func entryName(_ number: Int, _ ext: String) -> String { String(format: "%05d.%@", number, ext) }

    mutating func addPage(_ bytes: Data, _ width: Int, _ height: Int, _ ext: String) {
        zip.add(Self.entryName(pages.count + 1, ext), bytes)
        pages.append((width, height, bytes.count))
    }

    mutating func finish(date: Date = Date()) -> Data {
        zip.add("ComicInfo.xml", Data(Self.comicInfo(title, pages, date, language).utf8))
        return zip.finish()
    }

    static func comicInfo(_ title: String, _ pages: [(Int, Int, Int)], _ date: Date, _ language: String?) -> String {
        let cal = Calendar(identifier: .gregorian)
        let comp = cal.dateComponents([.year, .month, .day], from: date)
        func xml(_ s: String) -> String {
            s.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;")
                .replacingOccurrences(of: ">", with: "&gt;").replacingOccurrences(of: "\"", with: "&quot;")
        }
        var s = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
        s += "<ComicInfo xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">\n"
        s += "  <Title>\(xml(title))</Title>\n  <Series>\(xml(title))</Series>\n"
        s += "  <Year>\(comp.year ?? 0)</Year>\n  <Month>\(comp.month ?? 0)</Month>\n  <Day>\(comp.day ?? 0)</Day>\n"
        s += "  <PageCount>\(pages.count)</PageCount>\n"
        if let language, !language.trimmed.isEmpty { s += "  <LanguageISO>\(xml(language))</LanguageISO>\n" }
        s += "  <Notes>DrawGuidance</Notes>\n  <Pages>\n"
        for (i, p) in pages.enumerated() {
            s += "    <Page Image=\"\(i)\"" + (i == 0 ? " Type=\"FrontCover\"" : "") + " ImageSize=\"\(p.2)\" ImageWidth=\"\(p.0)\" ImageHeight=\"\(p.1)\" />\n"
        }
        s += "  </Pages>\n</ComicInfo>\n"
        return s
    }
}

/// Dışa aktarılmış bir dosyanın kaydı (Android `SavedImageEntity`; Kaydedilenler ekranı).
struct SavedImage: Equatable, Identifiable, Codable {
    var id: Int64
    var canvasLocalId: String
    var title: String
    /// Dosyanın yolu (varsayılan klasörde göreli; seçilen klasörde güvenlik kapsamlı yer imiyle).
    var path: String
    var bookmark: Data? = nil
    var width: Int
    var height: Int
    var createdAt: Int64
    var location: String
    var sizeBytes: Int64 = 0
    var folderId: String? = nil
    var mime = "image/png"
    var defaultLocation = true

    var isImage: Bool { mime.hasPrefix("image/") && mime != "image/tiff" }

    var typeLabel: String {
        if mime == "application/pdf" { return "PDF" }
        if mime.contains("comicbook") || path.lowercased().hasSuffix(".cbz") { return "CBZ" }
        switch mime {
        case "image/jpeg": return "JPEG"
        case "image/webp": return "WEBP"
        case "image/tiff": return "TIFF"
        default: return "PNG"
        }
    }

    static let CBZ_MIME = "application/vnd.comicbook+zip"
}
