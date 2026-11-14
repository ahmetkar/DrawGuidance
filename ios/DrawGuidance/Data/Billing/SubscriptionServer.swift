import CryptoKit
import Foundation

/// Abonelik için gereken hesap bilgisi (Firebase). `AccountStore` bu protokole uyar ve açılışta
/// `SubscriptionStore.shared.connect(...)` ile bağlanır; giriş yoksa `uid` nil.
@MainActor
protocol SubscriptionAccount: AnyObject {
    /// Giriş yapmış kullanıcının Firebase uid'si (yoksa nil).
    var subscriptionUid: String? { get }
    /// Firebase kimlik jetonu (`force`: yenilenmiş jeton).
    func subscriptionIdToken(force: Bool) async -> String?
}

/// StoreKit'teki `appAccountToken`: Firebase uid'sinin UTF-8 SHA-256 özetinin ilk 16 baytı, RFC 9562 sürüm 8 UUID.
/// Sunucudaki `AppleStates.appAccountToken` ile birebir aynı (test vektörü: "test-uid-123" →
/// a8c418aa-055f-8fdb-9801-967606c84f12). Satın alma bu değerle yapılınca sunucu onu yalnız bu hesaba bağlar.
enum AppAccountToken {
    static func forUid(_ uid: String) -> UUID {
        var b = Array(SHA256.hash(data: Data(uid.utf8)).prefix(16))
        b[6] = (b[6] & 0x0f) | 0x80
        b[8] = (b[8] & 0x3f) | 0x80
        return UUID(uuid: (b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]))
    }
}

/// Sunucudaki abonelik (`GET /api/v1/subscriptions/me` ve `POST /api/v1/subscriptions/verify-apple` yanıtı; Android
/// `RemoteSubscription`). Aynı Firebase hesabının Google Play ya da App Store aboneliği burada görünür.
struct ServerSubscription: Codable, Equatable {
    var planId: String = "free"
    var status: String?
    var active: Bool = false
    var productId: String?
    var expiryTime: Date?
    var autoRenewing: Bool?
    var features: [String] = []
    var cancelReason: String?
    /// "GOOGLE_PLAY" ya da "APP_STORE" (abonelik yoksa nil).
    var store: String?

    static let STORE_PLAY = "GOOGLE_PLAY", STORE_APP_STORE = "APP_STORE"

    enum CodingKeys: String, CodingKey { case planId, status, active, productId, expiryTime, autoRenewing, features, cancelReason, store }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        planId = (try? c.decodeIfPresent(String.self, forKey: .planId)) ?? "free"
        status = try? c.decodeIfPresent(String.self, forKey: .status)
        active = (try? c.decodeIfPresent(Bool.self, forKey: .active)) ?? false
        productId = try? c.decodeIfPresent(String.self, forKey: .productId)
        expiryTime = Self.time(c, .expiryTime)
        autoRenewing = try? c.decodeIfPresent(Bool.self, forKey: .autoRenewing)
        features = (try? c.decodeIfPresent([String].self, forKey: .features)) ?? []
        cancelReason = try? c.decodeIfPresent(String.self, forKey: .cancelReason)
        store = try? c.decodeIfPresent(String.self, forKey: .store)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(planId, forKey: .planId)
        try c.encodeIfPresent(status, forKey: .status)
        try c.encode(active, forKey: .active)
        try c.encodeIfPresent(productId, forKey: .productId)
        try c.encodeIfPresent(expiryTime.map { ISO8601DateFormatter().string(from: $0) }, forKey: .expiryTime)
        try c.encodeIfPresent(autoRenewing, forKey: .autoRenewing)
        try c.encode(features, forKey: .features)
        try c.encodeIfPresent(cancelReason, forKey: .cancelReason)
        try c.encodeIfPresent(store, forKey: .store)
    }

    /// ISO 8601 metin (kesirli saniyeli ya da değil) ya da epoch ms.
    private static func time(_ c: KeyedDecodingContainer<CodingKeys>, _ k: CodingKeys) -> Date? {
        if let ms = try? c.decodeIfPresent(Double.self, forKey: k) { return Date(timeIntervalSince1970: ms / 1000) }
        guard let s = try? c.decodeIfPresent(String.self, forKey: k), !s.isEmpty else { return nil }
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = f.date(from: s) { return d }
        f.formatOptions = [.withInternetDateTime]
        return f.date(from: s)
    }

    /// Hak var mı: sunucu etkin diyor, Premium hakları var ve bitiş zamanı geçmemiş (kapalı kalacak şekilde).
    func grants(_ key: String, now: Date = Date()) -> Bool {
        guard active, features.contains(key), let end = expiryTime else { return false }
        return now < end
    }
}

/// Sunucu hatası (`{"error": "kod", "message": "..."}`).
struct SubscriptionServerError: Error, Equatable {
    let status: Int
    let code: String?
}

/// Abonelik uçları (Firebase kimlik jetonuyla). Android `RemoteStore`'un abonelik kısmı.
struct SubscriptionApi {
    /// Info.plist `DGApiBaseUrl` (yoksa üretim sunucusu).
    static var baseUrl: URL {
        (Bundle.main.object(forInfoDictionaryKey: "DGApiBaseUrl") as? String).flatMap { $0.isEmpty ? nil : URL(string: $0) }
            ?? URL(string: "https://drawapi.ahmetkar.com/")!
    }

    var session: URLSession = .shared

    func me(token: String) async throws -> ServerSubscription {
        try await send("GET", "api/v1/subscriptions/me", token: token, body: nil)
    }

    /// StoreKit 2 işleminin imzalı hali (`Transaction.jwsRepresentation` / `VerificationResult.jwsRepresentation`).
    func verifyApple(token: String, signedTransaction: String) async throws -> ServerSubscription {
        let body = try JSONSerialization.data(withJSONObject: ["signedTransaction": signedTransaction])
        return try await send("POST", "api/v1/subscriptions/verify-apple", token: token, body: body)
    }

    private func send(_ method: String, _ path: String, token: String, body: Data?) async throws -> ServerSubscription {
        var r = URLRequest(url: Self.baseUrl.appendingPathComponent(path), timeoutInterval: 20)
        r.httpMethod = method
        r.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        r.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body { r.httpBody = body; r.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        let (data, response) = try await session.data(for: r)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(code) else {
            let err = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            throw SubscriptionServerError(status: code, code: err?["error"] as? String)
        }
        return try JSONDecoder().decode(ServerSubscription.self, from: data)
    }
}
