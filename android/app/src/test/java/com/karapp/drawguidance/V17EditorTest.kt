package com.karapp.drawguidance

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.VectorEdit
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * v17 düzenleyici: Seç + İncele birleşimi, Ekle ve Düzenle modları, moda göre değişen üst menü (Döndür/Ölçekle üstte),
 * iki parmakla uzun basma bağlam menüsü, alan alan boyama, kenar vektörleştirme ve çizim düzeltme önerisi.
 */
abstract class V17EditorBase : EditorTestBase() {

    protected fun modeBarTitle(text: String) = waitFor {
        compose.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag("mode_bar"))).fetchSemanticsNodes().isNotEmpty()
    }

    /** Seç modu, seçim yok: mod çubuğu yok, altta yönerge çipi. */
    protected fun inSelectIdle() = waitFor {
        compose.onAllNodesWithTag("mode_bar").fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodesWithText("Elemana dokunarak seç", substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** Mod çubuğundaki "Bitti" (simge düğmesi ya da yazılı düğme). */
    protected fun finish() {
        val icon = hasContentDescription("Bitti") and hasAnyAncestor(hasTestTag("mode_bar"))
        if (compose.onAllNodes(icon).fetchSemanticsNodes().isNotEmpty()) compose.onNode(icon).performClick()
        else compose.onNode(hasText("Bitti") and hasAnyAncestor(hasTestTag("mode_bar"))).performClick()
        compose.waitForIdle()
    }

    /** Üst menüdeki (moda özel satır) düğme. */
    protected fun topTool(label: String) = compose.onNode(hasContentDescription(label) and hasAnyAncestor(hasTestTag("tool_row_context")))
        // v45 dikey ekranda adlar düğmenin içinde, satır uzadı: ekran dışındaki düğmeye önce kaydırılır
        .also { runCatching { it.performScrollTo() } }

    protected fun hasTopTool(label: String) =
        compose.onAllNodes(hasContentDescription(label) and hasAnyAncestor(hasTestTag("tool_row_context"))).fetchSemanticsNodes().isNotEmpty()

    /** v33 şekil düzenlemenin nokta panelindeki araç. */
    protected fun hasNodeTool(label: String) =
        compose.onAllNodes(hasContentDescription(label) and hasAnyAncestor(hasTestTag("node_tool_controls"))).fetchSemanticsNodes().isNotEmpty()

    /** İki parmakla kıpırdamadan basılı tutma (bağlam menüsü). */
    protected fun twoFingerHold(at: Offset) {
        compose.waitForIdle()
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(0, at - Offset(30f, 0f))
            down(1, at + Offset(30f, 0f))
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("drawing_canvas").performTouchInput { up(0); up(1) }
        compose.waitForIdle()
    }

    protected fun menuItem(text: String) = compose.onNode(hasText(text) and inContextMenu())

    protected fun shapes(): List<Stroke> = canvas().strokes.filter { it.brushType == BrushType.SHAPE }

    protected fun addSquareViaMenu(from: Offset, delta: Offset) {
        addMenu("Şekil ekle")
        waitForText("Kare")
        compose.onNodeWithText("Kare").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Kare")
        drag(from, delta)
    }

    /** Ana iş parçacığında bekleyen gecikmeli işler (ör. çizim düzeltme önerisi) çalışsın. */
    protected fun advanceMain(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        compose.waitForIdle()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V17ModesTest : V17EditorBase() {
    @Test
    fun selectMergedWithViewAddAndEditModesAndContextualTopMenu() {
        createCanvasAndOpen("Modlar")
        val size = canvasSize()
        // v47 mod menüsü: Seç, Çiz, Ekle, Düzenle (Taşı ve Genişlet Düzenle satırında; İncele, Döndür, Ölçekle yok)
        val selector = listOf("Seç", "Çiz", "Ekle", "Düzenle")
        selector.forEach { label ->
            assertTrue(label, compose.onAllNodes(hasContentDescription(label) and hasAnyAncestor(hasTestTag("mode_selector"))).fetchSemanticsNodes().isNotEmpty())
        }
        listOf("İncele", "Döndür", "Ölçekle", "Taşı", "Genişlet").forEach { label ->
            assertTrue(label, compose.onAllNodes(hasContentDescription(label) and hasAnyAncestor(hasTestTag("mode_selector"))).fetchSemanticsNodes().isEmpty())
        }
        // Çiz: üst menüde çizim araçları (fırça, silgi, Ekle menüsü)
        assertTrue(compose.onAllNodesWithContentDescription("Fırça Seç").fetchSemanticsNodes().isNotEmpty())

        // Bir çizgi çiz, Seç'e geç: seçim yokken yönerge; üst menüde Döndür/Ölçekle (pasif)
        val start = Offset(size.x * 0.1f, size.y * 0.8f)
        drag(start, Offset(size.x * 0.5f, 0f))
        waitFor { canvas().strokes.size == 1 }
        mode("Seç")
        inSelectIdle()
        // v18: Seç'te kopyala/sil; Döndür/Ölçekle Düzenle'de
        assertTrue(hasTopTool("Kopyala") && hasTopTool("Sil") && !hasTopTool("Döndür") && !hasTopTool("Ölçekle"))
        assertTrue(compose.onAllNodesWithContentDescription("Fırça Seç").fetchSemanticsNodes().isEmpty())
        // Elemana dokun: seçilir, mod çubuğu çıkar; boş yere dokun: seçim kalkar
        tap(start + Offset(size.x * 0.25f, 0f))
        modeBarTitle("Seç")
        waitForText("1 eleman seçili", substring = true)
        tap(Offset(size.x * 0.5f, size.y * 0.3f))
        inSelectIdle()
        // Seç, sürükleme kaydırır (çizim eklenmez)
        drag(Offset(size.x * 0.5f, size.y * 0.3f), Offset(size.x * 0.1f, size.y * 0.1f))
        assertEquals(1, canvas().strokes.size)
        drag(Offset(size.x * 0.6f, size.y * 0.4f), Offset(-size.x * 0.1f, -size.y * 0.1f))
        assertEquals(1, canvas().strokes.size)
        // Döndür üst menüden: seçiliyken açılır (v18: Düzenle modunun üst menüsünde; bitince Düzenle'ye dönülür)
        tap(start + Offset(size.x * 0.25f, 0f))
        modeBarTitle("Seç")
        mode("Düzenle")
        topTool("Döndür").performClick()
        modeBarTitle("Döndür")
        finish()
        waitForText("Düzenlemek istediğin elemana dokun")
        mode("Seç")
        inSelectIdle()

        // Şekil ekle → Taşı → Bitti → Seç (seçimsiz)
        addMenu("Şekil ekle")
        waitForText("Kare")
        compose.onNodeWithText("Kare").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Şekil: Kare")
        // Ekleme sırasında üst menü eklenecekleri gösterir, Şekil ekle seçili
        assertTrue(hasTopTool("Şekil ekle") && hasTopTool("Yazı ekle"))
        drag(Offset(size.x * 0.25f, size.y * 0.2f), Offset(size.x * 0.4f, size.x * 0.4f))
        waitFor { shapes().size == 1 }
        modeBarTitle("Taşı")
        finish()
        inSelectIdle()

        // Ekle modu: üst menüden Yazı ekle → dokunulan yerde yazı yazılır (v47 boş yere dokunma menüsü kaldırıldı)
        mode("Ekle")
        waitForText("Eklemek istediğin yere dokun")
        assertTrue(hasTopTool("Resim ekle"))
        val here = Offset(size.x * 0.2f, size.y * 0.6f)
        compose.onNode(hasContentDescription("Yazı ekle") and hasAnyAncestor(hasTestTag("tool_row_context"))).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        if (compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isEmpty()) tap(here)
        assertTrue(compose.onAllNodesWithTag("canvas_add_menu").fetchSemanticsNodes().isEmpty())
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor"))).performTextInput("Burada")
        compose.onNodeWithContentDescription("Yazıyı bitir").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.any { it.text?.content == "Burada" } }

        // Düzenle modu: şekle dokununca şekil düzenleme açılır; üst menüde şekil araçları (Alan boya dahil)
        mode("Düzenle")
        waitForText("Düzenlemek istediğin elemana dokun")
        val square = shapes().single()
        val box = ElementOps.bounds(square)!!
        val map = mappingFrom(start, size.x * 0.5f)
        tap(map.toScreen(box.center.x, box.top))
        modeBarTitle("Şekli Düzenle: Noktalar")
        listOf("Noktalar", "Çizgiler", "Alan boya").forEach { assertTrue(it, hasTopTool(it)) }
        // v33 Nokta ekle ve Çizgi çiz alttaki nokta panelinde
        listOf("Nokta ekle", "Çizgi çiz").forEach { assertTrue(it, hasNodeTool(it)) }
        finish()
        inSelectIdle()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V17ContextMenuTest : V17EditorBase() {
    @Test
    fun twoFingerMenuDependsOnSelectionAndRegionFill() {
        createCanvasAndOpen("Bağlam")
        val size = canvasSize()
        val start = Offset(size.x * 0.1f, size.y * 0.85f)
        drag(start, Offset(size.x * 0.5f, 0f))
        waitFor { canvas().strokes.size == 1 }
        val map = mappingFrom(start, size.x * 0.5f)

        // v47 hiçbir şey seçili değilken iki parmakla basmak menü açmaz (yalnız saydam seçim menüsü kaldı)
        val anchor = Offset(size.x * 0.5f, size.y * 0.35f)
        twoFingerHold(anchor)
        compose.waitForIdle()
        assertFalse(contextMenuOpen())
        // Şekil ekle (Ekle satırı) → seçiciden Kare → dokunulan yere eklenir
        addMenu("Şekil ekle")
        waitForText("Kare")
        compose.onNodeWithText("Kare").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        tap(anchor)
        waitFor { shapes().size == 1 }
        val square = shapes().single()
        val box = ElementOps.bounds(square)!!
        val expected = Offset((anchor.x - map.offset.x) / map.scale, (anchor.y - map.offset.y) / map.scale)
        assertTrue("şekil basılan yerde: ${box.center} ~ $expected", (box.center - expected).getDistance() < box.width * 0.2f)
        modeBarTitle("Taşı")
        finish()

        // Şekil seçiliyken: şekil seçenekleri → Alan boya
        mode("Seç")
        tap(map.toScreen(box.center.x, box.top))
        modeBarTitle("Seç")
        twoFingerHold(Offset(size.x * 0.5f, size.y * 0.6f))
        waitFor { contextMenuOpen() }
        listOf("Şekli düzenle", "Alan boya", "Dolgu rengi", "Döndür", "Kopyala", "Sil").forEach {
            waitFor { compose.onAllNodes(hasText(it) and inContextMenu()).fetchSemanticsNodes().isNotEmpty() }
        }
        menuItem("Alan boya").performSemanticsAction(SemanticsActions.OnClick)
        modeBarTitle("Şekli Düzenle: Alan boya")
        waitFor { compose.onAllNodesWithTag("region_fill_controls").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Renk #E53935").performSemanticsAction(SemanticsActions.OnClick)
        // Karenin içine dokun: alan boyanır (kenarsız dolgu parçası, çizginin iç kenarına kadar)
        tap(map.toScreen(box.center.x, box.center.y))
        waitFor { ShapeFactory.subPaths(shapes().single()).any { it.tag == VectorEdit.REGION } }
        val region = ShapeFactory.subPaths(shapes().single()).single { it.tag == VectorEdit.REGION }
        assertEquals("#E53935", region.fillColorHex)
        assertEquals(0f, region.widthScale)
        val rb = ElementOps.bounds(shapes().single())!!
        val rx = region.points.maxOf { it.x } - region.points.minOf { it.x }
        assertTrue("alan karenin içi kadar: $rx / ${rb.width}", rx > rb.width * 0.85f && rx < rb.width)
        // Aynı alana başka renkle dokun: rengi değişir (yeni parça eklenmez); Temizle: kalkar
        compose.onNodeWithContentDescription("Renk #1E88E5").performSemanticsAction(SemanticsActions.OnClick)
        tap(map.toScreen(box.center.x, box.center.y))
        waitFor { ShapeFactory.subPaths(shapes().single()).singleOrNull { it.tag == VectorEdit.REGION }?.fillColorHex == "#1E88E5" }
        compose.onNodeWithTag("region_fill_clear").performSemanticsAction(SemanticsActions.OnClick)
        tap(map.toScreen(box.center.x, box.center.y))
        waitFor { ShapeFactory.subPaths(shapes().single()).none { it.tag == VectorEdit.REGION } }
        // Kapalı olmayan yer (şeklin dışı): bildirim
        tap(map.toScreen(box.center.x, box.bottom + box.height * 0.1f))
        finish()
        inSelectIdle()

        // Çizim (fırça izi) seçiliyken: çizim seçenekleri → Kalınlaştır
        val ink = canvas().strokes.first { it.brushType != BrushType.SHAPE }
        val before = ink.sizePx
        tap(start + Offset(size.x * 0.25f, 0f))
        modeBarTitle("Seç")
        // Alt çubukta da çizim düzenleme düğmeleri
        waitForText("Yumuşat")
        // v23: iki parmak seçili olmayan elemanın (kare) üstüne basılırsa onu seçer; boş yere basılır
        val empty = Offset(size.x * 0.85f, size.y * 0.62f)
        twoFingerHold(empty)
        waitFor { contextMenuOpen() }
        listOf("Renk", "İncelt", "Kalınlaştır", "Yumuşat").forEach {
            waitFor { compose.onAllNodes(hasText(it) and inContextMenu()).fetchSemanticsNodes().isNotEmpty() }
        }
        // v38 tek çizgiye öneri yok: tek çizim seçiliyken "Düzeltme öner" yok
        assertTrue(compose.onAllNodes(hasText("Düzeltme öner") and inContextMenu()).fetchSemanticsNodes().isEmpty())
        menuItem("Kalınlaştır").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.first { it.id == ink.id }.sizePx > before * 1.2f }
        // v38 tek çizgi için düzeltme önerisi yok (menüde de yok)
        twoFingerHold(empty)
        waitFor { contextMenuOpen() }
        assertTrue(compose.onAllNodes(hasText("Düzeltme öner") and inContextMenu()).fetchSemanticsNodes().isEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V17InkCorrectionTest : V17EditorBase() {
    @Test
    fun wobblyCircleIsSuggestedAsCircleAndAppliedOnConfirm() {
        createCanvasAndOpen("Düzeltme")
        val size = canvasSize()
        // Ayarlarda ve ⋮ menüsünde el yazısı ayarı yok
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        waitForText("Otomatik kaydet")
        assertTrue(compose.onAllNodesWithText("El yazısı önerisi").fetchSemanticsNodes().isEmpty())
        // Menü sistem geri hareketiyle kapanır (en üstteki pencere menü)
        SystemBack.press()
        waitFor { compose.onAllNodesWithText("Otomatik kaydet").fetchSemanticsNodes().isEmpty() }

        // Titrek bir çember çiz
        val c = Offset(size.x * 0.5f, size.y * 0.4f)
        val r = size.x * 0.25f
        val rnd = java.util.Random(3)
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(Offset(c.x + r, c.y))
            for (i in 1..72) {
                val a = i / 72.0 * 2 * PI * 1.04
                val rr = r * (1f + (rnd.nextFloat() - 0.5f) * 0.08f)
                moveTo(Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat()))
            }
            up()
        }
        waitFor { canvas().strokes.size == 1 }
        val drawn = canvas().strokes.single()
        advanceMain(900)
        // v38 tek çizgiye öneri yoktu; v40 v39 büyük tek çizgiye çizgi önerisi (SuggestRules.bigLine): tuvalin yarısı
        // çapındaki çember tek başına çizgi önerisi alır (yalnız bu çizgi; şekil önerisi yok)
        waitFor(15_000) { compose.onAllNodesWithContentDescription("Düzeltmeyi uygula: 1 çizgi birden").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithTag("shape_suggest_chip").fetchSemanticsNodes().isEmpty())
        // İçine ikinci (küçük) titrek çember: iki çizgi birlikte önerilir
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            val r2 = r * 0.4f
            down(Offset(c.x + r2, c.y))
            for (i in 1..72) {
                val a = i / 72.0 * 2 * PI * 1.04
                val rr = r2 * (1f + (rnd.nextFloat() - 0.5f) * 0.08f)
                moveTo(Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat()))
            }
            up()
        }
        waitFor { canvas().strokes.size == 2 }
        val inner = canvas().strokes.last()
        advanceMain(900)
        waitFor(15_000) { compose.onAllNodesWithTag("line_suggest_chip").fetchSemanticsNodes().isNotEmpty() }
        waitForText("Çizgi önerisi", substring = true)
        waitFor { compose.onAllNodesWithContentDescription("Düzeltmeyi uygula: 2 çizgi birden").fetchSemanticsNodes().isNotEmpty() }
        // v40 v39 Premium kilitleri: çizgiyi yerleşik şekle (çember) düzeltmek Free planda da açık (kilit yok)
        TestAccount.entitlements.value = com.karapp.drawguidance.data.billing.Entitlements.FREE
        compose.waitForIdle()
        // Onay: iki iz aynı fırçayla düzgün çember olur (tek düğme)
        compose.onNodeWithContentDescription("Düzeltmeyi uygula: 2 çizgi birden").performClick()
        waitFor { canvas().strokes.first().points != drawn.points && canvas().strokes.last().points != inner.points }
        val fixed = canvas().strokes.first()
        assertEquals(drawn.brushType, fixed.brushType)
        assertEquals(drawn.colorHex, fixed.colorHex)
        val cx = fixed.points.map { it.x }.average().toFloat()
        val cy = fixed.points.map { it.y }.average().toFloat()
        val radii = fixed.points.map { hypot(it.x - cx, it.y - cy) }
        val mean = radii.average().toFloat()
        assertTrue("düzgün çember", radii.all { abs(it - mean) < mean * 0.03f })
        assertTrue(compose.onAllNodesWithTag("handwriting_chip").fetchSemanticsNodes().isEmpty())
        // Tek geri alma adımıyla iki çizim de eski haline döner
        compose.onNodeWithContentDescription("Geri al").performClick()
        waitFor { canvas().strokes.first().points == drawn.points && canvas().strokes.last().points == inner.points }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V17VectorizeTest : V17EditorBase() {
    @Test
    fun imageAddedHereThenEdgesBecomeEditableLines() {
        createCanvasAndOpen("Vektör")
        val size = canvasSize()
        // Ekle modunda dokunulan yere resim
        val file = File(compose.activity.cacheDir, "cizim.png").apply { writeBytes(ImageImportValidatorTest.png(ImageImportValidatorTest.simpleDrawing(800, 600))) }
        // v47 Ekle satırındaki Resim ekle (boş yere dokununca açılan ekleme menüsü kaldırıldı)
        addMenu("Resim ekle")
        compose.waitForIdle()
        val shadow = shadowOf(compose.activity)
        val started = requireNotNull(shadow.nextStartedActivityForResult) { "resim seçici açılmadı" }
        shadow.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        waitFor { canvas().strokes.any { it.brushType == BrushType.IMAGE } }
        val image = canvas().strokes.single { it.brushType == BrushType.IMAGE }
        finish()

        // Düzenle: resme dokun → resim düzenleme; üst menüde Kırp / Vektörleştir / Filtre (v27 kalem yalnız Çiz'de)
        mode("Düzenle")
        val pts = image.points
        // Resmin köşesine yakın boş bir yere değil, resmin içine dokun (tuval → ekran: ekleme noktası ekranın ortası)
        tap(Offset(size.x * 0.5f, size.y * 0.4f))
        modeBarTitle("Resmi düzenle")
        // v34: Vektörleştir'in türleri doğrudan üst menüde (Otomatik / Kenarları / Çizgileri vektörleştir)
        listOf("Kırp", "Otomatik vektörleştir", "Kenarları vektörleştir", "Çizgileri vektörleştir").forEach { assertTrue(it, hasTopTool(it)) }
        assertTrue("Vektörleştir alt menüsü kalktı", !hasTopTool("Vektörleştir"))
        assertTrue("kalem aracı yalnız Çiz menüsünde", !hasTopTool("Kalem"))
        topTool("Çizgileri vektörleştir").performClick()
        waitFor { compose.onAllNodesWithTag("vectorize_sheet").fetchSemanticsNodes().isNotEmpty() }
        // Tür üst menüden seçildi: pencerede tür seçimi yok, yalnız ayarlar
        assertTrue(compose.onAllNodesWithTag("vectorize_mode_centerline").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("vectorize_apply").performClick()
        waitFor(30_000) { shapes().isNotEmpty() }
        val traced = shapes().single()
        val parts = ShapeFactory.subPaths(traced)
        // Çizgiler dolgusuz, vektör noktalı; resmin çemberi ve çizgisi (az sayıda parça)
        assertTrue("parça sayısı ${parts.size}", parts.size in 2..8)
        assertTrue(parts.all { it.fillColorHex == null })
        assertTrue(parts.all { (it.nodes?.size ?: 0) >= 2 })
        // Çizgiler resmin çizgilerinin üstünde: parçaların kapsadığı alan resmin çizimini kaplar
        val all = parts.flatMap { it.points }
        val ib = ElementOps.bounds(image)!!
        assertTrue((all.maxOf { it.x } - all.minOf { it.x }) > ib.width * 0.6f)
        // Resim korundu (varsayılan), yeni şekil resmin üstünde
        assertTrue(canvas().strokes.any { it.brushType == BrushType.IMAGE })
        assertEquals(pts, canvas().strokes.single { it.brushType == BrushType.IMAGE }.points)
    }
}
