package com.karapp.drawguidance.data.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.security.MessageDigest

/*
 * v39 Google Play Billing'in uygulamadaki arayüzü. Türler Play Billing Library'nin (7/8) adlarıyla aynı biçimdedir;
 * gerçek uygulama [PlayBillingGateway], testlerde sahte uygulama, kütüphane olmayan derlemede
 * [UnavailableBillingGateway] kullanılır (Koin'de tek satırla değişir: bkz. di/AppModule.kt).
 */

/** BillingClient.BillingResponseCode değerleri. */
object BillingResponseCode {
    const val SERVICE_DISCONNECTED = -1
    const val FEATURE_NOT_SUPPORTED = -2
    const val OK = 0
    const val USER_CANCELED = 1
    const val SERVICE_UNAVAILABLE = 2
    const val BILLING_UNAVAILABLE = 3
    const val ITEM_UNAVAILABLE = 4
    const val DEVELOPER_ERROR = 5
    const val ERROR = 6
    const val ITEM_ALREADY_OWNED = 7
    const val ITEM_NOT_OWNED = 8
    const val NETWORK_ERROR = 12
}

/** Purchase.PurchaseState değerleri. */
object PurchaseState {
    const val UNSPECIFIED_STATE = 0
    const val PURCHASED = 1
    const val PENDING = 2
}

/** BillingResult. */
data class BillingResult(val responseCode: Int, val debugMessage: String = "") {
    val ok: Boolean get() = responseCode == BillingResponseCode.OK
}

/** ProductDetails.PricingPhase. */
data class PricingPhase(
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
    /** ISO 8601 (P1M). */
    val billingPeriod: String,
    val billingCycleCount: Int = 0,
    val recurrenceMode: Int = 1,
)

/** ProductDetails.SubscriptionOfferDetails. */
data class SubscriptionOfferDetails(
    val basePlanId: String,
    val offerId: String?,
    val offerToken: String,
    val pricingPhases: List<PricingPhase>,
    val offerTags: List<String> = emptyList(),
) {
    /** Yinelenen (son) fiyat aşaması: abonelik ücreti. */
    val recurringPhase: PricingPhase? get() = pricingPhases.lastOrNull()
}

/** ProductDetails (abonelik). [native]: gerçek kütüphanenin nesnesi (satın alma akışına aynen verilir). */
data class ProductDetails(
    val productId: String,
    val productType: String,
    val title: String,
    val name: String,
    val description: String,
    val subscriptionOfferDetails: List<SubscriptionOfferDetails>,
    val native: Any? = null,
) {
    /** Temel planın teklifsiz (offerId == null) seçeneği; yoksa ilk seçenek. */
    fun basePlanOffer(basePlanId: String): SubscriptionOfferDetails? =
        subscriptionOfferDetails.filter { it.basePlanId == basePlanId }.let { list -> list.firstOrNull { it.offerId == null } ?: list.firstOrNull() }
}

/** Purchase. */
data class Purchase(
    val orderId: String?,
    val packageName: String,
    val products: List<String>,
    val purchaseToken: String,
    val purchaseState: Int,
    val purchaseTime: Long,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean,
    val obfuscatedAccountId: String? = null,
)

data class ProductDetailsResult(val billingResult: BillingResult, val productDetailsList: List<ProductDetails>)
data class PurchasesResult(val billingResult: BillingResult, val purchasesList: List<Purchase>)

/** PurchasesUpdatedListener'ın bildirdiği güncelleme. */
data class PurchasesUpdate(val billingResult: BillingResult, val purchases: List<Purchase>)

/** BillingFlowParams (tek abonelik ürünü). */
data class BillingFlowParams(
    val productDetails: ProductDetails,
    val offerToken: String,
    /** Firebase uid'sinin SHA-256 özeti (küçük harfli 64 onaltılık; bkz. [obfuscatedAccountId]). */
    val obfuscatedAccountId: String,
)

/** v39 Google Play Billing arayüzü (Play Billing Library biçiminde). */
interface BillingGateway {
    /** Bu derlemede Google Play mağazası kullanılabilir mi (false: satın alma kapalı, nedeni arayüzde yazılır). */
    val storeAvailable: Boolean

    /** BillingClient.startConnection (bağlıysa hemen OK). */
    suspend fun connect(): BillingResult

    /** BillingClient.queryProductDetails (abonelik ürünleri; fiyatlar kullanıcının ülkesinin para biriminde). */
    suspend fun queryProductDetails(productIds: List<String>): ProductDetailsResult

    /** BillingClient.launchBillingFlow: Play'in satın alma penceresi açılır; sonuç [purchaseUpdates] ile gelir. */
    fun launchBillingFlow(activity: Activity, params: BillingFlowParams): BillingResult

    /** PurchasesUpdatedListener (satın alma sonuçları). */
    val purchaseUpdates: Flow<PurchasesUpdate>

    /** BillingClient.queryPurchasesAsync(SUBS): cihazdaki Google hesabının geçerli abonelikleri. */
    suspend fun queryPurchases(): PurchasesResult

    /** BillingClient.acknowledgePurchase (sunucu onaylamadıysa yedek). */
    suspend fun acknowledgePurchase(purchaseToken: String): BillingResult

    /**
     * Google Play'in abonelik yönetimi sayfası (iptal, ödeme yöntemi, plan): kütüphane gerekmez, bağlantıyla açılır.
     * [productId] null ise bütün abonelikler.
     */
    fun openSubscriptionManagement(context: Context, productId: String?): Boolean {
        val uri = manageSubscriptionsUri(context.packageName, productId)
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    companion object {
        fun manageSubscriptionsUri(packageName: String, productId: String?): Uri =
            if (productId == null) Uri.parse("https://play.google.com/store/account/subscriptions")
            else Uri.parse("https://play.google.com/store/account/subscriptions?sku=${Uri.encode(productId)}&package=${Uri.encode(packageName)}")

        /** Firebase uid → SHA-256 (UTF-8), küçük harfli 64 onaltılık (sunucu aynı değeri bekler). */
        fun obfuscatedAccountId(uid: String): String =
            MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/**
 * v39 Google Play'in olmadığı derleme (ör. kütüphanesiz test derlemesi): hiçbir satın alma yapılmaz, her istek
 * dürüstçe "bu derlemede mağaza yok" döner. Asla başarılı satın alma uydurmaz.
 */
class UnavailableBillingGateway : BillingGateway {
    private val unavailable = BillingResult(BillingResponseCode.BILLING_UNAVAILABLE, "Store unavailable in this build")
    override val storeAvailable: Boolean = false
    override suspend fun connect(): BillingResult = unavailable
    override suspend fun queryProductDetails(productIds: List<String>) = ProductDetailsResult(unavailable, emptyList())
    override fun launchBillingFlow(activity: Activity, params: BillingFlowParams): BillingResult = unavailable
    override val purchaseUpdates: Flow<PurchasesUpdate> = emptyFlow()
    override suspend fun queryPurchases() = PurchasesResult(unavailable, emptyList())
    override suspend fun acknowledgePurchase(purchaseToken: String): BillingResult = unavailable
}
