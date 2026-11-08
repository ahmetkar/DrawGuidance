package com.karapp.drawguidance.data.ads

import android.app.Activity
import android.content.Context

/**
 * v39 reklamların uygulamadaki arayüzü (Google Mobile Ads ve UMP biçiminde). Gerçek uygulama [AdMobGateway];
 * testlerde ve reklamsız derlemede [NoOpAdsGateway] (Koin'de tek satırla değişir: bkz. di/AppModule.kt).
 * Uygulama yalnız geçiş reklamı (interstitial) kullanır: açılışta ve tuval kapatıldıktan sonra.
 */
interface AdsGateway {
    /** Bu derlemede reklam kütüphanesi var mı. */
    val available: Boolean

    /**
     * UMP: onay bilgisi güncellenir, gerekiyorsa onay penceresi gösterilir ([underAgeOfConsent]: çocuk hesabı).
     * [onDone] reklam istenebilir mi (ConsentInformation.canRequestAds) bilgisiyle çağrılır.
     */
    fun gatherConsent(activity: Activity, underAgeOfConsent: Boolean, onDone: (canRequestAds: Boolean) -> Unit)

    /** ConsentInformation.canRequestAds (daha önce alınan onay). */
    fun canRequestAds(context: Context): Boolean

    /** MobileAds.setRequestConfiguration: çocuklara yönelik işleme (COPPA) ve onay yaşının altı (GDPR) etiketleri. */
    fun setRequestConfiguration(childDirected: Boolean, underAgeOfConsent: Boolean)

    /** MobileAds.initialize (bir kez). */
    fun initialize(context: Context, onComplete: () -> Unit)

    /** InterstitialAd.load: [onResult] yüklendi mi. */
    fun loadInterstitial(context: Context, adUnitId: String, onResult: (loaded: Boolean) -> Unit)

    /** Yüklenmiş ve gösterilmemiş geçiş reklamı var mı. */
    val interstitialReady: Boolean

    /** InterstitialAd.show: [onFinished] reklam gösterildiyse true (kapatılınca), gösterilemediyse false. */
    fun showInterstitial(activity: Activity, onFinished: (shown: Boolean) -> Unit)
}

/** v39 reklamsız uygulama (testler, kütüphanesiz derleme): hiçbir reklam yüklenmez ya da gösterilmez. */
class NoOpAdsGateway : AdsGateway {
    override val available: Boolean = false
    override fun gatherConsent(activity: Activity, underAgeOfConsent: Boolean, onDone: (Boolean) -> Unit) = onDone(false)
    override fun canRequestAds(context: Context): Boolean = false
    override fun setRequestConfiguration(childDirected: Boolean, underAgeOfConsent: Boolean) {}
    override fun initialize(context: Context, onComplete: () -> Unit) = onComplete()
    override fun loadInterstitial(context: Context, adUnitId: String, onResult: (Boolean) -> Unit) = onResult(false)
    override val interstitialReady: Boolean = false
    override fun showInterstitial(activity: Activity, onFinished: (Boolean) -> Unit) = onFinished(false)
}
