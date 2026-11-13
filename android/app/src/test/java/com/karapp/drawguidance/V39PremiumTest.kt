package com.karapp.drawguidance

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.ads.AdDecision
import com.karapp.drawguidance.data.ads.AdTrigger
import com.karapp.drawguidance.data.ads.AdsPolicy
import com.karapp.drawguidance.data.billing.BillingFlowParams
import com.karapp.drawguidance.data.billing.BillingGateway
import com.karapp.drawguidance.data.billing.BillingMessage
import com.karapp.drawguidance.data.billing.BillingResponseCode
import com.karapp.drawguidance.data.billing.BillingResult
import com.karapp.drawguidance.data.billing.Entitlements
import com.karapp.drawguidance.data.billing.FeatureKeys
import com.karapp.drawguidance.data.billing.PlanIds
import com.karapp.drawguidance.data.billing.PremiumFeature
import com.karapp.drawguidance.data.billing.ProductDetailsResult
import com.karapp.drawguidance.data.billing.Purchase
import com.karapp.drawguidance.data.billing.PurchaseState
import com.karapp.drawguidance.data.billing.PurchasesResult
import com.karapp.drawguidance.data.billing.PurchasesUpdate
import com.karapp.drawguidance.data.billing.SubscriptionRepository
import com.karapp.drawguidance.data.billing.SubscriptionStatuses
import com.karapp.drawguidance.data.challenge.ChallengeProgressRepository
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.local.BillingDao
import com.karapp.drawguidance.data.local.ChallengeProgressDao
import com.karapp.drawguidance.data.local.ChallengeProgressEntity
import com.karapp.drawguidance.data.local.LocalUserDao
import com.karapp.drawguidance.data.local.LocalUserEntity
import com.karapp.drawguidance.data.local.PlanFeatureEntity
import com.karapp.drawguidance.data.local.SubscriptionEntity
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.remote.v1.RemoteStore
import com.karapp.drawguidance.data.settings.KidsPrefs
import com.karapp.drawguidance.data.settings.ServerConfig
import com.karapp.drawguidance.ui.premium.LocalPremium
import com.karapp.drawguidance.ui.premium.PremiumAccess
import com.karapp.drawguidance.ui.premium.PremiumScope
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeLaunch
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeLevels
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeScore
import com.karapp.drawguidance.ui.screens.home.ChallengeHomeCard
import com.karapp.drawguidance.ui.screens.home.HomeV39
import com.karapp.drawguidance.ui.screens.home.PremiumHomeCard
import com.karapp.drawguidance.ui.screens.subscription.SubscriptionScreen
import com.karapp.drawguidance.ui.screens.subscription.SubscriptionViewModel
import com.karapp.drawguidance.ui.theme.DrawGuidanceTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

private const val UID = "uid-ahmet"
private const val DAY = 86_400_000L

private fun sub(status: String, expiry: Long?, plan: String = PlanIds.PREMIUM, features: String = "", verifiedAt: Long? = 1_800_000_000_000L - DAY) = SubscriptionEntity(
    firebaseUid = UID, planId = plan, status = status, productId = "drawguidance_premium", basePlanId = "premium-monthly",
    startTime = 0, expiryTime = expiry, autoRenewing = true, acknowledged = true, orderId = null, purchaseToken = null,
    features = features, source = "server", verifiedAt = verifiedAt, updatedAt = 0,
)

private val planFeatures = FeatureKeys.PREMIUM.map { PlanFeatureEntity(PlanIds.PREMIUM, it) }

/** v39 erişim hakları: kapalı kalır (giriş yok, abonelik yok, süre doldu, beklemede), ek süre ve iptal bitişe kadar geçerli. */
class V39EntitlementsTest {
    private val now = 1_800_000_000_000L

    @Test fun failsClosedWithoutSignInOrSubscription() {
        assertFalse(Entitlements.compute(null, sub("ACTIVE", now + DAY), planFeatures, now).isPremium)
        assertFalse(Entitlements.compute(UID, null, planFeatures, now).isPremium)
        // Başka kullanıcının satırı
        assertFalse(Entitlements.compute("other", sub("ACTIVE", now + DAY), planFeatures, now).isPremium)
        // Bitiş zamanı yoksa hak yok
        assertFalse(Entitlements.compute(UID, sub("ACTIVE", null), planFeatures, now).isPremium)
        // Plan hakları ve sunucu hakları boşsa hak yok
        assertFalse(Entitlements.compute(UID, sub("ACTIVE", now + DAY), emptyList(), now).isPremium)
        assertFalse(Entitlements.FREE.has(FeatureKeys.ADS_NONE))
    }

    @Test fun expiryAndNonGrantingStatuses() {
        val expired = Entitlements.compute(UID, sub("ACTIVE", now - 1), planFeatures, now)
        assertFalse(expired.isPremium)
        assertEquals(SubscriptionStatuses.EXPIRED, expired.status)
        for (s in listOf("ON_HOLD", "PAUSED", "PENDING", "EXPIRED", "NONE")) {
            assertFalse(s, Entitlements.compute(UID, sub(s, now + DAY), planFeatures, now).isPremium)
        }
        // Süre geçince has() da kapanır
        val e = Entitlements.compute(UID, sub("ACTIVE", now + 1000), planFeatures, now)
        assertTrue(e.has(FeatureKeys.COMIC_TEMPLATES, now))
        assertFalse(e.has(FeatureKeys.COMIC_TEMPLATES, now + 1000))
    }

    @Test fun graceAndCanceledGrantUntilExpiry() {
        for (s in listOf("ACTIVE", "IN_GRACE_PERIOD", "CANCELED", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD")) {
            val e = Entitlements.compute(UID, sub(s, now + DAY), planFeatures, now)
            assertTrue(s, e.isPremium)
            assertTrue(e.adFree(now))
        }
        // Sunucunun hak listesi önceliklidir
        val only = Entitlements.compute(UID, sub("ACTIVE", now + DAY, features = FeatureKeys.MODEL3D_SUGGEST), planFeatures, now)
        assertEquals(setOf(FeatureKeys.MODEL3D_SUGGEST), only.features)
        assertFalse(only.adFree(now))
        // Free satırı hiçbir şey açmaz
        assertFalse(Entitlements.compute(UID, sub("ACTIVE", now + DAY, plan = PlanIds.FREE), planFeatures, now).isPremium)
    }

    @Test fun featureKeysPerMode() {
        val free = PremiumAccess(Entitlements.FREE, CanvasKind.ART) {}
        assertTrue(free.locked(PremiumFeature.IMAGE_BG_REMOVAL))
        assertFalse(free.locked(PremiumFeature.COMIC_TEMPLATES))
        val notes = PremiumAccess(Entitlements.FREE, CanvasKind.NOTES) {}
        assertTrue(notes.locked(PremiumFeature.IMAGE_BG_REMOVAL))
        assertFalse(notes.locked(PremiumFeature.COMIC_EFFECTS))
        // v40 şekil ekleme ve diyagramlar kilitsiz: özellikleri yok
        assertTrue(PremiumFeature.entries.none { it.name == "SHAPES_ADVANCED" || it.name == "DIAGRAMS_ADVANCED" })
        val comic = PremiumAccess(Entitlements.FREE, CanvasKind.COMIC) {}
        listOf(PremiumFeature.COMIC_TEMPLATES, PremiumFeature.COMIC_BACKGROUNDS, PremiumFeature.COMIC_CHARACTERS, PremiumFeature.COMIC_EFFECTS)
            .forEach { assertTrue(it.name, comic.locked(it)) }
        assertEquals(FeatureKeys.COMIC_EFFECTS_ADVANCED, PremiumFeature.IMAGE_EFFECTS_ADVANCED.key(CanvasKind.COMIC))
        // Çocuk modu ve basit modlar kilitlenmez (v40: çocuk modunda yalnız çizim görevinin son seviyeleri)
        val anyKind = setOf(PremiumFeature.MODEL3D_SUGGEST, PremiumFeature.MODEL3D_EXPORT, PremiumFeature.KIDS_CHALLENGE_ADVANCED)
        val kids = PremiumAccess(Entitlements.FREE, CanvasKind.KIDS) {}
        PremiumFeature.entries.filter { it !in anyKind }.forEach { assertFalse(it.name, kids.locked(it)) }
        assertTrue(kids.locked(PremiumFeature.KIDS_CHALLENGE_ADVANCED))
        assertEquals(FeatureKeys.KIDS_CHALLENGE_ADVANCED, PremiumFeature.KIDS_CHALLENGE_ADVANCED.key(CanvasKind.KIDS))
        CanvasKind.entries.filter { it.simple }.forEach { k ->
            PremiumFeature.entries.filter { it !in anyKind }.forEach { assertNull("$k $it", it.key(k)) }
        }
        val premium = PremiumAccess(Entitlements.premium(), CanvasKind.COMIC) {}
        PremiumFeature.entries.forEach { assertFalse(premium.locked(it)) }
        assertTrue(PremiumAccess(Entitlements.FREE, null) {}.locked(PremiumFeature.MODEL3D_SUGGEST))
        assertEquals(21, FeatureKeys.PREMIUM.size)
        assertEquals("kids", FeatureKeys.modeOf(FeatureKeys.KIDS_CHALLENGE_ADVANCED))
    }

    @Test fun obfuscatedAccountIdIsSha256Hex() {
        assertEquals("2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b", BillingGateway.obfuscatedAccountId("secret"))
    }

    @Test fun localizedPriceFallback() {
        assertEquals("100 TL", SubscriptionViewModel.formatPrice(100_000_000, "TRY", Locale.forLanguageTag("tr")))
        val en = SubscriptionViewModel.formatPrice(100_000_000, "TRY", Locale.US)!!
        assertTrue(en, en.contains("100"))
        assertNull(SubscriptionViewModel.formatPrice(0, "TRY"))
    }
}

/** v39 reklam kuralları. */
class V39AdsPolicyTest {
    private val p = AdsPolicy()
    private val now = 10_000_000L
    private fun d(trigger: AdTrigger = AdTrigger.CANVAS_CLOSED, e: Entitlements = Entitlements.FREE, child: Boolean = false, kind: CanvasKind? = CanvasKind.ART,
                  first: Boolean = false, consent: Boolean = true, last: Long? = null, available: Boolean = true) =
        p.decide(trigger, e, child, kind, first, consent, last, now, available)

    @Test fun rules() {
        assertEquals(AdDecision.SHOW, d())
        assertEquals(AdDecision.SHOW, d(trigger = AdTrigger.APP_ENTRY, kind = null))
        assertEquals(AdDecision.PREMIUM, d(e = Entitlements.premium()))
        assertEquals(AdDecision.NOT_LOADED_YET, d(e = Entitlements()))
        assertEquals(AdDecision.KIDS_CANVAS, d(kind = CanvasKind.KIDS))
        assertEquals(AdDecision.CHILD_USER, d(child = true))
        assertEquals(AdDecision.FIRST_LAUNCH, d(trigger = AdTrigger.APP_ENTRY, first = true, kind = null))
        assertEquals(AdDecision.NO_CONSENT, d(consent = false))
        assertEquals(AdDecision.FREQUENCY_CAP, d(last = now - 60_000))
        assertEquals(AdDecision.SHOW, d(last = now - 3 * 60_000))
        assertEquals(AdDecision.UNAVAILABLE, d(available = false))
        assertTrue(p.minIntervalMs >= 3 * 60_000)
    }

    @Test fun childUserByBirthDate() {
        val today = java.time.LocalDate.of(2026, 10, 20)
        fun u(birth: String?, signedIn: Boolean = true) = LocalUserEntity("u", UID, null, null, null, null, true, null, null, birth, signedIn, 0, 0)
        assertTrue(AdsPolicy.isChild(u("2015-01-01"), today))
        assertFalse(AdsPolicy.isChild(u("2010-01-01"), today))
        assertFalse(AdsPolicy.isChild(u("2015-01-01", signedIn = false), today))
        assertFalse(AdsPolicy.isChild(null, today))
    }
}

/** v39 Ana sayfa kartlarının kararları ve görev seviyesi. */
class V39HomeLogicTest {
    @Test fun premiumCardRouting() {
        assertEquals(HomeV39.PremiumTarget.SIGN_IN, HomeV39.premiumTarget(false))
        assertEquals(HomeV39.PremiumTarget.SUBSCRIPTION, HomeV39.premiumTarget(true))
        assertTrue(HomeV39.showPremiumCard(false))
        assertFalse(HomeV39.showPremiumCard(true))
    }

    @Test fun challengeCardOnlyForSignedInUnder13() {
        val y = java.time.LocalDate.now().year
        assertTrue(HomeV39.showChallengeCard(true, "${y - 8}-01-01"))
        assertFalse(HomeV39.showChallengeCard(false, "${y - 8}-01-01"))
        assertFalse(HomeV39.showChallengeCard(true, "${y - 20}-01-01"))
        assertFalse(HomeV39.showChallengeCard(true, null))
    }

    @Test fun nextLevelAndLaunch() {
        val pass = ChallengeScore.PASS
        assertEquals(0, ChallengeProgressRepository.nextLevel(emptyMap(), ChallengeLevels.count, pass))
        assertEquals(2, ChallengeProgressRepository.nextLevel(mapOf(0 to 90, 1 to 60, 2 to 40), ChallengeLevels.count, pass))
        // v40 50 seviye
        assertEquals(50, ChallengeLevels.count)
        assertEquals(49, ChallengeProgressRepository.nextLevel((0 until 50).associateWith { 100 }, 50, pass))
        assertEquals(15, ChallengeProgressRepository.nextLevel((0 until 15).associateWith { 100 }, 50, pass))
        assertEquals(2, ChallengeProgressRepository.passedCount(mapOf(0 to 90, 1 to 60, 2 to 40), pass))
        ChallengeLaunch.request("c1", 3)
        assertNull(ChallengeLaunch.take("other"))
        assertEquals(3, ChallengeLaunch.take("c1"))
        assertNull(ChallengeLaunch.take("c1"))
    }

    @Test fun mergeKeepsBest() {
        fun e(l: Int, s: Int, t: Long = 1, dirty: Boolean = false) = ChallengeProgressEntity(l, s, ChallengeScore.stars(s), t, dirty)
        val merged = ChallengeProgressRepository.merge(
            listOf(e(0, 80, dirty = true), e(1, 30, dirty = true), e(2, 50, 1, dirty = true)),
            listOf(e(0, 70), e(1, 90), e(2, 50, 5), e(3, 65)),
        ).associateBy { it.level }
        assertEquals(80, merged[0]!!.bestScore); assertTrue(merged[0]!!.dirty)
        assertEquals(90, merged[1]!!.bestScore); assertFalse(merged[1]!!.dirty)
        assertEquals(5L, merged[2]!!.updatedAt); assertFalse(merged[2]!!.dirty)
        assertEquals(65, merged[3]!!.bestScore)
    }
}

/** Satın alma sonucu elle verilen sahte Play (yalnız testte; uygulamada asla başarı uydurulmaz). */
class FakeBillingGateway(private val purchases: List<Purchase>) : BillingGateway {
    var acknowledged = 0
    override val storeAvailable = true
    override suspend fun connect() = BillingResult(BillingResponseCode.OK)
    override suspend fun queryProductDetails(productIds: List<String>) = ProductDetailsResult(BillingResult(BillingResponseCode.OK), emptyList())
    override fun launchBillingFlow(activity: android.app.Activity, params: BillingFlowParams) = BillingResult(BillingResponseCode.ERROR)
    override val purchaseUpdates: Flow<PurchasesUpdate> = emptyFlow()
    override suspend fun queryPurchases() = PurchasesResult(BillingResult(BillingResponseCode.OK), purchases)
    override suspend fun acknowledgePurchase(purchaseToken: String): BillingResult { acknowledged++; return BillingResult(BillingResponseCode.OK) }
}

/** v39 Room göçü 15→16, abonelik eşitleme (MockWebServer) ve çizim görevi ilerlemesi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V39DataTest {
    private val server = MockWebServer()
    private inline fun <reified T : Any> koin(): T = GlobalContext.get().get()
    private val requests = CopyOnWriteArrayList<String>()

    @Before fun setUp() { TestAccount.reset(); TestAccount.entitlements.value = null }
    @After fun stop() {
        TestAccount.reset()
        org.koin.core.context.stopKoin()
        server.shutdown()
    }

    private fun signIn(birth: String? = null) = runBlocking {
        TestAccount.auth.signedIn = FakeAuth.DEFAULT
        koin<LocalUserDao>().upsert(LocalUserEntity("u1", UID, "Ahmet", "Ahmet", "Kar", "a@x", true, null, null, birth, true, 1, 1))
    }

    private fun serve(routes: Map<String, (RecordedRequest) -> MockResponse>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?')
                requests += "${request.method} $path"
                return routes[path]?.invoke(request) ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        koin<ServerConfig>().update(server.url("/").toString())
    }

    @Test
    fun migration15to16CreatesTables() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "mig16-test.db"
        ctx.deleteDatabase(name)
        Room.databaseBuilder(ctx, AppDatabase::class.java, name).allowMainThreadQueries().build().apply {
            openHelper.writableDatabase.apply {
                listOf("plans", "features", "plan_features", "subscriptions", "challenge_progress").forEach { execSQL("DROP TABLE `$it`") }
                version = 15
            }
            close()
        }
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name).allowMainThreadQueries().addMigrations(*AppDatabase.ALL_MIGRATIONS).build()
        runBlocking {
            assertEquals(0, db.billingDao().planCount())
            db.billingDao().upsertSubscription(sub("ACTIVE", 5))
            assertEquals("ACTIVE", db.billingDao().subscription(UID)!!.status)
            db.challengeProgressDao().upsert(listOf(ChallengeProgressEntity(0, 70, 2, 1, true)))
            assertEquals(1, db.challengeProgressDao().count())
        }
        assertEquals(23, db.openHelper.readableDatabase.version)
        db.close()
        ctx.deleteDatabase(name)
    }

    @Test
    fun plansAndSubscriptionSyncFromServer() = runBlocking {
        val expiry = System.currentTimeMillis() + 10 * DAY
        serve(mapOf(
            "/api/v1/plans" to { _ -> MockResponse().setHeader("ETag", "\"p1\"").setBody(
                """{"plans":[{"id":"free","name":"Free","price":{"amountMicros":0,"currency":"TRY","display":"Ücretsiz"},"period":"P1M","sort":0,"features":[],"limits":{}},
                {"id":"premium","name":"Premium","productId":"drawguidance_premium","basePlanId":"premium-monthly","price":{"amountMicros":120000000,"currency":"TRY","display":"120 TL"},"period":"P1M","sort":1,"features":${FeatureKeys.PREMIUM.joinToString(",", "[", "]") { "\"$it\"" }},"limits":{}}],
                "featureKeys":${FeatureKeys.PREMIUM.joinToString(",", "[", "]") { "\"$it\"" }}}""") },
            "/api/v1/subscriptions/me" to { r ->
                assertEquals("Bearer fake-token", r.getHeader("Authorization"))
                MockResponse().setBody("""{"planId":"premium","status":"ACTIVE","active":true,"productId":"drawguidance_premium","basePlanId":"premium-monthly","startTime":"2026-10-01T00:00:00Z","expiryTime":$expiry,"autoRenewing":true,"acknowledged":true,"orderId":"GPA.1","features":["comic.templates","ads.none"],"limits":{},"updatedAt":"2026-10-20T00:00:00Z"}""")
            },
        ))
        val repo = koin<SubscriptionRepository>()
        repo.seed()
        assertTrue(repo.refreshPlans())
        val premium = withTimeout(5_000) { repo.plans.first { list -> list.any { it.plan.id == "premium" && it.plan.priceMicros == 120_000_000L } } }
        assertEquals(2, premium.size)
        // Giriş yoksa Free (kapalı)
        assertFalse(repo.entitlements.value.isPremium)
        signIn()
        assertEquals(BillingMessage.REFRESHED, repo.refresh())
        val e = withTimeout(5_000) { repo.entitlements.first { it.isPremium } }
        assertEquals(setOf("comic.templates", "ads.none"), e.features)
        assertEquals(expiry, e.expiryTime)
        assertTrue(e.autoRenewing)
        // Çıkışta Free
        koin<LocalUserDao>().upsert(koin<LocalUserDao>().get()!!.copy(signedIn = false))
        withTimeout(5_000) { repo.entitlements.first { !it.isPremium && !it.signedIn } }
        Unit
    }

    @Test
    fun verifyNotConfiguredKeepsFreeAndPending() = runBlocking {
        val bodies = CopyOnWriteArrayList<String>()
        serve(mapOf(
            "/api/v1/subscriptions/verify" to { r -> bodies += r.body.readUtf8(); MockResponse().setResponseCode(503).setBody("""{"error":"billing_not_configured","code":"billing_not_configured","message":"Play not configured"}""") },
            "/api/v1/subscriptions/me" to { _ -> MockResponse().setBody("""{"planId":"free","status":null,"active":false,"features":[]}""") },
        ))
        signIn()
        val purchase = Purchase("GPA.9", "com.karapp.drawguidance", listOf(SubscriptionRepository.PREMIUM_PRODUCT_ID), "tok-1", PurchaseState.PURCHASED, 1, false, true)
        val gateway = FakeBillingGateway(listOf(purchase))
        val repo = SubscriptionRepository(koin<BillingDao>(), koin(), TestAccount.auth, koin<RemoteStore>(), TestAccount.connectivity, gateway, "com.karapp.drawguidance",
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val m = repo.restore()
        assertEquals(BillingMessage.SERVER_NOT_CONFIGURED, m)
        assertTrue(bodies.single().contains("\"purchaseToken\":\"tok-1\""))
        assertTrue(bodies.single().contains("\"productId\":\"drawguidance_premium\""))
        // Sunucu doğrulamadan hak yok, satın alma onaylanmaz (sonra yeniden denenir)
        assertFalse(repo.entitlements.value.isPremium)
        assertEquals(0, gateway.acknowledged)
        assertTrue(koin<BillingDao>().subscription(UID)?.planId != PlanIds.PREMIUM)
    }

    @Test
    fun challengeMigratesKidsPrefsAndSyncsByMax() = runBlocking {
        val prefs = koin<KidsPrefs>()
        val ids = ChallengeLevels.all.map { it.id }
        prefs.recordChallenge(ids[0], 75)
        prefs.recordChallenge(ids[1], 40)
        prefs.challengeMigrated = false
        val dao = koin<ChallengeProgressDao>()
        val repo = ChallengeProgressRepository(dao, koin(), TestAccount.auth, koin<RemoteStore>(), null, prefs, { ids }, ChallengeScore::stars)
        repo.load()
        assertEquals(75, dao.get(0)!!.bestScore)
        assertTrue(prefs.challengeMigrated)
        // Taşıma bir kez
        prefs.recordChallenge(ids[2], 90)
        repo.load()
        assertNull(dao.get(2))
        assertEquals(mapOf(ids[0] to 75, ids[1] to 40), repo.best.let { withTimeout(5_000) { it.first { m -> m.size == 2 } } })

        val puts = CopyOnWriteArrayList<String>()
        serve(mapOf(
            "/api/v1/challenge/progress" to { r ->
                if (r.method == "PUT") puts += r.body.readUtf8()
                MockResponse().setBody("""{"levels":[{"level":0,"bestScore":75,"stars":2,"passed":true,"updatedAt":5},{"level":1,"bestScore":95,"stars":3,"passed":true,"updatedAt":6},{"level":4,"bestScore":61,"stars":1,"passed":true,"updatedAt":7}],"levelCount":50,"passScore":60,"passedCount":3,"totalStars":6,"updatedAt":7}""")
            },
        ))
        signIn()
        repo.sync()
        assertTrue(puts.any { it.contains("\"level\":0") && it.contains("\"bestScore\":75") })
        assertEquals(95, dao.get(1)!!.bestScore)
        assertEquals(61, dao.get(4)!!.bestScore)
        assertTrue(dao.dirty().isEmpty())
        // Yerelde daha iyi puan kalır
        assertEquals(80, repo.record(0, 80))
        assertEquals(80, repo.record(0, 10))
    }
}

/** v39 kilit arayüzü: taç, "Premium gerekli" penceresi, öneri gösterilir ama uygulanamaz; çocuk modunda kilit yok. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "tr-w411dp-h891dp-xxhdpi")
class V39GatingUiTest {
    @get:Rule val compose = createComposeRule()

    @After fun stop() { TestAccount.reset(); org.koin.core.context.stopKoin() }

    private fun fix(i: Int, parts: Int, vector: Boolean = false, solid: String? = null) =
        com.karapp.drawguidance.ui.screens.canvasedit.state.TemplateFix(
            "k$i", "Şablon $i", "c", 0.9f, com.karapp.drawguidance.content.shapes.MatchMode.entries.first(), emptySet(),
            androidx.compose.ui.geometry.Rect(0f, 0f, 10f, 10f), List(parts) { emptyList() }, solid = solid, vector = vector,
        )

    @Test
    fun suggestionShownButLockedOnFree() {
        var applied = -1
        var asked: PremiumFeature? = null
        val suggestion = com.karapp.drawguidance.ui.screens.canvasedit.state.ShapeSuggestion(listOf(fix(0, 6), fix(1, 1, vector = true, solid = "circle")), emptySet(), androidx.compose.ui.geometry.Rect.Zero)
        compose.setContent {
            DrawGuidanceTheme {
                CompositionLocalProvider(LocalPremium provides PremiumAccess(Entitlements.FREE, CanvasKind.ART) { asked = it }) {
                    com.karapp.drawguidance.ui.screens.canvasedit.components.ShapeSuggestionChip(suggestion, { applied = it }, {})
                }
            }
        }
        compose.onNodeWithTag("template_option_0_locked").performClick()
        assertEquals(-1, applied)
        assertEquals(PremiumFeature.SHAPE_SUGGEST_ADVANCED, asked)
        compose.onNodeWithTag("template_option_1").performClick()
        assertEquals(1, applied)
    }

    @Test
    fun comicTileLockedPerMode() {
        var picked = 0
        var asked = 0
        var kind by androidx.compose.runtime.mutableStateOf(CanvasKind.COMIC)
        compose.setContent {
            DrawGuidanceTheme {
                CompositionLocalProvider(LocalPremium provides PremiumAccess(Entitlements.FREE, kind) { asked++ }) {
                    com.karapp.drawguidance.ui.screens.canvasedit.components.ComicTile("T", false, { picked++ }, Modifier, "tile", premium = PremiumFeature.COMIC_TEMPLATES) {}
                }
            }
        }
        compose.onNodeWithTag("premium_badge").assertExists()
        compose.onNodeWithTag("tile_locked").performClick()
        assertEquals(0, picked); assertEquals(1, asked)
        // Çocuk tuvalinde kilit yok
        kind = CanvasKind.KIDS
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag("premium_badge").fetchSemanticsNodes().size)
        compose.onNodeWithTag("tile").performClick()
        assertEquals(1, picked)
    }

    @Test
    fun premiumScopeOpensRequiredSheetAndSubscription() {
        TestAccount.entitlements.value = Entitlements.FREE
        var opened = false
        compose.setContent {
            DrawGuidanceTheme {
                PremiumScope(CanvasKind.COMIC, { opened = true }) {
                    com.karapp.drawguidance.ui.screens.canvasedit.components.ComicTile("T", false, {}, Modifier, "tile", premium = PremiumFeature.COMIC_BACKGROUNDS) {}
                }
            }
        }
        compose.onNodeWithTag("tile_locked").performClick()
        compose.onNodeWithTag("premium_required_sheet").assertExists()
        compose.onNodeWithText("Premium gerekli").assertExists()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("premium_go_subscription").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue(opened)
    }

    @Test
    fun premiumUserSeesNoLocks() {
        TestAccount.entitlements.value = Entitlements.premium()
        var picked = false
        compose.setContent {
            DrawGuidanceTheme {
                PremiumScope(CanvasKind.COMIC, {}) {
                    com.karapp.drawguidance.ui.screens.canvasedit.components.ComicTile("T", false, { picked = true }, Modifier, "tile", premium = PremiumFeature.COMIC_TEMPLATES) {}
                }
            }
        }
        compose.onNodeWithTag("tile").performClick()
        assertTrue(picked)
    }

    @Test
    fun homeCards() {
        var premiumClicks = 0
        var continued = 0
        var passed by androidx.compose.runtime.mutableStateOf(0)
        compose.setContent {
            DrawGuidanceTheme {
                androidx.compose.foundation.layout.Column {
                    PremiumHomeCard(signedIn = false, onClick = { premiumClicks++ })
                    ChallengeHomeCard(passed = passed, nextLevel = passed + 1, onContinue = { continued++ })
                }
            }
        }
        compose.onNodeWithText("Giriş yap").assertExists()
        compose.onNodeWithTag("home_premium_card").performClick()
        assertEquals(1, premiumClicks)
        compose.onNodeWithTag("home_challenge_mascot").assertExists()
        compose.onNodeWithText("Devam et").assertExists()
        passed = 3
        compose.waitForIdle()
        compose.onNodeWithText("Kaldığın seviyeden devam et (4)").assertExists()
        compose.onNodeWithTag("home_challenge_continue").performClick()
        assertEquals(1, continued)
    }
}

/** v39 abonelik sayfasının durumları. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "tr-w411dp-h891dp-xxhdpi")
class V39SubscriptionScreenTest {
    @get:Rule val compose = createComposeRule()
    private inline fun <reified T : Any> koin(): T = GlobalContext.get().get()

    @After fun stop() { TestAccount.reset(); org.koin.core.context.stopKoin() }

    private fun show() {
        val repo = koin<SubscriptionRepository>()
        runBlocking { repo.seed() }
        val vm = SubscriptionViewModel(repo)
        compose.setContent { DrawGuidanceTheme { SubscriptionScreen(onBack = {}, vm = vm) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("plan_premium").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun freeNotSignedIn() {
        TestAccount.entitlements.value = null
        show()
        compose.onNodeWithText("Planın: Free").assertExists()
        compose.onNodeWithTag("plan_price_premium").assertExists()
        compose.onNodeWithText("100 TL/ay").assertExists()
        compose.onNodeWithTag("sub_upgrade").assertIsNotEnabled()
        compose.onNodeWithTag("sub_upgrade_reason").assertExists()
        compose.onNodeWithTag("sub_restore").assertIsNotEnabled()
        compose.onNodeWithTag("sub_manage").assertExists()
        compose.onNodeWithTag("sub_refresh").assertExists()
    }

    @Test
    fun signedInButStoreUnavailable() {
        TestAccount.entitlements.value = null
        TestAccount.auth.signedIn = FakeAuth.DEFAULT
        runBlocking { koin<LocalUserDao>().upsert(LocalUserEntity("u1", UID, "Ahmet", null, null, null, true, null, null, null, true, 1, 1)) }
        show()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Google Play bu cihazda kullanılamıyor.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sub_upgrade").assertIsNotEnabled()
    }

    @Test
    fun premiumShowsStatus() {
        TestAccount.entitlements.value = Entitlements.premium(until = System.currentTimeMillis() + 5 * DAY).copy(status = "ACTIVE", autoRenewing = true)
        show()
        compose.onNodeWithText("Planın: Premium").assertExists()
        compose.onNodeWithText("Durum: Etkin").assertExists()
        compose.onNodeWithText("Otomatik yenileme açık").assertExists()
        assertEquals(0, compose.onAllNodesWithTag("sub_upgrade").fetchSemanticsNodes().size)
    }
}
