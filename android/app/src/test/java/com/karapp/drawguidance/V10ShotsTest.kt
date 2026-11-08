package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.ui.drawing.DiagramRenderer
import com.karapp.drawguidance.ui.drawing.ShapeEditor
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v10 görsel kontrolü (DG_SHOTS verilince ekran görüntüsü). */
abstract class V10ShotsBase : WideLayoutTestBase() {

    private fun closeSheet(tag: String) {
        SystemBack.press()
        waitFor { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    }

    protected fun notesV10(prefix: String) {
        createNotesCanvasAndOpen("Geometri")
        addMenu("Şekil ekle")
        waitForText("Geometrik Cisimler")
        compose.onNodeWithText("Kare").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Kare")
        val size = canvasSize()
        val corner = Offset(size.x * 0.12f, size.y * 0.1f)
        drag(corner, Offset(size.x * 0.32f, size.x * 0.32f))
        waitFor { canvas().strokes.isNotEmpty() }
        val sq = ElementOps.bounds(canvas().strokes.single())!!
        val scale = (size.x * 0.32f) / sq.width
        val map = Mapping(scale, Offset(corner.x - sq.left * scale, corner.y - sq.top * scale))
        compose.waitForIdle()
        shot("$prefix-1-shape-selected")

        // Boya kutusu: seçili şeklin dolgusu
        clickFill()
        waitForText("Renk seç")
        compose.onNodeWithTag("color_sv_area").performTouchInput { click(Offset(width * 0.55f, height * 0.12f)) }
        waitFor { canvas().strokes.single().shape!!.style.fillColorHex != null }
        compose.onNodeWithTag("color_hue_bar").performTouchInput { click(Offset(width * 0.14f, height / 2f)) }
        compose.waitForIdle()
        shot("$prefix-2-paint-bucket")
        closeSheet("color_pick_sheet")

        // Şekil düzenleme (v16): vektör noktaları; iki kenarın ortası arasına çizgi çiz, nokta seç
        mode("Seç")
        editButton("Şekli düzenle")
        waitForText("Şekli Düzenle: Noktalar")
        val pts = canvas().strokes.single().points
        val l = pts.minOf { it.x }; val r = pts.maxOf { it.x }; val t = pts.minOf { it.y }; val btm = pts.maxOf { it.y }
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Çizgi çiz") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("node_tool_controls"))).performClick()
        tap(map.toScreen(l, (t + btm) / 2f))
        tap(map.toScreen(r, (t + btm) / 2f))
        waitFor { canvas().strokes.single().shape!!.subpaths.size == 2 }
        compose.waitForIdle()
        shot("$prefix-3-shape-line")
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Noktalar") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("tool_row_context"))).performClick()
        tap(map.toScreen(l, t))
        compose.waitForIdle()
        shot("$prefix-4-shape-part")
        compose.onNodeWithContentDescription("Bitti").performClick()
        compose.waitForIdle()

        // Yazı: dolgu ve kenarlık (v17: düzenleme bitince seçim kalkar, Seç'e geçilir)
        compose.waitForIdle()
        addMenu("Yazı ekle")
        waitForText("Yazı eklemek için tuvale dokun", substring = true)
        tap(Offset(size.x * 0.55f, size.y * 0.12f))
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor"))).performTextInput("Başlık")
        compose.onNodeWithContentDescription("Yazıyı bitir").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.any { it.text != null } }
        clickElements()
        val textLabels = (1..4).map { "Yazı $it" }
        waitFor {
            compose.onAllNodesWithTag("text_format_section").fetchSemanticsNodes().isNotEmpty() ||
                textLabels.any { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
        }
        if (compose.onAllNodesWithTag("text_format_section").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithText(textLabels.first { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }).performClick()
        }
        waitFor { compose.onAllNodesWithTag("text_format_section").fetchSemanticsNodes().isNotEmpty() }
        // v29 tek yazı biçimi alanı: renk ve kenarlık "Renk" bölümünde
        compose.onNodeWithTag("text_tab_COLOR").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { compose.onAllNodesWithTag("text_fill_border_section").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Kenarlık rengi").performScrollTo().performClick()
        waitForText("Kenarlık var")
        compose.onNodeWithText("Kenarlık var").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.any { it.text?.outlineColorHex != null } }
        compose.waitForIdle()
        shot("$prefix-5-text-outline-panel")
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        compose.waitForIdle()
        shot("$prefix-6-text-outline-canvas")

        // Diyagram: tuvalin üstünde düzenleme
        addMenu("Diyagram ekle")
        waitForText("Diyagram Türü")
        compose.onNodeWithText("Akış Diyagramı").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Diyagram: Akış Diyagramı")
        val screen = androidx.compose.ui.geometry.Rect(size.x * 0.12f, size.y * 0.42f, size.x * 0.72f, size.y * 0.9f)
        drag(screen.topLeft, Offset(screen.width, screen.height))
        waitFor { canvas().strokes.any { it.diagram != null } }
        editButton("Diyagramı düzenle")
        waitForText("Adım ekle")
        run {
            val d = canvas().strokes.first { it.diagram != null }
            val node = d.diagram!!.nodes[1]
            val xs = d.points.map { it.x }
            val ys = d.points.map { it.y }
            val c = DiagramRenderer.toCanvas(d, node.x + node.w / 2f, node.y + node.h / 2f)!!
            tap(Offset(
                screen.left + (c.x - xs.min()) / (xs.max() - xs.min()) * screen.width,
                screen.top + (c.y - ys.min()) / (ys.max() - ys.min()) * screen.height,
            ))
        }
        waitFor { compose.onAllNodesWithTag("diagram_focus_panel").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        shot("$prefix-7-diagram-on-canvas")
        compose.onNodeWithContentDescription("Bitti").performClick()
        compose.waitForIdle()

        // Kılavuz ve perspektif ayrı; genel ayarlar çizim alanının altında
        addMenu("Kılavuz çizgileri")
        waitForText("Kılavuz Çizgileri")
        compose.waitForIdle()
        shot("$prefix-8-guide-type-sheet")
        compose.onNodeWithText("Noktalı").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { compose.onAllNodesWithTag("guide_settings_bar").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        shot("$prefix-9-guide-bar")
        compose.onNodeWithText("Bitti").performClick()
        // v27 not tuvalinde perspektif yok (yalnız kılavuz)
        compose.waitForIdle()

        // Kaydetme penceresi (v42 not almada tek pencere: PDF öntanımlı, Resim dosyası sekmesi)
        compose.onNodeWithTag("header_save").performClick()
        waitFor { compose.onAllNodesWithTag("pdf_options_sheet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("save_tab_image").performClick()
        waitFor { compose.onAllNodesWithTag("image_save_options_dialog").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("JPEG").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        shot("$prefix-11-save-options")
    }

    protected fun miscV10(prefix: String) {
        createCanvasAndOpen("Kırpma")
        val size = canvasSize()
        val start = Offset(size.x * 0.15f, size.y * 0.35f)
        pickBrush("Marker")
        drag(start, Offset(size.x * 0.6f, size.y * 0.1f))
        waitFor { canvas().strokes.size == 1 }
        drag(Offset(size.x * 0.2f, size.y * 0.6f), Offset(size.x * 0.5f, -size.y * 0.05f))
        waitFor { canvas().strokes.size == 2 }
        clickElements()
        waitForText("Marker 1")
        compose.onNodeWithText("Marker 1").performClick()
        compose.waitForIdle()
        shot("$prefix-12-panel-selection")
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        mode("Kırp")
        waitForText("Kırp: Seçili eleman")
        compose.waitForIdle()
        shot("$prefix-13-crop-element")
        compose.onNode(hasText("Kırp") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Geri").performClick()
        waitForText("Kırpma")

        compose.onNode(hasText("Ayarlar") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        // v36 kaydetme ayarları Kaydetme sekmesinde
        waitFor { compose.onAllNodesWithTag("settings_tab_saving").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings_tab_saving").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Resim kaydetme")
        compose.onNodeWithTag("export_settings_card").performScrollTo()
        compose.waitForIdle()
        shot("$prefix-14-settings-export")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V10NotesShotsTest : V10ShotsBase() {
    @Test fun notes() = notesV10("v10")
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V10MiscShotsTest : V10ShotsBase() {
    @Test fun misc() = miscV10("v10")
}
