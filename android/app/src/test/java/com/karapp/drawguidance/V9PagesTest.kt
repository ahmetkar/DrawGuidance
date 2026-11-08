package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v9 sayfalar: çalışma/not tuvali birden çok sayfayla oluşturulur, çizim alanının hemen altındaki
 * şeritten sayfa eklenir ve sayfalar arası geçilir; her sayfanın çizimleri kendine aittir.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V9PagesTest : EditorTestBase() {

    private fun createNotes(name: String, pages: Int) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithContentDescription("Yeni Tuval").performClick()
        waitForText("Yeni Tuval Oluştur")
        compose.onNodeWithText("Çalışma / Not").clickInPopup()
        compose.onNode(hasSetTextAction()).performTextInput(name)
        waitForDescription("Sayfa sayısı: $pages")
        compose.onNodeWithContentDescription("Sayfa sayısı: $pages").clickInPopup()
        compose.onNodeWithText("Oluştur").clickInPopup()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun notesCanvasKeepsEachPageSeparatelyAndTheStripSwitchesPages() {
        createNotes("Ders Notları", 4)
        waitFor { canvas().pageCount == 4 }
        assertEquals(0, canvas().currentPage)
        // Sayfa şeridi yalnızca çalışma/not tuvalinde ve çizim alanının altında
        waitFor { compose.onAllNodesWithTag("page_strip").fetchSemanticsNodes().isNotEmpty() }
        waitForText("1 / 4")

        val size = canvasSize()
        drag(Offset(size.x * 0.2f, size.y * 0.3f), Offset(size.x * 0.5f, 0f))
        waitFor { canvas().strokes.size == 1 }

        // 3. sayfaya geç: boş sayfa
        compose.onNodeWithContentDescription("Sayfa 3").performClick()
        waitFor { canvas().currentPage == 2 }
        waitForText("3 / 4")
        assertTrue("yeni sayfa boş", canvas().strokes.isEmpty())
        drag(Offset(size.x * 0.25f, size.y * 0.5f), Offset(0f, size.y * 0.2f))
        waitFor { canvas().strokes.size == 1 }

        // 1. sayfaya dön: ilk çizim yerinde
        compose.onNodeWithContentDescription("Sayfa 1").performClick()
        waitFor { canvas().currentPage == 0 }
        assertEquals(1, canvas().strokes.size)
        assertEquals(4, canvas().pageCount)
        assertEquals(2, canvas().pages.sumOf { it.size })

        // Şeritten sayfa ekleme: açık sayfadan sonra eklenir ve ona geçilir
        compose.onNodeWithContentDescription("Sayfa ekle").performClick()
        waitFor { canvas().pageCount == 5 }
        assertEquals(1, canvas().currentPage)
        assertTrue(canvas().strokes.isEmpty())
        waitForText("2 / 5")

        // Tuval listesinde toplam eleman sayısı ve sayfa sayısı görünür
        compose.onNodeWithContentDescription("Geri").performClick()
        waitForText("5 sayfa", substring = true)
    }
}
