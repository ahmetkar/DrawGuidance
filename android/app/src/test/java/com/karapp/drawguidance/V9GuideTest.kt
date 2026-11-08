package com.karapp.drawguidance

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.GuideKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v10 kılavuz ve perspektif çizgileri ayrı eklenir: pencerede yalnızca tür seçilir, genel ayarlar
 * çizim alanının altındaki çubukta verilir; ikisi birlikte kullanılabilir ve ayrı kaldırılır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V9GuideTest : EditorTestBase() {

    @Test
    fun guideAndPerspectiveAreAddedSeparatelyAndSetFromTheBarBelowTheCanvas() {
        createCanvasAndOpen("Perspektif")
        assertEquals(GuideKind.NONE, canvas().guide.kind)
        assertEquals(0, canvas().guide.perspective)

        // Kılavuz: pencerede yalnızca tür, ayrıntılı ayar yok
        addMenu("Kılavuz çizgileri")
        waitForText("Kılavuz Çizgileri")
        assertTrue("perspektif türleri kılavuz penceresinde yok", compose.onAllNodesWithText("2 Nokta Perspektif").fetchSemanticsNodes().isEmpty())
        assertTrue("pencerede ayrıntılı ayar yok", compose.onAllNodesWithText("Aralık").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Kareli").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().guide.kind == GuideKind.GRID }
        // Genel ayarlar çizim alanının altındaki çubukta
        waitFor { compose.onAllNodesWithTag("guide_settings_bar").fetchSemanticsNodes().isNotEmpty() }
        waitForText("Kılavuz: Kareli")
        assertTrue(compose.onAllNodesWithText("Aralık").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Bitti").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { compose.onAllNodesWithTag("guide_settings_bar").fetchSemanticsNodes().isEmpty() }

        // Perspektif ayrı eklenir, kılavuzla birlikte durur
        addMenu("Perspektif çizgileri")
        waitForText("2 Nokta Perspektif")
        assertTrue("kılavuz türleri perspektif penceresinde yok", compose.onAllNodesWithText("Noktalı").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("2 Nokta Perspektif").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().guide.perspective == 2 }
        assertEquals("kılavuz yerinde kalır", GuideKind.GRID, canvas().guide.kind)
        waitForText("Perspektif · 2 nokta")
        assertTrue(compose.onAllNodesWithText("Ufuk çizgisi").fetchSemanticsNodes().isNotEmpty())
        assertTrue("kaçış noktaları tuvalde sürüklenir", compose.onAllNodesWithText("Kaçış noktalarını tuvalde sürükle").fetchSemanticsNodes().isNotEmpty())

        // Perspektifi kaldır: kılavuz kalır
        compose.onNodeWithText("Kaldır").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().guide.perspective == 0 }
        assertEquals(GuideKind.GRID, canvas().guide.kind)
        assertTrue("kılavuz varsayılan olarak dışa aktarılmaz", !canvas().guide.includeInExport)
    }
}
