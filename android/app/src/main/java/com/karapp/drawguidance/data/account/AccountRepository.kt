package com.karapp.drawguidance.data.account

import android.app.Activity
import com.karapp.drawguidance.data.local.LocalUserDao
import com.karapp.drawguidance.data.local.LocalUserEntity
import com.karapp.drawguidance.data.local.SyncEventEntity
import com.karapp.drawguidance.data.remote.v1.RemoteStore
import com.karapp.drawguidance.data.remote.v1.RemoteUser
import com.karapp.drawguidance.data.remote.v1.UserBody
import com.karapp.drawguidance.data.sync.Connectivity
import com.karapp.drawguidance.data.sync.PermanentSyncException
import com.karapp.drawguidance.data.sync.SyncEngine
import com.karapp.drawguidance.data.sync.SyncTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.UUID

/** v34 hesap silmenin sonucu. */
enum class DeleteResult {
    /** Sunucu, Firebase ve cihaz: hepsi silindi. */
    DONE,
    /** Sunucu ve cihaz silindi; Firebase kullanıcısı silinemedi (oturum kapatıldı). */
    FIREBASE_FAILED,
}

/** v34 hesap silinemedi: [stage] nerede durulduğunu söyler (hiçbir şey silinmedi). */
class AccountDeleteException(val stage: Stage, message: String? = null) : Exception(message ?: stage.name) {
    enum class Stage { NO_ACCOUNT, SERVER }
}

/** Girişin sonucu: tamam ya da doğum tarihi istenecek. */
sealed interface SignInResult {
    data class Done(val user: LocalUserEntity) : SignInResult
    /** İlk girişte doğum tarihi sorulur ([complete] ile tamamlanır). */
    data class NeedsBirthDate(val account: GoogleAccount) : SignInResult
}

/**
 * v33 hesap: Google (Firebase) ile giriş, doğum tarihi, cihazın kullanıcısı ve sunucuya yazma.
 *
 * - Kullanıcının bilgileri (Google kimliği, ad, soyad, e-posta, doğrulama, fotoğraf, hesap oluşturma tarihi, doğum
 *   tarihi) tek birincil kimlikle ([LocalUserEntity.id]) **önce cihaza** yazılır, sonra olay tablosundaki
 *   `USER_UPSERT` işiyle sunucunun `users` koleksiyonuna (bağlantı yoksa bağlantı gelince).
 * - Oturum Firebase'de kalıcıdır; bir kez girilince açık kalır.
 * - Hesapla ilgili her şey yalnız internet bağlantısı varken görünür ([active]); bağlantı yoksa uygulama giriş
 *   yapılmamış gibi çalışır.
 */
class AccountRepository(
    private val dao: LocalUserDao,
    private val auth: AuthProvider,
    private val engine: SyncEngine,
    private val remote: RemoteStore?,
    private val connectivity: Connectivity,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Cihazın kullanıcısı (oturum kapalıysa da bilgileri durur). */
    val user: StateFlow<LocalUserEntity?> = dao.observe().stateIn(scope, SharingStarted.Eagerly, null)

    /** Giriş yapılmış ve internet var: hesap ayarları ve ana sayfadaki profil yalnız bu durumda görünür. */
    val active: StateFlow<LocalUserEntity?> = combine(user, connectivity.online) { u, online -> u?.takeIf { it.signedIn && online } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val online: StateFlow<Boolean> get() = connectivity.online

    /** Bu derlemede giriş açık mı. */
    val available: Boolean get() = auth.available

    init {
        engine.register(SyncTypes.USER_UPSERT) { upsertRemote(it) }
    }

    /** Açılışta: Firebase oturumu kapanmışsa (ör. uygulama verisi silindi) cihazdaki kullanıcı da çıkış yapmış sayılır. */
    fun startup() {
        scope.launch {
            val local = dao.get() ?: return@launch
            val current = runCatching { auth.current() }.getOrNull()
            if (local.signedIn && (current == null || current.firebaseUid != local.firebaseUid)) {
                dao.upsert(local.copy(signedIn = false, updatedAt = clock()))
            }
        }
    }

    /**
     * Google ile giriş (hesap seçici açılır). Sunucuda (ya da cihazda) bu hesabın doğum tarihi varsa giriş biter, yoksa
     * doğum tarihi sorulur.
     */
    suspend fun signIn(activity: Activity): SignInResult {
        val account = auth.signIn(activity)
        val existing = dao.byUid(account.firebaseUid)
        val remoteUser = fetchRemote()
        val birth = existing?.birthDate ?: remoteUser?.birthDate
        if (birth == null) return SignInResult.NeedsBirthDate(account)
        return SignInResult.Done(save(account, birth, idHint = remoteUser?.id ?: existing?.id, keepNames = existing))
    }

    /** İlk girişte sorulan doğum tarihiyle girişi tamamlar. */
    suspend fun complete(account: GoogleAccount, birthDate: LocalDate, termsVersion: String? = null): LocalUserEntity {
        require(!birthDate.isAfter(LocalDate.now())) { "Doğum tarihi gelecekte olamaz" }
        val existing = dao.byUid(account.firebaseUid)
        return save(account, birthDate.toString(), existing?.id, existing, termsVersion)
    }

    /** Ad, soyad ve doğum tarihi (hesap ayarları). */
    suspend fun updateProfile(givenName: String?, familyName: String?, birthDate: LocalDate?) {
        val u = dao.get() ?: return
        if (birthDate != null) require(!birthDate.isAfter(LocalDate.now())) { "Doğum tarihi gelecekte olamaz" }
        val given = givenName?.trim()?.ifEmpty { null }
        val family = familyName?.trim()?.ifEmpty { null }
        val display = listOfNotNull(given, family).joinToString(" ").ifEmpty { u.displayName }
        dao.upsert(u.copy(givenName = given, familyName = family, displayName = display, birthDate = birthDate?.toString() ?: u.birthDate, updatedAt = clock(), syncedAt = null))
        engine.enqueue(SyncTypes.USER_UPSERT)
    }

    /** Google'daki güncel bilgiler (e-posta doğrulaması, fotoğraf, ad) alınır. */
    suspend fun refreshFromGoogle() {
        val u = dao.get() ?: return
        val a = auth.reload() ?: return
        dao.upsert(
            u.copy(
                displayName = a.displayName ?: u.displayName, email = a.email ?: u.email, emailVerified = a.emailVerified,
                photoUrl = a.photoUrl ?: u.photoUrl, accountCreatedAt = a.accountCreatedAt ?: u.accountCreatedAt,
                updatedAt = clock(), syncedAt = null,
            ),
        )
        engine.enqueue(SyncTypes.USER_UPSERT)
    }

    /** Çıkış: Firebase oturumu kapanır; cihazdaki bilgiler (doğum tarihi) aynı hesapla yeniden girişte kullanılır. */
    /** v43 çıkıştan hemen önce (oturum jetonu hâlâ geçerliyken): cihazın sunucudaki hesap bağı kaldırılır. */
    @Volatile var beforeSignOut: (suspend (token: String?) -> Unit)? = null

    suspend fun signOut() {
        beforeSignOut?.let { hook -> runCatching { hook(runCatching { auth.idToken(false) }.getOrNull()) } }
        auth.signOut()
        dao.get()?.let { dao.upsert(it.copy(signedIn = false, updatedAt = clock())) }
    }

    /**
     * v34 hesabı siler: kimlik yeniden doğrulanır; kullanıcının bekleyen sunucu işi silinir; sunucudaki kayıt
     * (`DELETE /api/v1/users/me`), Firebase kullanıcısı ve cihazdaki kullanıcı silinir.
     *
     * - Sunucuya ulaşılamazsa hiçbir şey silinmez ([AccountDeleteException.Stage.SERVER]); cihazdaki değişiklik
     *   sunucuya yazılmamışsa yazma işi yeniden sıraya girer.
     * - Sunucudaki kayıt silindikten sonra Firebase silme başarısız olursa yine de oturum kapanır ve cihazdaki bilgiler
     *   silinir ([DeleteResult.FIREBASE_FAILED]).
     */
    suspend fun deleteAccount(activity: Activity): DeleteResult {
        if (dao.get() == null) throw AccountDeleteException(AccountDeleteException.Stage.NO_ACCOUNT)
        // Kullanıcı arayüzü (hesap seçimi) gerekebilir: işlerin kilidinden önce
        auth.reauthenticate(activity)
        return engine.exclusive {
            // Sıradaki yazma işi kullanıcıyı sunucuda yeniden oluşturmasın
            engine.cancelPending(SyncTypes.USER_UPSERT)
            val r = remote
            if (r != null) {
                try {
                    val token = auth.idToken(true) ?: throw AccountDeleteException(AccountDeleteException.Stage.SERVER, "Oturum jetonu alınamadı")
                    r.deleteMe(token)
                } catch (e: Exception) {
                    val notFound = e is com.karapp.drawguidance.data.remote.v1.RemoteException && e.status == 404
                    if (!notFound) {
                        // Hiçbir şey silinmedi: sunucuya yazılmamış değişiklik yeniden sıraya
                        val current = dao.get()
                        // (enqueue kilidi almaz; iş kilit bırakılınca yapılır)
                        if (current != null && current.signedIn && current.syncedAt == null) engine.enqueue(SyncTypes.USER_UPSERT)
                        throw (e as? AccountDeleteException) ?: AccountDeleteException(AccountDeleteException.Stage.SERVER, e.message)
                    }
                }
            }
            val firebaseOk = runCatching { auth.deleteUser() }.isSuccess
            if (!firebaseOk) runCatching { auth.signOut() }
            dao.clear()
            if (firebaseOk) DeleteResult.DONE else DeleteResult.FIREBASE_FAILED
        }
    }

    private suspend fun fetchRemote(): RemoteUser? {
        if (remote == null || !connectivity.online.value) return null
        return withTimeoutOrNull(8_000) {
            runCatching { auth.idToken(false)?.let { remote.me(it) } }.getOrNull()
        }
    }

    /** Cihaza yazar (yalnız bu kullanıcı kalır) ve sunucuya yazma işini olay tablosuna ekler. */
    private suspend fun save(account: GoogleAccount, birthDate: String, idHint: String?, keepNames: LocalUserEntity?, termsVersion: String? = null): LocalUserEntity {
        val now = clock()
        val previous = dao.byUid(account.firebaseUid)
        val user = LocalUserEntity(
            id = idHint ?: UUID.randomUUID().toString(),
            firebaseUid = account.firebaseUid,
            displayName = account.displayName ?: keepNames?.displayName,
            givenName = account.givenName ?: keepNames?.givenName,
            familyName = account.familyName ?: keepNames?.familyName,
            email = account.email,
            emailVerified = account.emailVerified,
            photoUrl = account.photoUrl,
            accountCreatedAt = account.accountCreatedAt,
            birthDate = birthDate,
            signedIn = true,
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
            // v36 hesap açılırken onaylanan kullanım şartları ve KVKK metni (yeniden girişte önceki onay korunur)
            termsAcceptedAt = if (termsVersion != null) now else previous?.termsAcceptedAt,
            termsVersion = termsVersion ?: previous?.termsVersion,
        )
        dao.replaceAll(user)
        engine.enqueue(SyncTypes.USER_UPSERT)
        return user
    }

    /** `USER_UPSERT`: cihazdaki kullanıcının son hali sunucuya yazılır; sunucunun kimliği farklıysa o kullanılır. */
    private suspend fun upsertRemote(@Suppress("UNUSED_PARAMETER") event: SyncEventEntity) {
        val r = remote ?: throw PermanentSyncException("Sunucu yapılandırılmadı")
        val u = dao.get() ?: return
        if (!u.signedIn) return
        var token = auth.idToken(false) ?: throw IllegalStateException("Oturum jetonu alınamadı")
        val body = UserBody(
            id = u.id, firebaseUid = u.firebaseUid, displayName = u.displayName, givenName = u.givenName,
            familyName = u.familyName, email = u.email, emailVerified = u.emailVerified, photoUrl = u.photoUrl,
            accountCreatedAt = u.accountCreatedAt, birthDate = u.birthDate, clientUpdatedAt = u.updatedAt,
            termsAcceptedAt = u.termsAcceptedAt, termsVersion = u.termsVersion,
        )
        val saved = try {
            r.putMe(token, body)
        } catch (e: com.karapp.drawguidance.data.remote.v1.RemoteException) {
            if (!e.isUnauthorized) throw e
            // Jetonun süresi dolmuş olabilir: yenilenip bir kez daha denenir
            token = auth.idToken(true) ?: throw e
            r.putMe(token, body)
        }
        val current = dao.get() ?: return
        if (saved.id != current.id) dao.replaceAll(current.copy(id = saved.id, syncedAt = clock()))
        else dao.markSynced(current.id, clock())
    }
}
