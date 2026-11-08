package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.account.AccountDeleteException
import com.karapp.drawguidance.data.account.AccountRepository
import com.karapp.drawguidance.data.account.AuthException
import com.karapp.drawguidance.data.account.DeleteResult
import com.karapp.drawguidance.data.account.SignInResult
import com.karapp.drawguidance.data.local.LocalUserDao
import com.karapp.drawguidance.data.settings.ServerConfig
import com.karapp.drawguidance.data.sync.SyncEngine
import com.karapp.drawguidance.data.sync.SyncTypes
import com.karapp.drawguidance.ui.screens.account.deletePhraseMatches
import com.karapp.drawguidance.ui.screens.account.shortId
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.SelectionKind
import com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/** Kullanıcı ucunu taklit eden sunucu: GET 404, PUT yankı, DELETE [deleteCode]; istekler kaydedilir. */
internal class FakeUserServer {
    val server = MockWebServer()
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    @Volatile var deleteCode = 204
    @Volatile var putCode = 200

    fun start(): FakeUserServer {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path != "/api/v1/users/me") return MockResponse().setResponseCode(404)
                requests += request
                return when (request.method) {
                    "GET" -> MockResponse().setResponseCode(404).setBody("""{"error":"user_not_found","message":"yok"}""")
                    "PUT" -> if (putCode != 200) MockResponse().setResponseCode(putCode).setBody("""{"error":"down","message":"down"}""")
                    else MockResponse().setBody(
                        request.body.readUtf8().replace(Regex("\"id\":\"[^\"]*\""), "\"id\":\"sunucu-1\"").replace(Regex("\"id\":null"), "\"id\":\"sunucu-1\""),
                    )
                    "DELETE" -> if (deleteCode == 204) MockResponse().setResponseCode(204)
                    else MockResponse().setResponseCode(deleteCode).setBody("""{"error":"down","message":"down"}""")
                    else -> MockResponse().setResponseCode(405)
                }
            }
        }
        server.start()
        return this
    }

    fun count(method: String) = requests.count { it.method == method }
    fun url() = server.url("/").toString()
    fun stop() = server.shutdown()
}

/**
 * v34 (3) hesap silme (depo düzeyinde): kimlik yeniden doğrulanır; bekleyen kullanıcı yazma işi silinir; sunucudan
 * (DELETE), Firebase'den ve cihazdan silinir. Sunucuya ulaşılamazsa hiçbir şey silinmez ve yazılmamış değişiklik
 * yeniden sıraya girer; Firebase silinemezse oturum kapanır ve cihazdaki bilgiler yine silinir.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V34AccountDeleteTest {
    private inline fun <reified T : Any> koin(): T = GlobalContext.get().get()
    private lateinit var server: FakeUserServer
    private val activity by lazy { Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get() }

    @Before fun setUp() {
        TestAccount.reset()
        server = FakeUserServer().start()
        koin<ServerConfig>().update(server.url())
    }

    @After fun tearDown() { server.stop(); TestAccount.reset(); org.koin.core.context.stopKoin() }

    private fun repo() = koin<AccountRepository>()
    private fun pending() = runBlocking { koin<SyncEngine>().pendingCount.first() }
    private fun user() = runBlocking { koin<LocalUserDao>().get() }

    /** Bağlantısızken girilir: kullanıcı cihazda, sunucuya yazma işi bekliyor. */
    private fun signInOffline() = runBlocking {
        TestAccount.connectivity.set(false)
        val r = repo().signIn(activity)
        val acc = (r as SignInResult.NeedsBirthDate).account
        repo().complete(acc, LocalDate.of(1990, 5, 17))
        assertEquals(1, pending())
        TestAccount.connectivity.set(true)
    }

    @Test
    fun deletesServerFirebaseLocalAndPendingJob() = runBlocking {
        signInOffline()
        assertNotNull(user())
        val result = repo().deleteAccount(activity)
        assertEquals(DeleteResult.DONE, result)
        assertEquals(1, TestAccount.auth.reauths)
        assertEquals(1, TestAccount.auth.deletes)
        assertNull(user())
        assertNull(TestAccount.auth.signedIn)
        // Bekleyen kullanıcı yazma işi silindi: sunucuya hiç PUT gitmedi, DELETE jetonla gitti
        assertEquals(0, pending())
        assertEquals(0, server.count("PUT"))
        assertEquals(1, server.count("DELETE"))
        assertEquals("Bearer fake-token", server.requests.first { it.method == "DELETE" }.getHeader("Authorization"))
        assertTrue(koin<com.karapp.drawguidance.data.local.SyncEventDao>().pending().none { it.type == SyncTypes.USER_UPSERT })
    }

    @Test
    fun serverFailureDeletesNothingAndRequeues() = runBlocking {
        signInOffline()
        server.deleteCode = 503
        server.putCode = 503
        val error = runCatching { repo().deleteAccount(activity) }.exceptionOrNull()
        assertTrue("$error", error is AccountDeleteException && error.stage == AccountDeleteException.Stage.SERVER)
        // Hiçbir şey silinmedi: cihazdaki kullanıcı ve Firebase oturumu duruyor; yazma işi yeniden sırada
        assertNotNull(user())
        assertTrue(user()!!.signedIn)
        assertEquals(0, TestAccount.auth.deletes)
        assertNotNull(TestAccount.auth.signedIn)
        waitUntil { pending() == 1 }
        assertEquals(1, koin<com.karapp.drawguidance.data.local.SyncEventDao>().pending().count { it.type == SyncTypes.USER_UPSERT })
    }

    @Test
    fun firebaseFailureStillClearsLocalAndSignsOut() = runBlocking {
        signInOffline()
        TestAccount.auth.deleteFails = true
        assertEquals(DeleteResult.FIREBASE_FAILED, repo().deleteAccount(activity))
        assertEquals(1, server.count("DELETE"))
        assertNull(user())
        assertNull(TestAccount.auth.signedIn)
    }

    @Test
    fun cancelledReauthDeletesNothing() = runBlocking {
        signInOffline()
        TestAccount.auth.reauthFailWith = AuthException.Reason.CANCELLED
        val error = runCatching { repo().deleteAccount(activity) }.exceptionOrNull()
        assertTrue(error is AuthException)
        assertEquals(0, server.count("DELETE"))
        assertNotNull(user())
        assertEquals(1, pending())
    }

    @Test
    fun phraseAndShortId() {
        val phrase = "hesabımı silmek istiyorum"
        val tr = Locale.forLanguageTag("tr")
        assertTrue(deletePhraseMatches("  HESABIMI  silmek istiyorum ", phrase, tr))
        assertTrue(deletePhraseMatches("Hesabımı silmek istiyorum", phrase, Locale.US))
        assertFalse(deletePhraseMatches("hesabımı silmek", phrase, tr))
        assertFalse(deletePhraseMatches("", phrase, tr))
        assertEquals("abcdef…wxyz", shortId("abcdefghijklmnopqrstuvwxyz"))
        assertEquals("kisa-id", shortId("kisa-id"))
    }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("koşul gerçekleşmedi")
            Thread.sleep(30)
        }
    }
}

/** Ana sayfa testleri için: karşılama ekranı olmadan doğrudan Ana sayfa. */
class HomeNoWelcomeApp : TestDrawGuidanceApp() {
    override val startOnHome: Boolean = true
    override val showWelcome: Boolean = false
}

/**
 * v34 (2, 3) ana sayfada en üstte giriş önerisi (doğrudan Google hesap seçici), girişte doğum tarihi; girişliyken
 * selamlama adla ve halkalı fotoğraf; hesap ayarlarında Google bilgileri salt okunur kutularda, hesabı değiştir yok;
 * Hesabı sil onay metni yazılmadan çalışmaz; silinince ana sayfada yeniden giriş önerisi. Bağlantı yokken öneri yok.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HomeNoWelcomeApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V34HomeAccountTest : V33EditorBase() {
    private inline fun <reified T : Any> koin(): T = GlobalContext.get().get()
    private lateinit var server: FakeUserServer

    @Before fun startServer() {
        TestAccount.reset()
        server = FakeUserServer().start()
    }

    @After fun stopServer() { server.stop(); TestAccount.reset() }

    private fun user() = runBlocking { koin<LocalUserDao>().get() }

    @Test
    fun signInCardProfileAccountSettingsAndDelete() {
        koin<ServerConfig>().update(server.url())
        // Giriş önerisi en üstte: ana sayfanın ilk öğesi, selamlamanın üstünde
        waitFor { exists("home_sign_in_card") && exists("home_greeting") }
        val card = compose.onNodeWithTag("home_sign_in_card").fetchSemanticsNode().boundsInRoot
        val greeting = compose.onNodeWithTag("home_greeting").fetchSemanticsNode().boundsInRoot
        assertTrue("öneri üstte", card.bottom <= greeting.top)
        assertFalse(exists("home_profile"))
        shot("v34-home-sign-in-card")
        // Bağlantı yokken öneri görünmez
        compose.runOnIdle { TestAccount.connectivity.set(false) }
        waitFor { !exists("home_sign_in_card") }
        compose.runOnIdle { TestAccount.connectivity.set(true) }
        waitFor { exists("home_sign_in_card") }
        // Düğme doğrudan girişi başlatır (hesap seçici) → doğum tarihi
        compose.onNodeWithTag("home_sign_in").performClick()
        waitFor { exists("birth_date_dialog") }
        assertEquals(1, TestAccount.auth.signIns)
        compose.onNodeWithTag("birth_day").performTextInput("17")
        compose.onNodeWithTag("birth_month").performTextInput("5")
        compose.onNodeWithTag("birth_year").performTextInput("1990")
        // v36 Kullanım Şartları ve KVKK onayı
        compose.onNodeWithTag("consent_terms").performScrollTo().performClick()
        compose.onNodeWithTag("consent_kvkk").performScrollTo().performClick()
        compose.onNodeWithTag("birth_date_ok").performClick()
        // Girişli: öneri kalkar; selamlama adla, sağ üstte halkalı fotoğraf
        waitFor { !exists("home_sign_in_card") && exists("home_profile") }
        assertTrue(compose.onAllNodes(hasTestTag("home_greeting") and hasText("Ahmet", substring = true)).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasTestTag("home_profile") and hasContentDescription("Ahmet Kar", substring = true)).fetchSemanticsNodes().isNotEmpty())
        shot("v34-home-signed-in")

        // Hesap ayarları: Google bilgileri yazı alanlarıyla aynı boyda; hesabı değiştir yok
        compose.onNodeWithTag("home_profile").performClick()
        waitFor { exists("account_screen") && exists("account_info_email") }
        assertFalse(exists("account_change"))
        val input = compose.onNodeWithTag("account_given").fetchSemanticsNode()
        val info = compose.onNodeWithTag("account_info_email").fetchSemanticsNode()
        // Kutu yazı alanıyla aynı genişlikte (yazı alanının kutusunda 16 dp iç boşluk var)
        val pad = with(compose.density) { 32.dp.toPx() }
        assertEquals(input.boundsInRoot.width + pad, info.boundsInRoot.width, 3f)
        shot("v34-account-settings")
        // v36 Google/hesap kimliği ve sunucu durumu gösterilmez
        assertFalse(exists("account_info_google_id") || exists("account_info_user_id") || exists("account_info_server"))
        compose.onNodeWithTag("account_info_created").performScrollTo()
        shot("v34-account-settings-google")

        // Hesabı sil: onay metni yazılmadan düğme kapalı
        compose.onNodeWithTag("account_delete").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("account_delete_sheet") }
        val confirm = hasTestTag("account_delete_confirm") and isEnabled()
        assertTrue(compose.onAllNodes(confirm).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("account_delete_input").performTextReplacement("hesabımı sil")
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(confirm).fetchSemanticsNodes().isEmpty())
        shot("v34-delete-sheet")
        compose.onNodeWithTag("account_delete_input").performTextReplacement("Hesabımı silmek istiyorum")
        waitFor { compose.onAllNodes(confirm).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("account_delete_confirm").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        // Silindi: sunucu, Firebase ve cihaz
        waitFor(15_000) { exists("account_deleted_panel") }
        assertEquals(1, server.count("DELETE"))
        assertEquals(1, TestAccount.auth.deletes)
        assertNull(user())
        assertTrue(exists("account_screen_sign_in"))
        // Ana sayfada yeniden giriş önerisi
        compose.onNodeWithContentDescription("Geri").performClick()
        waitFor { exists("home_sign_in_card") && !exists("home_profile") }
    }
}

/**
 * v34 (1) Düzenle satırında Vektör düzenle yerine şekil düzenlemenin araçları doğrudan: Nokta düzenle, Çizgi düzenle,
 * Alan boya. Seçili şekilde araç hemen açılır; seçim yoksa araç bekler (seçili görünür) ve dokunulan şekil o araçla
 * açılır. 10 düğmenin hepsi (Katmanlar dahil) 411 dp'de görünür.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V34EditRowTest : V33EditorBase() {
    @Test
    fun directShapeToolsInEditRow() {
        createCanvasAndOpen("Düzenle satırı")
        val size = canvasSize()
        addSquareViaMenu(Offset(size.x * 0.3f, size.y * 0.3f), Offset(size.x * 0.3f, size.x * 0.3f))
        waitFor { shapes().size == 1 }
        val edge = Offset(size.x * 0.3f, size.y * 0.3f + size.x * 0.15f)
        mode("Düzenle")
        waitFor { vm().mode.value == EditMode.EDIT_PICK && hasTopTool("Nokta düzenle") }
        assertTrue(hasTopTool("Çizgi düzenle") && hasTopTool("Alan boya"))
        assertFalse("Vektör düzenle kalktı", hasTopTool("Vektör düzenle"))
        // Bütün düğmeler ekranda (kaydırmadan); v47 Katmanlar mod menüsünde
        val rootWidth = compose.onRoot().fetchSemanticsNode().size.width
        assertFalse(hasTopTool("Katmanlar"))
        listOf("Geri al", "Yinele", "Nokta düzenle", "Çizgi düzenle", "Alan boya", "Döndür", "Ölçekle", "Kırp").forEach { label ->
            val b = compose.onNode(inRow(label, "tool_row_context")).fetchSemanticsNode().boundsInRoot
            assertTrue("$label ekranda değil: $b", b.left >= 0f && b.right <= rootWidth.toFloat())
        }
        shot("v34-edit-row")

        // Seçim yokken Çizgi düzenle bekler (seçili görünür); dokunulan şekil Çizgiler aracıyla açılır
        compose.runOnUiThread { vm().clearSelection() }
        compose.waitForIdle()
        topTool("Çizgi düzenle").performClick()
        waitFor { vm().pendingShapeTool.value == ShapeTool.LINES }
        assertTrue(
            compose.onAllNodes(hasContentDescription("Çizgi düzenle") and hasAnyAncestor(hasTestTag("tool_row_context")) and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
                .fetchSemanticsNodes().isNotEmpty(),
        )
        tap(edge)
        waitFor { vm().mode.value == EditMode.SHAPE_EDIT }
        assertEquals(ShapeTool.LINES, vm().shapeTool.value)
        assertNull(vm().pendingShapeTool.value)
        finish()

        // Seçili şekilde Nokta düzenle hemen açılır
        mode("Seç")
        tap(edge)
        waitFor { vm().selectionKind() == SelectionKind.SHAPE }
        mode("Düzenle")
        waitFor { hasTopTool("Nokta düzenle") }
        if (vm().selectionKind() != SelectionKind.SHAPE) {
            // Düzenle'ye geçerken seçim kalktıysa: şekle dokunmak da aynı aracı açar
            topTool("Nokta düzenle").performClick()
            tap(edge)
        } else topTool("Nokta düzenle").performClick()
        waitFor { vm().mode.value == EditMode.SHAPE_EDIT }
        assertEquals(ShapeTool.NODES, vm().shapeTool.value)
    }
}
