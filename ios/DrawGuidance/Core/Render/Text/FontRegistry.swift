import CoreText
import Foundation
import UIKit

/// Yazı tipleri (Android `FontRegistry`): uygulamayla gelen dosyalar (`Fonts/*.ttf`) ve sistem aileleri.
/// Kalın/eğik biçimi dosyada yoksa yapay kalın (kenar çizgisi) ve yapay eğim kullanılır.
enum FontRegistry {
    private static var descriptors: [TextFont: CTFontDescriptor] = [:]
    private static let lock = NSLock()

    /// Uygulamayla gelen yazı tipi dosyasının tanımı (yoksa nil).
    private static func bundled(_ font: TextFont) -> CTFontDescriptor? {
        guard let asset = font.asset else { return nil }
        lock.lock(); defer { lock.unlock() }
        if let d = descriptors[font] { return d }
        let name = (asset as NSString).lastPathComponent.replacingOccurrences(of: ".ttf", with: "")
        guard let url = Bundle.main.url(forResource: name, withExtension: "ttf") ?? Bundle(for: Marker.self).url(forResource: name, withExtension: "ttf"),
              let list = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor], let d = list.first else { return nil }
        descriptors[font] = d
        return d
    }

    private final class Marker {}

    /// Sistem ailesinin karşılığı.
    private static func system(_ font: TextFont, _ size: CGFloat) -> UIFont {
        func design(_ d: UIFontDescriptor.SystemDesign, _ w: UIFont.Weight = .regular) -> UIFont {
            let base = UIFont.systemFont(ofSize: size, weight: w)
            return base.fontDescriptor.withDesign(d).map { UIFont(descriptor: $0, size: size) } ?? base
        }
        switch font.family {
        case "serif": return design(.serif)
        case "monospace": return design(.monospaced)
        case "serif-monospace": return UIFont(name: "Courier", size: size) ?? design(.monospaced)
        case "cursive": return UIFont(name: "SnellRoundhand", size: size) ?? design(.rounded)
        case "casual": return UIFont(name: "MarkerFelt-Thin", size: size) ?? design(.rounded)
        case "sans-serif-condensed":
            let d = UIFont.systemFont(ofSize: size).fontDescriptor.addingAttributes([.traits: [UIFontDescriptor.TraitKey.width: -0.2]])
            return UIFont(descriptor: d, size: size)
        case "sans-serif-light": return .systemFont(ofSize: size, weight: .light)
        case "sans-serif-medium": return .systemFont(ofSize: size, weight: .medium)
        case "sans-serif-black": return .systemFont(ofSize: size, weight: .black)
        case "sans-serif-smallcaps":
            let d = UIFont.systemFont(ofSize: size).fontDescriptor.addingAttributes([.featureSettings: [[
                UIFontDescriptor.FeatureKey.type: kLowerCaseType, UIFontDescriptor.FeatureKey.selector: kLowerCaseSmallCapsSelector]]])
            return UIFont(descriptor: d, size: size)
        default: return .systemFont(ofSize: size)
        }
    }

    /// Yazı tipi ve istenen biçimin uygulanıp uygulanamadığı (uygulanamadıysa yapay kalın/eğik gerekir).
    static func font(_ font: TextFont, size: CGFloat, bold: Bool, italic: Bool) -> (font: CTFont, fakeBold: Bool, fakeItalic: Bool) {
        let base: CTFont = bundled(font).map { CTFontCreateWithFontDescriptor($0, size, nil) } ?? (system(font, size) as CTFont)
        var traits: CTFontSymbolicTraits = []
        if bold { traits.insert(.traitBold) }
        if italic { traits.insert(.traitItalic) }
        if traits.isEmpty { return (base, false, false) }
        if let styled = CTFontCreateCopyWithSymbolicTraits(base, size, nil, traits, traits) { return (styled, false, false) }
        // Yalnız biri uygulanabiliyorsa onu kullan
        if bold, italic {
            if let b = CTFontCreateCopyWithSymbolicTraits(base, size, nil, .traitBold, .traitBold) { return (b, false, true) }
            if let i = CTFontCreateCopyWithSymbolicTraits(base, size, nil, .traitItalic, .traitItalic) { return (i, true, false) }
        }
        return (base, bold, italic)
    }

    /// Ekranda (seçicide) yazı tipinin kendisiyle göstermek için.
    static func uiFont(_ f: TextFont, size: CGFloat) -> UIFont { font(f, size: size, bold: false, italic: false).font as UIFont }
}
