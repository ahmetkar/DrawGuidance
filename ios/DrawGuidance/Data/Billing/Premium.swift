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

/// Abonelik (StoreKit 2; Android `SubscriptionRepository` + Play Billing karşılığı). Premium aylık abonelik.
///
/// Hak iki kaynaktan gelir, biri yeterlidir (kapalı kalacak şekilde):
/// - Cihazda Apple'ın imzasıyla doğrulanmış işlem (`Transaction.currentEntitlements`): girişsiz de çalışır (App Review
///   girişsiz satın almayı ister).
/// - Sunucudaki abonelik (`/subscriptions/me`): aynı Firebase hesabıyla Android'de (Google Play) alınmış Premium da
///   iOS'ta açılır. Son yanıt uid başına saklanır, çevrim dışında bitiş zamanına kadar geçerlidir.
///
/// Giriş yapılmışken satın alma `appAccountToken` (uid'den) ile yapılır ve sunucuda doğrulanır
/// (`/subscriptions/verify-apple`); girişsiz alınmış abonelik giriş yapılınca hesaba bağlanır. Böylece iOS'ta alınan
/// Premium Android'de de tanınır.
@MainActor
final class SubscriptionStore: ObservableObject {
    static let shared = SubscriptionStore()

    /// App Store Connect'teki ürün kimliği (Info.plist `DGPremiumProductId`; yoksa varsayılan).
    static var productId: String {
        (Bundle.main.object(forInfoDictionaryKey: "DGPremiumProductId") as? String).flatMap { $0.isEmpty ? nil : $0 }
            ?? "com.karapp.drawguidance.premium.monthly"
    }

    enum Message: Equatable {
        case none, storeUnavailable, productUnavailable, canceled, pending, verified, failed
        case restored, nothingToRestore, linked, serverUnavailable, otherAccount, offline
    }

    @Published private(set) var product: Product?
    /// Cihazdaki StoreKit hakkı.
    @Published private(set) var localPremium = false
    /// Sunucudaki abonelik (giriş yoksa nil).
    @Published private(set) var server: ServerSubscription?
    @Published private(set) var expiresAt: Date?
    @Published private(set) var willRenew = false
    @Published private(set) var busy = false
    @Published var message: Message = .none

    /// Premium mı (cihazdaki işlem ya da sunucudaki abonelik).
    var isPremium: Bool { localPremium || (server?.grants(FeatureKeys.ADS_NONE) ?? false) }

    /// Abonelik Google Play'den (Android) alınmış ve bu cihazda App Store aboneliği yok: yönetim Google Play'de.
    var managedOnGooglePlay: Bool {
        !localPremium && server?.store == ServerSubscription.STORE_PLAY && (server?.active ?? false)
    }

    private weak var account: SubscriptionAccount?
    private var lastUid: String?
    private var updates: Task<Void, Never>?
    private let api: SubscriptionApi
    private let defaults: UserDefaults

    init(api: SubscriptionApi = SubscriptionApi(), defaults: UserDefaults = .standard, listen: Bool = true) {
        self.api = api
        self.defaults = defaults
        guard listen else { return }
        updates = Task { [weak self] in
            for await result in Transaction.updates {
                // Uygulama dışında olan yenileme, iade, Aile Paylaşımı, teklif kodu, bekleyen ödemenin onayı
                if case let .verified(t) = result {
                    await t.finish()
                    await self?.sendToServer(result.jwsRepresentation, productId: t.productID)
                }
                await self?.refresh()
            }
        }
        Task { await refresh(); await loadProduct() }
    }

    deinit { updates?.cancel() }

    func has(_ key: String) -> Bool {
        guard FeatureKeys.PREMIUM.contains(key) else { return false }
        return localPremium || (server?.grants(key) ?? false)
    }

    func allows(_ feature: PremiumFeature, _ kind: CanvasKind?) -> Bool { feature.key(kind).map(has) ?? true }
    var showsAds: Bool { !has(FeatureKeys.ADS_NONE) }

    // MARK: - Hesap

    /// Hesabı bağlar (açılışta) ve giriş/çıkış değişince yeniden çağrılır (`accountChanged`).
    func connect(_ account: SubscriptionAccount) {
        self.account = account
        Task { await accountChanged() }
    }

    /// Giriş ya da çıkıştan sonra: sunucudaki abonelik okunur, cihazdaki bağlanmamış abonelik hesaba bağlanır.
    func accountChanged() async {
        let uid = account?.subscriptionUid
        guard uid != lastUid else { return }
        lastUid = uid
        guard let uid else { server = nil; recompute(); return }
        server = cached(uid)
        recompute()
        await refreshServer()
        // Girişten önce (ya da başka cihazda) alınmış App Store aboneliği bu hesaba bağlanır
        if localPremium, server?.store != ServerSubscription.STORE_APP_STORE || !(server?.active ?? false) {
            _ = await verifyCurrentEntitlements()
        }
    }

    // MARK: - StoreKit

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

    /// Cihazdaki hak (StoreKit) yeniden okunur.
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
        localPremium = premium
        localExpires = expires
        localRenew = renew
        recompute()
    }

    private var localExpires: Date?
    private var localRenew = false

    private func recompute() {
        if localPremium {
            expiresAt = localExpires
            willRenew = localRenew
        } else if let s = server, s.grants(FeatureKeys.ADS_NONE) {
            expiresAt = s.expiryTime
            willRenew = s.autoRenewing ?? false
        } else {
            expiresAt = nil
            willRenew = false
        }
    }

    func purchase() async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        var loaded = product
        if loaded == nil { loaded = await loadProduct() }
        guard let p = loaded else { message = .productUnavailable; return }
        var options: Set<Product.PurchaseOption> = []
        if let uid = account?.subscriptionUid { options.insert(.appAccountToken(AppAccountToken.forUid(uid))) }
        do {
            switch try await p.purchase(options: options) {
            case let .success(result):
                guard case let .verified(t) = result else { message = .failed; return }
                await t.finish()
                await refresh()
                let linked = await sendToServer(result.jwsRepresentation, productId: t.productID)
                message = linked == .otherAccount ? .otherAccount : .verified
            case .pending: message = .pending
            case .userCancelled: message = .canceled
            @unknown default: message = .failed
            }
        } catch {
            message = .failed
        }
    }

    /// "Satın alımları geri yükle": App Store eşitlenir; giriş varsa geçerli abonelik hesaba bağlanır ve sunucudaki
    /// (ör. Android'de alınmış) abonelik okunur.
    func restore() async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        do {
            try await AppStore.sync()
        } catch StoreKitError.userCancelled {
            message = .canceled
            return
        } catch {
            message = .storeUnavailable
        }
        await refresh()
        var linked: Message?
        if account?.subscriptionUid != nil {
            linked = await verifyCurrentEntitlements()
            await refreshServer()
        }
        if let linked, linked == .otherAccount { message = .otherAccount; return }
        message = isPremium ? .restored : .nothingToRestore
    }

    // MARK: - Sunucu

    /// Sunucudaki abonelik (`/subscriptions/me`).
    func refreshServer() async {
        guard let uid = account?.subscriptionUid else { return }
        do {
            let s = try await withToken { try await self.api.me(token: $0) }
            if account?.subscriptionUid == uid { store(s, uid: uid) }
        } catch {
            // Çevrim dışı ya da sunucu hatası: saklanan yanıt bitiş zamanına kadar geçerli kalır
        }
    }

    /// Cihazdaki geçerli işlemler sunucuda doğrulanır (hesaba bağlanır). Sonuç iletisi; işlem yoksa nil.
    private func verifyCurrentEntitlements() async -> Message? {
        var last: Message?
        for await result in Transaction.currentEntitlements {
            guard case let .verified(t) = result, t.productID == Self.productId else { continue }
            last = await sendToServer(result.jwsRepresentation, productId: t.productID)
        }
        return last
    }

    /// İmzalı işlem sunucuya gönderilir (giriş yoksa gönderilmez; hak cihazda zaten açık).
    @discardableResult
    private func sendToServer(_ jws: String, productId: String) async -> Message? {
        guard productId == Self.productId, let uid = account?.subscriptionUid else { return nil }
        do {
            let s = try await withToken { try await self.api.verifyApple(token: $0, signedTransaction: jws) }
            if account?.subscriptionUid == uid { store(s, uid: uid) }
            return .linked
        } catch let e as SubscriptionServerError {
            switch e.code {
            case "account_mismatch", "purchase_token_in_use": return .otherAccount
            default: return .serverUnavailable
            }
        } catch {
            return .offline
        }
    }

    private func withToken<T>(_ block: (String) async throws -> T) async throws -> T {
        guard let account, let token = await account.subscriptionIdToken(force: false) else {
            throw SubscriptionServerError(status: 401, code: "no_token")
        }
        do {
            return try await block(token)
        } catch let e as SubscriptionServerError where e.status == 401 {
            guard let fresh = await account.subscriptionIdToken(force: true) else { throw e }
            return try await block(fresh)
        }
    }

    // MARK: - Önbellek (uid başına son sunucu yanıtı)

    private static func cacheKey(_ uid: String) -> String { "subscription.server." + uid }

    private func store(_ s: ServerSubscription, uid: String) {
        server = s
        if let data = try? JSONEncoder().encode(s) { defaults.set(data, forKey: Self.cacheKey(uid)) }
        recompute()
    }

    private func cached(_ uid: String) -> ServerSubscription? {
        defaults.data(forKey: Self.cacheKey(uid)).flatMap { try? JSONDecoder().decode(ServerSubscription.self, from: $0) }
    }
}
