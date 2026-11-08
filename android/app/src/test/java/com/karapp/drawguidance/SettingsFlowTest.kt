package com.karapp.drawguidance

import android.content.Intent
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.settings.ServerConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Sunucu adresinin intent ile ayarlanması ve Ayarlar ekranı (gerçek backend'e bağlantı testi). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @After
    fun tearDown() = stopKoin()

    private fun waitFor(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            if (System.currentTimeMillis() > deadline) throw AssertionError("Koşul $timeoutMs ms içinde sağlanmadı")
            Thread.sleep(50)
        }
    }

    private fun waitForText(text: String, substring: Boolean = false, timeoutMs: Long = 10_000) = waitFor(timeoutMs) {
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun launch(serverUrl: String?): ActivityScenario<MainActivity> {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        if (serverUrl != null) intent.putExtra(MainActivity.EXTRA_SERVER_URL, serverUrl)
        return ActivityScenario.launch(intent)
    }

    /**
     * Hata ayıklama sürümünde Telefonda-Ac.bat'in yaptığı gibi uygulama server_url ile açılır (port yazılmadan) ve
     * adres kaydedilir. v39 Ayarlar'da sunucu adresi yok; Bağlantı sekmesinde tek eşitleme kartı var, "Şimdi eşitle"
     * gerçek sunucuyla eşitler.
     */
    @Test
    fun serverUrlExtraIsSavedAndSyncCardSyncs() {
        launch("127.0.0.1").use {
            val config: ServerConfig = GlobalContext.get().get()
            assertEquals("http://127.0.0.1:4747/", config.baseUrl.value)
            waitForText("Henüz bir tuvaliniz yok", substring = true)
            compose.onNodeWithText("Ayarlar").performSemanticsAction(SemanticsActions.OnClick)
            waitFor { compose.onAllNodes(hasTestTag("settings_tab_connection")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasTestTag("settings_tab_connection")).performSemanticsAction(SemanticsActions.OnClick)
            waitFor { compose.onAllNodes(hasTestTag("sync_control_card")).fetchSemanticsNodes().isNotEmpty() }
            // v44 sunucu seçimi ve sunucu ayarları kaldırıldı: Bağlantı sekmesinde yalnız eşitleme kartı
            assertTrue(compose.onAllNodes(hasTestTag("server_choice_card")).fetchSemanticsNodes().isEmpty())
            assertTrue(compose.onAllNodes(hasTestTag("server_settings_card")).fetchSemanticsNodes().isEmpty())
            val engine: com.karapp.drawguidance.data.sync.SyncEngine = GlobalContext.get().get()
            // v43 "Şimdi eşitle" düğmesi yok: eşitleme her zaman açık (kart bunu söyler)
            assertTrue(compose.onAllNodes(hasTestTag("sync_always_hint")).fetchSemanticsNodes().isNotEmpty())
            assertTrue(compose.onAllNodes(hasTestTag("sync_now")).fetchSemanticsNodes().isEmpty())
            kotlinx.coroutines.runBlocking { engine.process() }
            waitFor(15_000) { engine.lastRun.value != null }
            waitFor { compose.onAllNodesWithText("henüz yok", substring = true).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("Tuvallerim").performSemanticsAction(SemanticsActions.OnClick)
            waitForText("Henüz bir tuvaliniz yok", substring = true)
        }
    }
}
