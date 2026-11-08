package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Çalışma/Not tuvali: A4 dikey boyut, yalnızca not kalemleri, matematik/geometri şekilleri. Şekiller
 * sürükleyerek (ya da dokunarak) seçili kalemle eklenir; parçalı şekiller tek grup olur.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorNotesCanvasTest : EditorTestBase() {

    private fun exists(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun notesCanvasHasPensAndGeometryShapes() {
        createNotesCanvasAndOpen("Geometri Notları")
        waitFor { canvas().kind == CanvasKind.NOTES }
        assertEquals(1240, canvas().width)
        assertEquals(1754, canvas().height)
        waitForDescription("Seçili fırça: Tükenmez Kalem")

        // Fırça seçicide yalnızca not kalemleri
        compose.onNodeWithContentDescription("Fırça Seç").performScrollTo().performClick()
        waitForText("Kurşun Kalem")
        listOf("Dolma Kalem", "Jel Kalem", "Keçeli Kalem", "Fosforlu Kalem", "Yazı Kalemleri").forEach { assertTrue(it, exists(it)) }
        listOf("Pastel", "Kömür", "Airbrush", "Sanat Kalemleri").forEach {
            assertTrue("$it not tuvalinde olmamalı", compose.onAllNodesWithText(it).fetchSemanticsNodes().isEmpty())
        }
        compose.onNodeWithText("Dolma Kalem").performSemanticsAction(SemanticsActions.OnClick)
        waitForDescription("Seçili fırça: Dolma Kalem")

        // Şekil kataloğu: geometri; manzara şekilleri yok
        addMenu("Şekil ekle")
        waitForText("Geometrik Cisimler")
        listOf("Dik Üçgen", "Silindir").forEach { assertTrue(it, exists(it)) }
        assertTrue(compose.onAllNodesWithText("Dağlar").fetchSemanticsNodes().isEmpty())
        // v15: grafikler ve grafik eğrileri ayrı bölümde
        compose.onAllNodesWithText("Grafik")[0].performClick()
        waitForText("Grafik eğrileri")
        listOf("Grafikler", "Koordinat ekseni", "Sayı doğrusu", "Sinüs", "Parabol").forEach { assertTrue(it, exists(it)) }
        compose.onAllNodesWithText("Temel")[0].performClick()
        waitForText("Geometrik Cisimler")
        compose.onNodeWithText("Küp").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Küp")

        // Sürükleyerek küp: kesik ayrıtlar dahil tek vektör eleman
        val size = canvasSize()
        drag(Offset(size.x * 0.25f, size.y * 0.25f), Offset(size.x * 0.4f, size.x * 0.4f))
        waitFor { canvas().strokes.isNotEmpty() }
        val cube = canvas().strokes
        assertEquals("küp tek eleman olmalı", 1, cube.size)
        assertEquals(BrushType.SHAPE, cube[0].brushType)
        assertEquals("CUBE", cube[0].shape?.kind)
        assertTrue("kesik ayrıtlar da saklanmalı", cube[0].shape!!.subpaths.any { it.dashed })
        val index = ElementOps.index(cube)
        assertEquals("şekil tek eleman", 1, index.elements.size)
        val box = ElementOps.bounds(cube)!!
        assertEquals("küp kare olmalı", box.width, box.height, box.width * 0.05f)

        // v15: eklenen şekil seçili olarak Taşı moduna geçilir
        waitForText("1 eleman seçili")
        waitForText("Taşı")
        // Dokunarak varsayılan boyutta daire; geri al ile kalkar
        addMenu("Şekil ekle")
        waitForText("Merkez ve Yarıçap")
        compose.onNodeWithText("Daire").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Daire")
        tap(Offset(size.x * 0.5f, size.y * 0.75f))
        waitFor { canvas().strokes.size == cube.size + 1 }
        val circle = ElementOps.bounds(canvas().strokes.last())!!
        assertEquals(1240 * 0.3f, circle.width, 20f)
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { canvas().strokes.size == cube.size }

        // Tuval listesinde tür etiketi
        compose.onNodeWithText("Bitti").performClick()
        compose.onNodeWithContentDescription("Geri").performClick()
        waitFor { compose.onAllNodes(hasText("Not · 1240×1754", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }
}
