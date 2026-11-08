package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushParams
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TubeMaterial
import com.karapp.drawguidance.data.model.TubeParams
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** v20 görsel kontrolü (DG_SHOTS verilince): 3B fırça izleri, fırça paneli bölümleri, boyut penceresi, öneri kartı. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V20ShotsTest : V15ShotsBase() {

    private fun tube(id: String, y: Float, color: String, size: Float, order: Int, t: TubeParams) = Stroke(
        id, (0..60).map { i -> val x = 140f + 960f * i / 60f; StrokePoint(x, y + sin(i / 60f * 2f * PI.toFloat()) * 60f) },
        color, BrushType.BRUSH_3D, size, 1f, order, params = BrushParams(tube = t).takeIf { t != TubeParams() },
    )

    private fun loop(id: String, cx: Float, cy: Float, r: Float, color: String, order: Int, t: TubeParams) = Stroke(
        id, (0..90).map { i -> val a = i / 90f * 2.3f * PI.toFloat(); StrokePoint(cx + r * cos(a) * (1f - i / 300f), cy + r * sin(a) * (1f - i / 300f)) },
        color, BrushType.BRUSH_3D, 70f, 1f, order, params = BrushParams(tube = t),
    )

    private fun panelTo(tag: String, name: String) {
        compose.onNodeWithTag(tag).performScrollTo()
        settle()
        shot(name)
    }

    @Test
    fun v20() {
        // v21: kalem ayarları yalnız kalem tanınınca görünür (eğim bölümü için tanıma açık)
        com.karapp.drawguidance.platform.Stylus.setRecognition(com.karapp.drawguidance.platform.Stylus.Recognition.ALWAYS)
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val repository = GlobalContext.get().get<CanvasRepository>()
        runBlocking {
            val c = repository.createCanvas("3B Tüpler", 1240, 1754, "#F7F4EC", true)
            repository.saveStrokes(c.localId, listOf(listOf(
                tube("a", 220f, "#E53935", 60f, 0, TubeParams()),
                tube("b", 470f, "#1E88E5", 60f, 1, TubeParams(material = TubeMaterial.MATTE, relief = 1f)),
                tube("c", 720f, "#43A047", 60f, 2, TubeParams(material = TubeMaterial.METAL, gloss = 0.9f)),
                tube("d", 970f, "#FDD835", 60f, 3, TubeParams(material = TubeMaterial.GLASS)),
                tube("e", 1220f, "#8E24AA", 60f, 4, TubeParams(lightAngle = 315f, shadow = 0.9f, shadowSoftness = 0.2f)),
                loop("f", 620f, 1520f, 170f, "#FB8C00", 5, TubeParams(lightAngle = 200f, relief = 0.9f)),
            )))
        }
        waitForText("3B Tüpler")
        openCanvas("3B Tüpler")
        shot("v20-1-canvas-3d")

        // Fırça paneli (3B fırça): boyut, ayarlar, eğim, 3B bölümü
        pickBrush("3B Fırça")
        openProperties()
        waitFor { compose.onAllNodesWithTag("brush_tuning_section").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Metal") and hasAnyAncestor(hasTestTag("brush_tuning_section"))).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        panelTo("brush_size_value", "v20-2-panel-size")
        panelTo("brush_stabilizer", "v20-3-panel-speed-stabilizer")
        panelTo("brush_tilt_threshold", "v20-4-panel-tilt")
        panelTo("brush_3d_gloss", "v20-5-panel-3d")
        panelTo("brush_3d_shadow_soft", "v20-6-panel-3d-shadow")
        compose.onNodeWithTag("brush_size_value").performScrollTo().performClick()
        waitFor { compose.onAllNodesWithTag("brush_size_sheet").fetchSemanticsNodes().isNotEmpty() }
        settle()
        shot("v20-7-size-sheet")
        compose.onNode(hasText("İptal") and hasAnyAncestor(hasTestTag("brush_size_sheet"))).performClick()
        waitFor { compose.onAllNodesWithTag("brush_size_sheet").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        compose.waitForIdle()

        // Öneri kartı (kapatma düğmesiyle)
        pickBrush("Kalem")
        val size = canvasSize()
        val c = Offset(size.x * 0.75f, size.y * 0.35f)
        val r = size.x * 0.16f
        val rnd = java.util.Random(4)
        // v38 tek çizgiye öneri yok: yan yana iki çember (çizgi önerisi)
        for (cc in listOf(c, Offset(c.x - r * 1.9f, c.y + r * 0.4f))) compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(Offset(cc.x + r, cc.y))
            for (i in 1..72) {
                val a = i / 72.0 * 2 * PI * 1.04
                val rr = r * (1f + (rnd.nextFloat() - 0.5f) * 0.08f)
                moveTo(Offset(cc.x + rr * cos(a).toFloat(), cc.y + rr * sin(a).toFloat()))
            }
            up()
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(900))
        compose.waitForIdle()
        waitFor(15_000) { compose.onAllNodesWithTag("line_suggest_chip").fetchSemanticsNodes().isNotEmpty() }
        settle()
        shot("v20-8-suggestion-chip")
        compose.onNodeWithContentDescription("Çizgi önerilerini kapat").performClick()
        waitForText("Çizgi önerileri kapatıldı", substring = true)
        settle()
        shot("v20-9-suggestions-off")
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        waitFor { compose.onAllNodesWithTag("suggestions_menu_item").fetchSemanticsNodes().isNotEmpty() }
        settle()
        shot("v20-10-menu")
    }
}
