package com.karapp.drawguidance

import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.SavedImageDao
import com.karapp.drawguidance.data.images.SavedImageEntity
import com.karapp.drawguidance.data.model.DiagramSide
import com.karapp.drawguidance.ui.drawing.DiagramRenderer
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import org.robolectric.Shadows.shadowOf

/** v13 görsel kontrolü (DG_SHOTS verilince ekran görüntüsü). */
abstract class V13ShotsBase : WideLayoutTestBase() {

    private fun closeSheet(tag: String) {
        SystemBack.press()
        waitFor { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    }

    protected fun editor(prefix: String) {
        createNotesCanvasAndOpen("Ders Notu")
        shot("$prefix-1-toolbar")
        val size = canvasSize()
        // Şekil + düzenleme
        addMenu("Şekil ekle")
        waitForText("Geometrik Cisimler")
        compose.onNodeWithText("Küp").performSemanticsAction(SemanticsActions.OnClick)
        val corner = Offset(size.x * 0.12f, size.y * 0.1f)
        drag(corner, Offset(size.x * 0.34f, size.x * 0.34f))
        waitFor { canvas().strokes.size == 1 }
        shot("$prefix-2-shape-added-select")
        // v51 not almada düz şekillerin nokta düzenlemesi yok (parça boyama adımları kaldırıldı)

        // Yazı: başlık + liste
        mode("Çiz")
        addMenu("Yazı ekle")
        tap(Offset(size.x * 0.55f, size.y * 0.12f))
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        val field = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor")))
        compose.onNode(hasContentDescription("Başlık 1") and hasAnyAncestor(hasTestTag("inline_font_bar"))).performSemanticsAction(SemanticsActions.OnClick)
        field.performTextInput("Küp")
        compose.onNodeWithContentDescription("Yazıyı bitir").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.count { it.text != null } == 1 }
        mode("Çiz")
        addMenu("Yazı ekle")
        tap(Offset(size.x * 0.55f, size.y * 0.45f))
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        val field2 = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor")))
        compose.onNode(hasContentDescription("Numaralı liste") and hasAnyAncestor(hasTestTag("inline_font_bar"))).performSemanticsAction(SemanticsActions.OnClick)
        field2.performTextInput("6 yüz")
        field2.performTextInput("\n")
        field2.performTextInput("12 ayrıt")
        field2.performTextInput("\n")
        field2.performTextInput("8 köşe")
        shot("$prefix-6-list-editing")
        compose.onNode(hasTestTag("font_picker_button") and hasAnyAncestor(hasTestTag("inline_font_bar"))).performClick()
        waitFor { compose.onAllNodesWithTag("font_list").fetchSemanticsNodes().isNotEmpty() }
        shot("$prefix-7-font-list")
        compose.onNodeWithTag("font_list").performScrollToNode(hasContentDescription("Poppins"))
        compose.onNode(hasContentDescription("Poppins") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Yazıyı bitir").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.count { it.text != null } == 2 }
        mode("Çiz")

        // Diyagram: boşluğa bırakılan ok yeni adım (yazı yazılırken görünüm kaymış olabilir: önce sığdırılır)
        fitView()
        addMenu("Diyagram ekle")
        waitForText("Diyagram Türü")
        compose.onNodeWithText("Akış Diyagramı").performSemanticsAction(SemanticsActions.OnClick)
        val screen = Rect(size.x * 0.08f, size.y * 0.42f, size.x * 0.5f, size.y * 0.8f)
        drag(screen.topLeft, Offset(screen.width, screen.height))
        waitFor { canvas().strokes.any { it.diagram != null } }
        val d = canvas().strokes.first { it.diagram != null }
        val box = Rect(d.points.minOf { it.x }, d.points.minOf { it.y }, d.points.maxOf { it.x }, d.points.maxOf { it.y })
        val ds = screen.width / box.width
        fun toScreen(p: Offset) = fittedScreen(p)
        editButton("Diyagramı düzenle")
        waitForText("Adım ekle")
        val karar = d.diagram!!.nodes[2]
        tap(toScreen(DiagramRenderer.toCanvas(d, karar.x + karar.w / 2f, karar.y + karar.h / 2f)!!))
        val port = toScreen(DiagramRenderer.portPoint(d, karar, DiagramSide.RIGHT)!!)
        drag(port, Offset(size.x * 0.82f, port.y) - port, steps = 30)
        waitFor { canvas().strokes.first { it.diagram != null }.diagram!!.nodes.size == 5 }
        compose.onNodeWithContentDescription("Renk").performClick()
        waitFor { compose.onAllNodesWithTag("part_color_sheet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Renk #FDD835").performClick()
        closeSheet("part_color_sheet")
        shot("$prefix-8-diagram-dropped-node")
        compose.onNodeWithContentDescription("Bitti").performClick()
        mode("Çiz")
        shot("$prefix-9-page")
    }

    protected fun gallery(prefix: String) {
        val context = compose.activity
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DrawGuidance").apply { mkdirs() }
        val dao = GlobalContext.get().get<SavedImageDao>()
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        listOf(
            Triple("Deniz Feneri", 1080 to 1350, 0xFF1E5AA0.toInt()),
            Triple("Orman", 1600 to 1000, 0xFF2E7D32.toInt()),
            Triple("Portre Çalışması", 1000 to 1400, 0xFFE0A070.toInt()),
            Triple("Ders Notu", 1240 to 1754, 0xFFF5F0E6.toInt()),
            Triple("Gün Batımı", 1400 to 900, 0xFFE65100.toInt()),
        ).forEachIndexed { i, (title, dims, color) ->
            val file = File(dir, "img$i.png")
            val bitmap = Bitmap.createBitmap(dims.first / 10, dims.second / 10, Bitmap.Config.ARGB_8888).apply {
                eraseColor(color)
                val c = android.graphics.Canvas(this)
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x66FFFFFF }
                c.drawCircle(width * 0.6f, height * 0.4f, width * 0.22f, p)
            }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            runBlocking {
                dao.insert(SavedImageEntity(canvasLocalId = "c$i", title = title, uri = Uri.fromFile(file).toString(),
                    width = dims.first, height = dims.second, createdAt = now - i * 2 * day, location = file.absolutePath, sizeBytes = 180_000L + i * 90_000L))
            }
        }
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNode(hasText("Kaydedilenler") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Deniz Feneri")
        // Küçük resimler arka planda yüklenir
        repeat(10) { Thread.sleep(150); compose.waitForIdle(); shadowOf(android.os.Looper.getMainLooper()).idle() }
        shot("$prefix-1-gallery")
        compose.onNodeWithContentDescription("Liste görünümü").performClick()
        compose.waitForIdle()
        shot("$prefix-2-list")
        compose.onNode(hasText("Ayarlar") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        // v36 geliştirici ayarları Gelişmiş sekmesindeydi; v40 v39 ayarlar sadeleştirmede geliştirici modu ve günlükler
        // düğmesi kaldırıldı: Gelişmiş sekmesinde yalnız hata raporları kartı var
        waitFor { compose.onAllNodesWithTag("settings_tab_advanced").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings_tab_advanced").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { compose.onAllNodesWithTag("reports_card").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithText("Geliştirici modu").fetchSemanticsNodes().isEmpty())
        shot("$prefix-3-advanced")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V13PhoneShotsTest : V13ShotsBase() {
    @Test fun phone() = editor("v13-phone")
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w891dp-h411dp-land-xhdpi")
class V13LandShotsTest : V13ShotsBase() {
    @Test fun land() = editor("v13-land")
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V13GalleryShotsTest : V13ShotsBase() {
    @Test fun gallery() = gallery("v13-gallery")
}
