package com.karapp.drawguidance

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.i18n.I18n
import com.karapp.drawguidance.ui.drawing.ShapePickerFilter
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v43 (14) Şekil ekle penceresinde "Nesneler" sekmesi ve nesne çizimleri tamamen kaldırıldı: hiçbir tuval türünde
 * nesne bölümü, nesne kimliği (`lib.obj.*`) ya da stil süzgeci yok; arama kalan şekillerde çalışır.
 */
class V43ShapePickerFilterTest {
    @After fun resetLanguage() = I18n.setLanguage("tr")

    @Test fun noObjectsAnywhere() {
        for (kind in CanvasKind.entries) {
            val cats = ShapeRegistry.categories(kind)
            assertTrue("$kind nesne bölümü", cats.none { it.section == "Nesneler" })
            assertTrue("$kind nesne kimliği", cats.flatMap { it.items }.none { it.startsWith("lib.obj.") })
        }
        assertNull("kütüphanede yok", ShapeLibrary.find("lib.obj.sword.comic"))
        assertTrue(ShapeLibrary.categories.none { it.id.startsWith("obj_") })
    }

    @Test fun searchStillWorks() {
        val cats = ShapeRegistry.categories(CanvasKind.ART)
        val idx = ShapePickerFilter.Index(cats, { id -> I18n.tr(ShapeRegistry.title(id) ?: id) }, { c -> I18n.tr(c.title) })
        val stars = idx.search("yildiz").flatMap { it.items }
        assertTrue(stars.isNotEmpty())
        assertTrue(idx.search("kılıç").isEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V43ShapePickerTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun openKind(name: String, kind: CanvasKind) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun inPicker(text: String) = compose.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag("shape_picker")))

    @Test
    fun comicPickerHasNoObjectsTab() {
        openKind("Şekiller", CanvasKind.COMIC)
        compose.runOnIdle { vm().openShapePicker() }
        waitFor { exists("shape_picker") }
        assertTrue(inPicker("Nesneler").fetchSemanticsNodes().isEmpty())
        assertTrue(!exists("shape_style_filter"))
        shot("v43-comic-shape-picker")
        compose.onNode(hasTestTag("shape_search")).performClick()
        compose.onNode(hasTestTag("shape_search")).performTextInput("kilic")
        waitForText("Sonuç yok. Başka bir ad deneyin.")
    }
}
