package com.karapp.drawguidance

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel.SaveTab
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v42 birleşik Kaydet penceresi: çizgi roman ve not almada başlıktaki tek Kaydet düğmesi resim, PDF ve (çizgi romanda)
 * CBZ sekmeli pencereyi açar; varsayılan CMYK PDF. ⋮ menüsünde PDF ve CBZ olarak kaydet yok. Resim modlarında Kaydet
 * eskisi gibi resim kaydetme penceresini açar.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V42SaveTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun open(name: String, kind: CanvasKind) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1400, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun moreHasNoSaveItems() {
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        compose.waitForIdle()
        waitFor { exists("page_settings_menu_item") }
        assertTrue(compose.onAllNodesWithText("PDF olarak kaydet").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("CBZ olarak kaydet").fetchSemanticsNodes().isEmpty())
        assertTrue(!exists("cbz_menu_item"))
        SystemBack.press()
        compose.waitForIdle()
    }

    @Test
    fun comicSavesFromOneWindowWithCmykPdfByDefault() {
        open("Roman", CanvasKind.COMIC)
        moreHasNoSaveItems()
        compose.onNodeWithTag("header_save").performClick()
        waitFor { exists("pdf_options_sheet") }
        assertEquals(SaveTab.PDF, vm().saveTab.value)
        assertNotNull("çizgi roman PDF'i varsayılan CMYK", vm().pdfOptions.value?.cmyk)
        assertTrue(exists("save_tab_pdf") && exists("save_tab_cbz") && exists("save_tab_image"))
        assertTrue(exists("cmyk_section"))
        // CBZ sekmesi
        compose.onNodeWithTag("save_tab_cbz").performClick()
        waitFor { exists("cbz_options_sheet") }
        assertTrue(!exists("pdf_options_sheet"))
        // Resim sekmesi: çizgi romanda resim de varsayılan CMYK
        compose.onNodeWithTag("save_tab_image").performClick()
        waitFor { exists("image_save_options_dialog") }
        assertNotNull(vm().saveOptions.value?.cmyk)
        // RGB seçilebilir (öteki birleşimler)
        compose.onNodeWithTag("color_rgb").performScrollTo().performClick()
        compose.waitForIdle()
        // Geri tuşu pencereyi kapatır; her şey temizlenir
        SystemBack.press()
        waitFor { vm().saveTab.value == null }
        assertNull(vm().pdfOptions.value)
        assertNull(vm().saveOptions.value)
        assertNull(vm().v40.cbzOptions.value)
    }

    @Test
    fun notesDefaultToCmykPdfAndHaveNoCbz() {
        open("Ders", CanvasKind.NOTES)
        moreHasNoSaveItems()
        compose.onNodeWithTag("header_save").performClick()
        waitFor { exists("pdf_options_sheet") }
        assertEquals(SaveTab.PDF, vm().saveTab.value)
        assertNotNull(vm().pdfOptions.value?.cmyk)
        assertTrue(!exists("save_tab_cbz"))
        // Not almada resim sekmesi RGB kalır
        compose.onNodeWithTag("save_tab_image").performClick()
        waitFor { exists("image_save_options_dialog") }
        assertNull(vm().saveOptions.value?.cmyk)
        // PDF sekmesine dönüp kaydet: pencere kapanır
        compose.onNodeWithTag("save_tab_pdf").performClick()
        waitFor { exists("pdf_save") }
        compose.onNodeWithTag("pdf_save").performScrollTo().performClick()
        waitFor { vm().saveTab.value == null && !exists("pdf_options_sheet") }
    }

    @Test
    fun simpleNotesAlsoUseTheUnifiedWindow() {
        open("Basit not", CanvasKind.SIMPLE_NOTES)
        compose.onNodeWithTag("header_save").performClick()
        waitFor { exists("pdf_options_sheet") }
        assertTrue(exists("unified_save_header"))
        assertNotNull(vm().pdfOptions.value?.cmyk)
    }

    @Test
    fun artKeepsTheImageSaveWindow() {
        open("Afiş", CanvasKind.ART)
        compose.onNodeWithContentDescription("Resim olarak kaydet").performClick()
        waitFor { exists("image_save_options_dialog") }
        assertNull(vm().saveTab.value)
        assertTrue(!exists("unified_save_header"))
        assertNull(vm().saveOptions.value?.cmyk)
    }
}
