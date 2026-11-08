package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Plane3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.atan2

/**
 * v22 çoklu seçimde düzeltme: iki çizim seçilince alt çubukta "Çoklu düzeltme öner"; v38 çizgi önerisinde "Çizgileri düzelt (2)" iki
 * çizimi birden düzgün şekle çevirir (tek geri alma adımı).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V22MultiFixTest : V21EditorBase() {
    @Test
    fun fixAllSelectedStrokes() {
        createCanvasAndOpen("Çoklu")
        val size = canvasSize()
        prefs().setHandwriting(false)
        wobblyCircle(Offset(size.x * 0.3f, size.y * 0.3f), size.x * 0.12f, 3)
        waitFor { canvas().strokes.size == 1 }
        val line = jitteryLine(size.y * 0.6f, 1.5f)
        waitFor { canvas().strokes.size == 2 }
        prefs().setHandwriting(true)
        val before = canvas().strokes
        mode("Seç")
        tap(Offset(size.x * 0.3f + size.x * 0.12f, size.y * 0.3f))
        advanceMain(400)
        tap(Offset(size.x * 0.45f, size.y * 0.6f))
        waitFor { compose.onAllNodesWithText("Çoklu düzeltme öner").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Çoklu düzeltme öner").performClick()
        waitFor { chipShown() }
        waitFor { compose.onAllNodesWithText("Çizgileri düzelt (2)").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Düzeltmeyi uygula: 2 çizgi birden").performClick()
        waitFor { canvas().strokes.all { s -> before.first { it.id == s.id }.points != s.points } }
        assertEquals(before.map { it.id }, canvas().strokes.map { it.id })
        assertNotEquals(line.points, canvas().strokes.last().points)
        compose.onNodeWithContentDescription("Geri al").performClick()
        waitFor { canvas().strokes.map { it.points } == before.map { it.points } }
    }
}

/**
 * v22 son fırça hatırlanır: Marker seçiliyken silgiye geçip üst menüdeki Fırça düğmesine dokununca Marker'a dönülür;
 * fırçadayken dokununca seçici açılır ve Marker seçili görünür.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V22BrushMemoryTest : V21EditorBase() {
    @Test
    fun brushButtonReturnsToLastBrush() {
        createCanvasAndOpen("Fırça")
        pickBrush("Marker")
        compose.onNodeWithContentDescription("Silgi").performClick()
        waitForDescription("Seçili fırça: Silgi")
        compose.onNodeWithContentDescription("Fırça Seç").performClick()
        waitForDescription("Seçili fırça: Marker")
        assertTrue(compose.onAllNodesWithText("Fosforlu Kalem").fetchSemanticsNodes().isEmpty())
        // Silgiden sonra seçici açılınca son fırça seçili
        compose.onNodeWithContentDescription("Silgi").performClick()
        waitForDescription("Seçili fırça: Silgi")
        compose.onNodeWithContentDescription("Fırça Seç").performClick()
        compose.onNodeWithContentDescription("Fırça Seç").performClick()
        waitForText("Fosforlu Kalem")
        waitFor { compose.onAllNodes(hasText("Marker") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
    }
}
