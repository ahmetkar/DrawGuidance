package com.karapp.drawguidance.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.Period

/**
 * v37 çocuk modu tercihleri: maskot (Boyacık) çocuk tuvalinde sürekli açık mı ve Ana sayfadaki çocuk modu önerisi
 * kapatıldı mı. İlk girişteki tanıtımın gösterildiği bilgisi eğitim tercihlerinde ([TutorialPrefs]) tutulur.
 * v38: çizim görevinin ilerlemesi (seviyelerin en iyi puanları).
 */
class KidsPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("kids_mode", Context.MODE_PRIVATE)

    private val _mascotOn = MutableStateFlow(prefs.getBoolean(KEY_MASCOT, false))
    /** Maskot çocuk tuvalinin köşesinde sürekli duruyor mu (kapalıyken yalnız önerilerde ve eğitimde görünür). */
    val mascotOn: StateFlow<Boolean> = _mascotOn.asStateFlow()

    private val _homeDismissed = MutableStateFlow(prefs.getBoolean(KEY_HOME, false))
    /** Ana sayfadaki çocuk modu önerisi kapatıldı mı. */
    val homeCardDismissed: StateFlow<Boolean> = _homeDismissed.asStateFlow()

    fun setMascotOn(on: Boolean) {
        _mascotOn.value = on
        prefs.edit().putBoolean(KEY_MASCOT, on).apply()
    }

    fun dismissHomeCard() {
        _homeDismissed.value = true
        prefs.edit().putBoolean(KEY_HOME, true).apply()
    }

    private val _challengeBest = MutableStateFlow(parseBest(prefs.getString(KEY_CHALLENGE, null)))
    /** v38 çizim görevinde seviyelerin en iyi puanları (seviye kimliği → 0–100); uygulama kapansa da kalır. */
    val challengeBest: StateFlow<Map<String, Int>> = _challengeBest.asStateFlow()

    /** v39 eski en iyi puanlar (veritabanına bir kez taşınır; bkz. ChallengeProgressRepository). */
    fun legacyChallengeBest(): Map<String, Int> = parseBest(prefs.getString(KEY_CHALLENGE, null))

    /** v39 taşıma yapıldı mı. */
    var challengeMigrated: Boolean
        get() = prefs.getBoolean(KEY_CHALLENGE_MIGRATED, false)
        set(value) { prefs.edit().putBoolean(KEY_CHALLENGE_MIGRATED, value).apply() }

    /** v38 seviyenin puanı yazılır (en iyisi kalır); en iyi puan döner. */
    fun recordChallenge(levelId: String, score: Int): Int {
        val now = _challengeBest.value
        val best = maxOf(now[levelId] ?: 0, score.coerceIn(0, 100))
        val next = now + (levelId to best)
        _challengeBest.value = next
        prefs.edit().putString(KEY_CHALLENGE, next.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
        return best
    }

    companion object {
        private const val KEY_MASCOT = "mascot_on"
        private const val KEY_HOME = "home_card_dismissed"
        private const val KEY_CHALLENGE = "challenge_best"
        private const val KEY_CHALLENGE_MIGRATED = "challenge_migrated_v39"

        /** "kimlik:puan,kimlik:puan" → tablo (bozuk parçalar atlanır). */
        private fun parseBest(raw: String?): Map<String, Int> = raw.orEmpty().split(',').mapNotNull { part ->
            val i = part.lastIndexOf(':')
            if (i <= 0) null else part.substring(i + 1).toIntOrNull()?.let { part.substring(0, i) to it }
        }.toMap()

        /** Çocuk modu önerisinin yaş sınırı (bu yaştan küçükler). */
        const val CHILD_AGE = 12

        /**
         * Doğum tarihi (yyyy-MM-dd) [CHILD_AGE] yaşından küçük birine mi ait. Tarih yoksa ya da okunamıyorsa false
         * (giriş yapılmamışsa doğum tarihi bilinmez, öneri gösterilmez).
         */
        fun isChild(birthDate: String?, today: LocalDate = LocalDate.now()): Boolean = isUnder(birthDate, CHILD_AGE, today)

        /** v39 doğum tarihi [age] yaşından küçük birine mi ait (tarih yoksa ya da okunamıyorsa false). */
        fun isUnder(birthDate: String?, age: Int, today: LocalDate = LocalDate.now()): Boolean {
            val birth = birthDate?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() } ?: return false
            if (birth.isAfter(today)) return false
            return Period.between(birth, today).years < age
        }

        /** v39 Ana sayfadaki çizim görevi önerisinin yaş sınırı (13 yaşından küçükler). */
        const val CHALLENGE_AGE = 13
    }
}
