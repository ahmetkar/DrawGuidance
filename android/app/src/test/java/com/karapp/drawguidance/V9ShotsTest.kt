package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.ui.drawing.ShapeKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v9 görsel kontrolü (DG_SHOTS verilince ekran görüntüsü): sayfalar, diyagram, kılavuz, biçim paneli. */
abstract class V9ShotsBase : WideLayoutTestBase() {

    protected fun notesV9(prefix: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithContentDescription("Yeni Tuval").performClick()
        waitForText("Yeni Tuval Oluştur")
        compose.onNodeWithText("Çalışma / Not").clickInPopup()
        compose.onNode(hasSetTextAction()).performTextInput("Geometri Defteri")
        waitForDescription("Sayfa sayısı: 4")
        compose.onNodeWithContentDescription("Sayfa sayısı: 4").clickInPopup()
        compose.waitForIdle()
        shot("$prefix-1-new-notes-pages")
        compose.onNodeWithText("Oluştur").clickInPopup()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }
        waitFor { compose.onAllNodesWithTag("page_strip").fetchSemanticsNodes().isNotEmpty() }
        shot("$prefix-2-page-strip")

        // Şekil: grafik, sonra biçim paneli
        addMenu("Şekil ekle")
        waitForText("Geometrik Cisimler")
        // v15: grafikler ayrı bölümde (yeni grafik motoru)
        compose.onAllNodesWithText("Grafik")[0].performClick()
        waitForText("Fonksiyon grafiği")
        compose.onNodeWithText("Fonksiyon grafiği").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Fonksiyon grafiği")
        val size = canvasSize()
        drag(Offset(size.x * 0.12f, size.y * 0.12f), Offset(size.x * 0.5f, size.y * 0.35f))
        waitFor { canvas().strokes.isNotEmpty() }
        clickElements()
        waitFor { compose.onAllNodesWithTag("element_style_section").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("graph_formula_field"))).performTextClearance()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("graph_formula_field"))).performTextInput("sin(x)")
        waitFor { canvas().strokes.any { it.shape?.graph?.formula == "sin(x)" } }
        shot("$prefix-3-graph-settings")
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        compose.waitForIdle()

        // Diyagram
        addMenu("Diyagram ekle")
        waitForText("Diyagram Türü")
        compose.waitForIdle()
        shot("$prefix-4-diagram-picker")
        compose.onNodeWithText("Akış Diyagramı").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Diyagram: Akış Diyagramı")
        drag(Offset(size.x * 0.15f, size.y * 0.5f), Offset(size.x * 0.45f, size.y * 0.42f))
        waitFor { canvas().strokes.any { it.diagram != null } }
        shot("$prefix-5-diagram-placed")
        editButton("Diyagramı düzenle")
        waitForText("Adım ekle")
        // Tuval üstünde: ilk adıma dokununca yanında küçük panel açılır
        run {
            val d = canvas().strokes.first { it.diagram != null }
            val node = d.diagram!!.nodes.first()
            val ux = node.x + node.w / 2f
            val uy = node.y + node.h / 2f
            val c = com.karapp.drawguidance.ui.drawing.DiagramRenderer.toCanvas(d, ux, uy)!!
            // v47 menü satırı değişince kâğıt yeniden sığdırılır: konum görünümden hesaplanır
            tap(fittedScreen(c))
        }
        waitFor { compose.onAllNodesWithTag("diagram_focus_panel").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        shot("$prefix-6-diagram-editor")
        compose.onNodeWithContentDescription("Bitti").performClick()
        compose.waitForIdle()

        // Kılavuz: pencerede yalnızca tür, genel ayarlar çizim alanının altında
        addMenu("Kılavuz çizgileri")
        waitForText("Kılavuz Çizgileri")
        compose.waitForIdle()
        shot("$prefix-7-guide-sheet")
        compose.onNodeWithText("Kareli").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().guide.kind != com.karapp.drawguidance.data.model.GuideKind.NONE }
        waitForText("Aralık")
        // v27 not tuvalinde perspektif yok (yalnız kılavuz)
        compose.waitForIdle()
        shot("$prefix-8-guide-on-canvas")
        compose.onNodeWithText("Bitti").performClick()
        compose.waitForIdle()

        // Şekil düzenleme modu
        mode("Seç")
        compose.waitForIdle()
        tap(Offset(size.x * 0.3f, size.y * 0.2f))
        compose.waitForIdle()
        shot("$prefix-9-final")
    }

    /** Sanat şekillerinin (kelebek, ağaç, kuş, ev, çiçek) tek karede görünümü. */
    protected fun artDetail(prefix: String) {
        createCanvasAndOpen("Manzara")
        val size = canvasSize()
        // Kalibrasyon: ekran ↔ tuval dönüşümü (şekiller kağıdın içine yerleşsin)
        val calStart = Offset(size.x * 0.2f, size.y * 0.5f)
        drag(calStart, Offset(size.x * 0.5f, 0f))
        waitFor { canvas().strokes.size == 1 }
        val map = mappingFrom(calStart, size.x * 0.5f)
        compose.onNodeWithContentDescription("Geri al").performClick()
        waitFor { canvas().strokes.isEmpty() }

        val w = canvas().width.toFloat()
        val h = canvas().height.toFloat()
        val cell = Offset(w * 0.24f, h * 0.22f)
        // v42 resim tuvalinde nesne çizimleri yok: her kategoriden bir genel şekil
        val places = listOf(
            Triple("Yıldızlar ve patlamalar", "Patlama 1", Offset(w * 0.02f, h * 0.03f)),
            Triple("Afişler ve kurdeleler", "Kurdele afiş", Offset(w * 0.28f, h * 0.03f)),
            Triple("Blok oklar", "Sağ blok ok", Offset(w * 0.54f, h * 0.05f)),
            Triple("Akış şeması", "Karar", Offset(w * 0.02f, h * 0.29f)),
            Triple("Konuşma ve açıklama kutuları", "Düşünce balonu", Offset(w * 0.28f, h * 0.29f)),
            Triple("Semboller ve çerçeveler", "Dişli", Offset(w * 0.54f, h * 0.29f)),
            Triple("Semboller ve çerçeveler", "Bulut", Offset(w * 0.02f, h * 0.58f)),
            Triple("Çizgiler ve oklar", "Kavisli ok", Offset(w * 0.40f, h * 0.60f)),
        )
        places.forEach { (category, name, at) ->
            addMenu("Şekil ekle")
            waitForText(category)
            compose.onAllNodesWithText(category)[0].performSemanticsAction(SemanticsActions.OnClick)
            waitForText(name)
            compose.onAllNodesWithText(name)[0].performSemanticsAction(SemanticsActions.OnClick)
            waitForText("Şekil: $name")
            val before = canvas().strokes.size
            val from = map.toScreen(at.x, at.y)
            val to = map.toScreen(at.x + cell.x, at.y + cell.y)
            drag(from, to - from)
            try {
                waitFor(4_000) { canvas().strokes.size > before }
            } catch (e: AssertionError) {
                throw AssertionError("$name eklenemedi", e)
            }
        }
        compose.onNodeWithText("Bitti").performClick()
        compose.waitForIdle()
        shot("$prefix-10-art-detail")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V9NotesShotsTest : V9ShotsBase() {
    @Test fun notes() = notesV9("v9-portrait")
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V9ArtShotsTest : V9ShotsBase() {
    @Test fun art() = artDetail("v9-art")
}
