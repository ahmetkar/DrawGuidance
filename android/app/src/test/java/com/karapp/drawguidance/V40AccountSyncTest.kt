package com.karapp.drawguidance

import android.content.Context
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.account.AuthException
import com.karapp.drawguidance.data.billing.SubscriptionStatuses
import com.karapp.drawguidance.data.catalog.CatalogStore
import com.karapp.drawguidance.data.catalog.CatalogSyncer
import com.karapp.drawguidance.data.feedback.FeedbackModes
import com.karapp.drawguidance.data.feedback.FeedbackRepository
import com.karapp.drawguidance.data.feedback.FeedbackResult
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.local.SubscriptionEntity
import com.karapp.drawguidance.data.local.SyncEventEntity
import com.karapp.drawguidance.data.settings.ServerConfig
import com.karapp.drawguidance.data.remote.v1.FeedbackBody
import com.karapp.drawguidance.data.remote.v1.FeedbackDevice
import com.karapp.drawguidance.data.remote.v1.RemoteException
import com.karapp.drawguidance.data.remote.v1.RemoteStore
import com.karapp.drawguidance.data.remote.v1.RemoteSyncPermit
import com.karapp.drawguidance.data.reports.ErrorReporter
import com.karapp.drawguidance.data.sync.FixedConnectivity
import com.karapp.drawguidance.data.sync.PermitDecision
import com.karapp.drawguidance.data.sync.SyncEngine
import com.karapp.drawguidance.data.sync.SyncPermits
import com.karapp.drawguidance.data.sync.SyncTypes
import com.karapp.drawguidance.diagnostics.AppErrors
import com.karapp.drawguidance.diagnostics.ErrorReport
import com.karapp.drawguidance.diagnostics.ErrorSink
import com.karapp.drawguidance.diagnostics.ReportPolicy
import com.karapp.drawguidance.notify.EngagementChecks
import com.karapp.drawguidance.notify.EngagementRules
import com.karapp.drawguidance.notify.NotificationPrefs
import com.karapp.drawguidance.notify.UpdateDecision
import com.karapp.drawguidance.ui.components.ErrorReportHost
import com.karapp.drawguidance.ui.screens.account.SignInOutcome
import com.karapp.drawguidance.ui.screens.account.SignInResultDialog
import com.karapp.drawguidance.ui.screens.settings.FeedbackCard
import com.karapp.drawguidance.ui.screens.settings.FeedbackSheet
import com.karapp.drawguidance.ui.screens.settings.ForceUpdateHost
import com.karapp.drawguidance.ui.screens.settings.NotificationSettingsCard
import com.karapp.drawguidance.ui.screens.subscription.SubscriptionDetails
import com.karapp.drawguidance.ui.screens.subscription.SubscriptionDetailsCard
import com.karapp.drawguidance.ui.theme.DrawGuidanceTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
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
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

private inline fun <reified T : Any> koin(): T = GlobalContext.get().get()

/** v40 test aboneliği (Play'den gelen son hal). */
private fun sub(status: String, autoRenewing: Boolean = true, cancelTime: Long? = null, cancelReason: String? = null, autoResume: Long? = null) =
    SubscriptionEntity(
        "uid-ahmet", "premium", status, "premium_monthly", "monthly", 1_700_000_000_000L, 1_800_000_000_000L, autoRenewing, true,
        "GPA.1", "tok", "", "server", 1L, 1L, cancelTime, cancelReason, autoResume,
    )

/**
 * v40 saf kurallar: giriş sonucunun sınıflanması, geri bildirim gövdesi (izinsiz cihaz bilgisi yok), eşitleme izni
 * kararı (sunucu saati + dilim içi kayma), bildirim kuralları (3/7/14 gün; güncelleme) ve abonelik ayrıntıları.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V40AccountRulesTest {
    @After fun tearDown() = stopKoin()

    @Test
    fun signInOutcomeIsClassified() {
        assertEquals(SignInOutcome.SUCCESS, SignInOutcome.of(null, online = true))
        assertEquals(SignInOutcome.CANCELLED, SignInOutcome.of(AuthException(AuthException.Reason.CANCELLED), online = true))
        assertEquals(SignInOutcome.NO_NETWORK, SignInOutcome.of(AuthException(AuthException.Reason.NETWORK), online = false))
        assertEquals(SignInOutcome.SERVER_UNREACHABLE, SignInOutcome.of(AuthException(AuthException.Reason.NETWORK), online = true))
        assertEquals(SignInOutcome.ACCOUNT_PROBLEM, SignInOutcome.of(AuthException(AuthException.Reason.NO_ACCOUNT), online = true))
        assertEquals(SignInOutcome.UNKNOWN, SignInOutcome.of(AuthException(AuthException.Reason.FAILED), online = true))
        assertEquals(SignInOutcome.NO_NETWORK, SignInOutcome.of(AuthException(AuthException.Reason.FAILED), online = false))
        // Sunucu (kullanıcı kaydı) hataları
        assertEquals(SignInOutcome.SERVER_UNREACHABLE, SignInOutcome.of(RemoteException(503, "unavailable", "x"), online = true))
        assertEquals(SignInOutcome.ACCOUNT_PROBLEM, SignInOutcome.of(RemoteException(403, "forbidden", "x"), online = true))
        assertEquals(SignInOutcome.SERVER_UNREACHABLE, SignInOutcome.of(IOException("timeout"), online = true))
        assertEquals(SignInOutcome.NO_NETWORK, SignInOutcome.of(IOException("timeout"), online = false))
        assertEquals(SignInOutcome.UNKNOWN, SignInOutcome.of(IllegalStateException(), online = true))
        assertFalse(SignInOutcome.SUCCESS.canRetry)
        assertTrue(SignInOutcome.entries.filter { it != SignInOutcome.SUCCESS }.all { it.canRetry })
    }

    @Test
    fun feedbackBodyRespectsConsentAndLimits() {
        val device = FeedbackDevice("Pixel 8", "14 (API 34)", "4.0", 40000, "tr-TR")
        val ok = FeedbackRepository.build(FeedbackModes.COMIC, "balloons", 4, "  güzel  ", consent = true, device = device)!!
        assertEquals("COMIC", ok.mode)
        assertEquals("comic.balloons", ok.feature)
        assertEquals("güzel", ok.text)
        assertEquals(device, ok.device)
        assertTrue(ok.consent)
        // İzin yoksa cihaz bilgisi gönderilmez
        val noConsent = FeedbackRepository.build(FeedbackModes.ART, "brushes", 5, "x", consent = false, device = device)!!
        assertNull(noConsent.device)
        assertFalse(noConsent.consent)
        // Başka modun özelliği atılır; boş yazı null; uzun yazı kırpılır
        assertNull(FeedbackRepository.build(FeedbackModes.KIDS, "layers", 3, "", false, device)!!.feature)
        assertNull(FeedbackRepository.build(FeedbackModes.KIDS, null, 3, "   ", false, device)!!.text)
        assertEquals(FeedbackRepository.MAX_TEXT, FeedbackRepository.build(FeedbackModes.NOTES, null, 3, "a".repeat(9000), false, device)!!.text!!.length)
        // Geçersiz mod ya da puan
        assertNull(FeedbackRepository.build("PAINT", null, 3, "", false, device))
        assertNull(FeedbackRepository.build(FeedbackModes.ART, null, 0, "", false, device))
        assertNull(FeedbackRepository.build(FeedbackModes.ART, null, 6, "", false, device))
        // Her modun en az bir özelliği var
        assertEquals(7, FeedbackModes.ALL.size)
        assertTrue(FeedbackModes.FEATURES.values.all { it.isNotEmpty() })
    }

    @Test
    fun permitDecisionUsesServerTimeAndJitter() {
        assertEquals(PermitDecision.Allowed, SyncPermits.decide(RemoteSyncPermit(allowed = true), 0L))
        val server = 1_000_000_000L
        val p = RemoteSyncPermit(
            allowed = false, nextSlotStart = JsonPrimitive(server + 3_600_000L), slotLengthSeconds = 600, serverTime = JsonPrimitive(server),
        )
        // Cihaz saati sunucudan 1 gün ileride olsa da bekleme sunucu saatine göre: 1 saat + dilimin ilk yarısında kayma
        val now = server + 86_400_000L
        repeat(50) { seed ->
            val d = SyncPermits.decide(p, now, Random(seed)) as PermitDecision.Deferred
            assertTrue("${d.delayMs}", d.delayMs in 3_600_000L until 3_600_000L + 300_000L)
            assertEquals(now + d.delayMs, d.nextSlotStart)
        }
        // ISO zaman da okunur
        val iso = RemoteSyncPermit(allowed = false, nextSlotStart = JsonPrimitive("2026-10-24T10:10:00Z"), serverTime = JsonPrimitive("2026-10-24T10:00:00Z"))
        val d = SyncPermits.decide(iso, 0L, Random(1)) as PermitDecision.Deferred
        assertTrue(d.delayMs in 600_000L until 900_000L)
        // Zaman okunamazsa bir saat sonra yeniden sorulur
        assertEquals(PermitDecision.Deferred(3_600_000L, 3_600_000L), SyncPermits.decide(RemoteSyncPermit(allowed = false), 0L))
    }

    @Test
    fun inactivityRemindersAt3_7_14DaysThenStop() {
        val day = EngagementRules.DAY_MS
        val open = 10 * day
        assertNull(EngagementRules.inactivityReminderDue(open, open + 2 * day, 0))
        assertEquals(1, EngagementRules.inactivityReminderDue(open, open + 3 * day, 0))
        assertNull(EngagementRules.inactivityReminderDue(open, open + 6 * day, 1))
        assertEquals(2, EngagementRules.inactivityReminderDue(open, open + 7 * day, 1))
        assertNull(EngagementRules.inactivityReminderDue(open, open + 13 * day, 2))
        assertEquals(3, EngagementRules.inactivityReminderDue(open, open + 14 * day, 2))
        // Üçüncüden sonra durur
        assertNull(EngagementRules.inactivityReminderDue(open, open + 100 * day, 3))
        // Uzun süre kapalı kalan cihazda bir seferde yalnız bir hatırlatma (sıradaki)
        assertEquals(1, EngagementRules.inactivityReminderDue(open, open + 30 * day, 0))
        // Hiç açılmadıysa yok
        assertNull(EngagementRules.inactivityReminderDue(0, 100 * day, 0))
    }

    @Test
    fun updateDecision() {
        assertEquals(UpdateDecision.NONE, EngagementRules.update(installed = 40000, latest = 40000, minSupported = 39000, lastNotified = 0))
        assertEquals(UpdateDecision.NOTIFY, EngagementRules.update(40000, 40100, 39000, 0))
        // Aynı sürüm için ikinci kez bildirilmez
        assertEquals(UpdateDecision.NONE, EngagementRules.update(40000, 40100, 39000, 40100))
        assertEquals(UpdateDecision.NOTIFY, EngagementRules.update(40000, 40200, 39000, 40100))
        // Desteklenen en eskiden küçükse zorunlu
        assertEquals(UpdateDecision.FORCE, EngagementRules.update(38000, 40100, 39000, 40100))
        // Sunucu bilgisi yoksa (0) bir şey yapılmaz
        assertEquals(UpdateDecision.NONE, EngagementRules.update(40000, 0, 0, 0))
    }

    @Test
    fun subscriptionDetailsFromEntity() {
        assertNull(SubscriptionDetails.from(null))
        assertNull(SubscriptionDetails.from(sub(SubscriptionStatuses.NONE)))
        val active = SubscriptionDetails.from(sub(SubscriptionStatuses.ACTIVE))!!
        assertTrue(active.renews)
        assertTrue(active.autoRenewing)
        assertEquals(1_700_000_000_000L, active.startTime)
        assertEquals(1_800_000_000_000L, active.periodEnd)
        assertFalse(active.canceled || active.paused || active.onHold || active.inGracePeriod)
        val grace = SubscriptionDetails.from(sub(SubscriptionStatuses.IN_GRACE_PERIOD))!!
        assertTrue(grace.inGracePeriod && grace.renews)
        assertTrue(SubscriptionDetails.from(sub(SubscriptionStatuses.ON_HOLD))!!.onHold)
        val paused = SubscriptionDetails.from(sub(SubscriptionStatuses.PAUSED, autoResume = 1_750_000_000_000L))!!
        assertTrue(paused.paused)
        assertFalse(paused.renews)
        assertEquals(1_750_000_000_000L, paused.autoResumeTime)
        val canceled = SubscriptionDetails.from(sub(SubscriptionStatuses.CANCELED, autoRenewing = false, cancelTime = 5L, cancelReason = "USER"))!!
        assertTrue(canceled.canceled)
        assertFalse(canceled.renews)
        assertEquals(5L, canceled.cancelTime)
        assertEquals("USER", canceled.cancelReason)
        // Play abonelik sayfası (ürün ve paket adıyla)
        val uri = SubscriptionDetails.manageUri("com.karapp.drawguidance", "premium_monthly").toString()
        assertTrue(uri, uri.startsWith("https://play.google.com/store/account/subscriptions?sku=premium_monthly&package=com.karapp.drawguidance"))
    }
}

/**
 * v40 eşitleme: hesap verileri (kullanıcı, hata raporu, geri bildirim, görev ilerlemesi) eşitleme denetimini (yalnız
 * Wi-Fi, günlük sınır, durdurma) beklemez, yalnız bağlantı ister; içerik işleri denetimi bekler. Eşitleme izni
 * sunucudan sorulur, izin yoksa dilime ertelenir; katalog izin yokken inmez. Geri bildirim kuyruktan sunucuya gider.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V40SyncTest {
    private val server = MockWebServer()
    private lateinit var db: AppDatabase

    @Before fun open() {
        TestAccount.reset()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun close() {
        db.close()
        stopKoin()
        runCatching { server.shutdown() }
        TestAccount.reset()
    }

    @Test
    fun accountDataBypassesGateButNotConnectivity() = runBlocking {
        val net = FixedConnectivity(true)
        val engine = SyncEngine(db.syncEventDao(), net, TestScope())
        var allowed = false
        engine.gate = { allowed }
        val calls = CopyOnWriteArrayList<String>()
        (SyncTypes.ACCOUNT + "CATALOG_X").forEach { t -> engine.register(t) { calls += t } }
        SyncTypes.ACCOUNT.forEach { engine.enqueue(it, dedupeKey = null) }
        engine.enqueue("CATALOG_X", dedupeKey = null)

        // Denetim kapalı (ör. yalnız Wi-Fi, mobil veride): hesap verileri hemen gider, içerik bekler
        assertEquals(SyncTypes.ACCOUNT.size, engine.process())
        assertEquals(SyncTypes.ACCOUNT, calls.toSet())
        assertEquals(listOf("CATALOG_X"), db.syncEventDao().pending().map { it.type })

        // Bağlantı yoksa hesap verileri de bekler (kuyrukta kalır)
        net.set(false)
        engine.enqueue(SyncTypes.FEEDBACK, "f", dedupeKey = null)
        assertEquals(0, engine.process())
        assertEquals(2, db.syncEventDao().pending().size)

        // Bağlantı geldi, denetim açıldı: hepsi
        net.set(true)
        allowed = true
        assertEquals(2, engine.process())
        assertTrue(db.syncEventDao().pending().isEmpty())
        assertTrue("CATALOG_X" in calls)
    }

    private fun startServer(handler: (RecordedRequest) -> MockResponse): CopyOnWriteArrayList<RecordedRequest> {
        val seen = CopyOnWriteArrayList<RecordedRequest>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse { seen += request; return handler(request) }
        }
        server.start()
        koin<ServerConfig>().update(server.url("/").toString())
        return seen
    }

    private fun permits(scheduled: MutableList<Long>) =
        SyncPermits(ApplicationProvider.getApplicationContext(), koin<RemoteStore>(), TestAccount.auth, { scheduled += it }, Random(7))

    @Test
    fun permitDeniedSchedulesNextSlot() = runBlocking {
        val now = System.currentTimeMillis()
        val seen = startServer {
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"allowed":false,"slotStart":${now + 1_800_000},"slotEnd":${now + 2_400_000},"nextSlotStart":${now + 1_800_000},""" +
                    """"contentVersion":5,"slot":12,"group":3,"slotLengthSeconds":600,"serverTime":$now}""",
            )
        }
        val scheduled = mutableListOf<Long>()
        val p = permits(scheduled)
        val d = p.check(now) as PermitDecision.Deferred
        assertTrue("${d.delayMs}", d.delayMs in 1_800_000L until 2_100_000L)
        assertEquals(listOf(d.delayMs), scheduled)
        assertEquals(d.nextSlotStart, p.nextSlot.value)
        // Girişsiz: Firebase jetonu yok, cihaz kimliği (rastgele, kalıcı) gider
        val r = seen.single()
        assertTrue(r.path!!.startsWith("/api/v1/sync/permit"))
        assertNull(r.getHeader("Authorization"))
        val sent = r.requestUrl!!.queryParameter("deviceId") ?: r.getHeader("X-Device-Id")
        assertEquals(p.deviceId, sent)
        assertEquals(p.deviceId, permits(mutableListOf()).deviceId)
    }

    @Test
    fun permitWithTokenFallsBackToDeviceIdAndOldServerAllows() = runBlocking {
        TestAccount.auth.signedIn = FakeAuth.DEFAULT
        val seen = startServer { r ->
            when {
                r.getHeader("Authorization") != null -> MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val scheduled = mutableListOf<Long>()
        val p = permits(scheduled)
        // Jeton reddedildi → cihaz kimliğiyle; eski sunucuda uç yok (404) → izinli sayılır
        assertEquals(PermitDecision.Allowed, p.check())
        assertEquals(2, seen.size)
        assertEquals("Bearer fake-token", seen[0].getHeader("Authorization"))
        assertNull(seen[1].getHeader("Authorization"))
        assertTrue(scheduled.isEmpty())
        assertNull(p.nextSlot.value)
    }

    @Test
    fun catalogIsNotDownloadedWithoutPermit() = runBlocking {
        val version = com.karapp.drawguidance.content.catalog.CatalogInfo.VERSION + 1
        val seen = startServer { r ->
            if (r.path == "/api/v1/catalog/manifest") MockResponse().setHeader("ETag", "\"c\"").setBody("""{"version":$version}""")
            else MockResponse().setResponseCode(500)
        }
        var asked = 0
        val syncer = CatalogSyncer(
            koin(), koin<CatalogStore>(), koin(), null, permit = { asked++; false },
        )
        assertFalse(syncer.sync())
        assertEquals(1, asked)
        // Yalnız manifest istendi; gövdeler inmedi ve sürüm uygulanmadı
        assertEquals(listOf("/api/v1/catalog/manifest"), seen.map { it.path })
        assertFalse(koin<CatalogStore>().appliedVersion >= version)
    }

    @Test
    fun feedbackIsQueuedAndPostedWithToken() = runBlocking {
        val seen = startServer { MockResponse().setResponseCode(202).setHeader("Content-Type", "application/json").setBody("""{"id":"fb1"}""") }
        val net = FixedConnectivity(false)
        val engine = SyncEngine(db.syncEventDao(), net, TestScope())
        val repo = FeedbackRepository(engine, koin(), TestAccount.auth, locale = { "tr-TR" })

        // Girişsiz gönderilemez
        assertEquals(FeedbackResult.NOT_SIGNED_IN, repo.submit(FeedbackModes.ART, "brushes", 5, "x", true))
        TestAccount.auth.signedIn = FakeAuth.DEFAULT
        assertEquals(FeedbackResult.INVALID, repo.submit(FeedbackModes.ART, null, 0, "x", true))
        assertEquals(FeedbackResult.QUEUED, repo.submit(FeedbackModes.ART, "brushes", 5, "Fırçalar harika", consent = false))
        // Bağlantı yok: kuyrukta bekler
        assertEquals(0, engine.process())
        assertEquals(SyncTypes.FEEDBACK, db.syncEventDao().pending().single().type)
        net.set(true)
        assertEquals(1, engine.process())
        val r = seen.single()
        assertEquals("/api/v1/feedback", r.path)
        assertEquals("POST", r.method)
        assertEquals("Bearer fake-token", r.getHeader("Authorization"))
        val body = Json { ignoreUnknownKeys = true }.decodeFromString(FeedbackBody.serializer(), r.body.readUtf8())
        assertEquals("art.brushes", body.feature)
        assertEquals(5, body.rating)
        assertNull(body.device)
        assertEquals(SyncEventEntity.STATUS_DONE, db.syncEventDao().all().single().status)
    }

    @Test
    fun errorReportsNeedSignIn() = runBlocking {
        startServer { MockResponse().setResponseCode(202).setBody("""{"id":"r1"}""") }
        val engine = SyncEngine(db.syncEventDao(), FixedConnectivity(false), TestScope())
        val report = AppErrors.build(com.karapp.drawguidance.diagnostics.ReportKind.ERROR, "test", IllegalStateException("x"), "main")
        coroutineScope {
            val reporter = ErrorReporter(ApplicationProvider.getApplicationContext(), engine, koin(), TestAccount.auth, this)
            assertFalse(reporter.signedIn)
            reporter.send(report, "not")
        }
        // Girişsiz: kuyruğa bile girmez
        assertTrue(db.syncEventDao().all().isEmpty())
        TestAccount.auth.signedIn = FakeAuth.DEFAULT
        coroutineScope {
            val reporter = ErrorReporter(ApplicationProvider.getApplicationContext(), engine, koin(), TestAccount.auth, this)
            assertTrue(reporter.signedIn)
            reporter.send(report, "not")
        }
        assertEquals(SyncTypes.ERROR_REPORT, db.syncEventDao().pending().single().type)
    }
}

/** v40 hata penceresi sağanak önlemi: farklı hatalar arka arkaya pencere açmaz; tekrarlar sayılır. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V40ErrorThrottleTest {
    private class Sink(override val policy: ReportPolicy, override val signedIn: Boolean) : ErrorSink {
        val sent = ArrayList<ErrorReport>()
        override fun send(report: ErrorReport, note: String?) { sent += report }
    }

    // v40 imza yığının ilk satırlarından: aynı hata (aynı yığın) tekrar bildirilir
    private val a = IllegalStateException("bir")
    private val b = IllegalArgumentException("iki")
    private fun errA() = a
    private fun errB() = b

    @Before fun reset() = AppErrors.resetForTest()

    @After fun tearDown() {
        AppErrors.sink = null
        AppErrors.resetForTest()
        stopKoin()
    }

    @Test
    fun promptsAreThrottledAndRepeatsCounted() {
        var now = 1_000_000L
        AppErrors.clock = { now }
        AppErrors.sink = Sink(ReportPolicy.ASK, signedIn = true)
        AppErrors.report("a", errA(), userVisible = true)
        val first = AppErrors.prompt.value!!
        AppErrors.dismiss()
        // 30 sn dolmadan başka bir hata: pencere açılmaz (sonra yeniden sorulabilir)
        now += 5_000
        AppErrors.report("b", errB(), userVisible = true)
        assertNull(AppErrors.prompt.value)
        now += 30_000
        AppErrors.report("b", errB(), userVisible = true)
        val second = AppErrors.prompt.value!!
        assertTrue(second.signature != first.signature)
        assertEquals(2, AppErrors.repeatCount(second.signature))
        assertEquals(1, AppErrors.repeatCount(first.signature))
        AppErrors.dismiss()
        // Aynı hata oturumda bir kez sorulur, yine de sayılır
        now += 60_000
        AppErrors.report("a", errA(), userVisible = true)
        assertNull(AppErrors.prompt.value)
        assertEquals(2, AppErrors.repeatCount(first.signature))
    }

    @Test
    fun sinkKnowsSignInState() {
        assertTrue(Sink(ReportPolicy.ASK, true).signedIn)
        assertFalse(Sink(ReportPolicy.ASK, false).signedIn)
        // Varsayılan (eski göndericiler) girişli sayılır
        assertTrue(object : ErrorSink {
            override val policy = ReportPolicy.ASK
            override fun send(report: ErrorReport, note: String?) = Unit
        }.signedIn)
    }
}

/**
 * v40 bildirim işi: hatırlatma eşiği gelince bir kez gönderilir ve sayaç ilerler; kapalıysa gönderilmez. Güncelleme
 * denetimi sunucudan sürümü alır, saklar, aynı sürüm için bir kez bildirir; desteklenmeyen sürümde zorunlu.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V40EngagementTest {
    private val server = MockWebServer()
    private lateinit var prefs: NotificationPrefs
    private var now = 100 * EngagementRules.DAY_MS

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("notifications", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = NotificationPrefs(ctx) { now }
    }

    @After fun tearDown() { stopKoin(); runCatching { server.shutdown() } }

    @Test
    fun inactivityRemindersFollowPrefs() {
        val reminders = mutableListOf<Int>()
        val checks = EngagementChecks(ApplicationProvider.getApplicationContext(), prefs, null, 40000, { now }, postReminder = { reminders += it; true }, postUpdate = { true })
        prefs.onAppOpened()
        assertEquals(1, prefs.opens)
        now += 2 * EngagementRules.DAY_MS
        assertNull(checks.checkInactivity())
        now += EngagementRules.DAY_MS
        assertEquals(1, checks.checkInactivity())
        assertNull(checks.checkInactivity())
        now += 4 * EngagementRules.DAY_MS
        assertEquals(2, checks.checkInactivity())
        now += 7 * EngagementRules.DAY_MS
        assertEquals(3, checks.checkInactivity())
        now += 30 * EngagementRules.DAY_MS
        assertNull(checks.checkInactivity())
        assertEquals(listOf(1, 2, 3), reminders)
        // Açılınca sayaç sıfırlanır; kapalıysa gönderilmez
        prefs.onAppOpened()
        assertEquals(0, prefs.remindersSent)
        prefs.setInactivity(false)
        now += 5 * EngagementRules.DAY_MS
        assertNull(checks.checkInactivity())
        assertEquals(3, reminders.size)
    }

    @Test
    fun updateCheckNotifiesOnceAndStoresMinimum() = runBlocking {
        var body = """{"latestVersionCode":40100,"minSupportedVersionCode":39000,"message":null}"""
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                if (request.path == "/api/v1/app/version") MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                else MockResponse().setResponseCode(404)
        }
        server.start()
        koin<ServerConfig>().update(server.url("/").toString())
        val updates = mutableListOf<String?>()
        val checks = EngagementChecks(ApplicationProvider.getApplicationContext(), prefs, koin(), 40000, { now }, postReminder = { true }, postUpdate = { updates += it; true })
        assertEquals(UpdateDecision.NOTIFY, checks.checkUpdate())
        assertEquals(1, updates.size)
        assertEquals(40100, prefs.latestVersion)
        assertEquals(39000, prefs.minSupported.value)
        // Ertesi gün aynı sürüm: yeniden bildirilmez
        assertEquals(UpdateDecision.NONE, checks.checkUpdate())
        assertEquals(1, updates.size)
        // Güncelleme bildirimi kapalı: karar verilir ama bildirilmez
        prefs.setUpdates(false)
        body = """{"latestVersionCode":40200,"minSupportedVersionCode":40050,"message":"Lütfen güncelle"}"""
        assertEquals(UpdateDecision.FORCE, checks.checkUpdate())
        assertEquals(1, updates.size)
        assertEquals(40050, prefs.minSupported.value)
        assertEquals("Lütfen güncelle", prefs.message)
        // Sunucuya ulaşılamazsa karar yok
        server.shutdown()
        assertNull(checks.checkUpdate())
    }
}

/** v40 Room 19 → 20 göçü: `subscriptions` tablosuna iptal ve duraklatma sütunları eklenir, eski satırlar korunur. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V40SubscriptionMigrationTest {
    @After fun tearDown() = stopKoin()

    @Test
    fun migration19to20AddsSubscriptionColumns() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "mig20-test.db"
        ctx.deleteDatabase(name)
        Room.databaseBuilder(ctx, AppDatabase::class.java, name).allowMainThreadQueries().build().apply {
            openHelper.writableDatabase.apply {
                // v19 şeması: v39 tablosu (yeni sütunlar yok) ve içinde bir abonelik
                execSQL("DROP TABLE `subscriptions`")
                execSQL(SubscriptionEntity.CREATE_SQL)
                execSQL(
                    "INSERT INTO subscriptions (firebaseUid, planId, status, productId, basePlanId, startTime, expiryTime, autoRenewing, " +
                        "acknowledged, orderId, purchaseToken, features, source, verifiedAt, updatedAt) VALUES " +
                        "('uid-1', 'premium', 'ACTIVE', 'premium_monthly', 'monthly', 10, 20, 1, 1, 'GPA', 'tok', '', 'server', 5, 6)",
                )
                version = 19
            }
            close()
        }
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name).allowMainThreadQueries().addMigrations(*AppDatabase.ALL_MIGRATIONS).build()
        runBlocking {
            val old = db.billingDao().subscription("uid-1")!!
            assertEquals("ACTIVE", old.status)
            assertEquals(20L, old.expiryTime)
            assertNull(old.cancelTime)
            assertNull(old.cancelReason)
            assertNull(old.autoResumeTime)
            db.billingDao().upsertSubscription(old.copy(status = "CANCELED", cancelTime = 15, cancelReason = "USER", autoResumeTime = 30))
            val saved = db.billingDao().subscription("uid-1")!!
            assertEquals(15L, saved.cancelTime)
            assertEquals("USER", saved.cancelReason)
            assertEquals(30L, saved.autoResumeTime)
        }
        assertEquals(23, db.openHelper.readableDatabase.version)
        db.close()

        // v40 göç zinciri 1 → 21 eksiksiz (v42: 20 → 21 tema paketleri): her sürümden bir sonrakine tam bir göç
        // v43: 21 → 22 gelen kutusu ve izinler; v44: 22 → 23 çocuk kilidi
        assertEquals(23, AppDatabase.ALL_MIGRATIONS.maxOf { it.endVersion })
        (1 until 23).forEach { v ->
            assertEquals("göç $v → ${v + 1}", 1, AppDatabase.ALL_MIGRATIONS.count { it.startVersion == v && it.endVersion == v + 1 })
        }
        assertTrue(AppDatabase.ALL_MIGRATIONS.single { it.startVersion == 18 } === AppDatabase.MIGRATION_18_19)
        // Sütunlar zaten varsa (19 başka dalda eklediyse) göç yine çalışır
        val again = Room.databaseBuilder(ctx, AppDatabase::class.java, name).allowMainThreadQueries().build()
        again.openHelper.writableDatabase.let { AppDatabase.MIGRATION_19_20.migrate(it) }
        again.close()
        ctx.deleteDatabase(name)
    }
}

/**
 * v40 ekranlar: giriş sonucu penceresi, hata penceresinde giriş zorunluluğu, geri bildirim formu, bildirim ayarları,
 * zorunlu güncelleme ve abonelik ayrıntıları.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "tr-w411dp-h891dp-xxhdpi")
class V40AccountUiTest {
    @get:Rule val compose = createComposeRule()

    @Before fun setUp() {
        TestAccount.reset()
        AppErrors.resetForTest()
    }

    @After fun tearDown() {
        AppErrors.dismiss()
        AppErrors.resetForTest()
        TestAccount.reset()
        stopKoin()
    }

    @Test
    fun signInResultDialogShowsOutcomeAndRetry() {
        var outcome by androidx.compose.runtime.mutableStateOf(SignInOutcome.NO_NETWORK)
        var retries = 0
        var dismissed = 0
        compose.setContent { DrawGuidanceTheme { SignInResultDialog(outcome, onRetry = { retries++ }, onDismiss = { dismissed++ }) } }
        compose.onNodeWithTag("signin_result_no_network").assertExists()
        compose.onNodeWithText("İnternet bağlantısı yok").assertExists()
        compose.onNodeWithText("Tekrar dene").performClick()
        assertEquals(1, retries)
        assertEquals(1, dismissed)
        outcome = SignInOutcome.ACCOUNT_PROBLEM
        compose.waitForIdle()
        compose.onNodeWithText("Hesap sorunu").assertExists()
        compose.onNodeWithText("Başka hesap dene").assertExists()
        outcome = SignInOutcome.SERVER_UNREACHABLE
        compose.waitForIdle()
        compose.onNodeWithText("Sunucuya ulaşılamadı").assertExists()
        outcome = SignInOutcome.SUCCESS
        compose.waitForIdle()
        compose.onNodeWithText("Giriş yapıldı").assertExists()
        compose.onNodeWithTag("signin_result_retry").assertDoesNotExist()
        compose.onNodeWithText("Tamam").performClick()
        assertEquals(2, dismissed)
        assertEquals(1, retries)
    }

    @Test
    fun errorSheetAsksSignInBeforeReporting() {
        val sent = mutableListOf<ErrorReport>()
        AppErrors.sink = object : ErrorSink {
            override val policy = ReportPolicy.ASK
            override fun send(report: ErrorReport, note: String?) { sent += report }
        }
        AppErrors.report("canvas.save", IllegalStateException("kayıt"), userVisible = true)
        compose.setContent { DrawGuidanceTheme { ErrorReportHost() } }
        compose.onNodeWithTag("error_report_sheet").assertExists()
        compose.onNodeWithTag("error_report_sign_in_hint").assertExists()
        compose.onNodeWithText("Sorun bildir").assertExists()
        compose.onNodeWithText("Kapat").assertExists()
        // Girişsiz: "Sorun bildir" giriş uyarısı açar, rapor gitmez
        compose.onNodeWithTag("error_report_send").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag("signin_required").assertExists()
        compose.onNodeWithText("Giriş yap").assertExists()
        assertTrue(sent.isEmpty())
        compose.onNodeWithTag("signin_required_cancel").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag("signin_required").assertDoesNotExist()
        assertNotNull(AppErrors.prompt.value)
        compose.onNodeWithTag("error_report_dismiss").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertNull(AppErrors.prompt.value)
    }

    @Test
    fun feedbackNeedsSignInAndSheetQueuesFeedback() {
        compose.setContent { DrawGuidanceTheme { FeedbackCard() } }
        compose.onNodeWithTag("feedback_open").performClick()
        compose.onNodeWithTag("signin_required").assertExists()
        compose.onNodeWithText("Geri bildirim göndermek için giriş yapman gerekiyor.").assertExists()
        compose.onNodeWithTag("signin_required_cancel").performClick()
        compose.onNodeWithTag("feedback_sheet").assertDoesNotExist()
    }

    @Test
    fun feedbackSheetSendsModeFeatureRatingAndConsent() {
        TestAccount.auth.signedIn = FakeAuth.DEFAULT
        val repo = koin<FeedbackRepository>()
        var done: Boolean? = null
        compose.setContent { DrawGuidanceTheme { FeedbackSheet(repo, onDone = { done = it }) } }
        compose.onNodeWithTag("feedback_send").assertExists()
        fun tap(tag: String) {
            compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
        }
        tap("feedback_mode_comic")
        tap("feedback_feature_balloons")
        tap("feedback_star_4")
        compose.onNodeWithTag("feedback_text").performScrollTo().performTextInput("Balonlar çok iyi")
        tap("feedback_consent")
        tap("feedback_send")
        compose.waitUntil(5_000) { done != null }
        assertEquals(true, done)
        val event = runBlocking { koin<AppDatabase>().syncEventDao().all() }.single { it.type == SyncTypes.FEEDBACK }
        val body = Json { ignoreUnknownKeys = true }.decodeFromString(FeedbackBody.serializer(), event.payload)
        assertEquals("COMIC", body.mode)
        assertEquals("comic.balloons", body.feature)
        assertEquals(4, body.rating)
        assertEquals("Balonlar çok iyi", body.text)
        assertTrue(body.consent)
        assertNotNull(body.device)
    }

    @Test
    fun notificationSettingsToggleAndExplainPermission() {
        val prefs = koin<NotificationPrefs>()
        prefs.setUpdates(true)
        prefs.setInactivity(true)
        compose.setContent { DrawGuidanceTheme { NotificationSettingsCard() } }
        compose.onNodeWithTag("notifications_card").assertExists()
        compose.onNodeWithContentDescription("Güncelleme bildirimleri").performClick()
        compose.waitForIdle()
        assertFalse(prefs.updatesEnabled.value)
        compose.onNodeWithContentDescription("Hatırlatmalar").performClick()
        compose.waitForIdle()
        assertFalse(prefs.inactivityEnabled.value)
        // Android 13+: izin yok → önce uygulama içi açıklama
        compose.onNodeWithTag("notif_permission_open").performClick()
        compose.onNodeWithTag("notif_permission_explain").assertExists()
        compose.onNodeWithText("Bildirimlere izin verir misin?").assertExists()
        compose.onNodeWithTag("notif_permission_later").performClick()
        compose.onNodeWithTag("notif_permission_explain").assertDoesNotExist()
        prefs.setUpdates(true)
        prefs.setInactivity(true)
    }

    @Test
    fun forceUpdateShownBelowMinimumVersion() {
        val prefs = koin<NotificationPrefs>()
        prefs.saveVersion(40100, 40050, null)
        var installed by androidx.compose.runtime.mutableStateOf(40000L)
        compose.setContent { DrawGuidanceTheme { ForceUpdateHost(prefs, installed) } }
        compose.onNodeWithTag("force_update").assertExists()
        compose.onNodeWithText("Güncelleme gerekli").assertExists()
        compose.onNodeWithTag("force_update_open").assertExists()
        installed = 40050L
        compose.waitForIdle()
        compose.onNodeWithTag("force_update").assertDoesNotExist()
        prefs.saveVersion(0, 0, null)
    }

    @Test
    fun subscriptionDetailsCardShowsStateAndPlayButtons() {
        var entity by androidx.compose.runtime.mutableStateOf<SubscriptionEntity?>(sub(SubscriptionStatuses.ACTIVE))
        compose.setContent { DrawGuidanceTheme { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) { SubscriptionDetailsCard(entity) } } }
        compose.onNodeWithTag("sub_details").assertExists()
        compose.onNodeWithTag("sub_start").assertExists()
        compose.onNodeWithText("Sonraki yenileme", substring = true).assertExists()
        compose.onNodeWithText("Otomatik yenileme: açık").assertExists()
        compose.onNodeWithTag("sub_manage_auto_renew").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("sub_cancel_resume").performScrollTo().assertIsDisplayed()
        entity = sub(SubscriptionStatuses.CANCELED, autoRenewing = false, cancelTime = 1_750_000_000_000L, cancelReason = "USER")
        compose.waitForIdle()
        compose.onNodeWithText("Dönem sonu", substring = true).assertExists()
        compose.onNodeWithText("Otomatik yenileme: kapalı").assertExists()
        compose.onNodeWithText("İptal tarihi", substring = true).assertExists()
        entity = sub(SubscriptionStatuses.PAUSED, autoRenewing = false, autoResume = 1_760_000_000_000L)
        compose.waitForIdle()
        compose.onNodeWithTag("sub_paused").assertExists()
        compose.onNodeWithText("kendiliğinden devam edecek", substring = true).assertExists()
        entity = sub(SubscriptionStatuses.IN_GRACE_PERIOD)
        compose.waitForIdle()
        compose.onNodeWithTag("sub_grace").assertExists()
        entity = sub(SubscriptionStatuses.ON_HOLD)
        compose.waitForIdle()
        compose.onNodeWithTag("sub_on_hold").assertExists()
        entity = null
        compose.waitForIdle()
        compose.onNodeWithTag("sub_details").assertDoesNotExist()
    }
}

