package com.karapp.drawguidance

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v33 (1.1) sayfa sınırı: çizgi romanda 1000, not almada 200 sayfa. Sınırda sayfa şeridinde Sayfa ekle pasif, ekleme ve
 * çoğaltma yapılmaz, "Sayfa sınırına ulaşıldı" bildirilir; yeni tuval başlangıç sayfa sayısı da sınırla kısılır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V33PageLimitTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    /** Depoda [pages] sayfalı tuval oluşturup listeden açar. */
    private fun openWithPages(name: String, kind: CanvasKind, pages: Int) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 800, 1100, "#FFFFFF", aiEnabled = false, kind = kind, pageCount = pages) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun addButton() = compose.onNode(hasContentDescription("Sayfa ekle") and hasAnyAncestor(hasTestTag("page_strip")))

    @Test
    fun kindLimits() {
        assertEquals(1000, CanvasKind.COMIC.maxPages)
        assertEquals(200, CanvasKind.NOTES.maxPages)
        assertEquals(1, CanvasKind.ART.maxPages)
        // Depo da sınırı uygular
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val created = runBlocking { repo().createCanvas("Fazla", 800, 1100, "#FFFFFF", false, kind = CanvasKind.NOTES, pageCount = 450) }
        assertEquals(200, created.pageCount)
    }

    @Test
    fun notesStopAt200() {
        openWithPages("Not 199", CanvasKind.NOTES, 199)
        waitFor { vm().canvas.value!!.pageCount == 199 }
        addButton().assertIsEnabled()
        addButton().performClick()
        waitFor { vm().canvas.value!!.pageCount == 200 }
        addButton().assertIsNotEnabled()
        // Sınırda ekleme / çoğaltma yapılmaz, bildirilir
        compose.runOnUiThread { vm().addPage() }
        waitForText("Sayfa sınırına ulaşıldı (200)")
        compose.runOnUiThread { vm().duplicatePage(0) }
        compose.waitForIdle()
        assertEquals(200, vm().canvas.value!!.pageCount)
        shot("v33-page-limit-notes")
    }

    @Test
    fun comicStopsAt1000() {
        openWithPages("Roman 1000", CanvasKind.COMIC, 1000)
        waitFor { vm().canvas.value!!.pageCount == 1000 }
        addButton().assertIsNotEnabled()
        compose.runOnUiThread { vm().addPage() }
        waitForText("Sayfa sınırına ulaşıldı (1000)")
        assertEquals(1000, vm().canvas.value!!.pageCount)
        // Sayfa silinince yeniden eklenebilir
        compose.runOnUiThread { vm().deletePage(999) }
        waitFor { vm().canvas.value!!.pageCount == 999 }
        addButton().assertIsEnabled()
    }

    @Test
    fun formShowsLimit() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithContentDescription("Yeni Tuval").performClick()
        waitForText("Yeni Tuval Oluştur")
        compose.onAllNodesWithText("Çizgi roman")[0].performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitForText("En çok 1000 sayfa")
        compose.onNodeWithText("Çalışma / Not").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("En çok 200 sayfa")
    }
}
