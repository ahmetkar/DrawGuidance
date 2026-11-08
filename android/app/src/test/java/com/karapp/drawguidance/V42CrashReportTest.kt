package com.karapp.drawguidance

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.diagnostics.AppErrors
import com.karapp.drawguidance.diagnostics.CrashExport
import com.karapp.drawguidance.diagnostics.ReportKind
import com.karapp.drawguidance.diagnostics.SessionWatch
import com.karapp.drawguidance.ui.components.ErrorReportSheet
import com.karapp.drawguidance.ui.theme.DrawGuidanceTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/**
 * v42 hata raporu sunucusuz da iletilir: ön plandayken beklenmedik kapanma (yerel çökme, donma, bellek) açılışta
 * rapor olur; rapor penceresinde Kopyala ve Paylaş (e-posta) var, metinde sürüm, cihaz, bellek ve son ekran yazar.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], application = TestDrawGuidanceApp::class)
class V42CrashReportTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun reset() {
        SessionWatch.enabled = false
        AppErrors.resetForTest()
        AppErrors.clearPendingCrash(context)
        org.koin.core.context.GlobalContext.stopKoin()
    }

    @Test
    fun unexpectedExitWhileForegroundBecomesAReport() {
        SessionWatch.enabled = true
        // önceki çalışma: Ayarlar açıkken kapandı (oturum dosyası silinmedi)
        File(context.filesDir, "diagnostics").mkdirs()
        File(context.filesDir, "diagnostics/session-open.txt").writeText("1700000000000\nsettings\n")
        File(context.filesDir, "diagnostics/breadcrumbs.txt").writeText("1 ekran: home\n2 ekran: settings")
        assertTrue(SessionWatch.checkPrevious(context))
        assertFalse(File(context.filesDir, "diagnostics/session-open.txt").exists())
        AppErrors.checkPendingCrash(context)
        val r = AppErrors.prompt.value
        assertNotNull(r)
        assertEquals(ReportKind.CRASH, r!!.kind)
        assertTrue(r.feature, r.feature.contains("settings"))
        assertTrue(r.stackTrace.contains("Bellek"))
        assertTrue(r.breadcrumbs.any { it.contains("settings") })
        // ikinci açılışta yeniden sorulmaz (dosya yok)
        assertFalse(SessionWatch.checkPrevious(context))
    }

    @Test
    fun javaCrashIsNotDuplicatedAsUnexpectedExit() {
        SessionWatch.enabled = true
        File(context.filesDir, "diagnostics").mkdirs()
        File(context.filesDir, "diagnostics/session-open.txt").writeText("1700000000000\nsettings\n")
        AppErrors.writeCrash(context, Thread.currentThread(), IllegalStateException("deneme"))
        assertFalse(SessionWatch.checkPrevious(context))
        AppErrors.checkPendingCrash(context)
        val r = AppErrors.prompt.value!!
        assertTrue(r.stackTrace.contains("deneme"))
        assertTrue("çökmede bellek bilgisi", r.stackTrace.contains("Bellek"))
    }

    @Test
    fun reportCanBeCopiedWithoutSignIn() {
        val report = AppErrors.build(ReportKind.CRASH, "crash", IllegalStateException("Ayarlar açılamadı"), "main")
        val text = CrashExport.text(context, report, "Ayarlar'a dokununca")
        assertTrue(text.contains("Android"))
        assertTrue(text.contains("Ayarlar açılamadı"))
        assertTrue(text.contains("Ayarlar'a dokununca"))
        assertTrue(text.contains(BuildConfig.VERSION_NAME))
        compose.setContent { DrawGuidanceTheme { ErrorReportSheet(report, onSend = {}, onDismiss = {}, signedIn = false) } }
        compose.onNodeWithTag("error_report_copy").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        assertNotNull("panoda rapor", clip)
        assertTrue(clip!!.getItemAt(0).text.toString().contains("Ayarlar açılamadı"))
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("error_report_copied", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // Paylaş: e-posta seçici açılır
        compose.onNodeWithTag("error_report_share").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
        val started = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertNotNull(started)
        assertEquals(android.content.Intent.ACTION_CHOOSER, started.action)
    }
}
