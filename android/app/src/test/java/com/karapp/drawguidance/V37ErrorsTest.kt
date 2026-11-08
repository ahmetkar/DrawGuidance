package com.karapp.drawguidance

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.reports.ErrorReporter
import com.karapp.drawguidance.data.settings.ServerConfig
import com.karapp.drawguidance.data.sync.SyncEngine
import com.karapp.drawguidance.diagnostics.AppErrors
import com.karapp.drawguidance.diagnostics.AppLog
import com.karapp.drawguidance.diagnostics.ErrorReport
import com.karapp.drawguidance.diagnostics.ErrorSink
import com.karapp.drawguidance.diagnostics.ReportKind
import com.karapp.drawguidance.diagnostics.ReportPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Raporları biriktiren sahte gönderici. */
internal class RecordingSink(override var policy: ReportPolicy) : ErrorSink {
    val sent = ArrayList<Pair<ErrorReport, String?>>()
    override fun send(report: ErrorReport, note: String?) { synchronized(sent) { sent += report to note } }
}

/**
 * v37 (1.2) merkezi hata yönetimi: korunan iş hata verince yedek değer döner ve hata bildirilir; kullanıcının fark
 * ettiği hatada pencere açılır (aynı hata oturumda bir kez); sessiz hatalar pencere açmaz; iptal geçer; tercihler
 * (sor / sormadan gönder / gönderme); çökme diske yazılır, sonraki açılışta sorulur; [AppLog.e] hataları da merkeze
 * gelir; son işlemlerin izi en çok 30 satır.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V37ErrorsLogicTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun reset() = AppErrors.resetForTest()

    @After fun tearDown() {
        AppErrors.resetForTest()
        org.koin.core.context.stopKoin()
    }

    private fun fail(): Int = AppErrors.guard("test.feature", -1, userVisible = true) { error("bozuk") }

    @Test
    fun guardFallsBackReportsAndAsksOncePerError() {
        val sink = RecordingSink(ReportPolicy.ASK)
        AppErrors.sink = sink
        AppErrors.breadcrumb("tuval açıldı")
        // Aynı yerden iki kez (aynı imza): ilkinde sorulur, ikincisinde bu oturumda yeniden sorulmaz
        for (round in 1..2) {
            assertEquals(-1, fail())
            if (round == 2) { assertNull(AppErrors.prompt.value); continue }
            val p = AppErrors.prompt.value
            assertNotNull(p)
            assertEquals(ReportKind.ERROR, p!!.kind)
            assertEquals("test.feature", p.feature)
            assertTrue(p.stackTrace.contains("bozuk"))
            assertEquals(listOf("tuval açıldı"), p.breadcrumbs)
            assertEquals(16, p.signature.length)
            // Gönder: not eklenir, pencere kapanır
            AppErrors.send("kaydederken", null)
            assertNull(AppErrors.prompt.value)
            assertEquals(1, sink.sent.size)
            assertEquals("kaydederken", sink.sent[0].second)
        }
        // Sessiz hata pencere açmaz
        AppErrors.guard("silent", Unit) { throw IllegalStateException("x") }
        assertNull(AppErrors.prompt.value)
        // İptal geçer (bildirilmez)
        assertThrows(CancellationException::class.java) { AppErrors.guard("cancel", 0) { throw CancellationException("dur") } }
        assertEquals(1, sink.sent.size)
        // Başarılı iş değerini döndürür
        assertEquals(5, AppErrors.guard("ok", 0) { 5 })
    }

    @Test
    fun policiesAutoAndNever() {
        val sink = RecordingSink(ReportPolicy.AUTO)
        AppErrors.sink = sink
        // Sormadan gönder: sessiz hata da gönderilir (imza başına bir kez), pencere açılmaz
        repeat(3) { AppErrors.guard("auto", Unit) { throw IllegalArgumentException("y") } }
        assertNull(AppErrors.prompt.value)
        assertEquals(1, sink.sent.size)
        assertNull(sink.sent[0].second)
        // Gönderme: hiçbir şey gitmez, pencere yok
        AppErrors.resetForTest()
        val never = RecordingSink(ReportPolicy.NEVER)
        AppErrors.sink = never
        fail()
        assertNull(AppErrors.prompt.value)
        assertTrue(never.sent.isEmpty())
    }

    @Test
    fun crashIsAskedOnNextLaunch() {
        val sink = RecordingSink(ReportPolicy.ASK)
        AppErrors.sink = sink
        AppErrors.breadcrumb("fırça seçildi")
        AppErrors.writeCrash(context, Thread.currentThread(), RuntimeException("çöktü"))
        val file = File(context.filesDir, "diagnostics/pending-crash.json")
        assertTrue(file.exists())
        // Yeni açılış (oturum kaydı sıfır)
        AppErrors.resetForTest()
        AppErrors.sink = sink
        AppErrors.checkPendingCrash(context)
        val p = AppErrors.prompt.value!!
        assertEquals(ReportKind.CRASH, p.kind)
        assertTrue(p.message.contains("çöktü"))
        assertEquals(listOf("fırça seçildi"), p.breadcrumbs)
        AppErrors.dismiss(context)
        assertFalse(file.exists())
        AppErrors.checkPendingCrash(context)
        assertNull(AppErrors.prompt.value)
        assertTrue(sink.sent.isEmpty())
        // Sormadan gönder tercihinde çökme doğrudan gönderilir
        AppErrors.writeCrash(context, Thread.currentThread(), RuntimeException("yine"))
        sink.policy = ReportPolicy.AUTO
        AppErrors.checkPendingCrash(context)
        assertEquals(ReportKind.CRASH, sink.sent.single().first.kind)
        assertFalse(file.exists())
    }

    @Test
    fun appLogErrorsReachCentralHandlingAndCrumbsAreCapped() {
        val sink = RecordingSink(ReportPolicy.AUTO)
        AppErrors.sink = sink
        AppLog.e("canvas.save", "kaydedilemedi", java.io.IOException("disk dolu"))
        assertEquals("canvas.save", sink.sent.single().first.feature)
        // Hatasız günlük satırı bildirilmez
        AppLog.e("canvas.save", "yalnız ileti")
        assertEquals(1, sink.sent.size)
        // Kullanıcının işi yapılamadıysa (dışa aktarma, kaydetme) sorulacak tercihte rapor penceresi açılır
        sink.policy = ReportPolicy.ASK
        AppLog.e("PdfExport", "PDF kaydedilemedi", java.io.IOException("izin yok"))
        assertNull("sessiz hata pencere açmaz", AppErrors.prompt.value)
        AppLog.e("PdfExport", "PDF kaydedilemedi", IllegalStateException("bozuk sayfa"), userVisible = true)
        assertEquals("PdfExport", AppErrors.prompt.value?.feature)
        AppErrors.dismiss()
        repeat(45) { AppErrors.breadcrumb("adım $it") }
        val crumbs = AppErrors.breadcrumbs()
        assertEquals(30, crumbs.size)
        assertEquals("adım 44", crumbs.last())
    }
}

/**
 * v37 (1.2) rapor sunucuya olay tablosuyla gider (`POST /api/v1/reports`): tür, özellik, not, sürüm, cihaz, dil,
 * kurulum kimliği ve iz; bağlantı yokken bekler, gelince gönderilir. Tercih kalıcıdır.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V37ErrorReporterTest {
    private val server = MockWebServer()
    private val received = LinkedBlockingQueue<RecordedRequest>()
    private inline fun <reified T : Any> koin(): T = GlobalContext.get().get()

    @Before fun start() {
        AppErrors.resetForTest()
        TestAccount.reset()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path != "/api/v1/reports" || request.method != "POST") return MockResponse().setResponseCode(404)
                received += request
                return MockResponse().setResponseCode(202).setBody("""{"id":"r-${received.size}"}""")
            }
        }
        server.start()
        koin<ServerConfig>().update(server.url("/").toString())
    }

    @After fun stop() {
        server.shutdown()
        AppErrors.resetForTest()
        TestAccount.reset()
        org.koin.core.context.stopKoin()
    }

    @Test
    fun reportIsQueuedAndPosted() {
        // v40 sorun bildirmek giriş ister (sunucu da jetonsuz raporu reddeder)
        TestAccount.auth.signedIn = FakeAuth.DEFAULT
        val reporter = koin<ErrorReporter>()
        AppErrors.breadcrumb("dışa aktar")
        val report = AppErrors.build(ReportKind.ERROR, "canvas.export", RuntimeException("boom"), "main")
        reporter.send(report, "PNG kaydederken")
        val req = received.poll(10, TimeUnit.SECONDS)
        assertNotNull("rapor gitmedi", req)
        val body = Json.parseToJsonElement(req!!.body.readUtf8()).jsonObject
        assertEquals("ERROR", body["kind"]!!.jsonPrimitive.content)
        assertEquals("canvas.export", body["feature"]!!.jsonPrimitive.content)
        assertEquals("PNG kaydederken", body["note"]!!.jsonPrimitive.content)
        assertEquals(BuildConfig.VERSION_NAME, body["appVersion"]!!.jsonPrimitive.content)
        assertEquals(BuildConfig.VERSION_CODE, body["versionCode"]!!.jsonPrimitive.int)
        assertEquals("tr", body["language"]!!.jsonPrimitive.content)
        assertEquals(reporter.installId, body["installId"]!!.jsonPrimitive.content)
        assertTrue(body["stackTrace"]!!.jsonPrimitive.content.contains("boom"))
        assertEquals(listOf("dışa aktar"), body["breadcrumbs"]!!.jsonArray.map { it.jsonPrimitive.content })
        // v40 rapor girişli kullanıcının jetonuyla gider (adsız rapor yok)
        assertEquals("Bearer fake-token", req.getHeader("Authorization"))
        // Sayaç yanıt döndükten sonra artar (istek sunucuya ulaştığı an değil)
        val deadline = System.currentTimeMillis() + 5_000
        while (reporter.sentCount < 1 && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(1, reporter.sentCount)

        // Bağlantı yokken bekler; gelince gönderilir
        TestAccount.connectivity.set(false)
        reporter.send(AppErrors.build(ReportKind.CRASH, "crash", IllegalStateException("x"), "main"), null)
        assertNull(received.poll(1500, TimeUnit.MILLISECONDS))
        TestAccount.connectivity.set(true)
        runBlocking { koin<SyncEngine>().process() }
        val second = received.poll(10, TimeUnit.SECONDS)
        assertNotNull(second)
        assertEquals("CRASH", Json.parseToJsonElement(second!!.body.readUtf8()).jsonObject["kind"]!!.jsonPrimitive.content)

        // Tercih kalıcı ve kurulum kimliği değişmez
        reporter.setPolicy(ReportPolicy.NEVER)
        val again = ErrorReporter(ApplicationProvider.getApplicationContext(), koin(), koin(), null)
        assertEquals(ReportPolicy.NEVER, again.policy)
        assertEquals(reporter.installId, again.installId)
    }
}

/**
 * v37 (1.2) rapor penceresi uygulamanın her yerinde açılır: ne olduğu, ayrıntılar, not, Gönder / Gönderme. Ayarlar →
 * Gelişmiş → Hata raporları: tercih ve "Sorun bildir" (not yazılmadan gönderilemez).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V37ErrorReportUiTest : V33EditorBase() {
    @After fun clean() { AppErrors.resetForTest(); TestAccount.reset() }

    @Test
    fun reportSheetAndSettings() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        // Açılıştaki gönderici bağlandıktan sonra sahtesi konur
        waitFor { AppErrors.sink is ErrorReporter }
        val reporter = AppErrors.sink as ErrorReporter
        val sink = RecordingSink(ReportPolicy.ASK)
        AppErrors.sink = sink
        // v40 sorun bildirmek giriş ister: girişli kullanıcı
        TestAccount.auth.signedIn = FakeAuth.DEFAULT
        runBlocking {
            GlobalContext.get().get<com.karapp.drawguidance.data.local.LocalUserDao>().upsert(
                com.karapp.drawguidance.data.local.LocalUserEntity("u1", FakeAuth.DEFAULT.firebaseUid, "Ahmet Kar", "Ahmet", "Kar", "ahmet@example.com", true, null, 1L, "1990-01-01", true, 1, 1),
            )
        }
        waitFor { GlobalContext.get().get<com.karapp.drawguidance.data.account.AccountRepository>().user.value?.signedIn == true }
        compose.runOnIdle { AppErrors.report("canvas.export", RuntimeException("disk dolu"), userVisible = true) }
        waitFor { exists("error_report_sheet") }
        assertTrue(compose.onAllNodes(hasTestTag("error_report_title") and hasText("Bir sorun oluştu")).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("error_report_details_toggle").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("error_report_details") }
        compose.onNodeWithTag("error_report_note").performTextInput("PNG kaydederken")
        shot("v37-error-report")
        compose.onNodeWithTag("error_report_send").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { !exists("error_report_sheet") }
        assertEquals("PNG kaydederken", sink.sent.single().second)
        assertEquals("canvas.export", sink.sent.single().first.feature)

        // Ayarlar → Gelişmiş → Hata raporları
        AppErrors.sink = reporter
        compose.onNode(hasText("Ayarlar") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("settings_tab_advanced") }
        compose.onNodeWithTag("settings_tab_advanced").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("reports_card") }
        compose.onNodeWithTag("reports_policy_never").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { reporter.policy == ReportPolicy.NEVER }
        compose.onNodeWithTag("reports_policy_ask").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { reporter.policy == ReportPolicy.ASK }
        shot("v37-settings-reports")
        // Sorun bildir: not yazılmadan Gönder kapalı
        compose.onNodeWithTag("reports_user_report").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("error_report_sheet") }
        assertTrue(compose.onAllNodes(hasTestTag("error_report_title") and hasText("Sorun bildir")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasTestTag("error_report_send") and isEnabled()).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("error_report_dismiss").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { !exists("error_report_sheet") }
    }
}
