import XCTest
@testable import DrawGuidance

/// Sahte giriş: Google ya da Apple ile her zaman [account] döner; sunucu jetonu yok (sunucuya yazılmaz).
@MainActor
private final class FakeAuth: AuthProviding {
    var available = true
    var appleAvailable = true
    var googleAvailable = true
    var failWith: AuthError?
    var signedIn: AuthAccount?
    var lastMethod: SignInMethod?
    var deletedWithCode: String??
    var reauthCode: String? = nil

    func signIn(_ method: SignInMethod) async throws -> AuthAccount {
        if let failWith { throw failWith }
        lastMethod = method
        let a = AuthAccount(uid: "uid-\(method.rawValue)", displayName: "Ahmet Kar", givenName: "Ahmet", familyName: "Kar",
                            email: method == .apple ? "x@privaterelay.appleid.com" : "ahmet@example.com",
                            emailVerified: true, photoUrl: nil, accountCreatedAt: 1, method: method)
        signedIn = a
        return a
    }
    func reauthenticate() async throws -> String? {
        if let failWith { throw failWith }
        return reauthCode
    }
    func deleteUser(appleAuthCode: String?) async throws { deletedWithCode = .some(appleAuthCode); signedIn = nil }
    func current() -> AuthAccount? { signedIn }
    func idToken(force: Bool) async -> String? { nil }
    func signOut() { signedIn = nil }
}

@MainActor
final class AccountStoreTests: XCTestCase {
    private func store(_ auth: FakeAuth) -> AccountStore {
        let d = UserDefaults(suiteName: "account-test-\(UUID().uuidString)")!
        return AccountStore(auth: auth, defaults: d)
    }

    func testAppleSignInAsksBirthDateThenSavesUser() async {
        let auth = FakeAuth()
        let s = store(auth)
        await s.signIn(.apple)
        XCTAssertEqual(auth.lastMethod, .apple)
        XCTAssertNil(s.user)
        XCTAssertEqual(s.pendingBirth?.uid, "uid-apple")
        await s.completeBirth(Calendar.current.date(from: DateComponents(year: 1990, month: 1, day: 1, hour: 12))!)
        XCTAssertNil(s.pendingBirth)
        XCTAssertEqual(s.user?.method, .apple)
        XCTAssertEqual(s.user?.birthDate, "1990-01-01")
        XCTAssertEqual(s.user?.termsVersion, AccountStore.TERMS_VERSION)
        XCTAssertEqual(s.message, "account_signed_in")
    }

    func testGoogleSignInCancelShowsMessageAndKeepsSignedOut() async {
        let auth = FakeAuth()
        auth.failWith = AuthError(reason: .cancelled)
        let s = store(auth)
        await s.signIn(.google)
        XCTAssertNil(s.user)
        XCTAssertNil(s.pendingBirth)
        XCTAssertEqual(s.message, "account_error_cancelled")
        XCTAssertEqual(s.lastMethod, .google)
    }

    func testCancelBirthSignsOut() async {
        let auth = FakeAuth()
        let s = store(auth)
        await s.signIn(.google)
        s.cancelBirth()
        XCTAssertNil(s.user)
        XCTAssertNil(auth.signedIn)
    }

    func testSignOutClearsUser() async {
        let auth = FakeAuth()
        let s = store(auth)
        await s.signIn(.google)
        await s.completeBirth(Date(timeIntervalSince1970: 0))
        XCTAssertNotNil(s.user)
        s.signOut()
        XCTAssertNil(s.user)
        XCTAssertNil(auth.signedIn)
    }

    func testFutureBirthDateIsRejected() async {
        let auth = FakeAuth()
        let s = store(auth)
        await s.signIn(.google)
        await s.completeBirth(Date().addingTimeInterval(86_400 * 3))
        XCTAssertNil(s.user)
        XCTAssertEqual(s.message, "birth_date_invalid")
    }

    func testAppleNonceIsHashed() {
        let n = AppleAuthorizer.randomNonce()
        XCTAssertEqual(n.count, 32)
        XCTAssertEqual(AppleAuthorizer.sha256("abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }
}
