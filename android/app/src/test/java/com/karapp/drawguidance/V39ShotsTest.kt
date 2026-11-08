package com.karapp.drawguidance

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.billing.Entitlements
import com.karapp.drawguidance.data.billing.PremiumFeature
import com.karapp.drawguidance.data.billing.SubscriptionRepository
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.premium.PremiumScope
import com.karapp.drawguidance.ui.screens.home.ChallengeHomeCard
import com.karapp.drawguidance.ui.screens.home.PremiumHomeCard
import com.karapp.drawguidance.ui.screens.subscription.SubscriptionScreen
import com.karapp.drawguidance.ui.screens.subscription.SubscriptionViewModel
import com.karapp.drawguidance.ui.theme.DrawGuidanceTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v39 görsel kontrol (DG_SHOTS verilince ekran görüntüsü): abonelik sayfası, Ana sayfa kartları, kilitli öge ve pencere. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "tr-w411dp-h891dp-xxhdpi")
class V39ShotsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun stop() { TestAccount.reset(); org.koin.core.context.stopKoin() }

    private fun shot(name: String) {
        val dir = System.getenv("DG_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        repeat(4) { Thread.sleep(80); compose.waitForIdle(); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }
        val root = compose.activity.window.decorView
        val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { root.draw(android.graphics.Canvas(bitmap)) }
        java.io.File(dir).mkdirs()
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun subscriptionFree() {
        TestAccount.entitlements.value = null
        val repo = GlobalContext.get().get<SubscriptionRepository>()
        runBlocking { repo.seed() }
        val vm = SubscriptionViewModel(repo)
        compose.setContent { DrawGuidanceTheme { SubscriptionScreen(onBack = {}, vm = vm) } }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("plan_premium").assertExists() }.isSuccess }
        shot("v39_subscription_free")
    }

    @Test
    fun homeCardsAndLockedSheet() {
        TestAccount.entitlements.value = Entitlements.FREE
        compose.setContent {
            DrawGuidanceTheme {
                PremiumScope(CanvasKind.COMIC, {}) {
                    Column(Modifier.padding(16.dp)) {
                        ChallengeHomeCard(passed = 0, nextLevel = 1, onContinue = {})
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                        ChallengeHomeCard(passed = 3, nextLevel = 4, onContinue = {})
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                        PremiumHomeCard(signedIn = true, onClick = {})
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                        androidx.compose.foundation.layout.Row {
                            com.karapp.drawguidance.ui.screens.canvasedit.components.ComicTile("Webtoon", false, {}, Modifier.weight(1f), "tile", premium = PremiumFeature.COMIC_TEMPLATES) {
                                androidx.compose.foundation.layout.Box(Modifier.padding(30.dp))
                            }
                            com.karapp.drawguidance.ui.screens.canvasedit.components.ComicTile("Temel", false, {}, Modifier.weight(1f), "tile2") {
                                androidx.compose.foundation.layout.Box(Modifier.padding(30.dp))
                            }
                        }
                    }
                }
            }
        }
        shot("v39_home_cards_locked_tile")
        compose.onNodeWithTag("tile_locked").performClick()
        compose.waitForIdle()
        shot("v39_premium_required_sheet")
    }
}
