package com.karapp.drawguidance

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * v42 Ayarlar, cihazın yerel ayarı Türkçeyken (JVM varsayılanı tr-TR: "I/ı" büyük-küçük harf dönüşümü, ondalık
 * virgül) Android 10'da açılır, bütün sekmeler kaydırılır. Robolectric'in kendi kurulumu Türkçe yerel ayarda
 * çalışmadığından yerel ayar test başında değiştirilir.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], application = HomeNoWelcomeApp::class, qualifiers = "tr-rTR-w411dp-h891dp-xxhdpi")
class V42SettingsLocaleTest : V33EditorBase() {
    private val original = Locale.getDefault()

    @Before fun turkish() = Locale.setDefault(Locale.forLanguageTag("tr-TR"))

    @After fun restore() = Locale.setDefault(original)

    @Test
    fun settingsOpenWithTurkishDefaultLocale() {
        waitFor { compose.onAllNodesWithText("Ayarlar").fetchSemanticsNodes().isNotEmpty() }
        TestAccount.signInLocal()
        compose.onAllNodesWithText("Ayarlar").onFirst().performClick()
        waitFor { exists("language_card") }
        for (tab in listOf("general", "drawing", "saving", "connection", "advanced")) {
            compose.onAllNodes(hasTestTag("settings_tab_$tab")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
            repeat(6) { compose.onRoot().performTouchInput { swipeUp() }; compose.waitForIdle() }
        }
        compose.onAllNodes(hasTestTag("settings_tab_general")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("settings_subscription") }
        compose.onAllNodes(hasTestTag("settings_subscription")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("subscription_screen") }
        assertTrue(exists("plan_premium"))
    }
}
