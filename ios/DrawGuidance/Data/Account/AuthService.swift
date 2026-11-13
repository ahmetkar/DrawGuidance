import AuthenticationServices
import CryptoKit
import Foundation
import UIKit
#if canImport(FirebaseCore) && canImport(FirebaseAuth)
import FirebaseAuth
import FirebaseCore
#endif
#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

/// Giriş yöntemi (Android `SignInMethod`): ikisi de Firebase Authentication üzerinden.
enum SignInMethod: String, Codable { case google, apple }

/// Firebase hesabından alınan bilgiler (Android `GoogleAccount`).
struct AuthAccount: Equatable {
    var uid: String
    var displayName: String?
    var givenName: String?
    var familyName: String?
    var email: String?
    var emailVerified: Bool
    var photoUrl: String?
    /// Hesabın oluşturulma zamanı (ms).
    var accountCreatedAt: Int64?
    var method: SignInMethod
}

/// Giriş hatası (Android `AuthException.Reason`); `detail` Günlükler'de ve ekranda görünür.
struct AuthError: Error, Equatable {
    enum Reason { case cancelled, noAccount, notConfigured, network, failed }
    let reason: Reason
    var detail: String? = nil

    /// Kullanıcıya gösterilecek ileti (Android ile aynı anahtarlar).
    var messageKey: String {
        switch reason {
        case .cancelled: return "account_error_cancelled"
        case .noAccount: return "account_error_failed"
        case .notConfigured: return "account_error_not_configured"
        case .network: return "account_error_network"
        case .failed: return "account_error_failed"
        }
    }
}

/// Giriş sağlayıcısı (Android `AuthProvider`); uygulamada `FirebaseAuthService`, testlerde sahtesi.
@MainActor
protocol AuthProviding: AnyObject {
    /// Bu derlemede Firebase yapılandırılmış mı (GoogleService-Info.plist pakette mi).
    var available: Bool { get }
    var appleAvailable: Bool { get }
    var googleAvailable: Bool { get }
    func signIn(_ method: SignInMethod) async throws -> AuthAccount
    /// Hesap silmeden önce kimliği yeniden doğrular. Apple hesabında Apple'ın verdiği yetki kodu döner (Apple
    /// jetonlarını geri almak için; App Store kuralı 5.1.1(v)).
    func reauthenticate() async throws -> String?
    /// Firebase kullanıcısını siler; [appleAuthCode] varsa önce Apple jetonları geri alınır.
    func deleteUser(appleAuthCode: String?) async throws
    func current() -> AuthAccount?
    /// Sunucuya giden Firebase kimlik jetonu.
    func idToken(force: Bool) async -> String?
    func signOut()
}

/// Gerçek giriş: Google (GoogleSignIn → Firebase) ve Apple (AuthenticationServices → Firebase).
///
/// Firebase yalnız pakette `GoogleService-Info.plist` varsa açılır (Android'de google-services.json gibi); yoksa
/// giriş düğmeleri kapalı görünür. Google'ın geri dönüş adresi (REVERSED_CLIENT_ID) Info.plist'e derlemede
/// `DG_GOOGLE_URL_SCHEME` ayarıyla yazılır.
@MainActor
final class FirebaseAuthService: AuthProviding {
    static let shared = FirebaseAuthService()

    private init() {}

    /// Uygulama açılırken bir kez çağrılır.
    static func configure() {
        #if canImport(FirebaseCore) && canImport(FirebaseAuth)
        guard FirebaseApp.app() == nil, Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else { return }
        FirebaseApp.configure()
        #endif
    }

    /// Google girişinin geri dönüş adresi (`.onOpenURL`).
    static func handle(_ url: URL) -> Bool {
        #if canImport(GoogleSignIn)
        return GIDSignIn.sharedInstance.handle(url)
        #else
        return false
        #endif
    }

    var available: Bool {
        #if canImport(FirebaseCore) && canImport(FirebaseAuth)
        return FirebaseApp.app() != nil
        #else
        return false
        #endif
    }

    var appleAvailable: Bool { available }

    var googleAvailable: Bool {
        #if canImport(FirebaseCore) && canImport(GoogleSignIn)
        guard available, FirebaseApp.app()?.options.clientID?.isEmpty == false else { return false }
        // Geri dönüş adresi Info.plist'te yoksa Google sayfası uygulamaya dönemez
        let schemes = (Bundle.main.object(forInfoDictionaryKey: "CFBundleURLTypes") as? [[String: Any]] ?? [])
            .flatMap { $0["CFBundleURLSchemes"] as? [String] ?? [] }
        return schemes.contains { $0.hasPrefix("com.googleusercontent.apps.") }
        #else
        return false
        #endif
    }

    func signIn(_ method: SignInMethod) async throws -> AuthAccount {
        #if canImport(FirebaseCore) && canImport(FirebaseAuth)
        guard available else { throw AuthError(reason: .notConfigured) }
        switch method {
        case .google:
            let (credential, given, family, photo) = try await googleCredential()
            let user = try await firebaseSignIn(credential)
            return account(user, method: .google, given: given, family: family, photo: photo)
        case .apple:
            let apple = try await AppleAuthorizer().authorize()
            let user = try await firebaseSignIn(apple.credential)
            // Apple adı yalnız ilk girişte verir: Firebase profiline yazılır
            if (user.displayName ?? "").isEmpty, let name = apple.displayName {
                let change = user.createProfileChangeRequest()
                change.displayName = name
                try? await change.commitChanges()
            }
            return account(user, method: .apple, given: apple.given, family: apple.family, photo: nil)
        }
        #else
        throw AuthError(reason: .notConfigured)
        #endif
    }

    func reauthenticate() async throws -> String? {
        #if canImport(FirebaseCore) && canImport(FirebaseAuth)
        guard let user = Auth.auth().currentUser else { throw AuthError(reason: .failed, detail: "Oturum yok") }
        do {
            if Self.isApple(user) {
                let apple = try await AppleAuthorizer().authorize()
                try await user.reauthenticate(with: apple.credential)
                return apple.authorizationCode
            } else {
                let (credential, _, _, _) = try await googleCredential()
                try await user.reauthenticate(with: credential)
                return nil
            }
        } catch let e as AuthError {
            throw e
        } catch {
            throw Self.firebaseError(error)
        }
        #else
        throw AuthError(reason: .notConfigured)
        #endif
    }

    func deleteUser(appleAuthCode: String?) async throws {
        #if canImport(FirebaseCore) && canImport(FirebaseAuth)
        guard let user = Auth.auth().currentUser else { return }
        if let code = appleAuthCode {
            // Apple ile girişte hesap silinirken Apple jetonları da geri alınır
            try? await Auth.auth().revokeToken(withAuthorizationCode: code)
        }
        do { try await user.delete() } catch { throw Self.firebaseError(error) }
        signOut()
        #endif
    }

    func current() -> AuthAccount? {
        #if canImport(FirebaseCore) && canImport(FirebaseAuth)
        guard available, let user = Auth.auth().currentUser else { return nil }
        return account(user, method: Self.isApple(user) ? .apple : .google, given: nil, family: nil, photo: nil)
        #else
        return nil
        #endif
    }

    func idToken(force: Bool) async -> String? {
        #if canImport(FirebaseCore) && canImport(FirebaseAuth)
        guard available, let user = Auth.auth().currentUser else { return nil }
        return try? await user.getIDTokenResult(forcingRefresh: force).token
        #else
        return nil
        #endif
    }

    func signOut() {
        #if canImport(FirebaseCore) && canImport(FirebaseAuth)
        guard available else { return }
        try? Auth.auth().signOut()
        #endif
        #if canImport(GoogleSignIn)
        GIDSignIn.sharedInstance.signOut()
        #endif
    }

    // MARK: Firebase

    #if canImport(FirebaseCore) && canImport(FirebaseAuth)
    private static func isApple(_ user: User) -> Bool {
        let ids = user.providerData.map(\.providerID)
        return ids.contains("apple.com") && !ids.contains("google.com")
    }

    private func firebaseSignIn(_ credential: AuthCredential) async throws -> User {
        do {
            return try await Auth.auth().signIn(with: credential).user
        } catch {
            throw Self.firebaseError(error)
        }
    }

    private static func firebaseError(_ error: Error) -> AuthError {
        let ns = error as NSError
        guard ns.domain == AuthErrorDomain, let code = AuthErrorCode(rawValue: ns.code) else {
            return AuthError(reason: .failed, detail: error.localizedDescription)
        }
        switch code {
        case .networkError: return AuthError(reason: .network)
        case .webContextCancelled: return AuthError(reason: .cancelled)
        case .operationNotAllowed: return AuthError(reason: .notConfigured, detail: "Apple: Firebase'te Apple sağlayıcısı açık değil")
        case .accountExistsWithDifferentCredential, .credentialAlreadyInUse, .emailAlreadyInUse:
            return AuthError(reason: .failed, detail: "Bu e-posta adresi Google hesabıyla kayıtlı; Google ile giriş yapın")
        case .userDisabled, .userNotFound, .invalidCredential: return AuthError(reason: .noAccount, detail: "Firebase: \(ns.localizedDescription)")
        case .userMismatch: return AuthError(reason: .failed, detail: "Firebase: başka bir hesap seçildi")
        default: return AuthError(reason: .failed, detail: "Firebase: \(ns.localizedDescription)")
        }
    }

    private func account(_ user: User, method: SignInMethod, given: String?, family: String?, photo: String?) -> AuthAccount {
        let parts = (user.displayName ?? "").split(whereSeparator: \.isWhitespace).map(String.init)
        let g = parts.count > 1 ? parts.dropLast().joined(separator: " ") : parts.first
        let f = parts.count > 1 ? parts.last : nil
        return AuthAccount(
            uid: user.uid, displayName: user.displayName, givenName: given ?? g, familyName: family ?? f,
            email: user.email, emailVerified: user.isEmailVerified, photoUrl: user.photoURL?.absoluteString ?? photo,
            accountCreatedAt: user.metadata.creationDate.map { Int64($0.timeIntervalSince1970 * 1000) }, method: method
        )
    }

    /// Google hesap seçici → Google kimlik jetonu → Firebase kimliği.
    private func googleCredential() async throws -> (AuthCredential, String?, String?, String?) {
        #if canImport(GoogleSignIn)
        guard googleAvailable, let clientID = FirebaseApp.app()?.options.clientID else { throw AuthError(reason: .notConfigured) }
        guard let presenter = UIApplication.topViewController() else { throw AuthError(reason: .failed, detail: "Pencere yok") }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        let result: GIDSignInResult
        do {
            result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
        } catch {
            let ns = error as NSError
            if (error as? GIDSignInError)?.code == .canceled { throw AuthError(reason: .cancelled) }
            if ns.domain == NSURLErrorDomain { throw AuthError(reason: .network) }
            throw AuthError(reason: .failed, detail: "Google: \(ns.localizedDescription)")
        }
        guard let idToken = result.user.idToken?.tokenString else {
            throw AuthError(reason: .failed, detail: "Google: kimlik jetonu yok")
        }
        let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: result.user.accessToken.tokenString)
        let profile = result.user.profile
        return (credential, profile?.givenName, profile?.familyName, profile?.imageURL(withDimension: 256)?.absoluteString)
        #else
        throw AuthError(reason: .notConfigured)
        #endif
    }
    #endif
}

// MARK: Apple

/// Apple ile giriş penceresi (AuthenticationServices). Firebase'in istediği tek kullanımlık sayı (nonce) üretilir;
/// Apple'a SHA-256 özeti, Firebase'e kendisi verilir.
@MainActor
final class AppleAuthorizer: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    struct Result {
        let idToken: String
        let rawNonce: String
        let authorizationCode: String?
        let given: String?
        let family: String?
        var displayName: String? {
            let s = [given, family].compactMap { $0?.isEmpty == false ? $0 : nil }.joined(separator: " ")
            return s.isEmpty ? nil : s
        }
        #if canImport(FirebaseAuth)
        var credential: AuthCredential {
            OAuthProvider.appleCredential(withIDToken: idToken, rawNonce: rawNonce,
                                          fullName: given == nil && family == nil ? nil : {
                                              var c = PersonNameComponents(); c.givenName = given; c.familyName = family; return c
                                          }())
        }
        #endif
    }

    private var continuation: CheckedContinuation<Result, Error>?
    private var nonce = ""
    /// Pencere açıkken nesne yaşasın (temsilci zayıf tutulur).
    private var keepAlive: AppleAuthorizer?

    func authorize() async throws -> Result {
        nonce = Self.randomNonce()
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(nonce)
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        keepAlive = self
        defer { keepAlive = nil }
        return try await withCheckedThrowingContinuation { cont in
            continuation = cont
            controller.performRequests()
        }
    }

    nonisolated func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        MainActor.assumeIsolated { UIApplication.keyWindow() ?? ASPresentationAnchor() }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        MainActor.assumeIsolated {
            guard let cred = authorization.credential as? ASAuthorizationAppleIDCredential,
                  let data = cred.identityToken, let token = String(data: data, encoding: .utf8) else {
                finish(.failure(AuthError(reason: .failed, detail: "Apple: kimlik jetonu yok")))
                return
            }
            let code = cred.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
            finish(.success(Result(idToken: token, rawNonce: nonce, authorizationCode: code,
                                   given: cred.fullName?.givenName, family: cred.fullName?.familyName)))
        }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        MainActor.assumeIsolated {
            let code = (error as? ASAuthorizationError)?.code
            switch code {
            case .canceled: finish(.failure(AuthError(reason: .cancelled)))
            // Apple kimliği cihazda yoksa/yetki yoksa (ör. imzası Apple ile giriş yetkisi taşımayan derleme)
            case .unknown, .notHandled: finish(.failure(AuthError(reason: .failed, detail: "Apple: \(error.localizedDescription)")))
            default: finish(.failure(AuthError(reason: .failed, detail: "Apple: \(error.localizedDescription)")))
            }
        }
    }

    private func finish(_ result: Swift.Result<Result, Error>) {
        continuation?.resume(with: result)
        continuation = nil
    }

    static func randomNonce(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._")
        var bytes = [UInt8](repeating: 0, count: length)
        if SecRandomCopyBytes(kSecRandomDefault, length, &bytes) != errSecSuccess {
            return UUID().uuidString + UUID().uuidString
        }
        return String(bytes.map { charset[Int($0) % charset.count] })
    }

    static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

extension UIApplication {
    static func keyWindow() -> UIWindow? {
        shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first { $0.isKeyWindow }
    }

    /// En üstte görünen ekran (Google hesap seçicisini sunmak için).
    static func topViewController() -> UIViewController? {
        var top = keyWindow()?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
