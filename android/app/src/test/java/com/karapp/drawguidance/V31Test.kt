package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.vector.P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** v31 düzenleyici testleri için ortak yardımcılar. */
abstract class V31EditorBase : V30EditorBase() {
    /** Köşelerden geçen titrek kapalı çizgi (ekran koordinatı). */
    protected fun roughPoly(corners: List<Offset>, seed: Long, closed: Boolean = true, amp: Float = 2.2f): List<P> {
        val rnd = java.util.Random(seed)
        val pts = if (closed) corners + corners.first() else corners
        val out = ArrayList<P>()
        for (i in 0 until pts.size - 1) {
            val a = pts[i]; val b = pts[i + 1]
            for (k in 0 until 14) {
                val t = k / 14f
                out += P(a.x + (b.x - a.x) * t + (rnd.nextFloat() - 0.5f) * amp, a.y + (b.y - a.y) * t + (rnd.nextFloat() - 0.5f) * amp)
            }
        }
        out += P(pts.last().x, pts.last().y)
        return out
    }

    protected fun roughCircle(c: Offset, r: Float, seed: Long): List<P> {
        val rnd = java.util.Random(seed)
        return (0..72).map { i ->
            val a = i / 72.0 * 2 * PI * 1.03
            val rr = r * (1f + (rnd.nextFloat() - 0.5f) * 0.08f)
            P(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat())
        }
    }

    protected fun square(c: Offset, h: Float) = listOf(Offset(c.x - h, c.y - h), Offset(c.x + h, c.y - h), Offset(c.x + h, c.y + h), Offset(c.x - h, c.y + h))
    protected fun triangle(c: Offset, h: Float) = listOf(Offset(c.x, c.y - h), Offset(c.x + h, c.y + h), Offset(c.x - h, c.y + h))

    /** Gerçek parmak dokunuşu (anlam ağacı tıklaması değil: üstte duran bir katman dokunuşu yutuyorsa yakalanır). */
    protected fun touch(tag: String) {
        // Balondaki seçenekler yatay kayar: önce görünür alana getirilir
        compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { click(center) }
        compose.waitForIdle()
    }
}

/**
 * v31 (4) çoklu seçimde "Tümünü düzelt": çok eleman (beş ayrı çizim) seçilip seçim çubuğundaki "Çoklu düzeltme öner"
 * ile açılan önerideki "Tümünü düzelt" gerçek dokunuşla tüm çizimleri düzgün şekle çevirir (tek geri alma adımı).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V31FixAllTest : V31EditorBase() {
    internal fun drawFive(): List<Offset> {
        val size = canvasSize()
        prefs().setHandwriting(false)
        val r = size.x * 0.1f
        val c1 = Offset(size.x * 0.25f, size.y * 0.22f); val c2 = Offset(size.x * 0.72f, size.y * 0.22f)
        val c3 = Offset(size.x * 0.25f, size.y * 0.5f); val c4 = Offset(size.x * 0.72f, size.y * 0.5f)
        drawStrokes(listOf(
            roughCircle(c1, r, 1), roughCircle(c2, r * 0.8f, 2), roughPoly(square(c3, r), 3), roughPoly(triangle(c4, r), 4),
            roughPoly(listOf(Offset(size.x * 0.15f, size.y * 0.78f), Offset(size.x * 0.85f, size.y * 0.78f)), 5, closed = false, amp = 4f),
        ))
        waitFor { canvas().strokes.size == 5 }
        prefs().setHandwriting(true)
        // Her çizimin üzerindeki bir nokta (seçmek için)
        return listOf(Offset(c1.x + r, c1.y), Offset(c2.x + r * 0.8f, c2.y), Offset(c3.x - r, c3.y), Offset(c4.x, c4.y + r), Offset(size.x * 0.5f, size.y * 0.78f))
    }

    internal fun selectAll(points: List<Offset>) {
        mode("Seç")
        points.forEach { tap(it); advanceMain(300) }
        waitForText("${points.size} eleman seçili", substring = true)
    }

    @Test
    fun fixAllFromSelectionBar() {
        createCanvasAndOpen("Hepsi")
        val points = drawFive()
        val before = canvas().strokes
        selectAll(points)
        waitFor { compose.onAllNodesWithText("Çoklu düzeltme öner").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Çoklu düzeltme öner").performClick()
        waitFor { exists("line_fix_option") }
        val label = compose.onAllNodes(hasTestTag("line_fix_option")).fetchSemanticsNodes().size
        assertEquals(1, label)
        touch("line_fix_option")
        waitFor { canvas().strokes.count { s -> before.first { it.id == s.id }.points != s.points } >= 4 }
        assertEquals(before.map { it.id }, canvas().strokes.map { it.id })
    }

    /** Geometrisi tanınmayan serbest çizimler de düzelir (yumuşatılır): seçimdeki tüm çizimler değişir. */
    @Test
    fun fixAllIncludesFreeformDrawings() {
        createCanvasAndOpen("Serbest")
        val size = canvasSize()
        prefs().setHandwriting(false)
        val strokes = ArrayList<List<P>>(); val pts = ArrayList<Offset>()
        for (k in 0 until 4) {
            val x0 = size.x * 0.15f; val y0 = size.y * (0.18f + 0.17f * k)
            val rnd = java.util.Random(40L + k)
            strokes += (0..80).map { i ->
                val t = i / 80f
                P(x0 + t * size.x * 0.6f + (rnd.nextFloat() - 0.5f) * 3f, y0 + size.y * 0.05f * sin(t * 6.28f * 1.5f) + (rnd.nextFloat() - 0.5f) * 3f)
            }
            pts += Offset(strokes.last()[0].x, strokes.last()[0].y)
        }
        drawStrokes(strokes)
        waitFor { canvas().strokes.size == 4 }
        prefs().setHandwriting(true)
        val before = canvas().strokes
        selectAll(pts)
        waitFor { compose.onAllNodesWithText("Çoklu düzeltme öner").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Çoklu düzeltme öner").performClick()
        waitFor { compose.onAllNodesWithText("Çizgileri düzelt (4)").fetchSemanticsNodes().isNotEmpty() }
        touch("line_fix_option")
        waitFor { canvas().strokes.all { s -> before.first { it.id == s.id }.points != s.points } }
        // Tek geri alma adımı
        compose.onNodeWithTag("drawing_canvas").assertExists()
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Geri al")).performClick()
        waitFor { canvas().strokes.map { it.points } == before.map { it.points } }
    }

    @Test
    fun fixAllFromAutomaticSelectionSuggestion() {
        createCanvasAndOpen("Hepsi2")
        val points = drawFive()
        val before = canvas().strokes
        selectAll(points)
        advanceMain(800)
        waitFor { exists("line_fix_option") }
        touch("line_fix_option")
        waitFor { canvas().strokes.count { s -> before.first { it.id == s.id }.points != s.points } >= 4 }
    }
}

/**
 * v31 (4) öneri balonu üstteki yakınlık göstergesinin ve alttaki mod çubuğunun altında kalmaz (önceden seçim ekranın
 * üstüne ya da altına yakınken balonun bir kısmı bu düğmelerin altında kalıyor, "Tümünü düzelt"e dokununca yakınlık
 * düğmesi çalışıyordu).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V31BalloonPlacementTest : V31EditorBase() {
    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun check(at: List<Offset>, name: String, pan: Float = 0f) {
        mode("Seç")
        at.forEach { tap(it); advanceMain(250) }
        waitForText("${at.size} eleman seçili", substring = true)
        // Seç'te boş yerde sürükleme tuvali kaydırır: seçim ekranın altına (mod çubuğunun hizasına) iner
        if (pan != 0f) { val size = canvasSize(); drag(Offset(size.x * 0.5f, size.y * if (pan > 0f) 0.08f else 0.6f), Offset(0f, pan)); advanceMain(300) }
        waitFor { compose.onAllNodesWithText("Çoklu düzeltme öner").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Çoklu düzeltme öner").performClick()
        waitFor { exists("line_fix_option") }
        val chip = bounds("line_suggest_chip")
        val zoom = bounds("zoom_chip")
        val bar = bounds("mode_bar")
        println("$name: balon $chip, yakınlık $zoom, mod çubuğu $bar")
        shot("v31-balloon-$name")
        assertTrue("balon yakınlık göstergesinin altında", !chip.overlaps(zoom))
        assertTrue("balon mod çubuğunun altında", chip.bottom <= bar.top + 1f)
        val before = canvas().strokes
        touch("line_fix_option")
        waitFor { canvas().strokes.count { s -> before.first { it.id == s.id }.points != s.points } >= 2 }
    }

    @Test
    fun balloonClearOfZoomChipNearTop() {
        createCanvasAndOpen("Üst")
        val size = canvasSize()
        prefs().setHandwriting(false)
        val r = size.x * 0.06f
        val y = size.y * 0.35f
        val cs = listOf(Offset(size.x * 0.2f, y), Offset(size.x * 0.5f, y), Offset(size.x * 0.82f, y))
        drawStrokes(cs.mapIndexed { i, c -> roughCircle(c, r, 10L + i) })
        waitFor { canvas().strokes.size == 3 }
        prefs().setHandwriting(true)
        // Seçimden sonra tuval yukarı kaydırılır: seçim yakınlık göstergesinin hizasına çıkar
        check(cs.map { Offset(it.x, it.y + r) }, "ust", pan = -size.y * 0.3f)
    }

    @Test
    fun balloonClearOfModeBarNearBottom() {
        createCanvasAndOpen("Alt")
        val size = canvasSize()
        prefs().setHandwriting(false)
        val r = size.x * 0.06f
        val y = size.y * 0.42f
        val cs = listOf(Offset(size.x * 0.25f, y), Offset(size.x * 0.55f, y), Offset(size.x * 0.8f, y))
        drawStrokes(cs.mapIndexed { i, c -> roughCircle(c, r, 20L + i) })
        waitFor { canvas().strokes.size == 3 }
        prefs().setHandwriting(true)
        check(cs.map { Offset(it.x, it.y - r) }, "alt", pan = size.y * 0.42f)
    }
}

/**
 * v31 (3) bir arada (ara ara) çizilen elemanlar: seçmeye gerek kalmadan öneri kendiliğinden çıkar. v38 çizgi önerisi
 * ("Çizgileri düzelt") birbirine yakın 2+ çizgide, şekil önerisi 4+ elemanda; tek elemanda ve birbirinden uzak
 * elemanlarda çıkmaz.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V31AutoGroupTest : V31EditorBase() {
    private fun waitIndex() {
        val repo = org.koin.core.context.GlobalContext.get().get<com.karapp.drawguidance.data.repository.ShapeTemplateRepository>()
        waitFor(90_000) { repo.indexFlow.value != null }
    }

    @Test
    fun threeNearbyElementsShowGroupSuggestion() {
        createCanvasAndOpen("Yüz")
        waitIndex()
        val size = canvasSize()
        val c = Offset(size.x * 0.5f, size.y * 0.4f)
        val r = size.x * 0.25f
        // Yüz: baş, sonra ara vererek iki göz
        drawStrokes(listOf(roughCircle(c, r, 1)))
        advanceMain(1200)
        assertTrue("tek elemanda öneri yok", !exists("handwriting_chip"))
        drawStrokes(listOf(roughCircle(Offset(c.x - r * 0.4f, c.y - r * 0.25f), r * 0.14f, 2)))
        advanceMain(1200)
        // v38 iki elemanda çizgi önerisi (şekil önerisi yok)
        waitFor(20_000) { compose.onAllNodesWithText("Çizgileri düzelt (2)").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(!exists("shape_suggest_chip"))
        drawStrokes(listOf(roughCircle(Offset(c.x + r * 0.4f, c.y - r * 0.25f), r * 0.14f, 3)))
        waitFor { canvas().strokes.size == 3 }
        val before = canvas().strokes
        advanceMain(1200)
        waitFor(20_000) { exists("line_fix_option") }
        assertTrue(compose.onAllNodesWithText("Çizgileri düzelt (3)").fetchSemanticsNodes().isNotEmpty())
        shot("v31-auto-group")
        touch("line_fix_option")
        waitFor { canvas().strokes.all { s -> before.first { it.id == s.id }.points != s.points } }
        // Kapatılan/uygulanan grup için yeniden önerilmez; uzakta yeni çizim grubu bozmaz
        advanceMain(1200)
        assertTrue(!exists("line_fix_option"))
    }

    @Test
    fun farApartElementsDoNotShowGroupSuggestion() {
        createCanvasAndOpen("Uzak")
        waitIndex()
        val size = canvasSize()
        val r = size.x * 0.07f
        listOf(Offset(size.x * 0.2f, size.y * 0.2f), Offset(size.x * 0.8f, size.y * 0.45f), Offset(size.x * 0.25f, size.y * 0.75f)).forEachIndexed { i, c ->
            drawStrokes(listOf(roughCircle(c, r, 30L + i)))
            advanceMain(1200)
        }
        waitFor { canvas().strokes.size == 3 }
        advanceMain(1200)
        assertTrue(!exists("line_fix_option"))
    }

    /**
     * Kapatılan çizgi önerisi aynı çizgiler için yeniden çıkmaz (v38 o çizgiler çizgi önerisine bir daha girmez);
     * gruba yeni eleman eklenip 4 eleman olunca şekil önerisi çıkar.
     */
    @Test
    fun dismissedGroupStaysQuietUntilGroupGrows() {
        createCanvasAndOpen("Kapat")
        waitIndex()
        val size = canvasSize()
        val c = Offset(size.x * 0.5f, size.y * 0.4f)
        val h = size.x * 0.12f
        // Üç kenarla üçgen (ara vererek)
        val corners = triangle(c, h)
        for (i in 0 until 3) {
            drawStrokes(listOf(roughPoly(listOf(corners[i], corners[(i + 1) % 3]), 50L + i, closed = false)))
            advanceMain(1200)
        }
        waitFor(20_000) { compose.onAllNodesWithText("Çizgileri düzelt (3)").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("3 elemanda şekil önerisi yok", !exists("shape_suggest_chip"))
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Öneriyi kapat") and androidx.compose.ui.test.hasAnyAncestor(hasTestTag("line_suggest_chip"))).performClick()
        waitFor { !exists("line_fix_option") }
        advanceMain(1200)
        assertTrue("kapatılan çizgiler yeniden önerilmez", !exists("line_fix_option"))
        // Gruba bir çizgi daha (orta çizgi): 4 eleman, şekil önerisi; kapatılan çizgiler çizgi önerisine girmez
        drawStrokes(listOf(roughPoly(listOf(Offset(c.x, c.y - h), Offset(c.x, c.y + h)), 60, closed = false)))
        advanceMain(1200)
        waitFor(20_000) { exists("shape_suggest_chip") }
        assertTrue(!exists("line_fix_option"))
    }
}

/**
 * v31 (1) karşılama ekranı: nöromorfik zeminde saydam logo kendini çizer, altında yalnız "Draw Guidance" harf harf
 * yazılır (slogan yok); sonra Ana sayfa.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HomeTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V31WelcomeTest : V31EditorBase() {
    @Test
    fun logoAndTypedTitle() {
        compose.mainClock.autoAdvance = false
        waitFor { exists("welcome_screen") }
        // v36 Türkçe başlık "Çizim Rehberi", altında her dilde "created by karApp"
        assertTrue(compose.onAllNodes(hasTestTag("welcome_title") and hasText("Çizim Rehberi"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasTestTag("welcome_signature") and hasText("created by karApp"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        // Slogan kalktı
        assertTrue(compose.onAllNodesWithText("Rehberli çizim", substring = true).fetchSemanticsNodes().isEmpty())
        compose.mainClock.advanceTimeBy(800)
        shot("v31-welcome-typing")
        compose.mainClock.advanceTimeBy(650)
        shot("v31-welcome")
        compose.mainClock.autoAdvance = true
        repeat(30) { if (exists("welcome_screen")) { compose.mainClock.advanceTimeBy(150); advanceMain(150) } }
        waitFor { !exists("welcome_screen") }
        waitFor { exists("home_screen") }
    }
}
