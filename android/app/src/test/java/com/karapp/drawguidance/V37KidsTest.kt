package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.local.LocalUserDao
import com.karapp.drawguidance.data.local.LocalUserEntity
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.data.settings.KidsPrefs
import com.karapp.drawguidance.ui.components.MascotStateKey
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.VectorEdit
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.state.KidsLines
import com.karapp.drawguidance.ui.screens.canvasedit.state.KidsTopic
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialKids
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** v37 çocuk modu: yaş sınırı, maskotun cümle kalıpları ve eğitimin yüz/el tablosu. */
class V37KidsLogicTest {
    @Test
    fun childAgeFromBirthDate() {
        val today = LocalDate.of(2026, 10, 20)
        assertTrue(KidsPrefs.isChild("2016-05-01", today))
        assertTrue("12. doğum gününden bir gün önce", KidsPrefs.isChild("2014-10-21", today))
        assertFalse("tam 12 yaş", KidsPrefs.isChild("2014-10-20", today))
        assertFalse(KidsPrefs.isChild("1990-05-17", today))
        assertFalse("doğum tarihi yok (giriş yapılmamış)", KidsPrefs.isChild(null, today))
        assertFalse(KidsPrefs.isChild("17.05.2016", today))
        assertFalse("gelecek tarih", KidsPrefs.isChild("2030-01-01", today))
    }

    @Test
    fun suggestionTemplatesVaryButStayStable() {
        KidsTopic.entries.forEach { topic ->
            val lines = KidsLines.suggestions.getValue(topic)
            assertTrue("$topic için birden çok kalıp", lines.size >= 2)
            assertEquals("$topic kalıplarının yüzleri farklı", lines.size, lines.map { it.look }.toSet().size)
            // Aynı tohum hep aynı kalıp; farklı önerilerde farklı kalıplar çıkar
            (0L until 20L).forEach { seed -> assertTrue(KidsLines.pick(topic, seed) === KidsLines.pick(topic, seed)) }
            assertEquals(lines.size, (0L until 200L).map { KidsLines.pick(topic, it) }.toSet().size)
        }
        assertEquals(5, KidsLines.tips.size)
        assertEquals(3, KidsLines.praise.size)
    }

    @Test
    fun tutorialLooksChangeEveryStep() {
        val steps = TutorialKids.intro.steps
        // v40 cetvel ve çizim görevi adımları eklendi; v46 boya fırçası adımı
        assertEquals(12, steps.size)
        val looks = steps.map { TutorialKids.lookOf(it) }
        looks.zipWithNext().forEach { (a, b) ->
            assertNotEquals("art arda iki adımda yüz farklı", a.face, b.face)
            assertNotEquals("art arda iki adımda el farklı", a.gesture, b.gesture)
        }
        assertTrue("en az beş farklı yüz", looks.map { it.face }.toSet().size >= 5)
        assertTrue("en az beş farklı el hareketi", looks.map { it.gesture }.toSet().size >= 5)
    }
}

/** v37 çocuk tuvali testlerinin ortak yardımcıları. */
abstract class V37KidsBase : V33EditorBase() {
    protected fun repo() = GlobalContext.get().get<CanvasRepository>()
    protected fun kidsPrefs() = GlobalContext.get().get<KidsPrefs>()

    protected fun openKids(name: String = "Çocuk", ai: Boolean = false) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = ai, kind = CanvasKind.KIDS) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    /** Çocuk şekli kâğıdın ortasına (1000×1000 kâğıtta kutu: 440 birim; tasarım biriminin 4,4 katı). */
    protected fun addKidShape(id: String): String {
        compose.runOnIdle {
            vm().selectShapeId(id)
            vm().addShapeAt("g-$id", Offset(500f, 500f))
        }
        waitFor { vm().canvas.value?.strokes?.any { it.shape?.kind == id } == true }
        compose.runOnIdle { vm().chooseMode(EditMode.DRAW) }
        return vm().canvas.value!!.strokes.first { it.shape?.kind == id }.id
    }

    protected fun shape(id: String) = vm().canvas.value!!.strokes.first { it.id == id }
    protected fun regions(id: String) = ShapeFactory.subPaths(shape(id)).filter { it.tag == VectorEdit.REGION }

    protected fun pickPaint(color: String) {
        compose.onNodeWithTag("simple_tool_paint").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.PAINT && exists("kids_palette") }
        compose.onNodeWithContentDescription(color).performClick()
        compose.waitForIdle()
    }

    protected fun mascotState(): String =
        compose.onNodeWithTag("kids_mascot").fetchSemanticsNode().config.getOrElse(MascotStateKey) { "" }

    protected fun balloonText(): String =
        compose.onNodeWithTag("kids_mascot_text", useUnmergedTree = true).fetchSemanticsNode().config
            .getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString()

    /** Saati adım adım ilerletir (maskotun yazması, basılı tutma). */
    protected fun tick(ms: Long, step: Long = 50) {
        var left = ms
        while (left > 0) { val d = minOf(step, left); compose.mainClock.advanceTimeBy(d); left -= d }
        compose.waitForIdle()
    }

    protected fun wobblyCircleAt(c: Offset, r: Float, seed: Long) {
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
}

/**
 * v37 (3) çocuk boyaması: boyama düğmesi silginin hemen yanında; kova seçili renkle parmağın üstünde gezer, altındaki
 * şekil vurgulanır ve boyanmaya hazır alan görünür; dokununca alan boyanır, 2 sn basılı tutunca bütün şekil boyanır;
 * geri al çalışır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V37KidsPaintTest : V37KidsBase() {
    /** Menüdeki araçlar soldan sağa (menü kaydırılabilir: konumlar ekran dışında da sıralıdır). */
    private fun toolOrder(): List<String> =
        listOf("brush", "eraser", "paint", "shape", "background", "image", "text", "model3d", "elements", "select")
            .filter { exists("simple_tool_$it") }
            // v47 dikeyde iki satır: önce satır (üstten alta), sonra soldan sağa
            .sortedWith(compareBy({ compose.onNodeWithTag("simple_tool_$it").fetchSemanticsNode().positionInRoot.y.toInt() / 40 }, { compose.onNodeWithTag("simple_tool_$it").fetchSemanticsNode().positionInRoot.x }))

    @Test
    fun paintButtonRightAfterEraser() {
        openKids()
        val order = toolOrder()
        assertEquals(order.toString(), order.indexOf("eraser") + 1, order.indexOf("paint"))
        // v47 kalem, silgi ve boyamadan sonra şekil ve yazı (görev ve kalıp bu listede yok)
        assertEquals(listOf("brush", "eraser", "paint", "shape", "text"), order.take(5))
    }

    @Test
    fun hoverHighlightsAndTapFillsRegionThenUndo() {
        openKids()
        val house = addKidShape("lib.kid_house")
        pickPaint("Kırmızı")
        // Parmak çatının üstünde: kova ve hazır alan görünür (henüz boyanmaz). Saat elle ilerler (halkanın
        // canlandırması beklenirken 2 sn dolup bütün şekil boyanmasın)
        val roof = screenOf(280f + 4.4f * 30f, 280f + 4.4f * 40f)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("drawing_canvas").performTouchInput { down(roof) }
        waitFor { vm().paintCursor.value?.target?.region != null }
        val cursor = vm().paintCursor.value!!
        assertEquals(house, cursor.target!!.strokeId)
        // Saat durduğu için katman bir sonraki karede çizilir
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertTrue(exists("kids_paint_cursor"))
        assertTrue("henüz boyanmadı", regions(house).isEmpty())
        assertTrue("halka başladı", cursor.hold >= 0)
        tick(600)
        shot("v37-kids-paint-hover")
        compose.onNodeWithTag("drawing_canvas").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        waitFor { regions(house).any { it.fillColorHex.equals("#E53935", true) } }
        assertEquals(1, regions(house).size)
        waitFor { vm().paintCursor.value == null }
        // Başka renkle duvar (dokunma)
        compose.onNodeWithContentDescription("Sarı").performClick()
        tap(screenOf(280f + 4.4f * 30f, 280f + 4.4f * 84f))
        waitFor { regions(house).size == 2 && regions(house).any { it.fillColorHex.equals("#FDD835", true) } }
        shot("v37-kids-paint-tapped")
        // Geri al: önce duvar, sonra çatı
        compose.onNodeWithContentDescription("Geri al").performClick()
        waitFor { regions(house).size == 1 }
        compose.onNodeWithContentDescription("Geri al").performClick()
        waitFor { regions(house).isEmpty() }
    }

    @Test
    fun holdTwoSecondsFillsWholeShape() {
        openKids()
        val balloons = addKidShape("lib.kid_balloons")
        val before = ShapeFactory.subPaths(shape(balloons))
        pickPaint("Pembe")
        // Ortadaki balon (tasarımda 50, 44; kutu 300..700 × 280..720, birim 4)
        val at = screenOf(300f + 4f * 50f, 280f + 4f * 44f)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("drawing_canvas").performTouchInput { down(at) }
        waitFor { vm().paintCursor.value?.target != null }
        assertTrue("halka başladı", (vm().paintCursor.value?.hold ?: -1) >= 0)
        tick(1000)
        shot("v37-kids-paint-hold")
        assertTrue("1 sn'de bütün şekil boyanmaz", ShapeFactory.subPaths(shape(balloons)).none { it.fillColorHex.equals("#EC407A", true) })
        tick(1200)
        compose.onNodeWithTag("drawing_canvas").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val after = ShapeFactory.subPaths(shape(balloons))
        val pink = after.count { it.closed && it.fillColorHex.equals("#EC407A", true) }
        assertTrue("bütün balonlar ve fiyonk pembe ($pink)", pink >= 4)
        assertTrue("alan tek tek eklenmedi", after.none { it.tag == VectorEdit.REGION })
        shot("v37-kids-paint-whole")
        // Tek geri alma adımı
        compose.onNodeWithContentDescription("Geri al").performClick()
        waitFor { ShapeFactory.subPaths(shape(balloons)).map { it.fillColorHex } == before.map { it.fillColorHex } }
    }

    @Test
    fun dragMovesBucketAndHoldFillsWholeShape() {
        openKids()
        val house = addKidShape("lib.kid_house")
        pickPaint("Mavi")
        val size = canvasSize()
        val start = Offset(size.x * 0.12f, size.y * 0.3f)
        val wall = screenOf(280f + 4.4f * 30f, 280f + 4.4f * 84f)
        // Sürükleme görünümü kaydırmaz, kovayı taşır (kova parmağın tuvaldeki yerinde)
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(start)
            repeat(12) { i -> moveTo(start + (wall - start) * ((i + 1) / 12f)) }
        }
        compose.waitForIdle()
        waitFor { vm().paintCursor.value?.target?.strokeId == house }
        val p = vm().paintCursor.value!!.point
        assertTrue("kova parmağın yerinde: $p", (p - Offset(280f + 4.4f * 30f, 280f + 4.4f * 84f)).getDistance() < 30f)
        tick(2300)
        compose.onNodeWithTag("drawing_canvas").performTouchInput { up() }
        compose.waitForIdle()
        val parts = ShapeFactory.subPaths(shape(house)).count { it.closed && it.fillColorHex.equals("#1E88E5", true) }
        assertTrue("evin bütün parçaları mavi ($parts)", parts >= 5)
        waitFor { vm().paintCursor.value == null }
    }
}

/**
 * v37 (4.1) çocuk tuvalinde maskot: köşedeki yüz düğmesiyle açılır ve açık kalır (tercih hatırlanır); öneriler
 * maskotun balonunda (büyüklerin öneri balonu çıkmaz), tik ile uygulanır, çarpı öneriyi reddedip maskotu kapatır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V37KidsMascotTest : V37KidsBase() {
    @Test
    fun toggleStaysOnAndPersists() {
        openKids()
        waitFor { exists("kids_mascot_toggle") }
        assertFalse(exists("kids_mascot"))
        shot("v37-kids-mascot-toggle")
        compose.onNodeWithTag("kids_mascot_toggle").performClick()
        waitFor { exists("kids_mascot") && exists("kids_mascot_balloon") }
        assertTrue(kidsPrefs().mascotOn.value)
        assertTrue(balloonText().startsWith("Merhaba, ben Boyacık"))
        // Yazarken ağız oynar, yazı bitince kapanır
        val mouths = HashSet<String>()
        repeat(16) { tick(32, 16); mouths += mascotState().substringAfterLast('/') }
        assertEquals(setOf("open", "closed"), mouths)
        tick(4000)
        assertEquals("HAPPY/WAVE/closed", mascotState())
        shot("v37-kids-mascot-hello")
        // Tamam: balon kapanır, maskot kalır; maskota dokununca ipucu
        compose.onNodeWithTag("mascot_next").performClick()
        waitFor { !exists("kids_mascot_balloon") && exists("kids_mascot") }
        compose.onNodeWithTag("kids_mascot").performClick()
        waitFor { exists("kids_mascot_balloon") }
        assertTrue(balloonText().contains("Boya kovasını"))
        // Çarpı: maskot ve balon kapanır, yüz düğmesi döner
        compose.onNodeWithTag("mascot_close").performClick()
        waitFor { !exists("kids_mascot") && exists("kids_mascot_toggle") }
        assertFalse(kidsPrefs().mascotOn.value)
        // Yeniden açılır ve tuvale dönünce açık kalır (tercih kalıcı)
        compose.onNodeWithTag("kids_mascot_toggle").performClick()
        waitFor { exists("kids_mascot") }
        compose.onNodeWithContentDescription("Geri").performClick()
        waitFor { exists("canvas_list") || compose.onAllNodesWithText("Çocuk").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Çocuk")[0].performClick()
        waitFor { exists("drawing_canvas") }
        waitFor { exists("kids_mascot") }
        assertFalse(exists("kids_mascot_toggle"))
        assertTrue(KidsPrefs(ApplicationProvider.getApplicationContext()).mascotOn.value)
    }

    @Test
    fun suggestionsSpeakInBalloonApplyAndClose() {
        openKids(ai = true)
        val size = canvasSize()
        val r = size.x * 0.12f
        wobblyCircleAt(Offset(size.x * 0.3f, size.y * 0.3f), r, 3)
        waitFor { vm().canvas.value!!.strokes.size == 1 }
        advanceMain(900)
        // v38 tek çizgiye öneri yok; yan yana ikinci çemberle çizgi önerisi
        assertFalse(exists("kids_mascot_suggest"))
        wobblyCircleAt(Offset(size.x * 0.3f + r * 2.2f, size.y * 0.3f), r, 4)
        waitFor { vm().canvas.value!!.strokes.size == 2 }
        advanceMain(900)
        waitFor(15_000) { exists("kids_mascot_suggest") }
        // Büyüklerin öneri balonu yok; maskot kalıplardan birini söyler
        assertFalse(exists("shape_suggest_chip") || exists("line_suggest_chip"))
        tick(4000)
        val said = balloonText()
        println("maskot: $said / ${mascotState()}")
        assertTrue(said, said.isNotBlank() && !said.contains("%"))
        assertTrue(exists("mascot_apply") && exists("mascot_close"))
        shot("v37-kids-mascot-suggest")
        val before = vm().canvas.value!!.strokes.toList()
        compose.onNodeWithTag("mascot_apply").performClick()
        waitFor { vm().canvas.value!!.strokes.zip(before).all { (a, b) -> a.points != b.points } }
        waitFor { !exists("kids_mascot_suggest") }
        // Maskot kapalıyken uygulanınca köşede yüz düğmesi kalır
        waitFor { exists("kids_mascot_toggle") }

        // İkinci öneri: çarpı öneriyi reddeder, maskotu ve balonu kapatır
        compose.onNodeWithTag("kids_mascot_toggle").performClick()
        waitFor { kidsPrefs().mascotOn.value }
        wobblyCircleAt(Offset(size.x * 0.7f, size.y * 0.6f), r, 5)
        wobblyCircleAt(Offset(size.x * 0.7f - r * 2.2f, size.y * 0.6f), r, 6)
        waitFor { vm().canvas.value!!.strokes.size == 4 }
        advanceMain(900)
        waitFor(15_000) { exists("kids_mascot_suggest") }
        val drawn = vm().canvas.value!!.strokes.last()
        compose.onNodeWithTag("mascot_close").performClick()
        waitFor { vm().lineSuggestion.value == null && vm().shapeSuggestion.value == null && !exists("kids_mascot") }
        assertFalse(kidsPrefs().mascotOn.value)
        assertTrue(exists("kids_mascot_toggle"))
        assertEquals(drawn.points, vm().canvas.value!!.strokes.last().points)
    }

    @Test
    fun praiseAfterApplyWhenMascotOn() {
        openKids(ai = true)
        compose.runOnIdle { kidsPrefs().setMascotOn(true) }
        waitFor { exists("kids_mascot") }
        val size = canvasSize()
        wobblyCircleAt(Offset(size.x * 0.4f, size.y * 0.35f), size.x * 0.12f, 11)
        wobblyCircleAt(Offset(size.x * 0.4f + size.x * 0.26f, size.y * 0.35f), size.x * 0.12f, 12)
        advanceMain(900)
        waitFor(15_000) { exists("kids_mascot_suggest") }
        compose.onNodeWithTag("mascot_apply").performClick()
        waitFor { !exists("kids_mascot_suggest") && exists("kids_mascot_balloon") }
        val praise = balloonText()
        assertTrue(praise, listOf("Harika", "Süper", "Bayıldım").any { praise.startsWith(it) })
    }
}

/**
 * v37 (4) ilk girişte maskotlu eğitim: çocuk tuvaline ilk girişte öneri penceresi olmadan başlar; açıklamalar
 * maskotun başının üstündeki balonda (ok simgeli Tamam ve çarpı simgeli Sonlandır), her adımda farklı yüz ve el;
 * yazı yazılırken ağız oynar; örnek resim çizilir (güneş, ev, boyalı parçalar, pembe balonlar, mavi gökyüzü).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TutorialTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V37KidsTutorialTest : V32EditorBase() {
    private fun openKids(name: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { GlobalContext.get().get<CanvasRepository>().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = CanvasKind.KIDS) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") }
    }

    private fun mascotState(): String =
        compose.onNodeWithTag("kids_mascot").fetchSemanticsNode().config.getOrElse(MascotStateKey) { "" }

    @Test
    fun firstEntryMascotTutorialDrawsExample() {
        openKids("İlk")
        waitFor { exists("kids_tutorial") }
        assertFalse("öneri penceresi yok", offerShown())
        assertFalse("büyüklerin açıklama kutusu yok", calloutShown())
        assertTrue(exists("kids_mascot_balloon") && exists("mascot_next") && exists("mascot_close"))
        // Yazarken ağız açılıp kapanır
        val mouths = HashSet<String>()
        repeat(20) { compose.mainClock.advanceTimeBy(32); compose.waitForIdle(); mouths += mascotState().substringAfterLast('/') }
        assertEquals(setOf("open", "closed"), mouths)
        val seen = ArrayList<String>()
        val count = TutorialKids.intro.steps.size
        for (i in 0 until count) {
            runUntil { vm().tutorial.ui.value?.index == i }
            awaitDemo()
            advanceMain(300)
            repeat(40) { compose.mainClock.advanceTimeBy(100) }
            compose.waitForIdle()
            val state = mascotState()
            assertTrue("yazı bitince ağız kapalı: $state", state.endsWith("/closed"))
            seen += state.substringBeforeLast('/')
            if (i == 0) shot("v37-kids-tut-1")
            if (i == 3) shot("v37-kids-tut-paint")
            if (i == count - 1) {
                shot("v37-kids-tut-final")
                assertTrue(compose.onAllNodesWithText("Eğitimi bitir").fetchSemanticsNodes().isNotEmpty() ||
                    compose.onNodeWithTag("mascot_next").fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.contains("Eğitimi bitir"))
                // Örnek resim: güneş, ev ve balonlar; evin parçaları boyalı, balonlar pembe, gökyüzü mavi
                val strokes = live().strokes
                val house = strokes.first { it.shape?.kind == "lib.kid_house" }
                val balloons = strokes.first { it.shape?.kind == "lib.kid_balloons" }
                val houseFills = ShapeFactory.subPaths(house).mapNotNull { it.fillColorHex?.uppercase() }.toSet()
                assertTrue(houseFills.toString(), "#E53935" in houseFills && "#FDD835" in houseFills)
                assertTrue(ShapeFactory.subPaths(balloons).count { it.fillColorHex.equals("#EC407A", true) } >= 4)
                assertNotNull(live().background.gradient)
                assertTrue(strokes.any { it.colorHex.equals("#FDD835", true) })
            }
            compose.onNodeWithTag("mascot_next").performClick()
            compose.waitForIdle()
        }
        waitFor { !exists("kids_tutorial") }
        assertEquals("her adımda yüz ve el: $seen", count, seen.size)
        seen.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
        // Eğitim kaydedilmez: tuval boş döner
        assertTrue(live().strokes.isEmpty())
        assertNull(live().background.gradient)
    }

    @Test
    fun closeEndsTutorial() {
        openKids("Kapat")
        waitFor { exists("kids_tutorial") }
        compose.onNodeWithTag("mascot_close").performClick()
        waitFor { !exists("kids_tutorial") && vm().tutorial.ui.value == null }
        assertTrue(live().strokes.isEmpty())
        // Eğitim bitince köşede maskotun yüz düğmesi
        waitFor { exists("kids_mascot_toggle") }
    }
}

/**
 * v37 (3.1) Ana sayfada 12 yaşından küçüklere çocuk modu önerisi: maskot "İstersen en eğlenceli çizimleri yapmaya
 * başla" der; Hadi başla çocuk tuvali açar; Şimdi değil öneriyi kalıcı olarak kapatır. 12 yaş ve üstünde, girişsizken
 * öneri yok.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HomeNoWelcomeApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V37KidsHomeTest : V33EditorBase() {
    @Before fun reset() = TestAccount.reset()
    @After fun resetAfter() = TestAccount.reset()

    private fun signIn(birth: String?) = runBlocking {
        TestAccount.auth.signedIn = FakeAuth.DEFAULT
        val now = System.currentTimeMillis()
        GlobalContext.get().get<LocalUserDao>().replaceAll(
            LocalUserEntity(
                id = "u1", firebaseUid = FakeAuth.DEFAULT.firebaseUid, displayName = "Ada Kar", givenName = "Ada", familyName = "Kar",
                email = "ada@example.com", emailVerified = true, photoUrl = null, accountCreatedAt = now, birthDate = birth,
                signedIn = true, createdAt = now, updatedAt = now,
            ),
        )
    }

    private fun birthYearsAgo(years: Long) = LocalDate.now().minusYears(years).toString()

    @Test
    fun childSeesCardAndStartsKidsCanvas() {
        waitFor { exists("home_screen") }
        assertFalse("girişsiz öneri yok", exists("home_kids_card"))
        signIn(birthYearsAgo(8))
        waitFor { exists("home_kids_card") }
        val text = compose.onNodeWithTag("home_kids_text", useUnmergedTree = true).fetchSemanticsNode().config
            .getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString()
        assertEquals("İstersen en eğlenceli çizimleri yapmaya başla", text)
        // v44 12 yaşından küçük hesapta çocuk kilidi açılır: kart kapatılamaz
        assertTrue(exists("home_kids_start"))
        waitFor { exists("kids_lock_home") }
        assertFalse(exists("home_kids_dismiss"))
        repeat(40) { compose.mainClock.advanceTimeBy(60) }
        compose.waitForIdle()
        shot("v37-kids-home-card")
        compose.onNodeWithTag("home_kids_start").performClick()
        waitFor { exists("drawing_canvas") }
        assertEquals(CanvasKind.KIDS, vm().canvas.value?.kind)
        assertEquals(CanvasKind.KIDS, allCanvases().single().kind)
    }

    @Test
    fun noCardFromTwelveOrSignedOut() {
        waitFor { exists("home_screen") }
        signIn(birthYearsAgo(12))
        waitFor { exists("home_profile") }
        assertFalse(exists("home_kids_card"))
        signIn(birthYearsAgo(30))
        advanceMain(300)
        assertFalse(exists("home_kids_card"))
        // Çocuk ama çıkış yapmış
        signIn(birthYearsAgo(9))
        waitFor { exists("home_kids_card") }
        runBlocking {
            val dao = GlobalContext.get().get<LocalUserDao>()
            dao.upsert(dao.get()!!.copy(signedIn = false))
        }
        // v44 çocuk kilidi çıkışta da açık kalır (ebeveyn şifresiyle kapanır); kapanınca kart da gider
        compose.waitForIdle()
        assertTrue(exists("home_kids_card"))
        unlockKids()
        waitFor { !exists("home_kids_card") }
    }

    /** v44 yaşla açılan çocuk kilidini ebeveyn şifresiyle kapatır. */
    private fun unlockKids() = runBlocking {
        val lock = GlobalContext.get().get<com.karapp.drawguidance.data.kids.KidsLockRepository>()
        kotlinx.coroutines.withTimeout(5_000) { lock.locked.first { it } }
        lock.setPin("1357")
        assertEquals(com.karapp.drawguidance.data.kids.KidsLockRepository.Result.OK, lock.disable("1357"))
        kotlinx.coroutines.withTimeout(5_000) { lock.locked.first { !it } }
    }

    @Test
    fun childOfflineStillSeesCard() {
        waitFor { exists("home_screen") }
        TestAccount.connectivity.set(false)
        try {
            signIn(birthYearsAgo(7))
            // İnternet yokken profil görünmez ama öneri cihazdaki doğum tarihiyle görünür
            waitFor { exists("home_kids_card") }
            assertFalse(exists("home_profile"))
        } finally {
            TestAccount.connectivity.set(true)
        }
    }

    @Test
    fun dismissIsRemembered() {
        waitFor { exists("home_screen") }
        signIn(birthYearsAgo(10))
        waitFor { exists("home_kids_card") }
        // v44 kilit açıkken kart kapatılamaz; ebeveyn kilidi kapatınca öneri kartı kapatılabilir
        unlockKids()
        waitFor { exists("home_kids_dismiss") }
        compose.onNodeWithTag("home_kids_dismiss").performClick()
        waitFor { !exists("home_kids_card") }
        assertTrue(GlobalContext.get().get<KidsPrefs>().homeCardDismissed.value)
        assertTrue(KidsPrefs(ApplicationProvider.getApplicationContext()).homeCardDismissed.value)
    }
}
