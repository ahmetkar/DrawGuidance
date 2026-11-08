package com.karapp.drawguidance

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v19 görsel kontrolü (DG_SHOTS verilince): Katmanlar üst satırda, PDF olarak kaydet penceresi, PDF sayfası. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V19ShotsTest : V15ShotsBase() {

    private fun chip(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("pdf_options_sheet")))

    @Test
    fun v19() {
        BitmapPdfSink.install()
        try {
            waitForText("Henüz bir tuvaliniz yok", substring = true)
            val repository = GlobalContext.get().get<CanvasRepository>()
            runBlocking {
                val c = repository.createCanvas("Fizik Notları", 1240, 1754, "#FFFDF5", false, kind = CanvasKind.NOTES)
                val page = listOf(
                    formula("f", "F = m·a", 64f, 180f, 260f, 0),
                    ink("i1", BrushType.INK_PEN, "#1E1E1E", 520f, 160f, 1080f, 6f, 1),
                    ink("i2", BrushType.MARKER, "#FDD835", 700f, 160f, 900f, 26f, 2),
                    shape("s", "HEXAGON", 200f, 900f, 700f, 1300f, 3),
                )
                repository.saveStrokes(c.localId, listOf(page, page.map { it.copy(id = it.id + "2") }, listOf(ink("i3", BrushType.INK_PEN, "#1E1E1E", 400f, 160f, 1080f, 6f, 0))))
            }
            waitForText("Fizik Notları")
            compose.onNodeWithText("Fizik Notları").performSemanticsAction(SemanticsActions.OnClick)
            waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }
            settle()
            shot("v19-1-draw-row")
            mode("Ekle")
            settle()
            shot("v19-2-add-row")
            mode("Çiz")

            // v42 başlıktaki Kaydet → birleşik pencere (PDF sekmesi)
            compose.onNodeWithTag("header_save").performClick()
            waitFor { compose.onAllNodesWithTag("pdf_options_sheet").fetchSemanticsNodes().isNotEmpty() }
            chip("Aralık").performSemanticsAction(SemanticsActions.OnClick)
            chip("A4").performSemanticsAction(SemanticsActions.OnClick)
            chip("Normal").performSemanticsAction(SemanticsActions.OnClick)
            settle()
            shot("v19-3-pdf-sheet-top")
            compose.onNodeWithTag("pdf_page_numbers").performScrollTo().performClick()
            compose.onNodeWithTag("pdf_save").performScrollTo()
            settle()
            shot("v19-4-pdf-sheet-bottom")
            compose.onNodeWithTag("pdf_save").performClick()
            waitForText("PDF kaydedildi", substring = true)
            settle()
            shot("v19-5-pdf-saved")
            // PDF'in ilk sayfası (A4, normal kenar boşluğu, sayfa numarası)
            BitmapPdfSink.last?.pages?.firstOrNull()?.let { page ->
                System.getenv("DG_SHOTS")?.let { dir ->
                    java.io.File(dir, "v19-6-pdf-page.png").outputStream().use { page.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        } finally {
            BitmapPdfSink.uninstall()
        }
    }
}
