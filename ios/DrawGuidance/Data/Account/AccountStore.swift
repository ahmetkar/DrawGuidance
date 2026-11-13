import Foundation
import SwiftUI

/// Cihazdaki kullanıcı kaydı (Android `LocalUserEntity`'nin giriş için gereken kısmı).
struct LocalUser: Codable, Equatable {
    var id: String
    var firebaseUid: String
    var displayName: String?
    var givenName: String?
    var familyName: String?
    var email: String?
    var emailVerified: Bool
    var photoUrl: String?
    var accountCreatedAt: Int64?
    var birthDate: String
    var method: SignInMethod
    var termsAcceptedAt: Int64?
    var termsVersion: String?
    /// Sunucuya yazıldı mı (yazılamadıysa sonraki açılışta yeniden denenir).
    var synced: Bool = false

    var name: String {
        let n = [givenName, familyName].compactMap { $0?.isEmpty == false ? $0 : nil }.joined(separator: " ")
        if !n.isEmpty { return n }
        if let d = displayName, !d.isEmpty { return d }
        return email ?? ""
    }
}

/// Sunucunun kullanıcı uç noktaları (`/api/v1/users/me`, Firebase kimlik jetonuyla). Android `RemoteStore` karşılığı.
struct UserApi {
    static let baseUrl = URL(string: "https://drawapi.ahmetkar.com/")!

    struct RemoteUser: Decodable {
        let id: String
        let firebaseUid: String
        let birthDate: String?
        let termsAcceptedAt: Int64?
        let termsVersion: String?
    }

    struct Body: Encodable {
        let id: String
        let firebaseUid: String
        let displayName: String?
        let givenName: String?
        let familyName: String?
        let email: String?
        let emailVerified: Bool
        let photoUrl: String?
        let accountCreatedAt: Int64?
        let birthDate: String?
        let clientUpdatedAt: Int64
        let termsAcceptedAt: Int64?
        let termsVersion: String?
    }

    enum ApiError: Error { case status(Int), offline }

    var session: URLSession = .shared

    private func request(_ method: String, token: String, body: Data? = nil) -> URLRequest {
        var r = URLRequest(url: Self.baseUrl.appendingPathComponent("api/v1/users/me"), timeoutInterval: 15)
        r.httpMethod = method
        r.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        r.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body { r.httpBody = body; r.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        return r
    }

    private func send(_ r: URLRequest) async throws -> (Data, Int) {
        do {
            let (data, response) = try await session.data(for: r)
            return (data, (response as? HTTPURLResponse)?.statusCode ?? 0)
        } catch {
            throw ApiError.offline
        }
    }

    /// Kayıt yoksa `nil` (404).
    func me(token: String) async throws -> RemoteUser? {
        let (data, code) = try await send(request("GET", token: token))
        if code == 404 { return nil }
        guard (200..<300).contains(code) else { throw ApiError.status(code) }
        return try JSONDecoder().decode(RemoteUser.self, from: data)
    }

    func put(_ u: LocalUser, token: String) async throws -> RemoteUser {
        let body = Body(id: u.id, firebaseUid: u.firebaseUid, displayName: u.displayName, givenName: u.givenName,
                        familyName: u.familyName, email: u.email, emailVerified: u.emailVerified, photoUrl: u.photoUrl,
                        accountCreatedAt: u.accountCreatedAt, birthDate: u.birthDate,
                        clientUpdatedAt: Int64(Date().timeIntervalSince1970 * 1000),
                        termsAcceptedAt: u.termsAcceptedAt, termsVersion: u.termsVersion)
        let (data, code) = try await send(request("PUT", token: token, body: try JSONEncoder().encode(body)))
        guard (200..<300).contains(code) else { throw ApiError.status(code) }
        return try JSONDecoder().decode(RemoteUser.self, from: data)
    }

    func delete(token: String) async throws {
        let (_, code) = try await send(request("DELETE", token: token))
        guard (200..<300).contains(code) || code == 404 else { throw ApiError.status(code) }
    }
}

/// Hesap durumu (Android `AccountRepository` + `AccountViewModel`): Google ya da Apple ile giriş, ilk girişte doğum
/// tarihi ve kullanım şartları onayı, sunucuya kayıt, çıkış ve hesap silme.
@MainActor
final class AccountStore: ObservableObject {
    static let shared = AccountStore(auth: FirebaseAuthService.shared)
    /// Android `LegalTexts.VERSION` ile aynı.
    static let TERMS_VERSION = "2026-10-25"
    private static let KEY = "account.user"

    @Published private(set) var user: LocalUser?
    @Published private(set) var busy = false
    /// Son iletinin metin anahtarı ve ayrıntısı.
    @Published var message: String?
    @Published var detail: String?
    /// İlk girişte doğum tarihi bekleyen hesap.
    @Published var pendingBirth: AuthAccount?
    @Published private(set) var lastMethod: SignInMethod = .google

    let auth: AuthProviding
    var api = UserApi()
    private let defaults: UserDefaults

    init(auth: AuthProviding, defaults: UserDefaults = .standard) {
        self.auth = auth
        self.defaults = defaults
        if let data = defaults.data(forKey: Self.KEY), let u = try? JSONDecoder().decode(LocalUser.self, from: data) {
            user = u
        }
    }

    var available: Bool { auth.available }

    private func store(_ u: LocalUser?) {
        user = u
        if let u, let data = try? JSONEncoder().encode(u) { defaults.set(data, forKey: Self.KEY) } else { defaults.removeObject(forKey: Self.KEY) }
    }

    /// Açılışta: Firebase oturumu kapandıysa cihazdaki kayıt da kapanır; sunucuya yazılmamışsa yeniden denenir.
    func restore() async {
        guard let u = user else { return }
        if auth.available, auth.current()?.uid != u.firebaseUid { store(nil); return }
        if !u.synced { await push() }
    }

    func signIn(_ method: SignInMethod) async {
        if busy { return }
        busy = true; message = nil; detail = nil; lastMethod = method
        defer { busy = false }
        do {
            let account = try await auth.signIn(method)
            let remote = await fetchRemote()
            let previous = user?.firebaseUid == account.uid ? user : nil
            if let birth = previous?.birthDate ?? remote?.birthDate {
                save(account, birth: birth, id: remote?.id ?? previous?.id, terms: nil, remote: remote)
                message = "account_signed_in"
                await push()
            } else {
                pendingBirth = account
            }
        } catch let e as AuthError {
            message = e.messageKey; detail = e.detail
        } catch {
            message = "account_error_failed"; detail = error.localizedDescription
        }
    }

    /// İlk girişte doğum tarihi ve kullanım şartları onayıyla girişi tamamlar.
    func completeBirth(_ date: Date) async {
        guard let account = pendingBirth else { return }
        guard date <= Date() else { message = "birth_date_invalid"; return }
        pendingBirth = nil
        save(account, birth: Self.isoDate(date), id: nil, terms: Self.TERMS_VERSION, remote: nil)
        message = "account_signed_in"
        await push()
    }

    /// Doğum tarihi verilmezse giriş tamamlanmaz (oturum kapatılır).
    func cancelBirth() {
        pendingBirth = nil
        auth.signOut()
    }

    func signOut() {
        auth.signOut()
        store(nil)
        message = "account_signed_out"; detail = nil
    }

    /// Hesap silme: kimlik yeniden doğrulanır, sunucudaki kayıt silinir, Firebase kullanıcısı (Apple'da Apple jetonları
    /// geri alınarak) silinir, cihazdaki kayıt kalkar. Sunucuya ulaşılamazsa hiçbir şey silinmez.
    func deleteAccount() async -> Bool {
        guard user != nil, !busy else { return false }
        busy = true; message = nil; detail = nil
        defer { busy = false }
        let appleCode: String?
        do {
            appleCode = try await auth.reauthenticate()
        } catch let e as AuthError {
            message = e.reason == .cancelled ? "account_delete_cancelled" : e.messageKey; detail = e.detail
            return false
        } catch {
            message = "account_delete_cancelled"; return false
        }
        guard let token = await auth.idToken(force: true) else { message = "account_delete_server_failed"; return false }
        do {
            try await api.delete(token: token)
        } catch {
            message = "account_delete_server_failed"; return false
        }
        do {
            try await auth.deleteUser(appleAuthCode: appleCode)
            message = "account_deleted"
        } catch {
            auth.signOut()
            message = "account_delete_partial"
        }
        store(nil)
        return true
    }

    // MARK: İç

    private func fetchRemote() async -> UserApi.RemoteUser? {
        guard let token = await auth.idToken(force: false) else { return nil }
        return try? await api.me(token: token)
    }

    private func save(_ a: AuthAccount, birth: String, id: String?, terms: String?, remote: UserApi.RemoteUser?) {
        let previous = user?.firebaseUid == a.uid ? user : nil
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        store(LocalUser(
            id: id ?? UUID().uuidString.lowercased(), firebaseUid: a.uid,
            displayName: a.displayName ?? previous?.displayName,
            givenName: a.givenName ?? previous?.givenName, familyName: a.familyName ?? previous?.familyName,
            email: a.email, emailVerified: a.emailVerified, photoUrl: a.photoUrl, accountCreatedAt: a.accountCreatedAt,
            birthDate: birth, method: a.method,
            termsAcceptedAt: terms != nil ? now : (previous?.termsAcceptedAt ?? remote?.termsAcceptedAt),
            termsVersion: terms ?? previous?.termsVersion ?? remote?.termsVersion,
            synced: false
        ))
    }

    /// Cihazdaki kaydı sunucuya yazar (başarısızsa sonraki açılışta yeniden denenir).
    func push() async {
        guard var u = user, let token = await auth.idToken(force: false) else { return }
        guard let remote = try? await api.put(u, token: token) else { return }
        u.id = remote.id
        u.synced = true
        if user?.firebaseUid == u.firebaseUid { store(u) }
    }

    static func isoDate(_ d: Date) -> String {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: d)
    }
}
