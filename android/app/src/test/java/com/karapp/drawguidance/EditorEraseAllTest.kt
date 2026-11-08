package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Kullanıcı senaryosu: farklı fırçalarla çizilen her şey, birbirine binen birkaç silgi geçişiyle temizlenince
 * (hiçbir geçiş tek başına örtmese de) elemanlar listesi boşalır ve veride çizim kalmaz.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorEraseAllTest : EditorTestBase() {

    @Test
    fun erasingTheWholeDrawingEmptiesTheElementsList() {
        createCanvasAndOpen("Temizlik")
        val size = canvasSize()
        val left = size.x * 0.3f
        val top = size.y * 0.35f

        pickBrush("Pastel Boya")
        drag(Offset(left, top), Offset(size.x * 0.3f, 0f))
        pickBrush("Serpinti")
        drag(Offset(left, top + 60f), Offset(size.x * 0.3f, 30f))
        pickBrush("Kalem")
        drag(Offset(left + 40f, top - 40f), Offset(0f, 160f))
        waitFor { canvas().strokes.size == 3 }

        clickElements()
        waitForText("Pastel Boya", substring = true)
        compose.onNodeWithContentDescription("Paneli kapat").performClick()

        // Silgiyle yatay geçişler: alan boyunca aralıklı, birbirine binen şeritler
        compose.onNodeWithContentDescription("Silgi").performScrollTo().performClick()
        waitForDescription("Seçili fırça: Silgi")
        var y = top - 140f
        while (y <= top + 240f) {
            drag(Offset(left - 160f, y), Offset(size.x * 0.3f + 320f, 0f), steps = 30)
            y += 45f
        }

        waitFor(20_000) { canvas().strokes.isEmpty() }
        clickElements()
        waitForText("Henüz çizim yok", substring = true)
        assertTrue(compose.onAllNodesWithText("Pastel Boya 1").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Kalem 1").fetchSemanticsNodes().isEmpty())
    }
}
