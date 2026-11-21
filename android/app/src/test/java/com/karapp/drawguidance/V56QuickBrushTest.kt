package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.EyedropLoupe
import com.karapp.drawguidance.data.model.QuickBrushLayout
import com.karapp.drawguidance.data.model.QuickBrushLists
import com.karapp.drawguidance.data.model.QuickCorner
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick
import com.karapp.drawguidance.ui.screens.canvasedit.state.QuickBrushStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v56 aşama 4 saf kurallar: son kullanılanlar, favoriler, mod başına kayıt, yerleşim, renk dairesi konumu. */
class V56QuickBrushRulesTest {
    @Test fun recentKeepsSixMostRecentWithoutEraser() {
        var l = QuickBrushLists()
        listOf(BrushType.PENCIL, BrushType.MARKER, BrushType.INK_PEN, BrushType.PASTEL, BrushType.CHARCOAL, BrushType.OIL_PAINT, BrushType.AIRBRUSH)
            .forEach { l = l.used(it) }
        assertEquals(6, l.recent.size)
        assertEquals(BrushType.AIRBRUSH, l.recent.first())
        assertFalse(BrushType.PENCIL in l.recent)
        // Yeniden kullanılan başa gelir, çift olmaz; silgi ve resim girmez
        l = l.used(BrushType.PASTEL).used(BrushType.ERASER).used(BrushType.IMAGE)
        assertEquals(BrushType.PASTEL, l.recent.first())
        assertEquals(6, l.recent.size)
        assertFalse(BrushType.ERASER in l.recent)
        // İki parmak: önceki fırça
        assertEquals(BrushType.AIRBRUSH, l.previous(BrushType.PASTEL))
    }

    @Test fun favoritesPinUnpinMaxSix() {
        var l = QuickBrushLists()
        val six = listOf(BrushType.PENCIL, BrushType.MARKER, BrushType.INK_PEN, BrushType.PASTEL, BrushType.CHARCOAL, BrushType.OIL_PAINT)
        six.forEach { l = l.toggleFavorite(it).first }
        assertEquals(six, l.favorites)
        val (same, ok) = l.toggleFavorite(BrushType.AIRBRUSH)
        assertFalse("en çok 6", ok)
        assertEquals(six, same.favorites)
        l = l.toggleFavorite(BrushType.MARKER).first
        assertFalse(BrushType.MARKER in l.favorites)
        // Şerit: önce favoriler, sonra favori olmayan son kullanılanlar
        l = l.used(BrushType.MARKER).used(BrushType.PENCIL)
        assertEquals(l.favorites + BrushType.MARKER, l.strip())
    }

    @Test fun encodeDecodeAndPerModeStore() {
        val l = QuickBrushLists(listOf(BrushType.PENCIL, BrushType.MARKER), listOf(BrushType.INK_PEN))
        assertEquals("recent=PENCIL,MARKER;fav=INK_PEN", l.encode())
        assertEquals(l, QuickBrushLists.decode(l.encode()))
        assertEquals(QuickBrushLists(listOf(BrushType.PENCIL)), QuickBrushLists.decode("recent=PENCIL,NOPE;fav="))
        assertEquals(QuickBrushLists(), QuickBrushLists.decode(null))
        // Mod başına ayrı liste
        val saved = HashMap<String, String>()
        val store = QuickBrushStore(load = { saved[it] }, save = { k, v -> saved[k] = v })
        store.bind(CanvasKind.ART); store.used(BrushType.MARKER)
        store.bind(CanvasKind.SIMPLE_ART); store.used(BrushType.PENCIL)
        assertEquals(listOf(BrushType.MARKER), QuickBrushLists.decode(saved["ART"]).recent)
        assertEquals(listOf(BrushType.PENCIL), QuickBrushLists.decode(saved["SIMPLE_ART"]).recent)
        store.bind(CanvasKind.ART)
        assertEquals(listOf(BrushType.MARKER), store.lists.value.recent)
    }

    @Test fun layoutRailInsetCornersAndRtl() {
        // Yatayda sol üstteyse menü yuvanın altından başlar; başka köşede ya da dikeyde ortalanır
        assertEquals(QuickBrushLayout.SLOT, QuickBrushLayout.railTopInset(true, QuickCorner.TOP_START, rtl = false))
        assertEquals(QuickBrushLayout.SLOT, QuickBrushLayout.railTopInset(true, QuickCorner.TOP_START, rtl = true))
        assertNull(QuickBrushLayout.railTopInset(true, QuickCorner.TOP_END, rtl = false))
        assertNull(QuickBrushLayout.railTopInset(false, QuickCorner.TOP_START, rtl = false))
        // Yuva ile menü kesişmez: menü üstü ≥ düğme altı
        assertTrue(QuickBrushLayout.SLOT >= QuickBrushLayout.BUTTON + 8f)
        assertEquals(QuickBrushLayout.SLOT, QuickBrushLayout.pushBelowSlot(20f, QuickCorner.TOP_START))
        assertEquals(20f, QuickBrushLayout.pushBelowSlot(20f, QuickCorner.BOTTOM_END))
        // RTL'de mantıksal başlangıç sağ üst köşe
        assertEquals(false to false, QuickBrushLayout.physical(QuickCorner.TOP_START, rtl = false))
        assertEquals(true to false, QuickBrushLayout.physical(QuickCorner.TOP_START, rtl = true))
        assertEquals(QuickCorner.BOTTOM_END, QuickBrushLayout.nearest(900f, 700f, 1000f, 800f, rtl = false))
        assertEquals(QuickCorner.BOTTOM_START, QuickBrushLayout.nearest(900f, 700f, 1000f, 800f, rtl = true))
        // Kalem düğmeye yaklaşınca kayar
        assertTrue(QuickBrushLayout.dodge(30f, 30f, 30f, 30f, 22f) > 0f)
        assertEquals(0f, QuickBrushLayout.dodge(300f, 300f, 30f, 30f, 22f))
    }

    @Test fun loupeStaysOnScreenAndCancelsOutside() {
        // Dokunmanın 60 yukarısı; üstte yer yoksa aşağı iner; kenarda ekranda kalır
        assertEquals(200f to 140f, EyedropLoupe.center(200f, 200f, 1000f, 1000f, 40f))
        assertEquals(200f to 80f, EyedropLoupe.center(200f, 20f, 1000f, 1000f, 40f))
        assertEquals(40f to 140f, EyedropLoupe.center(5f, 200f, 1000f, 1000f, 40f))
        assertTrue(EyedropLoupe.radius(kids = true) > EyedropLoupe.radius(kids = false))
        assertTrue(EyedropLoupe.inside(10f, 10f, 100f, 100f))
        assertFalse(EyedropLoupe.inside(-1f, 10f, 100f, 100f))
    }
}

/** v56 aşama 4 arayüz: hızlı fırça düğmesi ve şeridi, renk düğmesi, renk penceresinde damlalık, canlı renk dairesi. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V56QuickBrushTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun openKind(name: String, kind: CanvasKind) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    @Test
    fun quickBrushOnlyInArtAndSimpleArt() {
        openKind("Hızlı", CanvasKind.ART)
        compose.runOnIdle { vm().setMode(EditMode.DRAW) }
        waitFor { exists("quick_brush_button") }
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        for (kind in listOf(CanvasKind.NOTES, CanvasKind.COMIC, CanvasKind.KIDS)) {
            assertFalse(kind.name, com.karapp.drawguidance.data.model.V56Flags.enabled(com.karapp.drawguidance.data.model.V56Flags.Feature.QUICK_BRUSH, kind))
        }
        assertTrue(com.karapp.drawguidance.data.model.V56Flags.enabled(com.karapp.drawguidance.data.model.V56Flags.Feature.QUICK_BRUSH, CanvasKind.SIMPLE_ART))
    }

    @Test
    fun stripSelectsPinsAndClosesWhileDrawing() {
        openKind("Şerit", CanvasKind.ART)
        compose.runOnIdle { vm().setMode(EditMode.DRAW); vm().selectBrush(BrushType.MARKER); vm().selectBrush(BrushType.PENCIL) }
        waitFor { exists("quick_brush_button") }
        compose.onNodeWithTag("quick_brush_button").performClick()
        waitFor { exists("quick_brush_strip") && exists("quick_brush_color") && exists("quick_brush_all") && exists("quick_brush_eraser") }
        compose.onNodeWithTag("quick_brush_item_MARKER").performClick()
        waitFor { vm().brushSettings.value.brushType == BrushType.MARKER }
        assertEquals(BrushType.MARKER, vm().quickBrush.lists.value.recent.first())
        // Şerit açık kalır; sabitle
        compose.runOnIdle { vm().quickBrush.toggleFavorite(BrushType.PENCIL) }
        assertTrue(BrushType.PENCIL in vm().quickBrush.lists.value.favorites)
        // Çizmeye başlayınca şerit kapanır
        val size = canvasSize()
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(Offset(size.x * 0.4f, size.y * 0.5f))
            moveTo(Offset(size.x * 0.5f, size.y * 0.55f))
            moveTo(Offset(size.x * 0.6f, size.y * 0.6f))
            up()
        }
        waitFor { !exists("quick_brush_strip") }
        // İki parmak: son iki fırça arasında geçiş
        compose.runOnIdle { assertTrue(vm().quickSwap()) }
        assertEquals(BrushType.PENCIL, vm().brushSettings.value.brushType)
    }

    @Test
    fun colorButtonOpensSheetAndEyedropperReturns() {
        openKind("Renk", CanvasKind.ART)
        compose.runOnIdle { vm().setMode(EditMode.DRAW) }
        waitFor { exists("quick_brush_button") }
        compose.onNodeWithTag("quick_brush_button").performClick()
        waitFor { exists("quick_brush_color") }
        compose.onNodeWithTag("quick_brush_color").performScrollTo().performClick()
        waitFor { exists("color_pick_sheet") && exists("color_pick_eyedropper") }
        compose.onNodeWithTag("color_pick_eyedropper").performClick()
        waitFor { !exists("color_pick_sheet") && vm().canvasPick.value == CanvasPick.EYEDROPPER }
        // Renk alınınca pencere yeniden açılır
        compose.runOnIdle { vm().eyedrop(Offset(500f, 500f)) }
        waitFor { exists("color_pick_sheet") }
    }

    @Test
    fun loupeShowsColorUnderFingerAndCancelsOutside() {
        openKind("Damla", CanvasKind.ART)
        // Kırmızı kare (kement dolgu), sonra fırça yeşil
        compose.runOnIdle {
            vm().setMode(EditMode.DRAW)
            vm().updateBrushColor("#FF0000")
            vm().lassoFill(listOf(Offset(100f, 100f), Offset(400f, 100f), Offset(400f, 400f), Offset(100f, 400f)))
            vm().updateBrushColor("#00FF00")
        }
        waitFor { vm().canvas.value?.strokes?.any { it.brushType == BrushType.LASSO_FILL } == true }
        compose.runOnIdle { vm().eyedropHover(Offset(250f, 250f)) }
        val l = vm().loupe.value
        assertNotNull(l)
        assertEquals("#FF0000", l!!.hex?.uppercase())
        assertEquals("#00FF00", l.previous.uppercase())
        waitFor { exists("eyedropper_loupe") }
        // Sayfanın dışında bırakınca renk değişmez
        compose.runOnIdle { vm().toggleCanvasPick(CanvasPick.EYEDROPPER) }
        assertNull(vm().eyedrop(Offset(-50f, 250f)))
        assertEquals("#00FF00", vm().brushSettings.value.colorHex.uppercase())
        assertNull(vm().loupe.value)
        // İçeride bırakınca fırça rengi o renk
        compose.runOnIdle { vm().toggleCanvasPick(CanvasPick.EYEDROPPER) }
        compose.runOnIdle { vm().eyedrop(Offset(250f, 250f)) }
        assertEquals("#FF0000", vm().brushSettings.value.colorHex.uppercase())
        assertTrue(compose.onAllNodesWithTag("eyedropper_loupe").fetchSemanticsNodes().size <= 1)
    }
}
