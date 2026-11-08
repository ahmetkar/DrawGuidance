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
import com.karapp.drawguidance.data.local.LocalUserDao
import com.karapp.drawguidance.data.local.LocalUserEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v40 Android 10'da (API 29) Ayarlar açılınca uygulama kapanıyordu: Ayarlar ekranı giriş yapılmamışken ve (v41)
 * profil fotoğraflı girişli hesapla açılır, bütün sekmeler baştan sona kaydırılır, abonelik ekranı açılır. Android
 * 10'un çerçevesinde olmayan bir API çağrılırsa test NoSuchMethodError/NoClassDefFoundError ile düşer.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], application = HomeNoWelcomeApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V40Android10Test : V33EditorBase() {
    @After fun resetAccount() = TestAccount.reset()

    private fun openSettings() {
        waitFor { compose.onAllNodesWithText("Ayarlar").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Ayarlar").onFirst().performClick()
        waitFor { exists("language_card") }
    }

    private fun scrollAll(times: Int = 10) = repeat(times) {
        compose.onRoot().performTouchInput { swipeUp() }
        compose.waitForIdle()
    }

    /** Bütün sekmeler açılır ve kaydırılır, abonelik sayfası açılır. */
    private fun everyTabAndSubscription(prefix: String) {
        // v43 abonelik satırı yalnız giriş yapılmışken
        TestAccount.signInLocal()
        for (tab in listOf("general", "drawing", "saving", "connection", "advanced")) {
            compose.onAllNodes(hasTestTag("settings_tab_$tab")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
            scrollAll(6)
            if (tab == "general") shot("$prefix-settings")
        }
        compose.onAllNodes(hasTestTag("settings_tab_general")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("settings_subscription") }
        compose.onAllNodes(hasTestTag("settings_subscription")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("subscription_screen") }
        scrollAll(4)
        shot("$prefix-subscription")
        assertTrue(exists("sub_section_yours") && exists("sub_billing"))
    }

    @Test
    fun settingsAndSubscriptionOpenOnAndroid10() {
        openSettings()
        everyTabAndSubscription("v40-android10")
    }

    /** v41 girişli kullanıcı (profil fotoğrafı, hesap başlığı, Çıkış yap) ile aynı yol. */
    @Test
    fun signedInSettingsOpenOnAndroid10() {
        TestAccount.auth.signedIn = FakeAuth.DEFAULT
        runBlocking {
            GlobalContext.get().get<LocalUserDao>().upsert(
                LocalUserEntity(
                    "u1", FakeAuth.DEFAULT.firebaseUid, "Ahmet Kar", "Ahmet", "Kar", "ahmet@example.com", true,
                    "https://example.com/foto.png", 1_700_000_000_000L, "1990-01-01", true, 1, 1, termsAcceptedAt = 1L,
                ),
            )
        }
        openSettings()
        waitFor { exists("account_name") }
        assertTrue(exists("account_sign_out"))
        everyTabAndSubscription("v41-android10-signed-in")
    }
}
