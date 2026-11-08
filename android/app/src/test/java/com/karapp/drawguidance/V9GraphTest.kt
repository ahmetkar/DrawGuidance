package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v9 grafikler: eksen etiketleri ve formül, seçili grafiğin ayarlarından verilir; şekil bulunduğu
 * kutuda yeniden kurulur. Ucu açık grafiklerde hizalama/dolgu ayarları sunulmaz.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V9GraphTest : EditorTestBase() {

    @Test
    fun graphAxisLabelsAndFormulaComeFromTheElementSettings() {
        createNotesCanvasAndOpen("Fonksiyon")
        addMenu("Şekil ekle")
        waitForText("Geometrik Cisimler")
        // v15: grafikler ayrı bölümde (yeni grafik motoru)
        compose.onAllNodesWithText("Grafik")[0].performClick()
        waitForText("Fonksiyon grafiği")
        compose.onNodeWithText("Fonksiyon grafiği").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Fonksiyon grafiği")
        val size = canvasSize()
        drag(Offset(size.x * 0.15f, size.y * 0.15f), Offset(size.x * 0.6f, size.y * 0.4f))
        waitFor { canvas().strokes.isNotEmpty() }
        val plain = canvas().strokes.single()
        assertEquals("x", plain.shape!!.graph!!.xLabel)

        clickElements()
        waitFor { compose.onAllNodesWithTag("element_style_section").fetchSemanticsNodes().isNotEmpty() }
        waitForText("Grafik")
        // Ucu açık grafikte dolgu/köşe ayarları sunulmaz
        assertTrue(compose.onAllNodesWithText("Köşe yuvarlaklığı").fetchSemanticsNodes().isEmpty())

        // Eksen etiketi
        val xField = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("graph_x_field")))
        xField.performTextClearance()
        xField.performTextInput("zaman")
        waitFor { canvas().strokes.single().shape?.graph?.xLabel == "zaman" }
        assertTrue(
            "etiket şeklin içinde yazı çapası olarak saklanır",
            canvas().strokes.single().shape!!.subpaths.any { it.label == "zaman" },
        )

        // Formül: geçerli formül eğri ekler
        // v15: fonksiyon grafiği örnek bir formülle başlar; formül değiştirilince eğri yeniden çizilir
        val field = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("graph_formula_field")))
        field.performTextClearance()
        field.performTextInput("x^2")
        waitFor { canvas().strokes.single().shape?.graph?.formula == "x^2" }
        assertTrue("formül eğrisi çizildi", canvas().strokes.single().shape!!.subpaths.any { it.tag == "curve" })

        // Geçersiz formül uyarı verir ve eğri çizilmez
        val formula = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("graph_formula_field")))
        formula.performTextClearance()
        formula.performTextInput("2 +")
        waitForText("Formül okunamadı")
    }
}
