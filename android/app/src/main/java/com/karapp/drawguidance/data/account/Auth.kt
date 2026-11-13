package com.karapp.drawguidance.data.account

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** v33 Google hesabından (Firebase Authentication) alınan bilgiler. */
data class GoogleAccount(
    val firebaseUid: String,
    val displayName: String?,
    val givenName: String?,
    val familyName: String?,
    val email: String?,
    val emailVerified: Boolean,
    val photoUrl: String?,
    /** Hesabın oluşturulma zamanı (ms). */
    val accountCreatedAt: Long?,
)

/** Giriş yöntemi: Google hesabı ya da Apple kimliği (ikisi de Firebase Authentication üzerinden). */
enum class SignInMethod { GOOGLE, APPLE }

/** Giriş hatası: [reason] kullanıcıya gösterilecek iletiyi seçer. */
class AuthException(val reason: Reason, message: String? = null) : Exception(message ?: reason.name) {
    enum class Reason { CANCELLED, NO_ACCOUNT, NOT_CONFIGURED, NETWORK, FAILED }
}

/**
 * v33 Google ile giriş sağlayıcısı. Uygulamada [FirebaseGoogleAuth] (Credential Manager → Google kimlik jetonu →
 * Firebase), testlerde sahte sağlayıcı kullanılır.
 */
interface AuthProvider {
    /** Bu derlemede giriş yapılandırılmış mı (google-services.json ile derlendiyse). */
    val available: Boolean

    /** Apple ile giriş gösterilsin mi (Firebase'te Apple sağlayıcısı açıldıysa derlemede `dg.appleSignIn=true`). */
    val appleAvailable: Boolean get() = false

    /**
     * Giriş: Google'da hesap seçici açılır (v34: hesabı değiştir kaldırıldı); Apple'da Apple'ın giriş sayfası (Chrome
     * Özel Sekmesi) açılır.
     */
    suspend fun signIn(activity: Activity, method: SignInMethod = SignInMethod.GOOGLE): GoogleAccount

    /**
     * v34 hesap silmeden önce kimliğin yeniden doğrulanması (Firebase, yakın zamanda giriş yapılmış olmasını ister).
     * Seçilen Google hesabı açık oturumun hesabı değilse [AuthException] (FAILED).
     */
    suspend fun reauthenticate(activity: Activity)

    /** v34 Firebase kullanıcısını siler ve kayıtlı kimlik durumunu temizler. */
    suspend fun deleteUser()

    /** Açık oturumun hesabı (Firebase oturumu yoksa null). */
    fun current(): GoogleAccount?

    /** Sunucuya giden Firebase kimlik jetonu ([force]: süresi dolmadan yenilenir). */
    suspend fun idToken(force: Boolean = false): String?

    /** Hesap bilgilerini Google'dan yeniler. */
    suspend fun reload(): GoogleAccount?

    suspend fun signOut()
}

/** Görünen addan ad ve soyad (son kelime soyad). */
internal fun splitName(display: String?): Pair<String?, String?> {
    val parts = display?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()
    return when (parts.size) {
        0 -> null to null
        1 -> parts[0] to null
        else -> parts.dropLast(1).joinToString(" ") to parts.last()
    }
}

/**
 * v33 gerçek giriş: Credential Manager ile Google hesabı seçilir, Google kimlik jetonu Firebase'e verilir
 * (`signInWithCredential`); oturum Firebase'de kalıcıdır (bir kez girilince açık kalır). Sunucu istemci kimliği
 * google-services eklentisinin ürettiği `default_web_client_id` kaynağındadır (yoksa giriş kapalıdır).
 */
class FirebaseGoogleAuth(private val context: Context) : AuthProvider {

    /** v35 Credential Manager bu oturumda hesap seçiminden sonra iptal döndüyse hep eski Google Sign-In kullanılır. */
    @Volatile
    private var preferLegacy = false

    private fun webClientId(): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id != 0) context.getString(id).takeIf { it.isNotBlank() } else null
    }

    private fun auth(): FirebaseAuth? = runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
        FirebaseAuth.getInstance()
    }.getOrNull()

    override val available: Boolean get() = webClientId() != null && auth() != null

    override val appleAvailable: Boolean get() = com.karapp.drawguidance.BuildConfig.APPLE_SIGN_IN && available

    override suspend fun signIn(activity: Activity, method: SignInMethod): GoogleAccount {
        if (method == SignInMethod.APPLE) return appleSignIn(activity)
        val clientId = webClientId() ?: throw AuthException(AuthException.Reason.NOT_CONFIGURED)
        val firebase = auth() ?: throw AuthException(AuthException.Reason.NOT_CONFIGURED)
        val google = googleToken(activity, clientId, reauth = false)
        val user = try {
            firebase.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await().user
        } catch (e: Exception) {
            com.karapp.drawguidance.diagnostics.AppLog.w(TAG, "Firebase girişi başarısız", e)
            // v40 hesap devre dışı/silinmiş ya da kimlik reddedildi: "hesap sorunu" penceresi
            val reason = when {
                isNetwork(e) -> AuthException.Reason.NETWORK
                isAccountProblem(e) -> AuthException.Reason.NO_ACCOUNT
                else -> AuthException.Reason.FAILED
            }
            throw AuthException(reason, "Firebase: ${e.message}")
        } ?: throw AuthException(AuthException.Reason.FAILED)
        return account(user, google.givenName, google.familyName, google.photoUrl)
    }

    override suspend fun reauthenticate(activity: Activity) {
        val clientId = webClientId() ?: throw AuthException(AuthException.Reason.NOT_CONFIGURED)
        val user = auth()?.currentUser ?: throw AuthException(AuthException.Reason.FAILED, "Oturum yok")
        if (isAppleUser(user)) {
            try {
                user.startActivityForReauthenticateWithProvider(activity, appleProvider()).await()
            } catch (e: Exception) {
                throw appleError(e)
            }
            return
        }
        val google = googleToken(activity, clientId, reauth = true)
        try {
            user.reauthenticate(GoogleAuthProvider.getCredential(google.idToken, null)).await()
        } catch (e: Exception) {
            // Başka bir hesap seçildiyse Firebase kullanıcı uyuşmazlığı bildirir
            throw AuthException(if (isNetwork(e)) AuthException.Reason.NETWORK else AuthException.Reason.FAILED, "Firebase: ${e.message}")
        }
    }

    override suspend fun deleteUser() {
        val user = auth()?.currentUser ?: return
        try {
            user.delete().await()
        } catch (e: Exception) {
            throw AuthException(if (isNetwork(e)) AuthException.Reason.NETWORK else AuthException.Reason.FAILED, e.message)
        }
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /**
     * Apple ile giriş: Firebase, Apple'ın giriş sayfasını Chrome Özel Sekmesi'nde açar (Android'de Apple'ın yerel
     * penceresi yok). Apple adı yalnız ilk girişte verir; e-postayı gizleyen kullanıcıda e-posta
     * `…@privaterelay.appleid.com` olur. Aynı e-posta Google hesabıyla kayıtlıysa Firebase çakışma bildirir: kullanıcı
     * Google ile girmelidir (hesaplar bu sürümde birleştirilmez).
     */
    private suspend fun appleSignIn(activity: Activity): GoogleAccount {
        val firebase = auth() ?: throw AuthException(AuthException.Reason.NOT_CONFIGURED)
        if (!com.karapp.drawguidance.BuildConfig.APPLE_SIGN_IN) throw AuthException(AuthException.Reason.NOT_CONFIGURED)
        val result = try {
            // Etkinlik giriş sırasında yeniden oluşturulduysa sonuç bekleyen işlemdedir
            firebase.pendingAuthResult?.await() ?: firebase.startActivityForSignInWithProvider(activity, appleProvider()).await()
        } catch (e: Exception) {
            com.karapp.drawguidance.diagnostics.AppLog.w(TAG, "Apple ile giriş başarısız", e)
            throw appleError(e)
        }
        val user = result.user ?: throw AuthException(AuthException.Reason.FAILED)
        // İlk girişte Apple'ın verdiği ad Firebase profiline yazılır (sonraki girişlerde Apple adı göndermez)
        val profile = result.additionalUserInfo?.profile
        val appleName = (profile?.get("name") as? Map<*, *>)
        val given = appleName?.get("firstName") as? String
        val family = appleName?.get("lastName") as? String
        if (user.displayName.isNullOrBlank() && (given != null || family != null)) {
            val display = listOfNotNull(given, family).joinToString(" ")
            runCatching { user.updateProfile(com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(display).build()).await() }
        }
        return account(user, given, family, null)
    }

    private fun appleProvider(): OAuthProvider = OAuthProvider.newBuilder("apple.com")
        .setScopes(listOf("email", "name"))
        .addCustomParameter("locale", java.util.Locale.getDefault().toLanguageTag().replace('-', '_'))
        .build()

    private fun isAppleUser(user: FirebaseUser): Boolean {
        val ids = user.providerData.map { it.providerId }
        return "apple.com" in ids && "google.com" !in ids
    }

    /** Firebase hata kodundan giriş hatası (Apple akışı). */
    private fun appleError(e: Exception): AuthException {
        if (e is AuthException) return e
        val code = (e as? com.google.firebase.auth.FirebaseAuthException)?.errorCode
        return when {
            code == "ERROR_WEB_CONTEXT_CANCELED" || code == "ERROR_WEB_CONTEXT_ALREADY_PRESENTED" -> AuthException(AuthException.Reason.CANCELLED)
            code == "ERROR_OPERATION_NOT_ALLOWED" -> AuthException(AuthException.Reason.NOT_CONFIGURED, "Apple: Firebase'te Apple sağlayıcısı açık değil")
            e is com.google.firebase.auth.FirebaseAuthUserCollisionException ->
                AuthException(AuthException.Reason.FAILED, "Bu e-posta adresi Google hesabıyla kayıtlı; Google ile giriş yapın")
            isNetwork(e) -> AuthException(AuthException.Reason.NETWORK)
            isAccountProblem(e) -> AuthException(AuthException.Reason.NO_ACCOUNT, "Apple: ${e.message}")
            else -> AuthException(AuthException.Reason.FAILED, "Apple: ${code ?: e.message}")
        }
    }

    /** Google kimlik jetonu ve Google'ın verdiği ad/fotoğraf. */
    private class GoogleToken(val idToken: String, val givenName: String?, val familyName: String?, val photoUrl: String?)

    /**
     * v35 Google kimlik jetonu. Önce Credential Manager: girişte "Google ile oturum aç" penceresi (düğmeyle başlatılan
     * giriş için önerilen akış), yeniden doğrulamada bu uygulamada kullanılmış hesap (tek dokunuş). Credential
     * Manager hesap seçildikten sonra da "iptal" dönebiliyor (Google tarafında [16] yeniden doğrulama/yapılandırma
     * hataları); o zaman — ve bu oturumda bir kez böyle olduysa hep — eski Google Sign-In akışı kullanılır; onun
     * hata kodları da gerçek nedeni söyler (10: SHA-1/OAuth yapılandırması, 7: ağ, 12501: kullanıcı iptal etti).
     */
    private suspend fun googleToken(activity: Activity, clientId: String, reauth: Boolean): GoogleToken {
        if (!preferLegacy) {
            try {
                return credentialManagerToken(activity, clientId, reauth)
            } catch (e: GetCredentialCancellationException) {
                com.karapp.drawguidance.diagnostics.AppLog.w(TAG, "Credential Manager iptal döndü (${e.type}): ${e.message}; eski Google Sign-In deneniyor")
                preferLegacy = true
            } catch (e: NoCredentialException) {
                com.karapp.drawguidance.diagnostics.AppLog.w(TAG, "Credential Manager hesap bulamadı: ${e.message}; eski Google Sign-In deneniyor")
            } catch (e: GetCredentialException) {
                com.karapp.drawguidance.diagnostics.AppLog.w(TAG, "Credential Manager hatası (${e.type}): ${e.message}; eski Google Sign-In deneniyor")
                preferLegacy = true
            }
        }
        return legacyToken(activity, clientId, silentFirst = reauth)
    }

    private suspend fun credentialManagerToken(activity: Activity, clientId: String, reauth: Boolean): GoogleToken {
        val manager = CredentialManager.create(activity)
        val option = if (reauth) {
            GetGoogleIdOption.Builder().setServerClientId(clientId).setFilterByAuthorizedAccounts(true).setAutoSelectEnabled(true).build()
        } else GetSignInWithGoogleOption.Builder(clientId).build()
        val result = manager.getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = result.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw AuthException(AuthException.Reason.FAILED, "Beklenmeyen kimlik türü")
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        return GoogleToken(google.idToken, google.givenName, google.familyName, google.profilePictureUri?.toString())
    }

    /** Eski Google Sign-In (play-services-auth): hesap seçici etkinliği; yeniden doğrulamada önce sessiz giriş. */
    private suspend fun legacyToken(activity: Activity, clientId: String, silentFirst: Boolean): GoogleToken {
        val host = activity as? androidx.activity.ComponentActivity ?: throw AuthException(AuthException.Reason.FAILED, "Etkinlik desteklenmiyor")
        val options = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId).requestEmail().requestProfile().build()
        val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(activity, options)
        if (silentFirst) {
            runCatching { client.silentSignIn().await() }.getOrNull()?.let { a -> a.idToken?.let { return GoogleToken(it, a.givenName, a.familyName, a.photoUrl?.toString()) } }
        } else {
            // Hesap seçici her seferinde açılsın (önceki seçim hatırlanmasın)
            runCatching { client.signOut().await() }
        }
        val data = suspendCancellableCoroutine<android.content.Intent?> { cont ->
            val key = "dg-google-sign-in-${System.nanoTime()}"
            var launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>? = null
            launcher = host.activityResultRegistry.register(key, androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
                launcher?.unregister()
                if (cont.isActive) cont.resume(r.data)
            }
            cont.invokeOnCancellation { launcher.unregister() }
            launcher.launch(client.signInIntent)
        }
        val account = try {
            com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data).await()
        } catch (e: com.google.android.gms.common.api.ApiException) {
            com.karapp.drawguidance.diagnostics.AppLog.w(TAG, "Google Sign-In kodu ${e.statusCode}: ${e.message}")
            throw legacyError(e.statusCode)
        } catch (e: Exception) {
            val code = (e.cause as? com.google.android.gms.common.api.ApiException)?.statusCode
            com.karapp.drawguidance.diagnostics.AppLog.w(TAG, "Google Sign-In başarısız (kod $code)", e)
            throw if (code != null) legacyError(code) else AuthException(AuthException.Reason.FAILED, e.message)
        }
        val token = account.idToken ?: throw AuthException(AuthException.Reason.FAILED, "Google: kimlik jetonu yok (web istemci kimliği)")
        return GoogleToken(token, account.givenName, account.familyName, account.photoUrl?.toString())
    }

    private fun isAccountProblem(e: Exception) = e.javaClass.simpleName.let { it.contains("InvalidUser") || it.contains("InvalidCredentials") || it.contains("UserCollision") }

    private fun isNetwork(e: Exception) = e is java.io.IOException || e.javaClass.simpleName.contains("Network")

    override fun current(): GoogleAccount? = auth()?.currentUser?.let { account(it, null, null, null) }

    override suspend fun idToken(force: Boolean): String? = runCatching { auth()?.currentUser?.getIdToken(force)?.await()?.token }.getOrNull()

    override suspend fun reload(): GoogleAccount? {
        val user = auth()?.currentUser ?: return null
        runCatching { user.reload().await() }
        return account(auth()?.currentUser ?: user, null, null, null)
    }

    override suspend fun signOut() {
        runCatching { auth()?.signOut() }
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    private fun account(user: FirebaseUser, given: String?, family: String?, photo: String?): GoogleAccount {
        val (g, f) = splitName(user.displayName)
        return GoogleAccount(
            firebaseUid = user.uid,
            displayName = user.displayName,
            givenName = given ?: g,
            familyName = family ?: f,
            email = user.email,
            emailVerified = user.isEmailVerified,
            photoUrl = user.photoUrl?.toString() ?: photo,
            accountCreatedAt = user.metadata?.creationTimestamp,
        )
    }
}

/** Play Services görevini askıya alarak bekler. */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) cont.resume(task.result)
        else cont.resumeWithException(task.exception ?: IllegalStateException("Görev başarısız"))
    }
}

/** v35 eski Google Sign-In durum kodundan giriş hatası (kod iletide kalır: Günlükler'de ve ekranda görünür). */
internal fun legacyError(code: Int): AuthException = when (code) {
    12501 -> AuthException(AuthException.Reason.CANCELLED) // SIGN_IN_CANCELLED
    7 -> AuthException(AuthException.Reason.NETWORK, "Google: ağ hatası (7)")
    10 -> AuthException(AuthException.Reason.FAILED, "Google: yapılandırma hatası (10): uygulamanın SHA-1 parmak izi ya da OAuth istemcisi Firebase'te kayıtlı değil")
    12500 -> AuthException(AuthException.Reason.FAILED, "Google: giriş başarısız (12500)")
    else -> AuthException(AuthException.Reason.FAILED, "Google: kod $code")
}

private const val TAG = "Account"
