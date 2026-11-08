package com.karapp.drawguidance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.local.LocalUserDao
import com.karapp.drawguidance.data.settings.ServerConfig
import com.karapp.drawguidance.ui.components.rememberKeyedThumb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v37 (1) seçici pencerelerdeki küçük resimler: anahtar (sekme/öğe) değişince eski resim kalmaz; önbellekteki resim
 * aynı karede gelir, önbellekte yoksa üretilir; üretim hata verirse başka öğenin resmi görünmez.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V37ThumbTest {
    @get:Rule val compose = createComposeRule()

    @After fun tearDown() = org.koin.core.context.stopKoin()

    @Test
    fun keyChangeNeverKeepsAnotherItemsThumbnail() {
        val a = ImageBitmap(2, 2)
        val b = ImageBitmap(3, 3)
        val cache = HashMap<String, ImageBitmap>().apply { put("a", a) }
        val renders = ArrayList<String>()
        var key by mutableStateOf("a")
        var shown: ImageBitmap? = null
        compose.setContent {
            shown = rememberKeyedThumb(key, { cache[it] }, dispatcher = Dispatchers.Unconfined) { k ->
                renders += k
                when (k) {
                    "b" -> b.also { cache[k] = it }
                    "boom" -> error("çizilemedi")
                    else -> null
                }
            }
        }
        compose.waitForIdle()
        // Önbellekte olan: hemen gelir, yeniden üretilmez
        assertSame(a, shown)
        assertTrue(renders.isEmpty())
        // Sekme değişti: "a"nın resmi kalmaz, "b" üretilir
        compose.runOnIdle { key = "b" }
        compose.waitForIdle()
        compose.waitUntil(5_000) { shown === b }
        assertEquals(listOf("b"), renders)
        // Geri dönünce önbellekten
        compose.runOnIdle { key = "a" }
        compose.waitForIdle()
        assertSame(a, shown)
        compose.runOnIdle { key = "b" }
        compose.waitForIdle()
        assertSame(b, shown)
        assertEquals(listOf("b"), renders)
        // Üretim hata verirse boş kalır (önceki öğenin resmi değil)
        compose.runOnIdle { key = "boom" }
        compose.waitForIdle()
        assertNull(shown)
    }
}

/**
 * v37 hesap ayarları: "Çıkış yap" hesap silmenin üstünde, ayrı bir kartta; aralarında "Tehlikeli bölge" ayracı ve
 * geniş boşluk var. Çıkış onaylanınca hesap silinmeden oturum kapanır ve giriş düğmesi görünür.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HomeNoWelcomeApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V37AccountSignOutTest : V33EditorBase() {
    private inline fun <reified T : Any> koin(): T = GlobalContext.get().get()
    private lateinit var server: FakeUserServer

    @Before fun startServer() {
        TestAccount.reset()
        server = FakeUserServer().start()
    }

    @After fun stopServer() { server.stop(); TestAccount.reset() }

    private fun user() = runBlocking { koin<LocalUserDao>().get() }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    fun signOutSitsAboveDeleteAndIsClearlySeparated() {
        koin<ServerConfig>().update(server.url())
        waitFor { exists("home_sign_in_card") }
        compose.onNodeWithTag("home_sign_in").performClick()
        waitFor { exists("birth_date_dialog") }
        compose.onNodeWithTag("birth_day").performTextInput("17")
        compose.onNodeWithTag("birth_month").performTextInput("5")
        compose.onNodeWithTag("birth_year").performTextInput("1990")
        compose.onNodeWithTag("consent_terms").performScrollTo().performClick()
        compose.onNodeWithTag("consent_kvkk").performScrollTo().performClick()
        compose.onNodeWithTag("birth_date_ok").performClick()
        waitFor { exists("home_profile") }
        assertNotNull(user())

        compose.onNodeWithTag("home_profile").performClick()
        waitFor { exists("account_screen") && exists("account_session_card") }
        compose.onNodeWithTag("account_delete").performScrollTo()
        compose.waitForIdle()
        val session = bounds("account_session_card")
        val zone = bounds("account_danger_zone")
        val delete = bounds("account_delete")
        // Sıra: çıkış kartı → tehlikeli bölge ayracı → hesabı sil
        assertTrue("çıkış silmenin üstünde", session.bottom < zone.top && zone.bottom < delete.top)
        // İkisi birbirinden belirgin biçimde ayrı (en az 40 dp)
        val gap = with(compose.density) { 40.dp.toPx() }
        assertTrue("çıkış ile silme arasında boşluk ${delete.top - session.bottom}", delete.top - session.bottom >= gap)
        shot("v37-account-sign-out")

        compose.onNodeWithTag("account_screen_sign_out").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("account_sign_out_confirm") }
        compose.onNodeWithTag("account_sign_out_confirm").performClick()
        // Oturum kapandı; hesap silinmedi (sunucuya DELETE gitmedi)
        waitFor { exists("account_screen_sign_in") }
        assertNull(TestAccount.auth.signedIn)
        assertEquals(0, TestAccount.auth.deletes)
        assertEquals(0, server.count("DELETE"))
    }
}
