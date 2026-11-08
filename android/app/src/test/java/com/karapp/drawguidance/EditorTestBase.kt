package com.karapp.drawguidance

import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.Shadows.shadowOf

/** Çizim ekranı UI testleri için ortak kurulum ve yardımcılar. */
abstract class EditorTestBase {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() = stopKoin()

    /** Tuval ekranındaki dokunuş koordinatı ile tuval koordinatı arasındaki dönüşüm. */
    data class Mapping(val scale: Float, val offset: Offset) {
        fun toScreen(x: Float, y: Float) = Offset(offset.x + x * scale, offset.y + y * scale)
    }

    protected fun canvas(): CanvasUi = runBlocking {
        GlobalContext.get().get<CanvasRepository>().observeCanvases().first().single()
    }

    protected fun waitFor(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            if (System.currentTimeMillis() > deadline) throw AssertionError("Koşul $timeoutMs ms içinde sağlanmadı")
            Thread.sleep(50)
        }
    }

    protected fun waitForText(text: String, substring: Boolean = false) = waitFor {
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    protected fun waitForGone(text: String) = waitFor {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }

    protected fun waitForDescription(description: String, substring: Boolean = false) = waitFor {
        compose.onAllNodesWithContentDescription(description, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    /**
     * v45 eski bağlam menüsünün yerine: boş yerde dikey ekleme menüsü, seçili elemanın köşesinde seçim menüsü ve
     * türüne özel işlemlerin Düzenle listesi.
     */
    protected fun inContextMenu(): androidx.compose.ui.test.SemanticsMatcher =
        listOf("canvas_add_menu", "selection_menu", "selection_menu_edit_list")
            .map { androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(it)) }.reduce { a, b -> a or b }

    /** v45 iki parmakla basınca açılan menü (boş yerde ekleme menüsü, seçimde Düzenle listesi) açık mı. */
    protected fun contextMenuOpen(): Boolean =
        listOf("canvas_add_menu", "selection_menu_edit_list").any { compose.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }

    /** v45 Seç'te seçim yokken boş yere dokunmak ekleme menüsünü açar; açıksa dışına dokunup kapatır. */
    protected fun dismissAddMenu() {
        compose.waitForIdle()
        if (compose.onAllNodesWithTag("canvas_add_menu_scrim").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("canvas_add_menu_scrim").performTouchInput { click(androidx.compose.ui.geometry.Offset(4f, 4f)) }
            compose.waitForIdle()
        }
    }

    protected fun SemanticsNodeInteraction.clickInPopup() {
        performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    protected fun createCanvasAndOpen(name: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithContentDescription("Yeni Tuval").performClick()
        waitForText("Yeni Tuval Oluştur")
        compose.onNode(hasSetTextAction()).performTextInput(name)
        compose.onNodeWithText("Oluştur").clickInPopup()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Çalışma/Not türünde tuval oluşturup açar. */
    protected fun createNotesCanvasAndOpen(name: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithContentDescription("Yeni Tuval").performClick()
        waitForText("Yeni Tuval Oluştur")
        compose.onNodeWithText("Çalışma / Not").clickInPopup()
        compose.onNode(hasSetTextAction()).performTextInput(name)
        compose.onNodeWithText("Oluştur").clickInPopup()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }
    }

    /**
     * v47 Özellikler (eski Fırça ayarları) alanını açar: düğme dikey ekranda mod menüsünde ya da basit menüde, yatayda
     * sağdaki rayda (kaydırılamayan yerde de çalışır).
     */
    protected fun openProperties() {
        val node = androidx.compose.ui.test.hasContentDescription("Özellikler") and androidx.compose.ui.test.hasClickAction()
        waitFor { compose.onAllNodes(node).fetchSemanticsNodes().isNotEmpty() }
        runCatching { compose.onAllNodes(node)[0].performScrollTo() }
        compose.onAllNodes(node)[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** Ekle menüsünden bir öğe (Şekil ekle / Yazı ekle / Resim ekle). */
    protected fun addMenu(item: String) {
        if (compose.onAllNodesWithContentDescription("Ekle menüsü").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("Ekle menüsü").performClick()
            waitForText(item)
            compose.onNodeWithText(item).clickInPopup()
        } else {
            // v17: Çiz dışındaki modlarda eklenecekler Ekle modunun üst menüsünde
            mode("Ekle")
            val node = androidx.compose.ui.test.hasContentDescription(item) and
                androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("tool_row_context"))
            waitFor { compose.onAllNodes(node).fetchSemanticsNodes().isNotEmpty() }
            // v23: Ekle satırı uzadı (Arka plan, 3B şekil); ekran dışındaki düğmeye önce kaydırılır
            runCatching { compose.onNode(node).performScrollTo() }
            compose.onNode(node).performClick()
            compose.waitForIdle()
        }
    }

    /** Parmağı [point] noktasında hareket ettirmeden [millis] ms basılı tutar. */
    protected fun longPress(point: Offset, millis: Long = 2200) {
        compose.waitForIdle()
        compose.onNodeWithTag("drawing_canvas").performTouchInput { down(point) }
        compose.mainClock.advanceTimeBy(millis)
        compose.onNodeWithTag("drawing_canvas").performTouchInput { up() }
        compose.waitForIdle()
    }

    protected fun tap(point: Offset) {
        compose.waitForIdle()
        compose.onNodeWithTag("drawing_canvas").performTouchInput { click(point) }
        compose.waitForIdle()
    }

    /**
     * v47 tuval noktasının sığdırılmış görünümde çizim alanındaki yeri (kenar payı 24 dp). Menü satırı bir ya da iki satır
     * olunca çizim alanının boyu değişir ve kâğıt yeniden sığdırılır: eski ekran konumları yerine bu kullanılır.
     */
    protected fun fittedScreen(p: Offset): Offset {
        val view = canvasSize()
        val c = canvas()
        val margin = 24f * compose.density.density
        val s = minOf((view.x - 2 * margin) / c.width, (view.y - 2 * margin) / c.height)
        return Offset((view.x - c.width * s) / 2f + p.x * s, (view.y - c.height * s) / 2f + p.y * s)
    }

    /** Görünümü kâğıda sığdırır (yakınlaştırma çipi): [fittedScreen] bundan sonra doğrudur. */
    protected fun fitView() {
        compose.onNodeWithContentDescription("Yakınlaştırma %", substring = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** [fittedScreen]'in tersi. */
    protected fun fittedCanvas(p: Offset): Offset {
        val view = canvasSize()
        val c = canvas()
        val margin = 24f * compose.density.density
        val s = minOf((view.x - 2 * margin) / c.width, (view.y - 2 * margin) / c.height)
        return Offset((p.x - (view.x - c.width * s) / 2f) / s, (p.y - (view.y - c.height * s) / 2f) / s)
    }

    protected fun canvasSize(): Offset {
        val size = compose.onNodeWithTag("drawing_canvas").fetchSemanticsNode().size
        return Offset(size.width.toFloat(), size.height.toFloat())
    }

    /** Tuvalde [from] noktasından [delta] kadar düz bir sürükleme. */
    protected fun drag(from: Offset, delta: Offset, steps: Int = 20) {
        compose.waitForIdle()
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(from)
            repeat(steps) { moveBy(Offset(delta.x / steps, delta.y / steps)) }
            up()
        }
        compose.waitForIdle()
    }

    protected fun pickBrush(label: String) {
        clickTool("Fırça Seç")
        // v22: silgideyken ilk dokunuş son fırçaya döner, ikincisi seçiciyi açar
        if (compose.onAllNodesWithText("Fosforlu Kalem").fetchSemanticsNodes().isEmpty()) {
            clickTool("Fırça Seç")
        }
        waitForText("Fosforlu Kalem")
        compose.onNodeWithText(label).clickInPopup()
        waitForDescription("Seçili fırça: $label")
    }

    protected fun mode(label: String) {
        // Önce mod menüsündeki düğme; yoksa (Döndür/Ölçekle üst menüde) herhangi bir düğme
        val inSelector = androidx.compose.ui.test.hasContentDescription(label) and
            androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("mode_selector"))
        if (compose.onAllNodes(inSelector).fetchSemanticsNodes().isNotEmpty()) {
            // v45 dikey ekranda mod adları düğmenin içinde, satır kayabilir: önce düğmeye kaydırılır
            runCatching { compose.onNode(inSelector).performScrollTo() }
            compose.onNode(inSelector).performClick()
        }
        else {
            // v48 gelişmiş modlarda Taşı ve Genişlet Düzenle satırında iki düğme: Taşı eleman seçiliyken, Genişlet tuval
            // seçiliyken etkin. Düğme kapalıysa (eski testlerin akışı) mod doğrudan seçilir.
            if (label in setOf("Taşı", "Genişlet") && compose.onAllNodes(androidx.compose.ui.test.hasTestTag("mode_selector")).fetchSemanticsNodes().isNotEmpty()) {
                val target = if (label == "Taşı") com.karapp.drawguidance.ui.screens.canvasedit.EditMode.MOVE else com.karapp.drawguidance.ui.screens.canvasedit.EditMode.EXPAND
                mode("Düzenle")
                val node = androidx.compose.ui.test.hasContentDescription(label) and androidx.compose.ui.test.hasClickAction() and
                    androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("tool_row_context"))
                waitFor { compose.onAllNodes(node).fetchSemanticsNodes().isNotEmpty() }
                val enabled = !compose.onNode(node).fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
                if (enabled) compose.onNode(node).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
                else compose.runOnIdle { com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel.lastCreated?.get()?.chooseMode(target) }
                compose.waitForIdle()
                return
            }
            // v18: Döndür, Ölçekle ve Kırp Düzenle modunun üst menüsünde (seçim korunur)
            if (label in setOf("Döndür", "Ölçekle", "Kırp") && compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isEmpty()) mode("Düzenle")
            runCatching { compose.onNodeWithContentDescription(label).performScrollTo() }
            compose.onNodeWithContentDescription(label).performClick()
        }
        compose.waitForIdle()
    }

    /**
     * v18: seçili elemanın düzenleme düğmesi (Şekli düzenle, Diyagramı düzenle…) Seç modunun alt çubuğunda. Taşı modunda
     * yalnızca taşıma seçenekleri olduğundan önce Seç'e geçilir (seçim korunur).
     */
    protected fun editButton(text: String) {
        compose.waitForIdle()
        if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()) mode("Seç")
        waitForText(text)
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    /** v18: Boya kutusu üst menüde; v34: yalnız Çiz satırında (Düzenle'de nokta/çizgi/alan boya): gerekirse Çiz'e geçilir. */
    protected fun clickFill() {
        compose.waitForIdle()
        if (compose.onAllNodesWithContentDescription("Boya kutusu").fetchSemanticsNodes().isEmpty()) mode("Çiz")
        clickTool("Boya kutusu")
    }

    /**
     * v45 üst menü düğmesine dokunur: dikey ekranda gelişmiş modlarda adlar düğmenin içinde yazdığından satır kayar ve
     * düğme ekranın dışında kalabilir; önce düğmeye kaydırılır (kaydırılan satırda değilse doğrudan dokunulur).
     */
    protected fun clickTool(description: String) {
        compose.waitForIdle()
        runCatching { compose.onNodeWithContentDescription(description).performScrollTo() }
        compose.onNodeWithContentDescription(description).performClick()
        compose.waitForIdle()
    }

    /** v18: Elemanlar düğmesi Taşı modunun üst menüsünde yok: gerekirse Seç'e geçilir (seçim korunur). */
    protected fun clickElements() {
        compose.waitForIdle()
        if (compose.onAllNodesWithContentDescription("Elemanlar").fetchSemanticsNodes().isEmpty()) mode("Seç")
        // v45 dikey ekranda üst menü satırı kayabilir: önce düğmeye kaydırılır
        runCatching { compose.onNodeWithContentDescription("Elemanlar").performScrollTo() }
        compose.onNodeWithContentDescription("Elemanlar").performClick()
        compose.waitForIdle()
    }

    /** Üst menüdeki yakınlaştırma oranı (%). */
    protected fun zoomPercent(): Int {
        val node = compose.onAllNodesWithContentDescription("Yakınlaştırma %", substring = true).fetchSemanticsNodes().first()
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription)!!.first()
        return Regex("%(\\d+)").find(description)!!.groupValues[1].toInt()
    }

    /** Sürüklenerek çizilen ilk çizimden ekran↔tuval dönüşümünü çıkarır. */
    protected fun mappingFrom(start: Offset, lengthPx: Float, strokeIndex: Int = 0): Mapping {
        val stroke = canvas().strokes[strokeIndex]
        val first = stroke.points.first()
        val last = stroke.points.last()
        val scale = lengthPx / (last.x - first.x)
        return Mapping(scale, Offset(start.x - first.x * scale, start.y - first.y * scale))
    }
}
