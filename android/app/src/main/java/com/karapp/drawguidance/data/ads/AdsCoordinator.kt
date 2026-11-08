package com.karapp.drawguidance.data.ads

import android.app.Activity
import android.content.Context
import com.karapp.drawguidance.data.billing.Entitlements
import com.karapp.drawguidance.data.local.LocalUserEntity
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.settings.KidsPrefs
import com.karapp.drawguidance.diagnostics.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** v39 reklamın gösterildiği geçişler. */
enum class AdTrigger { APP_ENTRY, CANVAS_CLOSED }

/** v39 reklam kararı ve nedeni (testlerde ve günlükte). */
enum class AdDecision { SHOW, NOT_LOADED_YET, PREMIUM, KIDS_CANVAS, CHILD_USER, FIRST_LAUNCH, NO_CONSENT, FREQUENCY_CAP, UNAVAILABLE }

/**
 * v39 reklam kuralları (saf; testlenir):
 * - Yalnız Free planda (haklar yüklenmeden karar verilmez: Premium kullanıcıya açılışta yanlışlıkla reklam çıkmaz).
 * - Çocuk tuvalinden çıkarken ve 13 yaşından küçük girişli kullanıcıda asla (Google Play Aileler politikası).
 * - Uygulamanın ilk açılışında (onay ve tanıtım öncesi) yok.
 * - UMP onayı olmadan yok.
 * - İki reklam arasında en az [minIntervalMs] (3 dk).
 */
class AdsPolicy(val minIntervalMs: Long = 3 * 60_000L) {
    fun decide(
        trigger: AdTrigger,
        entitlements: Entitlements,
        child: Boolean,
        canvasKind: CanvasKind? = null,
        firstLaunch: Boolean,
        consent: Boolean,
        lastShownAt: Long?,
        now: Long,
        available: Boolean = true,
    ): AdDecision = when {
        !available -> AdDecision.UNAVAILABLE
        !entitlements.loaded -> AdDecision.NOT_LOADED_YET
        entitlements.adFree(now) -> AdDecision.PREMIUM
        canvasKind == CanvasKind.KIDS -> AdDecision.KIDS_CANVAS
        child -> AdDecision.CHILD_USER
        trigger == AdTrigger.APP_ENTRY && firstLaunch -> AdDecision.FIRST_LAUNCH
        !consent -> AdDecision.NO_CONSENT
        lastShownAt != null && now - lastShownAt < minIntervalMs -> AdDecision.FREQUENCY_CAP
        else -> AdDecision.SHOW
    }

    companion object {
        /** Reklam açısından çocuk: girişli kullanıcının doğum tarihine göre 13 yaşından küçük. */
        const val CHILD_AGE = 13

        fun isChild(user: LocalUserEntity?, today: java.time.LocalDate = java.time.LocalDate.now()): Boolean =
            user != null && user.signedIn && KidsPrefs.isUnder(user.birthDate, CHILD_AGE, today)
    }
}

/**
 * v39 geçiş reklamlarını yöneten sınıf: açılışta (soğuk başlangıç) ve tuval kapatıldıktan sonra [AdsPolicy]'ye göre
 * gösterir. Açılışta onay (UMP) alınır, reklam kütüphanesi başlatılır ve bir reklam önceden yüklenir; tuval
 * kapatılınca hazır reklam varsa gösterilir (yoksa sonraki geçiş için yüklenir, geç gösterilmez).
 */
class AdsCoordinator(
    context: Context,
    private val gateway: AdsGateway,
    private val entitlements: StateFlow<Entitlements>,
    private val user: StateFlow<LocalUserEntity?>,
    private val adUnitId: String,
    private val policy: AdsPolicy = AdsPolicy(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("ads_v39", Context.MODE_PRIVATE)
    @Volatile var lastDecision: AdDecision? = null
        private set
    @Volatile private var consent = false

    private val lastShownAt: Long? get() = prefs.getLong(KEY_LAST, 0L).takeIf { it > 0 }

    /** Haklar yüklenene kadar en çok 3 sn beklenir (yüklenmezse reklam yok). */
    private suspend fun loadedEntitlements(): Entitlements =
        withTimeoutOrNull(3_000) { entitlements.first { it.loaded } } ?: entitlements.value

    /** Uygulamaya giriş (soğuk başlangıç). İlk açılış sayılır ve atlanır. */
    fun onAppEntry(activity: Activity) {
        val first = !prefs.getBoolean(KEY_LAUNCHED, false)
        prefs.edit().putBoolean(KEY_LAUNCHED, true).apply()
        scope.launch {
            val e = loadedEntitlements()
            val child = AdsPolicy.isChild(user.value)
            val pre = policy.decide(AdTrigger.APP_ENTRY, e, child, null, first, consent = true, lastShownAt = lastShownAt, now = clock(), available = gateway.available)
            if (pre != AdDecision.SHOW) { record(pre); return@launch }
            gateway.setRequestConfiguration(childDirected = child, underAgeOfConsent = child)
            gateway.gatherConsent(activity, underAgeOfConsent = child) { ok ->
                consent = ok
                val d = policy.decide(AdTrigger.APP_ENTRY, e, child, null, first, ok, lastShownAt, clock(), gateway.available)
                record(d)
                if (d != AdDecision.SHOW) return@gatherConsent
                gateway.initialize(app) {
                    val start = clock()
                    gateway.loadInterstitial(app, adUnitId) { loaded ->
                        // Açılış reklamı yalnız kısa sürede yüklendiyse (geç gelen reklam kullanıcıyı bölmesin)
                        if (loaded && clock() - start < 5_000 && !activity.isFinishing) show(activity) else preload()
                    }
                }
            }
        }
    }

    /** Tuval kapatıldı (çocuk tuvali hariç; [kind] bilinmiyorsa gösterilmez). */
    fun onCanvasClosed(activity: Activity, kind: CanvasKind?) {
        if (kind == null) return
        scope.launch {
            val e = loadedEntitlements()
            val child = AdsPolicy.isChild(user.value)
            val ok = consent || gateway.canRequestAds(app)
            val d = policy.decide(AdTrigger.CANVAS_CLOSED, e, child, kind, firstLaunch = false, consent = ok, lastShownAt = lastShownAt, now = clock(), available = gateway.available)
            record(d)
            if (d != AdDecision.SHOW) return@launch
            gateway.setRequestConfiguration(childDirected = false, underAgeOfConsent = false)
            gateway.initialize(app) {
                if (gateway.interstitialReady) show(activity) else preload()
            }
        }
    }

    private fun preload() {
        if (!gateway.available) return
        gateway.loadInterstitial(app, adUnitId) { }
    }

    private fun show(activity: Activity) {
        gateway.showInterstitial(activity) { shown ->
            if (shown) prefs.edit().putLong(KEY_LAST, clock()).apply()
            preload()
        }
    }

    private fun record(d: AdDecision) {
        lastDecision = d
        AppLog.i("Ads", "reklam kararı: $d")
    }

    private companion object {
        const val KEY_LAST = "last_shown_at"
        const val KEY_LAUNCHED = "launched_once"
    }
}
