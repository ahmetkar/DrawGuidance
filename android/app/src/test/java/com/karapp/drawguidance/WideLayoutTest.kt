package com.karapp.drawguidance

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Yatay telefon ve tablet düzeni: ana bölümlerde sol dikey menü, çizim ekranında solda dikey mod seçici,
 * başlıkta araçlar ve her zaman açık komut kutusu. Görsel kontrol için ekran görüntüleri kaydedilir.
 */
abstract class WideLayoutTestBase : EditorTestBase() {

    protected fun shot(name: String) {
        // Ekran görüntüleri yalnızca DG_SHOTS ortam değişkeni verildiğinde kaydedilir
        val dir = System.getenv("DG_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        compose.waitForIdle()
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread {
            // Açılır pencereler (alt sayfa, fırça seçici) ayrı pencerelerdir: hepsi sırayla çizilir
            val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
            val field = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val views = (field.get(global) as List<android.view.View>).toList()
            val canvas = Canvas(bitmap)
            views.filter { it.width > 0 && it.isShown }.forEach { view ->
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                canvas.save()
                canvas.translate(location[0].toFloat(), location[1].toFloat())
                view.draw(canvas)
                canvas.restore()
            }
        }
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    protected fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    protected fun wideFlow(prefix: String, landscape: Boolean) {
        // Ana bölüm: alt menü yok, sol dikey menü var
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithTag("main_nav_rail").assertExists()
        assertTrue(compose.onAllNodesWithTag("main_bottom_bar").fetchSemanticsNodes().isEmpty())
        val rail = bounds("main_nav_rail")
        assertTrue("dikey menü solda ve uzun olmalı: $rail", rail.left < 60f && rail.height > rail.width * 2)
        shot("$prefix-1-list-empty")

        compose.onNodeWithContentDescription("Yeni Tuval").performClick()
        waitForText("Yeni Tuval Oluştur")
        shot("$prefix-2-new-canvas-sheet")
        compose.onNode(hasSetTextAction()).performTextInput("Yatay Tuval")
        compose.onNodeWithText("Oluştur").clickInPopup()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }

        // Çizim ekranı: mod seçici tuvalin solunda dikey; v18: komut kutusu yok, araçlar tuvalin üstünde
        val modes = bounds("mode_selector")
        val canvasRect = bounds("drawing_canvas")
        val tools = bounds("tool_row_draw")
        assertTrue("mod seçici dikey olmalı: $modes", modes.height > modes.width * 2)
        assertTrue("mod seçici tuvalin solunda olmalı", modes.right <= canvasRect.left + 1f)
        assertTrue("araçlar tuvalin üstünde olmalı", tools.bottom <= canvasRect.top + 1f)
        assertTrue(compose.onAllNodesWithTag("command_input").fetchSemanticsNodes().isEmpty())
        if (landscape) assertTrue("tuval yatayda geniş olmalı: $canvasRect", canvasRect.width > canvasRect.height)

        // Çizim çalışır
        val size = canvasSize()
        drag(Offset(size.x * 0.2f, size.y * 0.5f), Offset(size.x * 0.5f, 0f))
        waitFor { canvas().strokes.size == 1 }
        shot("$prefix-3-editor")

        // v47 dikey tablette üst satır kayar: düğme görünür alanda olmayabilir
        runCatching { compose.onNodeWithContentDescription("Fırça Seç").performScrollTo() }
        compose.onNodeWithContentDescription("Fırça Seç").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Sanat Kalemleri")
        shot("$prefix-4-brush-picker")
        compose.onNodeWithText("Yarım Ton Nokta").clickInPopup()
        waitForDescription("Seçili fırça: Yarım Ton Nokta")

        // v42 Çiz satırında Cetvel ve Şekil kalıbı da var: yatay telefonda satır kayar
        openProperties()
        waitForText("Çizim Gücü")
        shot("$prefix-5-brush-panel")
        compose.onNodeWithContentDescription("Paneli kapat").performClick()

        mode("Seç")
        waitForText("Elemana dokunarak seç", substring = true)
        shot("$prefix-6-select-mode")

        // Listeye dön, diğer bölümler dikey menüden açılır
        compose.onNodeWithContentDescription("Geri").performClick()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isEmpty() }
        waitForText("Yatay Tuval")
        shot("$prefix-7-list")
        compose.onNodeWithText("Ayarlar").performClick()
        // v36 sunucu ayarları Bağlantı sekmesinde
        waitFor { compose.onAllNodesWithTag("settings_tab_connection").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings_tab_connection").performSemanticsAction(SemanticsActions.OnClick)
        // v40 v39 ayarlar sadeleştirmede sunucu adresi kartı kaldırıldı: Bağlantı sekmesinde tek eşitleme kartı var
        waitFor { compose.onAllNodesWithTag("sync_control_card").fetchSemanticsNodes().isNotEmpty() }
        // v40 v39 abonelik satırı ve sekmeler dar yatay ekranda da ekranın içinde kalır, eşitleme kartı görünür alanda başlar
        val screenH = compose.activity.window.decorView.height.toFloat()
        // v43 giriş yapılmamışken abonelik satırı yok
        assertTrue(compose.onAllNodesWithTag("settings_subscription").fetchSemanticsNodes().isEmpty())
        val sync = bounds("sync_control_card")
        assertTrue("eşitleme kartı görünür alanda başlamalı: $sync", sync.top < screenH)
        compose.onNodeWithTag("main_nav_rail").assertExists()
        shot("$prefix-8-settings")
        compose.onNodeWithText("Kaydedilenler").performClick()
        compose.waitForIdle()
        shot("$prefix-9-images")
        assertEquals(1, canvas().strokes.size)
    }
}

// Koin her test sınıfında temiz JVM istediği için (forkEvery = 1) her ekran boyutu ayrı sınıfta.

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w891dp-h411dp-land-xhdpi")
class LandscapePhoneLayoutTest : WideLayoutTestBase() {
    @Test fun landscapePhone() = wideFlow("land", landscape = true)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w1280dp-h800dp-xlarge-land-hdpi")
class TabletLandscapeLayoutTest : WideLayoutTestBase() {
    @Test fun tabletLandscape() = wideFlow("tablet", landscape = true)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w800dp-h1280dp-xlarge-port-hdpi")
class TabletPortraitLayoutTest : WideLayoutTestBase() {
    @Test fun tabletPortrait() = wideFlow("tabletport", landscape = false)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PortraitPhoneLayoutTest : WideLayoutTestBase() {
    @Test
    fun portraitPhoneKeepsBottomBarAndHorizontalModes() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithTag("main_bottom_bar").assertExists()
        assertTrue(compose.onAllNodesWithTag("main_nav_rail").fetchSemanticsNodes().isEmpty())
        createCanvasAndOpen("Dikey")
        val modes = bounds("mode_selector")
        assertTrue("dikey telefonda mod seçici yatay olmalı: $modes", modes.width > modes.height * 3)
        assertTrue(bounds("tool_row_draw").bottom <= bounds("drawing_canvas").top + 1f)
        assertTrue(compose.onAllNodesWithTag("command_input").fetchSemanticsNodes().isEmpty())
        shot("port-editor")
    }
}
