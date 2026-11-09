import Foundation
import StoreKit

/// v39 erişim hakkı anahtarları (sunucu ve Android ile aynı; değiştirilmemeli) (Android `FeatureKeys`).
enum FeatureKeys {
    static let ART_IMAGE_BG_REMOVAL = "art.image_bg_removal"
    static let ART_IMAGE_EFFECTS_ADVANCED = "art.image_effects_advanced"
    static let ART_SHAPE_SUGGEST_ADVANCED = "art.shape_suggest_advanced"
    static let ART_SHAPES_ADVANCED = "art.shapes_advanced"
    static let NOTES_IMAGE_BG_REMOVAL = "notes.image_bg_removal"
    static let NOTES_IMAGE_EFFECTS_ADVANCED = "notes.image_effects_advanced"
    static let NOTES_DIAGRAMS_ADVANCED = "notes.diagrams_advanced"
    static let NOTES_SHAPE_SUGGEST_ADVANCED = "notes.shape_suggest_advanced"
    static let COMIC_IMAGE_BG_REMOVAL = "comic.image_bg_removal"
    static let COMIC_EFFECTS_ADVANCED = "comic.effects_advanced"
    static let COMIC_CHARACTERS_ADVANCED = "comic.characters_advanced"
    static let COMIC_OBJECTS_SUGGEST_ADVANCED = "comic.objects_suggest_advanced"
    static let COMIC_EFFECTS = "comic.effects"
    static let COMIC_CHARACTERS = "comic.characters"
    static let COMIC_BACKGROUNDS = "comic.backgrounds"
    static let COMIC_TEMPLATES = "comic.templates"
    static let COMIC_SHAPES_ADVANCED = "comic.shapes_advanced"
    static let MODEL3D_SUGGEST = "model3d.suggest"
    static let MODEL3D_EXPORT = "model3d.export"
    static let KIDS_CHALLENGE_ADVANCED = "kids.challenge_advanced"
    static let ADS_NONE = "ads.none"

    /// Premium'un bütün hakları (abonelik sayfasında bu sırayla listelenir).
    static let PREMIUM: [String] = [
        ART_IMAGE_BG_REMOVAL, ART_IMAGE_EFFECTS_ADVANCED, ART_SHAPE_SUGGEST_ADVANCED, ART_SHAPES_ADVANCED,
        NOTES_IMAGE_BG_REMOVAL, NOTES_IMAGE_EFFECTS_ADVANCED, NOTES_DIAGRAMS_ADVANCED, NOTES_SHAPE_SUGGEST_ADVANCED,
        COMIC_IMAGE_BG_REMOVAL, COMIC_EFFECTS_ADVANCED, COMIC_CHARACTERS_ADVANCED, COMIC_OBJECTS_SUGGEST_ADVANCED,
        COMIC_EFFECTS, COMIC_CHARACTERS, COMIC_BACKGROUNDS, COMIC_TEMPLATES, COMIC_SHAPES_ADVANCED,
        MODEL3D_SUGGEST, MODEL3D_EXPORT, KIDS_CHALLENGE_ADVANCED, ADS_NONE,
    ]

    static func modeOf(_ key: String) -> String { String(key.split(separator: ".").first ?? "") }
}

/// v39 kilitlenebilen özellik; tuval türüne göre hangi hakkı istediği `key` ile bulunur (Android `PremiumFeature`).
enum PremiumFeature {
    case IMAGE_BG_REMOVAL, IMAGE_EFFECTS_ADVANCED, SHAPE_SUGGEST_ADVANCED
    case COMIC_EFFECTS, COMIC_CHARACTERS, COMIC_CHARACTERS_ADVANCED, COMIC_BACKGROUNDS, COMIC_TEMPLATES, MODEL3D_SUGGEST
    case MODEL3D_EXPORT, KIDS_CHALLENGE_ADVANCED

    func key(_ kind: CanvasKind?) -> String? {
        switch self {
        case .IMAGE_BG_REMOVAL:
            switch kind { case .ART: return FeatureKeys.ART_IMAGE_BG_REMOVAL; case .NOTES: return FeatureKeys.NOTES_IMAGE_BG_REMOVAL
            case .COMIC: return FeatureKeys.COMIC_IMAGE_BG_REMOVAL; default: return nil }
        case .IMAGE_EFFECTS_ADVANCED:
            switch kind { case .ART: return FeatureKeys.ART_IMAGE_EFFECTS_ADVANCED; case .NOTES: return FeatureKeys.NOTES_IMAGE_EFFECTS_ADVANCED
            case .COMIC: return FeatureKeys.COMIC_EFFECTS_ADVANCED; default: return nil }
        case .SHAPE_SUGGEST_ADVANCED:
            switch kind { case .ART: return FeatureKeys.ART_SHAPE_SUGGEST_ADVANCED; case .NOTES: return FeatureKeys.NOTES_SHAPE_SUGGEST_ADVANCED
            case .COMIC: return FeatureKeys.COMIC_OBJECTS_SUGGEST_ADVANCED; default: return nil }
        case .COMIC_EFFECTS: return kind == .COMIC ? FeatureKeys.COMIC_EFFECTS : nil
        case .COMIC_CHARACTERS: return kind == .COMIC ? FeatureKeys.COMIC_CHARACTERS : nil
        case .COMIC_CHARACTERS_ADVANCED: return kind == .COMIC ? FeatureKeys.COMIC_CHARACTERS_ADVANCED : nil
        case .COMIC_BACKGROUNDS: return kind == .COMIC ? FeatureKeys.COMIC_BACKGROUNDS : nil
        case .COMIC_TEMPLATES: return kind == .COMIC ? FeatureKeys.COMIC_TEMPLATES : nil
        case .MODEL3D_SUGGEST: return FeatureKeys.MODEL3D_SUGGEST
        case .MODEL3D_EXPORT: return FeatureKeys.MODEL3D_EXPORT
        case .KIDS_CHALLENGE_ADVANCED: return FeatureKeys.KIDS_CHALLENGE_ADVANCED
        }
    }
}

/// Abonelik (StoreKit 2; Android `SubscriptionRepository` + Play Billing karşılığı). Premium aylık abonelik; hak
/// cihazda doğrulanmış işlemlerden (`Transaction.currentEntitlements`) okunur.
@MainActor
final class SubscriptionStore: ObservableObject {
    static let shared = SubscriptionStore()

    /// App Store Connect'teki ürün kimliği (Info.plist `DGPremiumProductId`; yoksa varsayılan).
    static var productId: String {
        (Bundle.main.object(forInfoDictionaryKey: "DGPremiumProductId") as? String).flatMap { $0.isEmpty ? nil : $0 }
            ?? "com.karapp.drawguidance.premium.monthly"
    }

    enum Message: Equatable { case none, storeUnavailable, productUnavailable, canceled, pending, verified, failed }

    @Published private(set) var product: Product?
    @Published private(set) var isPremium = false
    @Published private(set) var expiresAt: Date?
    @Published private(set) var willRenew = false
    @Published private(set) var busy = false
    @Published var message: Message = .none

    private var updates: Task<Void, Never>?

    private init() {
        updates = Task { [weak self] in
            for await result in Transaction.updates {
                if case let .verified(t) = result { await t.finish() }
                await self?.refresh()
            }
        }
        Task { await refresh(); await loadProduct() }
    }

    deinit { updates?.cancel() }

    func has(_ key: String) -> Bool { isPremium && FeatureKeys.PREMIUM.contains(key) }
    func allows(_ feature: PremiumFeature, _ kind: CanvasKind?) -> Bool { feature.key(kind).map(has) ?? true }
    var showsAds: Bool { !has(FeatureKeys.ADS_NONE) }

    @discardableResult
    func loadProduct() async -> Product? {
        do {
            let p = try await Product.products(for: [Self.productId]).first
            product = p
            if p == nil { message = .productUnavailable }
            return p
        } catch {
            message = .storeUnavailable
            return nil
        }
    }

    func refresh() async {
        var premium = false, expires: Date?, renew = false
        for await result in Transaction.currentEntitlements {
            guard case let .verified(t) = result, t.productID == Self.productId, t.revocationDate == nil else { continue }
            if let e = t.expirationDate, e < Date() { continue }
            premium = true
            expires = t.expirationDate
        }
        if premium, let statuses = try? await product?.subscription?.status, let status = statuses.first,
           case let .verified(info) = status.renewalInfo {
            renew = info.willAutoRenew
        }
        isPremium = premium
        expiresAt = expires
        willRenew = renew
    }

    func purchase() async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        guard let p = product ?? (await loadProduct()) else { message = .productUnavailable; return }
        do {
            switch try await p.purchase() {
            case let .success(result):
                if case let .verified(t) = result { await t.finish(); message = .verified } else { message = .failed }
                await refresh()
            case .pending: message = .pending
            case .userCancelled: message = .canceled
            @unknown default: message = .failed
            }
        } catch {
            message = .failed
        }
    }

    func restore() async {
        busy = true
        defer { busy = false }
        try? await AppStore.sync()
        await refresh()
    }
}
