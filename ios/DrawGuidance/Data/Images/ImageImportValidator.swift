import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

/// Eklenmeye hazır, kaydedilecek en küçük biçime getirilmiş resim.
struct PreparedImage {
    let bytes: Data
    let mimeType: String
    let width: Int
    let height: Int
    let image: CGImage
    let originalBytes: Int
    let detailScore: Float
}

/// Resim kontrolünde bulunan sorun; hata (eklenmez) ya da uyarı (onayla eklenir).
enum ImageIssue: Equatable {
    case tooLarge(Int)
    case unsupportedFormat
    case unreadable
    /// v54 resmin içinde/sonunda kod ya da başka bir dosya var.
    case suspicious
    case tooSmall(Int, Int)
    case dimensionsTooLarge(Int, Int)
    case tooDetailed(Int)
    // Uyarılar
    case detailed(Int)
    case willDownscale(Int, Int, Int, Int)
    case nearSizeLimit(Int)

    /// Kullanıcıya gösterilen açıklama (Android metinleri).
    var message: String {
        switch self {
        // v54 sınırlar 10 MB ve 8192 px / 64 MP
        case .tooLarge(let b): return L("v54_image_err_too_large", ImageImportValidator.kb(b))
        case .unsupportedFormat: return L("image_err_format")
        case .unreadable: return L("image_err_unreadable")
        case .suspicious: return L("v54_image_err_suspicious")
        case .tooSmall(let w, let h): return L("image_err_too_small", w, h)
        case .dimensionsTooLarge(let w, let h): return L("v54_image_err_dimensions", w, h)
        case .tooDetailed(let p): return L("image_err_detail", p)
        case .detailed(let p): return L("image_warn_detail", p)
        case .willDownscale(let w, let h, let tw, let th): return L("image_warn_downscale", w, h, tw, th)
        case .nearSizeLimit(let b): return L("v54_image_warn_near_limit", ImageImportValidator.kb(b))
        }
    }

    var isError: Bool {
        switch self {
        case .detailed, .willDownscale, .nearSizeLimit: return false
        default: return true
        }
    }
}

struct ImageCheckResult {
    let errors: [ImageIssue]
    let warnings: [ImageIssue]
    let image: PreparedImage?
    var accepted: Bool { errors.isEmpty && image != nil }
}

/// Tuvale eklenecek resmin sıkı kontrolleri (Android `ImageImportValidator`):
/// - Biçim: yalnızca PNG, JPEG, WEBP (dosya imzasından)
/// - v54 yapı ve gömülü kod denetimi (`ImageSecurity`): betik, HTML, çalıştırılabilir ya da arşiv içeren resim reddedilir
/// - v54 dosya boyutu en fazla 10 MB (8 MB üstü uyarı)
/// - v54 kenarlar 16–8192 px, en fazla 64 MP; uzun kenarı 3000 px'ten (giriş düzeyinde 2000 px) büyükse küçültülür
///   (uyarı). Büyük resim tam boyda açılmaz (ImageIO küçük resim olarak hedef boyda çözer).
/// - Detay: 256 px'e küçültülmüş gri görüntüde güçlü kenarlı piksel oranı; %30 ve üstü reddedilir, %15 ve üstü uyarı.
/// Kaydedilecek veri: v54 her zaman yeniden kodlanır (üst veri ve eklenmiş veriler atılır): JPEG kaynakta JPEG, ötekilerde PNG.
enum ImageImportValidator {
    static let MAX_BYTES = 10 * 1024 * 1024
    static let WARN_BYTES = 8 * 1024 * 1024
    static let MIN_SIDE = 16
    static let MAX_SIDE = 8192
    static let MAX_PIXELS = 64_000_000
    static let STORE_MAX_SIDE = 3000
    static let STORE_MAX_SIDE_LOW = 2000
    static let DETAIL_REJECT: Float = 0.30
    static let DETAIL_WARN: Float = 0.15
    private static let ANALYSIS_SIDE = 256
    private static let EDGE_THRESHOLD = 96.0

    /// Cihaz sınıfına göre saklanan resmin uzun kenarı.
    static var storeMaxSide: Int { Budget3D.tier == .LOW ? STORE_MAX_SIDE_LOW : STORE_MAX_SIDE }

    static func kb(_ b: Int) -> String { b >= 1024 * 1024 ? String(format: "%.1f MB", Double(b) / 1048576).replacingOccurrences(of: ".", with: ",") : "\(b / 1024) KB" }

    static func check(_ bytes: Data, storeSide: Int = storeMaxSide) -> ImageCheckResult {
        var errors: [ImageIssue] = [], warnings: [ImageIssue] = []
        if bytes.count > MAX_BYTES { errors.append(.tooLarge(bytes.count)) }
        guard let mime = sniffMime(bytes) else { return ImageCheckResult(errors: errors + [.unsupportedFormat], warnings: [], image: nil) }
        if !errors.isEmpty { return ImageCheckResult(errors: errors, warnings: warnings, image: nil) }
        // v54 yapı ve gömülü kod denetimi (çözmeden önce)
        switch ImageSecurity.inspect(bytes, mime: mime) {
        case .MALFORMED: return ImageCheckResult(errors: [.unreadable], warnings: [], image: nil)
        case .SUSPICIOUS: return ImageCheckResult(errors: [.suspicious], warnings: [], image: nil)
        case .CLEAN: break
        }
        if bytes.count > WARN_BYTES { warnings.append(.nearSizeLimit(bytes.count)) }
        guard let src = CGImageSourceCreateWithData(bytes as CFData, nil),
              let props = CGImageSourceCopyPropertiesAtIndex(src, 0, nil) as? [CFString: Any],
              let w = props[kCGImagePropertyPixelWidth] as? Int, let h = props[kCGImagePropertyPixelHeight] as? Int, w > 0, h > 0 else {
            return ImageCheckResult(errors: [.unreadable], warnings: [], image: nil)
        }
        if w < MIN_SIDE || h < MIN_SIDE { errors.append(.tooSmall(w, h)) }
        if w > MAX_SIDE || h > MAX_SIDE || w * h > MAX_PIXELS { errors.append(.dimensionsTooLarge(w, h)) }
        if !errors.isEmpty { return ImageCheckResult(errors: errors, warnings: warnings, image: nil) }
        let longest = max(w, h)
        let factor: Float = longest > storeSide ? Float(storeSide) / Float(longest) : 1
        let tw = max(1, ktRound(Float(w) * factor)), th = max(1, ktRound(Float(h) * factor))
        guard let image = decodeTo(src, w: w, h: h, tw: tw, th: th) else {
            return ImageCheckResult(errors: [.unreadable], warnings: [], image: nil)
        }
        if factor < 1 { warnings.append(.willDownscale(w, h, image.width, image.height)) }
        let detail = detailScore(image)
        let percent = ktRound(detail * 100)
        if detail >= DETAIL_REJECT { errors.append(.tooDetailed(percent)) } else if detail >= DETAIL_WARN { warnings.append(.detailed(percent)) }
        if !errors.isEmpty { return ImageCheckResult(errors: errors, warnings: warnings, image: nil) }
        guard let (stored, storedMime) = reencode(mime, image) else { return ImageCheckResult(errors: [.unreadable], warnings: [], image: nil) }
        return ImageCheckResult(errors: errors, warnings: warnings,
                                image: PreparedImage(bytes: stored, mimeType: storedMime, width: image.width, height: image.height, image: image,
                                                     originalBytes: bytes.count, detailScore: detail))
    }

    /// v54 hedef boyda çözme: küçültülecekse ImageIO küçük resmi (tam boy bitmap açılmaz), ardından önçarpımlı RGBA kopya.
    static func decodeTo(_ src: CGImageSource, w: Int, h: Int, tw: Int, th: Int) -> CGImage? {
        let decoded: CGImage?
        if tw != w || th != h {
            let opts: [CFString: Any] = [kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceThumbnailMaxPixelSize: max(tw, th),
                                         kCGImageSourceShouldCacheImmediately: true]
            decoded = CGImageSourceCreateThumbnailAtIndex(src, 0, opts as CFDictionary)
        } else {
            decoded = CGImageSourceCreateImageAtIndex(src, 0, nil)
        }
        guard let decoded else { return nil }
        return scaled(decoded, decoded.width, decoded.height)
    }

    /// Uygulamanın kendi kaydettiği resim (Resimlerim): boyut sınırı yok, yapı denetimi var; uzun kenar cihaza göre küçültülür.
    static func prepareSaved(_ bytes: Data) -> PreparedImage? {
        guard let mime = sniffMime(bytes), ImageSecurity.inspect(bytes, mime: mime) == .CLEAN,
              let src = CGImageSourceCreateWithData(bytes as CFData, nil),
              let props = CGImageSourceCopyPropertiesAtIndex(src, 0, nil) as? [CFString: Any],
              let w = props[kCGImagePropertyPixelWidth] as? Int, let h = props[kCGImagePropertyPixelHeight] as? Int, w > 0, h > 0 else { return nil }
        let side = storeMaxSide
        let factor: Float = max(w, h) > side ? Float(side) / Float(max(w, h)) : 1
        guard let image = decodeTo(src, w: w, h: h, tw: max(1, ktRound(Float(w) * factor)), th: max(1, ktRound(Float(h) * factor))),
              let png = encode(image, UTType.png) else { return nil }
        let alpha = hasAlpha(image)
        if !alpha && png.count > 600 * 1024, let jpg = encode(image, UTType.jpeg, quality: 0.9) {
            return PreparedImage(bytes: jpg, mimeType: "image/jpeg", width: image.width, height: image.height, image: image, originalBytes: bytes.count, detailScore: 0)
        }
        return PreparedImage(bytes: png, mimeType: "image/png", width: image.width, height: image.height, image: image, originalBytes: bytes.count, detailScore: 0)
    }

    /// Dosya imzası: PNG, JPEG, WEBP; diğer her şey desteklenmez.
    static func sniffMime(_ d: Data) -> String? {
        let b = [UInt8](d.prefix(12))
        if b.count >= 8 && b[0] == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47 { return "image/png" }
        if b.count >= 3 && b[0] == 0xFF && b[1] == 0xD8 && b[2] == 0xFF { return "image/jpeg" }
        if b.count >= 12 && b[0...3] == [0x52, 0x49, 0x46, 0x46] && b[8...11] == [0x57, 0x45, 0x42, 0x50] { return "image/webp" }
        return nil
    }

    /// Önçarpımlı RGBA piksellere çizilmiş kopya.
    static func scaled(_ img: CGImage, _ w: Int, _ h: Int) -> CGImage? {
        guard let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: Bitmaps.space,
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        ctx.interpolationQuality = .high
        ctx.draw(img, in: CGRect(x: 0, y: 0, width: w, height: h))
        return ctx.makeImage()
    }

    /// RGBA (önçarpımlı) pikseller, üst satır önce.
    static func rgba(_ img: CGImage) -> [UInt8] {
        let w = img.width, h = img.height
        var data = [UInt8](repeating: 0, count: w * h * 4)
        data.withUnsafeMutableBytes { buf in
            let ctx = CGContext(data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: Bitmaps.space,
                                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
            ctx.draw(img, in: CGRect(x: 0, y: 0, width: w, height: h))
        }
        return data
    }

    static func hasAlpha(_ img: CGImage) -> Bool {
        switch img.alphaInfo {
        case .none, .noneSkipFirst, .noneSkipLast: return false
        default:
            let px = rgba(img)
            for i in stride(from: 3, to: px.count, by: 4) where px[i] < 255 { return true }
            return false
        }
    }

    /// Detay puanı (0..1): 256 px'e küçültülmüş gri görüntüde Sobel kenar büyüklüğü eşiği aşan piksel oranı.
    /// Saydam pikseller beyaz sayılır.
    static func detailScore(_ source: CGImage) -> Float {
        let scale = Float(ANALYSIS_SIDE) / Float(max(source.width, source.height))
        let w = max(3, scale < 1 ? ktRound(Float(source.width) * scale) : source.width)
        let h = max(3, scale < 1 ? ktRound(Float(source.height) * scale) : source.height)
        guard let small = (w != source.width || h != source.height) ? scaled(source, w, h) : source else { return 0 }
        let px = rgba(small)
        var gray = [Int](repeating: 255, count: w * h)
        for i in 0..<(w * h) {
            let a = Int(px[i * 4 + 3])
            if a == 0 { continue }
            // Önçarpımı geri al
            let r = Int(px[i * 4]) * 255 / a, g = Int(px[i * 4 + 1]) * 255 / a, b = Int(px[i * 4 + 2]) * 255 / a
            let l = (r * 299 + g * 587 + b * 114) / 1000
            gray[i] = (l * a + 255 * (255 - a)) / 255
        }
        var strong = 0, total = 0
        for y in 1..<(h - 1) {
            for x in 1..<(w - 1) {
                let i = y * w + x
                let gx = -gray[i - w - 1] - 2 * gray[i - 1] - gray[i + w - 1] + gray[i - w + 1] + 2 * gray[i + 1] + gray[i + w + 1]
                let gy = -gray[i - w - 1] - 2 * gray[i - w] - gray[i - w + 1] + gray[i + w - 1] + 2 * gray[i + w] + gray[i + w + 1]
                if Double(gx * gx + gy * gy).squareRoot() > EDGE_THRESHOLD { strong += 1 }
                total += 1
            }
        }
        return total == 0 ? 0 : Float(strong) / Float(total)
    }

    static func encode(_ img: CGImage, _ type: UTType, quality: Double = 1) -> Data? {
        let out = NSMutableData()
        guard let dst = CGImageDestinationCreateWithData(out, type.identifier as CFString, 1, nil) else { return nil }
        CGImageDestinationAddImage(dst, img, [kCGImageDestinationLossyCompressionQuality: quality] as CFDictionary)
        return CGImageDestinationFinalize(dst) ? out as Data : nil
    }

    /// v54 her zaman yeniden kodlanır (özgün dosya saklanmaz): JPEG kaynakta (saydamlık yoksa) JPEG %92, ötekilerde PNG.
    private static func reencode(_ mime: String, _ img: CGImage) -> (Data, String)? {
        if mime == "image/jpeg" && !hasAlpha(img), let jpg = encode(img, .jpeg, quality: 0.92), !jpg.isEmpty { return (jpg, "image/jpeg") }
        if let png = encode(img, .png), !png.isEmpty { return (png, "image/png") }
        return nil
    }
}
