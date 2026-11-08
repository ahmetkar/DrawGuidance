package com.karapp.drawguidance

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.comic.ComicEffects
import com.karapp.drawguidance.content.comic.ComicLines
import com.karapp.drawguidance.content.comic.ComicLinesKind
import com.karapp.drawguidance.content.comic.ComicSfx
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v36 (8.2) efekt ve kategori adları bütün dillerde çevrili. */
class V36EffectsNamesTest {
    @org.junit.After fun reset() = com.karapp.drawguidance.i18n.I18n.setLanguage("tr")

    @Test
    fun effectNamesAreTranslated() {
        val names = ComicEffects.all.map { it.title } + com.karapp.drawguidance.content.comic.EffectGroup.entries.map { it.title }
        for (lang in com.karapp.drawguidance.i18n.AppLanguage.entries.filter { it != com.karapp.drawguidance.i18n.AppLanguage.TR }) {
            com.karapp.drawguidance.i18n.I18n.setLanguage(lang.code)
            val same = names.filter { com.karapp.drawguidance.i18n.I18n.tr(it) == it }
            assertTrue("${lang.code} çevrilmeyen: $same", same.isEmpty())
        }
    }
}

/**
 * v36 (8.2) Efektler ve odak çizgileri tek düğme, tek pencere: Ekle menüsünde eski iki düğme yok; efekt eklenir
 * (dokunulan yere), seçili efektin boyutu/açısı/rengi/opaklığı değişir, kopyalanır ve silinir; ses yazıları aynı
 * pencerede (istenen yazılar önce); odak çizgileri odak noktası, kalınlık, renk ve opaklıkla eklenir ve seçili grup
 * sonradan değişir; yumuşak anime çizgileri pastel renklidir.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V36EffectsTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun openComic(name: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = CanvasKind.COMIC) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun strokes(): List<Stroke> = vm().canvas.value?.strokes.orEmpty()

    private fun effects() = strokes().filter { it.shape?.kind?.startsWith("lib." + ComicEffects.ID_PREFIX) == true }

    private fun slide(container: String, value: Float) {
        val node = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress) and hasAnyAncestor(hasTestTag(container)))
        node.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun openEffects() {
        addMenu("Efektler")
        waitFor { exists("effects_sheet") }
    }

    @Test
    fun oneButtonOneWindowPictureEffectsAndLines() {
        openComic("Efektler")
        // Ekle menüsünde tek düğme: eski "Efekt yazısı" ve "Hız ve odak çizgileri" düğmeleri yok
        compose.runOnIdle { vm().chooseMode(com.karapp.drawguidance.ui.screens.canvasedit.EditMode.ADD) }
        compose.waitForIdle()
        waitFor { compose.onAllNodes(hasContentDescription("Efektler")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodes(hasContentDescription("Efekt yazısı")).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasContentDescription("Hız ve odak çizgileri")).fetchSemanticsNodes().isEmpty())
        openEffects()
        // v48 yalnız iki sekme: Efektler (resimli efektler) ve Odak çizgileri; çizilen efektler ve ses yazıları yok
        waitFor { exists("fximg") }
        assertTrue(exists("effects_tab_images") && exists("effects_tab_lines"))
        assertFalse(exists("effects_tab_effects"))
        assertFalse(exists("effect_explosion") || exists("effects_group_fx_magic") || exists("effects_group_fx_sound") || exists("sfx_sheet"))
        shot("v36-effects-sheet")
    }

    @Test
    fun focusLinesWithFocalPointColorAndSelectedGroup() {
        openComic("Odak")
        openEffects()
        click("effects_tab_lines")
        waitFor { exists("lines_sheet") && exists("lines_ZOOM") }
        slide("lines_new_focus_x", 0.2f)
        slide("lines_new_thickness", 2f)
        compose.onNode(hasContentDescription("#E53935") and hasAnyAncestor(hasTestTag("lines_new_color"))).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        shot("v36-effects-lines")
        click("lines_ZOOM")
        waitFor { strokes().size > 20 }
        val zoom = strokes()
        val p = zoom.first().params!!.comicLines!!
        assertEquals(ComicLinesKind.ZOOM.name, p.kind)
        assertEquals(0.2f, p.focusX, 0.011f)
        assertTrue(zoom.all { it.colorHex == "#E53935" && it.groupId == zoom.first().groupId })
        // Seçili grubun ayarları: odak noktası değişince aynı türle yeniden üretilir, tek grup kalır
        val (_, o) = vm().selectedLinesOptions()!!
        assertEquals(2f, o.thickness, 0.15f)
        compose.runOnIdle { vm().setLinesOptions(o.copy(focusX = 0.8f, opacity = 0.6f)) }
        waitFor { strokes().first().params?.comicLines?.focusX == 0.8f }
        assertTrue(strokes().all { it.opacity == 0.6f })
        assertEquals(1, strokes().map { it.groupId }.distinct().size)
        // Yumuşak anime: renk seçilmezse pastel renkler
        compose.runOnIdle { vm().deleteSelected() }
        waitFor { strokes().isEmpty() }
        compose.runOnIdle { vm().insertComicLines(ComicLinesKind.ANIME_SOFT, com.karapp.drawguidance.ui.screens.canvasedit.state.LinesOptions()) }
        waitFor { strokes().isNotEmpty() }
        val soft = strokes().map { it.colorHex }.toSet()
        assertTrue("pastel: $soft", soft.size >= 3 && soft.all { it in ComicLines.SOFT_COLORS })
        assertTrue(strokes().first().params!!.comicLines!!.palette)
    }
}
