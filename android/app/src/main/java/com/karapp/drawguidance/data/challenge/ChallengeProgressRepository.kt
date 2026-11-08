package com.karapp.drawguidance.data.challenge

import com.karapp.drawguidance.data.account.AuthProvider
import com.karapp.drawguidance.data.local.ChallengeProgressDao
import com.karapp.drawguidance.data.local.ChallengeProgressEntity
import com.karapp.drawguidance.data.local.LocalUserDao
import com.karapp.drawguidance.data.remote.v1.ChallengeLevelBody
import com.karapp.drawguidance.data.remote.v1.ChallengeProgressBody
import com.karapp.drawguidance.data.remote.v1.RemoteException
import com.karapp.drawguidance.data.remote.v1.RemoteLevelProgress
import com.karapp.drawguidance.data.remote.v1.RemoteStore
import com.karapp.drawguidance.data.settings.KidsPrefs
import com.karapp.drawguidance.data.sync.SyncEngine
import com.karapp.drawguidance.diagnostics.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * v39 çizim görevinin ilerlemesi (`challenge_progress` tablosu). Ana sayfadaki öneri kartı, İlerleme penceresi ve
 * seviye kilitleri buradan okur. Eski ayar dosyasındaki (v38 KidsPrefs) en iyi puanlar bir kez taşınır.
 *
 * Google ile giriş yapılmışsa sunucuyla eşitlenir (`PUT /api/v1/challenge/progress`, olay tablosundaki
 * [SYNC_TYPE] işiyle): iki tarafta da seviyenin en iyi puanı kalır ([merge]). Seviye bitince ve girişte iş sıraya girer.
 *
 * [levelIds]: seviyelerin sırası → kalıcı kimliği (sunucu sırayı, eski kayıt kimliği kullanır); [stars] puandan yıldız.
 */
class ChallengeProgressRepository(
    private val dao: ChallengeProgressDao,
    private val users: LocalUserDao,
    private val auth: AuthProvider?,
    private val remote: RemoteStore?,
    private val engine: SyncEngine?,
    private val kidsPrefs: KidsPrefs?,
    private val levelIds: () -> List<String>,
    private val stars: (Int) -> Int,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Bellekteki son hal (seviye sırası → kayıt): yazma veritabanına arkadan gider, arayüz beklemez. */
    private val _rows = MutableStateFlow<Map<Int, ChallengeProgressEntity>>(emptyMap())
    val rows: StateFlow<Map<Int, ChallengeProgressEntity>> = _rows.asStateFlow()
    @Volatile var loaded = false
        private set

    /** Seviye kimliği → en iyi puan (düzenleyicideki görev denetleyicisi bu biçimi kullanır). */
    val best: StateFlow<Map<String, Int>> = _rows.map { m ->
        val ids = levelIds()
        m.mapNotNull { (i, r) -> ids.getOrNull(i)?.let { it to r.bestScore } }.toMap()
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val lock = Mutex()

    init {
        engine?.register(SYNC_TYPE) { sync() }
    }

    /**
     * Açılışta: eski puanlar taşınır, tablo okunur; giriş yapılınca eşitleme işi sıraya girer ([network] false ise
     * — eski testler — girişte iş eklenmez).
     */
    fun start(network: Boolean = true) {
        scope.launch {
            runCatching { load() }.onFailure { AppLog.w(TAG, "ilerleme okunamadı: ${it.message}") }
            if (!network) return@launch
            users.observe().map { u -> u?.takeIf { it.signedIn }?.firebaseUid }.distinctUntilChanged().collect { uid ->
                if (uid != null) requestSync()
            }
        }
    }

    /** Eski en iyi puanlar (bir kez) ve tablo. */
    suspend fun load() = lock.withLock {
        migrateLegacy()
        _rows.value = dao.all().associateBy { it.level }
        loaded = true
    }

    private suspend fun migrateLegacy() {
        val prefs = kidsPrefs ?: return
        if (prefs.challengeMigrated) return
        val ids = levelIds()
        val now = clock()
        val rows = prefs.legacyChallengeBest().mapNotNull { (id, score) ->
            val index = ids.indexOf(id).takeIf { it >= 0 } ?: return@mapNotNull null
            ChallengeProgressEntity(index, score.coerceIn(0, 100), stars(score), now, dirty = true)
        }
        if (rows.isNotEmpty()) dao.upsert(merge(dao.all(), rows))
        prefs.challengeMigrated = true
    }

    /** Seviye kimliğiyle puan (en iyisi kalır); en iyi puan döner. Girişliyse eşitleme sıraya girer. */
    fun record(levelId: String, score: Int): Int {
        val index = levelIds().indexOf(levelId)
        if (index < 0) return score
        return record(index, score)
    }

    fun record(level: Int, score: Int): Int {
        val s = score.coerceIn(0, 100)
        val old = _rows.value[level]
        if (old != null && old.bestScore >= s) return old.bestScore
        val row = ChallengeProgressEntity(level, s, stars(s), clock(), dirty = true)
        _rows.value = _rows.value + (level to row)
        scope.launch {
            lock.withLock {
                val current = dao.get(level)
                if (current == null || current.bestScore < s) dao.upsert(listOf(row))
            }
            requestSync()
        }
        return s
    }

    /** Girişliyse eşitleme işi olay tablosuna (bağlantı yoksa bağlantı gelince yapılır). */
    suspend fun requestSync() {
        if (users.get()?.signedIn != true) return
        engine?.enqueue(SYNC_TYPE)
    }

    /**
     * Eşitleme: cihazdaki bütün seviyeler gönderilir, sunucu en iyi puanlarla birleştirip döner; dönen hal cihazda da
     * en iyilerle birleştirilir ve gönderilenler temiz sayılır.
     */
    suspend fun sync() {
        val r = remote ?: return
        if (users.get()?.signedIn != true) return
        val local = lock.withLock { dao.all() }
        val body = ChallengeProgressBody(local.map { ChallengeLevelBody(it.level, it.bestScore, it.updatedAt) })
        val merged = withToken { t -> if (local.isEmpty()) r.challengeProgress(t) else r.putChallengeProgress(t, body) }
        lock.withLock {
            val now = dao.all()
            val remoteRows = merged.levels.map { it.toEntity() }
            val result = merge(now, remoteRows)
            dao.upsert(result)
            // Sunucuya ulaşan puanlar temiz (bu arada yükselen puan kirli kalır)
            local.forEach { dao.markClean(it.level, it.bestScore) }
            merged.levels.forEach { dao.markClean(it.level, it.bestScore) }
            _rows.value = dao.all().associateBy { it.level }
        }
    }

    private suspend fun <T> withToken(block: suspend (String) -> T): T {
        val a = auth ?: throw IllegalStateException("Giriş yok")
        val token = a.idToken(false) ?: throw IllegalStateException("Oturum jetonu alınamadı")
        return try {
            block(token)
        } catch (e: RemoteException) {
            if (!e.isUnauthorized) throw e
            block(a.idToken(true) ?: throw e)
        }
    }

    private fun RemoteLevelProgress.toEntity() =
        ChallengeProgressEntity(level, bestScore.coerceIn(0, 100), stars(bestScore), updatedAt, dirty = false)

    companion object {
        private const val TAG = "Challenge"
        const val SYNC_TYPE = "CHALLENGE_SYNC"

        /**
         * İki tarafın kayıtlarını birleştirir: her seviyede en iyi puan kalır (eşitse yeni tarih); yerelde daha iyi olan
         * kirli kalır, öteki taraftan gelen daha iyi puan temiz yazılır.
         */
        fun merge(local: List<ChallengeProgressEntity>, other: List<ChallengeProgressEntity>): List<ChallengeProgressEntity> {
            val out = LinkedHashMap<Int, ChallengeProgressEntity>()
            local.forEach { out[it.level] = it }
            other.forEach { o ->
                val l = out[o.level]
                out[o.level] = when {
                    l == null -> o
                    o.bestScore > l.bestScore -> o.copy(dirty = o.dirty)
                    o.bestScore == l.bestScore -> l.copy(updatedAt = maxOf(l.updatedAt, o.updatedAt), stars = maxOf(l.stars, o.stars), dirty = l.dirty && o.dirty)
                    else -> l
                }
            }
            return out.values.sortedBy { it.level }
        }

        /** Geçilmemiş ilk seviye (hepsi geçildiyse son seviye); [pass] geçme puanı. */
        fun nextLevel(best: Map<Int, Int>, levelCount: Int, pass: Int): Int =
            (0 until levelCount).firstOrNull { (best[it] ?: -1) < pass } ?: (levelCount - 1).coerceAtLeast(0)

        /** Geçilen seviye sayısı. */
        fun passedCount(best: Map<Int, Int>, pass: Int): Int = best.count { it.value >= pass }
    }
}
