package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL

/** Test uygulaması: Ayarlar'daki bağlantı testi gerçek backend'e (127.0.0.1:4747) gider. */
open class TestDrawGuidanceApp : DrawGuidanceApp() {
    override val apiBaseUrl: String = "http://127.0.0.1:4747/"
    // v30 eski testler Tuvallerim'den, karşılama ekranı olmadan başlar
    override val startOnHome: Boolean = false
    override val showWelcome: Boolean = false
    // v32 eski testlerde ilk girişte eğitim önerisi açılmaz
    override val offerTutorials: Boolean = false
    // v40 eski testlerde çocuk tuvaline girişte maskotun görev önerisi açılmaz
    override val offerChallengeSuggestion: Boolean = false
    // v40 eski testlerde bildirim izni açıklaması açılmaz
    override val askNotificationPermission: Boolean = false
    // v33 testlerde bağlantı ve Google girişi elle yönetilir; açılışta olay tablosu kendiliğinden işlenmez
    override val autoSync: Boolean = false
    override fun createConnectivity(): com.karapp.drawguidance.data.sync.Connectivity = TestAccount.connectivity
    override fun createAuthProvider(): com.karapp.drawguidance.data.account.AuthProvider = TestAccount.auth
    // v39 testlerde mağaza ve reklam yok; haklar elle (eski testler Premium)
    override fun createBillingGateway(): com.karapp.drawguidance.data.billing.BillingGateway = com.karapp.drawguidance.data.billing.UnavailableBillingGateway()
    override fun createAdsGateway(): com.karapp.drawguidance.data.ads.AdsGateway = com.karapp.drawguidance.data.ads.NoOpAdsGateway()
    override fun createEntitlementsOverride() = TestAccount.entitlements

    override fun onCreate() {
        ManifestFlags.mirrorBackCallbackFlag(this)
        super.onCreate()
    }
}

/**
 * Uygulamanın ana akışını gerçek Compose ekranları, gerçek Room (SQLite) ve
 * uçtan uca doğrular. Çizimler yalnızca cihazda saklanır; hiçbir çizim verisi sunucuya gitmez.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class CanvasFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() = stopKoin()

    private fun repository(): CanvasRepository = GlobalContext.get().get()

    private fun canvases(): List<CanvasUi> = runBlocking { repository().observeCanvases().first() }

    /**
     * Koşul sağlanana kadar bekler. Robolectric'te ana döngü (main looper) elle
     * ilerletilmezse arka plan işlerinin (Room, ağ) sonuçları UI'ya ulaşmaz; bu
     * yüzden her denemede looper ve Compose boşa alınır.
     */
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

    /** v47 Özellikler (eski Fırça ayarları) düğmesi: dikeyde mod menüsünün son grubunda. */
    private fun openProperties() {
        val node = androidx.compose.ui.test.hasContentDescription("Özellikler") and androidx.compose.ui.test.hasClickAction()
        waitFor { compose.onAllNodes(node).fetchSemanticsNodes().isNotEmpty() }
        runCatching { compose.onAllNodes(node)[0].performScrollTo() }
        compose.onAllNodes(node)[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun waitForText(text: String, substring: Boolean = false, timeoutMs: Long = 10_000) = waitFor(timeoutMs) {
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun httpGet(path: String): Pair<Int, String> {
        val conn = URL("http://127.0.0.1:4747$path").openConnection() as HttpURLConnection
        return try {
            val code = conn.responseCode
            val body = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""
            code to body
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Bottom sheet, dropdown menü ve dialog ayrı pencerelerde açılır; Robolectric bu
     * pencerelere dokunma olayı ulaştıramadığı için bu elemanlarda tıklama,
     * erişilebilirlik (semantics) OnClick aksiyonuyla yapılır.
     */
    private fun SemanticsNodeInteraction.clickInPopup() {
        performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun drawStroke(dx: Float, dy: Float) {
        compose.waitForIdle()
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(dx / 20f, dy / 20f)) }
            up()
        }
    }

    @Test
    fun createDrawUpdateDeleteLocallyWithoutCloud() {
        // 1) Boş tuval listesi
        waitForText("Henüz bir tuvaliniz yok", substring = true)

        // 2) Yeni Tuval modalı: cloud seçeneği yok, yalnızca yapay zeka anahtarı (v30: varsayılan AÇIK)
        compose.onNodeWithContentDescription("Yeni Tuval").performClick()
        waitForText("Yeni Tuval Oluştur")
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onAllNodes(isToggleable())[0].assertIsOn()
        assertTrue(compose.onAllNodesWithText("Cloud", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        compose.onNode(hasSetTextAction()).performTextInput("Test Tuvali")
        compose.onNodeWithText("Oluştur").clickInPopup()

        // 3) Oluşturunca Tuval Düzenleme ekranına gidilir
        waitFor(10_000) { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Test Tuvali").assertIsDisplayed()
        val created = canvases().single()
        assertEquals(true, created.aiEnabled)

        // 4) Çizim yapılır ve Room'a kaydedilir
        drawStroke(200f, 150f)
        waitFor(10_000) { canvases().single().strokes.size == 1 }
        assertEquals(BrushType.PENCIL, canvases().single().strokes[0].brushType)

        // 5) Fırça seçim modalı: Marker seçilir, sonraki çizim marker ile yapılır
        compose.onNodeWithContentDescription("Fırça Seç").performScrollTo().performClick()
        waitForText("Fosforlu Kalem")
        compose.onNodeWithText("Marker").clickInPopup()
        waitFor(5_000) { compose.onAllNodesWithText("Fosforlu Kalem").fetchSemanticsNodes().isEmpty() }
        drawStroke(-150f, 100f)
        waitFor(10_000) { canvases().single().strokes.size == 2 }
        assertEquals(BrushType.MARKER, canvases().single().strokes[1].brushType)

        // 6) Yan panel: boyut / renk / vurgu şiddeti ayarları
        openProperties()
        waitForText("Fırça Boyutu")
        compose.onNodeWithText("Renk").assertExists()
        compose.onNodeWithText("Opaklık").assertExists()
        compose.onNodeWithText("Akışkanlık").assertExists()
        compose.onNodeWithText("Çizim Gücü").assertExists()
        // Sıra: boyut, opaklık, akışkanlık, çizim gücü (v20: ardından fırça başına ayarlar; boyut kaydırıcısı üstel 0..1)
        val sliders = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        assertTrue(sliders.fetchSemanticsNodes().size >= 4)
        assertEquals("brush_size_slider", sliders[0].fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag))
        sliders[0].performSemanticsAction(SemanticsActions.SetProgress) { it(com.karapp.drawguidance.ui.screens.canvasedit.components.SizeScale.toPosition(32f)) }
        sliders[1].performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        sliders[2].performSemanticsAction(SemanticsActions.SetProgress) { it(0.3f) }
        sliders[3].performSemanticsAction(SemanticsActions.SetProgress) { it(0.6f) }
        // Panel çizim alanının üstüne açıldığı için çizmeden önce kapatılır
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        waitFor(5_000) { compose.onAllNodesWithText("Fırça Boyutu").fetchSemanticsNodes().isEmpty() }
        drawStroke(-100f, -120f)
        waitFor(10_000) { canvases().single().strokes.size == 3 }
        val third = canvases().single().strokes[2]
        assertEquals(32f, third.sizePx, 0.5f)
        assertEquals(0.5f, third.opacity, 0.01f)
        assertEquals(0.3f, third.flow, 0.01f)
        assertEquals(0.6f, third.strength, 0.01f)

        // 7) Listeye dönülür; kart görünür
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor(10_000) { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isEmpty() }
        waitForText("Test Tuvali")

        // 8) Karta uzun basınca üst menü: resim olarak kaydet ve diğer seçenekler var, cloud senkron yok
        compose.onAllNodesWithText("Test Tuvali")[0].performTouchInput { longClick() }
        waitFor(5_000) {
            compose.onAllNodesWithContentDescription("Diğer seçenekler").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Resim olarak kaydet").assertExists()
        assertTrue(compose.onAllNodesWithContentDescription("Cloud", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        // Sunucuda çizim kaydı ucu yok; çizimler yalnızca yerel veritabanında
        assertEquals(404, httpGet("/api/canvases").first)
        assertEquals(3, canvases().single().strokes.size)

        // 9) Context menü -> Tuval Ayarları modalı -> ad güncellenir
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        waitForText("Tuval Ayarları")
        compose.onNodeWithText("Tuval Ayarları").clickInPopup()
        waitFor(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement("Güncel Tuval")
        compose.onNodeWithText("Güncelle").clickInPopup()
        waitFor(10_000) { canvases().single().name == "Güncel Tuval" }

        // 10) Context menü -> Tuvali Sil -> onay -> yerelden silinir
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        waitForText("Tuvali Sil")
        compose.onNodeWithText("Tuvali Sil").clickInPopup()
        waitForText("kalıcı olarak silinecek", substring = true)
        compose.onNode(hasText("Tuvali Sil") and hasClickAction()).clickInPopup()
        waitFor(10_000) { canvases().isEmpty() }
        waitForText("Henüz bir tuvaliniz yok", substring = true)
    }
}
