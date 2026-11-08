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
        case .tooLarge(let b): return L("image_err_too_large", ImageImportValidator.kb(b))
        case .unsupportedFormat: return L("image_err_format")
        case .unreadable: return L("image_err_unreadable")
        case .tooSmall(let w, let h): return L("image_err_too_small", w, h)
        case .dimensionsTooLarge(let w, let h): return L("image_err_dimensions", w, h)
        case .tooDetailed(let p): return L("image_err_detail", p)
        case .detailed(let p): return L("image_warn_detail", p)
        case .willDownscale(let w, let h, let tw, let th): return L("image_warn_downscale", w, h, tw, th)
        case .nearSizeLimit(let b): return L("image_warn_near_limit", ImageImportValidator.kb(b))
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
/// - Dosya boyutu en fazla 1 MB (0,8 MB üstü uyarı)
/// - Kenarlar 16–4096 px, en fazla 16 MP; uzun kenarı 1600 px'ten büyükse küçültülür (uyarı)
/// - Detay: 256 px'e küçültülmüş gri görüntüde güçlü kenarlı piksel oranı; %30 ve üstü reddedilir, %15 ve üstü uyarı.
/// Kaydedilecek veri: özgün, PNG (ve küçültülen JPEG'de JPEG) içinden en küçüğü.
enum ImageImportValidator {
    static let MAX_BYTES = 1024 * 1024
    static let WARN_BYTES = 800 * 1024
    static let MIN_SIDE = 16
    static let MAX_SIDE = 4096
    static let MAX_PIXELS = 16_000_000
    static let STORE_MAX_SIDE = 1600
    static let DETAIL_REJECT: Float = 0.30
    static let DETAIL_WARN: Float = 0.15
    private static let ANALYSIS_SIDE = 256
    private static let EDGE_THRESHOLD = 96.0

    static func kb(_ b: Int) -> String { b >= 1024 * 1024 ? String(format: "%.1f MB", Double(b) / 1048576).replacingOccurrences(of: ".", with: ",") : "\(b / 1024) KB" }

    static func check(_ bytes: Data) -> ImageCheckResult {
        var errors: [ImageIssue] = [], warnings: [ImageIssue] = []
        if bytes.count > MAX_BYTES { errors.append(.tooLarge(bytes.count)) }
        guard let mime = sniffMime(bytes) else { return ImageCheckResult(errors: errors + [.unsupportedFormat], warnings: [], image: nil) }
        if !errors.isEmpty { return ImageCheckResult(errors: errors, warnings: warnings, image: nil) }
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
        let factor: Float = longest > STORE_MAX_SIDE ? Float(STORE_MAX_SIDE) / Float(longest) : 1
        let tw = max(1, ktRound(Float(w) * factor)), th = max(1, ktRound(Float(h) * factor))
        guard let decoded = CGImageSourceCreateImageAtIndex(src, 0, nil), let image = scaled(decoded, tw, th) else {
            return ImageCheckResult(errors: [.unreadable], warnings: [], image: nil)
        }
        if factor < 1 { warnings.append(.willDownscale(w, h, tw, th)) }
        let detail = detailScore(image)
        let percent = ktRound(detail * 100)
        if detail >= DETAIL_REJECT { errors.append(.tooDetailed(percent)) } else if detail >= DETAIL_WARN { warnings.append(.detailed(percent)) }
        if !errors.isEmpty { return ImageCheckResult(errors: errors, warnings: warnings, image: nil) }
        let (stored, storedMime) = smallestEncoding(bytes, mime, image, keepOriginal: factor == 1)
        return ImageCheckResult(errors: errors, warnings: warnings,
                                image: PreparedImage(bytes: stored, mimeType: storedMime, width: image.width, height: image.height, image: image,
                                                     originalBytes: bytes.count, detailScore: detail))
    }

    /// Uygulamanın kendi kaydettiği resim (Resimlerim): sınır yok, yalnız uzun kenar 1600 px'e küçültülür.
    static func prepareSaved(_ bytes: Data) -> PreparedImage? {
        guard let decoded = CanvasImageCache.decode(bytes) else { return nil }
        let w = decoded.width, h = decoded.height
        let factor: Float = max(w, h) > STORE_MAX_SIDE ? Float(STORE_MAX_SIDE) / Float(max(w, h)) : 1
        guard let image = scaled(decoded, max(1, ktRound(Float(w) * factor)), max(1, ktRound(Float(h) * factor))),
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

    private static func smallestEncoding(_ original: Data, _ mime: String, _ img: CGImage, keepOriginal: Bool) -> (Data, String) {
        var candidates: [(Data, String)] = []
        if keepOriginal { candidates.append((original, mime)) }
        if let png = encode(img, .png) { candidates.append((png, "image/png")) }
        // Küçültülen JPEG fotoğraflar için JPEG de aday (kaynak zaten kayıplı)
        if !keepOriginal && mime == "image/jpeg", let jpg = encode(img, .jpeg, quality: 0.92) { candidates.append((jpg, "image/jpeg")) }
        return candidates.filter { !$0.0.isEmpty }.min { $0.0.count < $1.0.count } ?? (original, mime)
    }
}
