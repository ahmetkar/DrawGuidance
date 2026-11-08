package com.karapp.drawguidance.data.kids

import android.util.Base64
import com.karapp.drawguidance.data.local.KidsLockDao
import com.karapp.drawguidance.data.local.KidsLockEntity
import com.karapp.drawguidance.data.local.LocalUserDao
import com.karapp.drawguidance.data.local.LocalUserEntity
import com.karapp.drawguidance.data.settings.KidsPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * v44 çocuk kilidi. Kilit açıkken uygulama yalnız çocuk çizim modunu gösterir: Ana sayfada yalnız çocuk çizimi,
 * Tuvallerim'de yalnız çocuk tuvalleri; başka türden tuval açılmaz ve oluşturulamaz.
 *
 * - **Yaş:** giriş yapmış kullanıcının doğum tarihine göre 12 yaşından küçükse kilit kendiliğinden açılır (aynı hesap
 *   için bir kez; ebeveyn kapattıysa yeniden açılmaz). Ebeveynden 4 haneli şifre belirlemesi istenir.
 * - **Ayarlar:** yaş ne olursa olsun kilit açılıp kapatılır; açarken şifre belirlenir, kapatırken şifre sorulur.
 * - **Şifre:** yerel veritabanında yalnız özeti (PBKDF2-HMAC-SHA1, 20 000 tur, 16 baytlık rastgele tuz); art arda
 *   [MAX_ATTEMPTS] yanlış denemeden sonra giderek uzayan bekleme.
 */
class KidsLockRepository(
    private val dao: KidsLockDao,
    private val users: LocalUserDao?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    data class State(val enabled: Boolean = false, val hasPin: Boolean = false, val source: String? = null, val blockedUntil: Long = 0)

    enum class Result { OK, WRONG, BLOCKED, NO_PIN, INVALID }

    private val mutex = Mutex()

    val state: StateFlow<State> = dao.observe().map { r ->
        State(r?.enabled == true, r?.pinHash != null, r?.source, r?.blockedUntil ?: 0)
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, State())

    /** Kilit etkin mi. */
    val locked: StateFlow<Boolean> = state.map { it.enabled }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, false)

    /** Açılışta: girişli kullanıcı değiştikçe yaş kuralı uygulanır. */
    fun start() {
        val u = users ?: return
        scope.launch { u.observe().collect { applyAge(it) } }
    }

    /** 12 yaşından küçük girişli kullanıcıda kilit kendiliğinden açılır (hesap başına bir kez). */
    suspend fun applyAge(user: LocalUserEntity?, today: java.time.LocalDate = java.time.LocalDate.now()) = mutex.withLock {
        val u = user?.takeIf { it.signedIn } ?: return@withLock
        if (!KidsPrefs.isUnder(u.birthDate, LOCK_AGE, today)) return@withLock
        val row = dao.get() ?: KidsLockEntity()
        if (row.ageAppliedUid == u.firebaseUid) return@withLock
        dao.upsert(row.copy(enabled = true, source = KidsLockEntity.SOURCE_AGE, ageAppliedUid = u.firebaseUid, updatedAt = clock()))
    }

    /** Ayarlar'dan kilidi açar ve şifreyi belirler. */
    suspend fun enable(pin: String): Result = mutex.withLock {
        if (!valid(pin)) return@withLock Result.INVALID
        val row = dao.get() ?: KidsLockEntity()
        val (hash, salt) = hash(pin)
        dao.upsert(row.copy(enabled = true, source = row.source.takeIf { row.enabled } ?: KidsLockEntity.SOURCE_MANUAL,
            pinHash = hash, pinSalt = salt, failedAttempts = 0, blockedUntil = 0, updatedAt = clock()))
        Result.OK
    }

    /** Kilit yaşla açıldı ve şifre yoksa ebeveyn şifreyi belirler (kilit açık kalır). */
    suspend fun setPin(pin: String): Result = mutex.withLock {
        if (!valid(pin)) return@withLock Result.INVALID
        val row = dao.get() ?: return@withLock Result.NO_PIN
        if (row.pinHash != null) return@withLock Result.WRONG
        val (hash, salt) = hash(pin)
        dao.upsert(row.copy(pinHash = hash, pinSalt = salt, updatedAt = clock()))
        Result.OK
    }

    /** Şifreyle kilidi kapatır. */
    suspend fun disable(pin: String): Result = mutex.withLock {
        val r = check(pin)
        if (r == Result.OK) {
            val row = dao.get() ?: return@withLock Result.OK
            dao.upsert(row.copy(enabled = false, failedAttempts = 0, blockedUntil = 0, updatedAt = clock()))
        }
        r
    }

    /** Şifreyi değiştirir (eskisiyle). */
    suspend fun changePin(old: String, new: String): Result = mutex.withLock {
        if (!valid(new)) return@withLock Result.INVALID
        val r = check(old)
        if (r != Result.OK) return@withLock r
        val row = dao.get() ?: return@withLock Result.NO_PIN
        val (hash, salt) = hash(new)
        dao.upsert(row.copy(pinHash = hash, pinSalt = salt, updatedAt = clock()))
        Result.OK
    }

    /** Şifre doğru mu (yanlışta sayaç artar, gerekirse bekletilir). [mutex] içinde çağrılır. */
    private suspend fun check(pin: String): Result {
        val row = dao.get() ?: return Result.NO_PIN
        val stored = row.pinHash ?: return Result.NO_PIN
        val salt = row.pinSalt ?: return Result.NO_PIN
        val now = clock()
        if (now < row.blockedUntil) return Result.BLOCKED
        val ok = valid(pin) && MessageDigest.isEqual(derive(pin, decode(salt)), decode(stored))
        if (ok) {
            if (row.failedAttempts != 0) dao.upsert(row.copy(failedAttempts = 0, blockedUntil = 0, updatedAt = now))
            return Result.OK
        }
        val fails = row.failedAttempts + 1
        val block = if (fails >= MAX_ATTEMPTS) now + BLOCK_MS * (1L shl (fails - MAX_ATTEMPTS).coerceAtMost(6)) else 0L
        dao.upsert(row.copy(failedAttempts = fails, blockedUntil = block, updatedAt = now))
        return if (block > 0) Result.BLOCKED else Result.WRONG
    }

    private fun valid(pin: String) = pin.length == 4 && pin.all { it in '0'..'9' }

    private fun hash(pin: String): Pair<String, String> {
        val salt = ByteArray(16).also(random::nextBytes)
        return encode(derive(pin, salt)) to encode(salt)
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    private fun encode(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun decode(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        /** Bu yaştan küçük girişli kullanıcılarda kilit kendiliğinden açılır. */
        const val LOCK_AGE = 12
        const val MAX_ATTEMPTS = 5
        const val BLOCK_MS = 30_000L
        private const val ITERATIONS = 20_000
    }
}
