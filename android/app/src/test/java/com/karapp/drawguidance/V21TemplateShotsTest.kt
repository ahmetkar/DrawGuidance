package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Plane3D
import com.karapp.drawguidance.data.repository.ShapeTemplateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v21 şekil veritabanı önerilerinin görsel kontrolü (DG_SHOTS verilince): elle çizilen ev ve yüz için öneri kartı ve
 * kesik çizgili önizleme, kabul sonrası; 3B çizimde ön yüzde masa ve serbest düzlemde küp (gölgeli cisim seçeneği).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V21TemplateShotsTest : V15ShotsBase() {

    private fun drawStrokes(strokes: List<List<P>>) {
        strokes.forEach { s ->
            compose.onNodeWithTag("drawing_canvas").performTouchInput {
                down(Offset(s[0].x, s[0].y))
                for (i in 1 until s.size) moveTo(Offset(s[i].x, s[i].y))
                up()
            }
        }
        compose.waitForIdle()
    }

    private fun suggestAfterPause() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(900))
        compose.waitForIdle()
        waitFor(20_000) { compose.onAllNodesWithTag("handwriting_chip").fetchSemanticsNodes().isNotEmpty() }
        settle()
    }

    private fun accept(cd: String) {
        waitFor { compose.onAllNodesWithContentDescription(cd).fetchSemanticsNodes().isNotEmpty() }
        // v31 balondaki seçenekler yatay kayar: önce görünür alana getirilir
        compose.onNodeWithContentDescription(cd).performScrollTo().performClick()
        waitFor { compose.onAllNodesWithTag("handwriting_chip").fetchSemanticsNodes().isEmpty() }
        settle()
    }

    @Test
    fun templates() {
        createCanvasAndOpen("Şablonlar")
        val repo = GlobalContext.get().get<ShapeTemplateRepository>()
        waitFor(90_000) { repo.indexFlow.value != null }
        val size = compose.onNodeWithTag("drawing_canvas").fetchSemanticsNode().size.let { Offset(it.width.toFloat(), it.height.toFloat()) }
        val paper = canvas().let { Offset(it.width.toFloat(), it.height.toFloat()) }
        val k = fit(size, paper.x, paper.y, 1f, 0f).x - fit(size, paper.x, paper.y, 0f, 0f).x
        /** Kağıt üzerinde (tuval koordinatı) merkez ve boyla elle çizilmiş şablon, ekran koordinatında. */
        fun at(key: String, cx: Float, cy: Float, s: Float, seed: Long): List<List<P>> {
            val c = fit(size, paper.x, paper.y, cx, cy)
            // v38 şekil önerisi birbirine yakın 4+ elemanda: az çizgili şablon parçalara bölünerek çizilir
            return com.karapp.drawguidance.content.shapes.ShapeAugment.atLeast(handDrawnTemplate(key, c.x, c.y, s * k, seed), 4, java.util.Random(seed))
        }

        // Ev (çok elemanlı)
        drawStrokes(at("yapi.ev.besik", 0.27f * paper.x, 0.22f * paper.y, 0.36f * paper.x, 21))
        suggestAfterPause()
        shot("v21-t1-house-chip")
        accept("Şablonla değiştir: Ev (beşik çatılı)")
        shot("v21-t2-house-accepted")

        // Gülen yüz
        drawStrokes(at("yuz.ifade.gulen", 0.73f * paper.x, 0.22f * paper.y, 0.3f * paper.x, 5))
        suggestAfterPause()
        shot("v21-t3-face-chip")
        accept("Şablonla değiştir: Gülen yüz")

        // v38 tek çizgi (çekik göz): hiçbir öneri yok
        val eye = fit(size, paper.x, paper.y, 0.27f * paper.x, 0.52f * paper.y)
        drawStrokes(handDrawnTemplate("yuz.goz.cekik", eye.x, eye.y, 0.28f * paper.x * k, 8).take(1))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(900))
        settle()
        assertTrue(compose.onAllNodesWithTag("handwriting_chip").fetchSemanticsNodes().isEmpty())
        shot("v21-t4-single-none")

        // v23: tuvaldeki 3B çizim kaldırıldı (3B düzenleyici penceresine taşındı); masa ve küp düz kağıtta
        val before = canvas().strokes.size
        // düz kağıtta gülen yüzle aynı gruba girmesin diye biraz aşağıda
        drawStrokes(at("mobilya.masa", 0.7f * paper.x, 0.64f * paper.y, 0.3f * paper.x, 3))
        waitFor { canvas().strokes.size >= before + 4 }
        suggestAfterPause()
        shot("v21-t5-3d-table-chip")
        accept("Şablonla değiştir: Masa")
        shot("v21-t6-3d-table-accepted")

        drawStrokes(at("cisim.kup", 0.3f * paper.x, 0.82f * paper.y, 0.24f * paper.x, 4))
        suggestAfterPause()
        shot("v21-t7-cube-chip")
        accept("Şablonla değiştir: Küp (çizim) (gölgeli)")
        val cube = canvas().strokes.last()
        assertEquals(BrushType.SHAPE, cube.brushType)
        shot("v21-t8-cube-solid")
    }
}
