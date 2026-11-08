package com.karapp.drawguidance

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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.model3d.Samples3D
import com.karapp.drawguidance.data.library.Model3DRepository
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.GuideSpec
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

abstract class V23EditorBase : V21EditorBase() {

    /** v26 3B düzenleyici: mod çubuğundan mod (Seç, Düzenle, Çiz, Boya, Ekle). */
    protected fun mode3(label: String) {
        compose.onNode(hasContentDescription(label) and hasAnyAncestor(hasTestTag("model3d_modes"))).performClick()
        compose.waitForIdle()
    }

    /** v26 3B düzenleyici: alt araç (modunu da seçer): Taşı/Çevir/Ölçekle, Nokta/Kenar/Yüz, Fırça/Silgi, Çiz, Boya. */
    protected fun tool3(label: String) {
        val mode = when (label) {
            "Taşı", "Çevir", "Ölçekle" -> "Seç"
            "Nokta", "Kenar", "Yüz" -> "Düzenle"
            "Fırça", "Silgi", "Çiz" -> "Çiz"
            "Boya" -> "Boya"
            else -> error("bilinmeyen araç: $label")
        }
        mode3(mode)
        if (label == "Çiz" || label == "Boya") return
        subtool3(label)
    }

    /** v27 alt araç ya da boya kapsamı (araç satırının üstündeki ayrı seçicide). */
    protected fun subtool3(label: String) {
        compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("model3d_subtools"))).performClick()
        compose.waitForIdle()
    }

    /** v26 3B düzenleyicinin araç satırındaki düğme (yazısıyla). */
    protected fun action3(label: String) {
        compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("model3d_actions"))).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** v26 açık paneldeki öğe (açıklamasıyla; ör. Ekle panelinde "Küre"). */
    protected fun panelItem3(description: String) {
        compose.onNode(hasContentDescription(description) and hasAnyAncestor(hasTestTag("model3d_panel"))).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** Tuvalin ilk sığdırılmış görünümünde tuval noktasının ekran konumu. */
    protected fun screenOf(x: Float, y: Float): Offset {
        val view = canvasSize()
        val c = canvas()
        val margin = 24f * compose.density.density
        val scale = minOf((view.x - 2 * margin) / c.width, (view.y - 2 * margin) / c.height)
        val ox = (view.x - c.width * scale) / 2f
        val oy = (view.y - c.height * scale) / 2f
        return Offset(ox + x * scale, oy + y * scale)
    }

    /** Açılır pencereler (alt sayfalar, menüler, iletişim kutuları) dahil tüm pencerelerin ekran görüntüsü. */
    protected fun shotAll(name: String) {
        val dir = System.getenv("DG_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        repeat(4) { Thread.sleep(80); compose.waitForIdle(); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }
        val root = compose.activity.window.decorView
        val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnUiThread {
            val canvas = android.graphics.Canvas(bitmap)
            val wmg = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val views = (wmg.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(wmg) as List<android.view.View>).toList()
            views.filter { it.isShown && it.width > 0 && it.height > 0 }.forEach { v ->
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                val lp = v.layoutParams as? android.view.WindowManager.LayoutParams
                if (v !== root && lp != null && lp.flags and android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
                    canvas.drawColor(android.graphics.Color.argb((lp.dimAmount * 255).toInt(), 0, 0, 0))
                }
                canvas.save()
                canvas.translate(loc[0].toFloat(), loc[1].toFloat())
                v.draw(canvas)
                canvas.restore()
            }
        }
        java.io.File(dir).mkdirs()
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Arka planda çizim/resim üretimi bitsin (3B çizim, küçük resimler). */
    protected fun settleFor(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            compose.waitForIdle()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(60)
        }
    }

    protected fun addText(at: Offset, text: String) {
        addMenu("Yazı ekle")
        tap(at)
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor"))).performTextInput(text)
        compose.onNodeWithContentDescription("Yazıyı bitir").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }
}

/** v23 (0), v24: Düzenle/Çiz menüsünden Arka plan: resim tuvalinde desen ve hazır resim seçilir, tuvale kaydedilir. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V23BackgroundTest : V24EditorBase() {
    @Test
    fun patternAndImageOnArtCanvas() {
        createCanvasAndOpen("Resim tuvali")
        // v24: Arka plan Düzenle/Çiz üst menüsünde
        openBackground()
        shotAll("v23-01-background-sheet")
        compose.onNodeWithContentDescription("Puantiye").performClick()
        compose.onNodeWithContentDescription("Suluboya kağıdı").performClick()
        settleFor(600)
        shotAll("v23-02-background-picked")
        // Desen rengi ve ölçeği
        compose.onNodeWithContentDescription("#E57373").performScrollTo().performClick()
        // v41 seçimler Uygula ile tuvale konur (aynı pencerede seçilen desen ve resim birlikte kalır)
        compose.onNodeWithTag("bg_apply").performScrollTo().performClick()
        waitFor { !exists("background_sheet") }
        waitFor { canvas().background.patternId == "art.polka" && canvas().background.paperImageId == "art.img.coldpress" }
        waitFor { canvas().background.patternColorHex == "#E57373" }
        settleFor(400)
        shot("v23-03-art-canvas-background")
    }
}

/**
 * v23 (0, 1.1, 4): not tuvali formda desenle oluşturulur; eklenen yazı Taşı moduna geçer; kutunun sağ kenar tutamağıyla
 * genişletilir; Elemanlar panelinde kutuya hazır resim (yapışkan not) verilir.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V23NotesTextBoxTest : V23EditorBase() {
    @Test
    fun formPatternTextMovesAndResizes() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithContentDescription("Yeni Tuval").performClick()
        waitForText("Yeni Tuval Oluştur")
        compose.onNodeWithText("Çalışma / Not").clickInPopup()
        compose.onNode(hasSetTextAction()).performTextInput("Notlar")
        compose.onNodeWithTag("bg_patterns").performScrollTo().performScrollToNode(hasContentDescription("Dar çizgili"))
        compose.onNodeWithContentDescription("Dar çizgili").performClick()
        compose.waitForIdle()
        shotAll("v23-04-form-background")
        compose.onNodeWithText("Oluştur").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("note.ruled_college", canvas().background.patternId)

        addText(screenOf(150f, 300f), "Toplantı notları: proje planı ve görev dağılımı")
        waitFor { canvas().strokes.any { it.text != null } }
        // v23 (4): not tuvalinde eklenen yazı seçili olarak Taşı modunda
        modeBarTitle("Taşı")
        shot("v23-05-text-move-handles")
        val t = canvas().strokes.first { it.text != null }
        val q = t.points
        val handle = screenOf((q[1].x + q[2].x) / 2f, (q[1].y + q[2].y) / 2f)
        val oldW = q[1].x - q[0].x
        // v27 Taşı modunda kenardan sürüklemek yazı kutusunu yalnız taşır (boyutlanmaz)
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(handle)
            repeat(20) { moveBy(Offset(-12f, 0f)) }
            up()
        }
        compose.waitForIdle()
        waitFor { canvas().strokes.first { it.text != null }.points[0].x < q[0].x - 20f }
        val moved = canvas().strokes.first { it.text != null }
        assertEquals("taşımada genişlik aynı", oldW, moved.points[1].x - moved.points[0].x, 0.5f)
        // Ölçekle modunda kenar tutamağı kutuyu daraltır, satırlar kayar
        // (v37 not tuvalinde uzun yazı sağ kenarda alt satıra geçer: kutunun eni baştan var, daralması beklenir)
        mode("Ölçekle")
        val m = moved.points
        val handle2 = screenOf((m[1].x + m[2].x) / 2f, (m[1].y + m[2].y) / 2f)
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(handle2)
            repeat(40) { moveBy(Offset(-12f, 0f)) }
            up()
        }
        compose.waitForIdle()
        waitFor { canvas().strokes.first { it.text != null }.let { it.points[1].x - it.points[0].x < oldW - 20f } }
        val resized = canvas().strokes.first { it.text != null }
        assertTrue("kutu daraldı, satırlar kaydı", resized.points[1].x - resized.points[0].x < oldW - 20f)
        assertTrue(resized.points[3].y - resized.points[0].y > q[3].y - q[0].y)
        shot("v23-06-text-box-resized")

        // Kutuya hazır resim (Elemanlar panelinde yazı ayarlarının altında)
        clickElements()
        // v29 yazı kutusu tek yazı biçimi alanının "Kutu ve yer" bölümünde
        waitFor { exists("text_tab_BOX") }
        compose.onNodeWithTag("text_tab_BOX").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("text_box_controls") }
        compose.onNode(hasText("Resim") and hasAnyAncestor(hasTestTag("text_box_controls"))).performScrollTo()
        settleFor(300)
        shot("v23-07a-before-image")
        compose.onNode(hasText("Resim") and hasAnyAncestor(hasTestTag("text_box_controls"))).performSemanticsAction(SemanticsActions.OnClick)
        settleFor(300)
        println("KUTU: " + canvas().strokes.first { it.text != null }.text?.box)
        waitFor { canvas().strokes.first { it.text != null }.text?.box?.imageId == "box.img.sticky_yellow" }
        settleFor(500)
        shot("v23-07-text-box-panel")
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        settleFor(400)
        shot("v23-08-text-box-sticky")
    }
}

/**
 * v23 (5, 7): seçili değilken şekle iki parmakla uzun basınca şekil seçilir ve menüde Taşı, sıra ve "Bu şekilden ok
 * çıkar" çıkar; ok şeklin kenarından sürüklenen yere çizilir; boş yerde iki parmak ekleme menüsünü açar.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V23ContextArrowTest : V23EditorBase() {
    @Test
    fun twoFingerSelectsThenArrowFromShape() {
        createNotesCanvasAndOpen("Ok")
        val size = canvasSize()
        val from = Offset(size.x * 0.25f, size.y * 0.3f)
        addSquareViaMenu(from, Offset(size.x * 0.22f, size.x * 0.22f))
        waitFor { shapes().size == 1 }
        finish()
        compose.waitForIdle()
        // Boş yere dokununca seçim kalkar
        mode("Seç")
        tap(Offset(size.x * 0.8f, size.y * 0.8f))
        compose.waitForIdle()
        // v45 seçim yoksa boş yere dokunmak ekleme menüsünü açar: kapatılır
        dismissAddMenu()
        twoFingerHold(Offset(from.x, from.y + size.x * 0.11f))
        waitFor { contextMenuOpen() }
        waitFor { compose.onAllNodes(hasText("Taşı") and inContextMenu()).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodes(hasText("Bu şekilden ok çıkar") and inContextMenu()).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("Bir üste getir") and inContextMenu()).fetchSemanticsNodes().isNotEmpty())
        shotAll("v23-09-context-shape")
        menuItem("Bu şekilden ok çıkar").clickInPopup()
        waitForText("Okun gideceği yere", substring = true)
        val target = Offset(size.x * 0.85f, size.y * 0.62f)
        drag(Offset(size.x * 0.6f, size.y * 0.45f), target - Offset(size.x * 0.6f, size.y * 0.45f))
        waitFor { shapes().size == 2 }
        val square = shapes().first()
        val arrow = shapes().last()
        val sx = square.points.maxOf { it.x }
        val start = arrow.points.first()
        // Okun başı karenin sağ/alt kenarının hemen dışında
        assertTrue("ok kenardan çıkıyor: ${start.x} / $sx", start.x >= sx - 2f && start.x <= sx + 60f || start.y >= square.points.maxOf { it.y } - 2f)
        settleFor(300)
        shot("v23-10-arrow-from-shape")
        finish()
        // v47 boş yerde ne dokunma ne iki parmak menü açar (tuvalde yalnız saydam seçim menüsü kaldı)
        mode("Seç")
        tap(Offset(size.x * 0.15f, size.y * 0.85f))
        compose.waitForIdle()
        twoFingerHold(Offset(size.x * 0.15f, size.y * 0.85f))
        settleFor(300)
        assertFalse(contextMenuOpen())
        assertTrue(compose.onAllNodes(hasText("Yazı ekle") and inContextMenu()).fetchSemanticsNodes().isEmpty())
        shotAll("v23-11-context-empty")
    }
}

/** v23 (6): elemanlar panelinde üst/alt sıra düğmeleri; katmanlar panelinde yukarı/aşağı düğmeleri. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V23OrderTest : V23EditorBase() {
    @Test
    fun bringToFrontAndLayerButtons() {
        createCanvasAndOpen("Sıra")
        val size = canvasSize()
        drag(Offset(size.x * 0.2f, size.y * 0.3f), Offset(size.x * 0.5f, 0f))
        drag(Offset(size.x * 0.2f, size.y * 0.5f), Offset(size.x * 0.5f, 0f))
        waitFor { canvas().strokes.size == 2 }
        val firstId = canvas().strokes[0].id
        mode("Seç")
        tap(Offset(size.x * 0.45f, size.y * 0.3f))
        waitForText("1 eleman seçili")
        clickElements()
        waitForDescription("En üste getir")
        shot("v23-12-elements-order")
        compose.onNodeWithContentDescription("En üste getir").performClick()
        waitFor { canvas().strokes.last().id == firstId }
        compose.onNodeWithContentDescription("En alta gönder").performClick()
        waitFor { canvas().strokes.first().id == firstId }
        compose.onNodeWithContentDescription("Bir üste getir").performClick()
        waitFor { canvas().strokes.last().id == firstId }
    }
}

/**
 * v23 (2): 3B Şekiller bölümünden yeni 3B şekil: düzenleyicide küre eklenir, yüz seçilip yükseltilir, malzeme ve doku
 * verilir, sahne döndürülür; kaydedilince 3B Şekillerim'de görünür.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V23Model3DEditorTest : V23EditorBase() {
    @Test
    fun newModelEditAndSave() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithText("3B Şekiller").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("models3d_screen") }
        shot("v23-13-models-empty")
        compose.onNodeWithText("Yeni 3B şekil").performClick()
        waitFor { exists("model3d_editor") }
        settleFor(1500)
        shot("v23-14-editor-cube")
        // Ekle: küre (v26 Ekle modu ekleme panelini açar)
        mode3("Ekle")
        waitFor { exists("model3d_panel_add") }
        settleFor(800)
        shot("v23-15-editor-add-panel")
        panelItem3("Küre")
        waitForText("2 parça", substring = true)
        settleFor(1200)
        shot("v23-16-editor-sphere")
        // Malzeme: doku (v26 Seç modunun satırında)
        mode3("Seç")
        action3("Malzeme")
        waitFor { exists("model3d_panel_material") }
        compose.onNodeWithContentDescription("Meşe").performScrollTo().performClick()
        settleFor(1500)
        shot("v23-17-editor-material")
        // Sahne döndür (iki parmakla sürükle)
        action3("Malzeme")
        val vp = compose.onNodeWithTag("model3d_viewport").fetchSemanticsNode().size
        val c = Offset(vp.width / 2f, vp.height / 2f)
        compose.onNodeWithTag("model3d_viewport").performTouchInput {
            down(0, c - Offset(40f, 0f)); down(1, c + Offset(40f, 0f))
            repeat(12) { updatePointerBy(0, Offset(14f, 4f)); updatePointerBy(1, Offset(14f, 4f)); move() }
            up(0); up(1)
        }
        settleFor(1500)
        shot("v23-18-editor-rotated")
        // Yüz: ortadaki parçanın yüzünü seç ve yükselt
        mode3("Ekle")
        panelItem3("Küp")
        tool3("Yüz")
        settleFor(1200)
        compose.onNodeWithTag("model3d_viewport").performTouchInput { click(center) }
        settleFor(600)
        compose.onNodeWithText("Yükselt").performScrollTo().performClick()
        // v24: kaydırıcıyla miktar, Tamam ile uygulanır
        waitFor { exists("model3d_op") }
        compose.onNodeWithTag("model3d_op_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0.4f) }
        compose.onNodeWithText("Tamam").performClick()
        settleFor(1500)
        shot("v23-19-editor-extrude")
        // Ayarlar paneli (v26 üst çubukta)
        compose.onNodeWithContentDescription("Ayarlar").performClick()
        waitFor { exists("model3d_panel_settings") }
        settleFor(800)
        shot("v23-20-editor-scene")
        compose.onNodeWithTag("model3d_panel_close").performClick()
        // Kaydet
        compose.onNodeWithContentDescription("Kaydet").performClick()
        val repo = GlobalContext.get().get<Model3DRepository>()
        waitFor(20_000) { runBlocking { repo.observe().first().size == 1 } }
        compose.onNodeWithContentDescription("Geri").performClick()
        waitFor { exists("models3d_screen") }
        waitFor { compose.onAllNodesWithContentDescription("Yeni 3B şekil", substring = true).fetchSemanticsNodes().isNotEmpty() }
        settleFor(600)
        shot("v23-21-models-list")
    }
}

/**
 * v23 (2): tuvale 3B Şekillerim'den şekil eklenir (resmi ve modeliyle); bağlam menüsünden "3B düzenle" kaydetme sorusuyla
 * düzenleyiciyi açar; kaydedilince tuvaldeki şeklin resmi yenilenir.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V23Model3DCanvasTest : V23EditorBase() {
    @Test
    fun insertEditAndApplyBack() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val repo = GlobalContext.get().get<Model3DRepository>()
        runBlocking { repo.save(Samples3D.all.first()) }
        createCanvasAndOpen("3B tuval")
        addMenu("3B şekil ekle")
        waitFor { exists("model3d_picker") }
        settleFor(500)
        shotAll("v23-22-model-picker")
        val name = Samples3D.all.first().name
        // v25 dokunmak seçer, "3B şekil ekle" düğmesi ekler
        compose.onNodeWithContentDescription(name).performClick()
        compose.onNodeWithTag("model3d_add_button").performClick()
        waitFor(30_000) { canvas().strokes.any { it.model != null } }
        val placed = canvas().strokes.first { it.model != null }
        assertEquals(BrushType.IMAGE, placed.brushType)
        assertNotNull(placed.assetId)
        modeBarTitle("Taşı")
        settleFor(500)
        shot("v23-23-model-on-canvas")
        // Bağlam menüsü → 3B düzenle → kaydetme sorusu
        val q = placed.points
        twoFingerHold(screenOf((q[0].x + q[2].x) / 2f, (q[0].y + q[2].y) / 2f))
        waitFor { compose.onAllNodes(hasText("3B düzenle") and inContextMenu()).fetchSemanticsNodes().isNotEmpty() }
        shotAll("v23-24-context-3d")
        menuItem("3B düzenle").clickInPopup()
        waitFor { exists("model3d_open_dialog") }
        shotAll("v23-25-open-dialog")
        compose.onNodeWithText("Kaydet ve aç").performClick()
        waitFor(20_000) { exists("model3d_editor") }
        settleFor(1500)
        shot("v23-26-editor-from-canvas")
        mode3("Ekle")
        panelItem3("Küp")
        settleFor(800)
        compose.onNodeWithContentDescription("Kaydet").performClick()
        waitForText("tuvale uygulanacak", substring = true)
        compose.onNodeWithContentDescription("Geri").performClick()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }
        waitFor(30_000) { canvas().strokes.first { it.model != null }.assetId != placed.assetId }
        assertEquals(1, canvas().strokes.count { it.model != null })
        settleFor(600)
        shot("v23-27-canvas-updated")
    }
}

/** v23 (2): tuvaldeki 3B çizim çubuğu kalktı; eski tuvalde açık kalmış 3B çizim açılışta kapanır. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V23No3dBarTest : V23EditorBase() {
    @Test
    fun legacyDraw3dTurnsOff() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val repo = GlobalContext.get().get<CanvasRepository>()
        val c = runBlocking { repo.createCanvas("Eski 3B", 1080, 1080, "#FFFFFF", false) }
        runBlocking { repo.saveGuide(c.localId, GuideSpec(draw3d = true)) }
        waitForText("Eski 3B")
        compose.onAllNodesWithText("Eski 3B")[0].performClick()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }
        waitFor { !canvas().guide.draw3d }
        assertTrue(compose.onAllNodesWithTag("draw3d_chip").fetchSemanticsNodes().isEmpty())
        // Çizim düz kalır
        val size = canvasSize()
        drag(Offset(size.x * 0.2f, size.y * 0.4f), Offset(size.x * 0.5f, 0f))
        waitFor { canvas().strokes.size == 1 }
        assertEquals(null, canvas().strokes.single().space)
        val s = canvas().strokes.single()
        assertTrue(abs(s.points.first().y - s.points.last().y) < 2f)
    }
}
