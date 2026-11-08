package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.VectorEdit
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * v17 görsel kontrolü (DG_SHOTS verilince ekran görüntüsü): mod menüsü (Ekle/Düzenle), moda göre üst menü, bağlam
 * menüsü, alan boyama, çizim yardımcıları seçicisi, buraya ekle menüsü ve çizim düzeltme önerisi.
 */
abstract class V17ShotsBase : V15ShotsBase() {

    private val ink = ShapeStyle(strokeColorHex = "#263238", strokeWidth = 3f)

    protected fun sketches(prefix: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val repository = GlobalContext.get().get<CanvasRepository>()
        runBlocking {
            val c = repository.createCanvas("Çizim Yardımcıları", 1600, 1200, "#FFFFFF", false, kind = CanvasKind.ART)
            repository.saveStrokes(
                c.localId,
                listOf(
                    listOf(
                        shape("house", "lib.sketch.house", 80f, 100f, 580f, 560f, 0, ink),
                        shape("face", "lib.sketch.face_front", 660f, 80f, 1060f, 580f, 1, ink),
                        shape("tree", "lib.sketch.tree", 1120f, 80f, 1520f, 576f, 2, ink),
                        shape("car", "lib.sketch.car_side", 80f, 700f, 740f, 996f, 3, ink),
                        shape("cat", "lib.sketch.cat", 820f, 660f, 1100f, 993f, 4, ink),
                        shape("apple", "lib.sketch.apple", 1180f, 700f, 1500f, 1044f, 5, ink),
                    ),
                ),
            )
        }
        waitForText("Çizim Yardımcıları")
        openCanvas("Çizim Yardımcıları")
        val size = canvasSize()
        fun at(x: Float, y: Float) = fit(size, 1600f, 1200f, x, y)
        shot("$prefix-1-draw-mode")

        // Seç: seçim yokken yönerge; eve dokununca seçilir, üst menü seçim işlemleri
        mode("Seç")
        settle()
        shot("$prefix-2-select-idle")
        val house = canvas().strokes.first { it.id == "house" }
        val wall = ShapeFactory.subPaths(house).first { it.closed }.points
        tap(at(wall[0].x, wall[0].y))
        waitForText("1 eleman seçili")
        settle()
        shot("$prefix-3-select-top-menu")

        // İki parmakla uzun basma: şekil seçenekleri (v23: başka elemana basılırsa o seçilir; evin üstüne basılır)
        val mid = at(330f, 330f)
        compose.onNodeWithTag("drawing_canvas").performTouchInput { down(0, mid - Offset(30f, 0f)); down(1, mid + Offset(30f, 0f)) }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("drawing_canvas").performTouchInput { up(0); up(1) }
        waitFor { contextMenuOpen() }
        settle()
        shot("$prefix-4-context-shape")
        compose.onNode(hasText("Alan boya") and inContextMenu()).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { compose.onAllNodesWithTag("region_fill_controls").fetchSemanticsNodes().isNotEmpty() }
        // Ev: duvar, çatı ve kapıyı ayrı renklerle boya
        val box = com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps.bounds(house)!!
        fun paint(color: String, fx: Float, fy: Float) {
            compose.onNodeWithContentDescription("Renk $color").performSemanticsAction(SemanticsActions.OnClick)
            val before = ShapeFactory.subPaths(canvas().strokes.first { it.id == "house" }).count { it.tag == VectorEdit.REGION }
            tap(at(box.left + box.width * fx, box.top + box.height * fy))
            waitFor { ShapeFactory.subPaths(canvas().strokes.first { it.id == "house" }).count { it.tag == VectorEdit.REGION } > before }
        }
        paint("#FDD835", 0.2f, 0.85f)
        paint("#E53935", 0.5f, 0.2f)
        paint("#6D4C41", 0.5f, 0.9f)
        paint("#1E88E5", 0.22f, 0.62f)
        settle()
        shot("$prefix-5-region-fill")
        compose.onNodeWithContentDescription("Bitti").performClick()
        settle()

        // Ekle modu: üst satırdan Şekil ekle (v47 boş yere dokunmanın ekleme menüsü kaldırıldı)
        addMenu("Şekil ekle")
        settle()
        shot("$prefix-6-add-here")
        // v42 resim tuvalinde çizim yardımcıları yerine genel şekiller (akış şeması, oklar, afişler...)
        waitForText("Akış şeması")
        compose.onAllNodesWithText("Akış şeması")[0].performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Karar")
        settle()
        shot("$prefix-7-shape-picker")
    }

    protected fun correction(prefix: String) {
        createCanvasAndOpen("Düzeltme")
        val size = canvasSize()
        val c = Offset(size.x * 0.5f, size.y * 0.35f)
        val r = size.x * 0.28f
        val rnd = java.util.Random(5)
        // v38 tek çizgiye öneri yok: iç içe iki titrek çember (çizgi önerisi, tek düğme)
        for (rad in listOf(r, r * 0.45f)) compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(Offset(c.x + rad, c.y))
            for (i in 1..80) {
                val a = i / 80.0 * 2 * PI * 1.03
                val rr = rad * (1f + (rnd.nextFloat() - 0.5f) * 0.09f)
                moveTo(Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat()))
            }
            up()
        }
        waitFor { canvas().strokes.size == 2 }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(900))
        waitFor(15_000) { compose.onAllNodesWithTag("line_suggest_chip").fetchSemanticsNodes().isNotEmpty() }
        settle()
        shot("$prefix-8-correction")
        compose.onNodeWithContentDescription("Düzeltmeyi uygula: 2 çizgi birden").performClick()
        waitFor { compose.onAllNodesWithContentDescription("Düzeltmeyi uygula: 2 çizgi birden").fetchSemanticsNodes().isEmpty() }
        settle()
        shot("$prefix-9-corrected")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V17SketchShotsTest : V17ShotsBase() {
    @Test fun sketches() = sketches("v17")
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V17CorrectionShotsTest : V17ShotsBase() {
    @Test fun correction() = correction("v17")
}
