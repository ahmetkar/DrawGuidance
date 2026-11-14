package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v40 düzenleyici: ⋮ → Sayfa ayarları (kaydedilir), basit notta PDF, çizgi romanda CBZ, sayfa şeridinde uzun basıp
 * sürükleyerek sayfa değiştirme, resim kaydetme penceresinde Konum seç ve CMYK, diyagram seçicide zeminli/zeminsiz.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V40EditorUiTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun open(name: String, kind: CanvasKind, pages: Int = 1) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1400, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
        repeat(pages - 1) { compose.runOnIdle { vm().addPage() } }
        waitFor { vm().canvas.value?.pages?.size == pages }
        if (pages > 1) compose.runOnIdle { vm().goToPage(0) }
        waitFor { vm().canvas.value?.currentPage == 0 }
    }

    private fun more() {
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        compose.waitForIdle()
    }

    private fun stored() = runBlocking { repo().observeCanvases().first().single() }

    @Test
    // v50 dikey ekranda pencereler küçük ve kaydırılır: pencere içindeki düğmeler anlam eylemiyle tıklanır
    fun pageSettingsSheetSavesWithTheCanvas() {
        open("Ders", CanvasKind.NOTES, pages = 2)
        more()
        waitFor { exists("page_settings_menu_item") }
        compose.onNodeWithTag("page_settings_menu_item").clickInPopup()
        waitFor { exists("page_settings_sheet") }
        compose.onNodeWithTag("page_numbers_switch").clickInPopup()
        compose.onNodeWithTag("number_format_total").clickInPopup()
        compose.onNode(hasTestTag("header_text") and hasSetTextAction()).performTextInput("Fizik")
        compose.onNodeWithTag("header_align_LEFT").clickInPopup()
        compose.onNodeWithTag("page_settings_done").clickInPopup()
        waitFor { !exists("page_settings_sheet") }
        waitFor { stored().pageSettings.header == "Fizik" }
        val s = stored().pageSettings
        assertTrue(s.pageNumbers)
        assertEquals("1 / 2", s.numberText(0, 2))
        assertEquals(com.karapp.drawguidance.data.model.PageAlign.LEFT, s.headerAlign)
        // Arka plan ayarı değişince sayfa ayarları korunur
        assertEquals(s, vm().canvas.value!!.pageSettings)
    }

    @Test
    fun simpleNotesHasPdfAndComicHasCbz() {
        open("Basit", CanvasKind.SIMPLE_NOTES)
        more()
        waitFor { exists("page_settings_menu_item") }
        // v42 PDF ⋮ menüsünde değil, Kaydet penceresinde
        assertTrue(compose.onAllNodesWithText("PDF olarak kaydet").fetchSemanticsNodes().isEmpty())
        SystemBack.press()
        compose.onNodeWithTag("header_save").performClick()
        waitFor { exists("pdf_options_sheet") }
        assertTrue("CBZ yalnız çizgi romanda", !exists("save_tab_cbz"))
        assertTrue(exists("save_tab_pdf") && exists("save_tab_image"))
    }

    @Test
    fun comicSavesAsCbz() {
        open("Roman", CanvasKind.COMIC, pages = 3)
        // v42 başlıktaki Kaydet → CBZ sekmesi
        compose.onNodeWithTag("header_save").performClick()
        waitFor { exists("save_tab_cbz") }
        compose.onNodeWithTag("save_tab_cbz").clickInPopup()
        waitFor { exists("cbz_options_sheet") }
        assertTrue(exists("save_location"))
        compose.onNodeWithTag("cbz_save").clickInPopup()
        waitForText("CBZ kaydedildi", substring = true)
        // Bildirimde dosyanın yeri (varsayılan belgeler klasörü, .cbz)
        waitForText(".cbz", substring = true)
        assertEquals(com.karapp.drawguidance.ui.screens.canvasedit.state.PdfExportState.Idle, vm().v40.cbz.value)
    }

    @Test
    fun longPressOnPageStripScrubsPagesLive() {
        open("Kaydır", CanvasKind.NOTES, pages = 8)
        val strip = compose.onNodeWithTag("page_strip")
        val h = strip.fetchSemanticsNode().size.height.toFloat()
        val dp = compose.density.density
        val start = Offset(24f * dp, h / 2)
        strip.performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(900)
        compose.waitForIdle()
        waitFor { exists("page_scrubber") }
        // Sürükledikçe sayfa hemen değişir (bırakmadan)
        strip.performTouchInput { repeat(10) { moveBy(Offset(12f * dp, 0f)) } }
        compose.waitForIdle()
        val during = vm().canvas.value!!.currentPage
        assertTrue("sürüklerken sayfa değişti: $during", during >= 2)
        assertTrue(exists("page_scrubber"))
        strip.performTouchInput { up() }
        waitFor { !exists("page_scrubber") }
        assertEquals(during, vm().canvas.value!!.currentPage)
        // Normal dokunuş hâlâ sayfa düğmesine gider
        // (şerit seçili sayfaya kaydırıldığından bir önceki sayfanın düğmesi)
        compose.onNodeWithContentDescription("Sayfa $during").performClick()
        waitFor { vm().canvas.value!!.currentPage == during - 1 }
    }

    @Test
    fun imageSaveDialogOffersLocationAndCmyk() {
        open("Afiş", CanvasKind.ART)
        compose.onNodeWithContentDescription("Resim olarak kaydet").performClick()
        waitFor { exists("image_save_options_dialog") }
        assertTrue(exists("save_location_default"))
        assertTrue(exists("save_location_pick"))
        compose.onNodeWithTag("color_cmyk").clickInPopup()
        waitFor { exists("cmyk_format_TIFF") }
        compose.onNodeWithTag("cmyk_format_TIFF").clickInPopup()
        waitForText(".tif", substring = true)
        // Soft proof önizlemesi ve renk uyarısı
        waitFor { exists("cmyk_gamut") }
    }

    @Test
    fun diagramPickerOffersFilledOrClearBackground() {
        open("Şema", CanvasKind.NOTES)
        addMenu("Diyagram ekle")
        waitForText("Diyagram Türü")
        assertTrue(exists("diagram_bg_filled"))
        compose.onNodeWithTag("diagram_bg_clear").performClick()
        waitFor { vm().diagramTheme.value.endsWith("_clear") }
        compose.onNodeWithTag("diagram_bg_filled").performClick()
        waitFor { !vm().diagramTheme.value.endsWith("_clear") }
    }

}
