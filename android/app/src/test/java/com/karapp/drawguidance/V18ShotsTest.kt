package com.karapp.drawguidance

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

/**
 * v18 görsel kontrolü (DG_SHOTS verilince ekran görüntüsü): moda göre üst menüler (Çiz, Seç, Düzenle, Taşı, Genişlet),
 * kağıt boyutu penceresi, koyu kağıtta grafik, kalem basıncıyla çizimler ve kalem ayarları.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V18ShotsTest : V15ShotsBase() {

    private fun pen(id: String, type: BrushType, color: String, y: Float, size: Float, order: Int, tilt: Float = 0f) = Stroke(
        id, (0..60).map { i ->
            val t = i / 60f
            StrokePoint(120f + 1000f * t, y + sin(t * 6.28f) * 40f, 0.1f + 0.9f * sin(t * 3.14f), tilt)
        }, color, type, size, 1f, order,
    )

    private fun topTool(label: String) = compose.onNode(hasContentDescription(label) and hasAnyAncestor(hasTestTag("tool_row_context")))

    @Test
    fun v18() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val repository = GlobalContext.get().get<CanvasRepository>()
        runBlocking {
            val c = repository.createCanvas("Kalem ve Grafik", 1240, 1600, "#1B1F24", false, kind = CanvasKind.NOTES)
            val graph = ShapeFactory.build(
                "curve.sinus", Offset(100f, 90f), Offset(1140f, 620f), ShapeStyle(strokeColorHex = "#E3F2FD", strokeWidth = 3f), id = "g", order = 0,
                graph = ShapeFactoryDefaults.sine(freq = 2, paper = "#1B1F24"),
            )!!
            repository.saveStrokes(
                c.localId,
                listOf(
                    listOf(
                        graph,
                        pen("p1", BrushType.PENCIL, "#FFFFFF", 800f, 14f, 1),
                        pen("p2", BrushType.INK_PEN, "#80DEEA", 960f, 18f, 2),
                        pen("p3", BrushType.CHARCOAL, "#FFCC80", 1130f, 28f, 3),
                        pen("p4", BrushType.PENCIL, "#C5E1A5", 1300f, 12f, 4, tilt = 0.8f),
                        pen("p5", BrushType.MARKER, "#F48FB1", 1460f, 22f, 5),
                    ),
                ),
            )
        }
        waitForText("Kalem ve Grafik")
        compose.onNodeWithText("Kalem ve Grafik").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }
        // Kalem algılandı (kalem modu çipi)
        penTap()
        settle()
        shot("v18-1-draw-pen")

        mode("Seç")
        val size = canvasSize()
        tap(Offset(size.x * 0.5f, size.y * 0.66f))
        settle()
        shot("v18-2-select")
        mode("Düzenle")
        settle()
        shot("v18-3-edit")
        mode("Taşı")
        settle()
        shot("v18-4-move")
        mode("Genişlet")
        settle()
        shot("v18-5-expand")
        compose.onNodeWithTag("canvas_size_chip").performClick()
        waitFor { compose.onAllNodesWithTag("canvas_size_sheet").fetchSemanticsNodes().isNotEmpty() }
        settle()
        shot("v18-6-size-sheet")
        SystemBack.press()
        settle()

        compose.onNodeWithContentDescription("Geri").performClick()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Ayarlar").performSemanticsAction(SemanticsActions.OnClick)
        // v36 kalem ayarları Çizim sekmesinde
        waitFor { compose.onAllNodesWithTag("settings_tab_drawing").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings_tab_drawing").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { compose.onAllNodesWithTag("stylus_settings_card").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("stylus_settings_card").performScrollTo()
        settle()
        shot("v18-7-stylus-settings")
    }

    /** Kalemle tek dokunuş (kalem algılansın; nokta çizer, kaydedilir). */
    private fun penTap() {
        val origin = compose.onNodeWithTag("drawing_canvas").fetchSemanticsNode().positionInWindow
        val size = canvasSize()
        val at = Offset(origin.x + size.x * 0.92f, origin.y + size.y * 0.96f)
        fun ev(action: Int, t: Long) = MotionEvent.obtain(
            t, t, action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
            arrayOf(MotionEvent.PointerCoords().apply { x = at.x; y = at.y; pressure = 0.5f; this.size = 0.05f }),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        val t0 = SystemClock.uptimeMillis()
        compose.runOnUiThread { compose.activity.window.decorView.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, t0)) }
        compose.runOnUiThread { compose.activity.window.decorView.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, t0 + 30)) }
        compose.waitForIdle()
    }
}

/** Test verisi: koyu kağıtta, deseni 2 olan sinüs grafiği. */
internal object ShapeFactoryDefaults {
    fun sine(freq: Int, paper: String) =
        com.karapp.drawguidance.ui.drawing.ShapeRegistry.defaultGraph("curve.sinus").copy(freq = freq, paper = paper)
}
