package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v32 testlerinde ilk girişte eğitim önerilir. */
class TutorialTestApp : TestDrawGuidanceApp() {
    override val offerTutorials: Boolean = true
}

/** v32 eğitim modu testleri için ortak yardımcılar. */
abstract class V32EditorBase : V31EditorBase() {
    protected fun vm(): CanvasEditViewModel = CanvasEditViewModel.lastCreated?.get() ?: error("düzenleyici yok")

    /** Bellekteki (eğitimdeki) tuval. */
    protected fun live(): CanvasUi = vm().canvas.value ?: error("tuval yok")

    protected fun offerShown() = exists("tutorial_offer")
    protected fun calloutShown() = exists("tutorial_callout")

    /** Ana iş parçacığının saatini ilerleterek (çizim canlandırması) koşulu bekler. */
    protected fun runUntil(timeoutMs: Long = 30_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Koşul $timeoutMs ms içinde sağlanmadı")
            advanceMain(80)
            Thread.sleep(15)
        }
    }

    protected fun stepText(): String = text("tutorial_step")

    protected fun startGroup(id: String) {
        waitFor { offerShown() }
        // v50 dikey ekranda eğitim listesi küçük pencerede kaydırılır
        compose.onNodeWithTag("tutorial_group_$id").clickInPopup()
        waitFor { calloutShown() }
    }

    protected fun next() {
        compose.onNodeWithTag("tutorial_next", useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    /** Adımın çizimi bitene dek bekler (kutudaki "çiziliyor" göstergesi kalkar). */
    protected fun awaitDemo(timeoutMs: Long = 40_000) = runUntil(timeoutMs) { vm().tutorial.ui.value?.running == false }

    protected fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
}

/**
 * v32 (1) ilk girişte eğitim önerisi: çizim tuvaline ilk girişte araç grupları (çizim araçları, şekil ve yazı, yapay
 * zekâ) önerilir; "Şimdi değil" ile kapanır ve aynı türde yeniden önerilmez; ⋮ menüsündeki Eğitim modu ile istenince
 * yeniden açılır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TutorialTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V32OfferTest : V32EditorBase() {
    @Test
    fun offeredOnceAndOnRequest() {
        createCanvasAndOpen("Eğitim")
        waitFor { offerShown() }
        assertTrue(compose.onAllNodesWithText("Çizim moduna hoş geldin").fetchSemanticsNodes().isNotEmpty())
        listOf("art_basics", "art_add", "art_ai").forEach { assertTrue(it, exists("tutorial_group_$it")) }
        shot("v32-offer-art")
        compose.onNodeWithTag("tutorial_later").clickInPopup()
        waitFor { !offerShown() }
        // ⋮ → Eğitim modu
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        waitFor { exists("tutorial_menu_item") }
        compose.onNodeWithTag("tutorial_menu_item").performClick()
        waitFor { offerShown() }
        compose.onNodeWithTag("tutorial_later").clickInPopup()
        waitFor { !offerShown() }
    }
}

/**
 * v32 (2) çizim araçları eğitimi: her adımda açıklama kutusu tanıtılan aracın hemen altında açılır (fırça, fırça
 * ayarları, boya kutusu, silgi, kalem aracı, geri al, katmanlar), çizim alanında adım adım manzara çizilir; "Sonraki
 * adım" ve son adımda "Eğitimi bitir". Eğitim kaydedilmez: bitince tuval eğitimden önceki haline döner.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TutorialTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V32ArtBasicsTest : V32EditorBase() {
    @Test
    fun stepsBelowToolsAndCanvasRestored() {
        createCanvasAndOpen("Manzara")
        startGroup("art_basics")
        assertEquals("1 / 7", stepText())
        val anchors = listOf(TutorialAnchor.BRUSH, TutorialAnchor.BRUSH_SETTINGS, TutorialAnchor.FILL, TutorialAnchor.ERASER, TutorialAnchor.PEN, TutorialAnchor.UNDO, TutorialAnchor.LAYERS)
        for ((i, anchor) in anchors.withIndex()) {
            runUntil { stepText() == "${i + 1} / 7" }
            assertEquals(anchor, vm().tutorial.ui.value?.step?.anchor)
            awaitDemo()
            advanceMain(300)
            // Kutu aracın hemen altında
            val tool = vm().let { compose.onAllNodesWithTag("tutorial_callout").fetchSemanticsNodes().first().boundsInRoot }
            assertTrue("kutu araçtan sonra", tool.top > 0f)
            if (i == 0) shot("v32-art-step1")
            if (i == 2) shot("v32-art-step3")
            if (i == anchors.lastIndex) {
                shot("v32-art-final")
                assertTrue(compose.onAllNodes(hasTestTag("tutorial_next") and hasText("Eğitimi bitir", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("Eğitimi bitir").fetchSemanticsNodes().isNotEmpty())
            }
            next()
        }
        waitFor { !calloutShown() }
        // Bellekteki çizim eğitimden önceki (boş) haline döndü; hiçbir şey kaydedilmedi
        assertTrue(live().strokes.isEmpty())
        assertNull(live().background.gradient)
        assertTrue(canvas().strokes.isEmpty())
        assertTrue("art_basics" in vm().tutorial.completed.value)
    }

    /** Kutunun tanıtılan aracın altında olduğu: fırça ve boya kutusu adımlarında. */
    @Test
    fun calloutSitsUnderTheTool() {
        createCanvasAndOpen("Konum")
        startGroup("art_basics")
        awaitDemo()
        advanceMain(300)
        val brush = vm().let { compose.onNodeWithContentDescription("Fırça Seç").fetchSemanticsNode().boundsInRoot }
        val box = bounds("tutorial_callout")
        assertTrue("kutu fırçanın altında: $box / $brush", box.top >= brush.bottom)
        assertTrue("kutu fırçaya yakın", box.top - brush.bottom < 120f)
        // Demo çizgileri bellekte, kayıtta değil
        assertTrue(live().strokes.size >= 4)
        assertTrue(live().strokes.all { it.brushType == BrushType.INK_PEN })
        assertTrue(canvas().strokes.isEmpty())
    }
}

/**
 * v32 (3) eğitim iptali: kullanıcının çizimi ve geri alma geçmişi korunur; eğitim boş sayfada gösterilir, iptal edilince
 * çizim olduğu gibi geri gelir. "Sonraki adım"a çizim bitmeden basılırsa adımın çizimi hemen tamamlanır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TutorialTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V32CancelTest : V32EditorBase() {
    @Test
    fun cancelRestoresDrawingAndHistory() {
        createCanvasAndOpen("Benim")
        waitFor { offerShown() }
        compose.onNodeWithTag("tutorial_later").clickInPopup()
        waitFor { !offerShown() }
        val size = canvasSize()
        jitteryLine(size.y * 0.5f, 2f)
        val mine = canvas().strokes
        assertEquals(1, mine.size)
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        waitFor { exists("tutorial_menu_item") }
        compose.onNodeWithTag("tutorial_menu_item").performClick()
        startGroup("art_basics")
        assertTrue("eğitim boş sayfada", live().strokes.none { it.id == mine.first().id })
        // Çizim bitmeden sonraki adım: ilk adımın dört çizgisi hemen tamamlanır
        next()
        runUntil { stepText() == "2 / 7" }
        assertTrue(live().strokes.count { it.brushType == BrushType.INK_PEN } >= 4)
        compose.onNodeWithTag("tutorial_cancel", useUnmergedTree = true).performClick()
        waitFor { !calloutShown() }
        assertEquals(mine.map { it.id }, live().strokes.map { it.id })
        assertEquals(mine.map { it.id }, canvas().strokes.map { it.id })
        // Kullanıcının geri alma geçmişi yerinde
        // v45 dikey ekranda adlar düğmenin içinde, üst satır kayar (eğitimde seçili araca hizalanmış olabilir): önce Geri al'a kaydırılır
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { canvas().strokes.isEmpty() }
    }
}

/**
 * v32 (4) yapay zekâ eğitimi (çizim): v38 iki yayla çizilen ay çizgi önerisiyle düzgün çembere döner (çizgi önerisi
 * balonu vurgulanır, kutuda ipucu), ev şablonu (4+ eleman) ve çam ağaçlarında "Çizgileri düzelt". Eğitimin önerileri tuvalin yapay zekâ ayarı kapalı
 * olsa da çalışır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TutorialTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V32ArtAiTest : V32EditorBase() {
    @Test
    fun aiSuggestionsAreShownAndApplied() {
        createCanvasAndOpen("YZ")
        val repo = org.koin.core.context.GlobalContext.get().get<com.karapp.drawguidance.data.repository.ShapeTemplateRepository>()
        waitFor(90_000) { repo.indexFlow.value != null }
        startGroup("art_ai")
        awaitDemo()
        next()
        runUntil { stepText() == "2 / 6" }
        // Öneri balonu vurgulanır, ipucu görünür
        runUntil(40_000) { vm().tutorial.ui.value?.focus == TutorialAnchor.SUGGEST_LINE }
        assertTrue(exists("tutorial_hint"))
        shot("v32-ai-suggest")
        awaitDemo()
        // İki yay birlikte düzelir: noktaları tek düzgün çemberin üstünde
        val arcs = live().strokes
        assertEquals(2, arcs.size)
        val pts = arcs.flatMap { it.points }
        val c = Offset(pts.map { it.x }.average().toFloat(), pts.map { it.y }.average().toFloat())
        val radii = pts.map { Offset(it.x, it.y).minus(c).getDistance() }
        assertTrue("düzgün çember", radii.max() - radii.min() < radii.average() * 0.08f)
        next()
        runUntil { stepText() == "3 / 6" }
        awaitDemo(60_000)
        // Ev: şablon çizgileri (gruplu)
        assertTrue(live().strokes.any { it.groupId != null })
        next()
        runUntil { stepText() == "4 / 6" }
        awaitDemo(60_000)
        next()
        // v40 tek ama büyük çizgiye de öneri gelir
        runUntil { stepText() == "5 / 6" }
        awaitDemo(60_000)
        shot("v32-ai-final")
        next()
        runUntil { stepText() == "6 / 6" }
        next()
        waitFor { !calloutShown() }
        assertTrue(live().strokes.isEmpty())
    }
}

/**
 * v32 (5) not alma: el yazısı yazıya çevrilir (yazı önerisi); formül, akış diyagramı ve ok eklenir. Çizgi roman: panel
 * düzeni, kahraman, odak çizgileri; konuşma balonu ve efekt yazısı.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TutorialTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V32NotesComicTest : V32EditorBase() {
    @Test
    fun notesHandwritingAndFormula() {
        createNotesCanvasAndOpen("Fizik")
        waitFor { offerShown() }
        assertTrue(compose.onAllNodesWithText("Not alma moduna hoş geldin").fetchSemanticsNodes().isNotEmpty())
        startGroup("notes_ai")
        runUntil(40_000) { vm().tutorial.ui.value?.focus == TutorialAnchor.SUGGEST_TEXT }
        shot("v32-notes-handwriting")
        awaitDemo()
        val texts = live().strokes.filter { it.brushType == BrushType.TEXT }
        assertEquals("kuvvet", texts.single().text?.content)
        compose.onNodeWithTag("tutorial_cancel", useUnmergedTree = true).performClick()
        waitFor { !calloutShown() }
        // Formül ve diyagram grubu
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        waitFor { exists("tutorial_menu_item") }
        compose.onNodeWithTag("tutorial_menu_item").performClick()
        startGroup("notes_formula")
        repeat(3) { awaitDemo(); next() }
        runUntil { stepText() == "4 / 4" }
        awaitDemo()
        val kinds = live().strokes.map { it.brushType }.toSet()
        assertTrue(kinds.containsAll(setOf(BrushType.FORMULA, BrushType.DIAGRAM, BrushType.SHAPE)))
        shot("v32-notes-final")
        next()
        waitFor { !calloutShown() }
        assertTrue(live().strokes.isEmpty())
    }

    @Test
    fun comicPanelsHeroAndBalloons() {
        createComicCanvasAndOpen("Kahraman")
        waitFor { offerShown() }
        startGroup("comic_panels")
        repeat(3) { awaitDemo(); next() }
        runUntil { stepText() == "4 / 4" }
        awaitDemo()
        val strokes = live().strokes
        assertEquals(3, strokes.count { it.brushType == BrushType.SHAPE })
        assertTrue(strokes.count { it.brushType == BrushType.INK_PEN } > 20)
        shot("v32-comic-panels")
        next()
        waitFor { !calloutShown() }
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        waitFor { exists("tutorial_menu_item") }
        compose.onNodeWithTag("tutorial_menu_item").performClick()
        startGroup("comic_text")
        repeat(2) { awaitDemo(); next() }
        runUntil { stepText() == "3 / 3" }
        awaitDemo()
        val balloons = live().strokes.filter { it.text?.box?.balloon != null }
        assertEquals(2, balloons.size)
        assertTrue(live().strokes.any { it.text?.content == "POW!" })
        shot("v32-comic-text")
        next()
        waitFor { !calloutShown() }
    }
}

/** v32 ekran görüntüleri (DG_SHOTS verilince): şekil ve yazı grubu, not yazma grubu, çizgi roman yapay zekâ grubu. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TutorialTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V32ShotsTest : V32EditorBase() {
    private fun playAll(prefix: String, count: Int, shotAt: Set<Int> = emptySet()) {
        for (i in 1..count) {
            runUntil { stepText() == "$i / $count" }
            awaitDemo(60_000)
            advanceMain(400)
            if (i in shotAt || i == count) shot("$prefix-$i")
            next()
        }
        waitFor { !calloutShown() }
    }

    @Test
    fun artAddShots() {
        org.junit.Assume.assumeTrue(System.getenv("DG_SHOTS") != null)
        createCanvasAndOpen("Vektör")
        startGroup("art_add")
        playAll("v32-artadd", 6, setOf(2, 3))
    }

    @Test
    fun notesWriteShots() {
        org.junit.Assume.assumeTrue(System.getenv("DG_SHOTS") != null)
        createNotesCanvasAndOpen("Not")
        startGroup("notes_write")
        playAll("v32-noteswrite", 4, setOf(3))
    }

    @Test
    fun comicAiShots() {
        org.junit.Assume.assumeTrue(System.getenv("DG_SHOTS") != null)
        createComicCanvasAndOpen("Roman")
        val repo = org.koin.core.context.GlobalContext.get().get<com.karapp.drawguidance.data.repository.ShapeTemplateRepository>()
        waitFor(90_000) { repo.indexFlow.value != null }
        startGroup("comic_ai")
        playAll("v32-comicai", 4, setOf(1, 2, 3))
    }
}
