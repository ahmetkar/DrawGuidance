package com.karapp.drawguidance

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.GuideSession
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialScripts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v50 "Resimden çizim" eğitimi: resim eklenir ve vektörleştirilir, resim kaldırılır, vektör çizgiler kalıp olur ve
 * kalemle kopyalanır, kütüphaneden kedi kalıbı çizilir; eğitim bitince tuval eski haline döner ve kalıp kapanır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V50TraceTutorialTest : V32EditorBase() {

    private fun play(kind: CanvasKind, id: String, check: (Int) -> Unit) {
        val group = TutorialScripts.groups(kind).first { it.id == id }
        val before = live().strokes.size
        compose.runOnIdle { vm().tutorial.fast = true; vm().tutorial.start(group) }
        val kids = kind == CanvasKind.KIDS
        val shown = { if (kids) exists("kids_tutorial") else calloutShown() }
        waitFor { shown() }
        for (i in 1..group.steps.size) {
            runUntil(120_000) { vm().tutorial.ui.value?.index == i - 1 }
            awaitDemo(120_000)
            advanceMain(300)
            check(i - 1)
            if (kids) { compose.onNodeWithTag("mascot_next", useUnmergedTree = true).performClick(); compose.waitForIdle() } else next()
        }
        waitFor { !shown() }
        assertEquals("eğitim bitince tuval eski haline döner", before, live().strokes.size)
        assertEquals(GuideSession.NONE, vm().ruler.session.value)
        assertTrue(id in vm().tutorial.completed.value)
    }

    private fun traced() = live().strokes.count { it.shape?.kind == ShapeRegistry.TRACED }
    private fun images() = live().strokes.count { it.brushType == BrushType.IMAGE }
    private fun pens() = live().strokes.count { it.brushType == BrushType.INK_PEN }

    private fun advanced(kind: CanvasKind, id: String) = play(kind, id) { step ->
        when (step) {
            0 -> assertEquals(1, images())
            1 -> { assertEquals(1, images()); assertEquals("resim vektörleştirildi", 1, traced()) }
            2 -> { assertEquals("resim kaldırıldı", 0, images()); assertEquals(1, traced()) }
            3 -> assertEquals(GuideSession.TEMPLATE, vm().ruler.session.value)
            4 -> assertTrue("kalıbın kenarları kalemle çizildi", pens() >= 4)
            5 -> assertEquals("kütüphane kalıbı çizilip Bitir'e basıldı", GuideSession.NONE, vm().ruler.session.value)
        }
    }

    @Test fun art() { createCanvasAndOpen("Resimden çizim"); advanced(CanvasKind.ART, "v50_trace_art") }

    @Test fun kids() {
        val repo = org.koin.core.context.GlobalContext.get().get<com.karapp.drawguidance.data.repository.CanvasRepository>()
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        kotlinx.coroutines.runBlocking { repo.createCanvas("Kalıp", 1000, 1414, "#FFFFFF", aiEnabled = false, kind = CanvasKind.KIDS) }
        waitForText("Kalıp")
        compose.onAllNodesWithText("Kalıp")[0].performClick()
        waitFor { exists("drawing_canvas") }
        play(CanvasKind.KIDS, "v50_trace_kids") { step -> if (step == 1) assertTrue(pens() >= 3) }
    }
}
