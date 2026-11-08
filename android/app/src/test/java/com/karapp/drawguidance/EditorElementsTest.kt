package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.settings.ColorPaletteStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Elemanlar: değen çizimler tek eleman, silgi listede yok, "Renk Ver" paletten
 * seçilen rengi uygular, Seç modunda dokunarak seçim / çift dokunarak seçimi kaldırma,
 * Taşı modunda sadece seçili eleman taşınır. Başlık altındaki menüde kaydet düğmesi yoktur.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorElementsTest : EditorTestBase() {

    @Test
    fun elementsEraserRecolorSelectionAndMove() {
        createCanvasAndOpen("Deniz Feneri")
        // v7: "Resim olarak kaydet" yeniden çizim ekranında, başlığın en sağında
        assertTrue(compose.onAllNodesWithContentDescription("Resim olarak kaydet").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithContentDescription("Seçili fırça: Kalem").assertExists()

        val size = canvasSize()
        val s1 = Offset(size.x * 0.2f, size.y * 0.3f)
        val s2 = Offset(size.x * 0.2f, size.y * 0.6f)

        // Pastel: yatay çizgi, onu kesen dikey çizgi (aynı eleman) ve uzakta ayrı bir çizgi
        pickBrush("Pastel Boya")
        drag(s1, Offset(250f, 0f))
        waitFor { canvas().strokes.size == 1 }
        drag(s1 + Offset(100f, -80f), Offset(0f, 160f))
        drag(s2, Offset(250f, 0f))
        waitFor { canvas().strokes.size == 3 }
        assertTrue(canvas().strokes.all { it.brushType == BrushType.PASTEL })

        // Silgi ile ayrı çizginin bir kısmı silinir
        compose.onNodeWithContentDescription("Silgi").performClick()
        waitForDescription("Seçili fırça: Silgi")
        drag(s2 + Offset(120f, -40f), Offset(0f, 80f))
        waitFor { canvas().strokes.size == 4 }

        // Elemanlar: 2 eleman (ilki 2 çizimden), silgi listede yok
        clickElements()
        waitForText("Pastel Boya 1")
        compose.onNodeWithText("Pastel Boya 2").assertExists()
        compose.onNodeWithText("2 çizim").assertExists()
        assertTrue(compose.onAllNodesWithText("Pastel Boya 3").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Silgi 3").fetchSemanticsNodes().isEmpty())

        // Renk Ver: paletteki (kayıtlı) renk seçilip uygulanır
        GlobalContext.get().get<ColorPaletteStore>().add("#2E7D32")
        compose.onNodeWithText("Pastel Boya 1").performClick()
        compose.onNodeWithText("Renk Ver").performClick()
        waitForText("Uygula")
        compose.onNodeWithContentDescription("Kayıtlı renk #2E7D32").clickInPopup()
        compose.onNodeWithText("Uygula").clickInPopup()
        waitFor { canvas().strokes.take(2).all { it.colorHex == "#2E7D32" } }
        assertEquals("#1E1E1E", canvas().strokes[2].colorHex)

        // Seç modu: panel kapanır; dokun = seç, çift dokun = seçimi kaldır
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        mode("Seç")
        waitForText("1 eleman seçili")
        // v17: Sil / Kopyala üst menüde; alt çubukta Seçimi temizle ve Bitti
        assertTrue(compose.onAllNodes(hasContentDescription("Sil") and hasAnyAncestor(hasTestTag("tool_row_context"))).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Seçimi temizle").performClick()
        waitFor { compose.onAllNodesWithText("eleman seçili", substring = true).fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithTag("drawing_canvas").performTouchInput { click(s2 + Offset(200f, 0f)) }
        waitForText("1 eleman seçili")
        compose.onNodeWithTag("drawing_canvas").performTouchInput { click(s1 + Offset(40f, 0f)) }
        waitForText("2 eleman seçili")
        compose.onNodeWithTag("drawing_canvas").performTouchInput { doubleClick(s2 + Offset(200f, 0f)) }
        waitForText("1 eleman seçili")

        // Taşı modu: yalnızca seçili eleman (ilk iki çizim) kayar
        val before = canvas().strokes.map { it.points.first() }
        mode("Taşı")
        drag(Offset(size.x * 0.5f, size.y * 0.45f), Offset(90f, 60f))
        waitFor { canvas().strokes[0].points.first() != before[0] }
        val after = canvas().strokes.map { it.points.first() }
        val dx = after[0].x - before[0].x
        val dy = after[0].y - before[0].y
        assertTrue("sağa-aşağı taşınmalı: $dx,$dy", dx > 10f && dy > 5f)
        assertEquals(dx, after[1].x - before[1].x, 0.01f)
        assertEquals(before[2], after[2])
        assertEquals(before[3], after[3])
    }
}
