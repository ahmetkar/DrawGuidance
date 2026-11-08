package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Plane3D
import com.karapp.drawguidance.data.repository.ShapeTemplateRepository
import com.karapp.drawguidance.platform.Stylus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

abstract class V21EditorBase : V20EditorBase() {

    /** Ekran koordinatındaki çizgileri parmakla çizer. */
    protected fun drawStrokes(strokes: List<List<P>>) {
        strokes.forEach { s ->
            compose.onNodeWithTag("drawing_canvas").performTouchInput {
                down(Offset(s[0].x, s[0].y))
                for (i in 1 until s.size) moveTo(Offset(s[i].x, s[i].y))
                up()
            }
        }
        compose.waitForIdle()
    }

    protected fun text(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()

    protected fun hasDescription(d: String) = compose.onAllNodesWithContentDescription(d).fetchSemanticsNodes().isNotEmpty()

    /** v22 ekran görüntüsü (DG_SHOTS verilince). */
    protected fun shot(name: String) {
        val dir = System.getenv("DG_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        repeat(4) { Thread.sleep(80); compose.waitForIdle(); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }
        val root = compose.activity.window.decorView
        val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { root.draw(android.graphics.Canvas(bitmap)) }
        java.io.File(dir).mkdirs()
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/**
 * v21 (1): Düzenle modunun üst menüsünden seçili şeklin vektör düzenlemesi açılır. v34: "Vektör düzenle" yerine şekil
 * düzenlemenin araçları doğrudan (Nokta düzenle, Çizgi düzenle, Alan boya); seçim yokken de etkin (dokunulan şekil o
 * araçla açılır).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V21VectorEditButtonTest : V21EditorBase() {
    @Test
    fun vectorEditButtonOpensShapeEditing() {
        createCanvasAndOpen("Vektör")
        val size = canvasSize()
        mode("Düzenle")
        waitFor { hasTopTool("Nokta düzenle") }
        assertFalse(hasTopTool("Vektör düzenle"))
        // Şekil ekle (eklenen şekil seçili)
        addMenu("Şekil ekle")
        waitForText("Kare")
        compose.onNodeWithText("Kare").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Kare")
        drag(Offset(size.x * 0.25f, size.y * 0.2f), Offset(size.x * 0.4f, size.x * 0.4f))
        waitFor { shapes().size == 1 }
        modeBarTitle("Taşı")
        // Düzenle modu: seçim korunur; Nokta düzenle seçili şeklin nokta düzenlemesini açar
        mode("Düzenle")
        waitFor { hasTopTool("Nokta düzenle") }
        topTool("Nokta düzenle").performClick()
        if (compose.onAllNodesWithText("Şekli Düzenle: Noktalar").fetchSemanticsNodes().isEmpty()) {
            // Seçim Düzenle'ye geçerken kalktıysa: araç bekler, şekle dokununca açılır
            tap(Offset(size.x * 0.25f, size.y * 0.2f + size.x * 0.2f))
        }
        modeBarTitle("Şekli Düzenle: Noktalar")
        assertTrue(hasTopTool("Noktalar"))
        // v33 Nokta ekle ve Çizgi çiz alttaki nokta panelinde
        listOf("Nokta ekle", "Çizgi çiz").forEach { assertTrue(it, hasNodeTool(it)) }
        finish()
    }
}

/**
 * v21 (2): seçili fırça izinin düzenleme seçeneklerinde (Seç modunun alt çubuğu ve bağlam menüsü) "Düzeltme öner" yalnız
 * çizim önerileri açıkken ve fırçanın görünümünü bozmayacaksa görünür. v38 tek iz için öneri yok: en az iki iz seçilir.
 * v40 v39 çizim miktarı fazla (büyük) tek ize de öneri var: tek iz seçiliyken "Düzeltme öner", iki izde "Çoklu düzeltme öner".
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V21InkCorrectionButtonTest : V21EditorBase() {
    @Test
    fun correctionShownOnlyWhenSuggestionsOn() {
        createCanvasAndOpen("Düzeltme")
        val size = canvasSize()
        val y = size.y * 0.4f
        jitteryLine(y, 1.5f)
        jitteryLine(y + size.y * 0.05f, 1.5f)
        mode("Seç")
        tap(Offset(size.x * 0.45f, y))
        modeBarTitle("Seç")
        waitForText("Yumuşat")
        // v38 tek çizgiye öneri yoktu; v40 v39 büyük tek çizgiye çizgi önerisi (SuggestRules.bigLine): tuval genişliğinin
        // üçte ikisi uzunluğundaki tek iz seçiliyken "Düzeltme öner" görünür, öneriler kapanınca kaybolur
        waitForText("Düzeltme öner")
        prefs().setHandwriting(false)
        waitFor { compose.onAllNodesWithText("Düzeltme öner", substring = true).fetchSemanticsNodes().isEmpty() }
        prefs().setHandwriting(true)
        waitForText("Düzeltme öner")
        advanceMain(400)
        tap(Offset(size.x * 0.45f, y + size.y * 0.05f))
        waitForText("Çoklu düzeltme öner")
        prefs().setHandwriting(false)
        waitFor { compose.onAllNodesWithText("Çoklu düzeltme öner").fetchSemanticsNodes().isEmpty() }
        prefs().setHandwriting(true)
        waitForText("Çoklu düzeltme öner")
        compose.onNodeWithText("Çoklu düzeltme öner").performClick()
        waitFor { chipShown() }
    }
}

/** v21 (4): Ayarlarda kalem tanıma görünür ve değiştirilebilir; fırça panelindeki kalem (eğim) ayarları buna göre. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V21StylusRecognitionTest : V21EditorBase() {
    @Test
    fun recognitionControlsPenSettings() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithText("Ayarlar").performSemanticsAction(SemanticsActions.OnClick)
        // v36 kalem ayarları Çizim sekmesinde
        waitFor { compose.onAllNodesWithTag("settings_tab_drawing").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings_tab_drawing").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("stylus_recognized").performScrollTo()
        waitFor { text("stylus_recognized").startsWith("Kalem tanınmadı") }
        // Tanınmadan kalem ayarları gizli
        assertTrue(compose.onAllNodesWithText("Kalemle çizerken parmak kaydırsın").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("stylus_recognition_always").performScrollTo().performClick()
        waitFor { text("stylus_recognized").startsWith("Kalem tanındı") }
        waitFor { compose.onAllNodesWithText("Kalemle çizerken parmak kaydırsın").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(Stylus.Recognition.ALWAYS, Stylus.recognition.value)
        compose.onNodeWithTag("stylus_recognition_off").performScrollTo().performClick()
        waitFor { text("stylus_recognized").startsWith("Kalem tanınmadı") }
        assertFalse(Stylus.penFeatures)
        SystemBack.press()

        // Fırça panelinde eğim ayarları yalnız kalem tanınınca
        createCanvasAndOpen("Kalem")
        openBrushPanel()
        assertFalse(exists("brush_tilt_threshold"))
        closePanel()
        Stylus.setRecognition(Stylus.Recognition.ALWAYS)
        openBrushPanel()
        waitFor { exists("brush_tilt_threshold") }
        closePanel()
    }
}

/**
 * v21 (8): şekil veritabanından öneri: parmakla çizilen ev (gövde, çatı, kapı, pencere) "Ev (beşik çatılı)" şablonuyla
 * değiştirilebilir; kabul edilince izler gruplu şablon çizgileri olur, geri alınınca eski çizim döner.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V21TemplateSuggestionTest : V21EditorBase() {
    @Test
    fun houseIsReplacedByTemplate() {
        createCanvasAndOpen("Şablon")
        val repo = GlobalContext.get().get<ShapeTemplateRepository>()
        waitFor(90_000) { repo.indexFlow.value != null }
        val size = canvasSize()
        val house = handDrawnTemplate("yapi.ev.besik", size.x * 0.5f, size.y * 0.4f, size.x * 0.6f, 21)
        drawStrokes(house)
        waitFor { canvas().strokes.size == 4 }
        val before = canvas().strokes
        advanceMain(900)
        waitFor(20_000) { chipShown() }
        val cd = "Şablonla değiştir: Ev (beşik çatılı)"
        waitFor { hasDescription(cd) }
        compose.onNodeWithContentDescription(cd).performScrollTo().performClick()
        waitFor { canvas().strokes.none { s -> before.any { it.id == s.id } } }
        val after = canvas().strokes
        assertEquals(4, after.size)
        val group = after.first().groupId
        assertNotNull(group)
        assertTrue(after.all { it.groupId == group && it.brushType == before.first().brushType && it.colorHex == before.first().colorHex })
        assertFalse(chipShown())
        // Öğrenme: kullanım sayacı
        waitFor { repo.indexFlow.value!!.usageOf("yapi.ev.besik") == 1 }
        // Geri al: eski çizim
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { canvas().strokes.map { it.id } == before.map { it.id } }
        assertTrue(canvas().strokes.all { it.brushType != BrushType.SHAPE })
    }
}
