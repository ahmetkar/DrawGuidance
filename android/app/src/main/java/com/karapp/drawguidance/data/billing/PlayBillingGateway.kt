package com.karapp.drawguidance.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import com.android.billingclient.api.BillingFlowParams as PlayFlowParams
import com.android.billingclient.api.BillingResult as PlayResult
import com.android.billingclient.api.ProductDetails as PlayProductDetails
import com.android.billingclient.api.Purchase as PlayPurchase

/**
 * v39 Google Play Billing Library 8 ile gerçek abonelik. Bağlantı ilk istekte kurulur (kopunca kütüphane kendisi
 * yeniden bağlanır: enableAutoServiceReconnection); fiyatlar Play'in kullanıcının ülkesine göre verdiği yerel fiyattır.
 * Satın almanın doğrulanması ve onaylanması sunucudadır (bkz. SubscriptionRepository); burası yalnız Play ile konuşur.
 */
class PlayBillingGateway(context: Context) : BillingGateway {
    private val updates = MutableSharedFlow<PurchasesUpdate>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val purchaseUpdates: Flow<PurchasesUpdate> = updates.asSharedFlow()
    override val storeAvailable: Boolean = true

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> updates.tryEmit(PurchasesUpdate(result.toResult(), purchases.orEmpty().map { it.toPurchase() })) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val connecting = Mutex()

    override suspend fun connect(): BillingResult = connecting.withLock {
        if (client.isReady) return@withLock BillingResult(BillingResponseCode.OK)
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: PlayResult) {
                    if (cont.isActive) cont.resume(result.toResult())
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(BillingResult(BillingResponseCode.SERVICE_DISCONNECTED, "disconnected"))
                }
            })
        }
    }

    override suspend fun queryProductDetails(productIds: List<String>): ProductDetailsResult {
        val c = connect()
        if (!c.ok) return ProductDetailsResult(c, emptyList())
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            productIds.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build() },
        ).build()
        val r = client.queryProductDetails(params)
        return ProductDetailsResult(r.billingResult.toResult(), r.productDetailsList.orEmpty().map { it.toDetails() })
    }

    override fun launchBillingFlow(activity: Activity, params: BillingFlowParams): BillingResult {
        val native = params.productDetails.native as? PlayProductDetails
            ?: return BillingResult(BillingResponseCode.DEVELOPER_ERROR, "Product details were not loaded from Play")
        val flow = PlayFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(PlayFlowParams.ProductDetailsParams.newBuilder().setProductDetails(native).setOfferToken(params.offerToken).build()),
            )
            .setObfuscatedAccountId(params.obfuscatedAccountId)
            .build()
        return client.launchBillingFlow(activity, flow).toResult()
    }

    override suspend fun queryPurchases(): PurchasesResult {
        val c = connect()
        if (!c.ok) return PurchasesResult(c, emptyList())
        val r = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        return PurchasesResult(r.billingResult.toResult(), r.purchasesList.map { it.toPurchase() })
    }

    override suspend fun acknowledgePurchase(purchaseToken: String): BillingResult {
        val c = connect()
        if (!c.ok) return c
        return client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build()).toResult()
    }

    private companion object {
        fun PlayResult.toResult() = BillingResult(responseCode, debugMessage)

        fun PlayPurchase.toPurchase() = Purchase(
            orderId = orderId, packageName = packageName, products = products, purchaseToken = purchaseToken,
            purchaseState = purchaseState, purchaseTime = purchaseTime, isAcknowledged = isAcknowledged,
            isAutoRenewing = isAutoRenewing, obfuscatedAccountId = accountIdentifiers?.obfuscatedAccountId,
        )

        fun PlayProductDetails.toDetails() = ProductDetails(
            productId = productId, productType = productType, title = title, name = name, description = description,
            subscriptionOfferDetails = subscriptionOfferDetails.orEmpty().map { o ->
                SubscriptionOfferDetails(
                    basePlanId = o.basePlanId, offerId = o.offerId, offerToken = o.offerToken, offerTags = o.offerTags,
                    pricingPhases = o.pricingPhases.pricingPhaseList.map { p ->
                        PricingPhase(p.formattedPrice, p.priceAmountMicros, p.priceCurrencyCode, p.billingPeriod, p.billingCycleCount, p.recurrenceMode)
                    },
                )
            },
            native = this,
        )
    }
}
