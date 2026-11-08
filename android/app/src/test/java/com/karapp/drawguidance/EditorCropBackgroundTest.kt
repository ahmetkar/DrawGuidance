package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tuvali kırpma: çerçeve daraltılır, dışarıda kalan çizimler silinmez (tuval yeniden genişletilince görünür).
 * Boya kutusu (seçim yokken) tuvalin arka plan rengini değiştirir ve tek adımda geri alınır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorCropBackgroundTest : EditorTestBase() {

    @Test
    fun cropHidesOutsideStrokesAndBackgroundColorIsUndoable() {
        createCanvasAndOpen("Kırpma")
        val size = canvasSize()
        val start = Offset(size.x * 0.2f, size.y * 0.4f)
        drag(start, Offset(size.x * 0.2f, 0f))
        waitFor { canvas().strokes.size == 1 }
        val map = mappingFrom(start, size.x * 0.2f)
        // Sağ tarafta ikinci çizgi
        drag(map.toScreen(820f, 700f), Offset(map.scale * 200f, 0f))
        waitFor { canvas().strokes.size == 2 }
        val right = canvas().strokes[1]
        assertTrue(right.points.first().x > 800f)

        // Kırp: sağ kenarı içeri çek, sağdaki çizgi çerçeve dışında kalsın
        mode("Kırp")
        waitForText("Çerçevenin kenarlarını sürükle", substring = true)
        drag(map.toScreen(1080f, 540f), Offset(-map.scale * 400f, 0f), steps = 10)
        waitForText("680 × 1080")
        compose.onNode(hasText("Kırp") and hasClickAction()).performClick()
        waitFor { canvas().width == 680 }
        assertEquals(1080, canvas().height)
        // Çizimler silinmedi: sağdaki çizgi tuvalin dışında (gizli) duruyor
        assertEquals(2, canvas().strokes.size)
        assertTrue("sağdaki çizgi gizli kalmalı", canvas().strokes[1].points.first().x > canvas().width)

        // Genişletilince yeniden görünür
        mode("Genişlet")
        // v34: kırpınca görünüm yeni kağıda sığdırılır (eski eşleme geçersiz); kağıt üst menüdeki "Sağdan genişlet" ile büyütülür
        repeat(6) { if (canvas().width <= 1000) { compose.onNodeWithContentDescription("Sağdan genişlet").performScrollTo().performClick(); compose.waitForIdle() } }
        waitFor { canvas().width > 1000 }
        assertEquals(2, canvas().strokes.size)
        compose.onNodeWithText("Bitti").performClick()

        // Boya kutusu: seçili eleman yokken tuvalin arka planını boyar; pencerede yalnızca "Renk seç" ve renk seçici
        assertEquals("#FFFFFF", canvas().backgroundColorHex)
        clickFill()
        waitForText("Renk seç")
        assertTrue("uygula düğmesi yok, seçilen renk anında uygulanır", compose.onAllNodesWithText("Uygula").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("color_sv_area").performTouchInput { click(Offset(width * 0.9f, height * 0.9f)) }
        waitFor { canvas().backgroundColorHex != "#FFFFFF" }
        val painted = canvas().backgroundColorHex
        assertTrue("çizimler değişmez", canvas().strokes.size == 2)
        SystemBack.press()
        waitFor { compose.onAllNodesWithTag("color_pick_sheet").fetchSemanticsNodes().isEmpty() }
        assertEquals(painted, canvas().backgroundColorHex)
        // Tek adımda geri alınır
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { canvas().backgroundColorHex == "#FFFFFF" }
    }
}
