package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.sin

/** v14 görsel kontrolü (DG_SHOTS verilince ekran görüntüsü): tuval ve resim galerisi (v16: Ayır/Parçala kalktı). */
abstract class V14ShotsBase : V13ShotsBase() {

    private fun sample(seed: Int, w: Int, h: Int): List<Stroke> {
        val colors = listOf("#1E88E5", "#E53935", "#43A047", "#FB8C00", "#8E24AA")
        return (0 until 4).map { k ->
            val cx = w * (0.25f + 0.17f * k); val cy = h * (0.3f + 0.12f * ((k + seed) % 3))
            val r = minOf(w, h) * (0.12f + 0.03f * k)
            val pts = (0..48).map { i ->
                val a = i / 48f * 6.283f
                StrokePoint(cx + cos(a) * r * (1f + 0.25f * sin(a * (2 + seed))), cy + sin(a) * r)
            }
            Stroke("s$seed-$k", pts, colors[(k + seed) % colors.size], BrushType.MARKER, minOf(w, h) * 0.02f, 0.9f, k)
        }
    }

    protected fun canvasGallery(prefix: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val repository = GlobalContext.get().get<CanvasRepository>()
        runBlocking {
            val specs = listOf(
                Triple("Deniz Feneri", 1080 to 1350, CanvasKind.ART),
                Triple("Manzara", 1600 to 1000, CanvasKind.ART),
                Triple("Ders Notu", 1240 to 1754, CanvasKind.NOTES),
                Triple("Logo Taslağı", 1080 to 1080, CanvasKind.ART),
                Triple("Hikâye Panosu", 1920 to 1080, CanvasKind.ART),
            )
            specs.forEachIndexed { i, (name, dims, kind) ->
                repository.createCanvas(name, dims.first, dims.second, if (i == 2) "#F5F0E6" else "#FFFFFF", false, kind = kind,
                    pageCount = if (kind == CanvasKind.NOTES) 3 else 1)
            }
            val created = repository.observeCanvases().first()
            created.forEachIndexed { i, c -> repository.saveStrokes(c.localId, listOf(sample(i, c.width, c.height))) }
        }
        waitForText("Manzara")
        waitFor { compose.onAllNodesWithTag("canvas_gallery").fetchSemanticsNodes().isNotEmpty() }
        repeat(6) { Thread.sleep(120); compose.waitForIdle(); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }
        shot("$prefix-1-canvas-gallery")
        compose.onNodeWithContentDescription("Liste görünümü").performClick()
        compose.waitForIdle()
        shot("$prefix-2-canvas-list")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V14CanvasShotsTest : V14ShotsBase() {
    @Test fun canvases() = canvasGallery("v14-canvas")
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V14ImagesShotsTest : V14ShotsBase() {
    @Test fun images() = gallery("v14-images")
}
