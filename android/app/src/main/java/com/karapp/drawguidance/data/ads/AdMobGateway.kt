package com.karapp.drawguidance.data.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * v39 Google Mobile Ads (AdMob) ve UMP ile gerçek geçiş reklamları. Uygulama kimliği manifestte
 * (`com.google.android.gms.ads.APPLICATION_ID`, derleme türüne göre), reklam birimi BuildConfig.ADMOB_INTERSTITIAL_ID.
 * Reklam yalnız UMP onayından sonra (canRequestAds) istenir; içerik derecesi en çok "G" (genel izleyici).
 */
class AdMobGateway : AdsGateway {
    override val available: Boolean = true
    private val initialized = AtomicBoolean(false)
    @Volatile private var interstitial: InterstitialAd? = null
    @Volatile private var loading = false

    override fun gatherConsent(activity: Activity, underAgeOfConsent: Boolean, onDone: (Boolean) -> Unit) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(underAgeOfConsent).build()
        info.requestConsentInfoUpdate(
            activity, params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ -> onDone(info.canRequestAds()) }
            },
            { _ -> onDone(info.canRequestAds()) },
        )
    }

    override fun canRequestAds(context: Context): Boolean =
        runCatching { UserMessagingPlatform.getConsentInformation(context).canRequestAds() }.getOrDefault(false)

    override fun setRequestConfiguration(childDirected: Boolean, underAgeOfConsent: Boolean) {
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTagForChildDirectedTreatment(
                    if (childDirected) RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE else RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED,
                )
                .setTagForUnderAgeOfConsent(
                    if (underAgeOfConsent) RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE else RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED,
                )
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .build(),
        )
    }

    override fun initialize(context: Context, onComplete: () -> Unit) {
        if (!initialized.compareAndSet(false, true)) { onComplete(); return }
        MobileAds.initialize(context.applicationContext) { onComplete() }
    }

    override val interstitialReady: Boolean get() = interstitial != null

    override fun loadInterstitial(context: Context, adUnitId: String, onResult: (Boolean) -> Unit) {
        if (interstitial != null) { onResult(true); return }
        if (loading) { onResult(false); return }
        loading = true
        InterstitialAd.load(context, adUnitId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                loading = false
                interstitial = ad
                onResult(true)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                loading = false
                interstitial = null
                onResult(false)
            }
        })
    }

    override fun showInterstitial(activity: Activity, onFinished: (Boolean) -> Unit) {
        val ad = interstitial ?: run { onFinished(false); return }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = onFinished(true)
            override fun onAdFailedToShowFullScreenContent(error: AdError) = onFinished(false)
        }
        ad.show(activity)
    }
}
