package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.settings.KidsPrefs
import com.karapp.drawguidance.ui.components.MascotStateKey
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeKind
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeLevels
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeScore
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.KidsChallengeController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * v38 çocuk modunda çizim görevinin puanı: hedef çizgilerin üstünden çizilen resim yüksek, boş ya da alakasız çizim düşük
 * puan alır; yarım çizim arada kalır; başka yere çizilen doğru resim de sayılır; boyama görevinde renkler, yerleştir ve
 * boyada ikisi birden ölçülür. Seviyelerin hedefleri kâğıt boyundan bağımsızdır.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V38ChallengeScoreTest {
    @After fun tearDown() = stopKoin()

    private val levels = ChallengeLevels.all

    private fun ink(lines: List<List<Offset>>, start: Int = 0): List<Stroke> = lines.mapIndexed { i, line ->
        Stroke("ink-${start + i}", line.map { StrokePoint(it.x, it.y) }, "#212121", BrushType.MARKER, 8f, 1f, start + i)
    }

    private fun eraser(line: List<Offset>, size: Float, order: Int) =
        Stroke("rub-$order", line.map { StrokePoint(it.x, it.y) }, "#FFFFFF", BrushType.ERASER, size, 1f, order)

    private fun shifted(lines: List<List<Offset>>, d: Offset) = lines.map { l -> l.map { it + d } }

    private fun jitter(lines: List<List<Offset>>, amount: Float, seed: Long): List<List<Offset>> {
        val rnd = java.util.Random(seed)
        return lines.map { l -> l.map { it + Offset((rnd.nextFloat() - 0.5f) * 2 * amount, (rnd.nextFloat() - 0.5f) * 2 * amount) } }
    }

    @Test
    fun fiftyLevelsEasyToHard() {
        // v40: 50 seviye; ilk 15'in kimlikleri ve sırası v38'deki gibi (eski puanlar aynı seviyelerde kalır)
        assertEquals(50, levels.size)
        assertEquals((1..50).toList(), levels.map { it.number })
        assertEquals(
            listOf(
                "sun.trace", "apple.trace", "house.paint", "kite.trace", "balloons.paint", "ice_cream.trace", "flower.build",
                "fish.trace", "mushroom.paint", "umbrella.trace", "car.build", "cat.trace", "rocket.paint", "butterfly.trace",
                "castle.build",
            ),
            levels.take(15).map { it.id },
        )
        assertEquals("kimlikler tekil", levels.size, levels.map { it.id }.toSet().size)
        // Hedefler uygulamanın kendi çocuk şekillerinden
        assertTrue(levels.all { it.shape.startsWith("lib.kid_") })
        assertEquals(ChallengeKind.TRACE, levels[0].kind)
        assertTrue("boyama ve yerleştir seviyeleri var", levels.count { it.kind == ChallengeKind.PAINT } >= 3 && levels.count { it.kind == ChallengeKind.BUILD } >= 2)
        // Çizgi sayısı ilerledikçe artar (ilk seviye tek daire, sonuncular çok parçalı)
        val first = ChallengeLevels.target(levels[0], 1000, 1000)
        // v47 6–9 yaş: ilk seviyede de üç çizgi
        assertEquals(3, first.lines.size)
        assertTrue(ChallengeLevels.target(levels[14], 1000, 1000).lines.size >= 5)
        for (level in levels) {
            val t = ChallengeLevels.target(level, 1000, 1000)
            if (level.kind == ChallengeKind.PAINT) assertTrue(level.id, t.lines.isEmpty()) else assertEquals(level.id, level.ranks.size, t.lines.size)
            assertEquals(level.id, level.paints.size, t.probes.size)
            assertTrue(level.id, level.tools.isNotEmpty())
            // Hedef kâğıdın içinde
            t.lines.flatten().forEach { p -> assertTrue(level.id, p.x in 0f..1000f && p.y in 0f..1000f) }
        }
    }

    @Test
    fun exactTraceScoresFullAndStarsFollowThresholds() {
        for (level in levels.filter { it.kind == ChallengeKind.TRACE }) {
            val t = ChallengeLevels.target(level, 1000, 1000)
            val score = KidsChallengeController.evaluate(t, ink(t.lines)).score
            assertTrue("${level.id}: $score", score >= 98)
        }
        assertEquals(0, ChallengeScore.stars(59))
        assertEquals(1, ChallengeScore.stars(60))
        assertEquals(1, ChallengeScore.stars(74))
        assertEquals(2, ChallengeScore.stars(75))
        assertEquals(2, ChallengeScore.stars(89))
        assertEquals(3, ChallengeScore.stars(90))
        assertFalse(ChallengeScore.passed(59))
        assertTrue(ChallengeScore.passed(60))
    }

    @Test
    fun shakyTraceStillHighShiftedTraceCounts() {
        val t = ChallengeLevels.target(levels[1], 1000, 1000)
        val shaky = KidsChallengeController.evaluate(t, ink(jitter(t.lines, 8f, 3))).score
        assertTrue("titrek çizim $shaky", shaky >= 85)
        // Aynı resim başka yerde: kutusu uydurulur (%95'i)
        val moved = KidsChallengeController.evaluate(t, ink(shifted(t.lines, Offset(180f, -120f)))).score
        assertTrue("kaydırılmış $moved", moved in 85..96)
    }

    @Test
    fun emptyWrongAndPartialDrawings() {
        val t = ChallengeLevels.target(levels[3], 1000, 1000)
        assertEquals(0, KidsChallengeController.evaluate(t, emptyList()).score)
        assertEquals(0, ChallengeScore.outline(emptyList(), t.lines))
        // Kâğıdın köşesinde düz bir çizgi
        val line = listOf(listOf(Offset(40f, 960f), Offset(400f, 960f)))
        val wrong = KidsChallengeController.evaluate(t, ink(line)).score
        assertTrue("alakasız $wrong", wrong < 35)
        // Kâğıdı karalamak: zikzak
        val zig = listOf((0..40).map { i -> Offset(100f + i * 20f, if (i % 2 == 0) 100f else 900f) })
        val scribble = KidsChallengeController.evaluate(t, ink(zig)).score
        assertTrue("karalama $scribble", scribble < 35)
        // Üstünden iki kez geçmek cezalandırılmaz
        val twice = KidsChallengeController.evaluate(t, ink(t.lines + jitter(t.lines, 4f, 9), 100)).score
        assertTrue("iki kez $twice", twice >= 90)
        // Yarısı çizilmiş (v47 çizgilerin uzunluğunun yarısı: ilk çizgiler büyük parçalar, sondakiler küçük ayrıntılar)
        fun len(l: List<Offset>) = l.zipWithNext { a, b -> (b - a).getDistance() }.sum()
        val total = t.lines.sumOf { len(it).toDouble() }
        var acc = 0.0
        val half = t.lines.takeWhile { acc += len(it); acc <= total * 0.55 }.ifEmpty { t.lines.take(1) }
        val partial = KidsChallengeController.evaluate(t, ink(half)).score
        val full = KidsChallengeController.evaluate(t, ink(t.lines)).score
        assertTrue("yarım $partial / tam $full", partial in 30 until full && partial < 90)
    }

    @Test
    fun erasedInkIsNotCounted() {
        val t = ChallengeLevels.target(levels[0], 1000, 1000)
        val full = KidsChallengeController.evaluate(t, ink(t.lines)).score
        // En büyük parçanın üst yarısı silinir (v47 güneşte üç parça: kutu en büyük parçanın kutusu)
        val circle = t.lines.first()
        val box = androidx.compose.ui.geometry.Rect(circle.minOf { it.x }, circle.minOf { it.y }, circle.maxOf { it.x }, circle.maxOf { it.y })
        val rub = eraser((0..20).map { Offset(box.left - 20f + it * (box.width + 40f) / 20f, box.top + box.height * 0.22f) }, box.height * 0.5f, 5)
        val erased = KidsChallengeController.evaluate(t, ink(t.lines) + rub).score
        // v47 güneşte üç parça: en büyük parçanın yarısı silinince puan belirgin düşer
        assertTrue("silinen $erased < $full", erased < full - 10)
        assertTrue(KidsChallengeController.drawnLines(ink(t.lines) + rub).size >= 1)
    }

    @Test
    fun colorCreditAndPaintScore() {
        assertEquals(1f, ChallengeScore.paintCredit("#E53935", "#E53935"), 1e-4f)
        assertEquals(0f, ChallengeScore.paintCredit("#E53935", null), 0f)
        assertEquals(0f, ChallengeScore.paintCredit("#E53935", "#FFFFFF"), 0f)
        // Yakın renk (başka kırmızı) yüksek, bambaşka renk yalnız emek payı
        assertTrue(ChallengeScore.paintCredit("#E53935", "#F44336") > 0.8f)
        assertEquals(ChallengeScore.PAINTED, ChallengeScore.paintCredit("#E53935", "#1E88E5"), 1e-4f)
        assertEquals(100, ChallengeScore.paint(listOf("#E53935" to "#E53935", "#1E88E5" to "#1E88E5")))
        assertEquals(50, ChallengeScore.paint(listOf("#E53935" to "#E53935", "#1E88E5" to null)))
        assertEquals(0, ChallengeScore.paint(emptyList()))
        assertEquals(76, ChallengeScore.combine(40, 100))
    }

    @Test
    fun paintLevelMeasuresColorsAtEachSpot() {
        val level = levels[2]
        assertEquals(ChallengeKind.PAINT, level.kind)
        val t = ChallengeLevels.target(level, 1000, 1000)
        val plain = ChallengeLevels.build(level, t.box, "house")!!
        val good = ChallengeLevels.build(level, t.box, "house", fills = ChallengeLevels.fills(level))!!
        assertEquals(0, KidsChallengeController.evaluate(t, listOf(plain)).score)
        val eval = KidsChallengeController.evaluate(t, listOf(good))
        assertEquals(100, eval.score)
        assertTrue(eval.marks.size == 3 && eval.marks.all { it.done })
        // Hepsi yanlış renk: yalnız emek payı
        val wrong = ChallengeLevels.build(level, t.box, "house", fills = ChallengeLevels.fills(level).mapValues { "#00ACC1" })!!
        assertEquals(30, KidsChallengeController.evaluate(t, listOf(wrong)).score)
    }

    @Test
    fun buildLevelNeedsShapeOnTargetAndColors() {
        val level = levels[6]
        assertEquals(ChallengeKind.BUILD, level.kind)
        val t = ChallengeLevels.target(level, 1000, 1000)
        assertEquals(0, KidsChallengeController.evaluate(t, emptyList()).score)
        val fills = ChallengeLevels.fills(level)
        val exact = ChallengeLevels.build(level, t.box, "f", fills = fills)!!
        // Şeklin hedefte olmayan küçük çizgileri isabeti çok az düşürür
        assertTrue(KidsChallengeController.evaluate(t, listOf(exact)).score >= 97)
        // Kesikli çizgiye konmamış ama boyanmış: çizgi payı %95
        val away = ChallengeLevels.build(level, t.box.translate(Offset(150f, 160f)), "f", fills = fills)!!
        val awayScore = KidsChallengeController.evaluate(t, listOf(away)).score
        assertTrue("başka yerde $awayScore", awayScore in 90..99)
        // Yerinde ama boyanmamış: yalnız çizgi payı
        val plain = ChallengeLevels.build(level, t.box, "f")!!
        val plainScore = KidsChallengeController.evaluate(t, listOf(plain)).score
        assertTrue("boyanmamış $plainScore", plainScore in 37..40)
    }

    @Test
    fun targetsDoNotDependOnPaperSize() {
        for (level in levels) {
            val a = ChallengeLevels.target(level, 1000, 1000)
            val b = ChallengeLevels.target(level, 1600, 900)
            assertEquals(level.id, a.lines.size, b.lines.size)
            // Boyanacak yerler her kâğıtta doğru parçanın içinde: örnek renklerle boyanan şekilde beklenen renk görünür
            val shape = ChallengeLevels.build(level, b.box, "x", fills = ChallengeLevels.fills(level))!!
            val paths = ShapeFactory.subPaths(shape)
            for ((at, color) in b.spots()) {
                assertEquals("${level.id} $at", color.uppercase(), KidsChallengeController.colorAt(shape, paths, at)?.uppercase())
            }
            // Kutunun oranı şeklinkiyle aynı, kâğıdın ortasında
            assertEquals(800f, b.box.center.x, 0.5f)
            assertEquals(450f, b.box.center.y, 0.5f)
        }
    }
}

/**
 * v38 (4, 5, 5.1) çocuk modunda çizim görevi: üst menüdeki "Çizim Görevi" düğmesi (Başlat / İlerleme; mod açıkken Modu
 * kapat / İlerleme), seviyenin hedefi, maskotun ipuçları, Bitti'de değerlendirme (başarı ve tekrar dene ekranları),
 * İlerleme'deki işaretler ve seviyeye geçiş, kalıcı ilerleme.
 */
abstract class V38ChallengeBase : V37KidsBase() {
    protected fun ctl() = vm().challenge

    protected fun stateOf(tag: String): String? =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    protected fun textOf(tag: String): String =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
            ?.joinToString { it.text }.orEmpty()

    protected fun strokes() = vm().canvas.value!!.strokes

    /** Maskotun o anki yüzü/el hareketi (sonuç penceresindeki gibi [tag] etiketli maskotun). */
    protected fun poseOf(tag: String): String =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrElse(MascotStateKey) { "" }

    protected fun challengeTool() {
        compose.onNodeWithTag("simple_tool_challenge").performScrollTo().performClick()
        compose.waitForIdle()
    }

    protected fun openMenu() {
        challengeTool()
        waitFor { exists("challenge_menu") }
    }

    protected fun startChallenge() {
        openMenu()
        compose.onNodeWithTag("challenge_start").performClick()
        waitFor { ctl().active.value && exists("challenge_card") && exists("challenge_mascot") }
    }

    protected fun openProgress() {
        openMenu()
        compose.onNodeWithTag("challenge_progress").performClick()
        waitFor { exists("challenge_progress_sheet") }
    }

    /** Maskotun balonu açıksa kapatılır (çizimin önünü kapatmasın). */
    protected fun quiet() {
        if (exists("challenge_mascot_close")) compose.onNodeWithTag("challenge_mascot_close").performClick()
        compose.waitForIdle()
    }

    /** Tuval noktası → ekran (kâğıt ekrana sığdırılmışken). */
    protected fun mapper(): (Offset) -> Offset {
        val a = screenOf(0f, 0f)
        val b = screenOf(1000f, 1000f)
        val s = (b.x - a.x) / 1000f
        return { p -> Offset(a.x + p.x * s, a.y + p.y * s) }
    }

    /** Çizgiler fırçayla parmakla çizilir. */
    protected fun trace(lines: List<List<Offset>>) {
        val map = mapper()
        lines.forEach { line ->
            compose.onNodeWithTag("drawing_canvas").performTouchInput {
                down(map(line[0]))
                line.drop(1).forEach { moveTo(map(it)) }
                up()
            }
            compose.waitForIdle()
        }
    }

    protected fun scribble() = trace(listOf(listOf(Offset(80f, 930f), Offset(300f, 930f), Offset(320f, 900f))))

    protected fun finishLevel() {
        compose.onNodeWithTag("challenge_finish").performClick()
        waitFor { ctl().result.value != null && exists("challenge_result") }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V38KidsChallengeTest : V38ChallengeBase() {
    @Test
    fun buttonMenuStartsAndStopsTheMode() {
        openKids()
        assertTrue(exists("simple_tool_challenge"))
        // Mod kapalı: Başlat ve İlerleme
        openMenu()
        assertEquals("idle", stateOf("challenge_menu"))
        assertTrue(exists("challenge_start") && exists("challenge_progress"))
        assertFalse(exists("challenge_stop"))
        shot("v38-challenge-menu-idle")
        // Karartılmış zemine dokununca menü kapanır
        compose.onNodeWithTag("challenge_menu_scrim").performTouchInput { click(Offset(width / 2f, height - 40f)) }
        waitFor { !exists("challenge_menu") }
        assertFalse(ctl().active.value)

        startChallenge()
        assertEquals(0, ctl().run.value!!.index)
        assertFalse(exists("challenge_menu"))
        assertEquals("Seviye 1 / 50", textOf("challenge_card_level"))
        assertEquals("3/0", stateOf("challenge_guide"))
        // v37 köşe maskotu yerine görevin maskotu
        assertFalse(exists("kids_mascot_toggle"))
        // Mod açık: Modu kapat ve İlerleme
        openMenu()
        assertEquals("running", stateOf("challenge_menu"))
        assertTrue(exists("challenge_stop") && exists("challenge_progress"))
        assertFalse(exists("challenge_start"))
        shot("v38-challenge-menu-running")
        compose.onNodeWithTag("challenge_stop").performClick()
        waitFor { !ctl().active.value }
        waitFor { !exists("challenge_card") && !exists("challenge_guide") && !exists("challenge_mascot") }
        assertTrue(exists("kids_mascot_toggle"))
    }

    @Test
    fun levelShowsTargetAndMascotGivesToolTips() {
        openKids()
        startChallenge()
        // Seviye başında görevi anlatır
        assertEquals("intro", stateOf("challenge_mascot"))
        assertTrue(mascotState(), mascotState().startsWith("EXPLAINING/"))
        tick(4000)
        assertTrue(balloonText(), balloonText().contains("1. seviye") && balloonText().contains("Güneş"))
        shot("v38-challenge-level1")
        // İpucu düğmesi sırayla seviyenin araçlarını anlatır
        compose.onNodeWithTag("challenge_mascot_tip").performClick()
        compose.waitForIdle()
        assertEquals("tip_brush", stateOf("challenge_mascot"))
        assertTrue(mascotState().startsWith("HAPPY/BRUSH"))
        tick(4000)
        // v45 çizim aracının çocuk menüsündeki adı Kalem (Boya artık boya fırçası)
        assertTrue(balloonText(), balloonText().contains("Kalem"))
        compose.onNodeWithTag("challenge_mascot_tip").performClick()
        compose.waitForIdle()
        assertEquals("tip_undo", stateOf("challenge_mascot"))
        compose.onNodeWithTag("challenge_mascot_tip").performClick()
        compose.waitForIdle()
        assertEquals("tip_finish", stateOf("challenge_mascot"))
        // Kapat: balon kapanır, maskot puana göre yüzle durur (henüz çizim yok)
        quiet()
        assertEquals("idle", stateOf("challenge_mascot"))
        assertFalse(exists("kids_mascot_balloon"))
        assertTrue(mascotState(), mascotState().startsWith("HAPPY/WAVE"))
        // Maskota dokununca da ipucu söyler
        compose.onNodeWithTag("kids_mascot").performClick()
        compose.waitForIdle()
        assertEquals("tip_brush", stateOf("challenge_mascot"))
    }

    @Test
    fun goodDrawingPassesWithStarsThenNextLevel() {
        openKids()
        startChallenge()
        quiet()
        val target = ctl().run.value!!.target
        trace(target.lines)
        // Hedefe yaklaşınca maskot sevinir ve Bitti'yi hatırlatır
        waitFor { (ctl().live.value?.score ?: 0) >= ChallengeScore.PASS }
        waitFor { stateOf("challenge_mascot") == "close" }
        assertTrue(mascotState(), mascotState().startsWith("EXCITED/CHEER"))
        tick(3000)
        shot("v38-challenge-traced")
        val drawn = strokes()
        assertTrue(drawn.isNotEmpty())
        finishLevel()
        val r = ctl().result.value!!
        assertTrue("puan ${r.score}", r.passed && r.score >= 75)
        assertEquals(ChallengeScore.stars(r.score), r.stars)
        assertEquals("passed", stateOf("challenge_result"))
        assertEquals("Seviye geçildi!", textOf("challenge_result_title"))
        assertEquals(r.stars.toString(), stateOf("challenge_result_stars"))
        assertTrue(exists("challenge_next") && exists("challenge_exit"))
        assertFalse(exists("challenge_retry"))
        assertTrue(poseOf("challenge_result_mascot").let { it.startsWith("PROUD/CHEER") || it.startsWith("EXCITED/CHEER") })
        tick(4000)
        shotAll("v38-challenge-passed")
        // Sonraki seviye: tuval temizlenir (geri alınabilir), 2. seviye kurulur
        compose.onNodeWithTag("challenge_next").performClick()
        waitFor { ctl().run.value?.index == 1 && ctl().result.value == null && !exists("challenge_result") }
        assertTrue(strokes().isEmpty())
        assertEquals("Seviye 2 / 50", textOf("challenge_card_level"))
        assertEquals("4/0", stateOf("challenge_guide"))
        assertEquals(EditMode.DRAW, vm().mode.value)
        assertTrue(ctl().passed(0) && ctl().reachable(1) && !ctl().reachable(2))
        compose.onNodeWithContentDescription("Geri al").performClick()
        waitFor { strokes().map { it.id } == drawn.map { it.id } }
        assertTrue("mod sürer", ctl().active.value)
    }

    @Test
    fun poorDrawingEncouragesRetryAndExitKeepsDrawing() {
        openKids()
        startChallenge()
        quiet()
        scribble()
        finishLevel()
        val r = ctl().result.value!!
        assertFalse("puan ${r.score}", r.passed)
        assertTrue(r.score < ChallengeScore.PASS)
        assertEquals("failed", stateOf("challenge_result"))
        assertEquals("Az kaldı!", textOf("challenge_result_title"))
        assertEquals(r.stars.toString(), stateOf("challenge_result_stars"))
        assertTrue(exists("challenge_retry") && exists("challenge_exit"))
        assertFalse(exists("challenge_next"))
        assertTrue(poseOf("challenge_result_mascot").startsWith("HAPPY/THUMBS_UP"))
        tick(5000)
        assertTrue(compose.onNodeWithTag("challenge_result_say").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)!!.joinToString().contains("60 puan"))
        shotAll("v38-challenge-failed")
        // Tekrar dene: aynı seviye baştan, tuval temiz
        val seq = ctl().run.value!!.seq
        compose.onNodeWithTag("challenge_retry").performClick()
        waitFor { ctl().result.value == null && (ctl().run.value?.seq ?: 0) > seq }
        assertEquals(0, ctl().run.value!!.index)
        assertTrue(strokes().isEmpty())
        assertFalse("geçilmedi", ctl().passed(0))
        // Yeniden zayıf çizim, Moddan çık: mod kapanır, çizim kalır
        quiet()
        scribble()
        finishLevel()
        compose.onNodeWithTag("challenge_exit").performClick()
        waitFor { !ctl().active.value && !exists("challenge_result") && !exists("challenge_card") }
        assertTrue(strokes().isNotEmpty())
    }

    @Test
    fun paintLevelShowsColorSpotsAndScoresColors() {
        openKids()
        kidsPrefs().recordChallenge(ChallengeLevels.all[0].id, 80)
        kidsPrefs().recordChallenge(ChallengeLevels.all[1].id, 80)
        compose.runOnIdle { assertTrue(ctl().open(2)) }
        waitFor { exists("challenge_card") }
        // Boyanacak ev kâğıda kondu; renkli noktalar görünür
        val house = strokes().single { it.shape?.kind == "lib.kid_house" }
        assertEquals("0/3", stateOf("challenge_guide"))
        tick(3000)
        shot("v38-challenge-paint-level")
        val spots = ctl().run.value!!.target.spots()
        val map = mapper()
        val names = mapOf("#FDD835" to "Sarı", "#E53935" to "Kırmızı", "#8D6E63" to "Kahverengi")
        pickPaint(names.getValue(spots.first().second))
        spots.forEachIndexed { i, (at, color) ->
            quiet()
            if (i > 0) compose.onNodeWithContentDescription(names.getValue(color)).performClick()
            tap(map(at))
            waitFor { ctl().live.value?.marks?.getOrNull(i)?.done == true }
        }
        assertEquals(house.id, strokes().single { it.shape?.kind == "lib.kid_house" }.id)
        waitFor { ctl().live.value?.score == 100 }
        quiet()
        shot("v38-challenge-painted")
        finishLevel()
        val r = ctl().result.value!!
        assertEquals(100, r.score)
        assertEquals(3, r.stars)
    }

    @Test
    fun progressMarksPassedOpenLockedAndOpensReachableLevel() {
        openKids()
        kidsPrefs().recordChallenge(ChallengeLevels.all[0].id, 95)
        kidsPrefs().recordChallenge(ChallengeLevels.all[1].id, 70)
        // Önce bir çizim
        wobblyCircleAt(screenOf(500f, 500f), 120f, 7)
        val before = strokes()
        assertEquals(1, before.size)
        openProgress()
        assertEquals("Geçildi", stateOf("challenge_level_1"))
        assertEquals("Geçildi", stateOf("challenge_level_2"))
        assertEquals("Açık", stateOf("challenge_level_3"))
        assertEquals("Kilitli", stateOf("challenge_level_4"))
        assertEquals("Kilitli", stateOf("challenge_level_15"))
        assertTrue(textOf("challenge_progress_note"), textOf("challenge_progress_note").startsWith("2 / 50"))
        // v40 Premium kullanıcıda son seviyeler de sıra kilidiyle kilitli (taç yok)
        assertEquals("Kilitli", stateOf("challenge_level_50"))
        assertFalse(exists("challenge_premium_note"))
        shotAll("v38-challenge-progress")
        // Kilitli seviye açılmaz; maskot önce geçilmesi gerekeni söyler
        compose.onNodeWithTag("challenge_level_4").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(textOf("challenge_progress_note"), textOf("challenge_progress_note").contains("3. seviye"))
        assertFalse(ctl().active.value)
        assertTrue(exists("challenge_progress_sheet"))
        compose.runOnIdle { assertFalse(ctl().open(3)) }
        assertFalse(ctl().active.value)
        // Açık seviye: mod açılır, tuval tek adımda temizlenir, seviye kurulur
        compose.onNodeWithTag("challenge_level_3").performScrollTo().performClick()
        waitFor { ctl().active.value && ctl().run.value?.index == 2 && !exists("challenge_progress_sheet") }
        waitFor { exists("challenge_card") }
        val setup = strokes()
        assertEquals(listOf("lib.kid_house"), setup.map { it.shape?.kind })
        assertEquals(EditMode.DRAW, vm().mode.value)
        compose.onNodeWithContentDescription("Geri al").performClick()
        waitFor { strokes().map { it.id } == before.map { it.id } }
        // Mod açıkken İlerleme'den başka seviyeye geçilir (devam)
        val seq = ctl().run.value!!.seq
        openProgress()
        compose.onNodeWithTag("challenge_level_1").performScrollTo().performClick()
        waitFor { ctl().run.value?.index == 0 && ctl().run.value!!.seq > seq }
        assertTrue(strokes().isEmpty())
        assertTrue(ctl().active.value)
    }

    @Test
    fun progressPersistsAcrossRestarts() {
        openKids()
        startChallenge()
        quiet()
        trace(ctl().run.value!!.target.lines)
        finishLevel()
        val score = ctl().result.value!!.score
        val id = ChallengeLevels.all[0].id
        // Yeni bir tercih nesnesi (uygulama yeniden açılmış gibi) kayıtlı puanı okur
        val fresh = KidsPrefs(ApplicationProvider.getApplicationContext())
        assertEquals(score, fresh.challengeBest.value[id])
        // En iyi puan kalır: daha düşük puan üstüne yazılmaz
        assertEquals(score, fresh.recordChallenge(id, 10))
        assertEquals(score, KidsPrefs(ApplicationProvider.getApplicationContext()).challengeBest.value[id])
        assertTrue(ctl().reachable(1))
        // Mod kapanıp açılınca sıradaki (geçilmemiş ilk) seviyeden başlar
        compose.onNodeWithTag("challenge_exit").performClick()
        waitFor { !ctl().active.value }
        startChallenge()
        assertEquals(1, ctl().run.value!!.index)
    }

    @Test
    fun buildLevelShowsDashedTargetAndSpots() {
        openKids()
        ChallengeLevels.all.take(6).forEach { kidsPrefs().recordChallenge(it.id, 90) }
        compose.runOnIdle { assertTrue(ctl().open(6)) }
        waitFor { exists("challenge_card") }
        assertTrue(strokes().isEmpty())
        assertEquals("9/8", stateOf("challenge_guide"))
        tick(3000)
        shot("v38-challenge-build-level")
        // Şekil Şekil ekle'den eklenir ve yerine konur
        addKidShape("lib.kid_flower")
        waitFor { ctl().live.value?.drawn == true }
        val eval = ctl().live.value!!
        assertTrue("yalnız çizgi payı ${eval.score}", eval.score in 30..40)
    }
}

/** v38 küçük telefonda (360×640) görev ekranları: kart, maskot ve pencerelerin düğmeleri ekrana sığar. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w360dp-h640dp-xhdpi")
class V38KidsChallengeSmallTest : V38ChallengeBase() {
    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun inside(tag: String) {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val b = bounds(tag)
        assertTrue("$tag $b / $root", b.left >= root.left - 1 && b.right <= root.right + 1 && b.top >= root.top - 1 && b.bottom <= root.bottom + 1)
    }

    @Test
    fun smallPhoneScreensFit() {
        openKids()
        startChallenge()
        tick(4000)
        shot("v38-small-level1")
        inside("challenge_card")
        inside("challenge_finish")
        inside("kids_mascot")
        // Kart hedefin üstünü kapatmaz
        val top = screenOf(0f, ctl().run.value!!.target.box.top).y
        val canvasTop = compose.onNodeWithTag("drawing_canvas").fetchSemanticsNode().boundsInRoot.top
        assertTrue("kart ${bounds("challenge_card").bottom} hedef ${canvasTop + top}", bounds("challenge_card").bottom <= canvasTop + top)
        quiet()
        scribble()
        finishLevel()
        tick(5000)
        shotAll("v38-small-failed")
        assertTrue(exists("challenge_retry") && exists("challenge_exit"))
        compose.onNodeWithTag("challenge_retry").performClick()
        waitFor { ctl().result.value == null }
        openProgress()
        shotAll("v38-small-progress")
        assertTrue(compose.onAllNodesWithTag("challenge_level_15").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithTag("challenge_level_50").fetchSemanticsNodes().isNotEmpty())
        assertNull(ctl().result.value)
        assertNotNull(ctl().run.value)
    }
}
