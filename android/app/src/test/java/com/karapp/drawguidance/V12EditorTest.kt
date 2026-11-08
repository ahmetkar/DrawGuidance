package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.DiagramSide
import com.karapp.drawguidance.ui.drawing.DiagramRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Üst menüdeki fare imleci inceleme modunu açar: dokunuş hiçbir şeyi seçmez, sürükleme çizmez/taşımaz yalnızca
 * kaydırır, çift dokunma kağıdı sığdırır. Yinele geri alınanı geri getirir.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V12ViewModeTest : EditorTestBase() {

    @Test
    fun cursorModeOnlyPansAndRedoRestoresUndoneWork() {
        createCanvasAndOpen("İnceleme")
        val size = canvasSize()
        val start = Offset(size.x * 0.25f, size.y * 0.4f)
        drag(start, Offset(size.x * 0.4f, 0f))
        waitFor { canvas().strokes.size == 1 }
        val drawn = canvas().strokes.single()

        // Geri al / Yinele
        compose.onNodeWithContentDescription("Yinele").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { canvas().strokes.isEmpty() }
        compose.onNodeWithContentDescription("Yinele").assertIsEnabled()
        compose.onNodeWithContentDescription("Yinele").performScrollTo().performClick()
        waitFor { canvas().strokes.size == 1 }
        assertEquals("aynı çizim geri geldi", drawn.points, canvas().strokes.single().points)
        compose.onNodeWithContentDescription("Yinele").assertIsNotEnabled()

        // v17: Seç (İncele ile birleşik): boş yerde dokunma seçmez, sürüklemek çizmez/taşımaz (kaydırır);
        // çizime dokunmak seçer, boş yere dokunmak seçimi kaldırır
        mode("Seç")
        waitForText("Elemana dokunarak seç", substring = true)
        tap(Offset(size.x * 0.5f, size.y * 0.15f))
        assertTrue("seçim yok", compose.onAllNodesWithText("eleman seçili", substring = true).fetchSemanticsNodes().isEmpty())
        // v47 seçim yokken boş yere dokunmak menü açmaz (ekleme menüsü kaldırıldı)
        assertTrue("ekleme menüsü yok", compose.onAllNodesWithTag("canvas_add_menu").fetchSemanticsNodes().isEmpty())
        drag(Offset(start.x + size.x * 0.2f, start.y), Offset(size.x * 0.1f, size.y * 0.1f))
        compose.waitForIdle()
        assertEquals("çizim eklenmedi", 1, canvas().strokes.size)
        assertEquals("çizim taşınmadı", drawn.points, canvas().strokes.single().points)
        tap(Offset(start.x + size.x * 0.3f, start.y + size.y * 0.1f))
        waitForText("1 eleman seçili")
        tap(Offset(size.x * 0.5f, size.y * 0.15f))
        waitFor { compose.onAllNodesWithText("eleman seçili", substring = true).fetchSemanticsNodes().isEmpty() }
        // Sürükleme kağıdı kaydırdı: boş yere çift dokununca kağıt yeniden sığar (yakınlık %100)
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            click(Offset(size.x * 0.5f, size.y * 0.15f))
            advanceEventTime(120)
            click(Offset(size.x * 0.5f, size.y * 0.15f))
        }
        waitFor { zoomPercent() == 100 }

        // Çiz'e dokununca çizime dönülür: kalem çalışır
        mode("Çiz")
        waitFor { compose.onAllNodesWithText("Elemana dokunarak seç", substring = true).fetchSemanticsNodes().isEmpty() }
        drag(Offset(size.x * 0.25f, size.y * 0.6f), Offset(size.x * 0.3f, 0f))
        waitFor { canvas().strokes.size == 2 }
    }
}

/**
 * Oklar seçilen adımın istenen tarafına bağlanır: "Ok ekle"de adımın kenarına dokunmak o tarafı seçer, odaktaki
 * adımın tutamağından sürüklemek de oku çeker; ok panelinden taraflar değiştirilir ve yönü çevrilir.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V12DiagramSidesTest : EditorTestBase() {

    @Test
    fun arrowsAttachToTheChosenSides() {
        createNotesCanvasAndOpen("Taraflar")
        addMenu("Diyagram ekle")
        waitForText("Diyagram Türü")
        compose.onNodeWithText("Akış Diyagramı").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Diyagram: Akış Diyagramı")
        val size = canvasSize()
        val screen = Rect(size.x * 0.1f, size.y * 0.1f, size.x * 0.9f, size.y * 0.7f)
        drag(screen.topLeft, Offset(screen.width, screen.height))
        waitFor { canvas().strokes.isNotEmpty() }
        val placed = canvas().strokes.single()
        val box = Rect(placed.points.minOf { it.x }, placed.points.minOf { it.y }, placed.points.maxOf { it.x }, placed.points.maxOf { it.y })
        // v47 menü satırı değişince kâğıt yeniden sığdırılır: konum her seferinde görünümden hesaplanır
        fun toScreen(c: Offset) = fittedScreen(c)
        fun spec() = canvas().strokes.single().diagram!!
        /** Adımın içinde (0..1 oranında) bir noktanın ekran konumu. */
        fun at(index: Int, fx: Float, fy: Float): Offset {
            val stroke = canvas().strokes.single()
            val n = stroke.diagram!!.nodes[index]
            return toScreen(DiagramRenderer.toCanvas(stroke, n.x + n.w * fx, n.y + n.h * fy)!!)
        }
        fun port(index: Int, side: DiagramSide): Offset {
            val stroke = canvas().strokes.single()
            return toScreen(DiagramRenderer.portPoint(stroke, stroke.diagram!!.nodes[index], side)!!)
        }

        editButton("Diyagramı düzenle")
        waitForText("Adım ekle")
        val ids = spec().nodes.map { it.id }

        // 1) Ok ekle: kaynağın sağ kenarına, hedefin sol kenarına dokun
        compose.onNode(hasText("Ok ekle") and hasClickAction()).performClick()
        waitForText("Okun başlayacağı adımın", substring = true)
        tap(at(2, 0.93f, 0.5f))
        waitForText("Okun gideceği adımın", substring = true)
        tap(at(0, 0.07f, 0.5f))
        waitFor { spec().edges.size == 4 }
        spec().edges.last().let {
            assertEquals(ids[2], it.from); assertEquals(ids[0], it.to)
            assertEquals(DiagramSide.RIGHT, it.fromSide); assertEquals(DiagramSide.LEFT, it.toSide)
        }

        // 2) Yeni ok odakta: panelden giriş tarafı değişir, yönü çevrilir
        waitFor { compose.onAllNodesWithTag("diagram_focus_panel").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Giriş: Üst").performClick()
        waitFor { spec().edges.last().toSide == DiagramSide.TOP }
        compose.onNodeWithContentDescription("Yönü çevir").performClick()
        waitFor { spec().edges.last().from == ids[0] }
        spec().edges.last().let {
            assertEquals(ids[2], it.to)
            assertEquals("taraflar da yer değiştirdi", DiagramSide.TOP, it.fromSide)
            assertEquals(DiagramSide.RIGHT, it.toSide)
        }
        // Geri al / yinele ok yönünde de çalışır
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { spec().edges.last().from == ids[2] }
        compose.onNodeWithContentDescription("Yinele").performScrollTo().performClick()
        waitFor { spec().edges.last().from == ids[0] }

        // 3) Adımın tutamağından sürükleyerek: Adım'ın altından Bitir'in üstüne
        tap(at(1, 0.5f, 0.5f))
        waitFor { compose.onAllNodesWithTag("diagram_focus_panel").fetchSemanticsNodes().isNotEmpty() }
        val from = port(1, DiagramSide.BOTTOM)
        val to = at(3, 0.5f, 0.08f)
        drag(from, to - from, steps = 30)
        waitFor { spec().edges.size == 5 }
        spec().edges.last().let {
            assertEquals(ids[1], it.from); assertEquals(ids[3], it.to)
            assertEquals(DiagramSide.BOTTOM, it.fromSide); assertEquals(DiagramSide.TOP, it.toSide)
        }

        // 4) Hedefin ortasına dokunmak otomatik tarafı verir
        tap(at(3, 0.5f, 0.5f))
        waitFor { compose.onAllNodesWithTag("diagram_focus_panel").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Ok ekle").performClick()
        waitForText("Okun gideceği adımın", substring = true)
        tap(at(1, 0.5f, 0.5f))
        waitFor { spec().edges.size == 6 }
        spec().edges.last().let {
            assertEquals(ids[3], it.from); assertEquals(ids[1], it.to)
            assertEquals(DiagramSide.AUTO, it.toSide)
        }
        assertEquals("hâlâ tek eleman", 1, canvas().strokes.size)
    }
}
