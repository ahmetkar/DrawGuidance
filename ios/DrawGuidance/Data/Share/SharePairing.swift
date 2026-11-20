import CryptoKit
import Foundation
import Security

/// v54 Wi-Fi paylaşımı ve canlı yayın için eşleşme şifreli bağlantı (Android `SharePairing` ile bayt düzeyinde aynı).
///
/// 1. Bağlanan (A) tanışmada geçici P-256 açık anahtarını gönderir; kabul eden (B) kendi anahtarıyla yanıtlar.
/// 2. Ortak sır Z = ECDH. Eşleşme sırrı S: ilk eşleşmede B'nin ekranında görünen 6 haneli şifre (A'da girilir), daha önce
///    eşleşmiş cihazlarda saklanan güven anahtarı. Tanışma çerçevelerinin özeti T.
/// 3. PRK = HMAC-SHA256(SHA-256("dgshare-v2-pair" ‖ S), Z ‖ T); anahtarlar PRK'dan etiketle türetilir.
/// 4. Önce A doğrulama kodunu gönderir; B doğrularsa kendi kodunu gönderir. Yanlış şifrede bağlantı kapanır.
/// 5. Sonraki her çerçeve AES-256-GCM: yöne göre ayrı anahtar, nonce = 4 sıfır bayt ‖ 8 bayt sayaç.
enum SharePairing {
    static let VERSION = 2
    static let CODE_DIGITS = 6

    struct Keys {
        let aToB: Data
        let bToA: Data
        let confirm: Data
        let trust: Data
    }

    static func newKey() -> P256.KeyAgreement.PrivateKey { P256.KeyAgreement.PrivateKey() }

    /// Sıkıştırılmamış X9.63 açık anahtar (65 bayt).
    static func publicRaw(_ key: P256.KeyAgreement.PrivateKey) -> Data { key.publicKey.x963Representation }

    /// ECDH ortak sırrı (X koordinatı, 32 bayt).
    static func sharedSecret(_ key: P256.KeyAgreement.PrivateKey, _ peerRaw: Data) throws -> Data {
        let peer = try P256.KeyAgreement.PublicKey(x963Representation: peerRaw)
        let secret = try key.sharedSecretFromKeyAgreement(with: peer)
        return secret.withUnsafeBytes { Data($0) }
    }

    /// Ekranda gösterilecek 6 haneli şifre.
    static func newCode() -> String {
        var g = SecureRandomGenerator()
        return (0..<CODE_DIGITS).map { _ in String(Int.random(in: 0...9, using: &g)) }.joined()
    }

    static func normalizeCode(_ code: String) -> String { code.filter { $0.isASCII && $0.isNumber } }

    static func transcript(_ request: Data, _ reply: Data) -> Data { sha256(request + reply) }

    static func derive(z: Data, secret: Data, transcript: Data) -> Keys {
        let salt = sha256(Data("dgshare-v2-pair".utf8) + secret)
        let prk = hmac(salt, z + transcript)
        func expand(_ label: String) -> Data { hmac(prk, Data(label.utf8) + Data([1])) }
        return Keys(aToB: expand("a->b"), bToA: expand("b->a"), confirm: expand("confirm"), trust: expand("trust"))
    }

    static func confirmTag(_ keys: Keys, _ role: String) -> Data { hmac(keys.confirm, Data(role.utf8)) }

    /// Sabit süreli karşılaştırma.
    static func same(_ a: Data, _ b: Data) -> Bool {
        guard a.count == b.count else { return false }
        var d: UInt8 = 0
        for i in 0..<a.count { d |= a[a.startIndex + i] ^ b[b.startIndex + i] }
        return d == 0
    }

    /// Tek yönde şifreleyici (sayaç her çerçevede artar).
    final class Sealer {
        private let key: SymmetricKey
        private var counter: UInt64 = 0
        private let lock = NSLock()
        init(_ key: Data) { self.key = SymmetricKey(data: key) }
        func seal(_ plain: Data) throws -> Data {
            lock.lock(); defer { lock.unlock() }
            let box = try AES.GCM.seal(plain, using: key, nonce: try nonce(counter))
            counter += 1
            return box.ciphertext + box.tag
        }
    }

    /// Tek yönde çözücü: çerçeveler sırayla gelir; bozuk çerçeve hata verir.
    final class Opener {
        private let key: SymmetricKey
        private var counter: UInt64 = 0
        private let lock = NSLock()
        init(_ key: Data) { self.key = SymmetricKey(data: key) }
        func open(_ sealed: Data) throws -> Data {
            lock.lock(); defer { lock.unlock() }
            guard sealed.count >= 16 else { throw CryptoKitError.incorrectParameterSize }
            let box = try AES.GCM.SealedBox(nonce: try nonce(counter), ciphertext: sealed.dropLast(16), tag: sealed.suffix(16))
            counter += 1
            return try AES.GCM.open(box, using: key)
        }
    }

    private static func nonce(_ counter: UInt64) throws -> AES.GCM.Nonce {
        var n = Data(count: 4)
        withUnsafeBytes(of: counter.bigEndian) { n.append(contentsOf: $0) }
        return try AES.GCM.Nonce(data: n)
    }

    static func sha256(_ d: Data) -> Data { Data(SHA256.hash(data: d)) }
    static func hmac(_ key: Data, _ data: Data) -> Data { Data(HMAC<SHA256>.authenticationCode(for: data, using: SymmetricKey(data: key))) }
}

/// Sistem güvenli rastgele sayı üreteci (SecRandomCopyBytes).
struct SecureRandomGenerator: RandomNumberGenerator {
    mutating func next() -> UInt64 {
        var v: UInt64 = 0
        _ = withUnsafeMutableBytes(of: &v) { SecRandomCopyBytes(kSecRandomDefault, 8, $0.baseAddress!) }
        return v
    }
}

/// v54 eşleşmiş cihazların güven anahtarları (Anahtar Zinciri; cihaz kimliği → anahtar).
enum ShareTrustStore {
    private static let service = "drawguidance.share.trust"

    static func get(_ id: String) -> Data? {
        let q: [CFString: Any] = [kSecClass: kSecClassGenericPassword, kSecAttrService: service, kSecAttrAccount: id,
                                  kSecReturnData: true, kSecMatchLimit: kSecMatchLimitOne]
        var out: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess else { return nil }
        return out as? Data
    }

    static func put(_ id: String, _ key: Data) {
        remove(id)
        let q: [CFString: Any] = [kSecClass: kSecClassGenericPassword, kSecAttrService: service, kSecAttrAccount: id, kSecValueData: key,
                                  kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        SecItemAdd(q as CFDictionary, nil)
    }

    static func remove(_ id: String) {
        let q: [CFString: Any] = [kSecClass: kSecClassGenericPassword, kSecAttrService: service, kSecAttrAccount: id]
        SecItemDelete(q as CFDictionary)
    }
}

/// v56 eşleşme şifresi kapısı (Android `PairingCodeGate`): şifre yalnız kullanıcı isteği onaylayınca üretilir, daha önce
/// eşleşmiş cihazda hiç üretilmez (onay yine sorulur).
final class PairingCodeGate {
    let known: Bool
    private let generate: () -> String
    private(set) var code: String?
    private(set) var approved = false

    init(known: Bool, generate: @escaping () -> String = SharePairing.newCode) {
        self.known = known
        self.generate = generate
    }

    @discardableResult
    func approve() -> String? {
        if !approved { approved = true; if !known { code = generate() } }
        return code
    }
}

/// v56 canlı yayın onay kuralları (Android `LiveApprovalPolicy`): yayın teklifi her zaman sorulur; 60 sn'de yanıt yoksa
/// zaman aşımı; aynı cihaz 1 dakikada 3 kez reddedilirse 5 dakika sorulmadan geri çevrilir; bağlanırken onaylanan teklif
/// bağlantıdan sonra (3 dk içinde) yeniden sorulmaz.
final class LiveApprovalPolicy {
    static let ANSWER_TIMEOUT_MS: Int64 = 60_000
    static let WINDOW_MS: Int64 = 60_000
    static let MAX_REJECTIONS = 3
    static let BLOCK_MS: Int64 = 5 * 60_000
    static let APPROVAL_VALID_MS: Int64 = 3 * 60_000

    private let clock: () -> Int64
    private var rejections: [String: [Int64]] = [:]
    private var blockedUntil: [String: Int64] = [:]
    private var approvedAt: [String: Int64] = [:]
    private let lock = NSLock()

    init(clock: @escaping () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }) { self.clock = clock }

    func isBlocked(_ peer: String) -> Bool { lock.lock(); defer { lock.unlock() }; return (blockedUntil[peer] ?? 0) > clock() }

    func onRejected(_ peer: String) {
        lock.lock(); defer { lock.unlock() }
        let now = clock()
        var q = (rejections[peer] ?? []) + [now]
        q.removeAll { now - $0 > Self.WINDOW_MS }
        if q.count >= Self.MAX_REJECTIONS { blockedUntil[peer] = now + Self.BLOCK_MS; q.removeAll() }
        rejections[peer] = q
    }

    func onApproved(_ peer: String) { lock.lock(); defer { lock.unlock() }; approvedAt[peer] = clock() }

    func consumeApproval(_ peer: String) -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard let at = approvedAt.removeValue(forKey: peer) else { return false }
        return clock() - at <= Self.APPROVAL_VALID_MS
    }
}
