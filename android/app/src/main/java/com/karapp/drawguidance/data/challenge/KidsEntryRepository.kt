package com.karapp.drawguidance.data.challenge

import com.karapp.drawguidance.data.local.KidsEntryDao
import com.karapp.drawguidance.data.local.KidsEntryEntity
import com.karapp.drawguidance.data.local.LocalUserDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * v40 çocuk tuvaline girişlerin sayacı (`kids_entries` tablosu; kullanıcı başına, giriş yoksa cihaz başına). İlk
 * [SUGGEST_ENTRIES] girişte, çizim görevi zaten açık değilse maskot Boyacık görevi önerir ([shouldSuggest]); önerinin
 * gösterilmesi ve Başlat / İptal cevapları da sayılır.
 */
class KidsEntryRepository(
    private val dao: KidsEntryDao,
    private val users: LocalUserDao,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Öneri gösterilsin mi (girişler yine sayılır; eski testlerde kapalı). */
    val suggestionsEnabled: Boolean = true,
) {
    private val lock = Mutex()

    /** Satırın anahtarı: giriş yapılmışsa Firebase uid'si, yoksa cihaz. */
    suspend fun userKey(): String = users.get()?.takeIf { it.signedIn }?.firebaseUid?.takeIf { it.isNotBlank() }?.let { "uid:$it" } ?: DEVICE

    /** Bir giriş sayılır; güncel satır döner (girişin sırası [KidsEntryEntity.entries]). */
    suspend fun recordEntry(): KidsEntryEntity = update { it.copy(entries = it.entries + 1, lastEntryAt = clock()) }

    /** Öneri gösterildi. */
    suspend fun recordSuggested(): KidsEntryEntity = update { it.copy(suggested = it.suggested + 1) }

    /** Önerinin cevabı: Başlat ([started] true) ya da İptal. */
    suspend fun recordAnswer(started: Boolean): KidsEntryEntity =
        update { if (started) it.copy(started = it.started + 1) else it.copy(cancelled = it.cancelled + 1) }

    suspend fun current(): KidsEntryEntity? = dao.get(userKey())

    private suspend fun update(change: (KidsEntryEntity) -> KidsEntryEntity): KidsEntryEntity = lock.withLock {
        val key = userKey()
        val row = change(dao.get(key) ?: KidsEntryEntity(key, 0, 0, 0, 0, 0L))
        dao.upsert(row)
        row
    }

    companion object {
        /** Giriş yapılmamış cihazın satırı. */
        const val DEVICE = "device"

        /** Öneri ilk bu kadar girişte gösterilir. */
        const val SUGGEST_ENTRIES = 5

        /** [entry]. girişte öneri gösterilir mi: ilk [SUGGEST_ENTRIES] giriş ve görev açık değil. */
        fun shouldSuggest(entry: Int, challengeActive: Boolean): Boolean = entry in 1..SUGGEST_ENTRIES && !challengeActive
    }
}
