package com.karapp.drawguidance

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BalloonStyle
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.FontGroup
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.data.model.TextBox
import com.karapp.drawguidance.data.model.TextEffects
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.drawing.BalloonRenderer
import com.karapp.drawguidance.ui.drawing.FontRegistry
import com.karapp.drawguidance.ui.drawing.TailDirection
import com.karapp.drawguidance.ui.drawing.TextRenderer
import com.karapp.drawguidance.ui.screens.canvasedit.state.BalloonTextOptions
import com.karapp.drawguidance.ui.screens.canvasedit.state.ComicController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * v36 (8.4) kuyruk yönü yardımcıları: her yön balonun doğru tarafında, uzaklık ve eğrilik geri okunur; birden çok kuyruk
 * kenar boyunca yayılır; telefon mesajı ve serbest yazı biçimleri çizilir (serbest yazının balonu ve kuyruğu yok).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V36BalloonLogicTest {
    @org.junit.After
    fun tearDown() = org.koin.core.context.stopKoin()

    @Test
    fun tailDirectionsRoundTrip() {
        for (dir in TailDirection.entries) {
            val tails = BalloonRenderer.tailsToward(dir, 1, 0.9f, 0.4f)
            val t = tails.single()
            when (dir) {
                TailDirection.BOTTOM_LEFT, TailDirection.BOTTOM_RIGHT -> assertTrue(dir.name, t.y > 1f)
                TailDirection.TOP_LEFT, TailDirection.TOP_RIGHT -> assertTrue(dir.name, t.y < 0f)
                TailDirection.LEFT -> assertTrue(dir.name, t.x < 0f)
                TailDirection.RIGHT -> assertTrue(dir.name, t.x > 1f)
            }
            val (d, length, curve) = BalloonRenderer.tailState(com.karapp.drawguidance.data.model.BalloonSpec(tails = tails))
            assertEquals(dir, d)
            assertEquals(0.9f, length, 1e-4f)
            assertEquals(0.4f, curve, 1e-4f)
        }
        val three = BalloonRenderer.tailsToward(TailDirection.BOTTOM_LEFT, 3, 0.5f, 0.3f)
        assertEquals(3, three.size)
        assertTrue(three.zipWithNext().all { (a, b) -> b.x > a.x })
        assertTrue(BalloonRenderer.tailsToward(TailDirection.RIGHT, 0, 0.5f, 0.3f).isEmpty())
        // Uzaklık sınırlanır
        assertEquals(1f + BalloonRenderer.MAX_TAIL, BalloonRenderer.tailsToward(TailDirection.BOTTOM_LEFT, 1, 9f, 0f).single().y, 1e-4f)
    }

    @Test
    fun phoneAndFreeStylesDraw() {
        fun render(style: BalloonStyle): Bitmap {
            val bmp = Bitmap.createBitmap(220, 160, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            c.translate(30f, 20f)
            val b = com.karapp.drawguidance.data.model.BalloonSpec(
                style = style, tails = if (style == BalloonStyle.FREE) emptyList() else BalloonRenderer.tailsToward(TailDirection.BOTTOM_RIGHT, 1, 0.3f, 0.6f),
                fillHex = ComicController.PHONE_FILL, strokeHex = ComicController.PHONE_BORDER,
            )
            BalloonRenderer.draw(c, b, 160f, 80f, 24f, 1f, false)
            return bmp
        }
        val phone = render(BalloonStyle.PHONE)
        // Ortası telefon mesajı mavisi
        assertEquals(android.graphics.Color.parseColor(ComicController.PHONE_FILL), phone.getPixel(110, 60))
        val free = render(BalloonStyle.FREE)
        var painted = 0
        for (y in 0 until free.height) for (x in 0 until free.width) if (free.getPixel(x, y) ushr 24 != 0) painted++
        assertEquals("serbest yazıda balon çizilmez", 0, painted)
    }

    @Test
    fun balloonFontsHaveTurkishLetters() {
        val letters = "ÇĞİÖŞÜçğıöşü"
        val fonts = TextFont.entries.filter { it.group == FontGroup.COMIC } + listOf(TextFont.PATRICK_HAND, TextFont.KALAM, TextFont.CAVEAT, TextFont.TYPEWRITER)
        for (f in fonts) {
            val paint = Paint().apply { typeface = FontRegistry.typeface(f) }
            val missing = letters.filter { !paint.hasGlyph(it.toString()) }
            assertTrue("${f.name} eksik harfler: $missing", missing.isEmpty())
        }
    }

    @Test
    fun capsFollowTurkishRules() {
        // Büyük harfli yazı (SFX) Türkçe kurallarıyla: "iğne" → "İĞNE" (noktalı İ), "ışık" → "IŞIK"
        fun draw(content: String, caps: Boolean): Bitmap {
            val spec = TextSpec(content = content, font = TextFont.BANGERS, box = TextBox(effects = TextEffects(caps = caps)))
            val quad = TextRenderer.quadAt(spec, 64f, 4f, 4f)
            val bmp = Bitmap.createBitmap(420, 120, Bitmap.Config.ARGB_8888)
            TextRenderer.draw(android.graphics.Canvas(bmp), Stroke("t", quad, "#111111", BrushType.TEXT, 64f, 1f, 0, text = spec), 0xFF111111.toInt())
            return bmp
        }
        assertTrue(draw("iğne", true).sameAs(draw("İĞNE", false)))
        assertFalse(draw("iğne", true).sameAs(draw("IĞNE", false)))
        assertTrue(draw("ışık", true).sameAs(draw("IŞIK", false)))
    }
}

/**
 * v36 (8.4) konuşma balonu penceresi: Tür, Yazı ve Kuyruk sekmeleri. Normal, düşünce, bağırma, fısıltı, anlatıcı kutusu,
 * telefon mesajı, serbest yazı ve SFX yazısı; yazı (Türkçe harflerle), yazı tipi, boyut, renk, hizalama ve kuyruk
 * yönü/uzunluğu/eğriliği yeni balonda ve seçili balonda.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V36BalloonTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun openComic(name: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = CanvasKind.COMIC) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun strokes(): List<Stroke> = vm().canvas.value?.strokes.orEmpty()
    private fun balloons() = strokes().filter { it.text?.box?.balloon != null }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun slide(container: String, value: Float) {
        val node = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress) and hasAnyAncestor(hasTestTag(container)))
        node.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        compose.waitForIdle()
    }

    private fun swatch(container: String, hex: String) {
        compose.onNode(hasContentDescription(hex) and hasAnyAncestor(hasTestTag(container))).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun openSheet() {
        addMenu("Konuşma balonu")
        waitFor { exists("balloon_sheet") }
    }

    @Test
    fun newBalloonWithTurkishTextFontSizeColorAlignAndTail() {
        openComic("Balon yazısı")
        openSheet()
        // Tür sekmesi: istenen sekiz tür ve diğer biçimler
        for (t in listOf("SPEECH", "THOUGHT", "SHOUT", "WHISPER", "NARRATION", "PHONE", "FREE", "SFX")) {
            compose.onNodeWithTag("balloon_$t").performScrollTo()
            assertTrue(t, exists("balloon_$t"))
        }
        compose.onNodeWithTag("balloon_ELECTRIC").performScrollTo()
        shot("v36-balloon-types")
        // Yazı sekmesi
        click("balloon_tab_text")
        waitFor { exists("balloon_text") }
        val text = "Çığlık! İĞÜŞÖÇ ışık"
        compose.onNodeWithTag("balloon_text").performScrollTo().performTextInput(text)
        compose.waitForIdle()
        waitFor { compose.onAllNodes(hasTestTag("balloon_preview_text") and hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        click("balloon_font_BANGERS")
        slide("balloon_text_size", 60f)
        swatch("balloon_text_color", "#E53935")
        click("balloon_align_LEFT")
        // Kuyruk sekmesi: üst sağ, uzun, ters eğri
        click("balloon_tab_tail")
        waitFor { exists("balloon_tail_TOP_RIGHT") }
        click("balloon_tail_TOP_RIGHT")
        slide("balloon_tail_length", 1.2f)
        slide("balloon_tail_curve", -0.5f)
        shot("v36-balloon-tail")
        click("balloon_pick_SHOUT")
        click("balloon_insert")
        waitFor { balloons().size == 1 }
        waitFor { !exists("balloon_sheet") }
        val s = balloons().single()
        val spec = s.text!!
        assertEquals(text, spec.content)
        assertEquals(TextFont.BANGERS, spec.font)
        assertEquals(60f, s.sizePx, 0.01f)
        assertEquals("#E53935", s.colorHex)
        assertEquals(TextAlignKind.LEFT, spec.align)
        val b = spec.box!!.balloon!!
        assertEquals(BalloonStyle.SHOUT, b.style)
        val (dir, length, curve) = BalloonRenderer.tailState(b)
        assertEquals(TailDirection.TOP_RIGHT, dir)
        assertEquals(1.2f, length, 0.01f)
        assertEquals(-0.5f, curve, 0.01f)
        // Yazı pencerede yazıldı: tuvalde yazma alanı açılmaz, balon seçili
        assertFalse(exists("inline_text_editor"))
        assertEquals(setOf(s.id), vm().selectedIds.value)
        settleFor(300)
        shotAll("v36-balloon-inserted")
    }

    @Test
    fun phoneFreeAndSfxTypes() {
        openComic("Balon türleri")
        // Telefon mesajı: mavi balon, beyaz yazı, kısa kuyruk sağ altta
        openSheet()
        click("balloon_tab_text")
        compose.onNodeWithTag("balloon_text").performScrollTo().performTextInput("Neredesin? Geliyorum")
        click("balloon_pick_PHONE")
        click("balloon_insert")
        waitFor { balloons().size == 1 }
        val phone = balloons().single()
        assertEquals(BalloonStyle.PHONE, phone.text!!.box!!.balloon!!.style)
        assertEquals(ComicController.PHONE_FILL, phone.text!!.box!!.balloon!!.fillHex)
        assertEquals("#FFFFFF", phone.colorHex)
        assertEquals(TailDirection.BOTTOM_RIGHT, BalloonRenderer.tailState(phone.text!!.box!!.balloon!!).first)
        // Serbest yazı: balon ve kuyruk yok
        openSheet()
        click("balloon_tab_text")
        compose.onNodeWithTag("balloon_text").performScrollTo().performTextInput("Bu arada şehirde…")
        click("balloon_pick_FREE")
        click("balloon_tab_tail")
        waitFor { compose.onAllNodes(hasText("Serbest yazının kuyruğu yoktur.")).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(exists("balloon_tail_LEFT"))
        click("balloon_insert")
        waitFor { balloons().size == 2 }
        val free = balloons().first { it.id != phone.id }
        assertEquals(BalloonStyle.FREE, free.text!!.box!!.balloon!!.style)
        assertTrue(free.text!!.box!!.balloon!!.tails.isEmpty())
        // SFX yazısı: balon değil, büyük harfli efekt yazısı (Türkçe büyük harf)
        openSheet()
        click("balloon_tab_text")
        compose.onNodeWithTag("balloon_text").performScrollTo().performTextInput("çatırtı!")
        swatch("balloon_text_color", "#43A047")
        click("balloon_pick_SFX")
        click("balloon_insert")
        waitFor { strokes().count { it.brushType == BrushType.TEXT } == 3 }
        val sfx = strokes().last { it.brushType == BrushType.TEXT }
        assertEquals(null, sfx.text!!.box?.balloon)
        assertEquals("çatırtı!", sfx.text!!.content)
        assertTrue(sfx.text!!.box!!.effects!!.caps)
        assertEquals(listOf(ComicController.lighter("#43A047"), "#43A047"), sfx.text!!.box!!.effects!!.gradient!!.colors)
        assertEquals(TextFont.BANGERS, sfx.text!!.font)
        // Tür sekmesinde SFX'e dokununca da eklenir (yazı yoksa BAM!)
        openSheet()
        click("balloon_SFX")
        waitFor { strokes().count { it.brushType == BrushType.TEXT } == 4 }
        assertEquals("BAM!", strokes().last { it.brushType == BrushType.TEXT }.text!!.content)
        settleFor(300)
        shotAll("v36-balloon-phone-free-sfx")
    }

    @Test
    fun editSelectedBalloonTextAndTail() {
        openComic("Balon düzenleme")
        compose.runOnIdle { vm().insertBalloon(BalloonStyle.SPEECH, 1, BalloonTextOptions(content = "Merhaba dünya")) }
        waitFor { balloons().size == 1 }
        val id = balloons().single().id
        val before = balloons().single()
        // Seçimdeki "Balon" düğmesi düzenleme penceresini açar
        val edit = hasContentDescription("Balon") or hasText("Balon")
        waitFor { compose.onAllNodes(edit).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(edit)[0].performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("balloon_sheet") }
        // SFX düzenlemede yok; tür değişir
        assertFalse(exists("balloon_SFX"))
        click("balloon_THOUGHT")
        waitFor { balloons().single().text!!.box!!.balloon!!.style == BalloonStyle.THOUGHT }
        // Yazı sekmesi seçili balonun yazısıyla açılır, değişiklik hemen uygulanır
        click("balloon_tab_text")
        waitFor { compose.onAllNodes(hasTestTag("balloon_text") and hasText("Merhaba dünya")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("balloon_text").performScrollTo().performTextReplacement("Göğüs gerdi: ŞİMDİ!")
        waitFor { balloons().single().text!!.content == "Göğüs gerdi: ŞİMDİ!" }
        click("balloon_font_BOOGALOO")
        waitFor { balloons().single().text!!.font == TextFont.BOOGALOO }
        val bigger = (before.sizePx * 2f).roundToInt().toFloat()
        slide("balloon_text_size", bigger)
        waitFor { balloons().single().sizePx == bigger }
        // Yazı büyüyünce balonun eni de aynı oranda
        assertEquals(before.text!!.box!!.width!! * bigger / before.sizePx, balloons().single().text!!.box!!.width!!, 1f)
        swatch("balloon_text_color", "#1E88E5")
        waitFor { balloons().single().colorHex == "#1E88E5" }
        click("balloon_align_RIGHT")
        waitFor { balloons().single().text!!.align == TextAlignKind.RIGHT }
        // Kuyruk sekmesi: sağa, uzaklık ve eğrilik
        click("balloon_tab_tail")
        click("balloon_tail_RIGHT")
        waitFor { BalloonRenderer.tailState(balloons().single().text!!.box!!.balloon!!).first == TailDirection.RIGHT }
        slide("balloon_tail_length", 0.4f)
        waitFor { kotlin.math.abs(BalloonRenderer.tailState(balloons().single().text!!.box!!.balloon!!).second - 0.4f) < 0.006f }
        assertTrue(balloons().single().text!!.box!!.balloon!!.tails.single().x > 1f)
        assertEquals(id, balloons().single().id)
        settleFor(300)
        shotAll("v36-balloon-edited")
        // Yazı değişiklikleri tek adımda geri alınır (her alan kendi adımı)
        val tailStep = balloons().single()
        compose.runOnIdle { vm().undo() }
        waitFor { balloons().single() != tailStep }
    }
}
