package com.karapp.drawguidance

import android.app.Activity
import com.karapp.drawguidance.data.account.AuthException
import com.karapp.drawguidance.data.account.AuthProvider
import com.karapp.drawguidance.data.account.GoogleAccount
import com.karapp.drawguidance.data.sync.FixedConnectivity

/** v33 testlerde bağlantı ve Google girişi (sahte; her test başında [reset]). */
object TestAccount {
    val connectivity = FixedConnectivity(true)
    val auth = FakeAuth()
    /** v39 testlerde haklar elle verilir: eski testler Premium (her şey açık); null gerçek abonelik hesabı. */
    val entitlements = kotlinx.coroutines.flow.MutableStateFlow<com.karapp.drawguidance.data.billing.Entitlements?>(com.karapp.drawguidance.data.billing.Entitlements.premium())

    /** v43 giriş yapılmış yerel kullanıcı (Ayarlar'daki abonelik satırı yalnız girişte görünür). */
    fun signInLocal() = kotlinx.coroutines.runBlocking {
        auth.signedIn = FakeAuth.DEFAULT
        org.koin.core.context.GlobalContext.get().get<com.karapp.drawguidance.data.local.LocalUserDao>().upsert(
            com.karapp.drawguidance.data.local.LocalUserEntity("u1", FakeAuth.DEFAULT.firebaseUid, "Ahmet Kar", "Ahmet", "Kar", "ahmet@example.com", true, null, null, null, true, 1, 1),
        )
    }

    fun reset() {
        entitlements.value = com.karapp.drawguidance.data.billing.Entitlements.premium()
        connectivity.set(true)
        auth.account = FakeAuth.DEFAULT
        auth.signedIn = null
        auth.token = "fake-token"
        auth.failWith = null
        auth.signIns = 0
        auth.reauths = 0
        auth.deletes = 0
        auth.reauthFailWith = null
        auth.deleteFails = false
    }
}

/** Sahte Google girişi: [account] ile giriş yapılır; jeton [token]. */
class FakeAuth : AuthProvider {
    companion object {
        val DEFAULT = GoogleAccount(
            firebaseUid = "uid-ahmet", displayName = "Ahmet Kar", givenName = "Ahmet", familyName = "Kar",
            email = "ahmet@example.com", emailVerified = true, photoUrl = null, accountCreatedAt = 1_700_000_000_000L,
        )
    }

    @Volatile var account: GoogleAccount = DEFAULT
    @Volatile var signedIn: GoogleAccount? = null
    @Volatile var token: String? = "fake-token"
    @Volatile var failWith: AuthException.Reason? = null
    @Volatile var signIns = 0

    override val available: Boolean = true

    /** v34 hesap silme: yeniden doğrulama ve Firebase silme sayıları; hata anahtarları. */
    @Volatile var reauths = 0
    @Volatile var deletes = 0
    @Volatile var reauthFailWith: AuthException.Reason? = null
    @Volatile var deleteFails = false

    override suspend fun signIn(activity: Activity): GoogleAccount {
        failWith?.let { throw AuthException(it) }
        signIns++
        signedIn = account
        return account
    }

    override suspend fun reauthenticate(activity: Activity) {
        reauthFailWith?.let { throw AuthException(it) }
        if (signedIn == null) throw AuthException(AuthException.Reason.FAILED, "Oturum yok")
        reauths++
    }

    override suspend fun deleteUser() {
        if (deleteFails) throw AuthException(AuthException.Reason.NETWORK)
        deletes++
        signedIn = null
    }

    override fun current(): GoogleAccount? = signedIn

    override suspend fun idToken(force: Boolean): String? = if (signedIn != null) token else null

    override suspend fun reload(): GoogleAccount? = signedIn?.let { account.also { a -> signedIn = a } }

    override suspend fun signOut() { signedIn = null }
}
