package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Layers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v30 düzenleyici testleri için ortak yardımcılar. */
abstract class V30EditorBase : V29EditorBase() {
    protected fun selectTextElement() {
        mode("Seç")
        val q = texts().single().points
        tap(screenOf((q[0].x + q[2].x) / 2f, (q[0].y + q[2].y) / 2f))
        waitForText("1 eleman seçili")
    }

    protected fun lines(): List<com.karapp.drawguidance.data.model.Stroke> = canvas().strokes.filter { it.brushType != BrushType.ERASER }
}

/**
 * v30 yazı rengi: yazı düzenlenirken (eklenince ya da dokunup açınca) fırça panelinde seçilen renk ilk seferde
 * düzenlenen yazıya geçer (önceden yalnız kalem rengi değişiyor, yazı eski renginde kalıyordu).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V30TextColorTest : V30EditorBase() {
    @Test
    fun brushPanelColorChangesEditedTextFirstTime() {
        createCanvasAndOpen("Renk")
        val size = canvasSize()
        // Yazı eklenir, Yazı modunda kalınır; eklenen yazıya yeniden dokunup düzenleme açılır
        addMenu("Yazı ekle")
        waitForText("Yazı eklemek için tuvale dokun", substring = true)
        val at = Offset(size.x * 0.3f, size.y * 0.3f)
        tap(at)
        waitFor { exists("inline_text_editor") }
        field().performTextInput("Renkli")
        finishText()
        waitFor { texts().size == 1 }
        val before = texts().single().colorHex
        selectTextElement()
        editButton("Metni Düzenle")
        waitFor { exists("inline_text_editor") }
        // Üstteki renk/yazı satırı fırça panelini açar; ilk seçilen renk düzenlenen yazıya geçer
        compose.onNodeWithTag("style_line").performClick()
        waitFor { exists("color_sv_area") }
        compose.onNodeWithTag("color_sv_area").performScrollTo().performTouchInput { click(Offset(width * 0.9f, height * 0.15f)) }
        compose.waitForIdle()
        finishText()
        waitFor(6000) { texts().single().colorHex != before }
        println("yazı rengi: $before → ${texts().single().colorHex}")
    }

    @Test
    fun formatPanelColorChangesEditedTextFirstTime() {
        createCanvasAndOpen("Renk2")
        val size = canvasSize()
        addTextAt(Offset(size.x * 0.3f, size.y * 0.3f), "Renkli")
        waitFor { texts().size == 1 }
        val before = texts().single().colorHex
        selectTextElement()
        editButton("Metni Düzenle")
        waitFor { exists("inline_font_bar") }
        compose.onNode(hasContentDescription("Yazı biçimi") and hasAnyAncestor(hasTestTag("inline_font_bar"))).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("inline_format_panel") }
        compose.onNode(hasTestTag("text_tab_COLOR") and hasAnyAncestor(hasTestTag("inline_format_panel"))).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNode(androidx.compose.ui.test.hasText("Dolgu (yazı rengi)") and hasAnyAncestor(hasTestTag("inline_format_panel"))).performScrollTo().performClick()
        waitFor { exists("color_sv_area") }
        compose.onNodeWithTag("color_sv_area").performScrollTo().performTouchInput { click(Offset(width * 0.9f, height * 0.15f)) }
        waitFor(6000) { texts().single().colorHex != before }
    }
}

/**
 * v30 gizleme: seçili elemanlardan istenenler gizlenir (seçim penceresinde işaret kaldırılabilir); gizlenenler
 * çizilmez, seçilemez, kayıtta korunur ve Elemanlar panelindeki göz düğmesiyle ya da "Tümünü göster" ile geri gelir.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V30HideTest : V30EditorBase() {
    @Test
    fun hideChosenSelectedElementsAndShowAgain() {
        createCanvasAndOpen("Gizle")
        val size = canvasSize()
        val starts = listOf(0.3f, 0.45f, 0.6f).map { Offset(size.x * 0.2f, size.y * it) }
        starts.forEach { drag(it, Offset(size.x * 0.5f, 0f)) }
        waitFor { lines().size == 3 }
        mode("Seç")
        starts.forEach { tap(it + Offset(size.x * 0.25f, 0f)) }
        waitForText("3 eleman seçili")
        // Üst menüde Gizle: birden çok eleman seçili → hangileri gizlenecek sorulur (hepsi işaretli)
        compose.onNode(hasContentDescription("Gizle") and hasAnyAncestor(hasTestTag("tool_row_context"))).performScrollTo().performClick()
        waitFor { exists("hide_sheet_apply") }
        val keep = lines()[1].id
        assertEquals(3, lines().count { exists("hide_pick_${it.id}") })
        compose.onNodeWithTag("hide_pick_$keep").performClick()
        waitForText("Gizle (2)")
        compose.onNodeWithTag("hide_sheet_apply").performClick()
        waitFor { canvas().strokes.count { it.hidden } == 2 }
        val c = canvas()
        assertEquals(listOf(keep), Layers.visible(c.strokes, c.layers).map { it.id })
        // Gizlenenler seçilemez: üstüne dokunmak seçime eklemez
        tap(starts[0] + Offset(size.x * 0.25f, 0f))
        tap(starts[1] + Offset(size.x * 0.25f, 0f))
        waitForText("1 eleman seçili")
        // Kayıtta korunur (canvas() veritabanından okur: içerik kodlayıcıdan geçmiştir)
        assertEquals(2, canvas().strokes.count { it.hidden })
        // Elemanlar panelinde göz düğmesiyle biri gösterilir, "Tümünü göster" ile diğeri
        clickElements()
        waitFor { exists("panel_show_all") }
        val first = canvas().strokes.first { it.hidden }.id
        compose.onNodeWithTag("eye_$first").performScrollTo().performClick()
        waitFor { canvas().strokes.count { it.hidden } == 1 }
        compose.onNodeWithTag("panel_show_all").performClick()
        waitFor { canvas().strokes.none { it.hidden } }
    }

    @Test
    fun singleSelectionHidesDirectlyAndUndoRestores() {
        createCanvasAndOpen("Gizle2")
        val (start, _) = drawAndSelect()
        compose.onNode(hasContentDescription("Gizle") and hasAnyAncestor(hasTestTag("tool_row_context"))).performScrollTo().performClick()
        waitFor { canvas().strokes.single().hidden }
        waitForText("1 eleman gizlendi", substring = true)
        // Gizli eleman dokunulunca seçilmez
        tap(start + Offset(canvasSize().x * 0.25f, 0f))
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("1 eleman seçili")).fetchSemanticsNodes().isEmpty())
        // Geri al: gizleme tek adımdır
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { !canvas().strokes.single().hidden }
    }
}

/**
 * v30 tuvalin yapay zekâ ayarı: yeni tuval formunda öntanımlı açık; düzenleyicinin ⋮ menüsünden kapatılınca o tuvalde
 * çizim önerisi gösterilmez, ayar tuvalle saklanır; yeniden açılınca öneriler döner.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V30AiTest : V30EditorBase() {
    @Test
    fun canvasAiSettingGatesSuggestions() {
        createCanvasAndOpen("Yapay")
        assertTrue("yeni tuvalde yapay zekâ açık", canvas().aiEnabled)
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        waitFor { exists("ai_menu_item") }
        compose.onNodeWithTag("ai_menu_item").performClick()
        waitFor { !canvas().aiEnabled }
        SystemBack.press()
        waitFor { !exists("ai_menu_item") }
        val size = canvasSize()
        val r = size.x * 0.18f
        wobblyCircle(Offset(size.x * 0.3f, size.y * 0.3f), r, 3)
        waitFor { canvas().strokes.size == 1 }
        advanceMain(900)
        repeat(10) { advanceMain(200); assertTrue("yapay zekâ kapalı: öneri yok", !chipShown()) }
        // Yeniden açılır
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        waitFor { exists("ai_menu_item") }
        compose.onNodeWithTag("ai_menu_item").performClick()
        waitFor { canvas().aiEnabled }
        SystemBack.press()
        waitFor { !exists("ai_menu_item") }
        wobblyCircle(Offset(size.x * 0.65f, size.y * 0.6f), r, 5)
        waitFor { canvas().strokes.size == 2 }
        advanceMain(900)
        waitFor(15_000) { chipShown() }
    }
}

/**
 * v30 resim araçları (tüm tuval türlerinde, Resmi düzenle üst menüsünde ve resmin bağlam menüsünde): arka planı
 * kaldır (önizlemeli; beyaz zemin saydam olur, çizim kalır) ve efektler (görünüm + ayarlar). Her biri yeni resim
 * verisi olarak saklanır; geri al özgün resme döner.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V30ImageToolsTest : V30EditorBase() {
    private fun image() = canvas().strokes.single { it.brushType == BrushType.IMAGE }

    private fun decoded(assetId: String): android.graphics.Bitmap = kotlinx.coroutines.runBlocking {
        val bytes = org.koin.core.context.GlobalContext.get().get<com.karapp.drawguidance.data.images.CanvasAssetStore>().bytes(assetId)!!
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun addImageAndEdit(): Offset {
        val size = canvasSize()
        val file = java.io.File(compose.activity.cacheDir, "cizim30.png").apply {
            writeBytes(ImageImportValidatorTest.png(ImageImportValidatorTest.simpleDrawing(600, 450)))
        }
        val at = Offset(size.x * 0.5f, size.y * 0.4f)
        // v47 boş yere dokunmanın ekleme menüsü kaldırıldı: Ekle satırından
        addMenu("Resim ekle")
        if (exists("image_source_sheet")) compose.onNodeWithTag("image_source_device").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        val shadow = org.robolectric.Shadows.shadowOf(compose.activity)
        val started = requireNotNull(shadow.nextStartedActivityForResult) { "resim seçici açılmadı" }
        shadow.receiveResult(started.intent, android.app.Activity.RESULT_OK, android.content.Intent().setData(android.net.Uri.fromFile(file)))
        waitFor { canvas().strokes.any { it.brushType == BrushType.IMAGE } }
        finish()
        mode("Düzenle")
        tap(at)
        modeBarTitle("Resmi düzenle")
        return at
    }

    @Test
    fun removeBackgroundThenEffectsOnArtCanvas() {
        createCanvasAndOpen("Resim araçları")
        addImageAndEdit()
        assertTrue(hasTopTool("Arka planı kaldır") && hasTopTool("Efektler"))
        val original = image().assetId!!
        topTool("Arka planı kaldır").performClick()
        waitFor { exists("bg_remove_sheet") }
        // Önizleme hesaplanınca kaldırılacak pay görünür (beyaz zemin: büyük kısmı)
        waitFor(20_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("bg_remove_ratio") and androidx.compose.ui.test.hasText("%0", substring = true).not())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("bg_remove_apply").performScrollTo().performClick()
        waitFor(20_000) { image().assetId != original }
        val cut = decoded(image().assetId!!)
        println("arka plan kaldırıldı: ${cut.width}×${cut.height}")
        assertEquals("köşe saydam", 0, cut.getPixel(3, 3) ushr 24)
        // Çemberin üstü (mavi çizgi) korunur
        val ring = (0 until cut.height).map { y -> cut.getPixel(cut.width / 2, y) }.count { it ushr 24 == 255 }
        assertTrue("çizim korunur: $ring", ring > 0)
        waitForText("Arka plan kaldırıldı", substring = true)

        // Efektler: siyah-beyaz görünüm
        val afterBg = image().assetId!!
        topTool("Efektler").performClick()
        waitFor { exists("image_effects_sheet") }
        waitFor(20_000) { exists("look_GRAYSCALE") }
        compose.onNodeWithTag("look_GRAYSCALE").performClick()
        compose.onNodeWithTag("image_effects_apply").performScrollTo().performClick()
        waitFor(20_000) { image().assetId != afterBg }
        val gray = decoded(image().assetId!!)
        val opaque = (0 until gray.height).map { y -> gray.getPixel(gray.width / 2, y) }.filter { it ushr 24 == 255 }
        assertTrue(opaque.isNotEmpty() && opaque.all { kotlin.math.abs((it shr 16 and 0xFF) - (it and 0xFF)) <= 3 })
        assertEquals("saydamlık korunur", 0, gray.getPixel(3, 3) ushr 24)
        // Geri al: efekt öncesi, bir daha: özgün resim (v45 satır Efektler'e kaymış olabilir: önce Geri al'a kaydırılır)
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { image().assetId == afterBg }
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { image().assetId == original }
    }

    @Test
    fun imageToolsExistOnNotesCanvas() {
        createNotesCanvasAndOpen("Not resim")
        addImageAndEdit()
        assertTrue(hasTopTool("Arka planı kaldır") && hasTopTool("Efektler"))
        val original = image().assetId!!
        topTool("Efektler").performClick()
        waitFor { exists("image_effects_sheet") }
        compose.onNodeWithTag("adj_brightness").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.6f) }
        compose.onNodeWithTag("image_effects_apply").performScrollTo().performClick()
        waitFor(20_000) { image().assetId != original }
    }
}

/** v30 Ana sayfayla ve karşılama ekranıyla açılan uygulama (gerçek uygulamadaki gibi). */
class HomeTestApp : TestDrawGuidanceApp() {
    override val startOnHome: Boolean = true
    override val showWelcome: Boolean = true
}

/**
 * v30 açılış: karşılama ekranında uygulamanın adı ve logosu, sonra Ana sayfa (karşılama yazısı, kısayollar); alt
 * menüde Ana sayfa en solda. Tuval oluşturulup bırakılınca Ana sayfada "Kaldığın yerden devam et" kartında ve son
 * çizimlerde görünür; karttan yeniden açılır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HomeTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V30HomeTest : V30EditorBase() {
    @Test
    fun welcomeThenHomeAndResumeLeftCanvas() {
        // Karşılama: ad ve logo
        waitFor { exists("welcome_screen") }
        // v31 başlık (harf harf yazılır; anlam ağacında baştan tam metin); v36 Türkçede "Çizim Rehberi"
        assertTrue(compose.onAllNodes(hasTestTag("welcome_screen") and androidx.compose.ui.test.hasText("Çizim Rehberi", substring = true)).fetchSemanticsNodes().isNotEmpty())
        // Canlandırma (~1,6 sn) ana iş parçacığının saatiyle ilerletilir
        repeat(30) { if (exists("welcome_screen")) { compose.mainClock.advanceTimeBy(150); advanceMain(150) } }
        waitFor { !exists("welcome_screen") }
        // Ana sayfa: karşılama yazısı ve ilk kullanım kartı
        waitFor { exists("home_greeting") && exists("home_start") }
        // Alt menüde Ana sayfa en solda
        val home = compose.onNodeWithTag("tab_home").fetchSemanticsNode().boundsInRoot
        val canvases = compose.onNode(hasContentDescription("Tuvallerim").or(androidx.compose.ui.test.hasText("Tuvallerim")) and hasAnyAncestor(hasTestTag("main_bottom_bar")))
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Ana sayfa en solda", home.left < canvases.left)

        // Kısayoldan not tuvali (v33: Gelişmiş çizim → Not alma)
        compose.onNodeWithTag("home_new_advanced").performScrollTo().performClick()
        waitFor { exists("new_kind_NOTES") }
        compose.onNodeWithTag("new_kind_NOTES").performScrollTo().performClick()
        waitForText("Yeni Tuval Oluştur")
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("Ders notu")
        compose.onNodeWithText("Oluştur").clickInPopup()
        waitFor { exists("drawing_canvas") }
        assertEquals(com.karapp.drawguidance.data.model.CanvasKind.NOTES, allCanvases().single().kind)
        val size = canvasSize()
        drag(Offset(size.x * 0.2f, size.y * 0.3f), Offset(size.x * 0.4f, 0f))
        waitFor { canvas().strokes.isNotEmpty() }
        compose.onNodeWithContentDescription("Geri").performClick()

        // Ana sayfa: kaldığın yerden devam et ve son çizimler
        waitFor { exists("home_resume") }
        assertTrue(compose.onAllNodes(hasTestTag("home_resume") and androidx.compose.ui.test.hasText("Ders notu", substring = true)).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasTestTag("home_resume") and androidx.compose.ui.test.hasText("az önce bıraktın", substring = true)).fetchSemanticsNodes().isNotEmpty())
        // v40 son çizimler yerine en çok vakit geçirdiklerin (düzenleyicide geçen süre kaydedilir)
        waitFor { exists("home_most_time_row") }
        compose.onNodeWithTag("home_resume").performClick()
        waitFor { exists("drawing_canvas") }
        assertEquals(1, canvas().strokes.size)
        compose.onNodeWithContentDescription("Geri").performClick()
        waitFor { exists("home_screen") }
        // Tümü → Tuvallerim; geri → Ana sayfa
        compose.onNodeWithTag("home_see_all").performScrollTo().performClick()
        waitFor { !exists("home_screen") }
        SystemBack.press()
        waitFor { exists("home_screen") }
    }
}

/** v30 görsel kontrol (DG_SHOTS verilince): Ana sayfa ve karşılama ekranı resimleri. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HomeTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V30HomeShotsTest : V30EditorBase() {
    private fun homeShot(name: String) = shot(name.removeSuffix(".png"))

    @Test
    fun homeShots() {
        org.junit.Assume.assumeTrue(System.getenv("DG_SHOTS") != null)
        waitFor { exists("welcome_screen") }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1300)
        homeShot("v30-welcome.png")
        compose.mainClock.autoAdvance = true
        repeat(30) { if (exists("welcome_screen")) { compose.mainClock.advanceTimeBy(150); advanceMain(150) } }
        waitFor { exists("home_screen") }
        homeShot("v30-home-empty.png")
        // Örnek tuvaller: çizimli üç tuval, ikisi bırakılmış
        val repo = org.koin.core.context.GlobalContext.get().get<com.karapp.drawguidance.data.repository.CanvasRepository>()
        val recent = org.koin.core.context.GlobalContext.get().get<com.karapp.drawguidance.data.settings.RecentCanvasStore>()
        kotlinx.coroutines.runBlocking {
            val kinds = listOf("Gün batımı" to com.karapp.drawguidance.data.model.CanvasKind.ART, "Fizik notları" to com.karapp.drawguidance.data.model.CanvasKind.NOTES, "Kahraman" to com.karapp.drawguidance.data.model.CanvasKind.COMIC)
            kinds.forEachIndexed { i, (name, kind) ->
                val c = repo.createCanvas(name, 800, 800, "#FFFFFF", true, kind)
                val strokes = List(6) { k ->
                    val cx = 200f + 80f * k; val cy = 300f + 60f * ((k + i) % 3)
                    com.karapp.drawguidance.data.model.Stroke(
                        "s$i$k", List(40) { t -> val a = t / 39f * 6.28f; com.karapp.drawguidance.data.model.StrokePoint(cx + 90f * kotlin.math.cos(a), cy + 70f * kotlin.math.sin(a)) },
                        listOf("#E53935", "#1E88E5", "#43A047", "#FB8C00", "#8E24AA", "#00897B")[k], BrushType.MARKER, 14f, 1f, k,
                    )
                }
                repo.saveStrokes(c.localId, listOf(strokes))
                if (i < 2) { recent.opened(c.localId, System.currentTimeMillis() - 3_600_000L * (i + 1)); recent.closed(c.localId, System.currentTimeMillis() - 1_200_000L * (i + 1)) }
            }
        }
        waitFor { exists("home_resume") }
        homeShot("v30-home.png")
    }
}
