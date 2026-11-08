package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.comic.ComicEffects
import com.karapp.drawguidance.data.model.BalloonStyle
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import com.karapp.drawguidance.ui.screens.canvasedit.state.BalloonTextOptions
import com.karapp.drawguidance.ui.screens.canvasedit.state.EffectOptions
import com.karapp.drawguidance.ui.screens.canvasedit.state.PanelSets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v36 (8.7) birleşik denetim: çizgi roman tuvaline eklenen her yeni öğe (nesne, efekt, konuşma balonu, karakter,
 * şablon paneli) seçilir, taşınır, ölçeklenir (düzenlenir), kopyalanır ve silinir; her işlem geri alınabilir.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V36IntegrationTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun open(name: String, kind: CanvasKind) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun strokes(): List<Stroke> = vm().canvas.value?.strokes.orEmpty()

    /** Öğe türü: ad, tanıma koşulu ve ekleme. */
    private class Item(val name: String, val match: (Stroke) -> Boolean, val insert: () -> Unit)

    @Test
    fun everyNewItemIsEditableMovableCopyableAndDeletable() {
        open("Birleşik denetim", CanvasKind.COMIC)
        val items = listOf(
            // v43 kütüphane nesneleri (Şekil ekle → Nesneler) kaldırıldı; nesneler artık resim (Ekle → Nesneler)
            Item("efekt", { it.shape?.kind == "lib.fx.flame" }) { vm().insertEffect(ComicEffects.byId("fx.flame")!!, EffectOptions(size = 0.3f)) },
            Item("balon", { it.text?.box?.balloon != null }) { vm().insertBalloon(BalloonStyle.SPEECH, 1, BalloonTextOptions(content = "Çık dışarı!")) },
            Item("karakter", { it.brushType == com.karapp.drawguidance.data.model.BrushType.IMAGE }) {
                vm().insertCharacter(com.karapp.drawguidance.data.comicassets.ComicAssets.catalog.characters.first { it.theme == "okul" }.cover)
            },
            Item("panel", { PanelSets.isPanel(it) }) { vm().insertTemplate("two_rows") },
        )
        for (item in items) {
            compose.runOnIdle { vm().chooseMode(EditMode.SELECT); item.insert() }
            waitFor { strokes().any(item.match) }
            val first = strokes().first(item.match)
            // Tek öğe seçili (şablonda tek panel)
            compose.runOnIdle { vm().chooseMode(EditMode.SELECT); vm().clearSelection(); first.points[0].let { p -> vm().selectOnlyAt(p.x, p.y, 4f) } }
            waitFor { vm().selectedIds.value == setOf(first.id) }
            val count = strokes().count(item.match)
            val b0 = ElementOps.bounds(first)!!
            // Taşı
            compose.runOnIdle { vm().moveSelected(40f, 30f) }
            waitFor { ElementOps.bounds(strokes().first { it.id == first.id })!!.left > b0.left + 39f }
            val moved = ElementOps.bounds(strokes().first { it.id == first.id })!!
            assertEquals(item.name, b0.top + 30f, moved.top, 1f)
            // Düzenle (ölçekle)
            compose.runOnIdle { vm().scaleSelected(1.5f, 1.5f) }
            waitFor { ElementOps.bounds(strokes().first { it.id == first.id })!!.width > moved.width * 1.3f }
            // Kopyala
            compose.runOnIdle { vm().duplicateSelected() }
            waitFor { strokes().count(item.match) == count + 1 }
            // Sil (kopya seçili)
            compose.runOnIdle { vm().deleteSelected() }
            waitFor { strokes().count(item.match) == count }
            assertTrue(item.name, strokes().any { it.id == first.id })
            // Geri al: silinen kopya geri gelir
            compose.runOnIdle { vm().undo() }
            waitFor { strokes().count(item.match) == count + 1 }
            compose.runOnIdle { vm().redo() }
            waitFor { strokes().count(item.match) == count }
        }
        settleFor(300)
        shotAll("v36-integration-page")
    }
}

/**
 * v36 (8.7) küçük telefon görünümü (360×640 dp): yeni ve genişletilen pencerelerin hepsi açılır, ekrana sığar (yatay
 * taşma yok) ve ilk içerikleri görünür.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w360dp-h640dp-xhdpi")
class V36MobileWindowsTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    @Test
    fun windowsFitSmallPhone() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas("Telefon", 800, 1130, "#FFFFFF", aiEnabled = false, kind = CanvasKind.COMIC) }
        waitForText("Telefon")
        compose.onAllNodesWithText("Telefon")[0].performClick()
        waitFor { exists("drawing_canvas") }
        val rootWidth = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        val windows = listOf(
            "Şekil ekle" to "shape_picker", "Efektler" to "effects_sheet", "Konuşma balonu" to "balloon_sheet",
            "Karakterler" to "characters_sheet", "Şablonlar" to "templates_sheet",
        )
        for ((label, tag) in windows) {
            addMenu(label)
            waitFor { exists(tag) }
            val w = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.width
            assertTrue("$label sığmalı: $w > $rootWidth", w <= rootWidth + 0.5f)
            settleFor(200)
            shot("v36-mobile-$tag")
            SystemBack.press()
            waitFor { !exists(tag) }
        }
        openBackground()
        val w = compose.onNodeWithTag("background_sheet").fetchSemanticsNode().boundsInRoot.width
        assertTrue(w <= rootWidth + 0.5f)
        shot("v36-mobile-background")
    }
}
