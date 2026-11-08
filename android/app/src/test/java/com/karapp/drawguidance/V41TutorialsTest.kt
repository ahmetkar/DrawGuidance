package com.karapp.drawguidance

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialScripts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v40/v41 eğitimleri: her türün yenilikler grubu (gelişmiş türlerde "Yeni", basit resim ve basit notta tanıtım, çocukta
 * yenilikler) baştan sona oynar; cetvel adımları ayrı Cetvel / Şekil kalıbı düğmelerini ve cetvelin yanındaki
 * tutamak düğmelerini gösterir; eğitim bitince tuval eski haline döner ve grup tamamlanmış sayılır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V41TutorialsTest : V32EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun open(name: String, kind: CanvasKind) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun playGroup(kind: CanvasKind, id: String, shotPrefix: String) {
        open("Eğitim $id", kind)
        val group = TutorialScripts.groups(kind).firstOrNull { it.id == id }
        assertNotNull("$kind: $id", group)
        val before = live().strokes.size
        compose.runOnIdle { vm().tutorial.fast = true; vm().tutorial.start(group!!) }
        // Çocuk tuvalinde maskotun balonu, ötekilerde açıklama kutusu
        val kids = kind == CanvasKind.KIDS
        val shown = { if (kids) exists("kids_tutorial") else calloutShown() }
        waitFor { shown() }
        val count = group!!.steps.size
        for (i in 1..count) {
            runUntil(60_000) { vm().tutorial.ui.value?.index == i - 1 }
            awaitDemo(60_000)
            advanceMain(200)
            assertEquals(group.steps[i - 1].anchor, vm().tutorial.ui.value?.step?.anchor)
            if (!kids) assertEquals("$i / $count", stepText())
            if (i == 1 || i == count) shot("$shotPrefix-$i")
            if (kids) {
                compose.onNodeWithTag("mascot_next", useUnmergedTree = true).performClick()
                compose.waitForIdle()
            } else next()
        }
        waitFor { !shown() }
        assertEquals("eğitim bitince tuval eski haline döner", before, live().strokes.size)
        assertTrue(id in vm().tutorial.completed.value)
        assertTrue("cetvel kapandı", !exists("ruler_overlay"))
    }

    @Test fun artWhatsNew() = playGroup(CanvasKind.ART, "art_v40", "v41-tut-art")
    @Test fun notesWhatsNew() = playGroup(CanvasKind.NOTES, "notes_v40", "v41-tut-notes")
    @Test fun comicWhatsNew() = playGroup(CanvasKind.COMIC, "comic_v40", "v41-tut-comic")
    @Test fun simpleArtIntro() = playGroup(CanvasKind.SIMPLE_ART, "simple_art_intro", "v41-tut-simple-art")
    @Test fun simpleNotesIntro() = playGroup(CanvasKind.SIMPLE_NOTES, "simple_notes_intro", "v41-tut-simple-notes")
    @Test fun kidsWhatsNew() = playGroup(CanvasKind.KIDS, "kids_v40", "v41-tut-kids")

    /** Her türün eğitim listesinde yenilikler grubu var; çizgi romanda hazır arka planlar Mekân arka planı düğmesinde. */
    @Test
    fun everyKindHasTutorials() {
        for (kind in CanvasKind.entries) assertTrue(kind.name, TutorialScripts.groups(kind).isNotEmpty())
        for (kind in listOf(CanvasKind.ART, CanvasKind.NOTES, CanvasKind.COMIC, CanvasKind.KIDS)) {
            val fresh = TutorialScripts.whatsNew(kind)
            assertNotNull(kind.name, fresh)
            assertTrue(kind.name, TutorialScripts.groups(kind).any { it.id == fresh!!.id })
        }
        // v48 yenilikler yeni menü sistemini tanıtır (mod menüsü, Özellikler, Taşı/Genişlet)
        val comic = TutorialScripts.whatsNew(CanvasKind.COMIC)!!
        assertTrue(comic.steps.any { it.anchor == com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor.BRUSH_SETTINGS })
        // Mekân arka planı eğitimi çizgi romanın tüm grupları arasında korunur
        assertTrue(TutorialScripts.groups(CanvasKind.COMIC).any { g -> g.steps.any { it.anchor == com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor.add("locations") } })
    }
}
