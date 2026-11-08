package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.TubeMaterial
import com.karapp.drawguidance.data.settings.EditorPrefsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

abstract class V20EditorBase : V19EditorBase() {

    protected fun prefs(): EditorPrefsStore = GlobalContext.get().get()

    /** Titrek bir çember (çizim önerisi: Daire). */
    protected fun wobblyCircle(c: Offset, r: Float, seed: Long) {
        val rnd = java.util.Random(seed)
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(Offset(c.x + r, c.y))
            for (i in 1..72) {
                val a = i / 72.0 * 2 * PI * 1.04
                val rr = r * (1f + (rnd.nextFloat() - 0.5f) * 0.08f)
                moveTo(Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat()))
            }
            up()
        }
        compose.waitForIdle()
    }

    protected fun chipShown() = compose.onAllNodesWithTag("handwriting_chip").fetchSemanticsNodes().isNotEmpty()

    /** Üç parmakla yatay kaydırma ([dx] < 0 sola). */
    protected fun threeFingerSwipe(dx: Float, dy: Float = 0f) {
        val size = canvasSize()
        val c = Offset(size.x * 0.5f, size.y * 0.5f)
        compose.waitForIdle()
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(0, c + Offset(-90f, -40f))
            down(1, c)
            down(2, c + Offset(90f, 40f))
            val steps = 12
            repeat(steps) {
                val d = Offset(dx / steps, dy / steps)
                updatePointerBy(0, d); updatePointerBy(1, d); updatePointerBy(2, d)
                move()
            }
            up(0); up(1); up(2)
        }
        compose.waitForIdle()
    }

    protected fun openBrushPanel() {
        openProperties()
        waitFor { compose.onAllNodesWithTag("brush_tuning_section").fetchSemanticsNodes().isNotEmpty() }
    }

    protected fun closePanel() {
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        waitFor { compose.onAllNodesWithTag("brush_tuning_section").fetchSemanticsNodes().isEmpty() }
    }

    protected fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    protected fun setSlider(tag: String, value: Float) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        compose.waitForIdle()
    }

    /** Yatay, titrek (zikzaklı) bir çizgi çizer ve eklenen izi döndürür. */
    protected fun jitteryLine(y: Float, amp: Float): Stroke {
        val before = canvas().strokes.size
        val size = canvasSize()
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(Offset(size.x * 0.15f, y))
            for (i in 1..60) moveTo(Offset(size.x * 0.15f + i * size.x * 0.011f, y + if (i % 2 == 0) amp else -amp))
            up()
        }
        waitFor { canvas().strokes.size == before + 1 }
        return canvas().strokes.last()
    }

    /** İzin, uçlarını birleştiren doğrudan dik sapmalarının standart sapması. */
    protected fun wobble(s: Stroke): Float {
        val a = s.points.first(); val b = s.points.last()
        val dx = b.x - a.x; val dy = b.y - a.y
        val len = sqrt(dx * dx + dy * dy)
        // Baştaki ve sondaki (yetişme) kısımlar hariç
        val mid = s.points.subList(s.points.size / 5, s.points.size * 4 / 5)
        val d = mid.map { ((it.x - a.x) * dy - (it.y - a.y) * dx) / len }
        val mean = d.average()
        return sqrt(d.map { (it - mean) * (it - mean) }.average()).toFloat()
    }
}

/**
 * v20 çizim önerileri kapatılabilir: öneri kartındaki "Önerileri kapat" ile kapanır (bildirim, sonraki çizimde kart
 * yok, ayar kalıcı), ⋮ menüsündeki "Çizim önerileri" ile yeniden açılır. v38 tek çizgiye öneri olmadığından yan yana
 * iki çember çizilir (çizgi önerisi; kendi kapatma düğmesi ve menü satırı). v40 v39 büyük tek çembere de çizgi önerisi
 * gelir; ikinci çemberle öneri iki çizgiyi birlikte kapsar.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V20SuggestionsOffTest : V20EditorBase() {
    @Test
    fun turnOffFromChipAndBackOnFromMenu() {
        createCanvasAndOpen("Öneriler")
        val size = canvasSize()
        val r = size.x * 0.18f
        wobblyCircle(Offset(size.x * 0.25f, size.y * 0.2f), r, 3)
        waitFor { canvas().strokes.size == 1 }
        advanceMain(900)
        // v38 tek çizgiye öneri yoktu; v40 v39 büyük tek çizgiye çizgi önerisi (SuggestRules.bigLine): tuvalin üçte biri
        // çapındaki tek çember tek başına çizgi önerisi alır (hesap arka planda; önceki sabit bekleme yarışlıydı)
        waitFor(15_000) { exists("line_suggest_chip") && compose.onAllNodesWithContentDescription("Düzeltmeyi uygula: 1 çizgi birden").fetchSemanticsNodes().isNotEmpty() }
        wobblyCircle(Offset(size.x * 0.25f + r * 2.1f, size.y * 0.2f), r, 4)
        waitFor { canvas().strokes.size == 2 }
        advanceMain(900)
        // Yan yana iki çember birlikte önerilir
        waitFor(15_000) { chipShown() && exists("line_suggest_chip") && compose.onAllNodesWithContentDescription("Düzeltmeyi uygula: 2 çizgi birden").fetchSemanticsNodes().isNotEmpty() }
        // Kartta kapatma düğmesi (v23 türler ayrı kapanır; v38 çizgi önerisi)
        compose.onNodeWithContentDescription("Çizgi önerilerini kapat").performClick()
        waitForText("Çizgi önerileri kapatıldı", substring = true)
        waitFor { !chipShown() }
        assertFalse(prefs().lineSuggest.value)
        assertTrue(prefs().handwriting.value)
        assertTrue(prefs().textSuggest.value)
        assertTrue(prefs().shapeSuggest.value)
        assertEquals(2, canvas().strokes.size)

        // Kapalıyken yeni iki çizimde çizgi önerisi yok
        wobblyCircle(Offset(size.x * 0.25f, size.y * 0.5f), r, 5)
        wobblyCircle(Offset(size.x * 0.25f + r * 2.1f, size.y * 0.5f), r, 6)
        waitFor { canvas().strokes.size == 4 }
        advanceMain(900)
        repeat(10) { advanceMain(200); assertFalse("öneri kapalı", exists("line_suggest_chip")) }

        // ⋮ menüsünden yeniden açılır (işaret kutusu; menü açık kalır, geri hareketiyle kapanır)
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        waitFor { exists("suggestions_menu_item") }
        compose.onNodeWithTag("line_suggest_menu_item").performClick()
        waitFor { prefs().lineSuggest.value }
        SystemBack.press()
        waitFor { !exists("suggestions_menu_item") }
        wobblyCircle(Offset(size.x * 0.25f, size.y * 0.8f), r, 7)
        wobblyCircle(Offset(size.x * 0.25f + r * 2.1f, size.y * 0.8f), r, 8)
        waitFor { canvas().strokes.size == 6 }
        advanceMain(900)
        waitFor(15_000) { exists("line_suggest_chip") }
    }
}

/** v20 üç parmakla sola kaydırma geri alır, sağa kaydırma yineler; tuval kaymaz, yeni çizgi oluşmaz. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V20ThreeFingerSwipeTest : V20EditorBase() {
    @Test
    fun swipeLeftUndoesRightRedoes() {
        createCanvasAndOpen("Üç parmak")
        val size = canvasSize()
        drag(Offset(size.x * 0.2f, size.y * 0.2f), Offset(size.x * 0.5f, 0f))
        drag(Offset(size.x * 0.2f, size.y * 0.8f), Offset(size.x * 0.5f, 0f))
        waitFor { canvas().strokes.size == 2 }
        val zoom = zoomPercent()

        threeFingerSwipe(-420f)
        waitFor { canvas().strokes.size == 1 }
        waitForText("Geri alındı")
        threeFingerSwipe(-420f)
        waitFor { canvas().strokes.isEmpty() }
        threeFingerSwipe(-420f)
        waitForText("Geri alınacak adım yok")

        threeFingerSwipe(420f)
        waitFor { canvas().strokes.size == 1 }
        waitForText("Yinelendi")
        threeFingerSwipe(420f)
        waitFor { canvas().strokes.size == 2 }
        threeFingerSwipe(420f)
        waitForText("Yinelenecek adım yok")
        assertEquals(2, canvas().strokes.size)

        // Dikey üç parmak hareketi bir şey yapmaz; yakınlaştırma değişmedi
        threeFingerSwipe(40f, 420f)
        compose.waitForIdle()
        assertEquals(2, canvas().strokes.size)
        assertEquals(zoom, zoomPercent())
    }
}

/**
 * v20 fırça paneli: boyut yazılarak 380 px'e kadar (geçersiz değer uygulanamaz), aralık yalnız damgalı fırçalarda
 * (izde saklanır), sabitleme açılınca gücü görünür ve titrek çizgi düzleşir; ayarlar fırça başına.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V20BrushPanelTest : V20EditorBase() {
    @Test
    fun sizeSpacingAndStabilizer() {
        createCanvasAndOpen("Fırça paneli")
        waitForDescription("Seçili fırça: Kalem")
        val size = canvasSize()

        // Titrek çizgi (sabitleme kapalı)
        val raw = jitteryLine(size.y * 0.2f, 14f)

        openBrushPanel()
        // Boyut yazılır
        compose.onNodeWithTag("brush_size_value").performScrollTo().performClick()
        waitFor { exists("brush_size_field") }
        compose.onNodeWithTag("brush_size_field").performTextClearance()
        compose.onNodeWithTag("brush_size_field").performTextInput("300")
        compose.onNodeWithTag("brush_size_apply").assertIsEnabled().performClick()
        waitFor { !exists("brush_size_field") }
        compose.onNodeWithTag("brush_size_value").assertTextEquals("300 px")
        // 380'den büyük uygulanamaz
        compose.onNodeWithTag("brush_size_value").performClick()
        waitFor { exists("brush_size_field") }
        compose.onNodeWithTag("brush_size_field").performTextClearance()
        compose.onNodeWithTag("brush_size_field").performTextInput("999")
        compose.onNodeWithTag("brush_size_apply").assertIsNotEnabled()
        compose.onNode(hasText("İptal") and hasAnyAncestor(hasTestTag("brush_size_sheet"))).performClick()
        waitFor { !exists("brush_size_field") }
        compose.onNodeWithTag("brush_size_value").assertTextEquals("300 px")

        // Kalem damgalı değil: aralık yok. Sabitleme açılınca gücü görünür
        assertFalse(exists("brush_spacing"))
        assertFalse(exists("brush_stabilizer_strength"))
        compose.onNodeWithTag("brush_stabilizer").performScrollTo().performClick()
        waitFor { exists("brush_stabilizer_strength") }
        setSlider("brush_stabilizer_strength", 1f)
        closePanel()

        val smooth = jitteryLine(size.y * 0.45f, 14f)
        assertEquals(300f, smooth.sizePx, 0.01f)
        val w0 = wobble(raw); val w1 = wobble(smooth)
        assertTrue("sabitleme titremeyi azaltır: $w0 → $w1", w1 < w0 * 0.4f)

        // Damgalı fırça: aralık kaydırıcısı var, izde saklanır; Kalem'in ayarları ona geçmez
        pickBrush("Sert Fırça")
        openBrushPanel()
        assertTrue(exists("brush_spacing"))
        compose.onNodeWithTag("brush_stabilizer").performScrollTo()
        assertFalse("sabitleme fırça başına", exists("brush_stabilizer_strength"))
        setSlider("brush_spacing", 1.5f)
        waitFor { exists("brush_spacing_default") }
        closePanel()
        drag(Offset(size.x * 0.2f, size.y * 0.7f), Offset(size.x * 0.5f, 0f))
        waitFor { canvas().strokes.size == 3 }
        val dabbed = canvas().strokes.last()
        assertEquals(BrushType.HARD_ROUND, dabbed.brushType)
        assertEquals(1.5f, dabbed.params?.spacing ?: 0f, 0.001f)

        // Kalem'e dönünce boyut ve sabitleme hatırlanır
        pickBrush("Kalem")
        openBrushPanel()
        compose.onNodeWithTag("brush_size_value").assertTextEquals("300 px")
        compose.onNodeWithTag("brush_stabilizer").performScrollTo()
        assertTrue(exists("brush_stabilizer_strength"))
        assertTrue(prefs().canvas(canvas().localId).brushTunings["PENCIL"]?.stabilizer == true)
    }
}

/** v20 3B fırça ayarları: ışık yönü, malzeme (Metal) ve gölge izde saklanır; fırça değişip dönünce hatırlanır. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V20Brush3DPanelTest : V20EditorBase() {
    @Test
    fun tubeSettingsPersistPerBrush() {
        createCanvasAndOpen("3B")
        val size = canvasSize()
        pickBrush("3B Fırça")
        openBrushPanel()
        assertTrue(exists("brush_3d_light"))
        // Diğer fırçalarda 3B ayarı yok (Kalem'de denendi aşağıda)
        compose.onNode(hasText("Metal") and hasAnyAncestor(hasTestTag("brush_tuning_section"))).performScrollTo().performClick()
        setSlider("brush_3d_light", 90f)
        setSlider("brush_3d_shadow", 0f)
        assertFalse("gölge yokken yumuşaklık gizli", exists("brush_3d_shadow_soft"))
        closePanel()
        drag(Offset(size.x * 0.2f, size.y * 0.3f), Offset(size.x * 0.5f, size.y * 0.1f))
        waitFor { canvas().strokes.size == 1 }
        val tube = canvas().strokes.single().params?.tube
        assertNotNull(tube)
        assertEquals(TubeMaterial.METAL, tube!!.material)
        assertEquals(90f, tube.lightAngle, 0.5f)
        assertEquals(0f, tube.shadow, 0.001f)

        pickBrush("Kalem")
        openBrushPanel()
        assertFalse(exists("brush_3d_light"))
        closePanel()
        pickBrush("3B Fırça")
        drag(Offset(size.x * 0.2f, size.y * 0.6f), Offset(size.x * 0.5f, 0f))
        waitFor { canvas().strokes.size == 2 }
        assertEquals(TubeMaterial.METAL, canvas().strokes.last().params?.tube?.material)
        assertEquals(TubeMaterial.METAL, prefs().canvas(canvas().localId).brushTunings["BRUSH_3D"]?.tube?.material)

        // Sıfırlama: varsayılan (plastik) ve izde ayar yok
        openBrushPanel()
        compose.onNodeWithTag("brush_tuning_reset").performScrollTo().performClick()
        waitFor { !exists("brush_tuning_reset") }
        closePanel()
        drag(Offset(size.x * 0.2f, size.y * 0.85f), Offset(size.x * 0.5f, 0f))
        waitFor { canvas().strokes.size == 3 }
        assertEquals(null, canvas().strokes.last().params)
    }
}
