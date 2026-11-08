package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.data.repository.CanvasTimeRepository
import com.karapp.drawguidance.ui.theme.ThemeMode
import com.karapp.drawguidance.ui.theme.ThemePrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v40 tasarım arayüzü: 3B Şekillerim'de "GLB içe aktar" yazısı, Tuvallerim özeti, düzenleyicide geçirilen sürenin
 * kaydı, özel boyutun inç/DPI ile oluşturulması ve Ayarlar'da tema modu / yüksek karşıtlık.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V40DesignUiTest : V33EditorBase() {
    @After fun resetTheme() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ThemePrefs.selectMode(ctx, ThemeMode.SYSTEM)
        ThemePrefs.setHighContrast(ctx, false)
    }

    private fun cd(tag: String): String? = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")

    @Test
    fun glbImportButtonIsLabelled() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithText("3B Şekiller").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("models3d_screen") }
        assertTrue(exists("models3d_import_glb"))
        assertTrue(exists("button_label:GLB içe aktar"))
    }

    @Test
    fun canvasesShowStatsAndEditorRecordsTime() {
        createCanvasAndOpen("Süre")
        val id = canvas().localId
        val size = canvasSize()
        drag(Offset(size.x * 0.2f, size.y * 0.3f), Offset(size.x * 0.4f, 0f))
        waitFor { canvas().strokes.isNotEmpty() }
        // Robolectric saati: düzenleyicide 30 sn geçmiş gibi (girdiden sonra, 60 sn sınırının içinde)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(30))
        compose.onNodeWithContentDescription("Geri").performClick()
        val times = GlobalContext.get().get<CanvasTimeRepository>()
        waitFor { runBlocking { (times.observe().first()[id] ?: 0L) > 0L } }
        // Tuvallerim'in üstünde özet: toplam, türler, bu hafta ve çizim süresi
        runBlocking { GlobalContext.get().get<CanvasRepository>().createCanvas("Notum", 800, 800, "#FFFFFF", false, CanvasKind.NOTES) }
        waitFor { exists("canvas_stats") && cd("stat_total") == "Toplam: 2" }
        // v42 resim, not ve çizgi roman sayıları (sıfır da yazılır)
        assertEquals("Resim: 1", cd("stat_drawings"))
        assertEquals("Not: 1", cd("stat_notes"))
        assertEquals("Çizgi roman: 0", cd("stat_comics"))
        assertEquals("Bu hafta: 2", cd("stat_week"))
        assertTrue(exists("stat_time"))
    }

    @Test
    fun customSizeInInchesWithDpi() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithContentDescription("Yeni Tuval").performClick()
        waitForText("Yeni Tuval Oluştur")
        // Resim türünün hazır boyutları
        assertTrue(exists("size_preset_square") && exists("size_preset_wallpaper") && exists("size_preset_a4_300"))
        compose.onNodeWithTag("size_preset_custom").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("size_units") }
        compose.onNodeWithTag("size_unit_in").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("size_dpi") }
        compose.onNodeWithTag("size_width").performTextReplacement("8.5")
        compose.onNodeWithTag("size_height").performTextReplacement("11")
        compose.onNodeWithTag("size_dpi").performTextReplacement("200")
        waitFor { compose.onAllNodes(hasTestTag("size_result") and hasText("1700 × 2200 px")).fetchSemanticsNodes().isNotEmpty() }
        // Sınır dışı: uyarı ve Oluştur kapalı; düzeltilince açılır
        compose.onNodeWithTag("size_dpi").performTextReplacement("1200")
        waitFor { exists("size_error") }
        compose.onNodeWithTag("canvas_form_submit").assertIsNotEnabled()
        compose.onNodeWithTag("size_dpi").performTextReplacement("200")
        waitFor { !exists("size_error") }
        compose.onNodeWithTag("canvas_form_submit").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("drawing_canvas") }
        assertEquals(1700 to 2200, canvas().width to canvas().height)
    }

    @Test
    fun settingsThemeModeAndHighContrast() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNode(hasContentDescription("Ayarlar").or(hasText("Ayarlar")) and hasAnyAncestor(hasTestTag("main_bottom_bar"))).performClick()
        waitFor { exists("theme_palette_card") }
        listOf("light", "dark", "system").forEach { assertTrue(it, exists("theme_mode_$it")) }
        compose.onNodeWithTag("theme_mode_dark").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { ThemePrefs.mode.value == ThemeMode.DARK }
        compose.onNodeWithTag("theme_high_contrast").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { ThemePrefs.highContrast.value }
        val prefs = compose.activity.getSharedPreferences("dg_theme", android.content.Context.MODE_PRIVATE)
        assertEquals("DARK", prefs.getString("mode", null))
        assertTrue(prefs.getBoolean("high_contrast", false))
        settleFor(200)
        shot("v40-settings-dark-high-contrast")
    }
}
