package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

abstract class V19EditorBase : V18EditorBase() {
    protected fun layersIn(tag: String) = compose.onAllNodes(hasContentDescription("Katmanlar") and hasAnyAncestor(hasTestTag(tag)))
        .fetchSemanticsNodes().size

    protected fun closeMenu() {
        SystemBack.press()
        waitFor { !contextMenuOpen() }
    }
}

/**
 * v19: resim tuvalinin bağlam menüsünde (seçim yokken) Formül ekle yok; Katmanlar başlıkta değil her modun üst
 * satırında (sığarsa satıra yayılmış, en sonda).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V19PictureCanvasTest : V19EditorBase() {
    @Test
    fun noFormulaInContextMenuAndLayersInTopRow() {
        createCanvasAndOpen("Resim")
        val size = canvasSize()
        // v47 boş yerde iki parmak menü açmaz; Ekle satırında resim tuvali için formül yok
        twoFingerHold(Offset(size.x * 0.5f, size.y * 0.5f))
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithTag("canvas_add_menu").fetchSemanticsNodes().isEmpty())
        mode("Ekle")
        waitFor { compose.onAllNodes(hasContentDescription("Yazı ekle") and hasAnyAncestor(hasTestTag("tool_row_context"))).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("resim tuvalinde formül yok", compose.onAllNodesWithContentDescription("Formül ekle").fetchSemanticsNodes().isEmpty())
        mode("Çiz")

        // v47 Katmanlar üst satırlarda değil, mod menüsünün son grubunda (her modda aynı yerde) ve tek
        assertEquals(1, compose.onAllNodesWithContentDescription("Katmanlar").fetchSemanticsNodes().size)
        assertEquals(0, layersIn("tool_row_draw"))
        assertEquals(1, layersIn("mode_selector"))
        compose.onNode(hasContentDescription("Katmanlar") and hasAnyAncestor(hasTestTag("mode_selector"))).performScrollTo().performClick()
        waitFor { compose.onAllNodesWithTag("layers_panel").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        waitFor { compose.onAllNodesWithTag("layers_panel").fetchSemanticsNodes().isEmpty() }

        drag(Offset(size.x * 0.3f, size.y * 0.4f), Offset(size.x * 0.3f, 0f))
        waitFor { canvas().strokes.size == 1 }
        for (m in listOf("Seç", "Ekle", "Düzenle", "Taşı", "Genişlet")) {
            mode(m)
            compose.waitForIdle()
            assertEquals(m, 0, layersIn("tool_row_context"))
            assertEquals(m, 1, layersIn("mode_selector"))
        }
        compose.onNode(hasContentDescription("Katmanlar") and hasAnyAncestor(hasTestTag("mode_selector"))).performScrollTo().performClick()
        waitFor { compose.onAllNodesWithTag("layers_panel").fetchSemanticsNodes().isNotEmpty() }
    }
}

/** v19 dar ekran: üst satıra sığmayan düğmelere (Katmanlar) satır kaydırılarak ulaşılır. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w330dp-h700dp-xhdpi")
class V19NarrowRowTest : V19EditorBase() {
    @Test
    fun topRowScrollsToReachLayers() {
        createCanvasAndOpen("Dar")
        // v47 dar ekranda Katmanlar mod menüsünün son grubunda: satır kaydırılınca ulaşılır
        val node = hasContentDescription("Katmanlar") and hasAnyAncestor(hasTestTag("mode_selector"))
        val row = compose.onNodeWithTag("mode_selector").fetchSemanticsNode().boundsInRoot
        fun right() = compose.onNode(node).fetchSemanticsNode().let { it.positionInRoot.x + it.size.width }
        repeat(8) {
            if (right() > row.right + 1f) {
                compose.onNodeWithTag("mode_selector").performTouchInput { swipeLeft() }
                compose.waitForIdle()
            }
        }
        assertTrue("kaydırınca görünür: ${right()} <= ${row.right}", right() <= row.right + 1f)
        compose.onNode(node).performClick()
        waitFor { compose.onAllNodesWithTag("layers_panel").fetchSemanticsNodes().isNotEmpty() }
    }
}

/**
 * v19 PDF olarak kaydet penceresi: dosya adı, sayfa aralığı, kağıt boyutu, yön, kenar boşluğu, kalite, arka plan,
 * sayfa numarası; seçilen ayarlarla yazılır ve sonraki açılışta hatırlanır. Not tuvalinin bağlam menüsünde formül var.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V19PdfSheetTest : V19EditorBase() {

    private fun line(id: String, y: Float) =
        Stroke(id, (0..20).map { StrokePoint(100f + it * 40f, y) }, "#1E1E1E", BrushType.INK_PEN, 10f, 1f, 0)

    private fun chip(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("pdf_options_sheet")))

    private fun summary(text: String) {
        runCatching {
            waitFor { compose.onAllNodes(hasTestTag("pdf_summary") and hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        }.onFailure {
            val now = compose.onAllNodesWithTag("pdf_summary").fetchSemanticsNodes().map { n ->
                n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString()
            }
            throw AssertionError("özet '$text' değil: $now", it)
        }
    }

    /** v42 başlıktaki Kaydet → birleşik pencere PDF sekmesiyle açılır; not almada varsayılan CMYK, bu test RGB'yi dener. */
    private fun openPdfSheet() {
        compose.onNodeWithTag("header_save").performClick()
        waitFor { compose.onAllNodesWithTag("pdf_options_sheet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("color_rgb").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun pdfOptionsSheet() {
        BitmapPdfSink.install()
        try {
            waitForText("Henüz bir tuvaliniz yok", substring = true)
            val repository = GlobalContext.get().get<CanvasRepository>()
            runBlocking {
                val c = repository.createCanvas("Ders Notu", 1240, 1754, "#FFFFFF", false, kind = CanvasKind.NOTES)
                repository.saveStrokes(c.localId, listOf(listOf(line("a", 300f)), listOf(line("b", 600f)), listOf(line("c", 900f))))
            }
            waitForText("Ders Notu")
            compose.onNodeWithText("Ders Notu").performSemanticsAction(SemanticsActions.OnClick)
            waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }

            // Not tuvalinde Ekle satırında formül var (v47 bağlam ekleme menüsü kaldırıldı)
            mode("Ekle")
            waitFor { compose.onAllNodesWithContentDescription("Formül ekle").fetchSemanticsNodes().isNotEmpty() }
            mode("Çiz")

            // Kaydet → PDF: hemen kaydetmez, pencere açılır
            openPdfSheet()
            assertEquals(null, BitmapPdfSink.last)
            summary("3 sayfa · Tuval oranı · 595 × 842 nokta")
            compose.onNodeWithTag("pdf_file_name").performTextClearance()
            compose.onNodeWithTag("pdf_file_name").performTextInput("Fizik 1")
            waitForText("Fizik_1.pdf")

            // Aralık 2–3; geçersiz aralıkta kaydet kapalı
            chip("Aralık").performSemanticsAction(SemanticsActions.OnClick)
            waitFor { compose.onAllNodesWithTag("pdf_range_from").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("pdf_range_from").performTextClearance()
            compose.onNodeWithTag("pdf_range_from").performTextInput("4")
            compose.onNodeWithTag("pdf_save").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("pdf_range_from").performScrollTo().performTextClearance()
            compose.onNodeWithTag("pdf_range_from").performTextInput("2")
            compose.onNodeWithTag("pdf_range_to").performTextClearance()
            compose.onNodeWithTag("pdf_range_to").performTextInput("3")
            summary("2 sayfa · Tuval oranı · 595 × 842 nokta")

            // Kağıt A4, yön yatay, normal kenar boşluğu, sayfa numarası, beyaz kağıt
            assertTrue("tuval oranında yön seçimi yok", compose.onAllNodes(hasText("Yatay") and hasAnyAncestor(hasTestTag("pdf_options_sheet"))).fetchSemanticsNodes().isEmpty())
            chip("A4").performSemanticsAction(SemanticsActions.OnClick)
            summary("2 sayfa · A4 dikey · 595 × 842 nokta")
            chip("Yatay").performSemanticsAction(SemanticsActions.OnClick)
            summary("2 sayfa · A4 yatay · 842 × 595 nokta")
            chip("Normal").performSemanticsAction(SemanticsActions.OnClick)
            chip("Resim").performSemanticsAction(SemanticsActions.OnClick)
            waitForText("ekrandaki gibi resim olarak", substring = true)
            chip("Keskin (vektör)").performSemanticsAction(SemanticsActions.OnClick)
            compose.onNodeWithTag("pdf_page_numbers").performScrollTo().performClick()
            compose.onNodeWithTag("pdf_background").performScrollTo().performClick()
            compose.onNodeWithTag("pdf_location").assertExists()

            compose.onNodeWithTag("pdf_save").performScrollTo().assertIsEnabled().performClick()
            // Bildirim görünür kalır (v19 öncesi durum sıfırlanınca hemen iptal oluyordu)
            waitForText("PDF kaydedildi", substring = true)
            compose.mainClock.advanceTimeBy(1000)
            waitForText("PDF kaydedildi", substring = true)
            val sink = BitmapPdfSink.last!!
            assertEquals(2, sink.pages.size)
            assertEquals(listOf(842 to 595), sink.pages.map { it.width to it.height }.distinct())
            // Beyaz kağıt, kenar boşluğunda çizim yok
            assertEquals(android.graphics.Color.WHITE, sink.pages[0].bitmap.getPixel(10, 10))
            assertTrue(compose.onAllNodesWithTag("pdf_options_sheet").fetchSemanticsNodes().isEmpty())

            // Tekrar açılınca ayarlar hatırlanır (sayfa seçimi ve ad yenilenir)
            openPdfSheet()
            summary("3 sayfa · A4 yatay · 842 × 595 nokta")
            assertFalse(compose.onAllNodes(hasText("Fizik_1.pdf")).fetchSemanticsNodes().isNotEmpty())
            SystemBack.press()
            waitFor { compose.onAllNodesWithTag("pdf_options_sheet").fetchSemanticsNodes().isEmpty() }
        } finally {
            BitmapPdfSink.uninstall()
        }
    }
}
