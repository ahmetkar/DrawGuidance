package com.karapp.drawguidance.data.billing

import android.app.Activity
import android.content.Context
import com.karapp.drawguidance.data.account.AuthProvider
import com.karapp.drawguidance.data.local.BillingDao
import com.karapp.drawguidance.data.local.FeatureEntity
import com.karapp.drawguidance.data.local.LocalUserDao
import com.karapp.drawguidance.data.local.PlanEntity
import com.karapp.drawguidance.data.local.PlanFeatureEntity
import com.karapp.drawguidance.data.local.SubscriptionEntity
import com.karapp.drawguidance.data.remote.v1.RemoteException
import com.karapp.drawguidance.data.remote.v1.RemoteStore
import com.karapp.drawguidance.data.remote.v1.RemoteSubscription
import com.karapp.drawguidance.data.remote.v1.VerifyBody
import com.karapp.drawguidance.data.sync.Connectivity
import com.karapp.drawguidance.diagnostics.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** v39 abonelik sayfasında gösterilen sonuç iletileri. */
enum class BillingMessage {
    NOT_SIGNED_IN, STORE_UNAVAILABLE, PRODUCT_UNAVAILABLE, CANCELED, PENDING, VERIFIED, SERVER_NOT_CONFIGURED,
    VERIFY_FAILED, OFFLINE, NOTHING_TO_RESTORE, RESTORED, REFRESHED, ALREADY_OWNED, ERROR,
}

/** v39 plan kartının içeriği: plan, hakları ve (yüklendiyse) Play'in yerel fiyatı. */
data class PlanUi(val plan: PlanEntity, val features: List<String>)

/** v39 abonelik işlemlerinin durumu. */
data class BillingState(
    val busy: Boolean = false,
    val message: BillingMessage? = null,
    /** Play'den gelen ürün (yerel fiyat); yüklenmediyse null. */
    val product: ProductDetails? = null,
    val storeAvailable: Boolean = false,
)

/**
 * v39 abonelik: planlar (yerelde tohumlanır, bağlantı varken sunucudan güncellenir), kullanıcının aboneliği
 * (`subscriptions` tablosu; ilk girişte Free kaydı, satın almada, doğrulamada ve açılışta sunucudan güncellenir) ve
 * erişim hakları ([entitlements], kapalı kalacak şekilde).
 *
 * Satın alma yalnız giriş yapılmışken başlar; Play'in satın alma sonucu sunucuda doğrulanmadan hak verilmez (sunucu
 * Play yapılandırılmadığını bildirirse [BillingMessage.SERVER_NOT_CONFIGURED]). Çıkışta haklar Free'ye döner.
 */
class SubscriptionRepository(
    private val dao: BillingDao,
    private val users: LocalUserDao,
    private val auth: AuthProvider,
    private val remote: RemoteStore?,
    private val connectivity: Connectivity,
    val billing: BillingGateway,
    private val packageName: String,
    /** Testlerde elle verilen haklar (null değerde gerçek abonelik). */
    private val override: StateFlow<Entitlements?>? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = System::currentTimeMillis,
    private val tickMs: Long = 60_000,
) {
    private val _state = MutableStateFlow(BillingState(storeAvailable = billing.storeAvailable))
    val state: StateFlow<BillingState> = _state.asStateFlow()

    private val ticker = flow { while (true) { emit(clock()); delay(tickMs) } }

    private val signedInUser = users.observe().map { u -> u?.takeIf { it.signedIn }?.let { it.firebaseUid to it.email?.takeIf { _ -> it.emailVerified } } }.distinctUntilChanged()
    private val signedInUid = signedInUser.map { it?.first }.distinctUntilChanged()

    private val computed: StateFlow<Entitlements> = combine(signedInUid, dao.observeSubscriptions(), dao.observePlanFeatures(), ticker) { uid, subs, pf, now ->
        Entitlements.compute(uid, subs.firstOrNull { it.firebaseUid == uid }, pf, now)
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, Entitlements())

    /** Kullanıcının o anki hakları (yüklenmeden önce Free, [Entitlements.loaded] false). */
    val entitlements: StateFlow<Entitlements> = if (override == null) computed else
        combine(computed, override) { real, forced -> forced ?: real }.stateIn(scope, SharingStarted.Eagerly, override.value ?: Entitlements())

    /** Plan kartları (sıralı). */
    val plans: StateFlow<List<PlanUi>> = combine(dao.observePlans(), dao.observePlanFeatures()) { plans, pf ->
        plans.map { p -> PlanUi(p, FeatureKeys.PREMIUM.filter { k -> pf.any { it.planId == p.id && it.featureKey == k } } + pf.filter { it.planId == p.id && it.featureKey !in FeatureKeys.PREMIUM }.map { it.featureKey }) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val signedIn: StateFlow<Boolean> = signedInUid.map { it != null }.stateIn(scope, SharingStarted.Eagerly, false)

    /** v40 girişli kullanıcının yerelde saklanan abonelik satırı (başlangıç, bitiş, yenileme, iptal; yoksa null). */
    val current: StateFlow<SubscriptionEntity?> = combine(signedInUid, dao.observeSubscriptions()) { uid, subs ->
        uid?.let { u -> subs.firstOrNull { it.firebaseUid == u } }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val lock = Mutex()
    private var started = false
    @Volatile private var lastReverifyAttempt = 0L

    /**
     * Açılışta bir kez: planlar tohumlanır, satın alma sonuçları dinlenir, giriş yapılınca abonelik güncellenir.
     * [network] false ise (eski testler) açılışta sunucuya gidilmez.
     */
    fun start(network: Boolean = true) {
        if (started) return
        started = true
        scope.launch { runCatching { seed() } }
        scope.launch { billing.purchaseUpdates.collect { onPurchasesUpdated(it) } }
        scope.launch {
            signedInUid.collect { uid ->
                if (uid != null) runCatching { onSignedIn(uid, network) }.onFailure { AppLog.w(TAG, "abonelik güncellenemedi: ${it.message}") }
            }
        }
        if (network) scope.launch { if (connectivity.online.value) runCatching { refreshPlans() } }
        // v50 bağlantı gelince ve uygulama açık kaldıkça doğrulama tazelenir (iade/iptal kaçmasın; çevrimdışı hak
        // [Entitlements.OFFLINE_GRACE_MS] ile sınırlı olduğundan Premium kullanıcının hakkı da böylece kesilmez)
        if (network) scope.launch {
            combine(signedInUid, connectivity.online, ticker) { uid, online, now -> Triple(uid, online, now) }.collect { (uid, online, now) ->
                if (uid == null || !online) return@collect
                val verifiedAt = dao.subscription(uid)?.verifiedAt
                if (now - lastReverifyAttempt < REVERIFY_RETRY_MS) return@collect
                if (verifiedAt == null || now - verifiedAt > REVERIFY_AFTER_MS) { lastReverifyAttempt = now; runCatching { refresh() } }
            }
        }
    }

    /** Uygulamayla gelen planlar (Free ve Premium 100 TL/ay) veritabanında yoksa yazılır. */
    suspend fun seed() {
        if (dao.planCount() > 0) return
        val now = clock()
        dao.replaceCatalog(BundledPlans.plans(now), BundledPlans.planFeatures(), BundledPlans.features())
    }

    /** Sunucudaki planlar ve hakları (boş ya da hatalı yanıtta yerel kayıt korunur). */
    suspend fun refreshPlans(): Boolean {
        val r = remote ?: return false
        val body = runCatching { r.plans() }.getOrElse { AppLog.w(TAG, "planlar alınamadı: ${it.message}"); return false }
        if (body.plans.isEmpty()) return false
        val now = clock()
        val plans = body.plans.map { p ->
            PlanEntity(
                id = p.id, name = p.name.ifBlank { p.id }, description = p.description, productId = p.productId, basePlanId = p.basePlanId,
                priceMicros = p.price?.amountMicros ?: 0, currency = p.price?.currency ?: "TRY", period = p.period.orEmpty(),
                sortOrder = p.sort, source = "remote", updatedAt = now,
            )
        }
        val pf = body.plans.flatMap { p -> p.features.map { PlanFeatureEntity(p.id, it) } }
        val keys = (body.featureKeys + pf.map { it.featureKey }).distinct()
        dao.replaceCatalog(plans, pf, keys.mapIndexed { i, k -> FeatureEntity(k, FeatureKeys.modeOf(k), i) })
        return true
    }

    /** İlk girişte Free kaydı; sonra sunucudaki abonelik ve cihazdaki Play satın almaları. */
    private suspend fun onSignedIn(uid: String, network: Boolean = true) {
        if (dao.subscription(uid) == null) dao.upsertSubscription(freeRow(uid, "local"))
        if (!network || !connectivity.online.value) return
        refresh()
        // Cihazda doğrulanmamış (ör. uygulama kapanırken yarım kalan) satın alma varsa doğrulanır
        if (billing.storeAvailable) runCatching { verifyPending(onlyUnknown = true) }
    }

    /** Sunucudaki abonelik (`GET /subscriptions/me`) tabloya yazılır. */
    suspend fun refresh(): BillingMessage = lock.withLock {
        val uid = currentUid() ?: return@withLock BillingMessage.NOT_SIGNED_IN
        val r = remote ?: return@withLock BillingMessage.OFFLINE
        if (!connectivity.online.value) return@withLock BillingMessage.OFFLINE
        try {
            val sub = withToken { r.mySubscription(it) }
            store(uid, sub, purchaseToken = dao.subscription(uid)?.purchaseToken)
            BillingMessage.REFRESHED
        } catch (e: Exception) {
            AppLog.w(TAG, "abonelik alınamadı: ${e.message}")
            BillingMessage.ERROR
        }
    }

    /** Play'den ürünün yerel fiyatı (ülkenin para biriminde). */
    suspend fun loadProduct(): ProductDetails? {
        if (!billing.storeAvailable) return null
        val r = billing.queryProductDetails(listOf(PREMIUM_PRODUCT_ID))
        val p = r.productDetailsList.firstOrNull { it.productId == PREMIUM_PRODUCT_ID }
        _state.value = _state.value.copy(product = p)
        return p
    }

    /**
     * "Premium'a geç": giriş yapılmamışsa [BillingMessage.NOT_SIGNED_IN], mağaza yoksa [BillingMessage.STORE_UNAVAILABLE];
     * yoksa Play'in satın alma penceresi açılır (null: açıldı, sonuç ayrıca gelir).
     */
    suspend fun purchase(activity: Activity): BillingMessage? {
        val uid = currentUid() ?: return message(BillingMessage.NOT_SIGNED_IN)
        if (!billing.storeAvailable) return message(BillingMessage.STORE_UNAVAILABLE)
        val product = _state.value.product ?: loadProduct() ?: return message(BillingMessage.PRODUCT_UNAVAILABLE)
        val offer = product.basePlanOffer(PREMIUM_BASE_PLAN_ID) ?: product.subscriptionOfferDetails.firstOrNull()
            ?: return message(BillingMessage.PRODUCT_UNAVAILABLE)
        val result = billing.launchBillingFlow(activity, BillingFlowParams(product, offer.offerToken, BillingGateway.obfuscatedAccountId(uid)))
        return when (result.responseCode) {
            BillingResponseCode.OK -> { _state.value = _state.value.copy(busy = true, message = null); null }
            BillingResponseCode.USER_CANCELED -> message(BillingMessage.CANCELED)
            BillingResponseCode.ITEM_ALREADY_OWNED -> { restore(); message(BillingMessage.ALREADY_OWNED) }
            BillingResponseCode.BILLING_UNAVAILABLE, BillingResponseCode.SERVICE_UNAVAILABLE -> message(BillingMessage.STORE_UNAVAILABLE)
            else -> message(BillingMessage.ERROR)
        }
    }

    /** "Satın alımları geri yükle": Play'deki geçerli abonelikler sunucuda yeniden doğrulanır, sonra abonelik alınır. */
    suspend fun restore(): BillingMessage {
        if (currentUid() == null) return message(BillingMessage.NOT_SIGNED_IN)
        if (!billing.storeAvailable) return message(BillingMessage.STORE_UNAVAILABLE)
        _state.value = _state.value.copy(busy = true)
        val m = verifyPending(onlyUnknown = false) ?: refresh().let { if (it == BillingMessage.REFRESHED) BillingMessage.NOTHING_TO_RESTORE else it }
        return message(m)
    }

    /** Google Play'in abonelik yönetimi sayfası. */
    fun manage(context: Context): Boolean = billing.openSubscriptionManagement(context, PREMIUM_PRODUCT_ID)

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    private suspend fun verifyPending(onlyUnknown: Boolean): BillingMessage? {
        val r = billing.queryPurchases()
        if (!r.billingResult.ok) return BillingMessage.STORE_UNAVAILABLE
        val uid = currentUid() ?: return BillingMessage.NOT_SIGNED_IN
        val known = dao.subscription(uid)?.purchaseToken
        val list = r.purchasesList.filter { it.purchaseState == PurchaseState.PURCHASED && PREMIUM_PRODUCT_ID in it.products }
            .filter { !onlyUnknown || it.purchaseToken != known }
        if (list.isEmpty()) return null
        var last: BillingMessage? = null
        for (p in list) last = verify(p)
        return if (last == BillingMessage.VERIFIED) BillingMessage.RESTORED else last
    }

    private suspend fun onPurchasesUpdated(update: PurchasesUpdate) {
        val m = when (update.billingResult.responseCode) {
            BillingResponseCode.OK -> {
                var last: BillingMessage = BillingMessage.ERROR
                for (p in update.purchases) {
                    last = when (p.purchaseState) {
                        PurchaseState.PURCHASED -> verify(p)
                        PurchaseState.PENDING -> BillingMessage.PENDING
                        else -> BillingMessage.ERROR
                    }
                }
                last
            }
            BillingResponseCode.USER_CANCELED -> BillingMessage.CANCELED
            BillingResponseCode.ITEM_ALREADY_OWNED -> verifyPending(onlyUnknown = false) ?: BillingMessage.ALREADY_OWNED
            else -> BillingMessage.ERROR
        }
        message(m)
    }

    /** Satın alma sunucuda doğrulanır (hak yalnız sunucunun yanıtıyla açılır). */
    private suspend fun verify(p: Purchase): BillingMessage = lock.withLock {
        val uid = currentUid() ?: return@withLock BillingMessage.NOT_SIGNED_IN
        val r = remote ?: return@withLock BillingMessage.OFFLINE
        if (!connectivity.online.value) return@withLock BillingMessage.OFFLINE
        try {
            val product = p.products.firstOrNull { it == PREMIUM_PRODUCT_ID } ?: p.products.first()
            val sub = withToken { r.verifySubscription(it, VerifyBody(p.packageName.ifEmpty { packageName }, product, p.purchaseToken)) }
            store(uid, sub, p.purchaseToken)
            // Sunucu onaylamadıysa (yedek) satın alma Play'de onaylanır: 3 gün içinde onaylanmayan iade edilir
            if (sub.acknowledged == false && !p.isAcknowledged) runCatching { billing.acknowledgePurchase(p.purchaseToken) }
            if (sub.active) BillingMessage.VERIFIED else BillingMessage.VERIFY_FAILED
        } catch (e: RemoteException) {
            AppLog.w(TAG, "doğrulama: ${e.status} ${e.code}")
            if (e.status == 503 && e.code == "billing_not_configured") BillingMessage.SERVER_NOT_CONFIGURED else BillingMessage.VERIFY_FAILED
        } catch (e: Exception) {
            AppLog.w(TAG, "doğrulama başarısız: ${e.message}")
            BillingMessage.VERIFY_FAILED
        }
    }

    private suspend fun store(uid: String, sub: RemoteSubscription, purchaseToken: String?) {
        val now = clock()
        val status = sub.status?.let { SubscriptionStatuses.normalize(it) } ?: SubscriptionStatuses.NONE
        dao.upsertSubscription(
            SubscriptionEntity(
                firebaseUid = uid, planId = if (sub.status == null) PlanIds.FREE else sub.planId.ifBlank { PlanIds.FREE },
                status = status, productId = sub.productId, basePlanId = sub.basePlanId,
                startTime = RemoteSubscription.time(sub.startTime), expiryTime = RemoteSubscription.time(sub.expiryTime),
                autoRenewing = sub.autoRenewing ?: false, acknowledged = sub.acknowledged ?: false, orderId = sub.orderId,
                purchaseToken = purchaseToken, features = if (sub.active) sub.features.joinToString(",") else "",
                source = "server", verifiedAt = now, updatedAt = now,
                // v40 iptal ve duraklatma ayrıntıları (abonelik sayfasında gösterilir)
                cancelTime = RemoteSubscription.time(sub.cancelTime), cancelReason = sub.cancelReason,
                autoResumeTime = RemoteSubscription.time(sub.autoResumeTime),
            ),
        )
    }

    private fun freeRow(uid: String, source: String): SubscriptionEntity {
        val now = clock()
        return SubscriptionEntity(
            firebaseUid = uid, planId = PlanIds.FREE, status = SubscriptionStatuses.NONE, productId = null, basePlanId = null,
            startTime = null, expiryTime = null, autoRenewing = false, acknowledged = false, orderId = null, purchaseToken = null,
            features = "", source = source, verifiedAt = null, updatedAt = now,
        )
    }

    private suspend fun currentUid(): String? = users.get()?.takeIf { it.signedIn }?.firebaseUid

    private suspend fun <T> withToken(block: suspend (String) -> T): T {
        val token = auth.idToken(false) ?: throw IllegalStateException("Oturum jetonu alınamadı")
        return try {
            block(token)
        } catch (e: RemoteException) {
            if (!e.isUnauthorized) throw e
            block(auth.idToken(true) ?: throw e)
        }
    }

    private fun message(m: BillingMessage): BillingMessage {
        _state.value = _state.value.copy(busy = false, message = m)
        return m
    }

    companion object {
        private const val TAG = "Subscription"

        /** v50 sunucu doğrulaması bu süreden eskiyse bağlantı varken yenilenir (12 saat). */
        const val REVERIFY_AFTER_MS: Long = 12L * 60 * 60 * 1000

        /** v50 başarısız yenilemeden sonra yeniden deneme aralığı (15 dakika). */
        const val REVERIFY_RETRY_MS: Long = 15L * 60 * 1000
        val PREMIUM_PRODUCT_ID: String = com.karapp.drawguidance.BuildConfig.PLAY_PREMIUM_PRODUCT_ID
        val PREMIUM_BASE_PLAN_ID: String = com.karapp.drawguidance.BuildConfig.PLAY_PREMIUM_BASE_PLAN_ID
    }
}

/** v39 uygulamayla gelen planlar: Free ve Premium (100 TL/ay; Play'in yerel fiyatı gelince o gösterilir). */
object BundledPlans {
    const val PREMIUM_PRICE_MICROS = 100_000_000L
    const val PREMIUM_CURRENCY = "TRY"

    fun plans(now: Long) = listOf(
        PlanEntity(PlanIds.FREE, "Free", null, null, null, 0, PREMIUM_CURRENCY, "", 0, "bundled", now),
        PlanEntity(
            PlanIds.PREMIUM, "Premium", null, SubscriptionRepository.PREMIUM_PRODUCT_ID, SubscriptionRepository.PREMIUM_BASE_PLAN_ID,
            PREMIUM_PRICE_MICROS, PREMIUM_CURRENCY, "P1M", 1, "bundled", now,
        ),
    )

    fun planFeatures() = FeatureKeys.PREMIUM.map { PlanFeatureEntity(PlanIds.PREMIUM, it) }

    fun features() = FeatureKeys.PREMIUM.mapIndexed { i, k -> FeatureEntity(k, FeatureKeys.modeOf(k), i) }
}
